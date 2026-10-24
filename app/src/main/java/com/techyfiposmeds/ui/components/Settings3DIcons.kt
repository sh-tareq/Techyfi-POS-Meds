package com.techyfiposmeds.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1. 3D Holographic Convex Earth Sphere with Orbiting Bilingual Ring ('A' & 'অ').
 * Used for App Language setting.
 */
@Composable
fun Realistic3DLangGlobeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GlobeOrbitTransition")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitAngle"
    )
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlobeFloat"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f + floatOffset * (s / 36f)
            val r = s * 0.38f

            // 1. Atmospheric Ambient Back Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 1.5f
                ),
                center = Offset(cx, cy),
                radius = r * 1.5f
            )

            // 2. 3D Convex Globe Base (Deep Ocean Spherical Gradient)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38BDF8),
                        Color(0xFF0284C7),
                        Color(0xFF0369A1),
                        Color(0xFF082F49)
                    ),
                    center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                    radius = r * 1.25f
                ),
                center = Offset(cx, cy),
                radius = r
            )

            // 3. Globe Continents (Emerald stylized landmasses)
            val continentPath = Path().apply {
                moveTo(cx - r * 0.4f, cy - r * 0.2f)
                cubicTo(
                    cx - r * 0.1f, cy - r * 0.5f,
                    cx + r * 0.3f, cy - r * 0.3f,
                    cx + r * 0.2f, cy
                )
                cubicTo(
                    cx + r * 0.1f, cy + r * 0.3f,
                    cx - r * 0.2f, cy + r * 0.4f,
                    cx - r * 0.4f, cy + r * 0.1f
                )
                close()
            }
            drawPath(
                path = continentPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669)),
                    start = Offset(cx - r * 0.4f, cy - r * 0.3f),
                    end = Offset(cx + r * 0.3f, cy + r * 0.3f)
                )
            )

            // 4. Specular Curved Glass Highlight (3D Convex Refraction)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.65f), Color.Transparent),
                    center = Offset(cx - r * 0.45f, cy - r * 0.45f),
                    radius = r * 0.55f
                ),
                center = Offset(cx - r * 0.35f, cy - r * 0.35f),
                radius = r * 0.45f
            )

            // 5. Orbiting Elliptical Halo Ring
            drawOval(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF38BDF8).copy(alpha = 0.8f),
                        Color.White,
                        Color(0xFF818CF8).copy(alpha = 0.7f),
                        Color(0xFF38BDF8).copy(alpha = 0.8f)
                    ),
                    center = Offset(cx, cy)
                ),
                topLeft = Offset(cx - r * 1.35f, cy - r * 0.42f),
                size = Size(r * 2.7f, r * 0.84f),
                style = Stroke(width = s * 0.045f)
            )

            // 6. Orbiting Bilingual Tags ('A' and 'অ')
            val rad = Math.toRadians(orbitAngle.toDouble())
            val tagAx = cx + (r * 1.35f) * Math.cos(rad).toFloat()
            val tagAy = cy + (r * 0.42f) * Math.sin(rad).toFloat()

            val radBn = Math.toRadians((orbitAngle + 180.0) % 360.0)
            val tagBnx = cx + (r * 1.35f) * Math.cos(radBn).toFloat()
            val tagBny = cy + (r * 0.42f) * Math.sin(radBn).toFloat()

            drawIntoCanvas { canvas ->
                val paintA = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = s * 0.22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(4f, 0f, 1f, android.graphics.Color.parseColor("#0284C7"))
                }
                val paintBn = Paint().apply {
                    color = android.graphics.Color.parseColor("#34D399")
                    textSize = s * 0.20f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(4f, 0f, 1f, android.graphics.Color.parseColor("#065F46"))
                }

                canvas.nativeCanvas.drawText("A", tagAx, tagAy + s * 0.07f, paintA)
                canvas.nativeCanvas.drawText("অ", tagBnx, tagBny + s * 0.07f, paintBn)
            }
        }
    }
}

/**
 * 2. 3D Celestial Yin-Yang Eclipse Orb (Golden Sun Corona + Obsidian Moon).
 * Used for App Theme setting.
 */
