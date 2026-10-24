package com.techyfiposmeds.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1. Photorealistic 3D Handheld POS Terminal with banknotes and receipt roll (Sell screen).
 */
@Composable
fun Realistic3DSellTerminalIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sellTerminalAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x35000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.1f, h * 0.80f),
                size = Size(w * 0.8f, h * 0.18f)
            )

            // Banknote in background
            val notePath = Path().apply {
                moveTo(w * 0.08f, h * 0.45f)
                lineTo(w * 0.52f, h * 0.36f)
                lineTo(w * 0.48f, h * 0.72f)
                lineTo(w * 0.04f, h * 0.81f)
                close()
            }
            drawPath(
                notePath,
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

            // Terminal Body (Right 3D Block)
            val termPath = Path().apply {
                moveTo(w * 0.36f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.16f)
                lineTo(w * 0.84f, h * 0.82f)
                lineTo(w * 0.32f, h * 0.78f)
                close()
            }
            drawPath(
                termPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                    start = Offset(w * 0.36f, h * 0.12f),
                    end = Offset(w * 0.88f, h * 0.82f)
                )
            )

            // Screen bezel & blue glowing screen
            val screenPath = Path().apply {
                moveTo(w * 0.42f, h * 0.20f)
                lineTo(w * 0.82f, h * 0.23f)
                lineTo(w * 0.79f, h * 0.48f)
                lineTo(w * 0.39f, h * 0.45f)
                close()
            }
            drawPath(
                screenPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00D2FF), Color(0xFF0284C7)),
                    start = Offset(w * 0.42f, h * 0.20f),
                    end = Offset(w * 0.82f, h * 0.48f)
                )
            )

            // Tactile Keypad dots
            drawCircle(Color(0xFFEF4444), radius = w * 0.035f, center = Offset(w * 0.46f, h * 0.60f))
            drawCircle(Color(0xFFF59E0B), radius = w * 0.035f, center = Offset(w * 0.58f, h * 0.61f))
            drawCircle(Color(0xFF10B981), radius = w * 0.035f, center = Offset(w * 0.70f, h * 0.62f))

            // White Receipt paper slot top
            val receiptPath = Path().apply {
                moveTo(w * 0.48f, h * 0.04f)
                lineTo(w * 0.76f, h * 0.06f)
                lineTo(w * 0.74f, h * 0.13f)
                lineTo(w * 0.46f, h * 0.11f)
                close()
            }
            drawPath(receiptPath, Color(0xFFF8FAFC))
        }
    }
}

/**
 * 2. Photorealistic 3D Isometric Cardboard Parcel Box with Tape & Label (Products).
 */
@Composable
fun Realistic3DProductsBoxIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "boxAnim")
    val wobble by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "boxWobble"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = wobble },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x35000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.88f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.12f, h * 0.82f),
                size = Size(w * 0.76f, h * 0.16f)
            )

            // 3D Metallic Silver Blister Pack Base
            val foilBase = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.32f)
                lineTo(w * 0.50f, h * 0.52f)
                lineTo(w * 0.12f, h * 0.32f)
                close()
            }
            drawPath(
                foilBase,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8)),
                    start = Offset(w * 0.5f, h * 0.12f),
                    end = Offset(w * 0.5f, h * 0.52f)
                )
            )

            // Blister thickness
            val leftSide = Path().apply {
                moveTo(w * 0.12f, h * 0.32f)
                lineTo(w * 0.50f, h * 0.52f)
                lineTo(w * 0.50f, h * 0.62f)
                lineTo(w * 0.12f, h * 0.42f)
                close()
            }
            drawPath(
                leftSide,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF64748B), Color(0xFF334155)),
                    start = Offset(w * 0.12f, h * 0.32f),
                    end = Offset(w * 0.50f, h * 0.62f)
                )
            )

            val rightSide = Path().apply {
                moveTo(w * 0.50f, h * 0.52f)
                lineTo(w * 0.88f, h * 0.32f)
                lineTo(w * 0.88f, h * 0.42f)
                lineTo(w * 0.50f, h * 0.62f)
                close()
            }
            drawPath(
                rightSide,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF475569), Color(0xFF1E293B)),
                    start = Offset(w * 0.50f, h * 0.52f),
                    end = Offset(w * 0.88f, h * 0.62f)
                )
            )

            // 3D Capsule Pill Floating on top
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFEF4444), Color(0xFFDC2626)),
                    start = Offset(w * 0.32f, h * 0.50f),
                    end = Offset(w * 0.48f, h * 0.80f)
                ),
                topLeft = Offset(w * 0.28f, h * 0.52f),
                size = Size(w * 0.22f, h * 0.38f)
            )
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF059669)),
                    start = Offset(w * 0.48f, h * 0.40f),
                    end = Offset(w * 0.68f, h * 0.70f)
                ),
                topLeft = Offset(w * 0.44f, h * 0.42f),
                size = Size(w * 0.22f, h * 0.38f)
            )

            // Specular glint
            drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.04f, center = Offset(w * 0.34f, h * 0.58f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.48f))
        }
    }
}

/**
 * 3. Photorealistic 3D Antique Gold Pocket Watch with Ticking Hands (Sales History).
 */
@Composable
fun Realistic3DHistoryClockIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "clockAnim")
    val handAngle by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "handAngle"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(45f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.55f)
            val radius = w * 0.36f

            // Drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x35000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.12f, h * 0.82f),
                size = Size(w * 0.76f, h * 0.16f)
            )

            // Top Crown & Ring
            drawCircle(
                brush = Brush.linearGradient(listOf(Color(0xFFFDE047), Color(0xFFCA8A04))),
                radius = w * 0.08f,
                center = Offset(w * 0.5f, h * 0.16f)
            )
            drawCircle(Color(0xFF0F172A), radius = w * 0.04f, center = Offset(w * 0.5f, h * 0.16f))

            // Outer Metallic Gold Bezel
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFDE047),
                        Color(0xFFCA8A04),
                        Color(0xFFFEF08A),
                        Color(0xFFB45309),
                        Color(0xFFFDE047)
                    ),
                    center = center
                ),
                radius = radius,
                center = center
            )

            // Inner Dark Slate Watch Dial
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0B1120)),
                    center = center,
                    radius = radius * 0.85f
                ),
                radius = radius * 0.85f,
                center = center
            )

            // Dial markers (12, 3, 6, 9)
            drawCircle(Color(0xFFFDE047), radius = w * 0.02f, center = Offset(center.x, center.y - radius * 0.65f))
            drawCircle(Color(0xFFFDE047), radius = w * 0.02f, center = Offset(center.x + radius * 0.65f, center.y))
            drawCircle(Color(0xFFFDE047), radius = w * 0.02f, center = Offset(center.x, center.y + radius * 0.65f))
            drawCircle(Color(0xFFFDE047), radius = w * 0.02f, center = Offset(center.x - radius * 0.65f, center.y))

            // Center Pin
            drawCircle(Color(0xFFFDE047), radius = w * 0.04f, center = center)

            // Hour Hand
            drawLine(
                color = Color(0xFFFDE047),
                start = center,
                end = Offset(center.x + radius * 0.35f, center.y - radius * 0.25f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )

            // Animated Minute Hand
            val rad = Math.toRadians(handAngle.toDouble())
            val minX = (center.x + (radius * 0.60f) * Math.sin(rad)).toFloat()
            val minY = (center.y - (radius * 0.60f) * Math.cos(rad)).toFloat()
            drawLine(
                color = Color(0xFF00D2FF),
                start = center,
                end = Offset(minX, minY),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 4. Photorealistic 3D Leather Clipboard Ledger with Golden Clamp (Customer Dues / Khata).
 */
@Composable
fun Realistic3DClipboardDuesIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "clipAnim")
    val tilt by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "clipTilt"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = tilt },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Drop shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(w * 0.12f, h * 0.18f),
                size = Size(w * 0.76f, h * 0.76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f, w * 0.1f)
            )

            // Leather Backboard
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF0F172A)),
                    start = Offset(w * 0.1f, h * 0.12f),
                    end = Offset(w * 0.9f, h * 0.88f)
                ),
                topLeft = Offset(w * 0.1f, h * 0.12f),
                size = Size(w * 0.8f, h * 0.78f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f)
            )

            // Crisp White Document Sheet
            drawRoundRect(
                color = Color(0xFFFFFFFF),
                topLeft = Offset(w * 0.18f, h * 0.20f),
                size = Size(w * 0.64f, h * 0.64f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Document Ledger Lines (Blue primary header + slate text lines)
            drawLine(
                color = Color(0xFF00D2FF),
                start = Offset(w * 0.26f, h * 0.34f),
                end = Offset(w * 0.74f, h * 0.34f),
                strokeWidth = w * 0.05f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(w * 0.26f, h * 0.46f),
                end = Offset(w * 0.66f, h * 0.46f),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(w * 0.26f, h * 0.58f),
                end = Offset(w * 0.70f, h * 0.58f),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF10B981),
                start = Offset(w * 0.26f, h * 0.70f),
                end = Offset(w * 0.52f, h * 0.70f),
                strokeWidth = w * 0.035f,
                cap = StrokeCap.Round
            )

            // Metallic Gold Clamp Top
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE047), Color(0xFFD97706)),
                    start = Offset(w * 0.32f, h * 0.06f),
                    end = Offset(w * 0.68f, h * 0.18f)
                ),
                topLeft = Offset(w * 0.32f, h * 0.06f),
                size = Size(w * 0.36f, h * 0.14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f)
            )
        }
    }
}

