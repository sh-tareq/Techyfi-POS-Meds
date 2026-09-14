package com.example.techyfipos.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isTablet: Boolean = false,
    onSplashFinished: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTabletDevice = isTablet || screenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Dynamic animated scale and alpha for logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "alpha"
    )

    // Dynamic loading messages
    var loadingStatusText by remember { mutableStateOf("Initializing Techyfi POS...") }

    LaunchedEffect(Unit) {
        delay(800)
        loadingStatusText = "Loading Product Catalog..."
        delay(900)
        loadingStatusText = "Preparing Sell..."
        delay(900)
        onSplashFinished()
    }

    // Adaptive logo sizing - ensures logo remains proportional across both phones and tablets
    val logoSize: Dp = when {
        isTabletDevice && isLandscape -> 160.dp
        isTabletDevice -> 190.dp
        isLandscape -> 120.dp
        else -> 145.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709))
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Specular ambient radial glow behind logo
        Box(
            modifier = Modifier
                .size(if (isTabletDevice) 520.dp else 360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0082FF).copy(alpha = glowAlpha),
                            Color(0xFF0082FF).copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Unified centered column: Logo + Spacing + Loading status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.wrapContentSize()
        ) {
            TechyfiSplashLogo(
                logoSize = logoSize,
                isTablet = isTabletDevice,
                modifier = Modifier
                    .scale(pulseScale)
                    .alpha(logoAlpha)
            )

            Spacer(
                modifier = Modifier.height(
                    when {
                        isTabletDevice && isLandscape -> 28.dp
                        isTabletDevice -> 40.dp
                        else -> 24.dp
                    }
                )
            )

            // Dynamic Loading Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isTabletDevice) 38.dp else 30.dp),
                    color = Color(0xFF0082FF),
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = loadingStatusText,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = if (isTabletDevice) 15.sp else 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic progress bar indicator
                val progress by animateFloatAsState(
                    targetValue = when (loadingStatusText) {
                        "Initializing Techyfi POS..." -> 0.35f
                        "Loading Product Catalog..." -> 0.75f
                        else -> 1.0f
                    },
                    animationSpec = tween(700),
                    label = "progress"
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(if (isTabletDevice) 220.dp else 160.dp)
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF0082FF),
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        }

        // Subdued enterprise footer safely anchored above navigation insets
        Text(
            text = "Enterprise POS • Secure Offline Engine",
            color = Color(0xFF475569),
            fontSize = if (isTabletDevice) 11.5.sp else 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun TechyfiSplashLogo(
    logoSize: Dp,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Techyfi Logo",
            modifier = Modifier.size(logoSize)
        )

        Spacer(modifier = Modifier.height(if (isTablet) 8.dp else 4.dp))

        Text(
            text = buildAnnotatedString {
                append("TECHY")
                withStyle(SpanStyle(color = Color(0xFF0082FF))) {
                    append("FI")
                }
            },
            color = Color.White,
            fontSize = if (isTablet) 28.sp else 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (isTablet) 2.2.sp else 1.6.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(if (isTablet) 6.dp else 4.dp))

        Text(
            text = "POWERING YOUR DIGITAL LIFESTYLE",
            color = Color(0xFF94A3B8),
            fontSize = if (isTablet) 10.5.sp else 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (isTablet) 1.5.sp else 1.1.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
