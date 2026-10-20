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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

    var searchOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = MonetaSpacing.screenHorizontal)) {
            PeriodBar(
                selection = state.period,
                onChange = viewModel::onPeriodChange,
                modes = PeriodMode.entries
            )
            // One compact row: search icon, type filters, account filter.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.onQueryChange("")
                    }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search records",
                            tint = if (searchOpen || state.query.isNotEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                item {
                    var groupMenu by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(
                            onClick = { groupMenu = true },
                            label = { Text(state.groupMode.label) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Group or sort records") }
                        )
                        DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                            GroupMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            mode.label,
                                            fontWeight = if (mode == state.groupMode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        groupMenu = false
                                        viewModel.onGroupModeChange(mode)
                                    }
                                )
                            }
                        }
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
            if (searchOpen || state.query.isNotEmpty()) {
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search records") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MonetaSpacing.xs)
                )
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
                    Column(modifier = Modifier.padding(MonetaSpacing.md)) {
                        Text(
                            state.period.label() + "  ·  " + state.count + " records",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                            SummaryTile("Income", state.income, IncomeGreen, Modifier.weight(1f))
                            SummaryTile("Expenses", state.expense, ExpenseRed, Modifier.weight(1f))
                            SummaryTile(
                                "Bal.",
                                state.net,
                                if (state.net.isNegative) ExpenseRed else MaterialTheme.colorScheme.primary,
                                Modifier.weight(1f)
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
                item(key = group.key) {
                    val groupDate = group.date
                    if (groupDate != null) {
                        DayHeader(date = groupDate, net = group.net)
                    } else if (group.title.isNotEmpty()) {
                        GroupHeader(group.title, group.totalLabel, group.net)
                    }
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

@Composable
private fun GroupHeader(title: String, totalLabel: String, net: com.moneta.app.core.money.Money) {
    val tint = if (net.isNegative) ExpenseRed else IncomeGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MonetaSpacing.md, bottom = MonetaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Surface(shape = RoundedCornerShape(50), color = tint.copy(alpha = 0.14f)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(totalLabel + " ", style = MaterialTheme.typography.labelSmall, color = tint)
                MoneyText(net, color = tint)
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, amount: com.moneta.app.core.money.Money, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            MoneyText(amount, color = color, modifier = Modifier)
        }
    }
}
