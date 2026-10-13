package com.moneta.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.RecurrenceFrequency
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.RecurringTransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

private data class AddRecurringForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalCount: Int = 1,
    val hasEndDate: Boolean = false,
    val endInMonths: Int = 12,
    val payee: String = "",
    val amountError: String? = null,
    val isSaving: Boolean = false
)

data class AddRecurringUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalCount: Int = 1,
    val hasEndDate: Boolean = false,
    val endInMonths: Int = 12,
    val payee: String = "",
    val amountError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class AddRecurringViewModel @Inject constructor(
    private val recurringRepository: RecurringTransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val form = MutableStateFlow(AddRecurringForm())
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = form.map { it.type }.distinctUntilChanged().flatMapLatest { type ->
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        categoryRepository.observeByType(categoryType)
    }

    val uiState: StateFlow<AddRecurringUiState> = combine(form, accountsFlow, categoriesFlow) { f, accounts, categories ->
        AddRecurringUiState(
            type = f.type,
            amountInput = f.amountInput,
            accounts = accounts,
            categories = categories,
            selectedAccountId = f.selectedAccountId ?: accounts.firstOrNull()?.id,
            selectedToAccountId = f.selectedToAccountId,
            selectedCategoryId = f.selectedCategoryId,
            frequency = f.frequency,
            intervalCount = f.intervalCount,
            hasEndDate = f.hasEndDate,
            endInMonths = f.endInMonths,
            payee = f.payee,
            amountError = f.amountError,
            isSaving = f.isSaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddRecurringUiState())

    fun onTypeChange(type: TransactionType) = form.update {
        it.copy(type = type, selectedCategoryId = null, selectedToAccountId = if (type == TransactionType.TRANSFER) it.selectedToAccountId else null)
    }
    fun onAmountChange(input: String) = form.update { it.copy(amountInput = input, amountError = null) }
    fun onAccountSelected(id: Long) = form.update { it.copy(selectedAccountId = id) }
    fun onToAccountSelected(id: Long) = form.update { it.copy(selectedToAccountId = id) }
    fun onCategorySelected(id: Long) = form.update { it.copy(selectedCategoryId = id) }
    fun onFrequencyChange(frequency: RecurrenceFrequency) = form.update { it.copy(frequency = frequency) }
    fun onIntervalChange(count: Int) = form.update { it.copy(intervalCount = count.coerceAtLeast(1)) }
    fun onHasEndDateChange(has: Boolean) = form.update { it.copy(hasEndDate = has) }
    fun onEndInMonthsChange(months: Int) = form.update { it.copy(endInMonths = months.coerceAtLeast(1)) }
    fun onPayeeChange(payee: String) = form.update { it.copy(payee = payee) }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        val accountId = state.selectedAccountId
        if (accountId == null) {
            form.update { it.copy(amountError = "Add an account first.") }
            return
        }
        val amount = Money.fromDecimalInput(state.amountInput, DEFAULT_CURRENCY)
        if (amount == null || amount.isZero) {
            form.update { it.copy(amountError = "Enter a valid amount.") }
            return
        }
        if (state.type == TransactionType.TRANSFER) {
            if (state.selectedToAccountId == null || state.selectedToAccountId == accountId) {
                form.update { it.copy(amountError = "Choose a different destination account.") }
                return
            }
        } else if (state.selectedCategoryId == null) {
            form.update { it.copy(amountError = "Choose a category.") }
            return
        }

        val startDate = DateRange.todayDate()
        val endDate = if (state.hasEndDate) startDate.plusMonths(state.endInMonths.toLong()) else null

        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            recurringRepository.create(
                templateType = state.type,
                amount = amount,
                accountId = accountId,
                toAccountId = state.selectedToAccountId,
                categoryId = state.selectedCategoryId,
                frequency = state.frequency,
                intervalCount = state.intervalCount,
                startDate = startDate,
                endDate = endDate,
                reminderEnabled = false,
                reminderOffsetDays = 1,
                notes = null,
                payee = state.payee.takeIf { it.isNotBlank() }
            )
            form.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