@Composable
fun Realistic3DThemeEclipseIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ThemeEclipseTransition")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingScale"
    )
    val coronaPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoronaPulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val r = (s * 0.40f) * breathingScale

            // 1. Solar Corona Outer Flares
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE047).copy(alpha = coronaPulse),
                        Color(0xFFF59E0B).copy(alpha = coronaPulse * 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(cx - r * 0.4f, cy - r * 0.4f),
                    radius = r * 1.5f
                ),
                center = Offset(cx, cy),
                radius = r * 1.5f
            )

            // 2. Base Sphere Split Yin-Yang (Solar Dawn & Obsidian Night)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFBBF24), // Golden Amber Sun
                        Color(0xFFEA580C),
                        Color(0xFF1E1B4B), // Cosmic Indigo Deep Space
                        Color(0xFF0F172A)
                    ),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r)
                ),
                center = Offset(cx, cy),
                radius = r
            )

            // 3. Solar Hemisphere Specular Flare
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.75f), Color.Transparent),
                    center = Offset(cx - r * 0.45f, cy - r * 0.45f),
                    radius = r * 0.5f
                ),
                center = Offset(cx - r * 0.4f, cy - r * 0.4f),
                radius = r * 0.35f
            )

            // 4. Shimmering Obsidian Crescent Moon Arc
            drawArc(
                color = Color(0xFFE2E8F0),
                startAngle = 30f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.25f, cy - r * 0.25f),
                size = Size(r * 1.15f, r * 1.15f),
                style = Stroke(width = s * 0.08f, cap = StrokeCap.Round)
            )

            // 5. Stardust Cosmic Sparkles
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                center = Offset(cx + r * 0.45f, cy - r * 0.3f),
                radius = s * 0.025f
            )
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.8f),
                center = Offset(cx + r * 0.55f, cy + r * 0.35f),
                radius = s * 0.02f
            )
        }
    }
}

/**
 * 3. 3D Isometric Space-Gray POS Tablet & Smartphone Suite.
 * Used for UI Layout & Terminal Mode setting.
 */
@Composable
fun Realistic3DDeviceLayoutIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DeviceFloatTransition")
    val floatY by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DeviceFloatY"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f + floatY * (s / 32f)

            // 1. Tablet Chassis (Space Gray Titanium Bevel)
            val tabW = s * 0.68f
            val tabH = s * 0.52f
            val tabX = cx - tabW * 0.62f
            val tabY = cy - tabH * 0.58f

            // Drop Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(tabX + s * 0.03f, tabY + s * 0.04f),
                size = Size(tabW, tabH),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )

            // Tablet Outer Frame
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF475569), Color(0xFF1E293B)),
                    start = Offset(tabX, tabY),
                    end = Offset(tabX + tabW, tabY + tabH)
                ),
                topLeft = Offset(tabX, tabY),
                size = Size(tabW, tabH),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
                style = Stroke(width = s * 0.045f)
            )

            // Tablet Display Screen
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B)),
                    start = Offset(tabX, tabY),
                    end = Offset(tabX + tabW, tabY + tabH)
                ),
                topLeft = Offset(tabX + s * 0.03f, tabY + s * 0.03f),
                size = Size(tabW - s * 0.06f, tabH - s * 0.06f),
                cornerRadius = CornerRadius(s * 0.06f, s * 0.06f)
            )

            // Tablet POS Header Bar
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                ),
                topLeft = Offset(tabX + s * 0.06f, tabY + s * 0.06f),
                size = Size(tabW - s * 0.12f, s * 0.08f),
                cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
            )

            // Tablet Grid Columns (Responsive Layout Wireframe)
            val colW = (tabW - s * 0.20f) / 3f
            for (i in 0..2) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset(tabX + s * 0.06f + i * (colW + s * 0.04f), tabY + s * 0.18f),
                    size = Size(colW, tabH - s * 0.26f),
                    cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
                )
            }

            // 2. Docked Smartphone in Foreground (Cyan Glass Bezel)
            val phW = s * 0.32f
            val phH = s * 0.58f
            val phX = cx + s * 0.10f
            val phY = cy - phH * 0.32f

            // Phone Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.6f),
                topLeft = Offset(phX + s * 0.02f, phY + s * 0.03f),
                size = Size(phW, phH),
                cornerRadius = CornerRadius(s * 0.07f, s * 0.07f)
            )

            // Phone Outer Frame
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7)),
                    start = Offset(phX, phY),
                    end = Offset(phX + phW, phY + phH)
                ),
                topLeft = Offset(phX, phY),
                size = Size(phW, phH),
                cornerRadius = CornerRadius(s * 0.07f, s * 0.07f),
                style = Stroke(width = s * 0.04f)
            )

            // Phone Screen
            drawRoundRect(
                color = Color(0xFF0B1120),
                topLeft = Offset(phX + s * 0.02f, phY + s * 0.02f),
                size = Size(phW - s * 0.04f, phH - s * 0.04f),
                cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
            )

            // Phone Status Dot & Mock Item
            drawCircle(
                color = Color(0xFF34D399),
                center = Offset(phX + phW * 0.5f, phY + s * 0.07f),
                radius = s * 0.025f
            )
            drawRoundRect(
                color = Color(0xFF38BDF8).copy(alpha = 0.5f),
                topLeft = Offset(phX + s * 0.06f, phY + s * 0.14f),
                size = Size(phW - s * 0.12f, s * 0.06f),
                cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
            )
        }
    }
}

