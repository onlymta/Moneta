@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.settings.PickerStyle
import com.moneta.app.data.settings.ThemeMode
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(MonetaSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
        ) {
            item {
                SettingsSection(title = "Profile") {
                    var nameInput by remember(prefs.userName) { mutableStateOf(prefs.userName) }
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(40) },
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(MonetaSpacing.sm))
                    Button(
                        onClick = { viewModel.setUserName(nameInput) },
                        enabled = nameInput.trim() != prefs.userName,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save name") }
                }
            }

            item {
                SettingsSection(title = "Appearance") {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(MonetaSpacing.sm))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = prefs.themeMode == mode,
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
            }

            item {
                SettingsSection(title = "Currency") {
                    OutlinedTextField(
                        value = prefs.currencyCode,
                        onValueChange = { if (it.length <= 3) viewModel.setCurrencyCode(it.uppercase()) },
                        label = { Text("Currency code (e.g. PKR, USD, EUR)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(MonetaSpacing.xs))
                    Text(
                        "Changes what's stored for new entries going forward. Existing amounts already recorded in a different currency aren't converted - Moneta doesn't support mixing currencies within one set of totals yet, so pick this once, early.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsSection(title = "Modules") {
                    SettingsToggleRow(
                        title = "Budgets",
                        subtitle = "Show the Budgets tab and budget status on Home.",
                        checked = prefs.budgetsEnabled,
                        onCheckedChange = viewModel::setBudgetsEnabled
                    )
                    SettingsToggleRow(
                        title = "Goals",
                        subtitle = "Show Goals under More.",
                        checked = prefs.goalsEnabled,
                        onCheckedChange = viewModel::setGoalsEnabled
                    )
                    SettingsToggleRow(
                        title = "Bills",
                        subtitle = "Show Bills under More.",
                        checked = prefs.billsEnabled,
                        onCheckedChange = viewModel::setBillsEnabled
                    )
                }
            }

            item {
                SettingsSection(title = "Transactions") {
                    SettingsToggleRow(
                        title = "Confirm before deleting",
                        subtitle = "Ask before deleting a transaction. (Currently only enforced on the Transaction Detail screen's own delete button, which already has this dialog - other delete actions in the app don't check this setting yet.)",
                        checked = prefs.confirmBeforeDelete,
                        onCheckedChange = viewModel::setConfirmBeforeDelete
                    )
                    SettingsToggleRow(
                        title = "Compact transaction rows",
                        subtitle = "Stored for a future denser list layout - the Transactions list doesn't read this yet.",
                        checked = prefs.compactTransactionRows,
                        onCheckedChange = viewModel::setCompactTransactionRows
                    )
                }
            }

            item {
                var advancedOpen by remember { mutableStateOf(false) }
                SettingsSection(title = "Advanced settings") {
                    TextButton(onClick = { advancedOpen = !advancedOpen }) {
                        Text(if (advancedOpen) "Hide customization" else "Show customization")
                    }
                    if (advancedOpen) {
                        Text("Customization", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(MonetaSpacing.xs))
                        SettingsToggleRow(
                            title = "Accounts in the bottom bar",
                            subtitle = "Add Accounts next to Home, Records, Budgets and Reports. Everything else is always in the side menu.",
                            checked = prefs.showAccountsInBottomBar,
                            onCheckedChange = viewModel::setShowAccountsInBottomBar
                        )
                        SettingsToggleRow(
                            title = "Carry forward balance",
                            subtitle = "On: each month's opening balance includes what was left from earlier months. Off: every month starts from zero and only shows that month's own income and spending.",
                            checked = prefs.carryForwardBalance,
                            onCheckedChange = viewModel::setCarryForwardBalance
                        )
                        SettingsToggleRow(
                            title = "Roll over unspent budget",
                            subtitle = "Add whatever you didn't spend of a category budget last month to this month's budget.",
                            checked = prefs.rolloverBudgets,
                            onCheckedChange = viewModel::setRolloverBudgets
                        )
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        Text("Add transaction: account and category pickers", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(MonetaSpacing.sm))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            PickerStyle.entries.forEachIndexed { index, style ->
                                SegmentedButton(
                                    selected = prefs.pickerStyle == style,
                                    onClick = { viewModel.setPickerStyle(style) },
                                    shape = SegmentedButtonDefaults.itemShape(index, PickerStyle.entries.size)
                                ) {
                                    Text(if (style == PickerStyle.CHIPS) "Chips" else "Dropdown")
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "About") {
                    Text("Moneta", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Version ${com.moneta.app.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(MonetaSpacing.sm))
        content()
        Spacer(Modifier.height(MonetaSpacing.sm))
        Divider()
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MonetaSpacing.xs),
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
