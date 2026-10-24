package com.techyfiposmeds.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.techyfiposmeds.R
import com.techyfiposmeds.theme.LocalDarkTheme
import com.techyfiposmeds.utils.formatTaka
import com.techyfiposmeds.utils.isAppBangla
import com.techyfiposmeds.utils.toBanglaDigits

/**
 * Realistic 3D Isometric Package Icon with dynamic gradients,
 * packing tape strip, top highlight, ambient shadow, and gentle floating animation.
 */
@Composable
fun Realistic3DIsometricPackageIcon(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_isometric_float")
    val rotY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationY = rotY
                translationY = offsetY
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Center origin
            val cx = w / 2f
            val topY = h * 0.10f
            val midY = h * 0.46f
            val botY = h * 0.88f
            val leftX = w * 0.12f
            val rightX = w * 0.88f
            val leftMidY = h * 0.28f
            val rightMidY = h * 0.28f
            val leftBotY = h * 0.70f
            val rightBotY = h * 0.70f

            // Ground ambient drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33000000), Color.Transparent),
                    center = Offset(cx, h * 0.94f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.08f, h * 0.88f),
                size = Size(w * 0.84f, h * 0.14f)
            )

            // 1. Top Face (Isometric rhombus)
            val topPath = Path().apply {
                moveTo(cx, topY)
                lineTo(rightX, rightMidY)
                lineTo(cx, midY)
                lineTo(leftX, leftMidY)
                close()
            }
            drawPath(
                path = topPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF93C5FD), Color(0xFF60A5FA)),
                    start = Offset(leftX, topY),
                    end = Offset(rightX, midY)
                )
            )

            // 2. Left Face
            val leftPath = Path().apply {
                moveTo(leftX, leftMidY)
                lineTo(cx, midY)
                lineTo(cx, botY)
                lineTo(leftX, leftBotY)
                close()
            }
            drawPath(
                path = leftPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
                    start = Offset(leftX, leftMidY),
                    end = Offset(cx, botY)
                )
            )

            // 3. Right Face
            val rightPath = Path().apply {
                moveTo(cx, midY)
                lineTo(rightX, rightMidY)
                lineTo(rightX, rightBotY)
                lineTo(cx, botY)
                close()
            }
            drawPath(
                path = rightPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB)),
                    start = Offset(cx, midY),
                    end = Offset(rightX, botY)
                )
            )

            // 4. Packaging Tape Band (Gold/Amber)
            val tapeTopPath = Path().apply {
                val tOffset = (rightX - cx) * 0.22f
                moveTo(cx - tOffset, (topY + leftMidY) / 2f)
                lineTo(cx + tOffset, (topY + rightMidY) / 2f)
                lineTo(cx + tOffset, (midY + rightMidY) / 2f)
                lineTo(cx - tOffset, (midY + leftMidY) / 2f)
                close()
            }
            drawPath(
                path = tapeTopPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
                    start = Offset(cx - 10f, topY),
                    end = Offset(cx + 10f, midY)
                )
            )

            // Left Tape side
            val tapeLeftPath = Path().apply {
                val tOffset = (cx - leftX) * 0.22f
                moveTo(cx - tOffset, (midY + leftMidY) / 2f)
                lineTo(cx, midY)
                lineTo(cx, botY)
                lineTo(cx - tOffset, (botY + leftBotY) / 2f)
                close()
            }
            drawPath(
                path = tapeLeftPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFFB45309)),
                    start = Offset(cx - 10f, midY),
                    end = Offset(cx, botY)
                )
            )

            // 5. Specular Highlights & Border bevels
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(leftX, leftMidY),
                end = Offset(cx, topY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(cx, topY),
                end = Offset(rightX, rightMidY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(cx, midY),
                end = Offset(cx, botY),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

/**
 * Realistic 3D Calendar Icon with glossy paper layer, crimson header,
 * chrome binder rings, date grid dots, and floating animation.
 */
@Composable
fun Realistic3DCalendarIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "calendarAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "calFloat"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val pad = s * 0.1f
            val w = s - pad * 2
            val h = s - pad * 2
            val r = s * 0.16f

            // 1. Ambient Drop Shadow
            drawRoundRect(
                color = Color(0x33000000),
                topLeft = Offset(pad, pad + s * 0.08f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )

            // 2. Paper Body (White with slight warm gradient)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9)),
                    startY = pad,
                    endY = pad + h
                ),
                topLeft = Offset(pad, pad),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )

            // 3. Header Bar (Crimson/Red)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                ),
                topLeft = Offset(pad, pad),
                size = Size(w, h * 0.32f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
            )

            // 4. Chrome Binder Rings
            drawCircle(
                color = Color(0xFF64748B),
                center = Offset(pad + w * 0.28f, pad + s * 0.05f),
                radius = s * 0.045f
            )
            drawCircle(
                color = Color(0xFF64748B),
                center = Offset(pad + w * 0.72f, pad + s * 0.05f),
                radius = s * 0.045f
            )

            // 5. Date Grid Dots
            val dotRadius = s * 0.038f
            for (row in 0..1) {
                for (col in 0..2) {
                    drawCircle(
                        color = Color(0xFF6366F1),
                        center = Offset(
                            pad + w * 0.26f + col * w * 0.24f,
                            pad + h * 0.54f + row * h * 0.22f
                        ),
                        radius = dotRadius
                    )
                }
            }
        }
    }
}

/**
 * Pulsing glowing status dot (radar animation) for stock health.
 */
@Composable
fun PulsingHealthDot(
    color: Color = Color(0xFF10B981),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_health")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radar ring
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .background(color, CircleShape)
        )
        // Center solid dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
    }
}

/**
 * Stock Status 3D Pill Badge with health status and pulsing dot.
 */
@Composable
fun StockStatus3DBadge(
    quantity: Double,
    reorderLevel: Int,
    modifier: Modifier = Modifier
) {
    val isOutOfStock = quantity <= 0.0
    val isLowStock = !isOutOfStock && quantity <= reorderLevel
    val isDark = isSystemInDarkTheme() || LocalDarkTheme.current

    val (bgColor, borderColor, textColor, dotColor, label) = when {
        isOutOfStock -> Tuple5(
            if (isDark) Color(0xFF450A0A) else Color(0xFFFEF2F2),
            if (isDark) Color(0xFF991B1B) else Color(0xFFFECACA),
            if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
            Color(0xFFEF4444),
            stringResource(R.string.out_of_stock)
        )
        isLowStock -> Tuple5(
            if (isDark) Color(0xFF451A03) else Color(0xFFFFFBEB),
            if (isDark) Color(0xFF92400E) else Color(0xFFFDE68A),
            if (isDark) Color(0xFFFCD34D) else Color(0xFFD97706),
            Color(0xFFF59E0B),
            stringResource(R.string.low_stock)
        )
        else -> Tuple5(
            if (isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
            if (isDark) Color(0xFF047857) else Color(0xFFA7F3D0),
            if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
            Color(0xFF10B981),
            stringResource(R.string.in_stock)
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulsingHealthDot(color = dotColor)
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val a: A, val b: B, val c: C, val d: D, val e: E
)

/**
 * Financial Intelligence Card showing Purchase, Selling, Profit Margin and Asset Valuation.
 */
@Composable
fun FinancialMarginCard(
    purchasePrice: Double,
    sellingPrice: Double,
    quantity: Double,
    mrpPrice: Double = 0.0,
    wholesalePrice: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val profit = sellingPrice - purchasePrice
    val marginPercent = if (purchasePrice > 0) {
        ((profit / purchasePrice) * 100).toInt()
    } else 0
    val totalAssetVal = (quantity * purchasePrice).coerceAtLeast(0.0)
    val isBn = isAppBangla()
    val marginDisplay = if (isBn) toBanglaDigits(marginPercent.toString()) else marginPercent.toString()
    val rawQtyDisplay = if (quantity % 1.0 == 0.0) "${quantity.toInt()}" else "$quantity"
    val qtyDisplay = if (isBn) toBanglaDigits(rawQtyDisplay) else rawQtyDisplay

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 4-Tier Price Grid: Purchase, Selling, MRP, Wholesale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Purchase
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.stock_check_purchase),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatTaka(purchasePrice),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )

                // 2. Selling
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.stock_check_selling),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatTaka(sellingPrice),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFF059669),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )

                // 3. MRP
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.stock_check_mrp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (mrpPrice > 0.0) formatTaka(mrpPrice) else "-",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (mrpPrice > 0.0) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )

                // 4. Wholesale
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.stock_check_wholesale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (wholesalePrice > 0.0) formatTaka(wholesalePrice) else "-",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (wholesalePrice > 0.0) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                thickness = 0.8.dp
            )

            // Performance & Valuation Row: Unit Margin and Total Asset Worth
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unit Margin Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.stock_check_unit_margin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (profit >= 0) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, if (profit >= 0) Color(0xFFA7F3D0) else Color(0xFFFECACA))
                    ) {
                        Text(
                            text = "${if (profit >= 0) "+" else ""}${formatTaka(profit)} (${marginDisplay}%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (profit >= 0) Color(0xFF065F46) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Total Asset Worth
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.stock_check_asset_worth, qtyDisplay),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        formatTaka(totalAssetVal),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4338CA)
                    )
                }
            }
        }
    }
}

/**
 * Delta change indicator pill (e.g. "+5 (51 → 56)").
 */
@Composable
fun StockDeltaPill(
    initialQty: Double,
    currentQty: Double,
    modifier: Modifier = Modifier
) {
    val delta = currentQty - initialQty
    val initStr = if (initialQty % 1.0 == 0.0) initialQty.toLong().toString() else String.format(java.util.Locale.US, "%.2f", initialQty).trimEnd('0').trimEnd('.')
    val currStr = if (currentQty % 1.0 == 0.0) currentQty.toLong().toString() else String.format(java.util.Locale.US, "%.2f", currentQty).trimEnd('0').trimEnd('.')
    val deltaStr = if (delta % 1.0 == 0.0) delta.toLong().toString() else String.format(java.util.Locale.US, "%.2f", delta).trimEnd('0').trimEnd('.')
    val (bgColor, textColor, label) = when {
        kotlin.math.abs(delta) < 0.0001 -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "No change")
        delta > 0 -> Triple(Color(0xFFECFDF5), Color(0xFF059669), "+$deltaStr ($initStr → $currStr)")
        else -> Triple(Color(0xFFFEF2F2), Color(0xFFDC2626), "$deltaStr ($initStr → $currStr)")
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/**
 * Quick batch increment chips (+0.5, +1, +5, +10, Zero, Reset).
 */
@Composable
fun QuickStepperChipsRow(
    onIncrement: (Double) -> Unit,
    onZero: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chipModifier = Modifier
            .weight(1f)
            .height(28.dp)

        // +1
        Surface(
            onClick = { onIncrement(1.0) },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = chipModifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+1", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // +5
        Surface(
            onClick = { onIncrement(5.0) },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = chipModifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+5", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // +10
        Surface(
            onClick = { onIncrement(10.0) },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = chipModifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+10", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Set to 0
        Surface(
            onClick = onZero,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFEE2E2),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
            modifier = chipModifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("0", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFDC2626))
            }
        }

        // Reset to Initial
        Surface(
            onClick = onReset,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = chipModifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.reset), fontWeight = FontWeight.Medium, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Realistic 3D Animated Cancel Icon (Crimson/Rose with beveled depth and floating animation).
 */
@Composable
fun Realistic3DCancelIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_cancel_anim")
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cancelRotZ"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cancelScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotZ
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = (w.coerceAtMost(h) / 2f) * 0.9f

            // 1. Ambient Ground Shadow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44BE123C), Color.Transparent),
                    center = Offset(cx, cy + r * 0.3f),
                    radius = r * 1.1f
                ),
                center = Offset(cx, cy + r * 0.2f),
                radius = r * 1.05f
            )

            // 2. Beveled Base (Dark Ruby)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF9F1239), Color(0xFF881337)),
                    startY = cy - r,
                    endY = cy + r
                ),
                center = Offset(cx, cy + 1.5f),
                radius = r
            )

            // 3. Top Face Gradient (Bright Crimson to Rose)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF43F5E), Color(0xFFE11D48)),
                    start = Offset(cx - r * 0.7f, cy - r * 0.7f),
                    end = Offset(cx + r * 0.7f, cy + r * 0.7f)
                ),
                center = Offset(cx, cy - 0.5f),
                radius = r * 0.92f
            )

            // 4. Specular Highlight Arc
            drawArc(
                color = Color.White.copy(alpha = 0.7f),
                startAngle = 200f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.75f, cy - r * 0.8f),
                size = Size(r * 1.5f, r * 1.5f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 5. White Cross (X)
            val arm = r * 0.45f
            drawLine(
                color = Color.White,
                start = Offset(cx - arm, cy - arm - 0.5f),
                end = Offset(cx + arm, cy + arm - 0.5f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(cx + arm, cy - arm - 0.5f),
                end = Offset(cx - arm, cy + arm - 0.5f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Realistic 3D Animated Edit Icon (Indigo/Purple with golden tip and floating tilt).
 */
@Composable
fun Realistic3DEditIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_edit_anim")
    val tilt by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "editTilt"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "editFloat"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = tilt
                translationY = floatY
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = (w.coerceAtMost(h) / 2f) * 0.9f

            // 1. Ambient Drop Shadow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x444338CA), Color.Transparent),
                    center = Offset(cx, cy + r * 0.3f),
                    radius = r * 1.1f
                ),
                center = Offset(cx, cy + r * 0.2f),
                radius = r * 1.05f
            )

            // 2. Beveled Base (Deep Indigo)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF3730A3), Color(0xFF312E81)),
                    startY = cy - r,
                    endY = cy + r
                ),
                center = Offset(cx, cy + 1.5f),
                radius = r
            )

            // 3. Top Face (Electric Indigo/Blue)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5)),
                    start = Offset(cx - r * 0.7f, cy - r * 0.7f),
                    end = Offset(cx + r * 0.7f, cy + r * 0.7f)
                ),
                center = Offset(cx, cy - 0.5f),
                radius = r * 0.92f
            )

            // 4. Specular Highlight Arc
            drawArc(
                color = Color.White.copy(alpha = 0.75f),
                startAngle = 190f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.75f, cy - r * 0.8f),
                size = Size(r * 1.5f, r * 1.5f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 5. Stylized Pencil Icon in center (White + Gold Tip)
            val pPath = Path().apply {
                moveTo(cx - r * 0.35f, cy + r * 0.35f)
                lineTo(cx - r * 0.45f, cy + r * 0.45f)
                lineTo(cx - r * 0.2f, cy + r * 0.45f)
                close()
            }
            drawPath(path = pPath, color = Color(0xFFFDE68A))

            drawLine(
                color = Color.White,
                start = Offset(cx - r * 0.35f, cy + r * 0.35f),
                end = Offset(cx + r * 0.4f, cy - r * 0.4f),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Realistic 3D Animated Save Icon (Emerald/Teal with beveled depth and pulsing shine).
 */
@Composable
fun Realistic3DSaveIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_save_anim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "saveScale"
    )
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "saveShine"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = (w.coerceAtMost(h) / 2f) * 0.9f

            // 1. Ambient Drop Shadow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44064E3B), Color.Transparent),
                    center = Offset(cx, cy + r * 0.3f),
                    radius = r * 1.1f
                ),
                center = Offset(cx, cy + r * 0.2f),
                radius = r * 1.05f
            )

            // 2. Beveled Base (Deep Emerald)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF047857), Color(0xFF064E3B)),
                    startY = cy - r,
                    endY = cy + r
                ),
                center = Offset(cx, cy + 1.5f),
                radius = r
            )

            // 3. Top Face (Vibrant Emerald Gradient)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981)),
                    start = Offset(cx - r * 0.7f, cy - r * 0.7f),
                    end = Offset(cx + r * 0.7f, cy + r * 0.7f)
                ),
                center = Offset(cx, cy - 0.5f),
                radius = r * 0.92f
            )

            // 4. Specular Highlight Arc
            drawArc(
                color = Color.White.copy(alpha = 0.8f),
                startAngle = 190f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.75f, cy - r * 0.8f),
                size = Size(r * 1.5f, r * 1.5f),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
            )

            // 5. White Checkmark (✓) with crisp stroke
            val checkPath = Path().apply {
                moveTo(cx - r * 0.45f, cy - 0.5f)
                lineTo(cx - r * 0.1f, cy + r * 0.35f)
                lineTo(cx + r * 0.45f, cy - r * 0.3f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * Realistic 3D Canvas Asset/Vault Icon with metallic emerald/gold gradients,
 * beveled rim, radial ambient shadow, chrome dial, and floating micro-animation.
 */
@Composable
fun Realistic3DVaultIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vaultIconAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vaultFloat"
    )
    val shineSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vaultShine"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = floatY
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val r = (w.coerceAtMost(h) / 2f) * 0.92f

            // 1. Ambient Soft Shadow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55000000), Color.Transparent),
                    center = Offset(cx, cy + r * 0.35f),
                    radius = r * 1.1f
                ),
                center = Offset(cx, cy + r * 0.25f),
                radius = r * 1.05f
            )

            // 2. Beveled Dark Emerald Base
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF047857), Color(0xFF022C22)),
                    startY = cy - r,
                    endY = cy + r
                ),
                topLeft = Offset(cx - r, cy - r + 1.5f),
                size = Size(r * 2f, r * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.45f, r * 0.45f)
            )

            // 3. Vault Front Plate (Vibrant Emerald / Teal with subtle metallic sheen)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF047857)),
                    start = Offset(cx - r * 0.8f, cy - r * 0.8f),
                    end = Offset(cx + r * 0.8f, cy + r * 0.8f)
                ),
                topLeft = Offset(cx - r * 0.92f, cy - r * 0.92f),
                size = Size(r * 1.84f, r * 1.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.4f, r * 0.4f)
            )

            // 4. Inner Golden Dial Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFBBF24),
                        Color(0xFFF59E0B),
                        Color(0xFFFEF3C7),
                        Color(0xFFD97706),
                        Color(0xFFFBBF24)
                    ),
                    center = Offset(cx, cy)
                ),
                center = Offset(cx, cy),
                radius = r * 0.52f,
                style = Stroke(width = 1.6.dp.toPx())
            )

            // 5. Central Chrome Dial Button
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF3C7), Color(0xFFD97706)),
                    center = Offset(cx - r * 0.1f, cy - r * 0.1f),
                    radius = r * 0.35f
                ),
                center = Offset(cx, cy),
                radius = r * 0.32f
            )

            // 6. Specular Highlight Arc on Top Corner
            drawArc(
                color = Color.White.copy(alpha = 0.85f),
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.8f, cy - r * 0.8f),
                size = Size(r * 1.6f, r * 1.6f),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}



/**
 * Realistic 3D Cash Banknote Stack Icon with multi-layered emerald notes,
 * radial glow, and central oval seal.
 */
@Composable
fun Realistic3DCashIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val pad = s * 0.08f
        val w = s - pad * 2
        val h = s * 0.58f
        val cy = s / 2f

        // 1. Bottom Note (Dark Shadow note)
        drawRoundRect(
            color = Color(0xFF065F46),
            topLeft = Offset(pad + s * 0.06f, cy - h / 2f + s * 0.08f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // 2. Middle Note
        drawRoundRect(
            color = Color(0xFF059669),
            topLeft = Offset(pad + s * 0.03f, cy - h / 2f + s * 0.04f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // 3. Top Glossy Note
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF047857)),
                start = Offset(pad, cy - h / 2f),
                end = Offset(pad + w, cy + h / 2f)
            ),
            topLeft = Offset(pad, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // 4. White / Mint Inner Border
        drawRoundRect(
            color = Color(0xFFA7F3D0),
            topLeft = Offset(pad, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 0.8.dp.toPx())
        )

        // 5. Central Oval Seal
        drawOval(
            color = Color(0xFFECFDF5),
            topLeft = Offset(s / 2f - w * 0.16f, cy - h * 0.22f),
            size = Size(w * 0.32f, h * 0.44f)
        )
    }
}

/**
 * Realistic 3D Metallic Credit Card Icon with gold EMV chip.
 */
@Composable
fun Realistic3DCardIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val pad = s * 0.08f
        val w = s - pad * 2
        val h = s * 0.62f
        val cy = s / 2f

        // 1. Drop shadow
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(pad, cy - h / 2f + s * 0.06f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // 2. Card Body (Metallic Royal Blue)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1E40AF)),
                start = Offset(pad, cy - h / 2f),
                end = Offset(pad + w, cy + h / 2f)
            ),
            topLeft = Offset(pad, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // 3. Gold EMV Chip
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B)),
                start = Offset(pad + s * 0.1f, cy - h * 0.15f),
                end = Offset(pad + s * 0.28f, cy + h * 0.2f)
            ),
            topLeft = Offset(pad + s * 0.1f, cy - h * 0.15f),
            size = Size(w * 0.24f, h * 0.32f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
    }
}

/**
 * Realistic 3D Mobile Wallet Icon (bKash magenta / Nagad orange).
 */
@Composable
fun Realistic3DMobileWalletIcon(
    primaryColor: Color = Color(0xFFBE185D),
    secondaryColor: Color = Color(0xFFDB2777),
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val pad = s * 0.12f
        val w = s * 0.52f
        val h = s - pad * 2
        val cx = s / 2f
        val cy = s / 2f

        // 1. Phone body
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(secondaryColor, primaryColor)
            ),
            topLeft = Offset(cx - w / 2f, pad),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )

        // 2. Screen Glass
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(cx - w * 0.36f, pad + h * 0.16f),
            size = Size(w * 0.72f, h * 0.65f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )

        // 3. Home Indicator / Notch
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            center = Offset(cx, pad + h * 0.9f),
            radius = s * 0.035f
        )
    }
}

/**
 * Realistic 3D Rising Analytics Icon with staggered bar bounce micro-animation.
 */
@Composable
fun Realistic3DAnalyticsIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "analyticsAnim")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barBounce"
    )

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val barW = s * 0.2f
        val bottom = s * 0.9f

        // Bar 1 (Short Indigo)
        val h1 = s * 0.35f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
                startY = bottom - h1,
                endY = bottom
            ),
            topLeft = Offset(s * 0.1f, bottom - h1),
            size = Size(barW, h1),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Bar 2 (Medium Sky)
        val h2 = s * 0.58f * bounce
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                startY = bottom - h2,
                endY = bottom
            ),
            topLeft = Offset(s * 0.4f, bottom - h2),
            size = Size(barW, h2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Bar 3 (Tall Emerald)
        val h3 = s * 0.82f
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF34D399), Color(0xFF059669)),
                startY = bottom - h3,
                endY = bottom
            ),
            topLeft = Offset(s * 0.7f, bottom - h3),
            size = Size(barW, h3),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

/**
 * Realistic 3D Filter Funnel Icon with glowing gradient.
 */
@Composable
fun Realistic3DFilterIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val path = Path().apply {
            moveTo(s * 0.12f, s * 0.18f)
            lineTo(s * 0.88f, s * 0.18f)
            lineTo(s * 0.6f, s * 0.54f)
            lineTo(s * 0.6f, s * 0.86f)
            lineTo(s * 0.4f, s * 0.76f)
            lineTo(s * 0.4f, s * 0.54f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
                start = Offset(0f, 0f),
                end = Offset(s, s)
            )
        )
    }
}

/**
 * Realistic 3D Empty Receipt Scroll with aura and bobbing animation.
 */
@Composable
fun Realistic3DEmptyReceiptIcon(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "receiptFloat")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Ambient Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x336366F1), Color(0x006366F1)),
                    center = Offset(cx, cy),
                    radius = s * 0.48f
                ),
                center = Offset(cx, cy),
                radius = s * 0.48f
            )

            // 2. Paper Scroll Body
            val w = s * 0.52f
            val h = s * 0.72f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFE2E8F0)),
                    start = Offset(cx - w / 2f, cy - h / 2f),
                    end = Offset(cx + w / 2f, cy + h / 2f)
                ),
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // 3. Border
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 4. Receipt Lines
            val lineY0 = cy - h * 0.28f
            val lineSpacing = h * 0.16f
            for (i in 0..3) {
                val lineW = if (i % 2 == 0) w * 0.65f else w * 0.45f
                drawRoundRect(
                    color = Color(0xFF6366F1),
                    topLeft = Offset(cx - w * 0.32f, lineY0 + i * lineSpacing),
                    size = Size(lineW, 2.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }
        }
    }
}

/**
 * Realistic 3D Gold Trophy Icon with specular highlights, handles, and pedestal base.
 */
