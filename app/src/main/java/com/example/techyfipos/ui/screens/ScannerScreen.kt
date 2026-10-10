@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package com.example.techyfipos.ui.screens

import com.example.techyfipos.ui.dialogs.*
import com.example.techyfipos.ui.components.SupervisorAuthDialog
import com.example.techyfipos.utils.CartAuthHelper
import com.example.techyfipos.data.AppPermissions
import android.Manifest
import java.util.Locale
import android.content.Context
import android.widget.Toast
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.ui.components.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.ui.viewmodel.CartViewModel
import com.example.techyfipos.ui.viewmodel.ScannerViewModel
import com.example.techyfipos.ui.viewmodel.ProductsViewModel
import com.example.techyfipos.ui.viewmodel.SettingsViewModel
import com.example.techyfipos.ui.viewmodel.isSyntheticSaleBarcode
import com.example.techyfipos.ui.viewmodel.CartItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.example.techyfipos.utils.beepAndVibrate
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.isFractionalUnit
import com.example.techyfipos.utils.HardwareBarcodeScanner
import com.example.techyfipos.utils.toEnglishDigits
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    scannerViewModel: ScannerViewModel,
    cartViewModel: CartViewModel,
    productsViewModel: ProductsViewModel? = null,
    settingsViewModel: SettingsViewModel? = null,
    authViewModel: com.example.techyfipos.ui.viewmodel.AuthViewModel? = null,
    isTablet: Boolean = false,
    isOwner: Boolean = true,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    canCheckStock: Boolean = isOwner,
    onNavigateToCart: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProducts: (String?) -> Unit,
    onLockOrLogout: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authPermissions by (authViewModel?.activePermissions ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())).collectAsStateWithLifecycle(initialValue = emptySet())
    val effectivePermissions = if (activePermissions.isNotEmpty()) activePermissions else authPermissions
    val invoiceSettingsData by settingsViewModel?.invoiceSettingsData?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val allowNegativeInventory = invoiceSettingsData?.allowNegativeInventory == true || isOwner || isSupervisor

    val isSellMode by scannerViewModel.isSellMode.collectAsStateWithLifecycle()

    // Stock Check mode mutates inventory; allow owners, supervisors, or staff with INVENTORY_ADD_EDIT permission
    LaunchedEffect(canCheckStock) {
        if (!canCheckStock) scannerViewModel.setMode(true)
    }
    val scannedProduct by scannerViewModel.scannedProduct.collectAsStateWithLifecycle()
    val scannedBarcode by scannerViewModel.scannedBarcode.collectAsStateWithLifecycle()
    val torchEnabled by scannerViewModel.torchEnabled.collectAsStateWithLifecycle()
    val showManualDialog by scannerViewModel.showManualDialog.collectAsStateWithLifecycle()
    val cartItems by cartViewModel.cartItems.collectAsStateWithLifecycle()
    val grandTotal by cartViewModel.grandTotal.collectAsStateWithLifecycle()
    val addedProductConfirmation by scannerViewModel.addedProductConfirmation.collectAsStateWithLifecycle()

    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var scanningPaused by remember { mutableStateOf(false) }
    var showCartPreview by remember { mutableStateOf(false) }
    var lastScannedProduct by remember { mutableStateOf<Product?>(null) }
    val latestScanningPaused by rememberUpdatedState(scanningPaused)
    
    var showSupervisorAuthDialog by remember { mutableStateOf(false) }
    var pendingSupervisorAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSupervisorDesc by remember { mutableStateOf("") }

    val canVoidCart = CartAuthHelper.canVoidCart(isOwner, isSupervisor, effectivePermissions)
    val canRemoveItem = CartAuthHelper.canRemoveItem(isOwner, isSupervisor, effectivePermissions)
    var showClearCartConfirm by remember { mutableStateOf(false) }
    var itemToRemoveConfirm by remember { mutableStateOf<Product?>(null) }

    val handleClearCartRequest = {
        CartAuthHelper.handleVoidCartRequest(
            context = context,
            isOwner = isOwner,
            isSupervisor = isSupervisor,
            activePermissions = effectivePermissions,
            authViewModel = authViewModel,
            onAllowed = { showClearCartConfirm = true },
            onRequestSupervisorAuth = { desc ->
                pendingSupervisorDesc = desc
                pendingSupervisorAction = { showClearCartConfirm = true }
                showSupervisorAuthDialog = true
            }
        )
    }
    val handleRemoveItemRequest: (Product) -> Unit = { product ->
        CartAuthHelper.handleRemoveItemRequest(
            context = context,
            productName = product.name,
            isOwner = isOwner,
            isSupervisor = isSupervisor,
            activePermissions = effectivePermissions,
            authViewModel = authViewModel,
            onAllowed = { itemToRemoveConfirm = product },
            onRequestSupervisorAuth = { desc ->
                pendingSupervisorDesc = desc
                pendingSupervisorAction = { itemToRemoveConfirm = product }
                showSupervisorAuthDialog = true
            }
        )
    }

    // Snackbar states
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Camera Permission launcher
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // USB/Bluetooth HID scans share the same product pipeline while the camera
    // remains live. No source switch or separate hardware-scanner screen is needed.
    LaunchedEffect(Unit) {
        HardwareBarcodeScanner.barcodeEvents.collect { code ->
            if (latestScanningPaused) return@collect
            val normalizedCode = toEnglishDigits(code.trim())
            if (normalizedCode.isBlank()) return@collect
            scannerViewModel.onBarcodeScanned(
                barcode = normalizedCode,
                cartViewModel = cartViewModel,
                onSoundVibrate = { beepAndVibrate(context) },
                onShowSnackbar = { name, price, stock, total, isSuccess ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = if (isSuccess) {
                                context.getString(R.string.scanner_added_snackbar, name, formatTaka(price), stock, formatTaka(total))
                            } else {
                                context.getString(R.string.scanner_out_of_stock_snackbar, name, stock)
                            },
                            duration = SnackbarDuration.Short
                        )
                    }
                },
                fromHardwareScanner = true
            )
        }
    }

    // Toggle Torch on camera controller
    LaunchedEffect(torchEnabled, cameraControl) {
        cameraControl?.enableTorch(torchEnabled)
    }

    LaunchedEffect(addedProductConfirmation?.timestamp) {
        val confirmation = addedProductConfirmation ?: return@LaunchedEffect
        if (confirmation.isSuccess) {
            lastScannedProduct = cartItems.firstOrNull { it.product.barcode == confirmation.barcode }?.product
        }
        delay(3000)
        scannerViewModel.dismissAddedConfirmation()
    }

    val cartUnitCount = cartItems.sumOf { it.quantity }
    val heldCarts by cartViewModel.allHeldCarts.collectAsStateWithLifecycle()
    val heldCartCount by cartViewModel.heldCartCount.collectAsStateWithLifecycle()
    var showHeldCartsSheet by remember { mutableStateOf(false) }
    var pendingResumeCart by remember { mutableStateOf<com.example.techyfipos.data.db.HeldCart?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (isTablet) {
            TabletSplitTerminal(
                scannerViewModel = scannerViewModel,
                cartViewModel = cartViewModel,
                productsViewModel = productsViewModel,
                cameraControl = cameraControl,
                onCameraControlReady = { cameraControl = it },
                hasCameraPermission = hasCameraPermission,
                onRequestCameraPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                torchEnabled = torchEnabled,
                onTorchToggle = { scannerViewModel.toggleTorch() },
                scanningPaused = scanningPaused,
                onScanningPausedToggle = { scanningPaused = !scanningPaused },
                isSellMode = isSellMode,
                canCheckStock = canCheckStock,
                onModeChanged = scannerViewModel::setMode,
                heldCartCount = heldCartCount,
                onShowHeldCarts = { showHeldCartsSheet = true },
                onManualEntry = { scannerViewModel.setShowManualDialog(true) },
                cartItems = cartItems,
                grandTotal = grandTotal,
                allowNegativeInventory = allowNegativeInventory,
                onNavigateToCart = onNavigateToCart,
                onClearCartRequested = handleClearCartRequest,
                onRemoveItemRequested = handleRemoveItemRequest,
                modifier = Modifier.fillMaxSize()
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    SellHeader(
                        isSellMode = isSellMode,
                        itemCount = cartUnitCount,
                        total = grandTotal,
                        onCartClick = { showCartPreview = true },
                        onLockClick = onLockOrLogout
                    )
                },
                bottomBar = {
                    SellCheckoutBar(
                        itemCount = cartUnitCount,
                        total = grandTotal,
                        onReviewCart = { showCartPreview = true }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Stock Check mode mutates inventory and requires an owner session server-side;
                        // a cashier is forced into Sell mode above, so there is no mode to switch here.
                        if (canCheckStock) {
                            SellControlBar(
                                isSellMode = isSellMode,
                                onModeChanged = scannerViewModel::setMode
                            )
                        }

                        AnimatedVisibility(
                            visible = isSellMode && heldCartCount > 0,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            com.example.techyfipos.ui.components.HeldCartsTopBanner(
                                count = heldCartCount,
                                onClick = { showHeldCartsSheet = true }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            if (hasCameraPermission) {
                                CameraPreviewView(
                                    scannerViewModel = scannerViewModel,
                                    cartViewModel = cartViewModel,
                                    scanningEnabled = !scanningPaused,
                                    torchEnabled = torchEnabled,
                                    onCameraControlReady = { cameraControl = it },
                                    modifier = Modifier.fillMaxSize()
                                )
                                ScanFrameOverlay()
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NoPhotography,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.camera_permission_required),
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                        Text(stringResource(R.string.grant_permission))
                                    }
                                }
                            }

                            ScannerStatusMessage(
                                scanningPaused = scanningPaused,
                                isSellMode = isSellMode,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 18.dp)
                            )

                            addedProductConfirmation
                                ?.takeIf { !it.isSuccess }
                                ?.let { confirmation ->
                                    CompactScanAlert(
                                        confirmation = confirmation,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(horizontal = 24.dp)
                                    )
                                }

                            ScannerToolDock(
                                torchEnabled = torchEnabled,
                                torchAvailable = true,
                                scanningPaused = scanningPaused,
                                onTorchClick = { scannerViewModel.toggleTorch() },
                                onManualEntry = { scannerViewModel.setShowManualDialog(true) },
                                onPauseChanged = { scanningPaused = !scanningPaused },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isSellMode && lastScannedProduct != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            lastScannedProduct?.let { product ->
                                LastScannedItemCard(
                                    product = product,
                                    quantity = cartItems.firstOrNull { it.product.barcode == product.barcode }?.quantity ?: 0.0,
                                    onUndo = {
                                        cartItems.firstOrNull { it.product.barcode == product.barcode }?.let { item ->
                                            cartViewModel.setQuantity(product, (item.quantity - 1.0).coerceAtLeast(0.0))
                                        }
                                        lastScannedProduct = null
                                        scannerViewModel.dismissAddedConfirmation()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

            // Dynamic bottom sheets
            if (scannedProduct != null) {
                StockCheckBottomSheet(
                    product = scannedProduct!!,
                    onAdjustStock = { delta ->
                        scannerViewModel.quickAdjustStock(scannedProduct!!, delta)
                    },
                    onSetStock = { newQty ->
                        scannerViewModel.setStockQty(scannedProduct!!, newQty)
                    },
                    onEdit = {
                        val code = scannedProduct?.barcode
                        scannerViewModel.clearScanned()
                        onNavigateToProducts(code)
                    },
                    onSave = { newQty ->
                        val p = scannedProduct
                        if (p != null) {
                            scannerViewModel.saveStockQty(p, newQty)
                            val stockSavedMsg = context.getString(R.string.scanner_stock_saved_toast, p.name, newQty.toString())
                            Toast.makeText(
                                context,
                                stockSavedMsg,
                                Toast.LENGTH_SHORT
                            ).show()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = stockSavedMsg,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        } else {
                            scannerViewModel.clearScanned()
                        }
                    },
                    onDismiss = { scannerViewModel.clearScanned() }
                )
            }

            if (scannedBarcode != null) {
                val canAddProduct = isOwner || effectivePermissions.contains(AppPermissions.INVENTORY_ADD_EDIT)
                StockCheckNotFoundDialog(
                    barcode = scannedBarcode!!,
                    onAddNow = if (canAddProduct) {
                        {
                            val code = scannedBarcode
                            scannerViewModel.clearScanned()
                            onNavigateToProducts(code)
                        }
                    } else null,
                    onDismiss = { scannerViewModel.clearScanned() }
                )
            }

            if (showManualDialog) {
                ManualBarcodeDialog(
                    onDismiss = { scannerViewModel.setShowManualDialog(false) },
                    onBarcodeEntered = { barcode ->
                        scannerViewModel.setShowManualDialog(false)
                        scannerViewModel.onBarcodeScanned(
                            barcode = barcode,
                            cartViewModel = cartViewModel,
                            onSoundVibrate = { beepAndVibrate(context) },
                            fromHardwareScanner = true,
                            onShowSnackbar = { name, price, stock, total, isSuccess ->
                                scope.launch {
                                    val msg = if (isSuccess) {
                                        context.getString(R.string.scanner_added_snackbar, name, formatTaka(price), stock, formatTaka(total))
                                    } else {
                                        context.getString(R.string.scanner_out_of_stock_snackbar, name, stock)
                                    }
                                    snackbarHostState.showSnackbar(
                                        message = msg,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        )
                    }
                )
            }

            if (showCartPreview) {
                CartPreviewBottomSheet(
                    cartItems = cartItems,
                    total = grandTotal,
                    allowNegativeInventory = allowNegativeInventory,
                    onQuantityChanged = { prod, qty ->
                        cartViewModel.setQuantity(prod, qty, allowNegative = allowNegativeInventory)
                    },
                    onRemove = { prod -> handleRemoveItemRequest(prod) },
                    onDismiss = { showCartPreview = false },
                    onHoldCart = {
                        cartViewModel.holdCurrentCart()
                        showCartPreview = false
                        Toast.makeText(context, context.getString(R.string.cart_held_success_toast), Toast.LENGTH_SHORT).show()
                    },
                    onContinue = {
                        showCartPreview = false
                        onNavigateToCart()
                    }
                )
            }

            if (showHeldCartsSheet) {
                com.example.techyfipos.ui.components.HeldCartsBottomSheet(
                    heldCarts = heldCarts,
                    onResume = { cart ->
                        if (cartItems.isNotEmpty()) {
                            pendingResumeCart = cart
                        } else {
                            cartViewModel.restoreHeldCart(cart) { result ->
                                Toast.makeText(context,
                                    if (result.unavailableLines > 0) context.getString(R.string.held_cart_restored_partial_toast, result.unavailableLines)
                                    else context.getString(R.string.held_cart_restored_toast, cart.title),
                                    if (result.unavailableLines > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDelete = { cartId ->
                        cartViewModel.deleteHeldCart(cartId)
                    },
                    onDismiss = { showHeldCartsSheet = false }
                )
            }

            pendingResumeCart?.let { cartToResume ->
                AlertDialog(
                    onDismissRequest = { pendingResumeCart = null },
                    properties = DialogProperties(dismissOnClickOutside = false),
                    title = { Text(stringResource(R.string.held_cart_resume_confirm_title)) },
                    text = { Text(stringResource(R.string.held_cart_resume_confirm_message, cartToResume.title)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                val cart = cartToResume
                                pendingResumeCart = null
                                showHeldCartsSheet = false
                                cartViewModel.restoreHeldCart(cart) { result ->
                                    Toast.makeText(context,
                                        if (result.unavailableLines > 0) context.getString(R.string.held_cart_restored_partial_toast, result.unavailableLines)
                                        else context.getString(R.string.held_cart_restored_toast, cart.title),
                                        if (result.unavailableLines > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingResumeCart = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showClearCartConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearCartConfirm = false },
                    properties = DialogProperties(dismissOnClickOutside = false),
                    title = { Text(stringResource(R.string.clear_cart_dialog_title)) },
                    text = { Text(stringResource(R.string.clear_cart_dialog_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            cartViewModel.clearCart()
                            showClearCartConfirm = false
                        }) {
                            Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearCartConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            itemToRemoveConfirm?.let { product ->
                AlertDialog(
                    onDismissRequest = { itemToRemoveConfirm = null },
                    properties = DialogProperties(dismissOnClickOutside = false),
                    title = { Text(stringResource(R.string.remove_item_confirm_title)) },
                    text = { Text(stringResource(R.string.remove_item_confirm_message_fmt, product.name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            cartViewModel.removeFromCart(product)
                            itemToRemoveConfirm = null
                        }) {
                            Text(stringResource(R.string.cd_remove), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToRemoveConfirm = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }

            if (showSupervisorAuthDialog && authViewModel != null) {
                SupervisorAuthDialog(
                    authViewModel = authViewModel,
                    actionDescription = pendingSupervisorDesc,
                    onAuthorized = {
                        showSupervisorAuthDialog = false
                        pendingSupervisorAction?.invoke()
                        pendingSupervisorAction = null
                    },
                    onDismiss = {
                        showSupervisorAuthDialog = false
                        pendingSupervisorAction = null
                    }
                )
            }
        }
    }

private object TabletTerminalColors {
    val isDark: Boolean
        @Composable get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val WorkspaceBg: Color
        @Composable get() = if (isDark) Color(0xFF080B11) else Color(0xFFF1F5F9)
    val CatalogSurfaceBg: Color
        @Composable get() = if (isDark) Color(0xFF161D2D) else Color(0xFFF8FAFC)
    val CartSurfaceBg: Color
        @Composable get() = if (isDark) Color(0xFF0F1523) else Color(0xFFFFFFFF)
    val CardBg: Color
        @Composable get() = if (isDark) Color(0xFF1E273D) else Color(0xFFFFFFFF)
    val CardHoverBorder: Color
        @Composable get() = if (isDark) Color(0xFF26334D) else Color(0xFFCBD5E1)
    val CardBorder: Color
        @Composable get() = if (isDark) Color(0x1FFFFFFF) else Color(0xFFE2E8F0)
    val InputBg: Color
        @Composable get() = if (isDark) Color(0xFF121824) else Color(0xFFF1F5F9)
    val FooterBg: Color
        @Composable get() = if (isDark) Color(0xFF0A0E18) else Color(0xFFF8FAFC)

    // Vibrant Brand Accents
    val Emerald = Color(0xFF10B981)
    val EmeraldDark = Color(0xFF047857)
    val EmeraldGlow = Color(0x5910B981)
    val EmeraldLight: Color
        @Composable get() = if (isDark) Color(0x2410B981) else Color(0x1810B981)
    val EmeraldTranslucent: Color
        @Composable get() = if (isDark) Color(0x1410B981) else Color(0x0F10B981)

    val Amber = Color(0xFFF59E0B)
    val AmberLight: Color
        @Composable get() = if (isDark) Color(0x26F59E0B) else Color(0x1AF59E0B)
    val AmberBorder: Color
        @Composable get() = if (isDark) Color(0x59F59E0B) else Color(0x40F59E0B)

    val Rose = Color(0xFFEF4444)
    val RoseLight: Color
        @Composable get() = if (isDark) Color(0x26EF4444) else Color(0x1AEF4444)
    val RoseBorder: Color
        @Composable get() = if (isDark) Color(0x59EF4444) else Color(0x40EF4444)

    val Indigo = Color(0xFF6366F1)
    val Purple = Color(0xFF8B5CF6)

    // Crisp Typography
    val TextMain: Color
        @Composable get() = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val TextSecondary: Color
        @Composable get() = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    val TextTertiary: Color
        @Composable get() = if (isDark) Color(0xFF64748B) else Color(0xFF64748B)
    val TextDisabled: Color
        @Composable get() = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)

    val Divider: Color
        @Composable get() = if (isDark) Color(0x1FFFFFFF) else Color(0xFFE2E8F0)
}

private data class TabletStockBadgeInfo(
    val bg: Color,
    val border: Color,
    val textColor: Color,
    val label: String
)

// 3D Indigo Cart Capsule matching Products Screen top bar
@Composable
private fun TabletCartCapsuleButton(
    cartUnitCount: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cartCapsuleBounceScale = remember { Animatable(1f) }
    val cartIconAnimScale = remember { Animatable(1f) }
    val cartIconAnimRotation = remember { Animatable(0f) }
    val cartIconAnimOffsetY = remember { Animatable(0f) }
    val cartBadgeAnimScale = remember { Animatable(1f) }
    val cartBadgeAnimRotation = remember { Animatable(0f) }
    var cartAnimKey by remember { mutableLongStateOf(0L) }
    val cartInteractionSource = remember { MutableInteractionSource() }
    val isCartPressed by cartInteractionSource.collectIsPressedAsState()
    val cartPressScale by animateFloatAsState(
        targetValue = if (isCartPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cartPressScale"
    )

    LaunchedEffect(cartUnitCount, cartAnimKey) {
        if (cartUnitCount > 0 || cartAnimKey > 0L) {
            launch {
                cartCapsuleBounceScale.snapTo(1.12f)
                cartCapsuleBounceScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                cartIconAnimOffsetY.snapTo(-8f)
                cartIconAnimScale.snapTo(1.38f)
                cartIconAnimRotation.snapTo(-16f)

                launch {
                    cartIconAnimOffsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                launch {
                    cartIconAnimScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    cartIconAnimRotation.animateTo(
                        targetValue = 14f,
                        animationSpec = tween(120, easing = FastOutSlowInEasing)
                    )
                    cartIconAnimRotation.animateTo(
                        targetValue = -7f,
                        animationSpec = tween(110, easing = FastOutSlowInEasing)
                    )
                    cartIconAnimRotation.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
            launch {
                cartBadgeAnimScale.snapTo(1.75f)
                cartBadgeAnimRotation.snapTo(22f)

                launch {
                    cartBadgeAnimScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                launch {
                    cartBadgeAnimRotation.animateTo(
                        targetValue = -12f,
                        animationSpec = tween(130, easing = FastOutSlowInEasing)
                    )
                    cartBadgeAnimRotation.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }
    }

    Surface(
        onClick = {
            cartAnimKey = System.currentTimeMillis()
            onClick()
        },
        interactionSource = cartInteractionSource,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF4F46E5),
        contentColor = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .graphicsLayer {
                scaleX = cartPressScale * cartCapsuleBounceScale.value
                scaleY = cartPressScale * cartCapsuleBounceScale.value
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = cartIconAnimScale.value
                    scaleY = cartIconAnimScale.value
                    rotationZ = cartIconAnimRotation.value
                    translationY = cartIconAnimOffsetY.value
                }
            ) {
                Realistic3DSupermarketCartIcon(size = 17.dp)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.cart),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (cartUnitCount > 0) {
                Spacer(Modifier.width(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .graphicsLayer {
                            scaleX = cartBadgeAnimScale.value
                            scaleY = cartBadgeAnimScale.value
                            rotationZ = cartBadgeAnimRotation.value
                        }
                        .background(Color(0xFFEF4444), RoundedCornerShape(9.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    val cStr = if (cartUnitCount % 1.0 == 0.0) cartUnitCount.toLong().toString() else String.format(Locale.US, "%.1f", cartUnitCount)
                    AnimatedContent(
                        targetState = cStr,
                        transitionSpec = {
                            (slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 1.35f))
                                .togetherWith(slideOutVertically { it } + fadeOut() + scaleOut(targetScale = 0.65f))
                        },
                        label = "cartCountTextAnim"
                    ) { targetCount ->
                        Text(
                            text = targetCount,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletSplitTerminal(
    scannerViewModel: ScannerViewModel,
    cartViewModel: CartViewModel,
    productsViewModel: ProductsViewModel?,
    cameraControl: androidx.camera.core.CameraControl?,
    onCameraControlReady: (androidx.camera.core.CameraControl) -> Unit,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    torchEnabled: Boolean,
    onTorchToggle: () -> Unit,
    scanningPaused: Boolean,
    onScanningPausedToggle: () -> Unit,
    isSellMode: Boolean,
    canCheckStock: Boolean,
    onModeChanged: (Boolean) -> Unit,
    heldCartCount: Int,
    onShowHeldCarts: () -> Unit,
    onManualEntry: () -> Unit,
    cartItems: List<CartItem>,
    grandTotal: Double,
    allowNegativeInventory: Boolean = false,
    onNavigateToCart: () -> Unit,
    onClearCartRequested: () -> Unit,
    onRemoveItemRequested: (com.example.techyfipos.data.db.Product) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxWidth < maxHeight || configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < configuration.screenHeightDp

    val allProducts by (productsViewModel?.allProducts?.collectAsStateWithLifecycle()) ?: remember { mutableStateOf(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showCameraPIP by remember { mutableStateOf(false) }


    val categories = remember(allProducts) {
        allProducts.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredProducts = remember(allProducts, searchQuery, selectedCategory) {
        allProducts.filter { product ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                val englishQuery = toEnglishDigits(searchQuery.trim())
                product.name.contains(searchQuery, ignoreCase = true) ||
                product.barcode.contains(englishQuery, ignoreCase = true)
            }
            val matchesCat = if (selectedCategory == null) true else {
                product.category.equals(selectedCategory, ignoreCase = true)
            }
            matchesQuery && matchesCat
        }
    }

    val subtotal = remember(cartItems) {
        cartItems.sumOf { it.product.sellingPrice * it.quantity }
    }
    val itemCount = remember(cartItems) {
        cartItems.sumOf { it.quantity }.toInt()
    }
    val cartUnitCount = remember(cartItems) {
        cartItems.sumOf { it.quantity }
    }


    if (isPortrait) {
        // =========================================================================
        // VERTICAL (PORTRAIT) VIEWPORT LAYOUT: 54% Catalog Top, 46% Cart Review Bottom
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TabletTerminalColors.WorkspaceBg)
        ) {
            // TOP PANE: Catalog, Search, Quick Actions & Product Grid (54% height)
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxWidth()
                    .background(TabletTerminalColors.CatalogSurfaceBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabletTopToolbar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    showCameraPIP = showCameraPIP,
                    onToggleCameraPIP = { showCameraPIP = !showCameraPIP },
                    onManualEntry = onManualEntry,
                    canCheckStock = canCheckStock,
                    isSellMode = isSellMode,
                    onToggleStockMode = { onModeChanged(!isSellMode) },
                    heldCartCount = heldCartCount,
                    onShowHeldCarts = onShowHeldCarts,
                    isPortrait = true
                )

                // Collapsible Camera PIP
                AnimatedVisibility(
                    visible = showCameraPIP,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TabletCameraPipView(
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = onRequestCameraPermission,
                        scannerViewModel = scannerViewModel,
                        cartViewModel = cartViewModel,
                        scanningPaused = scanningPaused,
                        torchEnabled = torchEnabled,
                        onTorchToggle = onTorchToggle,
                        onScanningPausedToggle = onScanningPausedToggle,
                        onCameraControlReady = onCameraControlReady
                    )
                }

                // Mode banner when in Stock Check mode
                if (!isSellMode) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TabletTerminalColors.AmberLight,
                        border = BorderStroke(1.dp, TabletTerminalColors.AmberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = TabletTerminalColors.Amber, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.tablet_stock_check_banner),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TabletTerminalColors.Amber
                            )
                        }
                    }
                }

                // Category Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        val isAllSelected = selectedCategory == null
                        Surface(
                            onClick = { selectedCategory = null },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAllSelected) TabletTerminalColors.EmeraldLight else TabletTerminalColors.CardBg,
                            border = BorderStroke(1.dp, if (isAllSelected) TabletTerminalColors.Emerald else TabletTerminalColors.CardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isAllSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TabletTerminalColors.Emerald)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.tablet_category_all),
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = if (isAllSelected) TabletTerminalColors.Emerald else TabletTerminalColors.TextSecondary
                                )
                            }
                        }
                    }
                    items(categories) { cat ->
                        val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                        Surface(
                            onClick = {
                                selectedCategory = if (isSelected) null else cat
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) TabletTerminalColors.EmeraldLight else TabletTerminalColors.CardBg,
                            border = BorderStroke(1.dp, if (isSelected) TabletTerminalColors.Emerald else TabletTerminalColors.CardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TabletTerminalColors.Emerald)
                                    )
                                }
                                Text(
                                    text = cat,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = if (isSelected) TabletTerminalColors.Emerald else TabletTerminalColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                // Product Grid in Portrait (130dp columns)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (filteredProducts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.tablet_no_products_found),
                                color = TabletTerminalColors.TextTertiary,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredProducts, key = { it.barcode }) { product ->
                                TabletProductGridCard(
                                    product = product,
                                    inCartQuantity = cartItems.firstOrNull { it.product.barcode == product.barcode }?.quantity ?: 0.0,
                                    isStockCheckMode = !isSellMode,
                                    allowNegativeInventory = allowNegativeInventory,
                                    onClick = {
                                        if (isSellMode) {
                                             val added = cartViewModel.addToCart(product)
                                             if (added) {
                                                 beepAndVibrate(context)
                                             } else {
                                                 Toast.makeText(context, context.getString(R.string.tablet_out_of_stock_toast), Toast.LENGTH_SHORT).show()
                                             }
                                        } else {
                                             beepAndVibrate(context)
                                             scannerViewModel.selectProductForStockCheck(product)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Divider between Catalog and Cart Review
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(TabletTerminalColors.Divider)
            )

            // BOTTOM PANE: Cart Review (46% height)
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxWidth()
                    .background(TabletTerminalColors.CartSurfaceBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Quick Actions
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(TabletTerminalColors.Emerald, TabletTerminalColors.EmeraldDark)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Realistic3DSupermarketCartIcon(size = 18.dp)
                            }
                            Text(
                                text = stringResource(R.string.tablet_cart_review_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = TabletTerminalColors.TextMain
                            )
                            TabletCartCapsuleButton(
                                cartUnitCount = cartUnitCount,
                                onClick = onNavigateToCart,
                                modifier = Modifier.height(48.dp)
                            )
                        }

                        // Right: Hold + Clear
                        if (cartItems.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        cartViewModel.holdCurrentCart()
                                        Toast.makeText(context, context.getString(R.string.cart_held_success_toast), Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = TabletTerminalColors.CardBg,
                                    border = BorderStroke(1.dp, TabletTerminalColors.CardBorder),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.tablet_cart_hold), tint = TabletTerminalColors.Amber, modifier = Modifier.size(16.dp))
                                    }
                                }

                                 Surface(
                                    onClick = onClearCartRequested,
                                    shape = RoundedCornerShape(8.dp),
                                    color = TabletTerminalColors.CardBg,
                                    border = BorderStroke(1.dp, TabletTerminalColors.CardBorder),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.tablet_cart_clear), tint = TabletTerminalColors.Rose, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Cart Items Scrollable List
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
                    if (cartItems.isEmpty()) {
                        TabletEmptyCartView()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(cartItems, key = { it.product.barcode }) { item ->
                                TabletCartItemRow(
                                    item = item,
                                    allowNegativeInventory = allowNegativeInventory,
                                    onQuantityChange = { newQty ->
                                        if (newQty <= 0.0) {
                                            onRemoveItemRequested(item.product)
                                        } else {
                                            cartViewModel.setQuantity(item.product, newQty, allowNegative = allowNegativeInventory)
                                        }
                                    },
                                    onRemove = {
                                        onRemoveItemRequested(item.product)
                                    }
                                )
                            }
                        }
                    }
                }

                // Cart Footer & Checkout Panel (Matching Landscape 100%)
                TabletCartFooter(
                    subtotal = subtotal,
                    grandTotal = grandTotal,
                    cartItemsNotEmpty = cartItems.isNotEmpty(),
                    onNavigateToCart = onNavigateToCart
                )
            }
        }
    } else {
        // =========================================================================
        // HORIZONTAL (LANDSCAPE) VIEWPORT LAYOUT: 58% Catalog Left, 42% Cart Review Right
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(TabletTerminalColors.WorkspaceBg)
        ) {
            // LEFT PANE: Catalog, Search, Categories, Grid & Camera PIP (58% width)
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .background(TabletTerminalColors.CatalogSurfaceBg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabletTopToolbar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    showCameraPIP = showCameraPIP,
                    onToggleCameraPIP = { showCameraPIP = !showCameraPIP },
                    onManualEntry = onManualEntry,
                    canCheckStock = canCheckStock,
                    isSellMode = isSellMode,
                    onToggleStockMode = { onModeChanged(!isSellMode) },
                    heldCartCount = heldCartCount,
                    onShowHeldCarts = onShowHeldCarts,
                    isPortrait = false
                )

                // Collapsible Camera PIP
                AnimatedVisibility(
                    visible = showCameraPIP,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TabletCameraPipView(
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = onRequestCameraPermission,
                        scannerViewModel = scannerViewModel,
                        cartViewModel = cartViewModel,
                        scanningPaused = scanningPaused,
                        torchEnabled = torchEnabled,
                        onTorchToggle = onTorchToggle,
                        onScanningPausedToggle = onScanningPausedToggle,
                        onCameraControlReady = onCameraControlReady
                    )
                }

                // Stock check banner when active
                if (!isSellMode) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TabletTerminalColors.AmberLight,
                        border = BorderStroke(1.dp, TabletTerminalColors.AmberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = TabletTerminalColors.Amber, modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.tablet_stock_check_banner),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TabletTerminalColors.Amber
                            )
                        }
                    }
                }

                // Category Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        val isAllSelected = selectedCategory == null
                        Surface(
                            onClick = { selectedCategory = null },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAllSelected) TabletTerminalColors.EmeraldLight else TabletTerminalColors.CardBg,
                            border = BorderStroke(1.dp, if (isAllSelected) TabletTerminalColors.Emerald else TabletTerminalColors.CardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isAllSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TabletTerminalColors.Emerald)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.tablet_category_all),
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isAllSelected) TabletTerminalColors.Emerald else TabletTerminalColors.TextSecondary
                                )
                            }
                        }
                    }
                    items(categories) { cat ->
                        val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                        Surface(
                            onClick = {
                                selectedCategory = if (isSelected) null else cat
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) TabletTerminalColors.EmeraldLight else TabletTerminalColors.CardBg,
                            border = BorderStroke(1.dp, if (isSelected) TabletTerminalColors.Emerald else TabletTerminalColors.CardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TabletTerminalColors.Emerald)
                                    )
                                }
                                Text(
                                    text = cat,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) TabletTerminalColors.Emerald else TabletTerminalColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                // Product Grid (135dp columns)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (filteredProducts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.tablet_no_products_found),
                                color = TabletTerminalColors.TextTertiary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 135.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredProducts, key = { it.barcode }) { product ->
                                TabletProductGridCard(
                                    product = product,
                                    inCartQuantity = cartItems.firstOrNull { it.product.barcode == product.barcode }?.quantity ?: 0.0,
                                    isStockCheckMode = !isSellMode,
                                    allowNegativeInventory = allowNegativeInventory,
                                    onClick = {
                                        if (isSellMode) {
                                            val added = cartViewModel.addToCart(product)
                                            if (added) {
                                                beepAndVibrate(context)
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.tablet_out_of_stock_toast), Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            beepAndVibrate(context)
                                            scannerViewModel.selectProductForStockCheck(product)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Vertical Divider
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(TabletTerminalColors.Divider)
            )

            // RIGHT PANE: Pinned Live Cart Review (42% width)
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .background(TabletTerminalColors.CartSurfaceBg)
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Cart Review Header
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(TabletTerminalColors.Emerald, TabletTerminalColors.EmeraldDark)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Realistic3DSupermarketCartIcon(size = 20.dp)
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.tablet_cart_review_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TabletTerminalColors.TextMain
                                )
                                Text(
                                    text = stringResource(R.string.tablet_cart_review_subtitle),
                                    fontSize = 11.sp,
                                    color = TabletTerminalColors.TextSecondary
                                )
                            }
                        }

                        // 3D Indigo Cart Capsule matching Products Screen top bar
                        TabletCartCapsuleButton(
                            cartUnitCount = cartUnitCount,
                            onClick = onNavigateToCart
                        )
                    }


                    // Quick actions: Hold & Clear
                    if (cartItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    cartViewModel.holdCurrentCart()
                                    Toast.makeText(context, context.getString(R.string.cart_held_success_toast), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = TabletTerminalColors.CardBg,
                                border = BorderStroke(1.dp, TabletTerminalColors.CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.tablet_cart_hold), modifier = Modifier.size(16.dp), tint = TabletTerminalColors.Amber)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.tablet_cart_hold), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TabletTerminalColors.Amber)
                                }
                            }

                            Surface(
                                onClick = onClearCartRequested,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = TabletTerminalColors.CardBg,
                                border = BorderStroke(1.dp, TabletTerminalColors.CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.tablet_cart_clear), modifier = Modifier.size(16.dp), tint = TabletTerminalColors.Rose)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.tablet_cart_clear), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TabletTerminalColors.Rose)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Cart Items Scrollable List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (cartItems.isEmpty()) {
                        TabletEmptyCartView()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(cartItems, key = { it.product.barcode }) { item ->
                                TabletCartItemRow(
                                    item = item,
                                    allowNegativeInventory = allowNegativeInventory,
                                    onQuantityChange = { newQty ->
                                        if (newQty <= 0.0) {
                                            onRemoveItemRequested(item.product)
                                        } else {
                                            cartViewModel.setQuantity(item.product, newQty, allowNegative = allowNegativeInventory)
                                        }
                                    },
                                    onRemove = {
                                        onRemoveItemRequested(item.product)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Cart Review Footer & Full-width Checkout CTA
                TabletCartFooter(
                    subtotal = subtotal,
                    grandTotal = grandTotal,
                    cartItemsNotEmpty = cartItems.isNotEmpty(),
                    onNavigateToCart = onNavigateToCart
                )
            }
}
}
}
}

@Composable
private fun TabletTopToolbar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showCameraPIP: Boolean,
    onToggleCameraPIP: () -> Unit,
    onManualEntry: () -> Unit,
    canCheckStock: Boolean,
    isSellMode: Boolean,
    onToggleStockMode: () -> Unit,
    heldCartCount: Int,
    onShowHeldCarts: () -> Unit,
    isPortrait: Boolean,
    modifier: Modifier = Modifier
) {
    var isSearchFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Search text field (Sleek Input with radiant emerald focus & 44dp height matching buttons)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = TabletTerminalColors.InputBg,
            border = BorderStroke(
                width = if (isSearchFocused) 1.5.dp else 1.dp,
                color = if (isSearchFocused) TabletTerminalColors.Emerald else TabletTerminalColors.CardBorder
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.products_search_cd),
                    tint = if (isSearchFocused) TabletTerminalColors.Emerald else TabletTerminalColors.Emerald.copy(alpha = 0.85f),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tablet_search_placeholder),
                            fontSize = if (isPortrait) 12.sp else 12.5.sp,
                            color = TabletTerminalColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TabletTerminalColors.TextMain,
                            fontSize = if (isPortrait) 12.5.sp else 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(TabletTerminalColors.Emerald),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isSearchFocused = it.isFocused }
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            tint = TabletTerminalColors.TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 1. Scanner Button
        Surface(
            onClick = onToggleCameraPIP,
            shape = RoundedCornerShape(10.dp),
            color = if (showCameraPIP) TabletTerminalColors.EmeraldLight else TabletTerminalColors.CardBg,
            border = BorderStroke(1.dp, if (showCameraPIP) TabletTerminalColors.Emerald else TabletTerminalColors.CardBorder),
            modifier = Modifier.height(48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (isPortrait) 8.dp else 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = if (showCameraPIP) TabletTerminalColors.Emerald else TabletTerminalColors.TextMain,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tablet_action_scan),
                    fontSize = if (isPortrait) 11.sp else 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showCameraPIP) TabletTerminalColors.Emerald else TabletTerminalColors.TextMain
                )
            }
        }

        // 2. Keypad Button
        Surface(
            onClick = onManualEntry,
            shape = RoundedCornerShape(10.dp),
            color = TabletTerminalColors.CardBg,
            border = BorderStroke(1.dp, TabletTerminalColors.CardBorder),
            modifier = Modifier.height(48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (isPortrait) 8.dp else 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = TabletTerminalColors.TextMain,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.tablet_action_numpad),
                    fontSize = if (isPortrait) 11.sp else 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TabletTerminalColors.TextMain
                )
            }
        }

        // 3. Stock Check Mode Button
        if (canCheckStock) {
            val isStockCheckActive = !isSellMode
            Surface(
                onClick = onToggleStockMode,
                shape = RoundedCornerShape(10.dp),
                color = if (isStockCheckActive) TabletTerminalColors.AmberLight else TabletTerminalColors.CardBg,
                border = BorderStroke(1.dp, if (isStockCheckActive) TabletTerminalColors.Amber else TabletTerminalColors.CardBorder),
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (isPortrait) 8.dp else 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        tint = if (isStockCheckActive) TabletTerminalColors.Amber else TabletTerminalColors.TextMain,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.tablet_action_stock),
                        fontSize = if (isPortrait) 11.sp else 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStockCheckActive) TabletTerminalColors.Amber else TabletTerminalColors.TextMain
                    )
                }
            }
        }

        // 4. Held Carts Button
        if (heldCartCount > 0) {
            Surface(
                onClick = onShowHeldCarts,
                shape = RoundedCornerShape(10.dp),
                color = TabletTerminalColors.AmberLight,
                border = BorderStroke(1.dp, TabletTerminalColors.AmberBorder),
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (isPortrait) 8.dp else 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PauseCircle,
                        contentDescription = null,
                        tint = TabletTerminalColors.Amber,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${stringResource(R.string.tablet_action_held)} ($heldCartCount)",
                        fontSize = if (isPortrait) 11.sp else 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TabletTerminalColors.Amber
                    )
                }
            }
        }
    }
}


