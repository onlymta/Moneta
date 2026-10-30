package com.moneta.app.ui.importexport

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.data.export.ExportInfo
import com.moneta.app.data.export.ExportRepository
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.ui.common.PeriodMode
import com.moneta.app.ui.common.PeriodSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ExportUiState(
    val period: PeriodSelection = PeriodSelection(),
    val userName: String = "",
    val currencyCode: String = "",
    val isExporting: Boolean = false,
    val lastResult: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val exportRepository: ExportRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(userName = prefs.userName, currencyCode = prefs.currencyCode) }
            }
        }
    }

    fun onPeriodChange(period: PeriodSelection) = _uiState.update { it.copy(period = period, lastResult = null) }

    /** A short, file-name-safe description of the period, e.g. 2026-10, 2026, 2026-10-01_to_2026-10-31 or All-time. */
    private fun periodForFileName(period: PeriodSelection): String {
        val range = period.range()
        val last = range.endExclusive.minusDays(1)
        return when (period.mode) {
            PeriodMode.MONTH -> DateTimeFormatter.ofPattern("yyyy-MM").format(range.start)
            PeriodMode.YEAR -> DateTimeFormatter.ofPattern("yyyy").format(range.start)
            PeriodMode.DAY -> range.start.toString()
            PeriodMode.ALL -> "All-time"
            else -> range.start.toString() + "_to_" + last.toString()
        }
    }

    /** For example Moneta_Transactions_Ali_2026-10.xlsx: what it is, whose it is, and the period. */
    fun fileName(extension: String): String {
        val state = uiState.value
        val name = state.userName.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
        val parts = mutableListOf("Expendora", "Transactions")
        if (name.isNotEmpty()) parts.add(name)
        parts.add(periodForFileName(state.period))
        return parts.joinToString("_") + "." + extension
    }

    private fun info(): ExportInfo {
        val state = uiState.value
        return ExportInfo(
            userName = state.userName,
            periodLabel = state.period.label(),
            currencyCode = state.currencyCode.ifBlank { "PKR" }
        )
    }

    fun exportCsv(uriString: String) = export(uriString) { outputStream, range ->
        exportRepository.exportCsv(range, outputStream)
    }

    fun exportXlsx(uriString: String) = export(uriString) { outputStream, range ->
        exportRepository.exportXlsx(range, info(), outputStream)
    }

    fun exportPdf(uriString: String) = export(uriString) { outputStream, range ->
        exportRepository.exportPdf(range, info(), outputStream)
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
                    lastResult = if (result.isSuccess) "Export saved." else "Export failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
