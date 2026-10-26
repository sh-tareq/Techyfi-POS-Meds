package com.techyfiposmeds.data.clinical

/**
 * DGDA & WHO Good Distribution Practice (GDP) Refrigerator & Biologicals Cold-Chain Helper.
 * Regulates 2°C – 8°C storage monitoring for insulins, vaccines, biologicals, and temperature-sensitive injectables.
 */
object ColdChainHelper {

    const val MIN_SAFE_TEMP_C = 2.0
    const val MAX_SAFE_TEMP_C = 8.0

    enum class TempBreachStatus {
        NORMAL,
        FREEZE_RISK,   // < 2.0°C (Risk of protein denaturing / crystallization)
        HEAT_BREACH    // > 8.0°C (Risk of potency decay / bacterial contamination)
    }

    private val COLD_CHAIN_KEYWORDS = setOf(
        "insulin", "glargine", "detemir", "degludec", "lispro", "aspart", "isophane",
        "vaccine", "toxoid", "verorab", "rabivax", "tetavax", "genevac", "vaxitet", "typhim", "vaxigrip",
        "enoxaparin", "clexane", "heparin", "erythropoietin", "epoetin", "filgrastim",
        "oxytocin", "somatropin", "growth hormone", "interferon", "adalimumab", "infliximab",
        "immunoglobulin", "antivenom", "botulinum", "latanoprost", "recormon", "epotin",
        "humulin", "mixtard", "lantus", "novomix", "novorapid", "tresiba", "ryzodeg", "apidra",
        "insulatard", "actrapid", "wosulin", "maxulin", "gensulin", "scilin", "deglar"
    )

    private val COLD_LOCATION_KEYWORDS = setOf(
        "fridge", "refrigerator", "cold", "chiller", "2-8", "2°c", "২-৮", "ফ্রিজ"
    )

    /**
     * Determines whether a medicine requires continuous 2°C – 8°C cold-chain storage.
     */
    fun isColdChainMedicine(
        name: String?,
        genericName: String?,
        category: String?,
        shelfLocation: String? = null
    ): Boolean {
        val combined = "${name ?: ""} ${genericName ?: ""} ${category ?: ""}".lowercase()
        if (COLD_CHAIN_KEYWORDS.any { combined.contains(it) }) return true

        val loc = shelfLocation?.lowercase() ?: ""
        if (COLD_LOCATION_KEYWORDS.any { loc.contains(it) }) return true

        return false
    }

    /**
     * Evaluates a recorded refrigerator temperature against WHO/DGDA 2°C – 8°C standards.
     */
    fun evaluateTemperature(tempC: Double): TempBreachStatus {
        return when {
            tempC < MIN_SAFE_TEMP_C -> TempBreachStatus.FREEZE_RISK
            tempC > MAX_SAFE_TEMP_C -> TempBreachStatus.HEAT_BREACH
            else -> TempBreachStatus.NORMAL
        }
    }

    /**
     * Generates a thermal label or receipt cautionary advisory line for cold-chain medicines.
     */
    fun getColdChainThermalWarning(isBangla: Boolean): String {
        return if (isBangla) {
            "❄️ কোল্ড-চেইন ওষুধ: ফ্রিজে (২°-৮° সে.) সংরক্ষণ করুন। ডিপ ফ্রিজে রাখবেন না।"
        } else {
            "❄️ COLD CHAIN ITEM: Store in refrigerator (2°C - 8°C). Do NOT freeze."
        }
    }
}
