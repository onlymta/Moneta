package com.moneta.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.Category
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import com.moneta.app.ui.common.TransactionListItem
import com.moneta.app.ui.common.buildTransactionItems
import com.moneta.app.ui.reports.ReportsLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class TypeFilter(val label: String) {
    ALL("All"),
    EXPENSE("Expenses"),
    INCOME("Income"),
    TRANSFER("Transfers")
}

/** How the list is laid out: records under day headers, under week headers, or one total per category. */
enum class GroupMode(val label: String) {
    DAY("Records by day"),
    WEEK("Records by week"),
    CATEGORY_TOTALS("Category totals")
}

enum class SortMode(val label: String) {
    DATE_DESC("Date, newest first"),
    DATE_ASC("Date, oldest first"),
    AMOUNT_DESC("Amount, high to low"),
    AMOUNT_ASC("Amount, low to high")
}

/** A block of records under one header, or (for an amount sort) one headerless block. */
data class RecordGroup(
    val key: String,
    val title: String,
    val date: LocalDate?,
    val totalLabel: String,
    val items: List<TransactionListItem>,
    val net: Money
)

/** One line of the category-totals view: a category, how many records, and their sum. */
data class CategoryTotalRow(
    val categoryId: Long,
    val name: String,
    val colorHex: String,
    val icon: String,
    val isIncome: Boolean,
    val count: Int,
    val total: Money
)

data class DayGroup(val date: LocalDate, val items: List<TransactionListItem>, val net: Money)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val query: String = "",
    val typeFilter: TypeFilter = TypeFilter.ALL,
    val accountFilterId: Long? = null,
    val categoryTitle: String? = null,
    val categoryFilterIds: Set<Long>? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val groupMode: GroupMode = GroupMode.DAY,
    val sortMode: SortMode = SortMode.DATE_DESC,
    val groups: List<RecordGroup> = emptyList(),
    val categoryTotals: List<CategoryTotalRow> = emptyList(),
    val currencyCode: String = "PKR",
    val income: Money = Money.zero("PKR"),
    val expense: Money = Money.zero("PKR"),
    val transferCount: Int = 0,
    val count: Int = 0
) {
    val net: Money get() = income - expense
    val activeFilterCount: Int
        get() = (if (typeFilter != TypeFilter.ALL) 1 else 0) +
            (if (accountFilterId != null) 1 else 0) +
            (if (categoryFilterIds != null) 1 else 0)
}

