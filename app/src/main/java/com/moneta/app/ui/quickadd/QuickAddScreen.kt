@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.moneta.app.ui.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import com.moneta.app.data.local.entity.AccountType
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
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
import com.moneta.app.core.money.AmountCalculator
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

    val context = LocalContext.current
    LaunchedEffect(state.formError) {
        state.formError?.let { Toast.makeText(context, "Not saved: $it", Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(state.amountError) {
        state.amountError?.let { Toast.makeText(context, "Not saved: $it", Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(state.addAnotherTick) {
        state.savedMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val successText = when {
        isEditing -> "Changes saved"
        state.isFutureDate -> "Scheduled for " + state.date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        else -> "Transaction saved"
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Drops focus (and the on-screen keyboard) so a field never grabs the keyboard back by itself.
    val dropFocus: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    val accent: Color = when (state.type) {
        TransactionType.EXPENSE -> ExpenseRed
        TransactionType.INCOME -> IncomeGreen
        TransactionType.TRANSFER -> TransferGray
    }

    val isTransfer = state.type == TransactionType.TRANSFER
    val dropdown = state.pickerStyle == PickerStyle.DROPDOWN
    val inRow = state.pickersInRow && dropdown

    Scaffold(
        // Insets are applied once, on the Column below.
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = MonetaSpacing.sm, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDone,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(22.dp))
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (state.saveAtTop) {
                    if (!isEditing) {
                        TextButton(
                            onClick = { viewModel.save(onSaved = onDone, addAnother = true) },
                            enabled = !state.isSaving
                        ) { Text("+ Another") }
                    }
                    Button(
                        onClick = {
                            viewModel.save(onSaved = {
                                Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                                onDone()
                            })
                        },
                        enabled = !state.isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.padding(end = MonetaSpacing.xs)
                    ) {
                        Text(if (isEditing) "Save changes" else if (state.isFutureDate) "Schedule" else "Save")
                    }
                }
            }
        }
    ) { padding ->
        // With the built-in number pad the phone keyboard (for notes) slides OVER the pad instead
        // of pushing it up, so only the navigation bar is avoided in that case.
        val bottomInsets = if (state.customKeypad) {
            WindowInsets.navigationBars
        } else {
            WindowInsets.ime.union(WindowInsets.navigationBars)
        }

        val amountBlock: @Composable () -> Unit = {
            if (state.customKeypad) {
                AmountDisplay(
                    expression = state.amountInput,
                    accent = accent,
                    error = state.amountError
                )
            } else {
                OutlinedTextField(
                    value = state.amountInput,
                    onValueChange = viewModel::onAmountChange,
                    placeholder = {
                        Text("0", style = MaterialTheme.typography.displayMedium)
                    },
                    prefix = { Text(com.moneta.app.core.money.DisplayCurrency.entryPrefix(), style = MaterialTheme.typography.titleLarge) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.displayMedium.copy(color = accent, fontWeight = FontWeight.Bold),
                    isError = state.amountError != null,
                    supportingText = state.amountError?.let { message -> { Text(message) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 104.dp)
                )
            }
            state.savedMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium, color = IncomeGreen)
            }
        }

        val pickersBlock: @Composable () -> Unit = {
            if (isTransfer) {
                val toAccounts = state.accounts.filter { it.id != state.selectedAccountId }
                if (inRow) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                        Box(modifier = Modifier.weight(1f)) {
                            AccountPicker("From", state.accounts, state.selectedAccountId, dropdown, viewModel::onAccountSelected, onCreate = { n, t -> viewModel.quickAddAccount(n, t, false) })
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AccountPicker("To", toAccounts, state.selectedToAccountId, dropdown, viewModel::onToAccountSelected, onCreate = { n, t -> viewModel.quickAddAccount(n, t, true) })
                        }
                    }
                } else {
                    AccountPicker("From account", state.accounts, state.selectedAccountId, dropdown, viewModel::onAccountSelected, onCreate = { n, t -> viewModel.quickAddAccount(n, t, false) })
                    AccountPicker("To account", toAccounts, state.selectedToAccountId, dropdown, viewModel::onToAccountSelected, onCreate = { n, t -> viewModel.quickAddAccount(n, t, true) })
                }
            } else {
                val levels = state.categoryLevels
                val first = levels.firstOrNull()
                if (inRow && first != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                        Box(modifier = Modifier.weight(1f)) {
                            AccountPicker("Account", state.accounts, state.selectedAccountId, dropdown, viewModel::onAccountSelected, onCreate = { n, t -> viewModel.quickAddAccount(n, t, false) })
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryPicker(first.label, first.options, first.selectedId, dropdown, viewModel::onCategorySelected, onCreate = { n -> viewModel.quickAddCategory(n, null) })
                        }
                    }
                } else {
                    AccountPicker("Account", state.accounts, state.selectedAccountId, dropdown, viewModel::onAccountSelected, onCreate = { n, t -> viewModel.quickAddAccount(n, t, false) })
                    if (first != null) {
                        CategoryPicker(first.label, first.options, first.selectedId, dropdown, viewModel::onCategorySelected, onCreate = { n -> viewModel.quickAddCategory(n, null) })
                    }
                }
                levels.forEachIndexed { index, level ->
                    if (index > 0) {
                        val parentId = levels[index - 1].selectedId
                        CategoryPicker(
                            level.label,
                            level.options,
                            level.selectedId,
                            dropdown,
                            viewModel::onCategorySelected,
                            onCreate = { n -> viewModel.quickAddCategory(n, parentId) }
                        )
                    }
                }
            }
        }

        // The notes box takes whatever height is left on a fixed screen, so it is as large as it can be.
        val notesBlock: @Composable ColumnScope.() -> Unit = {
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                placeholder = { Text("Notes or description (optional)") },
                modifier = if (dropdown) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 72.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                }
            )
        }

        val dateTimeBlock: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                CompactChip(
                    icon = Icons.Filled.Event,
                    text = state.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                    onClick = { dropFocus(); showDatePicker = true },
                    modifier = Modifier.weight(1.5f)
                )
                CompactChip(
                    icon = Icons.Filled.Schedule,
                    text = formatTime(state.timeMinutes),
                    onClick = { dropFocus(); showTimePicker = true },
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.isFutureDate && !isEditing) {
                Text(
                    "Scheduled for " + state.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")) +
                        ". Your balance changes on that date.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(bottomInsets)
                .pointerInput(Unit) { detectTapGestures(onTap = { dropFocus() }) }
        ) {
            // Fixed screen. It only scrolls when the account/category chips are used, since those can be long.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .let { if (dropdown) it else it.verticalScroll(rememberScrollState()) }
                    .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                TypeSelector(state.type, onSelect = viewModel::onTypeChange)
                if (state.pickersAboveAmount) pickersBlock()
                if (state.notesAboveAmount) notesBlock()
                amountBlock()
                if (!state.pickersAboveAmount) pickersBlock()
                if (!state.notesAboveAmount) notesBlock()
                if (!state.dateBelowKeypad) dateTimeBlock()
            }

            if (state.customKeypad) {
                CalculatorKeypad(
                    onKey = { key ->
                        dropFocus()
                        viewModel.onAmountExpressionChange(applyKeypadKey(state.amountInput, key))
                    }
                )
            }

            if (state.dateBelowKeypad) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) { dateTimeBlock() }
            }

            if (!state.saveAtTop) Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    state.formError?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                        if (!isEditing) {
                            OutlinedButton(
                                onClick = { viewModel.save(onSaved = onDone, addAnother = true) },
                                enabled = !state.isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Save & add another")
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.save(onSaved = {
                                    Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                                    onDone()
                                })
                            },
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
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
private fun CompactChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

/** A tappable card showing the current choice; it opens a centred pop-up card to pick from (like the date picker). */
@Composable
private fun <T> DropdownPicker(
    label: String,
    selectedText: String,
    selectedIcon: String?,
    selectedId: Long?,
    items: List<T>,
    itemId: (T) -> Long,
    itemText: (T) -> String,
    itemIcon: (T) -> String,
    onSelect: (T) -> Unit,
    onAddNew: (() -> Unit)? = null
) {
    var popupOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentIcon = selectedIcon ?: "category"
    Surface(
        onClick = {
            focusManager.clearFocus()
            keyboard?.hide()
            popupOpen = true
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.moneta.app.ui.common.IconBadge(
                iconName = currentIcon,
                colorHex = IconMapper.colorFor(currentIcon),
                size = 34.dp
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
    }
    if (popupOpen) {
        var search by remember { mutableStateOf("") }
        val filtered = if (search.isBlank()) items else items.filter {
            itemText(it).contains(search.trim(), ignoreCase = true)
        }
        Dialog(onDismissRequest = { popupOpen = false }) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    if (items.size > 6) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(top = 4.dp)
                    ) {
                        items(filtered, key = { itemId(it) }) { item ->
                            val selected = itemId(item) == selectedId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        popupOpen = false
                                        onSelect(item)
                                    }
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.moneta.app.ui.common.IconBadge(
                                    iconName = itemIcon(item),
                                    colorHex = IconMapper.colorFor(itemIcon(item)),
                                    size = 36.dp
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
                                Text(
                                    itemText(item),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    "Nothing matches your search.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (onAddNew != null) {
                            TextButton(onClick = {
                                popupOpen = false
                                onAddNew()
                            }) { Text("+ New") }
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                        TextButton(onClick = { popupOpen = false }) { Text("Cancel") }
                    }
                }
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
    onSelect: (Long) -> Unit,
    onCreate: (String, AccountType) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    if (accounts.isEmpty()) {
        Text("No account yet.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { showNew = true }) { Text("+ New account") }
    } else if (dropdown) {
        val selected = accounts.firstOrNull { it.id == selectedId }
        DropdownPicker(
            label = label,
            selectedText = selected?.name ?: "Select",
            selectedIcon = selected?.icon,
            selectedId = selectedId,
            items = accounts,
            itemId = { it.id },
            itemText = { it.name },
            itemIcon = { it.icon },
            onSelect = { onSelect(it.id) },
            onAddNew = { showNew = true }
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
            AssistChip(onClick = { showNew = true }, label = { Text("+ New") })
        }
    }
    if (showNew) {
        NewAccountDialog(
            onDismiss = { showNew = false },
            onCreate = { name, type ->
                showNew = false
                onCreate(name, type)
            }
        )
    }
}

@Composable
private fun CategoryPicker(
    label: String,
    categories: List<Category>,
    selectedId: Long?,
    dropdown: Boolean,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    if (categories.isEmpty()) {
        Text("No categories here yet.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { showNew = true }) { Text("+ New") }
    } else if (dropdown) {
        val selected = categories.firstOrNull { it.id == selectedId }
        DropdownPicker(
            label = label,
            selectedText = selected?.name ?: "Select",
            selectedIcon = selected?.icon,
            selectedId = selectedId,
            items = categories,
            itemId = { it.id },
            itemText = { it.name },
            itemIcon = { it.icon },
            onSelect = { onSelect(it.id) },
            onAddNew = { showNew = true }
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
            AssistChip(onClick = { showNew = true }, label = { Text("+ New") })
        }
    }
    if (showNew) {
        NewNameDialog(
            title = "New category",
            onDismiss = { showNew = false },
            onCreate = { name ->
                showNew = false
                onCreate(name)
            }
        )
    }
}

@Composable
private fun NewNameDialog(title: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NewAccountDialog(onDismiss: () -> Unit, onCreate: (String, AccountType) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CASH) }
    val types = listOf(
        AccountType.CASH to "Cash",
        AccountType.BANK to "Bank",
        AccountType.SAVINGS to "Savings",
        AccountType.E_WALLET to "Wallet",
        AccountType.CREDIT_CARD to "Card"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    types.forEach { (t, text) ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(text) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, type) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Shows the typed amount (or calculation) big, with the running result underneath. */
@Composable
private fun AmountDisplay(expression: String, accent: Color, error: String?) {
    val result = if (AmountCalculator.hasOperator(expression)) AmountCalculator.evaluateToPlainString(expression) else null
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MonetaSpacing.md, vertical = MonetaSpacing.md),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                (com.moneta.app.core.money.DisplayCurrency.entryPrefix() + " " +
                    (if (expression.isEmpty()) "0" else expression.replace('-', '−'))).trim(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 2
            )
            if (result != null) {
                Text(
                    "= " + result,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Applies one keypad press to the current amount text. */
private fun applyKeypadKey(current: String, key: String): String {
    return when (key) {
        "C" -> ""
        "<" -> current.dropLast(1)
        "=" -> AmountCalculator.evaluateToPlainString(current) ?: current
        "." -> {
            val lastNumber = current.takeLastWhile { !AmountCalculator.isOperator(it) }
            when {
                lastNumber.contains('.') -> current
                lastNumber.isEmpty() -> current + "0."
                else -> current + "."
            }
        }
        "+", "-", "×", "÷" -> {
            when {
                current.isEmpty() -> current
                AmountCalculator.isOperator(current.last()) -> current.dropLast(1) + key
                else -> current + key
            }
        }
        else -> if (current.length >= 40) current else current + key
    }
}

@Composable
private fun CalculatorKeypad(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "-"),
        listOf(".", "0", "<", "+")
    )
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MonetaSpacing.sm, vertical = MonetaSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { key ->
                        KeypadKey(key, Modifier.weight(1f), onKey)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                KeypadKey("C", Modifier.weight(1f), onKey)
                KeypadKey("=", Modifier.weight(3f), onKey)
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier, onKey: (String) -> Unit) {
    val isOp = key in listOf("÷", "×", "-", "+", "=")
    val label = when (key) {
        "<" -> "⌫"
        "-" -> "−"
        else -> key
    }
    Surface(
        onClick = { onKey(key) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = if (isOp) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier.height(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
