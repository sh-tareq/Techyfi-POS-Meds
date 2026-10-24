package com.techyfiposmeds.ui.screens

import com.techyfiposmeds.data.db.SplitPaymentEntry
import com.techyfiposmeds.utils.PaymentMath
import org.junit.Assert.assertEquals
import org.junit.Test

class DueSaleAmountTest {
    @Test
    fun dueSaleTreatsGrandTotalAsNewDueNotAmountPaid() {
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 850.0,
            paymentMethod = "Due",
            amountPaid = 0.0
        )

        assertEquals(0.0, breakdown.effectiveAmountPaid, 0.001)
        assertEquals(850.0, breakdown.dueAmount, 0.001)
    }

    @Test
    fun splitSaleExcludesDuePortionFromAmountPaid() {
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 1_000.0,
            isSplitPayment = true,
            splitPayments = listOf(
                SplitPaymentEntry(method = "Cash", amount = 600.0),
                SplitPaymentEntry(method = "Due", amount = 400.0)
            )
        )

        assertEquals(600.0, breakdown.effectiveAmountPaid, 0.001)
        assertEquals(400.0, breakdown.dueAmount, 0.001)
    }

    @Test
    fun cashSaleZeroTenderReturnsZeroAndDoesNotDefaultToGrandTotal() {
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 500.0,
            paymentMethod = "Cash",
            amountPaid = 0.0
        )

        assertEquals(0.0, breakdown.effectiveAmountPaid, 0.001)
    }

    @Test
    fun cashSaleWithExplicitTenderReturnsExactEnteredAmount() {
        val breakdown = PaymentMath.calculatePaymentBreakdown(
            grandTotal = 850.0,
            paymentMethod = "Cash",
            amountPaid = 1000.0
        )

        assertEquals(1000.0, breakdown.effectiveAmountPaid, 0.001)
        assertEquals(150.0, breakdown.changeAmount, 0.001)
    }
}
