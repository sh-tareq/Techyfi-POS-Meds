package com.example.techyfipos.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

enum class ProductCodeType { BARCODE, QR_CODE }

enum class ProductCodePriceTier {
    SELLING,
    MRP,
    WHOLESALE,
    DUAL
}

enum class ProductCodeLabelSize(
    val widthMm: Float,
    val heightMm: Float,
    val displayName: String
) {
    SIZE_25_X_15(25f, 15f, "25 × 15 mm"),
    SIZE_30_X_20(30f, 20f, "30 × 20 mm"),
    SIZE_40_X_25(40f, 25f, "40 × 25 mm"),
    SIZE_45_X_35(45f, 35f, "45 × 35 mm"),
    SIZE_50_X_30(50f, 30f, "50 × 30 mm"),
    SIZE_60_X_40(60f, 40f, "60 × 40 mm"),
    SIZE_100_X_150(100f, 150f, "100 × 150 mm"),
    CUSTOM(0f, 0f, "Custom size")
}

fun productCodeLabelPixelSize(
    labelSize: ProductCodeLabelSize,
    customWidthMm: Float = 50f,
    customHeightMm: Float = 30f
): Pair<Int, Int> {
    val widthMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customWidthMm else labelSize.widthMm
    val heightMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customHeightMm else labelSize.heightMm
    val rawWidth = (widthMm.coerceIn(15f, 150f) * 20f).roundToInt()
    val rawHeight = (heightMm.coerceIn(10f, 200f) * 20f).roundToInt()
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
    barcodeGraphicScale: Float = 1f
): Bitmap? {
    val cleanBarcode = toEnglishDigits(barcode.trim())
    if (cleanBarcode.isBlank()) return null

    return try {
        val (width, height) = productCodeLabelPixelSize(labelSize, customWidthMm, customHeightMm)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val framePadding = (width * 0.035f).coerceIn(8f, 28f)
        val frameRect = RectF(
            framePadding,
            framePadding,
            width - framePadding,
            height - framePadding
        )
        val borderCornerRadius = (width * 0.045f).coerceIn(12f, 36f)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = (width * 0.008f).coerceIn(2.5f, 7f)
        }
        canvas.drawRoundRect(frameRect, borderCornerRadius, borderCornerRadius, borderPaint)

        val horizontalPadding = (width * 0.08f).toInt()
        val contentWidth = width - horizontalPadding * 2
        var top = (height * 0.07f).toInt()
        val centerX = width / 2f
        val leftX = horizontalPadding.toFloat()
        val rightX = (width - horizontalPadding).toFloat()

        val effectiveStoreName = storeName.trim().uppercase()
        val mfgLabel = if (mfgOrPkgType == "Pkg") "PKG:" else "MFG:"
        val dateString = buildString {
            if (mfgOrPkgDate.isNotBlank()) {
                append("$mfgLabel $mfgOrPkgDate")
            }
            if (expDate.isNotBlank()) {
                if (isNotEmpty()) append("    |    ")
                append("EXP: $expDate")
            }
        }.trim()
        val showDate = showDateTracking && dateString.isNotBlank()

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

        val fixedVerticalSpace = height * (
            0.07f + 0.04f + 0.05f +
                (if (showPrice) 0.035f else 0f) +
                (if (showCodeNumber) 0.035f else 0f) +
                (if (showStoreName && effectiveStoreName.isNotBlank()) 0.035f else 0f) +
                (if (showDate) 0.045f else 0f)
            ) + 32f
        val requestedTextSpace = rawNameTextSize * 2.2f +
            (if (showPrice) rawPriceTextSize else 0f) +
            (if (showCodeNumber) rawNumberTextSize else 0f) +
            (if (showStoreName && effectiveStoreName.isNotBlank()) rawStoreTextSize else 0f) +
            (if (showDate) rawDateTextSize else 0f)
        val verticalTextScale = if (requestedTextSpace > 0f) {
            ((height - fixedVerticalSpace) / requestedTextSpace).coerceIn(0.55f, 1f)
        } else {
            1f
        }

        // 1. PRODUCT NAME (UPPERCASE)
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = rawNameTextSize * verticalTextScale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.02f
        }
        val uppercaseName = productName.ifBlank { "Product Name" }.uppercase()
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
        val format = if (codeType == ProductCodeType.QR_CODE) BarcodeFormat.QR_CODE else BarcodeFormat.CODE_128
        val hints = mapOf(EncodeHintType.MARGIN to if (codeType == ProductCodeType.QR_CODE) 1 else 6)
        val matrix = MultiFormatWriter().encode(cleanBarcode, format, codeWidth, codeHeight, hints)
        val codeBitmap = Bitmap.createBitmap(codeWidth, codeHeight, Bitmap.Config.ARGB_8888)
        for (y in 0 until codeHeight) {
            for (x in 0 until codeWidth) {
                codeBitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
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
            val spacedDigits = cleanBarcode.toCharArray().joinToString(" ")
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
                letterSpacing = 0.04f
            }
            val storeY = (nextBaseline + storePaint.textSize)
                .coerceAtMost(height - bottomPadding.toFloat())
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

        // 7. FOOTER SECTION (DIVIDER LINE & DATE TRACKING: MFG: 23-05-2025  |  EXP: 23-05-2027)
        if (showDate) {
            val footerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                strokeWidth = (height * 0.005f).coerceIn(2f, 5f)
            }
            val lineY = nextBaseline + (height * 0.008f)
            canvas.drawLine(leftX, lineY, rightX, lineY, footerLinePaint)

            val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = dateTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.03f
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

        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
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
            val effective = if (mrpPrice > 0) mrpPrice else sellingPrice
            val priceStr = formatVal(effective)
            if (showBanglaDigits) "এমআরপি মূল্য: $priceStr$unitStr" else "MRP Price: $priceStr$unitStr"
        }
        ProductCodePriceTier.WHOLESALE -> {
            val effective = if (wholesalePrice > 0) wholesalePrice else sellingPrice
            val priceStr = formatVal(effective)
            if (showBanglaDigits) "পাইকারি মূল্য: $priceStr$unitStr" else "Wholesale Price: $priceStr$unitStr"
        }
        ProductCodePriceTier.DUAL -> {
            val effectiveMrp = if (mrpPrice > 0) mrpPrice else (sellingPrice * 1.15)
            val mrpVal = formatVal(effectiveMrp)
            val sellVal = formatVal(sellingPrice)
            if (showBanglaDigits) {
                "এমআরপি: $mrpVal | অফার: $sellVal$unitStr"
            } else {
                "MRP: $mrpVal | Offer: $sellVal$unitStr"
            }
        }
    }
}

