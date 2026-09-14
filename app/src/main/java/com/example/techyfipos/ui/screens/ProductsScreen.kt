@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package com.example.techyfipos.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import android.net.Uri
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.techyfipos.data.db.InventoryAdjustment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.ui.components.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.R
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.data.CategoryItem
import com.example.techyfipos.ui.viewmodel.ProductsViewModel
import com.example.techyfipos.utils.HardwareBarcodeScanner
import com.example.techyfipos.utils.toEnglishDigits
import com.example.techyfipos.utils.toBanglaDigits
import com.example.techyfipos.utils.beepAndVibrate
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.formatProductCodeLabelPriceString
import com.example.techyfipos.utils.ProductCodeLabelSize
import com.example.techyfipos.utils.ProductCodePriceTier
import com.example.techyfipos.utils.ProductCodeType
import com.example.techyfipos.utils.ProductLabelItem
import com.example.techyfipos.utils.cacheBatchProductCodeLabelsPdf
import com.example.techyfipos.utils.cacheProductCodeLabelPdf
import com.example.techyfipos.utils.generateProductCodeLabelBitmap
import com.example.techyfipos.utils.printPdfDocument
import com.example.techyfipos.utils.saveProductCodeLabelToPictures
import com.example.techyfipos.utils.shareProductCodeLabelPdf
import com.example.techyfipos.utils.parseExpiryDateToMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ProductAddedConfirmation(
    val productName: String,
    val cartUnitCount: Double
)

private enum class ProductStockFilter { ALL, AVAILABLE, LOW, OUT }
private enum class ProductDateFilter { ALL, TRACKED, EXPIRING_SOON, EXPIRED, NO_DATE }
private enum class ProductSortOption { NAME_ASC, NAME_DESC, STOCK_LOW, STOCK_HIGH, PRICE_LOW, PRICE_HIGH, EXPIRY_SOONEST }
private enum class ProductViewMode { LIST, GRID }

