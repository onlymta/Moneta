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
import androidx.compose.foundation.layout.fillMaxHeight
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
    onOpenBudgets: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    plusViewModel: com.moneta.app.ui.plus.PlusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val plan by plusViewModel.state.collectAsState()

    when {
        state.isLoading -> LoadingState()
        !state.hasAnyAccounts -> HomeEmptyState(navController)
        else -> HomeContent(
            state = state,
            userName = userName,
            plan = plan,
            onPeriodChange = viewModel::onPeriodChange,
            onScopeChange = { sc -> viewModel.setBalanceScope(sc) },
            onToggleHide = { viewModel.toggleHideBalance() },
            onOpenBudgets = onOpenBudgets,
            onOpenAccount = { id -> navController.navigate(accountHistoryRoute(id)) },
            onTransactionClick = onTransactionClick,
            onSeeAll = {
                viewModel.prepareRecordsAll()
                navController.navigate(MonetaDestination.Transactions.route) {
                    launchSingleTop = true
                }
            },
            onOpenRecords = { type ->
                viewModel.prepareRecords(type)
                navController.navigate(MonetaDestination.Transactions.route) {
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
        Text("Welcome to Paisabook", style = MaterialTheme.typography.titleLarge)
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
    plan: com.moneta.app.data.plus.PlusState,
    onPeriodChange: (com.moneta.app.ui.common.PeriodSelection) -> Unit,
    onScopeChange: (com.moneta.app.data.settings.BalanceScope) -> Unit,
    onOpenAccount: (Long) -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onOpenRecords: (TypeFilter?) -> Unit,
    onOpenBudgets: () -> Unit,
    onToggleHide: () -> Unit
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
            groupSavings = true,
            note = if (state.isCurrentMonth && !state.yetToSpend.isZero) {
                "Less budgeted money still to spend " + state.yetToSpend.formatted() +
                    "  =  expected closing " + state.expectedClosing.formatted()
            } else null
        )
        HomeDialog.YET -> YetToSpendDialog(
            lines = state.yetToSpendLines,
            total = state.yetToSpend,
            onDismiss = { dialog = HomeDialog.NONE },
            onOpenBudgets = { dialog = HomeDialog.NONE; onOpenBudgets() }
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
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (userName.isNotBlank()) {
                    Text("Hello, $userName", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                PlanPill(plan)
            }
        }
        item { BalanceCard(state, onOpenAccount, onScopeChange, onToggleHide) }
        item {
            PeriodBar(
                selection = state.period,
                onChange = onPeriodChange,
                modes = listOf(PeriodMode.MONTH)
            )
        }
        val hasBudgets = !state.budgetTotal.isZero
        val orderedCards = state.cardOrder.mapNotNull { key ->
            com.moneta.app.data.settings.HomeCard.entries.firstOrNull { it.name == key }
        }.ifEmpty { com.moneta.app.data.settings.HomeCard.entries.toList() }
        val cards = orderedCards.filter { card ->
            card.name !in state.hiddenCards && (hasBudgets || (card != com.moneta.app.data.settings.HomeCard.BUDGETED &&
                card != com.moneta.app.data.settings.HomeCard.YET_TO_SPEND))
        }
        if (state.compactCards) item {
            CompactHomeCards(
                state = state,
                onOpening = { dialog = HomeDialog.OPENING },
                onIncome = { dialog = HomeDialog.INCOME },
                onExpenses = { onOpenRecords(TypeFilter.EXPENSE) },
                onYet = { dialog = HomeDialog.YET },
                onBalance = { dialog = HomeDialog.BALANCE }
            )
        } else items(cards.chunked(2), key = { row -> row.first().name }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
            ) {
                row.forEach { card ->
                    when (card) {
                        com.moneta.app.data.settings.HomeCard.OPENING -> FigureTile(
                            label = if (state.isCurrentMonth) "Opening (B/F)" else "Opening",
                            amount = state.openingBalance ?: Money.zero(state.currencyCode),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            caption = "Tap for accounts",
                            onClick = { dialog = HomeDialog.OPENING }
                        )
                        com.moneta.app.data.settings.HomeCard.INCOME -> FigureTile(
                            label = "Income",
                            amount = state.monthIncome,
                            color = IncomeGreen,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            caption = "Tap for sources",
                            onClick = { dialog = HomeDialog.INCOME }
                        )
                        com.moneta.app.data.settings.HomeCard.EXPENSES -> FigureTile(
                            label = "Expenses",
                            amount = state.monthExpense,
                            color = ExpenseRed,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            caption = "Tap for records",
                            onClick = { onOpenRecords(TypeFilter.EXPENSE) }
                        )
                        com.moneta.app.data.settings.HomeCard.EXPECTED_CLOSING -> FigureTile(
                            label = if (state.isCurrentMonth) "Expected closing" else "Closing balance",
                            amount = state.expectedClosing,
                            color = if (state.expectedClosing.isNegative) ExpenseRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            caption = if (state.isCurrentMonth) "Balance less budget still to spend" else "Tap for accounts",
                            onClick = { dialog = HomeDialog.BALANCE }
                        )
                        com.moneta.app.data.settings.HomeCard.BUDGETED -> FigureTile(
                            label = "Budgeted",
                            amount = state.budgetTotal,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            caption = "Tap for budgets",
                            onClick = onOpenBudgets
                        )
                        com.moneta.app.data.settings.HomeCard.YET_TO_SPEND -> FigureTile(
                            label = "Yet to spend",
                            amount = state.yetToSpend,
                            color = WarningAmber,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            caption = "Tap for breakdown",
                            onClick = { dialog = HomeDialog.YET }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
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

/** A small badge showing which plan is active; tapping it opens the Moneta Plus page. */
@Composable
private fun PlanPill(plan: com.moneta.app.data.plus.PlusState) {
    val openPlus = com.moneta.app.ui.plus.LocalOpenPlus.current
    val hasPlan = plan.purchased || plan.trialActive
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = if (hasPlan) color.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { openPlus() }
    ) {
        Text(
            when {
                plan.purchased -> "\u2605 Plus"
                plan.trialActive -> "\u2605 Plus trial \u00b7 ${plan.trialDaysLeft}d left"
                else -> "Free \u00b7 Get Plus"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (hasPlan) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/** Three wide cards: the month's total (with opening, income and expenses beneath), the budget in one card, and expected closing. */
@Composable
private fun CompactHomeCards(
    state: HomeUiState,
    onOpening: () -> Unit,
    onIncome: () -> Unit,
    onExpenses: () -> Unit,
    onYet: () -> Unit,
    onBalance: () -> Unit
) {
    val hidden = state.hiddenCards
    val showMonth = !("OPENING" in hidden && "INCOME" in hidden && "EXPENSES" in hidden)
    val showBudget = !state.budgetTotal.isZero && ("BUDGETED" !in hidden || "YET_TO_SPEND" !in hidden)
    val showClosing = "EXPECTED_CLOSING" !in hidden
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
        if (showMonth) {
            Card(colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    val opening = state.openingBalance ?: Money.zero(state.currencyCode)
                    val totalIn = opening + state.monthIncome
                    Text(
                        "Total Inflow this Month",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                    ) {
                        MoneyText(totalIn, color = primary)
                    }
                    Text(
                        opening.formatted() + " op. + " + state.monthIncome.formatted() + " income this month",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (showBudget) {
            val percent = state.budgetSpent.percentOf(state.budgetTotal) ?: 0.0
            val barColor = if (percent >= 100.0) ExpenseRed else if (percent >= 80.0) WarningAmber else IncomeGreen
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onYet)
            ) {
                Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Budget",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${percent.toInt()}% used", style = MaterialTheme.typography.labelLarge, color = barColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                        color = barColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                    Spacer(Modifier.height(MonetaSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                        CompactFigure("Budgeted", state.budgetTotal, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f), onYet)
                        CompactFigure("Spent", state.budgetSpent, ExpenseRed, Modifier.weight(1f), onYet)
                        CompactFigure("Yet to spend", state.yetToSpend, WarningAmber, Modifier.weight(1f), onYet)
                    }
                }
            }
        }
        if (showClosing) {
            val tint = if (state.expectedClosing.isNegative) ExpenseRed else primary
            Card(
                colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBalance)
            ) {
                Row(modifier = Modifier.padding(MonetaSpacing.cardPadding), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (state.isCurrentMonth) "Expected closing" else "Closing balance",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (state.isCurrentMonth) "Balance less budget still to spend" else "Where the month ended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                    ) {
                        MoneyText(state.expectedClosing, color = tint)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFigure(label: String, amount: Money, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
        ) {
            MoneyText(amount, color = color)
        }
    }
}

private enum class HomeDialog { NONE, OPENING, INCOME, BALANCE, YET }

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
        Row(modifier = Modifier.fillMaxHeight()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxSize()
                    .background(color)
            )
            Column(modifier = Modifier.padding(horizontal = MonetaSpacing.cardPadding, vertical = 18.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(MonetaSpacing.sm))
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                ) {
                    MoneyText(amount, color = color)
                }
                caption?.let {
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Compact list of budgeted categories with what is still to spend, each with a small share bar. */
@Composable
private fun YetToSpendDialog(
    lines: List<YetToSpendLine>,
    total: Money,
    onDismiss: () -> Unit,
    onOpenBudgets: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yet to spend") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                if (lines.isEmpty()) {
                    Text("Every budget is fully spent.", style = MaterialTheme.typography.bodyMedium)
                }
                val biggest = (lines.maxOfOrNull { it.amount.minorUnits } ?: 1L).coerceAtLeast(1L)
                lines.forEach { line ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBadge(
                                iconName = line.icon,
                                colorHex = com.moneta.app.ui.common.IconMapper.colorFor(line.icon),
                                size = 26.dp
                            )
                            Spacer(Modifier.width(MonetaSpacing.sm))
                            Text(
                                line.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            MoneyText(line.amount, color = WarningAmber)
                        }
                        Spacer(Modifier.height(3.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { line.amount.minorUnits.toFloat() / biggest.toFloat() },
                            color = WarningAmber,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    MoneyText(total, color = Color.Unspecified)
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenBudgets) { Text("Open budgets") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
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
    note: String? = null,
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
                if (note != null) {
                    Spacer(Modifier.height(MonetaSpacing.sm))
                    Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onScopeChange: (com.moneta.app.data.settings.BalanceScope) -> Unit,
    onToggleHide: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hidden = state.hideBalance
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
        Column(modifier = Modifier.padding(horizontal = MonetaSpacing.cardPadding, vertical = 20.dp)) {
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
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (expanded) "Tap to hide accounts" else "Tap to see accounts",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(onClick = onToggleHide) {
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