/**
 * 5. Photorealistic 3D Extruded Neon Electric Zap Bolt (More Hub).
 */
@Composable
fun Realistic3DMoreZapIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "zapAnim")
    val pulse by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "zapPulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x50F59E0B), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.40f
                ),
                topLeft = Offset(w * 0.15f, h * 0.84f),
                size = Size(w * 0.70f, h * 0.16f)
            )

            // Extruded 3D Shadow layer
            val shadowPath = Path().apply {
                moveTo(w * 0.58f, h * 0.08f)
                lineTo(w * 0.26f, h * 0.50f)
                lineTo(w * 0.50f, h * 0.50f)
                lineTo(w * 0.42f, h * 0.94f)
                lineTo(w * 0.78f, h * 0.44f)
                lineTo(w * 0.54f, h * 0.44f)
                close()
            }
            drawPath(shadowPath, Color(0xFFB45309))

            // Primary Neon Glowing Bolt
            val frontPath = Path().apply {
                moveTo(w * 0.55f, h * 0.04f)
                lineTo(w * 0.22f, h * 0.46f)
                lineTo(w * 0.48f, h * 0.46f)
                lineTo(w * 0.38f, h * 0.90f)
                lineTo(w * 0.76f, h * 0.40f)
                lineTo(w * 0.50f, h * 0.40f)
                close()
            }
            drawPath(
                frontPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFFACC15), Color(0xFFEA580C)),
                    start = Offset(w * 0.55f, h * 0.04f),
                    end = Offset(w * 0.38f, h * 0.90f)
                )
            )
        }
    }
}

/**
 * 6. Photorealistic 3D Glass Bar Chart with Trend Arrow (Reports).
 */
@Composable
fun Realistic3DReportChartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reportAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "reportFloat"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Bar 1 (Red/Rose)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFB7185), Color(0xFFE11D48))),
                topLeft = Offset(w * 0.12f, h * 0.52f),
                size = Size(w * 0.20f, h * 0.38f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )
            // Bar 2 (Amber)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706))),
                topLeft = Offset(w * 0.40f, h * 0.34f),
                size = Size(w * 0.20f, h * 0.56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )
            // Bar 3 (Emerald Green)
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669))),
                topLeft = Offset(w * 0.68f, h * 0.16f),
                size = Size(w * 0.20f, h * 0.74f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Rising Cyan Trend Line
            val trend = Path().apply {
                moveTo(w * 0.12f, h * 0.45f)
                cubicTo(w * 0.35f, h * 0.30f, w * 0.55f, h * 0.25f, w * 0.88f, h * 0.08f)
            }
            drawPath(
                trend,
                color = Color(0xFF00D2FF),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 7. Photorealistic 3D Cash Banknotes Stack with Flying Wings (Expenses & Cash In/Out).
 */
@Composable
fun Realistic3DExpensesCashIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cashAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cashFloat"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Banknote Body
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF047857)),
                    start = Offset(w * 0.15f, h * 0.30f),
                    end = Offset(w * 0.85f, h * 0.70f)
                ),
                topLeft = Offset(w * 0.15f, h * 0.30f),
                size = Size(w * 0.70f, h * 0.40f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f)
            )

            // Center Dark Emblem
            drawCircle(Color(0xFF065F46), radius = w * 0.10f, center = Offset(w * 0.5f, h * 0.50f))

            // White currency curve lines
            drawLine(
                color = Color(0xFFA7F3D0),
                start = Offset(w * 0.22f, h * 0.38f),
                end = Offset(w * 0.22f, h * 0.62f),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFA7F3D0),
                start = Offset(w * 0.78f, h * 0.38f),
                end = Offset(w * 0.78f, h * 0.62f),
                strokeWidth = w * 0.04f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 8. Photorealistic 3D Delivery Freight Truck (Suppliers & Vendors).
 */
@Composable
fun Realistic3DSupplierTruckIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Cargo Container Box
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFC2410C))),
                topLeft = Offset(w * 0.10f, h * 0.25f),
                size = Size(w * 0.48f, h * 0.45f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Cabin Front
            val cabin = Path().apply {
                moveTo(w * 0.58f, h * 0.38f)
                lineTo(w * 0.78f, h * 0.38f)
                lineTo(w * 0.90f, h * 0.52f)
                lineTo(w * 0.90f, h * 0.70f)
                lineTo(w * 0.58f, h * 0.70f)
                close()
            }
            drawPath(
                cabin,
                brush = Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1)))
            )

            // Wheels
            drawCircle(Color(0xFF0F172A), radius = w * 0.09f, center = Offset(w * 0.25f, h * 0.74f))
            drawCircle(Color(0xFF94A3B8), radius = w * 0.04f, center = Offset(w * 0.25f, h * 0.74f))
            drawCircle(Color(0xFF0F172A), radius = w * 0.09f, center = Offset(w * 0.78f, h * 0.74f))
            drawCircle(Color(0xFF94A3B8), radius = w * 0.04f, center = Offset(w * 0.78f, h * 0.74f))
        }
    }
}

/**
 * 9. Photorealistic 3D Interlocking Metallic Gears (Settings & Hardware).
 */
@Composable
fun Realistic3DSettingsGearIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gearAnim")
    val rot by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gearRot"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rot },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)

            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFF00D2FF), Color(0xFF0284C7), Color(0xFF67E8F9), Color(0xFF00D2FF)),
                    center = center
                ),
                radius = w * 0.38f,
                center = center
            )
            drawCircle(Color(0xFF0B1120), radius = w * 0.16f, center = center)
        }
    }
}

/**
 * 10. Photorealistic 3D Thermal Receipt Roll (Day Close / Register).
 */
@Composable
fun Realistic3DRegisterReceiptIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Receipt Paper Sheet
            drawRoundRect(
                color = Color(0xFFFFFFFF),
                topLeft = Offset(w * 0.20f, h * 0.10f),
                size = Size(w * 0.60f, h * 0.80f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Dashed lines / receipt text
            drawLine(Color(0xFF00D2FF), start = Offset(w * 0.30f, h * 0.25f), end = Offset(w * 0.70f, h * 0.25f), strokeWidth = w * 0.04f)
            drawLine(Color(0xFF94A3B8), start = Offset(w * 0.30f, h * 0.38f), end = Offset(w * 0.60f, h * 0.38f), strokeWidth = w * 0.03f)
            drawLine(Color(0xFF94A3B8), start = Offset(w * 0.30f, h * 0.50f), end = Offset(w * 0.65f, h * 0.50f), strokeWidth = w * 0.03f)
            drawLine(Color(0xFF10B981), start = Offset(w * 0.30f, h * 0.68f), end = Offset(w * 0.70f, h * 0.68f), strokeWidth = w * 0.05f)
        }
    }
}

/**
 * 11. Photorealistic 3D Animated Sparkle Crystal Star for Ad-Hoc Custom Sale Items.
 */
@Composable
fun Realistic3DCustomItemIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "customItemSparkleAnim")
    val shimmerPhase by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sparkleShimmer"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val pulseScale by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f
            val cy = h * 0.5f

            // 1. Ambient Glow Ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.35f),
                        Color(0xFFEC4899).copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.55f * pulseScale
                ),
                center = Offset(cx, cy),
                radius = w * 0.55f * pulseScale
            )

            // 2. 4-Point Diamond Sparkle Path
            val sparkle = Path().apply {
                moveTo(cx, cy - h * 0.44f * pulseScale)
                cubicTo(cx, cy - h * 0.12f, cx + w * 0.12f, cy, cx + w * 0.44f * pulseScale, cy)
                cubicTo(cx + w * 0.12f, cy, cx, cy + h * 0.12f, cx, cy + h * 0.44f * pulseScale)
                cubicTo(cx, cy + h * 0.12f, cx - w * 0.12f, cy, cx - w * 0.44f * pulseScale, cy)
                cubicTo(cx - w * 0.12f, cy, cx, cy - h * 0.12f, cx, cy - h * 0.44f * pulseScale)
                close()
            }

            // Dynamic Gradient Shift
            val colors = listOf(
                Color(0xFF38BDF8),
                Color(0xFF818CF8),
                Color(0xFFF43F5E),
                Color(0xFFFDE047),
                Color(0xFF38BDF8)
            )
            val startX = w * shimmerPhase
            val startY = 0f
            val endX = startX + w
            val endY = h

            drawPath(
                path = sparkle,
                brush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY)
                )
            )

            // 3. Specular Ridge Lines
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(cx, cy - h * 0.35f * pulseScale),
                end = Offset(cx, cy + h * 0.35f * pulseScale),
                strokeWidth = 1.2f.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(cx - w * 0.35f * pulseScale, cy),
                end = Offset(cx + w * 0.35f * pulseScale, cy),
                strokeWidth = 1.2f.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 4. Central Crystal Specular Point
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx() * pulseScale,
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * 12. Photorealistic 3D Golden Loyalty Star Badge.
 */
