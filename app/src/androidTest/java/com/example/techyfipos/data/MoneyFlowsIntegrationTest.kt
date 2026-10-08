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
    fun testM5_scenario3_card250PlusCash10On200TotalBalancesAndSucceedsInRepo() = runBlocking {
        val prod = Product(barcode = "BAR_M5_3", name = "M5-3 Item", category = "General", purchasePrice = 100.0, sellingPrice = 200.0, stockQty = 10.0, reorderLevel = 1)
        database.productDao().insertProduct(prod)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Card", 250.0, cardLastFour = "4321"),
                SplitPaymentEntry("Cash", 10.0)
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
            amountPaid = 10.0,
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
}

