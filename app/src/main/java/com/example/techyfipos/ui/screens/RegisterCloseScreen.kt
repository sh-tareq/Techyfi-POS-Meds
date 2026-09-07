package com.example.techyfipos.ui.screens

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.techyfipos.utils.isZeroMoney
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.RegisterClosing
import com.example.techyfipos.ui.components.Realistic3DCashInStashIcon
import com.example.techyfipos.ui.components.Realistic3DCashVaultRegisterIcon
import com.example.techyfipos.ui.components.Realistic3DExpenseReceiptIcon
import com.example.techyfipos.ui.components.Realistic3DRegisterAuditZReportIcon
import com.example.techyfipos.ui.components.Realistic3DVaultIcon
import com.example.techyfipos.ui.viewmodel.ExpenseViewModel
import com.example.techyfipos.ui.viewmodel.RegisterClosingViewModel
import com.example.techyfipos.ui.viewmodel.SalesReportData
import com.example.techyfipos.ui.viewmodel.SalesViewModel
import com.example.techyfipos.ui.viewmodel.REGISTER_CLOSE_DIGITAL_METHODS
import com.example.techyfipos.ui.viewmodel.calculateSaleDueAmount
import com.example.techyfipos.ui.viewmodel.cashRefundAmount
import com.example.techyfipos.ui.viewmodel.splitCashAndDigitalAmounts
import com.example.techyfipos.utils.findActivity
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.generateXReportJpeg
import com.example.techyfipos.utils.generateXReportPdf
import com.example.techyfipos.utils.generateZReportJpeg
import com.example.techyfipos.utils.generateZReportPdf
import com.example.techyfipos.utils.printBillJpeg
import com.example.techyfipos.utils.saveBillJpegToDownloads
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ZReportFormat(val label: String, val description: String, val icon: String) {
    JPEG("JPEG Image", "High-resolution 80mm receipt image (Default)", "🖼️"),
    PDF("PDF Document", "Standard printable PDF document", "📄"),
    CSV("CSV Spreadsheet", "Raw data breakdown for Excel & Sheets", "📊")
}

enum class DenomCounterMode {
    DETAILED,
    QUICK
}

data class DenominationItem(
    val value: Int,
    val label: String,
    val isCoin: Boolean = false,
    val badgeColor: Color
)

val BANGLADESHI_DENOMINATIONS = listOf(
    DenominationItem(1000, "৳1,000", false, Color(0xFF7C3AED)), // Ruby Purple
    DenominationItem(500, "৳500", false, Color(0xFF059669)),   // Emerald Green
    DenominationItem(200, "৳200", false, Color(0xFFD97706)),   // Amber Yellow
    DenominationItem(100, "৳100", false, Color(0xFF0284C7)),   // Blue
    DenominationItem(50, "৳50", false, Color(0xFFEA580C)),    // Orange
    DenominationItem(20, "৳20", false, Color(0xFF4F46E5)),    // Indigo
    DenominationItem(10, "৳10", false, Color(0xFF9333EA)),    // Purple
    DenominationItem(5, "৳5", false, Color(0xFF0D9488)),      // Teal
    DenominationItem(2, "৳2", false, Color(0xFF78350F)),      // Earth Brown
    DenominationItem(1, "৳1", true, Color(0xFFB45309))        // Bronze Coin
)

private fun todayRange(): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
    return start to (end + 1)
}

internal fun registerShiftStart(todayStart: Long, closings: List<RegisterClosing>): Long {
    val latestClose = closings.firstOrNull()
    return latestClose?.let { it.timestamp + 1L } ?: todayStart
}

