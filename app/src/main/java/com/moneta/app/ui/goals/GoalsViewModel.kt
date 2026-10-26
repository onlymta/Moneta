package com.moneta.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.Goal
import com.moneta.app.data.repository.GoalRepository
import com.moneta.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class GoalSort(val label: String) {
    MANUAL("My order"),
    DATE("Earliest date"),
    AMOUNT("Smallest first")
}

/** Whether the savings money, handed out in priority order, covers this goal. */
enum class FundStatus { DONE, FULL, PARTIAL, NONE }

data class GoalItem(
    val goal: Goal,
    val status: FundStatus,
    /** How much of what is still missing is covered by free savings right now. */
    val covered: Money,
    /** How much more is needed beyond what savings can cover. */
    val stillNeeded: Money,
    /** What to put aside each month to reach the date (null when there is no date, or nothing is missing). */
    val perMonth: Money?
)

data class GoalsUiState(
    val isLoading: Boolean = true,
    val items: List<GoalItem> = emptyList(),
    val sort: GoalSort = GoalSort.MANUAL,
    val currencyCode: String = "PKR",
    /** Money in the accounts chosen to pay for goals. */
    val poolBalance: Money = Money.zero("PKR"),
    /** Money already counted as saved inside goals. */
    val earmarked: Money = Money.zero("PKR"),
    /** Savings not yet given to any goal; this is what the priority list hands out. */
    val free: Money = Money.zero("PKR"),
    val accounts: List<Account> = emptyList(),
    val selectedAccountIds: Set<Long> = emptySet(),
    val usingAllSavings: Boolean = true
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<GoalsUiState> = combine(
        goalRepository.observeActiveGoals(),
        accountRepository.observeAllAccounts(),
        settingsRepository.preferences
    ) { goals, accounts, prefs ->
        val currency = prefs.currencyCode
        val live = accounts.filter { !it.isArchived }
        val pool = if (prefs.goalAccountIds.isEmpty()) {
            live.filter { it.type == AccountType.SAVINGS }
        } else {
            live.filter { it.id in prefs.goalAccountIds }
        }
        val poolMinor = pool.sumOf { it.currentBalance.minorUnits }
        val earmarkedMinor = goals.sumOf { it.currentAmount.minorUnits }
        var free = (poolMinor - earmarkedMinor).coerceAtLeast(0L)
        val freeAtStart = free

        val sort = GoalSort.entries.firstOrNull { it.name == prefs.goalSort } ?: GoalSort.MANUAL
        val ordered = orderGoals(goals, sort, prefs.goalOrder)
        val today = LocalDate.now()
        val items = ordered.map { goal ->
            val need = goal.remaining.minorUnits
            val status: FundStatus
            val covered: Long
            when {
                goal.isComplete -> { status = FundStatus.DONE; covered = 0L }
                free >= need -> { status = FundStatus.FULL; covered = need; free -= need }
                free > 0L -> { status = FundStatus.PARTIAL; covered = free; free = 0L }
                else -> { status = FundStatus.NONE; covered = 0L }
            }
            val missing = (need - covered).coerceAtLeast(0L)
            val date = goal.targetDate
            val perMonth = if (date != null && missing > 0L && date.isAfter(today)) {
                val months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), date.withDayOfMonth(1)).coerceAtLeast(1L)
                Money(missing / months, currency)
            } else null
            GoalItem(goal, status, Money(covered, currency), Money(missing, currency), perMonth)
        }
        GoalsUiState(
            isLoading = false,
            items = items,
            sort = sort,
            currencyCode = currency,
            poolBalance = Money(poolMinor, currency),
            earmarked = Money(earmarkedMinor, currency),
            free = Money(freeAtStart, currency),
            accounts = live,
            selectedAccountIds = prefs.goalAccountIds,
            usingAllSavings = prefs.goalAccountIds.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    private fun orderGoals(goals: List<Goal>, sort: GoalSort, manual: List<Long>): List<Goal> = when (sort) {
        GoalSort.DATE -> goals.sortedWith(
            compareBy<Goal> { it.targetDate == null }.thenBy { it.targetDate?.toEpochDay() ?: Long.MAX_VALUE }
        )
        GoalSort.AMOUNT -> goals.sortedBy { it.remaining.minorUnits }
        GoalSort.MANUAL -> {
            val position = manual.withIndex().associate { it.value to it.index }
            goals.sortedBy { position[it.id] ?: Int.MAX_VALUE }
        }
    }

    fun setSort(sort: GoalSort) {
        viewModelScope.launch {
            // Switching to "My order" starts from the order currently on screen.
            if (sort == GoalSort.MANUAL) settingsRepository.setGoalOrder(uiState.value.items.map { it.goal.id })
            settingsRepository.setGoalSort(sort.name)
        }
    }

    /** Moves a goal one place up (-1) or down (+1) in the user's own order and switches to that order. */
    fun move(goalId: Long, delta: Int) {
        val ids = uiState.value.items.map { it.goal.id }.toMutableList()
        val index = ids.indexOf(goalId)
        val target = index + delta
        if (index < 0 || target < 0 || target >= ids.size) return
        ids.removeAt(index)
        ids.add(target, goalId)
        viewModelScope.launch {
            settingsRepository.setGoalOrder(ids)
            settingsRepository.setGoalSort(GoalSort.MANUAL.name)
        }
    }

    fun setAccounts(ids: Set<Long>) {
        viewModelScope.launch { settingsRepository.setGoalAccounts(ids) }
    }

    fun setTargetDate(goal: Goal, date: LocalDate?) {
        viewModelScope.launch { goalRepository.updateDetails(goal.copy(targetDate = date)) }
    }

    fun archiveGoal(goalId: Long) {
        viewModelScope.launch { goalRepository.archive(goalId) }
    }
}
