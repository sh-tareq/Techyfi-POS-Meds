package com.techyfiposmeds.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.techyfiposmeds.data.db.Product
import com.techyfiposmeds.data.db.Supplier
import com.techyfiposmeds.utils.formatTaka
import com.techyfiposmeds.utils.parseExpiryDateToMillis
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuarantineManagementDialog(
    batches: List<MedicineBatch>,
    products: List<Product>,
    suppliers: List<Supplier>,
    onDismiss: () -> Unit,
    onQuarantineBatch: (batchId: Long, reason: String) -> Unit,
    onReleaseBatch: (batchId: Long) -> Unit,
    onReturnToSupplier: (batchId: Long, supplierId: Long, quantity: Double, refundPrice: Double, reason: String, note: String?) -> Unit,
    onDisposeBatch: (batchId: Long, quantity: Double, reason: String, notes: String?) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Expired, 1: Near Expiry, 2: Quarantined, 3: All Batches
    var nearExpiryDaysThreshold by remember { mutableIntStateOf(60) } // 30, 60, 90 days
    var searchQuery by remember { mutableStateOf("") }

    // Dialog action states
    var quarantineTarget by remember { mutableStateOf<MedicineBatch?>(null) }
    var quarantineReason by remember { mutableStateOf("মেয়াদোত্তীর্ণ") }

    var returnTarget by remember { mutableStateOf<MedicineBatch?>(null) }
    var returnSupplierId by remember { mutableStateOf(suppliers.firstOrNull()?.id ?: 0L) }
    var returnQuantityText by remember { mutableStateOf("") }
    var returnRefundPriceText by remember { mutableStateOf("") }
    var returnReason by remember { mutableStateOf("মেয়াদোত্তীর্ণ পণ্য ফেরত") }

    var disposeTarget by remember { mutableStateOf<MedicineBatch?>(null) }
    var disposeQuantityText by remember { mutableStateOf("") }
    var disposeReason by remember { mutableStateOf("DGDA নিয়ম অনুযায়ী আনুষ্ঠানিক ধ্বংসকরণ") }
    var disposeNotesText by remember { mutableStateOf("") }

    val now = System.currentTimeMillis()
    val nearExpiryMillis = now + (nearExpiryDaysThreshold.toLong() * 24L * 60L * 60L * 1000L)

    val productMap = remember(products) { products.associateBy { it.barcode } }

    val filteredBatches = remember(batches, selectedTab, nearExpiryDaysThreshold, searchQuery, productMap) {
        batches.filter { batch ->
            val expMillis = parseExpiryDateToMillis(batch.expiryDate) ?: Long.MAX_VALUE
            val prod = productMap[batch.productBarcode]
            val prodName = prod?.name.orEmpty()

            val matchesSearch = searchQuery.isBlank() ||
                prodName.contains(searchQuery, ignoreCase = true) ||
                batch.productBarcode.contains(searchQuery, ignoreCase = true) ||
                batch.batchNumber.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (selectedTab) {
                0 -> !batch.isQuarantined && expMillis < now // Expired
                1 -> !batch.isQuarantined && expMillis in now..nearExpiryMillis // Near Expiry
                2 -> batch.isQuarantined // Quarantined
                else -> true // All
            }
            matchesSearch && matchesTab
        }.sortedWith(
            compareBy<MedicineBatch> { it.isQuarantined }
                .thenBy { it.expiryDate }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
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
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🚫", fontSize = 20.sp)
                            }
                        }
                        Column {
                            Text(
                                text = "কোয়ারেন্টাইন ও মেয়াদোত্তীর্ণ ওষুধ ব্যবস্থাপনা",
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "DGDA সুরক্ষা মানদণ্ড · কোম্পানি ফেরত ও বর্জ্য অপসারণ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ওষুধের নাম, বারকোড বা ব্যাচ নং দিয়ে খুঁজুন...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            val count = batches.count { !it.isQuarantined && (parseExpiryDateToMillis(it.expiryDate) ?: Long.MAX_VALUE) < now }
                            Text("🚫 মেয়াদোত্তীর্ণ ($count)", fontSize = 11.5.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            val count = batches.count { !it.isQuarantined && (parseExpiryDateToMillis(it.expiryDate) ?: Long.MAX_VALUE) in now..nearExpiryMillis }
                            Text("⚠️ আসন্ন মেয়াদ ($count)", fontSize = 11.5.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            val count = batches.count { it.isQuarantined }
                            Text("🔒 কোয়ারেন্টাইন ($count)", fontSize = 11.5.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Text("📦 সকল ব্যাচ (${batches.size})", fontSize = 11.5.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }

                // If Near Expiry selected: Show 30/60/90 Days filter chips
                if (selectedTab == 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("মেয়াদ শেষ হতে বাকি:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        listOf(30 to "৩০ দিন", 60 to "৬০ দিন", 90 to "৯০ দিন").forEach { (days, label) ->
                            FilterChip(
                                selected = nearExpiryDaysThreshold == days,
                                onClick = { nearExpiryDaysThreshold = days },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Batches List
                if (filteredBatches.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (selectedTab == 0) "✅" else "📦", fontSize = 36.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when (selectedTab) {
                                    0 -> "কোন মেয়াদোত্তীর্ণ ব্যাচ নেই!"
                                    1 -> "আসন্ন $nearExpiryDaysThreshold দিনে কোন ব্যাচের মেয়াদ শেষ হচ্ছে না।"
                                    2 -> "বর্তমানে কোয়ারেন্টাইনে কোন ওষুধ নেই।"
                                    else -> "কোন ব্যাচ রেকর্ড পাওয়া যায়নি।"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredBatches, key = { it.id }) { batch ->
                            val prod = productMap[batch.productBarcode]
                            val isQuarantined = batch.isQuarantined
                            val expMillis = parseExpiryDateToMillis(batch.expiryDate) ?: Long.MAX_VALUE
                            val isExpired = expMillis < now
                            val daysLeft = ((expMillis - now) / (1000L * 60L * 60L * 24L)).coerceAtLeast(0)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isQuarantined -> Color(0xFFFEE2E2).copy(alpha = 0.6f)
                                    isExpired -> Color(0xFFFEF3C7).copy(alpha = 0.6f)
                                    daysLeft <= 30 -> Color(0xFFFFF7ED).copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        isQuarantined -> Color(0xFFEF4444)
                                        isExpired -> Color(0xFFF59E0B)
                                        daysLeft <= 30 -> Color(0xFFFB923C)
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
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = prod?.name ?: batch.productBarcode,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!prod?.genericName.isNullOrBlank()) {
                                                Text(
                                                    text = prod?.genericName.orEmpty(),
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        // Status Pill
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                isQuarantined -> Color(0xFFEF4444)
                                                isExpired -> Color(0xFFF59E0B)
                                                else -> Color(0xFF10B981)
                                            }
                                        ) {
                                            Text(
                                                text = when {
                                                    isQuarantined -> "কোয়ারেন্টাইন"
                                                    isExpired -> "মেয়াদোত্তীর্ণ"
                                                    else -> "সক্রিয়"
                                                },
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🏷️ ব্যাচ: ${batch.batchNumber} · ⏳ মেয়াদ: ${batch.expiryDate} ${if (isExpired) "(মেয়াদোত্তীর্ণ)" else "($daysLeft দিন বাকি)"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isExpired) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toLong().toString() else "%.1f".format(batch.stockQty)} পিস",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isQuarantined) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (isQuarantined && !batch.quarantineReason.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "🚫 কারণ: ${batch.quarantineReason}",
                                            fontSize = 11.sp,
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Action Buttons Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!isQuarantined) {
                                            FilledTonalButton(
                                                onClick = { quarantineTarget = batch },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = Color(0xFFFEE2E2),
                                                    contentColor = Color(0xFFDC2626)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                            ) {
                                                Text("🔒 কোয়ারেন্টাইন করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        returnTarget = batch
                                                        returnQuantityText = if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toLong().toString() else batch.stockQty.toString()
                                                        returnRefundPriceText = batch.purchasePrice.takeIf { it > 0.0 }?.toString().orEmpty()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("🚚 কোম্পানি ফেরত", fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        disposeTarget = batch
                                                        disposeQuantityText = if (batch.stockQty % 1.0 == 0.0) batch.stockQty.toLong().toString() else batch.stockQty.toString()
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("🗑️ ধ্বংসকরণ", fontSize = 11.sp)
                                                }
                                                FilledTonalButton(
                                                    onClick = { onReleaseBatch(batch.id) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("মুক্ত করুন", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quarantine Confirmation Dialog
    quarantineTarget?.let { batch ->
        AlertDialog(
            onDismissRequest = { quarantineTarget = null },
            title = { Text("স্টক কোয়ারেন্টাইন নিশ্চিতকরণ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val prod = productMap[batch.productBarcode]
                    Text(
                        "${prod?.name ?: batch.productBarcode} · ব্যাচ ${batch.batchNumber} (${batch.stockQty} পিস) কোয়ারেন্টাইন করা হলে এটি কাউন্টারে বিক্রি হওয়া থেকে সম্পূর্ণ লক হয়ে যাবে।",
                        fontSize = 12.5.sp
                    )
                    Text("কোয়ারেন্টাইনের কারণ:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    listOf("মেয়াদোত্তীর্ণ", "DGDA প্রত্যাহারের নির্দেশ", "ক্ষতিগ্রস্ত বা ভাঙা ওষুধ", "কোম্পানি ক্রেডিট ফেরত পেন্ডিং").forEach { reason ->
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
                        quarantineTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("কোয়ারেন্টাইন করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { quarantineTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Return To Supplier Dialog
    returnTarget?.let { batch ->
        var supplierMenuExpanded by remember { mutableStateOf(false) }
        val prod = productMap[batch.productBarcode]
        val selectedSupplier = suppliers.firstOrNull { it.id == returnSupplierId }

        AlertDialog(
            onDismissRequest = { returnTarget = null },
            title = { Text("কোম্পানি / সরবরাহকারীকে স্টক ফেরত", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${prod?.name ?: batch.productBarcode} · ব্যাচ: ${batch.batchNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Text("সরবরাহকারী নির্বাচন করুন:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { supplierMenuExpanded = true },
                            enabled = suppliers.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedSupplier?.let { "${it.companyName} (${it.name})" } ?: "সরবরাহকারী নির্বাচন করুন")
                        }
                        DropdownMenu(
                            expanded = supplierMenuExpanded,
                            onDismissRequest = { supplierMenuExpanded = false }
                        ) {
                            suppliers.forEach { sup ->
                                DropdownMenuItem(
                                    text = { Text("${sup.companyName} (${sup.name})") },
                                    onClick = {
                                        returnSupplierId = sup.id
                                        supplierMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = returnQuantityText,
                            onValueChange = { returnQuantityText = it },
                            label = { Text("ফেরতের পরিমাণ (পিস)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = returnRefundPriceText,
                            onValueChange = { returnRefundPriceText = it },
                            label = { Text("টিপি / রিফান্ড রেট (৳)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val retQty = returnQuantityText.toDoubleOrNull() ?: 0.0
                    val retPrice = returnRefundPriceText.toDoubleOrNull() ?: 0.0
                    val totalCredit = retQty * retPrice

                    if (totalCredit > 0.0) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFDCFCE7), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "সরবরাহকারী বকেয়া থেকে কর্তন হবে: ৳${formatTaka(totalCredit)}",
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = returnReason,
                        onValueChange = { returnReason = it },
                        label = { Text("ফেরতের কারণ / চালান রেফারেন্স") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val retQty = returnQuantityText.toDoubleOrNull() ?: 0.0
                        val retPrice = returnRefundPriceText.toDoubleOrNull() ?: 0.0
                        if (retQty > 0 && returnSupplierId > 0) {
                            onReturnToSupplier(batch.id, returnSupplierId, retQty, retPrice, returnReason, null)
                            returnTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("ফেরত নিশ্চিত করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { returnTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // DGDA Condemnation / Disposal Dialog
    disposeTarget?.let { batch ->
        val prod = productMap[batch.productBarcode]

        AlertDialog(
            onDismissRequest = { disposeTarget = null },
            title = { Text("DGDA বর্জ্য অপসারণ / ধ্বংসকরণ রেকর্ড", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("${prod?.name ?: batch.productBarcode} · ব্যাচ: ${batch.batchNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("DGDA পরিদর্শন মানদণ্ড অনুযায়ী মেয়াদোত্তীর্ণ বা নষ্ট ওষুধের আনুষ্ঠানিক ধ্বংসকরণ লগ সংরক্ষণ করা হবে।", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline)

                    OutlinedTextField(
                        value = disposeQuantityText,
                        onValueChange = { disposeQuantityText = it },
                        label = { Text("ধ্বংসের পরিমাণ (পিস)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = disposeReason,
                        onValueChange = { disposeReason = it },
                        label = { Text("ধ্বংসকরণের কারণ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = disposeNotesText,
                        onValueChange = { disposeNotesText = it },
                        label = { Text("উপস্থিত ফার্মাসিস্ট ও সাক্ষীর নাম / নোট") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dispQty = disposeQuantityText.toDoubleOrNull() ?: 0.0
                        if (dispQty > 0) {
                            onDisposeBatch(batch.id, dispQty, disposeReason, disposeNotesText)
                            disposeTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("ধ্বংসকরণ রেকর্ড করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { disposeTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
