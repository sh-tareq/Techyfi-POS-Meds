package com.techyfiposmeds.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            AutoBackupScheduler.scheduleAllAutoBackups(
                context,
                forceUpdate = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
            )
            AutoBackupScheduler.performImmediateBackupIfDue(context)
            StockAlertScheduler.scheduleStockAlerts(context, forceUpdate = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    DueReminderScheduler.reschedulePersistedReminders(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
