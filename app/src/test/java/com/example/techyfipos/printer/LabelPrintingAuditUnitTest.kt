package com.example.techyfipos.printer

import android.content.Context
import android.graphics.Bitmap
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.printer.BatchLabelPrintResult
import com.example.techyfipos.printer.DirectPrintResult
import com.example.techyfipos.printer.LabelJob
import com.example.techyfipos.utils.LabelPrintOutcome
import com.example.techyfipos.utils.ProductCodeLabelSize
import com.example.techyfipos.utils.ProductCodePriceTier
import com.example.techyfipos.utils.ProductCodeType
import com.example.techyfipos.utils.buildBatchLabelItemsResult
import com.example.techyfipos.utils.clampCopies
import com.example.techyfipos.utils.formatProductCodeLabelPriceString
import com.example.techyfipos.utils.getRecommendedCodeType
import com.example.techyfipos.utils.isValidEan13
import com.example.techyfipos.utils.isValidEan8
import com.example.techyfipos.utils.printLabelImages
import com.example.techyfipos.utils.resolveLabelDimensionsMm
import com.example.techyfipos.utils.stockToPrintCopies
import com.example.techyfipos.utils.isBarcodeTooDenseForLabel
import com.example.techyfipos.printer.calculateMultiUpRowWidthMm
import com.example.techyfipos.utils.ProductLabelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class LabelPrintingAuditUnitTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDummyProduct(
        barcode: String,
        name: String,
        stockQty: Double = 10.0,
        sellingPrice: Double = 100.0,
        mrpPrice: Double = 120.0,
        wholesalePrice: Double = 80.0,
        mfgDate: String = "2026-01-01",
        expDate: String = "2027-01-01"
    ): Product {
        return Product(
            barcode = barcode,
            name = name,
            category = "General",
            purchasePrice = 60.0,
            sellingPrice = sellingPrice,
            stockQty = stockQty,
            reorderLevel = 5,
            mfgOrPkgType = "Mfg",
            mfgOrPkgDate = mfgDate,
            expiryDate = expDate,
            wholesalePrice = wholesalePrice,
            mrpPrice = mrpPrice
        )
    }

    // 1. Tests for EAN-13 and EAN-8 validation checksums (LBL-16)
    @Test
    fun testValidEan13Checksums() {
        // Valid standard EAN-13 barcodes
        assertTrue(isValidEan13("5901234123457"))
        assertTrue(isValidEan13("9780201379624"))
        assertTrue(isValidEan13("4006381333931"))

        // Invalid checksum digit
        assertFalse(isValidEan13("5901234123458"))
        // Too short / too long
        assertFalse(isValidEan13("590123412345"))
        assertFalse(isValidEan13("59012341234570"))
        // Contains non-digit
        assertFalse(isValidEan13("590123412345X"))
        assertFalse(isValidEan13(""))
    }

    @Test
    fun testValidEan8Checksums() {
        // Valid standard EAN-8 barcodes
        assertTrue(isValidEan8("96385074"))
        assertTrue(isValidEan8("65833254"))

        // Invalid checksum digit
        assertFalse(isValidEan8("96385075"))
        // Too short / too long
        assertFalse(isValidEan8("9638507"))
        assertFalse(isValidEan8("963850740"))
        // Contains non-digit
        assertFalse(isValidEan8("9638507A"))
        assertFalse(isValidEan8(""))
    }

    @Test
    fun testGetRecommendedCodeType() {
        assertEquals(ProductCodeType.EAN_13, getRecommendedCodeType("5901234123457"))
        assertEquals(ProductCodeType.EAN_8, getRecommendedCodeType("96385074"))
        assertEquals(ProductCodeType.BARCODE, getRecommendedCodeType("ABC-12345"))
        assertEquals(ProductCodeType.BARCODE, getRecommendedCodeType("5901234123458")) // Invalid EAN-13 check digit
    }

    // 2. Tests for Copies Clamping and Stock-to-Copies Rule (LBL-07, LBL-10)
    @Test
    fun testCopiesClamping() {
        assertEquals(1, clampCopies(null))
        assertEquals(1, clampCopies(0))
        assertEquals(1, clampCopies(-10))
        assertEquals(1, clampCopies(1))
        assertEquals(42, clampCopies(42))
        assertEquals(500, clampCopies(500))
        assertEquals(500, clampCopies(9999))
    }

    @Test
    fun testStockToPrintCopiesRule() {
        assertEquals(1, stockToPrintCopies(0.0))
        assertEquals(1, stockToPrintCopies(-15.5))
        assertEquals(1, stockToPrintCopies(0.2)) // Ceil is 1
        assertEquals(5, stockToPrintCopies(4.1)) // Ceil is 5
        assertEquals(10, stockToPrintCopies(10.0))
        assertEquals(500, stockToPrintCopies(500.0))
        assertEquals(500, stockToPrintCopies(1200.0)) // Clamped to 500
    }

    // 3. Tests for formatProductCodeLabelPriceString (LBL-05)
    @Test
    fun testPriceTierFormattingFallbacks() {
        // Selling tier
        val sellingResult = formatProductCodeLabelPriceString(
            tier = ProductCodePriceTier.SELLING,
            sellingPrice = 150.0,
            mrpPrice = 0.0,
            wholesalePrice = 0.0
        )
        assertEquals("Price: ৳150", sellingResult)

        // DUAL tier with unset MRP falls back to selling price without inventing MRP
        val dualUnsetMrp = formatProductCodeLabelPriceString(
            tier = ProductCodePriceTier.DUAL,
            sellingPrice = 100.0,
            mrpPrice = 0.0,
            wholesalePrice = 80.0
        )
        assertEquals("Price: ৳100", dualUnsetMrp)

        // DUAL tier with valid MRP
        val dualValidMrp = formatProductCodeLabelPriceString(
            tier = ProductCodePriceTier.DUAL,
            sellingPrice = 100.0,
            mrpPrice = 120.0,
            wholesalePrice = 80.0
        )
        assertEquals("MRP: ৳120 | Offer: ৳100", dualValidMrp)

        // MRP tier with unset MRP falls back to selling price
        val mrpUnset = formatProductCodeLabelPriceString(
            tier = ProductCodePriceTier.MRP,
            sellingPrice = 100.0,
            mrpPrice = 0.0,
            wholesalePrice = 80.0
        )
        assertEquals("Price: ৳100", mrpUnset)

        // Wholesale tier with unset wholesale price falls back to selling price
        val wholesaleUnset = formatProductCodeLabelPriceString(
            tier = ProductCodePriceTier.WHOLESALE,
            sellingPrice = 100.0,
            mrpPrice = 120.0,
            wholesalePrice = 0.0
        )
        assertEquals("Price: ৳100", wholesaleUnset)

        // Wholesale tier with valid wholesale price
        val wholesaleValid = formatProductCodeLabelPriceString(
            tier = ProductCodePriceTier.WHOLESALE,
            sellingPrice = 100.0,
            mrpPrice = 120.0,
            wholesalePrice = 85.0
        )
        assertEquals("Wholesale Price: ৳85", wholesaleValid)
    }

    // 4. Tests for buildBatchLabelItemsResult (LBL-06, LBL-15)
    @Test
    fun testBatchBuildingEnforces500CapAndTracksCutProducts() {
        val products = (1..60).map { i ->
            createDummyProduct(barcode = "PROD_$i", name = "Product $i", stockQty = 10.0)
        }

        // Each product requests 10 copies. 60 * 10 = 600 total requested copies.
        val result = buildBatchLabelItemsResult(
            candidateProducts = products,
            copiesModeByStock = false,
            batchCopies = 10,
            priceTier = ProductCodePriceTier.SELLING,
            showUnit = false,
            showBanglaDigits = false,
            useProductDates = false,
            mfgOrPkgType = "Mfg",
            mfgOrPkgDate = "2026-01-01",
            expDate = "2026-12-31",
            maxTotalLabels = 500
        )

        assertEquals(600, result.totalRequestedLabels)
        assertEquals(500, result.totalIncludedLabels)
        assertEquals(50, result.items.size) // 50 items * 10 copies = 500
        assertEquals(10, result.cutProducts.size) // 10 products completely cut
        assertTrue(result.cutProducts.contains("Product 51"))
        assertTrue(result.cutProducts.contains("Product 60"))
    }

    @Test
    fun testBatchBuildingWithCustomQuantities() {
        val prodA = createDummyProduct(barcode = "A1", name = "Alpha")
        val prodB = createDummyProduct(barcode = "B2", name = "Beta")

        val customQtys = mapOf("A1" to 5, "B2" to 8)
        val result = buildBatchLabelItemsResult(
            candidateProducts = listOf(prodA, prodB),
            copiesModeByStock = false,
            batchCopies = 1,
            priceTier = ProductCodePriceTier.SELLING,
            showUnit = false,
            showBanglaDigits = false,
            useProductDates = false,
            mfgOrPkgType = "Mfg",
            mfgOrPkgDate = "",
            expDate = "",
            customQuantities = customQtys
        )

        assertEquals(13, result.totalIncludedLabels)
        assertEquals(2, result.items.size)
        assertEquals(5, result.items.first { it.barcode == "A1" }.quantity)
        assertEquals(8, result.items.first { it.barcode == "B2" }.quantity)
        assertTrue(result.cutProducts.isEmpty())
    }

    @Test
    fun testBatchBuildingPerProductDates() {
        val prod1 = createDummyProduct(barcode = "P1", name = "P1", mfgDate = "2025-05-01", expDate = "2026-05-01")
        val prod2 = createDummyProduct(barcode = "P2", name = "P2", mfgDate = "2025-10-10", expDate = "2027-10-10")

        // With useProductDates = true
        val resultWithProductDates = buildBatchLabelItemsResult(
            candidateProducts = listOf(prod1, prod2),
            copiesModeByStock = false,
            batchCopies = 1,
            priceTier = ProductCodePriceTier.SELLING,
            showUnit = false,
            showBanglaDigits = false,
            useProductDates = true,
            mfgOrPkgType = "Mfg",
            mfgOrPkgDate = "DEFAULT_MFG",
            expDate = "DEFAULT_EXP"
        )
        assertEquals("2025-05-01", resultWithProductDates.items[0].mfgOrPkgDate)
        assertEquals("2026-05-01", resultWithProductDates.items[0].expDate)
        assertEquals("2025-10-10", resultWithProductDates.items[1].mfgOrPkgDate)
        assertEquals("2027-10-10", resultWithProductDates.items[1].expDate)

        // With useProductDates = false: uses default dates
        val resultWithDefaultDates = buildBatchLabelItemsResult(
            candidateProducts = listOf(prod1, prod2),
            copiesModeByStock = false,
            batchCopies = 1,
            priceTier = ProductCodePriceTier.SELLING,
            showUnit = false,
            showBanglaDigits = false,
            useProductDates = false,
            mfgOrPkgType = "Pkg",
            mfgOrPkgDate = "DEFAULT_MFG",
            expDate = "DEFAULT_EXP"
        )
        assertEquals("DEFAULT_MFG", resultWithDefaultDates.items[0].mfgOrPkgDate)
        assertEquals("DEFAULT_EXP", resultWithDefaultDates.items[0].expDate)
        assertEquals("Pkg", resultWithDefaultDates.items[0].mfgOrPkgType)
    }

    // 5. Tests for buildTsplPayload DPI Calculation (LBL-12)
    @Test
    fun testBuildTsplPayloadDpiCalculation() {
        val dummyBytes = byteArrayOf(0x00, 0x01)

        // 50mm width at 203 DPI: 50 * (203 / 25.4) = 399.6 -> 400 dots.
        // 400 dots / 8 = 50 bytes per line.
        val dots203 = (50f * (203f / 25.4f)).roundToInt()
        val payload203 = buildTsplPayload(
            widthMm = 50f,
            heightMm = 30f,
            gapMm = 2f,
            copies = 1,
            darkness = 10,
            bitmapBytes = dummyBytes,
            targetWidthDots = dots203,
            targetHeightDots = 240
        )
        val text203 = String(payload203, Charsets.US_ASCII)
        assertTrue(text203.contains("SIZE 50 mm,30 mm"))
        assertTrue(text203.contains("BITMAP 0,0,50,240,0,"))

        // 50mm width at 300 DPI: 50 * (300 / 25.4) = 590.55 -> 591 dots.
        // (591 + 7) / 8 = 74 bytes per line.
        val dots300 = (50f * (300f / 25.4f)).roundToInt()
        val payload300 = buildTsplPayload(
            widthMm = 50f,
            heightMm = 30f,
            gapMm = 2f,
            copies = 1,
            darkness = 10,
            bitmapBytes = dummyBytes,
            targetWidthDots = dots300,
            targetHeightDots = 354
        )
        val text300 = String(payload300, Charsets.US_ASCII)
        assertTrue(text300.contains("BITMAP 0,0,74,354,0,"))
    }

    // 6. Tests for Multi-Up Roll Calculations (LBL-17)
    @Test
    fun testCalculateMultiUpRowWidthMm() {
        // 1 across: returns single label width
        assertEquals(30f, calculateMultiUpRowWidthMm(1, 2f, 30f), 0.01f)

        // 2 across: 2 * 30 + 1 * 2 = 62 mm
        assertEquals(62f, calculateMultiUpRowWidthMm(2, 2f, 30f), 0.01f)

        // 3 across: 3 * 30 + 2 * 3 = 96 mm
        assertEquals(96f, calculateMultiUpRowWidthMm(3, 3f, 30f), 0.01f)

        // Clamping across outside 1..3
        assertEquals(30f, calculateMultiUpRowWidthMm(0, 2f, 30f), 0.01f)
        assertEquals(96f, calculateMultiUpRowWidthMm(5, 3f, 30f), 0.01f) // Clamped to 3
    }

    // 7. Tests for printLabelImages flow and routing (LBL-03, LBL-20)
    @Test
    fun testPrintLabelImagesSystemModeSuccess() = runBlocking {
        val context = mock(Context::class.java)
        val dummyPdf = File.createTempFile("test_label", ".pdf").apply { deleteOnExit() }
        val settings = InvoiceSettingsData(labelPrintMode = "SYSTEM")

        val outcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = dummyPdf,
            widthMm = 50f,
            heightMm = 30f,
            copies = 1,
            jobName = "TestJob",
            settings = settings,
            pdfPrinter = { _, _, _, _, _ -> true } // Fake PDF printer succeeds
        )
        assertEquals(LabelPrintOutcome.OPENED_SYSTEM_DIALOG, outcome)
        assertTrue(outcome.isSuccess)
    }

    @Test
    fun testPrintLabelImagesSystemModeFailsWhenNoPdf() = runBlocking {
        val context = mock(Context::class.java)
        val settings = InvoiceSettingsData(labelPrintMode = "SYSTEM")

        val outcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = null,
            widthMm = 50f,
            heightMm = 30f,
            copies = 1,
            jobName = "TestJob",
            settings = settings,
            pdfFallbackBuilder = { null }
        )
        assertEquals(LabelPrintOutcome.FAILED, outcome)
        assertFalse(outcome.isSuccess)
    }

    @Test
    fun testPrintLabelImagesSamePrinterAsReceiptFails() = runBlocking {
        val context = mock(Context::class.java)
        val dummyPdf = File.createTempFile("test_label", ".pdf").apply { deleteOnExit() }
        val settings = InvoiceSettingsData(
            labelPrintMode = "BLUETOOTH",
            labelPrinterConnection = "BLUETOOTH",
            labelPrinterAddress = "AA:BB:CC:DD:EE:FF",
            directBluetoothPrinterAddress = "AA:BB:CC:DD:EE:FF" // Same address as receipt printer
        )

        var batchCalled = false
        val outcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = dummyPdf,
            widthMm = 50f,
            heightMm = 30f,
            copies = 1,
            jobName = "TestJob",
            settings = settings,
            batchPrinter = { _, _ ->
                batchCalled = true
                BatchLabelPrintResult(DirectPrintResult.Success, 1)
            }
        )
        assertEquals(LabelPrintOutcome.FAILED, outcome)
        assertFalse("Batch printer must not be called when label and receipt printer share same address", batchCalled)
    }

    @Test
    fun testPrintLabelImagesDirectSuccess() = runBlocking {
        val context = mock(Context::class.java)
        val settings = InvoiceSettingsData(
            labelPrintMode = "BLUETOOTH",
            labelPrinterConnection = "BLUETOOTH",
            labelPrinterAddress = "AA:BB:CC:DD:EE:01",
            directBluetoothPrinterAddress = "11:22:33:44:55:66"
        )

        val outcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = null,
            widthMm = 50f,
            heightMm = 30f,
            copies = 2,
            jobName = "DirectJob",
            settings = settings,
            batchPrinter = { _, _ ->
                BatchLabelPrintResult(DirectPrintResult.Success, 2)
            }
        )
        assertEquals(LabelPrintOutcome.SENT_DIRECT, outcome)
        assertTrue(outcome.isSuccess)
    }

    @Test
    fun testPrintLabelImagesAutoModeWithDirectFailureFallsBackToSystemDialog() = runBlocking {
        val context = mock(Context::class.java)
        val dummyPdf = File.createTempFile("test_fallback", ".pdf").apply { deleteOnExit() }
        val settings = InvoiceSettingsData(
            labelPrintMode = "AUTO",
            labelPrinterConnection = "BLUETOOTH",
            labelPrinterAddress = "AA:BB:CC:DD:EE:01",
            directBluetoothPrinterAddress = "11:22:33:44:55:66"
        )

        var pdfDialogOpened = false
        val outcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = dummyPdf,
            widthMm = 50f,
            heightMm = 30f,
            copies = 1,
            jobName = "FallbackJob",
            settings = settings,
            batchPrinter = { _, _ ->
                // Direct print fails with zero labels sent
                BatchLabelPrintResult(DirectPrintResult.Failed("Connection refused"), 0)
            },
            pdfPrinter = { _, _, _, _, _ ->
                pdfDialogOpened = true
                true
            }
        )
        assertEquals(LabelPrintOutcome.OPENED_SYSTEM_DIALOG, outcome)
        assertTrue(pdfDialogOpened)
    }

    @Test
    fun testPrintLabelImagesAutoModeWithPartialFailureDoesNotFallBack() = runBlocking {
        val context = mock(Context::class.java)
        val dummyPdf = File.createTempFile("test_fallback", ".pdf").apply { deleteOnExit() }
        val settings = InvoiceSettingsData(
            labelPrintMode = "AUTO",
            labelPrinterConnection = "BLUETOOTH",
            labelPrinterAddress = "AA:BB:CC:DD:EE:01",
            directBluetoothPrinterAddress = "11:22:33:44:55:66"
        )

        var pdfDialogOpened = false
        val outcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = dummyPdf,
            widthMm = 50f,
            heightMm = 30f,
            copies = 5,
            jobName = "PartialJob",
            settings = settings,
            batchPrinter = { _, _ ->
                // Partial failure: 2 labels sent before failure
                BatchLabelPrintResult(DirectPrintResult.Failed("Bluetooth disconnected mid-print"), 2)
            },
            pdfPrinter = { _, _, _, _, _ ->
                pdfDialogOpened = true
                true
            }
        )
        // Partial failure must NOT trigger system dialog (which would double-print already printed labels)
        assertEquals(LabelPrintOutcome.FAILED, outcome)
        assertFalse(pdfDialogOpened)
    }

    @Test
    fun testPrintLabelImagesNoAddressBluetoothFailsAutoFallsBack() = runBlocking {
        val context = mock(Context::class.java)
        val dummyPdf = File.createTempFile("test_no_addr", ".pdf").apply { deleteOnExit() }

        // BLUETOOTH mode with no address: fails
        val bluetoothSettings = InvoiceSettingsData(
            labelPrintMode = "BLUETOOTH",
            labelPrinterAddress = ""
        )
        val btOutcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = dummyPdf,
            widthMm = 50f,
            heightMm = 30f,
            copies = 1,
            jobName = "BtNoAddr",
            settings = bluetoothSettings
        )
        assertEquals(LabelPrintOutcome.FAILED, btOutcome)

        // AUTO mode with no address: falls back to system print dialog
        val autoSettings = InvoiceSettingsData(
            labelPrintMode = "AUTO",
            labelPrinterAddress = ""
        )
        var autoPdfOpened = false
        val autoOutcome = printLabelImages(
            context = context,
            activity = null,
            labels = emptyList(),
            pdfFallback = dummyPdf,
            widthMm = 50f,
            heightMm = 30f,
            copies = 1,
            jobName = "AutoNoAddr",
            settings = autoSettings,
            pdfPrinter = { _, _, _, _, _ ->
                autoPdfOpened = true
                true
            }
        )
        assertEquals(LabelPrintOutcome.OPENED_SYSTEM_DIALOG, autoOutcome)
        assertTrue(autoPdfOpened)
    }

    // 8. Tests for LabelPrintOutcome Properties (LBL-03)
    @Test
    fun testLabelPrintOutcomeProperties() {
        assertTrue(LabelPrintOutcome.SENT_DIRECT.isSuccess)
        assertTrue(LabelPrintOutcome.OPENED_SYSTEM_DIALOG.isSuccess)
        assertFalse(LabelPrintOutcome.FAILED.isSuccess)
    }

    // 9. Tests for Label Dimensions Clamping (LBL-11, LBL-25)
    @Test
    fun testResolveLabelDimensionsClamping() {
        // Standard predefined sizes return exact dimensions
        val standard50x30 = resolveLabelDimensionsMm(ProductCodeLabelSize.SIZE_50_X_30, 0f, 0f)
        assertEquals(50f, standard50x30.first, 0.01f)
        assertEquals(30f, standard50x30.second, 0.01f)

        // Custom size below minimum (min is 15 mm width, 10 mm height)
        val customBelowMin = resolveLabelDimensionsMm(ProductCodeLabelSize.CUSTOM, 5f, 5f)
        assertEquals(15f, customBelowMin.first, 0.01f)
        assertEquals(10f, customBelowMin.second, 0.01f)

        // Custom size above maximum (max is 150 mm width, 200 mm height)
        val customAboveMax = resolveLabelDimensionsMm(ProductCodeLabelSize.CUSTOM, 200f, 250f)
        assertEquals(150f, customAboveMax.first, 0.01f)
        assertEquals(200f, customAboveMax.second, 0.01f)

        // Valid custom size within bounds
        val customValid = resolveLabelDimensionsMm(ProductCodeLabelSize.CUSTOM, 60f, 40f)
        assertEquals(60f, customValid.first, 0.01f)
        assertEquals(40f, customValid.second, 0.01f)
    }

    // 10. Tests for InvoiceSettingsData labelDpi JSON Deserialization (LBL-12)
    @Test
    fun testInvoiceSettingsDpiDefaulting() {
        // Legacy backup without labelDpi field defaults to 203
        val legacyJson = """{"storeName":"Legacy POS Store"}"""
        val legacySettings = InvoiceSettingsData.fromJson(legacyJson)
        assertEquals(203, legacySettings.labelDpi)

        // Explicit 300 DPI
        val json300 = """{"storeName":"300 DPI Store","labelDpi":300}"""
        val settings300 = InvoiceSettingsData.fromJson(json300)
        assertEquals(300, settings300.labelDpi)

        // Any non-300 value falls back to 203
        val jsonOther = """{"storeName":"Other DPI Store","labelDpi":600}"""
        val settingsOther = InvoiceSettingsData.fromJson(jsonOther)
        assertEquals(203, settingsOther.labelDpi)
    }

    // 11. Tests for Code 128 Numeric Compression in Barcode Density Warning (R24-09)
    @Test
    fun testBarcodeTooDenseForLabelCode128NumericCompression() {
        // Standard 13-digit numeric barcode on 50x30 mm label at 203 DPI
        // In numeric compression mode (Mode C), 13 digits pack into ~7 symbol pairs (112 modules).
        // On 50 mm (44 mm printable = ~352 dots), module width is ~3.14 dots (>= 2 dots), so not too dense!
        val is50mmDense = isBarcodeTooDenseForLabel("1234567890123", ProductCodeType.BARCODE, 50f, 203)
        assertFalse(is50mmDense)

        // On very small 25 mm label (22 mm printable = ~176 dots), module width is ~1.57 dots (< 2 dots), so too dense!
        val is25mmDense = isBarcodeTooDenseForLabel("1234567890123", ProductCodeType.BARCODE, 25f, 203)
        assertTrue(is25mmDense)

        // QR Code is 2D matrix, never flagged by 1D density check
        assertFalse(isBarcodeTooDenseForLabel("1234567890123", ProductCodeType.QR_CODE, 25f, 203))
    }

    // 12. Tests for Multi-Up Print Head Width Limit (R24-12)
    @Test
    fun testMultiUpRowWidthCalculations() {
        // 1-across 40 mm is 40 mm
        assertEquals(40f, calculateMultiUpRowWidthMm(1, 2f, 40f), 0.01f)

        // 2-across 40 mm with 2 mm gap: 40 * 2 + 2 = 82 mm
        assertEquals(82f, calculateMultiUpRowWidthMm(2, 2f, 40f), 0.01f)

        // 3-across 40 mm with 2 mm gap: 40 * 3 + 2 * 2 = 124 mm (> 108 mm print head)
        val threeAcrossWidth = calculateMultiUpRowWidthMm(3, 2f, 40f)
        assertEquals(124f, threeAcrossWidth, 0.01f)
        assertTrue(threeAcrossWidth > 108f)
    }

    // 13. Tests for Batch Label Items in Custom Per-Product and Stock Modes (R24-10, R24-13)
    @Test
    fun testBuildBatchLabelItemsCustomPerProductAndStock() {
        val p1 = createDummyProduct("BAR01", "Product One", stockQty = 5.0)
        val p2 = createDummyProduct("BAR02", "Product Two (Zero Stock)", stockQty = 0.0)
        val p3 = createDummyProduct("BAR03", "Product Three", stockQty = 10.0)
        val products = listOf(p1, p2, p3)

        // In PER_PRODUCT mode with custom quantities map:
        // p1 entered as 4, p2 and p3 omitted. Omitted products default to 1 copy!
        val customRes = buildBatchLabelItemsResult(
            candidateProducts = products,
            copiesModeByStock = false,
            batchCopies = 2,
            priceTier = ProductCodePriceTier.SELLING,
            showUnit = false,
            showBanglaDigits = false,
            useProductDates = false,
            mfgOrPkgType = "Mfg",
            mfgOrPkgDate = "",
            expDate = "",
            customQuantities = mapOf("BAR01" to 4),
            isCustomPerProduct = true
        )
        assertEquals(3, customRes.items.size)
        assertEquals(4, customRes.items.first { it.barcode == "BAR01" }.quantity)
        assertEquals(1, customRes.items.first { it.barcode == "BAR02" }.quantity)
        assertEquals(1, customRes.items.first { it.barcode == "BAR03" }.quantity)
        assertEquals(6, customRes.totalIncludedLabels)

        // In STOCK mode: zero stock product yields 0 copies and is skipped
        val stockRes = buildBatchLabelItemsResult(
            candidateProducts = products,
            copiesModeByStock = true,
            batchCopies = 1,
            priceTier = ProductCodePriceTier.SELLING,
            showUnit = false,
            showBanglaDigits = false,
            useProductDates = false,
            mfgOrPkgType = "Mfg",
            mfgOrPkgDate = "",
            expDate = "",
            isCustomPerProduct = false
        )
        assertEquals(2, stockRes.items.size)
        assertEquals(5, stockRes.items.first { it.barcode == "BAR01" }.quantity)
        assertEquals(10, stockRes.items.first { it.barcode == "BAR03" }.quantity)
        assertEquals(15, stockRes.totalIncludedLabels)
    }

    // 14. Tests for Retry Remaining Arithmetic (R24-10)
    @Test
    fun testRetryRemainingItemCounts() {
        val item1 = ProductLabelItem("Prod1", "B1", "100", 5)
        val item2 = ProductLabelItem("Prod2", "B2", "200", 3)
        val items = listOf(item1, item2) // total 8 labels

        // Case A: 5 labels sent (item1 completely printed, item2 not printed)
        var sentCounter = 5
        val remA = mutableListOf<ProductLabelItem>()
        for (item in items) {
            if (sentCounter >= item.quantity) {
                sentCounter -= item.quantity
            } else if (sentCounter > 0) {
                val remQty = item.quantity - sentCounter
                sentCounter = 0
                remA.add(item.copy(quantity = remQty))
            } else {
                remA.add(item)
            }
        }
        assertEquals(1, remA.size)
        assertEquals("B2", remA[0].barcode)
        assertEquals(3, remA[0].quantity)

        // Case B: 6 labels sent (item1 printed + 1 of item2, 2 of item2 remain)
        sentCounter = 6
        val remB = mutableListOf<ProductLabelItem>()
        for (item in items) {
            if (sentCounter >= item.quantity) {
                sentCounter -= item.quantity
            } else if (sentCounter > 0) {
                val remQty = item.quantity - sentCounter
                sentCounter = 0
                remB.add(item.copy(quantity = remQty))
            } else {
                remB.add(item)
            }
        }
        assertEquals(1, remB.size)
        assertEquals("B2", remB[0].barcode)
        assertEquals(2, remB[0].quantity)
    }
}

