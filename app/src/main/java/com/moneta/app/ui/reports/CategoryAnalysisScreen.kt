package com.moneta.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.common.TransactionRowCard
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.WarningAmber

/** Full-page analysis of one spending category for one month: budget, history, outlook and every transaction. */
@Composable
fun CategoryAnalysisScreen(
    onBack: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    viewModel: CategoryAnalysisViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = state.name, onBack = onBack)
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
            item { SpentCard(state) }
            item { StatsCard(state) }
            item { OutlookCard(state) }
            item { HistoryCard(state) }
            item {
                Text(
                    "Transactions in " + state.monthLabel + " (" + state.txCount + ")",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (state.items.isEmpty()) {
                item {
                    Text(
                        "No spending in this category this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.items, key = { it.transaction.id }) { item ->
                    TransactionRowCard(
                        item = item,
                        showDate = true,
                        onClick = { onTransactionClick(item.transaction.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpentCard(state: CategoryAnalysisUiState) {
    val pct = state.percentUsed
    val color = when {
        pct == null -> MaterialTheme.colorScheme.primary
        pct >= 100.0 -> ExpenseRed
        pct >= 85.0 -> WarningAmber
        else -> IncomeGreen
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Spent in " + state.monthLabel, style = MaterialTheme.typography.labelLarge)
            Text(state.spent.formatted(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val budget = state.budget
            if (budget != null) {
                Spacer(Modifier.height(MonetaSpacing.sm))
                LinearProgressIndicator(
                    progress = { ((pct ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MonetaSpacing.xs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Budget " + budget.formatted(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        (pct ?: 0.0).toInt().toString() + "% used",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                val left = budget.minorUnits - state.spent.minorUnits
                Text(
                    if (left >= 0L) "Remaining " + com.moneta.app.core.money.Money(left, budget.currencyCode).formatted()
                    else "Over by " + com.moneta.app.core.money.Money(-left, budget.currencyCode).formatted(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (left >= 0L) Color.Unspecified else ExpenseRed
                )
            } else {
                Text(
                    "No budget set for this category in this month.",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatsCard(state: CategoryAnalysisUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Transactions", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            StatLine("Number of transactions", state.txCount.toString())
            StatLine("Average transaction", state.avgTransaction.formatted())
            StatLine("Largest transaction", state.largestTransaction.formatted())
        }
    }
}

@Composable
private fun OutlookCard(state: CategoryAnalysisUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Expected budget", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(MonetaSpacing.xs))
            val expected = state.expected
            if (expected == null) {
                Text(state.expectedNote, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    expected.formatted(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    state.expectedNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val budget = state.budget
                if (budget != null) {
                    val diff = expected.minorUnits - budget.minorUnits
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    Text(
                        if (diff > 0L) "Your current budget looks " + com.moneta.app.core.money.Money(diff, budget.currencyCode).formatted() + " too low."
                        else if (diff < 0L) "Your current budget has about " + com.moneta.app.core.money.Money(-diff, budget.currencyCode).formatted() + " of room."
                        else "Your budget matches the expected amount.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (diff > 0L) ExpenseRed else IncomeGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(state: CategoryAnalysisUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text("Previous months: budget vs spending", style = MaterialTheme.typography.titleMedium)
            state.history.forEach { row ->
                Spacer(Modifier.height(MonetaSpacing.sm))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        row.spent.formatted() + (row.budget?.let { " of " + it.formatted() } ?: " (no budget)"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                val pct = row.percent
                val denom = maxOf(row.budget?.minorUnits ?: 0L, row.spent.minorUnits)
                LinearProgressIndicator(
                    progress = { if (denom <= 0L) 0f else (row.spent.minorUnits.toFloat() / denom.toFloat()).coerceIn(0f, 1f) },
                    color = if (pct != null && pct >= 100.0) ExpenseRed else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pct != null) {
                    Text(pct.toInt().toString() + "% of budget", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
