package com.moneta.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One expense category and the amount given to it this month. */
data class PlanAllocRow(val categoryId: Long, val name: String, val amountMinor: Long)

data class BudgetPlanUiState(
    val isLoading: Boolean = true,
    val currencyCode: String = "PKR",
    val monthLabel: String = "",
    val income: Money = Money.zero("PKR"),
    val savings: Money = Money.zero("PKR"),
    val rows: List<PlanAllocRow> = emptyList(),
    val assigned: Money = Money.zero("PKR"),
    /** Overspending in a category last month is taken off that category's budget this month. */
    val reduceOverspend: Boolean = false,
    /** Money left unspent in a category last month is added to that category's budget this month. */
    val addUnderspend: Boolean = false,
    val message: String? = null
) {
    /** What is left to spend after the savings target. */
    val spending: Money get() = income - savings
    val left: Money get() = spending - assigned
}

private const val MONTHS_AHEAD = 12

@HiltViewModel
class BudgetPlanViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val thisMonth: LocalDate get() = LocalDate.now().withDayOfMonth(1)

    val uiState: StateFlow<BudgetPlanUiState> = combine(
        budgetRepository.observeAllBudgets(),
        categoryRepository.observeByType(CategoryType.EXPENSE),
        settingsRepository.preferences,
        message
    ) { budgets, categories, prefs, msg ->
        val currency = prefs.currencyCode
        val month = thisMonth
        val amounts = budgets
            .filter {
                it.isEnabled && it.scope == BudgetScope.CATEGORY && it.categoryId != null &&
                    it.period == BudgetPeriod.CUSTOM && it.customPeriodStart == month
            }
            .associate { it.categoryId!! to it.amount.minorUnits }
        val byId = categories.associateBy { it.id }
        val subs = prefs.subcategoriesEnabled
        val hasChildren = categories.mapNotNull { it.parentCategoryId }.toSet()
        // Budgets sit on the lowest level: with sub-categories on, a parent's budget is the total of its children.
        val targets = if (subs) categories.filter { it.id !in hasChildren } else categories.filter { it.parentCategoryId == null }
        fun nameOf(id: Long): String {
            val parts = ArrayList<String>()
            var cur = byId[id]
            var guard = 0
            while (cur != null && guard < 20) {
                parts.add(0, cur.name)
                cur = if (subs) cur.parentCategoryId?.let { byId[it] } else null
                guard++
            }
            return parts.joinToString(" > ")
        }
        val rows = targets.map { PlanAllocRow(it.id, nameOf(it.id), amounts[it.id] ?: 0L) }
        BudgetPlanUiState(
            isLoading = false,
            currencyCode = currency,
            monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            income = Money(prefs.planIncomeMinor, currency),
            savings = Money(prefs.planSavingsMinor, currency),
            rows = rows,
            assigned = Money(rows.sumOf { it.amountMinor }, currency),
            reduceOverspend = prefs.carryOverspend,
            addUnderspend = prefs.rolloverBudgets,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetPlanUiState())

    private fun parse(text: String, currency: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return 0L
        return Money.fromDecimalInput(t, currency)?.minorUnits
    }

    /** Saves expected income and the savings target. They apply to every month from now on. */
    fun savePlan(incomeInput: String, savingsInput: String): Boolean {
        val currency = uiState.value.currencyCode
        val income = parse(incomeInput, currency) ?: return false
        val savings = parse(savingsInput, currency) ?: return false
        viewModelScope.launch {
            settingsRepository.setPlan(income, savings)
            message.value = null
        }
        return true
    }

    fun setReduceOverspend(on: Boolean) {
        viewModelScope.launch { settingsRepository.setCarryOverspend(on) }
    }

    fun setAddUnderspend(on: Boolean) {
        viewModelScope.launch { settingsRepository.setRolloverBudgets(on) }
    }

    /** Gives [categoryId] this amount for this month and the next 11. A blank or zero amount removes its budget. */
    fun setAmount(categoryId: Long, input: String): Boolean {
        val currency = uiState.value.currencyCode
        val minor = parse(input, currency) ?: return false
        val start = thisMonth
        viewModelScope.launch {
            for (i in 0 until MONTHS_AHEAD) {
                val month = start.plusMonths(i.toLong())
                if (minor <= 0L) {
                    budgetRepository.clearCategoryBudgetForMonth(categoryId, month)
                } else {
                    budgetRepository.setCategoryBudgetForMonth(categoryId, Money(minor, currency), month)
                }
            }
            message.value = "Saved for this month and the next " + (MONTHS_AHEAD - 1) + " months."
        }
        return true
    }
}
