@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.BudgetScope
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AddEditBudgetScreen(
    onDone: () -> Unit,
    viewModel: AddEditBudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    com.moneta.app.ui.common.FormScreenScaffold(title = "Add budget", onClose = onDone) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.scope == BudgetScope.OVERALL,
                onClick = { viewModel.onScopeChange(BudgetScope.OVERALL) },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("Overall") }
            SegmentedButton(
                selected = state.scope == BudgetScope.CATEGORY,
                onClick = { viewModel.onScopeChange(BudgetScope.CATEGORY) },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("Category") }
        }

        if (state.scope == BudgetScope.CATEGORY) {
            Text("Category", style = MaterialTheme.typography.labelMedium)
            if (state.expenseCategories.isEmpty()) {
                Text(
                    "Add an expense category first from Categories.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    items(state.expenseCategories, key = { it.id }) { category ->
                        FilterChip(
                            selected = category.id == state.selectedCategoryId,
                            onClick = { viewModel.onCategorySelected(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.amountInput,
            onValueChange = viewModel::onAmountChange,
            label = { Text("Monthly limit") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.amountError != null,
            supportingText = state.amountError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Alert when spending reaches ${state.alertThresholdPercent}%",
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = state.alertThresholdPercent.toFloat(),
            onValueChange = { viewModel.onThresholdChange(it.toInt()) },
            valueRange = 50f..100f,
            steps = 9
        )

        Spacer(Modifier.height(0.dp))

        Button(
            onClick = { viewModel.save(onDone) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isSaving) "Saving…" else "Save budget")
        }
    }
}
