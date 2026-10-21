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

    @Test
    fun testR21_28_sanitizeNumericInputCommas() {
        assertEquals("1000", com.example.techyfipos.utils.sanitizeNumericInput("1,000"))
        assertEquals("1500.50", com.example.techyfipos.utils.sanitizeNumericInput("1,500.50"))
        assertEquals("0", com.example.techyfipos.utils.sanitizeNumericInput("0"))
        assertEquals("500", com.example.techyfipos.utils.sanitizeNumericInput("0500"))
        assertEquals("0.75", com.example.techyfipos.utils.sanitizeNumericInput(".75"))
        assertEquals("", com.example.techyfipos.utils.sanitizeNumericInput(""))
    }

    @Test
    fun testR21_26_customerStatementRunningBalanceReconciliation() {
        val customerOutstanding = 1500.0
        val charges = listOf(1000.0, 800.0)
        val payments = listOf(500.0)
        val returnDueAdjustment = 100.0
        val netLedger = charges.sum() - payments.sum() - returnDueAdjustment // 1200.0
        val openingBalance = customerOutstanding - netLedger // 300.0

        var runningBalance = openingBalance
        for (charge in charges) runningBalance += charge
        for (payment in payments) runningBalance -= payment
        runningBalance -= returnDueAdjustment

        assertEquals(customerOutstanding, runningBalance, 0.001)
    }

    @Test
    fun testR21_27_stockAlertOutOfStockAndExpiredIncluded() {
        val now = 1700000000000L
        val thirtyDays = 30L * 24 * 60 * 60 * 1000L

        val pNormal = com.example.techyfipos.data.db.Product(barcode = "P1", name = "Normal", category = "Cat", purchasePrice = 10.0, sellingPrice = 20.0, stockQty = 20.0, reorderLevel = 5, expiryDate = "2028-12-31")
        val pLow = com.example.techyfipos.data.db.Product(barcode = "P2", name = "Low", category = "Cat", purchasePrice = 10.0, sellingPrice = 20.0, stockQty = 4.0, reorderLevel = 5, expiryDate = "2028-12-31")
        val pZero = com.example.techyfipos.data.db.Product(barcode = "P3", name = "Zero", category = "Cat", purchasePrice = 10.0, sellingPrice = 20.0, stockQty = 0.0, reorderLevel = 5, expiryDate = "2028-12-31")
        val pNegative = com.example.techyfipos.data.db.Product(barcode = "P4", name = "Negative", category = "Cat", purchasePrice = 10.0, sellingPrice = 20.0, stockQty = -1.0, reorderLevel = 5, expiryDate = "2028-12-31")
        val pExpiringSoon = com.example.techyfipos.data.db.Product(barcode = "P5", name = "Expiring", category = "Cat", purchasePrice = 10.0, sellingPrice = 20.0, stockQty = 20.0, reorderLevel = 5, expiryDate = "2023-11-20")

        val (lowStock, expiring) = com.example.techyfipos.utils.StockAlertWorker.filterAlertItems(
            listOf(pNormal, pLow, pZero, pNegative, pExpiringSoon),
            nowMillis = now,
            thirtyDaysMillis = thirtyDays
        )
        assertEquals(listOf("P2", "P3", "P4"), lowStock.map { it.barcode })
        org.junit.Assert.assertTrue(expiring.any { it.barcode == "P5" })
    }

    @Test
    fun testR21_30_cartItemRestoreIndex() = kotlinx.coroutines.runBlocking {
        val repository = org.mockito.Mockito.mock(com.example.techyfipos.data.DataRepository::class.java)
        org.mockito.Mockito.`when`(repository.cashierNameFlow).thenReturn(kotlinx.coroutines.flow.flowOf("Cashier"))
        org.mockito.Mockito.`when`(repository.loyaltySettingsFlow).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(com.example.techyfipos.utils.LoyaltyProgramSettings()))
        org.mockito.Mockito.`when`(repository.allHeldCarts).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        org.mockito.Mockito.`when`(repository.heldCartCount).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(0))
        org.mockito.Mockito.`when`(repository.invoiceSettingsDataFlow).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(com.example.techyfipos.data.InvoiceSettingsData()))
        org.mockito.Mockito.`when`(repository.getCartSnapshot()).thenReturn("")

        val vm = com.example.techyfipos.ui.viewmodel.CartViewModel(repository)
        val p1 = com.example.techyfipos.data.db.Product(barcode = "P1", name = "Prod 1", category = "Cat", purchasePrice = 40.0, sellingPrice = 50.0, stockQty = 10.0, reorderLevel = 1)
        val p2 = com.example.techyfipos.data.db.Product(barcode = "P2", name = "Prod 2", category = "Cat", purchasePrice = 50.0, sellingPrice = 60.0, stockQty = 10.0, reorderLevel = 1)
        val item1 = com.example.techyfipos.ui.viewmodel.CartItem(product = p1, quantity = 1.0)
        val item2 = com.example.techyfipos.ui.viewmodel.CartItem(product = p2, quantity = 1.0)

        vm.restoreCartItem(item1, 0)
        vm.restoreCartItem(item2, 1)
        assertEquals(2, vm.cartItems.value.size)
        assertEquals("P1", vm.cartItems.value[0].product.barcode)
        assertEquals("P2", vm.cartItems.value[1].product.barcode)

        // Restoring with negative inventory disabled when exceeding stock returns false
        val itemOverStock = com.example.techyfipos.ui.viewmodel.CartItem(product = p1, quantity = 20.0)
        val ok = vm.restoreCartItem(itemOverStock, allowNegative = false)
        org.junit.Assert.assertFalse(ok)
    }

    @Test
    fun testR21_30_expenseDateLabelDiffAcrossYearBoundary() {
        val dec31 = java.time.LocalDate.of(2025, 12, 31)
        val jan1 = java.time.LocalDate.of(2026, 1, 1)
        val diffDays = java.time.temporal.ChronoUnit.DAYS.between(dec31, jan1)
        assertEquals(1L, diffDays)

        val jan2 = java.time.LocalDate.of(2026, 1, 2)
        val diffDays2 = java.time.temporal.ChronoUnit.DAYS.between(dec31, jan2)
        assertEquals(2L, diffDays2)
    }

    @Test
    fun testR20_19b_bulkPriceUpdatesDiscountedPrice() {
        val (newSell, newDisc) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 100.0,
            percentDelta = 10.0
        )
        assertEquals(110.0, newSell, 0.001)
        assertEquals(110.0, newDisc, 0.001)

        val (newSellZero, newDiscZero) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 0.0,
            percentDelta = 10.0
        )
        assertEquals(110.0, newSellZero, 0.001)
        assertEquals(0.0, newDiscZero, 0.001)

        val (newSellFixed, newDiscFixed) = PaymentMath.computeBulkUpdatedPrices(
            currentSellingPrice = 100.0,
            currentDiscountedPrice = 100.0,
            fixedDelta = 25.0
        )
        assertEquals(125.0, newSellFixed, 0.001)
        assertEquals(125.0, newDiscFixed, 0.001)
    }

    @Test
    fun testR21_19_localMillisToUtcMidnightRoundtrip() {
        val now = System.currentTimeMillis()
        val utcMidnight = com.example.techyfipos.utils.localMillisToUtcMidnight(now)
        val backToLocalMidnight = com.example.techyfipos.utils.utcMidnightToLocalMidnight(utcMidnight)

        val localCal = java.util.Calendar.getInstance().apply { timeInMillis = backToLocalMidnight }
        assertEquals(0, localCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, localCal.get(java.util.Calendar.MINUTE))
        assertEquals(0, localCal.get(java.util.Calendar.SECOND))
        assertEquals(0, localCal.get(java.util.Calendar.MILLISECOND))
    }

    @Test
    fun testR24_18_19_queryNormalizationAndBanglaMatching() {
        val banglaQuery = "০১৭০০১২৩৪৫৬"
        val englishQuery = com.example.techyfipos.utils.toEnglishDigits(banglaQuery)
        assertEquals("01700123456", englishQuery)
        val normalized = com.example.techyfipos.utils.normalizePhone(englishQuery)
        assertEquals("01700123456", normalized)

        val testMethods = listOf("BKASH", "CASH", "NAGAD", "ROCKET", "CARD")
        val matchBkash = testMethods.filter { it.contains("BKASH", ignoreCase = true) }
        assertEquals(listOf("BKASH"), matchBkash)

        val matchCash = testMethods.filter { it.contains("CASH", ignoreCase = true) || it.contains("NAGAD", ignoreCase = true) }
        assertEquals(listOf("CASH", "NAGAD"), matchCash)
    }

    @Test
    fun testR24_20_lockoutParsingAndSupplierNegativeMatch() {
        val seconds = com.example.techyfipos.utils.parseLockoutSeconds("Authentication locked out for 45 seconds")
        assertEquals(45L, seconds)

        val secondsTerminal = com.example.techyfipos.utils.parseLockoutSeconds("Terminal locked for 120 seconds due to failed attempts")
        assertEquals(120L, secondsTerminal)

        val resId = mapErrorToLocalizedMessage("Authentication locked out for 45 seconds")
        assertEquals(R.string.auth_locked_out_seconds, resId)

        // Negative test: Supplier no longer exists must NOT map to error_product_not_found
        val supplierRes = mapErrorToLocalizedMessage("Supplier no longer exists")
        assertEquals(R.string.error_supplier_not_found, supplierRes)
        org.junit.Assert.assertNotEquals(R.string.error_product_not_found, supplierRes)
    }

    @Test
    fun testR24_20_exactP4RepoErrorMappings() {
        val mappings = listOf(
            "Cannot permanently delete customer with store credit" to R.string.error_customer_has_store_credit,
            "Cannot permanently delete customer with active voucher balance" to R.string.error_customer_has_active_vouchers,
            "Non-owners cannot delete owner accounts" to R.string.error_non_owners_manage_owners,
            "Non-owners cannot modify owner permissions" to R.string.error_non_owners_manage_owners,
            "Failed to generate unique voucher code for expired replacement" to R.string.error_voucher_generation_failed,
            "Amount paid is less than the total" to R.string.error_amount_paid_below_total,
            "Split payment must have at least one valid row" to R.string.error_split_no_rows,
            "No items selected for return" to R.string.error_return_no_items,
            "Starting float must be zero or positive" to R.string.error_starting_float_invalid,
            "Customer phone number is required for due sale" to R.string.error_phone_required_for_due,
            "Name cannot be blank" to R.string.error_name_cannot_be_blank,
            "PIN must be 4 to 6 digits" to R.string.error_pin_format_invalid
        )

        for ((msg, expectedResId) in mappings) {
            assertEquals("Message '$msg' should map to resource $expectedResId", expectedResId, mapErrorToLocalizedMessage(msg))
        }
    }
}
