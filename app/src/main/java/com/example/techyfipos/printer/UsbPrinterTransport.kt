package com.example.techyfipos.printer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Shared hardware mutex ensuring that only one print or drawer job (Bluetooth or USB)
 * talks to printer hardware at any time.
 */
val printerHardwareMutex = Mutex()

private const val TAG = "UsbPrinterTransport"
private const val ACTION_USB_PERMISSION = "com.example.techyfipos.USB_PERMISSION"
private const val USB_TRANSFER_TIMEOUT_MS = 5000
private const val POST_PRINT_DRAIN_MS = 150L

/**
 * Finds a connected USB device matching [deviceKey] (e.g. "usb:1234:5678" or "usb:1234:5678:SN").
 */
fun findUsbDeviceByKey(usbManager: UsbManager, deviceKey: String): UsbDevice? {
    val id = parseUsbDeviceKey(deviceKey) ?: return null
    val devices = usbManager.deviceList.values

    // Try exact match with serial number if provided
    if (id.serialNumber.isNotBlank()) {
        val exact = devices.firstOrNull { dev ->
            dev.vendorId == id.vendorId &&
                dev.productId == id.productId &&
                try { dev.serialNumber?.equals(id.serialNumber, ignoreCase = true) == true } catch (_: SecurityException) { false }
        }
        if (exact != null) return exact
    }

    // Fall back to vendorId and productId
    return devices.firstOrNull { dev ->
        dev.vendorId == id.vendorId && dev.productId == id.productId
    }
}

/**
 * Discovers a suitable printer interface and bulk OUT endpoint for thermal printing.
 */
fun findPrinterInterfaceAndEndpoint(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
    val interfaceInfos = mutableListOf<UsbInterfaceInfo>()
    for (i in 0 until device.interfaceCount) {
        val iface = device.getInterface(i)
        val epInfos = mutableListOf<UsbEndpointInfo>()
        for (j in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(j)
            epInfos.add(
                UsbEndpointInfo(
                    endpointNumber = ep.endpointNumber,
                    direction = ep.direction,
                    type = ep.type,
                    maxPacketSize = ep.maxPacketSize
                )
            )
        }
        interfaceInfos.add(
            UsbInterfaceInfo(
                id = iface.id,
                interfaceClass = iface.interfaceClass,
                interfaceSubclass = iface.interfaceSubclass,
                interfaceProtocol = iface.interfaceProtocol,
                endpoints = epInfos
            )
        )
    }

    val selected = selectPrinterInterfaceAndEndpoint(interfaceInfos) ?: return null
    // UsbDevice.getInterface() takes an index, not the interface number, so match by id and a bulk OUT endpoint.
    for (i in 0 until device.interfaceCount) {
        val iface = device.getInterface(i)
        if (iface.id != selected.first.id) continue
        val ep = (0 until iface.endpointCount).map { iface.getEndpoint(it) }.firstOrNull {
            it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }
        if (ep != null) return Pair(iface, ep)
    }
    return null
}

/**
 * Checks if a USB device is likely a thermal receipt or label printer.
 */
fun isLikelyUsbPrinter(device: UsbDevice): Boolean {
    for (i in 0 until device.interfaceCount) {
        val iface = device.getInterface(i)
        if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
    }
    if (device.deviceClass == UsbConstants.USB_CLASS_PRINTER) return true

    // Check for bulk OUT endpoint as fallback for vendor-specific class 255
    for (i in 0 until device.interfaceCount) {
        val iface = device.getInterface(i)
        for (j in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(j)
            if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                return true
            }
        }
    }
    return false
}

/**
 * Formats a user-friendly USB device name for lists and cards.
 */
fun formatUsbDeviceDisplay(
    productName: String?,
    manufacturerName: String?,
    vendorId: Int,
    productId: Int
): String {
    val prod = productName?.trim().orEmpty()
    val mfg = manufacturerName?.trim().orEmpty()
    val vidPidHex = String.format(Locale.US, "%04X:%04X", vendorId, productId)
    return when {
        prod.isNotBlank() && mfg.isNotBlank() && !prod.contains(mfg, ignoreCase = true) ->
            "$mfg $prod (USB $vidPidHex)"
        prod.isNotBlank() ->
            "$prod (USB $vidPidHex)"
        mfg.isNotBlank() ->
            "$mfg (USB $vidPidHex)"
        else ->
            "USB Printer ($vidPidHex)"
    }
}

/**
 * Resolves the display name for a configured USB device key.
 */
fun getUsbDeviceDisplayName(context: Context, deviceKey: String): String {
    val id = parseUsbDeviceKey(deviceKey) ?: return deviceKey
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
    val device = if (usbManager != null) findUsbDeviceByKey(usbManager, deviceKey) else null

    return if (device != null) {
        val prodName = try { device.productName } catch (_: Exception) { null }
        val mfgName = try { device.manufacturerName } catch (_: Exception) { null }
        formatUsbDeviceDisplay(prodName, mfgName, device.vendorId, device.productId)
    } else {
        val vidPidHex = String.format(Locale.US, "%04X:%04X", id.vendorId, id.productId)
        "USB Printer ($vidPidHex)"
    }
}

