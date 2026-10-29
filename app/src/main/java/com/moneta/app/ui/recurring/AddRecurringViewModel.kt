package com.moneta.app.ui.recurring

import androidx.lifecycle.SavedStateHandle
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
import com.moneta.app.data.repository.RecurringTransaction
import com.moneta.app.data.repository.RecurringTransactionRepository
import com.moneta.app.domain.usecase.RecurrenceCalculator
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
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

private const val DEFAULT_CURRENCY = "PKR"

/** Last day of the month is stored as 31; shorter months clamp to their real last day. */
const val LAST_DAY_OF_MONTH = 31

private data class AddRecurringForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val currencyCode: String = DEFAULT_CURRENCY,
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalCount: Int = 1,
    val weekday: DayOfWeek = DateRange.todayDate().dayOfWeek,
    val monthDay: Int = DateRange.todayDate().dayOfMonth,
    val yearMonth: Int = DateRange.todayDate().monthValue,
    val yearDay: Int = DateRange.todayDate().dayOfMonth,
    val startFrom: LocalDate = DateRange.todayDate(),
    val endDate: LocalDate? = null,
    val payee: String = "",
    val notes: String = "",
    val amountError: String? = null,
    val isSaving: Boolean = false
)

data class AddRecurringUiState(
    val isEditing: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalCount: Int = 1,
    val weekday: DayOfWeek = DayOfWeek.MONDAY,
    val monthDay: Int = 1,
    val yearMonth: Int = 1,
    val yearDay: Int = 1,
    val startFrom: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val payee: String = "",
    val notes: String = "",
    val amountError: String? = null,
    val isSaving: Boolean = false,
    /** A plain sentence such as "Next: Mon, 1 Nov 2026", so people can check the schedule before saving. */
    val preview: String = ""
)

