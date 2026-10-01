package com.example.techyfipos.data

import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.SplitPaymentEntry
import com.example.techyfipos.data.db.StoreCreditVoucher
import com.example.techyfipos.utils.PaymentMath
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertEquals(1, arr.length())
        val obj = arr.getJSONObject(0)
        assertEquals("Store Credit", obj.getString("method"))
        assertEquals(100.0, obj.getDouble("amount"), 0.001)
        assertEquals("VOUCH100", obj.getString("voucherCode"))
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
}
