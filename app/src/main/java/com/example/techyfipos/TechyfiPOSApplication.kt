package com.example.techyfipos

import android.app.Application
import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.data.db.AppDatabase

import com.example.techyfipos.utils.AutoBackupScheduler
import com.example.techyfipos.utils.CrashLogger
import com.example.techyfipos.utils.StockAlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        applicationScope.launch { repository.removeLegacyDemoDuesIfUntouched() }

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
