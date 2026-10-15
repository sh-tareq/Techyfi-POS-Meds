package com.example.techyfipos

import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.utils.PaymentMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class OwnerDecisionsTest {

    @Test
    fun testR20_29_singleOwnerPromotionPolicy() {
        val existingRoleCashier = AppRoles.CASHIER
        val targetRoleOwner = AppRoles.OWNER

        // Policy rule: cannot promote non-owner to owner
        val isPromotionAllowed = AppRoles.canPromoteToOwner(existingRoleCashier, targetRoleOwner)
        assertFalse(isPromotionAllowed)

        // Existing owner keeping owner role is allowed
        val existingRoleOwner = AppRoles.OWNER
        val isOwnerPreserved = AppRoles.canPromoteToOwner(existingRoleOwner, targetRoleOwner)
        assertTrue(isOwnerPreserved)

        // Manager cannot be promoted to owner
        val existingRoleManager = AppRoles.MANAGER
        assertFalse(AppRoles.canPromoteToOwner(existingRoleManager, targetRoleOwner))
    }

    @Test
    fun testR20_30_ownerPinResetAcknowledgment() {
        val mockContext = Mockito.mock(android.content.Context::class.java)
        val mockPrefs = Mockito.mock(android.content.SharedPreferences::class.java)
        val mockEditor = Mockito.mock(android.content.SharedPreferences.Editor::class.java)
        val mockDb = Mockito.mock(com.example.techyfipos.data.db.AppDatabase::class.java, Mockito.RETURNS_DEEP_STUBS)
        val mockDataStore = Mockito.mock(com.example.techyfipos.data.SettingsDataStore::class.java, Mockito.RETURNS_DEEP_STUBS)

        var acknowledged = false
        val resetTimestamp = 1730000000000L

        Mockito.`when`(mockContext.getSharedPreferences(Mockito.anyString(), Mockito.anyInt())).thenReturn(mockPrefs)
        Mockito.`when`(mockPrefs.getLong("owner_device_pin_reset_timestamp", 0L)).thenAnswer { resetTimestamp }
        Mockito.`when`(mockPrefs.getBoolean("owner_device_pin_reset_acknowledged", false)).thenAnswer { acknowledged }
        Mockito.`when`(mockPrefs.getBoolean("owner_pin_changed_after_reset", false)).thenAnswer { true }
        Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)
        Mockito.`when`(mockEditor.putBoolean(Mockito.eq("owner_device_pin_reset_acknowledged"), Mockito.anyBoolean())).thenAnswer {
            acknowledged = it.getArgument(1)
            mockEditor
        }

        val repo = com.example.techyfipos.data.DataRepository(mockContext, mockDb, mockDataStore)

        // Initially unacknowledged, returns timestamp
        assertEquals(resetTimestamp, repo.getUnacknowledgedOwnerPinResetTimestamp())

        // After acknowledgement, returns 0L
        repo.acknowledgeOwnerPinReset()
        assertEquals(0L, repo.getUnacknowledgedOwnerPinResetTimestamp())
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

        assertFalse(PaymentMath.isIdleAutoLockTriggered(idleMillis, timeoutMinutes))
    }

    @Test
    fun testR21_01_idleAutoLockCustomDuration() {
        val timeoutMinutes = 5L // 5 minutes setting
        val idleMillis4Min = 4L * 60_000L
        val idleMillis5Min = 5L * 60_000L
        val idleMillis6Min = 6L * 60_000L

        assertFalse(PaymentMath.isIdleAutoLockTriggered(idleMillis4Min, timeoutMinutes))
        assertTrue(PaymentMath.isIdleAutoLockTriggered(idleMillis5Min, timeoutMinutes))
        assertTrue(PaymentMath.isIdleAutoLockTriggered(idleMillis6Min, timeoutMinutes))
    }
}
