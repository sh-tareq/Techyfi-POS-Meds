@file:android.annotation.SuppressLint("LocalContextConfigurationRead")

package com.example.techyfipos

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.techyfipos.data.AppPermissions
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
    val infiniteTransition = rememberInfiniteTransition(label = "nav3d_${type.name}")

    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    val wobbleAnim by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble"
    )

    val spinAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val targetSize = sellIconSize

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            if (selected) {
                when (type) {
                    Nav3DType.SELL -> translationY = floatAnim
                    Nav3DType.PRODUCTS -> rotationZ = wobbleAnim
                    Nav3DType.HISTORY -> rotationZ = wobbleAnim * 0.7f
                    Nav3DType.DUES -> translationY = floatAnim * 0.6f
                    Nav3DType.MORE -> {
                        scaleY = pulseScale
                        scaleX = pulseScale
                    }
                    Nav3DType.REPORT -> {
                        scaleY = pulseScale
                        scaleX = pulseScale
                    }
                    Nav3DType.EXPENSES -> translationY = floatAnim
                    Nav3DType.SUPPLIERS -> translationX = wobbleAnim * 0.5f
                    Nav3DType.SETTINGS -> rotationZ = spinAnim
                    Nav3DType.REGISTER -> translationY = floatAnim * 0.5f
                    Nav3DType.LOYALTY -> translationY = floatAnim * 0.5f
                    Nav3DType.CUSTOMERS -> translationY = floatAnim * 0.5f
                }
            }
        }
    ) {
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
            Nav3DType.LOYALTY -> Realistic3DClipboardDuesIcon(size = targetSize)
            Nav3DType.CUSTOMERS -> Realistic3DCustomerHubIcon(size = targetSize)
        }
    }
}

