@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.common.CategoryDonutChart
import com.moneta.app.ui.common.CategoryLegendList
import com.moneta.app.ui.common.ComingSoonPlaceholder
import com.moneta.app.ui.common.IncomeExpenseBarChart
import com.moneta.app.ui.common.SimpleLineChart
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.format.DateTimeFormatter

private val MONTH_LABEL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        val tabs = ReportTab.entries
        val selectedIndex = tabs.indexOf(state.selectedTab)
        ScrollableTabRow(selectedTabIndex = selectedIndex) {
            tabs.forEach { tab ->
                Tab(
                    selected = tab == state.selectedTab,
                    onClick = { viewModel.onTabSelected(tab) },
                    text = { Text(tabLabel(tab)) }
                )
            }
        }

        when (state.selectedTab) {
            ReportTab.OVERVIEW -> OverviewTab(state)
            ReportTab.SPENDING -> BreakdownTab(state.spendingBreakdown, "spending")
            ReportTab.INCOME -> BreakdownTab(state.incomeBreakdown, "income")
            ReportTab.CASH_FLOW -> CashFlowTab(state)
            ReportTab.NET_WORTH -> NetWorthTab(state)
        }
    }
}

private fun tabLabel(tab: ReportTab): String = when (tab) {
    ReportTab.OVERVIEW -> "Overview"
    ReportTab.SPENDING -> "Spending"
    ReportTab.INCOME -> "Income"
    ReportTab.CASH_FLOW -> "Cash flow"
    ReportTab.NET_WORTH -> "Net worth"
}

@Composable
private fun OverviewTab(state: ReportsUiState) {
    LazyColumn(
        contentPadding = PaddingValues(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
                SummaryCard("Income (this month)", state.currentMonthIncome, IncomeGreen, Modifier.weight(1f))
                SummaryCard("Expenses (this month)", state.currentMonthExpense, ExpenseRed, Modifier.weight(1f))
            }
        }
        if (state.monthlyCashFlow.isNotEmpty()) {
            item { Text("Last 6 months", style = MaterialTheme.typography.titleLarge) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    IncomeExpenseBarChart(
                        labels = state.monthlyCashFlow.map { it.monthStart.format(MONTH_LABEL_FORMAT) },
                        incomeValues = state.monthlyCashFlow.map { it.income },
                        expenseValues = state.monthlyCashFlow.map { it.expense },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MonetaSpacing.cardPadding)
                    )
                }
            }
        } else if (!state.isHistoryLoading) {
            item {
                Text(
                    "Add a few months of transactions to see your income/expense history here.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amount: com.moneta.app.core.money.Money,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            MoneyText(amount, color = color)
        }
    }
}

@Composable
private fun BreakdownTab(slices: List<com.moneta.app.domain.usecase.CategorySlice>, kind: String) {
    if (slices.isEmpty()) {
        ComingSoonPlaceholder(
            title = "No $kind data yet",
            body = "Once you record some $kind transactions this month, the breakdown will show up here."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CategoryDonutChart(slices, modifier = Modifier.size(160.dp))
            }
        }
        item { CategoryLegendList(slices) }
    }
}

@Composable
private fun CashFlowTab(state: ReportsUiState) {
    if (state.monthlyCashFlow.isEmpty()) {
        ComingSoonPlaceholder(
            title = "Not enough history yet",
            body = "Cash flow compares income and expenses over time - add a few months of transactions to see it."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
    ) {
        items(state.monthlyCashFlow.size) { index ->
            val month = state.monthlyCashFlow[index]
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text(month.monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelMedium)
                            MoneyText(month.income, color = IncomeGreen)
                        }
                        Column {
                            Text("Expense", style = MaterialTheme.typography.labelMedium)
                            MoneyText(month.expense, color = ExpenseRed)
                        }
                        Column {
                            Text("Net", style = MaterialTheme.typography.labelMedium)
                            MoneyText(month.net, color = if (month.net.isNegative) ExpenseRed else IncomeGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetWorthTab(state: ReportsUiState) {
    if (state.netWorthTrend.size < 2) {
        ComingSoonPlaceholder(
            title = "Not enough history yet",
            body = "Net worth needs at least a couple of months of account history to plot a trend."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        item {
            val latest = state.netWorthTrend.last()
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Text("Net worth today", style = MaterialTheme.typography.labelMedium)
                    MoneyText(latest.netWorth)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SimpleLineChart(
                    values = state.netWorthTrend.map { it.netWorth.minorUnits.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(MonetaSpacing.cardPadding)
                )
            }
        }
    }
}
