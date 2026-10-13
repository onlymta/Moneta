package com.moneta.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.repository.RecurringTransaction
import com.moneta.app.data.repository.RecurringTransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecurringUiState(
    val isLoading: Boolean = true,
    val items: List<RecurringTransaction> = emptyList()
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val repository: RecurringTransactionRepository
) : ViewModel() {

    val uiState: StateFlow<RecurringUiState> = repository.observeAll()
        .map { RecurringUiState(isLoading = false, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    fun setActive(id: Long, isActive: Boolean) {
        viewModelScope.launch { repository.setActive(id, isActive) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
