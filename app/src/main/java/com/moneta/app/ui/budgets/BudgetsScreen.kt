package com.moneta.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
fun BudgetsScreen(onOpenRecords: () -> Unit, onOpenReports: () -> Unit = {}, viewModel: BudgetsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<BudgetLine?>(null) }
    var deleting by remember { mutableStateOf<BudgetLine?>(null) }
    val monthKey = state.period.range().start
    var setupStarted by remember(monthKey) { mutableStateOf(false) }
    val showSetup = state.hasAnyBudget || setupStarted
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }
    var reorderingBudgets by remember { mutableStateOf(false) }
    // A line is visible when every ancestor above it is expanded (lines arrive in tree order).
    val visibleLines = remember(state.lines, expandedIds) {
        val out = ArrayList<BudgetLine>()
        val openAtDepth = HashMap<Int, Boolean>()
        state.lines.forEach { line ->
            val parentsOpen = (0 until line.depth).all { openAtDepth[it] == true }
            if (parentsOpen) out.add(line)
            openAtDepth[line.depth] = parentsOpen && line.category.id in expandedIds
        }
        out
    }

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
            var sortMenuOpen by remember { mutableStateOf(false) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Text(
                            "Order: " + (if (state.sortMode == "ORDER") "mine" else "smart"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Smart order") },
                            onClick = {
                                sortMenuOpen = false
                                viewModel.setSort("SMART")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("My order") },
                            onClick = {
                                sortMenuOpen = false
                                viewModel.setSort("ORDER")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reorder...") },
                            onClick = {
                                sortMenuOpen = false
                                reorderingBudgets = true
                            }
                        )
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
                "Tap a category's name or icon to see its transactions. Use the arrow to open its sub-categories. Press and hold the name to open its report.",
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
            items(visibleLines, key = { it.category.id }) { line ->
                CategoryBudgetCard(
                    line = line,
                    expanded = line.category.id in expandedIds,
                    onToggle = {
                        val id = line.category.id
                        expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                    },
                    onOpen = { editing = line },
                    onDelete = { deleting = line },
                    onSeeTransactions = {
                        viewModel.prepareRecords(line)
                        onOpenRecords()
                    },
                    onOpenReport = {
                        viewModel.prepareReports(line)
                        onOpenReports()
                    }
                )
            }
        }
    }

    if (reorderingBudgets) {
        com.moneta.app.ui.common.ReorderDialog(
            title = "Reorder budgets",
            items = state.lines.map { it.category.id to ("    ".repeat(it.depth) + it.category.name) },
            onMostUsed = { ids -> viewModel.mostUsedFirst(ids) },
            onSave = { ids ->
                viewModel.saveOrder(ids)
                reorderingBudgets = false
            },
            onDismiss = { reorderingBudgets = false }
        )
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
private fun OutlookTile(label: String, value: Money, color: Color, modifier: Modifier = Modifier, note: String? = null) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value.formatted(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The budget outlook for the month. Income is not part of it: only budgets and spending. */
@Composable
private fun SummaryCard(state: BudgetsUiState) {
    val over = state.totalRemaining.isNegative
    val percent = state.totalSpentOnBudgeted.percentOf(state.totalBudget) ?: 0.0
    val color = if (over) ExpenseRed else IncomeGreen
    val variance = state.expectedVariance
    val verdictColor = if (variance.minorUnits > 0L) ExpenseRed else IncomeGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Total budget", style = MaterialTheme.typography.labelLarge)
            Text(
                state.totalBudget.formatted(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            LinearProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${percent.toInt()}% of the budget used",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(MonetaSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                OutlookTile("Spent", state.totalSpentOnBudgeted, ExpenseRed, Modifier.weight(1f))
                OutlookTile(
                    if (over) "Exceeded by" else "Remaining",
                    state.totalRemaining.abs(),
                    color,
                    Modifier.weight(1f)
                )
            }
            if (state.hasAnyBudget) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                val excess = variance.minorUnits > 0L
                Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    OutlookTile(
                        if (state.isClosed) "Unused" else "Yet to spend",
                        state.yetToSpend,
                        WarningAmber,
                        Modifier.weight(1f),
                        note = if (state.isClosed) "Budget left over" else "Budgeted, not spent"
                    )
                    OutlookTile(
                        if (state.isClosed) "Final deviation" else "Expected deviation",
                        Money(if (excess) -variance.minorUnits else 0L, variance.currencyCode),
                        verdictColor,
                        Modifier.weight(1f),
                        note = if (excess) "Over budget" else "On track"
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CategoryBudgetCard(
    line: BudgetLine,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onSeeTransactions: () -> Unit,
    onOpenReport: () -> Unit
) {
    val budget = line.effectiveBudget
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(start = (line.depth * 18).dp)) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The icon and name open the transactions (or "set budget" when there is none yet); press and hold for the report.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = { if (!line.hasChildren && budget == null) onOpen() else onSeeTransactions() },
                            onLongClick = onOpenReport
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                IconBadge(iconName = line.category.icon, colorHex = line.category.colorHex)
                Spacer(Modifier.width(MonetaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.label, style = MaterialTheme.typography.titleMedium)
                    if (budget == null) {
                        Text(
                            if (line.hasChildren) {
                                "Total of its sub-categories" + if (line.spent.isZero) "" else " · spent ${line.spent.formatted()}"
                            } else if (line.spent.isZero) "No budget set" else "Spent ${line.spent.formatted()} · no budget set",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            (if (line.derived) "Total budget " else "Budget ") + budget.formatted() +
                                (if (line.carriedOver.isNegative) " (after ${line.carriedOver.abs().formatted()} overspent last month)"
                                else if (!line.carriedOver.isZero) " (incl. ${line.carriedOver.formatted()} carried over)" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                }
                if (line.hasChildren) {
                    IconButton(onClick = onToggle) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                } else if (budget == null) {
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
                    Text(
                        "${(line.percentUsed ?: 0.0).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
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
                    supportingText = { Text(error ?: "Leave blank or enter 0 for no budget this month") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onSave(input)) error = "Enter a valid amount."
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