@Composable
fun Realistic3DLoyaltyStarIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFFCA8A04), Color(0xFFFDE047)),
                    center = Offset(w * 0.5f, h * 0.5f)
                ),
                radius = w * 0.40f,
                center = Offset(w * 0.5f, h * 0.5f)
            )
            drawCircle(Color(0xFF0F172A), radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.5f))
            drawCircle(Color(0xFFFDE047), radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.5f))
        }
    }
}

/**
 * 13. Photorealistic 3D Chrome Supermarket Shopping Cart with Floating Bounce Animation.
 */
@Composable
fun Realistic3DSupermarketCartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cartAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cartFloat"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Cart Wire Basket
            val basket = Path().apply {
                moveTo(w * 0.10f, h * 0.22f)
                lineTo(w * 0.88f, h * 0.22f)
                lineTo(w * 0.74f, h * 0.65f)
                lineTo(w * 0.22f, h * 0.65f)
                close()
            }
            drawPath(
                basket,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFCBD5E1)),
                    start = Offset(w * 0.10f, h * 0.22f),
                    end = Offset(w * 0.88f, h * 0.65f)
                )
            )

            // Inner translucent wire grid
            drawRect(
                color = Color(0x33000000),
                topLeft = Offset(w * 0.22f, h * 0.30f),
                size = Size(w * 0.50f, h * 0.26f)
            )

            // Handle
            drawLine(
                color = Color(0xFFFFFFFF),
                start = Offset(w * 0.85f, h * 0.22f),
                end = Offset(w * 0.95f, h * 0.12f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )

            // Dual Golden Wheels
            drawCircle(Color(0xFFFDE047), radius = w * 0.09f, center = Offset(w * 0.32f, h * 0.82f))
            drawCircle(Color(0xFF78350F), radius = w * 0.04f, center = Offset(w * 0.32f, h * 0.82f))

            drawCircle(Color(0xFFFDE047), radius = w * 0.09f, center = Offset(w * 0.68f, h * 0.82f))
            drawCircle(Color(0xFF78350F), radius = w * 0.04f, center = Offset(w * 0.68f, h * 0.82f))
        }
    }
}

/**
 * 14. Photorealistic 3D Kraft Paper Stock Box with Packing Tape.
 */
@Composable
fun Realistic3DKraftStockBoxIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Top Face (Lighter Cardboard)
            val topFace = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.88f, h * 0.30f)
                lineTo(w * 0.50f, h * 0.48f)
                lineTo(w * 0.12f, h * 0.30f)
                close()
            }
            drawPath(
                topFace,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
                    start = Offset(w * 0.5f, h * 0.12f),
                    end = Offset(w * 0.5f, h * 0.48f)
                )
            )

            // Left Face (Medium Cardboard)
            val leftFace = Path().apply {
                moveTo(w * 0.12f, h * 0.30f)
                lineTo(w * 0.50f, h * 0.48f)
                lineTo(w * 0.50f, h * 0.86f)
                lineTo(w * 0.12f, h * 0.68f)
                close()
            }
            drawPath(
                leftFace,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFFB45309)),
                    start = Offset(w * 0.12f, h * 0.30f),
                    end = Offset(w * 0.50f, h * 0.86f)
                )
            )

            // Right Face (Deep Shadow Cardboard)
            val rightFace = Path().apply {
                moveTo(w * 0.50f, h * 0.48f)
                lineTo(w * 0.88f, h * 0.30f)
                lineTo(w * 0.88f, h * 0.68f)
                lineTo(w * 0.50f, h * 0.86f)
                close()
            }
            drawPath(
                rightFace,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFB45309), Color(0xFF78350F)),
                    start = Offset(w * 0.50f, h * 0.48f),
                    end = Offset(w * 0.88f, h * 0.86f)
                )
            )

            // Tape Highlight
            drawLine(
                color = Color(0x88FFFFFF),
                start = Offset(w * 0.50f, h * 0.16f),
                end = Offset(w * 0.50f, h * 0.44f),
                strokeWidth = w * 0.12f
            )
        }
    }
}

/**
 * 15. Photorealistic 3D Holographic Barcode/QR Stamp with Laser Sweep.
 */
@Composable
fun Realistic3DHologramLaserCodeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laserAnim")
    val laserOffset by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "laserSweep"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.5f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Holographic Card Plate
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                topLeft = Offset(w * 0.06f, h * 0.06f),
                size = Size(w * 0.88f, h * 0.88f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.18f, w * 0.18f)
            )

            // Barcode 5-Line Matrix (Thick, Thin, Med, Thin, Thick)
            drawLine(Color(0xFFFFFFFF), start = Offset(w * 0.22f, h * 0.20f), end = Offset(w * 0.22f, h * 0.80f), strokeWidth = w * 0.09f)
            drawLine(Color(0xFF93C5FD), start = Offset(w * 0.36f, h * 0.20f), end = Offset(w * 0.36f, h * 0.80f), strokeWidth = w * 0.05f)
            drawLine(Color(0xFFFFFFFF), start = Offset(w * 0.50f, h * 0.20f), end = Offset(w * 0.50f, h * 0.80f), strokeWidth = w * 0.07f)
            drawLine(Color(0xFF93C5FD), start = Offset(w * 0.64f, h * 0.20f), end = Offset(w * 0.64f, h * 0.80f), strokeWidth = w * 0.05f)
            drawLine(Color(0xFFFFFFFF), start = Offset(w * 0.78f, h * 0.20f), end = Offset(w * 0.78f, h * 0.80f), strokeWidth = w * 0.09f)

            // Animated Cyan-White Laser Sweep Beam
            val laserX = w * laserOffset
            if (laserX in 0f..w) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x9967E8F9),
                            Color(0xFFFFFFFF),
                            Color(0x9967E8F9),
                            Color.Transparent
                        ),
                        startX = laserX - w * 0.22f,
                        endX = laserX + w * 0.22f
                    ),
                    start = Offset(laserX, h * 0.06f),
                    end = Offset(laserX, h * 0.94f),
                    strokeWidth = w * 0.14f
                )
            }
        }
    }
}

/**
 * 16. Photorealistic 3D Chrome Triple Orbs (More Options).
 */
@Composable
fun Realistic3DChromePearlsIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val radius = w * 0.09f
            val centers = listOf(
                Offset(w * 0.5f, h * 0.25f),
                Offset(w * 0.5f, h * 0.50f),
                Offset(w * 0.5f, h * 0.75f)
            )

            centers.forEach { center ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFF94A3B8), Color(0xFF334155), Color(0xFF0F172A)),
                        center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                        radius = radius * 1.3f
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
    }
}

/**
 * 17. Photorealistic 3D Optical Glass Magnifier.
 */
@Composable
fun Realistic3DOpticalMagnifierIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Handle
            drawLine(
                brush = Brush.linearGradient(listOf(Color(0xFF64748B), Color(0xFF1E293B))),
                start = Offset(w * 0.65f, h * 0.65f),
                end = Offset(w * 0.92f, h * 0.92f),
                strokeWidth = w * 0.12f,
                cap = StrokeCap.Round
            )

            // Glass Lens
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xEEFFFFFF), Color(0x66CBD5E1), Color(0x2294A3B8)),
                    center = Offset(w * 0.35f, h * 0.35f),
                    radius = w * 0.38f
                ),
                radius = w * 0.32f,
                center = Offset(w * 0.42f, h * 0.42f)
            )

            // Chrome Rim
            drawCircle(
                color = Color(0xFF475569),
                radius = w * 0.32f,
                center = Offset(w * 0.42f, h * 0.42f),
                style = Stroke(width = w * 0.08f)
            )
        }
    }
}

/**
 * 18. Photorealistic 3D Animated Hardware Barcode Scanner Gun with Emitting Laser Beam.
 */
