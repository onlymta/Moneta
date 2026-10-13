@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.domain.insights.Insight
import com.moneta.app.domain.insights.InsightSeverity
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.WarningAmber

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Insights") }) }) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.insights.isEmpty() -> InsightsEmptyState(state.hasEnoughData, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(MonetaSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                items(state.insights, key = { it.id }) { insight ->
                    InsightCard(insight)
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

/** Section 19: if there isn't enough data, say so plainly rather than showing nothing or inventing something. */
@Composable
private fun InsightsEmptyState(hasEnoughData: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (hasEnoughData) "Nothing notable right now" else "Not enough data yet",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text(
            if (hasEnoughData) {
                "Your spending looks steady - no unusual patterns or budget risks to flag right now."
            } else {
                "Add a bit more transaction history and Moneta will start surfacing patterns and trends here."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val accentColor = when (insight.severity) {
        InsightSeverity.WARNING -> ExpenseRed
        InsightSeverity.NOTICE -> WarningAmber
        InsightSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Text(insight.title, style = MaterialTheme.typography.bodyLarge, color = accentColor)
            Spacer(Modifier.height(MonetaSpacing.xs))
            Text(insight.body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
