package com.example.techyfipos.utils

import com.example.techyfipos.data.db.SplitPaymentEntry
import org.json.JSONArray
import org.json.JSONObject

object PaymentMath {

    fun resolveMethodToSave(
        isSplitPayment: Boolean,
        storeCreditApplied: Double,
        grandTotal: Double,
        paymentMethod: String
    ): String {
        return if (storeCreditApplied >= grandTotal && grandTotal > 0.0) {
            "Store Credit"
        } else if (isSplitPayment) {
            "Split"
        } else if (storeCreditApplied > 0.0 && storeCreditApplied < grandTotal) {
            "Split"
        } else {
            paymentMethod
        }
    }

    fun buildAuthoritativeSplitJson(
        isSplitPayment: Boolean,
        splitPayments: List<SplitPaymentEntry>,
        storeCreditApplied: Double,
        grandTotal: Double,
        paymentMethod: String,
        amountPaid: Double = 0.0,
        redeemedVoucherCode: String? = null,
        walletPhone: String? = null,
        cardType: String? = null,
        cardLastFour: String? = null
    ): String? {
        if (!isSplitPayment && storeCreditApplied <= 0.0 && paymentMethod != "Store Credit") return null

        val array = JSONArray()

        if (isSplitPayment && splitPayments.isNotEmpty()) {
            val scInSplit = splitPayments.filter { it.method.equals("Store Credit", ignoreCase = true) }.sumOf { it.amount }
            val nonCashSum = splitPayments.filterNot { it.method.equals("Cash", ignoreCase = true) }.sumOf { it.amount }
            val cashCollected = splitPayments.filter { it.method.equals("Cash", ignoreCase = true) }.sumOf { it.amount }

            // Determine if auto SC is needed (e.g. split builder target was grandTotal - storeCreditApplied)
            // or if splitPayments already covers the full grand total.
            val splitEffectiveTotal = nonCashSum + minOf(cashCollected, grandTotal)
            val neededForGrandTotal = (grandTotal - splitEffectiveTotal).coerceAtLeast(0.0)
            val autoScAmount = if (storeCreditApplied > 0.0 && neededForGrandTotal > 0.0) {
                storeCreditApplied.coerceAtMost(neededForGrandTotal)
            } else 0.0

            if (autoScAmount > 0.0) {
                array.put(JSONObject().apply {
                    put("method", "Store Credit")
                    put("amount", autoScAmount)
                    if (!redeemedVoucherCode.isNullOrBlank()) put("voucherCode", redeemedVoucherCode)
                })
            }

            val targetForSplit = (grandTotal - autoScAmount).coerceAtLeast(0.0)

            val maxAllowedNonCash = if (nonCashSum > targetForSplit) {
                (targetForSplit - minOf(cashCollected, targetForSplit)).coerceAtLeast(0.0)
            } else {
                nonCashSum
            }

            var nonCashBudgetRemaining = maxAllowedNonCash
            var cashNeeded = (targetForSplit - maxAllowedNonCash).coerceAtLeast(0.0)

            splitPayments.forEach { sp ->
                if (sp.amount > 0.0) {
                    val isCash = sp.method.equals("Cash", ignoreCase = true)
                    val isStoreCredit = sp.method.equals("Store Credit", ignoreCase = true)
                    var owedAmount = sp.amount
                    var changeAmt = 0.0

                    if (isCash) {
                        if (cashCollected > cashNeeded) {
                            val sub = (cashCollected - cashNeeded).coerceAtMost(sp.amount)
                            owedAmount = (sp.amount - sub).coerceAtLeast(0.0)
                            changeAmt = sub
                            cashNeeded = 0.0
                        }
                    } else {
                        if (nonCashSum > targetForSplit && nonCashBudgetRemaining >= 0.0) {
                            val allocated = minOf(sp.amount, nonCashBudgetRemaining)
                            owedAmount = allocated
                            nonCashBudgetRemaining -= allocated
                        }
                    }

                    if (owedAmount > 0.0 || (isCash && changeAmt > 0.0)) {
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
                            val isWallet = sp.method.equals("bKash", ignoreCase = true) ||
                                sp.method.equals("Nagad", ignoreCase = true) ||
                                sp.method.equals("Rocket", ignoreCase = true)
                            val isCard = sp.method.equals("Card", ignoreCase = true) ||
                                sp.method.equals("Visa", ignoreCase = true) ||
                                sp.method.equals("Mastercard", ignoreCase = true) ||
                                sp.method.equals("Amex", ignoreCase = true)
                            if (isWallet && !sp.walletPhone.isNullOrBlank()) {
                                put("walletPhone", sp.walletPhone)
                            }
                            if (isCard) {
                                if (!sp.cardType.isNullOrBlank()) put("cardType", sp.cardType)
                                if (!sp.cardLastFour.isNullOrBlank()) put("cardLastFour", sp.cardLastFour)
                            }
                            if (!sp.referenceNote.isNullOrBlank()) put("referenceNote", sp.referenceNote)
                        })
                    }
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
            if (remainingAmt > 0.0) {
                val remainderMethod = if (paymentMethod.equals("Store Credit", ignoreCase = true)) {
                    if (amountPaid >= remainingAmt && amountPaid > 0.0) "Cash" else "Due"
                } else {
                    paymentMethod
                }
                val isCash = remainderMethod.equals("Cash", ignoreCase = true)
                val tendered = if (isCash && amountPaid > 0.0) amountPaid else remainingAmt
                val change = if (isCash && tendered > remainingAmt) tendered - remainingAmt else 0.0

                array.put(JSONObject().apply {
                    put("method", remainderMethod)
                    put("amount", remainingAmt)
                    if (isCash && change > 0.0) {
                        put("tenderedAmount", tendered)
                        put("changeAmount", change)
                    }
                    val isWallet = remainderMethod.equals("bKash", ignoreCase = true) ||
                        remainderMethod.equals("Nagad", ignoreCase = true) ||
                        remainderMethod.equals("Rocket", ignoreCase = true)
                    val isCard = remainderMethod.equals("Card", ignoreCase = true) ||
                        remainderMethod.equals("Visa", ignoreCase = true) ||
                        remainderMethod.equals("Mastercard", ignoreCase = true) ||
                        remainderMethod.equals("Amex", ignoreCase = true)
                    if (isWallet && !walletPhone.isNullOrBlank()) put("walletPhone", walletPhone)
                    if (isCard) {
                        if (!cardType.isNullOrBlank()) put("cardType", cardType)
                        if (!cardLastFour.isNullOrBlank()) put("cardLastFour", cardLastFour)
                    }
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
                var foundChangeKey = false
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.has("changeAmount")) {
                        changeFromCash += obj.optDouble("changeAmount", 0.0)
                        foundChangeKey = true
                    }
                }
                if (foundChangeKey) return changeFromCash
            } catch (_: Exception) {}
        }

        if (paymentMethod.equals("Cash", ignoreCase = true)) {
            val required = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
            return (amountPaid - required).coerceAtLeast(0.0)
        }

        return 0.0
    }

