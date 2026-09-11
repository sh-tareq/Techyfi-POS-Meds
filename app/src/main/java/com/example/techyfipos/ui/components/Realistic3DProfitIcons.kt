package com.example.techyfipos.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 1. 3D Owner Profit Vault Core
 * Hexagonal gold chamfered chassis, glowing emerald crystal core with glint sweep,
 * upward 3D growth arrow, and rotating orbital ring with gold coins.
 */
@Composable
fun Realistic3DProfitVaultIcon(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vault_anim")
    val rotation by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vault_spin"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val glintOffset by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = -0.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vault_glint"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.3f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.44f

        // Outer Hexagonal Gold Chassis
        val hexPath = Path()
        for (i in 0 until 6) {
            val angle = (i * 60f - 30f) * (PI / 180f).toFloat()
            val x = cx + r * cos(angle)
            val y = cy + r * sin(angle)
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()

        // Gold 3D Drop Shadow
        drawPath(
            path = hexPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF78350F), Color(0xFF451A03)),
                center = Offset(cx, cy + s * 0.08f),
                radius = r * 1.1f
            )
        )

        // Gold Bevel Front Plate
        drawPath(
            path = hexPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFB45309)),
                start = Offset(cx - r, cy - r),
                end = Offset(cx + r, cy + r)
            )
        )

        // Inner Emerald Crystal Chamber
        val innerR = r * 0.72f
        val innerHex = Path()
        for (i in 0 until 6) {
            val angle = (i * 60f - 30f) * (PI / 180f).toFloat()
            val x = cx + innerR * cos(angle)
            val y = cy + innerR * sin(angle)
            if (i == 0) innerHex.moveTo(x, y) else innerHex.lineTo(x, y)
        }
        innerHex.close()

        drawPath(
            path = innerHex,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF064E3B)),
                center = Offset(cx, cy - innerR * 0.2f),
                radius = innerR * 1.05f
            )
        )

        // Emerald Glint Sweep
        val glintX = cx + (glintOffset - 0.5f) * s * 1.2f
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent),
                start = Offset(glintX - s * 0.15f, cy - innerR),
                end = Offset(glintX + s * 0.15f, cy + innerR)
            ),
            start = Offset(glintX - s * 0.12f, cy - innerR * 0.7f),
            end = Offset(glintX + s * 0.12f, cy + innerR * 0.7f),
            strokeWidth = s * 0.08f,
            cap = StrokeCap.Round
        )

        // 3D Upward Growth Arrow (▲)
        val arrowPath = Path().apply {
            moveTo(cx, cy - innerR * 0.48f)
            lineTo(cx + innerR * 0.38f, cy + innerR * 0.22f)
            lineTo(cx + innerR * 0.14f, cy + innerR * 0.22f)
            lineTo(cx + innerR * 0.14f, cy + innerR * 0.46f)
            lineTo(cx - innerR * 0.14f, cy + innerR * 0.46f)
            lineTo(cx - innerR * 0.14f, cy + innerR * 0.22f)
            lineTo(cx - innerR * 0.38f, cy + innerR * 0.22f)
            close()
        }
        drawPath(
            path = arrowPath,
            brush = Brush.linearGradient(
                colors = listOf(Color.White, Color(0xFFFEF3C7), Color(0xFFFBBF24)),
                start = Offset(cx, cy - innerR * 0.5f),
                end = Offset(cx, cy + innerR * 0.5f)
            )
        )

        // Dual Counter-Rotating Orbital Rings with Minted Gold Beads
        val rad = (rotation * (PI / 180f)).toFloat()
        val orbitRx = r * 1.22f
        val orbitRy = r * 0.42f
        drawOval(
            brush = Brush.sweepGradient(
                colors = listOf(Color(0xFFFDE68A).copy(alpha = 0.2f), Color(0xFFF59E0B), Color(0xFFFDE68A).copy(alpha = 0.2f)),
                center = Offset(cx, cy)
            ),
            topLeft = Offset(cx - orbitRx, cy - orbitRy),
            size = Size(orbitRx * 2f, orbitRy * 2f),
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Orbiting Gold Coin Bead 1
        val beadX1 = cx + orbitRx * cos(rad)
        val beadY1 = cy + orbitRy * sin(rad)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFBEB), Color(0xFFF59E0B), Color(0xFF92400E)),
                center = Offset(beadX1 - s * 0.02f, beadY1 - s * 0.02f),
                radius = s * 0.07f
            ),
            radius = s * 0.07f,
            center = Offset(beadX1, beadY1)
        )

        // Orbiting Gold Coin Bead 2 (Opposite)
        val beadX2 = cx - orbitRx * cos(rad)
        val beadY2 = cy - orbitRy * sin(rad)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFBEB), Color(0xFFF59E0B), Color(0xFF92400E)),
                center = Offset(beadX2 - s * 0.02f, beadY2 - s * 0.02f),
                radius = s * 0.055f
            ),
            radius = s * 0.055f,
            center = Offset(beadX2, beadY2)
        )
    }
}

