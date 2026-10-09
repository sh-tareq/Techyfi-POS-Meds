package com.example.techyfipos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.R
import androidx.compose.ui.graphics.luminance
import com.example.techyfipos.ui.viewmodel.SaleReturnSummary
import com.example.techyfipos.utils.BillData
import com.example.techyfipos.utils.DateFormatters
import com.example.techyfipos.utils.formatTaka
import java.util.Date
import java.util.Locale

/**
 * Executive Owner Profit Analytics Content rendered inside ReceiptSummaryDialog
 * when isOwner == true. Includes Return & Refund Audit, 6 KPI cards,
 * Revenue vs Cost visual split bar, and Itemized Profit Table with Top Contributor badge.
 */
@Composable
fun OwnerProfitAnalyticsContent(
    bill: BillData,
    returnSummary: SaleReturnSummary? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Calculate line profits and metrics
    val itemsWithProfit = remember(bill, returnSummary) {
        bill.items.map { item ->
            val cost = item.purchasePrice ?: (item.unitPrice * 0.75) // fallback to 75% if unset
            val lineRev = item.lineTotal
            val lineC = cost * item.quantity
            val lineP = lineRev - lineC
            val lineMargin = if (lineRev > 0) (lineP / lineRev) * 100.0 else 0.0
            Triple(item, cost to lineC, lineP to lineMargin)
        }
    }

    val maxProfit = remember(itemsWithProfit) {
        itemsWithProfit.maxOfOrNull { it.third.first } ?: 0.0
    }

    val grossCogs = remember(itemsWithProfit) {
        itemsWithProfit.sumOf { it.second.second }
    }
    val grossRevenue = bill.subtotal
    val discountVal = bill.discountValue
    val netRevBeforeRefund = (grossRevenue - discountVal).coerceAtLeast(0.0)

    val refundAmount = returnSummary?.totalRefundAmount ?: bill.returnRefundAmount
    val hasReturns = bill.hasReturns || (returnSummary?.hasReturns == true) || refundAmount > 0.0

    // Reverse COGS for returned items if available
    val returnCogsReversal = remember(returnSummary, bill) {
        val retItems = returnSummary?.returnItems ?: emptyList()
        if (retItems.isNotEmpty()) {
            retItems.sumOf { ret ->
                val orig = bill.items.find { it.name.equals(ret.productName, ignoreCase = true) }
                val cost = orig?.purchasePrice ?: (ret.price * 0.75)
                cost * ret.quantity
            }
        } else if (refundAmount > 0.0) {
            // Proportional estimate if line items not loaded
            refundAmount * 0.75
        } else 0.0
    }

    val netRev = (netRevBeforeRefund - refundAmount).coerceAtLeast(0.0)
    val netCogs = (grossCogs - returnCogsReversal).coerceAtLeast(0.0)
    val netProfit = netRev - netCogs
    val netMargin = if (netRev > 0) (netProfit / netRev) * 100.0 else 0.0
    val markup = if (netCogs > 0) (netProfit / netCogs) * 100.0 else 0.0

    val originalProfit = netRevBeforeRefund - grossCogs

    val costPercent = if (netRev > 0) ((netCogs / netRev) * 100.0).coerceIn(0.0, 100.0).toInt() else 0
    val profitPercent = (100 - costPercent).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // A. Header Hero Banner with 3D Profit Vault Icon
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Realistic3DProfitVaultIcon(size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.invoice_tab_profit_analytics),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFDE68A)
                    )
                    Text(
                        text = stringResource(R.string.owner_analytics_invoice_sku_fmt, bill.saleId, bill.items.size),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (netProfit >= 0) Color(0xFF065F46) else Color(0xFF7F1D1D),
                    border = BorderStroke(1.dp, if (netProfit >= 0) Color(0xFF34D399) else Color(0xFFF87171))
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", netMargin)}% Margin",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (netProfit >= 0) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // B. Return & Refund Audit Card (Rendered if invoice processed returns)
        if (hasReturns) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else Color(0xFFFEF2F2)
                ),
                border = BorderStroke(1.5.dp, if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else Color(0xFFFECACA)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Realistic3DReturnOrbIcon(size = 18.dp, animated = true)
                            Text(
                                text = stringResource(R.string.invoice_return_audit_title),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) MaterialTheme.colorScheme.error else Color(0xFFB91C1C)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF991B1B)
                        ) {
                            Text(
                                text = "-${formatTaka(refundAmount)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFFCA5A5).copy(alpha = 0.5f), thickness = 0.8.dp)

                    // Details: Processed By, Method, Note
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.invoice_return_by_label),
                                fontSize = 10.sp,
                                color = if (isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF7F1D1D)
                            )
                            Text(
                                text = returnSummary?.performedByName?.takeIf { it.isNotBlank() } ?: "Staff",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF450A0A)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.invoice_return_refund_method),
                                fontSize = 10.sp,
                                color = if (isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF7F1D1D)
                            )
                            Text(
                                text = returnSummary?.refundMethod ?: "Refunded",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF450A0A)
                            )
                        }
                    }

                    if (returnSummary?.latestReturnTimestamp != null) {
                        Text(
                            text = "${stringResource(R.string.invoice_return_date_label)}: ${DateFormatters.formatDateTime(returnSummary.latestReturnTimestamp)}",
                            fontSize = 10.5.sp,
                            color = if (isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF991B1B)
                        )
                    }

                    // Returned Line Items
                    val retItems = returnSummary?.returnItems ?: emptyList()
                    if (retItems.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isDark) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.invoice_return_items_list_label),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) MaterialTheme.colorScheme.error else Color(0xFF991B1B)
                            )
                            retItems.forEach { rItem ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val qStr = if (rItem.quantity % 1.0 == 0.0) rItem.quantity.toLong().toString() else String.format(Locale.US, "%.1f", rItem.quantity)
                                    Text(
                                        text = "${rItem.productName} (x$qStr)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "-${formatTaka(rItem.price * rItem.quantity)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) MaterialTheme.colorScheme.error else Color(0xFFB91C1C)
                                    )
                                }
                            }
                        }
                    }

                    // Profit Adjustment Summary (Original vs Post-Return)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDark) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else Color(0xFFFEE2E2),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.invoice_original_profit_label),
                                fontSize = 10.sp,
                                color = if (isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF991B1B)
                            )
                            Text(
                                text = formatTaka(originalProfit),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.LineThrough,
                                color = if (isDark) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF991B1B)
                            )
                        }
                        Text("➔", fontSize = 14.sp, color = if (isDark) MaterialTheme.colorScheme.error else Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.invoice_net_adjusted_profit_label),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                            )
                            Text(
                                text = "+${formatTaka(netProfit)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFF10B981) else Color(0xFF047857)
                            )
                        }
                    }
                }
            }
        }

        // C. 6 Core Financial KPI Cards (2 Columns x 3 Rows)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. Net Profit Card
                KpiMetricCard(
                    title = stringResource(R.string.invoice_net_profit_title),
                    value = "${if (netProfit >= 0) "+" else ""}${formatTaka(netProfit)}",
                    sub = "${String.format(Locale.US, "%.1f", netMargin)}% Margin",
                    icon = {
                        if (netProfit >= 0) Realistic3DHighMarginGemIcon(size = 18.dp)
                        else Realistic3DLossPrismIcon(size = 18.dp)
                    },
                    containerColor = if (netProfit >= 0) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                    borderColor = if (netProfit >= 0) Color(0xFFA7F3D0) else Color(0xFFFECACA),
                    valueColor = if (netProfit >= 0) Color(0xFF047857) else Color(0xFFB91C1C),
                    modifier = Modifier.weight(1f)
                )
                // 2. Net Margin % Card
                KpiMetricCard(
                    title = stringResource(R.string.invoice_profit_margin_label),
                    value = "${String.format(Locale.US, "%.1f", netMargin)}%",
                    sub = if (netMargin >= 20) "Healthy margin" else "Low margin",
                    icon = { Realistic3DHighMarginGemIcon(size = 18.dp) },
                    containerColor = Color(0xFFEFF6FF),
                    borderColor = Color(0xFFBFDBFE),
                    valueColor = Color(0xFF1D4ED8),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 3. Wholesale Cost (COGS)
                KpiMetricCard(
                    title = stringResource(R.string.invoice_total_cogs_label),
                    value = formatTaka(netCogs),
                    sub = stringResource(R.string.invoice_purchase_cost_sub),
                    icon = { Realistic3DGoldTakaCoinIcon(size = 18.dp) },
                    containerColor = Color(0xFFFFFBEB),
                    borderColor = Color(0xFFFDE68A),
                    valueColor = Color(0xFFB45309),
                    modifier = Modifier.weight(1f)
                )
                // 4. Net Revenue
                KpiMetricCard(
                    title = stringResource(R.string.invoice_net_revenue_label),
                    value = formatTaka(netRev),
                    sub = if (discountVal > 0) "After ৳${discountVal.toInt()} disc." else "Full price sale",
                    icon = { Realistic3DGoldTakaCoinIcon(size = 18.dp) },
                    containerColor = Color(0xFFFAF5FF),
                    borderColor = Color(0xFFE9D5FF),
                    valueColor = Color(0xFF7E22CE),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 5. Markup %
                KpiMetricCard(
                    title = stringResource(R.string.invoice_markup_label),
                    value = "${String.format(Locale.US, "%.1f", markup)}%",
                    sub = "Price over wholesale",
                    icon = { Realistic3DProfitVaultIcon(size = 18.dp, animated = false) },
                    containerColor = Color(0xFFF0FDF4),
                    borderColor = Color(0xFFBBF7D0),
                    valueColor = Color(0xFF15803D),
                    modifier = Modifier.weight(1f)
                )
                // 6. Discount & Return Impact
                val totalDeductions = discountVal + refundAmount
                KpiMetricCard(
                    title = if (hasReturns) "Refund & Discount" else "Discount Given",
                    value = "-${formatTaka(totalDeductions)}",
                    sub = if (totalDeductions > 0) "Deducted from gross" else "No discounts given",
                    icon = {
                        if (hasReturns) Realistic3DReturnOrbIcon(size = 18.dp, animated = false)
                        else Text("🏷️", fontSize = 14.sp)
                    },
                    containerColor = Color(0xFFFFF1F2),
                    borderColor = Color(0xFFFECDD3),
                    valueColor = Color(0xFFBE123C),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // D. Visual Revenue vs Cost Proportional Split Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.invoice_cost_share_label, costPercent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB45309)
                )
                Text(
                    text = stringResource(R.string.invoice_profit_share_label, profitPercent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF047857)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(costPercent / 100f)
                            .height(10.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                            )
                    )
                }
            }
        }

        // E. Itemized Product Profit Breakdown Table
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.invoice_itemized_breakdown_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            itemsWithProfit.forEach { (item, costPair, profitPair) ->
                val (unitCost, lineCost) = costPair
                val (lineProfit, lineMargin) = profitPair
                val isTopContributor = maxProfit > 0 && lineProfit == maxProfit

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isTopContributor) (if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFFFBEB)) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isTopContributor) 1.5.dp else 1.dp,
                        if (isTopContributor) (if (isDark) Color(0xFFD97706) else Color(0xFFFDE68A)) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = item.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isTopContributor) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isDark) MaterialTheme.colorScheme.secondaryContainer else Color(0xFFFEF3C7),
                                        border = BorderStroke(0.6.dp, if (isDark) MaterialTheme.colorScheme.secondary else Color(0xFFF59E0B))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Realistic3DTopContributorStarIcon(size = 10.dp)
                                            Text(
                                                text = stringResource(R.string.invoice_top_contributor_label),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) MaterialTheme.colorScheme.onSecondaryContainer else Color(0xFF92400E)
                                            )
                                        }
                                    }
                                }
                            }

                            val profitSign = if (lineProfit >= 0) "+" else ""
                            Text(
                                text = "$profitSign${formatTaka(lineProfit)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (lineProfit >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val qStr = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.1f", item.quantity)
                            Text(
                                text = stringResource(R.string.owner_analytics_qty_sale_cost_fmt, qStr, formatTaka(item.unitPrice), formatTaka(unitCost)),
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "${String.format(Locale.US, "%.1f", lineMargin)}% margin",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lineMargin >= 20) Color(0xFF059669) else Color(0xFFB45309)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    sub: String,
    icon: @Composable () -> Unit,
    containerColor: Color,
    borderColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val effectiveBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else containerColor
    val effectiveBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else borderColor

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = effectiveBg,
        border = BorderStroke(1.dp, effectiveBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                icon()
            }
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor
            )
            Text(
                text = sub,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
