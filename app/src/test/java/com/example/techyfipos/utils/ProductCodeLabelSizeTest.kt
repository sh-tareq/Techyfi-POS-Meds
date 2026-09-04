package com.example.techyfipos.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductCodeLabelSizeTest {

    @Test
    fun `45 by 35 millimetre label converts to exact Android print media mils`() {
        assertEquals(1772 to 1378, pdfMediaSizeMils(45f, 35f))
    }

    @Test
    fun `print validation rejects JPEG bytes disguised as PDF`() {
        val file = File.createTempFile("receipt", ".pdf").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00, 0x00))
            deleteOnExit()
        }

        assertTrue(!isPdfFile(file))
    }

    @Test
    fun `print validation accepts PDF signature`() {
        val file = File.createTempFile("receipt", ".pdf").apply {
            writeText("%PDF-1.4\n")
            deleteOnExit()
        }

        assertTrue(isPdfFile(file))
    }

    @Test
    fun `shared label filename preserves Bengali product name`() {
        assertEquals(
            "ইসবগুল ভুসি ৫০ গ্রাম_1786833564332",
            sharedProductFileStem("  ইসবগুল ভুসি ৫০ গ্রাম  ", 1786833564332L)
        )
    }

    @Test
    fun `shared label filename removes only unsafe path characters`() {
        assertEquals("Milk-500ml_123", sharedProductFileStem("Milk/500ml", 123L))
    }
    @Test
    fun `45 by 35 preset preserves physical aspect ratio`() {
        val (width, height) = productCodeLabelPixelSize(ProductCodeLabelSize.SIZE_45_X_35)

        assertEquals(900, width)
        assertEquals(700, height)
    }

    @Test
    fun `custom dimensions are clamped to supported physical range`() {
        val (width, height) = productCodeLabelPixelSize(
            ProductCodeLabelSize.CUSTOM,
            customWidthMm = 5f,
            customHeightMm = 500f
        )

        assertTrue(width >= 200)
        assertEquals(2000, height)
        assertTrue(width < height)
    }

    @Test
    fun `shipping preset caps bitmap memory while retaining portrait orientation`() {
        val (width, height) = productCodeLabelPixelSize(ProductCodeLabelSize.SIZE_100_X_150)

        assertEquals(2000, height)
        assertTrue(width in 1332..1334)
    }

    @Test
    fun `PDF page uses selected physical label dimensions`() {
        val (widthPoints, heightPoints) = productCodeLabelPdfPageSize(45f, 35f)

        assertEquals(128, widthPoints)
        assertEquals(99, heightPoints)
    }

    @Test
    fun `label price includes a clear price prefix and taka amount`() {
        assertEquals("Price: ৳120", formatProductCodeLabelPrice("৳120"))
        assertEquals("Price: ৳120", formatProductCodeLabelPrice("Price: ৳120"))
    }
}
