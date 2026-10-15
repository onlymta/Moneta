package com.moneta.app.ui.transactions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.common.DayHeader
import com.moneta.app.ui.common.PeriodBar
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.TransactionRowCard
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = MonetaSpacing.screenHorizontal)) {
            PeriodBar(
                selection = state.period,
                onChange = viewModel::onPeriodChange,
                modes = PeriodMode.entries
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search these records") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.categoryTitle?.let { title ->
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { viewModel.clearCategoryFilter() },
                            label = { Text(title) },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Clear category filter") }
                        )
                    }
                }
                items(TypeFilter.entries.toList()) { filter ->
                    FilterChip(
                        selected = state.typeFilter == filter,
                        onClick = { viewModel.onTypeFilterChange(filter) },
                        label = { Text(filter.label) }
                    )
                }
                item {
                    var menuOpen by remember { mutableStateOf(false) }
                    val selected = state.accounts.firstOrNull { it.id == state.accountFilterId }
                    Box {
                        AssistChip(
                            onClick = { menuOpen = true },
                            label = { Text(selected?.name ?: "All accounts") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("All accounts") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onAccountFilterChange(null)
                                }
                            )
                            state.accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.onAccountFilterChange(account.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = MonetaSpacing.screenHorizontal,
                end = MonetaSpacing.screenHorizontal,
                top = MonetaSpacing.sm,
                bottom = MonetaSpacing.xxxl * 2
            ),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
                        Text(
                            "${state.count} transaction(s) · ${state.period.label()}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Income", style = MaterialTheme.typography.labelMedium)
                                MoneyText(state.income, color = IncomeGreen)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Expenses", style = MaterialTheme.typography.labelMedium)
                                MoneyText(state.expense, color = ExpenseRed)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Net", style = MaterialTheme.typography.labelMedium)
                                MoneyText(state.net, color = if (state.net.isNegative) ExpenseRed else IncomeGreen)
                            }
                        }
                        if (state.transferCount > 0) {
                            Text(
                                "Plus ${state.transferCount} transfer(s) between your accounts (not counted above).",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = MonetaSpacing.xs)
                            )
                        }
                    }
                }
            }

            if (!state.isLoading && state.groups.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MonetaSpacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nothing here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(MonetaSpacing.xs))
                        Text(
                            "No transactions match this period and filter. Try another period, or tap + to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            state.groups.forEach { group ->
                item(key = "day-${group.date}") {
                    DayHeader(date = group.date, net = group.net)
                }
                items(group.items, key = { it.transaction.id }) { entry ->
                    TransactionRowCard(
                        item = entry,
                        showDate = false,
                        onClick = { onTransactionClick(entry.transaction.id) }
                    )
                }
            }
        }
    }
}
