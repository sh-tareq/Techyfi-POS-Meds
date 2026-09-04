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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Expense
import com.example.techyfipos.ui.components.AddExpenseDialog
import com.example.techyfipos.ui.components.ExpenseItemRow
import com.example.techyfipos.ui.components.ExpenseSummaryCard
import com.example.techyfipos.ui.components.Realistic3DExpenseReceiptIcon
import com.example.techyfipos.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    expenseViewModel: ExpenseViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deleteFailedMessage = stringResource(R.string.expense_delete_failed)
    val expenseUpdatedMessage = stringResource(R.string.expense_edit_success)
    val exportExpenseSuccessMessage = stringResource(R.string.expense_export_excel_success)

    val allExpenses by expenseViewModel.allExpenses.collectAsStateWithLifecycle()
    val drawerCash by expenseViewModel.liveDrawerCash.collectAsStateWithLifecycle(0.0)
    val totalCashIn by expenseViewModel.totalCashIn.collectAsStateWithLifecycle(0.0)
    val totalCashOut by expenseViewModel.totalCashOut.collectAsStateWithLifecycle(0.0)

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showAddCashInDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedTypeFilter by remember { mutableStateOf("All") }

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
                    Toast.makeText(context, e.localizedMessage ?: "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val categories = listOf("All", "Tea & Refreshments", "Staff Meal", "Electricity (Token)", "Transport", "Shop Rent", "Cleaning", "Float In", "Other")

    val filteredExpenses = remember(allExpenses, searchQuery, selectedCategoryFilter, selectedTypeFilter) {
        allExpenses.filter { exp ->
            val matchesSearch = searchQuery.isBlank() ||
                    exp.title.contains(searchQuery, ignoreCase = true) ||
                    (exp.note?.contains(searchQuery, ignoreCase = true) == true) ||
                    exp.category.contains(searchQuery, ignoreCase = true)

            val matchesCat = selectedCategoryFilter == "All" || exp.category.equals(selectedCategoryFilter, ignoreCase = true)

            val matchesType = when (selectedTypeFilter) {
                "Cash In" -> exp.type == "CASH_IN"
                "Expense" -> exp.type == "EXPENSE"
                else -> true
            }

            matchesSearch && matchesCat && matchesType
        }
    }

    val totalCount = allExpenses.size
    val expCount = remember(allExpenses) { allExpenses.count { it.type == "EXPENSE" } }
    val cashInCount = remember(allExpenses) { allExpenses.count { it.type == "CASH_IN" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.expense_screen_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.expense_screen_subtitle),
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
                    IconButton(
                        onClick = {
                            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
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
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.expense_shift_active),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
        ) {
            item {
                ExpenseSummaryCard(
                    drawerCash = drawerCash,
                    totalCashIn = totalCashIn,
                    totalCashOut = totalCashOut,
                    onAddExpense = { showAddExpenseDialog = true },
                    onAddCashIn = { showAddCashInDialog = true }
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.expense_search_hint), fontSize = 12.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search), tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear), tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
            }

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
                                modifier = Modifier.weight(1f)
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
                                        Text(
                                            text = "$count",
                                            fontSize = 9.5.sp,
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

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        Surface(
                            onClick = { selectedCategoryFilter = cat },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color(0xFF0072FF) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF0072FF) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Text(
                                text = when (cat) {
                                    "All" -> stringResource(R.string.expense_category_all)
                                    "Tea & Refreshments" -> stringResource(R.string.expense_category_tea)
                                    "Staff Meal" -> stringResource(R.string.expense_category_staff_meal)
                                    "Electricity (Token)" -> stringResource(R.string.expense_category_electricity)
                                    "Shop Rent" -> stringResource(R.string.expense_category_rent)
                                    "Cleaning" -> stringResource(R.string.expense_category_cleaning)
                                    "Transport" -> stringResource(R.string.expense_category_transport)
                                    "Float In" -> stringResource(R.string.expense_category_float_in)
                                    else -> stringResource(R.string.expense_category_other_format, cat)
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

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
                    ExpenseItemRow(
                        expense = expense,
                        onEdit = { expenseToEdit = expense },
                        onDelete = { expenseViewModel.deleteExpense(expense) { result ->
                            result.onFailure { Toast.makeText(context, it.localizedMessage ?: deleteFailedMessage, Toast.LENGTH_LONG).show() }
                        } }
                    )
                }
            }
        }
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            isCashIn = false,
            onDismiss = { showAddExpenseDialog = false },
            onSave = { title, cat, amt, src, note ->
                expenseViewModel.recordExpense(title, cat, amt, src, note)
                showAddExpenseDialog = false
            }
        )
    }

    if (showAddCashInDialog) {
        AddExpenseDialog(
            isCashIn = true,
            onDismiss = { showAddCashInDialog = false },
            onSave = { title, cat, amt, src, note ->
                expenseViewModel.recordCashIn(title, amt, src, note)
                showAddCashInDialog = false
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
                    }
                }
                expenseToEdit = null
            }
        )
    }
}
