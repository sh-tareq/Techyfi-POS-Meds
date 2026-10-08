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
    fun testM6_cash200AndDue200On300TotalComputes100ChangeAndSucceedsInRepo() = runBlocking {
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
        assertEquals(100.0, change, 0.001)

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
        assertEquals(200.0, due?.outstandingAmount ?: 0.0, 0.001)
    }
}
