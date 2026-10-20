package com.moneta.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.BudgetStatus
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import com.moneta.app.ui.transactions.RecordsLaunchFilter
import com.moneta.app.ui.transactions.TypeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One expense category on the Budgets page, with or without a budget set for the chosen month. */
data class BudgetLine(
    val category: Category,
    val budgetId: Long?,
    val alertThresholdPercent: Int,
    /** The base monthly amount the user set (null when there is no budget). */
    val baseBudget: Money?,
    /** Unspent budget carried in from last month (zero unless roll-over is on). */
    val carriedOver: Money,
    val spent: Money,
    /** This category plus its subcategories, used to open its transactions. */
    val categoryIds: Set<Long> = emptySet()
) {
    val effectiveBudget: Money? get() = baseBudget?.let { it + carriedOver }
    val remaining: Money? get() = effectiveBudget?.let { it - spent }
    val percentUsed: Double? get() = effectiveBudget?.let { spent.percentOf(it) }
    val status: BudgetStatus?
        get() {
            val pct = percentUsed ?: return if (remaining?.isNegative == true) BudgetStatus.OVER else null
            return when {
                pct >= 100.0 -> BudgetStatus.OVER
                pct >= alertThresholdPercent -> BudgetStatus.NEAR
                else -> BudgetStatus.UNDER
            }
        }
}

