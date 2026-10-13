package com.example.techyfipos.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class ReceiptCutMode {
    FULL,
    PARTIAL,
    NONE;

    companion object {
        fun fromString(value: String?): ReceiptCutMode = when (value?.trim()?.uppercase()) {
            "FULL" -> FULL
            "NONE" -> NONE
            else -> PARTIAL
        }
    }
}

fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

object EscPosPrinterDriver {
    private const val TAG = "EscPosPrinterDriver"
    private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    // Standard ESC/POS commands
    val CMD_INIT = byteArrayOf(0x1B, 0x40)
    val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val CMD_FEED_3_LINES = byteArrayOf(0x1B, 0x64, 0x03)
    val CMD_FEED_4_LINES = byteArrayOf(0x1B, 0x64, 0x04)
    val CMD_PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x01)
    val CMD_FULL_CUT = byteArrayOf(0x1D, 0x56, 0x00)
    val CMD_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

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
        val targetHeightDots = (targetWidthDots * aspect).toInt().coerceAtLeast(1)
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
     * Pure function returning the ESC/POS cut bytes for a given cut mode and command style.
     * The "GS_V_FEED" style (GS V 65/66 n) uses n = 0, meaning "feed to the cutting position, then cut".
     * The user-visible feed lines are already sent separately as ESC d n, because n in GS V is a printer
     * specific motion unit and not a line count. [feedLines] is kept only for call compatibility.
     */
    @Suppress("UNUSED_PARAMETER")
    fun receiptCutBytes(cutMode: ReceiptCutMode, cutCommandStyle: String = "GS_V", feedLines: Int = 4): ByteArray {
        return when (cutMode) {
            ReceiptCutMode.NONE -> byteArrayOf()
            ReceiptCutMode.FULL -> when (cutCommandStyle.trim().uppercase()) {
                "LEGACY_ESC" -> byteArrayOf(0x1B, 0x69)
                "GS_V_FEED" -> byteArrayOf(0x1D, 0x56, 0x41, 0x00)
                else -> CMD_FULL_CUT
            }
            ReceiptCutMode.PARTIAL -> when (cutCommandStyle.trim().uppercase()) {
                "LEGACY_ESC" -> byteArrayOf(0x1B, 0x6D)
                "GS_V_FEED" -> byteArrayOf(0x1D, 0x56, 0x42, 0x00)
                else -> CMD_PARTIAL_CUT
            }
        }
    }

    /**
     * Pure function returning the ESC/POS feed bytes (ESC d n) clamped to 0..10 lines.
     * Returns empty byte array if feedLines <= 0.
     */
    fun receiptFeedBytes(feedLines: Int): ByteArray {
        val lines = feedLines.coerceIn(0, 10)
        return if (lines > 0) byteArrayOf(0x1B, 0x64, lines.toByte()) else byteArrayOf()
    }

    /**
     * Builds the final receipt segment: feed, then cut, then optional drawer kick.
     */
    fun buildReceiptEndSegment(
        cutMode: ReceiptCutMode,
        feedLines: Int,
        autoKickDrawer: Boolean = false,
        cutCommandStyle: String = "GS_V"
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val feed = receiptFeedBytes(feedLines)
        if (feed.isNotEmpty()) {
            out.write(feed)
        }
        val cut = receiptCutBytes(cutMode, cutCommandStyle, feedLines)
        if (cut.isNotEmpty()) {
            out.write(cut)
        }
        if (autoKickDrawer) {
            out.write(CMD_DRAWER_KICK)
        }
        return out.toByteArray()
    }

    /**
     * Pure function assembling timed byte segments for a cut test job.
     * Segment 1: CMD_INIT (delay 100 ms)
     * Segment 2: CMD_ALIGN_CENTER (delay 20 ms)
     * Segments 3..n: raster image bands (delay 20 ms each)
     * Last segment: feed + cut (delay 0 ms, never kicks drawer)
     */
    fun buildCutTestSegments(
        bands: List<ByteArray>,
        cutMode: ReceiptCutMode,
        feedLines: Int,
        cutCommandStyle: String = "GS_V"
    ): Pair<List<ByteArray>, List<Long>> {
        val segList = ArrayList<ByteArray>(bands.size + 3)
        val delayList = ArrayList<Long>(bands.size + 3)

        segList.add(CMD_INIT)
        delayList.add(100L)

        segList.add(CMD_ALIGN_CENTER)
        delayList.add(20L)

        for (band in bands) {
            segList.add(band)
            delayList.add(20L)
        }

        val endSegment = buildReceiptEndSegment(
            cutMode = cutMode,
            feedLines = feedLines,
            autoKickDrawer = false,
            cutCommandStyle = cutCommandStyle
        )
        segList.add(endSegment)
        delayList.add(0L)

        return Pair(segList, delayList)
    }

    /**
     * Generates a test banner bitmap for cutter verification.
     * When [isLong] is true, generates a 1200 rows tall bitmap simulating a 10-item receipt payload.
     */
    fun createCutTestBitmap(
        cutMode: ReceiptCutMode,
        feedLines: Int,
        targetWidthDots: Int = 384,
        isLong: Boolean = false
    ): Bitmap {
        val height = if (isLong) 1200 else 72
        val bitmap = Bitmap.createBitmap(targetWidthDots, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = android.graphics.Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val modeLabel = when (cutMode) {
            ReceiptCutMode.FULL -> "FULL CUT"
            ReceiptCutMode.PARTIAL -> "HALF CUT"
            ReceiptCutMode.NONE -> "NO CUT (FEED ONLY)"
        }
        val clampedFeed = feedLines.coerceIn(0, 10)

        if (!isLong) {
            val text = "CUT TEST: $modeLabel ($clampedFeed L)"
            // Shrink the text until it fits the roll width (58mm is only 384 dots).
            while (paint.textSize > 12f && paint.measureText(text) > targetWidthDots - 16f) {
                paint.textSize -= 1f
            }
            val x = targetWidthDots / 2f
            val y = (height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(text, x, y, paint)
            return bitmap
        }

        // Long receipt test (1200 rows tall): simulates 10 items, patterns, and totals
        val linePaint = android.graphics.Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }
        val textPaint = android.graphics.Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isAntiAlias = true
        }

        // Header
        paint.textSize = 22f
        canvas.drawText("LONG RECEIPT CUT TEST", targetWidthDots / 2f, 40f, paint)
        paint.textSize = 15f
        canvas.drawText("Payload: 1200 rows (approx 10 items)", targetWidthDots / 2f, 68f, paint)
        canvas.drawText("Mode: $modeLabel ($clampedFeed L)", targetWidthDots / 2f, 92f, paint)
        canvas.drawLine(10f, 110f, targetWidthDots - 10f, 110f, linePaint)

        // 10 simulated item rows
        var curY = 140f
        for (i in 1..10) {
            textPaint.isFakeBoldText = true
            textPaint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText("Item #$i: Test Product $i", 15f, curY, textPaint)
            textPaint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText("${i * 125}.00", targetWidthDots - 15f, curY, textPaint)

            textPaint.isFakeBoldText = false
            textPaint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText("Qty: 1 x ${i * 125}.00  CODE-$i", 15f, curY + 22f, textPaint)

            // Pattern stripes
            for (barX in 15 until (targetWidthDots - 15) step 6) {
                canvas.drawLine(barX.toFloat(), curY + 32f, barX.toFloat() + 2f, curY + 44f, linePaint)
            }
            curY += 62f
        }

        // Subtotal & Grand Total
        canvas.drawLine(10f, curY, targetWidthDots - 10f, curY, linePaint)
        curY += 28f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText("Subtotal (10 items):", 15f, curY, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText("6875.00", targetWidthDots - 15f, curY, textPaint)

        curY += 28f
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText("Grand Total:", 15f, curY, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText("6875.00", targetWidthDots - 15f, curY, textPaint)

        // Checkerboard pattern area
        curY += 35f
        canvas.drawLine(10f, curY, targetWidthDots - 10f, curY, linePaint)
        curY += 15f
        val patternStartX = 15
        val patternEndX = targetWidthDots - 15
        var py = curY
        while (py < 1110f) {
            var px = patternStartX
            while (px < patternEndX) {
                if (((px / 8) + (py.toInt() / 8)) % 2 == 0) {
                    canvas.drawRect(px.toFloat(), py, (px + 8).toFloat().coerceAtMost(patternEndX.toFloat()), (py + 8).coerceAtMost(1110f), paint)
                }
                px += 8
            }
            py += 8f
        }

        // Footer banner
        paint.textSize = 20f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("END OF TEST RECEIPT", targetWidthDots / 2f, 1145f, paint)
        paint.textSize = 15f
        canvas.drawText("FEED AND CUT FOLLOWS", targetWidthDots / 2f, 1172f, paint)

        return bitmap
    }

    /**
     * Direct thermal receipt printing over RFCOMM socket or USB bulk endpoint.
     * Runs rasterization off the main thread on Dispatchers.Default.
     * Transmits receipt commands and bands as distinct timed segments to protect printer buffers.
     */
    suspend fun printReceiptDirect(
        context: Context,
        target: PrinterTarget,
        bitmap: Bitmap,
        paperWidthDots: Int = 384,
        autoKickDrawer: Boolean = false,
        darkness: Int = 8,
        cutMode: ReceiptCutMode = ReceiptCutMode.PARTIAL,
        feedLines: Int = 4,
        cutCommandStyle: String = "GS_V"
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
            val endSegment = buildReceiptEndSegment(
                cutMode = cutMode,
                feedLines = feedLines,
                autoKickDrawer = autoKickDrawer,
                cutCommandStyle = cutCommandStyle
            )
            segList.add(endSegment)
            delayList.add(0L)

            Pair(segList, delayList)
        }
        val endSegmentHex = segments.lastOrNull()?.toHexString().orEmpty()
        Log.d(
            TAG,
            "printReceiptDirect: route=direct, target=${target::class.simpleName}(${target.identifier}), cutMode=$cutMode, cutStyle=$cutCommandStyle, feedLines=$feedLines, bands=${segments.size - 3}, totalBytes=${segments.sumOf { it.size }}, endSegmentHex=$endSegmentHex"
        )
        return sendToPrinter(context, target, segments, perSegmentDelaysMs = delays)
    }

    suspend fun printReceiptDirect(
        context: Context,
        printerAddress: String,
        bitmap: Bitmap,
        paperWidthDots: Int = 384,
        autoKickDrawer: Boolean = false,
        darkness: Int = 8,
        cutMode: ReceiptCutMode = ReceiptCutMode.PARTIAL,
        feedLines: Int = 4,
        cutCommandStyle: String = "GS_V"
    ): DirectPrintResult {
        val target = if (printerAddress.trim().startsWith("usb:", ignoreCase = true)) {
            PrinterTarget.Usb(printerAddress.trim())
        } else if (printerAddress.isNotBlank()) {
            PrinterTarget.Bluetooth(printerAddress.trim())
        } else {
            PrinterTarget.None
        }
        return printReceiptDirect(
            context = context,
            target = target,
            bitmap = bitmap,
            paperWidthDots = paperWidthDots,
            autoKickDrawer = autoKickDrawer,
            darkness = darkness,
            cutMode = cutMode,
            feedLines = feedLines,
            cutCommandStyle = cutCommandStyle
        )
    }

    /**
     * Prints a test cut banner and exercises the cutter according to cutMode and feedLines.
     * When [isLong] is true, prints a 1200 rows tall test receipt simulating a 10-item job.
     */
    suspend fun printCutTestDirect(
        context: Context,
        target: PrinterTarget,
        paperWidthDots: Int = 384,
        darkness: Int = 8,
        cutMode: ReceiptCutMode = ReceiptCutMode.PARTIAL,
        feedLines: Int = 4,
        cutCommandStyle: String = "GS_V",
        isLong: Boolean = false
    ): DirectPrintResult {
        val bitmap = createCutTestBitmap(cutMode, feedLines, paperWidthDots, isLong = isLong)
        val (segments, delays) = withContext(Dispatchers.Default) {
            val bands = bitmapToEscPosBands(bitmap, paperWidthDots, darkness)
            buildCutTestSegments(bands, cutMode, feedLines, cutCommandStyle)
        }
        val endSegmentHex = segments.lastOrNull()?.toHexString().orEmpty()
        Log.d(
            TAG,
            "printCutTestDirect: target=${target::class.simpleName}(${target.identifier}), isLong=$isLong, cutMode=$cutMode, cutStyle=$cutCommandStyle, feedLines=$feedLines, bands=${segments.size - 3}, totalBytes=${segments.sumOf { it.size }}, endSegmentHex=$endSegmentHex"
        )
        return sendToPrinter(context, target, segments, perSegmentDelaysMs = delays)
    }

    suspend fun printCutTestDirect(
        context: Context,
        printerAddress: String,
        paperWidthDots: Int = 384,
        darkness: Int = 8,
        cutMode: ReceiptCutMode = ReceiptCutMode.PARTIAL,
        feedLines: Int = 4,
        cutCommandStyle: String = "GS_V",
        isLong: Boolean = false
    ): DirectPrintResult {
        val target = if (printerAddress.trim().startsWith("usb:", ignoreCase = true)) {
            PrinterTarget.Usb(printerAddress.trim())
        } else if (printerAddress.isNotBlank()) {
            PrinterTarget.Bluetooth(printerAddress.trim())
        } else {
            PrinterTarget.None
        }
        return printCutTestDirect(
            context = context,
            target = target,
            paperWidthDots = paperWidthDots,
            darkness = darkness,
            cutMode = cutMode,
            feedLines = feedLines,
            cutCommandStyle = cutCommandStyle,
            isLong = isLong
        )
    }
}
