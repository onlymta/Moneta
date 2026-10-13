@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.moneta.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.local.entity.AccountType
import com.moneta.app.ui.theme.MonetaSpacing

private const val STEP_COUNT = ONBOARDING_LAST_STEP + 1

/**
 * One-time welcome flow: your name, accounts, expense categories, then record a
 * first transaction or import a file. Every step can be skipped, and "Do this
 * later" leaves a reminder on Home.
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
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
            ) {
                when (state.step) {
                    0 -> WelcomeStep(state, viewModel)
                    1 -> AccountsStep(state, viewModel)
                    2 -> CategoriesStep(state, viewModel)
                    else -> FinishStep(state, viewModel, onExit)
                }
            }

            if (state.step < ONBOARDING_LAST_STEP) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                    ) {
                        if (state.step > 0) {
                            OutlinedButton(
                                onClick = { viewModel.back() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) { Text("Back") }
                        }
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
private fun WelcomeStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Spacer(Modifier.height(MonetaSpacing.md))
    Text("Welcome to Moneta", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Let's set things up in a minute. You can skip any step and change everything later.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::onNameChange,
        label = { Text("What should we call you?") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AccountsStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("Where do you keep your money?", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Add the accounts you use, like your wallet cash or a bank account. The balance is what you have in it right now.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    state.accounts.forEach { account ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(MonetaSpacing.sm))
            Text(account.name, style = MaterialTheme.typography.bodyLarge)
        }
    }

    val types = listOf(
        AccountType.CASH to "Cash",
        AccountType.BANK to "Bank",
        AccountType.E_WALLET to "Mobile wallet",
        AccountType.SAVINGS to "Savings",
        AccountType.CREDIT_CARD to "Credit card"
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
    ) {
        types.forEach { (type, label) ->
            FilterChip(
                selected = state.accountType == type,
                onClick = { viewModel.onAccountTypeChange(type) },
                label = { Text(label) }
            )
        }
    }
    OutlinedTextField(
        value = state.accountName,
        onValueChange = viewModel::onAccountNameChange,
        label = { Text("Name (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.accountBalance,
        onValueChange = viewModel::onAccountBalanceChange,
        label = { Text("Current balance (optional)") },
        prefix = { Text("PKR ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = state.accountError != null,
        supportingText = state.accountError?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(onClick = { viewModel.addAccount() }, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.accounts.isEmpty()) "Add this account" else "Add another account")
    }
}

@Composable
private fun CategoriesStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("What do you spend on?", style = MaterialTheme.typography.headlineSmall)
    Text(
        "These are ready to use. Tap any you don't need to turn it off, or add your own below.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
    ) {
        state.expenseCategories.forEach { category ->
            FilterChip(
                selected = category.id !in state.removedCategoryIds,
                onClick = { viewModel.toggleCategory(category.id) },
                label = { Text(category.name) }
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = state.newCategoryName,
            onValueChange = viewModel::onNewCategoryChange,
            label = { Text("New category") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = { viewModel.addCategory() }) { Text("Add") }
    }
}

@Composable
private fun FinishStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onExit: (OnboardingExit) -> Unit
) {
    Spacer(Modifier.height(MonetaSpacing.md))
    Text("You're all set", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Record your first transaction, or bring in your old records from a spreadsheet.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = { viewModel.finish(OnboardingExit.ADD_TRANSACTION, onExit) },
        enabled = !state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) { Text("Record a transaction") }
    OutlinedButton(
        onClick = { viewModel.finish(OnboardingExit.IMPORT, onExit) },
        enabled = !state.isWorking,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) { Text("Import from Excel / CSV") }
    TextButton(
        onClick = { viewModel.finish(OnboardingExit.HOME, onExit) },
        enabled = !state.isWorking,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Go to Home") }
    TextButton(onClick = { viewModel.back() }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
}
