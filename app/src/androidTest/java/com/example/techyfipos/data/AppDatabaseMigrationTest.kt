package com.example.techyfipos.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.techyfipos.data.db.AppDatabase
import com.example.techyfipos.data.db.MIGRATION_9_10
import com.example.techyfipos.data.db.MIGRATION_10_11
import com.example.techyfipos.data.db.MIGRATION_14_15
import com.example.techyfipos.data.db.MIGRATION_15_16
import com.example.techyfipos.data.db.MIGRATION_16_17
import com.example.techyfipos.data.db.MIGRATION_17_18
import com.example.techyfipos.data.db.MIGRATION_18_19
import com.example.techyfipos.data.db.MIGRATION_11_12
import com.example.techyfipos.data.db.MIGRATION_12_13
import com.example.techyfipos.data.db.MIGRATION_13_14
import com.example.techyfipos.data.db.MIGRATION_19_20
import com.example.techyfipos.data.db.MIGRATION_20_21
import com.example.techyfipos.data.db.MIGRATION_21_22
import com.example.techyfipos.data.db.MIGRATION_22_23
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migration14To15AddsSupplierLineItemsAndPaymentLedger() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-14-15-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 14).close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            assertTrue(migrated.purchaseInvoiceDao().getAllPurchaseInvoiceItems().first().isEmpty())
            assertTrue(migrated.supplierDao().getAllSupplierPayments().first().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration16To17PreservesSaleItemsAndAddsCostSnapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-16-17-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 16).apply {
            execSQL(
                "INSERT INTO products (barcode, name, category, purchasePrice, sellingPrice, stockQty, reorderLevel, unit, wholesalePrice, mrpPrice, discountedPrice) VALUES ('P-1', 'Tea', 'Drinks', 7.0, 10.0, 5.0, 1, 'Pcs', 0.0, 0.0, 0.0)"
            )
            execSQL(
                "INSERT INTO sales (id, timestamp, subtotal, discountAmount, isPercentageDiscount, grandTotal, paymentMethod, amountPaid, taxRate, taxAmount, loyaltyPointsRedeemed) VALUES (1, 1000, 10.0, 0.0, 0, 10.0, 'Cash', 10.0, 0.0, 0.0, 0)"
            )
            execSQL(
                "INSERT INTO sale_items (id, saleId, productBarcode, productName, price, quantity, note) VALUES (1, 1, 'P-1', 'Tea', 10.0, 1.0, 'hot')"
            )
            close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val items = migrated.saleDao().getItemsForSale(1)
            assertEquals(1, items.size)
            assertEquals("hot", items.single().note)
            assertTrue(items.single().purchasePriceAtSale == null)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun restoredVersion9DatabaseOpensWithBundledDriverAndPreservesHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "restored-v9-${System.currentTimeMillis()}.db"

        createDatabaseFromExportedSchema(context, name, 9).apply {
            execSQL(
                """INSERT INTO sales
                    (id, timestamp, subtotal, discountAmount, isPercentageDiscount,
                     grandTotal, paymentMethod, customerName, customerPhone, amountPaid,
                     walletPhone, cardType, cardLastFour, taxRate, taxAmount, splitPaymentJson)
                    VALUES (41, 1723305600000, 250.0, 0.0, 0, 250.0, 'Cash',
                            'Legacy customer', '01700000000', 250.0, NULL, NULL, NULL,
                            0.0, 0.0, NULL)
                """.trimIndent()
            )
            close()
        }

        val restored = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        try {
            val sales = restored.saleDao().getAllSales().first()
            assertEquals(1, sales.size)
            assertEquals(41L, sales.single().id)
            assertEquals("Legacy customer", sales.single().customerName)
            assertEquals(0, sales.single().loyaltyPointsRedeemed)
        } finally {
            restored.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration17To18PreservesLegacySplitDueSettlement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-17-18-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 17).apply {
            execSQL("INSERT INTO sales (id,timestamp,subtotal,discountAmount,isPercentageDiscount,grandTotal,paymentMethod,amountPaid,taxRate,taxAmount,splitPaymentJson,loyaltyPointsRedeemed) VALUES (1,1000,100,0,0,100,'Split',40,0,0,'[{\"method\":\"Cash\",\"amount\":40},{\"method\":\"Due\",\"amount\":60}]',0)")
            execSQL("INSERT INTO returns (id,saleId,timestamp,refundMethod,refundAmount) VALUES (1,1,2000,'Cash',50)")
            close()
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val record = migrated.returnDao().getReturnsForSaleOnce(1).single()
            assertEquals(30.0, record.dueAdjustmentAmount, 0.001)
            assertEquals(20.0, record.externalRefundAmount, 0.001)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration18To19AddsOperationalAuditAndRecoveryTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-18-19-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 18).close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            assertTrue(migrated.loyaltyTransactionDao().getAll().first().isEmpty())
            assertTrue(migrated.registerShiftStateDao().get() == null)
            assertTrue(migrated.scheduledDueReminderDao().getAllOnce().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    /** Creates the same database file an older app/Drive backup contains. */
    private fun createDatabaseFromExportedSchema(
        context: android.content.Context,
        name: String,
        version: Int
    ): SupportSQLiteDatabase {
        val schemaPath = "com.example.techyfipos.data.db.AppDatabase/$version.json"
        val schema = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open(schemaPath).bufferedReader().use { it.readText() }
        ).getJSONObject("database")

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val tableName = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) for (j in 0 until indices.length()) {
                                db.execSQL(
                                    indices.getJSONObject(j).getString("createSql")
                                        .replace("\${TABLE_NAME}", tableName)
                                )
                            }
                        }
                        val setupQueries = schema.getJSONArray("setupQueries")
                        for (i in 0 until setupQueries.length()) {
                            db.execSQL(setupQueries.getString(i))
                        }
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }).build()
        )
        return helper.writableDatabase
    }

    @Test
    fun migration9To10PreservesDataAndAddsOperatorAuditColumns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-9-10-${System.currentTimeMillis()}.db"
        context.deleteDatabase(name)

        val version9 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, subtotal REAL NOT NULL, discountAmount REAL NOT NULL, isPercentageDiscount INTEGER NOT NULL, grandTotal REAL NOT NULL, paymentMethod TEXT NOT NULL, amountPaid REAL NOT NULL, taxRate REAL NOT NULL, taxAmount REAL NOT NULL)")
                        db.execSQL("CREATE TABLE returns (id INTEGER PRIMARY KEY, saleId INTEGER NOT NULL, timestamp INTEGER NOT NULL, refundMethod TEXT NOT NULL, refundAmount REAL NOT NULL, note TEXT)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        version9.writableDatabase.execSQL("INSERT INTO sales VALUES (1,1000,100,0,0,100,'Cash',100,0,0)")
        version9.close()

        val version10 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        MIGRATION_9_10.migrate(db)
                    }
                }).build()
        )
        version10.writableDatabase.query("SELECT id, performedByUserId, performedByName FROM sales").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        version10.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migration10To11PreservesSalesAndAddsLoyaltyAuditColumns() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-10-11-${System.currentTimeMillis()}.db"
        context.deleteDatabase(name)
        val version10 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY, timestamp INTEGER NOT NULL, subtotal REAL NOT NULL, discountAmount REAL NOT NULL, isPercentageDiscount INTEGER NOT NULL, grandTotal REAL NOT NULL, paymentMethod TEXT NOT NULL, amountPaid REAL NOT NULL, taxRate REAL NOT NULL, taxAmount REAL NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        version10.writableDatabase.execSQL("INSERT INTO sales VALUES (7,1000,100,0,0,100,'Cash',100,0,0)")
        version10.close()

        val version11 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        MIGRATION_10_11.migrate(db)
                    }
                }).build()
        )
        version11.writableDatabase.query("SELECT id, loyaltyPhone, loyaltyPointsRedeemed FROM sales").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertTrue(cursor.isNull(1))
            assertEquals(0, cursor.getInt(2))
        }
        version11.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migration19To20AddsDueReminderLogsTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-19-20-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 19).close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            assertTrue(migrated.dueReminderLogDao().getAllLogsOnce().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration20To21CreatesCustomersTableAndBackfillsFromDuesAndLoyalty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-20-21-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 20).apply {
            execSQL("INSERT INTO customer_dues (customerPhone, customerName, outstandingAmount, creditLimit, updatedAt, isArchived) VALUES ('01711111111', 'Rahim', 500.0, 1000.0, 5000, 0)")
            execSQL("INSERT INTO customer_loyalty (phone, name, pointsBalance, tier, lifetimeSpent, updatedAt, isActive) VALUES ('01722222222', 'Karim', 100, 'Gold', 15000.0, 6000, 1)")
            close()
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val customers = migrated.customerDao().getAllCustomersOnce()
            assertEquals(2, customers.size)
            val rahim = customers.first { it.phone == "01711111111" }
            assertEquals("Rahim", rahim.name)
            assertEquals(500.0, rahim.totalDue, 0.001)

            val karim = customers.first { it.phone == "01722222222" }
            assertEquals("Karim", karim.name)
            assertEquals(100, karim.loyaltyPoints)
            assertEquals("Gold", karim.loyaltyTier)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration21To22AddsUserCustomPermissionsAndLimits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-21-22-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 21).apply {
            execSQL("INSERT INTO users (id, name, role, pinHash, createdAt) VALUES (1, 'Cashier Bob', 'Cashier', 'hash123', 1000)")
            close()
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val user = migrated.userDao().getUserById(1)
            assertTrue(user != null)
            assertEquals(true, user?.isActive)
            assertEquals(10.0, user?.maxDiscountPercent ?: 0.0, 0.001)
            assertTrue(user?.customPermissions == null)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration22To23CreatesDueChargesAndBackfillsFromSales() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-22-23-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 22).apply {
            execSQL("INSERT INTO customer_dues (customerPhone, customerName, outstandingAmount, creditLimit, updatedAt, isArchived) VALUES ('01800000000', 'Customer Due', 300.0, 1000.0, 2000, 0)")
            execSQL("INSERT INTO sales (id, timestamp, subtotal, discountAmount, isPercentageDiscount, grandTotal, paymentMethod, customerPhone, amountPaid, taxRate, taxAmount, loyaltyPointsRedeemed) VALUES (55, 2000, 300.0, 0.0, 0, 300.0, 'Due', '01800000000', 0.0, 0.0, 0.0, 0)")
            close()
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val charges = migrated.customerDueDao().getAllDueCharges().first()
            assertEquals(1, charges.size)
            val charge = charges.single()
            assertEquals("01800000000", charge.customerPhone)
            assertEquals(300.0, charge.amount, 0.001)
            assertEquals(55L, charge.saleId)
            assertEquals("Sale", charge.reason)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun testN3_migration36To37_mergesCollidingBarcodeProducts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-36-37-n3-${System.currentTimeMillis()}.db"
        createDatabaseFromExportedSchema(context, name, 36).apply {
            // Insert two products with colliding barcodes: "COLLIDE-1" and "collide-1"
            execSQL("INSERT INTO products (barcode, name, category, purchasePrice, sellingPrice, stockQty, reorderLevel, unit, wholesalePrice, mrpPrice, discountedPrice, created_at) VALUES ('COLLIDE-1', 'Old Upper Item', 'General', 10.0, 20.0, 8.0, 1, 'Pcs', 0.0, 0.0, 0.0, 1000)")
            execSQL("INSERT INTO products (barcode, name, category, purchasePrice, sellingPrice, stockQty, reorderLevel, unit, wholesalePrice, mrpPrice, discountedPrice, created_at) VALUES ('collide-1', 'New Lower Item', 'General', 15.0, 25.0, 4.0, 1, 'Pcs', 0.0, 0.0, 0.0, 2000)")
            close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val product = migrated.productDao().getProductByBarcode("collide-1")
            assertTrue("Collided product must exist with lowercase barcode", product != null)
            assertEquals("collide-1", product?.barcode)
            // N-3: Stock must be summed (8 + 4 = 12), not overwritten/deleted!
            assertEquals(12.0, product?.stockQty ?: 0.0, 0.001)
            // Newest name and prices preserved
            assertEquals("New Lower Item", product?.name)
            assertEquals(25.0, product?.sellingPrice ?: 0.0, 0.001)

            val adjustments = migrated.inventoryAdjustmentDao().getAllAdjustments().first()
            assertTrue("An inventory adjustment must be logged for merged stock", adjustments.any { it.productBarcode == "collide-1" })
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