@Composable
private fun TabletCartFooter(
    subtotal: Double,
    grandTotal: Double,
    cartItemsNotEmpty: Boolean,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TabletTerminalColors.FooterBg,
        border = BorderStroke(1.dp, TabletTerminalColors.CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Subtotal Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tablet_subtotal),
                    fontSize = 12.5.sp,
                    color = TabletTerminalColors.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTaka(subtotal),
                    fontSize = 13.sp,
                    color = TabletTerminalColors.TextMain,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Estimated Grand Total Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tablet_estimated_total),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = TabletTerminalColors.TextMain
                )
                Text(
                    text = formatTaka(grandTotal),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = TabletTerminalColors.Emerald
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Primary Full-width CTA Button: Radiant Emerald Gradient
            Button(
                onClick = onNavigateToCart,
                enabled = cartItemsNotEmpty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = TabletTerminalColors.TextDisabled
                ),
                contentPadding = PaddingValues(0.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (cartItemsNotEmpty) {
                                Brush.horizontalGradient(
                                    listOf(TabletTerminalColors.Emerald, TabletTerminalColors.EmeraldDark)
                                )
                            } else {
                                Brush.linearGradient(
                                    if (TabletTerminalColors.isDark) listOf(Color(0xFF26334D), Color(0xFF1E273D))
                                    else listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Realistic3DSupermarketCartIcon(size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tablet_proceed_to_cart, formatTaka(grandTotal)),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.5.sp,
                            color = if (cartItemsNotEmpty) Color.White else TabletTerminalColors.TextDisabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletEmptyCartView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = TabletTerminalColors.CardBg,
            border = BorderStroke(1.dp, TabletTerminalColors.CardBorder),
            shadowElevation = 6.dp,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Realistic3DSupermarketCartIcon(size = 34.dp)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tablet_cart_empty_title),
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            color = TabletTerminalColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.tablet_cart_empty_subtitle),
            fontSize = 12.sp,
            color = TabletTerminalColors.TextTertiary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TabletCameraPipView(
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    scannerViewModel: ScannerViewModel,
    cartViewModel: CartViewModel,
    scanningPaused: Boolean,
    torchEnabled: Boolean,
    onTorchToggle: () -> Unit,
    onScanningPausedToggle: () -> Unit,
    onCameraControlReady: (androidx.camera.core.CameraControl) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasCameraPermission) {
                CameraPreviewView(
                    scannerViewModel = scannerViewModel,
                    cartViewModel = cartViewModel,
                    scanningEnabled = !scanningPaused,
                    torchEnabled = torchEnabled,
                    onCameraControlReady = onCameraControlReady,
                    modifier = Modifier.fillMaxSize()
                )
                ScanFrameOverlay()
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.camera_permission_required), color = Color.White, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(onClick = onRequestCameraPermission) {
                        Text(stringResource(R.string.grant_permission), fontSize = 12.sp)
                    }
                }
            }

            // Mini PIP controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledIconButton(
                    onClick = onTorchToggle,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (torchEnabled) Color(0xFFFFD54F) else Color.DarkGray
                    )
                ) {
                    Realistic3DTacticalFlashlightIcon(torchEnabled = torchEnabled, size = 18.dp)
                }

                FilledIconButton(
                    onClick = onScanningPausedToggle,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (scanningPaused) Color(0xFFEF5350) else Color.DarkGray
                    )
                ) {
                    Realistic3DPauseDiscIcon(isPaused = scanningPaused, size = 18.dp)
                }
            }
        }
    }
}

