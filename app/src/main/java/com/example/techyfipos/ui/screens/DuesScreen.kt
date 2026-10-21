package com.example.techyfipos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.utils.localMillisToUtcMidnight
import com.example.techyfipos.utils.utcMidnightToLocalMidnight
import com.example.techyfipos.utils.toEnglishDigits
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.techyfipos.theme.extendedColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.R
import com.example.techyfipos.data.DueTemplate
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.ui.viewmodel.SettingsViewModel
import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.CustomerDue
import com.example.techyfipos.data.db.DuePayment
import com.example.techyfipos.data.db.DueCharge
import com.example.techyfipos.data.db.DueReminderLog
import com.example.techyfipos.data.db.ScheduledDueReminder
import com.example.techyfipos.ui.viewmodel.CustomerViewModel
import com.example.techyfipos.ui.viewmodel.DuesAuditEntry
import com.example.techyfipos.ui.viewmodel.DuesAuditType
import com.example.techyfipos.ui.viewmodel.DuesViewModel
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.example.techyfipos.utils.DueReminderScheduler
import com.example.techyfipos.utils.convertToMonochromeThresholdBitmap
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.formatTakaBn
import com.example.techyfipos.utils.getLocalizedRepositoryErrorMessage
import com.example.techyfipos.utils.generateDueRepaymentJpeg
import com.example.techyfipos.utils.generateQrBitmap
import com.example.techyfipos.utils.openPaymentReminder
import com.example.techyfipos.utils.openPhoneDialer
import com.example.techyfipos.utils.openWhatsAppReminder
import com.example.techyfipos.utils.normalizePhone
import com.example.techyfipos.utils.printBillJpeg
import com.example.techyfipos.utils.saveBillJpegToDownloads
import com.example.techyfipos.utils.FileSaveResult
import com.example.techyfipos.utils.shareBillJpeg
import com.example.techyfipos.utils.shareReceiptAsText
import com.example.techyfipos.utils.findActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import com.example.techyfipos.utils.*
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class DuesTab(@get:StringRes val labelRes: Int) {
    OUTSTANDING(R.string.dues_tab_outstanding),
    OVERDUE(R.string.dues_tab_overdue),
    SETTLED(R.string.dues_tab_settled),
    PAYMENTS(R.string.dues_tab_payments)
}

private enum class DuePeriodFilter(@get:StringRes val labelRes: Int) {
    ALL_TIME(R.string.dues_period_all_dates),
    TODAY(R.string.dues_period_today),
    THIS_WEEK(R.string.dues_period_this_week),
    THIS_MONTH(R.string.dues_period_this_month),
    CUSTOM(R.string.dues_period_custom_range)
}

