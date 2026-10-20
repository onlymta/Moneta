package com.moneta.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import com.moneta.app.ui.common.TransactionListItem
import com.moneta.app.ui.common.buildTransactionItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

enum class GroupMode(val label: String) {
    DAY("By day"),
    WEEK("By week"),
    CATEGORY("By category"),
    AMOUNT("By amount")
}

/** A block of records under one header: a day, a week, a category, or (sorted by amount) one headerless block. */
data class RecordGroup(
    val key: String,
    val title: String,
    val date: LocalDate?,
    val totalLabel: String,
    val items: List<TransactionListItem>,
    val net: Money
)

data class DayGroup(val date: LocalDate, val items: List<TransactionListItem>, val net: Money)

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val query: String = "",
    val typeFilter: TypeFilter = TypeFilter.ALL,
    val accountFilterId: Long? = null,
    val categoryTitle: String? = null,
    val accounts: List<Account> = emptyList(),
    val groupMode: GroupMode = GroupMode.DAY,
    val groups: List<RecordGroup> = emptyList(),
    val currencyCode: String = "PKR",
    val income: Money = Money.zero("PKR"),
    val expense: Money = Money.zero("PKR"),
    val transferCount: Int = 0,
    val count: Int = 0
) {
    val net: Money get() = income - expense
}

/**
 * Records: every transaction for the chosen period (day, week, month, year, all
 * time or a custom range), optionally narrowed by type, account and a text
 * search that looks at payee, notes, category, account and amount. The totals
 * always describe exactly the rows currently shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    settingsRepository: SettingsRepository,
    launchFilter: RecordsLaunchFilter
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val queryFlow = MutableStateFlow("")
    private val typeFlow = MutableStateFlow(TypeFilter.ALL)
    private val accountFilterFlow = MutableStateFlow<Long?>(null)
    private val categoryIdsFlow = MutableStateFlow<Set<Long>?>(null)
    private val categoryTitleFlow = MutableStateFlow<String?>(null)
    private val groupModeFlow = MutableStateFlow(GroupMode.DAY)
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

    private val filters = combine(period, queryFlow, typeFlow, accountFilterFlow, categoryIdsFlow) { p, q, t, a, c ->
        Filters(p, q, t, a, c)
    }

    private data class Filters(
        val period: PeriodSelection,
        val query: String,
        val type: TypeFilter,
        val accountId: Long?,
        val categoryIds: Set<Long>?
    )

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
            groupModeFlow
        ) { transactions, accounts, categories, prefs, mode ->
            val currency = prefs.currencyCode
            var items = buildTransactionItems(transactions, accounts, categories)
            val ids = f.categoryIds
            if (ids != null) {
                items = items.filter { item -> item.transaction.categoryId?.let { it in ids } == true }
            }
            val needle = f.query.trim().lowercase()
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
            val dateFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")
            val groups: List<RecordGroup> = when (mode) {
                GroupMode.DAY -> items.groupBy { it.transaction.date }.map { (date, list) ->
                    RecordGroup("day-$date", date.toString(), date, "Day", list, netOf(list))
                }.sortedByDescending { it.date }
                GroupMode.WEEK -> items.groupBy {
                    it.transaction.date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                }.map { (monday, list) ->
                    val sunday = monday.plusDays(6)
                    RecordGroup(
                        "week-$monday",
                        monday.format(dateFmt) + " - " + sunday.format(dateFmt) + " " + sunday.year,
                        null,
                        "Week",
                        list.sortedByDescending { it.transaction.date },
                        netOf(list)
                    )
                }.sortedByDescending { it.key }
                GroupMode.CATEGORY -> {
                    val nameOf = categories.associate { it.id to it.name }
                    items.groupBy { item ->
                        val cid = item.transaction.categoryId
                        if (cid == null) "Transfers" else (nameOf[cid] ?: "Uncategorised")
                    }.map { (name, list) ->
                        RecordGroup(
                            "cat-$name",
                            name,
                            null,
                            "Total",
                            list.sortedByDescending { it.transaction.date },
                            netOf(list)
                        )
                    }.sortedByDescending { g -> g.items.sumOf { it.transaction.amount.minorUnits } }
                }
                GroupMode.AMOUNT -> if (items.isEmpty()) emptyList() else listOf(
                    RecordGroup(
                        "amount-all",
                        "",
                        null,
                        "Total",
                        items.sortedByDescending { it.transaction.amount.minorUnits },
                        netOf(items)
                    )
                )
            }
            TransactionsUiState(
                isLoading = false,
                period = f.period,
                query = f.query,
                typeFilter = f.type,
                accountFilterId = f.accountId,
                categoryTitle = if (f.categoryIds != null) categoryTitleFlow.value else null,
                accounts = accounts.filter { !it.isArchived },
                groupMode = mode,
                groups = groups,
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

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    fun onTypeFilterChange(filter: TypeFilter) {
        typeFlow.value = filter
    }

    fun onGroupModeChange(mode: GroupMode) {
        groupModeFlow.value = mode
    }

    fun clearCategoryFilter() {
        categoryIdsFlow.value = null
    }

    fun onAccountFilterChange(accountId: Long?) {
        accountFilterFlow.value = accountId
    }
}
