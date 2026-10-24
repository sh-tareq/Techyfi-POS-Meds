package com.techyfiposmeds.utils

import com.techyfiposmeds.R
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import com.techyfiposmeds.data.db.Product
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class BarcodeLabelDesign(
    val id: String,
    val displayName: String,
    val hasLogo: Boolean = false,
    @androidx.annotation.StringRes val displayNameRes: Int? = null,
    @androidx.annotation.StringRes val descriptionRes: Int? = null
) {
    STANDARD_RETAIL(
        id = "standard_retail",
        displayName = "Standard Retail",
        hasLogo = false,
        displayNameRes = R.string.barcode_design_standard,
        descriptionRes = R.string.barcode_design_standard_desc
    ),
    BOUTIQUE_CREST(
        id = "boutique_crest",
        displayName = "Boutique Crest",
        hasLogo = true,
        displayNameRes = R.string.barcode_design_boutique_crest,
        descriptionRes = R.string.barcode_design_boutique_crest_desc
    ),
    BRAND_HERO_SPLIT(
        id = "brand_hero_split",
        displayName = "Brand Split Card",
        hasLogo = true,
        displayNameRes = R.string.barcode_design_brand_split,
        descriptionRes = R.string.barcode_design_brand_split_desc
    ),
    COMPACT_MINIMALIST(
        id = "compact_minimalist",
        displayName = "Compact Grid",
        hasLogo = false,
        displayNameRes = R.string.barcode_design_compact_minimalist,
        descriptionRes = R.string.barcode_design_compact_minimalist_desc
    ),
    SHELF_EDGE_TAG(
        id = "shelf_edge_tag",
        displayName = "Shelf-Edge Tag",
        hasLogo = false,
        displayNameRes = R.string.barcode_design_shelf_edge,
        descriptionRes = R.string.barcode_design_shelf_edge_desc
    );

    fun getLocalizedName(context: Context): String =
        displayNameRes?.let { context.getString(it) } ?: displayName
}

enum class ProductCodeType {
    BARCODE, // Code 128
    QR_CODE,
    EAN_13,
    EAN_8,
    CODE_39
}

fun isValidEan13(barcode: String): Boolean {
    val clean = barcode.trim()
    if (clean.length != 13 || !clean.all { it.isDigit() }) return false
    val sum = clean.substring(0, 12).mapIndexed { idx, c ->
        val digit = c.digitToInt()
        if (idx % 2 == 0) digit else digit * 3
    }.sum()
    val checkDigit = (10 - (sum % 10)) % 10
    return clean[12].digitToInt() == checkDigit
}

fun isValidEan8(barcode: String): Boolean {
    val clean = barcode.trim()
    if (clean.length != 8 || !clean.all { it.isDigit() }) return false
    val sum = clean.substring(0, 7).mapIndexed { idx, c ->
        val digit = c.digitToInt()
        if (idx % 2 == 0) digit * 3 else digit
    }.sum()
    val checkDigit = (10 - (sum % 10)) % 10
    return clean[7].digitToInt() == checkDigit
}

fun getRecommendedCodeType(barcode: String): ProductCodeType {
    val clean = barcode.trim()
    return when {
        isValidEan13(clean) -> ProductCodeType.EAN_13
        isValidEan8(clean) -> ProductCodeType.EAN_8
        else -> ProductCodeType.BARCODE
    }
}

enum class ProductCodePriceTier {
    SELLING,
    MRP,
    WHOLESALE,
    DUAL
}

enum class ProductCodeLabelSize(
    val widthMm: Float,
    val heightMm: Float,
    val displayName: String,
    @androidx.annotation.StringRes val displayNameRes: Int? = null
) {
    SIZE_25_X_15(25f, 15f, "25 × 15 mm"),
    SIZE_30_X_20(30f, 20f, "30 × 20 mm"),
    SIZE_40_X_25(40f, 25f, "40 × 25 mm"),
    SIZE_45_X_35(45f, 35f, "45 × 35 mm"),
    SIZE_50_X_30(50f, 30f, "50 × 30 mm"),
    SIZE_60_X_40(60f, 40f, "60 × 40 mm"),
    SIZE_100_X_150(100f, 150f, "100 × 150 mm"),
    CUSTOM(0f, 0f, "Custom size", R.string.product_code_label_custom_size)
}

fun ProductCodeLabelSize.getLocalizedName(context: Context): String {
    return displayNameRes?.let { context.getString(it) } ?: displayName
}

fun productCodeLabelPixelSize(
    labelSize: ProductCodeLabelSize,
    customWidthMm: Float = 50f,
    customHeightMm: Float = 30f,
    dpi: Int? = null
): Pair<Int, Int> {
    val widthMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customWidthMm else labelSize.widthMm
    val heightMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customHeightMm else labelSize.heightMm
    val safeWidth = widthMm.coerceIn(15f, 150f)
    val safeHeight = heightMm.coerceIn(10f, 200f)
    if (dpi != null && dpi > 0) {
        val dpmm = dpi / 25.4f
        return maxOf(1, (safeWidth * dpmm).roundToInt()) to maxOf(1, (safeHeight * dpmm).roundToInt())
    }
    val rawWidth = (safeWidth * 20f).roundToInt()
    val rawHeight = (safeHeight * 20f).roundToInt()
    val longestEdge = max(rawWidth, rawHeight)
    val scale = if (longestEdge > 2000) 2000f / longestEdge else 1f
    return (rawWidth * scale).roundToInt().coerceAtLeast(200) to
        (rawHeight * scale).roundToInt().coerceAtLeast(200)
}

fun productCodeLabelPdfPageSize(widthMm: Float, heightMm: Float): Pair<Int, Int> =
    (widthMm.coerceIn(15f, 150f) / 25.4f * 72f).roundToInt().coerceAtLeast(1) to
        (heightMm.coerceIn(10f, 200f) / 25.4f * 72f).roundToInt().coerceAtLeast(1)

fun generateProductCodeLabelBitmap(
    productName: String,
    barcode: String,
    formattedPrice: String,
    codeType: ProductCodeType,
    labelSize: ProductCodeLabelSize,
    showPrice: Boolean,
    showCodeNumber: Boolean,
    customWidthMm: Float = 50f,
    customHeightMm: Float = 30f,
    productNameTextScale: Float = 1f,
    priceTextScale: Float = 1f,
    codeNumberTextScale: Float = 1f,
    showStoreName: Boolean = false,
    storeName: String = "",
    storeNameTextScale: Float = 1f,
    showDateTracking: Boolean = false,
    mfgOrPkgType: String = "Mfg",
    mfgOrPkgDate: String = "",
    expDate: String = "",
    dateTrackingTextScale: Float = 1f,
    barcodeGraphicScale: Float = 1f,
    showExtraField: Boolean = false,
    extraFieldLabel: String = "",
    extraFieldValue: String = "",
    extraFieldTextScale: Float = 1f,
    showLogo: Boolean = false,
    dpi: Int? = null,
    showBanglaDigits: Boolean = false,
    labelDesign: BarcodeLabelDesign = BarcodeLabelDesign.STANDARD_RETAIL
): Bitmap? {
    val cleanBarcode = toEnglishDigits(barcode.trim())
    if (cleanBarcode.isBlank()) return null

    return try {
        val (width, height) = productCodeLabelPixelSize(labelSize, customWidthMm, customHeightMm, dpi)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val framePadding = (width * 0.035f).coerceIn(6f, 22f)
        val cardRect = RectF(
            framePadding,
            framePadding,
            width - framePadding,
            height - framePadding
        )
        val borderCornerRadius = (width * 0.040f).coerceIn(10f, 30f)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.007f).coerceIn(2.5f, 6.5f)
        }

        val effectiveStoreName = storeName.trim().uppercase()
        val isBengali = showBanglaDigits || isAppBangla() || storeName.hasBengali() || productName.hasBengali()
        val mfgLabel = if (isBengali) {
            if (mfgOrPkgType == "Pkg") "প্যাকেজিং:" else "উৎপাদন:"
        } else {
            if (mfgOrPkgType == "Pkg") "PKG:" else "MFG:"
        }
        val expLabel = if (isBengali) "মেয়াদ:" else "EXP:"
        val dateString = buildString {
            if (mfgOrPkgDate.isNotBlank()) {
                append("$mfgLabel $mfgOrPkgDate")
            }
            if (expDate.isNotBlank()) {
                if (isNotEmpty()) append("    |    ")
                append("$expLabel $expDate")
            }
        }.trim()
        val hasDates = showDateTracking && (mfgOrPkgDate.isNotBlank() || expDate.isNotBlank())
        val showExtra = showExtraField && extraFieldValue.isNotBlank()

        when (labelDesign) {
            BarcodeLabelDesign.STANDARD_RETAIL -> {
                drawStandardRetailLabel(
                    canvas = canvas,
                    width = width,
                    height = height,
                    cardRect = cardRect,
                    borderCornerRadius = borderCornerRadius,
                    borderPaint = borderPaint,
                    productName = productName,
                    cleanBarcode = cleanBarcode,
                    formattedPrice = formattedPrice,
                    codeType = codeType,
                    showPrice = showPrice,
                    showCodeNumber = showCodeNumber,
                    effectiveStoreName = effectiveStoreName,
                    showStoreName = showStoreName,
                    showDate = hasDates && dateString.isNotBlank(),
                    dateString = dateString,
                    showBanglaDigits = showBanglaDigits,
                    productNameTextScale = productNameTextScale,
                    priceTextScale = priceTextScale,
                    codeNumberTextScale = codeNumberTextScale,
                    storeNameTextScale = storeNameTextScale,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale,
                    showExtra = showExtra,
                    extraFieldLabel = extraFieldLabel,
                    extraFieldValue = extraFieldValue,
                    extraFieldTextScale = extraFieldTextScale,
                    isBengali = isBengali
                )
            }
            BarcodeLabelDesign.BOUTIQUE_CREST -> {
                drawBoutiqueCrestLabel(
                    canvas = canvas,
                    width = width,
                    height = height,
                    cardRect = cardRect,
                    borderCornerRadius = borderCornerRadius,
                    borderPaint = borderPaint,
                    productName = productName,
                    cleanBarcode = cleanBarcode,
                    formattedPrice = formattedPrice,
                    codeType = codeType,
                    showPrice = showPrice,
                    showCodeNumber = showCodeNumber,
                    effectiveStoreName = effectiveStoreName,
                    showStoreName = showStoreName,
                    hasDates = hasDates,
                    mfgLabel = mfgLabel,
                    mfgDate = mfgOrPkgDate,
                    expLabel = expLabel,
                    expDate = expDate,
                    showBanglaDigits = showBanglaDigits,
                    productNameTextScale = productNameTextScale,
                    priceTextScale = priceTextScale,
                    codeNumberTextScale = codeNumberTextScale,
                    storeNameTextScale = storeNameTextScale,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale,
                    showLogo = showLogo
                )
            }
            BarcodeLabelDesign.BRAND_HERO_SPLIT -> {
                drawBrandHeroSplitLabel(
                    canvas = canvas,
                    width = width,
                    height = height,
                    cardRect = cardRect,
                    borderCornerRadius = borderCornerRadius,
                    borderPaint = borderPaint,
                    productName = productName,
                    cleanBarcode = cleanBarcode,
                    formattedPrice = formattedPrice,
                    codeType = codeType,
                    showPrice = showPrice,
                    showCodeNumber = showCodeNumber,
                    effectiveStoreName = effectiveStoreName,
                    showStoreName = showStoreName,
                    hasDates = hasDates,
                    mfgLabel = mfgLabel,
                    mfgDate = mfgOrPkgDate,
                    expLabel = expLabel,
                    expDate = expDate,
                    showBanglaDigits = showBanglaDigits,
                    productNameTextScale = productNameTextScale,
                    priceTextScale = priceTextScale,
                    codeNumberTextScale = codeNumberTextScale,
                    storeNameTextScale = storeNameTextScale,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale,
                    showLogo = showLogo
                )
            }
            BarcodeLabelDesign.COMPACT_MINIMALIST -> {
                drawCompactMinimalistLabel(
                    canvas = canvas,
                    width = width,
                    height = height,
                    cardRect = cardRect,
                    borderCornerRadius = borderCornerRadius,
                    borderPaint = borderPaint,
                    productName = productName,
                    cleanBarcode = cleanBarcode,
                    formattedPrice = formattedPrice,
                    codeType = codeType,
                    showPrice = showPrice,
                    showCodeNumber = showCodeNumber,
                    effectiveStoreName = effectiveStoreName,
                    showStoreName = showStoreName,
                    hasDates = hasDates,
                    mfgLabel = mfgLabel,
                    mfgDate = mfgOrPkgDate,
                    expLabel = expLabel,
                    expDate = expDate,
                    showBanglaDigits = showBanglaDigits,
                    productNameTextScale = productNameTextScale,
                    priceTextScale = priceTextScale,
                    codeNumberTextScale = codeNumberTextScale,
                    storeNameTextScale = storeNameTextScale,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale
                )
            }
            BarcodeLabelDesign.SHELF_EDGE_TAG -> {
                drawShelfEdgeTagLabel(
                    canvas = canvas,
                    width = width,
                    height = height,
                    cardRect = cardRect,
                    borderCornerRadius = borderCornerRadius,
                    borderPaint = borderPaint,
                    productName = productName,
                    cleanBarcode = cleanBarcode,
                    formattedPrice = formattedPrice,
                    codeType = codeType,
                    showPrice = showPrice,
                    showCodeNumber = showCodeNumber,
                    effectiveStoreName = effectiveStoreName,
                    showStoreName = showStoreName,
                    hasDates = hasDates,
                    mfgLabel = mfgLabel,
                    mfgDate = mfgOrPkgDate,
                    expLabel = expLabel,
                    expDate = expDate,
                    showBanglaDigits = showBanglaDigits,
                    productNameTextScale = productNameTextScale,
                    priceTextScale = priceTextScale,
                    codeNumberTextScale = codeNumberTextScale,
                    storeNameTextScale = storeNameTextScale,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale,
                    categoryOrVariant = if (showExtra) extraFieldValue else ""
                )
            }
        }

        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// =========================================================================
