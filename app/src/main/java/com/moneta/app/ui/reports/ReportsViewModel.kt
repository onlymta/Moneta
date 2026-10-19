package com.moneta.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.Budget
import com.moneta.app.data.repository.BudgetRepository
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class ReportTab(val label: String) {
    OVERVIEW("Overview"),
    SPENDING("Spending"),
    INCOME("Income"),
    ACCOUNTS("Accounts")
}

/** A closer look at one spending category: pace, comparison with last period and the outlook. */
data class CategoryInsight(
    val categoryId: Long,
    val name: String,
    val colorHex: String,
    val spent: Money,
    val prevSpent: Money?,
    val avgPerDay: Money,
    /** What the period will total if spending continues at the same daily pace. */
    val projected: Money,
    val budget: Money?,
    val daysElapsed: Int,
    val totalDays: Int,
    val isCurrent: Boolean
)

data class ReportsUiState(
    val isLoading: Boolean = true,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val tab: ReportTab = ReportTab.OVERVIEW,
    val currencyCode: String = "PKR",
    val income: Money = Money.zero("PKR"),
    val expense: Money = Money.zero("PKR"),
    val transactionCount: Int = 0,
    val spendingSlices: List<CategorySlice> = emptyList(),
    val incomeSlices: List<CategorySlice> = emptyList(),
    val openingBalance: Money? = null,
    /** Budgeted money that has not been spent yet (only for a period that has not ended). */
    val pendingBudget: Money = Money.zero("PKR"),
    val insights: Map<Long, CategoryInsight> = emptyMap(),
    val accounts: List<Account> = emptyList(),
    val netWorth: Money = Money.zero("PKR")
) {
    val net: Money get() = income - expense
    val closingBalance: Money? get() = openingBalance?.let { it + net }
    val expectedClosing: Money? get() = closingBalance?.let { it - pendingBudget }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    budgetRepository: BudgetRepository,
    settingsRepository: SettingsRepository,
    private val categoryBreakdownUseCase: CategoryBreakdownUseCase,
    private val netWorthTrendUseCase: NetWorthTrendUseCase
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val tab = MutableStateFlow(ReportTab.OVERVIEW)
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = categoryRepository.observeAll()
    private val budgetsFlow = budgetRepository.observeAllBudgets()
    private val prefsFlow = settingsRepository.preferences

    private data class Controls(val period: PeriodSelection, val tab: ReportTab)

    private val controls = combine(period, tab) { p, t -> Controls(p, t) }

    private data class Sources(
        val transactions: List<Transaction>,
        val accounts: List<Account>,
        val categories: List<com.moneta.app.data.repository.Category>,
        val budgets: List<Budget>
    )

    val uiState: StateFlow<ReportsUiState> = controls.flatMapLatest { c ->
        combine(
            transactionRepository.observeFiltered(range = c.period.range()),
            accountsFlow,
            categoriesFlow,
            budgetsFlow,
            prefsFlow
        ) { transactions, accounts, categories, budgets, prefs ->
            build(c, Sources(transactions, accounts, categories, budgets), prefs.currencyCode)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection
    }

    fun onTabSelected(selected: ReportTab) {
        tab.value = selected
    }

    private suspend fun build(c: Controls, src: Sources, currency: String): ReportsUiState {
        val transactions = src.transactions
        val categories = src.categories
        val range = c.period.range()
        val today = LocalDate.now()
        val incomeTx = transactions.filter { it.type == TransactionType.INCOME }
        val expenseTx = transactions.filter { it.type == TransactionType.EXPENSE }
        val income = Money(incomeTx.sumOf { it.amount.minorUnits }, currency)
        val expense = Money(expenseTx.sumOf { it.amount.minorUnits }, currency)

        fun totalsOf(list: List<Transaction>): List<CategoryTotal> =
            list.groupBy { it.categoryId }.map { (id, rows) -> CategoryTotal(id, rows.sumOf { it.amount.minorUnits }) }

        val spendingSlices = categoryBreakdownUseCase(rollUpToTopLevel(totalsOf(expenseTx), categories), expense)
        val incomeSlices = categoryBreakdownUseCase(rollUpToTopLevel(totalsOf(incomeTx), categories), income)

        val opening: Money? = if (c.period.mode != PeriodMode.ALL) {
            netWorthTrendUseCase(listOf(range.start.minusDays(1)), currency).firstOrNull()?.netWorth
        } else {
            null
        }

        // --- budgets that are set for months inside this period ---
        val periodEnded = !range.endExclusive.isAfter(today)
        val budgetsInRange = src.budgets.filter {
            it.isEnabled && it.period == BudgetPeriod.CUSTOM &&
                it.customPeriodStart != null && it.customPeriodStart in range
        }
        val categoryBudgetByTop = HashMap<Long, Long>()
        val parentOf = categories.associate { it.id to (it.parentCategoryId ?: it.id) }
        budgetsInRange.filter { it.scope == BudgetScope.CATEGORY && it.categoryId != null }.forEach { b ->
            val id = b.categoryId
            if (id != null) {
                val top = parentOf[id] ?: id
                categoryBudgetByTop[top] = (categoryBudgetByTop[top] ?: 0L) + b.amount.minorUnits
            }
        }
        val spentByTop = HashMap<Long, Long>()
        spendingSlices.forEach { slice ->
            val id = slice.categoryId
            if (id != null) spentByTop[id] = slice.amount.minorUnits
        }
        var pendingMinor = 0L
        if (!periodEnded && c.period.mode != PeriodMode.ALL) {
            if (categoryBudgetByTop.isNotEmpty()) {
                categoryBudgetByTop.forEach { entry ->
                    val left = entry.value - (spentByTop[entry.key] ?: 0L)
                    if (left > 0L) pendingMinor += left
                }
            } else {
                val overall = budgetsInRange.filter { it.scope == BudgetScope.OVERALL }.sumOf { it.amount.minorUnits }
                val left = overall - expense.minorUnits
                if (left > 0L) pendingMinor = left
            }
        }

        // --- per-category analysis for the Spending tab ---
        val totalDays = (ChronoUnit.DAYS.between(range.start, range.endExclusive).toInt()).coerceAtLeast(1)
        val isCurrent = today in range
        val elapsed = when {
            isCurrent -> (ChronoUnit.DAYS.between(range.start, today).toInt() + 1).coerceIn(1, totalDays)
            periodEnded -> totalDays
            else -> 1
        }
        val prevTotals: Map<Long, Long> = if (c.period.mode == PeriodMode.ALL || c.period.mode == PeriodMode.CUSTOM) {
            emptyMap()
        } else {
            val prevRange = c.period.shift(-1).range()
            val rows = transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, prevRange).first()
            rollUpToTopLevel(rows, categories).filter { it.categoryId != null }
                .associate { (it.categoryId ?: 0L) to it.totalMinor }
        }
        val hasPrev = c.period.mode != PeriodMode.ALL && c.period.mode != PeriodMode.CUSTOM
        val insights = HashMap<Long, CategoryInsight>()
        spendingSlices.forEach { slice ->
            val id = slice.categoryId
            if (id != null) {
                val spentMinor = slice.amount.minorUnits
                val perDay = spentMinor / elapsed
                val projected = if (isCurrent) spentMinor * totalDays / elapsed else spentMinor
                val budgetMinor = categoryBudgetByTop[id]
                insights[id] = CategoryInsight(
                    categoryId = id,
                    name = slice.name,
                    colorHex = slice.colorHex,
                    spent = slice.amount,
                    prevSpent = if (hasPrev) Money(prevTotals[id] ?: 0L, currency) else null,
                    avgPerDay = Money(perDay, currency),
                    projected = Money(projected, currency),
                    budget = if (budgetMinor != null) Money(budgetMinor, currency) else null,
                    daysElapsed = elapsed,
                    totalDays = totalDays,
                    isCurrent = isCurrent
                )
            }
        }

        return ReportsUiState(
            isLoading = false,
            period = c.period,
            tab = c.tab,
            currencyCode = currency,
            income = income,
            expense = expense,
            transactionCount = transactions.size,
            spendingSlices = spendingSlices,
            incomeSlices = incomeSlices,
            openingBalance = opening,
            pendingBudget = Money(pendingMinor, currency),
            insights = insights,
            accounts = src.accounts,
            netWorth = Money(src.accounts.filter { it.includeInNetWorth }.sumOf { it.currentBalance.minorUnits }, currency)
        )
    }
}
