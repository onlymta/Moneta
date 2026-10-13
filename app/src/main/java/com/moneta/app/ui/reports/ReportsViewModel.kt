package com.moneta.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.domain.usecase.CategoryBreakdownUseCase
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.domain.usecase.MonthlyCashFlow
import com.moneta.app.domain.usecase.MonthlyCashFlowUseCase
import com.moneta.app.domain.usecase.NetWorthPoint
import com.moneta.app.domain.usecase.NetWorthTrendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"
private const val MONTHS_OF_HISTORY = 6

enum class ReportTab { OVERVIEW, SPENDING, INCOME, CASH_FLOW, NET_WORTH }

data class ReportsUiState(
    val isLoading: Boolean = true,
    val selectedTab: ReportTab = ReportTab.OVERVIEW,
    val currentMonthIncome: Money = Money.zero(DEFAULT_CURRENCY),
    val currentMonthExpense: Money = Money.zero(DEFAULT_CURRENCY),
    val spendingBreakdown: List<CategorySlice> = emptyList(),
    val incomeBreakdown: List<CategorySlice> = emptyList(),
    val monthlyCashFlow: List<MonthlyCashFlow> = emptyList(),
    val netWorthTrend: List<NetWorthPoint> = emptyList(),
    val isHistoryLoading: Boolean = true
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryBreakdownUseCase: CategoryBreakdownUseCase,
    private val monthlyCashFlowUseCase: MonthlyCashFlowUseCase,
    private val netWorthTrendUseCase: NetWorthTrendUseCase
) : ViewModel() {

    private val currentMonth = DateRange.currentMonth()
    private val selectedTabFlow = MutableStateFlow(ReportTab.OVERVIEW)

    // Historical data (cash flow, net worth) needs one-shot suspend calls per month rather
    // than a live Flow per month, so it's loaded once into its own state and refreshed
    // whenever a transaction changes via the lightweight signal below - simpler than wiring
    // N month-range Flows, at the cost of not being instantly live like the rest of the app.
    private val historyState = MutableStateFlow(
        Triple(emptyList<MonthlyCashFlow>(), emptyList<NetWorthPoint>(), true)
    )

    private val incomeFlow =
        transactionRepository.observeTotalForTypeInRange(TransactionType.INCOME, currentMonth, DEFAULT_CURRENCY)
    private val expenseFlow =
        transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, currentMonth, DEFAULT_CURRENCY)
    private val expenseTotalsFlow =
        transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, currentMonth)
    private val incomeTotalsFlow =
        transactionRepository.observeTotalsByCategory(TransactionType.INCOME, currentMonth)

    val uiState: StateFlow<ReportsUiState> = combine(
        selectedTabFlow, incomeFlow, expenseFlow, expenseTotalsFlow, incomeTotalsFlow, historyState
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tab = values[0] as ReportTab
        val income = values[1] as Money
        val expense = values[2] as Money
        val expenseTotals = values[3] as List<com.moneta.app.data.local.dao.CategoryTotal>
        val incomeTotals = values[4] as List<com.moneta.app.data.local.dao.CategoryTotal>
        val (cashFlow, netWorth, historyLoading) = values[5] as Triple<List<MonthlyCashFlow>, List<NetWorthPoint>, Boolean>

        ReportsUiState(
            isLoading = false,
            selectedTab = tab,
            currentMonthIncome = income,
            currentMonthExpense = expense,
            spendingBreakdown = categoryBreakdownUseCase(expenseTotals, expense),
            incomeBreakdown = categoryBreakdownUseCase(incomeTotals, income),
            monthlyCashFlow = cashFlow,
            netWorthTrend = netWorth,
            isHistoryLoading = historyLoading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    init {
        loadHistory()
    }

    fun onTabSelected(tab: ReportTab) {
        selectedTabFlow.value = tab
    }

    /** Call after any transaction change (e.g. returning to this screen) to refresh the one-shot history data. */
    fun loadHistory() {
        viewModelScope.launch {
            val monthEnds = (0 until MONTHS_OF_HISTORY).map { offset ->
                val reference = DateRange.todayDate().minusMonths((MONTHS_OF_HISTORY - 1 - offset).toLong())
                DateRange.currentMonth(referenceDate = reference).endExclusive.minusDays(1)
            }
            val cashFlow = monthlyCashFlowUseCase(MONTHS_OF_HISTORY, DEFAULT_CURRENCY)
            val netWorth = netWorthTrendUseCase(monthEnds, DEFAULT_CURRENCY)
            historyState.value = Triple(cashFlow, netWorth, false)
        }
    }
}
