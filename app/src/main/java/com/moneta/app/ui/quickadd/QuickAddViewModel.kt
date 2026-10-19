package com.moneta.app.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.AmountCalculator
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.data.local.entity.RecurrenceFrequency
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.RecurringTransactionRepository
import com.moneta.app.data.repository.TransactionDraft
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.PickerStyle
import com.moneta.app.data.settings.SettingsRepository
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
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

/** Everything the user has typed/picked so far - the ViewModel's single mutable source of truth. */
private data class QuickAddForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val amountError: String? = null,
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val notes: String = "",
    val date: LocalDate = LocalDate.now(),
    val timeMinutes: Int = nowMinutes(),
    val formError: String? = null,
    val savedMessage: String? = null,
    val addAnotherTick: Int = 0,
    val isSaving: Boolean = false,
    val saved: Boolean = false
)

private fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

data class QuickAddUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val amountError: String? = null,
    val accounts: List<Account> = emptyList(),
    /** Top-level categories only - the fast, one-tap path (Section 7). */
    val topLevelCategories: List<Category> = emptyList(),
    /**
     * Subcategories to offer as a refinement, derived from whichever category is
     * currently selected (its children if a top-level category is selected, or its
     * siblings if a subcategory is already selected) - empty when there's nothing
     * to refine, so most categories never show a second row at all.
     */
    val subcategoryOptions: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val notes: String = "",
    val date: LocalDate = LocalDate.now(),
    val timeMinutes: Int = 0,
    /** True when the chosen date is after today - the entry will be scheduled, not recorded now. */
    val isFutureDate: Boolean = false,
    val formError: String? = null,
    val savedMessage: String? = null,
    val addAnotherTick: Int = 0,
    val pickerStyle: PickerStyle = PickerStyle.DROPDOWN,
    val saveAtTop: Boolean = false,
    val pickersInRow: Boolean = false,
    val pickersAboveAmount: Boolean = false,
    val customKeypad: Boolean = false,
    val notesAboveAmount: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false
)