@Composable
fun Realistic3DTrophyIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "trophyAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trophyFloat"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Pedestal Base
            val baseW = s * 0.54f
            val baseH = s * 0.18f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF78350F), Color(0xFF451A03)),
                    startY = s * 0.78f,
                    endY = s * 0.96f
                ),
                topLeft = Offset(cx - baseW / 2f, s * 0.78f),
                size = Size(baseW, baseH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // 2. Stem
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFB45309), Color(0xFFF59E0B), Color(0xFFFDE68A))
                ),
                topLeft = Offset(cx - s * 0.08f, s * 0.62f),
                size = Size(s * 0.16f, s * 0.18f)
            )

            // 3. Trophy Cup
            val cupW = s * 0.62f
            val cupH = s * 0.48f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFD97706)),
                    start = Offset(cx - cupW / 2f, s * 0.16f),
                    end = Offset(cx + cupW / 2f, s * 0.64f)
                ),
                topLeft = Offset(cx - cupW / 2f, s * 0.16f),
                size = Size(cupW, cupH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cupW * 0.35f, cupW * 0.35f)
            )

            // 4. Cup Lip/Rim
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFEF3C7), Color(0xFFFBBF24), Color(0xFFD97706))
                ),
                topLeft = Offset(cx - cupW * 0.58f, s * 0.12f),
                size = Size(cupW * 1.16f, s * 0.10f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

/**
 * Realistic 3D POS Thermal Receipt Printer Icon with paper feed and LED status.
 */
@Composable
fun Realistic3DPrinterIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "printerAnim")
    val feedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "printerFeed"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Printer Body (Dark Obsidian)
            val bodyW = s * 0.76f
            val bodyH = s * 0.58f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF1E293B)),
                    startY = cy - bodyH / 2f + s * 0.08f,
                    endY = cy + bodyH / 2f + s * 0.08f
                ),
                topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f + s * 0.08f),
                size = Size(bodyW, bodyH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // 2. Paper Feed Output (White receipt coming out from top slot)
            val paperW = bodyW * 0.62f
            val paperH = s * 0.32f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9)),
                    startY = cy - bodyH / 2f - paperH * 0.4f + feedOffset,
                    endY = cy - bodyH / 2f + s * 0.12f
                ),
                topLeft = Offset(cx - paperW / 2f, cy - bodyH / 2f - paperH * 0.4f + feedOffset),
                size = Size(paperW, paperH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // 3. Receipt slot line
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(cx - bodyW * 0.4f, cy - bodyH / 2f + s * 0.06f),
                size = Size(bodyW * 0.8f, s * 0.06f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )

            // 4. Green Status LED
            drawCircle(
                color = Color(0xFF10B981),
                center = Offset(cx + bodyW * 0.28f, cy + bodyH * 0.32f),
                radius = s * 0.045f
            )
        }
    }
}

/**
 * Realistic 3D Handshake & Credit Shield Icon for Dues & Credit Tracking.
 */
@Composable
fun Realistic3DHandshakeCreditIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "creditAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "creditFloat"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Shield Background
            val shieldW = s * 0.82f
            val shieldH = s * 0.82f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFECDD3), Color(0xFFF43F5E), Color(0xFF9F1239)),
                    start = Offset(cx - shieldW / 2f, cy - shieldH / 2f),
                    end = Offset(cx + shieldW / 2f, cy + shieldH / 2f)
                ),
                topLeft = Offset(cx - shieldW / 2f, cy - shieldH / 2f),
                size = Size(shieldW, shieldH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // 2. Inner Golden Seal
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B)),
                    start = Offset(cx - s * 0.22f, cy - s * 0.22f),
                    end = Offset(cx + s * 0.22f, cy + s * 0.22f)
                ),
                center = Offset(cx, cy),
                radius = s * 0.26f
            )

            // 3. Center Handshake / Taka node
            drawCircle(
                color = Color(0xFF881337),
                center = Offset(cx, cy),
                radius = s * 0.12f
            )
        }
    }
}

/**
 * Realistic 3D Phone Dialer Icon for direct customer calling.
 */
@Composable
fun Realistic3DPhoneDialerIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = null,
            tint = Color(0xFF059669),
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Realistic 3D Message Reminder Icon for WhatsApp & SMS.
 */
@Composable
fun Realistic3DMessageReminderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Message,
            contentDescription = null,
            tint = Color(0xFF4F46E5),
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Photorealistic 3D Animated Gold Reminder Bell with ambient emerald backdrop and swinging physics.
 */
@Composable
fun Realistic3DReminderBellIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bellAnim")
    val swingAngle by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bellSwing"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = swingAngle
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.15f)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Emerald Glow Ambient Backdrop Pill
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF047857)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.28f, w * 0.28f)
            )

            // Specular ambient shine on backdrop
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x66FFFFFF), Color(0x00FFFFFF)),
                    startY = 0f,
                    endY = h * 0.45f
                ),
                topLeft = Offset(w * 0.08f, h * 0.06f),
                size = Size(w * 0.84f, h * 0.40f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.20f, w * 0.20f)
            )

            // Bell Top Loop / Crown
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFCA8A04)),
                    start = Offset(w * 0.42f, h * 0.14f),
                    end = Offset(w * 0.58f, h * 0.26f)
                ),
                topLeft = Offset(w * 0.42f, h * 0.14f),
                size = Size(w * 0.16f, h * 0.14f)
            )
            drawOval(
                color = Color(0xFF047857),
                topLeft = Offset(w * 0.46f, h * 0.18f),
                size = Size(w * 0.08f, h * 0.06f)
            )

            // 3D Bell Body Path
            val bellPath = Path().apply {
                moveTo(w * 0.44f, h * 0.25f)
                cubicTo(w * 0.38f, h * 0.38f, w * 0.28f, h * 0.58f, w * 0.22f, h * 0.68f)
                cubicTo(w * 0.22f, h * 0.72f, w * 0.28f, h * 0.73f, w * 0.50f, h * 0.73f)
                cubicTo(w * 0.72f, h * 0.73f, w * 0.78f, h * 0.72f, w * 0.78f, h * 0.68f)
                cubicTo(w * 0.72f, h * 0.58f, w * 0.62f, h * 0.38f, w * 0.56f, h * 0.25f)
                close()
            }

            // Bell Metallic Gold Gradient
            drawPath(
                bellPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFEF9C3),
                        Color(0xFFFACC15),
                        Color(0xFFEAB308),
                        Color(0xFFCA8A04),
                        Color(0xFF854D0E)
                    ),
                    start = Offset(w * 0.25f, h * 0.25f),
                    end = Offset(w * 0.78f, h * 0.73f)
                )
            )

            // Bell Specular Curve Highlight
            val highlightPath = Path().apply {
                moveTo(w * 0.46f, h * 0.28f)
                cubicTo(w * 0.42f, h * 0.38f, w * 0.35f, h * 0.55f, w * 0.30f, h * 0.66f)
                lineTo(w * 0.35f, h * 0.67f)
                cubicTo(w * 0.40f, h * 0.55f, w * 0.47f, h * 0.38f, w * 0.50f, h * 0.28f)
                close()
            }
            drawPath(
                highlightPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0x99FFFFFF), Color(0x22FFFFFF)),
                    startY = h * 0.28f,
                    endY = h * 0.67f
                )
            )

            // Bell Rim / Bottom Lip
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFEAB308), Color(0xFF713F12)),
                    start = Offset(w * 0.20f, h * 0.66f),
                    end = Offset(w * 0.80f, h * 0.76f)
                ),
                topLeft = Offset(w * 0.20f, h * 0.66f),
                size = Size(w * 0.60f, h * 0.10f)
            )

            // Bell Clapper
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFA16207)),
                    center = Offset(w * 0.50f, h * 0.77f),
                    radius = w * 0.08f
                ),
                center = Offset(w * 0.50f, h * 0.77f),
                radius = w * 0.07f
            )
        }
    }
}


/**
 * Realistic 3D Cash Note / Collect Icon for payment collection button.
 */
@Composable
fun Realistic3DWalletCollectIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            // Banknote Body
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9))
                ),
                topLeft = Offset(0.5.dp.toPx(), h * 0.12f),
                size = Size(w - 1.dp.toPx(), h * 0.76f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            // Emerald Currency Seal Circle in Center
            drawCircle(
                color = Color(0xFF059669),
                radius = h * 0.22f,
                center = Offset(w / 2f, h / 2f)
            )
            // Left & Right Security Dots
            drawCircle(
                color = Color(0xFF059669),
                radius = h * 0.08f,
                center = Offset(w * 0.22f, h / 2f)
            )
            drawCircle(
                color = Color(0xFF059669),
                radius = h * 0.08f,
                center = Offset(w * 0.78f, h / 2f)
            )
        }
    }
}

/**
 * Realistic 3D Digital Khata (Ledger Book) Icon for Customer Ledger & Due Tracking.
 */
@Composable
fun Realistic3DDigitalKhataIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "khataAnim")
    val tilt by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "khataTilt"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = tilt },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Book Cover (Warm Amber / Saddle Leather)
            val bookW = s * 0.78f
            val bookH = s * 0.88f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFF78350F)),
                    start = Offset(cx - bookW / 2f, cy - bookH / 2f),
                    end = Offset(cx + bookW / 2f, cy + bookH / 2f)
                ),
                topLeft = Offset(cx - bookW / 2f, cy - bookH / 2f),
                size = Size(bookW, bookH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // 2. Spine Edge Shadow
            drawRoundRect(
                color = Color(0xFF451A03),
                topLeft = Offset(cx - bookW / 2f, cy - bookH / 2f),
                size = Size(bookW * 0.18f, bookH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // 3. Gold Emblem / Taka symbol node
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B)),
                    start = Offset(cx - s * 0.1f, cy - s * 0.1f),
                    end = Offset(cx + s * 0.1f, cy + s * 0.1f)
                ),
                center = Offset(cx + s * 0.05f, cy),
                radius = s * 0.18f
            )

            // 4. White Pages Edge
            drawRoundRect(
                color = Color(0xFFFFFBEB),
                topLeft = Offset(cx + bookW / 2f - 2.dp.toPx(), cy - bookH / 2f + 2.dp.toPx()),
                size = Size(2.dp.toPx(), bookH - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

/**
 * Photorealistic 3D Add Credit Khata Icon for Due/Credit entries.
 */
@Composable
fun Realistic3DAddCreditKhataIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "addCreditKhataAnim")
    val tilt by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "addCreditTilt"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = tilt },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Book Cover (Vibrant Amber/Gold Leather)
            val bookW = s * 0.8f
            val bookH = s * 0.88f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFF78350F)),
                    start = Offset(cx - bookW / 2f, cy - bookH / 2f),
                    end = Offset(cx + bookW / 2f, cy + bookH / 2f)
                ),
                topLeft = Offset(cx - bookW / 2f, cy - bookH / 2f),
                size = Size(bookW, bookH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // 2. Leather Spine
            drawRoundRect(
                color = Color(0xFF451A03),
                topLeft = Offset(cx - bookW / 2f, cy - bookH / 2f),
                size = Size(bookW * 0.18f, bookH),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // 3. Pages Edge
            drawRoundRect(
                color = Color(0xFFFEF3C7),
                topLeft = Offset(cx + bookW / 2f - 2.5.dp.toPx(), cy - bookH / 2f + 2.dp.toPx()),
                size = Size(2.5.dp.toPx(), bookH - 4.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )

            // 4. Center Gold Seal with '+' Credit Symbol
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    start = Offset(cx + s * 0.05f - s * 0.18f, cy - s * 0.18f),
                    end = Offset(cx + s * 0.05f + s * 0.18f, cy + s * 0.18f)
                ),
                center = Offset(cx + s * 0.05f, cy),
                radius = s * 0.20f
            )

            // '+' Plus Sign on Gold Seal
            val plusLen = s * 0.16f
            val plusCenter = Offset(cx + s * 0.05f, cy)
            drawLine(
                color = Color(0xFF78350F),
                start = Offset(plusCenter.x - plusLen / 2f, plusCenter.y),
                end = Offset(plusCenter.x + plusLen / 2f, plusCenter.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF78350F),
                start = Offset(plusCenter.x, plusCenter.y - plusLen / 2f),
                end = Offset(plusCenter.x, plusCenter.y + plusLen / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Photorealistic 3D Credit Limit Shield Icon.
 */
@Composable
fun Realistic3DCreditShieldIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            val path = Path().apply {
                moveTo(cx, s * 0.1f)
                lineTo(s * 0.85f, s * 0.22f)
                lineTo(s * 0.85f, s * 0.52f)
                cubicTo(s * 0.85f, s * 0.75f, cx, s * 0.92f, cx, s * 0.92f)
                cubicTo(cx, s * 0.92f, s * 0.15f, s * 0.75f, s * 0.15f, s * 0.52f)
                lineTo(s * 0.15f, s * 0.22f)
                close()
            }

            // Shield Gradient
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1)),
                    start = Offset(cx, 0f),
                    end = Offset(cx, s)
                )
            )

            // Inner Credit Card Node
            val cardW = s * 0.44f
            val cardH = s * 0.28f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - cardW / 2f, cy - cardH / 2f),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            // Magnetic strip
            drawRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(cx - cardW / 2f, cy - cardH / 4f),
                size = Size(cardW, cardH * 0.25f)
            )
        }
    }
}

/**
 * Realistic 3D Overdue / Credit Risk Alert Icon.
 */
@Composable
fun Realistic3DOverdueAlertIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "overdueAnim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "overduePulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Amber / Crimson Diamond Shield
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFCD34D), Color(0xFFF59E0B), Color(0xFFB45309)),
                    start = Offset(1.dp.toPx(), 1.dp.toPx()),
                    end = Offset(s - 1.dp.toPx(), s - 1.dp.toPx())
                ),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(s - 2.dp.toPx(), s - 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Inner Exclamation Center
            drawCircle(
                color = Color(0xFF78350F),
                center = Offset(cx, cy + s * 0.18f),
                radius = s * 0.08f
            )
            drawRoundRect(
                color = Color(0xFF78350F),
                topLeft = Offset(cx - s * 0.06f, cy - s * 0.25f),
                size = Size(s * 0.12f, s * 0.32f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}

/**
 * Realistic 3D Animated Gear / Cogwheel Icon for Settings Master Card.
 */
@Composable
fun Realistic3DGearIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gearAnim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gearRotate"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Outer teeth
            val teeth = 8
            val rOuter = s * 0.44f
            val rInner = s * 0.32f
            val path = androidx.compose.ui.graphics.Path()

            for (i in 0 until teeth * 2) {
                val angle = (i * Math.PI / teeth).toFloat()
                val r = if (i % 2 == 0) rOuter else rInner
                val x = cx + kotlin.math.cos(angle) * r
                val y = cy + kotlin.math.sin(angle) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFC7D2FE), Color(0xFF818CF8), Color(0xFF4F46E5)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                )
            )

            // Center hole
            drawCircle(
                color = Color(0xFF1E1B4B),
                center = Offset(cx, cy),
                radius = s * 0.16f
            )
        }
    }
}

/**
 * Realistic 3D Store / Shop Icon for Shop Identity.
 */
@Composable
fun Realistic3DStoreIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension

            // Background container
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(s - 4.dp.toPx(), s - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Awning roof
            val roofPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(s * 0.22f, s * 0.40f)
                lineTo(s * 0.78f, s * 0.40f)
                lineTo(s * 0.70f, s * 0.28f)
                lineTo(s * 0.30f, s * 0.28f)
                close()
            }
            drawPath(roofPath, Color.White)

            // Pillars
            drawRect(Color(0xFFE0F2FE), topLeft = Offset(s * 0.28f, s * 0.42f), size = Size(s * 0.08f, s * 0.30f))
            drawRect(Color(0xFFE0F2FE), topLeft = Offset(s * 0.64f, s * 0.42f), size = Size(s * 0.08f, s * 0.30f))
            drawRect(Color(0xFFBAE6FD), topLeft = Offset(s * 0.44f, s * 0.48f), size = Size(s * 0.12f, s * 0.24f))
        }
    }
}

/**
 * Realistic 3D Cloud Backup Icon for Backup & Restore.
 */
@Composable
fun Realistic3DCloudBackupIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cloudAnim")
    val arrowFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloudArrow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Background container
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(s - 4.dp.toPx(), s - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Cloud base circles
            drawCircle(Color.White, center = Offset(cx - s * 0.12f, cy + s * 0.04f), radius = s * 0.14f)
            drawCircle(Color.White, center = Offset(cx + s * 0.12f, cy + s * 0.02f), radius = s * 0.16f)
            drawCircle(Color.White, center = Offset(cx, cy - s * 0.10f), radius = s * 0.15f)

            // Upload Arrow
            val arrowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy - s * 0.06f + arrowFloat)
                lineTo(cx + s * 0.10f, cy + s * 0.08f + arrowFloat)
                lineTo(cx - s * 0.10f, cy + s * 0.08f + arrowFloat)
                close()
            }
            drawPath(arrowPath, Color(0xFF2563EB))
        }
    }
}

/**
 * Photorealistic 3D Cloud Vault Icon with dynamic ambient gradient lighting,
 * specular reflection, floating micro-motion, and sync pulse.
 */
@Composable
fun Realistic3DCloudVaultIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isSyncing: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cloudVaultAnim")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cloudVaultFloat"
    )
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloudVaultSpin"
    )

    Box(
        modifier = modifier
            .size(size)
            .offset(y = floatOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val corner = s * 0.28f

            // Outer Ambient Shadow
            drawRoundRect(
                color = Color(0xFF0284C7).copy(alpha = 0.35f),
                topLeft = Offset(s * 0.06f, s * 0.12f),
                size = Size(s * 0.88f, s * 0.88f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            )

            // Primary 3D Metallic Gloss Body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF38BDF8),
                        Color(0xFF2563EB),
                        Color(0xFF1E3A8A)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.05f, s * 0.05f),
                size = Size(s * 0.90f, s * 0.90f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            )

            // Top-left Specular Sheen Reflection
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    startY = s * 0.05f,
                    endY = s * 0.50f
                ),
                topLeft = Offset(s * 0.07f, s * 0.07f),
                size = Size(s * 0.86f, s * 0.40f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.9f, corner * 0.9f)
            )

            // Cloud Silhouette Base (Pure White / Cyan Highlight)
            val cloudY = cy + s * 0.02f
            drawCircle(Color.White, center = Offset(cx - s * 0.16f, cloudY + s * 0.04f), radius = s * 0.13f)
            drawCircle(Color.White, center = Offset(cx + s * 0.16f, cloudY + s * 0.03f), radius = s * 0.14f)
            drawCircle(Color.White, center = Offset(cx - s * 0.02f, cloudY - s * 0.10f), radius = s * 0.17f)
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - s * 0.20f, cloudY - s * 0.02f),
                size = Size(s * 0.40f, s * 0.19f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f, s * 0.08f)
            )

            // Inner Upload Arrow / Spin Pulse
            if (isSyncing) {
                rotate(degrees = syncRotation, pivot = Offset(cx, cy)) {
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = s * 0.10f,
                        center = Offset(cx, cloudY),
                        style = Stroke(width = s * 0.05f, cap = StrokeCap.Round)
                    )
                }
            } else {
                val arrowPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, cloudY - s * 0.09f)
                    lineTo(cx + s * 0.09f, cloudY + s * 0.04f)
                    lineTo(cx + s * 0.035f, cloudY + s * 0.04f)
                    lineTo(cx + s * 0.035f, cloudY + s * 0.12f)
                    lineTo(cx - s * 0.035f, cloudY + s * 0.12f)
                    lineTo(cx - s * 0.035f, cloudY + s * 0.04f)
                    lineTo(cx - s * 0.09f, cloudY + s * 0.04f)
                    close()
                }
                drawPath(arrowPath, Color(0xFF1D4ED8))
            }
        }
    }
}

/**
 * Photorealistic 3D AES-256 Security Shield Icon with emerald/sapphire depth.
 */
@Composable
fun Realistic3DAesShieldIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shieldAnim")
    val shieldScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shieldPulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = shieldScale
                scaleY = shieldScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Shield Path
            val shieldPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, s * 0.08f)
                cubicTo(cx + s * 0.38f, s * 0.10f, cx + s * 0.42f, s * 0.35f, cx + s * 0.40f, s * 0.55f)
                cubicTo(cx + s * 0.36f, s * 0.78f, cx + s * 0.15f, s * 0.90f, cx, s * 0.96f)
                cubicTo(cx - s * 0.15f, s * 0.90f, cx - s * 0.36f, s * 0.78f, cx - s * 0.40f, s * 0.55f)
                cubicTo(cx - s * 0.42f, s * 0.35f, cx - s * 0.38f, s * 0.10f, cx, s * 0.08f)
                close()
            }

            // Shield Shadow
            drawPath(shieldPath, Color(0xFF059669).copy(alpha = 0.35f))

            // Shield Body Gradient
            drawPath(
                shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF34D399),
                        Color(0xFF10B981),
                        Color(0xFF047857)
                    ),
                    start = Offset(cx - s * 0.4f, s * 0.1f),
                    end = Offset(cx + s * 0.4f, s * 0.9f)
                )
            )

            // Inner Keyhole / Lock Core
            drawCircle(
                color = Color.White,
                radius = s * 0.08f,
                center = Offset(cx, cy - s * 0.04f)
            )
            val lockBody = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - s * 0.045f, cy - s * 0.02f)
                lineTo(cx + s * 0.045f, cy - s * 0.02f)
                lineTo(cx + s * 0.07f, cy + s * 0.14f)
                lineTo(cx - s * 0.07f, cy + s * 0.14f)
                close()
            }
            drawPath(lockBody, Color.White)
        }
    }
}

/**
 * Photorealistic 3D Local Vault Disk Icon for Manual File Backup / Export.
 */
@Composable
fun Realistic3DLocalVaultDiskIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "diskAnim")
    val tilt by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "diskTilt"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = tilt },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val corner = s * 0.22f

            // Body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFA78BFA),
                        Color(0xFF7C3AED),
                        Color(0xFF4C1D95)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.06f, s * 0.06f),
                size = Size(s * 0.88f, s * 0.88f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            )

            // Top highlight
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    startY = s * 0.08f,
                    endY = s * 0.45f
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.8f, corner * 0.8f)
            )

            // Data Arrow / Drive Tray
            val arrowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy + s * 0.16f)
                lineTo(cx + s * 0.11f, cy + s * 0.02f)
                lineTo(cx + s * 0.045f, cy + s * 0.02f)
                lineTo(cx + s * 0.045f, cy - s * 0.14f)
                lineTo(cx - s * 0.045f, cy - s * 0.14f)
                lineTo(cx - s * 0.045f, cy + s * 0.02f)
                lineTo(cx - s * 0.11f, cy + s * 0.02f)
                close()
            }
            drawPath(arrowPath, Color.White)

            // Status Activity LED
            drawCircle(Color(0xFF34D399), radius = s * 0.04f, center = Offset(s * 0.80f, s * 0.20f))
        }
    }
}

/**
 * Photorealistic 3D Restore Rewind Capsule Icon for Snapshot Restore.
 */
@Composable
fun Realistic3DRestoreRewindIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rewindAnim")
    val rot by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rewindRot"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val corner = s * 0.22f

            // Amber / Gold Body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFBBF24),
                        Color(0xFFD97706),
                        Color(0xFF92400E)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.06f, s * 0.06f),
                size = Size(s * 0.88f, s * 0.88f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            )

            // Top highlight
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
                    startY = s * 0.08f,
                    endY = s * 0.45f
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * 0.8f, corner * 0.8f)
            )

            // Rewind Arc
            rotate(degrees = rot, pivot = Offset(cx, cy)) {
                drawArc(
                    color = Color.White,
                    startAngle = 40f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(cx - s * 0.18f, cy - s * 0.18f),
                    size = Size(s * 0.36f, s * 0.36f),
                    style = Stroke(width = s * 0.055f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Realistic 3D Spreadsheet Transfer Icon for Import & Export.
 */
@Composable
fun Realistic3DSpreadsheetTransferIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension

            // Background container
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(s - 4.dp.toPx(), s - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Sheet Paper
            drawRoundRect(
                Color.White,
                topLeft = Offset(s * 0.25f, s * 0.25f),
                size = Size(s * 0.50f, s * 0.50f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Table rows
            drawRect(Color(0xFF059669), topLeft = Offset(s * 0.32f, s * 0.34f), size = Size(s * 0.36f, s * 0.08f))
            drawRect(Color(0xFF10B981), topLeft = Offset(s * 0.32f, s * 0.48f), size = Size(s * 0.36f, s * 0.05f))
            drawRect(Color(0xFF10B981), topLeft = Offset(s * 0.32f, s * 0.58f), size = Size(s * 0.36f, s * 0.05f))
        }
    }
}

/**
 * Realistic 3D Holographic Info Orb for About screen.
 */
@Composable
fun Realistic3DInfoBadgeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFA78BFA), Color(0xFF7C3AED)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(s - 4.dp.toPx(), s - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            drawCircle(Color.White, center = Offset(cx, cy), radius = s * 0.25f)
            drawCircle(Color(0xFF7C3AED), center = Offset(cx, cy - s * 0.10f), radius = s * 0.04f)
            drawRect(Color(0xFF7C3AED), topLeft = Offset(cx - s * 0.035f, cy - s * 0.02f), size = Size(s * 0.07f, s * 0.16f))
        }
    }
}

