package com.example.techyfipos.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.data.db.StoreCreditVoucher
import com.example.techyfipos.ui.components.Realistic3DMessageReminderIcon
import com.example.techyfipos.ui.components.Realistic3DWhatsAppIcon
import com.example.techyfipos.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Redesigned Compact, Intuitive Dialog for viewing, printing (ESC/POS 58mm/80mm),
 * and sharing (Image, WhatsApp, SMS Text) Store Credit Voucher Slips.
 *
 * Features:
 * - Realistic animated 3D icons (Voucher Ticket, POS Thermal Printer, Instant Share Portal).
 * - Accurate 58mm vs 80mm thermal receipt print preview formatting.
 * - Language switcher affects ONLY the thermal receipt print content, preserving the app/window interface language.
 * - Anti-wrapping single-line action buttons with direct 1-tap WhatsApp quick button.
 * - Cashier Quick-Scan Card mode for rapid counter redemption.
 */
@Composable
fun StoreCreditVoucherSlipDialog(
    voucher: StoreCreditVoucher,
    shopName: String = "Techyfi POS",
    shopPhone: String = "",
    shopAddress: String = "",
    invoiceSettingsData: InvoiceSettingsData = InvoiceSettingsData(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var selectedRollWidth by rememberSaveable {
        mutableIntStateOf(invoiceSettingsData.paperRollWidthMm.takeIf { it == 58 || it == 80 } ?: 80)
    }
    // Receipt-only language toggle (affects printed receipt content, NOT the window UI)
    var isBangla by rememberSaveable { mutableStateOf(false) }
    var isZoomed by rememberSaveable { mutableStateOf(false) }
    var isQuickScanMode by rememberSaveable { mutableStateOf(false) }
    var showShareOptionsSheet by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

    // Micro-animation for 3D ticket breathing
    val infiniteTransition = rememberInfiniteTransition(label = "3d_ticket_anim")
    val ticketScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ticket_scale"
    )

    // Micro-animation for 3D printer pulse
    val printerPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "printer_pulse"
    )

    // Generates the slip JPEG asynchronously whenever roll width or receipt language toggles
    val slipJpegFile by produceState<File?>(initialValue = null, voucher, selectedRollWidth, isBangla) {
        value = withContext(Dispatchers.IO) {
            try {
                generateStoreCreditVoucherJpeg(
                    context = context,
                    voucher = voucher,
                    storeName = shopName,
                    storeAddress = shopAddress,
                    storePhone = shopPhone,
                    receiptLanguage = if (isBangla) "BN" else "EN",
                    settings = invoiceSettingsData.copy(paperRollWidthMm = selectedRollWidth)
                )
            } catch (e: Exception) {
                android.util.Log.e("StoreCreditVoucher", "Failed to generate voucher slip", e)
                null
            }
        }
    }

    val slipBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, slipJpegFile) {
        val file = slipJpegFile
        value = if (file != null && file.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        } else null
    }

    // Formatted voucher text for WhatsApp and SMS sharing
    val voucherText = remember(voucher, shopName, shopPhone, isBangla) {
        buildStoreCreditVoucherText(
            voucher = voucher,
            storeName = shopName,
            storePhone = shopPhone,
            isBangla = isBangla
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(22.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // --- 1. COMPACT ERGONOMIC HEADER WITH 3D TICKET ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 3D Iridescent Voucher Ticket Icon with subtle breathing animation
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF4F46E5).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.3f)),
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    scaleX = ticketScale
                                    scaleY = ticketScale
                                }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_3d_voucher_ticket),
                                contentDescription = "3D Voucher Ticket",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column {
                            // Dialog title always adheres to the main window interface language
                            Text(
                                text = stringResource(R.string.store_credit_voucher_slip_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Voucher Code Pill (with 1-tap copy) + Balance Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF4F46E5).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.25f)),
                                    modifier = Modifier.clickable {
                                        clipboardManager.setText(AnnotatedString(voucher.code))
                                        Toast.makeText(context, context.getString(R.string.store_credit_code_copied), Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = voucher.code,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF4F46E5)
                                        )
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.cd_copy),
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = formatTaka(voucher.remainingBalance),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Close Button (Ergonomic 48dp touch target)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- 2. COMPACT SEGMENTED PILL TOOLBAR ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Roll Width Segment: 58mm / 80mm
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            FilterChip(
                                selected = selectedRollWidth == 58,
                                onClick = { selectedRollWidth = 58 },
                                label = {
                                    Text(
                                        "58mm",
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedRollWidth == 58) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            FilterChip(
                                selected = selectedRollWidth == 80,
                                onClick = { selectedRollWidth = 80 },
                                label = {
                                    Text(
                                        "80mm",
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedRollWidth == 80) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }

                    // Receipt Print Language: EN / বাংলা (Affects ONLY the receipt slip content)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            FilterChip(
                                selected = !isBangla,
                                onClick = { isBangla = false },
                                label = {
                                    Text(
                                        "EN",
                                        fontSize = 11.sp,
                                        fontWeight = if (!isBangla) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            FilterChip(
                                selected = isBangla,
                                onClick = { isBangla = true },
                                label = {
                                    Text(
                                        "বাংলা",
                                        fontSize = 11.sp,
                                        fontWeight = if (isBangla) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }

                    // View Mode Switcher: Slip vs Quick Scan
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            FilterChip(
                                selected = !isQuickScanMode,
                                onClick = { isQuickScanMode = false },
                                leadingIcon = {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(13.dp))
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.store_credit_view_mode_slip),
                                        fontSize = 11.sp,
                                        fontWeight = if (!isQuickScanMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            FilterChip(
                                selected = isQuickScanMode,
                                onClick = { isQuickScanMode = true },
                                leadingIcon = {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(13.dp))
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.store_credit_view_mode_card),
                                        fontSize = 11.sp,
                                        fontWeight = if (isQuickScanMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // --- 3. ACCURATE THERMAL RECEIPT & QUICK-SCAN PREVIEW AREA ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFCBD5E1).copy(alpha = 0.6f))
                        .padding(10.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (isQuickScanMode) {
                        // VIEW MODE B: CASHIER QUICK-SCAN CARD (High contrast, instant gun scanning)
                        Card(
                            modifier = Modifier
                                .widthIn(max = 340.dp)
                                .padding(top = 8.dp)
                                .shadow(10.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFFDE047),
                                        modifier = Modifier.size(width = 28.dp, height = 20.dp)
                                    ) {}
                                    Text(
                                        text = shopName.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE0E7FF)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "STORE CREDIT PASS",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFA5B4FC),
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = formatTaka(voucher.remainingBalance),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }

                                // Barcode Card Container
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (slipBitmap != null) {
                                            Image(
                                                bitmap = slipBitmap!!,
                                                contentDescription = stringResource(R.string.barcode),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = "||||||||||||||||||||||||||||||||||||",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp,
                                                color = Color.Black
                                            )
                                        }
                                        Text(
                                            text = voucher.code,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E1B4B),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_3d_barcode_scanner),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.store_credit_ready_scanner),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFC7D2FE)
                                    )
                                }
                            }
                        }
                    } else {
                        // VIEW MODE A: ACCURATE PRINT PREVIEW FOR 58mm & 80mm
                        if (slipBitmap == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.store_credit_rendering_thermal_voucher, selectedRollWidth),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            val verticalScrollState = rememberScrollState()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(verticalScrollState)
                            ) {
                                // Accurate format badge indicator
                                Surface(
                                    shape = RoundedCornerShape(9999.dp),
                                    color = Color(0xFF4F46E5).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.25f)),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    Text(
                                        text = if (selectedRollWidth == 58) "58mm ESC/POS (Narrow 32 Col)" else "80mm ESC/POS (Standard 42 Col)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                // Realistic Thermal Paper Card with accurate roll width formatting
                                Card(
                                    modifier = Modifier
                                        .width(if (selectedRollWidth == 58) 260.dp else 360.dp)
                                        .shadow(8.dp, RoundedCornerShape(2.dp)),
                                    shape = RoundedCornerShape(2.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(if (selectedRollWidth == 58) 6.dp else 10.dp)
                                    ) {
                                        Image(
                                            bitmap = slipBitmap!!,
                                            contentDescription = stringResource(R.string.cd_store_credit_voucher_slip_fmt, selectedRollWidth),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = ContentScale.FillWidth
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // --- 4. ERGONOMIC ZERO-WRAPPING ACTIONS BAR WITH 3D ICONS ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PRINT VOUCHER (Primary Action with 3D Printer Icon)
                    Button(
                        onClick = {
                            val file = slipJpegFile
                            if (file != null && file.exists()) {
                                isPrinting = true
                                scope.launch {
                                    try {
                                        printStoreCreditVoucherSlip(
                                            context = context,
                                            voucherFile = file,
                                            voucherCode = voucher.code,
                                            settings = invoiceSettingsData.copy(paperRollWidthMm = selectedRollWidth)
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("StoreCreditVoucher", "Print voucher failed", e)
                                        Toast.makeText(context, context.getString(R.string.store_credit_print_failed_fmt, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isPrinting = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.store_credit_slip_not_ready), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1.35f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_3d_thermal_printer),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = printerPulse
                                        scaleY = printerPulse
                                    }
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.store_credit_voucher_print_compact),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // SHARE SLIP (Secondary Action with 3D Share Hub Icon)
                    OutlinedButton(
                        onClick = { showShareOptionsSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF0D9488)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0D9488))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_3d_share_hub),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.store_credit_voucher_share_compact),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 1-TAP WHATSAPP QUICK BUTTON (Ergonomic 48dp with 3D Animated WhatsApp Icon)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF25D366).copy(alpha = 0.12f),
                        border = BorderStroke(1.2.dp, Color(0xFF25D366)),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                val phone = voucher.customerPhone ?: ""
                                shareStoreCreditVoucherWhatsApp(context, phone, voucherText)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Realistic3DWhatsAppIcon(
                                size = 28.dp,
                                animate = true
                            )
                        }
                    }
                }
            }
        }
    }

    // --- MODAL SHARE OPTIONS DIALOG WITH 3D SHARE PORTAL ---
    if (showShareOptionsSheet) {
        AlertDialog(
            onDismissRequest = { showShareOptionsSheet = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0D9488).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.3f)),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_3d_share_hub),
                            contentDescription = "3D Share Hub",
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.store_credit_voucher_share),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: WhatsApp Direct
                    Surface(
                        onClick = {
                            showShareOptionsSheet = false
                            val phone = voucher.customerPhone ?: ""
                            shareStoreCreditVoucherWhatsApp(context, phone, voucherText)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF25D366).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Realistic3DWhatsAppIcon(
                                size = 30.dp,
                                animate = true
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_share_whatsapp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF075E54)
                                )
                                Text(
                                    text = stringResource(R.string.store_credit_share_whatsapp_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Share Image (FileProvider Intent Chooser)
                    Surface(
                        onClick = {
                            showShareOptionsSheet = false
                            val file = slipJpegFile
                            if (file != null && file.exists()) {
                                shareStoreCreditVoucherSlip(context, file, voucher.code, voucher.initialAmount)
                            } else {
                                Toast.makeText(context, context.getString(R.string.store_credit_slip_image_not_ready), Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_3d_voucher_ticket),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_share_image),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = stringResource(R.string.store_credit_share_image_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 3: SMS / Messaging Text
                    Surface(
                        onClick = {
                            showShareOptionsSheet = false
                            shareStoreCreditVoucherText(context, voucher.customerPhone, voucherText)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Realistic3DMessageReminderIcon(
                                size = 26.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_share_sms),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = stringResource(R.string.store_credit_share_sms_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 4: Copy Code to Clipboard
                    Surface(
                        onClick = {
                            showShareOptionsSheet = false
                            clipboardManager.setText(AnnotatedString(voucher.code))
                            Toast.makeText(context, context.getString(R.string.store_credit_code_copied), Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_copy_code),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = "${voucher.code} (${formatTaka(voucher.remainingBalance)})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showShareOptionsSheet = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}
