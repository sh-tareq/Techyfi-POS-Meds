package com.example.techyfipos.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.data.SettingsDataStore
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.db.ReturnLine
import com.example.techyfipos.data.db.SaleItem
import com.example.techyfipos.data.db.SaleWithItems
import com.example.techyfipos.ui.components.Realistic3DReturnBoxIcon
import com.example.techyfipos.ui.viewmodel.calculateSaleDueAmount
import com.example.techyfipos.utils.formatTaka
import org.json.JSONArray
import org.json.JSONObject

data class AuditPreset(
    val id: String,
    val icon: String,
    val text: String
)

private val DEFAULT_AUDIT_PRESETS = listOf(
    AuditPreset("1", "📦", "Damaged box"),
    AuditPreset("2", "📏", "Wrong size"),
    AuditPreset("3", "⚠️", "Defective unit"),
    AuditPreset("4", "💭", "Changed mind"),
    AuditPreset("5", "🎨", "Color mismatch")
)

@Composable
fun getLocalizedPresetText(preset: AuditPreset): String {
    return when (preset.id) {
        "1" -> stringResource(R.string.return_audit_damaged_box)
        "2" -> stringResource(R.string.return_audit_wrong_size)
        "3" -> stringResource(R.string.return_audit_defective_unit)
        "4" -> stringResource(R.string.return_audit_changed_mind)
        "5" -> stringResource(R.string.return_audit_color_mismatch)
        else -> preset.text
    }
}

data class ReasonChipItem(
    val icon: String,
    val reasonKey: String,
    @get:androidx.annotation.StringRes val titleRes: Int,
    @get:androidx.annotation.StringRes val shortTitleRes: Int,
    val isDamaged: Boolean
)

private val REASON_CHIPS = listOf(
    ReasonChipItem("💥", "Defective / Damaged", R.string.return_reason_defective_damaged, R.string.return_reason_short_defective, true),
    ReasonChipItem("🔀", "Wrong Item", R.string.return_reason_wrong_item, R.string.return_reason_short_wrong, false),
    ReasonChipItem("🔄", "Exchange", R.string.return_reason_exchange, R.string.return_reason_short_exchange, false),
    ReasonChipItem("🙋", "Customer Request", R.string.return_reason_customer_request, R.string.return_reason_short_request, false),
    ReasonChipItem("🏷️", "Price Adjust", R.string.return_reason_price_adjust, R.string.return_reason_short_price, false),
    ReasonChipItem("📝", "Other Note", R.string.return_reason_other_note, R.string.return_reason_short_other, false)
)

