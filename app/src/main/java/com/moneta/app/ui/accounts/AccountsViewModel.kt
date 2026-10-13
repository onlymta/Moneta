package com.moneta.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.ArchiveOrDeleteResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList()
)

/** One-shot events the screen should react to (a snackbar, etc.) rather than store in state. */
sealed class AccountsEvent {
    data class Archived(val accountName: String) : AccountsEvent()
    data class Deleted(val accountName: String) : AccountsEvent()
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = accountRepository.observeAllAccounts()
        .map { AccountsUiState(isLoading = false, accounts = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    private val _events = MutableSharedFlow<AccountsEvent>()
    val events: SharedFlow<AccountsEvent> = _events

    /** Section 10: archiving is the safe default for accounts with history; hard delete only when empty. */
    fun archiveOrDeleteAccount(account: Account) {
        viewModelScope.launch {
            when (accountRepository.archiveOrDelete(account.id)) {
                ArchiveOrDeleteResult.Archived -> _events.emit(AccountsEvent.Archived(account.name))
                ArchiveOrDeleteResult.Deleted -> _events.emit(AccountsEvent.Deleted(account.name))
                is ArchiveOrDeleteResult.Blocked -> Unit
            }
        }
    }
}
