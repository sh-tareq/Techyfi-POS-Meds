package com.example.techyfipos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

const val APP_DATABASE_VERSION = 35

/**
 * Keeps every migration compatible with both Room database backends. Room calls
 * the SupportSQLiteDatabase overload without a driver, and exclusively calls the
 * SQLiteConnection overload when a SQLiteDriver (including BundledSQLiteDriver)
 * is configured.
 */
private fun sqlMigration(startVersion: Int, endVersion: Int, vararg statements: String): Migration =
    object : Migration(startVersion, endVersion) {
        override fun migrate(db: SupportSQLiteDatabase) {
            statements.forEach(db::execSQL)
        }

        override fun migrate(connection: SQLiteConnection) {
            statements.forEach(connection::execSQL)
        }
    }

val MIGRATION_1_2 = sqlMigration(
    1, 2,
    "ALTER TABLE customer_dues ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
)

val MIGRATION_2_3 = sqlMigration(
    2, 3,
    "ALTER TABLE sales ADD COLUMN walletPhone TEXT",
    "ALTER TABLE sales ADD COLUMN cardType TEXT",
    "ALTER TABLE sales ADD COLUMN cardLastFour TEXT"
)

val MIGRATION_3_4 = sqlMigration(
    3, 4,
    "ALTER TABLE due_payments ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'Cash'",
    "ALTER TABLE due_payments ADD COLUMN referenceNote TEXT"
)

val MIGRATION_4_5 = sqlMigration(
    4, 5,
    "ALTER TABLE products ADD COLUMN mfgOrPkgType TEXT DEFAULT 'Mfg'",
    "ALTER TABLE products ADD COLUMN mfgOrPkgDate TEXT",
    "ALTER TABLE products ADD COLUMN expiryDate TEXT"
)

val MIGRATION_5_6 = sqlMigration(
    5, 6,
    "ALTER TABLE sales ADD COLUMN taxRate REAL NOT NULL DEFAULT 0",
    "ALTER TABLE sales ADD COLUMN taxAmount REAL NOT NULL DEFAULT 0",
    "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `pinHash` TEXT NOT NULL, `role` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
)

val MIGRATION_6_7 = sqlMigration(
    6, 7,
    "CREATE TABLE IF NOT EXISTS `returns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `saleId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `refundMethod` TEXT NOT NULL, `refundAmount` REAL NOT NULL, `note` TEXT, FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_returns_saleId` ON `returns` (`saleId`)",
    "CREATE TABLE IF NOT EXISTS `return_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `returnId` INTEGER NOT NULL, `productBarcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `price` REAL NOT NULL, `quantity` INTEGER NOT NULL, FOREIGN KEY(`returnId`) REFERENCES `returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_return_items_returnId` ON `return_items` (`returnId`)"
)

val MIGRATION_7_8 = sqlMigration(
    7, 8,
    "ALTER TABLE products ADD COLUMN unit TEXT NOT NULL DEFAULT 'Pcs'",
    "ALTER TABLE sales ADD COLUMN splitPaymentJson TEXT",
    "CREATE TABLE IF NOT EXISTS `held_carts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `customerName` TEXT, `customerPhone` TEXT, `itemsJson` TEXT NOT NULL, `discountAmount` REAL NOT NULL DEFAULT 0, `isPercentageDiscount` INTEGER NOT NULL DEFAULT 0, `timestamp` INTEGER NOT NULL, `note` TEXT)"
)

