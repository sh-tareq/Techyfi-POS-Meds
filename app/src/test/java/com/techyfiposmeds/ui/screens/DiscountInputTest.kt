package com.techyfiposmeds.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscountInputTest {
    @Test
    fun acceptsDecimalComma() {
        assertEquals("12.5", sanitizeDiscountInput("12,5"))
    }

    @Test
    fun acceptsBengaliDigits() {
        assertEquals("125", sanitizeDiscountInput("১২৫"))
    }

    @Test
    fun removesUnexpectedCharactersAndExtraSeparators() {
        assertEquals("12.50", sanitizeDiscountInput("৳12.5.0"))
    }

    @Test
    fun replacesLeadingZeroAsUserTypes() {
        assertEquals("10", sanitizeDiscountInput("010"))
        assertEquals("0.5", sanitizeDiscountInput("00.5"))
    }
}
