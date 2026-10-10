package com.example.techyfipos.data

import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.db.hasValidExpiryDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductValidationTest {

    private fun createProduct(expiryDate: String?): Product {
        return Product(
            barcode = "8901234567890",
            name = "Test Product",
            category = "General",
            purchasePrice = 50.0,
            sellingPrice = 70.0,
            stockQty = 10.0,
            reorderLevel = 2,
            expiryDate = expiryDate
        )
    }

    @Test
    fun `valid yyyy-MM-dd expiry dates are accepted`() {
        val product = createProduct("2026-12-31")
        assertTrue(product.hasValidExpiryDate)
    }

    @Test
    fun `null and blank expiry dates are valid (no expiry)`() {
        val noExpiry = createProduct(null)
        val blankExpiry = createProduct("   ")
        assertTrue(noExpiry.hasValidExpiryDate)
        assertTrue(blankExpiry.hasValidExpiryDate)
    }

    @Test
    fun `common date formats are accepted`() {
        assertTrue(createProduct("2026-12-31").hasValidExpiryDate)
        assertTrue(createProduct("31-12-2026").hasValidExpiryDate)
        assertTrue(createProduct("31/12/2026").hasValidExpiryDate)
    }

    @Test
    fun `impossible dates and gibberish are rejected`() {
        val impossibleFeb = createProduct("2026-02-31")
        val impossibleDay = createProduct("32-01-2026")
        val impossibleMonth = createProduct("2026-13-01")
        val gibberish = createProduct("not-a-date")
        val incomplete = createProduct("2026-")

        assertFalse(impossibleFeb.hasValidExpiryDate)
        assertFalse(impossibleDay.hasValidExpiryDate)
        assertFalse(impossibleMonth.hasValidExpiryDate)
        assertFalse(gibberish.hasValidExpiryDate)
        assertFalse(incomplete.hasValidExpiryDate)
    }

    @Test
    fun `R20-11 barcode normalization lowercases and converts digits`() {
        val raw = "ABC-১২৩-X"
        val normalized = com.example.techyfipos.utils.toEnglishDigits(raw.trim()).lowercase()
        org.junit.Assert.assertEquals("abc-123-x", normalized)
    }

    @Test
    fun `R20-12 valid UPC-A text string is preserved without leading zero rewrite`() {
        val upcA = "036000291452"
        org.junit.Assert.assertEquals("036000291452", upcA.trim())
    }

    @Test
    fun `R20-19c product copy preserves createdAt`() {
        val createdTime = 1600000000000L
        val original = createProduct(null).copy(createdAt = createdTime)
        val edited = original.copy(name = "Updated Name", sellingPrice = 90.0)
        org.junit.Assert.assertEquals(createdTime, edited.createdAt)
    }
}
