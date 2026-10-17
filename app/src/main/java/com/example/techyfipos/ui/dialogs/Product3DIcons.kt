package com.example.techyfipos.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.R
import com.example.techyfipos.utils.toEnglishDigits

@Composable
fun Animated3DPackageIcon(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_box")
    val rotY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotY
                translationY = offsetY
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        Text("📦", fontSize = fontSize)
    }
}

@Composable
fun Animated3DAddProductIcon(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_add_box")
    val rotY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    val badgeSize = (fontSize.value * 0.52f).dp
    val iconSize = (fontSize.value * 0.38f).dp
    val badgeOffsetX = (fontSize.value * 0.12f).dp
    val badgeOffsetY = (fontSize.value * 0.04f).dp

    Box(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp)
            .graphicsLayer {
                rotationY = rotY
                translationY = offsetY
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📦",
            fontSize = fontSize,
            modifier = Modifier.padding(top = 2.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = badgeOffsetX, y = badgeOffsetY)
                .size(badgeSize)
                .background(Color(0xFF16A34A), CircleShape)
                .border(1.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun Animated3DGalleryIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_gallery")
    val rotY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotY"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotY
            scaleX = scale
            scaleY = scale
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🖼️", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DCameraIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_camera")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotZ
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📸", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DFactoryIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_factory")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetY
            rotationZ = rotZ
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🏭", fontSize = 14.sp)
    }
}

@Composable
fun Animated3DCameraScannerToggleIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_camera_scanner_toggle")
    val flipAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flipAngle"
    )

    val showCamera = (flipAngle % 360f) < 180f

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = flipAngle
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        if (showCamera) {
            Text("📸", fontSize = 18.sp)
        } else {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = stringResource(R.string.cd_scan_barcode),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun Animated3DScannerIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_scanner")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationY = rotY
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = stringResource(R.string.cd_scan_barcode),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun Animated3DCalendarIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_calendar")
    val rotY by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotY
            scaleX = scale
            scaleY = scale
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📅", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DIsometricBoxIcon(modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_box")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = floatOffset
            rotationY = rotY
            cameraDistance = 14f * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📦", fontSize = fontSize)
    }
}

@Composable
fun Animated3DMetallicTagIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_tag")
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = rotZ
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🏷️", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DFolderIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_folder")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📂", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DStudioCameraIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_camera")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cameraScale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📸", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DInventoryGaugeIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_gauge")
    val scaleY by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.scaleY = scaleY
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📊", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DCoinIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_coin")
    val rotY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotY"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coinScale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotY
            scaleX = scale
            scaleY = scale
            cameraDistance = 12f * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🪙", fontSize = 18.sp)
    }
}

@Composable
fun Animated3DSaveIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_save")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotZ
        },
        contentAlignment = Alignment.Center
    ) {
        Text("✅", fontSize = 18.sp)
    }
}



@Composable
fun DateInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }

    var initYear = calendar.get(java.util.Calendar.YEAR)
    var initMonth = calendar.get(java.util.Calendar.MONTH)
    var initDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    val englishVal = remember(value) { toEnglishDigits(value.trim()) }
    if (englishVal.length >= 6) {
        val parts = englishVal.split(Regex("[-/.]"))
        if (parts.size == 3) {
            val d = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val y = parts[2].toIntOrNull()
            if (d != null && m != null && y != null) {
                initDay = d.coerceIn(1, 31)
                initMonth = (m - 1).coerceIn(0, 11)
                initYear = if (y < 100) 2000 + y else y
            }
        }
    }

    val datePickerDialog = remember(context, value) {
        android.app.DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                val yy = (year % 100).toString().padStart(2, '0')
                val mm = (monthOfYear + 1).toString().padStart(2, '0')
                val dd = dayOfMonth.toString().padStart(2, '0')
                onValueChange("$dd-$mm-$yy")
            },
            initYear,
            initMonth,
            initDay
        )
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        val english = toEnglishDigits(newValue)
                        val filtered = english.filter { it.isDigit() || it == '-' || it == '/' }.take(10)
                        onValueChange(filtered)
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(stringResource(R.string.date_placeholder_ddmmyy), color = Color.Gray, fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Animated3DCalendarIcon()
                }
            }
        }
    }
}

@Composable
private fun ProductDialogSectionLabel(icon: String, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$icon  ${label.uppercase()}",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFE2E8F0)
        )
    }
}

private fun Modifier.dashedProductImageBorder(active: Boolean): Modifier = drawBehind {
    drawRoundRect(
        color = if (active) Color(0xFF6366F1) else Color(0xFFCBD5E1),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
        )
    )
}

