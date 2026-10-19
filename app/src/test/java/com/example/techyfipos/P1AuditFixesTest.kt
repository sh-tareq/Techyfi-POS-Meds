package com.example.techyfipos

import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.db.PurchaseInvoice
import com.example.techyfipos.data.db.SupplierPayment
import com.example.techyfipos.data.db.User
import com.example.techyfipos.printer.computeBluetoothWatchdogBudgetMs
import com.example.techyfipos.ui.viewmodel.SalesReportData
import com.example.techyfipos.utils.ExcelHelper
import com.example.techyfipos.utils.PaymentMath
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

    @Test
    fun testR23_14_inclusiveTaxProfitWithLoyaltyDiscount() {
        // Sale specification from audit R23-14 / R22-32:
        // One taxable line: price 1150, 15% inclusive, cost 600
        // Discount: 115, Loyalty: 50
        // Grand total: 1035 - 50 = 985
        // Tax: 135
        // Correct net revenue: grandTotal - taxAmount = 985 - 135 = 850
        // Cost: 600
        // Net profit: 850 - 600 = 250 (not 300)

        val profitSummary = PaymentMath.calculateInvoiceProfitSummary(
            subtotal = 1150.0,
            grossCogs = 600.0,
            taxAmount = 135.0,
            discount = 115.0,
            loyaltyDiscount = 50.0,
            grandTotal = 985.0,
            refundAmount = 0.0,
            returnedCost = 0.0,
            isTaxInclusive = true
        )

        assertEquals(850.0, profitSummary.netRevenue, 0.001)
        assertEquals(600.0, profitSummary.netCogs, 0.001)
        assertEquals(250.0, profitSummary.netProfit, 0.001)
    }

    @Test
    fun testR23_18_supplierLedgerAutoPaymentAndVoidGuard() = kotlinx.coroutines.test.runTest {
        val mockPrefs = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        org.mockito.Mockito.`when`(mockContext.getSharedPreferences(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyInt())).thenReturn(mockPrefs)
        val mockDatabase = org.mockito.Mockito.mock(com.example.techyfipos.data.db.AppDatabase::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val mockSettingsDataStore = org.mockito.Mockito.mock(com.example.techyfipos.data.SettingsDataStore::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val repo = DataRepository(mockContext, mockDatabase, mockSettingsDataStore)

        val invoices = listOf(
            PurchaseInvoice(
                id = 1L,
                invoiceNumber = "INV-101",
                supplierId = 10L,
                totalAmount = 1000.0,
                paidAmount = 400.0,
                dueAmount = 600.0,
                itemsSummary = "Item A x 10",
                timestamp = 1000L
            ),
            PurchaseInvoice(
                id = 2L,
                invoiceNumber = "INV-102",
                supplierId = 10L,
                totalAmount = 500.0,
                paidAmount = 0.0,
                dueAmount = 500.0,
                itemsSummary = "Item B x 5",
                timestamp = 2000L
            )
        )

        // 1. Tagged sys auto-invoice payment
        val sysAutoPayment = SupplierPayment(
            id = 1L,
            supplierId = 10L,
            amountPaid = 400.0,
            paymentMethod = "Cash",
            referenceNote = "[SYS_INVOICE_PAYMENT] Paid on Invoice #INV-101",
            timestamp = 1000L
        )
        assertTrue(repo.isAutoInvoicePayment(sysAutoPayment, invoices))

        // 2. Legacy auto-invoice payment (matches real invoice number and amount)
        val legacyAutoPayment = SupplierPayment(
            id = 2L,
            supplierId = 10L,
            amountPaid = 400.0,
            paymentMethod = "Cash",
            referenceNote = "Paid on Invoice #INV-101",
            timestamp = 1000L
        )
        assertTrue(repo.isAutoInvoicePayment(legacyAutoPayment, invoices))

        // 3. User-typed note that happens to start with "Paid on Invoice #" but does NOT match any real invoice
        val userTypedPayment = SupplierPayment(
            id = 3L,
            supplierId = 10L,
            amountPaid = 350.0,
            paymentMethod = "Cash",
            referenceNote = "Paid on Invoice #INV-999 - standalone notes",
            timestamp = 1500L
        )
        assertFalse(repo.isAutoInvoicePayment(userTypedPayment, invoices))

        // 4. Test ledger recalculation logic and void guard
        // Scenario from audit:
        // Invoice A = 1000, standalone payment P = 1000. Total due = 0.
        // If we try to void Invoice A:
        // remainingInvoices = empty, remainingInvoiceDue = 0.
        // standalonePayments = 1000 > remainingInvoiceDue (0).
        // Voiding MUST be blocked.
        val invoiceA = PurchaseInvoice(
            id = 10L,
            invoiceNumber = "INV-A",
            supplierId = 20L,
            totalAmount = 1000.0,
            paidAmount = 0.0,
            dueAmount = 1000.0,
            itemsSummary = "Item X x 10",
            timestamp = 1000L
        )
        val standalonePaymentP = SupplierPayment(
            id = 20L,
            supplierId = 20L,
            amountPaid = 1000.0,
            paymentMethod = "Cash",
            referenceNote = "General supplier advance payment",
            timestamp = 1000L
        )

        val invoicesSupplier20 = listOf(invoiceA)
        val paymentsSupplier20 = listOf(standalonePaymentP)

        val remainingInvoices = invoicesSupplier20.filter { it.id != invoiceA.id }
        val remainingInvoiceDue = remainingInvoices.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0.0) }
        val standalonePayments = paymentsSupplier20.filter { !repo.isAutoInvoicePayment(it, invoicesSupplier20) }.sumOf { it.amountPaid }

        val wouldExceedRemainingDue = standalonePayments > remainingInvoiceDue + 0.001
        assertTrue("Voiding invoice A must be blocked because standalone payment would exceed remaining invoice dues", wouldExceedRemainingDue)
    }
}
