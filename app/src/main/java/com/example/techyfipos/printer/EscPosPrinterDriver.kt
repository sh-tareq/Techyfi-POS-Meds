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
     * Converts a Bitmap into an ESC/POS GS v 0 raster bit image byte stream.
     * Supports 384 dots (58mm) or 576 dots (80mm).
     */
    fun bitmapToEscPosRaster(src: Bitmap, targetWidthDots: Int = 384, darkness: Int = 8): ByteArray {
        val aspect = src.height.toFloat() / src.width.toFloat()
        val targetHeightDots = (targetWidthDots * aspect).toInt()
        val scaled = Bitmap.createScaledBitmap(src, targetWidthDots, targetHeightDots, true)

        val widthBytes = (targetWidthDots + 7) / 8
        val out = ByteArrayOutputStream()

        // Process in horizontal bands of up to 256 rows to avoid printer buffer overflows
        val maxBandHeight = 256
        var yOffset = 0
        while (yOffset < targetHeightDots) {
            val bandHeight = minOf(maxBandHeight, targetHeightDots - yOffset)

            // GS v 0 m xL xH yL yH
            val xL = (widthBytes and 0xFF).toByte()
            val xH = ((widthBytes shr 8) and 0xFF).toByte()
            val yL = (bandHeight and 0xFF).toByte()
            val yH = ((bandHeight shr 8) and 0xFF).toByte()

            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

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
                    out.write(b)
                }
            }
            yOffset += bandHeight
        }

        if (scaled != src) {
            scaled.recycle()
        }
        return out.toByteArray()
    }

    /**
     * Direct Bluetooth SPP printing over RFCOMM socket using shared BluetoothPrinterTransport.
     * Runs rasterization off the main thread on Dispatchers.Default.
     */
    suspend fun printReceiptDirect(
        context: Context,
        printerAddress: String,
        bitmap: Bitmap,
        paperWidthDots: Int = 384,
        autoKickDrawer: Boolean = false,
        darkness: Int = 8
    ): DirectPrintResult {
        val payload = withContext(Dispatchers.Default) {
            val rasterData = bitmapToEscPosRaster(bitmap, paperWidthDots, darkness)
            val out = ByteArrayOutputStream()
            out.write(CMD_INIT)
            out.write(CMD_ALIGN_CENTER)
            out.write(rasterData)
            out.write(CMD_FEED_4_LINES)
            out.write(CMD_PARTIAL_CUT)
            if (autoKickDrawer) {
                out.write(CMD_DRAWER_KICK)
            }
            out.toByteArray()
        }
        return sendToBluetoothPrinter(context, printerAddress, payload)
    }
}
