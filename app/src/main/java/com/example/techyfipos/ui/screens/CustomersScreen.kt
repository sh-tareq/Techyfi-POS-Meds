package com.example.techyfipos.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.techyfipos.theme.extendedColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.ui.viewmodel.CustomerFilterSegment
import com.example.techyfipos.ui.viewmodel.CustomerSortOption
import com.example.techyfipos.ui.viewmodel.CustomerViewModel
import com.example.techyfipos.ui.viewmodel.DuplicateCustomerPair
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.getLocalizedRepositoryErrorMessage
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.utils.DateFormatters
import com.example.techyfipos.utils.openPhoneDialer
import com.example.techyfipos.utils.openWhatsAppChat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customerViewModel: CustomerViewModel,
    settingsViewModel: com.example.techyfipos.ui.viewmodel.SettingsViewModel? = null,
    authViewModel: com.example.techyfipos.ui.viewmodel.AuthViewModel? = null,
    onBack: () -> Unit,
    onStartSaleForCustomer: (Customer) -> Unit,
    isTablet: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val customers by customerViewModel.filteredCustomers.collectAsStateWithLifecycle()
    val totalCount by customerViewModel.totalCustomersCount.collectAsStateWithLifecycle()
    val totalDues by customerViewModel.totalDuesAmount.collectAsStateWithLifecycle()
    val vipCount by customerViewModel.vipCustomersCount.collectAsStateWithLifecycle()
    val repeatPercent by customerViewModel.repeatCustomersPercent.collectAsStateWithLifecycle()
    val avgSpend by customerViewModel.averageCustomerSpend.collectAsStateWithLifecycle()

    val searchQuery by customerViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by customerViewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by customerViewModel.selectedSort.collectAsStateWithLifecycle()
    val isTableView by customerViewModel.isTableView.collectAsStateWithLifecycle()
    val selectedPhones by customerViewModel.selectedCustomerPhones.collectAsStateWithLifecycle()
    val duplicatePairs by customerViewModel.duplicatePairs.collectAsStateWithLifecycle()

    val dossierData by customerViewModel.selectedCustomerDossier.collectAsStateWithLifecycle()

    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showBulkTagDialog by remember { mutableStateOf(false) }
    var collisionMergePair by remember { mutableStateOf<com.example.techyfipos.ui.viewmodel.DuplicateCustomerPair?>(null) }

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
                    PosTopAppBarTitle(
                        title = stringResource(R.string.customers_screen_title),
                        metricBadge = stringResource(R.string.customers_metric_badge_total, totalCount),
                        badgeColor = PosSectionColors.Customers,
                        isTablet = isTablet,
                        icon = { size -> Realistic3DCustomerHubIcon(size = size) }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (isTablet) {
                        // Tablet mode: Show full tool deck side-by-side
                        IconButton(
                            onClick = { customerViewModel.toggleTableView() },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (isTableView) Icons.Default.ViewAgenda else Icons.Default.TableChart,
                                contentDescription = if (isTableView) stringResource(R.string.customer_view_cards) else stringResource(R.string.customer_view_table),
                                tint = if (isTableView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Sort Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_recent)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.RECENT_VISIT)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_lifetime_spend)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.LIFETIME_SPEND)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_highest_due)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.HIGHEST_DUE)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_orders_count)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.ORDERS_COUNT)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_loyalty)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.LOYALTY_POINTS)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_name)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.NAME_ASC)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_store_credit)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.STORE_CREDIT)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        // Export Menu (Excel / CSV)
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.customer_export_title))
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_export_excel_option)) },
                                    leadingIcon = { Icon(Icons.Default.TableChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showExportMenu = false
                                        val dateStr = DateFormatters.formatISO()
                                        exportCustomersExcelLauncher.launch("customers-$dateStr.xlsx")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_export_csv_option)) },
                                    leadingIcon = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showExportMenu = false
                                        scope.launch { exportCustomerCsv(context, customers) }
                                    }
                                )
                            }
                        }
                    } else {
                        // Phone mode: Consolidated Actions to guarantee zero title truncation
                        // 1. Sort Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.customer_sort_cd), modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_recent)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.RECENT_VISIT)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_lifetime_spend)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.LIFETIME_SPEND)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_highest_due)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.HIGHEST_DUE)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_orders_count)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.ORDERS_COUNT)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_loyalty)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.LOYALTY_POINTS)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_name)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.NAME_ASC)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_sort_store_credit)) },
                                    onClick = {
                                        customerViewModel.setSelectedSort(CustomerSortOption.STORE_CREDIT)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        // 2. Overflow Menu (View Switcher & Export Tools)
                        var showPhoneMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showPhoneMoreMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options), modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(
                                expanded = showPhoneMoreMenu,
                                onDismissRequest = { showPhoneMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isTableView) stringResource(R.string.customer_view_cards) else stringResource(R.string.customer_view_table)) },
                                    leadingIcon = {
                                        Icon(
                                            if (isTableView) Icons.Default.ViewAgenda else Icons.Default.TableChart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        showPhoneMoreMenu = false
                                        customerViewModel.toggleTableView()
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_export_excel_option)) },
                                    leadingIcon = { Text("📊", fontSize = 16.sp) },
                                    onClick = {
                                        showPhoneMoreMenu = false
                                        val dateStr = DateFormatters.formatISO()
                                        exportCustomersExcelLauncher.launch("customers-$dateStr.xlsx")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.customer_export_csv_option)) },
                                    leadingIcon = { Text("📄", fontSize = 16.sp) },
                                    onClick = {
                                        showPhoneMoreMenu = false
                                        scope.launch { exportCustomerCsv(context, customers) }
                                    }
                                )
                            }
                        }
                    }

                    // Add Customer Button (Responsive for Tablet & Phone)
                    if (isTablet) {
                        Button(
                            onClick = { showAddCustomerDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.customer_add_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        FilledIconButton(
                            onClick = { showAddCustomerDialog = true },
                            modifier = Modifier.padding(end = 4.dp).size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = stringResource(R.string.customer_add_cd), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                // Search Bar
                PosSearchBox(
                    query = searchQuery,
                    onQueryChange = { customerViewModel.setSearchQuery(it) },
                    placeholderText = stringResource(R.string.customer_search_placeholder),
                    accentColor = PosSectionColors.Customers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                // KPI Overview Metrics
                if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
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
                        KpiMetricCard(
                            title = stringResource(R.string.customer_kpi_aov),
                            value = formatTaka(avgSpend),
                            color = Color(0xFF0284C7),
                            icon = Icons.Default.AttachMoney
                        )
                    }
                } else {
                    // Mobile 2x2 Grid Layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                KpiMetricCard(
                                    title = stringResource(R.string.customers_kpi_total),
                                    value = "$totalCount",
                                    color = MaterialTheme.colorScheme.primary,
                                    icon = Icons.Default.People,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                KpiMetricCard(
                                    title = stringResource(R.string.customers_kpi_due),
                                    value = formatTaka(totalDues),
                                    color = if (totalDues > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                    icon = Icons.Default.Warning,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                KpiMetricCard(
                                    title = stringResource(R.string.customers_kpi_vip),
                                    value = "$vipCount",
                                    color = Color(0xFF8B5CF6),
                                    icon = Icons.Default.Stars,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                KpiMetricCard(
                                    title = stringResource(R.string.customers_kpi_repeat),
                                    value = "$repeatPercent%",
                                    color = Color(0xFF00875A),
                                    icon = Icons.Default.Repeat,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Duplicate Customer Detection Smart Alert Banner
                if (duplicatePairs.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⚡", fontSize = 16.sp)
                                Column {
                                    Text(
                                        text = stringResource(R.string.customer_dedup_banner_title, duplicatePairs.size),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFF59E0B)
                                    )
                                    Text(
                                        text = stringResource(R.string.customer_dedup_banner_desc),
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Button(
                                onClick = { showMergeDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.customer_btn_review_merge),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF451A03)
                                )
                            }
                        }
                    }
                }

                // Segment Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        CustomerFilterSegment.ALL to stringResource(R.string.customer_filter_all),
                        CustomerFilterSegment.WITH_STORE_CREDIT to stringResource(R.string.customer_filter_store_credit),
                        CustomerFilterSegment.WITH_DUES to stringResource(R.string.customer_filter_dues),
                        CustomerFilterSegment.VIP to stringResource(R.string.customer_filter_vip),
                        CustomerFilterSegment.FREQUENT to stringResource(R.string.customer_filter_frequent),
                        CustomerFilterSegment.WHOLESALE to stringResource(R.string.customer_filter_wholesale),
                        CustomerFilterSegment.RECENT to stringResource(R.string.customer_filter_recent),
                        CustomerFilterSegment.NEW_CUSTOMERS to stringResource(R.string.customer_filter_new),
                        CustomerFilterSegment.DEFAULTER to stringResource(R.string.customer_filter_defaulter)
                    ).forEach { (segment, title) ->
                        val isSelected = selectedFilter == segment
                        FilterChip(
                            selected = isSelected,
                            onClick = { customerViewModel.setSelectedFilter(segment) },
                            label = { Text(title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                // Main Customer Content (Table View vs Card View)
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
                } else if (isTableView) {
                    CustomerDataTable(
                        customers = customers,
                        selectedPhones = selectedPhones,
                        onToggleSelect = { phone -> customerViewModel.toggleCustomerSelection(phone) },
                        onSelectAll = { customerViewModel.selectAll(customers.map { it.phone }) },
                        onCustomerClick = { customer -> customerViewModel.loadCustomerDossier(customer.phone) },
                        onStartSale = onStartSaleForCustomer,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customers, key = { it.phone }) { customer ->
                            val isSelected = selectedPhones.contains(customer.phone)
                            CustomerCard(
                                customer = customer,
                                isSelected = isSelected,
                                onToggleSelect = { customerViewModel.toggleCustomerSelection(customer.phone) },
                                onClick = {
                                    customerViewModel.loadCustomerDossier(customer.phone)
                                },
                                onStartSale = { onStartSaleForCustomer(customer) }
                            )
                        }
                    }
                }
            }

            // Floating Bulk Action Deck at Bottom
            AnimatedVisibility(
                visible = selectedPhones.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.customer_bulk_selected, selectedPhones.size),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val target = selectedPhones.singleOrNull()
                                    if (target != null) {
                                        openWhatsAppChat(context, target)
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.customer_whatsapp_single_only), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = selectedPhones.size == 1,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("💬 ${stringResource(R.string.customer_action_whatsapp)}", fontSize = 11.sp)
                            }

                            FilledTonalButton(
                                onClick = { showBulkTagDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("🏷️ ${stringResource(R.string.customer_bulk_tag)}", fontSize = 11.sp)
                            }

                            IconButton(
                                onClick = { customerViewModel.clearCustomerSelection() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.customer_bulk_deselect), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Customer Dossier Sheet
    if (dossierData != null) {
        CustomerDossierSheet(
            dossier = dossierData!!,
            authViewModel = authViewModel,
            onDismiss = { customerViewModel.clearDossier() },
            onStartSale = onStartSaleForCustomer,
            onEditCustomer = { customer ->
                customerViewModel.clearDossier()
                customerToEdit = customer
            },
            onAddNote = { text, category ->
                customerViewModel.addCustomerNote(
                    phone = dossierData!!.customer.phone,
                    noteText = text,
                    category = category,
                    onSuccess = { Toast.makeText(context, context.getString(R.string.customer_note_saved), Toast.LENGTH_SHORT).show() },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                )
            },
            onDeleteNote = { index ->
                customerViewModel.deleteCustomerNote(
                    phone = dossierData!!.customer.phone,
                    noteIndex = index,
                    onSuccess = { Toast.makeText(context, context.getString(R.string.customer_note_deleted), Toast.LENGTH_SHORT).show() },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                )
            },
            onAdjustCredit = { delta, note ->
                customerViewModel.adjustCustomerStoreCredit(
                    phone = dossierData!!.customer.phone,
                    delta = delta,
                    note = note,
                    onSuccess = { Toast.makeText(context, context.getString(R.string.store_credit_adjusted_success), Toast.LENGTH_SHORT).show() },
                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                )
            },
            settingsViewModel = settingsViewModel
        )
    }

    // Add / Edit Customer Dialog
    if (showAddCustomerDialog || customerToEdit != null) {
        val editingCustomer = customerToEdit
        var hasActiveVouchers by remember(customerToEdit?.phone) { mutableStateOf(false) }
        LaunchedEffect(customerToEdit?.phone) {
            val phone = customerToEdit?.phone
            hasActiveVouchers = if (phone != null) {
                customerViewModel.hasActiveVouchers(phone)
            } else {
                false
            }
        }
        CustomerAddEditDialog(
            customer = customerToEdit,
            hasActiveVouchers = hasActiveVouchers,
            onDismiss = {
                showAddCustomerDialog = false
                customerToEdit = null
            },
            onSave = { phone, name, email, address, notes, tag, creditLimit ->
                val cleanPhone = phone.trim()
                if (editingCustomer != null && cleanPhone != editingCustomer.phone) {
                    customerViewModel.migrateCustomerPhone(
                        oldPhone = editingCustomer.phone,
                        newPhone = cleanPhone,
                        name = name,
                        email = email,
                        address = address,
                        notes = notes,
                        tag = tag,
                        creditLimit = creditLimit,
                        onSuccess = { finalPhone ->
                            Toast.makeText(context, customerSavedMessage, Toast.LENGTH_SHORT).show()
                            showAddCustomerDialog = false
                            customerToEdit = null
                            customerViewModel.loadCustomerDossier(finalPhone)
                        },
                        onCollision = { existingCust ->
                            collisionMergePair = com.example.techyfipos.ui.viewmodel.DuplicateCustomerPair(
                                customerA = existingCust,
                                customerB = editingCustomer,
                                matchReason = context.getString(R.string.customer_match_phone_collision, existingCust.phone)
                            )
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    customerViewModel.upsertCustomer(
                        phone = cleanPhone,
                        name = name,
                        email = email,
                        address = address,
                        notes = notes,
                        tag = tag,
                        creditLimit = creditLimit,
                        isEdit = editingCustomer != null,
                        onSuccess = {
                            Toast.makeText(context, customerSavedMessage, Toast.LENGTH_SHORT).show()
                            showAddCustomerDialog = false
                            val wasEditing = editingCustomer != null
                            customerToEdit = null
                            if (wasEditing) {
                                customerViewModel.loadCustomerDossier(cleanPhone)
                            }
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            },
            onDelete = if (editingCustomer != null) {
                {
                    customerViewModel.deleteCustomerPermanently(
                        phone = editingCustomer.phone,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.customer_deleted_toast), Toast.LENGTH_SHORT).show()
                            showAddCustomerDialog = false
                            customerToEdit = null
                        },
                        onError = { err ->
                            val msg = when {
                                err.contains("outstanding dues", ignoreCase = true) ->
                                    context.getString(R.string.customer_cannot_delete_due_desc, editingCustomer.name, formatTaka(editingCustomer.totalDue))
                                err.contains("store credit balance", ignoreCase = true) ->
                                    context.getString(R.string.customer_cannot_delete_credit_desc, editingCustomer.name, formatTaka(editingCustomer.storeCredit))
                                err.contains("active voucher balance", ignoreCase = true) ->
                                    context.getString(R.string.customer_cannot_delete_voucher_desc, editingCustomer.name)
                                else -> err
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } else null
        )
    }

    // Collision Merge Dialog
    if (collisionMergePair != null) {
        CustomerMergeDialog(
            pair = collisionMergePair!!,
            onDismiss = { collisionMergePair = null },
            onConfirmMerge = { masterPhone, secondaryPhone ->
                customerViewModel.mergeCustomers(
                    masterPhone = masterPhone,
                    secondaryPhone = secondaryPhone,
                    onSuccess = {
                        Toast.makeText(context, context.getString(R.string.customer_merge_success), Toast.LENGTH_SHORT).show()
                        collisionMergePair = null
                        showAddCustomerDialog = false
                        customerToEdit = null
                        customerViewModel.loadCustomerDossier(masterPhone)
                    },
                    onError = { err ->
                        Toast.makeText(context, context.getString(R.string.customer_merge_failed, err), Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Duplicate Customers Merge Dialog
    if (showMergeDialog && duplicatePairs.isNotEmpty()) {
        val pair = duplicatePairs.first()
        CustomerMergeDialog(
            pair = pair,
            onDismiss = { showMergeDialog = false },
            onConfirmMerge = { masterPhone, secondaryPhone ->
                customerViewModel.mergeCustomers(
                    masterPhone = masterPhone,
                    secondaryPhone = secondaryPhone,
                    onSuccess = {
                        Toast.makeText(context, context.getString(R.string.customer_merge_success), Toast.LENGTH_SHORT).show()
                        showMergeDialog = false
                    },
                    onError = { err ->
                        Toast.makeText(context, context.getString(R.string.customer_merge_failed, err), Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Bulk Tag Assignment Dialog
    if (showBulkTagDialog) {
        BulkAssignTagDialog(
            count = selectedPhones.size,
            onDismiss = { showBulkTagDialog = false },
            onApplyTag = { tag ->
                customerViewModel.bulkAssignTag(
                    tag = tag,
                    onSuccess = {
                        Toast.makeText(context, context.getString(R.string.customer_bulk_tag_success, selectedPhones.size), Toast.LENGTH_SHORT).show()
                        showBulkTagDialog = false
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

@Composable
fun CustomerDataTable(
    customers: List<Customer>,
    selectedPhones: Set<String>,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onCustomerClick: (Customer) -> Unit,
    onStartSale: (Customer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val horizontalScrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScrollState)
                .widthIn(min = 688.dp)
        ) {
            // Table Header Row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedPhones.size == customers.size && customers.isNotEmpty(),
                        onCheckedChange = { onSelectAll() },
                        modifier = Modifier.size(48.dp)
                    )
                    Text(stringResource(R.string.customer_col_customer), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(170.dp))
                    Text(stringResource(R.string.customer_col_tag), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(100.dp))
                    Text(stringResource(R.string.customer_col_spend_due), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(140.dp))
                    Text(stringResource(R.string.customer_col_orders), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                    Text(stringResource(R.string.customer_col_actions), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(140.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(customers, key = { it.phone }) { customer ->
                    val isSelected = selectedPhones.contains(customer.phone)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCustomerClick(customer) }
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect(customer.phone) },
                            modifier = Modifier.size(48.dp)
                        )

                        // Customer Info
                        Column(modifier = Modifier.width(170.dp)) {
                            Text(
                                text = customer.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "📞 ${customer.phone}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Segment Tag
                        Box(modifier = Modifier.width(100.dp)) {
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
                                    text = getLocalizedCustomerTag(customer.tag),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (customer.tag) {
                                        "VIP" -> Color(0xFF8B5CF6)
                                        "Wholesale" -> Color(0xFF0284C7)
                                        "Defaulter" -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Spend / Due
                        Column(modifier = Modifier.width(140.dp)) {
                            Text(
                                text = formatTaka(customer.lifetimeSpent),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (customer.totalDue > 0) {
                                Text(
                                    text = stringResource(R.string.customers_table_due_label, formatTaka(customer.totalDue)),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(stringResource(R.string.customers_table_zero_due), fontSize = 11.sp, color = MaterialTheme.extendedColors.success)
                            }
                            if (customer.storeCredit > 0) {
                                Text(
                                    text = "🪙 ${formatTaka(customer.storeCredit)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.extendedColors.success
                                )
                            }
                        }

                        // Orders
                        Column(modifier = Modifier.width(90.dp)) {
                            Text(stringResource(R.string.customers_table_orders_count, customer.totalOrders), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.customers_table_loyalty_points, customer.loyaltyPoints), fontSize = 11.sp, color = Color(0xFFE5A100))
                        }

                        // Action Shortcuts
                        Row(
                            modifier = Modifier.width(140.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { onStartSale(customer) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = stringResource(R.string.customer_start_sale_cd), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                            }
                            IconButton(
                                onClick = { openWhatsAppChat(context, customer.phone) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Text("💬", fontSize = 14.sp)
                            }
                            IconButton(
                                onClick = { onCustomerClick(customer) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.customer_profile_cd), modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.widthIn(min = 96.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Column {
                Text(title, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                Text(value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
            }
        }
    }
}

@Composable
fun CustomerCard(
    customer: Customer,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit,
    onStartSale: () -> Unit = {}
) {
    val context = LocalContext.current
    val initials = remember(customer.name) {
        customer.name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase().ifBlank { "C" }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.size(48.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
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
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = when (customer.tag) {
                                "VIP" -> Color(0xFF8B5CF6)
                                "Wholesale" -> Color(0xFF0284C7)
                                "Defaulter" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = customer.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (customer.totalDue > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.customer_badge_due, formatTaka(customer.totalDue)),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (customer.storeCredit > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.extendedColors.successContainer
                                ) {
                                    Text(
                                        text = "🪙 ${formatTaka(customer.storeCredit)}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.extendedColors.onSuccessContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "📞 ${customer.phone}${if (!customer.address.isNullOrBlank()) " • ${customer.address}" else ""}",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onStartSale,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = stringResource(R.string.customer_start_sale_cd), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    val whatsappCd = stringResource(R.string.customer_whatsapp_chat_cd)
                    IconButton(
                        onClick = { openWhatsAppChat(context, customer.phone) },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = whatsappCd }
                    ) {
                        Text("💬", fontSize = 15.sp)
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.customer_kpi_lifetime_spend)}: ${formatTaka(customer.lifetimeSpent)}",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${stringResource(R.string.customer_kpi_orders)}: ${customer.totalOrders}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (customer.loyaltyPoints > 0) {
                    Text(
                        text = stringResource(R.string.customer_pts_display, customer.loyaltyPoints),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5A100)
                    )
                } else {
                    Text(
                        text = "🏷️ ${getLocalizedCustomerTag(customer.tag)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerMergeDialog(
    pair: DuplicateCustomerPair,
    onDismiss: () -> Unit,
    onConfirmMerge: (masterPhone: String, secondaryPhone: String) -> Unit
) {
    var selectedMasterPhone by remember { mutableStateOf(pair.customerA.phone) }
    val isMasterA = selectedMasterPhone == pair.customerA.phone
    val master = if (isMasterA) pair.customerA else pair.customerB
    val secondary = if (isMasterA) pair.customerB else pair.customerA

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔗", fontSize = 20.sp)
                Text(stringResource(R.string.customer_merge_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.customer_match_reason_label, pair.matchReason),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF59E0B)
                )
                Text(
                    text = stringResource(R.string.customer_merge_master_prompt),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Option Card A
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(if (isMasterA) 2.dp else 1.dp, if (isMasterA) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)),
                    color = if (isMasterA) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { selectedMasterPhone = pair.customerA.phone }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = isMasterA, onClick = { selectedMasterPhone = pair.customerA.phone })
                        Column {
                            Text(pair.customerA.name, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(stringResource(R.string.customer_merge_phone_tag, pair.customerA.phone, getLocalizedCustomerTag(pair.customerA.tag)), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.customer_merge_orders_spend, pair.customerA.totalOrders, formatTaka(pair.customerA.lifetimeSpent)), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Option Card B
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(if (!isMasterA) 2.dp else 1.dp, if (!isMasterA) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)),
                    color = if (!isMasterA) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().clickable { selectedMasterPhone = pair.customerB.phone }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = !isMasterA, onClick = { selectedMasterPhone = pair.customerB.phone })
                        Column {
                            Text(pair.customerB.name, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(stringResource(R.string.customer_merge_phone_tag, pair.customerB.phone, getLocalizedCustomerTag(pair.customerB.tag)), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.customer_merge_orders_spend, pair.customerB.totalOrders, formatTaka(pair.customerB.lifetimeSpent)), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Consolidated Preview
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.customer_merged_preview), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.customer_combined_orders, master.totalOrders + secondary.totalOrders), fontSize = 10.sp)
                        Text(stringResource(R.string.customer_combined_spend, formatTaka(master.lifetimeSpent + secondary.lifetimeSpent)), fontSize = 10.sp)
                        Text(stringResource(R.string.customer_combined_dues, formatTaka(master.totalDue + secondary.totalDue)), fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmMerge(master.phone, secondary.phone) }
            ) {
                Text(stringResource(R.string.customer_merge_confirm_btn), fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontSize = 12.sp)
            }
        }
    )
}

private val CUSTOMER_TAG_OPTIONS = listOf(
    "Retail" to R.string.customer_tag_retail,
    "Wholesale" to R.string.customer_tag_wholesale,
    "VIP" to R.string.customer_tag_vip,
    "Corporate" to R.string.customer_tag_corporate,
    "Staff" to R.string.customer_tag_staff,
    "Defaulter" to R.string.customer_tag_defaulter
)

@Composable
fun getLocalizedCustomerTag(tag: String): String {
    val resId = CUSTOMER_TAG_OPTIONS.firstOrNull { it.first.equals(tag, ignoreCase = true) }?.second
    return if (resId != null) stringResource(resId) else tag
}

@Composable
fun BulkAssignTagDialog(
    count: Int,
    onDismiss: () -> Unit,
    onApplyTag: (String) -> Unit
) {
    var selectedTag by remember { mutableStateOf("VIP") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(stringResource(R.string.customer_bulk_tag_dialog_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.customer_select_tag_count, count), fontSize = 12.sp)
                CUSTOMER_TAG_OPTIONS.forEach { (tagKey, stringResId) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTag = tagKey }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = selectedTag == tagKey, onClick = { selectedTag = tagKey })
                        Text(stringResource(stringResId), fontSize = 12.5.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApplyTag(selectedTag) }) {
                Text(stringResource(R.string.customer_apply_tag), fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), fontSize = 12.sp)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAddEditDialog(
    customer: Customer?,
    onDismiss: () -> Unit,
    onSave: (phone: String, name: String, email: String?, address: String?, notes: String?, tag: String, creditLimit: Double) -> Unit,
    onDelete: (() -> Unit)? = null,
    hasActiveVouchers: Boolean = false
) {
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var email by remember { mutableStateOf(customer?.email ?: "") }
    var address by remember { mutableStateOf(customer?.address ?: "") }
    var notes by remember { mutableStateOf(customer?.notes ?: "") }
    var tag by remember { mutableStateOf(customer?.tag ?: "Retail") }
    var creditLimitStr by remember { mutableStateOf(if ((customer?.creditLimit ?: 0.0) > 0) customer!!.creditLimit.toString() else "") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDueBlockedDialog by remember { mutableStateOf(false) }
    var showCreditBlockedDialog by remember { mutableStateOf(false) }
    var showVoucherBlockedDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
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
                    label = { Text(stringResource(R.string.customer_dialog_phone_req)) },
                    singleLine = true,
                    enabled = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.customer_dialog_name_req)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.customer_dialog_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(stringResource(R.string.customer_dialog_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = creditLimitStr,
                    onValueChange = { creditLimitStr = it },
                    label = { Text(stringResource(R.string.customer_dialog_credit_limit)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.customer_dialog_tag_segment), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CUSTOMER_TAG_OPTIONS.forEach { (tagKey, stringResId) ->
                        FilterChip(
                            selected = tag == tagKey,
                            onClick = { tag = tagKey },
                            label = { Text(stringResource(stringResId), fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.customer_dialog_notes)) },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (customer != null && onDelete != null) {
                    TextButton(
                        onClick = {
                            if (customer.totalDue > 0.001) {
                                showDueBlockedDialog = true
                            } else if (customer.storeCredit > 0.001) {
                                showCreditBlockedDialog = true
                            } else if (hasActiveVouchers) {
                                showVoucherBlockedDialog = true
                            } else {
                                showDeleteConfirmDialog = true
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )

    if (showDueBlockedDialog && customer != null) {
        AlertDialog(
            onDismissRequest = { showDueBlockedDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.customer_cannot_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.customer_cannot_delete_due_desc, customer.name, formatTaka(customer.totalDue))) },
            confirmButton = {
                Button(onClick = { showDueBlockedDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showCreditBlockedDialog && customer != null) {
        AlertDialog(
            onDismissRequest = { showCreditBlockedDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.customer_cannot_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.customer_cannot_delete_credit_desc, customer.name, formatTaka(customer.storeCredit))) },
            confirmButton = {
                Button(onClick = { showCreditBlockedDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showVoucherBlockedDialog && customer != null) {
        AlertDialog(
            onDismissRequest = { showVoucherBlockedDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.customer_cannot_delete_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.customer_cannot_delete_voucher_desc, customer.name)) },
            confirmButton = {
                Button(onClick = { showVoucherBlockedDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showDeleteConfirmDialog && customer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.confirm_delete), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.customer_delete_confirm_desc, customer.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun csvEscape(s: String): String = "\"${s.replace("\"", "\"\"")}\""

suspend fun exportCustomerCsv(context: Context, customers: List<Customer>) = withContext(Dispatchers.IO) {
    try {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "customers_export_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.append("Phone,Name,Tag,TotalDue,CreditLimit,TotalOrders,LifetimeSpent,LoyaltyPoints,LoyaltyTier,Address,Notes\n")
            for (c in customers) {
                writer.append("${csvEscape(c.phone)},${csvEscape(c.name)},${csvEscape(c.tag)},${c.totalDue},${c.creditLimit},${c.totalOrders},${c.lifetimeSpent},${c.loyaltyPoints},${csvEscape(c.loyaltyTier)},${csvEscape(c.address ?: "")},${csvEscape(c.notes ?: "")}\n")
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
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.customer_export_csv_chooser_title)))
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.customer_export_failed, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_SHORT).show()
        }
    }
}
