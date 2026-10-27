package com.moneta.app.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.AmountCalculator
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.local.entity.CategoryType
import com.moneta.app.ui.accounts.accountColorPalette
import com.moneta.app.ui.accounts.defaultIconFor
import com.moneta.app.ui.categories.DEFAULT_CATEGORY_ICON
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

/** One step of the category picker: its options and which one (if any) is chosen. */
data class CategoryLevel(
    val label: String,
    val options: List<Category>,
    val selectedId: Long?
)

data class QuickAddUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val amountError: String? = null,
    val accounts: List<Account> = emptyList(),
    /** Category pickers from top level down; deeper levels only appear when sub-categories are enabled. */
    val categoryLevels: List<CategoryLevel> = emptyList(),
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
    val dateBelowKeypad: Boolean = false,
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

        val levels = buildCategoryLevels(categories, defaultCategoryId, prefs.subcategoriesEnabled)

        QuickAddUiState(
            type = f.type,
            amountInput = f.amountInput,
            amountError = f.amountError,
            accounts = accounts,
            categoryLevels = levels,
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
            dateBelowKeypad = prefs.formDateBelowKeypad,
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

    /** Used for both a top-level category tap and a subcategory refinement tap. */
    fun onCategorySelected(categoryId: Long) = form.update { it.copy(selectedCategoryId = categoryId, formError = null) }

    /** Creates an account from the form's small "new account" pop-up and selects it. */
    fun quickAddAccount(name: String, type: AccountType, forDestination: Boolean) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = accountRepository.createAccount(
                name = clean,
                type = type,
                icon = defaultIconFor(type),
                colorHex = accountColorPalette.first(),
                openingBalance = Money(0L, DEFAULT_CURRENCY)
            )
            if (forDestination) onToAccountSelected(id) else onAccountSelected(id)
        }
    }

    /** Creates a category (optionally inside [parentId]) from the small pop-up and selects it. */
    fun quickAddCategory(name: String, parentId: Long?) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val type = if (form.value.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        viewModelScope.launch {
            val id = categoryRepository.createCategory(
                name = clean,
                type = type,
                icon = DEFAULT_CATEGORY_ICON,
                colorHex = com.moneta.app.ui.common.IconMapper.colorFor(DEFAULT_CATEGORY_ICON),
                parentCategoryId = parentId
            )
            onCategorySelected(id)
        }
    }

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

    private var duplicatedFromId: Long? = null

    /**
     * Opens the form pre-filled from an existing transaction as a NEW record: everything is
     * copied except the date and time, which are set to now. Nothing is saved until the user taps Save.
     */
    fun loadForDuplicate(transactionId: Long) {
        if (duplicatedFromId == transactionId) return
        duplicatedFromId = transactionId
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
                    date = LocalDate.now(),
                    timeMinutes = nowMinutes()
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

/**
 * Builds the chain of pickers for the selected category. With sub-categories off there is one
 * level (root categories). With them on, each chosen category with children adds another level.
 */
private fun buildCategoryLevels(
    categories: List<Category>,
    selectedId: Long?,
    subcategoriesEnabled: Boolean
): List<CategoryLevel> {
    val byId = categories.associateBy { it.id }
    val path = ArrayList<Long>()
    var cursor: Category? = selectedId?.let { byId[it] }
    while (cursor != null && path.size < 12) {
        path.add(0, cursor.id)
        val parentId = cursor.parentCategoryId
        cursor = if (parentId == null) null else byId[parentId]
    }
    val roots = categories.filter { it.parentCategoryId == null }
    val levels = ArrayList<CategoryLevel>()
    levels.add(CategoryLevel("Category", roots, path.firstOrNull()))
    if (!subcategoriesEnabled) return levels
    var depth = 0
    while (depth < path.size || depth == 0) {
        val parentId = path.getOrNull(depth) ?: break
        val children = categories.filter { it.parentCategoryId == parentId }
        if (children.isEmpty()) break
        val label = if (depth == 0) "Sub-category (optional)" else "Sub-sub-category (optional)"
        levels.add(CategoryLevel(label, children, path.getOrNull(depth + 1)))
        depth++
    }
    return levels
}
