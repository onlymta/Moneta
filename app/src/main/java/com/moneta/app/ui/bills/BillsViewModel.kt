package com.moneta.app.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.repository.Bill
import com.moneta.app.data.repository.BillRepository
import com.moneta.app.data.repository.BillUrgency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillRow(val bill: Bill, val urgency: BillUrgency)

data class BillsUiState(
    val isLoading: Boolean = true,
    val bills: List<BillRow> = emptyList(),
    /** Set right after marking a bill paid, so the screen can tell the user whether a transaction was recorded too. */
    val lastMarkPaidCreatedTransaction: Boolean? = null
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val billRepository: BillRepository
) : ViewModel() {

    private val markPaidResultFlow = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<BillsUiState> = combine(
        billRepository.observeAll(), markPaidResultFlow
    ) { bills, lastResult ->
        BillsUiState(
            isLoading = false,
            bills = bills
                .map { BillRow(it, billRepository.urgencyOf(it)) }
                .sortedBy { it.bill.dueDate },
            lastMarkPaidCreatedTransaction = lastResult
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())

    fun markPaid(billId: Long) {
        viewModelScope.launch {
            val createdTransaction = billRepository.markPaid(billId)
            markPaidResultFlow.value = createdTransaction
        }
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch { billRepository.delete(billId) }
    }

    fun consumeMarkPaidResult() {
        markPaidResultFlow.value = null
    }
}
