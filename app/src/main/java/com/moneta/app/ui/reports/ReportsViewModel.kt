package com.moneta.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
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
import com.moneta.app.domain.usecase.countedUnder
import com.moneta.app.domain.usecase.rollUp
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import com.moneta.app.ui.common.TransactionListItem
import com.moneta.app.ui.common.buildTransactionItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class ReportTab(val label: String) {
    OVERVIEW("Overview"),
    SPENDING("Expenses"),
    INCOME("Income")
}

/** One earlier period in a category's history: what was recorded, and (for expenses) what was budgeted. */
data class PeriodRow(
    val label: String,
    val amount: Money,
    val budget: Money?
) {
    val percentUsed: Double? get() = budget?.let { if (it.minorUnits > 0L) amount.minorUnits * 100.0 / it.minorUnits else null }
    /** Positive: over budget by this much. Negative: unused budget. */
    val excess: Money? get() = budget?.let { Money(amount.minorUnits - it.minorUnits, it.currencyCode) }
}

/** Everything the category detail view shows, for the category and period chosen in Reports. */
data class CategoryAnalysis(
    val categoryId: Long,
    val name: String,
    val colorHex: String,
    val isIncome: Boolean,
    val periodLabel: String,
    val isCurrent: Boolean,
    val amount: Money,
    /** Share of all expenses (or income) in the period, in percent. */
    val sharePercent: Double,
    val typeTotal: Money,
    val budget: Money?,
    val budgetIsProRated: Boolean,
    val history: List<PeriodRow>,
    val txCount: Int,
    val lowest: Money,
    val highest: Money,
    val averagePerTransaction: Money,
    val activeDays: Int,
    val periodDays: Int,
    val averagePerDay: Money,
    val suggested: Money?,
    val suggestedNote: String,
    val items: List<TransactionListItem>
) {
    val percentUsed: Double? get() = budget?.let { if (it.minorUnits > 0L) amount.minorUnits * 100.0 / it.minorUnits else null }
    val excess: Money? get() = budget?.let { Money(amount.minorUnits - it.minorUnits, it.currencyCode) }
}

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
    val pendingBudget: Money = Money.zero("PKR")
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
    private val netWorthTrendUseCase: NetWorthTrendUseCase,
    private val reportsLaunch: ReportsLaunch
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val tab = MutableStateFlow(ReportTab.OVERVIEW)
    private val selected = MutableStateFlow<Long?>(null)
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = categoryRepository.observeAll()
    private val budgetsFlow = budgetRepository.observeAllBudgets()
    private val prefsFlow = settingsRepository.preferences

    private data class Controls(val period: PeriodSelection, val tab: ReportTab)

    private val controls = combine(period, tab) { p, t -> Controls(p, t) }

    private data class Sources(
        val transactions: List<Transaction>,
        val categories: List<com.moneta.app.data.repository.Category>,
        val budgets: List<Budget>
    )

    val uiState: StateFlow<ReportsUiState> = controls.flatMapLatest { c ->
        combine(
            transactionRepository.observeFiltered(range = c.period.range()),
            categoriesFlow,
            budgetsFlow,
            prefsFlow
        ) { transactions, categories, budgets, prefs ->
            build(c, Sources(transactions, categories, budgets), prefs.currencyCode, prefs.subcategoriesEnabled)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    /** Id of the category open in the detail view, or null when the category list is showing. */
    val selectedCategoryId: StateFlow<Long?> = selected

    /** The analysis of the selected category for the chosen period; follows the period as it changes. */
    val analysis: StateFlow<CategoryAnalysis?> = combine(period, tab, selected) { p, t, id -> Triple(p, t, id) }
        .flatMapLatest { (p, t, id) ->
            if (id == null || t == ReportTab.OVERVIEW) flowOf(null) else analysisFlow(p, t == ReportTab.INCOME, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection
    }

    fun onTabSelected(selectedTab: ReportTab) {
        if (selectedTab != tab.value) selected.value = null
        tab.value = selectedTab
    }

    fun selectCategory(id: Long?) {
        selected.value = id
    }

    /** Applies a request from another screen (e.g. long-pressing a budget) to open a category's analysis. */
    fun applyLaunch() {
        val id = reportsLaunch.categoryId ?: return
        reportsLaunch.period?.let { period.value = it }
        tab.value = if (reportsLaunch.isIncome) ReportTab.INCOME else ReportTab.SPENDING
        selected.value = id
        reportsLaunch.clear()
    }

    private fun analysisFlow(p: PeriodSelection, isIncome: Boolean, categoryId: Long): Flow<CategoryAnalysis?> {
        val shiftable = p.canShift
        val ranges: List<DateRange> = if (shiftable) (0..6).map { p.shift(-it).range() } else listOf(p.range())
        val whole = DateRange(ranges.last().start, ranges.first().endExclusive)
        return combine(
            transactionRepository.observeInRange(whole),
            categoriesFlow,
            accountsFlow,
            budgetsFlow,
            prefsFlow
        ) { all, categories, accounts, budgets, prefs ->
            val currency = prefs.currencyCode
            val type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
            val counted = countedUnder(categories, prefs.subcategoriesEnabled)
            val ids = counted.filter { it.value == categoryId }.keys + categoryId
            val category = categories.firstOrNull { it.id == categoryId } ?: return@combine null
            val typed = all.filter { it.type == type }
            fun inRange(list: List<Transaction>, r: DateRange) = list.filter { it.date in r }
            val mine = typed.filter { it.categoryId != null && it.categoryId in ids }

            val currentRange = ranges.first()
            val currentTx = inRange(mine, currentRange)
            val amount = currentTx.sumOf { it.amount.minorUnits }
            val typeTotal = inRange(typed, currentRange).sumOf { it.amount.minorUnits }

            val monthlyBudgets = budgets.filter {
                !isIncome && it.isEnabled && it.scope == BudgetScope.CATEGORY && it.categoryId != null &&
                    it.period == BudgetPeriod.CUSTOM && it.customPeriodStart != null &&
                    (counted[it.categoryId] ?: it.categoryId) == categoryId
            }
            // Months and years add up the monthly budgets inside them; a day or a week takes its share of the month.
            val proRated = p.mode == PeriodMode.DAY || p.mode == PeriodMode.WEEK
            fun budgetFor(r: DateRange): Long? {
                if (isIncome) return null
                if (proRated) {
                    val monthStart = r.start.withDayOfMonth(1)
                    val monthly = monthlyBudgets.firstOrNull { it.customPeriodStart == monthStart } ?: return null
                    val days = ChronoUnit.DAYS.between(r.start, r.endExclusive).coerceAtLeast(1)
                    return monthly.amount.minorUnits * days / monthStart.lengthOfMonth()
                }
                val inside = monthlyBudgets.filter { it.customPeriodStart != null && it.customPeriodStart in r }
                return if (inside.isEmpty()) null else inside.sumOf { it.amount.minorUnits }
            }

            val labelOf = { r: DateRange -> PeriodSelection(p.mode, r.start, r.start, r.endExclusive.minusDays(1)).label() }
            val history = if (shiftable) {
                ranges.drop(1).map { r ->
                    PeriodRow(
                        labelOf(r),
                        Money(inRange(mine, r).sumOf { it.amount.minorUnits }, currency),
                        budgetFor(r)?.let { Money(it, currency) }
                    )
                }
            } else {
                emptyList()
            }

            val today = LocalDate.now()
            val isCurrent = today in currentRange
            val totalDays = ChronoUnit.DAYS.between(currentRange.start, currentRange.endExclusive).toInt().coerceAtLeast(1)
            val elapsed = when {
                isCurrent -> (ChronoUnit.DAYS.between(currentRange.start, today).toInt() + 1).coerceIn(1, totalDays)
                !currentRange.endExclusive.isAfter(today) -> totalDays
                else -> 1
            }
            val sorted = currentTx.sortedWith(
                compareByDescending<Transaction> { it.date }.thenByDescending { it.timeMinutesSinceMidnight }
            )
            val activeDays = currentTx.map { it.date }.distinct().size
            val amounts = currentTx.map { it.amount.minorUnits }

            val thisEstimate = if (isCurrent) amount * totalDays / elapsed else amount
            val samples = history.map { it.amount.minorUnits }.filter { it > 0L } +
                (if (thisEstimate > 0L) listOf(thisEstimate) else emptyList())
            val suggested = if (samples.isEmpty() || isIncome) null else Money(samples.sum() / samples.size, currency)
            val note = when {
                isIncome -> ""
                samples.isEmpty() -> "Not enough spending history yet."
                else -> "Average of " + history.count { it.amount.minorUnits > 0L } +
                    " earlier period(s) and " + (if (isCurrent) "this period's pace." else "this period.")
            }

            CategoryAnalysis(
                categoryId = categoryId,
                name = category.name,
                colorHex = category.colorHex,
                isIncome = isIncome,
                periodLabel = p.label(),
                isCurrent = isCurrent,
                amount = Money(amount, currency),
                sharePercent = if (typeTotal > 0L) amount * 100.0 / typeTotal else 0.0,
                typeTotal = Money(typeTotal, currency),
                budget = budgetFor(currentRange)?.let { Money(it, currency) },
                budgetIsProRated = proRated,
                history = history,
                txCount = currentTx.size,
                lowest = Money(amounts.minOrNull() ?: 0L, currency),
                highest = Money(amounts.maxOrNull() ?: 0L, currency),
                averagePerTransaction = Money(if (amounts.isEmpty()) 0L else amount / amounts.size, currency),
                activeDays = activeDays,
                periodDays = elapsed,
                averagePerDay = Money(amount / elapsed, currency),
                suggested = suggested,
                suggestedNote = note,
                items = buildTransactionItems(sorted, accounts, categories)
            )
        }
    }

    private suspend fun build(c: Controls, src: Sources, currency: String, subs: Boolean): ReportsUiState {
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

        val spendingSlices = categoryBreakdownUseCase(rollUp(totalsOf(expenseTx), categories, subs), expense)
        val incomeSlices = categoryBreakdownUseCase(rollUp(totalsOf(incomeTx), categories, subs), income)

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
        val countedMap = countedUnder(categories, subs)
        val categoryBudgetByTop = HashMap<Long, Long>()
        budgetsInRange.filter { it.scope == BudgetScope.CATEGORY && it.categoryId != null }.forEach { b ->
            val id = b.categoryId
            if (id != null) {
                val top = countedMap[id] ?: id
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
            pendingBudget = Money(pendingMinor, currency)
        )
    }
}