// 5 BARCODE LABEL DESIGN RENDERERS
// =========================================================================

private fun drawFittedTitle(
    canvas: Canvas,
    text: String,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    paint: TextPaint,
    textColor: Int = Color.BLACK,
    maxLines: Int = 2,
    alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER
) {
    paint.color = textColor
    val safeWidth = width.toInt().coerceAtLeast(20)
    val layout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, safeWidth)
        .setAlignment(alignment)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setEllipsizedWidth(safeWidth)
        .setIncludePad(false)
        .build()
    val layoutH = layout.height
    val translateY = top + ((height - layoutH) / 2f).coerceAtLeast(0f)
    canvas.save()
    canvas.translate(left, translateY)
    layout.draw(canvas)
    canvas.restore()
}

// --- DESIGN 0: STANDARD RETAIL (ক্লাসিক স্ট্যান্ডার্ড রিটেইল - RESTORED ORIGINAL) ---
private fun drawStandardRetailLabel(
    canvas: Canvas,
    width: Int,
    height: Int,
    cardRect: RectF,
    borderCornerRadius: Float,
    borderPaint: Paint,
    productName: String,
    cleanBarcode: String,
    formattedPrice: String,
    codeType: ProductCodeType,
    showPrice: Boolean,
    showCodeNumber: Boolean,
    effectiveStoreName: String,
    showStoreName: Boolean,
    showDate: Boolean,
    dateString: String,
    showBanglaDigits: Boolean,
    productNameTextScale: Float,
    priceTextScale: Float,
    codeNumberTextScale: Float,
    storeNameTextScale: Float,
    dateTrackingTextScale: Float,
    barcodeGraphicScale: Float,
    showExtra: Boolean,
    extraFieldLabel: String,
    extraFieldValue: String,
    extraFieldTextScale: Float,
    isBengali: Boolean
) {
    canvas.drawRoundRect(cardRect, borderCornerRadius, borderCornerRadius, borderPaint)

    val framePadding = cardRect.left
    val horizontalPadding = (width * 0.08f).toInt()
    val contentWidth = width - horizontalPadding * 2
    var top = (height * 0.07f).toInt()
    val centerX = width / 2f
    val leftX = horizontalPadding.toFloat()
    val rightX = (width - horizontalPadding).toFloat()

    val rawNameTextSize = (width * 0.054f).coerceIn(20f, 74f) *
        productNameTextScale.coerceIn(0.6f, 1.8f)
    val rawPriceTextSize = (width * 0.040f).coerceIn(16f, 56f) *
        priceTextScale.coerceIn(0.6f, 1.8f)
    val rawNumberTextSize = (width * 0.030f).coerceIn(14f, 44f) *
        codeNumberTextScale.coerceIn(0.6f, 1.8f)
    val rawStoreTextSize = (width * 0.030f).coerceIn(14f, 44f) *
        storeNameTextScale.coerceIn(0.6f, 1.8f)
    val rawDateTextSize = (width * 0.028f).coerceIn(14f, 42f) *
        dateTrackingTextScale.coerceIn(0.6f, 1.8f)
    val rawExtraTextSize = (width * 0.028f).coerceIn(14f, 42f) *
        extraFieldTextScale.coerceIn(0.6f, 1.8f)

    val fixedVerticalSpace = height * (
        0.07f + 0.04f + 0.05f +
            (if (showPrice) 0.035f else 0f) +
            (if (showCodeNumber) 0.035f else 0f) +
            (if (showStoreName && effectiveStoreName.isNotBlank()) 0.035f else 0f) +
            (if (showExtra) 0.035f else 0f) +
            (if (showDate) 0.045f else 0f)
        ) + 32f
    val requestedTextSpace = rawNameTextSize * 2.2f +
        (if (showPrice) rawPriceTextSize else 0f) +
        (if (showCodeNumber) rawNumberTextSize else 0f) +
        (if (showStoreName && effectiveStoreName.isNotBlank()) rawStoreTextSize else 0f) +
        (if (showExtra) rawExtraTextSize else 0f) +
        (if (showDate) rawDateTextSize else 0f)
    val verticalTextScale = if (requestedTextSpace > 0f) {
        ((height - fixedVerticalSpace) / requestedTextSpace).coerceIn(0.55f, 1f)
    } else {
        1f
    }

    // 1. PRODUCT NAME
    val defaultFallback = if (isBengali) "পণ্যের নাম" else "Product Name"
    val uppercaseName = productName.ifBlank { defaultFallback }.uppercase()
    val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = rawNameTextSize * verticalTextScale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        letterSpacing = if (uppercaseName.hasBengali()) 0f else 0.02f
    }
    val nameLayout = StaticLayout.Builder
        .obtain(uppercaseName, 0, uppercaseName.length, namePaint, contentWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setMaxLines(2)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setEllipsizedWidth(contentWidth)
        .setIncludePad(false)
        .build()
    canvas.save()
    canvas.translate(horizontalPadding.toFloat(), top.toFloat())
    nameLayout.draw(canvas)
    canvas.restore()
    top += nameLayout.height + (height * 0.015f).toInt()

    // 2. HEADER DIVIDER LINE WITH CENTER DOT (─────── • ───────)
    val dividerY = top.toFloat()
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = (height * 0.005f).coerceIn(2f, 5f)
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    val dotRadius = (height * 0.012f).coerceIn(4f, 10f)
    val dotGap = dotRadius * 2.2f

    canvas.drawLine(leftX, dividerY, centerX - dotGap, dividerY, dividerPaint)
    canvas.drawCircle(centerX, dividerY, dotRadius, dotPaint)
    canvas.drawLine(centerX + dotGap, dividerY, rightX, dividerY, dividerPaint)

    top += (height * 0.022f).toInt()

    // 3. PRICE
    if (showPrice) {
        val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = rawPriceTextSize * verticalTextScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        drawFittedCenteredText(
            canvas = canvas,
            text = formatProductCodeLabelPrice(formattedPrice),
            centerX = centerX,
            baselineY = top + pricePaint.textSize,
            maxWidth = contentWidth.toFloat(),
            paint = pricePaint
        )
        top += pricePaint.textSize.toInt() + (height * 0.025f).toInt()
    }

    // Calculate barcode dimensions
    val numberTextSize = rawNumberTextSize * verticalTextScale
    val numberSpace = if (showCodeNumber) (numberTextSize + height * 0.035f).toInt() else 0
    val storeTextSize = rawStoreTextSize * verticalTextScale
    val storeSpace = if (showStoreName && effectiveStoreName.isNotBlank()) {
        (storeTextSize + height * 0.032f).toInt()
    } else {
        0
    }
    val dateTextSize = rawDateTextSize * verticalTextScale
    val dateSpace = if (showDate) (dateTextSize + height * 0.045f).toInt() else 0

    val bottomPadding = (height * 0.06f).toInt()
    val baseCodeHeight = (height - top - numberSpace - storeSpace - dateSpace - bottomPadding)
        .coerceAtLeast(28)
    val barcodeHeightMultiplier = barcodeGraphicScale.coerceIn(0.5f, 2.0f)

    val codeWidth: Int
    val codeHeight: Int
    if (codeType == ProductCodeType.QR_CODE) {
        val squareSize = (minOf(contentWidth, baseCodeHeight) * barcodeHeightMultiplier).roundToInt()
            .coerceIn(28, minOf(contentWidth, height - top - bottomPadding))
        codeWidth = squareSize
        codeHeight = squareSize
    } else {
        codeWidth = contentWidth
        codeHeight = (baseCodeHeight * barcodeHeightMultiplier).roundToInt()
            .coerceAtMost((height * 0.45f).toInt()).coerceAtLeast(24)
    }

    // 4. BARCODE / QR CODE GRAPHIC
    val format = when (codeType) {
        ProductCodeType.QR_CODE -> BarcodeFormat.QR_CODE
        ProductCodeType.EAN_13 -> if (isValidEan13(cleanBarcode)) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
        ProductCodeType.EAN_8 -> if (isValidEan8(cleanBarcode)) BarcodeFormat.EAN_8 else BarcodeFormat.CODE_128
        ProductCodeType.CODE_39 -> BarcodeFormat.CODE_39
        ProductCodeType.BARCODE -> BarcodeFormat.CODE_128
    }
    val hints = mapOf(EncodeHintType.MARGIN to if (codeType == ProductCodeType.QR_CODE) 1 else 6)
    val matrix = try {
        MultiFormatWriter().encode(cleanBarcode, format, codeWidth, codeHeight, hints)
    } catch (e: Exception) {
        MultiFormatWriter().encode(cleanBarcode, BarcodeFormat.CODE_128, codeWidth, codeHeight, hints)
    }
    val codeBitmap = Bitmap.createBitmap(codeWidth, codeHeight, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(codeWidth * codeHeight)
    for (y in 0 until codeHeight) {
        val rowOffset = y * codeWidth
        for (x in 0 until codeWidth) {
            pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
        }
    }
    codeBitmap.setPixels(pixels, 0, codeWidth, 0, 0, codeWidth, codeHeight)
    val codeLeft = (width - codeWidth) / 2f
    canvas.drawBitmap(codeBitmap, codeLeft, top.toFloat(), null)
    codeBitmap.recycle()

    var nextBaseline = top + codeHeight + height * 0.018f

    // 5. BARCODE NUMBERS (SPACED OUT DIGITS)
    if (showCodeNumber) {
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = numberTextSize
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        val numberY = nextBaseline + numberPaint.textSize
        val spacedDigits = (if (showBanglaDigits) toBanglaDigits(cleanBarcode) else cleanBarcode).toCharArray().joinToString(" ")
        drawFittedCenteredText(
            canvas = canvas,
            text = spacedDigits,
            centerX = centerX,
            baselineY = numberY,
            maxWidth = contentWidth.toFloat(),
            paint = numberPaint
        )
        nextBaseline = numberY + height * 0.016f
    }

    // 6. STORE NAME (UPPERCASE BOLD)
    if (showStoreName && effectiveStoreName.isNotBlank()) {
        val storePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = storeTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = if (effectiveStoreName.hasBengali()) 0f else 0.04f
        }
        val maxStoreY = if (showDate) {
            height - (dateTextSize + height * 0.045f + framePadding + 6f)
        } else {
            height - bottomPadding.toFloat()
        }
        val storeY = (nextBaseline + storePaint.textSize).coerceAtMost(maxStoreY)
        drawFittedCenteredText(
            canvas = canvas,
            text = effectiveStoreName,
            centerX = centerX,
            baselineY = storeY,
            maxWidth = contentWidth.toFloat(),
            paint = storePaint
        )
        nextBaseline = storeY + height * 0.018f
    }

    // 6b. OPTIONAL EXTRA FIELD (WEIGHT, VARIANT, BATCH, CATEGORY)
    if (showExtra) {
        val extraPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = rawExtraTextSize * verticalTextScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val extraText = if (extraFieldLabel.isNotBlank()) "$extraFieldLabel: $extraFieldValue" else extraFieldValue
        val maxExtraY = if (showDate) {
            height - (dateTextSize + height * 0.045f + framePadding + 6f)
        } else {
            height - bottomPadding.toFloat()
        }
        val extraY = (nextBaseline + extraPaint.textSize).coerceAtMost(maxExtraY)
        drawFittedCenteredText(
            canvas = canvas,
            text = extraText,
            centerX = centerX,
            baselineY = extraY,
            maxWidth = contentWidth.toFloat(),
            paint = extraPaint
        )
        nextBaseline = extraY + height * 0.018f
    }

    // 7. FOOTER SECTION (DIVIDER LINE & DATE TRACKING)
    if (showDate) {
        val footerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = (height * 0.005f).coerceIn(2f, 5f)
        }
        val lineY = nextBaseline + (height * 0.008f)
        canvas.drawLine(leftX, lineY, rightX, lineY, footerLinePaint)

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (rawDateTextSize * verticalTextScale).coerceIn(12f, 36f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = if (dateString.hasBengali()) 0f else 0.03f
        }
        val dateY = (lineY + (height * 0.022f) + datePaint.textSize)
            .coerceAtMost(height - framePadding - 4f)
        drawFittedCenteredText(
            canvas = canvas,
            text = dateString,
            centerX = centerX,
            baselineY = dateY,
            maxWidth = contentWidth.toFloat(),
            paint = datePaint
        )
    }
}