private fun formatShiftDuration(shiftStart: Long): String {
    val durationMs = (System.currentTimeMillis() - shiftStart).coerceAtLeast(0L)
    val hours = durationMs / 3600000
    val minutes = (durationMs % 3600000) / 60000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayStartCloseScreen(
    registerClosingViewModel: RegisterClosingViewModel,
    expenseViewModel: ExpenseViewModel,
    salesViewModel: SalesViewModel,
    onBack: () -> Unit,
    onNavigateToExpenses: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val closings by registerClosingViewModel.allClosings.collectAsStateWithLifecycle(initialValue = emptyList())
    val shiftState by registerClosingViewModel.shiftState.collectAsStateWithLifecycle()
    val liveDrawerCash by expenseViewModel.liveDrawerCash.collectAsStateWithLifecycle(0.0)
    val expenses by expenseViewModel.allExpenses.collectAsStateWithLifecycle()
    val sales by expenseViewModel.allSales.collectAsStateWithLifecycle()
    val returns by expenseViewModel.allReturns.collectAsStateWithLifecycle()
    val duePayments by expenseViewModel.allDuePayments.collectAsStateWithLifecycle()
    val supplierPayments by expenseViewModel.allSupplierPayments.collectAsStateWithLifecycle()
    val salesSnapshot by salesViewModel.salesWithItems.collectAsStateWithLifecycle()
    val providerSales by expenseViewModel.providerSalesBreakdown.collectAsStateWithLifecycle()

    val todayStart = remember { todayRange().first }
    val shiftStart = remember(closings, todayStart, shiftState) {
        shiftState?.startedAt?.takeIf { it > 0L } ?: registerShiftStart(todayStart, closings)
    }
    val shiftSales = remember(sales, shiftStart) { sales.filter { it.timestamp >= shiftStart } }
    val cashSales = remember(shiftSales) { shiftSales.sumOf { splitCashAndDigitalAmounts(it).first } }
    val dueDigitalCollected = remember(duePayments, shiftStart) {
        duePayments.filter {
            it.timestamp >= shiftStart && REGISTER_CLOSE_DIGITAL_METHODS.contains(it.paymentMethod.trim().lowercase())
        }.sumOf { it.amountPaid }
    }
    val digitalSales = remember(shiftSales, dueDigitalCollected) {
        shiftSales.sumOf { splitCashAndDigitalAmounts(it).second } + dueDigitalCollected
    }
    val cashIn = remember(expenses, shiftStart) {
        expenses.filter { it.timestamp >= shiftStart && it.type == "CASH_IN" && it.paymentSource == "Cash" }.sumOf { it.amount }
    }
    val cashOut = remember(expenses, shiftStart) {
        expenses.filter { it.timestamp >= shiftStart && it.type == "EXPENSE" && it.paymentSource == "Cash" && it.category != "Supplier Payment" }.sumOf { it.amount }
    }
    val dueCashCollected = remember(duePayments, shiftStart) {
        duePayments.filter { it.timestamp >= shiftStart && it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.amountPaid }
    }
    val supplierCashPaid = remember(supplierPayments, shiftStart) {
        supplierPayments.filter { it.timestamp >= shiftStart && it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.amountPaid }
    }
    val cashRefunds = remember(returns, sales, shiftStart) {
        val salesById = sales.associateBy { it.id }
        returns.filter { it.timestamp >= shiftStart }
            .sumOf { refund -> cashRefundAmount(refund, salesById[refund.saleId]) }
    }
    val digitalRefunds = remember(returns, sales, shiftStart) {
        val salesById = sales.associateBy { it.id }
        returns.filter { it.timestamp >= shiftStart }
            .sumOf { refund ->
                val method = refund.refundMethod
                val sale = salesById[refund.saleId]
                when {
                    method.startsWith("MFS") || method.startsWith("Card") || method.startsWith("bKash") || method.startsWith("Nagad") || method.startsWith("Rocket") -> refund.externalRefundAmount
                    method.startsWith("Original method") && sale != null -> {
                        val (_, digitalPortion) = splitCashAndDigitalAmounts(sale)
                        val originalCollected = (sale.grandTotal - calculateSaleDueAmount(sale)).coerceAtLeast(0.0)
                        if (originalCollected > 0.0) {
                            refund.externalRefundAmount * (digitalPortion / originalCollected).coerceIn(0.0, 1.0)
                        } else 0.0
                    }
                    else -> 0.0
                }
            }
    }
    val netDigitalSales = remember(digitalSales, digitalRefunds) {
        (digitalSales - digitalRefunds).coerceAtLeast(0.0)
    }
    val hasShiftTransactions = remember(shiftSales, cashIn, cashOut, dueCashCollected, supplierCashPaid, cashRefunds) {
        shiftSales.isNotEmpty() || cashIn > 0.0 || cashOut > 0.0 || dueCashCollected > 0.0 || supplierCashPaid > 0.0 || cashRefunds > 0.0
    }

    var startingFloatText by remember { mutableStateOf("0") }
    LaunchedEffect(shiftState?.startedAt) {
        startingFloatText = (shiftState?.startingFloat ?: 0.0).toString().removeSuffix(".0")
    }
    val startingFloat = startingFloatText.toDoubleOrNull() ?: 0.0
    LaunchedEffect(startingFloat) {
        if (startingFloat != (shiftState?.startingFloat ?: 0.0)) {
            registerClosingViewModel.updateStartingFloat(startingFloat)
        }
    }

    val expectedCash = startingFloat + cashSales + cashIn + dueCashCollected - cashOut - supplierCashPaid - cashRefunds

    // Counted physical cash state populated from Denomination Counter or direct close
    var appliedCountedCash by remember { mutableStateOf<Double?>(null) }
    var appliedAuditNote by remember { mutableStateOf("") }

    // Dialog flags
    var showDenomDialog by remember { mutableStateOf(false) }
    var showCashInDialog by remember { mutableStateOf(false) }
    var showSafeDropDialog by remember { mutableStateOf(false) }
    var showEditFloatDialog by remember { mutableStateOf(false) }
    var showCloseReconcileDialog by remember { mutableStateOf(false) }

    // Z-Report / X-Report dialog flags
    var showPnlZReportDialog by remember { mutableStateOf(false) }
    var showXReportDialog by remember { mutableStateOf(false) }
    var showShareFormatDialog by remember { mutableStateOf(false) }
    var showXShareFormatDialog by remember { mutableStateOf(false) }

    val rangeStart = shiftStart
    var report by remember { mutableStateOf<SalesReportData?>(null) }
    LaunchedEffect(rangeStart, salesSnapshot) {
        report = salesViewModel.buildReport(rangeStart, System.currentTimeMillis(), salesSnapshot)
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val rangeLabel = remember(rangeStart) { "Shift: ${dateFormat.format(Date(rangeStart))}" }
    var selectedHistoricalClosing by remember { mutableStateOf<RegisterClosing?>(null) }

    val successMessage = stringResource(R.string.register_close_success_message)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        com.example.techyfipos.ui.components.Realistic3DRegisterReceiptIcon(size = if (isTablet) 24.dp else 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.register_close_screen_title),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (isTablet) 18.sp else 16.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.register_close_screen_subtitle),
                                fontSize = if (isTablet) 11.5.sp else 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    val showFullReportLabel = isTablet || configuration.screenWidthDp >= 440
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (showFullReportLabel) 6.dp else 4.dp),
                        modifier = Modifier.padding(end = if (showFullReportLabel) 8.dp else 4.dp)
                    ) {
                        // Mid-Shift X-Report Action
                        Surface(
                            onClick = { showXReportDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F766E),
                            border = BorderStroke(1.dp, Color(0x5599F6E4)),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (showFullReportLabel) 9.dp else 7.dp,
                                    vertical = if (showFullReportLabel) 6.dp else 5.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (showFullReportLabel) 4.dp else 2.dp)
                            ) {
                                Text("📋", fontSize = if (showFullReportLabel) 12.sp else 11.sp)
                                Text(
                                    text = if (showFullReportLabel) "X-Report" else "X",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Shift Closing Z-Report Action
                        Surface(
                            onClick = { showPnlZReportDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E1B4B),
                            border = BorderStroke(1.dp, Color(0x55C7D2FE)),
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (showFullReportLabel) 9.dp else 7.dp,
                                    vertical = if (showFullReportLabel) 6.dp else 5.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (showFullReportLabel) 5.dp else 3.dp)
                            ) {
                                Realistic3DRegisterAuditZReportIcon(size = if (showFullReportLabel) 16.dp else 14.dp)
                                Text(
                                    text = if (showFullReportLabel) "Z-Report" else "Z",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
        ) {
            // 1. Photorealistic 3D Hero Card: Today's Drawer Cash Vault
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color(0x2238BDF8)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF172554), Color(0xFF0B1329))
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF059669).copy(alpha = 0.25f),
                                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f)),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Realistic3DCashVaultRegisterIcon(size = 38.dp)
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = stringResource(R.string.day_close_vault_label),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF94A3B8),
                                            letterSpacing = 0.8.sp
                                        )
                                        Text(
                                            text = formatTaka(liveDrawerCash),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Live Synced Pill Badge with pulsing glow
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val pulseAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000),
                                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                    ),
                                    label = "pulseAlpha"
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.20f),
                                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .alpha(pulseAlpha)
                                                .background(Color(0xFF34D399), CircleShape)
                                        )
                                        Text(
                                            text = stringResource(R.string.day_close_live_sync),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF34D399)
                                        )
                                    }
                                }
                            }

                            // Shift Status Mini Strip
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("🕒", fontSize = 11.5.sp)
                                        val activeShiftIndex = closings.size + 1
                                        Text(
                                            text = stringResource(R.string.day_close_shift_active, activeShiftIndex.toString(), formatShiftDuration(shiftStart)),
                                            fontSize = 11.sp,
                                            color = Color(0xFFCBD5E1),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text("👤", fontSize = 11.5.sp)
                                        Text(
                                            text = stringResource(R.string.day_close_cashier_label, registerClosingViewModel.activeCashierName),
                                            fontSize = 11.sp,
                                            color = Color(0xFFCBD5E1),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // 2-Column Cash Velocity Flow Strip
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Realistic3DCashInStashIcon(size = 26.dp)
                                        Column {
                                            Text(
                                                text = stringResource(R.string.day_close_float_in_label),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Text(
                                                text = "+${formatTaka(cashIn)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF34D399)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Realistic3DExpenseReceiptIcon(size = 26.dp)
                                        Column {
                                            Text(
                                                text = stringResource(R.string.day_close_cash_out_label),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Text(
                                                text = "-${formatTaka(cashOut)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFB7185)
                                            )
                                        }
                                    }
                                }
                            }

                            // Dual Vault Action Buttons (48dp height touch targets)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showCashInDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("➕", fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.day_close_add_float_btn),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Button(
                                    onClick = { showSafeDropDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    border = BorderStroke(1.dp, Color(0xFF64748B).copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("🔒", fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.day_close_safe_drop_btn),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Fast Navigation Tool Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Count Notes (Highlighted)
                    Surface(
                        onClick = { showDenomDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.5.dp, Color(0xFFFDE68A)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🧮", fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.day_close_tool_count_notes),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = stringResource(R.string.day_close_tool_count_notes_sub),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                        }
                    }

                    // Card 2: Opening Float
                    Surface(
                        onClick = {
                            if (hasShiftTransactions) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Opening float is locked once transactions exist in the shift.")
                                }
                            } else {
                                showEditFloatDialog = true
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(if (hasShiftTransactions) "🔒" else "💵", fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (hasShiftTransactions) "Float (Locked)" else stringResource(R.string.day_close_tool_open_float),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasShiftTransactions) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatTaka(startingFloat),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (hasShiftTransactions) MaterialTheme.colorScheme.outline else Color(0xFF4F46E5)
                            )
                        }
                    }

                    // Card 3: Expenses
                    Surface(
                        onClick = onNavigateToExpenses,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🧾", fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.day_close_tool_expenses),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatTaka(cashOut),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE11D48)
                            )
                        }
                    }
                }
            }

            // 2b. Provider-level Sales Breakdown (Cash, bKash, Nagad, Rocket, Card, Due)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💳 Sales By Payment Method",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Shift Total: ${formatTaka(providerSales.cash + providerSales.bkash + providerSales.nagad + providerSales.rocket + providerSales.card + providerSales.due)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Cash
                            PaymentMethodBreakdownTile(
                                method = "Cash",
                                label = "Cash",
                                amount = providerSales.cash,
                                themeColor = Color(0xFF059669),
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                            // bKash
                            PaymentMethodBreakdownTile(
                                method = "bKash",
                                label = "bKash",
                                amount = providerSales.bkash,
                                themeColor = Color(0xFFE11D48),
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                            // Nagad
                            PaymentMethodBreakdownTile(
                                method = "Nagad",
                                label = "Nagad",
                                amount = providerSales.nagad,
                                themeColor = Color(0xFFEA580C),
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Rocket
                            PaymentMethodBreakdownTile(
                                method = "Rocket",
                                label = "Rocket",
                                amount = providerSales.rocket,
                                themeColor = Color(0xFF7C3AED),
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                            // Card
                            PaymentMethodBreakdownTile(
                                method = "Card",
                                label = "Card",
                                amount = providerSales.card,
                                themeColor = Color(0xFF0284C7),
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                            // Due
                            PaymentMethodBreakdownTile(
                                method = "Due",
                                label = "Due",
                                amount = providerSales.due,
                                themeColor = Color(0xFFD97706),
                                isTablet = isTablet,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Shift Financial Reconciliation Math Breakdown Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("📊", fontSize = 16.sp)
                                Text(
                                    text = stringResource(R.string.day_close_shift_math_title),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF059669).copy(alpha = 0.25f)
                            ) {
                                Text(
                                    text = "Audited",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFF334155))

                        SummaryRow(stringResource(R.string.day_close_math_starting_float), formatTaka(startingFloat))
                        SummaryRow(stringResource(R.string.day_close_math_cash_sales), "+${formatTaka(cashSales)}", color = Color(0xFF34D399))
                        SummaryRow(stringResource(R.string.day_close_math_cash_in), "+${formatTaka(cashIn)}", color = Color(0xFF34D399))
                        if (dueCashCollected > 0.0) {
                            SummaryRow(stringResource(R.string.day_close_math_due_collected), "+${formatTaka(dueCashCollected)}", color = Color(0xFF34D399))
                        }
                        SummaryRow(stringResource(R.string.day_close_math_expenses), "−${formatTaka(cashOut)}", color = Color(0xFFFB7185))
                        if (supplierCashPaid > 0.0) {
                            SummaryRow(stringResource(R.string.day_close_math_supplier), "−${formatTaka(supplierCashPaid)}", color = Color(0xFFFB7185))
                        }
                        if (cashRefunds > 0.0) {
                            SummaryRow(stringResource(R.string.day_close_math_refunds), "−${formatTaka(cashRefunds)}", color = Color(0xFFFB7185))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0xFF334155))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.day_close_math_expected),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = formatTaka(expectedCash),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }
            }

            // 4. Primary Day Close CTA
            item {
                val canCloseRegister = hasShiftTransactions || startingFloat > 0.0
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = canCloseRegister,
                        onClick = { showCloseReconcileDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C3AED),
                            disabledContainerColor = Color(0xFF334155).copy(alpha = 0.6f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("🔒", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (canCloseRegister) stringResource(R.string.day_close_end_shift_btn) else "Shift Clean / No Transactions",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = if (canCloseRegister) Color.White else Color.LightGray
                        )
                    }
                    if (!canCloseRegister) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "✨ Active shift has no new transactions. Set float or make a sale to open a new shift.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 5. Previous Closings History Accordion Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.day_close_recent_closings_title),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (closings.isNotEmpty()) {
                        Text(
                            text = "${closings.size} records",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (closings.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.register_close_history_empty),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(closings) { closing ->
                    RegisterClosingRow(closing, onClick = { selectedHistoricalClosing = closing })
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS
    // =========================================================================

    // 0. Historical Closing Detail Dialog
    selectedHistoricalClosing?.let { closing ->
        HistoricalClosingDetailDialog(
            closing = closing,
            onDismiss = { selectedHistoricalClosing = null }
        )
    }

    // 1. Dual-Mode Bangladeshi Denomination Counter Dialog
    if (showDenomDialog) {
        DenominationCounterDialog(
            expectedCash = expectedCash,
            initialCountedCash = appliedCountedCash,
            onDismiss = { showDenomDialog = false },
            onApply = { countedTotal, note ->
                appliedCountedCash = countedTotal
                appliedAuditNote = note
                showDenomDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Physical cash counted: ${formatTaka(countedTotal)}")
                }
            }
        )
    }

    // 2. Add Cash Float Dialog
    if (showCashInDialog) {
        AddCashFloatDialog(
            onDismiss = { showCashInDialog = false },
            onConfirm = { amount, reason ->
                expenseViewModel.recordCashIn(
                    title = reason.ifBlank { "Cash Float Top-up" },
                    amount = amount,
                    paymentSource = "Cash",
                    note = "Added from Day Start & Close"
                )
                showCashInDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Cash float added: +${formatTaka(amount)}")
                }
            }
        )
    }

    // 3. Safe Drop Dialog
    if (showSafeDropDialog) {
        SafeDropDialog(
            currentDrawerCash = liveDrawerCash,
            onDismiss = { showSafeDropDialog = false },
            onConfirm = { amount, destination ->
                expenseViewModel.addExpense(
                    title = destination.ifBlank { "Safe Drop to Vault" },
                    category = "Safe Drop",
                    type = "EXPENSE",
                    amount = amount,
                    paymentSource = "Cash",
                    note = "Safe drop transfer from drawer"
                )
                showSafeDropDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Safe drop recorded: -${formatTaka(amount)}")
                }
            }
        )
    }

    // 4. Edit Opening Float Dialog
    if (showEditFloatDialog) {
        EditStartingFloatDialog(
            currentFloat = startingFloat,
            onDismiss = { showEditFloatDialog = false },
            onSave = { newFloat ->
                startingFloatText = newFloat.toString().removeSuffix(".0")
                registerClosingViewModel.updateStartingFloat(newFloat)
                showEditFloatDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Opening float updated: ${formatTaka(newFloat)}")
                }
            }
        )
    }

    // 5. Close Register Reconciliation Dialog
    if (showCloseReconcileDialog) {
        CloseRegisterReconciliationDialog(
            expectedCash = expectedCash,
            initialCountedCash = appliedCountedCash ?: expectedCash,
            initialNotes = appliedAuditNote,
            onDismiss = { showCloseReconcileDialog = false },
            onOpenDenominationCounter = {
                showCloseReconcileDialog = false
                showDenomDialog = true
            },
            onConfirmClose = { actualCash, notes ->
                registerClosingViewModel.closeRegister(
                    startingFloat = startingFloat,
                    cashSales = cashSales,
                    digitalSales = digitalSales,
                    cashIn = cashIn,
                    cashOut = cashOut,
                    cashRefunds = cashRefunds,
                    shiftStartedAt = shiftStart,
                    expectedCash = expectedCash,
                    actualCountedCash = actualCash,
                    notes = notes,
                    bkashSales = providerSales.bkash,
                    nagadSales = providerSales.nagad,
                    rocketSales = providerSales.rocket,
                    cardSales = providerSales.card,
                    dueSales = providerSales.due,
                    onResult = { result ->
                        result.onSuccess {
                            showCloseReconcileDialog = false
                            showPnlZReportDialog = true
                            coroutineScope.launch { snackbarHostState.showSnackbar(successMessage) }
                        }.onFailure {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(it.localizedMessage ?: "Unable to close register")
                            }
                        }
                    }
                )
            }
        )
    }

    // 6. X-Report Dialog (Interim)
    if (showXReportDialog && report != null) {
        val r = report!!
        ZReportDialog(
            data = r,
            rangeLabel = rangeLabel,
            isInterimX = true,
            onDismiss = { showXReportDialog = false },
            onPrint = {
                try {
                    val jpegFile = generateXReportJpeg(context, r, rangeLabel)
                    printBillJpeg(context, jpegFile, "X-Report - $rangeLabel")
                } catch (e: Exception) {
                    Toast.makeText(context, "Print error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            },
            onSave = {
                try {
                    val jpegFile = generateXReportJpeg(context, r, rangeLabel)
                    val ok = saveBillJpegToDownloads(context, jpegFile)
                    if (ok) {
                        Toast.makeText(context, "✅ X-Report saved to gallery/downloads as JPEG", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "⚠️ Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Save error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                showXShareFormatDialog = true
            }
        )
    }

    if (showXShareFormatDialog && report != null) {
        val r = report!!
        ShareZReportFormatDialog(
            onDismiss = { showXShareFormatDialog = false },
            onFormatSelected = { format ->
                showXShareFormatDialog = false
                shareReport(context, r, rangeLabel, isInterimX = true, format = format)
            }
        )
    }

    // 7. Z-Report Dialog (Closing)
    if (showPnlZReportDialog && report != null) {
        val r = report!!
        ZReportDialog(
            data = r,
            rangeLabel = rangeLabel,
            isInterimX = false,
            onDismiss = { showPnlZReportDialog = false },
            onPrint = {
                try {
                    val jpegFile = generateZReportJpeg(context, r, rangeLabel)
                    printBillJpeg(context, jpegFile, "Z-Report - $rangeLabel")
                } catch (e: Exception) {
                    Toast.makeText(context, "Print error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            },
            onSave = {
                try {
                    val jpegFile = generateZReportJpeg(context, r, rangeLabel)
                    val ok = saveBillJpegToDownloads(context, jpegFile)
                    if (ok) {
                        Toast.makeText(context, "✅ Z-Report saved to gallery/downloads as JPEG", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "⚠️ Failed to save image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Save error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                showShareFormatDialog = true
            }
        )
    }

    if (showShareFormatDialog && report != null) {
        val r = report!!
        ShareZReportFormatDialog(
            onDismiss = { showShareFormatDialog = false },
            onFormatSelected = { format ->
                showShareFormatDialog = false
                shareReport(context, r, rangeLabel, isInterimX = false, format = format)
            }
        )
    }
}

/**
 * Backward compatibility alias for Navigation.kt
 */
@Composable
fun RegisterCloseScreen(
    registerClosingViewModel: RegisterClosingViewModel,
    expenseViewModel: ExpenseViewModel,
    salesViewModel: SalesViewModel,
    onBack: () -> Unit,
    onNavigateToExpenses: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    DayStartCloseScreen(
        registerClosingViewModel = registerClosingViewModel,
        expenseViewModel = expenseViewModel,
        salesViewModel = salesViewModel,
        onBack = onBack,
        onNavigateToExpenses = onNavigateToExpenses,
        modifier = modifier
    )
}

// =============================================================================
// DUAL-MODE BANGLADESHI TAKA DENOMINATION COUNTER DIALOG
// =============================================================================

@Composable
fun DenominationCounterDialog(
    expectedCash: Double,
    initialCountedCash: Double? = null,
    onDismiss: () -> Unit,
    onApply: (countedTotal: Double, auditNote: String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(if (initialCountedCash != null && initialCountedCash > 0) DenomCounterMode.QUICK else DenomCounterMode.DETAILED) }

    // Mode 1 State: Note-by-note counts map
    var noteCounts by rememberSaveable(stateSaver = run {
        mapSaver(
            save = { map -> map.entries.associate { it.key.toString() to it.value } },
            restore = { savedMap -> savedMap.entries.associate { it.key.toInt() to (savedMap[it.key] as? Int ?: 0) } }
        )
    }) {
        mutableStateOf(BANGLADESHI_DENOMINATIONS.associate { it.value to 0 })
    }
    var isDetailedNotesHidden by rememberSaveable { mutableStateOf(false) }

    // Mode 2 State: Direct Total Amount Input
    var directTotalInput by rememberSaveable {
        mutableStateOf(if (initialCountedCash != null && initialCountedCash > 0) initialCountedCash.toInt().toString() else "")
    }
    var isQuickBreakdownExpanded by rememberSaveable { mutableStateOf(false) }
    var auditNote by rememberSaveable { mutableStateOf("") }

    // Compute sums
    val detailedSum = noteCounts.entries.sumOf { (denom, count) -> denom * count }
    val quickTotalVal = directTotalInput.toDoubleOrNull() ?: 0.0

    val currentTotal = if (mode == DenomCounterMode.DETAILED) detailedSum.toDouble() else quickTotalVal
    val variance = currentTotal - expectedCash

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🧮", fontSize = 20.sp)
                        Column {
                            Text(
                                text = stringResource(R.string.denom_dialog_title),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "BDT Denomination Counter",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Mode Switcher: Count by Note vs Quick Total Amount
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Count by Note
                        Surface(
                            onClick = {
                                if (mode != DenomCounterMode.DETAILED) {
                                    mode = DenomCounterMode.DETAILED
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = if (mode == DenomCounterMode.DETAILED) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (mode == DenomCounterMode.DETAILED) 2.dp else 0.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🧮", fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.denom_mode_count_notes),
                                    fontSize = 12.sp,
                                    fontWeight = if (mode == DenomCounterMode.DETAILED) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (mode == DenomCounterMode.DETAILED) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Quick Total Amount
                        Surface(
                            onClick = {
                                if (mode != DenomCounterMode.QUICK) {
                                    mode = DenomCounterMode.QUICK
                                    if (directTotalInput.isBlank() && detailedSum > 0) {
                                        directTotalInput = detailedSum.toString()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = if (mode == DenomCounterMode.QUICK) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (mode == DenomCounterMode.QUICK) 2.dp else 0.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.denom_mode_quick_total),
                                    fontSize = 12.sp,
                                    fontWeight = if (mode == DenomCounterMode.QUICK) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (mode == DenomCounterMode.QUICK) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Scrollable Body Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (mode == DenomCounterMode.DETAILED) {
                        // MODE 1: DETAILED NOTES
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.denom_detailed_sub),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { isDetailedNotesHidden = !isDetailedNotesHidden },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isDetailedNotesHidden) "👁️ ${stringResource(R.string.denom_show_notes)}" else "👁️ ${stringResource(R.string.denom_hide_notes)}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!isDetailedNotesHidden) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BANGLADESHI_DENOMINATIONS.forEach { item ->
                                    val count = noteCounts[item.value] ?: 0
                                    DenominationRowItem(
                                        item = item,
                                        count = count,
                                        onCountChange = { newCount ->
                                            noteCounts = noteCounts.toMutableMap().apply { put(item.value, newCount) }
                                        }
                                    )
                                }
                            }
                        } else {
                            // Collapsed Placeholder for Detailed Mode
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🗂️", fontSize = 24.sp)
                                    Text(
                                        text = stringResource(R.string.denom_hidden_notice),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.denom_hidden_sub),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { isDetailedNotesHidden = false },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "👁️ ${stringResource(R.string.denom_show_10_denominations)}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Switch Hint
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.denom_dont_wanna_count),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.denom_enter_total_directly),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    mode = DenomCounterMode.QUICK
                                    if (directTotalInput.isBlank() && detailedSum > 0) {
                                        directTotalInput = detailedSum.toString()
                                    }
                                }
                            )
                        }
                    } else {
                        // MODE 2: QUICK TOTAL AMOUNT
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("💵", fontSize = 20.sp)
                                    Column {
                                        Text(
                                            text = stringResource(R.string.denom_quick_title),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = stringResource(R.string.denom_quick_sub),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Large Currency Input
                                OutlinedTextField(
                                    value = directTotalInput,
                                    onValueChange = { input ->
                                        directTotalInput = input.filter { it.isDigit() || it == '.' }
                                    },
                                    leadingIcon = {
                                        Text("৳", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669))
                                    },
                                    placeholder = { Text("0", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Quick Shortcut Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(500, 1000, 2000, 5000).forEach { preset ->
                                        Surface(
                                            onClick = {
                                                val curr = directTotalInput.toDoubleOrNull() ?: 0.0
                                                directTotalInput = (curr + preset).toInt().toString()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "+৳$preset",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Match Expected
                                    Surface(
                                        onClick = {
                                            directTotalInput = expectedCash.toInt().toString()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFECFDF5),
                                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                        modifier = Modifier.weight(2f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.denom_match_expected, formatTaka(expectedCash)),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF065F46),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }

                                    // Clear
                                    Surface(
                                        onClick = { directTotalInput = "" },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFFF1F2),
                                        border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.denom_clear),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFBE123C),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }

                                // Live Reconciliation Comparison Card
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(stringResource(R.string.denom_expected_system), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formatTaka(expectedCash), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(stringResource(R.string.denom_entered_physical), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formatTaka(quickTotalVal), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                                        }

                                        HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(stringResource(R.string.denom_shift_variance), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            val (badgeText, badgeBg, badgeTextColor) = when {
                                                variance.isZeroMoney() -> Triple(
                                                    stringResource(R.string.denom_variance_balanced),
                                                    Color(0xFFDCFCE7),
                                                    Color(0xFF166534)
                                                )
                                                variance < 0.0 -> Triple(
                                                    stringResource(R.string.denom_variance_shortage, formatTaka(kotlin.math.abs(variance))),
                                                    Color(0xFFFEE2E2),
                                                    Color(0xFF991B1B)
                                                )
                                                else -> Triple(
                                                    stringResource(R.string.denom_variance_surplus, formatTaka(variance)),
                                                    Color(0xFFE0F2FE),
                                                    Color(0xFF075985)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = badgeBg
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeTextColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Collapsible Banknote Denomination Breakdown (Optional)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isQuickBreakdownExpanded = !isQuickBreakdownExpanded },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(if (isQuickBreakdownExpanded) "▾" else "▸", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                Column {
                                                    Text(
                                                        text = stringResource(R.string.denom_breakdown_title),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.denom_breakdown_sub),
                                                        fontSize = 9.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = if (isQuickBreakdownExpanded) stringResource(R.string.denom_breakdown_shown) else stringResource(R.string.denom_breakdown_hidden),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = isQuickBreakdownExpanded,
                                            enter = fadeIn() + expandVertically(),
                                            exit = fadeOut() + shrinkVertically()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(top = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                BANGLADESHI_DENOMINATIONS.forEach { item ->
                                                    val count = noteCounts[item.value] ?: 0
                                                    DenominationRowItem(
                                                        item = item,
                                                        count = count,
                                                        onCountChange = { newCount ->
                                                            val updated = noteCounts.toMutableMap().apply { put(item.value, newCount) }
                                                            noteCounts = updated
                                                            // Sync into directTotalInput
                                                            directTotalInput = updated.entries.sumOf { it.key * it.value }.toString()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Optional Counting Method / Audit Note Input
                                OutlinedTextField(
                                    value = auditNote,
                                    onValueChange = { auditNote = it },
                                    label = { Text(stringResource(R.string.denom_audit_note_label), fontSize = 11.sp) },
                                    placeholder = { Text(stringResource(R.string.denom_audit_note_hint), fontSize = 11.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Sticky Footer Total Bar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.denom_total_physical_cash),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = formatTaka(currentTotal),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399)
                            )
                        }

                        Button(
                            onClick = { onApply(currentTotal, auditNote) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.denom_apply_to_shift),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("➔", fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DenominationRowItem(
    item: DenominationItem,
    count: Int,
    onCountChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Denomination Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(90.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = item.badgeColor
                ) {
                    Text(
                        text = item.label,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Subtotal Calculation
            val subtotal = item.value * count
            Text(
                text = "= ${formatTaka(subtotal.toDouble())}",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )

            // Stepper Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { if (count > 0) onCountChange(count - 1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("−", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }

                var textVal by remember(count) { mutableStateOf(count.toString()) }
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { input: String ->
                        val digits = input.filter { it.isDigit() }
                        textVal = digits
                        onCountChange(digits.toIntOrNull() ?: 0)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.width(60.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                IconButton(
                    onClick = { onCountChange(count + 1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// =============================================================================
// ADD CASH FLOAT DIALOG
// =============================================================================

@Composable
fun AddCashFloatDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, reason: String) -> Unit
) {
    var amountText by remember { mutableStateOf("500") }
    var reasonText by remember { mutableStateOf("Drawer change float top-up") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("➕", fontSize = 18.sp)
                        Text(
                            text = stringResource(R.string.day_close_add_float_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount (৳ Taka)") },
                    leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(500, 1000, 2000).forEach { preset ->
                        Surface(
                            onClick = { amountText = preset.toString() },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "+৳$preset",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text(stringResource(R.string.day_close_add_float_reason)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) onConfirm(amt, reasonText)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.day_close_add_float_confirm),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// =============================================================================
// SAFE DROP DIALOG
// =============================================================================

@Composable
fun SafeDropDialog(
    currentDrawerCash: Double,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, destination: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var destText by remember { mutableStateOf("Main Store Safe Vault") }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val isOverAmount = amount > currentDrawerCash

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔒", fontSize = 18.sp)
                        Column {
                            Text(
                                text = stringResource(R.string.day_close_safe_drop_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Current in Drawer: ${formatTaka(currentDrawerCash)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.day_close_safe_drop_amount)) },
                    leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    singleLine = true,
                    isError = isOverAmount,
                    supportingText = if (isOverAmount) {
                        { Text("Amount exceeds current drawer cash balance!", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2000, 5000, 10000).forEach { preset ->
                        Surface(
                            onClick = { amountText = preset.toString() },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "৳$preset",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = destText,
                    onValueChange = { destText = it },
                    label = { Text(stringResource(R.string.day_close_safe_drop_dest)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (amount > 0 && !isOverAmount) onConfirm(amount, destText)
                    },
                    enabled = amount > 0 && !isOverAmount,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.day_close_safe_drop_confirm),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// =============================================================================
// EDIT STARTING FLOAT DIALOG
// =============================================================================

@Composable
fun EditStartingFloatDialog(
    currentFloat: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var floatText by remember { mutableStateOf(currentFloat.toInt().toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Edit Opening Float (৳)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                OutlinedTextField(
                    value = floatText,
                    onValueChange = { input -> floatText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.register_close_starting_float_label)) },
                    supportingText = { Text(stringResource(R.string.register_close_starting_float_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val v = floatText.toDoubleOrNull() ?: 0.0
                            onSave(v)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// =============================================================================
// CLOSE REGISTER RECONCILIATION DIALOG
// =============================================================================

@Composable
fun CloseRegisterReconciliationDialog(
    expectedCash: Double,
    initialCountedCash: Double,
    initialNotes: String = "",
    onDismiss: () -> Unit,
    onOpenDenominationCounter: () -> Unit,
    onConfirmClose: (actualCash: Double, notes: String?) -> Unit
) {
    var actualCashText by remember { mutableStateOf(initialCountedCash.toInt().toString()) }
    var notesText by remember { mutableStateOf(initialNotes) }
    var isSubmitting by remember { mutableStateOf(false) }

    val actualCash = actualCashText.toDoubleOrNull() ?: 0.0
    val variance = actualCash - expectedCash

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔒", fontSize = 20.sp)
                        Column {
                            Text(
                                text = "Close Register & Audit",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text("Reconcile physical cash drawer", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Expected Cash Tile
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("System Expected Cash", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            Text(formatTaka(expectedCash), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                        }
                        TextButton(onClick = onOpenDenominationCounter) {
                            Text("🧮 Count Notes", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                    }
                }

                // Actual Counted Cash Input
                OutlinedTextField(
                    value = actualCashText,
                    onValueChange = { input -> actualCashText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Actual Counted Cash (৳)") },
                    leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Variance Badge
                val (varianceText, varianceBg, varianceTextColor) = when {
                    variance.isZeroMoney() -> Triple("✓ Matched (Exact Match)", Color(0xFFDCFCE7), Color(0xFF166534))
                    variance < 0.0 -> Triple("⚠️ Shortage: -${formatTaka(kotlin.math.abs(variance))}", Color(0xFFFEE2E2), Color(0xFF991B1B))
                    else -> Triple("ℹ️ Surplus: +${formatTaka(variance)}", Color(0xFFE0F2FE), Color(0xFF075985))
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = varianceBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = varianceText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = varianceTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Shift Closing Note (Optional)") },
                    placeholder = { Text("e.g. End of evening shift") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                onConfirmClose(actualCash, notesText.ifBlank { null })
                            }
                        },
                        enabled = !isSubmitting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Confirm & Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-COMPONENTS & HELPERS
// =============================================================================

@Composable
private fun SummaryRow(label: String, value: String, color: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.5.sp, color = Color(0xFFCBD5E1))
        Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun RegisterClosingRow(closing: RegisterClosing, onClick: () -> Unit = {}) {
    val dateLabel = remember(closing.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(closing.timestamp))
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val varianceColor = when {
                    closing.variance.isZeroMoney() -> Color(0xFF16A34A)
                    closing.variance < 0.0 -> Color(0xFFDC2626)
                    else -> Color(0xFF0284C7)
                }
                val varianceText = when {
                    closing.variance.isZeroMoney() -> stringResource(R.string.register_close_variance_matched)
                    closing.variance < 0.0 -> stringResource(R.string.register_close_variance_shortage, formatTaka(kotlin.math.abs(closing.variance)))
                    else -> stringResource(R.string.register_close_variance_surplus, formatTaka(closing.variance))
                }
                Text(varianceText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = varianceColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Expected ${formatTaka(closing.expectedCash)} · Counted ${formatTaka(closing.actualCountedCash)} · ${closing.cashierName}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoricalClosingDetailDialog(
    closing: RegisterClosing,
    onDismiss: () -> Unit
) {
    val dateLabel = remember(closing.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(closing.timestamp))
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Shift Closing Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(dateLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cashier: ${closing.cashierName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Starting Float: ${formatTaka(closing.startingFloat)}", fontSize = 12.5.sp)
                    Text("Cash Sales: ${formatTaka(closing.cashSales)}", fontSize = 12.5.sp)
                    Text("Digital Sales: ${formatTaka(closing.digitalSales)}", fontSize = 12.5.sp)
                    if (closing.bkashSales > 0 || closing.nagadSales > 0 || closing.cardSales > 0) {
                        Text("  • bKash: ${formatTaka(closing.bkashSales)}  • Nagad: ${formatTaka(closing.nagadSales)}  • Card: ${formatTaka(closing.cardSales)}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Cash In: +${formatTaka(closing.cashIn)} · Cash Out: -${formatTaka(closing.cashOut)}", fontSize = 12.5.sp)
                    Text("Cash Refunds: -${formatTaka(closing.cashRefunds)}", fontSize = 12.5.sp)
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Expected Cash:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(formatTaka(closing.expectedCash), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Counted Physical Cash:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(formatTaka(closing.actualCountedCash), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                val varianceColor = when {
                    closing.variance.isZeroMoney() -> Color(0xFF16A34A)
                    closing.variance < 0.0 -> Color(0xFFDC2626)
                    else -> Color(0xFF0284C7)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Variance:", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text(formatTaka(closing.variance), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = varianceColor)
                }
                if (!closing.notes.isNullOrBlank()) {
                    Text("Note: ${closing.notes}", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ZReportDialog(
    data: SalesReportData,
    rangeLabel: String,
    isInterimX: Boolean = false,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    val reportTitle = if (isInterimX) "Mid-Shift X-Report (Interim)" else "Daily Closing Z-Report"
    val reportHeader = if (isInterimX) "TECHYFI POS - MID-SHIFT READING (X-REPORT)\n(INTERIM TOTALS - NO SHIFT RESET)" else "TECHYFI POS - DAILY CLOSING (Z-REPORT)"
    val reportFooter = if (isInterimX) "*** END OF X-REPORT (INTERIM) ***" else "*** END OF Z-REPORT (AUDIT FINAL) ***"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Realistic3DRegisterAuditZReportIcon(size = 32.dp)
                        Column {
                            Text(reportTitle, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Period: $rangeLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // 80mm Thermal Receipt Simulation
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "$reportHeader\n================================",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Period: $rangeLabel\n" +
                                "Invoices: ${data.transactionCount} | Units: ${data.itemsSold}\n" +
                                "--------------------------------\n" +
                                "Gross Sales:      ${formatTaka(data.totalRevenue)}\n" +
                                "Gross Margin:     ${formatTaka(data.totalProfit)}\n" +
                                "Discounts Given:  ${formatTaka(data.totalDiscount)}\n" +
                                "Expenses:         ${formatTaka(data.totalExpenses)}\n" +
                                "Net Profit (est): ${formatTaka(data.netProfit)}\n" +
                                "--------------------------------\n" +
                                "PAYMENTS:\n" +
                                data.paymentBreakdown.joinToString("\n") {
                                    "- ${it.method.padEnd(12)}: ${formatTaka(it.amount)}"
                                } + "\n================================\n$reportFooter",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.Black
                        )
                    }
                }

                // Action Row 1: Print & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPrint,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    FilledTonalButton(
                        onClick = onSave,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Action Row 2: Close & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onShare,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ShareZReportFormatDialog(
    onDismiss: () -> Unit,
    onFormatSelected: (ZReportFormat) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ZReportFormat.JPEG) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        Text("📤", fontSize = 20.sp)
                        Column {
                            Text("Share Report", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Select export format", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZReportFormat.values().forEach { format ->
                        val isSelected = selectedFormat == format
                        Surface(
                            onClick = { selectedFormat = format },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(format.icon, fontSize = 22.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = format.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (format == ZReportFormat.JPEG) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Default",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = format.description,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedFormat = format }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onFormatSelected(selectedFormat) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share ${selectedFormat.name}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun shareReport(context: android.content.Context, r: SalesReportData, rangeLabel: String, isInterimX: Boolean, format: ZReportFormat) {
    try {
        val subject = if (isInterimX) "Techyfi POS Mid-Shift X-Report – $rangeLabel" else "Techyfi POS Daily Closing Z-Report – $rangeLabel"
        when (format) {
            ZReportFormat.JPEG -> {
                val file = if (isInterimX) generateXReportJpeg(context, r, rangeLabel) else generateZReportJpeg(context, r, rangeLabel)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Report JPEG", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share Report via"))
            }
            ZReportFormat.PDF -> {
                val file = if (isInterimX) generateXReportPdf(context, r, rangeLabel) else generateZReportPdf(context, r, rangeLabel)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Report PDF", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share Report via"))
            }
            ZReportFormat.CSV -> {
                val directory = File(context.cacheDir, "reports").apply { mkdirs() }
                val file = File(directory, "techyfi-${if (isInterimX) "x" else "z"}-report-${System.currentTimeMillis()}.csv")
                file.writeText(buildReportCsv(r, if (isInterimX) "$rangeLabel (Interim)" else rangeLabel))
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "Report CSV", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Share Report via"))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun buildReportCsv(data: SalesReportData, rangeLabel: String): String {
    fun csv(value: Any): String = "\"${value.toString().replace("\"", "\"\"")}\""
    return buildString {
        appendLine("Report period,${csv(rangeLabel)}")
        appendLine("Net sales,${data.totalRevenue}")
        appendLine("Gross margin,${data.totalProfit}")
        appendLine("Discounts,${data.totalDiscount}")
        appendLine("Expenses,${data.totalExpenses}")
        appendLine("Net profit,${data.netProfit}")
        appendLine("Transactions,${data.transactionCount}")
        appendLine("Items sold,${data.itemsSold}")
        appendLine()
        appendLine("Top products")
        appendLine("Product,Barcode,Units,Sales")
        data.topProducts.forEach { product ->
            appendLine("${csv(product.name)},${csv(product.barcode)},${product.quantity},${product.revenue}")
        }
        appendLine()
        appendLine("Payment methods")
        appendLine("Method,Transactions,Amount")
        data.paymentBreakdown.forEach { payment ->
            appendLine("${csv(payment.method)},${payment.transactionCount},${payment.amount}")
        }
    }
}

@Composable
private fun PaymentMethodBreakdownTile(
    method: String,
    label: String,
    amount: Double,
    themeColor: Color,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    val iconWidth = if (isTablet) 26.dp else 22.dp
    val iconHeight = if (isTablet) 16.dp else 14.dp
    val emojiSize = if (isTablet) 15.sp else 13.5.sp
    val labelSize = if (isTablet) 11.5.sp else 10.5.sp
    val amountSize = if (isTablet) 13.sp else 11.5.sp

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = themeColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = if (isTablet) 8.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (method) {
                    "Cash" -> Text("💵", fontSize = emojiSize)
                    "Card" -> Text("💳", fontSize = emojiSize)
                    "Due" -> Text("⏳", fontSize = emojiSize)
                    "bKash" -> Image(
                        painter = painterResource(R.drawable.logo_bkash),
                        contentDescription = "bKash",
                        modifier = Modifier
                            .size(width = iconWidth, height = iconHeight)
                            .clip(RoundedCornerShape(2.5.dp)),
                        contentScale = ContentScale.Fit
                    )
                    "Nagad" -> Image(
                        painter = painterResource(R.drawable.logo_nagad),
                        contentDescription = "Nagad",
                        modifier = Modifier
                            .size(width = iconWidth, height = iconHeight)
                            .clip(RoundedCornerShape(2.5.dp)),
                        contentScale = ContentScale.Fit
                    )
                    "Rocket" -> Image(
                        painter = painterResource(R.drawable.logo_rocket),
                        contentDescription = "Rocket",
                        modifier = Modifier
                            .size(width = iconWidth, height = iconHeight)
                            .clip(RoundedCornerShape(2.5.dp)),
                        contentScale = ContentScale.Fit
                    )
                    else -> Text("🏷️", fontSize = emojiSize)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = labelSize,
                    fontWeight = FontWeight.SemiBold,
                    color = themeColor,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = formatTaka(amount),
                fontSize = amountSize,
                fontWeight = FontWeight.Black,
                color = themeColor,
                maxLines = 1
            )
        }
    }
}

