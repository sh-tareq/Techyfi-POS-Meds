package com.example.techyfipos.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.techyfipos.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlin.math.roundToInt

/** Stable IDs are persisted; enum order is the order in both label pickers. */
enum class LabelDesign(val id: String, val title: String, val titleRes: Int, val hasLogo: Boolean) {
    CLASSIC("classic", "Classic", R.string.label_design_classic, false),
    BRAND_HEADER("brand_header", "Brand Header · Logo", R.string.label_design_brand_header, true),
    BRAND_SIGNATURE("brand_signature", "Brand Signature · Logo", R.string.label_design_brand_signature, true),
    RETAIL_GRID("retail_grid", "Retail Grid", R.string.label_design_retail_grid, false),
    PRICE_FOCUS("price_focus", "Price Focus", R.string.label_design_price_focus, false);

    companion object {
        fun fromId(id: String?): LabelDesign = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

fun loadLabelDesign(preferences: SharedPreferences): LabelDesign =
    LabelDesign.fromId(preferences.getString("label_design", null))

/** Sample large source images before decoding. Missing/revoked URIs yield a clean text-only header. */
fun loadLabelStoreLogo(context: Context, location: String): Bitmap? {
    if (location.isBlank()) return null
    return try {
        fun open() = if (location.startsWith("/")) java.io.File(location).inputStream()
            else context.contentResolver.openInputStream(Uri.parse(location))
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        options.inJustDecodeBounds = false
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > 1024) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        open()?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) { null }
}

/** Geometry shared with tests. Barcode space cannot be consumed by text or logo scaling. */
internal fun designedBarcodeBounds(width: Int, height: Int, qr: Boolean = false): Rect =
    Rect((width * .08f).roundToInt(), (height * if (qr) .35f else .43f).roundToInt(),
        (width * .92f).roundToInt(), (height * if (qr) .74f else .71f).roundToInt())

internal fun renderDesignedProductLabel(
    productName: String, barcode: String, price: String, codeType: ProductCodeType,
    size: Pair<Int, Int>, design: LabelDesign, showPrice: Boolean, showNumber: Boolean,
    showStore: Boolean, storeName: String, showDates: Boolean, mfgType: String,
    mfgDate: String, expDate: String, showExtra: Boolean, extraLabel: String,
    extraValue: String, banglaDigits: Boolean, nameScale: Float, priceScale: Float,
    numberScale: Float, storeScale: Float, dateScale: Float, extraScale: Float,
    barcodeScale: Float, logo: Bitmap?, logoScale: Float
): Bitmap? {
    val (width, height) = size
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val qr = codeType == ProductCodeType.QR_CODE
        val left = width * .08f
        val right = width * .92f
        val contentWidth = right - left
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        fun rule(y: Float, start: Float = left, end: Float = right) {
            ink.strokeWidth = maxOf(1f, height * .003f)
            val adjustedY = if (qr && y <= height * .43f) y * .82f else y
            canvas.drawLine(start, adjustedY, end, adjustedY, ink)
        }
        // Each text slot clips to its own bounds and shrinks to fit full font metrics.
        fun text(value: String, rect: RectF, scale: Float, bold: Boolean = false, centered: Boolean = false, lines: Int = 1) {
            if (value.isBlank() || rect.width() <= 0 || rect.height() <= 0) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                textSize = minOf(width * .042f * scale.coerceIn(.6f, 1.8f), rect.height() / 1.4f)
            }
            fun layout() = StaticLayout.Builder.obtain(value, 0, value.length, paint, rect.width().toInt().coerceAtLeast(1))
                .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(true).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
            var block = layout()
            while ((block.height > rect.height() || (0 until block.lineCount).any { block.getEllipsisCount(it) > 0 }) && paint.textSize > 2f) {
                paint.textSize *= .9f
                block = layout()
            }
            canvas.save()
            canvas.clipRect(rect)
            canvas.translate(rect.left, rect.top + (rect.height() - block.height) / 2f)
            block.draw(canvas)
            canvas.restore()
        }
        fun slot(top: Float, bottom: Float, start: Float = left, end: Float = right): RectF {
            fun adjust(y: Float) = if (!qr) y else if (y <= .43f) y * .82f else y + .03f
            return RectF(start, height * adjust(top), end, height * adjust(bottom))
        }
        fun brand(top: Float, bottom: Float, centered: Boolean) {
            val box = slot(top, bottom)
            val hasLogo = design.hasLogo && logo != null && !logo.isRecycled
            var textLeft = left
            if (hasLogo) {
                val available = RectF(left, box.top, left + contentWidth * .26f, box.bottom)
                val source = logo!!
                val factor = minOf(available.width() / source.width, available.height() / source.height) *
                    (logoScale.coerceIn(.6f, 1.8f) / 1.8f)
                val dw = source.width * factor
                val dh = source.height * factor
                val target = RectF(available.centerX() - dw / 2, available.centerY() - dh / 2,
                    available.centerX() + dw / 2, available.centerY() + dh / 2)
                canvas.drawBitmap(source, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                textLeft = available.right + width * .025f
            }
            if (showStore) text(storeName, RectF(textLeft, box.top, right, box.bottom), storeScale * .8f, true, centered && !hasLogo)
        }
        when (design) {
            LabelDesign.BRAND_HEADER -> {
                brand(.055f, .18f, false)
                rule(height * .20f)
                text(productName, slot(.215f, .40f), nameScale, true, lines = 2)
            }
            LabelDesign.BRAND_SIGNATURE -> {
                text(productName, slot(.055f, .25f), nameScale, true, true, 2)
                if (showPrice) text(formatProductCodeLabelPrice(price), slot(.26f, .39f), priceScale * 1.2f, true, true)
                brand(.86f, .965f, true)
            }
            LabelDesign.RETAIL_GRID -> {
                if (showStore) text(storeName, slot(.055f, .14f), storeScale * .75f, true)
                rule(height * .16f)
                text(productName, slot(.18f, .39f, end = if (showPrice) width * .60f else right), nameScale, true, lines = 2)
                if (showPrice) {
                    rule(height * .395f, width * .65f, right)
                    text(formatProductCodeLabelPrice(price), slot(.18f, .39f, width * .65f), priceScale, true, true, 2)
                }
            }
            LabelDesign.PRICE_FOCUS -> {
                text(productName, slot(.055f, .20f), nameScale, true, lines = 2)
                if (showPrice) text(formatProductCodeLabelPrice(price), slot(.21f, .395f), priceScale * 1.5f, true)
                rule(height * .405f)
            }
            LabelDesign.CLASSIC -> error("Classic uses the preserved renderer")
        }
        val bounds = designedBarcodeBounds(width, height, qr)
        val format = when (codeType) {
            ProductCodeType.BARCODE -> BarcodeFormat.CODE_128
            ProductCodeType.EAN_13 -> if (isValidEan13(barcode)) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
            ProductCodeType.EAN_8 -> if (isValidEan8(barcode)) BarcodeFormat.EAN_8 else BarcodeFormat.CODE_128
            ProductCodeType.CODE_39 -> BarcodeFormat.CODE_39
            ProductCodeType.QR_CODE -> BarcodeFormat.QR_CODE
        }
        val codeHeight = (bounds.height() * barcodeScale.coerceIn(.5f, 1f)).roundToInt().coerceAtLeast(1)
        val codeWidth = if (format == BarcodeFormat.QR_CODE) minOf(bounds.width(), codeHeight) else bounds.width()
        val actualHeight = if (format == BarcodeFormat.QR_CODE) codeWidth else codeHeight
        val hints = mapOf(EncodeHintType.MARGIN to if (format == BarcodeFormat.QR_CODE) 4 else 20)
        val matrix = try {
            MultiFormatWriter().encode(barcode, format, codeWidth, actualHeight, hints)
        } catch (_: IllegalArgumentException) {
            // Retain the existing fallback for incompatible Code 39 input.
            MultiFormatWriter().encode(barcode, BarcodeFormat.CODE_128, codeWidth, actualHeight, hints)
        }
        require(matrix.width == codeWidth && matrix.height == actualHeight) { "Barcode does not fit label" }
        val code = Bitmap.createBitmap(codeWidth, actualHeight, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(codeWidth * actualHeight) { i -> if (matrix[i % codeWidth, i / codeWidth]) Color.BLACK else Color.WHITE }
            code.setPixels(pixels, 0, codeWidth, 0, 0, codeWidth, actualHeight)
            // Integer origin; no resizing or filtering of barcode modules.
            canvas.drawBitmap(code, ((width - codeWidth) / 2).toFloat(), bounds.top.toFloat(), null)
        } finally { code.recycle() }
        if (showNumber) text(if (banglaDigits) toBanglaDigits(barcode) else barcode, slot(.72f, .785f), numberScale * .7f, centered = true)
        when (design) {
            LabelDesign.BRAND_HEADER -> if (showPrice) text(formatProductCodeLabelPrice(price), slot(.79f, .87f), priceScale, true, true)
            LabelDesign.PRICE_FOCUS -> if (showStore) text(storeName, slot(.79f, .87f), storeScale * .75f, centered = true)
            else -> Unit
        }
        val extra = if (showExtra && extraValue.isNotBlank()) listOf(extraLabel, extraValue).filter { it.isNotBlank() }.joinToString(": ") else ""
        val dates = if (showDates) {
            val bn = banglaDigits || isAppBangla()
            listOfNotNull(
                mfgDate.takeIf { it.isNotBlank() }?.let { "${if (bn) { if (mfgType == "Pkg") "প্যাকেজিং:" else "উৎপাদন:" } else if (mfgType == "Pkg") "PKG:" else "MFG:"} $it" },
                expDate.takeIf { it.isNotBlank() }?.let { "${if (bn) "মেয়াদ:" else "EXP:"} $it" }
            ).joinToString("  |  ")
        } else ""
        val footerTop = if (design == LabelDesign.BRAND_SIGNATURE) .79f else .88f
        val footerBottom = if (design == LabelDesign.BRAND_SIGNATURE) .85f else .965f
        val middle = (footerTop + footerBottom) / 2
        if (extra.isNotBlank() && dates.isNotBlank()) {
            text(extra, slot(footerTop, middle), extraScale * .65f, centered = true)
            text(dates, slot(middle, footerBottom), dateScale * .65f, centered = true)
        } else text(extra.ifBlank { dates }, slot(footerTop, footerBottom), (if (extra.isBlank()) dateScale else extraScale) * .65f, centered = true)
        bitmap
    } catch (_: Exception) {
        bitmap.recycle()
        null
    }
}
