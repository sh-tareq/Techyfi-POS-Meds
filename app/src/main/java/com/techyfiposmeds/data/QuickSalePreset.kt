package com.techyfiposmeds.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class QuickSalePreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val defaultPrice: Double,
    val defaultUnit: String = "Pcs"
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("defaultPrice", defaultPrice)
        put("defaultUnit", defaultUnit)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): QuickSalePreset {
            return QuickSalePreset(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Custom Item"),
                defaultPrice = json.optDouble("defaultPrice", 0.0),
                defaultUnit = json.optString("defaultUnit", "Pcs")
            )
        }

        fun defaultPresets(): List<QuickSalePreset> = listOf(
            QuickSalePreset(id = "preset_free_sample", name = "🎁 ফ্রি ডক্টর স্যাম্পল", defaultPrice = 0.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_med_bag", name = "🛍️ মেডিসিন ক্যারিয়ার ব্যাগ", defaultPrice = 5.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_syringe", name = "💉 ডিসপোজেবল সিরিঞ্জ (৫ সিসি)", defaultPrice = 10.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_bandage", name = "🩹 ফার্স্ট এইড ব্যান্ডেজ", defaultPrice = 5.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_bp_check", name = "🩺 বিপি / প্রেশার চেক ফি", defaultPrice = 20.0, defaultUnit = "Service"),
            QuickSalePreset(id = "preset_injection_push", name = "💉 ইনজেকশন পুশ / ড্রেসিং", defaultPrice = 50.0, defaultUnit = "Service"),
            QuickSalePreset(id = "preset_delivery", name = "🚚 হোম ডেলিভারি চার্জ", defaultPrice = 40.0, defaultUnit = "Service")
        )

        fun listToJson(list: List<QuickSalePreset>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String): List<QuickSalePreset> {
            if (jsonStr.isBlank()) return defaultPresets()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<QuickSalePreset>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        list.add(fromJsonObject(obj))
                    }
                }
                if (list.isEmpty()) defaultPresets() else list
            } catch (e: Exception) {
                defaultPresets()
            }
        }
    }
}
