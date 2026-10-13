@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Transaction
import com.moneta.app.domain.usecase.CategorySlice
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing

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
        else -> HomeContent(state, userName)
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

/** Section 30: every major section needs a useful empty state, never a blank screen. */
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
        androidx.compose.material3.Button(
            onClick = { navController.navigate(com.moneta.app.ui.navigation.ACCOUNT_FORM_ADD_ROUTE) }
        ) {
            Text("Add account")
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState, userName: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = MonetaSpacing.screenHorizontal,
            vertical = MonetaSpacing.lg
        ),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        if (userName.isNotBlank()) {
            item { Text("Hello, $userName", style = MaterialTheme.typography.headlineSmall) }
        }
        item { BalanceCard(state) }
        item { IncomeExpenseRow(state) }
        item { SavingsAndTrendRow(state) }
        if (state.topBudgets.isNotEmpty()) {
            item { Text("Budget status", style = MaterialTheme.typography.titleLarge) }
            items(state.topBudgets, key = { "budget-${it.budget.id}" }) { progress ->
                BudgetStatusRow(progress)
            }
        }
        if (state.spendingBreakdown.isNotEmpty()) {
            item { Text("Spending breakdown", style = MaterialTheme.typography.titleLarge) }
            item { SpendingBreakdownCard(state.spendingBreakdown) }
        }
        item {
            Text("Recent transactions", style = MaterialTheme.typography.titleLarge)
        }
        if (state.recentTransactions.isEmpty()) {
            item { RecentEmptyState() }
        } else {
            items(state.recentTransactions, key = { it.id }) { transaction ->
                RecentTransactionRow(transaction)
            }
        }
    }
}

@Composable
private fun SavingsAndTrendRow(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Savings this month", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            MoneyText(
                state.monthSavings,
                color = if (state.monthSavings.isNegative) ExpenseRed else IncomeGreen
            )
            val trend = state.trend
            if (trend.hasPriorMonthData && trend.expenseDeltaPercent != null) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                val percentText = "%.0f".format(trend.expenseDeltaPercent)
                val trendLabel = if (trend.isExpenseUp) {
                    "Spending is up $percentText% vs last month"
                } else {
                    "Spending is down $percentText% vs last month"
                }
                Text(
                    trendLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (trend.isExpenseUp) ExpenseRed else IncomeGreen
                )
            }
        }
    }
}

/** Section 6/17's "spending breakdown" chart - a simple donut plus a legend, drawn with Canvas (no chart library dependency yet). */
@Composable
private fun SpendingBreakdownCard(slices: List<CategorySlice>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.moneta.app.ui.common.CategoryDonutChart(slices, modifier = Modifier.size(96.dp))
            Spacer(Modifier.width(MonetaSpacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                // Top 4 categories in the legend; the rest still count toward the donut but
                // don't clutter the card - full per-category detail lives in Reports (Phase 5).
                com.moneta.app.ui.common.CategoryLegendList(slices.take(4))
            }
        }
    }
}

@Composable
private fun BudgetStatusRow(progress: com.moneta.app.data.repository.BudgetProgress) {
    val statusColor = when (progress.status) {
        com.moneta.app.data.repository.BudgetStatus.UNDER -> IncomeGreen
        com.moneta.app.data.repository.BudgetStatus.NEAR -> com.moneta.app.ui.theme.WarningAmber
        com.moneta.app.data.repository.BudgetStatus.OVER -> ExpenseRed
    }
    val title = if (progress.budget.scope == com.moneta.app.data.local.entity.BudgetScope.OVERALL) {
        "Overall"
    } else {
        progress.categoryName ?: "Category"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                MoneyText(progress.spent, color = statusColor)
            }
            Spacer(Modifier.height(MonetaSpacing.xs))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { ((progress.percentUsed ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                color = statusColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BalanceCard(state: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Total balance", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            MoneyText(state.totalBalance, modifier = Modifier, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun IncomeExpenseRow(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
    ) {
        SummaryCard(title = "Income", amount = state.monthIncome, color = IncomeGreen, modifier = Modifier.weight(1f))
        SummaryCard(title = "Expenses", amount = state.monthExpense, color = ExpenseRed, modifier = Modifier.weight(1f))
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
private fun RecentEmptyState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text(
                stringResource(com.moneta.app.R.string.empty_transactions_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(MonetaSpacing.xs))
            Text(
                stringResource(com.moneta.app.R.string.empty_transactions_body),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun RecentTransactionRow(transaction: Transaction) {
    val amountColor = when (transaction.type) {
        TransactionType.INCOME -> IncomeGreen
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.payee ?: transaction.notes ?: transaction.type.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    transaction.date.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(0.dp))
            MoneyText(transaction.amount, color = amountColor)
        }
    }
}