@Composable
fun Realistic3DHardwareBarcodeScannerIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hardwareScannerAnim")
    val laserPulse by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "laserPulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.85f) }
    }
    val laserSweep by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "laserSweep"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }
    val floatBob by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatBob"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatBob },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x40000000), Color.Transparent),
                    center = Offset(w * 0.48f, h * 0.90f),
                    radius = w * 0.42f
                ),
                topLeft = Offset(w * 0.12f, h * 0.82f),
                size = Size(w * 0.72f, h * 0.16f)
            )

            // 2. Emitted Animated 3D Laser Fan & Line (Behind gun snout)
            val laserAlpha = (0.65f * laserPulse).coerceIn(0f, 1f)
            val laserGlowPath = Path().apply {
                moveTo(w * 0.22f, h * 0.36f)
                lineTo(w * 0.02f, h * 0.20f + laserSweep)
                lineTo(w * 0.02f, h * 0.52f + laserSweep)
                close()
            }
            drawPath(
                path = laserGlowPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFEF4444).copy(alpha = laserAlpha * 0.8f),
                        Color(0xFFDC2626).copy(alpha = laserAlpha * 0.35f),
                        Color.Transparent
                    ),
                    startX = w * 0.22f,
                    endX = 0f
                )
            )
            // Focused Sharp Red Laser Core Line
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFF9999).copy(alpha = laserAlpha),
                        Color(0xFFFF0033).copy(alpha = laserAlpha * 0.9f),
                        Color.Transparent
                    ),
                    startX = w * 0.24f,
                    endX = 0f
                ),
                start = Offset(w * 0.24f, h * 0.36f),
                end = Offset(0f, h * 0.36f + laserSweep * 0.7f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )

            // 3. Ergonomic Pistol Handle / Grip (3D angled downwards to bottom-right)
            val handlePath = Path().apply {
                moveTo(w * 0.50f, h * 0.44f)
                lineTo(w * 0.68f, h * 0.84f)
                lineTo(w * 0.54f, h * 0.88f)
                lineTo(w * 0.38f, h * 0.52f)
                close()
            }
            drawPath(
                path = handlePath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617)),
                    start = Offset(w * 0.38f, h * 0.44f),
                    end = Offset(w * 0.68f, h * 0.88f)
                )
            )

            // Rubberized Grip Ribs / Texture
            for (i in 1..3) {
                val offsetRatio = 0.56f + i * 0.08f
                drawLine(
                    color = Color(0xFF334155),
                    start = Offset(w * (0.42f + i * 0.05f), h * offsetRatio),
                    end = Offset(w * (0.50f + i * 0.05f), h * (offsetRatio + 0.02f)),
                    strokeWidth = w * 0.035f,
                    cap = StrokeCap.Round
                )
            }

            // 4. Trigger Switch (Inner trigger well)
            val triggerPath = Path().apply {
                moveTo(w * 0.41f, h * 0.46f)
                quadraticTo(w * 0.36f, h * 0.52f, w * 0.42f, h * 0.58f)
                lineTo(w * 0.45f, h * 0.54f)
                close()
            }
            drawPath(
                path = triggerPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF97316), Color(0xFFEA580C)),
                    start = Offset(w * 0.36f, h * 0.46f),
                    end = Offset(w * 0.45f, h * 0.58f)
                )
            )

            // 5. Scanner Head Housing (Angular 3D Casing)
            val headPath = Path().apply {
                moveTo(w * 0.18f, h * 0.26f)
                lineTo(w * 0.72f, h * 0.18f)
                lineTo(w * 0.82f, h * 0.34f)
                lineTo(w * 0.62f, h * 0.46f)
                lineTo(w * 0.24f, h * 0.46f)
                close()
            }
            drawPath(
                path = headPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A)),
                    start = Offset(w * 0.18f, h * 0.18f),
                    end = Offset(w * 0.72f, h * 0.46f)
                )
            )

            // Top Specular Highlight on Head Housing
            val highlightPath = Path().apply {
                moveTo(w * 0.22f, h * 0.28f)
                lineTo(w * 0.68f, h * 0.21f)
                lineTo(w * 0.66f, h * 0.25f)
                lineTo(w * 0.23f, h * 0.32f)
                close()
            }
            drawPath(
                path = highlightPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0x60FFFFFF), Color(0x20FFFFFF), Color.Transparent),
                    startX = w * 0.22f,
                    endX = w * 0.68f
                )
            )

            // 6. High-Visibility Protective Shock Rubber Bezel
            val bumperPath = Path().apply {
                moveTo(w * 0.17f, h * 0.24f)
                lineTo(w * 0.27f, h * 0.22f)
                lineTo(w * 0.30f, h * 0.46f)
                lineTo(w * 0.22f, h * 0.48f)
                close()
            }
            drawPath(
                path = bumperPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5), Color(0xFF3730A3)),
                    start = Offset(w * 0.17f, h * 0.22f),
                    end = Offset(w * 0.30f, h * 0.48f)
                )
            )

            // 7. Optical Scanner Glass Front Window (Snout Red Lens)
            val windowPath = Path().apply {
                moveTo(w * 0.16f, h * 0.26f)
                lineTo(w * 0.20f, h * 0.25f)
                lineTo(w * 0.22f, h * 0.46f)
                lineTo(w * 0.18f, h * 0.47f)
                close()
            }
            drawPath(
                path = windowPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFEF4444), Color(0xFF991B1B), Color(0xFF450A0A)),
                    start = Offset(w * 0.16f, h * 0.25f),
                    end = Offset(w * 0.22f, h * 0.47f)
                )
            )
            // Lens Glass Gloss Glare
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(w * 0.17f, h * 0.28f),
                end = Offset(w * 0.19f, h * 0.38f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )

            // 8. Active Status LED Indicator on Rear Crown
            val ledColor = if (animate) Color(0xFF10B981) else Color(0xFF64748B)
            drawCircle(
                color = ledColor.copy(alpha = 0.5f * laserPulse),
                radius = w * 0.08f,
                center = Offset(w * 0.72f, h * 0.24f)
            )
            drawCircle(
                color = if (animate) Color(0xFF34D399) else Color(0xFF94A3B8),
                radius = w * 0.045f,
                center = Offset(w * 0.72f, h * 0.24f)
            )
        }
    }
}

/**
 * Photorealistic 3D Camera / Barcode Laser Viewfinder.
 */
@Composable
fun Realistic3DCameraViewfinderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Realistic3DHardwareBarcodeScannerIcon(modifier = modifier, size = size)
}

/**
 * 19. Photorealistic 3D Brushed Metal Rotary Mixing Console (Filter Button).
 */
@Composable
fun Realistic3DConsoleDialsIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val centers = listOf(
                Offset(w * 0.32f, h * 0.32f),
                Offset(w * 0.68f, h * 0.32f),
                Offset(w * 0.32f, h * 0.68f),
                Offset(w * 0.68f, h * 0.68f)
            )
            val radius = w * 0.13f

            centers.forEachIndexed { i, center ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF8FAFC), Color(0xFF64748B), Color(0xFF1E293B)),
                        center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                        radius = radius * 1.2f
                    ),
                    radius = radius,
                    center = center
                )
                // Cyan Indicator Notch
                val angle = (i * 65f + 40f) * (Math.PI / 180).toFloat()
                val notchX = center.x + (radius * 0.55f) * kotlin.math.cos(angle)
                val notchY = center.y + (radius * 0.55f) * kotlin.math.sin(angle)
                drawCircle(Color(0xFF38BDF8), radius = w * 0.025f, center = Offset(notchX, notchY))
            }
        }
    }
}

/**
 * 20. Photorealistic 3D Ringing Golden Brass Bell.
 */
@Composable
fun Realistic3DBrassAlertBellIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bellAnim")
    val rot by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -10f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bellRot"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = rot },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Bell Body Dome
            val bellPath = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                cubicTo(w * 0.30f, h * 0.14f, w * 0.20f, h * 0.50f, w * 0.12f, h * 0.72f)
                lineTo(w * 0.88f, h * 0.72f)
                cubicTo(w * 0.80f, h * 0.50f, w * 0.70f, h * 0.14f, w * 0.50f, h * 0.12f)
                close()
            }
            drawPath(
                bellPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    center = Offset(w * 0.40f, h * 0.30f),
                    radius = w * 0.50f
                )
            )

            // Bell Lip
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFFB45309), Color(0xFFFDE047), Color(0xFFB45309))),
                topLeft = Offset(w * 0.08f, h * 0.72f),
                size = Size(w * 0.84f, h * 0.09f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Clapper
            drawCircle(Color(0xFF78350F), radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.86f))
        }
    }
}

/**
 * 21. Photorealistic 3D Glowing Emerald Sphere (In Stock).
 */
@Composable
fun Realistic3DGlowingEmeraldSphere(
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)

            // Ambient Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x8810B981), Color.Transparent),
                    center = center,
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = center
            )

            // 3D Glass Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF86EFAC), Color(0xFF10B981), Color(0xFF047857)),
                    center = Offset(w * 0.35f, h * 0.35f),
                    radius = w * 0.42f
                ),
                radius = w * 0.38f,
                center = center
            )
        }
    }
}

/**
 * 22. Photorealistic 3D Amber Hazard Diamond (Low Stock).
 */
@Composable
fun Realistic3DAmberHazardDiamond(
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            val diamond = Path().apply {
                moveTo(w * 0.5f, h * 0.1f)
                lineTo(w * 0.9f, h * 0.5f)
                lineTo(w * 0.5f, h * 0.9f)
                lineTo(w * 0.1f, h * 0.5f)
                close()
            }
            drawPath(
                diamond,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    center = Offset(w * 0.38f, h * 0.38f),
                    radius = w * 0.45f
                )
            )
        }
    }
}

/**
 * 23. Photorealistic 3D Ruby Pulsing Alert Orb (Out of Stock).
 */
@Composable
fun Realistic3DRubyPulsingAlertOrb(
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rubyAnim")
    val pulse by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rubyPulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = pulse; scaleY = pulse },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w * 0.5f, h * 0.5f)

            // Red Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x99EF4444), Color.Transparent),
                    center = center,
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = center
            )

            // 3D Ruby Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFCA5A5), Color(0xFFEF4444), Color(0xFF991B1B)),
                    center = Offset(w * 0.35f, h * 0.35f),
                    radius = w * 0.42f
                ),
                radius = w * 0.38f,
                center = center
            )
        }
    }
}

/**
 * 3D Photorealistic Customer Users Hub Icon (Customer Directory / CRM).
 */
