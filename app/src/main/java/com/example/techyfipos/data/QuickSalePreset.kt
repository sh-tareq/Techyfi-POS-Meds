package com.example.techyfipos.data

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
            QuickSalePreset(id = "preset_free_bag", name = "🎁 Free Gift Bag", defaultPrice = 0.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_free_sample", name = "🎉 Free Promotion Sample", defaultPrice = 0.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_bag", name = "🛍️ Shopping Bag", defaultPrice = 10.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_delivery", name = "🚚 Delivery Charge", defaultPrice = 60.0, defaultUnit = "Service"),
            QuickSalePreset(id = "preset_repair", name = "🔧 Device Servicing", defaultPrice = 250.0, defaultUnit = "Service"),
            QuickSalePreset(id = "preset_gift", name = "🎁 Gift Wrap Box", defaultPrice = 40.0, defaultUnit = "Pcs"),
            QuickSalePreset(id = "preset_loose", name = "🌾 খোলা পণ্য", defaultPrice = 85.0, defaultUnit = "Kg")
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
