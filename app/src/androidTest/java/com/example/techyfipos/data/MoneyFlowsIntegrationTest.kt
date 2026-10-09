package com.example.techyfipos.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.techyfipos.data.db.*
import com.example.techyfipos.utils.PaymentMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyFlowsIntegrationTest {
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
    fun testA1_checkoutPreservesCustomerStoreCreditAndMergeCombinesCredit() = runBlocking {
        val customerPhone = "01700000001"
        val initialCredit = 500.0
        repository.upsertCustomer(
            Customer(
                phone = customerPhone,
                name = "John Doe",
                storeCredit = initialCredit,
                creditLimit = 1000.0
            )
        )

        val prod = Product(
            barcode = "BAR01",
            name = "Test Item",
            category = "General",
            purchasePrice = 50.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Cash",
            customerName = "John Doe",
            customerPhone = customerPhone,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null))
        )

        val fetchedCust = repository.getCustomerByPhone(customerPhone)
        assertEquals(initialCredit, fetchedCust?.storeCredit ?: 0.0, 0.001)

        val phoneB = "01700000002"
        repository.upsertCustomer(Customer(phone = phoneB, name = "Target Customer", storeCredit = 300.0))
        repository.mergeCustomers(masterPhone = phoneB, secondaryPhone = customerPhone)

        val mergedTarget = repository.getCustomerByPhone(phoneB)
        assertEquals(800.0, mergedTarget?.storeCredit ?: 0.0, 0.001)
    }

    @Test
    fun testA3_splitPaymentBypassesSingleCashFullAmountRequirement() = runBlocking {
        val prod = Product(
            barcode = "BAR03",
            name = "Split Item",
            category = "General",
            purchasePrice = 200.0,
            sellingPrice = 500.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 200.0),
                SplitPaymentEntry("bKash", 300.0, walletPhone = "01700000000")
            ),
            storeCreditApplied = 0.0,
            grandTotal = 500.0,
            paymentMethod = "Split"
        )

        val saleId = repository.completeSaleWithNotes(
            subtotal = 500.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 500.0,
            paymentMethod = "Split",
            customerName = null,
            customerPhone = null,
            amountPaid = 500.0,
            walletPhone = "01700000000",
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson
        )

        assertTrue(saleId > 0)
        val savedSale = database.saleDao().getSaleWithItemsById(saleId)?.sale
        assertEquals("Split", savedSale?.paymentMethod)
        assertEquals(splitJson, savedSale?.splitPaymentJson)
    }

    @Test
    fun testA4_splitPaymentIncludesStoreCreditRowToMatchGrandTotal() = runBlocking {
        val custPhone = "01700000004"
        repository.upsertCustomer(Customer(phone = custPhone, name = "Credit User", storeCredit = 300.0))

        val prod = Product(
            barcode = "BAR04",
            name = "Big Item",
            category = "General",
            purchasePrice = 400.0,
            sellingPrice = 1000.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 700.0),
                SplitPaymentEntry("Store Credit", 300.0)
            ),
            storeCreditApplied = 300.0,
            grandTotal = 1000.0,
            paymentMethod = "Split"
        )

        val saleId = repository.completeSaleWithNotes(
            subtotal = 1000.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 1000.0,
            paymentMethod = "Split",
            customerName = "Credit User",
            customerPhone = custPhone,
            amountPaid = 1000.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 300.0
        )

        val savedSale = database.saleDao().getSaleWithItemsById(saleId)?.sale
        val arr = JSONArray(savedSale?.splitPaymentJson ?: "[]")
        var sum = 0.0
        for (i in 0 until arr.length()) {
            sum += arr.getJSONObject(i).getDouble("amount")
        }
        assertEquals(1000.0, sum, 0.001)
    }

    @Test
    fun testA6_voucherRedemptionWithoutCustomerPhoneIsValid() = runBlocking {
        val voucher = StoreCreditVoucher(
            code = "WALKIN_VOUCH",
            initialAmount = 500.0,
            remainingBalance = 500.0,
            status = "ACTIVE",
            issuedAt = System.currentTimeMillis()
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val prod = Product(
            barcode = "BAR06",
            name = "Voucher Item",
            category = "General",
            purchasePrice = 100.0,
            sellingPrice = 200.0,
            stockQty = 5.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = 200.0,
            grandTotal = 200.0,
            paymentMethod = "Store Credit",
            redeemedVoucherCode = "WALKIN_VOUCH"
        )

        val saleId = repository.completeSaleWithNotes(
            subtotal = 200.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 200.0,
            paymentMethod = "Store Credit",
            customerName = null,
            customerPhone = null,
            amountPaid = 200.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 200.0,
            appliedVoucherCode = "WALKIN_VOUCH"
        )

        assertTrue(saleId > 0)
        val updatedVoucher = database.storeCreditVoucherDao().getVoucherByCode("WALKIN_VOUCH")
        assertEquals(300.0, updatedVoucher?.remainingBalance ?: 0.0, 0.001)
    }

    @Test
    fun testA7_voidSaleRestoresVoucherBalanceAndCustomerCredit() = runBlocking {
        val custPhone = "01700000007"
        repository.upsertCustomer(Customer(phone = custPhone, name = "Void Test", storeCredit = 200.0))

        val voucher = StoreCreditVoucher(
            code = "VOID_VOUCH",
            initialAmount = 300.0,
            remainingBalance = 300.0,
            status = "ACTIVE",
            issuedAt = System.currentTimeMillis()
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val prod = Product(
            barcode = "BAR07",
            name = "Item",
            category = "General",
            purchasePrice = 200.0,
            sellingPrice = 400.0,
            stockQty = 5.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Store Credit", 100.0),
                SplitPaymentEntry("Store Credit", 300.0)
            ),
            storeCreditApplied = 400.0,
            grandTotal = 400.0,
            paymentMethod = "Split",
            redeemedVoucherCode = "VOID_VOUCH"
        )

        val saleId = repository.completeSaleWithNotes(
            subtotal = 400.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 400.0,
            paymentMethod = "Split",
            customerName = "Void Test",
            customerPhone = custPhone,
            amountPaid = 400.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 400.0,
            appliedVoucherCode = "VOID_VOUCH"
        )

        var cust = repository.getCustomerByPhone(custPhone)
        assertEquals(100.0, cust?.storeCredit ?: 0.0, 0.001)
        var vouch = database.storeCreditVoucherDao().getVoucherByCode("VOID_VOUCH")
        assertEquals(0.0, vouch?.remainingBalance ?: 0.0, 0.001)

        repository.deleteSale(saleId = saleId, reason = "Customer request")

        cust = repository.getCustomerByPhone(custPhone)
        assertEquals(200.0, cust?.storeCredit ?: 0.0, 0.001)

        vouch = database.storeCreditVoucherDao().getVoucherByCode("VOID_VOUCH")
        assertEquals(300.0, vouch?.remainingBalance ?: 0.0, 0.001)
        assertEquals("ACTIVE", vouch?.status)
    }

    @Test
    fun testR1_phantomLedgerDebitPreventedOnVoucherRedemptionWithoutCustomerCredit() = runBlocking {
        val custPhone = "01700000008"
        repository.upsertCustomer(Customer(phone = custPhone, name = "Plain Voucher Cust", storeCredit = 0.0))

        val voucher = StoreCreditVoucher(
            code = "PLAIN_VOUCH",
            initialAmount = 500.0,
            remainingBalance = 500.0,
            status = "ACTIVE",
            issuedAt = System.currentTimeMillis()
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val prod = Product(
            barcode = "BAR08",
            name = "Item",
            category = "General",
            purchasePrice = 100.0,
            sellingPrice = 200.0,
            stockQty = 5.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = 200.0,
            grandTotal = 200.0,
            paymentMethod = "Store Credit",
            redeemedVoucherCode = "PLAIN_VOUCH"
        )

        repository.completeSaleWithNotes(
            subtotal = 200.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 200.0,
            paymentMethod = "Store Credit",
            customerName = "Plain Voucher Cust",
            customerPhone = custPhone,
            amountPaid = 200.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 200.0,
            appliedVoucherCode = "PLAIN_VOUCH"
        )

        val cust = repository.getCustomerByPhone(custPhone)
        assertEquals(0.0, cust?.storeCredit ?: 0.0, 0.001)

        val ledgerEntries = database.storeCreditLedgerDao().getLedgerForCustomerOnce(custPhone)
        val redeemEntries = ledgerEntries.filter { it.type == "SALE_REDEMPTION" }
        assertTrue("No customer store credit redeem entries should be created for plain voucher redemption", redeemEntries.isEmpty())
    }

    @Test
    fun testM10_returnWithCashRefundApportionsCreditAndCashWithoutOverRefunding() = runBlocking {
        val custPhone = "01700000010"
        repository.upsertCustomer(
            Customer(
                phone = custPhone,
                name = "M10 Cust",
                storeCredit = 100.0,
                creditLimit = 1000.0
            )
        )
        val prod = Product(
            barcode = "BAR10",
            name = "Item M10",
            category = "General",
            purchasePrice = 100.0,
            sellingPrice = 300.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 200.0),
                SplitPaymentEntry("Store Credit", 100.0)
            ),
            storeCreditApplied = 100.0,
            grandTotal = 300.0,
            paymentMethod = "Split"
        )

        val saleId = repository.completeSaleWithNotes(
            subtotal = 300.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 300.0,
            paymentMethod = "Split",
            customerName = "M10 Cust",
            customerPhone = custPhone,
            amountPaid = 200.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 100.0
        )

        val custAfterSale = repository.getCustomerByPhone(custPhone)
        assertEquals(0.0, custAfterSale?.storeCredit ?: 0.0, 0.001)

        repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "BAR10", quantity = 1.0, name = "Item M10", price = 300.0)),
            refundMethod = "Cash",
            note = "Return full",
            restock = true
        )

        val custAfterReturn = repository.getCustomerByPhone(custPhone)
        assertEquals(100.0, custAfterReturn?.storeCredit ?: 0.0, 0.001)

        val returns = database.returnDao().getReturnsForSaleOnce(saleId)
        assertEquals(1, returns.size)
        val ret = returns.first()
        assertEquals(300.0, ret.refundAmount, 0.001)
        assertEquals(200.0, ret.externalRefundAmount, 0.001)
        assertEquals(0.0, ret.dueAdjustmentAmount, 0.001)
    }

    @Test
    fun testM1_plainStoreCreditWithPartialCreditAndRemainderRowSucceedsInRepo() = runBlocking {
        val custPhone = "01700000020"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "M1 Cust", storeCredit = 100.0, creditLimit = 1000.0)
        )
        val prod = Product(barcode = "BAR_M1", name = "M1 Item", category = "General", purchasePrice = 100.0, sellingPrice = 300.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = 100.0,
            grandTotal = 300.0,
            paymentMethod = "Store Credit",
            amountPaid = 0.0
        )
        val methodToSave = PaymentMath.resolveMethodToSave(
            isSplitPayment = false,
            storeCreditApplied = 100.0,
            grandTotal = 300.0,
            paymentMethod = "Store Credit"
        )
        assertEquals("Split", methodToSave)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 300.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 300.0,
            paymentMethod = methodToSave,
            customerName = "M1 Cust",
            customerPhone = custPhone,
            amountPaid = 0.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 100.0
        )
        assertTrue(saleId > 0)
        val cust = repository.getCustomerByPhone(custPhone)
        assertEquals(0.0, cust?.storeCredit ?: 0.0, 0.001)
        val due = database.customerDueDao().getCustomerDueByPhone(custPhone)
        assertEquals(200.0, due?.outstandingAmount ?: 0.0, 0.001)
    }

    @Test
    fun testM5_scenario1_explicitScSplitRowPlusAutoCreditSucceedsInRepo() = runBlocking {
        val custPhone = "01700000021"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "M5-1 Cust", storeCredit = 150.0, creditLimit = 1000.0)
        )
        val prod = Product(barcode = "BAR_M5_1", name = "M5-1 Item", category = "General", purchasePrice = 100.0, sellingPrice = 300.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 150.0),
                SplitPaymentEntry("Store Credit", 50.0)
            ),
            storeCreditApplied = 100.0,
            grandTotal = 300.0,
            paymentMethod = "Split"
        )
        val methodToSave = PaymentMath.resolveMethodToSave(
            isSplitPayment = true,
            storeCreditApplied = 100.0,
            grandTotal = 300.0,
            paymentMethod = "Split"
        )
        val saleId = repository.completeSaleWithNotes(
            subtotal = 300.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 300.0,
            paymentMethod = methodToSave,
            customerName = "M5-1 Cust",
            customerPhone = custPhone,
            amountPaid = 150.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 100.0
        )
        assertTrue(saleId > 0)
    }

    @Test
    fun testM5_scenario2_cash250PlusSc50On200TotalSucceedsInRepoWithChange() = runBlocking {
        val custPhone = "01700000022"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "M5-2 Cust", storeCredit = 50.0, creditLimit = 1000.0)
        )
        val prod = Product(barcode = "BAR_M5_2", name = "M5-2 Item", category = "General", purchasePrice = 100.0, sellingPrice = 200.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = 50.0,
            grandTotal = 200.0,
            paymentMethod = "Cash",
            amountPaid = 250.0
        )
        val change = PaymentMath.calculateChange(
            grandTotal = 200.0,
            amountPaid = 250.0,
            paymentMethod = "Cash",
            splitJson = splitJson,
            storeCreditApplied = 50.0
        )
        assertEquals(100.0, change, 0.001)

        val methodToSave = PaymentMath.resolveMethodToSave(
            isSplitPayment = false,
            storeCreditApplied = 50.0,
            grandTotal = 200.0,
            paymentMethod = "Cash"
        )
        val saleId = repository.completeSaleWithNotes(
            subtotal = 200.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 200.0,
            paymentMethod = methodToSave,
            customerName = "M5-2 Cust",
            customerPhone = custPhone,
            amountPaid = 250.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 50.0
        )
        assertTrue(saleId > 0)
    }

    @Test
    fun testM5_scenario3_cardOverTenderIsRejectedAndValidElectronicPlusCashSucceeds() = runBlocking {
        val prod = Product(barcode = "BAR_M5_3", name = "M5-3 Item", category = "General", purchasePrice = 100.0, sellingPrice = 200.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        // Card over-tender is rejected
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PaymentMath.buildAuthoritativeSplitJson(
                isSplitPayment = true,
                splitPayments = listOf(
                    SplitPaymentEntry("Card", 250.0, cardLastFour = "4321"),
                    SplitPaymentEntry("Cash", 10.0)
                ),
                storeCreditApplied = 0.0,
                grandTotal = 200.0,
                paymentMethod = "Split"
            )
        }

        // Valid split: Card 150 + Cash 50
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Card", 150.0, cardLastFour = "4321"),
                SplitPaymentEntry("Cash", 50.0)
            ),
            storeCreditApplied = 0.0,
            grandTotal = 200.0,
            paymentMethod = "Split"
        )
        val methodToSave = PaymentMath.resolveMethodToSave(
            isSplitPayment = true,
            storeCreditApplied = 0.0,
            grandTotal = 200.0,
            paymentMethod = "Split"
        )
        val saleId = repository.completeSaleWithNotes(
            subtotal = 200.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 200.0,
            paymentMethod = methodToSave,
            customerName = null,
            customerPhone = null,
            amountPaid = 200.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = "4321",
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 0.0
        )
        assertTrue(saleId > 0)
    }

    @Test
    fun testM6_cash200AndDue200On300TotalComputes0ChangeAndRecords100DueInRepo() = runBlocking {
        val custPhone = "01700000023"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "M6 Cust", storeCredit = 0.0, creditLimit = 1000.0)
        )
        val prod = Product(barcode = "BAR_M6", name = "M6 Item", category = "General", purchasePrice = 100.0, sellingPrice = 300.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 200.0),
                SplitPaymentEntry("Due", 200.0)
            ),
            storeCreditApplied = 0.0,
            grandTotal = 300.0,
            paymentMethod = "Split"
        )
        val change = PaymentMath.calculateChange(
            grandTotal = 300.0,
            amountPaid = 200.0,
            paymentMethod = "Split",
            splitJson = splitJson
        )
        assertEquals(0.0, change, 0.001)

        val methodToSave = PaymentMath.resolveMethodToSave(
            isSplitPayment = true,
            storeCreditApplied = 0.0,
            grandTotal = 300.0,
            paymentMethod = "Split"
        )
        val saleId = repository.completeSaleWithNotes(
            subtotal = 300.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 300.0,
            paymentMethod = methodToSave,
            customerName = "M6 Cust",
            customerPhone = custPhone,
            amountPaid = 200.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 0.0
        )
        assertTrue(saleId > 0)
        val due = database.customerDueDao().getCustomerDueByPhone(custPhone)
        assertEquals(100.0, due?.outstandingAmount ?: 0.0, 0.001)
    }

    @Test
    fun testM3_deleteSale_restoresExactVoucherShare_notInitialMinusRemaining_andWritesLedgerRow() = runBlocking {
        val custPhone = "01700000031"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "M3 Cust", storeCredit = 70.0, creditLimit = 500.0)
        )
        // Voucher V1: initial 100, was used 50 elsewhere, remaining 50.
        // In this sale, customer uses 30 from voucher and 70 from personal store credit (total SC = 100).
        val voucher = StoreCreditVoucher(
            code = "VOUCH-M3",
            initialAmount = 100.0,
            remainingBalance = 50.0,
            status = "ACTIVE"
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val prod = Product(barcode = "BAR_M3", name = "M3 Item", category = "General", purchasePrice = 50.0, sellingPrice = 100.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val splitJson = JSONArray().apply {
            put(JSONObject().apply {
                put("method", "Store Credit")
                put("amount", 30.0)
                put("voucherCode", "VOUCH-M3")
            })
            put(JSONObject().apply {
                put("method", "Store Credit")
                put("amount", 70.0)
            })
        }.toString()

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Store Credit",
            customerName = "M3 Cust",
            customerPhone = custPhone,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 100.0
        )
        assertTrue(saleId > 0)

        // After sale: voucher has 20.0 remaining, customer has 0.0 SC.
        val voucherAfterSale = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-M3")
        assertEquals(20.0, voucherAfterSale?.remainingBalance ?: 0.0, 0.001)
        val custAfterSale = repository.getCustomerByPhone(custPhone)
        assertEquals(0.0, custAfterSale?.storeCredit ?: 0.0, 0.001)

        // Now void/delete the sale
        repository.deleteSale(saleId)

        // Exact restore: Voucher was used for 30 in this sale, so balance becomes 20 + 30 = 50.
        // Customer was used for 70, so balance becomes 0 + 70 = 70.
        val voucherAfterVoid = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-M3")
        val custAfterVoid = repository.getCustomerByPhone(custPhone)
        val ledger = database.storeCreditLedgerDao().getAllEntries()

        assertEquals("Voucher must only restore 30 (total 50), not 80 (total 100)", 50.0, voucherAfterVoid?.remainingBalance ?: 0.0, 0.001)
        assertEquals("Customer must restore 70, not 20", 70.0, custAfterVoid?.storeCredit ?: 0.0, 0.001)
        assertTrue("Voucher restore ledger row must be recorded", ledger.any { it.type == "VOUCHER_RESTORE" && it.voucherCode == "VOUCH-M3" })
    }

    @Test
    fun testM3_deleteSale_cancelledVoucherDoesNotAbsorbRestoredBalance() = runBlocking {
        val custPhone = "01700000032"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "M3 Cancelled Cust", storeCredit = 0.0, creditLimit = 500.0)
        )
        val voucher = StoreCreditVoucher(
            code = "VOUCH-CANCELLED",
            initialAmount = 100.0,
            remainingBalance = 50.0,
            status = "ACTIVE"
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val prod = Product(barcode = "BAR_M3_2", name = "M3 Item 2", category = "General", purchasePrice = 50.0, sellingPrice = 50.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val splitJson = JSONArray().apply {
            put(JSONObject().apply {
                put("method", "Store Credit")
                put("amount", 50.0)
                put("voucherCode", "VOUCH-CANCELLED")
            })
        }.toString()

        val saleId = repository.completeSaleWithNotes(
            subtotal = 50.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 50.0,
            paymentMethod = "Store Credit",
            customerName = "M3 Cancelled Cust",
            customerPhone = custPhone,
            amountPaid = 50.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 50.0
        )
        assertTrue(saleId > 0)

        // Cancel the voucher while sale is active
        database.storeCreditVoucherDao().updateVoucherBalance("VOUCH-CANCELLED", 0.0, "CANCELLED")

        // Void the sale
        repository.deleteSale(saleId)

        val voucherAfterVoid = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-CANCELLED")
        val custAfterVoid = repository.getCustomerByPhone(custPhone)

        // Cancelled voucher must NOT absorb balance
        assertEquals("Cancelled voucher must not absorb restored balance", 0.0, voucherAfterVoid?.remainingBalance ?: 0.0, 0.001)
        assertEquals("Customer must receive the credit since voucher was cancelled", 50.0, custAfterVoid?.storeCredit ?: 0.0, 0.001)
    }

    @Test
    fun testM14_nonOwnerCannotOverwriteDueCreditLimitOnCustomerEdit() = runBlocking {
        val custPhone = "01700000033"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "M14 Cust", storeCredit = 0.0, creditLimit = 0.0)
        )
        // Suppose CustomerDue has a creditLimit set by owner
        val due = database.customerDueDao().getCustomerDueByPhone(custPhone)
        database.customerDueDao().upsertCustomerDue(
            (due ?: CustomerDue(customerPhone = custPhone, customerName = "M14 Cust", outstandingAmount = 0.0))
                .copy(creditLimit = 5000.0)
        )

        // Switch session to non-owner cashier with CUSTOMERS_EDIT permission
        repository.setSessionUser(User(id = 2, name = "Cashier", pinHash = "p", role = "CASHIER", customPermissions = "[\"${AppPermissions.CUSTOMERS_EDIT}\"]"))

        // Non-owner edits customer details (name change, creditLimit = 0.0 in DTO)
        val existingCust = repository.getCustomerByPhone(custPhone)!!
        repository.upsertCustomer(existingCust.copy(name = "M14 Cust Renamed", creditLimit = 0.0))

        val dueAfterEdit = database.customerDueDao().getCustomerDueByPhone(custPhone)
        assertEquals("CustomerDue creditLimit must be preserved at 5000.0", 5000.0, dueAfterEdit?.creditLimit ?: 0.0, 0.001)
    }

    @Test
    fun testX9_mergeCustomers_failsWhenCustomerMissing() = runBlocking {
        val custPhone = "01700000034"
        repository.upsertCustomer(
            Customer(phone = custPhone, name = "Master Cust")
        )
        val missingPhone = "01700000099"
        val result = repository.mergeCustomers(custPhone, missingPhone)
        org.junit.Assert.assertFalse("mergeCustomers must return false when secondary customer is missing", result)
    }

    private suspend fun importJsonBackup(json: JSONObject, mode: BackupImportMode): Boolean {
        val file = java.io.File.createTempFile("test_backup", ".json")
        try {
            file.writeText(json.toString())
            return repository.importBackup(file, mode)
        } finally {
            file.delete()
        }
    }

    @Test
    fun testN2_mergeBackupDoesNotAddVoucherRestoreLedgerDeltaToCustomerStoreCredit() = runBlocking {
        val phone = "01700000070"
        repository.upsertCustomer(Customer(phone = phone, name = "N2 Cust", storeCredit = 100.0))

        val backupJson = JSONObject().apply {
            put("storeCreditLedger", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 101L)
                    put("customerPhone", phone)
                    put("deltaAmount", 50.0)
                    put("balanceAfter", 50.0)
                    put("type", "VOUCHER_RESTORE")
                    put("voucherCode", "VOUCH-N2")
                    put("timestamp", 1000L)
                })
            })
        }

        importJsonBackup(backupJson, BackupImportMode.MERGE)
        val custAfter = repository.getCustomerByPhone(phone)!!
        assertEquals("Customer store credit must not absorb voucher restore delta", 100.0, custAfter.storeCredit, 0.001)
    }

    @Test
    fun testN3_mergeBackupUpdatesCustomerTotalDueWhenDueRowIsNewAndCustomerAlreadyExists() = runBlocking {
        val phone = "01700000071"
        repository.upsertCustomer(Customer(phone = phone, name = "N3 Cust", totalDue = 0.0))

        val backupJson = JSONObject().apply {
            put("dues", JSONArray().apply {
                put(JSONObject().apply {
                    put("customerPhone", phone)
                    put("customerName", "N3 Cust")
                    put("outstandingAmount", 500.0)
                    put("creditLimit", 1000.0)
                })
            })
        }

        importJsonBackup(backupJson, BackupImportMode.MERGE)
        val custAfter = repository.getCustomerByPhone(phone)!!
        assertEquals("Customer totalDue must be updated to 500.0 on merge", 500.0, custAfter.totalDue, 0.001)
    }

    @Test
    fun testN4_mergeCustomers_succeedsForDueOnlyDebtorWithNoCustomerRow() = runBlocking {
        val masterPhone = "01700000072"
        val secPhone = "01700000073"
        repository.upsertCustomer(Customer(phone = masterPhone, name = "Master", totalDue = 100.0))

        // Create secondary due account ONLY in customer_dues (due-only debtor)
        database.customerDueDao().upsertCustomerDue(
            CustomerDue(customerPhone = secPhone, customerName = "Due Only Debtor", outstandingAmount = 250.0)
        )

        val ok = repository.mergeCustomers(masterPhone, secPhone)
        assertTrue("mergeCustomers must succeed when secondary is a due-only debtor", ok)

        val masterAfter = repository.getCustomerByPhone(masterPhone)!!
        assertEquals("Combined due must be 350.0", 350.0, masterAfter.totalDue, 0.001)
    }

    @Test
    fun testN5_pureStoreCreditSaleDoesNotPersistSplitPaymentJson() = runBlocking {
        val prod = Product(barcode = "N5_PROD", name = "N5 Item", category = "General", purchasePrice = 50.0, sellingPrice = 300.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)
        val phone = "01700000075"
        repository.upsertCustomer(Customer(phone = phone, name = "N5 Cust", storeCredit = 500.0))

        val saleId = repository.completeSaleWithNotes(
            subtotal = 300.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 300.0,
            paymentMethod = "Store Credit",
            customerName = "N5 Cust",
            customerPhone = phone,
            amountPaid = 300.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 300.0
        )

        val savedSale = database.saleDao().getSaleWithItemsById(saleId)?.sale!!
        assertEquals("Store Credit", savedSale.paymentMethod)
        org.junit.Assert.assertNull("Non-split Store Credit sale must not save splitPaymentJson", savedSale.splitPaymentJson)
    }

    @Test
    fun testM3_voidSaleApportionsVoucherAndCustomerPortionsCorrectly() = runBlocking {
        val prod = Product(barcode = "M3_PROD", name = "M3 Item", category = "General", purchasePrice = 50.0, sellingPrice = 100.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)
        val phone = "01700000076"
        repository.upsertCustomer(Customer(phone = phone, name = "M3 Cust", storeCredit = 40.0))

        // Create voucher with 100 initial, 60 remaining
        val voucher = StoreCreditVoucher(code = "VOUCH-M3", initialAmount = 100.0, remainingBalance = 60.0, status = "ACTIVE")
        database.storeCreditVoucherDao().insertVoucher(voucher)

        // Split sale with 60 from voucher and 40 from customer
        val splitJson = """[{"method":"Store Credit","amount":100.0,"voucherCode":"VOUCH-M3","voucherAmount":60.0,"customerCreditAmount":40.0}]"""
        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Split",
            customerName = "M3 Cust",
            customerPhone = phone,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 100.0
        )

        // Void the sale
        repository.deleteSale(saleId)

        val voucherAfter = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-M3")!!
        val custAfter = repository.getCustomerByPhone(phone)!!
        assertEquals("Voucher must be restored by 60.0 (back to 60.0)", 60.0, voucherAfter.remainingBalance, 0.001)
        assertEquals("Customer must be restored by 40.0 (back to 40.0)", 40.0, custAfter.storeCredit, 0.001)
    }

    @Test
    fun testM3_repeatedPartialReturnsTrackVoucherRestoredSoFar() = runBlocking {
        val prod = Product(barcode = "M3_PROD2", name = "M3 Item 2", category = "General", purchasePrice = 50.0, sellingPrice = 100.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)
        val phone = "01700000077"
        repository.upsertCustomer(Customer(phone = phone, name = "M3 Cust 2", storeCredit = 40.0))

        val voucher = StoreCreditVoucher(code = "VOUCH-M3-2", initialAmount = 100.0, remainingBalance = 60.0, status = "ACTIVE")
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val splitJson = """[{"method":"Store Credit","amount":100.0,"voucherCode":"VOUCH-M3-2","voucherAmount":60.0,"customerCreditAmount":40.0}]"""
        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Split",
            customerName = "M3 Cust 2",
            customerPhone = phone,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            splitPaymentJson = splitJson,
            storeCreditApplied = 100.0
        )

        // 1st partial return: 40 refund
        repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "M3_PROD2", quantity = 0.4, name = "M3 Item 2", price = 100.0)),
            refundMethod = "Store Credit",
            note = "Return 1",
            restock = true
        )

        val voucherAfterRet1 = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-M3-2")!!
        val custAfterRet1 = repository.getCustomerByPhone(phone)!!
        assertEquals("Voucher restored by 40 (balance 40)", 40.0, voucherAfterRet1.remainingBalance, 0.001)
        assertEquals("Customer credit still 0 (no customer portion yet)", 0.0, custAfterRet1.storeCredit, 0.001)

        // 2nd partial return: 40 refund (total 80 returned). Remaining voucher portion was 20 (60 - 40).
        // So voucher should only absorb 20 (balance 60), and customer gets 20!
        repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "M3_PROD2", quantity = 0.4, name = "M3 Item 2", price = 100.0)),
            refundMethod = "Store Credit",
            note = "Return 2",
            restock = true
        )

        val voucherAfterRet2 = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-M3-2")!!
        val custAfterRet2 = repository.getCustomerByPhone(phone)!!
        assertEquals("Voucher capped at 60 (initial spent was 60)", 60.0, voucherAfterRet2.remainingBalance, 0.001)
        assertEquals("Customer must receive remaining 20", 20.0, custAfterRet2.storeCredit, 0.001)
    }

    @Test
    fun testM14_nonOwnerWithDuesAddDueCannotOverwriteExistingCreditLimitInUpsertCustomerDue() = runBlocking {
        val phone = "01700000080"
        repository.upsertCustomer(Customer(phone = phone, name = "M14-2 Cust", creditLimit = 2000.0))
        val due = database.customerDueDao().getCustomerDueByPhone(phone)
        assertEquals(2000.0, due?.creditLimit ?: 0.0, 0.001)

        // Non-owner cashier with DUES_ADD_DUE permission
        repository.setSessionUser(User(id = 3, name = "Cashier 2", pinHash = "p", role = "CASHIER", customPermissions = "[\"${AppPermissions.DUES_ADD_DUE}\"]"))
        // Tries to set creditLimit to 10000
        repository.upsertCustomerDue(due!!.copy(creditLimit = 10000.0))

        val dueAfter = database.customerDueDao().getCustomerDueByPhone(phone)
        assertEquals("Non-owner must not be able to change credit limit to 10000", 2000.0, dueAfter?.creditLimit ?: 0.0, 0.001)
    }

    @Test
    fun testM14_nonOwnerWithDuesAddDueCannotSetCreditLimitInAddManualDueCharge() = runBlocking {
        val phone = "01700000081"
        repository.upsertCustomer(Customer(phone = phone, name = "M14-3 Cust", creditLimit = 1500.0))

        // Non-owner cashier with DUES_ADD_DUE permission
        repository.setSessionUser(User(id = 3, name = "Cashier 2", pinHash = "p", role = "CASHIER", customPermissions = "[\"${AppPermissions.DUES_ADD_DUE}\"]"))
        repository.addManualDueCharge(phoneRaw = phone, customerName = "M14-3 Cust", amount = 100.0, creditLimit = 9999.0)

        val dueAfter = database.customerDueDao().getCustomerDueByPhone(phone)
        assertEquals("Non-owner must not be able to set credit limit to 9999", 1500.0, dueAfter?.creditLimit ?: 0.0, 0.001)
    }

    @Test
    fun testM14_ownerBulkTagAssignPreservesCustomerDueCreditLimitWhenCustomerRowHasZero() = runBlocking {
        val phone = "01700000082"
        // Customer has creditLimit 0 in Customer table, but 5000 in CustomerDue table
        database.customerDao().upsertCustomer(Customer(phone = phone, name = "M14-4 Cust", creditLimit = 0.0))
        database.customerDueDao().upsertCustomerDue(CustomerDue(customerPhone = phone, customerName = "M14-4 Cust", outstandingAmount = 0.0, creditLimit = 5000.0))

        // Owner runs bulk tag assignment (fetches customer via getCustomerByPhone and calls upsertCustomer)
        val cust = repository.getCustomerByPhone(phone)!!
        repository.upsertCustomer(cust.copy(tag = "VIP"))

        val dueAfter = database.customerDueDao().getCustomerDueByPhone(phone)
        assertEquals("CustomerDue creditLimit must be preserved at 5000.0 after bulk tag assign", 5000.0, dueAfter?.creditLimit ?: 0.0, 0.001)
    }

    @Test
    fun testM14_migrateCustomerPhone_requiresCustomersEditPermission() = runBlocking {
        val phone = "01700000083"
        repository.upsertCustomer(Customer(phone = phone, name = "M14-5 Cust"))
        // Switch to cashier without CUSTOMERS_EDIT
        repository.setSessionUser(User(id = 4, name = "Restricted Cashier", pinHash = "p", role = "CASHIER", customPermissions = "[]"))
        var thrown = false
        try {
            repository.migrateCustomerPhone(oldPhone = phone, newPhone = "01700000084")
        } catch (e: com.example.techyfipos.data.DataRepository.UnauthorizedException) {
            thrown = true
        } catch (e: SecurityException) {
            thrown = true
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue("migrateCustomerPhone must require CUSTOMERS_EDIT permission", thrown)
        Unit
    }

    @Test
    fun testB7_saleStoresIsTaxInclusiveFlagCorrectly() = runBlocking {
        val prod = Product(barcode = "BAR_B7", name = "B7 Item", category = "General", purchasePrice = 100.0, sellingPrice = 115.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 115.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 115.0,
            paymentMethod = "Cash",
            customerName = null,
            customerPhone = null,
            amountPaid = 115.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            taxRate = 15.0,
            taxAmount = 15.0,
            isTaxInclusive = true
        )

        val swi = database.saleDao().getSaleWithItemsById(saleId)
        org.junit.Assert.assertNotNull(swi)
        org.junit.Assert.assertTrue("Sale must store isTaxInclusive as true", swi!!.sale.isTaxInclusive)
    }

    @Test
    fun testB5_returnWithStoreCreditRefund_deductsFromStoreCreditInPaymentBreakdown() = runBlocking {
        val prod = Product(barcode = "BAR_B5", name = "B5 Item", category = "General", purchasePrice = 100.0, sellingPrice = 300.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 300.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 300.0,
            paymentMethod = "Cash",
            customerName = null,
            customerPhone = null,
            amountPaid = 300.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null))
        )

        repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "BAR_B5", quantity = 1.0, name = "B5 Item", price = 300.0)),
            refundMethod = "Store Credit",
            note = "Return for SC",
            restock = true
        )

        val sales = listOf(
            PaymentMath.SimpleSalePayment(
                saleId = saleId,
                paymentMethod = "Cash",
                grandTotal = 300.0,
                amountPaid = 300.0
            )
        )
        val returns = database.returnDao().getReturnsForSaleOnce(saleId).map {
            PaymentMath.SimpleReturnPayment(
                saleId = it.saleId,
                refundMethod = it.refundMethod,
                refundAmount = it.refundAmount,
                externalRefundAmount = it.externalRefundAmount,
                dueAdjustmentAmount = it.dueAdjustmentAmount
            )
        }
        val breakdown = PaymentMath.aggregatePaymentBreakdown(sales, returns)
        val scAmount = breakdown["Store Credit"] ?: 0.0
        org.junit.Assert.assertEquals(-300.0, scAmount, 0.001)
        val cashAmount = breakdown["Cash"] ?: 0.0
        org.junit.Assert.assertEquals(300.0, cashAmount, 0.001)
    }

    @Test
    fun testR1_importBackupLowercasesProductBarcodes() = runBlocking {
        val backupJson = JSONObject().apply {
            put("products", JSONArray().apply {
                put(JSONObject().apply {
                    put("barcode", "UPPER-BARCODE-R1")
                    put("name", "Upper Item")
                    put("price", 150.0)
                    put("sellingPrice", 150.0)
                    put("cost", 100.0)
                    put("purchasePrice", 100.0)
                    put("stock", 20.0)
                    put("stockQty", 20.0)
                })
            })
        }

        importJsonBackup(backupJson, BackupImportMode.MERGE)
        val prod = database.productDao().getProductByBarcode("upper-barcode-r1")
        org.junit.Assert.assertNotNull("Product barcode must be lowercased upon backup import", prod)
        assertEquals("upper-barcode-r1", prod!!.barcode)
    }

    @Test
    fun testM3_pureStoreCreditSaleWithVoucherRecordsColumnsOnSaleAndRestoresOnVoid() = runBlocking {
        val prod = Product(barcode = "m3_pure_prod", name = "M3 Pure Item", category = "General", purchasePrice = 50.0, sellingPrice = 100.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)
        val phone = "01700000078"
        repository.upsertCustomer(Customer(phone = phone, name = "M3 Pure Cust", storeCredit = 40.0))

        val voucher = StoreCreditVoucher(code = "VOUCH-M3-PURE", initialAmount = 100.0, remainingBalance = 60.0, status = "ACTIVE")
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Store Credit",
            customerName = "M3 Pure Cust",
            customerPhone = phone,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 100.0,
            appliedVoucherCode = "VOUCH-M3-PURE"
        )

        val sale = database.saleDao().getSaleWithItemsById(saleId)?.sale!!
        assertEquals("VOUCH-M3-PURE", sale.voucherCode)
        assertEquals(60.0, sale.voucherAmount, 0.001)
        assertEquals(40.0, sale.customerCreditAmount, 0.001)
        org.junit.Assert.assertNull("Pure Store Credit must have null splitPaymentJson (N-5)", sale.splitPaymentJson)

        val voucherMid = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-M3-PURE")!!
        val custMid = repository.getCustomerByPhone(phone)!!
        assertEquals(0.0, voucherMid.remainingBalance, 0.001)
        assertEquals(0.0, custMid.storeCredit, 0.001)

        repository.deleteSale(saleId)

        val voucherAfter = database.storeCreditVoucherDao().getVoucherByCode("VOUCH-M3-PURE")!!
        val custAfter = repository.getCustomerByPhone(phone)!!
        assertEquals(60.0, voucherAfter.remainingBalance, 0.001)
        assertEquals(40.0, custAfter.storeCredit, 0.001)
    }

    @Test
    fun testN1_walkInVoucherReturn_voucherShareGoesBackToVoucherOnly() = runBlocking {
        val vCode = "V-WALK-N1"
        database.storeCreditVoucherDao().insertVoucher(
            StoreCreditVoucher(
                code = vCode,
                initialAmount = 100.0,
                remainingBalance = 100.0,
                customerPhone = null,
                status = "ACTIVE"
            )
        )

        val prod = Product(
            barcode = "P-N1-WALK",
            name = "Item N1",
            category = "General",
            purchasePrice = 40.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Store Credit",
            customerName = "Walk-in",
            customerPhone = null,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 100.0,
            appliedVoucherCode = vCode
        )

        val vUsed = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(0.0, vUsed.remainingBalance, 0.001)

        val walkinPhone = "01799998888"
        repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "P-N1-WALK", quantity = 1.0, name = "Item N1", price = 100.0)),
            refundMethod = "Store Credit",
            note = "Return N1 test",
            restock = true,
            walkinPhone = walkinPhone,
            walkinName = "Walkin Registered",
            issueVoucher = false
        )

        val vRestored = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(100.0, vRestored.remainingBalance, 0.001)

        // N-1: The walk-in customer must NOT get an extra 100 credit. Voucher share goes to voucher only.
        val walkinCust = repository.getCustomerByPhone(walkinPhone)
        assertEquals(0.0, walkinCust?.storeCredit ?: 0.0, 0.001)
    }

    @Test
    fun testN5_expiredVoucherOnWalkInSale_replacesWithFreshVoucherOnVoid() = runBlocking {
        val vCode = "V-EXP-N5"
        database.storeCreditVoucherDao().insertVoucher(
            StoreCreditVoucher(
                code = vCode,
                initialAmount = 100.0,
                remainingBalance = 100.0,
                customerPhone = null,
                expiresAt = System.currentTimeMillis() + 50000L,
                status = "ACTIVE"
            )
        )

        val prod = Product(
            barcode = "P-N5-EXP",
            name = "Item N5",
            category = "General",
            purchasePrice = 30.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Store Credit",
            customerName = "Walk-in Exp",
            customerPhone = null,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 100.0,
            appliedVoucherCode = vCode
        )

        // Voucher expires after sale but before void:
        database.storeCreditVoucherDao().updateVoucherBalance(vCode, 0.0, "EXPIRED")

        repository.deleteSale(saleId)

        // N-5: Expired voucher share on walk-in sale must NOT be lost on void.
        // It must issue a fresh active voucher replacement.
        val allVouchers = database.storeCreditVoucherDao().getAllVouchersOnce().filter { it.code != vCode }
        assertTrue("A fresh replacement voucher must be issued for expired walk-in voucher", allVouchers.isNotEmpty())
        val freshVoucher = allVouchers.first()
        assertEquals(100.0, freshVoucher.remainingBalance, 0.001)
        assertEquals("ACTIVE", freshVoucher.status)
    }


    @Test
    fun testN5_expiredVoucherWithCustomerPhone_voidCreditsCustomerExactlyOnce() = runBlocking {
        // Sale 80 = expired voucher 30 + customer credit 50. Voiding must give the customer 80 back, not 110.
        val custPhone = "01700000051"
        repository.upsertCustomer(Customer(phone = custPhone, name = "Expired Double", storeCredit = 50.0))
        val vCode = "V-EXP-N5-PHONE"
        database.storeCreditVoucherDao().insertVoucher(
            StoreCreditVoucher(
                code = vCode,
                initialAmount = 30.0,
                remainingBalance = 30.0,
                customerPhone = null,
                expiresAt = System.currentTimeMillis() + 50000L,
                status = "ACTIVE"
            )
        )
        val prod = Product(
            barcode = "p-n5-phone",
            name = "Item N5 Phone",
            category = "General",
            purchasePrice = 30.0,
            sellingPrice = 80.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 80.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 80.0,
            paymentMethod = "Store Credit",
            customerName = "Expired Double",
            customerPhone = custPhone,
            amountPaid = 80.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 80.0,
            appliedVoucherCode = vCode
        )
        assertEquals(0.0, repository.getCustomerByPhone(custPhone)!!.storeCredit, 0.001)

        database.storeCreditVoucherDao().updateVoucherBalance(vCode, 0.0, "EXPIRED")
        repository.deleteSale(saleId)

        assertEquals(
            "Customer must get back exactly the 80 of store credit used (voucher share redirected once)",
            80.0, repository.getCustomerByPhone(custPhone)!!.storeCredit, 0.001
        )
        assertEquals(0.0, database.storeCreditVoucherDao().getVoucherByCode(vCode)!!.remainingBalance, 0.001)
    }

    @Test
    fun testN5_expiredVoucherOwnedByCustomerOnWalkInSale_voidCreditsVoucherOwner() = runBlocking {
        // Walk-in sale (no phone) paid by a voucher that belongs to a known customer. Once the voucher has
        // expired, voiding must credit the voucher owner instead of dropping the share.
        val ownerPhone = "01700000052"
        repository.upsertCustomer(Customer(phone = ownerPhone, name = "Voucher Owner", storeCredit = 0.0))
        val vCode = "V-EXP-N5-OWNER"
        database.storeCreditVoucherDao().insertVoucher(
            StoreCreditVoucher(
                code = vCode,
                initialAmount = 100.0,
                remainingBalance = 100.0,
                customerPhone = ownerPhone,
                expiresAt = System.currentTimeMillis() + 50000L,
                status = "ACTIVE"
            )
        )
        val prod = Product(
            barcode = "p-n5-owner",
            name = "Item N5 Owner",
            category = "General",
            purchasePrice = 30.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Store Credit",
            customerName = "Walk-in",
            customerPhone = null,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 100.0,
            appliedVoucherCode = vCode
        )

        database.storeCreditVoucherDao().updateVoucherBalance(vCode, 0.0, "EXPIRED")
        repository.deleteSale(saleId)

        assertEquals(
            "Expired voucher share must be credited to the voucher owner, not lost",
            100.0, repository.getCustomerByPhone(ownerPhone)!!.storeCredit, 0.001
        )
    }

    @Test
    fun testT4_voucherUsedAcrossMultipleSales_voidRestoresEachSalePortion() = runBlocking {
        val prod = Product(
            barcode = "p-t4-multi",
            name = "Item T4 Multi",
            category = "General",
            purchasePrice = 30.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val vCode = "VOUCH-T4-MULTI"
        val voucher = StoreCreditVoucher(
            code = vCode,
            initialAmount = 150.0,
            remainingBalance = 150.0,
            status = "ACTIVE"
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        // Sale 1 uses 60.0 of voucher
        val sale1Id = repository.completeSaleWithNotes(
            subtotal = 60.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 60.0,
            paymentMethod = "Store Credit",
            customerName = "Walk-in 1",
            customerPhone = null,
            amountPaid = 60.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 0.6, null)),
            storeCreditApplied = 60.0,
            appliedVoucherCode = vCode
        )
        val vAfterSale1 = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(90.0, vAfterSale1.remainingBalance, 0.001)

        // Sale 2 uses 50.0 of voucher
        val sale2Id = repository.completeSaleWithNotes(
            subtotal = 50.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 50.0,
            paymentMethod = "Store Credit",
            customerName = "Walk-in 2",
            customerPhone = null,
            amountPaid = 50.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 0.5, null)),
            storeCreditApplied = 50.0,
            appliedVoucherCode = vCode
        )
        val vAfterSale2 = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(40.0, vAfterSale2.remainingBalance, 0.001)

        // Void Sale 2: voucher restored by 50.0 (40 + 50 = 90.0)
        repository.deleteSale(sale2Id)
        val vAfterVoid2 = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(90.0, vAfterVoid2.remainingBalance, 0.001)

        // Void Sale 1: voucher restored by 60.0 (90 + 60 = 150.0)
        repository.deleteSale(sale1Id)
        val vAfterVoid1 = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(150.0, vAfterVoid1.remainingBalance, 0.001)
    }

    @Test
    fun testT4_walkInFullyVoucherPaidSale_voidRestoresVoucherBalance() = runBlocking {
        val prod = Product(
            barcode = "p-t4-void",
            name = "Item T4 Void",
            category = "General",
            purchasePrice = 30.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val vCode = "VOUCH-T4-VOID"
        val voucher = StoreCreditVoucher(
            code = vCode,
            initialAmount = 100.0,
            remainingBalance = 100.0,
            status = "ACTIVE"
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Store Credit",
            customerName = "Walk-in",
            customerPhone = null,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 100.0,
            appliedVoucherCode = vCode
        )
        val vAfterSale = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(0.0, vAfterSale.remainingBalance, 0.001)

        // Void the walk-in sale: full voucher balance (100.0) is restored
        repository.deleteSale(saleId)
        val vAfterVoid = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(100.0, vAfterVoid.remainingBalance, 0.001)
    }

    @Test
    fun testT4_walkInFullyVoucherPaidSale_partialAndFullReturnsRestoreVoucherBalance() = runBlocking {
        val prod = Product(
            barcode = "p-t4-ret",
            name = "Item T4 Return",
            category = "General",
            purchasePrice = 30.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 1
        )
        database.productDao().insertProduct(prod)

        val vCode = "VOUCH-T4-RET"
        val voucher = StoreCreditVoucher(
            code = vCode,
            initialAmount = 100.0,
            remainingBalance = 100.0,
            status = "ACTIVE"
        )
        database.storeCreditVoucherDao().insertVoucher(voucher)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            paymentMethod = "Store Credit",
            customerName = "Walk-in",
            customerPhone = null,
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = 100.0,
            appliedVoucherCode = vCode
        )
        val vAfterSale = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(0.0, vAfterSale.remainingBalance, 0.001)

        // 1st partial return: 40.0 -> voucher restored by 40.0
        repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "p-t4-ret", quantity = 0.4, name = "Item T4 Return", price = 100.0)),
            refundMethod = "Store Credit",
            note = "1st Return",
            restock = true
        )
        val vAfterRet1 = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(40.0, vAfterRet1.remainingBalance, 0.001)

        // 2nd partial return: 60.0 -> remaining 60.0 restored to voucher (back to 100.0)
        repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "p-t4-ret", quantity = 0.6, name = "Item T4 Return", price = 100.0)),
            refundMethod = "Store Credit",
            note = "2nd Return",
            restock = true
        )
        val vAfterRet2 = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        assertEquals(100.0, vAfterRet2.remainingBalance, 0.001)
    }

    @Test
    fun testN4_legacyPreV37Sale_voidAccuratelyAttributesVoucherAndCustomerCredit() = runBlocking {
        val phone = "01788888801"
        repository.upsertCustomer(Customer(phone = phone, name = "Legacy N4 Cust", storeCredit = 0.0))
        val vCode = "SC-N4-LEGACY"
        database.storeCreditVoucherDao().insertVoucher(
            StoreCreditVoucher(
                code = vCode,
                initialAmount = 100.0,
                remainingBalance = 70.0, // 30 spent
                customerPhone = null,
                status = "ACTIVE"
            )
        )

        val prod = Product(barcode = "p-n4", name = "N4 Item", category = "General", purchasePrice = 40.0, sellingPrice = 80.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        // Simulate legacy sale: voucherAmount=0.0 and customerCreditAmount=0.0 in DB
        val saleId = database.saleDao().insertSale(
            Sale(
                id = 9401,
                timestamp = System.currentTimeMillis(),
                subtotal = 80.0,
                discountAmount = 0.0,
                isPercentageDiscount = false,
                grandTotal = 80.0,
                paymentMethod = "Store Credit",
                customerName = "Legacy N4 Cust",
                customerPhone = phone,
                amountPaid = 80.0,
                storeCreditApplied = 80.0,
                voucherCode = vCode,
                voucherAmount = 0.0, // legacy pre-v37: unpopulated
                customerCreditAmount = 0.0 // legacy pre-v37: unpopulated
            )
        )
        // Legacy sale logged a customer credit redemption of 50.0
        database.storeCreditLedgerDao().insertEntry(
            StoreCreditLedgerEntry(
                customerPhone = phone,
                deltaAmount = -50.0,
                balanceAfter = 0.0,
                type = "SALE_REDEMPTION",
                referenceInvoiceId = saleId,
                voucherCode = vCode,
                note = "Redeemed at sale #$saleId"
            )
        )

        // Void the legacy sale
        repository.deleteSale(saleId)

        val voucherAfterVoid = database.storeCreditVoucherDao().getVoucherByCode(vCode)!!
        val customerAfterVoid = repository.getCustomerByPhone(phone)!!

        assertEquals("Voucher spent was 30 (80 applied - 50 customer credit), so voucher restored to 100", 100.0, voucherAfterVoid.remainingBalance, 0.001)
        assertEquals("Customer credit must be restored by 50", 50.0, customerAfterVoid.storeCredit, 0.001)
    }

    @Test
    fun testM10_registeredCustomerCashReturn_asStoreCredit_addsCreditToCustomerAndLogsLedger() = runBlocking {
        val phone = "01788888802"
        repository.upsertCustomer(Customer(phone = phone, name = "M10 Return Cust", storeCredit = 10.0))
        val prod = Product(barcode = "p-m10", name = "M10 Item", category = "General", purchasePrice = 50.0, sellingPrice = 100.0, stockQty = 5.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val saleId = repository.completeSaleWithNotes(
            subtotal = 100.0,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = 100.0,
            customerName = "M10 Return Cust",
            customerPhone = phone,
            paymentMethod = "Cash",
            amountPaid = 100.0,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null))
        )

        // Process return with refundMethod = "Store Credit"
        val result = repository.processReturn(
            saleId = saleId,
            lines = listOf(ReturnLine(barcode = "p-m10", quantity = 1.0, name = "M10 Item", price = 100.0)),
            refundMethod = "Store Credit",
            note = "Customer opted for store credit",
            restock = true
        )

        val custAfter = repository.getCustomerByPhone(phone)!!
        assertEquals("Customer store credit should increase by 100 (from 10 to 110)", 110.0, custAfter.storeCredit, 0.001)

        val ledgerEntries = database.storeCreditLedgerDao().getEntriesForInvoiceOnce(saleId)
        val refundEntry = ledgerEntries.firstOrNull { it.type == "RETURN_REFUND" }
        assertTrue("Must log RETURN_REFUND ledger entry for customer", refundEntry != null)
        assertEquals(100.0, refundEntry?.deltaAmount ?: 0.0, 0.001)
        assertEquals(phone, refundEntry?.customerPhone)

        val retRecord = database.returnDao().getReturnsForSaleOnce(saleId).first()
        assertEquals("externalRefundAmount must be 0 for Store Credit refund", 0.0, retRecord.externalRefundAmount, 0.001)
        assertEquals("refundMethod must be Store Credit", "Store Credit", retRecord.refundMethod)
    }

    @Test
    fun testM1_R4_partialStoreCreditCheckout_dueAmountReflectedInBreakdown() = runBlocking {
        val grandTotal = 300.0
        val scApplied = 100.0
        val breakdown = com.example.techyfipos.utils.PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            paymentMethod = "Store Credit",
            amountPaid = 0.0,
            storeCreditApplied = scApplied,
            splitPayments = emptyList()
        )

        assertEquals("Remaining 200 on partial Store Credit must be dueAmount", 200.0, breakdown.dueAmount, 0.001)
        assertEquals("paymentMethodToSave must be Split", "Split", breakdown.paymentMethodToSave)
        assertTrue("splitPaymentJson must not be null", !breakdown.splitPaymentJson.isNullOrBlank())
        assertTrue("splitPaymentJson contains Due", breakdown.splitPaymentJson!!.contains("\"Due\""))
    }

    @Test
    fun testM5_receiptSnapshotAmountPaidMatchesReprintPaid() = runBlocking {
        val prod = Product(barcode = "p-m5-match", name = "M5 Item", category = "General", purchasePrice = 100.0, sellingPrice = 400.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        repository.upsertCustomer(
            Customer(phone = "01799999955", name = "M5 Cust", storeCredit = 100.0)
        )

        val grandTotal = 400.0
        val autoSc = 100.0
        val splitEntries = listOf(SplitPaymentEntry("Cash", 300.0))
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            paymentMethod = "Split",
            storeCreditApplied = autoSc,
            splitPayments = splitEntries
        )

        // Snapshot uses breakdown.amountPaidToRecord
        val snapshotAmountPaid = breakdown.amountPaidToRecord
        assertEquals("Snapshot amountPaid must be 400.0 (Cash 300 + SC 100)", 400.0, snapshotAmountPaid, 0.001)

        val saleId = repository.completeSaleWithNotes(
            subtotal = grandTotal,
            discountAmount = 0.0,
            isPercentage = false,
            grandTotal = grandTotal,
            paymentMethod = breakdown.paymentMethodToSave,
            customerName = "M5 Cust",
            customerPhone = "01799999955",
            amountPaid = breakdown.amountPaidToRecord,
            walletPhone = null,
            cardType = null,
            cardLastFour = null,
            splitPaymentJson = breakdown.splitPaymentJson,
            cartItemsWithNotes = listOf(Triple(prod, 1.0, null)),
            storeCreditApplied = autoSc
        )

        val savedSale = database.saleDao().getSaleWithItemsById(saleId)!!.sale
        assertEquals("Saved Sale.amountPaid must be 400.0", 400.0, savedSale.amountPaid, 0.001)
        assertEquals("Reprint and snapshot amountPaid must match exactly", snapshotAmountPaid, savedSale.amountPaid, 0.001)
    }

    @Test
    fun testB7_taxInclusiveSaleProfitExcludesTaxAndBackfillUpdatesOldSales() = runBlocking {
        val prod = Product(barcode = "p-b7-tax", name = "B7 Item", category = "General", purchasePrice = 100.0, sellingPrice = 230.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        // Insert legacy sale with isTaxInclusive = false and taxAmount = 30.0
        val legacySaleId = database.saleDao().insertSale(
            Sale(
                timestamp = System.currentTimeMillis(),
                subtotal = 200.0,
                discountAmount = 0.0,
                isPercentageDiscount = false,
                grandTotal = 230.0,
                paymentMethod = "Cash",
                amountPaid = 230.0,
                taxRate = 15.0,
                taxAmount = 30.0,
                isTaxInclusive = false
            )
        )

        // Set shop tax inclusive to true
        SettingsDataStore(context).saveInvoiceSettingsData(
            com.example.techyfipos.data.InvoiceSettingsData(
                taxEnabled = true,
                taxInclusive = true,
                taxRate = 15.0
            )
        )

        val backfilled = repository.backfillLegacyTaxInclusiveIfConfigured()
        assertTrue("At least 1 legacy sale should be backfilled", backfilled >= 1)

        val updatedSale = database.saleDao().getSaleWithItemsById(legacySaleId)!!.sale
        assertTrue("Legacy sale isTaxInclusive must now be true", updatedSale.isTaxInclusive)
    }

    @Test
    fun testX8_recomputeLoyaltyTiersEvaluatesConfiguredThresholds() = runBlocking {
        // Configure Silver threshold to 2000.0
        val customSettings = com.example.techyfipos.utils.LoyaltyProgramSettings(
            isTiersEnabled = true,
            silverSpendThreshold = 2000.0,
            goldSpendThreshold = 5000.0,
            platinumSpendThreshold = 10000.0
        )
        repository.updateLoyaltySettings(customSettings)

        val phone = "01788888888"
        // Customer demoted to Regular despite 2500 lifetime spend
        database.customerLoyaltyDao().upsertLoyalty(
            CustomerLoyalty(phone = phone, name = "X8 Cust", pointsBalance = 25, tier = "Regular", lifetimeSpent = 2500.0)
        )
        database.customerDao().upsertCustomer(
            Customer(phone = phone, name = "X8 Cust", loyaltyTier = "Regular", lifetimeSpent = 2500.0)
        )

        val updatedCount = repository.recomputeCustomerLoyaltyTiersWithConfiguredThresholds()
        assertTrue("Must update customer tiers", updatedCount >= 1)

        val loyaltyAfter = database.customerLoyaltyDao().getLoyaltyByPhone(phone)!!
        assertEquals("Customer loyalty tier must be recomputed to Silver with 2000 threshold", "Silver", loyaltyAfter.tier)

        val custAfter = database.customerDao().getCustomerByPhone(phone)!!
        assertEquals("Customer entity tier must be recomputed to Silver", "Silver", custAfter.loyaltyTier)
    }

    @Test
    fun testX4_supplierPaymentWithInvoiceNoteReducesDueAndVoidAdjustmentSkippedOnMerge() = runBlocking {
        val supplierId = database.supplierDao().upsertSupplier(
            com.example.techyfipos.data.db.Supplier(name = "X4 Supplier", companyName = "X4 Co", phone = "01999887766", totalPurchased = 1000.0, totalDue = 500.0)
        )
        val prodBarcode = "p-x4-void"
        database.productDao().insertProduct(
            Product(barcode = prodBarcode, name = "X4 Product", category = "General", purchasePrice = 50.0, sellingPrice = 100.0, stockQty = 50.0, reorderLevel = 1)
        )

        // Create backup JSON with:
        // (a) Standalone supplier payment referencing an invoice note not restored in this backup
        // (b) Voided-sale cancellation inventory adjustment
        val backupJson = JSONObject().apply {
            put("suppliers", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", supplierId)
                    put("name", "X4 Supplier")
                    put("phone", "01999887766")
                    put("totalPurchased", 1000.0)
                    put("totalDue", 500.0)
                })
            })
            put("supplierPayments", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 9999L)
                    put("supplierId", supplierId)
                    put("amountPaid", 200.0)
                    put("paymentMethod", "Cash")
                    put("referenceNote", "Paid on Invoice #OLD-UNRESTORED-INV")
                    put("timestamp", 123456789L)
                })
            })
            put("inventoryAdjustments", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 8888L)
                    put("productBarcode", prodBarcode)
                    put("difference", 10.0)
                    put("reason", "Sale Cancelled/Deleted #INV-999")
                    put("timestamp", 123456789L)
                })
            })
        }

        val tempFile = java.io.File(context.cacheDir, "test_x4_backup.json")
        tempFile.writeText(backupJson.toString())

        repository.setSessionUser(com.example.techyfipos.data.db.User(id = 1L, name = "Owner", pinHash = "x", role = "OWNER"))
        val success = repository.importBackup(tempFile, BackupImportMode.MERGE)
        assertTrue("Merge must succeed", success)

        // (a) Supplier due must be reduced by 200.0 (from 500.0 to 300.0)
        val supplierAfter = database.supplierDao().getSupplierById(supplierId)!!
        assertEquals("Supplier totalDue must be reduced by 200.0", 300.0, supplierAfter.totalDue, 0.001)

        // (b) Stock of p-x4-void must NOT be inflated by +10.0 (should remain 50.0)
        val prodAfter = database.productDao().getProductByBarcode(prodBarcode)!!
        assertEquals("Stock must not be inflated by voided sale adjustment", 50.0, prodAfter.stockQty, 0.001)
    }

    @Test
    fun testM14_ownerCanClearCreditLimitToZeroAndUpsertDuePreservesExistingLimit() = runBlocking {
        repository.setSessionUser(com.example.techyfipos.data.db.User(id = 1L, name = "Owner", pinHash = "x", role = "OWNER"))

        val phone = "01755555555"
        repository.upsertCustomer(
            Customer(phone = phone, name = "M14 Customer", creditLimit = 5000.0)
        )
        val cust1 = repository.getCustomerByPhone(phone)!!
        assertEquals(5000.0, cust1.creditLimit, 0.001)

        // 1. Owner can explicitly clear credit limit to 0
        repository.upsertCustomer(
            cust1.copy(creditLimit = 0.0)
        )
        val custCleared = repository.getCustomerByPhone(phone)!!
        assertEquals("Owner must be able to clear credit limit to 0.0", 0.0, custCleared.creditLimit, 0.001)

        // 2. Set limit back to 3000.0
        repository.upsertCustomer(
            custCleared.copy(creditLimit = 3000.0)
        )

        // 3. Adding a due with customerDue.creditLimit = 0.0 must NOT overwrite the customer's 3000.0 limit
        repository.upsertCustomerDue(
            com.example.techyfipos.data.db.CustomerDue(
                customerPhone = phone,
                customerName = "M14 Customer",
                outstandingAmount = 500.0,
                creditLimit = 0.0
            )
        )
        val custAfterDue = repository.getCustomerByPhone(phone)!!
        assertEquals("upsertCustomerDue with 0.0 limit must preserve existing 3000.0 limit", 3000.0, custAfterDue.creditLimit, 0.001)
    }
}