@Composable
fun Realistic3DCustomerHubIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "custHubAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x35000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.45f
                ),
                topLeft = Offset(w * 0.1f, h * 0.82f),
                size = Size(w * 0.8f, h * 0.16f)
            )

            // Primary Figure (Center Person Head)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6EE7B7), Color(0xFF10B981), Color(0xFF047857)),
                    center = Offset(w * 0.45f, h * 0.28f),
                    radius = w * 0.22f
                ),
                radius = w * 0.20f,
                center = Offset(w * 0.50f, h * 0.32f)
            )

            // Primary Figure Body (Curved Torso)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF064E3B))
                ),
                topLeft = Offset(w * 0.24f, h * 0.54f),
                size = Size(w * 0.52f, h * 0.38f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.22f, w * 0.22f)
            )

            // Secondary Figure (Left Person Head)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    center = Offset(w * 0.22f, h * 0.36f),
                    radius = w * 0.14f
                ),
                radius = w * 0.13f,
                center = Offset(w * 0.25f, h * 0.38f)
            )

            // Secondary Figure (Right Person Head)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFC4B5FD), Color(0xFF8B5CF6), Color(0xFF5B21B6)),
                    center = Offset(w * 0.72f, h * 0.36f),
                    radius = w * 0.14f
                ),
                radius = w * 0.13f,
                center = Offset(w * 0.75f, h * 0.38f)
            )
        }
    }
}

/**
 * 23. Photorealistic 3D Holographic Animated Return Box with Reversal Arrow (Return Items dialog).
 */
@Composable
fun Realistic3DReturnBoxIcon(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "returnBoxAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "returnFloatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val glowAlpha by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "returnGlowAlpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Radial Drop Shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x404F46E5), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.46f
                ),
                topLeft = Offset(w * 0.12f, h * 0.82f),
                size = Size(w * 0.76f, h * 0.16f)
            )

            // 2. 3D Box Main Body (Indigo / Royal Blue Gradient)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5), Color(0xFF312E81))
                ),
                topLeft = Offset(w * 0.18f, h * 0.28f),
                size = Size(w * 0.64f, h * 0.54f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f)
            )

            // 3. 3D Top Flap Cap / Bevel
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFA5B4FC), Color(0xFF818CF8)),
                    start = Offset(w * 0.18f, h * 0.20f),
                    end = Offset(w * 0.82f, h * 0.32f)
                ),
                topLeft = Offset(w * 0.18f, h * 0.22f),
                size = Size(w * 0.64f, h * 0.14f)
            )

            // 4. Glowing Emerald Reversal / Return Arrow
            val arrowStroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669))
                ),
                startAngle = 50f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(w * 0.32f, h * 0.38f),
                size = Size(w * 0.36f, h * 0.36f),
                style = arrowStroke
            )

            // Arrowhead Pointer
            val arrowHead = Path().apply {
                moveTo(w * 0.28f, h * 0.44f)
                lineTo(w * 0.42f, h * 0.34f)
                lineTo(w * 0.42f, h * 0.54f)
                close()
            }
            drawPath(
                path = arrowHead,
                color = Color(0xFF34D399).copy(alpha = glowAlpha)
            )

            // 5. Specular Glass Sheen / Highlight
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)
                ),
                topLeft = Offset(w * 0.24f, h * 0.32f),
                size = Size(w * 0.22f, h * 0.28f)
            )
        }
    }
}

/**
 * Photorealistic 3D Excel Spreadsheet Export Icon for POS Accounting & Financial Export.
 */
@Composable
fun Realistic3DExcelSpreadsheetIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "excelAnim")
    val tilt by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -2.5f,
            targetValue = 2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "excelTilt"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = tilt },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension

            // 1. Soft Ambient Shadow Under Sheet
            drawRoundRect(
                color = Color(0x33000000),
                topLeft = Offset(s * 0.12f, s * 0.16f),
                size = Size(s * 0.76f, s * 0.76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f, s * 0.12f)
            )

            // 2. Main Spreadsheet Document Base (Clean Emerald / White Paper Base)
            val sheetW = s * 0.74f
            val sheetH = s * 0.74f
            val sheetLeft = s * 0.14f
            val sheetTop = s * 0.13f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F8E9)),
                    start = Offset(sheetLeft, sheetTop),
                    end = Offset(sheetLeft + sheetW, sheetTop + sheetH)
                ),
                topLeft = Offset(sheetLeft, sheetTop),
                size = Size(sheetW, sheetH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f, s * 0.10f)
            )
            // Outer Border around document sheet
            drawRoundRect(
                color = Color(0xFFC8E6C9),
                topLeft = Offset(sheetLeft, sheetTop),
                size = Size(sheetW, sheetH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f, s * 0.10f),
                style = Stroke(width = s * 0.035f)
            )

            // 3. Spreadsheet Data Grid Matrix (Header Row + Grid Cells)
            val gridLeft = sheetLeft + sheetW * 0.42f
            val gridRight = sheetLeft + sheetW * 0.90f
            val gridW = gridRight - gridLeft
            val gridTop = sheetTop + sheetH * 0.18f
            val rowH = sheetH * 0.18f

            // Top Header Row
            drawRoundRect(
                color = Color(0xFF81C784),
                topLeft = Offset(gridLeft, gridTop),
                size = Size(gridW, rowH * 0.8f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f, s * 0.03f)
            )

            // Grid Matrix Data Cells
            for (r in 1..3) {
                val y = gridTop + r * rowH
                drawRoundRect(
                    color = if (r == 1) Color(0xFFA5D6A7) else Color(0xFFC8E6C9),
                    topLeft = Offset(gridLeft, y),
                    size = Size(gridW * 0.45f, rowH * 0.7f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.02f, s * 0.02f)
                )
                drawRoundRect(
                    color = if (r == 1) Color(0xFFC8E6C9) else Color(0xFFE8F5E9),
                    topLeft = Offset(gridLeft + gridW * 0.52f, y),
                    size = Size(gridW * 0.48f, rowH * 0.7f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.02f, s * 0.02f)
                )
            }

            // 4. Overlapping 3D "X" Emerald Tile (Iconic Excel Look)
            val tileW = s * 0.48f
            val tileH = s * 0.52f
            val tileLeft = s * 0.08f
            val tileTop = s * 0.24f

            // Tile Shadow
            drawRoundRect(
                color = Color(0x40000000),
                topLeft = Offset(tileLeft + s * 0.02f, tileTop + s * 0.03f),
                size = Size(tileW, tileH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.09f, s * 0.09f)
            )

            // Tile Gradient (Excel Iconic Forest Green -> Vivid Emerald)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2E7D32), Color(0xFF1B5E20), Color(0xFF0D4A2B)),
                    start = Offset(tileLeft, tileTop),
                    end = Offset(tileLeft + tileW, tileTop + tileH)
                ),
                topLeft = Offset(tileLeft, tileTop),
                size = Size(tileW, tileH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f, s * 0.08f)
            )

            // Top highlight specular line on tile
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x80FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(tileLeft, tileTop),
                    end = Offset(tileLeft + tileW, tileTop)
                ),
                topLeft = Offset(tileLeft, tileTop),
                size = Size(tileW, s * 0.04f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f, s * 0.04f)
            )

            // 5. Crisp White "X" on the Green Tile
            val tcx = tileLeft + tileW / 2f
            val tcy = tileTop + tileH / 2f
            val arm = tileW * 0.24f
            val strokeW = s * 0.065f

            drawLine(
                color = Color.White,
                start = Offset(tcx - arm, tcy - arm),
                end = Offset(tcx + arm, tcy + arm),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(tcx + arm, tcy - arm),
                end = Offset(tcx - arm, tcy + arm),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 32. Photorealistic 3D Cash Banknote with Taka glyph & security borders.
 */
@Composable
fun Realistic3DCashBillIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cashBillAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width
            val h = this.size.height

            // 1. Drop shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(s * 0.05f, h * 0.22f),
                size = Size(s * 0.90f, h * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f, s * 0.12f)
            )

            // 2. Banknote Body Gradient (Vivid Emerald)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF047857)),
                    start = Offset(0f, 0f),
                    end = Offset(s, h)
                ),
                topLeft = Offset(s * 0.05f, h * 0.18f),
                size = Size(s * 0.90f, h * 0.64f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f, s * 0.12f)
            )

            // 3. Inner Decorative Border
            drawRoundRect(
                color = Color(0x60FFFFFF),
                topLeft = Offset(s * 0.10f, h * 0.23f),
                size = Size(s * 0.80f, h * 0.54f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f, s * 0.08f),
                style = Stroke(width = s * 0.035f)
            )

            // 4. Central Circular Guilloche Shield
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFECFDF5), Color(0xFFA7F3D0)),
                    center = Offset(s * 0.5f, h * 0.5f),
                    radius = s * 0.22f
                ),
                radius = s * 0.20f,
                center = Offset(s * 0.5f, h * 0.5f)
            )

            // 5. Crisp Bengali Taka "৳" Glyph
            val tcx = s * 0.5f
            val tcy = h * 0.5f
            val strokeW = s * 0.06f

            // Taka Horizontal Top Bar
            drawLine(
                color = Color(0xFF065F46),
                start = Offset(tcx - s * 0.10f, tcy - s * 0.10f),
                end = Offset(tcx + s * 0.10f, tcy - s * 0.10f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            // Vertical Spine
            drawLine(
                color = Color(0xFF065F46),
                start = Offset(tcx, tcy - s * 0.10f),
                end = Offset(tcx, tcy + s * 0.12f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            // Middle Hash
            drawLine(
                color = Color(0xFF065F46),
                start = Offset(tcx - s * 0.08f, tcy + s * 0.01f),
                end = Offset(tcx + s * 0.06f, tcy + s * 0.01f),
                strokeWidth = strokeW * 0.8f,
                cap = StrokeCap.Round
            )

            // Specular gloss top sheen
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x70FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(s * 0.05f, h * 0.18f),
                    end = Offset(s * 0.05f, h * 0.32f)
                ),
                topLeft = Offset(s * 0.05f, h * 0.18f),
                size = Size(s * 0.90f, h * 0.20f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f, s * 0.12f)
            )
        }
    }
}