/**
 * Realistic 3D Warning Icon for Factory Reset.
 */
@Composable
fun Realistic3DResetWarningIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "warningAnim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warnPulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF87171), Color(0xFFDC2626)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(s - 4.dp.toPx(), s - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // White triangle
            val triPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy - s * 0.22f)
                lineTo(cx + s * 0.24f, cy + s * 0.20f)
                lineTo(cx - s * 0.24f, cy + s * 0.20f)
                close()
            }
            drawPath(triPath, Color.White)

            // Exclamation mark
            drawRect(Color(0xFFDC2626), topLeft = Offset(cx - s * 0.03f, cy - s * 0.08f), size = Size(s * 0.06f, s * 0.14f))
            drawCircle(Color(0xFFDC2626), center = Offset(cx, cy + s * 0.12f), radius = (s * 0.035f) * pulse)
        }
    }
}

/**
 * Realistic 3D Language Globe Icon.
 */
@Composable
fun Realistic3DLangIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                center = Offset(cx, cy),
                radius = s * 0.42f
            )

            // Latitudes & Longitudes
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                center = Offset(cx, cy),
                radius = s * 0.30f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
            )
            drawLine(
                color = Color.White,
                start = Offset(cx - s * 0.38f, cy),
                end = Offset(cx + s * 0.38f, cy),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = Color.White,
                start = Offset(cx, cy - s * 0.38f),
                end = Offset(cx, cy + s * 0.38f),
                strokeWidth = 1.2.dp.toPx()
            )
        }
    }
}

/**
 * Realistic 3D Theme & Palette Icon for settings.
 */
@Composable
fun Realistic3DThemeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF6366F1), Color(0xFF1E1B4B)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                center = Offset(cx, cy),
                radius = s * 0.44f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx * 0.75f, cy * 0.75f),
                    radius = s * 0.35f
                ),
                center = Offset(cx, cy),
                radius = s * 0.42f
            )

            drawArc(
                color = Color(0xFFFDE047),
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.22f, cy - s * 0.22f),
                size = androidx.compose.ui.geometry.Size(s * 0.44f, s * 0.44f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}

/**
 * Realistic 3D QR Code Icon for settings.
 */
@Composable
fun Realistic3DQrCodeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(s - 4.dp.toPx(), s - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // QR square corners
            drawRect(Color.White, topLeft = Offset(s * 0.22f, s * 0.22f), size = Size(s * 0.22f, s * 0.22f))
            drawRect(Color(0xFFD97706), topLeft = Offset(s * 0.26f, s * 0.26f), size = Size(s * 0.14f, s * 0.14f))

            drawRect(Color.White, topLeft = Offset(s * 0.56f, s * 0.22f), size = Size(s * 0.22f, s * 0.22f))
            drawRect(Color(0xFFD97706), topLeft = Offset(s * 0.60f, s * 0.26f), size = Size(s * 0.14f, s * 0.14f))

            drawRect(Color.White, topLeft = Offset(s * 0.22f, s * 0.56f), size = Size(s * 0.22f, s * 0.22f))
            drawRect(Color(0xFFD97706), topLeft = Offset(s * 0.26f, s * 0.60f), size = Size(s * 0.14f, s * 0.14f))
        }
    }
}

/**
 * Photorealistic 3D Techyfi Brand Logo Badge matching User Sample.
 * Rounded square obsidian badge with metallic bevel, containing the stylized white & electric blue lightning 'T'.
 */
@Composable
fun Realistic3DTechyfiBrandIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "brand3DAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brandFloatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val cornerRadius = s * 0.28f

            // 1. Soft Ambient Drop Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(s * 0.05f, s * 0.12f),
                size = Size(s * 0.90f, s * 0.90f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )

            // 2. Obsidian Rounded Square Chassis
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF000000)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(0f, 0f),
                size = Size(s, s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
            )

            // Top Specular Highlight Bevel
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = s * 0.50f
                ),
                topLeft = Offset(0.8.dp.toPx(), 0.8.dp.toPx()),
                size = Size(s - 1.6.dp.toPx(), s - 1.6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(width = 1.dp.toPx())
            )

            // 3. Stylized White & Electric Blue Techyfi Lightning 'T'
            val whiteTPath = Path().apply {
                moveTo(s * 0.20f, s * 0.22f)
                lineTo(s * 0.80f, s * 0.22f)
                lineTo(s * 0.80f, s * 0.36f)
                lineTo(s * 0.58f, s * 0.36f)
                lineTo(s * 0.38f, s * 0.80f)
                lineTo(s * 0.46f, s * 0.50f)
                lineTo(s * 0.30f, s * 0.50f)
                close()
            }
            drawPath(path = whiteTPath, color = Color.White)

            val blueBoltPath = Path().apply {
                moveTo(s * 0.48f, s * 0.22f)
                lineTo(s * 0.80f, s * 0.22f)
                lineTo(s * 0.80f, s * 0.36f)
                lineTo(s * 0.58f, s * 0.36f)
                lineTo(s * 0.44f, s * 0.68f)
                lineTo(s * 0.54f, s * 0.44f)
                lineTo(s * 0.38f, s * 0.44f)
                close()
            }
            drawPath(
                path = blueBoltPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00D2FF), Color(0xFF0072FF)),
                    start = Offset(s * 0.38f, s * 0.22f),
                    end = Offset(s * 0.80f, s * 0.68f)
                )
            )
        }
    }
}

/**
 * Photorealistic 3D Cash Tray / Drawer with Banknotes & 3D Gold Coin for Top Header "Sell".
 * Features dark-navy chassis with electric-blue rim, Techyfi lightning logo on front,
 * layered emerald banknotes stack, and 3D embossed gold coin.
 */
@Composable
fun Realistic3DCashDrawerSellIcon(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cashTrayAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trayFloatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Ambient Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x350F172A), Color.Transparent),
                    center = Offset(w * 0.50f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.08f, h * 0.82f),
                size = Size(w * 0.84f, h * 0.16f)
            )

            // 2. Banknotes Stack Inside Tray
            // Layer 1 (Back Note)
            val note1 = Path().apply {
                moveTo(w * 0.22f, h * 0.42f)
                lineTo(w * 0.30f, h * 0.18f)
                lineTo(w * 0.68f, h * 0.18f)
                lineTo(w * 0.60f, h * 0.42f)
                close()
            }
            drawPath(note1, color = Color(0xFF047857).copy(alpha = 0.85f))

            // Layer 2 (Middle Note)
            val note2 = Path().apply {
                moveTo(w * 0.24f, h * 0.44f)
                lineTo(w * 0.32f, h * 0.22f)
                lineTo(w * 0.74f, h * 0.22f)
                lineTo(w * 0.66f, h * 0.44f)
                close()
            }
            drawPath(note2, color = Color(0xFF059669))

            // Layer 3 (Front Fanned Note)
            val note3 = Path().apply {
                moveTo(w * 0.26f, h * 0.46f)
                lineTo(w * 0.36f, h * 0.26f)
                lineTo(w * 0.80f, h * 0.26f)
                lineTo(w * 0.70f, h * 0.46f)
                close()
            }
            drawPath(
                note3,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669)),
                    start = Offset(w * 0.26f, h * 0.26f),
                    end = Offset(w * 0.80f, h * 0.46f)
                )
            )

            // 3. 3D Embossed Gold Dollar/Taka Coin (Standing on Right)
            val coinCenter = Offset(w * 0.76f, h * 0.32f)
            val coinRadius = w * 0.18f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFD97706)),
                    center = coinCenter,
                    radius = coinRadius
                ),
                center = coinCenter,
                radius = coinRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                center = coinCenter,
                radius = coinRadius,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF78350F).copy(alpha = 0.4f),
                center = coinCenter,
                radius = coinRadius * 0.75f,
                style = Stroke(width = 0.8.dp.toPx())
            )

            // 4. Dark Tech Cash Tray Box
            // Blue Top Rim
            val rimPath = Path().apply {
                moveTo(w * 0.10f, h * 0.48f)
                lineTo(w * 0.30f, h * 0.36f)
                lineTo(w * 0.94f, h * 0.36f)
                lineTo(w * 0.76f, h * 0.48f)
                close()
            }
            drawPath(
                path = rimPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00D2FF), Color(0xFF0072FF)),
                    start = Offset(w * 0.10f, h * 0.36f),
                    end = Offset(w * 0.94f, h * 0.48f)
                ),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Front & Side Box Walls
            val frontWall = Path().apply {
                moveTo(w * 0.10f, h * 0.48f)
                lineTo(w * 0.76f, h * 0.48f)
                lineTo(w * 0.76f, h * 0.82f)
                lineTo(w * 0.10f, h * 0.82f)
                close()
            }
            drawPath(
                path = frontWall,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                    start = Offset(w * 0.10f, h * 0.48f),
                    end = Offset(w * 0.76f, h * 0.82f)
                )
            )

            val sideWall = Path().apply {
                moveTo(w * 0.76f, h * 0.48f)
                lineTo(w * 0.94f, h * 0.36f)
                lineTo(w * 0.94f, h * 0.70f)
                lineTo(w * 0.76f, h * 0.82f)
                close()
            }
            drawPath(path = sideWall, color = Color(0xFF090E17))

            // Techyfi Lightning 'T' Stamped on Front Face
            val stampWhite = Path().apply {
                moveTo(w * 0.38f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.58f)
                lineTo(w * 0.46f, h * 0.58f)
                lineTo(w * 0.41f, h * 0.72f)
                lineTo(w * 0.44f, h * 0.63f)
                lineTo(w * 0.39f, h * 0.63f)
                close()
            }
            drawPath(stampWhite, color = Color.White)

            val stampBlue = Path().apply {
                moveTo(w * 0.44f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.54f)
                lineTo(w * 0.50f, h * 0.58f)
                lineTo(w * 0.46f, h * 0.58f)
                lineTo(w * 0.43f, h * 0.68f)
                lineTo(w * 0.45f, h * 0.61f)
                lineTo(w * 0.41f, h * 0.61f)
                close()
            }
            drawPath(stampBlue, color = Color(0xFF00D2FF))
        }
    }
}

/**
 * Photorealistic 3D Handheld POS Terminal with Banknotes Bundle for Selector Pill "Sell".
 * Features banknotes with blue Techyfi strap + card machine with Techyfi 'T' on screen,
 * full tactile colored keypad (red, yellow, green), and paper receipt roll.
 */
@Composable
fun Realistic3DPosTerminalSellIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "posTerminalAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "terminalFloatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Ambient Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x300F172A), Color.Transparent),
                    center = Offset(w * 0.50f, h * 0.88f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.10f, h * 0.80f),
                size = Size(w * 0.80f, h * 0.16f)
            )

            // 2. Banknotes Bundle in Background
            val noteRect = Path().apply {
                moveTo(w * 0.08f, h * 0.45f)
                lineTo(w * 0.52f, h * 0.36f)
                lineTo(w * 0.48f, h * 0.72f)
                lineTo(w * 0.04f, h * 0.81f)
                close()
            }
            drawPath(
                noteRect,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669)),
                    start = Offset(w * 0.08f, h * 0.36f),
                    end = Offset(w * 0.52f, h * 0.81f)
                )
            )
            // Blue Band Strap
            val strapPath = Path().apply {
                moveTo(w * 0.22f, h * 0.42f)
                lineTo(w * 0.34f, h * 0.40f)
                lineTo(w * 0.30f, h * 0.76f)
                lineTo(w * 0.18f, h * 0.78f)
                close()
            }
            drawPath(
                strapPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00D2FF), Color(0xFF0072FF)),
                    start = Offset(w * 0.22f, h * 0.40f),
                    end = Offset(w * 0.34f, h * 0.78f)
                )
            )

            // 3. 3D Handheld POS Terminal (Foreground Leaning Machine)
            val posBody = Path().apply {
                moveTo(w * 0.36f, h * 0.20f)
                lineTo(w * 0.80f, h * 0.20f)
                lineTo(w * 0.76f, h * 0.82f)
                lineTo(w * 0.32f, h * 0.82f)
                close()
            }
            drawPath(
                posBody,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A)),
                    start = Offset(w * 0.36f, h * 0.20f),
                    end = Offset(w * 0.76f, h * 0.82f)
                )
            )
            drawPath(
                posBody,
                color = Color.White.copy(alpha = 0.35f),
                style = Stroke(width = 1.dp.toPx())
            )

            // Top Receipt Paper
            val receiptPath = Path().apply {
                moveTo(w * 0.44f, h * 0.12f)
                lineTo(w * 0.72f, h * 0.12f)
                lineTo(w * 0.70f, h * 0.20f)
                lineTo(w * 0.42f, h * 0.20f)
                close()
            }
            drawPath(receiptPath, color = Color(0xFFF8FAFC))

            // Dark Screen
            drawRoundRect(
                color = Color(0xFF0A0F1D),
                topLeft = Offset(w * 0.40f, h * 0.26f),
                size = Size(w * 0.32f, h * 0.22f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Glowing Blue Techyfi 'T' on Terminal Screen
            val screenBolt = Path().apply {
                moveTo(w * 0.50f, h * 0.30f)
                lineTo(w * 0.62f, h * 0.30f)
                lineTo(w * 0.62f, h * 0.33f)
                lineTo(w * 0.58f, h * 0.33f)
                lineTo(w * 0.54f, h * 0.43f)
                lineTo(w * 0.56f, h * 0.37f)
                lineTo(w * 0.52f, h * 0.37f)
                close()
            }
            drawPath(screenBolt, color = Color(0xFF00D2FF))

            // Keypad (3 rows of buttons)
            // Number Buttons (Silver/Grey)
            drawCircle(Color(0xFF94A3B8), center = Offset(w * 0.45f, h * 0.54f), radius = 1.2.dp.toPx())
            drawCircle(Color(0xFF94A3B8), center = Offset(w * 0.56f, h * 0.54f), radius = 1.2.dp.toPx())
            drawCircle(Color(0xFF94A3B8), center = Offset(w * 0.67f, h * 0.54f), radius = 1.2.dp.toPx())

            drawCircle(Color(0xFF94A3B8), center = Offset(w * 0.45f, h * 0.62f), radius = 1.2.dp.toPx())
            drawCircle(Color(0xFF94A3B8), center = Offset(w * 0.56f, h * 0.62f), radius = 1.2.dp.toPx())
            drawCircle(Color(0xFF94A3B8), center = Offset(w * 0.67f, h * 0.62f), radius = 1.2.dp.toPx())

            // Action Buttons: Red (Cancel), Yellow (Back), Green (Enter/OK)
            drawCircle(Color(0xFFEF4444), center = Offset(w * 0.45f, h * 0.71f), radius = 1.3.dp.toPx())
            drawCircle(Color(0xFFF59E0B), center = Offset(w * 0.56f, h * 0.71f), radius = 1.3.dp.toPx())
            drawCircle(Color(0xFF10B981), center = Offset(w * 0.67f, h * 0.71f), radius = 1.3.dp.toPx())
        }
    }
}

/**
 * Photorealistic 3D Emerald Smart Cart with Gold Coin for Cart Review Pill.
 */
@Composable
fun Realistic3DSmartCartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "smartCartAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cartFloatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33000000), Color.Transparent),
                    center = Offset(cx, s * 0.90f),
                    radius = s * 0.40f
                ),
                topLeft = Offset(s * 0.12f, s * 0.84f),
                size = Size(s * 0.76f, s * 0.12f)
            )

            // 2. 3D Metallic Emerald Cart Basket Path
            val basketPath = Path().apply {
                moveTo(s * 0.16f, s * 0.22f)
                lineTo(s * 0.28f, s * 0.22f)
                lineTo(s * 0.38f, s * 0.66f)
                lineTo(s * 0.78f, s * 0.66f)
                lineTo(s * 0.86f, s * 0.33f)
                lineTo(s * 0.33f, s * 0.33f)
            }
            drawPath(
                path = basketPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF047857)),
                    start = Offset(s * 0.16f, s * 0.22f),
                    end = Offset(s * 0.86f, s * 0.66f)
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3. 3D Gold Coin inside Basket
            val coinCenter = Offset(s * 0.58f, s * 0.38f)
            val coinRadius = s * 0.14f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFD97706)),
                    center = coinCenter,
                    radius = coinRadius
                ),
                center = coinCenter,
                radius = coinRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                center = coinCenter,
                radius = coinRadius,
                style = Stroke(width = 0.8.dp.toPx())
            )

            // 4. Luminous Wheels
            val wheelRadius = s * 0.08f
            drawCircle(color = Color(0xFF1E293B), center = Offset(s * 0.42f, s * 0.78f), radius = wheelRadius)
            drawCircle(color = Color(0xFF10B981), center = Offset(s * 0.42f, s * 0.78f), radius = wheelRadius, style = Stroke(width = 1.2.dp.toPx()))

            drawCircle(color = Color(0xFF1E293B), center = Offset(s * 0.75f, s * 0.78f), radius = wheelRadius)
            drawCircle(color = Color(0xFF10B981), center = Offset(s * 0.75f, s * 0.78f), radius = wheelRadius, style = Stroke(width = 1.2.dp.toPx()))
        }
    }
}

/**
 * 0. Reference 3D Honey Ochre Shopping Bag with Cyan Loop Handles & Pink Grommets.
 */
@Composable
fun Realistic3DOchreShoppingBagIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ochreBagAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Soft Drop Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.20f),
                topLeft = Offset(cx - s * 0.40f, cy + s * 0.36f),
                size = Size(s * 0.80f, s * 0.14f)
            )

            // 2. Dual Cyan Loop Handles (Rear & Front)
            // Rear Cyan Handle (Taller, positioned slightly left)
            drawArc(
                color = Color(0xFF0284C7),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.26f, cy - s * 0.44f),
                size = Size(s * 0.28f, s * 0.56f),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF38BDF8),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.26f, cy - s * 0.44f),
                size = Size(s * 0.28f, s * 0.56f),
                style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round)
            )

            // Front Cyan Handle (Foreground handle)
            drawArc(
                color = Color(0xFF0284C7),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.08f, cy - s * 0.43f),
                size = Size(s * 0.30f, s * 0.58f),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF38BDF8),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.08f, cy - s * 0.43f),
                size = Size(s * 0.30f, s * 0.58f),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )

            // 3. Left Side Facet (Soft Cream / Warm Yellow Light Reflection)
            val leftPath = Path().apply {
                moveTo(cx - s * 0.32f, cy - s * 0.22f)
                lineTo(cx - s * 0.18f, cy - s * 0.26f)
                lineTo(cx - s * 0.22f, cy + s * 0.36f)
                lineTo(cx - s * 0.38f, cy + s * 0.30f)
                close()
            }
            drawPath(
                path = leftPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFFDE047), Color(0xFFFACC15)),
                    start = Offset(cx - s * 0.38f, cy - s * 0.22f),
                    end = Offset(cx - s * 0.18f, cy + s * 0.36f)
                )
            )
            drawPath(
                path = leftPath,
                color = Color(0xFFCA8A04),
                style = Stroke(width = 0.8.dp.toPx(), join = StrokeJoin.Round)
            )

            // 4. Front Main Ochre/Honey Bag Face
            val frontPath = Path().apply {
                moveTo(cx - s * 0.18f, cy - s * 0.26f)
                lineTo(cx + s * 0.28f, cy - s * 0.22f)
                lineTo(cx + s * 0.35f, cy + s * 0.30f)
                lineTo(cx - s * 0.22f, cy + s * 0.36f)
                close()
            }
            drawPath(
                path = frontPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFACC15), Color(0xFFEAB308), Color(0xFFCA8A04), Color(0xFFA16207)),
                    start = Offset(cx - s * 0.18f, cy - s * 0.26f),
                    end = Offset(cx + s * 0.35f, cy + s * 0.36f)
                )
            )
            drawPath(
                path = frontPath,
                color = Color(0xFFA16207),
                style = Stroke(width = 0.8.dp.toPx(), join = StrokeJoin.Round)
            )

            // Soft Ambient Specular Sheen across Front Face
            val sheenPath = Path().apply {
                moveTo(cx - s * 0.14f, cy - s * 0.22f)
                lineTo(cx + s * 0.12f, cy - s * 0.20f)
                lineTo(cx + s * 0.18f, cy + s * 0.30f)
                lineTo(cx - s * 0.17f, cy + s * 0.32f)
                close()
            }
            drawPath(
                path = sheenPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0f)),
                    start = Offset(cx - s * 0.14f, cy - s * 0.22f),
                    end = Offset(cx + s * 0.18f, cy + s * 0.32f)
                )
            )

            // 5. Pink / Coral Grommets & Eyelet Rings
            // Left Eyelet (Front Handle Base Left)
            val grommet1X = cx - s * 0.07f
            val grommet1Y = cy - s * 0.14f
            drawCircle(Color(0xFFF43F5E), radius = s * 0.045f, center = Offset(grommet1X, grommet1Y))
            drawCircle(Color(0xFFE11D48), radius = s * 0.045f, center = Offset(grommet1X, grommet1Y), style = Stroke(width = 0.8.dp.toPx()))
            drawCircle(Color(0xFF881337), radius = s * 0.022f, center = Offset(grommet1X, grommet1Y))

            // Right Eyelet (Front Handle Base Right)
            val grommet2X = cx + s * 0.22f
            val grommet2Y = cy - s * 0.12f
            drawCircle(Color(0xFFF43F5E), radius = s * 0.045f, center = Offset(grommet2X, grommet2Y))
            drawCircle(Color(0xFFE11D48), radius = s * 0.045f, center = Offset(grommet2X, grommet2Y), style = Stroke(width = 0.8.dp.toPx()))
            drawCircle(Color(0xFF881337), radius = s * 0.022f, center = Offset(grommet2X, grommet2Y))
        }
    }
}

/**
 * 1. Sell: Emerald Green Leather Wallet Icon with folded banknotes & white snap fastener.
 */
