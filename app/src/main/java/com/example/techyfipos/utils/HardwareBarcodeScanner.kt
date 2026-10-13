package com.example.techyfipos.utils

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object HardwareBarcodeScanner {
    private val _barcodeEvents = MutableSharedFlow<String>(extraBufferCapacity = 20)
    val barcodeEvents: SharedFlow<String> = _barcodeEvents.asSharedFlow()

    private val buffer = StringBuilder()
    private var lastKeyTime: Long = 0L

    // Some hardware/USB/Bluetooth scanners can be configured with no Enter/Tab/CR/LF suffix at
    // all. Without this, a scan from such a scanner would sit in `buffer` forever waiting for
    // a terminator key that never arrives. This mirrors the "emit after 500ms idle" behavior.
    private const val IDLE_EMIT_DELAY_MS = 500L
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
        val englishCode = toEnglishDigits(code.trim())
        if (englishCode.isBlank()) return
        val now = System.currentTimeMillis()
        if (englishCode == lastEmittedCode && now - lastEmittedTime < DUPLICATE_EMIT_WINDOW_MS) {
            return
        }
        lastEmittedCode = englishCode
        lastEmittedTime = now
        com.example.techyfipos.ui.viewmodel.AuthViewModel.recordUserActivity()
        _barcodeEvents.tryEmit(englishCode)
    }

    /**
     * Extracts pure English ASCII characters from hardware scanner key events.
     * Guaranteed to return English ASCII digits ('0'-'9') even when:
     * - Android system language or app language is set to Bangla
     * - A Bengali software or hardware keyboard layout is active
     * - Numpad keycodes are sent by USB/Bluetooth scanner guns
     * Also supports alphanumeric barcodes (Code-128, Code-39, QR) preserving ASCII letters and symbols.
     */
    fun extractAsciiBarcodeChar(
        keyCode: Int,
        unicodeChar: Int = 0,
        isShiftPressed: Boolean = false,
        isCapsLockOn: Boolean = false
    ): Char? {
        // 1. Hardware number row keys (0-9) - maps directly to ASCII '0'..'9' regardless of locale
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            return '0' + (keyCode - KeyEvent.KEYCODE_0)
        }

        // 2. Hardware numpad keys (0-9)
        if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            return '0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)
        }

        // 3. Bengali digits (০..৯, U+09E6..U+09EF) emitted if an IME or keymap transliterates keys
        if (unicodeChar in 0x09E6..0x09EF) {
            return '0' + (unicodeChar - 0x09E6)
        }

        // 4. Arabic-Indic digits (٠..٩, U+0660..U+0669)
        if (unicodeChar in 0x0660..0x0669) {
            return '0' + (unicodeChar - 0x0660)
        }

        // 5. Eastern Arabic-Indic / Persian digits (۰..۹, U+06F0..U+06F9)
        if (unicodeChar in 0x06F0..0x06F9) {
            return '0' + (unicodeChar - 0x06F0)
        }

        // 6. Alphabetic keys (A-Z, a-z)
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val isUpper = isShiftPressed xor isCapsLockOn
            val base = if (isUpper) 'A' else 'a'
            return base + (keyCode - KeyEvent.KEYCODE_A)
        }

        // 7. Standard barcode symbols and punctuation
        when (keyCode) {
            KeyEvent.KEYCODE_MINUS -> return '-'
            KeyEvent.KEYCODE_PERIOD -> return '.'
            KeyEvent.KEYCODE_SLASH -> return '/'
            KeyEvent.KEYCODE_SPACE -> return ' '
            KeyEvent.KEYCODE_STAR -> return '*'
            KeyEvent.KEYCODE_PLUS -> return '+'
            KeyEvent.KEYCODE_EQUALS -> return '='
            KeyEvent.KEYCODE_COMMA -> return ','
            KeyEvent.KEYCODE_AT -> return '@'
            KeyEvent.KEYCODE_POUND -> return '#'
            KeyEvent.KEYCODE_SEMICOLON -> return ';'
            KeyEvent.KEYCODE_APOSTROPHE -> return '\''
        }

        // 8. Fallback to standard printable ASCII characters
        if (unicodeChar in 32..126) {
            return unicodeChar.toChar()
        }

        return null
    }

    fun extractAsciiBarcodeChar(event: KeyEvent): Char? = extractAsciiBarcodeChar(
        keyCode = event.keyCode,
        unicodeChar = event.unicodeChar,
        isShiftPressed = event.isShiftPressed,
        isCapsLockOn = event.isCapsLockOn
    )

    /**
     * Detects if the key event represents a barcode scan terminator.
     * Includes standard Enter, Numpad Enter, and Tab (which many physical scanners emit as suffix).
     */
    private var lastDownWasConsumed = false

    fun isTerminatorKey(keyCode: Int, unicodeChar: Int = 0): Boolean {
        return keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_TAB ||
                unicodeChar == '\n'.code ||
                unicodeChar == '\r'.code ||
                unicodeChar == '\t'.code
    }

    fun isTerminatorKey(event: KeyEvent): Boolean =
        isTerminatorKey(event.keyCode, event.unicodeChar)

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            val wasConsumed = lastDownWasConsumed || isTerminatorKey(event)
            lastDownWasConsumed = false
            return wasConsumed
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val currentTime = System.currentTimeMillis()
        val interval = currentTime - lastKeyTime
        lastKeyTime = currentTime

        // If pause between keypresses > 800ms, reset buffer unless it is a terminator
        if (interval > 800L && buffer.isNotEmpty()) {
            buffer.clear()
        }

        // Terminator key signals completed hardware barcode scan
        if (isTerminatorKey(event)) {
            mainHandler.removeCallbacks(idleEmitRunnable)
            if (buffer.isNotEmpty()) {
                val scannedCode = buffer.toString().trim()
                buffer.clear()
                if (scannedCode.isNotBlank()) {
                    emit(scannedCode)
                    lastDownWasConsumed = true
                    return true
                }
            }
            lastDownWasConsumed = false
            return false
        }

        // Extract printable ASCII character
        val char = extractAsciiBarcodeChar(event)
        if (char != null) {
            buffer.append(char)
            mainHandler.removeCallbacks(idleEmitRunnable)
            mainHandler.postDelayed(idleEmitRunnable, IDLE_EMIT_DELAY_MS)
            lastDownWasConsumed = true
            return true
        }

        lastDownWasConsumed = false
        return false
    }

    fun emitScannedBarcode(barcode: String) {
        val trimmed = toEnglishDigits(barcode.trim())
        if (trimmed.isNotBlank()) {
            emit(trimmed)
        }
    }

    fun clearPendingInput() {
        buffer.clear()
        lastKeyTime = 0L
        mainHandler.removeCallbacks(idleEmitRunnable)
        burstBuffer.clear()
        burstCharCount = 0
        lastBurstTime = 0L
        lastDownWasConsumed = false
        mainHandler.removeCallbacks(burstIdleEmitRunnable)
    }

    private const val SCANNER_BURST_THRESHOLD_MS = 38L
    private var burstCharCount = 0
    private val burstBuffer = StringBuilder()
    private var lastBurstTime = 0L

    private val burstIdleEmitRunnable = Runnable {
        if (burstBuffer.isNotEmpty() && burstCharCount >= 3) {
            val scanned = burstBuffer.toString().trim()
            burstBuffer.clear()
            burstCharCount = 0
            if (scanned.isNotBlank()) {
                emit(scanned)
            }
        } else {
            burstBuffer.clear()
            burstCharCount = 0
        }
    }

    /**
     * Intercepts rapid hardware barcode scanner keystrokes (< 38ms delta) even when an editable
     * text field currently has focus. This prevents long barcodes from being typed into customer
     * search, discount, or phone input fields, while preserving normal human typing speed (> 60ms).
     *
     * When a rapid burst is confirmed on key 2, a synthetic backspace is dispatched to [focusedView]
     * to cleanly erase the leaked 1st character that was passed before the burst was recognized.
     */
    fun dispatchKeyEventWithEditorCheck(
        event: KeyEvent,
        isEditorFocused: Boolean,
        focusedView: View? = null
    ): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            val wasConsumed = lastDownWasConsumed || isTerminatorKey(event)
            lastDownWasConsumed = false
            return wasConsumed
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val now = System.currentTimeMillis()
        val delta = now - lastBurstTime
        lastBurstTime = now

        val isTerminator = isTerminatorKey(event)

        if (!isEditorFocused) {
            burstBuffer.clear()
            burstCharCount = 0
            mainHandler.removeCallbacks(burstIdleEmitRunnable)
            return dispatchKeyEvent(event)
        }

        // Editor is focused:
        if (delta > 120L) {
            burstBuffer.clear()
            burstCharCount = 0
            mainHandler.removeCallbacks(burstIdleEmitRunnable)
        }

        if (isTerminator) {
            mainHandler.removeCallbacks(burstIdleEmitRunnable)
            if (burstCharCount >= 3 && burstBuffer.isNotEmpty()) {
                val scanned = burstBuffer.toString().trim()
                burstBuffer.clear()
                burstCharCount = 0
                if (scanned.isNotBlank()) {
                    emit(scanned)
                    lastDownWasConsumed = true
                    return true // Intercept terminator so editor doesn't submit or advance focus
                }
            }
            burstBuffer.clear()
            burstCharCount = 0
            lastDownWasConsumed = false
            return false
        }

        val char = extractAsciiBarcodeChar(event)
        if (char != null) {
            if (delta <= SCANNER_BURST_THRESHOLD_MS || burstCharCount == 0) {
                burstBuffer.append(char)
                burstCharCount++

                mainHandler.removeCallbacks(burstIdleEmitRunnable)
                mainHandler.postDelayed(burstIdleEmitRunnable, IDLE_EMIT_DELAY_MS)

                if (burstCharCount == 2 && delta <= SCANNER_BURST_THRESHOLD_MS) {
                    // Confirmed hardware scanner burst!
                    // Erase the 1st character that was typed into the editor on key 1
                    try {
                        focusedView?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                        focusedView?.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    lastDownWasConsumed = true
                    return true
                } else if (burstCharCount > 2) {
                    lastDownWasConsumed = true
                    return true
                }
            } else {
                // Human typing pace (> 38ms)
                burstBuffer.clear()
                burstCharCount = 0
                mainHandler.removeCallbacks(burstIdleEmitRunnable)
                lastDownWasConsumed = false
                return false
            }
        }

        lastDownWasConsumed = false
        return false
    }
}
