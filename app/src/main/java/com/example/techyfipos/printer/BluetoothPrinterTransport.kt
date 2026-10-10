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
import java.io.OutputStream
import java.util.UUID

sealed class DirectPrintResult {
    object Success : DirectPrintResult()
    object NoAddress : DirectPrintResult()
    object NoPermission : DirectPrintResult()
    object BluetoothOff : DirectPrintResult()
    object DeviceNotPaired : DirectPrintResult()
    data class Failed(val message: String) : DirectPrintResult()
}

private const val TAG = "BtPrinterTransport"
private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
private const val CHUNK_SIZE = 512
private const val CHUNK_DELAY_MS = 15L
private const val POST_PRINT_DRAIN_MS = 300L

@SuppressLint("MissingPermission")
suspend fun sendToBluetoothPrinter(
    context: Context,
    address: String,
    payload: ByteArray
): DirectPrintResult = withContext(Dispatchers.IO) {
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
            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            insecureSocket.connect()
            insecureSocket
        }

        val os: OutputStream = socket.outputStream
        var offset = 0
        while (offset < payload.size) {
            val length = minOf(CHUNK_SIZE, payload.size - offset)
            os.write(payload, offset, length)
            offset += length
            if (offset < payload.size) {
                delay(CHUNK_DELAY_MS)
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

object BluetoothPrinterTransport {
    suspend fun sendToBluetoothPrinter(
        context: Context,
        address: String,
        payload: ByteArray
    ): DirectPrintResult = com.example.techyfipos.printer.sendToBluetoothPrinter(context, address, payload)
}
