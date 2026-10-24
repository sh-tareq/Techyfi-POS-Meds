package com.techyfiposmeds.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.util.Locale

@Composable
fun MedicineBatchesDialog(
    medicineName: String,
    genericName: String?,
    barcode: String,
    stripSize: Int = 10,
    mrpPrice: Double = 0.0,
    batches: List<MedicineBatch>,
    onDismiss: () -> Unit,
    onAddBatch: (MedicineBatch) -> Unit,
    onQuarantineBatch: (batchId: Long, reason: String) -> Unit,
    onReleaseBatch: (batchId: Long) -> Unit,
    onDeleteBatch: (batchId: Long) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var newBatchNumber by remember { mutableStateOf("BX-${(System.currentTimeMillis() % 100000).toString().padStart(5, '0')}") }
    var newExpiryDate by remember { mutableStateOf("2027-12-31") }
    var newQuantityText by remember { mutableStateOf("") }
    var newUnitCostText by remember { mutableStateOf("") }
    var newUnitType by remember { mutableStateOf("strip") } // "pc", "strip"

    var quarantineTargetBatch by remember { mutableStateOf<MedicineBatch?>(null) }
    var quarantineReason by remember { mutableStateOf("Expired") }

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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
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
                                text = medicineName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!genericName.isNullOrBlank()) {
                                Text(
                                    text = genericName,
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

                Spacer(Modifier.height(12.dp))

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
                            Text("মোট ব্যাচ সংখ্যা", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text("${batches.size} টি", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("সক্রিয় বিক্রয়যোগ্য স্টক", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "${if (totalActiveStock % 1.0 == 0.0) totalActiveStock.toLong().toString() else "%.1f".format(totalActiveStock)} পিস",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                        }
                        if (totalQuarantinedStock > 0) {
                            Divider(modifier = Modifier.height(24.dp).width(1.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("কোয়ারেন্টাইন স্টক", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Text(
                                    text = "${if (totalQuarantinedStock % 1.0 == 0.0) totalQuarantinedStock.toLong().toString() else "%.1f".format(totalQuarantinedStock)} পিস",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Add Batch Toggle Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "সকল সক্রিয় ও হিস্টোরিক্যাল ব্যাচ (FEFO)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = { showAddForm = !showAddForm }) {
                        Icon(if (showAddForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (showAddForm) "ফর্ম বন্ধ করুন" else "নতুন ব্যাচ যোগ", fontSize = 12.sp)
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
                                    label = { Text("ব্যাচ নং") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newExpiryDate,
                                    onValueChange = { newExpiryDate = it },
                                    label = { Text("মেয়াদ (YYYY-MM-DD)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = newUnitType == "strip",
                                        onClick = { newUnitType = "strip" },
                                        label = { Text("পাতা", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = newUnitType == "pc",
                                        onClick = { newUnitType = "pc" },
                                        label = { Text("পিস", fontSize = 11.sp) }
                                    )
                                }
                                OutlinedTextField(
                                    value = newQuantityText,
                                    onValueChange = { newQuantityText = it },
                                    label = { Text("পরিমাণ (${if (newUnitType == "strip") "পাতা" else "পিস"})") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newUnitCostText,
                                    onValueChange = { newUnitCostText = it },
                                    label = { Text("টিপি / ক্রয়মূল্য") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = {
                                    val rawQty = newQuantityText.toDoubleOrNull() ?: 0.0
                                    val unitCost = newUnitCostText.toDoubleOrNull() ?: 0.0
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
                                Text("ব্যাচ সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
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
                            Text("কোন ব্যাচ তথ্য পাওয়া যায়নি", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("উপরে '+ নতুন ব্যাচ যোগ' বাটনে চাপ দিয়ে প্রথম ব্যাচটি যুক্ত করুন।", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
                                                    Text("FEFO সক্রিয়", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                                }
                                            }
                                            if (isQuarantined) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEF4444)) {
                                                    Text("কোয়ারেন্টাইন", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                                }
                                            }
                                            if (isExpired && !isQuarantined) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFF59E0B)) {
                                                    Text("মেয়াদোত্তীর্ণ", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (isQuarantined) {
                                                FilledTonalButton(
                                                    onClick = { onReleaseBatch(batch.id) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("মুক্ত করুন", fontSize = 11.sp)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { quarantineTargetBatch = batch },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                                                ) {
                                                    Text("কোয়ারেন্টাইন", fontSize = 11.sp)
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
                                            text = "⏳ মেয়াদ: ${batch.expiryDate}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isExpired) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val strips = if (stripSize > 0) (batch.stockQty / stripSize).toInt() else 0
                                        Text(
                                            text = "স্টক: ${if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toLong().toString() else "%.1f".format(batch.stockQty)} পিস ${if (strips > 0) "($strips পাতা)" else ""}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (batch.stockQty > 0) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }

                                    if (batch.purchasePrice > 0.0 || batch.mrpPrice > 0.0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "টিপি: ৳${formatTaka(batch.purchasePrice)} · এমআরপি: ৳${formatTaka(batch.mrpPrice)}",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    if (isQuarantined && !batch.quarantineReason.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "🚫 কারণ: ${batch.quarantineReason}",
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

    // Quarantine Reason Dialog
    quarantineTargetBatch?.let { batch ->
        AlertDialog(
            onDismissRequest = { quarantineTargetBatch = null },
            title = { Text("ব্যাচ কোয়ারেন্টাইন নিশ্চিতকরণ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ব্যাচ ${batch.batchNumber} (${if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toLong().toString() else "%.1f".format(batch.stockQty)} পিস) কোয়ারেন্টাইন করা হলে এটি কাউন্টারে বিক্রি থেকে ব্লক হয়ে যাবে।",
                        fontSize = 12.5.sp
                    )
                    Text("কোয়ারেন্টাইনের কারণ নির্বাচন করুন:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    listOf("মেয়াদোত্তীর্ণ", "DGDA প্রত্যাহারের নির্দেশ", "ক্ষতিগ্রস্ত/ভাঙা অ্যাম্পুল", "কোম্পানি ফেরত পেন্ডিং").forEach { reason ->
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
                    Text("কোয়ারেন্টাইন করুন", fontWeight = FontWeight.Bold)
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
