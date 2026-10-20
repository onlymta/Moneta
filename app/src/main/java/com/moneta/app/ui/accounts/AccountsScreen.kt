@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.accounts

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moneta.app.ui.common.FabStyle
import com.moneta.app.ui.common.MovableFab
import com.moneta.app.ui.common.SubPageHeader
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.repository.Account
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    onBack: (() -> Unit)?,
    onAddAccount: () -> Unit,
    onEditAccount: (Long) -> Unit,
    onOpenAccount: (Long) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is AccountsEvent.Archived -> "${event.accountName} archived"
                is AccountsEvent.Deleted -> "${event.accountName} deleted"
            }
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = { if (onBack != null) SubPageHeader("Accounts", onBack) },
        floatingActionButton = {
            MovableFab(onClick = onAddAccount, contentDescription = "Add account", style = FabStyle.OTHER)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        if (!state.isLoading && state.accounts.isEmpty()) {
            AccountsEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = MonetaSpacing.screenHorizontal, end = MonetaSpacing.screenHorizontal, top = MonetaSpacing.sm, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                val active = state.accounts.filter { !it.isArchived }
                val archived = state.accounts.filter { it.isArchived }
                val savingsType = com.moneta.app.data.local.entity.AccountType.SAVINGS
                val currentList = active.filter { it.type != savingsType }
                val savingsList = active.filter { it.type == savingsType }
                if (active.isNotEmpty()) {
                    item(key = "dashboard") {
                        val cur = active.filter { it.includeInNetWorth }
                        val savMinor = cur.filter { it.type == savingsType }.sumOf { it.currentBalance.minorUnits }
                        val allMinor = cur.sumOf { it.currentBalance.minorUnits }
                        val code = active.first().currentBalance.currencyCode
                        AccountsDashboard(
                            all = com.moneta.app.core.money.Money(allMinor, code),
                            current = com.moneta.app.core.money.Money(allMinor - savMinor, code),
                            savings = com.moneta.app.core.money.Money(savMinor, code)
                        )
                    }
                }
                if (currentList.isNotEmpty()) {
                    item(key = "current-header") {
                        GroupHeader("Current accounts", currentList.sumOf { it.currentBalance.minorUnits }, currentList.first().currentBalance.currencyCode)
                    }
                    items(currentList, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            onClick = { onOpenAccount(account.id) },
                            onEdit = { onEditAccount(account.id) },
                            onArchiveOrDelete = { viewModel.archiveOrDeleteAccount(account) }
                        )
                    }
                }
                if (savingsList.isNotEmpty()) {
                    item(key = "savings-header") {
                        GroupHeader("Savings accounts", savingsList.sumOf { it.currentBalance.minorUnits }, savingsList.first().currentBalance.currencyCode)
                    }
                    items(savingsList, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            onClick = { onOpenAccount(account.id) },
                            onEdit = { onEditAccount(account.id) },
                            onArchiveOrDelete = { viewModel.archiveOrDeleteAccount(account) }
                        )
                    }
                }
                if (archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        Text(
                            "Archived",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = MonetaSpacing.md)
                        )
                    }
                    items(archived, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            onClick = { onOpenAccount(account.id) },
                            onEdit = { onEditAccount(account.id) },
                            onArchiveOrDelete = { viewModel.archiveOrDeleteAccount(account) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountsDashboard(
    all: com.moneta.app.core.money.Money,
    current: com.moneta.app.core.money.Money,
    savings: com.moneta.app.core.money.Money
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("All accounts", style = MaterialTheme.typography.labelLarge)
            Text(
                all.formatted(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Current", style = MaterialTheme.typography.labelMedium)
                    Text(current.formatted(), style = MaterialTheme.typography.titleMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Savings", style = MaterialTheme.typography.labelMedium)
                    Text(savings.formatted(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, minor: Long, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = MonetaSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(com.moneta.app.core.money.Money(minor, currency))
    }
}

@Composable
private fun AccountsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No accounts yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text(
            "Add a cash wallet, bank account, or card to start tracking your money.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun AccountRow(account: Account, onClick: () -> Unit, onEdit: () -> Unit, onArchiveOrDelete: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }

    val archived = account.isArchived
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (archived) 0.55f else 1f),
        colors = if (archived) {
            androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.moneta.app.ui.common.IconBadge(iconName = account.icon, colorHex = account.colorHex)
                Spacer(Modifier.width(MonetaSpacing.md))
                Column {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        (if (archived) "Archived · " else "") + account.type.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            MoneyText(account.currentBalance)
            Spacer(Modifier.height(0.dp))
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit (name, opening balance)") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    if (!archived) {
                        DropdownMenuItem(
                            text = { Text("Archive or delete") },
                            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onArchiveOrDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
