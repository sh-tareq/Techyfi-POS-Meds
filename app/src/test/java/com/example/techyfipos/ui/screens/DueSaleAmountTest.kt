package com.example.techyfipos.ui.screens

import com.example.techyfipos.data.db.SplitPaymentEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class DueSaleAmountTest {
    @Test
    fun dueSaleTreatsGrandTotalAsNewDueNotAmountPaid() {
        val paid = calculateEffectiveAmountPaid(
            isSplitPayment = false,
            paymentMethod = "Due",
            enteredAmountPaid = 0.0,
            grandTotal = 850.0,
            splitPayments = emptyList()
        )

        assertEquals(0.0, paid, 0.001)
        assertEquals(850.0, 850.0 - paid, 0.001)
    }

    @Test
    fun splitSaleExcludesDuePortionFromAmountPaid() {
        val paid = calculateEffectiveAmountPaid(
            isSplitPayment = true,
            paymentMethod = "Split",
            enteredAmountPaid = 0.0,
            grandTotal = 1_000.0,
            splitPayments = listOf(
                SplitPaymentEntry(method = "Cash", amount = 600.0),
                SplitPaymentEntry(method = "Due", amount = 400.0)
            )
        )

        assertEquals(600.0, paid, 0.001)
    }

    @Test
    fun cashSaleZeroTenderReturnsZeroAndDoesNotDefaultToGrandTotal() {
        val paid = calculateEffectiveAmountPaid(
            isSplitPayment = false,
            paymentMethod = "Cash",
            enteredAmountPaid = 0.0,
            grandTotal = 500.0,
            splitPayments = emptyList()
        )

        assertEquals(0.0, paid, 0.001)
    }

    @Test
    fun cashSaleWithExplicitTenderReturnsExactEnteredAmount() {
        val paid = calculateEffectiveAmountPaid(
            isSplitPayment = false,
            paymentMethod = "Cash",
            enteredAmountPaid = 1000.0,
            grandTotal = 850.0,
            splitPayments = emptyList()
        )

        assertEquals(1000.0, paid, 0.001)
    }
}