/**
 * Backs the Quick Add flow from ARCHITECTURE.md Section 7: amount-first entry,
 * category/account pickers pre-populated with real data, remembered recent
 * account/category as the default so repeat entries need fewer taps, and a
 * single Save action that goes through TransactionRepository's atomic
 * balance-mutation write path - never a shortcut that writes the row
 * without updating balances.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val recurringRepository: RecurringTransactionRepository
) : ViewModel() {

    private val form = MutableStateFlow(QuickAddForm())

    private var editingTransactionId: Long? = null

    private val accountsFlow = accountRepository.observeActiveAccounts()

    /** Re-queries categories whenever the transaction type flips between income/expense. */
    private val categoriesFlow = form
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type ->
            val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
            categoryRepository.observeByType(categoryType)
        }

    val uiState: StateFlow<QuickAddUiState> = combine(
        form, accountsFlow, categoriesFlow, settingsRepository.preferences
    ) { f, accounts, categories, prefs ->
        val defaultAccountId = f.selectedAccountId
            ?: prefs.lastUsedAccountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id

        val lastUsedCategoryId = if (f.type == TransactionType.INCOME) {
            prefs.lastUsedIncomeCategoryId
        } else {
            prefs.lastUsedExpenseCategoryId
        }
        // A transfer must never carry a category (TransactionDraft enforces this and
        // throws if it does), so the remembered last-used category must not leak in
        // as a default here the way it does for expense/income.
        val defaultCategoryId = if (f.type == TransactionType.TRANSFER) {
            null
        } else {
            f.selectedCategoryId ?: lastUsedCategoryId?.takeIf { id -> categories.any { it.id == id } }
        }

        val topLevel = categories.filter { it.parentCategoryId == null }
        val subcategoryOptions = defaultCategoryId?.let { selectedId ->
            val selected = categories.find { it.id == selectedId }
            val parentIdToShow = if (selected?.parentCategoryId == null) selectedId else selected.parentCategoryId
            categories.filter { it.parentCategoryId == parentIdToShow }
        }.orEmpty()

        QuickAddUiState(
            type = f.type,
            amountInput = f.amountInput,
            amountError = f.amountError,
            accounts = accounts,
            topLevelCategories = topLevel,
            subcategoryOptions = subcategoryOptions,
            selectedAccountId = defaultAccountId,
            selectedToAccountId = f.selectedToAccountId,
            selectedCategoryId = defaultCategoryId,
            notes = f.notes,
            date = f.date,
            timeMinutes = f.timeMinutes,
            isFutureDate = f.date.isAfter(LocalDate.now()),
            formError = f.formError,
            savedMessage = f.savedMessage,
            addAnotherTick = f.addAnotherTick,
            pickerStyle = prefs.pickerStyle,
            saveAtTop = prefs.formSaveAtTop,
            pickersInRow = prefs.formPickersInRow,
            pickersAboveAmount = prefs.formPickersAboveAmount,
            customKeypad = prefs.formCustomKeypad,
            notesAboveAmount = prefs.formNotesAboveAmount,
            isSaving = f.isSaving,
            saved = f.saved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickAddUiState())

    fun onTypeChange(type: TransactionType) = form.update {
        it.copy(
            type = type,
            selectedCategoryId = null,
            selectedToAccountId = if (type == TransactionType.TRANSFER) it.selectedToAccountId else null,
            amountError = null,
            formError = null
        )
    }

    fun onAmountChange(input: String) {
        // Only digits and one decimal point, so the numeric field can never hold junk.
        val cleaned = input.filter { ch -> ch.isDigit() || ch == '.' }
        val singleDot = if (cleaned.count { ch -> ch == '.' } > 1) cleaned.dropLast(1) else cleaned
        form.update {
            it.copy(
                amountInput = singleDot.take(12),
                amountError = null,
                formError = null,
                savedMessage = null
            )
        }
    }

    /** Used by the in-app keypad, which builds the text itself (digits, a dot and + - × ÷). */
    fun onAmountExpressionChange(expression: String) {
        form.update {
            it.copy(
                amountInput = expression.take(40),
                amountError = null,
                formError = null,
                savedMessage = null
            )
        }
    }

    fun onAccountSelected(accountId: Long) = form.update { it.copy(selectedAccountId = accountId, formError = null) }

    fun onToAccountSelected(accountId: Long) = form.update { it.copy(selectedToAccountId = accountId, formError = null) }

    /** Used for both a top-level category tap and a subcategory refinement tap - see [QuickAddUiState.subcategoryOptions]. */
    fun onCategorySelected(categoryId: Long) = form.update { it.copy(selectedCategoryId = categoryId, formError = null) }

    fun onNotesChange(notes: String) = form.update { it.copy(notes = notes) }

    fun setPickerStyle(style: PickerStyle) {
        viewModelScope.launch { settingsRepository.setPickerStyle(style) }
    }

    fun onDateChange(date: LocalDate) = form.update { it.copy(date = date, formError = null) }

    fun onTimeChange(minutesSinceMidnight: Int) = form.update { it.copy(timeMinutes = minutesSinceMidnight) }

    /** Loads an existing transaction into the form for editing (Section 9: "Edit" from Transaction Detail). */
    fun loadForEdit(transactionId: Long) {
        if (editingTransactionId == transactionId) return
        editingTransactionId = transactionId
        viewModelScope.launch {
            val existing = transactionRepository.getById(transactionId) ?: return@launch
            form.update {
                it.copy(
                    type = existing.type,
                    amountInput = existing.amount.toEditableDecimalString(),
                    selectedAccountId = existing.accountId,
                    selectedToAccountId = existing.toAccountId,
                    selectedCategoryId = existing.categoryId,
                    notes = existing.notes.orEmpty(),
                    date = existing.date,
                    timeMinutes = existing.timeMinutesSinceMidnight
                )
            }
        }
    }

    val isEditing: Boolean get() = editingTransactionId != null

    /** A plain number, or a calculation like 250+80×2, turned into the amount to save. */
    private fun resolveAmount(input: String): Money? {
        val text = if (AmountCalculator.hasOperator(input)) {
            AmountCalculator.evaluateToPlainString(input) ?: return null
        } else {
            input
        }
        return Money.fromDecimalInput(text, DEFAULT_CURRENCY)
    }

    fun save(onSaved: () -> Unit, addAnother: Boolean = false) {
        val state = uiState.value
        if (state.isSaving) return
        val accountId = state.selectedAccountId
        if (accountId == null) {
            form.update { it.copy(formError = "Add an account first (More \u2192 Accounts).") }
            return
        }
        val amount = resolveAmount(state.amountInput)
        if (amount == null || amount.isZero) {
            form.update { it.copy(amountError = "Enter an amount.") }
            return
        }
        if (state.type == TransactionType.TRANSFER) {
            if (state.selectedToAccountId == null || state.selectedToAccountId == accountId) {
                form.update { it.copy(formError = "Pick a different account to transfer to.") }
                return
            }
        } else if (state.selectedCategoryId == null) {
            form.update { it.copy(formError = "Pick a category.") }
            return
        }
        val existingId = editingTransactionId
        if (existingId != null && state.isFutureDate) {
            form.update { it.copy(formError = "A saved transaction can't be moved to a future date.") }
            return
        }

        viewModelScope.launch {
            form.update { it.copy(isSaving = true, formError = null) }
            val notes = state.notes.takeIf { it.isNotBlank() }
            try {
                if (existingId != null) {
                    transactionRepository.edit(
                        existingId,
                        TransactionDraft(
                            type = state.type,
                            amount = amount,
                            accountId = accountId,
                            toAccountId = state.selectedToAccountId,
                            categoryId = state.selectedCategoryId,
                            date = state.date,
                            timeMinutesSinceMidnight = state.timeMinutes,
                            notes = notes
                        )
                    )
                } else if (state.isFutureDate) {
                    // Future date: store it as a one-time scheduled item. The app turns it into
                    // a real transaction (and only then changes balances) once the date arrives.
                    recurringRepository.create(
                        templateType = state.type,
                        amount = amount,
                        accountId = accountId,
                        toAccountId = state.selectedToAccountId,
                        categoryId = state.selectedCategoryId,
                        frequency = RecurrenceFrequency.DAILY,
                        intervalCount = 1,
                        startDate = state.date,
                        endDate = state.date,
                        reminderEnabled = false,
                        reminderOffsetDays = 0,
                        notes = notes,
                        payee = null
                    )
                } else {
                    transactionRepository.record(
                        TransactionDraft(
                            type = state.type,
                            amount = amount,
                            accountId = accountId,
                            toAccountId = state.selectedToAccountId,
                            categoryId = state.selectedCategoryId,
                            date = state.date,
                            timeMinutesSinceMidnight = state.timeMinutes,
                            notes = notes
                        )
                    )
                }
            } catch (e: Exception) {
                form.update { it.copy(isSaving = false, formError = "Couldn't save. Please try again.") }
                return@launch
            }

            // Remember this account/category as the default for next time (Section 7).
            settingsRepository.setLastUsedAccountId(accountId)
            if (state.type != TransactionType.TRANSFER) {
                state.selectedCategoryId?.let { categoryId ->
                    if (state.type == TransactionType.INCOME) {
                        settingsRepository.setLastUsedIncomeCategoryId(categoryId)
                    } else {
                        settingsRepository.setLastUsedExpenseCategoryId(categoryId)
                    }
                }
            }

            if (addAnother && existingId == null) {
                val message = if (state.isFutureDate) "Scheduled. Add the next one." else "Saved. Add the next one."
                form.update {
                    it.copy(
                        isSaving = false,
                        amountInput = "",
                        notes = "",
                        amountError = null,
                        savedMessage = message,
                        addAnotherTick = it.addAnotherTick + 1
                    )
                }
            } else {
                form.update { it.copy(isSaving = false, saved = true) }
                onSaved()
            }
        }
    }
}
