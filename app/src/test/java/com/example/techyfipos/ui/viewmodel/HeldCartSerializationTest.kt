package com.example.techyfipos.ui.viewmodel

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
}
