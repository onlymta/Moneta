package com.moneta.app.ui.plus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.plus.TRIAL_DAYS
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing

private val plusFeatures = listOf(
    "No ads, ever",
    "Charts and the 6-period trends, always on (Free: watch a short ad to see them for 24 hours)",
    "Excel and PDF exports with no ad (Free: one short ad per file; CSV is always free)",
    "Sub-categories with their own budgets and analysis",
    "Compact Home cards and rearranging the cards",
    "Reordering accounts, categories and budgets",
    "Change the category of many transactions at once",
    "Bill reminder notifications",
    "Working-day and weekend repeats, and \"every 2, 3...\" repeats",
    "Automatic backups, including a copy in a folder you choose (Google Drive, if your phone offers it)",
    "Every future Plus feature, with no extra payment"
)

@Composable
fun PlusScreen(onBack: () -> Unit, viewModel: PlusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val billing by viewModel.billingUi.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadPrice() }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Expendora Plus", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    Text(
                        when {
                            state.purchased -> "You have Expendora Plus"
                            state.trialActive -> "Free trial: ${state.trialDaysLeft} day(s) left"
                            else -> "Expendora Plus"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            state.purchased -> "Thank you for supporting Expendora. Everything below is unlocked for good."
                            state.trialActive -> "Everything below is unlocked during your $TRIAL_DAYS-day trial. No card needed."
                            state.trialAvailable -> "Try every Plus feature free for $TRIAL_DAYS days. Starts only when you tap the button, no card needed."
                            else -> "One payment, yours for good. No subscription."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    Text("What Plus adds", style = MaterialTheme.typography.titleMedium)
                    plusFeatures.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (!state.purchased) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(MonetaSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                    ) {
                        if (state.trialAvailable) {
                            OutlinedButton(onClick = viewModel::startTrial, modifier = Modifier.fillMaxWidth()) {
                                Text("Start $TRIAL_DAYS-day free trial")
                            }
                        }
                        Button(
                            onClick = { context.findActivity()?.let { viewModel.buy(it) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (billing.price != null) "Unlock Plus for ${billing.price}"
                                else "Unlock Plus"
                            )
                        }
                        Text(
                            "One-time payment. Charged once by Google Play, never again.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = viewModel::restorePurchase,
                            enabled = !billing.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Restore purchase") }
                    }
                }
            }

            billing.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                ) {
                    Text("Our promise", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your records, budgets, CSV and Excel export, and manual backup and restore are free forever. " +
                            "Your data is never locked or deleted if Plus is off. " +
                            "If you change your mind, Google Play lets you request a refund; see Google Play's refund policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!state.hasPlus) {
                LockedInsightsCard(what = "Not ready to buy yet?")
            }
        }
    }
}
