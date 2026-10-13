package com.moneta.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

/** The default icon/color per account type, so a new account looks reasonable before the user customizes it. */
fun defaultIconFor(type: AccountType): String = when (type) {
    AccountType.CASH -> "payments"
    AccountType.BANK -> "account_balance"
    AccountType.CREDIT_CARD -> "credit_card"
    AccountType.SAVINGS -> "savings"
    AccountType.E_WALLET -> "account_balance_wallet"
    AccountType.OTHER -> "category"
}

val accountColorPalette = listOf(
    "#0F6B5C", "#5B6B76", "#B8860B", "#2E7D91", "#7A5C99", "#946B4D", "#4C6B8A"
)

data class AddEditAccountUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    val icon: String = defaultIconFor(AccountType.CASH),
    val colorHex: String = accountColorPalette.first(),
    val openingBalanceInput: String = "",
    val includeInNetWorth: Boolean = true,
    val nameError: String? = null,
    val amountError: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class AddEditAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditAccountUiState())
    val uiState: StateFlow<AddEditAccountUiState> = _uiState.asStateFlow()

    private var editingAccountId: Long? = null

    fun loadForEdit(accountId: Long) {
        if (editingAccountId == accountId) return
        editingAccountId = accountId
        _uiState.update { it.copy(isLoading = true, isEditing = true) }
        viewModelScope.launch {
            val account = accountRepository.getById(accountId) ?: return@launch
            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = account.name,
                    type = account.type,
                    icon = account.icon,
                    colorHex = account.colorHex,
                    openingBalanceInput = "", // opening balance is immutable after creation, shown read-only instead
                    includeInNetWorth = account.includeInNetWorth
                )
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, nameError = null) }

    fun onTypeChange(type: AccountType) = _uiState.update {
        // Only re-suggest an icon if the user hasn't already picked one for a previous type in this session.
        it.copy(type = type, icon = if (it.icon == defaultIconFor(it.type)) defaultIconFor(type) else it.icon)
    }

    fun onColorChange(colorHex: String) = _uiState.update { it.copy(colorHex = colorHex) }

    fun onOpeningBalanceChange(input: String) =
        _uiState.update { it.copy(openingBalanceInput = input, amountError = null) }

    fun onIncludeInNetWorthChange(include: Boolean) = _uiState.update { it.copy(includeInNetWorth = include) }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "Give this account a name.") }
            return
        }

        viewModelScope.launch {
            val existingId = editingAccountId
            if (existingId != null) {
                val existing = accountRepository.getById(existingId) ?: return@launch
                accountRepository.updateAccountDetails(
                    existing.copy(
                        name = state.name.trim(),
                        icon = state.icon,
                        colorHex = state.colorHex,
                        includeInNetWorth = state.includeInNetWorth
                    )
                )
            } else {
                val opening = Money.fromDecimalInput(
                    state.openingBalanceInput.ifBlank { "0" },
                    DEFAULT_CURRENCY
                )
                if (opening == null) {
                    _uiState.update { it.copy(amountError = "Enter a valid opening balance.") }
                    return@launch
                }
                accountRepository.createAccount(
                    name = state.name.trim(),
                    type = state.type,
                    icon = state.icon,
                    colorHex = state.colorHex,
                    openingBalance = opening,
                    includeInNetWorth = state.includeInNetWorth
                )
            }
            _uiState.update { it.copy(saved = true) }
            onSaved()
        }
    }
}
