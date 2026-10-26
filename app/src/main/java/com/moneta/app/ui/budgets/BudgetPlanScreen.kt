package com.moneta.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.Money
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MonetaSpacing

/** A simple monthly plan: income, savings, how the rest is handed to categories, and what to carry over. */
@Composable
fun BudgetPlanScreen(onBack: () -> Unit, viewModel: BudgetPlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editingPlan by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<PlanAllocRow?>(null) }
    val currency = state.currencyCode

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Budget plan", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            // 1. Income, savings, spending
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("1. My monthly plan", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Applies to every month from now on.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { editingPlan = true }) { Text("Edit") }
                    }
                    PlanLine("Expected income", state.income.formatted())
                    PlanLine("Savings", state.savings.formatted())
                    PlanLine("Spending (income minus savings)", state.spending.formatted(), bold = true)
                    if (state.income.isZero) {
                        Text(
                            "Tap Edit and enter what you expect to earn and want to save each month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 2. Give the spending to categories
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    Text("2. Give your spending to categories", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap a category to set its monthly budget. It is saved for ${state.monthLabel} and the 11 months after.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PlanLine("Given to categories", state.assigned.formatted())
                    val left = state.left
                    PlanLine(
                        if (left.isNegative) "Over your spending by" else "Still to give",
                        left.abs().formatted(),
                        bold = true,
                        color = if (state.income.isZero) Color.Unspecified else if (left.isNegative) ExpenseRed else IncomeGreen
                    )
                    state.rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingRow = row }
                                .padding(vertical = MonetaSpacing.sm),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (row.amountMinor > 0L) Money(row.amountMinor, currency).formatted() else "Set",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (row.amountMinor > 0L) Color.Unspecified else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 3. What to do with over and under spending
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    Text("3. When a month ends", style = MaterialTheme.typography.titleMedium)
                    ToggleLine(
                        "Spent more than a budget?",
                        "Take the extra off that category's budget next month.",
                        state.reduceOverspend,
                        viewModel::setReduceOverspend
                    )
                    ToggleLine(
                        "Spent less than a budget?",
                        "Add what was left to that category's budget next month.",
                        state.addUnderspend,
                        viewModel::setAddUnderspend
                    )
                    Text(
                        "With a switch off, the budget just stays as you set it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (editingPlan) {
        var income by remember { mutableStateOf(if (state.income.isZero) "" else state.income.toEditableDecimalString()) }
        var savings by remember { mutableStateOf(if (state.savings.isZero) "" else state.savings.toEditableDecimalString()) }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingPlan = false },
            title = { Text("My monthly plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    OutlinedTextField(
                        value = income,
                        onValueChange = { income = it; error = false },
                        label = { Text("Expected income per month") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = savings,
                        onValueChange = { savings = it; error = false },
                        label = { Text("Savings per month") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error) Text("Enter numbers only.", color = ExpenseRed, style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.savePlan(income, savings)) editingPlan = false else error = true
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingPlan = false }) { Text("Cancel") } }
        )
    }

    editingRow?.let { row ->
        var amount by remember(row.categoryId) {
            mutableStateOf(if (row.amountMinor > 0L) Money(row.amountMinor, currency).toEditableDecimalString() else "")
        }
        var error by remember(row.categoryId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingRow = null },
            title = { Text(row.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it; error = false },
                        label = { Text("Monthly budget") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Leave empty for no budget.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.setAmount(row.categoryId, amount)) editingRow = null else error = true
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingRow = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ToggleLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PlanLine(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}
