package com.techyfiposmeds.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HeldCartSerializationTest {

    @Test
    fun `held cart preserves store credit and voucher code across serialization roundtrip`() {
        val payload = HeldCartPayload(
            items = emptyList(),
            priceMode = PriceMode.DISCOUNTED,
            paymentMethod = "Cash",
            storeCreditApplied = 175.50,
            voucherCode = "VOUCHER-TEST-99"
        )

        val json = encodeHeldCartPayload(payload)
        val decoded = decodeHeldCartPayload(json)

        assertNotNull(decoded)
        assertEquals(175.50, decoded!!.storeCreditApplied, 0.001)
        assertEquals("VOUCHER-TEST-99", decoded.voucherCode)
        assertEquals(PriceMode.DISCOUNTED, decoded.priceMode)
        assertEquals("Cash", decoded.paymentMethod)
    }

    @Test
    fun `legacy held cart without store credit defaults to zero and null voucher`() {
        val legacyJson = """
            {
                "items": [],
                "priceMode": "Retail",
                "paymentMethod": "Cash"
            }
        """.trimIndent()

        val decoded = decodeHeldCartPayload(legacyJson)

        assertNotNull(decoded)
        assertEquals(0.0, decoded!!.storeCreditApplied, 0.001)
        assertEquals(null, decoded.voucherCode)
    }

    @Test
    fun `held cart preserves voucher balance and customer phone across serialization roundtrip`() {
        val payload = HeldCartPayload(
            items = emptyList(),
            priceMode = PriceMode.DISCOUNTED,
            paymentMethod = "Cash",
            storeCreditApplied = 250.0,
            voucherCode = "VOUCHER-RESTORE-123",
            voucherOriginalBalance = 500.0,
            voucherRemainingBalance = 250.0,
            voucherCustomerPhone = "01711122233"
        )

        val json = encodeHeldCartPayload(payload)
        val decoded = decodeHeldCartPayload(json)

        assertNotNull(decoded)
        assertEquals(250.0, decoded!!.storeCreditApplied, 0.001)
        assertEquals("VOUCHER-RESTORE-123", decoded.voucherCode)
        assertEquals(500.0, decoded.voucherOriginalBalance!!, 0.001)
        assertEquals(250.0, decoded.voucherRemainingBalance!!, 0.001)
        assertEquals("01711122233", decoded.voucherCustomerPhone)
    }
}
