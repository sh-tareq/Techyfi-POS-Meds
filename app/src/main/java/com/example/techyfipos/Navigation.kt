@file:android.annotation.SuppressLint("LocalContextConfigurationRead")

package com.example.techyfipos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.techyfipos.data.AppPermissions
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.ui.NavDisplay
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.ui.screens.*
import com.example.techyfipos.ui.viewmodel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Nav3DType {
    SELL, PRODUCTS, HISTORY, DUES, MORE, REPORT, EXPENSES, SUPPLIERS, SETTINGS, REGISTER, LOYALTY, CUSTOMERS
}

@Composable
fun Animated3DNavItemIcon(
    type: Nav3DType,
    selected: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit = if (selected) 20.sp else 17.sp,
    sellIconSize: androidx.compose.ui.unit.Dp = if (selected) 24.dp else 21.dp,
    modifier: Modifier = Modifier
) {
    val targetSize = sellIconSize
    if (selected) {
        ActiveAnimated3DNavItemIcon(
            type = type,
            targetSize = targetSize,
            modifier = modifier
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
        ) {
            Nav3DIconContent(type = type, targetSize = targetSize)
        }
    }
}

@Composable
private fun ActiveAnimated3DNavItemIcon(
    type: Nav3DType,
    targetSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nav3d_active_${type.name}")

    val layerModifier: Modifier = when (type) {
        Nav3DType.SELL, Nav3DType.EXPENSES -> {
            val floatAnim by infiniteTransition.animateFloat(
                initialValue = -3.5f,
                targetValue = 3.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "float"
            )
            Modifier.graphicsLayer { translationY = floatAnim }
        }
        Nav3DType.DUES -> {
            val floatAnim by infiniteTransition.animateFloat(
                initialValue = -2.1f,
                targetValue = 2.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "float_dues"
            )
            Modifier.graphicsLayer { translationY = floatAnim }
        }
        Nav3DType.REGISTER, Nav3DType.LOYALTY, Nav3DType.CUSTOMERS -> {
            val floatAnim by infiniteTransition.animateFloat(
                initialValue = -1.75f,
                targetValue = 1.75f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "float_sub"
            )
            Modifier.graphicsLayer { translationY = floatAnim }
        }
        Nav3DType.PRODUCTS -> {
            val wobbleAnim by infiniteTransition.animateFloat(
                initialValue = -6f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wobble"
            )
            Modifier.graphicsLayer { rotationZ = wobbleAnim }
        }
        Nav3DType.HISTORY -> {
            val wobbleAnim by infiniteTransition.animateFloat(
                initialValue = -4.2f,
                targetValue = 4.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wobble_hist"
            )
            Modifier.graphicsLayer { rotationZ = wobbleAnim }
        }
        Nav3DType.SUPPLIERS -> {
            val wobbleAnim by infiniteTransition.animateFloat(
                initialValue = -3f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wobble_sup"
            )
            Modifier.graphicsLayer { translationX = wobbleAnim }
        }
        Nav3DType.SETTINGS -> {
            val spinAnim by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spin"
            )
            Modifier.graphicsLayer { rotationZ = spinAnim }
        }
        Nav3DType.MORE, Nav3DType.REPORT -> {
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1300, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            Modifier.graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.then(layerModifier)
    ) {
        Nav3DIconContent(type = type, targetSize = targetSize)
    }
}

@Composable
private fun Nav3DIconContent(type: Nav3DType, targetSize: androidx.compose.ui.unit.Dp) {
    when (type) {
        Nav3DType.SELL -> Realistic3DSellTerminalIcon(size = targetSize)
        Nav3DType.PRODUCTS -> Realistic3DProductsBoxIcon(size = targetSize)
        Nav3DType.HISTORY -> Realistic3DHistoryClockIcon(size = targetSize)
        Nav3DType.DUES -> Realistic3DClipboardDuesIcon(size = targetSize)
        Nav3DType.MORE -> Realistic3DMoreZapIcon(size = targetSize)
        Nav3DType.REPORT -> Realistic3DReportChartIcon(size = targetSize)
        Nav3DType.EXPENSES -> Realistic3DExpensesCashIcon(size = targetSize)
        Nav3DType.SUPPLIERS -> Realistic3DSupplierTruckIcon(size = targetSize)
        Nav3DType.SETTINGS -> Realistic3DSettingsGearIcon(size = targetSize)
        Nav3DType.REGISTER -> Realistic3DRegisterReceiptIcon(size = targetSize)
        Nav3DType.LOYALTY -> Realistic3DLoyaltyStarIcon(size = targetSize)
        Nav3DType.CUSTOMERS -> Realistic3DCustomerHubIcon(size = targetSize)
    }
}

