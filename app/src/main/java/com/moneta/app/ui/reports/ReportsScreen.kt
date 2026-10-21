package com.moneta.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moneta.app.ui.common.PeriodMode
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import com.moneta.app.ui.common.TransactionRowCard
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.Money
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.ui.common.CategoryPieChart
import com.moneta.app.ui.common.IconBadge
import com.moneta.app.ui.common.PeriodBar
import com.moneta.app.ui.common.TrendBarChart
import com.moneta.app.ui.common.parseColorOrGray
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onOpenBudgets: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val selectedId by viewModel.selectedCategoryId.collectAsState()

    // Arriving from a long-press on a budget (or a category row in Records): open that category's analysis.
    LaunchedEffect(Unit) { viewModel.applyLaunch() }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodBar(
            selection = state.period,
            onChange = viewModel::onPeriodChange,
            modifier = Modifier.padding(horizontal = MonetaSpacing.screenHorizontal)
        )
        val tabs = ReportTab.entries
        TabRow(selectedTabIndex = tabs.indexOf(state.tab)) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = { Text(tab.label, maxLines = 1) }
                )
            }
        }

        when (state.tab) {
            ReportTab.OVERVIEW -> OverviewTab(state, viewModel::onTabSelected, onOpenBudgets)
            ReportTab.SPENDING, ReportTab.INCOME -> {
                val isIncome = state.tab == ReportTab.INCOME
                if (selectedId != null) {
                    val detail = analysis
                    if (detail == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    } else {
                        CategoryDetail(
                            analysis = detail,
                            onBack = { viewModel.selectCategory(null) },
                            onTransactionClick = onTransactionClick
                        )
                    }
                } else {
                    BreakdownTab(
                        slices = if (isIncome) state.incomeSlices else state.spendingSlices,
                        kind = if (isIncome) "income" else "expenses",
                        periodLabel = state.period.label(),
                        onSelect = { id -> viewModel.selectCategory(id) }
                    )
                }
            }
        }
    }
}

private val reportPadding = PaddingValues(
    start = MonetaSpacing.screenHorizontal,
    end = MonetaSpacing.screenHorizontal,
    top = MonetaSpacing.md,
    bottom = MonetaSpacing.xxxl * 2
)

@Composable
private fun OverviewTab(state: ReportsUiState, onGoTo: (ReportTab) -> Unit, onOpenBudgets: () -> Unit) {
    LazyColumn(contentPadding = reportPadding, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = MonetaSpacing.sm)) {
                    Text(
                        "Summary for ${state.period.label()}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = MonetaSpacing.cardPadding, vertical = MonetaSpacing.xs)
                    )
                    state.openingBalance?.let { opening ->
                        StatementRow("Opening balance", opening, Color.Unspecified, null, null)
                    }
                    StatementRow("Income", state.income, IncomeGreen, "+", { onGoTo(ReportTab.INCOME) })
                    StatementRow("Expenses", state.expense, ExpenseRed, "-", { onGoTo(ReportTab.SPENDING) })
                    state.closingBalance?.let { closing ->
                        Divider(modifier = Modifier.padding(horizontal = MonetaSpacing.cardPadding, vertical = MonetaSpacing.xs))
                        StatementRow(
                            "Closing balance",
                            closing,
                            if (closing.isNegative) ExpenseRed else Color.Unspecified,
                            null,
                            null,
                            bold = true
                        )
                    }
                    if (!state.pendingBudget.isZero) {
                        StatementRow(
                            "Budgeted, not yet spent",
                            state.pendingBudget,
                            MaterialTheme.colorScheme.tertiary,
                            "-",
                            onOpenBudgets
                        )
                    }
                    state.expectedClosing?.let { expected ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MonetaSpacing.sm, vertical = MonetaSpacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = MonetaSpacing.md, vertical = MonetaSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (state.pendingBudget.isZero) "Closing balance" else "Expected closing",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        if (state.pendingBudget.isZero) "Nothing left to spend from budgets" else "After spending the rest of your budgets",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                MoneyText(expected, color = if (expected.isNegative) ExpenseRed else IncomeGreen)
                            }
                        }
                    }
                    Text(
                        "${state.transactionCount} transaction(s). Tap a figure to see its details.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MonetaSpacing.cardPadding, vertical = MonetaSpacing.xs)
                    )
                }
            }
        }
        if (state.spendingSlices.isNotEmpty()) {
            item { Text("Where the money went", style = MaterialTheme.typography.titleMedium) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MonetaSpacing.cardPadding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CategoryPieChart(state.spendingSlices, modifier = Modifier.size(150.dp))
                        Spacer(Modifier.height(MonetaSpacing.md))
                        state.spendingSlices.take(5).forEach { SliceRow(it, showBar = false) }
                    }
                }
            }
        }
        if (state.transactionCount == 0) {
            item {
                Text(
                    "Nothing recorded in ${state.period.label()}. Pick another period above.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * One line of the summary statement; tappable when [onClick] is given. The sign, the figure and the arrow
 * each sit in their own fixed-width column so every row lines up with the one above it.
 */
@Composable
private fun StatementRow(
    label: String,
    amount: Money,
    color: Color,
    sign: String?,
    onClick: (() -> Unit)?,
    bold: Boolean = false
) {
    val base = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = MonetaSpacing.cardPadding, vertical = 12.dp)
    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (sign != null) Text(sign, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.width(132.dp), contentAlignment = Alignment.CenterEnd) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold
                )
            ) {
                MoneyText(amount, color = color)
            }
        }
        Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.CenterEnd) {
            if (onClick != null) {
                Text("\u203A", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, amount: Money, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            MoneyText(amount, color = color)
        }
    }
}

