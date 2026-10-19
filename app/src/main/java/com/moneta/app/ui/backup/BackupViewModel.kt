package com.moneta.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.app.core.date.DateRange
import com.moneta.app.data.backup.AutoBackupManager
import com.moneta.app.data.backup.LocalBackup
import com.moneta.app.data.settings.BackupFrequency
import com.moneta.app.data.settings.SettingsRepository
import com.moneta.app.data.backup.BackupManager
import com.moneta.app.data.backup.CloudBackupProvider
import com.moneta.app.data.backup.RestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BackupUiState(
    val isWorking: Boolean = false,
    val lastMessage: String? = null,
    /** Set when a restore is about to overwrite existing data, so the screen can confirm first. */
    val pendingRestoreUri: String? = null,
    val cloudProviderName: String = "",
    val isCloudConnected: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.OFF,
    val lastAutoBackupAt: Long = 0L,
    val localBackups: List<LocalBackup> = emptyList()
)

private const val LOCAL_PREFIX = "local:"

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val backupManager: BackupManager,
    private val cloudBackupProvider: CloudBackupProvider,
    private val autoBackupManager: AutoBackupManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BackupUiState(cloudProviderName = cloudBackupProvider.providerName)
    )
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(frequency = prefs.autoBackupFrequency, lastAutoBackupAt = prefs.lastAutoBackupAt)
                }
            }
        }
        refreshLocal()
        viewModelScope.launch {
            val connected = cloudBackupProvider.isConnected()
            _uiState.update { it.copy(isCloudConnected = connected) }
        }
    }

    private fun refreshLocal() {
        _uiState.update { it.copy(localBackups = autoBackupManager.list()) }
    }

    fun setFrequency(frequency: BackupFrequency) {
        viewModelScope.launch { settingsRepository.setAutoBackupFrequency(frequency) }
    }

    fun backupNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, lastMessage = null) }
            val result = runCatching { autoBackupManager.backupNow() }
            refreshLocal()
            _uiState.update {
                it.copy(
                    isWorking = false,
                    lastMessage = if (result.isSuccess) "Backup saved on this phone." else "Backup failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun requestRestoreLocal(name: String) {
        _uiState.update { it.copy(pendingRestoreUri = LOCAL_PREFIX + name) }
    }

    fun deleteLocal(name: String) {
        autoBackupManager.delete(name)
        refreshLocal()
    }

    fun connectCloud() {
        viewModelScope.launch {
            val result = cloudBackupProvider.connect()
            _uiState.update {
                it.copy(
                    lastMessage = result.exceptionOrNull()?.message
                        ?: "Connected to ${cloudBackupProvider.providerName}.",
                    isCloudConnected = result.isSuccess
                )
            }
        }
    }

    fun defaultBackupFileName(): String {
        val label = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(DateRange.todayDate())
        return "moneta-backup-$label.monetabackup"
    }

    fun createBackup(uriString: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, lastMessage = null) }
            val result = runCatching {
                appContext.contentResolver.openOutputStream(Uri.parse(uriString))?.use { stream ->
                    backupManager.createBackup(stream)
                } ?: error("Could not open the chosen location for writing.")
            }
            _uiState.update {
                it.copy(
                    isWorking = false,
                    lastMessage = if (result.isSuccess) "Backup saved." else "Backup failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /** Called when the user picks a file to restore - requires explicit confirmation before proceeding (Section 24/31). */
    fun requestRestore(uriString: String) {
        _uiState.update { it.copy(pendingRestoreUri = uriString) }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(pendingRestoreUri = null) }
    }

    fun confirmRestore() {
        val uriString = _uiState.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, pendingRestoreUri = null, lastMessage = null) }
            val result = if (uriString.startsWith(LOCAL_PREFIX)) {
                autoBackupManager.restore(uriString.removePrefix(LOCAL_PREFIX))
            } else {
                appContext.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                    backupManager.restoreBackup(stream)
                } ?: RestoreResult.Failed("Could not open the chosen file.")
            }

            _uiState.update {
                it.copy(
                    isWorking = false,
                    lastMessage = when (result) {
                        RestoreResult.Success -> "Restore complete. Your data has been replaced with the backup."
                        is RestoreResult.Failed -> result.reason
                    }
                )
            }
        }
    }
}
