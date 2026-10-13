package com.moneta.app.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.Money
import com.moneta.app.data.csvimport.ImportIssue
import com.moneta.app.data.csvimport.ImportPreview
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.format.DateTimeFormatter

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
            .verticalScroll(rememberScrollState())
            .padding(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        Text("Import transactions", style = MaterialTheme.typography.titleLarge)

        if (state.preview == null && state.outcome == null) {
            Text(
                "Bring in records from another app or a spreadsheet, hundreds or thousands at a time. " +
                    "Pick an Excel (.xlsx) or CSV file. Moneta tidies up messy dates, times and amounts, " +
                    "creates any accounts and categories it doesn't have yet, and shows you a full review " +
                    "before anything is saved.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Only a Date and an Amount are really needed. Type, Account, Category and the rest are optional.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { templateLauncher.launch(viewModel.defaultTemplateFileName()) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download CSV template")
            }
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose file to import")
            }
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
                    if (outcome.createdAccountCount > 0) {
                        Text("Created ${outcome.createdAccountCount} new account(s).")
                    }
                    if (outcome.createdCategoryCount > 0) {
                        Text("Created ${outcome.createdCategoryCount} new categories or subcategories.")
                    }
                    TextButton(onClick = viewModel::reset) { Text("Import another file") }
                }
            }
        }

        state.preview?.let { preview ->
            ImportPreviewCard(
                preview = preview,
                fileName = state.fileName,
                isBusy = state.isBusy,
                onConfirm = viewModel::confirmImport,
                onReset = viewModel::reset
            )
        }
    }
}

private fun typeLabel(name: String): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun ImportPreviewCard(
    preview: ImportPreview,
    fileName: String?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onReset: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val skipped = preview.errors.map { it.rowNumber }.distinct().size

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MonetaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            Text("Review before importing", style = MaterialTheme.typography.titleMedium)
            if (!fileName.isNullOrBlank()) {
                Text(fileName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                "${preview.readyToImportCount} of ${preview.dataRowCount} row(s) are ready to import.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (skipped > 0) {
                Text(
                    "$skipped row(s) can't be imported and will be skipped (listed below).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val first = preview.firstDate
            val last = preview.lastDate
            if (first != null && last != null) {
                val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
                Text("Dates: ${first.format(fmt)} to ${last.format(fmt)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Income ${Money(preview.incomeMinor, preview.currencyCode).formatted()}  ·  " +
                        "Expenses ${Money(preview.expenseMinor, preview.currencyCode).formatted()}  ·  " +
                        "${preview.transferCount} transfer(s)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            preview.notes.forEach { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (preview.newAccounts.isNotEmpty()) {
                Text("New accounts to be created (${preview.newAccounts.size}):", fontWeight = FontWeight.Medium)
                preview.newAccounts.take(10).forEach {
                    Text("• ${it.name} (${typeLabel(it.type.name)})", style = MaterialTheme.typography.bodySmall)
                }
                if (preview.newAccounts.size > 10) {
                    Text("...and ${preview.newAccounts.size - 10} more", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (preview.newCategoryLabels.isNotEmpty()) {
                Text("New categories to be created (${preview.newCategoryLabels.size}):", fontWeight = FontWeight.Medium)
                preview.newCategoryLabels.take(10).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if (preview.newCategoryLabels.size > 10) {
                    Text("...and ${preview.newCategoryLabels.size - 10} more", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (preview.warnings.isNotEmpty()) {
                Text("Fixed or filled in automatically (${preview.warnings.size}):", fontWeight = FontWeight.Medium)
                IssueList(preview.warnings, 6, MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (preview.errors.isNotEmpty()) {
                Text(
                    "Skipped rows (${preview.errors.size} problem(s)):",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
                IssueList(preview.errors, 10, MaterialTheme.colorScheme.error)
            }

            if (preview.rows.isNotEmpty()) {
                Text("A few of the rows that will be imported:", fontWeight = FontWeight.Medium)
                preview.rows.take(5).forEach { r ->
                    val where = if (r.toAccountName != null) "${r.accountName} → ${r.toAccountName}" else r.accountName
                    val cat = r.categoryTopLevelName?.let { top -> if (r.categorySubName != null) "$top > ${r.categorySubName}" else top }
                    Text(
                        r.date.toString() + "  " + typeLabel(r.type.name) + "  " + r.amount.formatted() +
                            "\n" + where + (if (cat != null) "  ·  $cat" else ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = { showConfirm = true },
                enabled = !isBusy && preview.readyToImportCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import ${preview.readyToImportCount} transaction(s)")
            }
            TextButton(onClick = onReset, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel and choose a different file")
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Import now?") },
            text = {
                val extras = buildList {
                    if (preview.newAccounts.isNotEmpty()) add("create ${preview.newAccounts.size} new account(s)")
                    if (preview.newCategoryLabels.isNotEmpty()) add("create ${preview.newCategoryLabels.size} new categories")
                }
                val extraText = if (extras.isEmpty()) "" else " and " + extras.joinToString(" and ")
                Text(
                    "This will add ${preview.readyToImportCount} transaction(s)$extraText." +
                        (if (skipped > 0) " $skipped row(s) with problems will be skipped." else "") +
                        " Account balances will change."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    onConfirm()
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun IssueList(issues: List<ImportIssue>, limit: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        issues.take(limit).forEach { issue ->
            Text("Row ${issue.rowNumber}: ${issue.message}", style = MaterialTheme.typography.bodySmall, color = color)
        }
        if (issues.size > limit) {
            Text("...and ${issues.size - limit} more", style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
