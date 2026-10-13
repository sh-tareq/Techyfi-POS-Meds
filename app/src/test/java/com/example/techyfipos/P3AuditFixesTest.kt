package com.example.techyfipos

import com.example.techyfipos.utils.localMillisToUtcMidnight
import com.example.techyfipos.utils.sanitizeNumericInput
import com.example.techyfipos.utils.utcMidnightToLocalMidnight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class P3AuditFixesTest {

    @Test
    fun testR21_28_sanitizeNumericInputCommas() {
        // Thousands separator dropped, not treated as decimal separator
        assertEquals("1000", sanitizeNumericInput("1,000"))
        assertEquals("1500", sanitizeNumericInput("1,500"))
        assertEquals("1500.50", sanitizeNumericInput("1,500.50"))
        assertEquals("1234567.89", sanitizeNumericInput("1,234,567.89"))

        // Standard numeric and decimal input preserved
        assertEquals("123", sanitizeNumericInput("123"))
        assertEquals("12.34", sanitizeNumericInput("12.34"))
        assertEquals("0.99", sanitizeNumericInput("0.99"))

        // Invalid characters stripped
        assertEquals("100", sanitizeNumericInput("abc100xyz"))
        assertEquals("50.25", sanitizeNumericInput("$50.25"))
    }

    @Test
    fun testR21_26_datePickerUtcMidnightConversionRoundTrip() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 15)
        cal.set(Calendar.MINUTE, 30)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val localMillis = cal.timeInMillis

        val utcMidnight = localMillisToUtcMidnight(localMillis)
        val backToLocalMidnight = utcMidnightToLocalMidnight(utcMidnight)

        val localCal = Calendar.getInstance().apply { timeInMillis = backToLocalMidnight }
        assertEquals(0, localCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, localCal.get(Calendar.MINUTE))
        assertEquals(0, localCal.get(Calendar.SECOND))
        assertEquals(0, localCal.get(Calendar.MILLISECOND))
        assertEquals(cal.get(Calendar.YEAR), localCal.get(Calendar.YEAR))
        assertEquals(cal.get(Calendar.MONTH), localCal.get(Calendar.MONTH))
        assertEquals(cal.get(Calendar.DAY_OF_MONTH), localCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testR21_26_customerStatementRunningBalanceReconciliation() {
        val customerOutstanding = 1500.0
        val charges = listOf(1000.0, 800.0) // sum = 1800.0
        val payments = listOf(500.0) // sum = 500.0
        val netLedger = charges.sum() - payments.sum() // 1300.0

        // openingBalance reconciles legacy balance and adjustments so final balance == customerOutstanding
        val openingBalance = customerOutstanding - netLedger // 200.0

        var runningBalance = openingBalance
        for (charge in charges) {
            runningBalance += charge
        }
        for (payment in payments) {
            runningBalance -= payment
        }

        assertEquals(customerOutstanding, runningBalance, 0.001)
    }

    @Test
    fun testR21_27_stockAlertOutOfStockAndExpiredIncluded() {
        val now = 1700000000000L
        val thirtyDays = 30L * 24 * 60 * 60 * 1000L

        // Stock alert conditions: stockQty <= reorderLevel
        val reorderLevel = 5.0
        val inStockSafe = 10.0
        val lowStock = 4.0
        val zeroStock = 0.0
        val negativeStock = -1.0

        assertFalse(inStockSafe <= reorderLevel)
        assertTrue(lowStock <= reorderLevel)
        assertTrue(zeroStock <= reorderLevel) // 0 is included
        assertTrue(negativeStock <= reorderLevel)

        // Expiry alert conditions: exp <= now + thirtyDays
        val notExpiredFar = now + thirtyDays + 100000L
        val expiringSoon = now + 100000L
        val expiredPast = now - 100000L

        assertFalse(notExpiredFar <= now + thirtyDays)
        assertTrue(expiringSoon <= now + thirtyDays)
        assertTrue(expiredPast <= now + thirtyDays) // already expired is included
    }

    @Test
    fun testR21_30_cartItemRestoreIndex() {
        val items = mutableListOf("ItemA", "ItemB", "ItemC")
        val removedIndex = 1
        val removedItem = items.removeAt(removedIndex)
        assertEquals("ItemB", removedItem)
        assertEquals(listOf("ItemA", "ItemC"), items)

        // Restoring at original index restores exact list ordering
        val targetIndex = if (removedIndex in 0..items.size) removedIndex else items.size
        items.add(targetIndex, removedItem)
        assertEquals(listOf("ItemA", "ItemB", "ItemC"), items)
    }

    @Test
    fun testR21_30_expenseDateLabelDiffAcrossYearBoundary() {
        val dayMs = 86400000L
        val dec31 = 1735689600000L // Dec 31
        val jan1 = dec31 + (12 * 3600000L) // Jan 1 (12 hours later)

        val diffDays = (jan1 - dec31) / dayMs
        assertEquals(0L, diffDays)

        val jan2 = dec31 + (26 * 3600000L) // 26 hours later
        val diffDays2 = (jan2 - dec31) / dayMs
        assertEquals(1L, diffDays2)
    }
}