val MIGRATION_8_9 = sqlMigration(
    8, 9,
    "ALTER TABLE products ADD COLUMN wholesalePrice REAL NOT NULL DEFAULT 0.0",
    "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` REAL NOT NULL, `paymentSource` TEXT NOT NULL DEFAULT 'Cash', `note` TEXT, `timestamp` INTEGER NOT NULL, `cashierName` TEXT NOT NULL DEFAULT 'Admin')",
    "CREATE TABLE IF NOT EXISTS `suppliers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `companyName` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT, `totalPurchased` REAL NOT NULL DEFAULT 0.0, `totalDue` REAL NOT NULL DEFAULT 0.0, `notes` TEXT, `updatedAt` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `purchase_invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplierId` INTEGER NOT NULL, `invoiceNumber` TEXT NOT NULL, `totalAmount` REAL NOT NULL, `paidAmount` REAL NOT NULL, `dueAmount` REAL NOT NULL, `itemsSummary` TEXT NOT NULL, `paymentMethod` TEXT NOT NULL DEFAULT 'Cash', `timestamp` INTEGER NOT NULL, `note` TEXT, FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_purchase_invoices_supplierId` ON `purchase_invoices` (`supplierId`)",
    "CREATE TABLE IF NOT EXISTS `register_closings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `startingFloat` REAL NOT NULL, `cashSales` REAL NOT NULL, `digitalSales` REAL NOT NULL, `cashIn` REAL NOT NULL, `cashOut` REAL NOT NULL, `expectedCash` REAL NOT NULL, `actualCountedCash` REAL NOT NULL, `variance` REAL NOT NULL, `notes` TEXT, `timestamp` INTEGER NOT NULL, `cashierName` TEXT NOT NULL DEFAULT 'Admin')",
    "CREATE TABLE IF NOT EXISTS `customer_loyalty` (`phone` TEXT PRIMARY KEY NOT NULL, `name` TEXT NOT NULL, `pointsBalance` INTEGER NOT NULL DEFAULT 0, `tier` TEXT NOT NULL DEFAULT 'Silver', `lifetimeSpent` REAL NOT NULL DEFAULT 0.0, `updatedAt` INTEGER NOT NULL)"
)

val MIGRATION_9_10 = sqlMigration(
    9, 10,
    "ALTER TABLE sales ADD COLUMN performedByUserId INTEGER",
    "ALTER TABLE sales ADD COLUMN performedByName TEXT",
    "ALTER TABLE returns ADD COLUMN performedByUserId INTEGER",
    "ALTER TABLE returns ADD COLUMN performedByName TEXT"
)

val MIGRATION_10_11 = sqlMigration(
    10, 11,
    "ALTER TABLE sales ADD COLUMN loyaltyPhone TEXT",
    "ALTER TABLE sales ADD COLUMN loyaltyPointsRedeemed INTEGER NOT NULL DEFAULT 0"
)

val MIGRATION_11_12 = sqlMigration(
    11, 12,
    "ALTER TABLE sale_items ADD COLUMN note TEXT",
    "ALTER TABLE customer_dues ADD COLUMN creditLimit REAL NOT NULL DEFAULT 0.0",
    "CREATE TABLE IF NOT EXISTS `inventory_adjustments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productBarcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `previousStock` REAL NOT NULL, `newStock` REAL NOT NULL, `difference` REAL NOT NULL, `reason` TEXT NOT NULL, `note` TEXT, `timestamp` INTEGER NOT NULL, `cashierName` TEXT NOT NULL DEFAULT 'Admin')"
)

val MIGRATION_12_13 = sqlMigration(
    12, 13,
    // 1. Recreate products table with REAL stockQty
    "CREATE TABLE IF NOT EXISTS `products_migration_13` (`barcode` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `purchasePrice` REAL NOT NULL, `sellingPrice` REAL NOT NULL, `stockQty` REAL NOT NULL, `reorderLevel` INTEGER NOT NULL, `imageUri` TEXT, `mfgOrPkgType` TEXT, `mfgOrPkgDate` TEXT, `expiryDate` TEXT, `unit` TEXT NOT NULL, `wholesalePrice` REAL NOT NULL, PRIMARY KEY(`barcode`))",
    "INSERT OR REPLACE INTO `products_migration_13` (`barcode`, `name`, `category`, `purchasePrice`, `sellingPrice`, `stockQty`, `reorderLevel`, `imageUri`, `mfgOrPkgType`, `mfgOrPkgDate`, `expiryDate`, `unit`, `wholesalePrice`) SELECT `barcode`, `name`, `category`, `purchasePrice`, `sellingPrice`, CAST(`stockQty` AS REAL), `reorderLevel`, `imageUri`, `mfgOrPkgType`, `mfgOrPkgDate`, `expiryDate`, `unit`, `wholesalePrice` FROM `products`",
    "DROP TABLE `products`",
    "ALTER TABLE `products_migration_13` RENAME TO `products`",

    // 2. Recreate sale_items table with REAL quantity
    "CREATE TABLE IF NOT EXISTS `sale_items_migration_13` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `saleId` INTEGER NOT NULL, `productBarcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `price` REAL NOT NULL, `quantity` REAL NOT NULL, `note` TEXT, FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "INSERT OR REPLACE INTO `sale_items_migration_13` (`id`, `saleId`, `productBarcode`, `productName`, `price`, `quantity`, `note`) SELECT `id`, `saleId`, `productBarcode`, `productName`, `price`, CAST(`quantity` AS REAL), `note` FROM `sale_items`",
    "DROP TABLE `sale_items`",
    "ALTER TABLE `sale_items_migration_13` RENAME TO `sale_items`",
    "CREATE INDEX IF NOT EXISTS `index_sale_items_saleId` ON `sale_items` (`saleId`)",

    // 3. Recreate return_items table with REAL quantity
    "CREATE TABLE IF NOT EXISTS `return_items_migration_13` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `returnId` INTEGER NOT NULL, `productBarcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `price` REAL NOT NULL, `quantity` REAL NOT NULL, FOREIGN KEY(`returnId`) REFERENCES `returns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "INSERT OR REPLACE INTO `return_items_migration_13` (`id`, `returnId`, `productBarcode`, `productName`, `price`, `quantity`) SELECT `id`, `returnId`, `productBarcode`, `productName`, `price`, CAST(`quantity` AS REAL) FROM `return_items`",
    "DROP TABLE `return_items`",
    "ALTER TABLE `return_items_migration_13` RENAME TO `return_items`",
    "CREATE INDEX IF NOT EXISTS `index_return_items_returnId` ON `return_items` (`returnId`)"
)

