package com.example.techyfipos.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.SaleWithItems
import com.example.techyfipos.ui.viewmodel.CustomerDossierData
import com.example.techyfipos.ui.viewmodel.CustomerTimelineEvent
import com.example.techyfipos.ui.viewmodel.CustomerTimelineEventType
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.openPhoneDialer
import com.example.techyfipos.utils.openWhatsAppChat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DossierTab {
    TOP_PRODUCTS,
    TIMELINE,
    INVOICES,
    NOTES,
    DUE_LEDGER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDossierSheet(
    dossier: CustomerDossierData,
    onDismiss: () -> Unit,
    onStartSale: (Customer) -> Unit,
    onEditCustomer: (Customer) -> Unit,
    onAddNote: (noteText: String, category: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customer = dossier.customer
    var selectedTab by remember { mutableStateOf(DossierTab.TOP_PRODUCTS) }
    var selectedSaleForReceipt by remember { mutableStateOf<SaleWithItems?>(null) }

    var newNoteText by remember { mutableStateOf("") }
    var selectedNoteCategory by remember { mutableStateOf("General") }

    val aov = remember(customer.lifetimeSpent, customer.totalOrders) {
        if (customer.totalOrders > 0) customer.lifetimeSpent / customer.totalOrders else 0.0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val initials = remember(customer.name) {
                        customer.name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase().ifBlank { "C" }
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                when (customer.tag) {
                                    "VIP" -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                    "Wholesale" -> Color(0xFF0284C7).copy(alpha = 0.2f)
                                    "Defaulter" -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = when (customer.tag) {
                                "VIP" -> Color(0xFF8B5CF6)
                                "Wholesale" -> Color(0xFF0284C7)
                                "Defaulter" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        if (customer.tag == "VIP") {
                            Text("👑", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = customer.name,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (customer.tag) {
                                    "VIP" -> Color(0xFF8B5CF6).copy(alpha = 0.15f)
                                    "Wholesale" -> Color(0xFF0284C7).copy(alpha = 0.15f)
                                    "Defaulter" -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                }
                            ) {
                                Text(
                                    text = customer.tag,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (customer.tag) {
                                        "VIP" -> Color(0xFF8B5CF6)
                                        "Wholesale" -> Color(0xFF0284C7)
                                        "Defaulter" -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "📞 ${customer.phone}${if (!customer.address.isNullOrBlank()) " • ${customer.address}" else ""}",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            onDismiss()
                            onEditCustomer(customer)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", modifier = Modifier.size(17.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(17.dp))
                    }
                }
            }

            // Credit Limit Utilization Progress Bar
            if (customer.creditLimit > 0) {
                val progress = (customer.totalDue / customer.creditLimit).toFloat().coerceIn(0f, 1f)
                val progressColor = when {
                    progress > 0.85f -> MaterialTheme.colorScheme.error
                    progress > 0.5f -> Color(0xFFF59E0B)
                    else -> MaterialTheme.colorScheme.primary
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Due: ${formatTaka(customer.totalDue)}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer.totalDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Limit: ${formatTaka(customer.creditLimit)} (${(progress * 100).toInt()}%)",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .padding(top = 4.dp)
                        )
                    }
                }
            }

            // Quick Action Buttons Deck (New Sale, WhatsApp, Call, Edit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onStartSale(customer)
                    },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("🛒 ${stringResource(R.string.customer_action_start_sale)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { openWhatsAppChat(context, customer.phone) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("💬 ${stringResource(R.string.customer_action_whatsapp)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = { openPhoneDialer(context, customer.phone) },
                    modifier = Modifier.weight(0.9f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("📞 ${stringResource(R.string.customer_action_call)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Financial Metrics Grid Strip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_lifetime_spend), fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTaka(customer.lifetimeSpent), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_orders), fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${customer.totalOrders}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_aov), fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTaka(aov), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_loyalty), fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.customer_pts_suffix, customer.loyaltyPoints), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5A100))
                    }
                }
            }

            // 5 Tabs Switcher (Top Products, Timeline, Invoices, Notes, Due Ledger)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    DossierTab.TOP_PRODUCTS to "🛍️ ${stringResource(R.string.customer_tab_top_products)}",
                    DossierTab.TIMELINE to "⏱️ ${stringResource(R.string.customer_tab_timeline)}",
                    DossierTab.INVOICES to "📜 ${stringResource(R.string.customer_tab_invoices)} (${dossier.sales.size})",
                    DossierTab.NOTES to "📝 ${stringResource(R.string.customer_tab_notes)}",
                    DossierTab.DUE_LEDGER to "📒 ${stringResource(R.string.customer_tab_due_ledger)}"
                ).forEach { (tab, title) ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Tab Content Panes
            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 290.dp)) {
                when (selectedTab) {
                    DossierTab.TOP_PRODUCTS -> {
                        if (dossier.topProducts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No purchase items recorded yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(dossier.topProducts) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("Bought ${item.purchaseCount} times", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("${item.totalQty} qty", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                                Text(formatTaka(item.totalSpent), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DossierTab.TIMELINE -> {
                        if (dossier.timelineEvents.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.customer_no_timeline), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val dtFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(dossier.timelineEvents, key = { it.id }) { event ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            val (badgeText, badgeColor) = when (event.type) {
                                                CustomerTimelineEventType.SALE -> "🛒" to Color(0xFF10B981)
                                                CustomerTimelineEventType.DUE_CREATED -> "⚠️" to MaterialTheme.colorScheme.error
                                                CustomerTimelineEventType.PAYMENT_RECEIVED -> "💵" to Color(0xFF0284C7)
                                                CustomerTimelineEventType.LOYALTY -> "⭐" to Color(0xFFF59E0B)
                                                CustomerTimelineEventType.NOTE -> "📝" to Color(0xFF8B5CF6)
                                                CustomerTimelineEventType.ACCOUNT_CREATED -> "👤" to MaterialTheme.colorScheme.primary
                                            }

                                            Text(badgeText, fontSize = 14.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(event.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text(dtFormat.format(Date(event.timestamp)), fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Text(event.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (event.amount != null && event.amount > 0) {
                                                Text(
                                                    text = formatTaka(event.amount),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    color = badgeColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DossierTab.INVOICES -> {
                        if (dossier.sales.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No sales invoices found", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val dtFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(dossier.sales) { saleWithItems ->
                                    val sale = saleWithItems.sale
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedSaleForReceipt = saleWithItems }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text("Invoice #${sale.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("• Tap to view receipt", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Text(
                                                    "${dtFormat.format(Date(sale.timestamp))} • ${sale.paymentMethod} • ${saleWithItems.items.size} items",
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                formatTaka(sale.grandTotal),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.5.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DossierTab.NOTES -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Staff Note Composer
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = stringResource(R.string.customer_add_note_prompt),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    OutlinedTextField(
                                        value = newNoteText,
                                        onValueChange = { newNoteText = it },
                                        placeholder = { Text("Log customer interaction, payment promise, wholesale rate...", fontSize = 11.sp) },
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("General", "Payment Promise", "Wholesale Quote", "Complaint").forEach { cat ->
                                                FilterChip(
                                                    selected = selectedNoteCategory == cat,
                                                    onClick = { selectedNoteCategory = cat },
                                                    label = { Text(cat, fontSize = 9.5.sp) }
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                if (newNoteText.isNotBlank()) {
                                                    onAddNote(newNoteText, selectedNoteCategory)
                                                    newNoteText = ""
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(stringResource(R.string.customer_note_save_btn), fontSize = 10.5.sp)
                                        }
                                    }
                                }
                            }

                            // Past Notes Display
                            if (customer.notes.isNullOrBlank()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.customer_no_notes), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                customer.notes.split("\n\n").forEach { noteEntry ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = noteEntry.trim(),
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DossierTab.DUE_LEDGER -> {
                        if (dossier.duePayments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No due payment history recorded", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val dtFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(dossier.duePayments) { p ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Payment: ${p.paymentMethod}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(
                                                    "${dtFormat.format(Date(p.timestamp))}${if (!p.referenceNote.isNullOrBlank()) " • ${p.referenceNote}" else ""}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                formatTaka(p.amountPaid),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF00875A)
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

    // Thermal Receipt Dialog for Selected Invoice
    if (selectedSaleForReceipt != null) {
        CustomerReceiptPreviewDialog(
            saleWithItems = selectedSaleForReceipt!!,
            customerName = customer.name,
            customerPhone = customer.phone,
            onDismiss = { selectedSaleForReceipt = null }
        )
    }
}

@Composable
fun CustomerReceiptPreviewDialog(
    saleWithItems: SaleWithItems,
    customerName: String,
    customerPhone: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var is80mm by remember { mutableStateOf(true) }
    var isBangla by remember { mutableStateOf(false) }
    val sale = saleWithItems.sale
    val dtFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Controls Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.customer_receipt_dialog_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = is80mm,
                            onClick = { is80mm = !is80mm },
                            label = { Text(if (is80mm) "80mm" else "58mm", fontSize = 10.sp) }
                        )
                        FilterChip(
                            selected = isBangla,
                            onClick = { isBangla = !isBangla },
                            label = { Text(if (isBangla) "বাং" else "EN", fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Simulated Thermal Slip Paper
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .width(if (is80mm) 280.dp else 220.dp)
                        .padding(vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TECHYFI RETAIL POS",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color.Black
                        )
                        Text(
                            text = if (isBangla) "ক্যাশ মেমো / বিক্রয় রশিদ" else "CASH MEMO / INVOICE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Text(
                            text = "Invoice #${sale.id} • ${dtFormat.format(Date(sale.timestamp))}",
                            fontSize = 9.sp,
                            color = Color.DarkGray
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.Gray)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Customer: $customerName", fontSize = 9.5.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Phone: $customerPhone", fontSize = 9.sp, color = Color.DarkGray)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.Gray)

                        // Items list
                        saleWithItems.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.quantity.toInt()}x ${item.productName}",
                                    fontSize = 9.5.sp,
                                    color = Color.Black,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatTaka(item.price * item.quantity),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.Gray)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isBangla) "সর্বমোট:" else "GRAND TOTAL:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
                            Text(formatTaka(sale.grandTotal), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = Color.Black)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isBangla) "পরিশোধিত:" else "Paid (${sale.paymentMethod}):", fontSize = 9.5.sp, color = Color.DarkGray)
                            Text(formatTaka(sale.amountPaid), fontSize = 9.5.sp, color = Color.Black)
                        }

                        val dueOnSale = (sale.grandTotal - sale.amountPaid).coerceAtLeast(0.0)
                        if (dueOnSale > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isBangla) "বকেয়া:" else "Due Balance:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Red)
                                Text(formatTaka(dueOnSale), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Red)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isBangla) "আমাদের সাথে কেনাকাটা করার জন্য ধন্যবাদ!" else "Thank you for shopping with us!",
                            fontSize = 8.5.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.close))
                    }
                    Button(
                        onClick = {
                            val shareText = "Invoice #${sale.id}\nCustomer: $customerName\nTotal: ${formatTaka(sale.grandTotal)}\nThank you for shopping with us!"
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Receipt"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Share / Print", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
