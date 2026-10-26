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
    val categoryIds: Set<Long> = emptySet(),
    /** Name to show: the plain name, or "Parent > Child" when sub-categories are on. */
    val label: String = category.name,
    val depth: Int = 0,
    val hasChildren: Boolean = false,
    /** True for a parent category: its budget is the total of its sub-categories, not set directly. */
    val derived: Boolean = false
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
    val sortMode: String = "SMART",
    /** The latest earlier month that has budgets, offered as "copy from" when this month has none. */
    val copySourceMonth: LocalDate? = null,
    val copySourceLabel: String? = null,
    /** Last month's spending per top-level category (minor units), used to set budgets automatically. */
    val prevMonthSpent: Map<Long, Long> = emptyMap(),
    val prevMonthLabel: String = "",
    /** Spending in budgeted categories carried forward at this month's pace (income plays no part). */
    val projectedSpend: Money = Money.zero("PKR"),
    /** Projected spend minus total budget: positive means expected to go over budget. */
    val expectedVariance: Money = Money.zero("PKR"),
    /** Budgeted money not yet spent, category by category. */
    val yetToSpend: Money = Money.zero("PKR"),
    /** True when the month on screen has ended, so the figures are final rather than expected. */
    val isClosed: Boolean = false
) {
    val totalRemaining: Money get() = totalBudget - totalSpentOnBudgeted
    val hasAnyBudget: Boolean get() = lines.any { it.budgetId != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val launchFilter: RecordsLaunchFilter,
    private val reportsLaunch: com.moneta.app.ui.reports.ReportsLaunch
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
                val subs = prefs.subcategoriesEnabled
                val countedMap = com.moneta.app.domain.usecase.countedUnder(categories, subs)
                val spentNow = com.moneta.app.domain.usecase.spentMap(totals, categories, subs)
                val spentBefore = com.moneta.app.domain.usecase.spentMap(prevTotals, categories, subs)
                val byId = categories.associateBy { it.id }
                fun labelOf(c: Category): String {
                    if (!subs) return c.name
                    val parts = ArrayList<String>()
                    var cur: Category? = c
                    var guard = 0
                    while (cur != null && guard < 20) {
                        parts.add(0, cur.name)
                        cur = cur.parentCategoryId?.let { byId[it] }
                        guard++
                    }
                    return parts.joinToString(" > ")
                }
                val categoryBudgets = budgets
                    .filter { it.scope == BudgetScope.CATEGORY && it.categoryId != null }
                    .associateBy { it.categoryId }

                val prevCategoryBudgets = prevBudgets
                    .filter { it.scope == BudgetScope.CATEGORY && it.categoryId != null }
                    .associateBy { it.categoryId }

                val kids = if (subs) com.moneta.app.domain.usecase.childIndex(categories) else emptyMap()
                val ownBase = categories.associate { it.id to (categoryBudgets[it.id]?.amount?.minorUnits ?: 0L) }
                val carriedOwn = categories.associate { c ->
                    val base = categoryBudgets[c.id]
                    val prevBase = prevCategoryBudgets[c.id]?.amount?.minorUnits
                    val diff = if (base != null && prevBase != null) prevBase - (spentBefore[c.id] ?: 0L) else 0L
                    val carry = when {
                        diff > 0L && prefs.rolloverBudgets -> diff
                        diff < 0L && prefs.carryOverspend && base != null -> maxOf(diff, -base.amount.minorUnits)
                        else -> 0L
                    }
                    c.id to carry
                }
                val aggBudget = if (subs) com.moneta.app.domain.usecase.treeTotals(categories, ownBase, true) else ownBase
                val aggCarried = if (subs) com.moneta.app.domain.usecase.treeTotals(categories, carriedOwn, true) else carriedOwn
                val aggSpent = if (subs) com.moneta.app.domain.usecase.treeTotals(categories, spentNow, false) else spentNow

                fun makeLine(category: Category, depth: Int): BudgetLine {
                    val hasKids = subs && !kids[category.id].isNullOrEmpty()
                    val budget = if (hasKids) null else categoryBudgets[category.id]
                    val baseMinor: Long? = when {
                        hasKids -> (aggBudget[category.id] ?: 0L).takeIf { it > 0L }
                        budget != null -> budget.amount.minorUnits
                        else -> null
                    }
                    return BudgetLine(
                        category = category,
                        budgetId = budget?.id,
                        alertThresholdPercent = budget?.alertThresholdPercent ?: 90,
                        baseBudget = baseMinor?.let { Money(it, currency) },
                        carriedOver = Money(if (baseMinor != null) (aggCarried[category.id] ?: 0L) else 0L, currency),
                        spent = Money(aggSpent[category.id] ?: 0L, currency),
                        categoryIds = if (subs) {
                            com.moneta.app.domain.usecase.descendantIds(categories, category.id) + category.id
                        } else {
                            countedMap.filter { it.value == category.id }.keys + category.id
                        },
                        label = category.name,
                        depth = depth,
                        hasChildren = hasKids,
                        derived = hasKids
                    )
                }
                val lineOrder = compareByDescending<BudgetLine> { it.baseBudget != null }
                    .thenByDescending { it.percentUsed ?: 0.0 }
                    .thenByDescending { it.spent.minorUnits }
                val lines = ArrayList<BudgetLine>()
                fun emit(list: List<Category>, depth: Int) {
                    val made = list.map { makeLine(it, depth) }
                    (if (prefs.budgetSort == "ORDER") made else made.sortedWith(lineOrder)).forEach { line ->
                        lines.add(line)
                        if (subs && depth < 12) {
                            emit(categories.filter { it.parentCategoryId == line.category.id }, depth + 1)
                        }
                    }
                }
                emit(categories.filter { it.parentCategoryId == null }, 0)

                val ownEffective = categories.mapNotNull { c ->
                    categoryBudgets[c.id]?.let { c.id to (it.amount.minorUnits + (carriedOwn[c.id] ?: 0L)) }
                }.toMap()
                val (unitBudgets, unitSpent) = com.moneta.app.domain.usecase.budgetUnits(
                    categories, ownEffective, totals, subs
                )
                val summary = com.moneta.app.domain.usecase.summarizeBudgets(
                    budgets = unitBudgets,
                    spent = unitSpent,
                    daysElapsed = run {
                        val t = LocalDate.now()
                        if (!t.isBefore(range.start) && t.isBefore(range.endExclusive)) t.dayOfMonth else range.start.lengthOfMonth()
                    },
                    totalDays = range.start.lengthOfMonth(),
                    isCurrentMonth = LocalDate.now().let { !it.isBefore(range.start) && it.isBefore(range.endExclusive) }
                )
                val totalBudget = Money(summary.totalBudget, currency)
                val spentOnBudgeted = Money(summary.spent, currency)
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
                    rolloverOn = prefs.rolloverBudgets || prefs.carryOverspend,
                    sortMode = prefs.budgetSort,
                    copySourceMonth = copySource,
                    copySourceLabel = copySource?.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    prevMonthSpent = spentBefore.filter { it.value > 0L },
                    prevMonthLabel = prevRange.start.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    projectedSpend = Money(summary.projectedSpend, currency),
                    expectedVariance = Money(summary.spent + summary.yetToSpend - summary.totalBudget, currency),
                    yetToSpend = Money(summary.yetToSpend, currency),
                    isClosed = !range.endExclusive.isAfter(LocalDate.now())
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection.copy(mode = PeriodMode.MONTH)
    }

    /** Creates or updates the monthly budget for [line]'s category. Returns false if the amount is invalid. */
    fun saveBudget(line: BudgetLine, amountInput: String): Boolean {
        val cleaned = amountInput.trim()
        // Blank or zero means "no budget this month for this category": an existing budget is removed.
        if (cleaned.isEmpty() || cleaned.toDoubleOrNull() == 0.0) {
            line.budgetId?.let { id -> viewModelScope.launch { budgetRepository.delete(id) } }
            return true
        }
        val amount = Money.fromDecimalInput(cleaned, uiState.value.currencyCode)
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

    fun setSort(mode: String) {
        viewModelScope.launch { settingsRepository.setBudgetSort(mode) }
    }

    fun saveOrder(ids: List<Long>) {
        viewModelScope.launch {
            categoryRepository.saveOrder(ids)
            settingsRepository.setBudgetSort("ORDER")
        }
    }

    suspend fun mostUsedFirst(ids: List<Long>): List<Long> = categoryRepository.mostUsedFirst(ids)

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

    /** Hands a category to Reports so it opens straight on that category's expense analysis for this month. */
    fun prepareReports(line: BudgetLine) {
        reportsLaunch.clear()
        reportsLaunch.categoryId = line.category.id
        reportsLaunch.isIncome = false
        reportsLaunch.period = uiState.value.period
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
