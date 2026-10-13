package com.moneta.app.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.csvimport.ImportIssue
import com.moneta.app.data.csvimport.ImportPreview
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun ImportScreen(viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.downloadTemplate(it.toString()) }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.pickFile(it.toString(), it.lastPathSegment) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        Text("Import transactions", style = MaterialTheme.typography.titleLarge)
        Text(
            "Bring in historical records from another app or a bank export, hundreds or " +
                "thousands at a time, instead of adding them one by one. Download the " +
                "template, fill it in a spreadsheet (Excel, Google Sheets, Numbers - any " +
                "app that saves as CSV), then pick the file here.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(
            onClick = { templateLauncher.launch(viewModel.defaultTemplateFileName()) },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Download CSV template")
        }

        Text(
            "Account (and To Account, for a transfer) must match an account name that " +
                "already exists - add it under More > Accounts first. A Category that " +
                "doesn't exist yet is created automatically.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { filePickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose file to import")
        }

        if (state.isBusy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(MonetaSpacing.sm))
                Text("Working...")
            }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        state.outcome?.let { outcome ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
                ) {
                    Text("Import complete", style = MaterialTheme.typography.titleMedium)
                    Text("Imported ${outcome.importedCount} transaction(s).")
                    if (outcome.createdCategoryCount > 0) {
                        val plural = if (outcome.createdCategoryCount == 1) "y" else "ies"
                        Text("Created ${outcome.createdCategoryCount} new categor$plural.")
                    }
                    TextButton(onClick = viewModel::reset) { Text("Import another file") }
                }
            }
        }

        state.preview?.let { preview ->
            ImportPreviewCard(
                preview = preview,
                isBusy = state.isBusy,
                onConfirm = viewModel::confirmImport,
                onReset = viewModel::reset
            )
        }
    }
}

@Composable
private fun ImportPreviewCard(
    preview: ImportPreview,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onReset: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MonetaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            Text(
                "${preview.readyToImportCount} of ${preview.dataRowCount} row(s) ready to import",
                style = MaterialTheme.typography.titleMedium
            )

            if (preview.newCategoryLabels.isNotEmpty()) {
                val plural = if (preview.newCategoryLabels.size == 1) "y" else "ies"
                Text("Will create ${preview.newCategoryLabels.size} new categor$plural:", fontWeight = FontWeight.Medium)
                preview.newCategoryLabels.take(8).forEach { Text("\u2022 $it", style = MaterialTheme.typography.bodySmall) }
                if (preview.newCategoryLabels.size > 8) {
                    Text("...and ${preview.newCategoryLabels.size - 8} more", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (preview.errors.isNotEmpty()) {
                Text(
                    "${preview.errors.size} row(s) will be skipped:",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
                IssueList(preview.errors, MaterialTheme.colorScheme.error)
            }

            if (preview.warnings.isNotEmpty()) {
                Text("${preview.warnings.size} warning(s):", fontWeight = FontWeight.Medium)
                IssueList(preview.warnings, MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = onConfirm,
                enabled = !isBusy && preview.readyToImportCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import ${preview.readyToImportCount} transaction(s)")
            }
            TextButton(onClick = onReset, enabled = !isBusy) { Text("Choose a different file") }
        }
    }
}

@Composable
private fun IssueList(issues: List<ImportIssue>, color: Color) {
    LazyColumn(modifier = Modifier.height(MonetaSpacing.xxxl * 2)) {
        items(issues.take(50)) { issue ->
            Text("Row ${issue.rowNumber}: ${issue.message}", style = MaterialTheme.typography.bodySmall, color = color)
        }
        if (issues.size > 50) {
            item { Text("...and ${issues.size - 50} more", style = MaterialTheme.typography.bodySmall, color = color) }
        }
    }
}