/**
 * 4. 3D Isometric Corrugated Carton with Pulsing Ruby Warning Shield ('-1').
 * Used for Allow Negative Inventory setting.
 */
@Composable
fun Realistic3DNegativeStockWarningIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WarningPulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WarningPulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            // 1. Isometric Kraft Box Base
            val boxW = s * 0.65f
            val boxH = s * 0.55f
            val bx = cx - boxW * 0.58f
            val by = cy - boxH * 0.35f

            // Box Ambient Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(bx + s * 0.03f, by + s * 0.04f),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(s * 0.06f, s * 0.06f)
            )

            // Box Body (Kraft Paper Gradient)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFD97706), Color(0xFF92400E)),
                    start = Offset(bx, by),
                    end = Offset(bx + boxW, by + boxH)
                ),
                topLeft = Offset(bx, by),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(s * 0.06f, s * 0.06f)
            )

            // Industrial Packaging Tape Strip
            drawRect(
                color = Color(0xFFFDE68A).copy(alpha = 0.85f),
                topLeft = Offset(bx, by + boxH * 0.38f),
                size = Size(boxW, s * 0.08f)
            )

            // 2. Floating Ruby Warning Shield
            val shW = (s * 0.44f) * pulseScale
            val shH = (s * 0.38f) * pulseScale
            val shX = cx + s * 0.04f
            val shY = cy - s * 0.38f

            // Red Glow Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFEF4444).copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(shX + shW * 0.5f, shY + shH * 0.5f),
                    radius = shW * 0.9f
                ),
                center = Offset(shX + shW * 0.5f, shY + shH * 0.5f),
                radius = shW * 0.9f
            )

            // Shield Container
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFF87171), Color(0xFFDC2626)),
                    start = Offset(shX, shY),
                    end = Offset(shX + shW, shY + shH)
                ),
                topLeft = Offset(shX, shY),
                size = Size(shW, shH),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )
            drawRoundRect(
                color = Color(0xFFFCA5A5),
                topLeft = Offset(shX, shY),
                size = Size(shW, shH),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
                style = Stroke(width = s * 0.035f)
            )

            // Draw "-1" inside the warning shield
            drawIntoCanvas { canvas ->
                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = shH * 0.58f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText(
                    "-1",
                    shX + shW * 0.5f,
                    shY + shH * 0.72f,
                    textPaint
                )
            }
        }
    }
}

/**
 * 5. 3D Brushed Stainless Cash Drawer Kick with Physical Recoil Physics.
 * Used for Automatic Cash Drawer Kick setting.
 */
