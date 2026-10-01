package com.example.techyfipos.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CategoryItem(
    val id: String = UUID.randomUUID().toString(),
    val icon: String = "🏷️",
    val nameEn: String = "",
    val nameBn: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("icon", icon)
            put("nameEn", nameEn)
            put("nameBn", nameBn)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CategoryItem {
            return CategoryItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                icon = json.optString("icon", "🏷️"),
                nameEn = json.optString("nameEn", ""),
                nameBn = json.optString("nameBn", "")
            )
        }

        fun listToJson(list: List<CategoryItem>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(jsonStr: String): List<CategoryItem> {
            if (jsonStr.isBlank()) return defaultCategories()
            return try {
                val array = JSONArray(jsonStr)
                val result = mutableListOf<CategoryItem>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null) {
                        result.add(fromJson(obj))
                    }
                }
                if (result.isEmpty()) defaultCategories() else result
            } catch (e: Exception) {
                defaultCategories()
            }
        }

        fun defaultCategories(): List<CategoryItem> = listOf(
            CategoryItem(id = "1", icon = "🛒", nameEn = "Grocery", nameBn = "মুদি"),
            CategoryItem(id = "2", icon = "📦", nameEn = "General", nameBn = "সাধারণ"),
            CategoryItem(id = "3", icon = "💻", nameEn = "Electronics", nameBn = "ইলেকট্রনিক্স"),
            CategoryItem(id = "4", icon = "👕", nameEn = "Clothing", nameBn = "পোশাক"),
            CategoryItem(id = "5", icon = "🥤", nameEn = "Beverage", nameBn = "পানীয়"),
            CategoryItem(id = "6", icon = "🥐", nameEn = "Bakery", nameBn = "বেকারি"),
            CategoryItem(id = "7", icon = "💊", nameEn = "Pharmacy", nameBn = "ফার্মেসি"),
            CategoryItem(id = "8", icon = "💄", nameEn = "Cosmetics", nameBn = "প্রসাধনী"),
            CategoryItem(id = "9", icon = "✏️", nameEn = "Stationery", nameBn = "স্টেশনারি")
        )
    }
}