@Composable
private fun Line(label: String, amount: Money, color: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        MoneyText(amount, color = color)
    }
}

@Composable
private fun SliceRow(slice: CategorySlice, showBar: Boolean) {
    val color = parseColorOrGray(slice.colorHex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape))
            Spacer(Modifier.width(MonetaSpacing.sm))
            Text(slice.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            MoneyText(slice.amount)
            Spacer(Modifier.width(MonetaSpacing.sm))
            Text(
                "%.0f%%".format(slice.percentOfTotal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showBar) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (slice.percentOfTotal / 100.0).toFloat().coerceIn(0f, 1f) },
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BreakdownTab(
    slices: List<CategorySlice>,
    kind: String,
    periodLabel: String,
    onSelect: (Long) -> Unit
) {
    if (slices.isEmpty()) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No $kind in $periodLabel", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            Text(
                "Record some $kind or pick another period to see the breakdown.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }
    LazyColumn(contentPadding = reportPadding, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CategoryPieChart(slices, modifier = Modifier.size(200.dp))
            }
        }
        items(slices, key = { "slice-${it.categoryId}-${it.name}" }) { slice ->
            val sliceId = slice.categoryId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (sliceId != null) it.clickable { onSelect(sliceId) } else it }
            ) {
                Column(modifier = Modifier.padding(horizontal = MonetaSpacing.md, vertical = MonetaSpacing.sm)) {
                    SliceRow(slice, showBar = true)
                }
            }
        }
        item {
            Text(
                "Tap a category for a closer look.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = valueColor
        )
    }
}

private fun percentText(value: Double): String = "%.0f%%".format(value)

/**
 * One category's analysis for the period chosen above: amount and share, budget used or left, earlier
 * periods side by side, a few key figures, and (tap the amount) every transaction behind the number.
 */
