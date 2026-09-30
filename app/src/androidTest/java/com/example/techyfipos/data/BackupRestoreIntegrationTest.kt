package com.example.techyfipos.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.techyfipos.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRestoreIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: DataRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        repository = DataRepository(context, database, SettingsDataStore(context))
        repository.setSessionUser(User(id = 1, name = "Test Owner", pinHash = "x", role = "OWNER"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun currentBackupRestoresHistoryAndLineItemsWithOriginalId() {
      runBlocking {
        val saleId = database.saleDao().insertSale(
            Sale(
                id = 81,
                timestamp = 1_723_400_000_000,
                subtotal = 240.0,
                discountAmount = 20.0,
                isPercentageDiscount = false,
                grandTotal = 220.0,
                paymentMethod = "Cash",
                amountPaid = 250.0
            )
        )
        database.saleDao().insertSaleItems(
            listOf(SaleItem(saleId = saleId, productBarcode = "1234", productName = "Test product", price = 120.0, quantity = 2.0))
        )
        val backup = File(context.cacheDir, "history-round-trip.json")

        assertEquals(true, repository.exportBackup(backup))
        database.clearAllTables()
        assertEquals(true, repository.importBackup(backup))

        val restored = repository.allSalesWithItems.first().single()
        assertEquals(81L, restored.sale.id)
        assertEquals(220.0, restored.sale.grandTotal, 0.001)
        assertEquals("Test product", restored.items.single().productName)
        assertEquals(2.0, restored.items.single().quantity, 0.001)
        backup.delete()
      }
    }

    @Test
    fun legacyBackupWithoutNewSaleFieldsStillRestoresHistory() {
      runBlocking {
        val legacySale = JSONObject()
            .put("id", 17)
            .put("timestamp", 1_700_000_000_000)
            .put("total", 150.0)
            .put("items", JSONArray().put(
                JSONObject()
                    .put("barcode", "5678")
                    .put("name", "Legacy product")
                    .put("sellingPrice", 75.0)
                    .put("qty", 2)
            ))
        val backup = File(context.cacheDir, "legacy-history.json")
        backup.writeText(JSONObject().put("sales", JSONArray().put(legacySale)).toString())

        assertEquals(true, repository.importBackup(backup))

        val restored = repository.allSalesWithItems.first().single()
        assertEquals(17L, restored.sale.id)
        assertEquals(150.0, restored.sale.grandTotal, 0.001)
        assertEquals("Cash", restored.sale.paymentMethod)
        assertEquals("Legacy product", restored.items.single().productName)
        backup.delete()
      }
    }

    @Test
    fun currentBackupRestoresAllOperationalEntitiesAndNewFields() = runBlocking {
        val sourceImage = File(context.cacheDir, "backup-product.jpg").apply {
            writeBytes(byteArrayOf(0x01, 0x23, 0x45, 0x67))
        }
        database.productDao().insertProduct(Product(
            barcode = "P-1", name = "বাংলা পণ্য", category = "Food", purchasePrice = 40.0,
            sellingPrice = 60.0, stockQty = 8.0, reorderLevel = 2, mfgOrPkgType = "Pkg",
            mfgOrPkgDate = "2026-08-01", expiryDate = "2027-08-01", unit = "Box", wholesalePrice = 52.0,
            imageUri = sourceImage.absolutePath
        ))
        val saleId = database.saleDao().insertSale(Sale(
            id = 91, timestamp = 1000L, subtotal = 60.0, discountAmount = 0.0,
            isPercentageDiscount = false, grandTotal = 63.0, paymentMethod = "Split",
            taxRate = 5.0, taxAmount = 3.0, splitPaymentJson = "[{\"method\":\"Cash\",\"amount\":63}]",
            performedByUserId = 7, performedByName = "Owner"
        ))
        database.saleDao().insertSaleItems(listOf(SaleItem(saleId = saleId, productBarcode = "P-1", productName = "বাংলা পণ্য", price = 60.0, quantity = 1.0)))
        database.userDao().insertUser(User(id = 7, name = "Owner", pinHash = "legacy", role = "OWNER"))
        val returnId = database.returnDao().insertReturn(Return(id = 4, saleId = saleId, timestamp = 2000L, refundMethod = "Cash", refundAmount = 60.0, performedByUserId = 7, performedByName = "Owner"))
        database.returnDao().insertReturnItems(listOf(ReturnItem(id = 5, returnId = returnId, productBarcode = "P-1", productName = "বাংলা পণ্য", price = 60.0, quantity = 1.0)))
        database.heldCartDao().insertHeldCart(HeldCart(id = 3, title = "Counter 2", itemsJson = "[]"))
        database.expenseDao().insertExpense(Expense(id = 2, title = "Electricity", category = "Bills", type = "EXPENSE", amount = 500.0))
        database.supplierDao().upsertSupplier(Supplier(id = 6, name = "Vendor", companyName = "Vendor Ltd", phone = "01700000000"))
        database.purchaseInvoiceDao().insertPurchaseInvoice(PurchaseInvoice(id = 8, supplierId = 6, invoiceNumber = "INV-8", totalAmount = 1000.0, paidAmount = 700.0, dueAmount = 300.0, itemsSummary = "10 boxes"))
        database.registerClosingDao().insertClosing(RegisterClosing(id = 9, date = "2026-08-23", startingFloat = 100.0, cashSales = 500.0, digitalSales = 200.0, cashIn = 0.0, cashOut = 50.0, expectedCash = 550.0, actualCountedCash = 550.0, variance = 0.0))
        database.customerLoyaltyDao().upsertLoyalty(CustomerLoyalty(phone = "01800000000", name = "Customer", pointsBalance = 12, lifetimeSpent = 1200.0))

        val backup = File(context.cacheDir, "complete-round-trip.json")
        assertEquals(true, repository.exportBackup(backup))
        database.clearAllTables()
        assertEquals(true, repository.importBackup(backup))

        val product = database.productDao().getProductByBarcode("P-1")!!
        assertEquals("Box", product.unit)
        assertEquals(52.0, product.wholesalePrice, 0.001)
        assertTrue(product.imageUri?.let { File(it).readBytes().contentEquals(byteArrayOf(0x01, 0x23, 0x45, 0x67)) } == true)
        val restoredSale = database.saleDao().getSaleWithItemsById(91)!!.sale
        assertEquals(5.0, restoredSale.taxRate, 0.001)
        assertEquals("Split", restoredSale.paymentMethod)
        assertEquals(7L, restoredSale.performedByUserId)
        assertEquals("Owner", restoredSale.performedByName)
        assertEquals(1, database.returnDao().getAllReturns().first().size)
        assertEquals(1, database.heldCartDao().getAllHeldCarts().first().size)
        assertEquals(1, database.expenseDao().getAllExpenses().first().size)
        assertEquals(1, database.supplierDao().getAllSuppliers().first().size)
        assertEquals(1, database.purchaseInvoiceDao().getAllPurchaseInvoices().first().size)
        assertEquals(1, database.registerClosingDao().getAllClosings().first().size)
        assertEquals(1, database.customerLoyaltyDao().getAllLoyaltyMembers().first().size)
        // Portable JSON deliberately excludes low-entropy PIN hashes.
        assertEquals(0, database.userDao().getAllUsersOnce().size)
        assertTrue(!JSONObject(backup.readText()).has("users"))
        sourceImage.delete()
        backup.delete()
        Unit
    }

    @Test
    fun authenticationHasNoDefaultOwnerBackdoorAndMigratesLegacyHashes() = runBlocking {
        context.getSharedPreferences("auth_security", Context.MODE_PRIVATE).edit().clear().commit()
        assertNull(repository.verifyPin("1234"))
        assertEquals(0, database.userDao().getUserCount())

        val legacyHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        val userId = database.userDao().insertUser(User(name = "Owner", pinHash = legacyHash, role = "OWNER"))
        assertEquals(userId, repository.verifyPin("1234")?.id)
        val upgraded = database.userDao().getAllUsersOnce().single().pinHash
        assertTrue(upgraded.startsWith("pbkdf2$"))
    }

    @Test
    fun ownerOnlyInventoryMutationsAreEnforcedInRepository() = runBlocking {
        val cashier = User(id = 1, name = "Cashier", pinHash = "x", role = "CASHIER")
        repository.setSessionUser(cashier)
        var denied = false
        try {
            repository.insertProduct(Product("P-2", "Restricted", "Test", 1.0, 2.0, 1.0, 1))
        } catch (_: DataRepository.UnauthorizedException) {
            denied = true
        }
        assertTrue(denied)

        repository.setSessionUser(cashier.copy(role = "OWNER"))
        repository.insertProduct(Product("P-2", "Allowed", "Test", 1.0, 2.0, 1.0, 1))
        assertEquals("Allowed", database.productDao().getProductByBarcode("P-2")?.name)
    }

    @Test
    fun dueOverpaymentIsCappedAndInsufficientStockRejectsSaleAtomically() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        database.customerDueDao().upsertCustomerDue(CustomerDue("01700000000", "Customer", 100.0))
        repository.recordDuePayment("01700000000", 150.0)
        assertEquals(0.0, database.customerDueDao().getCustomerDueByPhone("01700000000")!!.outstandingAmount, 0.001)
        assertEquals(100.0, database.customerDueDao().getPaymentsForCustomer("01700000000").first().single().amountPaid, 0.001)

        val product = Product("P-3", "One left", "Test", 1.0, 2.0, 1.0, 1)
        database.productDao().insertProduct(product)
        var rejected = false
        try {
            repository.completeSale(4.0, 0.0, false, 4.0, "Cash", null, null, 4.0, null, null, null, listOf(product to 2.0))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(1.0, database.productDao().getProductByBarcode("P-3")!!.stockQty, 0.0001)
        assertTrue(database.saleDao().getAllSales().first().isEmpty())
    }

    @Test
    fun loyaltyRedemptionAndSaleDeletionRemainAtomic() = runBlocking {
        val owner = User(id = 1, name = "Owner", pinHash = "x", role = "OWNER")
        repository.setSessionUser(owner)
        val product = Product("P-LOYALTY", "Loyalty item", "Test", 10.0, 20.0, 5.0, 1)
        database.productDao().insertProduct(product)
        database.customerLoyaltyDao().upsertLoyalty(
            CustomerLoyalty(phone = "01700000001", name = "Member", pointsBalance = 10)
        )

        val saleId = repository.completeSale(
            subtotal = 40.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 35.0,
            paymentMethod = "Cash",
            customerName = "Member",
            customerPhone = "01700000001",
            amountPaid = 35.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItems = listOf(product to 2.0),
            loyaltyPhone = "01700000001",
            loyaltyPointsToRedeem = 5
        )

        assertEquals(3.0, database.productDao().getProductByBarcode(product.barcode)!!.stockQty, 0.001)
        assertEquals(5, database.customerLoyaltyDao().getLoyaltyByPhone("01700000001")!!.pointsBalance)
        assertEquals(5, database.saleDao().getSaleWithItemsById(saleId)!!.sale.loyaltyPointsRedeemed)

        repository.deleteSale(saleId)
        assertEquals(5.0, database.productDao().getProductByBarcode(product.barcode)!!.stockQty, 0.001)
        assertEquals(10, database.customerLoyaltyDao().getLoyaltyByPhone("01700000001")!!.pointsBalance)
        assertNull(database.saleDao().getSaleWithItemsById(saleId))
    }

    @Test
    fun staleLoyaltyBalanceRollsBackSaleAndStock() = runBlocking {
        val product = Product("P-STALE", "Stale points item", "Test", 5.0, 10.0, 1.0, 1)
        database.productDao().insertProduct(product)
        database.customerLoyaltyDao().upsertLoyalty(
            CustomerLoyalty(phone = "01700000002", name = "Member", pointsBalance = 2)
        )

        var rejected = false
        try {
            repository.completeSale(
                subtotal = 10.0,
                discountAmount = 0.0,
                isPercentage = false,
                grandTotal = 5.0,
                paymentMethod = "Cash",
                customerName = "Member",
                customerPhone = "01700000002",
                amountPaid = 5.0,
                walletPhone = null,
                cardType = null,
                cardLastFour = null,
                cartItems = listOf(product to 1.0),
                loyaltyPhone = "01700000002",
                loyaltyPointsToRedeem = 5
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(1.0, database.productDao().getProductByBarcode(product.barcode)!!.stockQty, 0.0001)
        assertEquals(2, database.customerLoyaltyDao().getLoyaltyByPhone("01700000002")!!.pointsBalance)
        assertTrue(database.saleDao().getAllSales().first().isEmpty())
    }

    @Test
    fun barcodeReplacementIsAtomicAndRejectsDuplicates() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        val original = Product("OLD-CODE", "Original", "Test", 1.0, 2.0, 4.0, 1)
        database.productDao().insertProduct(original)
        database.productDao().insertProduct(Product("TAKEN", "Existing", "Test", 1.0, 2.0, 1.0, 1))

        var duplicateRejected = false
        try {
            repository.replaceProductBarcode("OLD-CODE", original.copy(barcode = "TAKEN", name = "Changed"))
        } catch (_: IllegalArgumentException) {
            duplicateRejected = true
        }
        assertTrue(duplicateRejected)
        assertEquals("Original", database.productDao().getProductByBarcode("OLD-CODE")?.name)

        repository.replaceProductBarcode("OLD-CODE", original.copy(barcode = "NEW-CODE", name = "Changed"))
        assertNull(database.productDao().getProductByBarcode("OLD-CODE"))
        assertEquals("Changed", database.productDao().getProductByBarcode("NEW-CODE")?.name)
    }

    @Test
    fun deletingPartiallyReturnedDueSaleRestoresOnlyRemainingStockAndDue() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        val product = Product("P-RETURN", "Return item", "Test", 50.0, 100.0, 10.0, 1)
        database.productDao().insertProduct(product)

        val saleId = repository.completeSale(
            subtotal = 400.0, discountAmount = 0.0, isPercentage = false,
            grandTotal = 400.0, paymentMethod = "Due", customerName = "Due Customer",
            customerPhone = "01711111111", amountPaid = 0.0, walletPhone = null,
            cardType = null, cardLastFour = null, cartItems = listOf(product to 4.0)
        )
        repository.processReturn(
            saleId,
            listOf(ReturnLine(product.barcode, product.name, product.sellingPrice, 1.0)),
            "Due adjustment",
            null
        )

        assertEquals(7.0, database.productDao().getProductByBarcode(product.barcode)!!.stockQty, 0.001)
        assertEquals(300.0, database.customerDueDao().getCustomerDueByPhone("01711111111")!!.outstandingAmount, 0.001)

        repository.deleteSale(saleId)

        assertEquals(10.0, database.productDao().getProductByBarcode(product.barcode)!!.stockQty, 0.001)
        assertEquals(0.0, database.customerDueDao().getCustomerDueByPhone("01711111111")!!.outstandingAmount, 0.001)
    }

    @Test
    fun splitDueReturnReducesDueBeforePayingCash() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        val product = Product("P-SPLIT-RETURN", "Split return", "Test", 20.0, 100.0, 5.0, 1)
        database.productDao().insertProduct(product)
        val saleId = repository.completeSale(
            subtotal = 100.0, discountAmount = 0.0, isPercentage = false,
            grandTotal = 100.0, paymentMethod = "Split", customerName = "Customer",
            customerPhone = "01733333333", amountPaid = 40.0, walletPhone = null,
            cardType = null, cardLastFour = null, cartItems = listOf(product to 1.0),
            splitPaymentJson = "[{\"method\":\"Cash\",\"amount\":40},{\"method\":\"Due\",\"amount\":60}]"
        )

        repository.processReturn(saleId, listOf(ReturnLine(product.barcode, product.name, 100.0, 0.7)), "Cash", null)

        val record = database.returnDao().getReturnsForSaleOnce(saleId).single()
        assertEquals(60.0, record.dueAdjustmentAmount, 0.001)
        assertEquals(10.0, record.externalRefundAmount, 0.001)
        assertEquals(0.0, database.customerDueDao().getCustomerDueByPhone("01733333333")!!.outstandingAmount, 0.001)
    }

    @Test
    fun reAddingLoyaltyMemberPreservesBalanceAndSpend() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        database.customerLoyaltyDao().upsertLoyalty(
            CustomerLoyalty("01744444444", "Old name", pointsBalance = 75, lifetimeSpent = 2500.0)
        )

        repository.upsertLoyalty(CustomerLoyalty("01744444444", "Updated name"))

        val member = database.customerLoyaltyDao().getLoyaltyByPhone("01744444444")!!
        assertEquals("Updated name", member.name)
        assertEquals(75, member.pointsBalance)
        assertEquals(2500.0, member.lifetimeSpent, 0.001)
    }

    @Test
    fun basicSpreadsheetImportPreservesAdvancedProductFields() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        val existing = Product(
            "P-IMPORT", "Old", "Test", 10.0, 20.0, 5.0, 1,
            unit = "Kg", wholesalePrice = 18.0, mrpPrice = 25.0, discountedPrice = 19.0
        )
        database.productDao().insertProduct(existing)

        repository.upsertProducts(
            listOf(existing.copy(name = "Updated", sellingPrice = 22.0, unit = "Pcs", mrpPrice = 0.0)),
            setOf("barcode", "name", "sellingPrice")
        )

        val updated = database.productDao().getProductByBarcode("P-IMPORT")!!
        assertEquals("Updated", updated.name)
        assertEquals(22.0, updated.sellingPrice, 0.001)
        assertEquals("Kg", updated.unit)
        assertEquals(25.0, updated.mrpPrice, 0.001)
        assertEquals(19.0, updated.discountedPrice, 0.001)
    }

    @Test
    fun cashierCannotMutateCustomerDueLedger() = runBlocking {
        database.customerDueDao().upsertCustomerDue(CustomerDue("01722222222", "Customer", 100.0))
        repository.setSessionUser(User(id = 2, name = "Cashier", pinHash = "x", role = "CASHIER"))

        var deniedCount = 0
        try { repository.recordDuePayment("01722222222", 10.0) } catch (_: DataRepository.UnauthorizedException) { deniedCount++ }
        try { repository.upsertCustomerDue(CustomerDue("01722222222", "Customer", 120.0)) } catch (_: DataRepository.UnauthorizedException) { deniedCount++ }
        try { repository.updateCustomerCreditLimit("01722222222", 500.0) } catch (_: DataRepository.UnauthorizedException) { deniedCount++ }

        assertEquals(3, deniedCount)
        assertEquals(100.0, database.customerDueDao().getCustomerDueByPhone("01722222222")!!.outstandingAmount, 0.001)
        assertTrue(database.customerDueDao().getPaymentsForCustomer("01722222222").first().isEmpty())
    }

    @Test
    fun supplierPaymentsAndMissingSupplierInvoicesAreRejectedAtomically() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        database.supplierDao().upsertSupplier(
            Supplier(id = 12, name = "Vendor", companyName = "Vendor Ltd", phone = "01800000000", totalDue = 100.0)
        )

        repository.recordSupplierPayment(12, 40.0, "Bank", "TRX-40")
        assertEquals(60.0, database.supplierDao().getSupplierById(12)!!.totalDue, 0.001)
        val payment = database.supplierDao().getPaymentsForSupplier(12).first().single()
        assertEquals("Bank", payment.paymentMethod)
        assertEquals("TRX-40", payment.referenceNote)

        database.productDao().insertProduct(Product("P-STOCK-IN", "Stock item", "Test", 5.0, 10.0, 2.0, 1))
        val invoiceId = repository.recordPurchaseInvoice(
            12, "INV-STOCK", 30.0, 30.0, "Stock item × 3", stockLines = listOf(PurchaseStockLine("P-STOCK-IN", 3.0, 10.0))
        )
        assertEquals(5.0, database.productDao().getProductByBarcode("P-STOCK-IN")!!.stockQty, 0.001)
        assertEquals(10.0, database.productDao().getProductByBarcode("P-STOCK-IN")!!.purchasePrice, 0.001)
        assertEquals(3.0, database.purchaseInvoiceDao().getItemsForInvoice(invoiceId).first().single().quantity, 0.001)

        var overpaymentRejected = false
        try {
            repository.recordSupplierPayment(12, 61.0)
        } catch (_: IllegalArgumentException) {
            overpaymentRejected = true
        }
        assertTrue(overpaymentRejected)
        assertEquals(60.0, database.supplierDao().getSupplierById(12)!!.totalDue, 0.001)

        var missingSupplierRejected = false
        try {
            repository.recordPurchaseInvoice(999, "MISSING", 100.0, 25.0, "Stock")
        } catch (_: IllegalArgumentException) {
            missingSupplierRejected = true
        }
        assertTrue(missingSupplierRejected)
        assertEquals(1, database.purchaseInvoiceDao().getAllPurchaseInvoices().first().size)
    }

    @Test
    fun archivingDuePreservesRepaymentLedger() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        database.customerDueDao().upsertCustomerDue(CustomerDue("01755555555", "Archived", 100.0))
        database.customerDueDao().recordDuePayment("01755555555", 20.0, System.currentTimeMillis())

        repository.deleteCustomerDue("01755555555")

        assertTrue(database.customerDueDao().getAllDues().first().isEmpty())
        assertTrue(database.customerDueDao().getCustomerDueByPhone("01755555555")!!.isArchived)
        assertEquals(1, database.customerDueDao().getPaymentsForCustomer("01755555555").first().size)
    }

    @Test
    fun registerCloseStoresRefundAuditAndStartsFreshShift() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        repository.updateRegisterShiftStartingFloat(500.0)
        val startedAt = database.registerShiftStateDao().get()!!.startedAt
        repository.insertRegisterClosing(RegisterClosing(
            date = "2026-08-30", startingFloat = 500.0, cashSales = 100.0, digitalSales = 50.0,
            cashIn = 0.0, cashOut = 20.0, cashRefunds = 10.0, shiftStartedAt = startedAt,
            expectedCash = 570.0, actualCountedCash = 570.0, variance = 0.0
        ))

        val closing = database.registerClosingDao().getAllClosings().first().single()
        assertEquals(10.0, closing.cashRefunds, 0.001)
        assertEquals(startedAt, closing.shiftStartedAt)
        assertEquals(0.0, database.registerShiftStateDao().get()!!.startingFloat, 0.001)
    }

    @Test
    fun productReferencedByActiveCartCannotBeDeleted() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        val product = Product("P-IN-CART", "Protected", "Test", 1.0, 2.0, 3.0, 1)
        database.productDao().insertProduct(product)
        repository.saveCartSnapshot("{\"version\":2,\"items\":[{\"barcode\":\"P-IN-CART\",\"quantity\":1}]}")

        var rejected = false
        try { repository.deleteProduct(product) } catch (_: IllegalArgumentException) { rejected = true }

        assertTrue(rejected)
        assertTrue(database.productDao().getProductByBarcode("P-IN-CART") != null)
    }

    @Test
    fun manualLoyaltyAdjustmentsCreateAuditEntries() = runBlocking {
        repository.setSessionUser(User(id = 1, name = "Owner", pinHash = "x", role = "OWNER"))
        repository.upsertLoyalty(CustomerLoyalty("01766666666", "Member"))
        repository.addLoyaltyPoints("01766666666", 10, 0.0, "Service recovery")
        assertTrue(repository.redeemLoyaltyPoints("01766666666", 4, "Gift redemption"))

        val records = database.loyaltyTransactionDao().getAll().first()
        assertEquals(2, records.size)
        assertTrue(records.any { it.reason == "Service recovery" && it.pointsChange == 10 })
        assertTrue(records.any { it.reason == "Gift redemption" && it.pointsChange == -4 })
    }

    @Test
    fun firstAccountMustBeOwner() = runBlocking {
        var rejected = false
        try {
            repository.createUser("Cashier", "2345", "CASHIER")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue(!repository.hasAnyUsers())
        repository.createUser("Owner", "1234", "OWNER")
        assertEquals("OWNER", repository.verifyPin("1234")?.role)
    }

    @Test
    fun updatingCashierRejectsPinAlreadyUsedByAnotherAccount() = runBlocking {
        repository.createUser("Owner", "1234", "OWNER")
        val firstCashier = repository.createUser("First", "2345", "CASHIER")
        val secondCashier = repository.createUser("Second", "3456", "CASHIER")

        var rejected = false
        try {
            repository.updateCashier(secondCashier, "Second", "2345")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(firstCashier, repository.verifyPin("2345")?.id)
        assertEquals(secondCashier, repository.verifyPin("3456")?.id)
    }

    @Test
    fun firstDueSaleRequiresOwnerOverrideAndKeepsCustomerMetricsInSync() = runBlocking {
        repository.createUser("Owner", "1234", "OWNER")
        repository.upsertCustomer(
            Customer(phone = "01777777777", name = "Credit Customer", creditLimit = 100.0)
        )
        val product = Product("P-CREDIT", "Credit item", "Test", 50.0, 120.0, 2.0, 1)
        database.productDao().insertProduct(product)

        var rejected = false
        try {
            repository.completeSale(
                120.0, 0.0, false, 120.0, "Due", "Credit Customer", "01777777777",
                0.0, null, null, null, listOf(product to 1.0)
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertTrue(database.saleDao().getAllSales().first().isEmpty())
        assertEquals(2.0, database.productDao().getProductByBarcode("P-CREDIT")!!.stockQty, 0.001)

        assertTrue(repository.authorizeCreditLimitOverride("1234", "01777777777"))
        val saleId = repository.completeSale(
            120.0, 0.0, false, 120.0, "Due", "Credit Customer", "01777777777",
            0.0, null, null, null, listOf(product to 1.0)
        )
        val due = database.customerDueDao().getCustomerDueByPhone("01777777777")!!
        assertEquals(120.0, due.outstandingAmount, 0.001)
        assertEquals(100.0, due.creditLimit, 0.001)
        assertEquals(120.0, database.customerDao().getCustomerByPhone("01777777777")!!.totalDue, 0.001)

        repository.processReturn(
            saleId,
            listOf(ReturnLine("P-CREDIT", "Credit item", 120.0, 1.0)),
            "Cash",
            null
        )
        val afterReturn = database.customerDao().getCustomerByPhone("01777777777")!!
        assertEquals(0.0, afterReturn.totalDue, 0.001)
        assertEquals(0.0, afterReturn.lifetimeSpent, 0.001)
        assertEquals(1, afterReturn.totalOrders)

        repository.deleteSale(saleId)
        val afterDelete = database.customerDao().getCustomerByPhone("01777777777")!!
        assertEquals(0, afterDelete.totalOrders)
        assertEquals(0.0, afterDelete.totalDue, 0.001)
    }

    @Test
    fun nonTaxableProductPreservesTaxableFalseAcrossBackupRestore() = runBlocking {
        val product = Product("CUSTOM-X", "Non-Taxable Item", "Test", 50.0, 100.0, 5.0, 1)
        database.productDao().insertProduct(product)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Cash",
            customerName = null,
            customerPhone = null,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(product, 1.0, null)),
            taxRate = 5.0,
            taxAmount = 0.0,
            nonTaxableBarcodes = setOf("CUSTOM-X")
        )

        val saleWithItemsBefore = database.saleDao().getSaleWithItemsById(saleId)!!
        val itemBefore = saleWithItemsBefore.items.single()
        assertEquals(false, itemBefore.taxable)

        val backup = File(context.cacheDir, "nontaxable-round-trip.json")
        assertEquals(true, repository.exportBackup(backup))
        database.clearAllTables()
        assertEquals(true, repository.importBackup(backup))

        val saleWithItemsAfter = database.saleDao().getSaleWithItemsById(saleId)!!
        val itemAfter = saleWithItemsAfter.items.single()
        assertEquals(false, itemAfter.taxable)
        backup.delete()
    }
}