/**
 * 33. Photorealistic 3D bKash Mobile Channel Icon (Signature Magenta & Origami Bird).
 */
@Composable
fun Realistic3DbKashIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bkashAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width

            // Shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(s * 0.08f, s * 0.12f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Base Tile Gradient (Signature bKash Vivid Magenta)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFE2136E), Color(0xFFC2185B), Color(0xFF880E4F)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Top Specular Highlight Sheen
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x65FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(s * 0.08f, s * 0.08f),
                    end = Offset(s * 0.08f, s * 0.40f)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Origami Bird Polygon Path
            val path = Path().apply {
                moveTo(s * 0.30f, s * 0.65f)
                lineTo(s * 0.50f, s * 0.28f)
                lineTo(s * 0.70f, s * 0.65f)
                lineTo(s * 0.50f, s * 0.52f)
                close()
            }
            drawPath(path, color = Color.White)

            // Yellow Accent Beak Dot
            drawCircle(
                color = Color(0xFFFDE047),
                radius = s * 0.055f,
                center = Offset(s * 0.68f, s * 0.35f)
            )
        }
    }
}

/**
 * 34. Photorealistic 3D Nagad Channel Icon (Signature Vibrant Orange & Flash Flame).
 */
@Composable
fun Realistic3DNagadIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nagadAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width

            // Shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(s * 0.08f, s * 0.12f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Base Tile Gradient (Signature Nagad Radiant Orange)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFFA726), Color(0xFFF7941D), Color(0xFFD84315)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Top Specular Highlight
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x70FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(s * 0.08f, s * 0.08f),
                    end = Offset(s * 0.08f, s * 0.40f)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Nagad Flash / Lightning Flame Path
            val flamePath = Path().apply {
                moveTo(s * 0.54f, s * 0.24f)
                lineTo(s * 0.32f, s * 0.50f)
                lineTo(s * 0.48f, s * 0.50f)
                lineTo(s * 0.42f, s * 0.76f)
                lineTo(s * 0.68f, s * 0.44f)
                lineTo(s * 0.52f, s * 0.44f)
                close()
            }
            drawPath(flamePath, color = Color.White)
        }
    }
}

/**
 * 35. Photorealistic 3D DBBL Rocket Channel Icon (Signature Royal Purple & Rocket Thruster).
 */
@Composable
fun Realistic3DRocketIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rocketAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width

            // Shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(s * 0.08f, s * 0.12f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Base Tile Gradient (Signature Rocket Royal Purple)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFAB47BC), Color(0xFF8C3494), Color(0xFF4A148C)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Top Specular Highlight
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x70FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(s * 0.08f, s * 0.08f),
                    end = Offset(s * 0.08f, s * 0.40f)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Rocket Body Path
            val rocketPath = Path().apply {
                moveTo(s * 0.50f, s * 0.24f)
                cubicTo(s * 0.42f, s * 0.35f, s * 0.38f, s * 0.48f, s * 0.38f, s * 0.60f)
                lineTo(s * 0.32f, s * 0.68f)
                lineTo(s * 0.42f, s * 0.65f)
                lineTo(s * 0.50f, s * 0.62f)
                lineTo(s * 0.58f, s * 0.65f)
                lineTo(s * 0.68f, s * 0.68f)
                lineTo(s * 0.62f, s * 0.60f)
                cubicTo(s * 0.62f, s * 0.48f, s * 0.58f, s * 0.35f, s * 0.50f, s * 0.24f)
                close()
            }
            drawPath(rocketPath, color = Color.White)

            // Porthole Window
            drawCircle(
                color = Color(0xFF8C3494),
                radius = s * 0.045f,
                center = Offset(s * 0.50f, s * 0.44f)
            )

            // Fiery Thruster Dot
            drawCircle(
                color = Color(0xFFFFB74D),
                radius = s * 0.04f,
                center = Offset(s * 0.50f, s * 0.72f)
            )
        }
    }
}

/**
 * 36. Photorealistic 3D Bank Vault / Pillars Channel Icon (Signature Deep Navy & Cyan).
 */
@Composable
fun Realistic3DBankVaultIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bankVaultAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width

            // Shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(s * 0.08f, s * 0.12f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Base Tile Gradient (Navy / Cobalt Bank Blue)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8), Color(0xFF0F172A)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Top Specular Highlight
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x70FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(s * 0.08f, s * 0.08f),
                    end = Offset(s * 0.08f, s * 0.40f)
                ),
                topLeft = Offset(s * 0.08f, s * 0.08f),
                size = Size(s * 0.84f, s * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f, s * 0.22f)
            )

            // Triangular Pediment Roof
            val roofPath = Path().apply {
                moveTo(s * 0.50f, s * 0.24f)
                lineTo(s * 0.26f, s * 0.38f)
                lineTo(s * 0.74f, s * 0.38f)
                close()
            }
            drawPath(roofPath, color = Color.White)

            // Architrave beam
            drawRect(
                color = Color.White,
                topLeft = Offset(s * 0.26f, s * 0.39f),
                size = Size(s * 0.48f, s * 0.04f)
            )

            // 3 Vertical Classical Columns
            val colW = s * 0.06f
            val colH = s * 0.22f
            val colTop = s * 0.44f
            drawRect(color = Color.White, topLeft = Offset(s * 0.30f, colTop), size = Size(colW, colH))
            drawRect(color = Color.White, topLeft = Offset(s * 0.47f, colTop), size = Size(colW, colH))
            drawRect(color = Color.White, topLeft = Offset(s * 0.64f, colTop), size = Size(colW, colH))

            // Base Podium
            drawRect(
                color = Color.White,
                topLeft = Offset(s * 0.24f, s * 0.67f),
                size = Size(s * 0.52f, s * 0.06f)
            )
        }
    }
}

/**
 * 37. Photorealistic 3D WhatsApp & Thermal Money Receipt Slip Icon.
 */
@Composable
fun Realistic3DReceiptSlipIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "receiptSlipAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "floatY"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width

            // Shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(s * 0.12f, s * 0.10f),
                size = Size(s * 0.76f, s * 0.82f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.14f, s * 0.14f)
            )

            // Thermal Slip Body (Vivid WhatsApp Emerald Gradient)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF25D366), Color(0xFF128C7E), Color(0xFF075E54)),
                    start = Offset(0f, 0f),
                    end = Offset(s, s)
                ),
                topLeft = Offset(s * 0.12f, s * 0.08f),
                size = Size(s * 0.76f, s * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.14f, s * 0.14f)
            )

            // Top Specular Sheen
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x60FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(s * 0.12f, s * 0.08f),
                    end = Offset(s * 0.12f, s * 0.35f)
                ),
                topLeft = Offset(s * 0.12f, s * 0.08f),
                size = Size(s * 0.76f, s * 0.30f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.14f, s * 0.14f)
            )

            // Printed Thermal Lines on the slip
            val lineStroke = s * 0.05f
            drawLine(
                color = Color.White,
                start = Offset(s * 0.28f, s * 0.28f),
                end = Offset(s * 0.72f, s * 0.28f),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(s * 0.28f, s * 0.42f),
                end = Offset(s * 0.62f, s * 0.42f),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFDE047),
                start = Offset(s * 0.28f, s * 0.56f),
                end = Offset(s * 0.52f, s * 0.56f),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )

            // Mini Checkmark Circle Badge in bottom corner
            drawCircle(
                color = Color.White,
                radius = s * 0.14f,
                center = Offset(s * 0.68f, s * 0.68f)
            )
            // Green Checkmark
            val cx = s * 0.68f
            val cy = s * 0.68f
            val checkPath = Path().apply {
                moveTo(cx - s * 0.07f, cy)
                lineTo(cx - s * 0.02f, cy + s * 0.05f)
                lineTo(cx + s * 0.07f, cy - s * 0.04f)
            }
            drawPath(
                checkPath,
                color = Color(0xFF128C7E),
                style = Stroke(width = s * 0.04f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 40. Photorealistic 3D Smart EMV Credit/Debit Card Icon.
 * Features an obsidian/indigo metallic card body, golden EMV smart chip,
 * contactless NFC waves, and holographic security badge.
 */
@Composable
fun Realistic3DCardIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardFloat")
    val floatOffset by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = -0.5f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cardFloatOffset"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .offset(y = floatOffset.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width
            val h = this.size.height

            // Soft 3D Drop Shadow
            drawRoundRect(
                color = Color(0x35000000),
                topLeft = Offset(s * 0.06f, h * 0.18f),
                size = Size(s * 0.88f, h * 0.64f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f, s * 0.12f)
            )

            // Beveled Dark Edge (3D Card Thickness)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF312E81), Color(0xFF1E1B4B)),
                    start = Offset(0f, 0f),
                    end = Offset(s, h)
                ),
                topLeft = Offset(s * 0.05f, h * 0.12f),
                size = Size(s * 0.90f, h * 0.65f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f, s * 0.10f)
            )

            // Front Card Body (Indigo/Violet Metallic Sheen)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4338CA), Color(0xFF3730A3)),
                    start = Offset(s * 0.05f, h * 0.10f),
                    end = Offset(s * 0.95f, h * 0.75f)
                ),
                topLeft = Offset(s * 0.05f, h * 0.10f),
                size = Size(s * 0.90f, h * 0.62f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f, s * 0.10f)
            )

            // Specular Top-Diagonal Gloss Sheen
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x65FFFFFF), Color(0x00FFFFFF)),
                    start = Offset(s * 0.05f, h * 0.10f),
                    end = Offset(s * 0.60f, h * 0.45f)
                ),
                topLeft = Offset(s * 0.05f, h * 0.10f),
                size = Size(s * 0.90f, h * 0.32f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f, s * 0.10f)
            )

            // Golden EMV Smart Chip
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE047), Color(0xFFD97706)),
                    start = Offset(s * 0.16f, h * 0.28f),
                    end = Offset(s * 0.36f, h * 0.48f)
                ),
                topLeft = Offset(s * 0.16f, h * 0.28f),
                size = Size(s * 0.20f, h * 0.20f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.03f, s * 0.03f)
            )
            // Chip Grid Line
            drawLine(
                color = Color(0xFF78350F),
                start = Offset(s * 0.26f, h * 0.28f),
                end = Offset(s * 0.26f, h * 0.48f),
                strokeWidth = s * 0.02f
            )
            drawLine(
                color = Color(0xFF78350F),
                start = Offset(s * 0.16f, h * 0.38f),
                end = Offset(s * 0.36f, h * 0.38f),
                strokeWidth = s * 0.02f
            )

            // Contactless Wave Arches
            val waveX = s * 0.44f
            val waveY = h * 0.38f
            drawArc(
                color = Color.White.copy(alpha = 0.85f),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(waveX - s * 0.04f, waveY - h * 0.08f),
                size = Size(s * 0.08f, h * 0.16f),
                style = Stroke(width = s * 0.025f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.65f),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(waveX + s * 0.02f, waveY - h * 0.12f),
                size = Size(s * 0.12f, h * 0.24f),
                style = Stroke(width = s * 0.025f, cap = StrokeCap.Round)
            )

            // Holographic Mastercard / Visa Circle Badges in bottom-right
            drawCircle(
                color = Color(0xFFEF4444).copy(alpha = 0.85f),
                radius = s * 0.08f,
                center = Offset(s * 0.72f, h * 0.54f)
            )
            drawCircle(
                color = Color(0xFFF59E0B).copy(alpha = 0.75f),
                radius = s * 0.08f,
                center = Offset(s * 0.82f, h * 0.54f)
            )
        }
    }
}

