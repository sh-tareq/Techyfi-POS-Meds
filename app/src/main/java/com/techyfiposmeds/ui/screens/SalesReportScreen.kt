package com.techyfiposmeds.ui.screens

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
import com.techyfiposmeds.theme.extendedColors
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techyfiposmeds.ui.components.*
import com.techyfiposmeds.ui.viewmodel.DailyReportData
import com.techyfiposmeds.ui.viewmodel.SalesReportData
import com.techyfiposmeds.ui.viewmodel.SalesViewModel
import com.techyfiposmeds.ui.viewmodel.ExpenseViewModel
import com.techyfiposmeds.ui.viewmodel.SupplierViewModel
import com.techyfiposmeds.data.db.Supplier
import com.techyfiposmeds.utils.formatTaka
import com.techyfiposmeds.utils.getLocalizedRepositoryErrorMessage
import com.techyfiposmeds.utils.startOfDay
import com.techyfiposmeds.utils.endOfDay
import com.techyfiposmeds.utils.localMillisToUtcMidnight
import com.techyfiposmeds.utils.utcMidnightToLocalMidnight
import com.techyfiposmeds.utils.toBanglaDigits
import com.techyfiposmeds.utils.isAppBangla
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import com.techyfiposmeds.ui.viewmodel.HourlyReportData
import com.techyfiposmeds.utils.DateFormatters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.techyfiposmeds.R
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
    PAYMENTS,
    ANTIBIOTIC_REGISTER,
    NARCOTICS_REGISTER
}

@Composable
private fun ReportTab.localizedTitle(): String = when (this) {
    ReportTab.OVERVIEW -> stringResource(R.string.sales_report_tab_overview)
    ReportTab.HOURLY -> stringResource(R.string.sales_report_tab_hourly)
    ReportTab.PRODUCTS -> stringResource(R.string.sales_report_tab_products)
    ReportTab.PAYMENTS -> stringResource(R.string.sales_report_tab_payments)
    ReportTab.ANTIBIOTIC_REGISTER -> stringResource(R.string.sales_report_tab_antibiotic_register)
    ReportTab.NARCOTICS_REGISTER -> stringResource(R.string.sales_report_tab_narcotics_register)
}

private enum class TrendMetric(@get:androidx.annotation.StringRes val labelRes: Int) {
    SALES(R.string.sales_report_sales_volume),
    PROFIT(R.string.sales_report_net_profit_metric)
}

private enum class TrendViewMode {
    DAILY, HOURLY
}


