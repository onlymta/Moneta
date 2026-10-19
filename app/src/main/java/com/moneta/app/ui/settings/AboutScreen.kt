package com.moneta.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "About", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            Text("Moneta", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Version ${com.moneta.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "A simple, private money tracker. Your data stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
