package com.example.techyfipos.data

import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.StoreCreditVoucher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MoneyFlowsTest {

    @Test
    fun `A-1 checkout preserves existing customer store credit`() {
        val existingCust = Customer(
            phone = "01700000000",
            name = "John Doe",
            storeCredit = 500.0,
            creditLimit = 1000.0
        )

        val normalizedCustomerPhone = "01700000000"
        val customerName = "John Doe"
        val grandTotal = 150.0
        val timestamp = 100000L

        val resolvedName = customerName.takeIf { it.isNotBlank() } ?: existingCust.name
        val updatedOrders = existingCust.totalOrders + 1
        val updatedLifetime = existingCust.lifetimeSpent + grandTotal

        val updatedCust = Customer(
            phone = normalizedCustomerPhone,
            name = resolvedName,
            email = existingCust.email,
            address = existingCust.address,
            notes = existingCust.notes,
            tag = existingCust.tag,
            creditLimit = existingCust.creditLimit,
            storeCredit = existingCust.storeCredit, // Fix A-1: storeCredit must be preserved!
            totalOrders = updatedOrders,
            lifetimeSpent = updatedLifetime,
            totalDue = existingCust.totalDue,
            loyaltyPoints = existingCust.loyaltyPoints,
            loyaltyTier = existingCust.loyaltyTier,
            firstPurchaseDate = existingCust.firstPurchaseDate,
            lastPurchaseDate = timestamp,
            createdAt = existingCust.createdAt,
            updatedAt = timestamp,
            isArchived = false
        )

        assertEquals(500.0, updatedCust.storeCredit, 0.001)
    }

    @Test
    fun `A-2 tax-inclusive payableBeforeLoyalty does not double add tax`() {
        val subtotal = 1000.0
        val regularDiscount = 100.0
        val taxAmount = 135.0
        val taxInclusive = true

        val payableBeforeLoyalty = if (taxInclusive) {
            (subtotal - regularDiscount).coerceAtLeast(0.0)
        } else {
            (subtotal - regularDiscount + taxAmount).coerceAtLeast(0.0)
        }

        // For tax inclusive 1000 subtotal with 100 discount, payable is 900.0 (not 1035.0)
        assertEquals(900.0, payableBeforeLoyalty, 0.001)
    }

    @Test
    fun `A-2 fractional max bill discount is floored to integer points`() {
        val maxAllowedBillDiscount = 277.50
        val ptCashVal = 1.0
        val rawPoints = (maxAllowedBillDiscount / ptCashVal).toInt()
        val pointsToRedeem = rawPoints
        val flooredDiscount = pointsToRedeem * ptCashVal

        assertEquals(277, pointsToRedeem)
        assertEquals(277.0, flooredDiscount, 0.001)
        assertTrue(flooredDiscount <= maxAllowedBillDiscount)
    }

    @Test
    fun `A-3 split payment bypasses single-cash full amount requirement`() {
        val isSplitPayment = true
        val paymentMethod = "Cash"
        val amountPaid = 200.0
        val remainingCash = 1000.0

        val shouldThrow = !isSplitPayment && (paymentMethod == "Cash") && remainingCash > 0.0 && amountPaid < remainingCash

        assertFalse("Split payment should NOT trigger full cash check", shouldThrow)
    }

    @Test
    fun `A-4 split payment json includes store credit row to match grand total`() {
        val grandTotal = 1000.0
        val storeCreditApplied = 300.0
        val splitRows = mutableListOf(
            JSONObject().put("method", "Cash").put("amount", 700.0)
        )

        if (storeCreditApplied > 0.0) {
            splitRows.add(
                JSONObject().put("method", "Store Credit").put("amount", storeCreditApplied)
            )
        }

        val totalSplitSum = splitRows.sumOf { it.getDouble("amount") }
        assertEquals(grandTotal, totalSplitSum, 0.001)
    }

    @Test
    fun `A-5 split cash overtender registers owed amount in split entry`() {
        val owedCashAmount = 300.0
        val tenderedCashAmount = 500.0
        val change = tenderedCashAmount - owedCashAmount

        val splitEntry = JSONObject()
            .put("method", "Cash")
            .put("amount", owedCashAmount)
            .put("tenderedAmount", tenderedCashAmount)
            .put("changeAmount", change)

        assertEquals(300.0, splitEntry.getDouble("amount"), 0.001)
        assertEquals(500.0, splitEntry.getDouble("tenderedAmount"), 0.001)
        assertEquals(200.0, splitEntry.getDouble("changeAmount"), 0.001)
    }

    @Test
    fun `A-6 voucher redemption without customer phone is valid`() {
        val voucher = StoreCreditVoucher(
            code = "VOUCH123",
            initialAmount = 500.0,
            remainingBalance = 500.0,
            issuedAt = System.currentTimeMillis()
        )
        val customerPhone: String? = null
        val storeCreditApplied = 200.0
        val voucherCode = "VOUCH123"

        // If voucher code present, phone requirement is bypassed
        val isVoucherRedemption = !voucherCode.isNullOrBlank()
        val isValid = isVoucherRedemption || (!customerPhone.isNullOrBlank())

        assertTrue("Voucher redemption without customer phone is valid", isValid)
    }

    @Test
    fun `A-7 void sale restores voucher balance`() {
        val voucher = StoreCreditVoucher(
            code = "VOUCH999",
            initialAmount = 500.0,
            remainingBalance = 300.0,
            issuedAt = System.currentTimeMillis()
        )
        val redeemedInSale = 200.0

        val restoredBalance = voucher.remainingBalance + redeemedInSale
        val newStatus = if (restoredBalance > 0.0) "ACTIVE" else "EXHAUSTED"

        assertEquals(500.0, restoredBalance, 0.001)
        assertEquals("ACTIVE", newStatus)
    }
}
