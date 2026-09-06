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

    fun getSettings(context: Context): LoyaltyProgramSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LoyaltyProgramSettings(
            takaPerPoint = prefs.getFloat("taka_per_point", 100.0f).toDouble(),
            pointCashValue = prefs.getFloat("point_cash_value", 1.0f).toDouble(),
            minRedeemPoints = prefs.getInt("min_redeem_points", 20),
            maxBillPercentPayable = prefs.getInt("max_bill_percent_payable", 50),
            silverSpendThreshold = prefs.getFloat("silver_spend_threshold", 3000.0f).toDouble(),
            goldSpendThreshold = prefs.getFloat("gold_spend_threshold", 7500.0f).toDouble(),
            platinumSpendThreshold = prefs.getFloat("platinum_spend_threshold", 15000.0f).toDouble(),
            pointsExpiryDays = prefs.getInt("points_expiry_days", 365),
            isTiersEnabled = prefs.getBoolean("is_tiers_enabled", true)
        )
    }

    fun saveSettings(context: Context, settings: LoyaltyProgramSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("taka_per_point", settings.takaPerPoint.toFloat())
            .putFloat("point_cash_value", settings.pointCashValue.toFloat())
            .putInt("min_redeem_points", settings.minRedeemPoints)
            .putInt("max_bill_percent_payable", settings.maxBillPercentPayable)
            .putFloat("silver_spend_threshold", settings.silverSpendThreshold.toFloat())
            .putFloat("gold_spend_threshold", settings.goldSpendThreshold.toFloat())
            .putFloat("platinum_spend_threshold", settings.platinumSpendThreshold.toFloat())
            .putInt("points_expiry_days", settings.pointsExpiryDays)
            .putBoolean("is_tiers_enabled", settings.isTiersEnabled)
            .apply()
    }
}
