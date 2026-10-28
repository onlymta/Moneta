package com.moneta.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.settings.OnboardingStatus
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.ui.accounts.accountColorPalette
import com.moneta.app.ui.accounts.defaultIconFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

/** Where the user goes when the welcome flow ends. */
enum class OnboardingExit { HOME, ADD_TRANSACTION, IMPORT }

const val ONBOARDING_LAST_STEP = 4

/** An account the user is setting up; it is only saved when the welcome flow finishes. */
data class PendingAccount(
    val key: Int,
    val name: String,
    val type: AccountType,
    val balance: String = ""
)

data class OnboardingUiState(
    val step: Int = 0,
    val name: String = "",
    val accounts: List<PendingAccount> = emptyList(),
    val newAccountName: String = "",
    val newAccountType: AccountType = AccountType.CASH,
    val newAccountBalance: String = "",
    val accountError: String? = null,
    val currency: String = DEFAULT_CURRENCY,
    val showSymbol: Boolean = true,
    val decimals: Int = 2,
    val carryForward: Boolean = true,
    val budgetsOn: Boolean = true,
    val goalsOn: Boolean = false,
    val billsOn: Boolean = false,
    val isWorking: Boolean = false,
    val restoreMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val backupManager: com.moneta.app.data.backup.BackupManager,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val form = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = form.asStateFlow()
    private var nextKey = 1

    init {
        // A new user starts with a Cash and a Bank account already listed; they only need to fill in balances.
        viewModelScope.launch {
            if (accountRepository.observeAllAccounts().first().isEmpty()) {
                form.update {
                    it.copy(
                        accounts = listOf(
                            PendingAccount(nextKey++, "Cash", AccountType.CASH),
                            PendingAccount(nextKey++, "Bank account", AccountType.BANK)
                        )
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) = form.update { it.copy(name = value.take(40)) }

    fun next() {
        val f = form.value
        if (f.step == 2) {
            // Added something in the "another account" boxes but forgot to press Add: add it for them.
            if (f.newAccountName.isNotBlank() || f.newAccountBalance.isNotBlank()) addAccount()
            val now = form.value
            val bad = now.accounts.any { it.balance.isNotBlank() && Money.fromDecimalInput(it.balance, DEFAULT_CURRENCY) == null }
            if (bad || now.accountError != null) {
                form.update { it.copy(accountError = "Enter balances as numbers, or leave them empty.") }
                return
            }
        }
        form.update { it.copy(step = (it.step + 1).coerceAtMost(ONBOARDING_LAST_STEP)) }
    }

    fun back() = form.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun onAccountBalanceChange(key: Int, value: String) {
        val cleaned = value.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
        form.update { f -> f.copy(accounts = f.accounts.map { if (it.key == key) it.copy(balance = cleaned) else it }, accountError = null) }
    }

    fun onAccountNameChange(key: Int, value: String) {
        form.update { f -> f.copy(accounts = f.accounts.map { if (it.key == key) it.copy(name = value.take(40)) else it }) }
    }

    fun removeAccount(key: Int) = form.update { f -> f.copy(accounts = f.accounts.filter { it.key != key }) }

    fun onNewAccountNameChange(value: String) = form.update { it.copy(newAccountName = value.take(40), accountError = null) }

    fun onNewAccountTypeChange(type: AccountType) = form.update { it.copy(newAccountType = type) }

    fun onNewAccountBalanceChange(value: String) {
        val cleaned = value.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
        form.update { it.copy(newAccountBalance = cleaned, accountError = null) }
    }

    fun addAccount() {
        val f = form.value
        val name = f.newAccountName.trim().ifEmpty { defaultAccountName(f.newAccountType) }
        if (f.newAccountBalance.isNotBlank() && Money.fromDecimalInput(f.newAccountBalance, DEFAULT_CURRENCY) == null) {
            form.update { it.copy(accountError = "Enter a valid balance, or leave it empty.") }
            return
        }
        form.update {
            it.copy(
                accounts = it.accounts + PendingAccount(nextKey++, name, f.newAccountType, f.newAccountBalance),
                newAccountName = "",
                newAccountBalance = "",
                accountError = null
            )
        }
    }

    fun setCurrency(code: String) = form.update { it.copy(currency = code) }
    fun setShowSymbol(on: Boolean) = form.update { it.copy(showSymbol = on) }
    fun setDecimals(value: Int) = form.update { it.copy(decimals = value.coerceIn(0, 2)) }
    fun setCarryForward(on: Boolean) = form.update { it.copy(carryForward = on) }
    fun setBudgetsOn(on: Boolean) = form.update { it.copy(budgetsOn = on) }
    fun setGoalsOn(on: Boolean) = form.update { it.copy(goalsOn = on) }
    fun setBillsOn(on: Boolean) = form.update { it.copy(billsOn = on) }

    /** "Do this later": remember it was skipped so Home can show a gentle reminder. */
    fun skip(onExit: (OnboardingExit) -> Unit) {
        viewModelScope.launch {
            saveName()
            settingsRepository.setOnboardingStatus(OnboardingStatus.SKIPPED)
            onExit(OnboardingExit.HOME)
        }
    }

    fun finish(exit: OnboardingExit, onExit: (OnboardingExit) -> Unit) {
        if (form.value.isWorking) return
        viewModelScope.launch {
            form.update { it.copy(isWorking = true) }
            val f = form.value
            saveName()
            f.accounts.forEach { pending ->
                try {
                    accountRepository.createAccount(
                        name = pending.name.trim().ifEmpty { defaultAccountName(pending.type) },
                        type = pending.type,
                        icon = defaultIconFor(pending.type),
                        colorHex = accountColorPalette[(pending.key - 1).coerceAtLeast(0) % accountColorPalette.size],
                        openingBalance = Money.fromDecimalInput(pending.balance, DEFAULT_CURRENCY) ?: Money.zero(DEFAULT_CURRENCY)
                    )
                } catch (e: Exception) {
                    // One account failing to save must not stop the rest of the setup.
                }
            }
            settingsRepository.setDisplayCurrency(f.currency)
            settingsRepository.setShowCurrencySymbol(f.showSymbol)
            settingsRepository.setDecimals(f.decimals)
            settingsRepository.setCarryForwardBalance(f.carryForward)
            settingsRepository.setBudgetsEnabled(f.budgetsOn)
            settingsRepository.setGoalsEnabled(f.goalsOn)
            settingsRepository.setBillsEnabled(f.billsOn)
            settingsRepository.setOnboardingStatus(OnboardingStatus.COMPLETED)
            form.update { it.copy(isWorking = false) }
            onExit(exit)
        }
    }

    /** Restores a backup file picked on the welcome screen, then goes straight to Home. */
    fun restoreFromBackup(uriString: String, onExit: (OnboardingExit) -> Unit) {
        if (form.value.isWorking) return
        viewModelScope.launch {
            form.update { it.copy(isWorking = true, restoreMessage = null) }
            val result = try {
                appContext.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { stream ->
                    backupManager.restoreBackup(stream)
                } ?: com.moneta.app.data.backup.RestoreResult.Failed("Could not open the chosen file.")
            } catch (e: Exception) {
                com.moneta.app.data.backup.RestoreResult.Failed(e.message ?: "Could not read that file.")
            }
            when (result) {
                is com.moneta.app.data.backup.RestoreResult.Success -> {
                    settingsRepository.setOnboardingStatus(OnboardingStatus.COMPLETED)
                    form.update { it.copy(isWorking = false) }
                    onExit(OnboardingExit.HOME)
                }
                is com.moneta.app.data.backup.RestoreResult.Failed -> {
                    form.update { it.copy(isWorking = false, restoreMessage = result.reason) }
                }
            }
        }
    }

    private suspend fun saveName() {
        val name = form.value.name.trim()
        if (name.isNotEmpty()) settingsRepository.setUserName(name)
    }

    private fun defaultAccountName(type: AccountType): String = when (type) {
        AccountType.CASH -> "Cash"
        AccountType.BANK -> "Bank account"
        AccountType.CREDIT_CARD -> "Credit card"
        AccountType.SAVINGS -> "Savings"
        AccountType.E_WALLET -> "Mobile wallet"
        AccountType.OTHER -> "Other"
    }
}
