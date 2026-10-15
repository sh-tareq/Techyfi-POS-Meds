package com.example.techyfipos.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.roundToInt

const val TSPL_INVERT_BITS: Boolean = false

private fun formatMm(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

/**
 * Fixed luminance threshold around 170 for TSPL label binarization.
 * Preserves crisp 1-bit text and barcode edge geometry while darkness setting controls printer DENSITY.
 */
fun labelLuminanceThreshold(darkness: Int): Int = 170

/**
 * Pure function converting pixel coordinates into TSPL raster BITMAP payload bytes.
 * In TSPL BITMAP data, bit 0 = black (printed) and bit 1 = white (blank), opposite of ESC/POS.
 * TSPL_INVERT_BITS constant allows flipping the polarity in one place if needed.
 */
fun tsplBitmapBytes(
    widthDots: Int,
    heightDots: Int,
    invertBits: Boolean = TSPL_INVERT_BITS,
    darkness: Int = 8,
    getPixel: (x: Int, y: Int) -> Int
): ByteArray {
    val threshold = labelLuminanceThreshold(darkness)
    val widthBytes = (widthDots + 7) / 8
    val out = ByteArray(widthBytes * heightDots)
    var outIndex = 0

    for (y in 0 until heightDots) {
        for (byteIndex in 0 until widthBytes) {
            var b = 0
            for (bit in 0 until 8) {
                val x = byteIndex * 8 + bit
                val isBlack = if (x < widthDots) {
                    val pixel = getPixel(x, y)
                    val alpha = (pixel shr 24) and 0xFF
                    val red = (pixel shr 16) and 0xFF
                    val green = (pixel shr 8) and 0xFF
                    val blue = pixel and 0xFF
                    val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                    alpha > 40 && luminance < threshold
                } else {
                    false
                }
                val dotBit = if (isBlack) 0 else 1
                val finalBit = if (invertBits) (1 - dotBit) else dotBit
                if (finalBit == 1) {
                    b = b or (1 shl (7 - bit))
                }
            }
            out[outIndex++] = b.toByte()
        }
    }
    return out
}

/**
 * Pure function converting a Bitmap into TSPL raster BITMAP payload bytes.
 */
fun tsplBitmapBytes(
    bitmap: Bitmap,
    invertBits: Boolean = TSPL_INVERT_BITS,
    darkness: Int = 8
): ByteArray = tsplBitmapBytes(bitmap.width, bitmap.height, invertBits, darkness) { x, y -> bitmap.getPixel(x, y) }

/**
 * Builds the complete TSPL binary command stream for printing a label from pre-rendered bitmap bytes.
 */
fun buildTsplPayload(
    widthMm: Float,
    heightMm: Float,
    gapMm: Float,
    copies: Int,
    darkness: Int,
    bitmapBytes: ByteArray,
    targetWidthDots: Int,
    targetHeightDots: Int,
    speed: Float = 4.0f,
    xOffsetDots: Int = 0,
    yOffsetDots: Int = 0,
    direction: Int = 1,
    paperType: String = "GAP"
): ByteArray {
    val safeCopies = copies.coerceAtLeast(1)
    val safeDarkness = darkness.coerceIn(0, 15)
    val safeGapMm = gapMm.coerceAtLeast(0f)
    val safeWidthDots = maxOf(1, targetWidthDots)
    val safeHeightDots = maxOf(1, targetHeightDots)
    val widthBytes = (safeWidthDots + 7) / 8

    val sb = StringBuilder()
    sb.append("SIZE ${formatMm(widthMm)} mm,${formatMm(heightMm)} mm\r\n")
    if (paperType.equals("CONTINUOUS", ignoreCase = true)) {
        sb.append("GAP 0 mm,0 mm\r\n")
    } else if (paperType.equals("BLACK_MARK", ignoreCase = true)) {
        sb.append("BLINE ${formatMm(safeGapMm)} mm,0 mm\r\n")
    } else {
        sb.append("GAP ${formatMm(safeGapMm)} mm,0 mm\r\n")
    }
    if (speed > 0f) {
        val speedStr = if (speed % 1f == 0f) speed.toInt().toString() else formatMm(speed)
        sb.append("SPEED $speedStr\r\n")
    }
    if (xOffsetDots != 0 || yOffsetDots != 0) {
        sb.append("REFERENCE $xOffsetDots,$yOffsetDots\r\n")
    }
    sb.append("DIRECTION $direction\r\n")
    sb.append("DENSITY $safeDarkness\r\n")
    sb.append("CLS\r\n")
    sb.append("BITMAP 0,0,$widthBytes,$safeHeightDots,0,")
    val header = sb.toString()

    val footer = "\r\nPRINT 1,$safeCopies\r\n"

    val headerBytes = header.toByteArray(Charsets.US_ASCII)
    val footerBytes = footer.toByteArray(Charsets.US_ASCII)

    val out = ByteArrayOutputStream(headerBytes.size + bitmapBytes.size + footerBytes.size)
    out.write(headerBytes)
    out.write(bitmapBytes)
    out.write(footerBytes)
    return out.toByteArray()
}

/**
 * Builds the complete TSPL binary command stream for printing a label.
 */
fun buildTsplPayload(
    labelBitmap: Bitmap,
    widthMm: Float,
    heightMm: Float,
    gapMm: Float,
    copies: Int,
    darkness: Int,
    invertBits: Boolean = TSPL_INVERT_BITS,
    dpi: Int = 203,
    speed: Float = 4.0f,
    xOffsetDots: Int = 0,
    yOffsetDots: Int = 0,
    direction: Int = 1,
    paperType: String = "GAP"
): ByteArray {
    val dotsPerMm = dpi / 25.4f
    val targetWidthDots = maxOf(1, (widthMm * dotsPerMm).roundToInt())
    val targetHeightDots = maxOf(1, (heightMm * dotsPerMm).roundToInt())

    val srcAspect = labelBitmap.width.toFloat() / labelBitmap.height.toFloat()
    val targetAspect = targetWidthDots.toFloat() / targetHeightDots.toFloat()

    val scaled: Bitmap = if (labelBitmap.width == targetWidthDots && labelBitmap.height == targetHeightDots) {
        labelBitmap
    } else if (kotlin.math.abs(srcAspect - targetAspect) < 0.02f) {
        Bitmap.createScaledBitmap(labelBitmap, targetWidthDots, targetHeightDots, true)
    } else {
        // Aspect-preserving fit and center onto white canvas to avoid stretching
        val scale = minOf(
            targetWidthDots.toFloat() / labelBitmap.width.toFloat(),
            targetHeightDots.toFloat() / labelBitmap.height.toFloat()
        )
        val fitWidth = (labelBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val fitHeight = (labelBitmap.height * scale).roundToInt().coerceAtLeast(1)

        val tempScaled = Bitmap.createScaledBitmap(labelBitmap, fitWidth, fitHeight, true)
        val canvasBitmap = Bitmap.createBitmap(targetWidthDots, targetHeightDots, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val left = (targetWidthDots - fitWidth) / 2f
        val top = (targetHeightDots - fitHeight) / 2f
        canvas.drawBitmap(tempScaled, left, top, null)
        if (tempScaled != labelBitmap) {
            tempScaled.recycle()
        }
        canvasBitmap
    }

    val safeDarkness = darkness.coerceIn(0, 15)
    val rawBytes = tsplBitmapBytes(scaled, invertBits, safeDarkness)
    if (scaled != labelBitmap) {
        scaled.recycle()
    }

    return buildTsplPayload(
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        copies = copies,
        darkness = safeDarkness,
        bitmapBytes = rawBytes,
        targetWidthDots = targetWidthDots,
        targetHeightDots = targetHeightDots,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )
}

data class LabelJob(
    val bitmap: Bitmap,
    val copies: Int = 1
)

data class BatchLabelPrintResult(
    val result: DirectPrintResult,
    val successfulCount: Int
)

/**
 * Prints label directly to a TSPL printer (Bluetooth or USB).
 */
suspend fun printLabel(
    context: Context,
    target: PrinterTarget,
    labelBitmap: Bitmap,
    widthMm: Float,
    heightMm: Float,
    gapMm: Float,
    copies: Int,
    darkness: Int,
    dpi: Int = 203,
    speed: Float = 4.0f,
    xOffsetDots: Int = 0,
    yOffsetDots: Int = 0,
    direction: Int = 1,
    paperType: String = "GAP"
): DirectPrintResult = withContext(Dispatchers.IO) {
    val payload = buildTsplPayload(
        labelBitmap = labelBitmap,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        copies = copies,
        darkness = darkness,
        dpi = dpi,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )
    sendToPrinter(context, target, listOf(payload))
}

suspend fun printLabel(
    context: Context,
    address: String,
    labelBitmap: Bitmap,
    widthMm: Float,
    heightMm: Float,
    gapMm: Float,
    copies: Int,
    darkness: Int,
    dpi: Int = 203,
    speed: Float = 4.0f,
    xOffsetDots: Int = 0,
    yOffsetDots: Int = 0,
    direction: Int = 1,
    paperType: String = "GAP"
): DirectPrintResult {
    val target = if (address.trim().startsWith("usb:", ignoreCase = true)) {
        PrinterTarget.Usb(address.trim())
    } else if (address.isNotBlank()) {
        PrinterTarget.Bluetooth(address.trim())
    } else {
        PrinterTarget.None
    }
    return printLabel(
        context = context,
        target = target,
        labelBitmap = labelBitmap,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        copies = copies,
        darkness = darkness,
        dpi = dpi,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )
}

/**
 * Prints a batch of label jobs to a TSPL printer in a single session.
 * Avoids repeated connect / settle / drain overhead per label.
 */
suspend fun printLabelsBatch(
    context: Context,
    target: PrinterTarget,
    jobs: List<LabelJob>,
    widthMm: Float,
    heightMm: Float,
    gapMm: Float,
    darkness: Int,
    invertBits: Boolean = TSPL_INVERT_BITS,
    dpi: Int = 203,
    speed: Float = 4.0f,
    xOffsetDots: Int = 0,
    yOffsetDots: Int = 0,
    direction: Int = 1,
    paperType: String = "GAP"
): BatchLabelPrintResult = withContext(Dispatchers.IO) {
    if (jobs.isEmpty()) {
        return@withContext BatchLabelPrintResult(DirectPrintResult.Success, 0)
    }

    val payloads = jobs.map { job ->
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        buildTsplPayload(
            labelBitmap = job.bitmap,
            widthMm = widthMm,
            heightMm = heightMm,
            gapMm = gapMm,
            copies = job.copies,
            darkness = darkness,
            invertBits = invertBits,
            dpi = dpi,
            speed = speed,
            xOffsetDots = xOffsetDots,
            yOffsetDots = yOffsetDots,
            direction = direction,
            paperType = paperType
        )
    }
    printLabelPayloadsBatch(context, target, payloads, jobs.map { it.copies })
}

suspend fun printLabelsBatch(
    context: Context,
    address: String,
    jobs: List<LabelJob>,
    widthMm: Float,
    heightMm: Float,
    gapMm: Float,
    darkness: Int,
    invertBits: Boolean = TSPL_INVERT_BITS,
    dpi: Int = 203,
    speed: Float = 4.0f,
    xOffsetDots: Int = 0,
    yOffsetDots: Int = 0,
    direction: Int = 1,
    paperType: String = "GAP"
): BatchLabelPrintResult {
    val target = if (address.trim().startsWith("usb:", ignoreCase = true)) {
        PrinterTarget.Usb(address.trim())
    } else if (address.isNotBlank()) {
        PrinterTarget.Bluetooth(address.trim())
    } else {
        PrinterTarget.None
    }
    return printLabelsBatch(
        context = context,
        target = target,
        jobs = jobs,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        darkness = darkness,
        invertBits = invertBits,
        dpi = dpi,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )
}

/**
 * Sends already-built TSPL payloads (see [buildTsplPayload]) in one session.
 * Lets callers convert and recycle each label bitmap immediately, so large batches
 * never hold every full-resolution bitmap in memory at once.
 */
suspend fun printLabelPayloadsBatch(
    context: Context,
    target: PrinterTarget,
    payloads: List<ByteArray>,
    copiesPerPayload: List<Int>,
    onProgress: ((sentCount: Int, totalCount: Int) -> Unit)? = null
): BatchLabelPrintResult = withContext(Dispatchers.IO) {
    if (payloads.isEmpty()) {
        return@withContext BatchLabelPrintResult(DirectPrintResult.Success, 0)
    }
    var totalSuccessfulCopies = 0
    val res = sendToPrinter(
        context = context,
        target = target,
        segments = payloads,
        perSegmentDelaysMs = List(payloads.size) { 100L },
        onSegmentSent = { index ->
            totalSuccessfulCopies += copiesPerPayload.getOrElse(index) { 1 }
            onProgress?.invoke(index + 1, payloads.size)
        }
    )
    BatchLabelPrintResult(res, totalSuccessfulCopies)
}

suspend fun printLabelPayloadsBatch(
    context: Context,
    address: String,
    payloads: List<ByteArray>,
    copiesPerPayload: List<Int>,
    onProgress: ((sentCount: Int, totalCount: Int) -> Unit)? = null
): BatchLabelPrintResult {
    val target = if (address.trim().startsWith("usb:", ignoreCase = true)) {
        PrinterTarget.Usb(address.trim())
    } else if (address.isNotBlank()) {
        PrinterTarget.Bluetooth(address.trim())
    } else {
        PrinterTarget.None
    }
    return printLabelPayloadsBatch(context, target, payloads, copiesPerPayload, onProgress)
}

object TsplLabelPrinterDriver {
    suspend fun printLabel(
        context: Context,
        target: PrinterTarget,
        labelBitmap: Bitmap,
        widthMm: Float,
        heightMm: Float,
        gapMm: Float,
        copies: Int,
        darkness: Int,
        dpi: Int = 203,
        speed: Float = 4.0f,
        xOffsetDots: Int = 0,
        yOffsetDots: Int = 0,
        direction: Int = 1,
        paperType: String = "GAP"
    ): DirectPrintResult = com.example.techyfipos.printer.printLabel(
        context = context,
        target = target,
        labelBitmap = labelBitmap,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        copies = copies,
        darkness = darkness,
        dpi = dpi,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )

    suspend fun printLabel(
        context: Context,
        address: String,
        labelBitmap: Bitmap,
        widthMm: Float,
        heightMm: Float,
        gapMm: Float,
        copies: Int,
        darkness: Int,
        dpi: Int = 203,
        speed: Float = 4.0f,
        xOffsetDots: Int = 0,
        yOffsetDots: Int = 0,
        direction: Int = 1,
        paperType: String = "GAP"
    ): DirectPrintResult = com.example.techyfipos.printer.printLabel(
        context = context,
        address = address,
        labelBitmap = labelBitmap,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        copies = copies,
        darkness = darkness,
        dpi = dpi,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )

    suspend fun printLabelsBatch(
        context: Context,
        target: PrinterTarget,
        jobs: List<LabelJob>,
        widthMm: Float,
        heightMm: Float,
        gapMm: Float,
        darkness: Int,
        dpi: Int = 203,
        speed: Float = 4.0f,
        xOffsetDots: Int = 0,
        yOffsetDots: Int = 0,
        direction: Int = 1,
        paperType: String = "GAP"
    ): BatchLabelPrintResult = com.example.techyfipos.printer.printLabelsBatch(
        context = context,
        target = target,
        jobs = jobs,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        darkness = darkness,
        dpi = dpi,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )

    suspend fun printLabelsBatch(
        context: Context,
        address: String,
        jobs: List<LabelJob>,
        widthMm: Float,
        heightMm: Float,
        gapMm: Float,
        darkness: Int,
        dpi: Int = 203,
        speed: Float = 4.0f,
        xOffsetDots: Int = 0,
        yOffsetDots: Int = 0,
        direction: Int = 1,
        paperType: String = "GAP"
    ): BatchLabelPrintResult = com.example.techyfipos.printer.printLabelsBatch(
        context = context,
        address = address,
        jobs = jobs,
        widthMm = widthMm,
        heightMm = heightMm,
        gapMm = gapMm,
        darkness = darkness,
        dpi = dpi,
        speed = speed,
        xOffsetDots = xOffsetDots,
        yOffsetDots = yOffsetDots,
        direction = direction,
        paperType = paperType
    )
}

