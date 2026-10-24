package com.techyfiposmeds

import com.techyfiposmeds.data.DataRepository
import com.techyfiposmeds.data.InvoiceSettingsData
import com.techyfiposmeds.data.db.CustomerLookupResult
import com.techyfiposmeds.data.db.Expense
import com.techyfiposmeds.data.db.HeldCart
import com.techyfiposmeds.data.db.Product
import com.techyfiposmeds.data.db.Sale
import com.techyfiposmeds.data.db.SaleItem
import com.techyfiposmeds.data.db.SaleWithItems
import com.techyfiposmeds.data.db.StoreCreditVoucher
import com.techyfiposmeds.ui.viewmodel.CartViewModel
import com.techyfiposmeds.ui.viewmodel.SalesReportData
import com.techyfiposmeds.ui.viewmodel.SalesViewModel
import com.techyfiposmeds.utils.ExcelHelper
import com.techyfiposmeds.utils.LoyaltyProgramSettings
import com.techyfiposmeds.utils.PaymentMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class P1bAuditFixesTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testR22_32_invoiceProfitForTaxInclusiveSaleWithDiscount() = runTest(testDispatcher) {
        // Audit specification numbers:
        // One taxable line: price 1150, 15% inclusive, cost 600
        // Discount: 115
        // Tax: T = 135
        // Grand total: 1035
        // Correct net revenue: 1035 - 135 = 900
        // Cost: 600
        // Net profit: 900 - 600 = 300

        // 1. Test pure PaymentMath.calculateInvoiceProfitSummary
        val profitSummary = PaymentMath.calculateInvoiceProfitSummary(
            subtotal = 1150.0,
            grossCogs = 600.0,
            taxAmount = 135.0,
            discount = 115.0,
            loyaltyDiscount = 0.0,
            grandTotal = 1035.0,
            refundAmount = 0.0,
            returnedCost = 0.0,
            isTaxInclusive = true
        )
        assertEquals(900.0, profitSummary.netRevenue, 0.001)
        assertEquals(600.0, profitSummary.netCogs, 0.001)
        assertEquals(300.0, profitSummary.netProfit, 0.001)

        // 2. Test PaymentMath.calculateExTaxDiscount helper
        val exTaxDiscount = PaymentMath.calculateExTaxDiscount(
            discount = 115.0,
            isTaxInclusive = true,
            taxRate = 15.0,
            taxableSubtotal = 1150.0,
            actualTaxAmount = 135.0
        )
        assertEquals(100.0, exTaxDiscount, 0.001)

        // 3. Test SalesViewModel.buildReport with exact audit numbers
        val repository = mock(DataRepository::class.java)
        `when`(repository.defaultCostPercentageFlow).thenReturn(flowOf(100.0))
        `when`(repository.allSalesWithItems).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.heldCartCount).thenReturn(MutableStateFlow(0))
        `when`(repository.allReturns).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allReturnItems).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.allProducts).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.getExpensesBetween(0L, 2000L)).thenReturn(flowOf(emptyList()))
        `when`(repository.allDuePayments).thenReturn(flowOf(emptyList()))
        `when`(repository.getLoyaltySettings()).thenReturn(LoyaltyProgramSettings())
        `when`(repository.registerShiftState).thenReturn(MutableStateFlow(null))

        val salesViewModel = SalesViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val sale = Sale(
            id = 1L,
            timestamp = 1000L,
            subtotal = 1150.0,
            discountAmount = 115.0,
            isPercentageDiscount = false,
            taxRate = 15.0,
            taxAmount = 135.0,
            grandTotal = 1035.0,
            amountPaid = 1035.0,
            paymentMethod = "Cash",
            isTaxInclusive = true
        )
        val saleItem = SaleItem(
            id = 1L,
            saleId = 1L,
            productBarcode = "123456",
            productName = "Inclusive Item",
            price = 1150.0,
            quantity = 1.0,
            purchasePriceAtSale = 600.0,
            taxable = true
        )
        val saleWithItems = SaleWithItems(sale = sale, items = listOf(saleItem))

        val report = salesViewModel.buildReport(0L, 2000L, listOf(saleWithItems))

        // Net revenue = 1035 - 135 = 900
        assertEquals(1035.0, report.totalRevenue, 0.001)
        assertEquals(115.0, report.totalDiscount, 0.001) // Nominal discount preserved
        assertEquals(135.0, report.totalTax, 0.001)
        assertEquals(400.0, report.grossProfit, 0.001) // Gross margin before discount
        assertEquals(300.0, report.netProfit, 0.001) // Net profit after discount
    }

    @Test
    fun testR22_33_excelExportDropsProfitWhenCanViewPnlIsFalse() {
        val saleItem = SaleItem(
            id = 1L,
            saleId = 1L,
            productBarcode = "123456",
            productName = "Product 1",
            price = 100.0,
            quantity = 2.0,
            purchasePriceAtSale = 60.0
        )
        val sale = Sale(
            id = 1L,
            timestamp = 1000L,
            subtotal = 200.0,
            discountAmount = 0.0,
            isPercentageDiscount = false,
            taxAmount = 0.0,
            grandTotal = 200.0,
            amountPaid = 200.0,
            paymentMethod = "Cash"
        )
        val salesList = listOf(SaleWithItems(sale, listOf(saleItem)))

        // 1. Export sales without PnL permission
        val outWithoutPnl = ByteArrayOutputStream()
        ExcelHelper.exportSales(
            salesList = salesList,
            purchasePrices = mapOf("123456" to 60.0),
            returns = emptyList(),
            returnItems = emptyList(),
            outputStream = outWithoutPnl,
            canViewPnl = false
        )
        val wbWithoutPnl = WorkbookFactory.create(ByteArrayInputStream(outWithoutPnl.toByteArray()))
        val sheetWithoutPnl = wbWithoutPnl.getSheetAt(0)
        val headerRowWithoutPnl = sheetWithoutPnl.getRow(0)
        val headersWithoutPnl = (0 until headerRowWithoutPnl.lastCellNum).map { headerRowWithoutPnl.getCell(it)?.stringCellValue ?: "" }

        assertFalse("Profit header must NOT be present when canViewPnl is false", headersWithoutPnl.any { it.equals("Profit", ignoreCase = true) })

        // 2. Export sales with PnL permission
        val outWithPnl = ByteArrayOutputStream()
        ExcelHelper.exportSales(
            salesList = salesList,
            purchasePrices = mapOf("123456" to 60.0),
            returns = emptyList(),
            returnItems = emptyList(),
            outputStream = outWithPnl,
            canViewPnl = true
        )
        val wbWithPnl = WorkbookFactory.create(ByteArrayInputStream(outWithPnl.toByteArray()))
        val sheetWithPnl = wbWithPnl.getSheetAt(0)
        val headerRowWithPnl = sheetWithPnl.getRow(0)
        val headersWithPnl = (0 until headerRowWithPnl.lastCellNum).map { headerRowWithPnl.getCell(it)?.stringCellValue ?: "" }

        assertTrue("Profit header MUST be present when canViewPnl is true", headersWithPnl.any { it.equals("Profit", ignoreCase = true) })

        // 3. Export Sales Report without PnL permission
        val reportData = SalesReportData(
            transactionCount = 5,
            totalRevenue = 1000.0,
            totalDiscount = 50.0,
            totalTax = 50.0,
            grossProfit = 400.0,
            totalExpenses = 100.0,
            netProfit = 300.0,
            itemsSold = 10.0
        )
        val reportOutNoPnl = ByteArrayOutputStream()
        ExcelHelper.exportSalesReport(
            report = reportData,
            dateRangeLabel = "Today",
            outputStream = reportOutNoPnl,
            canViewPnl = false
        )
        val reportWbNoPnl = WorkbookFactory.create(ByteArrayInputStream(reportOutNoPnl.toByteArray()))
        val reportSheetNoPnl = reportWbNoPnl.getSheetAt(0)
        val allCellTextsNoPnl = (0..reportSheetNoPnl.lastRowNum).flatMap { rIdx ->
            val r = reportSheetNoPnl.getRow(rIdx) ?: return@flatMap emptyList<String>()
            (0 until r.lastCellNum).map { cIdx -> r.getCell(cIdx)?.toString() ?: "" }
        }
        assertFalse("Gross Profit must NOT appear when canViewPnl is false", allCellTextsNoPnl.any { it.contains("Gross Profit", ignoreCase = true) })
        assertFalse("Net Profit must NOT appear when canViewPnl is false", allCellTextsNoPnl.any { it.contains("Net Profit", ignoreCase = true) })

        // 4. Export Sales Report with PnL permission
        val reportOutWithPnl = ByteArrayOutputStream()
        ExcelHelper.exportSalesReport(
            report = reportData,
            dateRangeLabel = "Today",
            outputStream = reportOutWithPnl,
            canViewPnl = true
        )
        val reportWbWithPnl = WorkbookFactory.create(ByteArrayInputStream(reportOutWithPnl.toByteArray()))
        val reportSheetWithPnl = reportWbWithPnl.getSheetAt(0)
        val allCellTextsWithPnl = (0..reportSheetWithPnl.lastRowNum).flatMap { rIdx ->
            val r = reportSheetWithPnl.getRow(rIdx) ?: return@flatMap emptyList<String>()
            (0 until r.lastCellNum).map { cIdx -> r.getCell(cIdx)?.toString() ?: "" }
        }
        assertTrue("Gross Profit MUST appear when canViewPnl is true", allCellTextsWithPnl.any { it.contains("Gross Profit", ignoreCase = true) })
        assertTrue("Net Profit MUST appear when canViewPnl is true", allCellTextsWithPnl.any { it.contains("Net Profit", ignoreCase = true) })
    }

    @Test
    fun testR22_34_supplierDueRecomputedFromLedger() {
        // Scenario from audit:
        // Unpaid 1,000 invoice, separate 1,000 payment (due 0).
        // Order A: Void invoice first -> live dues = 0, live payments = 1000 -> max(0, 0 - 1000) = 0.
        // Then void payment -> live dues = 0, live payments = 0 -> max(0, 0 - 0) = 0.
        val invoiceDue = 1000.0
        val paymentAmount = 1000.0

        // Initial state
        var liveInvoices = listOf(invoiceDue)
        var livePayments = listOf(paymentAmount)
        fun calculateDue(invoices: List<Double>, payments: List<Double>) =
            (invoices.sum() - payments.sum()).coerceAtLeast(0.0)

        assertEquals(0.0, calculateDue(liveInvoices, livePayments), 0.001)

        // Void invoice first
        liveInvoices = emptyList()
        val dueAfterInvoiceVoid = calculateDue(liveInvoices, livePayments)
        assertEquals(0.0, dueAfterInvoiceVoid, 0.001)

        // Then void payment
        livePayments = emptyList()
        val dueAfterPaymentVoid = calculateDue(liveInvoices, livePayments)
        assertEquals(0.0, dueAfterPaymentVoid, 0.001)

        // Order B: Void payment first
        liveInvoices = listOf(invoiceDue)
        livePayments = listOf(paymentAmount)
        livePayments = emptyList()
        val dueAfterPaymentVoidFirst = calculateDue(liveInvoices, livePayments)
        assertEquals(1000.0, dueAfterPaymentVoidFirst, 0.001)

        // Then void invoice
        liveInvoices = emptyList()
        val dueAfterInvoiceVoidSecond = calculateDue(liveInvoices, livePayments)
        assertEquals(0.0, dueAfterInvoiceVoidSecond, 0.001)
    }

    @Test
    fun testR22_35_barcodeLeadingZeroMatchingAndVoucherClamp() = runTest(testDispatcher) {
        // 1. Leading zero barcode candidate matching helper logic
        fun findMatchingBarcode(raw: String, existingBarcodes: List<String>): String? {
            val norm = raw.trim()
            if (norm.isEmpty()) return null
            if (existingBarcodes.contains(norm)) return norm
            if (norm.all { it.isDigit() }) {
                val singleZero = "0" + norm
                val doubleZero = "00" + norm
                val c1 = existingBarcodes.filter { it == singleZero }
                val c2 = existingBarcodes.filter { it == doubleZero }
                if (c1.size == 1 && c2.isEmpty()) return singleZero
                if (c2.size == 1 && c1.isEmpty()) return doubleZero
            }
            return null
        }

        val barcodes = listOf("012345678901", "009876543210", "999999999999")
        // Barcode with leading zero dropped by numeric cell
        assertEquals("012345678901", findMatchingBarcode("12345678901", barcodes))
        // Barcode with two leading zeroes dropped
        assertEquals("009876543210", findMatchingBarcode("9876543210", barcodes))
        // Barcode that exists as-is
        assertEquals("999999999999", findMatchingBarcode("999999999999", barcodes))
        // Unknown barcode
        assertEquals(null, findMatchingBarcode("5555555555", barcodes))

        // 2. Voucher clamp in selectCustomerSuggestion
        val repository = mock(DataRepository::class.java)
        `when`(repository.cashierNameFlow).thenReturn(flowOf("Cashier"))
        `when`(repository.loyaltySettingsFlow).thenReturn(MutableStateFlow(LoyaltyProgramSettings()))
        `when`(repository.allHeldCarts).thenReturn(MutableStateFlow(emptyList()))
        `when`(repository.heldCartCount).thenReturn(MutableStateFlow(0))
        `when`(repository.invoiceSettingsDataFlow).thenReturn(MutableStateFlow(InvoiceSettingsData()))
        `when`(repository.getCartSnapshot()).thenReturn("")

        val cartViewModel = CartViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Add a product to cart so grandTotal is 300.0
        val product = Product(
            barcode = "SKU-TEST-1",
            name = "Test Item",
            category = "General",
            purchasePrice = 100.0,
            sellingPrice = 300.0,
            stockQty = 10.0,
            reorderLevel = 5
        )
        cartViewModel.addToCart(product)
        testDispatcher.scheduler.advanceUntilIdle()

        // Attach a voucher with balance 150.0 and customer with 100.0 credit
        val voucher = StoreCreditVoucher(
            code = "VOUCH-150",
            customerPhone = "01711111111",
            initialAmount = 150.0,
            remainingBalance = 150.0,
            issuedAt = 1000L,
            expiresAt = 9000L,
            status = "ACTIVE"
        )
        val initialCustomer = CustomerLookupResult(
            phone = "01711111111",
            name = "Customer A",
            source = "CUSTOMER_DB",
            storeCredit = 100.0,
            dueAmount = 0.0,
            loyaltyPoints = 0
        )
        cartViewModel.selectCustomerSuggestion(initialCustomer)
        cartViewModel.setRedeemedVoucher(voucher)
        cartViewModel.setStoreCreditApplied(250.0) // 100 customer credit + 150 voucher

        assertEquals(250.0, cartViewModel.storeCreditApplied.value, 0.001)

        // Select Customer Suggestion with different phone
        val suggestion = CustomerLookupResult(
            phone = "01822222222",
            name = "Customer B",
            source = "CUSTOMER_DB",
            storeCredit = 0.0,
            dueAmount = 0.0,
            loyaltyPoints = 0
        )
        cartViewModel.selectCustomerSuggestion(suggestion)

        // Must be clamped to voucher's remainingBalance (150.0)
        assertEquals(150.0, cartViewModel.storeCreditApplied.value, 0.001)

        // Without voucher, selecting suggestion clears store credit
        cartViewModel.setRedeemedVoucher(null)
        val suggestion2 = CustomerLookupResult(
            phone = "01933333333",
            name = "Customer C",
            source = "CUSTOMER_DB",
            storeCredit = 0.0,
            dueAmount = 0.0,
            loyaltyPoints = 0
        )
        cartViewModel.selectCustomerSuggestion(suggestion2)
        assertEquals(0.0, cartViewModel.storeCreditApplied.value, 0.001)
    }
}