@Composable
fun Realistic3DLeatherWalletIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wallet3DAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Ground Drop Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(cx - s * 0.38f, cy + s * 0.34f),
                size = Size(s * 0.76f, s * 0.16f)
            )

            // Protruding Folded Banknotes at Top
            // Note 1: White Note
            drawRoundRect(
                color = Color(0xFFF8FAFC),
                topLeft = Offset(cx - s * 0.34f, cy - s * 0.40f),
                size = Size(s * 0.52f, s * 0.28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(cx - s * 0.34f, cy - s * 0.40f),
                size = Size(s * 0.52f, s * 0.28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Note 2: Light Green Cash
            drawRoundRect(
                color = Color(0xFF86EFAC),
                topLeft = Offset(cx - s * 0.30f, cy - s * 0.36f),
                size = Size(s * 0.46f, s * 0.24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF22C55E),
                topLeft = Offset(cx - s * 0.30f, cy - s * 0.36f),
                size = Size(s * 0.46f, s * 0.24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Emerald Wallet Main Body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF047857)),
                    start = Offset(cx - s * 0.42f, cy - s * 0.26f),
                    end = Offset(cx + s * 0.32f, cy + s * 0.32f)
                ),
                topLeft = Offset(cx - s * 0.42f, cy - s * 0.26f),
                size = Size(s * 0.74f, s * 0.58f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            // Specular Top Curved Highlight
            drawArc(
                color = Color.White.copy(alpha = 0.55f),
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.38f, cy - s * 0.24f),
                size = Size(s * 0.24f, s * 0.24f),
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Flap / Latch Strap on Right Side
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF059669), Color(0xFF065F46)),
                    start = Offset(cx, cy - s * 0.10f),
                    end = Offset(cx + s * 0.37f, cy + s * 0.16f)
                ),
                topLeft = Offset(cx - s * 0.05f, cy - s * 0.10f),
                size = Size(s * 0.42f, s * 0.26f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx())
            )

            // Glossy White Snap Button
            drawCircle(
                color = Color.White,
                radius = s * 0.07f,
                center = Offset(cx + s * 0.22f, cy + s * 0.03f)
            )
            drawCircle(
                color = Color(0xFFE2E8F0),
                radius = s * 0.07f,
                center = Offset(cx + s * 0.22f, cy + s * 0.03f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Photorealistic 3D Animated Money Cash Stack Icon with Emerald Banknotes & Metallic Gold Strap.
 */
@Composable
fun Realistic3DMoneyBundleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "money3DAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val tiltZ by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tiltZ"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = floatY
                rotationZ = tiltZ
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Dynamic Contact Shadow
            val shadowScale = 1f - ((-floatY) / 14f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55000000), Color.Transparent),
                    center = Offset(cx, cy + s * 0.36f),
                    radius = s * 0.45f * shadowScale
                ),
                topLeft = Offset(cx - s * 0.40f * shadowScale, cy + s * 0.28f),
                size = Size(s * 0.80f * shadowScale, s * 0.16f)
            )

            // 2. Bottom Angled Banknote (Fanned out left)
            rotate(degrees = -10f, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF047857), Color(0xFF065F46), Color(0xFF064E3B)),
                        start = Offset(cx - s * 0.38f, cy - s * 0.20f),
                        end = Offset(cx + s * 0.38f, cy + s * 0.20f)
                    ),
                    topLeft = Offset(cx - s * 0.38f, cy - s * 0.20f),
                    size = Size(s * 0.76f, s * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF059669),
                    topLeft = Offset(cx - s * 0.38f, cy - s * 0.20f),
                    size = Size(s * 0.76f, s * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // 3. Middle Banknote Layer (Fanned out right)
            rotate(degrees = 6f, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF047857)),
                        start = Offset(cx - s * 0.38f, cy - s * 0.20f),
                        end = Offset(cx + s * 0.38f, cy + s * 0.20f)
                    ),
                    topLeft = Offset(cx - s * 0.38f, cy - s * 0.20f),
                    size = Size(s * 0.76f, s * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFA7F3D0).copy(alpha = 0.6f),
                    topLeft = Offset(cx - s * 0.38f, cy - s * 0.20f),
                    size = Size(s * 0.76f, s * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // 4. Top Primary Emerald Banknote (Facing forward)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669)),
                    start = Offset(cx - s * 0.40f, cy - s * 0.22f),
                    end = Offset(cx + s * 0.40f, cy + s * 0.22f)
                ),
                topLeft = Offset(cx - s * 0.40f, cy - s * 0.22f),
                size = Size(s * 0.80f, s * 0.42f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF047857),
                topLeft = Offset(cx - s * 0.40f, cy - s * 0.22f),
                size = Size(s * 0.80f, s * 0.42f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.5.dp.toPx(), 4.5.dp.toPx()),
                style = Stroke(width = 1.0.dp.toPx())
            )

            // Inner Fine White Border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(cx - s * 0.35f, cy - s * 0.17f),
                size = Size(s * 0.70f, s * 0.32f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = 0.8.dp.toPx())
            )

            // Center Currency Medallion
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF059669), Color(0xFF047857)),
                    center = Offset(cx, cy - s * 0.01f),
                    radius = s * 0.16f
                ),
                topLeft = Offset(cx - s * 0.15f, cy - s * 0.11f),
                size = Size(s * 0.30f, s * 0.20f)
            )
            drawOval(
                color = Color(0xFFD1FAE5),
                topLeft = Offset(cx - s * 0.15f, cy - s * 0.11f),
                size = Size(s * 0.30f, s * 0.20f),
                style = Stroke(width = 0.8.dp.toPx())
            )

            // Taka Symbol "৳"
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.06f, cy - s * 0.08f),
                size = Size(s * 0.12f, s * 0.09f),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
            )
            drawLine(
                color = Color.White,
                start = Offset(cx + s * 0.06f, cy - s * 0.035f),
                end = Offset(cx + s * 0.06f, cy + s * 0.055f),
                strokeWidth = 1.3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 5. Metallic Gold Currency Ribbon Strap
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFD97706)),
                    start = Offset(cx - s * 0.08f, cy),
                    end = Offset(cx + s * 0.08f, cy)
                ),
                topLeft = Offset(cx - s * 0.08f, cy - s * 0.22f),
                size = Size(s * 0.16f, s * 0.42f)
            )
            drawRect(
                color = Color(0xFFB45309),
                topLeft = Offset(cx - s * 0.08f, cy - s * 0.22f),
                size = Size(s * 0.16f, s * 0.42f),
                style = Stroke(width = 0.6.dp.toPx())
            )

            // Top Specular Highlight Sweep
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent),
                    start = Offset(cx - s * 0.35f, cy - s * 0.20f),
                    end = Offset(cx + s * 0.35f, cy - s * 0.20f)
                ),
                start = Offset(cx - s * 0.35f, cy - s * 0.20f),
                end = Offset(cx + s * 0.35f, cy - s * 0.20f),
                strokeWidth = 1.2.dp.toPx()
            )
        }
    }
}

/** Backward compatible alias for Sell Header / Mode icon */
@Composable
fun Realistic3DMoneyIcon(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Realistic3DMoneyBundleIcon(modifier = modifier, size = size)
}

/**
 * Photorealistic 3D Isometric Cargo Box with High-Tech Inventory Badge & 3D Floating Animation.
 */
@Composable
fun Realistic3DStockPyramidIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stock3DAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = floatY
                rotationY = rotY
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Dynamic Contact Shadow
            val shadowScale = 1f - ((-floatY) / 14f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x55000000), Color.Transparent),
                    center = Offset(cx, cy + s * 0.38f),
                    radius = s * 0.45f * shadowScale
                ),
                topLeft = Offset(cx - s * 0.40f * shadowScale, cy + s * 0.30f),
                size = Size(s * 0.80f * shadowScale, s * 0.16f)
            )

            // 3D Isometric Box Dimensions
            val topY = cy - s * 0.40f
            val midY = cy - s * 0.05f
            val botY = cy + s * 0.32f
            val leftX = cx - s * 0.40f
            val rightX = cx + s * 0.40f
            val leftMidY = cy - s * 0.22f
            val rightMidY = cy - s * 0.22f
            val leftBotY = cy + s * 0.15f
            val rightBotY = cy + s * 0.15f

            // 1. Top Isometric Facet (Sunlight / Specular Highlight)
            val topPath = Path().apply {
                moveTo(cx, topY)
                lineTo(rightX, rightMidY)
                lineTo(cx, midY)
                lineTo(leftX, leftMidY)
                close()
            }
            drawPath(
                path = topPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
                    start = Offset(leftX, topY),
                    end = Offset(rightX, midY)
                )
            )

            // Top Sealing Tape
            val tapeTop = Path().apply {
                val tOff = (rightX - cx) * 0.26f
                moveTo(cx - tOff, (topY + leftMidY) / 2f)
                lineTo(cx + tOff, (topY + rightMidY) / 2f)
                lineTo(cx + tOff, (midY + rightMidY) / 2f)
                lineTo(cx - tOff, (midY + leftMidY) / 2f)
                close()
            }
            drawPath(
                path = tapeTop,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF3C7), Color(0xFFFBBF24)),
                    start = Offset(cx, topY),
                    end = Offset(cx, midY)
                )
            )

            // 2. Front / Left Facet (Direct Light Kraft Cardboard)
            val leftPath = Path().apply {
                moveTo(leftX, leftMidY)
                lineTo(cx, midY)
                lineTo(cx, botY)
                lineTo(leftX, leftBotY)
                close()
            }
            drawPath(
                path = leftPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFFB45309)),
                    start = Offset(leftX, leftMidY),
                    end = Offset(cx, botY)
                )
            )

            // 3. Right Facet (Shaded Depth Kraft Cardboard)
            val rightPath = Path().apply {
                moveTo(cx, midY)
                lineTo(rightX, rightMidY)
                lineTo(rightX, rightBotY)
                lineTo(cx, botY)
                close()
            }
            drawPath(
                path = rightPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFB45309), Color(0xFF78350F)),
                    start = Offset(cx, midY),
                    end = Offset(rightX, botY)
                )
            )

            // Front Left Tape Overhang
            val tapeFront = Path().apply {
                val tOff = (cx - leftX) * 0.26f
                moveTo(cx - tOff, (midY + leftMidY) / 2f)
                lineTo(cx, midY)
                lineTo(cx, botY)
                lineTo(cx - tOff, (botY + leftBotY) / 2f)
                close()
            }
            drawPath(
                path = tapeFront,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                    start = Offset(cx - 10f, midY),
                    end = Offset(cx, botY)
                )
            )

            // 4. Glowing Cyan/Blue Smart Warehouse Inventory Badge
            val badgeCenter = Offset(cx - s * 0.16f, cy + s * 0.06f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB)),
                    start = Offset(badgeCenter.x - s * 0.10f, badgeCenter.y - s * 0.08f),
                    end = Offset(badgeCenter.x + s * 0.10f, badgeCenter.y + s * 0.08f)
                ),
                topLeft = Offset(badgeCenter.x - s * 0.10f, badgeCenter.y - s * 0.08f),
                size = Size(s * 0.20f, s * 0.16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            // Badge Border
            drawRoundRect(
                color = Color(0xFFBFDBFE),
                topLeft = Offset(badgeCenter.x - s * 0.10f, badgeCenter.y - s * 0.08f),
                size = Size(s * 0.20f, s * 0.16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 0.6.dp.toPx())
            )
            // Barcode Lines on Badge
            drawLine(Color.White, Offset(badgeCenter.x - s * 0.06f, badgeCenter.y - s * 0.04f), Offset(badgeCenter.x - s * 0.06f, badgeCenter.y + s * 0.04f), strokeWidth = 1.0.dp.toPx())
            drawLine(Color.White, Offset(badgeCenter.x - s * 0.02f, badgeCenter.y - s * 0.04f), Offset(badgeCenter.x - s * 0.02f, badgeCenter.y + s * 0.04f), strokeWidth = 1.5.dp.toPx())
            drawLine(Color.White, Offset(badgeCenter.x + s * 0.03f, badgeCenter.y - s * 0.04f), Offset(badgeCenter.x + s * 0.03f, badgeCenter.y + s * 0.04f), strokeWidth = 0.8.dp.toPx())
            drawLine(Color.White, Offset(badgeCenter.x + s * 0.06f, badgeCenter.y - s * 0.04f), Offset(badgeCenter.x + s * 0.06f, badgeCenter.y + s * 0.04f), strokeWidth = 1.2.dp.toPx())

            // Top Specular Highlight Edge
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(cx, topY),
                end = Offset(rightX, rightMidY),
                strokeWidth = 1.0.dp.toPx()
            )
        }
    }
}

/**
 * 3. Barcode Scan: Dark Monitor Screen with Green Corner Bumpers & Red Scanning Laser.
 */
@Composable
fun Realistic3DBarcodeScreenIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laserScanAnim")
    val laserYOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Ground Drop Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(cx - s * 0.36f, cy + s * 0.34f),
                size = Size(s * 0.72f, s * 0.14f)
            )

            // Dark Screen Monitor Frame
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                    start = Offset(cx - s * 0.36f, cy - s * 0.36f),
                    end = Offset(cx + s * 0.36f, cy + s * 0.36f)
                ),
                topLeft = Offset(cx - s * 0.36f, cy - s * 0.36f),
                size = Size(s * 0.72f, s * 0.72f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF334155),
                topLeft = Offset(cx - s * 0.36f, cy - s * 0.36f),
                size = Size(s * 0.72f, s * 0.72f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Inner Glass Screen
            drawRoundRect(
                color = Color(0xFF090D16),
                topLeft = Offset(cx - s * 0.30f, cy - s * 0.30f),
                size = Size(s * 0.60f, s * 0.60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Barcode Lines in Center
            val bcY = cy - s * 0.14f
            val bcH = s * 0.28f
            val barWidths = listOf(1.5f, 1f, 2f, 1f, 1.5f, 2.5f, 1f, 1.5f, 2f, 1f, 1.5f)
            var startX = cx - s * 0.22f
            barWidths.forEach { bw ->
                drawRect(
                    color = Color.White,
                    topLeft = Offset(startX, bcY),
                    size = Size(bw.dp.toPx(), bcH)
                )
                startX += bw.dp.toPx() + 1.8.dp.toPx()
            }

            // Glowing Red Laser Scan Beam
            val curLaserY = cy + laserYOffset.dp.toPx()
            drawLine(
                color = Color(0xFFEF4444),
                start = Offset(cx - s * 0.28f, curLaserY),
                end = Offset(cx + s * 0.28f, curLaserY),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFCA5A5).copy(alpha = 0.6f),
                start = Offset(cx - s * 0.26f, curLaserY),
                end = Offset(cx + s * 0.26f, curLaserY),
                strokeWidth = 3.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 4 Green Bumper Corner Brackets
            val cSize = s * 0.14f
            val cCorner = 3.dp.toPx()
            val greenColor = Color(0xFF10B981)

            // Top-Left
            drawRoundRect(greenColor, topLeft = Offset(cx - s * 0.40f, cy - s * 0.40f), size = Size(cSize, cSize * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))
            drawRoundRect(greenColor, topLeft = Offset(cx - s * 0.40f, cy - s * 0.40f), size = Size(cSize * 0.5f, cSize), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))

            // Top-Right
            drawRoundRect(greenColor, topLeft = Offset(cx + s * 0.40f - cSize, cy - s * 0.40f), size = Size(cSize, cSize * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))
            drawRoundRect(greenColor, topLeft = Offset(cx + s * 0.40f - cSize * 0.5f, cy - s * 0.40f), size = Size(cSize * 0.5f, cSize), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))

            // Bottom-Left
            drawRoundRect(greenColor, topLeft = Offset(cx - s * 0.40f, cy + s * 0.40f - cSize * 0.5f), size = Size(cSize, cSize * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))
            drawRoundRect(greenColor, topLeft = Offset(cx - s * 0.40f, cy + s * 0.40f - cSize), size = Size(cSize * 0.5f, cSize), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))

            // Bottom-Right
            drawRoundRect(greenColor, topLeft = Offset(cx + s * 0.40f - cSize, cy + s * 0.40f - cSize * 0.5f), size = Size(cSize, cSize * 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))
            drawRoundRect(greenColor, topLeft = Offset(cx + s * 0.40f - cSize * 0.5f, cy + s * 0.40f - cSize), size = Size(cSize * 0.5f, cSize), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cCorner, cCorner))
        }
    }
}

/**
 * 4. Flash: Angled Tactical Torch with Glowing Illuminated Convex Lens & Green Button.
 */
@Composable
fun Realistic3DTacticalFlashlightIcon(
    torchEnabled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val pulseTransition = rememberInfiniteTransition(label = "torchPulse")
    val glowScale by pulseTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = -30f },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            if (torchEnabled) {
                // Volumetric Illuminated Light Flare
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.85f), Color(0xFF0284C7).copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(cx, cy - s * 0.35f),
                        radius = s * 0.60f * glowScale
                    ),
                    center = Offset(cx, cy - s * 0.35f),
                    radius = s * 0.60f * glowScale
                )
            }

            // Gunmetal Cylindrical Barrel
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF475569), Color(0xFF0F172A)),
                    start = Offset(cx - s * 0.10f, 0f),
                    end = Offset(cx + s * 0.10f, 0f)
                ),
                topLeft = Offset(cx - s * 0.10f, cy - s * 0.02f),
                size = Size(s * 0.20f, s * 0.46f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Knurled Grip Bands
            for (yOffset in listOf(s * 0.08f, s * 0.16f, s * 0.24f, s * 0.32f)) {
                drawLine(
                    color = Color(0xFF0F172A),
                    start = Offset(cx - s * 0.10f, cy + yOffset),
                    end = Offset(cx + s * 0.10f, cy + yOffset),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            // Bright Green Power Button on Barrel
            drawCircle(
                color = Color(0xFF10B981),
                radius = s * 0.045f,
                center = Offset(cx, cy + s * 0.12f)
            )

            // Flared Reflector Head Bezel
            val headPath = Path().apply {
                moveTo(cx - s * 0.24f, cy - s * 0.34f)
                lineTo(cx + s * 0.24f, cy - s * 0.34f)
                lineTo(cx + s * 0.14f, cy - s * 0.02f)
                lineTo(cx - s * 0.14f, cy - s * 0.02f)
                close()
            }
            drawPath(
                path = headPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF64748B), Color(0xFF0F172A)),
                    start = Offset(cx - s * 0.24f, 0f),
                    end = Offset(cx + s * 0.24f, 0f)
                )
            )

            // Convex Glowing Reflector Lens
            drawOval(
                brush = Brush.radialGradient(
                    colors = if (torchEnabled) {
                        listOf(Color.White, Color(0xFFE0F2FE), Color(0xFF38BDF8))
                    } else {
                        listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8))
                    },
                    center = Offset(cx, cy - s * 0.34f),
                    radius = s * 0.24f
                ),
                topLeft = Offset(cx - s * 0.24f, cy - s * 0.39f),
                size = Size(s * 0.48f, s * 0.10f)
            )
        }
    }
}

/** Backward-compatible alias for Flashlight icon */
@Composable
fun Realistic3DFlashlightIcon(torchEnabled: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Realistic3DTacticalFlashlightIcon(torchEnabled = torchEnabled, modifier = modifier, size = size)
}

/**
 * 5. Enter Code: Dark 3D Keypad with Blue Numbers & Green Check Button.
 */
@Composable
fun Realistic3DDigitKeypadIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val rotateTransition = rememberInfiniteTransition(label = "keypadAnim")
    val tilt by rotateTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = tilt },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Charcoal Rounded Keypad Base
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(cx - s * 0.40f, cy - s * 0.40f),
                size = Size(s * 0.80f, s * 0.80f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF475569),
                topLeft = Offset(cx - s * 0.40f, cy - s * 0.40f),
                size = Size(s * 0.80f, s * 0.80f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // 3x3 Blue buttons + bottom '0' and green check
            val btnW = s * 0.18f
            val btnH = s * 0.14f
            val gap = s * 0.05f
            val startX = cx - s * 0.24f
            val startY = cy - s * 0.28f

            for (r in 0..3) {
                for (c in 0..2) {
                    if (r == 3 && c == 0) continue // Empty slot on bottom-left
                    val bx = startX + c * (btnW + gap)
                    val by = startY + r * (btnH + gap)
                    val isCheck = (r == 3 && c == 2)

                    drawRoundRect(
                        color = if (isCheck) Color(0xFF10B981) else Color(0xFF3B82F6),
                        topLeft = Offset(bx - btnW / 2f, by - btnH / 2f),
                        size = Size(btnW, btnH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )

                    // White Indicator
                    if (isCheck) {
                        // Checkmark
                        val checkPath = Path().apply {
                            moveTo(bx - btnW * 0.25f, by)
                            lineTo(bx - btnW * 0.05f, by + btnH * 0.25f)
                            lineTo(bx + btnW * 0.25f, by - btnH * 0.25f)
                        }
                        drawPath(checkPath, Color.White, style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else {
                        drawCircle(Color.White, radius = 1.2.dp.toPx(), center = Offset(bx, by))
                    }
                }
            }
        }
    }
}

/** Backward-compatible alias for Keypad icon */
@Composable
fun Realistic3DKeypadIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Realistic3DDigitKeypadIcon(modifier = modifier, size = size)
}

/**
 * 6. Pause / Resume: Glossy Concentric Ruby Red 3D Button with Twin White Pause Bars.
 */
@Composable
fun Realistic3DPauseDiscIcon(
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val pulseTransition = rememberInfiniteTransition(label = "pausePulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPaused) 1.0f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            val outerR = s * 0.42f
            val midR = s * 0.35f
            val innerR = s * 0.28f

            // Outer Beveled Rim
            drawCircle(
                brush = Brush.linearGradient(
                    colors = if (isPaused) {
                        listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF064E3B))
                    } else {
                        listOf(Color(0xFFF87171), Color(0xFFDC2626), Color(0xFF7F1D1D))
                    },
                    start = Offset(cx - outerR, cy - outerR),
                    end = Offset(cx + outerR, cy + outerR)
                ),
                radius = outerR,
                center = Offset(cx, cy)
            )

            // Mid Beveled Groove
            drawCircle(
                color = if (isPaused) Color(0xFF047857) else Color(0xFF991B1B),
                radius = midR,
                center = Offset(cx, cy)
            )

            // Inner Glossy Button Cap
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isPaused) {
                        listOf(Color(0xFFA7F3D0), Color(0xFF10B981), Color(0xFF065F46))
                    } else {
                        listOf(Color(0xFFFCA5A5), Color(0xFFEF4444), Color(0xFFB91C1C))
                    },
                    center = Offset(cx - innerR * 0.3f, cy - innerR * 0.3f),
                    radius = innerR
                ),
                radius = innerR,
                center = Offset(cx, cy)
            )

            // White 3D Pause Bars or Play Triangle
            if (isPaused) {
                // Play Triangle
                val playPath = Path().apply {
                    moveTo(cx - s * 0.08f, cy - s * 0.15f)
                    lineTo(cx + s * 0.16f, cy)
                    lineTo(cx - s * 0.08f, cy + s * 0.15f)
                    close()
                }
                drawPath(playPath, Color.White)
            } else {
                // 2 Rounded 3D Pause Pills
                val barW = s * 0.08f
                val barH = s * 0.24f
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(cx - s * 0.12f, cy - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(cx + s * 0.04f, cy - barH / 2f),
                    size = Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

/** Backward-compatible alias for Pause / Play icon */
@Composable
fun Realistic3DPlayPauseIcon(isPaused: Boolean, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Realistic3DPauseDiscIcon(isPaused = isPaused, modifier = modifier, size = size)
}

/**
 * 7. Review Cart: Molded White Shopping Cart with Green Handle Grip & Green Hubcap Wheels.
 */
@Composable
fun Realistic3DMoldedCartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val cartTransition = rememberInfiniteTransition(label = "cart3DBounce")
    val bounceY by cartTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = bounceY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Green Handle Grip
            drawLine(
                color = Color(0xFF10B981),
                start = Offset(cx - s * 0.32f, cy - s * 0.24f),
                end = Offset(cx - s * 0.42f, cy - s * 0.34f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // White Molded Basket Frame
            val basketPath = Path().apply {
                moveTo(cx - s * 0.32f, cy - s * 0.24f)
                lineTo(cx + s * 0.30f, cy - s * 0.20f)
                lineTo(cx + s * 0.20f, cy + s * 0.14f)
                lineTo(cx - s * 0.24f, cy + s * 0.14f)
                close()
            }
            drawPath(
                path = basketPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF8FAFC), Color.White, Color(0xFFE2E8F0)),
                    start = Offset(cx - s * 0.32f, 0f),
                    end = Offset(cx + s * 0.30f, 0f)
                )
            )
            drawPath(
                path = basketPath,
                color = Color(0xFF94A3B8),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Molded Grid Cutouts
            for (c in 0..2) {
                for (r in 0..1) {
                    val gx = cx - s * 0.20f + c * (s * 0.14f)
                    val gy = cy - s * 0.14f + r * (s * 0.12f)
                    drawRect(
                        color = Color(0xFFCBD5E1),
                        topLeft = Offset(gx, gy),
                        size = Size(s * 0.08f, s * 0.06f)
                    )
                }
            }

            // Metal Under-Chassis Support Frame
            val chassisPath = Path().apply {
                moveTo(cx - s * 0.20f, cy + s * 0.14f)
                lineTo(cx - s * 0.22f, cy + s * 0.26f)
                lineTo(cx + s * 0.18f, cy + s * 0.26f)
                lineTo(cx + s * 0.16f, cy + s * 0.14f)
            }
            drawPath(chassisPath, Color(0xFF64748B), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // 4 Wheels (Dark Tire + Green Hubcap)
            val wheelY = cy + s * 0.26f
            listOf(cx - s * 0.22f, cx + s * 0.18f).forEach { wx ->
                // Dark Tire
                drawCircle(Color(0xFF1E293B), radius = s * 0.08f, center = Offset(wx, wheelY))
                // Green Hubcap
                drawCircle(Color(0xFF10B981), radius = s * 0.04f, center = Offset(wx, wheelY))
            }
        }
    }
}

/** Backward-compatible alias for Shopping Cart icon */
@Composable
fun Realistic3DShoppingCartIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Realistic3DMoldedCartIcon(modifier = modifier, size = size)
}

/**
 * 8. Current Sale: Emerald Green Tote Bag with Twin Yellow Arched Handles & Shield Checkmark.
 */
