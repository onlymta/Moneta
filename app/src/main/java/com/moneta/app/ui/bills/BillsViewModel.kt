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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillRow(val bill: Bill, val urgency: BillUrgency)

data class BillsUiState(
    val isLoading: Boolean = true,
    val bills: List<BillRow> = emptyList(),
    /** A one-time message for the snackbar (e.g. after recording a payment). */
    val message: String? = null
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val billRepository: BillRepository
) : ViewModel() {

    private val messageFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BillsUiState> = combine(
        billRepository.observeAll(), messageFlow
    ) { bills, message ->
        BillsUiState(
            isLoading = false,
            bills = bills
                .map { BillRow(it, billRepository.urgencyOf(it)) }
                .sortedWith(compareBy({ it.bill.status == com.moneta.app.data.local.entity.BillStatus.PAID }, { it.bill.dueDate })),
            message = message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())

    /** Marks the bill paid without recording anything in the ledger. */
    fun markPaid(billId: Long) {
        viewModelScope.launch {
            billRepository.markPaid(billId)
            messageFlow.value = "Marked as paid"
        }
    }

    /** Marks the bill paid and records the expense; needs an account and a category on the bill. */
    fun recordPayment(billId: Long) {
        viewModelScope.launch {
            val recorded = billRepository.recordPayment(billId)
            messageFlow.value = if (recorded) {
                "Paid, and recorded as an expense"
            } else {
                "Edit this bill and choose an account and a category first"
            }
        }
    }

    fun markUnpaid(billId: Long) {
        viewModelScope.launch {
            val done = billRepository.markUnpaid(billId)
            messageFlow.value = if (done) "Marked as unpaid" else "This payment was recorded as a transaction; delete that transaction to undo it"
        }
    }

    fun deleteBill(billId: Long) {
        viewModelScope.launch { billRepository.delete(billId) }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }
}
