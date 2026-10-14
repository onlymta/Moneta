package com.moneta.app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun NotificationsScreen(
    onOpen: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val alerts by viewModel.alerts.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Notifications",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = MonetaSpacing.screenHorizontal,
                end = MonetaSpacing.screenHorizontal,
                top = MonetaSpacing.md
            )
        )
        Text(
            "Alerts about your budgets, bills and scheduled transactions. They appear here inside the app.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = MonetaSpacing.screenHorizontal,
                vertical = MonetaSpacing.xs
            )
        )

        if (alerts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MonetaSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IncomeGreen)
                Spacer(Modifier.height(MonetaSpacing.sm))
                Text("You're all caught up", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No budget warnings, overdue bills or upcoming scheduled items.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = MonetaSpacing.screenHorizontal,
                    end = MonetaSpacing.screenHorizontal,
                    top = MonetaSpacing.sm,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(alert, onClick = { onOpen(alert.route) })
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: AppAlert, onClick: () -> Unit) {
    val tint = when (alert.level) {
        AlertLevel.URGENT -> ExpenseRed
        AlertLevel.WARNING -> androidx.compose.ui.graphics.Color(0xFFB8860B)
        AlertLevel.INFO -> MaterialTheme.colorScheme.primary
    }
    val icon = when (alert.level) {
        AlertLevel.URGENT -> Icons.Outlined.ErrorOutline
        AlertLevel.WARNING -> Icons.Outlined.WarningAmber
        AlertLevel.INFO -> Icons.Outlined.Info
    }
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(MonetaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
