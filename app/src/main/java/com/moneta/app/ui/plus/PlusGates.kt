package com.moneta.app.ui.plus

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.plus.AD_UNLOCK_HOURS
import com.moneta.app.ui.theme.MonetaSpacing

/** Opens the Moneta Plus page. Provided once around the whole navigation graph. */
val LocalOpenPlus = staticCompositionLocalOf<() -> Unit> { { } }

internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Shows [content] when charts are unlocked (Plus, trial, or a rewarded ad watched in the last
 * 24 hours). Otherwise shows a small card offering the ad or Plus.
 */
@Composable
fun InsightsGate(
    what: String,
    modifier: Modifier = Modifier,
    viewModel: PlusViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsState()
    if (state.insightsUnlocked) {
        content()
    } else {
        LockedInsightsCard(what = what, modifier = modifier, viewModel = viewModel)
    }
}

@Composable
fun LockedInsightsCard(what: String, modifier: Modifier = Modifier, viewModel: PlusViewModel = hiltViewModel()) {
    val busy by viewModel.adBusy.collectAsState()
    val message by viewModel.adMessage.collectAsState()
    val context = LocalContext.current
    val openPlus = LocalOpenPlus.current
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MonetaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            Text(what, style = MaterialTheme.typography.titleMedium)
            Text(
                "Watch one short ad to unlock this for $AD_UNLOCK_HOURS hours, or get Paisabook Plus once and never see ads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { context.findActivity()?.let { viewModel.watchAd(it) } },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Loading ad..." else "Watch an ad") }
                OutlinedButton(onClick = openPlus, modifier = Modifier.weight(1f)) { Text("Get Plus") }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** True for a Plus purchase, the free trial, or the test toggle's Plus mode. */
@Composable
fun rememberHasPlus(viewModel: PlusViewModel = hiltViewModel()): Boolean {
    val state by viewModel.state.collectAsState()
    return state.hasPlus
}

/** A short card for features that are only for Plus (nothing to "view for a while", so no ad option). */
@Composable
fun PlusOnlyCard(what: String, modifier: Modifier = Modifier) {
    val openPlus = LocalOpenPlus.current
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MonetaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            Text(what, style = MaterialTheme.typography.titleMedium)
            Text(
                "This is a Paisabook Plus feature. Plus is a one-time purchase, with no subscription.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = openPlus) { Text("See Paisabook Plus") }
        }
    }
}
