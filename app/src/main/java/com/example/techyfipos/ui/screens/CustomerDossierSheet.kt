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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
import com.example.techyfipos.utils.DateFormatters
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.utils.openPhoneDialer
import com.example.techyfipos.utils.openWhatsAppChat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.data.SettingsDataStore
import java.util.Date
import java.util.Locale

import com.example.techyfipos.data.db.DuePayment
import com.example.techyfipos.data.db.DueCharge

enum class DossierTab {
    TOP_PRODUCTS,
    TIMELINE,
    INVOICES,
    NOTES,
    DUE_LEDGER,
    CREDIT_LEDGER
}

private sealed class DossierDueLedgerRow(val timestamp: Long) {
    class Payment(val payment: DuePayment) : DossierDueLedgerRow(payment.timestamp)
    class Charge(val charge: DueCharge) : DossierDueLedgerRow(charge.timestamp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDossierSheet(
    dossier: CustomerDossierData,
    onDismiss: () -> Unit,
    onStartSale: (Customer) -> Unit,
    onEditCustomer: (Customer) -> Unit,
    onAddNote: (noteText: String, category: String) -> Unit = { _, _ -> },
    onDeleteNote: (index: Int) -> Unit = {},
    onAdjustCredit: (delta: Double, note: String) -> Unit = { _, _ -> },
    authViewModel: com.example.techyfipos.ui.viewmodel.AuthViewModel? = null,
    settingsViewModel: com.example.techyfipos.ui.viewmodel.SettingsViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUserState = authViewModel?.currentUser?.collectAsStateWithLifecycle()
    val isOwner = authViewModel?.let { currentUserState?.value?.role?.equals("OWNER", ignoreCase = true) == true } ?: false
    val customer = dossier.customer
    var selectedTab by remember { mutableStateOf(DossierTab.TOP_PRODUCTS) }
    var selectedSaleForReceipt by remember { mutableStateOf<SaleWithItems?>(null) }
    var showAdjustCreditDialog by remember { mutableStateOf(false) }

    var newNoteText by remember { mutableStateOf("") }
    var selectedNoteCategory by remember { mutableStateOf("General") }
    var noteIndexToDelete by remember { mutableStateOf<Int?>(null) }

    val aov = remember(customer.lifetimeSpent, customer.totalOrders) {
        if (customer.totalOrders > 0) customer.lifetimeSpent / customer.totalOrders else 0.0
    }

    if (noteIndexToDelete != null) {
        val targetIndex = noteIndexToDelete!!
        AlertDialog(
            onDismissRequest = { noteIndexToDelete = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.confirm_delete_customer_note_title)) },
            text = { Text(stringResource(R.string.confirm_delete_customer_note_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNote(targetIndex)
                        noteIndexToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteIndexToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
                                    fontSize = 11.sp,
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
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.customer_edit_cd), modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp))
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
                                text = stringResource(R.string.dossier_credit_due_label, formatTaka(customer.totalDue)),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer.totalDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.dossier_credit_limit_label, formatTaka(customer.creditLimit), (progress * 100).toInt()),
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
                                .padding(top = 4.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
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
                        Text(stringResource(R.string.customer_kpi_lifetime_spend), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTaka(customer.lifetimeSpent), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_orders), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${customer.totalOrders}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_aov), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTaka(aov), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_loyalty), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.customer_pts_suffix, customer.loyaltyPoints), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5A100))
                    }
                }
            }

            // Store Credit Wallet Banner Card with Adjust Action
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (customer.storeCredit > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, if (customer.storeCredit > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🪙", fontSize = 18.sp)
                        Column {
                            Text(
                                text = stringResource(R.string.store_credit_balance),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (customer.storeCredit > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTaka(customer.storeCredit),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (customer.storeCredit > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    val canAdjustCredit = isOwner
                    if (canAdjustCredit) {
                        OutlinedButton(
                            onClick = { showAdjustCreditDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (customer.storeCredit > 0) Color(0xFF059669) else MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, if (customer.storeCredit > 0) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dossier_credit_adjust_btn),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Tabs Switcher (Top Products, Timeline, Invoices, Notes, Due Ledger, Credit Ledger)
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
                    DossierTab.DUE_LEDGER to "📒 ${stringResource(R.string.customer_tab_due_ledger)}",
                    DossierTab.CREDIT_LEDGER to "🪙 ${stringResource(R.string.customer_tab_credit_ledger)} (${dossier.storeCreditLedger.size})"
                ).forEach { (tab, title) ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .heightIn(min = 48.dp)
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
            val maxTabHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.45f).coerceAtLeast(290.dp)
            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = maxTabHeight)) {
                when (selectedTab) {
                    DossierTab.TOP_PRODUCTS -> {
                        if (dossier.topProducts.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.customer_dossier_no_purchase_items), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                Text(stringResource(R.string.customer_dossier_bought_times, item.purchaseCount), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(stringResource(R.string.dossier_qty_suffix, item.totalQty), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
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
                                                    Text(DateFormatters.formatDateTime(event.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.customer_dossier_no_invoices), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
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
                                                    Text(stringResource(R.string.invoice_id_fmt, sale.id), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text(stringResource(R.string.customer_dossier_tap_to_view_receipt), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Text(
                                                    "${DateFormatters.formatDateTime(sale.timestamp)} • ${sale.paymentMethod} • ${pluralStringResource(R.plurals.items_count, saleWithItems.items.size, saleWithItems.items.size)}",
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
                                        placeholder = { Text(stringResource(R.string.customer_dossier_note_placeholder), fontSize = 11.sp) },
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val noteCategories = remember {
                                            listOf(
                                                "General" to R.string.note_cat_general,
                                                "Payment Promise" to R.string.note_cat_payment_promise,
                                                "Wholesale Quote" to R.string.note_cat_wholesale_quote,
                                                "Complaint" to R.string.note_cat_complaint
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            noteCategories.forEach { (catKey, catLabelRes) ->
                                                FilterChip(
                                                    selected = selectedNoteCategory == catKey,
                                                    onClick = { selectedNoteCategory = catKey },
                                                    label = { Text(stringResource(catLabelRes), fontSize = 11.sp) }
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
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Text(stringResource(R.string.customer_note_save_btn), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Past Notes Display
                            val noteEntries = remember(customer.notes) {
                                customer.notes?.split("\u001E")?.flatMap { it.split("\n\n") }?.filter { it.isNotBlank() } ?: emptyList()
                            }
                            if (noteEntries.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.customer_no_notes), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                noteEntries.forEachIndexed { index, noteEntry ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = noteEntry.trim(),
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { noteIndexToDelete = index },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DossierTab.DUE_LEDGER -> {
                        val ledgerEntries = remember(dossier.duePayments, dossier.dueCharges) {
                            (dossier.duePayments.map { DossierDueLedgerRow.Payment(it) } +
                             dossier.dueCharges.map { DossierDueLedgerRow.Charge(it) })
                                .sortedByDescending { it.timestamp }
                        }
                        if (ledgerEntries.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.customer_dossier_no_due_payments), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(ledgerEntries) { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        when (entry) {
                                            is DossierDueLedgerRow.Payment -> {
                                                val p = entry.payment
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFFECFDF5),
                                                            border = BorderStroke(0.8.dp, Color(0xFFA7F3D0)),
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    Icons.Default.Payment,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFF00875A),
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                        Column {
                                                            Text(stringResource(R.string.customer_payment_method, p.paymentMethod), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            Text(
                                                                "${DateFormatters.formatDateTime(p.timestamp)}${if (!p.referenceNote.isNullOrBlank()) " • ${p.referenceNote}" else ""}",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        "+${formatTaka(p.amountPaid)}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF00875A)
                                                    )
                                                }
                                            }
                                            is DossierDueLedgerRow.Charge -> {
                                                val c = entry.charge
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFFFEF2F2),
                                                            border = BorderStroke(0.8.dp, Color(0xFFFECACA)),
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    Icons.Default.ShoppingCart,
                                                                    contentDescription = null,
                                                                    tint = Color(0xFFDC2626),
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                        Column {
                                                            Text(
                                                                stringResource(
                                                                    if (c.reason == "Sale") R.string.dues_charge_reason_sale
                                                                    else R.string.dues_charge_reason_manual
                                                                ),
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                            Text(
                                                                "${DateFormatters.formatDateTime(c.timestamp)}${if (!c.note.isNullOrBlank()) " • ${c.note}" else ""}",
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        "-${formatTaka(c.amount)}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFDC2626)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DossierTab.CREDIT_LEDGER -> {
                        if (dossier.storeCreditLedger.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.customer_dossier_no_credit_transactions), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(dossier.storeCreditLedger) { entry ->
                                    val isPositive = entry.deltaAmount > 0
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isPositive) Color(0xFFECFDF5) else Color(0xFFFFF1F2),
                                        border = BorderStroke(1.dp, if (isPositive) Color(0xFFA7F3D0) else Color(0xFFFECDD3)),
                                        modifier = Modifier.fillMaxWidth()
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
                                                    Text(
                                                        text = when (entry.type) {
                                                            "RETURN_REFUND" -> stringResource(R.string.dossier_credit_return_refund)
                                                            "SALE_REDEMPTION" -> stringResource(R.string.dossier_credit_checkout_redemption)
                                                            "MANUAL_ADJUSTMENT" -> stringResource(R.string.dossier_credit_supervisor_adjustment)
                                                            "SALE_VOID" -> stringResource(R.string.dossier_credit_sale_void)
                                                            "MERGE_TRANSFER" -> stringResource(R.string.dossier_credit_merge_transfer)
                                                            "VOUCHER_RESTORE" -> stringResource(R.string.dossier_credit_voucher_restore)
                                                            "CHARGE_REVERSAL_OVERPAYMENT" -> stringResource(R.string.dossier_credit_charge_reversal_overpayment)
                                                            else -> stringResource(R.string.dossier_credit_unknown_type)
                                                        },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.5.sp,
                                                        color = if (isPositive) Color(0xFF065F46) else Color(0xFF9F1239)
                                                    )
                                                    if (entry.referenceInvoiceId != null) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = Color.White
                                                        ) {
                                                            Text(
                                                                text = "#${entry.referenceInvoiceId}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF4F46E5),
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = buildString {
                                                        append(DateFormatters.formatDateTime(entry.timestamp))
                                                        if (!entry.note.isNullOrBlank()) {
                                                            append(" • ")
                                                            append(entry.note)
                                                        }
                                                    },
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "${if (isPositive) "+" else ""}${formatTaka(entry.deltaAmount)}",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.5.sp,
                                                    color = if (isPositive) Color(0xFF059669) else Color(0xFFE11D48)
                                                )
                                                Text(
                                                    text = stringResource(R.string.dossier_balance_abbrev, formatTaka(entry.balanceAfter)),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }

    // Adjust Store Credit Dialog
    if (showAdjustCreditDialog) {
        AdjustStoreCreditDialog(
            customerName = customer.name,
            currentCredit = customer.storeCredit,
            onDismiss = { showAdjustCreditDialog = false },
            onConfirm = { delta, note ->
                onAdjustCredit(delta, note)
                showAdjustCreditDialog = false
            }
        )
    }

    // Thermal Receipt Dialog for Selected Invoice
    if (selectedSaleForReceipt != null) {
        CustomerReceiptPreviewDialog(
            saleWithItems = selectedSaleForReceipt!!,
            customerName = customer.name,
            customerPhone = customer.phone,
            settingsViewModel = settingsViewModel,
            onDismiss = { selectedSaleForReceipt = null }
        )
    }
}

@Composable
fun CustomerReceiptPreviewDialog(
    saleWithItems: SaleWithItems,
    customerName: String,
    customerPhone: String,
    settingsViewModel: com.example.techyfipos.ui.viewmodel.SettingsViewModel? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuredStoreName = settingsViewModel?.shopName?.collectAsStateWithLifecycle()?.value.orEmpty()
    val effectiveStoreName = configuredStoreName.ifBlank { stringResource(R.string.default_store_name) }
    var is80mm by remember { mutableStateOf(true) }
    var isBangla by remember { mutableStateOf(false) }
    val sale = saleWithItems.sale

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
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
                            text = effectiveStoreName,
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
                            text = stringResource(R.string.invoice_num_with_date_fmt, sale.id, DateFormatters.formatDateTime(sale.timestamp)),
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.Gray)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.customer_ledger_name, customerName), fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.customer_ledger_phone, customerPhone), fontSize = 11.sp, color = Color.DarkGray)
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
                                    fontSize = 11.sp,
                                    color = Color.Black,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatTaka(item.price * item.quantity),
                                    fontSize = 11.sp,
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
                            Text(if (isBangla) "পরিশোধিত (${sale.paymentMethod}):" else "PAID (${sale.paymentMethod}):", fontSize = 11.sp, color = Color.DarkGray)
                            Text(formatTaka(sale.amountPaid), fontSize = 11.sp, color = Color.Black)
                        }

                        val dueOnSale = (sale.grandTotal - sale.amountPaid).coerceAtLeast(0.0)
                        if (dueOnSale > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isBangla) "বকেয়া ব্যালেন্স:" else "DUE BALANCE:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Red)
                                Text(formatTaka(dueOnSale), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Red)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isBangla) "আমাদের সাথে কেনাকাটা করার জন্য ধন্যবাদ!" else "Thank you for shopping with us!",
                            fontSize = 10.5.sp,
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
                            val shareText = context.getString(R.string.customer_receipt_share_text, sale.id, customerName, formatTaka(sale.grandTotal))
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.customer_share_receipt_chooser)))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.customer_share_print), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustStoreCreditDialog(
    customerName: String,
    currentCredit: Double,
    onDismiss: () -> Unit,
    onConfirm: (delta: Double, note: String) -> Unit
) {
    var isAdd by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var reasonNote by remember { mutableStateOf("") }
    val defaultAdjustmentNote = stringResource(R.string.store_credit_default_adjustment_note)

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val canConfirm = amount > 0 && (isAdd || amount <= currentCredit)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🪙", fontSize = 22.sp)
                Text(stringResource(R.string.store_credit_adjust_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.store_credit_adjust_desc, customerName),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.store_credit_current_balance, formatTaka(currentCredit)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Action Switch: Add (+) vs Deduct (-)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = { isAdd = true },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAdd) Color(0xFF10B981) else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.store_credit_adjust_add),
                            fontSize = 11.sp,
                            fontWeight = if (isAdd) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAdd) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Surface(
                        onClick = { isAdd = false },
                        shape = RoundedCornerShape(6.dp),
                        color = if (!isAdd) Color(0xFFEF4444) else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.store_credit_adjust_deduct),
                            fontSize = 11.sp,
                            fontWeight = if (!isAdd) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isAdd) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = com.example.techyfipos.utils.sanitizeNumericInput(it) },
                    label = { Text(stringResource(R.string.store_credit_amount_to_adjust)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reasonNote,
                    onValueChange = { reasonNote = it },
                    label = { Text(stringResource(R.string.store_credit_note_reason)) },
                    placeholder = { Text(stringResource(R.string.store_credit_adjust_note_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isAdd && amount > currentCredit) {
                    Text(
                        stringResource(R.string.store_credit_deduct_exceeds_current, formatTaka(currentCredit)),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val delta = if (isAdd) amount else -amount
                    onConfirm(delta, reasonNote.trim().ifBlank { defaultAdjustmentNote })
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdd) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            ) {
                Text(
                    if (isAdd) stringResource(R.string.store_credit_add_credit_btn) else stringResource(R.string.store_credit_deduct_credit_btn),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