// =========================================================================
// DESIGN 1: BOUTIQUE CREST (বুটিক ক্রেস্ট - WITH STORE LOGO #1)
// =========================================================================
private fun drawBoutiqueCrestLabel(
    canvas: Canvas,
    width: Int,
    height: Int,
    cardRect: RectF,
    borderCornerRadius: Float,
    borderPaint: Paint,
    productName: String,
    cleanBarcode: String,
    formattedPrice: String,
    codeType: ProductCodeType,
    showPrice: Boolean,
    showCodeNumber: Boolean,
    effectiveStoreName: String,
    showStoreName: Boolean,
    hasDates: Boolean,
    mfgLabel: String,
    mfgDate: String,
    expLabel: String,
    expDate: String,
    showBanglaDigits: Boolean,
    productNameTextScale: Float,
    priceTextScale: Float,
    codeNumberTextScale: Float,
    storeNameTextScale: Float,
    dateTrackingTextScale: Float,
    barcodeGraphicScale: Float,
    showLogo: Boolean
) {
    // 1. Outer rounded card border
    canvas.drawRoundRect(cardRect, borderCornerRadius, borderCornerRadius, borderPaint)

    val pad = (width * 0.035f).coerceIn(8f, 22f)
    val left = cardRect.left + pad
    val right = cardRect.right - pad
    val contentW = right - left

    // 2. Top Bar: Store Logo Crest + Store Meta on Left, Inverted Price Pill on Right
    val topBarH = (height * 0.22f).coerceIn(24f, 80f)
    val topBarTop = cardRect.top + pad
    val topBarBottom = topBarTop + topBarH

    // Inverted Price Pill (Right side)
    var pricePillW = 0f
    if (showPrice) {
        val (currency, amount) = extractCleanPriceParts(formattedPrice)
        val dispAmt = if (showBanglaDigits) toBanglaDigits(amount) else amount
        val priceText = "$currency $dispAmt"

        val pillH = (topBarH * 0.82f).coerceIn(18f, 50f)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (pillH * 0.56f * priceTextScale).coerceIn(11f, 28f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val measuredTextW = pillTextPaint.measureText(priceText)
        pricePillW = (measuredTextW + pillH * 0.9f).coerceIn(48f, contentW * 0.40f)
        val pillRect = RectF(
            right - pricePillW,
            topBarTop + (topBarH - pillH) / 2f,
            right,
            topBarTop + (topBarH + pillH) / 2f
        )
        val pillRadius = pillH / 2f
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)

        val fm = pillTextPaint.fontMetrics
        val pillTextY = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(priceText, pillRect.centerX(), pillTextY, pillTextPaint)
    }

    // Logo Crest & Store Name (Left side)
    val sealR = (topBarH * 0.45f).coerceIn(12f, 34f)
    val sealCx = left + sealR
    val sealCy = topBarTop + topBarH / 2f
    drawCircularCrestLogo(canvas, sealCx, sealCy, sealR, effectiveStoreName)

    val metaLeft = sealCx + sealR + pad * 0.7f
    val metaMaxW = (right - (if (showPrice) pricePillW + pad * 0.5f else 0f) - metaLeft).coerceAtLeast(30f)
    if (showStoreName && effectiveStoreName.isNotBlank()) {
        val storePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (topBarH * 0.38f * storeNameTextScale).coerceIn(10f, 22f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = if (effectiveStoreName.hasBengali()) 0f else 0.03f
        }
        val storeY = topBarTop + topBarH * 0.46f
        drawFittedText(canvas, effectiveStoreName, metaLeft, storeY, metaMaxW, storePaint, Paint.Align.LEFT)

        val isBengali = showBanglaDigits || isAppBangla() || effectiveStoreName.hasBengali()
        val tagline = if (isBengali) "বুটিক ও লাইফস্টাইল" else "BOUTIQUE & LIFESTYLE"
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (topBarH * 0.22f).coerceIn(8f, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = if (isBengali) 0f else 0.04f
        }
        val tagY = topBarTop + topBarH * 0.82f
        drawFittedText(canvas, tagline, metaLeft, tagY, metaMaxW, tagPaint, Paint.Align.LEFT)
    }

    // 3. Divider Line
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = (height * 0.004f).coerceIn(1.5f, 3f)
    }
    val dividerY = topBarBottom + height * 0.010f
    canvas.drawLine(left, dividerY, right, dividerY, dividerPaint)

    // 4. Multi-line Product Title (2 lines)
    val titleTop = dividerY + height * 0.012f
    val titleH = (height * 0.19f).coerceIn(18f, 55f)
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (titleH * 0.46f * productNameTextScale).coerceIn(12f, 30f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    drawFittedTitle(canvas, productName, left, titleTop, contentW, titleH, titlePaint, Color.BLACK, maxLines = 2)

    // 5. Horizontal Barcode & Spaced Digits
    val footerH = if (hasDates) (height * 0.12f).coerceIn(16f, 34f) else 0f
    val barcodeAreaTop = titleTop + titleH + height * 0.010f
    val barcodeAreaBottom = cardRect.bottom - pad - footerH
    val barcodeAreaH = (barcodeAreaBottom - barcodeAreaTop).coerceAtLeast(26f)

    val digitsH = if (showCodeNumber) (barcodeAreaH * 0.24f).coerceIn(12f, 22f) * codeNumberTextScale else 0f
    val barcodeH = (barcodeAreaH - digitsH).coerceAtLeast(18f)
    val barcodeW = contentW * 0.92f
    val barcodeCx = cardRect.centerX()
    val barcodeCy = barcodeAreaTop + barcodeH / 2f
    drawHorizontalBarcode(canvas, cleanBarcode, codeType, barcodeCx, barcodeCy, barcodeW, barcodeH, barcodeGraphicScale)

    if (showCodeNumber) {
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = digitsH.coerceIn(10f, 22f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        val spacedDigits = (if (showBanglaDigits) toBanglaDigits(cleanBarcode) else cleanBarcode)
            .toCharArray().joinToString(" ")
        val numY = barcodeAreaTop + barcodeH + digitsH * 0.85f
        drawFittedCenteredText(canvas, spacedDigits, barcodeCx, numY, contentW, numberPaint)
    }

    // 6. Footer Bar (Date tracking)
    if (hasDates) {
        val footerLineY = barcodeAreaBottom + 2f
        canvas.drawLine(left, footerLineY, right, footerLineY, dividerPaint)

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (footerH * 0.44f * dateTrackingTextScale).coerceIn(9f, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.02f
        }
        val isBengali = showBanglaDigits || isAppBangla() || effectiveStoreName.hasBengali()
        val authLabel = if (isBengali) "১০০% অরিজিনাল" else "100% AUTHENTIC"
        val dateText = buildString {
            if (mfgDate.isNotBlank()) append("$mfgLabel $mfgDate  •  ")
            append(authLabel)
            if (expDate.isNotBlank()) append("  •  $expLabel $expDate")
        }
        val dateY = footerLineY + (cardRect.bottom - pad - footerLineY) * 0.70f
        drawFittedCenteredText(canvas, dateText, barcodeCx, dateY, contentW, datePaint)
    }
}

// =========================================================================
// DESIGN 2: BRAND HERO SPLIT CARD (ব্র্যান্ড স্প্লিট কার্ড - WITH STORE LOGO #2)
// =========================================================================
private fun drawBrandHeroSplitLabel(
    canvas: Canvas,
    width: Int,
    height: Int,
    cardRect: RectF,
    borderCornerRadius: Float,
    borderPaint: Paint,
    productName: String,
    cleanBarcode: String,
    formattedPrice: String,
    codeType: ProductCodeType,
    showPrice: Boolean,
    showCodeNumber: Boolean,
    effectiveStoreName: String,
    showStoreName: Boolean,
    hasDates: Boolean,
    mfgLabel: String,
    mfgDate: String,
    expLabel: String,
    expDate: String,
    showBanglaDigits: Boolean,
    productNameTextScale: Float,
    priceTextScale: Float,
    codeNumberTextScale: Float,
    storeNameTextScale: Float,
    dateTrackingTextScale: Float,
    barcodeGraphicScale: Float,
    showLogo: Boolean
) {
    // 1. Outer card border
    canvas.drawRoundRect(cardRect, borderCornerRadius, borderCornerRadius, borderPaint)

    val splitX = cardRect.left + cardRect.width() * 0.38f
    // Vertical dividing line between 38% left column and 62% right column
    canvas.drawLine(splitX, cardRect.top, splitX, cardRect.bottom, borderPaint)

    // -------------------------------------------------------------
    // LEFT COLUMN (38%): Brand Shield Logo + Store Name + Hero Price Card
    // -------------------------------------------------------------
    val leftColLeft = cardRect.left + 4f
    val leftColRight = splitX - 4f
    val leftColW = leftColRight - leftColLeft
    val leftColCx = cardRect.left + cardRect.width() * 0.19f

    // Shield Emblem Logo
    val shieldR = (leftColW * 0.28f).coerceIn(14f, 36f)
    val shieldCy = cardRect.top + height * 0.22f
    drawShieldEmblemLogo(canvas, leftColCx, shieldCy, shieldR, effectiveStoreName)

    // Store Name under shield
    if (showStoreName && effectiveStoreName.isNotBlank()) {
        val storePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (height * 0.065f * storeNameTextScale).coerceIn(9f, 18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = if (effectiveStoreName.hasBengali()) 0f else 0.02f
        }
        val storeY = shieldCy + shieldR + height * 0.050f
        drawFittedCenteredText(canvas, effectiveStoreName, leftColCx, storeY, leftColW - 4f, storePaint)
    }

    // Solid Boxed Hero Price Card (Bottom 44% of left column)
    if (showPrice) {
        val (currency, amount) = extractCleanPriceParts(formattedPrice)
        val dispAmt = if (showBanglaDigits) toBanglaDigits(amount) else amount

        val priceCardTop = cardRect.top + height * 0.52f
        val priceCardRect = RectF(
            cardRect.left + 6f,
            priceCardTop,
            splitX - 6f,
            cardRect.bottom - 6f
        )
        val fillBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(priceCardRect, 6f, 6f, fillBlack)

        // Header: PRICE / MRP
        val isBengali = showBanglaDigits || isAppBangla() || effectiveStoreName.hasBengali()
        val headerLbl = if (isBengali) "মূল্য / এমআরপি" else "PRICE / MRP"
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (priceCardRect.height() * 0.20f).coerceIn(8f, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = if (isBengali) 0f else 0.05f
        }
        val headerY = priceCardRect.top + priceCardRect.height() * 0.30f
        drawFittedCenteredText(canvas, headerLbl, priceCardRect.centerX(), headerY, priceCardRect.width() - 6f, headerPaint)

        // Giant white price digits
        val priceText = "$currency $dispAmt"
        val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (priceCardRect.height() * 0.44f * priceTextScale).coerceIn(13f, 32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val priceY = priceCardRect.top + priceCardRect.height() * 0.78f
        drawFittedCenteredText(canvas, priceText, priceCardRect.centerX(), priceY, priceCardRect.width() - 6f, pricePaint)
    }

    // -------------------------------------------------------------
    // RIGHT COLUMN (62%): Product Title + Horizontal Barcode + Dates
    // -------------------------------------------------------------
    val rightColLeft = splitX + 8f
    val rightColRight = cardRect.right - 8f
    val rightColW = rightColRight - rightColLeft
    val rightColCx = splitX + (cardRect.right - splitX) / 2f

    // 1. 2-Line Product Title
    val titleH = (height * 0.22f).coerceIn(20f, 60f)
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (titleH * 0.46f * productNameTextScale).coerceIn(12f, 28f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    drawFittedTitle(canvas, productName, rightColLeft, cardRect.top + 6f, rightColW, titleH, titlePaint, Color.BLACK, maxLines = 2)

    // Divider under title
    val titleDividerY = cardRect.top + titleH + 8f
    val thinDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = (height * 0.003f).coerceIn(1.2f, 2.5f)
    }
    canvas.drawLine(rightColLeft, titleDividerY, rightColRight, titleDividerY, thinDivider)

    // 2. Horizontal Barcode & Digits
    val dateFooterH = if (hasDates) (height * 0.14f).coerceIn(16f, 32f) else 0f
    val barcodeAreaTop = titleDividerY + 6f
    val barcodeAreaBottom = cardRect.bottom - 6f - dateFooterH
    val barcodeAreaH = (barcodeAreaBottom - barcodeAreaTop).coerceAtLeast(24f)

    val digitsH = if (showCodeNumber) (barcodeAreaH * 0.24f).coerceIn(11f, 20f) * codeNumberTextScale else 0f
    val barcodeH = (barcodeAreaH - digitsH).coerceAtLeast(18f)
    val barcodeW = rightColW * 0.92f
    val barcodeCy = barcodeAreaTop + barcodeH / 2f
    drawHorizontalBarcode(canvas, cleanBarcode, codeType, rightColCx, barcodeCy, barcodeW, barcodeH, barcodeGraphicScale)

    if (showCodeNumber) {
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = digitsH.coerceIn(10f, 20f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val spacedDigits = (if (showBanglaDigits) toBanglaDigits(cleanBarcode) else cleanBarcode)
            .toCharArray().joinToString(" ")
        val numY = barcodeAreaTop + barcodeH + digitsH * 0.85f
        drawFittedCenteredText(canvas, spacedDigits, rightColCx, numY, rightColW, numberPaint)
    }

    // 3. Date footer in right column
    if (hasDates) {
        val dateLineY = barcodeAreaBottom + 2f
        canvas.drawLine(rightColLeft, dateLineY, rightColRight, dateLineY, thinDivider)

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (dateFooterH * 0.44f * dateTrackingTextScale).coerceIn(9f, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val dateText = buildString {
            if (mfgDate.isNotBlank()) append("$mfgLabel $mfgDate")
            if (expDate.isNotBlank()) {
                if (isNotEmpty()) append("  |  ")
                append("$expLabel $expDate")
            }
        }
        val dateY = dateLineY + (cardRect.bottom - 6f - dateLineY) * 0.70f
        drawFittedCenteredText(canvas, dateText, rightColCx, dateY, rightColW, datePaint)
    }
}

// =========================================================================
// DESIGN 3: COMPACT MINIMALIST GRID (কমপ্যাক্ট গ্রিড - NO STORE LOGO #1)
// =========================================================================
private fun drawCompactMinimalistLabel(
    canvas: Canvas,
    width: Int,
    height: Int,
    cardRect: RectF,
    borderCornerRadius: Float,
    borderPaint: Paint,
    productName: String,
    cleanBarcode: String,
    formattedPrice: String,
    codeType: ProductCodeType,
    showPrice: Boolean,
    showCodeNumber: Boolean,
    effectiveStoreName: String,
    showStoreName: Boolean,
    hasDates: Boolean,
    mfgLabel: String,
    mfgDate: String,
    expLabel: String,
    expDate: String,
    showBanglaDigits: Boolean,
    productNameTextScale: Float,
    priceTextScale: Float,
    codeNumberTextScale: Float,
    storeNameTextScale: Float,
    dateTrackingTextScale: Float,
    barcodeGraphicScale: Float
) {
    // 1. Outer rounded card border
    canvas.drawRoundRect(cardRect, borderCornerRadius, borderCornerRadius, borderPaint)

    val pad = (width * 0.035f).coerceIn(8f, 22f)
    val left = cardRect.left + pad
    val right = cardRect.right - pad
    val contentW = right - left
    val cx = cardRect.centerX()

    // 2. 2-Line Product Title
    val titleTop = cardRect.top + pad
    val titleH = (height * 0.20f).coerceIn(18f, 52f)
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (titleH * 0.48f * productNameTextScale).coerceIn(12f, 30f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    drawFittedTitle(canvas, productName, left, titleTop, contentW, titleH, titlePaint, Color.BLACK, maxLines = 2)

    // 3. Geometric Diamond Rule (── ◆ ──)
    val ruleY = titleTop + titleH + height * 0.012f
    val diamondSize = (height * 0.022f).coerceIn(4f, 9f)
    val thinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = (height * 0.003f).coerceIn(1.2f, 2.2f)
    }
    canvas.drawLine(left, ruleY, cx - diamondSize * 1.8f, ruleY, thinLinePaint)
    canvas.drawLine(cx + diamondSize * 1.8f, ruleY, right, ruleY, thinLinePaint)

    val diamondPath = Path().apply {
        moveTo(cx, ruleY - diamondSize)
        lineTo(cx + diamondSize, ruleY)
        lineTo(cx, ruleY + diamondSize)
        lineTo(cx - diamondSize, ruleY)
        close()
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    canvas.drawPath(diamondPath, fillPaint)

    // 4. Center Horizontal Barcode & Spaced Digits
    val bottomGridH = (height * 0.25f).coerceIn(24f, 65f)
    val barcodeAreaTop = ruleY + diamondSize + height * 0.010f
    val barcodeAreaBottom = cardRect.bottom - bottomGridH - height * 0.010f
    val barcodeAreaH = (barcodeAreaBottom - barcodeAreaTop).coerceAtLeast(24f)

    val digitsH = if (showCodeNumber) (barcodeAreaH * 0.24f).coerceIn(11f, 20f) * codeNumberTextScale else 0f
    val barcodeH = (barcodeAreaH - digitsH).coerceAtLeast(18f)
    val barcodeW = contentW * 0.92f
    val barcodeCy = barcodeAreaTop + barcodeH / 2f
    drawHorizontalBarcode(canvas, cleanBarcode, codeType, cx, barcodeCy, barcodeW, barcodeH, barcodeGraphicScale)

    if (showCodeNumber) {
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = digitsH.coerceIn(10f, 20f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.10f
        }
        val spacedDigits = (if (showBanglaDigits) toBanglaDigits(cleanBarcode) else cleanBarcode)
            .toCharArray().joinToString(" ")
        val numY = barcodeAreaTop + barcodeH + digitsH * 0.85f
        drawFittedCenteredText(canvas, spacedDigits, cx, numY, contentW, numberPaint)
    }

    // 5. Divider Line above bottom grid
    val gridLineY = cardRect.bottom - bottomGridH
    canvas.drawLine(left, gridLineY, right, gridLineY, thinLinePaint)

    // 6. Bottom Grid: Left Price, Right Store Name & Dates
    val gridTop = gridLineY + 2f
    val priceW = contentW * 0.44f

    if (showPrice) {
        val (currency, amount) = extractCleanPriceParts(formattedPrice)
        val dispAmt = if (showBanglaDigits) toBanglaDigits(amount) else amount
        val priceText = "$currency $dispAmt"

        val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (bottomGridH * 0.52f * priceTextScale).coerceIn(14f, 32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val priceY = gridTop + bottomGridH * 0.65f
        drawFittedText(canvas, priceText, left, priceY, priceW, pricePaint, Paint.Align.LEFT)
    }

    // Right side: Store Name + Dates
    val rightSideLeft = left + priceW + 4f
    val rightSideW = right - rightSideLeft
    if (showStoreName && effectiveStoreName.isNotBlank()) {
        val storePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (bottomGridH * 0.36f * storeNameTextScale).coerceIn(9f, 18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            letterSpacing = if (effectiveStoreName.hasBengali()) 0f else 0.02f
        }
        val storeY = if (hasDates) gridTop + bottomGridH * 0.42f else gridTop + bottomGridH * 0.62f
        drawFittedText(canvas, effectiveStoreName, right, storeY, rightSideW, storePaint, Paint.Align.RIGHT)
    }

    if (hasDates) {
        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (bottomGridH * 0.28f * dateTrackingTextScale).coerceIn(8f, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val dateText = buildString {
            if (mfgDate.isNotBlank()) append("$mfgLabel $mfgDate")
            if (expDate.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("$expLabel $expDate")
            }
        }
        val dateY = gridTop + bottomGridH * 0.85f
        drawFittedText(canvas, dateText, right, dateY, rightSideW, datePaint, Paint.Align.RIGHT)
    }
}

// =========================================================================
// DESIGN 4: SUPERMARKET SHELF-EDGE TAG (শেল্ফ-ট্যাগ - NO STORE LOGO #2)
// =========================================================================
private fun drawShelfEdgeTagLabel(
    canvas: Canvas,
    width: Int,
    height: Int,
    cardRect: RectF,
    borderCornerRadius: Float,
    borderPaint: Paint,
    productName: String,
    cleanBarcode: String,
    formattedPrice: String,
    codeType: ProductCodeType,
    showPrice: Boolean,
    showCodeNumber: Boolean,
    effectiveStoreName: String,
    showStoreName: Boolean,
    hasDates: Boolean,
    mfgLabel: String,
    mfgDate: String,
    expLabel: String,
    expDate: String,
    showBanglaDigits: Boolean,
    productNameTextScale: Float,
    priceTextScale: Float,
    codeNumberTextScale: Float,
    storeNameTextScale: Float,
    dateTrackingTextScale: Float,
    barcodeGraphicScale: Float,
    categoryOrVariant: String
) {
    // 1. Outer card border
    canvas.drawRoundRect(cardRect, borderCornerRadius, borderCornerRadius, borderPaint)

    // 2. Header Bar: Title on Left, Category Pill on Right
    val headerH = (height * 0.22f).coerceIn(20f, 52f)
    val headerBottom = cardRect.top + headerH
    val headerPad = (width * 0.025f).coerceIn(6f, 16f)

    // Category pill on top right
    val isBengali = showBanglaDigits || isAppBangla() || effectiveStoreName.hasBengali()
    val catText = if (categoryOrVariant.isNotBlank()) {
        categoryOrVariant.uppercase()
    } else {
        if (isBengali) "রিটেইল" else "RETAIL"
    }

    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (headerH * 0.36f).coerceIn(8f, 14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = if (isBengali) 0f else 0.04f
    }
    val pillTextW = pillTextPaint.measureText(catText)
    val pillH = (headerH * 0.62f).coerceIn(14f, 32f)
    val pillW = (pillTextW + pillH * 0.9f).coerceIn(40f, cardRect.width() * 0.30f)
    val pillRect = RectF(
        cardRect.right - headerPad - pillW,
        cardRect.top + (headerH - pillH) / 2f,
        cardRect.right - headerPad,
        cardRect.top + (headerH + pillH) / 2f
    )
    canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillPaint)
    val fmPill = pillTextPaint.fontMetrics
    val pillTextY = pillRect.centerY() - (fmPill.ascent + fmPill.descent) / 2f
    canvas.drawText(catText, pillRect.centerX(), pillTextY, pillTextPaint)

    // Product Title on left of header
    val titleMaxW = pillRect.left - cardRect.left - headerPad * 2f
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (headerH * 0.44f * productNameTextScale).coerceIn(12f, 26f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    drawFittedTitle(
        canvas = canvas,
        text = productName,
        left = cardRect.left + headerPad,
        top = cardRect.top + 2f,
        width = titleMaxW,
        height = headerH - 4f,
        paint = titlePaint,
        textColor = Color.BLACK,
        maxLines = 2,
        alignment = Layout.Alignment.ALIGN_NORMAL
    )

    // Solid divider across full width under header
    canvas.drawLine(cardRect.left, headerBottom, cardRect.right, headerBottom, borderPaint)

    // 3. Body Section (Split 60% Left Barcode / 40% Right Price)
    val footerH = (height * 0.17f).coerceIn(18f, 44f)
    val bodyBottom = cardRect.bottom - footerH
    val bodyH = bodyBottom - headerBottom
    val colDividerX = cardRect.left + cardRect.width() * 0.60f

    // Vertical dividing line
    val thinDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = (height * 0.003f).coerceIn(1.2f, 2.5f)
    }
    canvas.drawLine(colDividerX, headerBottom, colDividerX, bodyBottom, thinDivider)

    // Left Column (60%): Extra-wide Horizontal Barcode for scanner guns
    val leftColLeft = cardRect.left + headerPad
    val leftColW = colDividerX - leftColLeft - headerPad * 0.5f
    val leftColCx = leftColLeft + leftColW / 2f

    val digitsH = if (showCodeNumber) (bodyH * 0.22f).coerceIn(11f, 20f) * codeNumberTextScale else 0f
    val barcodeH = (bodyH - digitsH - 8f).coerceAtLeast(20f)
    val barcodeW = leftColW * 0.94f
    val barcodeCy = headerBottom + barcodeH / 2f + 4f
    drawHorizontalBarcode(canvas, cleanBarcode, codeType, leftColCx, barcodeCy, barcodeW, barcodeH, barcodeGraphicScale)

    if (showCodeNumber) {
        val numberPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = digitsH.coerceIn(10f, 20f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
        }
        val spacedDigits = (if (showBanglaDigits) toBanglaDigits(cleanBarcode) else cleanBarcode)
            .toCharArray().joinToString(" ")
        val numY = headerBottom + barcodeH + digitsH * 0.85f + 4f
        drawFittedCenteredText(canvas, spacedDigits, leftColCx, numY, leftColW, numberPaint)
    }

    // Right Column (40%): Giant Retail Price
    val rightColW = cardRect.right - colDividerX - headerPad
    val rightColCx = colDividerX + (cardRect.right - colDividerX) / 2f

    if (showPrice) {
        val (currency, amount) = extractCleanPriceParts(formattedPrice)
        val dispAmt = if (showBanglaDigits) toBanglaDigits(amount) else amount

        // RETAIL PRICE header
        val retailLbl = if (isBengali) "খুচরা মূল্য" else "RETAIL PRICE"
        val retailPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (bodyH * 0.16f).coerceIn(8f, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = if (isBengali) 0f else 0.04f
        }
        val retailY = headerBottom + bodyH * 0.24f
        drawFittedCenteredText(canvas, retailLbl, rightColCx, retailY, rightColW, retailPaint)

        // Huge Price Digits
        val priceText = "$currency $dispAmt"
        val pricePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (bodyH * 0.44f * priceTextScale).coerceIn(14f, 36f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val priceY = headerBottom + bodyH * 0.65f
        drawFittedCenteredText(canvas, priceText, rightColCx, priceY, rightColW, pricePaint)

        // INCL. ALL TAXES subtext
        val taxesLbl = if (isBengali) "সর্বমোট কর সহ" else "INCL. ALL TAXES"
        val taxPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (bodyH * 0.14f).coerceIn(7f, 11f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = if (isBengali) 0f else 0.03f
        }
        val taxY = headerBottom + bodyH * 0.90f
        drawFittedCenteredText(canvas, taxesLbl, rightColCx, taxY, rightColW, taxPaint)
    }

    // 4. Inverted Solid Black Footer Bar across entire card width
    val footerRect = RectF(cardRect.left, bodyBottom, cardRect.right, cardRect.bottom)
    val footerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    canvas.drawRect(footerRect, footerFill)

    // White text inside footer
    val footerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (footerH * 0.44f).coerceIn(9f, 16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val fmFooter = footerTextPaint.fontMetrics
    val footerTextY = footerRect.centerY() - (fmFooter.ascent + fmFooter.descent) / 2f

    // Store Name on Left
    if (showStoreName && effectiveStoreName.isNotBlank()) {
        footerTextPaint.textSize = (footerH * 0.44f * storeNameTextScale).coerceIn(9f, 16f)
        drawFittedText(
            canvas = canvas,
            text = effectiveStoreName,
            x = cardRect.left + headerPad,
            baselineY = footerTextY,
            maxWidth = cardRect.width() * 0.45f,
            paint = footerTextPaint,
            align = Paint.Align.LEFT
        )
    }

    // Dates on Right
    if (hasDates) {
        footerTextPaint.textSize = (footerH * 0.42f * dateTrackingTextScale).coerceIn(8f, 14f)
        val dateText = buildString {
            if (mfgDate.isNotBlank()) append("$mfgLabel $mfgDate")
            if (expDate.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append("$expLabel $expDate")
            }
        }
        drawFittedText(
            canvas = canvas,
            text = dateText,
            x = cardRect.right - headerPad,
            baselineY = footerTextY,
            maxWidth = cardRect.width() * 0.50f,
            paint = footerTextPaint,
            align = Paint.Align.RIGHT
        )
    }
}

private fun getStoreInitials(storeName: String): String {
    val clean = storeName.trim()
    if (clean.isBlank()) return "MK"
    val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> {
            val c1 = words[0].first().uppercaseChar()
            val c2 = words[1].first().uppercaseChar()
            "$c1$c2"
        }
        clean.length >= 2 -> clean.take(2).uppercase()
        else -> clean.uppercase()
    }
}

private fun drawCircularCrestLogo(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    storeName: String
) {
    val r = radius.coerceAtLeast(8f)
    // Outer circle
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = (r * 0.10f).coerceIn(1.5f, 3.5f)
    }
    canvas.drawCircle(cx, cy, r, outerPaint)

    // Inner dashed circle
    val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = (r * 0.05f).coerceIn(1f, 2f)
        pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
    }
    canvas.drawCircle(cx, cy, r * 0.82f, dashedPaint)

    // Center filled black disc
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    val discR = r * 0.62f
    canvas.drawCircle(cx, cy, discR, discPaint)

    // Monogram text in center
    val initials = getStoreInitials(storeName)
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (discR * 0.90f).coerceIn(8f, 26f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val fontMetrics = textPaint.fontMetrics
    val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText(initials, cx, textY, textPaint)
}

private fun drawShieldEmblemLogo(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    storeName: String
) {
    val r = radius.coerceAtLeast(10f)
    val shieldPath = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.85f, cy - r * 0.70f)
        lineTo(cx + r * 0.85f, cy + r * 0.15f)
        lineTo(cx, cy + r)
        lineTo(cx - r * 0.85f, cy + r * 0.15f)
        lineTo(cx - r * 0.85f, cy - r * 0.70f)
        close()
    }

    // Outer shield stroke
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = (r * 0.09f).coerceIn(1.5f, 3.5f)
    }
    canvas.drawPath(shieldPath, strokePaint)

    // Inner filled shield
    val innerR = r * 0.80f
    val innerShieldPath = Path().apply {
        moveTo(cx, cy - innerR)
        lineTo(cx + innerR * 0.85f, cy - innerR * 0.70f)
        lineTo(cx + innerR * 0.85f, cy + innerR * 0.15f)
        lineTo(cx, cy + innerR)
        lineTo(cx - innerR * 0.85f, cy + innerR * 0.15f)
        lineTo(cx - innerR * 0.85f, cy - innerR * 0.70f)
        close()
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    canvas.drawPath(innerShieldPath, fillPaint)

    // Monogram text in center
    val initials = getStoreInitials(storeName)
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (innerR * 0.75f).coerceIn(8f, 24f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val fontMetrics = textPaint.fontMetrics
    val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText(initials, cx, textY, textPaint)
}


// =========================================================================
// VECTOR HELPERS (DIAMOND LOGO, LAUREL WREATH, DATES, HERO PRICE)
// =========================================================================

private fun drawDiamondMonogramLogo(
    canvas: Canvas,
    rect: RectF,
    storeName: String,
    paint: Paint
) {
    val cx = rect.centerX()
    val cy = rect.top + rect.height() * 0.44f
    val r = (minOf(rect.width(), rect.height()) * 0.38f).coerceAtLeast(14f)

    // Outer diamond
    val outerPath = Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r, cy)
        close()
    }
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = (r * 0.08f).coerceIn(1.8f, 4f)
    paint.pathEffect = null
    paint.color = Color.BLACK
    canvas.drawPath(outerPath, paint)

    // Inner dotted diamond
    val dottedR = r * 0.84f
    val dottedPath = Path().apply {
        moveTo(cx, cy - dottedR)
        lineTo(cx + dottedR, cy)
        lineTo(cx, cy + dottedR)
        lineTo(cx - dottedR, cy)
        close()
    }
    paint.strokeWidth = (r * 0.045f).coerceIn(1f, 2.5f)
    paint.pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f)
    canvas.drawPath(dottedPath, paint)
    paint.pathEffect = null

    // Center solid black diamond
    val innerR = r * 0.68f
    val innerPath = Path().apply {
        moveTo(cx, cy - innerR)
        lineTo(cx + innerR, cy)
        lineTo(cx, cy + innerR)
        lineTo(cx - innerR, cy)
        close()
    }
    paint.style = Paint.Style.FILL
    paint.color = Color.BLACK
    canvas.drawPath(innerPath, paint)

    // Monogram text inside center diamond
    val firstWord = storeName.split(" ").firstOrNull { it.isNotBlank() } ?: "POS"
    val initials = if (firstWord.length > 5) firstWord.take(4) else firstWord
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (innerR * 0.72f).coerceIn(10f, 30f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val textY = cy + textPaint.textSize * 0.35f
    canvas.drawText(initials.uppercase(), cx, textY, textPaint)

    // Store sub-text below diamond
    val words = storeName.split(" ").filter { it.isNotBlank() }
    val subText = if (words.size > 1) words.drop(1).joinToString(" ") else storeName
    if (subText.isNotBlank()) {
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (rect.height() * 0.16f).coerceIn(9f, 20f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.04f
        }
        val subY = rect.bottom - 4f
        drawFittedCenteredText(canvas, subText.uppercase(), cx, subY, rect.width() - 4f, subPaint)
    }
}

private fun drawLaurelWreath(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    scale: Float,
    isLeft: Boolean,
    paint: Paint
) {
    paint.style = Paint.Style.FILL
    paint.color = Color.BLACK

    val direction = if (isLeft) 1f else -1f
    canvas.save()
    canvas.translate(cx, cy)
    canvas.scale(scale * direction, scale)

    val stem = Path().apply {
        moveTo(14f, 8f)
        quadTo(6f, 0f, 0f, -10f)
    }
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.6f
    canvas.drawPath(stem, paint)

    paint.style = Paint.Style.FILL
    val leaf1 = Path().apply {
        moveTo(0f, -10f)
        quadTo(4f, -14f, 0f, -18f)
        quadTo(-4f, -14f, 0f, -10f)
    }
    canvas.drawPath(leaf1, paint)

    val leaf2 = Path().apply {
        moveTo(3f, -5f)
        quadTo(9f, -8f, 10f, -13f)
        quadTo(5f, -10f, 3f, -5f)
    }
    canvas.drawPath(leaf2, paint)

    val leaf3 = Path().apply {
        moveTo(7f, 1f)
        quadTo(13f, -1f, 15f, -6f)
        quadTo(9f, -3f, 7f, 1f)
    }
    canvas.drawPath(leaf3, paint)

    val leaf4 = Path().apply {
        moveTo(11f, 6f)
        quadTo(17f, 5f, 18f, 0f)
        quadTo(13f, 3f, 11f, 6f)
    }
    canvas.drawPath(leaf4, paint)

    canvas.restore()
}

private fun drawVerticalDigits(
    canvas: Canvas,
    digits: String,
    x: Float,
    topY: Float,
    bottomY: Float,
    paint: TextPaint
) {
    val clean = digits.filter { it.isDigit() || it in '\u09E6'..'\u09EF' }
    if (clean.isEmpty()) return
    val step = (bottomY - topY) / clean.length
    for (i in clean.indices) {
        val charY = topY + (i + 0.85f) * step
        canvas.drawText(clean[i].toString(), x, charY, paint)
    }
}

private fun createBarcodeBitmap(
    cleanBarcode: String,
    codeType: ProductCodeType,
    width: Int,
    height: Int
): Bitmap? {
    return try {
        val format = when (codeType) {
            ProductCodeType.QR_CODE -> BarcodeFormat.QR_CODE
            ProductCodeType.EAN_13 -> if (isValidEan13(cleanBarcode)) BarcodeFormat.EAN_13 else BarcodeFormat.CODE_128
            ProductCodeType.EAN_8 -> if (isValidEan8(cleanBarcode)) BarcodeFormat.EAN_8 else BarcodeFormat.CODE_128
            ProductCodeType.CODE_39 -> BarcodeFormat.CODE_39
            ProductCodeType.BARCODE -> BarcodeFormat.CODE_128
        }
        val hints = mapOf(EncodeHintType.MARGIN to if (codeType == ProductCodeType.QR_CODE) 1 else 4)
        val matrix = try {
            MultiFormatWriter().encode(cleanBarcode, format, width, height, hints)
        } catch (e: Exception) {
            MultiFormatWriter().encode(cleanBarcode, BarcodeFormat.CODE_128, width, height, hints)
        }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        bmp
    } catch (e: Exception) {
        null
    }
}

private fun drawVerticalBarcode(
    canvas: Canvas,
    cleanBarcode: String,
    codeType: ProductCodeType,
    cx: Float,
    cy: Float,
    availableW: Float,
    availableH: Float,
    scale: Float = 1f
) {
    if (codeType == ProductCodeType.QR_CODE) {
        val size = (minOf(availableW, availableH) * 0.90f * scale).toInt().coerceAtLeast(20)
        val qrBmp = createBarcodeBitmap(cleanBarcode, codeType, size, size)
        if (qrBmp != null) {
            canvas.drawBitmap(qrBmp, cx - size / 2f, cy - size / 2f, null)
            qrBmp.recycle()
        }
    } else {
        val barcodeLength = (availableH * 0.92f * scale).toInt().coerceAtLeast(28)
        val barcodeThickness = (availableW * 0.88f).toInt().coerceAtLeast(18)
        val barcodeBmp = createBarcodeBitmap(cleanBarcode, codeType, barcodeLength, barcodeThickness)
        if (barcodeBmp != null) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(-90f)
            canvas.drawBitmap(barcodeBmp, -barcodeLength / 2f, -barcodeThickness / 2f, null)
            canvas.restore()
            barcodeBmp.recycle()
        }
    }
}

private fun drawHorizontalBarcode(
    canvas: Canvas,
    cleanBarcode: String,
    codeType: ProductCodeType,
    cx: Float,
    cy: Float,
    availableW: Float,
    availableH: Float,
    scale: Float = 1f
) {
    val barcodeW = (availableW * 0.92f * scale).toInt().coerceAtLeast(24)
    val barcodeH = (availableH * 0.85f).toInt().coerceAtLeast(18)
    val barcodeBmp = createBarcodeBitmap(cleanBarcode, codeType, barcodeW, barcodeH)
    if (barcodeBmp != null) {
        canvas.drawBitmap(barcodeBmp, cx - barcodeW / 2f, cy - barcodeH / 2f, null)
        barcodeBmp.recycle()
    }
}

private fun extractCleanPriceParts(formattedPrice: String): Pair<String, String> {
    val clean = formattedPrice
        .replace("Price:", "", ignoreCase = true)
        .replace("MRP Price:", "", ignoreCase = true)
        .replace("Wholesale Price:", "", ignoreCase = true)
        .replace("MRP:", "", ignoreCase = true)
        .replace("Wholesale:", "", ignoreCase = true)
        .replace("মূল্য:", "")
        .replace("এমআরপি:", "")
        .replace("এমআরপি মূল্য:", "")
        .replace("পাইকারি:", "")
        .replace("পাইকারি মূল্য:", "")
        .trim()
    val currency = if (clean.startsWith("৳") || clean.startsWith("Tk", ignoreCase = true)) "৳" else "৳"
    val amount = clean.removePrefix("৳").removePrefix("Tk").removePrefix("TK").trim()
    return currency to amount.ifBlank { "0" }
}

private fun drawHeroPrice(
    canvas: Canvas,
    formattedPrice: String,
    centerX: Float,
    headerY: Float,
    heroBaselineY: Float,
    maxWidth: Float,
    maxHeight: Float,
    priceScale: Float,
    showBanglaDigits: Boolean,
    headerText: String = "PRICE"
) {
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = (maxHeight * 0.016f).coerceIn(1.5f, 3.5f)
    }

    val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (maxHeight * 0.15f).coerceIn(10f, 24f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.15f
    }
    val headerWidth = headerPaint.measureText(headerText)
    canvas.drawText(headerText, centerX, headerY, headerPaint)

    val ruleMargin = 10f
    val leftRuleEnd = (centerX - headerWidth / 2f - ruleMargin).coerceAtLeast(centerX - maxWidth / 2f)
    val leftRuleStart = centerX - maxWidth / 2f + 4f
    if (leftRuleEnd > leftRuleStart) {
        canvas.drawLine(leftRuleStart, headerY - headerPaint.textSize * 0.35f, leftRuleEnd, headerY - headerPaint.textSize * 0.35f, linePaint)
    }
    val rightRuleStart = (centerX + headerWidth / 2f + ruleMargin).coerceAtMost(centerX + maxWidth / 2f)
    val rightRuleEnd = centerX + maxWidth / 2f - 4f
    if (rightRuleEnd > rightRuleStart) {
        canvas.drawLine(rightRuleStart, headerY - headerPaint.textSize * 0.35f, rightRuleEnd, headerY - headerPaint.textSize * 0.35f, linePaint)
    }

    val (curr, amount) = extractCleanPriceParts(formattedPrice)
    val displayCurr = if (curr.isNotBlank()) curr else "৳"
    val displayAmount = if (showBanglaDigits) toBanglaDigits(amount) else amount

    val currPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (maxHeight * 0.48f * priceScale).coerceIn(18f, 75f)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    val amountPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (maxHeight * 0.60f * priceScale).coerceIn(24f, 98f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    var currWidth = currPaint.measureText(displayCurr)
    var amountWidth = amountPaint.measureText(displayAmount)
    val totalHeroWidth = currWidth + 6f + amountWidth

    if (totalHeroWidth > maxWidth && totalHeroWidth > 0f) {
        val factor = (maxWidth / totalHeroWidth).coerceIn(0.55f, 1f)
        currPaint.textSize *= factor
        amountPaint.textSize *= factor
        currWidth = currPaint.measureText(displayCurr)
        amountWidth = amountPaint.measureText(displayAmount)
    }

    val startX = centerX - (currWidth + 6f + amountWidth) / 2f
    canvas.drawText(displayCurr, startX, heroBaselineY, currPaint)
    canvas.drawText(displayAmount, startX + currWidth + 6f, heroBaselineY, amountPaint)
}

private fun drawPillDateFooter(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    mfgLabel: String,
    mfgDate: String,
    expLabel: String,
    expDate: String,
    showBanglaDigits: Boolean,
    dateScale: Float,
    strokePaint: Paint,
    iconStyle: Int
) {
    val midX = (left + right) / 2f
    val footerH = bottom - top
    val centerY = (top + bottom) / 2f

    // Center dividing line
    canvas.drawLine(midX, top, midX, bottom, strokePaint)

    val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = (footerH * 0.42f * dateScale).coerceIn(10f, 24f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        letterSpacing = 0.04f
    }

    // Left Pill (MFG)
    val leftCenterX = (left + midX) / 2f
    val mfgText = if (mfgDate.isNotBlank()) {
        val displayD = if (showBanglaDigits) toBanglaDigits(mfgDate) else mfgDate
        "$mfgLabel $displayD"
    } else ""

    if (mfgText.isNotBlank()) {
        val mfgTextW = datePaint.measureText(mfgText)
        val iconR = (footerH * 0.25f).coerceIn(7f, 18f)
        val totalW = iconR * 2 + 8f + mfgTextW
        val startX = leftCenterX - totalW / 2f

        drawDateBadgeIcon(canvas, startX + iconR, centerY, iconR, isGear = true, iconStyle = iconStyle)
        val textY = centerY + datePaint.textSize * 0.35f
        canvas.drawText(mfgText, startX + iconR * 2 + 8f, textY, datePaint)
    }

    // Right Pill (EXP)
    val rightCenterX = (midX + right) / 2f
    val expText = if (expDate.isNotBlank()) {
        val displayD = if (showBanglaDigits) toBanglaDigits(expDate) else expDate
        "$expLabel $displayD"
    } else ""

    if (expText.isNotBlank()) {
        val expTextW = datePaint.measureText(expText)
        val iconR = (footerH * 0.25f).coerceIn(7f, 18f)
        val totalW = iconR * 2 + 8f + expTextW
        val startX = rightCenterX - totalW / 2f

        drawDateBadgeIcon(canvas, startX + iconR, centerY, iconR, isGear = false, iconStyle = iconStyle)
        val textY = centerY + datePaint.textSize * 0.35f
        canvas.drawText(expText, startX + iconR * 2 + 8f, textY, datePaint)
    }
}

private fun drawDateBadgeIcon(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    r: Float,
    isGear: Boolean,
    iconStyle: Int
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = (r * 0.18f).coerceIn(1.5f, 3f)
    }

    when (iconStyle) {
        1 -> {
            // Rounded rect outline box
            val boxR = r * 1.35f
            paint.style = Paint.Style.STROKE
            val rect = RectF(cx - boxR, cy - boxR, cx + boxR, cy + boxR)
            canvas.drawRoundRect(rect, 4f, 4f, paint)

            if (isGear) {
                drawGearVector(canvas, cx, cy, r * 0.85f, paint)
            } else {
                drawCalendarVector(canvas, cx, cy, r * 0.85f, paint)
            }
        }
        2 -> {
            // Circle outline box
            val circleR = r * 1.35f
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy, circleR, paint)

            if (isGear) {
                drawGearVector(canvas, cx, cy, r * 0.85f, paint)
            } else {
                drawClockVector(canvas, cx, cy, r * 0.85f, paint)
            }
        }
        3 -> {
            // Square outline box
            val boxR = r * 1.35f
            paint.style = Paint.Style.STROKE
            val rect = RectF(cx - boxR, cy - boxR, cx + boxR, cy + boxR)
            canvas.drawRect(rect, paint)

            if (isGear) {
                drawGearVector(canvas, cx, cy, r * 0.85f, paint)
            } else {
                drawCalendarVector(canvas, cx, cy, r * 0.85f, paint)
            }
        }
    }
}

