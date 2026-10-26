@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.goals

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontWeight
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.WarningAmber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.moneta.app.data.repository.Goal
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.format.DateTimeFormatter

@Composable
fun GoalsScreen(
    onBack: (() -> Unit)?,
    onAddGoal: () -> Unit,
    onContribute: (Long) -> Unit,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pickingAccounts by remember { mutableStateOf(false) }
    var dateFor by remember { mutableStateOf<Goal?>(null) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { SubPageHeader("Goals", onBack) },
        floatingActionButton = {
            MovableFab(onClick = onAddGoal, contentDescription = "Add goal", style = FabStyle.OTHER)
        }
    ) { padding ->
        if (!state.isLoading && state.items.isEmpty()) {
            GoalsEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = MonetaSpacing.screenHorizontal, end = MonetaSpacing.screenHorizontal, top = MonetaSpacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                item { SavingsSummary(state, onPickAccounts = { pickingAccounts = true }) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text("Order", style = MaterialTheme.typography.labelLarge)
                        GoalSort.entries.forEach { option ->
                            FilterChip(
                                selected = state.sort == option,
                                onClick = { viewModel.setSort(option) },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
                items(state.items, key = { it.goal.id }) { item ->
                    val index = state.items.indexOf(item)
                    GoalRow(
                        item = item,
                        number = index + 1,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.items.size - 1,
                        onMove = { delta -> viewModel.move(item.goal.id, delta) },
                        onContribute = { onContribute(item.goal.id) },
                        onArchive = { viewModel.archiveGoal(item.goal.id) },
                        onSetDate = { dateFor = item.goal }
                    )
                }
            }
        }
    }

    if (pickingAccounts) {
        var chosen by remember { mutableStateOf(state.selectedAccountIds) }
        AlertDialog(
            onDismissRequest = { pickingAccounts = false },
            title = { Text("Accounts that pay for goals") },
            text = {
                Column {
                    Text(
                        "Pick the accounts whose money should go to your goals. Leave all unticked to use every Savings account.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    state.accounts.forEach { account ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = account.id in chosen,
                                onCheckedChange = { on -> chosen = if (on) chosen + account.id else chosen - account.id }
                            )
                            Text(account.name + " (" + account.currentBalance.formatted() + ")", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAccounts(chosen)
                    pickingAccounts = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pickingAccounts = false }) { Text("Cancel") } }
        )
    }

    dateFor?.let { goal ->
        GoalDatePickerDialog(
            initial = goal.targetDate,
            onPick = { date ->
                viewModel.setTargetDate(goal, date)
                dateFor = null
            },
            onDismiss = { dateFor = null }
        )
    }
}

/** A date picker used for a goal's target date; "No date" clears it. */
@Composable
fun GoalDatePickerDialog(initial: LocalDate?, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                onPick(millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("Set date") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("No date") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun SavingsSummary(state: GoalsUiState, onPickAccounts: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Savings for goals", style = MaterialTheme.typography.labelLarge)
            Text(state.poolBalance.formatted(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                "Already counted in goals: " + state.earmarked.formatted() + " · Free to hand out: " + state.free.formatted(),
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.usingAllSavings) "From: all Savings accounts"
                    else "From: " + state.accounts.filter { it.id in state.selectedAccountIds }.joinToString { it.name },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPickAccounts) { Text("Change") }
            }
            Text(
                "Free savings are given to goals from the top of the list down.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun GoalsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No goals yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text(
            "Set a savings target - an emergency fund, a trip, a big purchase - with or without a date. Then order them and see which your savings can cover.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun GoalRow(
    item: GoalItem,
    number: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onContribute: () -> Unit,
    onArchive: () -> Unit,
    onSetDate: () -> Unit
) {
    val goal = item.goal
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$number. ${goal.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    goal.targetDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "No date",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(MonetaSpacing.sm))
            LinearProgressIndicator(
                progress = { ((goal.percentComplete ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                color = IncomeGreen,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(goal.currentAmount)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(goal.targetAmount)
                }
            }
            Spacer(Modifier.height(MonetaSpacing.xs))
            val (text, color) = when (item.status) {
                FundStatus.DONE -> "Goal reached" to IncomeGreen
                FundStatus.FULL -> ("Your savings can cover the remaining " + goal.remaining.formatted()) to IncomeGreen
                FundStatus.PARTIAL -> (
                    "Savings cover " + item.covered.formatted() + ", needs " + item.stillNeeded.formatted() + " more"
                    ) to WarningAmber
                FundStatus.NONE -> ("Needs " + item.stillNeeded.formatted() + " more") to ExpenseRed
            }
            Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color)
            item.perMonth?.let {
                Text(
                    "About " + it.formatted() + " a month to reach it by the date",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(MonetaSpacing.xs))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onMove(-1) }, enabled = canMoveUp) { Text("Up") }
                TextButton(onClick = { onMove(1) }, enabled = canMoveDown) { Text("Down") }
                TextButton(onClick = onSetDate) { Text("Date") }
                TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onContribute) { Text(if (goal.isComplete) "Reached!" else "Add money") }
            }
        }
    }
}
