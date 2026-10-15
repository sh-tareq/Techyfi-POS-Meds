package com.example.techyfipos

import com.example.techyfipos.utils.ExcelHelper
import com.example.techyfipos.utils.PaymentMath
import com.example.techyfipos.utils.mapErrorToLocalizedMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class P3AuditFixesTest {

    @Test
    fun testR22_24_mapErrorToLocalizedMessage() {
        assertEquals(
            R.string.supplier_cannot_void_invoice_payment,
            mapErrorToLocalizedMessage("Cannot void invoice payments directly. Void the purchase invoice instead.")
        )
        assertEquals(
            R.string.error_duplicate_record,
            mapErrorToLocalizedMessage("SQLiteConstraintException: UNIQUE constraint failed: products.barcode")
        )
        assertEquals(
            R.string.error_duplicate_record,
            mapErrorToLocalizedMessage("A duplicate record was found")
        )
        assertEquals(
            R.string.error_duplicate_record,
            mapErrorToLocalizedMessage("constraint conflict detected")
        )
        assertEquals(
            R.string.error_invoice_not_found,
            mapErrorToLocalizedMessage("Invoice not found")
        )
        assertEquals(
            R.string.error_supplier_payment_not_found,
            mapErrorToLocalizedMessage("Supplier payment not found")
        )
        assertEquals(
            R.string.users_cannot_promote_to_owner,
            mapErrorToLocalizedMessage("Cannot promote staff to owner")
        )
        assertEquals(
            R.string.error_cannot_change_role_last_owner,
            mapErrorToLocalizedMessage("The only owner account cannot be changed")
        )
        assertEquals(
            R.string.error_supervisor_auth_required_discount,
            mapErrorToLocalizedMessage("Supervisor authorization is required to apply discount")
        )
    }

    @Test
    fun testR22_26_computeBulkUpdatedPricesWithMrp() {
        // Current price 100, increase 50% -> 150. MRP is 120 -> clamped to 120.
        val (priceClamped, _) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            percentDelta = 50.0,
            mrp = 120.0
        )
        assertEquals(120.0, priceClamped, 0.001)

        // Current price 100, increase 10% -> 110. MRP is 120 -> not clamped (110).
        val (priceNotClamped, _) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            percentDelta = 10.0,
            mrp = 120.0
        )
        assertEquals(110.0, priceNotClamped, 0.001)

        // Current price 100, fixed increase +30 -> 130. MRP is 115 -> clamped to 115.
        val (fixedClamped, _) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            fixedDelta = 30.0,
            mrp = 115.0
        )
        assertEquals(115.0, fixedClamped, 0.001)

        // Current price 100, increase 50% -> 150. MRP is null -> 150.
        val (noMrp, _) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            percentDelta = 50.0,
            mrp = null
        )
        assertEquals(150.0, noMrp, 0.001)

        // Current price 100, increase 50% -> 150. MRP is 0.0 -> 150.
        val (zeroMrp, _) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            percentDelta = 50.0,
            mrp = 0.0
        )
        assertEquals(150.0, zeroMrp, 0.001)
    }

    @Test
    fun testR22_26_excelBarcodeLeadingZerosPreservation() {
        val plain = ExcelHelper.preserveLeadingZeros("123456")
        assertEquals("123456", plain)
    }
}
