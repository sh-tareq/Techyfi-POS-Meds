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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.example.techyfipos.theme.extendedColors
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import com.example.techyfipos.utils.getLocalizedRepositoryErrorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    isTablet: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600,
    isOwner: Boolean = false,
    canManageStaff: Boolean = isOwner,
    canEditPermissions: Boolean = isOwner,
    modifier: Modifier = Modifier
) {
    if (!canManageStaff) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            onBack()
        }
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.access_denied), style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val context = LocalContext.current
    val users by authViewModel.users.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
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
            if (canManageStaff) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text(stringResource(R.string.users_add_staff), fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        if (users.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.users_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 800.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(users, key = { it.id }) { user ->
                        val isTargetOwner = user.role == AppRoles.OWNER
                        val isSelf = currentUser?.id == user.id
                        val canEditUser = isOwner || !isTargetOwner
                        val canDeleteUser = (isOwner || !isTargetOwner) && !isSelf
                        val canToggleUserActive = (isOwner || !isTargetOwner) && !isSelf
                        val canManageUserPermissions = canEditPermissions && (isOwner || !isTargetOwner) && !isSelf

                        StaffUserCard(
                            user = user,
                            canEdit = canEditUser,
                            canDelete = canDeleteUser,
                            canToggleActive = canToggleUserActive,
                            canEditPermissions = canManageUserPermissions,
                            onEdit = { userToEdit = user },
                            onPermissions = { userForPermissions = user },
                            onToggleActive = { isActive ->
                                authViewModel.setStaffActive(user.id, isActive) { success, err ->
                                    if (!success) {
                                        val msg = getLocalizedRepositoryErrorMessage(context, err ?: onlyOwnerMessage)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDelete = { userToDelete = user }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddStaffDialog(
            authViewModel = authViewModel,
            isOwner = isOwner,
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
            isOwner = isOwner,
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
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.users_remove_title, user.name)) },
            text = { Text(stringResource(R.string.users_remove_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.deleteUser(user) { success, err ->
                            if (!success) {
                                val msg = getLocalizedRepositoryErrorMessage(context, err ?: onlyOwnerMessage)
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
    canEdit: Boolean = true,
    canDelete: Boolean = true,
    canToggleActive: Boolean = true,
    canEditPermissions: Boolean = true,
    onEdit: () -> Unit,
    onPermissions: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val roleBg = when (user.role) {
        AppRoles.OWNER -> MaterialTheme.extendedColors.warningContainer
        AppRoles.MANAGER -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.extendedColors.successContainer
    }

    val roleTextColor = when (user.role) {
        AppRoles.OWNER -> MaterialTheme.extendedColors.onWarningContainer
        AppRoles.MANAGER -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.extendedColors.onSuccessContainer
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
                                color = roleTextColor
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
                                    color = roleTextColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            if (user.role == AppRoles.CASHIER) {
                                val discFormatted = if (user.maxDiscountPercent % 1.0 == 0.0) {
                                    user.maxDiscountPercent.toInt().toString()
                                } else {
                                    user.maxDiscountPercent.toString()
                                }
                                Text(
                                    text = stringResource(R.string.users_max_discount_format, discFormatted),
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
                    enabled = canToggleActive,
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
                if (canEditPermissions) {
                    OutlinedButton(
                        onClick = onPermissions,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
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
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canEdit) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.users_edit_staff_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
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
}

@Composable
private fun AddStaffDialog(
    authViewModel: AuthViewModel,
    isOwner: Boolean = false,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(AppRoles.CASHIER) }
    var maxDiscount by remember { mutableStateOf("10") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val currentUser by authViewModel.currentUser.collectAsState()
    val callerRole = currentUser?.role ?: if (isOwner) AppRoles.OWNER else AppRoles.CASHIER
    val allowedRoles = when (callerRole) {
        AppRoles.OWNER -> listOf(AppRoles.CASHIER, AppRoles.MANAGER, AppRoles.OWNER)
        AppRoles.MANAGER -> listOf(AppRoles.CASHIER, AppRoles.MANAGER)
        else -> listOf(AppRoles.CASHIER)
    }

    val invalidUserMessage = stringResource(R.string.users_invalid_input)
    val addFailedMessage = stringResource(R.string.users_add_failed)
    val invalidDiscountMessage = stringResource(R.string.users_max_discount_invalid_error)
    val isDiscountValid = if (selectedRole == AppRoles.CASHIER) {
        val d = maxDiscount.toDoubleOrNull()
        d != null && d in 0.0..100.0
    } else true

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
                    visualTransformation = PasswordVisualTransformation(),
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
                    allowOwnerRole = callerRole == AppRoles.OWNER,
                    allowedRoles = allowedRoles,
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
                        onValueChange = { maxDiscount = it },
                        label = { Text(stringResource(R.string.users_max_discount_label)) },
                        supportingText = {
                            if (!isDiscountValid) {
                                Text(invalidDiscountMessage, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(stringResource(R.string.users_max_discount_hint))
                            }
                        },
                        isError = !isDiscountValid,
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
                enabled = name.isNotBlank() && pin.length == 4 && isDiscountValid,
                onClick = {
                    if (name.isBlank() || pin.length != 4) {
                        errorMessage = invalidUserMessage
                        return@Button
                    }
                    if (!isDiscountValid) {
                        errorMessage = invalidDiscountMessage
                        return@Button
                    }
                    val disc = if (selectedRole == AppRoles.CASHIER) (maxDiscount.toDoubleOrNull() ?: 10.0) else 10.0
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
                            errorMessage = getLocalizedRepositoryErrorMessage(context, err ?: addFailedMessage)
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
    isOwner: Boolean = false,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var editName by remember(user) { mutableStateOf(user.name) }
    var editPin by remember(user) { mutableStateOf("") }
    var editOldPin by remember(user) { mutableStateOf("") }
    var editRole by remember(user) { mutableStateOf(user.role) }
    var editActive by remember(user) { mutableStateOf(user.isActive) }
    var editDiscount by remember(user) {
        mutableStateOf(
            if (user.maxDiscountPercent % 1.0 == 0.0) user.maxDiscountPercent.toInt().toString() else user.maxDiscountPercent.toString()
        )
    }
    var editError by remember(user) { mutableStateOf<String?>(null) }
    val currentUser by authViewModel.currentUser.collectAsState()
    val isSelf = user.id == currentUser?.id
    val isEditingOwner = user.role == AppRoles.OWNER
    val callerRole = currentUser?.role ?: if (isOwner) AppRoles.OWNER else AppRoles.CASHIER
    val hasPermissionsCapability = isOwner || (currentUser != null && AppPermissions.hasPermission(currentUser!!, AppPermissions.SYSTEM_PERMISSIONS))
    val canChangeRole = !isSelf && (callerRole == AppRoles.OWNER || (!isEditingOwner && hasPermissionsCapability))
    val allowedRoles = when (callerRole) {
        AppRoles.OWNER -> listOf(AppRoles.CASHIER, AppRoles.MANAGER, AppRoles.OWNER)
        AppRoles.MANAGER -> listOf(AppRoles.CASHIER, AppRoles.MANAGER)
        else -> listOf(AppRoles.CASHIER)
    }

    val invalidUserMessage = stringResource(R.string.users_invalid_input)
    val addFailedMessage = stringResource(R.string.users_add_failed)
    val currentPinRequiredMessage = stringResource(R.string.users_current_pin_required)
    val invalidDiscountMessage = stringResource(R.string.users_max_discount_invalid_error)
    val isDiscountValid = if (editRole == AppRoles.CASHIER) {
        val d = editDiscount.toDoubleOrNull()
        d != null && d in 0.0..100.0
    } else true

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
                    placeholder = { Text(stringResource(R.string.users_pin_leave_blank_placeholder)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (editPin.isNotBlank()) {
                    OutlinedTextField(
                        value = editOldPin,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) editOldPin = it },
                        label = { Text(stringResource(R.string.users_current_pin_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = stringResource(R.string.users_role_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                RoleSelectorRow(
                    selectedRole = editRole,
                    allowOwnerRole = callerRole == AppRoles.OWNER || isEditingOwner,
                    allowedRoles = if (callerRole == AppRoles.OWNER) listOf(AppRoles.CASHIER, AppRoles.MANAGER, AppRoles.OWNER) else (if (isEditingOwner) listOf(AppRoles.OWNER) else allowedRoles),
                    enabled = canChangeRole,
                    onRoleSelected = { if (canChangeRole) editRole = it }
                )
                if (isEditingOwner && callerRole != AppRoles.OWNER) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.users_owner_role_fixed_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (editRole == AppRoles.CASHIER) {
                    OutlinedTextField(
                        value = editDiscount,
                        onValueChange = { editDiscount = it },
                        enabled = !isSelf,
                        label = { Text(stringResource(R.string.users_max_discount_label)) },
                        supportingText = {
                            if (!isDiscountValid) {
                                Text(invalidDiscountMessage, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(stringResource(R.string.users_max_discount_hint))
                            }
                        },
                        isError = !isDiscountValid,
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
                        enabled = !isSelf,
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
                enabled = editName.isNotBlank() && isDiscountValid,
                onClick = {
                    if (editName.isBlank()) {
                        editError = invalidUserMessage
                        return@Button
                    }
                    if (editPin.isNotBlank()) {
                        if (editPin.length != 4) {
                            editError = invalidUserMessage
                            return@Button
                        }
                        if (editOldPin.length != 4) {
                            editError = currentPinRequiredMessage
                            return@Button
                        }
                    }
                    if (!isDiscountValid) {
                        editError = invalidDiscountMessage
                        return@Button
                    }
                    val parsedDisc = editDiscount.toDoubleOrNull()
                    val disc = if (editRole == AppRoles.CASHIER) parsedDisc else null
                    val finalDisc = if (disc != null && kotlin.math.abs(disc - user.maxDiscountPercent) < 0.001) null else disc
                    authViewModel.updateStaff(
                        userId = user.id,
                        name = editName.trim(),
                        pin = editPin.takeIf { it.isNotBlank() },
                        oldPin = editOldPin.takeIf { it.isNotBlank() },
                        role = editRole,
                        customPermissions = null,
                        isActive = editActive,
                        maxDiscount = finalDisc
                    ) { success, err ->
                        if (success) {
                            onSuccess()
                        } else {
                            editError = getLocalizedRepositoryErrorMessage(context, err ?: addFailedMessage)
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
    allowOwnerRole: Boolean = true,
    allowedRoles: List<String>? = null,
    enabled: Boolean = true,
    onRoleSelected: (String) -> Unit
) {
    val allRoles = listOf(
        Triple(AppRoles.CASHIER, stringResource(R.string.role_cashier), MaterialTheme.extendedColors.success to MaterialTheme.extendedColors.onSuccessContainer),
        Triple(AppRoles.MANAGER, stringResource(R.string.role_manager), MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimaryContainer),
        Triple(AppRoles.OWNER, stringResource(R.string.role_owner), MaterialTheme.extendedColors.warning to MaterialTheme.extendedColors.onWarningContainer)
    )
    val roles = allRoles.filter { (role, _, _) ->
        (allowedRoles == null || allowedRoles.contains(role)) && (allowOwnerRole || role != AppRoles.OWNER)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        roles.forEach { (role, label, colors) ->
            val (accentColor, textColor) = colors
            val isSelected = selectedRole == role
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) accentColor.copy(alpha = if (enabled) 0.15f else 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, if (enabled) accentColor else accentColor.copy(alpha = 0.4f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(enabled = enabled) { onRoleSelected(role) }
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
                        color = if (isSelected) (if (enabled) textColor else textColor.copy(alpha = 0.6f)) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isCallerOwner = currentUser?.role == AppRoles.OWNER
    val callerPermissions = remember(currentUser) {
        if (currentUser != null) AppPermissions.getEffectivePermissions(currentUser) else emptySet()
    }
    val initialPermissions = remember(user) {
        AppPermissions.getEffectivePermissions(user).toMutableSet()
    }
    var currentPermissions by remember(user) {
        mutableStateOf<Set<String>>(initialPermissions)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 640.dp)
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
                        val roleLabel = when (user.role) {
                            AppRoles.OWNER -> stringResource(R.string.role_owner)
                            AppRoles.MANAGER -> stringResource(R.string.role_manager)
                            else -> stringResource(R.string.role_cashier)
                        }
                        Text(
                            text = stringResource(
                                R.string.users_role_permissions_active_format,
                                roleLabel,
                                currentPermissions.size,
                                AppPermissions.ALL_PERMISSIONS.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reset to role defaults button
                OutlinedButton(
                    onClick = {
                        val roleDefaults = AppPermissions.getDefaultPermissionsForRole(user.role)
                        currentPermissions = if (isCallerOwner) {
                            roleDefaults
                        } else {
                            roleDefaults.filter { !AppPermissions.isSystemPermission(it) && callerPermissions.contains(it) }.toSet()
                        }
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
                                isCallerOwner = isCallerOwner,
                                callerPermissions = callerPermissions,
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
                            errorMessage = null
                            authViewModel.updateStaffPermissions(user.id, currentPermissions) { success ->
                                if (success) {
                                    onSaved()
                                } else {
                                    errorMessage = context.getString(R.string.users_permissions_save_failed)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCategorySection(
    category: PermissionCategory,
    activePermissions: Set<String>,
    isCallerOwner: Boolean,
    callerPermissions: Set<String>,
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
                val canToggle = isCallerOwner || (!AppPermissions.isSystemPermission(perm.key) && callerPermissions.contains(perm.key))
                PermissionItemRow(
                    permission = perm,
                    isChecked = activePermissions.contains(perm.key),
                    enabled = canToggle,
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
    enabled: Boolean = true,
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
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
            Text(
                text = stringResource(permission.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.scale(0.85f)
        )
    }
}
