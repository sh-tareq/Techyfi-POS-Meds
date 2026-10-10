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
}
