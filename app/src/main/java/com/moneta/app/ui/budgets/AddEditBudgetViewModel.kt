package com.moneta.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

private data class AddBudgetForm(
    val scope: BudgetScope = BudgetScope.OVERALL,
    val selectedCategoryId: Long? = null,
    val amountInput: String = "",
    val alertThresholdPercent: Int = 90,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

data class AddEditBudgetUiState(
    val scope: BudgetScope = BudgetScope.OVERALL,
    val expenseCategories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val amountInput: String = "",
    val alertThresholdPercent: Int = 90,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

/**
 * Only supports creating a new monthly budget in this phase (Section 13's
 * "custom-period budgets" is deferred - MONTHLY covers the common case and
 * keeps this screen honest about what it actually does).
 */
@HiltViewModel
class AddEditBudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val form = MutableStateFlow(AddBudgetForm())
    private val expenseCategoriesFlow = categoryRepository.observeByType(CategoryType.EXPENSE)

    val uiState: StateFlow<AddEditBudgetUiState> = combine(form, expenseCategoriesFlow) { f, categories ->
        AddEditBudgetUiState(
            scope = f.scope,
            expenseCategories = categories,
            selectedCategoryId = f.selectedCategoryId ?: categories.firstOrNull()?.id,
            amountInput = f.amountInput,
            alertThresholdPercent = f.alertThresholdPercent,
            amountError = f.amountError,
            isSaving = f.isSaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddEditBudgetUiState())

    fun onScopeChange(scope: BudgetScope) = form.update { it.copy(scope = scope, amountError = null) }

    fun onCategorySelected(categoryId: Long) = form.update { it.copy(selectedCategoryId = categoryId) }

    fun onAmountChange(input: String) = form.update { it.copy(amountInput = input, amountError = null) }

    fun onThresholdChange(percent: Int) = form.update { it.copy(alertThresholdPercent = percent) }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        val amount = Money.fromDecimalInput(state.amountInput, DEFAULT_CURRENCY)
        if (amount == null || amount.isZero) {
            form.update { it.copy(amountError = "Enter a valid budget amount.") }
            return
        }
        if (state.scope == BudgetScope.CATEGORY && state.selectedCategoryId == null) {
            form.update { it.copy(amountError = "Choose a category first, or add one from Categories.") }
            return
        }

        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            if (state.scope == BudgetScope.OVERALL) {
                budgetRepository.createOverallBudget(amount, state.alertThresholdPercent)
            } else {
                budgetRepository.createCategoryBudget(state.selectedCategoryId!!, amount, state.alertThresholdPercent)
            }
            form.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
