package com.example.techyfipos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.ui.viewmodel.DailyReportData
import com.example.techyfipos.ui.viewmodel.SalesReportData
import com.example.techyfipos.ui.viewmodel.SalesViewModel
import com.example.techyfipos.ui.viewmodel.ExpenseViewModel
import com.example.techyfipos.ui.viewmodel.SupplierViewModel
import com.example.techyfipos.data.db.Supplier
import com.example.techyfipos.utils.formatTaka
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import com.example.techyfipos.ui.viewmodel.HourlyReportData

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.example.techyfipos.R
import kotlinx.coroutines.launch

private enum class ReportPeriod {
    TODAY,
    WEEK,
    MONTH,
    YEAR,
    CUSTOM
}

@Composable
private fun ReportPeriod.localizedLabel(): String = when (this) {
    ReportPeriod.TODAY -> stringResource(R.string.sales_report_period_today)
    ReportPeriod.WEEK -> stringResource(R.string.sales_report_period_week)
    ReportPeriod.MONTH -> stringResource(R.string.sales_report_period_month)
    ReportPeriod.YEAR -> stringResource(R.string.sales_report_period_year)
    ReportPeriod.CUSTOM -> stringResource(R.string.sales_report_period_custom)
}

private enum class ReportTab {
    OVERVIEW,
    HOURLY,
    PRODUCTS,
    PAYMENTS
}

@Composable
private fun ReportTab.localizedTitle(): String = when (this) {
    ReportTab.OVERVIEW -> stringResource(R.string.sales_report_tab_overview)
    ReportTab.HOURLY -> stringResource(R.string.sales_report_tab_hourly)
    ReportTab.PRODUCTS -> stringResource(R.string.sales_report_tab_products)
    ReportTab.PAYMENTS -> stringResource(R.string.sales_report_tab_payments)
}

private enum class TrendMetric(val label: String) {
    SALES("Sales Volume"),
    PROFIT("Net Profit")
}

