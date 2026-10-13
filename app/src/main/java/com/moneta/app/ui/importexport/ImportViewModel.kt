package com.moneta.app.ui.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.csvimport.ImportOutcome
import com.moneta.app.data.csvimport.ImportPreview
import com.moneta.app.data.csvimport.TransactionCsvTemplate
import com.moneta.app.data.csvimport.TransactionImportCommitter
import com.moneta.app.data.csvimport.TransactionImportParser
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val isBusy: Boolean = false,
    val fileName: String? = null,
    /** Set once a file has been read and validated, before the user confirms committing it. */
    val preview: ImportPreview? = null,
    /** Set once the confirmed batch has actually been written to the database. */
    val outcome: ImportOutcome? = null,
    val errorMessage: String? = null
)

/**
 * Backs the "bring your data in from another app" CSV import flow (Section
 * 22): download a template, fill it in a spreadsheet, pick it back up here.
 * Parsing/validation and committing are two distinct steps so the user always
 * sees exactly what will happen - which rows are fine, which have problems,
 * and which categories are about to be created - before anything is written.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val importCommitter: TransactionImportCommitter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun defaultTemplateFileName(): String = "moneta-import-template.csv"

    fun downloadTemplate(uriString: String) {
        viewModelScope.launch {
            val uri = Uri.parse(uriString)
            val result = runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    TransactionCsvTemplate.write(stream)
                } ?: error("Could not open the chosen file for writing.")
            }
            _uiState.update {
                it.copy(
                    errorMessage = if (result.isFailure) "Couldn't save the template: ${result.exceptionOrNull()?.message}" else null
                )
            }
        }
    }

    /** Reads and validates [uriString] without writing anything to the database yet. */
    fun pickFile(uriString: String, displayName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, preview = null, outcome = null, fileName = displayName) }
            val uri = Uri.parse(uriString)
            val result = runCatching {
                val text = appContext.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: error("Could not open the chosen file for reading.")

                val accounts = accountRepository.observeActiveAccounts().first()
                val categories = categoryRepository.observeAll().first()
                val currencyCode = settingsRepository.preferences.first().currencyCode

                TransactionImportParser.parse(text, accounts, categories, currencyCode)
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    preview = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.let { e -> "Couldn't read that file: ${e.message}" }
                )
            }
        }
    }

    /** Commits the currently-previewed rows (only ever the ones that passed validation). */
    fun confirmImport() {
        val rows = uiState.value.preview?.rows.orEmpty()
        if (rows.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = runCatching { importCommitter.commit(rows) }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    outcome = result.getOrNull(),
                    preview = if (result.isSuccess) null else it.preview,
                    errorMessage = result.exceptionOrNull()?.let { e -> "Import failed: ${e.message}" }
                )
            }
        }
    }

    /** Clears everything so the user can pick a different file (or fix and re-pick the same one). */
    fun reset() {
        _uiState.value = ImportUiState()
    }
}