/**
 * Records: every transaction for the chosen period, narrowed by type, account, category and a text
 * search, laid out by day, by week or as one total per category, and sorted by date or amount.
 * The totals always describe exactly the rows currently shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    settingsRepository: SettingsRepository,
    launchFilter: RecordsLaunchFilter,
    private val reportsLaunch: ReportsLaunch
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val queryFlow = MutableStateFlow("")
    private val typeFlow = MutableStateFlow(TypeFilter.ALL)
    private val accountFilterFlow = MutableStateFlow<Long?>(null)
    private val categoryIdsFlow = MutableStateFlow<Set<Long>?>(null)
    private val categoryTitleFlow = MutableStateFlow<String?>(null)
    private val groupModeFlow = MutableStateFlow(GroupMode.DAY)
    private val sortModeFlow = MutableStateFlow(SortMode.DATE_DESC)
    private val accountsFlow = accountRepository.observeAllAccounts()
    private val categoriesFlow = categoryRepository.observeAll()
    private val prefsFlow = settingsRepository.preferences

    init {
        launchFilter.period?.let { period.value = it }
        launchFilter.type?.let { typeFlow.value = it }
        launchFilter.accountId?.let { accountFilterFlow.value = it }
        launchFilter.categoryIds?.let { categoryIdsFlow.value = it }
        categoryTitleFlow.value = launchFilter.categoryTitle
        launchFilter.clear()
    }

    private val filters = combine(period, typeFlow, accountFilterFlow, categoryIdsFlow) { p, t, a, c ->
        Filters(p, t, a, c)
    }

    private data class Filters(
        val period: PeriodSelection,
        val type: TypeFilter,
        val accountId: Long?,
        val categoryIds: Set<Long>?
    )

    private data class Layout(val group: GroupMode, val sort: SortMode, val categoryTitle: String?, val query: String)

    // The search text is applied to rows already loaded (not by re-reading the database on every key),
    // and waits a moment after typing stops, so typing and deleting stay smooth.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val layout = combine(
        groupModeFlow,
        sortModeFlow,
        categoryTitleFlow,
        queryFlow.debounce(180)
    ) { g, s, t, q -> Layout(g, s, t, q) }

    val uiState: StateFlow<TransactionsUiState> = filters.flatMapLatest { f ->
        val typeArg: TransactionType? = when (f.type) {
            TypeFilter.ALL -> null
            TypeFilter.EXPENSE -> TransactionType.EXPENSE
            TypeFilter.INCOME -> TransactionType.INCOME
            TypeFilter.TRANSFER -> TransactionType.TRANSFER
        }
        combine(
            transactionRepository.observeFiltered(accountId = f.accountId, type = typeArg, range = f.period.range()),
            accountsFlow,
            categoriesFlow,
            prefsFlow,
            layout
        ) { transactions, accounts, categories, prefs, lay ->
            val currency = prefs.currencyCode
            var items = buildTransactionItems(transactions, accounts, categories)
            val ids = f.categoryIds
            if (ids != null) {
                items = items.filter { item -> item.transaction.categoryId?.let { it in ids } == true }
            }
            val needle = lay.query.trim().lowercase()
            if (needle.isNotEmpty()) {
                items = items.filter { item ->
                    val t = item.transaction
                    item.title.lowercase().contains(needle) ||
                        item.subtitle.lowercase().contains(needle) ||
                        (t.payee?.lowercase()?.contains(needle) == true) ||
                        (t.notes?.lowercase()?.contains(needle) == true) ||
                        t.amount.toEditableDecimalString().contains(needle)
                }
            }
            val incomeMinor = items.filter { it.transaction.type == TransactionType.INCOME }
                .sumOf { it.transaction.amount.minorUnits }
            val expenseMinor = items.filter { it.transaction.type == TransactionType.EXPENSE }
                .sumOf { it.transaction.amount.minorUnits }

            fun netOf(list: List<TransactionListItem>): Money {
                val inc = list.filter { it.transaction.type == TransactionType.INCOME }.sumOf { it.transaction.amount.minorUnits }
                val exp = list.filter { it.transaction.type == TransactionType.EXPENSE }.sumOf { it.transaction.amount.minorUnits }
                return Money(inc - exp, currency)
            }
            fun sortItems(list: List<TransactionListItem>): List<TransactionListItem> = when (lay.sort) {
                SortMode.DATE_DESC -> list.sortedWith(
                    compareByDescending<TransactionListItem> { it.transaction.date }
                        .thenByDescending { it.transaction.timeMinutesSinceMidnight }
                )
                SortMode.DATE_ASC -> list.sortedWith(
                    compareBy<TransactionListItem> { it.transaction.date }
                        .thenBy { it.transaction.timeMinutesSinceMidnight }
                )
                SortMode.AMOUNT_DESC -> list.sortedByDescending { it.transaction.amount.minorUnits }
                SortMode.AMOUNT_ASC -> list.sortedBy { it.transaction.amount.minorUnits }
            }
            val byAmount = lay.sort == SortMode.AMOUNT_DESC || lay.sort == SortMode.AMOUNT_ASC
            val dateAscending = lay.sort == SortMode.DATE_ASC
            val dateFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")

            var groups: List<RecordGroup> = emptyList()
            var categoryRows: List<CategoryTotalRow> = emptyList()
            when {
                lay.group == GroupMode.CATEGORY_TOTALS -> {
                    val byId = categories.associateBy { it.id }
                    categoryRows = items
                        .filter { it.transaction.categoryId != null && it.transaction.type != TransactionType.TRANSFER }
                        .groupBy { it.transaction.categoryId!! }
                        .mapNotNull { (id, list) ->
                            val cat = byId[id] ?: return@mapNotNull null
                            CategoryTotalRow(
                                categoryId = id,
                                name = cat.name,
                                colorHex = cat.colorHex,
                                icon = cat.icon,
                                isIncome = list.first().transaction.type == TransactionType.INCOME,
                                count = list.size,
                                total = Money(list.sumOf { it.transaction.amount.minorUnits }, currency)
                            )
                        }
                        .let { rows ->
                            if (lay.sort == SortMode.AMOUNT_ASC) rows.sortedBy { it.total.minorUnits }
                            else rows.sortedByDescending { it.total.minorUnits }
                        }
                }
                byAmount -> {
                    groups = if (items.isEmpty()) emptyList() else listOf(
                        RecordGroup("amount-all", "", null, "Total", sortItems(items), netOf(items))
                    )
                }
                lay.group == GroupMode.WEEK -> {
                    groups = items.groupBy {
                        it.transaction.date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    }.map { (monday, list) ->
                        val sunday = monday.plusDays(6)
                        RecordGroup(
                            "week-$monday",
                            monday.format(dateFmt) + " - " + sunday.format(dateFmt) + " " + sunday.year,
                            null,
                            "Week",
                            sortItems(list),
                            netOf(list)
                        )
                    }.let { list -> if (dateAscending) list.sortedBy { it.key } else list.sortedByDescending { it.key } }
                }
                else -> {
                    groups = items.groupBy { it.transaction.date }.map { (date, list) ->
                        RecordGroup("day-$date", date.toString(), date, "Day", sortItems(list), netOf(list))
                    }.let { list -> if (dateAscending) list.sortedBy { it.date } else list.sortedByDescending { it.date } }
                }
            }
            TransactionsUiState(
                isLoading = false,
                period = f.period,
                query = lay.query,
                typeFilter = f.type,
                accountFilterId = f.accountId,
                categoryTitle = if (f.categoryIds != null) lay.categoryTitle else null,
                categoryFilterIds = f.categoryIds,
                accounts = accounts.filter { !it.isArchived },
                categories = categories,
                groupMode = lay.group,
                sortMode = lay.sort,
                groups = groups,
                categoryTotals = categoryRows,
                currencyCode = currency,
                income = Money(incomeMinor, currency),
                expense = Money(expenseMinor, currency),
                transferCount = items.count { it.transaction.type == TransactionType.TRANSFER },
                count = items.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection
    }

    fun currentQuery(): String = queryFlow.value

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    fun onTypeFilterChange(filter: TypeFilter) {
        typeFlow.value = filter
    }

    fun onGroupModeChange(mode: GroupMode) {
        groupModeFlow.value = mode
    }

    fun onSortModeChange(mode: SortMode) {
        sortModeFlow.value = mode
    }

    /** Filters to one category (and, when [ids] holds more than one id, its sub-categories); null clears it. */
    fun onCategoryFilterChange(ids: Set<Long>?, title: String?) {
        categoryTitleFlow.value = title
        categoryIdsFlow.value = ids
    }

    fun clearCategoryFilter() {
        categoryIdsFlow.value = null
    }

    fun onAccountFilterChange(accountId: Long?) {
        accountFilterFlow.value = accountId
    }

    fun clearAllFilters() {
        typeFlow.value = TypeFilter.ALL
        accountFilterFlow.value = null
        categoryIdsFlow.value = null
    }

    /** Hands a category to Reports so it opens straight on that category's analysis for the period on screen. */
    fun prepareReports(categoryId: Long, isIncome: Boolean) {
        reportsLaunch.clear()
        reportsLaunch.categoryId = categoryId
        reportsLaunch.isIncome = isIncome
        reportsLaunch.period = period.value
    }
}