private enum class TrendViewMode {
    DAILY, HOURLY
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun rangeFor(period: ReportPeriod, customStart: Long, customEnd: Long): Pair<Long, Long> {
    val now = System.currentTimeMillis()
    return when (period) {
        ReportPeriod.TODAY -> startOfDay(now) to endOfDay(now) + 1
        ReportPeriod.WEEK -> startOfDay(now - 6L * 24 * 60 * 60 * 1000) to endOfDay(now) + 1
        ReportPeriod.MONTH -> startOfDay(now - 29L * 24 * 60 * 60 * 1000) to endOfDay(now) + 1
        ReportPeriod.YEAR -> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_YEAR, 1)
            }
            startOfDay(cal.timeInMillis) to endOfDay(now) + 1
        }
        ReportPeriod.CUSTOM -> startOfDay(minOf(customStart, customEnd)) to
            endOfDay(maxOf(customStart, customEnd)) + 1
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesReportScreen(
    salesViewModel: SalesViewModel,
    expenseViewModel: ExpenseViewModel? = null,
    supplierViewModel: SupplierViewModel? = null,
    onNavigateToExpenses: (() -> Unit)? = null,
    onNavigateToSuppliers: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val emptyReportMessage = stringResource(R.string.sales_report_empty_title)
    val salesSnapshot by salesViewModel.salesWithItems.collectAsStateWithLifecycle()
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.WEEK) }
    val now = remember { System.currentTimeMillis() }
    var customStart by remember { mutableStateOf(now) }
    var customEnd by remember { mutableStateOf(now) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(ReportTab.OVERVIEW) }
    var trendMetric by remember { mutableStateOf(TrendMetric.SALES) }
    var report by remember { mutableStateOf<SalesReportData?>(null) }
    var previousReport by remember { mutableStateOf<SalesReportData?>(null) }

    val (rangeStart, rangeEnd) = remember(selectedPeriod, customStart, customEnd) {
        rangeFor(selectedPeriod, customStart, customEnd)
    }
    LaunchedEffect(rangeStart, rangeEnd, salesSnapshot) {
        report = salesViewModel.buildReport(rangeStart, rangeEnd, salesSnapshot)
        val duration = (rangeEnd - rangeStart).coerceAtLeast(1L)
        previousReport = salesViewModel.buildReport(rangeStart - duration, rangeStart, salesSnapshot)
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val rangeLabel = remember(rangeStart, rangeEnd) {
        val start = dateFormat.format(Date(rangeStart))
        val end = dateFormat.format(Date((rangeEnd - 1).coerceAtLeast(rangeStart)))
        if (start == end) start else "$start – $end"
    }

    val exportSuccessToast = stringResource(R.string.sales_report_export_success)
    val exportReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null && report != null) {
            coroutineScope.launch {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                    if (stream != null) {
                        val success = stream.use { salesViewModel.exportSalesReportToExcel(report!!, rangeLabel, it) }
                        if (success) {
                            Toast.makeText(context, exportSuccessToast, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Export failed. Please check storage permissions.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Unable to access target file.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SalesReportScreen", "Excel export failed", e)
                    Toast.makeText(context, "Export error: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
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
                        title = stringResource(R.string.sales_report_title),
                        metricBadge = "Live Report",
                        badgeColor = PosSectionColors.SalesReport,
                        icon = { size -> Realistic3DReportChartIcon(size = size) }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (report != null && report!!.transactionCount > 0) {
                                val cleanDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(rangeStart))
                                exportReportLauncher.launch("sales-report-$cleanDate.xlsx")
                            } else {
                                Toast.makeText(context, emptyReportMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.sales_report_export_excel),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Period Filter Chips Row (Smooth Horizontal Scroll Strip)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp)
            ) {
                ReportPeriod.entries.forEach { period ->
                    val isSelected = selectedPeriod == period
                    Surface(
                        onClick = { selectedPeriod = period },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        ),
                        shadowElevation = if (isSelected) 3.dp else 1.dp
                    ) {
                        Text(
                            text = period.localizedLabel(),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // 3D Animated Date Range Picker Bar
            // Screen Header Card
            Surface(
                onClick = {
                    customStart = rangeStart
                    customEnd = (rangeEnd - 1).coerceAtLeast(rangeStart)
                    showStartPicker = true
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PosSectionColors.SalesReport.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Realistic3DAnalyticsIcon(size = 22.dp)
                            }
                        }
                        Column {
                            Text(
                                stringResource(R.string.sales_report_title),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.sales_report_subtitle),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.sales_report_change_date),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val data = report
            if (data == null) {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (data.transactionCount == 0) {
                EmptyReportState(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it }
                )
            } else {
                // 💎 Executive Financial Analytics Master Card
                ReportExecutiveSummaryCard(
                    data = data,
                    previous = previousReport,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4-Way Sub-Tab Switcher with 3D Icons
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReportTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            Surface(
                                onClick = { selectedTab = tab },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    when (tab) {
                                        ReportTab.OVERVIEW -> Realistic3DAnalyticsIcon(size = 18.dp)
                                        ReportTab.HOURLY -> Realistic3DHistoryClockIcon(size = 18.dp)
                                        ReportTab.PRODUCTS -> Realistic3DLeaderboardTrophyIcon(size = 18.dp)
                                        ReportTab.PAYMENTS -> Realistic3DMultiPaymentMixIcon(size = 18.dp)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = tab.localizedTitle(),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                when (selectedTab) {
                    ReportTab.OVERVIEW -> OverviewPanel(data, trendMetric) { trendMetric = it }
                    ReportTab.HOURLY -> HourlyPanel(data)
                    ReportTab.PRODUCTS -> ProductsPanel(data)
                    ReportTab.PAYMENTS -> PaymentsPanel(data)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customStart)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customStart = it }
                    showStartPicker = false
                    showEndPicker = true
                }) { Text(stringResource(R.string.dues_next_end_date_button)) }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.sales_report_select_start_date), Modifier.padding(24.dp, 16.dp)) }) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customEnd)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customEnd = it }
                    selectedPeriod = ReportPeriod.CUSTOM
                    showEndPicker = false
                }) { Text(stringResource(R.string.dues_apply_button)) }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.sales_report_select_end_date), Modifier.padding(24.dp, 16.dp)) }) }
    }

}

