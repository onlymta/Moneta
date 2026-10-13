@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.RecurrenceFrequency
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AddRecurringScreen(
    onDone: () -> Unit,
    viewModel: AddRecurringViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    com.moneta.app.ui.common.FormScreenScaffold(title = "Add recurring transaction", onClose = onDone) {

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                TransactionType.EXPENSE to "Expense",
                TransactionType.INCOME to "Income",
                TransactionType.TRANSFER to "Transfer"
            ).forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = state.type == type,
                    onClick = { viewModel.onTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, 3)
                ) { Text(label) }
            }
        }

        OutlinedTextField(
            value = state.amountInput,
            onValueChange = viewModel::onAmountChange,
            label = { Text("Amount") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.amountError != null,
            supportingText = state.amountError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Account", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            items(state.accounts, key = { it.id }) { account ->
                FilterChip(
                    selected = state.selectedAccountId == account.id,
                    onClick = { viewModel.onAccountSelected(account.id) },
                    label = { Text(account.name) }
                )
            }
        }

        if (state.type == TransactionType.TRANSFER) {
            Text("Transfer to", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                items(state.accounts.filter { it.id != state.selectedAccountId }, key = { it.id }) { account ->
                    FilterChip(
                        selected = state.selectedToAccountId == account.id,
                        onClick = { viewModel.onToAccountSelected(account.id) },
                        label = { Text(account.name) }
                    )
                }
            }
        } else {
            Text("Category", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.onCategorySelected(category.id) },
                        label = { Text(category.name) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.payee,
            onValueChange = viewModel::onPayeeChange,
            label = { Text("Name (e.g. \"Netflix\", \"Salary\")") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Repeats", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            items(RecurrenceFrequency.entries) { frequency ->
                FilterChip(
                    selected = state.frequency == frequency,
                    onClick = { viewModel.onFrequencyChange(frequency) },
                    label = { Text(frequency.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ends after a while", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Off means it repeats indefinitely until you pause or delete it.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = state.hasEndDate, onCheckedChange = viewModel::onHasEndDateChange)
        }

        if (state.hasEndDate) {
            OutlinedTextField(
                value = state.endInMonths.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::onEndInMonthsChange) },
                label = { Text("Ends in how many months") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(0.dp))

        Button(
            onClick = { viewModel.save(onDone) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isSaving) "Saving…" else "Save")
        }
    }
}
