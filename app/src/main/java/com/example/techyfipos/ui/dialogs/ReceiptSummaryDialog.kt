package com.example.techyfipos.ui.dialogs

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.ui.viewmodel.InvoiceProfitSummary
import com.example.techyfipos.ui.viewmodel.SaleReturnSummary
import com.example.techyfipos.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class InvoiceShareFormat { PDF, JPEG, BOTH }

@Composable
fun ReceiptSummaryDialog(
    bill: BillData,
    title: String = stringResource(R.string.cart_sale_completed_title),
    onDismiss: () -> Unit,
    onReturn: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAddToCartAgain: (() -> Unit)? = null,
    onViewVoucherSlip: (() -> Unit)? = null,
    isOwner: Boolean = false,
    returnSummary: SaleReturnSummary? = null,
    profitSummary: InvoiceProfitSummary? = null
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var selectedDialogTab by rememberSaveable { mutableIntStateOf(0) }
    val receiptLanguageState = rememberSaveable { mutableStateOf("EN") }
    var receiptLanguage by receiptLanguageState
    val receiptText = remember(bill, receiptLanguage) { buildBillReceiptText(bill, receiptLanguage = receiptLanguage) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var selectedShareFormat by rememberSaveable { mutableStateOf(InvoiceShareFormat.JPEG) }

    var selectedRollWidth by rememberSaveable { mutableIntStateOf(bill.settingsData.paperRollWidthMm.takeIf { it == 58 || it == 80 } ?: 80) }
    var isZoomed by rememberSaveable { mutableStateOf(false) }

    val activeBill = remember(bill, selectedRollWidth) {
        bill.copy(settingsData = bill.settingsData.copy(paperRollWidthMm = selectedRollWidth))
    }

    fun withPdf(action: (java.io.File) -> Unit) {
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    generateBillPdf(context, activeBill, receiptLanguage = receiptLanguage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_failed_generate_pdf_toast), Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                action(file)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_action_failed_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun withJpeg(action: (java.io.File) -> Unit) {
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    generateBillJpeg(context, activeBill, receiptLanguage = receiptLanguage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_failed_generate_jpeg_toast), Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                action(file)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_action_failed_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val receiptBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, activeBill, receiptLanguage) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = generateBillJpeg(context, activeBill, receiptLanguage = receiptLanguage)
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val infiniteFloatTransition = rememberInfiniteTransition(label = "receiptIconFloat")
    val iconFloatY by infiniteFloatTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconFloatY"
    )

    var iconPressed by remember { mutableStateOf(false) }
    val iconScale by animateFloatAsState(
        targetValue = if (iconPressed) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconScale"
    )

    var printPressed by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    val printScale by animateFloatAsState(
        targetValue = if (printPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "printScale"
    )

    var savePressed by remember { mutableStateOf(false) }
    val saveScale by animateFloatAsState(
        targetValue = if (savePressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "saveScale"
    )

    var deletePressed by remember { mutableStateOf(false) }
    val deleteScale by animateFloatAsState(
        targetValue = if (deletePressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "deleteScale"
    )

    var sharePressed by remember { mutableStateOf(false) }
    val shareScale by animateFloatAsState(
        targetValue = if (sharePressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shareScale"
    )

    val infiniteShimmerTransition = rememberInfiniteTransition(label = "textShimmerSweep")
    val shimmerOffset by infiniteShimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF059669),
            Color(0xFF10B981),
            Color(0xFF059669),
            Color(0xFF0F172A)
        ),
        start = Offset(shimmerOffset - 400f, shimmerOffset - 400f),
        end = Offset(shimmerOffset, shimmerOffset)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Animated 3D Receipt Icon
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = iconFloatY
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        iconPressed = true
                                        delay(120)
                                        iconPressed = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF10B981),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF34D399), Color(0xFF059669))
                                            )
                                        )
                                ) {
                                    Text("🧾", fontSize = 22.sp)
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF15803D),
                                border = BorderStroke(1.5.dp, Color.White),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .size(15.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("✓", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Column {
                            Text(
                                text = title,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(brush = shimmerBrush)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Text(
                                    text = stringResource(R.string.cart_thermal_receipt_preview).replace("80mm", "$selectedRollWidth mm"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (onViewVoucherSlip != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF818CF8)),
                                modifier = Modifier.clickable { onViewVoucherSlip() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("🎟️", fontSize = 12.sp)
                                    Text(
                                        text = stringResource(R.string.store_credit_view_voucher_slip),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }
                        }

                        // Zoom inspect toggle
                        IconButton(
                            onClick = { isZoomed = !isZoomed },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text(if (isZoomed) "🔎" else "🔍", fontSize = 15.sp)
                        }

                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cart_close_button),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Segmented Tabs: 🧾 Customer Slip vs 💎 Owner Profit
                if (isOwner) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { selectedDialogTab = 0 },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedDialogTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (selectedDialogTab == 0) 2.dp else 0.dp,
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("🧾", fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.invoice_tab_receipt_slip),
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedDialogTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedDialogTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                onClick = { selectedDialogTab = 1 },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedDialogTab == 1) Color(0xFF0F172A) else Color.Transparent,
                                shadowElevation = if (selectedDialogTab == 1) 2.dp else 0.dp,
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Realistic3DProfitVaultIcon(size = 14.dp, animated = selectedDialogTab == 1)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.invoice_tab_profit_analytics),
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedDialogTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedDialogTab == 1) Color(0xFFFDE68A) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (isOwner && selectedDialogTab == 1) {
                    OwnerProfitAnalyticsContent(
                        bill = activeBill,
                        returnSummary = returnSummary,
                        profitSummary = profitSummary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // 2. Part 1: Quick Financial Metrics Ribbon (Photorealistic 3D Animated Icons)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 3D Gold Coins + Total
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Realistic3DGoldCoinsIcon(size = 28.dp)
                                Column {
                                    Text(
                                        text = stringResource(R.string.total),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatTaka(bill.grandTotal),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // 3D Cash Note + Method
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Realistic3DCashBillIcon(size = 26.dp)
                                Column {
                                    Text(
                                        text = stringResource(R.string.payment_method),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = bill.paymentMethod.ifBlank { "Cash" },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            val remainingDue = (bill.grandTotal - bill.amountPaid).coerceAtLeast(0.0)
                            if (bill.changeAmount > 0.0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(R.string.change),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatTaka(bill.changeAmount),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else if (remainingDue > 0.0) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = stringResource(R.string.due),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = formatTaka(remainingDue),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (remainingDue > 0.0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (remainingDue > 0.0) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                shadowElevation = 1.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                ) {
                                    if (remainingDue <= 0.0) {
                                        Realistic3DRepaymentSealIcon(size = 13.dp, animate = true)
                                    }
                                    Text(
                                        text = if (remainingDue > 0.0) "DUE" else "PAID",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (remainingDue > 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // 3. Expansive Receipt Viewport (.weight(1f))
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Viewport Top Control Bar (Roll switch, [ EN | বাং ] language toggle & Zoom chip)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = selectedRollWidth == 80,
                                        onClick = { selectedRollWidth = 80 },
                                        label = { Text("80mm", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF059669),
                                            selectedLabelColor = Color.White
                                        ),
                                        border = null,
                                        modifier = Modifier.height(28.dp)
                                    )
                                    FilterChip(
                                        selected = selectedRollWidth == 58,
                                        onClick = { selectedRollWidth = 58 },
                                        label = { Text("58mm", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF059669),
                                            selectedLabelColor = Color.White
                                        ),
                                        border = null,
                                        modifier = Modifier.height(28.dp)
                                    )
                                }

                                // Sliding Pill Language Toggle [ EN | বাং ]
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    shadowElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (receiptLanguage == "EN") Color(0xFF10B981) else Color.Transparent)
                                                .clickable { receiptLanguage = "EN" }
                                                .padding(horizontal = 9.dp, vertical = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "EN",
                                                color = if (receiptLanguage == "EN") Color.White else Color(0xFF475569),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(if (receiptLanguage == "BN") Color(0xFF10B981) else Color.Transparent)
                                                .clickable { receiptLanguage = "BN" }
                                                .padding(horizontal = 9.dp, vertical = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "বাং",
                                                color = if (receiptLanguage == "BN") Color.White else Color(0xFF475569),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .clickable { isZoomed = !isZoomed }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isZoomed) "🔎 Reset 1x" else "🔍 Zoom 1.2x",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Scrollable Receipt Content Box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                val currentBmp = receiptBitmap
                                if (currentBmp != null) {
                                    Image(
                                        bitmap = currentBmp,
                                        contentDescription = "$selectedRollWidth mm Thermal Receipt",
                                        modifier = Modifier
                                            .then(
                                                if (selectedRollWidth == 58) Modifier.fillMaxWidth(0.78f)
                                                else Modifier.fillMaxWidth(0.94f)
                                            )
                                            .graphicsLayer {
                                                if (isZoomed) {
                                                    scaleX = 1.25f
                                                    scaleY = 1.25f
                                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                                                }
                                            }
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Text(
                                        receiptText,
                                        color = Color.White,
                                        modifier = Modifier.padding(6.dp),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Ergonomic Bottom Action Deck
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Hero Primary Action: PRINT
                    Button(
                        onClick = {
                            if (isPrinting) return@Button
                            isPrinting = true
                            scope.launch {
                                try {
                                    printPressed = true
                                    delay(100)
                                    printPressed = false
                                    com.example.techyfipos.utils.printReceiptWithBluetoothFallback(
                                        context = context,
                                        activity = activity,
                                        bill = activeBill,
                                        settings = activeBill.settingsData,
                                        receiptLanguage = receiptLanguage,
                                        kickDrawer = false
                                    )
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    android.util.Log.e("ReceiptSummaryDialog", "Printing failed", e)
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.print_error_message, e.localizedMessage ?: ""),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isPrinting = false
                                }
                            }
                        },
                        enabled = !isPrinting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = printScale
                                scaleY = printScale
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Realistic3DPrinterIcon(size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.cart_print_button)} ($selectedRollWidth mm)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // 2. Secondary Row: Share, Save, Repeat Sale
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Share Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    sharePressed = true
                                    delay(100)
                                    sharePressed = false
                                    shareMenuExpanded = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .graphicsLayer {
                                    scaleX = shareScale
                                    scaleY = shareScale
                                },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Realistic3DShareIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cart_share_button),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Save Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    savePressed = true
                                    delay(100)
                                    savePressed = false
                                    withJpeg { file ->
                                        val saved = saveBillJpegToDownloads(context, file)
                                        if (saved) {
                                            copyImageUriToClipboard(context, getBillImageUri(context, file), "Invoice #${bill.saleId}")
                                            Toast.makeText(context, context.getString(R.string.cart_saved_jpeg_toast), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.cart_failed_save_jpeg_toast), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .graphicsLayer {
                                    scaleX = saveScale
                                    scaleY = saveScale
                                },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Realistic3DDownloadIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cart_save_button),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Repeat Sale / Add to Cart Again (if present)
                        if (onAddToCartAgain != null) {
                            OutlinedButton(
                                onClick = onAddToCartAgain,
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(40.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.cart_add_to_cart_again_button),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 3. Overflow Row (Edit, Return, Delete, Done)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onEdit != null && returnSummary?.hasReturns != true) {
                                TextButton(
                                    onClick = onEdit,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Realistic3DStylusPenIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cart_edit_sale_button),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }

                            if (onReturn != null) {
                                TextButton(
                                    onClick = onReturn,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Realistic3DReturnBoxIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cart_return_items_button),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }

                            if (onDelete != null && returnSummary?.hasReturns != true) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            deletePressed = true
                                            delay(100)
                                            deletePressed = false
                                            onDelete()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Realistic3DTrashCanIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cart_delete_button),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Realistic3DForwardArrowIcon(size = 14.dp)
                        }
                    }
                }
            }
        }
    }

    if (shareMenuExpanded) {
        AlertDialog(
            onDismissRequest = { shareMenuExpanded = false },
            title = { Text(stringResource(R.string.cart_share_invoice_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.cart_choose_file_format),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InvoiceShareFormat.entries.forEach { format ->
                        val (shareTitle, shareSubtitle) = when (format) {
                            InvoiceShareFormat.PDF -> stringResource(R.string.cart_format_pdf_title) to stringResource(R.string.cart_format_pdf_subtitle)
                            InvoiceShareFormat.JPEG -> stringResource(R.string.cart_format_jpeg_title) to stringResource(R.string.cart_format_jpeg_subtitle)
                            InvoiceShareFormat.BOTH -> stringResource(R.string.cart_format_both_title) to stringResource(R.string.cart_format_both_subtitle)
                        }
                        Surface(
                            onClick = { selectedShareFormat = format },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                if (selectedShareFormat == format) 2.dp else 1.dp,
                                if (selectedShareFormat == format) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            color = if (selectedShareFormat == format) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (format) {
                                        InvoiceShareFormat.PDF -> Icons.Default.PictureAsPdf
                                        InvoiceShareFormat.JPEG -> Icons.Default.Image
                                        InvoiceShareFormat.BOTH -> Icons.Default.FileCopy
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(shareTitle, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        shareSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = selectedShareFormat == format,
                                    onClick = { selectedShareFormat = format }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val format = selectedShareFormat
                        shareMenuExpanded = false
                        scope.launch {
                            try {
                                val files = withContext(Dispatchers.IO) {
                                    val pdf = if (format != InvoiceShareFormat.JPEG) generateBillPdf(context, activeBill, receiptLanguage = receiptLanguage) else null
                                    val jpeg = if (format != InvoiceShareFormat.PDF) generateBillJpeg(context, activeBill, receiptLanguage = receiptLanguage) else null
                                    pdf to jpeg
                                }
                                val shared = when (format) {
                                    InvoiceShareFormat.PDF -> shareBillPdf(
                                        activity ?: context,
                                        getBillPdfUri(context, requireNotNull(files.first))
                                    )
                                    InvoiceShareFormat.JPEG -> shareBillJpeg(
                                        activity ?: context,
                                        getBillImageUri(context, requireNotNull(files.second))
                                    )
                                    InvoiceShareFormat.BOTH -> shareBillPdfAndJpeg(
                                        activity ?: context,
                                        getBillPdfUri(context, requireNotNull(files.first)),
                                        getBillImageUri(context, requireNotNull(files.second))
                                    )
                                }
                                if (!shared) {
                                    Toast.makeText(context, context.getString(R.string.cart_unable_open_sharing_toast), Toast.LENGTH_SHORT).show()
                                }
                            } catch (error: Exception) {
                                error.printStackTrace()
                                Toast.makeText(context, context.getString(R.string.cart_failed_prepare_invoice_toast), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cart_continue_to_share_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { shareMenuExpanded = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