private val MFS_CHANNELS = listOf("Nagad", "bKash", "Rocket", "Card", "Bank")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReturnItemsDialog(
    saleWithItems: SaleWithItems,
    alreadyReturned: Map<String, Double>,
    storeCreditRestoredSoFar: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (
        lines: List<ReturnLine>,
        refundMethod: String,
        note: String?,
        restock: Boolean,
        walkinPhone: String?,
        walkinName: String?,
        issueVoucher: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val settingsDataStore = remember(context) { SettingsDataStore(context) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        settingsDataStore.migrateLegacyReturnAuditPresetsIfNeeded()
    }

    val customPresetsJson by settingsDataStore.returnAuditPresetsFlow.collectAsStateWithLifecycle(initialValue = null)
    val customPresets = remember(customPresetsJson) { parseAuditPresets(customPresetsJson) }
    var showManagePresetsDialog by remember { mutableStateOf(false) }

    // Direct manual quantity input state
    var manualInputItem by remember { mutableStateOf<SaleItem?>(null) }
    var manualInputInitial by remember { mutableStateOf(0.0) }
    var manualInputMax by remember { mutableStateOf(0.0) }

    val displayItems = remember(saleWithItems) {
        saleWithItems.items.groupBy { it.productBarcode }.map { (_, lines) ->
            lines.first().copy(quantity = lines.sumOf { it.quantity })
        }
    }
    var quantities by remember(saleWithItems) {
        mutableStateOf(displayItems.associate { it.productBarcode to 0.0 })
    }

    // Apportion sale discount/tax proportionally
    val paidRatio = if (saleWithItems.sale.subtotal > 0.0) {
        (saleWithItems.sale.grandTotal / saleWithItems.sale.subtotal).coerceAtLeast(0.0)
    } else 1.0

    val alreadyRefundedEstimate = displayItems.sumOf { item ->
        (alreadyReturned[item.productBarcode] ?: 0.0) * item.price * paidRatio
    }
    val remainingDue = (calculateSaleDueAmount(saleWithItems.sale) - alreadyRefundedEstimate).coerceAtLeast(0.0)
    val hasCustomerDue = remainingDue > 0.0
    val customerName = saleWithItems.sale.customerName?.trim()
    val customerPhone = saleWithItems.sale.customerPhone?.trim()
    val isWalkinCustomer = customerPhone.isNullOrBlank()
    val customerDisplayText = when {
        !customerName.isNullOrBlank() && !customerPhone.isNullOrBlank() -> "$customerName • $customerPhone"
        !customerName.isNullOrBlank() -> customerName
        !customerPhone.isNullOrBlank() -> customerPhone
        else -> stringResource(R.string.walk_in_customer)
    }

    // Walk-in store credit resolution mode: "REGISTER" or "VOUCHER"
    var walkinMode by remember { mutableStateOf("REGISTER") }
    var walkinRegPhone by remember { mutableStateOf("") }
    var walkinRegName by remember { mutableStateOf("") }

    // 4 Settlement Destinations: "CASH" (Default), "DUE", "MFS", "WALLET"
    var selectedSettlementDestination by remember {
        mutableStateOf(if (hasCustomerDue) "DUE" else "CASH")
    }
    var selectedMfsChannel by remember { mutableStateOf("Nagad") }

    var selectedReason by remember { mutableStateOf("Customer Request") }
    var isRestockEnabled by remember { mutableStateOf(true) }
    var returnNote by remember { mutableStateOf("") }

    val refundTotal = displayItems.sumOf { item ->
        (quantities[item.productBarcode] ?: 0.0) * item.price * paidRatio
    }

    // Returns on sales with outstanding debt MUST reduce the customer's debt first
    val dueAdjustment = if (hasCustomerDue) {
        refundTotal.coerceAtMost(remainingDue)
    } else 0.0

    val refundMethodCandidate = when (selectedSettlementDestination) {
        "WALLET" -> "Store Credit"
        "MFS" -> selectedMfsChannel
        else -> "Cash"
    }

    val returnApportionment = com.example.techyfipos.utils.PaymentMath.calculateReturnRefundApportionment(
        refundTotal = refundTotal,
        saleStoreCreditApplied = saleWithItems.sale.storeCreditApplied,
        storeCreditRestoredSoFar = storeCreditRestoredSoFar,
        dueReduction = dueAdjustment,
        refundMethod = refundMethodCandidate
    )
    val storeCreditRestored = if (selectedSettlementDestination == "WALLET") {
        (refundTotal - dueAdjustment).coerceAtLeast(0.0)
    } else {
        returnApportionment.storeCreditToRestore
    }
    val externalRefund = if (selectedSettlementDestination == "WALLET") {
        0.0
    } else {
        returnApportionment.externalRefundAmount
    }

    val hasSelection = quantities.values.any { it > 0.0 }

    // Speech-to-text Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                returnNote = if (returnNote.isBlank()) spokenText else "$returnNote, $spokenText"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .shadow(24.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // --- TOP MODAL HEADER WITH 3D ICON ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Realistic3DReturnBoxIcon(size = 36.dp, animate = true)
                            }
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.return_dialog_return_items),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = "#${saleWithItems.sale.id}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 1.dp)
                            ) {
                                Text(
                                    text = customerDisplayText,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (hasCustomerDue) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color(0xFFFFF1F2),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3))
                                    ) {
                                        Text(
                                            text = stringResource(R.string.return_dialog_due_fmt, formatTaka(remainingDue)),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFE11D48),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // --- SCROLLABLE BODY ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. SELECT QUANTITIES
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.return_select_quantities_header),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${displayItems.size} Item${if (displayItems.size > 1) "s" else ""}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    displayItems.forEach { item ->
                        val already = alreadyReturned[item.productBarcode] ?: 0.0
                        val maxReturnable = (item.quantity - already).coerceAtLeast(0.0)
                        val qty = quantities[item.productBarcode] ?: 0.0
                        val isFullyReturned = maxReturnable <= 0.0
                        val isActive = qty > 0.0

                        val alreadyStr = if (already % 1.0 == 0.0) already.toLong().toString() else String.format(java.util.Locale.US, "%.2f", already).trimEnd('0').trimEnd('.')
                        val soldStr = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(java.util.Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
                        val qtyStr = if (qty <= 0.0) "0" else if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(java.util.Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.')

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) Color(0xFFFAFAFF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isActive) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // --- TOP ROW: Product Icon, Full-Width Product Name, and Active Return Pill Badge ---
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(if (isActive) "↩️" else "📦", fontSize = 15.sp)
                                        }
                                    }

                                    Text(
                                        text = item.productName,
                                        style = TextStyle(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 18.sp,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            lineHeightStyle = LineHeightStyle(
                                                alignment = LineHeightStyle.Alignment.Center,
                                                trim = LineHeightStyle.Trim.None
                                            )
                                        ),
                                        color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.return_dialog_return_qty_fmt, qtyStr),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // --- BOTTOM ROW: Price & Sold Details on Left | Stepper & All Button on Right ---
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Price & sold metrics
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            text = formatTaka(item.price),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp
                                        )
                                        Text("•", color = MaterialTheme.colorScheme.outlineVariant, fontSize = 10.sp)
                                        Text(
                                            text = "$soldStr sold",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (already > 0.0) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "$alreadyStr returned",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Stepper controls or Fully Returned chip
                                    if (isFullyReturned) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = stringResource(R.string.return_dialog_fully_returned),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.height(48.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clickable(enabled = qty > 0.0) {
                                                                quantities = quantities + (item.productBarcode to (qty - 1.0).coerceAtLeast(0.0))
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            "−",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (qty > 0.0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .defaultMinSize(minWidth = 30.dp)
                                                            .clickable {
                                                                manualInputItem = item
                                                                manualInputInitial = qty
                                                                manualInputMax = maxReturnable
                                                            }
                                                            .padding(horizontal = 3.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = qtyStr,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            textAlign = TextAlign.Center,
                                                            color = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .size(48.dp)
                                                            .clickable(enabled = qty < maxReturnable) {
                                                                quantities = quantities + (item.productBarcode to (qty + 1.0).coerceAtMost(maxReturnable))
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            "+",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (qty < maxReturnable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (qty == maxReturnable && maxReturnable > 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (qty == maxReturnable && maxReturnable > 0.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .clickable {
                                                        quantities = quantities + (item.productBarcode to maxReturnable)
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.common_all),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (qty == maxReturnable && maxReturnable > 0.0) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. REASON FOR RETURN (2x3 GRID)
                    Text(
                        text = "2. REASON FOR RETURN",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        maxItemsInEachRow = 3
                    ) {
                        REASON_CHIPS.forEach { chip ->
                            val isSelected = selectedReason == chip.reasonKey
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                shadowElevation = if (isSelected) 3.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedReason = chip.reasonKey
                                        if (chip.isDamaged) {
                                            isRestockEnabled = false
                                        } else {
                                            isRestockEnabled = true
                                        }
                                    }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    Text(chip.icon, fontSize = 14.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(chip.shortTitleRes),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 3. SMART RESTOCK BANNER
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRestockEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRestockEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(if (isRestockEnabled) "📦" else "🗑️", fontSize = 16.sp)
                                Column {
                                    Text(
                                        text = if (isRestockEnabled) stringResource(R.string.return_restock_shelf_title) else stringResource(R.string.return_mark_damaged_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = if (isRestockEnabled) Color(0xFF166534) else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = if (isRestockEnabled) stringResource(R.string.return_will_restock) else stringResource(R.string.return_will_not_restock),
                                        fontSize = 10.sp,
                                        color = if (isRestockEnabled) Color(0xFF15803D) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Switch(
                                checked = isRestockEnabled,
                                onCheckedChange = { isRestockEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }

                    // 4. AUDIT NOTE & CUSTOMIZABLE PRESET TAGS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3. AUDIT NOTE",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                            shadowElevation = 2.dp,
                            modifier = Modifier.clickable { showManagePresetsDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("⚙️", fontSize = 10.sp)
                                Text(
                                    text = stringResource(R.string.return_dialog_manage_presets),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4338CA)
                                )
                            }
                        }
                    }

                    // Horizontal Preset Tags Scroll
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        customPresets.forEach { preset ->
                            val localizedText = getLocalizedPresetText(preset)
                            val isTagActive = returnNote.contains(localizedText) || returnNote.contains(preset.text)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isTagActive) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isTagActive) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.clickable {
                                    val currentTags = returnNote.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                    val existingIdx = currentTags.indexOfFirst { it.contains(localizedText) || it.contains(preset.text) }
                                    if (existingIdx >= 0) {
                                        currentTags.removeAt(existingIdx)
                                    } else {
                                        currentTags.add("${preset.icon} $localizedText")
                                    }
                                    returnNote = currentTags.joinToString(", ")
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(preset.icon, fontSize = 11.sp)
                                    Text(
                                        text = localizedText,
                                        fontSize = 11.sp,
                                        fontWeight = if (isTagActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isTagActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showManagePresetsDialog = true }
                        ) {
                            Text(
                                text = stringResource(R.string.common_add_chip),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Note Input Field with Voice Mic
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BasicTextField(
                                value = returnNote,
                                onValueChange = { returnNote = it },
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (returnNote.isEmpty()) {
                                        Text(
                                            text = "e.g. Customer tested and found seal broken",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.return_speech_prompt))
                                    }
                                    try {
                                        voiceLauncher.launch(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Text("🎙️", fontSize = 16.sp)
                            }
                        }
                    }

                    // 5. MASTER 4-CHANNEL SETTLEMENT SELECTOR (CASH DEFAULT)
                    Text(
                        text = "4. SETTLEMENT METHOD (REFUND DESTINATION)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )

                    // 4 Tabs: CASH (Default), DUE, MFS, WALLET
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // CASH TAB (DEFAULT)
                            SettlementTabButton(
                                label = "Cash",
                                icon = "💵",
                                isSelected = selectedSettlementDestination == "CASH",
                                modifier = Modifier.weight(1f)
                            ) { selectedSettlementDestination = "CASH" }

                            // DUE TAB
                            SettlementTabButton(
                                label = stringResource(R.string.return_dialog_label_due_debt),
                                icon = "📉",
                                isSelected = selectedSettlementDestination == "DUE",
                                modifier = Modifier.weight(1f)
                            ) { selectedSettlementDestination = "DUE" }

                            // MFS TAB
                            SettlementTabButton(
                                label = stringResource(R.string.return_dialog_label_digital_mfs),
                                icon = "📱",
                                isSelected = selectedSettlementDestination == "MFS",
                                modifier = Modifier.weight(1f)
                            ) { selectedSettlementDestination = "MFS" }

                            // WALLET TAB
                            SettlementTabButton(
                                label = stringResource(R.string.return_dialog_label_store_credit),
                                icon = "🪙",
                                isSelected = selectedSettlementDestination == "WALLET",
                                modifier = Modifier.weight(1f)
                            ) { selectedSettlementDestination = "WALLET" }
                        }
                    }

                    // Destination Details Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when (selectedSettlementDestination) {
                            "DUE" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            "MFS" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (selectedSettlementDestination) {
                                "DUE" -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                "MFS" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (selectedSettlementDestination) {
                                "CASH" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.return_cash_drawer_float_title), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                stringResource(R.string.return_active_register),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        if (externalRefund == 0.0 && storeCreditRestored > 0.0) {
                                            stringResource(R.string.return_credit_restored_no_cash, formatTaka(storeCreditRestored))
                                        } else if (storeCreditRestored > 0.0) {
                                            stringResource(R.string.return_credit_restored_and_cash, formatTaka(storeCreditRestored), formatTaka(externalRefund))
                                        } else {
                                            stringResource(R.string.return_physical_cash_desc)
                                        },
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                                "DUE" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = Color(0xFFFEE2E2),
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = Color(0xFFE11D48),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = customerDisplayText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF881337),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.return_dialog_due_debt_colon),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    text = formatTaka(remainingDue),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                                        thickness = 0.8.dp
                                    )

                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .padding(top = 1.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "✓",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }

                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.return_dialog_due_deduct_notice),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                lineHeight = 15.sp
                                            )
                                            Text(
                                                text = stringResource(R.string.return_dialog_drawer_untouched_notice),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                                "MFS" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.return_dialog_select_payment_gateway), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Text(selectedMfsChannel, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        MFS_CHANNELS.forEach { channel ->
                                            val isChanActive = selectedMfsChannel == channel
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isChanActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isChanActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier.clickable { selectedMfsChannel = channel }
                                            ) {
                                                Text(
                                                    text = when (channel) {
                                                        "Nagad" -> "🟠 Nagad"
                                                        "bKash" -> "🌸 bKash"
                                                        "Rocket" -> "🚀 Rocket"
                                                        "Card" -> "💳 Card"
                                                        else -> "🏦 Bank"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isChanActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                "WALLET" -> {
                                    if (isWalkinCustomer) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("⚠️ " + stringResource(R.string.store_credit_walkin_resolution_title), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                                                ) {
                                                    Text(
                                                        stringResource(R.string.walk_in_customer),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                stringResource(R.string.store_credit_walkin_resolution_desc),
                                                fontSize = 10.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Resolution Selector Tabs
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                // Register Pill
                                                val isReg = walkinMode == "REGISTER"
                                                Surface(
                                                    onClick = { walkinMode = "REGISTER" },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isReg) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = "👤 " + stringResource(R.string.store_credit_mode_register),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = if (isReg) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isReg) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 6.dp)
                                                    )
                                                }

                                                // Voucher Pill
                                                val isVoucher = walkinMode == "VOUCHER"
                                                Surface(
                                                    onClick = { walkinMode = "VOUCHER" },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isVoucher) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = "🎟️ " + stringResource(R.string.store_credit_mode_voucher),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = if (isVoucher) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isVoucher) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            if (walkinMode == "REGISTER") {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedTextField(
                                                        value = walkinRegPhone,
                                                        onValueChange = { walkinRegPhone = it },
                                                        label = { Text(stringResource(R.string.store_credit_register_phone_hint), fontSize = 11.sp) },
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                                    )
                                                    OutlinedTextField(
                                                        value = walkinRegName,
                                                        onValueChange = { walkinRegName = it },
                                                        label = { Text(stringResource(R.string.store_credit_register_name_hint), fontSize = 11.sp) },
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.fillMaxWidth().height(52.dp)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFEEF2FF),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text("🎟️", fontSize = 18.sp)
                                                        Text(
                                                            stringResource(R.string.store_credit_voucher_notice),
                                                            fontSize = 10.sp,
                                                            color = Color(0xFF3730A3),
                                                            lineHeight = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🪙 " + stringResource(R.string.store_credit_balance), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    stringResource(R.string.return_customer_wallet),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            stringResource(R.string.return_wallet_credit_notice),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. FINANCIAL SETTLEMENT CARD (DARK GLASSMORPHIC)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.return_dialog_gross_item_total), fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                                Text(formatTaka(refundTotal), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            if (dueAdjustment > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(stringResource(R.string.return_dialog_due_debt_cleared), fontSize = 11.5.sp, color = Color(0xFFFCD34D))
                                    Text("-${formatTaka(dueAdjustment)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCD34D))
                                }
                            }

                            if (storeCreditRestored > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.return_store_credit_restored), fontSize = 11.5.sp, color = Color(0xFF60A5FA))
                                    Text("+${formatTaka(storeCreditRestored)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                                }
                            }

                            HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (dueAdjustment > 0.0 && externalRefund > 0.0) {
                                        when (selectedSettlementDestination) {
                                            "WALLET" -> stringResource(R.string.return_excess_store_credit)
                                            "MFS" -> stringResource(R.string.return_excess_mfs_channel, selectedMfsChannel)
                                            else -> stringResource(R.string.return_excess_cash_payout)
                                        }
                                    } else if (dueAdjustment > 0.0 && externalRefund == 0.0) {
                                        if (selectedSettlementDestination == "WALLET" && storeCreditRestored > 0.0) stringResource(R.string.return_net_store_credit)
                                        else stringResource(R.string.return_net_cash_payout)
                                    } else if (externalRefund == 0.0 && storeCreditRestored > 0.0) {
                                        if (selectedSettlementDestination == "WALLET") stringResource(R.string.return_store_credit_refund)
                                        else stringResource(R.string.return_store_credit_restored)
                                    } else {
                                        when (selectedSettlementDestination) {
                                            "WALLET" -> stringResource(R.string.return_store_credit_payout)
                                            "MFS" -> stringResource(R.string.return_mfs_payout_title)
                                            else -> stringResource(R.string.return_customer_refund_payout)
                                        }
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = formatTaka(if (externalRefund == 0.0 && storeCreditRestored > 0.0) storeCreditRestored else externalRefund),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    }
                }

                // --- MODAL FOOTER ---
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                    }

                    val isWalkinRegIncomplete = selectedSettlementDestination == "WALLET" && isWalkinCustomer && walkinMode == "REGISTER" && walkinRegPhone.trim().length < 6
                    var isProcessing by remember { mutableStateOf(false) }
                    val isConfirmEnabled = hasSelection && !isWalkinRegIncomplete && !isProcessing

                    Button(
                        onClick = {
                            if (isProcessing) return@Button
                            isProcessing = true
                            val lines = displayItems.mapNotNull { item ->
                                val qty = quantities[item.productBarcode] ?: 0.0
                                if (qty > 0.0) {
                                    ReturnLine(item.productBarcode, item.productName, item.price, qty)
                                } else null
                            }

                            val refundMethodStr = if (externalRefund == 0.0 && dueAdjustment > 0.0) {
                                "Due Debt Clearing"
                            } else if (externalRefund == 0.0 && storeCreditRestored > 0.0) {
                                "Store Credit"
                            } else {
                                when (selectedSettlementDestination) {
                                    "CASH" -> "Cash"
                                    "DUE" -> "Cash"
                                    "MFS" -> selectedMfsChannel
                                    "WALLET" -> "Store Credit"
                                    else -> "Cash"
                                }
                            }

                            val combinedNote = buildString {
                                append(selectedReason)
                                if (!isRestockEnabled) {
                                    append(" [Damaged Write-off]")
                                }
                                if (returnNote.isNotBlank()) {
                                    append(" - ")
                                }
                                append(returnNote.trim())
                            }

                            val (finalWalkinPhone, finalWalkinName, finalIssueVoucher) = if (selectedSettlementDestination == "WALLET" && isWalkinCustomer) {
                                if (walkinMode == "REGISTER") {
                                    Triple(walkinRegPhone.trim(), walkinRegName.trim().ifBlank { null }, false)
                                } else {
                                    Triple(null, null, true)
                                }
                            } else {
                                Triple(null, null, false)
                            }

                            onConfirm(lines, refundMethodStr, combinedNote, isRestockEnabled, finalWalkinPhone, finalWalkinName, finalIssueVoucher)
                        },
                        enabled = isConfirmEnabled,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (!hasSelection) {
                                stringResource(R.string.return_select_items)
                            } else if (externalRefund == 0.0 && dueAdjustment > 0.0) {
                                stringResource(R.string.return_clear_debt_action, formatTaka(dueAdjustment))
                            } else if (dueAdjustment > 0.0 && externalRefund > 0.0) {
                                stringResource(R.string.return_clear_debt_and_refund_action, formatTaka(dueAdjustment), formatTaka(externalRefund))
                            } else if (selectedSettlementDestination == "WALLET") {
                                if (isWalkinCustomer) {
                                    if (walkinMode == "REGISTER") {
                                        if (walkinRegPhone.trim().length < 6) stringResource(R.string.return_enter_customer_mobile)
                                        else stringResource(R.string.return_register_and_credit_action, formatTaka(refundTotal))
                                    } else {
                                        stringResource(R.string.return_issue_voucher_action, formatTaka(refundTotal))
                                    }
                                } else {
                                    stringResource(R.string.return_add_store_credit_action, formatTaka(refundTotal))
                                }
                            } else if (storeCreditRestored > 0.0) {
                                if (externalRefund == 0.0) {
                                    stringResource(R.string.return_restore_store_credit_action, formatTaka(storeCreditRestored))
                                } else {
                                    stringResource(R.string.return_refund_and_restore_action, formatTaka(externalRefund), formatTaka(storeCreditRestored))
                                }
                            } else {
                                stringResource(R.string.return_process_refund_action, formatTaka(externalRefund))
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    // --- SUB-MODAL: MANAGE & EDIT CUSTOM AUDIT PRESETS ---
    if (showManagePresetsDialog) {
        ManageAuditPresetsDialog(
            currentPresets = customPresets,
            onDismiss = { showManagePresetsDialog = false },
            onSavePresets = { updated ->
                coroutineScope.launch {
                    settingsDataStore.saveReturnAuditPresets(encodeAuditPresets(updated))
                }
                showManagePresetsDialog = false
            }
        )
    }

    // --- SUB-MODAL: DIRECT RETURN QUANTITY INPUT ---
    if (manualInputItem != null) {
        ManualReturnQuantityDialog(
            item = manualInputItem!!,
            initialQty = manualInputInitial,
            maxReturnable = manualInputMax,
            onDismiss = { manualInputItem = null },
            onConfirm = { newQty ->
                quantities = quantities + (manualInputItem!!.productBarcode to newQty)
                manualInputItem = null
            }
        )
    }
}

@Composable
private fun SettlementTabButton(
    label: String,
    icon: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color.White else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 5.dp, horizontal = 2.dp)
        ) {
            Text(icon, fontSize = 12.sp)
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ManualReturnQuantityDialog(
    item: SaleItem,
    initialQty: Double,
    maxReturnable: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var textValue by remember {
        mutableStateOf(
            if (initialQty > 0.0) {
                if (initialQty % 1.0 == 0.0) initialQty.toLong().toString()
                else String.format(java.util.Locale.US, "%.2f", initialQty).trimEnd('0').trimEnd('.')
            } else ""
        )
    }

    val maxStr = if (maxReturnable % 1.0 == 0.0) maxReturnable.toLong().toString() else String.format(java.util.Locale.US, "%.2f", maxReturnable).trimEnd('0').trimEnd('.')
    val parsedQty = textValue.toDoubleOrNull() ?: 0.0
    val isValid = parsedQty > 0.0 && parsedQty <= maxReturnable

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.return_dialog_enter_qty),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = item.productName,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                            textValue = input
                        }
                    },
                    label = { Text(stringResource(R.string.return_dialog_quantity_max_fmt, maxStr)) },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1.0, 2.0, 5.0).forEach { quickVal ->
                        if (quickVal <= maxReturnable) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val q = quickVal.coerceAtMost(maxReturnable)
                                        textValue = if (q % 1.0 == 0.0) q.toLong().toString() else String.format(java.util.Locale.US, "%.2f", q).trimEnd('0').trimEnd('.')
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${quickVal.toInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEF2FF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                textValue = maxStr
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.return_dialog_all_max_fmt, maxStr),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF1F2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                textValue = "0"
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.clear_action),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE11D48)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalQty = (textValue.toDoubleOrNull() ?: 0.0).coerceIn(0.0, maxReturnable)
                    onConfirm(finalQty)
                },
                enabled = textValue.isEmpty() || isValid || parsedQty == 0.0,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text(stringResource(R.string.return_dialog_set_quantity), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ManageAuditPresetsDialog(
    currentPresets: List<AuditPreset>,
    onDismiss: () -> Unit,
    onSavePresets: (List<AuditPreset>) -> Unit
) {
    var presetsList by remember { mutableStateOf(currentPresets) }
    var newPresetText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("⚙️", fontSize = 14.sp) }
                }
                Text(stringResource(R.string.return_dialog_manage_audit_presets), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.return_manage_presets_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presetsList.forEachIndexed { index, preset ->
                    val isEditing = editingIndex == index
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isEditing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = editingText,
                                        onValueChange = { editingText = it },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.5.sp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            if (editingText.isNotBlank()) {
                                                val updated = presetsList.toMutableList()
                                                updated[index] = updated[index].copy(text = editingText.trim())
                                                presetsList = updated
                                            }
                                            editingIndex = null
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) { Text("💾", fontSize = 14.sp) }
                                    IconButton(
                                        onClick = { editingIndex = null },
                                        modifier = Modifier.size(48.dp)
                                    ) { Text("✕", fontSize = 14.sp) }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            editingIndex = index
                                            editingText = preset.text
                                        }
                                ) {
                                    Text(preset.icon, fontSize = 13.sp)
                                    Text(preset.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingIndex = index
                                            editingText = preset.text
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) { Text("✏️", fontSize = 14.sp) }

                                    IconButton(
                                        onClick = {
                                            presetsList = presetsList.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) { Text("🗑️", fontSize = 14.sp) }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Add New Preset Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newPresetText,
                        onValueChange = { newPresetText = it },
                        placeholder = { Text(stringResource(R.string.return_audit_placeholder), fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 11.5.sp),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (newPresetText.isNotBlank()) {
                                var icon = "🏷️"
                                var text = newPresetText.trim()
                                val emojiRegex = Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}]")
                                val match = emojiRegex.find(text)
                                if (match != null) {
                                    icon = match.value
                                    text = text.substring(match.value.length).trim()
                                }
                                presetsList = presetsList + AuditPreset(
                                    id = System.currentTimeMillis().toString(),
                                    icon = icon,
                                    text = text
                                )
                                newPresetText = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.common_add_chip), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSavePresets(presetsList) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.common_save_and_close), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { presetsList = DEFAULT_AUDIT_PRESETS }
            ) {
                Text(stringResource(R.string.common_reset_defaults), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

internal fun parseAuditPresets(jsonStr: String?): List<AuditPreset> {
    if (jsonStr.isNullOrBlank()) return DEFAULT_AUDIT_PRESETS
    return try {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<AuditPreset>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                AuditPreset(
                    id = obj.optString("id", i.toString()),
                    icon = obj.optString("icon", "🏷️"),
                    text = obj.optString("text", "")
                )
            )
        }
        if (list.isNotEmpty()) list else DEFAULT_AUDIT_PRESETS
    } catch (_: Exception) {
        DEFAULT_AUDIT_PRESETS
    }
}

internal fun encodeAuditPresets(presets: List<AuditPreset>): String {
    val array = JSONArray()
    presets.forEach { preset ->
        val obj = JSONObject().apply {
            put("id", preset.id)
            put("icon", preset.icon)
            put("text", preset.text)
        }
        array.put(obj)
    }
    return array.toString()
}
