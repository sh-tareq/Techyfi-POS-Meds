package com.example.techyfipos.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

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
     * Converts a Bitmap into an ESC/POS GS v 0 raster bit image byte stream.
     * Supports 384 dots (58mm) or 576 dots (80mm).
     */
    fun bitmapToEscPosRaster(src: Bitmap, targetWidthDots: Int = 384): ByteArray {
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
                            val isBlack = alpha > 40 && luminance < 170
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
     * Direct Bluetooth SPP printing over RFCOMM socket.
     * Takes < 1 second to print the receipt directly on a physical thermal printer.
     */
    @SuppressLint("MissingPermission")
    suspend fun printReceiptDirect(
        context: Context,
        printerAddress: String,
        bitmap: Bitmap,
        paperWidthDots: Int = 384,
        autoKickDrawer: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (printerAddress.isBlank()) {
            Log.w(TAG, "No printer address configured")
            return@withContext false
        }

        val hasBtPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!hasBtPermission) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
            return@withContext false
        }

        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: return@withContext false
            val device = adapter.bondedDevices.firstOrNull { it.address == printerAddress }
            if (device == null) {
                Log.w(TAG, "Printer $printerAddress not found in bonded devices")
                return@withContext false
            }

            adapter.cancelDiscovery()
            val socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
            socket.use { s ->
                s.connect()
                val os: OutputStream = s.outputStream

                // 1. Initialize printer
                os.write(CMD_INIT)
                os.write(CMD_ALIGN_CENTER)

                // 2. Write raster image data
                val rasterData = bitmapToEscPosRaster(bitmap, paperWidthDots)
                os.write(rasterData)

                // 3. Feed paper & cut
                os.write(CMD_FEED_4_LINES)
                os.write(CMD_PARTIAL_CUT)

                // 4. Optional Cash Drawer Kick
                if (autoKickDrawer) {
                    os.write(CMD_DRAWER_KICK)
                }

                os.flush()
            }
            Log.i(TAG, "Direct receipt print succeeded to $printerAddress")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed direct Bluetooth ESC/POS print: ${e.message}", e)
            false
        }
    }
}