@Composable
fun LocaleWrapper(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = java.util.Locale.forLanguageTag(language)
    
    val currentSysConfig = androidx.compose.ui.platform.LocalConfiguration.current
    val config = remember(currentSysConfig.orientation, currentSysConfig.screenWidthDp, currentSysConfig.screenHeightDp, language) {
        android.content.res.Configuration(context.resources.configuration).apply {
            setTo(currentSysConfig)
            setLocale(locale)
        }
    }
    
    val localizedContext = remember(config) {
        context.createConfigurationContext(config)
    }
    
    val registryOwner = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is androidx.activity.result.ActivityResultRegistryOwner) {
                break
            }
            ctx = ctx.baseContext
        }
        ctx as? androidx.activity.result.ActivityResultRegistryOwner
    }

    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) break
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }

    val locals = buildList {
        add(com.example.techyfipos.utils.LocalAppLanguage provides language)
        add(LocalContext provides localizedContext)
        add(androidx.compose.ui.platform.LocalConfiguration provides config)
        if (registryOwner != null) {
            add(androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner)
        }
        if (activity != null) {
            add(androidx.activity.compose.LocalActivity provides activity)
        }
    }.toTypedArray()

    androidx.compose.runtime.SideEffect {
        com.example.techyfipos.utils.setAppLanguage(language)
    }

    CompositionLocalProvider(*locals) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val context = LocalContext.current.applicationContext as TechyfiPOSApplication
    val repository = context.repository
    val factory = remember { POSViewModelFactory(repository) }

    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val currentLang by settingsViewModel.language.collectAsStateWithLifecycle("en")
    val uiLayoutMode by settingsViewModel.uiLayoutMode.collectAsStateWithLifecycle("AUTO")

    LocaleWrapper(language = currentLang) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val isTablet = when (uiLayoutMode) {
            "TABLET" -> true
            "PHONE" -> false
            else -> screenWidthDp >= 600
        }
        val ownerOnlyReportsText = stringResource(R.string.owner_only_reports)
        val ownerOnlyExpensesText = stringResource(R.string.owner_only_expenses)
        val ownerOnlySuppliersText = stringResource(R.string.owner_only_suppliers)
        val ownerOnlySettingsText = stringResource(R.string.owner_only_settings)
        val ownerOnlyRegisterCloseText = stringResource(R.string.owner_only_register_close)
        val ownerOnlyLoyaltyText = stringResource(R.string.owner_only_loyalty)
        val ownerOnlyCustomersText = stringResource(R.string.owner_only_customers)
        val ownerOnlyDuesText = stringResource(R.string.owner_only_dues)
        val backStack = rememberNavBackStack(Splash)
        val scope = rememberCoroutineScope()

        val scannerViewModel: ScannerViewModel = viewModel(factory = factory)
        val cartViewModel: CartViewModel = viewModel(factory = factory)
        val productsViewModel: ProductsViewModel = viewModel(factory = factory)
        val salesViewModel: SalesViewModel = viewModel(factory = factory)
        val duesViewModel: DuesViewModel = viewModel(factory = factory)
        val expenseViewModel: ExpenseViewModel = viewModel(factory = factory)
        val supplierViewModel: SupplierViewModel = viewModel(factory = factory)
        val registerClosingViewModel: RegisterClosingViewModel = viewModel(factory = factory)
        val loyaltyViewModel: LoyaltyViewModel = viewModel(factory = factory)
        val customerViewModel: CustomerViewModel = viewModel(factory = factory)
        val authViewModel: AuthViewModel = viewModel(factory = factory)
        val isOwner by authViewModel.isOwner.collectAsStateWithLifecycle()
        val isSupervisor by authViewModel.isSupervisor.collectAsStateWithLifecycle()
        val activePermissions by authViewModel.activePermissions.collectAsStateWithLifecycle()
        val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()

        var prefilledBarcodeForProductScreen by remember { mutableStateOf<String?>(null) }

        val currentKey = backStack.lastOrNull()
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestCurrentKey by rememberUpdatedState(currentKey)
        var backgroundedAt by rememberSaveable { mutableLongStateOf(0L) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> backgroundedAt = System.currentTimeMillis()
                    Lifecycle.Event.ON_START -> {
                        val shouldLock = backgroundedAt > 0L &&
                            System.currentTimeMillis() - backgroundedAt >= 5 * 60_000L &&
                            latestCurrentKey != Splash && latestCurrentKey != Lock
                        if (shouldLock) {
                            authViewModel.logout()
                            com.example.techyfipos.utils.HardwareBarcodeScanner.clearPendingInput()
                            while (backStack.isNotEmpty()) backStack.removeLastOrNull()
                            backStack.add(Lock)
                        }
                        backgroundedAt = 0L
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Pressed animation states for each tab
        var pressedTab by remember { mutableStateOf<Nav3DType?>(null) }

        val pendingNavDestination by MainActivity.pendingNavigationDestination.collectAsStateWithLifecycle()
        LaunchedEffect(pendingNavDestination, currentUser, isOwner, isSupervisor, activePermissions) {
            val dest = pendingNavDestination
            if (dest != null && currentUser != null && backStack.lastOrNull() != Splash && backStack.lastOrNull() != Lock) {
                MainActivity.pendingNavigationDestination.value = null
                val targetKey = when (dest.lowercase().trim()) {
                    "dues" -> {
                        val canViewDues = isOwner || isSupervisor || activePermissions.contains(AppPermissions.DUES_VIEW)
                        if (canViewDues) Dues else Scanner
                    }
                    "customers" -> {
                        val canCustomers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.CUSTOMERS_VIEW)
                        if (canCustomers) Customers else Scanner
                    }
                    "sales_history", "saleshistory", "sales" -> SalesHistory
                    "products" -> Products
                    "expenses" -> {
                        val canExpense = isOwner || isSupervisor || activePermissions.contains(AppPermissions.EXPENSES_MANAGE)
                        if (canExpense) Expenses else Scanner
                    }
                    "suppliers" -> {
                        val canSuppliers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SUPPLIERS_MANAGE)
                        if (canSuppliers) Suppliers else Scanner
                    }
                    "reports", "sales_report", "salesreport" -> {
                        val canReport = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_VIEW_DAILY) || activePermissions.contains(AppPermissions.REPORTS_VIEW_PNL)
                        if (canReport) SalesReport else Scanner
                    }
                    "settings" -> {
                        val canSettings = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_SHOP_SETTINGS)
                        if (canSettings) Settings else Scanner
                    }
                    "register_close", "registerclose" -> {
                        val canClose = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)
                        if (canClose) RegisterClose else Scanner
                    }
                    "loyalty" -> {
                        val canLoyalty = isOwner || isSupervisor || activePermissions.contains(AppPermissions.LOYALTY_MANAGE)
                        if (canLoyalty) Loyalty else Scanner
                    }
                    "manage_users", "users", "staff" -> {
                        if (isOwner) ManageUsers else Scanner
                    }
                    else -> null
                }
                if (targetKey != null) {
                    while (backStack.size > 1) { backStack.removeLastOrNull() }
                    if (backStack.lastOrNull() != targetKey) {
                        backStack.add(targetKey)
                    }
                }
            }
        }

        fun triggerTap(type: Nav3DType, action: () -> Unit) {
            scope.launch {
                pressedTab = type
                delay(120)
                pressedTab = null
                action()
            }
        }

        var showMoreHubSheet by remember { mutableStateOf(false) }
        var showUserDropdown by remember { mutableStateOf(false) }
        var showLogoutConfirmDialog by remember { mutableStateOf(false) }

        BackHandler(enabled = showMoreHubSheet || (backStack.size > 1 && currentKey != Splash && currentKey != Lock)) {
            if (showMoreHubSheet) {
                showMoreHubSheet = false
            } else {
                backStack.removeLastOrNull()
            }
        }

        Scaffold(
            bottomBar = {
                // Show bottom bar for core main sections on phone ONLY
                if (!isTablet && (currentKey == Scanner || currentKey == Products || currentKey == SalesHistory || currentKey == SalesReport || currentKey == Dues || currentKey == Settings || currentKey == Expenses || currentKey == Suppliers || currentKey == RegisterClose || currentKey == Loyalty || currentKey == Customers)) {
                    val isMoreActive = currentKey in listOf(SalesReport, Expenses, Suppliers, Settings, RegisterClose, Loyalty, Customers)

                    NavigationBar {
                        listOf(
                            Triple(Nav3DType.SELL, currentKey == Scanner, stringResource(R.string.sell_mode)) to {
                                while (backStack.size > 1) { backStack.removeLastOrNull() }
                            },
                            Triple(Nav3DType.PRODUCTS, currentKey == Products, stringResource(R.string.products)) to {
                                val canViewInventory = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_VIEW)
                                if (canViewInventory) {
                                    while (backStack.size > 1) { backStack.removeLastOrNull() }
                                    if (backStack.lastOrNull() != Products) { backStack.add(Products) }
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(R.string.inventory_permission_required), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            Triple(Nav3DType.HISTORY, currentKey == SalesHistory, stringResource(R.string.sales_history)) to {
                                while (backStack.size > 1) { backStack.removeLastOrNull() }
                                if (backStack.lastOrNull() != SalesHistory) { backStack.add(SalesHistory) }
                            },
                            Triple(Nav3DType.DUES, currentKey == Dues, stringResource(R.string.dues_list)) to {
                                val canViewDues = isOwner || isSupervisor || activePermissions.contains(AppPermissions.DUES_VIEW)
                                if (canViewDues) {
                                    while (backStack.size > 1) { backStack.removeLastOrNull() }
                                    if (backStack.lastOrNull() != Dues) { backStack.add(Dues) }
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(R.string.dues_permission_required), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            Triple(Nav3DType.MORE, isMoreActive, stringResource(R.string.more_hub)) to {
                                showMoreHubSheet = true
                            }
                        ).forEach { (info, onClickAction) ->
                            val (type, isSelected, labelText) = info
                            val isPressed = pressedTab == type
                            val scale by animateFloatAsState(
                                targetValue = if (isPressed) 0.82f else 1.0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "navScale_${type.name}"
                            )

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { triggerTap(type, onClickAction) },
                                icon = {
                                    Animated3DNavItemIcon(
                                        type = type,
                                        selected = isSelected,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                    )
                                },
                                label = {
                                    Text(
                                        text = labelText,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (showMoreHubSheet) {
                MoreOperationsHubSheet(
                    onDismiss = { showMoreHubSheet = false },
                    onNavigateToReport = {
                        showMoreHubSheet = false
                        val canReport = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_VIEW_DAILY) || activePermissions.contains(AppPermissions.REPORTS_VIEW_PNL)
                        if (canReport) {
                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                            if (backStack.lastOrNull() != SalesReport) { backStack.add(SalesReport) }
                        } else {
                            android.widget.Toast.makeText(context, ownerOnlyReportsText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateToExpenses = {
                        showMoreHubSheet = false
                        val canExpense = isOwner || isSupervisor || activePermissions.contains(AppPermissions.EXPENSES_MANAGE)
                        if (canExpense) {
                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                            if (backStack.lastOrNull() != Expenses) { backStack.add(Expenses) }
                        } else {
                            android.widget.Toast.makeText(context, ownerOnlyExpensesText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateToSuppliers = {
                        showMoreHubSheet = false
                        val canSuppliers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SUPPLIERS_MANAGE)
                        if (canSuppliers) {
                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                            if (backStack.lastOrNull() != Suppliers) { backStack.add(Suppliers) }
                        } else {
                            android.widget.Toast.makeText(context, ownerOnlySuppliersText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateToSettings = {
                        showMoreHubSheet = false
                        val canSettings = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_SHOP_SETTINGS)
                        if (canSettings) {
                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                            if (backStack.lastOrNull() != Settings) { backStack.add(Settings) }
                        } else {
                            android.widget.Toast.makeText(context, ownerOnlySettingsText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateToRegisterClose = {
                        showMoreHubSheet = false
                        val canClose = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)
                        if (canClose) {
                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                            if (backStack.lastOrNull() != RegisterClose) { backStack.add(RegisterClose) }
                        } else {
                            android.widget.Toast.makeText(context, ownerOnlyRegisterCloseText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateToLoyalty = {
                        showMoreHubSheet = false
                        val canLoyalty = isOwner || isSupervisor || activePermissions.contains(AppPermissions.LOYALTY_MANAGE)
                        if (canLoyalty) {
                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                            if (backStack.lastOrNull() != Loyalty) { backStack.add(Loyalty) }
                        } else {
                            android.widget.Toast.makeText(context, ownerOnlyLoyaltyText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onNavigateToCustomers = {
                        showMoreHubSheet = false
                        val canCustomers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.CUSTOMERS_VIEW)
                        if (canCustomers) {
                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                            if (backStack.lastOrNull() != Customers) { backStack.add(Customers) }
                        } else {
                            android.widget.Toast.makeText(context, ownerOnlyCustomersText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    isOwner = isOwner,
                    isSupervisor = isSupervisor,
                    activePermissions = activePermissions,
                    currentKey = currentKey,
                    isTablet = isTablet,
                    language = currentLang
                )
            }

            if (showLogoutConfirmDialog) {
                val user = currentUser
                val displayName = user?.name?.ifBlank { user.role } ?: stringResource(R.string.default_user_label)
                AlertDialog(
                    onDismissRequest = { showLogoutConfirmDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.logout_confirm_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.logout_confirm_message, displayName)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLogoutConfirmDialog = false
                                authViewModel.logout()
                                while (backStack.size > 0) { backStack.removeLastOrNull() }
                                backStack.add(Lock)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                text = stringResource(R.string.logout_confirm_button),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showLogoutConfirmDialog = false }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    }
                )
            }

            val isCoreScreen = currentKey in listOf(Scanner, Products, SalesHistory, SalesReport, Dues, Settings, Expenses, Suppliers, RegisterClose, Loyalty, Customers, Cart)
            val layoutDirection = LocalLayoutDirection.current
            val topInset = innerPadding.calculateTopPadding()
            val bottomInset = innerPadding.calculateBottomPadding()
            val startInset = innerPadding.calculateStartPadding(layoutDirection)
            val endInset = innerPadding.calculateEndPadding(layoutDirection)

            val contentPadding = if (isTablet) {
                PaddingValues(
                    start = if (isCoreScreen) 0.dp else startInset,
                    top = topInset,
                    end = endInset,
                    bottom = bottomInset
                )
            } else {
                innerPadding
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                if (isTablet && isCoreScreen) {
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val dividerColor = if (isDark) Color(0x1FFFFFFF) else Color(0xFFE2E8F0)
                    val brandEmerald = Color(0xFF10B981)
                    val railBg = if (isDark) Color(0xFF0E131F) else Color(0xFFFFFFFF)
                    val unselectedColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

                    NavigationRail(
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight()
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(size.width - strokeWidth / 2, 0f),
                                    end = Offset(size.width - strokeWidth / 2, size.height),
                                    strokeWidth = strokeWidth
                                )
                            },
                        containerColor = railBg,
                        header = {
                            Box(
                                modifier = Modifier
                                    .padding(
                                        top = if (topInset > 0.dp) topInset + 4.dp else 8.dp,
                                        bottom = 4.dp
                                    )
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                        )
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        brush = Brush.linearGradient(
                                            listOf(brandEmerald, Color(0xFF34D399), Color(0xFF1E293B))
                                        ),
                                        shape = RoundedCornerShape(11.dp)
                                    )
                                    .clickable {
                                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                    contentDescription = stringResource(R.string.app_name),
                                    modifier = Modifier
                                        .fillMaxSize(0.92f)
                                        .graphicsLayer {
                                            scaleX = 1.15f
                                            scaleY = 1.15f
                                        }
                                )
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(bottom = if (bottomInset > 0.dp) bottomInset + 4.dp else 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            val railScrollState = rememberScrollState()
                            LaunchedEffect(Unit) {
                                railScrollState.scrollTo(0)
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(railScrollState),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val navItems = listOf(
                                    Triple(Nav3DType.SELL, currentKey == Scanner || currentKey == Cart, stringResource(R.string.sell_mode)) to {
                                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                                    },
                                    Triple(Nav3DType.PRODUCTS, currentKey == Products, stringResource(R.string.products)) to {
                                        val canViewInventory = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_VIEW)
                                        if (canViewInventory) {
                                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                                            if (backStack.lastOrNull() != Products) { backStack.add(Products) }
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.inventory_permission_required), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    Triple(Nav3DType.HISTORY, currentKey == SalesHistory, stringResource(R.string.sales_history)) to {
                                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                                        if (backStack.lastOrNull() != SalesHistory) { backStack.add(SalesHistory) }
                                    },
                                    Triple(Nav3DType.DUES, currentKey == Dues, stringResource(R.string.dues_list)) to {
                                        val canViewDues = isOwner || isSupervisor || activePermissions.contains(AppPermissions.DUES_VIEW)
                                        if (canViewDues) {
                                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                                            if (backStack.lastOrNull() != Dues) { backStack.add(Dues) }
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.dues_permission_required), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    Triple(Nav3DType.EXPENSES, currentKey == Expenses, stringResource(R.string.expenses)) to {
                                        val canExpense = isOwner || isSupervisor || activePermissions.contains(AppPermissions.EXPENSES_MANAGE)
                                        if (canExpense) {
                                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                                            if (backStack.lastOrNull() != Expenses) { backStack.add(Expenses) }
                                        } else {
                                            android.widget.Toast.makeText(context, ownerOnlyExpensesText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    Triple(Nav3DType.REPORT, currentKey == SalesReport, stringResource(R.string.sales_report_section)) to {
                                        val canReport = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_VIEW_DAILY) || activePermissions.contains(AppPermissions.REPORTS_VIEW_PNL)
                                        if (canReport) {
                                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                                            if (backStack.lastOrNull() != SalesReport) { backStack.add(SalesReport) }
                                        } else {
                                            android.widget.Toast.makeText(context, ownerOnlyReportsText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    Triple(Nav3DType.SETTINGS, currentKey == Settings, stringResource(R.string.settings)) to {
                                        val canSettings = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_SHOP_SETTINGS)
                                        if (canSettings) {
                                            while (backStack.size > 1) { backStack.removeLastOrNull() }
                                            if (backStack.lastOrNull() != Settings) { backStack.add(Settings) }
                                        } else {
                                            android.widget.Toast.makeText(context, ownerOnlySettingsText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    Triple(Nav3DType.MORE, currentKey in listOf(Suppliers, Loyalty, Customers, RegisterClose), stringResource(R.string.more_hub)) to {
                                        showMoreHubSheet = true
                                    }
                                )

                                navItems.forEachIndexed { index, (info, onClickAction) ->
                                    val (type, isSelected, labelText) = info
                                    val isPressed = pressedTab == type
                                    val scale by animateFloatAsState(
                                        targetValue = if (isPressed) 0.88f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "railScale_${type.name}"
                                    )

                                    if (index == 4) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
                                            thickness = 0.8.dp,
                                            color = dividerColor
                                        )
                                    }

                                    NavigationRailItem(
                                        selected = isSelected,
                                        onClick = { triggerTap(type, onClickAction) },
                                        icon = {
                                            Animated3DNavItemIcon(
                                                type = type,
                                                selected = isSelected,
                                                sellIconSize = if (isSelected) 26.dp else 24.dp,
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = labelText,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                letterSpacing = 0.15.sp
                                            )
                                        },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = Color(0xFF10B981),
                                            selectedTextColor = Color(0xFF10B981),
                                            indicatorColor = if (isDark) Color(0x2410B981) else Color(0x1810B981),
                                            unselectedIconColor = unselectedColor,
                                            unselectedTextColor = unselectedColor
                                        ),
                                        modifier = Modifier.padding(vertical = 0.dp)
                                    )
                                }
                            }

                            // Bottom Utility Section: Cashier Shift Avatar & 1-tap Lock
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .fillMaxWidth()
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
                                    thickness = 0.8.dp,
                                    color = dividerColor
                                )

                                val user = currentUser
                                val displayName = user?.name?.ifBlank { user.role } ?: stringResource(R.string.sidebar_badge_cashier)
                                val initials = (user?.name?.ifBlank { user.role } ?: "POS").take(2).uppercase()
                                val roleBadge = if (user != null) {
                                    when (user.role.uppercase()) {
                                        "OWNER" -> stringResource(R.string.sidebar_badge_owner)
                                        "MANAGER" -> stringResource(R.string.sidebar_badge_manager)
                                        "CASHIER" -> stringResource(R.string.sidebar_badge_cashier)
                                        else -> user.role
                                    }
                                } else {
                                    stringResource(R.string.sidebar_badge_staff)
                                }

                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = if (isDark) 0.18f else 0.12f))
                                            .border(1.2.dp, Color(0xFF10B981).copy(alpha = if (isDark) 0.5f else 0.4f), CircleShape)
                                            .clickable { showUserDropdown = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showUserDropdown,
                                        onDismissRequest = { showUserDropdown = false },
                                        offset = DpOffset(x = 64.dp, y = (-20).dp),
                                        modifier = Modifier
                                            .widthIn(min = 230.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                                        .border(1.dp, Color(0xFF10B981), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = initials,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = displayName,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = roleBadge,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF10B981),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                            thickness = 0.8.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = stringResource(R.string.sidebar_lock_terminal_title),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.sidebar_lock_terminal_desc),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                showUserDropdown = false
                                                while (backStack.size > 0) { backStack.removeLastOrNull() }
                                                backStack.add(Lock)
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = stringResource(R.string.sidebar_logout_title),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.sidebar_logout_desc),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                                                    )
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            onClick = {
                                                showUserDropdown = false
                                                showLogoutConfirmDialog = true
                                            }
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        while (backStack.size > 0) { backStack.removeLastOrNull() }
                                        backStack.add(Lock)
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = stringResource(R.string.sidebar_lock_terminal_title),
                                        tint = unselectedColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        onBack = {
                            if (backStack.lastOrNull() != Lock) {
                                backStack.removeLastOrNull()
                            }
                        },
                        entryProvider = entryProvider {
                            entry<Splash> {
                                SplashScreen(
                                    isTablet = isTablet,
                                    onSplashFinished = {
                                        while (backStack.size > 0) {
                                            backStack.removeLastOrNull()
                                        }
                                        backStack.add(Lock)
                                    }
                                )
                            }
                            entry<Lock> {
                                LockScreen(
                                    authViewModel = authViewModel,
                                    onUnlocked = {
                                        while (backStack.size > 0) {
                                            backStack.removeLastOrNull()
                                        }
                                        val dest = MainActivity.pendingNavigationDestination.value
                                        MainActivity.pendingNavigationDestination.value = null
                                        val targetKey = when (dest?.lowercase()?.trim()) {
                                            "dues" -> {
                                                val canViewDues = isOwner || isSupervisor || activePermissions.contains(AppPermissions.DUES_VIEW)
                                                if (canViewDues) Dues else Scanner
                                            }
                                            "customers" -> {
                                                val canCustomers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.CUSTOMERS_VIEW)
                                                if (canCustomers) Customers else Scanner
                                            }
                                            "sales_history", "saleshistory", "sales" -> SalesHistory
                                            "products" -> Products
                                            "expenses" -> {
                                                val canExpense = isOwner || isSupervisor || activePermissions.contains(AppPermissions.EXPENSES_MANAGE)
                                                if (canExpense) Expenses else Scanner
                                            }
                                            "suppliers" -> {
                                                val canSuppliers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SUPPLIERS_MANAGE)
                                                if (canSuppliers) Suppliers else Scanner
                                            }
                                            "reports", "sales_report", "salesreport" -> {
                                                val canReport = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_VIEW_DAILY) || activePermissions.contains(AppPermissions.REPORTS_VIEW_PNL)
                                                if (canReport) SalesReport else Scanner
                                            }
                                            "settings" -> {
                                                val canSettings = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_SHOP_SETTINGS)
                                                if (canSettings) Settings else Scanner
                                            }
                                            "register_close", "registerclose" -> {
                                                val canClose = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)
                                                if (canClose) RegisterClose else Scanner
                                            }
                                            "loyalty" -> {
                                                val canLoyalty = isOwner || isSupervisor || activePermissions.contains(AppPermissions.LOYALTY_MANAGE)
                                                if (canLoyalty) Loyalty else Scanner
                                            }
                                            "manage_users", "users", "staff" -> {
                                                if (isOwner) ManageUsers else Scanner
                                            }
                                            else -> null
                                        }
                                        if (targetKey != null) {
                                            backStack.add(targetKey)
                                        } else {
                                            backStack.add(Scanner)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            entry<ManageUsers> {
                                val canManageStaff = isOwner
                                if (!canManageStaff) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.user_management_permission_required), android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                ManageUsersScreen(
                                    authViewModel = authViewModel,
                                    onBack = { backStack.removeLastOrNull() },
                                    isTablet = isTablet,
                                    isOwner = isOwner,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Scanner> {
                                ScannerScreen(
                                    scannerViewModel = scannerViewModel,
                                    cartViewModel = cartViewModel,
                                    productsViewModel = productsViewModel,
                                    settingsViewModel = settingsViewModel,
                                    authViewModel = authViewModel,
                                    isTablet = isTablet,
                                    isOwner = isOwner,
                                    isSupervisor = isSupervisor,
                                    activePermissions = activePermissions,
                                    canCheckStock = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_ADD_EDIT),
                                    onNavigateToCart = { backStack.add(Cart) },
                                    onNavigateToSettings = { backStack.add(Settings) },
                                    onNavigateToProducts = { barcode ->
                                        prefilledBarcodeForProductScreen = barcode
                                        backStack.add(Products)
                                    },
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Cart> {
                                CartScreen(
                                    cartViewModel = cartViewModel,
                                    settingsViewModel = settingsViewModel,
                                    salesViewModel = salesViewModel,
                                    authViewModel = authViewModel,
                                    isOwner = isOwner,
                                    isSupervisor = isSupervisor,
                                    activePermissions = activePermissions,
                                    onBack = { backStack.removeLastOrNull() },
                                    onNavigateToProducts = { barcode ->
                                        prefilledBarcodeForProductScreen = barcode
                                        backStack.add(Products)
                                    },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Products> {
                                val code = prefilledBarcodeForProductScreen
                                DisposableEffect(Unit) {
                                    onDispose { prefilledBarcodeForProductScreen = null }
                                }
                                ProductsScreen(
                                    productsViewModel = productsViewModel,
                                    cartViewModel = cartViewModel,
                                    prefilledBarcode = code,
                                    isOwner = isOwner,
                                    canViewCosts = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_VIEW_COSTS),
                                    canEdit = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_ADD_EDIT),
                                    canDelete = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_DELETE),
                                    canAdjustStock = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_ADJUST_STOCK),
                                    canPrintLabels = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_PRINT_LABELS),
                                    onBack = { backStack.removeLastOrNull() },
                                    onNavigateToCart = { backStack.add(Cart) },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<SalesHistory> {
                                SalesHistoryScreen(
                                    salesViewModel = salesViewModel,
                                    settingsViewModel = settingsViewModel,
                                    cartViewModel = cartViewModel,
                                    authViewModel = authViewModel,
                                    isOwner = isOwner,
                                    isSupervisor = isSupervisor,
                                    activePermissions = activePermissions,
                                    onBack = { backStack.removeLastOrNull() },
                                    onNavigateToReport = {
                                        val canReport = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_VIEW_DAILY) || activePermissions.contains(AppPermissions.REPORTS_VIEW_PNL)
                                        if (canReport) {
                                            if (backStack.lastOrNull() != SalesReport) {
                                                backStack.add(SalesReport)
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, ownerOnlyReportsText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onNavigateToCart = { backStack.add(Cart) },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<SalesReport> {
                                val canReport = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_VIEW_DAILY) || activePermissions.contains(AppPermissions.REPORTS_VIEW_PNL)
                                if (!canReport) {
                                    android.widget.Toast.makeText(context, ownerOnlyReportsText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                SalesReportScreen(
                                    salesViewModel = salesViewModel,
                                    expenseViewModel = expenseViewModel,
                                    supplierViewModel = supplierViewModel,
                                    isTablet = isTablet,
                                    onNavigateToExpenses = {
                                        val canExpense = isOwner || isSupervisor || activePermissions.contains(AppPermissions.EXPENSES_MANAGE)
                                        if (canExpense) {
                                            if (backStack.lastOrNull() != Expenses) { backStack.add(Expenses) }
                                        } else {
                                            android.widget.Toast.makeText(context, ownerOnlyExpensesText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onNavigateToSuppliers = {
                                        val canSuppliers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SUPPLIERS_MANAGE)
                                        if (canSuppliers) {
                                            if (backStack.lastOrNull() != Suppliers) { backStack.add(Suppliers) }
                                        } else {
                                            android.widget.Toast.makeText(context, ownerOnlySuppliersText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onBack = { backStack.removeLastOrNull() },
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Expenses> {
                                val canExpense = isOwner || isSupervisor || activePermissions.contains(AppPermissions.EXPENSES_MANAGE)
                                if (!canExpense) {
                                    android.widget.Toast.makeText(context, ownerOnlyExpensesText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                ExpensesScreen(
                                    expenseViewModel = expenseViewModel,
                                    onBack = { backStack.removeLastOrNull() },
                                    onNavigateToDayStartClose = {
                                        if (isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)) {
                                            if (backStack.lastOrNull() != RegisterClose) { backStack.add(RegisterClose) }
                                        } else {
                                            android.widget.Toast.makeText(context, ownerOnlyRegisterCloseText, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Suppliers> {
                                val canSuppliers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SUPPLIERS_MANAGE)
                                if (!canSuppliers) {
                                    android.widget.Toast.makeText(context, ownerOnlySuppliersText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                SuppliersScreen(
                                    supplierViewModel = supplierViewModel,
                                    onBack = { backStack.removeLastOrNull() },
                                    isOwner = isOwner,
                                    isSupervisor = isSupervisor,
                                    activePermissions = activePermissions,
                                    authViewModel = authViewModel,
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Dues> {
                                val canViewDues = isOwner || isSupervisor || activePermissions.contains(AppPermissions.DUES_VIEW)
                                if (!canViewDues) {
                                    android.widget.Toast.makeText(context, ownerOnlyDuesText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                DuesScreen(
                                    duesViewModel = duesViewModel,
                                    customerViewModel = customerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    authViewModel = authViewModel,
                                    isOwner = isOwner,
                                    isSupervisor = isSupervisor,
                                    activePermissions = activePermissions,
                                    onBack = { backStack.removeLastOrNull() },
                                    onStartSaleForCustomer = { customer ->
                                        cartViewModel.onCustomerPhoneChanged(customer.phone)
                                        cartViewModel.setCustomerName(customer.name)
                                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                                        backStack.add(Scanner)
                                        android.widget.Toast.makeText(context, context.getString(R.string.sale_started_for_customer, customer.name), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Settings> {
                                val canSettings = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_SHOP_SETTINGS)
                                if (!canSettings) {
                                    android.widget.Toast.makeText(context, ownerOnlySettingsText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                SettingsScreen(
                                    settingsViewModel = settingsViewModel,
                                    isOwner = isOwner,
                                    onBack = { backStack.removeLastOrNull() },
                                    onExpensesClick = { backStack.add(Expenses) },
                                    onSuppliersClick = { backStack.add(Suppliers) },
                                    onManageUsersClick = {
                                        val canManageStaff = isOwner
                                        if (canManageStaff) {
                                            backStack.add(ManageUsers)
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.user_management_permission_required), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLogout = {
                                        authViewModel.logout()
                                        while (backStack.size > 0) { backStack.removeLastOrNull() }
                                        backStack.add(Lock)
                                    },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<RegisterClose> {
                                val canClose = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)
                                if (!canClose) {
                                    android.widget.Toast.makeText(context, ownerOnlyRegisterCloseText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                RegisterCloseScreen(
                                    registerClosingViewModel = registerClosingViewModel,
                                    expenseViewModel = expenseViewModel,
                                    salesViewModel = salesViewModel,
                                    onBack = { backStack.removeLastOrNull() },
                                    onNavigateToExpenses = {
                                        if (backStack.lastOrNull() != Expenses) { backStack.add(Expenses) }
                                    },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Loyalty> {
                                val canLoyalty = isOwner || isSupervisor || activePermissions.contains(AppPermissions.LOYALTY_MANAGE)
                                if (!canLoyalty) {
                                    android.widget.Toast.makeText(context, ownerOnlyLoyaltyText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                LoyaltyScreen(
                                    loyaltyViewModel = loyaltyViewModel,
                                    customerViewModel = customerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    authViewModel = authViewModel,
                                    onBack = { backStack.removeLastOrNull() },
                                    onStartSaleForCustomer = { customer ->
                                        cartViewModel.onCustomerPhoneChanged(customer.phone)
                                        cartViewModel.setCustomerName(customer.name)
                                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                                        backStack.add(Scanner)
                                        android.widget.Toast.makeText(context, context.getString(R.string.sale_started_for_customer, customer.name), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                            entry<Customers> {
                                val canCustomers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.CUSTOMERS_VIEW)
                                if (!canCustomers) {
                                    android.widget.Toast.makeText(context, ownerOnlyCustomersText, android.widget.Toast.LENGTH_SHORT).show()
                                    backStack.removeLastOrNull()
                                    return@entry
                                }
                                CustomersScreen(
                                    customerViewModel = customerViewModel,
                                    settingsViewModel = settingsViewModel,
                                    authViewModel = authViewModel,
                                    onBack = { backStack.removeLastOrNull() },
                                    onStartSaleForCustomer = { customer ->
                                        cartViewModel.onCustomerPhoneChanged(customer.phone)
                                        cartViewModel.setCustomerName(customer.name)
                                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                                        backStack.add(Scanner)
                                        android.widget.Toast.makeText(context, context.getString(R.string.sale_started_for_customer, customer.name), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    isTablet = isTablet,
                                    modifier = Modifier.padding(contentPadding)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

enum class OperationCategory {
    ALL, SHIFT, CRM, MANAGEMENT
}

data class OperationHubItem(
    val title: String,
    val subtitle: String,
    val navType: Nav3DType,
    val badge: String,
    val badgeColor: Color,
    val isSelected: Boolean,
    val category: OperationCategory = OperationCategory.ALL,
    val isLocked: Boolean = false,
    val lockedMessage: String? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOperationsHubSheet(
    onDismiss: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRegisterClose: () -> Unit,
    onNavigateToLoyalty: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    isOwner: Boolean,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    currentKey: Any?,
    isTablet: Boolean = false,
    language: String = "en"
) {
    if (isTablet) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth(0.94f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(vertical = 20.dp)
                ) {
                    LocaleWrapper(language = language) {
                        MoreOperationsHubContent(
                            onDismiss = onDismiss,
                            onNavigateToReport = onNavigateToReport,
                            onNavigateToExpenses = onNavigateToExpenses,
                            onNavigateToSuppliers = onNavigateToSuppliers,
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToRegisterClose = onNavigateToRegisterClose,
                            onNavigateToLoyalty = onNavigateToLoyalty,
                            onNavigateToCustomers = onNavigateToCustomers,
                            isOwner = isOwner,
                            isSupervisor = isSupervisor,
                            activePermissions = activePermissions,
                            currentKey = currentKey,
                            isTablet = true,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }
        ) {
            LocaleWrapper(language = language) {
                MoreOperationsHubContent(
                    onDismiss = onDismiss,
                    onNavigateToReport = onNavigateToReport,
                    onNavigateToExpenses = onNavigateToExpenses,
                    onNavigateToSuppliers = onNavigateToSuppliers,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToRegisterClose = onNavigateToRegisterClose,
                    onNavigateToLoyalty = onNavigateToLoyalty,
                    onNavigateToCustomers = onNavigateToCustomers,
                    isOwner = isOwner,
                    isSupervisor = isSupervisor,
                    activePermissions = activePermissions,
                    currentKey = currentKey,
                    isTablet = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreOperationsHubContent(
    onDismiss: () -> Unit,
    onNavigateToReport: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToSuppliers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRegisterClose: () -> Unit,
    onNavigateToLoyalty: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    isOwner: Boolean,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    currentKey: Any?,
    isTablet: Boolean,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(OperationCategory.ALL) }
    var isGridView by rememberSaveable { mutableStateOf(isTablet) }

    val canCloseRegister = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)
    val canExpense = isOwner || isSupervisor || activePermissions.contains(AppPermissions.EXPENSES_MANAGE)
    val canReport = isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_VIEW_DAILY) || activePermissions.contains(AppPermissions.REPORTS_VIEW_PNL)
    val canSuppliers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SUPPLIERS_MANAGE)
    val canLoyalty = isOwner || isSupervisor || activePermissions.contains(AppPermissions.LOYALTY_MANAGE)
    val canCustomers = isOwner || isSupervisor || activePermissions.contains(AppPermissions.CUSTOMERS_VIEW)
    val canSettings = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_SHOP_SETTINGS)

    val operationsList = buildList {
        add(
            OperationHubItem(
                title = stringResource(R.string.register_close_section),
                subtitle = stringResource(R.string.register_close_hub_subtitle),
                navType = Nav3DType.REGISTER,
                badge = if (canCloseRegister) stringResource(R.string.more_audit_badge) else stringResource(R.string.more_locked_badge),
                badgeColor = if (canCloseRegister) Color(0xFFF59E0B) else Color(0xFF64748B),
                isSelected = currentKey == RegisterClose,
                category = OperationCategory.SHIFT,
                isLocked = !canCloseRegister,
                lockedMessage = stringResource(R.string.owner_only_register_close),
                onClick = onNavigateToRegisterClose
            )
        )
        if (!isTablet) {
            add(
                OperationHubItem(
                    title = stringResource(R.string.expenses),
                    subtitle = stringResource(R.string.more_expenses_subtitle),
                    navType = Nav3DType.EXPENSES,
                    badge = if (canExpense) stringResource(R.string.more_expenses_badge) else stringResource(R.string.more_locked_badge),
                    badgeColor = if (canExpense) Color(0xFFEF4444) else Color(0xFF64748B),
                    isSelected = currentKey == Expenses,
                    category = OperationCategory.SHIFT,
                    isLocked = !canExpense,
                    lockedMessage = stringResource(R.string.owner_only_expenses),
                    onClick = onNavigateToExpenses
                )
            )
        }
        if (!isTablet) {
            add(
                OperationHubItem(
                    title = stringResource(R.string.sales_report_section),
                    subtitle = stringResource(R.string.more_report_subtitle),
                    navType = Nav3DType.REPORT,
                    badge = if (canReport) stringResource(R.string.more_report_badge) else stringResource(R.string.more_locked_badge),
                    badgeColor = if (canReport) Color(0xFF3B82F6) else Color(0xFF64748B),
                    isSelected = currentKey == SalesReport,
                    category = OperationCategory.MANAGEMENT,
                    isLocked = !canReport,
                    lockedMessage = stringResource(R.string.owner_only_reports),
                    onClick = onNavigateToReport
                )
            )
        }
        add(
            OperationHubItem(
                title = stringResource(R.string.suppliers),
                subtitle = stringResource(R.string.more_suppliers_subtitle),
                navType = Nav3DType.SUPPLIERS,
                badge = if (canSuppliers) stringResource(R.string.more_suppliers_badge) else stringResource(R.string.more_locked_badge),
                badgeColor = if (canSuppliers) Color(0xFF10B981) else Color(0xFF64748B),
                isSelected = currentKey == Suppliers,
                category = OperationCategory.MANAGEMENT,
                isLocked = !canSuppliers,
                lockedMessage = stringResource(R.string.owner_only_suppliers),
                onClick = onNavigateToSuppliers
            )
        )
        add(
            OperationHubItem(
                title = stringResource(R.string.loyalty_screen_title),
                subtitle = stringResource(R.string.loyalty_hub_subtitle),
                navType = Nav3DType.LOYALTY,
                badge = if (canLoyalty) stringResource(R.string.loyalty_badge) else stringResource(R.string.more_locked_badge),
                badgeColor = if (canLoyalty) Color(0xFFEC4899) else Color(0xFF64748B),
                isSelected = currentKey == Loyalty,
                category = OperationCategory.CRM,
                isLocked = !canLoyalty,
                lockedMessage = stringResource(R.string.owner_only_loyalty),
                onClick = onNavigateToLoyalty
            )
        )
        add(
            OperationHubItem(
                title = stringResource(R.string.customers_hub_title),
                subtitle = stringResource(R.string.customers_hub_subtitle),
                navType = Nav3DType.CUSTOMERS,
                badge = if (canCustomers) stringResource(R.string.customers_badge) else stringResource(R.string.more_locked_badge),
                badgeColor = if (canCustomers) Color(0xFF0D9488) else Color(0xFF64748B),
                isSelected = currentKey == Customers,
                category = OperationCategory.CRM,
                isLocked = !canCustomers,
                lockedMessage = stringResource(R.string.owner_only_customers),
                onClick = onNavigateToCustomers
            )
        )
        if (!isTablet) {
            add(
                OperationHubItem(
                    title = stringResource(R.string.settings),
                    subtitle = stringResource(R.string.more_settings_subtitle),
                    navType = Nav3DType.SETTINGS,
                    badge = if (canSettings) stringResource(R.string.more_admin_badge) else stringResource(R.string.more_locked_badge),
                    badgeColor = if (canSettings) Color(0xFF8B5CF6) else Color(0xFF64748B),
                    isSelected = currentKey == Settings,
                    category = OperationCategory.MANAGEMENT,
                    isLocked = !canSettings,
                    lockedMessage = stringResource(R.string.owner_only_settings),
                    onClick = onNavigateToSettings
                )
            )
        }
    }

    val filteredList = if (selectedCategory == OperationCategory.ALL) {
        operationsList
    } else {
        operationsList.filter { it.category == selectedCategory }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PosTopAppBarTitle(
                title = stringResource(R.string.store_operations),
                subtitle = stringResource(R.string.store_operations_sub),
                metricBadge = stringResource(R.string.more_hub_badge_operations),
                badgeColor = PosSectionColors.More,
                isTablet = isTablet,
                modifier = Modifier.weight(1f),
                icon = { size -> Realistic3DMoreZapIcon(size = size) }
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // List / Grid View Mode Toggle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // List View
                        Surface(
                            onClick = { isGridView = false },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isGridView) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = stringResource(R.string.more_view_list_cd),
                                    modifier = Modifier.size(17.dp),
                                    tint = if (!isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Grid View
                        Surface(
                            onClick = { isGridView = true },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isGridView) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.more_view_grid_cd),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isGridView) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Category Quick-Filter Chips
        val categories = listOf(
            Triple(OperationCategory.ALL, stringResource(R.string.more_filter_all), operationsList.size),
            Triple(OperationCategory.SHIFT, stringResource(R.string.more_filter_shift), operationsList.count { it.category == OperationCategory.SHIFT }),
            Triple(OperationCategory.CRM, stringResource(R.string.more_filter_crm), operationsList.count { it.category == OperationCategory.CRM }),
            Triple(OperationCategory.MANAGEMENT, stringResource(R.string.more_filter_management), operationsList.count { it.category == OperationCategory.MANAGEMENT })
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { (cat, label, count) ->
                val isCatSelected = selectedCategory == cat
                FilterChip(
                    selected = isCatSelected,
                    onClick = { selectedCategory = cat },
                    label = {
                        Text(
                            text = "$label ($count)",
                            fontSize = 11.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isCatSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(30.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val maxContentHeight = run {
            // Dynamic available budget leaving comfortable space for drag handle, title, and search header (~130dp)
            val availableBudget = (screenHeight - 130.dp).coerceAtLeast(160.dp)
            val desiredHeight = screenHeight * if (isTablet) 0.55f else 0.65f
            desiredHeight.coerceIn(160.dp, 540.dp).coerceAtMost(availableBudget)
        }

        // Operations List / Grid
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
            ) {
                items(
                    items = filteredList,
                    key = { it.title },
                    span = { item ->
                        val index = filteredList.indexOf(item)
                        val isLastOdd = index == filteredList.lastIndex && filteredList.size % 2 != 0
                        GridItemSpan(if (isLastOdd) 2 else 1)
                    }
                ) { item ->
                    val isLastOdd = filteredList.indexOf(item) == filteredList.lastIndex && filteredList.size % 2 != 0
                    OperationHubCard(
                        item = item,
                        isGrid = if (!isTablet && isLastOdd) false else true,
                        isTablet = isTablet
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredList.forEach { item ->
                    OperationHubCard(
                        item = item,
                        isGrid = false,
                        isTablet = isTablet
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun OperationHubCard(
    item: OperationHubItem,
    isGrid: Boolean = false,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tile_scale"
    )

    val effectiveBadgeColor = if (item.isLocked) Color(0xFF64748B) else item.badgeColor

    Card(
        onClick = {
            if (item.isLocked) {
                val msg = item.lockedMessage ?: item.badge
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                item.onClick()
            }
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                item.isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (item.isSelected) 1.5.dp else 1.dp,
            color = when {
                item.isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                item.isLocked -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = if (item.isLocked) 0.50f else 1f
            }
    ) {
        if (isGrid && !isTablet) {
            // Compact 2-column Grid Card for Phone
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                // Top Row: 3D Icon & Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(effectiveBadgeColor.copy(alpha = 0.12f))
                            .border(
                                1.dp,
                                effectiveBadgeColor.copy(alpha = 0.25f),
                                RoundedCornerShape(11.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Animated3DNavItemIcon(
                            type = item.navType,
                            selected = !item.isLocked,
                            fontSize = 18.sp,
                            sellIconSize = 18.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        if (item.isLocked) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.38f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (item.isLocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else effectiveBadgeColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.isLocked) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else effectiveBadgeColor.copy(alpha = 0.32f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            if (item.isLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Text(
                                text = item.badge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isLocked) MaterialTheme.colorScheme.error else effectiveBadgeColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Title Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.5.dp))

                // Subtitle (2 Lines, No Cutoff)
                Text(
                    text = item.subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.isLocked) 0.6f else 1f),
                    lineHeight = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // Horizontal Card for Tablet (Grid & List) and Phone (List)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Squircle Icon Container
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(effectiveBadgeColor.copy(alpha = 0.12f))
                        .border(
                            1.dp,
                            effectiveBadgeColor.copy(alpha = 0.25f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Animated3DNavItemIcon(
                        type = item.navType,
                        selected = !item.isLocked,
                        fontSize = 20.sp,
                        sellIconSize = 20.dp,
                        modifier = Modifier.size(26.dp)
                    )
                    if (item.isLocked) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.38f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Title & Subtitle Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (item.isLocked) 0.6f else 1f),
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Trailing Section: Badge & Lock indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = if (item.isLocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else effectiveBadgeColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.isLocked) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else effectiveBadgeColor.copy(alpha = 0.32f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        ) {
                            if (item.isLocked) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Text(
                                text = item.badge,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isLocked) MaterialTheme.colorScheme.error else effectiveBadgeColor
                            )
                        }
                    }

                    if (!item.isLocked) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