// Maps the stored (English) inventory-adjustment reason value to a localized display label.
// The underlying stored/compared value is left untouched — only the Text() shown to the user is localized.
@Composable
private fun localizedAdjustmentReasonLabel(reason: String): String = when (reason) {
    "All" -> stringResource(R.string.products_reason_all)
    "Restock" -> stringResource(R.string.products_reason_restock)
    "Damage" -> stringResource(R.string.products_reason_damage)
    "Expired" -> stringResource(R.string.products_reason_expired)
    "Discrepancy" -> stringResource(R.string.products_reason_discrepancy)
    "Sample" -> stringResource(R.string.products_reason_sample)
    "Store Use" -> stringResource(R.string.products_reason_store_use)
    "Internal Use" -> stringResource(R.string.products_reason_internal_use)
    "Other" -> stringResource(R.string.products_reason_other)
    else -> reason
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductsScreen(
    productsViewModel: ProductsViewModel,
    cartViewModel: com.example.techyfipos.ui.viewmodel.CartViewModel,
    prefilledBarcode: String?,
    isOwner: Boolean = true,
    canViewCosts: Boolean = isOwner,
    canEdit: Boolean = isOwner,
    canDelete: Boolean = isOwner,
    canAdjustStock: Boolean = isOwner,
    canPrintLabels: Boolean = isOwner,
    onBack: () -> Unit,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchQuery by productsViewModel.searchQuery.collectAsStateWithLifecycle()
    val productsList by productsViewModel.products.collectAsStateWithLifecycle()
    val allProductsList by productsViewModel.allProducts.collectAsStateWithLifecycle()
    val cartItems by cartViewModel.cartItems.collectAsStateWithLifecycle()
    val cartUnitCount = cartItems.sumOf { it.quantity }

    val totalSellingValue = remember(allProductsList) {
        allProductsList.sumOf { it.sellingPrice * it.stockQty }
    }
    val totalPurchaseValue = remember(allProductsList) {
        allProductsList.sumOf { it.purchasePrice * it.stockQty }
    }
    val totalStockUnits = remember(allProductsList) {
        allProductsList.sumOf { it.stockQty }
    }
    val totalSKUCount = allProductsList.size
    val lowStockCount = remember(allProductsList) {
        allProductsList.count { it.stockQty > 0.0 && it.stockQty <= it.reorderLevel }
    }
    val outOfStockCount = remember(allProductsList) {
        allProductsList.count { it.stockQty <= 0.0 }
    }

    val nowTimestamp = remember { System.currentTimeMillis() }
    val in30DaysTimestamp = remember { System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) }
    val dateTrackedCount = remember(allProductsList) {
        allProductsList.count { !it.expiryDate.isNullOrBlank() || !it.mfgOrPkgDate.isNullOrBlank() }
    }
    val expiringSoonCount = remember(allProductsList) {
        allProductsList.count { p ->
            if (!p.expiryDate.isNullOrBlank()) {
                val exp = parseExpiryDateToMillis(p.expiryDate)
                exp != null && exp > nowTimestamp && exp <= in30DaysTimestamp
            } else false
        }
    }
    val expiredCount = remember(allProductsList) {
        allProductsList.count { p ->
            if (!p.expiryDate.isNullOrBlank()) {
                val exp = parseExpiryDateToMillis(p.expiryDate)
                exp != null && exp <= nowTimestamp
            } else false
        }
    }
    val noDateCount = remember(allProductsList) {
        allProductsList.count { it.expiryDate.isNullOrBlank() && it.mfgOrPkgDate.isNullOrBlank() }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var scannedNewBarcodeForDialog by remember { mutableStateOf<String?>(null) }
    var selectedProductForEdit by remember { mutableStateOf<Product?>(null) }
    var productForCodeDialog by remember { mutableStateOf<Product?>(null) }
    var productForEnlargedImage by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var stockFilterName by rememberSaveable { mutableStateOf(ProductStockFilter.ALL.name) }
    var dateFilterName by rememberSaveable { mutableStateOf(ProductDateFilter.ALL.name) }
    var sortOptionName by rememberSaveable { mutableStateOf(ProductSortOption.NAME_ASC.name) }
    var viewModeName by rememberSaveable { mutableStateOf(if (isTablet) ProductViewMode.GRID.name else ProductViewMode.LIST.name) }
    var highMarginOnly by rememberSaveable { mutableStateOf(false) }
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var minimumPrice by rememberSaveable { mutableStateOf("") }
    var maximumPrice by rememberSaveable { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var addedConfirmation by remember { mutableStateOf<ProductAddedConfirmation?>(null) }
    var stockScannerEnabled by rememberSaveable { mutableStateOf(false) }
    var stockCheckProduct by remember { mutableStateOf<Product?>(null) }
    var stockNotFoundBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var showBatchPrintDialog by rememberSaveable { mutableStateOf(false) }
    var batchSelectMode by rememberSaveable { mutableStateOf(false) }
    val selectedBarcodes = remember { mutableStateListOf<String>() }
    var showBulkPriceDialog by rememberSaveable { mutableStateOf(false) }
    var showBulkCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var showBulkDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isScrolling by remember {
        derivedStateOf {
            listState.isScrollInProgress || gridState.isScrollInProgress ||
            listState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemIndex > 0
        }
    }

    val exportSuccessToast = stringResource(R.string.products_export_success)
    val exportExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                    if (stream != null) {
                        val success = stream.use { productsViewModel.exportProductsToExcel(it) }
                        if (success) {
                            Toast.makeText(context, exportSuccessToast, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Export failed. Check storage permissions.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Unable to open destination file.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProductsScreen", "Excel export failed", e)
                    Toast.makeText(context, "Export error: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val result = productsViewModel.importProductsFromExcel(inputStream)
                        val msg = context.getString(R.string.products_import_success, result.importedProducts.size)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorMsg = e.localizedMessage ?: "File could not be parsed"
                    Toast.makeText(context, "Excel Import Failed: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val cartInteractionSource = remember { MutableInteractionSource() }
    val isCartPressed by cartInteractionSource.collectIsPressedAsState()
    var cartAnimKey by remember { mutableLongStateOf(0L) }

    val cartIconAnimScale = remember { Animatable(1f) }
    val cartIconAnimRotation = remember { Animatable(0f) }
    val cartIconAnimOffsetY = remember { Animatable(0f) }
    val cartBadgeAnimScale = remember { Animatable(1f) }
    val cartBadgeAnimRotation = remember { Animatable(0f) }
    val cartCapsuleBounceScale = remember { Animatable(1f) }

    val cartPressScale by animateFloatAsState(
        targetValue = if (isCartPressed) 0.86f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cartPressScale"
    )

    fun requestEditProduct(product: Product) {
        if (canEdit) {
            selectedProductForEdit = product
        } else {
            Toast.makeText(context, context.getString(R.string.products_editing_owner_only), Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDeleteProduct(product: Product) {
        if (canDelete) {
            productToDelete = product
        } else {
            Toast.makeText(context, context.getString(R.string.products_deleting_owner_only), Toast.LENGTH_SHORT).show()
        }
    }

    val stockFilter = ProductStockFilter.valueOf(stockFilterName)
    val dateFilter = ProductDateFilter.valueOf(dateFilterName)
    val sortOption = ProductSortOption.valueOf(sortOptionName)
    val viewMode = ProductViewMode.valueOf(viewModeName)
    val availableCategories = remember(productsList) {
        productsList.map { it.category.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val activeFilterCount = selectedCategories.size +
        (if (stockFilter != ProductStockFilter.ALL) 1 else 0) +
        (if (highMarginOnly) 1 else 0) +
        (if (dateFilter != ProductDateFilter.ALL) 1 else 0) +
        (if (sortOption != ProductSortOption.NAME_ASC) 1 else 0) +
        (if (minimumPrice.isNotBlank()) 1 else 0) +
        (if (maximumPrice.isNotBlank()) 1 else 0)

    val visibleProducts = remember(
        productsList,
        stockFilter,
        dateFilter,
        highMarginOnly,
        selectedCategories,
        minimumPrice,
        maximumPrice,
        sortOption
    ) {
        val minPrice = minimumPrice.toDoubleOrNull()
        val maxPrice = maximumPrice.toDoubleOrNull()
        val now = System.currentTimeMillis()
        val in30Days = now + (30L * 24 * 60 * 60 * 1000)

        productsList
            .asSequence()
            .filter { product ->
                selectedCategories.isEmpty() || product.category.trim() in selectedCategories
            }
            .filter { product ->
                if (!highMarginOnly) true
                else product.purchasePrice > 0 && (product.sellingPrice - product.purchasePrice) / product.purchasePrice >= 0.3
            }
            .filter { product ->
                when (stockFilter) {
                    ProductStockFilter.ALL -> true
                    ProductStockFilter.AVAILABLE -> product.stockQty > 0.0
                    ProductStockFilter.LOW -> product.stockQty > 0.0 && product.stockQty <= product.reorderLevel
                    ProductStockFilter.OUT -> product.stockQty <= 0.0
                }
            }
            .filter { product ->
                when (dateFilter) {
                    ProductDateFilter.ALL -> true
                    ProductDateFilter.TRACKED -> !product.expiryDate.isNullOrBlank() || !product.mfgOrPkgDate.isNullOrBlank()
                    ProductDateFilter.EXPIRING_SOON -> {
                        if (!product.expiryDate.isNullOrBlank()) {
                            val exp = parseExpiryDateToMillis(product.expiryDate)
                            exp != null && exp > now && exp <= in30Days
                        } else false
                    }
                    ProductDateFilter.EXPIRED -> {
                        if (!product.expiryDate.isNullOrBlank()) {
                            val exp = parseExpiryDateToMillis(product.expiryDate)
                            exp != null && exp <= now
                        } else false
                    }
                    ProductDateFilter.NO_DATE -> product.expiryDate.isNullOrBlank() && product.mfgOrPkgDate.isNullOrBlank()
                }
            }
            .filter { product -> minPrice == null || product.sellingPrice >= minPrice }
            .filter { product -> maxPrice == null || product.sellingPrice <= maxPrice }
            .sortedWith(
                when (sortOption) {
                    ProductSortOption.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    ProductSortOption.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name }
                    ProductSortOption.STOCK_LOW -> compareBy<Product> { it.stockQty }.thenBy { it.name }
                    ProductSortOption.STOCK_HIGH -> compareByDescending<Product> { it.stockQty }.thenBy { it.name }
                    ProductSortOption.PRICE_LOW -> compareBy<Product> { it.sellingPrice }.thenBy { it.name }
                    ProductSortOption.PRICE_HIGH -> compareByDescending<Product> { it.sellingPrice }.thenBy { it.name }
                    ProductSortOption.EXPIRY_SOONEST -> compareBy<Product> {
                        if (!it.expiryDate.isNullOrBlank()) {
                            parseExpiryDateToMillis(it.expiryDate) ?: Long.MAX_VALUE
                        } else Long.MAX_VALUE
                    }.thenBy { it.name }
                }
            )
            .toList()
    }

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

    LaunchedEffect(addedConfirmation) {
        if (addedConfirmation != null) {
            delay(3_500)
            addedConfirmation = null
        }
    }

    val allAlerts by productsViewModel.allAlerts.collectAsStateWithLifecycle()
    var showInventoryAlertsModal by remember { mutableStateOf(false) }
    val allAdjustments by productsViewModel.allInventoryAdjustments.collectAsStateWithLifecycle()
    var showStockAuditHistoryModal by remember { mutableStateOf(false) }

    // Prefill launch check
    LaunchedEffect(prefilledBarcode) {
        if (!prefilledBarcode.isNullOrBlank()) {
            showAddDialog = true
        }
    }

    // Hardware Barcode Scanner listener (USB HID, Bluetooth barcode gun, physical POS scanner)
    LaunchedEffect(Unit) {
        HardwareBarcodeScanner.barcodeEvents.collect { code ->
            val trimmedCode = toEnglishDigits(code.trim())
            if (trimmedCode.isNotBlank()) {
                if (showAddDialog || selectedProductForEdit != null) {
                    scannedNewBarcodeForDialog = trimmedCode
                    beepAndVibrate(context)
                } else if (productToDelete == null) {
                    val product = productsViewModel.getProductByBarcode(trimmedCode)
                    beepAndVibrate(context)
                    if (stockScannerEnabled) {
                        if (product != null) {
                            stockCheckProduct = product
                        } else {
                            stockNotFoundBarcode = trimmedCode
                        }
                    } else if (product != null) {
                        val added = cartViewModel.addToCart(product)
                        if (added) {
                            addedConfirmation = ProductAddedConfirmation(
                                productName = product.name,
                                cartUnitCount = cartViewModel.cartItems.value.sumOf { it.quantity }
                            )
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.products_out_of_stock_snackbar, product.name),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    } else {
                        stockNotFoundBarcode = trimmedCode
                    }
                }
            }
        }
    }

    val addProductToCart: (Product) -> Unit = { product ->
        val added = cartViewModel.addToCart(product)
        if (added) {
            addedConfirmation = ProductAddedConfirmation(
                productName = product.name,
                cartUnitCount = cartViewModel.cartItems.value.sumOf { it.quantity }
            )
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "${product.name} - OUT OF STOCK!",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val stockPulseTransition = rememberInfiniteTransition(label = "stockPulseTransition")
    val stockPulseScale by stockPulseTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stockPulseScale"
    )
    val stockPulseRotate by stockPulseTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stockPulseRotate"
    )

    val addIconTransition = rememberInfiniteTransition(label = "addIconTransition")
    val addIconScale by addIconTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "addIconScale"
    )
    val addIconRotate by addIconTransition.animateFloat(
        initialValue = 0f,
        targetValue = 90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "addIconRotate"
    )

    val cartIconTransition = rememberInfiniteTransition(label = "cartIconTransition")
    val cartIconBounce by cartIconTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cartIconBounce"
    )
    val cartIconWiggle by cartIconTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cartIconWiggle"
    )

    var isInventoryCardExpanded by rememberSaveable { mutableStateOf(false) }
    val inStockCount = remember(allProductsList) {
        allProductsList.count { it.stockQty > it.reorderLevel }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            var showExcelMenu by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        PosTopAppBarTitle(
                            title = stringResource(R.string.products),
                            metricBadge = if (allProductsList.isNotEmpty()) "${allProductsList.size} SKUs" else null,
                            badgeColor = PosSectionColors.Products,
                            isTablet = isTablet,
                            icon = { size -> Realistic3DProductsBoxIcon(size = size) }
                        )
                    },
                    navigationIcon = {
                        if (!isTablet) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                            }
                        }
                    },
                    actions = {
                        // 3D Ringing Brass Bell for Inventory Alerts with Non-Clipping Badge (Present on both phone and tablet)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showInventoryAlertsModal = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Realistic3DBrassAlertBellIcon(size = 22.dp)
                            }
                            if (allAlerts.totalCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .background(
                                            if (allAlerts.expiredProducts.isNotEmpty()) Color(0xFFE11D48) else Color(0xFFF59E0B),
                                            CircleShape
                                        )
                                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .size(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val countText = if (allAlerts.totalCount > 99) "99+" else allAlerts.totalCount.toString()
                                    Text(
                                        text = countText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        lineHeight = 9.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        // 3D Indigo Cart Capsule with Tap & Add Animations
                        Surface(
                            onClick = {
                                cartAnimKey = System.currentTimeMillis()
                                onNavigateToCart()
                            },
                            interactionSource = cartInteractionSource,
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4F46E5),
                            contentColor = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(36.dp)
                                .graphicsLayer {
                                    scaleX = cartPressScale * cartCapsuleBounceScale.value
                                    scaleY = cartPressScale * cartCapsuleBounceScale.value
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
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
                                Text(stringResource(R.string.cart), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                                fontSize = 9.5.sp,
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
                )

                // Integrated Horizontal Quick-Tool Ribbon (Consistent across Phone and Tablet)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = if (isTablet) 14.dp else 12.dp, vertical = 4.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        // 1. Excel Tools Chip
                        Box {
                            Surface(
                                onClick = { showExcelMenu = true },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = "Excel Tools",
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Excel Tools",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showExcelMenu,
                                onDismissRequest = { showExcelMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.products_import_excel), fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showExcelMenu = false
                                        importExcelLauncher.launch(
                                            arrayOf(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                "application/vnd.ms-excel",
                                                "application/octet-stream"
                                            )
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.products_export_excel), fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showExcelMenu = false
                                        val cleanDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                                        exportExcelLauncher.launch("products-$cleanDate.xlsx")
                                    }
                                )
                            }
                        }

                        // 2. Batch Select Mode Chip
                        Surface(
                            onClick = {
                                batchSelectMode = !batchSelectMode
                                if (!batchSelectMode) selectedBarcodes.clear()
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (batchSelectMode) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (batchSelectMode) Color(0xFF6EE7B7) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = if (batchSelectMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                    contentDescription = "Batch Operations",
                                    tint = if (batchSelectMode) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (batchSelectMode) "Exit Batch" else "Batch Select",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (batchSelectMode) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 3. Stock Audit History Chip
                        Surface(
                            onClick = { showStockAuditHistoryModal = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Realistic3DClipboardDuesIcon(size = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Stock Audit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 4. Batch Print Labels Chip
                        Surface(
                            onClick = { showBatchPrintDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "Print Labels",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Print Labels",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
            }
        },
        floatingActionButton = {
            if (!isTablet) {
                // 🟢 3D Emerald Jewel Floating Action Button (Collapses to jewel on scroll)
                AnimatedContent(
                    targetState = isScrolling,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.85f))
                            .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.85f))
                    },
                    label = "fab_collapse_anim"
                ) { scrolling ->
                    if (scrolling) {
                        FloatingActionButton(
                            onClick = { showAddDialog = true },
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.products_add_product_button), modifier = Modifier.size(24.dp), tint = Color.White)
                        }
                    } else {
                        ExtendedFloatingActionButton(
                            onClick = { showAddDialog = true },
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(26.dp),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                            icon = {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    }
                                }
                            },
                            text = {
                                Text(
                                    stringResource(R.string.products_add_product_button),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 📊 Executive Inventory Valuation & Stock Health Gauge Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .clickable { isInventoryCardExpanded = !isInventoryCardExpanded },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📊", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.products_inventory_valuation_health),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFA5B4FC),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (isInventoryCardExpanded) stringResource(R.string.products_collapse_arrow) else stringResource(R.string.products_details_arrow),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.products_total_selling_value_skus, totalSKUCount),
                                fontSize = 10.5.sp,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                formatTaka(totalSellingValue),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        if (canViewCosts) {
                            val marginPct = if (totalPurchaseValue > 0) {
                                ((totalSellingValue - totalPurchaseValue) / totalPurchaseValue * 100.0)
                            } else 0.0
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(stringResource(R.string.products_margin_label), fontSize = 8.5.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold)
                                    Text(
                                        String.format(Locale.US, "+%.1f%%", marginPct),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF6EE7B7)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Live Segmented Health Progress Bar
                    val inStockRatio = if (totalSKUCount > 0) (inStockCount.toFloat() / totalSKUCount.toFloat()) else 1f
                    val lowStockRatio = if (totalSKUCount > 0) (lowStockCount.toFloat() / totalSKUCount.toFloat()) else 0f
                    val outStockRatio = if (totalSKUCount > 0) (outOfStockCount.toFloat() / totalSKUCount.toFloat()) else 0f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        if (inStockRatio > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(inStockRatio.coerceAtLeast(0.01f))
                                    .background(Color(0xFF10B981))
                            )
                        }
                        if (lowStockRatio > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(lowStockRatio.coerceAtLeast(0.01f))
                                    .background(Color(0xFFF59E0B))
                            )
                        }
                        if (outStockRatio > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(outStockRatio.coerceAtLeast(0.01f))
                                    .background(Color(0xFFEF4444))
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isInventoryCardExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(stringResource(R.string.products_purchase_cost_label), fontSize = 8.5.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                                    Text(if (canViewCosts) formatTaka(totalPurchaseValue) else "••••••", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text(stringResource(R.string.products_in_stock_units_label), fontSize = 8.5.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                                    val uStr = if (totalStockUnits % 1.0 == 0.0) totalStockUnits.toLong().toString() else String.format(Locale.US, "%.1f", totalStockUnits)
                                    Text(stringResource(R.string.products_units_pcs, uStr), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6EE7B7))
                                }
                                Column {
                                    Text(stringResource(R.string.products_stock_alerts_label), fontSize = 8.5.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.products_low_out_summary, lowStockCount, outOfStockCount), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCD34D))
                                }
                            }
                        }
                    }
                }
            }

            // ⚡ Batch Product Operations Selection Bar
            AnimatedVisibility(
                visible = batchSelectMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val allVisibleSelected = visibleProducts.isNotEmpty() && visibleProducts.all { it.barcode in selectedBarcodes }
                            IconButton(
                                onClick = {
                                    if (allVisibleSelected) {
                                        visibleProducts.forEach { selectedBarcodes.remove(it.barcode) }
                                    } else {
                                        visibleProducts.forEach {
                                            if (it.barcode !in selectedBarcodes) selectedBarcodes.add(it.barcode)
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (allVisibleSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Select All",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "${selectedBarcodes.size} selected",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { showBulkPriceDialog = true },
                                enabled = selectedBarcodes.isNotEmpty() && canEdit,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Price %", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { showBulkCategoryDialog = true },
                                enabled = selectedBarcodes.isNotEmpty() && canEdit,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Category", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (canDelete) {
                                Button(
                                    onClick = { showBulkDeleteDialog = true },
                                    enabled = selectedBarcodes.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                                }
                            }

                            IconButton(
                                onClick = {
                                    batchSelectMode = false
                                    selectedBarcodes.clear()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Exit Selection",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 🔎 Single Fast-Action Command Row: Search + Scanner + 3D Filter + View Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Full-width Search Box with 3D Optical Magnifier & Embedded Laser Scanner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.products_search_cd),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.products_search_placeholder),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { productsViewModel.setSearchQuery(it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { productsViewModel.setSearchQuery("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_search),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        // Embedded 3D Hardware Barcode Scanner Button
                        Surface(
                            onClick = {
                                stockScannerEnabled = !stockScannerEnabled
                                if (stockScannerEnabled) {
                                    Toast.makeText(context, context.getString(R.string.products_barcode_scanner_activated), Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (stockScannerEnabled) Color(0xFF4F46E5) else Color(0xFFEEF2FF),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Realistic3DHardwareBarcodeScannerIcon(size = 20.dp, animate = true)
                            }
                        }
                    }
                }

                // Native Tune/Filter Button (Opens Filter Bottom Sheet)
                val hasActiveFilters = selectedCategories.isNotEmpty() ||
                        stockFilter != ProductStockFilter.ALL ||
                        highMarginOnly ||
                        minimumPrice.isNotEmpty() ||
                        maximumPrice.isNotEmpty()

                Surface(
                    onClick = { showFilters = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasActiveFilters) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (hasActiveFilters) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.products_filter_cd),
                            modifier = Modifier.size(20.dp),
                            tint = if (hasActiveFilters) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasActiveFilters) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(8.dp)
                                    .background(Color(0xFFEF4444), CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }

                // Native View Mode Switcher (Grid Default on Tablet / List)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { viewModeName = ProductViewMode.GRID.name },
                            shape = RoundedCornerShape(8.dp),
                            color = if (viewMode == ProductViewMode.GRID) Color(0xFFEEF2FF) else Color.Transparent,
                            border = if (viewMode == ProductViewMode.GRID) BorderStroke(1.dp, Color(0xFFA5B4FC)) else null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.products_grid_view_cd),
                                    modifier = Modifier.size(19.dp),
                                    tint = if (viewMode == ProductViewMode.GRID) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            onClick = { viewModeName = ProductViewMode.LIST.name },
                            shape = RoundedCornerShape(8.dp),
                            color = if (viewMode == ProductViewMode.LIST) Color(0xFFEEF2FF) else Color.Transparent,
                            border = if (viewMode == ProductViewMode.LIST) BorderStroke(1.dp, Color(0xFFA5B4FC)) else null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = stringResource(R.string.products_list_view_cd),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (viewMode == ProductViewMode.LIST) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Primary Action Button on Tablet (+ Add Product)
                if (isTablet) {
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_product), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // =======================================================================
            // Quick Category & Status Filter Chips Strip (Directly Below Search Bar)
            // Exactly matching HTML Preview lines 3420-3448 & user screenshot
            // =======================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. All SKUs
                val isAllSkus = stockFilter == ProductStockFilter.ALL && !highMarginOnly
                Surface(
                    onClick = {
                        stockFilterName = ProductStockFilter.ALL.name
                        highMarginOnly = false
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isAllSkus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isAllSkus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    shadowElevation = if (isAllSkus) 1.dp else 0.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "All SKUs",
                            fontSize = 12.sp,
                            fontWeight = if (isAllSkus) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAllSkus) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isAllSkus) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${allProductsList.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAllSkus) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // 2. ⚠️ Low Stock
                val isLowActive = stockFilter == ProductStockFilter.LOW
                Surface(
                    onClick = {
                        stockFilterName = if (isLowActive) ProductStockFilter.ALL.name else ProductStockFilter.LOW.name
                        highMarginOnly = false
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isLowActive) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isLowActive) Color(0xFFF59E0B) else Color(0xFFF59E0B).copy(alpha = 0.45f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚠️ Low Stock",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "$lowStockCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // 3. 🚫 Out of Stock
                val isOutActive = stockFilter == ProductStockFilter.OUT
                Surface(
                    onClick = {
                        stockFilterName = if (isOutActive) ProductStockFilter.ALL.name else ProductStockFilter.OUT.name
                        highMarginOnly = false
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isOutActive) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isOutActive) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.45f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🚫 Out of Stock",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "$outOfStockCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // 4. 💎 High Margin (>30%)
                Surface(
                    onClick = {
                        highMarginOnly = !highMarginOnly
                        if (highMarginOnly) {
                            stockFilterName = ProductStockFilter.ALL.name
                        }
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = if (highMarginOnly) Color(0xFFD1FAE5) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (highMarginOnly) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.45f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "💎 High Margin (>30%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857)
                        )
                    }
                }

                // Vertical Divider
                Text(
                    text = "|",
                    color = MaterialTheme.colorScheme.outlineVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                // 5. All Categories
                val isAllCats = selectedCategories.isEmpty()
                Surface(
                    onClick = { selectedCategories = emptySet() },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isAllCats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isAllCats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = "All Categories",
                        fontSize = 12.sp,
                        fontWeight = if (isAllCats) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllCats) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                // 6. Dynamic Categories with Emojis & Counts
                availableCategories.forEach { category ->
                    val isCatActive = category in selectedCategories
                    val emoji = getCategoryEmoji(category)
                    Surface(
                        onClick = {
                            selectedCategories = if (isCatActive) emptySet() else setOf(category)
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (isCatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (isCatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "$emoji $category",
                                fontSize = 12.sp,
                                fontWeight = if (isCatActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCatActive) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Active Filter Indicator Strip (Visible only when filters are active)
            val hasActiveFilterBar = selectedCategories.isNotEmpty() ||
                    stockFilter != ProductStockFilter.ALL ||
                    dateFilter != ProductDateFilter.ALL ||
                    sortOption != ProductSortOption.NAME_ASC ||
                    minimumPrice.isNotEmpty() ||
                    maximumPrice.isNotEmpty()

            if (hasActiveFilterBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEEF2FF))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val expiringSoonLabel = stringResource(R.string.products_filter_expiring_soon)
                    val expiredLabel = stringResource(R.string.products_filter_expired)
                    val trackedLabel = stringResource(R.string.products_filter_tracked)
                    val untrackedLabel = stringResource(R.string.products_filter_untracked)
                    val nameZALabel = stringResource(R.string.products_sort_name_za)
                    val stockLowLabel = stringResource(R.string.products_sort_stock_low)
                    val stockHighLabel = stringResource(R.string.products_sort_stock_high)
                    val priceLowLabel = stringResource(R.string.products_sort_price_low)
                    val priceHighLabel = stringResource(R.string.products_sort_price_high)
                    val expirySoonLabel = stringResource(R.string.products_sort_expiry_soon)
                    val filterSummary = buildList {
                        if (selectedCategories.isNotEmpty()) add(selectedCategories.joinToString(", "))
                        if (stockFilter != ProductStockFilter.ALL) add(stockFilter.name)
                        if (dateFilter != ProductDateFilter.ALL) add(when (dateFilter) {
                            ProductDateFilter.EXPIRING_SOON -> expiringSoonLabel
                            ProductDateFilter.EXPIRED -> expiredLabel
                            ProductDateFilter.TRACKED -> trackedLabel
                            ProductDateFilter.NO_DATE -> untrackedLabel
                            ProductDateFilter.ALL -> ""
                        })
                        if (sortOption != ProductSortOption.NAME_ASC) add(when (sortOption) {
                            ProductSortOption.NAME_DESC -> nameZALabel
                            ProductSortOption.STOCK_LOW -> stockLowLabel
                            ProductSortOption.STOCK_HIGH -> stockHighLabel
                            ProductSortOption.PRICE_LOW -> priceLowLabel
                            ProductSortOption.PRICE_HIGH -> priceHighLabel
                            ProductSortOption.EXPIRY_SOONEST -> expirySoonLabel
                            ProductSortOption.NAME_ASC -> ""
                        })
                        if (minimumPrice.isNotEmpty() || maximumPrice.isNotEmpty()) add("৳$minimumPrice-৳$maximumPrice")
                    }.joinToString(" • ")

                    Text(
                        text = stringResource(R.string.products_filtered_summary, filterSummary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4338CA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {
                            selectedCategories = emptySet()
                            stockFilterName = ProductStockFilter.ALL.name
                            dateFilterName = ProductDateFilter.ALL.name
                            sortOptionName = ProductSortOption.NAME_ASC.name
                            minimumPrice = ""
                            maximumPrice = ""
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(stringResource(R.string.products_clear_x), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF4444))
                    }
                }
            }

            AnimatedVisibility(
                visible = addedConfirmation != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                addedConfirmation?.let { conf ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFDCFCE7),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Realistic3DGlowingEmeraldSphere(size = 14.dp)
                                Column {
                                    Text(
                                        text = stringResource(R.string.products_added_to_cart),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF047857)
                                    )
                                    Text(
                                        text = stringResource(R.string.products_added_to_cart_detail, conf.productName, if (conf.cartUnitCount % 1.0 == 0.0) conf.cartUnitCount.toInt().toString() else conf.cartUnitCount.toString()),
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF065F46)
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = onNavigateToCart,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(stringResource(R.string.products_view_cart), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Products Content Area
            if (allProductsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Realistic3DProductsBoxIcon(size = 72.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.products_empty_no_products),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.products_empty_add_first),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isOwner) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.products_add_product_button))
                            }
                        }
                    }
                }
            } else if (visibleProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Realistic3DConsoleDialsIcon(size = 56.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.products_empty_no_match),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.products_empty_try_adjusting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                selectedCategories = emptySet()
                                stockFilterName = ProductStockFilter.ALL.name
                                dateFilterName = ProductDateFilter.ALL.name
                                sortOptionName = ProductSortOption.NAME_ASC.name
                                minimumPrice = ""
                                maximumPrice = ""
                            }
                        ) {
                            Text(stringResource(R.string.products_reset_filters))
                        }
                    }
                }
            } else {
                if (viewMode == ProductViewMode.LIST) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleProducts, key = { it.barcode }) { product ->
                            val isSelected = product.barcode in selectedBarcodes
                            ProductItemRow(
                                product = product,
                                isTablet = isTablet,
                                isSelectionMode = batchSelectMode,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    if (isSelected) selectedBarcodes.remove(product.barcode)
                                    else selectedBarcodes.add(product.barcode)
                                },
                                onRowClick = {
                                    if (batchSelectMode) {
                                        if (isSelected) selectedBarcodes.remove(product.barcode)
                                        else selectedBarcodes.add(product.barcode)
                                    }
                                },
                                onRowLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!batchSelectMode) {
                                        batchSelectMode = true
                                        selectedBarcodes.clear()
                                        selectedBarcodes.add(product.barcode)
                                    } else {
                                        if (isSelected) selectedBarcodes.remove(product.barcode)
                                        else selectedBarcodes.add(product.barcode)
                                    }
                                },
                                onAdjustStock = { stockCheckProduct = product },
                                onEdit = { requestEditProduct(product) },
                                onGenerateCode = {
                                    if (canPrintLabels) {
                                        productForCodeDialog = product
                                    } else {
                                        android.widget.Toast.makeText(context, "Permission required to print labels", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onImageClick = { productForEnlargedImage = Pair(product.imageUri, product.name) },
                                onDelete = { requestDeleteProduct(product) },
                                onAddToCart = { addProductToCart(product) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = if (isTablet) GridCells.Adaptive(minSize = 300.dp) else GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(visibleProducts, key = { it.barcode }) { product ->
                            val isSelected = product.barcode in selectedBarcodes
                            ProductGridCard(
                                product = product,
                                isSelectionMode = batchSelectMode,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    if (isSelected) selectedBarcodes.remove(product.barcode)
                                    else selectedBarcodes.add(product.barcode)
                                },
                                onCardClick = {
                                    if (batchSelectMode) {
                                        if (isSelected) selectedBarcodes.remove(product.barcode)
                                        else selectedBarcodes.add(product.barcode)
                                    }
                                },
                                onCardLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!batchSelectMode) {
                                        batchSelectMode = true
                                        selectedBarcodes.clear()
                                        selectedBarcodes.add(product.barcode)
                                    } else {
                                        if (isSelected) selectedBarcodes.remove(product.barcode)
                                        else selectedBarcodes.add(product.barcode)
                                    }
                                },
                                onAdjustStock = { stockCheckProduct = product },
                                onEdit = { requestEditProduct(product) },
                                onGenerateCode = {
                                    if (canPrintLabels) {
                                        productForCodeDialog = product
                                    } else {
                                        android.widget.Toast.makeText(context, "Permission required to print labels", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onImageClick = { productForEnlargedImage = Pair(product.imageUri, product.name) },
                                onDelete = { requestDeleteProduct(product) },
                                onAddToCart = { addProductToCart(product) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val hasActiveFilters = selectedCategories.isNotEmpty() ||
                stockFilter != ProductStockFilter.ALL ||
                dateFilter != ProductDateFilter.ALL ||
                sortOption != ProductSortOption.NAME_ASC ||
                minimumPrice.isNotBlank() ||
                maximumPrice.isNotBlank()

        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = filterSheetState,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Box(modifier = Modifier.size(width = 36.dp, height = 4.dp))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Header Bar with 3D Console Icon, Title, Live Counter, and Quick Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Realistic3DConsoleDialsIcon(size = 22.dp)
                        Text(
                            text = stringResource(R.string.products_filter_sort_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEEF2FF),
                            border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                        ) {
                            Text(
                                text = stringResource(R.string.products_matching_count, visibleProducts.size),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (hasActiveFilters) {
                        Surface(
                            onClick = {
                                selectedCategories = emptySet()
                                stockFilterName = ProductStockFilter.ALL.name
                                dateFilterName = ProductDateFilter.ALL.name
                                sortOptionName = ProductSortOption.NAME_ASC.name
                                minimumPrice = ""
                                maximumPrice = ""
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEE2E2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA))
                        ) {
                            Text(
                                text = stringResource(R.string.products_clear_all),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Categories (Horizontal Scrollable Pill Bar)
                Text(
                    text = stringResource(R.string.products_category_section),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (availableCategories.isEmpty()) {
                    Text(
                        stringResource(R.string.no_categories),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isAllSelected = selectedCategories.isEmpty()
                        Surface(
                            onClick = { selectedCategories = emptySet() },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAllSelected) Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = if (isAllSelected) BorderStroke(1.dp, Color(0xFF4338CA)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.products_all_tag_count, allProductsList.size),
                                fontSize = 12.sp,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isAllSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        availableCategories.forEach { category ->
                            val isSelected = category in selectedCategories
                            val count = allProductsList.count { it.category.trim() == category }
                            Surface(
                                onClick = {
                                    selectedCategories = if (isSelected) {
                                        selectedCategories - category
                                    } else {
                                        selectedCategories + category
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFF4338CA)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.products_category_name_count, category, count),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Stock Health Status (2x2 Segmented Card Grid with 3D Animated Jewels)
                Text(
                    text = stringResource(R.string.products_stock_health_section),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // All SKUs
                        val isAllStock = stockFilter == ProductStockFilter.ALL
                        Surface(
                            onClick = { stockFilterName = ProductStockFilter.ALL.name },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAllStock) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isAllStock) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(Brush.radialGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5))))
                                )
                                Text(
                                    text = stringResource(R.string.products_all_skus_count, totalSKUCount),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isAllStock) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isAllStock) Color(0xFF312E81) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // In Stock with 3D Glowing Emerald
                        val isInStock = stockFilter == ProductStockFilter.AVAILABLE
                        Surface(
                            onClick = { stockFilterName = ProductStockFilter.AVAILABLE.name },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isInStock) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isInStock) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Realistic3DGlowingEmeraldSphere(size = 10.dp)
                                Text(
                                    text = stringResource(R.string.products_in_stock_count, inStockCount),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isInStock) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isInStock) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Low Stock with 3D Amber Hazard Diamond
                        val isLowStock = stockFilter == ProductStockFilter.LOW
                        Surface(
                            onClick = { stockFilterName = ProductStockFilter.LOW.name },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isLowStock) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isLowStock) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Realistic3DAmberHazardDiamond(size = 10.dp)
                                Text(
                                    text = stringResource(R.string.products_low_stock_count, lowStockCount),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isLowStock) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isLowStock) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Out of Stock with 3D Ruby Alert Orb
                        val isOutStock = stockFilter == ProductStockFilter.OUT
                        Surface(
                            onClick = { stockFilterName = ProductStockFilter.OUT.name },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isOutStock) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isOutStock) Color(0xFFEF4444) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Realistic3DRubyPulsingAlertOrb(size = 10.dp)
                                Text(
                                    text = stringResource(R.string.products_out_of_stock_count, outOfStockCount),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isOutStock) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isOutStock) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: Date & Expiry Tracking (Horizontal Scroll Row)
                Text(
                    text = stringResource(R.string.products_date_expiry_tracking_section),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        ProductDateFilter.ALL to stringResource(R.string.products_date_filter_all_dates, totalSKUCount),
                        ProductDateFilter.EXPIRING_SOON to stringResource(R.string.products_date_filter_expiring_soon, expiringSoonCount),
                        ProductDateFilter.EXPIRED to stringResource(R.string.products_date_filter_expired, expiredCount),
                        ProductDateFilter.TRACKED to stringResource(R.string.products_date_filter_tracked, dateTrackedCount),
                        ProductDateFilter.NO_DATE to stringResource(R.string.products_date_filter_untracked, noDateCount)
                    ).forEach { (df, label) ->
                        val isSelected = dateFilter == df
                        Surface(
                            onClick = { dateFilterName = df.name },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                when (df) {
                                    ProductDateFilter.EXPIRING_SOON -> Realistic3DAmberHazardDiamond(size = 8.dp)
                                    ProductDateFilter.EXPIRED -> Realistic3DRubyPulsingAlertOrb(size = 8.dp)
                                    else -> {}
                                }
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF312E81) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 4: Sort Order (Compact Segmented 2-Row Grid)
                Text(
                    text = stringResource(R.string.products_sort_by_section),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Row 1: Name A-Z, Name Z-A, Expiry Soonest
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ProductSortOption.NAME_ASC to stringResource(R.string.products_sort_chip_name_az),
                            ProductSortOption.NAME_DESC to stringResource(R.string.products_sort_chip_name_za),
                            ProductSortOption.EXPIRY_SOONEST to stringResource(R.string.products_sort_chip_expiry_soon)
                        ).forEach { (option, label) ->
                            val isSelected = sortOption == option
                            Surface(
                                onClick = { sortOptionName = option.name },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF312E81) else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Stock Low, Stock High, Price Low, Price High
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            ProductSortOption.STOCK_LOW to stringResource(R.string.products_sort_chip_stock_low),
                            ProductSortOption.STOCK_HIGH to stringResource(R.string.products_sort_chip_stock_high),
                            ProductSortOption.PRICE_LOW to stringResource(R.string.products_sort_chip_price_low),
                            ProductSortOption.PRICE_HIGH to stringResource(R.string.products_sort_chip_price_high)
                        ).forEach { (option, label) ->
                            val isSelected = sortOption == option
                            Surface(
                                onClick = { sortOptionName = option.name },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF6366F1) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 6.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF312E81) else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 5: Compact Selling Price Range Bar
                Text(
                    text = stringResource(R.string.products_selling_price_section),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Min Price
                    OutlinedTextField(
                        value = minimumPrice,
                        onValueChange = { minimumPrice = it.filter { char -> char.isDigit() || char == '.' } },
                        placeholder = { Text(stringResource(R.string.products_min_price_placeholder), fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.products_price_range_to),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Max Price
                    OutlinedTextField(
                        value = maximumPrice,
                        onValueChange = { maximumPrice = it.filter { char -> char.isDigit() || char == '.' } },
                        placeholder = { Text(stringResource(R.string.products_max_price_placeholder), fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 6: Sticky Actions (Reset & Apply)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedCategories = emptySet()
                            stockFilterName = ProductStockFilter.ALL.name
                            dateFilterName = ProductDateFilter.ALL.name
                            sortOptionName = ProductSortOption.NAME_ASC.name
                            minimumPrice = ""
                            maximumPrice = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.reset),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }

                    Button(
                        onClick = { showFilters = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.products_apply_filters_count, visibleProducts.size),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (productToDelete != null) {
        val deleteSuccessMessage = stringResource(R.string.products_deleted_snackbar, productToDelete!!.name)
        val deleteFailedMessage = stringResource(R.string.products_delete_failed)
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.products_delete_product_title)) },
            text = {
                Text(stringResource(R.string.products_delete_product_confirm, productToDelete!!.name))
            },
            confirmButton = {
                Button(
                    onClick = {
                        productsViewModel.deleteProduct(productToDelete!!) { result ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    result.fold(
                                        onSuccess = { deleteSuccessMessage },
                                        onFailure = { it.localizedMessage ?: deleteFailedMessage }
                                    ),
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.products_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text(stringResource(R.string.products_cancel_action))
                }
            }
        )
    }

    // Add Product Dialog
    if (showAddDialog) {
        AddEditProductDialog(
            initialBarcode = toEnglishDigits(scannedNewBarcodeForDialog ?: prefilledBarcode ?: ""),
            onDismiss = {
                showAddDialog = false
                scannedNewBarcodeForDialog = null
            },
            onSave = { name, barcode, cat, pPrice, sPrice, mrp, disc, qty, reorder, imageUri, mfgType, mfgDate, expDate, unit, wPrice, reason, note ->
                scope.launch {
                    val p = Product(
                        barcode = barcode,
                        name = name,
                        category = cat,
                        purchasePrice = pPrice,
                        sellingPrice = sPrice,
                        stockQty = qty,
                        reorderLevel = reorder,
                        imageUri = imageUri,
                        mfgOrPkgType = mfgType,
                        mfgOrPkgDate = mfgDate,
                        expiryDate = expDate,
                        unit = unit,
                        wholesalePrice = wPrice,
                        mrpPrice = mrp,
                        discountedPrice = disc
                    )
                    val success = productsViewModel.addProduct(p)
                    if (success) {
                        if (qty > 0.0) {
                            productsViewModel.recordInventoryAdjustment(
                                productBarcode = p.barcode,
                                productName = p.name,
                                previousStock = 0.0,
                                newStock = qty,
                                difference = qty,
                                reason = reason ?: "Restock",
                                note = note ?: "Initial stock recorded upon product creation"
                            )
                        }
                        showAddDialog = false
                        scannedNewBarcodeForDialog = null
                        Toast.makeText(
                            context,
                            context.getString(R.string.products_added_successfully, p.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.products_added_successfully, p.name),
                            duration = SnackbarDuration.Short
                        )
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.products_barcode_already_exists),
                            Toast.LENGTH_SHORT
                        ).show()
                        snackbarHostState.showSnackbar(context.getString(R.string.products_barcode_already_exists))
                    }
                }
            },
            onCategoryRenamed = { old, new -> productsViewModel.renameCategory(old, new) }
        )
    }

    // Edit Product Dialog
    if (selectedProductForEdit != null) {
        val prod = selectedProductForEdit!!
        AddEditProductDialog(
            isEdit = true,
            initialBarcode = toEnglishDigits(scannedNewBarcodeForDialog ?: prod.barcode),
            initialName = prod.name,
            initialCategory = prod.category,
            initialPurchasePrice = prod.purchasePrice,
            initialSellingPrice = prod.sellingPrice,
            initialMrpPrice = if (prod.mrpPrice > 0.0) prod.mrpPrice else prod.sellingPrice,
            initialDiscountedPrice = if (prod.discountedPrice > 0.0) prod.discountedPrice else prod.sellingPrice,
            initialWholesalePrice = prod.wholesalePrice,
            initialStock = prod.stockQty,
            initialReorder = prod.reorderLevel,
            initialImageUri = prod.imageUri,
            initialMfgOrPkgType = prod.mfgOrPkgType ?: "Mfg",
            initialMfgOrPkgDate = prod.mfgOrPkgDate ?: "",
            initialExpiryDate = prod.expiryDate ?: "",
            initialUnit = prod.unit,
            onDismiss = {
                selectedProductForEdit = null
                scannedNewBarcodeForDialog = null
            },
            onDelete = {
                productToDelete = prod
                selectedProductForEdit = null
                scannedNewBarcodeForDialog = null
            },
            onSave = { name, barcode, cat, pPrice, sPrice, mrp, disc, qty, reorder, imageUri, mfgType, mfgDate, expDate, unit, wPrice, reason, note ->
                val prevStock = prod.stockQty
                val diff = qty - prevStock
                val p = Product(
                    barcode = barcode,
                    name = name,
                    category = cat,
                    purchasePrice = pPrice,
                    sellingPrice = sPrice,
                    stockQty = qty,
                    reorderLevel = reorder,
                    imageUri = imageUri,
                    mfgOrPkgType = mfgType,
                    mfgOrPkgDate = mfgDate,
                    expiryDate = expDate,
                    unit = unit,
                    wholesalePrice = wPrice,
                    mrpPrice = mrp,
                    discountedPrice = disc
                )
                scope.launch {
                    val adjustment = if (kotlin.math.abs(diff) > 0.0001) {
                        InventoryAdjustment(
                            productBarcode = p.barcode,
                            productName = name,
                            previousStock = prevStock,
                            newStock = qty,
                            difference = diff,
                            reason = reason ?: if (diff > 0) "Restock" else "Discrepancy",
                            note = note
                        )
                    } else null
                    productsViewModel.updateProduct(prod.barcode, p, adjustment)
                        .onSuccess {
                            selectedProductForEdit = null
                            scannedNewBarcodeForDialog = null
                            Toast.makeText(context, context.getString(R.string.products_updated_successfully, p.name), Toast.LENGTH_SHORT).show()
                            snackbarHostState.showSnackbar(context.getString(R.string.products_updated_successfully, p.name), duration = SnackbarDuration.Short)
                        }
                        .onFailure { error ->
                            val message = error.message ?: context.getString(R.string.products_unable_to_update)
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                        }
                }
            },
            onCategoryRenamed = { old, new -> productsViewModel.renameCategory(old, new) }
        )
    }

    if (showInventoryAlertsModal) {
        InventoryAlertsCenterBottomSheet(
            alertsData = allAlerts,
            onEditProduct = { prod ->
                selectedProductForEdit = prod
            },
            onQuickAddStock = { prod, addedQty ->
                val prevStock = prod.stockQty
                val newStock = prevStock + addedQty
                productsViewModel.updateProductWithAdjustment(
                    updatedProduct = prod.copy(stockQty = newStock),
                    previousStock = prevStock,
                    newStock = newStock,
                    difference = addedQty.toDouble(),
                    reason = "Restock",
                    note = "Quick restock from Inventory Alerts"
                )
                Toast.makeText(context, context.getString(R.string.products_added_stock_toast, addedQty, prod.name), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showInventoryAlertsModal = false }
        )
    }

    stockCheckProduct?.let { product ->
        HardwareStockCheckDialog(
            product = product,
            onDismiss = { stockCheckProduct = null },
            onEdit = {
                selectedProductForEdit = product
                stockCheckProduct = null
            },
            onSave = { newQuantity, reason, note ->
                val prevStock = product.stockQty
                val newStock = newQuantity.coerceAtLeast(0.0)
                val diff = newStock - prevStock
                val updated = product.copy(stockQty = newStock)
                if (kotlin.math.abs(diff) > 0.0001) {
                    productsViewModel.updateProductWithAdjustment(
                        updatedProduct = updated,
                        previousStock = prevStock,
                        newStock = newStock,
                        difference = diff,
                        reason = reason,
                        note = note
                    )
                } else {
                    productsViewModel.updateProduct(updated)
                }
                stockCheckProduct = null
                val sStr = if (updated.stockQty % 1.0 == 0.0) updated.stockQty.toLong().toString() else String.format(Locale.US, "%.2f", updated.stockQty).trimEnd('0').trimEnd('.')
                Toast.makeText(
                    context,
                    context.getString(R.string.products_stock_saved_toast, product.name, sStr, product.unit),
                    Toast.LENGTH_SHORT
                ).show()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.products_stock_saved_snackbar, product.name, sStr, product.unit),
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    stockNotFoundBarcode?.let { barcode ->
        val englishBarcode = toEnglishDigits(barcode)
        StockCheckNotFoundDialog(
            barcode = englishBarcode,
            onDismiss = { stockNotFoundBarcode = null },
            onAddNow = {
                scannedNewBarcodeForDialog = englishBarcode
                showAddDialog = true
                stockNotFoundBarcode = null
            }
        )
    }

    // QR Dialog
    if (productForCodeDialog != null) {
        val prod = productForCodeDialog!!
        ProductCodeImageDialog(
            product = prod,
            onDismiss = { productForCodeDialog = null }
        )
    }

    // Batch Barcode Print Dialog
    if (showBatchPrintDialog) {
        BatchProductCodePrintDialog(
            products = allProductsList,
            onDismiss = { showBatchPrintDialog = false }
        )
    }

    // Enlarged Image Dialog
    if (productForEnlargedImage != null) {
        val (uri, title) = productForEnlargedImage!!
        EnlargedImageDialog(
            imageUri = uri,
            title = title,
            onDismiss = { productForEnlargedImage = null }
        )
    }

    if (showStockAuditHistoryModal) {
        StockAuditHistorySheet(
            adjustments = allAdjustments,
            allProducts = productsList,
            onAdjustProductStock = { prod ->
                stockCheckProduct = prod
                showStockAuditHistoryModal = false
            },
            onDismiss = { showStockAuditHistoryModal = false }
        )
    }

    // Bulk Operation Dialogs
    if (showBulkPriceDialog) {
        BulkPriceAdjustmentDialog(
            selectedCount = selectedBarcodes.size,
            onDismiss = { showBulkPriceDialog = false },
            onApply = { percent, fixed ->
                scope.launch {
                    try {
                        val count = selectedBarcodes.size
                        productsViewModel.bulkUpdateProductPrices(
                            barcodes = selectedBarcodes.toList(),
                            percentDelta = percent,
                            fixedDelta = fixed
                        )
                        Toast.makeText(context, "Prices updated for $count products", Toast.LENGTH_SHORT).show()
                        selectedBarcodes.clear()
                        batchSelectMode = false
                        showBulkPriceDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Price update failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showBulkCategoryDialog) {
        BulkCategoryReassignmentDialog(
            selectedCount = selectedBarcodes.size,
            existingCategories = availableCategories,
            onDismiss = { showBulkCategoryDialog = false },
            onApply = { newCat ->
                scope.launch {
                    try {
                        val count = selectedBarcodes.size
                        productsViewModel.bulkUpdateProductCategory(
                            barcodes = selectedBarcodes.toList(),
                            newCategory = newCat
                        )
                        Toast.makeText(context, "Category updated for $count products", Toast.LENGTH_SHORT).show()
                        selectedBarcodes.clear()
                        batchSelectMode = false
                        showBulkCategoryDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Category update failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showBulkDeleteDialog) {
        BulkDeleteConfirmationDialog(
            selectedCount = selectedBarcodes.size,
            onDismiss = { showBulkDeleteDialog = false },
            onConfirm = {
                scope.launch {
                    try {
                        val count = selectedBarcodes.size
                        productsViewModel.bulkDeleteProducts(selectedBarcodes.toList())
                        Toast.makeText(context, "Deleted $count products", Toast.LENGTH_SHORT).show()
                        selectedBarcodes.clear()
                        batchSelectMode = false
                        showBulkDeleteDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "Bulk delete failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkPriceAdjustmentDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onApply: (percentDelta: Double?, fixedDelta: Double?) -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0: Percentage, 1: Fixed Amount
    var valueText by remember { mutableStateOf("") }
    var isIncrease by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Bulk Price Adjustment", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Adjust prices for $selectedCount selected product(s).",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text("Percentage (%)") }
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text("Fixed (৳)") }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isIncrease,
                        onClick = { isIncrease = true },
                        label = { Text("Increase (+)") }
                    )
                    FilterChip(
                        selected = !isIncrease,
                        onClick = { isIncrease = false },
                        label = { Text("Decrease (-)") }
                    )
                }

                if (mode == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 20).forEach { p ->
                            SuggestionChip(
                                onClick = { valueText = p.toString() },
                                label = { Text("$p%") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (mode == 0) "Percentage (e.g. 10)" else "Amount in ৳ (e.g. 20)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawVal = valueText.toDoubleOrNull() ?: 0.0
                    val factor = if (isIncrease) 1.0 else -1.0
                    val finalVal = rawVal * factor
                    if (mode == 0) {
                        onApply(finalVal, null)
                    } else {
                        onApply(null, finalVal)
                    }
                },
                enabled = valueText.toDoubleOrNull() != null && (valueText.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Apply to $selectedCount items")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkCategoryReassignmentDialog(
    selectedCount: Int,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onApply: (newCategory: String) -> Unit
) {
    var selectedCat by remember { mutableStateOf(existingCategories.firstOrNull() ?: "") }
    var isCustomCat by remember { mutableStateOf(existingCategories.isEmpty()) }
    var customCatText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Reassign Category", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Reassign category for $selectedCount product(s):",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (existingCategories.isNotEmpty()) {
                    Text("Select existing category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(existingCategories) { cat ->
                            FilterChip(
                                selected = !isCustomCat && selectedCat == cat,
                                onClick = {
                                    isCustomCat = false
                                    selectedCat = cat
                                },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                FilterChip(
                    selected = isCustomCat,
                    onClick = { isCustomCat = true },
                    label = { Text("+ New Category") }
                )

                if (isCustomCat) {
                    OutlinedTextField(
                        value = customCatText,
                        onValueChange = { customCatText = it },
                        label = { Text("Enter Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            val targetCategory = if (isCustomCat) customCatText.trim() else selectedCat.trim()
            Button(
                onClick = { onApply(targetCategory) },
                enabled = targetCategory.isNotBlank()
            ) {
                Text("Reassign ($selectedCount)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BulkDeleteConfirmationDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete $selectedCount Products?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(
                "Are you sure you want to permanently delete $selectedCount selected products? This action cannot be undone.",
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete $selectedCount Products", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}




@Composable
fun StockCheckNotFoundDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onAddNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.products_not_found_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = barcode,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.products_not_found_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAddNow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.products_add_product_now), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.products_keep_scanning))
            }
        }
    )
}

@Composable
private fun HardwareStockCheckDialog(
    product: Product,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSave: (newQuantity: Double, reason: String, note: String?) -> Unit
) {
    val initialQty = remember(product.barcode) { product.stockQty }
    var quantityText by remember(product.barcode) {
        mutableStateOf(
            if (product.stockQty % 1.0 == 0.0) product.stockQty.toLong().toString()
            else String.format(Locale.US, "%.2f", product.stockQty).trimEnd('0').trimEnd('.')
        )
    }
    val quantity = quantityText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    var selectedReason by remember { mutableStateOf("Restock") }
    var adjustmentNote by remember { mutableStateOf("") }
    val reasonsList = listOf("Restock", "Damage", "Expired", "Discrepancy", "Sample", "Internal Use")

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .padding(vertical = 16.dp),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                                stringResource(R.string.products_inventory_level_label),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6B21A8)
                            )
                            Text(
                                stringResource(R.string.products_quantity_units, quantity.toString()),
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
                val mfgTypeLabel = if (product.mfgOrPkgType.equals("pkg", ignoreCase = true)) stringResource(R.string.products_pkg_abbr) else stringResource(R.string.products_mfg_abbr)

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
                                stringResource(R.string.products_date_tracking_label),
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
                                            stringResource(R.string.products_mfg_type_date, mfgTypeLabel, mfgDate ?: ""),
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
                                            stringResource(R.string.products_exp_date, expDate ?: ""),
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
                                stringResource(R.string.products_no_date_set),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Adjust Quantity Stepper Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.products_adjust_quantity_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )

                        StockDeltaPill(initialQty = initialQty, currentQty = quantity)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = {
                                val n = (quantity - 1.0).coerceAtLeast(0.0)
                                quantityText = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.products_decrease_stock_cd))
                        }

                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) {
                                    quantityText = filtered.take(7)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                        )

                        FilledIconButton(
                            onClick = {
                                val n = quantity + 1.0
                                quantityText = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFF059669)
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.products_increase_stock_cd), tint = Color.White)
                        }
                    }

                    // Quick increment chips (+1, +5, +10, Zero, Reset)
                    QuickStepperChipsRow(
                        onIncrement = { delta ->
                            val n = quantity + delta
                            quantityText = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
                        },
                        onZero = { quantityText = "0" },
                        onReset = {
                            quantityText = if (initialQty % 1.0 == 0.0) initialQty.toLong().toString() else String.format(Locale.US, "%.2f", initialQty).trimEnd('0').trimEnd('.')
                        }
                    )

                    if (quantity != initialQty) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.products_reason_for_adjustment_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            reasonsList.forEach { reason ->
                                FilterChip(
                                    selected = selectedReason == reason,
                                    onClick = { selectedReason = reason },
                                    label = { Text(localizedAdjustmentReasonLabel(reason), fontSize = 11.5.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = adjustmentNote,
                            onValueChange = { adjustmentNote = it },
                            label = { Text(stringResource(R.string.products_audit_note_label)) },
                            placeholder = { Text(stringResource(R.string.products_audit_note_placeholder)) },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
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
                        stringResource(R.string.products_cancel_action),
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
                        stringResource(R.string.products_edit_action),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                // 3. Save Button with 3D Save Icon
                Button(
                    onClick = { onSave(quantity, selectedReason, adjustmentNote.trim().takeIf { it.isNotBlank() }) },
                    enabled = quantityText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Realistic3DSaveIcon(size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.save),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    )
}

fun copyUriToInternalStorage(context: android.content.Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (originalBitmap == null) return null

        val maxDim = 600
        val width = originalBitmap.width
        val height = originalBitmap.height
        val scaledBitmap = if (width > maxDim || height > maxDim) {
            val ratio = width.toFloat() / height.toFloat()
            val targetW = if (width >= height) maxDim else (maxDim * ratio).toInt().coerceAtLeast(1)
            val targetH = if (height > width) maxDim else (maxDim / ratio).toInt().coerceAtLeast(1)
            android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
        } else {
            originalBitmap
        }

        val fileName = "product_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        file.outputStream().use { outputStream ->
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        }
        if (scaledBitmap != originalBitmap) {
            scaledBitmap.recycle()
        }
        originalBitmap.recycle()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

internal object ProductImageMemoryCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    fun get(key: String): android.graphics.Bitmap? = cache.get(key)
    fun put(key: String, bitmap: android.graphics.Bitmap) {
        cache.put(key, bitmap)
    }
}

private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

private fun decodeSampledBitmapFromUri(context: android.content.Context, uriString: String, reqWidth: Int = 256, reqHeight: Int = 256): android.graphics.Bitmap? {
    try {
        val file = File(uriString)
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        if (file.exists()) {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            return android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        } else {
            val uri = Uri.parse(uriString)
            val cr = context.contentResolver
            val is1 = cr.openInputStream(uri)
            if (is1 != null) {
                is1.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, options)
                }
            }
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            val is2 = cr.openInputStream(uri)
            return if (is2 != null) {
                is2.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, options)
                }
            } else null
        }
    } catch (_: Exception) {
        // Corrupted or missing file
    }
    return null
}

@Composable
fun rememberUriImagePainter(uriString: String?): ImageBitmap? {
    if (uriString.isNullOrBlank()) return null
    val context = LocalContext.current.applicationContext
    val cached = ProductImageMemoryCache.get(uriString)
    if (cached != null) return remember(cached) { cached.asImageBitmap() }

    val state = produceState<ImageBitmap?>(initialValue = null, uriString) {
        val inCache = ProductImageMemoryCache.get(uriString)
        if (inCache != null) {
            value = inCache.asImageBitmap()
            return@produceState
        }
        val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            decodeSampledBitmapFromUri(context, uriString)
        }
        if (decoded != null) {
            ProductImageMemoryCache.put(uriString, decoded)
            value = decoded.asImageBitmap()
        } else {
            value = null
        }
    }
    return state.value
}

@Composable
fun UriImage(
    uriString: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val imageBitmap = rememberUriImagePainter(uriString)
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ProductStockFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    filterType: ProductStockFilter = ProductStockFilter.ALL
) {
    val chipColors = when (filterType) {
        ProductStockFilter.ALL -> FilterChipColorsTuple(
            selectedBg = Color(0xFFE0E7FF),
            selectedFg = Color(0xFF3730A3),
            selectedBorder = Color(0xFFC7D2FE),
            unselectedFg = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedBorder = MaterialTheme.colorScheme.outlineVariant,
            emoji = "🏷️"
        )
        ProductStockFilter.LOW -> FilterChipColorsTuple(
            selectedBg = Color(0xFFFEF3C7),
            selectedFg = Color(0xFF92400E),
            selectedBorder = Color(0xFFFDE68A),
            unselectedFg = Color(0xFFD97706),
            unselectedBorder = Color(0xFFFDE68A),
            emoji = "⚠️"
        )
        ProductStockFilter.OUT -> FilterChipColorsTuple(
            selectedBg = Color(0xFFFEE2E2),
            selectedFg = Color(0xFF991B1B),
            selectedBorder = Color(0xFFFCA5A5),
            unselectedFg = Color(0xFFDC2626),
            unselectedBorder = Color(0xFFFCA5A5),
            emoji = "❌"
        )
        ProductStockFilter.AVAILABLE -> FilterChipColorsTuple(
            selectedBg = Color(0xFFD1FAE5),
            selectedFg = Color(0xFF065F46),
            selectedBorder = Color(0xFFA7F3D0),
            unselectedFg = Color(0xFF059669),
            unselectedBorder = Color(0xFFA7F3D0),
            emoji = "✅"
        )
    }

    val currentBg = if (selected) chipColors.selectedBg else MaterialTheme.colorScheme.surface
    val currentFg = if (selected) chipColors.selectedFg else chipColors.unselectedFg
    val currentBorderColor = if (selected) chipColors.selectedBorder else chipColors.unselectedBorder

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = currentBg,
        contentColor = currentFg,
        border = androidx.compose.foundation.BorderStroke(1.dp, currentBorderColor),
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (selected) "✓" else chipColors.emoji,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = currentFg
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = currentFg
            )
        }
    }
}

private data class FilterChipColorsTuple(
    val selectedBg: Color,
    val selectedFg: Color,
    val selectedBorder: Color,
    val unselectedFg: Color,
    val unselectedBorder: Color,
    val emoji: String
)

@Composable
private fun ProductViewButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(9.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
private fun ProductAddButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val rotationAnim = remember { Animatable(0f) }

    Surface(
        onClick = {
            if (enabled) {
                scope.launch {
                    launch {
                        scaleAnim.animateTo(1.32f, tween(90, easing = FastOutSlowInEasing))
                        scaleAnim.animateTo(0.85f, tween(80))
                        scaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                    launch {
                        rotationAnim.animateTo(-15f, tween(70))
                        rotationAnim.animateTo(15f, tween(70))
                        rotationAnim.animateTo(0f, tween(70))
                    }
                }
                onClick()
            }
        },
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🛒",
                fontSize = 13.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    rotationZ = rotationAnim.value
                }
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.add),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ProductStockStatus(product: Product, modifier: Modifier = Modifier) {
    val isOut = product.stockQty <= 0
    val isLow = !isOut && product.stockQty <= product.reorderLevel
    val (label, emoji) = when {
        isOut -> stringResource(R.string.out_of_stock) to "❌"
        isLow -> stringResource(R.string.low_stock) to "⚠️"
        else -> stringResource(R.string.in_stock) to "✅"
    }
    val containerColor = when {
        isOut -> Color(0xFFFEE2E2)
        isLow -> Color(0xFFFEF3C7)
        else -> Color(0xFFD1FAE5)
    }
    val contentColor = when {
        isOut -> Color(0xFF991B1B)
        isLow -> Color(0xFF92400E)
        else -> Color(0xFF065F46)
    }

    val scope = rememberCoroutineScope()
    val popScale = remember { Animatable(1f) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable {
                scope.launch {
                    popScale.animateTo(1.3f, tween(100, easing = FastOutSlowInEasing))
                    popScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            },
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 11.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = popScale.value
                    scaleY = popScale.value
                }
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProductOverflowMenu(
    productName: String,
    onEdit: () -> Unit,
    onAdjustStock: () -> Unit = {},
    onGenerateCode: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val spinAnim = remember { Animatable(0f) }

    Box(modifier = modifier) {
        Surface(
            onClick = {
                scope.launch {
                    spinAnim.animateTo(180f, tween(220, easing = FastOutSlowInEasing))
                    spinAnim.snapTo(0f)
                }
                expanded = true
            },
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.products_options_cd),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = spinAnim.value },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.products_adjust_stock_audit)) },
                leadingIcon = { Text("📦", fontSize = 16.sp) },
                onClick = {
                    expanded = false
                    onAdjustStock()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_product)) },
                leadingIcon = { Text("✏️", fontSize = 16.sp) },
                onClick = {
                    expanded = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.generate_code_image)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    expanded = false
                    onGenerateCode()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Text("🗑️", fontSize = 16.sp) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun ProductStockPillChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductItemRow(
    product: Product,
    onRowClick: () -> Unit = {},
    onRowLongClick: () -> Unit = {},
    onAdjustStock: () -> Unit = {},
    onEdit: () -> Unit,
    onGenerateCode: () -> Unit,
    onImageClick: () -> Unit,
    onDelete: () -> Unit,
    onAddToCart: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    isTablet: Boolean = false
) {
    val productImage = rememberUriImagePainter(product.imageUri)
    val isOut = product.stockQty <= 0.0
    val isLow = product.stockQty > 0.0 && product.stockQty <= product.reorderLevel
    val sStr = if (product.stockQty % 1.0 == 0.0) product.stockQty.toLong().toString() else String.format(Locale.US, "%.1f", product.stockQty)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        if (isTablet) {
            // Streamlined Single-Row Layout for Tablet Mode (Exact match to Preview & Grid parity)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 14.dp,
                        vertical = 9.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (productImage != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { onImageClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = productImage,
                            contentDescription = product.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // Left Section: Title + Sub-meta (Barcode Badge & Category)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None
                            )
                        )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = onGenerateCode,
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Realistic3DHologramLaserCodeIcon(size = 11.dp)
                                Text(
                                    text = "#${product.barcode}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (product.category.isNotBlank()) {
                            Text(
                                text = product.category,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Center Column: 3D Stock Status Pill
                Surface(
                    onClick = onAdjustStock,
                    shape = RoundedCornerShape(999.dp),
                    color = when {
                        isOut -> Color(0xFFFEE2E2)
                        isLow -> Color(0xFFFEF3C7)
                        else -> Color(0xFFDCFCE7)
                    },
                    border = BorderStroke(1.dp, when {
                        isOut -> Color(0xFFFECACA)
                        isLow -> Color(0xFFFDE68A)
                        else -> Color(0xFFA7F3D0)
                    }),
                    modifier = Modifier.widthIn(min = 90.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        when {
                            isOut -> Realistic3DRubyPulsingAlertOrb(size = 7.dp)
                            isLow -> Realistic3DAmberHazardDiamond(size = 7.dp)
                            else -> Realistic3DGlowingEmeraldSphere(size = 7.dp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when {
                                isOut -> stringResource(R.string.products_stock_pill_out)
                                isLow -> stringResource(R.string.products_stock_pill_low, sStr)
                                else -> stringResource(R.string.products_stock_pill_qty_unit, sStr, product.unit)
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isOut -> Color(0xFFB91C1C)
                                isLow -> Color(0xFFB45309)
                                else -> Color(0xFF047857)
                            }
                        )
                    }
                }

                // Center-Right: Price & Unit
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.widthIn(min = 100.dp)
                ) {
                    Text(
                        text = formatTaka(product.sellingPrice),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF047857)
                    )
                    Text(
                        text = "/${product.unit}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }

                // Right: Action Dock ([🛒 Cart], [📦 ± Stock], [🏷️ Code], [⋮])
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAddToCart,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Realistic3DSupermarketCartIcon(size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.cart), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Surface(
                        onClick = onAdjustStock,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Realistic3DKraftStockBoxIcon(size = 14.dp)
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.products_adjust_stock_pm), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                        }
                    }

                    Surface(
                        onClick = onGenerateCode,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Realistic3DHologramLaserCodeIcon(size = 15.dp)
                        }
                    }

                    ProductOverflowMenu(
                        productName = product.name,
                        onEdit = onEdit,
                        onAdjustStock = onAdjustStock,
                        onGenerateCode = onGenerateCode,
                        onDelete = onDelete,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // Row 1: Left (Thumbnail + Bangla Title + SKU Capsule) | Right (3D Stock Pill)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (productImage != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { onImageClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = productImage,
                                contentDescription = product.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = TextStyle(
                                lineHeight = 20.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            Surface(
                                onClick = onGenerateCode,
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Realistic3DHologramLaserCodeIcon(size = 11.dp)
                                    Text(
                                        text = "#${product.barcode}",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (product.category.isNotBlank()) {
                                Text(
                                    text = product.category,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 3D Stock Status Pill
                Surface(
                    onClick = onAdjustStock,
                    shape = RoundedCornerShape(999.dp),
                    color = when {
                        isOut -> Color(0xFFFEE2E2)
                        isLow -> Color(0xFFFEF3C7)
                        else -> Color(0xFFDCFCE7)
                    },
                    border = BorderStroke(1.dp, when {
                        isOut -> Color(0xFFFECACA)
                        isLow -> Color(0xFFFDE68A)
                        else -> Color(0xFFA7F3D0)
                    }),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.5.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        when {
                            isOut -> Realistic3DRubyPulsingAlertOrb(size = 7.dp)
                            isLow -> Realistic3DAmberHazardDiamond(size = 7.dp)
                            else -> Realistic3DGlowingEmeraldSphere(size = 7.dp)
                        }
                        Text(
                            text = when {
                                isOut -> stringResource(R.string.products_stock_pill_out)
                                isLow -> stringResource(R.string.products_stock_pill_low, sStr)
                                else -> stringResource(R.string.products_stock_pill_qty_unit, sStr, product.unit)
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isOut -> Color(0xFFB91C1C)
                                isLow -> Color(0xFFB45309)
                                else -> Color(0xFF047857)
                            }
                        )
                    }
                }
            }

            // Row 2: Left (Price/Unit) | Right (Action Dock: [🛒 Cart], [📦 ± Stock], [🏷️ Code], [⋮])
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = formatTaka(product.sellingPrice),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF047857)
                    )
                    Text(
                        text = "/${product.unit}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 1.5.dp)
                    )
                }

                // Micro-Action Dock
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 3D Cart Button
                    Button(
                        onClick = onAddToCart,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Realistic3DSupermarketCartIcon(size = 15.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.cart), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. 3D ± Stock Button
                    Surface(
                        onClick = onAdjustStock,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Realistic3DKraftStockBoxIcon(size = 14.dp)
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.products_adjust_stock_pm), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                        }
                    }

                    // 3. 3D Holographic Code Button
                    Surface(
                        onClick = onGenerateCode,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Realistic3DHologramLaserCodeIcon(size = 15.dp)
                        }
                    }

                    // 4. Menu Button
                    ProductOverflowMenu(
                        productName = product.name,
                        onEdit = onEdit,
                        onAdjustStock = onAdjustStock,
                        onGenerateCode = onGenerateCode,
                        onDelete = onDelete,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
}

fun getCategoryEmoji(category: String): String {
    val lower = category.lowercase(Locale.ROOT)
    return when {
        lower.contains("সস") || lower.contains("sauce") || lower.contains("ketchup") -> "🥫"
        lower.contains("সোপ") || lower.contains("soap") || lower.contains("সাবান") -> "🧼"
        lower.contains("freshener") || lower.contains("সুগন্ধি") -> "🌸"
        lower.contains("wash") || lower.contains("লোশন") || lower.contains("lotion") || lower.contains("shampoo") || lower.contains("শ্যাম্পু") -> "🧴"
        lower.contains("bakery") || lower.contains("bread") || lower.contains("রুটি") || lower.contains("toast") || lower.contains("বিস্কুট") || lower.contains("biscuit") || lower.contains("কেক") || lower.contains("cake") -> "🍞"
        lower.contains("oil") || lower.contains("তেল") -> "🌻"
        lower.contains("rice") || lower.contains("চাল") || lower.contains("ডাল") || lower.contains("lentil") || lower.contains("grain") -> "🍚"
        lower.contains("tea") || lower.contains("coffee") || lower.contains("চা") || lower.contains("কফি") -> "☕"
        lower.contains("milk") || lower.contains("dairy") || lower.contains("দুধ") -> "🥛"
        lower.contains("spice") || lower.contains("মসলা") || lower.contains("মরিচ") || lower.contains("হলুদ") -> "🧂"
        lower.contains("snack") || lower.contains("মুড়ি") || lower.contains("মুড়ি") || lower.contains("chanachur") || lower.contains("চানাচুর") || lower.contains("chips") || lower.contains("চিপস") -> "🍿"
        lower.contains("drink") || lower.contains("juice") || lower.contains("শরবত") || lower.contains("beverage") -> "🧃"
        lower.contains("sweet") || lower.contains("chocolate") || lower.contains("চকলেট") || lower.contains("মিষ্টি") -> "🍫"
        lower.contains("care") || lower.contains("সৌন্দর্য") -> "✨"
        else -> "🏷️"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductGridCard(
    product: Product,
    onCardClick: () -> Unit = {},
    onCardLongClick: () -> Unit = {},
    onAdjustStock: () -> Unit = {},
    onEdit: () -> Unit,
    onGenerateCode: () -> Unit,
    onImageClick: () -> Unit,
    onDelete: () -> Unit,
    onAddToCart: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val productImage = rememberUriImagePainter(product.imageUri)
    val isOut = product.stockQty <= 0.0
    val isLow = product.stockQty > 0.0 && product.stockQty <= product.reorderLevel
    val sStr = if (product.stockQty % 1.0 == 0.0) product.stockQty.toLong().toString() else String.format(Locale.US, "%.1f", product.stockQty)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onCardClick() },
                onLongClick = onCardLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.2.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.55f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Selection Mode Checkbox Row if active
            if (isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Optional Thumbnail (If product has an image)
            if (productImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = productImage,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ==========================================
            // 1. TOP ROW: Product Title + Stock Badge Pill
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Product Title (Left, Bold, 2 lines max with Bangla support)
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                )

                // Stock Badge Pill (Right, clickable for stock adjustment)
                Surface(
                    onClick = onAdjustStock,
                    shape = RoundedCornerShape(999.dp),
                    color = when {
                        isOut -> Color(0xFFFEE2E2)
                        isLow -> Color(0xFFFEF3C7)
                        else -> Color(0xFFDCFCE7)
                    },
                    border = BorderStroke(1.dp, when {
                        isOut -> Color(0xFFFECACA)
                        isLow -> Color(0xFFFDE68A)
                        else -> Color(0xFFA7F3D0)
                    })
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        when {
                            isOut -> Realistic3DRubyPulsingAlertOrb(size = 7.dp)
                            isLow -> Realistic3DAmberHazardDiamond(size = 7.dp)
                            else -> Realistic3DGlowingEmeraldSphere(size = 7.dp)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when {
                                isOut -> stringResource(R.string.products_stock_pill_out)
                                isLow -> stringResource(R.string.products_stock_pill_low, sStr)
                                else -> stringResource(R.string.products_stock_pill_qty_unit, sStr, product.unit)
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isOut -> Color(0xFFB91C1C)
                                isLow -> Color(0xFFB45309)
                                else -> Color(0xFF047857)
                            },
                            maxLines = 1
                        )
                    }
                }
            }

            // ==========================================
            // 2. META ROW: [#barcode] + [Category text]
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Barcode Capsule [#846656005286]
                Surface(
                    onClick = onGenerateCode,
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Realistic3DHologramLaserCodeIcon(size = 11.dp)
                        Text(
                            text = "#${product.barcode}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Category Text (in bold indigo)
                if (product.category.isNotBlank()) {
                    Text(
                        text = product.category,
                        color = Color(0xFF4F46E5),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.18f else 0.25f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(vertical = 1.dp)
            )

            // ==========================================
            // 3. BOTTOM ROW: Price on Left + Action Cluster on Right
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Price Display (৳550 /Pcs)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = formatTaka(product.sellingPrice),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF047857)
                    )
                    Text(
                        text = "/${product.unit}",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }

                // Actions Cluster: [🛒 Cart] + [📦 ± Stock] + [🏷️ Code] + [⋮ Menu]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 3D Cart Button
                    Button(
                        onClick = onAddToCart,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Realistic3DSupermarketCartIcon(size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.cart), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. 3D ± Stock Button
                    Surface(
                        onClick = onAdjustStock,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Realistic3DKraftStockBoxIcon(size = 14.dp)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.products_adjust_stock_pm),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }

                    // 3. 3D Barcode Label Button
                    Surface(
                        onClick = onGenerateCode,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Realistic3DHologramLaserCodeIcon(size = 15.dp)
                        }
                    }

                    // 4. Overflow Menu [⋮]
                    ProductOverflowMenu(
                        productName = product.name,
                        onEdit = onEdit,
                        onAdjustStock = onAdjustStock,
                        onGenerateCode = onGenerateCode,
                        onDelete = onDelete,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun Animated3DPackageIcon(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_box")
    val rotY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotY
                translationY = offsetY
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        Text("📦", fontSize = fontSize)
    }
}

@Composable
fun Animated3DAddProductIcon(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_add_box")
    val rotY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    val badgeSize = (fontSize.value * 0.52f).dp
    val iconSize = (fontSize.value * 0.38f).dp
    val badgeOffsetX = (fontSize.value * 0.12f).dp
    val badgeOffsetY = (fontSize.value * 0.04f).dp

    Box(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp)
            .graphicsLayer {
                rotationY = rotY
                translationY = offsetY
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📦",
            fontSize = fontSize,
            modifier = Modifier.padding(top = 2.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = badgeOffsetX, y = badgeOffsetY)
                .size(badgeSize)
                .background(Color(0xFF16A34A), CircleShape)
                .border(1.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun Animated3DGalleryIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_gallery")
    val rotY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotY"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotY
            scaleX = scale
            scaleY = scale
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🖼️", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DCameraIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_camera")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotZ
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📸", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DFactoryIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_factory")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetY
            rotationZ = rotZ
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🏭", fontSize = 14.sp)
    }
}

@Composable
fun Animated3DCameraScannerToggleIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_camera_scanner_toggle")
    val flipAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flipAngle"
    )

    val showCamera = (flipAngle % 360f) < 180f

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = flipAngle
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        if (showCamera) {
            Text("📸", fontSize = 18.sp)
        } else {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "Scan Barcode",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun Animated3DScannerIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_scanner")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationY = rotY
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = "Scan Barcode",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun Animated3DCalendarIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_calendar")
    val rotY by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotY
            scaleX = scale
            scaleY = scale
            cameraDistance = 12 * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📅", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DIsometricBoxIcon(modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_box")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    val rotY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            translationY = floatOffset
            rotationY = rotY
            cameraDistance = 14f * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📦", fontSize = fontSize)
    }
}

@Composable
fun Animated3DMetallicTagIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_tag")
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationZ = rotZ
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🏷️", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DFolderIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_folder")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📂", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DStudioCameraIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_camera")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cameraScale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📸", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DInventoryGaugeIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_gauge")
    val scaleY by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.scaleY = scaleY
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
        },
        contentAlignment = Alignment.Center
    ) {
        Text("📊", fontSize = 16.sp)
    }
}

@Composable
fun Animated3DCoinIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_coin")
    val rotY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotY"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coinScale"
    )

    Box(
        modifier = modifier.graphicsLayer {
            rotationY = rotY
            scaleX = scale
            scaleY = scale
            cameraDistance = 12f * density
        },
        contentAlignment = Alignment.Center
    ) {
        Text("🪙", fontSize = 18.sp)
    }
}

@Composable
fun Animated3DSaveIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "3d_save")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotZ by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotZ"
    )

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotZ
        },
        contentAlignment = Alignment.Center
    ) {
        Text("✅", fontSize = 18.sp)
    }
}