/**
 * 66. Photorealistic 3D Thermal POS Receipt Printer with paper feed animation.
 */
@Composable
fun Realistic3DPrinterIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "printerAnim")
    val paperAnim by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "paperFeed"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width
            val h = this.size.height

            // 1. Printer Body Shadow
            drawRoundRect(
                color = Color(0xFF0369A1).copy(alpha = 0.35f),
                topLeft = Offset(s * 0.12f, h * 0.40f),
                size = Size(s * 0.76f, h * 0.48f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f, s * 0.12f)
            )

            // 2. Paper Feed Roll (Top Chamber)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE0F2FE), Color(0xFFBAE6FD), Color(0xFF7DD3FC))
                ),
                topLeft = Offset(s * 0.24f, h * 0.12f),
                size = Size(s * 0.52f, h * 0.30f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f, s * 0.08f)
            )

            // Paper Feed Roll lines
            drawLine(
                color = Color(0xFF0284C7).copy(alpha = 0.4f),
                start = Offset(s * 0.30f, h * 0.22f),
                end = Offset(s * 0.70f, h * 0.22f),
                strokeWidth = s * 0.035f,
                cap = StrokeCap.Round
            )

            // 3. Main Printer Shell (Metallic Cobalt Blue)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1))
                ),
                topLeft = Offset(s * 0.10f, h * 0.34f),
                size = Size(s * 0.80f, h * 0.52f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.14f, s * 0.14f)
            )

            // Bevel highlight
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(s * 0.16f, h * 0.37f),
                end = Offset(s * 0.84f, h * 0.37f),
                strokeWidth = s * 0.03f,
                cap = StrokeCap.Round
            )

            // 4. Output Paper Slot
            drawRoundRect(
                color = Color(0xFF0F172A),
                topLeft = Offset(s * 0.20f, h * 0.50f),
                size = Size(s * 0.60f, h * 0.08f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f, s * 0.04f)
            )

            // 5. Emerging Receipt Slip (Animated feed)
            val feedOffset = (paperAnim * s * 0.04f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF1F5F9))
                ),
                topLeft = Offset(s * 0.25f, h * 0.54f),
                size = Size(s * 0.50f, h * 0.32f + feedOffset),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f, s * 0.04f)
            )

            // Receipt Text Lines on paper
            drawLine(
                color = Color(0xFF0284C7),
                start = Offset(s * 0.32f, h * 0.62f),
                end = Offset(s * 0.68f, h * 0.62f),
                strokeWidth = s * 0.035f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(s * 0.32f, h * 0.70f),
                end = Offset(s * 0.58f, h * 0.70f),
                strokeWidth = s * 0.03f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(s * 0.32f, h * 0.78f),
                end = Offset(s * 0.64f, h * 0.78f),
                strokeWidth = s * 0.03f,
                cap = StrokeCap.Round
            )

            // 6. Status LED (Glowing Emerald Green)
            drawCircle(
                color = Color(0xFF10B981),
                radius = s * 0.05f,
                center = Offset(s * 0.76f, h * 0.44f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = s * 0.02f,
                center = Offset(s * 0.75f, h * 0.43f)
            )
        }
    }
}

/**
 * 67. Photorealistic 3D WhatsApp & SMS Share Slip with flying paper plane.
 */
@Composable
fun Realistic3DShareIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shareAnim")
    val tiltOffset by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -2.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tilt"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width
            val h = this.size.height

            // 1. 3D Emerald Chat Bubble Background
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF059669)),
                    start = Offset(0f, 0f),
                    end = Offset(s, h)
                ),
                topLeft = Offset(s * 0.08f, h * 0.08f),
                size = Size(s * 0.84f, h * 0.84f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.24f, s * 0.24f)
            )

            // Glossy highlight arc
            drawArc(
                color = Color.White.copy(alpha = 0.45f),
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(s * 0.12f, h * 0.12f),
                size = Size(s * 0.76f, h * 0.40f),
                style = Stroke(width = s * 0.05f, cap = StrokeCap.Round)
            )

            // 2. Paper Plane Vector
            val planePath = Path().apply {
                moveTo(s * 0.24f + tiltOffset, h * 0.52f)
                lineTo(s * 0.74f + tiltOffset, h * 0.26f)
                lineTo(s * 0.52f + tiltOffset, h * 0.76f)
                lineTo(s * 0.44f + tiltOffset, h * 0.58f)
                close()
            }
            drawPath(planePath, color = Color.White)

            val planeFold = Path().apply {
                moveTo(s * 0.44f + tiltOffset, h * 0.58f)
                lineTo(s * 0.74f + tiltOffset, h * 0.26f)
                lineTo(s * 0.46f + tiltOffset, h * 0.48f)
                close()
            }
            drawPath(planeFold, color = Color(0xFFA7F3D0).copy(alpha = 0.9f))

            // 3. Notification Sparkle Dot
            drawCircle(
                color = Color(0xFFFDE047),
                radius = s * 0.08f,
                center = Offset(s * 0.76f, h * 0.24f)
            )
            drawCircle(
                color = Color(0xFFEAB308),
                radius = s * 0.04f,
                center = Offset(s * 0.76f, h * 0.24f)
            )
        }
    }
}

/**
 * 68. Photorealistic 3D Save Image / Download Disk with animated downward arrow.
 */
