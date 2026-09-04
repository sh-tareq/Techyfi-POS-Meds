package com.example.techyfipos.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.techyfipos.data.db.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class ExcelHelperIntegrationTest {

    @Test
    fun generatedTemplateCanBeImported() {
        val bytes = ByteArrayOutputStream().use { output ->
            ExcelHelper.generateProductsTemplate(output)
            output.toByteArray()
        }

        val result = ExcelHelper.importProducts(ByteArrayInputStream(bytes))

        assertEquals(7, result.successCount)
        assertEquals(1, result.failCount)
        assertTrue(result.importedProducts.any { it.name == "Pran Potato Crackers 25g" })
    }

    @Test
    fun bengaliProductTextSurvivesExcelRoundTrip() {
        val original = Product(
            barcode = "BAN-123456",
            name = "বাংলা পণ্যের নাম ৫০০ গ্রাম",
            category = "মুদিখানা",
            purchasePrice = 85.5,
            sellingPrice = 120.0,
            stockQty = 14.0,
            reorderLevel = 3
        )
        val bytes = ByteArrayOutputStream().use { output ->
            ExcelHelper.exportProducts(listOf(original), output)
            output.toByteArray()
        }

        val result = ExcelHelper.importProducts(ByteArrayInputStream(bytes))

        assertEquals(1, result.successCount)
        assertEquals(0, result.failCount)
        assertEquals(original, result.importedProducts.single())
    }
}
