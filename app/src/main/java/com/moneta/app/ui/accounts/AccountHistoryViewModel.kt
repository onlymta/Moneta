package com.moneta.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.ui.common.buildTransactionItems
import com.moneta.app.ui.transactions.DayGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AccountHistoryUiState(
    val isLoading: Boolean = true,
    val account: Account? = null,
    val groups: List<DayGroup> = emptyList(),
    val monthsShown: Int = 1,
    val showingLabel: String = "",
    val previousMonthLabel: String = "",
    val income: Money = Money.zero("PKR"),
    val expense: Money = Money.zero("PKR"),
    val count: Int = 0
)

/**
 * One account's transaction history. Starts with the current month; "view previous
 * month" adds one more month each time it is tapped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountHistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val accountId = MutableStateFlow<Long?>(null)
    private val months = MutableStateFlow(1)

    fun load(id: Long) {
        accountId.value = id
    }

    fun showPreviousMonth() {
        months.value = months.value + 1
    }

    val uiState: StateFlow<AccountHistoryUiState> = combine(accountId, months) { id, count -> id to count }
        .flatMapLatest { (id, count) ->
            if (id == null) {
                kotlinx.coroutines.flow.flowOf(AccountHistoryUiState())
            } else {
                val thisMonth = LocalDate.now().withDayOfMonth(1)
                val start = thisMonth.minusMonths((count - 1).toLong())
                val range = DateRange(start, thisMonth.plusMonths(1))
                val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
                combine(
                    transactionRepository.observeFiltered(accountId = id, range = range),
                    accountRepository.observeAllAccounts(),
                    categoryRepository.observeAll()
                ) { transactions, accounts, categories ->
                    val account = accounts.firstOrNull { it.id == id }
                    val currency = account?.currentBalance?.currencyCode ?: "PKR"
                    val items = buildTransactionItems(transactions, accounts, categories)
                    // Money in/out of THIS account: transfers in count as in, transfers out as out.
                    val incomeMinor = transactions.sumOf { t -> maxOf(signedFor(t, id), 0L) }
                    val expenseMinor = transactions.sumOf { t -> maxOf(-signedFor(t, id), 0L) }
                    val groups = items.groupBy { it.transaction.date }.map { (date, list) ->
                        DayGroup(date, list, Money(list.sumOf { signedFor(it.transaction, id) }, currency))
                    }.sortedByDescending { it.date }
                    AccountHistoryUiState(
                        isLoading = false,
                        account = account,
                        groups = groups,
                        monthsShown = count,
                        showingLabel = if (count == 1) thisMonth.format(monthFormat)
                        else start.format(monthFormat) + " - " + thisMonth.format(monthFormat),
                        previousMonthLabel = start.minusMonths(1).format(monthFormat),
                        income = Money(incomeMinor, currency),
                        expense = Money(expenseMinor, currency),
                        count = items.size
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountHistoryUiState())
}

/** The effect of one transaction on account [accountId], in minor units (+ in, - out). */
private fun signedFor(t: com.moneta.app.data.repository.Transaction, accountId: Long): Long = when (t.type) {
    TransactionType.INCOME -> t.amount.minorUnits
    TransactionType.EXPENSE -> -t.amount.minorUnits
    TransactionType.TRANSFER -> if (t.toAccountId == accountId) t.amount.minorUnits else -t.amount.minorUnits
}
