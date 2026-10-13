package com.moneta.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.data.repository.Transaction
import com.moneta.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val transactions: List<Transaction> = emptyList()
)

/**
 * Phase 1 scope: shows the current month's transactions with a simple text
 * search. Full filter sheet (date range, account, category, amount range,
 * tags - Section 9 of the original brief) is a Phase 2/5 addition; this
 * screen is real and functional for what it currently supports, per Section
 * 47's "no fake features" rule - it doesn't show filter controls it can't
 * back yet.
 */
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val uiState: StateFlow<TransactionsUiState> = queryFlow
        .flatMapLatest { query ->
            transactionRepository.observeFiltered(range = DateRange.currentMonth(), query = query)
                .combine(queryFlow) { transactions, currentQuery ->
                    TransactionsUiState(isLoading = false, query = currentQuery, transactions = transactions)
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }
}
