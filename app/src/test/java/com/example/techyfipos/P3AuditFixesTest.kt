package com.example.techyfipos

import com.example.techyfipos.utils.ExcelHelper
import com.example.techyfipos.utils.PaymentMath
import com.example.techyfipos.utils.mapErrorToLocalizedMessage
import com.example.techyfipos.utils.toBanglaDigits
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Test

class P3AuditFixesTest {

    @Test
    fun testMapErrorToLocalizedMessage_P3Mappings() {
        assertEquals(
            R.string.supplier_cannot_void_invoice_payment,
            mapErrorToLocalizedMessage("Cannot void invoice payments directly. Void the purchase invoice instead.")
        )
        assertEquals(
            R.string.error_duplicate_record,
            mapErrorToLocalizedMessage("UNIQUE constraint failed: products.barcode")
        )
        assertEquals(
            R.string.error_duplicate_record,
            mapErrorToLocalizedMessage("constraint conflict: product already exists")
        )
        assertEquals(
            R.string.error_duplicate_record,
            mapErrorToLocalizedMessage("duplicate record detected")
        )
        assertEquals(
            R.string.error_invoice_not_found,
            mapErrorToLocalizedMessage("Invoice not found in system")
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
    fun testComputeBulkUpdatedPrices_mrpClamp() {
        // Price increased by 50% from 100 -> 150, but MRP is 120 -> clamped to 120
        val (newSell, newDisc) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            percentDelta = 50.0,
            mrp = 120.0
        )
        assertEquals(120.0, newSell, 0.001)
        assertEquals(0.0, newDisc, 0.001)

        // Without MRP or with zero MRP, price increases to 150
        val (uncappedSell, _) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            percentDelta = 50.0,
            mrp = 0.0
        )
        assertEquals(150.0, uncappedSell, 0.001)

        // Price increase is below MRP -> not clamped
        val (belowMrpSell, _) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            fixedDelta = 10.0,
            mrp = 200.0
        )
        assertEquals(110.0, belowMrpSell, 0.001)
    }

    @Test
    fun testExcelHelper_preserveLeadingZerosOnBarcode() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("TestSheet")
        val row = sheet.createRow(0)

        // String cell with leading zeros
        val stringCell = row.createCell(0)
        stringCell.setCellValue("0001234567")
        val strResult = ExcelHelper.getCellStringValue(stringCell, isBarcode = true)
        assertEquals("0001234567", strResult)

        // Numeric cell formatted with leading zero
        val numericCell = row.createCell(1)
        numericCell.setCellValue(123456.0)
        val numResult = ExcelHelper.getCellStringValue(numericCell, isBarcode = true)
        assertEquals("123456", numResult)

        workbook.close()
    }

    @Test
    fun testToBanglaDigits() {
        assertEquals("০১২৩৪৫৬৭৮৯", toBanglaDigits("0123456789"))
        assertEquals("৳১২.৫০", toBanglaDigits("৳12.50"))
    }
}
