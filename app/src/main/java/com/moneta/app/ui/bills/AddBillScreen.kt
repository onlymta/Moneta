@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.bills

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.goals.GoalDatePickerDialog
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AddBillScreen(
    onDone: () -> Unit,
    viewModel: AddBillViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pickingDate by remember { mutableStateOf(false) }
    val hasPlus = com.moneta.app.ui.plus.rememberHasPlus()
    val openPlus = com.moneta.app.ui.plus.LocalOpenPlus.current

    // Reminders are notifications, so Android 13+ asks for permission the first time they are switched on.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    com.moneta.app.ui.common.FormScreenScaffold(
        title = if (state.isEditing) "Edit bill" else "Add bill",
        onClose = onDone
    ) {
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
        Text(
            "With both an account and a category set, \"Record payment\" can add the expense for you.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Due date", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickingDate = true }) { Text(state.dueDate.toString()) }
            Text("or due in", style = MaterialTheme.typography.bodyMedium)
            NumberDropdown(
                current = state.dueInDays?.let { if (it == 1) "1 day" else "$it days" } ?: "Pick",
                values = (1..31).toList(),
                label = { if (it == 1) "1 day" else "$it days" },
                onPick = viewModel::onDueInDaysChange
            )
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Remind me", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (hasPlus) "A notification on the day you choose, around 9 am." else "Plus feature: a notification on the day you choose.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.reminderOn,
                onCheckedChange = { on ->
                    if (on && !hasPlus) {
                        openPlus()
                    } else {
                        viewModel.onReminderOnChange(on)
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            )
        }
        if (state.reminderOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("Remind", style = MaterialTheme.typography.bodyMedium)
                NumberDropdown(
                    current = reminderLabel(state.reminderDaysBefore),
                    values = (0..30).toList(),
                    label = ::reminderLabel,
                    onPick = viewModel::onReminderDaysChange
                )
            }
        }

        Spacer(Modifier.height(0.dp))

        Button(
            onClick = { viewModel.save(onDone) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isSaving) "Saving…" else "Save bill")
        }
    }

    if (pickingDate) {
        GoalDatePickerDialog(
            initial = state.dueDate,
            onPick = { date ->
                if (date != null) viewModel.onDueDateChange(date)
                pickingDate = false
            },
            onDismiss = { pickingDate = false }
        )
    }
}

@Composable
private fun NumberDropdown(current: String, values: List<Int>, label: (Int) -> String, onPick: (Int) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menuOpen = true }) { Text(current) }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(label(value)) },
                    onClick = {
                        onPick(value)
                        menuOpen = false
                    }
                )
            }
        }
    }
}

private fun reminderLabel(days: Int): String = when (days) {
    0 -> "on the due date"
    1 -> "1 day before"
    else -> "$days days before"
}