val MIGRATION_13_14 = sqlMigration(
    13, 14,
    "ALTER TABLE products ADD COLUMN mrpPrice REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE products ADD COLUMN discountedPrice REAL NOT NULL DEFAULT 0.0"
)

val MIGRATION_14_15 = sqlMigration(
    14, 15,
    "CREATE TABLE IF NOT EXISTS `purchase_invoice_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `invoiceId` INTEGER NOT NULL, `productBarcode` TEXT NOT NULL, `productName` TEXT NOT NULL, `quantity` REAL NOT NULL, `unitCost` REAL NOT NULL, FOREIGN KEY(`invoiceId`) REFERENCES `purchase_invoices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_purchase_invoice_items_invoiceId` ON `purchase_invoice_items` (`invoiceId`)",
    "CREATE INDEX IF NOT EXISTS `index_purchase_invoice_items_productBarcode` ON `purchase_invoice_items` (`productBarcode`)",
    "CREATE TABLE IF NOT EXISTS `supplier_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplierId` INTEGER NOT NULL, `amountPaid` REAL NOT NULL, `paymentMethod` TEXT NOT NULL DEFAULT 'Cash', `referenceNote` TEXT, `timestamp` INTEGER NOT NULL, `performedByName` TEXT, FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_supplier_payments_supplierId` ON `supplier_payments` (`supplierId`)"
)

val MIGRATION_15_16 = sqlMigration(
    15, 16,
    "CREATE TABLE IF NOT EXISTS `scheduled_due_reminders` (`customerPhone` TEXT NOT NULL, `customerName` TEXT NOT NULL, `dueAmount` REAL NOT NULL, `channel` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, `fireAt` INTEGER NOT NULL, PRIMARY KEY(`customerPhone`))"
)

val MIGRATION_16_17 = sqlMigration(
    16, 17,
    "ALTER TABLE sale_items ADD COLUMN purchasePriceAtSale REAL"
)

val MIGRATION_17_18 = sqlMigration(
    17, 18,
    "ALTER TABLE returns ADD COLUMN externalRefundAmount REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE returns ADD COLUMN dueAdjustmentAmount REAL NOT NULL DEFAULT 0.0",
    // Reconstruct the proportional due reduction used by app versions <=17 so an update does not
    // apply that same reduction again. New v18 returns use due-first settlement instead.
    "UPDATE returns SET dueAdjustmentAmount = refundAmount * COALESCE((SELECT CASE WHEN sales.grandTotal > 0 THEN CASE WHEN sales.paymentMethod = 'Due' THEN sales.grandTotal WHEN sales.paymentMethod = 'Split' THEN COALESCE((SELECT SUM(CAST(json_extract(value, '$.amount') AS REAL)) FROM json_each(sales.splitPaymentJson) WHERE lower(json_extract(value, '$.method')) = 'due'), 0.0) ELSE 0.0 END / sales.grandTotal ELSE 0.0 END FROM sales WHERE sales.id = returns.saleId), 0.0)",
    "UPDATE returns SET externalRefundAmount = MAX(0.0, refundAmount - dueAdjustmentAmount)"
)

