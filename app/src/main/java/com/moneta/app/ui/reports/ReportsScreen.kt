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
import androidx.compose.material3.ScrollableTabRow
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
    onOpenCategory: (Long, Long) -> Unit = { _, _ -> },
    viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodBar(
            selection = state.period,
            onChange = viewModel::onPeriodChange,
            modifier = Modifier.padding(horizontal = MonetaSpacing.screenHorizontal)
        )
        val tabs = ReportTab.entries
        ScrollableTabRow(selectedTabIndex = tabs.indexOf(state.tab), edgePadding = 8.dp) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = { Text(tab.label) }
                )
            }
        }

        when (state.tab) {
            ReportTab.OVERVIEW -> OverviewTab(state, viewModel::onTabSelected, onOpenBudgets)
            ReportTab.SPENDING -> BreakdownTab(state.spendingSlices, "spending", state.period.label(), state, onOpenCategory)
            ReportTab.INCOME -> BreakdownTab(state.incomeSlices, "income", state.period.label(), null, onOpenCategory)
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

/** One line of the summary statement; tappable when [onClick] is given. */
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
        .padding(horizontal = MonetaSpacing.cardPadding, vertical = 10.dp)
    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (sign != null) {
            Text(sign + " ", style = MaterialTheme.typography.bodyLarge, color = color)
        }
        MoneyText(amount, color = color)
        if (onClick != null) {
            Text(
                "  ›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun BreakdownTab(slices: List<CategorySlice>, kind: String, periodLabel: String,
    spendingState: ReportsUiState?,
    onOpenCategory: (Long, Long) -> Unit
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
            val canOpen = spendingState != null && sliceId != null
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (canOpen) it.clickable { sliceId?.let { id -> onOpenCategory(id, spendingState?.period?.range()?.start?.toEpochDay() ?: 0L) } } else it }
            ) {
                Column(modifier = Modifier.padding(horizontal = MonetaSpacing.md, vertical = MonetaSpacing.sm)) {
                    SliceRow(slice, showBar = true)
                }
            }
        }
        if (spendingState != null) {
            item {
                Text(
                    "Tap a category for a closer look.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InsightDialog(insight: CategoryInsight, monthMode: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(insight.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                InsightLine("Spent", insight.spent.formatted())
                InsightLine("Average per day", insight.avgPerDay.formatted() + "  (" + insight.daysElapsed + " day(s))")
                val prev = insight.prevSpent
                if (prev != null) {
                    InsightLine("Previous period", prev.formatted())
                    val diff = insight.spent.minorUnits - prev.minorUnits
                    val text = when {
                        prev.minorUnits == 0L && diff > 0L -> "Nothing spent here last period"
                        prev.minorUnits == 0L -> "No change"
                        diff > 0L -> "Up " + (diff * 100 / prev.minorUnits) + "% on last period"
                        diff < 0L -> "Down " + (-diff * 100 / prev.minorUnits) + "% on last period"
                        else -> "Same as last period"
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (diff > 0L) ExpenseRed else IncomeGreen
                    )
                }
                Divider()
                if (insight.isCurrent) {
                    InsightLine(
                        if (monthMode) "Expected next month (same pace)" else "Projected for this period",
                        insight.projected.formatted()
                    )
                }
                val budget = insight.budget
                if (budget != null) {
                    InsightLine("Budget", budget.formatted())
                    val gap = budget.minorUnits - insight.projected.minorUnits
                    val verdict = when {
                        !insight.isCurrent && insight.spent.minorUnits > budget.minorUnits ->
                            "Went over budget by " + Money(insight.spent.minorUnits - budget.minorUnits, budget.currencyCode).formatted()
                        !insight.isCurrent ->
                            "Stayed within budget, saved " + Money(gap, budget.currencyCode).formatted()
                        gap < 0L ->
                            "At this pace you will exceed the budget by " + Money(-gap, budget.currencyCode).formatted()
                        else ->
                            "At this pace you will save " + Money(gap, budget.currencyCode).formatted()
                    }
                    Text(
                        verdict,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (verdict.contains("exceed") || verdict.contains("over")) ExpenseRed else IncomeGreen
                    )
                } else {
                    Text(
                        "No budget set for this category in this period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
private fun InsightLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AccountsTab(state: ReportsUiState) {
    LazyColumn(contentPadding = reportPadding, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text("Net worth today", style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.netWorth.formatted(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "The total of all your accounts that are counted in your net worth.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(state.accounts, key = { it.id }) { account ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(iconName = account.icon, colorHex = account.colorHex)
                        Spacer(Modifier.width(MonetaSpacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                account.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(
                            account.currentBalance,
                            color = if (account.currentBalance.isNegative) ExpenseRed else Color.Unspecified
                        )
                    }
                    val share = if (state.netWorth.isPositive && account.currentBalance.isPositive) {
                        account.currentBalance.percentOf(state.netWorth) ?: 0.0
                    } else 0.0
                    if (share > 0.0) {
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        LinearProgressIndicator(
                            progress = { (share / 100.0).toFloat().coerceIn(0f, 1f) },
                            color = parseColorOrGray(account.colorHex),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "%.0f%% of your total".format(share),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
