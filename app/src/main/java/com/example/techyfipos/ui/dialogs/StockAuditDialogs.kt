package com.example.techyfipos.ui.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.db.InventoryAdjustment
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.ui.screens.localizedAdjustmentReasonLabel
import com.example.techyfipos.utils.DateFormatters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAuditHistorySheet(
    adjustments: List<InventoryAdjustment>,
    allProducts: List<Product> = emptyList(),
    onAdjustProductStock: (Product) -> Unit = {},
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedReasonFilter by remember { mutableStateOf("All") }
    var showProductPicker by remember { mutableStateOf(false) }
    val reasons = listOf("All", "Restock", "Damage", "Expired", "Discrepancy", "Sample", "Store Use", "Internal Use")

    val filtered = remember(adjustments, searchQuery, selectedReasonFilter) {
        adjustments.filter { adj ->
            val matchQuery = searchQuery.isBlank() ||
                adj.productName.contains(searchQuery, ignoreCase = true) ||
                adj.productBarcode.contains(searchQuery, ignoreCase = true) ||
                (adj.note?.contains(searchQuery, ignoreCase = true) == true)
            val matchReason = selectedReasonFilter == "All" || adj.reason.equals(selectedReasonFilter, ignoreCase = true)
            matchQuery && matchReason
        }.sortedByDescending { it.timestamp }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text("📋", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.products_stock_audit_logs_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.products_total_audit_records, adjustments.size),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showProductPicker = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("📦", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.products_plus_adjust_button), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description), modifier = Modifier.size(20.dp))
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.products_search_audit_placeholder), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                reasons.forEach { reason ->
                    FilterChip(
                        selected = selectedReasonFilter == reason,
                        onClick = { selectedReasonFilter = reason },
                        label = { Text(localizedAdjustmentReasonLabel(reason), fontSize = 11.5.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📋", fontSize = 36.sp)
                    Text(
                        text = if (adjustments.isEmpty()) stringResource(R.string.products_no_adjustments_recorded) else stringResource(R.string.products_no_matching_adjustments),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.products_audit_log_description),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { showProductPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Text("📦", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.products_record_first_stock_adjustment), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { adj ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
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
                                    val (badgeBg, badgeFg) = when (adj.reason.lowercase().trim()) {
                                        "damage" -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
                                        "expired" -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
                                        "restock" -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                                        "sample" -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
                                        "store use", "internal use", "internal" -> Color(0xFFE0E7FF) to Color(0xFF3730A3)
                                        "discrepancy" -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                                        else -> Color(0xFFF1F5F9) to Color(0xFF475569)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = localizedAdjustmentReasonLabel(adj.reason).uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    val dateStr = remember(adj.timestamp) {
                                        DateFormatters.formatDateTime(adj.timestamp)
                                    }
                                    val cashierText = if (adj.cashierName.isNotBlank()) " - By: ${adj.cashierName}" else ""
                                    Text("$dateStr$cashierText", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(adj.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(R.string.products_barcode_colon, adj.productBarcode), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val prevStr = if (adj.previousStock % 1.0 == 0.0) adj.previousStock.toLong().toString() else String.format(Locale.US, "%.2f", adj.previousStock).trimEnd('0').trimEnd('.')
                                        val newStr = if (adj.newStock % 1.0 == 0.0) adj.newStock.toLong().toString() else String.format(Locale.US, "%.2f", adj.newStock).trimEnd('0').trimEnd('.')
                                        val delta = adj.difference
                                        val deltaValStr = if (delta % 1.0 == 0.0) delta.toLong().toString() else String.format(Locale.US, "%.2f", delta).trimEnd('0').trimEnd('.')
                                        val deltaStr = if (delta > 0) "+$deltaValStr" else deltaValStr

                                        Text(
                                            text = "$prevStr ➔ $newStr",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "($deltaStr)",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (delta >= 0) Color(0xFF059669) else Color(0xFFE11D48)
                                        )
                                    }
                                }

                                if (!adj.note.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.products_note_colon, adj.note ?: ""),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showProductPicker) {
            ProductPickerForAdjustmentDialog(
                products = allProducts,
                onSelectProduct = { prod ->
                    showProductPicker = false
                    onAdjustProductStock(prod)
                },
                onDismiss = { showProductPicker = false }
            )
        }
    }
}

@Composable
private fun ProductPickerForAdjustmentDialog(
    products: List<Product>,
    onSelectProduct: (Product) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filteredProducts = remember(products, search) {
        if (search.isBlank()) products
        else products.filter {
            it.name.contains(search, ignoreCase = true) ||
            it.barcode.contains(search, ignoreCase = true) ||
            it.category.contains(search, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📦", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.products_select_product_to_adjust), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(stringResource(R.string.products_choose_product_to_adjust_stock), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description))
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.products_search_product_or_barcode)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.products_no_matching_products_found), color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts, key = { it.barcode }) { prod ->
                            Surface(
                                onClick = { onSelectProduct(prod) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${prod.barcode} • ${prod.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    val sStr = if (prod.stockQty % 1.0 == 0.0) prod.stockQty.toLong().toString() else String.format(Locale.US, "%.2f", prod.stockQty).trimEnd('0').trimEnd('.')
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.products_stock_pill_qty_unit, sStr, prod.unit),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
}