@Composable
private fun ReportExecutiveSummaryCard(
    data: SalesReportData,
    previous: SalesReportData?,
    modifier: Modifier = Modifier
) {
    var showProfitMode by remember { mutableStateOf(false) }
    val average = data.totalRevenue / data.transactionCount.coerceAtLeast(1)
    val netSales = (data.totalRevenue - data.totalTax).coerceAtLeast(0.0)
    val marginPercent = if (netSales == 0.0) 0.0 else (data.netProfit / netSales * 100)
    val growthDelta = change(data.totalRevenue, previous?.totalRevenue)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E1B4B),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, Color(0x40C7D2FE))
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
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Tag & Valuation Switch Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Realistic3DExecutiveAnalyticsOrbIcon(size = 20.dp)
                        Text(
                            text = stringResource(R.string.sales_report_executive_summary),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            color = Color(0xFFC7D2FE)
                        )
                    }

                    // Interactive Switch Pill
                    Surface(
                        onClick = { showProfitMode = !showProfitMode },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (showProfitMode) stringResource(R.string.sales_report_gross_sales) else stringResource(R.string.sales_report_net_margin),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = stringResource(R.string.products_switch_valuation_cd),
                                tint = Color(0xFFC7D2FE),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Middle Row: Big Amount & Margin Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (showProfitMode) formatTaka(data.netProfit) else formatTaka(data.totalRevenue),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.6).sp
                        )
                        Text(
                            text = if (showProfitMode) "${stringResource(R.string.sales_report_net_margin)} (${String.format(Locale.US, "%.1f", marginPercent)}%)"
                                   else "${stringResource(R.string.sales_report_total_revenue)} (${growthDelta?.let { "${if (it >= 0) "+" else ""}${String.format(Locale.US, "%.1f", it)}%" } ?: ""})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA5B4FC)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color(0x40A5B4FC))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${if (data.netProfit >= 0) "+" else ""}${formatTaka(data.netProfit)}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF34D399)
                            )
                            Text(
                                text = "${stringResource(R.string.sales_report_profit_margin)} (${String.format(Locale.US, "%.1f", marginPercent)}%)",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC7D2FE)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    thickness = 0.8.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4-Column Bottom Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.sales_report_completed_orders).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC7D2FE))
                        Text("${data.transactionCount}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Column {
                        Text(stringResource(R.string.sales_report_units_sold).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC7D2FE))
                        val itemsSoldStr = if (data.itemsSold % 1.0 == 0.0) data.itemsSold.toLong().toString() else String.format(java.util.Locale.US, "%.2f", data.itemsSold).trimEnd('0').trimEnd('.')
                        Text("$itemsSoldStr", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Column {
                        Text(stringResource(R.string.sales_report_discounts_given).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC7D2FE))
                        Text(formatTaka(data.totalDiscount), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                    Column {
                        Text(stringResource(R.string.sales_report_avg_order_value).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC7D2FE))
                        Text(formatTaka(average), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    thickness = 0.8.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Net Profit Breakdown
                Text(
                    text = stringResource(R.string.sales_report_net_profit_metric).uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    color = Color(0xFFC7D2FE)
                )
                Spacer(modifier = Modifier.height(8.dp))
                NetProfitBreakdownRow(stringResource(R.string.sales_report_gross_profit), data.totalProfit, isTotal = false)
                NetProfitBreakdownRow(stringResource(R.string.sales_report_discounts_given), -data.totalDiscount, isTotal = false)
                if (data.totalLoyaltyDiscount > 0.0) {
                    NetProfitBreakdownRow("Loyalty Points Redeemed", -data.totalLoyaltyDiscount, isTotal = false)
                }
                NetProfitBreakdownRow(stringResource(R.string.sales_report_total_expenses), -data.totalExpenses, isTotal = false)
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(6.dp))
                NetProfitBreakdownRow(stringResource(R.string.sales_report_net_profit), data.netProfit, isTotal = true)
            }
        }
    }
}

@Composable
private fun NetProfitBreakdownRow(label: String, amount: Double, isTotal: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isTotal) 0.dp else 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = if (isTotal) 12.5.sp else 11.5.sp,
            fontWeight = if (isTotal) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isTotal) Color.White else Color(0xFFA5B4FC)
        )
        Text(
            text = "${if (amount >= 0) "+" else "−"}${formatTaka(kotlin.math.abs(amount))}",
            fontSize = if (isTotal) 12.5.sp else 11.5.sp,
            fontWeight = if (isTotal) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isTotal) {
                if (amount >= 0) Color(0xFF34D399) else Color(0xFFFCA5A5)
            } else Color.White
        )
    }
}

