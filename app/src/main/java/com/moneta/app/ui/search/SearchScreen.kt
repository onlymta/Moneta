package com.moneta.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.common.TransactionRowCard
import com.moneta.app.ui.theme.MonetaSpacing

/**
 * Search the whole app. Tapping a result opens it: [onOpen] receives a route
 * (a screen, an account or category form, ...) and [onOpenTransaction] a
 * transaction id. Records has its own, narrower search box for transactions only.
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val results by viewModel.results.collectAsState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonetaSpacing.sm, vertical = MonetaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back")
            }
            OutlinedTextField(
                value = results.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search accounts, categories, records, bills...") },
                singleLine = true,
                trailingIcon = {
                    if (results.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
        }

        if (results.query.isBlank()) {
            Hint("Type to search the whole app: screens, accounts, categories, budgets, bills, goals, scheduled items and every transaction.")
        } else if (results.isEmpty) {
            Hint("Nothing matches \"${results.query.trim()}\".")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MonetaSpacing.screenHorizontal,
                    end = MonetaSpacing.screenHorizontal,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                hitSection("Screens", results.screens, onOpen)
                hitSection("Accounts", results.accounts, onOpen)
                hitSection("Categories", results.categories, onOpen)
                hitSection("Budgets", results.budgets, onOpen)
                hitSection("Bills", results.bills, onOpen)
                hitSection("Goals", results.goals, onOpen)
                hitSection("Scheduled", results.scheduled, onOpen)
                if (results.transactions.isNotEmpty()) {
                    item(key = "h-tx") {
                        val extra = results.totalTransactionMatches - results.transactions.size
                        SectionLabel(
                            if (extra > 0) "Transactions (showing ${results.transactions.size} of ${results.totalTransactionMatches})"
                            else "Transactions"
                        )
                    }
                    items(results.transactions.size, key = { "tx-" + results.transactions[it].transaction.id }) { index ->
                        val item = results.transactions[index]
                        TransactionRowCard(
                            item = item,
                            showDate = true,
                            onClick = { onOpenTransaction(item.transaction.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.hitSection(
    title: String,
    hits: List<SearchHit>,
    onOpen: (String) -> Unit
) {
    if (hits.isEmpty()) return
    item(key = "h-$title") { SectionLabel(title) }
    items(hits.size, key = { hits[it].key }) { index ->
        val hit = hits[index]
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(hit.route) }
        ) {
            Column(modifier = Modifier.padding(MonetaSpacing.md)) {
                Text(hit.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    hit.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = MonetaSpacing.md)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(MonetaSpacing.xl)
    )
}
