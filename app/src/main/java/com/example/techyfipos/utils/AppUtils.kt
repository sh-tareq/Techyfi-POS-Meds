package com.example.techyfipos.utils

import android.content.Context
import com.example.techyfipos.R
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
    fun getAppLocale(): Locale =
        if (isAppBangla()) Locale.forLanguageTag("bn") else Locale.US

    fun formatDateTime(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("dd MMM yyyy, hh:mm a", locale).format(Date(timestamp))

    fun formatDate(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("dd MMM yyyy", locale).format(Date(timestamp))

    fun formatTime(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("hh:mm a", locale).format(Date(timestamp))

    fun formatShortDateTime(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("dd MMM, hh:mm a", locale).format(Date(timestamp))

    fun formatLongDateTime(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("dd MMMM yyyy, hh:mm a", locale).format(Date(timestamp))

    fun formatISO(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

    fun formatExportDate(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestamp))

    fun formatExportDateTime(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestamp))

    fun formatReportTimestamp(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(timestamp))

    fun formatBackupDate(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("MMM d, yyyy • h:mm a", locale).format(Date(timestamp))

    fun formatShortDate(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("dd MMM", locale).format(Date(timestamp))

    fun formatDayMonth(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("EEE, dd MMM", locale).format(Date(timestamp))

    fun formatDayOfWeek(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("EEE", locale).format(Date(timestamp))

    fun formatDayGroup(timestamp: Long, locale: Locale = getAppLocale()): String =
        SimpleDateFormat("EEEE, dd MMMM yyyy", locale).format(Date(timestamp))

    fun formatHourAmPm(hour: Int, locale: Locale = getAppLocale()): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        return SimpleDateFormat("ha", locale).format(cal.time)
    }
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

/**
 * Converts local epoch milliseconds to UTC midnight milliseconds representing the same calendar date.
 * Required for Material 3 DatePicker which operates in UTC midnight.
 */
fun localMillisToUtcMidnight(localMillis: Long): Long {
    val localCal = Calendar.getInstance().apply { timeInMillis = localMillis }
    val year = localCal.get(Calendar.YEAR)
    val month = localCal.get(Calendar.MONTH)
    val day = localCal.get(Calendar.DAY_OF_MONTH)
    val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }
    return utcCal.timeInMillis
}

/**
 * Converts UTC midnight milliseconds from Material 3 DatePicker back to local midnight milliseconds.
 */
fun utcMidnightToLocalMidnight(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val year = utcCal.get(Calendar.YEAR)
    val month = utcCal.get(Calendar.MONTH)
    val day = utcCal.get(Calendar.DAY_OF_MONTH)
    val localCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return localCal.timeInMillis
}

val LocalAppLanguage = androidx.compose.runtime.staticCompositionLocalOf { "en" }

@Volatile
var currentAppLanguage: String = "en"

fun setAppLanguage(lang: String) {
    currentAppLanguage = lang
    try {
        Locale.setDefault(if (lang.equals("bn", ignoreCase = true)) Locale.forLanguageTag("bn") else Locale.US)
    } catch (_: Throwable) {}
}

fun isAppBangla(locale: Locale = Locale.getDefault()): Boolean =
    currentAppLanguage.equals("bn", ignoreCase = true) || locale.language.equals("bn", ignoreCase = true)

fun getLocalizedContext(context: Context, locale: Locale = DateFormatters.getAppLocale()): Context {
    val config = android.content.res.Configuration(context.resources.configuration).apply {
        setLocale(locale)
    }
    return context.createConfigurationContext(config)
}

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

fun formatUnitsSold(units: Double, isBangla: Boolean = isAppBangla()): String {
    val raw = if (units % 1.0 == 0.0) units.toLong().toString() else String.format(Locale.US, "%.2f", units).trimEnd('0').trimEnd('.')
    return if (isBangla) toBanglaDigits(raw) else raw
}

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
    val clean = toEnglishDigits(rawDate.trim())

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

/**
 * Sanitizes numeric input by converting non-English digits (Bengali, Arabic),
 * handling comma/period as decimal separator, and trimming leading zeros.
 */
fun sanitizeNumericInput(rawValue: String): String {
    var hasDecimalSeparator = false
    val sanitized = buildString {
        rawValue.forEach { character ->
            when {
                character.isDigit() -> {
                    val digit = Character.getNumericValue(character)
                    if (digit in 0..9) append(digit)
                }
                character == '.' && !hasDecimalSeparator -> {
                    append('.')
                    hasDecimalSeparator = true
                }
                character == ',' -> {
                    // Ignore commas as thousands separator
                }
            }
        }
    }
    if (sanitized.isEmpty()) return ""

    val decimalIndex = sanitized.indexOf('.')
    val integerPart = (if (decimalIndex >= 0) sanitized.substring(0, decimalIndex) else sanitized)
        .trimStart('0')
    val normalizedInteger = when {
        integerPart.isEmpty() && decimalIndex >= 0 -> "0"
        integerPart.isEmpty() -> "0"
        else -> integerPart
    }

    return if (decimalIndex >= 0) {
        val fractionPart = sanitized.substring(decimalIndex + 1)
        "$normalizedInteger.$fractionPart"
    } else {
        normalizedInteger
    }
}

/**
 * Maps database and repository exception messages to localized string resource IDs if recognized.
 */
fun mapErrorToLocalizedMessageResId(msg: String?): Int? {
    if (msg.isNullOrBlank()) return null
    return when {
        // Specific credit limit supervisor authorization MUST precede generic supervisor check
        msg.contains("Supervisor authorization required to exceed customer credit limit", ignoreCase = true) ||
            (msg.contains("credit limit", ignoreCase = true) && msg.contains("authorization", ignoreCase = true)) ->
            R.string.error_credit_limit_exceeded_auth

        msg.contains("Supervisor authorization is required to apply discount", ignoreCase = true) ->
            R.string.error_supervisor_auth_required_discount
        msg.contains("Owner privileges required", ignoreCase = true) ->
            R.string.error_unauthorized_owner
        msg.contains("Supervisor", ignoreCase = true) ->
            R.string.error_unauthorized_supervisor
        msg.contains("Staff management permission required", ignoreCase = true) ||
            msg.contains("Permission", ignoreCase = true) ->
            R.string.error_unauthorized_permission
        msg.contains("Insufficient stock", ignoreCase = true) ->
            R.string.error_insufficient_stock

        // Voucher errors
        msg.contains("not found", ignoreCase = true) && msg.contains("Voucher", ignoreCase = true) ->
            R.string.error_voucher_not_found
        msg.contains("Invalid store credit voucher", ignoreCase = true) ->
            R.string.error_voucher_not_found
        msg.contains("expired", ignoreCase = true) && msg.contains("Voucher", ignoreCase = true) ->
            R.string.error_voucher_expired
        msg.contains("Voucher has no remaining balance", ignoreCase = true) ||
            msg.contains("is exhausted", ignoreCase = true) ||
            (msg.contains("voucher", ignoreCase = true) && (msg.contains("not active", ignoreCase = true) || msg.contains("cancelled", ignoreCase = true))) ->
            R.string.error_voucher_inactive_or_exhausted

        // Store credit errors
        msg.contains("Insufficient store credit balance", ignoreCase = true) ->
            R.string.error_insufficient_store_credit
        msg.contains("Customer phone number is required for store credit", ignoreCase = true) ||
            msg.contains("Customer phone number is required for Store Credit", ignoreCase = true) ->
            R.string.error_store_credit_phone_required

        // Customer deletion blocks
        msg.contains("Cannot delete sale", ignoreCase = true) && msg.contains("return", ignoreCase = true) ->
            R.string.error_cannot_delete_sale_has_returns
        msg.contains("Cannot delete or archive customer with outstanding dues", ignoreCase = true) ||
            msg.contains("Cannot permanently delete customer with outstanding dues", ignoreCase = true) ||
            msg.contains("Cannot delete due ledger with outstanding dues", ignoreCase = true) ->
            R.string.error_customer_has_dues
        msg.contains("store credit balance", ignoreCase = true) && (msg.contains("Cannot delete", ignoreCase = true) || msg.contains("archive", ignoreCase = true)) ->
            R.string.error_customer_has_store_credit
        msg.contains("voucher balance", ignoreCase = true) && (msg.contains("Cannot delete", ignoreCase = true) || msg.contains("archive", ignoreCase = true)) ->
            R.string.error_customer_has_active_vouchers

        msg.contains("Cannot delete supplier with active due balance", ignoreCase = true) ->
            R.string.error_supplier_has_dues

        // Lockout
        msg.contains("Authentication locked out for", ignoreCase = true) ||
            msg.contains("Terminal locked for", ignoreCase = true) ->
            R.string.auth_locked_out_seconds

        // User management
        msg.contains("User not found", ignoreCase = true) ->
            R.string.error_user_not_found
        msg.contains("Cannot delete your own account", ignoreCase = true) ->
            R.string.error_cannot_delete_own_account
        msg.contains("Users cannot change their own role", ignoreCase = true) ->
            R.string.error_cannot_change_own_role
        msg.contains("Users cannot change their own active status", ignoreCase = true) ->
            R.string.error_cannot_change_own_status
        msg.contains("Users cannot change their own maximum discount", ignoreCase = true) ->
            R.string.error_cannot_change_own_discount
        msg.contains("The only owner account cannot be changed", ignoreCase = true) ||
            msg.contains("cannot be changed to another role", ignoreCase = true) ->
            R.string.error_cannot_change_role_last_owner
        msg.contains("The last owner account cannot be deleted", ignoreCase = true) ||
            msg.contains("last remaining Owner account cannot be removed", ignoreCase = true) ->
            R.string.error_cannot_delete_last_owner
        msg.contains("cannot be deactivated", ignoreCase = true) ->
            R.string.error_cannot_deactivate_last_owner
        msg.contains("Non-owners cannot", ignoreCase = true) ->
            R.string.error_non_owners_privilege_escalation
        msg.contains("Cannot promote staff to owner", ignoreCase = true) ->
            R.string.users_cannot_promote_to_owner
        msg.contains("Cannot grant maximum discount higher than your own", ignoreCase = true) ->
            R.string.error_cannot_grant_higher_discount

        // Supplier expense
        msg.contains("Supplier payment expenses are auto-generated", ignoreCase = true) ->
            R.string.error_supplier_expense_auto_locked

        // Supplier overpayment
        msg.contains("Payment exceeds the outstanding supplier balance", ignoreCase = true) ->
            R.string.error_supplier_payment_exceeds_due

        // Returns
        msg.contains("Product was not part of this sale", ignoreCase = true) ->
            R.string.error_product_not_in_sale
        msg.contains("Return quantity exceeds quantity sold", ignoreCase = true) ->
            R.string.error_return_quantity_exceeds_sold

        // Cart & Barcode
        msg.contains("Product barcode already exists", ignoreCase = true) ->
            R.string.products_barcode_already_exists
        msg.contains("Remove this product from the active and held carts", ignoreCase = true) ||
            msg.contains("active and held carts", ignoreCase = true) ->
            R.string.error_product_in_saved_carts

        // Passwords
        msg.contains("Password must be at least 4 characters", ignoreCase = true) ->
            R.string.error_backup_password_min_length
        msg.contains("Passwords do not match", ignoreCase = true) ->
            R.string.error_backup_passwords_do_not_match

        // Sale validation
        msg.contains("Sale amounts must be finite", ignoreCase = true) ||
            msg.contains("Sale amounts cannot be negative", ignoreCase = true) ||
            msg.contains("Sale quantity must be finite", ignoreCase = true) ||
            msg.contains("Sale price must be finite", ignoreCase = true) ->
            R.string.error_sale_amounts_invalid
        msg.contains("A sale must contain at least one product", ignoreCase = true) ->
            R.string.error_sale_empty_cart
        msg.contains("Customer name and phone are required", ignoreCase = true) ||
            msg.contains("A wallet number is required", ignoreCase = true) ||
            msg.contains("The card's last 4 digits are required", ignoreCase = true) ->
            R.string.error_payment_details_required
        msg.contains("Split payment rows do not add up", ignoreCase = true) ->
            R.string.error_split_total_mismatch

        // Existing entities - ONLY match "Product ... no longer exists" so Supplier and Due ledger don't match!
        msg.contains("Product no longer exists", ignoreCase = true) ||
            (msg.contains("Product", ignoreCase = true) && msg.contains("no longer exists", ignoreCase = true)) ->
            R.string.error_product_not_found
        msg.contains("Supplier no longer exists", ignoreCase = true) || msg.contains("Supplier not found", ignoreCase = true) ->
            R.string.error_supplier_not_found
        msg.contains("Due ledger no longer exists", ignoreCase = true) ->
            R.string.error_due_ledger_no_longer_exists
        msg.contains("Sale not found", ignoreCase = true) ->
            R.string.error_sale_not_found
        msg.contains("Invoice not found", ignoreCase = true) ->
            R.string.error_invoice_not_found
        msg.contains("Supplier payment not found", ignoreCase = true) ->
            R.string.error_supplier_payment_not_found

        msg.contains("Cannot void invoice payments directly", ignoreCase = true) ->
            R.string.supplier_cannot_void_invoice_payment
        msg.contains("constraint conflict", ignoreCase = true) ||
            msg.contains("duplicate record", ignoreCase = true) ||
            msg.contains("UNIQUE constraint failed", ignoreCase = true) ->
            R.string.error_duplicate_record
        else -> null
    }
}

fun mapErrorToLocalizedMessage(rawMessage: String?): Int? = mapErrorToLocalizedMessageResId(rawMessage)

fun mapErrorToLocalizedMessage(error: Throwable?): Int? =
    mapErrorToLocalizedMessageResId(error?.message ?: error?.localizedMessage)

/**
 * Maps database and repository exception messages to localized, human-friendly string resources.
 */
fun getLocalizedRepositoryErrorMessage(context: Context, error: Throwable?): String {
    if (error == null) return context.getString(R.string.error_generic)
    val msg = error.message ?: error.localizedMessage ?: ""
    val resId = mapErrorToLocalizedMessageResId(msg)
    return when {
        resId != null -> context.getString(resId)
        msg.isNotBlank() -> msg
        else -> context.getString(R.string.error_generic)
    }
}

fun getLocalizedRepositoryErrorMessage(context: Context, rawMessage: String?): String {
    if (rawMessage.isNullOrBlank()) return context.getString(R.string.error_generic)
    return getLocalizedRepositoryErrorMessage(context, Exception(rawMessage))
}

