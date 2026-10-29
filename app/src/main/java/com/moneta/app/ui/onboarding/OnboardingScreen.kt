@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.moneta.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.DisplayCurrency
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.ui.help.GuideCards
import com.moneta.app.ui.theme.MonetaSpacing

private const val STEP_COUNT = ONBOARDING_LAST_STEP + 1

/**
 * The welcome flow, in the order a new user needs things: welcome (new, or restore a backup),
 * name, accounts, basic settings, then a short guide. "Do this later" leaves a reminder on Home.
 */
@Composable
fun OnboardingScreen(
    onExit: (OnboardingExit) -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = state.step > 0) { viewModel.back() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Step ${state.step + 1} of $STEP_COUNT") },
                    actions = {
                        TextButton(onClick = { viewModel.skip(onExit) }) { Text("Do this later") }
                    }
                )
                LinearProgressIndicator(
                    progress = { (state.step + 1) / STEP_COUNT.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                    .padding(MonetaSpacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
            ) {
                when (state.step) {
                    0 -> WelcomeStep(state, viewModel, onExit)
                    1 -> NameStep(state, viewModel)
                    2 -> AccountsStep(state, viewModel)
                    3 -> SettingsStep(state, viewModel)
                    else -> GuideStep(state, viewModel, onExit)
                }
            }

            if (state.step in 1 until ONBOARDING_LAST_STEP) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.back() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) { Text("Back") }
                        Button(
                            onClick = { viewModel.next() },
                            modifier = Modifier
                                .weight(2f)
                                .height(52.dp)
                        ) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(state: OnboardingUiState, viewModel: OnboardingViewModel, onExit: (OnboardingExit) -> Unit) {
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restoreFromBackup(it.toString(), onExit) }
    }
    Spacer(Modifier.height(MonetaSpacing.xl))
    Text("Welcome to Paisabook", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text(
        "Your private money tracker. Everything stays on your phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(MonetaSpacing.lg))
    Text("How would you like to start?", style = MaterialTheme.typography.titleMedium)
    Button(
        onClick = { viewModel.next() },
        enabled = !state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) { Text("I'm new to Paisabook") }
    OutlinedButton(
        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
        enabled = !state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) { Text(if (state.isWorking) "Restoring..." else "I have a Paisabook backup file") }
    state.restoreMessage?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    Text(
        "A backup file is what Paisabook's Backup page saves. Choosing it brings back all your accounts, records and settings.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NameStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Spacer(Modifier.height(MonetaSpacing.md))
    Text("What should we call you?", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Your name appears on exported reports and greetings. You can leave it empty.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AccountsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("Where do you keep your money?", style = MaterialTheme.typography.headlineSmall)
    Text(
        "We listed a Cash and a Bank account. Type what each holds right now, remove any you don't use, or add more.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    state.accounts.forEach { account ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(MonetaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = account.name,
                        onValueChange = { viewModel.onAccountNameChange(account.key, it) },
                        label = { Text(accountTypeLabel(account.type)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.removeAccount(account.key) }) { Text("Remove") }
                }
                OutlinedTextField(
                    value = account.balance,
                    onValueChange = { viewModel.onAccountBalanceChange(account.key, it) },
                    label = { Text("Balance now (optional)") },
                    prefix = { Text(state.currency + " ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Text("Add another account", style = MaterialTheme.typography.titleMedium)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
    ) {
        listOf(
            AccountType.CASH, AccountType.BANK, AccountType.E_WALLET, AccountType.SAVINGS, AccountType.CREDIT_CARD
        ).forEach { type ->
            FilterChip(
                selected = state.newAccountType == type,
                onClick = { viewModel.onNewAccountTypeChange(type) },
                label = { Text(accountTypeLabel(type)) }
            )
        }
    }
    OutlinedTextField(
        value = state.newAccountName,
        onValueChange = viewModel::onNewAccountNameChange,
        label = { Text("Name (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.newAccountBalance,
        onValueChange = viewModel::onNewAccountBalanceChange,
        label = { Text("Balance now (optional)") },
        prefix = { Text(state.currency + " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = state.accountError != null,
        supportingText = state.accountError?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(onClick = { viewModel.addAccount() }, modifier = Modifier.fillMaxWidth()) {
        Text("Add this account")
    }
}

@Composable
private fun SettingsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("A few basics", style = MaterialTheme.typography.headlineSmall)
    Text(
        "You can change all of these later in Settings.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    var menuOpen by remember { mutableStateOf(false) }
    val currencyName = DisplayCurrency.choices.firstOrNull { it.first == state.currency }?.second.orEmpty()
    Box {
        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Currency: " + state.currency + (if (currencyName.isNotEmpty()) " ($currencyName)" else ""))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DisplayCurrency.choices.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text("$code  $name") },
                    onClick = {
                        viewModel.setCurrency(code)
                        menuOpen = false
                    }
                )
            }
        }
    }

    SwitchLine("Show the currency symbol", null, state.showSymbol, viewModel::setShowSymbol)
    Text("Decimals", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
        listOf(0, 1, 2).forEach { value ->
            FilterChip(
                selected = state.decimals == value,
                onClick = { viewModel.setDecimals(value) },
                label = { Text(value.toString()) }
            )
        }
    }
    SwitchLine(
        "Carry forward my balance",
        "Each month starts with what was left from earlier months.",
        state.carryForward,
        viewModel::setCarryForward
    )
    Text("Which features do you want?", style = MaterialTheme.typography.titleMedium)
    SwitchLine("Budgets", "Monthly limits for categories.", state.budgetsOn, viewModel::setBudgetsOn)
    SwitchLine("Goals", "Save towards something.", state.goalsOn, viewModel::setGoalsOn)
    SwitchLine("Bills", "Reminders for what is due.", state.billsOn, viewModel::setBillsOn)
}

@Composable
private fun GuideStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onExit: (OnboardingExit) -> Unit
) {
    Text("You're all set", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Tap a question to see the answer. You can come back to these any time from the side menu under Help and FAQs.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = { viewModel.finish(OnboardingExit.HOME, onExit) },
        enabled = !state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) { Text(if (state.isWorking) "Setting up..." else "Start using Paisabook") }
    OutlinedButton(
        onClick = { viewModel.finish(OnboardingExit.ADD_TRANSACTION, onExit) },
        enabled = !state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) { Text("Record my first transaction") }
    GuideCards()
    TextButton(onClick = { viewModel.back() }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}

@Composable
private fun SwitchLine(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun accountTypeLabel(type: AccountType): String = when (type) {
    AccountType.CASH -> "Cash"
    AccountType.BANK -> "Bank"
    AccountType.CREDIT_CARD -> "Credit card"
    AccountType.SAVINGS -> "Savings"
    AccountType.E_WALLET -> "Mobile wallet"
    AccountType.OTHER -> "Other"
}
