package com.example.techyfipos

import com.example.techyfipos.data.db.Product
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import com.example.techyfipos.ui.viewmodel.CartItem
import com.example.techyfipos.ui.viewmodel.CartViewModel
import com.example.techyfipos.ui.viewmodel.PriceMode
import com.example.techyfipos.utils.formatUnitsSold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class P0AuditFixesTest {

    @Test
    fun testR21_01_idleActivityTracking() {
        val initialTime = 100_000L
        AuthViewModel.setLastUserActivityElapsedForTesting(initialTime)
        assertEquals(initialTime, AuthViewModel.getLastUserActivityElapsed())

        AuthViewModel.recordUserActivity()
        assertEquals(android.os.SystemClock.elapsedRealtime(), AuthViewModel.getLastUserActivityElapsed())
    }

    @Test
    fun testR21_02_formatUnitsSoldWithDouble() {
        val wholeUnits = 15.0
        val fracUnits = 15.75
        val enWhole = formatUnitsSold(wholeUnits, isBangla = false)
        val enFrac = formatUnitsSold(fracUnits, isBangla = false)
        assertEquals("15", enWhole)
        assertEquals("15.75", enFrac)

        val bnWhole = formatUnitsSold(wholeUnits, isBangla = true)
        assertEquals("১৫", bnWhole)

        val enFormatString = "Invoices: %1\$d | Units: %2\$s"
        val formattedEn = String.format(java.util.Locale.US, enFormatString, 5, enWhole)
        assertEquals("Invoices: 5 | Units: 15", formattedEn)

        val bnFormatString = "চালান: %1\$d | পণ্য বিক্রয়: %2\$s"
        val formattedBn = String.format(java.util.Locale.US, bnFormatString, 5, bnWhole)
        assertEquals("চালান: 5 | পণ্য বিক্রয়: ১৫", formattedBn)
    }

    @Test
    fun testR21_03_cartHashParityAcrossPriceModes() {
        val product = Product(
            barcode = "ITEM-001",
            name = "Test Item",
            category = "General",
            purchasePrice = 80.0,
            sellingPrice = 100.0,
            stockQty = 10.0,
            reorderLevel = 2,
            unit = "pcs",
            wholesalePrice = 90.0,
            mrpPrice = 120.0
        )
        val cartItems = listOf(CartItem(product = product, quantity = 2.0))

        // Regular (Discounted) mode
        val hashRegular = CartViewModel.computeCartHash(cartItems, PriceMode.DISCOUNTED)
        val repoTriplesRegular = listOf(
            Triple(product.copy(sellingPrice = CartViewModel.effectivePrice(product, PriceMode.DISCOUNTED)), 2.0, null)
        )
        val repoHashRegular = CartViewModel.computeCartHashFromTriples(repoTriplesRegular)
        assertEquals(hashRegular, repoHashRegular)
        assertEquals("ITEM-001:2.0:100.0", hashRegular)

        // MRP mode
        val hashMrp = CartViewModel.computeCartHash(cartItems, PriceMode.MRP)
        val repoTriplesMrp = listOf(
            Triple(product.copy(sellingPrice = CartViewModel.effectivePrice(product, PriceMode.MRP)), 2.0, null)
        )
        val repoHashMrp = CartViewModel.computeCartHashFromTriples(repoTriplesMrp)
        assertEquals(hashMrp, repoHashMrp)
        assertEquals("ITEM-001:2.0:120.0", hashMrp)
        assertNotEquals(hashRegular, hashMrp)

        // Wholesale mode
        val hashWholesale = CartViewModel.computeCartHash(cartItems, PriceMode.WHOLESALE)
        val repoTriplesWholesale = listOf(
            Triple(product.copy(sellingPrice = CartViewModel.effectivePrice(product, PriceMode.WHOLESALE)), 2.0, null)
        )
        val repoHashWholesale = CartViewModel.computeCartHashFromTriples(repoTriplesWholesale)
        assertEquals(hashWholesale, repoHashWholesale)
        assertEquals("ITEM-001:2.0:90.0", hashWholesale)
        assertNotEquals(hashRegular, hashWholesale)
    }
}