@Composable
private fun CategoryDetail(analysis: CategoryAnalysis, onBack: () -> Unit, onTransactionClick: (Long) -> Unit) {
    var showTransactions by rememberSaveable(analysis.categoryId) { mutableStateOf(false) }
    val accent = parseColorOrGray(analysis.colorHex)
    val kind = if (analysis.isIncome) "Income" else "Spent"
    LazyColumn(contentPadding = reportPadding, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back to all categories")
                }
                Box(modifier = Modifier.size(12.dp).background(accent, CircleShape))
                Spacer(Modifier.width(MonetaSpacing.sm))
                Text(
                    analysis.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTransactions = !showTransactions }
                        .padding(MonetaSpacing.cardPadding)
                ) {
                    Text(
                        kind + " in " + analysis.periodLabel + (if (analysis.isCurrent) "  (current)" else ""),
                        style = MaterialTheme.typography.labelLarge
                    )
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                    ) {
                        MoneyText(analysis.amount, color = if (analysis.isIncome) IncomeGreen else ExpenseRed)
                    }
                    Text(
                        percentText(analysis.sharePercent) + " of all " + (if (analysis.isIncome) "income" else "expenses") +
                            " (" + analysis.typeTotal.formatted() + ")",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (showTransactions) "Tap to hide the transactions" else "Tap to see each transaction (" + analysis.txCount + ")",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (showTransactions) {
            if (analysis.items.isEmpty()) {
                item { Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(analysis.items, key = { "tx-${it.transaction.id}" }) { entry ->
                    TransactionRowCard(
                        item = entry,
                        showDate = true,
                        onClick = { onTransactionClick(entry.transaction.id) }
                    )
                }
            }
        }
        if (!analysis.isIncome) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Text("Budget", style = MaterialTheme.typography.titleMedium)
                        val budget = analysis.budget
                        if (budget == null) {
                            Text(
                                "No budget was set for this category in this period.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val pct = analysis.percentUsed ?: 0.0
                            val excess = analysis.excess ?: Money.zero(budget.currencyCode)
                            val over = excess.minorUnits > 0L
                            val tint = if (over) ExpenseRed else IncomeGreen
                            DetailLine("Budgeted", budget.formatted() + (if (analysis.budgetIsProRated) " (share of the month)" else ""))
                            DetailLine("Used", analysis.amount.formatted() + "  (" + percentText(pct) + ")")
                            LinearProgressIndicator(
                                progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                color = tint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                            DetailLine(
                                if (over) "Over budget by" else "Remaining",
                                excess.abs().formatted() + "  (" + percentText(if (budget.minorUnits > 0L) kotlin.math.abs(excess.minorUnits) * 100.0 / budget.minorUnits else 0.0) + ")",
                                valueColor = tint,
                                bold = true
                            )
                        }
                    }
                }
            }
        }
        if (analysis.history.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Text(
                            if (analysis.isIncome) "Earlier periods" else "Earlier periods: spent vs budget",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        analysis.history.forEachIndexed { index, row ->
                            if (index > 0) Divider(modifier = Modifier.padding(vertical = 6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(row.amount.formatted(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            val budget = row.budget
                            if (!analysis.isIncome) {
                                if (budget == null) {
                                    Text("No budget", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    val excess = row.excess ?: Money.zero(budget.currencyCode)
                                    val over = excess.minorUnits > 0L
                                    Text(
                                        "Budget " + budget.formatted() + "  \u00B7  " + percentText(row.percentUsed ?: 0.0) + " used  \u00B7  " +
                                            (if (over) "over by " else "unused ") + excess.abs().formatted(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (over) ExpenseRed else IncomeGreen
                                    )
                                }
                            } else {
                                val newer = if (index == 0) analysis.amount else analysis.history[index - 1].amount
                                if (row.amount.minorUnits > 0L) {
                                    val change = (newer.minorUnits - row.amount.minorUnits) * 100.0 / row.amount.minorUnits
                                    Text(
                                        "Next period " + (if (change >= 0) "+" else "") + percentText(change) + " vs this one",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text("Key figures", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    DetailLine("Transactions", analysis.txCount.toString())
                    if (analysis.txCount > 0) {
                        DetailLine("Highest transaction", analysis.highest.formatted())
                        DetailLine("Lowest transaction", analysis.lowest.formatted())
                        DetailLine("Average transaction", analysis.averagePerTransaction.formatted())
                        if (analysis.activeDays * 2 >= analysis.periodDays) {
                            DetailLine("Average per day", analysis.averagePerDay.formatted())
                        } else {
                            DetailLine("Days with activity", analysis.activeDays.toString() + " of " + analysis.periodDays)
                        }
                    }
                    val suggested = analysis.suggested
                    if (suggested != null) {
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        DetailLine("Suggested budget", suggested.formatted(), valueColor = MaterialTheme.colorScheme.primary, bold = true)
                        Text(
                            analysis.suggestedNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
