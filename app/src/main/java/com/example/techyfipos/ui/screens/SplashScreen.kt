package com.example.techyfipos.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    // Dynamic animated scale and alpha for logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = LinearOutSlowInEasing),
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070709)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.weight(1.2f))

            // Keep the splash artwork vector/text based so it remains sharp at
            // every density and throughout the scale animation.
            TechyfiSplashLogo(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .scale(pulseScale)
                    .alpha(logoAlpha)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Dynamic Loading Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 56.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFF0082FF),
                    strokeWidth = 3.5.dp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = loadingStatusText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

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
                        .width(180.dp)
                        .height(3.5.dp),
                    color = Color(0xFF0082FF),
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        }
    }
}

@Composable
private fun TechyfiSplashLogo(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Techyfi Logo",
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(1f)
        )

        Text(
            text = buildAnnotatedString {
                append("TECHY")
                withStyle(SpanStyle(color = Color(0xFF0082FF))) {
                    append("FI")
                }
            },
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = "POWERING YOUR DIGITAL LIFESTYLE",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 6.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.45.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
