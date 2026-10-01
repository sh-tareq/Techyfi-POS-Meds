package com.example.techyfipos.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.techyfipos.ui.components.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Customer
import com.example.techyfipos.data.db.CustomerLoyalty
import com.example.techyfipos.data.db.LoyaltyTransaction
import com.example.techyfipos.ui.viewmodel.CustomerViewModel
import com.example.techyfipos.ui.viewmodel.LoyaltyFilterOption
import com.example.techyfipos.ui.viewmodel.LoyaltySortOption
import com.example.techyfipos.ui.viewmodel.LoyaltyViewModel
import com.example.techyfipos.utils.LoyaltyProgramSettings
import com.example.techyfipos.utils.LoyaltySettingsHelper
import com.example.techyfipos.utils.formatTaka
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import com.example.techyfipos.TechyfiPOSApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoyaltyScreen(
    loyaltyViewModel: LoyaltyViewModel,
    customerViewModel: CustomerViewModel? = null,
    settingsViewModel: com.example.techyfipos.ui.viewmodel.SettingsViewModel? = null,
    authViewModel: com.example.techyfipos.ui.viewmodel.AuthViewModel? = null,
    onBack: () -> Unit,
    onStartSaleForCustomer: ((Customer) -> Unit)? = null,
    isTablet: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val memberDeletedMessage = stringResource(R.string.loyalty_deleted_success)

    // State from ViewModel
    val members by loyaltyViewModel.allMembers.collectAsStateWithLifecycle()
    val filteredMembers by loyaltyViewModel.filteredMembers.collectAsStateWithLifecycle()
    val allTransactions by loyaltyViewModel.allTransactions.collectAsStateWithLifecycle()
    val kpiStats by loyaltyViewModel.kpiStats.collectAsStateWithLifecycle()
    val searchQuery by loyaltyViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by loyaltyViewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by loyaltyViewModel.selectedSort.collectAsStateWithLifecycle()
    val dossierData by (customerViewModel?.selectedCustomerDossier?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) })

    // Program settings
    val programSettings by loyaltyViewModel.programSettings.collectAsStateWithLifecycle()

    // Dialog & Sheet States
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSortFilterSheet by remember { mutableStateOf(false) }
    var memberToQuickRedeem by remember { mutableStateOf<CustomerLoyalty?>(null) }
    var memberToAdjust by remember { mutableStateOf<CustomerLoyalty?>(null) }
    var memberToDelete by remember { mutableStateOf<CustomerLoyalty?>(null) }
    var memberForLedgerSheet by remember { mutableStateOf<CustomerLoyalty?>(null) }
    var customerToEditInLoyalty by remember { mutableStateOf<Customer?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // TOP APP BAR
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                        PosTopAppBarTitle(
                            title = stringResource(R.string.loyalty_screen_title),
                            metricBadge = if (members.isNotEmpty()) stringResource(R.string.loyalty_metric_badge_members, members.size) else null,
                            badgeColor = PosSectionColors.Loyalty,
                            isTablet = isTablet,
                            icon = { size -> Realistic3DLoyaltyStarIcon(size = size) }
                        )
                }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { showSortFilterSheet = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.loyalty_sort_sheet_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Realistic3DSettingsGearIcon(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // 1. TOP KPI DASHBOARD ROW
            LoyaltyKpiBar(
                kpiStats = kpiStats,
                pointCashValue = programSettings.pointCashValue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // 2. LIVE INSTANT SEARCH BAR
            LoyaltySearchBar(
                query = searchQuery,
                onQueryChange = { loyaltyViewModel.setSearchQuery(it) },
                onClear = { loyaltyViewModel.setSearchQuery("") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )

            // 3. HORIZONTAL QUICK FILTER CHIPS
            LoyaltyFilterChipsRow(
                selectedFilter = selectedFilter,
                onSelectFilter = { loyaltyViewModel.setSelectedFilter(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            // 4. ULTRA-COMPACT CUSTOMER LIST
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (filteredMembers.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Realistic3DLoyaltyStarIcon(modifier = Modifier.size(44.dp))
                                Text(
                                    stringResource(R.string.loyalty_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    items(filteredMembers, key = { it.phone }) { member ->
                        CompactLoyaltyRow(
                            member = member,
                            pointCashValue = programSettings.pointCashValue,
                            onClick = { memberForLedgerSheet = member },
                            onViewProfile = { memberForLedgerSheet = member },
                            onAddPoints = { memberToAdjust = member },
                            onRedeemPoints = { memberToQuickRedeem = member },
                            onAdjustPoints = { memberToAdjust = member },
                            onViewLedger = { memberForLedgerSheet = member },
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phone.trim()}"))
                                context.startActivity(intent)
                            },
                            onDelete = { memberToDelete = member }
                        )
                    }
                }
            }
        }

        // FLOATING ACTION BUTTON (Add Member)
        FloatingActionButton(
            onClick = { showAddMemberDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.loyalty_add_member))
        }
    }

    // ==========================================
    // DIALOGS & BOTTOM SHEETS
    // ==========================================

    // 1. QUICK 1-TOUCH REDEEM DIALOG
    memberToQuickRedeem?.let { member ->
        LoyaltyQuickRedeemDialog(
            member = member,
            pointCashValue = programSettings.pointCashValue,
            minRedeem = programSettings.minRedeemPoints,
            onDismiss = { memberToQuickRedeem = null },
            onConfirmRedeem = { points, reason ->
                loyaltyViewModel.redeemPoints(member.phone, points, reason)
                memberToQuickRedeem = null
                Toast.makeText(context, context.getString(R.string.loyalty_redeemed_points_toast, points, member.name), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. ADJUST / ADD POINTS DIALOG
    memberToAdjust?.let { member ->
        LoyaltyAdjustPointsDialog(
            member = member,
            onDismiss = { memberToAdjust = null },
            onSaveProfile = { name ->
                loyaltyViewModel.upsertMember(member.phone, name)
                Toast.makeText(context, context.getString(R.string.loyalty_profile_saved_toast), Toast.LENGTH_SHORT).show()
            },
            onAddPoints = { pts, reason ->
                loyaltyViewModel.addPoints(member.phone, pts, 0.0, reason)
                memberToAdjust = null
            },
            onRedeemPoints = { pts, reason ->
                loyaltyViewModel.redeemPoints(member.phone, pts, reason)
                memberToAdjust = null
            },
            onDeactivate = {
                loyaltyViewModel.deactivateMember(member.phone)
                memberToAdjust = null
            }
        )
    }

    // 3. DELETE MEMBER CONFIRMATION DIALOG
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

    // 4. ADD MEMBER WITH PRE-REGISTRATION DUPLICATE DETECTION
    if (showAddMemberDialog) {
        LoyaltyAddMemberDialog(
            loyaltyViewModel = loyaltyViewModel,
            onDismiss = { showAddMemberDialog = false },
            onSave = { phone, name, tier ->
                loyaltyViewModel.upsertMember(phone, name, tier)
                showAddMemberDialog = false
                Toast.makeText(context, context.getString(R.string.loyalty_member_registered_toast), Toast.LENGTH_SHORT).show()
            },
            onViewExistingMember = { existing ->
                showAddMemberDialog = false
                memberForLedgerSheet = existing
            }
        )
    }

    // 5. LOYALTY PROGRAM RULES & TIERS SETTINGS DIALOG
    if (showSettingsDialog) {
        LoyaltySettingsDialog(
            currentSettings = programSettings,
            onDismiss = { showSettingsDialog = false },
            onSave = { updated ->
                loyaltyViewModel.updateProgramSettings(updated)
                showSettingsDialog = false
                Toast.makeText(context, context.getString(R.string.loyalty_settings_saved_toast), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 6. SORT & FILTER SHEET
    if (showSortFilterSheet) {
        LoyaltySortFilterBottomSheet(
            currentSort = selectedSort,
            onSelectSort = {
                loyaltyViewModel.setSelectedSort(it)
                showSortFilterSheet = false
            },
            onDismiss = { showSortFilterSheet = false }
        )
    }

    // 7. 360° LOYALTY PROFILE & AUDIT LEDGER SHEET
    memberForLedgerSheet?.let { member ->
        val memberTxs = remember(allTransactions, member.phone) {
            allTransactions.filter { it.customerPhone == member.phone }
        }
        LoyaltyAuditLedgerSheet(
            member = member,
            transactions = memberTxs,
            pointCashValue = programSettings.pointCashValue,
            settings = programSettings,
            onDismiss = { memberForLedgerSheet = null },
            onQuickRedeem = {
                memberToQuickRedeem = member
                memberForLedgerSheet = null
            },
            onAdjust = {
                memberToAdjust = member
                memberForLedgerSheet = null
            },
            onStartSale = {
                if (onStartSaleForCustomer != null) {
                    memberForLedgerSheet = null
                    scope.launch {
                        val fullCustomer = customerViewModel?.getCustomerByPhone(member.phone)
                            ?: loyaltyViewModel.getCustomerByPhone(member.phone)
                            ?: Customer(
                                phone = member.phone,
                                name = member.name,
                                tag = member.tier,
                                loyaltyPoints = member.pointsBalance,
                                loyaltyTier = member.tier
                            )
                        onStartSaleForCustomer(fullCustomer)
                    }
                }
            },
            onCall = {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phone.trim()}"))
                context.startActivity(intent)
            }
        )
    }

    // 8. 360 CUSTOMER DOSSIER SHEET (if triggered via CustomerViewModel)
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
                customerToEditInLoyalty = customer
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
                    onSuccess = { Toast.makeText(context, context.getString(R.string.delete), Toast.LENGTH_SHORT).show() },
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

    // 9. CUSTOMER EDIT DIALOG (from dossier sheet)
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
                        Toast.makeText(context, context.getString(R.string.loyalty_customer_saved_toast), Toast.LENGTH_SHORT).show()
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

// =========================================================================
// SUB-COMPONENTS
// =========================================================================

/**
 * Top KPI Dashboard Bar showing 4 core metrics
 */
@Composable
private fun LoyaltyKpiBar(
    kpiStats: com.example.techyfipos.ui.viewmodel.LoyaltyKpiData,
    pointCashValue: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Card 1: Members
        KpiStatCard(
            title = stringResource(R.string.loyalty_kpi_members),
            value = kpiStats.totalMembers.toString(),
            subtitle = stringResource(R.string.loyalty_kpi_active_program),
            icon = { Realistic3DCustomerHubIcon(modifier = Modifier.size(24.dp)) },
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(132.dp)
        )

        // Card 2: Outstanding Points
        val liability = kpiStats.outstandingPoints * pointCashValue
        KpiStatCard(
            title = stringResource(R.string.loyalty_kpi_outstanding),
            value = stringResource(R.string.loyalty_pts_suffix, kpiStats.outstandingPoints),
            subtitle = stringResource(R.string.loyalty_kpi_liability_val, formatTaka(liability)),
            icon = { Realistic3DGoldCoinsIcon(modifier = Modifier.size(24.dp)) },
            containerColor = Color(0xFFFEF3C7).copy(alpha = 0.6f),
            modifier = Modifier.width(148.dp)
        )

        // Card 3: Redeemed This Month
        KpiStatCard(
            title = stringResource(R.string.loyalty_kpi_redeemed),
            value = stringResource(R.string.loyalty_pts_suffix, kpiStats.redeemedThisMonth),
            subtitle = stringResource(R.string.loyalty_kpi_last_30_days),
            icon = { Realistic3DLoyaltyStarIcon(modifier = Modifier.size(24.dp)) },
            containerColor = Color(0xFFDCFCE7).copy(alpha = 0.6f),
            modifier = Modifier.width(132.dp)
        )

        // Card 4: VIP Members
        KpiStatCard(
            title = stringResource(R.string.loyalty_kpi_vip),
            value = kpiStats.vipMembers.toString(),
            subtitle = stringResource(R.string.loyalty_kpi_gold_platinum),
            icon = { Realistic3DReportChartIcon(modifier = Modifier.size(24.dp)) },
            containerColor = Color(0xFFF3E8FF).copy(alpha = 0.6f),
            modifier = Modifier.width(132.dp)
        )
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                icon()
            }
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Instant Search Bar with clear (X) icon
 */
@Composable
private fun LoyaltySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.loyalty_search), fontSize = 13.sp) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search_cd), tint = Color.Gray)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier.height(50.dp)
    )
}

/**
 * Filter chips row
 */
@Composable
private fun LoyaltyFilterChipsRow(
    selectedFilter: LoyaltyFilterOption,
    onSelectFilter: (LoyaltyFilterOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChipItem(
            text = stringResource(R.string.loyalty_filter_all),
            isSelected = selectedFilter == LoyaltyFilterOption.ALL,
            onClick = { onSelectFilter(LoyaltyFilterOption.ALL) }
        )
        FilterChipItem(
            text = "⭐ ${stringResource(R.string.loyalty_filter_vip)}",
            isSelected = selectedFilter == LoyaltyFilterOption.VIP,
            onClick = { onSelectFilter(LoyaltyFilterOption.VIP) }
        )
        FilterChipItem(
            text = stringResource(R.string.loyalty_filter_with_points),
            isSelected = selectedFilter == LoyaltyFilterOption.WITH_POINTS,
            onClick = { onSelectFilter(LoyaltyFilterOption.WITH_POINTS) }
        )
        FilterChipItem(
            text = stringResource(R.string.loyalty_filter_active),
            isSelected = selectedFilter == LoyaltyFilterOption.ACTIVE,
            onClick = { onSelectFilter(LoyaltyFilterOption.ACTIVE) }
        )
        FilterChipItem(
            text = stringResource(R.string.loyalty_filter_expiring),
            isSelected = selectedFilter == LoyaltyFilterOption.EXPIRING_SOON,
            onClick = { onSelectFilter(LoyaltyFilterOption.EXPIRING_SOON) }
        )
        FilterChipItem(
            text = stringResource(R.string.loyalty_filter_zero),
            isSelected = selectedFilter == LoyaltyFilterOption.ZERO_POINTS,
            onClick = { onSelectFilter(LoyaltyFilterOption.ZERO_POINTS) }
        )
    }
}

@Composable
private fun FilterChipItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Ultra-Compact Customer Loyalty Row (52-56dp)
 */
@Composable
private fun CompactLoyaltyRow(
    member: CustomerLoyalty,
    pointCashValue: Double,
    onClick: () -> Unit,
    onViewProfile: () -> Unit,
    onAddPoints: () -> Unit,
    onRedeemPoints: () -> Unit,
    onAdjustPoints: () -> Unit,
    onViewLedger: () -> Unit,
    onCall: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left & Center (Tappable to open 360 Profile Dossier)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 2.dp)
            ) {
                // Tier Avatar
                TierAvatar(tier = member.tier, name = member.name)

                // Info column
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Line 1: Customer Name (full column width, bold, up to 2 lines so large names are NEVER cut off!)
                    Text(
                        text = member.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Line 2: Tier Badge Capsule + Phone + Dot + Spent
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        TierBadgeCapsule(tier = member.tier)
                        Text(
                            text = member.phone,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = stringResource(R.string.loyalty_spent_suffix, formatTaka(member.lifetimeSpent)),
                            fontSize = 11.sp,
                            color = Color(0xFF059669),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Points Pill (compact vertical 2-line badge saves ~55dp horizontal width for customer names)
                val cashVal = formatTaka(member.pointsBalance * pointCashValue)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⭐ " + stringResource(R.string.loyalty_pts_suffix, member.pointsBalance),
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                        Text(
                            text = cashVal,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Right: 3-Dot Overflow Menu Button (Independent touch target)
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.options_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loyalty_action_view_profile), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            showMenu = false
                            onViewProfile()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loyalty_action_redeem_points), fontSize = 13.sp) },
                        leadingIcon = { Realistic3DLoyaltyStarIcon(modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onRedeemPoints()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loyalty_action_add_points), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color(0xFF059669)) },
                        onClick = {
                            showMenu = false
                            onAddPoints()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loyalty_action_adjust_points), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onAdjustPoints()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loyalty_action_history), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onViewLedger()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loyalty_action_call), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF0284C7)) },
                        onClick = {
                            showMenu = false
                            onCall()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.loyalty_action_delete), fontSize = 13.sp, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TierAvatar(tier: String, name: String) {
    val initial = name.trim().firstOrNull()?.toString()?.uppercase() ?: "C"
    val (bgBrush, textColor) = when (tier.lowercase()) {
        "platinum" -> Pair(
            Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4338CA))),
            Color.White
        )
        "gold", "vip" -> Pair(
            Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
            Color.White
        )
        "silver" -> Pair(
            Brush.linearGradient(listOf(Color(0xFF94A3B8), Color(0xFF64748B))),
            Color.White
        )
        else -> Pair(
            Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB))),
            Color.White
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(bgBrush)
    ) {
        Text(
            text = initial,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TierBadgeCapsule(tier: String) {
    val (bg, fg) = when (tier.lowercase()) {
        "platinum" -> Pair(Color(0xFFEDE9FE), Color(0xFF6D28D9))
        "gold", "vip" -> Pair(Color(0xFFFEF3C7), Color(0xFF92400E))
        "silver" -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
        else -> Pair(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = tier.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = fg,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

// =========================================================================
// MODALS & DIALOGS
// =========================================================================

/**
 * 1-Touch Quick Points Redemption Dialog
 */
@Composable
private fun LoyaltyQuickRedeemDialog(
    member: CustomerLoyalty,
    pointCashValue: Double,
    minRedeem: Int,
    onDismiss: () -> Unit,
    onConfirmRedeem: (points: Int, reason: String) -> Unit
) {
    val defaultReason = stringResource(R.string.loyalty_redeem_default_reason)
    var pointsInput by remember { mutableStateOf("") }
    var reasonInput by remember(defaultReason) { mutableStateOf(defaultReason) }
    val pointsToRedeem = pointsInput.toIntOrNull() ?: 0
    val cashSaved = pointsToRedeem * pointCashValue
    val newBalance = (member.pointsBalance - pointsToRedeem).coerceAtLeast(0)

    val isValid = pointsToRedeem >= minRedeem && pointsToRedeem <= member.pointsBalance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Realistic3DLoyaltyStarIcon(modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.loyalty_action_redeem_points), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Member balance pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.loyalty_current_balance), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = "${stringResource(R.string.loyalty_pts_suffix, member.pointsBalance)} (${formatTaka(member.pointsBalance * pointCashValue)})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Quick percentage chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(25, 50, 75).forEach { pct ->
                        val pts = (member.pointsBalance * pct / 100.0).toInt().coerceAtLeast(0)
                        OutlinedButton(
                            onClick = { pointsInput = pts.toString() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("$pct%", fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = { pointsInput = member.pointsBalance.toString() },
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.loyalty_max_label, member.pointsBalance), fontSize = 11.sp)
                    }
                }

                // Custom points field
                OutlinedTextField(
                    value = pointsInput,
                    onValueChange = { pointsInput = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.loyalty_points_to_redeem_min, minRedeem)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Live projection
                if (pointsToRedeem > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isValid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = if (isValid) stringResource(R.string.loyalty_redeem_valid_preview, formatTaka(cashSaved), newBalance)
                                else stringResource(R.string.loyalty_redeem_invalid_range, minRedeem, member.pointsBalance),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isValid) Color(0xFF166534) else Color(0xFF991B1B)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text(stringResource(R.string.loyalty_redeem_reason_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val redeemBtnText = if (pointsToRedeem > 0) {
                stringResource(R.string.loyalty_redeem_amount_button, formatTaka(cashSaved))
            } else {
                stringResource(R.string.loyalty_redeem_button_simple)
            }
            Button(
                enabled = isValid,
                onClick = { onConfirmRedeem(pointsToRedeem, reasonInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                Text(redeemBtnText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Adjust Points Dialog
 */
@Composable
private fun LoyaltyAdjustPointsDialog(
    member: CustomerLoyalty,
    onDismiss: () -> Unit,
    onSaveProfile: (name: String) -> Unit,
    onAddPoints: (pts: Int, reason: String) -> Unit,
    onRedeemPoints: (pts: Int, reason: String) -> Unit,
    onDeactivate: () -> Unit
) {
    var pointsText by remember(member.phone) { mutableStateOf("") }
    var reason by remember(member.phone) { mutableStateOf("") }
    var editedName by remember(member.phone) { mutableStateOf(member.name) }
    val points = pointsText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loyalty_adjust_points), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.loyalty_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.loyalty_points_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.loyalty_adjustment_reason)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onSaveProfile(editedName) }) {
                        Text(stringResource(R.string.loyalty_save_profile), fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDeactivate) {
                        Text(stringResource(R.string.loyalty_deactivate), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            val defaultAddReason = stringResource(R.string.loyalty_manual_add_reason)
            val defaultDeductReason = stringResource(R.string.loyalty_manual_deduct_reason)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = points in 1..member.pointsBalance,
                    onClick = { onRedeemPoints(points, reason.ifBlank { defaultDeductReason }) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.loyalty_redeem_points), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(
                    enabled = points > 0,
                    onClick = { onAddPoints(points, reason.ifBlank { defaultAddReason }) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.loyalty_add_points), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Add Member Dialog with Pre-Registration Duplicate Phone Detection
 */
@Composable
private fun LoyaltyAddMemberDialog(
    loyaltyViewModel: LoyaltyViewModel,
    onDismiss: () -> Unit,
    onSave: (phone: String, name: String, tier: String) -> Unit,
    onViewExistingMember: (CustomerLoyalty) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf("Silver") }

    val duplicateMember = remember(phone) {
        loyaltyViewModel.findMemberByPhone(phone)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loyalty_add_member), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.loyalty_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.loyalty_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // DUPLICATE WARNING BANNER
                if (duplicateMember != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Realistic3DAmberHazardDiamond(modifier = Modifier.size(18.dp))
                                Text(
                                    stringResource(
                                        R.string.loyalty_duplicate_warning,
                                        duplicateMember.name,
                                        duplicateMember.pointsBalance
                                    ),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Button(
                                onClick = { onViewExistingMember(duplicateMember) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    stringResource(R.string.loyalty_duplicate_view_existing),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Tier Selector
                Text(stringResource(R.string.loyalty_membership_tier_label), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Regular" to R.string.tier_regular,
                        "Silver" to R.string.tier_silver,
                        "Gold" to R.string.tier_gold,
                        "Platinum" to R.string.tier_platinum
                    ).forEach { (tier, tierRes) ->
                        val isSel = selectedTier == tier
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { selectedTier = tier }
                        ) {
                            Text(
                                text = stringResource(tierRes),
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && phone.length >= 6 && duplicateMember == null,
                onClick = { onSave(phone, name, selectedTier) }
            ) {
                Text(stringResource(R.string.loyalty_save_member), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Loyalty Program Rules & Tiers Settings Dialog
 */
@Composable
private fun LoyaltySettingsDialog(
    currentSettings: LoyaltyProgramSettings,
    onDismiss: () -> Unit,
    onSave: (LoyaltyProgramSettings) -> Unit
) {
    var takaPerPoint by remember(currentSettings) { mutableStateOf(currentSettings.takaPerPoint.toString()) }
    var cashValPerPoint by remember(currentSettings) { mutableStateOf(currentSettings.pointCashValue.toString()) }
    var minRedeemPts by remember(currentSettings) { mutableStateOf(currentSettings.minRedeemPoints.toString()) }
    var maxBillPercent by remember(currentSettings) { mutableStateOf(currentSettings.maxBillPercentPayable.toString()) }
    var silverThresh by remember(currentSettings) { mutableStateOf(currentSettings.silverSpendThreshold.toString()) }
    var goldThresh by remember(currentSettings) { mutableStateOf(currentSettings.goldSpendThreshold.toString()) }
    var platThresh by remember(currentSettings) { mutableStateOf(currentSettings.platinumSpendThreshold.toString()) }
    var pointsExpiryDays by remember(currentSettings) { mutableStateOf(currentSettings.pointsExpiryDays.toString()) }
    var isTiersEnabled by remember(currentSettings) { mutableStateOf(currentSettings.isTiersEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Realistic3DSettingsGearIcon(modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.loyalty_settings_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = takaPerPoint,
                    onValueChange = { takaPerPoint = it },
                    label = { Text(stringResource(R.string.loyalty_settings_earn_rate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cashValPerPoint,
                    onValueChange = { cashValPerPoint = it },
                    label = { Text(stringResource(R.string.loyalty_settings_redeem_rate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minRedeemPts,
                        onValueChange = { minRedeemPts = it },
                        label = { Text(stringResource(R.string.loyalty_settings_min_redeem), fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxBillPercent,
                        onValueChange = { maxBillPercent = it },
                        label = { Text(stringResource(R.string.loyalty_settings_max_discount), fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.loyalty_settings_enable_tiers),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isTiersEnabled,
                        onCheckedChange = { isTiersEnabled = it }
                    )
                }
                if (isTiersEnabled) {
                    val sVal = silverThresh.toDoubleOrNull() ?: 0.0
                    val gVal = goldThresh.toDoubleOrNull() ?: 0.0
                    val pVal = platThresh.toDoubleOrNull() ?: 0.0
                    val isTiersValid = sVal < gVal && gVal < pVal
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = silverThresh,
                            onValueChange = { silverThresh = it },
                            label = { Text(stringResource(R.string.loyalty_tier_silver_bdt), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = goldThresh,
                            onValueChange = { goldThresh = it },
                            label = { Text(stringResource(R.string.loyalty_tier_gold_bdt), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = platThresh,
                            onValueChange = { platThresh = it },
                            label = { Text(stringResource(R.string.loyalty_tier_platinum_bdt), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (!isTiersValid) {
                        Text(
                            text = stringResource(R.string.loyalty_tier_order_error),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            val sVal = silverThresh.toDoubleOrNull() ?: 0.0
            val gVal = goldThresh.toDoubleOrNull() ?: 0.0
            val pVal = platThresh.toDoubleOrNull() ?: 0.0
            val isTiersValid = !isTiersEnabled || (sVal < gVal && gVal < pVal)
            Button(
                enabled = isTiersValid,
                onClick = {
                    val updated = currentSettings.copy(
                        takaPerPoint = takaPerPoint.toDoubleOrNull() ?: currentSettings.takaPerPoint,
                        pointCashValue = cashValPerPoint.toDoubleOrNull() ?: currentSettings.pointCashValue,
                        minRedeemPoints = minRedeemPts.toIntOrNull() ?: currentSettings.minRedeemPoints,
                        maxBillPercentPayable = maxBillPercent.toIntOrNull() ?: currentSettings.maxBillPercentPayable,
                        silverSpendThreshold = silverThresh.toDoubleOrNull() ?: currentSettings.silverSpendThreshold,
                        goldSpendThreshold = goldThresh.toDoubleOrNull() ?: currentSettings.goldSpendThreshold,
                        platinumSpendThreshold = platThresh.toDoubleOrNull() ?: currentSettings.platinumSpendThreshold,
                        pointsExpiryDays = pointsExpiryDays.toIntOrNull() ?: currentSettings.pointsExpiryDays,
                        isTiersEnabled = isTiersEnabled
                    )
                    onSave(updated)
                }
            ) {
                Text(stringResource(R.string.loyalty_settings_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * Sort BottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoyaltySortFilterBottomSheet(
    currentSort: LoyaltySortOption,
    onSelectSort: (LoyaltySortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.loyalty_sort_sheet_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            SortOptionItem(
                title = stringResource(R.string.loyalty_sort_points_desc),
                isSelected = currentSort == LoyaltySortOption.POINTS_DESC,
                onClick = { onSelectSort(LoyaltySortOption.POINTS_DESC) }
            )
            SortOptionItem(
                title = stringResource(R.string.loyalty_sort_points_asc),
                isSelected = currentSort == LoyaltySortOption.POINTS_ASC,
                onClick = { onSelectSort(LoyaltySortOption.POINTS_ASC) }
            )
            SortOptionItem(
                title = stringResource(R.string.loyalty_sort_spend_desc),
                isSelected = currentSort == LoyaltySortOption.SPEND_DESC,
                onClick = { onSelectSort(LoyaltySortOption.SPEND_DESC) }
            )
            SortOptionItem(
                title = stringResource(R.string.loyalty_sort_recent),
                isSelected = currentSort == LoyaltySortOption.RECENTLY_ACTIVE,
                onClick = { onSelectSort(LoyaltySortOption.RECENTLY_ACTIVE) }
            )
            SortOptionItem(
                title = stringResource(R.string.loyalty_sort_name),
                isSelected = currentSort == LoyaltySortOption.NAME_ASC,
                onClick = { onSelectSort(LoyaltySortOption.NAME_ASC) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SortOptionItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * 360° Customer Profile & Points Audit Ledger Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoyaltyAuditLedgerSheet(
    member: CustomerLoyalty,
    transactions: List<LoyaltyTransaction>,
    pointCashValue: Double,
    settings: LoyaltyProgramSettings,
    onDismiss: () -> Unit,
    onQuickRedeem: () -> Unit,
    onAdjust: () -> Unit,
    onStartSale: () -> Unit,
    onCall: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    TierAvatar(tier = member.tier, name = member.name)
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(member.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TierBadgeCapsule(tier = member.tier)
                        }
                        Text(member.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onCall, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Phone, contentDescription = stringResource(R.string.customer_action_call), tint = Color(0xFF0284C7))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
            }

            // Tabs: Summary vs Ledger
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.loyalty_tab_summary), fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("${stringResource(R.string.loyalty_tab_ledger)} (${transactions.size})", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                // SUMMARY TAB
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Points Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.loyalty_current_balance), fontSize = 11.sp, color = Color(0xFF92400E))
                                Text(
                                    stringResource(R.string.loyalty_points_balance_display, member.pointsBalance),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF78350F)
                                )
                                Text(
                                    stringResource(R.string.loyalty_cash_value_display, formatTaka(member.pointsBalance * pointCashValue)),
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.loyalty_lifetime_spend_label), fontSize = 11.sp, color = Color(0xFF92400E))
                                Text(
                                    formatTaka(member.lifetimeSpent),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF78350F)
                                )
                            }
                        }
                    }

                    // Next Tier Progression Bar
                    val isPlatinum = member.tier.equals("platinum", ignoreCase = true)
                    val (nextTier, nextTarget) = when (member.tier.lowercase()) {
                        "regular" -> Pair(stringResource(R.string.tier_silver), settings.silverSpendThreshold)
                        "silver" -> Pair(stringResource(R.string.tier_gold), settings.goldSpendThreshold)
                        "gold" -> Pair(stringResource(R.string.tier_platinum), settings.platinumSpendThreshold)
                        else -> Pair(stringResource(R.string.tier_max_level_platinum), settings.platinumSpendThreshold)
                    }
                    val progress = if (nextTarget > 0) (member.lifetimeSpent / nextTarget).toFloat().coerceIn(0f, 1f) else 1f

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.loyalty_tier_progression), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                if (isPlatinum) {
                                    Text(stringResource(R.string.loyalty_highest_tier_reached), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                } else {
                                    Text(stringResource(R.string.loyalty_target_label, nextTier, formatTaka(nextTarget)), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            val remaining = (nextTarget - member.lifetimeSpent).coerceAtLeast(0.0)
                            Text(
                                if (isPlatinum || remaining <= 0.0) {
                                    stringResource(R.string.loyalty_highest_tier_reached)
                                } else {
                                    stringResource(R.string.loyalty_upgrade_prompt, formatTaka(remaining), nextTier)
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick Action buttons (Perfectly aligned with identical 46dp fixed height, padding, and icons)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Redeem Points
                        Button(
                            onClick = onQuickRedeem,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.loyalty_action_redeem_points),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 2. Adjust Points
                        OutlinedButton(
                            onClick = onAdjust,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.loyalty_action_adjust_points),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 3. Start Sale
                        Button(
                            onClick = onStartSale,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = stringResource(R.string.customer_action_start_sale),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // AUDIT LEDGER TAB
                if (transactions.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.loyalty_no_transactions), color = Color.Gray, fontSize = 12.5.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                    ) {
                        items(transactions, key = { "tx-${it.id}" }) { tx ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(tx.reason.ifBlank { tx.type }, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            dateFormat.format(Date(tx.timestamp)),
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val isPos = tx.pointsChange >= 0
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isPos) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.loyalty_pts_suffix, if (isPos) "+${tx.pointsChange}" else "${tx.pointsChange}"),
                                            color = if (isPos) Color(0xFF166534) else Color(0xFF991B1B),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