private fun drawGearVector(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    canvas.drawCircle(cx, cy, r * 0.60f, paint)

    paint.style = Paint.Style.FILL
    for (i in 0 until 8) {
        val angle = i * 45.0 * Math.PI / 180.0
        val tx = (cx + (r * 0.82f) * Math.cos(angle)).toFloat()
        val ty = (cy + (r * 0.82f) * Math.sin(angle)).toFloat()
        canvas.drawCircle(tx, ty, r * 0.18f, paint)
    }
    paint.color = Color.WHITE
    canvas.drawCircle(cx, cy, r * 0.30f, paint)
    paint.color = Color.BLACK
}

private fun drawCalendarVector(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    val w = r * 1.4f
    val h = r * 1.3f
    val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    canvas.drawRoundRect(rect, 3f, 3f, paint)

    // Inner clock circle
    canvas.drawCircle(cx, cy + r * 0.12f, r * 0.40f, paint)
    // Clock hands
    canvas.drawLine(cx, cy + r * 0.12f, cx, cy - r * 0.12f, paint)
    canvas.drawLine(cx, cy + r * 0.12f, cx + r * 0.20f, cy + r * 0.12f, paint)
}

private fun drawClockVector(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
    paint.style = Paint.Style.STROKE
    canvas.drawCircle(cx, cy, r * 0.85f, paint)
    canvas.drawLine(cx, cy, cx, cy - r * 0.55f, paint)
    canvas.drawLine(cx, cy, cx + r * 0.42f, cy + r * 0.15f, paint)
}

