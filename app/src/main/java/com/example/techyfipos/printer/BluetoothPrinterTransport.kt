package com.example.techyfipos.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.techyfipos.R
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStream
import java.util.UUID

sealed class DirectPrintResult {
    object Success : DirectPrintResult()
    object NoAddress : DirectPrintResult()
    object NoPermission : DirectPrintResult()
    object BluetoothOff : DirectPrintResult()
    object DeviceNotPaired : DirectPrintResult()
    object UsbDeviceNotFound : DirectPrintResult()
    object UsbNoPermission : DirectPrintResult()
    object UsbNoPrinterInterface : DirectPrintResult()
    object UsbNotSupported : DirectPrintResult()
    data class Failed(val message: String) : DirectPrintResult()
}

/**
 * Common abstraction for sending byte segments over a printer transport (Bluetooth or USB).
 */
interface PrinterTransport {
    suspend fun send(
        context: Context,
        segments: List<ByteArray>,
        perSegmentDelaysMs: List<Long>?,
        onSegmentSent: ((Int) -> Unit)?
    ): DirectPrintResult
}

/**
 * Compares two printer identifiers (Bluetooth MAC addresses or USB device keys).
 * Returns true if both trimmed addresses/keys match ignoring case.
 * For USB device keys with serial numbers, matches vendorId, productId, and serial.
 * Returns false if either identifier is blank, or if one is Bluetooth and the other is USB.
 */
fun isSamePrinter(a: String, b: String): Boolean {
    val trimmedA = a.trim()
    val trimmedB = b.trim()
    if (trimmedA.isBlank() || trimmedB.isBlank()) return false

    val isUsbA = trimmedA.startsWith("usb:", ignoreCase = true)
    val isUsbB = trimmedB.startsWith("usb:", ignoreCase = true)

    if (isUsbA && isUsbB) {
        val idA = parseUsbDeviceKey(trimmedA)
        val idB = parseUsbDeviceKey(trimmedB)
        if (idA != null && idB != null) {
            if (idA.vendorId != idB.vendorId || idA.productId != idB.productId) return false
            return if (idA.serialNumber.isNotBlank() && idB.serialNumber.isNotBlank()) {
                idA.serialNumber.equals(idB.serialNumber, ignoreCase = true)
            } else {
                true
            }
        }
        return trimmedA.equals(trimmedB, ignoreCase = true)
    }

    if (isUsbA || isUsbB) {
        return false
    }

    return trimmedA.equals(trimmedB, ignoreCase = true)
}

/**
 * Pure function that formats a Bluetooth device name and address.
 * Returns "name (address)" if name is present and distinct from address, or trimmed address alone.
 */
fun formatBluetoothDeviceDisplay(name: String?, address: String): String {
    val trimmed = address.trim()
    if (trimmed.isBlank()) return ""
    val trimmedName = name?.trim().orEmpty()
    return if (trimmedName.isNotBlank() && !trimmedName.equals(trimmed, ignoreCase = true)) {
        "$trimmedName ($trimmed)"
    } else {
        trimmed
    }
}

/**
 * Resolves the bonded Bluetooth device name from [address].
 * Returns "name (address)" if name is known, otherwise the trimmed address.
 */
fun getBluetoothDeviceDisplayName(context: Context, address: String): String {
    val trimmed = address.trim()
    if (trimmed.isBlank()) return ""
    return try {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val device = adapter?.bondedDevices?.firstOrNull { it.address.equals(trimmed, ignoreCase = true) }
        formatBluetoothDeviceDisplay(device?.name, trimmed)
    } catch (_: SecurityException) {
        trimmed
    } catch (_: Exception) {
        trimmed
    }
}

/**
 * Formats a user-friendly display string for a [PrinterTarget], including
 * the '(Not connected)' hint if a USB printer is not currently plugged in.
 */
fun formatPrinterTargetDisplay(context: Context, target: PrinterTarget): String {
    return when (target) {
        is PrinterTarget.None -> ""
        is PrinterTarget.Bluetooth -> getBluetoothDeviceDisplayName(context, target.address)
        is PrinterTarget.Usb -> {
            val name = getUsbDeviceDisplayName(context, target.deviceKey)
            if (!isUsbDeviceAttached(context, target.deviceKey)) {
                "$name (${context.getString(R.string.settings_usb_not_connected_hint)})"
            } else {
                name
            }
        }
    }
}

