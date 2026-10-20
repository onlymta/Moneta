package com.moneta.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.data.repository.Account
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
            onScopeChange = { sc -> viewModel.setBalanceScope(sc) },
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
    onScopeChange: (com.moneta.app.data.settings.BalanceScope) -> Unit,
    onOpenAccount: (Long) -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenRecords: (TypeFilter?) -> Unit
) {
    var dialog by remember { mutableStateOf(HomeDialog.NONE) }
    when (dialog) {
        HomeDialog.OPENING -> BreakdownDialog(
            title = "Opening balance",
            rows = state.openingBreakdown,
            total = state.openingBalance ?: Money.zero(state.currencyCode),
            onDismiss = { dialog = HomeDialog.NONE },
            onOpenAccount = onOpenAccount
        )
        HomeDialog.INCOME -> BreakdownDialog(
            title = "Income by account",
            rows = state.incomeBreakdown,
            total = state.monthIncome,
            onDismiss = { dialog = HomeDialog.NONE },
            onOpenAccount = onOpenAccount,
            footerAction = "See income records" to { dialog = HomeDialog.NONE; onOpenRecords(TypeFilter.INCOME) }
        )
        HomeDialog.BALANCE -> BreakdownDialog(
            title = "Current accounts and savings",
            rows = state.accounts.filter { it.includeInNetWorth }.map { AccountAmount(it, it.currentBalance) },
            total = Money(state.currentSubtotal.minorUnits + state.savingsSubtotal.minorUnits, state.currencyCode),
            onDismiss = { dialog = HomeDialog.NONE },
            onOpenAccount = onOpenAccount,
            groupSavings = true
        )
        HomeDialog.NONE -> Unit
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
        if (userName.isNotBlank()) {
            item { Text("Hello, $userName", style = MaterialTheme.typography.headlineSmall) }
        }
        item { BalanceCard(state, onOpenAccount, onScopeChange) }
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
                    label = if (state.isCurrentMonth) "Opening (B/F)" else "Opening",
                    amount = state.openingBalance ?: Money.zero(state.currencyCode),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    caption = "Tap for accounts",
                    onClick = { dialog = HomeDialog.OPENING }
                )
                FigureTile(
                    label = "Income",
                    amount = state.monthIncome,
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f),
                    caption = "Tap for sources",
                    onClick = { dialog = HomeDialog.INCOME }
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
                FigureTile(
                    label = "Expenses",
                    amount = state.monthExpense,
                    color = ExpenseRed,
                    modifier = Modifier.weight(1f),
                    caption = "Tap for records",
                    onClick = { onOpenRecords(TypeFilter.EXPENSE) }
                )
                FigureTile(
                    label = if (state.isCurrentMonth) "Current balance" else "Closing balance",
                    amount = state.currentBalance,
                    color = if (state.currentBalance.isNegative) ExpenseRed else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    caption = "Tap for accounts",
                    onClick = { dialog = HomeDialog.BALANCE }
                )
            }
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

private enum class HomeDialog { NONE, OPENING, INCOME, BALANCE }

