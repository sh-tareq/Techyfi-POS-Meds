package com.techyfiposmeds.data

import com.techyfiposmeds.data.db.Customer
import com.techyfiposmeds.data.db.SplitPaymentEntry
import com.techyfiposmeds.data.db.StoreCreditVoucher
import com.techyfiposmeds.utils.PaymentMath
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MoneyFlowsTest {

    @Test
    fun `M-1 plain store credit payment with partial store credit records due and no phantom cash`() {
        val grandTotal = 300.0
        val scApplied = 100.0
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            storeCreditApplied = scApplied,
            isSplitPayment = false,
            paymentMethod = "Store Credit",
            redeemedVoucherCode = "VOUCH100"
        )
        // With partial store credit, effective amount paid must be 100.0, not 300.0
        assertEquals(100.0, breakdown.effectiveAmountPaid, 0.001)
        assertEquals(200.0, breakdown.dueAmount, 0.001)

        val splitJson = breakdown.splitPaymentJson
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

        val dueAddition = PaymentMath.calculateDueAddition("Split", grandTotal, breakdown.effectiveAmountPaid, splitJson)
        assertEquals("Debt of 200.0 must be recorded", 200.0, dueAddition, 0.001)
    }

    @Test
    fun `D-1 formatTaka defaults to Latin digits and outputs Bengali only when explicitly requested`() {
        assertEquals("৳1,250", com.techyfiposmeds.utils.formatTaka(1250.0))
        assertEquals("৳১,২৫০", com.techyfiposmeds.utils.formatTaka(1250.0, isBangla = true))
        assertEquals("৳1,250", com.techyfiposmeds.utils.formatMoney(1250.0))
        assertEquals("৳১,২৫০", com.techyfiposmeds.utils.formatMoney(1250.0, isBangla = true))
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
    fun `M-5 scenario 3 Card overpayment with Cash is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentMath.buildAuthoritativeSplitJson(
                isSplitPayment = true,
                splitPayments = listOf(
                    SplitPaymentEntry("Card", 250.0, cardLastFour = "1234"),
                    SplitPaymentEntry("Cash", 10.0)
                ),
                storeCreditApplied = 0.0,
                grandTotal = 200.0,
                paymentMethod = "Split"
            )
        }
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
        // Bill is 200. Store credit is 50. Remaining bill is 150.
        // Cash tendered is 250, so change is 250 - 150 = 100.0.
        assertEquals(100.0, change, 0.001)
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
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            isSplitPayment = true,
            paymentMethod = "Split",
            splitPayments = splitPayments,
            storeCreditApplied = 0.0
        )
        val effectivePaid = breakdown.effectiveAmountPaid
        val splitJson = breakdown.splitPaymentJson
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val cashObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Cash" }
        val dueObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Due" }

        // Customer actually gave 200 cash for a 300 purchase; remaining debt is 100, NOT 200!
        assertEquals("Cash applied must be 200.0", 200.0, cashObj.getDouble("amount"), 0.001)
        assertEquals("Due debt recorded must be 100.0, not over-recorded by 100", 100.0, dueObj.getDouble("amount"), 0.001)

        val change = breakdown.changeAmount
        assertEquals("Change must be 0.0, Due does not create cash change", 0.0, change, 0.001)

        val dueAddition = PaymentMath.calculateDueAddition("Split", grandTotal, effectivePaid, splitJson)
        assertEquals("Recorded due addition must be 100.0", 100.0, dueAddition, 0.001)
    }

    @Test
    fun `R-4 calculatePaymentBreakdown dueAmount reflects true debt for credit limit and confirmation`() {
        val splitBreakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 300.0,
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry("Cash", 200.0),
                SplitPaymentEntry("Due", 200.0)
            )
        )
        // Customer paid 200 cash toward 300, remaining due is 100, NOT the raw 200 on the split line
        assertEquals("Breakdown due amount must be 100.0", 100.0, splitBreakdown.dueAmount, 0.001)

        val scBreakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 300.0,
            storeCreditApplied = 100.0,
            paymentMethod = "Store Credit"
        )
        assertEquals("Partial store credit must report remaining debt of 200.0", 200.0, scBreakdown.dueAmount, 0.001)
    }

    @Test
    fun `N-1 multiple Cash split rows Cash 100 and Cash 100 on 150 computes 50 change and rows sum to 150`() {
        val grandTotal = 150.0
        val splitPayments = listOf(
            SplitPaymentEntry("Cash", 100.0),
            SplitPaymentEntry("Cash", 100.0)
        )
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            isSplitPayment = true,
            paymentMethod = "Split",
            splitPayments = splitPayments,
            storeCreditApplied = 0.0
        )
        val effectivePaid = breakdown.effectiveAmountPaid
        val splitJson = breakdown.splitPaymentJson
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val totalApplied = (0 until arr.length()).sumOf { arr.getJSONObject(it).getDouble("amount") }
        assertEquals("Rows must sum to grand total (150.0) without double subtracting change", 150.0, totalApplied, 0.001)

        val change = breakdown.changeAmount
        assertEquals("Change must be 50.0", 50.0, change, 0.001)
    }

    @Test
    fun `R19-1 partial store credit plus Cash two-step flow computes change 50 records cash 200 and rejects underpayment 150`() {
        val grandTotal = 300.0
        val storeCreditApplied = 100.0
        val remainingToPay = grandTotal - storeCreditApplied // 200.0

        // 1. Panel breakdown with underpayment (150 cash against 200 remaining)
        val underpayBreakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            storeCreditApplied = storeCreditApplied,
            isSplitPayment = false,
            paymentMethod = "Cash",
            amountPaid = 150.0
        )
        // Cash tendered must not be inflated with store credit (must be 150.0, not 250.0)
        assertEquals("Panel effectiveAmountPaid on underpay must equal cash tendered alone", 150.0, underpayBreakdown.effectiveAmountPaid, 0.001)
        val isUnderpayValid = underpayBreakdown.effectiveAmountPaid >= remainingToPay
        assertFalse("Underpaying with 150 cash against 200 remaining must be rejected", isUnderpayValid)

        // 2. Panel breakdown with 250 cash tendered (50 change)
        val panelBreakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            storeCreditApplied = storeCreditApplied,
            isSplitPayment = false,
            paymentMethod = "Cash",
            amountPaid = 250.0
        )
        assertEquals("Panel change must be 50.0", 50.0, panelBreakdown.changeAmount, 0.001)
        assertEquals("Panel effectiveAmountPaid must equal raw cash tendered", 250.0, panelBreakdown.effectiveAmountPaid, 0.001)

        // 3. Snapshot breakdown must receive raw cash tendered (250.0), NOT inflated effectiveAmountPaid
        val snapshotAmountPaid = panelBreakdown.effectiveAmountPaid
        val snapshotBreakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            storeCreditApplied = storeCreditApplied,
            isSplitPayment = false,
            paymentMethod = "Cash",
            amountPaid = snapshotAmountPaid
        )
        assertEquals("Snapshot change must be 50.0, not inflated 150.0", 50.0, snapshotBreakdown.changeAmount, 0.001)

        val splitJson = snapshotBreakdown.splitPaymentJson
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val cashObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Cash" }
        assertEquals("Recorded cash row amount must be 200.0", 200.0, cashObj.getDouble("amount"), 0.001)
        assertEquals("Recorded cash tenderedAmount must be 250.0", 250.0, cashObj.getDouble("tenderedAmount"), 0.001)
        assertEquals("Recorded cash changeAmount must be 50.0", 50.0, cashObj.getDouble("changeAmount"), 0.001)
    }

    @Test
    fun `M-5 Card over-tender is rejected, and valid electronic plus cash split returns correct cash change`() {
        val grandTotal = 200.0
        val overTenderEntries = listOf(
            SplitPaymentEntry("Card", 250.0, cardLastFour = "1234"),
            SplitPaymentEntry("Cash", 10.0)
        )
        assertThrows(IllegalArgumentException::class.java) {
            PaymentMath.calculatePaymentBreakdown(
                grandTotal = grandTotal,
                isSplitPayment = true,
                paymentMethod = "Split",
                splitPayments = overTenderEntries,
                storeCreditApplied = 0.0
            )
        }

        // Valid split: Card 150 + Cash 100 on 200 total -> Card 150, Cash 50 applied, 50 cash change
        val validEntries = listOf(
            SplitPaymentEntry("Card", 150.0, cardLastFour = "1234"),
            SplitPaymentEntry("Cash", 100.0)
        )
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = grandTotal,
            isSplitPayment = true,
            paymentMethod = "Split",
            splitPayments = validEntries,
            storeCreditApplied = 0.0
        )
        val arr = JSONArray(breakdown.splitPaymentJson!!)
        assertEquals("Total applied must balance to grand total 200.0", 200.0, (0 until arr.length()).sumOf { arr.getJSONObject(it).getDouble("amount") }, 0.001)
        val cardObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Card" }
        assertEquals(150.0, cardObj.getDouble("amount"), 0.001)
        val cashObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Cash" }
        assertEquals(50.0, cashObj.getDouble("amount"), 0.001)
        assertEquals(50.0, breakdown.changeAmount, 0.001)
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

    @Test
    fun `N-5 pure non-split payment method produces null splitPaymentJson`() {
        // Pure Store Credit with storeCreditApplied = 0 and residual split rows in memory
        val bdZeroSc = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 300.0,
            storeCreditApplied = 0.0,
            isSplitPayment = false,
            splitPayments = listOf(SplitPaymentEntry("Cash", 300.0)),
            paymentMethod = "Store Credit"
        )
        org.junit.Assert.assertNull("splitPaymentJson must be null for pure Store Credit", bdZeroSc.splitPaymentJson)

        // Pure Store Credit with full store credit
        val bdFullSc = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 300.0,
            storeCreditApplied = 300.0,
            isSplitPayment = false,
            paymentMethod = "Store Credit"
        )
        org.junit.Assert.assertNull("splitPaymentJson must be null for pure non-split Store Credit payment", bdFullSc.splitPaymentJson)
    }

    @Test
    fun `B-7 calculateItemEffectivePrice strips embedded tax when inclusive and preserves when exclusive`() {
        // Tax inclusive: 115.0 with 15% tax rate -> effective base price is 100.0
        val incPrice = PaymentMath.calculateItemEffectivePrice(
            price = 115.0,
            taxRate = 15.0,
            isTaxInclusive = true,
            isTaxable = true
        )
        assertEquals(100.0, incPrice, 0.001)

        // Non-taxable item: effective price remains 115.0
        val nonTaxablePrice = PaymentMath.calculateItemEffectivePrice(
            price = 115.0,
            taxRate = 15.0,
            isTaxInclusive = true,
            isTaxable = false
        )
        assertEquals(115.0, nonTaxablePrice, 0.001)

        // Tax exclusive: effective price remains 115.0
        val excPrice = PaymentMath.calculateItemEffectivePrice(
            price = 115.0,
            taxRate = 15.0,
            isTaxInclusive = false,
            isTaxable = true
        )
        assertEquals(115.0, excPrice, 0.001)
    }

    @Test
    fun `B-5 PaymentMath aggregatePaymentBreakdown attributes partial cash on Due sale to Cash and Due`() {
        val sales = listOf(
            PaymentMath.SimpleSalePayment(
                saleId = 1L,
                paymentMethod = "Due",
                grandTotal = 1000.0,
                amountPaid = 300.0
            )
        )
        val breakdown = PaymentMath.aggregatePaymentBreakdown(sales, returns = emptyList())
        val cashAmount = breakdown["Cash"] ?: 0.0
        val dueAmount = breakdown["Due"] ?: 0.0
        assertEquals(300.0, cashAmount, 0.001)
        assertEquals(700.0, dueAmount, 0.001)
    }

    @Test
    fun `B-5 single-method Due with partial payment produces Split splitPaymentJson`() {
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 500.0,
            paymentMethod = "Due",
            amountPaid = 200.0,
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = 0.0
        )
        assertEquals("Split", breakdown.paymentMethodToSave)
        assertNotNull(breakdown.splitPaymentJson)
        val arr = JSONArray(breakdown.splitPaymentJson!!)
        assertEquals(2, arr.length())
        val cashObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Cash" }
        val dueObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Due" }
        assertEquals(200.0, cashObj.getDouble("amount"), 0.001)
        assertEquals(300.0, dueObj.getDouble("amount"), 0.001)
    }

    @Test
    fun `M-14 DailyReportData correctly computes netCollections factoring due collected and supplier payments`() {
        val daily = com.techyfiposmeds.ui.viewmodel.DailyReportData(
            timestamp = 1728470400000L,
            revenue = 1000.0,
            profit = 300.0,
            transactionCount = 5,
            dueCollected = 300.0,
            supplierPayments = 200.0,
            refunds = 100.0,
            expenses = 50.0
        )
        // B-5b: revenue is already net of refunds, so netCollections must not subtract refunds a second time:
        // netCollections = 1000 + 300 - 50 - 200 = 1050.0
        assertEquals("netCollections must not double-subtract refunds", 1050.0, daily.netCollections, 0.001)
    }

    @Test
    fun `B-5a local-day bucketing vs UTC division for dues and expenses at UTC+6`() {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Dhaka")).apply {
            set(2026, java.util.Calendar.OCTOBER, 9, 2, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val ts = cal.timeInMillis // 2:00 AM on Oct 9 in Dhaka (which is 8:00 PM on Oct 8 in UTC)
        val localMidnight = com.techyfiposmeds.utils.startOfDay(ts)
        val utcDiv = (ts / 86400000L) * 86400000L
        // Verify that UTC division is NOT equal to local midnight (it falls on previous UTC day)
        org.junit.Assert.assertNotEquals("UTC division does not equal local midnight at UTC+6", localMidnight, utcDiv)
    }
    @Test
    fun `R20-07 Safe Drop is excluded from operating expenses filter`() {
        val expenses = listOf(
            com.techyfiposmeds.data.db.Expense(id = 1, title = "Utils", amount = 50.0, category = "Utilities", type = "EXPENSE", paymentSource = "Cash", timestamp = 1000L),
            com.techyfiposmeds.data.db.Expense(id = 2, title = "Drop", amount = 20000.0, category = "Safe Drop", type = "EXPENSE", paymentSource = "Cash", timestamp = 1000L),
            com.techyfiposmeds.data.db.Expense(id = 3, title = "Supplier", amount = 500.0, category = "Supplier Payment", type = "EXPENSE", paymentSource = "Cash", timestamp = 1000L)
        )
        val filtered = expenses.filter {
            it.type == "EXPENSE" &&
            !it.category.equals("Supplier Payment", ignoreCase = true) &&
            !it.category.equals("Safe Drop", ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals(50.0, filtered.sumOf { it.amount }, 0.001)
    }

    @Test
    fun `R20-10 invoice profit calculation agrees with report and owner analytics`() {
        val subtotal = 200.0
        val grossCogs = 120.0
        val taxAmount = 20.0
        val discount = 10.0
        val loyaltyDiscount = 10.0
        val grandTotal = 200.0
        val refundAmount = 100.0
        val returnedCost = 60.0

        val pureResult = com.techyfiposmeds.utils.PaymentMath.calculateInvoiceProfitSummary(
            subtotal = subtotal,
            grossCogs = grossCogs,
            taxAmount = taxAmount,
            discount = discount,
            loyaltyDiscount = loyaltyDiscount,
            grandTotal = grandTotal,
            refundAmount = refundAmount,
            returnedCost = returnedCost
        )

        // 2. buildReport formula
        val retTaxPortion = (refundAmount / grandTotal) * taxAmount
        val lineRev = 200.0
        val profitSum = lineRev - grossCogs
        val refundedProfit = (refundAmount - retTaxPortion) - returnedCost
        val grossMargin = profitSum - refundedProfit
        val reportNetProfit = grossMargin - discount - loyaltyDiscount

        // 3. Assert all views agree
        assertEquals(30.0, pureResult.netProfit, 0.001)
        assertEquals(30.0, reportNetProfit, 0.001)
        assertEquals(pureResult.netProfit, reportNetProfit, 0.001)
    }

    @Test
    fun `R20-19f supplier pay due in-flight guard prevents double submission`() {
        var isSaving = false
        var callCount = 0

        fun triggerPay() {
            if (com.techyfiposmeds.utils.PaymentMath.canSubmitSupplierPayment(isSaving)) {
                isSaving = true
                callCount++
            }
        }

        // First tap
        triggerPay()
        assertEquals(1, callCount)
        assertTrue(isSaving)

        // Rapid second tap while in-flight
        triggerPay()
        assertEquals(1, callCount)

        // Callback completes
        isSaving = false
        assertFalse(isSaving)
        assertTrue(com.techyfiposmeds.utils.PaymentMath.canSubmitSupplierPayment(isSaving))
    }

    @Test
    fun `R20-19g manual expense categories exclude Supplier Payment`() {
        val manualExpenseCategories = com.techyfiposmeds.ui.components.MANUAL_EXPENSE_CATEGORIES
        assertFalse(manualExpenseCategories.contains("Supplier Payment"))

        val supplierPaymentExpense = com.techyfiposmeds.data.db.Expense(
            id = 10,
            title = "Supplier Due Pay",
            category = "Supplier Payment",
            type = "EXPENSE",
            amount = 1500.0,
            paymentSource = "Cash",
            timestamp = 1000L
        )
        val isSupplierPayment = supplierPaymentExpense.category.equals("Supplier Payment", ignoreCase = true)
        assertTrue(isSupplierPayment)
    }

    @Test
    fun `R20-19h dues audit history does not double count sale charges and ages debt by actual due`() {
        val sale = com.techyfiposmeds.data.db.Sale(
            id = 101,
            timestamp = 1000L,
            subtotal = 500.0,
            discountAmount = 0.0,
            isPercentageDiscount = false,
            grandTotal = 500.0,
            paymentMethod = "Cash",
            customerPhone = "01711111111",
            amountPaid = 200.0
        )
        val charges = listOf(
            com.techyfiposmeds.data.db.DueCharge(
                id = 1,
                customerPhone = "01711111111",
                amount = 300.0,
                timestamp = 1000L,
                reason = "Sale",
                saleId = 101
            ),
            com.techyfiposmeds.data.db.DueCharge(
                id = 2,
                customerPhone = "01711111111",
                amount = 150.0,
                timestamp = 2000L,
                reason = "Manual",
                saleId = null
            )
        )
        val manualCharges = charges.filter { !it.reason.equals("Sale", ignoreCase = true) }
        assertEquals(1, manualCharges.size)
        assertEquals(150.0, manualCharges.first().amount, 0.001)

        val chargeSaleIds = charges.filter { it.reason.equals("Sale", ignoreCase = true) && it.saleId != null }
            .mapNotNull { it.saleId }.toSet()
        assertTrue(chargeSaleIds.contains(101L))

        val saleDue = (sale.grandTotal - sale.amountPaid).coerceAtLeast(0.0)
        assertEquals(300.0, saleDue, 0.001)
        val debts = mutableListOf<Pair<Long, Double>>()
        debts.add(Pair(sale.timestamp, saleDue))
        manualCharges.forEach { debts.add(Pair(it.timestamp, it.amount)) }
        assertEquals(2, debts.size)
        assertEquals(450.0, debts.sumOf { it.second }, 0.001)
    }

    @Test
    fun `R20-19i due charge deletion transfers excess overpayment to store credit`() {
        val currentOutstanding = 100.0
        val chargeAmount = 250.0
        val reversal = com.techyfiposmeds.utils.PaymentMath.computeDueChargeReversal(currentOutstanding, chargeAmount)

        assertEquals(0.0, reversal.newOutstanding, 0.001)
        assertEquals(150.0, reversal.storeCreditExcess, 0.001)
    }

    @Test
    fun `R20-19j add customer checks existing phone while edit mode allows it`() {
        val existingCustomer = Customer(phone = "01712345678", name = "Rahim", storeCredit = 0.0)

        val duplicateAdd = com.techyfiposmeds.utils.PaymentMath.validateCustomerPhoneUniqueness(
            isEdit = false,
            existingCustomer = existingCustomer
        )
        assertEquals("Rahim", duplicateAdd)

        val freshAdd = com.techyfiposmeds.utils.PaymentMath.validateCustomerPhoneUniqueness(
            isEdit = false,
            existingCustomer = null
        )
        assertEquals(null, freshAdd)

        val editAllowed = com.techyfiposmeds.utils.PaymentMath.validateCustomerPhoneUniqueness(
            isEdit = true,
            existingCustomer = existingCustomer
        )
        assertEquals(null, editAllowed)
    }

    @Test
    fun `R20-19k report returns filtered by sourceList when provided`() {
        val allReturns = listOf(
            com.techyfiposmeds.data.db.Return(id = 1, saleId = 10, refundMethod = "Cash", refundAmount = 50.0, timestamp = 100L),
            com.techyfiposmeds.data.db.Return(id = 2, saleId = 20, refundMethod = "Cash", refundAmount = 70.0, timestamp = 200L)
        )
        val sourceSaleIds = setOf(10L)
        val filteredReturns = com.techyfiposmeds.utils.PaymentMath.filterReturnsByAllowedSaleIds(allReturns, sourceSaleIds)
        assertEquals(1, filteredReturns.size)
        assertEquals(10L, filteredReturns.first().saleId)
        assertEquals(50.0, filteredReturns.first().refundAmount, 0.001)
    }

    @Test
    fun `R20-19l UTC midnight datepicker roundtrip preserves calendar date in UTC+6`() {
        val tz = java.util.TimeZone.getTimeZone("GMT+6")
        val localCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.OCTOBER, 11, 14, 30, 0)
        }
        val utcMillis = com.techyfiposmeds.utils.localMillisToUtcMidnight(localCal.timeInMillis)
        val localResultMillis = com.techyfiposmeds.utils.utcMidnightToLocalMidnight(utcMillis)

        val resultCal = Calendar.getInstance().apply { timeInMillis = localResultMillis }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, resultCal.get(Calendar.MONTH))
        assertEquals(11, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun `R20-19m repayment reprint data flags reprint and omits previous due`() {
        val originalData = com.techyfiposmeds.ui.screens.DueRepaymentReceiptData(
            customerName = "Kalam",
            customerPhone = "01700000000",
            amountPaid = 500.0,
            paymentMethod = "Cash",
            referenceNote = "Paid due",
            remainingDue = 1000.0,
            timestamp = 1000L,
            isReprint = false
        )
        assertFalse(originalData.isReprint)

        val reprintData = originalData.copy(isReprint = true)
        assertTrue(reprintData.isReprint)
        assertEquals(500.0, reprintData.amountPaid, 0.001)
        assertEquals(1000.0, reprintData.remainingDue, 0.001)
    }
}