private fun rangeFor(period: ReportPeriod, customStart: Long, customEnd: Long, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
    return when (period) {
        ReportPeriod.TODAY -> startOfDay(now) to endOfDay(now) + 1
        ReportPeriod.WEEK -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            while (cal.get(Calendar.DAY_OF_WEEK) != cal.firstDayOfWeek) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
            startOfDay(cal.timeInMillis) to endOfDay(now) + 1
        }
        ReportPeriod.MONTH -> {
            val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1) }
            startOfDay(cal.timeInMillis) to endOfDay(now) + 1
        }
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
    isTablet: Boolean,
    canViewPnl: Boolean = true,
    canExport: Boolean = true,
    onNavigateToExpenses: (() -> Unit)? = null,
    onNavigateToSuppliers: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val emptyReportMessage = stringResource(R.string.sales_report_empty_title)
    val salesSnapshot by salesViewModel.salesWithItems.collectAsStateWithLifecycle()
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.TODAY) }
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
    var customStart by remember { mutableStateOf(now) }
    var customEnd by remember { mutableStateOf(now) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(ReportTab.OVERVIEW) }
    var trendMetric by remember { mutableStateOf(TrendMetric.SALES) }
    var report by remember { mutableStateOf<SalesReportData?>(null) }
    var previousReport by remember { mutableStateOf<SalesReportData?>(null) }

    val (rangeStart, rangeEnd) = remember(selectedPeriod, customStart, customEnd, now) {
        rangeFor(selectedPeriod, customStart, customEnd, now)
    }

    val allExpenses by (expenseViewModel?.allExpenses?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) })
    val periodExpenses = remember(allExpenses, rangeStart, rangeEnd) {
        allExpenses.filter { it.timestamp in rangeStart until rangeEnd && it.type == "EXPENSE" && !it.category.equals("Supplier Payment", ignoreCase = true) && !it.category.equals("Safe Drop", ignoreCase = true) }
    }
    val periodExpenseTotal = remember(periodExpenses) { periodExpenses.sumOf { it.amount } }

    val allSuppliers by (supplierViewModel?.allSuppliers?.collectAsStateWithLifecycle(emptyList()) ?: remember { mutableStateOf(emptyList()) })
    val totalSupplierDue = remember(allSuppliers) { allSuppliers.sumOf { it.totalDue } }
    LaunchedEffect(rangeStart, rangeEnd, salesSnapshot) {
        report = salesViewModel.buildReport(rangeStart, rangeEnd, salesSnapshot)
        val duration = (rangeEnd - rangeStart).coerceAtLeast(1L)
        previousReport = salesViewModel.buildReport(rangeStart - duration, rangeStart, salesSnapshot)
    }

    val rangeLabel = remember(rangeStart, rangeEnd) {
        val start = DateFormatters.formatDate(rangeStart)
        val end = DateFormatters.formatDate((rangeEnd - 1).coerceAtLeast(rangeStart))
        if (start == end) start else "$start – $end"
    }
    val currentRangeLabel by rememberUpdatedState(rangeLabel)

    val exportSuccessToast = stringResource(R.string.sales_report_export_success)
    val exportReportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null && report != null) {
            coroutineScope.launch {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                    if (stream != null) {
                        val success = stream.use { salesViewModel.exportSalesReportToExcel(report!!, currentRangeLabel, it) }
                        if (success) {
                            Toast.makeText(context, exportSuccessToast, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.sales_report_export_permission_error), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.sales_report_export_access_error), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SalesReportScreen", "Excel export failed", e)
                    Toast.makeText(context, context.getString(R.string.sales_report_export_general_error, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var viewingPrescriptionUri by remember { mutableStateOf<String?>(null) }

    val antibioticRecords = remember(salesSnapshot, rangeStart, rangeEnd) {
        val list = mutableListOf<AntibioticDispenseRecord>()
        salesSnapshot.filter { it.sale.timestamp in rangeStart until rangeEnd }.forEach { saleWithItems ->
            val sale = saleWithItems.sale
            saleWithItems.items.filter { it.isAntibiotic }.forEach { item ->
                list.add(
                    AntibioticDispenseRecord(
                        saleId = sale.id,
                        timestamp = sale.timestamp,
                        patientName = sale.customerName?.takeIf { it.isNotBlank() } ?: "Walk-in Patient",
                        patientPhone = sale.customerPhone?.takeIf { it.isNotBlank() } ?: "N/A",
                        doctorName = sale.doctorName?.takeIf { it.isNotBlank() } ?: "",
                        doctorBmdcReg = sale.doctorBmdcReg?.takeIf { it.isNotBlank() } ?: "",
                        hasPrescriptionPhoto = !sale.prescriptionImageUri.isNullOrBlank(),
                        isEmergencyOtc = sale.doctorName.isNullOrBlank() && sale.prescriptionImageUri.isNullOrBlank(),
                        medicineName = item.productName,
                        genericName = item.genericName ?: "",
                        batchNumber = item.batchNumber ?: "N/A",
                        expiryDate = item.expiryDate ?: "N/A",
                        quantity = item.quantity,
                        unitType = item.unitType,
                        price = item.price,
                        totalAmount = item.price * item.quantity,
                        dosageSchedule = item.dosageSchedule,
                        prescriptionImageUri = sale.prescriptionImageUri
                    )
                )
            }
        }
        list
    }

    val narcoticsRecords = remember(salesSnapshot, rangeStart, rangeEnd) {
        val list = mutableListOf<NarcoticsDispenseRecord>()
        salesSnapshot.filter { it.sale.timestamp in rangeStart until rangeEnd }.forEach { saleWithItems ->
            val sale = saleWithItems.sale
            saleWithItems.items.filter { isControlledOrNarcoticMedicine(it.genericName, it.productName) }.forEach { item ->
                list.add(
                    NarcoticsDispenseRecord(
                        saleId = sale.id,
                        timestamp = sale.timestamp,
                        patientName = sale.customerName?.takeIf { it.isNotBlank() } ?: "Walk-in Patient",
                        patientPhone = sale.customerPhone?.takeIf { it.isNotBlank() } ?: "N/A",
                        doctorName = sale.doctorName?.takeIf { it.isNotBlank() } ?: "",
                        doctorBmdcReg = sale.doctorBmdcReg?.takeIf { it.isNotBlank() } ?: "",
                        hasPrescriptionPhoto = !sale.prescriptionImageUri.isNullOrBlank(),
                        medicineName = item.productName,
                        controlledGeneric = item.genericName ?: item.productName,
                        batchNumber = item.batchNumber ?: "N/A",
                        expiryDate = item.expiryDate ?: "N/A",
                        quantity = item.quantity,
                        unitType = item.unitType,
                        price = item.price,
                        totalAmount = item.price * item.quantity,
                        dosageSchedule = item.dosageSchedule,
                        prescriptionImageUri = sale.prescriptionImageUri
                    )
                )
            }
        }
        list
    }

    val exportAntibioticCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                    if (stream != null) {
                        stream.use { s ->
                            val csv = generateAntibioticRegisterCsv(antibioticRecords, currentRangeLabel)
                            s.write(csv.toByteArray(Charsets.UTF_8))
                        }
                        Toast.makeText(context, exportSuccessToast, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportNarcoticsCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                    if (stream != null) {
                        stream.use { s ->
                            val csv = generateNarcoticsRegisterCsv(narcoticsRecords, currentRangeLabel)
                            s.write(csv.toByteArray(Charsets.UTF_8))
                        }
                        Toast.makeText(context, exportSuccessToast, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
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
                        metricBadge = stringResource(R.string.sales_report_live_badge),
                        badgeColor = PosSectionColors.SalesReport,
                        icon = { size -> Realistic3DReportChartIcon(size = size) }
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
                    val hasTransactions = report != null && (report!!.transactionCount > 0 || report!!.totalRefunds > 0.0 || report!!.totalExpenses > 0.0)
                    if (canExport) {
                        IconButton(
                            onClick = {
                                if (report != null) {
                                    val textReport = generateStructuredReportText(report!!, rangeLabel, periodExpenseTotal, canViewPnl)
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.share_sales_report_subject, rangeLabel))
                                        putExtra(android.content.Intent.EXTRA_TEXT, textReport)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, context.getString(R.string.share_sales_report_chooser)))
                                }
                            },
                            enabled = hasTransactions
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share),
                                tint = if (hasTransactions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    if (canExport) {
                        IconButton(
                            onClick = {
                                if (hasTransactions) {
                                    val cleanDate = DateFormatters.formatExportDate(rangeStart)
                                    exportReportLauncher.launch("sales-report-$cleanDate.xlsx")
                                }
                            },
                            enabled = hasTransactions
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = stringResource(R.string.sales_report_export_excel),
                                tint = if (hasTransactions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
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
                    .fillMaxHeight()
                    .widthIn(max = if (isTablet) 960.dp else Dp.Unspecified)
                    .fillMaxWidth()
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
                        shadowElevation = if (isSelected) 3.dp else 1.dp,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
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
            } else if (data.transactionCount == 0 && data.totalRefunds == 0.0 && data.totalExpenses == 0.0) {
                EmptyReportState(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it }
                )
            } else {
                // 💎 Executive Financial Analytics Master Card
                ReportExecutiveSummaryCard(
                    data = data,
                    previous = previousReport,
                    periodExpenseTotal = periodExpenseTotal,
                    canViewPnl = canViewPnl,
                    isTablet = isTablet,
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
                                        ReportTab.ANTIBIOTIC_REGISTER -> Realistic3DAmberHazardDiamond(size = 18.dp)
                                        ReportTab.NARCOTICS_REGISTER -> Realistic3DStealthShieldIcon(isStealthActive = true, size = 18.dp)
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
                    ReportTab.OVERVIEW -> OverviewPanel(
                        data = data,
                        metric = if (canViewPnl) trendMetric else TrendMetric.SALES,
                        periodExpenseTotal = periodExpenseTotal,
                        totalSupplierDue = totalSupplierDue,
                        hasExpenseFeature = expenseViewModel != null,
                        hasSupplierFeature = supplierViewModel != null,
                        canViewPnl = canViewPnl,
                        onNavigateToExpenses = onNavigateToExpenses,
                        onNavigateToSuppliers = onNavigateToSuppliers,
                        onMetricChange = { if (canViewPnl) trendMetric = it }
                    )
                    ReportTab.HOURLY -> HourlyPanel(data, canViewPnl = canViewPnl)
                    ReportTab.PRODUCTS -> ProductsPanel(data)
                    ReportTab.PAYMENTS -> PaymentsPanel(data)
                    ReportTab.ANTIBIOTIC_REGISTER -> AntibioticRegisterPanel(
                        records = antibioticRecords,
                        onExportCsv = {
                            val cleanDate = DateFormatters.formatExportDate(rangeStart)
                            exportAntibioticCsvLauncher.launch("dgda-antibiotic-register-$cleanDate.csv")
                        },
                        onViewPrescription = { uri -> viewingPrescriptionUri = uri }
                    )
                    ReportTab.NARCOTICS_REGISTER -> NarcoticsRegisterPanel(
                        records = narcoticsRecords,
                        onExportCsv = {
                            val cleanDate = DateFormatters.formatExportDate(rangeStart)
                            exportNarcoticsCsvLauncher.launch("narcotics-controlled-register-$cleanDate.csv")
                        },
                        onViewPrescription = { uri -> viewingPrescriptionUri = uri }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        }
    }

    if (viewingPrescriptionUri != null) {
        PrescriptionImageViewerDialog(imageUriString = viewingPrescriptionUri!!) {
            viewingPrescriptionUri = null
        }
    }

    val currentDayEndUtcMillis = remember(now) {
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

    val pastAndPresentSelectableDates = remember(currentDayEndUtcMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= currentDayEndUtcMillis
            override fun isSelectableYear(year: Int): Boolean = year <= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = localMillisToUtcMidnight(customStart.coerceAtMost(System.currentTimeMillis())),
            selectableDates = pastAndPresentSelectableDates
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customStart = utcMidnightToLocalMidnight(it).coerceAtMost(System.currentTimeMillis()) }
                    showStartPicker = false
                    showEndPicker = true
                }) { Text(stringResource(R.string.dues_next_end_date_button)) }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.sales_report_select_start_date), Modifier.padding(24.dp, 16.dp)) }) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = localMillisToUtcMidnight(customEnd.coerceAtMost(System.currentTimeMillis())),
            selectableDates = pastAndPresentSelectableDates
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val selected = utcMidnightToLocalMidnight(it).coerceAtMost(System.currentTimeMillis())
                        if (selected < customStart) {
                            customEnd = customStart
                            customStart = selected
                        } else {
                            customEnd = selected
                        }
                    }
                    selectedPeriod = ReportPeriod.CUSTOM
                    showEndPicker = false
                }) { Text(stringResource(R.string.dues_apply_button)) }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.sales_report_select_end_date), Modifier.padding(24.dp, 16.dp)) }) }
    }

}

