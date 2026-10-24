package com.techyfiposmeds.utils

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object StockAlertScheduler {

    const val WORK_STOCK_ALERT = "StockAlertPeriodicWork"

    fun scheduleStockAlerts(context: Context, forceUpdate: Boolean = false) {
        try {
            val workRequest = PeriodicWorkRequestBuilder<StockAlertWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_STOCK_ALERT,
                policy,
                workRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
