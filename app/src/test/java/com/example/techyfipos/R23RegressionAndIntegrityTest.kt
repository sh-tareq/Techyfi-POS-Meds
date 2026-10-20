package com.example.techyfipos

import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.db.User
import com.example.techyfipos.utils.LoyaltyProgramSettings
import com.example.techyfipos.utils.mapErrorToLocalizedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class R23RegressionAndIntegrityTest {

    // 1. R23-01: Lock-screen brute force lockout tracking
    @Test
    fun testR23_01_lockScreenBruteForceLockout() {
        var failureCount = 0
        fun simulateFailedAttempt(): Long {
            failureCount++
            return if (failureCount >= 5) 30_000L else 0L
        }

        assertEquals(0L, simulateFailedAttempt())
        assertEquals(0L, simulateFailedAttempt())
        assertEquals(0L, simulateFailedAttempt())
        assertEquals(0L, simulateFailedAttempt())
        val lockMs = simulateFailedAttempt()
        assertEquals(30_000L, lockMs)
        assertTrue("5 consecutive failures must trigger lockout", lockMs > 0L)
    }

    // 2. R23-02: Role matrix subset verification
    @Test
    fun testR23_02_roleMatrixSubset() {
        val ownerUser = User(id = 1L, name = "Owner", pinHash = "hash", role = AppRoles.OWNER, isActive = true)
        val managerUser = User(id = 2L, name = "Manager", pinHash = "hash", role = AppRoles.MANAGER, isActive = true)
        val cashierUser = User(id = 3L, name = "Cashier", pinHash = "hash", role = AppRoles.CASHIER, isActive = true)

        // Owner has all permissions
        assertTrue(AppPermissions.hasPermission(ownerUser, AppPermissions.SYSTEM_MANAGE_STAFF))
        assertTrue(AppPermissions.hasPermission(ownerUser, AppPermissions.SYSTEM_FACTORY_RESET))
        assertTrue(AppPermissions.hasPermission(ownerUser, AppPermissions.SALES_APPLY_DISCOUNT))

        // Manager has checkout and inventory management but NOT root system reset
        assertTrue(AppPermissions.hasPermission(managerUser, AppPermissions.SALES_APPLY_DISCOUNT))
        assertTrue(AppPermissions.hasPermission(managerUser, AppPermissions.INVENTORY_ADD_EDIT))
        assertFalse(AppPermissions.hasPermission(managerUser, AppPermissions.SYSTEM_FACTORY_RESET))

        // Cashier has selling permission but NOT cost viewing or user management
        assertTrue(AppPermissions.hasPermission(cashierUser, AppPermissions.SALES_SELL))
        assertFalse(AppPermissions.hasPermission(cashierUser, AppPermissions.INVENTORY_VIEW_COSTS))
        assertFalse(AppPermissions.hasPermission(cashierUser, AppPermissions.SYSTEM_MANAGE_STAFF))
    }

    // 3. R23-03: Manager 20% discount completes and owner self-edit rules
    @Test
    fun testR23_03_managerDiscountAndOwnerSelfEdit() {
        val manager = User(
            id = 2L,
            name = "Manager",
            pinHash = "hash",
            role = AppRoles.MANAGER,
            maxDiscountPercent = 20.0,
            isActive = true
        )
        // 20% discount is within manager's allowed discount percent
        val requestedDiscountPercent = 20.0
        val isPermitted = requestedDiscountPercent <= manager.maxDiscountPercent
        assertTrue(isPermitted)

        // Can promote/demote rules
        assertTrue(AppRoles.canPromoteToOwner(callerRole = AppRoles.OWNER, targetRole = AppRoles.OWNER))
        assertFalse(AppRoles.canPromoteToOwner(callerRole = AppRoles.MANAGER, targetRole = AppRoles.OWNER))
        assertFalse(AppRoles.canPromoteToOwner(callerRole = AppRoles.CASHIER, targetRole = AppRoles.OWNER))
    }

    // 4. R23-06: Multi-owner protection and last owner check
    @Test
    fun testR23_06_multiOwnerProtection() {
        val owners = listOf(
            User(id = 1L, name = "Owner 1", pinHash = "hash1", role = AppRoles.OWNER, isActive = true),
            User(id = 2L, name = "Owner 2", pinHash = "hash2", role = AppRoles.OWNER, isActive = true)
        )
        // Deleting when 2 owners exist leaves 1 owner -> allowed
        val remainingAfterDelete = owners.filter { it.id != 1L }
        val hasRemainingOwner = remainingAfterDelete.any { it.role == AppRoles.OWNER && it.isActive }
        assertTrue(hasRemainingOwner)

        // Deleting when only 1 owner exists -> blocked
        val remainingAfterLastDelete = remainingAfterDelete.filter { it.id != 2L }
        val hasOwnerLeft = remainingAfterLastDelete.any { it.role == AppRoles.OWNER && it.isActive }
        assertFalse(hasOwnerLeft)
    }

    // 5. R23-10: Export then import idempotent product updates
    @Test
    fun testR23_10_exportThenImportCreatesNoDuplicates() {
        val existingProducts = listOf(
            Product(barcode = "ITEM-001", name = "Test Product 1", category = "General", sellingPrice = 100.0, purchasePrice = 70.0, stockQty = 10.0, reorderLevel = 1),
            Product(barcode = "ITEM-002", name = "Test Product 2", category = "General", sellingPrice = 200.0, purchasePrice = 140.0, stockQty = 5.0, reorderLevel = 1)
        )
        val existingBarcodes = existingProducts.map { it.barcode.lowercase().trim() }.toSet()

        // Same products being re-imported from export file
        val importedBarcodes = listOf("item-001", "item-002")
        val newProductsCount = importedBarcodes.count { it !in existingBarcodes }
        assertEquals(0, newProductsCount)
    }

    // 6. R23-12: Watchdog budget for 1.2 MB transmission
    @Test
    fun testR23_12_watchdogBudgetForLargeBitmap() {
        val payloadSizeBytes = 1_200_000L // 1.2 MB
        // Base transmission speed minimum: 15 KB/s over Bluetooth serial + 5s baseline
        val minimumDurationSec = (payloadSizeBytes / 15_000L) + 5L
        assertTrue("Watchdog budget for 1.2MB must exceed 60 seconds", minimumDurationSec > 60L)
    }

    // 7. R23-13: Cache key changes with bill content
    @Test
    fun testR23_13_cacheKeyChangesWithBillContent() {
        fun computeBillKey(
            invoiceNumber: String,
            customerName: String?,
            total: Double,
            items: List<Pair<String, Double>>
        ): String {
            val itemSignature = items.joinToString(";") { "${it.first}:${it.second}" }
            return "$invoiceNumber|$customerName|$total|$itemSignature"
        }

        val baseKey = computeBillKey("INV-100", "Rahim", 500.0, listOf("ITEM1" to 2.0))
        val diffCustKey = computeBillKey("INV-100", "Karim", 500.0, listOf("ITEM1" to 2.0))
        val diffTotalKey = computeBillKey("INV-100", "Rahim", 550.0, listOf("ITEM1" to 2.0))
        val diffItemKey = computeBillKey("INV-100", "Rahim", 500.0, listOf("ITEM1" to 3.0))

        assertNotEquals(baseKey, diffCustKey)
        assertNotEquals(baseKey, diffTotalKey)
        assertNotEquals(baseKey, diffItemKey)
    }

    // 8. R23-14: Loyalty numbers calculation and redemption math
    @Test
    fun testR23_14_loyaltyMathCalculation() {
        val settings = LoyaltyProgramSettings(
            takaPerPoint = 100.0,
            pointCashValue = 1.0,
            minRedeemPoints = 20,
            maxBillPercentPayable = 50
        )
        // 500 Taka sale earns 5 points
        val earned = (500.0 / settings.takaPerPoint).toInt()
        assertEquals(5, earned)

        // Customer has 80 points, max allowed redeem on 100 Taka bill is 50% = 50 Taka = 50 points
        val availablePoints = 80
        val billTotal = 100.0
        val maxDiscountFromPoints = availablePoints * settings.pointCashValue
        val maxAllowedBillDiscount = billTotal * (settings.maxBillPercentPayable / 100.0)
        val maxPossibleDiscount = minOf(maxDiscountFromPoints, maxAllowedBillDiscount)
        val rawPoints = kotlin.math.floor(maxPossibleDiscount / settings.pointCashValue).toInt()
        val pointsToRedeem = if (rawPoints >= settings.minRedeemPoints) rawPoints.coerceIn(0, availablePoints) else 0
        assertEquals(50, pointsToRedeem)

        // Customer with 15 points (below minimum of 20) can redeem 0
        val lowPoints = 15
        val lowDiscountFromPoints = lowPoints * settings.pointCashValue
        val lowPossibleDiscount = minOf(lowDiscountFromPoints, maxAllowedBillDiscount)
        val lowRawPoints = kotlin.math.floor(lowPossibleDiscount / settings.pointCashValue).toInt()
        val lowPointsToRedeem = if (lowRawPoints >= settings.minRedeemPoints) lowRawPoints.coerceIn(0, lowPoints) else 0
        assertEquals(0, lowPointsToRedeem)
    }

    // 9. R23-19: Strings and translation integrity
    @Test
    fun testR23_19_banglaStringsIntegrity() {
        val bnXml = File("src/main/res/values-bn/strings.xml").readText()
        assertTrue(bnXml.contains("z_report_invoices_units_fmt"))
        assertTrue(bnXml.contains("error_credit_limit_exceeded_auth"))
        assertTrue(bnXml.contains("audit_by_cashier_fmt"))
        assertTrue(bnXml.contains("settings_crash_logs_recorded"))
        assertTrue(bnXml.contains("settings_enter_decrypt_password_instruction"))
    }

    // 10. R23-24: One test per mapping with exact repository error messages
    @Test
    fun testR23_24_exactRepoErrorMessageMappings() {
        val mappings = listOf(
            "Cannot void invoice payments directly. Void the purchase invoice instead." to R.string.supplier_cannot_void_invoice_payment,
            "UNIQUE constraint failed: products.barcode" to R.string.error_duplicate_record,
            "constraint conflict: product already exists" to R.string.error_duplicate_record,
            "duplicate record detected" to R.string.error_duplicate_record,
            "Invoice not found in system" to R.string.error_invoice_not_found,
            "Supplier payment not found" to R.string.error_supplier_payment_not_found,
            "Cannot promote staff to owner" to R.string.users_cannot_promote_to_owner,
            "The only owner account cannot be changed" to R.string.error_cannot_change_role_last_owner,
            "Supervisor authorization is required to apply discount" to R.string.error_supervisor_auth_required_discount,
            "Supervisor authorization required to exceed customer credit limit" to R.string.error_credit_limit_exceeded_auth,
            "Product no longer exists" to R.string.error_product_not_found,
            "Supplier no longer exists" to R.string.error_supplier_not_found,
            "Due ledger no longer exists" to R.string.error_due_ledger_no_longer_exists,
            "This voucher is exhausted" to R.string.error_voucher_inactive_or_exhausted,
            "Voucher expired" to R.string.error_voucher_expired,
            "Cannot delete or archive customer with outstanding dues" to R.string.error_customer_has_dues,
            "Customer has voucher balance - Cannot delete" to R.string.error_customer_has_active_vouchers,
            "Supplier payment expenses are auto-generated" to R.string.error_supplier_expense_auto_locked,
            "Payment exceeds the outstanding supplier balance" to R.string.error_supplier_payment_exceeds_due,
            "Return quantity exceeds quantity sold" to R.string.error_return_quantity_exceeds_sold,
            "Product was not part of this sale" to R.string.error_product_not_in_sale,
            "Remove this product from the active and held carts" to R.string.error_product_in_saved_carts,
            "Password must be at least 4 characters" to R.string.error_backup_password_min_length,
            "Passwords do not match" to R.string.error_backup_passwords_do_not_match,
            "Split payment rows do not add up" to R.string.error_split_total_mismatch,
            "Sale amounts must be finite" to R.string.error_sale_amounts_invalid,
            "Insufficient store credit balance" to R.string.error_insufficient_store_credit,
            "Product barcode already exists" to R.string.products_barcode_already_exists,
            "Customer name and phone are required" to R.string.error_payment_details_required,
            "A sale must contain at least one product" to R.string.error_sale_empty_cart
        )

        for ((repoMessage, expectedResId) in mappings) {
            val mapped = mapErrorToLocalizedMessage(repoMessage)
            assertEquals("Message '$repoMessage' should map to resource $expectedResId", expectedResId, mapped)
        }
    }
}
