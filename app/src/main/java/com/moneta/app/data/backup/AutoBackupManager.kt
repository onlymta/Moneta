package com.moneta.app.data.backup

import android.content.Context
import com.moneta.app.data.settings.BackupFrequency
import com.moneta.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** One backup file kept inside the app's own storage. */
data class LocalBackup(val name: String, val modifiedAt: Long, val sizeBytes: Long)

/**
 * Keeps automatic backups in the app's private storage. A backup is taken when the app
 * is opened and the chosen frequency (daily or weekly) has passed since the last one; it
 * is not a background job. The newest few are kept and older ones are removed.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val settingsRepository: SettingsRepository
) {
    private val keepCount = 7

    private fun directory(): File {
        val dir = File(context.filesDir, "auto_backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Takes a backup now if the chosen frequency says one is due. */
    suspend fun runIfDue() {
        val prefs = settingsRepository.preferences.first()
        val days = when (prefs.autoBackupFrequency) {
            BackupFrequency.OFF -> return
            BackupFrequency.DAILY -> 1L
            BackupFrequency.WEEKLY -> 7L
        }
        val dueAfterMillis = days * 24L * 60L * 60L * 1000L - 60L * 60L * 1000L
        if (System.currentTimeMillis() - prefs.lastAutoBackupAt >= dueAfterMillis) {
            backupNow()
        }
    }

    /** Writes a backup file into app storage and trims old ones. Returns the new file's name. */
    suspend fun backupNow(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
        val file = File(directory(), "moneta-auto-$stamp.monetabackup")
        withContext(Dispatchers.IO) {
            FileOutputStream(file).use { stream -> backupManager.createBackup(stream) }
            val all = directory().listFiles { f -> f.name.endsWith(".monetabackup") }.orEmpty()
                .sortedByDescending { it.lastModified() }
            all.drop(keepCount).forEach { it.delete() }
        }
        settingsRepository.setLastAutoBackupAt(System.currentTimeMillis())
        return file.name
    }

    fun list(): List<LocalBackup> =
        directory().listFiles { f -> f.name.endsWith(".monetabackup") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { LocalBackup(it.name, it.lastModified(), it.length()) }

    suspend fun restore(name: String): RestoreResult = withContext(Dispatchers.IO) {
        val file = File(directory(), name)
        if (!file.exists() || file.parentFile != directory()) {
            RestoreResult.Failed("That backup file is no longer there.")
        } else {
            FileInputStream(file).use { stream -> backupManager.restoreBackup(stream) }
        }
    }

    fun delete(name: String) {
        val file = File(directory(), name)
        if (file.exists() && file.parentFile == directory()) file.delete()
    }
}