private fun drawFittedCenteredText(
    canvas: Canvas,
    text: String,
    centerX: Float,
    baselineY: Float,
    maxWidth: Float,
    paint: Paint
) {
    val originalSize = paint.textSize
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
    canvas.drawText(fittedText, centerX, baselineY, paint)
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
    heightMm: Float
): Pair<File, Uri>? {
    return try {
        val directory = File(context.cacheDir, "product_codes").apply { mkdirs() }
        val file = File(directory, "${sharedProductFileStem(productName)}.pdf")
        val (pageWidthPoints, pageHeightPoints) = productCodeLabelPdfPageSize(widthMm, heightMm)
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, pageWidthPoints.toFloat(), pageHeightPoints.toFloat()),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            document.finishPage(page)
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
        val chooser = Intent.createChooser(intent, "Share label PDF").apply {
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

data class ProductLabelItem(
    val productName: String,
    val barcode: String,
    val formattedPrice: String,
    val quantity: Int = 1
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
    barcodeGraphicScale: Float = 1f
): Pair<File, Uri>? {
    return try {
        val validItems = items.filter { it.barcode.isNotBlank() && it.quantity > 0 }
        if (validItems.isEmpty()) return null

        val directory = File(context.cacheDir, "product_codes").apply { mkdirs() }
        val file = File(directory, "batch_labels_${System.currentTimeMillis()}.pdf")
        val effectiveWidthMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customWidthMm else labelSize.widthMm
        val effectiveHeightMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customHeightMm else labelSize.heightMm
        val (pageWidthPoints, pageHeightPoints) = productCodeLabelPdfPageSize(effectiveWidthMm, effectiveHeightMm)

        val document = PdfDocument()
        var pageNumber = 0
        try {
            for (item in validItems) {
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
                    mfgOrPkgType = mfgOrPkgType,
                    mfgOrPkgDate = mfgOrPkgDate,
                    expDate = expDate,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale
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

