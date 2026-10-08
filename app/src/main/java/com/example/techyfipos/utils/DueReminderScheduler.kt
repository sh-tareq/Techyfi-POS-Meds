package com.example.techyfipos.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.example.techyfipos.MainActivity
import com.example.techyfipos.R
import com.example.techyfipos.data.db.AppDatabase
import com.example.techyfipos.data.db.DueReminderLog
import com.example.techyfipos.data.db.ScheduledDueReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DueReminderScheduler {
    private const val TAG = "DueReminderScheduler"
    const val CHANNEL_ID = "due_reminders_channel"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cancel() {
        scope.cancel()
    }

    enum class SchedulePreset(val titleBn: String, val titleEn: String) {
        TOMORROW_10AM("আগামীকাল ১০টা", "Tomorrow 10 AM"),
        IN_3_DAYS("৩ দিন পর", "In 3 Days"),
        WEEKLY("সাপ্তাহিক", "Weekly"),
        CUSTOM("কাস্টম", "Custom")
    }

    fun calculateDelayForPreset(
        preset: SchedulePreset,
        customHours: Int = 12,
        customTargetEpochMillis: Long? = null
    ): Long {
        val now = Calendar.getInstance()
        return when (preset) {
            SchedulePreset.TOMORROW_10AM -> {
                val target = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 10)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val delay = target.timeInMillis - now.timeInMillis
                if (delay > 0) delay else TimeUnit.HOURS.toMillis(24)
            }
            SchedulePreset.IN_3_DAYS -> {
                val target = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 3)
                    set(Calendar.HOUR_OF_DAY, 11)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val delay = target.timeInMillis - now.timeInMillis
                if (delay > 0) delay else TimeUnit.DAYS.toMillis(3)
            }
            SchedulePreset.WEEKLY -> {
                val target = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 7)
                    set(Calendar.HOUR_OF_DAY, 10)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val delay = target.timeInMillis - now.timeInMillis
                if (delay > 0) delay else TimeUnit.DAYS.toMillis(7)
            }
            SchedulePreset.CUSTOM -> {
                if (customTargetEpochMillis != null) {
                    val delay = customTargetEpochMillis - System.currentTimeMillis()
                    if (delay > 0) delay else 60_000L // Minimum 1 min safe delay
                } else {
                    TimeUnit.HOURS.toMillis(customHours.coerceIn(1, 24 * 30).toLong())
                }
            }
        }
    }

    fun scheduleDueReminder(
        context: Context,
        customerName: String,
        customerPhone: String,
        dueAmount: Double,
        messageText: String,
        channel: String, // "wa", "sms", "both"
        delayMillis: Long,
        isRecurring: Boolean = false
    ) {
        val normalizedPhone = normalizePhone(customerPhone)
        val safeDelay = if (delayMillis <= 0) 5000L else delayMillis
        val triggerAtMillis = System.currentTimeMillis() + safeDelay

        // 1. Schedule AlarmManager exact alarm (guarantees wakeup when app is closed / asleep in Doze mode)
        scheduleAlarmManagerReminder(
            context = context,
            customerName = customerName,
            customerPhone = normalizedPhone,
            dueAmount = dueAmount,
            messageText = messageText,
            channel = channel,
            triggerAtMillis = triggerAtMillis,
            isRecurring = isRecurring
        )

        // 2. Cancel any legacy WorkManager periodic/one-time reminder for this customer so AlarmManager is the sole scheduler
        try {
            WorkManager.getInstance(context).cancelUniqueWork("due_reminder_$normalizedPhone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel legacy WorkManager work: ${e.message}")
        }

        // 3. Persist in database
        val now = System.currentTimeMillis()
        scope.launch {
            try {
                AppDatabase.getDatabase(context).scheduledDueReminderDao().upsert(
                    ScheduledDueReminder(
                        customerPhone = normalizedPhone,
                        customerName = customerName,
                        dueAmount = dueAmount,
                        channel = channel,
                        scheduledAt = now,
                        fireAt = triggerAtMillis,
                        messageText = messageText,
                        isRecurring = isRecurring
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist scheduled reminder tracking row: ${e.message}")
            }
        }

        Log.i(TAG, "Scheduled due reminder for $customerName with delay ${safeDelay / 1000}s (at $triggerAtMillis)")
    }

    private fun scheduleAlarmManagerReminder(
        context: Context,
        customerName: String,
        customerPhone: String,
        dueAmount: Double,
        messageText: String,
        channel: String,
        triggerAtMillis: Long,
        isRecurring: Boolean
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DueReminderAlarmReceiver::class.java).apply {
            action = DueReminderAlarmReceiver.ACTION_DUE_REMINDER_ALARM
            putExtra(DueReminderWorker.KEY_CUSTOMER_NAME, customerName)
            putExtra(DueReminderWorker.KEY_CUSTOMER_PHONE, customerPhone)
            putExtra(DueReminderWorker.KEY_DUE_AMOUNT, dueAmount)
            putExtra(DueReminderWorker.KEY_MESSAGE_TEXT, messageText)
            putExtra(DueReminderWorker.KEY_DELIVERY_CHANNEL, channel)
            putExtra(DueReminderWorker.KEY_IS_RECURRING, isRecurring)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        val requestCode = (customerPhone.hashCode() and 0x7FFFFFFF)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.i(TAG, "AlarmManager setExactAndAllowWhileIdle scheduled for $customerName at $triggerAtMillis")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission restricted, falling back to setAndAllowWhileIdle: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule AlarmManager fallback: ${ex.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule AlarmManager reminder: ${e.message}")
        }
    }


    fun cancelDueReminder(context: Context, customerPhone: String) {
        val normalizedPhone = normalizePhone(customerPhone)
        val uniqueWorkName = "due_reminder_$normalizedPhone"

        // Cancel WorkManager
        try {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel work: ${e.message}")
        }

        // Cancel AlarmManager
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val intent = Intent(context, DueReminderAlarmReceiver::class.java).apply {
                    action = DueReminderAlarmReceiver.ACTION_DUE_REMINDER_ALARM
                }
                val requestCode = (normalizedPhone.hashCode() and 0x7FFFFFFF)
                val flags = PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel AlarmManager alarm: ${e.message}")
        }

        // Delete from Room
        scope.launch {
            try {
                AppDatabase.getDatabase(context).scheduledDueReminderDao().deleteByPhone(normalizedPhone)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear scheduled reminder tracking row: ${e.message}")
            }
        }
        Log.i(TAG, "Cancelled due reminder for $uniqueWorkName")
    }

    suspend fun reschedulePersistedReminders(context: Context) {
        val reminders = AppDatabase.getDatabase(context).scheduledDueReminderDao().getAllOnce()
        val now = System.currentTimeMillis()
        reminders.forEach { reminder ->
            if (!reminder.isRecurring && reminder.fireAt < now) {
                try {
                    AppDatabase.getDatabase(context).dueReminderLogDao().insertLog(
                        com.example.techyfipos.data.db.DueReminderLog(
                            customerPhone = reminder.customerPhone,
                            customerName = reminder.customerName,
                            dueAmount = reminder.dueAmount,
                            messageText = reminder.messageText,
                            channel = reminder.channel,
                            status = "MISSED",
                            timestamp = reminder.fireAt
                        )
                    )
                    showReminderNotification(
                        context = context,
                        customerName = reminder.customerName,
                        customerPhone = reminder.customerPhone,
                        dueAmount = reminder.dueAmount,
                        messageText = reminder.messageText,
                        channel = reminder.channel
                    )
                    AppDatabase.getDatabase(context).scheduledDueReminderDao().deleteByPhone(reminder.customerPhone)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cleanup expired one-shot reminder: ${e.message}")
                }
                return@forEach
            }
            val remainingDelay = (reminder.fireAt - now).coerceAtLeast(3_000L)
            scheduleDueReminder(
                context = context,
                customerName = reminder.customerName,
                customerPhone = reminder.customerPhone,
                dueAmount = reminder.dueAmount,
                messageText = reminder.messageText,
                channel = reminder.channel,
                delayMillis = remainingDelay,
                isRecurring = reminder.isRecurring
            )
        }
        Log.i(TAG, "Rescheduled ${reminders.size} pending due reminders after boot/update")
    }

    suspend fun executeReminder(
        context: Context,
        customerName: String,
        customerPhone: String,
        scheduledDueAmount: Double,
        scheduledMessageText: String,
        channel: String,
        isRecurring: Boolean
    ): Boolean {
        val normalizedPhone = normalizePhone(customerPhone)

        // Verify if the due is still outstanding
        val liveDue = if (normalizedPhone.isNotBlank()) {
            try {
                AppDatabase.getDatabase(context).customerDueDao().getCustomerDueByPhone(normalizedPhone)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-fetch current due for $normalizedPhone: ${e.message}")
                null
            }
        } else null

        if (normalizedPhone.isNotBlank() && (liveDue == null || liveDue.outstandingAmount <= 0.0)) {
            Log.i(TAG, "Skipping reminder for $customerName ($normalizedPhone): due is settled or removed")
            cancelDueReminder(context, normalizedPhone)
            return true
        }

        val currentDue = liveDue?.outstandingAmount
        val dueAmount = currentDue ?: scheduledDueAmount
        val messageText = if (currentDue != null && currentDue != scheduledDueAmount) {
            scheduledMessageText.replace(formatTaka(scheduledDueAmount), formatTaka(currentDue))
        } else {
            scheduledMessageText
        }

        Log.i(TAG, "Executing due reminder for $customerName ($normalizedPhone), channel=$channel")

        val notificationShown = showReminderNotification(
            context = context,
            customerName = customerName,
            customerPhone = normalizedPhone,
            dueAmount = dueAmount,
            messageText = messageText,
            channel = channel
        )

        // Log the reminder
        try {
            val status = "NOTIFIED"
            val channelName = when (channel) {
                "sms" -> "SMS"
                "wa" -> "WHATSAPP"
                else -> "BOTH"
            }
            AppDatabase.getDatabase(context).dueReminderLogDao().insertLog(
                DueReminderLog(
                    customerPhone = normalizedPhone,
                    customerName = customerName,
                    dueAmount = dueAmount,
                    channel = channelName,
                    messageText = messageText,
                    status = status,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert reminder log: ${e.message}")
        }

        // Update or reschedule recurring reminder
        if (normalizedPhone.isNotBlank()) {
            try {
                val dao = AppDatabase.getDatabase(context).scheduledDueReminderDao()
                if (isRecurring) {
                    val nextFireAt = System.currentTimeMillis() + DueReminderWorker.RECURRING_INTERVAL_MILLIS
                    dao.upsert(
                        ScheduledDueReminder(
                            customerPhone = normalizedPhone,
                            customerName = customerName,
                            dueAmount = dueAmount,
                            channel = channel,
                            scheduledAt = System.currentTimeMillis(),
                            fireAt = nextFireAt,
                            messageText = messageText,
                            isRecurring = true
                        )
                    )
                    // Schedule next alarm
                    scheduleAlarmManagerReminder(
                        context = context,
                        customerName = customerName,
                        customerPhone = normalizedPhone,
                        dueAmount = dueAmount,
                        messageText = messageText,
                        channel = channel,
                        triggerAtMillis = nextFireAt,
                        isRecurring = true
                    )
                } else {
                    dao.deleteByPhone(normalizedPhone)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update scheduled reminder tracking row: ${e.message}")
            }
        }

        return notificationShown
    }

    private fun showReminderNotification(
        context: Context,
        customerName: String,
        customerPhone: String,
        dueAmount: Double,
        messageText: String,
        channel: String
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted; reminder notification skipped")
            return false
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.due_reminder_channel_name)
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.due_reminder_channel_description)
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val cleanPhone = customerPhone.replace(Regex("[^0-9+]"), "").let {
            if (it.startsWith("01")) "+88$it" else it
        }
        val encodedMsg = try { URLEncoder.encode(messageText, "UTF-8") } catch (e: Exception) { "" }
        val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMsg")
        val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val baseCode = customerPhone.hashCode() and 0x7FFFFFFF
        val waPendingIntent = PendingIntent.getActivity(
            context,
            baseCode,
            waIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val smsUri = Uri.parse("smsto:$cleanPhone")
        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", messageText)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val smsPendingIntent = PendingIntent.getActivity(
            context,
            baseCode + 2,
            smsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Open App Intent
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "dues")
        }
        val appPendingIntent = PendingIntent.getActivity(
            context,
            baseCode + 1,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = context.getString(R.string.due_reminder_notification_title, customerName, formatTaka(dueAmount))
        val subtitle = if (channel == "sms") {
            context.getString(R.string.due_reminder_sms_review_body, customerName)
        } else if (channel == "both") {
            context.getString(R.string.due_reminder_both_ready_body)
        } else {
            context.getString(R.string.due_reminder_wa_ready_body)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$subtitle\n\n\"$messageText\""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(appPendingIntent)
            .setAutoCancel(true)

        // Add Action Buttons
        if (channel == "sms" || channel == "both") {
            builder.addAction(
                android.R.drawable.ic_menu_send,
                context.getString(R.string.due_reminder_action_send_sms),
                smsPendingIntent
            )
        }
        if (channel == "wa" || channel == "both") {
            builder.addAction(
                android.R.drawable.ic_menu_send,
                context.getString(R.string.due_reminder_action_send_whatsapp),
                waPendingIntent
            )
        }

        val notificationId = (customerPhone.hashCode() and 0x7FFFFFFF)
        notificationManager.notify(notificationId, builder.build())
        return true
    }
}
