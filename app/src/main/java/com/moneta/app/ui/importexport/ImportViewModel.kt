package com.moneta.app.ui.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.data.csvimport.CsvTable
import com.moneta.app.data.csvimport.ImportOutcome
import com.moneta.app.data.csvimport.ImportPreview
import com.moneta.app.data.csvimport.TransactionCsvTemplate
import com.moneta.app.data.csvimport.TransactionImportCommitter
import com.moneta.app.data.csvimport.TransactionImportParser
import com.moneta.app.data.csvimport.XlsxReader
import com.moneta.app.data.repository.AccountRepository
import com.moneta.app.core.date.DateRange
import com.moneta.app.data.repository.CategoryRepository
import com.moneta.app.data.repository.TransactionRepository
import java.time.LocalDate
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
    /** The file's column headings, shown while the user matches them to Moneta's fields. */
    val headers: List<String> = emptyList(),
    /** One example value per column (from the first data row). */
    val sampleRow: List<String> = emptyList(),
    /** Moneta field -> zero-based column of the file. A field that is absent is "not in my file". */
    val mapping: Map<String, Int> = emptyMap(),
    val dataRowCount: Int = 0,
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
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val importCommitter: TransactionImportCommitter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var table: List<List<String>> = emptyList()
    private var headerRowOffset = 0

    fun defaultTemplateFileName(): String = "Moneta_Import_Template.xlsx"

    fun downloadTemplate(uriString: String) {
        viewModelScope.launch {
            val uri = Uri.parse(uriString)
            val result = runCatching {
                appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                    TransactionCsvTemplate.writeXlsx(stream)
                } ?: error("Could not open the chosen file for writing.")
            }
            _uiState.update {
                it.copy(
                    errorMessage = if (result.isFailure) "Couldn't save the template: ${result.exceptionOrNull()?.message}" else null
                )
            }
        }
    }

    /** Reads the file and proposes how its columns match Moneta's fields. Nothing is written yet. */
    fun pickFile(uriString: String, displayName: String?) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isBusy = true, errorMessage = null, preview = null, outcome = null, fileName = displayName, headers = emptyList())
            }
            val uri = Uri.parse(uriString)
            val result = runCatching {
                val bytes = appContext.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: error("Could not open the chosen file for reading.")

                val isZip = bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
                val isOldXls = bytes.size > 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte()
                if (isOldXls) {
                    error("This is an old Excel (.xls) file. In Excel use Save As and choose .xlsx or CSV, then pick that file.")
                }
                val raw = if (isZip) {
                    XlsxReader.read(bytes)
                } else {
                    val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
                    CsvTable.parse(text, CsvTable.detectDelimiter(text))
                }
                if (raw.isEmpty()) error("The file is empty.")
                raw
            }
            val raw = result.getOrNull()
            if (raw != null) {
                headerRowOffset = TransactionImportParser.detectHeaderRow(raw)
                table = raw.drop(headerRowOffset)
                val headers = table.first()
                val data = table.drop(1).filter { row -> row.any { it.isNotBlank() } }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        headers = headers,
                        sampleRow = data.firstOrNull().orEmpty(),
                        mapping = TransactionImportParser.suggestMapping(headers),
                        dataRowCount = data.size
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = result.exceptionOrNull()?.let { e -> "Couldn't read that file: ${e.message}" }
                    )
                }
            }
        }
    }

    /** Points [field] at the file column [column], or at "not in my file" when [column] is null. */
    fun setMapping(field: String, column: Int?) {
        _uiState.update {
            val next = it.mapping.toMutableMap()
            if (column == null) next.remove(field) else next[field] = column
            it.copy(mapping = next)
        }
    }

    /** Checks every row using the chosen column matches and builds the review. */
    fun checkFile() {
        val current = uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val result = runCatching {
                val accounts = accountRepository.observeActiveAccounts().first()
                val categories = categoryRepository.observeAll().first()
                val currencyCode = settingsRepository.preferences.first().currencyCode
                val nameById = accounts.associate { a -> a.id to a.name }
                val everything = DateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1))
                val existing = transactionRepository.observeFiltered(range = everything).first()
                val keys = HashMap<String, Int>()
                existing.forEach { t ->
                    val account = nameById[t.accountId]
                    if (account != null) {
                        val to = t.toAccountId?.let { id -> nameById[id] }
                        val key = TransactionImportParser.duplicateKey(t.date, t.amount.minorUnits, t.type, account, to)
                        keys[key] = (keys[key] ?: 0) + 1
                    }
                }
                TransactionImportParser.parseTable(
                    table, accounts, categories, currencyCode,
                    mapping = current.mapping, existingKeys = keys, rowOffset = headerRowOffset
                )
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    preview = result.getOrNull(),
                    errorMessage = result.exceptionOrNull()?.let { e -> "Couldn't check that file: ${e.message}" }
                )
            }
        }
    }

    /** Goes back from the review to the column matching. */
    fun backToColumns() {
        _uiState.update { it.copy(preview = null, errorMessage = null) }
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
