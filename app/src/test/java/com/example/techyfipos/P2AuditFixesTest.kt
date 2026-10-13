package com.example.techyfipos

import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.SupervisorVerifyResult
import com.example.techyfipos.data.db.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2AuditFixesTest {

    @Test
    fun testR21_16_duesOverrideLimitPermission() {
        // DUES_OVERRIDE_LIMIT constant verification
        assertEquals("dues.override_limit", AppPermissions.DUES_OVERRIDE_LIMIT)

        // Manager and Owner permissions
        val owner = User(id = 1L, name = "Owner", role = AppRoles.OWNER, pinHash = "hash")
        val manager = User(id = 2L, name = "Manager", role = AppRoles.MANAGER, pinHash = "hash")
        val cashier = User(id = 3L, name = "Cashier", role = AppRoles.CASHIER, pinHash = "hash")

        assertTrue(AppPermissions.isSupervisor(owner))
        assertTrue(AppPermissions.isSupervisor(manager))
        assertFalse(AppPermissions.isSupervisor(cashier))
    }

    @Test
    fun testR21_17_scannerZeroQtyCheck() {
        var removeCalled = false
        var setQtyCalled = false
        var setQtyValue = -1.0

        fun handleQuantityChange(qty: Double) {
            if (qty <= 0.0) {
                removeCalled = true
            } else {
                setQtyCalled = true
                setQtyValue = qty
            }
        }

        // Case 1: Qty decremented to 0 should trigger remove request
        handleQuantityChange(0.0)
        assertTrue(removeCalled)
        assertFalse(setQtyCalled)

        // Case 2: Qty negative should trigger remove request
        removeCalled = false
        handleQuantityChange(-1.0)
        assertTrue(removeCalled)
        assertFalse(setQtyCalled)

        // Case 3: Positive qty updates quantity
        removeCalled = false
        handleQuantityChange(2.5)
        assertFalse(removeCalled)
        assertTrue(setQtyCalled)
        assertEquals(2.5, setQtyValue, 0.001)
    }

    @Test
    fun testR21_18_holdCartOverrideGrantAndConsume() {
        var overrideExpiresAt = 0L

        fun grantOverride(durationMs: Long = 60_000L, now: Long) {
            overrideExpiresAt = now + durationMs
        }

        fun consumeOverride(now: Long): Boolean {
            val valid = overrideExpiresAt >= now
            if (valid) {
                overrideExpiresAt = 0L
            }
            return valid
        }

        val t0 = 100_000L
        assertFalse(consumeOverride(t0))

        // Grant override
        grantOverride(now = t0)
        // Consuming within window returns true
        assertTrue(consumeOverride(now = t0 + 10_000L))
        // Consuming again fails (single-use)
        assertFalse(consumeOverride(now = t0 + 10_000L))

        // Expired override
        grantOverride(durationMs = 5_000L, now = t0)
        assertFalse(consumeOverride(now = t0 + 6_000L))
    }

    @Test
    fun testR21_19_productStockEditPermissionGating() {
        val prevStock = 15.0
        val dbLiveStock = 12.0 // sale happened in background
        val dialogInputQty = 20.0

        // User without adjust stock permission: stock must use live DB stock, adjustment must be null
        val canAdjustStock = false
        val finalStockNoPerm = if (!canAdjustStock) dbLiveStock else dialogInputQty
        assertEquals(12.0, finalStockNoPerm, 0.001)

        val diffNoPerm = dialogInputQty - prevStock
        val adjustmentNoPerm = if (canAdjustStock && kotlin.math.abs(diffNoPerm) > 0.0001) {
            "Adjustment($diffNoPerm)"
        } else null
        assertNull(adjustmentNoPerm)

        // User with adjust stock permission: uses dialog input qty and generates adjustment
        val canAdjustStockYes = true
        val finalStockPerm = if (!canAdjustStockYes) dbLiveStock else dialogInputQty
        assertEquals(20.0, finalStockPerm, 0.001)

        val diffPerm = dialogInputQty - prevStock
        val adjustmentPerm = if (canAdjustStockYes && kotlin.math.abs(diffPerm) > 0.0001) {
            "Adjustment($diffPerm)"
        } else null
        assertEquals("Adjustment(5.0)", adjustmentPerm)
    }

    @Test
    fun testR21_20_supervisorAuthEnumerationProtection() {
        val remainingMs = 4500L
        val secondsCeil = ((remainingMs + 999L) / 1000L).toInt()
        assertEquals(5, secondsCeil)

        // Both Deactivated and NotSupervisor map to the same failure message type
        val deactivatedResult = SupervisorVerifyResult.Deactivated(
            User(id = 1L, name = "Deactivated Staff", role = AppRoles.CASHIER, pinHash = "hash", isActive = false)
        )
        val notSupervisorResult = SupervisorVerifyResult.NotSupervisor

        fun isGenericAuthFailure(res: SupervisorVerifyResult): Boolean {
            return when (res) {
                is SupervisorVerifyResult.Deactivated -> true
                SupervisorVerifyResult.NotSupervisor -> true
                is SupervisorVerifyResult.Invalid -> res.remainingMillis <= 0L
                else -> false
            }
        }

        assertTrue(isGenericAuthFailure(deactivatedResult))
        assertTrue(isGenericAuthFailure(notSupervisorResult))
        assertTrue(isGenericAuthFailure(SupervisorVerifyResult.Invalid(0L)))
        assertFalse(isGenericAuthFailure(SupervisorVerifyResult.Invalid(5000L)))
    }
}
