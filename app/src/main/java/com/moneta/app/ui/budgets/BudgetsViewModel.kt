package com.moneta.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.repository.BudgetProgress
import com.moneta.app.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val budgets: List<BudgetProgress> = emptyList()
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    val uiState: StateFlow<BudgetsUiState> = budgetRepository.observeProgress()
        .map { BudgetsUiState(isLoading = false, budgets = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch { budgetRepository.delete(budgetId) }
    }
}
