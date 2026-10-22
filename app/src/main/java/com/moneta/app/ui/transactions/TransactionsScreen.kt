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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListScope
import com.moneta.app.ui.common.IconBadge
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.moneta.app.ui.common.PeriodModeButton
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
    onOpenReports: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var searchOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }

    if (filterOpen) {
        FilterDialog(state, viewModel) { filterOpen = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = MonetaSpacing.screenHorizontal)) {
            PeriodBar(
                selection = state.period,
                onChange = viewModel::onPeriodChange,
                modes = PeriodMode.entries,
                calendarPick = true
            )
            // One row of equally sized controls: filter, sort, view as, period type, search.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalsView = state.groupMode == GroupMode.CATEGORY_TOTALS
                val idle = MaterialTheme.colorScheme.onSurfaceVariant
                val active = MaterialTheme.colorScheme.primary
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { filterOpen = true }) {
                        androidx.compose.material3.BadgedBox(
                            badge = {
                                if (state.activeFilterCount > 0) {
                                    androidx.compose.material3.Badge { Text(state.activeFilterCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Filter records",
                                tint = if (state.activeFilterCount > 0) active else idle
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(
                            Icons.Filled.SwapVert,
                            contentDescription = "Sort records",
                            tint = if (state.sortMode != SortMode.DATE_DESC) active else idle
                        )
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        Text(
                            "Sort by",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (mode == state.sortMode) "\u2713  " else "     ") + mode.label,
                                        fontWeight = if (mode == state.sortMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = { sortOpen = false; viewModel.onSortModeChange(mode) }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = {
                        viewModel.onGroupModeChange(if (totalsView) GroupMode.DAY else GroupMode.CATEGORY_TOTALS)
                    }) {
                        Icon(
                            if (totalsView) Icons.Filled.PieChart else Icons.Filled.ViewList,
                            contentDescription = if (totalsView) "Showing category totals. Tap for individual records" else "Showing individual records. Tap for category totals",
                            tint = if (totalsView) active else idle
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PeriodModeButton(selection = state.period, onChange = viewModel::onPeriodChange)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.onQueryChange("")
                    }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search records",
                            tint = if (searchOpen || state.query.isNotEmpty()) active else idle
                        )
                    }
                }
            }
            if (state.activeFilterCount > 0) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.typeFilter != TypeFilter.ALL) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.onTypeFilterChange(TypeFilter.ALL) },
                                label = { Text(state.typeFilter.label) },
                                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Clear type filter") }
                            )
                        }
                    }
                    state.accounts.firstOrNull { it.id == state.accountFilterId }?.let { account ->
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.onAccountFilterChange(null) },
                                label = { Text(account.name) },
                                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Clear account filter") }
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

            if (!state.isLoading && state.groups.isEmpty() && state.groupMode != GroupMode.CATEGORY_TOTALS) {
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

            if (state.groupMode == GroupMode.CATEGORY_TOTALS) {
                if (!state.isLoading && state.categoryTotals.isEmpty()) {
                    item {
                        Text(
                            "No categorised records match this period and filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(MonetaSpacing.md)
                        )
                    }
                }
                items(state.categoryTotals, key = { "cat-${it.categoryId}" }) { row ->
                    CategoryTotalCard(row) {
                        viewModel.prepareReports(row.categoryId, row.isIncome)
                        onOpenReports()
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

@Composable
private fun CategoryTotalCard(row: CategoryTotalRow, onClick: () -> Unit) {
    val tint = if (row.isIncome) IncomeGreen else ExpenseRed
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(iconName = row.icon, colorHex = row.colorHex)
            Spacer(Modifier.width(MonetaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    row.count.toString() + (if (row.count == 1) " record" else " records"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            ) {
                MoneyText(row.total, color = tint)
            }
        }
    }
}

/** "Parent > Child" style names so categories at any depth can be told apart in a list. */
private fun categoryPaths(categories: List<com.moneta.app.data.repository.Category>): List<Pair<com.moneta.app.data.repository.Category, String>> {
    val byId = categories.associateBy { it.id }
    fun path(c: com.moneta.app.data.repository.Category): String {
        val parts = ArrayList<String>()
        var current: com.moneta.app.data.repository.Category? = c
        var guard = 0
        while (current != null && guard < 20) {
            parts.add(0, current.name)
            current = current.parentCategoryId?.let { byId[it] }
            guard++
        }
        return parts.joinToString(" > ")
    }
    return categories.map { it to path(it) }.sortedBy { it.second.lowercase() }
}

private fun descendantIds(
    id: Long,
    categories: List<com.moneta.app.data.repository.Category>
): Set<Long> {
    val result = HashSet<Long>()
    result.add(id)
    var added = true
    while (added) {
        added = false
        categories.forEach { c ->
            val parent = c.parentCategoryId
            if (parent != null && parent in result && result.add(c.id)) added = true
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(state: TransactionsUiState, viewModel: TransactionsViewModel, onDismiss: () -> Unit) {
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    val wantedType = when (state.typeFilter) {
        TypeFilter.EXPENSE -> com.moneta.app.data.local.entity.CategoryType.EXPENSE
        TypeFilter.INCOME -> com.moneta.app.data.local.entity.CategoryType.INCOME
        else -> null
    }
    val choices = categoryPaths(state.categories.filter { !it.isArchived && (wantedType == null || it.type == wantedType) })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                Text("Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    items(TypeFilter.entries.toList()) { filter ->
                        FilterChip(
                            selected = state.typeFilter == filter,
                            onClick = { viewModel.onTypeFilterChange(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
                Text("Account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Box {
                    AssistChip(
                        onClick = { accountMenu = true },
                        label = { Text(state.accounts.firstOrNull { it.id == state.accountFilterId }?.name ?: "All accounts") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All accounts") },
                            onClick = { accountMenu = false; viewModel.onAccountFilterChange(null) }
                        )
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { accountMenu = false; viewModel.onAccountFilterChange(account.id) }
                            )
                        }
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Box {
                    AssistChip(
                        onClick = { categoryMenu = true },
                        label = { Text(state.categoryTitle ?: "All categories") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All categories") },
                            onClick = { categoryMenu = false; viewModel.onCategoryFilterChange(null, null) }
                        )
                        choices.forEach { (category, path) ->
                            DropdownMenuItem(
                                text = { Text(path) },
                                onClick = {
                                    categoryMenu = false
                                    viewModel.onCategoryFilterChange(descendantIds(category.id, state.categories), category.name)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { viewModel.clearAllFilters() }) { Text("Clear all") } }
    )
}
