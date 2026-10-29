package com.moneta.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.settings.BackupFrequency
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun formatWhen(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a"))

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.createBackup(it.toString()) } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.requestRestore(it.toString()) } }

    val context = androidx.compose.ui.platform.LocalContext.current
    val openPlus = com.moneta.app.ui.plus.LocalOpenPlus.current
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.setBackupFolder(uri.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Backup & restore", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    Text("Your data file", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everything in one file. When saving, pick Google Drive, OneDrive, Dropbox or any storage app from the list to keep it in the cloud, free.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                        Button(
                            onClick = { createLauncher.launch(viewModel.defaultBackupFileName()) },
                            enabled = !state.isWorking,
                            modifier = Modifier.weight(1f)
                        ) { Text("Download backup") }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                            enabled = !state.isWorking,
                            modifier = Modifier.weight(1f)
                        ) { Text("Restore") }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    Text("Automatic backup", style = MaterialTheme.typography.titleMedium)
                    if (!state.hasPlus) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Plus feature: backups on a schedule, plus an extra copy in a folder (Google Drive, if your phone offers it).",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = openPlus) { Text("See Plus") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                        BackupFrequency.entries.forEach { option ->
                            FilterChip(
                                selected = state.frequency == option,
                                enabled = state.hasPlus,
                                onClick = { viewModel.setFrequency(option) },
                                label = {
                                    Text(
                                        when (option) {
                                            BackupFrequency.OFF -> "Off"
                                            BackupFrequency.DAILY -> "Daily"
                                            BackupFrequency.WEEKLY -> "Weekly"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Text(
                        "Saved inside Paisabook when you open the app and a backup is due (latest 7 kept). " +
                            "Those copies are deleted if the app is uninstalled, so choose a folder below to keep copies outside the app.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (state.lastAutoBackupAt > 0L) "Last automatic backup: " + formatWhen(state.lastAutoBackupAt)
                        else "No automatic backup yet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Extra copy in a folder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (state.backupFolder.isBlank()) "Not set"
                                else android.net.Uri.decode(android.net.Uri.parse(state.backupFolder).lastPathSegment.orEmpty()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { folderLauncher.launch(null) }, enabled = state.hasPlus) {
                            Text(if (state.backupFolder.isBlank()) "Choose" else "Change")
                        }
                        if (state.backupFolder.isNotBlank()) {
                            TextButton(onClick = { viewModel.setBackupFolder("") }) { Text("Stop") }
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::backupNow,
                        enabled = !state.isWorking && state.hasPlus,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back up now") }

                    state.localBackups.forEach { backup ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(formatWhen(backup.modifiedAt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    (backup.sizeBytes / 1024L).coerceAtLeast(1L).toString() + " KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.requestRestoreLocal(backup.name) }) { Text("Restore") }
                            TextButton(onClick = { viewModel.deleteLocal(backup.name) }) { Text("Delete") }
                        }
                    }
                }
            }

            state.lastMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

        }
    }

    if (state.pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Replace all current data?") },
            text = {
                Text(
                    "Restoring replaces every account, category, transaction, budget, goal and bill in Paisabook with what is in this backup. This can't be undone, so download a backup of your current data first if unsure."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) { Text("Replace and restore") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") }
            }
        )
    }
}
