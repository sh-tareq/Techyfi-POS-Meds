package com.example.techyfipos.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.techyfipos.utils.findActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.techyfipos.R
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LockMode { LOADING, SETUP, LOGIN }

@Composable
fun LockScreen(
    authViewModel: AuthViewModel,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        val activity = context.findActivity()
        activity?.moveTaskToBack(true)
    }

    var mode by remember { mutableStateOf(LockMode.LOADING) }
    var ownerName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var awaitingConfirm by remember { mutableStateOf(false) }
    var isPinVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPinRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryPin by remember { mutableStateOf("") }
    var recoveryPinConfirm by remember { mutableStateOf("") }

    val verificationCancelledText = stringResource(R.string.lock_device_verification_cancelled)
    val pinResetSuccessText = stringResource(R.string.lock_pin_reset_success)
    val pinInUseErrorText = stringResource(R.string.lock_pin_in_use_error)

    val credentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            recoveryPin = ""
            recoveryPinConfirm = ""
            showPinRecoveryDialog = true
        } else {
            errorMessage = verificationCancelledText
        }
    }

    // Shake animation for error feedback
    val shakeOffset = remember { Animatable(0f) }

    fun triggerHaptic(strong: Boolean = false) {
        try {
            haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        if (strong) 35 else 18,
                        if (strong) VibrationEffect.DEFAULT_AMPLITUDE else 120
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(if (strong) 35L else 18L)
            }
        } catch (_: Exception) {}
    }

    fun triggerErrorShake(message: String) {
        errorMessage = message
        triggerHaptic(strong = true)
        scope.launch {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    -22f at 50
                    22f at 100
                    -16f at 150
                    16f at 200
                    -10f at 250
                    10f at 300
                    -5f at 350
                    0f at 400
                }
            )
        }
    }

    fun beginSecurePinRecovery() {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard?.isDeviceSecure != true) {
            triggerErrorShake("Set a device screen lock before using secure PIN recovery.")
            return
        }
        @Suppress("DEPRECATION")
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "Verify device owner",
            "Confirm your Android screen lock to reset the Techyfi POS owner PIN."
        )
        if (intent != null) credentialLauncher.launch(intent)
        else triggerErrorShake("Device verification is unavailable on this device.")
    }

    val lockoutSeconds by authViewModel.lockoutSeconds.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authViewModel.refreshLockout()
        mode = if (authViewModel.hasAnyUsers()) LockMode.LOGIN else LockMode.SETUP
    }

    var successfulLogin by remember { mutableStateOf(false) }

    LaunchedEffect(lockoutSeconds, successfulLogin) {
        if (!successfulLogin && lockoutSeconds > 0L) {
            pin = ""
            delay(1000L)
            if (!successfulLogin) {
                authViewModel.refreshLockout()
            }
        }
    }

    fun reset() {
        pin = ""
        confirmPin = ""
        awaitingConfirm = false
    }

    fun onDigit(digit: String) {
        if (lockoutSeconds > 0L) {
            triggerHaptic(strong = true)
            return
        }
        triggerHaptic(strong = false)
        errorMessage = null
        if (mode == LockMode.SETUP && awaitingConfirm) {
            if (confirmPin.length < 4) confirmPin += digit
            if (confirmPin.length == 4) {
                if (confirmPin == pin) {
                    authViewModel.createOwnerAccount(ownerName, pin) { success ->
                        if (success) {
                            successfulLogin = true
                            triggerHaptic(strong = true)
                            onUnlocked()
                        } else {
                            triggerErrorShake("Could not create owner account. Try again.")
                            reset()
                        }
                    }
                } else {
                    triggerErrorShake("PINs didn't match. Choose a new 4-digit PIN.")
                    reset()
                }
            }
        } else {
            if (pin.length < 4) pin += digit
            if (pin.length == 4) {
                if (mode == LockMode.SETUP) {
                    awaitingConfirm = true
                } else {
                    authViewModel.attemptLogin(pin) { success, loginMessage ->
                        if (!success) {
                            triggerErrorShake(loginMessage ?: "Incorrect PIN. Try again.")
                            pin = ""
                        } else {
                            successfulLogin = true
                            triggerHaptic(strong = true)
                            onUnlocked()
                        }
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (lockoutSeconds > 0L) return
        triggerHaptic(strong = false)
        errorMessage = null
        if (mode == LockMode.SETUP && awaitingConfirm) {
            confirmPin = confirmPin.dropLast(1)
        } else {
            pin = pin.dropLast(1)
        }
    }

    fun onClear() {
        if (lockoutSeconds > 0L) return
        triggerHaptic(strong = false)
        errorMessage = null
        reset()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C1322),
                        Color(0xFF0F172A),
                        Color(0xFF070B14)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background glow spheres
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x280082FF), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.15f),
                    radius = size.width * 0.55f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x1810B981), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.8f),
                    radius = size.width * 0.45f
                )
            )
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isCompactHeight = maxHeight < 650.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = if (isCompactHeight) 8.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header / Branding
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = if (isCompactHeight) 4.dp else 8.dp)
                ) {
                    TechyfiLockBrandingHeader(isCompact = isCompactHeight)

                    Spacer(Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                    when (mode) {
                        LockMode.LOADING -> {
                            CircularProgressIndicator(
                                color = Color(0xFF0082FF),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        LockMode.SETUP -> {
                            Surface(
                                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(
                                    text = if (!awaitingConfirm) "🔐 Owner Account Setup" else "🔄 Confirm Security PIN",
                                    color = Color(0xFF93C5FD),
                                    fontSize = if (isCompactHeight) 11.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }

                            if (!awaitingConfirm) {
                                Spacer(Modifier.height(if (isCompactHeight) 8.dp else 12.dp))
                                OutlinedTextField(
                                    value = ownerName,
                                    onValueChange = { ownerName = it },
                                    label = { Text("Owner / Admin Name", color = Color(0xFF94A3B8)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF0082FF),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.6f),
                                        unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .widthIn(max = 380.dp)
                                        .fillMaxWidth(0.85f)
                                )
                            }

                            Spacer(Modifier.height(if (isCompactHeight) 10.dp else 14.dp))

                            val currentSetupPin = if (awaitingConfirm) confirmPin else pin
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Spacer(Modifier.width(48.dp))
                                Box(modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }) {
                                    Realistic3DPinDots(
                                        length = currentSetupPin.length,
                                        pinText = currentSetupPin,
                                        isPinVisible = isPinVisible,
                                        isCompact = isCompactHeight
                                    )
                                }
                                IconButton(
                                    onClick = { isPinVisible = !isPinVisible },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPinVisible) stringResource(R.string.user_hide_pin) else stringResource(R.string.user_show_pin),
                                        tint = if (isPinVisible) Color(0xFF0082FF) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (!awaitingConfirm) "Choose a 4-digit master PIN" else "Re-enter the 4-digit PIN to confirm",
                                color = Color(0xFF94A3B8),
                                fontSize = if (isCompactHeight) 11.sp else 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        LockMode.LOGIN -> {
                            Surface(
                                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = "Terminal Locked · Cash Register",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = if (isCompactHeight) 11.sp else 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(Modifier.height(if (isCompactHeight) 10.dp else 14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Spacer(Modifier.width(48.dp))
                                Box(modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }) {
                                    Realistic3DPinDots(
                                        length = pin.length,
                                        pinText = pin,
                                        isPinVisible = isPinVisible,
                                        isCompact = isCompactHeight
                                    )
                                }
                                IconButton(
                                    onClick = { isPinVisible = !isPinVisible },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPinVisible) stringResource(R.string.user_hide_pin) else stringResource(R.string.user_show_pin),
                                        tint = if (isPinVisible) Color(0xFF0082FF) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Enter your 4-digit cashier/owner PIN",
                                color = Color(0xFF94A3B8),
                                fontSize = if (isCompactHeight) 11.5.sp else 12.5.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = lockoutSeconds > 0L || errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val displayMsg = if (lockoutSeconds > 0L) {
                            "🔒 Too many failed attempts. Terminal locked for ${lockoutSeconds}s."
                        } else {
                            errorMessage?.let { "⚠️ $it" }
                        }
                        if (displayMsg != null) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = displayMsg,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    if (mode == LockMode.LOGIN) {
                        TextButton(
                            onClick = ::beginSecurePinRecovery,
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .padding(top = 2.dp)
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.lock_forgot_owner_pin),
                                color = Color(0xFF7DD3FC),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (isCompactHeight) 10.dp else 16.dp))

                // 3D Tactile Numpad
                if (mode != LockMode.LOADING) {
                    Realistic3DNumpad(
                        onDigit = ::onDigit,
                        onBackspace = ::onBackspace,
                        onClear = ::onClear,
                        isCompact = isCompactHeight,
                        enabled = lockoutSeconds <= 0L,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }

    if (showPinRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showPinRecoveryDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
            title = { Text(stringResource(R.string.lock_reset_owner_pin)) },
            text = {
                var recoveryPinVisible by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.lock_ownership_verified))
                    OutlinedTextField(
                        value = recoveryPin,
                        onValueChange = { recoveryPin = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.users_pin_label)) },
                        singleLine = true,
                        visualTransformation = if (recoveryPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { recoveryPinVisible = !recoveryPinVisible }) {
                                Icon(
                                    if (recoveryPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (recoveryPinVisible) stringResource(R.string.user_hide_pin) else stringResource(R.string.user_show_pin)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = recoveryPinConfirm,
                        onValueChange = { recoveryPinConfirm = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.users_pin_label)) },
                        singleLine = true,
                        visualTransformation = if (recoveryPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { recoveryPinVisible = !recoveryPinVisible }) {
                                Icon(
                                    if (recoveryPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (recoveryPinVisible) stringResource(R.string.user_hide_pin) else stringResource(R.string.user_show_pin)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = recoveryPin.length == 4 && recoveryPin == recoveryPinConfirm,
                    onClick = {
                        authViewModel.resetOwnerPinAfterDeviceCredential(recoveryPin) { success ->
                            showPinRecoveryDialog = false
                            if (success) {
                                pin = ""
                                errorMessage = pinResetSuccessText
                            } else {
                                triggerErrorShake(pinInUseErrorText)
                            }
                        }
                    }
                ) { Text(stringResource(R.string.lock_save_new_pin)) }
            },
            dismissButton = {
                TextButton(onClick = { showPinRecoveryDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/**
 * High-definition Techyfi branding logo with 3D glowing badge and stylized wordmark
 */
@Composable
private fun TechyfiLockBrandingHeader(isCompact: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val boxSize = if (isCompact) 52.dp else 64.dp
        val cornerRadius = if (isCompact) 16.dp else 20.dp
        Box(
            modifier = Modifier
                .size(boxSize)
                .shadow(12.dp, shape = RoundedCornerShape(cornerRadius), spotColor = Color(0xFF0082FF), ambientColor = Color(0xFF0082FF))
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF0082FF), Color(0xFF38BDF8), Color(0xFF1E293B))
                    ),
                    shape = RoundedCornerShape(cornerRadius)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Techyfi Logo",
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .graphicsLayer {
                        scaleX = 1.15f
                        scaleY = 1.15f
                    }
            )
        }

        Spacer(Modifier.height(if (isCompact) 6.dp else 8.dp))

        Text(
            text = buildAnnotatedString {
                append("TECHY")
                withStyle(SpanStyle(color = Color(0xFF0082FF))) {
                    append("FI")
                }
                append(" POS")
            },
            color = Color.White,
            fontSize = if (isCompact) 18.sp else 21.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = if (isCompact) 1.2.sp else 1.6.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "POWERING YOUR DIGITAL LIFESTYLE",
            color = Color(0xFF64748B),
            fontSize = if (isCompact) 7.5.sp else 8.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 3D Glowing PIN Dots with bouncy animations
 */
@Composable
private fun Realistic3DPinDots(
    length: Int,
    pinText: String = "",
    isPinVisible: Boolean = false,
    isCompact: Boolean = false
) {
    val outerSize = if (isCompact) (if (isPinVisible) 26.dp else 14.dp) else (if (isPinVisible) 30.dp else 16.dp)
    val innerSize = if (isCompact) 9.dp else 11.dp
    val spacing = if (isCompact) 10.dp else 12.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val filled = index < length
            val digit = if (filled && isPinVisible) pinText.getOrNull(index)?.toString() ?: "" else ""
            val scale by animateFloatAsState(
                targetValue = if (filled) 1.15f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "pinDotScale_$index"
            )

            Box(
                modifier = Modifier
                    .size(outerSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isPinVisible) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (filled) Color(0xFF1E293B) else Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (filled) Color(0xFF0082FF) else Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = digit,
                                color = if (filled) Color(0xFF38BDF8) else Color(0xFF64748B),
                                fontSize = if (isCompact) 13.sp else 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                } else {
                    if (filled) {
                        // Glow ring
                        Box(
                            modifier = Modifier
                                .size(outerSize)
                                .clip(CircleShape)
                                .background(Color(0xFF0082FF).copy(alpha = 0.35f))
                        )
                        // Inner dot
                        Box(
                            modifier = Modifier
                                .size(innerSize)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0082FF))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(innerSize)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                                .border(1.5.dp, Color(0xFF475569), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Realistic 3D Tactile Numpad with physical press-in feedback, depth bevels, and subtext letters
 */
@Composable
private fun Realistic3DNumpad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    isCompact: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val keypadData = listOf(
        listOf(Triple("1", "", KeypadKeyType.DIGIT), Triple("2", "ABC", KeypadKeyType.DIGIT), Triple("3", "DEF", KeypadKeyType.DIGIT)),
        listOf(Triple("4", "GHI", KeypadKeyType.DIGIT), Triple("5", "JKL", KeypadKeyType.DIGIT), Triple("6", "MNO", KeypadKeyType.DIGIT)),
        listOf(Triple("7", "PQRS", KeypadKeyType.DIGIT), Triple("8", "TUV", KeypadKeyType.DIGIT), Triple("9", "WXYZ", KeypadKeyType.DIGIT)),
        listOf(Triple("C", "CLEAR", KeypadKeyType.CLEAR), Triple("0", "+", KeypadKeyType.DIGIT), Triple("⌫", "DELETE", KeypadKeyType.BACKSPACE))
    )

    val rowSpacing = if (isCompact) 8.dp else 10.dp
    val colSpacing = if (isCompact) 10.dp else 12.dp

    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth(0.92f),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keypadData.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(colSpacing, Alignment.CenterHorizontally)
            ) {
                row.forEach { (mainText, subText, keyType) ->
                    Realistic3DNumpadButton(
                        mainText = mainText,
                        subText = subText,
                        keyType = keyType,
                        isCompact = isCompact,
                        enabled = enabled,
                        onClick = {
                            when (keyType) {
                                KeypadKeyType.DIGIT -> onDigit(mainText)
                                KeypadKeyType.BACKSPACE -> onBackspace()
                                KeypadKeyType.CLEAR -> onClear()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private enum class KeypadKeyType { DIGIT, BACKSPACE, CLEAR }

@Composable
private fun Realistic3DNumpadButton(
    mainText: String,
    subText: String,
    keyType: KeypadKeyType,
    isCompact: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnPress_$mainText"
    )

    val elevation = if (enabled) (5f * (1f - pressProgress)).dp else 0.dp
    val translateY = (3f * pressProgress).dp
    val btnHeight = if (isCompact) 48.dp else 54.dp
    val shapeRadius = if (isCompact) 16.dp else 18.dp

    Box(
        modifier = modifier
            .height(btnHeight)
            .graphicsLayer {
                this.translationY = translateY.toPx()
                val scale = 1f - (0.04f * pressProgress)
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = if (enabled) 1f else 0.45f
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(shapeRadius),
                spotColor = when (keyType) {
                    KeypadKeyType.DIGIT -> Color(0xFF0082FF).copy(alpha = 0.3f)
                    KeypadKeyType.CLEAR -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                    KeypadKeyType.BACKSPACE -> Color(0xFFEF4444).copy(alpha = 0.3f)
                },
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(shapeRadius))
            .background(
                Brush.verticalGradient(
                    colors = when (keyType) {
                        KeypadKeyType.DIGIT -> if (isPressed && enabled) {
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                        } else {
                            listOf(Color(0xFF27354A), Color(0xFF172033))
                        }
                        KeypadKeyType.CLEAR -> if (isPressed && enabled) {
                            listOf(Color(0xFF78350F), Color(0xFF451A03))
                        } else {
                            listOf(Color(0xFF92400E).copy(alpha = 0.85f), Color(0xFF451A03))
                        }
                        KeypadKeyType.BACKSPACE -> if (isPressed && enabled) {
                            listOf(Color(0xFF7F1D1D), Color(0xFF450A0A))
                        } else {
                            listOf(Color(0xFF991B1B).copy(alpha = 0.85f), Color(0xFF450A0A))
                        }
                    }
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = if (isPressed && enabled) {
                        listOf(Color(0xFF334155), Color(0xFF1E293B))
                    } else {
                        when (keyType) {
                            KeypadKeyType.DIGIT -> listOf(Color(0xFF475569), Color(0xFF1E293B))
                            KeypadKeyType.CLEAR -> listOf(Color(0xFFF59E0B), Color(0xFF78350F))
                            KeypadKeyType.BACKSPACE -> listOf(Color(0xFFEF4444), Color(0xFF7F1D1D))
                        }
                    }
                ),
                shape = RoundedCornerShape(shapeRadius)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Realistic Top Specular Highlight Bevel
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width

            // Top rim highlight
            drawLine(
                color = Color.White.copy(alpha = if (isPressed) 0.08f else 0.22f),
                start = Offset(14.dp.toPx(), 2.dp.toPx()),
                end = Offset(w - 14.dp.toPx(), 2.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (keyType) {
                KeypadKeyType.DIGIT -> {
                    Text(
                        text = mainText,
                        color = Color.White,
                        fontSize = if (isCompact) 20.sp else 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (subText.isNotEmpty()) {
                        Text(
                            text = subText,
                            color = Color(0xFF94A3B8),
                            fontSize = if (isCompact) 7.5.sp else 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                KeypadKeyType.BACKSPACE -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)
                    )
                    Text(
                        text = "DELETE",
                        color = Color(0xFFFCA5A5).copy(alpha = 0.8f),
                        fontSize = if (isCompact) 7.5.sp else 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                KeypadKeyType.CLEAR -> {
                    Text(
                        text = "C",
                        color = Color(0xFFFDE68A),
                        fontSize = if (isCompact) 19.sp else 21.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "CLEAR",
                        color = Color(0xFFFDE68A).copy(alpha = 0.8f),
                        fontSize = if (isCompact) 7.5.sp else 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}
