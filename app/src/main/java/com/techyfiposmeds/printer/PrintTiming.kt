package com.techyfiposmeds.printer

import android.util.Log

/**
 * End-to-end latency tracer for receipt and label print jobs.
 * Tracks timestamp deltas across each pipeline stage from initial button tap
 * to final socket close, and logs both a compact single-line summary and a detailed
 * metadata report under the tag "PrintTiming".
 *
 * Style constraint: strictly no em dashes in any log or summary string.
 */
class PrintTiming(
    val startMs: Long = System.currentTimeMillis()
) {
    var buttonTapMs: Long = startMs
    var generateDoneMs: Long = 0L
    var decodeDoneMs: Long = 0L
    var rasterDoneMs: Long = 0L
    var mutexWaitMs: Long = 0L
    var mutexAcquiredMs: Long = 0L
    var connectDoneMs: Long = 0L
    var settleDoneMs: Long = 0L
    var firstChunkWrittenMs: Long = 0L
    var lastChunkWrittenMs: Long = 0L
    var drainDoneMs: Long = 0L
    var socketClosedMs: Long = 0L

    var bitmapWidth: Int = 0
    var bitmapHeight: Int = 0
    var paperWidthDots: Int = 0
    var bandCount: Int = 0
    var totalBytes: Int = 0
    var targetType: String = "Unknown"
    var mutexContended: Boolean = false
    var connectAttempts: Int = 1
    var usedInsecureFallback: Boolean = false
    var replyReceived: Boolean = false

    fun recordButtonTap(ms: Long = System.currentTimeMillis()) {
        buttonTapMs = ms
    }

    fun recordGenerateDone(ms: Long = System.currentTimeMillis()) {
        generateDoneMs = ms
    }

    fun recordDecodeDone(ms: Long = System.currentTimeMillis()) {
        decodeDoneMs = ms
    }

    fun recordRasterDone(
        bands: Int,
        bytes: Int,
        width: Int,
        height: Int,
        paperDots: Int,
        ms: Long = System.currentTimeMillis()
    ) {
        rasterDoneMs = ms
        bandCount = bands
        totalBytes = bytes
        bitmapWidth = width
        bitmapHeight = height
        paperWidthDots = paperDots
    }

    fun recordMutexAcquired(waitMs: Long, ms: Long = System.currentTimeMillis()) {
        mutexWaitMs = waitMs
        mutexContended = waitMs > 10L
        mutexAcquiredMs = ms
    }

    fun recordConnected(
        target: String,
        attempts: Int = 1,
        insecure: Boolean = false,
        ms: Long = System.currentTimeMillis()
    ) {
        connectDoneMs = ms
        targetType = target
        connectAttempts = attempts
        usedInsecureFallback = insecure
    }

    fun recordSettleDone(ms: Long = System.currentTimeMillis()) {
        settleDoneMs = ms
    }

    fun recordFirstChunkWritten(ms: Long = System.currentTimeMillis()) {
        firstChunkWrittenMs = ms
    }

    fun recordLastChunkWritten(ms: Long = System.currentTimeMillis()) {
        lastChunkWrittenMs = ms
    }

    fun recordDrainDone(reply: Boolean, ms: Long = System.currentTimeMillis()) {
        drainDoneMs = ms
        replyReceived = reply
    }

    fun recordSocketClosed(ms: Long = System.currentTimeMillis()) {
        socketClosedMs = ms
        logTimeline()
    }

    private fun delta(from: Long, to: Long): Long = if (from > 0L && to >= from) to - from else 0L

    /**
     * Pure formatter for single-line summary string easily extracted from bug reports.
     */
    fun formatSummary(): String {
        val endMs = when {
            socketClosedMs > 0L -> socketClosedMs
            drainDoneMs > 0L -> drainDoneMs
            lastChunkWrittenMs > 0L -> lastChunkWrittenMs
            else -> System.currentTimeMillis()
        }
        val totalMs = delta(buttonTapMs, endMs)
        val genMs = delta(buttonTapMs, generateDoneMs)
        val prevDecode = if (generateDoneMs > 0L) generateDoneMs else buttonTapMs
        val decMs = delta(prevDecode, decodeDoneMs)
        val prevRaster = if (decodeDoneMs > 0L) decodeDoneMs else prevDecode
        val rasterMs = delta(prevRaster, rasterDoneMs)
        val connectMs = if (mutexAcquiredMs > 0L && connectDoneMs >= mutexAcquiredMs) connectDoneMs - mutexAcquiredMs else 0L
        val settleMs = delta(connectDoneMs, settleDoneMs)
        val firstWriteMs = delta(buttonTapMs, firstChunkWrittenMs)
        val writeMs = delta(firstChunkWrittenMs, lastChunkWrittenMs)
        val drainMs = delta(lastChunkWrittenMs, drainDoneMs)
        val closeMs = delta(drainDoneMs, socketClosedMs)

        return "TOTAL=${totalMs}ms generate=${genMs} decode=${decMs} raster=${rasterMs} mutexWait=${mutexWaitMs} connect=${connectMs} settle=${settleMs} firstWrite=${firstWriteMs} write=${writeMs} drain=${drainMs} close=${closeMs}"
    }

    /**
     * Pure formatter for diagnostic metadata details.
     */
    fun formatDetailedLog(): String {
        return "metadata: target=$targetType(attempts=$connectAttempts, insecure=$usedInsecureFallback), bitmap=${bitmapWidth}x${bitmapHeight}, paperWidth=${paperWidthDots}dots, bands=$bandCount, totalBytes=$totalBytes, mutexContended=$mutexContended, replyReceived=$replyReceived"
    }

    fun logTimeline() {
        Log.d(TAG, formatSummary())
        Log.d(TAG, formatDetailedLog())
    }

    companion object {
        const val TAG = "PrintTiming"
    }
}