internal fun formatProductCodeLabelPrice(formattedPrice: String): String {
    val value = formattedPrice.trim()
    return when {
        value.startsWith("Price:", ignoreCase = true) ||
        value.startsWith("MRP Price:", ignoreCase = true) ||
        value.startsWith("Wholesale Price:", ignoreCase = true) ||
        value.startsWith("MRP:", ignoreCase = true) ||
        value.startsWith("Wholesale:", ignoreCase = true) ||
        value.contains("|") ||
        value.startsWith("মূল্য:") ||
        value.startsWith("এমআরপি:") ||
        value.startsWith("এমআরপি মূল্য:") ||
        value.startsWith("পাইকারি:") ||
        value.startsWith("পাইকারি মূল্য:") -> value
        else -> "Price: $value"
    }
}

fun formatProductCodeLabelPriceString(
    tier: ProductCodePriceTier,
    sellingPrice: Double,
    mrpPrice: Double,
    wholesalePrice: Double,
    showUnit: Boolean = false,
    showBanglaDigits: Boolean = false
): String {
    val unitStr = if (showUnit) {
        if (showBanglaDigits) "/পিস" else "/Pcs"
    } else ""

    fun formatVal(amount: Double): String {
        return formatTaka(amount, isBangla = showBanglaDigits)
    }

    return when (tier) {
        ProductCodePriceTier.SELLING -> {
            val priceStr = formatVal(sellingPrice)
            if (showBanglaDigits) "মূল্য: $priceStr$unitStr" else "Price: $priceStr$unitStr"
        }
        ProductCodePriceTier.MRP -> {
            if (mrpPrice > 0) {
                val priceStr = formatVal(mrpPrice)
                if (showBanglaDigits) "এমআরপি মূল্য: $priceStr$unitStr" else "MRP Price: $priceStr$unitStr"
            } else {
                val priceStr = formatVal(sellingPrice)
                if (showBanglaDigits) "মূল্য: $priceStr$unitStr" else "Price: $priceStr$unitStr"
            }
        }
        ProductCodePriceTier.WHOLESALE -> {
            if (wholesalePrice > 0) {
                val priceStr = formatVal(wholesalePrice)
                if (showBanglaDigits) "পাইকারি মূল্য: $priceStr$unitStr" else "Wholesale Price: $priceStr$unitStr"
            } else {
                val priceStr = formatVal(sellingPrice)
                if (showBanglaDigits) "মূল্য: $priceStr$unitStr" else "Price: $priceStr$unitStr"
            }
        }
        ProductCodePriceTier.DUAL -> {
            if (mrpPrice > 0) {
                val mrpVal = formatVal(mrpPrice)
                val sellVal = formatVal(sellingPrice)
                if (showBanglaDigits) {
                    "এমআরপি: $mrpVal | অফার: $sellVal$unitStr"
                } else {
                    "MRP: $mrpVal | Offer: $sellVal$unitStr"
                }
            } else {
                val priceStr = formatVal(sellingPrice)
                if (showBanglaDigits) "মূল্য: $priceStr$unitStr" else "Price: $priceStr$unitStr"
            }
        }
    }
}