/**
 * 2. ↩️ Realistic 3D Return & Refund Orb
 * Volumetric ruby sphere with specular light glint, glowing equator, and
 * metallic counter-rotating reversal curved arrow.
 */
@Composable
fun Realistic3DReturnOrbIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    animated: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "return_orb_anim")
    val pulseAlpha by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.75f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    val spinAngle by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin_arrow"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.42f

        // 1. Drop Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEF4444).copy(alpha = 0.35f * pulseAlpha), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.35f
            ),
            radius = r * 1.35f,
            center = Offset(cx, cy)
        )

        // 2. 3D Volumetric Ruby Sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFCA5A5), // Highlight
                    Color(0xFFEF4444), // Primary ruby
                    Color(0xFFB91C1C), // Deep crimson shadow
                    Color(0xFF450A0A)  // Rim ambient shadow
                ),
                center = Offset(cx - r * 0.32f, cy - r * 0.32f),
                radius = r * 1.25f
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        // 3. Specular Glint (Gloss Reflection on top-left)
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                start = Offset(cx - r * 0.5f, cy - r * 0.5f),
                end = Offset(cx - r * 0.1f, cy - r * 0.1f)
            ),
            topLeft = Offset(cx - r * 0.65f, cy - r * 0.65f),
            size = Size(r * 0.65f, r * 0.38f)
        )

        // 4. Glowing Equator Belt
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFECACA).copy(alpha = 0.15f), Color(0xFFF87171).copy(alpha = 0.8f), Color(0xFFFECACA).copy(alpha = 0.15f)),
                start = Offset(cx - r, cy),
                end = Offset(cx + r, cy)
            ),
            topLeft = Offset(cx - r * 0.95f, cy - r * 0.22f),
            size = Size(r * 1.9f, r * 0.44f),
            style = Stroke(width = 1.dp.toPx())
        )

        // 5. Metallic Reversal Curved Arrow (↩️)
        val arrowAngleRad = (spinAngle * (PI / 180f)).toFloat()
        val arcR = r * 0.58f
        val arcPath = Path().apply {
            val startAngle = arrowAngleRad
            val sweep = 210f * (PI / 180f).toFloat()
            for (step in 0..12) {
                val t = step / 12f
                val a = startAngle + sweep * t
                val x = cx + arcR * cos(a)
                val y = cy + arcR * 0.82f * sin(a)
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = arcPath,
            color = Color.White.copy(alpha = 0.95f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Reversal Arrowhead
        val headAngle = arrowAngleRad
        val headX = cx + arcR * cos(headAngle)
        val headY = cy + arcR * 0.82f * sin(headAngle)
        val headPath = Path().apply {
            moveTo(headX, headY)
            lineTo(headX - s * 0.14f * cos(headAngle - 0.6f), headY - s * 0.14f * sin(headAngle - 0.6f))
            lineTo(headX - s * 0.14f * cos(headAngle + 0.6f), headY - s * 0.14f * sin(headAngle + 0.6f))
            close()
        }
        drawPath(path = headPath, color = Color.White)
    }
}

/**
 * 3. 🛡️ 3D Cyber Stealth Privacy Shield
 * Obsidian & sapphire defense shield.
 * When isStealthActive is true: brushed-steel mechanical shutter plates close
 * and a 3D gold padlock locks in the center.
 */
@Composable
fun Realistic3DStealthShieldIcon(
    isStealthActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val w = s * 0.78f
        val h = s * 0.88f

        // Shield Contour Path
        val shieldPath = Path().apply {
            moveTo(cx, cy - h / 2f)
            lineTo(cx + w / 2f, cy - h / 2f + h * 0.18f)
            lineTo(cx + w / 2f, cy + h * 0.12f)
            cubicTo(
                cx + w / 2f, cy + h * 0.35f,
                cx + w * 0.22f, cy + h * 0.46f,
                cx, cy + h / 2f
            )
            cubicTo(
                cx - w * 0.22f, cy + h * 0.46f,
                cx - w / 2f, cy + h * 0.35f,
                cx - w / 2f, cy + h * 0.12f
            )
            lineTo(cx - w / 2f, cy - h / 2f + h * 0.18f)
            close()
        }

        if (isStealthActive) {
            // STEALTH ACTIVE: Heavy Brushed Steel Armor + 3D Padlock
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF64748B), Color(0xFF334155), Color(0xFF0F172A)),
                    start = Offset(cx - w / 2f, cy - h / 2f),
                    end = Offset(cx + w / 2f, cy + h / 2f)
                )
            )

            // Gold Outer Bevel Rim
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFD97706), Color(0xFF78350F)),
                    start = Offset(cx, cy - h / 2f),
                    end = Offset(cx, cy + h / 2f)
                ),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // Mechanical Shutter Plates (Horizontal armor grooves)
            val grooveY1 = cy - h * 0.16f
            val grooveY2 = cy + h * 0.08f
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(cx - w * 0.38f, grooveY1),
                end = Offset(cx + w * 0.38f, grooveY1),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = Color(0xFF94A3B8).copy(alpha = 0.4f),
                start = Offset(cx - w * 0.38f, grooveY1 + 1f),
                end = Offset(cx + w * 0.38f, grooveY1 + 1f),
                strokeWidth = 0.6.dp.toPx()
            )

            // 3D Gold Padlock Body
            val lockW = w * 0.38f
            val lockH = h * 0.28f
            val lockY = cy - lockH * 0.15f
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFB45309)),
                    start = Offset(cx - lockW / 2f, lockY),
                    end = Offset(cx + lockW / 2f, lockY + lockH)
                ),
                topLeft = Offset(cx - lockW / 2f, lockY),
                size = Size(lockW, lockH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // Padlock Shackle
            val shackleR = lockW * 0.32f
            drawOval(
                color = Color(0xFFE2E8F0),
                topLeft = Offset(cx - shackleR, lockY - shackleR * 1.35f),
                size = Size(shackleR * 2f, shackleR * 1.8f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Keyhole
            drawCircle(
                color = Color(0xFF451A03),
                radius = 1.5.dp.toPx(),
                center = Offset(cx, lockY + lockH * 0.42f)
            )
        } else {
            // UNMASKED / VIBRANT MODE: Obsidian & Cyan Cybernetic Iris
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0369A1)),
                    start = Offset(cx - w / 2f, cy - h / 2f),
                    end = Offset(cx + w / 2f, cy + h / 2f)
                )
            )

            // Cyan Energy Rim
            drawPath(
                path = shieldPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF67E8F9), Color(0xFF0284C7), Color(0xFF0369A1)),
                    start = Offset(cx, cy - h / 2f),
                    end = Offset(cx, cy + h / 2f)
                ),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // Cyan Reticle Rings
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.6f),
                radius = w * 0.22f,
                center = Offset(cx, cy - h * 0.05f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE0F2FE), Color(0xFF0EA5E9), Color(0xFF0369A1)),
                    center = Offset(cx, cy - h * 0.05f),
                    radius = w * 0.12f
                ),
                radius = w * 0.12f,
                center = Offset(cx, cy - h * 0.05f)
            )
        }
    }
}

