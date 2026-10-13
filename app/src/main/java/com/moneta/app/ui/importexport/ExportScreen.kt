package com.moneta.app.ui.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.ui.theme.MonetaSpacing

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.exportCsv(it.toString()) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.exportPdf(it.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MonetaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(MonetaSpacing.lg)
    ) {
        Text("Export", style = MaterialTheme.typography.titleLarge)

        Text("Period", style = MaterialTheme.typography.labelMedium)
        Column(verticalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
            ExportPeriod.entries.chunked(3).forEach { rowItems ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(MonetaSpacing.sm)) {
                    rowItems.forEach { period ->
                        FilterChip(
                            selected = state.period == period,
                            onClick = { viewModel.onPeriodChange(period) },
                            label = { Text(periodLabel(period)) }
                        )
                    }
                }
            }
        }

        Text(
            "CSV opens in Excel, Google Sheets, or Numbers. A dedicated .xlsx writer isn't built yet - CSV is the fully working option for spreadsheet import today.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { csvLauncher.launch(viewModel.defaultFileName("csv")) },
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export as CSV")
        }

        OutlinedButton(
            onClick = { pdfLauncher.launch(viewModel.defaultFileName("pdf")) },
            enabled = !state.isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export as PDF report")
        }

        state.lastResult?.let {
            Spacer(Modifier.height(MonetaSpacing.sm))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun periodLabel(period: ExportPeriod): String = when (period) {
    ExportPeriod.THIS_MONTH -> "This month"
    ExportPeriod.LAST_MONTH -> "Last month"
    ExportPeriod.THIS_YEAR -> "This year"
}
