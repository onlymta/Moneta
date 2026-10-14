package com.moneta.app.ui.reports

import androidx.compose.foundation.background
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
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
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
            ReportTab.OVERVIEW -> OverviewTab(state)
            ReportTab.SPENDING -> BreakdownTab(state.spendingSlices, "spending", state.period.label())
            ReportTab.INCOME -> BreakdownTab(state.incomeSlices, "income", state.period.label())
            ReportTab.TRENDS -> TrendsTab(state, viewModel::onBucketChoice)
            ReportTab.ACCOUNTS -> AccountsTab(state)
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
private fun OverviewTab(state: ReportsUiState) {
    LazyColumn(contentPadding = reportPadding, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
                StatCard("Income", state.income, IncomeGreen, Modifier.weight(1f))
                StatCard("Expenses", state.expense, ExpenseRed, Modifier.weight(1f))
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    state.openingBalance?.let { opening ->
                        Line("Opening balance", opening, Color.Unspecified)
                    }
                    Line(
                        "Net change (income - expenses)",
                        state.net,
                        if (state.net.isNegative) ExpenseRed else IncomeGreen
                    )
                    state.openingBalance?.let { opening ->
                        Divider(modifier = Modifier.padding(vertical = MonetaSpacing.xs))
                        Line("Closing balance", opening + state.net, Color.Unspecified, bold = true)
                    }
                    Text(
                        "${state.transactionCount} transaction(s) in ${state.period.label()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MonetaSpacing.xs)
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
        if (state.buckets.isNotEmpty() && (!state.income.isZero || !state.expense.isZero)) {
            item { Text("Income vs expenses", style = MaterialTheme.typography.titleMedium) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        ChartLegend()
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        TrendBarChart(
                            labels = state.buckets.map { it.shortLabel },
                            incomeMinor = state.buckets.map { it.incomeMinor },
                            expenseMinor = state.buckets.map { it.expenseMinor },
                            currencyCode = state.currencyCode,
                            labelEvery = state.labelEvery
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
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier
            .size(10.dp)
            .background(IncomeGreen, CircleShape))
        Text("Income", style = MaterialTheme.typography.labelMedium)
        Box(modifier = Modifier
            .size(10.dp)
            .background(ExpenseRed, CircleShape))
        Text("Expenses", style = MaterialTheme.typography.labelMedium)
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
private fun BreakdownTab(slices: List<CategorySlice>, kind: String, periodLabel: String) {
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = MonetaSpacing.md, vertical = MonetaSpacing.sm)) {
                    SliceRow(slice, showBar = true)
                }
            }
        }
    }
}

@Composable
private fun TrendsTab(state: ReportsUiState, onBucketChoice: (BucketChoice) -> Unit) {
    LazyColumn(contentPadding = reportPadding, verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("Group by", style = MaterialTheme.typography.labelLarge)
                BucketChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = state.bucketChoice == choice,
                        onClick = { onBucketChoice(choice) },
                        label = { Text(choice.label) }
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    ChartLegend()
                    Spacer(Modifier.height(MonetaSpacing.sm))
                    TrendBarChart(
                        labels = state.buckets.map { it.shortLabel },
                        incomeMinor = state.buckets.map { it.incomeMinor },
                        expenseMinor = state.buckets.map { it.expenseMinor },
                        currencyCode = state.currencyCode,
                        labelEvery = state.labelEvery
                    )
                }
            }
        }
        items(state.buckets.reversed(), key = { "bucket-${it.label}" }) { bucket ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text(bucket.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelMedium)
                            MoneyText(Money(bucket.incomeMinor, state.currencyCode), color = IncomeGreen)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Expenses", style = MaterialTheme.typography.labelMedium)
                            MoneyText(Money(bucket.expenseMinor, state.currencyCode), color = ExpenseRed)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Net", style = MaterialTheme.typography.labelMedium)
                            MoneyText(
                                Money(bucket.netMinor, state.currencyCode),
                                color = if (bucket.netMinor < 0) ExpenseRed else IncomeGreen
                            )
                        }
                    }
                }
            }
        }
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
