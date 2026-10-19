package com.example.techyfipos.printer

import android.graphics.Bitmap

/**
 * Thread-safe in-memory cache for pre-rendered receipt bitmaps.
 * Avoids the expensive JPEG compression and decompression disk round-trip
 * before direct thermal printing.
 *
 * Style constraint: strictly no em dashes in any comments or strings.
 */
object ReceiptBitmapCache {
    private val lock = Any()
    private var cachedKey: String? = null
    private var cachedBitmap: Bitmap? = null

    /**
     * Pure function that formats a deterministic cache key.
     */
    fun buildKey(saleId: Long, lang: String, paperWidthMm: Int, billHash: Int? = null): String =
        if (billHash != null) {
            "${saleId}_${lang.trim().uppercase()}_${paperWidthMm}_${billHash}"
        } else {
            "${saleId}_${lang.trim().uppercase()}_${paperWidthMm}"
        }

    /**
     * Retrieves the cached receipt bitmap if it matches the requested parameters and is not recycled.
     */
    fun get(saleId: Long, lang: String, paperWidthMm: Int, billHash: Int? = null): Bitmap? {
        synchronized(lock) {
            val key = buildKey(saleId, lang, paperWidthMm, billHash)
            val bmp = cachedBitmap
            if (cachedKey == key && bmp != null && !bmp.isRecycled) {
                return bmp
            }
            return null
        }
    }

    /**
     * Stores a receipt bitmap in cache. Does NOT recycle previous bitmaps because an in-flight
     * print operation may still be actively reading them.
     */
    fun put(saleId: Long, lang: String, paperWidthMm: Int, bitmap: Bitmap, billHash: Int? = null) {
        synchronized(lock) {
            val key = buildKey(saleId, lang, paperWidthMm, billHash)
            cachedKey = key
            cachedBitmap = bitmap
        }
    }

    /**
     * Clears the cache by dropping references without recycling, avoiding crashing in-flight prints.
     */
    fun clear() {
        synchronized(lock) {
            cachedBitmap = null
            cachedKey = null
        }
    }
}