private fun generateStructuredReportText(
    report: SalesReportData,
    rangeLabel: String,
    periodExpenseTotal: Double,
    canViewPnl: Boolean = true
): String {
    val isBangla = com.techyfiposmeds.utils.isAppBangla()
    val sb = StringBuilder()
    sb.appendLine(if (isBangla) "=== টেকিফাই ফার্মা বিক্রয় রিপোর্ট ===" else "=== Techyfi Pharma Sales Report ===")
    sb.appendLine("${if (isBangla) "সময়কাল" else "Period"}: $rangeLabel")
    sb.appendLine("--------------------------------")
    sb.appendLine("${if (isBangla) "মোট বিক্রয়" else "Total Revenue"}: ${formatTaka(report.totalRevenue, isBangla = isBangla)}")
    sb.appendLine("${if (isBangla) "নিট বিক্রয়" else "Net Sales"}: ${formatTaka((report.totalRevenue - report.totalTax).coerceAtLeast(0.0), isBangla = isBangla)}")
    if (canViewPnl) {
        sb.appendLine("${if (isBangla) "মোট লাভ" else "Gross Profit"}: ${formatTaka(report.grossProfit, isBangla = isBangla)}")
    }
    val invCountStr = if (isBangla) com.techyfiposmeds.utils.toBanglaDigits(report.transactionCount.toString()) else report.transactionCount.toString()
    sb.appendLine("${if (isBangla) "ইনভয়েস / বিল সংখ্যা" else "Invoices / Bills"}: $invCountStr")
    val rawItemsSoldStr = if (report.itemsSold % 1.0 == 0.0) report.itemsSold.toLong().toString() else String.format(Locale.US, "%.2f", report.itemsSold).trimEnd('0').trimEnd('.')
    val itemsSoldStr = if (isBangla) com.techyfiposmeds.utils.toBanglaDigits(rawItemsSoldStr) else rawItemsSoldStr
    sb.appendLine("${if (isBangla) "বিক্রিত ওষুধ" else "Medicines Sold"}: $itemsSoldStr")
    sb.appendLine("${if (isBangla) "মোট ভ্যাট/ট্যাক্স" else "Total Tax"}: ${formatTaka(report.totalTax, isBangla = isBangla)}")
    sb.appendLine("${if (isBangla) "মোট ডিসকাউন্ট" else "Total Discount"}: ${formatTaka(report.totalDiscount, isBangla = isBangla)}")
    sb.appendLine("${if (isBangla) "মোট রিফান্ড" else "Total Refunds"}: ${formatTaka(report.totalRefunds, isBangla = isBangla)}")
    val effectiveExpense = if (periodExpenseTotal > 0.0) periodExpenseTotal else report.totalExpenses
    sb.appendLine("${if (isBangla) "খরচ" else "Expenses"}: ${formatTaka(effectiveExpense, isBangla = isBangla)}")
    if (canViewPnl) {
        val netOperatingProfit = report.grossProfit - effectiveExpense
        sb.appendLine("${if (isBangla) "নিট অপারেটিং লাভ" else "Net Operating Profit"}: ${formatTaka(netOperatingProfit, isBangla = isBangla)}")
    }
    if (report.paymentBreakdown.isNotEmpty()) {
        sb.appendLine("--------------------------------")
        sb.appendLine(if (isBangla) "মূল্য পরিশোধ বিবরণী:" else "Payment Breakdown:")
        report.paymentBreakdown.forEach { p ->
            val bCount = if (isBangla) com.techyfiposmeds.utils.toBanglaDigits(p.transactionCount.toString()) else p.transactionCount.toString()
            val billsSuffix = if (isBangla) "$bCount টি বিল" else "$bCount bills"
            val methodLabel = if (isBangla) com.techyfiposmeds.utils.formatPaymentMethodBangla(p.method) else p.method
            sb.appendLine("  $methodLabel: ${formatTaka(p.amount, isBangla = isBangla)} ($billsSuffix)")
        }
    }
    if (report.topProducts.isNotEmpty()) {
        sb.appendLine("--------------------------------")
        sb.appendLine(if (isBangla) "সেরা বিক্রিত ওষুধ:" else "Top Medicines:")
        report.topProducts.take(10).forEachIndexed { i, p ->
            val rawQ = if (p.quantity % 1.0 == 0.0) p.quantity.toLong().toString() else String.format(Locale.US, "%.2f", p.quantity).trimEnd('0').trimEnd('.')
            val qStr = if (isBangla) com.techyfiposmeds.utils.toBanglaDigits(rawQ) else rawQ
            val rank = if (isBangla) com.techyfiposmeds.utils.toBanglaDigits((i + 1).toString()) else (i + 1).toString()
            val soldSuffix = if (isBangla) "$qStr টি বিক্রয়" else "$qStr sold"
            sb.appendLine("  $rank. ${p.name}: $soldSuffix (${formatTaka(p.revenue, isBangla = isBangla)})")
        }
    }
    sb.appendLine("================================")
    return sb.toString()
}

