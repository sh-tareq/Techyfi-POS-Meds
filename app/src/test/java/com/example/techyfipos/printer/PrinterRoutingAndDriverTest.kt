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
}


