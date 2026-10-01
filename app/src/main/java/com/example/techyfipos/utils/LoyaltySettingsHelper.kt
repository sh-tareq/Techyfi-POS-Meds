package com.example.techyfipos.utils

import android.content.Context
import android.content.SharedPreferences

data class LoyaltyProgramSettings(
    val takaPerPoint: Double = 100.0,
    val pointCashValue: Double = 1.0,
    val minRedeemPoints: Int = 20,
    val maxBillPercentPayable: Int = 50,
    val silverSpendThreshold: Double = 3000.0,
    val goldSpendThreshold: Double = 7500.0,
    val platinumSpendThreshold: Double = 15000.0,
    val pointsExpiryDays: Int = 365,
    val isTiersEnabled: Boolean = true
)

object LoyaltySettingsHelper {
    private const val PREFS_NAME = "loyalty_program_prefs"

    private fun SharedPreferences.getDouble(key: String, defaultValue: Double): Double {
        return if (contains(key)) {
            try {
                java.lang.Double.longBitsToDouble(getLong(key, defaultValue.toBits()))
            } catch (e: ClassCastException) {
                getFloat(key, defaultValue.toFloat()).toDouble()
            }
        } else {
            defaultValue
        }
    }

    private fun SharedPreferences.Editor.putDouble(key: String, value: Double): SharedPreferences.Editor {
        return putLong(key, value.toBits())
    }

    fun getSettings(context: Context): LoyaltyProgramSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LoyaltyProgramSettings(
            takaPerPoint = prefs.getDouble("taka_per_point", 100.0),
            pointCashValue = prefs.getDouble("point_cash_value", 1.0),
            minRedeemPoints = prefs.getInt("min_redeem_points", 20),
            maxBillPercentPayable = prefs.getInt("max_bill_percent_payable", 50),
            silverSpendThreshold = prefs.getDouble("silver_spend_threshold", 3000.0),
            goldSpendThreshold = prefs.getDouble("gold_spend_threshold", 7500.0),
            platinumSpendThreshold = prefs.getDouble("platinum_spend_threshold", 15000.0),
            pointsExpiryDays = prefs.getInt("points_expiry_days", 365),
            isTiersEnabled = prefs.getBoolean("is_tiers_enabled", true)
        )
    }

    fun saveSettings(context: Context, settings: LoyaltyProgramSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putDouble("taka_per_point", settings.takaPerPoint)
            .putDouble("point_cash_value", settings.pointCashValue)
            .putInt("min_redeem_points", settings.minRedeemPoints)
            .putInt("max_bill_percent_payable", settings.maxBillPercentPayable)
            .putDouble("silver_spend_threshold", settings.silverSpendThreshold)
            .putDouble("gold_spend_threshold", settings.goldSpendThreshold)
            .putDouble("platinum_spend_threshold", settings.platinumSpendThreshold)
            .putInt("points_expiry_days", settings.pointsExpiryDays)
            .putBoolean("is_tiers_enabled", settings.isTiersEnabled)
            .apply()
    }
}
