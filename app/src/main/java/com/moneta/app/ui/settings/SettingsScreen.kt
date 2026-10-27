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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.input.ImeAction
import android.widget.Toast
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ColumnScope
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            PlusStatusCard()
            ProfileSection(prefs.userName, viewModel)
            GeneralSection(prefs, viewModel)
            MoneySection(prefs, viewModel)
            HomeSection(prefs, viewModel)
            LayoutSection(prefs, viewModel)
            RemindersSection(prefs.reminderEnabled, prefs.reminderHour, prefs.reminderMinute, viewModel)
            Spacer(Modifier.height(MonetaSpacing.xl))
        }
    }
}

@Composable
private fun PlusStatusCard(plusViewModel: com.moneta.app.ui.plus.PlusViewModel = hiltViewModel()) {
    val plus by plusViewModel.state.collectAsState()
    val openPlus = com.moneta.app.ui.plus.LocalOpenPlus.current
    Card(modifier = Modifier.fillMaxWidth().clickable { openPlus() }) {
        Row(
            modifier = Modifier.padding(MonetaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Moneta Plus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        plus.purchased -> "Unlocked. Thank you!"
                        plus.trialActive -> "Free trial: ${plus.trialDaysLeft} day(s) left"
                        plus.trialAvailable -> "Try all features free for 14 days"
                        else -> "No ads and all features, one payment"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(if (plus.purchased) "View" else "See", color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---- building blocks ------------------------------------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = MonetaSpacing.sm)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = MonetaSpacing.cardPadding, vertical = MonetaSpacing.xs),
            content = content
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A label on the left and a small segmented choice on the right, on one line. */
@Composable
private fun SegmentRow(title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.8f))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1.4f)) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) {
                    Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** A label with the current value on the right; tapping opens a small list to pick from. */
@Composable
private fun PickRow(title: String, value: String, choices: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var menuShown by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuShown = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = menuShown, onDismissRequest = { menuShown = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.first + "  " + choice.second) },
                    onClick = {
                        menuShown = false
                        onPick(choice.first)
                    }
                )
            }
        }
    }
}

