package com.moneta.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moneta.app.ui.common.SubPageHeader
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
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val alerts by viewModel.alerts.collectAsState()
    val seen by viewModel.seenIds.collectAsState()
    val unread = alerts.count { it.id !in seen }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Notifications", onBack = onBack)

        if (alerts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MonetaSpacing.screenHorizontal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (unread == 0) "All read" else "$unread unread",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (unread > 0) {
                    TextButton(onClick = viewModel::markAllRead) { Text("Mark all read") }
                }
                TextButton(onClick = viewModel::clearAll) { Text("Clear all") }
            }
        }

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
                    val isNew = alert.id !in seen
                    AlertCard(
                        alert = alert,
                        isNew = isNew,
                        onView = {
                            viewModel.markRead(alert.id)
                            onOpen(alert.route)
                        },
                        onToggleRead = {
                            if (isNew) viewModel.markRead(alert.id) else viewModel.markUnread(alert.id)
                        },
                        onDelete = { viewModel.delete(alert.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: AppAlert,
    isNew: Boolean,
    onView: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit
) {
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
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onView),
        colors = CardDefaults.cardColors(
            containerColor = if (isNew) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MonetaSpacing.cardPadding, top = MonetaSpacing.sm, bottom = MonetaSpacing.sm, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(MonetaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        alert.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "View",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (isNew) "Mark as read" else "Mark as unread") },
                        onClick = {
                            menuOpen = false
                            onToggleRead()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
