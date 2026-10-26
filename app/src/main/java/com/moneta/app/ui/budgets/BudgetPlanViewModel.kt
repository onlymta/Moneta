package com.moneta.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.domain.usecase.spentMap
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One budgeted category when looking back at a month, with what next month's budget should be to stay on plan. */
data class PlanRow(
    val categoryId: Long,
    val name: String,
    val budgetMinor: Long,
    val spentMinor: Long,
    /** Budget minus the difference: spent too much and next month's budget shrinks to pay it back. */
    val suggestedMinor: Long,
    val selected: Boolean
) {
    val differenceMinor: Long get() = spentMinor - budgetMinor
    val changeMinor: Long get() = suggestedMinor - budgetMinor
}

data class BudgetPlanUiState(
    val isLoading: Boolean = true,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val monthLabel: String = "",
    val nextMonthLabel: String = "",
    val currencyCode: String = "PKR",
    /** Income the user said to expect each month (0 when not set). */
    val plannedIncome: Money = Money.zero("PKR"),
    val savingsTarget: Money = Money.zero("PKR"),
    val actualIncome: Money = Money.zero("PKR"),
    val totalBudget: Money = Money.zero("PKR"),
    val totalSpentOnBudgeted: Money = Money.zero("PKR"),
    val totalSpentAll: Money = Money.zero("PKR"),
    val rows: List<PlanRow> = emptyList(),
    val raiseWhenUnder: Boolean = false,
    val nextTotalBudget: Money = Money.zero("PKR"),
    val message: String? = null
) {
    /** The income the plan works from: what was typed in, or this month's real income if nothing was typed. */
    val planIncome: Money get() = if (plannedIncome.isZero) actualIncome else plannedIncome
    val availableToSpend: Money get() = planIncome - savingsTarget
    val actualSaved: Money get() = actualIncome - totalSpentAll
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetPlanViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH).shift(-1))
    private val deselected = MutableStateFlow<Set<Long>>(emptySet())
    private val raise = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")

    private data class Base(
        val period: PeriodSelection,
        val currency: String,
        val plannedIncome: Long,
        val savings: Long,
        val actualIncome: Money,
        val totalSpentAll: Money,
        val budgetRows: List<Triple<Long, String, Pair<Long, Long>>>
    )

    private val base = period.flatMapLatest { p ->
        val range = p.range()
        combine(
            budgetRepository.observeAllBudgets(),
            categoryRepository.observeByType(CategoryType.EXPENSE),
            transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, range),
            settingsRepository.preferences
        ) { allBudgets, categories, totals, prefs ->
            val currency = prefs.currencyCode
            val start = range.start
            val own = allBudgets.filter {
                it.isEnabled && it.scope == BudgetScope.CATEGORY && it.categoryId != null &&
                    it.period == BudgetPeriod.CUSTOM && it.customPeriodStart == start
            }
            val spent = spentMap(totals, categories, prefs.subcategoriesEnabled)
            val byId = categories.associateBy { it.id }
            val rows = own.mapNotNull { b ->
                val id = b.categoryId ?: return@mapNotNull null
                val name = byId[id]?.name ?: return@mapNotNull null
                Triple(id, name, b.amount.minorUnits to (spent[id] ?: 0L))
            }.sortedBy { it.second.lowercase() }
            Base(
                period = p,
                currency = currency,
                plannedIncome = prefs.planIncomeMinor,
                savings = prefs.planSavingsMinor,
                actualIncome = Money.zero(currency),
                totalSpentAll = Money(totals.sumOf { it.totalMinor }, currency),
                budgetRows = rows
            )
        }.flatMapLatest { b ->
            transactionRepository.observeTotalForTypeInRange(TransactionType.INCOME, range, b.currency)
                .map { income -> b.copy(actualIncome = income) }
        }
    }

    val uiState: StateFlow<BudgetPlanUiState> = combine(base, deselected, raise, message) { b, off, up, msg ->
        val currency = b.currency
        val rows = b.budgetRows.map { (id, name, pair) ->
            val budget = pair.first
            val spent = pair.second
            val diff = spent - budget
            val suggested = when {
                diff > 0L -> (budget - diff).coerceAtLeast(0L)
                diff < 0L && up -> budget - diff
                else -> budget
            }
            PlanRow(id, name, budget, spent, suggested, selected = id !in off && suggested != budget)
        }
        val totalBudget = b.budgetRows.sumOf { it.third.first }
        val spentBudgeted = b.budgetRows.sumOf { it.third.second }
        val nextTotal = rows.sumOf { if (it.selected) it.suggestedMinor else it.budgetMinor }
        BudgetPlanUiState(
            isLoading = false,
            period = b.period,
            monthLabel = b.period.range().start.format(monthFmt),
            nextMonthLabel = b.period.range().start.plusMonths(1).format(monthFmt),
            currencyCode = currency,
            plannedIncome = Money(b.plannedIncome, currency),
            savingsTarget = Money(b.savings, currency),
            actualIncome = b.actualIncome,
            totalBudget = Money(totalBudget, currency),
            totalSpentOnBudgeted = Money(spentBudgeted, currency),
            totalSpentAll = b.totalSpentAll,
            rows = rows,
            raiseWhenUnder = up,
            nextTotalBudget = Money(nextTotal, currency),
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetPlanUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection.copy(mode = PeriodMode.MONTH)
        deselected.value = emptySet()
        message.value = null
    }

    fun setRaiseWhenUnder(on: Boolean) {
        raise.value = on
    }

    fun toggleRow(categoryId: Long, selected: Boolean) {
        deselected.update { if (selected) it - categoryId else it + categoryId }
    }

    /** Saves the income and savings plan. Returns false if an amount can't be read. */
    fun savePlan(incomeInput: String, savingsInput: String): Boolean {
        val currency = uiState.value.currencyCode
        fun parse(text: String): Long? {
            val t = text.trim()
            if (t.isEmpty()) return 0L
            return Money.fromDecimalInput(t, currency)?.minorUnits
        }
        val income = parse(incomeInput) ?: return false
        val savings = parse(savingsInput) ?: return false
        viewModelScope.launch { settingsRepository.setPlan(income, savings) }
        return true
    }

    /** Writes the chosen suggested amounts as next month's budgets. */
    fun applyToNextMonth() {
        val state = uiState.value
        val nextMonth = state.period.range().start.plusMonths(1)
        val chosen = state.rows.filter { it.selected }
        if (chosen.isEmpty()) {
            message.value = "Nothing to change. Every budget already matches."
            return
        }
        viewModelScope.launch {
            chosen.forEach { row ->
                budgetRepository.setCategoryBudgetForMonth(
                    row.categoryId,
                    Money(row.suggestedMinor, state.currencyCode),
                    nextMonth
                )
            }
            message.value = "Updated ${chosen.size} budget(s) for ${state.nextMonthLabel}."
        }
    }
}