@Composable
fun LocaleWrapper(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = java.util.Locale.forLanguageTag(language)
    java.util.Locale.setDefault(locale)
    
    val config = android.content.res.Configuration(context.resources.configuration)
    config.setLocale(locale)
    
    val localizedContext = context.createConfigurationContext(config)
    
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
        add(LocalContext provides localizedContext)
        add(androidx.compose.ui.platform.LocalConfiguration provides config)
        if (registryOwner != null) {
            add(androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner)
        }
        if (activity != null) {
            add(androidx.activity.compose.LocalActivity provides activity)
        }
    }.toTypedArray()

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

    LocaleWrapper(language = currentLang) {
        val ownerOnlyReportsText = stringResource(R.string.owner_only_reports)
        val ownerOnlyExpensesText = stringResource(R.string.owner_only_expenses)
        val ownerOnlySuppliersText = stringResource(R.string.owner_only_suppliers)
        val ownerOnlySettingsText = stringResource(R.string.owner_only_settings)
        val ownerOnlyRegisterCloseText = stringResource(R.string.owner_only_register_close)
        val ownerOnlyLoyaltyText = stringResource(R.string.owner_only_loyalty)
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

        var prefilledBarcodeForProductScreen by remember { mutableStateOf<String?>(null) }

        val currentKey = backStack.lastOrNull()
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestCurrentKey by rememberUpdatedState(currentKey)
        var backgroundedAt by remember { mutableLongStateOf(0L) }

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

        fun triggerTap(type: Nav3DType, action: () -> Unit) {
            scope.launch {
                pressedTab = type
                delay(120)
                pressedTab = null
                action()
            }
        }

        var showMoreHubSheet by remember { mutableStateOf(false) }

        Scaffold(
            bottomBar = {
                // Show bottom bar for core main sections (Scanner, Products, SalesHistory, SalesReport, Dues, Settings, Expenses, Suppliers, RegisterClose, Loyalty, Customers)
                if (currentKey == Scanner || currentKey == Products || currentKey == SalesHistory || currentKey == SalesReport || currentKey == Dues || currentKey == Settings || currentKey == Expenses || currentKey == Suppliers || currentKey == RegisterClose || currentKey == Loyalty || currentKey == Customers) {
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
                                    android.widget.Toast.makeText(context, "Permission required to view inventory", android.widget.Toast.LENGTH_SHORT).show()
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
                                    android.widget.Toast.makeText(context, "Permission required to view dues", android.widget.Toast.LENGTH_SHORT).show()
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
                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                        if (backStack.lastOrNull() != Loyalty) { backStack.add(Loyalty) }
                    },
                    onNavigateToCustomers = {
                        showMoreHubSheet = false
                        while (backStack.size > 1) { backStack.removeLastOrNull() }
                        if (backStack.lastOrNull() != Customers) { backStack.add(Customers) }
                    },
                    isOwner = isOwner,
                    currentKey = currentKey,
                    language = currentLang
                )
            }
            NavDisplay(
                backStack = backStack,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Splash> {
                        SplashScreen(
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
                                backStack.add(Scanner)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<ManageUsers> {
                        ManageUsersScreen(
                            authViewModel = authViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Scanner> {
                        ScannerScreen(
                            scannerViewModel = scannerViewModel,
                            cartViewModel = cartViewModel,
                            isOwner = isOwner,
                            onNavigateToCart = { backStack.add(Cart) },
                            onNavigateToSettings = { backStack.add(Settings) },
                            onNavigateToProducts = { barcode ->
                                prefilledBarcodeForProductScreen = barcode
                                backStack.add(Products)
                            },
                            modifier = Modifier.padding(innerPadding)
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
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Products> {
                        val code = prefilledBarcodeForProductScreen
                        // Clear prefill so it doesn't open dialog next time we open products screen
                        DisposableEffect(Unit) {
                            onDispose { prefilledBarcodeForProductScreen = null }
                        }
                        ProductsScreen(
                            productsViewModel = productsViewModel,
                            cartViewModel = cartViewModel,
                            prefilledBarcode = code,
                            isOwner = isOwner,
                            canViewCosts = isOwner || activePermissions.contains(AppPermissions.INVENTORY_VIEW_COSTS),
                            canEdit = isOwner || activePermissions.contains(AppPermissions.INVENTORY_ADD_EDIT),
                            canDelete = isOwner || activePermissions.contains(AppPermissions.INVENTORY_DELETE),
                            canAdjustStock = isOwner || activePermissions.contains(AppPermissions.INVENTORY_ADJUST_STOCK),
                            canPrintLabels = isOwner || isSupervisor || activePermissions.contains(AppPermissions.INVENTORY_PRINT_LABELS),
                            onBack = { backStack.removeLastOrNull() },
                            onNavigateToCart = { backStack.add(Cart) },
                            modifier = Modifier.padding(innerPadding)
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
                                    backStack.add(SalesReport)
                                } else {
                                    android.widget.Toast.makeText(context, ownerOnlyReportsText, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onNavigateToCart = { backStack.add(Cart) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<SalesReport> {
                        SalesReportScreen(
                            salesViewModel = salesViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Expenses> {
                        ExpensesScreen(
                            expenseViewModel = expenseViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Suppliers> {
                        SuppliersScreen(
                            supplierViewModel = supplierViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Dues> {
                        DuesScreen(
                            duesViewModel = duesViewModel,
                            customerViewModel = customerViewModel,
                            isOwner = isOwner,
                            isSupervisor = isSupervisor,
                            activePermissions = activePermissions,
                            onBack = { backStack.removeLastOrNull() },
                            onStartSaleForCustomer = { customer ->
                                cartViewModel.onCustomerPhoneChanged(customer.phone)
                                cartViewModel.customerName.value = customer.name
                                while (backStack.size > 1) { backStack.removeLastOrNull() }
                                backStack.add(Scanner)
                                android.widget.Toast.makeText(context, "Started sale for ${customer.name}", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Settings> {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            isOwner = isOwner,
                            onBack = { backStack.removeLastOrNull() },
                            onExpensesClick = { backStack.add(Expenses) },
                            onSuppliersClick = { backStack.add(Suppliers) },
                            onManageUsersClick = {
                                val canManageStaff = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_MANAGE_STAFF)
                                if (canManageStaff) {
                                    backStack.add(ManageUsers)
                                } else {
                                    android.widget.Toast.makeText(context, "Only Shop Owner or authorized staff can manage user accounts", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLogout = {
                                authViewModel.logout()
                                while (backStack.size > 0) { backStack.removeLastOrNull() }
                                backStack.add(Lock)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<RegisterClose> {
                        RegisterCloseScreen(
                            registerClosingViewModel = registerClosingViewModel,
                            expenseViewModel = expenseViewModel,
                            salesViewModel = salesViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Loyalty> {
                        LoyaltyScreen(
                            loyaltyViewModel = loyaltyViewModel,
                            customerViewModel = customerViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onStartSaleForCustomer = { customer ->
                                cartViewModel.onCustomerPhoneChanged(customer.phone)
                                cartViewModel.customerName.value = customer.name
                                while (backStack.size > 1) { backStack.removeLastOrNull() }
                                backStack.add(Scanner)
                                android.widget.Toast.makeText(context, "Started sale for ${customer.name}", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                    entry<Customers> {
                        CustomersScreen(
                            customerViewModel = customerViewModel,
                            onBack = { backStack.removeLastOrNull() },
                            onStartSaleForCustomer = { customer ->
                                cartViewModel.onCustomerPhoneChanged(customer.phone)
                                cartViewModel.customerName.value = customer.name
                                while (backStack.size > 1) { backStack.removeLastOrNull() }
                                backStack.add(Scanner)
                                android.widget.Toast.makeText(context, "Started sale for ${customer.name}", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            )
        }
    }
}

data class OperationHubItem(
    val title: String,
    val subtitle: String,
    val navType: Nav3DType,
    val badge: String,
    val badgeColor: Color,
    val isSelected: Boolean,
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
    currentKey: Any?,
    language: String = "en"
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(44.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    ) {
        LocaleWrapper(language = language) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.store_operations),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.store_operations_sub),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close_content_description),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val operationsList = listOf(
                OperationHubItem(
                    title = stringResource(R.string.sales_report_section),
                    subtitle = stringResource(R.string.more_report_subtitle),
                    navType = Nav3DType.REPORT,
                    badge = stringResource(R.string.more_report_badge),
                    badgeColor = Color(0xFF3B82F6),
                    isSelected = currentKey == SalesReport,
                    onClick = onNavigateToReport
                ),
                OperationHubItem(
                    title = stringResource(R.string.expenses),
                    subtitle = stringResource(R.string.more_expenses_subtitle),
                    navType = Nav3DType.EXPENSES,
                    badge = stringResource(R.string.more_expenses_badge),
                    badgeColor = Color(0xFFEF4444),
                    isSelected = currentKey == Expenses,
                    onClick = onNavigateToExpenses
                ),
                OperationHubItem(
                    title = stringResource(R.string.suppliers),
                    subtitle = stringResource(R.string.more_suppliers_subtitle),
                    navType = Nav3DType.SUPPLIERS,
                    badge = stringResource(R.string.more_suppliers_badge),
                    badgeColor = Color(0xFF10B981),
                    isSelected = currentKey == Suppliers,
                    onClick = onNavigateToSuppliers
                ),
                OperationHubItem(
                    title = stringResource(R.string.settings),
                    subtitle = stringResource(R.string.more_settings_subtitle),
                    navType = Nav3DType.SETTINGS,
                    badge = if (isOwner) stringResource(R.string.more_admin_badge) else stringResource(R.string.more_locked_badge),
                    badgeColor = if (isOwner) Color(0xFF8B5CF6) else Color(0xFF64748B),
                    isSelected = currentKey == Settings,
                    onClick = onNavigateToSettings
                ),
                OperationHubItem(
                    title = stringResource(R.string.register_close_section),
                    subtitle = stringResource(R.string.register_close_hub_subtitle),
                    navType = Nav3DType.REGISTER,
                    badge = if (isOwner) stringResource(R.string.more_audit_badge) else stringResource(R.string.more_locked_badge),
                    badgeColor = if (isOwner) Color(0xFFF59E0B) else Color(0xFF64748B),
                    isSelected = currentKey == RegisterClose,
                    onClick = onNavigateToRegisterClose
                ),
                OperationHubItem(
                    title = stringResource(R.string.loyalty_screen_title),
                    subtitle = stringResource(R.string.loyalty_hub_subtitle),
                    navType = Nav3DType.LOYALTY,
                    badge = stringResource(R.string.loyalty_badge),
                    badgeColor = Color(0xFFEC4899),
                    isSelected = currentKey == Loyalty,
                    onClick = onNavigateToLoyalty
                ),
                OperationHubItem(
                    title = stringResource(R.string.customers_hub_title),
                    subtitle = stringResource(R.string.customers_hub_subtitle),
                    navType = Nav3DType.CUSTOMERS,
                    badge = stringResource(R.string.customers_badge),
                    badgeColor = Color(0xFF0D9488),
                    isSelected = currentKey == Customers,
                    onClick = onNavigateToCustomers
                )
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(operationsList) { item ->
                    OperationHubCard(item = item)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
        }
    }
}

@Composable
fun OperationHubCard(item: OperationHubItem) {
    Card(
        onClick = item.onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (item.isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Animated3DNavItemIcon(
                    type = item.navType,
                    selected = true,
                    fontSize = 28.sp,
                    sellIconSize = 28.dp,
                    modifier = Modifier.size(36.dp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = item.badgeColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, item.badgeColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = item.badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = item.badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
