package com.example.techyfipos.data

import org.json.JSONArray
import org.json.JSONObject

data class CustomInfoBlock(
    val id: String,
    val title: String,
    val content: String,
    val enabled: Boolean = true,
    val position: Int = 1
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("content", content)
            put("enabled", enabled)
            put("position", position)
        }
    }

    companion object {
        fun fromJsonObject(json: JSONObject): CustomInfoBlock {
            return CustomInfoBlock(
                id = json.optString("id", System.currentTimeMillis().toString()),
                title = json.optString("title", ""),
                content = json.optString("content", ""),
                enabled = json.optBoolean("enabled", true),
                position = json.optInt("position", 1)
            )
        }
    }
}

data class InvoiceSettingsData(
    val storeName: String = "My Shop",
    val address: String = "",
    val phonePrimary: String = "",
    val phoneSecondary: String = "",
    val facebookUrl: String = "",
    val websiteUrl: String = "",
    val logoUri: String = "",
    val showLogo: Boolean = true,
    val showFacebookInHeader: Boolean = true,
    val showFacebookBelowQr: Boolean = true,
    val showQr: Boolean = true,
    val qrMode: String = "digital_invoice", // "digital_invoice", "store_info", "facebook_page", "custom_url", "embedded_json"
    val qrCustomUrl: String = "",
    val qrSize: String = "medium", // "small", "medium", "large"
    val qrCaption: String = "Scan QR for Digital Invoice / Info",
    val thankYouMessage: String = "Thank You For Shopping!",
    val secondaryFooter: String = "Please Visit Again",
    val poweredByText: String = "Powered by Techyfi POS",
    val cashierName: String = "Admin",
    val cashierPresets: List<String> = listOf("Admin"),
    // Toggles
    val showAddress: Boolean = true,
    val showPhone: Boolean = true,
    val showCashier: Boolean = true,
    val showCustomerDetails: Boolean = true,
    val showDiscount: Boolean = true,
    val showPaymentStatus: Boolean = true,
    val showSecondaryFooter: Boolean = true,
    val showPoweredBy: Boolean = true,
    val taxEnabled: Boolean = false,
    val taxRate: Double = 0.0,
    val taxLabel: String = "VAT",
    val taxInclusive: Boolean = false,
    val cashDrawerKickEnabled: Boolean = true,
    // MAC address of the paired Bluetooth (SPP) receipt printer the cash-drawer ESC/POS pulse is sent to.
    val cashDrawerPrinterAddress: String = "",
    val decimalQuantitiesEnabled: Boolean = true,
    val paperRollWidthMm: Int = 80,
    // Money Receipt & Due Repayment Voucher Settings
    val showDueReceiptLogo: Boolean = true,
    val showDueReceiptHeader: Boolean = true,
    val showDueReceiptSignatures: Boolean = true,
    val showDueReceiptBarcode: Boolean = true,
    val showDueReceiptQr: Boolean = false,
    val showDueReceiptCustomNotes: Boolean = false,
    val dueReceiptCustomTitle: String = "Notice & Terms",
    val dueReceiptCustomNotes: String = "Received with thanks. All credit adjustments are final. Keep this slip for record.",
    val customInfoBlocks: List<CustomInfoBlock> = listOf(
        CustomInfoBlock(
            id = "return-policy",
            title = "Return Policy",
            content = "Products may be exchanged within 7 days with the original receipt.",
            enabled = true,
            position = 1
        ),
        CustomInfoBlock(
            id = "opening-hours",
            title = "Opening Hours",
            content = "Saturday–Thursday: 8:00 AM–10:00 PM\nFriday: 3:00 PM–10:00 PM",
            enabled = true,
            position = 2
        )
    )
) {
    fun toJson(): String {
        val obj = JSONObject().apply {
            put("storeName", storeName)
            put("address", address)
            put("phonePrimary", phonePrimary)
            put("phoneSecondary", phoneSecondary)
            put("facebookUrl", facebookUrl)
            put("websiteUrl", websiteUrl)
            put("logoUri", logoUri)
            put("showLogo", showLogo)
            put("showFacebookInHeader", showFacebookInHeader)
            put("showFacebookBelowQr", showFacebookBelowQr)
            put("showQr", showQr)
            put("qrMode", qrMode)
            put("qrCustomUrl", qrCustomUrl)
            put("qrSize", qrSize)
            put("qrCaption", qrCaption)
            put("thankYouMessage", thankYouMessage)
            put("secondaryFooter", secondaryFooter)
            put("poweredByText", poweredByText)
            put("cashierName", cashierName)
            val presetsArr = JSONArray()
            cashierPresets.forEach { presetsArr.put(it) }
            put("cashierPresets", presetsArr)
            put("showAddress", showAddress)
            put("showPhone", showPhone)
            put("showCashier", showCashier)
            put("showCustomerDetails", showCustomerDetails)
            put("showDiscount", showDiscount)
            put("showPaymentStatus", showPaymentStatus)
            put("showSecondaryFooter", showSecondaryFooter)
            put("showPoweredBy", showPoweredBy)
            put("taxEnabled", taxEnabled)
            put("taxRate", taxRate)
            put("taxLabel", taxLabel)
            put("taxInclusive", taxInclusive)
            put("cashDrawerKickEnabled", cashDrawerKickEnabled)
            put("cashDrawerPrinterAddress", cashDrawerPrinterAddress)
            put("decimalQuantitiesEnabled", decimalQuantitiesEnabled)
            put("paperRollWidthMm", paperRollWidthMm)
            put("showDueReceiptLogo", showDueReceiptLogo)
            put("showDueReceiptHeader", showDueReceiptHeader)
            put("showDueReceiptSignatures", showDueReceiptSignatures)
            put("showDueReceiptBarcode", showDueReceiptBarcode)
            put("showDueReceiptQr", showDueReceiptQr)
            put("showDueReceiptCustomNotes", showDueReceiptCustomNotes)
            put("dueReceiptCustomTitle", dueReceiptCustomTitle)
            put("dueReceiptCustomNotes", dueReceiptCustomNotes)

            val blocksArr = JSONArray()
            customInfoBlocks.forEach { blocksArr.put(it.toJsonObject()) }
            put("customInfoBlocks", blocksArr)
        }
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): InvoiceSettingsData {
            if (jsonStr.isBlank()) return InvoiceSettingsData()
            return try {
                val json = JSONObject(jsonStr)
                val blocksList = mutableListOf<CustomInfoBlock>()
                val blocksArr = json.optJSONArray("customInfoBlocks")
                if (blocksArr != null) {
                    for (i in 0 until blocksArr.length()) {
                        val itemObj = blocksArr.optJSONObject(i)
                        if (itemObj != null) {
                            blocksList.add(CustomInfoBlock.fromJsonObject(itemObj))
                        }
                    }
                }

                val presetsList = mutableListOf<String>()
                val presetsArr = json.optJSONArray("cashierPresets")
                if (presetsArr != null) {
                    for (i in 0 until presetsArr.length()) {
                        val itemStr = presetsArr.optString(i, "").trim()
                        if (itemStr.isNotBlank() && !presetsList.contains(itemStr)) {
                            presetsList.add(itemStr)
                        }
                    }
                }

                val fallback = InvoiceSettingsData()
                InvoiceSettingsData(
                    storeName = json.optString("storeName", fallback.storeName),
                    address = json.optString("address", fallback.address),
                    phonePrimary = json.optString("phonePrimary", fallback.phonePrimary),
                    phoneSecondary = json.optString("phoneSecondary", fallback.phoneSecondary),
                    facebookUrl = json.optString("facebookUrl", fallback.facebookUrl),
                    websiteUrl = json.optString("websiteUrl", ""),
                    logoUri = json.optString("logoUri", ""),
                    showLogo = json.optBoolean("showLogo", true),
                    showFacebookInHeader = json.optBoolean("showFacebookInHeader", true),
                    showFacebookBelowQr = json.optBoolean("showFacebookBelowQr", true),
                    showQr = json.optBoolean("showQr", true),
                    qrMode = json.optString("qrMode", "digital_invoice"),
                    qrCustomUrl = json.optString("qrCustomUrl", ""),
                    qrSize = json.optString("qrSize", "medium"),
                    qrCaption = json.optString("qrCaption", "Scan QR for Digital Invoice / Info"),
                    thankYouMessage = json.optString("thankYouMessage", "Thank You For Shopping!"),
                    secondaryFooter = json.optString("secondaryFooter", "Please Visit Again"),
                    poweredByText = json.optString("poweredByText", "Powered by Techyfi POS"),
                    cashierName = json.optString("cashierName", "Admin"),
                    cashierPresets = if (presetsList.isNotEmpty()) presetsList else listOf("Admin", "Ahmed", "Fatema", "Karim", "Rahim"),
                    showAddress = json.optBoolean("showAddress", true),
                    showPhone = json.optBoolean("showPhone", true),
                    showCashier = json.optBoolean("showCashier", true),
                    showCustomerDetails = json.optBoolean("showCustomerDetails", true),
                    showDiscount = json.optBoolean("showDiscount", true),
                    showPaymentStatus = json.optBoolean("showPaymentStatus", true),
                    showSecondaryFooter = json.optBoolean("showSecondaryFooter", true),
                    showPoweredBy = json.optBoolean("showPoweredBy", true),
                    taxEnabled = json.optBoolean("taxEnabled", false),
                    taxRate = json.optDouble("taxRate", 0.0).takeUnless { it.isNaN() } ?: 0.0,
                    taxLabel = json.optString("taxLabel", "VAT").ifBlank { "VAT" },
                    taxInclusive = json.optBoolean("taxInclusive", false),
                    cashDrawerKickEnabled = json.optBoolean("cashDrawerKickEnabled", true),
                    cashDrawerPrinterAddress = json.optString("cashDrawerPrinterAddress", ""),
                    decimalQuantitiesEnabled = json.optBoolean("decimalQuantitiesEnabled", true),
                    paperRollWidthMm = json.optInt("paperRollWidthMm", 80),
                    showDueReceiptLogo = json.optBoolean("showDueReceiptLogo", true),
                    showDueReceiptHeader = json.optBoolean("showDueReceiptHeader", true),
                    showDueReceiptSignatures = json.optBoolean("showDueReceiptSignatures", true),
                    showDueReceiptBarcode = json.optBoolean("showDueReceiptBarcode", true),
                    showDueReceiptQr = json.optBoolean("showDueReceiptQr", false),
                    showDueReceiptCustomNotes = json.optBoolean("showDueReceiptCustomNotes", false),
                    dueReceiptCustomTitle = json.optString("dueReceiptCustomTitle", fallback.dueReceiptCustomTitle),
                    dueReceiptCustomNotes = json.optString("dueReceiptCustomNotes", fallback.dueReceiptCustomNotes),
                    customInfoBlocks = if (blocksList.isNotEmpty()) blocksList else InvoiceSettingsData().customInfoBlocks
                )
            } catch (e: Exception) {
                InvoiceSettingsData()
            }
        }
    }
}