@Composable
private fun TabletProductGridCard(
    product: Product,
    inCartQuantity: Double,
    isStockCheckMode: Boolean = false,
    allowNegativeInventory: Boolean = false,
    onClick: () -> Unit
) {
    val isOutOfStock = product.stockQty <= 0.0
    val isLowStock = !isOutOfStock && product.stockQty <= product.reorderLevel
    val isInCart = inCartQuantity > 0.0

    Card(
        onClick = onClick,
        enabled = if (isStockCheckMode) true else (!isOutOfStock || allowNegativeInventory),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = TabletTerminalColors.CardBg,
            disabledContainerColor = if (isStockCheckMode) TabletTerminalColors.CardBg else TabletTerminalColors.CardBg.copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            1.dp,
            when {
                isInCart -> TabletTerminalColors.Emerald
                isStockCheckMode -> TabletTerminalColors.AmberBorder
                else -> TabletTerminalColors.CardBorder
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: 3D Visual Box + Stock Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.linearGradient(
                                    if (TabletTerminalColors.isDark) listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF))
                                    else listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                                )
                            )
                            .border(
                                1.dp,
                                if (TabletTerminalColors.isDark) Color(0x26FFFFFF) else Color(0xFFCBD5E1),
                                RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Realistic3DProductsBoxIcon(size = 20.dp)
                    }

                    val badgeInfo = when {
                        isOutOfStock -> TabletStockBadgeInfo(
                            TabletTerminalColors.RoseLight,
                            TabletTerminalColors.RoseBorder,
                            TabletTerminalColors.Rose,
                            stringResource(R.string.tablet_stock_out)
                        )
                        isLowStock -> TabletStockBadgeInfo(
                            TabletTerminalColors.AmberLight,
                            TabletTerminalColors.AmberBorder,
                            TabletTerminalColors.Amber,
                            "${product.stockQty.toInt()} ${product.unit}"
                        )
                        else -> TabletStockBadgeInfo(
                            TabletTerminalColors.EmeraldLight,
                            Color(0x4010B981),
                            TabletTerminalColors.Emerald,
                            "${product.stockQty.toInt()} ${product.unit}"
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = badgeInfo.bg,
                        border = BorderStroke(1.dp, badgeInfo.border)
                    ) {
                        Text(
                            text = badgeInfo.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeInfo.textColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Middle: Product Name & Barcode
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = product.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        lineHeight = 15.5.sp,
                        color = TabletTerminalColors.TextMain
                    )
                    if (product.barcode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = product.barcode,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TabletTerminalColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom Row: Vibrant Emerald Price + In-Cart Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTaka(product.sellingPrice),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = TabletTerminalColors.Emerald,
                        maxLines = 1,
                        softWrap = false
                    )

                    if (isInCart) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TabletTerminalColors.Emerald,
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = "${inCartQuantity.toInt()}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletCartItemRow(
    item: CartItem,
    allowNegativeInventory: Boolean = false,
    onQuantityChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    var showQuantityDialog by remember { mutableStateOf(false) }

    if (showQuantityDialog) {
        val isCustomItem = isSyntheticSaleBarcode(item.product.barcode) ||
                item.product.barcode.startsWith("CUSTOM-") ||
                item.product.barcode.startsWith("ADHOC-") ||
                item.product.stockQty >= 999999.0

        ManualQuantityDialog(
            currentQuantity = item.quantity,
            availableStock = item.product.stockQty,
            allowNegativeInventory = allowNegativeInventory,
            isCustomItem = isCustomItem,
            unit = item.product.unit,
            onConfirm = { qty ->
                if (qty <= 0.0) {
                    onRemove()
                } else {
                    onQuantityChange(qty)
                }
                showQuantityDialog = false
            },
            onDismiss = { showQuantityDialog = false }
        )
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TabletTerminalColors.CardBg),
        border = BorderStroke(1.dp, TabletTerminalColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Column: Item Name (up to 3 lines, NO cut-off!) + Price breakdown
            Column(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = item.product.name,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = TabletTerminalColors.TextMain
                )
                Spacer(modifier = Modifier.height(3.dp))
                val itemQtyDisplay = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatTaka(item.product.sellingPrice)} × $itemQtyDisplay = ",
                        fontSize = 11.5.sp,
                        color = TabletTerminalColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTaka(item.product.sellingPrice * item.quantity),
                        fontSize = 12.sp,
                        color = TabletTerminalColors.Emerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Row: Stepper Controls + Delete Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TabletTerminalColors.InputBg,
                    border = BorderStroke(1.dp, TabletTerminalColors.CardBorder)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (item.quantity <= 1.0) {
                                    onRemove()
                                } else {
                                    onQuantityChange(item.quantity - 1.0)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = stringResource(R.string.cd_decrease),
                                tint = TabletTerminalColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Surface(
                            onClick = { showQuantityDialog = true },
                            color = Color.Transparent,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            val displayQty = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
                            Text(
                                text = displayQty,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.5.sp,
                                color = TabletTerminalColors.TextMain,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(min = 28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (allowNegativeInventory || item.product.stockQty <= 0.0 || item.quantity + 1.0 <= item.product.stockQty) {
                                    onQuantityChange(item.quantity + 1.0)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_increase),
                                tint = TabletTerminalColors.Emerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_remove),
                        tint = TabletTerminalColors.Rose,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SellHeader(
    isSellMode: Boolean,
    itemCount: Double,
    total: Double,
    onCartClick: () -> Unit,
    onLockClick: (() -> Unit)? = null
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Realistic3DTechyfiBrandIcon(size = 18.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TECHYFI ",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "POS",
                            color = Color(0xFF0072FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSellMode) {
                        Realistic3DCashDrawerSellIcon(size = 34.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = stringResource(R.string.sell_mode),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Realistic3DStockPyramidIcon(size = 32.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = stringResource(R.string.stock_check_mode),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onLockClick != null) {
                    IconButton(
                        onClick = onLockClick,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.sidebar_lock_terminal_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    onClick = onCartClick,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Realistic3DSmartCartIcon(size = 22.dp)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(stringResource(R.string.scanner_current_sale), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (itemCount > 0.0) {
                                val badgeStr = if (itemCount % 1.0 == 0.0) itemCount.toLong().toString() else String.format(Locale.US, "%.2f", itemCount).trimEnd('0').trimEnd('.')
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .background(Color(0xFFEF4444), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = badgeStr,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        Text(formatTaka(total), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Realistic3DChevronRightIcon(size = 14.dp)
                }
            }
        }
        }
    }
}

@Composable
private fun SellControlBar(
    isSellMode: Boolean,
    onModeChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(4.dp)
        ) {
            listOf(
                true to stringResource(R.string.tablet_mode_sell),
                false to stringResource(R.string.tablet_mode_stock)
            ).forEach { (sell, label) ->
                val selected = isSellMode == sell
                Surface(
                    onClick = { onModeChanged(sell) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 3.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (sell) {
                            Realistic3DPosTerminalSellIcon(size = 24.dp)
                        } else {
                            Realistic3DStockPyramidIcon(size = 22.dp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerStatusMessage(
    scanningPaused: Boolean,
    isSellMode: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xCC07130F),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    scanningPaused -> "Scanner Paused"
                    else -> "Align Barcode Inside The Frame"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    scanningPaused -> "Tap Resume when you are ready"
                    !isSellMode -> "Camera + hardware scanner • view or adjust stock"
                    else -> "Camera + hardware scanner ready"
                },
                color = Color(0xFFB6C9C1),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ScannerToolDock(
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    scanningPaused: Boolean,
    onTorchClick: () -> Unit,
    onManualEntry: () -> Unit,
    onPauseChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xE6101D18),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ScannerToolButton(
                label = if (torchEnabled) "Flash On" else "Flash",
                icon = { Realistic3DTacticalFlashlightIcon(torchEnabled = torchEnabled, size = 22.dp) },
                enabled = torchAvailable,
                selected = torchEnabled,
                onClick = onTorchClick,
                modifier = Modifier.weight(1f)
            )
            ScannerToolButton(
                label = stringResource(R.string.scanner_enter_code_label),
                icon = { Realistic3DDigitKeypadIcon(size = 22.dp) },
                onClick = onManualEntry,
                modifier = Modifier.weight(1f)
            )
            ScannerToolButton(
                label = if (scanningPaused) "Resume" else "Pause",
                icon = { Realistic3DPauseDiscIcon(isPaused = scanningPaused, size = 22.dp) },
                selected = scanningPaused,
                onClick = onPauseChanged,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScannerToolButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = if (enabled) Color.White else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactScanAlert(
    confirmation: com.example.techyfipos.ui.viewmodel.AddedProductConfirmation,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xEE301313),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF7B78))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(stringResource(R.string.out_of_stock), color = Color(0xFFFF7B78), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(confirmation.productName, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LastScannedItemCard(
    product: Product,
    quantity: Double,
    onUndo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Realistic3DStockPyramidIcon(size = 28.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.scanner_last_scanned), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp)
                Text(
                    product.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                val qStr = if (quantity % 1.0 == 0.0) quantity.toLong().toString() else String.format(Locale.US, "%.2f", quantity).trimEnd('0').trimEnd('.')
                Text("${formatTaka(product.sellingPrice)} · Qty $qStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.scanner_undo), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SellCheckoutBar(
    itemCount: Double,
    total: Double,
    onReviewCart: () -> Unit
) {
    val checkoutTransition = rememberInfiniteTransition(label = "checkoutBarAnim")
    val arrowSlide by checkoutTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowSlide"
    )

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val itemCountStr = if (itemCount % 1.0 == 0.0) itemCount.toLong().toString() else String.format(Locale.US, "%.2f", itemCount).trimEnd('0').trimEnd('.')
                Text("$itemCountStr ${if (itemCount == 1.0) "item" else "items"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Text(formatTaka(total), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Button(
                onClick = onReviewCart,
                enabled = itemCount > 0.0,
                modifier = Modifier.height(50.dp),
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Realistic3DMoldedCartIcon(size = 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cart_review_button), fontWeight = FontWeight.Bold)
                    if (itemCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        val badgeText = if (itemCount % 1.0 == 0.0) {
                            itemCount.toLong().toString()
                        } else {
                            String.format(Locale.US, "%.2f", itemCount).trimEnd('0').trimEnd('.')
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                .background(Color(0xFFEF4444), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Realistic3DForwardArrowIcon(size = 18.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartPreviewBottomSheet(
    cartItems: List<com.example.techyfipos.ui.viewmodel.CartItem>,
    total: Double,
    allowNegativeInventory: Boolean = false,
    onQuantityChanged: (Product, Double) -> Unit,
    onRemove: (Product) -> Unit,
    onDismiss: () -> Unit,
    onHoldCart: () -> Unit,
    onContinue: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var itemToSetQuantity by remember { mutableStateOf<com.example.techyfipos.ui.viewmodel.CartItem?>(null) }

    if (itemToSetQuantity != null) {
        val currentItem = itemToSetQuantity!!
        val isCustomItem = isSyntheticSaleBarcode(currentItem.product.barcode) ||
                currentItem.product.barcode.startsWith("CUSTOM-") ||
                currentItem.product.barcode.startsWith("ADHOC-") ||
                currentItem.product.stockQty >= 999999.0

        ManualQuantityDialog(
            currentQuantity = currentItem.quantity,
            availableStock = currentItem.product.stockQty,
            allowNegativeInventory = allowNegativeInventory,
            isCustomItem = isCustomItem,
            unit = currentItem.product.unit,
            onConfirm = { qty ->
                onQuantityChanged(currentItem.product, qty)
                itemToSetQuantity = null
            },
            onDismiss = { itemToSetQuantity = null }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(stringResource(R.string.scanner_current_sale_upper), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛒", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cart_review_button), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))

            if (cartItems.isEmpty()) {
                Text(stringResource(R.string.scanner_cart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 28.dp))
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cartItems.forEach { item ->
                        val minusAnim = remember { Animatable(1f) }
                        val plusAnim = remember { Animatable(1f) }
                        val removeAnim = remember { Animatable(0f) }
                        val qtyBounceAnim = remember { Animatable(1f) }
                        val itemQtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')

                        LaunchedEffect(item.quantity) {
                            qtyBounceAnim.animateTo(1.35f, tween(90, easing = FastOutSlowInEasing))
                            qtyBounceAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }

                        val unitPriceStr = formatTaka(item.product.sellingPrice)
                        val totalAmountStr = formatTaka(item.product.sellingPrice * item.quantity)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                // Full Width Product Name on Top (Optimized for Bangla & English scripts)
                                Text(
                                    text = item.product.name,
                                    style = TextStyle(
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 21.sp,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                        lineHeightStyle = LineHeightStyle(
                                            alignment = LineHeightStyle.Alignment.Center,
                                            trim = LineHeightStyle.Trim.None
                                        )
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(6.dp))

                                // Bottom Row: Price & Total on Left, Stepper & Trash on Right
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: Unit Price & Line Total Pill
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = "$unitPriceStr/${item.product.unit}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.scanner_total_fmt, totalAmountStr),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Right: Stepper controls
                                    val isFractionalUnit = isFractionalUnit(item.product.unit)
                                    val stepVal = if (isFractionalUnit) 0.25 else 1.0

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Decrease Button
                                        Surface(
                                            onClick = {
                                                scope.launch {
                                                    minusAnim.animateTo(0.82f, tween(70))
                                                    minusAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                }
                                                val next = (Math.round((item.quantity - stepVal) * 100.0) / 100.0).coerceAtLeast(0.0)
                                                onQuantityChanged(item.product, next)
                                            },
                                            shape = RoundedCornerShape(9.dp),
                                            color = Color(0xFFFEE2E2),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "➖",
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.graphicsLayer {
                                                        scaleX = minusAnim.value
                                                        scaleY = minusAnim.value
                                                    }
                                                )
                                            }
                                        }

                                        // Quantity counter badge (tap to enter manual quantity)
                                        Surface(
                                            onClick = { itemToSetQuantity = item },
                                            shape = RoundedCornerShape(9.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .defaultMinSize(minWidth = 36.dp)
                                                .padding(horizontal = 1.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 7.dp)
                                            ) {
                                                Text(
                                                    text = itemQtyStr,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.graphicsLayer {
                                                        scaleX = qtyBounceAnim.value
                                                        scaleY = qtyBounceAnim.value
                                                    }
                                                )
                                            }
                                        }

                                        // Increase Button
                                        Surface(
                                            onClick = {
                                                val next = Math.round((item.quantity + stepVal) * 100.0) / 100.0
                                                if (allowNegativeInventory || item.product.stockQty <= 0.0 || next <= item.product.stockQty) {
                                                    scope.launch {
                                                        plusAnim.animateTo(1.35f, tween(70))
                                                        plusAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                    }
                                                    onQuantityChanged(item.product, next)
                                                }
                                            },
                                            shape = RoundedCornerShape(9.dp),
                                            color = Color(0xFF10B981),
                                            contentColor = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "➕",
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.graphicsLayer {
                                                        scaleX = plusAnim.value
                                                        scaleY = plusAnim.value
                                                    }
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(1.dp))

                                        // Remove Single Item Button
                                        Surface(
                                            onClick = {
                                                scope.launch {
                                                    removeAnim.animateTo(-20f, tween(50))
                                                    removeAnim.animateTo(20f, tween(50))
                                                    removeAnim.animateTo(0f, tween(50))
                                                }
                                                onRemove(item.product)
                                            },
                                            shape = RoundedCornerShape(9.dp),
                                            color = Color(0xFFFEE2E2),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "🗑️",
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.graphicsLayer { rotationZ = removeAnim.value }
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

            Spacer(Modifier.height(14.dp))

            // Cart Summary Footer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalUnits = cartItems.sumOf { it.quantity }
                val totalUnitsStr = if (totalUnits % 1.0 == 0.0) totalUnits.toLong().toString() else String.format(Locale.US, "%.2f", totalUnits).trimEnd('0').trimEnd('.')
                Text(stringResource(R.string.scanner_total_items_fmt, totalUnitsStr), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text(formatTaka(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onHoldCart,
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Text("⏸️", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scanner_hold_cart), fontWeight = FontWeight.Bold, color = Color(0xFFD97706), fontSize = 13.5.sp)
                }

                Button(
                    onClick = onContinue,
                    enabled = cartItems.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    )
                ) {
                    Text("🛍️", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.checkout), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("➔", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun CameraPreviewView(
    scannerViewModel: ScannerViewModel,
    cartViewModel: CartViewModel,
    scanningEnabled: Boolean,
    torchEnabled: Boolean,
    onCameraControlReady: (androidx.camera.core.CameraControl) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val latestScanningEnabled by rememberUpdatedState(scanningEnabled)

    val snackbarHostState = remember { SnackbarHostState() }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                    )
                    .build()

                imageAnalysis.setAnalyzer(
                    cameraExecutor,
                    BarcodeAnalyzer(
                        getPreviewSize = { android.util.Size(previewView.width, previewView.height) },
                        onBarcodeDetected = { barcode ->
                            if (!latestScanningEnabled) return@BarcodeAnalyzer
                            scope.launch {
                                scannerViewModel.onBarcodeScanned(
                                    barcode = barcode,
                                    cartViewModel = cartViewModel,
                                    onSoundVibrate = { beepAndVibrate(ctx) },
                                    onShowSnackbar = { name, price, stock, total, isSuccess ->
                                        scope.launch {
                                            val msg = if (isSuccess) {
                                                "$name - ${formatTaka(price)} | Stock: $stock (Total: ${formatTaka(total)})"
                                            } else {
                                                "$name - OUT OF STOCK! | Stock: $stock"
                                            }
                                            snackbarHostState.showSnackbar(
                                                message = msg,
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                )
                            }
                        },
                        onNoBarcodeInFrame = {
                            scope.launch {
                                scannerViewModel.onNoBarcodeInFrame()
                            }
                        }
                    )
                )

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    onCameraControlReady(camera.cameraControl)
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}


@Composable
fun ScanFrameOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val animatedY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset"
    )

    val reticleTransition = rememberInfiniteTransition(label = "reticlePulse")
    val reticleScale by reticleTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticleScale"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Responsive scan box sizing that works elegantly in both portrait phone and landscape tablet banner
        val boxHeight = minOf(height * 0.78f, width * 0.75f, 260.dp.toPx())
        val boxWidth = minOf(width * 0.85f, boxHeight * 2.2f, 480.dp.toPx())
        val left = (width - boxWidth) / 2f
        val top = (height - boxHeight) / 2f
        val right = left + boxWidth
        val bottom = top + boxHeight
        val centerX = width / 2f
        val centerY = height / 2f

        // 1. Soft scrim outside the scan box, with CRYSTAL CLEAR view inside the scan box using EvenOdd fill rule
        val scrimPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, width, height))
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, right, bottom),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            )
        }
        drawPath(
            path = scrimPath,
            color = Color.Black.copy(alpha = 0.32f)
        )

        // 2. Draw border frame corners in vibrant Emerald Green
        val cornerSize = minOf(24.dp.toPx(), boxWidth * 0.15f, boxHeight * 0.25f)
        val strokeWidth = 3.5.dp.toPx()
        val cornerColor = Color(0xFF10B981) // Emerald Green

        // Top Left
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerSize, top), strokeWidth)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerSize), strokeWidth)

        // Top Right
        drawLine(cornerColor, Offset(right, top), Offset(right - cornerSize, top), strokeWidth)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerSize), strokeWidth)

        // Bottom Left
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerSize, bottom), strokeWidth)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerSize), strokeWidth)

        // Bottom Right
        drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerSize, bottom), strokeWidth)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerSize), strokeWidth)

        // 3. Draw animated glowing reticle in exact center
        drawCircle(
            color = Color(0x3310B981),
            radius = 16.dp.toPx() * reticleScale,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = Color(0xFF10B981),
            radius = 8.dp.toPx(),
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF10B981),
            radius = 3.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        // 4. Draw animated laser scan line
        val lineY = top + (boxHeight * animatedY)
        drawLine(
            color = Color(0xFF10B981), // Emerald green laser scan line
            start = Offset(left + 8.dp.toPx(), lineY),
            end = Offset(right - 8.dp.toPx(), lineY),
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockCheckBottomSheet(
    product: Product,
    onAdjustStock: (Double) -> Unit,
    onSetStock: (Double) -> Unit,
    onEdit: () -> Unit,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val initialQty = remember(product.barcode) { product.stockQty }
    var stockInputText by remember(product.barcode) {
        mutableStateOf(
            if (product.stockQty % 1.0 == 0.0) product.stockQty.toLong().toString()
            else String.format(Locale.US, "%.2f", product.stockQty).trimEnd('0').trimEnd('.')
        )
    }
    val quantity = stockInputText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row with 3D Floating Isometric Package Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Realistic3DProductsBoxIcon(size = 46.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = product.barcode,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        if (product.category.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEEF2FF),
                                border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                            ) {
                                Text(
                                    text = product.category,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Inventory Level & Health Status Card
            Surface(
                color = Color(0xFFF5F3FF),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "INVENTORY LEVEL",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8)
                        )
                        Text(
                            "$quantity units",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF3B0764)
                        )
                    }
                    StockStatus3DBadge(
                        quantity = quantity,
                        reorderLevel = product.reorderLevel
                    )
                }
            }

            // Financial Intelligence Card (Purchase, Selling, Margin, Asset Valuation)
            FinancialMarginCard(
                purchasePrice = product.purchasePrice,
                sellingPrice = product.sellingPrice,
                quantity = quantity,
                mrpPrice = product.mrpPrice,
                wholesalePrice = product.wholesalePrice
            )

            // Date Tracking Info Row with 3D Calendar Icon
            val mfgDate = product.mfgOrPkgDate
            val expDate = product.expiryDate
            val hasDate = !mfgDate.isNullOrBlank() || !expDate.isNullOrBlank()
            val mfgTypeLabel = if (product.mfgOrPkgType.equals("pkg", ignoreCase = true)) "PKG" else "MFG"

            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Realistic3DCalendarIcon(size = 24.dp)
                        Text(
                            "Date Tracking",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }
                    if (hasDate) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!mfgDate.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "$mfgTypeLabel: $mfgDate",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (!expDate.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "EXP: $expDate",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "No Date Set",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Stock Stepper with Delta Indicator
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ADJUST QUANTITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StockDeltaPill(
                        initialQty = initialQty,
                        currentQty = quantity
                    )
                }

                // Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = {
                            val newQty = (quantity - 1.0).coerceAtLeast(0.0)
                            stockInputText = if (newQty % 1.0 == 0.0) newQty.toLong().toString() else String.format(Locale.US, "%.2f", newQty).trimEnd('0').trimEnd('.')
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF64748B)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_decrease_stock), tint = Color.White)
                    }

                    OutlinedTextField(
                        value = stockInputText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                stockInputText = filtered.take(7)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    )

                    FilledIconButton(
                        onClick = {
                            val newQty = quantity + 1.0
                            stockInputText = if (newQty % 1.0 == 0.0) newQty.toLong().toString() else String.format(Locale.US, "%.2f", newQty).trimEnd('0').trimEnd('.')
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF059669)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_increase_stock), tint = Color.White)
                    }
                }

                // Quick increment chips (+1, +5, +10, Zero, Reset)
                QuickStepperChipsRow(
                    onIncrement = { delta ->
                        val newQty = quantity + delta
                        stockInputText = if (newQty % 1.0 == 0.0) newQty.toLong().toString() else String.format(Locale.US, "%.2f", newQty).trimEnd('0').trimEnd('.')
                    },
                    onZero = {
                        stockInputText = "0"
                    },
                    onReset = {
                        stockInputText = if (initialQty % 1.0 == 0.0) initialQty.toLong().toString() else String.format(Locale.US, "%.2f", initialQty).trimEnd('0').trimEnd('.')
                    }
                )
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons Row: Cancel, Edit (in-between), Save with Realistic 3D Animated Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Cancel Button with 3D Cancel Icon
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFFF1F2),
                        contentColor = Color(0xFFBE123C)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Realistic3DCancelIcon(size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // 2. Edit Button with 3D Edit Icon
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFEEF2FF),
                        contentColor = Color(0xFF4338CA)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Realistic3DEditIcon(size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Edit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // 3. Save Button with 3D Save Icon
                Button(
                    onClick = {
                        onSave(quantity)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Realistic3DSaveIcon(size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            // Reorder check warning
            val isOut = product.stockQty <= 0.0
            val isLow = !isOut && product.stockQty <= product.reorderLevel
            if (isOut || isLow) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(if (isOut) R.string.out_of_stock else R.string.warning_stock_low),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBarcodeDialog(
    onDismiss: () -> Unit,
    onBarcodeEntered: (String) -> Unit
) {
    var barcode by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        HardwareBarcodeScanner.barcodeEvents.collect { code ->
            val englishCode = toEnglishDigits(code.trim())
            if (englishCode.isNotBlank()) {
                barcode = englishCode
                isError = false
                onBarcodeEntered(englishCode)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                "QUICK ADD",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = .8.sp
            )
            Text(stringResource(R.string.enter_barcode), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = barcode,
                onValueChange = {
                    barcode = toEnglishDigits(it)
                    isError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.barcode)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.invalid_input)) }
                } else null,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.scanner_barcode_placeholder)) },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val cleanBarcode = toEnglishDigits(barcode.trim())
                    if (cleanBarcode.isNotBlank()) {
                        onBarcodeEntered(cleanBarcode)
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.scanner_find_add_product), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// A product barcode/QR is a short alphanumeric code, never a URL, sentence, or
// other free text that happens to be printed as a QR on packaging or marketing material.
private fun isPlausibleProductCode(value: String): Boolean {
    if (value.length !in 4..32) return false
    if (value.contains("://") || value.contains(" ") || value.contains(".")) return false
    return value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

// CameraX Barcode Analyzer binding ML Kit
class BarcodeAnalyzer(
    private val getPreviewSize: () -> android.util.Size,
    private val onBarcodeDetected: (String) -> Unit,
    private val onNoBarcodeInFrame: () -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_QR_CODE
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    /**
     * Verifies that the detected barcode is within the visible camera preview area
     * (accounting for ScaleType.FILL_CENTER cropping), preventing phantom scans of items
     * outside the user's screen while ensuring responsive scanning across the entire viewfinder.
     */
    private fun isBarcodeInVisiblePreview(box: android.graphics.Rect?, imageWidth: Int, imageHeight: Int): Boolean {
        if (box == null) return true
        val previewSize = getPreviewSize()
        val previewW = previewSize.width
        val previewH = previewSize.height
        if (previewW <= 0 || previewH <= 0) return true

        val scale = maxOf(previewW.toFloat() / imageWidth, previewH.toFloat() / imageHeight)
        val visibleImgW = previewW / scale
        val visibleImgH = previewH / scale
        val visibleLeft = (imageWidth - visibleImgW) / 2f
        val visibleTop = (imageHeight - visibleImgH) / 2f

        val visibleBounds = android.graphics.RectF(
            visibleLeft,
            visibleTop,
            visibleLeft + visibleImgW,
            visibleTop + visibleImgH
        )
        return visibleBounds.contains(box.exactCenterX(), box.exactCenterY())
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)
            // ML Kit reports bounding boxes in the upright (rotated) orientation.
            val imageWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
            val imageHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    var detectedAny = false
                    for (barcode in barcodes) {
                        val format = barcode.format
                        val value = barcode.rawValue ?: continue
                        // Prevent partial or cut-off scans of 1D barcodes
                        if (format != Barcode.FORMAT_QR_CODE && value.length < 6) {
                            continue
                        }
                        // Ignore QR codes that aren't plausible product codes (URLs, marketing
                        // links, free text printed on packaging) so pointing at other print on
                        // the package doesn't trigger a "Product not found" prompt.
                        if (format == Barcode.FORMAT_QR_CODE && !isPlausibleProductCode(value)) {
                            continue
                        }
                        // Ensure the barcode is within the visible camera preview
                        val box = barcode.boundingBox
                        if (!isBarcodeInVisiblePreview(box, imageWidth, imageHeight)) {
                            continue
                        }
                        detectedAny = true
                        onBarcodeDetected(value)
                    }
                    if (!detectedAny) {
                        onNoBarcodeInFrame()
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

@Composable
fun ProductAddedConfirmationBanner(
    confirmation: com.example.techyfipos.ui.viewmodel.AddedProductConfirmation,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto dismiss pop-up banner after 2.5 seconds
    LaunchedEffect(confirmation.timestamp) {
        kotlinx.coroutines.delay(2500L)
        onDismiss()
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(250)) + slideInVertically(initialOffsetY = { -it / 2 }) + expandVertically(),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it / 2 }) + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (confirmation.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)
            ),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Success / Warning Icon Badge
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (confirmation.isSuccess) Color(0xFF10B981).copy(alpha = 0.2f)
                            else Color(0xFFEF4444).copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (confirmation.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (confirmation.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (confirmation.isSuccess) "✓ PRODUCT ADDED TO CART" else "⚠ OUT OF STOCK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (confirmation.isSuccess) Color(0xFF10B981) else Color(0xFFEF4444),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // LARGE BOLD PRODUCT NAME
                    Text(
                        text = confirmation.productName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 26.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = formatTaka(confirmation.price),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "• Cart Total: ${formatTaka(confirmation.grandTotal)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_dismiss),
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