private fun drawFittedText(
    canvas: Canvas,
    text: String,
    x: Float,
    baselineY: Float,
    maxWidth: Float,
    paint: Paint,
    align: Paint.Align = Paint.Align.CENTER
) {
    val originalSize = paint.textSize
    val originalAlign = paint.textAlign
    paint.textAlign = align
    val measuredWidth = paint.measureText(text)
    if (measuredWidth > maxWidth && measuredWidth > 0f) {
        paint.textSize = (originalSize * maxWidth / measuredWidth).coerceAtLeast(originalSize * 0.62f)
    }
    val fittedText = TextUtils.ellipsize(
        text,
        TextPaint(paint),
        maxWidth,
        TextUtils.TruncateAt.END
    ).toString()
    canvas.drawText(fittedText, x, baselineY, paint)
    paint.textAlign = originalAlign
    paint.textSize = originalSize
}

private fun drawFittedCenteredText(
    canvas: Canvas,
    text: String,
    centerX: Float,
    baselineY: Float,
    maxWidth: Float,
    paint: Paint
) {
    drawFittedText(canvas, text, centerX, baselineY, maxWidth, paint, Paint.Align.CENTER)
}

private fun safeProductFileStem(productName: String, codeType: ProductCodeType): String {
    val safeName = productName.trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "Product" }
    val typeName = if (codeType == ProductCodeType.QR_CODE) "QR" else "Barcode"
    return "${safeName}_${typeName}_${System.currentTimeMillis()}"
}

