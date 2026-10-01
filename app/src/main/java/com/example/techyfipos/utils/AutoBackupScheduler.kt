package com.example.techyfipos.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.example.techyfipos.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AutoBackupScheduler {

    const val WORK_LOCAL_BACKUP = "LocalAutoBackupWork"
    const val WORK_DRIVE_BACKUP = "AutoBackupWork"
    const val WORK_ONETIME_BACKUP = "OneTimeAutoBackupWork"
    const val INPUT_BACKUP_SCOPE = "backup_scope"
    const val INPUT_RESPECT_DUE_TIME = "respect_due_time"
    const val SCOPE_LOCAL = "local"
    const val SCOPE_DRIVE = "drive"
    const val SCOPE_ALL = "all"

    fun scheduleAllAutoBackups(context: Context, forceUpdate: Boolean = false) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val settingsDataStore = SettingsDataStore(context)
                val localEnabled = settingsDataStore.localAutoBackupFlow.first()
                val localFreq = settingsDataStore.autoBackupFrequencyFlow.first()

                val driveEnabled = settingsDataStore.autoBackupFlow.first()
                val driveFreq = settingsDataStore.driveAutoBackupFrequencyFlow.first()
                val driveNetPolicy = settingsDataStore.driveNetworkPolicyFlow.first()

                scheduleLocalBackup(context, localEnabled, localFreq, forceUpdate)
                scheduleDriveBackup(context, driveEnabled, driveFreq, driveNetPolicy, forceUpdate)
                scheduleAlarmManagerBackup(context, localEnabled || driveEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scheduleLocalBackup(context: Context, enabled: Boolean, frequency: String, forceUpdate: Boolean = false) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val repeatDays = when (frequency.uppercase()) {
                "WEEKLY" -> 7L
                "MONTHLY" -> 30L
                else -> 1L
            }
            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(repeatDays, TimeUnit.DAYS)
                .setInputData(workDataOf(INPUT_BACKUP_SCOPE to SCOPE_LOCAL))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
            workManager.enqueueUniquePeriodicWork(
                WORK_LOCAL_BACKUP,
                policy,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(WORK_LOCAL_BACKUP)
        }
    }

    fun scheduleDriveBackup(
        context: Context,
        enabled: Boolean,
        frequency: String,
        networkPolicy: String = "WIFI_ONLY",
        forceUpdate: Boolean = false
    ) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val repeatDays = when (frequency.uppercase()) {
                "WEEKLY" -> 7L
                "MONTHLY" -> 30L
                else -> 1L
            }
            val netType = if (networkPolicy.uppercase() == "ANY") {
                NetworkType.CONNECTED
            } else {
                NetworkType.UNMETERED
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(netType)
                .setRequiresBatteryNotLow(true)
                .build()
            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(repeatDays, TimeUnit.DAYS)
                .setInputData(workDataOf(INPUT_BACKUP_SCOPE to SCOPE_DRIVE))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
            workManager.enqueueUniquePeriodicWork(
                WORK_DRIVE_BACKUP,
                policy,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork(WORK_DRIVE_BACKUP)
        }
    }

    fun triggerOneTimeBackup(
        context: Context,
        scope: String = SCOPE_ALL,
        respectDueTime: Boolean = false
    ) {
        val workManager = WorkManager.getInstance(context)
        val constraintsBuilder = Constraints.Builder()
        if (scope == SCOPE_DRIVE) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        val workRequest = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInputData(
                workDataOf(
                    INPUT_BACKUP_SCOPE to scope,
                    INPUT_RESPECT_DUE_TIME to respectDueTime
                )
            )
            .setConstraints(constraintsBuilder.build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(
            WORK_ONETIME_BACKUP,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun scheduleAlarmManagerBackup(context: Context, enabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AutoBackupReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (enabled) {
            val interval = AlarmManager.INTERVAL_DAY
            val triggerAt = System.currentTimeMillis() + interval
            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    interval,
                    pendingIntent
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun performImmediateBackupIfDue(context: Context, force: Boolean = false) {
        // Enqueue synchronously. The worker performs the asynchronous DataStore
        // reads and due-time check after WorkManager has durably persisted the
        // request, so closing the app cannot kill the scheduling coroutine.
        triggerOneTimeBackup(
            context = context.applicationContext,
            scope = SCOPE_ALL,
            respectDueTime = !force
        )
    }
}
