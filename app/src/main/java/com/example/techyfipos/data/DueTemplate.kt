package com.example.techyfipos.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DueTemplate(
    val id: String = UUID.randomUUID().toString(),
    val nameBn: String,
    val nameEn: String,
    val textBn: String,
    val textEn: String,
    val isDefault: Boolean = false
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("id", id)
        put("nameBn", nameBn)
        put("nameEn", nameEn)
        put("textBn", textBn)
        put("textEn", textEn)
        put("isDefault", isDefault)
    }

    companion object {
        fun fromJsonObject(json: JSONObject): DueTemplate {
            return DueTemplate(
                id = json.optString("id", UUID.randomUUID().toString()),
                nameBn = json.optString("nameBn", "🌿 নম্র"),
                nameEn = json.optString("nameEn", "🌿 Polite"),
                textBn = json.optString("textBn", ""),
                textEn = json.optString("textEn", ""),
                isDefault = json.optBoolean("isDefault", false)
            )
        }

        fun defaultTemplates(): List<DueTemplate> = listOf(
            DueTemplate(
                id = "template_polite",
                nameBn = "🌿 নম্র",
                nameEn = "🌿 Polite",
                textBn = "আসসালামু আলাইকুম {customer}, {store}-এ আপনার বকেয়া ব্যালেন্স {due}। অনুগ্রহ করে সময়মতো পরিশোধ করার অনুরোধ রইল। bKash/Nagad: {bkash}। ধন্যবাদ!",
                textEn = "Dear {customer}, friendly reminder that your outstanding balance at {store} is {due}. Kindly settle at your earliest convenience. bKash/Nagad: {bkash}. Thank you!",
                isDefault = true
            ),
            DueTemplate(
                id = "template_urgent",
                nameBn = "⚡ জরুরি",
                nameEn = "⚡ Urgent",
                textBn = "জরুরি তাগাদা: {customer}, {store}-এ আপনার বকেয়া {due} অনতিবিলম্বে পরিশোধের জন্য বিনীত অনুরোধ করা হচ্ছে। bKash/Nagad: {bkash}।",
                textEn = "Urgent Notice: {customer}, your pending balance of {due} at {store} is overdue. Please settle immediately via bKash/Nagad: {bkash}.",
                isDefault = true
            ),
            DueTemplate(
                id = "template_discount",
                nameBn = "🏷️ বিশেষ অফার",
                nameEn = "🏷️ Discount Offer",
                textBn = "বিশেষ সুযোগ: {customer}, {store}-এ আপনার বকেয়া {due} আগামী ৩ দিনের মধ্যে পরিশোধ করলে উপভোগ করুন বিশেষ সুবিধা! bKash/Nagad: {bkash}।",
                textEn = "Special Offer: {customer}, settle your outstanding due of {due} at {store} within 3 days to enjoy special customer benefits! bKash/Nagad: {bkash}.",
                isDefault = false
            ),
            DueTemplate(
                id = "template_invoice",
                nameBn = "📋 ইনভয়েস সারসংক্ষেপ",
                nameEn = "📋 Invoice Summary",
                textBn = "বকেয়া হিসাব: গ্রাহক {customer}, মোট বকেয়া {due}। প্রতিষ্ঠান: {store}। bKash/Nagad: {bkash}।",
                textEn = "Due Summary: Customer {customer}, Total Due: {due}. Store: {store}. bKash/Nagad: {bkash}.",
                isDefault = false
            )
        )

        fun listToJson(list: List<DueTemplate>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String): List<DueTemplate> {
            if (jsonStr.isBlank()) return defaultTemplates()
            return try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<DueTemplate>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        list.add(fromJsonObject(obj))
                    }
                }
                if (list.isEmpty()) defaultTemplates() else list
            } catch (e: Exception) {
                defaultTemplates()
            }
        }
    }
}
