@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.moneta.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moneta.app.ui.theme.MonetaSpacing

private data class MoreItem(val title: String, val onClick: (() -> Unit)? = null)

/**
 * Section 27's "More" destination. Every module has a real, navigable
 * screen. Goals and Bills rows are hidden entirely when disabled in Settings
 * (Section 26's module toggles) rather than shown-but-nonfunctional.
 */
@Composable
fun MoreScreen(
    goalsEnabled: Boolean,
    billsEnabled: Boolean,
    onAccountsClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onGoalsClick: () -> Unit,
    onBillsClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRecurringClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val items = buildList {
        add(MoreItem("Accounts", onAccountsClick))
        add(MoreItem("Categories", onCategoriesClick))
        if (billsEnabled) add(MoreItem("Bills", onBillsClick))
        add(MoreItem("Recurring transactions", onRecurringClick))
        if (goalsEnabled) add(MoreItem("Goals", onGoalsClick))
        add(MoreItem("Insights", onInsightsClick))
        add(MoreItem("Backup & Restore", onBackupClick))
        add(MoreItem("Import", onImportClick))
        add(MoreItem("Export", onExportClick))
        add(MoreItem("Settings", onSettingsClick))
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = if (item.onClick == null) {
                    { Text("Coming soon") }
                } else null,
                trailingContent = {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                },
                modifier = Modifier
                    .padding(horizontal = MonetaSpacing.xs)
                    .let { base -> item.onClick?.let { base.clickable(onClick = it) } ?: base }
            )
        }
    }
}
