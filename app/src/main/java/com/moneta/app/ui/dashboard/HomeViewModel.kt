package com.moneta.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.BudgetProgress
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.Transaction
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.domain.usecase.CategoryBreakdownUseCase
import com.moneta.app.domain.usecase.CategorySlice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Default currency used until Settings (Phase 10 in the full roadmap, stubbed for now) lets users change it. */
private const val DEFAULT_CURRENCY = "PKR"

/** Simple month-over-month comparison used for the Home "trend" snippet (Section 6/19). */
data class MonthTrend(
    val hasPriorMonthData: Boolean,
    val expenseDeltaPercent: Double?,
    val isExpenseUp: Boolean
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasAnyAccounts: Boolean = false,
    val totalBalance: Money = Money.zero(DEFAULT_CURRENCY),
    val monthIncome: Money = Money.zero(DEFAULT_CURRENCY),
    val monthExpense: Money = Money.zero(DEFAULT_CURRENCY),
    val monthSavings: Money = Money.zero(DEFAULT_CURRENCY),
    val recentTransactions: List<Transaction> = emptyList(),
    val spendingBreakdown: List<CategorySlice> = emptyList(),
    val trend: MonthTrend = MonthTrend(hasPriorMonthData = false, expenseDeltaPercent = null, isExpenseUp = false),
    /** Top 2 budgets by how close/over they are, for the Home "budget status" snippet (Section 6). */
    val topBudgets: List<BudgetProgress> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    budgetRepository: BudgetRepository,
    private val categoryBreakdownUseCase: CategoryBreakdownUseCase
) : ViewModel() {

    private val currentMonth = DateRange.currentMonth()
    private val previousMonth = DateRange.previousMonth()

    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val netWorthFlow = accountRepository.observeTotalNetWorth(DEFAULT_CURRENCY)
    private val monthIncomeFlow =
        transactionRepository.observeTotalForTypeInRange(TransactionType.INCOME, currentMonth, DEFAULT_CURRENCY)
    private val monthExpenseFlow =
        transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, currentMonth, DEFAULT_CURRENCY)
    private val previousMonthExpenseFlow =
        transactionRepository.observeTotalForTypeInRange(TransactionType.EXPENSE, previousMonth, DEFAULT_CURRENCY)
    private val recentFlow = transactionRepository.observeRecent(limit = 5)
    private val categoryTotalsFlow =
        transactionRepository.observeTotalsByCategory(TransactionType.EXPENSE, currentMonth)
    private val budgetProgressFlow = budgetRepository.observeProgress()

    val uiState: StateFlow<HomeUiState> = combine(
        accountsFlow,
        netWorthFlow,
        monthIncomeFlow,
        monthExpenseFlow,
        previousMonthExpenseFlow,
        recentFlow,
        categoryTotalsFlow,
        budgetProgressFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        buildUiState(
            accounts = values[0] as List<Account>,
            netWorth = values[1] as Money,
            income = values[2] as Money,
            expense = values[3] as Money,
            previousExpense = values[4] as Money,
            recent = values[5] as List<Transaction>,
            categoryTotals = values[6] as List<CategoryTotal>,
            budgetProgress = values[7] as List<BudgetProgress>
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private suspend fun buildUiState(
        accounts: List<Account>,
        netWorth: Money,
        income: Money,
        expense: Money,
        previousExpense: Money,
        recent: List<Transaction>,
        categoryTotals: List<CategoryTotal>,
        budgetProgress: List<BudgetProgress>
    ): HomeUiState {
        val breakdown = categoryBreakdownUseCase(categoryTotals, expense)
        val trend = buildTrend(expense, previousExpense)
        return HomeUiState(
            isLoading = false,
            hasAnyAccounts = accounts.isNotEmpty(),
            totalBalance = netWorth,
            monthIncome = income,
            monthExpense = expense,
            monthSavings = income - expense,
            recentTransactions = recent,
            spendingBreakdown = breakdown,
            trend = trend,
            topBudgets = budgetProgress
                .sortedByDescending { it.percentUsed ?: 0.0 }
                .take(2)
        )
    }

    /** Section 19: only states a trend when there IS prior-month data to compare against. */
    private fun buildTrend(currentExpense: Money, previousExpense: Money): MonthTrend {
        if (previousExpense.isZero) {
            return MonthTrend(hasPriorMonthData = false, expenseDeltaPercent = null, isExpenseUp = false)
        }
        val deltaPercent = ((currentExpense - previousExpense).percentOf(previousExpense)) ?: return MonthTrend(
            hasPriorMonthData = false, expenseDeltaPercent = null, isExpenseUp = false
        )
        return MonthTrend(
            hasPriorMonthData = true,
            expenseDeltaPercent = kotlin.math.abs(deltaPercent),
            isExpenseUp = deltaPercent > 0
        )
    }
}
