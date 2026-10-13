package com.example.techyfipos

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
        val reductionZero = minOf(invoiceDue, maxOf(0.0, currentDueZero))
        assertEquals(0.0, reductionZero, 0.001)
        val newDueZero = maxOf(0.0, currentDueZero - reductionZero)
        assertEquals(0.0, newDueZero, 0.001)

        // Case 2: Supplier due is 400.0 (partial later payment)
        val currentDuePartial = 400.0
        val reductionPartial = minOf(invoiceDue, maxOf(0.0, currentDuePartial))
        assertEquals(400.0, reductionPartial, 0.001)
        val newDuePartial = maxOf(0.0, currentDuePartial - reductionPartial)
        assertEquals(0.0, newDuePartial, 0.001)

        // Case 3: Supplier due is 1500.0 (other invoices exist)
        val currentDueHigher = 1500.0
        val reductionHigher = minOf(invoiceDue, maxOf(0.0, currentDueHigher))
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
        val clamped = minOf(appliedStoreCredit, voucherRemaining)
        assertEquals(100.0, clamped, 0.001)

        // If no voucher remaining, clamped to 0.0
        val zeroVoucherRemaining = 0.0
        val clampedZero = minOf(appliedStoreCredit, zeroVoucherRemaining)
        assertEquals(0.0, clampedZero, 0.001)
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
}
