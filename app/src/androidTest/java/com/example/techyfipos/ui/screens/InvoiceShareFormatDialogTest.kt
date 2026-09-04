package com.example.techyfipos.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.techyfipos.utils.BillData
import com.example.techyfipos.utils.BillLineItem
import org.junit.Rule
import org.junit.Test

class InvoiceShareFormatDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shareButtonOffersPdfJpegAndBothFormats() {
        composeRule.setContent {
            MaterialTheme {
                ReceiptSummaryDialog(
                    bill = BillData(
                        saleId = 39L,
                        timestampMillis = 1786833564332L,
                        shopName = "Techyfi Test Store",
                        shopAddress = "Dhaka",
                        shopPhone = "01700000000",
                        items = listOf(BillLineItem("Test product", 1.0, 140.0, 140.0)),
                        subtotal = 140.0,
                        discountValue = 0.0,
                        grandTotal = 140.0,
                        paymentMethod = "Cash",
                        customerName = "",
                        customerPhone = "",
                        amountPaid = 140.0,
                        changeAmount = 0.0
                    ),
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Share").performClick()
        composeRule.onNodeWithText("PDF Document").assertIsDisplayed()
        composeRule.onNodeWithText("JPEG Image").assertIsDisplayed()
        composeRule.onNodeWithText("PDF + JPEG").assertIsDisplayed()
        composeRule.onNodeWithText("Continue To Share").assertIsDisplayed()
    }
}
