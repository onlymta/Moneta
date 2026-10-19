@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.DisplayCurrency
import com.moneta.app.data.settings.BackupFrequency
import com.moneta.app.data.settings.PickerStyle
import com.moneta.app.data.settings.ThemeMode
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.navigation.BACKUP_ROUTE
import com.moneta.app.ui.navigation.CATEGORIES_ROUTE
import com.moneta.app.ui.navigation.EXPORT_ROUTE
import com.moneta.app.ui.navigation.IMPORT_ROUTE
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The areas the Settings home links to. */
private enum class SettingsArea(val title: String) {
    PROFILE("Profile"),
    CURRENCY("Currency and format"),
    APPEARANCE("Appearance"),
    BUDGETS("Budgets and balances"),
    MODULES("Features"),
    CUSTOMIZE("Customization"),
    REMINDERS("Reminders")
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    var areaName by rememberSaveable { mutableStateOf("") }
    val area: SettingsArea? = SettingsArea.entries.firstOrNull { it.name == areaName }

    BackHandler(enabled = area != null) { areaName = "" }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(
            title = area?.title ?: "Settings",
            onBack = { if (area != null) areaName = "" else onBack() }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            if (area == null) {
                SettingsHome(prefs, viewModel) { areaName = it.name }
            } else {
                when (area) {
                    SettingsArea.PROFILE -> ProfileArea(prefs.userName, viewModel)
                    SettingsArea.CURRENCY -> CurrencyArea(prefs.displayCurrency, prefs.showCurrencySymbol, viewModel)
                    SettingsArea.APPEARANCE -> AppearanceArea(prefs.themeMode, viewModel)
                    SettingsArea.BUDGETS -> BudgetsArea(prefs.carryForwardBalance, prefs.rolloverBudgets, viewModel)
                    SettingsArea.MODULES -> ModulesArea(prefs.budgetsEnabled, prefs.goalsEnabled, prefs.billsEnabled, viewModel)
                    SettingsArea.CUSTOMIZE -> CustomizeArea(prefs, viewModel)
                    SettingsArea.REMINDERS -> RemindersArea(prefs.reminderEnabled, prefs.reminderHour, prefs.reminderMinute, viewModel)
                }
            }
        }
    }
}

// ---- the home list --------------------------------------------------------------------------

@Composable
private fun SettingsHome(
    prefs: com.moneta.app.data.settings.AppPreferences,
    viewModel: SettingsViewModel,
    onOpen: (SettingsArea) -> Unit
) {
    // Profile card
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable { onOpen(SettingsArea.PROFILE) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    prefs.userName.trim().take(1).uppercase().ifEmpty { "M" },
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.width(MonetaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    prefs.userName.ifBlank { "Add your name" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Currency: " + prefs.displayCurrency,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    GroupLabel("General")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            AreaRow(Icons.Filled.AttachMoney, "Currency and format", prefs.displayCurrency + (if (prefs.showCurrencySymbol) ", symbol shown" else ", plain figures")) {
                onOpen(SettingsArea.CURRENCY)
            }
            Divider()
            AreaRow(Icons.Filled.Palette, "Appearance", themeLabel(prefs.themeMode)) { onOpen(SettingsArea.APPEARANCE) }
            Divider()
            AreaRow(Icons.Filled.Notifications, "Reminders", if (prefs.reminderEnabled) "Daily at " + timeLabel(prefs.reminderHour, prefs.reminderMinute) else "Off") {
                onOpen(SettingsArea.REMINDERS)
            }
        }
    }

    GroupLabel("Money")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            AreaRow(Icons.Filled.AccountBalanceWallet, "Budgets and balances", if (prefs.carryForwardBalance) "Carry forward on" else "Carry forward off") {
                onOpen(SettingsArea.BUDGETS)
            }
            Divider()
            AreaRow(Icons.Filled.Extension, "Features", "Budgets, goals, bills") { onOpen(SettingsArea.MODULES) }
        }
    }

    GroupLabel("App")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            AreaRow(Icons.Filled.Tune, "Customization", "Bottom bar, record form") { onOpen(SettingsArea.CUSTOMIZE) }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = MonetaSpacing.xs)
    )
}

@Composable
private fun AreaRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MonetaSpacing.cardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(MonetaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Follows your phone"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun timeLabel(hour: Int, minute: Int): String =
    LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).format(DateTimeFormatter.ofPattern("h:mm a"))

// ---- the areas ------------------------------------------------------------------------------------

