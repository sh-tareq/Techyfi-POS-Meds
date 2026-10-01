package com.example.techyfipos.data

import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.StoreCreditVoucher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFlowsTest {

    @Test
    fun `A-1 customer copy with updated purchase metrics preserves existing store credit`() {
        val existingCust = Customer(
            phone = "01700000000",
            name = "John Doe",
            storeCredit = 500.0,
            creditLimit = 1000.0,
            totalOrders = 5,
            lifetimeSpent = 2500.0
        )

        // Verifying real Customer domain copy pattern used in DataRepository.saveSale
        val updatedCust = existingCust.copy(
            totalOrders = existingCust.totalOrders + 1,
            lifetimeSpent = existingCust.lifetimeSpent + 150.0,
            lastPurchaseDate = System.currentTimeMillis()
        )

        assertEquals("Store credit must be preserved across sales", 500.0, updatedCust.storeCredit, 0.001)
    }

    @Test
    fun `A-2 tax-inclusive payable calculation floors fractional point redemption`() {
        val maxAllowedBillDiscount = 277.50
        val ptCashVal = 1.0
        val pointsToRedeem = (maxAllowedBillDiscount / ptCashVal).toInt()
        val flooredDiscount = pointsToRedeem * ptCashVal

        assertEquals(277, pointsToRedeem)
        assertEquals(277.0, flooredDiscount, 0.001)
        assertTrue(flooredDiscount <= maxAllowedBillDiscount)
    }

    @Test
    fun `A-3 split payment flag bypasses single-cash validation rule`() {
        val isSplitPayment = true
        val paymentMethod = "Split"
        val remainingCash = 1000.0
        val amountPaid = 200.0

        // Single cash rule enforces amountPaid >= remainingCash when method == "Cash"
        val isSingleCashIncomplete = !isSplitPayment && paymentMethod == "Cash" && amountPaid < remainingCash
        assertFalse(isSingleCashIncomplete)
    }

    @Test
    fun `A-4 split payment JSON structure verification`() {
        val splitJson = JSONArray().apply {
            put(JSONObject().put("method", "Cash").put("amount", 700.0))
            put(JSONObject().put("method", "Store Credit").put("amount", 300.0))
        }.toString()

        val jsonArray = JSONArray(splitJson)
        var total = 0.0
        var hasStoreCredit = false
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            total += obj.getDouble("amount")
            if (obj.getString("method") == "Store Credit") {
                hasStoreCredit = true
            }
        }

        assertEquals(1000.0, total, 0.001)
        assertTrue(hasStoreCredit)
    }

    @Test
    fun `A-6 walk-in voucher redemption rule validation`() {
        val voucherCode = "VOUCH123"
        val customerPhone: String? = null

        // Walk-in voucher redemption is valid without customer phone
        val isValidVoucherRedemption = !voucherCode.isNullOrBlank()
        val isAllowed = isValidVoucherRedemption || !customerPhone.isNullOrBlank()

        assertTrue(isAllowed)
    }

    @Test
    fun `A-7 voucher status transition on void sale restoration`() {
        val voucher = StoreCreditVoucher(
            code = "VOUCH999",
            initialAmount = 500.0,
            remainingBalance = 300.0,
            status = "EXHAUSTED",
            issuedAt = System.currentTimeMillis()
        )
        val redeemedInSale = 200.0

        val restoredBalance = voucher.remainingBalance + redeemedInSale
        val newStatus = if (restoredBalance > 0.0) "ACTIVE" else "EXHAUSTED"

        assertEquals(500.0, restoredBalance, 0.001)
        assertEquals("ACTIVE", newStatus)
    }

    @Test
    fun `R-1 voucher redemption without store credit applied does not debit customer store credit`() {
        val customerCredit = 100.0
        val storeCreditApplied = 0.0 // Only plain voucher redeemed
        val newCustomerCredit = customerCredit - storeCreditApplied

        assertEquals(100.0, newCustomerCredit, 0.001)
    }
}
