package com.moneta.app.ui.bills

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
import java.time.LocalDate
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

private data class AddBillForm(
    val name: String = "",
    val amountInput: String = "",
    val dueInDays: Int = 7,
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val nameError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

data class AddBillUiState(
    val name: String = "",
    val amountInput: String = "",
    val dueInDays: Int = 7,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val nameError: String? = null,
    val amountError: String? = null,
    val isSaving: Boolean = false
)

/**
 * Account/category are optional (Section 16 allows tracking a bill without
 * fully wiring it to the ledger), but marking a bill paid only records a
 * matching transaction when both are set - see BillRepository.markPaid.
 */
@HiltViewModel
class AddBillViewModel @Inject constructor(
    private val billRepository: BillRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val form = MutableStateFlow(AddBillForm())
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = categoryRepository.observeByType(CategoryType.EXPENSE)

    val uiState: StateFlow<AddBillUiState> = combine(form, accountsFlow, categoriesFlow) { f, accounts, categories ->
        AddBillUiState(
            name = f.name,
            amountInput = f.amountInput,
            dueInDays = f.dueInDays,
            accounts = accounts,
            categories = categories,
            selectedAccountId = f.selectedAccountId,
            selectedCategoryId = f.selectedCategoryId,
            nameError = f.nameError,
            amountError = f.amountError,
            isSaving = f.isSaving
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddBillUiState())

    fun onNameChange(name: String) = form.update { it.copy(name = name, nameError = null) }
    fun onAmountChange(input: String) = form.update { it.copy(amountInput = input, amountError = null) }
    fun onDueInDaysChange(days: Int) = form.update { it.copy(dueInDays = days) }
    fun onAccountSelected(id: Long?) = form.update { it.copy(selectedAccountId = id) }
    fun onCategorySelected(id: Long?) = form.update { it.copy(selectedCategoryId = id) }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        if (state.name.isBlank()) {
            form.update { it.copy(nameError = "Give this bill a name.") }
            return
        }
        val amount = Money.fromDecimalInput(state.amountInput, DEFAULT_CURRENCY)
        if (amount == null || amount.isZero) {
            form.update { it.copy(amountError = "Enter a valid amount.") }
            return
        }
        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            billRepository.createBill(
                name = state.name.trim(),
                amount = amount,
                dueDate = DateRange.todayDate().plusDays(state.dueInDays.toLong()),
                accountId = state.selectedAccountId,
                categoryId = state.selectedCategoryId
            )
            form.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