@Composable
private fun ReportExecutiveSummaryCard(
    data: SalesReportData,
    previous: SalesReportData?,
    periodExpenseTotal: Double = 0.0,
    canViewPnl: Boolean = true,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showProfitMode by remember { mutableStateOf(false) }
    val average = data.totalRevenue / data.transactionCount.coerceAtLeast(1)
    val netSales = (data.totalRevenue - data.totalTax).coerceAtLeast(0.0)
    val effectiveNetProfit = data.netProfit
    val marginPercent = if (netSales == 0.0) 0.0 else (effectiveNetProfit / netSales * 100)
    val growthDelta = change(data.totalRevenue, previous?.totalRevenue)
    val isBn = isAppBangla()
    fun fmtPct(value: Double): String {
        val s = String.format(Locale.US, "%.1f", value)
        return if (isBn) toBanglaDigits(s) else s
    }

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

                    if (canViewPnl) {
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
                            text = if (showProfitMode) formatTaka(effectiveNetProfit) else formatTaka(data.totalRevenue),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.6).sp
                        )
                        Text(
                            text = if (showProfitMode) "${stringResource(R.string.sales_report_net_margin)} (${fmtPct(marginPercent)}%)"
                                   else "${stringResource(R.string.sales_report_total_revenue)} (${growthDelta?.let { "${if (it >= 0) "+" else ""}${fmtPct(it)}%" } ?: ""})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA5B4FC)
                        )
                    }

                    if (canViewPnl) {
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
                                    text = "${if (effectiveNetProfit >= 0) "+" else ""}${formatTaka(effectiveNetProfit)}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399)
                                )
                                Text(
                                    text = "${stringResource(R.string.sales_report_profit_margin)} (${fmtPct(marginPercent)}%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC7D2FE)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.15f),
                    thickness = 0.8.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Metrics Grid: 4-Column on Tablet, Responsive 2x2 Grid on Mobile
                val txCountStr = if (isBn) toBanglaDigits(data.transactionCount.toString()) else "${data.transactionCount}"
                val rawItemsSold = if (data.itemsSold % 1.0 == 0.0) data.itemsSold.toLong().toString() else String.format(java.util.Locale.US, "%.2f", data.itemsSold).trimEnd('0').trimEnd('.')
                val itemsSoldStr = if (isBn) toBanglaDigits(rawItemsSold) else rawItemsSold

                if (isTablet) {
                    // Single row of 4 weighted tiles for Tablet displays (width >= 600dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExecutiveMetricCell(
                            label = stringResource(R.string.sales_report_completed_orders),
                            value = txCountStr,
                            modifier = Modifier.weight(1f)
                        )
                        ExecutiveMetricCell(
                            label = stringResource(R.string.sales_report_units_sold),
                            value = itemsSoldStr,
                            modifier = Modifier.weight(1f)
                        )
                        ExecutiveMetricCell(
                            label = stringResource(R.string.sales_report_discounts_given),
                            value = formatTaka(data.totalDiscount),
                            modifier = Modifier.weight(1f)
                        )
                        ExecutiveMetricCell(
                            label = stringResource(R.string.sales_report_avg_order_value),
                            value = formatTaka(average),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    // 2x2 Grid of frosted tiles for Compact Mobile screens (< 600dp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExecutiveMetricCell(
                                label = stringResource(R.string.sales_report_completed_orders),
                                value = txCountStr,
                                modifier = Modifier.weight(1f)
                            )
                            ExecutiveMetricCell(
                                label = stringResource(R.string.sales_report_units_sold),
                                value = itemsSoldStr,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExecutiveMetricCell(
                                label = stringResource(R.string.sales_report_discounts_given),
                                value = formatTaka(data.totalDiscount),
                                modifier = Modifier.weight(1f)
                            )
                            ExecutiveMetricCell(
                                label = stringResource(R.string.sales_report_avg_order_value),
                                value = formatTaka(average),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (canViewPnl) {
                    Spacer(modifier = Modifier.height(14.dp))

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.15f),
                        thickness = 0.8.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Net Profit Breakdown
                    Text(
                        text = stringResource(R.string.sales_report_net_profit_metric).uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = Color(0xFFC7D2FE)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NetProfitBreakdownRow(stringResource(R.string.sales_report_gross_profit), data.grossProfit, isTotal = false)
                    NetProfitBreakdownRow(stringResource(R.string.sales_report_discounts_given), -data.totalDiscount, isTotal = false)
                    if (data.totalLoyaltyDiscount > 0.0) {
                        NetProfitBreakdownRow(stringResource(R.string.sales_report_loyalty_points_redeemed), -data.totalLoyaltyDiscount, isTotal = false)
                    }
                    NetProfitBreakdownRow(stringResource(R.string.sales_report_total_expenses), -periodExpenseTotal, isTotal = false)
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    NetProfitBreakdownRow(stringResource(R.string.sales_report_net_profit), effectiveNetProfit, isTotal = true)
                }
            }
        }
    }
}

