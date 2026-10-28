package com.moneta.app.ui.bills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.BillRepository
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

private data class AddBillForm(
    val name: String = "",
    val amountInput: String = "",
    val currencyCode: String = DEFAULT_CURRENCY,
    val dueDate: LocalDate = DateRange.todayDate().plusDays(7),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val notes: String = "",
    val reminderOn: Boolean = false,
    val reminderDaysBefore: Int = 1,
    val nameError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

data class AddBillUiState(
    val isEditing: Boolean = false,
    val name: String = "",
    val amountInput: String = "",
    val dueDate: LocalDate = LocalDate.now(),
    /** 1..31 when the due date is that many days from today, otherwise null (a custom date). */
    val dueInDays: Int? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val notes: String = "",
    val reminderOn: Boolean = false,
    val reminderDaysBefore: Int = 1,
    val nameError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

/**
 * Account/category are optional, but "Record payment" on the Bills page needs both - see BillRepository.recordPayment.
 */
@HiltViewModel
class AddBillViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val billRepository: BillRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>("billId")?.takeIf { it >= 0 }

    private val form = MutableStateFlow(AddBillForm())
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = categoryRepository.observeByType(CategoryType.EXPENSE)

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val bill = billRepository.getById(editingId) ?: return@launch
                form.value = AddBillForm(
                    name = bill.name,
                    amountInput = BigDecimal.valueOf(bill.amount.minorUnits).movePointLeft(2).stripTrailingZeros().toPlainString(),
                    currencyCode = bill.amount.currencyCode,
                    dueDate = bill.dueDate,
                    selectedAccountId = bill.accountId,
                    selectedCategoryId = bill.categoryId,
                    notes = bill.notes.orEmpty(),
                    reminderOn = bill.reminderDaysBefore >= 0,
                    reminderDaysBefore = if (bill.reminderDaysBefore >= 0) bill.reminderDaysBefore else 1
                )
            }
        }
    }

    val uiState: StateFlow<AddBillUiState> = combine(form, accountsFlow, categoriesFlow) { f, accounts, categories ->
        val days = ChronoUnit.DAYS.between(DateRange.todayDate(), f.dueDate).toInt()
        AddBillUiState(
            isEditing = editingId != null,
            name = f.name,
            amountInput = f.amountInput,
            dueDate = f.dueDate,
            dueInDays = days.takeIf { it in 1..31 },
            accounts = accounts,
            categories = categories,
            selectedAccountId = f.selectedAccountId,
            selectedCategoryId = f.selectedCategoryId,
            notes = f.notes,
            reminderOn = f.reminderOn,
            reminderDaysBefore = f.reminderDaysBefore,
            nameError = f.nameError,
            amountError = f.amountError,
            isSaving = f.isSaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddBillUiState())

    fun onNameChange(name: String) = form.update { it.copy(name = name, nameError = null) }
    fun onAmountChange(input: String) = form.update { it.copy(amountInput = input, amountError = null) }
    fun onDueInDaysChange(days: Int) = form.update { it.copy(dueDate = DateRange.todayDate().plusDays(days.coerceIn(1, 31).toLong())) }
    fun onDueDateChange(date: LocalDate) = form.update { it.copy(dueDate = date) }
    fun onAccountSelected(id: Long?) = form.update { it.copy(selectedAccountId = id) }
    fun onCategorySelected(id: Long?) = form.update { it.copy(selectedCategoryId = id) }
    fun onNotesChange(notes: String) = form.update { it.copy(notes = notes) }
    fun onReminderOnChange(on: Boolean) = form.update { it.copy(reminderOn = on) }
    fun onReminderDaysChange(days: Int) = form.update { it.copy(reminderDaysBefore = days.coerceIn(0, 30)) }

    fun save(onSaved: () -> Unit) {
        val f = form.value
        if (f.name.isBlank()) {
            form.update { it.copy(nameError = "Give this bill a name.") }
            return
        }
        val amount = Money.fromDecimalInput(f.amountInput, f.currencyCode)
        if (amount == null || amount.isZero) {
            form.update { it.copy(amountError = "Enter a valid amount.") }
            return
        }
        val reminder = if (f.reminderOn) f.reminderDaysBefore else -1
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            if (editingId != null) {
                billRepository.updateBill(
                    id = editingId,
                    name = f.name.trim(),
                    amount = amount,
                    dueDate = f.dueDate,
                    accountId = f.selectedAccountId,
                    categoryId = f.selectedCategoryId,
                    reminderDaysBefore = reminder,
                    notes = f.notes.takeIf { it.isNotBlank() }
                )
            } else {
                billRepository.createBill(
                    name = f.name.trim(),
                    amount = amount,
                    dueDate = f.dueDate,
                    accountId = f.selectedAccountId,
                    categoryId = f.selectedCategoryId,
                    reminderDaysBefore = reminder,
                    notes = f.notes.takeIf { it.isNotBlank() }
                )
            }
            form.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
