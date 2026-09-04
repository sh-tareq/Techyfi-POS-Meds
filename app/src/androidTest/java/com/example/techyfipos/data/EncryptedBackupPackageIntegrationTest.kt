package com.example.techyfipos.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.support.getSupportWrapper
import com.example.techyfipos.TechyfiPOSApplication
import com.example.techyfipos.data.db.AppDatabase
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.utils.BackupPackageManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedBackupPackageIntegrationTest {
    @Test
    fun encryptedRestoreRebindsRepositoryAndRestoresProductImage() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<TechyfiPOSApplication>()
        val database = AppDatabase.getDatabase(application)
        val sqliteVersion = database.getSupportWrapper()
            .query("SELECT sqlite_version()")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
        val versionParts = sqliteVersion.split('.').mapNotNull(String::toIntOrNull)
        val supportsVacuumInto = versionParts.getOrElse(0) { 0 } > 3 ||
            (versionParts.getOrElse(0) { 0 } == 3 && versionParts.getOrElse(1) { 0 } >= 27)
        assertTrue("Bundled SQLite is too old: $sqliteVersion", supportsVacuumInto)
        database.clearAllTables()
        val image = File(application.filesDir, "encrypted-backup-image.jpg").apply {
            writeBytes(byteArrayOf(9, 8, 7, 6))
        }
        database.productDao().insertProduct(
            Product("ENC-1", "Backup product", "Test", 10.0, 20.0, 3.0, 1, imageUri = image.absolutePath)
        )
        val packageFile = File(application.cacheDir, "full-package.enc")
        val metadata = BackupPackageManager.createEncryptedBackupPackage(
            application,
            packageFile,
            "integration-test-secret"
        )
        assertEquals(1, metadata?.productCount)

        database.productDao().updateProduct(database.productDao().getProductByBarcode("ENC-1")!!.copy(name = "Changed"))
        val result = BackupPackageManager.restoreEncryptedBackupPackage(
            application,
            packageFile,
            "integration-test-secret"
        )
        assertTrue(result.isSuccess)
        assertTrue(result.requiresUiReload)
        val restored = AppDatabase.getDatabase(application).productDao().getProductByBarcode("ENC-1")!!
        assertEquals("Backup product", restored.name)
        assertTrue(restored.imageUri?.let { File(it).readBytes().contentEquals(byteArrayOf(9, 8, 7, 6)) } == true)
        packageFile.delete()
        Unit
    }
}
