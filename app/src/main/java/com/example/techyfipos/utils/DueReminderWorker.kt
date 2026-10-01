package com.example.techyfipos.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DueReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "DueReminderWorker"
        const val CHANNEL_ID = DueReminderScheduler.CHANNEL_ID

        const val KEY_CUSTOMER_NAME = "customer_name"
        const val KEY_CUSTOMER_PHONE = "customer_phone"
        const val KEY_DUE_AMOUNT = "due_amount"
        const val KEY_MESSAGE_TEXT = "message_text"
        const val KEY_DELIVERY_CHANNEL = "delivery_channel" // "wa", "sms", "both"
        const val KEY_IS_RECURRING = "is_recurring"
        const val RECURRING_INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    override suspend fun doWork(): Result {
        val customerName = inputData.getString(KEY_CUSTOMER_NAME) ?: "Customer"
        val customerPhone = inputData.getString(KEY_CUSTOMER_PHONE) ?: ""
        val scheduledDueAmount = inputData.getDouble(KEY_DUE_AMOUNT, 0.0)
        val scheduledMessageText = inputData.getString(KEY_MESSAGE_TEXT) ?: ""
        val channel = inputData.getString(KEY_DELIVERY_CHANNEL) ?: "both"
        val isRecurring = inputData.getBoolean(KEY_IS_RECURRING, false)

        val success = DueReminderScheduler.executeReminder(
            context = applicationContext,
            customerName = customerName,
            customerPhone = customerPhone,
            scheduledDueAmount = scheduledDueAmount,
            scheduledMessageText = scheduledMessageText,
            channel = channel,
            isRecurring = isRecurring
        )

        return if (success) Result.success() else Result.failure()
    }
}
