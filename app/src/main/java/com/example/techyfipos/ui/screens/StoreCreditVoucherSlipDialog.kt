package com.example.techyfipos.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.data.db.StoreCreditVoucher
import com.example.techyfipos.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Interactive Dialog for viewing, printing (ESC/POS 58mm/80mm),
 * and sharing (Image, WhatsApp, SMS Text) Store Credit Voucher Slips.
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
    val clipboardManager = LocalClipboardManager.current

    var selectedRollWidth by rememberSaveable {
        mutableIntStateOf(invoiceSettingsData.paperRollWidthMm.takeIf { it == 58 || it == 80 } ?: 80)
    }
    var isBangla by rememberSaveable { mutableStateOf(false) }
    var isZoomed by rememberSaveable { mutableStateOf(false) }
    var showShareOptionsSheet by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }

    // Generates the slip JPEG asynchronously whenever roll width or language toggles
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // --- TOP HEADER ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF6366F1).copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🎟️", fontSize = 18.sp)
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.store_credit_voucher_slip_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${voucher.code} · ${formatTaka(voucher.remainingBalance)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(voucher.code))
                                Toast.makeText(context, context.getString(R.string.store_credit_code_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // --- CONTROLS BAR (Roll Width, Language, Zoom) ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 58mm / 80mm Segmented Toggle
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            FilterChip(
                                selected = selectedRollWidth == 58,
                                onClick = { selectedRollWidth = 58 },
                                label = { Text("58mm", fontSize = 12.sp, fontWeight = if (selectedRollWidth == 58) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = selectedRollWidth == 80,
                                onClick = { selectedRollWidth = 80 },
                                label = { Text("80mm", fontSize = 12.sp, fontWeight = if (selectedRollWidth == 80) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }

                    // Language Toggle: EN / বাংলা
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            FilterChip(
                                selected = !isBangla,
                                onClick = { isBangla = false },
                                label = { Text("EN", fontSize = 12.sp, fontWeight = if (!isBangla) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = isBangla,
                                onClick = { isBangla = true },
                                label = { Text("বাংলা", fontSize = 12.sp, fontWeight = if (isBangla) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                    }

                    // Zoom / Fit Toggle
                    IconButton(
                        onClick = { isZoomed = !isZoomed },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isZoomed) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                            contentDescription = if (isZoomed) "Fit Preview" else "Zoom Preview",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // --- THERMAL PREVIEW CONTAINER ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0)) // subtle slate preview background
                        .padding(12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (slipBitmap == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Rendering thermal voucher...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Paper slip styling: white sheet with shadow
                        val verticalScrollState = rememberScrollState()
                        val horizontalScrollState = rememberScrollState()

                        Card(
                            modifier = Modifier
                                .then(
                                    if (isZoomed) {
                                        Modifier
                                            .fillMaxHeight()
                                            .horizontalScroll(horizontalScrollState)
                                            .verticalScroll(verticalScrollState)
                                    } else {
                                        Modifier
                                            .widthIn(max = if (selectedRollWidth == 58) 320.dp else 420.dp)
                                            .verticalScroll(verticalScrollState)
                                    }
                                )
                                .shadow(8.dp, RoundedCornerShape(4.dp)),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Image(
                                bitmap = slipBitmap!!,
                                contentDescription = "Store Credit Voucher Slip Preview",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .then(
                                        if (isZoomed) Modifier.wrapContentSize()
                                        else Modifier.fillMaxWidth()
                                    ),
                                contentScale = if (isZoomed) ContentScale.None else ContentScale.FillWidth
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // --- BOTTOM ACTIONS BAR ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PRINT BUTTON (Primary)
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
                                            settings = invoiceSettingsData
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("StoreCreditVoucher", "Print voucher failed", e)
                                        Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isPrinting = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Voucher slip not ready", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
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
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.store_credit_voucher_print),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // SHARE BUTTON (Launches Modal Share Options Sheet)
                    OutlinedButton(
                        onClick = { showShareOptionsSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF0D9488)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0D9488))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.store_credit_voucher_share),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // --- MODAL SHARE OPTIONS DIALOG ---
    if (showShareOptionsSheet) {
        AlertDialog(
            onDismissRequest = { showShareOptionsSheet = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0D9488).copy(alpha = 0.12f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(24.dp))
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.store_credit_voucher_share),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Share Image (FileProvider Intent Chooser)
                    Surface(
                        onClick = {
                            showShareOptionsSheet = false
                            val file = slipJpegFile
                            if (file != null && file.exists()) {
                                shareStoreCreditVoucherSlip(context, file, voucher.code, voucher.initialAmount)
                            } else {
                                Toast.makeText(context, "Voucher slip image not ready", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🖼️", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_share_image),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "JPEG voucher slip via Bluetooth, AirDrop/QuickShare, etc.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: WhatsApp
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
                            .heightIn(min = 54.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("💬", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_share_whatsapp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF075E54)
                                )
                                Text(
                                    text = "Send formatted voucher to customer's WhatsApp",
                                    fontSize = 11.5.sp,
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
                            .heightIn(min = 54.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("✉️", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_share_sms),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Send voucher code and details via SMS app",
                                    fontSize = 11.5.sp,
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
                            .heightIn(min = 54.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("📋", fontSize = 22.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.store_credit_copy_code),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${voucher.code} (${formatTaka(voucher.remainingBalance)})",
                                    fontSize = 11.5.sp,
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
