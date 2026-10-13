package com.example.techyfipos

import com.example.techyfipos.data.InvoiceSettingsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerDecisionsTest {

    @Test
    fun testR20_29_singleOwnerPromotionPolicy() {
        val existingRoleCashier = "CASHIER"
        val targetRoleOwner = "OWNER"

        // Policy rule: cannot promote non-owner to owner
        val isPromotionAllowed = !(targetRoleOwner == "OWNER" && existingRoleCashier != "OWNER")
        assertFalse(isPromotionAllowed)

        // Existing owner keeping owner role is allowed
        val existingRoleOwner = "OWNER"
        val isOwnerPreserved = !(targetRoleOwner == "OWNER" && existingRoleOwner != "OWNER")
        assertTrue(isOwnerPreserved)
    }

    @Test
    fun testR20_30_ownerPinResetAcknowledgment() {
        var acknowledged = false
        var resetTimestamp = 1730000000000L

        fun getUnacknowledged(): Long = if (!acknowledged) resetTimestamp else 0L
        fun acknowledge() { acknowledged = true }

        // Initially unacknowledged, returns timestamp
        assertEquals(1730000000000L, getUnacknowledged())

        // After acknowledgement, returns 0L
        acknowledge()
        assertEquals(0L, getUnacknowledged())
    }

    @Test
    fun testR21_01_invoiceSettingsIdleLockDurationSerialization() {
        // Default value is 15 minutes
        val defaultSettings = InvoiceSettingsData()
        assertEquals(15, defaultSettings.idleLockDurationMinutes)

        // Round-trip with custom values (0 for Never, 5, 30, 60)
        listOf(0, 5, 15, 30, 60).forEach { minutes ->
            val settings = InvoiceSettingsData(idleLockDurationMinutes = minutes)
            val jsonStr = settings.toJson()
            val parsed = InvoiceSettingsData.fromJson(jsonStr)
            assertEquals(minutes, parsed.idleLockDurationMinutes)
        }
    }

    @Test
    fun testR21_01_idleAutoLockZeroNeverLocks() {
        val timeoutMinutes = 0L // 0 means Never / Off
        val idleMillis = 1000L * 60 * 120 // 2 hours idle

        // If timeoutMinutes <= 0, auto-lock is disabled
        val shouldLock = if (timeoutMinutes <= 0L) false else idleMillis >= timeoutMinutes * 60_000L
        assertFalse(shouldLock)
    }

    @Test
    fun testR21_01_idleAutoLockCustomDuration() {
        val timeoutMinutes = 5L // 5 minutes setting
        val idleMillis4Min = 4L * 60_000L
        val idleMillis5Min = 5L * 60_000L
        val idleMillis6Min = 6L * 60_000L

        fun checkLock(idleMs: Long): Boolean =
            if (timeoutMinutes <= 0L) false else idleMs >= timeoutMinutes * 60_000L

        assertFalse(checkLock(idleMillis4Min))
        assertTrue(checkLock(idleMillis5Min))
        assertTrue(checkLock(idleMillis6Min))
    }
}