@Composable
private fun ProfileArea(userName: String, viewModel: SettingsViewModel) {
    var nameInput by remember(userName) { mutableStateOf(userName) }
    OutlinedTextField(
        value = nameInput,
        onValueChange = { nameInput = it.take(40) },
        label = { Text("Your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "Shown on Home and printed on exports.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = { viewModel.setUserName(nameInput) },
        enabled = nameInput.trim() != userName,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Save") }
}

@Composable
private fun CurrencyArea(code: String, showSymbol: Boolean, viewModel: SettingsViewModel) {
    ToggleRow("Show currency symbol", "Off shows plain figures only", showSymbol, viewModel::setShowCurrencySymbol)
    Text(
        "Changing the currency only changes the label. Amounts you already recorded are not converted.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            DisplayCurrency.choices.forEachIndexed { index, choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setDisplayCurrency(choice.first) }
                        .padding(horizontal = MonetaSpacing.cardPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(choice.first, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.width(56.dp))
                    Text(choice.second, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (choice.first == code) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (index < DisplayCurrency.choices.size - 1) Divider()
            }
        }
    }
}

@Composable
private fun AppearanceArea(themeMode: ThemeMode, viewModel: SettingsViewModel) {
    Text("Theme", style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = themeMode == mode,
                onClick = { viewModel.setThemeMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
            ) {
                Text(
                    when (mode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                )
            }
        }
    }
}

@Composable
private fun BudgetsArea(carryForward: Boolean, rollover: Boolean, viewModel: SettingsViewModel) {
    ToggleRow(
        "Carry forward balance",
        "Each month's opening balance includes what was left from earlier months",
        carryForward,
        viewModel::setCarryForwardBalance
    )
    ToggleRow(
        "Roll over unspent budget",
        "Add last month's unspent budget to this month's",
        rollover,
        viewModel::setRolloverBudgets
    )
}

@Composable
private fun ModulesArea(budgets: Boolean, goals: Boolean, bills: Boolean, viewModel: SettingsViewModel) {
    ToggleRow("Budgets", "The Budgets tab", budgets, viewModel::setBudgetsEnabled)
    ToggleRow("Goals", "Savings goals in the side menu", goals, viewModel::setGoalsEnabled)
    ToggleRow("Bills", "Bill tracking in the side menu", bills, viewModel::setBillsEnabled)
}

@Composable
private fun CustomizeArea(prefs: com.moneta.app.data.settings.AppPreferences, viewModel: SettingsViewModel) {
    GroupLabel("Bottom bar")
    Text(
        "Turn tabs on or off and move them left or right. Home is always shown.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    prefs.tabOrder.forEachIndexed { index, key ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tabLabel(key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(
                enabled = index > 0,
                onClick = {
                    val list = prefs.tabOrder.toMutableList()
                    list.removeAt(index)
                    list.add(index - 1, key)
                    viewModel.setTabOrder(list)
                }
            ) { Text("Left") }
            TextButton(
                enabled = index < prefs.tabOrder.size - 1,
                onClick = {
                    val list = prefs.tabOrder.toMutableList()
                    list.removeAt(index)
                    list.add(index + 1, key)
                    viewModel.setTabOrder(list)
                }
            ) { Text("Right") }
            Switch(
                checked = key !in prefs.hiddenTabs,
                enabled = key != "home",
                onCheckedChange = { on ->
                    viewModel.setHiddenTabs(if (on) prefs.hiddenTabs - key else prefs.hiddenTabs + key)
                }
            )
        }
    }

    Divider(modifier = Modifier.padding(vertical = MonetaSpacing.xs))
    GroupLabel("Record form")
    ToggleRow("Save buttons at top", "Instead of the bottom", prefs.formSaveAtTop, viewModel::setFormSaveAtTop)
    ToggleRow("Account and category in one row", "Pop-up list style only", prefs.formPickersInRow, viewModel::setFormPickersInRow)
    ToggleRow("Pickers above the amount", "Choose account and category first", prefs.formPickersAboveAmount, viewModel::setFormPickersAboveAmount)
    ToggleRow("Notes above the amount", "Instead of below it", prefs.formNotesAboveAmount, viewModel::setFormNotesAboveAmount)
    ToggleRow("Date and time below the pad", "Moves the date row under the number pad", prefs.formDateBelowKeypad, viewModel::setFormDateBelowKeypad)
    ToggleRow("Built-in number pad", "Calculator keys (+ − × ÷) instead of the phone keyboard", prefs.formCustomKeypad, viewModel::setFormCustomKeypad)
    Text("Account and category pickers", style = MaterialTheme.typography.bodyMedium)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        PickerStyle.entries.forEachIndexed { index, style ->
            SegmentedButton(
                selected = prefs.pickerStyle == style,
                onClick = { viewModel.setPickerStyle(style) },
                shape = SegmentedButtonDefaults.itemShape(index, PickerStyle.entries.size)
            ) {
                Text(if (style == PickerStyle.CHIPS) "Chips" else "Pop-up list")
            }
        }
    }
}

@Composable
private fun RemindersArea(enabled: Boolean, hour: Int, minute: Int, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showTime by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        blocked = !granted
        if (granted) viewModel.setReminder(true, hour, minute)
    }

    ToggleRow(
        "Daily reminder",
        "A notification asking you to record your transactions",
        enabled
    ) { on ->
        if (!on) {
            blocked = false
            viewModel.setReminder(false, hour, minute)
        } else {
            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                blocked = false
                viewModel.setReminder(true, hour, minute)
            }
        }
    }
    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Time: " + timeLabel(hour, minute))
    }
    if (blocked) {
        Text(
            "Notifications are blocked for Moneta. Allow them in your phone's app settings to get reminders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Text(
        "Your phone may deliver it a few minutes late to save battery.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminder(enabled, timeState.hour, timeState.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) }
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun tabLabel(key: String): String = when (key) {
    "home" -> "Home"
    "transactions" -> "Records"
    "budgets" -> "Budgets"
    "reports" -> "Reports"
    "accounts" -> "Accounts"
    else -> key
}
