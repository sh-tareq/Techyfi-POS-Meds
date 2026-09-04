package com.example.techyfipos.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.ui.viewmodel.SalesReportData
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

data class BillLineItem(
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val unit: String = "Pcs",
    val note: String? = null
)

data class BillData(
    val saleId: Long,
    val timestampMillis: Long,
    val shopName: String,
    val shopAddress: String,
    val shopPhone: String,
    val items: List<BillLineItem>,
    val subtotal: Double,
    val discountValue: Double,
    val grandTotal: Double,
    val paymentMethod: String,
    val customerName: String,
    val customerPhone: String,
    val amountPaid: Double,
    val changeAmount: Double,
    val walletPhone: String = "",
    val cardType: String = "",
    val cardLastFour: String = "",
    val splitPaymentJson: String? = null,
    val logoUri: String = "",
    val facebookUrl: String = "",
    val qrCodeInfo: String = "",
    val cashierName: String = "Admin",
    val settingsData: InvoiceSettingsData = InvoiceSettingsData(),
    val taxLabel: String = "VAT",
    val taxValue: Double = 0.0,
    /** Customer's total outstanding balance after this sale, including any due portion just added. Only meaningful when [hasDuePortion] is true. */
    val totalOutstandingDue: Double = 0.0
) {
    /** True when this sale (fully or partially, via Split) put money on the customer's due account. */
    fun hasDuePortion(): Boolean {
        val method = paymentMethod.trim()
        if (method.equals("Due", ignoreCase = true)) return true
        if (method.equals("Split", ignoreCase = true) && !splitPaymentJson.isNullOrBlank()) {
            return try {
                val arr = org.json.JSONArray(splitPaymentJson)
                (0 until arr.length()).any { i ->
                    val obj = arr.getJSONObject(i)
                    obj.optString("method").equals("Due", ignoreCase = true) && obj.optDouble("amount", 0.0) > 0.0
                }
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    fun getFormattedPaymentMethod(): String {
        val method = paymentMethod.trim()
        val phone = walletPhone.trim()
        val type = cardType.trim()
        val lastFour = cardLastFour.trim()

        if (method.equals("Split", ignoreCase = true) && !splitPaymentJson.isNullOrBlank()) {
            return try {
                val arr = org.json.JSONArray(splitPaymentJson)
                val parts = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val m = obj.getString("method")
                    val a = obj.getDouble("amount")
                    val formattedAmt = if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()
                    parts.add("$m: ৳$formattedAmt")
                }
                "Split (${parts.joinToString(", ")})"
            } catch (e: Exception) {
                "Split Payment"
            }
        }

        return when {
            method.equals("bKash", ignoreCase = true) ||
            method.equals("Rocket", ignoreCase = true) ||
            method.equals("Nagad", ignoreCase = true) -> {
                if (phone.isNotBlank()) "$method ($phone)" else method
            }
            method.equals("Card", ignoreCase = true) -> {
                when {
                    type.isNotBlank() && lastFour.isNotBlank() -> "$type ($lastFour)"
                    lastFour.isNotBlank() -> "Card ($lastFour)"
                    type.isNotBlank() -> type
                    else -> "Card"
                }
            }
            method.equals("Visa", ignoreCase = true) ||
            method.equals("Mastercard", ignoreCase = true) ||
            method.equals("Amex", ignoreCase = true) -> {
                if (lastFour.isNotBlank()) "$method ($lastFour)" else method
            }
            phone.isNotBlank() -> "$method ($phone)"
            lastFour.isNotBlank() -> {
                val cardBrand = if (type.isNotBlank()) type else method
                "$cardBrand ($lastFour)"
            }
            else -> method
        }
    }
}

private const val APP_BRAND = "Techyfi POS"

/** Converted monochrome thresholding bitmap helper for sharp 1-bit thermal print output */
fun convertToMonochromeThresholdBitmap(src: Bitmap, targetWidthDots: Int = 380): Bitmap {
    val aspectRatio = src.height.toFloat() / src.width.toFloat()
    val targetHeightDots = (targetWidthDots * aspectRatio).toInt().coerceIn(60, 300)
    val scaled = Bitmap.createScaledBitmap(src, targetWidthDots, targetHeightDots, true)

    val bwBitmap = Bitmap.createBitmap(targetWidthDots, targetHeightDots, Bitmap.Config.ARGB_8888)
    for (x in 0 until targetWidthDots) {
        for (y in 0 until targetHeightDots) {
            val pixel = scaled.getPixel(x, y)
            val alpha = (pixel shr 24) and 0xFF
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            // Grayscale luminance formula
            val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
            // Threshold logic (transparent or light pixels become white; dark pixels become black)
            val isDark = alpha > 50 && luminance < 160
            bwBitmap.setPixel(x, y, if (isDark) Color.BLACK else Color.WHITE)
        }
    }
    return bwBitmap
}

fun wrapItemName(name: String, maxCharsPerLine: Int = 18): List<String> {
    if (name.length <= maxCharsPerLine) return listOf(name)
    val result = mutableListOf<String>()
    var current = name.trim()
    while (current.length > maxCharsPerLine) {
        var breakIdx = current.lastIndexOf(' ', maxCharsPerLine)
        if (breakIdx <= 0) {
            breakIdx = maxCharsPerLine
        }
        result.add(current.substring(0, breakIdx).trim())
        current = current.substring(breakIdx).trim()
    }
    if (current.isNotEmpty()) {
        result.add(current)
    }
    return result
}

/**
 * Wraps item name into multiple lines based on actual pixel width (not character count).
 * This handles Bengali/Unicode text correctly since those glyphs are wider than ASCII.
 */
fun wrapItemNameByPixelWidth(name: String, maxWidthPx: Float, paint: Paint): List<String> {
    if (paint.measureText(name) <= maxWidthPx) return listOf(name)
    val result = mutableListOf<String>()
    var remaining = name.trim()
    while (remaining.isNotEmpty()) {
        if (paint.measureText(remaining) <= maxWidthPx) {
            result.add(remaining)
            break
        }
        // Find the longest substring that fits within maxWidthPx
        var breakIdx = remaining.length
        // Try to break at a space first
        for (i in remaining.length downTo 1) {
            if (paint.measureText(remaining, 0, i) <= maxWidthPx) {
                breakIdx = i
                break
            }
        }
        // Try to find a space near the break point for cleaner wrapping
        val spaceIdx = remaining.lastIndexOf(' ', breakIdx)
        if (spaceIdx > 0) {
            breakIdx = spaceIdx
        }
        result.add(remaining.substring(0, breakIdx).trim())
        remaining = remaining.substring(breakIdx).trim()
    }
    return result
}

fun formatPaymentMethodBangla(method: String): String {
    return when {
        method.contains("Cash", ignoreCase = true) -> method.replace("Cash", "নগদ", ignoreCase = true)
        method.contains("bKash", ignoreCase = true) -> method.replace("bKash", "বিকাশ", ignoreCase = true)
        method.contains("Nagad", ignoreCase = true) -> method.replace("Nagad", "নগদ", ignoreCase = true)
        method.contains("Rocket", ignoreCase = true) -> method.replace("Rocket", "রকেট", ignoreCase = true)
        method.contains("Card", ignoreCase = true) -> method.replace("Card", "কার্ড", ignoreCase = true)
        method.contains("Due", ignoreCase = true) -> method.replace("Due", "বাকি", ignoreCase = true)
        method.contains("Split", ignoreCase = true) -> method.replace("Split", "যৌথ পরিশোধ", ignoreCase = true)
        else -> method
    }
}

fun buildBillReceiptText(bill: BillData, receiptLanguage: String = "EN"): String {
    val isBn = receiptLanguage.equals("BN", ignoreCase = true)
    val date = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US).format(Date(bill.timestampMillis))
    val settings = bill.settingsData
    val sb = StringBuilder()
    sb.appendLine(bill.shopName.ifBlank { settings.storeName })
    if (settings.showAddress && bill.shopAddress.isNotBlank()) sb.appendLine(bill.shopAddress)
    if (settings.showPhone && bill.shopPhone.isNotBlank()) sb.appendLine(bill.shopPhone)
    if (settings.showFacebookInHeader && settings.facebookUrl.isNotBlank()) sb.appendLine(settings.facebookUrl)
    sb.appendLine("==========================================")
    sb.appendLine(if (isBn) "চালান নং : #${bill.saleId}" else "Invoice  : #${bill.saleId}")
    sb.appendLine(if (isBn) "তারিখ    : $date" else "Date     : $date")
    if (settings.showCashier) {
        val cashierVal = bill.cashierName.ifBlank { settings.cashierName }
        sb.appendLine(if (isBn) "ক্যাশিয়ার : $cashierVal" else "Cashier  : $cashierVal")
    }
    sb.appendLine("------------------------------------------")
    sb.appendLine(if (isBn) "আইটেম                 পরিমাণ    দর     মোট" else "Item                 Qty   Price   Total")
    sb.appendLine("------------------------------------------")
    bill.items.forEach { item ->
        val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
        val priceStr = formatTaka(item.unitPrice).replace("৳", "").trim()
        val totalStr = formatTaka(item.lineTotal).replace("৳", "").trim()

        val nameLines = wrapItemName(item.name, 18)
        nameLines.forEachIndexed { index, line ->
            if (index == 0) {
                sb.appendLine(String.format(Locale.US, "%-18s %4s %7s %7s", line, qtyStr, priceStr, totalStr))
            } else {
                sb.appendLine(String.format(Locale.US, "%-18s", line))
            }
        }
    }
    sb.appendLine("------------------------------------------")
    sb.appendLine(if (isBn) "উপমোট: ${formatTaka(bill.subtotal)}" else "Subtotal: ${formatTaka(bill.subtotal)}")
    if (settings.showDiscount && bill.discountValue > 0.0) {
        sb.appendLine(if (isBn) "ছাড়: -${formatTaka(bill.discountValue)}" else "Discount: -${formatTaka(bill.discountValue)}")
    }
    if (bill.taxValue > 0.0) {
        val taxLbl = if (isBn) (if (settings.taxInclusive) "ভ্যাট (সহ)" else bill.taxLabel.ifBlank { "কর" }) else bill.taxLabel.ifBlank { "Tax" }
        sb.appendLine("$taxLbl: ${formatTaka(bill.taxValue)}")
    }
    sb.appendLine("==========================================")
    sb.appendLine(if (isBn) "সর্বমোট: ${formatTaka(bill.grandTotal)}" else "GRAND TOTAL: ${formatTaka(bill.grandTotal)}")
    sb.appendLine("==========================================")
    if (settings.showCustomerDetails && (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank())) {
        sb.appendLine(if (isBn) "গ্রাহক    : ${bill.customerName.ifBlank { "N/A" }}" else "Customer : ${bill.customerName.ifBlank { "N/A" }}")
        if (bill.customerPhone.isNotBlank()) sb.appendLine(if (isBn) "ফোন      : ${bill.customerPhone}" else "Phone    : ${bill.customerPhone}")
        sb.appendLine("------------------------------------------")
    }
    val payMethodFormatted = if (isBn) formatPaymentMethodBangla(bill.getFormattedPaymentMethod()) else bill.getFormattedPaymentMethod()
    sb.appendLine(if (isBn) "পরিশোধ মাধ্যম : $payMethodFormatted" else "Payment Method : $payMethodFormatted")
    if (settings.showPaymentStatus) {
        if (bill.paymentMethod == "Due") {
            sb.appendLine(if (isBn) "অবস্থা         : বাকি" else "Status         : UNPAID")
        } else {
            if (bill.paymentMethod == "Cash") {
                sb.appendLine(if (isBn) "জমা            : ${formatTaka(bill.amountPaid)}" else "Paid           : ${formatTaka(bill.amountPaid)}")
                sb.appendLine(if (isBn) "ফেরত           : ${formatTaka(bill.changeAmount)}" else "Change         : ${formatTaka(bill.changeAmount)}")
            }
            sb.appendLine(if (isBn) "অবস্থা         : পরিশোধিত" else "Status         : PAID")
        }
        if (bill.hasDuePortion()) {
            sb.appendLine(if (isBn) "মোট বাকি       : ${formatTaka(bill.totalOutstandingDue)}" else "Total Due      : ${formatTaka(bill.totalOutstandingDue)}")
        }
        sb.appendLine("------------------------------------------")
    }
    val thankMsg = if (isBn && (settings.thankYouMessage.isBlank() || settings.thankYouMessage == "Thank you for shopping with us!")) {
        "আমাদের সাথে কেনাকাটা করার জন্য ধন্যবাদ!"
    } else {
        settings.thankYouMessage
    }
    sb.appendLine(thankMsg)
    if (settings.showSecondaryFooter) sb.appendLine(settings.secondaryFooter)
    val enabledInfoBlocks = settings.customInfoBlocks.filter { it.enabled }.sortedBy { it.position }
    if (enabledInfoBlocks.isNotEmpty()) {
        sb.appendLine("------------------------------------------")
        enabledInfoBlocks.forEach { block ->
            sb.appendLine(block.title)
            sb.appendLine(block.content)
        }
    }
    if (settings.showPoweredBy) sb.appendLine(settings.poweredByText)
    return sb.toString()
}

/** Generates QR Code Bitmap using ZXing with Error Correction M/Q and Quiet Zone */
fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 4) // Quiet zone of at least 4 modules
        }
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Renders the bill as an 80mm ESC/POS thermal receipt image (576 dots printable width),
 * adhering to high contrast monochrome standards, dynamic toggles, and multi-line item formatting.
 */
