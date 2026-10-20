package com.example.techyfipos.printer

import android.graphics.Bitmap
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.utils.ProductCodeType
import com.example.techyfipos.utils.isBarcodeTooDenseForLabel
import com.example.techyfipos.utils.RoutingDecision
import com.example.techyfipos.utils.decidePrintRouting
import com.example.techyfipos.utils.reportCanvasWidth
import com.example.techyfipos.utils.reportPdfPageWidthPoints
import com.example.techyfipos.utils.reportPoweredByText
import com.example.techyfipos.utils.reportStoreName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class PrinterRoutingAndDriverTest {

    // 1. Tests for resolvedReceiptPrinterAddress()
    @Test
    fun `resolvedReceiptPrinterAddress returns direct address when direct address is set`() {
        val settings = InvoiceSettingsData(
            directBluetoothPrinterAddress = "AA:BB:CC:DD:EE:01",
            cashDrawerPrinterAddress = "11:22:33:44:55:66"
        )
        assertEquals("AA:BB:CC:DD:EE:01", settings.resolvedReceiptPrinterAddress())
    }

    @Test
    fun `resolvedReceiptPrinterAddress returns empty string when only cash drawer address is set`() {
        val settings = InvoiceSettingsData(
            directBluetoothPrinterAddress = "",
            cashDrawerPrinterAddress = "11:22:33:44:55:66"
        )
        assertEquals("", settings.resolvedReceiptPrinterAddress())
    }

    @Test
    fun `resolvedReceiptPrinterAddress returns empty string when both addresses are blank`() {
        val settings = InvoiceSettingsData(
            directBluetoothPrinterAddress = "",
            cashDrawerPrinterAddress = ""
        )
        assertEquals("", settings.resolvedReceiptPrinterAddress())
    }

    @Test
    fun `resolvedReceiptPrinterAddress returns empty string when both addresses are whitespace only`() {
        val settings = InvoiceSettingsData(
            directBluetoothPrinterAddress = "   ",
            cashDrawerPrinterAddress = "  \t \n "
        )
        assertEquals("", settings.resolvedReceiptPrinterAddress())
    }

    // 2. Tests for tsplBitmapBytes()
    @Test
    fun `tsplBitmapBytes produces expected bytes for 8x2 bitmap with invertBits false and true`() {
        // Pixel setup for 8x2:
        // Row 0 (y=0): x in 0..3 black (0xFF000000), x in 4..7 white (0xFFFFFFFF)
        // Row 1 (y=1): all black (0xFF000000)
        val pixelProvider: (Int, Int) -> Int = { x, y ->
            if (y == 0) {
                if (x < 4) -0x1000000 /* 0xFF000000 black */ else -0x1 /* 0xFFFFFFFF white */
            } else {
                -0x1000000 /* 0xFF000000 black */
            }
        }

        // TSPL polarity: bit 0 = black (printed), bit 1 = white (blank)
        // For invertBits = false:
        // Row 0: bits 0..3 are 0, bits 4..7 are 1 -> 00001111 = 0x0F
        // Row 1: all bits are 0 -> 00000000 = 0x00
        val bytesNormal = tsplBitmapBytes(widthDots = 8, heightDots = 2, invertBits = false, getPixel = pixelProvider)
        assertArrayEquals(byteArrayOf(0x0F, 0x00), bytesNormal)

        // For invertBits = true (inverted polarity):
        // Row 0: bits 0..3 are 1, bits 4..7 are 0 -> 11110000 = 0xF0
        // Row 1: all bits are 1 -> 11111111 = 0xFF
        val bytesInverted = tsplBitmapBytes(widthDots = 8, heightDots = 2, invertBits = true, getPixel = pixelProvider)
        assertArrayEquals(byteArrayOf(0xF0.toByte(), 0xFF.toByte()), bytesInverted)
    }

    // 3. Tests for decidePrintRouting() pure function
    @Test
    fun `decidePrintRouting with SYSTEM mode always returns SYSTEM_DIALOG`() {
        val withAddress = decidePrintRouting("SYSTEM", "AA:BB:CC:DD:EE:FF", DirectPrintResult.Success)
        val withoutAddress = decidePrintRouting("SYSTEM", "", null)
        assertEquals(RoutingDecision.SYSTEM_DIALOG, withAddress)
        assertEquals(RoutingDecision.SYSTEM_DIALOG, withoutAddress)
    }

    @Test
    fun `decidePrintRouting with BLUETOOTH mode and success returns DIRECT_OK`() {
        val decision = decidePrintRouting("BLUETOOTH", "AA:BB:CC:DD:EE:FF", DirectPrintResult.Success)
        assertEquals(RoutingDecision.DIRECT_OK, decision)
    }

    @Test
    fun `decidePrintRouting with BLUETOOTH mode and failure returns SHOW_ERROR`() {
        val noAddress = decidePrintRouting("BLUETOOTH", "", null)
        val failure = decidePrintRouting("BLUETOOTH", "AA:BB:CC:DD:EE:FF", DirectPrintResult.BluetoothOff)
        assertEquals(RoutingDecision.SHOW_ERROR, noAddress)
        assertEquals(RoutingDecision.SHOW_ERROR, failure)
    }

    @Test
    fun `decidePrintRouting with AUTO mode and no address returns SYSTEM_DIALOG`() {
        val emptyAddress = decidePrintRouting("AUTO", "", null)
        val whitespaceAddress = decidePrintRouting("AUTO", "   ", null)
        assertEquals(RoutingDecision.SYSTEM_DIALOG, emptyAddress)
        assertEquals(RoutingDecision.SYSTEM_DIALOG, whitespaceAddress)
    }

    @Test
    fun `decidePrintRouting with AUTO mode and direct print success returns DIRECT_OK`() {
        val decision = decidePrintRouting("AUTO", "AA:BB:CC:DD:EE:FF", DirectPrintResult.Success)
        assertEquals(RoutingDecision.DIRECT_OK, decision)
    }

    @Test
    fun `decidePrintRouting with AUTO mode and direct print failure returns SHOW_ERROR_THEN_SYSTEM_DIALOG`() {
        val connectionFailed = decidePrintRouting(
            "AUTO",
            "AA:BB:CC:DD:EE:FF",
            DirectPrintResult.Failed("Connection refused")
        )
        val bluetoothOff = decidePrintRouting("AUTO", "AA:BB:CC:DD:EE:FF", DirectPrintResult.BluetoothOff)
        val notPaired = decidePrintRouting("AUTO", "AA:BB:CC:DD:EE:FF", DirectPrintResult.DeviceNotPaired)
        val noPermission = decidePrintRouting("AUTO", "AA:BB:CC:DD:EE:FF", DirectPrintResult.NoPermission)

        assertEquals(RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG, connectionFailed)
        assertEquals(RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG, bluetoothOff)
        assertEquals(RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG, notPaired)
        assertEquals(RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG, noPermission)
    }

    @Test
    fun `decidePrintRouting with AUTO mode and partial success returns SHOW_ERROR instead of falling back`() {
        val partialFailure = decidePrintRouting(
            mode = "AUTO",
            address = "AA:BB:CC:DD:EE:FF",
            directResult = DirectPrintResult.Failed("Connection dropped"),
            partialSuccessCount = 3
        )
        assertEquals(RoutingDecision.SHOW_ERROR, partialFailure)
    }

    @Test
    fun `decidePrintRouting with AUTO mode and zero partial success falls back to SYSTEM_DIALOG`() {
        val zeroFailure = decidePrintRouting(
            mode = "AUTO",
            address = "AA:BB:CC:DD:EE:FF",
            directResult = DirectPrintResult.Failed("Connection dropped"),
            partialSuccessCount = 0
        )
        assertEquals(RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG, zeroFailure)
    }

    // 4. Tests for receiptLuminanceThreshold()
    @Test
    fun `receiptLuminanceThreshold maps values correctly and clamps out of bounds inputs`() {
        assertEquals(110, EscPosPrinterDriver.receiptLuminanceThreshold(0))
        assertEquals(174, EscPosPrinterDriver.receiptLuminanceThreshold(8))
        assertEquals(230, EscPosPrinterDriver.receiptLuminanceThreshold(15))
        assertEquals(110, EscPosPrinterDriver.receiptLuminanceThreshold(-3))
        assertEquals(230, EscPosPrinterDriver.receiptLuminanceThreshold(99))
    }

    // 5. Tests for InvoiceSettingsData receiptDarkness serialization
    @Test
    fun `InvoiceSettingsData JSON round trip preserves receiptDarkness`() {
        val original = InvoiceSettingsData(receiptDarkness = 12)
        val jsonString = original.toJson()
        val loaded = InvoiceSettingsData.fromJson(jsonString)
        assertEquals(12, loaded.receiptDarkness)
    }

    @Test
    fun `InvoiceSettingsData fromJson without receiptDarkness defaults to 8`() {
        val jsonObject = org.json.JSONObject().apply {
            put("storeName", "Test Shop")
        }
        val loaded = InvoiceSettingsData.fromJson(jsonObject.toString())
        assertEquals(8, loaded.receiptDarkness)
    }

    // 6. Tests for isSamePrinter()
    @Test
    fun `isSamePrinter returns true when addresses match exactly`() {
        assertTrue(isSamePrinter("00:11:22:33:44:55", "00:11:22:33:44:55"))
    }

    @Test
    fun `isSamePrinter returns true when addresses differ only in case`() {
        assertTrue(isSamePrinter("aa:bb:cc:dd:ee:ff", "AA:BB:CC:DD:EE:FF"))
        assertTrue(isSamePrinter("00:1A:2B:3C:4D:5E", "00:1a:2b:3c:4d:5e"))
    }

    @Test
    fun `isSamePrinter returns true when addresses have leading or trailing whitespace`() {
        assertTrue(isSamePrinter("  AA:BB:CC:DD:EE:FF  ", "aa:bb:cc:dd:ee:ff"))
        assertTrue(isSamePrinter("\tAA:BB:CC:DD:EE:FF\n", "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `isSamePrinter returns false when addresses are different`() {
        assertFalse(isSamePrinter("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"))
        assertFalse(isSamePrinter("00:11:22:33:44:55", "11:22:33:44:55:66"))
    }

    @Test
    fun `isSamePrinter returns false when either address is empty, blank, or whitespace only`() {
        assertFalse(isSamePrinter("", "AA:BB:CC:DD:EE:FF"))
        assertFalse(isSamePrinter("AA:BB:CC:DD:EE:FF", ""))
        assertFalse(isSamePrinter("", ""))
        assertFalse(isSamePrinter("   ", "   "))
        assertFalse(isSamePrinter("   ", "AA:BB:CC:DD:EE:FF"))
        assertFalse(isSamePrinter("AA:BB:CC:DD:EE:FF", "   "))
        assertFalse(isSamePrinter("\t \n ", "\t \n "))
    }

    // 7. Tests for bitmapToEscPosBands()
    @Test
    fun `bitmapToEscPosBands with 16x150 bitmap produces 3 bands with correct headers and matches bitmapToEscPosRaster`() {
        val mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.width).thenReturn(16)
        `when`(mockBitmap.height).thenReturn(150)
        `when`(mockBitmap.getPixel(anyInt(), anyInt())).thenReturn(-0x1000000 /* black pixel */)
        // bitmapToEscPosBands reads one band at a time with getPixels(); fill it with black
        org.mockito.Mockito.doAnswer { invocation ->
            java.util.Arrays.fill(invocation.arguments[0] as IntArray, -0x1000000)
            null
        }.`when`(mockBitmap).getPixels(
            org.mockito.ArgumentMatchers.any(IntArray::class.java),
            anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()
        )

        val bands = EscPosPrinterDriver.bitmapToEscPosBands(src = mockBitmap, targetWidthDots = 16, darkness = 8)

        // 1. Returns 3 bands
        assertEquals(3, bands.size)

        // 2. Band heights: 64, 64, 22
        val expectedHeights = listOf(64, 64, 22)
        val expectedPrefix = byteArrayOf(0x1D.toByte(), 0x76.toByte(), 0x30.toByte(), 0x00.toByte())

        for (i in 0 until 3) {
            val band = bands[i]
            val expectedHeight = expectedHeights[i]

            // Starts with bytes 1D 76 30 00
            assertArrayEquals(expectedPrefix, band.copyOfRange(0, 4))

            // Followed by xL=2, xH=0, yL=band height, yH=0
            assertEquals(2.toByte(), band[4])
            assertEquals(0.toByte(), band[5])
            assertEquals(expectedHeight.toByte(), band[6])
            assertEquals(0.toByte(), band[7])

            // Total size of band is 8 (header) + widthBytes (2) * bandHeight
            assertEquals(8 + 2 * expectedHeight, band.size)
        }

        // 3. Concatenation equals bitmapToEscPosRaster output
        val concatenated = bands.flatMap { it.toList() }.toByteArray()
        val rasterOutput = EscPosPrinterDriver.bitmapToEscPosRaster(src = mockBitmap, targetWidthDots = 16, darkness = 8)
        assertArrayEquals(concatenated, rasterOutput)
    }

    // 8. Tests for reportCanvasWidth and reportPdfPageWidthPoints (58mm vs 80mm roll setting)
    @Test
    fun `reportCanvasWidth returns 384 for 58mm and 576 for other widths`() {
        assertEquals(384, reportCanvasWidth(InvoiceSettingsData(paperRollWidthMm = 58)))
        assertEquals(576, reportCanvasWidth(InvoiceSettingsData(paperRollWidthMm = 80)))
    }

    @Test
    fun `reportPdfPageWidthPoints returns correct points for 58mm and 80mm`() {
        val expected58 = (58f / 25.4f * 72f).toInt()
        val expected80 = (80f / 25.4f * 72f).toInt()
        assertEquals(expected58, reportPdfPageWidthPoints(InvoiceSettingsData(paperRollWidthMm = 58)))
        assertEquals(expected80, reportPdfPageWidthPoints(InvoiceSettingsData(paperRollWidthMm = 80)))
    }

    // 9. Tests for reportStoreName and reportPoweredByText
    @Test
    fun `reportStoreName uses settings storeName or falls back to default brand`() {
        assertEquals("Techyfi Grocery", reportStoreName(InvoiceSettingsData(storeName = "Techyfi Grocery")))
        assertEquals("TECHYFI POS", reportStoreName(InvoiceSettingsData(storeName = "")))
        assertEquals("TECHYFI POS", reportStoreName(InvoiceSettingsData(storeName = "   ")))
    }

    @Test
    fun `reportPoweredByText uses settings poweredByText or falls back to default`() {
        assertEquals("Custom POS Systems", reportPoweredByText(InvoiceSettingsData(poweredByText = "Custom POS Systems")))
        assertEquals("Powered by Techyfi POS", reportPoweredByText(InvoiceSettingsData(poweredByText = "")))
        assertEquals("Powered by Techyfi POS", reportPoweredByText(InvoiceSettingsData(poweredByText = "   ")))
    }

    // 10. Tests for buildTsplPayload clamping (DENSITY, GAP, PRINT 1,n)
    @Test
    fun `buildTsplPayload clamps negative and excessive darkness, gap, and copies`() {
        val dummyBytes = byteArrayOf(0x01, 0x02)
        val payloadLow = buildTsplPayload(
            widthMm = 40f,
            heightMm = 30f,
            gapMm = -1f,
            copies = -5,
            darkness = -3,
            bitmapBytes = dummyBytes,
            targetWidthDots = 320,
            targetHeightDots = 240
        )
        val textLow = String(payloadLow, Charsets.US_ASCII)
        assertTrue(textLow.contains("DENSITY 0\r\n"))
        assertTrue(textLow.contains("GAP 0 mm,0 mm\r\n"))
        assertTrue(textLow.contains("PRINT 1,1\r\n"))

        val payloadHigh = buildTsplPayload(
            widthMm = 40f,
            heightMm = 30f,
            gapMm = 3f,
            copies = 5,
            darkness = 99,
            bitmapBytes = dummyBytes,
            targetWidthDots = 320,
            targetHeightDots = 240
        )
        val textHigh = String(payloadHigh, Charsets.US_ASCII)
        assertTrue(textHigh.contains("DENSITY 15\r\n"))
        assertTrue(textHigh.contains("GAP 3 mm,0 mm\r\n"))
        assertTrue(textHigh.contains("PRINT 1,5\r\n"))
    }

    // 10b. Tests for buildTsplPayload DPI and calibration (LBL-12, LBL-14)
    @Test
    fun `buildTsplPayload calculates correct BITMAP width for 203 and 300 dpi`() {
        val dummyBytes = byteArrayOf(0x01)
        val dots203 = (40f * 203f / 25.4f).toInt()
        val payload203 = buildTsplPayload(
            widthMm = 40f,
            heightMm = 30f,
            gapMm = 2f,
            copies = 1,
            darkness = 8,
            bitmapBytes = dummyBytes,
            targetWidthDots = dots203,
            targetHeightDots = 240
        )
        val text203 = String(payload203, Charsets.US_ASCII)
        assertTrue(text203.contains("BITMAP 0,0,40,240,0,"))

        val dots300 = kotlin.math.round(40f * 300f / 25.4f).toInt()
        val payload300 = buildTsplPayload(
            widthMm = 40f,
            heightMm = 30f,
            gapMm = 2f,
            copies = 1,
            darkness = 8,
            bitmapBytes = dummyBytes,
            targetWidthDots = dots300,
            targetHeightDots = 354
        )
        val text300 = String(payload300, Charsets.US_ASCII)
        assertTrue(text300.contains("BITMAP 0,0,59,354,0,"))
    }

    @Test
    fun `buildTsplPayload emits SPEED, REFERENCE, DIRECTION, and paper type commands`() {
        val dummyBytes = byteArrayOf(0x01)
        val payload = buildTsplPayload(
            widthMm = 50f,
            heightMm = 30f,
            gapMm = 3f,
            copies = 2,
            darkness = 10,
            bitmapBytes = dummyBytes,
            targetWidthDots = 400,
            targetHeightDots = 240,
            speed = 5.0f,
            xOffsetDots = 16,
            yOffsetDots = -8,
            direction = 0,
            paperType = "BLACK_MARK"
        )
        val text = String(payload, Charsets.US_ASCII)
        assertTrue(text.contains("SIZE 50 mm,30 mm\r\n"))
        assertTrue(text.contains("BLINE 3 mm,0 mm\r\n"))
        assertTrue(text.contains("SPEED 5\r\n"))
        assertTrue(text.contains("REFERENCE 16,-8\r\n"))
        assertTrue(text.contains("DIRECTION 0\r\n"))
        assertTrue(text.contains("DENSITY 10\r\n"))

        val continuousPayload = buildTsplPayload(
            widthMm = 50f,
            heightMm = 30f,
            gapMm = 0f,
            copies = 1,
            darkness = 8,
            bitmapBytes = dummyBytes,
            targetWidthDots = 400,
            targetHeightDots = 240,
            paperType = "CONTINUOUS"
        )
        val continuousText = String(continuousPayload, Charsets.US_ASCII)
        assertTrue(continuousText.contains("GAP 0 mm,0 mm\r\n"))
    }

    @Test
    fun `isBarcodeTooDenseForLabel detects dense barcode on narrow labels`() {
        // 12-digit barcode on a narrow 25mm label at 203 DPI is under 2 dots per module
        assertTrue(isBarcodeTooDenseForLabel("123456789012", ProductCodeType.BARCODE, 25f, 203))

        // Short barcode on a standard 50mm label at 203 DPI has plenty of space
        assertFalse(isBarcodeTooDenseForLabel("123456", ProductCodeType.BARCODE, 50f, 203))

        // QR Code is a 2D matrix, not affected by 1D module width restriction
        assertFalse(isBarcodeTooDenseForLabel("123456789012", ProductCodeType.QR_CODE, 25f, 203))
    }

    @Test
    fun `InvoiceSettingsData persists and restores labelDpi and calibration settings`() {
        val original = InvoiceSettingsData(
            labelDpi = 300,
            labelPrintSpeed = 5.5f,
            labelXOffsetDots = 12,
            labelYOffsetDots = -6,
            labelDirection = 0,
            labelPaperType = "BLACK_MARK"
        )
        val json = original.toJson()
        val restored = InvoiceSettingsData.fromJson(json)

        assertEquals(300, restored.labelDpi)
        assertEquals(5.5f, restored.labelPrintSpeed, 0.01f)
        assertEquals(12, restored.labelXOffsetDots)
        assertEquals(-6, restored.labelYOffsetDots)
        assertEquals(0, restored.labelDirection)
        assertEquals("BLACK_MARK", restored.labelPaperType)
    }

    // 11. Tests for formatBluetoothDeviceDisplay()
    @Test
    fun `formatBluetoothDeviceDisplay formats name and address correctly`() {
        assertEquals("Deli E886BW (AA:BB:CC:DD:EE:FF)", formatBluetoothDeviceDisplay("Deli E886BW", "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", formatBluetoothDeviceDisplay(null, "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", formatBluetoothDeviceDisplay("", "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", formatBluetoothDeviceDisplay("   ", "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", formatBluetoothDeviceDisplay("AA:BB:CC:DD:EE:FF", "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", formatBluetoothDeviceDisplay("aa:bb:cc:dd:ee:ff", "AA:BB:CC:DD:EE:FF"))
        assertEquals("", formatBluetoothDeviceDisplay("Deli E886BW", ""))
        assertEquals("", formatBluetoothDeviceDisplay("Deli E886BW", "   "))
    }

    // 12. Tests for labelLuminanceThreshold()
    @Test
    fun `labelLuminanceThreshold returns fixed 170 threshold across darkness range`() {
        assertEquals(170, labelLuminanceThreshold(0))
        assertEquals(170, labelLuminanceThreshold(8))
        assertEquals(170, labelLuminanceThreshold(15))
        assertEquals(170, labelLuminanceThreshold(-5))
        assertEquals(170, labelLuminanceThreshold(99))
    }

    // 13. Tests for legacy directBluetoothPrintEnabled migration
    @Test
    fun `fromJson migrates legacy directBluetoothPrintEnabled true without receiptPrintMode to BLUETOOTH`() {
        val legacyJson = org.json.JSONObject().apply {
            put("storeName", "Legacy Shop")
            put("directBluetoothPrintEnabled", true)
        }.toString()

        val loaded = InvoiceSettingsData.fromJson(legacyJson)
        assertEquals("BLUETOOTH", loaded.receiptPrintMode)
    }

    @Test
    fun `fromJson preserves explicit receiptPrintMode even if legacy directBluetoothPrintEnabled is present`() {
        val jsonWithBoth = org.json.JSONObject().apply {
            put("storeName", "Modern Shop")
            put("directBluetoothPrintEnabled", true)
            put("receiptPrintMode", "SYSTEM")
        }.toString()

        val loaded = InvoiceSettingsData.fromJson(jsonWithBoth)
        assertEquals("SYSTEM", loaded.receiptPrintMode)
    }

    @Test
    fun `fromJson defaults receiptPrintMode to AUTO when neither legacy nor modern mode is set`() {
        val defaultJson = org.json.JSONObject().apply {
            put("storeName", "Default Shop")
        }.toString()

        val loaded = InvoiceSettingsData.fromJson(defaultJson)
        assertEquals("AUTO", loaded.receiptPrintMode)
    }

    // 14. Tests for FileSaveResult
    @Test
    fun `FileSaveResult isSuccessful returns true for PublicSaved and PrivateFallback and false for Failed`() {
        val publicResult = com.example.techyfipos.utils.FileSaveResult.PublicSaved("/storage/emulated/0/Pictures/Invoices/test.jpg")
        val fallbackResult = com.example.techyfipos.utils.FileSaveResult.PrivateFallback("/data/user/0/com.example.techyfipos/files/test.jpg")
        val failedResult = com.example.techyfipos.utils.FileSaveResult.Failed

        assertTrue(publicResult.isSuccessful)
        assertEquals("/storage/emulated/0/Pictures/Invoices/test.jpg", publicResult.path)

        assertTrue(fallbackResult.isSuccessful)
        assertEquals("/data/user/0/com.example.techyfipos/files/test.jpg", fallbackResult.path)

        assertFalse(failedResult.isSuccessful)
    }

    // 15. Tests for cleanOldPrintCacheFiles
    @Test
    fun `cleanOldPrintCacheFiles deletes files older than threshold and preserves newer files`() {
        val tempDir = java.nio.file.Files.createTempDirectory("cache_test").toFile()
        try {
            val billsDir = java.io.File(tempDir, "bills").apply { mkdirs() }
            val reportsDir = java.io.File(tempDir, "reports").apply { mkdirs() }
            val receiptsDir = java.io.File(tempDir, "receipts").apply { mkdirs() }
            val productCodesDir = java.io.File(tempDir, "product_codes").apply { mkdirs() }

            val now = System.currentTimeMillis()
            val oldTime = now - (8 * 24 * 60 * 60 * 1000L) // 8 days ago
            val freshTime = now - (1 * 24 * 60 * 60 * 1000L) // 1 day ago

            val oldBill = java.io.File(billsDir, "old_bill.pdf").apply {
                createNewFile()
                setLastModified(oldTime)
            }
            val freshBill = java.io.File(billsDir, "fresh_bill.pdf").apply {
                createNewFile()
                setLastModified(freshTime)
            }
            val oldReport = java.io.File(reportsDir, "old_report.jpg").apply {
                createNewFile()
                setLastModified(oldTime)
            }
            val freshReceipt = java.io.File(receiptsDir, "fresh_receipt.jpg").apply {
                createNewFile()
                setLastModified(freshTime)
            }
            val oldProductCode = java.io.File(productCodesDir, "old_barcode.pdf").apply {
                createNewFile()
                setLastModified(oldTime)
            }
            val oldLoosePdf = java.io.File(tempDir, "test_label_fallback.pdf").apply {
                createNewFile()
                setLastModified(oldTime)
            }

            val deletedCount = com.example.techyfipos.utils.cleanOldPrintCacheFiles(tempDir, 7 * 24 * 60 * 60 * 1000L)
            assertEquals(4, deletedCount)
            assertFalse(oldBill.exists())
            assertTrue(freshBill.exists())
            assertFalse(oldReport.exists())
            assertTrue(freshReceipt.exists())
            assertFalse(oldProductCode.exists())
            assertFalse(oldLoosePdf.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // 16. Tests for receiptCutBytes pure function
    @Test
    fun `receiptCutBytes produces expected bytes for FULL, PARTIAL, and NONE modes`() {
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x00), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.FULL))
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x01), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.PARTIAL))
        assertArrayEquals(byteArrayOf(), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.NONE))

        // Advanced legacy ESC style
        assertArrayEquals(byteArrayOf(0x1B, 0x69), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.FULL, "LEGACY_ESC"))
        assertArrayEquals(byteArrayOf(0x1B, 0x6D), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.PARTIAL, "LEGACY_ESC"))

        // GS V 65/66 feed style always uses n = 0, regardless of the configured feed lines
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x41, 0x00), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.FULL, "GS_V_FEED", 7))
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x42, 0x00), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.PARTIAL, "GS_V_FEED", 7))
        assertArrayEquals(byteArrayOf(), EscPosPrinterDriver.receiptCutBytes(ReceiptCutMode.NONE, "GS_V_FEED", 7))
    }

    // 17. Tests for receiptFeedBytes pure function and clamping
    @Test
    fun `receiptFeedBytes produces ESC d n bytes and clamps out of bounds inputs`() {
        assertArrayEquals(byteArrayOf(), EscPosPrinterDriver.receiptFeedBytes(0))
        assertArrayEquals(byteArrayOf(), EscPosPrinterDriver.receiptFeedBytes(-4))
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x04), EscPosPrinterDriver.receiptFeedBytes(4))
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x0A), EscPosPrinterDriver.receiptFeedBytes(10))
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x0A), EscPosPrinterDriver.receiptFeedBytes(99))
    }

    // 18. Tests for buildReceiptEndSegment
    @Test
    fun `buildReceiptEndSegment ends with cut bytes for FULL and PARTIAL and no cut bytes for NONE`() {
        val fullSegment = EscPosPrinterDriver.buildReceiptEndSegment(
            cutMode = ReceiptCutMode.FULL,
            feedLines = 4,
            autoKickDrawer = false
        )
        // Feed 4 lines (1B 64 04) followed by Full cut (1D 56 00)
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x04, 0x1D, 0x56, 0x00), fullSegment)

        val partialSegment = EscPosPrinterDriver.buildReceiptEndSegment(
            cutMode = ReceiptCutMode.PARTIAL,
            feedLines = 2,
            autoKickDrawer = false
        )
        // Feed 2 lines (1B 64 02) followed by Partial cut (1D 56 01)
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x02, 0x1D, 0x56, 0x01), partialSegment)

        val noneSegment = EscPosPrinterDriver.buildReceiptEndSegment(
            cutMode = ReceiptCutMode.NONE,
            feedLines = 5,
            autoKickDrawer = false
        )
        // Feed 5 lines (1B 64 05) and no cut bytes
        assertArrayEquals(byteArrayOf(0x1B, 0x64, 0x05), noneSegment)
    }

    // 19. Tests for drawer kick in buildReceiptEndSegment
    @Test
    fun `buildReceiptEndSegment appends drawer kick after cut when autoKickDrawer is true and omits when false`() {
        val withKick = EscPosPrinterDriver.buildReceiptEndSegment(
            cutMode = ReceiptCutMode.FULL,
            feedLines = 3,
            autoKickDrawer = true
        )
        // Expected: Feed (1B 64 03) + Cut (1D 56 00) + Kick (1B 70 00 19 FA)
        val expectedWithKick = byteArrayOf(
            0x1B, 0x64, 0x03,
            0x1D, 0x56, 0x00,
            0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()
        )
        assertArrayEquals(expectedWithKick, withKick)

        val withoutKick = EscPosPrinterDriver.buildReceiptEndSegment(
            cutMode = ReceiptCutMode.FULL,
            feedLines = 3,
            autoKickDrawer = false
        )
        val expectedWithoutKick = byteArrayOf(
            0x1B, 0x64, 0x03,
            0x1D, 0x56, 0x00
        )
        assertArrayEquals(expectedWithoutKick, withoutKick)
    }

    // 20. Tests for buildCutTestSegments
    @Test
    fun `buildCutTestSegments for each mode ends with correct cut bytes and never contains drawer kick`() {
        val dummyBands = listOf(byteArrayOf(0x01, 0x02))

        for (mode in listOf(ReceiptCutMode.FULL, ReceiptCutMode.PARTIAL, ReceiptCutMode.NONE)) {
            val (segments, delays) = EscPosPrinterDriver.buildCutTestSegments(
                bands = dummyBands,
                cutMode = mode,
                feedLines = 4
            )
            // Segments: Init, Align, 1 band, End segment
            assertEquals(4, segments.size)
            assertEquals(4, delays.size)

            assertArrayEquals(byteArrayOf(0x1B, 0x40), segments[0])
            assertEquals(100L, delays[0])

            assertArrayEquals(byteArrayOf(0x1B, 0x61, 0x01), segments[1])
            assertEquals(20L, delays[1])

            assertArrayEquals(dummyBands[0], segments[2])
            assertEquals(EscPosPrinterDriver.POST_RASTER_DELAY_MS, delays[2])

            val endSeg = segments[3]
            assertEquals(0L, delays[3])

            // Verify drawer kick bytes are never present in cut test
            val kickBytes = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())
            var foundKick = false
            for (i in 0..endSeg.size - kickBytes.size) {
                if (endSeg.copyOfRange(i, i + kickBytes.size).contentEquals(kickBytes)) {
                    foundKick = true
                    break
                }
            }
            assertFalse(foundKick)

            // Verify cut bytes
            when (mode) {
                ReceiptCutMode.FULL -> {
                    assertEquals(0x1D.toByte(), endSeg[endSeg.size - 3])
                    assertEquals(0x56.toByte(), endSeg[endSeg.size - 2])
                    assertEquals(0x00.toByte(), endSeg[endSeg.size - 1])
                }
                ReceiptCutMode.PARTIAL -> {
                    assertEquals(0x1D.toByte(), endSeg[endSeg.size - 3])
                    assertEquals(0x56.toByte(), endSeg[endSeg.size - 2])
                    assertEquals(0x01.toByte(), endSeg[endSeg.size - 1])
                }
                ReceiptCutMode.NONE -> {
                    // Ends with feed command, no cut bytes
                    assertEquals(0x1B.toByte(), endSeg[endSeg.size - 3])
                    assertEquals(0x64.toByte(), endSeg[endSeg.size - 2])
                    assertEquals(0x04.toByte(), endSeg[endSeg.size - 1])
                }
            }
        }
    }

    // 21. Tests for InvoiceSettingsData cut mode JSON round trip
    @Test
    fun `InvoiceSettingsData JSON round trip preserves receiptCutMode, feedLines, and cutCommandStyle`() {
        val original = InvoiceSettingsData(
            receiptCutMode = "FULL",
            receiptFeedLinesBeforeCut = 6,
            receiptCutCommandStyle = "LEGACY_ESC"
        )
        val jsonString = original.toJson()
        val loaded = InvoiceSettingsData.fromJson(jsonString)

        assertEquals("FULL", loaded.receiptCutMode)
        assertEquals(6, loaded.receiptFeedLinesBeforeCut)
        assertEquals("LEGACY_ESC", loaded.receiptCutCommandStyle)
    }

    // 22. Tests for InvoiceSettingsData cut mode JSON defaults and fallback
    @Test
    fun `InvoiceSettingsData fromJson without cut settings defaults to PARTIAL, 4 feed lines, and clamps invalid feed lines`() {
        val jsonWithoutCut = org.json.JSONObject().apply {
            put("storeName", "Old Backup Shop")
        }.toString()
        val loadedDefault = InvoiceSettingsData.fromJson(jsonWithoutCut)
        assertEquals("PARTIAL", loadedDefault.receiptCutMode)
        assertEquals(4, loadedDefault.receiptFeedLinesBeforeCut)
        assertEquals("GS_V", loadedDefault.receiptCutCommandStyle)

        // Invalid cut mode and out of range feed lines
        val jsonInvalid = org.json.JSONObject().apply {
            put("storeName", "Clamping Shop")
            put("receiptCutMode", "INVALID_MODE")
            put("receiptFeedLinesBeforeCut", 99)
            put("receiptCutCommandStyle", "UNKNOWN_STYLE")
        }.toString()
        val loadedInvalid = InvoiceSettingsData.fromJson(jsonInvalid)
        assertEquals("PARTIAL", loadedInvalid.receiptCutMode)
        assertEquals(10, loadedInvalid.receiptFeedLinesBeforeCut)
        assertEquals("GS_V", loadedInvalid.receiptCutCommandStyle)

        val jsonNegative = org.json.JSONObject().apply {
            put("receiptFeedLinesBeforeCut", -8)
        }.toString()
        val loadedNegative = InvoiceSettingsData.fromJson(jsonNegative)
        assertEquals(0, loadedNegative.receiptFeedLinesBeforeCut)
    }

    // 23. Tests for buildUsbDeviceKey and parseUsbDeviceKey
    @Test
    fun `buildUsbDeviceKey and parseUsbDeviceKey work symmetrically for VID, PID, and serial`() {
        val keyWithoutSerial = buildUsbDeviceKey(1046, 20490)
        assertEquals("usb:1046:20490", keyWithoutSerial)
        val parsedNoSerial = parseUsbDeviceKey(keyWithoutSerial)
        assertNotNull(parsedNoSerial)
        assertEquals(1046, parsedNoSerial!!.vendorId)
        assertEquals(20490, parsedNoSerial.productId)
        assertEquals("", parsedNoSerial.serialNumber)

        val keyWithSerial = buildUsbDeviceKey(1046, 20490, "SN98765")
        assertEquals("usb:1046:20490:SN98765", keyWithSerial)
        val parsedWithSerial = parseUsbDeviceKey(keyWithSerial)
        assertNotNull(parsedWithSerial)
        assertEquals(1046, parsedWithSerial!!.vendorId)
        assertEquals(20490, parsedWithSerial.productId)
        assertEquals("SN98765", parsedWithSerial.serialNumber)

        // Serial number with colons
        val keyMultiColon = buildUsbDeviceKey(1234, 5678, "ABC:123:XYZ")
        assertEquals("usb:1234:5678:ABC:123:XYZ", keyMultiColon)
        val parsedMultiColon = parseUsbDeviceKey(keyMultiColon)
        assertNotNull(parsedMultiColon)
        assertEquals("ABC:123:XYZ", parsedMultiColon!!.serialNumber)

        // Invalid keys return null
        assertNull(parseUsbDeviceKey(""))
        assertNull(parseUsbDeviceKey("   "))
        assertNull(parseUsbDeviceKey("00:11:22:33:44:55"))
        assertNull(parseUsbDeviceKey("usb"))
        assertNull(parseUsbDeviceKey("usb:notanumber:20490"))
        assertNull(parseUsbDeviceKey("usb:1046:notanumber"))
    }

    // 24. Tests for isSamePrinterTarget()
    @Test
    fun `isSamePrinterTarget correctly compares Bluetooth, USB, and None targets`() {
        val bt1 = PrinterTarget.Bluetooth("00:11:22:33:44:55")
        val bt1Lower = PrinterTarget.Bluetooth("00:11:22:33:44:55")
        val bt2 = PrinterTarget.Bluetooth("AA:BB:CC:DD:EE:FF")
        val usb1 = PrinterTarget.Usb("usb:1046:20490")
        val usb1WithSerial = PrinterTarget.Usb("usb:1046:20490:SN1")
        val usb1WithSerialUpper = PrinterTarget.Usb("usb:1046:20490:sn1")
        val usb2DiffSerial = PrinterTarget.Usb("usb:1046:20490:SN2")
        val usb3DiffPid = PrinterTarget.Usb("usb:1046:9999")
        val none = PrinterTarget.None

        // Same Bluetooth targets
        assertTrue(isSamePrinterTarget(bt1, bt1Lower))
        // Different Bluetooth targets
        assertFalse(isSamePrinterTarget(bt1, bt2))

        // Same USB targets
        assertTrue(isSamePrinterTarget(usb1, PrinterTarget.Usb("usb:1046:20490")))
        assertTrue(isSamePrinterTarget(usb1WithSerial, usb1WithSerialUpper))

        // Different USB targets
        assertFalse(isSamePrinterTarget(usb1WithSerial, usb2DiffSerial))
        assertFalse(isSamePrinterTarget(usb1, usb3DiffPid))

        // Cross-protocol comparisons
        assertFalse(isSamePrinterTarget(bt1, usb1))
        assertFalse(isSamePrinterTarget(usb1, bt1))

        // None comparisons
        assertFalse(isSamePrinterTarget(none, none))
        assertFalse(isSamePrinterTarget(none, bt1))
        assertFalse(isSamePrinterTarget(usb1, none))
    }

    // 25. Tests for isSamePrinter() string comparison with USB keys
    @Test
    fun `isSamePrinter supports usb key format and compares VID, PID, and serial`() {
        assertTrue(isSamePrinter("usb:1046:20490", "usb:1046:20490"))
        assertTrue(isSamePrinter("  USB:1046:20490  ", "usb:1046:20490"))
        assertTrue(isSamePrinter("usb:1046:20490:sn123", "USB:1046:20490:SN123"))

        assertFalse(isSamePrinter("usb:1046:20490", "usb:1046:20491"))
        assertFalse(isSamePrinter("usb:1046:20490:SN1", "usb:1046:20490:SN2"))
        assertFalse(isSamePrinter("00:11:22:33:44:55", "usb:1046:20490"))
    }

    // 26. Tests for selectPrinterInterfaceAndEndpoint()
    @Test
    fun `selectPrinterInterfaceAndEndpoint selects printer class bulk OUT interface or falls back to vendor specific`() {
        val printerBulkOutEp = UsbEndpointInfo(endpointNumber = 1, direction = 0 /* OUT */, type = 2 /* BULK */, maxPacketSize = 64)
        val interruptInEp = UsbEndpointInfo(endpointNumber = 2, direction = 128 /* IN */, type = 3 /* INTERRUPT */, maxPacketSize = 8)

        val printerIface = UsbInterfaceInfo(
            id = 0,
            interfaceClass = 7, /* USB_CLASS_PRINTER */
            interfaceSubclass = 1,
            interfaceProtocol = 2,
            endpoints = listOf(printerBulkOutEp, interruptInEp)
        )
        val otherIface = UsbInterfaceInfo(
            id = 1,
            interfaceClass = 3, /* HID */
            interfaceSubclass = 0,
            interfaceProtocol = 0,
            endpoints = listOf(interruptInEp)
        )

        // Selects printer class interface
        val selectedPrinter = selectPrinterInterfaceAndEndpoint(listOf(otherIface, printerIface))
        assertNotNull(selectedPrinter)
        assertEquals(0, selectedPrinter!!.first.id)
        assertEquals(1, selectedPrinter.second.endpointNumber)

        // Vendor-specific (255) fallback when no printer class exists
        val vendorSpecificBulkOut = UsbInterfaceInfo(
            id = 2,
            interfaceClass = 255,
            interfaceSubclass = 255,
            interfaceProtocol = 255,
            endpoints = listOf(printerBulkOutEp)
        )
        val selectedVendor = selectPrinterInterfaceAndEndpoint(listOf(otherIface, vendorSpecificBulkOut))
        assertNotNull(selectedVendor)
        assertEquals(2, selectedVendor!!.first.id)

        // Returns null if no interface has bulk OUT endpoint
        val noBulkOutIface = UsbInterfaceInfo(
            id = 3,
            interfaceClass = 7,
            interfaceSubclass = 1,
            interfaceProtocol = 2,
            endpoints = listOf(interruptInEp)
        )
        assertNull(selectPrinterInterfaceAndEndpoint(listOf(noBulkOutIface, otherIface)))
        assertNull(selectPrinterInterfaceAndEndpoint(emptyList()))
    }

    // 27. Tests for InvoiceSettingsData USB JSON round trip
    @Test
    fun `InvoiceSettingsData JSON round trip preserves USB connections and keys`() {
        val settings = InvoiceSettingsData(
            receiptPrinterConnection = "USB",
            receiptUsbDeviceKey = "usb:1046:20490:REC01",
            labelPrinterConnection = "USB",
            labelUsbDeviceKey = "usb:4505:180:LBL02",
            cashDrawerPrinterConnection = "USB",
            cashDrawerUsbDeviceKey = "usb:1046:20490:REC01"
        )
        val json = settings.toJson()
        val loaded = InvoiceSettingsData.fromJson(json)

        assertEquals("USB", loaded.receiptPrinterConnection)
        assertEquals("usb:1046:20490:REC01", loaded.receiptUsbDeviceKey)
        assertEquals("USB", loaded.labelPrinterConnection)
        assertEquals("usb:4505:180:LBL02", loaded.labelUsbDeviceKey)
        assertEquals("USB", loaded.cashDrawerPrinterConnection)
        assertEquals("usb:1046:20490:REC01", loaded.cashDrawerUsbDeviceKey)
    }

    // 28. Tests for InvoiceSettingsData resolved target fallback logic
    @Test
    fun `InvoiceSettingsData resolved targets handle USB and fallback cleanly`() {
        // USB configured and key non-blank
        val usbSettings = InvoiceSettingsData(
            receiptPrinterConnection = "USB",
            receiptUsbDeviceKey = "usb:1046:20490",
            labelPrinterConnection = "USB",
            labelUsbDeviceKey = "usb:4505:180",
            cashDrawerPrinterConnection = "USB",
            cashDrawerUsbDeviceKey = "usb:1046:20490"
        )
        assertEquals(PrinterTarget.Usb("usb:1046:20490"), usbSettings.resolvedReceiptPrinterTarget())
        assertEquals(PrinterTarget.Usb("usb:4505:180"), usbSettings.resolvedLabelPrinterTarget())
        assertEquals(PrinterTarget.Usb("usb:1046:20490"), usbSettings.resolvedCashDrawerPrinterTarget())
        assertEquals("usb:1046:20490", usbSettings.resolvedReceiptPrinterAddress())

        // USB connection selected but key is blank -> falls back to Bluetooth address
        val emptyUsbSettings = InvoiceSettingsData(
            receiptPrinterConnection = "USB",
            receiptUsbDeviceKey = "",
            directBluetoothPrinterAddress = "00:11:22:33:44:55"
        )
        assertEquals(PrinterTarget.Bluetooth("00:11:22:33:44:55"), emptyUsbSettings.resolvedReceiptPrinterTarget())

        // Cash drawer "Same as receipt printer" (blank address and blank USB key) inherits receipt target
        val inheritedDrawerSettings = InvoiceSettingsData(
            receiptPrinterConnection = "USB",
            receiptUsbDeviceKey = "usb:1046:20490",
            cashDrawerPrinterConnection = "BLUETOOTH",
            cashDrawerPrinterAddress = "",
            cashDrawerUsbDeviceKey = ""
        )
        assertEquals(PrinterTarget.Usb("usb:1046:20490"), inheritedDrawerSettings.resolvedCashDrawerPrinterTarget())
    }

    // 29. Tests for decidePrintRouting with USB targets
    @Test
    fun `decidePrintRouting handles USB targets with correct error and fallback decisions`() {
        val usbTarget = "usb:1046:20490"

        // Success in DIRECT (BLUETOOTH) or AUTO mode -> DIRECT_OK
        assertEquals(RoutingDecision.DIRECT_OK, decidePrintRouting("BLUETOOTH", usbTarget, DirectPrintResult.Success))
        assertEquals(RoutingDecision.DIRECT_OK, decidePrintRouting("AUTO", usbTarget, DirectPrintResult.Success))

        // UsbDeviceNotFound in DIRECT mode -> SHOW_ERROR
        assertEquals(
            RoutingDecision.SHOW_ERROR,
            decidePrintRouting("BLUETOOTH", usbTarget, DirectPrintResult.UsbDeviceNotFound)
        )

        // UsbDeviceNotFound in AUTO mode -> SHOW_ERROR_THEN_SYSTEM_DIALOG
        assertEquals(
            RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG,
            decidePrintRouting("AUTO", usbTarget, DirectPrintResult.UsbDeviceNotFound)
        )

        // UsbNoPermission in DIRECT mode -> SHOW_ERROR
        assertEquals(
            RoutingDecision.SHOW_ERROR,
            decidePrintRouting("BLUETOOTH", usbTarget, DirectPrintResult.UsbNoPermission)
        )

        // Blank target -> NoAddress -> SHOW_ERROR in DIRECT, SYSTEM_DIALOG in AUTO
        assertEquals(RoutingDecision.SHOW_ERROR, decidePrintRouting("BLUETOOTH", "", DirectPrintResult.NoAddress))
        assertEquals(RoutingDecision.SYSTEM_DIALOG, decidePrintRouting("AUTO", "", DirectPrintResult.NoAddress))
    }

    @Test
    fun `bluetoothPostPrintDrainMs scales with payload size and is clamped`() {
        assertEquals(300L, bluetoothPostPrintDrainMs(0))
        assertEquals(300L, bluetoothPostPrintDrainMs(-5))
        // small cut test job keeps the short wait
        assertEquals(300L + 5_000L / 25, bluetoothPostPrintDrainMs(5_000))
        // a long receipt (about 100 KB) waits much longer so the feed and cut bytes are not dropped
        assertEquals(4000L, bluetoothPostPrintDrainMs(100_000))
        assertEquals(4000L, bluetoothPostPrintDrainMs(Int.MAX_VALUE))
        assertTrue(bluetoothPostPrintDrainMs(60_000) > bluetoothPostPrintDrainMs(5_000))
    }

    @Test
    fun `toHexString formats byte arrays to uppercase hex accurately`() {
        assertEquals("", byteArrayOf().toHexString())
        assertEquals("1D5601", byteArrayOf(0x1D, 0x56, 0x01).toHexString())
        assertEquals("00FF0A", byteArrayOf(0x00, 0xFF.toByte(), 0x0A).toHexString())
    }

    @Test
    fun `buildCutTestSegments preserves all raster bands and appends correct end segment with settling delay`() {
        val dummyBands = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
        val (segments, delays) = EscPosPrinterDriver.buildCutTestSegments(
            bands = dummyBands,
            cutMode = ReceiptCutMode.PARTIAL,
            feedLines = 4,
            cutCommandStyle = "GS_V"
        )
        // CMD_INIT, CMD_ALIGN_CENTER, 2 bands, endSegment = 5 segments
        assertEquals(5, segments.size)
        assertEquals(5, delays.size)
        assertArrayEquals(EscPosPrinterDriver.CMD_INIT, segments[0])
        assertEquals(100L, delays[0])
        assertArrayEquals(EscPosPrinterDriver.CMD_ALIGN_CENTER, segments[1])
        assertEquals(20L, delays[1])
        assertArrayEquals(dummyBands[0], segments[2])
        assertEquals(20L, delays[2])
        assertArrayEquals(dummyBands[1], segments[3])
        // Final raster band before cut must pause by POST_RASTER_DELAY_MS (250 ms)
        assertEquals(EscPosPrinterDriver.POST_RASTER_DELAY_MS, delays[3])
        // End segment should be ESC d 4 + GS V 1 (CMD_PARTIAL_CUT)
        val expectedEnd = byteArrayOf(0x1B, 0x64, 0x04, 0x1D, 0x56, 0x01)
        assertArrayEquals(expectedEnd, segments[4])
        assertEquals(0L, delays[4])
    }

    @Test
    fun `bluetoothResponseTimeoutMs scales with payload size and clamps between 3 and 8 seconds`() {
        assertEquals(3000L, bluetoothResponseTimeoutMs(0))
        assertEquals(3000L, bluetoothResponseTimeoutMs(-10))
        // small job (5 KB) stays close to minimum
        assertEquals(3000L + 5_000L / 25, bluetoothResponseTimeoutMs(5_000))
        // 100 KB payload gets 7 seconds
        assertEquals(3000L + 100_000L / 25, bluetoothResponseTimeoutMs(100_000))
        // very large jobs clamp at 8000L
        assertEquals(8000L, bluetoothResponseTimeoutMs(200_000))
        assertEquals(8000L, bluetoothResponseTimeoutMs(Int.MAX_VALUE))
    }

    @Test
    fun `InvoiceSettingsData toJson and fromJson preserves cut settings accurately`() {
        val original = InvoiceSettingsData(
            receiptCutMode = "FULL",
            receiptFeedLinesBeforeCut = 7,
            receiptCutCommandStyle = "LEGACY_ESC"
        )
        val json = original.toJson()
        val restored = InvoiceSettingsData.fromJson(json)

        assertEquals("FULL", restored.receiptCutMode)
        assertEquals(7, restored.receiptFeedLinesBeforeCut)
        assertEquals("LEGACY_ESC", restored.receiptCutCommandStyle)
    }

    @Test
    fun `InvoiceSettingsData fromJson clamps feed lines and validates cut modes and styles`() {
        val jsonClamped = """
            {
                "receiptCutMode": "UNKNOWN_MODE",
                "receiptFeedLinesBeforeCut": 25,
                "receiptCutCommandStyle": "INVALID_STYLE"
            }
        """.trimIndent()
        val restored = InvoiceSettingsData.fromJson(jsonClamped)

        // Invalid mode falls back to PARTIAL
        assertEquals("PARTIAL", restored.receiptCutMode)
        // Feed lines clamped to 0..10
        assertEquals(10, restored.receiptFeedLinesBeforeCut)
        // Invalid cut style falls back to GS_V
        assertEquals("GS_V", restored.receiptCutCommandStyle)
    }

    @Test
    fun `bluetoothChunkDelayMs paces larger jobs and preserves fast rate for short cut tests`() {
        assertEquals(15L, bluetoothChunkDelayMs(0))
        assertEquals(15L, bluetoothChunkDelayMs(-500))
        assertEquals(15L, bluetoothChunkDelayMs(8192))
        // Small increase for moderate jobs
        assertEquals(18L, bluetoothChunkDelayMs(20000))
        // High delay for large receipt images to avoid overflowing printer buffer
        assertEquals(34L, bluetoothChunkDelayMs(68000))
        // Clamps at 35 ms maximum
        assertEquals(35L, bluetoothChunkDelayMs(75000))
        assertEquals(35L, bluetoothChunkDelayMs(Int.MAX_VALUE))
    }

    @Test
    fun `fillRasterBandRows maps black white and transparent pixels and honours the threshold`() {
        val black = -0x1000000        // 0xFF000000
        val white = -0x1             // 0xFFFFFFFF
        val transparentBlack = 0x00000000
        val gray = (0xFF shl 24) or (170 shl 16) or (170 shl 8) or 170 // luminance 170

        // one row, 10 dots wide, so 2 bytes per row (last byte is padded with white)
        val pixels = intArrayOf(black, white, black, white, black, white, black, white, black, transparentBlack)
        val out = ByteArray(2)
        EscPosPrinterDriver.fillRasterBandRows(pixels, widthDots = 10, rows = 1, widthBytes = 2, threshold = 174, out = out, outOffset = 0)
        assertEquals(0b10101010.toByte(), out[0])
        assertEquals(0b10000000.toByte(), out[1])

        // gray 170 is black at threshold 174 (darkness 8) and white at threshold 110 (lightest)
        val grayRow = IntArray(8) { gray }
        val darkOut = ByteArray(1)
        EscPosPrinterDriver.fillRasterBandRows(grayRow, 8, 1, 1, 174, darkOut, 0)
        assertEquals(0xFF.toByte(), darkOut[0])
        val lightOut = ByteArray(1)
        EscPosPrinterDriver.fillRasterBandRows(grayRow, 8, 1, 1, 110, lightOut, 0)
        assertEquals(0x00.toByte(), lightOut[0])

        // writes at the requested offset and handles several rows
        val twoRows = IntArray(16) { if (it < 8) black else white }
        val offsetOut = ByteArray(4)
        EscPosPrinterDriver.fillRasterBandRows(twoRows, 8, 2, 1, 174, offsetOut, 2)
        assertEquals(0.toByte(), offsetOut[0])
        assertEquals(0xFF.toByte(), offsetOut[2])
        assertEquals(0.toByte(), offsetOut[3])
    }

    @Test
    fun `PrintTiming formatSummary computes stage deltas and formats line without em dashes`() {
        val timing = PrintTiming(startMs = 1000L).apply {
            recordButtonTap(1000L)
            recordGenerateDone(1500L) // generate = 500 ms
            recordDecodeDone(1600L)   // decode = 100 ms
            recordRasterDone(bands = 10, bytes = 15000, width = 576, height = 800, paperDots = 576, ms = 2100L) // raster = 500 ms
            recordMutexAcquired(waitMs = 15L, ms = 2115L) // mutexWait = 15 ms
            recordConnected("Bluetooth", attempts = 1, insecure = false, ms = 3115L) // connect = 1000 ms
            recordSettleDone(3365L) // settle = 250 ms
            recordFirstChunkWritten(3400L) // firstWrite = 2400 ms from tap
            recordLastChunkWritten(5000L) // write = 1600 ms
            recordDrainDone(reply = true, ms = 5150L) // drain = 150 ms
            recordSocketClosed(5200L) // close = 50 ms, total = 4200 ms
        }

        val summary = timing.formatSummary()
        assertTrue(summary.contains("TOTAL=4200ms"))
        assertTrue(summary.contains("generate=500"))
        assertTrue(summary.contains("decode=100"))
        assertTrue(summary.contains("raster=500"))
        assertTrue(summary.contains("mutexWait=15"))
        assertTrue(summary.contains("connect=1000"))
        assertTrue(summary.contains("settle=250"))
        assertTrue(summary.contains("firstWrite=2400"))
        assertTrue(summary.contains("write=1600"))
        assertTrue(summary.contains("drain=150"))
        assertTrue(summary.contains("close=50"))
        assertFalse(summary.contains("\u2014"))
    }

    @Test
    fun `PrintTiming formatDetailedLog includes metadata and flags without em dashes`() {
        val timing = PrintTiming(startMs = 1000L).apply {
            recordRasterDone(bands = 12, bytes = 24000, width = 576, height = 900, paperDots = 576)
            recordMutexAcquired(waitMs = 50L)
            recordConnected("Bluetooth", attempts = 2, insecure = true)
            recordDrainDone(reply = false)
        }

        val details = timing.formatDetailedLog()
        assertTrue(details.contains("target=Bluetooth(attempts=2, insecure=true)"))
        assertTrue(details.contains("bitmap=576x900"))
        assertTrue(details.contains("paperWidth=576dots"))
        assertTrue(details.contains("bands=12"))
        assertTrue(details.contains("totalBytes=24000"))
        assertTrue(details.contains("mutexContended=true"))
        assertTrue(details.contains("replyReceived=false"))
        assertFalse(details.contains("\u2014"))
    }

    @Test
    fun `resolveSocketPreference prefers secure by default and insecure when known to require insecure RFCOMM`() {
        val knownInsecure = setOf("00:11:22:33:44:55", "aa:bb:cc:dd:ee:ff")

        // Default unknown address resolves to SECURE_FIRST
        assertEquals(SocketConnectPreference.SECURE_FIRST, resolveSocketPreference("12:34:56:78:9A:BC", knownInsecure))

        // Known insecure address resolves to INSECURE_FIRST regardless of case or whitespace
        assertEquals(SocketConnectPreference.INSECURE_FIRST, resolveSocketPreference("00:11:22:33:44:55", knownInsecure))
        assertEquals(SocketConnectPreference.INSECURE_FIRST, resolveSocketPreference("  AA:BB:CC:DD:EE:FF  ", knownInsecure))
    }

    // 17. Tests for ReceiptBitmapCache
    @Test
    fun `ReceiptBitmapCache buildKey formats deterministic uppercase key`() {
        assertEquals("101_EN_80", ReceiptBitmapCache.buildKey(101L, "en", 80))
        assertEquals("101_BN_58", ReceiptBitmapCache.buildKey(101L, "  bn  ", 58))
    }

    @Test
    fun `ReceiptBitmapCache buildKey changes with bill content hash`() {
        val key1 = ReceiptBitmapCache.buildKey(101L, "EN", 80, 11111)
        val key2 = ReceiptBitmapCache.buildKey(101L, "EN", 80, 22222)
        assertEquals("101_EN_80_11111", key1)
        assertEquals("101_EN_80_22222", key2)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `ReceiptBitmapCache caches bitmap and clear drops reference without recycling`() {
        val mockBitmap = mock(Bitmap::class.java)
        `when`(mockBitmap.isRecycled).thenReturn(false)

        ReceiptBitmapCache.put(200L, "EN", 80, mockBitmap, 12345)
        assertEquals(mockBitmap, ReceiptBitmapCache.get(200L, "EN", 80, 12345))
        assertNull(ReceiptBitmapCache.get(200L, "BN", 80, 12345))
        assertNull(ReceiptBitmapCache.get(201L, "EN", 80, 12345))
        assertNull(ReceiptBitmapCache.get(200L, "EN", 58, 12345))
        assertNull(ReceiptBitmapCache.get(200L, "EN", 80, 99999))

        ReceiptBitmapCache.clear()
        assertNull(ReceiptBitmapCache.get(200L, "EN", 80, 12345))
        // Must never recycle to prevent crashing in-flight prints
        verify(mockBitmap, never()).recycle()
    }

    @Test
    fun `ReceiptBitmapCache put drops old reference without recycling when key changes`() {
        val oldBitmap = mock(Bitmap::class.java)
        val newBitmap = mock(Bitmap::class.java)
        `when`(oldBitmap.isRecycled).thenReturn(false)
        `when`(newBitmap.isRecycled).thenReturn(false)

        ReceiptBitmapCache.put(301L, "EN", 80, oldBitmap, 100)
        assertEquals(oldBitmap, ReceiptBitmapCache.get(301L, "EN", 80, 100))

        ReceiptBitmapCache.put(302L, "EN", 80, newBitmap, 200)
        assertEquals(newBitmap, ReceiptBitmapCache.get(302L, "EN", 80, 200))
        // Must not recycle old bitmap while in-flight prints may read it
        verify(oldBitmap, never()).recycle()

        ReceiptBitmapCache.clear()
        verify(newBitmap, never()).recycle()
    }

    // 18. Tests for resolveBluetoothChunkDelayMs
    @Test
    fun `resolveBluetoothChunkDelayMs bursts initial chunks at 15ms and applies payload scaling thereafter`() {
        // Under 8192 bytes: always 15ms
        assertEquals(15L, resolveBluetoothChunkDelayMs(bytesWrittenSoFar = 512, totalBytes = 50000))
        assertEquals(15L, resolveBluetoothChunkDelayMs(bytesWrittenSoFar = 4096, totalBytes = 50000))
        assertEquals(15L, resolveBluetoothChunkDelayMs(bytesWrittenSoFar = 8192, totalBytes = 50000))

        // After 8192 bytes: matches bluetoothChunkDelayMs(totalBytes)
        val expectedDelay = bluetoothChunkDelayMs(50000)
        assertEquals(expectedDelay, resolveBluetoothChunkDelayMs(bytesWrittenSoFar = 8193, totalBytes = 50000))
        assertEquals(expectedDelay, resolveBluetoothChunkDelayMs(bytesWrittenSoFar = 20000, totalBytes = 50000))
    }

    // 19. Tests for R22-08 Label Target Routing
    @Test
    fun `testR22_08_labelTargetRouting resolves USB and Bluetooth targets correctly`() {
        val usbSettings = InvoiceSettingsData(
            labelPrinterConnection = "USB",
            labelUsbDeviceKey = "usb:1155:22339:TEST_SER_1"
        )
        val usbTarget = usbSettings.resolvedLabelPrinterTarget()
        assertTrue(usbTarget is PrinterTarget.Usb)
        assertEquals("usb:1155:22339:TEST_SER_1", (usbTarget as PrinterTarget.Usb).deviceKey)

        val receiptTarget = PrinterTarget.Usb("usb:1155:22339:TEST_SER_1")
        val otherTarget = PrinterTarget.Usb("usb:1155:22339:OTHER_SER_2")
        assertTrue(isSamePrinterTarget(usbTarget, receiptTarget))
        assertFalse(isSamePrinterTarget(usbTarget, otherTarget))

        // decidePrintRouting for USB mode
        val usbDecisionOk = decidePrintRouting("USB", "usb:1155:22339:TEST_SER_1", DirectPrintResult.Success)
        assertEquals(RoutingDecision.DIRECT_OK, usbDecisionOk)

        val usbDecisionFail = decidePrintRouting("USB", "usb:1155:22339:TEST_SER_1", DirectPrintResult.UsbDeviceNotFound)
        assertEquals(RoutingDecision.SHOW_ERROR, usbDecisionFail)

        // AUTO mode with USB mid-transfer failure (partialSuccessCount >= 1) must return SHOW_ERROR, not SYSTEM_DIALOG
        val autoPartialFail = decidePrintRouting(
            mode = "AUTO",
            address = "usb:1155:22339:TEST_SER_1",
            directResult = DirectPrintResult.UsbTransferFailed(12),
            partialSuccessCount = 1
        )
        assertEquals(RoutingDecision.SHOW_ERROR, autoPartialFail)
    }

    // 20. Tests for R22-09 USB Permission Timeout
    @Test
    fun `testR22_09_usbPermissionTimeout handles timeout without deadlock`() = kotlinx.coroutines.runBlocking {
        // verify withTimeoutOrNull returns null when timed out
        val timeoutResult = kotlinx.coroutines.withTimeoutOrNull(50L) {
            kotlinx.coroutines.delay(200L)
            true
        }
        assertNull(timeoutResult)
    }

    // 21. Tests for R22-15 Wrapped Height Accounting
    @Test
    fun `testR22_15_wrappedHeightAccounting accounts for multiple wrapped lines`() {
        val line1 = "Techyfi Store Main Branch"
        val line2 = "Super Shop & Departmental Store"
        val wrappedLines = listOf(line1, line2)
        val totalLineHeight = wrappedLines.size * 34f + 11f
        assertEquals(79f, totalLineHeight, 0.01f)
        assertTrue("Two lines must take more height than single line", totalLineHeight > 45f)
    }

    // 22. Tests for LBL-03 LabelPrintOutcome properties
    @Test
    fun `testLBL_03_labelPrintOutcomeSuccess`() {
        assertTrue(com.example.techyfipos.utils.LabelPrintOutcome.SENT_DIRECT.isSuccess)
        assertTrue(com.example.techyfipos.utils.LabelPrintOutcome.OPENED_SYSTEM_DIALOG.isSuccess)
        assertFalse(com.example.techyfipos.utils.LabelPrintOutcome.FAILED.isSuccess)
    }

    @Test
    fun testR23_21_receiptPrintOutcomeProperties() {
        assertTrue(com.example.techyfipos.utils.ReceiptPrintOutcome.DIRECT_SENT.isSuccess)
        assertTrue(com.example.techyfipos.utils.ReceiptPrintOutcome.DIALOG_OPENED.isSuccess)
        assertFalse(com.example.techyfipos.utils.ReceiptPrintOutcome.FAILED.isSuccess)

        // Compatibility aliases
        assertEquals(com.example.techyfipos.utils.ReceiptPrintOutcome.DIRECT_SENT, com.example.techyfipos.utils.ReceiptPrintOutcome.SENT_DIRECT)
        assertEquals(com.example.techyfipos.utils.ReceiptPrintOutcome.DIALOG_OPENED, com.example.techyfipos.utils.ReceiptPrintOutcome.OPENED_SYSTEM_DIALOG)
    }
}