@Composable
fun Realistic3DShieldToteIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tote3DAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Twin Arched Yellow Handles
            drawArc(
                color = Color(0xFFF59E0B),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.22f, cy - s * 0.36f),
                size = Size(s * 0.32f, s * 0.36f),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFFFBBF24),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.10f, cy - s * 0.36f),
                size = Size(s * 0.32f, s * 0.36f),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )

            // Emerald Tote Bag Body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF047857)),
                    start = Offset(cx - s * 0.36f, cy - s * 0.18f),
                    end = Offset(cx + s * 0.36f, cy + s * 0.40f)
                ),
                topLeft = Offset(cx - s * 0.36f, cy - s * 0.18f),
                size = Size(s * 0.72f, s * 0.58f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // Brass Eyelets
            drawCircle(Color(0xFFD97706), radius = s * 0.04f, center = Offset(cx - s * 0.16f, cy - s * 0.12f))
            drawCircle(Color(0xFFD97706), radius = s * 0.04f, center = Offset(cx + s * 0.16f, cy - s * 0.12f))

            // Metallic Dark Shield Badge on Front Right Corner
            val sx = cx + s * 0.20f
            val sy = cy + s * 0.14f
            val sW = s * 0.20f
            val sH = s * 0.24f

            val shieldPath = Path().apply {
                moveTo(sx - sW / 2f, sy - sH / 2f)
                lineTo(sx + sW / 2f, sy - sH / 2f)
                lineTo(sx + sW / 2f, sy + sH * 0.1f)
                lineTo(sx, sy + sH / 2f)
                lineTo(sx - sW / 2f, sy + sH * 0.1f)
                close()
            }
            drawPath(shieldPath, Color(0xFF334155))
            drawPath(shieldPath, Color(0xFF94A3B8), style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // White Checkmark inside Shield
            val checkPath = Path().apply {
                moveTo(sx - sW * 0.25f, sy)
                lineTo(sx - sW * 0.05f, sy + sH * 0.18f)
                lineTo(sx + sW * 0.30f, sy - sH * 0.18f)
            }
            drawPath(checkPath, Color.White, style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Backward-compatible alias for Shopping Bag icon */
@Composable
fun Realistic3DShoppingBagIcon(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Realistic3DShieldToteIcon(modifier = modifier, size = size)
}

/**
 * Realistic 3D Product Box / Package Icon.
 */
@Composable
fun Realistic3DProductBoxIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Realistic3DStockPyramidIcon(modifier = modifier, size = size)
}

/**
 * Photorealistic 3D Animated Forward Arrow Icon for Review Cart button and CTAs.
 */
@Composable
fun Realistic3DForwardArrowIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arrow3DGlide")
    val slideX by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slideX"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationX = slideX },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Soft Ambient Drop Shadow
            val shadowPath = Path().apply {
                moveTo(cx - s * 0.38f, cy - s * 0.05f + s * 0.08f)
                lineTo(cx + s * 0.08f, cy - s * 0.05f + s * 0.08f)
                lineTo(cx + s * 0.08f, cy - s * 0.24f + s * 0.08f)
                lineTo(cx + s * 0.42f, cy + s * 0.08f)
                lineTo(cx + s * 0.08f, cy + s * 0.24f + s * 0.08f)
                lineTo(cx + s * 0.08f, cy + s * 0.05f + s * 0.08f)
                lineTo(cx - s * 0.38f, cy + s * 0.05f + s * 0.08f)
                close()
            }
            drawPath(shadowPath, Color.Black.copy(alpha = 0.22f))

            // 2. 3D Extruded Lower Depth Layer (Shadow Facet)
            val depthPath = Path().apply {
                moveTo(cx - s * 0.38f, cy - s * 0.05f + 1.5.dp.toPx())
                lineTo(cx + s * 0.08f, cy - s * 0.05f + 1.5.dp.toPx())
                lineTo(cx + s * 0.08f, cy - s * 0.24f + 1.5.dp.toPx())
                lineTo(cx + s * 0.42f, cy + 1.5.dp.toPx())
                lineTo(cx + s * 0.08f, cy + s * 0.24f + 1.5.dp.toPx())
                lineTo(cx + s * 0.08f, cy + s * 0.05f + 1.5.dp.toPx())
                lineTo(cx - s * 0.38f, cy + s * 0.05f + 1.5.dp.toPx())
                close()
            }
            drawPath(depthPath, Color(0xFF94A3B8))

            // 3. Top Face 3D Glossy Arrow
            val arrowPath = Path().apply {
                moveTo(cx - s * 0.38f, cy - s * 0.06f)
                lineTo(cx + s * 0.08f, cy - s * 0.06f)
                lineTo(cx + s * 0.08f, cy - s * 0.24f)
                lineTo(cx + s * 0.42f, cy)
                lineTo(cx + s * 0.08f, cy + s * 0.24f)
                lineTo(cx + s * 0.08f, cy + s * 0.06f)
                lineTo(cx - s * 0.38f, cy + s * 0.06f)
                close()
            }
            drawPath(
                path = arrowPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9), Color(0xFFE2E8F0)),
                    start = Offset(cx - s * 0.38f, cy - s * 0.24f),
                    end = Offset(cx + s * 0.42f, cy + s * 0.24f)
                )
            )
            drawPath(
                path = arrowPath,
                color = Color.White.copy(alpha = 0.8f),
                style = Stroke(width = 0.8.dp.toPx())
            )

            // 4. Specular Highlight Ridge on Top Edge
            drawLine(
                color = Color.White,
                start = Offset(cx - s * 0.36f, cy - s * 0.06f),
                end = Offset(cx + s * 0.08f, cy - s * 0.06f),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(cx + s * 0.08f, cy - s * 0.22f),
                end = Offset(cx + s * 0.40f, cy),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Photorealistic 3D Animated Chevron Right Icon for Header Pill and List Items.
 */
@Composable
fun Realistic3DChevronRightIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chevron3DGlide")
    val slideX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slideX"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationX = slideX },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // Shadow
            val shadowPath = Path().apply {
                moveTo(cx - s * 0.18f, cy - s * 0.32f + 1.dp.toPx())
                lineTo(cx + s * 0.20f, cy + 1.dp.toPx())
                lineTo(cx - s * 0.18f, cy + s * 0.32f + 1.dp.toPx())
            }
            drawPath(
                path = shadowPath,
                color = Color.Black.copy(alpha = 0.18f),
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // 3D Chevron Body
            val chevronPath = Path().apply {
                moveTo(cx - s * 0.18f, cy - s * 0.32f)
                lineTo(cx + s * 0.20f, cy)
                lineTo(cx - s * 0.18f, cy + s * 0.32f)
            }
            drawPath(
                path = chevronPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF0F172A)),
                    start = Offset(cx - s * 0.18f, cy - s * 0.32f),
                    end = Offset(cx + s * 0.20f, cy + s * 0.32f)
                ),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Specular Top Highlight
            drawLine(
                color = Color(0xFFA7F3D0),
                start = Offset(cx - s * 0.18f, cy - s * 0.32f),
                end = Offset(cx + s * 0.18f, cy),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Realistic 3D Receipt Paper Layout Icon for Settings.
 */
@Composable
fun Realistic3DReceiptPaperIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension

            // Background container
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                size = Size(s - 4.dp.toPx(), s - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )

            // Paper body
            drawRoundRect(
                Color.White,
                topLeft = Offset(s * 0.26f, s * 0.20f),
                size = Size(s * 0.48f, s * 0.60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Lines on paper
            drawRect(Color(0xFF6D28D9), topLeft = Offset(s * 0.32f, s * 0.28f), size = Size(s * 0.36f, s * 0.05f))
            drawRect(Color(0xFFA78BFA), topLeft = Offset(s * 0.32f, s * 0.38f), size = Size(s * 0.36f, s * 0.04f))
            drawRect(Color(0xFFA78BFA), topLeft = Offset(s * 0.32f, s * 0.46f), size = Size(s * 0.36f, s * 0.04f))
            drawRect(Color(0xFFA78BFA), topLeft = Offset(s * 0.32f, s * 0.54f), size = Size(s * 0.36f, s * 0.04f))
            drawRect(Color(0xFF6D28D9), topLeft = Offset(s * 0.32f, s * 0.64f), size = Size(s * 0.36f, s * 0.06f))
        }
    }
}

/**
 * Realistic 3D Laser Shopping Bag Icon for Sell Header.
 */
@Composable
fun Realistic3DLaserBagIcon(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_bag_anim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserProgress"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Ground Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33000000), Color.Transparent),
                    center = Offset(cx, cy + s * 0.40f),
                    radius = s * 0.40f
                ),
                topLeft = Offset(cx - s * 0.36f, cy + s * 0.34f),
                size = Size(s * 0.72f, s * 0.14f)
            )

            // 1. Left Gusset Depth
            val gussetPath = Path().apply {
                moveTo(cx - s * 0.32f, cy - s * 0.18f)
                lineTo(cx - s * 0.22f, cy - s * 0.28f)
                lineTo(cx - s * 0.22f, cy + s * 0.28f)
                lineTo(cx - s * 0.32f, cy + s * 0.36f)
                close()
            }
            drawPath(
                path = gussetPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF047857), Color(0xFF064E3B)),
                    start = Offset(cx - s * 0.32f, cy),
                    end = Offset(cx - s * 0.22f, cy)
                )
            )

            // 2. Front Bag Face
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF059669), Color(0xFF047857)),
                    start = Offset(cx - s * 0.24f, cy - s * 0.28f),
                    end = Offset(cx + s * 0.28f, cy + s * 0.32f)
                ),
                topLeft = Offset(cx - s * 0.24f, cy - s * 0.28f),
                size = Size(s * 0.52f, s * 0.60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // 3. Specular Highlight on Top Rim
            drawLine(
                color = Color.White.copy(alpha = 0.75f),
                start = Offset(cx - s * 0.22f, cy - s * 0.26f),
                end = Offset(cx + s * 0.26f, cy - s * 0.26f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 4. Embossed Lightning Bolt Emblem
            val boltPath = Path().apply {
                moveTo(cx + s * 0.04f, cy - s * 0.14f)
                lineTo(cx - s * 0.08f, cy)
                lineTo(cx + s * 0.01f, cy)
                lineTo(cx - s * 0.04f, cy + s * 0.14f)
                lineTo(cx + s * 0.08f, cy - s * 0.02f)
                lineTo(cx, cy - s * 0.02f)
                close()
            }
            drawPath(
                path = boltPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B)),
                    start = Offset(cx, cy - s * 0.14f),
                    end = Offset(cx, cy + s * 0.14f)
                )
            )

            // 5. Chrome Silver Handle
            drawArc(
                color = Color(0xFFE2E8F0),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.14f, cy - s * 0.42f),
                size = Size(s * 0.28f, s * 0.28f),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.14f, cy - s * 0.42f),
                size = Size(s * 0.28f, s * 0.28f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )

            // 6. Laser Scan Beam
            val laserY = (cy - s * 0.26f) + laserProgress * (s * 0.56f)
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0xFF34D399), Color.White, Color(0xFF34D399), Color.Transparent),
                    start = Offset(cx - s * 0.36f, laserY),
                    end = Offset(cx + s * 0.36f, laserY)
                ),
                start = Offset(cx - s * 0.36f, laserY),
                end = Offset(cx + s * 0.36f, laserY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/**
 * Realistic 3D Current Sale Tote Icon with notification badge.
 */
@Composable
fun Realistic3DCurrentSaleToteIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tote_bounce")
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = bounceY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Bag Body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF047857)),
                    start = Offset(cx - s * 0.32f, cy - s * 0.20f),
                    end = Offset(cx + s * 0.32f, cy + s * 0.36f)
                ),
                topLeft = Offset(cx - s * 0.32f, cy - s * 0.20f),
                size = Size(s * 0.64f, s * 0.56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())
            )

            // Handle
            drawArc(
                color = Color(0xFF34D399),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.16f, cy - s * 0.36f),
                size = Size(s * 0.32f, s * 0.32f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Badge Dot
            drawCircle(
                color = Color(0xFFEF4444),
                radius = s * 0.12f,
                center = Offset(cx + s * 0.22f, cy - s * 0.16f)
            )
            drawCircle(
                color = Color.White,
                radius = s * 0.12f,
                center = Offset(cx + s * 0.22f, cy - s * 0.16f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Realistic 3D Scanner Lens Icon for Sell Mode Toggle.
 */
@Composable
fun Realistic3DScannerLensIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    active: Boolean = true
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Outer Rim
            drawCircle(
                color = if (active) Color(0xFF059669) else Color(0xFF64748B),
                radius = s * 0.44f,
                center = Offset(cx, cy)
            )

            // Inner Lens Glass
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (active) listOf(Color(0xFF34D399), Color(0xFF065F46), Color(0xFF022C22))
                             else listOf(Color(0xFF94A3B8), Color(0xFF475569), Color(0xFF1E293B)),
                    center = Offset(cx - 1.dp.toPx(), cy - 1.dp.toPx()),
                    radius = s * 0.36f
                ),
                radius = s * 0.36f,
                center = Offset(cx, cy)
            )

            // Target Reticle Dot
            drawCircle(
                color = if (active) Color(0xFFA7F3D0) else Color.White,
                radius = s * 0.16f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * Realistic 3D Inventory Box Icon for Stock Mode Toggle & Item Cards.
 */
@Composable
fun Realistic3DInventoryBoxIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    active: Boolean = true
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Top Isometric Face
            val topPath = Path().apply {
                moveTo(cx, cy - s * 0.36f)
                lineTo(cx + s * 0.34f, cy - s * 0.16f)
                lineTo(cx, cy)
                lineTo(cx - s * 0.34f, cy - s * 0.16f)
                close()
            }
            drawPath(
                path = topPath,
                color = if (active) Color(0xFF93C5FD) else Color(0xFF94A3B8)
            )

            // Left Face
            val leftPath = Path().apply {
                moveTo(cx - s * 0.34f, cy - s * 0.16f)
                lineTo(cx, cy)
                lineTo(cx, cy + s * 0.36f)
                lineTo(cx - s * 0.34f, cy + s * 0.20f)
                close()
            }
            drawPath(
                path = leftPath,
                color = if (active) Color(0xFF2563EB) else Color(0xFF475569)
            )

            // Right Face
            val rightPath = Path().apply {
                moveTo(cx, cy)
                lineTo(cx + s * 0.34f, cy - s * 0.16f)
                lineTo(cx + s * 0.34f, cy + s * 0.20f)
                lineTo(cx, cy + s * 0.36f)
                close()
            }
            drawPath(
                path = rightPath,
                color = if (active) Color(0xFF3B82F6) else Color(0xFF64748B)
            )

            // Amber Tape Strip
            val tapePath = Path().apply {
                moveTo(cx - s * 0.08f, cy - s * 0.28f)
                lineTo(cx + s * 0.08f, cy - s * 0.28f)
                lineTo(cx + s * 0.08f, cy + s * 0.28f)
                lineTo(cx - s * 0.08f, cy + s * 0.28f)
                close()
            }
            drawPath(path = tapePath, color = Color(0xFFF59E0B))
        }
    }
}

/**
 * Realistic 3D Tactical Flashlight Tool Icon.
 */
@Composable
fun Realistic3DTorchIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    active: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Barrel
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF64748B), Color(0xFFE2E8F0), Color(0xFF334155)),
                    start = Offset(cx - s * 0.12f, cy),
                    end = Offset(cx + s * 0.12f, cy)
                ),
                topLeft = Offset(cx - s * 0.12f, cy - s * 0.05f),
                size = Size(s * 0.24f, s * 0.45f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Bezel Head
            val headPath = Path().apply {
                moveTo(cx - s * 0.22f, cy - s * 0.35f)
                lineTo(cx + s * 0.22f, cy - s * 0.35f)
                lineTo(cx + s * 0.14f, cy - s * 0.05f)
                lineTo(cx - s * 0.14f, cy - s * 0.05f)
                close()
            }
            drawPath(
                path = headPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF475569), Color(0xFFCBD5E1), Color(0xFF1E293B)),
                    start = Offset(cx - s * 0.22f, cy),
                    end = Offset(cx + s * 0.22f, cy)
                )
            )

            // Bulb / Glowing Lens
            drawOval(
                color = if (active) Color(0xFFFEF08A) else Color(0xFF94A3B8),
                topLeft = Offset(cx - s * 0.22f, cy - s * 0.40f),
                size = Size(s * 0.44f, s * 0.12f)
            )

            // Active Light Rays Flare
            if (active) {
                val beamPath = Path().apply {
                    moveTo(cx - s * 0.20f, cy - s * 0.38f)
                    lineTo(cx - s * 0.45f, cy - s * 0.65f)
                    lineTo(cx + s * 0.45f, cy - s * 0.65f)
                    lineTo(cx + s * 0.20f, cy - s * 0.38f)
                    close()
                }
                drawPath(
                    path = beamPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x99FEF08A), Color.Transparent),
                        start = Offset(cx, cy - s * 0.38f),
                        end = Offset(cx, cy - s * 0.65f)
                    )
                )
            }
        }
    }
}

/**
 * Realistic 3D Hologram Keypad Tool Icon.
 */
@Composable
fun Realistic3DBarcodeKeypadIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Keypad Base
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF0F172A)),
                    start = Offset(cx - s * 0.36f, cy - s * 0.36f),
                    end = Offset(cx + s * 0.36f, cy + s * 0.36f)
                ),
                topLeft = Offset(cx - s * 0.36f, cy - s * 0.36f),
                size = Size(s * 0.72f, s * 0.72f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // Barcode Display Screen
            drawRect(
                color = Color(0xFF06B6D4),
                topLeft = Offset(cx - s * 0.26f, cy - s * 0.26f),
                size = Size(s * 0.52f, s * 0.16f)
            )

            // Barcode Strips
            var xPos = cx - s * 0.22f
            while (xPos < cx + s * 0.22f) {
                drawRect(
                    color = Color(0xFF0F172A),
                    topLeft = Offset(xPos, cy - s * 0.24f),
                    size = Size(2.dp.toPx(), s * 0.12f)
                )
                xPos += 4.dp.toPx()
            }

            // Keys Matrix
            val keyOffsets = listOf(
                Offset(cx - s * 0.22f, cy - s * 0.04f),
                Offset(cx, cy - s * 0.04f),
                Offset(cx + s * 0.22f, cy - s * 0.04f),
                Offset(cx - s * 0.22f, cy + s * 0.16f),
                Offset(cx, cy + s * 0.16f),
                Offset(cx + s * 0.22f, cy + s * 0.16f)
            )
            keyOffsets.forEach { pos ->
                drawRoundRect(
                    color = Color(0xFF64748B),
                    topLeft = Offset(pos.x - 3.5.dp.toPx(), pos.y - 3.dp.toPx()),
                    size = Size(7.dp.toPx(), 6.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(pos.x - 2.dp.toPx(), pos.y - 2.dp.toPx()),
                    size = Size(4.dp.toPx(), 1.dp.toPx())
                )
            }
        }
    }
}

/**
 * Realistic 3D Pause / Resume Cyber-Orb Tool Icon.
 */
@Composable
fun Realistic3DPauseOrbIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    isPaused: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Orbital Halo Glow
            drawCircle(
                color = if (isPaused) Color(0x33F59E0B) else Color(0x3310B981),
                radius = s * 0.44f * haloPulse,
                center = Offset(cx, cy)
            )

            // Glass Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isPaused) listOf(Color(0xFFFDE047), Color(0xFFD97706), Color(0xFF78350F))
                             else listOf(Color(0xFF6EE7B7), Color(0xFF059669), Color(0xFF064E3B)),
                    center = Offset(cx - 2.dp.toPx(), cy - 2.dp.toPx()),
                    radius = s * 0.38f
                ),
                radius = s * 0.38f,
                center = Offset(cx, cy)
            )

            // Specular Highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.65f),
                radius = s * 0.12f,
                center = Offset(cx - s * 0.12f, cy - s * 0.12f)
            )

            // Symbol Inside Orb
            if (isPaused) {
                // Play Triangle
                val playPath = Path().apply {
                    moveTo(cx - s * 0.08f, cy - s * 0.14f)
                    lineTo(cx + s * 0.14f, cy)
                    lineTo(cx - s * 0.08f, cy + s * 0.14f)
                    close()
                }
                drawPath(path = playPath, color = Color.White)
            } else {
                // Pause Bars
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cx - s * 0.12f, cy - s * 0.14f),
                    size = Size(s * 0.08f, s * 0.28f)
                )
                drawRect(
                    color = Color.White,
                    topLeft = Offset(cx + s * 0.04f, cy - s * 0.14f),
                    size = Size(s * 0.08f, s * 0.28f)
                )
            }
        }
    }
}

/**
 * Realistic 3D Supermarket Shopping Cart Icon for Checkout Bar.
 */
@Composable
fun Realistic3DCheckoutCartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cart_wobble")
    val wobble by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = wobble },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Chrome Basket Mesh
            val basketPath = Path().apply {
                moveTo(cx - s * 0.36f, cy - s * 0.20f)
                lineTo(cx + s * 0.26f, cy - s * 0.20f)
                lineTo(cx + s * 0.16f, cy + s * 0.14f)
                lineTo(cx - s * 0.26f, cy + s * 0.14f)
                close()
            }
            drawPath(
                path = basketPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFCBD5E1), Color.White, Color(0xFF94A3B8)),
                    start = Offset(cx - s * 0.36f, cy),
                    end = Offset(cx + s * 0.26f, cy)
                )
            )
            drawPath(
                path = basketPath,
                color = Color(0xFF475569),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Basket Wire Grid Lines
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(cx - s * 0.18f, cy - s * 0.20f),
                end = Offset(cx - s * 0.12f, cy + s * 0.14f),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(cx, cy - s * 0.20f),
                end = Offset(cx, cy + s * 0.14f),
                strokeWidth = 1.dp.toPx()
            )

            // Amber Handle Grip
            drawLine(
                color = Color(0xFFF59E0B),
                start = Offset(cx - s * 0.36f, cy - s * 0.20f),
                end = Offset(cx - s * 0.44f, cy - s * 0.32f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Wheels
            val wheelY = cy + s * 0.26f
            listOf(cx - s * 0.18f, cx + s * 0.10f).forEach { wx ->
                drawCircle(color = Color(0xFF1E293B), radius = s * 0.08f, center = Offset(wx, wheelY))
                drawCircle(color = Color(0xFFE2E8F0), radius = s * 0.04f, center = Offset(wx, wheelY))
            }
        }
    }
}

// =========================================================================
// PHOTOREALISTIC 3D ANIMATED ICONS FOR STORE EXPENSES & CASH DRAWER
// =========================================================================

/**
 * Photorealistic 3D Cyber-Titanium POS Cash Vault / Register Icon
 */
@Composable
fun Realistic3DCashVaultRegisterIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VaultRegisterAnim")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "VaultFloat"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "VaultGlow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f + floatOffset.dp.toPx()

            // 1. Ambient Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(cx, this.size.height / 2f + s * 0.38f),
                    radius = s * 0.40f
                ),
                topLeft = Offset(cx - s * 0.38f, this.size.height / 2f + s * 0.28f),
                size = Size(s * 0.76f, s * 0.20f)
            )

            // 2. Register Metallic Base Chassis (Deep Slate Gradient)
            val chassisWidth = s * 0.82f
            val chassisHeight = s * 0.65f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF0F172A), Color(0xFF020617)),
                    start = Offset(cx - chassisWidth / 2f, cy - chassisHeight / 2f),
                    end = Offset(cx + chassisWidth / 2f, cy + chassisHeight / 2f)
                ),
                topLeft = Offset(cx - chassisWidth / 2f, cy - chassisHeight * 0.45f),
                size = Size(chassisWidth, chassisHeight),
                cornerRadius = CornerRadius(s * 0.16f, s * 0.16f)
            )

            // 3. Cyber Titanium Bevel Stroke
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.9f),
                        Color(0xFF818CF8).copy(alpha = 0.5f),
                        Color(0xFF0284C7).copy(alpha = 0.8f)
                    ),
                    start = Offset(cx - chassisWidth / 2f, cy - chassisHeight / 2f),
                    end = Offset(cx + chassisWidth / 2f, cy + chassisHeight / 2f)
                ),
                topLeft = Offset(cx - chassisWidth / 2f, cy - chassisHeight * 0.45f),
                size = Size(chassisWidth, chassisHeight),
                cornerRadius = CornerRadius(s * 0.16f, s * 0.16f),
                style = Stroke(width = 1.6.dp.toPx())
            )

            // 4. Neon Emerald Register Screen Display
            val screenWidth = s * 0.54f
            val screenHeight = s * 0.20f
            val screenTop = cy - chassisHeight * 0.35f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF064E3B), Color(0xFF022C22))
                ),
                topLeft = Offset(cx - screenWidth / 2f, screenTop),
                size = Size(screenWidth, screenHeight),
                cornerRadius = CornerRadius(s * 0.06f, s * 0.06f)
            )
            // Screen Glow Border
            drawRoundRect(
                color = Color(0xFF34D399).copy(alpha = glowAlpha),
                topLeft = Offset(cx - screenWidth / 2f, screenTop),
                size = Size(screenWidth, screenHeight),
                cornerRadius = CornerRadius(s * 0.06f, s * 0.06f),
                style = Stroke(width = 1.dp.toPx())
            )

            // Digital Screen Dot Matrix
            drawCircle(
                color = Color(0xFF34D399),
                radius = 1.4.dp.toPx(),
                center = Offset(cx - screenWidth * 0.28f, screenTop + screenHeight * 0.5f)
            )
            drawLine(
                color = Color(0xFF34D399),
                start = Offset(cx - screenWidth * 0.12f, screenTop + screenHeight * 0.5f),
                end = Offset(cx + screenWidth * 0.30f, screenTop + screenHeight * 0.5f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 5. Gold Bullion Drawer Tray Handle
            val handleWidth = s * 0.36f
            val handleTop = cy + s * 0.08f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFD97706))
                ),
                topLeft = Offset(cx - handleWidth / 2f, handleTop),
                size = Size(handleWidth, s * 0.065f),
                cornerRadius = CornerRadius(s * 0.03f, s * 0.03f)
            )

            // 6. 3D Floating Gold Coin
            val coinCenter = Offset(cx + s * 0.22f, cy - s * 0.28f + floatOffset * 0.8f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    center = coinCenter,
                    radius = s * 0.11f
                ),
                radius = s * 0.11f,
                center = coinCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = s * 0.06f,
                center = Offset(coinCenter.x - s * 0.03f, coinCenter.y - s * 0.03f)
            )
        }
    }
}