/**
 * Checks whether the configured USB printer is currently plugged into the device.
 */
fun isUsbDeviceAttached(context: Context, deviceKey: String): Boolean {
    if (deviceKey.isBlank()) return false
    val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
    return findUsbDeviceByKey(usbManager, deviceKey) != null
}

/**
 * Requests USB device permission asynchronously using coroutine suspension.
 */
suspend fun requestUsbPermission(context: Context, usbManager: UsbManager, device: UsbDevice): Boolean {
    if (usbManager.hasPermission(device)) return true

    return suspendCancellableCoroutine { continuation ->
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    val receivedDevice: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (receivedDevice != null && receivedDevice.deviceId == device.deviceId) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        try {
                            recvContext.unregisterReceiver(this)
                        } catch (_: Exception) {}
                        if (continuation.isActive) {
                            continuation.resume(granted)
                        }
                    }
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        }

        continuation.invokeOnCancellation {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }

        usbManager.requestPermission(device, permissionIntent)
    }
}

/**
 * Sends ESC/POS or TSPL data segments directly to a USB printer via bulk transfer.
 */
suspend fun sendToUsbPrinter(
    context: Context,
    deviceKey: String,
    segments: List<ByteArray>,
    delayBetweenSegmentsMs: Long = 0L,
    perSegmentDelaysMs: List<Long>? = null,
    onSegmentSent: ((index: Int) -> Unit)? = null,
    timing: PrintTiming? = null
): DirectPrintResult {
    val mutexReqMs = System.currentTimeMillis()
    return printerHardwareMutex.withLock {
        timing?.recordMutexAcquired(System.currentTimeMillis() - mutexReqMs)
        withContext(Dispatchers.IO) {
            if (deviceKey.isBlank()) {
                return@withContext DirectPrintResult.NoAddress
            }

            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return@withContext DirectPrintResult.UsbNotSupported

            val device = findUsbDeviceByKey(usbManager, deviceKey)
                ?: return@withContext DirectPrintResult.UsbDeviceNotFound

            if (!usbManager.hasPermission(device)) {
                val granted = requestUsbPermission(context, usbManager, device)
                if (!granted) {
                    return@withContext DirectPrintResult.UsbNoPermission
                }
            }

            val pair = findPrinterInterfaceAndEndpoint(device)
                ?: return@withContext DirectPrintResult.UsbNoPrinterInterface

            val iface = pair.first
            val endpoint = pair.second

            val connection = usbManager.openDevice(device)
                ?: return@withContext DirectPrintResult.Failed("Could not open USB printer connection")

            val claimed = connection.claimInterface(iface, true)
            if (!claimed) {
                connection.close()
                return@withContext DirectPrintResult.Failed("Could not claim USB printer interface")
            }

            timing?.recordConnected("USB", 1, false)
            timing?.recordSettleDone()

            val maxPacketSize = endpoint.maxPacketSize.coerceAtLeast(64)
            val chunkSize = ((4096 / maxPacketSize) * maxPacketSize).coerceAtLeast(maxPacketSize)

            try {
                for ((index, segment) in segments.withIndex()) {
                    var offset = 0
                    while (offset < segment.size) {
                        if (index == 0 && offset == 0) {
                            timing?.recordFirstChunkWritten()
                        }
                        val length = minOf(chunkSize, segment.size - offset)
                        val transferred = connection.bulkTransfer(endpoint, segment, offset, length, USB_TRANSFER_TIMEOUT_MS)
                        if (transferred <= 0) {
                            return@withContext DirectPrintResult.Failed("USB transfer failed (code $transferred)")
                        }
                        offset += transferred
                    }
                    onSegmentSent?.invoke(index)
                    val delayMs = perSegmentDelaysMs?.getOrNull(index) ?: delayBetweenSegmentsMs
                    if (delayMs > 0L) {
                        delay(delayMs)
                    }
                }
                timing?.recordLastChunkWritten()
                delay(POST_PRINT_DRAIN_MS)
                timing?.recordDrainDone(false)
                DirectPrintResult.Success
            } catch (e: Exception) {
                Log.e(TAG, "USB communication failed: ${e.message}", e)
                DirectPrintResult.Failed(e.localizedMessage ?: e.message ?: "USB communication error")
            } finally {
                try {
                    connection.releaseInterface(iface)
                } catch (_: Exception) {}
                try {
                    connection.close()
                } catch (_: Exception) {}
                timing?.recordSocketClosed()
            }
        }
    }
}

/**
 * Implementation of PrinterTransport for USB printers.
 */
class UsbPrinterTransport(private val deviceKey: String) : PrinterTransport {
    override suspend fun send(
        context: Context,
        segments: List<ByteArray>,
        perSegmentDelaysMs: List<Long>?,
        onSegmentSent: ((Int) -> Unit)?
    ): DirectPrintResult = sendToUsbPrinter(
        context = context,
        deviceKey = deviceKey,
        segments = segments,
        perSegmentDelaysMs = perSegmentDelaysMs,
        onSegmentSent = onSegmentSent
    )
}
