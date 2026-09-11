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
    val isNegative = amount < 0.0
    val absFormatted = MoneyUtils.formatBanglaGrouping(Math.abs(amount))
    return if (isNegative) "-৳$absFormatted" else "৳$absFormatted"
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

/**
 * Automatically and seamlessly restarts the application after a database restore.
 * Rebinds the application repository, clears ViewModelStore to wipe stale memory states,
 * and relaunches the MainActivity with a fresh task so the user immediately sees all
 * restored data without needing to manually kill and reopen the app.
 */
fun restartApp(context: Context) {
    try {
        val appContext = context.applicationContext
        (appContext as? com.example.techyfipos.TechyfiPOSApplication)?.reloadRepositoryAfterDatabaseRestore()
        val activity = (context as? android.app.Activity) ?: com.example.techyfipos.MainActivity.currentActivity?.get()
        (activity as? androidx.activity.ComponentActivity)?.viewModelStore?.clear()

        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) {
            if (activity != null) {
                activity.finish()
                activity.startActivity(intent)
            } else {
                appContext.startActivity(intent)
            }
        } else {
            activity?.recreate()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        (context as? android.app.Activity)?.recreate()
    }
}

/**
 * Converts Bengali (০-৯), Arabic-Indic (٠-٩), and Persian (۰-۹) digits to standard
 * English ASCII digits ('0'-'9'). Leaves ASCII digits, letters, and symbols unchanged.
 * Essential for barcodes, phone numbers, quantities, and numeric identifiers so that
 * switching app language to Bangla never leaks Bengali numerals into barcodes or DB queries.
 */
fun toEnglishDigits(input: String): String {
    if (input.isEmpty()) return input
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val converted = when (ch) {
            in '০'..'৯' -> '0' + (ch - '০')
            in '٠'..'٩' -> '0' + (ch - '٠')
            in '۰'..'۹' -> '0' + (ch - '۰')
            else -> ch
        }
        sb.append(converted)
    }
    return sb.toString()
}