private const val TAG = "BtPrinterTransport"
private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
private const val CHUNK_SIZE = 512
private const val CHUNK_DELAY_MS = 15L
private const val CHUNK_DELAY_MIN_MS = 15L
private const val CHUNK_DELAY_MAX_MS = 35L
private const val CHUNK_DELAY_THRESHOLD_BYTES = 8192
private const val CONNECT_SETTLE_MS = 250L
private const val POST_PRINT_DRAIN_MS = 300L
private const val POST_PRINT_DRAIN_MAX_MS = 4000L
private const val STATUS_TIMEOUT_MIN_MS = 3000L
private const val STATUS_TIMEOUT_MAX_MS = 8000L
private const val DRAIN_BYTES_PER_MS = 25

/**
 * Dynamically paces Bluetooth RFCOMM chunk transmission to prevent printer input buffer overruns.
 * Short jobs (<= 8 KB, such as cut test banners) transmit fast with a 15 ms chunk delay (~34 KB/s).
 * Larger jobs (full receipts with tens of KB of raster data) scale chunk delay up to 35 ms (~14.6 KB/s),
 * matching thermal printing speed and printer microcontroller UART reception limits so the job tail is not dropped.
 */
fun bluetoothChunkDelayMs(totalBytes: Int): Long {
    val bytes = totalBytes.coerceAtLeast(0)
    return if (bytes <= CHUNK_DELAY_THRESHOLD_BYTES) {
        CHUNK_DELAY_MIN_MS
    } else {
        (CHUNK_DELAY_MIN_MS + (bytes - CHUNK_DELAY_THRESHOLD_BYTES) / 3000L).coerceIn(CHUNK_DELAY_MIN_MS, CHUNK_DELAY_MAX_MS)
    }
}

/**
 * How long to keep the Bluetooth socket open after the last write when no status reply is received.
 * Closing the RFCOMM socket drops bytes still queued in the phone's Bluetooth stack. A long receipt
 * leaves tens of KB queued, so a fixed short wait loses the tail of the job (the feed and cut command).
 * Scales with the payload size, between 300 ms and 4 s.
 */
fun bluetoothPostPrintDrainMs(totalBytes: Int): Long =
    (POST_PRINT_DRAIN_MS + totalBytes.coerceAtLeast(0) / DRAIN_BYTES_PER_MS).coerceIn(POST_PRINT_DRAIN_MS, POST_PRINT_DRAIN_MAX_MS)

/**
 * Timeout for awaiting a response to a buffered ESC/POS status query (GS r 1).
 * Scales with payload size from 3 s to 8 s, ensuring that long jobs have ample time
 * for the printer to finish burning raster data and cutting before answering.
 */
fun bluetoothResponseTimeoutMs(totalBytes: Int): Long =
    (STATUS_TIMEOUT_MIN_MS + totalBytes.coerceAtLeast(0) / DRAIN_BYTES_PER_MS).coerceIn(STATUS_TIMEOUT_MIN_MS, STATUS_TIMEOUT_MAX_MS)

