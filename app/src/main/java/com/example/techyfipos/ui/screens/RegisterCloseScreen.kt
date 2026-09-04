package com.example.techyfipos.ui.screens

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.example.techyfipos.R
import com.example.techyfipos.data.db.RegisterClosing
import com.example.techyfipos.ui.components.Realistic3DCashVaultRegisterIcon
import com.example.techyfipos.ui.components.Realistic3DRegisterAuditZReportIcon
import com.example.techyfipos.ui.components.ZReportDialog
import com.example.techyfipos.ui.viewmodel.ExpenseViewModel
import com.example.techyfipos.ui.viewmodel.RegisterClosingViewModel
import com.example.techyfipos.ui.viewmodel.SalesReportData
import com.example.techyfipos.ui.viewmodel.SalesViewModel
import com.example.techyfipos.ui.viewmodel.cashRefundAmount
import com.example.techyfipos.ui.viewmodel.splitCashAndDigitalAmounts
import com.example.techyfipos.utils.findActivity
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.generateZReportJpeg
import com.example.techyfipos.utils.generateZReportPdf
import com.example.techyfipos.utils.generateXReportJpeg
import com.example.techyfipos.utils.generateXReportPdf
import com.example.techyfipos.utils.printBillJpeg
import com.example.techyfipos.utils.saveBillJpegToDownloads
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterCloseScreen(
    registerClosingViewModel: RegisterClosingViewModel,
    expenseViewModel: ExpenseViewModel,
    salesViewModel: SalesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val closings by registerClosingViewModel.allClosings.collectAsStateWithLifecycle(initialValue = emptyList())
    val shiftState by registerClosingViewModel.shiftState.collectAsStateWithLifecycle()
    val expenses by expenseViewModel.allExpenses.collectAsStateWithLifecycle()
    val sales by expenseViewModel.allSales.collectAsStateWithLifecycle()
    val returns by expenseViewModel.allReturns.collectAsStateWithLifecycle()
    val duePayments by expenseViewModel.allDuePayments.collectAsStateWithLifecycle()
    val supplierPayments by expenseViewModel.allSupplierPayments.collectAsStateWithLifecycle()
    val salesSnapshot by salesViewModel.salesWithItems.collectAsStateWithLifecycle()

    val todayStart = remember { todayRange().first }
    val shiftStart = remember(closings, todayStart, shiftState) {
        shiftState?.startedAt?.takeIf { it > 0L } ?: registerShiftStart(todayStart, closings)
    }
    val shiftSales = remember(sales, shiftStart) { sales.filter { it.timestamp >= shiftStart } }
    val cashSales = remember(shiftSales) { shiftSales.sumOf { splitCashAndDigitalAmounts(it).first } }
    val digitalSales = remember(shiftSales) { shiftSales.sumOf { splitCashAndDigitalAmounts(it).second } }
    val cashIn = remember(expenses, shiftStart) {
        expenses.filter { it.timestamp >= shiftStart && it.type == "CASH_IN" && it.paymentSource == "Cash" }.sumOf { it.amount }
    }
    val cashOut = remember(expenses, shiftStart) {
        expenses.filter { it.timestamp >= shiftStart && it.type == "EXPENSE" && it.paymentSource == "Cash" }.sumOf { it.amount }
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

    var startingFloatText by remember { mutableStateOf("0") }
    LaunchedEffect(shiftState?.startedAt) {
        startingFloatText = (shiftState?.startingFloat ?: 0.0).toString().removeSuffix(".0")
    }
    var showZReportDialog by remember { mutableStateOf(false) }
    var showPnlZReportDialog by remember { mutableStateOf(false) }
    var showXReportDialog by remember { mutableStateOf(false) }
    var showShareFormatDialog by remember { mutableStateOf(false) }
    var showXShareFormatDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val startingFloat = startingFloatText.toDoubleOrNull() ?: 0.0
    LaunchedEffect(startingFloat) {
        if (startingFloat != (shiftState?.startingFloat ?: 0.0)) {
            registerClosingViewModel.updateStartingFloat(startingFloat)
        }
    }
    val expectedCash = startingFloat + cashSales + cashIn + dueCashCollected - cashOut - supplierCashPaid - cashRefunds

    val successMessage = stringResource(R.string.register_close_success_message)

    val (rangeStart, rangeEnd) = remember { todayRange() }
    var report by remember { mutableStateOf<SalesReportData?>(null) }
    LaunchedEffect(rangeStart, rangeEnd, salesSnapshot) {
        report = salesViewModel.buildReport(rangeStart, rangeEnd, salesSnapshot)
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val rangeLabel = remember(rangeStart) { dateFormat.format(Date(rangeStart)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.register_close_screen_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.register_close_screen_subtitle),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // Mid-Shift X-Report
                        Surface(
                            onClick = { showXReportDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0F766E),
                            border = BorderStroke(1.dp, Color(0x5599F6E4)),
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text("📋", fontSize = 13.sp)
                                Text(
                                    text = "X-Report",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Shift Closing Z-Report
                        Surface(
                            onClick = { showPnlZReportDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E1B4B),
                            border = BorderStroke(1.dp, Color(0x55C7D2FE)),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Realistic3DRegisterAuditZReportIcon(size = 18.dp)
                                Text(
                                    text = "Z-Report",
                                    fontSize = 12.sp,
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Realistic3DCashVaultRegisterIcon(size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.register_close_today_summary),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        SummaryRow(stringResource(R.string.register_close_cash_sales), formatTaka(cashSales))
                        SummaryRow(stringResource(R.string.register_close_digital_sales), formatTaka(digitalSales))
                        SummaryRow(stringResource(R.string.expense_cash_in_label), formatTaka(cashIn))
                        if (dueCashCollected > 0.0) {
                            SummaryRow(stringResource(R.string.register_close_due_cash_collected), "+${formatTaka(dueCashCollected)}")
                        }
                        SummaryRow(stringResource(R.string.expense_cash_out_label), formatTaka(cashOut))
                        if (supplierCashPaid > 0.0) {
                            SummaryRow(stringResource(R.string.register_close_supplier_cash_paid), "−${formatTaka(supplierCashPaid)}")
                        }
                        SummaryRow(stringResource(R.string.register_close_cash_refunds_label), formatTaka(cashRefunds))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.zreport_expected_cash_label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(formatTaka(expectedCash), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = startingFloatText,
                    onValueChange = { input -> startingFloatText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text(stringResource(R.string.register_close_starting_float_label)) },
                    supportingText = { Text(stringResource(R.string.register_close_starting_float_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = { showZReportDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text(stringResource(R.string.register_close_open_dialog_button), fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(
                    text = stringResource(R.string.register_close_history_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (closings.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.register_close_history_empty),
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(closings) { closing ->
                    RegisterClosingRow(closing)
                }
            }
        }
    }

    if (showZReportDialog) {
        ZReportDialog(
            expectedCash = expectedCash,
            cashSales = cashSales,
            digitalSales = digitalSales,
            cashIn = cashIn,
            cashOut = cashOut,
            onDismiss = { showZReportDialog = false },
            onCloseRegister = { actualCash, notes ->
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
                    onResult = { result ->
                        result.onSuccess {
                            showZReportDialog = false
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
                try {
                    when (format) {
                        ZReportFormat.JPEG -> {
                            val file = generateXReportJpeg(context, r, rangeLabel)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_SUBJECT, "Techyfi POS Mid-Shift X-Report – $rangeLabel")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "X-Report JPEG", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(intent, "Share X-Report via").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                        ZReportFormat.PDF -> {
                            val file = generateXReportPdf(context, r, rangeLabel)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_SUBJECT, "Techyfi POS Mid-Shift X-Report – $rangeLabel")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "X-Report PDF", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(intent, "Share X-Report via").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                        ZReportFormat.CSV -> {
                            val directory = File(context.cacheDir, "reports").apply { mkdirs() }
                            val file = File(directory, "techyfi-x-report-${System.currentTimeMillis()}.csv")
                            file.writeText(buildReportCsv(r, "$rangeLabel (Interim)"))
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "Techyfi POS Mid-Shift X-Report – $rangeLabel")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "X-Report CSV", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(intent, "Share X-Report via").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

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
                try {
                    when (format) {
                        ZReportFormat.JPEG -> {
                            val file = generateZReportJpeg(context, r, rangeLabel)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_SUBJECT, "Techyfi POS Daily Closing Z-Report – $rangeLabel")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "Z-Report JPEG", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(intent, "Share Z-Report via").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                        ZReportFormat.PDF -> {
                            val file = generateZReportPdf(context, r, rangeLabel)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_SUBJECT, "Techyfi POS Daily Closing Z-Report – $rangeLabel")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "Z-Report PDF", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(intent, "Share Z-Report via").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                        ZReportFormat.CSV -> {
                            val directory = File(context.cacheDir, "reports").apply { mkdirs() }
                            val file = File(directory, "techyfi-z-report-${System.currentTimeMillis()}.csv")
                            file.writeText(buildReportCsv(r, rangeLabel))
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "Techyfi POS Daily Closing Z-Report – $rangeLabel")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "Z-Report CSV", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = Intent.createChooser(intent, "Share Z-Report via").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(chooser)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFFCBD5E1))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun RegisterClosingRow(closing: RegisterClosing) {
    val dateLabel = remember(closing.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(java.util.Date(closing.timestamp))
    }
    Surface(
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
                    closing.variance == 0.0 -> Color(0xFF16A34A)
                    closing.variance < 0.0 -> Color(0xFFDC2626)
                    else -> Color(0xFF0284C7)
                }
                val varianceText = when {
                    closing.variance == 0.0 -> stringResource(R.string.register_close_variance_matched)
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
                            Text("Share Z-Report", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                        onClick = {
                            onFormatSelected(selectedFormat)
                        },
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
