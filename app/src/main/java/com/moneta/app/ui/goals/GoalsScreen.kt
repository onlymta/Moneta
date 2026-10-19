@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.goals

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.moneta.app.ui.common.FabStyle
import com.moneta.app.ui.common.MovableFab
import com.moneta.app.ui.common.SubPageHeader
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.repository.Goal
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MoneyText
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.format.DateTimeFormatter

@Composable
fun GoalsScreen(
    onBack: (() -> Unit)?,
    onAddGoal: () -> Unit,
    onContribute: (Long) -> Unit,
    viewModel: GoalsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = { SubPageHeader("Goals", onBack) },
        floatingActionButton = {
            MovableFab(onClick = onAddGoal, contentDescription = "Add goal", style = FabStyle.OTHER)
        }
    ) { padding ->
        if (!state.isLoading && state.goals.isEmpty()) {
            GoalsEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = MonetaSpacing.screenHorizontal, end = MonetaSpacing.screenHorizontal, top = MonetaSpacing.screenHorizontal, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                items(state.goals, key = { it.id }) { goal ->
                    GoalRow(goal, onContribute = { onContribute(goal.id) }, onArchive = { viewModel.archiveGoal(goal.id) })
                }
            }
        }
    }
}

@Composable
private fun GoalsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MonetaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No goals yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        Text(
            "Set a savings target - an emergency fund, a trip, a big purchase - and track progress toward it.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun GoalRow(goal: Goal, onContribute: () -> Unit, onArchive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MonetaSpacing.cardPadding)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(goal.name, style = MaterialTheme.typography.bodyLarge)
                goal.targetDate?.let {
                    Text(
                        it.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(MonetaSpacing.sm))
            LinearProgressIndicator(
                progress = { ((goal.percentComplete ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
                color = IncomeGreen,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(goal.currentAmount)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(goal.targetAmount)
                }
            }
            Spacer(Modifier.height(MonetaSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onContribute) { Text(if (goal.isComplete) "Reached!" else "Add money") }
            }
        }
    }
}
