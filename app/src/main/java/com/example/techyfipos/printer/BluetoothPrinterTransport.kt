package com.example.techyfipos.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
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

private val bluetoothPrinterMutex = Mutex()

sealed class DirectPrintResult {
    object Success : DirectPrintResult()
    object NoAddress : DirectPrintResult()
    object NoPermission : DirectPrintResult()
    object BluetoothOff : DirectPrintResult()
    object DeviceNotPaired : DirectPrintResult()
    data class Failed(val message: String) : DirectPrintResult()
}

/**
 * Compares two printer Bluetooth MAC addresses.
 * Returns true if both trimmed addresses match ignoring case.
 * Returns false if either address is blank.
 */
fun isSamePrinter(a: String, b: String): Boolean {
    val trimmedA = a.trim()
    val trimmedB = b.trim()
    if (trimmedA.isBlank() || trimmedB.isBlank()) return false
    return trimmedA.equals(trimmedB, ignoreCase = true)
}

private const val TAG = "BtPrinterTransport"
private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
private const val CHUNK_SIZE = 512
private const val CHUNK_DELAY_MS = 15L
private const val CONNECT_SETTLE_MS = 250L
private const val POST_PRINT_DRAIN_MS = 300L

@SuppressLint("MissingPermission")
suspend fun sendToBluetoothPrinter(
    context: Context,
    address: String,
    segments: List<ByteArray>,
    delayBetweenSegmentsMs: Long = 0L,
    perSegmentDelaysMs: List<Long>? = null,
    onSegmentSent: ((index: Int) -> Unit)? = null
): DirectPrintResult = bluetoothPrinterMutex.withLock {
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
    try {
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
            Log.w(TAG, "Standard RFCOMM socket failed, retrying insecure RFCOMM socket: ${firstEx.message}")
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
                throw secEx
            }
        }

        // Allow printer firmware and buffer to settle after connection establishment
        delay(CONNECT_SETTLE_MS)

        val os: OutputStream = socket.outputStream
        for ((index, segment) in segments.withIndex()) {
            var offset = 0
            while (offset < segment.size) {
                val length = minOf(CHUNK_SIZE, segment.size - offset)
                os.write(segment, offset, length)
                offset += length
                if (offset < segment.size) {
                    delay(CHUNK_DELAY_MS)
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
        delay(POST_PRINT_DRAIN_MS)
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
