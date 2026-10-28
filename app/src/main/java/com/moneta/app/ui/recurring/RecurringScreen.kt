@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moneta.app.ui.common.FabStyle
import com.moneta.app.ui.common.MovableFab
import com.moneta.app.ui.common.SubPageHeader
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.RecurrenceFrequency
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.RecurringTransaction
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.format.DateTimeFormatter

@Composable
fun RecurringScreen(
    onBack: (() -> Unit)?,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit = {},
    viewModel: RecurringViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { SubPageHeader("Scheduled & recurring", onBack) },
        floatingActionButton = {
            MovableFab(onClick = onAdd, contentDescription = "Add recurring transaction", style = FabStyle.OTHER)
        }
    ) { padding ->
        if (!state.isLoading && state.items.isEmpty()) {
            RecurringEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = MonetaSpacing.screenHorizontal, end = MonetaSpacing.screenHorizontal, top = MonetaSpacing.screenHorizontal, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                items(state.items, key = { it.id }) { item ->
                    RecurringRow(
                        item = item,
                        onEdit = { onEdit(item.id) },
                        onToggleActive = { viewModel.setActive(item.id, !item.isActive) },
                        onDelete = { viewModel.delete(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecurringEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No recurring transactions", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text(
            "Set up salary, rent, subscriptions, or any other transaction that repeats on a schedule.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun RecurringRow(item: RecurringTransaction, onEdit: () -> Unit, onToggleActive: () -> Unit, onDelete: () -> Unit) {
    val amountColor = when (item.templateType) {
        TransactionType.INCOME -> IncomeGreen
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.payee ?: item.notes ?: item.templateType.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (item.endDate != null && item.endDate == item.startDate) {
                            "One-time · Scheduled for ${item.nextOccurrenceDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                        } else {
                            "${frequencyLabel(item.frequency, item.intervalCount)} · Next: ${item.nextOccurrenceDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MoneyText(item.amount, color = amountColor)
            }
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item.isActive) "Active" else "Paused", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = item.isActive, onCheckedChange = { onToggleActive() })
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit recurring transaction")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete recurring transaction")
                    }
                }
            }
        }
    }
}

private fun frequencyLabel(frequency: RecurrenceFrequency, interval: Int): String {
    if (frequency == RecurrenceFrequency.WEEKDAYS) return "Working days (Mon–Fri)"
    if (frequency == RecurrenceFrequency.WEEKENDS) return "Weekends (Sat & Sun)"
    val unit = when (frequency) {
        RecurrenceFrequency.DAILY -> "day"
        RecurrenceFrequency.WEEKLY -> "week"
        RecurrenceFrequency.MONTHLY -> "month"
        RecurrenceFrequency.YEARLY -> "year"
        RecurrenceFrequency.WEEKDAYS, RecurrenceFrequency.WEEKENDS -> "day"
    }
    return if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
}
