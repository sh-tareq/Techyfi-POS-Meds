@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package com.example.techyfipos.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.ui.components.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.ui.viewmodel.CartViewModel
import com.example.techyfipos.ui.viewmodel.ScannerViewModel
import com.example.techyfipos.utils.beepAndVibrate
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.HardwareBarcodeScanner
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
    isOwner: Boolean = true,
    onNavigateToCart: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProducts: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isSellMode by scannerViewModel.isSellMode.collectAsStateWithLifecycle()

    // Stock Check mode mutates inventory, which is an owner-only operation server-side
    // (DataRepository.requireOwnerSession) — hide it from cashiers rather than let them enter a
    // mode whose mutations would throw uncaught and crash the app.
    LaunchedEffect(isOwner) {
        if (!isOwner) scannerViewModel.setMode(true)
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
            scannerViewModel.onBarcodeScanned(
                barcode = code,
                cartViewModel = cartViewModel,
                onSoundVibrate = { beepAndVibrate(context) },
                onShowSnackbar = { name, price, stock, total, isSuccess ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = if (isSuccess) {
                                "$name - ${formatTaka(price)} ${context.getString(R.string.add_to_cart)} | Stock: $stock (Total: ${formatTaka(total)})"
                            } else {
                                "$name - OUT OF STOCK! | Stock: $stock"
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SellHeader(
                isSellMode = isSellMode,
                itemCount = cartUnitCount,
                total = grandTotal,
                onCartClick = { showCartPreview = true }
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
                if (isOwner) {
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
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF07130F))
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
                        onTorchClick = { scannerViewModel.torchEnabled.value = !torchEnabled },
                        onManualEntry = { scannerViewModel.showManualDialog.value = true },
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
                                    cartViewModel.updateQuantity(product, (item.quantity - 1.0).coerceAtLeast(0.0))
                                }
                                lastScannedProduct = null
                                scannerViewModel.dismissAddedConfirmation()
                            }
                        )
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
                            Toast.makeText(
                                context,
                                "✅ ${p.name} stock saved: $newQty units",
                                Toast.LENGTH_SHORT
                            ).show()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "✅ ${p.name} stock saved (${newQty} units)",
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
                StockCheckNotFoundDialog(
                    barcode = scannedBarcode!!,
                    onAddNow = {
                        val code = scannedBarcode
                        scannerViewModel.clearScanned()
                        onNavigateToProducts(code)
                    },
                    onDismiss = { scannerViewModel.clearScanned() }
                )
            }

            if (showManualDialog) {
                ManualBarcodeDialog(
                    onDismiss = { scannerViewModel.showManualDialog.value = false },
                    onBarcodeEntered = { barcode ->
                        scannerViewModel.showManualDialog.value = false
                        scannerViewModel.onBarcodeScanned(
                            barcode = barcode,
                            cartViewModel = cartViewModel,
                            onSoundVibrate = { beepAndVibrate(context) },
                            onShowSnackbar = { name, price, stock, total, isSuccess ->
                                scope.launch {
                                    val msg = if (isSuccess) {
                                        "$name - ${formatTaka(price)} ${context.getString(R.string.add_to_cart)} | Stock: $stock (Total: ${formatTaka(total)})"
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
                )
            }

            if (showCartPreview) {
                CartPreviewBottomSheet(
                    cartItems = cartItems,
                    total = grandTotal,
                    onQuantityChanged = { prod, qty -> cartViewModel.updateQuantity(prod, qty) },
                    onRemove = { prod -> cartViewModel.removeFromCart(prod) },
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
                        cartViewModel.restoreHeldCart(cart) { result ->
                            Toast.makeText(context,
                                if (result.unavailableLines > 0) "Restored available items; ${result.unavailableLines} unavailable line(s) remain saved"
                                else "Restored cart for ${cart.title}",
                                if (result.unavailableLines > 0) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDelete = { cartId ->
                        cartViewModel.deleteHeldCart(cartId)
                    },
                    onDismiss = { showHeldCartsSheet = false }
                )
            }
        }
    }
}

@Composable
private fun SellHeader(
    isSellMode: Boolean,
    itemCount: Double,
    total: Double,
    onCartClick: () -> Unit
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
                            Text("Current Sale", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        fontSize = 9.sp,
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
            listOf(true to "Sell", false to "Stock").forEach { (sell, label) ->
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
                label = "Enter Code",
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
                Text("Out Of Stock", color = Color(0xFFFF7B78), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                Text("LAST SCANNED", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp)
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
                Text("Undo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("Review Cart", fontWeight = FontWeight.Bold)
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
    onQuantityChanged: (Product, Double) -> Unit,
    onRemove: (Product) -> Unit,
    onDismiss: () -> Unit,
    onHoldCart: () -> Unit,
    onContinue: () -> Unit
) {
    val scope = rememberCoroutineScope()

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
            Text("CURRENT SALE", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .8.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛒", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text("Review Cart", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))

            if (cartItems.isEmpty()) {
                Text("Your Cart Is Empty.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 28.dp))
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
                                                    text = "Total: $totalAmountStr",
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
                                    val isFractionalUnit = item.product.unit.lowercase() in listOf("kg", "gm", "ltr", "liter", "litre", "mtr", "meter", "metre", "g", "ml")
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

                                        // Quantity counter badge
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .height(32.dp)
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
                                                if (item.product.stockQty <= 0.0 || next <= item.product.stockQty) {
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
                Text("Total · $totalUnitsStr items", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
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
                    Text("Hold Cart", fontWeight = FontWeight.Bold, color = Color(0xFFD97706), fontSize = 13.5.sp)
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
                    Text("Checkout", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
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

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
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
                    Executors.newSingleThreadExecutor(),
                    BarcodeAnalyzer(
                        getPreviewSize = { android.util.Size(previewView.width, previewView.height) },
                        onBarcodeDetected = { barcode ->
                            if (!latestScanningEnabled) return@BarcodeAnalyzer
                            scope.launch {
                                scannerViewModel.onBarcodeScanned(
                                    barcode = barcode,
                                    cartViewModel = cartViewModel,
                                    onSoundVibrate = { beepAndVibrate(context) },
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
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

const val SCAN_BOX_WIDTH_FRACTION = 0.75f
const val SCAN_BOX_HEIGHT_TO_WIDTH_RATIO = 0.6f

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
        val boxWidth = width * SCAN_BOX_WIDTH_FRACTION
        val boxHeight = boxWidth * SCAN_BOX_HEIGHT_TO_WIDTH_RATIO
        val left = (width - boxWidth) / 2
        val top = (height - boxHeight) / 2
        val right = left + boxWidth
        val bottom = top + boxHeight
        val centerX = (left + right) / 2
        val centerY = (top + bottom) / 2

        // 1. Draw a dark transparent background over the camera view
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                    )
                )
            },
            color = Color.Black.copy(alpha = 0.5f),
            blendMode = BlendMode.SrcOut
        )

        // 2. Draw border frame corners
        val cornerSize = 24.dp.toPx()
        val strokeWidth = 4.dp.toPx()
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
            color = Color(0x5510B981),
            radius = 18.dp.toPx() * reticleScale,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = Color(0xFF10B981),
            radius = 10.dp.toPx(),
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color(0xFF10B981),
            radius = 4.5.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        // 4. Draw animated scan line
        val lineY = top + (boxHeight * animatedY)
        drawLine(
            color = Color(0xFFF59E0B), // Glowing Amber scan line
            start = Offset(left + 8.dp.toPx(), lineY),
            end = Offset(right - 8.dp.toPx(), lineY),
            strokeWidth = 3.5.dp.toPx()
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
                Realistic3DIsometricPackageIcon(size = 46.dp)

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
                quantity = quantity
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
                                    color = Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "$mfgTypeLabel: $mfgDate",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (!expDate.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "EXP: $expDate",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626),
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
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease stock", tint = Color.White)
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
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase stock", tint = Color.White)
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
            if (product.stockQty <= product.reorderLevel) {
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
                            stringResource(R.string.warning_stock_low),
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
            Text("Enter Barcode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = barcode,
                onValueChange = {
                    barcode = it
                    isError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Barcode Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.invalid_input)) }
                } else null,
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                placeholder = { Text("e.g. 8941100501234") },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (barcode.isNotBlank()) {
                        onBarcodeEntered(barcode.trim())
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Find & Add Product", fontWeight = FontWeight.Bold)
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
     * The PreviewView renders with ScaleType.FILL_CENTER, which center-crops the camera
     * image to fill the view. A barcode's on-screen position only matches its position in
     * the *analyzed* image once that same center-crop is accounted for — otherwise a code
     * near the edge of the (wider) analyzed frame can be inside the visible preview while
     * outside the drawn scan box, or vice versa. This computes the scan box, in the
     * analyzed image's own coordinate space, so detections outside it can be dropped.
     */
    private fun scanBoxInImageSpace(imageWidth: Int, imageHeight: Int): android.graphics.RectF? {
        val previewSize = getPreviewSize()
        val previewW = previewSize.width
        val previewH = previewSize.height
        if (previewW <= 0 || previewH <= 0) return null

        val scale = maxOf(previewW.toFloat() / imageWidth, previewH.toFloat() / imageHeight)
        val visibleImgW = previewW / scale
        val visibleImgH = previewH / scale
        val visibleLeft = (imageWidth - visibleImgW) / 2f
        val visibleTop = (imageHeight - visibleImgH) / 2f

        val boxWidth = previewW * SCAN_BOX_WIDTH_FRACTION
        val boxHeight = boxWidth * SCAN_BOX_HEIGHT_TO_WIDTH_RATIO
        val boxLeft = (previewW - boxWidth) / 2f
        val boxTop = (previewH - boxHeight) / 2f

        return android.graphics.RectF(
            visibleLeft + boxLeft / scale,
            visibleTop + boxTop / scale,
            visibleLeft + (boxLeft + boxWidth) / scale,
            visibleTop + (boxTop + boxHeight) / scale
        )
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
            val scanBox = scanBoxInImageSpace(imageWidth, imageHeight)

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
                        // Only accept codes that actually fall within the on-screen scan box.
                        val box = barcode.boundingBox
                        if (scanBox != null && box != null) {
                            val centerX = box.exactCenterX()
                            val centerY = box.exactCenterY()
                            if (!scanBox.contains(centerX, centerY)) {
                                continue
                            }
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
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