/**
 * Photorealistic 3D Emerald Banknote Float Stash Icon
 */
@Composable
fun Realistic3DCashInStashIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CashInAnim")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CashInFloat"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f + floatOffset.dp.toPx()

            // 1. Soft Emerald Shadow
            drawOval(
                color = Color(0xFF10B981).copy(alpha = 0.25f),
                topLeft = Offset(cx - s * 0.35f, this.size.height / 2f + s * 0.26f),
                size = Size(s * 0.70f, s * 0.16f)
            )

            // 2. Banknote Tier 1 (Bottom)
            drawRoundRect(
                color = Color(0xFF065F46),
                topLeft = Offset(cx - s * 0.36f, cy - s * 0.04f),
                size = Size(s * 0.72f, s * 0.32f),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )

            // 3. Banknote Tier 2 (Middle)
            drawRoundRect(
                color = Color(0xFF047857),
                topLeft = Offset(cx - s * 0.34f, cy - s * 0.14f),
                size = Size(s * 0.68f, s * 0.32f),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )

            // 4. Banknote Tier 3 (Top with Gradient)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669)),
                    start = Offset(cx - s * 0.32f, cy - s * 0.24f),
                    end = Offset(cx + s * 0.32f, cy + s * 0.10f)
                ),
                topLeft = Offset(cx - s * 0.32f, cy - s * 0.24f),
                size = Size(s * 0.64f, s * 0.32f),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )
            drawRoundRect(
                color = Color(0xFFA7F3D0).copy(alpha = 0.8f),
                topLeft = Offset(cx - s * 0.32f, cy - s * 0.24f),
                size = Size(s * 0.64f, s * 0.32f),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
                style = Stroke(width = 1.dp.toPx())
            )

            // 5. Gold Bullion Ribbon Strap
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFD97706))
                ),
                topLeft = Offset(cx - s * 0.08f, cy - s * 0.24f),
                size = Size(s * 0.16f, s * 0.32f),
                cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
            )

            // 6. Upward Emerald Indicator Arrow
            val arrowY = cy - s * 0.28f + floatOffset * 0.6f
            val arrowPath = Path().apply {
                moveTo(cx + s * 0.26f, arrowY)
                lineTo(cx + s * 0.20f, arrowY + s * 0.08f)
                lineTo(cx + s * 0.32f, arrowY + s * 0.08f)
                close()
            }
            drawPath(arrowPath, color = Color(0xFF34D399))
        }
    }
}

/**
 * Photorealistic 3D Ruby Expense Voucher Capsule Icon
 */
@Composable
fun Realistic3DExpenseReceiptIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ExpenseAnim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LaserProgress"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // 1. Soft Rose Shadow
            drawOval(
                color = Color(0xFFF43F5E).copy(alpha = 0.25f),
                topLeft = Offset(cx - s * 0.30f, this.size.height / 2f + s * 0.28f),
                size = Size(s * 0.60f, s * 0.14f)
            )

            // 2. Receipt White/Porcelain Slab
            val slipWidth = s * 0.58f
            val slipHeight = s * 0.68f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFFFF1F2), Color(0xFFFFE4E6))
                ),
                topLeft = Offset(cx - slipWidth / 2f, cy - slipHeight / 2f),
                size = Size(slipWidth, slipHeight),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )
            drawRoundRect(
                color = Color(0xFFFECDD3),
                topLeft = Offset(cx - slipWidth / 2f, cy - slipHeight / 2f),
                size = Size(slipWidth, slipHeight),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 3. Ruby Receipt Header Banner
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFFFB7185))
                ),
                topLeft = Offset(cx - slipWidth * 0.40f, cy - slipHeight * 0.42f),
                size = Size(slipWidth * 0.80f, s * 0.09f),
                cornerRadius = CornerRadius(s * 0.03f, s * 0.03f)
            )

            // 4. Receipt Dotted Lines
            listOf(0.18f, 0.02f, -0.14f).forEach { offsetYRatio ->
                drawLine(
                    color = Color(0xFF94A3B8).copy(alpha = 0.7f),
                    start = Offset(cx - slipWidth * 0.35f, cy + s * offsetYRatio),
                    end = Offset(cx + slipWidth * 0.35f, cy + s * offsetYRatio),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 5. Laser Scan Beam Line
            val laserY = (cy - slipHeight * 0.4f) + slipHeight * 0.8f * laserProgress
            drawLine(
                color = Color(0xFFF43F5E).copy(alpha = 0.85f),
                start = Offset(cx - slipWidth * 0.45f, laserY),
                end = Offset(cx + slipWidth * 0.45f, laserY),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

/**
 * Photorealistic 3D Cash Flow Velocity Ring / Meter Icon
 */
@Composable
fun Realistic3DCashFlowMeterIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FlowMeterAnim")
    val needleAngle by infiniteTransition.animateFloat(
        initialValue = -35f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "NeedleAngle"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // 1. Base Dark Ring Track
            drawCircle(
                color = Color(0xFF1E293B),
                radius = s * 0.36f,
                center = Offset(cx, cy),
                style = Stroke(width = s * 0.10f)
            )

            // 2. Inflow Arc (Emerald)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF34D399))
                ),
                startAngle = -140f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.36f, cy - s * 0.36f),
                size = Size(s * 0.72f, s * 0.72f),
                style = Stroke(width = s * 0.10f, cap = StrokeCap.Round)
            )

            // 3. Outflow Arc (Rose)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFFFB7185))
                ),
                startAngle = 10f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.36f, cy - s * 0.36f),
                size = Size(s * 0.72f, s * 0.72f),
                style = Stroke(width = s * 0.10f, cap = StrokeCap.Round)
            )

            // 4. Center Needle Indicator
            rotate(degrees = needleAngle, pivot = Offset(cx, cy)) {
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(cx, cy),
                    end = Offset(cx, cy - s * 0.28f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = Color(0xFF38BDF8), radius = s * 0.06f, center = Offset(cx, cy))
            }
        }
    }
}

/**
 * Photorealistic 3D Supplier Distribution Warehouse Hub Icon
 * Cyber logistics warehouse with metallic roof gable, neon cyan illumination,
 * and floating elevation micro-animation.
 */
@Composable
fun Realistic3DSupplierWarehouseIcon(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WarehouseFloatAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WarehouseFloat"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // 1. Ambient Ground Shadow
            drawOval(
                color = Color(0x55000000),
                topLeft = Offset(cx - s * 0.38f, s * 0.84f),
                size = Size(s * 0.76f, s * 0.12f)
            )

            // 2. Main Distribution Depot Body (Deep Slate Gradient)
            val bodyW = s * 0.76f
            val bodyH = s * 0.56f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0F172A)),
                    start = Offset(cx - bodyW / 2f, cy - bodyH / 2f),
                    end = Offset(cx + bodyW / 2f, cy + bodyH / 2f)
                ),
                topLeft = Offset(cx - bodyW / 2f, cy - s * 0.18f),
                size = Size(bodyW, bodyH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // 3. Roof Gable (Titanium Blue)
            val roofPath = Path().apply {
                moveTo(cx - s * 0.42f, cy - s * 0.18f)
                lineTo(cx, cy - s * 0.42f)
                lineTo(cx + s * 0.42f, cy - s * 0.18f)
                close()
            }
            drawPath(
                path = roofPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1))
                )
            )
            drawPath(
                path = roofPath,
                color = Color(0xFF7DD3FC),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 4. Roll-Up Shutter Door / Loading Dock
            val doorW = s * 0.34f
            val doorH = s * 0.28f
            drawRoundRect(
                color = Color(0xFF0B132B),
                topLeft = Offset(cx - doorW / 2f, cy + s * 0.08f),
                size = Size(doorW, doorH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF38BDF8),
                topLeft = Offset(cx - doorW / 2f, cy + s * 0.08f),
                size = Size(doorW, doorH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // 5. Door Slits
            for (i in 0..2) {
                val slitY = cy + s * 0.14f + i * (s * 0.07f)
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(cx - doorW * 0.35f, slitY),
                    end = Offset(cx + doorW * 0.35f, slitY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

/**
 * Photorealistic 3D Cargo Freight Truck Icon
 * Heavy delivery truck with metallic cabin, emerald cargo container, and speed aura.
 */
@Composable
fun Realistic3DCargoInflowTruckIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TruckFloatAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TruckFloat"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Ground Shadow
            drawOval(
                color = Color(0x44000000),
                topLeft = Offset(cx - s * 0.42f, s * 0.82f),
                size = Size(s * 0.84f, s * 0.10f)
            )

            // Cargo Container (Emerald / Mint gradient)
            val contW = s * 0.46f
            val contH = s * 0.42f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669)),
                    start = Offset(cx - s * 0.40f, cy - s * 0.26f),
                    end = Offset(cx + s * 0.06f, cy + s * 0.16f)
                ),
                topLeft = Offset(cx - s * 0.40f, cy - s * 0.26f),
                size = Size(contW, contH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Truck Cabin (Titanium Cyan)
            val cabW = s * 0.32f
            val cabH = s * 0.32f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                ),
                topLeft = Offset(cx + s * 0.08f, cy - s * 0.16f),
                size = Size(cabW, cabH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Windshield Glass
            drawRoundRect(
                color = Color(0xFFE0F2FE),
                topLeft = Offset(cx + s * 0.18f, cy - s * 0.14f),
                size = Size(s * 0.18f, s * 0.14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Wheels (Obsidian + Hubcap)
            val wheelY = cy + s * 0.18f
            listOf(cx - s * 0.24f, cx + s * 0.26f).forEach { wx ->
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = s * 0.11f,
                    center = Offset(wx, wheelY)
                )
                drawCircle(
                    color = Color(0xFFE2E8F0),
                    radius = s * 0.045f,
                    center = Offset(wx, wheelY)
                )
            }
        }
    }
}

/**
 * Photorealistic 3D Supplier Due Security Shield Icon
 * Gold bullion protective crest with amber lock core for credit risk audit.
 */
@Composable
fun Realistic3DSupplierDueShieldIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DueShieldAnim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DueShieldFloat"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Shield Path
            val shieldPath = Path().apply {
                moveTo(cx, cy - s * 0.40f)
                lineTo(cx + s * 0.36f, cy - s * 0.22f)
                lineTo(cx + s * 0.30f, cy + s * 0.18f)
                lineTo(cx, cy + s * 0.42f)
                lineTo(cx - s * 0.30f, cy + s * 0.18f)
                lineTo(cx - s * 0.36f, cy - s * 0.22f)
                close()
            }

            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    start = Offset(cx - s * 0.35f, cy - s * 0.4f),
                    end = Offset(cx + s * 0.35f, cy + s * 0.4f)
                )
            )
            drawPath(
                path = shieldPath,
                color = Color.White.copy(alpha = 0.8f),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Inner Lock Body
            val lockW = s * 0.26f
            val lockH = s * 0.22f
            drawRoundRect(
                color = Color(0xFF78350F),
                topLeft = Offset(cx - lockW / 2f, cy - s * 0.04f),
                size = Size(lockW, lockH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Lock Shackle
            drawArc(
                color = Color(0xFFFEF3C7),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.09f, cy - s * 0.15f),
                size = Size(s * 0.18f, s * 0.20f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * Photorealistic 3D Inward Stock Invoice Voucher Icon
 * Delivery receipt clipboard with animated laser scan beam.
 */
@Composable
fun Realistic3DInwardInvoiceIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "InvoiceScanAnim")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserProgress"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Clipboard Body
            val w = s * 0.60f
            val h = s * 0.74f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9))
                ),
                topLeft = Offset(cx - w / 2f, cy - h / 2f + s * 0.04f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFCBD5E1),
                topLeft = Offset(cx - w / 2f, cy - h / 2f + s * 0.04f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Top Metallic Clip
            val clipW = w * 0.50f
            val clipH = s * 0.12f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                ),
                topLeft = Offset(cx - clipW / 2f, cy - h / 2f - clipH * 0.3f + s * 0.04f),
                size = Size(clipW, clipH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Receipt Text Lines
            for (i in 0..2) {
                val lineY = cy - s * 0.10f + i * (s * 0.10f)
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(cx - w * 0.32f, lineY),
                    end = Offset(cx + w * 0.32f, lineY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Cyan Laser Scan Beam
            val laserY = (cy - s * 0.20f) + (s * 0.44f * laserProgress)
            drawLine(
                color = Color(0xFF38BDF8),
                start = Offset(cx - w * 0.38f, laserY),
                end = Offset(cx + w * 0.38f, laserY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Photorealistic 3D Executive Analytics Crystal Orb Icon
 * Holographic crystal orb with rotating orbital ring, depth glow, and rising neon profit arrow.
 */
@Composable
fun Realistic3DExecutiveAnalyticsOrbIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ExecOrbAnim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "OrbPulse"
    )
    val ringAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingAngle"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Orbital Ring (3D angled tilt)
            rotate(degrees = 35f + ringAngle * 0.1f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = Color(0xFFA5B4FC).copy(alpha = 0.6f * pulse),
                    topLeft = Offset(cx - s * 0.44f, cy - s * 0.18f),
                    size = Size(s * 0.88f, s * 0.36f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Crystal Orb Body (Deep indigo to electric violet radial glow)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC7D2FE),
                        Color(0xFF818CF8),
                        Color(0xFF4338CA),
                        Color(0xFF1E1B4B)
                    ),
                    center = Offset(cx - s * 0.10f, cy - s * 0.10f),
                    radius = s * 0.38f
                ),
                radius = s * 0.34f,
                center = Offset(cx, cy)
            )

            // Neon Upward Trend Line
            val path = Path().apply {
                moveTo(cx - s * 0.16f, cy + s * 0.10f)
                lineTo(cx - s * 0.04f, cy - s * 0.04f)
                lineTo(cx + s * 0.06f, cy + s * 0.02f)
                lineTo(cx + s * 0.18f, cy - s * 0.14f)
            }
            drawPath(
                path = path,
                color = Color(0xFF34D399),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Specular Glint Reflection
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = s * 0.06f,
                center = Offset(cx - s * 0.14f, cy - s * 0.14f)
            )
        }
    }
}

/**
 * Photorealistic 3D POS Register Audit Z-Report Icon
 * Modern thermal terminal with micro receipt paper tape, gold seal, and status LED.
 */
@Composable
fun Realistic3DRegisterAuditZReportIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ZReportAnim")
    val paperFloat by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PaperFloat"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Bottom Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(cx - s * 0.38f, cy + s * 0.26f),
                size = Size(s * 0.76f, s * 0.20f)
            )

            // Terminal Chassis (Deep royal indigo gradient)
            val chassisW = s * 0.70f
            val chassisH = s * 0.54f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4338CA), Color(0xFF1E1B4B))
                ),
                topLeft = Offset(cx - chassisW / 2f, cy - s * 0.12f),
                size = Size(chassisW, chassisH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFA5B4FC).copy(alpha = 0.5f),
                topLeft = Offset(cx - chassisW / 2f, cy - s * 0.12f),
                size = Size(chassisW, chassisH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // White Printed Slip Tape Rolling Out
            val paperW = s * 0.44f
            val paperH = s * 0.34f
            val paperY = cy - s * 0.38f + (paperFloat * 0.5f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFE2E8F0))
                ),
                topLeft = Offset(cx - paperW / 2f, paperY),
                size = Size(paperW, paperH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Micro-printed text lines on tape
            for (i in 0..2) {
                val lineY = paperY + s * 0.08f + i * (s * 0.08f)
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(cx - paperW * 0.35f, lineY),
                    end = Offset(cx + paperW * (if (i == 1) 0.15f else 0.35f), lineY),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Gold Audit Seal Badge
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = s * 0.09f,
                center = Offset(cx + s * 0.18f, cy + s * 0.16f)
            )
            drawCircle(
                color = Color(0xFFFEF3C7),
                radius = s * 0.09f,
                center = Offset(cx + s * 0.18f, cy + s * 0.16f),
                style = Stroke(width = 1.dp.toPx())
            )

            // Active Green Status LED
            drawCircle(
                color = Color(0xFF34D399),
                radius = s * 0.04f,
                center = Offset(cx - s * 0.22f, cy + s * 0.16f)
            )
        }
    }
}

/**
 * Photorealistic 3D Leaderboard Champion Trophy Icon
 * Metallic polished gold cup with reflective specular highlights and marble base.
 */
@Composable
fun Realistic3DLeaderboardTrophyIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TrophyAnim")
    val sheenX by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TrophySheen"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Marble Pedestal Base
            val baseW = s * 0.54f
            val baseH = s * 0.16f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF78350F), Color(0xFF451A03))
                ),
                topLeft = Offset(cx - baseW / 2f, cy + s * 0.22f),
                size = Size(baseW, baseH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Golden Stem
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFFFDE68A), Color(0xFFB45309))
                ),
                topLeft = Offset(cx - s * 0.07f, cy + s * 0.10f),
                size = Size(s * 0.14f, s * 0.14f)
            )

            // Golden Chalice Body
            val cupPath = Path().apply {
                moveTo(cx - s * 0.28f, cy - s * 0.30f)
                lineTo(cx + s * 0.28f, cy - s * 0.30f)
                quadraticTo(cx + s * 0.28f, cy + s * 0.08f, cx, cy + s * 0.14f)
                quadraticTo(cx - s * 0.28f, cy + s * 0.08f, cx - s * 0.28f, cy - s * 0.30f)
                close()
            }
            drawPath(
                path = cupPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    start = Offset(cx - s * 0.3f, cy - s * 0.3f),
                    end = Offset(cx + s * 0.3f, cy + s * 0.2f)
                )
            )

            // Cup Handles (Left & Right)
            drawArc(
                color = Color(0xFFF59E0B),
                startAngle = 120f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - s * 0.44f, cy - s * 0.24f),
                size = Size(s * 0.22f, s * 0.28f),
                style = Stroke(width = 2.dp.toPx())
            )
            drawArc(
                color = Color(0xFFF59E0B),
                startAngle = -80f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx + s * 0.22f, cy - s * 0.24f),
                size = Size(s * 0.22f, s * 0.28f),
                style = Stroke(width = 2.dp.toPx())
            )

            // Sheen Reflection Gleam
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(cx + s * sheenX, cy - s * 0.24f),
                end = Offset(cx + s * sheenX, cy + s * 0.04f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Photorealistic 3D Multi-Payment Mix Channel Stack Icon
 * Contactless smart card, digital wallet coin, and mobile channel indicator.
 */
@Composable
fun Realistic3DMultiPaymentMixIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PaymentMixAnim")
    val tilt by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PaymentTilt"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // Base Card (Blue Visa/Mastercard style)
            val cardW = s * 0.68f
            val cardH = s * 0.44f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                ),
                topLeft = Offset(cx - cardW / 2f - s * 0.08f, cy - cardH / 2f - s * 0.08f),
                size = Size(cardW, cardH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Top Overlapping Card (Emerald Green / Cashless Channel)
            rotate(degrees = tilt, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF10B981), Color(0xFF047857))
                    ),
                    topLeft = Offset(cx - cardW / 2f + s * 0.06f, cy - cardH / 2f + s * 0.06f),
                    size = Size(cardW, cardH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFF6EE7B7).copy(alpha = 0.5f),
                    topLeft = Offset(cx - cardW / 2f + s * 0.06f, cy - cardH / 2f + s * 0.06f),
                    size = Size(cardW, cardH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // Gold Smart Chip
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B))
                    ),
                    topLeft = Offset(cx - s * 0.16f, cy + s * 0.04f),
                    size = Size(s * 0.15f, s * 0.11f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }
        }
    }
}

/**
 * 📦 Photorealistic 3D Inventory Box Icon with isometric perspective, tape, and dynamic ambient lighting
 */
@Composable
fun Realistic3DInventoryBoxIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_box_rot")
    val tilt by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = tilt
                translationY = floatY
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33000000), Color.Transparent),
                    center = Offset(cx, h * 0.90f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.1f, h * 0.82f),
                size = Size(w * 0.8f, h * 0.16f)
            )

            // Front/Main Box
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFFB45309)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                topLeft = Offset(w * 0.12f, h * 0.22f),
                size = Size(w * 0.76f, h * 0.62f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Box Top Flap Highlight
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFDE68A).copy(alpha = 0.5f), Color.Transparent)
                ),
                topLeft = Offset(w * 0.12f, h * 0.22f),
                size = Size(w * 0.76f, h * 0.18f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Center Packing Tape (Cream White / Translucent)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A))
                ),
                topLeft = Offset(cx - w * 0.10f, h * 0.22f),
                size = Size(w * 0.20f, h * 0.62f)
            )

            // Tape glossy reflection
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(cx - w * 0.05f, h * 0.24f),
                end = Offset(cx - w * 0.05f, h * 0.80f),
                strokeWidth = 1.2.dp.toPx()
            )
        }
    }
}

/**
 * 📷 Photorealistic 3D Barcode Scanner Icon with laser beam glow
 */
