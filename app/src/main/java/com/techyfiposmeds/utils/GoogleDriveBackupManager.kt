package com.techyfiposmeds.utils

import android.util.Log
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise Google Drive Backup Manager targeting appDataFolder.
 * 
 * Provides:
 * - Scoped Google Drive API client using https://www.googleapis.com/auth/drive.appdata
 * - Encrypted snapshot upload directly to appDataFolder
 * - Automatic retention enforcement (prunes oldest backups after upload)
 * - Resilient download and deletion
 */
object GoogleDriveBackupManager {

    private const val TAG = "GoogleDriveBackupMgr"
    private const val BACKUP_PREFIX = "techyfi-pos-backup-"
    private const val BACKUP_MIME_TYPE = "application/octet-stream"
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    data class DriveBackupRecord(
        val fileId: String,
        val fileName: String,
        val fileSize: Long,
        val createdTimestamp: Long,
        val isAutoBackup: Boolean,
        val formattedDate: String,
        val formattedSize: String
    )

    data class UploadResult(
        val isSuccess: Boolean,
        val fileId: String? = null,
        val message: String
    )

    /**
     * Builds a configured Google Drive Service with the minimal drive.appdata scope.
     */
    fun getDriveService(accessToken: String): Drive {
        require(accessToken.isNotBlank()) { "A valid Google Drive access token is required" }
        val bearerToken = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
            request.connectTimeout = 15_000
            request.readTimeout = 30_000
        }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            bearerToken
        )
        .setApplicationName("Techyfi POS")
        .build()
    }

    /**
     * Uploads an encrypted backup archive to Google Drive appDataFolder and enforces retention limits.
     */
    suspend fun uploadEncryptedBackup(
        driveService: Drive,
        localEncryptedFile: java.io.File,
        isAutoBackup: Boolean = false,
        retentionCount: Int = 7
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val remoteFileName = if (isAutoBackup) {
                "${BACKUP_PREFIX}auto-$dateStr.enc"
            } else {
                "${BACKUP_PREFIX}manual-$dateStr.enc"
            }

            val fileMetadata = File().apply {
                name = remoteFileName
                mimeType = BACKUP_MIME_TYPE
                description = "Techyfi POS Encrypted Backup Package"
                // Store in Google Drive's isolated appDataFolder sandbox
                parents = listOf("appDataFolder")
            }

            val mediaContent = FileContent(BACKUP_MIME_TYPE, localEncryptedFile)
            val uploaded = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name, size, createdTime")
                .execute()

            val uploadedId = uploaded.id
            if (uploadedId.isNullOrBlank()) {
                return@withContext UploadResult(false, null, "Google Drive did not return a file ID.")
            }

            Log.i(TAG, "Uploaded backup to Drive successfully. ID: $uploadedId")

            // Automatically enforce backup retention policy
            pruneExcessBackups(driveService, retentionCount)

            UploadResult(true, uploadedId, "Backup uploaded successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading backup to Google Drive", e)
            UploadResult(false, null, "Drive upload failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Lists all Techyfi POS backups stored in appDataFolder (and drive.file) with explicit Result return.
     */
    suspend fun listBackupsResult(driveService: Drive): Result<List<DriveBackupRecord>> = withContext(Dispatchers.IO) {
        try {
            val query = "name contains '$BACKUP_PREFIX' and trashed = false"
            val fileList = driveService.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .setFields("files(id, name, size, createdTime)")
                .setOrderBy("createdTime desc")
                .execute()

            val dateFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

            val records = fileList.files?.map { file ->
                val sizeBytes = file.getSize() ?: 0L
                val timeMillis = file.createdTime?.value ?: 0L
                val isAuto = file.name?.contains("auto", ignoreCase = true) == true

                val sizeStr = when {
                    sizeBytes >= 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
                    sizeBytes >= 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
                    else -> "$sizeBytes B"
                }

                DriveBackupRecord(
                    fileId = file.id ?: "",
                    fileName = file.name ?: "Unnamed Backup",
                    fileSize = sizeBytes,
                    createdTimestamp = timeMillis,
                    isAutoBackup = isAuto,
                    formattedDate = if (timeMillis > 0) dateFormat.format(Date(timeMillis)) else "Unknown Date",
                    formattedSize = sizeStr
                )
            } ?: emptyList()
            Result.success(records)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing Drive backups", e)
            Result.failure(e)
        }
    }

    suspend fun listBackups(driveService: Drive): List<DriveBackupRecord> =
        listBackupsResult(driveService).getOrDefault(emptyList())

    /**
     * Downloads an encrypted backup file from Google Drive to local storage.
     */
    suspend fun downloadBackup(
        driveService: Drive,
        fileId: String,
        destinationFile: java.io.File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(destinationFile).use { outputStream ->
                driveService.files().get(fileId)
                    .executeMediaAndDownloadTo(outputStream)
            }
            destinationFile.exists() && destinationFile.length() > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading backup from Drive", e)
            false
        }
    }

    /**
     * Deletes a specific backup from Google Drive.
     */
    suspend fun deleteBackup(driveService: Drive, fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            driveService.files().delete(fileId).execute()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting backup from Drive", e)
            false
        }
    }

    /**
     * Enforces the retention count (e.g. keep latest 7, 14, or 30 backups) by deleting older auto backups only.
     * Manual backups are permanently retained and never pruned.
     */
    private suspend fun pruneExcessBackups(driveService: Drive, maxToKeep: Int) {
        if (maxToKeep <= 0) return
        try {
            val allBackups = listBackups(driveService)
            val autoBackups = allBackups.filter { it.isAutoBackup }
            if (autoBackups.size > maxToKeep) {
                val toDelete = autoBackups.drop(maxToKeep)
                Log.i(TAG, "Pruning ${toDelete.size} old automatic backups to maintain retention limit of $maxToKeep (preserving manual backups)")
                for (record in toDelete) {
                    deleteBackup(driveService, record.fileId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune older backups: ${e.message}")
        }
    }
}