@SuppressLint("MissingPermission")
suspend fun sendToBluetoothPrinter(
    context: Context,
    address: String,
    segments: List<ByteArray>,
    delayBetweenSegmentsMs: Long = 0L,
    perSegmentDelaysMs: List<Long>? = null,
    onSegmentSent: ((index: Int) -> Unit)? = null
): DirectPrintResult = printerHardwareMutex.withLock {
    withContext(Dispatchers.IO) {
    val trimmedAddress = address.trim()
    if (trimmedAddress.isBlank()) {
        Log.w(TAG, "No printer address configured")
        return@withContext DirectPrintResult.NoAddress
    }

    val hasBtPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    if (!hasBtPermission) {
        Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
        return@withContext DirectPrintResult.NoPermission
    }

    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter
    if (adapter == null || !adapter.isEnabled) {
        Log.w(TAG, "Bluetooth adapter is off or unavailable")
        return@withContext DirectPrintResult.BluetoothOff
    }

    val device = try {
        adapter.bondedDevices?.firstOrNull { it.address.equals(trimmedAddress, ignoreCase = true) }
    } catch (e: SecurityException) {
        Log.e(TAG, "SecurityException listing bonded devices", e)
        return@withContext DirectPrintResult.NoPermission
    }

    if (device == null) {
        Log.w(TAG, "Device $trimmedAddress not found in bonded devices")
        return@withContext DirectPrintResult.DeviceNotPaired
    }

    try {
        adapter.cancelDiscovery()
    } catch (e: Exception) {
        Log.w(TAG, "Error cancelling discovery: ${e.message}")
    }

    val uuid = UUID.fromString(SPP_UUID)
    var socket: BluetoothSocket? = null
    var lastConnectEx: Exception? = null

    try {
        for (attempt in 1..2) {
            if (attempt > 1) {
                delay(500L)
                try { adapter.cancelDiscovery() } catch (_: Exception) {}
            }
            var secureSocket: BluetoothSocket? = null
            socket = try {
                val s = device.createRfcommSocketToServiceRecord(uuid)
                secureSocket = s
                s.connect()
                s
            } catch (firstEx: Exception) {
                try {
                    secureSocket?.close()
                } catch (closeEx: Exception) {
                    Log.w(TAG, "Error closing secure socket before fallback: ${closeEx.message}")
                }
                Log.w(TAG, "Standard RFCOMM socket failed (attempt $attempt), retrying insecure RFCOMM socket: ${firstEx.message}")
                var inSec: BluetoothSocket? = null
                try {
                    val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                    inSec = insecureSocket
                    insecureSocket.connect()
                    insecureSocket
                } catch (secEx: Exception) {
                    try {
                        inSec?.close()
                    } catch (closeEx: Exception) {
                        Log.w(TAG, "Error closing insecure socket after failure: ${closeEx.message}")
                    }
                    lastConnectEx = secEx
                    null
                }
            }
            if (socket != null) break
        }

        if (socket == null) {
            throw (lastConnectEx ?: java.io.IOException("Bluetooth connection failed"))
        }

        // Allow printer firmware and buffer to settle after connection establishment
        delay(CONNECT_SETTLE_MS)

        val os: OutputStream = socket.outputStream
        val totalBytes = segments.sumOf { it.size }
        val chunkDelayMs = bluetoothChunkDelayMs(totalBytes)
        val writeStartTime = System.currentTimeMillis()
        for ((index, segment) in segments.withIndex()) {
            var offset = 0
            while (offset < segment.size) {
                val length = minOf(CHUNK_SIZE, segment.size - offset)
                os.write(segment, offset, length)
                offset += length
                if (offset < segment.size) {
                    delay(chunkDelayMs)
                }
            }
            onSegmentSent?.invoke(index)
            val delayMs = perSegmentDelaysMs?.getOrNull(index) ?: delayBetweenSegmentsMs
            if (delayMs > 0L) {
                os.flush()
                delay(delayMs)
            }
        }
        os.flush()
        val writeElapsedMs = System.currentTimeMillis() - writeStartTime

        // Query status deterministically with GS r 1 (transmit paper sensor status).
        // Since ESC/POS commands are executed sequentially, receiving a response byte
        // proves that all preceding commands (raster bands, feed lines, and cut) were consumed.
        var replyReceived = false
        val timeoutMs = bluetoothResponseTimeoutMs(totalBytes)
        try {
            val inputStream = socket.inputStream
            while (inputStream.available() > 0) {
                inputStream.read()
            }
            os.write(byteArrayOf(0x1D, 0x72, 0x01))
            os.flush()

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (inputStream.available() > 0) {
                    val b = inputStream.read()
                    if (b != -1) {
                        replyReceived = true
                        break
                    }
                }
                delay(50L)
            }
        } catch (queryEx: Exception) {
            Log.w(TAG, "Status query check skipped or errored: ${queryEx.message}")
        }

        val drainMs = if (replyReceived) {
            // Reply received: printer finished job and cutter. Brief settle for blade retraction.
            150L
        } else {
            // Fall back to payload-scaled drain for printers lacking bidirectional status support.
            bluetoothPostPrintDrainMs(totalBytes)
        }
        delay(drainMs)
        Log.d(
            TAG,
            "sendToBluetoothPrinter: totalBytes=$totalBytes, chunkDelayMs=$chunkDelayMs, writeElapsedMs=$writeElapsedMs, drainMs=$drainMs, replyReceived=$replyReceived"
        )
        DirectPrintResult.Success
    } catch (e: Exception) {
        Log.e(TAG, "Bluetooth communication failed: ${e.message}", e)
        DirectPrintResult.Failed(e.localizedMessage ?: e.message ?: "Bluetooth communication error")
    } finally {
        try {
            socket?.close()
        } catch (closeEx: Exception) {
            Log.w(TAG, "Error closing socket: ${closeEx.message}")
        }
    }
}
}

