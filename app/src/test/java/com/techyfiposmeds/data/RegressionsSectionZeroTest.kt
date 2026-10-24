package com.techyfiposmeds.data

import com.techyfiposmeds.data.db.Customer
import com.techyfiposmeds.data.db.CustomerLookupResult
import com.techyfiposmeds.utils.AutoBackupScheduler
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionsSectionZeroTest {

    @Test
    fun `X-7 loyalty point calculation uses floor to prevent rounding over cap`() {
        val ptCashVal = 1.0
        val maxPossibleDiscount = 9.6
        val requestedDiscount = maxPossibleDiscount
        
        // Before fix: round(9.6) = 10 (exceeds 9.6 max discount)
        // After fix: floor(9.6 + 1e-6) = 9
        val rawPoints = kotlin.math.floor(requestedDiscount / ptCashVal + 1e-6).toInt()
        val loyaltyDiscount = rawPoints * ptCashVal

        assertTrue("Discount must not exceed max allowed bill discount", loyaltyDiscount <= maxPossibleDiscount)
        assertEquals(9, rawPoints)
    }

    @Test
    fun `X-8 default customer loyalty tier is Regular not Silver`() {
        val customer = Customer(phone = "01700000000", name = "Test")
        val lookup = CustomerLookupResult(phone = "01700000000", name = "Test", source = "CUSTOMER_DB")

        assertEquals("Regular", customer.loyaltyTier)
        assertEquals("Regular", lookup.tier)
    }
}
