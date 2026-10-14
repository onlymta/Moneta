@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.moneta.app.ui.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.TransactionType
import com.moneta.app.data.repository.Account
import com.moneta.app.data.repository.Category
import com.moneta.app.data.settings.PickerStyle
import com.moneta.app.ui.common.IconMapper
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MonetaSpacing
import com.moneta.app.ui.theme.TransferGray
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fast, low-effort entry: type the amount (keyboard opens by itself), tap a
 * category, tap Save. Date defaults to today and time to now; backdating or
 * scheduling is one extra tap on the date row. Save stays pinned at the bottom.
 */
@Composable
fun QuickAddScreen(
    onDone: () -> Unit,
    transactionId: Long? = null,
    viewModel: QuickAddViewModel = hiltViewModel()
) {
    LaunchedEffect(transactionId) {
        if (transactionId != null) viewModel.loadForEdit(transactionId)
    }
    val state by viewModel.uiState.collectAsState()
    val isEditing = viewModel.isEditing

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(state.addAnotherTick) {
        if (transactionId == null) {
            try {
                amountFocus.requestFocus()
            } catch (e: Exception) {
                // Focus can fail if the field is not on screen yet; the user can just tap it.
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val accent: Color = when (state.type) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> TransferGray
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit transaction" else "Add transaction") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
            ) {
                TypeSelector(state.type, onSelect = viewModel::onTypeChange)

                OutlinedTextField(
                    value = state.amountInput,
                    onValueChange = viewModel::onAmountChange,
                    placeholder = {
                        Text("0", style = MaterialTheme.typography.displaySmall)
                    },
                    prefix = { Text("PKR ", style = MaterialTheme.typography.titleMedium) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.displaySmall.copy(color = accent),
                    isError = state.amountError != null,
                    supportingText = state.amountError?.let { message -> { Text(message) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocus)
                )

                state.savedMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = IncomeGreen)
                }

                val isTransfer = state.type == TransactionType.TRANSFER
                val dropdown = state.pickerStyle == PickerStyle.DROPDOWN
                val selectedSub = state.subcategoryOptions.firstOrNull { it.id == state.selectedCategoryId }
                val topSelectedId = selectedSub?.parentCategoryId ?: state.selectedCategoryId

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(if (isTransfer) "Accounts" else "Account and category")
                    TextButton(onClick = {
                        viewModel.setPickerStyle(if (dropdown) PickerStyle.CHIPS else PickerStyle.DROPDOWN)
                    }) {
                        Text(if (dropdown) "Show as chips" else "Show as dropdown")
                    }
                }

                if (isTransfer) {
                    AccountPicker("From account", state.accounts, state.selectedAccountId, dropdown, viewModel::onAccountSelected)
                    AccountPicker(
                        "To account",
                        state.accounts.filter { it.id != state.selectedAccountId },
                        state.selectedToAccountId,
                        dropdown,
                        viewModel::onToAccountSelected
                    )
                } else {
                    AccountPicker("Account", state.accounts, state.selectedAccountId, dropdown, viewModel::onAccountSelected)
                    CategoryPicker("Category", state.topLevelCategories, topSelectedId, dropdown, viewModel::onCategorySelected)
                    if (state.subcategoryOptions.isNotEmpty()) {
                        CategoryPicker(
                            "Subcategory (optional)",
                            state.subcategoryOptions,
                            selectedSub?.id,
                            dropdown,
                            viewModel::onCategorySelected
                        )
                    }
                }

                // --- When: one tappable date and one tappable time, both showing "now" until changed ---
                Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    ClickableField(
                        label = "Date",
                        value = state.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                        icon = Icons.Filled.Event,
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1.5f)
                    )
                    ClickableField(
                        label = "Time",
                        value = formatTime(state.timeMinutes),
                        icon = Icons.Filled.Schedule,
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.isFutureDate && !isEditing) {
                    Text(
                        "Scheduled for " + state.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")) +
                            ". It will be added to your records, and your balance will change, on that date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::onNotesChange,
                    placeholder = { Text("Notes or description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    state.formError?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                        if (!isEditing) {
                            OutlinedButton(
                                onClick = { viewModel.save(onDone, addAnother = true) },
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text("Save & add another")
                            }
                        }
                        Button(
                            onClick = { viewModel.save(onDone) },
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text(
                                when {
                                    state.isSaving -> "Saving…"
                                    isEditing -> "Save changes"
                                    state.isFutureDate -> "Schedule"
                                    else -> "Save"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = state.timeMinutes / 60,
            initialMinute = state.timeMinutes % 60,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onTimeChange(timeState.hour * 60 + timeState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}

private fun formatTime(minutesSinceMidnight: Int): String =
    LocalTime.of((minutesSinceMidnight / 60).coerceIn(0, 23), (minutesSinceMidnight % 60).coerceIn(0, 59))
        .format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TypeSelector(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val options = listOf(
        TransactionType.EXPENSE to "Expense",
        TransactionType.INCOME to "Income",
        TransactionType.TRANSFER to "Transfer"
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ClickableField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun <T> DropdownPicker(
    label: String,
    selectedText: String,
    selectedIcon: String?,
    items: List<T>,
    itemText: (T) -> String,
    itemIcon: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    IconMapper.iconFor(selectedIcon ?: "category"),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemText(item)) },
                    leadingIcon = {
                        Icon(IconMapper.iconFor(itemIcon(item)), contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun AccountPicker(
    label: String,
    accounts: List<Account>,
    selectedId: Long?,
    dropdown: Boolean,
    onSelect: (Long) -> Unit
) {
    if (accounts.isEmpty()) {
        Text(
            "Add an account from the side menu (Accounts) to get started.",
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    if (dropdown) {
        val selected = accounts.firstOrNull { it.id == selectedId }
        DropdownPicker(
            label = label,
            selectedText = selected?.name ?: "Select",
            selectedIcon = selected?.icon,
            items = accounts,
            itemText = { it.name },
            itemIcon = { it.icon },
            onSelect = { onSelect(it.id) }
        )
    } else {
        SectionLabel(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            accounts.forEach { account ->
                FilterChip(
                    selected = account.id == selectedId,
                    onClick = { onSelect(account.id) },
                    label = { Text(account.name) },
                    leadingIcon = {
                        Icon(
                            IconMapper.iconFor(account.icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    label: String,
    categories: List<Category>,
    selectedId: Long?,
    dropdown: Boolean,
    onSelect: (Long) -> Unit
) {
    if (categories.isEmpty()) {
        Text("No categories yet. Add some from the side menu (Categories).", style = MaterialTheme.typography.bodyMedium)
        return
    }
    if (dropdown) {
        val selected = categories.firstOrNull { it.id == selectedId }
        DropdownPicker(
            label = label,
            selectedText = selected?.name ?: "Select",
            selectedIcon = selected?.icon,
            items = categories,
            itemText = { it.name },
            itemIcon = { it.icon },
            onSelect = { onSelect(it.id) }
        )
    } else {
        SectionLabel(label)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = category.id == selectedId,
                    onClick = { onSelect(category.id) },
                    label = { Text(category.name) },
                    leadingIcon = {
                        Icon(
                            IconMapper.iconFor(category.icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}
