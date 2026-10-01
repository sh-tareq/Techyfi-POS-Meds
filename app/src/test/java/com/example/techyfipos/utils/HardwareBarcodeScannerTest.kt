package com.example.techyfipos.utils

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareBarcodeScannerTest {

    @Test
    fun `toEnglishDigits converts all Bengali digits to standard English ASCII digits`() {
        val bengaliDigits = "০১২৩৪৫৬৭৮৯"
        val expected = "0123456789"
        assertEquals(expected, toEnglishDigits(bengaliDigits))
    }

    @Test
    fun `toEnglishDigits converts realistic 13 digit Bengali barcode`() {
        val bengaliBarcode = "৮৯৪১১০০৫০১২৩৪"
        val expected = "8941100501234"
        assertEquals(expected, toEnglishDigits(bengaliBarcode))
    }

    @Test
    fun `toEnglishDigits preserves already English barcodes and alphanumeric codes`() {
        assertEquals("8941100501234", toEnglishDigits("8941100501234"))
        assertEquals("PROD-1024-X", toEnglishDigits("PROD-1024-X"))
        assertEquals("SKU_99-A", toEnglishDigits("SKU_99-A"))
    }

    @Test
    fun `toEnglishDigits handles mixed Bengali text and digits`() {
        assertEquals("পণ্য-894", toEnglishDigits("পণ্য-৮৯৪"))
    }

    @Test
    fun `toEnglishDigits converts Arabic and Persian numerals`() {
        assertEquals("0123456789", toEnglishDigits("٠١٢٣٤٥٦٧٨٩")) // Arabic-Indic
        assertEquals("0123456789", toEnglishDigits("۰۱۲۳۴۵۶۷۸۹")) // Persian/Urdu
    }

    @Test
    fun `toEnglishDigits handles blank and empty strings gracefully`() {
        assertEquals("", toEnglishDigits(""))
        assertEquals("   ", toEnglishDigits("   "))
    }

    @Test
    fun `extractAsciiBarcodeChar extracts English digits from hardware number row keycodes`() {
        for (i in 0..9) {
            val keyCode = KeyEvent.KEYCODE_0 + i
            val extracted = HardwareBarcodeScanner.extractAsciiBarcodeChar(keyCode = keyCode)
            assertEquals('0' + i, extracted)
        }
    }

    @Test
    fun `extractAsciiBarcodeChar extracts English digits from hardware numpad keycodes`() {
        for (i in 0..9) {
            val keyCode = KeyEvent.KEYCODE_NUMPAD_0 + i
            val extracted = HardwareBarcodeScanner.extractAsciiBarcodeChar(keyCode = keyCode)
            assertEquals('0' + i, extracted)
        }
    }

    @Test
    fun `extractAsciiBarcodeChar converts Bengali unicode digits to English ASCII`() {
        for (i in 0..9) {
            val unicodeChar = 0x09E6 + i // '০'..'৯'
            val extracted = HardwareBarcodeScanner.extractAsciiBarcodeChar(keyCode = 0, unicodeChar = unicodeChar)
            assertEquals('0' + i, extracted)
        }
    }

    @Test
    fun `extractAsciiBarcodeChar extracts letters and symbols`() {
        assertEquals('a', HardwareBarcodeScanner.extractAsciiBarcodeChar(keyCode = KeyEvent.KEYCODE_A))
        assertEquals('A', HardwareBarcodeScanner.extractAsciiBarcodeChar(keyCode = KeyEvent.KEYCODE_A, isShiftPressed = true))
        assertEquals('-', HardwareBarcodeScanner.extractAsciiBarcodeChar(keyCode = KeyEvent.KEYCODE_MINUS))
        assertEquals('/', HardwareBarcodeScanner.extractAsciiBarcodeChar(keyCode = KeyEvent.KEYCODE_SLASH))
    }

    @Test
    fun `isTerminatorKey identifies Enter, Numpad Enter, and Tab`() {
        assertTrue(HardwareBarcodeScanner.isTerminatorKey(keyCode = KeyEvent.KEYCODE_ENTER))
        assertTrue(HardwareBarcodeScanner.isTerminatorKey(keyCode = KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertTrue(HardwareBarcodeScanner.isTerminatorKey(keyCode = KeyEvent.KEYCODE_TAB))
        assertTrue(HardwareBarcodeScanner.isTerminatorKey(keyCode = 0, unicodeChar = '\n'.code))
        assertTrue(HardwareBarcodeScanner.isTerminatorKey(keyCode = 0, unicodeChar = '\t'.code))
        assertFalse(HardwareBarcodeScanner.isTerminatorKey(keyCode = KeyEvent.KEYCODE_SPACE))
        assertFalse(HardwareBarcodeScanner.isTerminatorKey(keyCode = KeyEvent.KEYCODE_0))
    }
}
