package com.moneta.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.settings.OnboardingStatus
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.ui.accounts.accountColorPalette
import com.moneta.app.ui.accounts.defaultIconFor
import com.moneta.app.ui.categories.DEFAULT_CATEGORY_ICON
import com.moneta.app.ui.categories.categoryColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

/** Where the user goes when the welcome flow ends. */
enum class OnboardingExit { HOME, ADD_TRANSACTION, IMPORT }

const val ONBOARDING_LAST_STEP = 3

private data class OnboardingForm(
    val step: Int = 0,
    val name: String = "",
    val accountName: String = "",
    val accountType: AccountType = AccountType.CASH,
    val accountBalance: String = "",
    val accountError: String? = null,
    val removedCategoryIds: Set<Long> = emptySet(),
    val newCategoryName: String = "",
    val isWorking: Boolean = false
)

data class OnboardingUiState(
    val step: Int = 0,
    val name: String = "",
    val accounts: List<Account> = emptyList(),
    val accountName: String = "",
    val accountType: AccountType = AccountType.CASH,
    val accountBalance: String = "",
    val accountError: String? = null,
    val expenseCategories: List<Category> = emptyList(),
    val removedCategoryIds: Set<Long> = emptySet(),
    val newCategoryName: String = "",
    val isWorking: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val form = MutableStateFlow(OnboardingForm())

    val uiState: StateFlow<OnboardingUiState> = combine(
        form,
        accountRepository.observeAllAccounts(),
        categoryRepository.observeByType(CategoryType.EXPENSE)
    ) { f, accounts, categories ->
        OnboardingUiState(
            step = f.step,
            name = f.name,
            accounts = accounts,
            accountName = f.accountName,
            accountType = f.accountType,
            accountBalance = f.accountBalance,
            accountError = f.accountError,
            expenseCategories = categories.filter { it.parentCategoryId == null },
            removedCategoryIds = f.removedCategoryIds,
            newCategoryName = f.newCategoryName,
            isWorking = f.isWorking
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun onNameChange(value: String) = form.update { it.copy(name = value.take(40)) }

    fun next() {
        val f = form.value
        // If they typed an account but forgot to press "Add", add it for them.
        if (f.step == 1 && (f.accountName.isNotBlank() || f.accountBalance.isNotBlank())) {
            addAccount()
            if (form.value.accountError != null) return
        }
        form.update { it.copy(step = (it.step + 1).coerceAtMost(ONBOARDING_LAST_STEP)) }
    }

    fun back() = form.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun onAccountNameChange(value: String) = form.update { it.copy(accountName = value.take(40), accountError = null) }

    fun onAccountTypeChange(type: AccountType) = form.update { it.copy(accountType = type, accountError = null) }

    fun onAccountBalanceChange(value: String) {
        val cleaned = value.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
        form.update { it.copy(accountBalance = cleaned, accountError = null) }
    }

    fun addAccount() {
        val f = form.value
        val name = f.accountName.trim().ifEmpty { defaultAccountName(f.accountType) }
        val balance = if (f.accountBalance.isBlank()) {
            Money.zero(DEFAULT_CURRENCY)
        } else {
            Money.fromDecimalInput(f.accountBalance, DEFAULT_CURRENCY)
        }
        if (balance == null) {
            form.update { it.copy(accountError = "Enter a valid balance, or leave it empty.") }
            return
        }
        viewModelScope.launch {
            try {
                accountRepository.createAccount(
                    name = name,
                    type = f.accountType,
                    icon = defaultIconFor(f.accountType),
                    colorHex = accountColorPalette.first(),
                    openingBalance = balance
                )
                form.update { it.copy(accountName = "", accountBalance = "", accountError = null) }
            } catch (e: Exception) {
                form.update { it.copy(accountError = "Couldn't add the account. Try a different name.") }
            }
        }
    }

    fun toggleCategory(id: Long) = form.update {
        val removed = if (id in it.removedCategoryIds) it.removedCategoryIds - id else it.removedCategoryIds + id
        it.copy(removedCategoryIds = removed)
    }

    fun onNewCategoryChange(value: String) = form.update { it.copy(newCategoryName = value.take(30)) }

    fun addCategory() {
        val name = form.value.newCategoryName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            try {
                categoryRepository.createCategory(
                    name = name,
                    type = CategoryType.EXPENSE,
                    icon = DEFAULT_CATEGORY_ICON,
                    colorHex = categoryColorPalette.first()
                )
                form.update { it.copy(newCategoryName = "") }
            } catch (e: Exception) {
                // Ignore a failed add (for example a duplicate name); the field stays so the user can edit it.
            }
        }
    }

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
            saveName()
            form.value.removedCategoryIds.forEach { id ->
                try {
                    categoryRepository.tryDelete(id)
                } catch (e: Exception) {
                    // A category that can't be removed simply stays; harmless.
                }
            }
            settingsRepository.setOnboardingStatus(OnboardingStatus.COMPLETED)
            form.update { it.copy(isWorking = false) }
            onExit(exit)
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
