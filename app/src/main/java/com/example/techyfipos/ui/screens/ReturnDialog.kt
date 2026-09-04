package com.example.techyfipos.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.db.ReturnLine
import com.example.techyfipos.data.db.SaleWithItems
import com.example.techyfipos.ui.components.Realistic3DReturnBoxIcon
import com.example.techyfipos.ui.viewmodel.calculateSaleDueAmount
import com.example.techyfipos.utils.formatTaka
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "techyfi_return_dialog_prefs"
private const val KEY_CUSTOM_PRESETS = "custom_audit_presets_json"

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

private val REASON_CHIPS = listOf(
    Triple("💥", "Defective / Damaged", true),
    Triple("🔀", "Wrong Item", false),
    Triple("🔄", "Exchange", false),
    Triple("🙋", "Customer Request", false),
    Triple("🏷️", "Price Adjust", false),
    Triple("📝", "Other Note", false)
)

private val MFS_CHANNELS = listOf("Nagad", "bKash", "Rocket", "Card", "Bank")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReturnItemsDialog(
    saleWithItems: SaleWithItems,
    alreadyReturned: Map<String, Double>,
    onDismiss: () -> Unit,
    onConfirm: (lines: List<ReturnLine>, refundMethod: String, note: String?, restock: Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // Custom Presets State
    var customPresets by remember {
        mutableStateOf(loadAuditPresets(prefs))
    }
    var showManagePresetsDialog by remember { mutableStateOf(false) }

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
    val customerPhone = saleWithItems.sale.customerPhone

    // 4 Settlement Destinations: "CASH" (Default), "DUE", "MFS", "WALLET"
    var selectedSettlementDestination by remember {
        mutableStateOf(if (hasCustomerDue) "DUE" else "CASH")
    }
    var selectedMfsChannel by remember { mutableStateOf("Nagad") }

    var selectedReason by remember { mutableStateOf("Defective / Damaged") }
    var isRestockEnabled by remember { mutableStateOf(false) }
    var returnNote by remember { mutableStateOf("") }

    val refundTotal = displayItems.sumOf { item ->
        (quantities[item.productBarcode] ?: 0.0) * item.price * paidRatio
    }

    // Returns on sales with outstanding debt MUST reduce the customer's debt first
    val dueAdjustment = if (hasCustomerDue) {
        refundTotal.coerceAtMost(remainingDue)
    } else 0.0

    val externalRefund = (refundTotal - dueAdjustment).coerceAtLeast(0.0)

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
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                            color = Color(0xFFEEF2FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
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
                                    text = "Return Items",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFEEF2FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE))
                                ) {
                                    Text(
                                        text = "#${saleWithItems.sale.id}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (!customerPhone.isNullOrBlank()) customerPhone else "Walk-in Customer",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasCustomerDue) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color(0xFFFFF1F2),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3))
                                    ) {
                                        Text(
                                            text = "Due: ${formatTaka(remainingDue)}",
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
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
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
                            text = "1. SELECT RETURN QUANTITIES",
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

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) Color(0xFFFAFAFF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (isActive) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📦", fontSize = 16.sp)
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.productName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = formatTaka(item.price),
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4F46E5),
                                                fontSize = 11.sp
                                            )
                                            Text("•", color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                                            Text(
                                                text = if (already > 0.0) "$soldStr sold ($alreadyStr returned)" else "$soldStr sold",
                                                fontSize = 11.sp,
                                                color = if (already > 0.0) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (isFullyReturned) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "Fully Returned",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clickable(enabled = qty > 0.0) {
                                                            quantities = quantities + (item.productBarcode to (qty - 1.0).coerceAtLeast(0.0))
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("−", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                                }

                                                Text(
                                                    text = if (qty <= 0.0) "0" else if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(java.util.Locale.US, "%.2f", qty).trimEnd('0').trimEnd('.'),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.width(28.dp)
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clickable(enabled = qty < maxReturnable) {
                                                            quantities = quantities + (item.productBarcode to (qty + 1.0).coerceAtMost(maxReturnable))
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFEEF2FF),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
                                            modifier = Modifier.clickable {
                                                quantities = quantities + (item.productBarcode to maxReturnable)
                                            }
                                        ) {
                                            Text(
                                                text = "All",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF4F46E5),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
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
                        REASON_CHIPS.forEach { (icon, reasonTitle, isDamaged) ->
                            val isSelected = selectedReason == reasonTitle
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                shadowElevation = if (isSelected) 3.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedReason = reasonTitle
                                        if (isDamaged) {
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
                                    Text(icon, fontSize = 14.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = reasonTitle.substringBefore(" /"),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF4338CA) else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 3. SMART RESTOCK BANNER
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isRestockEnabled) Color(0xFFF0FDF4) else Color(0xFFFFF1F2),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRestockEnabled) Color(0xFFBBF7D0) else Color(0xFFFECDD3)
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
                                        text = if (isRestockEnabled) "Restock item(s) to shelf inventory" else "Mark as Damaged / Write-off",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = if (isRestockEnabled) Color(0xFF166534) else Color(0xFF9F1239)
                                    )
                                    Text(
                                        text = if (isRestockEnabled) "Will increment stock count automatically" else "Will NOT add defective item back into shelf stock",
                                        fontSize = 10.sp,
                                        color = if (isRestockEnabled) Color(0xFF15803D) else Color(0xFFBE123C)
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
                            color = Color(0xFFEEF2FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
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
                                    text = "Manage Presets",
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
                            val isTagActive = returnNote.contains(preset.text)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isTagActive) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isTagActive) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.clickable {
                                    val currentTags = returnNote.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                    val existingIdx = currentTags.indexOfFirst { it.contains(preset.text) }
                                    if (existingIdx >= 0) {
                                        currentTags.removeAt(existingIdx)
                                    } else {
                                        currentTags.add("${preset.icon} ${preset.text}")
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
                                        text = preset.text,
                                        fontSize = 11.sp,
                                        fontWeight = if (isTagActive) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isTagActive) Color(0xFF4338CA) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEEF2FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5B4FC)),
                            modifier = Modifier.clickable { showManagePresetsDialog = true }
                        ) {
                            Text(
                                text = "+ Add",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
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
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak return reason or note...")
                                    }
                                    try {
                                        voiceLauncher.launch(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text("🎙️", fontSize = 13.sp)
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
                                label = "Due Debt",
                                icon = "📉",
                                isSelected = selectedSettlementDestination == "DUE",
                                modifier = Modifier.weight(1f)
                            ) { selectedSettlementDestination = "DUE" }

                            // MFS TAB
                            SettlementTabButton(
                                label = "Digital / MFS",
                                icon = "📱",
                                isSelected = selectedSettlementDestination == "MFS",
                                modifier = Modifier.weight(1f)
                            ) { selectedSettlementDestination = "MFS" }

                            // WALLET TAB
                            SettlementTabButton(
                                label = "Store Credit",
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
                            "DUE" -> Color(0xFFFFF1F2)
                            "MFS" -> Color(0xFFEEF2FF)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when (selectedSettlementDestination) {
                                "DUE" -> Color(0xFFFDA4AF)
                                "MFS" -> Color(0xFFC7D2FE)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when (selectedSettlementDestination) {
                                "CASH" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("💵 Cash Drawer Float", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Active Register", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                                    }
                                    Text(
                                        "Physical cash will be handed over to customer from till drawer.",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                "DUE" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("👤 ${if (!customerPhone.isNullOrBlank()) customerPhone else "Customer"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color(0xFFFFF1F2),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3))
                                        ) {
                                            Text(
                                                "Due Debt: ${formatTaka(remainingDue)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFE11D48),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        "✓ Return will deduct from customer's due ledger (retaining register cash)",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFFBE123C),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                "MFS" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Select Payment Gateway:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                                        Text(selectedMfsChannel, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4F46E5))
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
                                                color = if (isChanActive) Color(0xFF4F46E5) else Color.White,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isChanActive) Color(0xFF4F46E5) else Color(0xFFCBD5E1)),
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
                                                    color = if (isChanActive) Color.White else Color(0xFF334155),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                "WALLET" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🪙 Store Credit Balance", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Customer Wallet", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD97706))
                                    }
                                    Text(
                                        "Refund amount will be added to customer's account for their next purchase.",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                Text("Gross Item Total", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                                Text(formatTaka(refundTotal), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            if (dueAdjustment > 0.0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Due Debt Cleared", fontSize = 11.5.sp, color = Color(0xFFFCD34D))
                                    Text("-${formatTaka(dueAdjustment)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCD34D))
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
                                            "WALLET" -> "Excess Store Credit"
                                            "MFS" -> "Excess ($selectedMfsChannel)"
                                            else -> "Excess Cash Payout"
                                        }
                                    } else if (dueAdjustment > 0.0 && externalRefund == 0.0) {
                                        "Net Cash Payout"
                                    } else {
                                        when (selectedSettlementDestination) {
                                            "WALLET" -> "Store Credit Payout"
                                            "MFS" -> "Digital / MFS Payout"
                                            else -> "Customer Refund Payout"
                                        }
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = formatTaka(externalRefund),
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
                            .height(44.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val lines = displayItems.mapNotNull { item ->
                                val qty = quantities[item.productBarcode] ?: 0.0
                                if (qty > 0.0) {
                                    ReturnLine(item.productBarcode, item.productName, item.price, qty)
                                } else null
                            }

                            val refundMethodStr = if (externalRefund == 0.0 && dueAdjustment > 0.0) {
                                "Due Debt Clearing"
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
                                    append(returnNote.trim())
                                }
                            }

                            onConfirm(lines, refundMethodStr, combinedNote, isRestockEnabled)
                        },
                        enabled = hasSelection,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        ),
                        modifier = Modifier
                            .weight(2f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = if (!hasSelection) {
                                "Select Items to Return"
                            } else if (externalRefund == 0.0 && dueAdjustment > 0.0) {
                                "Clear ${formatTaka(dueAdjustment)} Customer Debt"
                            } else if (dueAdjustment > 0.0 && externalRefund > 0.0) {
                                "Clear ${formatTaka(dueAdjustment)} Debt + Refund ${formatTaka(externalRefund)}"
                            } else if (selectedSettlementDestination == "WALLET") {
                                "Add ${formatTaka(refundTotal)} to Store Credit"
                            } else {
                                "Process ${formatTaka(externalRefund)} Return"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color.White
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
                customPresets = updated
                saveAuditPresets(prefs, updated)
                showManagePresetsDialog = false
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
            modifier = Modifier.padding(vertical = 5.dp)
        ) {
            Text(icon, fontSize = 12.sp)
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF64748B)
            )
        }
    }
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEF2FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("⚙️", fontSize = 14.sp) }
                }
                Text("Manage Audit Presets", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
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
                    "Tap ✏️ to rename or 🗑️ to delete custom preset notes:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presetsList.forEachIndexed { index, preset ->
                    val isEditing = editingIndex == index
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isEditing) Color(0xFFFAFAFF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isEditing) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                                        modifier = Modifier.size(28.dp)
                                    ) { Text("💾", fontSize = 12.sp) }
                                    IconButton(
                                        onClick = { editingIndex = null },
                                        modifier = Modifier.size(28.dp)
                                    ) { Text("✕", fontSize = 12.sp) }
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
                                        modifier = Modifier.size(28.dp)
                                    ) { Text("✏️", fontSize = 12.sp) }

                                    IconButton(
                                        onClick = {
                                            presetsList = presetsList.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) { Text("🗑️", fontSize = 12.sp) }
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
                        placeholder = { Text("e.g. 🏷️ Wrong Barcode", fontSize = 11.sp) },
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("+ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSavePresets(presetsList) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
            ) {
                Text("Save & Close", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { presetsList = DEFAULT_AUDIT_PRESETS }
            ) {
                Text("Reset Defaults", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private fun loadAuditPresets(prefs: android.content.SharedPreferences): List<AuditPreset> {
    val jsonStr = prefs.getString(KEY_CUSTOM_PRESETS, null) ?: return DEFAULT_AUDIT_PRESETS
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

private fun saveAuditPresets(prefs: android.content.SharedPreferences, presets: List<AuditPreset>) {
    try {
        val array = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("icon", preset.icon)
                put("text", preset.text)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, array.toString()).apply()
    } catch (_: Exception) {}
}
