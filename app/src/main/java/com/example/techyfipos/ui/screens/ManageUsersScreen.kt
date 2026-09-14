package com.example.techyfipos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.PermissionCategory
import com.example.techyfipos.data.PermissionItem
import com.example.techyfipos.data.db.User
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    isTablet: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val users by authViewModel.users.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    var userForPermissions by remember { mutableStateOf<User?>(null) }
    var userToDelete by remember { mutableStateOf<User?>(null) }

    val onlyOwnerMessage = stringResource(R.string.users_only_owner_error)
    val cashierUpdatedMessage = stringResource(R.string.users_edit_cashier_success)

    val activeCount = users.count { it.isActive }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = stringResource(R.string.users_title),
                        metricBadge = stringResource(R.string.users_metric_badge_rbac_active),
                        badgeColor = PosSectionColors.Users,
                        isTablet = isTablet,
                        icon = { size -> Realistic3DCustomerHubIcon(size = size) }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.users_add_staff), fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (users.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.users_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    StaffUserCard(
                        user = user,
                        onEdit = { userToEdit = user },
                        onPermissions = { userForPermissions = user },
                        onToggleActive = { isActive ->
                            authViewModel.setStaffActive(user.id, isActive) { success ->
                                if (!success) {
                                    Toast.makeText(context, onlyOwnerMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = { userToDelete = user }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddStaffDialog(
            authViewModel = authViewModel,
            onDismiss = { showAddDialog = false },
            onSuccess = {
                showAddDialog = false
                Toast.makeText(context, context.getString(R.string.users_staff_added_toast), Toast.LENGTH_SHORT).show()
            }
        )
    }

    userToEdit?.let { user ->
        EditStaffDialog(
            user = user,
            authViewModel = authViewModel,
            onDismiss = { userToEdit = null },
            onSuccess = {
                userToEdit = null
                Toast.makeText(context, cashierUpdatedMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }

    userForPermissions?.let { user ->
        PermissionsMatrixDialog(
            user = user,
            authViewModel = authViewModel,
            onDismiss = { userForPermissions = null },
            onSaved = {
                userForPermissions = null
                Toast.makeText(context, context.getString(R.string.users_permissions_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.users_remove_title, user.name)) },
            text = { Text(stringResource(R.string.users_remove_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.deleteUser(user) { success ->
                            if (!success) {
                                Toast.makeText(context, onlyOwnerMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                        userToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.users_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun StaffUserCard(
    user: User,
    onEdit: () -> Unit,
    onPermissions: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val roleColor = when (user.role) {
        AppRoles.OWNER -> Color(0xFFF59E0B) // Amber
        AppRoles.MANAGER -> Color(0xFF6366F1) // Indigo
        else -> Color(0xFF10B981) // Emerald
    }

    val roleBg = when (user.role) {
        AppRoles.OWNER -> Color(0xFFFEF3C7)
        AppRoles.MANAGER -> Color(0xFFEEF2FF)
        else -> Color(0xFFECFDF5)
    }

    val roleLabel = when (user.role) {
        AppRoles.OWNER -> stringResource(R.string.role_owner)
        AppRoles.MANAGER -> stringResource(R.string.role_manager)
        else -> stringResource(R.string.role_cashier)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.isActive) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (user.isActive) 2.dp else 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar
                    Surface(
                        shape = CircleShape,
                        color = roleBg,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = user.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = roleColor
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!user.isActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = stringResource(R.string.users_status_suspended),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = roleBg
                            ) {
                                Text(
                                    text = roleLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = roleColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            if (user.role == AppRoles.CASHIER) {
                                Text(
                                    text = "Max Disc: ${user.maxDiscountPercent.toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Active Switch
                Switch(
                    checked = user.isActive,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Permissions Button
                OutlinedButton(
                    onClick = onPermissions,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.users_permissions_button),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.users_edit_staff_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.users_remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddStaffDialog(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(AppRoles.CASHIER) }
    var maxDiscount by remember { mutableStateOf("10") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidUserMessage = stringResource(R.string.users_invalid_input)
    val addFailedMessage = stringResource(R.string.users_add_failed)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.users_add_staff), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.users_staff_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text(stringResource(R.string.users_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.users_role_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                RoleSelectorRow(
                    selectedRole = selectedRole,
                    onRoleSelected = { role ->
                        selectedRole = role
                        maxDiscount = when (role) {
                            AppRoles.OWNER -> "100"
                            AppRoles.MANAGER -> "50"
                            else -> "10"
                        }
                    }
                )

                if (selectedRole == AppRoles.CASHIER) {
                    OutlinedTextField(
                        value = maxDiscount,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) maxDiscount = it },
                        label = { Text(stringResource(R.string.users_max_discount_label)) },
                        supportingText = { Text(stringResource(R.string.users_max_discount_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || pin.length != 4) {
                        errorMessage = invalidUserMessage
                        return@Button
                    }
                    val disc = maxDiscount.toDoubleOrNull() ?: 10.0
                    authViewModel.addStaff(
                        name = name.trim(),
                        pin = pin,
                        role = selectedRole,
                        customPermissions = null,
                        maxDiscount = disc
                    ) { success, err ->
                        if (success) {
                            onSuccess()
                        } else {
                            errorMessage = err ?: addFailedMessage
                        }
                    }
                }
            ) { Text(stringResource(R.string.users_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun EditStaffDialog(
    user: User,
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var editName by remember(user) { mutableStateOf(user.name) }
    var editPin by remember(user) { mutableStateOf("") }
    var editRole by remember(user) { mutableStateOf(user.role) }
    var editActive by remember(user) { mutableStateOf(user.isActive) }
    var editDiscount by remember(user) { mutableStateOf(user.maxDiscountPercent.toInt().toString()) }
    var editError by remember(user) { mutableStateOf<String?>(null) }
    val invalidUserMessage = stringResource(R.string.users_invalid_input)
    val addFailedMessage = stringResource(R.string.users_add_failed)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.users_edit_staff_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text(stringResource(R.string.users_staff_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editPin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) editPin = it },
                    label = { Text(stringResource(R.string.users_reset_pin_label)) },
                    placeholder = { Text("Leave blank to keep current PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.users_role_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                RoleSelectorRow(
                    selectedRole = editRole,
                    onRoleSelected = { editRole = it }
                )

                if (editRole == AppRoles.CASHIER) {
                    OutlinedTextField(
                        value = editDiscount,
                        onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) editDiscount = it },
                        label = { Text(stringResource(R.string.users_max_discount_label)) },
                        supportingText = { Text(stringResource(R.string.users_max_discount_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (editActive) stringResource(R.string.users_status_active) else stringResource(R.string.users_status_suspended),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Switch(
                        checked = editActive,
                        onCheckedChange = { editActive = it }
                    )
                }

                if (editError != null) {
                    Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editName.isBlank()) {
                        editError = invalidUserMessage
                        return@Button
                    }
                    if (editPin.isNotBlank() && editPin.length != 4) {
                        editError = invalidUserMessage
                        return@Button
                    }
                    val disc = editDiscount.toDoubleOrNull() ?: 10.0
                    authViewModel.updateStaff(
                        userId = user.id,
                        name = editName.trim(),
                        pin = editPin.takeIf { it.isNotBlank() },
                        role = editRole,
                        customPermissions = AppPermissions.deserializePermissions(user.customPermissions),
                        isActive = editActive,
                        maxDiscount = disc
                    ) { success, err ->
                        if (success) {
                            onSuccess()
                        } else {
                            editError = err ?: addFailedMessage
                        }
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RoleSelectorRow(
    selectedRole: String,
    onRoleSelected: (String) -> Unit
) {
    val roles = listOf(
        Triple(AppRoles.CASHIER, stringResource(R.string.role_cashier), Color(0xFF10B981)),
        Triple(AppRoles.MANAGER, stringResource(R.string.role_manager), Color(0xFF6366F1)),
        Triple(AppRoles.OWNER, stringResource(R.string.role_owner), Color(0xFFF59E0B))
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        roles.forEach { (role, label, color) ->
            val isSelected = selectedRole == role
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onRoleSelected(role) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionsMatrixDialog(
    user: User,
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val initialPermissions = remember(user) {
        AppPermissions.getEffectivePermissions(user).toMutableSet()
    }
    var currentPermissions by remember(user) {
        mutableStateOf<Set<String>>(initialPermissions)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.users_permissions_title, user.name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Role: ${user.role} · ${currentPermissions.size} / ${AppPermissions.ALL_PERMISSIONS.size} Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reset to role defaults button
                OutlinedButton(
                    onClick = {
                        currentPermissions = AppPermissions.getDefaultPermissionsForRole(user.role)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.users_permissions_reset_defaults),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()

                // Permission List by Category
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    AppPermissions.CATEGORIES.forEach { category ->
                        item(key = category.id) {
                            PermissionCategorySection(
                                category = category,
                                activePermissions = currentPermissions,
                                onPermissionToggled = { key, isEnabled ->
                                    currentPermissions = if (isEnabled) {
                                        currentPermissions + key
                                    } else {
                                        currentPermissions - key
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        onClick = {
                            authViewModel.updateStaffPermissions(user.id, currentPermissions) { success ->
                                if (success) {
                                    onSaved()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCategorySection(
    category: PermissionCategory,
    activePermissions: Set<String>,
    onPermissionToggled: (String, Boolean) -> Unit
) {
    val categoryTitle = stringResource(category.titleRes)
    val activeInCategory = category.permissions.count { activePermissions.contains(it.key) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = categoryTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$activeInCategory / ${category.permissions.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            category.permissions.forEach { perm ->
                PermissionItemRow(
                    permission = perm,
                    isChecked = activePermissions.contains(perm.key),
                    onCheckedChange = { onPermissionToggled(perm.key, it) }
                )
            }
        }
    }
}

@Composable
private fun PermissionItemRow(
    permission: com.example.techyfipos.data.PermissionItem,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = stringResource(permission.titleRes),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(permission.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f)
        )
    }
}
