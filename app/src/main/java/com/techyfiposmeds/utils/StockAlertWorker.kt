package com.techyfiposmeds.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.techyfiposmeds.MainActivity
import com.techyfiposmeds.R
import com.techyfiposmeds.data.db.AppDatabase
import kotlinx.coroutines.flow.first

class StockAlertWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "StockAlertWorker"
        const val CHANNEL_ID = "techyfi_stock_alerts"
        const val CHANNEL_NAME = "Inventory & Expiry Alerts"
        const val NOTIFICATION_ID = 20260902

        fun filterAlertItems(
            products: List<com.techyfiposmeds.data.db.Product>,
            nowMillis: Long,
            thirtyDaysMillis: Long = 30L * 24 * 60 * 60 * 1000L
        ): Pair<List<com.techyfiposmeds.data.db.Product>, List<com.techyfiposmeds.data.db.Product>> {
            val lowStockItems = products.filter { it.stockQty <= it.reorderLevel }
            val expiringItems = products.filter { p ->
                val exp = parseExpiryDateToMillis(p.expiryDate)
                exp != null && exp <= (nowMillis + thirtyDaysMillis)
            }
            return Pair(lowStockItems, expiringItems)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val settings = com.techyfiposmeds.data.SettingsDataStore(context)
            val localizedContext = runCatching {
                val lang = settings.languageFlow.first()
                val locale = java.util.Locale.forLanguageTag(lang)
                val config = android.content.res.Configuration(context.resources.configuration).apply {
                    setLocale(locale)
                }
                context.createConfigurationContext(config)
            }.getOrDefault(context)

            val db = AppDatabase.getDatabase(context)
            val products = db.productDao().getAllProductsList()

            val now = System.currentTimeMillis()
            val (lowStockItems, expiringItems) = filterAlertItems(products, now)

            if (lowStockItems.isNotEmpty() || expiringItems.isNotEmpty()) {
                val alertSignature = "${lowStockItems.map { it.barcode }.sorted()}_${expiringItems.map { it.barcode }.sorted()}"
                val prefs = context.getSharedPreferences("stock_alert_worker_prefs", Context.MODE_PRIVATE)
                val lastSignature = prefs.getString("last_alert_signature", null)
                if (alertSignature != lastSignature) {
                    val fallbackSample = localizedContext.getString(R.string.notif_stock_fallback_sample_product)
                    sendStockNotification(
                        context = localizedContext,
                        lowStockCount = lowStockItems.size,
                        expiringCount = expiringItems.size,
                        sampleProduct = (lowStockItems + expiringItems).firstOrNull()?.name ?: fallbackSample
                    )
                    prefs.edit().putString("last_alert_signature", alertSignature).apply()
                }
            } else {
                val prefs = context.getSharedPreferences("stock_alert_worker_prefs", Context.MODE_PRIVATE)
                prefs.edit().remove("last_alert_signature").apply()
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
                context.getString(R.string.notif_stock_channel_name),
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

        val title = context.getString(R.string.notif_stock_alert_title)
        val body = buildString {
            if (lowStockCount > 0) {
                append(context.getString(R.string.notif_stock_low_stock_fmt, lowStockCount))
            }
            if (expiringCount > 0) {
                if (isNotEmpty()) append(", ")
                append(context.getString(R.string.notif_stock_expiring_soon_fmt, expiringCount))
            }
            val displaySample = sampleProduct.ifBlank { context.getString(R.string.notif_stock_fallback_sample_product) }
            append(context.getString(R.string.notif_stock_sample_tap_fmt, displaySample))
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
