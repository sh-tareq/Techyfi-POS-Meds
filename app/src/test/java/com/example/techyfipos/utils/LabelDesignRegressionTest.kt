package com.example.techyfipos.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabelDesignRegressionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun render(design: LabelDesign, logo: Bitmap? = null, dpi: Int = 203,
                       type: ProductCodeType = ProductCodeType.BARCODE,
                       code: String = "352258311570", logoEnabled: Boolean = true,
                       size: ProductCodeLabelSize = ProductCodeLabelSize.SIZE_50_X_30,
                       scale: Float = 1f) = generateProductCodeLabelBitmap(
        productName = "Premium Cotton Shirt", barcode = code, formattedPrice = "৳1250.00",
        codeType = type, labelSize = size, showPrice = true, showCodeNumber = true,
        showStoreName = true, storeName = "TECHYFI STORE", showLogo = logoEnabled,
        labelDesign = design, storeLogo = logo, dpi = dpi, barcodeGraphicScale = scale
    )!!

    @Test fun catalogHasClassicFirstAndExactlyTwoLogoTemplates() {
        val presets = getDefaultLabelPresets()
        assertEquals(LabelDesign.entries.map { it.id }, presets.map { it.id })
        assertEquals(LabelDesign.CLASSIC, presets.first().style.labelDesign)
        assertEquals(5, presets.size)
        assertEquals(2, presets.count { it.style.showLogo })
        assertEquals(2, presets.drop(1).count { !it.style.showLogo })
    }

    @Test fun selectionAndLogoScaleRoundTripAndUnknownIdsFallBack() {
        val prefs = context.getSharedPreferences("label-design-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(LabelDesign.CLASSIC, loadLabelDesign(prefs))
        for (design in LabelDesign.entries) {
            val editor = prefs.edit()
            saveLabelStyleSettings(editor, LabelStyleSettings(labelDesign = design,
                showLogo = design.hasLogo, storeLogoScale = 1.4f))
            editor.commit()
            val restored = loadLabelStyleSettings(prefs)
            assertEquals(design, restored.labelDesign)
            assertEquals(design.hasLogo, restored.showLogo)
            assertEquals(1.4f, restored.storeLogoScale)
        }
        prefs.edit().putString("label_design", "removed_preset").commit()
        assertEquals(LabelDesign.CLASSIC, loadLabelDesign(prefs))
    }

    @Test fun actualLogoPixelsAppearOnlyInTheTwoLogoDesignsAndRespectToggle() {
        val logo = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        for (design in LabelDesign.entries) {
            val bitmap = render(design, logo)
            var red = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == Color.RED) red++
            }
            assertEquals(design.name, design.hasLogo, red > 0)
            val off = render(design, logo, logoEnabled = false)
            val absent = render(design)
            assertTrue(off.sameAs(absent))
            bitmap.recycle(); off.recycle(); absent.recycle()
        }
        logo.recycle()
    }

    @Test fun fileLogoLoadsAndInvalidLogoFallsBack() {
        val source = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val file = File(context.cacheDir, "configured-logo.png")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val decoded = loadLabelStoreLogo(context, file.toURI().toString())!!
        assertEquals(40, decoded.width)
        assertEquals(Color.RED, decoded.getPixel(10, 10))
        assertNull(loadLabelStoreLogo(context, "content://missing/logo"))
        source.recycle(); decoded.recycle()
    }

    @Test fun barcodesDecodeAtBothPrinterResolutionsAcrossAllTemplatesAndSizes() {
        val samples = listOf(ProductCodeType.BARCODE to "352258311570",
            ProductCodeType.EAN_13 to "5901234123457", ProductCodeType.EAN_8 to "96385074",
            ProductCodeType.CODE_39 to "ABC123", ProductCodeType.QR_CODE to "352258311570")
        for (dpi in listOf(203, 300)) for (design in LabelDesign.entries) {
            for (size in listOf(ProductCodeLabelSize.SIZE_40_X_25, ProductCodeLabelSize.SIZE_50_X_30, ProductCodeLabelSize.SIZE_60_X_40)) {
                for ((type, code) in samples) {
                    val bitmap = render(design, dpi = dpi, type = type, code = code, size = size)
                    val expected = productCodeLabelPixelSize(size, dpi = dpi)
                    assertEquals(expected.first, bitmap.width); assertEquals(expected.second, bitmap.height)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val decoded = try { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(
                        RGBLuminanceSource(bitmap.width, bitmap.height, pixels))), mapOf(DecodeHintType.TRY_HARDER to true))
                    } catch (e: ReaderException) { throw AssertionError("Cannot decode $design $dpi $size $type", e) }
                    assertEquals("$design $dpi $size $type", code, decoded.text)
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun barcodeScaleCannotInvadeTextSlotsAndQuietZonesStayWhite() {
        for (design in LabelDesign.entries.drop(1)) for (dpi in listOf(203, 300)) {
            val bitmap = render(design, dpi = dpi, scale = 2f)
            val bounds = designedBarcodeBounds(bitmap.width, bitmap.height)
            for (y in bounds.top until bounds.bottom) {
                assertEquals(Color.WHITE, bitmap.getPixel(bounds.left, y))
                assertEquals(Color.WHITE, bitmap.getPixel(bounds.right - 1, y))
            }
            assertTrue(bounds.height() >= bitmap.height * .27f)
            bitmap.recycle()
        }
    }

    @Test fun printerRasterPreservesRenderedDimensionsAndBarcodePixels() {
        for (design in LabelDesign.entries) for (dpi in listOf(203, 300)) {
            val bitmap = render(design, dpi = dpi)
            val raster = com.example.techyfipos.printer.tsplBitmapBytes(bitmap)
            val stride = (bitmap.width + 7) / 8
            assertEquals(stride * bitmap.height, raster.size)
            val bounds = designedBarcodeBounds(bitmap.width, bitmap.height)
            for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
                val white = (raster[y * stride + x / 8].toInt() and (1 shl (7 - x % 8))) != 0
                assertEquals(bitmap.getPixel(x, y) == Color.WHITE, white)
            }
            bitmap.recycle()
        }
    }

    @Test fun oversizedBarcodesAreRejectedRatherThanCropped() {
        for (design in LabelDesign.entries) {
            assertNull(generateProductCodeLabelBitmap("Small label", "A".repeat(80), "৳100",
                ProductCodeType.BARCODE, ProductCodeLabelSize.SIZE_25_X_15, true, true,
                labelDesign = design, dpi = 203))
        }
    }

    @Test fun designsAreVisuallyDistinctAndCanExportReviewSamples() {
        // Kept outside app assets; useful for reviewing real rendering without a device or APK.
        val directory = System.getenv("LABEL_RENDER_SAMPLES")?.let { File(it).apply { mkdirs() } }
        val fingerprints = mutableSetOf<Int>()
        val logo = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(logo).apply {
            drawColor(Color.WHITE)
            drawCircle(40f, 40f, 32f, android.graphics.Paint().apply { color = Color.BLACK })
            drawText("T", 24f, 56f, android.graphics.Paint().apply { color = Color.WHITE; textSize = 48f })
        }
        for (design in LabelDesign.entries) {
            val bitmap = render(design, logo, dpi = 300)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Duplicate design: $design", fingerprints.add(pixels.contentHashCode()))
            if (directory != null) File(directory, "${design.id}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        logo.recycle()
    }
}
