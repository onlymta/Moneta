package com.moneta.app.ui.transactiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.Transaction
import com.moneta.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionDetailUiState(
    val isLoading: Boolean = true,
    val transaction: Transaction? = null,
    val accountName: String = "",
    val toAccountName: String? = null,
    val categoryName: String? = null,
    val deleted: Boolean = false,
    val duplicatedId: Long? = null
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    private var loadedId: Long? = null

    fun load(transactionId: Long) {
        if (loadedId == transactionId) return
        loadedId = transactionId
        viewModelScope.launch {
            val transaction = transactionRepository.getById(transactionId) ?: return@launch
            val accountName = accountRepository.getById(transaction.accountId)?.name ?: "Unknown account"
            val toAccountName = transaction.toAccountId?.let { accountRepository.getById(it)?.name }
            val categoryName = transaction.categoryId?.let { categoryRepository.getById(it)?.name }
            _uiState.value = TransactionDetailUiState(
                isLoading = false,
                transaction = transaction,
                accountName = accountName,
                toAccountName = toAccountName,
                categoryName = categoryName
            )
        }
    }

    fun duplicate() {
        val id = loadedId ?: return
        viewModelScope.launch {
            val newId = transactionRepository.duplicate(id)
            _uiState.value = _uiState.value.copy(duplicatedId = newId)
        }
    }

    /** Soft-deletes (Section 9: recoverable, never a silent hard delete from this screen). */
    fun delete() {
        val id = loadedId ?: return
        viewModelScope.launch {
            transactionRepository.delete(id)
            _uiState.value = _uiState.value.copy(deleted = true)
        }
    }

    fun undoDelete() {
        val id = loadedId ?: return
        viewModelScope.launch {
            transactionRepository.undoDelete(id)
            _uiState.value = _uiState.value.copy(deleted = false)
        }
    }
}
