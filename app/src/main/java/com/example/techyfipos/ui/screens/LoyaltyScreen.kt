package com.example.techyfipos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.CustomerLoyalty
import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.ui.viewmodel.CustomerViewModel
import com.example.techyfipos.ui.viewmodel.LoyaltyViewModel
import com.example.techyfipos.utils.formatTaka

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoyaltyScreen(
    loyaltyViewModel: LoyaltyViewModel,
    customerViewModel: CustomerViewModel? = null,
    onBack: () -> Unit,
    onStartSaleForCustomer: ((Customer) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val memberDeletedMessage = stringResource(R.string.loyalty_deleted_success)
    val members by loyaltyViewModel.allMembers.collectAsStateWithLifecycle()
    val transactions by loyaltyViewModel.allTransactions.collectAsStateWithLifecycle()
    val dossierData by (customerViewModel?.selectedCustomerDossier?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf<CustomerLoyalty?>(null) }
    var memberToDelete by remember { mutableStateOf<CustomerLoyalty?>(null) }
    var selectedMemberForHistory by remember { mutableStateOf<CustomerLoyalty?>(null) }
    var customerToEditInLoyalty by remember { mutableStateOf<Customer?>(null) }

    val filtered = remember(members, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) members else members.filter {
            it.name.contains(normalized, ignoreCase = true) || it.phone.contains(normalized)
        }
    }

    val displayTransactions = remember(transactions, selectedMemberForHistory) {
        if (selectedMemberForHistory == null) {
            transactions
        } else {
            transactions.filter { it.customerPhone == selectedMemberForHistory?.phone }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.loyalty_screen_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (selectedMemberForHistory != null) selectedMemberForHistory!!.name else stringResource(R.string.loyalty_members_count, members.size),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (selectedMemberForHistory != null) {
                        TextButton(onClick = { selectedMemberForHistory = null }) {
                            Text(stringResource(R.string.loyalty_show_all_history), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.loyalty_add_member))
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.loyalty_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                Text(stringResource(R.string.loyalty_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }
                    }
                }
                items(filtered, key = { it.phone }) { member ->
                    val isSelected = selectedMemberForHistory?.phone == member.phone
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        onClick = { adjusting = member },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(member.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(member.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        stringResource(R.string.loyalty_lifetime_spend, formatTaka(member.lifetimeSpent)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                ) {
                                    Text(
                                        stringResource(R.string.loyalty_points, member.pointsBalance),
                                        color = Color(0xFF92400E),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (customerViewModel != null) {
                                    IconButton(
                                        onClick = { customerViewModel.loadCustomerDossier(member.phone) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = "Customer 360 Dossier",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { memberToDelete = member },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (displayTransactions.isNotEmpty()) {
                    item {
                        Text(
                            text = if (selectedMemberForHistory != null) stringResource(R.string.loyalty_customer_history_title, selectedMemberForHistory!!.name) else stringResource(R.string.loyalty_recent_activity),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(displayTransactions.take(30), key = { "tx-${it.id}" }) { tx ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tx.reason, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        if (tx.pointsChange >= 0) "+${tx.pointsChange}" else tx.pointsChange.toString(),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (tx.pointsChange >= 0) Color(0xFF059669) else Color(0xFFE11D48)
                                    )
                                }
                                Text(tx.customerPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.loyalty_add_member), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.loyalty_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.loyalty_phone)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank() && phone.isNotBlank(),
                    onClick = {
                        loyaltyViewModel.upsertMember(phone, name)
                        showAdd = false
                    }
                ) { Text(stringResource(R.string.loyalty_save_member), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    adjusting?.let { member ->
        var pointsText by remember(member.phone) { mutableStateOf("") }
        var reason by remember(member.phone) { mutableStateOf("") }
        var editedName by remember(member.phone) { mutableStateOf(member.name) }
        val points = pointsText.toIntOrNull() ?: 0
        AlertDialog(
            onDismissRequest = { adjusting = null },
            title = { Text(stringResource(R.string.loyalty_adjust_points), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text(stringResource(R.string.loyalty_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pointsText,
                        onValueChange = { pointsText = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.loyalty_points_amount)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.loyalty_adjustment_reason)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            loyaltyViewModel.upsertMember(member.phone, editedName)
                            adjusting = null
                        }) { Text(stringResource(R.string.loyalty_save_profile), fontWeight = FontWeight.Bold) }
                        TextButton(onClick = {
                            loyaltyViewModel.deactivateMember(member.phone)
                            adjusting = null
                        }) { Text(stringResource(R.string.loyalty_deactivate), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = points > 0,
                    onClick = {
                        loyaltyViewModel.addPoints(member.phone, points, 0.0, reason)
                        adjusting = null
                    }
                ) { Text(stringResource(R.string.loyalty_add_points), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = points in 1..member.pointsBalance,
                    onClick = {
                        loyaltyViewModel.redeemPoints(member.phone, points, reason)
                        adjusting = null
                    }
                ) { Text(stringResource(R.string.loyalty_redeem_points), fontWeight = FontWeight.Bold) }
            }
        )
    }

    memberToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text(stringResource(R.string.loyalty_delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.loyalty_delete_confirm_message, member.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        loyaltyViewModel.deleteMember(member.phone)
                        memberToDelete = null
                        Toast.makeText(context, memberDeletedMessage, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (dossierData != null && customerViewModel != null) {
        CustomerDossierSheet(
            dossier = dossierData!!,
            onDismiss = { customerViewModel.clearDossier() },
            onStartSale = { customer ->
                customerViewModel.clearDossier()
                onStartSaleForCustomer?.invoke(customer)
            },
            onEditCustomer = { customer ->
                customerViewModel.clearDossier()
                customerToEditInLoyalty = customer
            }
        )
    }

    if (customerToEditInLoyalty != null && customerViewModel != null) {
        val editingCustomer = customerToEditInLoyalty!!
        CustomerAddEditDialog(
            customer = editingCustomer,
            onDismiss = { customerToEditInLoyalty = null },
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
                        Toast.makeText(context, "Customer saved successfully", Toast.LENGTH_SHORT).show()
                        customerToEditInLoyalty = null
                        customerViewModel.loadCustomerDossier(phone)
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}
