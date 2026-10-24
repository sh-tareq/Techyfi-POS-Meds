package com.techyfiposmeds.ui.viewmodel

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
        val remainingAfterSC = com.techyfiposmeds.utils.PaymentMath.computeRemainingAfterStoreCredit(grandTotal, storeCreditApplied)
        assertEquals(200.0, remainingAfterSC, 0.001)
    }

    @Test
    fun testR20_06_toggleDiscountUnitResetsAmount() {
        val resetAmount = com.techyfiposmeds.utils.PaymentMath.resetDiscountOnUnitToggle()
        assertEquals(0.0, resetAmount, 0.001)
    }

    @Test
    fun testR20_16_storeCreditResetsWhenPhoneChangesWithoutVoucher() {
        val oldPhone = "01711111111"
        val newPhone = "01822222222"
        val storeCreditApplied = 500.0
        val resolvedCredit = com.techyfiposmeds.utils.PaymentMath.resolveStoreCreditOnPhoneChange(
            oldPhone = oldPhone,
            newPhone = newPhone,
            currentCredit = storeCreditApplied,
            hasRedeemedVoucher = false
        )
        assertEquals(0.0, resolvedCredit, 0.001)

        val preservedWithVoucher = com.techyfiposmeds.utils.PaymentMath.resolveStoreCreditOnPhoneChange(
            oldPhone = oldPhone,
            newPhone = newPhone,
            currentCredit = storeCreditApplied,
            hasRedeemedVoucher = true
        )
        assertEquals(500.0, preservedWithVoucher, 0.001)
    }

    @Test
    fun testR20_17_storeCreditCappedToGrandTotalAndProtectsVoucherBalance() {
        val grandTotal = 300.0
        val requestedStoreCredit = 500.0
        val voucherRemainingBalance = 500.0

        val effectiveStoreCredit = com.techyfiposmeds.utils.PaymentMath.computeEffectiveStoreCredit(requestedStoreCredit, grandTotal)
        assertEquals(300.0, effectiveStoreCredit, 0.001)

        val voucherDeduction = com.techyfiposmeds.utils.PaymentMath.computeVoucherDeduction(effectiveStoreCredit, voucherRemainingBalance)
        assertEquals(300.0, voucherDeduction, 0.001)

        val postSaleVoucherBalance = com.techyfiposmeds.utils.PaymentMath.computePostSaleVoucherBalance(voucherRemainingBalance, voucherDeduction)
        assertEquals(200.0, postSaleVoucherBalance, 0.001)
    }
}
