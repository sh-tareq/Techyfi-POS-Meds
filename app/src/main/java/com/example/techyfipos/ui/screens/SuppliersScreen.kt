package com.example.techyfipos.ui.screens

import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import com.example.techyfipos.data.AppPermissions
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Supplier
import com.example.techyfipos.ui.components.InwardStockDialog
import com.example.techyfipos.ui.components.SupplierCard
import com.example.techyfipos.ui.components.Realistic3DSupplierWarehouseIcon
import com.example.techyfipos.ui.components.Realistic3DCargoInflowTruckIcon
import com.example.techyfipos.ui.components.Realistic3DSupplierDueShieldIcon
import com.example.techyfipos.ui.components.Realistic3DInwardInvoiceIcon
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.ui.viewmodel.SupplierViewModel

private enum class SupplierDueFilter {
    ALL,
    HAS_DUE,
    SETTLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(
    supplierViewModel: SupplierViewModel,
    onBack: () -> Unit,
    isOwner: Boolean = true,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    authViewModel: AuthViewModel? = null,
    isTablet: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canManageSuppliers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SUPPLIERS_MANAGE)
    var showSupervisorAuthDialog by remember { mutableStateOf(false) }
    var pendingSupervisorAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSupervisorDesc by remember { mutableStateOf("") }

