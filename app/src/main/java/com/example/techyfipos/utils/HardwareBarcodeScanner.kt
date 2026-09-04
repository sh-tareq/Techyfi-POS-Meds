package com.example.techyfipos.utils

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object HardwareBarcodeScanner {
    private val _barcodeEvents = MutableSharedFlow<String>(extraBufferCapacity = 20)
    val barcodeEvents: SharedFlow<String> = _barcodeEvents.asSharedFlow()

    private val buffer = StringBuilder()
    private var lastKeyTime: Long = 0L

    // Some hardware/USB/Bluetooth scanners can be configured with no Enter/CR/LF suffix at
    // all. Without this, a scan from such a scanner would sit in `buffer` forever waiting for
    // a terminator key that never arrives. This mirrors the "emit after 300ms idle" behavior
    // the class always intended to have (see the comment this replaces below).
    private const val IDLE_EMIT_DELAY_MS = 300L
    private const val MIN_CHARS_FOR_IDLE_EMIT = 3
    private val mainHandler = Handler(Looper.getMainLooper())
    private val idleEmitRunnable = Runnable {
        if (buffer.isNotEmpty()) {
            val scannedCode = buffer.toString().trim()
            buffer.clear()
            if (scannedCode.length >= MIN_CHARS_FOR_IDLE_EMIT) {
                emit(scannedCode)
            }
        }
    }

    // Guards every emission point (hardware trigger, manual entry) against the same
    // code firing twice in immediate succession (e.g. scanner contact bounce), while
    // still allowing a deliberate, repeated scan of the same product after this window.
    // Screens that consume `barcodeEvents` directly (Cart, Products) rely on this to be
    // deduped, since they don't run ScannerViewModel's own camera/hardware cross-check.
    private const val DUPLICATE_EMIT_WINDOW_MS = 450L
    private var lastEmittedCode: String? = null
    private var lastEmittedTime: Long = 0L

    private fun emit(code: String) {
        val now = System.currentTimeMillis()
        if (code == lastEmittedCode && now - lastEmittedTime < DUPLICATE_EMIT_WINDOW_MS) {
            return
        }
        lastEmittedCode = code
        lastEmittedTime = now
        _barcodeEvents.tryEmit(code)
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()
        val interval = currentTime - lastKeyTime
        lastKeyTime = currentTime

        val keyCode = event.keyCode
        val unicodeChar = event.unicodeChar

        // If pause between keypresses > 600ms, reset buffer unless it is the enter key
        if (interval > 600L && buffer.isNotEmpty()) {
            buffer.clear()
        }

        // Enter key signals completed hardware barcode scan
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || unicodeChar == '\n'.code || unicodeChar == '\r'.code) {
            mainHandler.removeCallbacks(idleEmitRunnable)
            if (buffer.isNotEmpty()) {
                val scannedCode = buffer.toString().trim()
                buffer.clear()
                if (scannedCode.isNotBlank()) {
                    emit(scannedCode)
                    return true
                }
            }
            return false
        }

        // Printable ASCII characters (digits, letters, symbols)
        if (unicodeChar > 31 && unicodeChar < 127) {
            buffer.append(unicodeChar.toChar())
            // Scanners configured without an Enter/CR/LF suffix never fire the branch above,
            // so treat a pause in keystrokes as the end of the scan too.
            mainHandler.removeCallbacks(idleEmitRunnable)
            mainHandler.postDelayed(idleEmitRunnable, IDLE_EMIT_DELAY_MS)
            return true
        }

        return false
    }

    fun emitScannedBarcode(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isNotBlank()) {
            emit(trimmed)
        }
    }

    fun clearPendingInput() {
        buffer.clear()
        lastKeyTime = 0L
        mainHandler.removeCallbacks(idleEmitRunnable)
    }
}
