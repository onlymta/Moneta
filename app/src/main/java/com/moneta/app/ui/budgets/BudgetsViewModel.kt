package com.moneta.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.CategoryTotal
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val spent: Money
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
    val rolloverOn: Boolean = false
) {
    val totalRemaining: Money get() = totalBudget - totalSpentOnBudgeted
    val hasAnyBudget: Boolean get() = lines.any { it.budgetId != null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val currencyFlow = settingsRepository.preferences
    private val categoriesFlow = categoryRepository.observeByType(CategoryType.EXPENSE)
    private val budgetsFlow = budgetRepository.observeEnabledBudgets()

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
            ) { budgets, categories, totals, prevTotals, totalExpense ->
                val currency = prefs.currencyCode
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

                val lines = categories
                    .filter { it.parentCategoryId == null }
                    .map { category ->
                        val budget = categoryBudgets[category.id]
                        val base = budget?.let { Money(it.amountMinor, currency) }
                        val carried = if (prefs.rolloverBudgets && base != null) {
                            val left = base.minorUnits - (spentBefore[category.id] ?: 0L)
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
                            spent = Money(spentNow[category.id] ?: 0L, currency)
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
                        OverallBudgetLine(it.id, Money(it.amountMinor, currency), totalExpense)
                    },
                    totalBudget = totalBudget,
                    totalSpentOnBudgeted = spentOnBudgeted,
                    spentOutsideBudgets = if (outside.isNegative) Money.zero(currency) else outside,
                    rolloverOn = prefs.rolloverBudgets
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection.copy(mode = PeriodMode.MONTH)
    }

    /** Creates or updates the monthly budget for [line]'s category. Returns false if the amount is invalid. */
    fun saveBudget(line: BudgetLine, amountInput: String): Boolean {
        val amount = Money.fromDecimalInput(amountInput, uiState.value.currencyCode)
        if (amount == null || amount.isZero) return false
        viewModelScope.launch {
            val existingId = line.budgetId
            if (existingId != null) {
                budgetRepository.updateAmount(existingId, amount, line.alertThresholdPercent)
            } else {
                budgetRepository.createCategoryBudget(line.category.id, amount)
            }
        }
        return true
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch { budgetRepository.delete(budgetId) }
    }
}
