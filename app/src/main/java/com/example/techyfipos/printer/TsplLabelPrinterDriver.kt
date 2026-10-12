package com.example.techyfipos.printer

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.roundToInt

const val TSPL_INVERT_BITS: Boolean = false

private fun formatMm(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

/**
 * Pure function converting pixel coordinates into TSPL raster BITMAP payload bytes.
 * In TSPL BITMAP data, bit 0 = black (printed) and bit 1 = white (blank), opposite of ESC/POS.
 * TSPL_INVERT_BITS constant allows flipping the polarity in one place if needed.
 */
fun tsplBitmapBytes(
    widthDots: Int,
    heightDots: Int,
    invertBits: Boolean = TSPL_INVERT_BITS,
    getPixel: (x: Int, y: Int) -> Int
): ByteArray {
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
                    alpha > 40 && luminance < 170
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
fun tsplBitmapBytes(bitmap: Bitmap, invertBits: Boolean = TSPL_INVERT_BITS): ByteArray =
    tsplBitmapBytes(bitmap.width, bitmap.height, invertBits) { x, y -> bitmap.getPixel(x, y) }

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
    val safeCopies = copies.coerceAtLeast(1)
    val safeDarkness = darkness.coerceIn(0, 15)
    val safeGapMm = gapMm.coerceAtLeast(0f)
    val targetWidthDots = maxOf(1, (widthMm * 8f).roundToInt())
    val targetHeightDots = maxOf(1, (heightMm * 8f).roundToInt())
    val widthBytes = (targetWidthDots + 7) / 8

    val scaled = if (labelBitmap.width == targetWidthDots && labelBitmap.height == targetHeightDots) {
        labelBitmap
    } else {
        Bitmap.createScaledBitmap(labelBitmap, targetWidthDots, targetHeightDots, true)
    }

    val rawBytes = tsplBitmapBytes(scaled, invertBits)
    if (scaled != labelBitmap) {
        scaled.recycle()
    }

    val header = "SIZE ${formatMm(widthMm)} mm,${formatMm(heightMm)} mm\r\n" +
        "GAP ${formatMm(safeGapMm)} mm,0 mm\r\n" +
        "DIRECTION 1\r\n" +
        "DENSITY $safeDarkness\r\n" +
        "CLS\r\n" +
        "BITMAP 0,0,$widthBytes,$targetHeightDots,0,"

    val footer = "\r\nPRINT 1,$safeCopies\r\n"

    val headerBytes = header.toByteArray(Charsets.US_ASCII)
    val footerBytes = footer.toByteArray(Charsets.US_ASCII)

    val out = ByteArrayOutputStream(headerBytes.size + rawBytes.size + footerBytes.size)
    out.write(headerBytes)
    out.write(rawBytes)
    out.write(footerBytes)
    return out.toByteArray()
}

/**
 * Prints label directly to a Bluetooth TSPL printer (e.g. Deli E886BW).
 */
suspend fun printLabel(
    context: Context,
    address: String,
    labelBitmap: Bitmap,
    widthMm: Float,
    heightMm: Float,
    gapMm: Float,
    copies: Int,
    darkness: Int
): DirectPrintResult = withContext(Dispatchers.IO) {
    val payload = buildTsplPayload(labelBitmap, widthMm, heightMm, gapMm, copies, darkness)
    sendToBluetoothPrinter(context, address, payload)
}

object TsplLabelPrinterDriver {
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
}