@Composable
fun Realistic3DCashDrawerKickIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    isKicking: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "DrawerKickTransition")
    val idleRecoil by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isKicking) 4.5f else 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isKicking) 300 else 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "DrawerIdleRecoil"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            val dW = s * 0.78f
            val dH = s * 0.44f
            val dX = cx - dW * 0.5f
            val dY = cy - dH * 0.5f

            // 1. Heavy Stainless Outer Housing (Brushed Steel Gradient)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF64748B), Color(0xFF334155), Color(0xFF1E293B)),
                    start = Offset(dX, dY),
                    end = Offset(dX + dW, dY + dH)
                ),
                topLeft = Offset(dX, dY),
                size = Size(dW, dH),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f)
            )
            drawRoundRect(
                color = Color(0xFF94A3B8).copy(alpha = 0.6f),
                topLeft = Offset(dX, dY),
                size = Size(dW, dH),
                cornerRadius = CornerRadius(s * 0.08f, s * 0.08f),
                style = Stroke(width = s * 0.035f)
            )

            // 2. Sliding Ejection Cash Tray (Recoil Animation)
            val trayW = dW * 0.88f
            val trayH = dH * 0.55f
            val trayX = cx - trayW * 0.5f
            val trayY = dY + dH * 0.42f + idleRecoil * (s / 30f)

            // Tray Base
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF38BDF8), Color(0xFF0369A1)),
                    start = Offset(trayX, trayY),
                    end = Offset(trayX + trayW, trayY + trayH)
                ),
                topLeft = Offset(trayX, trayY),
                size = Size(trayW, trayH),
                cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
            )

            // Currency Partitions: Green Notes + Gold Coins
            drawRoundRect(
                color = Color(0xFF10B981),
                topLeft = Offset(trayX + s * 0.04f, trayY + s * 0.04f),
                size = Size(trayW * 0.35f, trayH - s * 0.08f),
                cornerRadius = CornerRadius(s * 0.02f, s * 0.02f)
            )
            drawCircle(
                color = Color(0xFFF59E0B),
                center = Offset(trayX + trayW * 0.68f, trayY + trayH * 0.5f),
                radius = s * 0.055f
            )

            // 3. Solenoid Electric Spark
            drawIntoCanvas { canvas ->
                val sparkPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#FDE047")
                    textSize = s * 0.26f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText("⚡", cx + dW * 0.42f, dY + s * 0.14f, sparkPaint)
            }
        }
    }
}

/**
 * 6. 3D Desktop Thermal Receipt Printer with Spooling Slip & RF Beacon.
 * Used for Bluetooth Printer row.
 */
@Composable
fun Realistic3DBluetoothPrinterIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    isConnected: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PrinterSpoolTransition")
    val spoolProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PaperSpool"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f

            val pW = s * 0.72f
            val pH = s * 0.56f
            val pX = cx - pW * 0.5f
            val pY = cy - pH * 0.35f

            // 1. Printer Body (Matte Obsidian Industrial Chassis)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF0F172A)),
                    start = Offset(pX, pY),
                    end = Offset(pX + pW, pY + pH)
                ),
                topLeft = Offset(pX, pY),
                size = Size(pW, pH),
                cornerRadius = CornerRadius(s * 0.10f, s * 0.10f)
            )
            drawRoundRect(
                color = Color(0xFF64748B).copy(alpha = 0.5f),
                topLeft = Offset(pX, pY),
                size = Size(pW, pH),
                cornerRadius = CornerRadius(s * 0.10f, s * 0.10f),
                style = Stroke(width = s * 0.035f)
            )

            // 2. Paper Feed Slot & Spooling Receipt Slip
            val paperW = pW * 0.48f
            val paperH = s * 0.28f + spoolProgress * (s * 0.06f)
            val paperX = cx - paperW * 0.5f
            val paperY = pY - paperH * 0.75f

            drawRoundRect(
                color = Color(0xFFF8FAFC),
                topLeft = Offset(paperX, paperY),
                size = Size(paperW, paperH),
                cornerRadius = CornerRadius(s * 0.03f, s * 0.03f)
            )
            // Printed dashed receipt text lines
            for (i in 0..2) {
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(paperX + s * 0.04f, paperY + s * 0.06f + i * s * 0.06f),
                    end = Offset(paperX + paperW - s * 0.04f, paperY + s * 0.06f + i * s * 0.06f),
                    strokeWidth = s * 0.02f
                )
            }

            // 3. Bluetooth RF Beacon Halo
            val beaconColor = if (isConnected) Color(0xFF10B981) else Color(0xFF0284C7)
            drawCircle(
                color = beaconColor,
                center = Offset(pX + pW * 0.85f, pY + pH * 0.25f),
                radius = s * 0.065f
            )
            drawCircle(
                color = beaconColor.copy(alpha = 0.35f),
                center = Offset(pX + pW * 0.85f, pY + pH * 0.25f),
                radius = s * 0.12f,
                style = Stroke(width = s * 0.02f)
            )
        }
    }
}

/**
 * 7. 3D High-Voltage Brass Solenoid Core with Particle Burst.
 * Used inside the Test Kick Button.
 */
@Composable
fun Realistic3DVoltPulseKickIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoltPulseTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "VoltGlow"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val r = (s * 0.42f) * pulseGlow

            // Energy Glow Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFDE047).copy(alpha = 0.7f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 1.4f
                ),
                center = Offset(cx, cy),
                radius = r * 1.4f
            )

            // Brass Solenoid Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFF59E0B), Color(0xFF78350F)),
                    center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                    radius = r * 1.2f
                ),
                center = Offset(cx, cy),
                radius = r
            )

            // Electric Spark Bolt
            drawIntoCanvas { canvas ->
                val boltPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = s * 0.55f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.parseColor("#FDE047"))
                }
                canvas.nativeCanvas.drawText("⚡", cx, cy + s * 0.18f, boltPaint)
            }
        }
    }
}

