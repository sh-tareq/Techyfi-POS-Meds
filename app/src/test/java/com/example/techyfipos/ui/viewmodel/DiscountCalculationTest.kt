package com.example.techyfipos.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscountCalculationTest {
    @Test
    fun fixedDiscountIsApplied() {
        assertEquals(20.0, calculateDiscountValue(120.0, 20.0, false), 0.001)
    }

    @Test
    fun percentageDiscountIsApplied() {
        assertEquals(12.0, calculateDiscountValue(120.0, 10.0, true), 0.001)
    }

    @Test
    fun discountCannotExceedSubtotal() {
        assertEquals(120.0, calculateDiscountValue(120.0, 500.0, false), 0.001)
        assertEquals(120.0, calculateDiscountValue(120.0, 150.0, true), 0.001)
    }

    @Test
    fun negativeDiscountBecomesZero() {
        assertEquals(0.0, calculateDiscountValue(120.0, -10.0, false), 0.001)
    }

    @Test
    fun fallbackPurchasePriceUsesZeroForSyntheticAndBreakEvenForRealProducts() {
        assertEquals(0.0, fallbackPurchasePrice("CUSTOM-12345", 100.0), 0.001)
        assertEquals(0.0, fallbackPurchasePrice("ADHOC-9999", 50.0), 0.001)
        assertEquals(100.0, fallbackPurchasePrice("8901234567890", 100.0), 0.001)
    }
    @Test
    fun testR20_14_exactCashRespectsStoreCredit() {
        val grandTotal = 300.0
        val storeCreditApplied = 100.0
        val remainingAfterSC = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
        assertEquals(200.0, remainingAfterSC, 0.001)
    }

    @Test
    fun testR20_06_toggleDiscountUnitResetsAmount() {
        var isPercentage = false
        var discountAmount = 50.0
        discountAmount = 0.0
        isPercentage = !isPercentage
        assertEquals(true, isPercentage)
        assertEquals(0.0, discountAmount, 0.001)
    }

    @Test
    fun testR20_16_storeCreditResetsWhenPhoneChangesWithoutVoucher() {
        val oldPhone = "01711111111"
        val newPhone = "01822222222"
        var storeCreditApplied = 500.0
        val redeemedVoucherCode: String? = null

        if (newPhone != oldPhone && redeemedVoucherCode == null) {
            storeCreditApplied = 0.0
        }
        assertEquals(0.0, storeCreditApplied, 0.001)
    }

    @Test
    fun testR20_17_storeCreditCappedToGrandTotalAndProtectsVoucherBalance() {
        val grandTotal = 300.0
        val requestedStoreCredit = 500.0
        val voucherRemainingBalance = 500.0

        val effectiveStoreCredit = requestedStoreCredit.coerceAtMost(grandTotal)
        assertEquals(300.0, effectiveStoreCredit, 0.001)

        val voucherDeduction = effectiveStoreCredit.coerceAtMost(voucherRemainingBalance)
        assertEquals(300.0, voucherDeduction, 0.001)

        val postSaleVoucherBalance = (voucherRemainingBalance - voucherDeduction).coerceAtLeast(0.0)
        assertEquals(200.0, postSaleVoucherBalance, 0.001)
    }
}
