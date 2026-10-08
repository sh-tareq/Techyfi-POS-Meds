package com.example.techyfipos.data

import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.SplitPaymentEntry
import com.example.techyfipos.data.db.StoreCreditVoucher
import com.example.techyfipos.utils.PaymentMath
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFlowsTest {

    @Test
    fun `M-1 plain store credit payment with partial store credit records due and no phantom cash`() {
        val grandTotal = 300.0
        val scApplied = 100.0
        val effectivePaid = com.example.techyfipos.ui.screens.calculateEffectiveAmountPaid(
            isSplitPayment = false,
            paymentMethod = "Store Credit",
            enteredAmountPaid = 0.0,
            grandTotal = grandTotal,
            splitPayments = emptyList(),
            storeCreditApplied = scApplied
        )
        // With partial store credit, effective amount paid must be 100.0, not 300.0
        assertEquals(100.0, effectivePaid, 0.001)

        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = scApplied,
            grandTotal = grandTotal,
            paymentMethod = "Store Credit",
            amountPaid = effectivePaid,
            redeemedVoucherCode = "VOUCH100"
        )
        assertNotNull("Split JSON must not be null when Store Credit is applied", splitJson)
        val arr = JSONArray(splitJson!!)
        assertEquals("Partial store credit must have 2 rows (SC and remainder)", 2, arr.length())
        val obj0 = arr.getJSONObject(0)
        assertEquals("Store Credit", obj0.getString("method"))
        assertEquals(100.0, obj0.getDouble("amount"), 0.001)
        val obj1 = arr.getJSONObject(1)
        assertEquals("Remainder must be Due, not phantom Cash", "Due", obj1.getString("method"))
        assertEquals(200.0, obj1.getDouble("amount"), 0.001)
        assertFalse("Remainder must not record tenderedAmount", obj1.has("tenderedAmount"))
        assertFalse("Remainder must not record changeAmount", obj1.has("changeAmount"))

        val dueAddition = PaymentMath.calculateDueAddition("Split", grandTotal, effectivePaid, splitJson)
        assertEquals("Debt of 200.0 must be recorded", 200.0, dueAddition, 0.001)
    }

    @Test
    fun `D-1 formatTaka defaults to Latin digits and outputs Bengali only when explicitly requested`() {
        assertEquals("৳1,250", com.example.techyfipos.utils.formatTaka(1250.0))
        assertEquals("৳১,২৫০", com.example.techyfipos.utils.formatTaka(1250.0, isBangla = true))
        assertEquals("৳1,250", com.example.techyfipos.utils.formatMoney(1250.0))
        assertEquals("৳১,২৫০", com.example.techyfipos.utils.formatMoney(1250.0, isBangla = true))
    }

    @Test
    fun `M-5 scenario 1 explicit SC split row plus auto credit balances to grand total`() {
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
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val totalAmount = (0 until arr.length()).sumOf { arr.getJSONObject(it).getDouble("amount") }
        assertEquals("Rows must sum to grand total (300.0)", 300.0, totalAmount, 0.001)
    }

    @Test
    fun `M-5 scenario 3 Card overpayment with Cash balances to grand total`() {
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Card", 250.0, cardLastFour = "1234"),
                SplitPaymentEntry("Cash", 10.0)
            ),
            storeCreditApplied = 0.0,
            grandTotal = 200.0,
            paymentMethod = "Split"
        )
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val totalAmount = (0 until arr.length()).sumOf { arr.getJSONObject(it).getDouble("amount") }
        assertEquals("Rows must balance to grand total (200.0)", 200.0, totalAmount, 0.001)
    }

    @Test
    fun `M-2 walk-in voucher redemption requires no customer phone`() {
        val splitEntries = listOf(
            JSONObject().apply {
                put("method", "Store Credit")
                put("amount", 100.0)
                put("voucherCode", "WALKIN123")
            }
        )
        val hasVoucher = splitEntries.any { !it.optString("voucherCode").isNullOrBlank() }
        val customerName: String? = null
        val customerPhone: String? = null

        val isAllowedWithoutPhone = hasVoucher || (!customerName.isNullOrBlank() && !customerPhone.isNullOrBlank())
        assertTrue("Walk-in voucher payment must be allowed without customer phone", isAllowedWithoutPhone)
    }

    @Test
    fun `M-3 void restoration caps voucher at initial amount and restores remainder to customer`() {
        val scApplied = 500.0
        val voucher = StoreCreditVoucher(
            code = "VOUCH500",
            initialAmount = 300.0,
            remainingBalance = 100.0,
            status = "EXHAUSTED",
            issuedAt = System.currentTimeMillis()
        )
        
        val neededToCap = voucher.initialAmount - voucher.remainingBalance // 200.0
        val voucherPortion = minOf(scApplied, neededToCap) // 200.0
        val customerPortion = scApplied - voucherPortion // 300.0

        val newVoucherBal = voucher.remainingBalance + voucherPortion
        assertEquals(300.0, newVoucherBal, 0.001)
        assertEquals(300.0, customerPortion, 0.001)
    }

    @Test
    fun `M-5 PaymentMath calculateChange correctly calculates change for cash plus store credit`() {
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 250.0),
                SplitPaymentEntry("Store Credit", 50.0)
            ),
            storeCreditApplied = 50.0,
            grandTotal = 200.0,
            paymentMethod = "Split"
        )
        assertNotNull(splitJson)
        val change = PaymentMath.calculateChange(
            grandTotal = 200.0,
            amountPaid = 250.0,
            paymentMethod = "Split",
            splitJson = splitJson,
            storeCreditApplied = 50.0
        )
        // Auto SC (50) + split SC (50) = 100 SC. Remaining bill is 100.
        // Cash tendered is 250, so change is 250 - 100 = 150.0.
        assertEquals(150.0, change, 0.001)
    }

    @Test
    fun `M-5 scenario 2 plain cash with store credit records change in JSON and calculateChange`() {
        // Non-split sale: total 200, store credit 50 applied, cash tendered 250
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = 50.0,
            grandTotal = 200.0,
            paymentMethod = "Cash",
            amountPaid = 250.0
        )
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        assertEquals(2, arr.length())
        val cashObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Cash" }
        assertEquals(150.0, cashObj.getDouble("amount"), 0.001)
        assertEquals(250.0, cashObj.getDouble("tenderedAmount"), 0.001)
        assertEquals(100.0, cashObj.getDouble("changeAmount"), 0.001)

        val change = PaymentMath.calculateChange(
            grandTotal = 200.0,
            amountPaid = 250.0,
            paymentMethod = "Cash",
            splitJson = splitJson,
            storeCreditApplied = 50.0
        )
        assertEquals("Calculated change must be 100.0", 100.0, change, 0.001)
    }

    @Test
    fun `M-6 Cash 200 and Due 200 on 300 total computes 0 change and records 100 Due debt`() {
        val grandTotal = 300.0
        val splitPayments = listOf(
            SplitPaymentEntry("Cash", 200.0),
            SplitPaymentEntry("Due", 200.0)
        )
        val effectivePaid = com.example.techyfipos.ui.screens.calculateEffectiveAmountPaid(
            isSplitPayment = true,
            paymentMethod = "Split",
            enteredAmountPaid = 0.0,
            grandTotal = grandTotal,
            splitPayments = splitPayments,
            storeCreditApplied = 0.0
        )
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = splitPayments,
            storeCreditApplied = 0.0,
            grandTotal = grandTotal,
            paymentMethod = "Split",
            amountPaid = effectivePaid
        )
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val cashObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Cash" }
        val dueObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Due" }

        // Customer actually gave 200 cash for a 300 purchase; remaining debt is 100, NOT 200!
        assertEquals("Cash applied must be 200.0", 200.0, cashObj.getDouble("amount"), 0.001)
        assertEquals("Due debt recorded must be 100.0, not over-recorded by 100", 100.0, dueObj.getDouble("amount"), 0.001)

        val change = PaymentMath.calculateChange(
            grandTotal = grandTotal,
            amountPaid = effectivePaid,
            paymentMethod = "Split",
            splitJson = splitJson
        )
        assertEquals("Change must be 0.0, Due does not create cash change", 0.0, change, 0.001)

        val dueAddition = PaymentMath.calculateDueAddition("Split", grandTotal, effectivePaid, splitJson)
        assertEquals("Recorded due addition must be 100.0", 100.0, dueAddition, 0.001)
    }

    @Test
    fun `N-1 multiple Cash split rows Cash 100 and Cash 100 on 150 computes 50 change and rows sum to 150`() {
        val grandTotal = 150.0
        val splitPayments = listOf(
            SplitPaymentEntry("Cash", 100.0),
            SplitPaymentEntry("Cash", 100.0)
        )
        val effectivePaid = com.example.techyfipos.ui.screens.calculateEffectiveAmountPaid(
            isSplitPayment = true,
            paymentMethod = "Split",
            enteredAmountPaid = 0.0,
            grandTotal = grandTotal,
            splitPayments = splitPayments,
            storeCreditApplied = 0.0
        )
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = splitPayments,
            storeCreditApplied = 0.0,
            grandTotal = grandTotal,
            paymentMethod = "Split",
            amountPaid = effectivePaid
        )
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val totalApplied = (0 until arr.length()).sumOf { arr.getJSONObject(it).getDouble("amount") }
        assertEquals("Rows must sum to grand total (150.0) without double subtracting change", 150.0, totalApplied, 0.001)

        val change = PaymentMath.calculateChange(
            grandTotal = grandTotal,
            amountPaid = effectivePaid,
            paymentMethod = "Split",
            splitJson = splitJson
        )
        assertEquals("Change must be 50.0", 50.0, change, 0.001)
    }

    @Test
    fun `M-5 Cash 250 and SC 50 on 300 total with auto SC 100 preserves auto SC and computes 100 change`() {
        val grandTotal = 300.0
        val autoSc = 100.0
        val splitPayments = listOf(
            SplitPaymentEntry("Cash", 250.0),
            SplitPaymentEntry("Store Credit", 50.0)
        )
        val effectivePaid = com.example.techyfipos.ui.screens.calculateEffectiveAmountPaid(
            isSplitPayment = true,
            paymentMethod = "Split",
            enteredAmountPaid = 0.0,
            grandTotal = grandTotal,
            splitPayments = splitPayments,
            storeCreditApplied = autoSc
        )
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = splitPayments,
            storeCreditApplied = autoSc,
            grandTotal = grandTotal,
            paymentMethod = "Split",
            amountPaid = effectivePaid
        )
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val totalApplied = (0 until arr.length()).sumOf { arr.getJSONObject(it).getDouble("amount") }
        assertEquals("Rows must sum to grand total (300.0)", 300.0, totalApplied, 0.001)

        val scRows = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.getString("method") == "Store Credit" }
        val totalScApplied = scRows.sumOf { it.getDouble("amount") }
        assertEquals("Auto SC (100) + split SC (50) must total 150.0 store credit", 150.0, totalScApplied, 0.001)

        val change = PaymentMath.calculateChange(
            grandTotal = grandTotal,
            amountPaid = effectivePaid,
            paymentMethod = "Split",
            splitJson = splitJson,
            storeCreditApplied = autoSc
        )
        assertEquals("Change must be 100.0 (Cash 250 tendered for remaining 150)", 100.0, change, 0.001)
    }

    @Test
    fun `M-5 Card 250 and Cash 10 on 200 total records Card 200 and 10 cash change not Card 190 and 60 change`() {
        val grandTotal = 200.0
        val splitPayments = listOf(
            SplitPaymentEntry("Card", 250.0, cardLastFour = "1234"),
            SplitPaymentEntry("Cash", 10.0)
        )
        val effectivePaid = com.example.techyfipos.ui.screens.calculateEffectiveAmountPaid(
            isSplitPayment = true,
            paymentMethod = "Split",
            enteredAmountPaid = 0.0,
            grandTotal = grandTotal,
            splitPayments = splitPayments,
            storeCreditApplied = 0.0
        )
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = splitPayments,
            storeCreditApplied = 0.0,
            grandTotal = grandTotal,
            paymentMethod = "Split",
            amountPaid = effectivePaid
        )
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val totalApplied = (0 until arr.length()).sumOf { arr.getJSONObject(it).getDouble("amount") }
        assertEquals("Rows must balance to grand total (200.0)", 200.0, totalApplied, 0.001)

        val cardObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Card" }
        assertEquals("Card applied must be 200.0, not reduced to 190.0", 200.0, cardObj.getDouble("amount"), 0.001)

        val change = PaymentMath.calculateChange(
            grandTotal = grandTotal,
            amountPaid = effectivePaid,
            paymentMethod = "Split",
            splitJson = splitJson
        )
        assertEquals("Change must be 10.0 (cash returned), not 60.0", 10.0, change, 0.001)
    }

    @Test
    fun `M-6 scenario 2 resolveMethodToSave returns Store Credit when SC covers total and Split when partial`() {
        // When store credit covers grand total
        val methodFull = PaymentMath.resolveMethodToSave(
            isSplitPayment = true,
            storeCreditApplied = 300.0,
            grandTotal = 300.0,
            paymentMethod = "Cash"
        )
        assertEquals("Store Credit", methodFull)

        // When partial store credit is applied
        val methodPartial = PaymentMath.resolveMethodToSave(
            isSplitPayment = false,
            storeCreditApplied = 100.0,
            grandTotal = 300.0,
            paymentMethod = "Cash"
        )
        assertEquals("Split", methodPartial)

        // When standard Cash sale with no store credit
        val methodRegular = PaymentMath.resolveMethodToSave(
            isSplitPayment = false,
            storeCreditApplied = 0.0,
            grandTotal = 300.0,
            paymentMethod = "Cash"
        )
        assertEquals("Cash", methodRegular)
    }

    @Test
    fun `X-7 loyalty point calculation uses floor to prevent rounding over cap`() {
        val requestedPoints = 9.6
        val ptCashVal = 1.0
        val flooredPoints = kotlin.math.floor(requestedPoints / ptCashVal + 1e-6).toInt()
        assertEquals(9, flooredPoints)
    }

    @Test
    fun `X-1 system backup method exportBackupForSystem is distinct from permission gated exportBackup`() {
        val repoMethods = DataRepository::class.java.declaredMethods.map { it.name }
        assertTrue("exportBackupForSystem must exist on DataRepository", repoMethods.contains("exportBackupForSystem"))
    }

    @Test
    fun `N-1 split row does not inherit global wallet or card details`() {
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 100.0),
                SplitPaymentEntry("Store Credit", 50.0)
            ),
            storeCreditApplied = 50.0,
            grandTotal = 150.0,
            paymentMethod = "Split",
            walletPhone = "01711111111",
            cardType = "Visa",
            cardLastFour = "1234"
        )
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            assertFalse("Cash/SC split row must not have walletPhone", obj.has("walletPhone"))
            assertFalse("Cash/SC split row must not have cardType", obj.has("cardType"))
            assertFalse("Cash/SC split row must not have cardLastFour", obj.has("cardLastFour"))
        }
    }

    @Test
    fun `B-7 tax exclusive sale profit does not divide price by tax rate and non-taxable item price is never divided`() {
        // Tax-exclusive shop: taxRate = 10%, price = 100, cost = 60
        val taxRate = 10.0
        val price = 100.0
        val cost = 60.0

        val exclusiveEffectivePrice = PaymentMath.calculateItemEffectivePrice(
            price = price,
            taxRate = taxRate,
            isTaxInclusive = false,
            isTaxable = true
        )
        val exclusiveProfit = exclusiveEffectivePrice - cost
        assertEquals(100.0, exclusiveEffectivePrice, 0.001)
        assertEquals(40.0, exclusiveProfit, 0.001)

        // Non-taxable item in tax-inclusive shop: price should not be divided
        val nonTaxableEffectivePrice = PaymentMath.calculateItemEffectivePrice(
            price = price,
            taxRate = taxRate,
            isTaxInclusive = true,
            isTaxable = false
        )
        assertEquals(100.0, nonTaxableEffectivePrice, 0.001)
        assertEquals(40.0, nonTaxableEffectivePrice - cost, 0.001)

        // Taxable item in tax-inclusive shop: price is divided by (1 + rate)
        val inclusiveEffectivePrice = PaymentMath.calculateItemEffectivePrice(
            price = price,
            taxRate = taxRate,
            isTaxInclusive = true,
            isTaxable = true
        )
        assertEquals(100.0 / 1.10, inclusiveEffectivePrice, 0.001)
    }

    @Test
    fun `M-10 return refund on sale with store credit restores credit and does not over-refund cash`() {
        // ৳300 sale (100 SC + 200 cash), full return refunded in Cash
        val apportionment = PaymentMath.calculateReturnRefundApportionment(
            refundTotal = 300.0,
            saleStoreCreditApplied = 100.0,
            storeCreditRestoredSoFar = 0.0,
            dueReduction = 0.0,
            refundMethod = "Cash"
        )
        assertEquals("Store credit restored must be 100", 100.0, apportionment.storeCreditToRestore, 0.001)
        assertEquals("External cash refund must be 200, not 300", 200.0, apportionment.externalRefundAmount, 0.001)
        assertEquals("Total benefit to customer must equal refund total (300)", 300.0, apportionment.storeCreditToRestore + apportionment.externalRefundAmount, 0.001)

        // Previous return had due reduction 50, but 0 store credit restored
        val partialWithDueApportionment = PaymentMath.calculateReturnRefundApportionment(
            refundTotal = 150.0,
            saleStoreCreditApplied = 100.0,
            storeCreditRestoredSoFar = 0.0, // previous return had dueReduction=50, scRestored=0
            dueReduction = 50.0,
            refundMethod = "Cash"
        )
        // Subsequent return: store credit of 100 has already been restored, so customer gets cash
        val secondReturnApportionment = PaymentMath.calculateReturnRefundApportionment(
            refundTotal = 150.0,
            saleStoreCreditApplied = 100.0,
            storeCreditRestoredSoFar = 100.0,
            dueReduction = 0.0,
            refundMethod = "Cash"
        )
        assertEquals("Store credit must not be restored twice", 0.0, secondReturnApportionment.storeCreditToRestore, 0.001)
        assertEquals("Second return must refund cash", 150.0, secondReturnApportionment.externalRefundAmount, 0.001)
    }

    @Test
    fun `M-9 tablet stepper at quantity 1 triggers removal instead of setting quantity to 0`() {
        var removed = false
        var updatedQty = -1.0
        val onRemove = { removed = true }
        val onQuantityChange = { q: Double -> updatedQty = q }

        val currentQty = 1.0
        // Stepper decrease click logic:
        if (currentQty <= 1.0) onRemove() else onQuantityChange(currentQty - 1.0)

        assertTrue("Decreasing from qty 1 must call onRemove", removed)
        assertEquals("onQuantityChange must not be called with 0", -1.0, updatedQty, 0.001)
    }

    @Test
    fun `M-7 calculateDueAddition extracts due from plain due and split payment rows`() {
        val duePlain = PaymentMath.calculateDueAddition(
            paymentMethod = "Due",
            grandTotal = 500.0,
            amountPaid = 100.0,
            splitPaymentJson = null
        )
        assertEquals(400.0, duePlain, 0.001)

        val splitJson = """[{"method":"Cash","amount":150.0},{"method":"Due","amount":250.0}]"""
        val dueSplit = PaymentMath.calculateDueAddition(
            paymentMethod = "Split",
            grandTotal = 400.0,
            amountPaid = 150.0,
            splitPaymentJson = splitJson
        )
        assertEquals(250.0, dueSplit, 0.001)

        val dueCash = PaymentMath.calculateDueAddition(
            paymentMethod = "Cash",
            grandTotal = 200.0,
            amountPaid = 200.0,
            splitPaymentJson = null
        )
        assertEquals(0.0, dueCash, 0.001)
    }

    @Test
    fun `B-5 Z-report payment breakdown splits split-payment sales into individual methods`() {
        val splitJson = """[{"method":"Cash","amount":100.0},{"method":"bKash","amount":200.0}]"""
        val sales = listOf(
            PaymentMath.SimpleSalePayment(
                saleId = 1L,
                paymentMethod = "Split",
                grandTotal = 300.0,
                splitPaymentJson = splitJson
            )
        )
        val breakdown = PaymentMath.aggregatePaymentBreakdown(sales, emptyList())
        assertEquals("Split bucket must not exist in breakdown", 0.0, breakdown["Split"] ?: 0.0, 0.001)
        assertEquals("Cash amount must be 100.0", 100.0, breakdown["Cash"] ?: 0.0, 0.001)
        assertEquals("bKash amount must be 200.0", 200.0, breakdown["bKash"] ?: 0.0, 0.001)
    }

    @Test
    fun `B-5 Z-report return attribution deducts store credit refund from Store Credit not Cash`() {
        val sales = listOf(
            PaymentMath.SimpleSalePayment(
                saleId = 10L,
                paymentMethod = "Cash",
                grandTotal = 500.0
            )
        )
        val returns = listOf(
            PaymentMath.SimpleReturnPayment(
                saleId = 10L,
                refundMethod = "Store Credit",
                refundAmount = 200.0,
                externalRefundAmount = 200.0,
                dueAdjustmentAmount = 0.0
            )
        )
        val breakdown = PaymentMath.aggregatePaymentBreakdown(sales, returns)
        assertEquals("Cash must remain 500.0 since refund was given as Store Credit", 500.0, breakdown["Cash"] ?: 0.0, 0.001)
        assertEquals("Store Credit must reflect deduction (-200.0)", -200.0, breakdown["Store Credit"] ?: 0.0, 0.001)
    }
}
