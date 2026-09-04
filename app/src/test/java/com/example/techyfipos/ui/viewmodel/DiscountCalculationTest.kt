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
}
