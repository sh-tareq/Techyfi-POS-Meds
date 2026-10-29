package com.techyfiposmeds.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.data.db.Product
import com.techyfiposmeds.utils.isAppBangla
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmaStockInwardDialog(
    products: List<Product>,
    preselectedProduct: Product? = null,
    onDismiss: () -> Unit,
    onConfirmInward: (
        product: Product,
        batchNumber: String,
        expiryDate: String,
        mfgDate: String?,
        totalQty: Double,
        effectiveCost: Double,
        mrpPrice: Double,
        supplierName: String,
        invoiceNo: String
    ) -> Unit
) {
    val context = LocalContext.current
    val isBn = isAppBangla()
    var selectedProduct by remember { mutableStateOf(preselectedProduct) }
    var searchQuery by remember { mutableStateOf("") }
    var isSelectingProduct by remember { mutableStateOf(preselectedProduct == null) }

    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) products.take(20)
        else {
            val q = searchQuery.trim().lowercase()
            products.filter {
                it.name.lowercase().contains(q) ||
                it.genericName?.lowercase()?.contains(q) == true ||
                it.barcode.lowercase().contains(q)
            }.take(30)
        }
    }

    // Inward fields
    var supplierName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var batchNumber by remember { mutableStateOf("BX-" + SimpleDateFormat("yyMM", Locale.getDefault()).format(Date())) }
    var expiryDate by remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, 2)
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time))
    }
    var mfgDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    var paidQtyStr by remember { mutableStateOf("10") }
    var bonusQtyStr by remember { mutableStateOf("1") }
    var tpStr by remember { mutableStateOf(selectedProduct?.purchasePrice?.toString() ?: "50") }
    var mrpStr by remember { mutableStateOf(selectedProduct?.mrpPrice?.toString() ?: "60") }

    // Update prices when product changes
    LaunchedEffect(selectedProduct) {
        selectedProduct?.let {
            if (it.purchasePrice > 0) tpStr = it.purchasePrice.toString()
            if (it.mrpPrice > 0) mrpStr = it.mrpPrice.toString()
        }
    }

    // Calculations
    val paidQty = paidQtyStr.toDoubleOrNull() ?: 0.0
    val bonusQty = bonusQtyStr.toDoubleOrNull() ?: 0.0
    val tp = tpStr.toDoubleOrNull() ?: 0.0
    val mrp = mrpStr.toDoubleOrNull() ?: 0.0

    val totalReceivedQty = paidQty + bonusQty
    val totalInvoiceCost = paidQty * tp
    val effectiveCost = if (totalReceivedQty > 0) totalInvoiceCost / totalReceivedQty else tp
    val unitSavings = tp - effectiveCost
    val marginPercent = if (mrp > 0) ((mrp - effectiveCost) / mrp) * 100 else 0.0

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
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F766E).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Pharma Stock Inward & Scheme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isBn) "কোম্পানি চালান ইনওয়ার্ড ও ফ্রি বোনাস পাতা হিসাব" else "Supplier Challan Inward & Bonus Scheme",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0F766E),
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

                if (isSelectingProduct || selectedProduct == null) {
                    // Search & Select Product
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (isBn) "ওষুধের নাম বা জেনেরিক খুঁজুন..." else "Search medicine or generic...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts, key = { it.barcode }) { prod ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProduct = prod
                                        isSelectingProduct = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (!prod.genericName.isNullOrBlank()) {
                                            Text(prod.genericName ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    Text(if (isBn) "মজুদ: ${prod.stockQty.toInt()}" else "Stock: ${prod.stockQty.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    // Selected Product Banner
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F766E).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.3f)),
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
                                    text = selectedProduct!!.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isBn) "জেনেরিক: ${selectedProduct!!.genericName ?: "N/A"} • বর্তমান মজুদ: ${selectedProduct!!.stockQty.toInt()} pcs"
                                           else "Generic: ${selectedProduct!!.genericName ?: "N/A"} • Current Stock: ${selectedProduct!!.stockQty.toInt()} pcs",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF0F766E)
                                )
                            }
                            TextButton(
                                onClick = { isSelectingProduct = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(if (isBn) "পরিবর্তন" else "Change", fontSize = 11.5.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Form Fields
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = supplierName,
                                onValueChange = { supplierName = it },
                                label = { Text(if (isBn) "কোম্পানি / সরবরাহকারী" else "Supplier / Company", fontSize = 11.sp) },
                                placeholder = { Text(if (isBn) "যেমন: Square, Incepta" else "e.g. Square, Incepta", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = invoiceNo,
                                onValueChange = { invoiceNo = it },
                                label = { Text(if (isBn) "চালান / ইনভয়েস নং" else "Invoice / Challan No", fontSize = 11.sp) },
                                placeholder = { Text("INV-2026-01", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = batchNumber,
                                onValueChange = { batchNumber = it },
                                label = { Text(if (isBn) "ব্যাচ নম্বর (Batch No)" else "Batch Number", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = expiryDate,
                                onValueChange = { expiryDate = it },
                                label = { Text(if (isBn) "মেয়াদ (YYYY-MM-DD)" else "Expiry (YYYY-MM-DD)", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Bonus Scheme Stepper
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isBn) "🎁 বোনাস পাতা স্কিম ও পরিমাণ (Bonus Strips / Pcs):" else "🎁 Bonus Strips / Free Scheme:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = paidQtyStr,
                                        onValueChange = { paidQtyStr = it },
                                        label = { Text(if (isBn) "কেনা পরিমাণ (Paid)" else "Paid Quantity", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    OutlinedTextField(
                                        value = bonusQtyStr,
                                        onValueChange = { bonusQtyStr = it },
                                        label = { Text(if (isBn) "ফ্রি বোনাস (Free)" else "Free / Bonus Qty", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        // Price Details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = tpStr,
                                onValueChange = { tpStr = it },
                                label = { Text(if (isBn) "ট্রেড প্রাইস (TP / কেনা)" else "Trade Price (TP)", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = mrpStr,
                                onValueChange = { mrpStr = it },
                                label = { Text(if (isBn) "খুচরা মূল্য (MRP)" else "Retail Price (MRP)", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Live Calculation Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F766E).copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, Color(0xFF0F766E).copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isBn) "সর্বমোট প্রাপ্ত পরিমাণ:" else "Total Received Quantity:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline)
                                    Text(if (isBn) "${totalReceivedQty.toInt()} pcs (${paidQty.toInt()} পেইড + ${bonusQty.toInt()} ফ্রি)"
                                         else "${totalReceivedQty.toInt()} pcs (${paidQty.toInt()} Paid + ${bonusQty.toInt()} Free)",
                                         fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isBn) "কোম্পানিকে প্রদেয় মূল্য:" else "Payable to Supplier:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("৳ ${String.format(Locale.US, "%,.2f", totalInvoiceCost)}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Divider(color = Color(0xFF0F766E).copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(if (isBn) "প্রকৃত কার্যকর কেনা মূল্য (Effective Cost):" else "Effective Cost Price:", fontSize = 11.sp, color = Color(0xFF0F766E), fontWeight = FontWeight.Bold)
                                        Text(if (isBn) "বোনাসে সাশ্রয়: ৳${String.format(Locale.US, "%.2f", unitSavings)} / pc" else "Bonus Savings: ৳${String.format(Locale.US, "%.2f", unitSavings)} / pc", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text("৳ ${String.format(Locale.US, "%.2f", effectiveCost)}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF0F766E))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (isBn) "প্রত্যাশিত নিট মার্জিন:" else "Expected Net Margin:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("${String.format(Locale.US, "%.1f", marginPercent)}%", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color(0xFF047857))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inward Action Button
                    Button(
                        onClick = {
                            if (totalReceivedQty <= 0) {
                                Toast.makeText(context, if (isBn) "পরিমাণ লিখুন" else "Enter quantity", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (batchNumber.isBlank()) {
                                Toast.makeText(context, if (isBn) "ব্যাচ নম্বর লিখুন" else "Enter batch number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onConfirmInward(
                                selectedProduct!!,
                                batchNumber.trim(),
                                expiryDate.trim(),
                                mfgDate.trim().ifBlank { null },
                                totalReceivedQty,
                                effectiveCost,
                                mrp,
                                supplierName.trim(),
                                invoiceNo.trim()
                            )
                            Toast.makeText(context, if (isBn) "স্টক সফলভাবে ইনওয়ার্ড করা হয়েছে" else "Stock successfully inwarded", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                    ) {
                        Text(if (isBn) "ইনওয়ার্ড সম্পন্ন করুন (${totalReceivedQty.toInt()} pcs)" else "Complete Inward (${totalReceivedQty.toInt()} pcs)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