suspend fun sendToBluetoothPrinter(
    context: Context,
    address: String,
    payload: ByteArray
): DirectPrintResult = sendToBluetoothPrinter(context, address, listOf(payload), 0L)

suspend fun sendToBluetoothPrinter(
    context: Context,
    address: String,
    segments: List<ByteArray>,
    perSegmentDelaysMs: List<Long>
): DirectPrintResult = sendToBluetoothPrinter(
    context = context,
    address = address,
    segments = segments,
    delayBetweenSegmentsMs = 0L,
    perSegmentDelaysMs = perSegmentDelaysMs
)

object BluetoothPrinterTransport {
    suspend fun sendToBluetoothPrinter(
        context: Context,
        address: String,
        payload: ByteArray
    ): DirectPrintResult = com.example.techyfipos.printer.sendToBluetoothPrinter(context, address, payload)

    suspend fun sendToBluetoothPrinter(
        context: Context,
        address: String,
        segments: List<ByteArray>,
        delayBetweenSegmentsMs: Long
    ): DirectPrintResult = com.example.techyfipos.printer.sendToBluetoothPrinter(
        context = context,
        address = address,
        segments = segments,
        delayBetweenSegmentsMs = delayBetweenSegmentsMs
    )

    suspend fun sendToBluetoothPrinter(
        context: Context,
        address: String,
        segments: List<ByteArray>,
        perSegmentDelaysMs: List<Long>
    ): DirectPrintResult = com.example.techyfipos.printer.sendToBluetoothPrinter(
        context = context,
        address = address,
        segments = segments,
        perSegmentDelaysMs = perSegmentDelaysMs
    )
}

/**
 * Implementation of PrinterTransport for Bluetooth SPP printers.
 */
class BluetoothPrinterTransportImpl(private val macAddress: String) : PrinterTransport {
    override suspend fun send(
        context: Context,
        segments: List<ByteArray>,
        perSegmentDelaysMs: List<Long>?,
        onSegmentSent: ((Int) -> Unit)?
    ): DirectPrintResult = sendToBluetoothPrinter(
        context = context,
        address = macAddress,
        segments = segments,
        perSegmentDelaysMs = perSegmentDelaysMs,
        onSegmentSent = onSegmentSent
    )
}

/**
 * Unified dispatch function sending segments to the configured PrinterTarget (Bluetooth or USB).
 */
suspend fun sendToPrinter(
    context: Context,
    target: PrinterTarget,
    segments: List<ByteArray>,
    delayBetweenSegmentsMs: Long = 0L,
    perSegmentDelaysMs: List<Long>? = null,
    onSegmentSent: ((index: Int) -> Unit)? = null
): DirectPrintResult {
    return when (target) {
        is PrinterTarget.None -> DirectPrintResult.NoAddress
        is PrinterTarget.Bluetooth -> sendToBluetoothPrinter(
            context = context,
            address = target.macAddress,
            segments = segments,
            delayBetweenSegmentsMs = delayBetweenSegmentsMs,
            perSegmentDelaysMs = perSegmentDelaysMs,
            onSegmentSent = onSegmentSent
        )
        is PrinterTarget.Usb -> sendToUsbPrinter(
            context = context,
            deviceKey = target.deviceKey,
            segments = segments,
            delayBetweenSegmentsMs = delayBetweenSegmentsMs,
            perSegmentDelaysMs = perSegmentDelaysMs,
            onSegmentSent = onSegmentSent
        )
    }
}

/**
 * Unified dispatch function sending segments to a printer identified by MAC address or USB device key string.
 */
suspend fun sendToPrinter(
    context: Context,
    addressOrKey: String,
    segments: List<ByteArray>,
    delayBetweenSegmentsMs: Long = 0L,
    perSegmentDelaysMs: List<Long>? = null,
    onSegmentSent: ((index: Int) -> Unit)? = null
): DirectPrintResult {
    val trimmed = addressOrKey.trim()
    if (trimmed.isBlank()) return DirectPrintResult.NoAddress
    val target = if (trimmed.startsWith("usb:", ignoreCase = true)) {
        PrinterTarget.Usb(trimmed)
    } else {
        PrinterTarget.Bluetooth(trimmed)
    }
    return sendToPrinter(
        context = context,
        target = target,
        segments = segments,
        delayBetweenSegmentsMs = delayBetweenSegmentsMs,
        perSegmentDelaysMs = perSegmentDelaysMs,
        onSegmentSent = onSegmentSent
    )
}
