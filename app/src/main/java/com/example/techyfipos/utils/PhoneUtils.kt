package com.example.techyfipos.utils

/**
 * Canonicalizes a Bangladeshi mobile number to the local 11-digit form (e.g. "01712345678")
 * so the same customer typed as "+8801712345678", "8801712345678", or "1712345678" resolves
 * to one ledger key instead of silently creating separate due/loyalty accounts.
 */
fun normalizePhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.length > 10 && digits.startsWith("880") -> "0" + digits.removePrefix("880")
        digits.length == 10 && digits.startsWith("1") -> "0$digits"
        else -> digits
    }
}
