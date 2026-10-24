package com.techyfiposmeds.data

import android.content.Context
import com.techyfiposmeds.data.db.AppDatabase
import com.techyfiposmeds.data.db.Product
import com.techyfiposmeds.data.db.User
import com.techyfiposmeds.ui.viewmodel.CartItem
import com.techyfiposmeds.ui.viewmodel.CartViewModel
import com.techyfiposmeds.utils.CartAuthHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AccessControlP2Test {

    private fun createTestUser(
        id: Long = 1L,
        role: String = AppRoles.CASHIER,
        customPermissions: String? = null
    ): User {
        return User(
            id = id,
            name = "Test User",
            pinHash = "dummyHash",
            role = role,
            customPermissions = customPermissions,
            maxDiscountPercent = 10.0,
            isActive = true
        )
    }

    private fun createTestProduct(barcode: String, price: Double): Product {
        return Product(
            barcode = barcode,
            name = "Product $barcode",
            category = "General",
            purchasePrice = price * 0.7,
            sellingPrice = price,
            stockQty = 10.0,
            reorderLevel = 2,
            imageUri = null,
            unit = "pcs"
        )
    }

    @Test
    fun `R20-20 CartAuthHelper enforces permission matrix and does not allow Manager role to bypass without permission`() {
        val activePermissionsWithoutVoid = setOf(AppPermissions.SALES_APPLY_DISCOUNT)
        val activePermissionsWithVoid = setOf(AppPermissions.SALES_VOID_CART)

        // Owner can always void cart
        assertTrue(CartAuthHelper.canVoidCart(isOwner = true, isSupervisor = false, activePermissions = emptySet()))

        // Manager (isSupervisor = true) MUST NOT bypass if permission is unticked
        assertFalse(
            "Manager without SALES_VOID_CART must not be allowed to void cart",
            CartAuthHelper.canVoidCart(isOwner = false, isSupervisor = true, activePermissions = activePermissionsWithoutVoid)
        )

        // Manager with SALES_VOID_CART can void cart
        assertTrue(
            CartAuthHelper.canVoidCart(isOwner = false, isSupervisor = true, activePermissions = activePermissionsWithVoid)
        )

        // Same for item removal
        assertFalse(
            "Manager without SALES_VOID_CART must not be allowed to remove item",
            CartAuthHelper.canRemoveItem(isOwner = false, isSupervisor = true, activePermissions = activePermissionsWithoutVoid)
        )
        assertTrue(
            CartAuthHelper.canRemoveItem(isOwner = false, isSupervisor = true, activePermissions = activePermissionsWithVoid)
        )
    }

    @Test
    fun `R20-20 AppPermissions respects custom permissions for Manager role`() {
        val allPermissions = AppPermissions.CATEGORIES.flatMap { it.permissions.map { p -> p.key } }.toSet()

        // Untick inventory stock adjustment for manager
        val managerPermissions = allPermissions - AppPermissions.INVENTORY_ADJUST_STOCK
        val serialized = AppPermissions.serializePermissions(managerPermissions)
        val managerUser = createTestUser(role = AppRoles.MANAGER, customPermissions = serialized)

        assertFalse(
            "Manager with unticked stock adjustment must not have INVENTORY_ADJUST_STOCK",
            AppPermissions.hasPermission(managerUser, AppPermissions.INVENTORY_ADJUST_STOCK)
        )
        assertTrue(
            "Manager retains other permissions",
            AppPermissions.hasPermission(managerUser, AppPermissions.INVENTORY_VIEW)
        )
    }

    private fun createTestRepository(): DataRepository {
        val mockContext = mock(Context::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val mockDatabase = mock(AppDatabase::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val mockSettingsDataStore = mock(SettingsDataStore::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val mockPrefs = mock(android.content.SharedPreferences::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        org.mockito.Mockito.`when`(mockContext.getSharedPreferences(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyInt())).thenReturn(mockPrefs)
        org.mockito.Mockito.`when`(mockDatabase.saleAuditLogDao().getAllAuditLogs()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
        org.mockito.Mockito.`when`(mockDatabase.expenseAuditLogDao().getAllAuditLogs()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
        return DataRepository(mockContext, mockDatabase, mockSettingsDataStore)
    }

    @Test
    fun `R20-24 and R20-26 DataRepository requirePermission blocks unauthorized sessions`() {
        val repo = createTestRepository()

        val cashier = createTestUser(role = AppRoles.CASHIER, customPermissions = null)
        repo.setSessionUser(cashier)

        // Cashier lacks INVENTORY_ADJUST_STOCK
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            repo.requirePermission(AppPermissions.INVENTORY_ADJUST_STOCK)
        }

        // Cashier lacks SYSTEM_MANAGE_STAFF
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            repo.requirePermission(AppPermissions.SYSTEM_MANAGE_STAFF)
        }

        // Cashier lacks SYSTEM_PERMISSIONS
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            repo.requirePermission(AppPermissions.SYSTEM_PERMISSIONS)
        }

        // Cashier lacks REPORTS_EXPORT_EXCEL
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            repo.requirePermission(AppPermissions.REPORTS_EXPORT_EXCEL)
        }

        // Cashier lacks REPORTS_VIEW_Z_REPORT
        assertThrows(DataRepository.UnauthorizedException::class.java) {
            repo.requirePermission(AppPermissions.REPORTS_VIEW_Z_REPORT)
        }

        // Switching session to Owner permits all actions
        val owner = createTestUser(role = AppRoles.OWNER)
        repo.setSessionUser(owner)
        repo.requirePermission(AppPermissions.INVENTORY_ADJUST_STOCK)
        repo.requirePermission(AppPermissions.SYSTEM_MANAGE_STAFF)
        repo.requirePermission(AppPermissions.SYSTEM_PERMISSIONS)
        repo.requirePermission(AppPermissions.REPORTS_EXPORT_EXCEL)
        repo.requirePermission(AppPermissions.REPORTS_VIEW_Z_REPORT)
    }

    @Test
    fun `R20-27 Discount override token is single use and matches cart hash and max discount`() {
        val repo = createTestRepository()

        val cartHash = "hash-12345"

        // Grant override of 50.0 taka for cartHash
        repo.grantDiscountOverride(maxDiscountAmount = 50.0, cartHash = cartHash)

        // Attempting to consume with mismatched cart hash fails
        assertFalse(
            "Mismatched cart hash must reject discount override",
            repo.consumeDiscountOverride(discountAmount = 30.0, cartHash = "different-hash")
        )

        // Grant again
        repo.grantDiscountOverride(maxDiscountAmount = 50.0, cartHash = cartHash)

        // Attempting to consume with discount amount exceeding authorized max fails
        assertFalse(
            "Discount exceeding authorized max must be rejected",
            repo.consumeDiscountOverride(discountAmount = 75.0, cartHash = cartHash)
        )

        // Grant again
        repo.grantDiscountOverride(maxDiscountAmount = 50.0, cartHash = cartHash)

        // Valid consumption succeeds
        assertTrue(
            "Valid discount override must succeed",
            repo.consumeDiscountOverride(discountAmount = 45.0, cartHash = cartHash)
        )

        // Second consumption must fail because token is consumed
        assertFalse(
            "Discount override must be single-use only",
            repo.consumeDiscountOverride(discountAmount = 45.0, cartHash = cartHash)
        )
    }

    @Test
    fun `R20-27 CartViewModel computeCartHash is deterministic and detects cart changes`() {
        val prod1 = createTestProduct("111", 100.0)
        val prod2 = createTestProduct("222", 50.0)

        val cartItems1 = listOf(
            CartItem(product = prod1, quantity = 2.0),
            CartItem(product = prod2, quantity = 1.0)
        )

        val cartItemsIdentical = listOf(
            CartItem(product = prod1, quantity = 2.0),
            CartItem(product = prod2, quantity = 1.0)
        )

        val cartItemsModifiedQty = listOf(
            CartItem(product = prod1, quantity = 3.0),
            CartItem(product = prod2, quantity = 1.0)
        )

        val hash1 = CartViewModel.computeCartHash(cartItems1)
        val hash2 = CartViewModel.computeCartHash(cartItemsIdentical)
        val hash3 = CartViewModel.computeCartHash(cartItemsModifiedQty)

        assertEquals("Same items must produce exact same cart hash", hash1, hash2)
        assertNotEquals("Modified quantity must change cart hash", hash1, hash3)
    }

    @Test
    fun `R20-28 Role change passing null custom permissions resets to role defaults`() {
        // Cashier defaults
        val defaultCashierPerms = AppPermissions.getDefaultPermissionsForRole(AppRoles.CASHIER)
        // Manager defaults
        val defaultManagerPerms = AppPermissions.getDefaultPermissionsForRole(AppRoles.MANAGER)

        assertNotEquals(defaultCashierPerms, defaultManagerPerms)

        // Role change from Cashier to Manager with null custom permissions yields default Manager permissions
        val effectivePermissionsAfterRoleChange = AppPermissions.getEffectivePermissions(
            createTestUser(role = AppRoles.MANAGER, customPermissions = null)
        )
        assertEquals(defaultManagerPerms, effectivePermissionsAfterRoleChange)
    }
}
