package com.moneta.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.moneta.app.ui.common.BarGroup
import com.moneta.app.ui.common.BarValue
import com.moneta.app.ui.common.ChartLegend
import com.moneta.app.ui.common.GroupedBarChart
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
    // The system back button closes an open category analysis first, instead of leaving Reports.
    androidx.activity.compose.BackHandler(enabled = selectedId != null) { viewModel.selectCategory(null) }

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
                        tree = if (isIncome) state.incomeTree else state.spendingTree,
                        trend = state.trend,
                        isIncome = isIncome,
                        total = if (isIncome) state.income else state.expense,
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
                        StatementRow(
                            if (state.pendingBudget.isZero) "Closing balance" else "Expected closing",
                            expected,
                            if (expected.isNegative) ExpenseRed else IncomeGreen,
                            null,
                            null,
                            bold = true,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            subtitle = if (state.pendingBudget.isZero) "Nothing left to spend from budgets" else "After spending the rest of your budgets"
                        )
                    }
                }
            }
        }
        if (state.trend.isNotEmpty() && state.trend.any { !it.income.isZero || !it.expense.isZero }) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Text("Income vs expenses", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Last " + state.trend.size + " period(s). The percentage is how much of that period's income was spent.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        ChartLegend(listOf("Income" to IncomeGreen, "Expenses" to ExpenseRed))
                        Spacer(Modifier.height(MonetaSpacing.md))
                        GroupedBarChart(
                            groups = state.trend.map { stat ->
                                BarGroup(
                                    label = stat.label,
                                    bars = listOf(
                                        BarValue(stat.income.minorUnits, IncomeGreen),
                                        BarValue(stat.expense.minorUnits, ExpenseRed)
                                    ),
                                    notes = listOf(stat.expensePercentOfIncome?.let { percentText(it) } ?: "-"),
                                    highlight = stat.isSelected
                                )
                            }
                        )
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
    bold: Boolean = false,
    container: Color? = null,
    subtitle: String? = null
) {
    // A highlighted row sits inside a rounded block; its padding is trimmed so the figures still line up with the rows above.
    val inset = if (container != null) MonetaSpacing.sm else 0.dp
    val base = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(horizontal = MonetaSpacing.cardPadding - inset, vertical = 12.dp)
    val rowContent: @Composable () -> Unit = {
    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
    if (container != null) {
        Surface(
            color = container,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = inset, vertical = MonetaSpacing.xs)
        ) { rowContent() }
    } else {
        rowContent()
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BreakdownTab(
    slices: List<CategorySlice>,
    tree: List<TreeRow>,
    trend: List<PeriodStat>,
    isIncome: Boolean,
    total: Money,
    periodLabel: String,
    onSelect: (Long) -> Unit
) {
    val kind = if (isIncome) "income" else "expenses"
    val accent = if (isIncome) IncomeGreen else ExpenseRed
    var expandedIds by remember { mutableStateOf(setOf<Long>()) }
    val visibleRows = remember(tree, expandedIds) {
        val out = ArrayList<TreeRow>()
        val openAtDepth = HashMap<Int, Boolean>()
        tree.forEach { row ->
            val parentsOpen = (0 until row.depth).all { openAtDepth[it] == true }
            if (parentsOpen) out.add(row)
            val id = row.categoryId
            openAtDepth[row.depth] = parentsOpen && id != null && id in expandedIds
        }
        out
    }
    LazyColumn(contentPadding = reportPadding, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text(
                        (if (isIncome) "Total income" else "Total expenses") + " in " + periodLabel,
                        style = MaterialTheme.typography.labelLarge
                    )
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                    ) {
                        MoneyText(total, color = accent)
                    }
                }
            }
        }
        if (slices.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MonetaSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No $kind in $periodLabel", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    Text(
                        "Record some $kind or pick another period to see the breakdown.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@LazyColumn
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CategoryPieChart(slices, modifier = Modifier.size(180.dp))
            }
        }
        items(visibleRows, key = { "row-${it.categoryId}-${it.name}-${it.depth}" }) { row ->
            val rowId = row.categoryId
            val rowColor = parseColorOrGray(row.colorHex)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (row.depth * 18).dp)
                    .let {
                        if (rowId != null) {
                            it.combinedClickable(
                                onClick = {
                                    if (row.hasChildren) {
                                        expandedIds = if (rowId in expandedIds) expandedIds - rowId else expandedIds + rowId
                                    } else {
                                        onSelect(rowId)
                                    }
                                },
                                onLongClick = { onSelect(rowId) }
                            )
                        } else it
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = MonetaSpacing.md, vertical = MonetaSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier
                            .size(10.dp)
                            .background(rowColor, CircleShape))
                        Spacer(Modifier.width(MonetaSpacing.sm))
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        MoneyText(row.amount)
                        Spacer(Modifier.width(MonetaSpacing.sm))
                        Text(
                            "%.0f%%".format(row.percentOfTotal),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (row.hasChildren) {
                            Icon(
                                if (rowId != null && rowId in expandedIds) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (row.percentOfTotal / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = rowColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (trend.size > 1) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Text(
                            (if (isIncome) "Income" else "Expenses") + ", last " + trend.size + " periods",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(MonetaSpacing.md))
                        GroupedBarChart(
                            groups = trend.map { stat ->
                                val amount = if (isIncome) stat.income else stat.expense
                                BarGroup(
                                    label = stat.label,
                                    bars = listOf(BarValue(amount.minorUnits, accent)),
                                    highlight = stat.isSelected
                                )
                            }
                        )
                    }
                }
            }
        }
        item {
            Text(
                if (tree.any { it.hasChildren }) {
                    "Tap a category with sub-categories to expand it. Tap a sub-category, or press and hold any category, for its analysis."
                } else {
                    "Tap a category for a closer look."
                },
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
        if (analysis.bars.isNotEmpty()) {
            item {
                val actualColor = if (analysis.isIncome) IncomeGreen else ExpenseRed
                val budgetColor = MaterialTheme.colorScheme.tertiary
                val showBudget = !analysis.isIncome && analysis.bars.any { it.budget != null }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Text(
                            analysis.name + ", last " + analysis.bars.size + " periods",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        ChartLegend(
                            buildList {
                                if (showBudget) add("Budget" to budgetColor)
                                add((if (analysis.isIncome) "Income" else "Spent") to actualColor)
                            }
                        )
                        Spacer(Modifier.height(MonetaSpacing.md))
                        GroupedBarChart(
                            groups = analysis.bars.map { bar ->
                                BarGroup(
                                    label = bar.label,
                                    bars = buildList {
                                        if (showBudget) add(BarValue(bar.budget?.minorUnits ?: 0L, budgetColor))
                                        add(BarValue(bar.amount.minorUnits, actualColor))
                                    },
                                    notes = buildList {
                                        if (!analysis.isIncome) add("bud " + (bar.percentOfBudget?.let { percentText(it) } ?: "-"))
                                        add("tot " + percentText(bar.percentOfTotal))
                                    },
                                    highlight = bar.isSelected
                                )
                            }
                        )
                        Text(
                            (if (analysis.isIncome) "" else "bud = share of the budget used. ") +
                                "tot = share of all " + (if (analysis.isIncome) "income" else "expenses") + " in that period.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
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
