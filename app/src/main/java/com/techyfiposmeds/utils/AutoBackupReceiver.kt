package com.techyfiposmeds.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoBackupScheduler.triggerOneTimeBackup(
            context = context,
            scope = AutoBackupScheduler.SCOPE_ALL,
            respectDueTime = true
        )
    }
}
