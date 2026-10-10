package com.example.techyfipos.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.room.support.getSupportWrapper
import com.example.techyfipos.R
import com.example.techyfipos.TechyfiPOSApplication
import com.example.techyfipos.data.CategoryItem
import com.example.techyfipos.data.DueTemplate
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.data.QuickSalePreset
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.data.db.AppDatabase
import com.example.techyfipos.data.db.APP_DATABASE_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages atomic database snapshots, packaging with metadata/settings/assets,
 * compression, encryption, and safe restoration with automatic rollback protection.
 */
object BackupPackageManager {

    private const val TAG = "BackupPackageManager"
    private const val CURRENT_APP_VERSION = "2.6.3-2026.08.30"
    private const val CURRENT_DB_VERSION = APP_DATABASE_VERSION
    private const val BACKUP_FORMAT_VERSION = 4
    private const val DB_NAME = "techyfi_pos_db"
    private const val MAX_ROLLBACK_SNAPSHOTS = 3
    private const val MAX_ROLLBACK_AGE_MS = 7L * 24 * 60 * 60 * 1000

    data class BackupMetadata(
        val appVersion: String,
        val databaseVersion: Int,
        val backupVersion: Int,
        val createdAt: Long,
        val createdAtFormatted: String,
        val device: String,
        val storeName: String,
        val productCount: Int,
        val salesCount: Int,
        val expenseCount: Int,
        val supplierCount: Int,
        val duesCount: Int,
        val checksumSha256: String
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("appVersion", appVersion)
            put("databaseVersion", databaseVersion)
            put("backupVersion", backupVersion)
            put("createdAt", createdAt)
            put("createdAtFormatted", createdAtFormatted)
            put("device", device)
            put("storeName", storeName)
            put("productCount", productCount)
            put("salesCount", salesCount)
            put("expenseCount", expenseCount)
            put("supplierCount", supplierCount)
            put("duesCount", duesCount)
            put("checksumSha256", checksumSha256)
        }