@Composable
fun DateInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }

    var initYear = calendar.get(java.util.Calendar.YEAR)
    var initMonth = calendar.get(java.util.Calendar.MONTH)
    var initDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    if (value.length >= 8 && value.contains("-")) {
        val parts = value.split("-")
        if (parts.size == 3) {
            val d = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val y = parts[2].toIntOrNull()
            if (d != null && m != null && y != null) {
                initDay = d
                initMonth = (m - 1).coerceIn(0, 11)
                initYear = if (y < 100) 2000 + y else y
            }
        }
    }

    val datePickerDialog = remember(context, value) {
        android.app.DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                val yy = (year % 100).toString().padStart(2, '0')
                val mm = (monthOfYear + 1).toString().padStart(2, '0')
                val dd = dayOfMonth.toString().padStart(2, '0')
                onValueChange("$dd-$mm-$yy")
            },
            initYear,
            initMonth,
            initDay
        )
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isDigit() || it == '-' }.take(8)
                        onValueChange(filtered)
                    },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text("DD-MM-YY", color = Color.Gray, fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Animated3DCalendarIcon()
                }
            }
        }
    }
}

@Composable
private fun ProductDialogSectionLabel(icon: String, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$icon  ${label.uppercase()}",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFFE2E8F0)
        )
    }
}

