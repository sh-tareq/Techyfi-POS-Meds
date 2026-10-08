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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormatters {
    fun formatDateTime(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date(timestamp))

    fun formatDate(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("dd MMM yyyy", locale).format(Date(timestamp))

    fun formatTime(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("hh:mm a", locale).format(Date(timestamp))

    fun formatShortDateTime(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("dd MMM, hh:mm a", locale).format(Date(timestamp))

    fun formatLongDateTime(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("dd MMMM yyyy, hh:mm a", locale).format(Date(timestamp))

    fun formatISO(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    fun formatExportDate(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))

    fun formatExportDateTime(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestamp))

    fun formatReportTimestamp(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(timestamp))

    fun formatBackupDate(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("MMM d, yyyy • h:mm a", locale).format(Date(timestamp))

    fun formatShortDate(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("dd MMM", locale).format(Date(timestamp))

    fun formatDayMonth(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEE, dd MMM", locale).format(Date(timestamp))

    fun formatDayOfWeek(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEE", locale).format(Date(timestamp))

    fun formatDayGroup(timestamp: Long, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("EEEE, dd MMMM yyyy", locale).format(Date(timestamp))
}

/**
 * Returns epoch timestamp in milliseconds for the very beginning of the day (00:00:00.000).
 */
fun startOfDay(millis: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Returns epoch timestamp in milliseconds for the very end of the day (23:59:59.999).
 */
fun endOfDay(millis: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

fun isAppBangla(locale: Locale = Locale.getDefault()): Boolean =
    locale.language.equals("bn", ignoreCase = true)

fun toBanglaDigits(str: String): String {
    val digits = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
    )
    return str.map { digits[it] ?: it }.joinToString("")
}

fun formatTaka(amount: Double, isBangla: Boolean = isAppBangla()): String {
    val isNegative = amount < 0.0
    val absFormatted = MoneyUtils.formatBanglaGrouping(Math.abs(amount))
    val en = if (isNegative) "-৳$absFormatted" else "৳$absFormatted"
    return if (isBangla) toBanglaDigits(en) else en
}

fun formatMoney(amount: Double, isBangla: Boolean = isAppBangla()): String =
    formatTaka(amount, isBangla)

fun formatTakaBn(amount: Double): String = formatTaka(amount, isBangla = true)

/**
 * Checks whether a unit string represents a fractional/weighable quantity (e.g., Kg, Gram, Ltr, Mtr).
 * Case-insensitive and supports multiple aliases ("Gram", "gm", "g", "Kg", "Ltr", etc.).
 */
fun isFractionalUnit(unit: String?): Boolean {
    if (unit == null) return false
    val normalized = unit.trim().lowercase()
    return normalized in listOf(
        "kg", "kgs", "kilogram", "kilograms",
        "gm", "g", "gram", "grams",
        "ltr", "liter", "litre", "liters", "litres",
        "mtr", "meter", "metre", "meters", "metres",
        "ml"
    )
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
        val pixels = IntArray(size * size)
        val black = android.graphics.Color.BLACK
        val white = android.graphics.Color.WHITE
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) black else white
            }
        }
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
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
                isLenient = false
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, day)
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }
            try {
                return cal.timeInMillis
            } catch (e: Exception) {
                return null
            }
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
