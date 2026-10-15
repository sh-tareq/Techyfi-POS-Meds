package com.example.techyfipos.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.techyfipos.R
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
            val chainStartTime = chainStartTimes.getOrPut(id) { now }
            val lastLocalBackup = settings.lastLocalBackupTimestampFlow.first()
            val localDue = shouldRunLocalBackup(
                localRequested = localRequested,
                runAttemptCount = runAttemptCount,
                lastLocalBackupTimestamp = lastLocalBackup,
                chainStartTime = chainStartTime,
                respectDueTime = respectDueTime,
                now = now,
                frequency = localFrequency
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
                chainStartTimes.remove(id)
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
                val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
                val backupFileName = "TechyfiPOS_AutoBackup_$timeString.json"
                var completedAt: Long? = null
                var driveFailed = false
                var driveNeedsUserAuthorization = false

                if (localDue) {
                    val exported = repository.exportBackupForSystem(tempFile)
                    if (!exported || !tempFile.exists() || tempFile.length() == 0L) {
                        Log.e(TAG, "Backup export failed or produced an empty file")
                        return Result.retry()
                    }
                    saveLocalCopies(context, tempFile, backupFileName)
                    completedAt = System.currentTimeMillis()
                    settings.saveLastLocalBackupTimestamp(completedAt)
                    Log.i(TAG, "Local automatic backup completed: $backupFileName")
                }

                if (driveDue) {
                    val storedEmail = settings.driveAccountEmailFlow.first().takeIf { it.isNotBlank() }
                    var authNetworkError = false
                    val connection = try {
                        val authClient = GoogleDriveHelper.getAuthorizationClient(context)
                        val authRequest = GoogleDriveHelper.buildAuthorizationRequest(storedEmail)
                        val authResult = com.google.android.gms.tasks.Tasks.await(authClient.authorize(authRequest))
                        if (authResult.hasResolution()) {
                            driveNeedsUserAuthorization = true
                            null
                        } else {
                            GoogleDriveHelper.resolveConnection(authResult)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Drive authorization refresh failed", e)
                        if (isNetworkOrTimeoutException(e)) {
                            authNetworkError = true
                        } else {
                            driveNeedsUserAuthorization = true
                        }
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
                        if (authNetworkError) {
                            Log.w(TAG, "Drive backup skipped due to network connectivity; will retry")
                            return Result.retry()
                        } else {
                            Log.w(TAG, "Drive backup requires user authorization")
                        }
                    }
                }

                if (!driveFailed && !driveNeedsUserAuthorization) {
                    completedAt?.let { settings.saveLastBackupTimestamp(it) }
                }
                val localizedContext = runCatching {
                    val lang = settings.languageFlow.first()
                    val locale = Locale.forLanguageTag(lang)
                    val config = android.content.res.Configuration(context.resources.configuration).apply {
                        setLocale(locale)
                    }
                    context.createConfigurationContext(config)
                }.getOrDefault(context)

                when {
                    driveNeedsUserAuthorization -> {
                        chainStartTimes.remove(id)
                        notifyBackupFailure(
                            context = localizedContext,
                            title = localizedContext.getString(R.string.notif_drive_reconnect_title),
                            message = localizedContext.getString(R.string.notif_drive_reconnect_desc)
                        )
                        Result.failure()
                    }
                    driveFailed -> {
                        notifyBackupFailure(
                            context = localizedContext,
                            title = localizedContext.getString(R.string.notif_drive_backup_failed_title),
                            message = localizedContext.getString(R.string.notif_drive_backup_failed_desc)
                        )
                        Result.retry()
                    }
                    else -> {
                        chainStartTimes.remove(id)
                        Result.success()
                    }
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

        // Ensure external storage does not leak plaintext customer and financial JSON records.
        // Clean up any legacy unencrypted backup files from external storage.
        context.getExternalFilesDir(null)?.let { root ->
            val externalDir = File(root, BACKUP_FOLDER)
            if (externalDir.exists()) {
                externalDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { it.delete() }
            }
        }

        // Automatic JSON backups contain sensitive customer dues, sales, and profit data.
        // They remain securely protected in app-private internal storage (context.filesDir).
        // Users can explicitly export a shareable backup copy from Settings when needed.
    }

    private fun pruneFiles(files: Array<File>?) {
        files.orEmpty()
            .sortedBy { it.lastModified() }
            .take((files.orEmpty().size - MAX_BACKUPS).coerceAtLeast(0))
            .forEach { it.delete() }
    }

    private fun notifyBackupFailure(context: Context, title: String, message: String) {
        try {
            val channelId = "techyfi_backup_alerts"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    context.getString(R.string.notif_backup_channel_name),
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_drive_channel_desc)
                }
                notificationManager.createNotificationChannel(channel)
            }
            val intent = android.content.Intent(context, com.example.techyfipos.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(1099, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post backup alert notification", e)
        }
    }

    companion object {
        const val TAG = "AutoBackupWorker"
        const val BACKUP_FOLDER = "AutoBackups"
        const val LATEST_FILE = "TechyfiPOS_AutoBackup_latest.json"
        const val MAX_BACKUPS = 15

        val chainStartTimes = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long>()

        fun isNetworkOrTimeoutException(e: Throwable): Boolean {
            val root = (e as? java.util.concurrent.ExecutionException)?.cause ?: e
            val isApiExceptionNetwork = (root as? com.google.android.gms.common.api.ApiException)?.statusCode in setOf(
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR,
                com.google.android.gms.common.api.CommonStatusCodes.TIMEOUT
            )
            return root is java.io.IOException || root.cause is java.io.IOException || isApiExceptionNetwork
        }

        fun shouldRunLocalBackup(
            localRequested: Boolean,
            runAttemptCount: Int,
            lastLocalBackupTimestamp: Long,
            chainStartTime: Long,
            respectDueTime: Boolean,
            now: Long,
            frequency: String
        ): Boolean {
            if (!localRequested) return false
            val localAlreadyDone = runAttemptCount > 0 && lastLocalBackupTimestamp >= chainStartTime
            if (localAlreadyDone) return false
            return !respectDueTime || isDue(now, lastLocalBackupTimestamp, frequency)
        }

        fun isDue(now: Long, lastBackup: Long, frequency: String): Boolean {
            if (lastBackup <= 0L) return true
            val intervalDays = when (frequency.uppercase(Locale.US)) {
                "WEEKLY" -> 7L
                "MONTHLY" -> 30L
                else -> 1L
            }
            return now - lastBackup >= TimeUnit.DAYS.toMillis(intervalDays)
        }
    }
}
