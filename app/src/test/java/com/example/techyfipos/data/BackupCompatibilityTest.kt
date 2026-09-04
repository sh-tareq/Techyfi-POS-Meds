package com.example.techyfipos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCompatibilityTest {
    @Test
    fun `legacy sale without newer fields restores with safe defaults`() {
        val sale = buildBackwardCompatibleSale(
            id = 42,
            timestamp = 123456L,
            legacyTotal = 250.0,
            paymentMethod = null,
            amountPaid = null
        )

        assertEquals(42L, sale.id)
        assertEquals(250.0, sale.subtotal, 0.001)
        assertEquals(250.0, sale.grandTotal, 0.001)
        assertEquals(250.0, sale.amountPaid, 0.001)
        assertEquals("Cash", sale.paymentMethod)
        assertFalse(sale.isPercentageDiscount)
    }

    @Test
    fun `legacy due sale defaults amount paid to zero`() {
        val sale = buildBackwardCompatibleSale(
            timestamp = 123456L,
            grandTotal = 300.0,
            paymentMethod = "Due"
        )

        assertEquals(0.0, sale.amountPaid, 0.001)
    }

    @Test
    fun `literal null customer values are not restored as text`() {
        val sale = buildBackwardCompatibleSale(
            timestamp = 123456L,
            grandTotal = 100.0,
            customerName = "null",
            customerPhone = "null"
        )

        assertNull(sale.customerName)
        assertNull(sale.customerPhone)
    }

    @Test
    fun `legacy item field defaults still produce visible history line`() {
        val item = buildBackwardCompatibleSaleItem(
            saleId = 7,
            productBarcode = "1234",
            productName = null,
            price = null,
            quantity = null
        )

        assertEquals("1234", item.productName)
        assertEquals(0.0, item.price, 0.001)
        assertEquals(1.0, item.quantity, 0.0001)
    }
}