@Composable
fun Realistic3DBarcodeScannerIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_scanner_beam")
    val beamOffset by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beamOffset"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Scanner Body / Viewfinder Frame
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4338CA))
                ),
                topLeft = Offset(w * 0.10f, h * 0.15f),
                size = Size(w * 0.80f, h * 0.70f),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Inner Viewfinder Window
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(w * 0.20f, h * 0.25f),
                size = Size(w * 0.60f, h * 0.50f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Barcode Lines inside Window
            val barY1 = h * 0.32f
            val barY2 = h * 0.68f
            val barWidths = listOf(1.5f, 2.5f, 1.2f, 2.8f, 1.5f, 2.0f)
            var curX = w * 0.28f
            barWidths.forEach { bw ->
                drawLine(
                    color = Color.White.copy(alpha = 0.85f),
                    start = Offset(curX, barY1),
                    end = Offset(curX, barY2),
                    strokeWidth = bw.dp.toPx()
                )
                curX += (bw + 2.2f).dp.toPx()
            }

            // Laser Scan Beam (Vibrant Emerald / Red Glow)
            val laserY = h * beamOffset
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFFEF4444), Color(0xFFFF7171), Color(0xFFEF4444), Color.Transparent)
                ),
                start = Offset(w * 0.16f, laserY),
                end = Offset(w * 0.84f, laserY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/**
 * ➕ Photorealistic 3D Add Product Icon with emerald cylinder and gold highlight
 */
@Composable
fun Realistic3DAddProductIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_add_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f

            // Emerald 3D Disc
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF047857)),
                    center = Offset(cx - w * 0.1f, cy - h * 0.1f),
                    radius = w * 0.48f
                ),
                radius = w * 0.44f,
                center = Offset(cx, cy)
            )

            // Top Glass Specular Arc
            drawArc(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.65f), Color.Transparent)
                ),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.12f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.40f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Bold Plus Symbol in Center
            val plusLen = w * 0.42f
            val plusThick = 2.4.dp.toPx()

            // Horizontal bar
            drawLine(
                color = Color.White,
                start = Offset(cx - plusLen / 2f, cy),
                end = Offset(cx + plusLen / 2f, cy),
                strokeWidth = plusThick,
                cap = StrokeCap.Round
            )
            // Vertical bar
            drawLine(
                color = Color.White,
                start = Offset(cx, cy - plusLen / 2f),
                end = Offset(cx, cy + plusLen / 2f),
                strokeWidth = plusThick,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Photorealistic 3D Animated Gold Folder Icon with Document Sheet and Ambient Physics.
 */
@Composable
fun Realistic3DFolderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "folder3DTransition")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "folderFloatY"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val rotY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "folderRotY"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = floatY
                rotationY = rotY
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Soft Dynamic Contact Shadow
            val shadowScale = 1f - ((-floatY) / 14f)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x45000000), Color.Transparent),
                    center = Offset(cx, cy + s * 0.38f),
                    radius = s * 0.44f * shadowScale
                ),
                topLeft = Offset(cx - s * 0.40f * shadowScale, cy + s * 0.30f),
                size = Size(s * 0.80f * shadowScale, s * 0.16f)
            )

            // 2. Back Folder Tab
            val backFolder = Path().apply {
                val left = cx - s * 0.42f
                val right = cx + s * 0.42f
                val top = cy - s * 0.38f
                val tabW = s * 0.35f
                val tabH = s * 0.12f
                val bot = cy + s * 0.32f
                val r = 4.dp.toPx()

                moveTo(left + r, top + tabH)
                lineTo(left + tabW - r, top + tabH)
                quadraticTo(left + tabW, top + tabH, left + tabW + r, top + tabH - r)
                lineTo(left + tabW + r * 2, top + r)
                quadraticTo(left + tabW + r * 3, top, left + tabW + r * 4, top)
                lineTo(right - r, top)
                quadraticTo(right, top, right, top + r)
                lineTo(right, bot - r)
                quadraticTo(right, bot, right - r, bot)
                lineTo(left + r, bot)
                quadraticTo(left, bot, left, bot - r)
                lineTo(left, top + tabH + r)
                quadraticTo(left, top + tabH, left + r, top + tabH)
                close()
            }
            drawPath(
                path = backFolder,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFFB45309), Color(0xFF78350F)),
                    start = Offset(cx, cy - s * 0.38f),
                    end = Offset(cx, cy + s * 0.32f)
                )
            )

            // 3. Crisp White Paper Document Insert
            val docLeft = cx - s * 0.32f
            val docTop = cy - s * 0.32f
            val docW = s * 0.64f
            val docH = s * 0.42f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9)),
                    start = Offset(docLeft, docTop),
                    end = Offset(docLeft + docW, docTop + docH)
                ),
                topLeft = Offset(docLeft, docTop),
                size = Size(docW, docH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            // Document text preview lines
            drawLine(Color(0xFF94A3B8), Offset(docLeft + s * 0.08f, docTop + s * 0.08f), Offset(docLeft + s * 0.38f, docTop + s * 0.08f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color(0xFFCBD5E1), Offset(docLeft + s * 0.08f, docTop + s * 0.16f), Offset(docLeft + s * 0.50f, docTop + s * 0.16f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)

            // 4. Front Golden Folder Flap
            val frontFolder = Path().apply {
                val left = cx - s * 0.44f
                val right = cx + s * 0.44f
                val top = cy - s * 0.15f
                val bot = cy + s * 0.36f
                val r = 4.5.dp.toPx()

                moveTo(left + r, top)
                lineTo(right - r, top)
                quadraticTo(right, top, right, top + r)
                lineTo(right, bot - r)
                quadraticTo(right, bot, right - r, bot)
                lineTo(left + r, bot)
                quadraticTo(left, bot, left, bot - r)
                lineTo(left, top + r)
                quadraticTo(left, top, left + r, top)
                close()
            }
            drawPath(
                path = frontFolder,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFD97706)),
                    start = Offset(cx, cy - s * 0.15f),
                    end = Offset(cx, cy + s * 0.36f)
                )
            )

            // Specular Top Rim Highlight
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.3f)),
                    start = Offset(cx - s * 0.40f, cy - s * 0.15f),
                    end = Offset(cx + s * 0.40f, cy - s * 0.15f)
                ),
                start = Offset(cx - s * 0.40f, cy - s * 0.15f),
                end = Offset(cx + s * 0.40f, cy - s * 0.15f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Photorealistic 3D Animated Instant Lightning Bolt Icon.
 * Features electric amber-gold gradient, metallic chamfer, glowing energy halo, and pulsing physics.
 */
@Composable
fun Realistic3DInstantBoltIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "instantBoltAnim")
    val pulseScale by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "boltPulse"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val glowAlpha by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "boltGlow"
        )
    } else {
        remember { mutableFloatStateOf(0.6f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Ambient Electric Glow Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFBBF24).copy(alpha = glowAlpha), Color(0xFFF59E0B).copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = s * 0.48f
                ),
                center = Offset(cx, cy),
                radius = s * 0.48f
            )

            // 2. 3D Bolt Base Path (Deep Shadow / Extrusion Layer)
            val extrudePath = Path().apply {
                moveTo(cx + s * 0.06f, cy - s * 0.42f)
                lineTo(cx - s * 0.28f, cy + s * 0.02f)
                lineTo(cx - s * 0.04f, cy + s * 0.04f)
                lineTo(cx - s * 0.18f, cy + s * 0.44f)
                lineTo(cx + s * 0.30f, cy - s * 0.02f)
                lineTo(cx + s * 0.06f, cy - s * 0.04f)
                close()
            }

            // Draw Extrusion / Dark Chamfer
            drawPath(
                path = extrudePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFB45309), Color(0xFF78350F)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                )
            )

            // 3. 3D Bolt Main Facet Path
            val mainBoltPath = Path().apply {
                moveTo(cx + s * 0.04f, cy - s * 0.40f)
                lineTo(cx - s * 0.25f, cy + s * 0.01f)
                lineTo(cx - s * 0.02f, cy + s * 0.03f)
                lineTo(cx - s * 0.15f, cy + s * 0.41f)
                lineTo(cx + s * 0.27f, cy - s * 0.03f)
                lineTo(cx + s * 0.04f, cy - s * 0.05f)
                close()
            }

            drawPath(
                path = mainBoltPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFEF08A),
                        Color(0xFFFDE047),
                        Color(0xFFF59E0B),
                        Color(0xFFD97706)
                    ),
                    start = Offset(cx - s * 0.2f, cy - s * 0.4f),
                    end = Offset(cx + s * 0.2f, cy + s * 0.4f)
                )
            )

            // 4. Specular Left Facet Highlight
            val leftShinePath = Path().apply {
                moveTo(cx + s * 0.04f, cy - s * 0.40f)
                lineTo(cx - s * 0.25f, cy + s * 0.01f)
                lineTo(cx - s * 0.02f, cy + s * 0.03f)
                lineTo(cx - s * 0.08f, cy - s * 0.18f)
                close()
            }
            drawPath(
                path = leftShinePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.15f)),
                    start = Offset(cx - s * 0.2f, cy - s * 0.35f),
                    end = Offset(cx, cy)
                )
            )

            // 5. Bright Core Glint
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                center = Offset(cx - s * 0.04f, cy - s * 0.02f),
                radius = s * 0.06f
            )
        }
    }
}

/**
 * Photorealistic 3D Animated Chronometer Schedule Clock Icon.
 * Features brushed steel bezel, deep navy chronometer dial, rotating seconds hand, and convex glass shine.
 */
@Composable
fun Realistic3DClockScheduleIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "clockScheduleAnim")
    val handRotation by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "clockHandRotation"
        )
    } else {
        remember { mutableFloatStateOf(45f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Top Crown Push Button
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE2E8F0), Color(0xFF64748B))
                ),
                topLeft = Offset(cx - s * 0.08f, cy - s * 0.48f),
                size = Size(s * 0.16f, s * 0.12f),
                cornerRadius = CornerRadius(s * 0.03f, s * 0.03f)
            )

            // 2. Outer Bezel Shadow & Rim
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF334155)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                center = Offset(cx, cy),
                radius = s * 0.42f
            )

            // 3. Metallic Chrome Ring
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFF1F5F9),
                        Color(0xFF94A3B8),
                        Color(0xFFFFFFFF),
                        Color(0xFF64748B),
                        Color(0xFFF1F5F9)
                    ),
                    center = Offset(cx, cy)
                ),
                center = Offset(cx, cy),
                radius = s * 0.39f
            )

            // 4. Dial Face (Deep Sky-to-Indigo Radial Gradient)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF0C4A6E)),
                    center = Offset(cx, cy),
                    radius = s * 0.34f
                ),
                center = Offset(cx, cy),
                radius = s * 0.34f
            )

            // 5. Hour Tick Marks (12, 3, 6, 9)
            listOf(0f, 90f, 180f, 270f).forEach { angle ->
                rotate(degrees = angle, pivot = Offset(cx, cy)) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(cx, cy - s * 0.31f),
                        end = Offset(cx, cy - s * 0.24f),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 6. Hour & Minute Static Hands
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = Offset(cx + s * 0.16f, cy - s * 0.05f),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(cx, cy),
                end = Offset(cx - s * 0.04f, cy - s * 0.20f),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 7. Rotating Animated Gold Second Hand
            rotate(degrees = handRotation, pivot = Offset(cx, cy)) {
                drawLine(
                    color = Color(0xFFF59E0B),
                    start = Offset(cx, cy + s * 0.07f),
                    end = Offset(cx, cy - s * 0.28f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Color(0xFFFBBF24),
                    center = Offset(cx, cy - s * 0.20f),
                    radius = s * 0.025f
                )
            }

            // 8. Center Pin Cap
            drawCircle(
                color = Color(0xFFF8FAFC),
                center = Offset(cx, cy),
                radius = s * 0.045f
            )
            drawCircle(
                color = Color(0xFF0284C7),
                center = Offset(cx, cy),
                radius = s * 0.02f
            )

            // 9. Convex Glass Reflection (Top Crescent)
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.0f)),
                    startY = cy - s * 0.32f,
                    endY = cy
                ),
                topLeft = Offset(cx - s * 0.28f, cy - s * 0.32f),
                size = Size(s * 0.56f, s * 0.26f)
            )
        }
    }
}

/**
 * Photorealistic 3D Animated WhatsApp Icon.
 * Features emerald green glossy chat bubble with tail, 3D receiver handset, specular reflection, and subtle breathing.
 */
@Composable
fun Realistic3DWhatsAppIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "whatsappAnim")
    val breatheScale by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "waBreathe"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = breatheScale
                scaleY = breatheScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Ambient Drop Shadow
            drawCircle(
                color = Color(0x33064E3B),
                center = Offset(cx, cy + s * 0.06f),
                radius = s * 0.44f
            )

            // 2. Chat Bubble Base with Tail
            val bubblePath = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(cx - s * 0.40f, cy - s * 0.40f, cx + s * 0.40f, cy + s * 0.40f))
                moveTo(cx - s * 0.22f, cy + s * 0.25f)
                lineTo(cx - s * 0.38f, cy + s * 0.38f)
                lineTo(cx - s * 0.12f, cy + s * 0.34f)
                close()
            }

            drawPath(
                path = bubblePath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF4ADE80),
                        Color(0xFF22C55E),
                        Color(0xFF16A34A),
                        Color(0xFF15803D)
                    ),
                    start = Offset(cx - s * 0.3f, cy - s * 0.35f),
                    end = Offset(cx + s * 0.3f, cy + s * 0.4f)
                )
            )

            // 3. Specular Curved Top Highlight
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.0f)),
                    startY = cy - s * 0.36f,
                    endY = cy
                ),
                topLeft = Offset(cx - s * 0.30f, cy - s * 0.36f),
                size = Size(s * 0.60f, s * 0.28f)
            )

            // 4. 3D Phone Handset Graphic
            val phoneHandsetPath = Path().apply {
                moveTo(cx - s * 0.18f, cy - s * 0.08f)
                cubicTo(cx - s * 0.18f, cy - s * 0.18f, cx - s * 0.08f, cy - s * 0.22f, cx + s * 0.02f, cy - s * 0.18f)
                lineTo(cx + s * 0.08f, cy - s * 0.12f)
                cubicTo(cx + s * 0.12f, cy - s * 0.08f, cx + s * 0.10f, cy - s * 0.02f, cx + s * 0.04f, cy + s * 0.02f)
                lineTo(cx - s * 0.02f, cy + s * 0.06f)
                cubicTo(cx + s * 0.02f, cy + s * 0.14f, cx + s * 0.08f, cy + s * 0.18f, cx + s * 0.16f, cy + s * 0.20f)
                lineTo(cx + s * 0.20f, cy + s * 0.14f)
                cubicTo(cx + s * 0.24f, cy + s * 0.10f, cx + s * 0.30f, cy + s * 0.12f, cx + s * 0.32f, cy + s * 0.18f)
                lineTo(cx + s * 0.30f, cy + s * 0.26f)
                cubicTo(cx + s * 0.24f, cy + s * 0.32f, cx + s * 0.12f, cy + s * 0.30f, cx - s * 0.02f, cy + s * 0.18f)
                cubicTo(cx - s * 0.14f, cy + s * 0.08f, cx - s * 0.18f, cy + s * 0.02f, cx - s * 0.18f, cy - s * 0.08f)
                close()
            }

            drawPath(
                path = phoneHandsetPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFF0FDF4)),
                    start = Offset(cx - s * 0.15f, cy - s * 0.15f),
                    end = Offset(cx + s * 0.25f, cy + s * 0.25f)
                )
            )
        }
    }
}

/**
 * Photorealistic 3D Animated SMS Send Icon.
 * Features origami paper jet / message supersonic flight with shaded undersides and gentle floating animation.
 */
@Composable
fun Realistic3DSmsSendIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "smsSendAnim")
    val floatOffset by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "planeFloat"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = floatOffset
                rotationZ = floatOffset * 1.5f
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Jet Propulsion Ambient Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF0284C7).copy(alpha = 0f)),
                    center = Offset(cx - s * 0.2f, cy + s * 0.2f),
                    radius = s * 0.4f
                ),
                center = Offset(cx - s * 0.2f, cy + s * 0.2f),
                radius = s * 0.4f
            )

            // 2. Main Top Wing Facet
            val topWing = Path().apply {
                moveTo(cx + s * 0.38f, cy - s * 0.32f) // Nose
                lineTo(cx - s * 0.36f, cy - s * 0.05f) // Left tip
                lineTo(cx - s * 0.10f, cy + s * 0.08f) // Center crease
                close()
            }
            drawPath(
                path = topWing,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    start = Offset(cx + s * 0.38f, cy - s * 0.32f),
                    end = Offset(cx - s * 0.36f, cy - s * 0.05f)
                )
            )

            // 3. Main Bottom Wing Facet
            val bottomWing = Path().apply {
                moveTo(cx + s * 0.38f, cy - s * 0.32f) // Nose
                lineTo(cx - s * 0.10f, cy + s * 0.08f) // Center crease
                lineTo(cx - s * 0.05f, cy + s * 0.36f) // Bottom tail
                close()
            }
            drawPath(
                path = bottomWing,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1E40AF)),
                    start = Offset(cx + s * 0.38f, cy - s * 0.32f),
                    end = Offset(cx - s * 0.05f, cy + s * 0.36f)
                )
            )

            // 4. Underside / Rudder Keel (Dark shaded facet)
            val keel = Path().apply {
                moveTo(cx - s * 0.10f, cy + s * 0.08f)
                lineTo(cx - s * 0.18f, cy + s * 0.24f)
                lineTo(cx - s * 0.05f, cy + s * 0.36f)
                close()
            }
            drawPath(
                path = keel,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF172554)),
                    start = Offset(cx - s * 0.10f, cy + s * 0.08f),
                    end = Offset(cx - s * 0.18f, cy + s * 0.24f)
                )
            )

            // 5. Specular Nose Ridge Glint
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0.2f)),
                    start = Offset(cx + s * 0.38f, cy - s * 0.32f),
                    end = Offset(cx - s * 0.10f, cy + s * 0.08f)
                ),
                start = Offset(cx + s * 0.38f, cy - s * 0.32f),
                end = Offset(cx - s * 0.10f, cy + s * 0.08f),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Photorealistic 3D Animated Share Nodes Icon.
 * Features spherical connected nodes with metallic links, orbital particle glow, and gentle rotation.
 */
@Composable
fun Realistic3DShareNodesIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shareNodesAnim")
    val orbitAngle by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "nodesOrbit"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            val pLeft = Offset(cx - s * 0.28f, cy)
            val pTopRight = Offset(cx + s * 0.26f, cy - s * 0.26f)
            val pBottomRight = Offset(cx + s * 0.26f, cy + s * 0.26f)

            // 1. Connection Struts (3D Cylindrical Chrome Rods)
            listOf(pLeft to pTopRight, pLeft to pBottomRight).forEach { (p1, p2) ->
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFF475569)),
                        start = p1,
                        end = p2
                    ),
                    start = p1,
                    end = p2,
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Specular Core Beam
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.2f)),
                        start = p1,
                        end = p2
                    ),
                    start = p1,
                    end = p2,
                    strokeWidth = 0.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 2. 3D Spherical Nodes
            listOf(
                Triple(pLeft, s * 0.14f, listOf(Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFF312E81))),
                Triple(pTopRight, s * 0.12f, listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF4C1D95))),
                Triple(pBottomRight, s * 0.12f, listOf(Color(0xFFC084FC), Color(0xFF9333EA), Color(0xFF581C87)))
            ).forEach { (center, radius, colors) ->
                // Drop shadow
                drawCircle(
                    color = Color(0x33000000),
                    center = Offset(center.x, center.y + radius * 0.25f),
                    radius = radius
                )
                // Sphere body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = colors,
                        center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                        radius = radius * 1.3f
                    ),
                    center = center,
                    radius = radius
                )
                // Specular glint
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    center = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f),
                    radius = radius * 0.32f
                )
            }
        }
    }
}

/**
 * Photorealistic 3D Animated Save Scheduled Reminder Bell Alarm Icon.
 * Features vibrant royal indigo clock base, dual golden dome bells, vibrating clapper, and glowing rim.
 */
@Composable
fun Realistic3DAlarmSaveIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "alarmSaveAnim")
    val clapperWobble by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(220, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "clapperWobble"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = clapperWobble * 0.4f
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Dual Golden Bells (Left & Right top corners)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFEAB308), Color(0xFF854D0E)),
                    start = Offset(cx - s * 0.38f, cy - s * 0.38f),
                    end = Offset(cx - s * 0.20f, cy - s * 0.20f)
                ),
                center = Offset(cx - s * 0.26f, cy - s * 0.26f),
                radius = s * 0.12f
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFEAB308), Color(0xFF854D0E)),
                    start = Offset(cx + s * 0.20f, cy - s * 0.38f),
                    end = Offset(cx + s * 0.38f, cy - s * 0.20f)
                ),
                center = Offset(cx + s * 0.26f, cy - s * 0.26f),
                radius = s * 0.12f
            )

            // 2. Striking Hammer (Center Top)
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(cx, cy - s * 0.25f),
                end = Offset(cx, cy - s * 0.38f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFFFEF08A),
                center = Offset(cx, cy - s * 0.38f),
                radius = s * 0.04f
            )

            // 3. Main Alarm Clock Drum Body
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFF312E81)),
                    start = Offset(cx - s * 0.3f, cy - s * 0.3f),
                    end = Offset(cx + s * 0.3f, cy + s * 0.4f)
                ),
                center = Offset(cx, cy + s * 0.02f),
                radius = s * 0.36f
            )

            // 4. White Glossy Dial Face
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9)),
                    start = Offset(cx, cy - s * 0.28f),
                    end = Offset(cx, cy + s * 0.28f)
                ),
                center = Offset(cx, cy + s * 0.02f),
                radius = s * 0.28f
            )

            // 5. Clock Hands (Indicating 10:10)
            drawLine(
                color = Color(0xFF1E1B4B),
                start = Offset(cx, cy + s * 0.02f),
                end = Offset(cx - s * 0.12f, cy - s * 0.10f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF4F46E5),
                start = Offset(cx, cy + s * 0.02f),
                end = Offset(cx + s * 0.14f, cy - s * 0.08f),
                strokeWidth = 1.4.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center Pin
            drawCircle(
                color = Color(0xFFE11D48),
                center = Offset(cx, cy + s * 0.02f),
                radius = s * 0.035f
            )

            // 6. Dual Peg Legs (Bottom Left & Right)
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(cx - s * 0.22f, cy + s * 0.32f),
                end = Offset(cx - s * 0.30f, cy + s * 0.42f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(cx + s * 0.22f, cy + s * 0.32f),
                end = Offset(cx + s * 0.30f, cy + s * 0.42f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 3D Photorealistic Animated Sparkle Gift Box Icon
 * Displays a 3D isometric emerald present box with 24K gold ribbon, top lid, specular glints, and floating physics.
 */
@Composable
fun Realistic3DSparkleGiftIcon(
    modifier: Modifier = Modifier,
    isSparkling: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gift_anim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gift_float"
    )
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gift_sparkle"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val s = minOf(w, h)
            val cx = w / 2f
            val cy = h / 2f + floatY * (s / 32f)

            // 1. Soft Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E000000), Color.Transparent),
                    center = Offset(cx, cy + s * 0.40f),
                    radius = s * 0.36f
                ),
                topLeft = Offset(cx - s * 0.36f, cy + s * 0.32f),
                size = Size(s * 0.72f, s * 0.18f)
            )

            // 2. Gift Box Base (Emerald Gradient)
            val boxLeft = cx - s * 0.28f
            val boxTop = cy - s * 0.15f
            val boxWidth = s * 0.56f
            val boxHeight = s * 0.46f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF064E3B)),
                    startY = boxTop,
                    endY = boxTop + boxHeight
                ),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )

            // 3. Top Lid (Wider Emerald Cap)
            val lidLeft = cx - s * 0.32f
            val lidTop = cy - s * 0.28f
            val lidWidth = s * 0.64f
            val lidHeight = s * 0.18f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF6EE7B7), Color(0xFF10B981), Color(0xFF047857)),
                    startY = lidTop,
                    endY = lidTop + lidHeight
                ),
                topLeft = Offset(lidLeft, lidTop),
                size = Size(lidWidth, lidHeight),
                cornerRadius = CornerRadius(s * 0.06f, s * 0.06f)
            )

            // Lid Bottom Shadow
            drawRect(
                color = Color(0x33000000),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, s * 0.04f)
            )

            // 4. Gold Ribbon Vertical (Cross)
            val ribbonWidth = s * 0.12f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFFFDE047)),
                    startX = cx - ribbonWidth / 2f,
                    endX = cx + ribbonWidth / 2f
                ),
                topLeft = Offset(cx - ribbonWidth / 2f, lidTop),
                size = Size(ribbonWidth, boxTop + boxHeight - lidTop),
                cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
            )

            // Gold Ribbon Horizontal on Lid
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFB45309)),
                    startY = lidTop + lidHeight * 0.35f,
                    endY = lidTop + lidHeight * 0.65f
                ),
                topLeft = Offset(lidLeft, lidTop + lidHeight * 0.35f),
                size = Size(lidWidth, lidHeight * 0.30f)
            )

            // 5. Gold Bow Loops (Top)
            val bowLeft = Path().apply {
                moveTo(cx, lidTop)
                cubicTo(cx - s * 0.18f, lidTop - s * 0.20f, cx - s * 0.22f, lidTop - s * 0.02f, cx, lidTop)
                close()
            }
            drawPath(
                path = bowLeft,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    center = Offset(cx - s * 0.10f, lidTop - s * 0.08f),
                    radius = s * 0.15f
                )
            )

            val bowRight = Path().apply {
                moveTo(cx, lidTop)
                cubicTo(cx + s * 0.18f, lidTop - s * 0.20f, cx + s * 0.22f, lidTop - s * 0.02f, cx, lidTop)
                close()
            }
            drawPath(
                path = bowRight,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    center = Offset(cx + s * 0.10f, lidTop - s * 0.08f),
                    radius = s * 0.15f
                )
            )

            // Center Bow Knot
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFD97706)),
                    center = Offset(cx - s * 0.01f, lidTop - s * 0.01f),
                    radius = s * 0.06f
                ),
                radius = s * 0.055f,
                center = Offset(cx, lidTop)
            )

            // 6. Sparkle Glints
            if (isSparkling) {
                val glintColor = Color.White.copy(alpha = sparkleAlpha)
                drawCircle(
                    color = glintColor,
                    center = Offset(cx + s * 0.22f, lidTop - s * 0.08f),
                    radius = s * 0.04f
                )
                drawCircle(
                    color = glintColor,
                    center = Offset(cx - s * 0.24f, boxTop + s * 0.25f),
                    radius = s * 0.03f
                )
            }
        }
    }
}

