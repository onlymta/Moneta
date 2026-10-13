package com.moneta.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.domain.insights.Insight
import com.moneta.app.domain.insights.InsightContext
import com.moneta.app.domain.insights.InsightsEngine
import com.moneta.app.domain.usecase.CategoryBreakdownUseCase
import com.moneta.app.domain.usecase.MonthlyCashFlowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"
private const val MONTHS_OF_HISTORY = 4

data class InsightsUiState(
    val isLoading: Boolean = true,
    val insights: List<Insight> = emptyList(),
    val hasEnoughData: Boolean = false
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryBreakdownUseCase: CategoryBreakdownUseCase,
    private val monthlyCashFlowUseCase: MonthlyCashFlowUseCase,
    private val insightsEngine: InsightsEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = InsightsUiState(isLoading = true)

            val currentMonth = DateRange.currentMonth()
            val previousMonth = DateRange.previousMonth()

            val currentExpenseTotals = transactionRepository
                .observeTotalsByCategory(TransactionType.EXPENSE, currentMonth).first()
            val previousExpenseTotals = transactionRepository
                .observeTotalsByCategory(TransactionType.EXPENSE, previousMonth).first()
            val currentMonthExpense = transactionRepository
                .observeTotalForTypeInRange(TransactionType.EXPENSE, currentMonth, DEFAULT_CURRENCY).first()
            val previousMonthExpense = transactionRepository
                .observeTotalForTypeInRange(TransactionType.EXPENSE, previousMonth, DEFAULT_CURRENCY).first()

            val context = InsightContext(
                currencyCode = DEFAULT_CURRENCY,
                currentMonthExpenseByCategory = categoryBreakdownUseCase(currentExpenseTotals, currentMonthExpense),
                previousMonthExpenseByCategory = categoryBreakdownUseCase(previousExpenseTotals, previousMonthExpense),
                monthlyCashFlow = monthlyCashFlowUseCase(MONTHS_OF_HISTORY, DEFAULT_CURRENCY),
                budgetProgress = budgetRepository.observeProgress().first(),
                currentMonthExpenseTransactions = transactionRepository
                    .observeFiltered(type = TransactionType.EXPENSE, range = currentMonth)
                    .first()
            )

            val insights = insightsEngine.generate(context)
            _uiState.value = InsightsUiState(
                isLoading = false,
                insights = insights,
                hasEnoughData = context.monthlyCashFlow.size >= 2 || context.currentMonthExpenseTransactions.isNotEmpty()
            )
        }
    }
}