    fun calculateItemEffectivePrice(
        price: Double,
        taxRate: Double,
        isTaxInclusive: Boolean,
        isTaxable: Boolean
    ): Double {
        return if (isTaxInclusive && isTaxable && taxRate > 0.0) {
            price / (1.0 + taxRate / 100.0)
        } else {
            price
        }
    }

    data class ReturnRefundApportionment(
        val storeCreditToRestore: Double,
        val externalRefundAmount: Double
    )

    fun calculateReturnRefundApportionment(
        refundTotal: Double,
        saleStoreCreditApplied: Double,
        storeCreditRestoredSoFar: Double,
        dueReduction: Double,
        refundMethod: String
    ): ReturnRefundApportionment {
        val netAfterDue = (refundTotal - dueReduction).coerceAtLeast(0.0)
        val remainingScToRestore = (saleStoreCreditApplied - storeCreditRestoredSoFar).coerceAtLeast(0.0)
        val scToRestore = minOf(netAfterDue, remainingScToRestore)
        val externalRefund = if (refundMethod.equals("Store Credit", ignoreCase = true)) {
            0.0
        } else {
            (netAfterDue - scToRestore).coerceAtLeast(0.0)
        }
        return ReturnRefundApportionment(
            storeCreditToRestore = scToRestore,
            externalRefundAmount = externalRefund
        )
    }

    fun calculateDueAddition(
        paymentMethod: String,
        grandTotal: Double,
        amountPaid: Double,
        splitPaymentJson: String?
    ): Double {
        var dueAddition = 0.0
        if (paymentMethod.equals("Due", ignoreCase = true)) {
            dueAddition = (grandTotal - amountPaid).coerceAtLeast(0.0)
        } else if (paymentMethod.equals("Split", ignoreCase = true) && !splitPaymentJson.isNullOrBlank()) {
            try {
                val array = JSONArray(splitPaymentJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("method", "").equals("Due", ignoreCase = true)) {
                        dueAddition += obj.optDouble("amount", 0.0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return dueAddition
    }
}
