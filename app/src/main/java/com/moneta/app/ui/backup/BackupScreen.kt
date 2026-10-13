package com.moneta.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun BackupScreen(viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.createBackup(it.toString()) } }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.requestRestore(it.toString()) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        Text("Backup & Restore", style = MaterialTheme.typography.titleLarge)

        Column {
            Text("Local backup", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(MonetaSpacing.xs))
            Text(
                "Saves everything - accounts, categories, transactions, budgets, goals, and bills - to a single file you choose the location for. This is separate from any cloud backup, which isn't set up yet.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = { createLauncher.launch(viewModel.defaultBackupFileName()) },
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create backup")
        }

        OutlinedButton(
            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restore from backup")
        }

        state.lastMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }

        Column {
            Text("Cloud backup", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(MonetaSpacing.xs))
            Text(
                if (state.isCloudConnected) {
                    "Connected to ${state.cloudProviderName}."
                } else {
                    "Not connected. ${state.cloudProviderName} support needs a Google Cloud project and OAuth client that only Moneta's publisher can set up - this button is honest about that rather than pretending to work."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            onClick = viewModel::connectCloud,
            enabled = !state.isCloudConnected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isCloudConnected) "Connected" else "Connect ${state.cloudProviderName}")
        }
    }

    if (state.pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Replace all current data?") },
            text = {
                Text(
                    "Restoring will replace every account, category, transaction, budget, goal, and bill currently in Moneta with what's in this backup file. This can't be undone from within the app - make sure this is the backup you want."
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
