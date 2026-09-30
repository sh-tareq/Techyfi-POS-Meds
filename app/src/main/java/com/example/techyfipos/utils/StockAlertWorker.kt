package com.example.techyfipos.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.techyfipos.MainActivity
import com.example.techyfipos.R
import com.example.techyfipos.data.db.AppDatabase

class StockAlertWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "StockAlertWorker"
        const val CHANNEL_ID = "techyfi_stock_alerts"
        const val CHANNEL_NAME = "Inventory & Expiry Alerts"
        const val NOTIFICATION_ID = 20260902
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val db = AppDatabase.getDatabase(context)
            val products = db.productDao().getAllProductsList()

            val now = System.currentTimeMillis()
            val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000

            val lowStockItems = products.filter { it.stockQty <= it.reorderLevel }
            val expiringItems = products.filter { p ->
                val exp = parseExpiryDateToMillis(p.expiryDate)
                exp != null && exp > 0 && exp <= (now + sevenDaysMillis)
            }

            if (lowStockItems.isNotEmpty() || expiringItems.isNotEmpty()) {
                sendStockNotification(
                    context = context,
                    lowStockCount = lowStockItems.size,
                    expiringCount = expiringItems.size,
                    sampleProduct = (lowStockItems + expiringItems).firstOrNull()?.name ?: "Products"
                )
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun sendStockNotification(
        context: Context,
        lowStockCount: Int,
        expiringCount: Int,
        sampleProduct: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_stock_channel_desc)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "products")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ Techyfi POS: Inventory Alert"
        val body = buildString {
            if (lowStockCount > 0) {
                append("$lowStockCount item(s) are low in stock")
            }
            if (expiringCount > 0) {
                if (isNotEmpty()) append(", ")
                append("$expiringCount item(s) are expiring soon")
            }
            append(" (e.g. $sampleProduct). Tap to restock.")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