/**
 * 4. 🔷 3D High-Margin Emerald Gemstone
 * Brilliant-cut octagonal emerald gemstone with faceted specular planes.
 */
@Composable
fun Realistic3DHighMarginGemIcon(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.44f

        val pts = (0..7).map { i ->
            val a = (i * 45f + 22.5f) * (PI / 180f).toFloat()
            Offset(cx + r * cos(a), cy + r * sin(a))
        }

        val gemPath = Path().apply {
            pts.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }

        drawPath(
            path = gemPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF064E3B)),
                center = Offset(cx - r * 0.2f, cy - r * 0.2f),
                radius = r * 1.1f
            )
        )

        val innerPts = (0..7).map { i ->
            val a = (i * 45f + 22.5f) * (PI / 180f).toFloat()
            Offset(cx + r * 0.52f * cos(a), cy + r * 0.52f * sin(a))
        }
        val innerPath = Path().apply {
            innerPts.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(
            path = innerPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFA7F3D0), Color(0xFF10B981)),
                start = Offset(cx - r * 0.5f, cy - r * 0.5f),
                end = Offset(cx + r * 0.5f, cy + r * 0.5f)
            )
        )

        for (i in 0..7) {
            drawLine(
                color = Color(0xFFD1FAE5).copy(alpha = 0.65f),
                start = innerPts[i],
                end = pts[i],
                strokeWidth = 0.8.dp.toPx()
            )
        }

        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = 1.dp.toPx(),
            center = Offset(cx - r * 0.22f, cy - r * 0.22f)
        )
    }
}

