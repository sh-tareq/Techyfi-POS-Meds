package com.example.techyfipos.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.techyfipos.TechyfiPOSApplication
import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsDataStore(context)
        val scope = inputData.getString(AutoBackupScheduler.INPUT_BACKUP_SCOPE)
            ?: AutoBackupScheduler.SCOPE_ALL
        val respectDueTime = inputData.getBoolean(
            AutoBackupScheduler.INPUT_RESPECT_DUE_TIME,
            false
        )

        return try {
            val localEnabled = settings.localAutoBackupFlow.first()
            val driveEnabled = settings.autoBackupFlow.first()
            val localFrequency = settings.autoBackupFrequencyFlow.first()
            val driveFrequency = settings.driveAutoBackupFrequencyFlow.first()
            val now = System.currentTimeMillis()

            val localRequested = scope != AutoBackupScheduler.SCOPE_DRIVE && localEnabled
            val driveRequested = scope != AutoBackupScheduler.SCOPE_LOCAL && driveEnabled
            val localDue = localRequested && (
                !respectDueTime || isDue(
                    now = now,
                    lastBackup = settings.lastLocalBackupTimestampFlow.first(),
                    frequency = localFrequency
                )
            )
            val driveDue = driveRequested && (
                !respectDueTime || isDue(
                    now = now,
                    lastBackup = settings.lastDriveBackupTimestampFlow.first(),
                    frequency = driveFrequency
                )
            )

            if (!localDue && !driveDue) {
                Log.i(TAG, "Backup skipped: disabled or not due")
                return Result.success()
            }

            val app = context as? TechyfiPOSApplication
            val repository = app?.repository ?: run {
                val db = AppDatabase.getDatabase(context)
                DataRepository(context, db, settings)
            }

            val tempFile = File(context.cacheDir, "auto_backup_${id}.json")
            val tempEncryptedFile = File(context.cacheDir, "auto_backup_enc_${id}.enc")
            try {
                val exported = repository.exportBackup(tempFile)
                if (!exported || !tempFile.exists() || tempFile.length() == 0L) {
                    Log.e(TAG, "Backup export failed or produced an empty file")
                    return Result.retry()
                }

                val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
                val backupFileName = "TechyfiPOS_AutoBackup_$timeString.json"
                var completedAt: Long? = null
                var driveFailed = false
                var driveNeedsUserAuthorization = false

                if (localDue) {
                    saveLocalCopies(context, tempFile, backupFileName)
                    completedAt = System.currentTimeMillis()
                    settings.saveLastLocalBackupTimestamp(completedAt)
                    Log.i(TAG, "Local automatic backup completed: $backupFileName")
                }

                if (driveDue) {
                    val storedEmail = settings.driveAccountEmailFlow.first().takeIf { it.isNotBlank() }
                    val connection = try {
                        GoogleDriveHelper.authorizeWithoutUi(context, storedEmail)
                    } catch (e: Exception) {
                        Log.e(TAG, "Drive authorization refresh failed", e)
                        null
                    }
                    if (connection != null) {
                        settings.saveDriveIdentity(connection.accountId, connection.email)
                        val meta = BackupPackageManager.createEncryptedBackupPackage(
                            context = context,
                            destinationFile = tempEncryptedFile,
                            accountIdOrPassphrase = connection.accountId
                        )
                        if (meta != null && tempEncryptedFile.exists()) {
                            val driveService = GoogleDriveHelper.getDriveService(connection)
                            val retentionCount = settings.driveRetentionCountFlow.first()
                            val uploadResult = GoogleDriveBackupManager.uploadEncryptedBackup(
                                driveService = driveService,
                                localEncryptedFile = tempEncryptedFile,
                                isAutoBackup = true,
                                retentionCount = retentionCount
                            )
                            if (uploadResult.isSuccess) {
                                completedAt = System.currentTimeMillis()
                                settings.saveLastDriveBackupTimestamp(completedAt)
                                Log.i(TAG, "Google Drive automatic encrypted backup completed: ${uploadResult.fileId}")
                            } else {
                                driveFailed = true
                                Log.e(TAG, "Google Drive upload failed: ${uploadResult.message}")
                            }
                        } else {
                            driveFailed = true
                            Log.e(TAG, "Drive backup package could not be created")
                        }
                    } else {
                        driveFailed = true
                        driveNeedsUserAuthorization = true
                        Log.w(TAG, "Drive backup requires the user to reconnect Google Drive")
                    }
                }

                if (!driveFailed && !driveNeedsUserAuthorization) {
                    completedAt?.let { settings.saveLastBackupTimestamp(it) }
                }
                when {
                    driveNeedsUserAuthorization -> Result.retry()
                    driveFailed -> Result.retry()
                    else -> Result.success()
                }
            } finally {
                tempFile.delete()
                tempEncryptedFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Automatic backup failed", e)
            Result.retry()
        }
    }

    private fun saveLocalCopies(context: Context, source: File, fileName: String) {
        // Private internal copy is the guaranteed recovery source.
        val internalDir = File(context.filesDir, BACKUP_FOLDER).apply {
            check(exists() || mkdirs()) { "Could not create internal backup directory" }
        }
        source.copyTo(File(internalDir, fileName), overwrite = true)
        source.copyTo(File(internalDir, LATEST_FILE), overwrite = true)
        pruneFiles(
            internalDir.listFiles { _, name ->
                name.startsWith("TechyfiPOS_AutoBackup_2") && name.endsWith(".json")
            }
        )

        // App-specific external storage remains available to the app without
        // storage permission, even when the UI process is not running.
        context.getExternalFilesDir(null)?.let { root ->
            val externalDir = File(root, BACKUP_FOLDER)
            if (externalDir.exists() || externalDir.mkdirs()) {
                source.copyTo(File(externalDir, fileName), overwrite = true)
                pruneFiles(externalDir.listFiles { _, name -> name.endsWith(".json") })
            }
        }

        // Automatic JSON backups contain sales and customer data, so they intentionally remain
        // in app-private storage. Users can explicitly export a shareable copy from Settings.
    }

    private fun pruneFiles(files: Array<File>?) {
        files.orEmpty()
            .sortedBy { it.lastModified() }
            .take((files.orEmpty().size - MAX_BACKUPS).coerceAtLeast(0))
            .forEach { it.delete() }
    }

    private fun isDue(now: Long, lastBackup: Long, frequency: String): Boolean {
        if (lastBackup <= 0L) return true
        val intervalDays = when (frequency.uppercase(Locale.US)) {
            "WEEKLY" -> 7L
            "MONTHLY" -> 30L
            else -> 1L
        }
        return now - lastBackup >= TimeUnit.DAYS.toMillis(intervalDays)
    }

    private companion object {
        const val TAG = "AutoBackupWorker"
        const val BACKUP_FOLDER = "AutoBackups"
        const val LATEST_FILE = "TechyfiPOS_AutoBackup_latest.json"
        const val MAX_BACKUPS = 15
    }
}
