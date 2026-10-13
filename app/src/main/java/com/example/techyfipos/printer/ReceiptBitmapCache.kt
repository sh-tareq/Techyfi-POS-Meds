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
    fun buildKey(saleId: Long, lang: String, paperWidthMm: Int): String =
        "${saleId}_${lang.trim().uppercase()}_${paperWidthMm}"

    /**
     * Retrieves the cached receipt bitmap if it matches the requested parameters and is not recycled.
     */
    fun get(saleId: Long, lang: String, paperWidthMm: Int): Bitmap? {
        synchronized(lock) {
            val key = buildKey(saleId, lang, paperWidthMm)
            val bmp = cachedBitmap
            if (cachedKey == key && bmp != null && !bmp.isRecycled) {
                return bmp
            }
            return null
        }
    }

    /**
     * Stores a receipt bitmap in cache. Recycles any previously cached bitmap for a different key.
     */
    fun put(saleId: Long, lang: String, paperWidthMm: Int, bitmap: Bitmap) {
        synchronized(lock) {
            val key = buildKey(saleId, lang, paperWidthMm)
            val oldBmp = cachedBitmap
            if (cachedKey != key && oldBmp != null && !oldBmp.isRecycled && oldBmp !== bitmap) {
                oldBmp.recycle()
            }
            cachedKey = key
            cachedBitmap = bitmap
        }
    }

    /**
     * Clears the cache and recycles any held bitmap.
     */
    fun clear() {
        synchronized(lock) {
            val oldBmp = cachedBitmap
            if (oldBmp != null && !oldBmp.isRecycled) {
                oldBmp.recycle()
            }
            cachedBitmap = null
            cachedKey = null
        }
    }
}
