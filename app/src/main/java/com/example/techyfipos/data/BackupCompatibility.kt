package com.example.techyfipos.data

import com.example.techyfipos.data.db.Sale
import com.example.techyfipos.data.db.SaleItem
import com.example.techyfipos.utils.toEnglishDigits

internal fun buildBackwardCompatibleSale(
    id: Long = 0,
    timestamp: Long,
    subtotal: Double? = null,
    discountAmount: Double? = null,
    isPercentageDiscount: Boolean? = null,
    grandTotal: Double? = null,
    legacyTotal: Double? = null,
    paymentMethod: String? = null,
    customerName: String? = null,
    customerPhone: String? = null,
    amountPaid: Double? = null,
    walletPhone: String? = null,
    cardType: String? = null,
    cardLastFour: String? = null,
    taxRate: Double? = null,
    taxAmount: Double? = null,
    splitPaymentJson: String? = null,
    performedByUserId: Long? = null,
    performedByName: String? = null,
    loyaltyPhone: String? = null,
    loyaltyPointsRedeemed: Int = 0,
    storeCreditApplied: Double? = null
): Sale {
    val restoredGrandTotal = (grandTotal ?: legacyTotal ?: 0.0).coerceAtLeast(0.0)
    val restoredDiscount = (discountAmount ?: 0.0).coerceAtLeast(0.0)
    val restoredSubtotal = (subtotal ?: restoredGrandTotal + restoredDiscount)
        .coerceAtLeast(restoredGrandTotal)
    val restoredPaymentMethod = paymentMethod?.trim().orEmpty().ifBlank { "Cash" }
    val restoredAmountPaid = amountPaid ?: if (restoredPaymentMethod.equals("Due", true)) {
        0.0
    } else {
        restoredGrandTotal
    }

    return Sale(
        id = id.coerceAtLeast(0),
        timestamp = timestamp,
        subtotal = restoredSubtotal,
        discountAmount = restoredDiscount,
        isPercentageDiscount = isPercentageDiscount ?: false,
        grandTotal = restoredGrandTotal,
        paymentMethod = restoredPaymentMethod,
        customerName = customerName?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        customerPhone = customerPhone?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        amountPaid = restoredAmountPaid.coerceAtLeast(0.0),
        walletPhone = walletPhone?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        cardType = cardType?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        cardLastFour = cardLastFour?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        taxRate = (taxRate ?: 0.0).coerceAtLeast(0.0),
        taxAmount = (taxAmount ?: 0.0).coerceAtLeast(0.0),
        splitPaymentJson = splitPaymentJson?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        performedByUserId = performedByUserId?.takeIf { it > 0L },
        performedByName = performedByName?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        loyaltyPhone = loyaltyPhone?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        loyaltyPointsRedeemed = loyaltyPointsRedeemed.coerceAtLeast(0),
        storeCreditApplied = (storeCreditApplied ?: 0.0).coerceAtLeast(0.0)
    )
}

internal fun buildBackwardCompatibleSaleItem(
    saleId: Long,
    productBarcode: String?,
    productName: String?,
    price: Double?,
    quantity: Double?,
    note: String? = null,
    purchasePriceAtSale: Double? = null
): SaleItem {
    val barcode = toEnglishDigits(productBarcode?.trim().orEmpty())
    return SaleItem(
        saleId = saleId,
        productBarcode = barcode,
        productName = productName?.trim().orEmpty().ifBlank { barcode.ifBlank { "Restored product" } },
        price = (price ?: 0.0).coerceAtLeast(0.0),
        quantity = (quantity ?: 1.0).coerceAtLeast(0.001),
        note = note?.trim()?.takeIf { it.isNotEmpty() && it != "null" },
        purchasePriceAtSale = purchasePriceAtSale
    )
}
