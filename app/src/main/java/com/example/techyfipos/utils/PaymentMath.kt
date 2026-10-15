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
        return if (!paymentMethod.equals("Split", ignoreCase = true) && storeCreditApplied >= grandTotal && grandTotal > 0.0) {
            "Store Credit"
        } else if (isSplitPayment || paymentMethod.equals("Split", ignoreCase = true)) {
            "Split"
        } else if (storeCreditApplied > 0.0 && storeCreditApplied < grandTotal) {
            "Split"
        } else {
            paymentMethod
        }
    }

    data class PaymentBreakdown(
        val paymentMethodToSave: String,
        val splitPaymentJson: String?,
        val changeAmount: Double,
        val amountPaidToRecord: Double,
        val effectiveAmountPaid: Double,
        val dueAmount: Double
    )

    fun calculatePaymentBreakdown(
        grandTotal: Double,
        storeCreditApplied: Double = 0.0,
        isSplitPayment: Boolean = false,
        splitPayments: List<SplitPaymentEntry> = emptyList(),
        paymentMethod: String = "Cash",
        amountPaid: Double = 0.0,
        redeemedVoucherCode: String? = null,
        walletPhone: String? = null,
        cardType: String? = null,
        cardLastFour: String? = null,
        voucherAmount: Double? = null,
        customerCreditAmount: Double? = null
    ): PaymentBreakdown {
        if (!isSplitPayment) {
            val scAmount = if (storeCreditApplied > 0.0) storeCreditApplied.coerceAtMost(grandTotal) else 0.0
            val remainingAmt = (grandTotal - scAmount).coerceAtLeast(0.0)

            if (scAmount >= grandTotal && grandTotal > 0.0) {
                // Fully covered by Store Credit - pure non-split method saves null splitPaymentJson
                return PaymentBreakdown(
                    paymentMethodToSave = "Store Credit",
                    splitPaymentJson = null,
                    changeAmount = 0.0,
                    amountPaidToRecord = grandTotal,
                    effectiveAmountPaid = grandTotal,
                    dueAmount = 0.0
                )
            } else if (scAmount > 0.0) {
                // Partial Store Credit + remainder method
                val remainderMethod = if (paymentMethod.equals("Store Credit", ignoreCase = true)) {
                    "Due"
                } else {
                    paymentMethod
                }
                val isCash = remainderMethod.equals("Cash", ignoreCase = true)
                val isDue = remainderMethod.equals("Due", ignoreCase = true)
                val tendered = if (isCash && amountPaid > 0.0) amountPaid else remainingAmt
                val change = if (isCash && tendered > remainingAmt) tendered - remainingAmt else 0.0
                val due = if (isDue) remainingAmt else 0.0

                val vAmt = if (voucherAmount != null) voucherAmount.coerceAtMost(scAmount) else scAmount
                val cAmt = if (customerCreditAmount != null) customerCreditAmount.coerceAtMost(scAmount - vAmt) else (scAmount - vAmt).coerceAtLeast(0.0)

                val array = JSONArray()
                array.put(JSONObject().apply {
                    put("method", "Store Credit")
                    put("amount", scAmount)
                    if (!redeemedVoucherCode.isNullOrBlank()) {
                        put("voucherCode", redeemedVoucherCode)
                        put("voucherAmount", vAmt)
                        put("customerCreditAmount", cAmt)
                    }
                })
                if (remainingAmt > 0.0) {
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

                val effPaid = if (isDue) scAmount else if (isCash) tendered else grandTotal
                return PaymentBreakdown(
                    paymentMethodToSave = "Split",
                    splitPaymentJson = array.toString(),
                    changeAmount = change,
                    amountPaidToRecord = if (isCash) tendered else if (isDue) scAmount else grandTotal,
                    effectiveAmountPaid = effPaid,
                    dueAmount = due
                )
            } else {
                // Single payment with NO store credit
                val isCash = paymentMethod.equals("Cash", ignoreCase = true)
                val isDue = paymentMethod.equals("Due", ignoreCase = true)
                if (isDue && amountPaid > 0.0) {
                    val cashPaid = minOf(amountPaid, grandTotal)
                    val remainingDue = (grandTotal - cashPaid).coerceAtLeast(0.0)
                    val array = JSONArray().apply {
                        if (cashPaid > 0.0) {
                            put(JSONObject().apply {
                                put("method", "Cash")
                                put("amount", cashPaid)
                            })
                        }
                        if (remainingDue > 0.0) {
                            put(JSONObject().apply {
                                put("method", "Due")
                                put("amount", remainingDue)
                            })
                        }
                    }
                    return PaymentBreakdown(
                        paymentMethodToSave = "Split",
                        splitPaymentJson = array.toString(),
                        changeAmount = (amountPaid - grandTotal).coerceAtLeast(0.0),
                        amountPaidToRecord = cashPaid,
                        effectiveAmountPaid = cashPaid,
                        dueAmount = remainingDue
                    )
                }

                val change = if (isCash && amountPaid > grandTotal) amountPaid - grandTotal else 0.0
                val due = if (isDue) (grandTotal - amountPaid).coerceAtLeast(0.0) else 0.0
                val effPaid = if (isDue) 0.0 else if (isCash) amountPaid else grandTotal

                return PaymentBreakdown(
                    paymentMethodToSave = paymentMethod,
                    splitPaymentJson = null,
                    changeAmount = change,
                    amountPaidToRecord = if (isCash) amountPaid else if (isDue) 0.0 else grandTotal,
                    effectiveAmountPaid = effPaid,
                    dueAmount = due
                )
            }
        }

        // Split Payment flow
        val methodToSave = resolveMethodToSave(
            isSplitPayment = true,
            storeCreditApplied = storeCreditApplied,
            grandTotal = grandTotal,
            paymentMethod = paymentMethod
        )

        val scEntries = splitPayments.filter { it.method.equals("Store Credit", ignoreCase = true) }
        val splitScSum = scEntries.sumOf { it.amount }

        // 1. Store Credit allocation (Auto + Explicit Split Rows)
        // If splitPayments already contains explicit Store Credit covering storeCreditApplied,
        // do not duplicate auto store credit.
        val autoScAmount = if (storeCreditApplied > 0.0 && splitScSum < storeCreditApplied) {
            storeCreditApplied.coerceAtMost(grandTotal)
        } else 0.0
        val remainingForSplitSc = (grandTotal - autoScAmount).coerceAtLeast(0.0)
        val effectiveSplitSc = minOf(splitScSum, remainingForSplitSc)

        var scBudgetRemaining = effectiveSplitSc
        val allocatedSplitSc = mutableListOf<Double>()
        for (entry in scEntries) {
            val allocated = minOf(entry.amount, scBudgetRemaining)
            allocatedSplitSc.add(allocated)
            scBudgetRemaining = (scBudgetRemaining - allocated).coerceAtLeast(0.0)
        }

        val totalEffectiveSc = autoScAmount + effectiveSplitSc
        val remainingAfterSc = (grandTotal - totalEffectiveSc).coerceAtLeast(0.0)

        // 2. Electronic / Other Non-Cash, Non-Due allocation (Card, bKash, Nagad, Rocket, etc.)
        val electronicEntries = splitPayments.filterNot {
            it.method.equals("Cash", ignoreCase = true) ||
            it.method.equals("Due", ignoreCase = true) ||
            it.method.equals("Store Credit", ignoreCase = true)
        }
        val electronicSum = electronicEntries.sumOf { it.amount }
        require(electronicSum <= remainingAfterSc + 0.001) {
            "Over-tender is not permitted on electronic or card payments"
        }
        val effectiveElectronic = minOf(electronicSum, remainingAfterSc)

        var elecBudgetRemaining = effectiveElectronic
        val allocatedElectronic = mutableListOf<Double>()
        for (entry in electronicEntries) {
            val allocated = minOf(entry.amount, elecBudgetRemaining)
            allocatedElectronic.add(allocated)
            elecBudgetRemaining = (elecBudgetRemaining - allocated).coerceAtLeast(0.0)
        }

        val remainingAfterElectronic = (remainingAfterSc - effectiveElectronic).coerceAtLeast(0.0)

        // 3. Cash & Due allocation
        val cashEntries = splitPayments.filter { it.method.equals("Cash", ignoreCase = true) }
        val totalCashCollected = cashEntries.sumOf { it.amount }

        val dueEntries = splitPayments.filter { it.method.equals("Due", ignoreCase = true) }
        val dueEntered = dueEntries.sumOf { it.amount }

        // Due is unpaid debt. It cannot exceed what remains after electronic and cash tenders.
        val unpaidBeforeDue = (remainingAfterElectronic - totalCashCollected).coerceAtLeast(0.0)
        val effectiveDue = minOf(dueEntered, unpaidBeforeDue)

        var dueBudgetRemaining = effectiveDue
        val allocatedDue = mutableListOf<Double>()
        for (entry in dueEntries) {
            val allocated = minOf(entry.amount, dueBudgetRemaining)
            allocatedDue.add(allocated)
            dueBudgetRemaining = (dueBudgetRemaining - allocated).coerceAtLeast(0.0)
        }

        val cashNeeded = (remainingAfterElectronic - effectiveDue).coerceAtLeast(0.0)
        val totalCashChange = (totalCashCollected - cashNeeded).coerceAtLeast(0.0)

        // Allocate cash change and applied amount across cash rows (deducting change sequentially from the last row)
        var remainingChangeToDeduct = totalCashChange
        val reversedAllocatedCash = mutableListOf<Pair<Double, Double>>() // Pair(appliedAmount, changeAmount)
        for (entry in cashEntries.reversed()) {
            val changeForRow = minOf(entry.amount, remainingChangeToDeduct)
            val applied = (entry.amount - changeForRow).coerceAtLeast(0.0)
            remainingChangeToDeduct = (remainingChangeToDeduct - changeForRow).coerceAtLeast(0.0)
            reversedAllocatedCash.add(applied to changeForRow)
        }
        val allocatedCash = reversedAllocatedCash.reversed()

        // Build the Authoritative Split JSON Array
        val array = JSONArray()

        // Auto SC row first, if applicable
        if (autoScAmount > 0.0) {
            array.put(JSONObject().apply {
                put("method", "Store Credit")
                put("amount", autoScAmount)
                if (!redeemedVoucherCode.isNullOrBlank()) put("voucherCode", redeemedVoucherCode)
            })
        }

        // Iterate over each splitPayment entry in its original order
        var scIndex = 0
        var elecIndex = 0
        var dueIndex = 0
        var cashIndex = 0

        for (sp in splitPayments) {
            if (sp.amount <= 0.0) continue

            val isCash = sp.method.equals("Cash", ignoreCase = true)
            val isDue = sp.method.equals("Due", ignoreCase = true)
            val isSc = sp.method.equals("Store Credit", ignoreCase = true)

            var owedAmount = 0.0
            var changeAmt = 0.0

            when {
                isCash -> {
                    val pair = allocatedCash[cashIndex++]
                    owedAmount = pair.first
                    changeAmt = pair.second
                }
                isDue -> {
                    owedAmount = allocatedDue[dueIndex++]
                }
                isSc -> {
                    owedAmount = allocatedSplitSc[scIndex++]
                }
                else -> {
                    owedAmount = allocatedElectronic[elecIndex++]
                }
            }

            if (owedAmount > 0.0 || (isCash && changeAmt > 0.0)) {
                array.put(JSONObject().apply {
                    put("method", sp.method)
                    put("amount", owedAmount)
                    if (isCash && changeAmt > 0.0) {
                        put("tenderedAmount", sp.amount)
                        put("changeAmount", changeAmt)
                    } else if (!isCash && sp.amount > owedAmount) {
                        put("tenderedAmount", sp.amount)
                    }
                    if (isSc && !redeemedVoucherCode.isNullOrBlank()) {
                        put("voucherCode", redeemedVoucherCode)
                        val vAmt = if (voucherAmount != null) voucherAmount.coerceAtMost(owedAmount) else owedAmount
                        val cAmt = if (customerCreditAmount != null) customerCreditAmount.coerceAtMost(owedAmount - vAmt) else (owedAmount - vAmt).coerceAtLeast(0.0)
                        put("voucherAmount", vAmt)
                        put("customerCreditAmount", cAmt)
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

        val effPaid = splitPayments.filterNot { it.method.equals("Due", ignoreCase = true) }.sumOf { it.amount }
        val amountPaidToRec = effPaid + autoScAmount

        return PaymentBreakdown(
            paymentMethodToSave = methodToSave,
            splitPaymentJson = if (methodToSave.equals("Split", ignoreCase = true) && array.length() > 0) array.toString() else null,
            changeAmount = totalCashChange,
            amountPaidToRecord = amountPaidToRec,
            effectiveAmountPaid = effPaid,
            dueAmount = effectiveDue
        )
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
        cardLastFour: String? = null,
        voucherAmount: Double? = null,
        customerCreditAmount: Double? = null
    ): String? {
        return calculatePaymentBreakdown(
            grandTotal = grandTotal,
            storeCreditApplied = storeCreditApplied,
            isSplitPayment = isSplitPayment,
            splitPayments = splitPayments,
            paymentMethod = paymentMethod,
            amountPaid = amountPaid,
            redeemedVoucherCode = redeemedVoucherCode,
            walletPhone = walletPhone,
            cardType = cardType,
            cardLastFour = cardLastFour,
            voucherAmount = voucherAmount,
            customerCreditAmount = customerCreditAmount
        ).splitPaymentJson
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

    data class SimpleSalePayment(
        val saleId: Long,
        val paymentMethod: String,
        val grandTotal: Double,
        val splitPaymentJson: String? = null,
        val amountPaid: Double = 0.0
    )

    data class SimpleReturnPayment(
        val saleId: Long,
        val refundMethod: String,
        val refundAmount: Double,
        val externalRefundAmount: Double = refundAmount,
        val dueAdjustmentAmount: Double = 0.0
    )

    fun aggregatePaymentBreakdown(
        sales: List<SimpleSalePayment>,
        returns: List<SimpleReturnPayment>,
        salesById: Map<Long, SimpleSalePayment> = sales.associateBy { it.saleId }
    ): Map<String, Double> {
        val paymentTotals = mutableMapOf<String, Double>()

        // 1. Accumulate sales: break down Split payments into their actual component methods
        for (sale in sales) {
            if (sale.paymentMethod.equals("Split", ignoreCase = true) && !sale.splitPaymentJson.isNullOrBlank()) {
                try {
                    val arr = JSONArray(sale.splitPaymentJson)
                    var parsedAny = false
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val m = obj.optString("method", "").ifBlank { "Other" }
                        val amt = obj.optDouble("amount", 0.0)
                        if (amt > 0.0) {
                            paymentTotals[m] = (paymentTotals[m] ?: 0.0) + amt
                            parsedAny = true
                        }
                    }
                    if (!parsedAny) {
                        paymentTotals["Split"] = (paymentTotals["Split"] ?: 0.0) + sale.grandTotal
                    }
                } catch (_: Exception) {
                    paymentTotals["Split"] = (paymentTotals["Split"] ?: 0.0) + sale.grandTotal
                }
            } else if (sale.paymentMethod.equals("Due", ignoreCase = true) && sale.amountPaid > 0.0) {
                val cashPart = minOf(sale.amountPaid, sale.grandTotal)
                val duePart = (sale.grandTotal - cashPart).coerceAtLeast(0.0)
                if (cashPart > 0.0) paymentTotals["Cash"] = (paymentTotals["Cash"] ?: 0.0) + cashPart
                if (duePart > 0.0) paymentTotals["Due"] = (paymentTotals["Due"] ?: 0.0) + duePart
            } else {
                val key = sale.paymentMethod.ifBlank { "Other" }
                paymentTotals[key] = (paymentTotals[key] ?: 0.0) + sale.grandTotal
            }
        }

        // 2. Deduct returns according to actual refund method, not original sale method
        for (ret in returns) {
            val originalSale = salesById[ret.saleId]
            val rMethod = ret.refundMethod.trim()

            // Deduct due reduction from Due
            if (ret.dueAdjustmentAmount > 0.0) {
                paymentTotals["Due"] = (paymentTotals["Due"] ?: 0.0) - ret.dueAdjustmentAmount
            }

            // Deduct store credit refund from Store Credit
            if (rMethod.equals("Store Credit", ignoreCase = true) || rMethod.equals("Store credit", ignoreCase = true)) {
                val scDeduct = if (ret.externalRefundAmount > 0.0) ret.externalRefundAmount else (ret.refundAmount - ret.dueAdjustmentAmount).coerceAtLeast(0.0)
                if (scDeduct > 0.0) {
                    paymentTotals["Store Credit"] = (paymentTotals["Store Credit"] ?: 0.0) - scDeduct
                }
            } else {
                val extAmount = ret.externalRefundAmount
                if (extAmount > 0.0) {
                    when {
                        rMethod.equals("Cash", ignoreCase = true) -> {
                            paymentTotals["Cash"] = (paymentTotals["Cash"] ?: 0.0) - extAmount
                        }
                    rMethod.startsWith("MFS", ignoreCase = true) ||
                    rMethod.startsWith("Card", ignoreCase = true) ||
                    rMethod.startsWith("bKash", ignoreCase = true) ||
                    rMethod.startsWith("Nagad", ignoreCase = true) ||
                    rMethod.startsWith("Rocket", ignoreCase = true) -> {
                        paymentTotals[rMethod] = (paymentTotals[rMethod] ?: 0.0) - extAmount
                    }
                    rMethod.equals("Original method", ignoreCase = true) -> {
                        if (originalSale != null) {
                            if (originalSale.paymentMethod.equals("Split", ignoreCase = true) && !originalSale.splitPaymentJson.isNullOrBlank()) {
                                try {
                                    val arr = JSONArray(originalSale.splitPaymentJson)
                                    val splitTotal = (0 until arr.length()).sumOf { arr.getJSONObject(it).optDouble("amount", 0.0) }
                                    if (splitTotal > 0.0) {
                                        for (i in 0 until arr.length()) {
                                            val obj = arr.getJSONObject(i)
                                            val m = obj.optString("method", "").ifBlank { "Other" }
                                            val amt = obj.optDouble("amount", 0.0)
                                            val share = extAmount * (amt / splitTotal)
                                            paymentTotals[m] = (paymentTotals[m] ?: 0.0) - share
                                        }
                                    } else {
                                        paymentTotals["Cash"] = (paymentTotals["Cash"] ?: 0.0) - extAmount
                                    }
                                } catch (_: Exception) {
                                    paymentTotals["Cash"] = (paymentTotals["Cash"] ?: 0.0) - extAmount
                                }
                            } else {
                                val origKey = originalSale.paymentMethod.ifBlank { "Cash" }
                                paymentTotals[origKey] = (paymentTotals[origKey] ?: 0.0) - extAmount
                            }
                        } else {
                            paymentTotals["Cash"] = (paymentTotals["Cash"] ?: 0.0) - extAmount
                        }
                    }
                    else -> {
                        paymentTotals[rMethod] = (paymentTotals[rMethod] ?: 0.0) - extAmount
                    }
                }
            }
        }
    }

        return paymentTotals
    }

    data class PureInvoiceProfitResult(
        val netRevenue: Double,
        val netCogs: Double,
        val netProfit: Double
    )

    fun calculateExTaxDiscount(
        discount: Double,
        isTaxInclusive: Boolean,
        taxRate: Double,
        taxableSubtotal: Double,
        actualTaxAmount: Double
    ): Double {
        if (!isTaxInclusive || taxRate <= 0.0) return discount
        val r = taxRate / 100.0
        val taxWithoutDiscount = taxableSubtotal * r / (1.0 + r)
        val taxShare = (taxWithoutDiscount - actualTaxAmount).coerceAtLeast(0.0)
        return (discount - taxShare).coerceAtLeast(0.0)
    }

    fun calculateInvoiceProfitSummary(
        subtotal: Double,
        grossCogs: Double,
        taxAmount: Double,
        discount: Double,
        loyaltyDiscount: Double,
        grandTotal: Double,
        refundAmount: Double = 0.0,
        returnedCost: Double = 0.0,
        isTaxInclusive: Boolean = false
    ): PureInvoiceProfitResult {
        val totalDiscounts = discount + loyaltyDiscount
        val retTaxPortion = if (grandTotal > 0.0 && taxAmount > 0.0) {
            (refundAmount / grandTotal) * taxAmount
        } else 0.0
        val effectiveRefundRevenue = (refundAmount - retTaxPortion).coerceAtLeast(0.0)
        val netRevBeforeRefund = if (isTaxInclusive) {
            (grandTotal + loyaltyDiscount - taxAmount).coerceAtLeast(0.0)
        } else {
            (subtotal - totalDiscounts).coerceAtLeast(0.0)
        }
        val netRev = (netRevBeforeRefund - effectiveRefundRevenue).coerceAtLeast(0.0)
        val netCogs = (grossCogs - returnedCost).coerceAtLeast(0.0)
        val netProfit = netRev - netCogs
        return PureInvoiceProfitResult(
            netRevenue = netRev,
            netCogs = netCogs,
            netProfit = netProfit
        )
    }

    fun computeRemainingAfterStoreCredit(grandTotal: Double, storeCreditApplied: Double): Double {
        val sc = storeCreditApplied.coerceAtMost(grandTotal)
        return (grandTotal - sc).coerceAtLeast(0.0)
    }

    fun resetDiscountOnUnitToggle(): Double = 0.0

    fun resolveStoreCreditOnPhoneChange(oldPhone: String, newPhone: String, currentCredit: Double, hasRedeemedVoucher: Boolean): Double {
        return if (newPhone != oldPhone && !hasRedeemedVoucher) 0.0 else currentCredit
    }

    fun computeEffectiveStoreCredit(requestedCredit: Double, grandTotal: Double): Double {
        return requestedCredit.coerceAtMost(grandTotal)
    }

    fun computeVoucherDeduction(effectiveStoreCredit: Double, voucherRemainingBalance: Double): Double {
        return effectiveStoreCredit.coerceAtMost(voucherRemainingBalance)
    }

    fun computePostSaleVoucherBalance(voucherRemainingBalance: Double, voucherDeduction: Double): Double {
        return (voucherRemainingBalance - voucherDeduction).coerceAtLeast(0.0)
    }

    fun canSubmitSupplierPayment(isSaving: Boolean): Boolean = !isSaving

    data class DueChargeReversalResult(
        val newOutstanding: Double,
        val storeCreditExcess: Double
    )

    fun computeDueChargeReversal(currentOutstanding: Double, chargeAmount: Double): DueChargeReversalResult {
        val rawOutstanding = currentOutstanding - chargeAmount
        val excess = if (rawOutstanding < 0.0) -rawOutstanding else 0.0
        return DueChargeReversalResult(
            newOutstanding = rawOutstanding.coerceAtLeast(0.0),
            storeCreditExcess = excess
        )
    }

    fun validateCustomerPhoneUniqueness(isEdit: Boolean, existingCustomer: com.example.techyfipos.data.db.Customer?): String? {
        return if (!isEdit && existingCustomer != null) existingCustomer.name else null
    }

    fun filterReturnsByAllowedSaleIds(returns: List<com.example.techyfipos.data.db.Return>, allowedSaleIds: Set<Long>?): List<com.example.techyfipos.data.db.Return> {
        return if (allowedSaleIds != null) returns.filter { it.saleId in allowedSaleIds } else returns
    }

    fun computeSupplierInvoiceVoidDueReduction(invoiceDue: Double, currentSupplierDue: Double): Double {
        return minOf(invoiceDue, maxOf(0.0, currentSupplierDue))
    }

    fun computeBulkUpdatedPrices(
        currentSellingPrice: Double,
        currentDiscountedPrice: Double,
        percentDelta: Double? = null,
        fixedDelta: Double? = null
    ): Pair<Double, Double> {
        var newPrice = currentSellingPrice
        if (percentDelta != null) {
            newPrice = (newPrice * (1.0 + percentDelta / 100.0)).coerceAtLeast(0.0)
        } else if (fixedDelta != null) {
            newPrice = (newPrice + fixedDelta).coerceAtLeast(0.0)
        }
        val rounded = kotlin.math.round(newPrice * 100.0) / 100.0
        val newDiscounted = if (currentDiscountedPrice > 0.0) rounded else 0.0
        return Pair(rounded, newDiscounted)
    }
}