val MIGRATION_18_19 = sqlMigration(
    18, 19,
    "ALTER TABLE register_closings ADD COLUMN cashRefunds REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE register_closings ADD COLUMN shiftStartedAt INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE scheduled_due_reminders ADD COLUMN messageText TEXT NOT NULL DEFAULT ''",
    "ALTER TABLE scheduled_due_reminders ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0",
    "ALTER TABLE customer_loyalty ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE customer_dues ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0",
    "CREATE TABLE IF NOT EXISTS loyalty_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerPhone TEXT NOT NULL, pointsChange INTEGER NOT NULL, spendChange REAL NOT NULL, type TEXT NOT NULL, reason TEXT NOT NULL, saleId INTEGER, timestamp INTEGER NOT NULL, performedByName TEXT)",
    "CREATE INDEX IF NOT EXISTS index_loyalty_transactions_customerPhone ON loyalty_transactions(customerPhone)",
    "CREATE TABLE IF NOT EXISTS register_shift_state (id INTEGER NOT NULL PRIMARY KEY, startedAt INTEGER NOT NULL, startingFloat REAL NOT NULL)"
)

val MIGRATION_19_20 = sqlMigration(
    19, 20,
    "CREATE TABLE IF NOT EXISTS `due_reminder_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerPhone` TEXT NOT NULL, `customerName` TEXT NOT NULL, `dueAmount` REAL NOT NULL, `channel` TEXT NOT NULL, `messageText` TEXT NOT NULL DEFAULT '', `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL DEFAULT 'SENT')"
)

val MIGRATION_20_21 = sqlMigration(
    20, 21,
    "CREATE TABLE IF NOT EXISTS `customers` (`phone` TEXT PRIMARY KEY NOT NULL, `name` TEXT NOT NULL, `email` TEXT, `address` TEXT, `notes` TEXT, `tag` TEXT NOT NULL DEFAULT 'Retail', `creditLimit` REAL NOT NULL DEFAULT 0.0, `totalOrders` INTEGER NOT NULL DEFAULT 0, `lifetimeSpent` REAL NOT NULL DEFAULT 0.0, `totalDue` REAL NOT NULL DEFAULT 0.0, `loyaltyPoints` INTEGER NOT NULL DEFAULT 0, `loyaltyTier` TEXT NOT NULL DEFAULT 'Silver', `firstPurchaseDate` INTEGER, `lastPurchaseDate` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL DEFAULT 0)",
    "INSERT OR IGNORE INTO `customers` (`phone`, `name`, `totalDue`, `creditLimit`, `createdAt`, `updatedAt`, `isArchived`) SELECT `customerPhone`, `customerName`, `outstandingAmount`, `creditLimit`, `updatedAt`, `updatedAt`, `isArchived` FROM `customer_dues` WHERE TRIM(`customerPhone`) != ''",
    "INSERT OR REPLACE INTO `customers` (`phone`, `name`, `loyaltyPoints`, `loyaltyTier`, `lifetimeSpent`, `createdAt`, `updatedAt`, `isArchived`, `totalDue`, `creditLimit`, `tag`, `totalOrders`) SELECT l.phone, l.name, l.pointsBalance, l.tier, l.lifetimeSpent, l.updatedAt, l.updatedAt, 0, COALESCE(c.totalDue, 0.0), COALESCE(c.creditLimit, 0.0), COALESCE(c.tag, 'Retail'), COALESCE(c.totalOrders, 0) FROM `customer_loyalty` l LEFT JOIN `customers` c ON l.phone = c.phone WHERE TRIM(l.phone) != ''",
    "INSERT OR IGNORE INTO `customers` (`phone`, `name`, `createdAt`, `updatedAt`, `isArchived`) SELECT `customerPhone`, `customerName`, MIN(`timestamp`), MAX(`timestamp`), 0 FROM `sales` WHERE `customerPhone` IS NOT NULL AND TRIM(`customerPhone`) != '' AND `customerName` IS NOT NULL AND TRIM(`customerName`) != '' GROUP BY `customerPhone`"
)

val MIGRATION_21_22 = sqlMigration(
    21, 22,
    "ALTER TABLE `users` ADD COLUMN `customPermissions` TEXT DEFAULT NULL",
    "ALTER TABLE `users` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE `users` ADD COLUMN `maxDiscountPercent` REAL NOT NULL DEFAULT 10.0"
)

