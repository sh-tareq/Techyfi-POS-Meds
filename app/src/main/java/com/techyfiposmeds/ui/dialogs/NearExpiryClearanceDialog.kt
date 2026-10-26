package com.techyfiposmeds.ui.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.data.db.MedicineBatch
import com.techyfiposmeds.data.db.Product
import java.text.SimpleDateFormat
import java.util.*

private enum class ExpiryBucket {
    ALL,
    CRITICAL_30,
    URGENT_60,
    WARNING_90
}

data class NearExpiryItem(
    val batch: MedicineBatch,
    val product: Product?,
    val daysRemaining: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearExpiryClearanceDialog(
    batches: List<MedicineBatch>,
    products: List<Product>,
    onDismiss: () -> Unit,
    onMoveToQuarantine: ((Long, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val productMap = remember(products) { products.associateBy { it.barcode } }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayMillis = remember { System.currentTimeMillis() }

    val allNearExpiryItems = remember(batches, productMap) {
        batches
            .filter { !it.isQuarantined && it.stockQty > 0 }
            .mapNotNull { batch ->
                val expDate = try {
                    dateFormat.parse(batch.expiryDate)
                } catch (e: Exception) {
                    null
                }
                val days = if (expDate != null) {
                    (expDate.time - todayMillis) / (1000 * 60 * 60 * 24)
                } else {
                    999L
                }
                if (days <= 90) {
                    NearExpiryItem(
                        batch = batch,
                        product = productMap[batch.productBarcode],
                        daysRemaining = days
                    )
                } else null
            }
            .sortedBy { it.daysRemaining }
    }

    var selectedBucket by remember { mutableStateOf(ExpiryBucket.ALL) }

    val filteredItems = remember(allNearExpiryItems, selectedBucket) {
        when (selectedBucket) {
            ExpiryBucket.ALL -> allNearExpiryItems
            ExpiryBucket.CRITICAL_30 -> allNearExpiryItems.filter { it.daysRemaining <= 30 }
            ExpiryBucket.URGENT_60 -> allNearExpiryItems.filter { it.daysRemaining in 31..60 }
            ExpiryBucket.WARNING_90 -> allNearExpiryItems.filter { it.daysRemaining in 61..90 }
        }
    }

    val totalValueAtRisk = remember(filteredItems) {
        filteredItems.sumOf { it.batch.stockQty * (it.product?.mrpPrice ?: it.batch.mrpPrice ?: 0.0) }
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Near-Expiry Liquidation & Clearance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "আসন্ন মেয়াদোত্তীর্ণ ওষুধ ব্যবস্থাপনা ও কোম্পানি ফেরত তালিকা",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD97706),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Financial Risk Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "মোট ঝুঁকিপূর্ণ স্টকের মূল্য:",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "৳ ${String.format(Locale.US, "%,.2f", totalValueAtRisk)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFD97706).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${filteredItems.size}টি ব্যাচ তালিকাভুক্ত",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedBucket == ExpiryBucket.ALL,
                        onClick = { selectedBucket = ExpiryBucket.ALL },
                        label = { Text("সব (< ৯০ দিন)", fontSize = 11.sp) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    FilterChip(
                        selected = selectedBucket == ExpiryBucket.CRITICAL_30,
                        onClick = { selectedBucket = ExpiryBucket.CRITICAL_30 },
                        label = { Text("🚨 < ৩০ দিন", fontSize = 11.sp, color = if (selectedBucket == ExpiryBucket.CRITICAL_30) Color.White else Color(0xFFDC2626)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    FilterChip(
                        selected = selectedBucket == ExpiryBucket.URGENT_60,
                        onClick = { selectedBucket = ExpiryBucket.URGENT_60 },
                        label = { Text("⚠️ ৩১-৬০ দিন", fontSize = 11.sp) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                    FilterChip(
                        selected = selectedBucket == ExpiryBucket.WARNING_90,
                        onClick = { selectedBucket = ExpiryBucket.WARNING_90 },
                        label = { Text("⏳ ৬১-৯০ দিন", fontSize = 11.sp) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Items List
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "এই ক্যাটাগরিতে কোনো মেয়াদোত্তীর্ণ বা ঝুঁকিপূর্ণ ব্যাচ নেই!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredItems, key = { it.batch.id }) { item ->
                            NearExpiryCard(
                                item = item,
                                onMoveToQuarantine = onMoveToQuarantine
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            shareSupplierReturnChallan(context, filteredItems)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("কোম্পানি ফেরত তালিকা তৈরি করুন (Share Challan)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearExpiryCard(
    item: NearExpiryItem,
    onMoveToQuarantine: ((Long, String) -> Unit)?
) {
    val (statusBg, statusText, statusBadge) = when {
        item.daysRemaining <= 0 -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "মেয়াদোত্তীর্ণ (Expired)")
        item.daysRemaining <= 30 -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "🚨 ${item.daysRemaining} দিন বাকি")
        item.daysRemaining <= 60 -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "⚠️ ${item.daysRemaining} দিন বাকি")
        else -> Triple(Color(0xFFFFEDD5), Color(0xFFEA580C), "⏳ ${item.daysRemaining} দিন বাকি")
    }

    val prodName = item.product?.name ?: item.batch.productBarcode
    val genName = item.product?.genericName
    val mrp = item.product?.mrpPrice ?: item.batch.mrpPrice ?: 0.0
    val totalBatchValue = item.batch.stockQty * mrp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = prodName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!genName.isNullOrBlank()) {
                        Text(
                            text = "🧬 $genName",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusBadge,
                        color = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ব্যাচ: ${item.batch.batchNumber} • মেয়াদ: ${item.batch.expiryDate}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "মজুদ: ${item.batch.stockQty.toInt()} pcs • ইউনিট MRP: ৳$mrp",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "মোট মূল্য:",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "৳ ${String.format(Locale.US, "%,.1f", totalBatchValue)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (onMoveToQuarantine != null && item.daysRemaining <= 0) {
                OutlinedButton(
                    onClick = { onMoveToQuarantine(item.batch.id, "মেয়াদ উত্তীর্ণ") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth().height(36.dp).minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("কোয়ারেন্টাইনে স্থানান্তর করুন", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun shareSupplierReturnChallan(context: Context, items: List<NearExpiryItem>) {
    if (items.isEmpty()) {
        Toast.makeText(context, "তালিকায় কোনো ওষুধ নেই", Toast.LENGTH_SHORT).show()
        return
    }

    val sb = StringBuilder()
    sb.appendLine("========================================")
    sb.appendLine("    ওষুধ কোম্পানি ফেরত চালান (RETURN CHALLAN)")
    sb.appendLine("========================================")
    sb.appendLine("তারিখ: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
    sb.appendLine("মোট আইটেম: ${items.size}টি ব্যাচ")
    sb.appendLine("----------------------------------------")

    var totalReturnVal = 0.0
    items.forEachIndexed { idx, it ->
        val name = it.product?.name ?: it.batch.productBarcode
        val mrp = it.product?.mrpPrice ?: it.batch.mrpPrice ?: 0.0
        val valAtRisk = it.batch.stockQty * mrp
        totalReturnVal += valAtRisk

        sb.appendLine("${idx + 1}. $name")
        sb.appendLine("   ব্যাচ: ${it.batch.batchNumber} | মেয়াদ: ${it.batch.expiryDate}")
        sb.appendLine("   পরিমাণ: ${it.batch.stockQty.toInt()} pcs | ইউনিট: ৳$mrp | মোট: ৳${String.format(Locale.US, "%.1f", valAtRisk)}")
    }

    sb.appendLine("----------------------------------------")
    sb.appendLine("সর্বমোট দাবিকৃত মূল্য: ৳${String.format(Locale.US, "%,.2f", totalReturnVal)}")
    sb.appendLine("========================================")
    sb.appendLine("ফার্মেসি সিল ও স্বাক্ষর: _________________")

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Medicine Return Challan - ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "কোম্পানি ফেরত চালান শেয়ার করুন"))
}