@Composable
private fun FigureTile(
    label: String,
    amount: Money,
    color: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxSize()
                    .background(color)
            )
            Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(MonetaSpacing.xs))
                MoneyText(amount, color = color)
                caption?.let {
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Modal list of accounts with amounts; optionally split into Current and Savings groups. */
@Composable
private fun BreakdownDialog(
    title: String,
    rows: List<AccountAmount>,
    total: Money,
    onDismiss: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    groupSavings: Boolean = false,
    footerAction: Pair<String, () -> Unit>? = null
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (rows.isEmpty()) {
                    Text("Nothing to show for this month.", style = MaterialTheme.typography.bodyMedium)
                }
                if (groupSavings) {
                    val savings = rows.filter { it.account.type == AccountType.SAVINGS }
                    val current = rows.filter { it.account.type != AccountType.SAVINGS }
                    BreakdownGroup("Current accounts", current, onOpenAccount, onDismiss)
                    BreakdownGroup("Savings accounts", savings, onOpenAccount, onDismiss)
                } else {
                    rows.forEach { BreakdownRow(it, onOpenAccount, onDismiss) }
                }
                Spacer(Modifier.height(MonetaSpacing.sm))
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = MonetaSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    MoneyText(total, color = Color.Unspecified)
                }
            }
        },
        confirmButton = {
            if (footerAction != null) {
                TextButton(onClick = footerAction.second) { Text(footerAction.first) }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (footerAction != null) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun BreakdownGroup(
    title: String,
    rows: List<AccountAmount>,
    onOpenAccount: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (rows.isEmpty()) return
    val sum = rows.sumOf { it.amount.minorUnits }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MonetaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(Money(sum, rows.first().amount.currencyCode).formatted(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    Divider()
    rows.forEach { BreakdownRow(it, onOpenAccount, onDismiss) }
}

@Composable
private fun BreakdownRow(row: AccountAmount, onOpenAccount: (Long) -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismiss(); onOpenAccount(row.account.id) }
            .padding(vertical = MonetaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(iconName = row.account.icon, colorHex = row.account.colorHex, size = 28.dp)
        Spacer(Modifier.width(MonetaSpacing.md))
        Text(row.account.name, modifier = Modifier.weight(1f), maxLines = 1)
        MoneyText(row.amount, color = if (row.amount.isNegative) ExpenseRed else Color.Unspecified)
    }
}

/**
 * Balance card: tap it to open the accounts that make up the selected scope. The eye hides the figure,
 * the three-dots menu chooses All / Current / Savings.
 */
@Composable
private fun BalanceCard(
    state: HomeUiState,
    onOpenAccount: (Long) -> Unit,
    onScopeChange: (com.moneta.app.data.settings.BalanceScope) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = state.balanceScope
    val savingsType = AccountType.SAVINGS
    val counted = state.accounts.filter { it.includeInNetWorth }
    val currentAccounts = counted.filter { it.type != savingsType }
    val savingsAccounts = counted.filter { it.type == savingsType }
    val mask = "\u2022\u2022\u2022\u2022\u2022\u2022"
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
                    Text(
                        when (scope) {
                            com.moneta.app.data.settings.BalanceScope.ALL -> "Total balance"
                            com.moneta.app.data.settings.BalanceScope.CURRENT -> "Current accounts balance"
                            com.moneta.app.data.settings.BalanceScope.SAVINGS -> "Savings balance"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (hidden) mask else state.totalBalance.formatted(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (expanded) "Tap to hide accounts" else "Tap to see accounts",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(onClick = { hidden = !hidden }) {
                    Icon(
                        if (hidden) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (hidden) "Show balance" else "Hide balance"
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Choose which balance to show")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        com.moneta.app.data.settings.BalanceScope.values().forEach { sc ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (sc) {
                                            com.moneta.app.data.settings.BalanceScope.ALL -> "All accounts"
                                            com.moneta.app.data.settings.BalanceScope.CURRENT -> "Current accounts only"
                                            com.moneta.app.data.settings.BalanceScope.SAVINGS -> "Savings only"
                                        },
                                        fontWeight = if (sc == scope) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = { menuOpen = false; onScopeChange(sc) }
                            )
                        }
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                Divider()
                val showCurrent = scope != com.moneta.app.data.settings.BalanceScope.SAVINGS
                val showSavings = scope != com.moneta.app.data.settings.BalanceScope.CURRENT
                if (showCurrent) {
                    BalanceGroupHeader(
                        "Current accounts",
                        if (hidden) mask else state.currentSubtotal.formatted(),
                        MaterialTheme.colorScheme.primary
                    )
                    currentAccounts.forEach { BalanceAccountRow(it, hidden, mask, onOpenAccount) }
                }
                if (showSavings) {
                    BalanceGroupHeader(
                        "Savings accounts",
                        if (hidden) mask else state.savingsSubtotal.formatted(),
                        IncomeGreen
                    )
                    savingsAccounts.forEach { BalanceAccountRow(it, hidden, mask, onOpenAccount) }
                }
            }
        }
    }
}

@Composable
private fun BalanceGroupHeader(title: String, amountText: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MonetaSpacing.md)
            .background(color.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = MonetaSpacing.sm, vertical = MonetaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(amountText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BalanceAccountRow(account: Account, hidden: Boolean, mask: String, onOpenAccount: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenAccount(account.id) }
            .padding(top = MonetaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(iconName = account.icon, colorHex = account.colorHex, size = 32.dp)
        Spacer(Modifier.width(MonetaSpacing.md))
        Text(account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
        if (hidden) {
            Text(mask)
        } else {
            MoneyText(account.currentBalance, color = if (account.currentBalance.isNegative) ExpenseRed else Color.Unspecified)
        }
    }
}
