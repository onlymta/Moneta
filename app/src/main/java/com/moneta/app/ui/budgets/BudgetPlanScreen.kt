package com.moneta.app.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.Money
import com.moneta.app.ui.common.PeriodBar
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun BudgetPlanScreen(onBack: () -> Unit, viewModel: BudgetPlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var editingPlan by remember { mutableStateOf(false) }
    val currency = state.currencyCode

    fun money(minor: Long) = Money(minor, currency).formatted()

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Plan and adjust", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            PeriodBar(
                selection = state.period,
                onChange = viewModel::onPeriodChange,
                modes = listOf(PeriodMode.MONTH)
            )

            // --- The plan: income, savings, what is left to spend ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("My monthly plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editingPlan = true }) { Text("Edit") }
                    }
                    PlanLine(
                        if (state.plannedIncome.isZero) "Income (this month's actual)" else "Expected income",
                        state.planIncome.formatted()
                    )
                    PlanLine("Savings target", state.savingsTarget.formatted())
                    PlanLine("Available to spend", state.availableToSpend.formatted(), bold = true)
                    PlanLine("Budgeted in ${state.monthLabel}", state.totalBudget.formatted())
                    val gap = state.availableToSpend - state.totalBudget
                    Text(
                        if (state.planIncome.isZero) "Add your expected income (Edit) so budgets can be checked against it."
                        else if (gap.isNegative) "Budgets are ${gap.abs().formatted()} more than you can spend and still reach your savings target."
                        else "${gap.formatted()} of your spendable income is not given to any budget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!state.planIncome.isZero && gap.isNegative) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- How the month went ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    Text("How ${state.monthLabel} went", style = MaterialTheme.typography.titleMedium)
                    PlanLine("Income", state.actualIncome.formatted())
                    PlanLine("Spent (all expenses)", state.totalSpentAll.formatted())
                    val saved = state.actualSaved
                    PlanLine("Saved", saved.formatted(), color = if (saved.isNegative) ExpenseRed else IncomeGreen, bold = true)
                    if (!state.savingsTarget.isZero) {
                        val diff = saved - state.savingsTarget
                        Text(
                            if (diff.isNegative) "${diff.abs().formatted()} short of your savings target."
                            else "${diff.formatted()} ahead of your savings target.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diff.isNegative) ExpenseRed else IncomeGreen
                        )
                    }
                    val budgetDiff = state.totalSpentOnBudgeted - state.totalBudget
                    if (!state.totalBudget.isZero) {
                        Text(
                            if (budgetDiff.minorUnits > 0L) "Budgeted categories went ${budgetDiff.formatted()} over budget."
                            else "Budgeted categories finished ${budgetDiff.abs().formatted()} under budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (budgetDiff.minorUnits > 0L) ExpenseRed else IncomeGreen
                        )
                    }
                }
            }

            // --- Adjust next month ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    Text("Adjust ${state.nextMonthLabel}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Overspent a category? Its budget next month is cut by the same amount, so your average stays on plan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Also add back what I underspent", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(checked = state.raiseWhenUnder, onCheckedChange = viewModel::setRaiseWhenUnder)
                    }
                    if (state.rows.isEmpty()) {
                        Text(
                            "No budgets were set for ${state.monthLabel}, so there is nothing to compare yet.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    state.rows.forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.selected,
                                enabled = row.suggestedMinor != row.budgetMinor,
                                onCheckedChange = { viewModel.toggleRow(row.categoryId, it) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "Budget ${money(row.budgetMinor)}, spent ${money(row.spentMinor)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    money(row.suggestedMinor),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (row.changeMinor != 0L) {
                                    Text(
                                        (if (row.changeMinor > 0L) "+" else "-") + money(kotlin.math.abs(row.changeMinor)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (row.changeMinor < 0L) ExpenseRed else IncomeGreen
                                    )
                                }
                            }
                        }
                    }
                    if (state.rows.isNotEmpty()) {
                        PlanLine("Total budget for ${state.nextMonthLabel}", state.nextTotalBudget.formatted(), bold = true)
                        val room = state.availableToSpend - state.nextTotalBudget
                        if (!state.planIncome.isZero) {
                            Text(
                                if (room.isNegative) "Still ${room.abs().formatted()} above what you can spend and keep your savings target."
                                else "Fits your plan, with ${room.formatted()} to spare.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (room.isNegative) ExpenseRed else IncomeGreen
                            )
                        }
                        Button(onClick = viewModel::applyToNextMonth, modifier = Modifier.fillMaxWidth()) {
                            Text("Apply to ${state.nextMonthLabel}")
                        }
                    }
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (editingPlan) {
        var income by remember { mutableStateOf(if (state.plannedIncome.isZero) "" else state.plannedIncome.toEditableDecimalString()) }
        var savings by remember { mutableStateOf(if (state.savingsTarget.isZero) "" else state.savingsTarget.toEditableDecimalString()) }
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
                        label = { Text("Savings target per month") },
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
}

@Composable
private fun PlanLine(
    label: String,
    value: String,
    bold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}