/**
 * 3D Photorealistic Minted Gold Taka Coin Icon
 */
@Composable
fun Realistic3DPriceCoinIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "coin_anim")
    val glintOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coin_glint"
    )

    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = s * 0.42f

        // Outer Rim Shadow
        drawCircle(
            color = Color(0x33000000),
            center = Offset(cx, cy + s * 0.04f),
            radius = r
        )

        // Coin Outer Bezel (Radial Specular Gold)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFEF08A), Color(0xFFFACC15), Color(0xFFCA8A04), Color(0xFF854D0E)),
                center = Offset(cx + glintOffset * s * 0.2f, cy - s * 0.1f),
                radius = r
            ),
            center = Offset(cx, cy),
            radius = r
        )

        // Inner Coin Face
        val innerR = r * 0.82f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFEF08A), Color(0xFFEAB308), Color(0xFFA16207)),
                center = Offset(cx - s * 0.05f, cy - s * 0.05f),
                radius = innerR
            ),
            center = Offset(cx, cy),
            radius = innerR
        )

        // Inner Rim Stroke
        drawCircle(
            color = Color(0xFF713F12).copy(alpha = 0.35f),
            center = Offset(cx, cy),
            radius = innerR,
            style = Stroke(width = s * 0.03f)
        )

        // Embossed Taka Symbol '৳'
        val paint = android.graphics.Paint().apply {
            color = 0xFF713F12.toInt()
            textSize = s * 0.50f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(s * 0.02f, s * 0.01f, s * 0.01f, 0xFFFEF08A.toInt())
        }
        drawContext.canvas.nativeCanvas.drawText(
            "৳",
            cx,
            cy + s * 0.18f,
            paint
        )
    }
}

/**
 * 3D Photorealistic Precision Balance Scale Icon
 */
@Composable
fun Realistic3DBalanceScaleIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scale_anim")
    val beamTilt by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_tilt"
    )

    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Central Pillar / Mast (Chrome/Brass)
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1))
            ),
            start = Offset(cx, cy - s * 0.35f),
            end = Offset(cx, cy + s * 0.35f),
            strokeWidth = s * 0.08f,
            cap = StrokeCap.Round
        )

        // Pillar Base
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF0C4A6E))
            ),
            topLeft = Offset(cx - s * 0.28f, cy + s * 0.30f),
            size = Size(s * 0.56f, s * 0.10f),
            cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
        )

        // Fulcrum Top Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFBAE6FD), Color(0xFF0284C7)),
                center = Offset(cx - s * 0.02f, cy - s * 0.35f),
                radius = s * 0.08f
            ),
            center = Offset(cx, cy - s * 0.35f),
            radius = s * 0.07f
        )

        // Tilting Beam
        val beamY = cy - s * 0.26f
        val tiltRad = Math.toRadians(beamTilt.toDouble()).toFloat()
        val cos = kotlin.math.cos(tiltRad)
        val sin = kotlin.math.sin(tiltRad)

        val halfBeam = s * 0.36f
        val leftX = cx - halfBeam * cos
        val leftY = beamY - halfBeam * sin
        val rightX = cx + halfBeam * cos
        val rightY = beamY + halfBeam * sin

        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF0284C7)),
                start = Offset(leftX, leftY),
                end = Offset(rightX, rightY)
            ),
            start = Offset(leftX, leftY),
            end = Offset(rightX, rightY),
            strokeWidth = s * 0.06f,
            cap = StrokeCap.Round
        )

        // Left Pan Strings & Bowl
        val leftPanY = leftY + s * 0.28f
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(leftX, leftY),
            end = Offset(leftX - s * 0.08f, leftPanY),
            strokeWidth = s * 0.025f
        )
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(leftX, leftY),
            end = Offset(leftX + s * 0.08f, leftPanY),
            strokeWidth = s * 0.025f
        )
        drawArc(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
            ),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(leftX - s * 0.12f, leftPanY - s * 0.02f),
            size = Size(s * 0.24f, s * 0.12f)
        )

        // Right Pan Strings & Bowl
        val rightPanY = rightY + s * 0.28f
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(rightX, rightY),
            end = Offset(rightX - s * 0.08f, rightPanY),
            strokeWidth = s * 0.025f
        )
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(rightX, rightY),
            end = Offset(rightX + s * 0.08f, rightPanY),
            strokeWidth = s * 0.025f
        )
        drawArc(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
            ),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(rightX - s * 0.12f, rightPanY - s * 0.02f),
            size = Size(s * 0.24f, s * 0.12f)
        )
    }
}

/**
 * 3D Isometric Measurement Ruler & Unit Box Icon
 */
@Composable
fun Realistic3DMeasureUnitIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Isometric Wooden Ruler (Diagonal)
        val rulerPath = Path().apply {
            moveTo(cx - s * 0.38f, cy + s * 0.18f)
            lineTo(cx + s * 0.22f, cy - s * 0.42f)
            lineTo(cx + s * 0.42f, cy - s * 0.22f)
            lineTo(cx - s * 0.18f, cy + s * 0.38f)
            close()
        }

        drawPath(
            path = rulerPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFF92400E)),
                start = Offset(cx - s * 0.38f, cy + s * 0.18f),
                end = Offset(cx + s * 0.42f, cy - s * 0.22f)
            )
        )

        // Ruler Tick Marks
        val tickCount = 6
        for (i in 0 until tickCount) {
            val t = i / (tickCount - 1f)
            val startX = (cx - s * 0.30f) + t * (s * 0.50f)
            val startY = (cy + s * 0.22f) - t * (s * 0.50f)
            val isLong = i % 2 == 0
            val tickLen = if (isLong) s * 0.09f else s * 0.05f

            drawLine(
                color = Color(0xFF78350F),
                start = Offset(startX, startY),
                end = Offset(startX + tickLen * 0.707f, startY + tickLen * 0.707f),
                strokeWidth = s * 0.025f,
                cap = StrokeCap.Round
            )
        }

        // Small Isometric Cube in Foreground
        val cubeX = cx - s * 0.22f
        val cubeY = cy + s * 0.08f
        val cubeSize = s * 0.24f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1D4ED8))
            ),
            topLeft = Offset(cubeX, cubeY),
            size = Size(cubeSize, cubeSize),
            cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
        )
    }
}

/**
 * 3D Hologram Security Shield & Warranty Seal Icon
 */
@Composable
fun Realistic3DWarrantyShieldIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_anim")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "shield_orbit"
    )

    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Shield Outer Silhouette
        val shieldPath = Path().apply {
            moveTo(cx, cy - s * 0.42f)
            cubicTo(cx + s * 0.38f, cy - s * 0.42f, cx + s * 0.40f, cy - s * 0.10f, cx + s * 0.34f, cy + s * 0.12f)
            cubicTo(cx + s * 0.26f, cy + s * 0.32f, cx + s * 0.10f, cy + s * 0.42f, cx, cy + s * 0.46f)
            cubicTo(cx - s * 0.10f, cy + s * 0.42f, cx - s * 0.26f, cy + s * 0.32f, cx - s * 0.34f, cy + s * 0.12f)
            cubicTo(cx - s * 0.40f, cy - s * 0.10f, cx - s * 0.38f, cy - s * 0.42f, cx, cy - s * 0.42f)
            close()
        }

        // Shield Body Gradient (Navy to Indigo/Sapphire)
        drawPath(
            path = shieldPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFF312E81)),
                center = Offset(cx - s * 0.08f, cy - s * 0.12f),
                radius = s * 0.48f
            )
        )

        // Shield Metallic Border
        drawPath(
            path = shieldPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFA5B4FC), Color(0xFF6366F1), Color(0xFF3730A3)),
                start = Offset(cx - s * 0.38f, cy - s * 0.42f),
                end = Offset(cx + s * 0.38f, cy + s * 0.46f)
            ),
            style = Stroke(width = s * 0.06f)
        )

        // Central Checkmark (Gold/White)
        val checkPath = Path().apply {
            moveTo(cx - s * 0.14f, cy + s * 0.02f)
            lineTo(cx - s * 0.04f, cy + s * 0.14f)
            lineTo(cx + s * 0.16f, cy - s * 0.12f)
        }
        drawPath(
            path = checkPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFEF08A), Color(0xFFFBBF24), Color(0xFFFFFFFF))
            ),
            style = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Orbiting Particle Ring
        val rad = Math.toRadians(orbitAngle.toDouble()).toFloat()
        val orbitR = s * 0.38f
        val px = cx + orbitR * kotlin.math.cos(rad)
        val py = cy + (orbitR * 0.45f) * kotlin.math.sin(rad)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFF818CF8), Color.Transparent),
                center = Offset(px, py),
                radius = s * 0.08f
            ),
            center = Offset(px, py),
            radius = s * 0.05f
        )
    }
}

/**
 * 3D Photorealistic Digital POS Calculator & Cash Subtotal Icon
 */
@Composable
fun Realistic3DSubtotalCalculatorIcon(
    modifier: Modifier = Modifier,
    isFree: Boolean = false
) {
    val primaryColor = if (isFree) Color(0xFF10B981) else Color(0xFF2563EB)
    val darkColor = if (isFree) Color(0xFF064E3B) else Color(0xFF1E3A8A)
    val screenBg = Color(0xFF0F172A)
    val ledColor = if (isFree) Color(0xFF34D399) else Color(0xFF60A5FA)

    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f

        val bodyW = s * 0.72f
        val bodyH = s * 0.88f
        val bodyLeft = cx - bodyW / 2f
        val bodyTop = cy - bodyH / 2f

        // Shadow
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(bodyLeft, bodyTop + s * 0.04f),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(s * 0.12f, s * 0.12f)
        )

        // Calculator Body (Beveled Rounded Rect)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor, darkColor),
                startY = bodyTop,
                endY = bodyTop + bodyH
            ),
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(s * 0.12f, s * 0.12f)
        )

        // Glowing LED Screen
        val screenW = bodyW * 0.80f
        val screenH = bodyH * 0.24f
        val screenLeft = cx - screenW / 2f
        val screenTop = bodyTop + bodyH * 0.10f

        drawRoundRect(
            color = screenBg,
            topLeft = Offset(screenLeft, screenTop),
            size = Size(screenW, screenH),
            cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
        )

        // LED Taka Digits
        val paint = android.graphics.Paint().apply {
            color = ledColor.hashCode()
            textSize = screenH * 0.65f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            if (isFree) "FREE" else "৳.00",
            screenLeft + screenW - s * 0.03f,
            screenTop + screenH * 0.72f,
            paint
        )

        // Keypad Grid (3x3 Buttons)
        val keyRows = 3
        val keyCols = 3
        val keyGap = s * 0.035f
        val keyW = (screenW - keyGap * (keyCols - 1)) / keyCols
        val keyH = s * 0.11f
        val keypadTop = screenTop + screenH + s * 0.06f

        for (r in 0 until keyRows) {
            for (c in 0 until keyCols) {
                val kx = screenLeft + c * (keyW + keyGap)
                val ky = keypadTop + r * (keyH + keyGap)
                val isEquals = r == keyRows - 1 && c == keyCols - 1
                val btnColor = if (isEquals) Color(0xFFF59E0B) else Color(0xFFFFFFFF).copy(alpha = 0.85f)

                drawRoundRect(
                    color = btnColor,
                    topLeft = Offset(kx, ky),
                    size = Size(keyW, keyH),
                    cornerRadius = CornerRadius(s * 0.025f, s * 0.025f)
                )
            }
        }
    }
}

/**
 * 3D Photorealistic Velocity Shopping Cart Icon
 */
@Composable
fun Realistic3DCartLaunchIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Cart Basket Path (Thick White Wireframe)
        val cartPath = Path().apply {
            moveTo(cx - s * 0.36f, cy - s * 0.28f)
            lineTo(cx - s * 0.24f, cy - s * 0.28f)
            lineTo(cx - s * 0.10f, cy + s * 0.12f)
            lineTo(cx + s * 0.28f, cy + s * 0.12f)
            lineTo(cx + s * 0.36f, cy - s * 0.16f)
            lineTo(cx - s * 0.18f, cy - s * 0.16f)
        }

        drawPath(
            path = cartPath,
            color = Color.White,
            style = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Basket Grid Lines
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(cx - s * 0.02f, cy - s * 0.16f),
            end = Offset(cx - s * 0.02f, cy + s * 0.12f),
            strokeWidth = s * 0.04f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.7f),
            start = Offset(cx + s * 0.14f, cy - s * 0.16f),
            end = Offset(cx + s * 0.14f, cy + s * 0.12f),
            strokeWidth = s * 0.04f,
            cap = StrokeCap.Round
        )

        // Chrome Wheels
        val wheelR = s * 0.08f
        drawCircle(
            color = Color(0xFFFEF08A),
            center = Offset(cx - s * 0.06f, cy + s * 0.28f),
            radius = wheelR
        )
        drawCircle(
            color = Color(0xFF0F172A),
            center = Offset(cx - s * 0.06f, cy + s * 0.28f),
            radius = wheelR * 0.45f
        )

        drawCircle(
            color = Color(0xFFFEF08A),
            center = Offset(cx + s * 0.24f, cy + s * 0.28f),
            radius = wheelR
        )
        drawCircle(
            color = Color(0xFF0F172A),
            center = Offset(cx + s * 0.24f, cy + s * 0.28f),
            radius = wheelR * 0.45f
        )
    }
}

/**
 * 3D Photorealistic Pulsing Preset Lightning Bolt Icon
 */
@Composable
fun Realistic3DPresetFlashIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "flash_anim")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash_scale"
    )

    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height) * scale
        val cx = size.width / 2f
        val cy = size.height / 2f

        val boltPath = Path().apply {
            moveTo(cx + s * 0.08f, cy - s * 0.44f)
            lineTo(cx - s * 0.28f, cy + s * 0.02f)
            lineTo(cx + s * 0.02f, cy + s * 0.02f)
            lineTo(cx - s * 0.08f, cy + s * 0.44f)
            lineTo(cx + s * 0.28f, cy - s * 0.02f)
            lineTo(cx - s * 0.02f, cy - s * 0.02f)
            close()
        }

        // Glow
        drawPath(
            path = boltPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B), Color(0xFFD97706)),
                center = Offset(cx, cy),
                radius = s * 0.44f
            )
        )
    }
}

/**
 * 3D Photorealistic Product Package Box Icon
 */
@Composable
fun Realistic3DProductPackageIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "box_float")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "box_offset"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = offsetY }
    ) {
        val s = minOf(this.size.width, this.size.height)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // Ambient ground drop shadow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x35000000), Color.Transparent),
                center = Offset(cx, cy + s * 0.40f),
                radius = s * 0.38f
            ),
            topLeft = Offset(cx - s * 0.38f, cy + s * 0.32f),
            size = Size(s * 0.76f, s * 0.16f)
        )

        // Box Main Front Face
        val boxW = s * 0.68f
        val boxH = s * 0.52f
        val boxX = cx - boxW / 2f
        val boxY = cy - s * 0.12f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFD97706), Color(0xFF92400E)),
                startY = boxY,
                endY = boxY + boxH
            ),
            topLeft = Offset(boxX, boxY),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(s * 0.06f, s * 0.06f)
        )

        // Top Lid Overhang
        val lidW = boxW * 1.06f
        val lidH = s * 0.22f
        val lidX = cx - lidW / 2f
        val lidY = boxY - s * 0.14f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFEF3C7), Color(0xFFF59E0B), Color(0xFFB45309)),
                startY = lidY,
                endY = lidY + lidH
            ),
            topLeft = Offset(lidX, lidY),
            size = Size(lidW, lidH),
            cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
        )

        // Lid Top Specular Highlight Edge
        drawLine(
            color = Color.White.copy(alpha = 0.75f),
            start = Offset(lidX + s * 0.04f, lidY + s * 0.02f),
            end = Offset(lidX + lidW - s * 0.04f, lidY + s * 0.02f),
            strokeWidth = s * 0.035f,
            cap = StrokeCap.Round
        )

        // Sealing Tape (Vertical center stripe)
        val tapeW = s * 0.16f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1)),
                startY = lidY,
                endY = boxY + boxH
            ),
            topLeft = Offset(cx - tapeW / 2f, lidY),
            size = Size(tapeW, (boxY + boxH) - lidY)
        )

        // Tape Center Seam
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = Offset(cx, lidY + s * 0.02f),
            end = Offset(cx, boxY + boxH - s * 0.02f),
            strokeWidth = s * 0.02f
        )
    }
}

/**
 * 3D Photorealistic Service Wrench & Precision Gear Icon
 */
@Composable
fun Realistic3DServiceWrenchIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wrench_anim")
    val rotAngle by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wrench_rot"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotAngle
            }
    ) {
        val s = minOf(this.size.width, this.size.height)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // 1. Background Chrome Cogwheel/Gear
        val gearR = s * 0.28f
        val gearCenter = Offset(cx - s * 0.12f, cy - s * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF94A3B8), Color(0xFF475569), Color(0xFF1E293B)),
                center = gearCenter,
                radius = gearR
            ),
            center = gearCenter,
            radius = gearR
        )
        drawCircle(
            color = Color(0xFF0F172A),
            center = gearCenter,
            radius = gearR * 0.40f
        )
        // 6 Gear Teeth
        for (i in 0 until 6) {
            val angleRad = Math.toRadians((i * 60.0)).toFloat()
            val toothX = gearCenter.x + (gearR * 1.08f) * kotlin.math.cos(angleRad)
            val toothY = gearCenter.y + (gearR * 1.08f) * kotlin.math.sin(angleRad)
            drawCircle(
                color = Color(0xFF64748B),
                center = Offset(toothX, toothY),
                radius = s * 0.055f
            )
        }

        // 2. Diagonal 3D Chrome Wrench Body (from bottom-left to top-right)
        val shaftStart = Offset(cx - s * 0.30f, cy + s * 0.30f)
        val shaftEnd = Offset(cx + s * 0.22f, cy - s * 0.22f)

        // Drop shadow
        drawLine(
            color = Color(0x35000000),
            start = Offset(shaftStart.x + s * 0.03f, shaftStart.y + s * 0.03f),
            end = Offset(shaftEnd.x + s * 0.03f, shaftEnd.y + s * 0.03f),
            strokeWidth = s * 0.14f,
            cap = StrokeCap.Round
        )

        // Main Wrench Shaft (Silver Specular Chrome)
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFCBD5E1), Color(0xFFF8FAFC), Color(0xFF64748B), Color(0xFF334155)),
                start = shaftStart,
                end = shaftEnd
            ),
            start = shaftStart,
            end = shaftEnd,
            strokeWidth = s * 0.12f,
            cap = StrokeCap.Round
        )

        // Top Wrench Head (Open Jaw)
        val headCenter = Offset(cx + s * 0.24f, cy - s * 0.24f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF94A3B8), Color(0xFF334155)),
                center = Offset(headCenter.x - s * 0.02f, headCenter.y - s * 0.02f),
                radius = s * 0.18f
            ),
            center = headCenter,
            radius = s * 0.17f
        )
        // Jaw Cutout
        drawCircle(
            color = Color(0xFF0F172A).copy(alpha = 0.9f),
            center = Offset(headCenter.x + s * 0.05f, headCenter.y - s * 0.05f),
            radius = s * 0.09f
        )

        // Bottom Ring Head
        val ringCenter = Offset(cx - s * 0.28f, cy + s * 0.28f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFF94A3B8), Color(0xFF334155)),
                center = Offset(ringCenter.x - s * 0.02f, ringCenter.y - s * 0.02f),
                radius = s * 0.16f
            ),
            center = ringCenter,
            radius = s * 0.14f
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            center = ringCenter,
            radius = s * 0.06f
        )
    }
}

/**
 * 3D Photorealistic Fast Logistics / Delivery Truck Icon
 */
@Composable
fun Realistic3DDeliveryTruckIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "truck_anim")
    val bumpOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "truck_bump"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = bumpOffset }
    ) {
        val s = minOf(this.size.width, this.size.height)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        // Ambient ground shadow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x35000000), Color.Transparent),
                center = Offset(cx, cy + s * 0.38f),
                radius = s * 0.42f
            ),
            topLeft = Offset(cx - s * 0.42f, cy + s * 0.30f),
            size = Size(s * 0.84f, s * 0.15f)
        )

        // 1. Cargo Box (Back)
        val cargoW = s * 0.46f
        val cargoH = s * 0.44f
        val cargoX = cx - s * 0.40f
        val cargoY = cy - s * 0.22f

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1D4ED8)),
                startY = cargoY,
                endY = cargoY + cargoH
            ),
            topLeft = Offset(cargoX, cargoY),
            size = Size(cargoW, cargoH),
            cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
        )

        // Cargo Speed Stripe
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(cargoX + s * 0.04f, cargoY + cargoH * 0.42f),
            size = Size(cargoW - s * 0.08f, s * 0.06f),
            cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
        )

        // 2. Truck Cab (Front)
        val cabW = s * 0.30f
        val cabH = s * 0.34f
        val cabX = cargoX + cargoW - s * 0.02f
        val cabY = cargoY + cargoH - cabH

        val cabPath = Path().apply {
            moveTo(cabX, cabY)
            lineTo(cabX + cabW * 0.55f, cabY)
            lineTo(cabX + cabW, cabY + cabH * 0.42f)
            lineTo(cabX + cabW, cabY + cabH)
            lineTo(cabX, cabY + cabH)
            close()
        }

        drawPath(
            path = cabPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1)),
                startY = cabY,
                endY = cabY + cabH
            )
        )

        // Windshield
        val glassPath = Path().apply {
            moveTo(cabX + s * 0.04f, cabY + s * 0.04f)
            lineTo(cabX + cabW * 0.50f, cabY + s * 0.04f)
            lineTo(cabX + cabW * 0.84f, cabY + cabH * 0.38f)
            lineTo(cabX + s * 0.04f, cabY + cabH * 0.38f)
            close()
        }
        drawPath(
            path = glassPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFBAE6FD), Color(0xFF0284C7)),
                start = Offset(cabX, cabY),
                end = Offset(cabX + cabW, cabY + cabH * 0.4f)
            )
        )

        // Headlight
        drawRoundRect(
            color = Color(0xFFFEF08A),
            topLeft = Offset(cabX + cabW - s * 0.03f, cabY + cabH * 0.55f),
            size = Size(s * 0.04f, s * 0.08f),
            cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
        )

        // 3. Wheels (Back and Front)
        val wheelR = s * 0.09f
        val wheelY = cargoY + cargoH + s * 0.02f
        val backWheelX = cargoX + s * 0.12f
        val frontWheelX = cabX + cabW * 0.65f

        // Back Wheel
        drawCircle(color = Color(0xFF0F172A), center = Offset(backWheelX, wheelY), radius = wheelR)
        drawCircle(color = Color(0xFFCBD5E1), center = Offset(backWheelX, wheelY), radius = wheelR * 0.52f)
        drawCircle(color = Color(0xFF0F172A), center = Offset(backWheelX, wheelY), radius = wheelR * 0.22f)

        // Front Wheel
        drawCircle(color = Color(0xFF0F172A), center = Offset(frontWheelX, wheelY), radius = wheelR)
        drawCircle(color = Color(0xFFCBD5E1), center = Offset(frontWheelX, wheelY), radius = wheelR * 0.52f)
        drawCircle(color = Color(0xFF0F172A), center = Offset(frontWheelX, wheelY), radius = wheelR * 0.22f)
    }
}

/**
 * Dynamic 3D Category Icon Component that renders in sync with the selected category
 */
@Composable
fun Realistic3DCategoryIcon(
    category: String,
    isFreeMode: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    when {
        isFreeMode || category.equals("Freebie", ignoreCase = true) || category.equals("Free Gift", ignoreCase = true) -> {
            Realistic3DSparkleGiftIcon(modifier = modifier.size(size))
        }
        category.equals("Service", ignoreCase = true) -> {
            Realistic3DServiceWrenchIcon(modifier = modifier, size = size)
        }
        category.equals("Fee", ignoreCase = true) || category.equals("Delivery", ignoreCase = true) -> {
            Realistic3DDeliveryTruckIcon(modifier = modifier, size = size)
        }
        else -> {
            Realistic3DProductPackageIcon(modifier = modifier, size = size)
        }
    }
}