/**
 * 8. 3D Obsidian Security Portal Vault.
 * Used for the Executive Logout Card.
 */
@Composable
fun Realistic3DLogoutVaultIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VaultRotationTransition")
    val portalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PortalAngle"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val r = s * 0.40f

            // Ruby Laser Corona Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF43F5E).copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 1.4f
                ),
                center = Offset(cx, cy),
                radius = r * 1.4f
            )

            // Obsidian Vault Portal Outer Body
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF000000)),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r)
                ),
                center = Offset(cx, cy),
                radius = r
            )
            drawCircle(
                color = Color(0xFFF43F5E),
                center = Offset(cx, cy),
                radius = r,
                style = Stroke(width = s * 0.05f)
            )

            // Rotating Directional Portal Arrow
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.rotate(portalAngle, cx, cy)
                val arrowPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#FDA4AF")
                    textSize = s * 0.42f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.parseColor("#F43F5E"))
                }
                canvas.nativeCanvas.drawText("➔", cx, cy + s * 0.14f, arrowPaint)
                canvas.restore()
            }
        }
    }
}

/**
 * 9. 3D Golden Tax & VAT Coin with Milled Ledger Rim.
 * Used for Tax & VAT setting (Bugfix replacing duplicated printer icon).
 */
@Composable
fun Realistic3DTaxCalculatorIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TaxCoinTransition")
    val tiltAngle by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "TaxCoinTilt"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val r = s * 0.42f

            // Golden Glow Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFDE047).copy(alpha = 0.5f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r * 1.35f
                ),
                center = Offset(cx, cy),
                radius = r * 1.35f
            )

            // 3D Gold Coin Base
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFEF08A), Color(0xFFEAB308), Color(0xFFA16207), Color(0xFF713F12)),
                    center = Offset(cx - r * 0.35f, cy - r * 0.35f),
                    radius = r * 1.25f
                ),
                center = Offset(cx, cy),
                radius = r
            )
            drawCircle(
                color = Color(0xFFCA8A04),
                center = Offset(cx, cy),
                radius = r,
                style = Stroke(width = s * 0.045f)
            )

            // Bold '%' Symbol in Center
            drawIntoCanvas { canvas ->
                val percentPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#78350F")
                    textSize = s * 0.44f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(2f, 1f, 1f, android.graphics.Color.parseColor("#FEF08A"))
                }
                canvas.nativeCanvas.drawText("%", cx, cy + s * 0.15f, percentPaint)
            }
        }
    }
}

/**
 * 10. 3D Cobalt Security Shield with Keypad Vault.
 * Used for Staff PINs & Roles (Upgrade replacing generic gear icon).
 */
@Composable
fun Realistic3DStaffShieldLockIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ShieldPulseTransition")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShieldPulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = s / 2f
            val cy = s / 2f
            val w = (s * 0.65f) * pulse
            val h = (s * 0.75f) * pulse

            // Cobalt Shield Path
            val shieldPath = Path().apply {
                moveTo(cx, cy - h * 0.5f)
                lineTo(cx + w * 0.5f, cy - h * 0.3f)
                lineTo(cx + w * 0.48f, cy + h * 0.2f)
                lineTo(cx, cy + h * 0.5f)
                lineTo(cx - w * 0.48f, cy + h * 0.2f)
                lineTo(cx - w * 0.5f, cy - h * 0.3f)
                close()
            }

            // Shield Ambient Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = h * 0.8f
                ),
                center = Offset(cx, cy),
                radius = h * 0.8f
            )

            // Shield Body Gradient
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1E3A8A)),
                    start = Offset(cx, cy - h * 0.5f),
                    end = Offset(cx, cy + h * 0.5f)
                )
            )
            drawPath(
                path = shieldPath,
                color = Color(0xFF93C5FD),
                style = Stroke(width = s * 0.04f)
            )

            // Center Lock Icon
            drawIntoCanvas { canvas ->
                val lockPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = s * 0.34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(4f, 0f, 1f, android.graphics.Color.parseColor("#1E3A8A"))
                }
                canvas.nativeCanvas.drawText("🔒", cx, cy + s * 0.12f, lockPaint)
            }
        }
    }
}