val MIGRATION_22_23 = sqlMigration(
    22, 23,
    "CREATE TABLE IF NOT EXISTS `due_charges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerPhone` TEXT NOT NULL, `amount` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `reason` TEXT NOT NULL DEFAULT 'Manual', `saleId` INTEGER, `note` TEXT, FOREIGN KEY(`customerPhone`) REFERENCES `customer_dues`(`customerPhone`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_due_charges_customerPhone` ON `due_charges` (`customerPhone`)",
    // Backfill charge history for existing straight-Due sales so upgrading installs get a starting
    // statement instead of an empty debit side. Split-payment Due portions aren't backfilled here —
    // they're stored as JSON on the sale, not reliably extractable in plain SQL — but are logged for
    // every Due/Split sale going forward from the app code that runs this migration.
    "INSERT INTO `due_charges` (`customerPhone`, `amount`, `timestamp`, `reason`, `saleId`) " +
        "SELECT s.`customerPhone`, (s.`grandTotal` - s.`amountPaid`), s.`timestamp`, 'Sale', s.`id` " +
        "FROM `sales` s INNER JOIN `customer_dues` cd ON cd.`customerPhone` = s.`customerPhone` " +
        "WHERE s.`paymentMethod` = 'Due' AND s.`customerPhone` IS NOT NULL AND TRIM(s.`customerPhone`) != '' " +
        "AND (s.`grandTotal` - s.`amountPaid`) > 0"
)

val MIGRATION_23_24 = sqlMigration(
    23, 24,
    "ALTER TABLE `customers` ADD COLUMN `storeCredit` REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE `register_closings` ADD COLUMN `bkashSales` REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE `register_closings` ADD COLUMN `nagadSales` REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE `register_closings` ADD COLUMN `rocketSales` REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE `register_closings` ADD COLUMN `cardSales` REAL NOT NULL DEFAULT 0.0",
    "ALTER TABLE `register_closings` ADD COLUMN `dueSales` REAL NOT NULL DEFAULT 0.0"
)

val MIGRATION_24_25 = sqlMigration(
    24, 25,
    "CREATE TABLE IF NOT EXISTS `sale_audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `saleId` INTEGER NOT NULL, `action` TEXT NOT NULL, `reason` TEXT NOT NULL, `grandTotal` REAL NOT NULL, `paymentMethod` TEXT NOT NULL, `cashierName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_sale_audit_logs_saleId` ON `sale_audit_logs` (`saleId`)"
)

val MIGRATION_25_26 = sqlMigration(
    25, 26,
    "CREATE TABLE IF NOT EXISTS `expense_audit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `expenseId` INTEGER NOT NULL, `action` TEXT NOT NULL, `reason` TEXT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `paymentSource` TEXT NOT NULL, `userName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_expense_audit_logs_expenseId` ON `expense_audit_logs` (`expenseId`)"
)

val MIGRATION_26_27 = sqlMigration(
    26, 27,
    "CREATE TABLE IF NOT EXISTS `store_credit_vouchers` (`code` TEXT PRIMARY KEY NOT NULL, `initialAmount` REAL NOT NULL, `remainingBalance` REAL NOT NULL, `customerPhone` TEXT, `sourceReturnId` INTEGER, `sourceSaleId` INTEGER, `issuedAt` INTEGER NOT NULL, `expiresAt` INTEGER, `status` TEXT NOT NULL, `notes` TEXT)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_store_credit_vouchers_code` ON `store_credit_vouchers` (`code`)",
    "CREATE INDEX IF NOT EXISTS `index_store_credit_vouchers_customerPhone` ON `store_credit_vouchers` (`customerPhone`)",
    "CREATE TABLE IF NOT EXISTS `store_credit_ledger` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `customerPhone` TEXT NOT NULL, `deltaAmount` REAL NOT NULL, `balanceAfter` REAL NOT NULL, `type` TEXT NOT NULL, `referenceInvoiceId` INTEGER, `voucherCode` TEXT, `note` TEXT, `timestamp` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_store_credit_ledger_customerPhone` ON `store_credit_ledger` (`customerPhone`)",
    "CREATE INDEX IF NOT EXISTS `index_store_credit_ledger_timestamp` ON `store_credit_ledger` (`timestamp`)"
)

val MIGRATION_27_28 = sqlMigration(
    27, 28,
    "ALTER TABLE `register_closings` ADD COLUMN `discrepancyReason` TEXT"
)

val MIGRATION_28_29 = sqlMigration(
    28, 29,
    "ALTER TABLE `sales` ADD COLUMN `storeCreditApplied` REAL NOT NULL DEFAULT 0.0"
)

val MIGRATION_29_30 = sqlMigration(
    29, 30,
    "CREATE INDEX IF NOT EXISTS `index_sales_timestamp` ON `sales` (`timestamp`)"
)

val MIGRATION_30_31 = sqlMigration(
    30, 31,
    "ALTER TABLE `products` ADD COLUMN `created_at` INTEGER NOT NULL DEFAULT 0"
)