private fun Modifier.dashedProductImageBorder(active: Boolean): Modifier = drawBehind {
    drawRoundRect(
        color = if (active) Color(0xFF6366F1) else Color(0xFFCBD5E1),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    isEdit: Boolean = false,
    initialBarcode: String = "",
    initialName: String = "",
    initialCategory: String = "",
    initialPurchasePrice: Double = 0.0,
    initialSellingPrice: Double = 0.0,
    initialMrpPrice: Double = 0.0,
    initialDiscountedPrice: Double = 0.0,
    initialWholesalePrice: Double = 0.0,
    initialStock: Double = 0.0,
    initialReorder: Int = 5,
    initialImageUri: String? = null,
    initialMfgOrPkgType: String = "Mfg",
    initialMfgOrPkgDate: String = "",
    initialExpiryDate: String = "",
    initialUnit: String = "Pcs",
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        barcode: String,
        category: String,
        purchasePrice: Double,
        sellingPrice: Double,
        mrpPrice: Double,
        discountedPrice: Double,
        stock: Double,
        reorderLevel: Int,
        imageUri: String?,
        mfgOrPkgType: String,
        mfgOrPkgDate: String,
        expiryDate: String,
        unit: String,
        wholesalePrice: Double,
        adjustmentReason: String?,
        adjustmentNote: String?
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCategoryRenamed: suspend (oldName: String, newName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var barcode by remember { mutableStateOf(toEnglishDigits(initialBarcode)) }
    var showBarcodeScanModal by remember { mutableStateOf(false) }

    LaunchedEffect(initialBarcode) {
        if (initialBarcode.isNotBlank()) {
            barcode = toEnglishDigits(initialBarcode)
        }
    }
    LaunchedEffect(Unit) {
        HardwareBarcodeScanner.barcodeEvents.collect { code ->
            val englishCode = toEnglishDigits(code.trim())
            if (englishCode.isNotBlank()) {
                barcode = englishCode
                beepAndVibrate(context)
            }
        }
    }
    val settingsDataStore = remember { SettingsDataStore(context) }
    val customCategories by settingsDataStore.categoriesFlow.collectAsStateWithLifecycle(initialValue = CategoryItem.defaultCategories())
    val coroutineScope = rememberCoroutineScope()
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(initialCategory) }
    var purchasePrice by remember { mutableStateOf(if (initialPurchasePrice == 0.0) "" else initialPurchasePrice.toString()) }
    var mrpPrice by remember {
        mutableStateOf(
            if (initialMrpPrice > 0.0) initialMrpPrice.toString()
            else if (initialSellingPrice > 0.0) initialSellingPrice.toString()
            else ""
        )
    }
    var discountedPrice by remember {
        mutableStateOf(
            if (initialDiscountedPrice > 0.0) initialDiscountedPrice.toString()
            else if (initialSellingPrice > 0.0) initialSellingPrice.toString()
            else ""
        )
    }
    var wholesalePrice by remember { mutableStateOf(if (initialWholesalePrice == 0.0) "" else initialWholesalePrice.toString()) }
    var stockQty by remember {
        mutableStateOf(
            if (initialStock == 0.0) ""
            else if (initialStock % 1.0 == 0.0) initialStock.toLong().toString()
            else String.format(Locale.US, "%.2f", initialStock).trimEnd('0').trimEnd('.')
        )
    }
    var reorderLevel by remember { mutableStateOf(initialReorder.toString()) }
    var unit by remember { mutableStateOf(initialUnit) }
    var imageUri by remember { mutableStateOf(initialImageUri) }
    var mfgOrPkgType by remember { mutableStateOf(initialMfgOrPkgType) }
    var mfgOrPkgDate by remember { mutableStateOf(initialMfgOrPkgDate) }
    var expiryDate by remember { mutableStateOf(initialExpiryDate) }
    var editStockReason by remember { mutableStateOf("Restock") }
    var editStockNote by remember { mutableStateOf("") }

    var showEnlargedView by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = copyUriToInternalStorage(context, uri)
            if (savedPath != null) {
                imageUri = savedPath
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val file = File(context.filesDir, "product_${System.currentTimeMillis()}.jpg")
            try {
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                imageUri = file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val submitProduct = {
        val pPrice = purchasePrice.toDoubleOrNull() ?: 0.0
        val mPrice = mrpPrice.toDoubleOrNull() ?: 0.0
        val dPrice = discountedPrice.toDoubleOrNull() ?: 0.0
        val wPrice = wholesalePrice.toDoubleOrNull() ?: 0.0
        val effectiveSelling = if (dPrice > 0.0) dPrice else if (mPrice > 0.0) mPrice else 0.0
        val effectiveMrp = if (mPrice > 0.0) mPrice else effectiveSelling
        val effectiveDisc = if (dPrice > 0.0) dPrice else effectiveSelling
        val qty = stockQty.toDoubleOrNull() ?: 0.0
        val reorder = reorderLevel.toIntOrNull() ?: 0
        val finalBarcode = toEnglishDigits(barcode).trim().ifBlank {
            (100000000000..999999999999).random().toString()
        }

        when {
            name.isBlank() -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_enter_product_name_warning), Toast.LENGTH_SHORT).show()
            }
            effectiveSelling <= 0 -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_enter_valid_selling_price_warning), Toast.LENGTH_SHORT).show()
            }
            pPrice < 0.0 || wPrice < 0.0 || qty < 0.0 || reorder < 0 -> {
                isError = true
                Toast.makeText(context, "Prices and stock quantities cannot be negative", Toast.LENGTH_SHORT).show()
            }
            else -> {
                isError = false
                onSave(
                    name, finalBarcode, category, pPrice, effectiveSelling, effectiveMrp,
                    effectiveDisc, qty, reorder, imageUri, mfgOrPkgType, mfgOrPkgDate,
                    expiryDate, unit, wPrice, editStockReason.takeIf { isEdit },
                    editStockNote.trim().takeIf { isEdit && it.isNotBlank() }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF6366F1), Color(0xFF4F46E5), Color(0xFF4338CA))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isEdit) {
                            Text("✏️", fontSize = 24.sp)
                        } else {
                            Animated3DIsometricBoxIcon(fontSize = 28.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEdit) stringResource(R.string.edit_product) else stringResource(R.string.add_new_product),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isEdit) stringResource(R.string.products_edit_subtitle) else stringResource(R.string.products_add_subtitle),
                                fontSize = 10.5.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(30.dp)
                                .background(Color.White.copy(alpha = 0.20f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFAFAFA),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.products_product_name_required_label), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                                    BasicTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        textStyle = TextStyle(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B)
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFF4F46E5)),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                                    .background(Color.White, RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        if (name.isEmpty()) {
                                                            Text(
                                                                text = stringResource(R.string.products_product_name_placeholder),
                                                                fontSize = 13.sp,
                                                                color = Color(0xFF94A3B8)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    if (name.isNotBlank()) {
                                                        IconButton(onClick = { name = "" }, modifier = Modifier.size(24.dp)) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = stringResource(R.string.products_clear_cd),
                                                                modifier = Modifier.size(16.dp),
                                                                tint = Color(0xFF64748B)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.products_barcode_sku_optional_label), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                                        Text(stringResource(R.string.products_auto_generated_hint), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
                                    }
                                    BasicTextField(
                                        value = barcode,
                                        onValueChange = { barcode = toEnglishDigits(it) },
                                        textStyle = TextStyle(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B)
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFF4F46E5)),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                                    .background(Color.White, RoundedCornerShape(8.dp))
                                                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("🔢", fontSize = 13.sp)
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        if (barcode.isEmpty()) {
                                                            Text(
                                                                text = stringResource(R.string.products_barcode_placeholder),
                                                                fontSize = 12.5.sp,
                                                                color = Color(0xFF94A3B8),
                                                                maxLines = 1
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    if (barcode.isBlank()) {
                                                        Surface(
                                                            onClick = {
                                                                barcode = "SKU" + System.currentTimeMillis().toString().takeLast(8)
                                                            },
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color(0xFFEEF2FF),
                                                            border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.products_auto_chip),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF4F46E5),
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    } else {
                                                        IconButton(onClick = { barcode = "" }, modifier = Modifier.size(24.dp)) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = stringResource(R.string.products_clear_cd),
                                                                modifier = Modifier.size(16.dp),
                                                                tint = Color(0xFF64748B)
                                                            )
                                                        }
                                                    }
                                                    IconButton(onClick = { showBarcodeScanModal = true }, modifier = Modifier.size(28.dp)) {
                                                        Animated3DScannerIcon()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Category Dropdown & Custom Category Manager
                    item {
                        val selectedCatItem = customCategories.find { it.nameEn.equals(category, ignoreCase = true) }
                        val displayIcon = selectedCatItem?.icon ?: "🏷️"

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Header Row: 3D Animated Gold Folder + Title + Manage Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Realistic3DFolderIcon(size = 26.dp)
                                        Text(
                                            text = stringResource(R.string.products_category_label),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFEEF2FF),
                                        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                                        modifier = Modifier.clickable { showCategoryManagerDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("⚙️", fontSize = 11.sp)
                                            Text(
                                                text = stringResource(R.string.products_manage_button),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4F46E5)
                                            )
                                        }
                                    }
                                }

                                // Dropdown Selector with Search & Predefined Items
                                ExposedDropdownMenuBox(
                                    expanded = categoryDropdownExpanded,
                                    onExpandedChange = { categoryDropdownExpanded = it }
                                ) {
                                    BasicTextField(
                                        value = category,
                                        onValueChange = {
                                            category = it
                                            categoryDropdownExpanded = true
                                        },
                                        textStyle = TextStyle(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1E293B)
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(Color(0xFF4F46E5)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                                    .background(Color.White, RoundedCornerShape(8.dp))
                                                    .border(
                                                        1.dp,
                                                        if (categoryDropdownExpanded) Color(0xFF4F46E5) else Color(0xFFCBD5E1),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 10.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(displayIcon, fontSize = 15.sp)
                                                    Box(modifier = Modifier.weight(1f)) {
                                                        if (category.isEmpty()) {
                                                            Text(
                                                                text = stringResource(R.string.products_category_placeholder),
                                                                fontSize = 13.sp,
                                                                color = Color(0xFF94A3B8)
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = "Expand category dropdown",
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    val filtered = if (category.isBlank()) customCategories else customCategories.filter {
                                        it.nameEn.contains(category, ignoreCase = true) || it.nameBn.contains(category)
                                    }

                                    ExposedDropdownMenu(
                                        expanded = categoryDropdownExpanded,
                                        onDismissRequest = { categoryDropdownExpanded = false },
                                        modifier = Modifier.background(Color.White)
                                    ) {
                                        if (filtered.isEmpty()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.products_custom_category, category),
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF64748B),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                },
                                                onClick = { categoryDropdownExpanded = false }
                                            )
                                        } else {
                                            filtered.forEach { item ->
                                                val isSelected = category.equals(item.nameEn, ignoreCase = true)
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Text(item.icon, fontSize = 16.sp)
                                                                Text(
                                                                    item.nameEn,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF0F172A)
                                                                )
                                                                if (item.nameBn.isNotBlank() && item.nameBn != item.nameEn) {
                                                                    Text(
                                                                        "(${item.nameBn})",
                                                                        fontSize = 11.sp,
                                                                        color = Color(0xFF64748B)
                                                                    )
                                                                }
                                                            }
                                                            if (isSelected) {
                                                                Text("✓", color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    },
                                                    onClick = {
                                                        category = item.nameEn
                                                        categoryDropdownExpanded = false
                                                    },
                                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFFE2E8F0))

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text("⚙️", fontSize = 12.sp)
                                                    Text(
                                                        stringResource(R.string.products_manage_edit_categories),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF4F46E5)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                categoryDropdownExpanded = false
                                                showCategoryManagerDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFAFAFA),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Animated3DStudioCameraIcon()
                                        Text(
                                            text = stringResource(R.string.products_product_image_label),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                    if (imageUri != null) {
                                        TextButton(
                                            onClick = { imageUri = null },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text(stringResource(R.string.products_remove_button), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val painter = rememberUriImagePainter(imageUri)
                                    if (painter != null) {
                                        Image(
                                            bitmap = painter,
                                            contentDescription = stringResource(R.string.products_image_preview_cd),
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.5.dp, Color(0xFF4F46E5), RoundedCornerShape(8.dp))
                                                .clickable { showEnlargedView = true },
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(Color.White, RoundedCornerShape(8.dp))
                                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = null,
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { cameraLauncher.launch(null) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Text(stringResource(R.string.products_camera_button), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = { galleryLauncher.launch("image/*") },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Text(stringResource(R.string.products_gallery_button), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Animated3DCoinIcon()
                                        Text(
                                            stringResource(R.string.products_pricing_profit_intelligence),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            color = Color(0xFF4338CA),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFE0E7FF)
                                    ) {
                                        Text(
                                            stringResource(R.string.products_4tier_badge),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4338CA),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                // Quick Profit Markup Presets
                                val pCostVal = purchasePrice.toDoubleOrNull() ?: 0.0
                                if (pCostVal > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.products_quick_markup_label), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                                        listOf(10, 20, 30, 50).forEach { pct ->
                                            Surface(
                                                onClick = {
                                                    val sell = pCostVal * (1.0 + pct / 100.0)
                                                    discountedPrice = String.format(Locale.US, "%.2f", sell)
                                                    val currMrp = mrpPrice.toDoubleOrNull() ?: 0.0
                                                    if (currMrp < sell) {
                                                        mrpPrice = String.format(Locale.US, "%.2f", sell * 1.15)
                                                    }
                                                },
                                                shape = RoundedCornerShape(5.dp),
                                                color = Color(0xFFEEF2FF),
                                                border = BorderStroke(1.dp, Color(0xFFC7D2FE))
                                            ) {
                                                Text(
                                                    "+$pct%",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4338CA),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.products_purchase_price_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                        BasicTextField(
                                            value = purchasePrice,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() || it == '.' }
                                                if (filtered.count { it == '.' } <= 1) purchasePrice = filtered.take(8)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            cursorBrush = SolidColor(Color(0xFFEF4444)),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (purchasePrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.products_mrp_price_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                                        BasicTextField(
                                            value = mrpPrice,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() || it == '.' }
                                                if (filtered.count { it == '.' } <= 1) {
                                                    mrpPrice = filtered.take(8)
                                                    if (discountedPrice.isBlank()) {
                                                        discountedPrice = filtered.take(8)
                                                    }
                                                }
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            cursorBrush = SolidColor(Color(0xFF8B5CF6)),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (mrpPrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.products_selling_price_required_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                        BasicTextField(
                                            value = discountedPrice,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() || it == '.' }
                                                if (filtered.count { it == '.' } <= 1) discountedPrice = filtered.take(8)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            cursorBrush = SolidColor(Color(0xFF10B981)),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (discountedPrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.products_wholesale_price_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                        BasicTextField(
                                            value = wholesalePrice,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() || it == '.' }
                                                if (filtered.count { it == '.' } <= 1) wholesalePrice = filtered.take(8)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            cursorBrush = SolidColor(Color(0xFFF59E0B)),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (wholesalePrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }
                                }

                                val pCost = purchasePrice.toDoubleOrNull() ?: 0.0
                                val pMrp = mrpPrice.toDoubleOrNull() ?: 0.0
                                val pDiscounted = discountedPrice.toDoubleOrNull() ?: 0.0
                                val effectiveSell = if (pDiscounted > 0.0) pDiscounted else pMrp

                                if (effectiveSell > 0.0 || pCost > 0.0) {
                                    val profitPerUnit = effectiveSell - pCost
                                    val marginPct = if (effectiveSell > 0.0) (profitPerUnit / effectiveSell) * 100.0 else 0.0
                                    val markupPct = if (pCost > 0.0) (profitPerUnit / pCost) * 100.0 else 0.0
                                    val discountPct = if (pMrp > 0.0 && pDiscounted > 0.0 && pMrp > pDiscounted) ((pMrp - pDiscounted) / pMrp) * 100.0 else 0.0

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (profitPerUnit >= 0) Color(0xFFECFDF5) else Color(0xFFFFF1F2),
                                        border = BorderStroke(1.dp, if (profitPerUnit >= 0) Color(0xFFA7F3D0) else Color(0xFFFECDD3)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(if (profitPerUnit >= 0) "📈" else "⚠️", fontSize = 13.sp)
                                                    Text(
                                                        text = if (profitPerUnit >= 0) "Unit Profit: +৳${String.format(Locale.US, "%.2f", profitPerUnit)}" else "Loss per Unit: -৳${String.format(Locale.US, "%.2f", -profitPerUnit)}",
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 12.sp,
                                                        color = if (profitPerUnit >= 0) Color(0xFF065F46) else Color(0xFF9F1239)
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Surface(
                                                        shape = RoundedCornerShape(5.dp),
                                                        color = if (profitPerUnit >= 0) Color(0xFF059669) else Color(0xFFE11D48)
                                                    ) {
                                                        Text(
                                                            text = "Margin ${String.format(Locale.US, "%.1f", marginPct)}%",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    if (markupPct != 0.0 && pCost > 0.0) {
                                                        Surface(
                                                            shape = RoundedCornerShape(5.dp),
                                                            color = Color(0xFF4338CA)
                                                        ) {
                                                            Text(
                                                                text = "Markup ${String.format(Locale.US, "%.1f", markupPct)}%",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp,
                                                                color = Color.White,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (profitPerUnit < 0) {
                                                Text(
                                                    text = "⚠️ Alert: Selling price is lower than cost price!",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFBE123C)
                                                )
                                            } else if (discountPct > 0.0) {
                                                Text(
                                                    text = stringResource(R.string.products_discount_off_mrp, String.format(Locale.US, "%.1f", discountPct)),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        var showUnitDropdown by remember { mutableStateOf(false) }
                        val unitOptions = listOf("Pcs", "Kg", "Gram", "Ltr", "Mtr", "Box", "Dozen", "Pack", "Pair")

                        val currentStockVal = stockQty.toDoubleOrNull() ?: 0.0
                        val currentMinAlert = reorderLevel.toDoubleOrNull() ?: 5.0
                        val (healthText, healthBg, healthColor) = when {
                            currentStockVal <= 0.0 -> Triple(stringResource(R.string.out_of_stock), Color(0xFFFEF2F2), Color(0xFFDC2626))
                            currentStockVal <= currentMinAlert -> Triple(stringResource(R.string.low_stock), Color(0xFFFFFBEB), Color(0xFFD97706))
                            else -> Triple(stringResource(R.string.in_stock), Color(0xFFECFDF5), Color(0xFF059669))
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFAFAFA),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Animated3DInventoryGaugeIcon()
                                        Text(
                                            text = stringResource(R.string.products_stock_measurement_label),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = healthBg
                                    ) {
                                        Text(
                                            healthText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = healthColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // 1. Stock Qty
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.stock_qty),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        BasicTextField(
                                            value = stockQty,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() || it == '.' }
                                                if (filtered.count { it == '.' } <= 1) {
                                                    stockQty = filtered.take(7)
                                                }
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            cursorBrush = SolidColor(Color(0xFF4F46E5)),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (stockQty.isEmpty()) {
                                                        Text("0", fontSize = 13.5.sp, color = Color(0xFF94A3B8))
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }

                                    // 2. Min Alert
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.products_min_alert_label),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        BasicTextField(
                                            value = reorderLevel,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() }
                                                reorderLevel = filtered.take(5)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1E293B)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            cursorBrush = SolidColor(Color(0xFF4F46E5)),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(Color.White, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (reorderLevel.isEmpty()) {
                                                        Text("5", fontSize = 13.5.sp, color = Color(0xFF94A3B8))
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }

                                    // 3. Unit Selector
                                    Column(
                                        modifier = Modifier.weight(1.05f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.products_unit_label),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Surface(
                                                onClick = { showUnitDropdown = true },
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.White,
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = unit,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF1E293B)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = "Select unit",
                                                        tint = Color(0xFF64748B),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = showUnitDropdown,
                                                onDismissRequest = { showUnitDropdown = false },
                                                modifier = Modifier.background(Color.White)
                                            ) {
                                                unitOptions.forEach { u ->
                                                    val isSelected = unit == u
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = u,
                                                                fontSize = 13.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF334155)
                                                            )
                                                        },
                                                        onClick = {
                                                            unit = u
                                                            showUnitDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                val stockAltered = isEdit && stockQty.toDoubleOrNull() != null && kotlin.math.abs((stockQty.toDoubleOrNull() ?: 0.0) - initialStock) > 0.0001
                                AnimatedVisibility(
                                    visible = stockAltered,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.products_reason_for_stock_adjustment_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4F46E5),
                                            letterSpacing = 0.5.sp
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            listOf("Restock", "Damage", "Expired", "Discrepancy", "Sample", "Store Use", "Internal Use").forEach { r ->
                                                FilterChip(
                                                    selected = editStockReason == r,
                                                    onClick = { editStockReason = r },
                                                    label = { Text(localizedAdjustmentReasonLabel(r), fontSize = 10.5.sp) },
                                                    shape = RoundedCornerShape(7.dp)
                                                )
                                            }
                                        }
                                        OutlinedTextField(
                                            value = editStockNote,
                                            onValueChange = { editStockNote = it },
                                            placeholder = { Text(stringResource(R.string.products_audit_note_hint), fontSize = 12.sp) },
                                            maxLines = 2,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.products_date_tracking_section),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        FilterChip(
                                            selected = mfgOrPkgType == "Mfg",
                                            onClick = { mfgOrPkgType = "Mfg" },
                                            label = { Text("Mfg", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Animated3DFactoryIcon() },
                                            shape = RoundedCornerShape(7.dp),
                                            modifier = Modifier.height(28.dp)
                                        )
                                        FilterChip(
                                            selected = mfgOrPkgType == "Pkg",
                                            onClick = { mfgOrPkgType = "Pkg" },
                                            label = { Text("Pkg", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Animated3DPackageIcon(fontSize = 12.sp) },
                                            shape = RoundedCornerShape(7.dp),
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DateInputField(
                                        label = if (mfgOrPkgType == "Pkg") stringResource(R.string.products_pkg_date_label) else stringResource(R.string.products_mfg_date_label),
                                        value = mfgOrPkgDate,
                                        onValueChange = { mfgOrPkgDate = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DateInputField(
                                        label = stringResource(R.string.products_exp_date_label),
                                        value = expiryDate,
                                        onValueChange = { expiryDate = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    if (isError) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.products_validation_error_message),
                                    color = Color(0xFFB91C1C),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(stringResource(R.string.products_cancel_action), fontWeight = FontWeight.Bold, color = Color(0xFF475569), fontSize = 12.5.sp)
                        }

                        if (isEdit && onDelete != null) {
                            OutlinedButton(
                                onClick = onDelete,
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Text(stringResource(R.string.products_delete_action), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            }
                        }

                        Button(
                            onClick = submitProduct,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Animated3DSaveIcon()
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.products_save_product_button), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }


        if (showEnlargedView) {
            EnlargedImageDialog(
                imageUri = imageUri,
                title = name.ifBlank { stringResource(R.string.products_product_image_label) },
                onDismiss = { showEnlargedView = false }
            )
        }
    }

    if (showBarcodeScanModal) {
        ProductBarcodeScanDialog(
            onBarcodeScanned = { scannedCode ->
                barcode = scannedCode
                showBarcodeScanModal = false
            },
            onDismiss = { showBarcodeScanModal = false }
        )
    }

    if (showCategoryManagerDialog) {
        ManageCategoriesDialog(
            categories = customCategories,
            onDismiss = { showCategoryManagerDialog = false },
            onSave = { updatedList ->
                coroutineScope.launch {
                    // Cascade any renames to products already tagged with the old category name,
                    // so they don't silently fall out of category filters/reports.
                    val oldById = customCategories.associateBy { it.id }
                    updatedList.forEach { updated ->
                        val previous = oldById[updated.id]
                        if (previous != null && previous.nameEn != updated.nameEn && previous.nameEn.isNotBlank()) {
                            onCategoryRenamed(previous.nameEn, updated.nameEn)
                        }
                    }
                    settingsDataStore.saveCategories(updatedList)
                    showCategoryManagerDialog = false
                    Toast.makeText(context, context.getString(R.string.products_categories_updated_toast), Toast.LENGTH_SHORT).show()
                }
            },
            onReset = {
                coroutineScope.launch {
                    settingsDataStore.resetDefaultCategories()
                    Toast.makeText(context, context.getString(R.string.products_categories_reset_toast), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun ProductBarcodeScanDialog(
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(440.dp),
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.products_scan_recapture_barcode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description), tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                    DisposableEffect(Unit) {
                        onDispose {
                            cameraExecutor.shutdown()
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
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
                                                if (barcode.isNotBlank()) {
                                                    beepAndVibrate(ctx)
                                                    onBarcodeScanned(barcode)
                                                }
                                            }
                                        )
                                    )

                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .size(width = 240.dp, height = 120.dp)
                            .border(2.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val newBarcode = (100000000000..999999999999).random().toString()
                            beepAndVibrate(context)
                            onBarcodeScanned(newBarcode)
                        }
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.products_auto_generate_button), fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text(stringResource(R.string.products_cancel_action), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PriceTierSwitcherCard(
    showPrice: Boolean,
    onShowPriceChange: (Boolean) -> Unit,
    selectedTier: ProductCodePriceTier,
    onTierSelect: (ProductCodePriceTier) -> Unit,
    sellingPriceDisplay: String,
    mrpPriceDisplay: String,
    wholesalePriceDisplay: String,
    showUnit: Boolean = false,
    onShowUnitChange: (Boolean) -> Unit = {},
    showBanglaDigits: Boolean = false,
    onShowBanglaDigitsChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val cardBorderColor = Color(0xFFC084FC)
    val headerTitleColor = if (isDark) Color(0xFFE9D5FF) else Color(0xFF581C87)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(2.dp, cardBorderColor),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: 🏷️ Price Tier on Label (NO "NEW FEATURE" badge) | Show Price Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "🏷️",
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(R.string.products_price_tier_label),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = headerTitleColor,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShowPriceChange(!showPrice) }
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showPrice,
                        onCheckedChange = { onShowPriceChange(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF0D9488)
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.show_price),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body content dimmed and disabled if showPrice is false
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (showPrice) 1f else 0.4f }
            ) {
                // 3-Way Segmented Grid (Selling, MRP, Wholesale)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Selling Price
                    val isSellingActive = selectedTier == ProductCodePriceTier.SELLING
                    val sellingBg = if (isSellingActive) {
                        if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC)
                    }
                    val sellingBorder = if (isSellingActive) Color(0xFF0284C7) else if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
                    val sellingTagColor = if (isSellingActive) {
                        if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
                    } else {
                        Color(0xFF64748B)
                    }
                    val sellingValColor = if (isSellingActive) {
                        if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF0F172A)
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = showPrice) { onTierSelect(ProductCodePriceTier.SELLING) },
                        shape = RoundedCornerShape(12.dp),
                        color = sellingBg,
                        border = BorderStroke(1.5.dp, sellingBorder),
                        shadowElevation = if (isSellingActive) 3.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.products_price_tier_selling).uppercase(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                color = sellingTagColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sellingPriceDisplay,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = sellingValColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. MRP Price
                    val isMrpActive = selectedTier == ProductCodePriceTier.MRP
                    val mrpBg = if (isMrpActive) {
                        if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC)
                    }
                    val mrpBorder = if (isMrpActive) Color(0xFFD97706) else if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
                    val mrpTagColor = if (isMrpActive) {
                        if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309)
                    } else {
                        Color(0xFF64748B)
                    }
                    val mrpValColor = if (isMrpActive) {
                        if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF0F172A)
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = showPrice) { onTierSelect(ProductCodePriceTier.MRP) },
                        shape = RoundedCornerShape(12.dp),
                        color = mrpBg,
                        border = BorderStroke(1.5.dp, mrpBorder),
                        shadowElevation = if (isMrpActive) 3.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.products_price_tier_mrp).uppercase(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                color = mrpTagColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mrpPriceDisplay,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = mrpValColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 3. Wholesale Price
                    val isWholesaleActive = selectedTier == ProductCodePriceTier.WHOLESALE
                    val wholesaleBg = if (isWholesaleActive) {
                        if (isDark) Color(0xFF3B0764) else Color(0xFFF3E8FF)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC)
                    }
                    val wholesaleBorder = if (isWholesaleActive) Color(0xFF7C3AED) else if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
                    val wholesaleTagColor = if (isWholesaleActive) {
                        if (isDark) Color(0xFFD8B4FE) else Color(0xFF6D28D9)
                    } else {
                        Color(0xFF64748B)
                    }
                    val wholesaleValColor = if (isWholesaleActive) {
                        if (isDark) Color(0xFFA855F7) else Color(0xFF7C3AED)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF0F172A)
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = showPrice) { onTierSelect(ProductCodePriceTier.WHOLESALE) },
                        shape = RoundedCornerShape(12.dp),
                        color = wholesaleBg,
                        border = BorderStroke(1.5.dp, wholesaleBorder),
                        shadowElevation = if (isWholesaleActive) 3.dp else 0.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.products_price_tier_wholesale).uppercase(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                                color = wholesaleTagColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = wholesalePriceDisplay,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = wholesaleValColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dual Display (MRP + Offer) Button
                val isDualActive = selectedTier == ProductCodePriceTier.DUAL
                val dualBg = if (isDualActive) {
                    if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5)
                } else {
                    if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF0FDF4)
                }
                val dualBorderDashedColor = if (isDark) Color(0xFF059669).copy(alpha = 0.7f) else Color(0xFF86EFAC)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(dualBg)
                        .then(
                            if (isDualActive) {
                                Modifier.border(1.5.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                            } else {
                                Modifier.drawBehind {
                                    drawRoundRect(
                                        color = dualBorderDashedColor,
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                                        ),
                                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                                    )
                                }
                            }
                        )
                        .clickable(enabled = showPrice) { onTierSelect(ProductCodePriceTier.DUAL) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.products_price_tier_dual_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        val notSetText = stringResource(R.string.products_price_tier_not_set)
                        val mrpCandidate = if (mrpPriceDisplay.isNotBlank() && mrpPriceDisplay != notSetText && mrpPriceDisplay != "—") {
                            mrpPriceDisplay
                        } else {
                            sellingPriceDisplay
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0xFF064E3B).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "MRP $mrpCandidate",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B),
                                    textDecoration = TextDecoration.LineThrough,
                                    maxLines = 1
                                )
                                Text(
                                    text = sellingPriceDisplay,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dotted / Dashed Divider Line
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                ) {
                    drawLine(
                        color = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Micro-customizations (Unit & Language options)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Show Unit (/Pcs)
                    val unitBg = if (showUnit) {
                        if (isDark) Color(0xFF312E81) else Color(0xFFE0E7FF)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F5F9)
                    }
                    val unitBorder = if (showUnit) {
                        Color(0xFF818CF8)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
                    }
                    val unitTextColor = if (showUnit) {
                        if (isDark) Color(0xFFC7D2FE) else Color(0xFF3730A3)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF475569)
                    }
                    val unitIndicatorColor = if (showUnit) {
                        if (isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else Color(0xFF64748B)
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = showPrice) { onShowUnitChange(!showUnit) },
                        shape = RoundedCornerShape(8.dp),
                        color = unitBg,
                        border = BorderStroke(1.dp, unitBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.products_price_opt_unit),
                                fontSize = 11.sp,
                                fontWeight = if (showUnit) FontWeight.Bold else FontWeight.SemiBold,
                                color = unitTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showUnit) stringResource(R.string.opt_on) else stringResource(R.string.opt_off),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = unitIndicatorColor
                            )
                        }
                    }

                    // Option 2: Bangla Digits (৳৭০)
                    val banglaBg = if (showBanglaDigits) {
                        if (isDark) Color(0xFF312E81) else Color(0xFFE0E7FF)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F5F9)
                    }
                    val banglaBorder = if (showBanglaDigits) {
                        Color(0xFF818CF8)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFFE2E8F0)
                    }
                    val banglaTextColor = if (showBanglaDigits) {
                        if (isDark) Color(0xFFC7D2FE) else Color(0xFF3730A3)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF475569)
                    }
                    val banglaIndicatorColor = if (showBanglaDigits) {
                        if (isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                    } else {
                        if (isDark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else Color(0xFF64748B)
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = showPrice) { onShowBanglaDigitsChange(!showBanglaDigits) },
                        shape = RoundedCornerShape(8.dp),
                        color = banglaBg,
                        border = BorderStroke(1.dp, banglaBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.products_price_opt_bangla_digits),
                                fontSize = 11.sp,
                                fontWeight = if (showBanglaDigits) FontWeight.Bold else FontWeight.SemiBold,
                                color = banglaTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showBanglaDigits) stringResource(R.string.opt_on) else stringResource(R.string.opt_off),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = banglaIndicatorColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchProductCodePrintDialog(
    products: List<Product>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuredStoreName by remember(context) { SettingsDataStore(context).shopNameFlow }
        .collectAsStateWithLifecycle(initialValue = "")

    var filterInStockOnly by rememberSaveable { mutableStateOf(false) }
    var copiesModeByStock by rememberSaveable { mutableStateOf(false) }
    var showPrice by rememberSaveable { mutableStateOf(true) }
    var priceTierName by rememberSaveable { mutableStateOf(ProductCodePriceTier.SELLING.name) }
    val priceTier = ProductCodePriceTier.entries.firstOrNull { it.name == priceTierName } ?: ProductCodePriceTier.SELLING
    var showUnit by rememberSaveable { mutableStateOf(false) }
    var showBanglaDigits by rememberSaveable { mutableStateOf(false) }
    var showCodeNumber by rememberSaveable { mutableStateOf(true) }
    var showStoreName by rememberSaveable { mutableStateOf(true) }
    var labelSizeName by rememberSaveable { mutableStateOf(ProductCodeLabelSize.SIZE_50_X_30.name) }
    var labelSizeMenuExpanded by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }

    val labelSize = ProductCodeLabelSize.entries.firstOrNull { it.name == labelSizeName }
        ?: ProductCodeLabelSize.SIZE_50_X_30

    val candidateProducts = remember(products, filterInStockOnly) {
        products.filter { prod ->
            prod.barcode.isNotBlank() && (!filterInStockOnly || prod.stockQty > 0)
        }
    }

    val totalLabelCount = remember(candidateProducts, copiesModeByStock) {
        if (copiesModeByStock) {
            candidateProducts.sumOf { kotlin.math.max(1, it.stockQty.toInt()) }
        } else {
            candidateProducts.size
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.batch_print_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.batch_print_scope),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isGenerating,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Scope: All vs In-Stock Only
                Text(
                    stringResource(R.string.batch_print_scope),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !filterInStockOnly,
                        onClick = { filterInStockOnly = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.batch_print_all, products.count { it.barcode.isNotBlank() }),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    SegmentedButton(
                        selected = filterInStockOnly,
                        onClick = { filterInStockOnly = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(R.string.batch_print_in_stock, products.count { it.barcode.isNotBlank() && it.stockQty > 0 }),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Copies Mode
                Text(
                    stringResource(R.string.batch_print_copies_mode),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !copiesModeByStock,
                        onClick = { copiesModeByStock = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.batch_print_one_each), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = copiesModeByStock,
                        onClick = { copiesModeByStock = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.batch_print_stock_qty), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Label Size Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { labelSizeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(labelSize.displayName, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(
                        expanded = labelSizeMenuExpanded,
                        onDismissRequest = { labelSizeMenuExpanded = false }
                    ) {
                        ProductCodeLabelSize.entries.filter { it != ProductCodeLabelSize.CUSTOM }.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size.displayName) },
                                onClick = {
                                    labelSizeName = size.name
                                    labelSizeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Price Tier Switcher Card
                val sampleProd = candidateProducts.firstOrNull()
                val batchSellingDisplay = if (sampleProd != null) {
                    val base = formatTaka(sampleProd.sellingPrice)
                    if (showBanglaDigits) toBanglaDigits(base) else base
                } else "—"
                val batchMrpDisplay = if (sampleProd != null && sampleProd.mrpPrice > 0) {
                    val base = formatTaka(sampleProd.mrpPrice)
                    if (showBanglaDigits) toBanglaDigits(base) else base
                } else stringResource(R.string.products_price_tier_not_set)
                val batchWholesaleDisplay = if (sampleProd != null && sampleProd.wholesalePrice > 0) {
                    val base = formatTaka(sampleProd.wholesalePrice)
                    if (showBanglaDigits) toBanglaDigits(base) else base
                } else stringResource(R.string.products_price_tier_not_set)

                PriceTierSwitcherCard(
                    showPrice = showPrice,
                    onShowPriceChange = { showPrice = it },
                    selectedTier = priceTier,
                    onTierSelect = { priceTierName = it.name },
                    sellingPriceDisplay = batchSellingDisplay,
                    mrpPriceDisplay = batchMrpDisplay,
                    wholesalePriceDisplay = batchWholesaleDisplay,
                    showUnit = showUnit,
                    onShowUnitChange = { showUnit = it },
                    showBanglaDigits = showBanglaDigits,
                    onShowBanglaDigitsChange = { showBanglaDigits = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.show_code_number), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = showCodeNumber, onCheckedChange = { showCodeNumber = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.products_show_store_name), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = showStoreName, onCheckedChange = { showStoreName = it })
                }

                // Summary Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total Pages/Labels:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "$totalLabelCount Labels",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Action Button
                Button(
                    onClick = {
                        if (candidateProducts.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.batch_print_no_products), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isGenerating = true
                        scope.launch(Dispatchers.IO) {
                            val items = candidateProducts.map { prod ->
                                val qty = if (copiesModeByStock) kotlin.math.max(1, prod.stockQty.toInt()) else 1
                                val priceString = formatProductCodeLabelPriceString(
                                    tier = priceTier,
                                    sellingPrice = prod.sellingPrice,
                                    mrpPrice = prod.mrpPrice,
                                    wholesalePrice = prod.wholesalePrice,
                                    showUnit = showUnit,
                                    showBanglaDigits = showBanglaDigits
                                )
                                ProductLabelItem(
                                    productName = prod.name,
                                    barcode = prod.barcode,
                                    formattedPrice = priceString,
                                    quantity = qty
                                )
                            }
                            val result = cacheBatchProductCodeLabelsPdf(
                                context = context,
                                items = items,
                                codeType = ProductCodeType.BARCODE,
                                labelSize = labelSize,
                                showPrice = showPrice,
                                showCodeNumber = showCodeNumber,
                                showStoreName = showStoreName,
                                storeName = configuredStoreName
                            )
                            withContext(Dispatchers.Main) {
                                isGenerating = false
                                if (result != null) {
                                    shareProductCodeLabelPdf(context, result.second, "Batch_Labels")
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to generate batch labels PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isGenerating && candidateProducts.isNotEmpty()
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.batch_print_generating), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.batch_print_generate_btn), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCodeImageDialog(
    product: Product,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val configuredStoreName by remember(context) { SettingsDataStore(context).shopNameFlow }
        .collectAsStateWithLifecycle(initialValue = "")
    val preferences = remember {
        context.getSharedPreferences("product_code_image_settings", android.content.Context.MODE_PRIVATE)
    }
    val hadSavedStoreName = remember { preferences.contains("store_name") }
    var codeTypeName by rememberSaveable {
        mutableStateOf(preferences.getString("code_type", ProductCodeType.BARCODE.name) ?: ProductCodeType.BARCODE.name)
    }
    var labelSizeName by rememberSaveable {
        val stored = preferences.getString("label_size", ProductCodeLabelSize.SIZE_50_X_30.name)
        mutableStateOf(
            when (stored) {
                "WIDE" -> ProductCodeLabelSize.SIZE_50_X_30.name
                "COMPACT" -> ProductCodeLabelSize.SIZE_40_X_25.name
                "SQUARE" -> ProductCodeLabelSize.SIZE_45_X_35.name
                else -> stored ?: ProductCodeLabelSize.SIZE_50_X_30.name
            }
        )
    }
    var showPrice by rememberSaveable { mutableStateOf(preferences.getBoolean("show_price", true)) }
    var priceTierName by rememberSaveable {
        mutableStateOf(preferences.getString("price_tier", ProductCodePriceTier.SELLING.name) ?: ProductCodePriceTier.SELLING.name)
    }
    val priceTier = ProductCodePriceTier.entries.firstOrNull { it.name == priceTierName } ?: ProductCodePriceTier.SELLING
    var showUnit by rememberSaveable { mutableStateOf(preferences.getBoolean("show_price_unit", false)) }
    var showBanglaDigits by rememberSaveable { mutableStateOf(preferences.getBoolean("show_bangla_digits", false)) }
    var showCodeNumber by rememberSaveable { mutableStateOf(preferences.getBoolean("show_code_number", true)) }
    var showStoreName by rememberSaveable { mutableStateOf(preferences.getBoolean("show_store_name", true)) }
    var showDateTracking by rememberSaveable { mutableStateOf(preferences.getBoolean("show_date_tracking", true)) }
    var mfgOrPkgType by rememberSaveable { mutableStateOf(product.mfgOrPkgType ?: "Mfg") }
    var mfgOrPkgDate by rememberSaveable { mutableStateOf(product.mfgOrPkgDate ?: "") }
    var expDate by rememberSaveable { mutableStateOf(product.expiryDate ?: "") }

    val formattedPriceString = formatProductCodeLabelPriceString(
        tier = priceTier,
        sellingPrice = product.sellingPrice,
        mrpPrice = product.mrpPrice,
        wholesalePrice = product.wholesalePrice,
        showUnit = showUnit,
        showBanglaDigits = showBanglaDigits
    )

    var storeNameInput by rememberSaveable {
        mutableStateOf(preferences.getString("store_name", "") ?: "")
    }
    var customWidthInput by rememberSaveable {
        mutableStateOf(preferences.getFloat("custom_width_mm", 50f).toString().removeSuffix(".0"))
    }
    var customHeightInput by rememberSaveable {
        mutableStateOf(preferences.getFloat("custom_height_mm", 30f).toString().removeSuffix(".0"))
    }
    var productNameTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("product_name_text_scale", 1f))
    }
    var priceTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("price_text_scale", 1f))
    }
    var codeNumberTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("code_number_text_scale", 1f))
    }
    var storeNameTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("store_name_text_scale", 1f))
    }
    var dateTrackingTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("date_tracking_text_scale", 1f))
    }
    var barcodeGraphicScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("barcode_graphic_scale", 1f))
    }
    var labelSizeMenuExpanded by remember { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val codeType = ProductCodeType.entries.firstOrNull { it.name == codeTypeName } ?: ProductCodeType.BARCODE
    val labelSize = ProductCodeLabelSize.entries.firstOrNull { it.name == labelSizeName }
        ?: ProductCodeLabelSize.SIZE_50_X_30
    val customWidthMm = customWidthInput.toFloatOrNull()?.coerceIn(15f, 150f) ?: 50f
    val customHeightMm = customHeightInput.toFloatOrNull()?.coerceIn(10f, 200f) ?: 30f
    val selectedLabelWidthMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customWidthMm else labelSize.widthMm
    val selectedLabelHeightMm = if (labelSize == ProductCodeLabelSize.CUSTOM) customHeightMm else labelSize.heightMm

    LaunchedEffect(configuredStoreName) {
        if (!hadSavedStoreName && configuredStoreName.isNotBlank()) {
            storeNameInput = configuredStoreName
        }
    }

    val bitmap = remember(
        product, codeType, labelSize, showPrice, priceTierName, showUnit, showBanglaDigits, showCodeNumber, showStoreName, storeNameInput,
        showDateTracking, mfgOrPkgType, mfgOrPkgDate, expDate,
        customWidthMm, customHeightMm, productNameTextScale, priceTextScale,
        codeNumberTextScale, storeNameTextScale, dateTrackingTextScale, barcodeGraphicScale
    ) {
        generateProductCodeLabelBitmap(
            productName = product.name,
            barcode = product.barcode,
            formattedPrice = formattedPriceString,
            codeType = codeType,
            labelSize = labelSize,
            showPrice = showPrice,
            showCodeNumber = showCodeNumber,
            customWidthMm = customWidthMm,
            customHeightMm = customHeightMm,
            productNameTextScale = productNameTextScale,
            priceTextScale = priceTextScale,
            codeNumberTextScale = codeNumberTextScale,
            showStoreName = showStoreName,
            storeName = storeNameInput,
            storeNameTextScale = storeNameTextScale,
            showDateTracking = showDateTracking,
            mfgOrPkgType = mfgOrPkgType,
            mfgOrPkgDate = mfgOrPkgDate,
            expDate = expDate,
            dateTrackingTextScale = dateTrackingTextScale,
            barcodeGraphicScale = barcodeGraphicScale
        )
    }

    LaunchedEffect(
        codeTypeName, labelSizeName, showPrice, priceTierName, showUnit, showBanglaDigits, showCodeNumber, showStoreName, storeNameInput,
        showDateTracking, mfgOrPkgType, mfgOrPkgDate, expDate,
        customWidthMm, customHeightMm, productNameTextScale, priceTextScale,
        codeNumberTextScale, storeNameTextScale, dateTrackingTextScale, barcodeGraphicScale
    ) {
        preferences.edit()
            .putString("code_type", codeTypeName)
            .putString("label_size", labelSizeName)
            .putBoolean("show_price", showPrice)
            .putString("price_tier", priceTierName)
            .putBoolean("show_price_unit", showUnit)
            .putBoolean("show_bangla_digits", showBanglaDigits)
            .putBoolean("show_code_number", showCodeNumber)
            .putBoolean("show_store_name", showStoreName)
            .putBoolean("show_date_tracking", showDateTracking)
            .putString("store_name", storeNameInput.trim())
            .putFloat("custom_width_mm", customWidthMm)
            .putFloat("custom_height_mm", customHeightMm)
            .putFloat("product_name_text_scale", productNameTextScale)
            .putFloat("price_text_scale", priceTextScale)
            .putFloat("code_number_text_scale", codeNumberTextScale)
            .putFloat("store_name_text_scale", storeNameTextScale)
            .putFloat("date_tracking_text_scale", dateTrackingTextScale)
            .putFloat("barcode_graphic_scale", barcodeGraphicScale)
            .apply()
        actionStatus = null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.generate_code_image),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = codeType == ProductCodeType.BARCODE,
                        onClick = { codeTypeName = ProductCodeType.BARCODE.name },
                        label = { Text(stringResource(R.string.barcode_image)) },
                        leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = codeType == ProductCodeType.QR_CODE,
                        onClick = { codeTypeName = ProductCodeType.QR_CODE.name },
                        label = { Text(stringResource(R.string.qr_code)) },
                        leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStoreName = !showStoreName }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = showStoreName, onCheckedChange = { showStoreName = it })
                        Text(stringResource(R.string.products_show_store_name), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                AnimatedVisibility(visible = showStoreName) {
                    OutlinedTextField(
                        value = storeNameInput,
                        onValueChange = { storeNameInput = it.take(80) },
                        label = { Text(stringResource(R.string.products_store_name_label)) },
                        supportingText = { Text(stringResource(R.string.products_store_name_supporting_text)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.product_code_image_preview),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp, max = 300.dp)
                                    .background(Color.White),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                stringResource(R.string.code_generation_failed),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val singleSellingDisplay = if (showBanglaDigits) toBanglaDigits(formatTaka(product.sellingPrice)) else formatTaka(product.sellingPrice)
                val singleMrpDisplay = if (product.mrpPrice > 0) {
                    val base = formatTaka(product.mrpPrice)
                    if (showBanglaDigits) toBanglaDigits(base) else base
                } else stringResource(R.string.products_price_tier_not_set)
                val singleWholesaleDisplay = if (product.wholesalePrice > 0) {
                    val base = formatTaka(product.wholesalePrice)
                    if (showBanglaDigits) toBanglaDigits(base) else base
                } else stringResource(R.string.products_price_tier_not_set)

                PriceTierSwitcherCard(
                    showPrice = showPrice,
                    onShowPriceChange = { showPrice = it },
                    selectedTier = priceTier,
                    onTierSelect = { priceTierName = it.name },
                    sellingPriceDisplay = singleSellingDisplay,
                    mrpPriceDisplay = singleMrpDisplay,
                    wholesalePriceDisplay = singleWholesaleDisplay,
                    showUnit = showUnit,
                    onShowUnitChange = { showUnit = it },
                    showBanglaDigits = showBanglaDigits,
                    onShowBanglaDigitsChange = { showBanglaDigits = it }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCodeNumber = !showCodeNumber }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = showCodeNumber, onCheckedChange = { showCodeNumber = it })
                        Text(
                            stringResource(R.string.show_code_number),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDateTracking = !showDateTracking }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = showDateTracking, onCheckedChange = { showDateTracking = it })
                        Text(stringResource(R.string.products_show_date_tracking), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                AnimatedVisibility(visible = showDateTracking) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.products_date_tracking_info_label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = mfgOrPkgType == "Mfg",
                                    onClick = { mfgOrPkgType = "Mfg" },
                                    label = { Text("Mfg", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Animated3DFactoryIcon() },
                                    modifier = Modifier.height(32.dp)
                                )
                                FilterChip(
                                    selected = mfgOrPkgType == "Pkg",
                                    onClick = { mfgOrPkgType = "Pkg" },
                                    label = { Text("Pkg", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Animated3DPackageIcon(fontSize = 14.sp) },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DateInputField(
                                label = if (mfgOrPkgType == "Pkg") stringResource(R.string.products_pkg_date_label) else stringResource(R.string.products_mfg_date_label),
                                value = mfgOrPkgDate,
                                onValueChange = { mfgOrPkgDate = it },
                                modifier = Modifier.weight(1f)
                            )
                            DateInputField(
                                label = stringResource(R.string.products_exp_date_label),
                                value = expDate,
                                onValueChange = { expDate = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.label_size), style = MaterialTheme.typography.labelLarge)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { labelSizeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            labelSize.displayName,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = labelSizeMenuExpanded,
                        onDismissRequest = { labelSizeMenuExpanded = false }
                    ) {
                        ProductCodeLabelSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text = {
                                    Text(size.displayName)
                                },
                                leadingIcon = if (size == labelSize) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = {
                                    labelSizeName = size.name
                                    labelSizeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = labelSize == ProductCodeLabelSize.CUSTOM) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customWidthInput,
                            onValueChange = { value ->
                                if (value.matches(Regex("\\d{0,3}(\\.\\d?)?"))) customWidthInput = value
                            },
                            label = { Text(stringResource(R.string.products_width_mm_label)) },
                            supportingText = { Text(stringResource(R.string.products_width_range_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeightInput,
                            onValueChange = { value ->
                                if (value.matches(Regex("\\d{0,3}(\\.\\d?)?"))) customHeightInput = value
                            },
                            label = { Text(stringResource(R.string.products_height_mm_label)) },
                            supportingText = { Text(stringResource(R.string.products_height_range_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.products_element_graphic_sizes), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                LabelTextSizeControl(
                    label = stringResource(R.string.products_label_product_name),
                    scale = productNameTextScale,
                    onScaleChange = { productNameTextScale = it }
                )
                LabelTextSizeControl(
                    label = stringResource(R.string.products_label_price),
                    scale = priceTextScale,
                    enabled = showPrice,
                    onScaleChange = { priceTextScale = it }
                )
                LabelTextSizeControl(
                    label = stringResource(R.string.products_label_code_number),
                    scale = codeNumberTextScale,
                    enabled = showCodeNumber,
                    onScaleChange = { codeNumberTextScale = it }
                )
                LabelTextSizeControl(
                    label = stringResource(R.string.products_label_store_name),
                    scale = storeNameTextScale,
                    enabled = showStoreName && storeNameInput.isNotBlank(),
                    onScaleChange = { storeNameTextScale = it }
                )
                LabelTextSizeControl(
                    label = stringResource(R.string.products_label_date_tracking_text),
                    scale = dateTrackingTextScale,
                    enabled = showDateTracking && (mfgOrPkgDate.isNotBlank() || expDate.isNotBlank()),
                    onScaleChange = { dateTrackingTextScale = it }
                )
                LabelTextSizeControl(
                    label = stringResource(R.string.products_label_barcode_graphic_height),
                    scale = barcodeGraphicScale,
                    enabled = true,
                    onScaleChange = { barcodeGraphicScale = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                if (bitmap != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    val savedUri = withContext(Dispatchers.IO) {
                                        saveProductCodeLabelToPictures(context, bitmap, product.name, codeType)
                                    }
                                    isSaving = false
                                    actionStatus = if (savedUri != null) {
                                        context.getString(R.string.code_image_saved)
                                    } else {
                                        context.getString(R.string.code_image_save_failed)
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save_png))
                        }
                        OutlinedIconButton(
                            onClick = {
                                scope.launch {
                                    val cached = withContext(Dispatchers.IO) {
                                        cacheProductCodeLabelPdf(
                                            context = context,
                                            bitmap = bitmap,
                                            productName = product.name,
                                            codeType = codeType,
                                            widthMm = selectedLabelWidthMm,
                                            heightMm = selectedLabelHeightMm
                                        )
                                    }
                                    if (cached != null) {
                                        val shared = shareProductCodeLabelPdf(
                                            activity ?: context,
                                            cached.second,
                                            product.name
                                        )
                                        if (!shared) {
                                            actionStatus = context.getString(R.string.code_image_share_failed)
                                        }
                                    } else {
                                        actionStatus = context.getString(R.string.code_image_share_failed)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_label_pdf))
                        }
                        OutlinedIconButton(
                            onClick = {
                                scope.launch {
                                    val cached = withContext(Dispatchers.IO) {
                                        cacheProductCodeLabelPdf(
                                            context = context,
                                            bitmap = bitmap,
                                            productName = product.name,
                                            codeType = codeType,
                                            widthMm = selectedLabelWidthMm,
                                            heightMm = selectedLabelHeightMm
                                        )
                                    }
                                    val opened = cached?.let { (pdfFile, _) ->
                                        printPdfDocument(
                                            context = activity ?: context,
                                            pdfFile = pdfFile,
                                            jobName = "${product.name} label",
                                            widthMm = selectedLabelWidthMm,
                                            heightMm = selectedLabelHeightMm
                                        )
                                    } ?: false
                                    if (!opened) {
                                        actionStatus = context.getString(R.string.code_image_print_failed)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Print, contentDescription = stringResource(R.string.print_label))
                        }
                    }
                }

                actionStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        status,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (status == context.getString(R.string.code_image_saved)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelTextSizeControl(
    label: String,
    scale: Float,
    enabled: Boolean = true,
    onScaleChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { onScaleChange((scale - 0.1f).coerceAtLeast(0.6f)) },
            enabled = enabled && scale > 0.6f
        ) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.products_decrease_text_size_cd, label))
        }
        Text(
            text = "${(scale * 100).toInt()}%",
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge
        )
        IconButton(
            onClick = { onScaleChange((scale + 0.1f).coerceAtMost(1.8f)) },
            enabled = enabled && scale < 1.8f
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.products_increase_text_size_cd, label))
        }
    }
}

@Composable
fun EnlargedImageDialog(
    imageUri: String?,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title.ifBlank { stringResource(R.string.products_product_image_label) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = 420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    UriImage(
                        uriString = imageUri,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.products_enlarged_image_view),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAuditHistorySheet(
    adjustments: List<InventoryAdjustment>,
    allProducts: List<Product> = emptyList(),
    onAdjustProductStock: (Product) -> Unit = {},
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedReasonFilter by remember { mutableStateOf("All") }
    var showProductPicker by remember { mutableStateOf(false) }
    val reasons = listOf("All", "Restock", "Damage", "Expired", "Discrepancy", "Sample", "Store Use", "Internal Use")

    val filtered = remember(adjustments, searchQuery, selectedReasonFilter) {
        adjustments.filter { adj ->
            val matchQuery = searchQuery.isBlank() ||
                adj.productName.contains(searchQuery, ignoreCase = true) ||
                adj.productBarcode.contains(searchQuery, ignoreCase = true) ||
                (adj.note?.contains(searchQuery, ignoreCase = true) == true)
            val matchReason = selectedReasonFilter == "All" || adj.reason.equals(selectedReasonFilter, ignoreCase = true)
            matchQuery && matchReason
        }.sortedByDescending { it.timestamp }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text("📋", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(R.string.products_stock_audit_logs_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.products_total_audit_records, adjustments.size),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showProductPicker = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("📦", fontSize = 13.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.products_plus_adjust_button), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description), modifier = Modifier.size(20.dp))
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.products_search_audit_placeholder), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                reasons.forEach { reason ->
                    FilterChip(
                        selected = selectedReasonFilter == reason,
                        onClick = { selectedReasonFilter = reason },
                        label = { Text(localizedAdjustmentReasonLabel(reason), fontSize = 11.5.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📋", fontSize = 36.sp)
                    Text(
                        text = if (adjustments.isEmpty()) stringResource(R.string.products_no_adjustments_recorded) else stringResource(R.string.products_no_matching_adjustments),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.products_audit_log_description),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { showProductPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Text("📦", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.products_record_first_stock_adjustment), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { adj ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (badgeBg, badgeFg) = when (adj.reason.lowercase().trim()) {
                                        "damage" -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
                                        "expired" -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
                                        "restock" -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                                        "sample" -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
                                        "store use", "internal use", "internal" -> Color(0xFFE0E7FF) to Color(0xFF3730A3)
                                        "discrepancy" -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                                        else -> Color(0xFFF1F5F9) to Color(0xFF475569)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = badgeBg
                                    ) {
                                        Text(
                                            text = localizedAdjustmentReasonLabel(adj.reason).uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = badgeFg,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    val dateStr = remember(adj.timestamp) {
                                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(adj.timestamp))
                                    }
                                    Text(dateStr, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(adj.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(R.string.products_barcode_colon, adj.productBarcode), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val prevStr = if (adj.previousStock % 1.0 == 0.0) adj.previousStock.toLong().toString() else String.format(Locale.US, "%.2f", adj.previousStock).trimEnd('0').trimEnd('.')
                                        val newStr = if (adj.newStock % 1.0 == 0.0) adj.newStock.toLong().toString() else String.format(Locale.US, "%.2f", adj.newStock).trimEnd('0').trimEnd('.')
                                        val delta = adj.difference
                                        val deltaValStr = if (delta % 1.0 == 0.0) delta.toLong().toString() else String.format(Locale.US, "%.2f", delta).trimEnd('0').trimEnd('.')
                                        val deltaStr = if (delta > 0) "+$deltaValStr" else deltaValStr

                                        Text(
                                            text = "$prevStr ➔ $newStr",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "($deltaStr)",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (delta >= 0) Color(0xFF059669) else Color(0xFFE11D48)
                                        )
                                    }
                                }

                                if (!adj.note.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = stringResource(R.string.products_note_colon, adj.note ?: ""),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showProductPicker) {
            ProductPickerForAdjustmentDialog(
                products = allProducts,
                onSelectProduct = { prod ->
                    showProductPicker = false
                    onAdjustProductStock(prod)
                },
                onDismiss = { showProductPicker = false }
            )
        }
    }
}

@Composable
private fun ProductPickerForAdjustmentDialog(
    products: List<Product>,
    onSelectProduct: (Product) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filteredProducts = remember(products, search) {
        if (search.isBlank()) products
        else products.filter {
            it.name.contains(search, ignoreCase = true) ||
            it.barcode.contains(search, ignoreCase = true) ||
            it.category.contains(search, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📦", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.products_select_product_to_adjust), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(stringResource(R.string.products_choose_product_to_adjust_stock), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description))
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(R.string.products_search_product_or_barcode)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.products_no_matching_products_found), color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts, key = { it.barcode }) { prod ->
                            Surface(
                                onClick = { onSelectProduct(prod) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("${prod.barcode} • ${prod.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    val sStr = if (prod.stockQty % 1.0 == 0.0) prod.stockQty.toLong().toString() else String.format(Locale.US, "%.2f", prod.stockQty).trimEnd('0').trimEnd('.')
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.products_stock_pill_qty_unit, sStr, prod.unit),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
}

/**
 * Manage Categories Dialog allowing adding, editing (with full ✏️ Edit mode), deleting, and resetting categories.
 */
@Composable
fun ManageCategoriesDialog(
    categories: List<CategoryItem>,
    onDismiss: () -> Unit,
    onSave: (List<CategoryItem>) -> Unit,
    onReset: () -> Unit
) {
    var localCategories by remember(categories) { mutableStateOf(categories) }
    var editingCategory by remember { mutableStateOf<CategoryItem?>(null) }
    var iconInput by remember { mutableStateOf("🏷️") }
    var nameEnInput by remember { mutableStateOf("") }
    var nameBnInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    val startEditing = { item: CategoryItem ->
        editingCategory = item
        iconInput = item.icon
        nameEnInput = item.nameEn
        nameBnInput = item.nameBn
    }

    val cancelEditing = {
        editingCategory = null
        iconInput = "🏷️"
        nameEnInput = ""
        nameBnInput = ""
    }

    val handleSubmit = {
        val trimEn = nameEnInput.trim()
        val trimBn = nameBnInput.trim()
        val trimIcon = iconInput.trim().ifBlank { "🏷️" }

        if (trimEn.isBlank()) {
            Toast.makeText(context, context.getString(R.string.products_enter_category_name_warning), Toast.LENGTH_SHORT).show()
        } else {
            val currentEditing = editingCategory
            if (currentEditing != null) {
                // Update existing category
                localCategories = localCategories.map {
                    if (it.id == currentEditing.id) {
                        it.copy(icon = trimIcon, nameEn = trimEn, nameBn = if (trimBn.isNotBlank()) trimBn else trimEn)
                    } else it
                }
                cancelEditing()
            } else {
                // Add new category
                val newItem = CategoryItem(
                    icon = trimIcon,
                    nameEn = trimEn,
                    nameBn = if (trimBn.isNotBlank()) trimBn else trimEn
                )
                localCategories = localCategories + newItem
                nameEnInput = ""
                nameBnInput = ""
                iconInput = "🏷️"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Gradient Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF4F46E5), Color(0xFF4338CA))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚙️", fontSize = 16.sp)
                            Text(
                                text = stringResource(R.string.products_manage_categories_title),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.20f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Body content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Add / Edit Card Box
                    val isEditing = editingCategory != null
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isEditing) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (isEditing) Color(0xFF93C5FD) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isEditing) stringResource(R.string.products_edit_category_title, editingCategory?.nameEn ?: "") else stringResource(R.string.products_add_new_category_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isEditing) Color(0xFF1D4ED8) else Color(0xFF1E293B)
                            )

                            // Row 1: Icon Emoji Box + English Name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = iconInput,
                                    onValueChange = { iconInput = it.take(4) },
                                    placeholder = { Text("🏷️", fontSize = 12.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 14.sp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(52.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedBorderColor = Color(0xFF4F46E5)
                                    )
                                )

                                OutlinedTextField(
                                    value = nameEnInput,
                                    onValueChange = { nameEnInput = it },
                                    placeholder = { Text(stringResource(R.string.products_category_name_en_placeholder), fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedBorderColor = Color(0xFF4F46E5)
                                    )
                                )
                            }

                            // Row 2: Bangla Name + Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = nameBnInput,
                                    onValueChange = { nameBnInput = it },
                                    placeholder = { Text(stringResource(R.string.products_category_name_bn_placeholder), fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedBorderColor = Color(0xFF4F46E5)
                                    )
                                )

                                if (isEditing) {
                                    OutlinedButton(
                                        onClick = { cancelEditing() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text(stringResource(R.string.products_cancel_action), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                    }
                                }

                                Button(
                                    onClick = { handleSubmit() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isEditing) Color(0xFF2563EB) else Color(0xFF4F46E5)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text(
                                        text = if (isEditing) stringResource(R.string.products_update_action) else stringResource(R.string.products_plus_add_action),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Section Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.products_all_categories_tap_edit, localCategories.size),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    // Categories List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(localCategories, key = { it.id }) { catItem ->
                            val isItemBeingEdited = editingCategory?.id == catItem.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isItemBeingEdited) Color(0xFFEEF2FF) else Color.White,
                                border = BorderStroke(
                                    1.dp,
                                    if (isItemBeingEdited) Color(0xFF6366F1) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { startEditing(catItem) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(catItem.icon, fontSize = 16.sp)
                                        Column {
                                            Text(
                                                catItem.nameEn,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            if (catItem.nameBn.isNotBlank()) {
                                                Text(
                                                    catItem.nameBn,
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Edit Button
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                            modifier = Modifier.clickable { startEditing(catItem) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text("✏️", fontSize = 10.sp)
                                                Text(
                                                    stringResource(R.string.products_edit_action),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4F46E5)
                                                )
                                            }
                                        }

                                        // Delete Button
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF2F2),
                                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                            modifier = Modifier.clickable {
                                                if (localCategories.size <= 1) {
                                                    Toast.makeText(context, context.getString(R.string.products_keep_at_least_one_category_warning), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    localCategories = localCategories.filter { it.id != catItem.id }
                                                    if (editingCategory?.id == catItem.id) {
                                                        cancelEditing()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                "🗑️",
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Bar
                Surface(
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                localCategories = CategoryItem.defaultCategories()
                                cancelEditing()
                                onReset()
                            }
                        ) {
                            Text(
                                stringResource(R.string.products_reset_defaults),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }

                        Button(
                            onClick = {
                                onSave(localCategories)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.products_save_apply_action),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
