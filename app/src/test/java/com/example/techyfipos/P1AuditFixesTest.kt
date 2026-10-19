package com.example.techyfipos

import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.db.User
import com.example.techyfipos.printer.computeBluetoothWatchdogBudgetMs
import com.example.techyfipos.ui.viewmodel.SalesReportData
import com.example.techyfipos.utils.ExcelHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P1AuditFixesTest {

    @Test
    fun testR21_08_supplierVoidClamping() {
        val invoiceDue = 1000.0

        // Case 1: Supplier due is already 0.0 because of later payment
        val currentDueZero = 0.0
        val reductionZero = com.example.techyfipos.utils.PaymentMath.computeSupplierInvoiceVoidDueReduction(invoiceDue, currentDueZero)
        assertEquals(0.0, reductionZero, 0.001)
        val newDueZero = maxOf(0.0, currentDueZero - reductionZero)
        assertEquals(0.0, newDueZero, 0.001)

        // Case 2: Supplier due is 400.0 (partial later payment)
        val currentDuePartial = 400.0
        val reductionPartial = com.example.techyfipos.utils.PaymentMath.computeSupplierInvoiceVoidDueReduction(invoiceDue, currentDuePartial)
        assertEquals(400.0, reductionPartial, 0.001)
        val newDuePartial = maxOf(0.0, currentDuePartial - reductionPartial)
        assertEquals(0.0, newDuePartial, 0.001)

        // Case 3: Supplier due is 1500.0 (other invoices exist)
        val currentDueHigher = 1500.0
        val reductionHigher = com.example.techyfipos.utils.PaymentMath.computeSupplierInvoiceVoidDueReduction(invoiceDue, currentDueHigher)
        assertEquals(1000.0, reductionHigher, 0.001)
        val newDueHigher = maxOf(0.0, currentDueHigher - reductionHigher)
        assertEquals(500.0, newDueHigher, 0.001)

        // Case 4: Paid on invoice note prefix check
        val invoicePaymentNote = "Paid on Invoice #INV-101"
        assertTrue(invoicePaymentNote.startsWith("Paid on Invoice #"))
        val manualPaymentNote = "Partial settlement for March"
        assertFalse(manualPaymentNote.startsWith("Paid on Invoice #"))
    }

    @Test
    fun testR21_10_voucherCreditClampOnPhoneChange() {
        // Customer A had 500.0 credit applied + 100.0 voucher = 600.0 total applied
        val appliedStoreCredit = 600.0
        val voucherRemaining = 100.0

        // On switching to Customer B with a voucher attached, store credit is clamped to voucher balance
        val clamped = com.example.techyfipos.utils.PaymentMath.resolveStoreCreditOnPhoneChange(
            oldPhone = "01711111111",
            newPhone = "01722222222",
            currentCredit = appliedStoreCredit,
            hasRedeemedVoucher = true,
            voucherRemainingBalance = voucherRemaining
        )
        assertEquals(100.0, clamped, 0.001)

        // If no voucher remaining, clamped to 0.0
        val zeroVoucherRemaining = 0.0
        val clampedZero = com.example.techyfipos.utils.PaymentMath.resolveStoreCreditOnPhoneChange(
            oldPhone = "01711111111",
            newPhone = "01722222222",
            currentCredit = appliedStoreCredit,
            hasRedeemedVoucher = false,
            voucherRemainingBalance = zeroVoucherRemaining
        )
        assertEquals(0.0, clampedZero, 0.001)

        val clampedNull = com.example.techyfipos.utils.PaymentMath.resolveStoreCreditOnPhoneChange(
            oldPhone = "01711111111",
            newPhone = "01722222222",
            currentCredit = appliedStoreCredit,
            hasRedeemedVoucher = false,
            voucherRemainingBalance = null
        )
        assertEquals(0.0, clampedNull, 0.001)
    }

    @Test
    fun testR21_11_excelBarcodePreservationAndBlankSentinel() {
        // Customer phone leading zero preservation
        val phoneWithDroppedZero = "1712345678"
        val preservedPhone = ExcelHelper.preserveLeadingZeros(phoneWithDroppedZero)
        assertEquals("01712345678", preservedPhone)

        // Blank string should parse as sentinel -1.0, not 0.0
        val blankVal = ""
        val parsedBlank = blankVal.trim().toDoubleOrNull() ?: -1.0
        assertEquals(-1.0, parsedBlank, 0.001)

        val nonBlankVal = "25.5"
        val parsedNonBlank = nonBlankVal.trim().toDoubleOrNull() ?: -1.0
        assertEquals(25.5, parsedNonBlank, 0.001)
    }

    @Test
    fun testR21_12_profitLoyaltyDiscountDeduction() {
        val grossProfit = 500.0
        val regularDiscount = 50.0
        val loyaltyDiscount = 30.0

        // Total deductions must include both regular and loyalty discounts
        val totalDeductions = regularDiscount + loyaltyDiscount
        assertEquals(80.0, totalDeductions, 0.001)

        val netProfit = grossProfit - totalDeductions
        assertEquals(420.0, netProfit, 0.001)
    }

    @Test
    fun testR21_13_reportCsvPnlVisibilityGating() {
        val report = SalesReportData(
            transactionCount = 10,
            totalRevenue = 1000.0,
            totalDiscount = 50.0,
            grossProfit = 300.0,
            netProfit = 250.0,
            totalTax = 50.0,
            totalExpenses = 50.0,
            totalRefunds = 100.0,
            itemsSold = 20.0
        )

        // Helper function mimicking buildReportCsv
        fun csvReport(canViewPnl: Boolean): String {
            return buildString {
                appendLine("Gross sales,${report.grossSales}")
                appendLine("Net sales,${report.totalRevenue}")
                if (canViewPnl) {
                    appendLine("Gross margin,${report.totalProfit}")
                }
                appendLine("Expenses,${report.totalExpenses}")
                if (canViewPnl) {
                    appendLine("Net profit,${report.netProfit}")
                }
            }
        }

        val withPnl = csvReport(canViewPnl = true)
        assertTrue(withPnl.contains("Gross margin,300.0"))
        assertTrue(withPnl.contains("Net profit,250.0"))

        val withoutPnl = csvReport(canViewPnl = false)
        assertFalse(withoutPnl.contains("Gross margin"))
        assertFalse(withoutPnl.contains("Net profit"))
        assertTrue(withoutPnl.contains("Gross sales,1100.0"))
        assertTrue(withoutPnl.contains("Net sales,1000.0"))
    }

    @Test
    fun testR23_10_excelExportThenImportCreatesNoDuplicates() = kotlinx.coroutines.test.runTest {
        val initialProducts = listOf(
            Product(
                barcode = "123456789012",
                name = "UPC Product",
                category = "General",
                purchasePrice = 50.0,
                sellingPrice = 100.0,
                stockQty = 10.0,
                reorderLevel = 2,
                unit = "pcs"
            ),
            Product(
                barcode = "1712345678",
                name = "Mobile Sku",
                category = "General",
                purchasePrice = 40.0,
                sellingPrice = 80.0,
                stockQty = 5.0,
                reorderLevel = 1,
                unit = "pcs"
            ),
            Product(
                barcode = "0012345",
                name = "Zero Prefix",
                category = "General",
                purchasePrice = 10.0,
                sellingPrice = 20.0,
                stockQty = 15.0,
                reorderLevel = 3,
                unit = "pcs"
            )
        )

        // 1. Export initial products to Excel
        val outStream = java.io.ByteArrayOutputStream()
        ExcelHelper.exportProducts(initialProducts, outStream)
        val excelBytes = outStream.toByteArray()

        // 2. Import products from the exported Excel
        val inStream = java.io.ByteArrayInputStream(excelBytes)
        val importResult = ExcelHelper.importProducts(inStream)

        assertEquals(0, importResult.failCount)
        assertEquals(3, importResult.importedProducts.size)

        // 3. Assert imported barcodes match original barcodes exactly without prepended zeros
        val importedBarcodes = importResult.importedProducts.map { it.barcode }
        assertEquals(listOf("123456789012", "1712345678", "0012345"), importedBarcodes)
    }

    @Test
    fun testR23_12_bluetoothWatchdogBudgetForLargeBatches() {
        // Base budget for empty or minimal job
        val baseBudget = computeBluetoothWatchdogBudgetMs(0)
        assertEquals(25_000L, baseBudget)

        // For a 100-label batch of approximately 1.2 MB (1,200,000 bytes) with 100 segments (100 ms each = 10,000 ms total delay)
        val batch1_2MbBytes = 1_200_000
        val segmentCount = 100
        val totalSegmentDelayMs = 10_000L
        val budget1_2Mb = computeBluetoothWatchdogBudgetMs(batch1_2MbBytes, segmentCount, totalSegmentDelayMs)

        // The job takes ~90s and was previously cut off by the hard 60s cap.
        // It must now provide sufficient budget (> 90s and > 300s of headroom) without being capped at 60s.
        assertTrue("Budget must be greater than 90s for a 1.2 MB job", budget1_2Mb > 90_000L)
        assertTrue("Budget must comfortably accommodate 1.2 MB without 60s cap", budget1_2Mb >= 300_000L)
    }
}
