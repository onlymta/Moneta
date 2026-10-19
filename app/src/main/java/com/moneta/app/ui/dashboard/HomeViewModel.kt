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
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.domain.usecase.CategoryBreakdownUseCase
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.domain.usecase.NetWorthTrendUseCase
import com.moneta.app.domain.usecase.rollUpToTopLevel
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Month-over-month comparison used for the small "spending is up/down" note. */
data class MonthTrend(
    val hasPriorMonthData: Boolean,
    val expenseDeltaPercent: Double?,
    val isExpenseUp: Boolean
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasAnyAccounts: Boolean = false,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val isCurrentMonth: Boolean = true,
    val currencyCode: String = "PKR",
    val totalBalance: Money = Money.zero("PKR"),
    val accounts: List<Account> = emptyList(),
    /** Balance at the start of the month when "carry forward" is on, otherwise null. */
    val openingBalance: Money? = null,
    val monthIncome: Money = Money.zero("PKR"),
    val monthExpense: Money = Money.zero("PKR"),
    val recentItems: List<TransactionListItem> = emptyList(),
    val spendingBreakdown: List<CategorySlice> = emptyList(),
    val trend: MonthTrend = MonthTrend(hasPriorMonthData = false, expenseDeltaPercent = null, isExpenseUp = false),
    val topBudgets: List<BudgetProgress> = emptyList()
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
    settingsRepository: SettingsRepository,
    private val categoryBreakdownUseCase: CategoryBreakdownUseCase,
    private val netWorthTrendUseCase: NetWorthTrendUseCase,
    private val launchFilter: com.moneta.app.ui.transactions.RecordsLaunchFilter
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = categoryRepository.observeAll()
    private val budgetProgressFlow = budgetRepository.observeProgress()
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
                categoriesFlow
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
                categories = values[8] as List<Category>
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection.copy(mode = PeriodMode.MONTH)
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
        categories: List<Category>
    ): HomeUiState {
        val currency = prefs.currencyCode
        val range = p.range()
        val breakdown = categoryBreakdownUseCase(rollUpToTopLevel(categoryTotals, categories), expense)
        val opening: Money? = if (prefs.carryForwardBalance) {
            netWorthTrendUseCase(listOf(range.start.minusDays(1)), currency).firstOrNull()?.netWorth
        } else {
            null
        }
        val total = Money(
            accounts.filter { it.includeInNetWorth }.sumOf { it.currentBalance.minorUnits },
            currency
        )
        return HomeUiState(
            isLoading = false,
            hasAnyAccounts = accounts.isNotEmpty(),
            period = p,
            isCurrentMonth = p.containsToday(),
            currencyCode = currency,
            totalBalance = total,
            accounts = accounts,
            openingBalance = opening,
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