private enum class DuesSort(@get:StringRes val labelRes: Int) {
    HIGHEST(R.string.dues_sort_highest),
    RECENT(R.string.dues_sort_recent),
    OLDEST(R.string.dues_sort_oldest)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesScreen(
    duesViewModel: DuesViewModel,
    customerViewModel: CustomerViewModel? = null,
    settingsViewModel: SettingsViewModel? = null,
    authViewModel: com.example.techyfipos.ui.viewmodel.AuthViewModel? = null,
    isOwner: Boolean = true,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    isTablet: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    onBack: () -> Unit,
    onStartSaleForCustomer: ((Customer) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canSendReminders = isOwner || activePermissions.contains(AppPermissions.DUES_SEND_REMINDERS)
    val canOverrideLimit = isOwner || activePermissions.contains(AppPermissions.DUES_OVERRIDE_LIMIT)
    val canCollect = isOwner || activePermissions.contains(AppPermissions.DUES_COLLECT_PAYMENT)
    val canAddDue = isOwner || activePermissions.contains(AppPermissions.DUES_ADD_DUE)
    val reminderPermissionRequiredToast = stringResource(R.string.dues_reminder_permission_required_toast)
    val storeName by (settingsViewModel?.shopName ?: flowOf("")).collectAsStateWithLifecycle("")
    val storeAddress by (settingsViewModel?.shopAddress ?: flowOf("")).collectAsStateWithLifecycle("")
    val storePhone by (settingsViewModel?.shopPhone ?: flowOf("")).collectAsStateWithLifecycle("")
    val invoiceSettingsData by (settingsViewModel?.invoiceSettingsData ?: flowOf(InvoiceSettingsData())).collectAsStateWithLifecycle(InvoiceSettingsData())
    val appLanguage by (settingsViewModel?.language ?: flowOf("en")).collectAsStateWithLifecycle("en")
    val isAppBangla = appLanguage == "bn"
    val deleteOwnerOnlyToast = stringResource(R.string.dues_delete_owner_only_toast)
    val addDueSuccessToast = stringResource(R.string.dues_add_due_success_toast)
    val addDueFailureToast = stringResource(R.string.dues_add_due_failure_toast)
    val duesExportSuccessMessage = stringResource(R.string.dues_export_excel_success)
    val duesExportFailedPrefix = stringResource(R.string.dues_export_excel_failed, "").trimEnd()
    val statementExportSuccessMessage = stringResource(R.string.dues_customer_statement_excel_success)
    val outstandingDues by duesViewModel.duesList.collectAsStateWithLifecycle()
    val allDues by duesViewModel.allDuesList.collectAsStateWithLifecycle()
    val allPayments by duesViewModel.allDuePayments.collectAsStateWithLifecycle()
    val selectedCustomer by duesViewModel.selectedCustomer.collectAsStateWithLifecycle()
    val selectedPayments by duesViewModel.selectedCustomerPayments.collectAsStateWithLifecycle()
    val selectedCharges by duesViewModel.selectedCustomerCharges.collectAsStateWithLifecycle()
    val selectedReturns by duesViewModel.selectedCustomerReturns.collectAsStateWithLifecycle()
    val dossierData by (customerViewModel?.selectedCustomerDossier?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    val debtAgeMap by duesViewModel.customerDebtOriginTimestamps.collectAsStateWithLifecycle()

    val exportDuesExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val success = duesViewModel.exportDuesToExcel(output)
                        if (success) {
                            Toast.makeText(context, duesExportSuccessMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "$duesExportFailedPrefix ${context.getString(R.string.dues_workbook_failed)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "$duesExportFailedPrefix ${getLocalizedRepositoryErrorMessage(context, e)}".trim(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val exportCustomerStatementExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: android.net.Uri? ->
        if (uri != null && selectedCustomer != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val success = duesViewModel.exportCustomerStatementToExcel(selectedCustomer!!, output)
                        if (success) {
                            Toast.makeText(context, statementExportSuccessMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "$duesExportFailedPrefix ${context.getString(R.string.dues_statement_failed)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "$duesExportFailedPrefix ${getLocalizedRepositoryErrorMessage(context, e)}".trim(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val configuration = LocalConfiguration.current

    var selectedTab by rememberSaveable { mutableStateOf(DuesTab.OUTSTANDING) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(DuesSort.HIGHEST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var duePeriod by rememberSaveable { mutableStateOf(DuePeriodFilter.ALL_TIME) }
    var showDuePeriodMenu by remember { mutableStateOf(false) }
    var customDueStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var customDueEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDuesStartPicker by remember { mutableStateOf(false) }
    var showDuesEndPicker by remember { mutableStateOf(false) }
    var minimumDue by remember { mutableStateOf("") }
    var draftMinimumDue by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var showAddDueDialog by remember { mutableStateOf(false) }
    var showAddDueForCustomer by remember { mutableStateOf<CustomerDue?>(null) }
    var reminderCustomer by remember { mutableStateOf<CustomerDue?>(null) }
    var paymentCustomer by remember { mutableStateOf<CustomerDue?>(null) }
    var repaymentReceiptData by remember { mutableStateOf<DueRepaymentReceiptData?>(null) }
    var customerToDelete by remember { mutableStateOf<CustomerDue?>(null) }
    var customerToEditDue by remember { mutableStateOf<CustomerDue?>(null) }
    var collisionMergeCustomer by remember { mutableStateOf<Pair<CustomerDue, com.example.techyfipos.data.db.Customer>?>(null) }
    var paymentToDelete by remember { mutableStateOf<DuePayment?>(null) }
    var chargeToDelete by remember { mutableStateOf<com.example.techyfipos.data.db.DueCharge?>(null) }
    var showScheduledReminders by remember { mutableStateOf(false) }
    var showDuesAuditSheet by remember { mutableStateOf(false) }
    var showReminderHubSheet by remember { mutableStateOf(false) }
    val scheduledReminders by duesViewModel.allScheduledReminders.collectAsStateWithLifecycle()
    val duesAuditEntries by duesViewModel.allDuesAuditEntries.collectAsStateWithLifecycle()
    val reminderLogs by duesViewModel.allDueReminderLogs.collectAsStateWithLifecycle()

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    val monthStart = remember(now) {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis.let(::startOfDay)
    }
    var paymentStart by remember { mutableStateOf(monthStart) }
    var paymentEnd by remember { mutableStateOf(now) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val totalOutstanding = outstandingDues.sumOf { it.outstandingAmount }
    val collectedThisMonth = allPayments
        .filter { it.timestamp in monthStart..now }
        .sumOf { it.amountPaid }
    val totalEverCollected = allPayments.sumOf { it.amountPaid }
    val paymentsByPhone = remember(allPayments) { allPayments.groupBy { it.customerPhone } }
    val customerByPhone = remember(allDues) { allDues.associateBy { it.customerPhone } }

    val overdueThresholdMillis = remember(now) { now - (14L * 24L * 60L * 60L * 1000L) }

    val (dueStartRange, dueEndRange) = remember(duePeriod, customDueStart, customDueEnd, now, monthStart) {
        when (duePeriod) {
            DuePeriodFilter.ALL_TIME -> Pair(null, null)
            DuePeriodFilter.TODAY -> Pair(startOfDay(now), endOfDay(now))
            DuePeriodFilter.THIS_WEEK -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }
                Pair(startOfDay(cal.timeInMillis), endOfDay(now))
            }
            DuePeriodFilter.THIS_MONTH -> Pair(monthStart, endOfDay(now))
            DuePeriodFilter.CUSTOM -> {
                val start = customDueStart ?: now
                val end = customDueEnd ?: now
                Pair(startOfDay(start), endOfDay(end))
            }
        }
    }

    val customerRows = remember(allDues, selectedTab, searchQuery, sort, minimumDue, overdueThresholdMillis, dueStartRange, dueEndRange) {
        val query = searchQuery.trim().lowercase()
        val minimum = minimumDue.toDoubleOrNull()
        allDues.filter { customer ->
            val matchesTab = when (selectedTab) {
                DuesTab.OUTSTANDING -> customer.outstandingAmount > 0.0
                DuesTab.OVERDUE -> customer.outstandingAmount > 0.0 && (customer.updatedAt < overdueThresholdMillis || (customer.creditLimit > 0 && customer.outstandingAmount >= customer.creditLimit * 0.8))
                DuesTab.SETTLED -> customer.outstandingAmount <= 0.0
                DuesTab.PAYMENTS -> false
            }
            val matchesSearch = query.isBlank() ||
                customer.customerName.lowercase().contains(query) ||
                customer.customerPhone.lowercase().contains(query)
            val matchesMin = minimum == null || customer.outstandingAmount >= minimum
            val matchesDate = if (dueStartRange != null && dueEndRange != null) {
                val dueCreated = if (customer.createdAt > 0L) customer.createdAt else customer.updatedAt
                dueCreated in dueStartRange..dueEndRange
            } else true

            matchesTab && matchesSearch && matchesMin && matchesDate
        }.let { rows ->
            when (sort) {
                DuesSort.HIGHEST -> rows.sortedByDescending { it.outstandingAmount }
                DuesSort.OLDEST -> rows.sortedBy { if (it.createdAt > 0L) it.createdAt else it.updatedAt }
                DuesSort.RECENT -> rows.sortedByDescending { if (it.createdAt > 0L) it.createdAt else it.updatedAt }
            }
        }
    }

    val filteredPayments = remember(allPayments, paymentStart, paymentEnd, searchQuery, customerByPhone) {
        val query = searchQuery.trim().lowercase()
        allPayments.filter { payment ->
            val inDate = payment.timestamp in startOfDay(paymentStart)..endOfDay(paymentEnd)
            if (!inDate) return@filter false
            if (query.isBlank()) return@filter true
            val customerName = customerByPhone[payment.customerPhone]?.customerName?.lowercase() ?: ""
            payment.customerPhone.lowercase().contains(query) ||
                customerName.contains(query) ||
                (payment.referenceNote?.lowercase()?.contains(query) == true) ||
                payment.paymentMethod.lowercase().contains(query)
        }.sortedByDescending { it.timestamp }
    }
    val dateRangeLabel = remember(paymentStart, paymentEnd) {
        val start = DateFormatters.formatDate(paymentStart)
        val end = DateFormatters.formatDate(paymentEnd)
        if (start == end) start else "$start – $end"
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = if (selectedCustomer != null) selectedCustomer!!.customerName else (if (isTablet) stringResource(R.string.dues_title) else stringResource(R.string.dues_list)),
                        metricBadge = if (selectedCustomer == null && outstandingDues.isNotEmpty()) formatTaka(totalOutstanding) else null,
                        badgeColor = PosSectionColors.Dues,
                        isTablet = isTablet,
                        icon = { size -> Realistic3DClipboardDuesIcon(size = size) }
                    )
                },
                navigationIcon = {
                    if (selectedCustomer != null || !isTablet) {
                        IconButton(
                            onClick = {
                                if (selectedCustomer != null) duesViewModel.selectCustomer(null) else onBack()
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                actions = {
                    val actionButtonSize = if (isTablet) 48.dp else 40.dp
                    val actionIconSize = if (isTablet) 24.dp else 22.dp
                    if (selectedCustomer == null) {
                        // 1. Dues & Credit Audit Trail Ledger Icon
                        IconButton(
                            onClick = { showDuesAuditSheet = true },
                            modifier = Modifier.size(actionButtonSize)
                        ) {
                            Realistic3DDigitalKhataIcon(size = actionIconSize)
                        }

                        // 2. Permanent Reminder Bell Icon with BadgedBox
                        IconButton(
                            onClick = { showReminderHubSheet = true },
                            modifier = Modifier.size(actionButtonSize)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (scheduledReminders.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ) {
                                            Text("${scheduledReminders.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            ) {
                                Realistic3DReminderBellIcon(size = actionIconSize, animate = scheduledReminders.isNotEmpty())
                            }
                        }

                        // 3. Export Comprehensive Dues to Excel (.xlsx)
                        IconButton(
                            onClick = {
                                val cleanDate = DateFormatters.formatExportDateTime()
                                exportDuesExcelLauncher.launch("dues-ledger-$cleanDate.xlsx")
                            },
                            modifier = Modifier.size(actionButtonSize)
                        ) {
                            Realistic3DExcelSpreadsheetIcon(size = actionIconSize)
                        }

                        // 4. + Add Due (Compact filled icon button for owner or staff with DUES_ADD_DUE)
                        if (canAddDue) {
                            FilledIconButton(
                                onClick = { showAddDueDialog = true },
                                shape = RoundedCornerShape(9.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .padding(start = 2.dp, end = 2.dp)
                                    .size(actionButtonSize)
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = stringResource(R.string.dues_add_due_button),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // 1. Export Customer Statement to Excel (.xlsx)
                        IconButton(
                            onClick = {
                                selectedCustomer?.let { c ->
                                    val cleanDate = DateFormatters.formatExportDate()
                                    val cleanPhone = c.customerPhone.filter { it.isDigit() }
                                    exportCustomerStatementExcelLauncher.launch("statement-$cleanPhone-$cleanDate.xlsx")
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Realistic3DExcelSpreadsheetIcon(size = 24.dp)
                        }

                        // 2. Share Customer Statement as Text
                        IconButton(
                            onClick = {
                                selectedCustomer?.let { c ->
                                    shareCustomerStatement(context, c, selectedPayments, selectedCharges, selectedReturns, storeName)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.dues_share_statement_cd), modifier = Modifier.size(22.dp))
                        }

                        // 3. Delete Due Account
                        if (isOwner) {
                            IconButton(
                                onClick = {
                                    customerToDelete = selectedCustomer
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dues_delete_account_cd), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (selectedCustomer != null) {
            CustomerDueDetailsView(
                customer = selectedCustomer!!,
                payments = selectedPayments,
                charges = selectedCharges,
                canMutate = isOwner,
                canCollect = canCollect,
                canAddDue = canAddDue,
                onClose = { duesViewModel.selectCustomer(null) },
                onRecordPaymentClick = { paymentCustomer = selectedCustomer },
                onAddDueClick = { showAddDueForCustomer = selectedCustomer },
                onRepaymentClick = { p ->
                    repaymentReceiptData = DueRepaymentReceiptData(
                        customerName = selectedCustomer!!.customerName,
                        customerPhone = selectedCustomer!!.customerPhone,
                        amountPaid = p.amountPaid,
                        paymentMethod = p.paymentMethod,
                        referenceNote = p.referenceNote,
                        remainingDue = selectedCustomer!!.outstandingAmount,
                        timestamp = p.timestamp,
                        isReprint = true
                    )
                },
                onDeletePaymentClick = { p -> paymentToDelete = p },
                onDeleteChargeClick = { c -> chargeToDelete = c },
                onShareStatement = {
                    selectedCustomer?.let { c ->
                        shareCustomerStatement(context, c, selectedPayments, selectedCharges, selectedReturns, storeName)
                    }
                },
                onSetCreditLimit = { limit ->
                    selectedCustomer?.let { c ->
                        duesViewModel.updateCustomerCreditLimit(c.customerPhone, limit)
                    }
                },
                onEditClick = {
                    customerToEditDue = selectedCustomer
                },
                onDeleteClick = {
                    if (isOwner) {
                        customerToDelete = selectedCustomer
                    } else {
                        android.widget.Toast.makeText(context, deleteOwnerOnlyToast, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onCall = {
                    openPhoneDialer(context, selectedCustomer!!.customerPhone)
                },
                onRemind = {
                    if (canSendReminders) {
                        reminderCustomer = selectedCustomer
                    } else {
                        Toast.makeText(context, reminderPermissionRequiredToast, Toast.LENGTH_SHORT).show()
                    }
                },
                onViewDossier = {
                    selectedCustomer?.let { c ->
                        customerViewModel?.loadCustomerDossier(c.customerPhone)
                    }
                },
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            )
        } else {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Spacious Search Box + Matching 46dp 3D Filter Funnel Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PosSearchBox(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholderText = stringResource(R.string.dues_search_placeholder),
                            accentColor = PosSectionColors.Dues,
                            modifier = Modifier.weight(1f)
                        )

                        // 3D Filter Funnel Button with Badge (Unified 46dp height & 12dp squircle)
                        BadgedBox(
                            badge = { if (minimumDue.isNotBlank()) Badge { Text("1") } }
                        ) {
                            Surface(
                                onClick = {
                                    draftMinimumDue = minimumDue
                                    showFilters = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (minimumDue.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (minimumDue.isNotBlank()) PosSectionColors.Dues.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                shadowElevation = 0.5.dp,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Realistic3DFilterIcon(size = 20.dp)
                                }
                            }
                        }
                    }

                    // Row 2: Complete Date Period Chips (All, Today, Week, Month, Custom 📅) + Debtors Count & Sort
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                DuePeriodFilter.ALL_TIME,
                                DuePeriodFilter.TODAY,
                                DuePeriodFilter.THIS_WEEK,
                                DuePeriodFilter.THIS_MONTH,
                                DuePeriodFilter.CUSTOM
                            ).forEach { period ->
                                val isSelected = duePeriod == period
                                val label = when (period) {
                                    DuePeriodFilter.ALL_TIME -> stringResource(R.string.dues_period_chip_all)
                                    DuePeriodFilter.TODAY -> stringResource(R.string.dues_period_chip_today)
                                    DuePeriodFilter.THIS_WEEK -> stringResource(R.string.dues_period_chip_week)
                                    DuePeriodFilter.THIS_MONTH -> stringResource(R.string.dues_period_chip_month)
                                    DuePeriodFilter.CUSTOM -> {
                                        if (isSelected && customDueStart != null && customDueEnd != null) {
                                            val s = DateFormatters.formatDate(customDueStart!!)
                                            val e = DateFormatters.formatDate(customDueEnd!!)
                                            if (s == e) s else "$s – $e"
                                        } else {
                                            stringResource(R.string.dues_period_custom_range)
                                        }
                                    }
                                }
                                Surface(
                                    onClick = {
                                        if (period == DuePeriodFilter.CUSTOM) {
                                            showDuesStartPicker = true
                                        } else {
                                            duePeriod = period
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF059669) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = if (isSelected) null else BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        if (period == DuePeriodFilter.CUSTOM) {
                                            Realistic3DCalendarIcon(size = 12.dp)
                                        }
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
                                text = stringResource(R.string.dues_debtors_count, customerRows.size),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box {
                                TextButton(
                                    onClick = { showSortMenu = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.dues_sort_button),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    DuesSort.entries.forEach { option ->
                                        val sortLabel = when (option) {
                                            DuesSort.HIGHEST -> stringResource(R.string.dues_sort_highest)
                                            DuesSort.RECENT -> stringResource(R.string.dues_sort_recent)
                                            DuesSort.OLDEST -> stringResource(R.string.dues_sort_oldest)
                                        }
                                        DropdownMenuItem(
                                            text = { Text(sortLabel, fontSize = 12.5.sp) },
                                            onClick = { sort = option; showSortMenu = false },
                                            leadingIcon = if (sort == option) ({ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }) else null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Row 3: Status Tabs (Outstanding, Overdue, Settled, Payments)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DuesTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val count = when (tab) {
                                DuesTab.OUTSTANDING -> outstandingDues.size
                                DuesTab.OVERDUE -> allDues.count {
                                    it.outstandingAmount > 0.0 && (it.updatedAt < overdueThresholdMillis || (it.creditLimit > 0 && it.outstandingAmount >= it.creditLimit * 0.8))
                                }
                                DuesTab.SETTLED -> allDues.count { it.outstandingAmount <= 0 }
                                DuesTab.PAYMENTS -> allPayments.size
                            }
                            val tabLabel = when (tab) {
                                DuesTab.OUTSTANDING -> stringResource(R.string.dues_tab_outstanding_count, count)
                                DuesTab.OVERDUE -> stringResource(R.string.dues_tab_overdue_count, count)
                                DuesTab.SETTLED -> stringResource(R.string.dues_tab_settled_count, count)
                                DuesTab.PAYMENTS -> stringResource(R.string.dues_tab_payments_count, count)
                            }
                            Surface(
                                onClick = { selectedTab = tab },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = tabLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                if (selectedTab == DuesTab.PAYMENTS) {
                    Surface(
                        onClick = { showStartPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Realistic3DCalendarIcon(size = 22.dp)
                                Column {
                                    Text(stringResource(R.string.dues_payment_range_label), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                                    Text(dateRangeLabel, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Text(stringResource(R.string.dues_change_range_button), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    PaymentHistoryList(
                        payments = filteredPayments,
                        customerByPhone = customerByPhone,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    )
                } else {
                    // Due Aging Analysis Header (Current vs Follow-up vs Overdue)
                    val sevenDaysAgo = remember(now) { now - 7L * 24 * 60 * 60 * 1000 }
                    val thirtyDaysAgo = remember(now) { now - 30L * 24 * 60 * 60 * 1000 }
                    val currentDueTotal = remember(outstandingDues, debtAgeMap, sevenDaysAgo) {
                        outstandingDues.filter { (debtAgeMap[it.customerPhone] ?: it.updatedAt) >= sevenDaysAgo }.sumOf { it.outstandingAmount }
                    }
                    val moderateDueTotal = remember(outstandingDues, debtAgeMap, sevenDaysAgo, thirtyDaysAgo) {
                        outstandingDues.filter {
                            val ts = debtAgeMap[it.customerPhone] ?: it.updatedAt
                            ts in thirtyDaysAgo until sevenDaysAgo
                        }.sumOf { it.outstandingAmount }
                    }
                    val overdueDueTotal = remember(outstandingDues, debtAgeMap, thirtyDaysAgo) {
                        outstandingDues.filter { (debtAgeMap[it.customerPhone] ?: it.updatedAt) < thirtyDaysAgo }.sumOf { it.outstandingAmount }
                    }

                    if (outstandingDues.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(stringResource(R.string.dues_aging_under_7_days), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                    Text(formatTaka(currentDueTotal), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.dues_aging_7_to_30_days), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                    Text(formatTaka(moderateDueTotal), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(stringResource(R.string.dues_aging_over_30_days), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                    Text(formatTaka(overdueDueTotal), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }

                    if (isTablet) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 440.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item(key = "dues_summary_card", span = { GridItemSpan(maxLineSpan) }) {
                                DuesExecutiveSummaryCard(
                                    outstanding = totalOutstanding,
                                    outstandingCustomers = outstandingDues.size,
                                    collectedThisMonth = collectedThisMonth,
                                    totalEverCollected = totalEverCollected,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (customerRows.isEmpty()) {
                                item(key = "empty_dues", span = { GridItemSpan(maxLineSpan) }) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Realistic3DDigitalKhataIcon(size = 36.dp)
                                            Text(
                                                text = if (searchQuery.isNotBlank()) stringResource(R.string.dues_no_customers_match, searchQuery) else stringResource(R.string.dues_no_customer_accounts_found),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            if (canAddDue && searchQuery.isBlank() && selectedTab == DuesTab.OUTSTANDING) {
                                                Button(
                                                    onClick = { showAddDueDialog = true },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.padding(top = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(stringResource(R.string.dues_add_customer_due_button))
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                gridItems(customerRows, key = { it.customerPhone }) { customer ->
                                    val repaid = remember(paymentsByPhone, customer.customerPhone) {
                                        paymentsByPhone[customer.customerPhone]?.sumOf { it.amountPaid } ?: 0.0
                                    }
                                    val isOverdue = customer.updatedAt < overdueThresholdMillis && customer.outstandingAmount > 0
                                    CustomerDueRow(
                                        customer = customer,
                                        repaidAmount = repaid,
                                        isOverdue = isOverdue,
                                        canCollect = canCollect,
                                        canDelete = isOwner,
                                        onClick = { duesViewModel.selectCustomer(customer) },
                                        onCollect = { paymentCustomer = customer },
                                        onWhatsApp = {
                                            val shop = storeName.trim().ifBlank { if (isAppBangla) "আমাদের প্রতিষ্ঠান" else "Our Store" }
                                            val waMessage = if (isAppBangla) {
                                                "আসসালামু আলাইকুম জনাব ${customer.customerName},\n${shop} থেকে আপনার বকেয়া হিসাবের বিবরণী:\n• মোট অপরিশোধিত বকেয়া: ${formatTaka(customer.outstandingAmount, isBangla = true)}\nঅনুগ্রহপূর্বক সুবিধাজনক সময়ে বকেয়া পরিশোধ করার জন্য বিনীত অনুরোধ করা যাচ্ছে। ধন্যবাদ।"
                                            } else {
                                                "Dear ${customer.customerName},\nOutstanding balance statement from ${shop}:\n• Total Outstanding Due: ${formatTaka(customer.outstandingAmount, isBangla = false)}\nPlease settle the due balance at your earliest convenience. Thank you."
                                            }
                                            openWhatsAppReminder(context, customer.customerPhone, waMessage)
                                        },
                                        onRemind = {
                                            if (canSendReminders) {
                                                reminderCustomer = customer
                                            } else {
                                                Toast.makeText(context, reminderPermissionRequiredToast, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onCall = {
                                            openPhoneDialer(context, customer.customerPhone)
                                        },
                                        onEditDue = {
                                            customerToEditDue = customer
                                        },
                                        onDeleteDue = {
                                            if (isOwner) {
                                                customerToDelete = customer
                                            } else {
                                                Toast.makeText(context, deleteOwnerOnlyToast, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "dues_summary_card") {
                                DuesExecutiveSummaryCard(
                                    outstanding = totalOutstanding,
                                    outstandingCustomers = outstandingDues.size,
                                    collectedThisMonth = collectedThisMonth,
                                    totalEverCollected = totalEverCollected,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (customerRows.isEmpty()) {
                                item(key = "empty_dues") {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Realistic3DDigitalKhataIcon(size = 36.dp)
                                            Text(
                                                text = if (searchQuery.isNotBlank()) stringResource(R.string.dues_no_customers_match, searchQuery) else stringResource(R.string.dues_no_customer_accounts_found),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            if (canAddDue && searchQuery.isBlank() && selectedTab == DuesTab.OUTSTANDING) {
                                                Button(
                                                    onClick = { showAddDueDialog = true },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.padding(top = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(stringResource(R.string.dues_add_customer_due_button))
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(customerRows, key = { it.customerPhone }) { customer ->
                                    val repaid = remember(paymentsByPhone, customer.customerPhone) {
                                        paymentsByPhone[customer.customerPhone]?.sumOf { it.amountPaid } ?: 0.0
                                    }
                                    val isOverdue = customer.updatedAt < overdueThresholdMillis && customer.outstandingAmount > 0
                                    CustomerDueRow(
                                        customer = customer,
                                        repaidAmount = repaid,
                                        isOverdue = isOverdue,
                                        canCollect = canCollect,
                                        canDelete = isOwner,
                                        onClick = { duesViewModel.selectCustomer(customer) },
                                        onCollect = { paymentCustomer = customer },
                                        onWhatsApp = {
                                            val shop = storeName.trim().ifBlank { if (isAppBangla) "আমাদের প্রতিষ্ঠান" else "Our Store" }
                                            val waMessage = if (isAppBangla) {
                                                "আসসালামু আলাইকুম জনাব ${customer.customerName},\n${shop} থেকে আপনার বকেয়া হিসাবের বিবরণী:\n• মোট অপরিশোধিত বকেয়া: ${formatTaka(customer.outstandingAmount, isBangla = true)}\nঅনুগ্রহপূর্বক সুবিধাজনক সময়ে বকেয়া পরিশোধ করার জন্য বিনীত অনুরোধ করা যাচ্ছে। ধন্যবাদ।"
                                            } else {
                                                "Dear ${customer.customerName},\nOutstanding balance statement from ${shop}:\n• Total Outstanding Due: ${formatTaka(customer.outstandingAmount, isBangla = false)}\nPlease settle the due balance at your earliest convenience. Thank you."
                                            }
                                            openWhatsAppReminder(context, customer.customerPhone, waMessage)
                                        },
                                        onRemind = {
                                            if (canSendReminders) {
                                                reminderCustomer = customer
                                            } else {
                                                Toast.makeText(context, reminderPermissionRequiredToast, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onCall = {
                                            openPhoneDialer(context, customer.customerPhone)
                                        },
                                        onEditDue = {
                                            customerToEditDue = customer
                                        },
                                        onDeleteDue = {
                                            if (isOwner) {
                                                customerToDelete = customer
                                            } else {
                                                Toast.makeText(context, deleteOwnerOnlyToast, Toast.LENGTH_SHORT).show()
                                            }
                                        }
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

    if (paymentCustomer != null) {
        RecordPaymentDialog(
            customer = paymentCustomer!!,
            isAppBangla = isAppBangla,
            onDismiss = { paymentCustomer = null },
            onRecordPayment = { amount, method, reference, onSaved ->
                val customer = paymentCustomer ?: return@RecordPaymentDialog
                val appliedAmount = amount.coerceAtMost(customer.outstandingAmount)
                val newDue = (customer.outstandingAmount - appliedAmount).coerceAtLeast(0.0)
                duesViewModel.recordPayment(
                    phone = customer.customerPhone,
                    amount = appliedAmount,
                    paymentMethod = method,
                    referenceNote = reference
                ) { success ->
                    onSaved(success)
                    if (success) {
                        paymentCustomer = null
                        repaymentReceiptData = DueRepaymentReceiptData(
                            customerName = customer.customerName,
                            customerPhone = customer.customerPhone,
                            amountPaid = appliedAmount,
                            paymentMethod = method,
                            referenceNote = reference,
                            remainingDue = newDue
                        )
                    }
                }
            }
        )
    }

    if (repaymentReceiptData != null) {
        DueRepaymentReceiptDialog(
            data = repaymentReceiptData!!,
            storeName = storeName,
            storeAddress = storeAddress,
            storePhone = storePhone,
            settings = invoiceSettingsData,
            isAppBangla = isAppBangla,
            onDismiss = { repaymentReceiptData = null }
        )
    }

    if (showAddDueDialog) {
        AddDueDialog(
            canOverrideLimit = canOverrideLimit,
            onDismiss = { showAddDueDialog = false },
            onAddDue = { name, phone, amount, limit, note, onComplete ->
                duesViewModel.addOrUpdateDue(
                    name = name,
                    phone = phone,
                    amount = amount,
                    creditLimit = limit,
                    note = note,
                    onComplete = { success ->
                        onComplete(success)
                        if (success) {
                            showAddDueDialog = false
                            android.widget.Toast.makeText(context, addDueSuccessToast, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        )
    }

    if (showAddDueForCustomer != null) {
        val target = showAddDueForCustomer!!
        val addDueToCustomerSuccessToast = stringResource(R.string.dues_add_due_to_customer_success_toast, target.customerName)
        val addDueGenericFailureToast = stringResource(R.string.dues_add_due_generic_failure_toast)
        AddDueDialog(
            initialName = target.customerName,
            initialPhone = target.customerPhone,
            initialDue = target.outstandingAmount,
            initialCreditLimit = target.creditLimit,
            canOverrideLimit = canOverrideLimit,
            onDismiss = { showAddDueForCustomer = null },
            onAddDue = { name, phone, amount, limit, note, onComplete ->
                duesViewModel.addOrUpdateDue(
                    name = name,
                    phone = phone,
                    amount = amount,
                    creditLimit = limit,
                    note = note,
                    onComplete = { success ->
                        onComplete(success)
                        if (success) {
                            showAddDueForCustomer = null
                            android.widget.Toast.makeText(context, addDueToCustomerSuccessToast, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        )
    }

    if (reminderCustomer != null) {
        SendDueReminderDialog(
            customer = reminderCustomer!!,
            storeName = storeName,
            settingsViewModel = settingsViewModel,
            onLogReminder = { phone, name, amount, channel, text, status ->
                duesViewModel.logReminder(phone, name, amount, channel, text, status)
            },
            onDismiss = { reminderCustomer = null }
        )
    }

    if (showReminderHubSheet) {
        DuesReminderHubSheet(
            reminders = scheduledReminders,
            logs = reminderLogs,
            onCancelReminder = { phone -> duesViewModel.cancelScheduledReminder(context, phone) },
            onSendReminderNow = { reminder ->
                scope.launch {
                    val normPhone = normalizePhone(reminder.customerPhone)
                    val freshDue = duesViewModel.getCustomerDue(normPhone)
                        ?: duesViewModel.duesList.value.firstOrNull { normalizePhone(it.customerPhone) == normPhone }
                    val currentDue = freshDue?.outstandingAmount ?: 0.0
                    if (currentDue <= 0.001) {
                        android.widget.Toast.makeText(context, context.getString(R.string.dues_settled_no_reminder_toast), android.widget.Toast.LENGTH_SHORT).show()
                        duesViewModel.cancelScheduledReminder(context, reminder.customerPhone)
                        return@launch
                    }
                    val message = if (currentDue != reminder.dueAmount) {
                        reminder.messageText
                            .replace(formatTaka(reminder.dueAmount, isBangla = false), formatTaka(currentDue, isBangla = false))
                            .replace(formatTaka(reminder.dueAmount, isBangla = true), formatTaka(currentDue, isBangla = true))
                    } else reminder.messageText

                    val channelName = when (reminder.channel.lowercase(Locale.ROOT)) {
                        "sms" -> {
                            openPaymentReminder(context, reminder.customerPhone, message)
                            "SMS"
                        }
                        "wa" -> {
                            openWhatsAppReminder(context, reminder.customerPhone, message)
                            "WHATSAPP"
                        }
                        else -> {
                            shareReceiptAsText(context, message)
                            "BOTH"
                        }
                    }
                    duesViewModel.logReminder(reminder.customerPhone, reminder.customerName, currentDue, channelName, message, "SENT")
                    if (!reminder.isRecurring) {
                        duesViewModel.cancelScheduledReminder(context, reminder.customerPhone)
                    }
                }
            },
            onDeleteLog = { id -> duesViewModel.deleteReminderLog(id) },
            onClearAllLogs = { duesViewModel.clearAllReminderLogs() },
            onDismiss = { showReminderHubSheet = false }
        )
    }

    if (showDuesAuditSheet) {
        DuesAuditHistorySheet(
            entries = duesAuditEntries,
            onDismiss = { showDuesAuditSheet = false }
        )
    }

    if (dossierData != null && customerViewModel != null) {
        CustomerDossierSheet(
            dossier = dossierData!!,
            authViewModel = authViewModel,
            onDismiss = { customerViewModel.clearDossier() },
            onStartSale = { customer ->
                customerViewModel.clearDossier()
                onStartSaleForCustomer?.invoke(customer)
            },
            onEditCustomer = { customer ->
                customerViewModel.clearDossier()
                val due = allDues.find { it.customerPhone == customer.phone }
                    ?: CustomerDue(
                        customerName = customer.name,
                        customerPhone = customer.phone,
                        outstandingAmount = customer.totalDue,
                        creditLimit = customer.creditLimit,
                        updatedAt = System.currentTimeMillis()
                    )
                customerToEditDue = due
            },
            onAddNote = { text, cat ->
                dossierData?.customer?.phone?.let { phone ->
                    customerViewModel.addCustomerNote(
                        phone = phone,
                        noteText = text,
                        category = cat,
                        onSuccess = { Toast.makeText(context, context.getString(R.string.customer_note_saved), Toast.LENGTH_SHORT).show() },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                }
            },
            onDeleteNote = { index ->
                dossierData?.customer?.phone?.let { phone ->
                    customerViewModel.deleteCustomerNote(
                        phone = phone,
                        noteIndex = index,
                        onSuccess = { Toast.makeText(context, context.getString(R.string.customer_note_deleted), Toast.LENGTH_SHORT).show() },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                }
            },
            onAdjustCredit = { delta, note ->
                dossierData?.customer?.phone?.let { phone ->
                    customerViewModel.adjustCustomerStoreCredit(
                        phone = phone,
                        delta = delta,
                        note = note,
                        onSuccess = { Toast.makeText(context, context.getString(R.string.store_credit_adjusted_success), Toast.LENGTH_SHORT).show() },
                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                    )
                }
            },
            settingsViewModel = settingsViewModel
        )
    }

    if (customerToEditDue != null) {
        val editingCustomer = customerToEditDue!!
        EditDueCustomerDialog(
            customer = editingCustomer,
            onDismiss = { customerToEditDue = null },
            onSave = { newPhone, newName, newLimit, notes ->
                val oldPhone = editingCustomer.customerPhone
                val cleanNewPhone = newPhone.trim()
                if (cleanNewPhone != oldPhone) {
                    duesViewModel.migrateCustomerDuePhone(
                        oldPhone = oldPhone,
                        newPhone = cleanNewPhone,
                        newName = newName,
                        creditLimit = newLimit,
                        note = notes,
                        onSuccess = { finalPhone ->
                            Toast.makeText(context, context.getString(R.string.customer_phone_updated_success), Toast.LENGTH_SHORT).show()
                            val updatedDue = editingCustomer.copy(
                                customerPhone = finalPhone,
                                customerName = newName,
                                creditLimit = newLimit,
                                updatedAt = System.currentTimeMillis()
                            )
                            if (selectedCustomer?.customerPhone == oldPhone || selectedCustomer?.customerPhone == finalPhone) {
                                duesViewModel.selectCustomer(updatedDue)
                            }
                            customerToEditDue = null
                        },
                        onCollision = { existingCust ->
                            customerToEditDue = null
                            collisionMergeCustomer = Pair(editingCustomer, existingCust)
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                } else {
                    duesViewModel.addOrUpdateDue(
                        name = newName,
                        phone = editingCustomer.customerPhone,
                        amount = 0.0,
                        creditLimit = newLimit,
                        note = notes
                    ) { success ->
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.customer_due_info_updated), Toast.LENGTH_SHORT).show()
                            val updatedDue = editingCustomer.copy(
                                customerName = newName,
                                creditLimit = newLimit,
                                updatedAt = System.currentTimeMillis()
                            )
                            if (selectedCustomer?.customerPhone == editingCustomer.customerPhone) {
                                duesViewModel.selectCustomer(updatedDue)
                            }
                        }
                    }
                    customerToEditDue = null
                }
            }
        )
    }

    if (collisionMergeCustomer != null) {
        val (dueCustomer, existingCustomer) = collisionMergeCustomer!!
        AlertDialog(
            onDismissRequest = { collisionMergeCustomer = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔗", fontSize = 20.sp)
                    Text(stringResource(R.string.customer_merge_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.customer_phone_collision_msg, existingCustomer.phone, existingCustomer.name),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.customer_merge_prompt, dueCustomer.customerName, dueCustomer.customerPhone, existingCustomer.name, existingCustomer.phone),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        duesViewModel.mergeCustomers(
                            masterPhone = existingCustomer.phone,
                            secondaryPhone = dueCustomer.customerPhone,
                            onSuccess = {
                                Toast.makeText(context, context.getString(R.string.customer_merge_success), Toast.LENGTH_SHORT).show()
                                collisionMergeCustomer = null
                            },
                            onError = { err ->
                                Toast.makeText(context, context.getString(R.string.customer_merge_failed, err), Toast.LENGTH_LONG).show()
                                collisionMergeCustomer = null
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.customer_merge_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { collisionMergeCustomer = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showFilters) {
        DuesFilterBottomSheet(
            onDismiss = { showFilters = false },
            currentMinDue = minimumDue,
            onApply = { newMin ->
                minimumDue = newMin
            },
            matchingCount = customerRows.size
        )
    }

    if (customerToDelete != null) {
        val c = customerToDelete!!
        val hasDues = c.outstandingAmount > 0.001
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Text(
                    text = if (hasDues) stringResource(R.string.dues_cannot_delete_title) else stringResource(R.string.dues_delete_ledger_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (hasDues) {
                    Text(stringResource(R.string.dues_cannot_delete_message, c.customerName, c.customerPhone, formatTaka(c.outstandingAmount)))
                } else {
                    Text(stringResource(R.string.dues_delete_ledger_confirm, c.customerName, c.customerPhone))
                }
            },
            confirmButton = {
                if (!hasDues) {
                    Button(
                        onClick = {
                            customerToDelete?.let { cust ->
                                duesViewModel.deleteCustomerDue(cust.customerPhone)
                            }
                            customerToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.dues_archive_button))
                    }
                } else {
                    Button(onClick = { customerToDelete = null }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            },
            dismissButton = {
                if (!hasDues) {
                    TextButton(onClick = { customerToDelete = null }) {
                        Text(stringResource(R.string.dues_cancel))
                    }
                }
            }
        )
    }

    if (paymentToDelete != null) {
        val payment = paymentToDelete!!
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.dues_delete_payment_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.dues_delete_payment_confirm, formatTaka(payment.amountPaid)))
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedCustomer?.let { c ->
                            duesViewModel.deletePayment(c.customerPhone, payment.id) { success ->
                                if (!success) {
                                    android.widget.Toast.makeText(context, deleteOwnerOnlyToast, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dues_delete_payment_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text(stringResource(R.string.dues_cancel))
                }
            }
        )
    }

    if (chargeToDelete != null) {
        val charge = chargeToDelete!!
        AlertDialog(
            onDismissRequest = { chargeToDelete = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.dues_delete_charge_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(
                        if (charge.reason == "Sale") R.string.dues_delete_charge_confirm_sale
                        else R.string.dues_delete_charge_confirm_manual,
                        formatTaka(charge.amount)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedCustomer?.let { c ->
                            duesViewModel.deleteCharge(c.customerPhone, charge.id) { success ->
                                if (!success) {
                                    android.widget.Toast.makeText(context, deleteOwnerOnlyToast, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        chargeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dues_delete_charge_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { chargeToDelete = null }) {
                    Text(stringResource(R.string.dues_cancel))
                }
            }
        )
    }

    if (showDuesStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcMidnight(customDueStart ?: now))
        DatePickerDialog(
            onDismissRequest = { showDuesStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customDueStart = utcMidnightToLocalMidnight(it) }
                    showDuesStartPicker = false
                    showDuesEndPicker = true
                }) { Text(stringResource(R.string.dues_next_end_date_button)) }
            },
            dismissButton = { TextButton(onClick = { showDuesStartPicker = false }) { Text(stringResource(R.string.dues_cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.dues_select_dues_start_date), Modifier.padding(24.dp, 16.dp)) }) }
    }

    if (showDuesEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcMidnight(customDueEnd ?: customDueStart ?: now))
        DatePickerDialog(
            onDismissRequest = { showDuesEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { customDueEnd = utcMidnightToLocalMidnight(it) }
                    val s = customDueStart ?: now
                    val e = customDueEnd ?: now
                    if (s > e) {
                        customDueStart = e
                        customDueEnd = s
                    }
                    duePeriod = DuePeriodFilter.CUSTOM
                    showDuesEndPicker = false
                }) { Text(stringResource(R.string.dues_apply_filter_button)) }
            },
            dismissButton = { TextButton(onClick = { showDuesEndPicker = false }) { Text(stringResource(R.string.dues_cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.dues_select_dues_end_date), Modifier.padding(24.dp, 16.dp)) }) }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcMidnight(paymentStart))
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { paymentStart = utcMidnightToLocalMidnight(it) }
                    if (paymentStart > paymentEnd) {
                        val swap = paymentStart
                        paymentStart = paymentEnd
                        paymentEnd = swap
                    }
                    showStartPicker = false
                }) { Text(stringResource(R.string.dues_apply_button)) }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.dues_cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.dues_select_start_date), Modifier.padding(24.dp, 16.dp)) }) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localMillisToUtcMidnight(paymentEnd))
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { paymentEnd = utcMidnightToLocalMidnight(it) }
                    if (paymentStart > paymentEnd) {
                        val swap = paymentStart
                        paymentStart = paymentEnd
                        paymentEnd = swap
                    }
                    showEndPicker = false
                }) { Text(stringResource(R.string.dues_apply_button)) }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.dues_cancel)) } }
        ) { DatePicker(state = state, title = { Text(stringResource(R.string.dues_select_end_date), Modifier.padding(24.dp, 16.dp)) }) }
    }
}

private fun shareCustomerStatement(
    context: android.content.Context,
    customer: CustomerDue,
    payments: List<DuePayment>,
    charges: List<DueCharge> = emptyList(),
    returns: List<com.example.techyfipos.data.db.Return> = emptyList(),
    storeName: String = ""
) {
    val isBangla = com.example.techyfipos.utils.isAppBangla()
    val totalRepaid = payments.sumOf { it.amountPaid }
    val displayStore = storeName.trim().ifBlank { if (isBangla) "টেকিফাই স্টোর" else "TECHYFI STORE" }.uppercase()

    data class StatementLedgerEntry(
        val timestamp: Long,
        val description: String,
        val debit: Double,
        val credit: Double
    )

    val ledger = mutableListOf<StatementLedgerEntry>()
    charges.forEach { c ->
        val desc = when {
            c.reason.equals("Sale", ignoreCase = true) -> if (isBangla) "বাকিতে বিক্রয়" else "Credit Sale"
            !c.note.isNullOrBlank() -> c.note
            else -> if (isBangla) "বকেয়া চার্জ" else "Due Charge"
        }
        ledger.add(StatementLedgerEntry(c.timestamp, desc, debit = c.amount, credit = 0.0))
    }
    payments.forEach { p ->
        val noteSuffix = p.referenceNote?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
        val desc = "${if (isBangla) "পরিশোধ" else "Payment"} [${p.paymentMethod}]$noteSuffix"
        ledger.add(StatementLedgerEntry(p.timestamp, desc, debit = 0.0, credit = p.amountPaid))
    }
    returns.forEach { r ->
        val desc = "${if (isBangla) "পণ্য ফেরত" else "Return"} #${r.saleId}"
        ledger.add(StatementLedgerEntry(r.timestamp, desc, debit = 0.0, credit = r.dueAdjustmentAmount))
    }
    ledger.sortBy { it.timestamp }

    // Reconcile opening balance / return adjustments so running balance matches outstandingAmount exactly
    val netLedger = charges.sumOf { it.amount } - payments.sumOf { it.amountPaid } - returns.sumOf { it.dueAdjustmentAmount }
    val openingBalance = customer.outstandingAmount - netLedger
    if (kotlin.math.abs(openingBalance) > 0.001) {
        val earliestTime = (charges.map { it.timestamp } + payments.map { it.timestamp } + returns.map { it.timestamp }).minOrNull() ?: customer.createdAt
        val desc = if (openingBalance > 0.0) {
            if (isBangla) "পূর্ববর্তী জের / প্রারম্ভিক বকেয়া" else "Opening / Previous Due"
        } else {
            if (isBangla) "সমন্বয় / ক্রেডিট" else "Adjustment / Credit"
        }
        val debit = if (openingBalance > 0.0) openingBalance else 0.0
        val credit = if (openingBalance < 0.0) -openingBalance else 0.0
        ledger.add(0, StatementLedgerEntry(earliestTime - 1000L, desc, debit = debit, credit = credit))
    }

    val statement = buildString {
        appendLine("================================")
        appendLine(if (isBangla) "     $displayStore - খাতা স্টেটমেন্ট" else "     $displayStore - KHATA STATEMENT")
        appendLine("================================")
        appendLine("${if (isBangla) "গ্রাহক" else "Customer"}: ${customer.customerName}")
        appendLine("${if (isBangla) "মোবাইল" else "Phone"}: ${customer.customerPhone}")
        appendLine("--------------------------------")
        appendLine("${if (isBangla) "বর্তমান বকেয়া" else "Outstanding Due"}: ${formatTaka(customer.outstandingAmount, isBangla = isBangla)}")
        if (customer.creditLimit > 0.0) {
            val pct = ((customer.outstandingAmount / customer.creditLimit) * 100).toInt()
            val pctStr = if (isBangla) "${toBanglaDigits(pct.toString())}% ব্যবহৃত" else "$pct% used"
            appendLine("${if (isBangla) "ক্রেডিট সীমা" else "Credit Limit"}: ${formatTaka(customer.creditLimit, isBangla = isBangla)} ($pctStr)")
        }
        appendLine("${if (isBangla) "সর্বমোট পরিশোধ" else "Total Repaid to Date"}: ${formatTaka(totalRepaid, isBangla = isBangla)}")
        appendLine("--------------------------------")
        appendLine(if (isBangla) "লেনদেনের খতিয়ান (চলতি জের):" else "TRANSACTION LEDGER (RUNNING BALANCE):")

        if (ledger.isEmpty()) {
            appendLine(if (isBangla) "  কোনো লেনদেন রেকর্ড পাওয়া যায়নি।" else "  No transactions recorded yet.")
        } else {
            var runningBalance = 0.0
            ledger.forEach { entry ->
                runningBalance += (entry.debit - entry.credit)
                val dateStr = DateFormatters.formatDateTime(entry.timestamp)
                appendLine("• $dateStr")
                appendLine("  ${entry.description}")
                if (entry.debit > 0.0) {
                    appendLine("  +${if (isBangla) "বকেয়া" else "Due"}: ${formatTaka(entry.debit, isBangla = isBangla)}")
                }
                if (entry.credit > 0.0) {
                    appendLine("  -${if (isBangla) "জমা" else "Paid"}: ${formatTaka(entry.credit, isBangla = isBangla)}")
                }
                appendLine("  => ${if (isBangla) "জের" else "Balance"}: ${formatTaka(runningBalance, isBangla = isBangla)}")
            }
        }
        appendLine("--------------------------------")
        appendLine("${if (isBangla) "তৈরির তারিখ" else "Generated"}: ${DateFormatters.formatDateTime(System.currentTimeMillis())}")
        appendLine(if (isBangla) "আমাদের সাথে থাকার জন্য ধন্যবাদ!" else "Thank you for your business!")
    }
    shareReceiptAsText(context, statement)
}

enum class ReminderMode(
    @StringRes val titleRes: Int,
    val titleBn: String,
    val titleEn: String
) {
    INSTANT(R.string.due_reminder_mode_instant, "তাৎক্ষণিক পাঠান", "Instant Send"),
    SCHEDULE(R.string.due_reminder_mode_schedule, "শিডিউল করুন", "Auto-Schedule")
}

enum class ReminderLanguage(
    @StringRes val titleRes: Int,
    val titleBn: String,
    val titleEn: String
) {
    BANGLA(R.string.due_reminder_lang_bangla, "বাংলা (Bengali)", "Bengali (বাংলা)"),
    ENGLISH(R.string.due_reminder_lang_english, "ইংরেজি (English)", "English"),
    BILINGUAL(R.string.due_reminder_lang_bilingual, "উভয় (Bilingual)", "Bilingual (উভয়)")
}

enum class DeliveryChannel(
    @StringRes val titleRes: Int,
    val titleBn: String,
    val titleEn: String,
    val id: String,
    @StringRes val hintRes: Int,
    val hintBn: String,
    val hintEn: String
) {
    WHATSAPP(R.string.delivery_channel_whatsapp, "WhatsApp", "WhatsApp", "wa", R.string.delivery_channel_whatsapp_hint, "নির্ধারিত সময়ে WhatsApp পাঠানোর নোটিফিকেশন আসবে", "Triggers WhatsApp notification prompt"),
    SMS(R.string.delivery_channel_sms, "SMS", "SMS", "sms", R.string.delivery_channel_sms_hint, "নির্ধারিত সময়ে SMS পাঠানোর নোটিফিকেশন আসবে", "Triggers SMS notification prompt"),
    BOTH(R.string.delivery_channel_both, "উভয় (Both)", "Both", "both", R.string.delivery_channel_both_hint, "SMS ও WhatsApp নোটিফিকেশন প্রম্পট আসবে", "SMS & WhatsApp notification prompts")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendDueReminderDialog(
    customer: CustomerDue,
    storeName: String,
    settingsViewModel: SettingsViewModel? = null,
    onLogReminder: (customerPhone: String, customerName: String, dueAmount: Double, channel: String, messageText: String, status: String) -> Unit = { _, _, _, _, _, _ -> },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reminderPermissionRequiredToast = stringResource(R.string.dues_reminder_permission_required_toast)

    val savedTemplates by (settingsViewModel?.dueReminderTemplates ?: flowOf(DueTemplate.defaultTemplates())).collectAsStateWithLifecycle(DueTemplate.defaultTemplates())
    val templates = if (savedTemplates.isEmpty()) DueTemplate.defaultTemplates() else savedTemplates
    val shopPhone by (settingsViewModel?.shopPhone ?: flowOf("")).collectAsStateWithLifecycle("")
    val appLanguage by (settingsViewModel?.language ?: flowOf("en")).collectAsStateWithLifecycle("en")
    val isAppBangla = appLanguage == "bn"

    val effectiveStoreName = storeName.trim().ifBlank { if (isAppBangla) "আমাদের প্রতিষ্ঠান" else "Our Store" }
    val effectiveStoreNameEng = storeName.trim().ifBlank { "Our Store" }
    val effectiveBkash = shopPhone.trim()

    var mode by rememberSaveable { mutableStateOf(ReminderMode.INSTANT) }
    var selectedLang by rememberSaveable { mutableStateOf(if (isAppBangla) ReminderLanguage.BANGLA else ReminderLanguage.ENGLISH) }
    var selectedTemplateId by rememberSaveable { mutableStateOf(templates.firstOrNull()?.id ?: "template_polite") }
    var selectedChannel by rememberSaveable { mutableStateOf(DeliveryChannel.BOTH) }
    var selectedPreset by rememberSaveable { mutableStateOf(DueReminderScheduler.SchedulePreset.TOMORROW_10AM) }
    var customHours by rememberSaveable { mutableStateOf(12) }
    val initialCustomTarget = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var customTargetMillis by rememberSaveable { mutableStateOf(initialCustomTarget) }

    var isCustomEdited by rememberSaveable { mutableStateOf(false) }
    var customReminderText by rememberSaveable { mutableStateOf("") }
    var showTemplateManager by remember { mutableStateOf(false) }

    LaunchedEffect(appLanguage) {
        if (!isCustomEdited) {
            selectedLang = if (isAppBangla) ReminderLanguage.BANGLA else ReminderLanguage.ENGLISH
        }
    }

    fun formatTemplateString(raw: String, isBn: Boolean = isAppBangla): String {
        var res = raw
            .replace("{customer}", customer.customerName)
            .replace("{due}", formatTaka(customer.outstandingAmount, isBangla = isBn))
            .replace("{store}", if (!isBn) effectiveStoreNameEng else effectiveStoreName)
            .replace("{phone}", customer.customerPhone)
        if (effectiveBkash.isNotBlank()) {
            res = res.replace("{bkash}", effectiveBkash)
        } else {
            res = res
                .replace(" bKash/Nagad: {bkash}।", "।")
                .replace(" bKash/Nagad: {bkash}.", ".")
                .replace(" via bKash/Nagad: {bkash}.", ".")
                .replace("bKash/Nagad: {bkash}।", "")
                .replace("bKash/Nagad: {bkash}.", "")
                .replace("via bKash/Nagad: {bkash}.", "")
                .replace("{bkash}", "")
                .trim()
        }
        return res
    }

    val activeTemplate = templates.find { it.id == selectedTemplateId } ?: templates.firstOrNull() ?: DueTemplate.defaultTemplates().first()

    val computedReminderText = remember(activeTemplate, selectedLang, customer, effectiveStoreName, effectiveBkash) {
        when (selectedLang) {
            ReminderLanguage.BANGLA -> formatTemplateString(activeTemplate.textBn, isBn = true)
            ReminderLanguage.ENGLISH -> formatTemplateString(activeTemplate.textEn, isBn = false)
            ReminderLanguage.BILINGUAL -> {
                val en = formatTemplateString(activeTemplate.textEn, isBn = false)
                val bn = formatTemplateString(activeTemplate.textBn, isBn = true)
                "$bn\n\n($en)"
            }
        }
    }

    val currentReminderText = if (isCustomEdited) customReminderText else computedReminderText

    val scheduleReminder = {
        val delayMillis = DueReminderScheduler.calculateDelayForPreset(
            preset = selectedPreset,
            customHours = customHours,
            customTargetEpochMillis = if (selectedPreset == DueReminderScheduler.SchedulePreset.CUSTOM) customTargetMillis else null
        )
        DueReminderScheduler.scheduleDueReminder(
            context = context,
            customerName = customer.customerName,
            customerPhone = customer.customerPhone,
            dueAmount = customer.outstandingAmount,
            messageText = currentReminderText,
            channel = selectedChannel.id,
            delayMillis = delayMillis,
            isRecurring = selectedPreset == DueReminderScheduler.SchedulePreset.WEEKLY
        )
        onLogReminder(
            customer.customerPhone,
            customer.customerName,
            customer.outstandingAmount,
            selectedChannel.name,
            currentReminderText,
            "SCHEDULED"
        )
        val presetName = when (selectedPreset) {
            DueReminderScheduler.SchedulePreset.CUSTOM -> {
                DateFormatters.formatShortDateTime(customTargetMillis, if (isAppBangla) Locale.forLanguageTag("bn") else Locale.ENGLISH)
            }
            else -> context.getString(selectedPreset.titleRes)
        }
        Toast.makeText(
            context,
            context.getString(R.string.dues_reminder_scheduled_toast_fmt, presetName),
            Toast.LENGTH_SHORT
        ).show()
        onDismiss()
    }
    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val notificationGranted = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            grants[android.Manifest.permission.POST_NOTIFICATIONS] == true ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (notificationGranted) {
            scheduleReminder()
        } else {
            Toast.makeText(context, reminderPermissionRequiredToast, Toast.LENGTH_LONG).show()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 700.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Realistic3DReminderBellIcon(size = 40.dp, animate = true)
                    Column {
                        Text(
                            text = if (isAppBangla) "বকেয়া তাগাদা" else "Due Reminder",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${customer.customerName} • ${customer.customerPhone}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 2. Scrollable Body Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Outstanding Balance Strip
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFE4E6),
                                modifier = Modifier.padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isAppBangla) "বকেয়া" else "OVERDUE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFE11D48),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (isAppBangla) "মোট অপরিশোধিত:" else "Outstanding Due:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF9F1239)
                            )
                        }
                        Text(
                            text = formatTaka(customer.outstandingAmount, isBangla = isAppBangla),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFBE123C)
                        )
                    }
                }

                // Mode Selector with 3D Animated Bolt vs Chrono Clock
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReminderMode.values().forEach { m ->
                            val isSelected = mode == m
                            Surface(
                                onClick = { mode = m },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color.White else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (m == ReminderMode.INSTANT) {
                                        Realistic3DInstantBoltIcon(size = 18.dp, animate = isSelected)
                                    } else {
                                        Realistic3DClockScheduleIcon(size = 18.dp, animate = isSelected)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(m.titleRes),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }

                // Template & Language Customizer
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Realistic3DDigitalKhataIcon(size = 18.dp)
                                Text(
                                    text = if (isAppBangla) "টেমপ্লেট ও ভাষা" else "Template & Language",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                            }
                            TextButton(
                                onClick = { showTemplateManager = true },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Realistic3DGearIcon(size = 14.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isAppBangla) "ম্যানেজ" else "Manage",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Language Selector Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReminderLanguage.values().forEach { lang ->
                                val isSelected = selectedLang == lang
                                Surface(
                                    onClick = {
                                        selectedLang = lang
                                        isCustomEdited = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFF059669) else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF059669) else Color(0xFFCBD5E1)),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = stringResource(lang.titleRes),
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else Color(0xFF334155),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Template Selector Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(templates) { tpl ->
                                val isSelected = selectedTemplateId == tpl.id
                                Surface(
                                    onClick = {
                                        selectedTemplateId = tpl.id
                                        isCustomEdited = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF3B82F6) else Color(0xFFCBD5E1)),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Realistic3DMessageReminderIcon(size = 14.dp)
                                        val rawName = if (isAppBangla) tpl.nameBn else tpl.nameEn
                                        val cleanName = rawName.replace(Regex("^[^a-zA-Z0-9\\u0980-\\u09FF]+\\s*"), "")
                                        Text(
                                            text = cleanName.ifBlank { rawName },
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF334155)
                                        )
                                    }
                                }
                            }
                        }

                        // Message Preview Box / Custom Editor
                        OutlinedTextField(
                            value = currentReminderText,
                            onValueChange = {
                                isCustomEdited = true
                                customReminderText = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                            shape = RoundedCornerShape(10.dp),
                            minLines = 3,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF334155),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏪 $effectiveStoreName",
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            val smsCount = ((currentReminderText.length / 160) + 1).coerceAtLeast(1)
                            Text(
                                text = "${currentReminderText.length} chars • $smsCount SMS",
                                fontSize = 10.5.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Auto-Schedule Details (Only shown in Auto-Schedule Mode)
                if (mode == ReminderMode.SCHEDULE) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Realistic3DClockScheduleIcon(size = 16.dp, animate = true)
                                    Text(
                                        text = if (isAppBangla) "শিডিউল ফ্রিকোয়েন্সি" else "Schedule Frequency",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.dues_exact_alarm_engine),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                            }

                            // Preset Buttons Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DueReminderScheduler.SchedulePreset.values().forEach { preset ->
                                    val isSelected = selectedPreset == preset
                                    val title = stringResource(preset.titleRes)
                                    Surface(
                                        onClick = { selectedPreset = preset },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF0284C7) else Color.White,
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else Color(0xFFBAE6FD)),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = title,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.White else Color(0xFF0369A1),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Custom Date & Time Picker Container
                            if (selectedPreset == DueReminderScheduler.SchedulePreset.CUSTOM) {
                                val customDateStr = remember(customTargetMillis, isAppBangla) {
                                    DateFormatters.formatDate(customTargetMillis, if (isAppBangla) Locale.forLanguageTag("bn") else Locale.ENGLISH)
                                }
                                val customTimeStr = remember(customTargetMillis, isAppBangla) {
                                    DateFormatters.formatTime(customTargetMillis, if (isAppBangla) Locale.forLanguageTag("bn") else Locale.ENGLISH)
                                }
                                val diffMillis = (customTargetMillis - System.currentTimeMillis()).coerceAtLeast(0)
                                val diffHoursTotal = diffMillis / (3600 * 1000L)
                                val diffDays = diffHoursTotal / 24
                                val diffHoursRemaining = diffHoursTotal % 24

                                val timeDiffText = when {
                                    diffDays > 0 && diffHoursRemaining > 0 -> if (isAppBangla) "${diffDays} দিন ${diffHoursRemaining} ঘণ্টা পর" else "in ${diffDays}d ${diffHoursRemaining}h"
                                    diffDays > 0 -> if (isAppBangla) "${diffDays} দিন পর" else "in ${diffDays} days"
                                    diffHoursTotal > 0 -> if (isAppBangla) "${diffHoursTotal} ঘণ্টা পর" else "in ${diffHoursTotal} hours"
                                    else -> if (isAppBangla) "শীঘ্রই (১ ঘণ্টার মধ্যে)" else "shortly"
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFF0F9FF))
                                        .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row with Date & Time buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Date Picker Card with 3D Calendar Icon
                                        Surface(
                                            onClick = {
                                                val hostActivity = context.findActivity() ?: (context as? android.app.Activity)
                                                if (hostActivity != null && !hostActivity.isFinishing && !hostActivity.isDestroyed) {
                                                    val cal = Calendar.getInstance().apply { timeInMillis = customTargetMillis }
                                                    android.app.DatePickerDialog(
                                                        hostActivity,
                                                        { _, year, month, dayOfMonth ->
                                                            val updated = Calendar.getInstance().apply {
                                                                timeInMillis = customTargetMillis
                                                                set(Calendar.YEAR, year)
                                                                set(Calendar.MONTH, month)
                                                                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                            }
                                                            customTargetMillis = updated.timeInMillis
                                                        },
                                                        cal.get(Calendar.YEAR),
                                                        cal.get(Calendar.MONTH),
                                                        cal.get(Calendar.DAY_OF_MONTH)
                                                    ).apply {
                                                        datePicker.minDate = System.currentTimeMillis() - 1000L
                                                    }.show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(1.15f).height(52.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Realistic3DCalendarIcon(size = 20.dp)
                                                }
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                                    Text(
                                                        text = if (isAppBangla) "তারিখ (Date)" else "DATE",
                                                        fontSize = 11.sp,
                                                        lineHeight = 13.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = customDateStr,
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        // Time Picker Card with 3D Clock Icon
                                        Surface(
                                            onClick = {
                                                val hostActivity = context.findActivity() ?: (context as? android.app.Activity)
                                                if (hostActivity != null && !hostActivity.isFinishing && !hostActivity.isDestroyed) {
                                                    val cal = Calendar.getInstance().apply { timeInMillis = customTargetMillis }
                                                    android.app.TimePickerDialog(
                                                        hostActivity,
                                                        { _, hourOfDay, minute ->
                                                            val updated = Calendar.getInstance().apply {
                                                                timeInMillis = customTargetMillis
                                                                set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                                set(Calendar.MINUTE, minute)
                                                                set(Calendar.SECOND, 0)
                                                                set(Calendar.MILLISECOND, 0)
                                                            }
                                                            customTargetMillis = updated.timeInMillis
                                                        },
                                                        cal.get(Calendar.HOUR_OF_DAY),
                                                        cal.get(Calendar.MINUTE),
                                                        false
                                                    ).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.weight(0.95f).height(52.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Realistic3DClockScheduleIcon(size = 20.dp, animate = false)
                                                }
                                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                                    Text(
                                                        text = if (isAppBangla) "সময় (Time)" else "TIME",
                                                        fontSize = 11.sp,
                                                        lineHeight = 13.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = customTimeStr,
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Quick Nudge Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        listOf(
                                            Pair("+1h", 1 to 0),
                                            Pair("+3h", 3 to 0),
                                            Pair("+12h", 12 to 0),
                                            Pair("+1d", 0 to 1),
                                            Pair("+3d", 0 to 3)
                                        ).forEach { (label, delta) ->
                                            Surface(
                                                onClick = {
                                                    val cal = Calendar.getInstance().apply {
                                                        timeInMillis = customTargetMillis.coerceAtLeast(System.currentTimeMillis())
                                                        if (delta.first > 0) add(Calendar.HOUR_OF_DAY, delta.first)
                                                        if (delta.second > 0) add(Calendar.DAY_OF_YEAR, delta.second)
                                                    }
                                                    customTargetMillis = cal.timeInMillis
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                                modifier = Modifier.weight(1f).height(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Summary time badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isAppBangla) "⏰ নির্ধারিত শিডিউল:" else "⏰ Target Time:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "$customDateStr • $customTimeStr ($timeDiffText)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0284C7)
                                            )
                                        }
                                    }
                                }
                            }

                            // Delivery Channel Selector with 3D Icons
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isAppBangla) "ডেলিভারি মাধ্যম:" else "Delivery Channel:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        text = stringResource(R.string.dues_auto_sms_whatsapp_prompt),
                                        fontSize = 11.sp,
                                        color = Color(0xFF0284C7),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DeliveryChannel.values().forEach { chan ->
                                        val isSelected = selectedChannel == chan
                                        Surface(
                                            onClick = { selectedChannel = chan },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color(0xFF0284C7) else Color.White,
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1)),
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                when (chan) {
                                                    DeliveryChannel.WHATSAPP -> Realistic3DWhatsAppIcon(size = 14.dp, animate = isSelected)
                                                    DeliveryChannel.SMS -> Realistic3DSmsSendIcon(size = 14.dp, animate = isSelected)
                                                    DeliveryChannel.BOTH -> Realistic3DMessageReminderIcon(size = 14.dp)
                                                }
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(chan.titleRes),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) Color.White else Color(0xFF0369A1),
                                                    maxLines = 1
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

            // 3. Sticky Bottom Action Footer (Thumb Zone)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (mode == ReminderMode.INSTANT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // WhatsApp Button with 3D Animated WhatsApp Icon
                                Button(
                                    onClick = {
                                        openWhatsAppReminder(context, customer.customerPhone, currentReminderText)
                                        onLogReminder(customer.customerPhone, customer.customerName, customer.outstandingAmount, "WHATSAPP", currentReminderText, "SENT")
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Realistic3DWhatsAppIcon(size = 22.dp, animate = true)
                                    Spacer(Modifier.width(6.dp))
                                    Text("WhatsApp", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }

                                // SMS Button with 3D Animated Supersonic SMS Icon
                                Button(
                                    onClick = {
                                        openPaymentReminder(context, customer.customerPhone, currentReminderText)
                                        onLogReminder(customer.customerPhone, customer.customerName, customer.outstandingAmount, "SMS", currentReminderText, "SENT")
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Realistic3DSmsSendIcon(size = 22.dp, animate = true)
                                    Spacer(Modifier.width(6.dp))
                                    Text("SMS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                }

                                // Share Button with 3D Animated Share Nodes Icon
                                OutlinedButton(
                                    onClick = {
                                        shareReceiptAsText(context, currentReminderText)
                                        onLogReminder(customer.customerPhone, customer.customerName, customer.outstandingAmount, "DIRECT", currentReminderText, "SENT")
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Realistic3DShareNodesIcon(size = 20.dp, animate = true)
                                }
                            }
                        } else {
                            // Auto-Schedule Save Button with 3D Animated Alarm Clock Icon
                            Button(
                                onClick = {
                                    val permissions = buildList {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                            androidx.core.content.ContextCompat.checkSelfPermission(
                                                context, android.Manifest.permission.POST_NOTIFICATIONS
                                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                        ) add(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    if (permissions.isEmpty()) scheduleReminder()
                                    else reminderPermissionLauncher.launch(permissions.toTypedArray())
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Realistic3DAlarmSaveIcon(size = 24.dp, animate = true)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isAppBangla) "শিডিউল রিমাইন্ডার সেট করুন" else "Save Scheduled Reminder",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTemplateManager) {
        ManageDueTemplatesDialog(
            templates = templates,
            onSave = { updatedList ->
                settingsViewModel?.saveDueReminderTemplates(updatedList)
                showTemplateManager = false
            },
            onDismiss = { showTemplateManager = false }
        )
    }
}

@Composable
fun ManageDueTemplatesDialog(
    templates: List<DueTemplate>,
    onSave: (List<DueTemplate>) -> Unit,
    onDismiss: () -> Unit
) {
    var editableList by remember { mutableStateOf(templates.toMutableList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.85f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📝", fontSize = 18.sp)
                        Text(
                            text = stringResource(R.string.dues_manage_templates_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = stringResource(R.string.dues_template_placeholders_hint),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(editableList.size) { index ->
                        val tpl = editableList[index]
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${index + 1} ${tpl.nameEn}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (editableList.size > 1) {
                                        IconButton(
                                            onClick = {
                                                val next = editableList.toMutableList()
                                                next.removeAt(index)
                                                editableList = next
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = tpl.nameBn,
                                        onValueChange = { newBn ->
                                            val next = editableList.toMutableList()
                                            next[index] = tpl.copy(nameBn = newBn)
                                            editableList = next
                                        },
                                        label = { Text(stringResource(R.string.dues_bangla_name_label), fontSize = 11.sp) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = tpl.nameEn,
                                        onValueChange = { newEn ->
                                            val next = editableList.toMutableList()
                                            next[index] = tpl.copy(nameEn = newEn)
                                            editableList = next
                                        },
                                        label = { Text(stringResource(R.string.dues_english_name_label), fontSize = 11.sp) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                OutlinedTextField(
                                    value = tpl.textBn,
                                    onValueChange = { newText ->
                                        val next = editableList.toMutableList()
                                        next[index] = tpl.copy(textBn = newText)
                                        editableList = next
                                    },
                                    label = { Text(stringResource(R.string.dues_bangla_message_text_label), fontSize = 11.sp) },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )

                                OutlinedTextField(
                                    value = tpl.textEn,
                                    onValueChange = { newText ->
                                        val next = editableList.toMutableList()
                                        next[index] = tpl.copy(textEn = newText)
                                        editableList = next
                                    },
                                    label = { Text(stringResource(R.string.dues_english_message_text_label), fontSize = 11.sp) },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val newTpl = DueTemplate(
                                nameBn = "✨ নতুন টেমপ্লেট",
                                nameEn = "✨ Custom Template",
                                textBn = "আসসালামু আলাইকুম {customer}, {store}-এ আপনার বকেয়া {due} পরিশোধের জন্য অনুরোধ রইল। ধন্যবাদ!",
                                textEn = "Dear {customer}, friendly reminder that your balance at {store} is {due}. Thank you!"
                            )
                            editableList = (editableList + newTpl).toMutableList()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dues_add_template_button), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onSave(editableList) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dues_save_apply_button), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesAuditHistorySheet(
    entries: List<DuesAuditEntry>,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var selectedDatePeriod by remember { mutableStateOf(DuePeriodFilter.ALL_TIME) }
    val now = remember { System.currentTimeMillis() }
    val monthStart = remember {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis.let(::startOfDay)
    }

    val (startTime, endTime) = remember(selectedDatePeriod, now, monthStart) {
        when (selectedDatePeriod) {
            DuePeriodFilter.ALL_TIME -> Pair(null, null)
            DuePeriodFilter.TODAY -> Pair(startOfDay(now), endOfDay(now))
            DuePeriodFilter.THIS_WEEK -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }
                Pair(startOfDay(cal.timeInMillis), endOfDay(now))
            }
            DuePeriodFilter.THIS_MONTH -> Pair(monthStart, endOfDay(now))
            else -> Pair(null, null)
        }
    }

    val filteredEntries = remember(entries, searchQuery, selectedTypeFilter, startTime, endTime) {
        val query = searchQuery.trim().lowercase()
        entries.filter { entry ->
            val matchQuery = query.isBlank() ||
                entry.customerName.lowercase().contains(query) ||
                entry.customerPhone.lowercase().contains(query) ||
                (entry.referenceId?.lowercase()?.contains(query) == true) ||
                (entry.note?.lowercase()?.contains(query) == true)

            val matchType = when (selectedTypeFilter) {
                "ALL" -> true
                "REPAYMENT" -> entry.type == DuesAuditType.REPAYMENT
                "CREDIT_SALE" -> entry.type == DuesAuditType.CREDIT_SALE
                "RETURN_ADJUSTMENT" -> entry.type == DuesAuditType.RETURN_ADJUSTMENT
                else -> true
            }

            val matchDate = if (startTime != null && endTime != null) {
                entry.timestamp in startTime..endTime
            } else true

            matchQuery && matchType && matchDate
        }.sortedByDescending { it.timestamp }
    }

    val totalRepaidInView = remember(filteredEntries) {
        filteredEntries.filter { it.type == DuesAuditType.REPAYMENT }.sumOf { it.amount }
    }
    val totalCreditInView = remember(filteredEntries) {
        filteredEntries.filter { it.type == DuesAuditType.CREDIT_SALE }.sumOf { it.amount }
    }
    val totalReturnsInView = remember(filteredEntries) {
        filteredEntries.filter { it.type == DuesAuditType.RETURN_ADJUSTMENT }.sumOf { it.amount }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Realistic3DDigitalKhataIcon(size = 28.dp)
                    Column {
                        Text(
                            stringResource(R.string.dues_audit_trail_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.dues_total_audit_records, filteredEntries.size),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp))
                }
            }

            // Summary Metrics Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(stringResource(R.string.dues_total_repaid_label), fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                    Text(formatTaka(totalRepaidInView), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.dues_credit_given), fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    Text(formatTaka(totalCreditInView), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD97706))
                }
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.dues_return_settled), fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                    Text(formatTaka(totalReturnsInView), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2563EB))
                }
            }

            // Search bar
            PosSearchBox(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholderText = stringResource(R.string.dues_search_audit_placeholder),
                accentColor = PosSectionColors.Dues,
                modifier = Modifier.fillMaxWidth()
            )

            // Filter Chips (Type)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "ALL" to stringResource(R.string.dues_audit_filter_all),
                    "REPAYMENT" to stringResource(R.string.dues_audit_filter_repayments),
                    "CREDIT_SALE" to stringResource(R.string.dues_audit_filter_credit_sales),
                    "RETURN_ADJUSTMENT" to stringResource(R.string.dues_audit_filter_returns)
                ).forEach { (key, label) ->
                    val isSelected = selectedTypeFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTypeFilter = key },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Entries List
            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Realistic3DEmptyReceiptIcon(size = 44.dp)
                        Text(
                            stringResource(R.string.dues_audit_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        DuesAuditCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
fun DuesAuditCard(
    entry: DuesAuditEntry
) {
    val (badgeBg, badgeFg, typeLabel) = when (entry.type) {
        DuesAuditType.REPAYMENT -> Triple(MaterialTheme.extendedColors.successContainer, MaterialTheme.extendedColors.onSuccessContainer, stringResource(R.string.dues_audit_type_repayment))
        DuesAuditType.CREDIT_SALE -> Triple(MaterialTheme.extendedColors.warningContainer, MaterialTheme.extendedColors.onWarningContainer, stringResource(R.string.dues_audit_type_credit_sale))
        DuesAuditType.RETURN_ADJUSTMENT -> Triple(MaterialTheme.extendedColors.infoContainer, MaterialTheme.extendedColors.onInfoContainer, stringResource(R.string.dues_audit_type_return_settlement))
        DuesAuditType.MANUAL_DUE -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, stringResource(R.string.dues_audit_type_manual_due))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg,
                        modifier = Modifier.padding(vertical = 1.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = entry.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = when (entry.type) {
                        DuesAuditType.REPAYMENT -> "+ ${formatTaka(entry.amount)}"
                        DuesAuditType.CREDIT_SALE -> formatTaka(entry.amount)
                        DuesAuditType.RETURN_ADJUSTMENT -> "- ${formatTaka(entry.amount)}"
                        DuesAuditType.MANUAL_DUE -> formatTaka(entry.amount)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = when (entry.type) {
                        DuesAuditType.REPAYMENT -> MaterialTheme.extendedColors.success
                        DuesAuditType.CREDIT_SALE -> MaterialTheme.extendedColors.warning
                        DuesAuditType.RETURN_ADJUSTMENT -> MaterialTheme.colorScheme.primary
                        DuesAuditType.MANUAL_DUE -> MaterialTheme.colorScheme.tertiary
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.customerPhone} • ${entry.channelOrMethod}" + if (!entry.referenceId.isNullOrBlank()) " (${entry.referenceId})" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = DateFormatters.formatDateTime(entry.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (!entry.note.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📝 ${entry.note}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesReminderHubSheet(
    reminders: List<ScheduledDueReminder>,
    logs: List<DueReminderLog>,
    onCancelReminder: (String) -> Unit,
    onSendReminderNow: (ScheduledDueReminder) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onClearAllLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var logIdToDelete by remember { mutableStateOf<Long?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.dues_clear_reminder_logs)) },
            text = { Text(stringResource(R.string.confirm_delete)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllConfirm = false
                        onClearAllLogs()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (logIdToDelete != null) {
        val targetId = logIdToDelete!!
        AlertDialog(
            onDismissRequest = { logIdToDelete = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.confirm_delete_reminder_log_title)) },
            text = { Text(stringResource(R.string.confirm_delete_reminder_log_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteLog(targetId)
                        logIdToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { logIdToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Realistic3DReminderBellIcon(size = 28.dp, animate = reminders.isNotEmpty())
                    Column {
                        Text(
                            stringResource(R.string.dues_reminder_hub_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Scheduled alerts & sent message history",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(18.dp))
                }
            }

            // Segmented Tab Bar
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.dues_reminder_tab_scheduled, reminders.size),
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.dues_reminder_tab_logs, logs.size),
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                // Tab 0: Scheduled Reminders
                if (reminders.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⏰", fontSize = 32.sp)
                            Text(
                                stringResource(R.string.dues_no_scheduled_reminders),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reminders, key = { it.customerPhone }) { reminder ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(reminder.customerName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                            Text(
                                                "${reminder.customerPhone} • ${formatTaka(reminder.dueAmount)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                stringResource(R.string.dues_fires_at_label, DateFormatters.formatShortDateTime(reminder.fireAt)),
                                                fontSize = 11.sp,
                                                color = Color(0xFF0284C7),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = { onSendReminderNow(reminder) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(stringResource(R.string.dues_send_reminder_now), color = Color(0xFF059669), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            }
                                            TextButton(
                                                onClick = { onCancelReminder(reminder.customerPhone) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(stringResource(R.string.dues_cancel), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            }
                                        }
                                    }

                                    if (reminder.messageText.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = reminder.messageText,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: Delivery Logs
                if (logs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showClearAllConfirm = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.dues_clear_reminder_logs), fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📋", fontSize = 32.sp)
                            Text(
                                stringResource(R.string.dues_no_reminder_logs),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = when (log.channel.uppercase()) {
                                                    "WHATSAPP" -> Color(0xFFDCFCE7)
                                                    "SMS" -> Color(0xFFE0E7FF)
                                                    else -> Color(0xFFE0F2FE)
                                                }
                                            ) {
                                                Text(
                                                    text = log.channel,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (log.channel.uppercase()) {
                                                        "WHATSAPP" -> Color(0xFF15803D)
                                                        "SMS" -> Color(0xFF4338CA)
                                                        else -> Color(0xFF0369A1)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                            Text(log.customerName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Text(
                                            "${log.customerPhone} • ${formatTaka(log.dueAmount)} • ${DateFormatters.formatShortDateTime(log.timestamp)}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        if (log.messageText.isNotBlank()) {
                                            Text(
                                                log.messageText,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { logIdToDelete = log.id },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuesFilterBottomSheet(
    onDismiss: () -> Unit,
    currentMinDue: String,
    onApply: (String) -> Unit,
    matchingCount: Int
) {
    var draftMin by remember { mutableStateOf(currentMinDue) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Realistic3DFilterIcon(size = 20.dp)
                    Text(
                        text = stringResource(R.string.dues_filter_debtors_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (draftMin.isNotBlank()) {
                    TextButton(
                        onClick = { draftMin = "" },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.dues_reset_button), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Quick presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.dues_minimum_due_presets_label),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 0.5.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "" to stringResource(R.string.dues_all_amounts_label),
                        "500" to "≥ ${formatMoney(500.0)}",
                        "1000" to "≥ ${formatMoney(1000.0)}",
                        "2000" to "≥ ${formatMoney(2000.0)}",
                        "5000" to "≥ ${formatMoney(5000.0)}"
                    ).forEach { (presetVal, label) ->
                        val isSelected = draftMin == presetVal
                        FilterChip(
                            selected = isSelected,
                            onClick = { draftMin = presetVal },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEEF2FF),
                                selectedLabelColor = Color(0xFF4338CA)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                selectedBorderColor = Color(0xFF818CF8),
                                selectedBorderWidth = 1.dp
                            )
                        )
                    }
                }
            }

            // Custom Min Due input
            OutlinedTextField(
                value = draftMin,
                onValueChange = { draftMin = toEnglishDigits(it).filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text(stringResource(R.string.dues_custom_minimum_due_label)) },
                placeholder = { Text(stringResource(R.string.dues_example_1500_placeholder)) },
                prefix = { Text("${formatMoney(0.0).take(1)} ", fontWeight = FontWeight.Bold) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(stringResource(R.string.dues_cancel), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        onApply(draftMin)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.5f).height(48.dp)
                ) {
                    Text(
                        text = if (draftMin.isBlank()) stringResource(R.string.dues_show_all_debtors_button) else stringResource(R.string.dues_apply_filter_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DuesExecutiveSummaryCard(
    outstanding: Double,
    outstandingCustomers: Int,
    collectedThisMonth: Double,
    totalEverCollected: Double,
    modifier: Modifier = Modifier
) {
    var isCollapsed by rememberSaveable { mutableStateOf(false) }
    var showRateMode by remember { mutableStateOf(false) }
    val totalCredit = outstanding + totalEverCollected
    val recoveryRate = if (totalCredit == 0.0) 0.0 else (totalEverCollected / totalCredit * 100)

    val chevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 180f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    Surface(
        modifier = modifier.animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF4C0519),
        shadowElevation = if (isCollapsed) 2.dp else 5.dp,
        border = BorderStroke(1.dp, Color(0x33FECDD3))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4C0519),
                            Color(0xFF881337),
                            Color(0xFF9F1239)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .padding(horizontal = 16.dp, vertical = if (isCollapsed) 10.dp else 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        Realistic3DHandshakeCreditIcon(size = 18.dp)
                        Text(
                            text = stringResource(R.string.dues_credit_recovery_summary_label),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFFFECDD3)
                        )
                        if (isCollapsed) {
                            Text("•", fontSize = 11.sp, color = Color(0xFFFB7185))
                            Text(
                                text = if (showRateMode) "${String.format(Locale.US, "%.1f", recoveryRate)}%" else formatTaka(outstanding),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { showRateMode = !showRateMode },
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
                                    text = if (showRateMode) stringResource(R.string.dues_outstanding_label) else stringResource(R.string.dues_recovery_rate_label),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = stringResource(R.string.cd_switch_metric),
                                    tint = Color(0xFFFECDD3),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { isCollapsed = !isCollapsed },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                tint = Color(0xFFFECDD3),
                                modifier = Modifier.rotate(chevronRotation).size(18.dp)
                            )
                        }
                    }
                }

                if (!isCollapsed) {
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (showRateMode) stringResource(R.string.dues_credit_recovery_rate_full_label) else stringResource(R.string.dues_total_outstanding_borrowers, outstandingCustomers),
                                fontSize = 11.sp,
                                color = Color(0xFFFDA4AF),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (showRateMode) "${String.format(Locale.US, "%.1f", recoveryRate)}%" else formatTaka(outstanding),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (showRateMode) Color(0xFF34D399) else Color.White,
                                letterSpacing = (-0.5).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = stringResource(R.string.dues_collected_this_month_label),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFDA4AF),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = "+${formatTaka(collectedThisMonth)}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val animatedProgress by animateFloatAsState(
                        targetValue = (recoveryRate / 100f).toFloat().coerceIn(0f, 1f),
                        animationSpec = tween(1000, easing = FastOutSlowInEasing),
                        label = "progressAnim"
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().height(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF34D399), Color(0xFF10B981))
                                    )
                                )
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.dues_total_credit_label), fontSize = 11.sp, color = Color(0xFFFECDD3), fontWeight = FontWeight.Bold)
                            Text(formatTaka(totalCredit), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.dues_total_repaid_label), fontSize = 11.sp, color = Color(0xFFFECDD3), fontWeight = FontWeight.Bold)
                            Text(formatTaka(totalEverCollected), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.dues_recovery_percent_label), fontSize = 11.sp, color = Color(0xFFFECDD3), fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.US, "%.1f", recoveryRate)}%", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDueRow(
    customer: CustomerDue,
    repaidAmount: Double,
    isOverdue: Boolean = false,
    canCollect: Boolean = true,
    canDelete: Boolean = true,
    onClick: () -> Unit,
    onCollect: () -> Unit,
    onWhatsApp: () -> Unit = {},
    onRemind: () -> Unit = {},
    onCall: () -> Unit = {},
    onEditDue: () -> Unit = {},
    onDeleteDue: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isOverdue) Color(0xFFF59E0B).copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (customer.outstandingAmount > 0) Color(0xFFF43F5E) else Color(0xFF10B981),
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = customer.customerName.firstOrNull()?.uppercase() ?: "?",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = customer.customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isOverdue) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFEF3C7),
                                border = BorderStroke(0.5.dp, Color(0xFFF59E0B))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Realistic3DOverdueAlertIcon(size = 10.dp)
                                    Text(stringResource(R.string.dues_overdue_badge), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                }
                            }
                        }
                    }

                    val updated = remember(customer.updatedAt) {
                        DateFormatters.formatShortDate(customer.updatedAt)
                    }
                    Text(
                        text = "📞 ${customer.customerPhone} • $updated",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatTaka(customer.outstandingAmount),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = if (isOverdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.error
                    )
                    if (isOverdue) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = stringResource(R.string.dues_overdue_badge),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (repaidAmount > 0) {
                        Text(
                            text = stringResource(R.string.dues_repaid_amount_label, formatTaka(repaidAmount)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (customer.outstandingAmount > 0) {
                    if (canCollect) {
                        Button(
                            onClick = onCollect,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.weight(1.05f).height(48.dp)
                        ) {
                            Realistic3DWalletCollectIcon(size = 13.dp)
                            Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.dues_collect_button), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }

                    Button(
                        onClick = onWhatsApp,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                        modifier = Modifier.weight(1.35f).height(48.dp)
                    ) {
                        Realistic3DWhatsAppIcon(size = 13.dp, animate = false)
                        Spacer(Modifier.width(3.dp))
                        Text("WhatsApp", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA7F3D0), maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = onRemind,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                        modifier = Modifier.weight(0.90f).height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(stringResource(R.string.dues_remind_button), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = onCall,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 0.dp),
                        modifier = Modifier.weight(0.75f).height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(stringResource(R.string.dues_call_button), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // 3-dots more options menu
                var showCardMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showCardMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.cd_options),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    DropdownMenu(
                        expanded = showCardMenu,
                        onDismissRequest = { showCardMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dues_edit_customer_info), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showCardMenu = false
                                onEditDue()
                            }
                        )
                        if (canDelete) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.dues_delete_archive_due), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showCardMenu = false
                                    onDeleteDue()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddDueDialog(
    initialName: String = "",
    initialPhone: String = "",
    initialDue: Double = 0.0,
    initialCreditLimit: Double = 0.0,
    canOverrideLimit: Boolean = false,
    onDismiss: () -> Unit,
    onAddDue: (String, String, Double, Double, String?, (Boolean) -> Unit) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }
    var amount by remember { mutableStateOf("") }
    var creditLimit by remember(initialCreditLimit) {
        mutableStateOf(if (initialCreditLimit > 0.0) initialCreditLimit.toInt().toString() else "")
    }
    var note by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isPresetCustomer = initialPhone.isNotBlank()
    val errorEnterCustomerName = stringResource(R.string.dues_error_enter_customer_name)
    val errorInvalidPhone = stringResource(R.string.dues_error_invalid_phone)
    val errorInvalidDueAmount = stringResource(R.string.dues_error_invalid_due_amount)
    val errorCreditLimitExceeded = stringResource(R.string.error_credit_limit_exceeded_auth)

    val addedDue = amount.toDoubleOrNull() ?: 0.0
    val totalUpdatedDue = initialDue + addedDue
    val limitVal = creditLimit.toDoubleOrNull() ?: initialCreditLimit
    val isOverLimit = limitVal > 0.0 && totalUpdatedDue > limitVal
    val limitRatio = if (limitVal > 0.0) (totalUpdatedDue / limitVal).toFloat().coerceIn(0f, 1f) else 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Header with 3D Add Credit Khata & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Realistic3DAddCreditKhataIcon(size = 24.dp)
                        Column {
                            Text(
                                text = if (isPresetCustomer) stringResource(R.string.dues_add_due_to_customer_title) else stringResource(R.string.dues_add_customer_due_button),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.dues_digital_khata_subtitle),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFD97706),
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Customer Capsule (Preset) OR 2-Column Inputs (New Customer)
                if (isPresetCustomer) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            brush = Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))),
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = name.take(1).uppercase().ifBlank { "C" },
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = phone,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.dues_current_due_label),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline,
                                    letterSpacing = 0.3.sp
                                )
                                Text(
                                    text = formatTaka(initialDue),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = Color(0xFFD97706),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Customer Name
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dues_customer_name_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.outline,
                                letterSpacing = 0.3.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (name.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.dues_customer_name_hint),
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                            )
                                        }
                                        BasicTextField(
                                            value = name,
                                            onValueChange = { name = it; errorMessage = null },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        // Phone Number
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dues_phone_number_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.outline,
                                letterSpacing = 0.3.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (phone.isEmpty()) {
                                             Text(
                                                text = "017...",
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                            )
                                        }
                                        BasicTextField(
                                            value = phone,
                                            onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' }; errorMessage = null },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Hero Due Amount Input Box with 1-Tap Preset Chips
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.dues_due_amount_header, formatMoney(0.0).take(1)),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706),
                                letterSpacing = 0.4.sp
                            )
                            Text(
                                text = stringResource(R.string.dues_enter_or_tap_preset),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // Compact Sleek Amount Input Container (40dp height)
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, Color(0xFFF59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formatMoney(0.0).take(1),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFD97706)
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (amount.isEmpty()) {
                                        Text(
                                            text = "0",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    BasicTextField(
                                        value = amount,
                                        onValueChange = {
                                            amount = toEnglishDigits(it).filter { ch -> ch.isDigit() || ch == '.' }
                                            errorMessage = null
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (amount.isNotEmpty()) {
                                    IconButton(
                                        onClick = { amount = ""; errorMessage = null },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = stringResource(R.string.cd_clear),
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Increment Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(500, 1000, 2000, 5000).forEach { chipVal ->
                                Surface(
                                    onClick = {
                                        val currentVal = amount.toDoubleOrNull() ?: 0.0
                                        val newVal = (currentVal + chipVal).toInt()
                                        amount = newVal.toString()
                                        errorMessage = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "+$chipVal",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                            Surface(
                                onClick = {
                                    amount = ""
                                    errorMessage = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF43F5E).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.3f)),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text(
                                    text = stringResource(R.string.cd_clear),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFF43F5E),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 4. Real-time Live Calculation Matrix & Credit Limit Progress
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.dues_previous_due), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
                            Text(formatTaka(initialDue), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.dues_new_unpaid_credit), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
                            Text("+${formatTaka(addedDue)}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706), fontFamily = FontFamily.Monospace)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.8.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.dues_updated_outstanding), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(formatTaka(totalUpdatedDue), fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (isOverLimit) Color(0xFFF43F5E) else Color(0xFFD97706), fontFamily = FontFamily.Monospace)
                        }

                        if (limitVal > 0.0) {
                            Spacer(Modifier.height(1.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.dues_credit_limit_val_fmt, formatTaka(limitVal)),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = if (isOverLimit) stringResource(R.string.dues_limit_exceeded) else stringResource(R.string.dues_limit_pct_used, (totalUpdatedDue / limitVal * 100).toInt()),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOverLimit) Color(0xFFF43F5E) else if (limitRatio >= 0.8f) Color(0xFFF59E0B) else Color(0xFF10B981)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { limitRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (isOverLimit) Color(0xFFF43F5E) else if (limitRatio >= 0.8f) Color(0xFFF59E0B) else Color(0xFF10B981),
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // 5. Optional Reference Note & Credit Limit Input (Compact 38dp height)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Note / Memo
                    Column(
                        modifier = Modifier.weight(1.1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dues_note_memo_label),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 0.3.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("📝", fontSize = 11.sp)
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (note.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.dues_memo_hint),
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                        )
                                    }
                                    BasicTextField(
                                        value = note,
                                        onValueChange = { note = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Credit Limit
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dues_credit_limit_header, formatMoney(0.0).take(1)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 0.3.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(formatMoney(0.0).take(1), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (creditLimit.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.dues_no_limit),
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                        )
                                    }
                                    BasicTextField(
                                        value = creditLimit,
                                        onValueChange = { creditLimit = toEnglishDigits(it).filter { ch -> ch.isDigit() || ch == '.' } },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 6. Action Buttons Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dues_cancel),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        enabled = !isSaving,
                        onClick = {
                            val cleanName = name.trim()
                            val cleanPhone = phone.trim()
                            val dueVal = amount.toDoubleOrNull() ?: 0.0
                            val limitValFinal = creditLimit.toDoubleOrNull() ?: 0.0

                            if (cleanName.isBlank()) {
                                errorMessage = errorEnterCustomerName
                            } else if (cleanPhone.length < 5) {
                                errorMessage = errorInvalidPhone
                            } else if (dueVal <= 0.0) {
                                errorMessage = errorInvalidDueAmount
                            } else if (isOverLimit && !canOverrideLimit) {
                                errorMessage = errorCreditLimitExceeded
                            } else {
                                isSaving = true
                                onAddDue(cleanName, cleanPhone, dueVal, limitValFinal, note.trim().takeIf { it.isNotBlank() }) { success ->
                                    if (!success) isSaving = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(4.dp))
                            } else {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = if (isSaving) stringResource(R.string.dues_saving_ellipsis)
                                       else if (addedDue > 0.0) "Save Due (${formatTaka(totalUpdatedDue)})"
                                       else stringResource(R.string.dues_save_due_record_button),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentHistoryList(
    payments: List<DuePayment>,
    customerByPhone: Map<String, CustomerDue>,
    modifier: Modifier = Modifier
) {
    if (payments.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.dues_no_payments_in_range), color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(payments, key = { it.id }) { payment ->
                val customer = customerByPhone[payment.customerPhone]
                PaymentHistoryRow(payment = payment, customer = customer)
            }
        }
    }
}

@Composable
private fun PaymentHistoryRow(
    payment: DuePayment,
    customer: CustomerDue?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFECFDF5),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Realistic3DWalletCollectIcon(size = 18.dp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer?.customerName ?: payment.customerPhone,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Text(
                    text = "${payment.paymentMethod} • ${DateFormatters.formatDateTime(payment.timestamp)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                payment.referenceNote?.takeIf { it.isNotBlank() }?.let { ref ->
                    Text(
                        text = stringResource(R.string.dues_ref_label, ref),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = "+${formatTaka(payment.amountPaid)}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.5.sp,
                color = Color(0xFF059669)
            )
        }
    }
}

/** A single row in the merged Transaction History - either a collection (credit) or a charge
 * (debit, from a Due sale or a manual Add Due entry) - sortable together by timestamp. */
private sealed class DueLedgerRow(val timestamp: Long) {
    class Payment(val payment: DuePayment) : DueLedgerRow(payment.timestamp)
    class Charge(val charge: com.example.techyfipos.data.db.DueCharge) : DueLedgerRow(charge.timestamp)
}

@Composable
fun CustomerDueDetailsView(
    customer: CustomerDue,
    payments: List<DuePayment>,
    charges: List<com.example.techyfipos.data.db.DueCharge> = emptyList(),
    canMutate: Boolean = true,
    canCollect: Boolean = canMutate,
    canAddDue: Boolean = canMutate,
    onClose: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    onAddDueClick: () -> Unit,
    onRepaymentClick: ((DuePayment) -> Unit)? = null,
    onDeletePaymentClick: ((DuePayment) -> Unit)? = null,
    onDeleteChargeClick: ((com.example.techyfipos.data.db.DueCharge) -> Unit)? = null,
    onShareStatement: () -> Unit,
    onSetCreditLimit: (Double) -> Unit,
    onEditClick: (() -> Unit)? = null,
    onDeleteClick: () -> Unit,
    onCall: () -> Unit,
    onRemind: () -> Unit,
    onViewDossier: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val totalRepaid = payments.sumOf { it.amountPaid }
    val ledgerEntries = remember(payments, charges) {
        (payments.map { DueLedgerRow.Payment(it) } + charges.map { DueLedgerRow.Charge(it) })
            .sortedByDescending { it.timestamp }
    }
    var showSetCreditLimitDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // Account Statement Hero Card (Midnight Indigo Gradient)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1B4B),
            border = BorderStroke(1.dp, Color(0x33C7D2FE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF3730A3)),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Realistic3DDigitalKhataIcon(size = 18.dp)
                            Text(stringResource(R.string.dues_account_statement_label), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC7D2FE), letterSpacing = 0.5.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = "📞 ${customer.customerPhone}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFA5B4FC),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(stringResource(R.string.dues_outstanding_balance_label), fontSize = 11.sp, color = Color(0xFFC7D2FE))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatTaka(customer.outstandingAmount),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (customer.outstandingAmount > 0) Color(0xFFFB7185) else Color(0xFF34D399),
                                letterSpacing = (-0.5).sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (customer.outstandingAmount > 0) Color(0xFF9F1239).copy(alpha = 0.6f) else Color(0xFF065F46).copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, if (customer.outstandingAmount > 0) Color(0xFFF43F5E) else Color(0xFF10B981))
                        ) {
                            Text(
                                text = if (customer.outstandingAmount > 0) stringResource(R.string.dues_due_active_badge) else stringResource(R.string.dues_fully_settled_badge),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dues_total_repaid_with_amount, formatTaka(totalRepaid)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC7D2FE)
                        )
                        if (customer.creditLimit > 0.0) {
                            val pct = ((customer.outstandingAmount / customer.creditLimit) * 100).coerceIn(0.0, 100.0)
                            val isBn = com.example.techyfipos.utils.isAppBangla()
                            val pctStr = if (isBn) com.example.techyfipos.utils.toBanglaDigits(String.format(Locale.US, "%.0f", pct)) else String.format(Locale.US, "%.0f", pct)
                            Text(
                                text = stringResource(R.string.dues_limit_percent_detail, formatTaka(customer.creditLimit), pctStr),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pct >= 80) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
                            )
                        }
                    }
                }
            }
        }

        // Primary Action Grid (4 balanced, single-line buttons)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canCollect) Button(
                onClick = onRecordPaymentClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                modifier = Modifier.weight(1.15f).height(48.dp)
            ) {
                Realistic3DWalletCollectIcon(size = 15.dp)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.dues_collect_button), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
            }

            if (canAddDue) Button(
                onClick = onAddDueClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                modifier = Modifier.weight(1.15f).height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.dues_add_due_button), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = onRemind,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.dues_remind_button), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }

            OutlinedButton(
                onClick = onCall,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                modifier = Modifier.weight(0.9f).height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = Color(0xFF059669),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.dues_call_button), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }

        // Secondary Utility Strip: Statement Share, Dossier, Edit Info & Credit Limit
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Statement Share & Customer Profile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onShareStatement,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dues_share_statement_button),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (onViewDossier != null) {
                        FilledTonalButton(
                            onClick = onViewDossier,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.dues_customer_profile_button),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Row 2: Edit Info & Set / Adjust Limit
                if (canMutate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onEditClick != null) {
                            FilledTonalButton(
                                onClick = onEditClick,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.dues_edit_info_button),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showSetCreditLimitDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (customer.creditLimit > 0.0) stringResource(R.string.dues_limit_amount_label, formatTaka(customer.creditLimit)) else stringResource(R.string.dues_set_limit_button),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Transaction History Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.dues_transaction_history_label, ledgerEntries.size), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    if (payments.isNotEmpty()) {
                        Text(stringResource(R.string.dues_total_with_amount, formatTaka(totalRepaid)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                }

                if (ledgerEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dues_no_payments_for_customer),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    ledgerEntries.forEach { entry ->
                        when (entry) {
                            is DueLedgerRow.Payment -> {
                                val payment = entry.payment
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    onClick = { onRepaymentClick?.invoke(payment) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
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
                                                    Realistic3DWalletCollectIcon(size = 14.dp)
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.dues_payment_method_suffix, payment.paymentMethod),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = DateFormatters.formatDateTime(payment.timestamp),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                payment.referenceNote?.takeIf { it.isNotBlank() }?.let { ref ->
                                                    Text(stringResource(R.string.dues_ref_label, ref), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "+${formatTaka(payment.amountPaid)}",
                                                color = Color(0xFF059669),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.5.sp
                                            )
                                            if (canMutate && onDeletePaymentClick != null) {
                                                IconButton(
                                                    onClick = { onDeletePaymentClick(payment) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = stringResource(R.string.dues_delete_payment_button),
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = stringResource(R.string.cd_view_receipt),
                                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            is DueLedgerRow.Charge -> {
                                val charge = entry.charge
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
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
                                                text = stringResource(
                                                    if (charge.reason == "Sale") R.string.dues_charge_reason_sale
                                                    else R.string.dues_charge_reason_manual
                                                ),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = DateFormatters.formatDateTime(charge.timestamp),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            charge.note?.takeIf { it.isNotBlank() }?.let { n ->
                                                Text(stringResource(R.string.dues_ref_label, n), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "-${formatTaka(charge.amount)}",
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.5.sp
                                        )
                                        if (canMutate && onDeleteChargeClick != null) {
                                            IconButton(
                                                onClick = { onDeleteChargeClick(charge) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.dues_delete_charge_button),
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                                    modifier = Modifier.size(18.dp)
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

        if (canMutate) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dues_delete_archive_account), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(R.string.dues_back_to_dues_list_button), fontWeight = FontWeight.Bold)
        }
    }
    }

    if (canMutate && showSetCreditLimitDialog) {
        SetCreditLimitDialog(
            customer = customer,
            onDismiss = { showSetCreditLimitDialog = false },
            onConfirm = { limit ->
                onSetCreditLimit(limit)
                showSetCreditLimitDialog = false
            }
        )
    }
}

@Composable
fun RecordPaymentDialog(
    customer: CustomerDue,
    isAppBangla: Boolean = false,
    onDismiss: () -> Unit,
    onRecordPayment: (Double, String, String?, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current

    var amount by remember(customer.customerPhone) {
        val amt = customer.outstandingAmount
        mutableStateOf(if (amt % 1.0 == 0.0) amt.toLong().toString() else amt.toString())
    }
    var method by remember { mutableStateOf("Cash") }
    var reference by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val paidDouble = amount.toDoubleOrNull() ?: 0.0
    val isOverpaid = paidDouble > customer.outstandingAmount
    val isFullSettlement = paidDouble == customer.outstandingAmount && customer.outstandingAmount > 0
    val isPartial = paidDouble > 0 && paidDouble < customer.outstandingAmount
    val remainingDue = (customer.outstandingAmount - paidDouble).coerceAtLeast(0.0)
    val isValid = paidDouble > 0 && !isOverpaid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        com.example.techyfipos.LocaleWrapper(language = if (isAppBangla) "bn" else "en") {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 560.dp)
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    // 1. Header: 3D Wallet Icon + Title + Subtitle + Close Button (100% strict to App Language)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Realistic3DWalletCollectIcon(size = 32.dp)
                            Column {
                                Text(
                                    text = stringResource(R.string.dues_collect_payment_title),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.dues_voucher_subtitle),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 2. Customer & Outstanding Balance Hero Card (Spacious 16dp radius)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFFF43F5E), Color(0xFFBE123C))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = customer.customerName.take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = customer.customerName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = customer.customerPhone,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.dues_unpaid_balance_label),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp,
                                    color = Color(0xFFE11D48)
                                )
                                Text(
                                    text = formatTaka(customer.outstandingAmount),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFE11D48)
                                )
                            }
                        }
                    }

                    // 3. Amount Received Input & Status Badge
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.dues_amount_received_label),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            // Status Badge
                            val (badgeText, badgeBg, badgeFg) = when {
                                isOverpaid -> Triple(
                                    stringResource(R.string.dues_invalid_amount_badge),
                                    Color(0x25EF4444),
                                    Color(0xFFEF4444)
                                )
                                isFullSettlement -> Triple(
                                    stringResource(R.string.dues_full_settlement_badge),
                                    Color(0x2510B981),
                                    Color(0xFF059669)
                                )
                                isPartial -> Triple(
                                    stringResource(R.string.dues_partial_repayment_badge),
                                    Color(0x25F59E0B),
                                    Color(0xFFD97706)
                                )
                                else -> Triple(
                                    stringResource(R.string.dues_amount_received_label),
                                    Color(0x156B7280),
                                    MaterialTheme.colorScheme.outline
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeBg,
                                border = BorderStroke(0.9.dp, badgeFg.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // High Visibility Hero Amount Input Container (54dp height)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = 2.dp,
                                color = if (isOverpaid) Color(0xFFEF4444) else Color(0xFF059669)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatMoney(0.0).take(1),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isOverpaid) Color(0xFFEF4444) else Color(0xFF059669),
                                    fontSize = 22.sp
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (amount.isEmpty()) {
                                        Text(
                                            text = "0",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    BasicTextField(
                                        value = amount,
                                        onValueChange = { amount = sanitizeNumericInput(it) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (amount.isNotBlank() && amount != "0") {
                                    IconButton(
                                        onClick = { amount = "0" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = stringResource(R.string.cd_clear),
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Quick Percentage Preset Chips
                    if (customer.outstandingAmount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0.25 to (if (isAppBangla) "২৫%" else "25%"),
                                0.50 to (if (isAppBangla) "৫০%" else "50%"),
                                0.75 to (if (isAppBangla) "৭৫%" else "75%"),
                                1.00 to (if (isAppBangla) "১০০%" else "100%")
                            ).forEach { (pct, label) ->
                                val chipVal = if (pct == 1.0) customer.outstandingAmount else (customer.outstandingAmount * pct).toInt().toDouble()
                                val isSelected = (paidDouble == chipVal)
                                val isFull = (pct == 1.0)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        isSelected && isFull -> Color(0xFF059669)
                                        isSelected -> Color(0x25059669)
                                        isFull -> Color(0x15059669)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.4.dp else 0.9.dp,
                                        color = when {
                                            isSelected -> Color(0xFF059669)
                                            isFull -> Color(0xFF059669).copy(alpha = 0.5f)
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            amount = if (chipVal % 1.0 == 0.0) chipVal.toLong().toString() else chipVal.toString()
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected && isFull) Color.White else if (isFull || isSelected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = formatTaka(chipVal),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected && isFull) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Quick Cash Add-on Shortcut Row (+100, +500, +1,000, +2,000)
                    if (customer.outstandingAmount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dues_quick_add_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                            listOf(
                                100.0 to (if (isAppBangla) "+১০০" else "+100"),
                                500.0 to (if (isAppBangla) "+৫০০" else "+500"),
                                1000.0 to (if (isAppBangla) "+১০০০" else "+1000"),
                                2000.0 to (if (isAppBangla) "+২০০০" else "+2000")
                            ).forEach { (delta, btnText) ->
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val current = amount.toDoubleOrNull() ?: 0.0
                                            val newAmt = (current + delta).coerceAtMost(customer.outstandingAmount)
                                            amount = if (newAmt % 1.0 == 0.0) newAmt.toLong().toString() else newAmt.toString()
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = btnText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Live Dynamic Remaining Balance Preview Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isOverpaid -> Color(0x15EF4444)
                            isFullSettlement -> Color(0x1810B981)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = when {
                                isOverpaid -> Color(0xFFEF4444).copy(alpha = 0.5f)
                                isFullSettlement -> Color(0xFF10B981).copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when {
                                        isOverpaid -> "⚠️"
                                        isFullSettlement -> "✨"
                                        else -> "⏳"
                                    },
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = when {
                                        isOverpaid -> stringResource(R.string.dues_overpaid_by_error, formatTaka(paidDouble - customer.outstandingAmount))
                                        isFullSettlement -> stringResource(R.string.dues_balance_fully_settled)
                                        else -> stringResource(R.string.dues_partial_repayment_badge)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isOverpaid -> Color(0xFFEF4444)
                                        isFullSettlement -> Color(0xFF059669)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            Text(
                                text = if (isOverpaid) {
                                    "+${formatTaka(paidDouble - customer.outstandingAmount)}"
                                } else {
                                    stringResource(R.string.dues_remaining_due_preview, formatTaka(remainingDue))
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    isOverpaid -> Color(0xFFEF4444)
                                    isFullSettlement -> Color(0xFF059669)
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                        }
                    }

                    // 7. Branded Payment Channels Grid (54dp Ergonomic Height)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.dues_payment_channel_label),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val channels = listOf(
                                PaymentChannelOption("Cash", R.string.dues_channel_cash, Color(0xFF10B981)) { Realistic3DCashBillIcon(size = 20.dp) },
                                PaymentChannelOption("bKash", R.string.dues_channel_bkash, Color(0xFFE2136E)) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color.White,
                                        shadowElevation = 0.5.dp,
                                        modifier = Modifier.size(width = 28.dp, height = 18.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.logo_bkash),
                                            contentDescription = stringResource(R.string.dues_channel_bkash),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 0.5.dp)
                                        )
                                    }
                                },
                                PaymentChannelOption("Nagad", R.string.dues_channel_nagad, Color(0xFFF7941D)) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color.White,
                                        shadowElevation = 0.5.dp,
                                        modifier = Modifier.size(width = 28.dp, height = 18.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.logo_nagad),
                                            contentDescription = stringResource(R.string.dues_channel_nagad),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 0.5.dp)
                                        )
                                    }
                                },
                                PaymentChannelOption("Rocket", R.string.dues_channel_rocket, Color(0xFF8C3494)) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color(0xFF8C3494),
                                        shadowElevation = 0.5.dp,
                                        modifier = Modifier.size(width = 28.dp, height = 18.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(R.drawable.logo_rocket),
                                            contentDescription = stringResource(R.string.dues_channel_rocket),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.padding(horizontal = 1.dp, vertical = 0.5.dp)
                                        )
                                    }
                                },
                                PaymentChannelOption("Card", R.string.dues_channel_card, Color(0xFF6366F1)) { Realistic3DCardIcon(size = 20.dp) },
                                PaymentChannelOption("Bank", R.string.dues_channel_bank, Color(0xFF3B82F6)) { Realistic3DBankVaultIcon(size = 20.dp) }
                            )

                            channels.forEach { ch ->
                                val isSelected = (method == ch.id)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) ch.brandColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.8.dp else 0.9.dp,
                                        color = if (isSelected) ch.brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .clickable { method = ch.id }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 5.dp, horizontal = 1.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        ch.iconComposable()
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = stringResource(ch.labelRes),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                            color = if (isSelected) ch.brandColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 8. Reference / Note Input (44dp Height)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📝", fontSize = 13.sp)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (reference.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.dues_reference_note_label),
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                }
                                BasicTextField(
                                    value = reference,
                                    onValueChange = { reference = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 9. Ergonomic Dual Action Buttons (48dp Height)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dues_cancel),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }

                        Button(
                            enabled = !isSaving && isValid,
                            onClick = {
                                if (!isValid) return@Button
                                isSaving = true
                                onRecordPayment(
                                    paidDouble,
                                    method,
                                    reference.takeIf { it.isNotBlank() }
                                ) { success ->
                                    if (!success) isSaving = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier
                                .weight(1.6f)
                                .height(48.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.dues_saving_ellipsis),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isValid) {
                                        stringResource(R.string.dues_save_payment_amount_button, formatTaka(paidDouble))
                                    } else {
                                        stringResource(R.string.dues_save_payment_button)
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PaymentChannelOption(
    val id: String,
    val labelRes: Int,
    val brandColor: Color,
    val iconComposable: @Composable () -> Unit
)

data class DueRepaymentReceiptData(
    val customerName: String,
    val customerPhone: String,
    val amountPaid: Double,
    val paymentMethod: String,
    val referenceNote: String?,
    val remainingDue: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isReprint: Boolean = false
)

@Composable
fun DueRepaymentReceiptDialog(
    data: DueRepaymentReceiptData,
    storeName: String,
    storeAddress: String,
    storePhone: String,
    settings: InvoiceSettingsData = InvoiceSettingsData(),
    isAppBangla: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var selectedRollWidth by rememberSaveable { mutableIntStateOf(settings.paperRollWidthMm.takeIf { it == 58 || it == 80 } ?: 80) }
    var zoomScale by rememberSaveable { mutableFloatStateOf(1f) }
    var isReceiptBangla by remember { mutableStateOf(isAppBangla) }
    var receiptFile by remember { mutableStateOf<java.io.File?>(null) }
    var bitmapPreview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(data, isReceiptBangla, settings, selectedRollWidth) {
        try {
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                generateDueRepaymentJpeg(
                    context = context,
                    customerName = data.customerName,
                    customerPhone = data.customerPhone,
                    amountPaid = data.amountPaid,
                    paymentMethod = data.paymentMethod,
                    referenceNote = data.referenceNote,
                    remainingDue = data.remainingDue,
                    storeName = storeName,
                    storeAddress = storeAddress,
                    storePhone = storePhone,
                    isBangla = isReceiptBangla,
                    settings = settings.copy(paperRollWidthMm = selectedRollWidth),
                    timestamp = data.timestamp,
                    isReprint = data.isReprint
                )
            }
            receiptFile = file
            val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                BitmapFactory.decodeFile(file.absolutePath)
            }
            bitmapPreview = bmp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val isSettled = data.remainingDue <= 0.001
    var isPrintingReceipt by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2200)
            isCopied = false
        }
    }

    val receiptText = remember(data, storeName, isReceiptBangla, settings) {
        buildString {
            if (isReceiptBangla) {
                appendLine("★ ${storeName.ifBlank { "TECHYFI STORE" }} ★")
                appendLine(if (data.isReprint) "--- টাকা জমার রশিদ (পুনঃমুদ্রণ) ---" else "--- টাকা জমার রশিদ • বাকি আদায় ---")
                appendLine("তারিখ: ${DateFormatters.formatLongDateTime(data.timestamp, java.util.Locale.forLanguageTag("bn-BD"))}")
                appendLine("ভাউচার নং: REC-${data.timestamp % 1000000}")
                appendLine("কাস্টমার: ${data.customerName}")
                appendLine("মোবাইল: ${data.customerPhone}")
                data.referenceNote?.takeIf { it.isNotBlank() }?.let { appendLine("রেফারেন্স: $it") }
                if (data.isReprint) {
                    appendLine("জমা করা হয়েছে: ${formatTakaBn(data.amountPaid)} (${if (data.paymentMethod.equals("Cash", ignoreCase = true)) "নগদ" else data.paymentMethod})")
                    appendLine("বর্তমান বাকি: ${formatTakaBn(data.remainingDue)}")
                } else {
                    appendLine("আগের বাকি: ${formatTakaBn(data.amountPaid + data.remainingDue)}")
                    appendLine("আজকের জমা: ${formatTakaBn(data.amountPaid)} (${if (data.paymentMethod.equals("Cash", ignoreCase = true)) "নগদ" else data.paymentMethod})")
                    appendLine("অবশিষ্ট বাকি: ${if (isSettled) "${formatTakaBn(0.0)} (সম্পূর্ণ পরিশোধ ✓)" else formatTakaBn(data.remainingDue)}")
                }
                appendLine(if (data.isReprint) "পরিশোধের রসিদ (পুনঃমুদ্রণ)" else if (isSettled) "✔ বাকি সম্পূর্ণ পরিশোধ হয়েছে" else "⏳ আংশিক জমা নেওয়া হলো")
                if (settings.showDueReceiptCustomNotes && settings.dueReceiptCustomNotes.isNotBlank()) {
                    appendLine("নোট: ${settings.dueReceiptCustomNotes}")
                }
                appendLine("ধন্যবাদ, আবার আসবেন!")
            } else {
                appendLine("★ ${storeName.ifBlank { "TECHYFI STORE" }} ★")
                appendLine(if (data.isReprint) "--- MONEY RECEIPT (REPRINT) ---" else "--- MONEY RECEIPT / DUE PAYMENT ---")
                appendLine("Date: ${DateFormatters.formatDateTime(data.timestamp, Locale.US)}")
                appendLine("Voucher #: REC-${data.timestamp % 1000000}")
                appendLine("Customer: ${data.customerName}")
                appendLine("Phone: ${data.customerPhone}")
                data.referenceNote?.takeIf { it.isNotBlank() }?.let { appendLine("Ref: $it") }
                if (data.isReprint) {
                    appendLine("Amount Paid: ${formatTaka(data.amountPaid)} (${data.paymentMethod})")
                    appendLine("Current Due: ${formatTaka(data.remainingDue)}")
                } else {
                    appendLine("Previous Due: ${formatTaka(data.amountPaid + data.remainingDue)}")
                    appendLine("Amount Paid: ${formatTaka(data.amountPaid)} (${data.paymentMethod})")
                    appendLine("Remaining Due: ${if (isSettled) "${formatTaka(0.0)} (Fully Settled ✓)" else formatTaka(data.remainingDue)}")
                }
                appendLine(if (data.isReprint) "RECORDED PAYMENT (REPRINT)" else if (isSettled) "✔ PAID IN FULL & SETTLED" else "⏳ PARTIAL PAYMENT RECEIVED")
                if (settings.showDueReceiptCustomNotes && settings.dueReceiptCustomNotes.isNotBlank()) {
                    appendLine("Note: ${settings.dueReceiptCustomNotes}")
                }
                appendLine("Thank you for your payment!")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        com.example.techyfipos.LocaleWrapper(language = if (isAppBangla) "bn" else "en") {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 560.dp)
                    .wrapContentHeight()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Header with 3D Seal, Bilingual Toggle, and Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Realistic3DRepaymentSealIcon(size = 24.dp)
                            Column {
                                Text(
                                    text = if (isAppBangla) "টাকা জমার রশিদ" else "Money Receipt",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = (-0.2).sp
                                )
                                Text(
                                    text = if (isAppBangla) "বাকি পরিশোধের ভাউচার" else "DUE REPAYMENT VOUCHER",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    letterSpacing = 0.4.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Sleek Segmented Language Toggle (EN / বাং) - ONLY changes receipt language!
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(2.5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { isReceiptBangla = false },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (!isReceiptBangla) Color(0xFF10B981) else Color.Transparent,
                                        shadowElevation = if (!isReceiptBangla) 2.dp else 0.dp
                                    ) {
                                        Text(
                                            text = "EN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (!isReceiptBangla) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = { isReceiptBangla = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isReceiptBangla) Color(0xFF10B981) else Color.Transparent,
                                        shadowElevation = if (isReceiptBangla) 2.dp else 0.dp
                                    ) {
                                        Text(
                                            text = "বাং",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isReceiptBangla) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // Frosted Glass Close Button
                            Surface(
                                onClick = onDismiss,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.close),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Instant Voucher Summary Micro-Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dues_repaid_label),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                                Text(
                                    text = if (isAppBangla) "${formatTakaBn(data.amountPaid)} (${if (data.paymentMethod.equals("Cash", ignoreCase = true)) stringResource(R.string.cash) else data.paymentMethod})" else "${formatTaka(data.amountPaid)} (${data.paymentMethod})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF065F46)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981)
                            ) {
                                Text(
                                    text = if (data.isReprint) {
                                        stringResource(R.string.dues_receipt_reprint_badge)
                                    } else if (isSettled) {
                                        stringResource(R.string.dues_paid_in_full)
                                    } else {
                                        stringResource(R.string.dues_partial_payment)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 3. Compact High-Resolution Thermal Printer Preview Chamber with 80mm/58mm & Zoom Controls
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 390.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 7.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)
                        ) {
                            // Chamber status indicator & Paper roll switcher (80mm vs 58mm)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = stringResource(R.string.thermal_dispenser_active),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF94A3B8),
                                        letterSpacing = 0.4.sp
                                    )
                                }

                                // 80mm / 58mm Filter Chips
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedRollWidth == 80,
                                        onClick = {
                                             haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedRollWidth = 80
                                        },
                                        label = { Text("80mm", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF059669),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            labelColor = Color(0xFF94A3B8)
                                        ),
                                        border = null,
                                        modifier = Modifier.height(24.dp)
                                    )
                                    FilterChip(
                                        selected = selectedRollWidth == 58,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedRollWidth = 58
                                        },
                                        label = { Text("58mm", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF059669),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            labelColor = Color(0xFF94A3B8)
                                        ),
                                        border = null,
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // Zoom Suite Toolbar [ SCALE: 100% | − | Reset 1x | + ]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(7.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.thermal_zoom_scale_label),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "${(zoomScale * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF10B981)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Zoom out
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            zoomScale = (zoomScale - 0.2f).coerceIn(0.7f, 2.2f)
                                        },
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        modifier = Modifier.size(width = 24.dp, height = 20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("−", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFCBD5E1))
                                        }
                                    }

                                    // Reset 1x
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            zoomScale = if (zoomScale != 1f) 1f else 1.35f
                                        },
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.height(20.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        ) {
                                            Text(
                                                text = if (zoomScale != 1f) stringResource(R.string.thermal_zoom_reset) else stringResource(R.string.thermal_zoom_in),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }
                                    }

                                    // Zoom in
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            zoomScale = (zoomScale + 0.2f).coerceIn(0.7f, 2.2f)
                                        },
                                        shape = RoundedCornerShape(5.dp),
                                        color = Color.White.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        modifier = Modifier.size(width = 24.dp, height = 20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("+", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFCBD5E1))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(5.dp))

                            // Scrollable thermal image viewport with 2D scroll support
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                if (bitmapPreview != null) {
                                    val baseFraction = if (selectedRollWidth == 58) 0.78f else 0.94f
                                    val targetFraction = (baseFraction * zoomScale).coerceIn(0.6f, 2.5f)
                                    Image(
                                        bitmap = bitmapPreview!!.asImageBitmap(),
                                        contentDescription = "$selectedRollWidth mm Thermal Due Repayment Receipt Preview",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth(targetFraction)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Quick Text Actions Bar with 3D Clipboard & Action Chips
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        clipboardManager.setText(AnnotatedString(receiptText))
                                        isCopied = true
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.receipt_text_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            ) {
                                Realistic3DClipboardCopyIcon(size = 18.dp, copied = isCopied)
                                Text(
                                    text = stringResource(R.string.receipt_sms_whatsapp_text),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                // Share Text Button
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        shareReceiptAsText(context, receiptText)
                                    },
                                    shape = RoundedCornerShape(7.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = stringResource(R.string.cd_share_text),
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.share),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                }

                                // Copy Text Button
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        clipboardManager.setText(AnnotatedString(receiptText))
                                        isCopied = true
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.receipt_text_copied),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (isCopied) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = if (isCopied) {
                                                stringResource(R.string.copied_check)
                                            } else {
                                                stringResource(R.string.copy)
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isCopied) Color.White else Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Redesigned Action Buttons Deck with Photorealistic 3D Animated Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Print Button (Blue Gradient with 3D Printer Icon)
                        Button(
                            onClick = {
                                if (!isPrintingReceipt) {
                                    val fileToPrint = receiptFile
                                    if (fileToPrint != null) {
                                        isPrintingReceipt = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        coroutineScope.launch {
                                            try {
                                                val outcome = printReceiptImage(
                                                    context = context,
                                                    activity = context.findActivity(),
                                                    imageFile = fileToPrint,
                                                    jobName = context.getString(R.string.print_job_due_voucher_fmt, data.customerName),
                                                    settings = settings.copy(paperRollWidthMm = selectedRollWidth),
                                                    kickDrawer = false
                                                )
                                                if (outcome == ReceiptPrintOutcome.DIRECT_SENT) {
                                                    Toast.makeText(context, context.getString(R.string.print_sent_to_printer), Toast.LENGTH_SHORT).show()
                                                } else if (outcome == ReceiptPrintOutcome.DIALOG_OPENED) {
                                                    Toast.makeText(context, context.getString(R.string.print_system_dialog_opened), Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                                Toast.makeText(context, context.getString(R.string.toast_print_error_fmt, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isPrintingReceipt = false
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isPrintingReceipt,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Realistic3DPrinterIcon(size = 18.dp, animate = true)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.print_roll_fmt, if (isAppBangla) (if (selectedRollWidth == 80) "৮০মিমি" else "৫৮মিমি") else "${selectedRollWidth}mm"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Share Slip Button (Emerald with 3D Share Icon)
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                try {
                                    val file = receiptFile ?: generateDueRepaymentJpeg(
                                        context = context,
                                        customerName = data.customerName,
                                        customerPhone = data.customerPhone,
                                        amountPaid = data.amountPaid,
                                        paymentMethod = data.paymentMethod,
                                        referenceNote = data.referenceNote,
                                        remainingDue = data.remainingDue,
                                        storeName = storeName,
                                        storeAddress = storeAddress,
                                        storePhone = storePhone,
                                        isBangla = isReceiptBangla,
                                        settings = settings.copy(paperRollWidthMm = selectedRollWidth),
                                        timestamp = data.timestamp,
                                        isReprint = data.isReprint
                                    )
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shared = shareBillJpeg(context, uri)
                                    if (!shared) {
                                        shareReceiptAsText(context, receiptText)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    shareReceiptAsText(context, receiptText)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.05f)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Realistic3DShareIcon(size = 18.dp, animate = true)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.share_slip),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Save Image Button (Glassmorphic Outlined with 3D Download Icon)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                try {
                                    val file = receiptFile ?: generateDueRepaymentJpeg(
                                        context = context,
                                        customerName = data.customerName,
                                        customerPhone = data.customerPhone,
                                        amountPaid = data.amountPaid,
                                        paymentMethod = data.paymentMethod,
                                        referenceNote = data.referenceNote,
                                        remainingDue = data.remainingDue,
                                        storeName = storeName,
                                        storeAddress = storeAddress,
                                        storePhone = storePhone,
                                        isBangla = isReceiptBangla,
                                        settings = settings.copy(paperRollWidthMm = selectedRollWidth),
                                        timestamp = data.timestamp,
                                        isReprint = data.isReprint
                                    )
                                    when (val result = saveBillJpegToDownloads(context, file)) {
                                        is FileSaveResult.PublicSaved -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.repayment_receipt_saved_success),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        is FileSaveResult.PrivateFallback -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.file_saved_private_fallback, result.path),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        FileSaveResult.Failed -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.receipt_save_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.receipt_save_failed),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, Color(0xFFCBD5E1)),
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .weight(0.95f)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Realistic3DDownloadIcon(size = 18.dp, animate = true)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isAppBangla) "সংরক্ষণ" else "Save Slip",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
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
fun SetCreditLimitDialog(
    customer: CustomerDue,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var text by remember(customer.customerPhone) {
        mutableStateOf(if (customer.creditLimit > 0.0) customer.creditLimit.toLong().toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💳", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dues_set_credit_limit_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.dues_credit_limit_description, customer.customerName),
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = toEnglishDigits(it).filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.dues_credit_limit_label)) },
                    placeholder = { Text(stringResource(R.string.dues_no_limit_placeholder)) },
                    prefix = { Text("${formatMoney(0.0).take(1)} ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1000, 5000, 10000, 20000).forEach { preset ->
                        OutlinedButton(
                            onClick = { text = preset.toString() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(formatMoney(preset.toDouble()), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = text.toDoubleOrNull() ?: 0.0
                    onConfirm(limit)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.dues_save_limit_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dues_cancel))
            }
        }
    )
}

@Composable
fun EditDueCustomerDialog(
    customer: CustomerDue,
    onDismiss: () -> Unit,
    onSave: (newPhone: String, name: String, creditLimit: Double, notes: String?) -> Unit
) {
    var phone by remember(customer.customerPhone) { mutableStateOf(customer.customerPhone) }
    var name by remember(customer.customerPhone) { mutableStateOf(customer.customerName) }
    var creditLimitStr by remember(customer.customerPhone) {
        mutableStateOf(if (customer.creditLimit > 0) customer.creditLimit.toLong().toString() else "")
    }
    var notes by remember(customer.customerPhone) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✏️", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.edit_customer_due_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    enabled = true,
                    label = { Text(stringResource(R.string.dues_phone_number_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dues_customer_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = creditLimitStr,
                    onValueChange = { creditLimitStr = toEnglishDigits(it).filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(stringResource(R.string.dues_credit_limit_label)) },
                    prefix = { Text("${formatMoney(0.0).take(1)} ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.customer_notes_optional_label)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val limit = creditLimitStr.toDoubleOrNull() ?: 0.0
                    onSave(phone.trim(), name.trim(), limit, notes.trim().takeIf { it.isNotBlank() })
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
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

