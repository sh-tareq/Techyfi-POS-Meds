package com.techyfiposmeds.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.data.db.MedicineBatch
import com.techyfiposmeds.utils.formatTaka
import com.techyfiposmeds.utils.isAppBangla
import com.techyfiposmeds.utils.toEnglishDigits
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MedicineBatchesDialog(
    medicineName: String,
    genericName: String?,
    barcode: String,
    stripSize: Int = 10,
    mrpPrice: Double = 0.0,
    shelfLocation: String? = null,
    batches: List<MedicineBatch>,
    onDismiss: () -> Unit,
    onAddBatch: (MedicineBatch) -> Unit,
    onUpdateBatch: (MedicineBatch) -> Unit = {},
    onUpdateShelfLocation: ((String?) -> Unit)? = null,
    onQuarantineBatch: (batchId: Long, reason: String) -> Unit,
    onReleaseBatch: (batchId: Long) -> Unit,
    onDeleteBatch: (batchId: Long) -> Unit
) {
    val isBn = isAppBangla()
    var showAddForm by remember { mutableStateOf(false) }
    var newBatchNumber by remember { mutableStateOf("BX-${(System.currentTimeMillis() % 100000).toString().padStart(5, '0')}") }
    var newExpiryDate by remember { mutableStateOf("2027-12-31") }
    var newQuantityText by remember { mutableStateOf("") }
    var newUnitCostText by remember { mutableStateOf("") }
    var newUnitType by remember { mutableStateOf("strip") } // "pc", "strip"

    var editingBatch by remember { mutableStateOf<MedicineBatch?>(null) }
    var showEditRackDialog by remember { mutableStateOf(false) }
    var currentRack by remember(shelfLocation) { mutableStateOf(shelfLocation.orEmpty()) }

    var quarantineTargetBatch by remember { mutableStateOf<MedicineBatch?>(null) }
    var quarantineReason by remember { mutableStateOf(if (isBn) "মেয়াদোত্তীর্ণ" else "Expired") }

    val sortedBatches = remember(batches) {
        batches.sortedWith(
            compareBy<MedicineBatch> { it.isQuarantined }
                .thenBy { it.expiryDate }
        )
    }

    val earliestActiveId = remember(batches) {
        batches.filter { !it.isQuarantined && it.stockQty > 0 }
            .minByOrNull { it.expiryDate }?.id
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🏷️", fontSize = 20.sp)
                            }
                        }
                        Column {
                            Text(
                                medicineName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!genericName.isNullOrBlank()) {
                                Text(
                                    genericName,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Shelf Location Banner
                Surface(
                    onClick = { if (onUpdateShelfLocation != null) showEditRackDialog = true },
                    enabled = onUpdateShelfLocation != null,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.08f),
                    border = BorderStroke(0.8.dp, Color(0xFF0284C7).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📍", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = if (isBn) "র্যাক / শেলফ লোকেশন" else "Rack / Shelf Location",
                                    fontSize = 10.sp,
                                    color = Color(0xFF0369A1).copy(alpha = 0.7f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (currentRack.isBlank()) (if (isBn) "নির্ধারিত নেই (ট্যাপ করে যুক্ত করুন)" else "Not Set (Tap to set)") else currentRack,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0369A1)
                                )
                            }
                        }
                        if (onUpdateShelfLocation != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, Color(0xFF0284C7).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF0369A1))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isBn) "র্যাক পরিবর্তন" else "Change Rack", fontSize = 11.5.sp, color = Color(0xFF0369A1), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Summary Stats Banner
                val totalActiveStock = batches.filter { !it.isQuarantined }.sumOf { it.stockQty }
                val totalQuarantinedStock = batches.filter { it.isQuarantined }.sumOf { it.stockQty }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isBn) "মোট ব্যাচ সংখ্যা" else "Total Batches", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(if (isBn) "${batches.size} টি" else "${batches.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isBn) "সক্রিয় বিক্রয়যোগ্য স্টক" else "Active Sellable Stock", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "${if (totalActiveStock % 1.0 == 0.0) totalActiveStock.toLong().toString() else "%.1f".format(totalActiveStock)} ${if (isBn) "পিস" else "Pcs"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                        }
                        if (totalQuarantinedStock > 0) {
                            HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (isBn) "কোয়ারেন্টাইন স্টক" else "Quarantine Stock", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "${if (totalQuarantinedStock % 1.0 == 0.0) totalQuarantinedStock.toLong().toString() else "%.1f".format(totalQuarantinedStock)} ${if (isBn) "পিস" else "Pcs"}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Add Batch Toggle Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "সকল সক্রিয় ও হিস্টোরিক্যাল ব্যাচ (FEFO)" else "All Batches (FEFO Dispensing)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { showAddForm = !showAddForm }) {
                        Icon(if (showAddForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showAddForm) (if (isBn) "ফর্ম বন্ধ করুন" else "Close Form") else (if (isBn) "নতুন ব্যাচ যোগ" else "Add Batch"), fontSize = 12.sp)
                    }
                }

                // Add Batch Expandable Form
                AnimatedVisibility(visible = showAddForm) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newBatchNumber,
                                    onValueChange = { newBatchNumber = it },
                                    label = { Text(if (isBn) "ব্যাচ নং" else "Batch No") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newExpiryDate,
                                    onValueChange = { newExpiryDate = it },
                                    label = { Text(if (isBn) "মেয়াদ (YYYY-MM-DD)" else "Expiry (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = newUnitType == "strip",
                                        onClick = { newUnitType = "strip" },
                                        label = { Text(if (isBn) "পাতা" else "Strip", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = newUnitType == "pc",
                                        onClick = { newUnitType = "pc" },
                                        label = { Text(if (isBn) "পিস" else "Pcs", fontSize = 11.sp) }
                                    )
                                }
                                OutlinedTextField(
                                    value = newQuantityText,
                                    onValueChange = { newQuantityText = it },
                                    label = { Text(if (isBn) "পরিমাণ (${if (newUnitType == "strip") "পাতা" else "পিস"})" else "Quantity (${if (newUnitType == "strip") "Strips" else "Pcs"})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newUnitCostText,
                                    onValueChange = { newUnitCostText = it },
                                    label = { Text(if (isBn) "টিপি / ক্রয়মূল্য" else "Cost Price (TP)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = {
                                    val rawQty = toEnglishDigits(newQuantityText).toDoubleOrNull() ?: 0.0
                                    val unitCost = toEnglishDigits(newUnitCostText).toDoubleOrNull() ?: 0.0
                                    if (newBatchNumber.isNotBlank() && rawQty > 0) {
                                        val baseQty = if (newUnitType == "strip") rawQty * (if (stripSize > 0) stripSize else 10) else rawQty
                                        onAddBatch(
                                            MedicineBatch(
                                                productBarcode = barcode,
                                                batchNumber = newBatchNumber.trim(),
                                                expiryDate = newExpiryDate.trim(),
                                                stockQty = baseQty,
                                                purchasePrice = unitCost,
                                                mrpPrice = mrpPrice
                                            )
                                        )
                                        showAddForm = false
                                        newQuantityText = ""
                                        newBatchNumber = "BX-${(System.currentTimeMillis() % 100000).toString().padStart(5, '0')}"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isBn) "ব্যাচ সংরক্ষণ করুন" else "Save Batch", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Batches List
                if (sortedBatches.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📦", fontSize = 32.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(if (isBn) "কোন ব্যাচ তথ্য পাওয়া যায়নি" else "No batches found", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(if (isBn) "উপরে '+ নতুন ব্যাচ যোগ' বাটনে চাপ দিয়ে প্রথম ব্যাচটি যুক্ত করুন।" else "Tap '+ Add Batch' above to add the first batch.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedBatches, key = { it.id }) { batch ->
                            val isFefo = batch.id == earliestActiveId
                            val isQuarantined = batch.isQuarantined
                            val isExpired = com.techyfiposmeds.utils.parseExpiryDateToMillis(batch.expiryDate)?.let { it < System.currentTimeMillis() } == true

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isQuarantined -> Color(0xFFFEE2E2).copy(alpha = 0.5f)
                                    isExpired -> Color(0xFFFEF3C7).copy(alpha = 0.5f)
                                    isFefo -> Color(0xFFDCFCE7).copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = BorderStroke(
                                    width = if (isFefo) 1.5.dp else 0.8.dp,
                                    color = when {
                                        isQuarantined -> Color(0xFFEF4444)
                                        isExpired -> Color(0xFFF59E0B)
                                        isFefo -> Color(0xFF10B981)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("🏷️ ${batch.batchNumber}", fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                                            if (isFefo) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF10B981)) {
                                                    Text(if (isBn) "FEFO সক্রিয়" else "FEFO ACTIVE", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                                }
                                            }
                                            if (isQuarantined) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEF4444)) {
                                                    Text(if (isBn) "কোয়ারেন্টাইন" else "QUARANTINE", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                                }
                                            }
                                            if (isExpired && !isQuarantined) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF59E0B)) {
                                                    Text(if (isBn) "মেয়াদোত্তীর্ণ" else "EXPIRED", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // Edit Batch Button
                                            OutlinedButton(
                                                onClick = { editingBatch = batch },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text(if (isBn) "সম্পাদনা" else "Edit", fontSize = 11.sp)
                                            }

                                            if (isQuarantined) {
                                                FilledTonalButton(
                                                    onClick = { onReleaseBatch(batch.id) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text(if (isBn) "মুক্ত করুন" else "Release", fontSize = 11.sp)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { quarantineTargetBatch = batch },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Text(if (isBn) "কোয়ারেন্টাইন" else "Quarantine", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${if (isBn) "⏳ মেয়াদ:" else "⏳ Expiry:"} ${batch.expiryDate}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isExpired) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val strips = if (stripSize > 0) (batch.stockQty / stripSize).toInt() else 0
                                        val stripsLabel = if (strips > 0) " (${if (isBn) "$strips পাতা" else "$strips strips"})" else ""
                                        Text(
                                            text = "${if (isBn) "স্টক:" else "Stock:"} ${if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toLong().toString() else "%.1f".format(batch.stockQty)} ${if (isBn) "পিস" else "pcs"}$stripsLabel",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (batch.stockQty > 0) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }

                                    if (batch.purchasePrice > 0.0 || batch.mrpPrice > 0.0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${if (isBn) "টিপি" else "Cost"}: ${formatTaka(batch.purchasePrice, isBangla = isBn)} · ${if (isBn) "এমআরপি" else "MRP"}: ${formatTaka(batch.mrpPrice, isBangla = isBn)}",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    if (isQuarantined && !batch.quarantineReason.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "🚫 ${if (isBn) "কারণ:" else "Reason:"} ${batch.quarantineReason}",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Existing Batch Dialog
    editingBatch?.let { batch ->
        var bNum by remember { mutableStateOf(batch.batchNumber) }
        var bExp by remember { mutableStateOf(batch.expiryDate) }
        var bUnitType by remember { mutableStateOf(if (stripSize > 1) "strip" else "pc") }
        var bQtyText by remember {
            mutableStateOf(
                if (bUnitType == "strip" && stripSize > 1) {
                    if (batch.stockQty % stripSize == 0.0) (batch.stockQty / stripSize).toInt().toString()
                    else "%.1f".format(Locale.US, batch.stockQty / stripSize).trimEnd('0').trimEnd('.')
                } else {
                    if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toInt().toString()
                    else "%.1f".format(Locale.US, batch.stockQty).trimEnd('0').trimEnd('.')
                }
            )
        }
        var bCostText by remember { mutableStateOf(if (batch.purchasePrice > 0) batch.purchasePrice.toString() else "") }
        var bMrpText by remember { mutableStateOf(if (batch.mrpPrice > 0) batch.mrpPrice.toString() else (if (mrpPrice > 0) mrpPrice.toString() else "")) }

        AlertDialog(
            onDismissRequest = { editingBatch = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✏️", fontSize = 18.sp)
                    Text(if (isBn) "ব্যাচ তথ্য আপডেট (${batch.batchNumber})" else "Update Batch (${batch.batchNumber})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bNum,
                            onValueChange = { bNum = it },
                            label = { Text(if (isBn) "ব্যাচ নং" else "Batch No") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bExp,
                            onValueChange = { bExp = it },
                            label = { Text(if (isBn) "মেয়াদ (YYYY-MM-DD)" else "Expiry (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Quick Expiry Shortcuts
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isBn) "দ্রুত মেয়াদ:" else "Quick Expiry:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        listOf(Pair(6, "+6M"), Pair(12, "+1Y"), Pair(24, "+2Y"), Pair(36, "+3Y")).forEach { (m, lbl) ->
                            Surface(
                                onClick = {
                                    val c = Calendar.getInstance()
                                    c.add(Calendar.MONTH, m)
                                    bExp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                border = BorderStroke(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                            ) {
                                Text(lbl, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = bUnitType == "strip",
                                onClick = { bUnitType = "strip" },
                                label = { Text(if (isBn) "পাতা" else "Strip", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = bUnitType == "pc",
                                onClick = { bUnitType = "pc" },
                                label = { Text(if (isBn) "পিস" else "Pcs", fontSize = 11.sp) }
                            )
                        }
                        OutlinedTextField(
                            value = bQtyText,
                            onValueChange = { bQtyText = it },
                            label = { Text(if (isBn) "মজুদ (${if (bUnitType == "strip") "পাতা" else "পিস"})" else "Stock (${if (bUnitType == "strip") "Strip" else "Pc"})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bCostText,
                            onValueChange = { bCostText = it },
                            label = { Text(if (isBn) "টিপি / ক্রয়মূল্য" else "Cost Price (TP)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bMrpText,
                            onValueChange = { bMrpText = it },
                            label = { Text(if (isBn) "এমআরপি মূল্য" else "MRP Price") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rawQty = toEnglishDigits(bQtyText).toDoubleOrNull() ?: 0.0
                        val baseQty = if (bUnitType == "strip") rawQty * (if (stripSize > 0) stripSize else 10) else rawQty
                        val cost = toEnglishDigits(bCostText).toDoubleOrNull() ?: 0.0
                        val mrp = toEnglishDigits(bMrpText).toDoubleOrNull() ?: mrpPrice

                        val updated = batch.copy(
                            batchNumber = bNum.trim(),
                            expiryDate = bExp.trim(),
                            stockQty = baseQty,
                            purchasePrice = cost,
                            mrpPrice = mrp
                        )
                        onUpdateBatch(updated)
                        editingBatch = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                ) {
                    Text(if (isBn) "আপডেট করুন" else "Update Batch", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBatch = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Edit Rack / Shelf Location Sub-Dialog
    if (showEditRackDialog) {
        var rackInput by remember { mutableStateOf(currentRack) }
        AlertDialog(
            onDismissRequest = { showEditRackDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📍", fontSize = 18.sp)
                    Text(if (isBn) "র্যাক / শেলফ নম্বর পরিবর্তন" else "Update Rack / Shelf Location", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isBn) "$medicineName এর কাউন্টার বা স্টোরেজ র্যাক নম্বর দিন:" else "Set shelf location for $medicineName:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rackInput,
                        onValueChange = { rackInput = it },
                        label = { Text(if (isBn) "র্যাক নম্বর (যেমন: Rack A-1, ফ্রিজ)" else "Rack / Shelf Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        currentRack = rackInput.trim()
                        onUpdateShelfLocation?.invoke(currentRack.ifBlank { null })
                        showEditRackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text(if (isBn) "সংরক্ষণ করুন" else "Save Location", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditRackDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Quarantine Reason Dialog
    quarantineTargetBatch?.let { batch ->
        AlertDialog(
            onDismissRequest = { quarantineTargetBatch = null },
            title = { Text(if (isBn) "ব্যাচ কোয়ারেন্টাইন নিশ্চিতকরণ" else "Confirm Batch Quarantine", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isBn) "ব্যাচ ${batch.batchNumber} (${if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toLong().toString() else "%.1f".format(batch.stockQty)} পিস) কোয়ারেন্টাইন করা হলে এটি কাউন্টারে বিক্রি থেকে ব্লক হয়ে যাবে।" else "Batch ${batch.batchNumber} will be quarantined and blocked from point-of-sale checkout counter.",
                        fontSize = 12.5.sp
                    )
                    Text(if (isBn) "কোয়ারেন্টাইনের কারণ নির্বাচন করুন:" else "Select reason for batch quarantine:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    (if (isBn) listOf("মেয়াদোত্তীর্ণ", "DGDA প্রত্যাহারের নির্দেশ", "ক্ষতিগ্রস্ত/ভাঙা অ্যাম্পুল", "কোম্পানি ফেরত পেন্ডিং") else listOf("Expired", "DGDA Recall Order", "Damaged / Broken Ampoule", "Return to Company Pending")).forEach { reason ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = quarantineReason == reason,
                                onClick = { quarantineReason = reason }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(reason, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onQuarantineBatch(batch.id, quarantineReason)
                        quarantineTargetBatch = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(if (isBn) "কোয়ারেন্টাইন করুন" else "Quarantine", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { quarantineTargetBatch = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