/**
 * 5. 🟠 3D Low-Margin Amber Warning Core
 * Molten orange sphere with rotating brass gyroscopic caution ring.
 */
@Composable
fun Realistic3DLowMarginCoreIcon(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.4f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFED7AA), Color(0xFFF97316), Color(0xFFC2410C), Color(0xFF7C2D12)),
                center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                radius = r * 1.2f
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFD97706), Color(0xFF78350F)),
                start = Offset(cx - r * 1.2f, cy - r * 0.4f),
                end = Offset(cx + r * 1.2f, cy + r * 0.4f)
            ),
            topLeft = Offset(cx - r * 1.15f, cy - r * 0.38f),
            size = Size(r * 2.3f, r * 0.76f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/**
 * 6. 🔻 3D Deficit / Loss Crimson Prism
 * Inverted triangular prism with glowing red edge wires.
 */
@Composable
fun Realistic3DLossPrismIcon(
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val w = s * 0.82f
        val h = s * 0.74f

        val p1 = Offset(cx - w / 2f, cy - h / 2f)
        val p2 = Offset(cx + w / 2f, cy - h / 2f)
        val p3 = Offset(cx, cy + h / 2f)

        val prismPath = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }

        drawPath(
            path = prismPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF87171), Color(0xFFDC2626), Color(0xFF7F1D1D)),
                startY = cy - h / 2f,
                endY = cy + h / 2f
            )
        )

        drawPath(
            path = prismPath,
            color = Color(0xFFFECACA),
            style = Stroke(width = 1.dp.toPx())
        )

        drawLine(
            color = Color.White,
            start = Offset(cx, cy - h * 0.28f),
            end = Offset(cx, cy + h * 0.15f),
            strokeWidth = 1.3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * 7. 🪙 3D Minted Gold Taka Bullion Coin
 * 24K minted gold bullion coin with reeded rim and embossed Bengali `৳` symbol.
 */
@Composable
fun Realistic3DGoldTakaCoinIcon(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val r = s * 0.44f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFF92400E)),
                center = Offset(cx, cy),
                radius = r
            ),
            radius = r,
            center = Offset(cx, cy)
        )

        val innerR = r * 0.82f
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFBEB), Color(0xFFFBBF24), Color(0xFFD97706)),
                start = Offset(cx - innerR, cy - innerR),
                end = Offset(cx + innerR, cy + innerR)
            ),
            radius = innerR,
            center = Offset(cx, cy)
        )

        drawCircle(
            color = Color(0xFF78350F).copy(alpha = 0.5f),
            radius = innerR,
            center = Offset(cx, cy),
            style = Stroke(width = 0.8.dp.toPx())
        )

        drawArc(
            color = Color.White.copy(alpha = 0.75f),
            startAngle = -120f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(cx - innerR * 0.85f, cy - innerR * 0.85f),
            size = Size(innerR * 1.7f, innerR * 1.7f),
            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * 8. ⭐ 3D Top Contributor Star Trophy
 * Volumetric 5-pointed faceted gold star with light-reflecting facet shading.
 */
@Composable
fun Realistic3DTopContributorStarIcon(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val cx = s / 2f
        val cy = s / 2f
        val outerR = s * 0.46f
        val innerR = outerR * 0.42f

        val starPath = Path()
        for (i in 0 until 10) {
            val a = (i * 36f - 90f) * (PI / 180f).toFloat()
            val rad = if (i % 2 == 0) outerR else innerR
            val x = cx + rad * cos(a)
            val y = cy + rad * sin(a)
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()

        drawPath(
            path = starPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFFBEB), Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFF78350F)),
                center = Offset(cx, cy - outerR * 0.2f),
                radius = outerR * 1.15f
            )
        )

        for (i in 0 until 5) {
            val a = (i * 72f - 90f) * (PI / 180f).toFloat()
            val tipX = cx + outerR * cos(a)
            val tipY = cy + outerR * sin(a)
            drawLine(
                color = Color(0xFFFFFBEB).copy(alpha = 0.8f),
                start = Offset(cx, cy),
                end = Offset(tipX, tipY),
                strokeWidth = 0.8.dp.toPx()
            )
        }
    }
}
