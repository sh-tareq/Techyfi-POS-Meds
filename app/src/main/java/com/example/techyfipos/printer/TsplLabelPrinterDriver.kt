package com.example.techyfipos.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
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
    targetHeightDots: Int
): ByteArray {
    val safeCopies = copies.coerceAtLeast(1)
    val safeDarkness = darkness.coerceIn(0, 15)
    val safeGapMm = gapMm.coerceAtLeast(0f)
    val safeWidthDots = maxOf(1, targetWidthDots)
    val safeHeightDots = maxOf(1, targetHeightDots)
    val widthBytes = (safeWidthDots + 7) / 8

    val header = "SIZE ${formatMm(widthMm)} mm,${formatMm(heightMm)} mm\r\n" +
        "GAP ${formatMm(safeGapMm)} mm,0 mm\r\n" +
        "DIRECTION 1\r\n" +
        "DENSITY $safeDarkness\r\n" +
        "CLS\r\n" +
        "BITMAP 0,0,$widthBytes,$safeHeightDots,0,"

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
    invertBits: Boolean = TSPL_INVERT_BITS
): ByteArray {
    val targetWidthDots = maxOf(1, (widthMm * 8f).roundToInt())
    val targetHeightDots = maxOf(1, (heightMm * 8f).roundToInt())

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
        targetHeightDots = targetHeightDots
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
    darkness: Int
): DirectPrintResult = withContext(Dispatchers.IO) {
    val payload = buildTsplPayload(labelBitmap, widthMm, heightMm, gapMm, copies, darkness)
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
    darkness: Int
): DirectPrintResult {
    val target = if (address.trim().startsWith("usb:", ignoreCase = true)) {
        PrinterTarget.Usb(address.trim())
    } else if (address.isNotBlank()) {
        PrinterTarget.Bluetooth(address.trim())
    } else {
        PrinterTarget.None
    }
    return printLabel(context, target, labelBitmap, widthMm, heightMm, gapMm, copies, darkness)
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
    invertBits: Boolean = TSPL_INVERT_BITS
): BatchLabelPrintResult = withContext(Dispatchers.IO) {
    if (jobs.isEmpty()) {
        return@withContext BatchLabelPrintResult(DirectPrintResult.Success, 0)
    }

    val payloads = jobs.map { job ->
        buildTsplPayload(
            labelBitmap = job.bitmap,
            widthMm = widthMm,
            heightMm = heightMm,
            gapMm = gapMm,
            copies = job.copies,
            darkness = darkness,
            invertBits = invertBits
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
    invertBits: Boolean = TSPL_INVERT_BITS
): BatchLabelPrintResult {
    val target = if (address.trim().startsWith("usb:", ignoreCase = true)) {
        PrinterTarget.Usb(address.trim())
    } else if (address.isNotBlank()) {
        PrinterTarget.Bluetooth(address.trim())
    } else {
        PrinterTarget.None
    }
    return printLabelsBatch(context, target, jobs, widthMm, heightMm, gapMm, darkness, invertBits)
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
        darkness: Int
    ): DirectPrintResult = com.example.techyfipos.printer.printLabel(
        context, target, labelBitmap, widthMm, heightMm, gapMm, copies, darkness
    )

    suspend fun printLabel(
        context: Context,
        address: String,
        labelBitmap: Bitmap,
        widthMm: Float,
        heightMm: Float,
        gapMm: Float,
        copies: Int,
        darkness: Int
    ): DirectPrintResult = com.example.techyfipos.printer.printLabel(
        context, address, labelBitmap, widthMm, heightMm, gapMm, copies, darkness
    )

    suspend fun printLabelsBatch(
        context: Context,
        target: PrinterTarget,
        jobs: List<LabelJob>,
        widthMm: Float,
        heightMm: Float,
        gapMm: Float,
        darkness: Int
    ): BatchLabelPrintResult = com.example.techyfipos.printer.printLabelsBatch(
        context, target, jobs, widthMm, heightMm, gapMm, darkness
    )

    suspend fun printLabelsBatch(
        context: Context,
        address: String,
        jobs: List<LabelJob>,
        widthMm: Float,
        heightMm: Float,
        gapMm: Float,
        darkness: Int
    ): BatchLabelPrintResult = com.example.techyfipos.printer.printLabelsBatch(
        context, address, jobs, widthMm, heightMm, gapMm, darkness
    )
}

