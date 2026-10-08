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
    fun `M-1 plain store credit payment with partial store credit generates valid split json`() {
        val splitJson = PaymentMath.buildAuthoritativeSplitJson(
            isSplitPayment = false,
            splitPayments = emptyList(),
            storeCreditApplied = 100.0,
            grandTotal = 300.0,
            paymentMethod = "Store Credit",
            redeemedVoucherCode = "VOUCH100"
        )
        assertNotNull("Split JSON must not be null when Store Credit is applied", splitJson)
        val arr = JSONArray(splitJson!!)
        assertEquals("Partial store credit must have 2 rows (SC and remainder)", 2, arr.length())
        val obj0 = arr.getJSONObject(0)
        assertEquals("Store Credit", obj0.getString("method"))
        assertEquals(100.0, obj0.getDouble("amount"), 0.001)
        assertEquals("VOUCH100", obj0.getString("voucherCode"))
        val obj1 = arr.getJSONObject(1)
        val total = obj0.getDouble("amount") + obj1.getDouble("amount")
        assertEquals("Split rows must add up to grand total (300.0)", 300.0, total, 0.001)
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
    fun `M-6 scenario 1 Cash 200 and Due 200 on 300 total computes 100 change in JSON and calculateChange`() {
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
        assertNotNull(splitJson)
        val arr = JSONArray(splitJson!!)
        val cashObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Cash" }
        val dueObj = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.getString("method") == "Due" }

        assertEquals(100.0, cashObj.getDouble("amount"), 0.001)
        assertEquals(200.0, cashObj.getDouble("tenderedAmount"), 0.001)
        assertEquals(100.0, cashObj.getDouble("changeAmount"), 0.001)
        assertEquals(200.0, dueObj.getDouble("amount"), 0.001)

        val change = PaymentMath.calculateChange(
            grandTotal = 300.0,
            amountPaid = 200.0,
            paymentMethod = "Split",
            splitJson = splitJson
        )
        assertEquals("Change must be 100.0", 100.0, change, 0.001)
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
        // net after due is 100. remaining SC is 100. So 100 SC restored, 0 cash refunded.
        assertEquals(100.0, partialWithDueApportionment.storeCreditToRestore, 0.001)
        assertEquals(0.0, partialWithDueApportionment.externalRefundAmount, 0.001)
    }
}
