package com.moneta.app.data.backup

import javax.inject.Inject
import javax.inject.Singleton

/** One backup file as it appears in a cloud provider's storage. */
data class CloudBackupEntry(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val sizeBytes: Long
)

/**
 * ARCHITECTURE.md Section 12: cloud backup is a distinct concept from local
 * backup and from multi-device sync. This interface is the seam a real
 * provider (Google Drive first, per the architecture doc's reasoning about
 * not forcing a paid subscription) plugs into, without any of the local
 * backup/restore code in [BackupManager] needing to change.
 *
 * Implementations must never touch local backup/restore behavior, and a
 * missing/unconnected provider must never block local backup/restore from
 * working (Section 24: "do not make cloud functionality compromise offline
 * functionality" - Section 9 of the roadmap says the same for Phase 9).
 */
interface CloudBackupProvider {
    val providerName: String
    suspend fun isConnected(): Boolean
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun uploadBackup(fileName: String, content: ByteArray): Result<CloudBackupEntry>
    suspend fun listBackups(): Result<List<CloudBackupEntry>>
    suspend fun downloadBackup(entry: CloudBackupEntry): Result<ByteArray>
}

/**
 * The real implementation would use Google Sign-In / Credential Manager for
 * OAuth and the Drive REST API (`https://www.googleapis.com/drive/v3/files`)
 * for upload/list/download, scoped to `drive.appdata` or `drive.file` so
 * Moneta only ever sees its own backup files, never the user's whole Drive.
 *
 * That requires a Google Cloud project, an OAuth client ID, and a
 * `google-services.json` specific to whoever publishes this app - none of
 * which can be generated inside this codebase. Rather than fake a working
 * "Connect Google Drive" button (explicitly forbidden by Section 47), this
 * stub is honest about not being connected, so the UI can show a real,
 * truthful state instead of a button that does nothing.
 */
@Singleton
class UnavailableCloudBackupProvider @Inject constructor() : CloudBackupProvider {
    override val providerName: String = "Google Drive"

    override suspend fun isConnected(): Boolean = false

    override suspend fun connect(): Result<Unit> =
        Result.failure(CloudBackupUnavailableException())

    override suspend fun disconnect() = Unit

    override suspend fun uploadBackup(fileName: String, content: ByteArray): Result<CloudBackupEntry> =
        Result.failure(CloudBackupUnavailableException())

    override suspend fun listBackups(): Result<List<CloudBackupEntry>> =
        Result.failure(CloudBackupUnavailableException())

    override suspend fun downloadBackup(entry: CloudBackupEntry): Result<ByteArray> =
        Result.failure(CloudBackupUnavailableException())
}

class CloudBackupUnavailableException :
    Exception("Cloud backup isn't set up in this build - it needs a Google Cloud project and OAuth client that only the app's publisher can configure.")
