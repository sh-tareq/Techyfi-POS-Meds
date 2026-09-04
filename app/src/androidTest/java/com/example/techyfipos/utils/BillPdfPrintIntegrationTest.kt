package com.example.techyfipos.utils

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillPdfPrintIntegrationTest {

    @Test
    fun generatedReceiptIsAReadableSinglePagePdf() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = generateBillPdf(
            context,
            BillData(
                saleId = 12001L,
                timestampMillis = System.currentTimeMillis(),
                shopName = "Techyfi Test Store",
                shopAddress = "Dhaka",
                shopPhone = "01700000000",
                items = listOf(BillLineItem("Print test product", 2.0, 100.0, 200.0)),
                subtotal = 200.0,
                discountValue = 0.0,
                grandTotal = 200.0,
                paymentMethod = "Cash",
                customerName = "",
                customerPhone = "",
                amountPaid = 200.0,
                changeAmount = 0.0
            )
        )

        assertEquals("pdf", file.extension)
        assertTrue(isPdfFile(file))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertTrue(page.width > 0)
                    assertTrue(page.height > page.width)
                }
            }
        }
    }
}
