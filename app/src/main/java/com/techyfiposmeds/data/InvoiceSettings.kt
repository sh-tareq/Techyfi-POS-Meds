package com.techyfiposmeds.data

import com.techyfiposmeds.printer.PrinterTarget
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
    val cashDrawerPrinterConnection: String = "BLUETOOTH", // "BLUETOOTH", "USB"
    val cashDrawerUsbDeviceKey: String = "",
    // MAC address of the paired Bluetooth (SPP) receipt printer the cash-drawer ESC/POS pulse is sent to.
    val cashDrawerPrinterAddress: String = "",
    val directBluetoothPrintEnabled: Boolean = false,
    val receiptPrinterConnection: String = "BLUETOOTH", // "BLUETOOTH", "USB"
    val receiptUsbDeviceKey: String = "",
    val directBluetoothPrinterAddress: String = "",
    val receiptPrintMode: String = "AUTO", // "BLUETOOTH", "SYSTEM", "AUTO"
    val receiptDarkness: Int = 8,
    val receiptCutMode: String = "PARTIAL", // "FULL", "PARTIAL", "NONE"
    val receiptFeedLinesBeforeCut: Int = 4,
    val receiptCutCommandStyle: String = "GS_V", // "GS_V", "LEGACY_ESC", "GS_V_FEED"
    val labelPrinterConnection: String = "BLUETOOTH", // "BLUETOOTH", "USB"
    val labelUsbDeviceKey: String = "",
    val labelPrintMode: String = "AUTO", // "BLUETOOTH", "SYSTEM", "AUTO"
    val labelPrinterAddress: String = "",
    val labelGapMm: Float = 2f,
    val labelDarkness: Int = 8,
    val labelDpi: Int = 203,
    val labelPrintSpeed: Float = 4.0f,
    val labelXOffsetDots: Int = 0,
    val labelYOffsetDots: Int = 0,
    val labelDirection: Int = 1,
    val labelPaperType: String = "GAP",
    val labelLabelsAcross: Int = 1,
    val labelColumnGapMm: Float = 2.0f,
    val allowNegativeInventory: Boolean = false,
    val decimalQuantitiesEnabled: Boolean = true,
    val paperRollWidthMm: Int = 80,
    val showMrpDiscountInSaleMode: Boolean = true,
    val showItemWiseMrpDiscount: Boolean = true,
    // Money Receipt & Due Repayment Voucher Settings
    val showDueReceiptLogo: Boolean = true,
    val showDueReceiptHeader: Boolean = true,
    val showDueReceiptSignatures: Boolean = true,
    val showDueReceiptBarcode: Boolean = true,
    val showDueReceiptQr: Boolean = false,
    val showDueReceiptVerificationQr: Boolean = true,
    val showDueReceiptCustomNotes: Boolean = false,
    val dueReceiptCustomTitle: String = "Notice & Terms",
    val dueReceiptCustomNotes: String = "Received with thanks. All credit adjustments are final. Keep this slip for record.",
    val idleLockDurationMinutes: Int = 15,
    val drugLicenseNumber: String = "",
    val pharmacistRegNumber: String = "",
    val showDrugLicense: Boolean = true,
    val enforceAntibioticDoctor: Boolean = true,
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
            content = "Saturday-Thursday: 8:00 AM-10:00 PM\nFriday: 3:00 PM-10:00 PM",
            enabled = true,
            position = 2
        )
    )
) {
    fun resolvedReceiptPrinterTarget(): PrinterTarget {
        val conn = receiptPrinterConnection.trim().uppercase()
        if (conn == "USB" && receiptUsbDeviceKey.isNotBlank()) {
            return PrinterTarget.Usb(receiptUsbDeviceKey.trim())
        }
        val direct = directBluetoothPrinterAddress.trim()
        if (direct.isNotBlank()) {
            return if (direct.startsWith("usb:", ignoreCase = true)) {
                PrinterTarget.Usb(direct)
            } else {
                PrinterTarget.Bluetooth(direct)
            }
        }
        return PrinterTarget.None
    }

    fun resolvedLabelPrinterTarget(): PrinterTarget {
        val conn = labelPrinterConnection.trim().uppercase()
        if (conn == "USB" && labelUsbDeviceKey.isNotBlank()) {
            return PrinterTarget.Usb(labelUsbDeviceKey.trim())
        }
        val addr = labelPrinterAddress.trim()
        if (addr.isNotBlank()) {
            return if (addr.startsWith("usb:", ignoreCase = true)) {
                PrinterTarget.Usb(addr)
            } else {
                PrinterTarget.Bluetooth(addr)
            }
        }
        return PrinterTarget.None
    }

    fun resolvedCashDrawerPrinterTarget(): PrinterTarget {
        val conn = cashDrawerPrinterConnection.trim().uppercase()
        if (conn == "USB" && cashDrawerUsbDeviceKey.isNotBlank()) {
            return PrinterTarget.Usb(cashDrawerUsbDeviceKey.trim())
        }
        val addr = cashDrawerPrinterAddress.trim()
        if (addr.isNotBlank()) {
            return if (addr.startsWith("usb:", ignoreCase = true)) {
                PrinterTarget.Usb(addr)
            } else {
                PrinterTarget.Bluetooth(addr)
            }
        }
        return resolvedReceiptPrinterTarget()
    }

    fun resolvedReceiptPrinterAddress(): String {
        return when (val target = resolvedReceiptPrinterTarget()) {
            is PrinterTarget.Bluetooth -> target.macAddress
            is PrinterTarget.Usb -> target.deviceKey
            is PrinterTarget.None -> ""
        }
    }

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
            put("cashDrawerPrinterConnection", cashDrawerPrinterConnection)
            put("cashDrawerUsbDeviceKey", cashDrawerUsbDeviceKey)
            put("cashDrawerPrinterAddress", cashDrawerPrinterAddress)
            put("directBluetoothPrintEnabled", directBluetoothPrintEnabled)
            put("receiptPrinterConnection", receiptPrinterConnection)
            put("receiptUsbDeviceKey", receiptUsbDeviceKey)
            put("directBluetoothPrinterAddress", directBluetoothPrinterAddress)
            put("receiptPrintMode", receiptPrintMode)
            put("receiptDarkness", receiptDarkness)
            put("receiptCutMode", receiptCutMode)
            put("receiptFeedLinesBeforeCut", receiptFeedLinesBeforeCut)
            put("receiptCutCommandStyle", receiptCutCommandStyle)
            put("labelPrinterConnection", labelPrinterConnection)
            put("labelUsbDeviceKey", labelUsbDeviceKey)
            put("labelPrintMode", labelPrintMode)
            put("labelPrinterAddress", labelPrinterAddress)
            put("labelGapMm", labelGapMm.toDouble())
            put("labelDarkness", labelDarkness)
            put("labelDpi", labelDpi)
            put("labelPrintSpeed", labelPrintSpeed.toDouble())
            put("labelXOffsetDots", labelXOffsetDots)
            put("labelYOffsetDots", labelYOffsetDots)
            put("labelDirection", labelDirection)
            put("labelPaperType", labelPaperType)
            put("labelLabelsAcross", labelLabelsAcross)
            put("labelColumnGapMm", labelColumnGapMm.toDouble())
            put("allowNegativeInventory", allowNegativeInventory)
            put("decimalQuantitiesEnabled", decimalQuantitiesEnabled)
            put("paperRollWidthMm", paperRollWidthMm)
            put("showMrpDiscountInSaleMode", showMrpDiscountInSaleMode)
            put("showItemWiseMrpDiscount", showItemWiseMrpDiscount)
            put("showDueReceiptLogo", showDueReceiptLogo)
            put("showDueReceiptHeader", showDueReceiptHeader)
            put("showDueReceiptSignatures", showDueReceiptSignatures)
            put("showDueReceiptBarcode", showDueReceiptBarcode)
            put("showDueReceiptQr", showDueReceiptQr)
            put("showDueReceiptVerificationQr", showDueReceiptVerificationQr)
            put("showDueReceiptCustomNotes", showDueReceiptCustomNotes)
            put("dueReceiptCustomTitle", dueReceiptCustomTitle)
            put("dueReceiptCustomNotes", dueReceiptCustomNotes)
            put("idleLockDurationMinutes", idleLockDurationMinutes)
            put("drugLicenseNumber", drugLicenseNumber)
            put("pharmacistRegNumber", pharmacistRegNumber)
            put("showDrugLicense", showDrugLicense)
            put("enforceAntibioticDoctor", enforceAntibioticDoctor)

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

                val directBtEnabled = json.optBoolean("directBluetoothPrintEnabled", false)
                val resolvedReceiptPrintMode = if ((!json.has("receiptPrintMode") || json.optString("receiptPrintMode").isBlank()) && directBtEnabled) {
                    "BLUETOOTH"
                } else {
                    json.optString("receiptPrintMode", "AUTO").ifBlank { "AUTO" }
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
                    cashDrawerPrinterConnection = json.optString("cashDrawerPrinterConnection", "BLUETOOTH").trim().uppercase().let {
                        if (it in listOf("BLUETOOTH", "USB")) it else "BLUETOOTH"
                    },
                    cashDrawerUsbDeviceKey = json.optString("cashDrawerUsbDeviceKey", "").trim(),
                    cashDrawerPrinterAddress = json.optString("cashDrawerPrinterAddress", ""),
                    directBluetoothPrintEnabled = directBtEnabled || resolvedReceiptPrintMode == "BLUETOOTH",
                    receiptPrinterConnection = json.optString("receiptPrinterConnection", "BLUETOOTH").trim().uppercase().let {
                        if (it in listOf("BLUETOOTH", "USB")) it else "BLUETOOTH"
                    },
                    receiptUsbDeviceKey = json.optString("receiptUsbDeviceKey", "").trim(),
                    directBluetoothPrinterAddress = json.optString("directBluetoothPrinterAddress", ""),
                    receiptPrintMode = resolvedReceiptPrintMode,
                    receiptDarkness = json.optInt("receiptDarkness", 8),
                    receiptCutMode = json.optString("receiptCutMode", "PARTIAL").trim().uppercase().let {
                        if (it in listOf("FULL", "PARTIAL", "NONE")) it else "PARTIAL"
                    },
                    receiptFeedLinesBeforeCut = json.optInt("receiptFeedLinesBeforeCut", 4).coerceIn(0, 10),
                    receiptCutCommandStyle = json.optString("receiptCutCommandStyle", "GS_V").trim().uppercase().let {
                        if (it in listOf("GS_V", "LEGACY_ESC", "GS_V_FEED")) it else "GS_V"
                    },
                    labelPrinterConnection = json.optString("labelPrinterConnection", "BLUETOOTH").trim().uppercase().let {
                        if (it in listOf("BLUETOOTH", "USB")) it else "BLUETOOTH"
                    },
                    labelUsbDeviceKey = json.optString("labelUsbDeviceKey", "").trim(),
                    labelPrintMode = json.optString("labelPrintMode", "AUTO").ifBlank { "AUTO" },
                    labelPrinterAddress = json.optString("labelPrinterAddress", ""),
                    labelGapMm = json.optDouble("labelGapMm", 2.0).toFloat(),
                    labelDarkness = json.optInt("labelDarkness", 8),
                    labelDpi = json.optInt("labelDpi", 203).let { if (it == 300) 300 else 203 },
                    labelPrintSpeed = json.optDouble("labelPrintSpeed", 4.0).toFloat().coerceIn(1.5f, 6.0f),
                    labelXOffsetDots = json.optInt("labelXOffsetDots", 0),
                    labelYOffsetDots = json.optInt("labelYOffsetDots", 0),
                    labelDirection = json.optInt("labelDirection", 1).let { if (it == 0) 0 else 1 },
                    labelPaperType = json.optString("labelPaperType", "GAP").trim().uppercase().let {
                        if (it in listOf("GAP", "CONTINUOUS", "BLACK_MARK")) it else "GAP"
                    },
                    labelLabelsAcross = json.optInt("labelLabelsAcross", 1).coerceIn(1, 3),
                    labelColumnGapMm = json.optDouble("labelColumnGapMm", 2.0).toFloat().coerceIn(0f, 10f),
                    allowNegativeInventory = json.optBoolean("allowNegativeInventory", false),
                    decimalQuantitiesEnabled = json.optBoolean("decimalQuantitiesEnabled", true),
                    paperRollWidthMm = json.optInt("paperRollWidthMm", 80),
                    showMrpDiscountInSaleMode = json.optBoolean("showMrpDiscountInSaleMode", true),
                    showItemWiseMrpDiscount = json.optBoolean("showItemWiseMrpDiscount", true),
                    showDueReceiptLogo = json.optBoolean("showDueReceiptLogo", true),
                    showDueReceiptHeader = json.optBoolean("showDueReceiptHeader", true),
                    showDueReceiptSignatures = json.optBoolean("showDueReceiptSignatures", true),
                    showDueReceiptBarcode = json.optBoolean("showDueReceiptBarcode", true),
                    showDueReceiptQr = json.optBoolean("showDueReceiptQr", false),
                    showDueReceiptVerificationQr = json.optBoolean("showDueReceiptVerificationQr", true),
                    showDueReceiptCustomNotes = json.optBoolean("showDueReceiptCustomNotes", false),
                    dueReceiptCustomTitle = json.optString("dueReceiptCustomTitle", fallback.dueReceiptCustomTitle),
                    dueReceiptCustomNotes = json.optString("dueReceiptCustomNotes", fallback.dueReceiptCustomNotes),
                    idleLockDurationMinutes = run {
                        val raw = json.optInt("idleLockDurationMinutes", fallback.idleLockDurationMinutes)
                        val options = listOf(0, 5, 15, 30, 60)
                        if (raw in options) raw
                        else if (raw > 0) {
                            val nonZeroOptions = listOf(5, 15, 30, 60)
                            nonZeroOptions.minByOrNull { kotlin.math.abs(it - raw) } ?: 5
                        } else {
                            0
                        }
                    },
                    drugLicenseNumber = json.optString("drugLicenseNumber", ""),
                    pharmacistRegNumber = json.optString("pharmacistRegNumber", ""),
                    showDrugLicense = json.optBoolean("showDrugLicense", true),
                    enforceAntibioticDoctor = json.optBoolean("enforceAntibioticDoctor", true),
                    customInfoBlocks = if (blocksList.isNotEmpty()) blocksList else InvoiceSettingsData().customInfoBlocks
                )
            } catch (e: Exception) {
                InvoiceSettingsData()
            }
        }
    }
}
