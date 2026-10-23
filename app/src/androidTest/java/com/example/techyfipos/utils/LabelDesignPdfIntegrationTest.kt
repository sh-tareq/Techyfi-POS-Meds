package com.example.techyfipos.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabelDesignPdfIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Test fun pdfUsesSameDesignLogoAndPhysicalPageDimensions() {
        val logo = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        for (design in LabelDesign.entries) {
            val style = LabelStyleSettings(labelDesign = design, showLogo = design.hasLogo,
                showStoreName = true, storeName = "TECHYFI STORE", showBanglaDigits = true)
            val result = cacheBatchProductCodeLabelsPdf(context,
                listOf(ProductLabelItem("Cotton Shirt", "352258311570", "৳1250", quantity = 2)),
                ProductCodeType.BARCODE, style, dpi = 300, storeLogo = logo)
            assertNotNull("PDF failed for $design", result)
            assertTrue(result!!.first.inputStream().use { String(ByteArray(5).also { bytes -> it.read(bytes) }) } == "%PDF-")
            ParcelFileDescriptor.open(result.first, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { pdf ->
                    assertEquals(2, pdf.pageCount)
                    pdf.openPage(0).use { page ->
                        assertEquals(142, page.width)
                        assertEquals(85, page.height)
                        val raster = Bitmap.createBitmap(591, 354, Bitmap.Config.ARGB_8888)
                        raster.eraseColor(Color.WHITE)
                        page.render(raster, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        var redPixels = 0
                        for (y in 0 until raster.height) for (x in 0 until raster.width) {
                            val pixel = raster.getPixel(x, y)
                            if (Color.red(pixel) > 200 && Color.green(pixel) < 70) redPixels++
                        }
                        assertEquals(design.hasLogo, redPixels > 0)
                        raster.recycle()
                    }
                }
            }
        }
        logo.recycle()
    }

}
