package com.example.techyfipos.ui.screens

import com.example.techyfipos.ui.dialogs.*
import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import com.example.techyfipos.utils.PaymentMath
import com.example.techyfipos.data.db.getFormattedPaymentMethod
import com.example.techyfipos.ui.viewmodel.calculateDiscountValue
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.SaleWithItems
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.ui.viewmodel.CartViewModel
import com.example.techyfipos.ui.viewmodel.SaleReturnSummary
import com.example.techyfipos.ui.viewmodel.InvoiceProfitSummary
import com.example.techyfipos.ui.viewmodel.SalesViewModel
import androidx.annotation.StringRes
import com.example.techyfipos.ui.viewmodel.SettingsViewModel
import com.example.techyfipos.utils.*
import kotlinx.coroutines.launch
import java.util.*

private enum class HistoryReportPeriod(@get:StringRes val labelRes: Int) {
    TODAY(R.string.history_period_today),
    WEEK(R.string.history_period_week),
    MONTH(R.string.history_period_month),
    YEAR(R.string.history_period_year),
    ALL_TIME(R.string.history_period_all_time),
    CUSTOM(R.string.dues_period_custom_range)
}

private enum class HistoryCustomerFilter(@get:StringRes val labelRes: Int) {
    ALL(R.string.history_customer_all),
    SAVED_CUSTOMER(R.string.history_customer_saved),
    WALK_IN(R.string.history_customer_walkin)
}

private enum class HistorySortOption(@get:StringRes val labelRes: Int) {
    NEWEST_FIRST(R.string.history_sort_newest),
    HIGHEST_TOTAL(R.string.history_sort_highest_total),
    LOWEST_TOTAL(R.string.history_sort_lowest_total),
    HIGHEST_PROFIT(R.string.invoice_sort_profit_high),
    LOWEST_PROFIT(R.string.invoice_sort_profit_low)
}

