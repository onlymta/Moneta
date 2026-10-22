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
    private val folderKeepCount = 14

    /** What happened with the optional chosen folder on the last backup: null if none is set, else a short message. */
    @Volatile
    var lastFolderResult: String? = null
        private set

    /** Also writes the backup into the folder the user picked, so it survives uninstalling the app. */
    private suspend fun copyToChosenFolder(file: File): String? {
        val folder = settingsRepository.preferences.first().autoBackupFolder
        if (folder.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val tree = android.net.Uri.parse(folder)
                val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(tree)
                val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
                val created = android.provider.DocumentsContract.createDocument(
                    resolver, parent, "application/octet-stream", file.name
                ) ?: return@withContext "Couldn't create the file in the chosen folder."
                resolver.openOutputStream(created)?.use { out ->
                    FileInputStream(file).use { input -> input.copyTo(out) }
                } ?: return@withContext "Couldn't write to the chosen folder."
                pruneFolder(tree, treeDocId)
                "Copied to your chosen folder."
            } catch (e: Exception) {
                "Couldn't write to the chosen folder. Pick it again in Backup."
            }
        }
    }

    /** Keeps only the newest few automatic backups in the chosen folder. */
    private fun pruneFolder(tree: android.net.Uri, treeDocId: String) {
        val resolver = context.contentResolver
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        val found = ArrayList<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (name.startsWith("moneta-auto-") && name.endsWith(".monetabackup")) found.add(cursor.getString(0) to name)
            }
        }
        found.sortedByDescending { it.second }.drop(folderKeepCount).forEach { entry ->
            val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, entry.first)
            runCatching { android.provider.DocumentsContract.deleteDocument(resolver, doc) }
        }
    }

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
        lastFolderResult = copyToChosenFolder(file)
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
