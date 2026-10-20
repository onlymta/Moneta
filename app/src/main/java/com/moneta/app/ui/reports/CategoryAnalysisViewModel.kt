package com.moneta.app.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.BudgetPeriod
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.ui.common.TransactionListItem
import com.moneta.app.ui.common.buildTransactionItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CategoryMonthRow(
    val label: String,
    val spent: Money,
    val budget: Money?
) {
    val percent: Double? get() = budget?.let { if (it.minorUnits > 0L) spent.minorUnits * 100.0 / it.minorUnits else null }
}

data class CategoryAnalysisUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val spent: Money = Money.zero("PKR"),
    val budget: Money? = null,
    val history: List<CategoryMonthRow> = emptyList(),
    val txCount: Int = 0,
    val avgTransaction: Money = Money.zero("PKR"),
    val largestTransaction: Money = Money.zero("PKR"),
    val expected: Money? = null,
    val expectedNote: String = "",
    val items: List<TransactionListItem> = emptyList()
) {
    val percentUsed: Double? get() = budget?.let { if (it.minorUnits > 0L) spent.minorUnits * 100.0 / it.minorUnits else null }
}

@HiltViewModel
class CategoryAnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    budgetRepository: BudgetRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val categoryId: Long = savedStateHandle.get<Long>("categoryId") ?: -1L
    private val monthStart: LocalDate =
        LocalDate.ofEpochDay(savedStateHandle.get<Long>("monthStart") ?: LocalDate.now().toEpochDay()).withDayOfMonth(1)

    private val historyMonths = 6L
    private val monthEnd: LocalDate = monthStart.plusMonths(1)
    private val allRange = DateRange(monthStart.minusMonths(historyMonths), monthEnd)

    val uiState: StateFlow<CategoryAnalysisUiState> = combine(
        transactionRepository.observeInRange(allRange),
        categoryRepository.observeAll(),
        accountRepository.observeActiveAccounts(),
        budgetRepository.observeAllBudgets(),
        settingsRepository.preferences
    ) { transactions, categories, accounts, budgets, prefs ->
        val currency = prefs.currencyCode
        val category = categories.firstOrNull { it.id == categoryId }
        val ids = (setOf(categoryId) + categories.filter { it.parentCategoryId == categoryId }.map { it.id })
        val expenses = transactions.filter {
            it.type == TransactionType.EXPENSE && it.categoryId != null && it.categoryId in ids
        }
        val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy")
        fun budgetFor(start: LocalDate): Money? = budgets.firstOrNull {
            it.isEnabled && it.scope == BudgetScope.CATEGORY && it.categoryId == categoryId &&
                it.period == BudgetPeriod.CUSTOM && it.customPeriodStart == start
        }?.let { Money(it.amount.minorUnits, currency) }

        fun spentIn(start: LocalDate): Long = expenses
            .filter { !it.date.isBefore(start) && it.date.isBefore(start.plusMonths(1)) }
            .sumOf { it.amount.minorUnits }

        val thisMonthTx = expenses
            .filter { !it.date.isBefore(monthStart) && it.date.isBefore(monthEnd) }
            .sortedWith(compareByDescending<com.moneta.app.data.repository.Transaction> { it.date }
                .thenByDescending { it.timeMinutesSinceMidnight })
        val spentNow = thisMonthTx.sumOf { it.amount.minorUnits }

        val history = (1..historyMonths).map { back ->
            val start = monthStart.minusMonths(back)
            CategoryMonthRow(start.format(monthFmt), Money(spentIn(start), currency), budgetFor(start))
        }
        val priorSpends = history.map { it.spent.minorUnits }.filter { it > 0L }

        val today = LocalDate.now()
        val isCurrent = !today.isBefore(monthStart) && today.isBefore(monthEnd)
        val daysElapsed = if (isCurrent) today.dayOfMonth else monthStart.lengthOfMonth()
        val thisMonthEstimate = if (isCurrent && daysElapsed > 0) {
            spentNow * monthStart.lengthOfMonth() / daysElapsed
        } else {
            spentNow
        }
        val samples = priorSpends + (if (thisMonthEstimate > 0L) listOf(thisMonthEstimate) else emptyList())
        val expected = if (samples.isEmpty()) null else Money(samples.sum() / samples.size, currency)
        val note = when {
            samples.isEmpty() -> "Not enough spending history yet."
            isCurrent -> "Average of " + priorSpends.size + " earlier month(s) and this month's pace."
            else -> "Average of this month and " + priorSpends.size + " earlier month(s)."
        }
        val largest = thisMonthTx.maxOfOrNull { it.amount.minorUnits } ?: 0L
        CategoryAnalysisUiState(
            isLoading = false,
            name = category?.name ?: "Category",
            monthLabel = monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            isCurrentMonth = isCurrent,
            spent = Money(spentNow, currency),
            budget = budgetFor(monthStart),
            history = history,
            txCount = thisMonthTx.size,
            avgTransaction = Money(if (thisMonthTx.isEmpty()) 0L else spentNow / thisMonthTx.size, currency),
            largestTransaction = Money(largest, currency),
            expected = expected,
            expectedNote = note,
            items = buildTransactionItems(thisMonthTx, accounts, categories)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryAnalysisUiState())
}