private fun rangeForPeriod(period: HistoryReportPeriod, customStart: Long, customEnd: Long, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
    return when (period) {
        HistoryReportPeriod.TODAY -> startOfDay(now) to endOfDay(now) + 1
        HistoryReportPeriod.WEEK -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            while (cal.get(Calendar.DAY_OF_WEEK) != cal.firstDayOfWeek) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
            startOfDay(cal.timeInMillis) to endOfDay(now) + 1
        }
        HistoryReportPeriod.MONTH -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1) }
            startOfDay(cal.timeInMillis) to endOfDay(now) + 1
        }
        HistoryReportPeriod.YEAR -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_YEAR, 1) }
            startOfDay(cal.timeInMillis) to endOfDay(now) + 1
        }
        HistoryReportPeriod.ALL_TIME -> 0L to Long.MAX_VALUE
        HistoryReportPeriod.CUSTOM -> startOfDay(customStart) to endOfDay(customEnd) + 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHistoryScreen(
    salesViewModel: SalesViewModel,
    settingsViewModel: SettingsViewModel? = null,
    cartViewModel: CartViewModel? = null,
    authViewModel: AuthViewModel? = null,
    isOwner: Boolean = true,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    onBack: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToCart: (() -> Unit)? = null,
    isTablet: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val salesWithItems by salesViewModel.historySalesWithItems.collectAsStateWithLifecycle()
    val returnsBySaleId by salesViewModel.returnsBySaleId.collectAsStateWithLifecycle()
    var onlyReturnsFilter by rememberSaveable { mutableStateOf(false) }
    var isStealthMode by rememberSaveable { mutableStateOf(false) }
    var returnTarget by remember { mutableStateOf<SaleWithItems?>(null) }
    var returnAlreadyReturned by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var returnStoreCreditRestoredSoFar by remember { mutableStateOf(0.0) }
    var issuedVoucherCode by remember { mutableStateOf<String?>(null) }
    var issuedVoucherAmount by remember { mutableStateOf(0.0) }
    var issuedVoucherEntity by remember { mutableStateOf<com.example.techyfipos.data.db.StoreCreditVoucher?>(null) }
    val defaultShopName = stringResource(R.string.app_name)
    val defaultAdminName = stringResource(R.string.more_admin_badge)
    val historyShopName = settingsViewModel?.shopName?.collectAsStateWithLifecycle(initialValue = defaultShopName)?.value ?: defaultShopName
    val historyShopPhone = settingsViewModel?.shopPhone?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""
    val historyShopAddress = settingsViewModel?.shopAddress?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""
    val historyInvoiceSettingsData = settingsViewModel?.invoiceSettingsData?.collectAsStateWithLifecycle(initialValue = com.example.techyfipos.data.InvoiceSettingsData())?.value ?: com.example.techyfipos.data.InvoiceSettingsData()

    var showSupervisorAuthDialog by remember { mutableStateOf(false) }
    var pendingSupervisorAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSupervisorDesc by remember { mutableStateOf("") }

    val canReturn = isOwner || isSupervisor || activePermissions.contains(AppPermissions.RETURNS_PROCESS)
    val canDelete = isOwner || isSupervisor || activePermissions.contains(AppPermissions.RETURNS_DELETE_SALE)

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            val currentTime = System.currentTimeMillis()
            value = currentTime
            val cal = Calendar.getInstance().apply {
                timeInMillis = currentTime
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 1)
            }
            val millisUntilMidnight = (cal.timeInMillis - currentTime).coerceAtLeast(1000L)
            kotlinx.coroutines.delay(millisUntilMidnight.coerceAtMost(3600_000L))
        }
    }

    var selectedPeriod by remember { mutableStateOf(HistoryReportPeriod.TODAY) }
    var customStart by remember { mutableStateOf(System.currentTimeMillis()) }
    var customEnd by remember { mutableStateOf(System.currentTimeMillis()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var selectedSaleDetail by remember { mutableStateOf<SaleWithItems?>(null) }
    var deleteTarget by remember { mutableStateOf<SaleWithItems?>(null) }
    var deleteReason by remember { mutableStateOf("") }
    var deleteInProgress by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var selectedPayments by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customerFilter by remember { mutableStateOf(HistoryCustomerFilter.ALL) }
    var minimumTotal by remember { mutableStateOf("") }
    var maximumTotal by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(HistorySortOption.NEWEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }

    var draftPayments by remember { mutableStateOf<Set<String>>(emptySet()) }
    var draftCustomerFilter by remember { mutableStateOf(HistoryCustomerFilter.ALL) }
    var draftMinimumTotal by remember { mutableStateOf("") }
    var draftMaximumTotal by remember { mutableStateOf("") }
    var draftPeriod by remember { mutableStateOf(HistoryReportPeriod.TODAY) }
    var draftCustomStart by remember { mutableStateOf(System.currentTimeMillis()) }
    var draftCustomEnd by remember { mutableStateOf(System.currentTimeMillis()) }

    val (rangeStart, rangeEnd) = remember(selectedPeriod, customStart, customEnd, now) {
        rangeForPeriod(selectedPeriod, customStart, customEnd, now)
    }

    LaunchedEffect(rangeStart, rangeEnd) {
        if (rangeStart == 0L && rangeEnd == Long.MAX_VALUE) {
            salesViewModel.clearHistoryDateRange()
        } else {
            salesViewModel.setHistoryDateRange(rangeStart, rangeEnd)
        }
    }

    val currentLocale = LocalConfiguration.current.locales[0]
    val dateFilteredSales = remember(salesWithItems, rangeStart, rangeEnd) {
        if (rangeStart == 0L && rangeEnd == Long.MAX_VALUE) {
            salesWithItems
        } else {
            salesWithItems.filter { it.sale.timestamp in rangeStart until rangeEnd }
        }
    }

    val filteredSales = remember(
        dateFilteredSales, searchQuery, selectedPayments, customerFilter,
        minimumTotal, maximumTotal, sortOrder, onlyReturnsFilter, returnsBySaleId
    ) {
        val query = searchQuery.trim().lowercase()
        val minimum = minimumTotal.toDoubleOrNull()
        val maximum = maximumTotal.toDoubleOrNull()
        dateFilteredSales.filter { saleWithItems ->
            val sale = saleWithItems.sale
            val matchesSearch = query.isBlank() ||
                sale.id.toString().contains(query) ||
                sale.customerName?.lowercase()?.contains(query) == true ||
                sale.customerPhone?.lowercase()?.contains(query) == true ||
                sale.paymentMethod.lowercase().contains(query) ||
                saleWithItems.items.any {
                    it.productName.lowercase().contains(query) || it.productBarcode.lowercase().contains(query)
                }
            val matchesPayment = selectedPayments.isEmpty() ||
                sale.paymentMethod in selectedPayments ||
                (sale.paymentMethod == "Split" && sale.splitPaymentJson != null && selectedPayments.any { selectedMethod ->
                    sale.splitPaymentJson.contains(selectedMethod, ignoreCase = true)
                })
            val isWalkIn = sale.customerName.isNullOrBlank() && sale.customerPhone.isNullOrBlank()
            val matchesCustomer = when (customerFilter) {
                HistoryCustomerFilter.SAVED_CUSTOMER -> !isWalkIn
                HistoryCustomerFilter.WALK_IN -> isWalkIn
                HistoryCustomerFilter.ALL -> true
            }
            val matchesReturns = !onlyReturnsFilter || (returnsBySaleId[sale.id]?.hasReturns == true)
            matchesSearch && matchesPayment && matchesCustomer && matchesReturns &&
                (minimum == null || sale.grandTotal >= minimum) &&
                (maximum == null || sale.grandTotal <= maximum)
        }.let { matches ->
            when (sortOrder) {
                HistorySortOption.HIGHEST_TOTAL -> matches.sortedByDescending { it.sale.grandTotal }
                HistorySortOption.LOWEST_TOTAL -> matches.sortedBy { it.sale.grandTotal }
                HistorySortOption.HIGHEST_PROFIT -> matches.sortedByDescending {
                    salesViewModel.calculateInvoiceProfit(it, returnsBySaleId[it.sale.id]).netProfit
                }
                HistorySortOption.LOWEST_PROFIT -> matches.sortedBy {
                    salesViewModel.calculateInvoiceProfit(it, returnsBySaleId[it.sale.id]).netProfit
                }
                HistorySortOption.NEWEST_FIRST -> matches.sortedByDescending { it.sale.timestamp }
            }
        }
    }

    val historyProfit by produceState(initialValue = 0.0 to 0.0, filteredSales, rangeStart, rangeEnd) {
        val report = salesViewModel.buildReport(rangeStart, rangeEnd, filteredSales)
        value = report.totalProfit to (if (report.totalRevenue != 0.0) report.totalProfit / report.totalRevenue * 100.0 else 0.0)
    }

    val resultTotal = filteredSales.sumOf { it.sale.grandTotal }
    val resultItems = filteredSales.sumOf { sale -> sale.items.sumOf { it.quantity } }
    val cashCount = filteredSales.count { it.sale.paymentMethod.equals("Cash", ignoreCase = true) }
    val cashSplitPercent = if (filteredSales.isNotEmpty()) ((cashCount.toDouble() / filteredSales.size) * 100).toInt() else 0
    val filterCount = selectedPayments.size +
        (if (customerFilter != HistoryCustomerFilter.ALL) 1 else 0) +
        (if (minimumTotal.isNotBlank() || maximumTotal.isNotBlank()) 1 else 0)
    val dateRangeLabel = when (selectedPeriod) {
        HistoryReportPeriod.ALL_TIME -> stringResource(R.string.history_period_all_sales)
        else -> {
            val startText = DateFormatters.formatDate(rangeStart, currentLocale)
            val endText = DateFormatters.formatDate((rangeEnd - 1).coerceAtMost(now), currentLocale)
            if (startText == endText) startText else "$startText – $endText"
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = stringResource(R.string.sales_history),
                        metricBadge = if (filteredSales.isNotEmpty()) stringResource(R.string.sales_history_sales_count, filteredSales.size) else null,
                        badgeColor = PosSectionColors.SalesHistory,
                        isTablet = isTablet,
                        icon = { size -> Realistic3DHistoryClockIcon(size = size) }
                    )
                },
                navigationIcon = {
                    if (!isTablet) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                actions = {
                    if (isOwner) {
                        Surface(
                            onClick = { isStealthMode = !isStealthMode },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isStealthMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, if (isStealthMode) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Realistic3DStealthShieldIcon(isStealthActive = isStealthMode, size = 16.dp)
                                Text(
                                    text = if (isStealthMode) stringResource(R.string.invoice_stealth_mode_on) else stringResource(R.string.invoice_stealth_mode_off),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStealthMode) Color(0xFFFDE68A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = onNavigateToReport,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Realistic3DAnalyticsIcon(size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sales_history_reports_button), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp)
            ) {
            // 💎 Compact Unified Sticky Toolbar (~76dp total)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Row 1: Search Field + Quick Date Pill + 3D Filter Funnel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isBlank()) {
                                        Text(
                                            stringResource(R.string.sales_history_search_placeholder),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear_search_cd),
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Date Pill Button (opens Date Range Picker)
                    Surface(
                        onClick = {
                            val currentMillis = System.currentTimeMillis()
                            customStart = if (selectedPeriod == HistoryReportPeriod.ALL_TIME) currentMillis else rangeStart
                            customEnd = if (selectedPeriod == HistoryReportPeriod.ALL_TIME) currentMillis else (rangeEnd - 1)
                            showStartPicker = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Realistic3DCalendarIcon(size = 16.dp)
                            Text(
                                text = when (selectedPeriod) {
                                    HistoryReportPeriod.ALL_TIME -> stringResource(R.string.history_period_all_time)
                                    HistoryReportPeriod.TODAY -> stringResource(R.string.history_period_today)
                                    HistoryReportPeriod.WEEK -> stringResource(R.string.history_period_week)
                                    HistoryReportPeriod.MONTH -> stringResource(R.string.history_period_month)
                                    HistoryReportPeriod.YEAR -> stringResource(R.string.history_period_year)
                                    HistoryReportPeriod.CUSTOM -> dateRangeLabel
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA),
                                maxLines = 1
                            )
                            if (selectedPeriod != HistoryReportPeriod.ALL_TIME) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFC7D2FE).copy(alpha = 0.6f))
                                        .clickable {
                                            selectedPeriod = HistoryReportPeriod.ALL_TIME
                                            customStart = System.currentTimeMillis()
                                            customEnd = System.currentTimeMillis()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.history_filter_reset),
                                        tint = Color(0xFF4338CA),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF4338CA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // 3D Filter Funnel Button with Badge
                    BadgedBox(
                        badge = {
                            if (filterCount > 0) Badge { Text(filterCount.toString()) }
                        }
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                draftPayments = selectedPayments
                                draftCustomerFilter = customerFilter
                                draftMinimumTotal = minimumTotal
                                draftMaximumTotal = maximumTotal
                                draftPeriod = selectedPeriod
                                draftCustomStart = customStart
                                draftCustomEnd = customEnd
                                showFilters = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Realistic3DFilterIcon(size = 18.dp)
                        }
                    }
                }

                // Row 2: Micro Period Chips + Count & Sort Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        buildList {
                            add(HistoryReportPeriod.ALL_TIME to stringResource(R.string.history_period_all))
                            add(HistoryReportPeriod.TODAY to stringResource(R.string.history_period_today))
                            add(HistoryReportPeriod.WEEK to stringResource(R.string.history_period_week))
                            add(HistoryReportPeriod.MONTH to stringResource(R.string.history_period_month))
                            add(HistoryReportPeriod.YEAR to stringResource(R.string.history_period_year))
                            if (selectedPeriod == HistoryReportPeriod.CUSTOM) {
                                add(HistoryReportPeriod.CUSTOM to dateRangeLabel.ifBlank { stringResource(R.string.history_period_selected_dates) })
                            }
                        }.forEach { (period, label) ->
                            val isSelected = selectedPeriod == period
                            Surface(
                                onClick = { selectedPeriod = period },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = if (isSelected) null else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (selectedPeriod != HistoryReportPeriod.ALL_TIME) {
                            Surface(
                                onClick = {
                                    selectedPeriod = HistoryReportPeriod.ALL_TIME
                                    customStart = System.currentTimeMillis()
                                    customEnd = System.currentTimeMillis()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEE2E2),
                                border = BorderStroke(0.8.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.history_filter_reset),
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.history_filter_reset),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }

                        val periodReturnsCount = remember(dateFilteredSales, returnsBySaleId) {
                            dateFilteredSales.count { returnsBySaleId[it.sale.id]?.hasReturns == true }
                        }
                        if (periodReturnsCount > 0) {
                            Surface(
                                onClick = { onlyReturnsFilter = !onlyReturnsFilter },
                                shape = RoundedCornerShape(8.dp),
                                color = if (onlyReturnsFilter) Color(0xFFEF4444) else Color(0xFFFEF2F2),
                                border = BorderStroke(0.8.dp, if (onlyReturnsFilter) Color(0xFFB91C1C) else Color(0xFFFECACA)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Realistic3DReturnOrbIcon(size = 11.dp, animated = onlyReturnsFilter)
                                    Text(
                                        text = "${stringResource(R.string.invoice_has_returns_filter)} ($periodReturnsCount)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (onlyReturnsFilter) Color.White else Color(0xFFB91C1C)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_sales_count, filteredSales.size),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            TextButton(
                                onClick = { showSortMenu = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.history_sort_button),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                val sortOptions = remember(isOwner) {
                                    val list = mutableListOf(
                                        HistorySortOption.NEWEST_FIRST,
                                        HistorySortOption.HIGHEST_TOTAL,
                                        HistorySortOption.LOWEST_TOTAL
                                    )
                                    if (isOwner) {
                                        list.add(HistorySortOption.HIGHEST_PROFIT)
                                        list.add(HistorySortOption.LOWEST_PROFIT)
                                    }
                                    list
                                }
                                sortOptions.forEach { option ->
                                    val displayLabel = stringResource(option.labelRes)
                                    DropdownMenuItem(
                                        text = { Text(displayLabel) },
                                        onClick = { sortOrder = option; showSortMenu = false },
                                        leadingIcon = if (sortOrder == option) {
                                            { Icon(Icons.Default.Check, null) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.8.dp
            )

            if (filteredSales.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Realistic3DEmptyReceiptIcon(size = 68.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (salesWithItems.isEmpty()) stringResource(R.string.no_sales) else stringResource(R.string.sales_history_no_matches),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.sales_history_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    if (searchQuery.isNotBlank() || filterCount > 0 || selectedPeriod != HistoryReportPeriod.TODAY) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            searchQuery = ""
                            selectedPayments = emptySet()
                            customerFilter = HistoryCustomerFilter.ALL
                            minimumTotal = ""
                            maximumTotal = ""
                            selectedPeriod = HistoryReportPeriod.TODAY
                            customStart = System.currentTimeMillis()
                            customEnd = System.currentTimeMillis()
                        }) { Text(stringResource(R.string.history_clear_search_filters)) }
                    }
                }
            } else {
                val groupedSales = remember(filteredSales, currentLocale) { groupSalesByDay(filteredSales, currentLocale) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 💎 In-Feed Executive Revenue Summary Card (Scrolls away with list)
                    item(key = "revenue_summary") {
                        HistoryRevenueSummaryCard(
                            periodLabel = if (selectedPeriod == HistoryReportPeriod.CUSTOM) stringResource(R.string.history_period_selected_dates) else stringResource(selectedPeriod.labelRes),
                            totalRevenue = resultTotal,
                            transactionCount = filteredSales.size,
                            itemCount = resultItems,
                            cashSplitPercent = cashSplitPercent,
                            grossMargin = historyProfit.first,
                            marginPercent = historyProfit.second,
                            isOwner = isOwner,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    groupedSales.forEach { (dateHeader, salesInGroup) ->
                        item(key = "header_$dateHeader") {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        items(salesInGroup, key = { it.sale.id }) { saleWithItems ->
                            val returnSum = returnsBySaleId[saleWithItems.sale.id]
                            val profitSum = remember(saleWithItems, returnSum, isOwner) {
                                if (isOwner) salesViewModel.calculateInvoiceProfit(saleWithItems, returnSum) else null
                            }
                            SaleCard(
                                saleWithItems = saleWithItems,
                                isOwner = isOwner,
                                isStealthMode = isStealthMode,
                                returnSummary = returnSum,
                                profitSummary = profitSum,
                                onClick = { selectedSaleDetail = saleWithItems }
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (selectedSaleDetail != null) {
        val sale = selectedSaleDetail!!
        SaleDetailDialog(
            saleWithItems = sale,
            settingsViewModel = settingsViewModel,
            salesViewModel = salesViewModel,
            cartViewModel = cartViewModel,
            authViewModel = authViewModel,
            isOwner = isOwner,
            isSupervisor = isSupervisor,
            activePermissions = activePermissions,
            onDismiss = { selectedSaleDetail = null },
            onReturn = {
                selectedSaleDetail = null
                scope.launch {
                    returnAlreadyReturned = salesViewModel.getReturnedQuantities(sale.sale.id)
                    returnStoreCreditRestoredSoFar = salesViewModel.getStoreCreditRestoredSoFar(sale.sale.id)
                    returnTarget = sale
                }
            },
            onDelete = {
                deleteTarget = sale
                selectedSaleDetail = null
            },
            onRequestSupervisorElevation = { desc, action ->
                pendingSupervisorDesc = desc
                pendingSupervisorAction = action
                showSupervisorAuthDialog = true
            },
            onNavigateToCart = onNavigateToCart
        )
    }

    if (showSupervisorAuthDialog && authViewModel != null) {
        SupervisorAuthDialog(
            title = stringResource(R.string.users_supervisor_auth_title),
            actionDescription = pendingSupervisorDesc.ifBlank { stringResource(R.string.users_supervisor_auth_desc) },
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

    deleteTarget?.let { target ->
        val deletedMessage = stringResource(R.string.sales_history_invoice_deleted, target.sale.id)
        val deleteFailedMessage = stringResource(R.string.sales_history_invoice_delete_failed)
        AlertDialog(
            onDismissRequest = {
                if (!deleteInProgress) {
                    deleteTarget = null
                    deleteReason = ""
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.sales_history_delete_invoice_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.sales_history_delete_invoice_message, target.sale.id))
                    OutlinedTextField(
                        value = deleteReason,
                        onValueChange = { deleteReason = it },
                        label = { Text(stringResource(R.string.sales_history_void_reason_label)) },
                        placeholder = { Text(stringResource(R.string.sales_history_void_reason_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !deleteInProgress,
                    onClick = {
                        deleteInProgress = true
                        scope.launch {
                            val reason = deleteReason.ifBlank { context.getString(R.string.sales_history_void_reason_fallback, target.sale.id.toString()) }
                            val deleted = salesViewModel.deleteSaleAndAwait(target.sale.id, reason)
                            deleteInProgress = false
                            if (deleted) {
                                deleteTarget = null
                                deleteReason = ""
                            }
                            Toast.makeText(
                                context,
                                if (deleted) deletedMessage else deleteFailedMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (deleteInProgress) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(enabled = !deleteInProgress, onClick = {
                    deleteTarget = null
                    deleteReason = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    returnTarget?.let { sale ->
        ReturnItemsDialog(
            saleWithItems = sale,
            alreadyReturned = returnAlreadyReturned,
            storeCreditRestoredSoFar = returnStoreCreditRestoredSoFar,
            onDismiss = { returnTarget = null },
            onConfirm = { lines, refundMethod, note, restock, walkinPhone, walkinName, issueVoucher ->
                salesViewModel.processReturn(
                    saleId = sale.sale.id,
                    lines = lines,
                    refundMethod = refundMethod,
                    note = note,
                    restock = restock,
                    walkinPhone = walkinPhone,
                    walkinName = walkinName,
                    issueVoucher = issueVoucher
                ) { success, refundTotal, voucherCode, voucherEntity ->
                    returnTarget = null
                    if (success) {
                        if (voucherEntity != null) {
                            issuedVoucherEntity = voucherEntity
                        } else if (!voucherCode.isNullOrBlank()) {
                            scope.launch {
                                val fetched = salesViewModel.getVoucherByCode(voucherCode)
                                issuedVoucherEntity = fetched ?: com.example.techyfipos.data.db.StoreCreditVoucher(
                                    code = voucherCode,
                                    initialAmount = refundTotal,
                                    remainingBalance = refundTotal,
                                    customerPhone = sale.sale.customerPhone ?: walkinPhone,
                                    sourceSaleId = sale.sale.id,
                                    status = "ACTIVE"
                                )
                            }
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.return_success, formatTaka(refundTotal)),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.return_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    issuedVoucherEntity?.let { voucher ->
        StoreCreditVoucherSlipDialog(
            voucher = voucher,
            shopName = historyShopName,
            shopPhone = historyShopPhone,
            shopAddress = historyShopAddress,
            invoiceSettingsData = historyInvoiceSettingsData,
            onDismiss = { issuedVoucherEntity = null }
        )
    }

    val historyCurrentDayEndUtcMillis = remember(now) {
        val localCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(java.util.Calendar.YEAR, localCal.get(java.util.Calendar.YEAR))
            set(java.util.Calendar.MONTH, localCal.get(java.util.Calendar.MONTH))
            set(java.util.Calendar.DAY_OF_MONTH, localCal.get(java.util.Calendar.DAY_OF_MONTH))
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        utcCal.timeInMillis
    }

    val historySelectableDates = remember(historyCurrentDayEndUtcMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= historyCurrentDayEndUtcMillis
            override fun isSelectableYear(year: Int): Boolean = year <= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = customStart.coerceAtMost(System.currentTimeMillis()),
            selectableDates = historySelectableDates
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        customStart = it.coerceAtMost(System.currentTimeMillis())
                    }
                    showStartPicker = false
                    showEndPicker = true
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = state, title = { Text(stringResource(R.string.history_select_start_date), Modifier.padding(24.dp, 16.dp)) })
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = customEnd.coerceAtMost(System.currentTimeMillis()),
            selectableDates = historySelectableDates
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        customEnd = it.coerceAtMost(System.currentTimeMillis())
                        selectedPeriod = HistoryReportPeriod.CUSTOM
                    }
                    showEndPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = state, title = { Text(stringResource(R.string.history_select_end_date), Modifier.padding(24.dp, 16.dp)) })
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.history_filter_transactions), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.history_filter_payment_method), fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due", "Store Credit", "Split").forEach { method ->
                        val methodLabel = when (method) {
                            "Cash" -> stringResource(R.string.cash)
                            "bKash" -> stringResource(R.string.bkash)
                            "Nagad" -> stringResource(R.string.nagad)
                            "Rocket" -> stringResource(R.string.rocket)
                            "Card" -> stringResource(R.string.card)
                            "Due" -> stringResource(R.string.due)
                            "Store Credit" -> stringResource(R.string.payment_store_credit)
                            "Split" -> stringResource(R.string.cart_split_payment)
                            else -> method
                        }
                        FilterChip(
                            selected = method in draftPayments,
                            onClick = {
                                draftPayments = if (method in draftPayments) draftPayments - method else draftPayments + method
                            },
                            label = { Text(methodLabel) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.history_filter_customer), fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    HistoryCustomerFilter.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = draftCustomerFilter == option,
                            onClick = { draftCustomerFilter = option },
                            shape = SegmentedButtonDefaults.itemShape(index, HistoryCustomerFilter.entries.size),
                            label = { Text(stringResource(option.labelRes), fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.history_filter_sale_total), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftMinimumTotal,
                        onValueChange = { draftMinimumTotal = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(stringResource(R.string.history_filter_min_taka)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draftMaximumTotal,
                        onValueChange = { draftMaximumTotal = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(stringResource(R.string.history_filter_max_taka)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            draftPayments = emptySet()
                            draftCustomerFilter = HistoryCustomerFilter.ALL
                            draftMinimumTotal = ""
                            draftMaximumTotal = ""
                            draftPeriod = HistoryReportPeriod.TODAY
                            draftCustomStart = System.currentTimeMillis()
                            draftCustomEnd = System.currentTimeMillis()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.history_filter_reset)) }
                    Button(
                        onClick = {
                            selectedPayments = draftPayments
                            customerFilter = draftCustomerFilter
                            minimumTotal = draftMinimumTotal
                            maximumTotal = draftMaximumTotal
                            selectedPeriod = draftPeriod
                            customStart = draftCustomStart
                            customEnd = draftCustomEnd
                            showFilters = false
                        },
                        modifier = Modifier.weight(2f)
                    ) { Text(stringResource(R.string.history_filter_apply)) }
                }
            }
        }
    }
}

private fun groupSalesByDay(sales: List<SaleWithItems>, locale: Locale = Locale.getDefault()): Map<String, List<SaleWithItems>> {
    return sales.groupBy { DateFormatters.formatDayGroup(it.sale.timestamp, locale) }
}

@Composable
private fun HistoryRevenueSummaryCard(
    periodLabel: String,
    totalRevenue: Double,
    transactionCount: Int,
    itemCount: Double,
    cashSplitPercent: Int,
    grossMargin: Double,
    marginPercent: Double,
    isOwner: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isCollapsed by rememberSaveable { mutableStateOf(false) }
    // Profit/margin is owner-only elsewhere in the app; a cashier session can never switch into
    // margin mode here even if this state were somehow set.
    var showMarginMode by rememberSaveable { mutableStateOf(false) }
    val canShowMargin = isOwner && showMarginMode
    val avgOrderValue = if (transactionCount > 0) totalRevenue / transactionCount else 0.0
    val estGrossMargin = grossMargin

    val chevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 180f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    Surface(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E1B4B),
        shadowElevation = if (isCollapsed) 3.dp else 6.dp,
        border = BorderStroke(1.dp, Color(0x33C7D2FE))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E1B4B),
                            Color(0xFF312E81),
                            Color(0xFF4338CA)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .padding(horizontal = 16.dp, vertical = if (isCollapsed) 10.dp else 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Tag with 3D Vault Icon, Compact Amount (when collapsed), Metric Switch & Collapse/Expand Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isCollapsed = !isCollapsed },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Realistic3DVaultIcon(size = 18.dp)
                        Text(
                            text = stringResource(R.string.history_revenue_summary_title),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = Color(0xFFC7D2FE)
                        )
                        if (isCollapsed) {
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = Color(0xFF818CF8)
                            )
                            Text(
                                text = if (showMarginMode) formatTaka(estGrossMargin) else formatTaka(totalRevenue),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Interactive Switch Pill (owner-only: cashiers never see the margin toggle)
                        if (isOwner) {
                            Surface(
                                onClick = { showMarginMode = !showMarginMode },
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (canShowMargin) stringResource(R.string.history_metric_net_margin) else stringResource(R.string.history_metric_total_revenue),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = stringResource(R.string.history_switch_metric_desc),
                                        tint = Color(0xFFC7D2FE),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Collapse / Expand Toggle Button
                        Surface(
                            onClick = { isCollapsed = !isCollapsed },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isCollapsed) stringResource(R.string.history_expand_summary_desc) else stringResource(R.string.history_collapse_summary_desc),
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(chevronRotation)
                                )
                            }
                        }
                    }
                }

                // Collapsible Body with AnimatedVisibility
                AnimatedVisibility(
                    visible = !isCollapsed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(6.dp))

                        // Middle Row: Amount & Stat Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (canShowMargin) formatTaka(estGrossMargin) else formatTaka(totalRevenue),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = (-0.6).sp
                                )
                                Text(
                                    text = if (canShowMargin) stringResource(R.string.history_gross_margin_rate, String.format(java.util.Locale.US, "%.1f", marginPercent))
                                           else stringResource(R.string.history_aov_avg_ticket, formatTaka(avgOrderValue)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFA5B4FC)
                                )
                            }

                            // Margin badge is owner-only; a cashier session never sees profit figures here.
                            if (isOwner) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, Color(0x33A5B4FC))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "+${formatTaka(estGrossMargin)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399)
                                        )
                                        Text(
                                            text = stringResource(R.string.history_est_margin_format, String.format(java.util.Locale.US, "%.1f", marginPercent)),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFC7D2FE)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Footer Row: Transactions, Units, Cash Split
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.15f),
                            thickness = 0.8.dp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.history_invoices_label),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC7D2FE)
                                )
                                Text(
                                    text = "$transactionCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.history_units_label),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC7D2FE)
                                )
                                val itemStr = if (itemCount % 1.0 == 0.0) itemCount.toLong().toString() else String.format(Locale.US, "%.2f", itemCount).trimEnd('0').trimEnd('.')
                                Text(
                                    text = stringResource(R.string.history_items_count, itemStr),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(Color.White.copy(alpha = 0.4f), CircleShape)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.history_cash_split_label),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFC7D2FE)
                                )
                                Text(
                                    text = stringResource(R.string.history_cash_split_value, cashSplitPercent),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Payment3DBadge(
    paymentMethod: String,
    modifier: Modifier = Modifier
) {
    val isCash = paymentMethod.equals("Cash", ignoreCase = true)
    val isBkash = paymentMethod.contains("bKash", ignoreCase = true)
    val isNagad = paymentMethod.contains("Nagad", ignoreCase = true)
    val isRocket = paymentMethod.contains("Rocket", ignoreCase = true)
    val isCard = paymentMethod.contains("Card", ignoreCase = true)
    val isDue = paymentMethod.contains("Due", ignoreCase = true)

    val bgColor = when {
        isCash -> Color(0xFFECFDF5)
        isBkash -> Color(0xFFFDF2F8)
        isNagad -> Color(0xFFFFF7ED)
        isRocket -> Color(0xFFFAF5FF)
        isCard -> Color(0xFFEFF6FF)
        isDue -> Color(0xFFFEF2F2)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        isCash -> Color(0xFF047857)
        isBkash -> Color(0xFFBE185D)
        isNagad -> Color(0xFFC2410C)
        isRocket -> Color(0xFF7E22CE)
        isCard -> Color(0xFF1D4ED8)
        isDue -> Color(0xFFB91C1C)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val borderColor = when {
        isCash -> Color(0xFFA7F3D0)
        isBkash -> Color(0xFFFBCFE8)
        isNagad -> Color(0xFFFED7AA)
        isRocket -> Color(0xFFE9D5FF)
        isCard -> Color(0xFFBFDBFE)
        isDue -> Color(0xFFFECACA)
        else -> Color.Transparent
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when {
                isCash -> Realistic3DCashIcon(size = 14.dp)
                isBkash -> Realistic3DMobileWalletIcon(primaryColor = Color(0xFFBE185D), secondaryColor = Color(0xFFDB2777), size = 14.dp)
                isNagad -> Realistic3DMobileWalletIcon(primaryColor = Color(0xFFC2410C), secondaryColor = Color(0xFFEA580C), size = 14.dp)
                isRocket -> Realistic3DMobileWalletIcon(primaryColor = Color(0xFF7E22CE), secondaryColor = Color(0xFFA855F7), size = 14.dp)
                isCard -> Realistic3DCardIcon(size = 14.dp)
                isDue -> Text("⚠️", fontSize = 11.sp)
                else -> Text("💳", fontSize = 11.sp)
            }
            val displayMethod = when (paymentMethod) {
                "Cash" -> stringResource(R.string.cash)
                "bKash" -> stringResource(R.string.bkash)
                "Nagad" -> stringResource(R.string.nagad)
                "Rocket" -> stringResource(R.string.rocket)
                "Card" -> stringResource(R.string.card)
                "Due" -> stringResource(R.string.due)
                "Store Credit" -> stringResource(R.string.payment_store_credit)
                "Split" -> stringResource(R.string.cart_split_payment)
                else -> paymentMethod
            }
            Text(
                text = displayMethod,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun SaleCard(
    saleWithItems: SaleWithItems,
    isOwner: Boolean = false,
    isStealthMode: Boolean = false,
    returnSummary: SaleReturnSummary? = null,
    profitSummary: InvoiceProfitSummary? = null,
    onClick: () -> Unit
) {
    val saleTime = remember(saleWithItems.sale.timestamp) {
        DateFormatters.formatTime(saleWithItems.sale.timestamp)
    }

    val hasReturns = returnSummary?.hasReturns == true
    val refundAmount = returnSummary?.totalRefundAmount ?: 0.0
    val netSaleTotal = (saleWithItems.sale.grandTotal - refundAmount).coerceAtLeast(0.0)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            // Top Row: Invoice #, 3D Payment Badge, and Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "#${saleWithItems.sale.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 3D Payment Badge
                    Payment3DBadge(paymentMethod = saleWithItems.sale.paymentMethod)
                }

                if (isStealthMode) {
                    Text(
                        text = "${formatMoney(0.0).take(1)}••••",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (hasReturns) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatTaka(saleWithItems.sale.grandTotal),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = formatTaka(netSaleTotal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = formatTaka(saleWithItems.sale.grandTotal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (saleWithItems.sale.paymentMethod == "Due") Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Return Alert Badge (Prominent crimson capsule if invoice processed returns)
            if (returnSummary != null && returnSummary.hasReturns) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Realistic3DReturnOrbIcon(size = 13.dp, animated = true)
                            Text(
                                text = stringResource(R.string.invoice_return_processed_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        val refundStr = formatTaka(returnSummary.totalRefundAmount)
                        val itemsCountStr = if (returnSummary.returnedItemsCount % 1.0 == 0.0)
                            returnSummary.returnedItemsCount.toLong().toString()
                        else String.format(Locale.US, "%.1f", returnSummary.returnedItemsCount)
                        Text(
                            text = stringResource(R.string.invoice_return_badge_desc, itemsCountStr, refundStr),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF991B1B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Middle Row: Customer Info with Avatar & Time & Items Count
            val customerName = saleWithItems.sale.customerName?.takeIf { it.isNotBlank() }
            val customerPhone = saleWithItems.sale.customerPhone?.takeIf { it.isNotBlank() }
            val isWalkIn = customerName == null && customerPhone == null
            val totalUnits = saleWithItems.items.sumOf { it.quantity }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Customer Avatar Initial
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (isWalkIn) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isWalkIn) "W" else customerName?.firstOrNull()?.uppercase() ?: "C",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWalkIn) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = when {
                            customerName != null && customerPhone != null -> "$customerName ($customerPhone)"
                            customerName != null -> customerName
                            customerPhone != null -> stringResource(R.string.history_customer_prefix, customerPhone)
                            else -> stringResource(R.string.history_customer_walkin)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val totalUnitsStr = if (totalUnits % 1.0 == 0.0) totalUnits.toLong().toString() else String.format(Locale.US, "%.2f", totalUnits).trimEnd('0').trimEnd('.')
                    Text(
                        text = stringResource(R.string.history_items_count, totalUnitsStr),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = saleTime,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (saleWithItems.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                // Item Pills Summary (single compact row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    saleWithItems.items.take(2).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = item.productName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val qStr = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
                                Text(
                                    text = "x$qStr",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (saleWithItems.items.size > 2) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stringResource(R.string.history_items_more_count, saleWithItems.items.size - 2),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }
            }

            // Owner Profit Pill (Only visible when isOwner == true)
            if (isOwner && profitSummary != null) {
                Spacer(modifier = Modifier.height(5.dp))
                val netProfit = profitSummary.netProfit
                val margin = profitSummary.netMarginPercent
                val isHigh = margin >= 20.0 && netProfit > 0
                val isLow = margin in 0.0..20.0 && netProfit >= 0
                val isLoss = netProfit < 0

                val (profitBg, profitText, profitBorder) = when {
                    isHigh -> Triple(Color(0xFFECFDF5), Color(0xFF047857), Color(0xFFA7F3D0))
                    isLow -> Triple(Color(0xFFFFFBEB), Color(0xFFB45309), Color(0xFFFDE68A))
                    else -> Triple(Color(0xFFFEF2F2), Color(0xFFB91C1C), Color(0xFFFECACA))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = profitBg,
                        border = BorderStroke(0.8.dp, profitBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when {
                                isHigh -> Realistic3DHighMarginGemIcon(size = 12.dp)
                                isLow -> Realistic3DLowMarginCoreIcon(size = 12.dp)
                                else -> Realistic3DLossPrismIcon(size = 12.dp)
                            }
                            val profitVal = if (isStealthMode) "•••• (•••%)" else {
                                val sign = if (netProfit >= 0) "+" else ""
                                "$sign${formatTaka(netProfit)} (${String.format(Locale.US, "%.1f", margin)}%)"
                            }
                            Text(
                                text = "${stringResource(R.string.invoice_profit_label)}: $profitVal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = profitText
                            )
                        }
                    }

                    if (hasReturns) {
                        Text(
                            text = stringResource(R.string.net_after_returns),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF991B1B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleDetailDialog(
    saleWithItems: SaleWithItems,
    settingsViewModel: SettingsViewModel? = null,
    salesViewModel: SalesViewModel? = null,
    cartViewModel: CartViewModel? = null,
    authViewModel: AuthViewModel? = null,
    isOwner: Boolean = true,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onReturn: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onRequestSupervisorElevation: ((actionDesc: String, action: () -> Unit) -> Unit)? = null,
    onNavigateToCart: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    var showEditConfirm by remember { mutableStateOf(false) }

    val canReturn = isOwner || isSupervisor || activePermissions.contains(AppPermissions.RETURNS_PROCESS)
    val canDelete = isOwner || isSupervisor || activePermissions.contains(AppPermissions.RETURNS_DELETE_SALE)
    val canEdit = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_EDIT_INVOICE)

    // BillData.totalOutstandingDue isn't stored on the Sale row — it's only meaningful at the
    // moment of checkout — so reopening an old Due invoice from History must re-fetch the
    // customer's current balance live, rather than showing a stale/zero value.
    val hasDuePortion = remember(saleWithItems) {
        val method = saleWithItems.sale.paymentMethod.trim()
        method.equals("Due", ignoreCase = true) || (
            method.equals("Split", ignoreCase = true) && !saleWithItems.sale.splitPaymentJson.isNullOrBlank() &&
                try {
                    val arr = org.json.JSONArray(saleWithItems.sale.splitPaymentJson)
                    (0 until arr.length()).any { i ->
                        val obj = arr.getJSONObject(i)
                        obj.optString("method").equals("Due", ignoreCase = true) && obj.optDouble("amount", 0.0) > 0.0
                    }
                } catch (e: Exception) { false }
            )
    }
    val liveOutstandingDue by produceState(initialValue = 0.0, saleWithItems, hasDuePortion) {
        val phone = saleWithItems.sale.customerPhone
        value = if (hasDuePortion && !phone.isNullOrBlank() && salesViewModel != null) {
            salesViewModel.getCustomerDue(phone)?.outstandingAmount ?: 0.0
        } else 0.0
    }
    val defaultShopName = stringResource(R.string.app_name)
    val defaultAdminName = stringResource(R.string.more_admin_badge)
    val shopName = settingsViewModel?.shopName?.collectAsStateWithLifecycle(initialValue = defaultShopName)?.value ?: defaultShopName
    val shopAddress = settingsViewModel?.shopAddress?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""
    val shopPhone = settingsViewModel?.shopPhone?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""
    val shopLogoUri = settingsViewModel?.shopLogoUri?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""
    val facebookUrl = settingsViewModel?.facebookUrl?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""
    val qrCodeInfo = settingsViewModel?.qrCodeInfo?.collectAsStateWithLifecycle(initialValue = "")?.value ?: ""
    val cashierName = settingsViewModel?.cashierName?.collectAsStateWithLifecycle(initialValue = defaultAdminName)?.value ?: defaultAdminName
    val invoiceSettingsData = settingsViewModel?.invoiceSettingsData?.collectAsStateWithLifecycle(initialValue = com.example.techyfipos.data.InvoiceSettingsData())?.value ?: com.example.techyfipos.data.InvoiceSettingsData()

    var voucherForSale by remember { mutableStateOf<com.example.techyfipos.data.db.StoreCreditVoucher?>(null) }
    var showVoucherSlipDialog by remember { mutableStateOf(false) }

    val returnsBySaleId by salesViewModel?.returnsBySaleId?.collectAsStateWithLifecycle(emptyMap()) ?: remember { mutableStateOf(emptyMap()) }
    val currentReturnSummary = returnsBySaleId[saleWithItems.sale.id]

    LaunchedEffect(saleWithItems.sale.id) {
        if (salesViewModel != null) {
            val v = salesViewModel.getVoucherBySaleId(saleWithItems.sale.id)
            if (v != null) {
                voucherForSale = v
            } else if (!saleWithItems.sale.splitPaymentJson.isNullOrBlank()) {
                try {
                    val arr = org.json.JSONArray(saleWithItems.sale.splitPaymentJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val code = obj.optString("voucherCode", "")
                        if (code.isNotBlank()) {
                            voucherForSale = salesViewModel.getVoucherByCode(code)
                            if (voucherForSale != null) break
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val billData = remember(saleWithItems, shopName, shopAddress, shopPhone, shopLogoUri, facebookUrl, qrCodeInfo, cashierName, invoiceSettingsData, liveOutstandingDue, voucherForSale, currentReturnSummary) {
        var appliedSc: Double? = null
        var vCode: String? = voucherForSale?.code
        var vAmt: Double? = null
        var vRemaining: Double? = voucherForSale?.remainingBalance
        if (!saleWithItems.sale.splitPaymentJson.isNullOrBlank()) {
            try {
                val arr = org.json.JSONArray(saleWithItems.sale.splitPaymentJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val method = obj.optString("method", "")
                    if (method.equals("Store Credit", ignoreCase = true) || method.equals("WALLET", ignoreCase = true)) {
                        val amt = obj.optDouble("amount", 0.0)
                        appliedSc = (appliedSc ?: 0.0) + amt
                        val c = obj.optString("voucherCode", "")
                        if (c.isNotBlank()) {
                            vCode = c
                            vAmt = amt
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback for pure Store Credit payment (no split JSON)
        if ((appliedSc == null || appliedSc == 0.0) &&
            saleWithItems.sale.paymentMethod.equals("Store Credit", ignoreCase = true)) {
            val sc = saleWithItems.sale.storeCreditApplied
            if (sc > 0.0) appliedSc = sc
        }

        BillData(
            saleId = saleWithItems.sale.id,
            timestampMillis = saleWithItems.sale.timestamp,
            shopName = shopName,
            shopAddress = shopAddress,
            shopPhone = shopPhone,
            items = saleWithItems.items.map { item ->
                BillLineItem(
                    name = item.productName,
                    quantity = item.quantity,
                    unitPrice = item.price,
                    lineTotal = item.price * item.quantity,
                    note = item.note,
                    purchasePrice = item.purchasePriceAtSale,
                    taxable = item.taxable
                )
            },
            subtotal = saleWithItems.sale.subtotal,
            discountValue = calculateDiscountValue(
                saleWithItems.sale.subtotal,
                saleWithItems.sale.discountAmount,
                saleWithItems.sale.isPercentageDiscount
            ),
            grandTotal = saleWithItems.sale.grandTotal,
            paymentMethod = saleWithItems.sale.paymentMethod,
            customerName = saleWithItems.sale.customerName ?: "",
            customerPhone = saleWithItems.sale.customerPhone ?: "",
            amountPaid = saleWithItems.sale.amountPaid,
            changeAmount = PaymentMath.calculateChange(
                grandTotal = saleWithItems.sale.grandTotal,
                amountPaid = saleWithItems.sale.amountPaid,
                paymentMethod = saleWithItems.sale.paymentMethod,
                splitJson = saleWithItems.sale.splitPaymentJson,
                storeCreditApplied = appliedSc ?: 0.0
            ),
            walletPhone = saleWithItems.sale.walletPhone ?: "",
            cardType = saleWithItems.sale.cardType ?: "",
            cardLastFour = saleWithItems.sale.cardLastFour ?: "",
            splitPaymentJson = saleWithItems.sale.splitPaymentJson,
            logoUri = shopLogoUri,
            facebookUrl = facebookUrl,
            qrCodeInfo = qrCodeInfo,
            cashierName = saleWithItems.sale.performedByName?.takeIf { it.isNotBlank() } ?: cashierName,
            settingsData = invoiceSettingsData,
            taxLabel = invoiceSettingsData.taxLabel,
            taxValue = saleWithItems.sale.taxAmount,
            totalOutstandingDue = liveOutstandingDue,
            storeCreditApplied = appliedSc,
            voucherCode = vCode,
            voucherAmount = vAmt,
            voucherRemainingBalance = vRemaining,
            hasReturns = currentReturnSummary?.hasReturns == true,
            returnRefundAmount = currentReturnSummary?.totalRefundAmount ?: 0.0,
            returnCount = currentReturnSummary?.returnCount ?: 0
        )
    }

    val currentProfitSummary = remember(saleWithItems, currentReturnSummary, isOwner) {
        if (isOwner) salesViewModel.calculateInvoiceProfit(saleWithItems, currentReturnSummary) else null
    }

    ReceiptSummaryDialog(
        bill = billData,
        title = stringResource(R.string.history_invoice_title_fmt, saleWithItems.sale.id),
        onDismiss = onDismiss,
        onReturn = if (onReturn != null) {
            {
                if (canReturn) {
                    onReturn()
                } else if (onRequestSupervisorElevation != null) {
                    onRequestSupervisorElevation("Authorize Return for Invoice #${saleWithItems.sale.id}") {
                        onReturn()
                    }
                }
            }
        } else null,
        onDelete = {
            if (canDelete) {
                onDelete()
                onDismiss()
            } else if (onRequestSupervisorElevation != null) {
                onRequestSupervisorElevation("Authorize Delete for Invoice #${saleWithItems.sale.id}") {
                    onDelete()
                    onDismiss()
                }
            }
        },
        onAddToCartAgain = if (cartViewModel != null) {
            {
                scope.launch {
                    val skipped = cartViewModel.loadFromPastSale(saleWithItems, replaceCart = false)
                    if (skipped > 0) {
                        Toast.makeText(context, resources.getString(R.string.cart_add_to_cart_again_skipped_toast, skipped), Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                    onNavigateToCart?.invoke()
                }
            }
        } else null,
        onEdit = if (cartViewModel != null) {
            {
                if (canEdit) {
                    showEditConfirm = true
                } else if (onRequestSupervisorElevation != null) {
                    onRequestSupervisorElevation("Authorize Edit for Invoice #${saleWithItems.sale.id}") {
                        showEditConfirm = true
                    }
                }
            }
        } else null,
        onViewVoucherSlip = if (voucherForSale != null) {
            { showVoucherSlipDialog = true }
        } else null,
        isOwner = isOwner,
        returnSummary = currentReturnSummary,
        profitSummary = currentProfitSummary
    )

    if (showVoucherSlipDialog && voucherForSale != null) {
        StoreCreditVoucherSlipDialog(
            voucher = voucherForSale!!,
            shopName = shopName,
            shopPhone = shopPhone,
            shopAddress = shopAddress,
            invoiceSettingsData = invoiceSettingsData,
            onDismiss = { showVoucherSlipDialog = false }
        )
    }

    if (showEditConfirm) {
        val s = saleWithItems.sale
        val dueReversed = com.example.techyfipos.utils.PaymentMath.calculateDueAddition(
            s.paymentMethod, s.grandTotal, s.amountPaid, s.splitPaymentJson
        )
        val totalQty = saleWithItems.items.sumOf { it.quantity }
        val qtyStr = if (totalQty % 1.0 == 0.0) totalQty.toLong().toString() else String.format(Locale.US, "%.2f", totalQty).trimEnd('0').trimEnd('.')

        AlertDialog(
            onDismissRequest = { showEditConfirm = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.cart_edit_sale_confirm_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.cart_edit_sale_confirm_message),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.cart_edit_sale_breakdown_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.grand_total), fontSize = 12.sp)
                        Text(formatTaka(s.grandTotal), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    val (voucherCode, voucherPortion, customerCreditPortion) = remember(s) {
                        var vCode: String? = null
                        var vAmt = 0.0
                        var cAmt = 0.0
                        s.splitPaymentJson?.let { json ->
                            try {
                                val arr = org.json.JSONArray(json)
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    val m = obj.optString("method", "")
                                    if (m.equals("Store Credit", ignoreCase = true) || m.equals("Voucher", ignoreCase = true)) {
                                        val code = obj.optString("voucherCode", "")
                                        if (code.isNotBlank()) vCode = code
                                        val vP = obj.optDouble("voucherAmount", 0.0)
                                        val cP = obj.optDouble("customerCreditAmount", 0.0)
                                        if (vP > 0.0 || cP > 0.0) {
                                            vAmt += vP
                                            cAmt += cP
                                        } else if (code.isNotBlank()) {
                                            vAmt += obj.optDouble("amount", 0.0)
                                        } else {
                                            cAmt += obj.optDouble("amount", 0.0)
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        if (vAmt == 0.0 && cAmt == 0.0 && s.storeCreditApplied > 0.0) {
                            cAmt = s.storeCreditApplied
                        }
                        Triple(vCode, vAmt, cAmt)
                    }

                    val isCashSale = s.paymentMethod.equals("Cash", ignoreCase = true)
                    val actualCashPaid = if (isCashSale && s.amountPaid > s.grandTotal) s.grandTotal else s.amountPaid
                    val cashChange = if (isCashSale && s.amountPaid > s.grandTotal) s.amountPaid - s.grandTotal else 0.0
                    val loyaltySettings = remember { com.example.techyfipos.utils.LoyaltySettingsHelper.getSettings(context) }
                    val ptCashVal = loyaltySettings.pointCashValue.takeIf { it > 0.0 } ?: 1.0
                    val takaPerPt = loyaltySettings.takaPerPoint.takeIf { it > 0.0 } ?: 100.0
                    val loyaltyTargetPhone = s.loyaltyPhone?.takeIf { it.isNotBlank() } ?: s.customerPhone
                    val pointsEarnedClawback = remember(s, takaPerPt, loyaltyTargetPhone) {
                        if (!loyaltyTargetPhone.isNullOrBlank() && s.grandTotal > 0.0) {
                            val alreadyRefunded = currentReturnSummary?.totalRefundAmount ?: 0.0
                            val remainingSpend = (s.grandTotal - alreadyRefunded).coerceAtLeast(0.0)
                            kotlin.math.floor(remainingSpend / takaPerPt).toInt()
                        } else 0
                    }

                    if (actualCashPaid > 0.0 || !s.paymentMethod.equals("Due", ignoreCase = true)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.receipt_paid_label, s.paymentMethod), fontSize = 12.sp)
                            Text(formatTaka(actualCashPaid), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                    if (cashChange > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.change_returned_label), fontSize = 12.sp)
                            Text(formatTaka(cashChange), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                    if (voucherPortion > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.voucher_code_label_fmt, voucherCode ?: stringResource(R.string.voucher_plain_label)), fontSize = 12.sp)
                            Text("+${formatTaka(voucherPortion)}", color = Color(0xFF16A34A), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                    if (customerCreditPortion > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.customer_credit_label), fontSize = 12.sp)
                            Text("+${formatTaka(customerCreditPortion)}", color = Color(0xFF16A34A), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                    if (dueReversed > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.receipt_due_balance_label), fontSize = 12.sp)
                            Text("-${formatTaka(dueReversed)}", color = Color(0xFF16A34A), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                    if (s.loyaltyPointsRedeemed > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.loyalty_points_amount), fontSize = 12.sp)
                            Text("+${stringResource(R.string.customer_pts_suffix, s.loyaltyPointsRedeemed)} (${formatTaka(s.loyaltyPointsRedeemed * ptCashVal)})", color = Color(0xFF16A34A), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                    if (pointsEarnedClawback > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.points_clawback_label), fontSize = 12.sp)
                            Text("-${stringResource(R.string.customer_pts_suffix, pointsEarnedClawback)}", color = Color(0xFFDC2626), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.cart_edit_sale_restored_stock), fontSize = 12.sp)
                        Text(stringResource(R.string.history_items_count, qtyStr), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditConfirm = false
                        scope.launch {
                            val voided = salesViewModel?.deleteSaleAndAwait(saleWithItems.sale.id, context.getString(R.string.void_reason_for_edit)) ?: false
                            if (voided && cartViewModel != null) {
                                val skipped = cartViewModel.loadFromPastSale(saleWithItems, replaceCart = true)
                                if (skipped > 0) {
                                    Toast.makeText(context, resources.getString(R.string.cart_add_to_cart_again_skipped_toast, skipped), Toast.LENGTH_LONG).show()
                                }
                                onDismiss()
                                onNavigateToCart?.invoke()
                            } else {
                                Toast.makeText(context, resources.getString(R.string.sales_history_void_for_edit_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) { Text(stringResource(R.string.cart_edit_sale_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
