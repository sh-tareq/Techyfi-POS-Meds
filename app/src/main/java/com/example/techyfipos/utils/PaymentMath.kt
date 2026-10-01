package com.example.techyfipos.utils

import com.example.techyfipos.data.db.SplitPaymentEntry
import org.json.JSONArray
import org.json.JSONObject

object PaymentMath {

    fun buildAuthoritativeSplitJson(
        isSplitPayment: Boolean,
        splitPayments: List<SplitPaymentEntry>,
        storeCreditApplied: Double,
        grandTotal: Double,
        paymentMethod: String,
        redeemedVoucherCode: String? = null,
        walletPhone: String? = null,
        cardType: String? = null,
        cardLastFour: String? = null
    ): String? {
        if (!isSplitPayment && storeCreditApplied <= 0.0 && paymentMethod != "Store Credit") return null

        val array = JSONArray()

        if (isSplitPayment && splitPayments.isNotEmpty()) {
            val scInSplit = splitPayments.filter { it.method.equals("Store Credit", ignoreCase = true) }.sumOf { it.amount }
            val effectiveSC = if (scInSplit > 0.0) scInSplit else storeCreditApplied.coerceAtMost(grandTotal)
            val targetNonSC = (grandTotal - effectiveSC).coerceAtLeast(0.0)

            val nonScCollected = splitPayments.filterNot { it.method.equals("Store Credit", ignoreCase = true) }.sumOf { it.amount }
            val hasCashSplit = splitPayments.any { it.method.equals("Cash", ignoreCase = true) }
            var remainingChangeToSubtract = if (hasCashSplit && nonScCollected > targetNonSC) (nonScCollected - targetNonSC) else 0.0

            splitPayments.forEach { sp ->
                if (sp.amount > 0.0) {
                    val isCash = sp.method.equals("Cash", ignoreCase = true)
                    val isStoreCredit = sp.method.equals("Store Credit", ignoreCase = true)
                    var owedAmount = sp.amount
                    var changeAmt = 0.0

                    if (isCash && remainingChangeToSubtract > 0.0) {
                        val sub = remainingChangeToSubtract.coerceAtMost(sp.amount)
                        owedAmount = (sp.amount - sub).coerceAtLeast(0.0)
                        changeAmt = sub
                        remainingChangeToSubtract -= sub
                    }

                    if (owedAmount > 0.0 || isCash) {
                        array.put(JSONObject().apply {
                            put("method", sp.method)
                            put("amount", owedAmount)
                            if (isCash && changeAmt > 0.0) {
                                put("tenderedAmount", sp.amount)
                                put("changeAmount", changeAmt)
                            }
                            if (isStoreCredit && !redeemedVoucherCode.isNullOrBlank()) {
                                put("voucherCode", redeemedVoucherCode)
                            }
                            val resolvedWalletPhone = sp.walletPhone.takeIf { !it.isNullOrBlank() } ?: walletPhone
                            val resolvedCardType = sp.cardType.takeIf { !it.isNullOrBlank() } ?: cardType
                            val resolvedCardLastFour = sp.cardLastFour.takeIf { !it.isNullOrBlank() } ?: cardLastFour
                            if (!resolvedWalletPhone.isNullOrBlank()) put("walletPhone", resolvedWalletPhone)
                            if (!resolvedCardType.isNullOrBlank()) put("cardType", resolvedCardType)
                            if (!resolvedCardLastFour.isNullOrBlank()) put("cardLastFour", resolvedCardLastFour)
                            if (!sp.referenceNote.isNullOrBlank()) put("referenceNote", sp.referenceNote)
                        })
                    }
                }
            }
            if (scInSplit == 0.0 && storeCreditApplied > 0.0) {
                val scAmt = storeCreditApplied.coerceAtMost(grandTotal)
                if (scAmt > 0.0) {
                    array.put(JSONObject().apply {
                        put("method", "Store Credit")
                        put("amount", scAmt)
                        if (!redeemedVoucherCode.isNullOrBlank()) put("voucherCode", redeemedVoucherCode)
                    })
                }
            }
        } else if (storeCreditApplied > 0.0 || paymentMethod == "Store Credit") {
            val scAmount = storeCreditApplied.coerceAtMost(grandTotal)
            val remainingAmt = (grandTotal - scAmount).coerceAtLeast(0.0)
            if (scAmount > 0.0) {
                array.put(JSONObject().apply {
                    put("method", "Store Credit")
                    put("amount", scAmount)
                    if (!redeemedVoucherCode.isNullOrBlank()) put("voucherCode", redeemedVoucherCode)
                })
            }
            if (remainingAmt > 0.0 && paymentMethod != "Store Credit") {
                array.put(JSONObject().apply {
                    put("method", paymentMethod)
                    put("amount", remainingAmt)
                    if (!walletPhone.isNullOrBlank()) put("walletPhone", walletPhone)
                    if (!cardType.isNullOrBlank()) put("cardType", cardType)
                    if (!cardLastFour.isNullOrBlank()) put("cardLastFour", cardLastFour)
                })
            }
        }

        return if (array.length() > 0) array.toString() else null
    }

    fun calculateChange(
        grandTotal: Double,
        amountPaid: Double,
        paymentMethod: String,
        splitJson: String?,
        storeCreditApplied: Double = 0.0
    ): Double {
        if (!splitJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(splitJson)
                var changeFromCash = 0.0
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.has("changeAmount")) {
                        changeFromCash += obj.optDouble("changeAmount", 0.0)
                    }
                }
                if (changeFromCash > 0.0) return changeFromCash
            } catch (_: Exception) {}
        }

        if (paymentMethod.equals("Cash", ignoreCase = true)) {
            val required = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
            return (amountPaid - required).coerceAtLeast(0.0)
        }

        return 0.0
    }
}