@Composable
fun Realistic3DDownloadIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "downloadAnim")
    val bounceY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "arrowBounce"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width
            val h = this.size.height

            // 1. 3D Indigo/Royal Blue Tray
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF1D4ED8))
                ),
                topLeft = Offset(s * 0.12f, h * 0.12f),
                size = Size(s * 0.76f, h * 0.76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.18f, s * 0.18f)
            )

            // Top disk shutter
            drawRoundRect(
                color = Color(0xFFDBEAFE).copy(alpha = 0.9f),
                topLeft = Offset(s * 0.28f, h * 0.12f),
                size = Size(s * 0.44f, h * 0.24f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f, s * 0.04f)
            )

            // 2. Animated Downward Arrow
            val yOffset = bounceY * s * 0.04f
            drawLine(
                color = Color.White,
                start = Offset(s * 0.50f, h * 0.44f + yOffset),
                end = Offset(s * 0.50f, h * 0.72f + yOffset),
                strokeWidth = s * 0.08f,
                cap = StrokeCap.Round
            )
            val arrowHead = Path().apply {
                moveTo(s * 0.34f, h * 0.60f + yOffset)
                lineTo(s * 0.50f, h * 0.76f + yOffset)
                lineTo(s * 0.66f, h * 0.60f + yOffset)
            }
            drawPath(arrowHead, color = Color.White, style = Stroke(width = s * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Bottom tray bar
            drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(s * 0.26f, h * 0.80f),
                end = Offset(s * 0.74f, h * 0.80f),
                strokeWidth = s * 0.06f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * 69. Photorealistic 3D Emerald Repayment Voucher Verification Seal.
 */
@Composable
fun Realistic3DRepaymentSealIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sealAnim")
    val pulseScale by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.width
            val h = this.size.height

            // 1. Circular Emerald Shield with Specular Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF047857)),
                    center = Offset(s * 0.40f, h * 0.35f),
                    radius = s * 0.55f * pulseScale
                ),
                radius = s * 0.44f * pulseScale,
                center = Offset(s * 0.50f, h * 0.50f)
            )

            // Inner dashed ring
            drawCircle(
                color = Color(0xFFA7F3D0).copy(alpha = 0.6f),
                radius = s * 0.36f * pulseScale,
                center = Offset(s * 0.50f, h * 0.50f),
                style = Stroke(
                    width = s * 0.035f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.08f, s * 0.05f), 0f)
                )
            )

            // 2. Bold White Checkmark in Center
            val checkPath = Path().apply {
                moveTo(s * 0.32f, h * 0.50f)
                lineTo(s * 0.46f, h * 0.64f)
                lineTo(s * 0.70f, h * 0.36f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 70. Photorealistic 3D Golden Clipboard Icon with animated copy feedback.
 */
@Composable
fun Realistic3DClipboardCopyIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    copied: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 1. Soft Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(w * 0.16f, h * 0.18f),
                size = Size(w * 0.68f, h * 0.74f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f)
            )

            // 2. Wooden/Golden Clipboard Board
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706), Color(0xFFB45309)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                topLeft = Offset(w * 0.14f, h * 0.14f),
                size = Size(w * 0.72f, h * 0.74f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f)
            )

            // 3. Crisp White Paper Sheet
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFF8FAFC))
                ),
                topLeft = Offset(w * 0.24f, h * 0.26f),
                size = Size(w * 0.52f, h * 0.54f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f)
            )

            // 4. Metallic Clip on Top
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF64748B))
                ),
                topLeft = Offset(w * 0.34f, h * 0.08f),
                size = Size(w * 0.32f, h * 0.16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f)
            )

            // 5. Paper details or Checkmark if copied
            if (copied) {
                val check = Path().apply {
                    moveTo(w * 0.34f, h * 0.54f)
                    lineTo(w * 0.46f, h * 0.66f)
                    lineTo(w * 0.66f, h * 0.38f)
                }
                drawPath(
                    path = check,
                    color = Color(0xFF10B981),
                    style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else {
                // Subtle ruled lines on paper
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(w * 0.32f, h * 0.42f),
                    end = Offset(w * 0.68f, h * 0.42f),
                    strokeWidth = w * 0.05f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(w * 0.32f, h * 0.54f),
                    end = Offset(w * 0.68f, h * 0.54f),
                    strokeWidth = w * 0.05f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(w * 0.32f, h * 0.66f),
                    end = Offset(w * 0.56f, h * 0.66f),
                    strokeWidth = w * 0.05f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * 71. Photorealistic 3D Gold Coins Stack with Metallic Luster & Taka Symbol (Total Revenue).
 */
@Composable
fun Realistic3DGoldCoinsIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "goldCoinsAnim")
    val floatY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "coinsFloat"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = floatY },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Drop shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x35000000), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.90f),
                    radius = w * 0.44f
                ),
                topLeft = Offset(w * 0.10f, h * 0.82f),
                size = Size(w * 0.80f, h * 0.16f)
            )

            // Coin 1 (Bottom)
            val coin1Edge = Path().apply {
                moveTo(w * 0.16f, h * 0.62f)
                lineTo(w * 0.16f, h * 0.74f)
                cubicTo(w * 0.16f, h * 0.84f, w * 0.84f, h * 0.84f, w * 0.84f, h * 0.74f)
                lineTo(w * 0.84f, h * 0.62f)
                close()
            }
            drawPath(
                coin1Edge,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFB45309), Color(0xFF78350F))
                )
            )
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFBBF24), Color(0xFFD97706)),
                    start = Offset(w * 0.16f, h * 0.54f),
                    end = Offset(w * 0.84f, h * 0.70f)
                ),
                topLeft = Offset(w * 0.16f, h * 0.54f),
                size = Size(w * 0.68f, h * 0.16f)
            )

            // Coin 2 (Middle)
            val coin2Edge = Path().apply {
                moveTo(w * 0.16f, h * 0.44f)
                lineTo(w * 0.16f, h * 0.56f)
                cubicTo(w * 0.16f, h * 0.66f, w * 0.84f, h * 0.66f, w * 0.84f, h * 0.56f)
                lineTo(w * 0.84f, h * 0.44f)
                close()
            }
            drawPath(
                coin2Edge,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFF92400E))
                )
            )
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
                    start = Offset(w * 0.16f, h * 0.36f),
                    end = Offset(w * 0.84f, h * 0.52f)
                ),
                topLeft = Offset(w * 0.16f, h * 0.36f),
                size = Size(w * 0.68f, h * 0.16f)
            )

            // Coin 3 (Top - Golden with specular rim & inner ring)
            val coin3Edge = Path().apply {
                moveTo(w * 0.16f, h * 0.26f)
                lineTo(w * 0.16f, h * 0.38f)
                cubicTo(w * 0.16f, h * 0.48f, w * 0.84f, h * 0.48f, w * 0.84f, h * 0.38f)
                lineTo(w * 0.84f, h * 0.26f)
                close()
            }
            drawPath(
                coin3Edge,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF59E0B), Color(0xFFB45309))
                )
            )
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFFBBF24), Color(0xFFD97706)),
                    start = Offset(w * 0.20f, h * 0.18f),
                    end = Offset(w * 0.80f, h * 0.34f)
                ),
                topLeft = Offset(w * 0.16f, h * 0.18f),
                size = Size(w * 0.68f, h * 0.16f)
            )
            // Coin 3 Inner Ring
            drawOval(
                color = Color(0xFFFFFBEB).copy(alpha = 0.7f),
                topLeft = Offset(w * 0.24f, h * 0.205f),
                size = Size(w * 0.52f, h * 0.11f),
                style = Stroke(width = w * 0.025f)
            )
            // Central gold dot
            drawCircle(
                color = Color(0xFF92400E),
                radius = w * 0.035f,
                center = Offset(w * 0.50f, h * 0.26f)
            )
        }
    }
}

/**
 * 72. Photorealistic 3D Amber Stylus Pen (Edit Action).
 */
@Composable
fun Realistic3DStylusPenIcon(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // 45 degree angled stylus body
            val barrel = Path().apply {
                moveTo(w * 0.70f, h * 0.10f)
                lineTo(w * 0.88f, h * 0.28f)
                lineTo(w * 0.36f, h * 0.80f)
                lineTo(w * 0.18f, h * 0.62f)
                close()
            }
            drawPath(
                barrel,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    start = Offset(w * 0.18f, h * 0.10f),
                    end = Offset(w * 0.88f, h * 0.80f)
                )
            )

            // Tip (Nib)
            val nib = Path().apply {
                moveTo(w * 0.18f, h * 0.62f)
                lineTo(w * 0.36f, h * 0.80f)
                lineTo(w * 0.08f, h * 0.92f)
                close()
            }
            drawPath(nib, Color(0xFF78350F))

            // Metallic Ring Accent
            val ring = Path().apply {
                moveTo(w * 0.52f, h * 0.28f)
                lineTo(w * 0.62f, h * 0.38f)
                lineTo(w * 0.56f, h * 0.44f)
                lineTo(w * 0.46f, h * 0.34f)
                close()
            }
            drawPath(ring, Color(0xFFFFFFFF).copy(alpha = 0.8f))
        }
    }
}

/**
 * 73. Photorealistic 3D Ruby Shredder / Trash Can (Delete Action).
 */
@Composable
fun Realistic3DTrashCanIcon(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height

            // Bin Body
            val bin = Path().apply {
                moveTo(w * 0.22f, h * 0.35f)
                lineTo(w * 0.78f, h * 0.35f)
                lineTo(w * 0.70f, h * 0.88f)
                lineTo(w * 0.30f, h * 0.88f)
                close()
            }
            drawPath(
                bin,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                )
            )

            // Ribbed lines on body
            drawLine(
                color = Color(0xFFFCA5A5).copy(alpha = 0.7f),
                start = Offset(w * 0.40f, h * 0.44f),
                end = Offset(w * 0.42f, h * 0.78f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFCA5A5).copy(alpha = 0.7f),
                start = Offset(w * 0.50f, h * 0.44f),
                end = Offset(w * 0.50f, h * 0.78f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFCA5A5).copy(alpha = 0.7f),
                start = Offset(w * 0.60f, h * 0.44f),
                end = Offset(w * 0.58f, h * 0.78f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )

            // Lid Rim
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFF87171), Color(0xFFDC2626), Color(0xFF991B1B))
                ),
                topLeft = Offset(w * 0.16f, h * 0.22f),
                size = Size(w * 0.68f, h * 0.14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f, w * 0.04f)
            )

            // Lid Handle
            drawRoundRect(
                color = Color(0xFFB91C1C),
                topLeft = Offset(w * 0.38f, h * 0.12f),
                size = Size(w * 0.24f, h * 0.10f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.03f, w * 0.03f)
            )
        }
    }
}


