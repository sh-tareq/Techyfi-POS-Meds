package com.example.techyfipos.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `storeCreditApplied defaults to zero and preserves explicit value`() {
        val legacy = buildBackwardCompatibleSale(
            timestamp = 123456L,
            grandTotal = 100.0
        )
        assertEquals(0.0, legacy.storeCreditApplied, 0.001)

        val withCredit = buildBackwardCompatibleSale(
            timestamp = 123456L,
            grandTotal = 100.0,
            storeCreditApplied = 25.5
        )
        assertEquals(25.5, withCredit.storeCreditApplied, 0.001)
    }

    @Test
    fun `taxable property defaults to true and preserves false for non-taxable sale items`() {
        val defaultItem = buildBackwardCompatibleSaleItem(
            saleId = 1,
            productBarcode = "CUSTOM-100",
            productName = "Standard Item",
            price = 50.0,
            quantity = 1.0
        )
        assertTrue(defaultItem.taxable)

        val nonTaxableItem = buildBackwardCompatibleSaleItem(
            saleId = 1,
            productBarcode = "CUSTOM-200",
            productName = "Non-taxable Item",
            price = 100.0,
            quantity = 1.0,
            taxable = false
        )
        assertFalse(nonTaxableItem.taxable)
    }

    @Test
    fun `json backup restores non-taxable items with taxable false and legacy backups as true`() {
        val nonTaxableJson = JSONObject().apply {
            put("productBarcode", "CUSTOM-300")
            put("productName", "Service Fee")
            put("price", 150.0)
            put("quantity", 1.0)
            put("taxable", false)
        }
        val restoredNonTaxable = buildBackwardCompatibleSaleItem(
            saleId = 1,
            productBarcode = nonTaxableJson.optString("productBarcode"),
            productName = nonTaxableJson.optString("productName"),
            price = nonTaxableJson.optDouble("price"),
            quantity = nonTaxableJson.optDouble("quantity"),
            taxable = nonTaxableJson.optBoolean("taxable", true)
        )
        assertFalse(restoredNonTaxable.taxable)

        val legacyJson = JSONObject().apply {
            put("productBarcode", "123456")
            put("productName", "Legacy Item")
            put("price", 80.0)
            put("quantity", 2.0)
        }
        val restoredLegacy = buildBackwardCompatibleSaleItem(
            saleId = 1,
            productBarcode = legacyJson.optString("productBarcode"),
            productName = legacyJson.optString("productName"),
            price = legacyJson.optDouble("price"),
            quantity = legacyJson.optDouble("quantity"),
            taxable = legacyJson.optBoolean("taxable", true)
        )
        assertTrue(restoredLegacy.taxable)
    }
}

