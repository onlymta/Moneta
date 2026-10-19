package com.moneta.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.Money
import com.moneta.app.data.repository.BudgetStatus
import com.moneta.app.ui.common.IconBadge
import com.moneta.app.ui.common.PeriodBar
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.WarningAmber

private fun statusColor(status: BudgetStatus?): Color = when (status) {
    BudgetStatus.OVER -> ExpenseRed
    BudgetStatus.NEAR -> WarningAmber
    else -> IncomeGreen
}

@Composable
fun BudgetsScreen(onOpenRecords: () -> Unit, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<BudgetLine?>(null) }
    var deleting by remember { mutableStateOf<BudgetLine?>(null) }
    val monthKey = state.period.range().start
    var setupStarted by remember(monthKey) { mutableStateOf(false) }
    val showSetup = state.hasAnyBudget || setupStarted

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MonetaSpacing.screenHorizontal,
            end = MonetaSpacing.screenHorizontal,
            top = MonetaSpacing.sm,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
    ) {
        item {
            PeriodBar(
                selection = state.period,
                onChange = viewModel::onPeriodChange,
                modes = listOf(PeriodMode.MONTH)
            )
        }
        if (!state.isLoading && !showSetup) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Text("No budgets for ${state.period.label()}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(MonetaSpacing.xs))
                        Text(
                            "Each month has its own budgets. Copy last month's, or set this month's from scratch.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(MonetaSpacing.md))
                        state.copySourceLabel?.let { label ->
                            Button(onClick = viewModel::copyFromPrevious, modifier = Modifier.fillMaxWidth()) {
                                Text("Copy budgets from $label")
                            }
                            Spacer(Modifier.height(MonetaSpacing.sm))
                        }
                        if (state.prevMonthSpent.isNotEmpty()) {
                            OutlinedButton(
                                onClick = viewModel::autoSetFromPreviousSpending,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Auto-set from ${state.prevMonthLabel} spending")
                            }
                            Spacer(Modifier.height(MonetaSpacing.sm))
                        }
                        OutlinedButton(onClick = { setupStarted = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Set this month's budgets")
                        }
                    }
                }
            }
        }
        if (showSetup) {
            item { SummaryCard(state) }
        }

        state.overall?.takeIf { showSetup }?.let { overall ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Overall monthly limit", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { viewModel.deleteBudget(overall.budgetId) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete overall limit")
                            }
                        }
                        val over = overall.remaining.isNegative
                        val color = if (over) ExpenseRed else IncomeGreen
                        LinearProgressIndicator(
                            progress = { ((overall.percentUsed ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                            color = color,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Spent ${overall.spent.formatted()} of ${overall.budget.formatted()}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (if (over) "Exceeded by " else "Left ") + overall.remaining.abs().formatted(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        if (showSetup) item {
            Text(
                "Your expense categories",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = MonetaSpacing.sm)
            )
            Text(
                "Tap a category with a budget to see its transactions, or tap Set budget.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showSetup && !state.isLoading && state.lines.isEmpty()) {
            item {
                Text(
                    "No expense categories yet. Add some from the side menu (Categories).",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        if (showSetup) {
            items(state.lines, key = { it.category.id }) { line ->
                CategoryBudgetCard(
                    line = line,
                    onOpen = { editing = line },
                    onDelete = { deleting = line },
                    onSeeTransactions = {
                        viewModel.prepareRecords(line)
                        onOpenRecords()
                    }
                )
            }
        }
    }

    editing?.let { line ->
        BudgetAmountDialog(
            line = line,
            onSave = { input ->
                val ok = viewModel.saveBudget(line, input)
                if (ok) editing = null
                ok
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { line ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove budget?") },
            text = { Text("The monthly budget for ${line.category.name} will be removed. Your transactions are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    line.budgetId?.let { viewModel.deleteBudget(it) }
                    deleting = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SummaryCard(state: BudgetsUiState) {
    val over = state.totalRemaining.isNegative
    val percent = state.totalSpentOnBudgeted.percentOf(state.totalBudget) ?: 0.0
    val color = if (over) ExpenseRed else IncomeGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Total budget", style = MaterialTheme.typography.labelLarge)
            Text(
                state.totalBudget.formatted(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Spent", style = MaterialTheme.typography.labelMedium)
                    Text(state.totalSpentOnBudgeted.formatted(), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (over) "Exceeded by" else "Remaining", style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.totalRemaining.abs().formatted(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
            if (!state.hasAnyBudget) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                Text(
                    "No budgets yet. Pick a category below to set one.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!state.spentOutsideBudgets.isZero) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                Text(
                    "Plus ${state.spentOutsideBudgets.formatted()} spent in categories without a budget.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetCard(
    line: BudgetLine,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onSeeTransactions: () -> Unit
) {
    val budget = line.effectiveBudget
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = if (budget == null) onOpen else onSeeTransactions)) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(iconName = line.category.icon, colorHex = line.category.colorHex)
                Spacer(Modifier.width(MonetaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.category.name, style = MaterialTheme.typography.titleMedium)
                    if (budget == null) {
                        Text(
                            if (line.spent.isZero) "No budget set" else "Spent ${line.spent.formatted()} · no budget set",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Budget ${budget.formatted()}" +
                                (if (!line.carriedOver.isZero) " (incl. ${line.carriedOver.formatted()} carried over)" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (budget == null) {
                    TextButton(onClick = onOpen) { Text("Set budget") }
                } else {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Budget options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit amount") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpen()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete budget") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
            if (budget != null) {
                val color = statusColor(line.status)
                val remaining = line.remaining ?: Money.zero(budget.currencyCode)
                Spacer(Modifier.height(MonetaSpacing.sm))
                LinearProgressIndicator(
                    progress = { ((line.percentUsed ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MonetaSpacing.sm))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(line.spent, color = color)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (remaining.isNegative) "Exceeded by" else "Remaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        MoneyText(remaining.abs(), color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetAmountDialog(line: BudgetLine, onSave: (String) -> Boolean, onDismiss: () -> Unit) {
    var input by remember(line.category.id) {
        mutableStateOf(line.baseBudget?.toEditableDecimalString() ?: "")
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (line.budgetId == null) "Set budget" else "Edit budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                Text(line.category.name, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12)
                        error = null
                    },
                    label = { Text("Monthly budget") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onSave(input)) error = "Enter an amount greater than zero."
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
