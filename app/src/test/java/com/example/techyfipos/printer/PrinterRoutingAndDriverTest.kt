package com.example.techyfipos.printer

import android.graphics.Bitmap
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.utils.RoutingDecision
import com.example.techyfipos.utils.decidePrintRouting
import com.example.techyfipos.utils.reportCanvasWidth
import com.example.techyfipos.utils.reportPdfPageWidthPoints
import com.example.techyfipos.utils.reportPoweredByText
import com.example.techyfipos.utils.reportStoreName
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

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
    fun `resolvedReceiptPrinterAddress returns cash drawer address when only cash drawer address is set`() {
        val settings = InvoiceSettingsData(
            directBluetoothPrinterAddress = "",
            cashDrawerPrinterAddress = "11:22:33:44:55:66"
        )
        assertEquals("11:22:33:44:55:66", settings.resolvedReceiptPrinterAddress())
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
    fun `labelLuminanceThreshold maps values correctly and clamps out of bounds inputs`() {
        assertEquals(110, labelLuminanceThreshold(0))
        assertEquals(174, labelLuminanceThreshold(8))
        assertEquals(230, labelLuminanceThreshold(15))
        assertEquals(110, labelLuminanceThreshold(-5))
        assertEquals(230, labelLuminanceThreshold(99))
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
            assertEquals(20L, delays[2])

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
}
