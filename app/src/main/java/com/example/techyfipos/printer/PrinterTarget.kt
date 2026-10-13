package com.example.techyfipos.printer

/**
 * Represents the target destination for a direct thermal print job (ESC/POS or TSPL).
 */
sealed class PrinterTarget {
    object None : PrinterTarget()
    data class Bluetooth(val macAddress: String) : PrinterTarget() {
        val address: String get() = macAddress
    }
    data class Usb(val deviceKey: String) : PrinterTarget()

    val isConfigured: Boolean
        get() = when (this) {
            is None -> false
            is Bluetooth -> macAddress.isNotBlank()
            is Usb -> deviceKey.isNotBlank()
        }

    val identifier: String
        get() = when (this) {
            is None -> ""
            is Bluetooth -> macAddress.trim()
            is Usb -> deviceKey.trim()
        }

    companion object {
        fun fromConnectionAndAddress(connection: String?, addressOrKey: String?): PrinterTarget {
            val trimmed = addressOrKey?.trim().orEmpty()
            if (trimmed.isBlank()) return None
            val conn = connection?.trim()?.uppercase().orEmpty()
            return when {
                conn == "USB" || trimmed.startsWith("usb:", ignoreCase = true) -> Usb(trimmed)
                else -> Bluetooth(trimmed)
            }
        }
    }
}

/**
 * Parsed representation of a USB printer identifier.
 * Stored as a device key (e.g. "usb:1234:5678" or "usb:1234:5678:SN12345").
 */
data class UsbDeviceIdentifier(
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String = ""
) {
    fun toKey(): String = if (serialNumber.isNotBlank()) {
        "usb:$vendorId:$productId:${serialNumber.trim()}"
    } else {
        "usb:$vendorId:$productId"
    }

    companion object {
        fun parse(key: String?): UsbDeviceIdentifier? {
            val trimmed = key?.trim().orEmpty()
            if (!trimmed.startsWith("usb:", ignoreCase = true)) return null
            val parts = trimmed.split(":")
            if (parts.size < 3) return null
            val vid = parts[1].toIntOrNull() ?: return null
            val pid = parts[2].toIntOrNull() ?: return null
            val serial = if (parts.size >= 4) parts.subList(3, parts.size).joinToString(":") else ""
            return UsbDeviceIdentifier(vid, pid, serial)
        }
    }
}

fun buildUsbDeviceKey(vendorId: Int, productId: Int, serialNumber: String? = null): String {
    val cleanSerial = serialNumber?.trim().orEmpty()
    return UsbDeviceIdentifier(vendorId, productId, cleanSerial).toKey()
}

fun buildUsbDeviceKey(device: android.hardware.usb.UsbDevice): String {
    val serial = try { device.serialNumber } catch (_: SecurityException) { null } catch (_: Exception) { null }
    return buildUsbDeviceKey(device.vendorId, device.productId, serial)
}

fun parseUsbDeviceKey(key: String?): UsbDeviceIdentifier? = UsbDeviceIdentifier.parse(key)

/**
 * Pure data model representing a USB endpoint for testable endpoint selection.
 */
data class UsbEndpointInfo(
    val endpointNumber: Int,
    val direction: Int, // 0 = OUT (UsbConstants.USB_DIR_OUT), 128 = IN (UsbConstants.USB_DIR_IN)
    val type: Int, // 2 = BULK (UsbConstants.USB_ENDPOINT_XFER_BULK)
    val maxPacketSize: Int
)

/**
 * Pure data model representing a USB interface for testable interface selection.
 */
data class UsbInterfaceInfo(
    val id: Int,
    val interfaceClass: Int, // 7 = PRINTER (UsbConstants.USB_CLASS_PRINTER)
    val interfaceSubclass: Int = 0,
    val interfaceProtocol: Int = 0,
    val endpoints: List<UsbEndpointInfo> = emptyList()
)

/**
 * Pure function to select the best interface and bulk OUT endpoint for thermal printing.
 * Prefers class 7 (printer) with a bulk OUT endpoint, falling back to any interface with a bulk OUT endpoint.
 */
fun selectPrinterInterfaceAndEndpoint(
    interfaces: List<UsbInterfaceInfo>
): Pair<UsbInterfaceInfo, UsbEndpointInfo>? {
    // 1. Try interfaces with USB_CLASS_PRINTER (7) having a bulk OUT endpoint
    for (iface in interfaces) {
        if (iface.interfaceClass == 7) {
            val bulkOut = iface.endpoints.firstOrNull {
                it.direction == 0 && it.type == 2
            }
            if (bulkOut != null) {
                return Pair(iface, bulkOut)
            }
        }
    }

    // 2. Fall back to any interface having a bulk OUT endpoint (some printers report vendor-specific class 255)
    for (iface in interfaces) {
        val bulkOut = iface.endpoints.firstOrNull {
            it.direction == 0 && it.type == 2
        }
        if (bulkOut != null) {
            return Pair(iface, bulkOut)
        }
    }

    return null
}

/**
 * Compares two printer targets to prevent assigning the same hardware to both receipt and label roles.
 */
fun isSamePrinterTarget(a: PrinterTarget, b: PrinterTarget): Boolean {
    if (!a.isConfigured || !b.isConfigured) return false
    return when {
        a is PrinterTarget.Bluetooth && b is PrinterTarget.Bluetooth ->
            isSamePrinter(a.macAddress, b.macAddress)
        a is PrinterTarget.Usb && b is PrinterTarget.Usb ->
            isSamePrinter(a.deviceKey, b.deviceKey)
        else -> false
    }
}
