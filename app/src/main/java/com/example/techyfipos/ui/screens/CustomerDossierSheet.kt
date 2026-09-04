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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.ui.viewmodel.CustomerDossierData
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.openPhoneDialer
import com.example.techyfipos.utils.openWhatsAppChat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DossierTab {
    TOP_PRODUCTS,
    INVOICES,
    DUE_LEDGER,
    LOYALTY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDossierSheet(
    dossier: CustomerDossierData,
    onDismiss: () -> Unit,
    onStartSale: (Customer) -> Unit,
    onEditCustomer: (Customer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customer = dossier.customer
    var selectedTab by remember { mutableStateOf(DossierTab.TOP_PRODUCTS) }

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
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(44.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val initials = remember(customer.name) {
                        customer.name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase().ifBlank { "C" }
                    }
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "📞 ${customer.phone}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            onDismiss()
                            onEditCustomer(customer)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Customer Tags & Info Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = "🏷️ ${customer.tag}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (customer.loyaltyTier.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = stringResource(R.string.customer_badge_tier, customer.loyaltyTier),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (customer.totalDue > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "⚠️ ${stringResource(R.string.customer_badge_due, formatTaka(customer.totalDue))}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (customer.creditLimit > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = stringResource(R.string.customer_badge_limit, formatTaka(customer.creditLimit)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (!customer.address.isNullOrBlank()) {
                Text(
                    text = "📍 ${customer.address}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Quick Contact & Start Sale Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        openWhatsAppChat(context, customer.phone)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("💬 ${stringResource(R.string.customer_action_whatsapp)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                FilledTonalButton(
                    onClick = {
                        openPhoneDialer(context, customer.phone)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("📞 ${stringResource(R.string.customer_action_call)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onDismiss()
                        onStartSale(customer)
                    },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text("🛒 ${stringResource(R.string.customer_action_start_sale)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Financial Metrics Grid
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_lifetime_spend), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTaka(customer.lifetimeSpent), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_orders), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${customer.totalOrders}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_aov), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatTaka(aov), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.customer_kpi_loyalty), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.customer_pts_suffix, customer.loyaltyPoints), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE5A100))
                    }
                }
            }

            // Tabs Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    DossierTab.TOP_PRODUCTS to "🛍️ ${stringResource(R.string.customer_tab_top_products)}",
                    DossierTab.INVOICES to "📜 ${stringResource(R.string.customer_tab_invoices)} (${dossier.sales.size})",
                    DossierTab.DUE_LEDGER to "📒 ${stringResource(R.string.customer_tab_due_ledger)}",
                    DossierTab.LOYALTY to "⭐ ${stringResource(R.string.customer_tab_loyalty)}"
                ).forEach { (tab, title) ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 6.dp),
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

            // Tab Content
            Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 280.dp)) {
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
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("Bought ${item.purchaseCount} times", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("${item.totalQty} qty", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(formatTaka(item.totalSpent), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Invoice #${sale.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(
                                                    "${dtFormat.format(Date(sale.timestamp))} • ${sale.paymentMethod}",
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                formatTaka(sale.grandTotal),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
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
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Payment: ${p.paymentMethod}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(
                                                    "${dtFormat.format(Date(p.timestamp))}${if (!p.referenceNote.isNullOrBlank()) " • ${p.referenceNote}" else ""}",
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                formatTaka(p.amountPaid),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF00875A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    DossierTab.LOYALTY -> {
                        if (dossier.loyaltyTransactions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No loyalty transactions yet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            val dtFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(dossier.loyaltyTransactions) { t ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(t.reason.ifBlank { t.type }, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(
                                                    dtFormat.format(Date(t.timestamp)),
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = if (t.pointsChange >= 0) "+${t.pointsChange} pts" else "${t.pointsChange} pts",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (t.pointsChange >= 0) Color(0xFF00875A) else MaterialTheme.colorScheme.error
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