val MIGRATION_31_32 = sqlMigration(
    31, 32,
    "ALTER TABLE `customer_dues` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0",
    "CREATE INDEX IF NOT EXISTS `idx_products_name` ON `products` (`name`)",
    "CREATE INDEX IF NOT EXISTS `idx_products_category` ON `products` (`category`)",
    "CREATE INDEX IF NOT EXISTS `idx_sales_customer_phone` ON `sales` (`customerPhone`)",
    "CREATE INDEX IF NOT EXISTS `idx_sale_items_barcode` ON `sale_items` (`productBarcode`)"
)

val MIGRATION_32_33 = sqlMigration(
    32, 33,
    "CREATE INDEX IF NOT EXISTS `index_inventory_adjustments_productBarcode` ON `inventory_adjustments` (`productBarcode`)",
    "CREATE INDEX IF NOT EXISTS `index_due_reminder_logs_customerPhone` ON `due_reminder_logs` (`customerPhone`)"
)

val MIGRATION_33_34 = sqlMigration(
    33, 34,
    "CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)"
)

val MIGRATION_34_35 = sqlMigration(
    34, 35,
    "ALTER TABLE sale_items ADD COLUMN taxable INTEGER NOT NULL DEFAULT 1",
    "CREATE INDEX IF NOT EXISTS `index_customers_tag` ON `customers` (`tag`)",
    "CREATE INDEX IF NOT EXISTS `index_customers_phone` ON `customers` (`phone`)"
)

@Database(
    entities = [
        Product::class,
        Sale::class,
        SaleItem::class,
        CustomerDue::class,
        DuePayment::class,
        DueCharge::class,
        User::class,
        Return::class,
        ReturnItem::class,
        HeldCart::class,
        Expense::class,
        Supplier::class,
        PurchaseInvoice::class,
        PurchaseInvoiceItem::class,
        SupplierPayment::class,
        RegisterClosing::class,
        CustomerLoyalty::class,
        LoyaltyTransaction::class,
        RegisterShiftState::class,
        InventoryAdjustment::class,
        ScheduledDueReminder::class,
        DueReminderLog::class,
        Customer::class,
        SaleAuditLog::class,
        ExpenseAuditLog::class,
        StoreCreditVoucher::class,
        StoreCreditLedgerEntry::class
    ],
    version = APP_DATABASE_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun customerDueDao(): CustomerDueDao
    abstract fun userDao(): UserDao
    abstract fun returnDao(): ReturnDao
    abstract fun heldCartDao(): HeldCartDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseInvoiceDao(): PurchaseInvoiceDao
    abstract fun registerClosingDao(): RegisterClosingDao
    abstract fun customerLoyaltyDao(): CustomerLoyaltyDao
    abstract fun loyaltyTransactionDao(): LoyaltyTransactionDao
    abstract fun registerShiftStateDao(): RegisterShiftStateDao
    abstract fun inventoryAdjustmentDao(): InventoryAdjustmentDao
    abstract fun scheduledDueReminderDao(): ScheduledDueReminderDao
    abstract fun dueReminderLogDao(): DueReminderLogDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleAuditLogDao(): SaleAuditLogDao
    abstract fun expenseAuditLogDao(): ExpenseAuditLogDao
    abstract fun storeCreditVoucherDao(): StoreCreditVoucherDao
    abstract fun storeCreditLedgerDao(): StoreCreditLedgerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val oldDbFile = context.getDatabasePath("bangladesh_pos_db")
                    val newDbFile = context.getDatabasePath("techyfi_pos_db")
                    if (oldDbFile.exists() && !newDbFile.exists()) {
                        oldDbFile.renameTo(newDbFile)
                        val oldShm = context.getDatabasePath("bangladesh_pos_db-shm")
                        val newShm = context.getDatabasePath("techyfi_pos_db-shm")
                        if (oldShm.exists()) oldShm.renameTo(newShm)
                        val oldWal = context.getDatabasePath("bangladesh_pos_db-wal")
                        val newWal = context.getDatabasePath("techyfi_pos_db-wal")
                        if (oldWal.exists()) oldWal.renameTo(newWal)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "techyfi_pos_db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                        MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                        MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
                        MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
                        MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33,
                        MIGRATION_33_34, MIGRATION_34_35
                    )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                try {
                    if (INSTANCE?.isOpen == true) {
                        INSTANCE?.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    INSTANCE = null
                }
            }
        }
    }
}
