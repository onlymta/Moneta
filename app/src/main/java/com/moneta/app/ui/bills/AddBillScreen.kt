package com.moneta.app.ui.bills

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.theme.MonetaSpacing

private val DUE_IN_DAYS_OPTIONS = listOf(1, 3, 7, 14, 30)

@Composable
fun AddBillScreen(
    onDone: () -> Unit,
    viewModel: AddBillViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    com.moneta.app.ui.common.FormScreenScaffold(title = "Add bill", onClose = onDone) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Bill name") },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.amountInput,
            onValueChange = viewModel::onAmountChange,
            label = { Text("Amount") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.amountError != null,
            supportingText = state.amountError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Due in", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            items(DUE_IN_DAYS_OPTIONS) { days ->
                FilterChip(
                    selected = state.dueInDays == days,
                    onClick = { viewModel.onDueInDaysChange(days) },
                    label = { Text(if (days == 1) "1 day" else "$days days") }
                )
            }
        }

        if (state.accounts.isNotEmpty()) {
            Text("Pay from (optional)", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                items(state.accounts, key = { it.id }) { account ->
                    FilterChip(
                        selected = state.selectedAccountId == account.id,
                        onClick = {
                            viewModel.onAccountSelected(if (state.selectedAccountId == account.id) null else account.id)
                        },
                        label = { Text(account.name) }
                    )
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            Text("Category (optional)", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = {
                            viewModel.onCategorySelected(if (state.selectedCategoryId == category.id) null else category.id)
                        },
                        label = { Text(category.name) }
                    )
                }
            }
        }

        Text(
            "Set both an account and a category so marking this bill paid can record the matching expense automatically.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(0.dp))

        Button(
            onClick = { viewModel.save(onDone) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isSaving) "Saving…" else "Save bill")
        }
    }
}
