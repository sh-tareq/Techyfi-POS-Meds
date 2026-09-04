package com.example.techyfipos.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.ui.viewmodel.CustomerFilterSegment
import com.example.techyfipos.ui.viewmodel.CustomerSortOption
import com.example.techyfipos.ui.viewmodel.CustomerViewModel
import com.example.techyfipos.utils.formatTaka
import java.io.File
import java.io.FileWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customerViewModel: CustomerViewModel,
    onBack: () -> Unit,
    onStartSaleForCustomer: (Customer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customers by customerViewModel.filteredCustomers.collectAsStateWithLifecycle()
    val totalCount by customerViewModel.totalCustomersCount.collectAsStateWithLifecycle()
    val totalDues by customerViewModel.totalDuesAmount.collectAsStateWithLifecycle()
    val vipCount by customerViewModel.vipCustomersCount.collectAsStateWithLifecycle()
    val repeatPercent by customerViewModel.repeatCustomersPercent.collectAsStateWithLifecycle()

    val searchQuery by customerViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by customerViewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by customerViewModel.selectedSort.collectAsStateWithLifecycle()

    val dossierData by customerViewModel.selectedCustomerDossier.collectAsStateWithLifecycle()
    val isLoadingDossier by customerViewModel.isLoadingDossier.collectAsStateWithLifecycle()

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val exportSuccessMessage = stringResource(R.string.customer_export_excel_success)
    val exportFailedPrefix = stringResource(R.string.customer_export_failed, "").trimEnd()
    val customerSavedMessage = stringResource(R.string.customer_saved_success)

    val exportCustomersExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val success = customerViewModel.exportCustomersExcel(output)
                        if (success) {
                            Toast.makeText(context, exportSuccessMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "$exportFailedPrefix ${e.localizedMessage.orEmpty()}".trim(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.customers_screen_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.customers_count_subtitle, totalCount),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Sort Menu
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customer_sort_recent)) },
                                onClick = {
                                    customerViewModel.selectedSort.value = CustomerSortOption.RECENT_VISIT
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customer_sort_lifetime_spend)) },
                                onClick = {
                                    customerViewModel.selectedSort.value = CustomerSortOption.LIFETIME_SPEND
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customer_sort_highest_due)) },
                                onClick = {
                                    customerViewModel.selectedSort.value = CustomerSortOption.HIGHEST_DUE
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customer_sort_orders_count)) },
                                onClick = {
                                    customerViewModel.selectedSort.value = CustomerSortOption.ORDERS_COUNT
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customer_sort_name)) },
                                onClick = {
                                    customerViewModel.selectedSort.value = CustomerSortOption.NAME_ASC
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    // Export Menu (Excel / CSV)
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.customer_export_title))
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customer_export_excel_option)) },
                                leadingIcon = { Text("📊", fontSize = 16.sp) },
                                onClick = {
                                    showExportMenu = false
                                    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    exportCustomersExcelLauncher.launch("customers-$dateStr.xlsx")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customer_export_csv_option)) },
                                leadingIcon = { Text("📄", fontSize = 16.sp) },
                                onClick = {
                                    showExportMenu = false
                                    exportCustomerCsv(context, customers)
                                }
                            )
                        }
                    }

                    // Add Customer Button
                    FilledIconButton(
                        onClick = { showAddCustomerDialog = true },
                        modifier = Modifier.padding(end = 4.dp).size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer", modifier = Modifier.size(17.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { customerViewModel.searchQuery.value = it },
                placeholder = { Text(stringResource(R.string.customer_search_placeholder), fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { customerViewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // KPI Overview Metrics Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMetricCard(
                    title = stringResource(R.string.customers_kpi_total),
                    value = "$totalCount",
                    color = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.People
                )
                KpiMetricCard(
                    title = stringResource(R.string.customers_kpi_due),
                    value = formatTaka(totalDues),
                    color = if (totalDues > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    icon = Icons.Default.Warning
                )
                KpiMetricCard(
                    title = stringResource(R.string.customers_kpi_vip),
                    value = "$vipCount",
                    color = Color(0xFF8B5CF6),
                    icon = Icons.Default.Stars
                )
                KpiMetricCard(
                    title = stringResource(R.string.customers_kpi_repeat),
                    value = "$repeatPercent%",
                    color = Color(0xFF00875A),
                    icon = Icons.Default.Repeat
                )
            }

            // Segment Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    CustomerFilterSegment.ALL to stringResource(R.string.customer_filter_all),
                    CustomerFilterSegment.WITH_DUES to stringResource(R.string.customer_filter_dues),
                    CustomerFilterSegment.VIP to stringResource(R.string.customer_filter_vip),
                    CustomerFilterSegment.FREQUENT to stringResource(R.string.customer_filter_frequent),
                    CustomerFilterSegment.WHOLESALE to stringResource(R.string.customer_filter_wholesale),
                    CustomerFilterSegment.RECENT to stringResource(R.string.customer_filter_recent)
                ).forEach { (segment, title) ->
                    val isSelected = selectedFilter == segment
                    FilterChip(
                        selected = isSelected,
                        onClick = { customerViewModel.selectedFilter.value = segment },
                        label = { Text(title, fontSize = 11.5.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Customer List
            if (customers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(44.dp))
                        Text(
                            text = stringResource(R.string.customers_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(customers, key = { it.phone }) { customer ->
                        CustomerCard(
                            customer = customer,
                            onClick = {
                                customerViewModel.loadCustomerDossier(customer.phone)
                            }
                        )
                    }
                }
            }
        }
    }

    // Customer Dossier Sheet
    if (dossierData != null) {
        CustomerDossierSheet(
            dossier = dossierData!!,
            onDismiss = { customerViewModel.clearDossier() },
            onStartSale = onStartSaleForCustomer,
            onEditCustomer = { customer ->
                customerViewModel.clearDossier()
                customerToEdit = customer
            }
        )
    }

    // Add / Edit Customer Dialog
    if (showAddCustomerDialog || customerToEdit != null) {
        val editingCustomer = customerToEdit
        CustomerAddEditDialog(
            customer = customerToEdit,
            onDismiss = {
                showAddCustomerDialog = false
                customerToEdit = null
            },
            onSave = { phone, name, email, address, notes, tag, creditLimit ->
                customerViewModel.upsertCustomer(
                    phone = phone,
                    name = name,
                    email = email,
                    address = address,
                    notes = notes,
                    tag = tag,
                    creditLimit = creditLimit,
                    onSuccess = {
                        Toast.makeText(context, customerSavedMessage, Toast.LENGTH_SHORT).show()
                        showAddCustomerDialog = false
                        val wasEditing = editingCustomer != null
                        customerToEdit = null
                        if (wasEditing) {
                            customerViewModel.loadCustomerDossier(phone)
                        }
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.widthIn(min = 100.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Column {
                Text(title, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
            }
        }
    }
}

@Composable
fun CustomerCard(
    customer: Customer,
    onClick: () -> Unit
) {
    val initials = remember(customer.name) {
        customer.name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase().ifBlank { "C" }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when (customer.tag) {
                                    "VIP" -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                    "Wholesale" -> Color(0xFF0284C7).copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = when (customer.tag) {
                                "VIP" -> Color(0xFF8B5CF6)
                                "Wholesale" -> Color(0xFF0284C7)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = customer.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customer.totalDue > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                 ) {
                                    Text(
                                        text = stringResource(R.string.customer_badge_due, formatTaka(customer.totalDue)),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "📞 ${customer.phone}${if (!customer.address.isNullOrBlank()) " • ${customer.address}" else ""}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.customer_kpi_lifetime_spend)}: ${formatTaka(customer.lifetimeSpent)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stringResource(R.string.customer_kpi_orders)}: ${customer.totalOrders}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (customer.loyaltyPoints > 0) {
                    Text(
                        text = "⭐ ${customer.loyaltyPoints} pts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5A100)
                    )
                } else {
                    Text(
                        text = "🏷️ ${customer.tag}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAddEditDialog(
    customer: Customer?,
    onDismiss: () -> Unit,
    onSave: (phone: String, name: String, email: String?, address: String?, notes: String?, tag: String, creditLimit: Double) -> Unit
) {
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var email by remember { mutableStateOf(customer?.email ?: "") }
    var address by remember { mutableStateOf(customer?.address ?: "") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }
    var tag by remember { mutableStateOf(customer?.tag ?: "Retail") }
    var creditLimitStr by remember { mutableStateOf(if ((customer?.creditLimit ?: 0.0) > 0) customer!!.creditLimit.toString() else "") }

    val tagsList = listOf("Retail", "Wholesale", "VIP", "Corporate", "Staff", "Defaulter")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (customer == null) stringResource(R.string.customer_add_title) else stringResource(R.string.customer_edit_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    enabled = customer == null, // Phone is PrimaryKey
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = creditLimitStr,
                    onValueChange = { creditLimitStr = it },
                    label = { Text("Credit Limit (৳)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                // Tag Selector
                Text("Customer Tag / Segment:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tagsList.forEach { t ->
                        FilterChip(
                            selected = tag == t,
                            onClick = { tag = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Special Notes / Remarks") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = creditLimitStr.toDoubleOrNull() ?: 0.0
                    onSave(phone, name, email, address, notes, tag, limit)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

fun exportCustomerCsv(context: Context, customers: List<Customer>) {
    try {
        val file = File(context.cacheDir, "customers_export_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.append("Phone,Name,Tag,TotalDue,CreditLimit,TotalOrders,LifetimeSpent,LoyaltyPoints,LoyaltyTier,Address,Notes\n")
            for (c in customers) {
                writer.append("\"${c.phone}\",\"${c.name}\",\"${c.tag}\",${c.totalDue},${c.creditLimit},${c.totalOrders},${c.lifetimeSpent},${c.loyaltyPoints},\"${c.loyaltyTier}\",\"${c.address ?: ""}\",\"${c.notes ?: ""}\"\n")
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.customer_export_csv_chooser_title)))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, context.getString(R.string.customer_export_failed, e.localizedMessage ?: ""), Toast.LENGTH_SHORT).show()
    }
}