/** A row that unfolds in place to show more controls. */
@Composable
private fun ExpandRow(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expanded) {
            Column(modifier = Modifier.padding(bottom = 6.dp), content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

// ---- sections -------------------------------------------------------------------------------------

@Composable
private fun ProfileSection(userName: String, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var nameInput by remember(userName) { mutableStateOf(userName) }
    val latestInput by rememberUpdatedState(nameInput)
    val latestSaved by rememberUpdatedState(userName)
    val save = {
        val clean = nameInput.trim()
        if (clean != userName) {
            viewModel.setUserName(clean)
            Toast.makeText(context, "Name saved", Toast.LENGTH_SHORT).show()
        }
    }
    // Leaving the page with an unsaved name saves it, so typing a name is never lost.
    DisposableEffect(Unit) {
        onDispose {
            val clean = latestInput.trim()
            if (clean != latestSaved) viewModel.setUserName(clean)
        }
    }
    SectionCard("Profile") {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    nameInput.trim().take(1).uppercase().ifEmpty { "M" },
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.width(MonetaSpacing.md))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it.take(40) },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                trailingIcon = {
                    IconButton(onClick = { save() }, enabled = nameInput.trim() != userName) {
                        Icon(Icons.Filled.Check, contentDescription = "Save name")
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GeneralSection(prefs: com.moneta.app.data.settings.AppPreferences, viewModel: SettingsViewModel) {
    SectionCard("General") {
        val currencyName = DisplayCurrency.choices.firstOrNull { it.first == prefs.displayCurrency }?.second.orEmpty()
        PickRow("Currency", prefs.displayCurrency + (if (currencyName.isNotEmpty()) " ($currencyName)" else ""), DisplayCurrency.choices) {
            viewModel.setDisplayCurrency(it)
        }
        RowDivider()
        SwitchRow("Show currency symbol", null, prefs.showCurrencySymbol, viewModel::setShowCurrencySymbol)
        RowDivider()
        SegmentRow("Decimals", listOf("0", "1", "2"), prefs.decimals.coerceIn(0, 2)) { viewModel.setDecimals(it) }
        RowDivider()
        SegmentRow(
            "Theme",
            listOf("System", "Light", "Dark"),
            ThemeMode.entries.indexOf(prefs.themeMode).coerceAtLeast(0)
        ) { viewModel.setThemeMode(ThemeMode.entries[it]) }
    }
}

@Composable
private fun MoneySection(prefs: com.moneta.app.data.settings.AppPreferences, viewModel: SettingsViewModel) {
    val plusVm: com.moneta.app.ui.plus.PlusViewModel = hiltViewModel()
    val plusState by plusVm.state.collectAsState()
    val hasPlus = plusState.hasPlus
    val openPlus = com.moneta.app.ui.plus.LocalOpenPlus.current
    SectionCard("Money and budgets") {
        SwitchRow(
            "Carry forward balance",
            "Each month opens with what was left from earlier months",
            prefs.carryForwardBalance,
            viewModel::setCarryForwardBalance
        )
        RowDivider()
        SwitchRow(
            "Roll over unspent budget",
            "Add last month's unspent budget to this month's",
            prefs.rolloverBudgets,
            viewModel::setRolloverBudgets
        )
        RowDivider()
        SwitchRow(
            "Take overspending off next month",
            "Spent more than a budget? Next month's budget for it is reduced by the extra",
            prefs.carryOverspend,
            viewModel::setCarryOverspend
        )
        RowDivider()
        SwitchRow(
            "Sub-categories",
            if (hasPlus) "Categories inside categories, each with its own budget and analysis" else "Plus feature: categories inside categories, each with its own budget",
            prefs.subcategoriesEnabled,
            { on -> if (hasPlus) viewModel.setSubcategoriesEnabled(on) else openPlus() }
        )
        RowDivider()
        SwitchRow("Budgets", "Show the Budgets tab", prefs.budgetsEnabled, viewModel::setBudgetsEnabled)
        RowDivider()
        SwitchRow("Goals", "Savings goals in the side menu", prefs.goalsEnabled, viewModel::setGoalsEnabled)
        RowDivider()
        SwitchRow("Bills", "Bill tracking in the side menu", prefs.billsEnabled, viewModel::setBillsEnabled)
    }
}

@Composable
private fun HomeSection(prefs: com.moneta.app.data.settings.AppPreferences, viewModel: SettingsViewModel) {
    val plusVm: com.moneta.app.ui.plus.PlusViewModel = hiltViewModel()
    val plusState by plusVm.state.collectAsState()
    val hasPlus = plusState.hasPlus
    val openPlus = com.moneta.app.ui.plus.LocalOpenPlus.current
    SectionCard("Home page") {
        SwitchRow(
            "Compact cards",
            if (hasPlus) "Three wide cards instead of six tiles" else "Plus feature: three wide cards instead of six tiles",
            prefs.compactHome,
            { on -> if (hasPlus) viewModel.setCompactHome(on) else openPlus() }
        )
        RowDivider()
        ExpandRow("Cards", "Show, hide and reorder") {
            val hidden = prefs.hiddenHomeCards
            val order = prefs.homeCardOrder
            order.forEachIndexed { index, key ->
                val card = com.moneta.app.data.settings.HomeCard.entries.firstOrNull { it.name == key } ?: return@forEachIndexed
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = index > 0 && !prefs.compactHome,
                        onClick = {
                            if (!hasPlus) {
                                openPlus()
                            } else {
                                val list = order.toMutableList()
                                list.removeAt(index)
                                list.add(index - 1, key)
                                viewModel.setHomeCardOrder(list)
                            }
                        }
                    ) { Text("Up") }
                    TextButton(
                        enabled = index < order.size - 1 && !prefs.compactHome,
                        onClick = {
                            if (!hasPlus) {
                                openPlus()
                            } else {
                                val list = order.toMutableList()
                                list.removeAt(index)
                                list.add(index + 1, key)
                                viewModel.setHomeCardOrder(list)
                            }
                        }
                    ) { Text("Down") }
                    Switch(
                        checked = card.name !in hidden,
                        onCheckedChange = { on ->
                            viewModel.setHiddenHomeCards(if (on) hidden - card.name else hidden + card.name)
                        }
                    )
                }
            }
            if (prefs.compactHome) {
                Text(
                    "Compact mode keeps a fixed order; the switches still hide figures.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LayoutSection(prefs: com.moneta.app.data.settings.AppPreferences, viewModel: SettingsViewModel) {
    SectionCard("Layout") {
        ExpandRow("Bottom bar", "Show, hide and reorder tabs") {
            prefs.tabOrder.forEachIndexed { index, key ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tabLabel(key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
        }
        RowDivider()
        ExpandRow("Record form", "Order and keypad options") {
            SwitchRow("Save buttons at top", null, prefs.formSaveAtTop, viewModel::setFormSaveAtTop)
            SwitchRow("Account and category in one row", null, prefs.formPickersInRow, viewModel::setFormPickersInRow)
            SwitchRow("Pickers above the amount", null, prefs.formPickersAboveAmount, viewModel::setFormPickersAboveAmount)
            SwitchRow("Notes above the amount", null, prefs.formNotesAboveAmount, viewModel::setFormNotesAboveAmount)
            SwitchRow("Built-in number pad", "Calculator keys instead of the phone keyboard", prefs.formCustomKeypad, viewModel::setFormCustomKeypad)
            SwitchRow("Date and time below the pad", null, prefs.formDateBelowKeypad, viewModel::setFormDateBelowKeypad)
            SegmentRow(
                "Pickers",
                listOf("Pop-up list", "Chips"),
                if (prefs.pickerStyle == PickerStyle.DROPDOWN) 0 else 1
            ) { viewModel.setPickerStyle(if (it == 0) PickerStyle.DROPDOWN else PickerStyle.CHIPS) }
        }
    }
}

@Composable
private fun RemindersSection(enabled: Boolean, hour: Int, minute: Int, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var showTime by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        blocked = !granted
        if (granted) viewModel.setReminder(true, hour, minute)
    }

    SectionCard("Reminders") {
        SwitchRow("Daily reminder", "A notification to record your transactions", enabled) { on ->
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
        if (enabled) {
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTime = true }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Time", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(timeLabel(hour, minute), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (blocked) {
            Text(
                "Notifications are blocked for Moneta. Allow them in your phone's app settings to get reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }

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

private fun timeLabel(hour: Int, minute: Int): String =
    LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)).format(DateTimeFormatter.ofPattern("h:mm a"))

private fun tabLabel(key: String): String = when (key) {
    "home" -> "Home"
    "transactions" -> "Records"
    "budgets" -> "Budgets"
    "reports" -> "Reports"
    "accounts" -> "Accounts"
    else -> key
}