@Composable
private fun ExecutiveMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFC7D2FE),
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
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
                        text = stringResource(R.string.sales_report_view_historical_analytics),
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sales_report_empty_switch_week, stringResource(R.string.sales_report_period_week)),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (selectedPeriod != ReportPeriod.MONTH) {
                            FilledTonalButton(
                                onClick = { onSelectPeriod(ReportPeriod.MONTH) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sales_report_empty_switch_month, stringResource(R.string.sales_report_period_month)),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (selectedPeriod != ReportPeriod.YEAR) {
                            FilledTonalButton(
                                onClick = { onSelectPeriod(ReportPeriod.YEAR) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sales_report_empty_switch_year, stringResource(R.string.sales_report_period_year)),
                                    fontSize = 11.sp,
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
}

@Composable
private fun OverviewPanel(
    data: SalesReportData,
    metric: TrendMetric,
    periodExpenseTotal: Double = 0.0,
    totalSupplierDue: Double = 0.0,
    hasExpenseFeature: Boolean = false,
    hasSupplierFeature: Boolean = false,
    canViewPnl: Boolean = true,
    onNavigateToExpenses: (() -> Unit)? = null,
    onNavigateToSuppliers: (() -> Unit)? = null,
    onMetricChange: (TrendMetric) -> Unit
) {
    var viewMode by remember { mutableStateOf(if (data.dailyBreakdown.size <= 1) TrendViewMode.HOURLY else TrendViewMode.DAILY) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (hasExpenseFeature || hasSupplierFeature) {
            val netOperatingProfit = data.netProfit
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Realistic3DSupplierWarehouseIcon(size = 20.dp)
                        Text(
                            text = stringResource(R.string.sales_report_cross_ref_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val isBn = isAppBangla()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Period Expenses
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(enabled = onNavigateToExpenses != null) { onNavigateToExpenses?.invoke() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.sales_report_period_expenses),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF991B1B),
                                        minLines = 2,
                                        maxLines = 2,
                                        lineHeight = 13.sp,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = formatTaka(periodExpenseTotal),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFDC2626),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }

                                if (onNavigateToExpenses != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sales_report_view_expenses),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFFB91C1C)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }

                        if (canViewPnl) {
                            // 2. Net Operating Profit
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (netOperatingProfit >= 0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, if (netOperatingProfit >= 0) Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.sales_report_operating_profit),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (netOperatingProfit >= 0) Color(0xFF166534) else Color(0xFF991B1B),
                                            minLines = 2,
                                            maxLines = 2,
                                            lineHeight = 13.sp,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = formatTaka(netOperatingProfit),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (netOperatingProfit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (netOperatingProfit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626))
                                        )
                                        Text(
                                            text = if (netOperatingProfit >= 0) {
                                                if (isBn) "লাভজনক" else "Profitable"
                                            } else {
                                                if (isBn) "লোকসান" else "Net Loss"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (netOperatingProfit >= 0) Color(0xFF15803D) else Color(0xFFB91C1C),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Vendor Payables (Due)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(enabled = onNavigateToSuppliers != null) { onNavigateToSuppliers?.invoke() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.sales_report_supplier_due),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF92400E),
                                        minLines = 2,
                                        maxLines = 2,
                                        lineHeight = 13.sp,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = formatTaka(totalSupplierDue),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFD97706),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                }

                                if (onNavigateToSuppliers != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sales_report_view_suppliers),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB45309),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFFB45309)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

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
                    Text(stringResource(R.string.sales_report_performance_trend), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(
                        if (viewMode == TrendViewMode.DAILY) stringResource(R.string.sales_report_daily_sales_profit) else stringResource(R.string.sales_report_hourly_distribution_breakdown),
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
                        listOf(TrendViewMode.DAILY to stringResource(R.string.sales_report_trend_daily), TrendViewMode.HOURLY to stringResource(R.string.sales_report_trend_hourly)).forEach { (mode, label) ->
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

                if (canViewPnl) {
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
            }

            // 3D Trend Chart (Daily or Hourly)
            if (viewMode == TrendViewMode.DAILY) {
                TrendChart(data.dailyBreakdown, metric)

                val best = data.dailyBreakdown.maxByOrNull { if (metric == TrendMetric.SALES) it.revenue else it.profit }
                if (best != null) {
                    val day = DateFormatters.formatDayMonth(best.timestamp)
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
                                text = stringResource(R.string.sales_report_best_day_format, day),
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
                                val rawPeakItems = if (peak.itemsSold % 1.0 == 0.0) peak.itemsSold.toLong().toString() else String.format(Locale.US, "%.1f", peak.itemsSold)
                                val itemsSoldStr = if (isAppBangla()) toBanglaDigits(rawPeakItems) else rawPeakItems
                                Text(
                                    text = stringResource(R.string.sales_report_hourly_invoices_units_sold, peak.transactionCount, itemsSoldStr),
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFB45309),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
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

    val currentLocale = LocalConfiguration.current.locales[0]
    val hourLabels = remember(currentLocale) {
        (0..23).associateWith { hour ->
            val formatted = DateFormatters.formatHourAmPm(hour, Locale.getDefault())
            if (currentLocale.language == "bn") {
                toBanglaDigits(formatted)
            } else {
                formatted
            }
        }
    }

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
                val label = hourLabels[index] ?: when (index) {
                    0 -> "12A"
                    12 -> "12P"
                    in 1..11 -> "${index}A"
                    else -> "${index - 12}P"
                }
                val measured = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPeak) primaryColor else labelColor
                    )
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(x + (barWidth - measured.size.width) / 2f, h + 3.dp.toPx()),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPeak) primaryColor else labelColor
                    )
                )
            }
        }
    }
}

