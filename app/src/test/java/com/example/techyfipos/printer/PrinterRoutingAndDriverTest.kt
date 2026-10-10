package com.example.techyfipos.printer

import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.utils.RoutingDecision
import com.example.techyfipos.utils.decidePrintRouting
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