fun generateBillJpeg(context: Context, bill: BillData, receiptLanguage: String = "EN"): File {
    val isBn = receiptLanguage.equals("BN", ignoreCase = true)
    val settings = bill.settingsData
    val is58 = settings.paperRollWidthMm == 58
    val width = if (is58) 384 else 576 // 58mm (384 dots) vs 80mm (576 dots) ESC/POS printable dot width
    val sidePad = if (is58) 14f else 24f
    val doubleDivider = if (is58) "================================" else "=========================================="
    val singleDivider = if (is58) "--------------------------------" else "------------------------------------------"
    val textSizeBody = if (is58) 17f else 21f
    val textSizeBold = if (is58) 18f else 23f
    val textSizeTitle = if (is58) 25f else 32f
    val rowStep = if (is58) 26f else 30f

    // Measure dynamic canvas height
    var totalHeight = 10f

    // 1. Logo Height
    var logoBitmap: Bitmap? = null
    val effectiveLogoUri = bill.logoUri.ifBlank { settings.logoUri }
    if (settings.showLogo && effectiveLogoUri.isNotBlank()) {
        try {
            val uri = Uri.parse(effectiveLogoUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val decoded = BitmapFactory.decodeStream(stream)
                if (decoded != null) {
                    val targetLogoWidth = if (is58) 260 else 380
                    logoBitmap = convertToMonochromeThresholdBitmap(decoded, targetLogoWidth)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (logoBitmap != null) {
        totalHeight += logoBitmap!!.height + 20f
    }

    // 2. Store Header Text
    totalHeight += if (is58) 36f else 45f // Store Name
    if (settings.showAddress && bill.shopAddress.ifBlank { settings.address }.isNotBlank()) totalHeight += (if (is58) 28f else 34f)
    val effectivePhone = bill.shopPhone.ifBlank {
        if (settings.phoneSecondary.isNotBlank()) "${settings.phonePrimary} | ${settings.phoneSecondary}" else settings.phonePrimary
    }
    if (settings.showPhone && effectivePhone.isNotBlank()) totalHeight += (if (is58) 28f else 34f)

    val effectiveFb = bill.facebookUrl.ifBlank { settings.facebookUrl }
    if (settings.showFacebookInHeader && effectiveFb.isNotBlank()) totalHeight += (if (is58) 30f else 38f)
    totalHeight += 40f // Double divider

    // 3. Invoice Meta
    totalHeight += rowStep * 2 // Invoice ID & Date
    if (settings.showCashier) totalHeight += rowStep // Cashier
    totalHeight += 40f // Single divider

    // 4. Item Table Header
    totalHeight += 32f + 40f // Header + single divider

    // 5. Items — use a temporary Paint to measure pixel widths for Bengali text wrapping
    val tempPaint = Paint().apply {
        textSize = textSizeBody
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    val colItemMaxWidth = if (is58) 150f else 230f // pixel width reserved for Item column
    bill.items.forEach { item ->
        val nameLines = wrapItemNameByPixelWidth(item.name, colItemMaxWidth, tempPaint)
        val noteLines = if (!item.note.isNullOrBlank()) wrapItemNameByPixelWidth("• Note: ${item.note}", colItemMaxWidth, tempPaint) else emptyList()
        totalHeight += (nameLines.size + noteLines.size) * rowStep + 8f
    }
    totalHeight += 40f // Single divider

    // 6. Subtotal & Discount & Tax
    totalHeight += 34f
    if (settings.showDiscount && bill.discountValue > 0.0) totalHeight += 34f
    if (bill.taxValue > 0.0) totalHeight += 34f
    totalHeight += 40f // Double divider

    // 7. Grand Total
    totalHeight += 50f + 40f // Grand total + double divider

    // 8. Customer Details
    if (settings.showCustomerDetails && (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank())) {
        totalHeight += 30f
        if (bill.customerPhone.isNotBlank()) totalHeight += 30f
        totalHeight += 40f
    }

    // 9. Payment Details
    totalHeight += 30f
    if (settings.showPaymentStatus) {
        if (bill.paymentMethod == "Due") {
            totalHeight += 30f
        } else {
            if (bill.paymentMethod == "Cash") {
                totalHeight += 60f
            }
            totalHeight += 30f
        }
        if (bill.hasDuePortion()) totalHeight += 30f
        totalHeight += 40f
    }

    // 10. Footer Messages
    val thankYouMsg = if (isBn && (settings.thankYouMessage.isBlank() || settings.thankYouMessage == "Thank you for shopping with us!")) {
        "আমাদের সাথে কেনাকাটা করার জন্য ধন্যবাদ!"
    } else {
        settings.thankYouMessage
    }
    if (thankYouMsg.isNotBlank()) totalHeight += 34f
    if (settings.showSecondaryFooter && settings.secondaryFooter.isNotBlank()) totalHeight += 34f

    // 10b. Custom Info Blocks (e.g. Return Policy, Opening Hours)
    val enabledInfoBlocks = settings.customInfoBlocks.filter { it.enabled }.sortedBy { it.position }
    if (enabledInfoBlocks.isNotEmpty()) {
        totalHeight += 40f // divider before the block section
        enabledInfoBlocks.forEach { block ->
            if (block.title.isNotBlank()) totalHeight += 32f
            block.content.split("\n").forEach { paragraph ->
                val lines = wrapItemName(paragraph, if (is58) 28 else 38)
                totalHeight += lines.size * 28f
            }
            totalHeight += 12f
        }
    }

    totalHeight += 20f

    // 11. QR Code
    val qrPixelSize = when (settings.qrSize.lowercase()) {
        "small" -> if (is58) 160 else 200
        "large" -> if (is58) 240 else 320
        else -> if (is58) 200 else 260
    }

    val qrContent = when (settings.qrMode) {
        "facebook_page" -> effectiveFb
        "custom_url" -> settings.qrCustomUrl.ifBlank { "https://techyfi.net/invoice/${bill.saleId}" }
        "store_info" -> "Store: ${bill.shopName}\nAddress: ${bill.shopAddress}\nPhone: $effectivePhone"
        "embedded_json" -> "{\"invoice\":\"${bill.saleId}\",\"total\":${bill.grandTotal},\"status\":\"${bill.paymentMethod}\"}"
        else -> "https://techyfi.net/invoice/${bill.saleId}" // "digital_invoice"
    }

    val qrBmp = if (settings.showQr) generateQrBitmap(qrContent, qrPixelSize) else null
    if (qrBmp != null) {
        totalHeight += qrBmp.height + 20f
        if (settings.qrCaption.isNotBlank()) totalHeight += 36f
        totalHeight += 20f
    }

    // 12. Footer Facebook Link
    if (settings.showFacebookBelowQr && effectiveFb.isNotBlank()) totalHeight += 46f

    // 13. Powered by Pill Tag
    if (settings.showPoweredBy) totalHeight += 60f
    totalHeight += 10f // Bottom margin

    val bitmap = Bitmap.createBitmap(width, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val paintText = Paint().apply {
        color = Color.BLACK
        textSize = textSizeBody
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    val paintBold = Paint(paintText).apply {
        isFakeBoldText = true
        textSize = textSizeBold
    }

    val paintTitle = Paint().apply {
        color = Color.BLACK
        textSize = textSizeTitle
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    var currentY = 10f

    // 1. Draw Logo
    if (logoBitmap != null) {
        val logoX = (width - logoBitmap!!.width) / 2f
        canvas.drawBitmap(logoBitmap!!, logoX, currentY, null)
        currentY += logoBitmap!!.height + 20f
    }

    // 2. Draw Header
    val nameText = bill.shopName.ifBlank { settings.storeName }
    val nameW = paintTitle.measureText(nameText)
    canvas.drawText(nameText, (width - nameW) / 2f, currentY + 28f, paintTitle)
    currentY += 45f

    val shopAddr = bill.shopAddress.ifBlank { settings.address }
    if (settings.showAddress && shopAddr.isNotBlank()) {
        val addrW = paintText.measureText(shopAddr)
        canvas.drawText(shopAddr, (width - addrW) / 2f, currentY + 20f, paintText)
        currentY += 34f
    }

    if (settings.showPhone && effectivePhone.isNotBlank()) {
        val phoneW = paintText.measureText(effectivePhone)
        canvas.drawText(effectivePhone, (width - phoneW) / 2f, currentY + 20f, paintText)
        currentY += 34f
    }

    if (settings.showFacebookInHeader && effectiveFb.isNotBlank()) {
        val fbW = paintText.measureText(effectiveFb)
        canvas.drawText(effectiveFb, (width - fbW) / 2f, currentY + 20f, paintText)
        currentY += 38f
    }

    // 3. Double Divider
    val divDoubleW = paintText.measureText(doubleDivider)
    val divX = (width - divDoubleW) / 2f
    canvas.drawText(doubleDivider, divX, currentY + 20f, paintText)
    currentY += 35f

    // 4. Meta Info
    val dateStr = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.US).format(Date(bill.timestampMillis))
    canvas.drawText(String.format(Locale.US, if (isBn) "চালান নং : #%d" else "Invoice  : #%d", bill.saleId), sidePad, currentY + 20f, paintText)
    currentY += rowStep
    canvas.drawText(String.format(Locale.US, if (isBn) "তারিখ    : %s" else "Date     : %s", dateStr), sidePad, currentY + 20f, paintText)
    currentY += rowStep
    val cashierVal = bill.cashierName.ifBlank { settings.cashierName }
    if (settings.showCashier) {
        canvas.drawText(String.format(Locale.US, if (isBn) "ক্যাশিয়ার : %s" else "Cashier  : %s", cashierVal), sidePad, currentY + 20f, paintText)
        currentY += rowStep
    }

    // Single Divider
    canvas.drawText(singleDivider, divX, currentY + 20f, paintText)
    currentY += 35f

    // 5. Product Table Header — Fixed pixel X positions for each column
    val colItemX = sidePad            // Item name starts here
    val colItemMaxW = if (is58) 150f else 230f // Max pixel width for item name column
    val colQtyX = if (is58) 185f else 280f     // Qty column (right-aligned)
    val colPriceX = if (is58) 265f else 390f   // Price column (right-aligned)
    val colTotalX = width - sidePad            // Total column (right-aligned to right margin)

    val itemHdr = if (isBn) "আইটেম" else "Item"
    val qtyHdr = if (isBn) "পরিমাণ" else "Qty"
    val priceHdr = if (isBn) "দর" else "Price"
    val totalHdr = if (isBn) "মোট" else "Total"

    canvas.drawText(itemHdr, colItemX, currentY + 20f, paintBold)
    val qtyHdrW = paintBold.measureText(qtyHdr)
    canvas.drawText(qtyHdr, colQtyX - qtyHdrW / 2f, currentY + 20f, paintBold)
    val priceHdrW = paintBold.measureText(priceHdr)
    canvas.drawText(priceHdr, colPriceX - priceHdrW / 2f, currentY + 20f, paintBold)
    val totalHdrW = paintBold.measureText(totalHdr)
    canvas.drawText(totalHdr, colTotalX - totalHdrW, currentY + 20f, paintBold)
    currentY += 32f
    canvas.drawText(singleDivider, divX, currentY + 20f, paintText)
    currentY += 35f

    // 6. Product Table Items — pixel-based column positioning
    bill.items.forEach { item ->
        val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
        val priceStr = formatTaka(item.unitPrice).replace("৳", "").trim()
        val totalStr = formatTaka(item.lineTotal).replace("৳", "").trim()

        val nameLines = wrapItemNameByPixelWidth(item.name, colItemMaxW, paintText)
        nameLines.forEachIndexed { index, line ->
            // Draw item name left-aligned
            canvas.drawText(line, colItemX, currentY + 20f, paintText)
            if (index == 0) {
                // Qty — center-aligned on colQtyX
                val qtyW = paintText.measureText(qtyStr)
                canvas.drawText(qtyStr, colQtyX - qtyW / 2f, currentY + 20f, paintText)
                // Price — center-aligned on colPriceX
                val priceW = paintText.measureText(priceStr)
                canvas.drawText(priceStr, colPriceX - priceW / 2f, currentY + 20f, paintText)
                // Total — right-aligned to colTotalX
                val totalW = paintText.measureText(totalStr)
                canvas.drawText(totalStr, colTotalX - totalW, currentY + 20f, paintText)
            }
            currentY += rowStep
        }
        if (!item.note.isNullOrBlank()) {
            val noteLines = wrapItemNameByPixelWidth("• Note: ${item.note}", colItemMaxW, paintText)
            noteLines.forEach { nLine ->
                canvas.drawText(nLine, colItemX + 8f, currentY + 20f, paintText)
                currentY += rowStep
            }
        }
        currentY += 8f
    }

    canvas.drawText(singleDivider, divX, currentY + 20f, paintText)
    currentY += 35f

    // 7. Subtotal & Discount
    val subtotalVal = formatTaka(bill.subtotal)
    canvas.drawText(if (isBn) "উপমোট" else "Subtotal", sidePad, currentY + 20f, paintText)
    val subValW = paintText.measureText(subtotalVal)
    canvas.drawText(subtotalVal, colTotalX - subValW, currentY + 20f, paintText)
    currentY += 34f

    if (settings.showDiscount && bill.discountValue > 0.0) {
        val discVal = "-${formatTaka(bill.discountValue)}"
        canvas.drawText(if (isBn) "ছাড়" else "Discount", sidePad, currentY + 20f, paintText)
        val discValW = paintText.measureText(discVal)
        canvas.drawText(discVal, colTotalX - discValW, currentY + 20f, paintText)
        currentY += 34f
    }

    if (bill.taxValue > 0.0) {
        val taxVal = formatTaka(bill.taxValue)
        val label = if (isBn) {
            if (settings.taxInclusive) "(ভ্যাট সহ)" else bill.taxLabel.ifBlank { "কর" }
        } else {
            if (settings.taxInclusive) {
                "(Incl. ${bill.taxLabel.ifBlank { "VAT" }})"
            } else {
                bill.taxLabel.ifBlank { "Tax" }
            }
        }
        canvas.drawText(label, sidePad, currentY + 20f, paintText)
        val taxValW = paintText.measureText(taxVal)
        canvas.drawText(taxVal, colTotalX - taxValW, currentY + 20f, paintText)
        currentY += 34f
    }

    canvas.drawText(doubleDivider, divX, currentY + 20f, paintText)
    currentY += 35f

    // 8. GRAND TOTAL
    val grandVal = formatTaka(bill.grandTotal)
    val paintGrandLabel = Paint().apply {
        color = Color.BLACK
        textSize = if (is58) 22f else 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    val paintGrandVal = Paint(paintGrandLabel).apply {
        textSize = if (is58) 25f else 32f
    }
    canvas.drawText(if (isBn) "সর্বমোট" else "GRAND TOTAL", sidePad, currentY + 25f, paintGrandLabel)
    val grandW = paintGrandVal.measureText(grandVal)
    canvas.drawText(grandVal, colTotalX - grandW, currentY + 25f, paintGrandVal)
    currentY += 45f

    canvas.drawText(doubleDivider, divX, currentY + 20f, paintText)
    currentY += 35f

    // 9. Customer details
    if (settings.showCustomerDetails && (bill.customerName.isNotBlank() || bill.customerPhone.isNotBlank())) {
        canvas.drawText(String.format(Locale.US, if (isBn) "গ্রাহক    : %s" else "Customer : %s", bill.customerName.ifBlank { "N/A" }), sidePad, currentY + 20f, paintText)
        currentY += 30f
        if (bill.customerPhone.isNotBlank()) {
            canvas.drawText(String.format(Locale.US, if (isBn) "ফোন      : %s" else "Phone    : %s", bill.customerPhone), sidePad, currentY + 20f, paintText)
            currentY += 30f
        }
        canvas.drawText(singleDivider, divX, currentY + 20f, paintText)
        currentY += 35f
    }

    // 10. Payment Info
    val payMethodFormatted = if (isBn) formatPaymentMethodBangla(bill.getFormattedPaymentMethod()) else bill.getFormattedPaymentMethod()
    canvas.drawText(String.format(Locale.US, if (isBn) "পরিশোধ মাধ্যম : %s" else "Payment Method : %s", payMethodFormatted), sidePad, currentY + 20f, paintText)
    currentY += 30f
    if (settings.showPaymentStatus) {
        if (bill.paymentMethod == "Due") {
            canvas.drawText(if (isBn) "অবস্থা         : বাকি" else "Status         : UNPAID", sidePad, currentY + 20f, paintText)
            currentY += 30f
        } else {
            if (bill.paymentMethod == "Cash") {
                canvas.drawText(String.format(Locale.US, if (isBn) "জমা            : %s" else "Paid           : %s", formatTaka(bill.amountPaid)), sidePad, currentY + 20f, paintText)
                currentY += 30f
                canvas.drawText(String.format(Locale.US, if (isBn) "ফেরত           : %s" else "Change         : %s", formatTaka(bill.changeAmount)), sidePad, currentY + 20f, paintText)
                currentY += 30f
            }
            canvas.drawText(if (isBn) "অবস্থা         : পরিশোধিত" else "Status         : PAID", sidePad, currentY + 20f, paintText)
            currentY += 30f
        }
        if (bill.hasDuePortion()) {
            // Same left-aligned "Label (padded) : value" style as Payment Method/Status directly
            // above, using paintText (not paintBold, which is both fake-bold AND a different
            // textSize) so the colon lines up in the same column instead of looking like a
            // mismatched, oddly-spaced row in this block.
            canvas.drawText(
                String.format(Locale.US, if (isBn) "মোট বাকি       : %s" else "Total Due      : %s", formatTaka(bill.totalOutstandingDue)),
                sidePad, currentY + 20f, paintText
            )
            currentY += 30f
        }
        canvas.drawText(singleDivider, divX, currentY + 20f, paintText)
        currentY += 35f
    }

    // 11. Footer Visit Messages
    if (thankYouMsg.isNotBlank()) {
        val t1W = paintBold.measureText(thankYouMsg)
        canvas.drawText(thankYouMsg, (width - t1W) / 2f, currentY + 20f, paintBold)
        currentY += 32f
    }

    if (settings.showSecondaryFooter && settings.secondaryFooter.isNotBlank()) {
        val t2W = paintBold.measureText(settings.secondaryFooter)
        canvas.drawText(settings.secondaryFooter, (width - t2W) / 2f, currentY + 20f, paintBold)
        currentY += 40f
    }

    // 11b. Custom Info Blocks (e.g. Return Policy, Opening Hours)
    if (enabledInfoBlocks.isNotEmpty()) {
        canvas.drawText(singleDivider, divX, currentY + 20f, paintText)
        currentY += 35f
        enabledInfoBlocks.forEach { block ->
            if (block.title.isNotBlank()) {
                canvas.drawText(block.title, sidePad, currentY + 20f, paintBold)
                currentY += 32f
            }
            block.content.split("\n").forEach { paragraph ->
                val wrapChars = if (is58) 28 else 38
                wrapItemName(paragraph, wrapChars).forEach { line ->
                    canvas.drawText(line, sidePad, currentY + 20f, paintText)
                    currentY += 28f
                }
            }
            currentY += 12f
        }
    }

    // 12. QR Code
    if (qrBmp != null) {
        val qrX = (width - qrBmp.width) / 2f
        canvas.drawBitmap(qrBmp, qrX, currentY, null)
        currentY += qrBmp.height + 15f

        if (settings.qrCaption.isNotBlank()) {
            val qrSubW = paintText.measureText(settings.qrCaption)
            canvas.drawText(settings.qrCaption, (width - qrSubW) / 2f, currentY + 18f, paintText)
            currentY += 36f
        }
        currentY += 20f
    }

    // 13. Facebook Link Banner
    if (settings.showFacebookBelowQr && effectiveFb.isNotBlank()) {
        val fbPaint = Paint().apply {
            color = 0xFF1877F2.toInt()
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val fbBadgePaint = Paint().apply {
            color = 0xFF1877F2.toInt()
            isAntiAlias = true
        }
        val fbBadgeTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val fbTextW = fbPaint.measureText(effectiveFb)
        val totalFbW = 34f + 8f + fbTextW
        val startFbX = (width - totalFbW) / 2f

        val fbRect = RectF(startFbX, currentY, startFbX + 30f, currentY + 30f)
        canvas.drawRoundRect(fbRect, 6f, 6f, fbBadgePaint)
        canvas.drawText("f", startFbX + 10f, currentY + 22f, fbBadgeTextPaint)

        canvas.drawText(effectiveFb, startFbX + 38f, currentY + 23f, fbPaint)
        currentY += 46f
    }

    // 14. Watermark Pill Tag (Powered by Techyfi POS)
    if (settings.showPoweredBy) {
        val pillText = settings.poweredByText.ifBlank { "Powered by $APP_BRAND" }
        val pillPaint = Paint().apply {
            color = 0xFF1A1A1A.toInt()
            isAntiAlias = true
        }
        val pillTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val pTextW = pillTextPaint.measureText(pillText)
        val pillWidth = pTextW + 48f
        val pillHeight = 44f
        val pillX = (width - pillWidth) / 2f

        val pillRect = RectF(pillX, currentY, pillX + pillWidth, currentY + pillHeight)
        canvas.drawRoundRect(pillRect, 22f, 22f, pillPaint)
        canvas.drawText(pillText, pillX + 24f, currentY + 29f, pillTextPaint)
    }

    // Save Output JPEG
    val dir = File(context.cacheDir, "bills").apply { mkdirs() }
    val langSuffix = if (isBn) "_bn" else ""
    val file = File(dir, "invoice_${bill.saleId}$langSuffix.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    return file
}

fun generateBillPdf(
    context: Context,
    bill: BillData,
    paperRollWidthMm: Int = 80,
    receiptLanguage: String = "EN"
): File {
    val isBn = receiptLanguage.equals("BN", ignoreCase = true)
    val imageFile = generateBillJpeg(context, bill, receiptLanguage = receiptLanguage)
    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        ?: throw IllegalStateException("Unable to render receipt image")
    val pageWidthPoints = (paperRollWidthMm.toFloat() / 25.4f * 72f).toInt().coerceAtLeast(1)
    val pageHeightPoints = (
        pageWidthPoints.toFloat() * bitmap.height.toFloat() / bitmap.width.toFloat()
        ).toInt().coerceAtLeast(1)
    val langSuffix = if (isBn) "_bn" else ""
    val outputFile = File(imageFile.parentFile, "invoice_${bill.saleId}$langSuffix.pdf")
    val document = PdfDocument()

    try {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, pageWidthPoints.toFloat(), pageHeightPoints.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        document.finishPage(page)
        FileOutputStream(outputFile).use(document::writeTo)
    } finally {
        document.close()
        bitmap.recycle()
    }

    return outputFile
}

fun getBillImageUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

fun getBillPdfUri(context: Context, file: File): Uri = getBillImageUri(context, file)

fun generateZReportJpeg(context: Context, data: SalesReportData, rangeLabel: String): File {
    val width = 576
    val doubleDivider = "=========================================="
    val singleDivider = "------------------------------------------"

    // Measure dynamic canvas height
    var totalHeight = 40f

    // 1. Header
    totalHeight += 45f // TECHYFI POS
    totalHeight += 35f // DAILY CLOSING Z-REPORT
    totalHeight += 40f // Double divider

    // 2. Meta
    totalHeight += 30f // Period: ...
    totalHeight += 30f // Generated: ...
    totalHeight += 30f // Invoices: ... | Units: ...
    totalHeight += 40f // Single divider

    // 3. Financial Summary
    totalHeight += 34f // Gross Sales: ...
    totalHeight += 34f // Discounts Given: ...
    totalHeight += 34f // Gross Margin: ...
    totalHeight += 34f // Expenses: ...
    totalHeight += 34f // Net Profit: ...
    totalHeight += 40f // Single divider

    // 4. Payments
    totalHeight += 32f // PAYMENTS:
    val payments = if (data.paymentBreakdown.isEmpty()) 1 else data.paymentBreakdown.size
    totalHeight += payments * 30f
    totalHeight += 40f // Single divider

    // 5. Top Products (if any)
    if (data.topProducts.isNotEmpty()) {
        totalHeight += 32f // TOP PRODUCTS:
        totalHeight += data.topProducts.take(8).size * 30f
        totalHeight += 40f // Single divider
    }

    // 6. Footer
    totalHeight += 35f // *** END OF Z-REPORT ***
    totalHeight += 30f // Techyfi POS • Smart Store System
    totalHeight += 40f // Bottom padding

    val bitmap = Bitmap.createBitmap(width, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    val boldPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    var y = 45f

    // Header
    boldPaint.textSize = 28f
    boldPaint.textAlign = Paint.Align.CENTER
    canvas.drawText("TECHYFI POS", width / 2f, y, boldPaint)
    y += 35f

    boldPaint.textSize = 22f
    canvas.drawText("DAILY CLOSING Z-REPORT", width / 2f, y, boldPaint)
    y += 35f

    paint.textSize = 20f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(doubleDivider, width / 2f, y, paint)
    y += 35f

    // Meta
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("Period    : $rangeLabel", 24f, y, paint)
    y += 30f
    val generatedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
    canvas.drawText("Generated : $generatedDate", 24f, y, paint)
    y += 30f
    canvas.drawText("Invoices  : ${data.transactionCount} | Units Sold: ${data.itemsSold}", 24f, y, paint)
    y += 35f

    canvas.drawText(singleDivider, 24f, y, paint)
    y += 35f

    // Financials
    boldPaint.textAlign = Paint.Align.LEFT
    boldPaint.textSize = 21f
    canvas.drawText("FINANCIAL SUMMARY", 24f, y, boldPaint)
    y += 32f

    paint.textSize = 20f
    fun drawKeyValue(label: String, value: String) {
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, 24f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, width - 24f, y, paint)
        y += 30f
    }

    drawKeyValue("Gross Sales", formatTaka(data.totalRevenue))
    drawKeyValue("Discounts Given", formatTaka(data.totalDiscount))
    drawKeyValue("Gross Margin", formatTaka(data.totalProfit))
    drawKeyValue("Expenses", formatTaka(data.totalExpenses))
    drawKeyValue("Net Profit", formatTaka(data.netProfit))

    paint.textAlign = Paint.Align.LEFT
    canvas.drawText(singleDivider, 24f, y, paint)
    y += 35f

    // Payments
    boldPaint.textSize = 21f
    canvas.drawText("PAYMENT BREAKDOWN", 24f, y, boldPaint)
    y += 32f

    if (data.paymentBreakdown.isEmpty()) {
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("No payments recorded", 24f, y, paint)
        y += 30f
    } else {
        data.paymentBreakdown.forEach { p ->
            drawKeyValue("${p.method} (${p.transactionCount})", formatTaka(p.amount))
        }
    }

    // Top Products
    if (data.topProducts.isNotEmpty()) {
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(singleDivider, 24f, y, paint)
        y += 35f

        boldPaint.textSize = 21f
        canvas.drawText("TOP SELLING PRODUCTS", 24f, y, boldPaint)
        y += 32f

        data.topProducts.take(8).forEach { prod ->
            val name = if (prod.name.length > 20) prod.name.take(18) + ".." else prod.name
            val qStr = if (prod.quantity % 1.0 == 0.0) prod.quantity.toLong().toString() else String.format(Locale.US, "%.2f", prod.quantity).trimEnd('0').trimEnd('.')
            drawKeyValue("${qStr}x $name", formatTaka(prod.revenue))
        }
    }

    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(doubleDivider, width / 2f, y, paint)
    y += 35f

    boldPaint.textAlign = Paint.Align.CENTER
    boldPaint.textSize = 20f
    canvas.drawText("*** END OF Z-REPORT ***", width / 2f, y, boldPaint)
    y += 30f

    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 16f
    canvas.drawText("Techyfi POS • Smart Store System", width / 2f, y, paint)

    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val file = File(dir, "techyfi-z-report-${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    bitmap.recycle()
    return file
}

fun generateZReportPdf(context: Context, data: SalesReportData, rangeLabel: String): File {
    val imageFile = generateZReportJpeg(context, data, rangeLabel)
    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        ?: throw IllegalStateException("Unable to render Z-Report image for PDF")
    val pageWidthPoints = (80f / 25.4f * 72f).toInt().coerceAtLeast(1)
    val pageHeightPoints = (
        pageWidthPoints.toFloat() * bitmap.height.toFloat() / bitmap.width.toFloat()
        ).toInt().coerceAtLeast(1)
    val outputFile = File(imageFile.parentFile, "techyfi-z-report-${System.currentTimeMillis()}.pdf")
    val document = PdfDocument()

    try {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, pageWidthPoints.toFloat(), pageHeightPoints.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        document.finishPage(page)
        FileOutputStream(outputFile).use(document::writeTo)
    } finally {
        document.close()
        bitmap.recycle()
    }

    return outputFile
}

fun generateXReportJpeg(context: Context, data: SalesReportData, rangeLabel: String): File {
    val width = 576
    val doubleDivider = "=========================================="
    val singleDivider = "------------------------------------------"

    var totalHeight = 40f
    totalHeight += 45f // TECHYFI POS
    totalHeight += 35f // MID-SHIFT X-REPORT (INTERIM)
    totalHeight += 40f // Double divider
    totalHeight += 30f // Period
    totalHeight += 30f // Generated
    totalHeight += 30f // Invoices & Units
    totalHeight += 40f // Single divider
    totalHeight += 34f // Gross Sales
    totalHeight += 34f // Discounts
    totalHeight += 34f // Gross Margin
    totalHeight += 34f // Expenses
    totalHeight += 34f // Net Profit
    totalHeight += 40f // Single divider
    totalHeight += 32f // PAYMENTS header
    val payments = if (data.paymentBreakdown.isEmpty()) 1 else data.paymentBreakdown.size
    totalHeight += payments * 30f
    totalHeight += 40f // Single divider
    if (data.topProducts.isNotEmpty()) {
        totalHeight += 32f
        totalHeight += data.topProducts.take(8).size * 30f
        totalHeight += 40f
    }
    totalHeight += 35f // *** END OF X-REPORT (INTERIM) ***
    totalHeight += 30f // Techyfi POS • Smart Store System
    totalHeight += 40f

    val bitmap = Bitmap.createBitmap(width, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    val boldPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    var y = 45f
    boldPaint.textSize = 28f
    boldPaint.textAlign = Paint.Align.CENTER
    canvas.drawText("TECHYFI POS", width / 2f, y, boldPaint)
    y += 35f

    boldPaint.textSize = 22f
    canvas.drawText("MID-SHIFT X-REPORT (INTERIM)", width / 2f, y, boldPaint)
    y += 35f

    paint.textSize = 20f
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(doubleDivider, width / 2f, y, paint)
    y += 35f

    paint.textAlign = Paint.Align.LEFT
    canvas.drawText("Period    : $rangeLabel", 24f, y, paint)
    y += 30f
    val generatedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
    canvas.drawText("Generated : $generatedDate", 24f, y, paint)
    y += 30f
    canvas.drawText("Invoices  : ${data.transactionCount} | Units Sold: ${data.itemsSold}", 24f, y, paint)
    y += 35f

    canvas.drawText(singleDivider, 24f, y, paint)
    y += 35f

    boldPaint.textAlign = Paint.Align.LEFT
    boldPaint.textSize = 21f
    canvas.drawText("CURRENT TILL SUMMARY (INTERIM)", 24f, y, boldPaint)
    y += 32f

    paint.textSize = 20f
    fun drawKeyValue(label: String, value: String) {
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, 24f, y, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, width - 24f, y, paint)
        y += 30f
    }

    drawKeyValue("Gross Sales", formatTaka(data.totalRevenue))
    drawKeyValue("Discounts Given", formatTaka(data.totalDiscount))
    drawKeyValue("Gross Margin", formatTaka(data.totalProfit))
    drawKeyValue("Expenses", formatTaka(data.totalExpenses))
    drawKeyValue("Net Profit", formatTaka(data.netProfit))

    paint.textAlign = Paint.Align.LEFT
    canvas.drawText(singleDivider, 24f, y, paint)
    y += 35f

    boldPaint.textSize = 21f
    canvas.drawText("PAYMENT BREAKDOWN", 24f, y, boldPaint)
    y += 32f

    if (data.paymentBreakdown.isEmpty()) {
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("No payments recorded", 24f, y, paint)
        y += 30f
    } else {
        data.paymentBreakdown.forEach { p ->
            drawKeyValue("${p.method} (${p.transactionCount})", formatTaka(p.amount))
        }
    }

    if (data.topProducts.isNotEmpty()) {
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(singleDivider, 24f, y, paint)
        y += 35f

        boldPaint.textSize = 21f
        canvas.drawText("TOP SELLING PRODUCTS", 24f, y, boldPaint)
        y += 32f

        data.topProducts.take(8).forEach { prod ->
            val name = if (prod.name.length > 20) prod.name.take(18) + ".." else prod.name
            val qStr = if (prod.quantity % 1.0 == 0.0) prod.quantity.toLong().toString() else String.format(Locale.US, "%.2f", prod.quantity).trimEnd('0').trimEnd('.')
            drawKeyValue("${qStr}x $name", formatTaka(prod.revenue))
        }
    }

    paint.textAlign = Paint.Align.LEFT
    canvas.drawText(doubleDivider, 24f, y, paint)
    y += 35f

    boldPaint.textAlign = Paint.Align.CENTER
    boldPaint.textSize = 20f
    canvas.drawText("*** INTERIM READING (DOES NOT CLOSE DAY) ***", width / 2f, y, boldPaint)
    y += 30f

    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 16f
    canvas.drawText("Techyfi POS • Smart Store System", width / 2f, y, paint)

    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val file = File(dir, "techyfi-x-report-${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    bitmap.recycle()
    return file
}

fun generateXReportPdf(context: Context, data: SalesReportData, rangeLabel: String): File {
    val imageFile = generateXReportJpeg(context, data, rangeLabel)
    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        ?: throw IllegalStateException("Unable to render X-Report image for PDF")
    val pageWidthPoints = (80f / 25.4f * 72f).toInt().coerceAtLeast(1)
    val pageHeightPoints = (
        pageWidthPoints.toFloat() * bitmap.height.toFloat() / bitmap.width.toFloat()
        ).toInt().coerceAtLeast(1)
    val outputFile = File(imageFile.parentFile, "techyfi-x-report-${System.currentTimeMillis()}.pdf")
    val document = PdfDocument()

    try {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, pageWidthPoints.toFloat(), pageHeightPoints.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        document.finishPage(page)
        FileOutputStream(outputFile).use(document::writeTo)
    } finally {
        document.close()
        bitmap.recycle()
    }

    return outputFile
}

fun formatTakaBn(amount: Double): String {
val digits = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
    )
    val en = formatTaka(amount)
    return en.map { digits[it] ?: it }.joinToString("")
}

fun generateDueRepaymentJpeg(
    context: Context,
    customerName: String,
    customerPhone: String,
    amountPaid: Double,
    paymentMethod: String,
    referenceNote: String?,
    remainingDue: Double,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    isBangla: Boolean = false,
    settings: InvoiceSettingsData = InvoiceSettingsData(),
    timestamp: Long = System.currentTimeMillis()
): File {
    val is58 = settings.paperRollWidthMm == 58
    val width = if (is58) 384 else 576
    val isSettled = remainingDue <= 0.001
    val sidePad = if (is58) 14f else 24f

    // 1. Process Monochrome Logo if enabled (Exact same sharp thresholding as sale receipt)
    var logoBitmap: Bitmap? = null
    val shouldShowLogo = (settings.showDueReceiptLogo || settings.showLogo) && settings.logoUri.isNotBlank()
    if (shouldShowLogo) {
        try {
            val uri = Uri.parse(settings.logoUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val decoded = BitmapFactory.decodeStream(stream)
                if (decoded != null) {
                    val targetLogoWidth = if (is58) 260 else 380
                    logoBitmap = convertToMonochromeThresholdBitmap(decoded, targetLogoWidth)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. Calculate dynamic height accurately based on configurable settings
    var totalHeight = 36f
    if (logoBitmap != null) {
        totalHeight += logoBitmap!!.height + 20f
    }
    if (settings.showDueReceiptHeader) {
        totalHeight += if (is58) 26f else 32f // Store Name
        if (settings.showAddress && storeAddress.isNotBlank()) totalHeight += (if (is58) 24f else 30f)
        if (settings.showPhone && storePhone.isNotBlank()) totalHeight += (if (is58) 22f else 28f)
    }
    totalHeight += if (is58) 30f else 38f // Title Banner
    totalHeight += 20f // Divider 1
    val metaRowHeight = if (is58) 22f else 28f
    totalHeight += metaRowHeight * (if (!referenceNote.isNullOrBlank()) 5 else 4) // Metadata rows
    totalHeight += 20f // Divider 2
    totalHeight += if (is58) 28f else 34f // Previous Due
    totalHeight += 12f + (if (is58) 46f else 54f) + 30f // Amount Paid Hero Box + padding
    totalHeight += if (is58) 28f else 34f // Remaining Due
    totalHeight += 20f // Divider 3
    totalHeight += 14f + (if (is58) 38f else 46f) + 30f // Status Stamp + padding

    // Custom Section Lines
    val hasCustomNotes = settings.showDueReceiptCustomNotes && settings.dueReceiptCustomNotes.isNotBlank()
    val customLines = mutableListOf<String>()
    if (hasCustomNotes) {
        val maxChars = if (is58) 28 else 44
        val rawWords = settings.dueReceiptCustomNotes.replace("\n", " \n ").split(" ")
        var currentLine = StringBuilder()
        for (w in rawWords) {
            if (w == "\n") {
                if (currentLine.isNotEmpty()) customLines.add(currentLine.toString())
                currentLine = StringBuilder()
            } else if (currentLine.isEmpty()) {
                currentLine.append(w)
            } else if (currentLine.length + 1 + w.length <= maxChars) {
                currentLine.append(" ").append(w)
            } else {
                customLines.add(currentLine.toString())
                currentLine = StringBuilder(w)
            }
        }
        if (currentLine.isNotEmpty()) customLines.add(currentLine.toString())

        totalHeight += 16f + (if (settings.dueReceiptCustomTitle.isNotBlank()) 24f else 0f) + (customLines.size * (if (is58) 18f else 22f)) + 16f
    }

    if (settings.showDueReceiptSignatures) {
        val sigTopClearance = if (is58) 70f else 95f
        val sigLineToLabel = if (is58) 18f else 24f
        val sigBottomMargin = if (is58) 32f else 44f
        totalHeight += sigTopClearance + sigLineToLabel + sigBottomMargin // Generous clearance for seal & signature + line + labels + padding
    }
    if (settings.showDueReceiptQr) {
        totalHeight += (if (is58) 150f else 200f) + 24f // QR Code + caption/padding
    }
    totalHeight += if (is58) 24f else 28f // Thank You
    totalHeight += if (is58) 20f else 24f // Techyfi POS footer
    if (settings.showDueReceiptBarcode) {
        totalHeight += 20f + 24f // Barcode & padding
    }
    totalHeight += 32f // Bottom padding

    val bitmap = Bitmap.createBitmap(width, totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val tfNormal = if (isBangla) Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) else Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    val tfBold = if (isBangla) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    val paint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        typeface = tfNormal
    }

    val boldPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
        typeface = tfBold
    }

    val dashDividerPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = if (is58) 1.2f else 1.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
        isAntiAlias = true
    }

    val solidLinePaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = if (is58) 1.2f else 1.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    var y = if (is58) 32f else 46f

    // 1. Draw Logo if loaded
    if (logoBitmap != null) {
        val logoLeft = (width - logoBitmap!!.width) / 2f
        canvas.drawBitmap(logoBitmap!!, logoLeft, y, null)
        y += logoBitmap!!.height + 20f
        logoBitmap!!.recycle()
    }

    // 2. Store Branding Header (Centered Thermal POS Style)
    if (settings.showDueReceiptHeader) {
        boldPaint.textSize = if (is58) 22f else 28f
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(storeName.ifBlank { "TECHYFI STORE" }.uppercase(), width / 2f, y, boldPaint)
        y += if (is58) 24f else 30f

        paint.textSize = if (is58) 14f else 18f
        paint.textAlign = Paint.Align.CENTER
        if (settings.showAddress && storeAddress.isNotBlank()) {
            canvas.drawText(storeAddress, width / 2f, y, paint)
            y += if (is58) 22f else 28f
        }
        if (settings.showPhone && storePhone.isNotBlank()) {
            val phoneLabel = if (isBangla) "হটলাইন: $storePhone" else "Hotline: $storePhone"
            canvas.drawText(phoneLabel, width / 2f, y, paint)
            y += if (is58) 22f else 28f
        }
    }

    // Title banner
    val titleBanner = if (isBangla) "★ টাকা জমার রশিদ • বাকি আদায় ★" else "★ MONEY RECEIPT • DUE REPAYMENT ★"
    boldPaint.textSize = if (is58) 15f else 20f
    boldPaint.textAlign = Paint.Align.CENTER
    canvas.drawText(titleBanner, width / 2f, y, boldPaint)
    y += if (is58) 16f else 20f

    // Divider 1
    canvas.drawLine(sidePad, y, width - sidePad, y, dashDividerPaint)
    y += if (is58) 20f else 26f

    // 3. Customer & Receipt Metadata (Tabular 2-Column Layout)
    val locale = if (isBangla) Locale.forLanguageTag("bn-BD") else Locale.getDefault()
    val datePattern = if (isBangla) "dd MMMM yyyy, hh:mm a" else "dd MMM yyyy, hh:mm a"
    val dateStr = SimpleDateFormat(datePattern, locale).format(Date(timestamp))
    val voucherNo = "REC-${timestamp % 1000000}"

    fun drawMetaRow(label: String, value: String) {
        paint.textSize = if (is58) 14f else 18f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, sidePad, y, paint)
        boldPaint.textSize = if (is58) 14f else 18f
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, width - sidePad, y, boldPaint)
        y += metaRowHeight
    }

    drawMetaRow(if (isBangla) "তারিখ ও সময়:" else "Date:", dateStr)
    drawMetaRow(if (isBangla) "ভাউচার নং:" else "Voucher #:", voucherNo)
    drawMetaRow(if (isBangla) "কাস্টমার:" else "Customer:", customerName.ifBlank { if (isBangla) "সম্মানিত কাস্টমার" else "Valued Customer" })
    drawMetaRow(if (isBangla) "মোবাইল:" else "Phone:", customerPhone.ifBlank { "N/A" })
    if (!referenceNote.isNullOrBlank()) {
        drawMetaRow(if (isBangla) "রেফারেন্স / নোট:" else "Ref / TrxID:", referenceNote)
    }

    // Divider 2
    canvas.drawLine(sidePad, y, width - sidePad, y, dashDividerPaint)
    y += if (is58) 22f else 30f

    // 4. Financial Breakdown
    val prevDueVal = if (isBangla) formatTakaBn(amountPaid + remainingDue) else formatTaka(amountPaid + remainingDue)
    val paidAmtVal = if (isBangla) formatTakaBn(amountPaid) else formatTaka(amountPaid)
    val remDueVal = if (isSettled) {
        if (isBangla) "৳ ০ (সম্পূর্ণ পরিশোধ ✓)" else "৳ 0 (Fully Settled ✓)"
    } else {
        if (isBangla) formatTakaBn(remainingDue) else formatTaka(remainingDue)
    }

    // Previous Outstanding Row
    paint.textSize = if (is58) 15f else 20f
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText(if (isBangla) "আগের বাকি" else "Previous Outstanding", sidePad, y, paint)
    boldPaint.textSize = if (is58) 15f else 20f
    boldPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(prevDueVal, width - sidePad, y, boldPaint)
    y += if (is58) 18f else 24f

    // Hero Box for Amount Collected
    y += 8f
    val boxTop = y
    val boxHeight = if (is58) 46f else 54f
    val boxBottom = boxTop + boxHeight
    val boxRect = RectF(sidePad - 4f, boxTop, width - sidePad + 4f, boxBottom)

    val boxBgPaint = Paint().apply {
        color = 0xFFFAFAFA.toInt()
        style = Paint.Style.FILL
    }
    val boxStrokePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = if (is58) 1.5f else 2f
        isAntiAlias = true
    }
    canvas.drawRoundRect(boxRect, 6f, 6f, boxBgPaint)
    canvas.drawRoundRect(boxRect, 6f, 6f, boxStrokePaint)

    val boxTextY = boxTop + (if (is58) 30f else 35f)
    boldPaint.textSize = if (is58) 15f else 20f
    boldPaint.textAlign = Paint.Align.LEFT
    val methodDisplay = if (isBangla && paymentMethod.equals("Cash", ignoreCase = true)) "নগদ" else paymentMethod
    val paidLabel = if (isBangla) "আজকের জমা [$methodDisplay]" else "AMOUNT COLLECTED [$paymentMethod]"
    canvas.drawText(paidLabel, sidePad + 6f, boxTextY, boldPaint)

    boldPaint.textSize = if (is58) 17f else 22f
    boldPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(paidAmtVal, width - sidePad - 6f, boxTextY, boldPaint)

    // Clear vertical advance past the hero box
    y = boxBottom + (if (is58) 26f else 34f)

    // Remaining Balance Row
    boldPaint.textSize = if (is58) 16f else 21f
    boldPaint.textAlign = Paint.Align.LEFT
    canvas.drawText(if (isBangla) "অবশিষ্ট বাকি" else "Remaining Balance", sidePad, y, boldPaint)
    boldPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(remDueVal, width - sidePad, y, boldPaint)
    y += if (is58) 18f else 24f

    // Divider 3
    canvas.drawLine(sidePad, y, width - sidePad, y, dashDividerPaint)
    y += if (is58) 16f else 20f

    // 5. Status Stamp
    val stampTop = y
    val stampHeight = if (is58) 36f else 44f
    val stampBottom = stampTop + stampHeight
    val stampRect = RectF(if (is58) 24f else 40f, stampTop, width - (if (is58) 24f else 40f), stampBottom)

    val stampStrokePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        isAntiAlias = true
    }
    canvas.drawRoundRect(stampRect, 6f, 6f, stampStrokePaint)

    boldPaint.textAlign = Paint.Align.CENTER
    boldPaint.textSize = if (is58) 14.5f else 19f
    val statusText = if (isSettled) {
        if (isBangla) "✔ বাকি সম্পূর্ণ পরিশোধ হয়েছে" else "✔ PAID IN FULL & SETTLED"
    } else {
        if (isBangla) "⏳ আংশিক জমা নেওয়া হলো" else "⏳ PARTIAL PAYMENT RECEIVED"
    }
    canvas.drawText(statusText, width / 2f, stampTop + (if (is58) 24f else 29f), boldPaint)
    y = stampBottom + (if (is58) 28f else 38f)

    // 6. Custom Section / Notice if enabled
    if (hasCustomNotes && customLines.isNotEmpty()) {
        val customBoxTop = y
        val customBoxPadding = if (is58) 8f else 12f
        val lineH = if (is58) 18f else 22f
        val hasTitle = settings.dueReceiptCustomTitle.isNotBlank()
        val customBoxHeight = customBoxPadding * 2 + (if (hasTitle) 22f else 0f) + (customLines.size * lineH)
        val customBoxRect = RectF(sidePad - 2f, customBoxTop, width - sidePad + 2f, customBoxTop + customBoxHeight)

        val customBoxPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            isAntiAlias = true
        }
        canvas.drawRoundRect(customBoxRect, 4f, 4f, customBoxPaint)

        var curCustomY = customBoxTop + customBoxPadding + 14f
        if (hasTitle) {
            boldPaint.textSize = if (is58) 12.5f else 15f
            boldPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(settings.dueReceiptCustomTitle.uppercase(), width / 2f, curCustomY, boldPaint)
            curCustomY += 20f
        }

        paint.textSize = if (is58) 11.5f else 14f
        paint.textAlign = Paint.Align.LEFT
        for (cline in customLines) {
            canvas.drawText(cline, sidePad + 6f, curCustomY, paint)
            curCustomY += lineH
        }

        y = customBoxTop + customBoxHeight + 20f
    }

    // 7. Dual Signature Lines (Generous headroom for physical rubber seal & pen signature)
    if (settings.showDueReceiptSignatures) {
        val sigTopClearance = if (is58) 70f else 95f
        y += sigTopClearance

        val sigLineW = if (is58) 125f else 195f
        val sigCenter1 = sidePad + sigLineW / 2f
        val sigCenter2 = width - sidePad - sigLineW / 2f
        canvas.drawLine(sidePad, y, sidePad + sigLineW, y, solidLinePaint)
        canvas.drawLine(width - sidePad - sigLineW, y, width - sidePad, y, solidLinePaint)
        y += if (is58) 18f else 24f

        val sigPaint = Paint().apply {
            color = Color.BLACK
            textSize = if (is58) 12f else 14.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = tfBold
        }
        canvas.drawText(if (isBangla) "কাস্টমারের স্বাক্ষর" else "Customer Signature", sigCenter1, y, sigPaint)
        canvas.drawText(if (isBangla) "দোকানের সিল ও স্বাক্ষর" else "Authorized Seal", sigCenter2, y, sigPaint)
        y += if (is58) 32f else 44f
    }

    // 8. Optional Verification QR Code
    if (settings.showDueReceiptQr) {
        val qrSize = if (is58) 140 else 180
        val qrContent = "REC-${voucherNo}|${customerName}|${amountPaid}|${System.currentTimeMillis()}"
        val qrBmp = generateQrBitmap(qrContent, qrSize)
        if (qrBmp != null) {
            val qrLeft = (width - qrBmp.width) / 2f
            canvas.drawBitmap(qrBmp, qrLeft, y, null)
            y += qrBmp.height + (if (is58) 12f else 16f)
            paint.textSize = if (is58) 11.5f else 14f
            paint.textAlign = Paint.Align.CENTER
            val qrCaptionText = if (isBangla) "স্ক্যান করে ডিজিটাল ভাউচার যাচাই করুন" else "Scan QR to verify payment voucher"
            canvas.drawText(qrCaptionText, width / 2f, y, paint)
            y += if (is58) 18f else 24f
            qrBmp.recycle()
        }
    }

    // 9. Footer Greetings
    boldPaint.textAlign = Paint.Align.CENTER
    boldPaint.textSize = if (is58) 14.5f else 18f
    val thanksMsg = if (isBangla) "*** ধন্যবাদ, আবার আসবেন! ***" else "*** Thank you for your payment! ***"
    canvas.drawText(thanksMsg, width / 2f, y, boldPaint)
    y += if (is58) 20f else 26f

    paint.textAlign = Paint.Align.CENTER
    paint.textSize = if (is58) 12f else 15f
    canvas.drawText("Techyfi POS • Smart Store System", width / 2f, y, paint)
    y += if (is58) 16f else 20f

    // 10. Barcode Simulation
    if (settings.showDueReceiptBarcode) {
        val barPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        val barWidths = listOf(3f, 2f, 4f, 2f, 3f, 5f, 2f, 3f, 4f, 2f, 3f, 5f, 3f, 2f, 4f, 3f, 4f, 2f, 5f, 3f, 2f, 4f)
        val scale = if (is58) 2.4f else 3.5f
        var barX = (width - barWidths.sum() * scale) / 2f
        val barHeight = if (is58) 18f else 22f
        for (bw in barWidths) {
            val curW = if (is58) bw * 1.4f else bw * 2f
            canvas.drawRect(barX, y, barX + curW, y + barHeight, barPaint)
            barX += curW + (if (is58) 2.2f else 3.2f)
        }
    }

    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "techyfi-repayment-${if (isBangla) "bn" else "en"}-${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    bitmap.recycle()
    return file
}

fun generateDueRepaymentPdf(
    context: Context,
    customerName: String,
    customerPhone: String,
    amountPaid: Double,
    paymentMethod: String,
    referenceNote: String?,
    remainingDue: Double,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    isBangla: Boolean = false,
    settings: InvoiceSettingsData = InvoiceSettingsData()
): File {
    val imageFile = generateDueRepaymentJpeg(
        context, customerName, customerPhone, amountPaid, paymentMethod, referenceNote, remainingDue, storeName, storeAddress, storePhone, isBangla, settings
    )
    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        ?: throw IllegalStateException("Unable to render Repayment image for PDF")
    val pageWidthPoints = (settings.paperRollWidthMm.toFloat() / 25.4f * 72f).toInt().coerceAtLeast(1)
    val pageHeightPoints = (
        pageWidthPoints.toFloat() * bitmap.height.toFloat() / bitmap.width.toFloat()
        ).toInt().coerceAtLeast(1)
    val outputFile = File(imageFile.parentFile, "techyfi-repayment-${if (isBangla) "bn" else "en"}-${System.currentTimeMillis()}.pdf")
    val document = PdfDocument()

    try {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, pageWidthPoints.toFloat(), pageHeightPoints.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        document.finishPage(page)
        FileOutputStream(outputFile).use(document::writeTo)
    } finally {
        document.close()
        bitmap.recycle()
    }

    return outputFile
}

