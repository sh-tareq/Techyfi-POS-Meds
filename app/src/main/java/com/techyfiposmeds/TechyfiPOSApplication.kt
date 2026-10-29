package com.techyfiposmeds

import android.app.Application
import com.techyfiposmeds.data.DataRepository
import com.techyfiposmeds.data.SettingsDataStore
import com.techyfiposmeds.data.db.AppDatabase

import com.techyfiposmeds.utils.AutoBackupScheduler
import com.techyfiposmeds.utils.CrashLogger
import com.techyfiposmeds.utils.StockAlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TechyfiPOSApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: DataRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        val database = AppDatabase.getDatabase(this)
        val settingsDataStore = SettingsDataStore(this)
        repository = DataRepository(this, database, settingsDataStore)
        applicationScope.launch {
            try {
                val savedLang = settingsDataStore.languageFlow.first()
                com.techyfiposmeds.utils.setAppLanguage(savedLang)
            } catch (_: Throwable) {}
            repository.removeLegacyDemoDuesIfUntouched()
            repository.backfillLegacyTaxInclusiveIfConfigured()
            repository.recomputeCustomerLoyaltyTiersWithConfiguredThresholds()
            com.techyfiposmeds.utils.cleanOldPrintCacheFiles(this@TechyfiPOSApplication)
        }

        // Ensure periodic work exists.
        AutoBackupScheduler.scheduleAllAutoBackups(this)
        StockAlertScheduler.scheduleStockAlerts(this)
    }

    /** Rebinds every DAO after a full database-file restore. The activity must be recreated next. */
    @Synchronized
    fun reloadRepositoryAfterDatabaseRestore() {
        repository.setSessionUser(null)
        repository = DataRepository(this, AppDatabase.getDatabase(this), SettingsDataStore(this))
    }
}