internal fun sharedProductFileStem(
    productName: String,
    timestampMillis: Long = System.currentTimeMillis()
): String {
    val safeName = productName
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '.', '-')
        .ifBlank { "Product" }
    return "${safeName}_$timestampMillis"
}

fun saveProductCodeLabelToPictures(
    context: Context,
    bitmap: Bitmap,
    productName: String,
    codeType: ProductCodeType
): Uri? {
    val fileName = "${safeProductFileStem(productName, codeType)}.png"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Techyfi POS/Labels")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: return null
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val directory = File(pictures, "Techyfi POS/Labels").apply { mkdirs() }
            val file = File(directory, fileName)
            FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
            Uri.fromFile(file)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun cacheProductCodeLabelPdf(
    context: Context,
    bitmap: Bitmap,
    productName: String,
    codeType: ProductCodeType,
    widthMm: Float,
    heightMm: Float,
    copies: Int = 1
): Pair<File, Uri>? {
    return try {
        val directory = File(context.cacheDir, "product_codes").apply { mkdirs() }
        val file = File(directory, "${sharedProductFileStem(productName)}.pdf")
        val (pageWidthPoints, pageHeightPoints) = productCodeLabelPdfPageSize(widthMm, heightMm)
        val document = PdfDocument()
        val totalCopies = copies.coerceIn(1, 500)
        try {
            for (i in 1..totalCopies) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, i).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, pageWidthPoints.toFloat(), pageHeightPoints.toFloat()),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                document.finishPage(page)
            }
            FileOutputStream(file).use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        file to uri
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun shareProductCodeLabelPdf(context: Context, pdfUri: Uri, productName: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, productName)
            clipData = ClipData.newUri(context.contentResolver, productName, pdfUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_label_pdf)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val launchContext = context.findActivity() ?: context
        if (launchContext !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchContext.startActivity(chooser)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun clampCopies(copies: Int?, min: Int = 1, max: Int = 500): Int {
    if (copies == null) return min
    return copies.coerceIn(min, max)
}

fun stockToPrintCopies(stockQty: Double, minCopies: Int = 1, maxCopies: Int = 500): Int {
    if (stockQty <= 0.0) return minCopies
    return kotlin.math.ceil(stockQty).toInt().coerceIn(minCopies, maxCopies)
}

fun formatStockQuantityBadge(stockQty: Double): String {
    return if (stockQty % 1.0 == 0.0) {
        stockQty.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", stockQty).trimEnd('0').trimEnd('.')
    }
}

fun resolveLabelDimensionsMm(
    labelSize: ProductCodeLabelSize,
    customWidthMm: Float,
    customHeightMm: Float
): Pair<Float, Float> {
    return if (labelSize == ProductCodeLabelSize.CUSTOM) {
        val w = customWidthMm.coerceIn(15f, 150f)
        val h = customHeightMm.coerceIn(10f, 200f)
        Pair(w, h)
    } else {
        Pair(labelSize.widthMm, labelSize.heightMm)
    }
}

fun resolveConfiguredLabelDimensionsMm(context: Context): Pair<Float, Float> {
    val prefs = context.getSharedPreferences("product_code_image_settings", Context.MODE_PRIVATE)
    val sizeName = prefs.getString("label_size", ProductCodeLabelSize.SIZE_50_X_30.name)
    val size = ProductCodeLabelSize.entries.firstOrNull { it.name == sizeName } ?: ProductCodeLabelSize.SIZE_50_X_30
    val customW = prefs.getFloat("custom_width_mm", 50f)
    val customH = prefs.getFloat("custom_height_mm", 30f)
    return resolveLabelDimensionsMm(size, customW, customH)
}

private fun String.hasBengali(): Boolean = any { it in '\u0980'..'\u09FF' }

data class LabelStyleSettings(
    val labelDesign: BarcodeLabelDesign = BarcodeLabelDesign.STANDARD_RETAIL,
    val labelSize: ProductCodeLabelSize = ProductCodeLabelSize.SIZE_50_X_30,
    val customWidthMm: Float = 50f,
    val customHeightMm: Float = 30f,
    val showPrice: Boolean = true,
    val priceTier: ProductCodePriceTier = ProductCodePriceTier.SELLING,
    val showCodeNumber: Boolean = true,
    val showStoreName: Boolean = false,
    val storeName: String = "",
    val showDateTracking: Boolean = false,
    val mfgOrPkgType: String = "Mfg",
    val mfgOrPkgDate: String = "",
    val expDate: String = "",
    val showUnit: Boolean = false,
    val showBanglaDigits: Boolean = false,
    val productNameTextScale: Float = 1f,
    val priceTextScale: Float = 1f,
    val codeNumberTextScale: Float = 1f,
    val storeNameTextScale: Float = 1f,
    val dateTrackingTextScale: Float = 1f,
    val barcodeGraphicScale: Float = 1f,
    val showExtraField: Boolean = false,
    val extraFieldLabel: String = "",
    val extraFieldValue: String = "",
    val extraFieldTextScale: Float = 1f,
    val showLogo: Boolean = false
)

data class LabelPresetTemplate(
    val id: String,
    val name: String,
    val nameRes: Int? = null,
    val style: LabelStyleSettings
)

fun getDefaultLabelPresets(): List<LabelPresetTemplate> = listOf(
    LabelPresetTemplate(
        id = "standard_retail",
        name = "Standard Retail",
        nameRes = R.string.label_preset_standard,
        style = LabelStyleSettings(
            labelDesign = BarcodeLabelDesign.STANDARD_RETAIL,
            labelSize = ProductCodeLabelSize.SIZE_50_X_30,
            showPrice = true,
            showCodeNumber = true,
            showStoreName = true
        )
    ),
    LabelPresetTemplate(
        id = "boutique_crest",
        name = "Boutique & Lifestyle",
        nameRes = R.string.barcode_design_boutique_crest,
        style = LabelStyleSettings(
            labelDesign = BarcodeLabelDesign.BOUTIQUE_CREST,
            labelSize = ProductCodeLabelSize.SIZE_50_X_30,
            showPrice = true,
            showCodeNumber = true,
            showStoreName = true,
            showLogo = true
        )
    ),
    LabelPresetTemplate(
        id = "brand_hero_split",
        name = "Brand Hero Split",
        nameRes = R.string.barcode_design_brand_split,
        style = LabelStyleSettings(
            labelDesign = BarcodeLabelDesign.BRAND_HERO_SPLIT,
            labelSize = ProductCodeLabelSize.SIZE_60_X_40,
            showPrice = true,
            showCodeNumber = true,
            showStoreName = true,
            showLogo = true,
            showDateTracking = true
        )
    ),
    LabelPresetTemplate(
        id = "compact_minimalist",
        name = "Compact Grid Tag",
        nameRes = R.string.barcode_design_compact_minimalist,
        style = LabelStyleSettings(
            labelDesign = BarcodeLabelDesign.COMPACT_MINIMALIST,
            labelSize = ProductCodeLabelSize.SIZE_40_X_25,
            showPrice = true,
            showCodeNumber = true,
            showStoreName = true
        )
    ),
    LabelPresetTemplate(
        id = "shelf_edge_tag",
        name = "Supermarket Shelf-Edge",
        nameRes = R.string.barcode_design_shelf_edge,
        style = LabelStyleSettings(
            labelDesign = BarcodeLabelDesign.SHELF_EDGE_TAG,
            labelSize = ProductCodeLabelSize.SIZE_60_X_40,
            showPrice = true,
            showCodeNumber = true,
            showStoreName = true,
            showDateTracking = true
        )
    )
)

fun loadLabelStyleSettings(
    preferences: SharedPreferences,
    defaultStoreName: String = ""
): LabelStyleSettings {
    val designName = preferences.getString("barcode_label_design", BarcodeLabelDesign.STANDARD_RETAIL.name)
    val labelDesign = BarcodeLabelDesign.entries.firstOrNull { it.name == designName } ?: BarcodeLabelDesign.STANDARD_RETAIL
    val sizeName = preferences.getString("label_size", ProductCodeLabelSize.SIZE_50_X_30.name)
    val labelSize = ProductCodeLabelSize.entries.firstOrNull { it.name == sizeName } ?: ProductCodeLabelSize.SIZE_50_X_30
    val tierName = preferences.getString("price_tier", ProductCodePriceTier.SELLING.name)
    val priceTier = ProductCodePriceTier.entries.firstOrNull { it.name == tierName } ?: ProductCodePriceTier.SELLING
    val storeName = if (preferences.contains("store_name")) {
        preferences.getString("store_name", "") ?: ""
    } else {
        defaultStoreName
    }
    val showUnit = if (preferences.contains("show_unit")) {
        preferences.getBoolean("show_unit", false)
    } else {
        preferences.getBoolean("show_price_unit", false)
    }
    val extraFieldLabel = preferences.getString("extra_field_label", preferences.getString("extra_field_title", "") ?: "") ?: ""
    return LabelStyleSettings(
        labelDesign = labelDesign,
        labelSize = labelSize,
        customWidthMm = preferences.getFloat("custom_width_mm", 50f).coerceIn(15f, 150f),
        customHeightMm = preferences.getFloat("custom_height_mm", 30f).coerceIn(10f, 200f),
        showPrice = preferences.getBoolean("show_price", true),
        priceTier = priceTier,
        showCodeNumber = preferences.getBoolean("show_code_number", true),
        showStoreName = preferences.getBoolean("show_store_name", false),
        storeName = storeName,
        showDateTracking = preferences.getBoolean("show_date_tracking", false),
        mfgOrPkgType = preferences.getString("mfg_or_pkg_type", "Mfg") ?: "Mfg",
        mfgOrPkgDate = preferences.getString("mfg_or_pkg_date", "") ?: "",
        expDate = preferences.getString("exp_date", "") ?: "",
        showUnit = showUnit,
        showBanglaDigits = preferences.getBoolean("show_bangla_digits", false),
        productNameTextScale = preferences.getFloat("product_name_text_scale", 1f).coerceIn(0.6f, 1.8f),
        priceTextScale = preferences.getFloat("price_text_scale", 1f).coerceIn(0.6f, 1.8f),
        codeNumberTextScale = preferences.getFloat("code_number_text_scale", 1f).coerceIn(0.6f, 1.8f),
        storeNameTextScale = preferences.getFloat("store_name_text_scale", 1f).coerceIn(0.6f, 1.8f),
        dateTrackingTextScale = preferences.getFloat("date_tracking_text_scale", 1f).coerceIn(0.6f, 1.8f),
        barcodeGraphicScale = preferences.getFloat("barcode_graphic_scale", 1f).coerceIn(0.5f, 2.0f),
        showExtraField = preferences.getBoolean("show_extra_field", false),
        extraFieldLabel = extraFieldLabel,
        extraFieldValue = preferences.getString("extra_field_value", "") ?: "",
        extraFieldTextScale = preferences.getFloat("extra_field_text_scale", 1f).coerceIn(0.6f, 1.8f),
        showLogo = preferences.getBoolean("show_logo", false)
    )
}

fun saveLabelStyleSettings(
    editor: SharedPreferences.Editor,
    settings: LabelStyleSettings
) {
    editor
        .putString("barcode_label_design", settings.labelDesign.name)
        .putString("label_size", settings.labelSize.name)
        .putBoolean("show_store_name", settings.showStoreName)
        .putString("store_name", settings.storeName)
        .putBoolean("show_price", settings.showPrice)
        .putString("price_tier", settings.priceTier.name)
        .putBoolean("show_code_number", settings.showCodeNumber)
        .putBoolean("show_date_tracking", settings.showDateTracking)
        .putString("mfg_or_pkg_type", settings.mfgOrPkgType)
        .putString("mfg_or_pkg_date", settings.mfgOrPkgDate)
        .putString("exp_date", settings.expDate)
        .putBoolean("show_unit", settings.showUnit)
        .putBoolean("show_price_unit", settings.showUnit)
        .putBoolean("show_bangla_digits", settings.showBanglaDigits)
        .putFloat("product_name_text_scale", settings.productNameTextScale)
        .putFloat("price_text_scale", settings.priceTextScale)
        .putFloat("code_number_text_scale", settings.codeNumberTextScale)
        .putFloat("store_name_text_scale", settings.storeNameTextScale)
        .putFloat("date_tracking_text_scale", settings.dateTrackingTextScale)
        .putFloat("barcode_graphic_scale", settings.barcodeGraphicScale)
        .putBoolean("show_extra_field", settings.showExtraField)
        .putString("extra_field_label", settings.extraFieldLabel)
        .putString("extra_field_title", settings.extraFieldLabel)
        .putString("extra_field_value", settings.extraFieldValue)
        .putFloat("extra_field_text_scale", settings.extraFieldTextScale)
        .putBoolean("show_logo", settings.showLogo)
    if (settings.customWidthMm in 15f..150f) {
        editor.putFloat("custom_width_mm", settings.customWidthMm)
    }
    if (settings.customHeightMm in 10f..200f) {
        editor.putFloat("custom_height_mm", settings.customHeightMm)
    }
}

data class BatchBuildResult(
    val items: List<ProductLabelItem>,
    val totalRequestedLabels: Int,
    val totalIncludedLabels: Int,
    val cutProducts: List<String>
)

fun buildBatchLabelItemsResult(
    candidateProducts: List<Product>,
    copiesModeByStock: Boolean,
    batchCopies: Int,
    priceTier: ProductCodePriceTier,
    showUnit: Boolean,
    showBanglaDigits: Boolean,
    useProductDates: Boolean,
    mfgOrPkgType: String,
    mfgOrPkgDate: String,
    expDate: String,
    customQuantities: Map<String, Int> = emptyMap(),
    maxTotalLabels: Int = 500,
    isCustomPerProduct: Boolean = false,
    cutFormat: String? = null
): BatchBuildResult {
    var remainingAllowed = maxTotalLabels
    val items = mutableListOf<ProductLabelItem>()
    val cutProducts = mutableListOf<String>()
    var totalRequested = 0
    var totalIncluded = 0
    val effectiveCopies = clampCopies(batchCopies)

    for (prod in candidateProducts) {
        val requestedQty = when {
            isCustomPerProduct -> (customQuantities[prod.barcode] ?: 1).coerceIn(1, 500)
            customQuantities.containsKey(prod.barcode) -> customQuantities[prod.barcode]!!.coerceIn(1, 500)
            copiesModeByStock -> if (prod.stockQty <= 0.0) 0 else stockToPrintCopies(prod.stockQty)
            else -> effectiveCopies
        }
        totalRequested += requestedQty
        if (remainingAllowed <= 0) {
            cutProducts.add(prod.name)
            continue
        }
        val qty = requestedQty.coerceAtMost(remainingAllowed)
        if (qty < requestedQty) {
            val cutStr = if (cutFormat != null) String.format(cutFormat, requestedQty - qty) else "(${requestedQty - qty} cut)"
            cutProducts.add("${prod.name} $cutStr")
        }
        if (qty <= 0) continue
        remainingAllowed -= qty
        totalIncluded += qty

        val priceString = formatProductCodeLabelPriceString(
            tier = priceTier,
            sellingPrice = prod.sellingPrice,
            mrpPrice = prod.mrpPrice,
            wholesalePrice = prod.wholesalePrice,
            showUnit = showUnit,
            showBanglaDigits = showBanglaDigits
        )
        val itemMfgType = if (useProductDates) (prod.mfgOrPkgType ?: "Mfg") else mfgOrPkgType
        val itemMfgDate = if (useProductDates) prod.mfgOrPkgDate.orEmpty() else mfgOrPkgDate
        val itemExpDate = if (useProductDates) prod.expiryDate.orEmpty() else expDate
        items.add(
            ProductLabelItem(
                productName = prod.name,
                barcode = prod.barcode,
                formattedPrice = priceString,
                quantity = qty,
                mfgOrPkgType = itemMfgType,
                mfgOrPkgDate = itemMfgDate,
                expDate = itemExpDate
            )
        )
    }
    return BatchBuildResult(
        items = items,
        totalRequestedLabels = totalRequested,
        totalIncludedLabels = totalIncluded,
        cutProducts = cutProducts
    )
}

fun buildBatchLabelItems(
    candidateProducts: List<Product>,
    copiesModeByStock: Boolean,
    batchCopies: Int,
    priceTier: ProductCodePriceTier,
    showUnit: Boolean,
    showBanglaDigits: Boolean,
    useProductDates: Boolean,
    mfgOrPkgType: String,
    mfgOrPkgDate: String,
    expDate: String,
    customQuantities: Map<String, Int> = emptyMap(),
    maxTotalLabels: Int = 500
): List<ProductLabelItem> {
    return buildBatchLabelItemsResult(
        candidateProducts = candidateProducts,
        copiesModeByStock = copiesModeByStock,
        batchCopies = batchCopies,
        priceTier = priceTier,
        showUnit = showUnit,
        showBanglaDigits = showBanglaDigits,
        useProductDates = useProductDates,
        mfgOrPkgType = mfgOrPkgType,
        mfgOrPkgDate = mfgOrPkgDate,
        expDate = expDate,
        customQuantities = customQuantities,
        maxTotalLabels = maxTotalLabels
    ).items
}

data class ProductLabelItem(
    val productName: String,
    val barcode: String,
    val formattedPrice: String,
    val quantity: Int = 1,
    val mfgOrPkgType: String = "",
    val mfgOrPkgDate: String = "",
    val expDate: String = "",
    val extraFieldLabel: String = "",
    val extraFieldValue: String = ""
)

fun cacheBatchProductCodeLabelsPdf(
    context: Context,
    items: List<ProductLabelItem>,
    codeType: ProductCodeType,
    style: LabelStyleSettings
): Pair<File, Uri>? = cacheBatchProductCodeLabelsPdf(
    context = context,
    items = items,
    codeType = codeType,
    labelSize = style.labelSize,
    customWidthMm = style.customWidthMm,
    customHeightMm = style.customHeightMm,
    showPrice = style.showPrice,
    showCodeNumber = style.showCodeNumber,
    showStoreName = style.showStoreName,
    storeName = style.storeName,
    productNameTextScale = style.productNameTextScale,
    priceTextScale = style.priceTextScale,
    codeNumberTextScale = style.codeNumberTextScale,
    storeNameTextScale = style.storeNameTextScale,
    showDateTracking = style.showDateTracking,
    mfgOrPkgType = style.mfgOrPkgType,
    mfgOrPkgDate = style.mfgOrPkgDate,
    expDate = style.expDate,
    dateTrackingTextScale = style.dateTrackingTextScale,
    barcodeGraphicScale = style.barcodeGraphicScale,
    showExtraField = style.showExtraField,
    extraFieldLabel = style.extraFieldLabel,
    extraFieldValue = style.extraFieldValue,
    extraFieldTextScale = style.extraFieldTextScale,
    showLogo = style.showLogo
)

fun cacheBatchProductCodeLabelsPdf(
    context: Context,
    items: List<ProductLabelItem>,
    codeType: ProductCodeType = ProductCodeType.BARCODE,
    labelSize: ProductCodeLabelSize = ProductCodeLabelSize.SIZE_50_X_30,
    customWidthMm: Float = 50f,
    customHeightMm: Float = 30f,
    showPrice: Boolean = true,
    showCodeNumber: Boolean = true,
    showStoreName: Boolean = false,
    storeName: String = "",
    productNameTextScale: Float = 1f,
    priceTextScale: Float = 1f,
    codeNumberTextScale: Float = 1f,
    storeNameTextScale: Float = 1f,
    showDateTracking: Boolean = false,
    mfgOrPkgType: String = "Mfg",
    mfgOrPkgDate: String = "",
    expDate: String = "",
    dateTrackingTextScale: Float = 1f,
    barcodeGraphicScale: Float = 1f,
    showExtraField: Boolean = false,
    extraFieldLabel: String = "",
    extraFieldValue: String = "",
    extraFieldTextScale: Float = 1f,
    showLogo: Boolean = false
): Pair<File, Uri>? {
    return try {
        val validItems = items.filter { it.barcode.isNotBlank() && it.quantity > 0 }
        if (validItems.isEmpty()) return null

        val directory = File(context.cacheDir, "product_codes").apply { mkdirs() }
        val file = File(directory, "batch_labels_${System.currentTimeMillis()}.pdf")
        val (effectiveWidthMm, effectiveHeightMm) = resolveLabelDimensionsMm(labelSize, customWidthMm, customHeightMm)
        val (pageWidthPoints, pageHeightPoints) = productCodeLabelPdfPageSize(effectiveWidthMm, effectiveHeightMm)

        val document = PdfDocument()
        var pageNumber = 0
        try {
            for (item in validItems) {
                val itemMfgType = item.mfgOrPkgType.ifBlank { mfgOrPkgType }.ifBlank { "Mfg" }
                val itemMfgDate = item.mfgOrPkgDate.ifBlank { mfgOrPkgDate }
                val itemExpDate = item.expDate.ifBlank { expDate }
                val itemExtraFieldLabel = item.extraFieldLabel.ifBlank { extraFieldLabel }
                val itemExtraFieldValue = item.extraFieldValue.ifBlank { extraFieldValue }
                val bitmap = generateProductCodeLabelBitmap(
                    productName = item.productName,
                    barcode = item.barcode,
                    formattedPrice = item.formattedPrice,
                    codeType = codeType,
                    labelSize = labelSize,
                    showPrice = showPrice,
                    showCodeNumber = showCodeNumber,
                    customWidthMm = customWidthMm,
                    customHeightMm = customHeightMm,
                    productNameTextScale = productNameTextScale,
                    priceTextScale = priceTextScale,
                    codeNumberTextScale = codeNumberTextScale,
                    showStoreName = showStoreName,
                    storeName = storeName,
                    storeNameTextScale = storeNameTextScale,
                    showDateTracking = showDateTracking,
                    mfgOrPkgType = itemMfgType,
                    mfgOrPkgDate = itemMfgDate,
                    expDate = itemExpDate,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale,
                    showExtraField = showExtraField || itemExtraFieldLabel.isNotBlank(),
                    extraFieldLabel = itemExtraFieldLabel,
                    extraFieldValue = itemExtraFieldValue,
                    extraFieldTextScale = extraFieldTextScale,
                    showLogo = showLogo
                ) ?: continue

                val copies = item.quantity.coerceIn(1, 500)
                for (copy in 0 until copies) {
                    pageNumber++
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, pageNumber).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(0f, 0f, pageWidthPoints.toFloat(), pageHeightPoints.toFloat()),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    document.finishPage(page)
                }
                bitmap.recycle()
            }
            if (pageNumber == 0) {
                return null
            }
            FileOutputStream(file).use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        file to uri
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Estimates whether a 1D barcode on the given label width results in a narrow bar (module) width
 * of less than 2 printer dots at the specified DPI (typically 203 or 300).
 * Commercial 1D scanners require at least 2 dots (>= 10 mils at 203 DPI) for reliable scanning.
 * When true, callers should display a density warning and recommend a larger label size or QR Code.
 */
fun isBarcodeTooDenseForLabel(
    barcode: String,
    codeType: ProductCodeType,
    widthMm: Float,
    dpi: Int = 203
): Boolean {
    if (codeType == ProductCodeType.QR_CODE) return false
    val clean = barcode.trim()
    if (clean.isEmpty()) return false
    val dotsPerMm = dpi / 25.4f
    // Printable barcode graphic width in mm is roughly widthMm minus horizontal padding (~12%)
    val printableWidthMm = (widthMm * 0.88f).coerceAtLeast(10f)
    val totalDots = printableWidthMm * dotsPerMm
    val estimatedModules = when (codeType) {
        ProductCodeType.EAN_13 -> 115
        ProductCodeType.EAN_8 -> 87
        ProductCodeType.CODE_39 -> (clean.length + 2) * 16
        else -> {
            if (clean.all(Char::isDigit)) {
                // Code 128 Mode C packs two digits per symbol character (11 modules each)
                val pairs = (clean.length + 1) / 2
                pairs * 11 + 35
            } else {
                (clean.length + 2) * 11 + 25
            }
        }
    }
    val moduleWidthDots = totalDots / estimatedModules
    return moduleWidthDots < 2.0f
}