data class OverallBudgetLine(
    val budgetId: Long,
    val budget: Money,
    val spent: Money
) {
    val remaining: Money get() = budget - spent
    val percentUsed: Double? get() = spent.percentOf(budget)
}

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val currencyCode: String = "PKR",
    val lines: List<BudgetLine> = emptyList(),
    val overall: OverallBudgetLine? = null,
    val totalBudget: Money = Money.zero("PKR"),
    val totalSpentOnBudgeted: Money = Money.zero("PKR"),
    val spentOutsideBudgets: Money = Money.zero("PKR"),
    val rolloverOn: Boolean = false,
    /** The latest earlier month that has budgets, offered as "copy from" when this month has none. */
    val copySourceMonth: LocalDate? = null,
    val copySourceLabel: String? = null,
    /** Last month's spending per top-level category (minor units), used to set budgets automatically. */
    val prevMonthSpent: Map<Long, Long> = emptyMap(),
    val prevMonthLabel: String = "",
    val monthIncome: Money = Money.zero("PKR"),
    val totalExpense: Money = Money.zero("PKR"),
    /** Budgeted money not yet spent: for each budgeted category, what is left of its budget (never negative). */
    val yetToSpend: Money = Money.zero("PKR"),
    /** True when the month on screen has already ended, so the figure is actual savings rather than expected. */
    val isClosed: Boolean = false
) {
    /** Income minus everything spent so far minus budgeted money still to be spent. */
    val expectedSavings: Money get() = monthIncome - totalExpense - yetToSpend
    val actualSavings: Money get() = monthIncome - totalExpense
    val totalRemaining: Money get() = totalBudget - totalSpentOnBudgeted
    val hasAnyBudget: Boolean get() = lines.any { it.budgetId != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
    private val launchFilter: RecordsLaunchFilter
) : ViewModel() {

    init {
        viewModelScope.launch { budgetRepository.convertLegacyMonthlyBudgets() }
    }

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val currencyFlow = settingsRepository.preferences
    private val categoriesFlow = categoryRepository.observeByType(CategoryType.EXPENSE)
    private val budgetsFlow = budgetRepository.observeAllBudgets()

    val uiState: StateFlow<BudgetsUiState> = combine(period, currencyFlow) { p, prefs -> p to prefs }
        .flatMapLatest { (p, prefs) ->
            val range = p.range()
            val prevRange = p.shift(-1).range()
            val incomeFlow = transactionRepository.observeTotalForTypeInRange(TransactionType.INCOME, range, prefs.currencyCode)
            combine(
                budgetsFlow,
                categoriesFlow,
                transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, range),
                transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, prevRange),
                transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, range, prefs.currencyCode)
            ) { allBudgets, categories, totals, prevTotals, totalExpense ->
                val currency = prefs.currencyCode
                fun forMonth(start: LocalDate) = allBudgets.filter {
                    it.isEnabled && it.period == BudgetPeriod.CUSTOM && it.customPeriodStart == start
                }
                val budgets = forMonth(range.start)
                val prevBudgets = forMonth(prevRange.start)
                val copySource = allBudgets
                    .filter { it.period == BudgetPeriod.CUSTOM }
                    .mapNotNull { it.customPeriodStart }
                    .filter { it.isBefore(range.start) }
                    .maxByOrNull { it.toEpochDay() }
                val parentOf = categories.associate { it.id to (it.parentCategoryId ?: it.id) }

                fun spentByTop(rows: List<CategoryTotal>): Map<Long, Long> {
                    val result = HashMap<Long, Long>()
                    rows.forEach { row ->
                        val id = row.categoryId
                        if (id != null) {
                            val top = parentOf[id] ?: id
                            result[top] = (result[top] ?: 0L) + row.totalMinor
                        }
                    }
                    return result
                }

                val spentNow = spentByTop(totals)
                val spentBefore = spentByTop(prevTotals)
                val categoryBudgets = budgets
                    .filter { it.scope == BudgetScope.CATEGORY && it.categoryId != null }
                    .associateBy { it.categoryId }

                val prevCategoryBudgets = prevBudgets
                    .filter { it.scope == BudgetScope.CATEGORY && it.categoryId != null }
                    .associateBy { it.categoryId }

                val lines = categories
                    .filter { it.parentCategoryId == null }
                    .map { category ->
                        val budget = categoryBudgets[category.id]
                        val base = budget?.let { Money(it.amount.minorUnits, currency) }
                        val prevBase = prevCategoryBudgets[category.id]?.amount?.minorUnits
                        val carried = if (prefs.rolloverBudgets && base != null && prevBase != null) {
                            val left = prevBase - (spentBefore[category.id] ?: 0L)
                            Money(if (left > 0) left else 0L, currency)
                        } else {
                            Money.zero(currency)
                        }
                        BudgetLine(
                            category = category,
                            budgetId = budget?.id,
                            alertThresholdPercent = budget?.alertThresholdPercent ?: 90,
                            baseBudget = base,
                            carriedOver = carried,
                            spent = Money(spentNow[category.id] ?: 0L, currency),
                            categoryIds = setOf(category.id) +
                                categories.filter { it.parentCategoryId == category.id }.map { it.id }
                        )
                    }
                    .sortedWith(
                        compareByDescending<BudgetLine> { it.budgetId != null }
                            .thenByDescending { it.percentUsed ?: 0.0 }
                            .thenByDescending { it.spent.minorUnits }
                    )

                val budgeted = lines.filter { it.budgetId != null }
                val totalBudget = Money(budgeted.sumOf { it.effectiveBudget?.minorUnits ?: 0L }, currency)
                val spentOnBudgeted = Money(budgeted.sumOf { it.spent.minorUnits }, currency)
                val outside = totalExpense - spentOnBudgeted
                val overallBudget = budgets.firstOrNull { it.scope == BudgetScope.OVERALL }

                BudgetsUiState(
                    isLoading = false,
                    period = p,
                    currencyCode = currency,
                    lines = lines,
                    overall = overallBudget?.let {
                        OverallBudgetLine(it.id, Money(it.amount.minorUnits, currency), totalExpense)
                    },
                    totalBudget = totalBudget,
                    totalSpentOnBudgeted = spentOnBudgeted,
                    spentOutsideBudgets = if (outside.isNegative) Money.zero(currency) else outside,
                    rolloverOn = prefs.rolloverBudgets,
                    copySourceMonth = copySource,
                    copySourceLabel = copySource?.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    prevMonthSpent = spentBefore.filter { it.value > 0L },
                    prevMonthLabel = prevRange.start.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    totalExpense = totalExpense,
                    yetToSpend = Money(
                        budgeted.sumOf { line -> maxOf(0L, line.remaining?.minorUnits ?: 0L) },
                        currency
                    ),
                    isClosed = !range.endExclusive.isAfter(LocalDate.now())
                )
            }.combine(incomeFlow) { st, inc -> st.copy(monthIncome = inc) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection.copy(mode = PeriodMode.MONTH)
    }

    /** Creates or updates the monthly budget for [line]'s category. Returns false if the amount is invalid. */
    fun saveBudget(line: BudgetLine, amountInput: String): Boolean {
        val amount = Money.fromDecimalInput(amountInput, uiState.value.currencyCode)
        if (amount == null || amount.isZero) return false
        val monthStart = uiState.value.period.range().start
        viewModelScope.launch {
            val existingId = line.budgetId
            if (existingId != null) {
                budgetRepository.updateAmount(existingId, amount, line.alertThresholdPercent)
            } else {
                budgetRepository.createCategoryBudgetForMonth(line.category.id, amount, monthStart)
            }
        }
        return true
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch { budgetRepository.delete(budgetId) }
    }

    /** Copies the budgets of the latest earlier month that has any into the month on screen. */
    fun copyFromPrevious() {
        val state = uiState.value
        val source = state.copySourceMonth ?: return
        val target = state.period.range().start
        viewModelScope.launch { budgetRepository.copyBudgets(source, target) }
    }

    /** Sets this month's budgets to what was actually spent in each category last month. */
    fun autoSetFromPreviousSpending() {
        val state = uiState.value
        val target = state.period.range().start
        val spent = state.prevMonthSpent
        if (spent.isEmpty()) return
        val currency = state.currencyCode
        viewModelScope.launch {
            spent.forEach { entry ->
                budgetRepository.createCategoryBudgetForMonth(entry.key, Money(entry.value, currency), target)
            }
        }
    }

    /** Hands a category (and its subcategories) to Records so the user sees its transactions for this month. */
    fun prepareRecords(line: BudgetLine) {
        launchFilter.clear()
        launchFilter.type = TypeFilter.EXPENSE
        launchFilter.period = uiState.value.period
        launchFilter.categoryIds = line.categoryIds
        launchFilter.categoryTitle = line.category.name
    }
}