    fun runWithSupplierPermission(description: String, action: () -> Unit) {
        if (canManageSuppliers) {
            action()
        } else if (authViewModel != null) {
            pendingSupervisorDesc = description
            pendingSupervisorAction = action
            showSupervisorAuthDialog = true
        } else {
            Toast.makeText(context, context.getString(R.string.perm_suppliers_manage_desc), Toast.LENGTH_SHORT).show()
        }
    }
    val suppliers by supplierViewModel.allSuppliers.collectAsStateWithLifecycle(emptyList())
    val products by supplierViewModel.allProducts.collectAsStateWithLifecycle(emptyList())
    val purchaseInvoices by supplierViewModel.allPurchaseInvoices.collectAsStateWithLifecycle(emptyList())
    val supplierPayments by supplierViewModel.allSupplierPayments.collectAsStateWithLifecycle(emptyList())
    val purchaseSavedMessage = stringResource(R.string.supplier_purchase_saved)
    val supplierSavedMessage = stringResource(R.string.supplier_saved)
    val paymentSavedMessage = stringResource(R.string.supplier_payment_saved)
    val supplierOperationFailedMessage = stringResource(R.string.supplier_operation_failed)

    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var supplierToEdit by remember { mutableStateOf<Supplier?>(null) }
    var supplierToDelete by remember { mutableStateOf<Supplier?>(null) }
    var selectedSupplierForInflow by remember { mutableStateOf<Supplier?>(null) }
    var showSupplierPickerForInflow by remember { mutableStateOf(false) }
    var selectedSupplierForPayDue by remember { mutableStateOf<Supplier?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var dueFilter by remember { mutableStateOf(SupplierDueFilter.ALL) }
    var showAllInvoices by remember { mutableStateOf(false) }
    var showAllPayments by remember { mutableStateOf(false) }
    val ledgerDateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val totalPurchased = remember(suppliers) { suppliers.sumOf { it.totalPurchased } }
    val totalDue = remember(suppliers) { suppliers.sumOf { it.totalDue } }

    val filteredSuppliers = remember(suppliers, searchQuery, dueFilter) {
        suppliers.filter { sup ->
            val matchesSearch = searchQuery.isBlank() ||
                    sup.companyName.contains(searchQuery, ignoreCase = true) ||
                    sup.name.contains(searchQuery, ignoreCase = true) ||
                    sup.phone.contains(searchQuery, ignoreCase = true) ||
                    (sup.address?.contains(searchQuery, ignoreCase = true) == true)

            val matchesDue = when (dueFilter) {
                SupplierDueFilter.HAS_DUE -> sup.totalDue > 0
                SupplierDueFilter.SETTLED -> sup.totalDue <= 0
                SupplierDueFilter.ALL -> true
            }

            matchesSearch && matchesDue
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = stringResource(R.string.supplier_screen_title),
                        metricBadge = pluralStringResource(R.plurals.supplier_active_vendors_count, suppliers.size, suppliers.size),
                        badgeColor = PosSectionColors.Suppliers,
                        isTablet = isTablet,
                        icon = { size -> Realistic3DSupplierTruckIcon(size = size) }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Live Hub Status Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF0F9FF),
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0284C7))
                            )
                            Text(
                                text = stringResource(R.string.supplier_active_hub),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
        ) {
            // Master Supplier Portfolio Hero Card
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF0F172A),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF0C4A6E), Color(0xFF0284C7))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Top Row: 3D Warehouse + Title + Live Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Realistic3DSupplierWarehouseIcon(size = 46.dp)
                                    }

                                    Column {
                                        Text(
                                            text = stringResource(R.string.supplier_portfolio_label),
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.supplier_active_vendors_count,
                                                suppliers.size,
                                                suppliers.size
                                            ),
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF38BDF8).copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = stringResource(R.string.supplier_live_tracked),
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // 2-Column Telemetry Strip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Inflow Tile
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Realistic3DCargoInflowTruckIcon(size = 30.dp)
                                        Column {
                                            Text(stringResource(R.string.supplier_total_inflow), color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(formatTaka(totalPurchased), color = Color(0xFF38BDF8), fontSize = 13.5.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }

                                // Outstanding Due Tile
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.Black.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Realistic3DSupplierDueShieldIcon(size = 30.dp)
                                        Column {
                                            Text(stringResource(R.string.supplier_outstanding_due), color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = formatTaka(totalDue),
                                                color = if (totalDue > 0) Color(0xFFFBBF24) else Color(0xFF34D399),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                            }

                            // Dual Primary CTAs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { runWithSupplierPermission("Add Supplier") { showAddSupplierDialog = true } },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.supplier_add_vendor), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                Button(
                                    onClick = {
                                        runWithSupplierPermission("Receive Stock") {
                                            if (suppliers.isEmpty()) {
                                                showAddSupplierDialog = true
                                            } else if (suppliers.size == 1) {
                                                selectedSupplierForInflow = suppliers.first()
                                            } else {
                                                showSupplierPickerForInflow = true
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(stringResource(R.string.supplier_quick_inward), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }

            // Compact Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.supplier_search_hint), fontSize = 12.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear), tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Segmented Filter Switcher
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // 1. All Vendors Tab
                        val isAll = dueFilter == SupplierDueFilter.ALL
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (isAll) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isAll) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1.1f)
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable { dueFilter = SupplierDueFilter.ALL }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.supplier_filter_all),
                                    fontSize = 11.sp,
                                    fontWeight = if (isAll) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isAll) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isAll) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "${suppliers.size}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isAll) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // 2. Pending Due Tab
                        val isDue = dueFilter == SupplierDueFilter.HAS_DUE
                        val dueCount = suppliers.count { it.totalDue > 0 }
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (isDue) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isDue) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1.3f)
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable { dueFilter = SupplierDueFilter.HAS_DUE }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.supplier_filter_has_due),
                                    fontSize = 11.sp,
                                    fontWeight = if (isDue) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isDue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDue) Color(0xFFD97706) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$dueCount",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDue) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // 3. Settled Tab
                        val isSettled = dueFilter == SupplierDueFilter.SETTLED
                        val settledCount = suppliers.count { it.totalDue <= 0 }
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSettled) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSettled) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1.2f)
                                .defaultMinSize(minHeight = 44.dp)
                                .clickable { dueFilter = SupplierDueFilter.SETTLED }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.supplier_filter_settled),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSettled) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSettled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSettled) Color(0xFF059669) else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$settledCount",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSettled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section Header Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.supplier_directory_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.supplier_records_count,
                            filteredSuppliers.size,
                            filteredSuppliers.size
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Empty State or List
            if (filteredSuppliers.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Realistic3DCargoInflowTruckIcon(size = 54.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty() || dueFilter != SupplierDueFilter.ALL) stringResource(R.string.supplier_empty_search_title) else stringResource(R.string.supplier_empty_title),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.supplier_empty_subtitle),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (searchQuery.isBlank() && dueFilter == SupplierDueFilter.ALL) {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { runWithSupplierPermission("Add Supplier") { showAddSupplierDialog = true } },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(stringResource(R.string.supplier_add_first_vendor), fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredSuppliers, key = { it.id }) { supplier ->
                    SupplierCard(
                        supplier = supplier,
                        onAddInflow = { runWithSupplierPermission("Receive Stock") { selectedSupplierForInflow = supplier } },
                        onPayDue = { runWithSupplierPermission("Record Supplier Payment") { selectedSupplierForPayDue = supplier } },
                        onEdit = { runWithSupplierPermission("Edit Supplier") { supplierToEdit = supplier } },
                        onDelete = { runWithSupplierPermission("Delete Supplier") { supplierToDelete = supplier } }
                    )
                }
            }

            if (purchaseInvoices.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.supplier_recent_purchase_ledger),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val displayedInvoices = if (showAllInvoices) purchaseInvoices else purchaseInvoices.take(20)
                items(displayedInvoices, key = { "invoice-${it.id}" }) { invoice ->
                    val supplierName = suppliers.firstOrNull { it.id == invoice.supplierId }?.companyName
                        ?: stringResource(R.string.supplier_unknown_vendor)
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("#${invoice.invoiceNumber}", fontWeight = FontWeight.Bold)
                                Text(formatTaka(invoice.totalAmount), fontWeight = FontWeight.ExtraBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(supplierName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text(ledgerDateFormat.format(Date(invoice.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(
                                stringResource(
                                    R.string.supplier_purchase_ledger_payment,
                                    formatTaka(invoice.paidAmount), formatTaka(invoice.dueAmount), invoice.paymentMethod
                                ),
                                fontSize = 11.5.sp
                            )
                            if (invoice.itemsSummary.isNotBlank()) Text(invoice.itemsSummary, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (!showAllInvoices && purchaseInvoices.size > 20) {
                    item(key = "view_all_invoices") {
                        TextButton(
                            onClick = { showAllInvoices = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.supplier_view_all_invoices, purchaseInvoices.size))
                        }
                    }
                }
            }
            if (supplierPayments.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.supplier_recent_payment_ledger),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val displayedPayments = if (showAllPayments) supplierPayments else supplierPayments.take(20)
                items(displayedPayments, key = { "payment-${it.id}" }) { payment ->
                    val supplierName = suppliers.firstOrNull { it.id == payment.supplierId }?.companyName
                        ?: stringResource(R.string.supplier_unknown_vendor)
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(supplierName, fontWeight = FontWeight.Bold)
                                Text(payment.paymentMethod, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ledgerDateFormat.format(Date(payment.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                payment.referenceNote?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp) }
                            }
                            Text(formatTaka(payment.amountPaid), fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
                        }
                    }
                }
                if (!showAllPayments && supplierPayments.size > 20) {
                    item(key = "view_all_payments") {
                        TextButton(
                            onClick = { showAllPayments = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.supplier_view_all_payments, supplierPayments.size))
                        }
                    }
                }
            }
        }
    }

    // Dialog: Delete Supplier Confirmation & Balance Check (D2, D3)
    if (supplierToDelete != null) {
        val s = supplierToDelete!!
        val hasBalance = kotlin.math.abs(s.totalDue) > 0.001
        AlertDialog(
            onDismissRequest = { supplierToDelete = null },
            title = {
                Text(
                    text = if (hasBalance) "Cannot Delete Supplier" else stringResource(R.string.confirm_delete),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (hasBalance) {
                    Text("Cannot delete supplier \"${s.companyName}\" because they have an active balance of ${formatTaka(s.totalDue)}. Settle the balance before deleting.")
                } else {
                    Text("Are you sure you want to delete supplier \"${s.companyName}\"? This action cannot be undone.")
                }
            },
            confirmButton = {
                if (!hasBalance) {
                    Button(
                        onClick = {
                            supplierViewModel.deleteSupplier(s) { result ->
                                result.onFailure {
                                    Toast.makeText(context, it.message ?: "Failed to delete supplier", Toast.LENGTH_LONG).show()
                                }
                            }
                            supplierToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                } else {
                    Button(onClick = { supplierToDelete = null }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            },
            dismissButton = {
                if (!hasBalance) {
                    TextButton(onClick = { supplierToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // Dialog: Add New Supplier
    if (showAddSupplierDialog) {
        var name by remember { mutableStateOf("") }
        var companyName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddSupplierDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Realistic3DSupplierWarehouseIcon(size = 32.dp)
                    Text(stringResource(R.string.supplier_add_dialog_title), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text(stringResource(R.string.supplier_company_name_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_company_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.supplier_contact_person_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_contact_person_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.supplier_phone_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_phone_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.supplier_address_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_address_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.supplier_notes_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_notes_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (companyName.isNotBlank()) {
                            supplierViewModel.addSupplier(
                                name = name.ifBlank { companyName },
                                companyName = companyName,
                                phone = phone.ifBlank { "N/A" },
                                address = address.ifBlank { null },
                                notes = notes.ifBlank { null },
                                onResult = { result ->
                                    result.onSuccess {
                                        showAddSupplierDialog = false
                                        android.widget.Toast.makeText(context, supplierSavedMessage, android.widget.Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, it.localizedMessage ?: supplierOperationFailedMessage, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.supplier_save_vendor), fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSupplierDialog = false }) {
                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Dialog: Edit Supplier
    supplierToEdit?.let { currentSupplier ->
        var name by remember(currentSupplier) { mutableStateOf(currentSupplier.name) }
        var companyName by remember(currentSupplier) { mutableStateOf(currentSupplier.companyName) }
        var phone by remember(currentSupplier) { mutableStateOf(currentSupplier.phone) }
        var address by remember(currentSupplier) { mutableStateOf(currentSupplier.address ?: "") }
        var notes by remember(currentSupplier) { mutableStateOf(currentSupplier.notes ?: "") }

        AlertDialog(
            onDismissRequest = { supplierToEdit = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Realistic3DSupplierWarehouseIcon(size = 32.dp)
                    Text(stringResource(R.string.supplier_edit_title), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text(stringResource(R.string.supplier_company_name_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_company_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.supplier_contact_person_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_contact_person_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.supplier_phone_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_phone_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.supplier_address_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_address_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.supplier_notes_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_notes_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (companyName.isNotBlank()) {
                            supplierViewModel.updateSupplier(
                                currentSupplier.copy(
                                    name = name.ifBlank { companyName },
                                    companyName = companyName,
                                    phone = phone.ifBlank { "N/A" },
                                    address = address.ifBlank { null },
                                    notes = notes.ifBlank { null },
                                    updatedAt = System.currentTimeMillis()
                                ),
                                onResult = { result ->
                                    result.onSuccess {
                                        supplierToEdit = null
                                        android.widget.Toast.makeText(context, supplierSavedMessage, android.widget.Toast.LENGTH_SHORT).show()
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, it.localizedMessage ?: supplierOperationFailedMessage, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { supplierToEdit = null }) {
                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Dialog: Inward Stock
    selectedSupplierForInflow?.let { sup ->
        InwardStockDialog(
            supplier = sup,
            products = products,
            onDismiss = { selectedSupplierForInflow = null },
            onSave = { invNo, total, paid, items, method, stockLines ->
                supplierViewModel.recordPurchase(
                    supplierId = sup.id,
                    invoiceNumber = invNo,
                    totalAmount = total,
                    paidAmount = paid,
                    itemsSummary = items,
                    paymentMethod = method,
                    stockLines = stockLines,
                    onResult = { result ->
                        result.onSuccess {
                            selectedSupplierForInflow = null
                            android.widget.Toast.makeText(context, purchaseSavedMessage, android.widget.Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            android.widget.Toast.makeText(context, it.localizedMessage ?: supplierOperationFailedMessage, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        )
    }

    if (showSupplierPickerForInflow) {
        var pickerSearchQuery by remember { mutableStateOf("") }
        val filteredSuppliersForInflow = remember(suppliers, pickerSearchQuery) {
            if (pickerSearchQuery.isBlank()) suppliers
            else suppliers.filter {
                it.name.contains(pickerSearchQuery, ignoreCase = true) ||
                it.phone.contains(pickerSearchQuery) ||
                it.companyName.contains(pickerSearchQuery, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showSupplierPickerForInflow = false },
            title = {
                Text(
                    text = stringResource(R.string.supplier_select_for_inflow_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    OutlinedTextField(
                        value = pickerSearchQuery,
                        onValueChange = { pickerSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.supplier_search_hint), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (filteredSuppliersForInflow.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.supplier_no_matching_found),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            items(filteredSuppliersForInflow, key = { it.id }) { sup ->
                                Surface(
                                    onClick = {
                                        showSupplierPickerForInflow = false
                                        selectedSupplierForInflow = sup
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sup.name.ifBlank { sup.companyName }, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            if (sup.companyName.isNotBlank() && sup.name.isNotBlank()) {
                                                Text(sup.companyName, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                            if (sup.phone.isNotBlank()) {
                                                Text(sup.phone, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                        Text(
                                            formatTaka(sup.totalDue),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (sup.totalDue > 0) MaterialTheme.colorScheme.error else Color(0xFF059669)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSupplierPickerForInflow = false
                    showAddSupplierDialog = true
                }) {
                    Text(stringResource(R.string.supplier_add_vendor), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupplierPickerForInflow = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dialog: Pay Supplier Due
    selectedSupplierForPayDue?.let { sup ->
        var payAmountText by remember { mutableStateOf("") }
        var payNote by remember { mutableStateOf("") }
        var payMethod by remember { mutableStateOf("Cash") }

        AlertDialog(
            onDismissRequest = { selectedSupplierForPayDue = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Realistic3DSupplierDueShieldIcon(size = 32.dp)
                    Text(stringResource(R.string.supplier_pay_due_dialog_title), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.supplier_vendor_label, sup.companyName),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(R.string.supplier_outstanding_due_amount, formatTaka(sup.totalDue)),
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = payAmountText,
                        onValueChange = { payAmountText = it },
                        label = { Text(stringResource(R.string.supplier_amount_to_pay_label)) },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("Cash", "bKash", "Nagad", "Rocket", "Bank").forEach { method ->
                            FilterChip(
                                selected = payMethod == method,
                                onClick = { payMethod = method },
                                label = { Text(method, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = payNote,
                        onValueChange = { payNote = it },
                        label = { Text(stringResource(R.string.supplier_payment_note_label)) },
                        placeholder = { Text(stringResource(R.string.supplier_payment_note_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = payAmountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            supplierViewModel.paySupplierDue(sup, amount, payMethod, payNote) { result ->
                                result.onSuccess {
                                    selectedSupplierForPayDue = null
                                    android.widget.Toast.makeText(context, paymentSavedMessage, android.widget.Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    android.widget.Toast.makeText(context, it.localizedMessage ?: supplierOperationFailedMessage, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text(stringResource(R.string.record_payment), fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedSupplierForPayDue = null }) {
                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSupervisorAuthDialog && authViewModel != null) {
        SupervisorAuthDialog(
            title = stringResource(R.string.users_supervisor_auth_title),
            actionDescription = pendingSupervisorDesc.ifBlank { stringResource(R.string.perm_suppliers_manage_desc) },
            authViewModel = authViewModel,
            onDismiss = {
                showSupervisorAuthDialog = false
                pendingSupervisorAction = null
            },
            onAuthorized = { supervisor ->
                showSupervisorAuthDialog = false
                val action = pendingSupervisorAction
                pendingSupervisorAction = null
                action?.invoke()
            }
        )
    }
}
