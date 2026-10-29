package com.moneta.app.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.core.money.Money
import com.moneta.app.data.csvimport.ImportIssue
import com.moneta.app.data.csvimport.ImportPreview
import com.moneta.app.data.csvimport.TransactionImportParser
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.ExpenseRed
import com.moneta.app.ui.theme.IncomeGreen
import com.moneta.app.ui.theme.MonetaSpacing
import java.time.format.DateTimeFormatter

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun ImportScreen(onBack: () -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        uri?.let { viewModel.downloadTemplate(it.toString()) }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.pickFile(it.toString(), it.lastPathSegment) }
    }

    val stageStart = state.headers.isEmpty() && state.outcome == null
    val stageMatch = state.headers.isNotEmpty() && state.preview == null && state.outcome == null

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Import", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            if (stageStart) {
                Text(
                    "Bring in transactions from an Excel (.xlsx) or CSV file. Only Date and Amount are required. " +
                        "Your own column names are fine, you'll match them next. Transactions already in Paisabook are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    OutlinedButton(
                        onClick = { templateLauncher.launch(viewModel.defaultTemplateFileName()) },
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Get template") }
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !state.isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Choose file") }
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

            if (stageMatch) {
                ColumnMatchCard(
                    state = state,
                    onMap = viewModel::setMapping,
                    onCheck = viewModel::checkFile,
                    onChooseAnother = { filePickerLauncher.launch(arrayOf("*/*")) }
                )
            }

            state.outcome?.let { outcome ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(MonetaSpacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
                    ) {
                        Text("Import complete", style = MaterialTheme.typography.titleMedium, color = IncomeGreen)
                        Text("${outcome.importedCount} transaction(s) imported.")
                        if (outcome.createdAccountCount > 0) {
                            Text("${outcome.createdAccountCount} new account(s) created.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (outcome.createdCategoryCount > 0) {
                            Text("${outcome.createdCategoryCount} new categories created.", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = viewModel::reset) { Text("Import another file") }
                    }
                }
            }

            state.preview?.let { preview ->
                ReviewCard(
                    preview = preview,
                    fileName = state.fileName,
                    isBusy = state.isBusy,
                    onConfirm = viewModel::confirmImport,
                    onBack = viewModel::backToColumns,
                    onReset = viewModel::reset
                )
            }
        }
    }
}

private fun typeLabel(name: String): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

// ---- step 2: match the file's columns to Moneta's fields -----------------------------------

@Composable
private fun ColumnMatchCard(
    state: ImportUiState,
    onMap: (String, Int?) -> Unit,
    onCheck: () -> Unit,
    onChooseAnother: () -> Unit
) {
    val requiredOk = TransactionImportParser.REQUIRED_FIELDS.all { state.mapping.containsKey(it) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MonetaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.xs)
        ) {
            Text("Match columns", style = MaterialTheme.typography.titleMedium)
            Text(
                (state.fileName ?: "Your file") + "  ·  ${state.dataRowCount} row(s)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TransactionImportParser.FIELDS.forEach { field ->
                val required = field in TransactionImportParser.REQUIRED_FIELDS
                val chosen = state.mapping[field]
                MappingRow(
                    label = if (required) "$field *" else field,
                    headers = state.headers,
                    chosen = chosen,
                    sample = chosen?.let { state.sampleRow.getOrNull(it) },
                    problem = required && chosen == null,
                    onChoose = { onMap(field, it) }
                )
            }
            if (!requiredOk) {
                Text(
                    "Date and Amount must be matched to a column.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.size(MonetaSpacing.xs))
            Button(onClick = onCheck, enabled = requiredOk && !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("Check file")
            }
            TextButton(onClick = onChooseAnother, modifier = Modifier.fillMaxWidth()) {
                Text("Choose a different file")
            }
        }
    }
}

@Composable
private fun MappingRow(
    label: String,
    headers: List<String>,
    chosen: Int?,
    sample: String?,
    problem: Boolean,
    onChoose: (Int?) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.9f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (problem) MaterialTheme.colorScheme.error else Color.Unspecified
            )
            if (!sample.isNullOrBlank()) {
                Text(
                    "e.g. $sample",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(modifier = Modifier.weight(1.1f)) {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (chosen == null) "Not in my file" else headers.getOrNull(chosen).orEmpty().ifBlank { "Column ${chosen + 1}" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Not in my file") },
                    onClick = {
                        menuOpen = false
                        onChoose(null)
                    }
                )
                headers.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(name.ifBlank { "Column ${index + 1}" }) },
                        onClick = {
                            menuOpen = false
                            onChoose(index)
                        }
                    )
                }
            }
        }
    }
}

