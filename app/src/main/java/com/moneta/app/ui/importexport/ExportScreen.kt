package com.moneta.app.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.common.PeriodBar
import com.moneta.app.ui.common.SubPageHeader
import com.moneta.app.ui.theme.MonetaSpacing

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        uri?.let { viewModel.exportXlsx(it.toString()) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.exportPdf(it.toString()) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it.toString()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubPageHeader(title = "Export", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MonetaSpacing.screenHorizontal, vertical = MonetaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MonetaSpacing.md)
        ) {
            PeriodBar(selection = state.period, onChange = viewModel::onPeriodChange)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MonetaSpacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetailLine("Prepared for", state.userName.ifBlank { "Add your name in Settings" })
                    DetailLine("Period", state.period.label())
                    DetailLine("Currency", state.currencyCode.ifBlank { "-" })
                }
            }

            FormatRow(
                badge = "XLSX",
                badgeColor = Color(0xFF1B8A4B),
                title = "Excel workbook",
                subtitle = "Summary, transactions and a by-category sheet",
                fileName = viewModel.fileName("xlsx"),
                enabled = !state.isExporting,
                onExport = { xlsxLauncher.launch(viewModel.fileName("xlsx")) }
            )
            FormatRow(
                badge = "PDF",
                badgeColor = Color(0xFFC62828),
                title = "PDF report",
                subtitle = "A neat printable statement",
                fileName = viewModel.fileName("pdf"),
                enabled = !state.isExporting,
                onExport = { pdfLauncher.launch(viewModel.fileName("pdf")) }
            )
            FormatRow(
                badge = "CSV",
                badgeColor = Color(0xFF546E7A),
                title = "CSV file",
                subtitle = "Plain data, can be imported back",
                fileName = viewModel.fileName("csv"),
                enabled = !state.isExporting,
                onExport = { csvLauncher.launch(viewModel.fileName("csv")) }
            )

            state.lastResult?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FormatRow(
    badge: String,
    badgeColor: Color,
    title: String,
    subtitle: String,
    fileName: String,
    enabled: Boolean,
    onExport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MonetaSpacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = badgeColor.copy(alpha = 0.14f),
                modifier = Modifier.size(width = 52.dp, height = 40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(badge, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = badgeColor)
                }
            }
            Spacer(Modifier.width(MonetaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(MonetaSpacing.sm))
            Button(onClick = onExport, enabled = enabled) { Text("Export") }
        }
    }
}
