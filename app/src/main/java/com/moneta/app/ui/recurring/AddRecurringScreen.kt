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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moneta.app.ui.goals.GoalDatePickerDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
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
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    com.moneta.app.ui.common.FormScreenScaffold(
        title = if (state.isEditing) "Edit recurring transaction" else "Add recurring transaction",
        onClose = onDone
    ) {

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

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Repeats", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            items(FREQUENCY_CHOICES) { (frequency, label) ->
                FilterChip(
                    selected = state.frequency == frequency,
                    onClick = { viewModel.onFrequencyChange(frequency) },
                    label = { Text(label) }
                )
            }
        }

        when (state.frequency) {
            RecurrenceFrequency.WEEKLY -> {
                Text("On", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    items(DayOfWeek.entries) { day ->
                        FilterChip(
                            selected = state.weekday == day,
                            onClick = { viewModel.onWeekdayChange(day) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                        )
                    }
                }
            }
            RecurrenceFrequency.MONTHLY -> {
                Text("On day of the month", style = MaterialTheme.typography.labelMedium)
                ChoiceButton(
                    current = if (state.monthDay == LAST_DAY_OF_MONTH) "Last day" else ordinal(state.monthDay),
                    options = (1..30).map { it to ordinal(it) } + (LAST_DAY_OF_MONTH to "Last day"),
                    onPick = viewModel::onMonthDayChange
                )
            }
            RecurrenceFrequency.YEARLY -> {
                Text("On", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    ChoiceButton(
                        current = java.time.Month.of(state.yearMonth).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        options = (1..12).map { it to java.time.Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                        onPick = { viewModel.onYearlyChange(it, state.yearDay) }
                    )
                    ChoiceButton(
                        current = state.yearDay.toString(),
                        options = (1..31).map { it to it.toString() },
                        onPick = { viewModel.onYearlyChange(state.yearMonth, it) }
                    )
                }
            }
            else -> Unit
        }

        if (state.frequency == RecurrenceFrequency.DAILY ||
            state.frequency == RecurrenceFrequency.WEEKLY ||
            state.frequency == RecurrenceFrequency.MONTHLY ||
            state.frequency == RecurrenceFrequency.YEARLY
        ) {
            OutlinedTextField(
                value = state.intervalCount.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::onIntervalChange) },
                label = { Text("Every how many ${intervalUnit(state.frequency)}?") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Starts from", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { pickingStart = true }, modifier = Modifier.fillMaxWidth()) {
            Text(state.startFrom.toString())
        }
        Text(
            state.preview,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ends on a date", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Off means it repeats until you pause or delete it.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.endDate != null,
                onCheckedChange = { on ->
                    if (on) viewModel.onEndDateChange(state.startFrom.plusYears(1)) else viewModel.onEndDateChange(null)
                }
            )
        }
        state.endDate?.let { end ->
            OutlinedButton(onClick = { pickingEnd = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Ends $end")
            }
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

    if (pickingStart) {
        GoalDatePickerDialog(
            initial = state.startFrom,
            onPick = { date ->
                if (date != null) viewModel.onStartFromChange(date)
                pickingStart = false
            },
            onDismiss = { pickingStart = false }
        )
    }
    if (pickingEnd) {
        GoalDatePickerDialog(
            initial = state.endDate,
            onPick = { date ->
                if (date != null) viewModel.onEndDateChange(date)
                pickingEnd = false
            },
            onDismiss = { pickingEnd = false }
        )
    }
}

/** A button that opens a scrollable drop-down list; used for day-of-month and month pickers. */
@Composable
private fun ChoiceButton(current: String, options: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { menuOpen = true }) { Text(current) }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onPick(value)
                        menuOpen = false
                    }
                )
            }
        }
    }
}

private val FREQUENCY_CHOICES = listOf(
    RecurrenceFrequency.DAILY to "Daily",
    RecurrenceFrequency.WEEKDAYS to "Working days",
    RecurrenceFrequency.WEEKENDS to "Weekends",
    RecurrenceFrequency.WEEKLY to "Weekly",
    RecurrenceFrequency.MONTHLY to "Monthly",
    RecurrenceFrequency.YEARLY to "Yearly"
)

private fun intervalUnit(frequency: RecurrenceFrequency): String = when (frequency) {
    RecurrenceFrequency.DAILY -> "days"
    RecurrenceFrequency.WEEKLY -> "weeks"
    RecurrenceFrequency.MONTHLY -> "months"
    RecurrenceFrequency.YEARLY -> "years"
    RecurrenceFrequency.WEEKDAYS, RecurrenceFrequency.WEEKENDS -> "times"
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}