@HiltViewModel
class AddRecurringViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recurringRepository: RecurringTransactionRepository,
    private val calculator: RecurrenceCalculator,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>("recurringId")?.takeIf { it >= 0 }
    private var original: RecurringTransaction? = null
    private var originalSignature: String? = null

    private val form = MutableStateFlow(AddRecurringForm())
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = form.map { it.type }.distinctUntilChanged().flatMapLatest { type ->
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        categoryRepository.observeByType(categoryType)
    }

    init {
        if (editingId != null) {
            viewModelScope.launch {
                val rule = recurringRepository.getById(editingId) ?: return@launch
                original = rule
                val anchor = if (rule.anchorDay in 1..31) rule.anchorDay else rule.startDate.dayOfMonth
                val loaded = AddRecurringForm(
                    type = rule.templateType,
                    amountInput = BigDecimal.valueOf(rule.amount.minorUnits).movePointLeft(2).stripTrailingZeros().toPlainString(),
                    currencyCode = rule.amount.currencyCode,
                    selectedAccountId = rule.accountId,
                    selectedToAccountId = rule.toAccountId,
                    selectedCategoryId = rule.categoryId,
                    frequency = rule.frequency,
                    intervalCount = rule.intervalCount,
                    weekday = rule.nextOccurrenceDate.dayOfWeek,
                    monthDay = anchor,
                    yearMonth = rule.startDate.monthValue,
                    yearDay = rule.startDate.dayOfMonth,
                    startFrom = rule.startDate,
                    endDate = rule.endDate,
                    payee = "",
                    notes = rule.notes ?: rule.payee.orEmpty()
                )
                originalSignature = scheduleSignature(loaded)
                form.value = loaded
            }
        }
    }

    private fun scheduleSignature(f: AddRecurringForm): String =
        "${f.frequency}|${f.intervalCount}|${f.weekday}|${f.monthDay}|${f.yearMonth}|${f.yearDay}|${f.startFrom}"

    private fun firstDateFor(f: AddRecurringForm, from: LocalDate): LocalDate = calculator.firstDate(
        from = from,
        frequency = f.frequency,
        weekday = f.weekday,
        monthDay = f.monthDay,
        yearlyMonth = f.yearMonth,
        yearlyDay = f.yearDay
    )

    val uiState: StateFlow<AddRecurringUiState> = combine(form, accountsFlow, categoriesFlow) { f, accounts, categories ->
        val next = firstDateFor(f, f.startFrom)
        AddRecurringUiState(
            isEditing = editingId != null,
            type = f.type,
            amountInput = f.amountInput,
            accounts = accounts,
            categories = categories,
            selectedAccountId = f.selectedAccountId ?: accounts.firstOrNull()?.id,
            selectedToAccountId = f.selectedToAccountId,
            selectedCategoryId = f.selectedCategoryId,
            frequency = f.frequency,
            intervalCount = f.intervalCount,
            weekday = f.weekday,
            monthDay = f.monthDay,
            yearMonth = f.yearMonth,
            yearDay = f.yearDay,
            startFrom = f.startFrom,
            endDate = f.endDate,
            payee = f.payee,
            notes = f.notes,
            amountError = f.amountError,
            isSaving = f.isSaving,
            preview = "First date: ${next.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, $next"
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
    fun onIntervalChange(count: Int) = form.update { it.copy(intervalCount = count.coerceIn(1, 99)) }
    fun onWeekdayChange(day: DayOfWeek) = form.update { it.copy(weekday = day) }
    fun onMonthDayChange(day: Int) = form.update { it.copy(monthDay = day.coerceIn(1, 31)) }
    fun onYearlyChange(month: Int, day: Int) = form.update { it.copy(yearMonth = month.coerceIn(1, 12), yearDay = day.coerceIn(1, 31)) }
    fun onStartFromChange(date: LocalDate) = form.update { it.copy(startFrom = date) }
    fun onEndDateChange(date: LocalDate?) = form.update { it.copy(endDate = date) }
    fun onPayeeChange(payee: String) = form.update { it.copy(payee = payee) }
    fun onNotesChange(notes: String) = form.update { it.copy(notes = notes) }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        val f = form.value
        val accountId = state.selectedAccountId
        if (accountId == null) {
            form.update { it.copy(amountError = "Add an account first.") }
            return
        }
        val amount = Money.fromDecimalInput(state.amountInput, f.currencyCode)
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
        val endDate = f.endDate
        if (endDate != null && endDate.isBefore(f.startFrom)) {
            form.update { it.copy(amountError = "The end date must be after the start date.") }
            return
        }

        val anchor = if (f.frequency == RecurrenceFrequency.MONTHLY) f.monthDay else 0
        val today = DateRange.todayDate()
        val existing = original
        val scheduleUnchanged = existing != null && originalSignature == scheduleSignature(f)
        val nextDate = when {
            scheduleUnchanged && existing != null -> existing.nextOccurrenceDate
            existing != null -> firstDateFor(f, if (f.startFrom.isAfter(today)) f.startFrom else today)
            else -> firstDateFor(f, f.startFrom)
        }

        viewModelScope.launch {
            form.update { it.copy(isSaving = true) }
            if (editingId != null && existing != null) {
                recurringRepository.update(
                    id = editingId,
                    templateType = state.type,
                    amount = amount,
                    accountId = accountId,
                    toAccountId = if (state.type == TransactionType.TRANSFER) state.selectedToAccountId else null,
                    categoryId = if (state.type == TransactionType.TRANSFER) null else state.selectedCategoryId,
                    frequency = f.frequency,
                    intervalCount = f.intervalCount,
                    startDate = f.startFrom,
                    endDate = endDate,
                    nextDate = nextDate,
                    anchorDay = anchor,
                    notes = f.notes.takeIf { it.isNotBlank() },
                    payee = null
                )
            } else {
                recurringRepository.create(
                    templateType = state.type,
                    amount = amount,
                    accountId = accountId,
                    toAccountId = if (state.type == TransactionType.TRANSFER) state.selectedToAccountId else null,
                    categoryId = if (state.type == TransactionType.TRANSFER) null else state.selectedCategoryId,
                    frequency = f.frequency,
                    intervalCount = f.intervalCount,
                    startDate = f.startFrom,
                    endDate = endDate,
                    reminderEnabled = false,
                    reminderOffsetDays = 1,
                    notes = f.notes.takeIf { it.isNotBlank() },
                    payee = null,
                    firstDate = nextDate,
                    anchorDay = anchor
                )
            }
            form.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
