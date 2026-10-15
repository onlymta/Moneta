package com.moneta.app.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.data.repository.BillRepository
import com.moneta.app.data.repository.BillUrgency
import com.moneta.app.data.repository.BudgetProgress
import com.moneta.app.data.repository.BudgetRepository
import com.moneta.app.data.repository.BudgetStatus
import com.moneta.app.data.repository.RecurringTransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.moneta.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class AlertLevel { URGENT, WARNING, INFO }

/** One in-app alert. [route] is where tapping it should go. */
data class AppAlert(
    val id: String,
    val level: AlertLevel,
    val title: String,
    val message: String,
    val route: String
)

/**
 * Builds the notification list from what is in the app right now: budgets that
 * are near or over their limit, unpaid bills that are overdue or due soon, and
 * scheduled transactions coming up in the next 7 days. These are in-app alerts
 * only - the phone's notification shade is not used.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    budgetRepository: BudgetRepository,
    private val billRepository: BillRepository,
    recurringRepository: RecurringTransactionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val alerts: StateFlow<List<AppAlert>> = combine(
        budgetRepository.observeProgress(),
        billRepository.observeAll(),
        recurringRepository.observeActive()
    ) { progress, bills, recurring ->
        val today = DateRange.todayDate()
        val out = mutableListOf<AppAlert>()

        progress.forEach { p: BudgetProgress ->
            val name = p.categoryName ?: "Overall budget"
            when (p.status) {
                BudgetStatus.OVER -> out.add(
                    AppAlert(
                        id = "budget-${p.budget.id}-${p.status.name}",
                        level = AlertLevel.URGENT,
                        title = "$name is over budget",
                        message = "Spent ${p.spent.formatted()} of ${p.budget.amount.formatted()}.",
                        route = "budgets"
                    )
                )
                BudgetStatus.NEAR -> out.add(
                    AppAlert(
                        id = "budget-${p.budget.id}-${p.status.name}",
                        level = AlertLevel.WARNING,
                        title = "$name is close to its limit",
                        message = "Spent ${p.spent.formatted()} of ${p.budget.amount.formatted()}.",
                        route = "budgets"
                    )
                )
                BudgetStatus.UNDER -> Unit
            }
        }

        bills.forEach { bill ->
            when (billRepository.urgencyOf(bill, today)) {
                BillUrgency.OVERDUE -> out.add(
                    AppAlert(
                        id = "bill-${bill.id}-${bill.dueDate}",
                        level = AlertLevel.URGENT,
                        title = "${bill.name} is overdue",
                        message = "${bill.amount.formatted()} was due " +
                            bill.dueDate.format(DateTimeFormatter.ofPattern("d MMM")) + ".",
                        route = "bills"
                    )
                )
                BillUrgency.DUE_TODAY -> out.add(
                    AppAlert(
                        id = "bill-${bill.id}-${bill.dueDate}",
                        level = AlertLevel.URGENT,
                        title = "${bill.name} is due today",
                        message = bill.amount.formatted(),
                        route = "bills"
                    )
                )
                BillUrgency.DUE_SOON -> out.add(
                    AppAlert(
                        id = "bill-${bill.id}-${bill.dueDate}",
                        level = AlertLevel.WARNING,
                        title = "${bill.name} is due soon",
                        message = bill.amount.formatted() + " on " +
                            bill.dueDate.format(DateTimeFormatter.ofPattern("d MMM")) + ".",
                        route = "bills"
                    )
                )
                BillUrgency.UPCOMING, BillUrgency.PAID -> Unit
            }
        }

        recurring.forEach { r ->
            val days = ChronoUnit.DAYS.between(today, r.nextOccurrenceDate)
            if (days in 0..7) {
                val label = r.payee ?: r.notes ?: "Scheduled transaction"
                out.add(
                    AppAlert(
                        id = "recurring-${r.id}-${r.nextOccurrenceDate}",
                        level = AlertLevel.INFO,
                        title = "Coming up: $label",
                        message = r.amount.formatted() + " on " +
                            r.nextOccurrenceDate.format(DateTimeFormatter.ofPattern("d MMM")) + ".",
                        route = "recurring"
                    )
                )
            }
        }

        out.sortedBy { it.level.ordinal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val seenIds: StateFlow<Set<String>> = settingsRepository.preferences
        .map { it.seenAlertIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** How many alerts the user hasn't looked at yet (drives the bell badge). */
    val unseenCount: StateFlow<Int> = combine(alerts, seenIds) { list, seen ->
        list.count { it.id !in seen }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAllSeen() {
        viewModelScope.launch {
            settingsRepository.markAlertsSeen(alerts.value.map { it.id }.toSet())
        }
    }
}