@Composable
private fun HourlyPanel(data: SalesReportData, canViewPnl: Boolean = true) {
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
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Peak Sales Hour Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp, vertical = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔥", fontSize = 11.5.sp)
                        Text(
                            text = stringResource(R.string.sales_report_peak_hour_badge),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF),
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = peakHour?.hourLabel ?: stringResource(R.string.sales_report_none),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E3A8A),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = peakHour?.let { formatTaka(it.revenue) } ?: "৳0",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Busiest Hour Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFECFDF5),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp, vertical = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⚡", fontSize = 11.5.sp)
                        Text(
                            text = stringResource(R.string.sales_report_busiest_hour_badge),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46),
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = busiestHour?.hourLabel ?: stringResource(R.string.sales_report_none),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF064E3B),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = busiestHour?.let { stringResource(R.string.sales_report_invoices_format, it.transactionCount) } ?: stringResource(R.string.sales_report_invoices_format, 0),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Active Hours Count Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFAF5FF),
                border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp, vertical = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⏱️", fontSize = 11.5.sp)
                        Text(
                            text = stringResource(R.string.sales_report_active_hours_badge),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8),
                            letterSpacing = 0.2.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    val activeHoursCount = activeHours.size
                    Text(
                        text = stringResource(R.string.sales_report_hours_format, activeHoursCount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF581C87),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.sales_report_tracked_format, data.hourlyBreakdown.size),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7E22CE),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
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
                        Text(stringResource(R.string.sales_report_hourly_volume_curve), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(stringResource(R.string.sales_report_24h_timeline_analysis), color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    }

                    if (canViewPnl) {
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
                        Text(stringResource(R.string.sales_report_timeslot_breakdown), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(stringResource(R.string.sales_report_active_windows_count, activeHours.size), color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                    }

                    Surface(
                        onClick = { showOnlyActiveHours = !showOnlyActiveHours },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (showOnlyActiveHours) stringResource(R.string.sales_report_active_only) else stringResource(R.string.sales_report_all_24h),
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
                        Text(stringResource(R.string.sales_report_no_hourly_sales), color = MaterialTheme.colorScheme.outline, fontSize = 12.5.sp)
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
                                                    Text(stringResource(R.string.sales_report_peak_tag), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF92400E), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                        }
                                        val rawHourQty = if (hourData.itemsSold % 1.0 == 0.0) hourData.itemsSold.toLong().toString() else String.format(Locale.US, "%.1f", hourData.itemsSold)
                                        val itemsQtyStr = if (isAppBangla()) toBanglaDigits(rawHourQty) else rawHourQty
                                        Text(
                                            text = stringResource(R.string.sales_report_hourly_invoices_units, hourData.transactionCount, itemsQtyStr),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
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
                                    if (canViewPnl) {
                                        Text(
                                            text = stringResource(R.string.sales_report_profit_prefix, formatTaka(hourData.profit)),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.extendedColors.success
                                        )
                                    }
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
                                    color = if (isPeak) MaterialTheme.extendedColors.warning else MaterialTheme.colorScheme.primary,
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
                val dayLabel = DateFormatters.formatDayOfWeek(points[index].timestamp)
                val measured = textMeasurer.measure(
                    text = dayLabel,
                    style = TextStyle(fontSize = 10.5.sp, color = labelColor)
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = dayLabel,
                    topLeft = Offset(x + (barWidth - measured.size.width) / 2f, h + 3.dp.toPx()),
                    style = TextStyle(fontSize = 10.5.sp, color = labelColor)
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
                    Text(stringResource(R.string.sales_report_top_products_title), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(stringResource(R.string.sales_report_ranked_by_revenue), color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
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
                            Text(stringResource(R.string.sales_report_product_units_sold, qStr), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(formatTaka(product.revenue), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, stringResource(R.string.sales_report_view_product_details_cd), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
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
                                    Text(stringResource(R.string.sales_report_barcode_sku_header), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                    Text(product.barcode, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.sales_report_avg_unit_price_header), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
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
                    Text(stringResource(R.string.sales_report_payment_channel_mix), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(stringResource(R.string.sales_report_revenue_collected_per_channel), color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
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
                            Text(stringResource(R.string.sales_report_payment_sales_percent, payment.transactionCount, percent), color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
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

            if (data.paymentBreakdown.isNotEmpty()) {
                val totalAmount = data.paymentBreakdown.sumOf { it.amount }
                val totalCount = data.paymentBreakdown.sumOf { it.transactionCount }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.grand_total),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.sales_report_total_transactions, totalCount),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = formatTaka(totalAmount),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// PHASE 3: REGULATORY DISPENSING REGISTERS & COMPLIANCE (DGDA & NARCOTICS)
// ============================================================================

fun isControlledOrNarcoticMedicine(genericName: String?, productName: String? = null): Boolean {
    val g = (genericName ?: "").lowercase()
    val p = (productName ?: "").lowercase()
    val controlledKeywords = listOf(
        "morphine", "pethidine", "fentanyl", "tramadol", "tapentadol", "codeine", "buprenorphine",
        "diazepam", "clonazepam", "alprazolam", "lorazepam", "midazolam", "bromazepam", "clobazam",
        "chlordiazepoxide", "nitrazepam", "flurazepam", "oxazepam", "zolpidem", "zopiclone", "eszopiclone",
        "phenobarbitone", "phenobarbital", "ketamine", "pentazocine", "methadone", "methylphenidate"
    )
    return controlledKeywords.any { g.contains(it) || p.contains(it) }
}

data class AntibioticDispenseRecord(
    val saleId: Long,
    val timestamp: Long,
    val patientName: String,
    val patientPhone: String,
    val doctorName: String,
    val doctorBmdcReg: String,
    val hasPrescriptionPhoto: Boolean,
    val isEmergencyOtc: Boolean,
    val medicineName: String,
    val genericName: String,
    val batchNumber: String,
    val expiryDate: String,
    val quantity: Double,
    val unitType: String,
    val price: Double,
    val totalAmount: Double,
    val dosageSchedule: String?,
    val prescriptionImageUri: String?
)

data class NarcoticsDispenseRecord(
    val saleId: Long,
    val timestamp: Long,
    val patientName: String,
    val patientPhone: String,
    val doctorName: String,
    val doctorBmdcReg: String,
    val hasPrescriptionPhoto: Boolean,
    val medicineName: String,
    val controlledGeneric: String,
    val batchNumber: String,
    val expiryDate: String,
    val quantity: Double,
    val unitType: String,
    val price: Double,
    val totalAmount: Double,
    val dosageSchedule: String?,
    val prescriptionImageUri: String?
)

fun generateAntibioticRegisterCsv(records: List<AntibioticDispenseRecord>, rangeLabel: String): String {
    val sb = StringBuilder()
    sb.append("DGDA Antibiotic Dispensing Register (ডাইরেক্টরেট জেনারেল অব ড্রাগ অ্যাডমিনিস্ট্রেশন রেগুলেশন)\n")
    sb.append("Period: $rangeLabel, Generated: ${DateFormatters.formatDateTime(System.currentTimeMillis())}\n\n")
    sb.append("SL,Date & Time,Invoice ID,Patient Name,Patient Phone,Doctor Name,BMDC Reg No,Prescription Status,Medicine Brand,Generic Molecule,Batch No,Expiry Date,Qty,Unit,Unit Price,Total Price,Dosage Schedule\n")
    records.forEachIndexed { index, r ->
        val dateStr = DateFormatters.formatDateTime(r.timestamp).replace(",", " ")
        val patientName = r.patientName.replace(",", " ")
        val doctorName = r.doctorName.replace(",", " ")
        val status = if (r.hasPrescriptionPhoto) "Prescription Attached" else if (r.isEmergencyOtc) "Emergency OTC Override" else if (r.doctorName.isNotBlank()) "Doctor Rx Verified" else "OTC"
        val medName = r.medicineName.replace(",", " ")
        val genName = r.genericName.replace(",", " ")
        val dosage = (r.dosageSchedule ?: "N/A").replace(",", " ")
        sb.append("${index + 1},\"$dateStr\",\"#${r.saleId}\",\"$patientName\",\"${r.patientPhone}\",\"$doctorName\",\"${r.doctorBmdcReg}\",\"$status\",\"$medName\",\"$genName\",\"${r.batchNumber}\",\"${r.expiryDate}\",${r.quantity},\"${r.unitType}\",${r.price},${r.totalAmount},\"$dosage\"\n")
    }
    return sb.toString()
}

fun generateNarcoticsRegisterCsv(records: List<NarcoticsDispenseRecord>, rangeLabel: String): String {
    val sb = StringBuilder()
    sb.append("Department of Narcotics Control (DNC) & DGDA Schedule G Register (মাদকদ্রব্য ও নিয়ন্ত্রিত ওষুধ রেজিস্টার)\n")
    sb.append("Period: $rangeLabel, Generated: ${DateFormatters.formatDateTime(System.currentTimeMillis())}\n\n")
    sb.append("SL,Date & Time,Invoice ID,Patient Name,Patient Phone,Doctor Name,BMDC Reg No,Prescription Status,Controlled Medicine,Active Controlled Molecule,Batch No,Expiry Date,Qty,Unit,Unit Price,Total Price,Dosage Schedule\n")
    records.forEachIndexed { index, r ->
        val dateStr = DateFormatters.formatDateTime(r.timestamp).replace(",", " ")
        val patientName = r.patientName.replace(",", " ")
        val doctorName = r.doctorName.replace(",", " ")
        val status = if (r.hasPrescriptionPhoto) "Prescription Attached" else if (r.doctorName.isNotBlank()) "Doctor Rx Verified" else "Unverified"
        val medName = r.medicineName.replace(",", " ")
        val genName = r.controlledGeneric.replace(",", " ")
        val dosage = (r.dosageSchedule ?: "N/A").replace(",", " ")
        sb.append("${index + 1},\"$dateStr\",\"#${r.saleId}\",\"$patientName\",\"${r.patientPhone}\",\"$doctorName\",\"${r.doctorBmdcReg}\",\"$status\",\"$medName\",\"$genName\",\"${r.batchNumber}\",\"${r.expiryDate}\",${r.quantity},\"${r.unitType}\",${r.price},${r.totalAmount},\"$dosage\"\n")
    }
    return sb.toString()
}

@Composable
fun PrescriptionImageViewerDialog(
    imageUriString: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageBitmap = remember(imageUriString) {
        try {
            val uri = android.net.Uri.parse(imageUriString)
            if (uri.scheme == "content" || uri.scheme == "file") {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } else {
                val file = java.io.File(imageUriString)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(20.dp))
                        Text(
                            text = stringResource(R.string.prescription_view_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (imageBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = imageBitmap,
                        contentDescription = "Prescription Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("প্রেসক্রিপশন ছবি লোড করা সম্ভব হয়নি", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AntibioticRegisterPanel(
    records: List<AntibioticDispenseRecord>,
    onExportCsv: () -> Unit,
    onViewPrescription: (String) -> Unit
) {
    val totalDispensed = records.size
    val totalRevenue = records.sumOf { it.totalAmount }
    val rxVerifiedCount = records.count { !it.isEmergencyOtc && it.doctorName.isNotBlank() }
    val emergencyOtcCount = records.count { it.isEmergencyOtc }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Regulatory Header Card
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Realistic3DAmberHazardDiamond(size = 28.dp)
                    Column {
                        Text(
                            text = stringResource(R.string.antibiotic_register_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.antibiotic_register_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Summary KPIs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("মোট ডিসপেন্স", fontSize = 10.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                            Text("$totalDispensed টি", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB45309))
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("প্রেসক্রিপশন", fontSize = 10.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                            Text("$rxVerifiedCount টি", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF047857))
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("জরুরি OTC", fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                            Text("$emergencyOtcCount টি", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB91C1C))
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("মোট বিক্রি", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(formatTaka(totalRevenue), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Export Button
                Button(
                    onClick = onExportCsv,
                    enabled = records.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.antibiotic_register_export_csv), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // List of entries
        if (records.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.antibiotic_register_empty),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            records.forEach { record ->
                AntibioticRecordCard(record = record, onViewPrescription = onViewPrescription)
            }
        }
    }
}

@Composable
private fun AntibioticRecordCard(
    record: AntibioticDispenseRecord,
    onViewPrescription: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: Invoice & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "#SALE-${record.saleId}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = DateFormatters.formatDateTime(record.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Medicine Name & Molecule
            Column {
                Text(
                    text = record.medicineName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (record.genericName.isNotBlank()) {
                    Text(
                        text = "জেনেরিক: ${record.genericName}",
                        fontSize = 11.5.sp,
                        color = Color(0xFF0284C7),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Batch & Expiry Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.batchNumber.isNotBlank() && record.batchNumber != "N/A") {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("🏷️ ব্যাচ: ${record.batchNumber}", fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (record.expiryDate.isNotBlank() && record.expiryDate != "N/A") {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("⏳ মেয়াদ: ${record.expiryDate}", fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (!record.dosageSchedule.isNullOrBlank()) {
                    Text("• ${record.dosageSchedule}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Patient & Doctor Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("👤 রোগী: ${record.patientName}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    if (record.patientPhone.isNotBlank() && record.patientPhone != "N/A") {
                        Text("📞 ${record.patientPhone}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (record.doctorName.isNotBlank()) {
                        Text("🩺 ডা: ${record.doctorName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        if (record.doctorBmdcReg.isNotBlank()) {
                            Text("BMDC: ${record.doctorBmdcReg}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    } else if (record.isEmergencyOtc) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEF2F2)) {
                            Text("⚠️ জরুরি OTC", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // Dispensed Quantity & Total + Prescription Photo Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val unitLabel = when (record.unitType.lowercase()) {
                    "strip" -> "পাতা"
                    "box" -> "বক্স"
                    else -> "পিস"
                }
                Text(
                    text = "${record.quantity.toInt()} $unitLabel × ${formatTaka(record.price)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.hasPrescriptionPhoto && !record.prescriptionImageUri.isNullOrBlank()) {
                        Surface(
                            onClick = { onViewPrescription(record.prescriptionImageUri) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE11D48).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(12.dp))
                                Text("প্রেসক্রিপশন", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                            }
                        }
                    }
                    Text(
                        text = formatTaka(record.totalAmount),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun NarcoticsRegisterPanel(
    records: List<NarcoticsDispenseRecord>,
    onExportCsv: () -> Unit,
    onViewPrescription: (String) -> Unit
) {
    val totalDispensed = records.size
    val totalRevenue = records.sumOf { it.totalAmount }
    val uniquePatients = records.map { it.patientPhone }.distinct().size

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Regulatory Header Card
        Card(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Realistic3DStealthShieldIcon(isStealthActive = true, size = 28.dp)
                    Column {
                        Text(
                            text = stringResource(R.string.narcotics_register_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.narcotics_register_desc),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Summary KPIs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("মোট ডিসপেন্স", fontSize = 10.sp, color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
                            Text("$totalDispensed টি", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4338CA))
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF06B6D4).copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("রোগী সংখ্যা", fontSize = 10.sp, color = Color(0xFF0891B2), fontWeight = FontWeight.Bold)
                            Text("$uniquePatients জন", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0E7490))
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("মোট বিক্রি", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(formatTaka(totalRevenue), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Export Button
                Button(
                    onClick = onExportCsv,
                    enabled = records.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.narcotics_register_export_csv), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // List of entries
        if (records.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.narcotics_register_empty),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            records.forEach { record ->
                NarcoticsRecordCard(record = record, onViewPrescription = onViewPrescription)
            }
        }
    }
}

@Composable
private fun NarcoticsRecordCard(
    record: NarcoticsDispenseRecord,
    onViewPrescription: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: Invoice & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "#SALE-${record.saleId}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = DateFormatters.formatDateTime(record.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Medicine Name & Molecule
            Column {
                Text(
                    text = record.medicineName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "নিয়ন্ত্রিত মলিকিউল: ${record.controlledGeneric}",
                    fontSize = 11.5.sp,
                    color = Color(0xFF9333EA),
                    fontWeight = FontWeight.Bold
                )
            }

            // Batch & Expiry Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.batchNumber.isNotBlank() && record.batchNumber != "N/A") {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("🏷️ ব্যাচ: ${record.batchNumber}", fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (record.expiryDate.isNotBlank() && record.expiryDate != "N/A") {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("⏳ মেয়াদ: ${record.expiryDate}", fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                if (!record.dosageSchedule.isNullOrBlank()) {
                    Text("• ${record.dosageSchedule}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Patient & Doctor Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("👤 রোগী: ${record.patientName}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    if (record.patientPhone.isNotBlank() && record.patientPhone != "N/A") {
                        Text("📞 ${record.patientPhone}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (record.doctorName.isNotBlank()) {
                        Text("🩺 ডা: ${record.doctorName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        if (record.doctorBmdcReg.isNotBlank()) {
                            Text("BMDC: ${record.doctorBmdcReg}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        Text("ডাক্তারের নাম অমিল", fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Dispensed Quantity & Total + Prescription Photo Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val unitLabel = when (record.unitType.lowercase()) {
                    "strip" -> "পাতা"
                    "box" -> "বক্স"
                    else -> "পিস"
                }
                Text(
                    text = "${record.quantity.toInt()} $unitLabel × ${formatTaka(record.price)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.hasPrescriptionPhoto && !record.prescriptionImageUri.isNullOrBlank()) {
                        Surface(
                            onClick = { onViewPrescription(record.prescriptionImageUri) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE11D48).copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, Color(0xFFE11D48).copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(12.dp))
                                Text("প্রেসক্রিপশন", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE11D48))
                            }
                        }
                    }
                    Text(
                        text = formatTaka(record.totalAmount),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}


