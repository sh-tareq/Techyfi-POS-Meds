@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package com.example.techyfipos.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import com.example.techyfipos.BuildConfig
import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.printer.getBluetoothDeviceDisplayName
import com.example.techyfipos.printer.isSamePrinter
import com.example.techyfipos.ui.components.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.data.CustomInfoBlock
import com.example.techyfipos.data.BackupImportMode
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.utils.BillData
import com.example.techyfipos.utils.BillLineItem
import com.example.techyfipos.utils.generateBillJpeg
import com.example.techyfipos.utils.generateDueRepaymentJpeg
import com.example.techyfipos.utils.printBillJpeg
import com.example.techyfipos.utils.printReceiptImage
import com.example.techyfipos.utils.printLabelImages
import com.example.techyfipos.utils.ProductCodeLabelSize
import com.example.techyfipos.utils.findActivity
import kotlin.math.roundToInt
import com.example.techyfipos.utils.convertToMonochromeThresholdBitmap
import com.example.techyfipos.utils.triggerCashDrawerKick
import com.example.techyfipos.utils.shareReceiptAsText
import com.example.techyfipos.utils.restartApp
import com.example.techyfipos.R
import com.example.techyfipos.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.techyfipos.utils.GoogleDriveHelper
import com.example.techyfipos.utils.SigningCertificateHelper
import com.example.techyfipos.utils.formatTaka
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.utils.DateFormatters
import com.google.android.gms.auth.api.identity.AuthorizationResult

private enum class SettingsPage {
    HOME,
    RECEIPT,
    SHOP_IDENTITY,
    SHOP_LOGO,
    QR_CODE_LINKS,
    RECEIPT_LAYOUT,
    DUE_RECEIPT,
    TAX,
    BACKUP,
    TRANSFER,
    ABOUT
}

@Composable
private fun SettingsMasterCard(
    storeName: String,
    address: String,
    paperRollWidthMm: Int = 80,
    driveConnected: Boolean = false,
    stockCheckEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF1E1B4B),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color(0x33C7D2FE))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E1B4B),
                            Color(0xFF312E81),
                            Color(0xFF4338CA)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Realistic3DGearIcon(size = 20.dp)
                        Text(
                            text = stringResource(R.string.settings_brand_badge),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = Color(0xFFC7D2FE)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x3310B981),
                        border = BorderStroke(1.dp, Color(0x5534D399))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34D399))
                            )
                            Text(
                                text = stringResource(R.string.settings_status_offline_terminal),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = storeName.ifBlank { stringResource(R.string.settings_default_store_name) },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = address.ifBlank { stringResource(R.string.settings_default_address) },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA5B4FC)
                )

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    thickness = 0.8.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.settings_label_printer_engine), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC7D2FE))
                        Text("${paperRollWidthMm}mm ${stringResource(R.string.settings_printer_type_thermal)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                    }
                    Column {
                        Text(stringResource(R.string.settings_label_data_backup), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC7D2FE))
                        Text(
                            text = if (driveConnected) stringResource(R.string.settings_value_local_drive_ready) else stringResource(R.string.settings_value_local_only),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (driveConnected) Color(0xFF60A5FA) else Color(0xFFA5B4FC)
                        )
                    }
                    Column {
                        Text(stringResource(R.string.settings_label_display_mode), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC7D2FE))
                        Text(
                            text = if (stockCheckEnabled) stringResource(R.string.settings_value_stock_check_on) else stringResource(R.string.settings_value_stock_check_off),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (stockCheckEnabled) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

fun getConfiguredLabelDimensions(context: Context): Pair<Float, Float> {
    val prefs = context.getSharedPreferences("product_code_image_settings", Context.MODE_PRIVATE)
    val sizeName = prefs.getString("label_size", ProductCodeLabelSize.SIZE_50_X_30.name)
    val size = ProductCodeLabelSize.entries.firstOrNull { it.name == sizeName } ?: ProductCodeLabelSize.SIZE_50_X_30
    return if (size == ProductCodeLabelSize.CUSTOM) {
        val w = prefs.getFloat("custom_width_mm", 50f).coerceAtLeast(10f)
        val h = prefs.getFloat("custom_height_mm", 30f).coerceAtLeast(10f)
        Pair(w, h)
    } else {
        Pair(size.widthMm, size.heightMm)
    }
}

private fun buildTestLabelBitmap(
    storeName: String,
    widthMm: Float = 50f,
    heightMm: Float = 30f
): android.graphics.Bitmap {
    val targetWidthDots = maxOf(1, (widthMm * 8f).toInt())
    val targetHeightDots = maxOf(1, (heightMm * 8f).toInt())
    val bmp = android.graphics.Bitmap.createBitmap(targetWidthDots, targetHeightDots, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)
    val centerX = targetWidthDots / 2f
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = (targetHeightDots * 0.12f).coerceIn(14f, 32f)
        isFakeBoldText = true
    }
    canvas.drawText(storeName.ifBlank { "Techyfi POS" }, centerX, targetHeightDots * 0.25f, paint)
    paint.textSize = (targetHeightDots * 0.10f).coerceIn(12f, 26f)
    paint.isFakeBoldText = false
    canvas.drawText("TEST LABEL", centerX, targetHeightDots * 0.48f, paint)
    paint.textSize = (targetHeightDots * 0.08f).coerceIn(10f, 20f)
    val sizeText = "${if (widthMm % 1f == 0f) widthMm.toInt() else widthMm} x ${if (heightMm % 1f == 0f) heightMm.toInt() else heightMm} mm"
    canvas.drawText(sizeText, centerX, targetHeightDots * 0.68f, paint)
    canvas.drawText(
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
        centerX,
        targetHeightDots * 0.88f,
        paint
    )
    return bmp
}

private fun buildTestLabelPdf(bitmap: android.graphics.Bitmap, cacheDir: java.io.File): java.io.File {
    val pdfFile = java.io.File(cacheDir, "test_label_fallback.pdf")
    val pdfDocument = android.graphics.pdf.PdfDocument()
    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
    pdfDocument.finishPage(page)
    pdfFile.outputStream().use { pdfDocument.writeTo(it) }
    pdfDocument.close()
    return pdfFile
}

