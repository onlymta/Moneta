package com.moneta.app.ui.dashboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.moneta.app.core.money.Money
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.data.repository.BudgetProgress
import com.moneta.app.data.repository.BudgetStatus
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.ui.common.CategoryDonutChart
import com.moneta.app.ui.common.CategoryLegendList
import com.moneta.app.ui.common.IconBadge
import com.moneta.app.ui.common.PeriodBar
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.TransactionRowCard
import com.moneta.app.ui.navigation.ACCOUNT_FORM_ADD_ROUTE
import com.moneta.app.ui.navigation.MonetaDestination
import com.moneta.app.ui.navigation.transactionDetailRoute
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    navController: NavHostController,
    userName: String = "",
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    when {
        state.isLoading -> LoadingState()
        !state.hasAnyAccounts -> HomeEmptyState(navController)
        else -> HomeContent(
            state = state,
            userName = userName,
            onPeriodChange = viewModel::onPeriodChange,
            onTransactionClick = { id -> navController.navigate(transactionDetailRoute(id)) },
            onSeeAll = {
                navController.navigate(MonetaDestination.Transactions.route) {
                    popUpTo(MonetaDestination.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            }
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

/** Every major section needs a useful empty state, never a blank screen. */
@Composable
private fun HomeEmptyState(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Moneta", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text(
            "Add your first account to start tracking your money.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(MonetaSpacing.lg))
        Button(onClick = { navController.navigate(ACCOUNT_FORM_ADD_ROUTE) }) {
            Text("Add account")
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    userName: String,
    onPeriodChange: (com.moneta.app.ui.common.PeriodSelection) -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSeeAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MonetaSpacing.screenHorizontal,
            end = MonetaSpacing.screenHorizontal,
            top = MonetaSpacing.sm,
            bottom = MonetaSpacing.xxxl * 2
        ),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
    ) {
        if (userName.isNotBlank()) {
            item { Text("Hello, $userName", style = MaterialTheme.typography.headlineSmall) }
        }
        item { BalanceCard(state) }
        item {
            PeriodBar(
                selection = state.period,
                onChange = onPeriodChange,
                modes = listOf(PeriodMode.MONTH)
            )
        }
        item { MonthSummaryCard(state) }

        if (state.spendingBreakdown.isNotEmpty()) {
            item { Text("Where it went", style = MaterialTheme.typography.titleMedium) }
            item { SpendingBreakdownCard(state.spendingBreakdown) }
        }

        if (state.isCurrentMonth && state.topBudgets.isNotEmpty()) {
            item { Text("Budget status", style = MaterialTheme.typography.titleMedium) }
            items(state.topBudgets, key = { "budget-${it.budget.id}" }) { progress ->
                BudgetStatusRow(progress)
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }
        if (state.recentItems.isEmpty()) {
            item { RecentEmptyState(state.period.label()) }
        } else {
            items(state.recentItems, key = { it.transaction.id }) { item ->
                TransactionRowCard(item = item, showDate = true, onClick = { onTransactionClick(item.transaction.id) })
            }
        }
    }
}

@Composable
private fun BalanceCard(state: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Total balance", style = MaterialTheme.typography.labelLarge)
            Text(
                state.totalBalance.formatted(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Across ${state.accounts.size} account(s), as of today",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            Divider()
            state.accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MonetaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(iconName = account.icon, colorHex = account.colorHex, size = 32.dp)
                    Spacer(Modifier.width(MonetaSpacing.md))
                    Text(
                        account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    MoneyText(
                        account.currentBalance,
                        color = if (account.currentBalance.isNegative) ExpenseRed else Color.Unspecified
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthSummaryCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text(state.period.label(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Income", style = MaterialTheme.typography.labelMedium)
                    MoneyText(state.monthIncome, color = IncomeGreen)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Expenses", style = MaterialTheme.typography.labelMedium)
                    MoneyText(state.monthExpense, color = ExpenseRed)
                }
            }
            val trend = state.trend
            if (trend.hasPriorMonthData && trend.expenseDeltaPercent != null) {
                val percentText = "%.0f".format(trend.expenseDeltaPercent)
                Text(
                    if (trend.isExpenseUp) "Spending is $percentText% higher than the month before"
                    else "Spending is $percentText% lower than the month before",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (trend.isExpenseUp) ExpenseRed else IncomeGreen,
                    modifier = Modifier.padding(top = MonetaSpacing.xs)
                )
            }
            Spacer(Modifier.height(MonetaSpacing.sm))
            Divider()
            Spacer(Modifier.height(MonetaSpacing.sm))
            state.openingBalance?.let { opening ->
                SummaryLine("Opening balance (carried forward)", opening, Color.Unspecified)
            }
            SummaryLine(
                if (state.isCurrentMonth) "Net change so far (income - expenses)" else "Net change (income - expenses)",
                state.netChange,
                if (state.netChange.isNegative) ExpenseRed else IncomeGreen
            )
            state.openingBalance?.let { opening ->
                SummaryLine(
                    if (state.isCurrentMonth) "Balance so far this month" else "Balance at month end",
                    opening + state.netChange,
                    Color.Unspecified,
                    bold = true
                )
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, amount: Money, color: Color, bold: Boolean = false) {
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

/** A simple donut plus legend, drawn with Canvas (no chart library). */
@Composable
private fun SpendingBreakdownCard(slices: List<CategorySlice>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryDonutChart(slices, modifier = Modifier.size(96.dp))
            Spacer(Modifier.width(MonetaSpacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                CategoryLegendList(slices.take(5))
            }
        }
    }
}

@Composable
private fun BudgetStatusRow(progress: BudgetProgress) {
    val statusColor = when (progress.status) {
        BudgetStatus.UNDER -> IncomeGreen
        BudgetStatus.NEAR -> WarningAmber
        BudgetStatus.OVER -> ExpenseRed
    }
    val title = if (progress.budget.scope == BudgetScope.OVERALL) "Overall" else progress.categoryName ?: "Category"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    progress.spent.formatted() + " of " + progress.budget.amount.formatted(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(MonetaSpacing.xs))
            LinearProgressIndicator(
                progress = { ((progress.percentUsed ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                color = statusColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RecentEmptyState(periodLabel: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Nothing recorded in $periodLabel", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            Text(
                "Tap the + button to record an expense or income.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
