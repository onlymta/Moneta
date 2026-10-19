package com.moneta.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moneta.app.ui.navigation.accountHistoryRoute
import com.moneta.app.ui.transactions.TypeFilter
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
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    navController: NavHostController,
    userName: String = "",
    onTransactionClick: (Long) -> Unit = {},
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
            onOpenAccount = { id -> navController.navigate(accountHistoryRoute(id)) },
            onTransactionClick = onTransactionClick,
            onSeeAll = {
                viewModel.prepareRecordsAll()
                navController.navigate(MonetaDestination.Transactions.route) {
                    popUpTo(MonetaDestination.Home.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onOpenRecords = { type ->
                viewModel.prepareRecords(type)
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
    onOpenAccount: (Long) -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenRecords: (TypeFilter?) -> Unit
) {
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
        if (userName.isNotBlank()) {
            item { Text("Hello, $userName", style = MaterialTheme.typography.headlineSmall) }
        }
        item { BalanceCard(state, onOpenAccount) }
        item {
            PeriodBar(
                selection = state.period,
                onChange = onPeriodChange,
                modes = listOf(PeriodMode.MONTH)
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
                FigureTile(
                    label = "Income",
                    amount = state.monthIncome,
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenRecords(TypeFilter.INCOME) }
                )
                FigureTile(
                    label = "Expenses",
                    amount = state.monthExpense,
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenRecords(TypeFilter.EXPENSE) }
                )
            }
        }
        item {
            val net = state.netChange
            FigureTile(
                label = if (state.isCurrentMonth) "Net change so far" else "Net change",
                amount = net,
                color = if (net.isNegative) ExpenseRed else IncomeGreen,
                modifier = Modifier.fillMaxWidth(),
                caption = state.openingBalance?.let { "Opening " + it.formatted() + "  →  Closing " + (it + net).formatted() },
                onClick = { onOpenRecords(null) }
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Last 5 transactions", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }
        if (state.recentItems.isEmpty()) {
            item {
                Text(
                    "Nothing recorded yet. Tap + to add your first transaction.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.recentItems, key = { it.transaction.id }) { item ->
                TransactionRowCard(
                    item = item,
                    showDate = true,
                    onClick = { onTransactionClick(item.transaction.id) }
                )
            }
        }
    }
}

@Composable
private fun FigureTile(
    label: String,
    amount: Money,
    color: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: () -> Unit
) {
    Card(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(MonetaSpacing.xs))
            MoneyText(amount, color = color)
            caption?.let {
                Spacer(Modifier.height(MonetaSpacing.xs))
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Total balance: just the figure by default; tap to see each account (tap an account for its history). */
@Composable
private fun BalanceCard(state: HomeUiState, onOpenAccount: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total balance", style = MaterialTheme.typography.labelLarge)
                    Text(
                        state.totalBalance.formatted(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (expanded) "Tap to hide accounts" else "Tap to see ${state.accounts.size} account(s)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            if (expanded) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                Divider()
                state.accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAccount(account.id) }
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
}
