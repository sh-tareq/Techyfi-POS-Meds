package com.example.techyfipos.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

fun formatTaka(amount: Double): String {
    val rounded = Math.round(amount * 100.0) / 100.0
    val hasPaisa = Math.abs(rounded - Math.round(rounded)) > 0.001
    val pattern = if (hasPaisa) "#,##0.00" else "#,##0"
    val formatter = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
    val formatted = formatter.format(rounded)
    return "৳$formatted"
}

fun beepAndVibrate(context: Context) {
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(100)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun generateQrCodeBitmap(text: String, size: Int = 512): android.graphics.Bitmap? {
    return try {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Robustly parses user-entered or stored expiry dates across all formats:
 * - DD-MM-YY (e.g. "27-08-27" -> Aug 27, 2027)
 * - DD-MM-YYYY (e.g. "27-08-2027" -> Aug 27, 2027)
 * - YYYY-MM-DD (e.g. "2027-08-27" -> Aug 27, 2027)
 * - DD/MM/YY or DD/MM/YYYY
 * - MM-YY or MM-YYYY
 * Returns timestamp at end-of-day (23:59:59.999) or null if unparseable.
 */
fun parseExpiryDateToMillis(rawDate: String?): Long? {
    if (rawDate.isNullOrBlank()) return null
    val clean = rawDate.trim()

    val separators = charArrayOf('-', '/', '.', ' ')
    val parts = clean.split(*separators).filter { it.isNotBlank() }

    if (parts.size == 3) {
        val n1 = parts[0].toIntOrNull() ?: return null
        val n2 = parts[1].toIntOrNull() ?: return null
        val n3 = parts[2].toIntOrNull() ?: return null

        val day: Int
        val month: Int // 1-12
        val year: Int

        if (parts[0].length == 4) {
            // YYYY-MM-DD
            year = n1
            month = n2
            day = n3
        } else {
            // DD-MM-YYYY or DD-MM-YY
            day = n1
            month = n2
            year = if (n3 < 100) 2000 + n3 else n3
        }

        if (month in 1..12 && day in 1..31 && year in 1970..2100) {
            val cal = java.util.Calendar.getInstance().apply {
                clear()
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, day)
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            return cal.timeInMillis
        }
    } else if (parts.size == 2) {
        // MM-YY or MM-YYYY
        val n1 = parts[0].toIntOrNull() ?: return null
        val n2 = parts[1].toIntOrNull() ?: return null
        val month = n1
        val year = if (n2 < 100) 2000 + n2 else n2
        if (month in 1..12 && year in 1970..2100) {
            val cal = java.util.Calendar.getInstance().apply {
                clear()
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.DAY_OF_MONTH, getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            return cal.timeInMillis
        }
    }

    // Fallback standard formats
    val formats = listOf(
        "dd-MM-yy",
        "dd-MM-yyyy",
        "yyyy-MM-dd",
        "dd/MM/yy",
        "dd/MM/yyyy",
        "yyyy/MM/dd",
        "d-M-yy",
        "d-M-yyyy"
    )
    for (fmt in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
            sdf.isLenient = false
            val parsed = sdf.parse(clean)
            if (parsed != null) {
                val cal = java.util.Calendar.getInstance().apply {
                    time = parsed
                    val y = get(java.util.Calendar.YEAR)
                    if (y < 100) {
                        set(java.util.Calendar.YEAR, 2000 + y)
                    }
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                    set(java.util.Calendar.MINUTE, 59)
                    set(java.util.Calendar.SECOND, 59)
                }
                return cal.timeInMillis
            }
        } catch (ignored: Exception) {}
    }
    return null
}