private fun change(current: Double, previous: Double?): Double? {
    if (previous == null || previous == 0.0) return null
    return (current - previous) / abs(previous) * 100
}

@Composable
private fun EmptyReportState(
    selectedPeriod: ReportPeriod,
    onSelectPeriod: (ReportPeriod) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Realistic3DEmptyReceiptIcon(size = 68.dp)
            Text(
                stringResource(R.string.sales_report_empty_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(R.string.sales_report_empty_message),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center
            )

            // Smart Period Switcher
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💡 View Historical Period Analytics:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        if (selectedPeriod != ReportPeriod.WEEK) {
                            FilledTonalButton(
                                onClick = { onSelectPeriod(ReportPeriod.WEEK) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🔄 ${stringResource(R.string.sales_report_period_week)}", fontSize = 11.sp)
                            }
                        }

                        if (selectedPeriod != ReportPeriod.MONTH) {
                            FilledTonalButton(
                                onClick = { onSelectPeriod(ReportPeriod.MONTH) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🗓️ ${stringResource(R.string.sales_report_period_month)}", fontSize = 11.sp)
                            }
                        }

                        if (selectedPeriod != ReportPeriod.YEAR) {
                            FilledTonalButton(
                                onClick = { onSelectPeriod(ReportPeriod.YEAR) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("📊 ${stringResource(R.string.sales_report_period_year)}", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewPanel(data: SalesReportData, metric: TrendMetric, onMetricChange: (TrendMetric) -> Unit) {
    var viewMode by remember { mutableStateOf(if (data.dailyBreakdown.size <= 1) TrendViewMode.HOURLY else TrendViewMode.DAILY) }

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Title & Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Realistic3DAnalyticsIcon(size = 22.dp)
                Column(Modifier.weight(1f)) {
                    Text("Performance Trend", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(
                        if (viewMode == TrendViewMode.DAILY) "Daily sales & profit volume" else "24-hour hourly distribution breakdown",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp
                    )
                }
            }

            // Controls Row: Granularity (Daily vs Hourly) + Metric (Sales vs Profit) with zero clipping
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // View Mode (Daily vs Hourly)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(TrendViewMode.DAILY to "📅 Daily", TrendViewMode.HOURLY to "⏱️ Hourly").forEach { (mode, label) ->
                            val isSelected = viewMode == mode
                            Surface(
                                onClick = { viewMode = mode },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 1.dp else 0.dp
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // Metric Mode (Sales Volume vs Net Profit)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TrendMetric.entries.forEach { item ->
                            val isSelected = metric == item
                            Surface(
                                onClick = { onMetricChange(item) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 1.dp else 0.dp
                            ) {
                                Text(
                                    text = when (item) {
                                        TrendMetric.SALES -> stringResource(R.string.sales_report_sales_volume)
                                        TrendMetric.PROFIT -> stringResource(R.string.sales_report_net_profit_metric)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3D Trend Chart (Daily or Hourly)
            if (viewMode == TrendViewMode.DAILY) {
                TrendChart(data.dailyBreakdown, metric)

                val best = data.dailyBreakdown.maxByOrNull { if (metric == TrendMetric.SALES) it.revenue else it.profit }
                if (best != null) {
                    val day = remember { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()) }.format(Date(best.timestamp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Best Day: $day",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatTaka(if (metric == TrendMetric.SALES) best.revenue else best.profit),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else {
                HourlyTrendChart(data.hourlyBreakdown, metric)

                val peak = data.hourlyBreakdown.filter { it.transactionCount > 0 }.maxByOrNull { if (metric == TrendMetric.SALES) it.revenue else it.profit }
                if (peak != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔥", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val metricLabel = if (metric == TrendMetric.SALES) stringResource(R.string.sales_report_peak_hours) else stringResource(R.string.sales_report_net_profit_metric)
                                Text(
                                    text = "$metricLabel: ${peak.timeSlotLabel}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val itemsSoldStr = if (peak.itemsSold % 1.0 == 0.0) peak.itemsSold.toLong().toString() else String.format(Locale.US, "%.1f", peak.itemsSold)
                                Text(
                                    text = "${peak.transactionCount} invoices • $itemsSoldStr units sold",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFB45309),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = formatTaka(if (metric == TrendMetric.SALES) peak.revenue else peak.profit),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF92400E),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyTrendChart(points: List<HourlyReportData>, metric: TrendMetric) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.outline
    val values = points.map { if (metric == TrendMetric.SALES) it.revenue else it.profit.coerceAtLeast(0.0) }
    val maxValue = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val peakIndex = values.indexOfFirst { it == maxValue }.takeIf { maxValue > 0.0 } ?: -1

    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width
        val h = size.height - 18.dp.toPx()

        // 1. Grid Lines
        repeat(4) { index ->
            val y = h * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }

        if (values.isEmpty()) return@Canvas

        val count = values.size // 24
        val gap = (w / (count * 5)).coerceIn(1.5.dp.toPx(), 3.5.dp.toPx())
        val barWidth = ((w - gap * (count + 1)) / count).coerceAtLeast(4.dp.toPx())

        // 2. Render 24-Hour 3D Cylindrical Glowing Bars
        values.forEachIndexed { index, value ->
            val barHeight = if (value <= 0.0) 2.dp.toPx() else ((value / maxValue) * (h - 16.dp.toPx())).toFloat().coerceAtLeast(4.dp.toPx())
            val x = gap + index * (barWidth + gap)
            val y = h - barHeight
            val isPeak = index == peakIndex

            // Cylinder Body Gradient
            val barGradient = if (metric == TrendMetric.PROFIT) {
                Brush.verticalGradient(
                    colors = if (isPeak) listOf(Color(0xFF34D399), Color(0xFF047857))
                             else listOf(Color(0xFFA7F3D0), Color(0xFF10B981)),
                    startY = y,
                    endY = h
                )
            } else {
                Brush.verticalGradient(
                    colors = if (isPeak) listOf(Color(0xFF818CF8), Color(0xFF3730A3))
                             else listOf(Color(0xFFC7D2FE), primaryColor),
                    startY = y,
                    endY = h
                )
            }

            drawRoundRect(
                brush = barGradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Top Specular Highlight Cap
            if (value > 0.0) {
                val capColor = if (metric == TrendMetric.PROFIT) Color(0xFF6EE7B7) else Color(0xFFE0E7FF)
                drawRoundRect(
                    color = capColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, 2.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // Draw X-axis Hour Ticks at key intervals (0, 4, 8, 12, 16, 20, 23)
            if (index in listOf(0, 4, 8, 12, 16, 20, 23)) {
                val label = when (index) {
                    0 -> "12A"
                    12 -> "12P"
                    in 1..11 -> "${index}A"
                    else -> "${index - 12}P"
                }
                val measured = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        fontSize = 8.5.sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPeak) primaryColor else labelColor
                    )
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(x + (barWidth - measured.size.width) / 2f, h + 3.dp.toPx()),
                    style = TextStyle(
                        fontSize = 8.5.sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPeak) primaryColor else labelColor
                    )
                )
            }
        }
    }
}

@Composable
private fun HourlyPanel(data: SalesReportData) {
    var metric by remember { mutableStateOf(TrendMetric.SALES) }
    var showOnlyActiveHours by remember { mutableStateOf(true) }

    val activeHours = remember(data.hourlyBreakdown) {
        data.hourlyBreakdown.filter { it.transactionCount > 0 }
    }
    val peakHour = remember(activeHours) {
        activeHours.maxByOrNull { it.revenue }
    }
    val busiestHour = remember(activeHours) {
        activeHours.maxByOrNull { it.transactionCount }
    }
    val displayedHours = if (showOnlyActiveHours && activeHours.isNotEmpty()) activeHours else data.hourlyBreakdown
    val maxHourlyRevenue = (activeHours.maxOfOrNull { it.revenue } ?: 1.0).coerceAtLeast(1.0)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 3-KPI Highlight Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Peak Sales Hour Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.weight(1f)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🔥", fontSize = 12.sp)
                        Text("PEAK HOUR", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E40AF), letterSpacing = 0.4.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = peakHour?.hourLabel ?: "None",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E3A8A),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = peakHour?.let { formatTaka(it.revenue) } ?: "৳0",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Busiest Hour Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFECFDF5),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.weight(1f)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⚡", fontSize = 12.sp)
                        Text("BUSIEST HOUR", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF065F46), letterSpacing = 0.4.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = busiestHour?.hourLabel ?: "None",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF064E3B),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = busiestHour?.let { "${it.transactionCount} Invoices" } ?: "0 Invoices",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Active Hours Count Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFAF5FF),
                border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                modifier = Modifier.weight(1f)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("⏱️", fontSize = 12.sp)
                        Text("ACTIVE HOURS", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B21A8), letterSpacing = 0.4.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    val activeHoursCount = data.hourlyBreakdown.count { it.transactionCount > 0 }
                    Text(
                        text = "$activeHoursCount Hours",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF581C87),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "${data.hourlyBreakdown.size} Tracked",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7E22CE),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 24-Hour Interactive Cylindrical Chart Card
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Hourly Volume Curve", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text("24-hour timeline analysis", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    }

                    // Metric Switcher
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TrendMetric.entries.forEach { item ->
                                val isSelected = metric == item
                                Surface(
                                    onClick = { metric = item },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (isSelected) 1.dp else 0.dp
                                ) {
                                    Text(
                                        text = when (item) {
                                            TrendMetric.SALES -> stringResource(R.string.sales_report_sales_volume)
                                            TrendMetric.PROFIT -> stringResource(R.string.sales_report_net_profit_metric)
                                        },
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HourlyTrendChart(data.hourlyBreakdown, metric)
            }
        }

        // Detailed Hourly Table / List Card
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Time-Slot Breakdown", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text("${activeHours.size} active hourly time windows", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    }

                    Surface(
                        onClick = { showOnlyActiveHours = !showOnlyActiveHours },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (showOnlyActiveHours) "Active Only" else "All 24h",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (displayedHours.none { it.transactionCount > 0 }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hourly sales recorded in this period.", color = MaterialTheme.colorScheme.outline, fontSize = 12.5.sp)
                    }
                } else {
                    displayedHours.forEachIndexed { index, hourData ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        val isPeak = peakHour != null && hourData.hour == peakHour.hour
                        val fraction = (hourData.revenue / maxHourlyRevenue).toFloat().coerceIn(0f, 1f)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val timeIcon = when (hourData.hour) {
                                        in 5..11 -> "☀️"
                                        in 12..16 -> "🌤️"
                                        in 17..20 -> "🌆"
                                        else -> "🌙"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPeak) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        border = if (isPeak) BorderStroke(1.dp, Color(0xFFFCD34D)) else null,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(timeIcon, fontSize = 13.sp)
                                        }
                                    }

                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = hourData.timeSlotLabel,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isPeak) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFFEF3C7)
                                                ) {
                                                    Text("PEAK", fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                        }
                                        val itemsQtyStr = if (hourData.itemsSold % 1.0 == 0.0) hourData.itemsSold.toLong().toString() else String.format(Locale.US, "%.1f", hourData.itemsSold)
                                        Text(
                                            text = "${hourData.transactionCount} invoices • $itemsQtyStr units",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatTaka(hourData.revenue),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Profit: ${formatTaka(hourData.profit)}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF059669)
                                    )
                                }
                            }

                            if (hourData.transactionCount > 0) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = if (isPeak) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    strokeCap = StrokeCap.Round
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
private fun TrendChart(points: List<DailyReportData>, metric: TrendMetric) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.outline
    val values = points.map { if (metric == TrendMetric.SALES) it.revenue else it.profit.coerceAtLeast(0.0) }
    val maxValue = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width
        val h = size.height - 18.dp.toPx()

        // 1. Grid Lines
        repeat(4) { index ->
            val y = h * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }

        if (values.isEmpty()) return@Canvas

        val gap = 6.dp.toPx()
        val barWidth = ((w - gap * (values.size + 1)) / values.size.coerceAtLeast(1)).coerceAtLeast(6.dp.toPx())
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

        // 2. Render 3D Cylindrical Glowing Bars
        values.forEachIndexed { index, value ->
            val barHeight = ((value / maxValue) * (h - 20.dp.toPx())).toFloat().coerceAtLeast(4.dp.toPx())
            val x = gap + index * (barWidth + gap)
            val y = h - barHeight

            // Cylinder Body Gradient
            val barGradient = if (metric == TrendMetric.PROFIT) {
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF34D399), Color(0xFF059669)),
                    startY = y,
                    endY = h
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF818CF8), primaryColor),
                    startY = y,
                    endY = h
                )
            }

            drawRoundRect(
                brush = barGradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // Top Specular Highlight Cap
            val capColor = if (metric == TrendMetric.PROFIT) Color(0xFF6EE7B7) else Color(0xFFC7D2FE)
            drawRoundRect(
                color = capColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, 3.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            // Draw day label if list is not crowded (e.g. <= 14 days)
            if (points.size <= 14 && index < points.size) {
                val dayLabel = dayFormat.format(Date(points[index].timestamp))
                val measured = textMeasurer.measure(
                    text = dayLabel,
                    style = TextStyle(fontSize = 9.sp, color = labelColor)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = dayLabel,
                    topLeft = Offset(x + (barWidth - measured.size.width) / 2f, h + 3.dp.toPx()),
                    style = TextStyle(fontSize = 9.sp, color = labelColor)
                )
            }
        }
    }
}

@Composable
private fun ProductsPanel(data: SalesReportData) {
    var expandedBarcode by remember { mutableStateOf<String?>(null) }
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Realistic3DLeaderboardTrophyIcon(size = 22.dp)
                Column {
                    Text("Top Selling Products", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("Ranked by revenue contribution", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))

            data.topProducts.take(8).forEachIndexed { index, product ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            expandedBarcode = if (expandedBarcode == product.barcode) null else product.barcode
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Rank Badge (Gold, Silver, Bronze, or Neutral)
                        val rankBg = when (index) {
                            0 -> Color(0xFFFEF3C7)
                            1 -> Color(0xFFF1F5F9)
                            2 -> Color(0xFFFFFBEB)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val rankTextColor = when (index) {
                            0 -> Color(0xFFB45309)
                            1 -> Color(0xFF475569)
                            2 -> Color(0xFF92400E)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val rankBorder = when (index) {
                            0 -> Color(0xFFFCD34D)
                            1 -> Color(0xFFCBD5E1)
                            2 -> Color(0xFFFDE68A)
                            else -> Color.Transparent
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = rankBg,
                            border = BorderStroke(1.dp, rankBorder),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = rankTextColor
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = product.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 17.sp
                            )
                            val qStr = if (product.quantity % 1.0 == 0.0) product.quantity.toLong().toString() else String.format(java.util.Locale.US, "%.2f", product.quantity).trimEnd('0').trimEnd('.')
                            Text("$qStr units sold", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(formatTaka(product.revenue), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, "View product details", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                    }

                    if (expandedBarcode == product.barcode) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("BARCODE / SKU", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                    Text(product.barcode, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("AVG UNIT PRICE", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                    val avgPrice = if (product.quantity > 0.0) product.revenue / product.quantity else 0.0
                                    Text(formatTaka(avgPrice), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentsPanel(data: SalesReportData) {
    val total = data.paymentBreakdown.sumOf { it.amount }.coerceAtLeast(1.0)
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Realistic3DMultiPaymentMixIcon(size = 22.dp)
                Column {
                    Text("Payment Channel Mix", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("Revenue collected per channel", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(14.dp))

            data.paymentBreakdown.forEachIndexed { index, payment ->
                val fraction = (payment.amount / total).toFloat().coerceIn(0f, 1f)
                val percent = (fraction * 100).toInt()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Payment3DBadge(paymentMethod = payment.method)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("${payment.transactionCount} sales ($percent%)", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                            Text(formatTaka(payment.amount), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = when {
                            payment.method.contains("Cash", true) -> Color(0xFF10B981)
                            payment.method.contains("bKash", true) -> Color(0xFFEC4899)
                            payment.method.contains("Nagad", true) -> Color(0xFFF97316)
                            payment.method.contains("Card", true) -> Color(0xFF3B82F6)
                            payment.method.contains("Rocket", true) -> Color(0xFFA855F7)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

