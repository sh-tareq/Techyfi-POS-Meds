package com.example.techyfipos.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object EscPosPrinterDriver {
    private const val TAG = "EscPosPrinterDriver"
    private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    // Standard ESC/POS commands
    private val CMD_INIT = byteArrayOf(0x1B, 0x40)
    private val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val CMD_FEED_3_LINES = byteArrayOf(0x1B, 0x64, 0x03)
    private val CMD_FEED_4_LINES = byteArrayOf(0x1B, 0x64, 0x04)
    private val CMD_PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x01)
    private val CMD_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

    /**
     * Pure function returning the grayscale luminance cutoff (0-255) for black dots.
     * 0 -> 110 lightest, 8 -> 174 default, 15 -> 230 darkest.
     */
    fun receiptLuminanceThreshold(darkness: Int): Int = 110 + darkness.coerceIn(0, 15) * 8

    /**
     * Converts a Bitmap into a list of ESC/POS GS v 0 raster bit image bands.
     * Supports 384 dots (58mm) or 576 dots (80mm).
     * Bands are capped at 64 rows to ensure yH = 0 and minimize corrupted rows on packet drop.
     */
    fun bitmapToEscPosBands(src: Bitmap, targetWidthDots: Int = 384, darkness: Int = 8): List<ByteArray> {
        val aspect = src.height.toFloat() / src.width.toFloat()
        val targetHeightDots = (targetWidthDots * aspect).toInt()
        val scaled = if (src.width == targetWidthDots && src.height == targetHeightDots) {
            src
        } else {
            Bitmap.createScaledBitmap(src, targetWidthDots, targetHeightDots, true)
        }

        val widthBytes = (targetWidthDots + 7) / 8
        val bands = mutableListOf<ByteArray>()

        // Process in horizontal bands of up to 64 rows so every band header has yH = 0
        val maxBandHeight = 64
        var yOffset = 0
        while (yOffset < targetHeightDots) {
            val bandHeight = minOf(maxBandHeight, targetHeightDots - yOffset)
            val bandOut = ByteArrayOutputStream(8 + widthBytes * bandHeight)

            // GS v 0 m xL xH yL yH
            val xL = (widthBytes and 0xFF).toByte()
            val xH = ((widthBytes shr 8) and 0xFF).toByte()
            val yL = (bandHeight and 0xFF).toByte()
            val yH = ((bandHeight shr 8) and 0xFF).toByte()

            bandOut.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

            for (y in 0 until bandHeight) {
                val actualY = yOffset + y
                for (byteIndex in 0 until widthBytes) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val x = byteIndex * 8 + bit
                        if (x < targetWidthDots) {
                            val pixel = scaled.getPixel(x, actualY)
                            val alpha = (pixel shr 24) and 0xFF
                            val red = (pixel shr 16) and 0xFF
                            val green = (pixel shr 8) and 0xFF
                            val blue = pixel and 0xFF
                            // Luminance: 0.299R + 0.587G + 0.114B
                            val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                            // In ESC/POS raster, 1 is black (dot printed), 0 is white (blank)
                            val isBlack = alpha > 40 && luminance < receiptLuminanceThreshold(darkness)
                            if (isBlack) {
                                b = b or (1 shl (7 - bit))
                            }
                        }
                    }
                    bandOut.write(b)
                }
            }
            bands.add(bandOut.toByteArray())
            yOffset += bandHeight
        }

        if (scaled != src) {
            scaled.recycle()
        }
        return bands
    }

    /**
     * Converts a Bitmap into an ESC/POS GS v 0 raster bit image byte stream.
     * Backwards-compatible wrapper concatenating all raster bands.
     */
    fun bitmapToEscPosRaster(src: Bitmap, targetWidthDots: Int = 384, darkness: Int = 8): ByteArray {
        val bands = bitmapToEscPosBands(src, targetWidthDots, darkness)
        val totalSize = bands.sumOf { it.size }
        val out = ByteArrayOutputStream(totalSize)
        for (band in bands) {
            out.write(band)
        }
        return out.toByteArray()
    }

    /**
     * Direct Bluetooth SPP printing over RFCOMM socket using shared BluetoothPrinterTransport.
     * Runs rasterization off the main thread on Dispatchers.Default.
     * Transmits receipt commands and bands as distinct timed segments to protect printer buffers.
     */
    suspend fun printReceiptDirect(
        context: Context,
        printerAddress: String,
        bitmap: Bitmap,
        paperWidthDots: Int = 384,
        autoKickDrawer: Boolean = false,
        darkness: Int = 8
    ): DirectPrintResult {
        val (segments, delays) = withContext(Dispatchers.Default) {
            val bands = bitmapToEscPosBands(bitmap, paperWidthDots, darkness)
            val segList = ArrayList<ByteArray>(bands.size + 3)
            val delayList = ArrayList<Long>(bands.size + 3)

            // Segment 1: ESC @ (CMD_INIT) only
            segList.add(CMD_INIT)
            delayList.add(100L)

            // Segment 2: ESC a 1 (CMD_ALIGN_CENTER)
            segList.add(CMD_ALIGN_CENTER)
            delayList.add(20L)

            // Segments 3..n: one segment per GS v 0 band (header + its rows)
            for (band in bands) {
                segList.add(band)
                delayList.add(20L)
            }

            // Last segment: feed, cut, and optional drawer kick
            val endOut = ByteArrayOutputStream()
            endOut.write(CMD_FEED_4_LINES)
            endOut.write(CMD_PARTIAL_CUT)
            if (autoKickDrawer) {
                endOut.write(CMD_DRAWER_KICK)
            }
            segList.add(endOut.toByteArray())
            delayList.add(0L)

            Pair(segList, delayList)
        }
        return sendToBluetoothPrinter(context, printerAddress, segments, delays)
    }
}
