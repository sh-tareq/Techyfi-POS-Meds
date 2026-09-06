package com.example.techyfipos.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Standardized money arithmetic and rounding utilities for Techyfi POS.
 * Enforces banker's rounding (HALF_EVEN) at 2 decimal places (poisha/cents)
 * to avoid IEEE-754 binary floating point representation drift.
 */
object MoneyUtils {

    /**
     * Rounds an amount to 2 decimal places using Banker's Rounding (HALF_EVEN).
     */
    fun roundMoney(amount: Double): Double {
        if (amount.isNaN() || amount.isInfinite()) return 0.0
        return BigDecimal.valueOf(amount)
            .setScale(2, RoundingMode.HALF_EVEN)
            .toDouble()
    }

    /**
     * Converts a Taka amount into integer poisha (cents).
     */
    fun toPoisha(amount: Double): Long {
        if (amount.isNaN() || amount.isInfinite()) return 0L
        return Math.round(roundMoney(amount) * 100.0)
    }

    /**
     * Converts integer poisha back to Taka.
     */
    fun toTaka(poisha: Long): Double {
        return poisha / 100.0
    }

    /**
     * Formats a double value with South Asian / Bangladeshi lakh-crore digit grouping.
     * e.g. 1000 -> 1,000 | 100000 -> 1,00,000 | 10000000 -> 1,00,00,000
     */
    fun formatBanglaGrouping(amount: Double): String {
        if (amount.isNaN() || amount.isInfinite()) return "0.00"
        val rounded = roundMoney(amount)
        val isNegative = rounded < 0.0
        val absAmount = Math.abs(rounded)
        val longPart = absAmount.toLong()
        val fracPart = Math.round((absAmount - longPart) * 100.0).toInt()

        val longStr = longPart.toString()
        val formattedInt = if (longStr.length <= 3) {
            longStr
        } else {
            val last3 = longStr.substring(longStr.length - 3)
            val rest = longStr.substring(0, longStr.length - 3)
            val sb = StringBuilder()
            var count = 0
            for (i in rest.length - 1 downTo 0) {
                sb.append(rest[i])
                count++
                if (count % 2 == 0 && i != 0) {
                    sb.append(',')
                }
            }
            sb.reverse().toString() + "," + last3
        }

        val result = if (fracPart > 0) {
            "$formattedInt.${String.format(Locale.US, "%02d", fracPart)}"
        } else {
            formattedInt
        }

        return if (isNegative) "-$result" else result
    }
}

/** Extension for convenient rounding */
fun Double.roundMoney(): Double = MoneyUtils.roundMoney(this)
