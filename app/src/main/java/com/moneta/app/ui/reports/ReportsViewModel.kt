package com.moneta.app.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.dao.CategoryTotal
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.Transaction
import com.moneta.app.data.repository.TransactionRepository
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.domain.usecase.CategoryBreakdownUseCase
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.domain.usecase.NetWorthTrendUseCase
import com.moneta.app.domain.usecase.rollUpToTopLevel
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class ReportTab(val label: String) {
    OVERVIEW("Overview"),
    SPENDING("Spending"),
    INCOME("Income"),
    TRENDS("Trends"),
    ACCOUNTS("Accounts")
}

enum class BucketChoice(val label: String) { AUTO("Auto"), DAY("Day"), WEEK("Week"), MONTH("Month") }

data class TrendBucket(val label: String, val shortLabel: String, val incomeMinor: Long, val expenseMinor: Long) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

data class ReportsUiState(
    val isLoading: Boolean = true,
    val period: PeriodSelection = PeriodSelection(mode = PeriodMode.MONTH),
    val tab: ReportTab = ReportTab.OVERVIEW,
    val bucketChoice: BucketChoice = BucketChoice.AUTO,
    val bucketUsed: BucketChoice = BucketChoice.DAY,
    val currencyCode: String = "PKR",
    val income: Money = Money.zero("PKR"),
    val expense: Money = Money.zero("PKR"),
    val transactionCount: Int = 0,
    val spendingSlices: List<CategorySlice> = emptyList(),
    val incomeSlices: List<CategorySlice> = emptyList(),
    val buckets: List<TrendBucket> = emptyList(),
    val labelEvery: Int = 1,
    val openingBalance: Money? = null,
    val accounts: List<Account> = emptyList(),
    val netWorth: Money = Money.zero("PKR")
) {
    val net: Money get() = income - expense
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    settingsRepository: SettingsRepository,
    private val categoryBreakdownUseCase: CategoryBreakdownUseCase,
    private val netWorthTrendUseCase: NetWorthTrendUseCase
) : ViewModel() {

    private val period = MutableStateFlow(PeriodSelection(mode = PeriodMode.MONTH))
    private val tab = MutableStateFlow(ReportTab.OVERVIEW)
    private val bucketChoice = MutableStateFlow(BucketChoice.AUTO)
    private val accountsFlow = accountRepository.observeActiveAccounts()
    private val categoriesFlow = categoryRepository.observeAll()
    private val prefsFlow = settingsRepository.preferences

    private data class Controls(val period: PeriodSelection, val tab: ReportTab, val bucket: BucketChoice)

    private val controls = combine(period, tab, bucketChoice) { p, t, b -> Controls(p, t, b) }

    val uiState: StateFlow<ReportsUiState> = controls.flatMapLatest { c ->
        combine(
            transactionRepository.observeFiltered(range = c.period.range()),
            accountsFlow,
            categoriesFlow,
            prefsFlow
        ) { transactions, accounts, categories, prefs ->
            build(c, transactions, accounts, categories, prefs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun onPeriodChange(selection: PeriodSelection) {
        period.value = selection
    }

    fun onTabSelected(selected: ReportTab) {
        tab.value = selected
    }

    fun onBucketChoice(choice: BucketChoice) {
        bucketChoice.value = choice
    }

    private suspend fun build(
        c: Controls,
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<com.moneta.app.data.repository.Category>,
        prefs: com.moneta.app.data.settings.AppPreferences
    ): ReportsUiState {
        val currency = prefs.currencyCode
        val range = c.period.range()
        val incomeTx = transactions.filter { it.type == TransactionType.INCOME }
        val expenseTx = transactions.filter { it.type == TransactionType.EXPENSE }
        val income = Money(incomeTx.sumOf { it.amount.minorUnits }, currency)
        val expense = Money(expenseTx.sumOf { it.amount.minorUnits }, currency)

        fun totalsOf(list: List<Transaction>): List<CategoryTotal> =
            list.groupBy { it.categoryId }.map { (id, rows) -> CategoryTotal(id, rows.sumOf { it.amount.minorUnits }) }

        val spendingSlices = categoryBreakdownUseCase(rollUpToTopLevel(totalsOf(expenseTx), categories), expense)
        val incomeSlices = categoryBreakdownUseCase(rollUpToTopLevel(totalsOf(incomeTx), categories), income)

        // --- time buckets for the Trends view ---
        val firstDate = transactions.minOfOrNull { it.date }
        val lastDate = transactions.maxOfOrNull { it.date }
        val effStart: LocalDate = if (c.period.mode == PeriodMode.ALL) (firstDate ?: LocalDate.now()) else range.start
        val effEnd: LocalDate = if (c.period.mode == PeriodMode.ALL) (lastDate ?: LocalDate.now()) else range.endExclusive.minusDays(1)
        val days = ChronoUnit.DAYS.between(effStart, effEnd).toInt() + 1

        var used = when (c.bucket) {
            BucketChoice.AUTO -> if (days <= 31) BucketChoice.DAY else if (days <= 120) BucketChoice.WEEK else BucketChoice.MONTH
            else -> c.bucket
        }
        if (used == BucketChoice.DAY && days > 92) used = BucketChoice.WEEK
        if (used == BucketChoice.WEEK && days > 900) used = BucketChoice.MONTH

        fun bucketStart(d: LocalDate): LocalDate = when (used) {
            BucketChoice.WEEK -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            BucketChoice.MONTH -> d.withDayOfMonth(1)
            else -> d
        }

        val incomeBy = HashMap<LocalDate, Long>()
        val expenseBy = HashMap<LocalDate, Long>()
        transactions.forEach { t ->
            val key = bucketStart(t.date)
            if (t.type == TransactionType.INCOME) incomeBy[key] = (incomeBy[key] ?: 0L) + t.amount.minorUnits
            if (t.type == TransactionType.EXPENSE) expenseBy[key] = (expenseBy[key] ?: 0L) + t.amount.minorUnits
        }

        val buckets = ArrayList<TrendBucket>()
        var cursor = bucketStart(effStart)
        val lastBucket = bucketStart(effEnd)
        var guard = 0
        while (!cursor.isAfter(lastBucket) && guard < 400) {
            val next = when (used) {
                BucketChoice.WEEK -> cursor.plusWeeks(1)
                BucketChoice.MONTH -> cursor.plusMonths(1)
                else -> cursor.plusDays(1)
            }
            val label = when (used) {
                BucketChoice.WEEK -> cursor.format(DateTimeFormatter.ofPattern("d MMM")) + " - " +
                    next.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM"))
                BucketChoice.MONTH -> cursor.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                else -> cursor.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            }
            val shortLabel = when (used) {
                BucketChoice.WEEK -> cursor.format(DateTimeFormatter.ofPattern("d MMM"))
                BucketChoice.MONTH -> cursor.format(DateTimeFormatter.ofPattern("MMM"))
                else -> cursor.format(DateTimeFormatter.ofPattern("d"))
            }
            buckets += TrendBucket(label, shortLabel, incomeBy[cursor] ?: 0L, expenseBy[cursor] ?: 0L)
            cursor = next
            guard++
        }
        val labelEvery = if (buckets.size <= 8) 1 else (buckets.size + 7) / 8

        val opening: Money? = if (prefs.carryForwardBalance && c.period.mode != PeriodMode.ALL) {
            netWorthTrendUseCase(listOf(range.start.minusDays(1)), currency).firstOrNull()?.netWorth
        } else {
            null
        }

        return ReportsUiState(
            isLoading = false,
            period = c.period,
            tab = c.tab,
            bucketChoice = c.bucket,
            bucketUsed = used,
            currencyCode = currency,
            income = income,
            expense = expense,
            transactionCount = transactions.size,
            spendingSlices = spendingSlices,
            incomeSlices = incomeSlices,
            buckets = buckets,
            labelEvery = labelEvery,
            openingBalance = opening,
            accounts = accounts,
            netWorth = Money(accounts.filter { it.includeInNetWorth }.sumOf { it.currentBalance.minorUnits }, currency)
        )
    }
}
