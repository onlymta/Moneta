package com.moneta.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.BillRepository
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.GoalRepository
import com.moneta.app.data.repository.RecurringTransactionRepository
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.core.date.DateRange
import com.moneta.app.ui.common.TransactionListItem
import com.moneta.app.ui.common.buildTransactionItems
import com.moneta.app.ui.navigation.accountEditRoute
import com.moneta.app.ui.navigation.categoryEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** A non-transaction match: where it is, what it is, and which route opens it. */
data class SearchHit(val key: String, val title: String, val subtitle: String, val route: String)

data class SearchResults(
    val query: String = "",
    val screens: List<SearchHit> = emptyList(),
    val accounts: List<SearchHit> = emptyList(),
    val categories: List<SearchHit> = emptyList(),
    val budgets: List<SearchHit> = emptyList(),
    val bills: List<SearchHit> = emptyList(),
    val goals: List<SearchHit> = emptyList(),
    val scheduled: List<SearchHit> = emptyList(),
    val transactions: List<TransactionListItem> = emptyList(),
    val totalTransactionMatches: Int = 0
) {
    val isEmpty: Boolean
        get() = screens.isEmpty() && accounts.isEmpty() && categories.isEmpty() && budgets.isEmpty() &&
            bills.isEmpty() && goals.isEmpty() && scheduled.isEmpty() && transactions.isEmpty()
}

private data class SearchData1(
    val items: List<TransactionListItem>,
    val accounts: List<com.moneta.app.data.repository.Account>,
    val categories: List<com.moneta.app.data.repository.Category>
)

private data class SearchData2(
    val bills: List<com.moneta.app.data.repository.Bill>,
    val goals: List<com.moneta.app.data.repository.Goal>,
    val recurring: List<com.moneta.app.data.repository.RecurringTransaction>,
    val budgets: List<com.moneta.app.data.repository.BudgetProgress>
)

private val SCREEN_SHORTCUTS = listOf(
    SearchHit("s-home", "Home", "Overview", "home"),
    SearchHit("s-records", "Records", "All transactions", "transactions"),
    SearchHit("s-budgets", "Budgets", "Monthly limits", "budgets"),
    SearchHit("s-reports", "Reports", "Charts and trends", "reports"),
    SearchHit("s-accounts", "Accounts", "Wallets, banks, cards", "accounts"),
    SearchHit("s-categories", "Categories", "Expense and income categories", "categories"),
    SearchHit("s-bills", "Bills", "Bills and due dates", "bills"),
    SearchHit("s-recurring", "Scheduled & recurring", "Repeating and future transactions", "recurring"),
    SearchHit("s-goals", "Goals", "Savings goals", "goals"),
    SearchHit("s-insights", "Insights", "Spending insights", "insights"),
    SearchHit("s-notifications", "Notifications", "Budget, bill and schedule alerts", "notifications"),
    SearchHit("s-import", "Import", "Import transactions from a file", "import"),
    SearchHit("s-export", "Export", "Export to CSV or PDF", "export"),
    SearchHit("s-backup", "Backup & restore", "Back up your data", "backup"),
    SearchHit("s-settings", "Settings", "Profile, currency, customization", "settings"),
    SearchHit("s-add", "Add transaction", "Open Quick Add", "quick_add")
)

private const val MAX_PER_SECTION = 8
private const val MAX_TRANSACTIONS = 30

/** Whole-app search: screens, accounts, categories, budgets, bills, goals, scheduled items and transactions. */
@HiltViewModel
class SearchViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    billRepository: BillRepository,
    goalRepository: GoalRepository,
    recurringRepository: RecurringTransactionRepository,
    budgetRepository: BudgetRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    fun onQueryChange(value: String) {
        queryFlow.value = value
    }

    private val data1 = combine(
        transactionRepository.observeInRange(DateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1))),
        accountRepository.observeAllAccounts(),
        categoryRepository.observeAll()
    ) { transactions, accounts, categories ->
        val sorted = transactions.sortedWith(
            compareByDescending<com.moneta.app.data.repository.Transaction> { it.date }
                .thenByDescending { it.timeMinutesSinceMidnight }
        )
        SearchData1(buildTransactionItems(sorted, accounts, categories), accounts, categories)
    }

    private val data2 = combine(
        billRepository.observeAll(),
        goalRepository.observeActiveGoals(),
        recurringRepository.observeAll(),
        budgetRepository.observeProgress()
    ) { bills, goals, recurring, budgets ->
        SearchData2(bills, goals, recurring, budgets)
    }

    val results: StateFlow<SearchResults> = combine(queryFlow, data1, data2) { query, d1, d2 ->
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            SearchResults(query = query)
        } else {
            fun String?.hit(): Boolean = this?.lowercase()?.contains(needle) == true

            val matchedTx = d1.items.filter { item ->
                val t = item.transaction
                item.title.hit() || item.subtitle.hit() || t.payee.hit() || t.notes.hit() ||
                    t.amount.toEditableDecimalString().contains(needle)
            }
            SearchResults(
                query = query,
                screens = SCREEN_SHORTCUTS.filter { it.title.hit() || it.subtitle.hit() }.take(MAX_PER_SECTION),
                accounts = d1.accounts.filter { it.name.hit() }.take(MAX_PER_SECTION).map {
                    SearchHit("a-${it.id}", it.name, "Balance " + it.currentBalance.formatted(), accountEditRoute(it.id))
                },
                categories = d1.categories.filter { it.name.hit() }.take(MAX_PER_SECTION).map {
                    SearchHit(
                        "c-${it.id}",
                        it.name,
                        it.type.name.lowercase().replaceFirstChar { c -> c.uppercase() } + " category",
                        categoryEditRoute(it.type, it.id)
                    )
                },
                budgets = d2.budgets.filter { (it.categoryName ?: "Overall budget").hit() }.take(MAX_PER_SECTION).map {
                    SearchHit(
                        "b-${it.budget.id}",
                        it.categoryName ?: "Overall budget",
                        "Spent " + it.spent.formatted() + " of " + it.budget.amount.formatted(),
                        "budgets"
                    )
                },
                bills = d2.bills.filter { it.name.hit() }.take(MAX_PER_SECTION).map {
                    SearchHit("bl-${it.id}", it.name, it.amount.formatted() + " due " + it.dueDate, "bills")
                },
                goals = d2.goals.filter { it.name.hit() }.take(MAX_PER_SECTION).map {
                    SearchHit("g-${it.id}", it.name, it.currentAmount.formatted() + " of " + it.targetAmount.formatted(), "goals")
                },
                scheduled = d2.recurring.filter { it.payee.hit() || it.notes.hit() }.take(MAX_PER_SECTION).map {
                    SearchHit(
                        "r-${it.id}",
                        it.payee ?: it.notes ?: "Scheduled transaction",
                        it.amount.formatted() + " next " + it.nextOccurrenceDate,
                        "recurring"
                    )
                },
                transactions = matchedTx.take(MAX_TRANSACTIONS),
                totalTransactionMatches = matchedTx.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())
}