// ---- step 3: the review ------------------------------------------------------------------------

@Composable
private fun ReviewCard(
    preview: ImportPreview,
    fileName: String?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val skipped = preview.errors.map { it.rowNumber }.distinct().size
    val duplicateCount = preview.duplicates.size
    val amber = Color(0xFFB8860B)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MonetaSpacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)
        ) {
            Text("Review", style = MaterialTheme.typography.titleMedium)
            if (!fileName.isNullOrBlank()) {
                Text(
                    fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                StatTile("To import", preview.readyToImportCount, IncomeGreen, Modifier.weight(1f))
                StatTile("Duplicates", duplicateCount, amber, Modifier.weight(1f))
                StatTile("Skipped", skipped, ExpenseRed, Modifier.weight(1f))
            }

            val first = preview.firstDate
            val last = preview.lastDate
            if (first != null && last != null) {
                val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
                Text(
                    first.format(fmt) + " to " + last.format(fmt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.md)) {
                    Text(
                        "In " + Money(preview.incomeMinor, preview.currencyCode).formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IncomeGreen
                    )
                    Text(
                        "Out " + Money(preview.expenseMinor, preview.currencyCode).formatted(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseRed
                    )
                    if (preview.transferCount > 0) {
                        Text("${preview.transferCount} transfer(s)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            preview.notes.forEach { note ->
                Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (preview.errors.isNotEmpty()) {
                FoldSection("Can't import (${preview.errors.size})", ExpenseRed) {
                    IssueList(preview.errors, 15, ExpenseRed)
                }
            }
            if (duplicateCount > 0) {
                FoldSection("Already in Paisabook ($duplicateCount)", amber) {
                    Text(
                        "Same date, amount, type and account as an existing transaction, so they are left out.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    preview.duplicates.take(8).forEach { r ->
                        Text(
                            "Row ${r.rowNumber}: ${r.date}  ${r.amount.formatted()}  ${r.accountName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (duplicateCount > 8) Text("...and ${duplicateCount - 8} more", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (preview.warnings.isNotEmpty()) {
                FoldSection("Adjusted automatically (${preview.warnings.size})", MaterialTheme.colorScheme.onSurfaceVariant) {
                    IssueList(preview.warnings, 10, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val newCount = preview.newAccounts.size + preview.newCategoryLabels.size
            if (newCount > 0) {
                FoldSection("New accounts and categories ($newCount)", MaterialTheme.colorScheme.primary) {
                    preview.newAccounts.take(10).forEach {
                        Text("Account: ${it.name} (${typeLabel(it.type.name)})", style = MaterialTheme.typography.bodySmall)
                    }
                    preview.newCategoryLabels.take(12).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (newCount > 22) Text("...and more", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (preview.rows.isNotEmpty()) {
                FoldSection("Sample rows", MaterialTheme.colorScheme.onSurfaceVariant) {
                    preview.rows.take(5).forEach { r ->
                        val where = if (r.toAccountName != null) "${r.accountName} → ${r.toAccountName}" else r.accountName
                        val cat = r.categoryTopLevelName?.let { top ->
                            if (r.categorySubName != null) "$top › ${r.categorySubName}" else top
                        }
                        Text(
                            r.date.toString() + "  " + typeLabel(r.type.name) + "  " + r.amount.formatted() +
                                "\n" + where + (if (cat != null) "  ·  $cat" else ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Button(
                onClick = { showConfirm = true },
                enabled = !isBusy && preview.readyToImportCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import ${preview.readyToImportCount} transaction(s)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onBack, enabled = !isBusy, modifier = Modifier.weight(1f)) { Text("Columns") }
                OutlinedButton(onClick = onReset, enabled = !isBusy, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Import now?") },
            text = {
                Text(
                    "This adds ${preview.readyToImportCount} transaction(s) and changes account balances." +
                        (if (duplicateCount > 0) " $duplicateCount duplicate(s) are skipped." else "") +
                        (if (skipped > 0) " $skipped row(s) with problems are skipped." else "")
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
private fun StatTile(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = MonetaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** A heading that expands to show its details when tapped. */
@Composable
private fun FoldSection(title: String, color: Color, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        }
    }
}

@Composable
private fun IssueList(issues: List<ImportIssue>, limit: Int, color: Color) {
    Column {
        issues.take(limit).forEach { issue ->
            Text("Row ${issue.rowNumber}: ${issue.message}", style = MaterialTheme.typography.bodySmall, color = color)
        }
        if (issues.size > limit) {
            Text("...and ${issues.size - limit} more", style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}
