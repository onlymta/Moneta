package com.moneta.app.ui.importexport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.data.export.ExportRepository
import com.moneta.app.ui.common.PeriodSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ExportUiState(
    val period: PeriodSelection = PeriodSelection(),
    val isExporting: Boolean = false,
    val lastResult: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val exportRepository: ExportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun onPeriodChange(period: PeriodSelection) = _uiState.update { it.copy(period = period, lastResult = null) }

    fun defaultFileName(extension: String): String {
        val label = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(DateRange.todayDate())
        return "moneta-export-$label.$extension"
    }

    fun exportCsv(uriString: String) = export(uriString) { outputStream, range ->
        exportRepository.exportCsv(range, outputStream)
    }

    fun exportPdf(uriString: String) = export(uriString) { outputStream, range ->
        exportRepository.exportPdf(
            range = range,
            reportTitle = "Transaction Report",
            periodLabel = uiState.value.period.label(),
            outputStream = outputStream
        )
    }

    private fun export(uriString: String, block: suspend (java.io.OutputStream, DateRange) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, lastResult = null) }
            val uri = android.net.Uri.parse(uriString)
            val result = runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    block(stream, uiState.value.period.range())
                } ?: error("Could not open the chosen file for writing.")
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    lastResult = if (result.isSuccess) "Export complete." else "Export failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