@Composable
private fun OptimizedSettingsHome(
    currentSettings: InvoiceSettingsData = InvoiceSettingsData(),
    onUpdateSettings: (InvoiceSettingsData) -> Unit = {},
    storeName: String,
    address: String,
    paperRollWidthMm: Int = 80,
    currentLanguage: String,
    currentThemeMode: String = "SYSTEM",
    currentLayoutMode: String = "AUTO",
    receiptConfigured: Boolean,
    driveConnected: Boolean,
    taxEnabled: Boolean,
    allowNegativeInventory: Boolean = false,
    onAllowNegativeInventoryChanged: (Boolean) -> Unit = {},
    cashDrawerKickEnabled: Boolean = true,
    onCashDrawerKickChanged: (Boolean) -> Unit = {},
    cashDrawerPrinterAddress: String = "",
    onCashDrawerPrinterSelected: (String) -> Unit = {},
    onLanguageSelected: (String) -> Unit,
    onThemeModeSelected: (String) -> Unit = {},
    onLayoutModeSelected: (String) -> Unit = {},
    defaultCostPercentage: Double = 100.0,
    onDefaultCostPercentageChanged: (Double) -> Unit = {},
    onExpensesClick: () -> Unit = {},
    onSuppliersClick: () -> Unit = {},
    onReceiptClick: () -> Unit,
    onTaxClick: () -> Unit,
    onManageUsersClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBackupClick: () -> Unit,
    onTransferClick: () -> Unit,
    onAboutClick: () -> Unit,
    onResetClick: () -> Unit,
    onRepairDuesClick: () -> Unit = {},
    isOwner: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPrinterPicker by remember { mutableStateOf(false) }
    var showReceiptPrinterPicker by remember { mutableStateOf(false) }
    var showLabelPrinterPicker by remember { mutableStateOf(false) }
    var printerConflictDialogMessage by remember { mutableStateOf<String?>(null) }

    // 💎 Settings Master Executive Card
    SettingsMasterCard(
        storeName = storeName,
        address = address,
        paperRollWidthMm = paperRollWidthMm,
        driveConnected = driveConnected,
        stockCheckEnabled = !allowNegativeInventory,
        modifier = Modifier.fillMaxWidth()
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Realistic3DLangGlobeIcon(size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.settings_app_language_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.settings_app_language_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = currentLanguage == "en",
                    onClick = { onLanguageSelected("en") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_language_english), fontWeight = FontWeight.Bold) }
                SegmentedButton(
                    selected = currentLanguage == "bn",
                    onClick = { onLanguageSelected("bn") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.weight(1f)
                ) { Text("বাংলা", fontWeight = FontWeight.Bold) }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Realistic3DThemeEclipseIcon(size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.settings_theme_mode_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.settings_theme_mode_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = currentThemeMode == "SYSTEM",
                    onClick = { onThemeModeSelected("SYSTEM") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_theme_system), fontWeight = FontWeight.Bold) }
                SegmentedButton(
                    selected = currentThemeMode == "LIGHT",
                    onClick = { onThemeModeSelected("LIGHT") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_theme_light), fontWeight = FontWeight.Bold) }
                SegmentedButton(
                    selected = currentThemeMode == "DARK",
                    onClick = { onThemeModeSelected("DARK") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_theme_dark), fontWeight = FontWeight.Bold) }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Realistic3DDeviceLayoutIcon(size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.settings_ui_layout_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        stringResource(R.string.settings_ui_layout_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = currentLayoutMode == "AUTO",
                    onClick = { onLayoutModeSelected("AUTO") },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    modifier = Modifier.weight(1f)
                ) { 
                    Text(
                        stringResource(R.string.settings_ui_layout_auto),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    ) 
                }
                SegmentedButton(
                    selected = currentLayoutMode == "TABLET",
                    onClick = { onLayoutModeSelected("TABLET") },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    modifier = Modifier.weight(1f)
                ) { 
                    Text(
                        stringResource(R.string.settings_ui_layout_tablet),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    ) 
                }
                SegmentedButton(
                    selected = currentLayoutMode == "PHONE",
                    onClick = { onLayoutModeSelected("PHONE") },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    modifier = Modifier.weight(1f)
                ) { 
                    Text(
                        stringResource(R.string.settings_ui_layout_phone),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    ) 
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_default_cost_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        stringResource(R.string.settings_default_cost_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val presets = listOf(50.0, 70.0, 85.0, 100.0)
                presets.forEachIndexed { index, pct ->
                    SegmentedButton(
                        selected = kotlin.math.abs(defaultCostPercentage - pct) < 0.5,
                        onClick = { onDefaultCostPercentageChanged(pct) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = presets.size),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (pct == 100.0) stringResource(R.string.settings_default_cost_preset_breakeven) else "${pct.toInt()}%",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            var customCostText by remember(defaultCostPercentage) {
                mutableStateOf(if (defaultCostPercentage % 1.0 == 0.0) defaultCostPercentage.toInt().toString() else defaultCostPercentage.toString())
            }
            val parsedCost = customCostText.toDoubleOrNull()
            val isCostInvalid = parsedCost != null && (parsedCost < 0.0 || parsedCost > 100.0)

            OutlinedTextField(
                value = customCostText,
                onValueChange = { input ->
                    val sanitized = input.filter { ch -> ch.isDigit() || ch == '.' }
                    customCostText = sanitized
                    sanitized.toDoubleOrNull()?.let { value ->
                        if (value in 0.0..100.0) {
                            onDefaultCostPercentageChanged(value)
                        }
                    }
                },
                isError = isCostInvalid,
                supportingText = if (isCostInvalid) {
                    { Text(stringResource(R.string.cost_margin_range_error), color = MaterialTheme.colorScheme.error) }
                } else null,
                label = { Text(stringResource(R.string.settings_custom_cost_margin_label)) },
                suffix = { Text("%", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.settings_default_cost_description,
                    defaultCostPercentage.toInt().toString(),
                    (100 - defaultCostPercentage.toInt()).coerceAtLeast(0).toString()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }

    SettingsSectionLabel(stringResource(R.string.settings_section_business_setup))
    SettingsGroup {
        SettingsNavigationRow3D(
            icon = { Realistic3DPrinterIcon(size = 32.dp) },
            title = stringResource(R.string.settings_row_receipt_layout_title),
            subtitle = stringResource(R.string.settings_row_receipt_layout_subtitle),
            status = if (receiptConfigured) stringResource(R.string.settings_status_configured) else stringResource(R.string.settings_status_setup),
            statusColor = if (receiptConfigured) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
            onClick = onReceiptClick
        )
    }

    SettingsSectionLabel(stringResource(R.string.settings_section_business_rules))
    SettingsGroup {
        SettingsNavigationRow3D(
            icon = { Realistic3DTaxCalculatorIcon(size = 32.dp) },
            title = stringResource(R.string.settings_row_tax_title),
            subtitle = stringResource(R.string.settings_row_tax_subtitle),
            status = if (taxEnabled) stringResource(R.string.settings_status_on) else stringResource(R.string.settings_status_off),
            statusColor = if (taxEnabled) Color(0xFF059669) else MaterialTheme.colorScheme.outline,
            onClick = onTaxClick
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsNavigationRow3D(
            icon = { Realistic3DStaffShieldLockIcon(size = 32.dp) },
            title = stringResource(R.string.settings_row_staff_pins_title),
            subtitle = stringResource(R.string.settings_row_staff_pins_subtitle),
            status = stringResource(R.string.settings_status_owner_only),
            statusColor = MaterialTheme.colorScheme.primary,
            onClick = onManageUsersClick
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Realistic3DNegativeStockWarningIcon(size = 32.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.settings_allow_negative_inventory_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        stringResource(R.string.settings_allow_negative_inventory_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = allowNegativeInventory,
                onCheckedChange = onAllowNegativeInventoryChanged
            )
        }
    }

    SettingsSectionLabel(stringResource(R.string.settings_section_printers))

    // Receipt Printer Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var isTestingReceipt by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Realistic3DPrinterIcon(size = 32.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_receipt_printer_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        stringResource(R.string.settings_receipt_printer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Print Method Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_print_method_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val currentMode = currentSettings.receiptPrintMode.ifBlank { "AUTO" }
                    SegmentedButton(
                        selected = currentMode == "AUTO",
                        onClick = { onUpdateSettings(currentSettings.copy(receiptPrintMode = "AUTO")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_print_mode_auto), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = currentMode == "BLUETOOTH",
                        onClick = { onUpdateSettings(currentSettings.copy(receiptPrintMode = "BLUETOOTH")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_print_mode_bluetooth), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = currentMode == "SYSTEM",
                        onClick = { onUpdateSettings(currentSettings.copy(receiptPrintMode = "SYSTEM")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_print_mode_system), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Bluetooth device picker (shown for Bluetooth and Auto)
            if (currentSettings.receiptPrintMode != "SYSTEM") {
                Surface(
                    onClick = { showReceiptPrinterPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Realistic3DBluetoothPrinterIcon(
                                size = 28.dp,
                                isConnected = currentSettings.directBluetoothPrinterAddress.isNotBlank()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    stringResource(R.string.settings_receipt_bluetooth_device),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (currentSettings.directBluetoothPrinterAddress.isBlank())
                                        stringResource(R.string.settings_bluetooth_not_paired)
                                    else getBluetoothDeviceDisplayName(context, currentSettings.directBluetoothPrinterAddress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Paper width chips (58 mm / 80 mm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_paper_roll_width),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentSettings.paperRollWidthMm == 80,
                        onClick = { onUpdateSettings(currentSettings.copy(paperRollWidthMm = 80)) },
                        label = { Text("80 mm", fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = currentSettings.paperRollWidthMm == 58,
                        onClick = { onUpdateSettings(currentSettings.copy(paperRollWidthMm = 58)) },
                        label = { Text("58 mm", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Receipt print darkness slider (0-15) or system note
            if (currentSettings.receiptPrintMode != "SYSTEM") {
                var localReceiptDarkness by remember(currentSettings.receiptDarkness) {
                    mutableStateOf(currentSettings.receiptDarkness.toFloat().coerceIn(0f, 15f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.settings_receipt_darkness_label, localReceiptDarkness.roundToInt().coerceIn(0, 15)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = localReceiptDarkness,
                        onValueChange = { v -> localReceiptDarkness = v },
                        onValueChangeFinished = {
                            onUpdateSettings(currentSettings.copy(receiptDarkness = localReceiptDarkness.roundToInt().coerceIn(0, 15)))
                        },
                        valueRange = 0f..15f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_darkness_lighter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.settings_darkness_darker),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_receipt_darkness_system_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Test receipt button
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isTestingReceipt = true
                        val ok = try {
                            val testFile = generateBillJpeg(context, buildSettingsPreviewBill(currentSettings))
                            printReceiptImage(
                                context = context,
                                activity = context.findActivity(),
                                imageFile = testFile,
                                jobName = "Test_Receipt",
                                settings = currentSettings,
                                kickDrawer = false
                            )
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            android.util.Log.e("SettingsScreen", "Test receipt failed", e)
                            false
                        }
                        withContext(Dispatchers.Main) {
                            isTestingReceipt = false
                            if (ok) {
                                Toast.makeText(context, context.getString(R.string.settings_test_receipt_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.settings_test_receipt_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isTestingReceipt,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isTestingReceipt) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_test_receipt_button), fontWeight = FontWeight.Bold)
            }
        }
    }

    // Label Printer Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var isTestingLabel by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Realistic3DPrinterIcon(size = 32.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.settings_label_printer_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        stringResource(R.string.settings_label_printer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Print Method Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_print_method_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val currentMode = currentSettings.labelPrintMode.ifBlank { "AUTO" }
                    SegmentedButton(
                        selected = currentMode == "AUTO",
                        onClick = { onUpdateSettings(currentSettings.copy(labelPrintMode = "AUTO")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_print_mode_auto), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = currentMode == "BLUETOOTH",
                        onClick = { onUpdateSettings(currentSettings.copy(labelPrintMode = "BLUETOOTH")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_print_mode_bluetooth), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SegmentedButton(
                        selected = currentMode == "SYSTEM",
                        onClick = { onUpdateSettings(currentSettings.copy(labelPrintMode = "SYSTEM")) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_print_mode_system), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Bluetooth device picker (shown for Bluetooth and Auto)
            if (currentSettings.labelPrintMode != "SYSTEM") {
                Surface(
                    onClick = { showLabelPrinterPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Realistic3DBluetoothPrinterIcon(
                                size = 28.dp,
                                isConnected = currentSettings.labelPrinterAddress.isNotBlank()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    stringResource(R.string.settings_label_bluetooth_device),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (currentSettings.labelPrinterAddress.isBlank())
                                        stringResource(R.string.settings_bluetooth_not_paired)
                                    else getBluetoothDeviceDisplayName(context, currentSettings.labelPrinterAddress),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (isSamePrinter(currentSettings.labelPrinterAddress, currentSettings.resolvedReceiptPrinterAddress())) {
                    Text(
                        text = stringResource(R.string.settings_label_printer_same_as_receipt_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Label gap field (mm)
            var gapText by remember { mutableStateOf(currentSettings.labelGapMm.toString()) }
            LaunchedEffect(currentSettings.labelGapMm) {
                // Only resync when the stored value differs from what the user has typed,
                // so typing "3." does not get rewritten to "3.0" mid-edit.
                if (gapText.toFloatOrNull() != currentSettings.labelGapMm) {
                    gapText = currentSettings.labelGapMm.toString()
                }
            }
            OutlinedTextField(
                value = gapText,
                onValueChange = { input ->
                    gapText = input
                    input.toFloatOrNull()?.let { v ->
                        if (v in 0f..20f) {
                            onUpdateSettings(currentSettings.copy(labelGapMm = v))
                        }
                    }
                },
                label = { Text(stringResource(R.string.settings_label_gap_label)) },
                suffix = { Text("mm", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Darkness slider (0-15)
            var localLabelDarkness by remember(currentSettings.labelDarkness) {
                mutableStateOf(currentSettings.labelDarkness.toFloat().coerceIn(0f, 15f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_label_darkness_label, localLabelDarkness.roundToInt().coerceIn(0, 15)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = localLabelDarkness,
                    onValueChange = { v -> localLabelDarkness = v },
                    onValueChangeFinished = {
                        onUpdateSettings(currentSettings.copy(labelDarkness = localLabelDarkness.roundToInt().coerceIn(0, 15)))
                    },
                    valueRange = 0f..15f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_darkness_lighter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.settings_darkness_darker),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Test label button
            OutlinedButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isTestingLabel = true
                        val ok = try {
                            val (labelW, labelH) = getConfiguredLabelDimensions(context)
                            val testBitmap = buildTestLabelBitmap(currentSettings.storeName, labelW, labelH)
                            val dummyPdf = buildTestLabelPdf(testBitmap, context.cacheDir)
                            printLabelImages(
                                context = context,
                                activity = context.findActivity(),
                                labels = listOf(testBitmap),
                                pdfFallback = dummyPdf,
                                widthMm = labelW,
                                heightMm = labelH,
                                copies = 1,
                                jobName = "Test_Label",
                                settings = currentSettings
                            )
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            android.util.Log.e("SettingsScreen", "Test label failed", e)
                            false
                        }
                        withContext(Dispatchers.Main) {
                            isTestingLabel = false
                            if (ok) {
                                Toast.makeText(context, context.getString(R.string.settings_test_label_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.settings_test_label_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isTestingLabel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isTestingLabel) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_test_label_button), fontWeight = FontWeight.Bold)
            }
        }
    }

    SettingsSectionLabel(stringResource(R.string.settings_section_hardware_cash_drawer))
    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Realistic3DCashDrawerKickIcon(size = 32.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.settings_cash_drawer_kick_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        stringResource(R.string.settings_cash_drawer_kick_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = cashDrawerKickEnabled,
                onCheckedChange = onCashDrawerKickChanged
            )
        }
        if (cashDrawerKickEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clickable { showPrinterPicker = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val hasDrawerPrinter = cashDrawerPrinterAddress.isNotBlank() || currentSettings.resolvedReceiptPrinterAddress().isNotBlank()
                    Realistic3DBluetoothPrinterIcon(
                        size = 32.dp,
                        isConnected = hasDrawerPrinter
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.settings_bluetooth_printer_label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        val displayAddress = if (cashDrawerPrinterAddress.isBlank()) {
                            val resolved = currentSettings.resolvedReceiptPrinterAddress()
                            if (resolved.isNotBlank()) stringResource(R.string.settings_cash_drawer_same_as_receipt) + " (${getBluetoothDeviceDisplayName(context, resolved)})"
                            else stringResource(R.string.settings_cash_drawer_same_as_receipt)
                        } else {
                            getBluetoothDeviceDisplayName(context, cashDrawerPrinterAddress)
                        }
                        Text(
                            displayAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.settings_cash_drawer_command_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val addr = cashDrawerPrinterAddress.ifBlank { currentSettings.resolvedReceiptPrinterAddress() }
                            triggerCashDrawerKick(context, addr)
                        }
                    },
                    enabled = cashDrawerPrinterAddress.isNotBlank() || currentSettings.resolvedReceiptPrinterAddress().isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Realistic3DVoltPulseKickIcon(size = 18.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_test_kick_button), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPrinterPicker) {
        BluetoothPrinterPickerDialog(
            currentAddress = cashDrawerPrinterAddress,
            showSameAsReceiptOption = true,
            receiptPrinterAddress = currentSettings.resolvedReceiptPrinterAddress(),
            onSelected = { address ->
                onCashDrawerPrinterSelected(address)
                showPrinterPicker = false
            },
            onDismiss = { showPrinterPicker = false }
        )
    }

    if (showReceiptPrinterPicker) {
        BluetoothPrinterPickerDialog(
            currentAddress = currentSettings.directBluetoothPrinterAddress,
            disabledPrinterAddress = currentSettings.labelPrinterAddress,
            onSelected = { address ->
                if (isSamePrinter(address, currentSettings.labelPrinterAddress)) {
                    printerConflictDialogMessage = context.getString(R.string.settings_receipt_picker_same_as_label_dialog)
                } else {
                    onUpdateSettings(currentSettings.copy(directBluetoothPrinterAddress = address))
                }
                showReceiptPrinterPicker = false
            },
            onDismiss = { showReceiptPrinterPicker = false }
        )
    }

    if (showLabelPrinterPicker) {
        BluetoothPrinterPickerDialog(
            currentAddress = currentSettings.labelPrinterAddress,
            disabledPrinterAddress = currentSettings.resolvedReceiptPrinterAddress(),
            onSelected = { address ->
                if (isSamePrinter(address, currentSettings.resolvedReceiptPrinterAddress())) {
                    printerConflictDialogMessage = context.getString(R.string.settings_label_picker_same_as_receipt_dialog)
                } else {
                    onUpdateSettings(currentSettings.copy(labelPrinterAddress = address))
                }
                showLabelPrinterPicker = false
            },
            onDismiss = { showLabelPrinterPicker = false }
        )
    }

    printerConflictDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { printerConflictDialogMessage = null },
            title = {
                Text(
                    text = stringResource(R.string.settings_printer_conflict_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { printerConflictDialogMessage = null }) {
                    Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLogoutClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF43F5E).copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Realistic3DLogoutVaultIcon(size = 32.dp)
                Column {
                    Text(
                        text = stringResource(R.string.settings_log_out_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFF43F5E)
                    )
                    Text(
                        text = stringResource(R.string.settings_lock_register_session_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = Color(0xFFF43F5E),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    SettingsSectionLabel(stringResource(R.string.settings_section_data_management_cloud))
    SettingsGroup {
        SettingsNavigationRow3D(
            icon = { Realistic3DCloudBackupIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_backup_restore_title),
            subtitle = stringResource(R.string.settings_row_backup_restore_subtitle),
            status = if (driveConnected) stringResource(R.string.settings_status_drive_ready) else stringResource(R.string.settings_status_local_ready),
            statusColor = Color(0xFF2563EB),
            onClick = onBackupClick
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsNavigationRow3D(
            icon = { Realistic3DSpreadsheetTransferIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_import_export_title),
            subtitle = stringResource(R.string.settings_row_import_export_subtitle),
            status = stringResource(R.string.settings_status_excel_csv),
            statusColor = Color(0xFF059669),
            onClick = onTransferClick
        )
    }

    SettingsSectionLabel(stringResource(R.string.settings_section_about))
    SettingsGroup {
        SettingsNavigationRow3D(
            icon = { Realistic3DInfoBadgeIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_about_title),
            subtitle = stringResource(R.string.settings_row_about_subtitle),
            status = stringResource(R.string.settings_status_version_pro),
            statusColor = Color(0xFF7C3AED),
            onClick = onAboutClick
        )
    }

    SettingsSectionLabel(stringResource(R.string.settings_section_danger_zone), color = MaterialTheme.colorScheme.error)
    if (isOwner) {
        Card(
            onClick = onRepairDuesClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Realistic3DResetWarningIcon(size = 36.dp)
                    Column {
                        Text(
                            stringResource(R.string.settings_repair_dues_title),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                        Text(
                            stringResource(R.string.settings_repair_dues_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    Card(
        onClick = onResetClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Realistic3DResetWarningIcon(size = 36.dp)
                Column {
                    Text(
                        stringResource(R.string.settings_factory_reset_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Text(
                        stringResource(R.string.settings_factory_reset_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** Lists the phone's already-paired (bonded) Bluetooth devices so the owner can pick the receipt printer the cash-drawer ESC/POS pulse is sent to. Pairing itself is done from Android's own Bluetooth settings, not here. */
@Composable
private fun BluetoothPrinterPickerDialog(
    currentAddress: String,
    showSameAsReceiptOption: Boolean = false,
    receiptPrinterAddress: String = "",
    disabledPrinterAddress: String = "",
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<android.bluetooth.BluetoothDevice>>(emptyList()) }
    var permissionDenied by remember { mutableStateOf(false) }
    var bluetoothOff by remember { mutableStateOf(false) }

    fun loadDevices() {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionDenied = true
            return
        }
        permissionDenied = false
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                bluetoothOff = true
                devices = emptyList()
                return
            }
            bluetoothOff = false
            devices = adapter.bondedDevices?.toList().orEmpty()
        } catch (e: SecurityException) {
            permissionDenied = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) loadDevices() else permissionDenied = true }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            loadDevices()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.settings_dialog_select_bt_printer_title)) },
        text = {
            Column {
                if (permissionDenied) {
                    Text(
                        stringResource(R.string.settings_bt_permission_required),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                } else if (bluetoothOff) {
                    Text(
                        stringResource(R.string.settings_bt_bluetooth_off),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (devices.isEmpty() && !showSameAsReceiptOption && currentAddress.isBlank()) {
                    Text(
                        stringResource(R.string.settings_bt_no_devices_found),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showSameAsReceiptOption) {
                            Surface(
                                onClick = { onSelected("") },
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentAddress.isBlank()) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.settings_cash_drawer_same_as_receipt),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            if (receiptPrinterAddress.isNotBlank()) getBluetoothDeviceDisplayName(context, receiptPrinterAddress) else stringResource(R.string.settings_bluetooth_not_paired),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (currentAddress.isBlank()) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        } else {
                            val isNoneSelected = currentAddress.isBlank()
                            Surface(
                                onClick = { onSelected("") },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNoneSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.settings_printer_none_not_set),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            stringResource(R.string.settings_bluetooth_not_paired),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (isNoneSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                        devices.forEach { device ->
                            val isConflict = disabledPrinterAddress.isNotBlank() && isSamePrinter(device.address, disabledPrinterAddress)
                            val isSelected = device.address.equals(currentAddress.trim(), ignoreCase = true)
                            val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                            Surface(
                                onClick = {
                                    if (!isConflict) {
                                        onSelected(device.address)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isConflict,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isConflict) Color.Gray else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(device.address, fontSize = 11.sp, color = Color.Gray)
                                        if (isConflict) {
                                            Text(
                                                stringResource(R.string.settings_label_printer_same_as_receipt_warning),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_button_close)) }
        }
    )
}

@Composable
private fun TaxSettings(
    currentSettings: InvoiceSettingsData,
    onSettingsChanged: (InvoiceSettingsData) -> Unit,
    onSave: () -> Unit
) {
    var rateText by remember(currentSettings.taxRate) {
        mutableStateOf(if (currentSettings.taxRate % 1.0 == 0.0) currentSettings.taxRate.toInt().toString() else currentSettings.taxRate.toString())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_tax_charge_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        stringResource(R.string.settings_tax_charge_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = currentSettings.taxEnabled,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(taxEnabled = it)) }
                )
            }

            if (currentSettings.taxEnabled) {
                OutlinedTextField(
                    value = currentSettings.taxLabel,
                    onValueChange = { onSettingsChanged(currentSettings.copy(taxLabel = it)) },
                    label = { Text(stringResource(R.string.settings_tax_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val parsedTaxRate = rateText.toDoubleOrNull()
                val isTaxRateInvalid = parsedTaxRate != null && (parsedTaxRate < 0.0 || parsedTaxRate > 100.0)
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { input ->
                        val sanitized = input.filter { ch -> ch.isDigit() || ch == '.' }
                        rateText = sanitized
                        sanitized.toDoubleOrNull()?.let { rate ->
                            if (rate in 0.0..100.0) {
                                onSettingsChanged(currentSettings.copy(taxRate = rate))
                            }
                        }
                    },
                    isError = isTaxRateInvalid,
                    supportingText = if (isTaxRateInvalid) {
                        { Text(stringResource(R.string.tax_rate_range_error), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    label = { Text(stringResource(R.string.settings_tax_rate_field)) },
                    suffix = { Text("%", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.settings_tax_calc_mode), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !currentSettings.taxInclusive,
                        onClick = { onSettingsChanged(currentSettings.copy(taxInclusive = false)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_tax_mode_exclusive), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = currentSettings.taxInclusive,
                        onClick = { onSettingsChanged(currentSettings.copy(taxInclusive = true)) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_tax_mode_inclusive), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    text = if (currentSettings.taxInclusive)
                        stringResource(R.string.settings_tax_mode_inclusive_desc)
                    else
                        stringResource(R.string.settings_tax_mode_exclusive_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_save_tax_button))
            }
        }
    }
}

@Composable
private fun OptimizedReceiptOverview(
    settings: InvoiceSettingsData,
    onOpenShopIdentity: () -> Unit,
    onOpenShopLogo: () -> Unit,
    onOpenQrCodeLinks: () -> Unit,
    onOpenReceiptLayout: () -> Unit,
    onOpenDueReceipt: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Realistic3DPrinterIcon(size = 38.dp)
            Column {
                Text(stringResource(R.string.settings_receipt_customizer_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    stringResource(R.string.settings_receipt_customizer_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    SettingsGroup {
        SettingsNavigationRow3D(
            icon = { Realistic3DStoreIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_shop_identity_title),
            subtitle = stringResource(R.string.settings_row_shop_identity_subtitle),
            status = if (settings.storeName.isNotBlank()) stringResource(R.string.settings_status_configured) else stringResource(R.string.settings_status_empty),
            statusColor = if (settings.storeName.isNotBlank()) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
            onClick = onOpenShopIdentity
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsNavigationRow3D(
            icon = { Realistic3DPrinterIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_shop_logo_title),
            subtitle = stringResource(R.string.settings_row_shop_logo_subtitle),
            status = if (settings.showLogo) stringResource(R.string.settings_status_active) else stringResource(R.string.settings_status_off),
            statusColor = if (settings.showLogo) Color(0xFF2563EB) else MaterialTheme.colorScheme.outline,
            onClick = onOpenShopLogo
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsNavigationRow3D(
            icon = { Realistic3DQrCodeIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_qr_links_title),
            subtitle = stringResource(R.string.settings_row_qr_links_subtitle),
            status = if (settings.showQr) stringResource(R.string.settings_status_enabled) else stringResource(R.string.settings_status_off),
            statusColor = if (settings.showQr) Color(0xFFD97706) else MaterialTheme.colorScheme.outline,
            onClick = onOpenQrCodeLinks
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsNavigationRow3D(
            icon = { Realistic3DReceiptPaperIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_receipt_layout_notes_title),
            subtitle = stringResource(R.string.settings_row_receipt_layout_notes_subtitle),
            status = stringResource(R.string.settings_status_80_58mm),
            statusColor = Color(0xFF7C3AED),
            onClick = onOpenReceiptLayout
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        SettingsNavigationRow3D(
            icon = { Realistic3DRepaymentSealIcon(size = 36.dp) },
            title = stringResource(R.string.settings_row_due_receipt_title),
            subtitle = stringResource(R.string.settings_row_due_receipt_subtitle),
            status = stringResource(R.string.settings_status_configured),
            statusColor = Color(0xFF10B981),
            onClick = onOpenDueReceipt
        )
    }
}

@Composable
private fun ShopIdentitySettings(
    currentSettings: InvoiceSettingsData,
    onSettingsChanged: (InvoiceSettingsData) -> Unit,
    onSave: () -> Unit,
    onTestPrint: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val defaultAdmin = stringResource(R.string.more_admin_badge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Realistic3DStoreIcon(size = 30.dp)
                Text(stringResource(R.string.settings_row_shop_identity_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = currentSettings.storeName,
                onValueChange = { onSettingsChanged(currentSettings.copy(storeName = it)) },
                label = { Text(stringResource(R.string.settings_field_store_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = currentSettings.address,
                onValueChange = { onSettingsChanged(currentSettings.copy(address = it)) },
                label = { Text(stringResource(R.string.settings_field_store_address)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentSettings.phonePrimary,
                    onValueChange = { onSettingsChanged(currentSettings.copy(phonePrimary = it)) },
                    label = { Text(stringResource(R.string.settings_field_primary_phone)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = currentSettings.phoneSecondary,
                    onValueChange = { onSettingsChanged(currentSettings.copy(phoneSecondary = it)) },
                    label = { Text(stringResource(R.string.settings_field_secondary_phone)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = currentSettings.cashierName,
                onValueChange = { onSettingsChanged(currentSettings.copy(cashierName = it)) },
                label = { Text(stringResource(R.string.settings_field_default_cashier_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.settings_cashier_presets_label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            var newCashierPresetInput by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newCashierPresetInput,
                    onValueChange = { newCashierPresetInput = it },
                    label = { Text(stringResource(R.string.settings_field_add_cashier_name)) },
                    placeholder = { Text(stringResource(R.string.settings_placeholder_cashier_example)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val trimmed = newCashierPresetInput.trim()
                        if (trimmed.isNotBlank() && !currentSettings.cashierPresets.contains(trimmed)) {
                            val updatedList = currentSettings.cashierPresets + trimmed
                            onSettingsChanged(
                                currentSettings.copy(
                                    cashierPresets = updatedList,
                                    cashierName = if (currentSettings.cashierName.isBlank()) trimmed else currentSettings.cashierName
                                )
                            )
                            newCashierPresetInput = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.settings_button_add))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                currentSettings.cashierPresets.forEach { preset ->
                    InputChip(
                        selected = (currentSettings.cashierName == preset),
                        onClick = {
                            onSettingsChanged(currentSettings.copy(cashierName = preset))
                        },
                        label = { Text(preset, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        trailingIcon = {
                            if (currentSettings.cashierPresets.size > 1) {
                                IconButton(
                                    onClick = {
                                        val updatedList = currentSettings.cashierPresets.filter { it != preset }
                                        val newDefault = if (currentSettings.cashierName == preset) (updatedList.firstOrNull() ?: defaultAdmin) else currentSettings.cashierName
                                        onSettingsChanged(currentSettings.copy(cashierPresets = updatedList, cashierName = newDefault))
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove), modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_save_changes))
        }
        OutlinedButton(onClick = onTestPrint, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_test_print))
        }
    }
}

@Composable
private fun ShopLogoSettings(
    currentSettings: InvoiceSettingsData,
    onSettingsChanged: (InvoiceSettingsData) -> Unit,
    onPickLogo: () -> Unit,
    onSave: () -> Unit,
    onTestPrint: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Realistic3DPrinterIcon(size = 30.dp)
                    Text(stringResource(R.string.settings_custom_logo_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_show_logo_label), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = currentSettings.showLogo,
                        onCheckedChange = { onSettingsChanged(currentSettings.copy(showLogo = it)) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var logoImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                var monochromeLogoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

                LaunchedEffect(currentSettings.logoUri, File(context.filesDir, "store_logo.png").lastModified()) {
                    if (currentSettings.logoUri.isNotBlank()) {
                        val (orig, mono) = withContext(Dispatchers.IO) {
                            try {
                                val uri = Uri.parse(currentSettings.logoUri)
                                var oBmp: ImageBitmap? = null
                                var mBmp: ImageBitmap? = null
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val decoded = BitmapFactory.decodeStream(stream)
                                    if (decoded != null) {
                                        oBmp = decoded.asImageBitmap()
                                        mBmp = convertToMonochromeThresholdBitmap(decoded, 180).asImageBitmap()
                                    }
                                }
                                Pair(oBmp, mBmp)
                            } catch (e: Exception) {
                                Pair(null, null)
                            }
                        }
                        logoImageBitmap = orig
                        monochromeLogoBitmap = mono
                    } else {
                        logoImageBitmap = null
                        monochromeLogoBitmap = null
                    }
                }

                val currentLogo = logoImageBitmap
                val currentMono = monochromeLogoBitmap

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.settings_color_original_label), fontSize = 10.sp, color = Color.Gray)
                    Surface(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        if (currentLogo != null) {
                            Image(bitmap = currentLogo, contentDescription = stringResource(R.string.cd_logo_original), modifier = Modifier.fillMaxSize())
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.settings_thermal_1bit_label), fontSize = 10.sp, color = Color.Gray)
                    Surface(
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                        color = Color.White
                    ) {
                        if (currentMono != null) {
                            Image(bitmap = currentMono, contentDescription = stringResource(R.string.cd_thermal_logo), modifier = Modifier.fillMaxSize())
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.settings_none_label), fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onPickLogo) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (currentSettings.logoUri.isBlank()) stringResource(R.string.settings_button_upload_logo) else stringResource(R.string.settings_button_replace))
                    }
                    if (currentSettings.logoUri.isNotBlank()) {
                        TextButton(
                            onClick = { onSettingsChanged(currentSettings.copy(logoUri = "")) }
                        ) {
                            Text(stringResource(R.string.settings_button_remove), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_save_changes))
        }
        OutlinedButton(onClick = onTestPrint, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_test_print))
        }
    }
}

@Composable
private fun QrCodeLinksSettings(
    currentSettings: InvoiceSettingsData,
    onSettingsChanged: (InvoiceSettingsData) -> Unit,
    onSave: () -> Unit,
    onTestPrint: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Realistic3DQrCodeIcon(size = 30.dp)
                Text(stringResource(R.string.settings_facebook_qr_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = currentSettings.facebookUrl,
                onValueChange = { onSettingsChanged(currentSettings.copy(facebookUrl = it)) },
                label = { Text(stringResource(R.string.settings_field_facebook_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = currentSettings.websiteUrl,
                onValueChange = { onSettingsChanged(currentSettings.copy(websiteUrl = it)) },
                label = { Text(stringResource(R.string.settings_field_website_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_show_fb_header), fontSize = 13.sp)
                Switch(
                    checked = currentSettings.showFacebookInHeader,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showFacebookInHeader = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_show_fb_below_qr), fontSize = 13.sp)
                Switch(
                    checked = currentSettings.showFacebookBelowQr,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showFacebookBelowQr = it)) }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_enable_bottom_qr), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Switch(
                    checked = currentSettings.showQr,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showQr = it)) }
                )
            }

            if (currentSettings.showQr) {
                Text(stringResource(R.string.settings_qr_destination_label), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        "digital_invoice" to stringResource(R.string.settings_qr_mode_digital_invoice),
                        "store_info" to stringResource(R.string.settings_qr_mode_store_info),
                        "facebook_page" to stringResource(R.string.settings_qr_mode_facebook_page),
                        "custom_url" to stringResource(R.string.settings_qr_mode_custom_url),
                        "embedded_json" to stringResource(R.string.settings_qr_mode_embedded_json)
                    )
                    modes.forEach { (key, label) ->
                        FilterChip(
                            selected = currentSettings.qrMode == key,
                            onClick = { onSettingsChanged(currentSettings.copy(qrMode = key)) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) }
                        )
                    }
                }

                if (currentSettings.qrMode == "custom_url") {
                    OutlinedTextField(
                        value = currentSettings.qrCustomUrl,
                        onValueChange = { onSettingsChanged(currentSettings.copy(qrCustomUrl = it)) },
                        label = { Text(stringResource(R.string.settings_field_custom_qr_url)) },
                        placeholder = { Text("https://yourdomain.com/invoice") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(stringResource(R.string.settings_printed_qr_size_label), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sizeOptions = listOf(
                        "small" to stringResource(R.string.settings_qr_size_small),
                        "medium" to stringResource(R.string.settings_qr_size_medium),
                        "large" to stringResource(R.string.settings_qr_size_large)
                    )
                    sizeOptions.forEach { (key, label) ->
                        FilterChip(
                            selected = currentSettings.qrSize == key,
                            onClick = { onSettingsChanged(currentSettings.copy(qrSize = key)) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = currentSettings.qrCaption,
                    onValueChange = { onSettingsChanged(currentSettings.copy(qrCaption = it)) },
                    label = { Text(stringResource(R.string.settings_field_qr_caption)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_save_changes))
        }
        OutlinedButton(onClick = onTestPrint, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_test_print))
        }
    }
}

@Composable
private fun ReceiptLayoutSettings(
    currentSettings: InvoiceSettingsData,
    onSettingsChanged: (InvoiceSettingsData) -> Unit,
    onAddInfoBlock: () -> Unit,
    onEditInfoBlock: (CustomInfoBlock) -> Unit,
    onDeleteInfoBlock: (String) -> Unit,
    onSave: () -> Unit,
    onTestPrint: () -> Unit,
    onResetForm: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Realistic3DReceiptPaperIcon(size = 30.dp)
                Text(stringResource(R.string.settings_receipt_fields_layout_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Thermal Paper Roll Width Selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_paper_roll_width_title),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_paper_roll_width_desc),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = currentSettings.paperRollWidthMm == 80,
                            onClick = { onSettingsChanged(currentSettings.copy(paperRollWidthMm = 80)) },
                            label = { Text("80mm", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
                        )
                        FilterChip(
                            selected = currentSettings.paperRollWidthMm == 58,
                            onClick = { onSettingsChanged(currentSettings.copy(paperRollWidthMm = 58)) },
                            label = { Text("58mm", fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_store_address), fontSize = 13.sp)
                Switch(checked = currentSettings.showAddress, onCheckedChange = { onSettingsChanged(currentSettings.copy(showAddress = it)) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_phone_numbers), fontSize = 13.sp)
                Switch(checked = currentSettings.showPhone, onCheckedChange = { onSettingsChanged(currentSettings.copy(showPhone = it)) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_cashier_name), fontSize = 13.sp)
                Switch(checked = currentSettings.showCashier, onCheckedChange = { onSettingsChanged(currentSettings.copy(showCashier = it)) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_customer_details), fontSize = 13.sp)
                Switch(checked = currentSettings.showCustomerDetails, onCheckedChange = { onSettingsChanged(currentSettings.copy(showCustomerDetails = it)) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_discount_line), fontSize = 13.sp)
                Switch(checked = currentSettings.showDiscount, onCheckedChange = { onSettingsChanged(currentSettings.copy(showDiscount = it)) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_mrp_discount_sale_mode), fontSize = 13.sp)
                Switch(checked = currentSettings.showMrpDiscountInSaleMode, onCheckedChange = { onSettingsChanged(currentSettings.copy(showMrpDiscountInSaleMode = it)) })
            }

            if (currentSettings.showMrpDiscountInSaleMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.settings_show_item_wise_mrp_discount), fontSize = 12.5.sp)
                        Text(stringResource(R.string.settings_item_by_item_discount_desc), fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = currentSettings.showItemWiseMrpDiscount, onCheckedChange = { onSettingsChanged(currentSettings.copy(showItemWiseMrpDiscount = it)) })
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_payment_status), fontSize = 13.sp)
                Switch(checked = currentSettings.showPaymentStatus, onCheckedChange = { onSettingsChanged(currentSettings.copy(showPaymentStatus = it)) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_secondary_footer), fontSize = 13.sp)
                Switch(checked = currentSettings.showSecondaryFooter, onCheckedChange = { onSettingsChanged(currentSettings.copy(showSecondaryFooter = it)) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_show_powered_by_banner), fontSize = 13.sp)
                Switch(checked = currentSettings.showPoweredBy, onCheckedChange = { onSettingsChanged(currentSettings.copy(showPoweredBy = it)) })
            }

            OutlinedTextField(
                value = currentSettings.thankYouMessage,
                onValueChange = { onSettingsChanged(currentSettings.copy(thankYouMessage = it)) },
                label = { Text(stringResource(R.string.settings_field_thank_you_message)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = currentSettings.secondaryFooter,
                onValueChange = { onSettingsChanged(currentSettings.copy(secondaryFooter = it)) },
                label = { Text(stringResource(R.string.settings_field_secondary_footer_message)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_custom_info_blocks_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                IconButton(onClick = onAddInfoBlock) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_info_block))
                }
            }
            Text(
                stringResource(R.string.settings_custom_info_blocks_desc),
                fontSize = 11.sp,
                color = Color.Gray
            )

            currentSettings.customInfoBlocks.forEach { block ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(block.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(block.content, fontSize = 11.sp, maxLines = 2, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = block.enabled,
                                onCheckedChange = { isEnabled ->
                                    val updated = currentSettings.customInfoBlocks.map {
                                        if (it.id == block.id) it.copy(enabled = isEnabled) else it
                                    }
                                    onSettingsChanged(currentSettings.copy(customInfoBlocks = updated))
                                }
                            )
                            IconButton(onClick = { onEditInfoBlock(block) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_block), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDeleteInfoBlock(block.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_block), tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.settings_realtime_preview_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)

            val previewBillData = remember(currentSettings) { buildSettingsPreviewBill(currentSettings) }
            val previewBitmap = remember(previewBillData) {
                try {
                    val file = generateBillJpeg(context, previewBillData)
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                color = Color.DarkGray
            ) {
                Box(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = stringResource(R.string.cd_receipt_preview),
                            modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Text(stringResource(R.string.settings_generating_preview), color = Color.White)
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_save_changes))
        }
        OutlinedButton(onClick = onTestPrint, modifier = Modifier.weight(1f), shape = RoundedCornerShape(11.dp)) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_test_print))
        }
        TextButton(onClick = onResetForm) {
            Text(stringResource(R.string.settings_button_reset_form), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MoneyReceiptVoucherSettings(
    currentSettings: InvoiceSettingsData,
    onSettingsChanged: (InvoiceSettingsData) -> Unit,
    onSave: () -> Unit,
    onTestPrint: () -> Unit
) {
    val context = LocalContext.current
    var previewIsBangla by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Realistic3DRepaymentSealIcon(size = 30.dp)
                Column {
                    Text(
                        text = stringResource(R.string.settings_title_due_receipt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle_due_receipt),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // 1. Paper Roll Width (80mm vs 58mm)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.settings_due_receipt_paper_width_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
                Text(
                    text = stringResource(R.string.settings_due_receipt_paper_width_desc),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        80 to stringResource(R.string.settings_paper_width_80),
                        58 to stringResource(R.string.settings_paper_width_58)
                    ).forEach { (width, label) ->
                        val isSelected = currentSettings.paperRollWidthMm == width
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSettingsChanged(currentSettings.copy(paperRollWidthMm = width)) },
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 2. Shop Logo & Brand Mark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsChanged(currentSettings.copy(showDueReceiptLogo = !currentSettings.showDueReceiptLogo)) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_due_receipt_logo_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_due_receipt_logo_desc),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = currentSettings.showDueReceiptLogo,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showDueReceiptLogo = it)) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 3. Shop Header & Address
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsChanged(currentSettings.copy(showDueReceiptHeader = !currentSettings.showDueReceiptHeader)) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_due_receipt_header_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_due_receipt_header_desc),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = currentSettings.showDueReceiptHeader,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showDueReceiptHeader = it)) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 4. Custom Section & Terms / Notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsChanged(currentSettings.copy(showDueReceiptCustomNotes = !currentSettings.showDueReceiptCustomNotes)) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_due_receipt_custom_notes_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_due_receipt_custom_notes_desc),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = currentSettings.showDueReceiptCustomNotes,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showDueReceiptCustomNotes = it)) }
                )
            }

            if (currentSettings.showDueReceiptCustomNotes) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    OutlinedTextField(
                        value = currentSettings.dueReceiptCustomTitle,
                        onValueChange = { onSettingsChanged(currentSettings.copy(dueReceiptCustomTitle = it)) },
                        label = { Text(stringResource(R.string.settings_due_receipt_custom_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = currentSettings.dueReceiptCustomNotes,
                        onValueChange = { onSettingsChanged(currentSettings.copy(dueReceiptCustomNotes = it)) },
                        label = { Text(stringResource(R.string.settings_due_receipt_custom_text_label)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 5. Dual Signature Lines
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsChanged(currentSettings.copy(showDueReceiptSignatures = !currentSettings.showDueReceiptSignatures)) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_due_receipt_signatures_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_due_receipt_signatures_desc),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = currentSettings.showDueReceiptSignatures,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showDueReceiptSignatures = it)) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 6. 1D Barcode Line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsChanged(currentSettings.copy(showDueReceiptBarcode = !currentSettings.showDueReceiptBarcode)) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_due_receipt_barcode_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_due_receipt_barcode_desc),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = currentSettings.showDueReceiptBarcode,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showDueReceiptBarcode = it)) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 7. Scannable QR Code Verification
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSettingsChanged(currentSettings.copy(showDueReceiptQr = !currentSettings.showDueReceiptQr)) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_due_receipt_qr_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_due_receipt_qr_desc),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = currentSettings.showDueReceiptQr,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(showDueReceiptQr = it)) }
                )
            }

            HorizontalDivider()

            // 8. Live Interactive Thermal Paper Slip Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_due_receipt_live_preview_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = if (currentSettings.paperRollWidthMm == 58) stringResource(R.string.settings_paper_roll_58mm_compact) else stringResource(R.string.settings_paper_roll_80mm_standard),
                        fontSize = 10.5.sp,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { previewIsBangla = false },
                            shape = RoundedCornerShape(12.dp),
                            color = if (!previewIsBangla) Color(0xFF10B981) else Color.Transparent
                        ) {
                            Text(
                                text = "EN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!previewIsBangla) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            onClick = { previewIsBangla = true },
                            shape = RoundedCornerShape(12.dp),
                            color = if (previewIsBangla) Color(0xFF10B981) else Color.Transparent
                        ) {
                            Text(
                                text = "বাং",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (previewIsBangla) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            val previewBitmap = remember(currentSettings, previewIsBangla) {
                try {
                    val file = generateDueRepaymentJpeg(
                        context = context,
                        customerName = if (previewIsBangla) "রহিম স্টোর" else "Rahim Store",
                        customerPhone = "01812345678",
                        amountPaid = 5000.0,
                        paymentMethod = "bKash",
                        referenceNote = "TRX-982310",
                        remainingDue = 0.0,
                        storeName = currentSettings.storeName,
                        storeAddress = currentSettings.address,
                        storePhone = currentSettings.phonePrimary,
                        isBangla = previewIsBangla,
                        settings = currentSettings
                    )
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0xFF1E293B)
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 14.dp, horizontal = 10.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        val slipFraction = if (currentSettings.paperRollWidthMm == 58) 0.65f else 0.88f
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = stringResource(R.string.cd_due_voucher_preview),
                            modifier = Modifier
                                .fillMaxWidth(slipFraction)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.settings_generating_preview),
                            color = Color.White,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(11.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_save_changes))
        }
        OutlinedButton(
            onClick = onTestPrint,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(11.dp)
        ) {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_button_test_print))
        }
    }
}

@Composable
private fun OptimizedBackupSettings(
    driveConnection: GoogleDriveHelper.DriveConnection?,
    savedDriveAccountEmail: String = "",
    autoBackupEnabled: Boolean,
    localAutoBackupEnabled: Boolean,
    autoBackupFrequency: String,
    lastLocalBackupTimestamp: Long,
    lastDriveBackupTimestamp: Long,
    driveAutoBackupFrequency: String,
    driveRetentionCount: Int,
    driveNetworkPolicy: String,
    isBackingUpToDrive: Boolean,
    onLocalBackup: () -> Unit,
    onLocalRestore: () -> Unit,
    onExportEncryptedBackup: () -> Unit = {},
    onImportEncryptedBackup: () -> Unit = {},
    onOpenLocalAutoBackups: () -> Unit = {},
    onGoogleSignIn: () -> Unit,
    onGoogleSignOut: () -> Unit,
    onDriveBackupNow: () -> Unit,
    onOpenDriveHistory: () -> Unit,
    onTestDriveConnection: () -> Unit = {},
    onDriveSettingsChanged: (Boolean, String, String, Int) -> Unit,
    onLocalAutoBackupChanged: (Boolean, String) -> Unit
) {
    val isDriveConfigured = driveConnection != null || savedDriveAccountEmail.isNotBlank()
    var activeStorageTab by remember { mutableStateOf(if (isDriveConfigured) "CLOUD" else "LOCAL") }

    // 1. Master Compact Cloud Status & Telemetry Hub
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E1B4B),
                            Color(0xFF1E293B)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(800f, 800f)
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val isOnlineConnected = driveConnection != null
                val isOfflineConnected = driveConnection == null && savedDriveAccountEmail.isNotBlank()
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Realistic3DCloudVaultIcon(
                            size = 42.dp,
                            isSyncing = isBackingUpToDrive
                        )
                        Column {
                            val displayEmail = driveConnection?.email
                                ?: savedDriveAccountEmail.ifBlank { stringResource(R.string.settings_google_drive_cloud_default) }
                            Text(
                                text = displayEmail,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            val statusSubtitle = when {
                                isOnlineConnected -> stringResource(R.string.settings_vault_isolated_label)
                                isOfflineConnected -> stringResource(R.string.settings_offline_cloud_sync_pending)
                                else -> stringResource(R.string.settings_connect_cloud_sync)
                            }
                            Text(
                                text = statusSubtitle,
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Status Pill
                    val pillBg = when {
                        isOnlineConnected -> Color(0xFF10B981).copy(alpha = 0.20f)
                        isOfflineConnected -> Color(0xFFF59E0B).copy(alpha = 0.20f)
                        else -> Color(0xFFEF4444).copy(alpha = 0.20f)
                    }
                    val pillBorder = when {
                        isOnlineConnected -> Color(0xFF34D399).copy(alpha = 0.5f)
                        isOfflineConnected -> Color(0xFFFBBF24).copy(alpha = 0.5f)
                        else -> Color(0xFFF87171).copy(alpha = 0.5f)
                    }
                    val pillText = when {
                        isOnlineConnected -> stringResource(R.string.settings_status_connected_dot)
                        isOfflineConnected -> stringResource(R.string.settings_status_connected_offline)
                        else -> stringResource(R.string.settings_status_not_connected_dot)
                    }
                    val pillTextColor = when {
                        isOnlineConnected -> Color(0xFF34D399)
                        isOfflineConnected -> Color(0xFFFBBF24)
                        else -> Color(0xFFF87171)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = pillBg,
                        border = BorderStroke(1.dp, pillBorder)
                    ) {
                        Text(
                            text = pillText,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = pillTextColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 3-Pillar Telemetry Grid
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.settings_label_last_cloud_backup), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            val lastBackupStr = if (lastDriveBackupTimestamp > 0L) {
                                DateFormatters.formatShortDateTime(lastDriveBackupTimestamp)
                            } else {
                                stringResource(R.string.settings_no_backup_yet)
                            }
                            Text(lastBackupStr, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF38BDF8))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.settings_label_integrity), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            val integrityText = if (lastDriveBackupTimestamp > 0L) stringResource(R.string.settings_value_100_verified) else "-"
                            val integrityColor = if (lastDriveBackupTimestamp > 0L) Color(0xFF34D399) else Color(0xFF94A3B8)
                            Text(integrityText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = integrityColor)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.settings_label_security), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            val securityText = if (lastDriveBackupTimestamp > 0L) stringResource(R.string.settings_value_aes256_armed) else "Standby"
                            Text(securityText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }

                // Cloud Actions
                if (!isDriveConfigured) {
                    Button(
                        onClick = onGoogleSignIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0082FF))
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_button_connect_drive), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDriveBackupNow,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isBackingUpToDrive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0082FF))
                        ) {
                            if (isBackingUpToDrive) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_backing_up), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.settings_button_backup_to_cloud), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenDriveHistory,
                            modifier = Modifier
                                .weight(0.7f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.settings_button_history), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onTestDriveConnection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_test_drive_connection), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // 2. Dual Storage Channel Segmented Switcher
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            val isCloud = activeStorageTab == "CLOUD"
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeStorageTab = "CLOUD" },
                shape = RoundedCornerShape(9.dp),
                color = if (isCloud) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (isCloud) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (isCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_tab_cloud_sync_engine),
                        fontSize = 11.5.sp,
                        fontWeight = if (isCloud) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCloud) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeStorageTab = "LOCAL" },
                shape = RoundedCornerShape(9.dp),
                color = if (!isCloud) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (!isCloud) 2.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SdCard,
                        contentDescription = null,
                        tint = if (!isCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_tab_local_file_storage),
                        fontSize = 11.5.sp,
                        fontWeight = if (!isCloud) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isCloud) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 3. Dynamic Section: Cloud Sync Engine
    if (activeStorageTab == "CLOUD") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Auto cloud backup switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_auto_drive_backup_title),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (!isDriveConfigured) {
                                stringResource(R.string.settings_requires_google_connection)
                            } else if (autoBackupEnabled) {
                                if (driveConnection == null) stringResource(R.string.settings_offline_cloud_sync_pending)
                                else stringResource(R.string.settings_auto_sync_freq, formatFrequencyLabel(driveAutoBackupFrequency))
                            } else {
                                stringResource(R.string.settings_disabled_manual_only)
                            },
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = autoBackupEnabled && isDriveConfigured,
                        enabled = isDriveConfigured,
                        onCheckedChange = { isChecked ->
                            onDriveSettingsChanged(isChecked, driveAutoBackupFrequency, driveNetworkPolicy, driveRetentionCount)
                        },
                        modifier = Modifier.scale(0.85f)
                    )
                }

                if (autoBackupEnabled && isDriveConfigured) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Frequency Pills
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.settings_label_backup_frequency),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("DAILY" to stringResource(R.string.settings_freq_daily), "WEEKLY" to stringResource(R.string.settings_freq_weekly), "MONTHLY" to stringResource(R.string.settings_freq_monthly)).forEach { (key, label) ->
                                val isSelected = driveAutoBackupFrequency.equals(key, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onDriveSettingsChanged(true, key, driveNetworkPolicy, driveRetentionCount) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(vertical = 6.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }

                    // Network Policy Pills
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.settings_label_network_constraint),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("WIFI_ONLY" to stringResource(R.string.settings_network_wifi_only), "ANY" to stringResource(R.string.settings_network_any)).forEach { (key, label) ->
                                val isSelected = driveNetworkPolicy.equals(key, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onDriveSettingsChanged(true, driveAutoBackupFrequency, key, driveRetentionCount) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(vertical = 6.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }

                    // Retention Limit Pills
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.settings_label_retention_limit),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(7 to stringResource(R.string.settings_retention_keep_7), 14 to stringResource(R.string.settings_retention_keep_14), 30 to stringResource(R.string.settings_retention_keep_30)).forEach { (count, label) ->
                                val isSelected = driveRetentionCount == count
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onDriveSettingsChanged(true, driveAutoBackupFrequency, driveNetworkPolicy, count) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(vertical = 6.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Dynamic Section: Local Device Storage
    if (activeStorageTab == "LOCAL") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dual Action Grid with 3D Icons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onLocalBackup() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Realistic3DLocalVaultDiskIcon(size = 34.dp)
                            Column {
                                Text(stringResource(R.string.settings_export_backup_title), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.settings_save_local_json), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onLocalRestore() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Realistic3DRestoreRewindIcon(size = 34.dp)
                            Column {
                                Text(stringResource(R.string.settings_restore_file_title), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.settings_load_local_json), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Encrypted Backup Row (.posenc)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(stringResource(R.string.settings_vault_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(R.string.settings_vault_desc), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExportEncryptedBackup,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_export_posenc), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onImportEncryptedBackup,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(9.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_import_posenc), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Auto local backup switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_auto_local_backup_title),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val lastBackup = if (lastLocalBackupTimestamp > 0L) {
                            DateFormatters.formatShortDateTime(lastLocalBackupTimestamp)
                        } else null
                        Text(
                            text = if (localAutoBackupEnabled) {
                                if (lastBackup != null) stringResource(R.string.settings_last_backup_freq, lastBackup ?: "", formatFrequencyLabel(autoBackupFrequency))
                                else stringResource(R.string.settings_scheduled_freq, formatFrequencyLabel(autoBackupFrequency))
                            } else stringResource(R.string.settings_off_manual_backup_active),
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = localAutoBackupEnabled,
                        onCheckedChange = { isChecked ->
                            onLocalAutoBackupChanged(isChecked, autoBackupFrequency)
                        },
                        modifier = Modifier.scale(0.85f)
                    )
                }

                if (localAutoBackupEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.settings_label_local_frequency),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("DAILY" to stringResource(R.string.settings_freq_daily), "WEEKLY" to stringResource(R.string.settings_freq_weekly), "MONTHLY" to stringResource(R.string.settings_freq_monthly)).forEach { (key, label) ->
                                val isSelected = autoBackupFrequency.equals(key, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onLocalAutoBackupChanged(true, key) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(vertical = 6.dp)
                                            .wrapContentWidth(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                OutlinedButton(
                    onClick = onOpenLocalAutoBackups,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_local_auto_backups_title), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 5. Military-Grade AES-256 Security Banner
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10B981).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Realistic3DAesShieldIcon(size = 36.dp)
            Column {
                Text(
                    text = stringResource(R.string.settings_aes_encryption_title),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF047857)
                )
                Text(
                    text = stringResource(R.string.settings_aes_encryption_desc),
                    fontSize = 10.sp,
                    color = Color(0xFF065F46),
                    lineHeight = 13.5.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }

    // Disconnect Button
    if (driveConnection != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(
                onClick = onGoogleSignOut,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.settings_button_disconnect_google), color = MaterialTheme.colorScheme.error, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OptimizedTransferSettings(
    onExportProducts: () -> Unit,
    onImportProducts: () -> Unit,
    onExportSales: () -> Unit,
    onExportDues: () -> Unit,
    onDownloadTemplate: () -> Unit
) {
    SettingsSummaryCard3D(
        icon = { Realistic3DSpreadsheetTransferIcon(size = 36.dp) },
        title = stringResource(R.string.settings_transfer_title),
        subtitle = stringResource(R.string.settings_transfer_subtitle)
    )
    SettingsGroup {
        SettingsNavigationRow(Icons.Default.UploadFile, stringResource(R.string.settings_row_export_products_title), stringResource(R.string.settings_row_export_products_subtitle), onClick = onExportProducts)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsNavigationRow(Icons.Default.Download, stringResource(R.string.settings_row_import_products_title), stringResource(R.string.settings_row_import_products_subtitle), onClick = onImportProducts)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsNavigationRow(Icons.Default.UploadFile, stringResource(R.string.settings_row_export_sales_title), stringResource(R.string.settings_row_export_sales_subtitle), onClick = onExportSales)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsNavigationRow(Icons.Default.UploadFile, stringResource(R.string.settings_row_export_dues_title), stringResource(R.string.settings_row_export_dues_subtitle), onClick = onExportDues)
    }
    OutlinedButton(
        onClick = onDownloadTemplate,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp)
    ) {
        Icon(Icons.Default.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(7.dp))
        Text(stringResource(R.string.settings_button_download_template))
    }
}

@Composable
private fun OptimizedAboutSettings() {
    val context = LocalContext.current
    var showCrashLogs by remember { mutableStateOf(false) }
    var crashLogFiles by remember { mutableStateOf(com.example.techyfipos.utils.CrashLogger.listLogs(context)) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.techyfi_logo),
            contentDescription = stringResource(R.string.cd_techyfi_logo),
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.settings_app_name_display), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.settings_app_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsStatusPill(stringResource(R.string.settings_version_pro_pill))
        }
    }
    SettingsGroup {
        SettingsStaticRow(stringResource(R.string.settings_label_developer), "Techyfi")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsStaticRow(stringResource(R.string.settings_label_developed_by), "Shahidul H Tareq")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsStaticRow(stringResource(R.string.settings_label_country), stringResource(R.string.settings_value_country_bangladesh))
    }

    SettingsSectionLabel("Diagnostics")
    SettingsGroup {
        SettingsNavigationRow3D(
            icon = { Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = stringResource(R.string.settings_crash_logs_title),
            subtitle = if (crashLogFiles.isEmpty()) "No crashes recorded" else "${crashLogFiles.size} recorded — tap to view & share",
            onClick = {
                crashLogFiles = com.example.techyfipos.utils.CrashLogger.listLogs(context)
                showCrashLogs = true
            }
        )
    }

    Text(
        stringResource(R.string.settings_copyright_footer),
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (showCrashLogs) {
        CrashLogsDialog(
            files = crashLogFiles,
            onDismiss = { showCrashLogs = false },
            onCleared = {
                com.example.techyfipos.utils.CrashLogger.deleteAllLogs(context)
                crashLogFiles = emptyList()
            }
        )
    }
}

@Composable
private fun CrashLogsDialog(
    files: List<java.io.File>,
    onDismiss: () -> Unit,
    onCleared: () -> Unit
) {
    val context = LocalContext.current
    var expandedFile by remember { mutableStateOf<java.io.File?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.settings_crash_logs_title_fmt, files.size)) },
        text = {
            if (files.isEmpty()) {
                Text(stringResource(R.string.settings_no_crashes), color = Color.Gray, fontSize = 13.sp)
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    files.forEach { file ->
                        Surface(
                            onClick = { expandedFile = if (expandedFile == file) null else file },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name.removePrefix("crash_").removeSuffix(".txt"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(
                                            com.example.techyfipos.utils.CrashLogger.summarize(file),
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            maxLines = if (expandedFile == file) Int.MAX_VALUE else 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = {
                                        shareReceiptAsText(context, com.example.techyfipos.utils.CrashLogger.readLog(file))
                                    }) {
                                        Text(stringResource(R.string.share), fontSize = 12.sp)
                                    }
                                }
                                if (expandedFile == file) {
                                    Text(
                                        com.example.techyfipos.utils.CrashLogger.readLog(file),
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = if (files.isNotEmpty()) {
            {
                var showConfirmClear by remember { mutableStateOf(false) }
                if (showConfirmClear) {
                    AlertDialog(
                        onDismissRequest = { showConfirmClear = false },
                        properties = DialogProperties(dismissOnClickOutside = false),
                        title = { Text(stringResource(R.string.confirm)) },
                        text = { Text(stringResource(R.string.settings_confirm_clear_crash_logs_msg)) },
                        confirmButton = {
                            TextButton(onClick = { showConfirmClear = false; onCleared(); onDismiss() }) {
                                Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmClear = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
                TextButton(onClick = { showConfirmClear = true }) { Text(stringResource(R.string.settings_clear_all), color = MaterialTheme.colorScheme.error) }
            }
        } else null
    )
}

@Composable
private fun SettingsSummaryCard3D(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSummaryCard(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsIconBox(icon)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        content = { Column(content = content) }
    )
}

@Composable
private fun SettingsNavigationRow3D(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    status: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (status != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        SettingsNavigationRowContent(icon, title, subtitle, status, tint, background)
    }
}

@Composable
private fun SettingsNavigationRowContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon, tint, background)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        status?.let {
            Spacer(modifier = Modifier.width(6.dp))
            SettingsStatusPill(it)
        }
        Spacer(modifier = Modifier.width(5.dp))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsIconBox(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SettingsStatusPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun SettingsLargeAction(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(13.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(9.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsStaticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(13.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun buildSettingsPreviewBill(settings: InvoiceSettingsData): BillData = BillData(
    saleId = 12L,
    timestampMillis = System.currentTimeMillis(),
    shopName = settings.storeName,
    shopAddress = settings.address,
    shopPhone = if (settings.phoneSecondary.isNotBlank()) {
        "${settings.phonePrimary} | ${settings.phoneSecondary}"
    } else {
        settings.phonePrimary
    },
    items = listOf(
        BillLineItem("Aci Nazir 5 Kg", 1.0, 530.0, 530.0, mrpPrice = 580.0),
        BillLineItem("Aci Suji 500 gm", 1.0, 48.0, 48.0, mrpPrice = 55.0),
        BillLineItem("Ama 3in1 Coffee", 1.0, 10.0, 10.0, mrpPrice = 12.0)
    ),
    subtotal = 588.0,
    discountValue = 10.0,
    grandTotal = 578.0,
    paymentMethod = "Cash",
    customerName = "Sample Customer",
    customerPhone = "01700-000000",
    amountPaid = 578.0,
    changeAmount = 0.0,
    logoUri = settings.logoUri,
    facebookUrl = settings.facebookUrl,
    cashierName = settings.cashierName,
    settingsData = settings,
    mrpSubtotal = 647.0,
    mrpSavings = 59.0,
    isSaleMode = true,
    showItemWiseMrpDiscount = settings.showItemWiseMrpDiscount
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    isOwner: Boolean = true,
    isTablet: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    onBack: () -> Unit,
    onExpensesClick: () -> Unit = {},
    onSuppliersClick: () -> Unit = {},
    onManageUsersClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current
    val scope = rememberCoroutineScope()
    val signingSha1 = remember(context) { SigningCertificateHelper.sha1Fingerprint(context) }

    val currentLang by settingsViewModel.language.collectAsStateWithLifecycle()
    val currentThemeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val currentLayoutMode by settingsViewModel.uiLayoutMode.collectAsStateWithLifecycle()
    val currentDefaultCostPercentage by settingsViewModel.defaultCostPercentage.collectAsStateWithLifecycle()
    val invoiceSettingsData by settingsViewModel.invoiceSettingsData.collectAsStateWithLifecycle()
    var currentSettings by remember { mutableStateOf(InvoiceSettingsData()) }

    var showAddBlockDialog by remember { mutableStateOf(false) }
    var blockTitleInput by remember { mutableStateOf("") }
    var blockContentInput by remember { mutableStateOf("") }
    var editingBlockId by remember { mutableStateOf<String?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var resetAcknowledged by remember { mutableStateOf(false) }
    var duplicateLedgerGroups by remember { mutableStateOf<List<DataRepository.DuplicateDueLedgerGroup>?>(null) }
    var isCheckingDuplicateLedgers by remember { mutableStateOf(false) }
    var importFailureDetails by remember { mutableStateOf<List<String>?>(null) }
    var settingsPage by remember { mutableStateOf(SettingsPage.HOME) }

    // Sync state once ready
    LaunchedEffect(invoiceSettingsData) {
        currentSettings = invoiceSettingsData
    }

    // Logo Picker Launcher
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val file = File(context.filesDir, "store_logo.png")
                    val outputStream = FileOutputStream(file)
                    inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
                    val savedUri = Uri.fromFile(file).toString()
                    withContext(Dispatchers.Main) {
                        currentSettings = currentSettings.copy(logoUri = savedUri)
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        Toast.makeText(context, context.getString(R.string.settings_toast_logo_updated), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.settings_toast_logo_save_failed, e.localizedMessage), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var isLocalBackupBusy by remember { mutableStateOf(false) }

    // Document Picker for Database Backup Export
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            isLocalBackupBusy = true
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        settingsViewModel.backupDatabase(output)
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                } finally {
                    withContext(Dispatchers.Main) {
                        isLocalBackupBusy = false
                    }
                }
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(context, context.getString(R.string.backup_success), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.backup_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    // Document Picker for Database Restore Import. The user explicitly chooses whether this is a
    // true replacement or a merge so stale records can never be kept accidentally.
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        pendingRestoreUri = uri
    }

    var pendingEncryptedExportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEncryptedImportUri by remember { mutableStateOf<Uri?>(null) }
    var showEncryptedExportPasswordDialog by remember { mutableStateOf(false) }
    var showEncryptedImportPasswordDialog by remember { mutableStateOf(false) }

    val exportEncryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            pendingEncryptedExportUri = uri
            showEncryptedExportPasswordDialog = true
        }
    }

    val importEncryptedBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingEncryptedImportUri = uri
            showEncryptedImportPasswordDialog = true
        }
    }

    // Google Identity Services authorization state. Drive permission is requested independently
    // from app authentication, as recommended by the current AuthorizationClient API.
    val driveAuthorizationClient = remember { GoogleDriveHelper.getAuthorizationClient(context) }
    val settingsStore = remember { SettingsDataStore(context) }
    val savedDriveAccountEmail by settingsStore.driveAccountEmailFlow.collectAsStateWithLifecycle(initialValue = "")
    var driveConnection by remember { mutableStateOf<GoogleDriveHelper.DriveConnection?>(null) }
    var showDriveBackupsDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf<GoogleDriveHelper.DriveBackupFile?>(null) }
    var showGoogleConfigDialog by remember { mutableStateOf(false) }
    var showLocalAutoBackupsDialog by remember { mutableStateOf(false) }
    var selectedAutoBackupFileForRestore by remember { mutableStateOf<File?>(null) }

    val driveBackups by settingsViewModel.driveBackups.collectAsStateWithLifecycle()
    val isLoadingBackups by settingsViewModel.isLoadingBackups.collectAsStateWithLifecycle()
    val autoBackupEnabled by settingsViewModel.autoBackup.collectAsStateWithLifecycle()
    val localAutoBackupEnabled by settingsViewModel.localAutoBackup.collectAsStateWithLifecycle()
    val autoBackupFrequency by settingsViewModel.autoBackupFrequency.collectAsStateWithLifecycle()
    val driveAutoBackupFrequency by settingsViewModel.driveAutoBackupFrequency.collectAsStateWithLifecycle()
    val lastLocalBackupTimestamp by settingsViewModel.lastLocalBackupTimestamp.collectAsStateWithLifecycle()
    val lastDriveBackupTimestamp by settingsViewModel.lastDriveBackupTimestamp.collectAsStateWithLifecycle()
    val driveRetentionCount by settingsViewModel.driveRetentionCount.collectAsStateWithLifecycle()
    val driveNetworkPolicy by settingsViewModel.driveNetworkPolicy.collectAsStateWithLifecycle()

    var isBackingUpToDrive by remember { mutableStateOf(false) }
    var inspectedBackupMetadata by remember { mutableStateOf<com.example.techyfipos.utils.BackupPackageManager.BackupMetadata?>(null) }
    var isInspectingBackup by remember { mutableStateOf(false) }
    var isRestoringFromDrive by remember { mutableStateOf(false) }

    val completeDriveAuthorization: (AuthorizationResult) -> Unit = { authorizationResult ->
        scope.launch {
            try {
                val connection = GoogleDriveHelper.resolveConnection(authorizationResult)
                settingsStore.saveDriveIdentity(connection.accountId, connection.email)
                driveConnection = connection
                Toast.makeText(context, context.getString(R.string.settings_toast_drive_connected), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.settings_toast_drive_connection_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val data = result.data ?: error("Google authorization was cancelled")
            completeDriveAuthorization(driveAuthorizationClient.getAuthorizationResultFromIntent(data))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.settings_toast_drive_auth_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
        }
    }

    fun requestDriveAuthorization() {
        scope.launch {
            val savedEmail = settingsStore.driveAccountEmailFlow.first().takeIf { it.isNotBlank() }
            driveAuthorizationClient.authorize(GoogleDriveHelper.buildAuthorizationRequest(savedEmail))
                .addOnSuccessListener { authorizationResult ->
                    val pendingIntent = authorizationResult.pendingIntent
                    if (authorizationResult.hasResolution() && pendingIntent != null) {
                        driveAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        completeDriveAuthorization(authorizationResult)
                    }
                }
                .addOnFailureListener { error ->
                    error.printStackTrace()
                    showGoogleConfigDialog = true
                    Toast.makeText(context, context.getString(R.string.settings_toast_drive_auth_failed_simple), Toast.LENGTH_LONG).show()
                }
        }
    }

    LaunchedEffect(Unit) {
        val savedEmail = settingsStore.driveAccountEmailFlow.first().takeIf { it.isNotBlank() }
        driveConnection = runCatching {
            GoogleDriveHelper.authorizeWithoutUi(context, savedEmail)
        }.onSuccess { connection ->
            connection?.let { settingsStore.saveDriveIdentity(it.accountId, it.email) }
        }.getOrNull()
    }

    val exportProductsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        settingsViewModel.exportProductsToExcel(output)
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(context, context.getString(R.string.excel_export_success), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.excel_export_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val exportSalesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        settingsViewModel.exportSalesReport(output)
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(context, context.getString(R.string.excel_export_success), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.excel_export_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val exportDuesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        settingsViewModel.exportDuesReport(output)
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(context, context.getString(R.string.excel_export_success), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.excel_export_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val downloadTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        settingsViewModel.generateTemplate(output)
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                withContext(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(context, context.getString(R.string.settings_toast_template_downloaded), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.settings_toast_download_failed), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importProductsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val result = settingsViewModel.importProductsFromExcel(input)
                        withContext(Dispatchers.Main) {
                            val msg = context.getString(R.string.excel_import_success, result.successCount, result.failCount)
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (result.failures.isNotEmpty()) {
                                result.failures.forEach { android.util.Log.e("ExcelImport", it) }
                                importFailureDetails = result.failures
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.excel_import_failed), Toast.LENGTH_LONG).show()
                        importFailureDetails = listOf(e.message ?: e.toString())
                    }
                }
            }
        }
    }

    val settingsTitle = when (settingsPage) {
        SettingsPage.HOME -> stringResource(R.string.settings)
        SettingsPage.RECEIPT -> stringResource(R.string.settings_title_receipt)
        SettingsPage.SHOP_IDENTITY -> stringResource(R.string.settings_title_shop_identity)
        SettingsPage.SHOP_LOGO -> stringResource(R.string.settings_title_shop_logo)
        SettingsPage.QR_CODE_LINKS -> stringResource(R.string.settings_title_qr_links)
        SettingsPage.RECEIPT_LAYOUT -> stringResource(R.string.settings_title_receipt_layout)
        SettingsPage.DUE_RECEIPT -> stringResource(R.string.settings_title_due_receipt)
        SettingsPage.TAX -> stringResource(R.string.settings_row_tax_title)
        SettingsPage.BACKUP -> stringResource(R.string.settings_row_backup_restore_title)
        SettingsPage.TRANSFER -> stringResource(R.string.settings_title_import_export)
        SettingsPage.ABOUT -> stringResource(R.string.settings_section_about)
    }
    val settingsSubtitle = when (settingsPage) {
        SettingsPage.HOME -> stringResource(R.string.settings_subtitle_home)
        SettingsPage.RECEIPT -> stringResource(R.string.settings_subtitle_receipt)
        SettingsPage.SHOP_IDENTITY -> stringResource(R.string.settings_subtitle_shop_identity)
        SettingsPage.SHOP_LOGO -> stringResource(R.string.settings_subtitle_shop_logo)
        SettingsPage.QR_CODE_LINKS -> stringResource(R.string.settings_subtitle_qr_links)
        SettingsPage.RECEIPT_LAYOUT -> stringResource(R.string.settings_subtitle_receipt_layout)
        SettingsPage.DUE_RECEIPT -> stringResource(R.string.settings_subtitle_due_receipt)
        SettingsPage.TAX -> stringResource(R.string.settings_subtitle_tax)
        SettingsPage.BACKUP -> stringResource(R.string.settings_subtitle_backup)
        SettingsPage.TRANSFER -> stringResource(R.string.settings_subtitle_transfer)
        SettingsPage.ABOUT -> stringResource(R.string.settings_subtitle_about)
    }

    BackHandler(enabled = settingsPage != SettingsPage.HOME) {
        currentSettings = invoiceSettingsData
        settingsPage = when (settingsPage) {
            SettingsPage.SHOP_IDENTITY,
            SettingsPage.SHOP_LOGO,
            SettingsPage.QR_CODE_LINKS,
            SettingsPage.RECEIPT_LAYOUT,
            SettingsPage.DUE_RECEIPT -> SettingsPage.RECEIPT
            else -> SettingsPage.HOME
        }
    }

    val configuration = LocalConfiguration.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = settingsTitle,
                        metricBadge = "v${com.example.techyfipos.BuildConfig.VERSION_NAME} POS",
                        badgeColor = PosSectionColors.Settings,
                        isTablet = isTablet,
                        icon = { size -> Realistic3DSettingsGearIcon(size = size) }
                    )
                },
                navigationIcon = {
                    if (settingsPage != SettingsPage.HOME || !isTablet) {
                        IconButton(
                            onClick = {
                                if (settingsPage != SettingsPage.HOME) {
                                    currentSettings = invoiceSettingsData
                                }
                                settingsPage = when (settingsPage) {
                                    SettingsPage.HOME -> {
                                        onBack()
                                        SettingsPage.HOME
                                    }
                                    SettingsPage.SHOP_IDENTITY,
                                    SettingsPage.SHOP_LOGO,
                                    SettingsPage.QR_CODE_LINKS,
                                    SettingsPage.RECEIPT_LAYOUT,
                                    SettingsPage.DUE_RECEIPT -> SettingsPage.RECEIPT
                                    else -> SettingsPage.HOME
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val homeScrollState = rememberScrollState()
        val subpageScrollState = key(settingsPage) { rememberScrollState() }
        val currentScrollState = if (settingsPage == SettingsPage.HOME) homeScrollState else subpageScrollState

        LaunchedEffect(settingsPage) {
            if (settingsPage != SettingsPage.HOME) {
                currentScrollState.scrollTo(0)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (isTablet) 760.dp else Dp.Unspecified)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .verticalScroll(currentScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            when (settingsPage) {
                SettingsPage.HOME -> OptimizedSettingsHome(
                    currentSettings = currentSettings,
                    onUpdateSettings = { updated ->
                        currentSettings = updated
                        settingsViewModel.saveInvoiceSettingsData(updated)
                    },
                    storeName = currentSettings.storeName,
                    address = currentSettings.address,
                    paperRollWidthMm = currentSettings.paperRollWidthMm,
                    currentLanguage = currentLang,
                    currentThemeMode = currentThemeMode,
                    receiptConfigured = currentSettings.storeName.isNotBlank(),
                    driveConnected = driveConnection != null || savedDriveAccountEmail.isNotBlank(),
                    taxEnabled = currentSettings.taxEnabled,
                    allowNegativeInventory = currentSettings.allowNegativeInventory,
                    onAllowNegativeInventoryChanged = { enabled ->
                        val updated = currentSettings.copy(allowNegativeInventory = enabled)
                        currentSettings = updated
                        settingsViewModel.saveInvoiceSettingsData(updated)
                    },
                    cashDrawerKickEnabled = currentSettings.cashDrawerKickEnabled,
                    onCashDrawerKickChanged = { enabled ->
                        val updated = currentSettings.copy(cashDrawerKickEnabled = enabled)
                        currentSettings = updated
                        settingsViewModel.saveInvoiceSettingsData(updated)
                    },
                    cashDrawerPrinterAddress = currentSettings.cashDrawerPrinterAddress,
                    onCashDrawerPrinterSelected = { address ->
                        val updated = currentSettings.copy(cashDrawerPrinterAddress = address)
                        currentSettings = updated
                        settingsViewModel.saveInvoiceSettingsData(updated)
                    },
                    onLanguageSelected = settingsViewModel::saveLanguage,
                    onThemeModeSelected = settingsViewModel::saveThemeMode,
                    currentLayoutMode = currentLayoutMode,
                    onLayoutModeSelected = settingsViewModel::saveUiLayoutMode,
                    defaultCostPercentage = currentDefaultCostPercentage,
                    onDefaultCostPercentageChanged = settingsViewModel::saveDefaultCostPercentage,
                    onExpensesClick = onExpensesClick,
                    onSuppliersClick = onSuppliersClick,
                    onReceiptClick = { settingsPage = SettingsPage.RECEIPT },
                    onTaxClick = { settingsPage = SettingsPage.TAX },
                    onManageUsersClick = onManageUsersClick,
                    onLogoutClick = onLogout,
                    onBackupClick = { settingsPage = SettingsPage.BACKUP },
                    onTransferClick = { settingsPage = SettingsPage.TRANSFER },
                    onAboutClick = { settingsPage = SettingsPage.ABOUT },
                    onResetClick = {
                        if (isOwner) {
                            resetAcknowledged = false
                            showResetConfirmDialog = true
                        } else {
                            Toast.makeText(context, context.getString(R.string.settings_reset_owner_only_toast), Toast.LENGTH_LONG).show()
                        }
                    },
                    onRepairDuesClick = {
                        if (isOwner) {
                            isCheckingDuplicateLedgers = true
                            settingsViewModel.previewDuplicateDueLedgers { groups ->
                                isCheckingDuplicateLedgers = false
                                duplicateLedgerGroups = groups
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.owner_only_settings), Toast.LENGTH_SHORT).show()
                        }
                    },
                    isOwner = isOwner
                )
                SettingsPage.RECEIPT -> OptimizedReceiptOverview(
                    settings = currentSettings,
                    onOpenShopIdentity = { settingsPage = SettingsPage.SHOP_IDENTITY },
                    onOpenShopLogo = { settingsPage = SettingsPage.SHOP_LOGO },
                    onOpenQrCodeLinks = { settingsPage = SettingsPage.QR_CODE_LINKS },
                    onOpenReceiptLayout = { settingsPage = SettingsPage.RECEIPT_LAYOUT },
                    onOpenDueReceipt = { settingsPage = SettingsPage.DUE_RECEIPT }
                )
                SettingsPage.DUE_RECEIPT -> MoneyReceiptVoucherSettings(
                    currentSettings = currentSettings,
                    onSettingsChanged = { currentSettings = it },
                    onSave = {
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        Toast.makeText(context, context.getString(R.string.settings_toast_due_receipt_saved), Toast.LENGTH_SHORT).show()
                    },
                    onTestPrint = {
                        scope.launch(Dispatchers.IO) {
                            val testFile = generateDueRepaymentJpeg(
                                context = context,
                                customerName = "Rahim Store",
                                customerPhone = "01812345678",
                                amountPaid = 5000.0,
                                paymentMethod = "bKash",
                                referenceNote = "TRX-982310",
                                remainingDue = 0.0,
                                storeName = currentSettings.storeName,
                                storeAddress = currentSettings.address,
                                storePhone = currentSettings.phonePrimary,
                                isBangla = false,
                                settings = currentSettings
                            )
                            printReceiptImage(
                                context = context,
                                activity = activity ?: context.findActivity(),
                                imageFile = testFile,
                                jobName = "Test_Repayment_#1",
                                settings = currentSettings,
                                kickDrawer = false
                            )
                        }
                    }
                )
                SettingsPage.SHOP_IDENTITY -> ShopIdentitySettings(
                    currentSettings = currentSettings,
                    onSettingsChanged = { currentSettings = it },
                    onSave = {
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        settingsViewModel.saveShopInfo(currentSettings.storeName, currentSettings.address, currentSettings.phonePrimary)
                        settingsViewModel.saveInvoiceSettings(currentSettings.logoUri, currentSettings.facebookUrl, currentSettings.qrCustomUrl, currentSettings.cashierName)
                        Toast.makeText(context, context.getString(R.string.settings_toast_shop_identity_saved), Toast.LENGTH_SHORT).show()
                    },
                    onTestPrint = {
                        scope.launch(Dispatchers.IO) {
                            val testFile = generateBillJpeg(context, buildSettingsPreviewBill(currentSettings))
                            printReceiptImage(
                                context = context,
                                activity = activity ?: context.findActivity(),
                                imageFile = testFile,
                                jobName = "Test_Receipt_#12",
                                settings = currentSettings,
                                kickDrawer = false
                            )
                        }
                    }
                )
                SettingsPage.SHOP_LOGO -> ShopLogoSettings(
                    currentSettings = currentSettings,
                    onSettingsChanged = { currentSettings = it },
                    onPickLogo = { logoPickerLauncher.launch("image/*") },
                    onSave = {
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        Toast.makeText(context, context.getString(R.string.settings_toast_shop_logo_saved), Toast.LENGTH_SHORT).show()
                    },
                    onTestPrint = {
                        scope.launch(Dispatchers.IO) {
                            val testFile = generateBillJpeg(context, buildSettingsPreviewBill(currentSettings))
                            printReceiptImage(
                                context = context,
                                activity = activity ?: context.findActivity(),
                                imageFile = testFile,
                                jobName = "Test_Receipt_#12",
                                settings = currentSettings,
                                kickDrawer = false
                            )
                        }
                    }
                )
                SettingsPage.QR_CODE_LINKS -> QrCodeLinksSettings(
                    currentSettings = currentSettings,
                    onSettingsChanged = { currentSettings = it },
                    onSave = {
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        Toast.makeText(context, context.getString(R.string.settings_toast_qr_links_saved), Toast.LENGTH_SHORT).show()
                    },
                    onTestPrint = {
                        scope.launch(Dispatchers.IO) {
                            val testFile = generateBillJpeg(context, buildSettingsPreviewBill(currentSettings))
                            printReceiptImage(
                                context = context,
                                activity = activity ?: context.findActivity(),
                                imageFile = testFile,
                                jobName = "Test_Receipt_#12",
                                settings = currentSettings,
                                kickDrawer = false
                            )
                        }
                    }
                )
                SettingsPage.RECEIPT_LAYOUT -> ReceiptLayoutSettings(
                    currentSettings = currentSettings,
                    onSettingsChanged = { currentSettings = it },
                    onAddInfoBlock = {
                        editingBlockId = null
                        blockTitleInput = ""
                        blockContentInput = ""
                        showAddBlockDialog = true
                    },
                    onEditInfoBlock = { block ->
                        editingBlockId = block.id
                        blockTitleInput = block.title
                        blockContentInput = block.content
                        showAddBlockDialog = true
                    },
                    onDeleteInfoBlock = { id ->
                        val updated = currentSettings.customInfoBlocks.filter { it.id != id }
                        currentSettings = currentSettings.copy(customInfoBlocks = updated)
                    },
                    onSave = {
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        Toast.makeText(context, context.getString(R.string.settings_toast_receipt_layout_saved), Toast.LENGTH_SHORT).show()
                    },
                    onTestPrint = {
                        scope.launch(Dispatchers.IO) {
                            val testFile = generateBillJpeg(context, buildSettingsPreviewBill(currentSettings))
                            printReceiptImage(
                                context = context,
                                activity = activity ?: context.findActivity(),
                                imageFile = testFile,
                                jobName = "Test_Receipt_#12",
                                settings = currentSettings,
                                kickDrawer = false
                            )
                        }
                    },
                    onResetForm = {
                        currentSettings = InvoiceSettingsData()
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        Toast.makeText(context, context.getString(R.string.settings_toast_reset_form_default), Toast.LENGTH_SHORT).show()
                    }
                )
                SettingsPage.TAX -> TaxSettings(
                    currentSettings = currentSettings,
                    onSettingsChanged = { currentSettings = it },
                    onSave = {
                        settingsViewModel.saveInvoiceSettingsData(currentSettings)
                        Toast.makeText(context, context.getString(R.string.settings_toast_tax_settings_saved), Toast.LENGTH_SHORT).show()
                    }
                )
                SettingsPage.BACKUP -> OptimizedBackupSettings(
                    driveConnection = driveConnection,
                    savedDriveAccountEmail = savedDriveAccountEmail,
                    autoBackupEnabled = autoBackupEnabled,
                    localAutoBackupEnabled = localAutoBackupEnabled,
                    autoBackupFrequency = autoBackupFrequency,
                    lastLocalBackupTimestamp = lastLocalBackupTimestamp,
                    lastDriveBackupTimestamp = lastDriveBackupTimestamp,
                    driveAutoBackupFrequency = driveAutoBackupFrequency,
                    driveRetentionCount = driveRetentionCount,
                    driveNetworkPolicy = driveNetworkPolicy,
                    isBackingUpToDrive = isBackingUpToDrive,
                    onLocalBackup = {
                        val dateStr = DateFormatters.formatISO()
                        backupLauncher.launch("techyfi-pos-backup-$dateStr.json")
                    },
                    onLocalRestore = {
                        if (isOwner) {
                            restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
                        } else {
                            Toast.makeText(context, context.getString(R.string.settings_restore_owner_only_toast), Toast.LENGTH_LONG).show()
                        }
                    },
                    onExportEncryptedBackup = {
                        val dateStr = DateFormatters.formatISO()
                        exportEncryptedBackupLauncher.launch("techyfi-pos-encrypted-$dateStr.posenc")
                    },
                    onImportEncryptedBackup = {
                        if (isOwner) {
                            importEncryptedBackupLauncher.launch(arrayOf("*/*"))
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_only_owner_restore_backup), Toast.LENGTH_LONG).show()
                        }
                    },
                    onOpenLocalAutoBackups = { showLocalAutoBackupsDialog = true },
                    onTestDriveConnection = {
                        val connection = driveConnection
                        if (connection != null) {
                            scope.launch {
                                Toast.makeText(context, context.getString(R.string.toast_testing_drive_connection), Toast.LENGTH_SHORT).show()
                                val ok = GoogleDriveHelper.testDriveConnection(context, connection.email).isSuccess
                                Toast.makeText(
                                    context,
                                    if (ok) "Google Drive connection test passed!" else "Google Drive test failed. Please re-authenticate.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            requestDriveAuthorization()
                        }
                    },
                    onGoogleSignIn = { requestDriveAuthorization() },
                    onGoogleSignOut = {
                        driveConnection?.let { connection ->
                            scope.launch {
                                runCatching { GoogleDriveHelper.revoke(context, connection.email) }
                                settingsStore.clearDriveIdentity()
                                settingsViewModel.saveDriveSettings(
                                    false,
                                    driveAutoBackupFrequency,
                                    driveNetworkPolicy,
                                    driveRetentionCount,
                                    context
                                )
                                driveConnection = null
                                Toast.makeText(context, context.getString(R.string.settings_toast_drive_disconnected), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDriveBackupNow = {
                        val connection = driveConnection
                        if (connection != null) {
                            scope.launch {
                                isBackingUpToDrive = true
                                val ok = settingsViewModel.backupToDriveEncrypted(context, connection)
                                isBackingUpToDrive = false
                                Toast.makeText(
                                    context,
                                    if (ok) context.getString(R.string.settings_toast_cloud_backup_success) else context.getString(R.string.settings_toast_cloud_backup_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            requestDriveAuthorization()
                        }
                    },
                    onOpenDriveHistory = {
                        val connection = driveConnection
                        if (connection != null) {
                            val driveService = GoogleDriveHelper.getDriveService(connection)
                            settingsViewModel.fetchDriveBackups(driveService)
                            showDriveBackupsDialog = true
                        } else {
                            requestDriveAuthorization()
                        }
                    },
                    onDriveSettingsChanged = { enabled, freq, netPolicy, retention ->
                        settingsViewModel.saveDriveSettings(enabled, freq, netPolicy, retention, context)
                    },
                    onLocalAutoBackupChanged = { enabled, freq ->
                        settingsViewModel.saveLocalAutoBackup(enabled, freq, context)
                    }
                )
                SettingsPage.TRANSFER -> OptimizedTransferSettings(
                    onExportProducts = {
                        val dateStr = DateFormatters.formatISO()
                        exportProductsLauncher.launch("products-export-$dateStr.xlsx")
                    },
                    onImportProducts = {
                        importProductsLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    },
                    onExportSales = {
                        val dateStr = DateFormatters.formatISO()
                        exportSalesLauncher.launch("sales-report-$dateStr.xlsx")
                    },
                    onExportDues = {
                        val dateStr = DateFormatters.formatISO()
                        exportDuesLauncher.launch("dues-list-$dateStr.xlsx")
                    },
                    onDownloadTemplate = {
                        val dateStr = DateFormatters.formatISO()
                        downloadTemplateLauncher.launch("products-import-template-$dateStr.xlsx")
                    }
                )
                SettingsPage.ABOUT -> OptimizedAboutSettings()
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
        }
    }

    var showReplaceConfirmDialog by remember { mutableStateOf(false) }
    var isRestoringDatabase by remember { mutableStateOf(false) }

    if (isLocalBackupBusy) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            stringResource(R.string.settings_exporting_local_backup_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_exporting_local_backup_msg),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (isRestoringDatabase) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.settings_restoring_database_progress),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    pendingRestoreUri?.let { restoreUri ->
        if (showReplaceConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showReplaceConfirmDialog = false },
                properties = DialogProperties(dismissOnClickOutside = false),
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.settings_confirm_replace_title)) },
                text = { Text(stringResource(R.string.settings_confirm_replace_desc)) },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            showReplaceConfirmDialog = false
                            val targetUri = pendingRestoreUri
                            pendingRestoreUri = null
                            if (targetUri != null) {
                                isRestoringDatabase = true
                                scope.launch(Dispatchers.IO) {
                                    val ok = try {
                                        context.contentResolver.openInputStream(targetUri)?.use { input ->
                                            settingsViewModel.restoreDatabase(input, BackupImportMode.REPLACE)
                                        } ?: false
                                    } catch (e: Exception) {
                                        Log.e("SettingsScreen", "Error during REPLACE restore", e)
                                        false
                                    }
                                    withContext(Dispatchers.Main) {
                                        isRestoringDatabase = false
                                        if (ok) {
                                            Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                                            restartApp(context)
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.settings_button_replace_all_data))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplaceConfirmDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingRestoreUri = null },
                properties = DialogProperties(dismissOnClickOutside = false),
                icon = { Icon(Icons.Default.Restore, contentDescription = null) },
                title = { Text(stringResource(R.string.settings_dialog_restore_mode_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.settings_restore_replace_desc))
                        Text(stringResource(R.string.settings_restore_merge_desc), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (!isOwner) {
                                Toast.makeText(context, context.getString(R.string.toast_only_owner_restore_backup), Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            showReplaceConfirmDialog = true
                        }) { Text(stringResource(R.string.settings_button_replace_all_data)) }

                        TextButton(onClick = {
                            if (!isOwner) {
                                Toast.makeText(context, context.getString(R.string.toast_only_owner_restore_backup), Toast.LENGTH_LONG).show()
                                return@TextButton
                            }
                            val targetUri = pendingRestoreUri
                            pendingRestoreUri = null
                            if (targetUri != null) {
                                isRestoringDatabase = true
                                scope.launch(Dispatchers.IO) {
                                    val ok = try {
                                        context.contentResolver.openInputStream(targetUri)?.use { input ->
                                            settingsViewModel.restoreDatabase(input, BackupImportMode.MERGE)
                                        } ?: false
                                    } catch (e: Exception) {
                                        Log.e("SettingsScreen", "Error during MERGE restore", e)
                                        false
                                    }
                                    withContext(Dispatchers.Main) {
                                        isRestoringDatabase = false
                                        if (ok) {
                                            Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                                            restartApp(context)
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }) { Text(stringResource(R.string.settings_button_merge)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestoreUri = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    // Local Automatic Backups History Dialog
    if (showLocalAutoBackupsDialog) {
        val autoBackupsDir = remember { File(context.filesDir, "AutoBackups") }
        val backupFiles = remember(showLocalAutoBackupsDialog) {
            autoBackupsDir.listFiles { _, name -> name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }

        AlertDialog(
            onDismissRequest = { showLocalAutoBackupsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_local_auto_backups_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                if (backupFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.settings_no_auto_backups),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(backupFiles) { file ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${DateFormatters.formatShortDateTime(file.lastModified())} • ${file.length() / 1024} KB",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file
                                                    )
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/json"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, file.name))
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    Toast.makeText(context, "Failed to share backup file", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (!isOwner) {
                                                    Toast.makeText(context, context.getString(R.string.toast_only_owner_restore_backup), Toast.LENGTH_LONG).show()
                                                    return@IconButton
                                                }
                                                selectedAutoBackupFileForRestore = file
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Restore,
                                                contentDescription = "Restore",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocalAutoBackupsDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    selectedAutoBackupFileForRestore?.let { fileToRestore ->
        AlertDialog(
            onDismissRequest = { selectedAutoBackupFileForRestore = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_restore_auto_backup_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_restore_auto_backup_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        val file = fileToRestore
                        selectedAutoBackupFileForRestore = null
                        showLocalAutoBackupsDialog = false
                        isRestoringDatabase = true
                        scope.launch(Dispatchers.IO) {
                            val ok = try {
                                java.io.FileInputStream(file).use { input ->
                                    settingsViewModel.restoreDatabase(input, BackupImportMode.REPLACE)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            }
                            withContext(Dispatchers.Main) {
                                isRestoringDatabase = false
                                if (ok) {
                                    Toast.makeText(context, context.getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                                    restartApp(context)
                                } else {
                                    Toast.makeText(context, context.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_button_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedAutoBackupFileForRestore = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Google Drive Backups List Dialog
    if (showDriveBackupsDialog) {
        AlertDialog(
            onDismissRequest = { showDriveBackupsDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_dialog_drive_backups_title), fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            driveConnection?.let { connection ->
                                val driveService = GoogleDriveHelper.getDriveService(connection)
                                settingsViewModel.fetchDriveBackups(driveService)
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh), modifier = Modifier.size(18.dp))
                    }
                }
            },
            text = {
                if (isLoadingBackups) {
                    Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(stringResource(R.string.settings_loading_drive_backups), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (driveBackups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.settings_no_drive_backups_found), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(driveBackups) { backup ->
                            val isAuto = backup.name.contains("auto", ignoreCase = true)
                            val dateStr = if (backup.createdTime > 0L) DateFormatters.formatBackupDate(backup.createdTime) else backup.name
                            val sizeStr = if (backup.size >= 1024 * 1024) "%.1f MB".format(backup.size / (1024.0 * 1024.0)) else "${(backup.size / 1024.0).toInt()} KB"

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dateStr,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(sizeStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isAuto) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = if (isAuto) stringResource(R.string.settings_label_auto) else stringResource(R.string.settings_label_manual),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isAuto) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (isOwner) {
                                        Button(
                                            onClick = {
                                                driveConnection?.let { connection ->
                                                    scope.launch {
                                                        isInspectingBackup = true
                                                        inspectedBackupMetadata = settingsViewModel.inspectDriveBackup(context, connection, backup.id)
                                                        isInspectingBackup = false
                                                        showRestoreConfirmDialog = backup
                                                        showDriveBackupsDialog = false
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(stringResource(R.string.settings_button_restore), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDriveBackupsDialog = false }) {
                    Text(stringResource(R.string.settings_button_close))
                }
            }
        )
    }

    // Google Drive Restore Confirmation Warning Dialog
    if (showRestoreConfirmDialog != null) {
        val backup = showRestoreConfirmDialog!!
        val meta = inspectedBackupMetadata
        val dateStr = if (backup.createdTime > 0L) DateFormatters.formatBackupDate(backup.createdTime) else backup.name
        val sizeStr = if (backup.size >= 1024 * 1024) "%.1f MB".format(backup.size / (1024.0 * 1024.0)) else "${(backup.size / 1024.0).toInt()} KB"

        AlertDialog(
            onDismissRequest = {
                if (!isRestoringFromDrive) {
                    showRestoreConfirmDialog = null
                    inspectedBackupMetadata = null
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(stringResource(R.string.settings_dialog_restore_backup_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.settings_target_backup_info, dateStr, sizeStr),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (meta != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(stringResource(R.string.settings_backup_meta_products, meta.productCount), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.settings_backup_meta_sales, meta.salesCount), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.settings_backup_meta_dues, meta.duesCount), fontSize = 11.5.sp)
                                Text(stringResource(R.string.settings_backup_meta_suppliers_expenses, meta.supplierCount, meta.expenseCount), fontSize = 11.5.sp)
                                Text(stringResource(R.string.settings_backup_meta_store, meta.storeName, meta.appVersion), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.settings_safety_shield_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    if (isRestoringFromDrive) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.settings_restoring_database), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isOwner) {
                            Toast.makeText(context, context.getString(R.string.toast_only_owner_restore_backup), Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        driveConnection?.let { connection ->
                            scope.launch {
                                isRestoringFromDrive = true
                                val result = settingsViewModel.restoreFromDriveEncrypted(context, connection, backup.id)
                                isRestoringFromDrive = false
                                showRestoreConfirmDialog = null
                                inspectedBackupMetadata = null
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                if (result.requiresUiReload) {
                                    restartApp(context)
                                }
                            }
                        }
                    },
                    enabled = !isRestoringFromDrive,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_button_confirm_restore), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        if (!isRestoringFromDrive) {
                            showRestoreConfirmDialog = null
                            inspectedBackupMetadata = null
                        }
                    },
                    enabled = !isRestoringFromDrive
                ) {
                    Text(stringResource(R.string.settings_button_cancel))
                }
            }
        )
    }

    if (showGoogleConfigDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleConfigDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.settings_dialog_drive_setup_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_drive_setup_error_desc),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_label_package_name), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    SelectionContainer {
                        Text(context.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_label_sha1_fingerprint), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    SelectionContainer {
                        Text(signingSha1, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_local_backup_note), fontSize = 12.sp, color = Color.Gray)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("SHA1", signingSha1)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.settings_toast_sha1_copied), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.settings_button_copy_sha1))
                }
            },
            confirmButton = {
                Button(onClick = { showGoogleConfigDialog = false }) {
                    Text(stringResource(R.string.settings_button_got_it))
                }
            }
        )
    }

    // Add / Edit Custom Info Block Dialog
    if (showAddBlockDialog) {
        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(if (editingBlockId != null) stringResource(R.string.settings_dialog_edit_info_block_title) else stringResource(R.string.settings_dialog_add_info_block_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = blockTitleInput,
                        onValueChange = { blockTitleInput = it },
                        label = { Text(stringResource(R.string.settings_field_block_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = blockContentInput,
                        onValueChange = { blockContentInput = it },
                        label = { Text(stringResource(R.string.settings_field_block_content)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (blockTitleInput.isNotBlank()) {
                            val updatedBlocks = currentSettings.customInfoBlocks.toMutableList()
                            if (editingBlockId != null) {
                                val idx = updatedBlocks.indexOfFirst { it.id == editingBlockId }
                                if (idx != -1) {
                                    updatedBlocks[idx] = updatedBlocks[idx].copy(title = blockTitleInput, content = blockContentInput)
                                }
                            } else {
                                updatedBlocks.add(
                                    CustomInfoBlock(
                                        id = System.currentTimeMillis().toString(),
                                        title = blockTitleInput,
                                        content = blockContentInput,
                                        enabled = true,
                                        position = updatedBlocks.size + 1
                                    )
                                )
                            }
                            currentSettings = currentSettings.copy(customInfoBlocks = updatedBlocks)
                            showAddBlockDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.settings_button_save_block))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockDialog = false }) {
                    Text(stringResource(R.string.settings_button_cancel))
                }
            }
        )
    }

    // Factory Reset Confirmation Warning Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                resetAcknowledged = false
                showResetConfirmDialog = false
            },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    stringResource(R.string.settings_dialog_factory_reset_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.settings_factory_reset_confirm_question),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        stringResource(R.string.settings_factory_reset_delete_list),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { resetAcknowledged = !resetAcknowledged }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = resetAcknowledged,
                            onCheckedChange = { resetAcknowledged = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.settings_checkbox_understand_reset),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isOwner) {
                            Toast.makeText(context, context.getString(R.string.toast_only_owner_factory_reset), Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        settingsViewModel.resetFactoryDefaults {
                            currentSettings = InvoiceSettingsData()
                            showResetConfirmDialog = false
                            Toast.makeText(context, context.getString(R.string.settings_toast_factory_reset_done), Toast.LENGTH_LONG).show()
                            onLogout()
                        }
                    },
                    enabled = resetAcknowledged,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_button_erase_everything), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    resetAcknowledged = false
                    showResetConfirmDialog = false
                }) {
                    Text(stringResource(R.string.settings_button_cancel))
                }
            }
        )
    }

    if (isCheckingDuplicateLedgers) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnClickOutside = false),
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.settings_repair_dues_title))
                }
            },
            confirmButton = {}
        )
    }

    duplicateLedgerGroups?.let { groups ->
        AlertDialog(
            onDismissRequest = { duplicateLedgerGroups = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    stringResource(
                        if (groups.isEmpty()) R.string.settings_repair_dues_none_found_title
                        else R.string.settings_repair_dues_found_title
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.settings_repair_dues_none_found_body), fontSize = 13.sp)
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.settings_repair_dues_found_intro),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        groups.forEach { group ->
                            Text(
                                stringResource(
                                    R.string.settings_repair_dues_merge_summary,
                                    group.customerName,
                                    group.canonicalPhone,
                                    group.rows.size,
                                    formatTaka(group.mergedOutstanding)
                                ),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (groups.isNotEmpty()) {
                    Button(
                        onClick = {
                            settingsViewModel.mergeDuplicateDueLedgers { mergedCount ->
                                duplicateLedgerGroups = null
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_repair_dues_toast_done, mergedCount),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            stringResource(R.string.settings_repair_dues_button_merge, groups.size),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { duplicateLedgerGroups = null }) {
                    Text(stringResource(R.string.settings_button_cancel))
                }
            }
        )
    }

    importFailureDetails?.let { failures ->
        AlertDialog(
            onDismissRequest = { importFailureDetails = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text(stringResource(R.string.settings_dialog_import_issues_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_import_issues_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    failures.forEach { reason ->
                        Text(
                            stringResource(R.string.settings_import_issue_row_prefix, reason),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { importFailureDetails = null }) {
                    Text(stringResource(R.string.settings_button_ok))
                }
            }
        )
    }

    if (showEncryptedExportPasswordDialog && pendingEncryptedExportUri != null) {
        val targetUri = pendingEncryptedExportUri!!
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isExporting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isExporting) {
                    showEncryptedExportPasswordDialog = false
                    pendingEncryptedExportUri = null
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_set_backup_password), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a password to encrypt this backup with AES-256-GCM. You will need this exact password to restore this file.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text(stringResource(R.string.settings_password_min_4_chars)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(if (passwordVisible) R.string.settings_hide_password else R.string.settings_show_password)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text(stringResource(R.string.settings_confirm_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.length < 4) {
                            errorMessage = "Password must be at least 4 characters"
                            return@Button
                        }
                        if (password != confirmPassword) {
                            errorMessage = "Passwords do not match"
                            return@Button
                        }
                        isExporting = true
                        scope.launch(Dispatchers.IO) {
                            val ok = try {
                                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                                    settingsViewModel.exportEncryptedLocalBackup(out, password)
                                } ?: false
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            }
                            withContext(Dispatchers.Main) {
                                isExporting = false
                                showEncryptedExportPasswordDialog = false
                                pendingEncryptedExportUri = null
                                Toast.makeText(
                                    context,
                                    if (ok) "Encrypted backup saved successfully (.posenc)!" else "Failed to export encrypted backup",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_encrypt_and_save))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEncryptedExportPasswordDialog = false
                        pendingEncryptedExportUri = null
                    },
                    enabled = !isExporting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showEncryptedImportPasswordDialog && pendingEncryptedImportUri != null) {
        val targetUri = pendingEncryptedImportUri!!
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var isReplacing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isImporting by remember { mutableStateOf(false) }
        var showReplaceConfirmDialogForEncrypted by remember { mutableStateOf(false) }

        val startImportAction: (BackupImportMode) -> Unit = { mode ->
            isImporting = true
            scope.launch(Dispatchers.IO) {
                val ok = try {
                    context.contentResolver.openInputStream(targetUri)?.use { input ->
                        settingsViewModel.importEncryptedLocalBackup(input, password, mode)
                    } ?: false
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                withContext(Dispatchers.Main) {
                    isImporting = false
                    if (ok) {
                        showEncryptedImportPasswordDialog = false
                        pendingEncryptedImportUri = null
                        Toast.makeText(context, context.getString(R.string.settings_encrypted_backup_restored_toast), Toast.LENGTH_LONG).show()
                        restartApp(context)
                    } else {
                        errorMessage = context.getString(R.string.settings_encrypted_backup_decrypt_failed)
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    showEncryptedImportPasswordDialog = false
                    pendingEncryptedImportUri = null
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_decrypt_backup_file), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the password used when creating this .posenc backup file.",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text(stringResource(R.string.settings_backup_password)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(if (passwordVisible) R.string.settings_hide_password else R.string.settings_show_password)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isReplacing,
                            onClick = { isReplacing = true },
                            label = { Text(stringResource(R.string.settings_full_replace)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isReplacing,
                            onClick = { isReplacing = false },
                            label = { Text(stringResource(R.string.settings_merge)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isBlank()) {
                            errorMessage = "Please enter password"
                            return@Button
                        }
                        if (isReplacing) {
                            showReplaceConfirmDialogForEncrypted = true
                        } else {
                            startImportAction(BackupImportMode.MERGE)
                        }
                    },
                    enabled = !isImporting
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.settings_decrypt_and_restore))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEncryptedImportPasswordDialog = false
                        pendingEncryptedImportUri = null
                    },
                    enabled = !isImporting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )

        if (showReplaceConfirmDialogForEncrypted) {
            AlertDialog(
                onDismissRequest = { showReplaceConfirmDialogForEncrypted = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.settings_replace_confirm_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.settings_replace_confirm_msg)) },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            showReplaceConfirmDialogForEncrypted = false
                            startImportAction(BackupImportMode.REPLACE)
                        }
                    ) {
                        Text(stringResource(R.string.settings_full_replace))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplaceConfirmDialogForEncrypted = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun formatFrequencyLabel(freq: String): String {
    return when (freq.trim().lowercase(java.util.Locale.ROOT)) {
        "daily" -> stringResource(R.string.settings_freq_daily)
        "weekly" -> stringResource(R.string.settings_freq_weekly)
        "monthly" -> stringResource(R.string.settings_freq_monthly)
        else -> freq
    }
}
