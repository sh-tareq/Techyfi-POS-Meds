package com.example.techyfipos.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DueReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DueReminderAlarmReceiver"
        const val ACTION_DUE_REMINDER_ALARM = "com.example.techyfipos.ACTION_DUE_REMINDER_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DUE_REMINDER_ALARM) {
            Log.w(TAG, "Ignoring unexpected broadcast action: ${intent.action}")
            return
        }
        val customerName = intent.getStringExtra(DueReminderWorker.KEY_CUSTOMER_NAME) ?: "Customer"
        val customerPhone = intent.getStringExtra(DueReminderWorker.KEY_CUSTOMER_PHONE) ?: ""
        val dueAmount = intent.getDoubleExtra(DueReminderWorker.KEY_DUE_AMOUNT, 0.0)
        val messageText = intent.getStringExtra(DueReminderWorker.KEY_MESSAGE_TEXT) ?: ""
        val channel = intent.getStringExtra(DueReminderWorker.KEY_DELIVERY_CHANNEL) ?: "both"
        val isRecurring = intent.getBooleanExtra(DueReminderWorker.KEY_IS_RECURRING, false)

        Log.i(TAG, "AlarmManager triggered due reminder for $customerName ($customerPhone), channel=$channel")

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DueReminderScheduler.executeReminder(
                    context = context.applicationContext,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    scheduledDueAmount = dueAmount,
                    scheduledMessageText = messageText,
                    channel = channel,
                    isRecurring = isRecurring
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error executing AlarmManager reminder: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
