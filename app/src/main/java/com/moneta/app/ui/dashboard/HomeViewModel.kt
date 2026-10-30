package com.moneta.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.BudgetProgress
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.Transaction
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.BalanceScope
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.domain.usecase.CategoryBreakdownUseCase
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.domain.usecase.NetWorthTrendUseCase
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import com.moneta.app.ui.common.TransactionListItem
import com.moneta.app.ui.common.buildTransactionItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Month-over-month comparison used for the small "spending is up/down" note. */
data class MonthTrend(
    val hasPriorMonthData: Boolean,
    val expenseDeltaPercent: Double?,
    val isExpenseUp: Boolean
)

/** One account with an amount attached, used by the tap-to-expand breakdowns on Home. */
data class AccountAmount(val account: Account, val amount: Money)

/** One budgeted category with money still to spend. */
data class YetToSpendLine(val name: String, val icon: String, val amount: Money)

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasAnyAccounts: Boolean = false,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val isCurrentMonth: Boolean = true,
    val currencyCode: String = "PKR",
    val totalBalance: Money = Money.zero("PKR"),
    val balanceScope: BalanceScope = BalanceScope.ALL,
    val currentSubtotal: Money = Money.zero("PKR"),
    val savingsSubtotal: Money = Money.zero("PKR"),
    val accounts: List<Account> = emptyList(),
    /** Balance at the start of the month when "carry forward" is on, otherwise null. */
    val openingBalance: Money? = null,
    val monthIncome: Money = Money.zero("PKR"),
    val monthExpense: Money = Money.zero("PKR"),
    val recentItems: List<TransactionListItem> = emptyList(),
    val spendingBreakdown: List<CategorySlice> = emptyList(),
    val trend: MonthTrend = MonthTrend(hasPriorMonthData = false, expenseDeltaPercent = null, isExpenseUp = false),
    val topBudgets: List<BudgetProgress> = emptyList(),
    val openingBreakdown: List<AccountAmount> = emptyList(),
    val incomeBreakdown: List<AccountAmount> = emptyList(),
    val currentBalance: Money = Money.zero("PKR"),
    val budgetTotal: Money = Money.zero("PKR"),
    val budgetSpent: Money = Money.zero("PKR"),
    val yetToSpend: Money = Money.zero("PKR"),
    /** Balance expected at the end of the month: today's balance less budgeted money still to be spent. */
    val expectedClosing: Money = Money.zero("PKR"),
    val hiddenCards: Set<String> = emptySet(),
    val cardOrder: List<String> = emptyList(),
    val compactCards: Boolean = false,
    /** Budgeted money still to spend, category by category (largest first). */
    val yetToSpendLines: List<YetToSpendLine> = emptyList(),
    val hideBalance: Boolean = false
) {
    /** Income minus expenses for the chosen month - a plain "net change", not "savings". */
    val netChange: Money get() = monthIncome - monthExpense
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val categoryBreakdownUseCase: CategoryBreakdownUseCase,
    private val netWorthTrendUseCase: NetWorthTrendUseCase,
    private val launchFilter: com.moneta.app.ui.transactions.RecordsLaunchFilter
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = categoryRepository.observeAll()
    private val budgetProgressFlow = budgetRepository.observeProgress()
    private val allBudgetsFlow = budgetRepository.observeAllBudgets()
    private val prefsFlow = settingsRepository.preferences

    val uiState: StateFlow<HomeUiState> = period.flatMapLatest { p ->
        val range = p.range()
        val previous = p.shift(-1).range()
        combine(
            listOf(
                accountsFlow,
                prefsFlow,
                transactionRepository.observeTotalForTypeInRange(TransactionType.INCOME, range, "PKR"),
                transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, range, "PKR"),
                transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, previous, "PKR"),
                transactionRepository.observeRecent(5),
                transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, range),
                budgetProgressFlow,
                categoriesFlow,
                allBudgetsFlow
            )
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            build(
                p = p,
                accounts = values[0] as List<Account>,
                prefs = values[1] as com.moneta.app.data.settings.AppPreferences,
                income = values[2] as Money,
                expense = values[3] as Money,
                previousExpense = values[4] as Money,
                transactions = values[5] as List<Transaction>,
                categoryTotals = values[6] as List<CategoryTotal>,
                budgetProgress = values[7] as List<BudgetProgress>,
                categories = values[8] as List<Category>,
                allBudgets = values[9] as List<com.moneta.app.data.repository.Budget>
            )
        }
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection.copy(mode = PeriodMode.MONTH)
    }

    fun toggleHideBalance() {
        viewModelScope.launch { settingsRepository.setHideBalance(!uiState.value.hideBalance) }
    }

    fun setBalanceScope(scope: BalanceScope) {
        viewModelScope.launch { settingsRepository.setBalanceScope(scope) }
    }

    private suspend fun build(
        p: PeriodSelection,
        accounts: List<Account>,
        prefs: com.moneta.app.data.settings.AppPreferences,
        income: Money,
        expense: Money,
        previousExpense: Money,
        transactions: List<Transaction>,
        categoryTotals: List<CategoryTotal>,
        budgetProgress: List<BudgetProgress>,
        categories: List<Category>,
        allBudgets: List<com.moneta.app.data.repository.Budget>
    ): HomeUiState {
        val currency = prefs.currencyCode
        val range = p.range()
        val breakdown = categoryBreakdownUseCase(com.moneta.app.domain.usecase.rollUp(categoryTotals, categories, prefs.subcategoriesEnabled), expense)
        val dayBefore = range.start.minusDays(1)
        val openingRows = accounts.filter { it.includeInNetWorth }.map { account ->
            val effect = transactionRepository.getAccountNetEffectAsOf(account.id, dayBefore)
            AccountAmount(account, Money(account.openingBalance.minorUnits + effect, currency))
        }
        val opening: Money = Money(openingRows.sumOf { it.amount.minorUnits }, currency)
        val monthTx = transactionRepository.observeInRange(range).first()
        val incomeRows = monthTx.filter { it.type == TransactionType.INCOME }
            .groupBy { it.accountId }
            .mapNotNull { (accountId, list) ->
                val acc = accounts.firstOrNull { it.id == accountId } ?: return@mapNotNull null
                AccountAmount(acc, Money(list.sumOf { it.amount.minorUnits }, currency))
            }
            .sortedByDescending { it.amount.minorUnits }
        val counted = accounts.filter { it.includeInNetWorth }
        val savingsMinor = counted.filter { it.type == com.moneta.app.data.local.entity.AccountType.SAVINGS }
            .sumOf { it.currentBalance.minorUnits }
        val allMinor = counted.sumOf { it.currentBalance.minorUnits }
        val total = Money(
            when (prefs.balanceScope) {
                BalanceScope.ALL -> allMinor
                BalanceScope.SAVINGS -> savingsMinor
                BalanceScope.CURRENT -> allMinor - savingsMinor
            },
            currency
        )
        val prevRange = p.shift(-1).range()
        val prevSpent = if (prefs.rolloverBudgets || prefs.carryOverspend) {
            com.moneta.app.domain.usecase.spentMap(
                transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, prevRange).first(),
                categories,
                prefs.subcategoriesEnabled
            )
        } else {
            emptyMap()
        }
        val effective = com.moneta.app.domain.usecase.effectiveBudgets(
            thisMonth = com.moneta.app.domain.usecase.monthCategoryBudgets(allBudgets, range.start),
            lastMonth = com.moneta.app.domain.usecase.monthCategoryBudgets(allBudgets, prevRange.start),
            lastMonthSpent = prevSpent,
            rollover = prefs.rolloverBudgets,
            carryOverspend = prefs.carryOverspend
        )
        val (unitBudgets, unitSpent) = com.moneta.app.domain.usecase.budgetUnits(
            categories, effective, categoryTotals, prefs.subcategoriesEnabled
        )
        val budgetSummary = com.moneta.app.domain.usecase.summarizeBudgets(
            budgets = unitBudgets,
            spent = unitSpent,
            daysElapsed = java.time.LocalDate.now().dayOfMonth,
            totalDays = range.start.lengthOfMonth(),
            isCurrentMonth = p.containsToday(),
            completed = com.moneta.app.domain.usecase.monthCompletedCategories(allBudgets, range.start)
        )
        val byIdAll = categories.associateBy { it.id }
        fun pathName(c: Category): String {
            if (!prefs.subcategoriesEnabled) return c.name
            val parts = ArrayList<String>()
            var cur: Category? = c
            var guard = 0
            while (cur != null && guard < 20) {
                parts.add(0, cur.name)
                cur = cur.parentCategoryId?.let { byIdAll[it] }
                guard++
            }
            return parts.joinToString(" > ")
        }
        val completedIds = com.moneta.app.domain.usecase.monthCompletedCategories(allBudgets, range.start)
        val yetLines = unitBudgets.mapNotNull { (id, budget) ->
            if (id in completedIds) return@mapNotNull null
            val left = budget - (unitSpent[id] ?: 0L)
            val cat = byIdAll[id]
            if (cat == null || budget <= 0L || left <= 0L) null
            else YetToSpendLine(pathName(cat), cat.icon, Money(left, currency))
        }.sortedByDescending { it.amount.minorUnits }
        val currentBalanceNow = if (p.containsToday()) Money(allMinor, currency) else opening + income - expense
        return HomeUiState(
            isLoading = false,
            hasAnyAccounts = accounts.isNotEmpty(),
            period = p,
            isCurrentMonth = p.containsToday(),
            currencyCode = currency,
            totalBalance = total,
            balanceScope = prefs.balanceScope,
            currentSubtotal = Money(allMinor - savingsMinor, currency),
            savingsSubtotal = Money(savingsMinor, currency),
            accounts = accounts,
            openingBalance = opening,
            openingBreakdown = openingRows,
            incomeBreakdown = incomeRows,
            currentBalance = currentBalanceNow,
            budgetTotal = Money(budgetSummary.totalBudget, currency),
            budgetSpent = Money(budgetSummary.spent, currency),
            yetToSpend = Money(budgetSummary.yetToSpend, currency),
            expectedClosing = if (p.containsToday()) {
                Money(currentBalanceNow.minorUnits - budgetSummary.yetToSpend, currency)
            } else {
                currentBalanceNow
            },
            hiddenCards = prefs.hiddenHomeCards,
            cardOrder = prefs.homeCardOrder,
            compactCards = prefs.compactHome,
            yetToSpendLines = yetLines,
            hideBalance = prefs.hideBalance,
            monthIncome = income,
            monthExpense = expense,
            recentItems = buildTransactionItems(transactions.take(5), accounts, categories),
            spendingBreakdown = breakdown,
            trend = buildTrend(expense, previousExpense),
            topBudgets = budgetProgress.sortedByDescending { it.percentUsed ?: 0.0 }.take(3)
        )
    }

    private fun buildTrend(currentExpense: Money, previousExpense: Money): MonthTrend {
        if (previousExpense.isZero) {
            return MonthTrend(hasPriorMonthData = false, expenseDeltaPercent = null, isExpenseUp = false)
        }
        val deltaPercent = ((currentExpense - previousExpense).percentOf(previousExpense)) ?: return MonthTrend(
            hasPriorMonthData = false, expenseDeltaPercent = null, isExpenseUp = false
        )
        return MonthTrend(
            hasPriorMonthData = true,
            expenseDeltaPercent = kotlin.math.abs(deltaPercent),
            isExpenseUp = deltaPercent > 0
        )
    }

    /** "See all": Records over all time. */
    fun prepareRecordsAll() {
        launchFilter.clear()
        launchFilter.period = PeriodSelection(mode = PeriodMode.ALL)
    }

    /** Tells Records what to show when the user taps a Home figure (the caller then opens Records). */
    fun prepareRecords(type: com.moneta.app.ui.transactions.TypeFilter?) {
        launchFilter.clear()
        launchFilter.type = type
        launchFilter.period = period.value
    }
}
