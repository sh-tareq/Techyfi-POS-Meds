package com.techyfiposmeds.ui.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.techyfiposmeds.data.db.Product
import com.techyfiposmeds.data.db.SplitPaymentEntry
import com.techyfiposmeds.data.db.RegisterClosing
import com.techyfiposmeds.ui.screens.registerShiftStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeldCartPayloadTest {
    @Test
    fun versionedPayloadPreservesQuickSaleNotesAndCheckoutState() {
        val quickSale = Product(
            barcode = "CUSTOM-123",
            name = "Delivery fee",
            category = "Quick Sale",
            purchasePrice = 0.0,
            sellingPrice = 80.0,
            stockQty = Double.MAX_VALUE,
            reorderLevel = 0,
            unit = "Service"
        )
        val original = HeldCartPayload(
            items = listOf(CartItem(quickSale, 2.0, "outside city")),
            customerName = "Rahim", customerPhone = "01700000000",
            discountAmount = 12.0, isPercentageDiscount = false,
            priceMode = PriceMode.MRP,
            paymentMethod = "Split",
            isSplitPayment = true,
            splitPayments = listOf(SplitPaymentEntry("Cash", 100.0), SplitPaymentEntry("bKash", 60.0, walletPhone = "01700000000")),
            loyaltyPhone = "01800000000",
            loyaltyDiscount = 10.0
        )

        val restored = decodeHeldCartPayload(encodeHeldCartPayload(original))

        assertEquals(PriceMode.MRP, restored.priceMode)
        assertEquals("Rahim", restored.customerName)
        assertEquals("01700000000", restored.customerPhone)
        assertEquals(12.0, restored.discountAmount, 0.001)
        assertEquals("Split", restored.paymentMethod)
        assertTrue(restored.isSplitPayment)
        assertEquals(2, restored.splitPayments.size)
        assertEquals("Delivery fee", restored.items.single().product.name)
        assertEquals("outside city", restored.items.single().note)
        assertEquals(10.0, restored.loyaltyDiscount, 0.001)
    }

    @Test
    fun legacyArrayPayloadStillOpens() {
        val restored = decodeHeldCartPayload("[{\"barcode\":\"P-1\",\"quantity\":3}]")

        assertEquals("P-1", restored.items.single().product.barcode)
        assertEquals(3.0, restored.items.single().quantity, 0.001)
        assertEquals(PriceMode.DISCOUNTED, restored.priceMode)
    }

    @Test
    fun registerShiftStartsAfterLatestCloseInsteadOfRepeatingTheWholeDay() {
        val todayStart = 1_000L
        val closings = listOf(
            RegisterClosing(
                date = "2026-08-30", startingFloat = 0.0, cashSales = 100.0,
                digitalSales = 0.0, cashIn = 0.0, cashOut = 0.0, expectedCash = 100.0,
                actualCountedCash = 100.0, variance = 0.0, timestamp = 5_000L
            ),
            RegisterClosing(
                date = "2026-08-30", startingFloat = 0.0, cashSales = 50.0,
                digitalSales = 0.0, cashIn = 0.0, cashOut = 0.0, expectedCash = 50.0,
                actualCountedCash = 50.0, variance = 0.0, timestamp = 3_000L
            )
        )

        assertEquals(5_001L, registerShiftStart(todayStart, closings))
    }
}
