package com.techyfiposmeds.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductCodeLabelRenderingIntegrationTest {

    @Test
    fun bengaliProductAndStoreNamesRenderAtEveryPresetSize() {
        ProductCodeLabelSize.entries
            .filterNot { it == ProductCodeLabelSize.CUSTOM }
            .forEach { size ->
                val bitmap = generateProductCodeLabelBitmap(
                    productName = "এসি আইodized চিনি ৫০০ গ্রাম",
                    barcode = "352258311570",
                    formattedPrice = "৳120",
                    codeType = ProductCodeType.BARCODE,
                    labelSize = size,
                    showPrice = true,
                    showCodeNumber = true,
                    showStoreName = true,
                    storeName = "আল কাউসার স্টোর",
                    storeNameTextScale = 1.8f
                )

                assertNotNull("Bitmap was null for ${size.displayName}", bitmap)
                val expected = productCodeLabelPixelSize(size)
                assertEquals(expected.first, bitmap!!.width)
                assertEquals(expected.second, bitmap.height)
                assertTrue("Rendered label was blank for ${size.displayName}", bitmapHasInk(bitmap))
                bitmap.recycle()
            }
    }

    @Test
    fun minimumCustomSizeRendersBengaliWithoutFailure() {
        val bitmap = generateProductCodeLabelBitmap(
            productName = "ইসবগুল ভুসি ৫০ গ্রাম - বিশেষ প্যাক",
            barcode = "1786833564332",
            formattedPrice = "৳140",
            codeType = ProductCodeType.QR_CODE,
            labelSize = ProductCodeLabelSize.CUSTOM,
            showPrice = true,
            showCodeNumber = true,
            customWidthMm = 15f,
            customHeightMm = 10f,
            productNameTextScale = 1.8f,
            priceTextScale = 1.8f,
            codeNumberTextScale = 1.8f,
            showStoreName = true,
            storeName = "টেকিফাই বাংলা স্টোরের অনেক লম্বা নাম",
            storeNameTextScale = 1.8f
        )

        assertNotNull(bitmap)
        assertEquals(300, bitmap!!.width)
        assertEquals(200, bitmap.height)
        assertTrue(bitmapHasInk(bitmap))
        bitmap.recycle()
    }

    private fun bitmapHasInk(bitmap: android.graphics.Bitmap): Boolean {
        val stepX = (bitmap.width / 50).coerceAtLeast(1)
        val stepY = (bitmap.height / 50).coerceAtLeast(1)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                if (bitmap.getPixel(x, y) != android.graphics.Color.WHITE) return true
            }
        }
        return false
    }
}
