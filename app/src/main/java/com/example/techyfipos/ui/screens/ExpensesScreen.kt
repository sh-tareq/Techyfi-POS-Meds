package com.example.techyfipos.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import com.example.techyfipos.theme.extendedColors
import com.example.techyfipos.ui.components.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Expense
import com.example.techyfipos.ui.components.AddExpenseDialog
import com.example.techyfipos.ui.components.ExpenseItemRow
import com.example.techyfipos.ui.components.Realistic3DExpenseReceiptIcon
import com.example.techyfipos.ui.viewmodel.ExpenseViewModel
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.getLocalizedRepositoryErrorMessage
import com.example.techyfipos.utils.DateFormatters
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun todayStartMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    expenseViewModel: ExpenseViewModel,
    onBack: () -> Unit,
    onNavigateToDayStartClose: () -> Unit = {},
    isTablet: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deleteFailedMessage = stringResource(R.string.expense_delete_failed)
    val expenseUpdatedMessage = stringResource(R.string.expense_edit_success)
    val exportExpenseSuccessMessage = stringResource(R.string.expense_export_excel_success)

    val allExpenses by expenseViewModel.allExpenses.collectAsStateWithLifecycle()

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddCashInDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedTypeFilter by remember { mutableStateOf("All") }
    var selectedDateFilter by remember { mutableStateOf("All") }

    val todayStart = remember { todayStartMillis() }
    val todayExpenses = remember(allExpenses, todayStart) {
        allExpenses.filter { it.type == "EXPENSE" && it.timestamp >= todayStart }
    }
    val todayExpenseTotal = remember(todayExpenses) {
        todayExpenses.sumOf { it.amount }
    }
    val todayExpenseCount = todayExpenses.size

    val categoryBreakdown = remember(todayExpenses) {
        todayExpenses
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val exportExpensesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val success = expenseViewModel.exportExpensesReport(output)
                        if (success) {
                            Toast.makeText(context, exportExpenseSuccessMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "${context.getString(R.string.excel_export_failed)}: ${getLocalizedRepositoryErrorMessage(context, e)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val categories = remember {
        listOf(
            ExpenseCategoryFilter("All", R.string.expense_category_all),
            ExpenseCategoryFilter("Tea & Refreshments", R.string.expense_category_tea, listOf("Tea", "Snacks", "চা")),
            ExpenseCategoryFilter("Staff Meal", R.string.expense_category_staff_meal, listOf("Meal", "খাবার")),
            ExpenseCategoryFilter("Electricity (Token)", R.string.expense_category_electricity, listOf("Electricity", "Electricity (DESCO/DPDC)", "Power", "বিদ্যুৎ")),
            ExpenseCategoryFilter("Shop Rent", R.string.expense_category_rent, listOf("Rent", "দোকান ভাড়া", "ভাড়া")),
            ExpenseCategoryFilter("Cleaning", R.string.expense_category_cleaning, listOf("পরিষ্কার")),
            ExpenseCategoryFilter("Transport", R.string.expense_category_transport, listOf("যাতায়াত")),
            ExpenseCategoryFilter("Supplier Payment", R.string.expense_category_supplier_payment, listOf("সাপ্লায়ার", "মহাজন")),
            ExpenseCategoryFilter("Float In", R.string.expense_category_float_in, listOf("ফ্লোট ইন", "Owner Capital", "Change Exchange")),
            ExpenseCategoryFilter("Other", R.string.expense_category_other_format, listOf("অন্যান্য"))
        )
    }

    val filteredExpenses = remember(allExpenses, searchQuery, selectedCategoryFilter, selectedTypeFilter, selectedDateFilter, categories) {
        allExpenses.filter { exp ->
            val matchesSearch = searchQuery.isBlank() ||
                    exp.title.contains(searchQuery, ignoreCase = true) ||
                    (exp.note?.contains(searchQuery, ignoreCase = true) == true) ||
                    exp.category.contains(searchQuery, ignoreCase = true)

            val activeCat = categories.find { it.key == selectedCategoryFilter }
            val matchesCat = activeCat?.matches(exp.category) ?: (selectedCategoryFilter == "All" || exp.category.equals(selectedCategoryFilter, ignoreCase = true))

            val matchesType = when (selectedTypeFilter) {
                "Cash In" -> exp.type == "CASH_IN"
                "Expense" -> exp.type == "EXPENSE"
                else -> true
            }

            val matchesDate = when (selectedDateFilter) {
                "Today" -> exp.timestamp >= todayStart
                "Week" -> exp.timestamp >= (todayStart - 6 * 24 * 60 * 60 * 1000L)
                "Month" -> {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    exp.timestamp >= cal.timeInMillis
                }
                else -> true
            }

            matchesSearch && matchesCat && matchesType && matchesDate
        }
    }

    val totalCount = allExpenses.size
    val expCount = remember(allExpenses) { allExpenses.count { it.type == "EXPENSE" } }
    val cashInCount = remember(allExpenses) { allExpenses.count { it.type == "CASH_IN" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = stringResource(R.string.expense_screen_title),
                        metricBadge = stringResource(R.string.today),
                        badgeColor = PosSectionColors.Expenses,
                        isTablet = isTablet,
                        icon = { size -> Realistic3DExpensesCashIcon(size = size) }
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
                            val dateStr = DateFormatters.formatISO()
                            exportExpensesLauncher.launch("expenses-$dateStr.xlsx")
                        }
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = stringResource(R.string.expense_export_excel_button),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.extendedColors.successContainer,
                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.success.copy(alpha = 0.5f)),
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
                                    .background(MaterialTheme.extendedColors.success, CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.expense_shift_active),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.extendedColors.onSuccessContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp)
            ) {
            // 1. Pure Expense Analytics Hero Card
            item {
                ExpenseAnalyticsHeroCard(
                    totalAmount = todayExpenseTotal,
                    expenseCount = todayExpenseCount,
                    categoryBreakdown = categoryBreakdown,
                    onRecordExpense = { showAddExpenseDialog = true },
                    onRecordCashIn = { showAddCashInDialog = true }
                )
            }

            // 2. Relocation Banner: Cash Drawer has moved to Cash Register
            item {
                ExpenseRelocationNoticeBanner(
                    onViewDrawer = onNavigateToDayStartClose
                )
            }

            // 3. Search Bar
            item {
                PosSearchBox(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = stringResource(R.string.expense_search_hint),
                    accentColor = PosSectionColors.Expenses,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Type Filter Segmented Control
            item {
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
                        listOf(
                            Triple("All", stringResource(R.string.expense_filter_all), totalCount),
                            Triple("Expense", stringResource(R.string.expense_filter_expenses), expCount),
                            Triple("Cash In", stringResource(R.string.expense_filter_cash_in), cashInCount)
                        ).forEach { (typeKey, label, count) ->
                            val isSelected = selectedTypeFilter == typeKey
                            Surface(
                                onClick = { selectedTypeFilter = typeKey },
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 7.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    ) {
                                        val isBangla = com.example.techyfipos.utils.isAppBangla()
                                        val countStr = if (isBangla) com.example.techyfipos.utils.toBanglaDigits(count.toString()) else count.toString()
                                        Text(
                                            text = countStr,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4.5 Date Filter Chips
            item {
                val dateOptions = listOf(
                    "All" to R.string.history_period_all,
                    "Today" to R.string.today,
                    "Week" to R.string.history_period_week,
                    "Month" to R.string.history_period_month
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dateOptions.forEach { (key, labelRes) ->
                        val isSelected = selectedDateFilter == key
                        Surface(
                            onClick = { selectedDateFilter = key },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            shadowElevation = if (isSelected) 1.dp else 0.dp,
                            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // 5. Category Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { filterItem ->
                        val isSelected = selectedCategoryFilter == filterItem.key
                        Surface(
                            onClick = { selectedCategoryFilter = filterItem.key },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color(0xFF0072FF) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF0072FF) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                        ) {
                            Text(
                                text = if (filterItem.key == "Other") stringResource(filterItem.labelRes, stringResource(R.string.category_other))
                                else stringResource(filterItem.labelRes),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // 6. Header Row: Ledger records count
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.expense_ledger_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.expense_records_count,
                            filteredExpenses.size,
                            filteredExpenses.size
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(28.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Realistic3DExpenseReceiptIcon(size = 48.dp)
                            Text(
                                text = if (searchQuery.isNotEmpty() || selectedCategoryFilter != "All") stringResource(R.string.expense_empty_search_title) else stringResource(R.string.expense_empty_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.expense_empty_subtitle),
                                fontSize = 11.5.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                items(filteredExpenses, key = { it.id }) { expense ->
                    val isSupplierPayment = expense.category.equals("Supplier Payment", ignoreCase = true) &&
                        (expense.title.startsWith("Supplier Invoice #") || expense.title.startsWith("Supplier Payment: "))
                    ExpenseItemRow(
                        expense = expense,
                        onEdit = if (!isSupplierPayment) { { expenseToEdit = expense } } else null,
                        onDelete = if (!isSupplierPayment) {
                            {
                                expenseViewModel.deleteExpense(expense) { result ->
                                    result.onFailure { Toast.makeText(context, it.localizedMessage ?: deleteFailedMessage, Toast.LENGTH_LONG).show() }
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }
}

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            isCashIn = false,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { title, cat, amt, src, note ->
                expenseViewModel.recordExpense(title, cat, amt, src, note) { res ->
                    res.onSuccess {
                        showAddExpenseDialog = false
                        Toast.makeText(context, context.getString(R.string.expense_add_success), Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, getLocalizedRepositoryErrorMessage(context, err), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showAddCashInDialog) {
        AddExpenseDialog(
            isCashIn = true,
            onDismiss = { showAddCashInDialog = false },
            onSave = { title, cat, amt, src, note ->
                expenseViewModel.recordCashIn(title, amt, src, note) { res ->
                    res.onSuccess {
                        showAddCashInDialog = false
                        Toast.makeText(context, context.getString(R.string.expense_cash_in_success), Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, getLocalizedRepositoryErrorMessage(context, err), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    expenseToEdit?.let { expense ->
        AddExpenseDialog(
            initialExpense = expense,
            isCashIn = expense.type == "CASH_IN",
            onDismiss = { expenseToEdit = null },
            onSave = { title, cat, amt, src, note ->
                expenseViewModel.updateExpense(
                    expense.copy(
                        title = title,
                        category = cat,
                        amount = amt,
                        paymentSource = src,
                        note = note
                    )
                ) { result ->
                    result.onSuccess {
                        Toast.makeText(context, expenseUpdatedMessage, Toast.LENGTH_SHORT).show()
                        expenseToEdit = null
                    }.onFailure { err ->
                        Toast.makeText(context, getLocalizedRepositoryErrorMessage(context, err), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

// =============================================================================
// EXPENSE ANALYTICS HERO CARD
// =============================================================================

@Composable
fun ExpenseAnalyticsHeroCard(
    totalAmount: Double,
    expenseCount: Int,
    categoryBreakdown: List<Pair<String, Double>>,
    onRecordExpense: () -> Unit,
    onRecordCashIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0x33FB7185)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF881337), Color(0xFF4C0519), Color(0xFF1E1B4B))
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Top Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.expense_hero_total_label),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFCBD5E1),
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = formatTaka(totalAmount),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text("⚠️", fontSize = 11.sp)
                            Text(
                                text = stringResource(R.string.expense_hero_disbursements, expenseCount),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFCA5A5)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Realistic3DExpenseReceiptIcon(size = 38.dp)
                        }
                    }
                }

                // Visual Category Distribution Bar
                if (totalAmount > 0.0 && categoryBreakdown.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val categoryColors = listOf(
                            Color(0xFFF59E0B), // Amber
                            Color(0xFFEF4444), // Rose/Red
                            Color(0xFF8B5CF6), // Purple
                            Color(0xFF10B981), // Emerald
                            Color(0xFF0EA5E9), // Sky Blue
                            Color(0xFFEC4899)  // Pink
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            categoryBreakdown.forEachIndexed { index, (_, amt) ->
                                val fraction = (amt / totalAmount).toFloat().coerceIn(0.01f, 1f)
                                val color = categoryColors[index % categoryColors.size]
                                Box(
                                    modifier = Modifier
                                        .weight(fraction)
                                        .fillMaxHeight()
                                        .background(color)
                                )
                            }
                        }

                        // Legend items row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            categoryBreakdown.take(4).forEachIndexed { index, (cat, amt) ->
                                val pct = ((amt / totalAmount) * 100).toInt()
                                val color = categoryColors[index % categoryColors.size]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    val shortCat = when {
                                        cat.contains("Tea", ignoreCase = true) -> "Tea"
                                        cat.contains("Staff", ignoreCase = true) -> "Meal"
                                        cat.contains("Electricity", ignoreCase = true) -> "Power"
                                        cat.contains("Transport", ignoreCase = true) -> "Transport"
                                        cat.contains("Rent", ignoreCase = true) -> "Rent"
                                        else -> cat.take(8)
                                    }
                                    Text(
                                        text = "$shortCat $pct%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons Row: Record Expense & Cash In
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRecordExpense,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.expense_add_expense_button),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onRecordCashIn,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.expense_add_cash_in_button),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// RELOCATION NOTICE BANNER
// =============================================================================

@Composable
fun ExpenseRelocationNoticeBanner(
    onViewDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.extendedColors.successContainer,
        border = BorderStroke(1.dp, MaterialTheme.extendedColors.success.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💡", fontSize = 16.sp)
                Text(
                    text = stringResource(R.string.expense_cash_moved_banner),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.extendedColors.onSuccessContainer,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onViewDrawer,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.success),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = stringResource(R.string.expense_view_drawer_btn),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.extendedColors.onSuccess
                )
            }
        }
    }
}

data class ExpenseCategoryFilter(
    val key: String,
    @get:androidx.annotation.StringRes val labelRes: Int,
    val aliases: List<String> = emptyList()
) {
    fun matches(category: String): Boolean {
        if (key == "All") return true
        if (category.equals(key, ignoreCase = true)) return true
        return aliases.any { it.equals(category, ignoreCase = true) || category.contains(it, ignoreCase = true) }
    }
}