        companion object {
            fun fromJson(json: JSONObject): BackupMetadata = BackupMetadata(
                appVersion = json.optString("appVersion", CURRENT_APP_VERSION),
                databaseVersion = json.optInt("databaseVersion", CURRENT_DB_VERSION),
                backupVersion = json.optInt("backupVersion", BACKUP_FORMAT_VERSION),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                createdAtFormatted = json.optString("createdAtFormatted", ""),
                device = json.optString("device", "Android"),
                storeName = json.optString("storeName", "Techyfi Store"),
                productCount = json.optInt("productCount", 0),
                salesCount = json.optInt("salesCount", 0),
                expenseCount = json.optInt("expenseCount", 0),
                supplierCount = json.optInt("supplierCount", 0),
                duesCount = json.optInt("duesCount", 0),
                checksumSha256 = json.optString("checksumSha256", "")
            )
        }
    }

    data class RestoreResult(
        val isSuccess: Boolean,
        val message: String,
        val restoredMetadata: BackupMetadata? = null,
        val requiresUiReload: Boolean = false
    )

    /**
     * Creates an encrypted, atomic, transactional backup archive.
     */
    suspend fun createEncryptedBackupPackage(
        context: Context,
        destinationFile: File,
        accountIdOrPassphrase: String
    ): BackupMetadata? = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "backup_staging_${System.currentTimeMillis()}").apply { mkdirs() }
        val tempZip = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}.zip")

        try {
            val db = AppDatabase.getDatabase(context)
            val settings = SettingsDataStore(context)

            // 1. Gather entity counts
            val products = try { db.productDao().getAllProducts().first() } catch (_: Exception) { emptyList() }
            val productCount = products.size
            val salesCount = try { db.saleDao().getAllSalesWithItems().first().size } catch (_: Exception) { 0 }
            val expenseCount = try { db.expenseDao().getAllExpenses().first().size } catch (_: Exception) { 0 }
            val supplierCount = try { db.supplierDao().getAllSuppliers().first().size } catch (_: Exception) { 0 }
            val duesCount = try { db.customerDueDao().getAllDues().first().size } catch (_: Exception) { 0 }

            val invoiceSettings = settings.invoiceSettingsDataFlow.first()
            val storeName = invoiceSettings.storeName.ifBlank { "Techyfi Store" }

            // 2. Ask SQLite itself for a transactionally consistent snapshot. Copying the live
            // main file after a WAL checkpoint is not safe because another sale can commit between
            // the checkpoint and file copy.
            val stagingDbFile = File(stagingDir, "$DB_NAME.sqlite")
            createConsistentDatabaseSnapshot(db, stagingDbFile)

            // 4. Export DataStore preferences to preferences.json
            val stagingPrefFile = File(stagingDir, "preferences.json")
            val prefJson = capturePreferences(settings, invoiceSettings, context)
            stagingPrefFile.writeText(prefJson.toString(2))

            // 5. Copy logo asset if available
            if (invoiceSettings.logoUri.isNotBlank()) {
                try {
                    val logoFile = File(invoiceSettings.logoUri)
                    if (logoFile.exists()) {
                        logoFile.copyTo(File(stagingDir, "shop_logo.png"), overwrite = true)
                    } else {
                        val uri = Uri.parse(invoiceSettings.logoUri)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(File(stagingDir, "shop_logo.png")).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy shop logo into backup: ${e.message}")
                }
            }

            // Product images live outside SQLite, so package the actual files instead of only
            // preserving device-specific absolute paths stored in Product.imageUri.
            val imagesDir = File(stagingDir, "product_images").apply { mkdirs() }
            val imageManifest = JSONArray()
            products.forEachIndexed { index, product ->
                val source = product.imageUri?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                try {
                    val extension = source.substringAfterLast('.', "jpg").substringBefore('?')
                        .lowercase(Locale.US).takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "jpg"
                    val archiveName = "product_${index}_${product.barcode.hashCode().toUInt()}.$extension"
                    val target = File(imagesDir, archiveName)
                    openAssetInput(context, source)?.use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    if (target.exists() && target.length() > 0L) {
                        imageManifest.put(JSONObject().apply {
                            put("barcode", product.barcode)
                            put("file", archiveName)
                        })
                    } else {
                        target.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not package image for ${product.barcode}: ${e.message}")
                }
            }
            if (imageManifest.length() > 0) {
                File(stagingDir, "product_images.json").writeText(imageManifest.toString(2))
            } else {
                imagesDir.delete()
            }

            // 6. Calculate DB checksum
            val checksum = BackupEncryptionHelper.calculateSha256(stagingDbFile)
            val now = System.currentTimeMillis()
            val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(now))

            val metadata = BackupMetadata(
                appVersion = CURRENT_APP_VERSION,
                databaseVersion = CURRENT_DB_VERSION,
                backupVersion = BACKUP_FORMAT_VERSION,
                createdAt = now,
                createdAtFormatted = dateFormatted,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                storeName = storeName,
                productCount = productCount,
                salesCount = salesCount,
                expenseCount = expenseCount,
                supplierCount = supplierCount,
                duesCount = duesCount,
                checksumSha256 = checksum
            )

            // 7. Write metadata.json into staging
            File(stagingDir, "metadata.json").writeText(metadata.toJson().toString(2))

            // 8. Compress staging folder to zip
            zipDirectory(stagingDir, tempZip)

            // 9. Encrypt with AES-256-GCM using a per-backup randomly salted key
            val encryptedOk = BackupEncryptionHelper.encryptFile(tempZip, destinationFile, accountIdOrPassphrase)

            if (!encryptedOk || !destinationFile.exists() || destinationFile.length() == 0L) {
                Log.e(TAG, "Encryption failed for backup package")
                return@withContext null
            }

            Log.i(TAG, "Backup package created successfully: ${destinationFile.name} (${destinationFile.length()} bytes)")
            metadata
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create backup package", e)
            null
        } finally {
            stagingDir.deleteRecursively()
            tempZip.delete()
        }
    }

    /**
     * Inspects an encrypted backup file and extracts metadata without altering the local database.
     */
    suspend fun readMetadataFromEncryptedPackage(
        context: Context,
        encryptedFile: File,
        accountIdOrPassphrase: String
    ): BackupMetadata? = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "inspect_temp_${System.currentTimeMillis()}.zip")
        val stagingDir = File(context.cacheDir, "inspect_staging_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            val decryptedOk = BackupEncryptionHelper.decryptFile(encryptedFile, tempZip, accountIdOrPassphrase)
            if (!decryptedOk || !tempZip.exists()) {
                Log.w(TAG, "Could not decrypt backup file for metadata inspection")
                return@withContext null
            }

            unzipFile(tempZip, stagingDir)
            val metaFile = File(stagingDir, "metadata.json")
            if (metaFile.exists()) {
                val json = JSONObject(metaFile.readText())
                BackupMetadata.fromJson(json)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting backup metadata", e)
            null
        } finally {
            stagingDir.deleteRecursively()
            tempZip.delete()
        }
    }

    /**
     * Safely restores an encrypted backup package with rollback protection.
     */
    suspend fun restoreEncryptedBackupPackage(
        context: Context,
        encryptedFile: File,
        accountIdOrPassphrase: String
    ): RestoreResult = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}.zip")
        val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }
        val rollbackDir = File(context.filesDir, "safety_rollback").apply { mkdirs() }
        val rollbackDbFile = File(rollbackDir, "pre_restore_${DB_NAME}_${System.currentTimeMillis()}.sqlite")
        val settings = SettingsDataStore(context)
        val rollbackPreferences = runCatching { capturePreferences(settings, context = context) }.getOrNull()
        val restoredLogoFile = File(context.filesDir, "restored_shop_logo.png")
        val previousRestoredLogo = restoredLogoFile.takeIf(File::isFile)?.readBytes()
        var databaseWasSwapped = false

        try {
            // 1. Decrypt archive
            val decryptedOk = BackupEncryptionHelper.decryptFile(encryptedFile, tempZip, accountIdOrPassphrase)
            if (!decryptedOk || !tempZip.exists()) {
                return@withContext RestoreResult(false, "Decryption failed. Invalid encryption key or corrupt archive.")
            }

            // 2. Unpack archive
            unzipFile(tempZip, stagingDir)
            val metaFile = File(stagingDir, "metadata.json")
            val stagingDbFile = File(stagingDir, "$DB_NAME.sqlite")

            if (!metaFile.exists() || !stagingDbFile.exists()) {
                return@withContext RestoreResult(false, "Corrupt backup package: metadata or database file is missing.")
            }

            val metadata = BackupMetadata.fromJson(JSONObject(metaFile.readText()))
            if (metadata.backupVersion > BACKUP_FORMAT_VERSION || metadata.databaseVersion > CURRENT_DB_VERSION) {
                return@withContext RestoreResult(
                    false,
                    "This backup was created by a newer Techyfi POS version. Update the app before restoring."
                )
            }

            // 3. Verify SQLite checksum
            val calculatedChecksum = BackupEncryptionHelper.calculateSha256(stagingDbFile)
            if (metadata.checksumSha256.isNotBlank() && calculatedChecksum != metadata.checksumSha256) {
                return@withContext RestoreResult(false, "Integrity check failed: database checksum mismatch.")
            }

            // 4. Create Safety Rollback snapshot of active live DB
            pruneRollbackSnapshots(rollbackDir)
            val liveDbFile = context.getDatabasePath(DB_NAME)
            if (liveDbFile.exists()) {
                // Flush WAL first; copying only the main SQLite file without this checkpoint can
                // silently omit the newest sales from the emergency rollback snapshot.
                try {
                    AppDatabase.getDatabase(context).getSupportWrapper()
                        .query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
                } catch (e: Exception) {
                    Log.w(TAG, "Pre-restore rollback checkpoint warning: ${e.message}")
                }
                liveDbFile.copyTo(rollbackDbFile, overwrite = true)
                Log.i(TAG, "Safety rollback copy saved to: ${rollbackDbFile.name}")
            }

            // 5. Close Room Database connection cleanly
            AppDatabase.closeDatabase()
            databaseWasSwapped = true

            // 6. Overwrite live DB file. Prefer an atomic rename (a single filesystem metadata
            // operation) over a streamed copy so a process kill mid-swap can never leave the
            // live database truncated; fall back to copy only if rename isn't possible (e.g. staging
            // and live files are on different filesystems).
            if (liveDbFile.exists()) liveDbFile.delete()
            if (!stagingDbFile.renameTo(liveDbFile)) {
                stagingDbFile.copyTo(liveDbFile, overwrite = true)
            }
            // Remove existing WAL/SHM files so SQLite starts in a fresh state
            context.getDatabasePath("$DB_NAME-wal").delete()
            context.getDatabasePath("$DB_NAME-shm").delete()

            // 7. Restore DataStore Preferences
            val prefFile = File(stagingDir, "preferences.json")
            if (prefFile.exists()) {
                try {
                    val prefJson = JSONObject(prefFile.readText())
                    applyPreferences(settings, prefJson, context)
                } catch (e: Exception) {
                    Log.w(TAG, "Error restoring preferences: ${e.message}")
                }
            }

            // 8. Restore Shop Logo if available
            val logoFile = File(stagingDir, "shop_logo.png")
            if (logoFile.exists()) {
                logoFile.copyTo(restoredLogoFile, overwrite = true)
                val current = settings.invoiceSettingsDataFlow.first()
                settings.saveInvoiceSettingsData(current.copy(logoUri = Uri.fromFile(restoredLogoFile).toString()))
            }

            // 9. Reopen database and verify accessibility
            val reopenedDb = AppDatabase.getDatabase(context)
            restoreProductImages(context, stagingDir, reopenedDb)
            val restoredProds = reopenedDb.productDao().getAllProducts().first().size
            DueReminderScheduler.reschedulePersistedReminders(context.applicationContext)
            AutoBackupScheduler.scheduleAllAutoBackups(context.applicationContext, forceUpdate = true)
            Log.i(TAG, "Database successfully restored. Verified $restoredProds products.")

            // The process-wide repository captured DAO instances from the database that was just
            // closed. Rebind it now; the caller recreates the Activity to rebuild all ViewModels.
            (context.applicationContext as? TechyfiPOSApplication)?.reloadRepositoryAfterDatabaseRestore()
            pruneRollbackSnapshots(rollbackDir)

            RestoreResult(
                isSuccess = true,
                message = context.getString(R.string.restore_success_summary_fmt, metadata.productCount, metadata.salesCount),
                restoredMetadata = metadata,
                requiresUiReload = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed, executing automatic rollback", e)
            // Rollback to pre-restore copy
            try {
                if (rollbackDbFile.exists()) {
                    AppDatabase.closeDatabase()
                    rollbackDbFile.copyTo(context.getDatabasePath(DB_NAME), overwrite = true)
                    context.getDatabasePath("$DB_NAME-wal").delete()
                    context.getDatabasePath("$DB_NAME-shm").delete()
                    AppDatabase.getDatabase(context)
                    (context.applicationContext as? TechyfiPOSApplication)?.reloadRepositoryAfterDatabaseRestore()
                    rollbackPreferences?.let { applyPreferences(settings, it, context) }
                    if (previousRestoredLogo == null) {
                        restoredLogoFile.delete()
                    } else {
                        restoredLogoFile.writeBytes(previousRestoredLogo)
                    }
                    Log.i(TAG, "Automatic rollback executed successfully")
                }
            } catch (rbEx: Exception) {
                Log.e(TAG, "Failed during automatic rollback", rbEx)
            }
            RestoreResult(
                false,
                "Restore failed: ${e.localizedMessage ?: "Unknown error"}. Previous database state was safely restored.",
                requiresUiReload = databaseWasSwapped
            )
        } finally {
            stagingDir.deleteRecursively()
            tempZip.delete()
        }
    }

    private fun zipDirectory(sourceDir: File, outZipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outZipFile))).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relativeName = file.relativeTo(sourceDir).invariantSeparatorsPath
                    val entry = ZipEntry(relativeName)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
            }
        }
    }

    private fun createConsistentDatabaseSnapshot(db: AppDatabase, destination: File) {
        destination.delete()
        val escapedPath = destination.absolutePath.replace("'", "''")
        db.getSupportWrapper().execSQL("VACUUM INTO '$escapedPath'")
        check(destination.isFile && destination.length() > 0L) {
            "SQLite could not create a consistent backup snapshot"
        }
    }

    private suspend fun capturePreferences(
        settings: SettingsDataStore,
        invoiceSettings: InvoiceSettingsData? = null,
        context: Context? = null
    ): JSONObject = JSONObject().apply {
        val effectiveInvoiceSettings = invoiceSettings ?: settings.invoiceSettingsDataFlow.first()
        put("invoiceSettings", effectiveInvoiceSettings.toJson())
        put("language", settings.languageFlow.first())
        put("autoBackup", settings.autoBackupFlow.first())
        put("autoBackupFrequency", settings.autoBackupFrequencyFlow.first())
        put("localAutoBackup", settings.localAutoBackupFlow.first())
        put("driveAutoBackupFrequency", settings.driveAutoBackupFrequencyFlow.first())
        put("driveRetentionCount", settings.driveRetentionCountFlow.first())
        put("driveNetworkPolicy", settings.driveNetworkPolicyFlow.first())
        put("driveBackupTime", settings.driveBackupTimeFlow.first())
        put("quickSalePresets", QuickSalePreset.listToJson(settings.quickSalePresetsFlow.first()))
        put("notePresets", JSONArray().apply { settings.notePresetsFlow.first().forEach { put(it) } })
        put("dueReminderTemplates", DueTemplate.listToJson(settings.dueReminderTemplatesFlow.first()))
        put("customCategories", CategoryItem.listToJson(settings.categoriesFlow.first()))
        settings.returnAuditPresetsFlow.first()?.let { put("returnAuditPresets", it) }
        put("themeMode", settings.themeModeFlow.first())
        put("uiLayoutMode", settings.uiLayoutModeFlow.first())
        put("defaultCostPercentage", settings.defaultCostPercentageFlow.first())
        if (context != null) {
            val loyaltySettings = LoyaltySettingsHelper.getSettings(context)
            put("loyaltyProgramSettings", JSONObject().apply {
                put("takaPerPoint", loyaltySettings.takaPerPoint)
                put("pointCashValue", loyaltySettings.pointCashValue)
                put("minRedeemPoints", loyaltySettings.minRedeemPoints)
                put("maxBillPercentPayable", loyaltySettings.maxBillPercentPayable)
                put("silverSpendThreshold", loyaltySettings.silverSpendThreshold)
                put("goldSpendThreshold", loyaltySettings.goldSpendThreshold)
                put("platinumSpendThreshold", loyaltySettings.platinumSpendThreshold)
                put("pointsExpiryDays", loyaltySettings.pointsExpiryDays)
                put("isTiersEnabled", loyaltySettings.isTiersEnabled)
            })
        }
    }

    private suspend fun applyPreferences(settings: SettingsDataStore, json: JSONObject, context: Context? = null) {
        if (json.has("invoiceSettings")) {
            settings.saveInvoiceSettingsData(InvoiceSettingsData.fromJson(json.getString("invoiceSettings")))
        }
        if (json.has("language")) settings.saveLanguage(json.getString("language"))
        if (json.has("autoBackup")) {
            settings.saveAutoBackup(
                json.optBoolean("autoBackup", false),
                json.optString("driveAutoBackupFrequency", json.optString("autoBackupFrequency", "DAILY"))
            )
        }
        if (json.has("localAutoBackup")) {
            settings.saveLocalAutoBackup(
                json.optBoolean("localAutoBackup", true),
                json.optString("autoBackupFrequency", "DAILY")
            )
        }
        if (json.has("driveRetentionCount")) settings.saveDriveRetentionCount(json.optInt("driveRetentionCount", 7))
        if (json.has("driveNetworkPolicy")) settings.saveDriveNetworkPolicy(json.optString("driveNetworkPolicy", "WIFI_ONLY"))
        if (json.has("driveBackupTime")) settings.saveDriveBackupTime(json.optString("driveBackupTime", "02:00"))
        if (json.has("quickSalePresets")) {
            runCatching {
                settings.saveQuickSalePresets(QuickSalePreset.listFromJson(json.getString("quickSalePresets")))
            }
        }
        if (json.has("notePresets")) {
            runCatching {
                val array = json.getJSONArray("notePresets")
                val list = (0 until array.length()).map { array.getString(it) }
                settings.saveNotePresets(list)
            }
        }
        if (json.has("dueReminderTemplates")) {
            runCatching {
                settings.saveDueReminderTemplates(DueTemplate.listFromJson(json.getString("dueReminderTemplates")))
            }
        }
        if (json.has("customCategories")) {
            runCatching {
                settings.saveCategories(CategoryItem.listFromJson(json.getString("customCategories")))
            }
        }
        if (json.has("returnAuditPresets")) {
            runCatching {
                settings.saveReturnAuditPresets(json.getString("returnAuditPresets"))
            }
        }
        if (json.has("themeMode")) {
            runCatching { settings.saveThemeMode(json.getString("themeMode")) }
        }
        if (json.has("uiLayoutMode")) {
            runCatching { settings.saveUiLayoutMode(json.getString("uiLayoutMode")) }
        }
        if (json.has("defaultCostPercentage")) {
            runCatching { settings.saveDefaultCostPercentage(json.getDouble("defaultCostPercentage")) }
        }
        if (context != null && json.has("loyaltyProgramSettings")) {
            runCatching {
                val lObj = json.getJSONObject("loyaltyProgramSettings")
                val restoredLoyalty = LoyaltyProgramSettings(
                    takaPerPoint = lObj.optDouble("takaPerPoint", 100.0),
                    pointCashValue = lObj.optDouble("pointCashValue", 1.0),
                    minRedeemPoints = lObj.optInt("minRedeemPoints", 20),
                    maxBillPercentPayable = lObj.optInt("maxBillPercentPayable", 50),
                    silverSpendThreshold = lObj.optDouble("silverSpendThreshold", 3000.0),
                    goldSpendThreshold = lObj.optDouble("goldSpendThreshold", 7500.0),
                    platinumSpendThreshold = lObj.optDouble("platinumSpendThreshold", 15000.0),
                    pointsExpiryDays = lObj.optInt("pointsExpiryDays", 365),
                    isTiersEnabled = lObj.optBoolean("isTiersEnabled", true)
                )
                LoyaltySettingsHelper.saveSettings(context, restoredLoyalty)
                val app = context.applicationContext as? TechyfiPOSApplication
                app?.repository?.updateLoyaltySettings(restoredLoyalty)
            }
        }
    }

    private fun unzipFile(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            var extractedBytes = 0L
            var entryCount = 0
            while (entry != null) {
                entryCount++
                if (entryCount > 10_000) throw SecurityException("Backup archive contains too many files")
                val outFile = File(targetDir, entry.name)
                // Prevent path traversal
                val rootPath = targetDir.canonicalPath + File.separator
                if (!outFile.canonicalPath.startsWith(rootPath)) {
                    throw SecurityException("Zip traversal exploit detected")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zis.read(buffer)
                            if (count <= 0) break
                            extractedBytes += count
                            if (extractedBytes > 512L * 1024 * 1024) {
                                throw SecurityException("Backup archive is larger than the safe extraction limit")
                            }
                            fos.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun openAssetInput(context: Context, uriOrPath: String) = when {
        uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://") ->
            context.contentResolver.openInputStream(Uri.parse(uriOrPath))
        else -> File(uriOrPath).takeIf { it.isFile }?.inputStream()
    }

    private suspend fun restoreProductImages(context: Context, stagingDir: File, db: AppDatabase) {
        val manifestFile = File(stagingDir, "product_images.json")
        val imageDir = File(stagingDir, "product_images")
        if (!manifestFile.isFile || !imageDir.isDirectory) return
        val destinationDir = File(context.filesDir, "restored_product_images").apply { mkdirs() }
        try {
            val manifest = JSONArray(manifestFile.readText())
            for (index in 0 until manifest.length()) {
                val entry = manifest.getJSONObject(index)
                val barcode = entry.optString("barcode")
                val archiveName = entry.optString("file")
                val source = File(imageDir, archiveName)
                if (barcode.isBlank() || !source.isFile || source.canonicalFile.parentFile != imageDir.canonicalFile) continue
                val destination = File(destinationDir, "${System.currentTimeMillis()}_$archiveName")
                source.copyTo(destination, overwrite = false)
                db.productDao().getProductByBarcode(barcode)?.let { product ->
                    db.productDao().updateProduct(product.copy(imageUri = destination.absolutePath))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Product image restore completed with warnings: ${e.message}")
        }
    }

    private fun pruneRollbackSnapshots(directory: File) {
        val now = System.currentTimeMillis()
        val snapshots = directory.listFiles { file -> file.isFile && file.name.startsWith("pre_restore_") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        snapshots.forEachIndexed { index, file ->
            if (index >= MAX_ROLLBACK_SNAPSHOTS || now - file.lastModified() > MAX_ROLLBACK_AGE_MS) {
                file.delete()
            }
        }
    }
}
