@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package com.example.techyfipos.ui.screens

import com.example.techyfipos.ui.dialogs.*
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import android.net.Uri
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import com.example.techyfipos.utils.DateFormatters
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
import androidx.compose.ui.graphics.luminance
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
import com.example.techyfipos.theme.extendedColors
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
import androidx.compose.ui.res.stringArrayResource
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
import com.example.techyfipos.utils.isAppBangla
import com.example.techyfipos.utils.beepAndVibrate
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.formatMoney
import com.example.techyfipos.utils.getLocalizedRepositoryErrorMessage
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
// The underlying stored/compared value is left untouched - only the Text() shown to the user is localized.
@Composable
internal fun localizedAdjustmentReasonLabel(reason: String): String = when (reason) {
    "All" -> stringResource(R.string.products_reason_all)
    "Restock" -> stringResource(R.string.products_reason_restock)
    "Damage" -> stringResource(R.string.products_reason_damage)
    "Expired" -> stringResource(R.string.products_reason_expired)
    "Discrepancy" -> stringResource(R.string.products_reason_discrepancy)
    "Sample" -> stringResource(R.string.products_reason_sample)
    "Store Use" -> stringResource(R.string.products_reason_store_use)
    "Internal Use" -> stringResource(R.string.products_reason_internal_use)
    "Import" -> stringResource(R.string.products_reason_import)
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
    isTablet: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    onBack: () -> Unit,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchQuery by productsViewModel.searchQuery.collectAsStateWithLifecycle()
    val productsList by productsViewModel.products.collectAsStateWithLifecycle()
    val allProductsList by productsViewModel.allProducts.collectAsStateWithLifecycle()
    val isProductsLoaded by productsViewModel.isProductsLoaded.collectAsStateWithLifecycle()
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

    val nowTimestamp by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 1)
            }
            val millisUntilMidnight = (cal.timeInMillis - now).coerceAtLeast(1000L)
            kotlinx.coroutines.delay(millisUntilMidnight.coerceAtMost(3600_000L))
        }
    }
    val in30DaysTimestamp = remember(nowTimestamp) { nowTimestamp + (30L * 24 * 60 * 60 * 1000) }
    val dateTrackedCount = remember(allProductsList) {
        allProductsList.count { !it.expiryDate.isNullOrBlank() || !it.mfgOrPkgDate.isNullOrBlank() }
    }
    val expiringSoonCount = remember(allProductsList, nowTimestamp) {
        allProductsList.count { p ->
            if (!p.expiryDate.isNullOrBlank()) {
                val exp = parseExpiryDateToMillis(p.expiryDate)
                exp != null && exp > nowTimestamp && exp <= in30DaysTimestamp
            } else false
        }
    }
    val expiredCount = remember(allProductsList, nowTimestamp) {
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
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
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
                            Toast.makeText(context, context.getString(R.string.products_export_failed_storage), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.products_export_failed_open_file), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProductsScreen", "Excel export failed", e)
                    Toast.makeText(context, context.getString(R.string.products_export_error, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var isImporting by remember { mutableStateOf(false) }
    var importFailureResult by remember { mutableStateOf<com.example.techyfipos.utils.ExcelHelper.ImportResult?>(null) }

    val importExcelLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (!canEdit) {
                Toast.makeText(context, context.getString(R.string.products_editing_owner_only), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isImporting = true
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val result = productsViewModel.importProductsFromExcel(inputStream)
                        val msg = if (result.failCount > 0) {
                            context.getString(R.string.products_import_partial_failed, result.importedProducts.size, result.failCount)
                        } else {
                            context.getString(R.string.products_import_success, result.importedProducts.size)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (result.failCount > 0) {
                            importFailureResult = result
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorMsg = e.localizedMessage ?: context.getString(R.string.products_import_parse_error)
                    Toast.makeText(context, context.getString(R.string.products_import_error, errorMsg), Toast.LENGTH_LONG).show()
                } finally {
                    isImporting = false
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
    val activeFilterCount = (if (selectedCategory != null) 1 else 0) +
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
        selectedCategory,
        minimumPrice,
        maximumPrice,
        sortOption,
        canViewCosts,
        nowTimestamp
    ) {
        val minPrice = minimumPrice.toDoubleOrNull()
        val maxPrice = maximumPrice.toDoubleOrNull()
        val now = nowTimestamp
        val in30Days = now + (30L * 24 * 60 * 60 * 1000)

        productsList
            .asSequence()
            .filter { product ->
                selectedCategory == null || product.category.trim() == selectedCategory
            }
            .filter { product ->
                if (!highMarginOnly || !canViewCosts) true
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
        if (!prefilledBarcode.isNullOrBlank() && canEdit) {
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
                        if (stockCheckProduct != null) {
                            if (stockCheckProduct?.barcode != trimmedCode) {
                                Toast.makeText(context, context.getString(R.string.products_stock_check_in_progress), Toast.LENGTH_SHORT).show()
                            }
                        } else if (product != null) {
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
                    message = context.getString(R.string.products_out_of_stock_snackbar, product.name),
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
                            metricBadge = if (allProductsList.isNotEmpty()) pluralStringResource(R.plurals.products_sku_count, allProductsList.size, allProductsList.size) else null,
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
                                .size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showInventoryAlertsModal = true },
                                modifier = Modifier.size(48.dp)
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
                                        .size(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val countText = if (allAlerts.totalCount > 99) "99+" else allAlerts.totalCount.toString()
                                    Text(
                                        text = countText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        // 3D Indigo Cart Capsule with Tap & Add Animations
                        Surface(
                            onClick = {
                                if (cartUnitCount > 0) {
                                    cartAnimKey = System.currentTimeMillis()
                                }
                                onNavigateToCart()
                            },
                            interactionSource = cartInteractionSource,
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF4F46E5),
                            contentColor = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(48.dp)
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
                                modifier = Modifier.height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = stringResource(R.string.products_excel_tools),
                                        tint = Color(0xFF059669),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.products_excel_tools),
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
                                    leadingIcon = { Icon(Icons.Default.FileUpload, null, tint = if (canEdit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                    enabled = !isImporting,
                                    onClick = {
                                        showExcelMenu = false
                                        if (!canEdit) {
                                            Toast.makeText(context, context.getString(R.string.products_editing_owner_only), Toast.LENGTH_SHORT).show()
                                            return@DropdownMenuItem
                                        }
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
                                        val cleanDate = DateFormatters.formatExportDate()
                                        exportExcelLauncher.launch("products-$cleanDate.xlsx")
                                    }
                                )
                            }
                        }

                        // 2. Batch Select Mode Chip
                        if (canEdit || canDelete) {
                            Surface(
                                onClick = {
                                    batchSelectMode = !batchSelectMode
                                    if (!batchSelectMode) selectedBarcodes.clear()
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = if (batchSelectMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (batchSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (batchSelectMode) Icons.Default.CheckCircle else Icons.Default.Checklist,
                                        contentDescription = stringResource(R.string.products_batch_operations_cd),
                                        tint = if (batchSelectMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (batchSelectMode) stringResource(R.string.products_exit_batch) else stringResource(R.string.products_batch_select),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (batchSelectMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // 3. Stock Audit History Chip
                        Surface(
                            onClick = { showStockAuditHistoryModal = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Realistic3DClipboardDuesIcon(size = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.products_stock_audit),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 4. Batch Print Labels Chip
                        if (canPrintLabels) {
                            Surface(
                                onClick = {
                                    if (canPrintLabels) {
                                        showBatchPrintDialog = true
                                    } else {
                                        android.widget.Toast.makeText(context, context.getString(R.string.perm_inventory_print_labels_desc), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = stringResource(R.string.products_print_labels),
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.products_print_labels),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    if (isImporting) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
            }
        },
        floatingActionButton = {
            if (!isTablet) {
                // 🟢 3D Emerald Floating Action Button (Compact circular FAB on phones to preserve product card action dock visibility)
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF059669),
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.products_add_product_button),
                        modifier = Modifier.size(26.dp),
                        tint = Color.White
                    )
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
                                    Text(stringResource(R.string.products_margin_label), fontSize = 11.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold)
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
                                    Text(stringResource(R.string.products_purchase_cost_label), fontSize = 11.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                                    Text(if (canViewCosts) formatTaka(totalPurchaseValue) else "••••••", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text(stringResource(R.string.products_in_stock_units_label), fontSize = 11.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
                                    val uStr = if (totalStockUnits % 1.0 == 0.0) totalStockUnits.toLong().toString() else String.format(Locale.US, "%.1f", totalStockUnits)
                                    Text(stringResource(R.string.products_units_pcs, uStr), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6EE7B7))
                                }
                                Column {
                                    Text(stringResource(R.string.products_stock_alerts_label), fontSize = 11.sp, color = Color(0xFFA5B4FC), fontWeight = FontWeight.Bold)
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
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = if (allVisibleSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = stringResource(R.string.products_select_all_cd),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.products_selected_count, selectedBarcodes.size),
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
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(stringResource(R.string.products_batch_price_pct), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { showBulkCategoryDialog = true },
                                enabled = selectedBarcodes.isNotEmpty() && canEdit,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(stringResource(R.string.products_batch_category), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (canDelete) {
                                Button(
                                    onClick = { showBulkDeleteDialog = true },
                                    enabled = selectedBarcodes.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text(stringResource(R.string.products_batch_delete), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                                }
                            }

                            IconButton(
                                onClick = {
                                    batchSelectMode = false
                                    selectedBarcodes.clear()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.products_exit_selection_cd),
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
                PosSearchBox(
                    query = searchQuery,
                    onQueryChange = { productsViewModel.setSearchQuery(it) },
                    placeholderText = stringResource(R.string.products_search_placeholder),
                    accentColor = PosSectionColors.Products,
                    modifier = Modifier.weight(1f),
                    trailingContent = {
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Realistic3DHardwareBarcodeScannerIcon(size = 20.dp, animate = true)
                            }
                        }
                    }
                )

                // Native Tune/Filter Button (Opens Filter Bottom Sheet)
                val hasActiveFilters = selectedCategory != null ||
                        stockFilter != ProductStockFilter.ALL ||
                        highMarginOnly ||
                        minimumPrice.isNotEmpty() ||
                        maximumPrice.isNotEmpty() ||
                        dateFilter != ProductDateFilter.ALL ||
                        sortOption != ProductSortOption.NAME_ASC

                Surface(
                    onClick = { showFilters = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasActiveFilters) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.products_filter_cd),
                            modifier = Modifier.size(20.dp),
                            tint = if (hasActiveFilters) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { viewModeName = ProductViewMode.GRID.name },
                            shape = RoundedCornerShape(8.dp),
                            color = if (viewMode == ProductViewMode.GRID) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = if (viewMode == ProductViewMode.GRID) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.products_grid_view_cd),
                                    modifier = Modifier.size(19.dp),
                                    tint = if (viewMode == ProductViewMode.GRID) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            onClick = { viewModeName = ProductViewMode.LIST.name },
                            shape = RoundedCornerShape(8.dp),
                            color = if (viewMode == ProductViewMode.LIST) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = if (viewMode == ProductViewMode.LIST) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ViewList,
                                    contentDescription = stringResource(R.string.products_list_view_cd),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (viewMode == ProductViewMode.LIST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                        modifier = Modifier.height(48.dp)
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
                            text = stringResource(R.string.products_filter_all_skus),
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
                            text = stringResource(R.string.products_filter_low_stock_chip),
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
                            text = stringResource(R.string.products_filter_out_of_stock_chip),
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
                if (canViewCosts) {
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
                                text = stringResource(R.string.products_filter_high_margin_chip),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                        }
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
                val isAllCats = selectedCategory == null
                Surface(
                    onClick = { selectedCategory = null },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isAllCats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isAllCats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = stringResource(R.string.products_filter_all_categories),
                        fontSize = 12.sp,
                        fontWeight = if (isAllCats) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllCats) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                // 6. Dynamic Categories with Emojis & Counts
                availableCategories.forEach { category ->
                    val isCatActive = selectedCategory == category
                    val emoji = getCategoryEmoji(category)
                    val displayCategory = getLocalizedCategoryName(category)
                    Surface(
                        onClick = {
                            selectedCategory = if (isCatActive) null else category
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
                                text = "$emoji $displayCategory",
                                fontSize = 12.sp,
                                fontWeight = if (isCatActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCatActive) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Active Filter Indicator Strip (Visible only when filters are active)
            val hasActiveFilterBar = selectedCategory != null ||
                    stockFilter != ProductStockFilter.ALL ||
                    dateFilter != ProductDateFilter.ALL ||
                    sortOption != ProductSortOption.NAME_ASC ||
                    highMarginOnly ||
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
                    val stockLowChipLabel = stringResource(R.string.products_filter_low_stock_chip)
                    val stockOutChipLabel = stringResource(R.string.products_filter_out_of_stock_chip)
                    val inStockLabel = stringResource(R.string.in_stock)
                    val filterSummary = buildList {
                        if (selectedCategory != null) add(selectedCategory!!)
                        if (stockFilter != ProductStockFilter.ALL) add(when (stockFilter) {
                            ProductStockFilter.AVAILABLE -> inStockLabel
                            ProductStockFilter.LOW -> stockLowChipLabel
                            ProductStockFilter.OUT -> stockOutChipLabel
                            ProductStockFilter.ALL -> ""
                        })
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
                        if (highMarginOnly) add(stringResource(R.string.products_filter_high_margin_chip))
                        if (minimumPrice.isNotEmpty() || maximumPrice.isNotEmpty()) {
                            val minStr = minimumPrice.toDoubleOrNull()?.let { formatMoney(it) } ?: minimumPrice
                            val maxStr = maximumPrice.toDoubleOrNull()?.let { formatMoney(it) } ?: maximumPrice
                            add("$minStr-$maxStr")
                        }
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
                            selectedCategory = null
                            stockFilterName = ProductStockFilter.ALL.name
                            dateFilterName = ProductDateFilter.ALL.name
                            sortOptionName = ProductSortOption.NAME_ASC.name
                            highMarginOnly = false
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
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(stringResource(R.string.products_view_cart), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Products Content Area
            if (!isProductsLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            } else if (allProductsList.isEmpty()) {
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
                                selectedCategory = null
                                stockFilterName = ProductStockFilter.ALL.name
                                dateFilterName = ProductDateFilter.ALL.name
                                sortOptionName = ProductSortOption.NAME_ASC.name
                                highMarginOnly = false
                                minimumPrice = ""
                                maximumPrice = ""
                            }
                        ) {
                            Text(stringResource(R.string.products_reset_filters))
                        }
                    }
                }
            } else {
                key(selectedCategory) {
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
                                        if (canEdit || canDelete) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!batchSelectMode) {
                                                batchSelectMode = true
                                                selectedBarcodes.clear()
                                                selectedBarcodes.add(product.barcode)
                                            } else {
                                                if (isSelected) selectedBarcodes.remove(product.barcode)
                                                else selectedBarcodes.add(product.barcode)
                                            }
                                        }
                                    },
                                    onAdjustStock = {
                                        if (canAdjustStock) {
                                            stockCheckProduct = product
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.perm_inventory_adjust_stock_desc), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onEdit = { requestEditProduct(product) },
                                    onGenerateCode = {
                                        if (canPrintLabels) {
                                            productForCodeDialog = product
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.perm_inventory_print_labels_desc), android.widget.Toast.LENGTH_SHORT).show()
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
                                        if (canEdit || canDelete) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!batchSelectMode) {
                                                batchSelectMode = true
                                                selectedBarcodes.clear()
                                                selectedBarcodes.add(product.barcode)
                                            } else {
                                                if (isSelected) selectedBarcodes.remove(product.barcode)
                                                else selectedBarcodes.add(product.barcode)
                                            }
                                        }
                                    },
                                    onAdjustStock = {
                                        if (canAdjustStock) {
                                            stockCheckProduct = product
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.perm_inventory_adjust_stock_desc), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onEdit = { requestEditProduct(product) },
                                    onGenerateCode = {
                                        if (canPrintLabels) {
                                            productForCodeDialog = product
                                        } else {
                                            android.widget.Toast.makeText(context, context.getString(R.string.perm_inventory_print_labels_desc), android.widget.Toast.LENGTH_SHORT).show()
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
    }

    if (showFilters) {
        val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val hasActiveFilters = selectedCategory != null ||
                stockFilter != ProductStockFilter.ALL ||
                dateFilter != ProductDateFilter.ALL ||
                sortOption != ProductSortOption.NAME_ASC ||
                minimumPrice.isNotBlank() ||
                maximumPrice.isNotBlank() ||
                highMarginOnly

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
                                selectedCategory = null
                                stockFilterName = ProductStockFilter.ALL.name
                                dateFilterName = ProductDateFilter.ALL.name
                                sortOptionName = ProductSortOption.NAME_ASC.name
                                minimumPrice = ""
                                maximumPrice = ""
                                highMarginOnly = false
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
                        val isAllSelected = selectedCategory == null
                        Surface(
                            onClick = { selectedCategory = null },
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
                            val isSelected = selectedCategory == category
                            val count = allProductsList.count { it.category.trim() == category }
                            Surface(
                                onClick = {
                                    selectedCategory = if (isSelected) null else category
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
                        onValueChange = { minimumPrice = toEnglishDigits(it).filter { char -> char.isDigit() || char == '.' } },
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
                        onValueChange = { maximumPrice = toEnglishDigits(it).filter { char -> char.isDigit() || char == '.' } },
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
                            selectedCategory = null
                            stockFilterName = ProductStockFilter.ALL.name
                            dateFilterName = ProductDateFilter.ALL.name
                            sortOptionName = ProductSortOption.NAME_ASC.name
                            minimumPrice = ""
                            maximumPrice = ""
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp)
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
                            .height(48.dp)
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
            properties = DialogProperties(dismissOnClickOutside = false),
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
            existingBarcodes = allProductsList.map { it.barcode }.toSet(),
            initialBarcode = toEnglishDigits(scannedNewBarcodeForDialog ?: prefilledBarcode ?: ""),
            canAdjustStock = canAdjustStock,
            onDismiss = {
                showAddDialog = false
                scannedNewBarcodeForDialog = null
            },
            onSave = { name, barcode, cat, pPrice, sPrice, mrp, disc, qty, reorder, imageUri, mfgType, mfgDate, expDate, unit, wPrice, reason, note, onResult ->
                scope.launch {
                    val p = Product(
                        barcode = barcode,
                        name = name,
                        category = cat,
                        purchasePrice = pPrice,
                        sellingPrice = sPrice,
                        stockQty = if (canAdjustStock) qty else 0.0,
                        reorderLevel = reorder,
                        imageUri = imageUri,
                        mfgOrPkgType = mfgType,
                        mfgOrPkgDate = mfgDate,
                        expiryDate = expDate,
                        unit = unit,
                        wholesalePrice = wPrice,
                        mrpPrice = mrp,
                        discountedPrice = disc,
                        createdAt = System.currentTimeMillis()
                    )
                    val success = productsViewModel.addProduct(p)
                    if (success) {
                        if (canAdjustStock && qty > 0.0) {
                            productsViewModel.recordInventoryAdjustment(
                                productBarcode = p.barcode,
                                productName = p.name,
                                previousStock = 0.0,
                                newStock = qty,
                                difference = qty,
                                reason = reason ?: "Restock",
                                note = note ?: context.getString(R.string.products_initial_stock_note)
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
                        onResult?.invoke(false)
                        Toast.makeText(
                            context,
                            context.getString(R.string.products_barcode_already_exists),
                            Toast.LENGTH_SHORT
                        ).show()
                        snackbarHostState.showSnackbar(context.getString(R.string.products_barcode_already_exists))
                    }
                }
            },
            onCategoryRenamed = { old, new -> productsViewModel.renameCategory(old, new) },
            checkBarcodeExists = { code -> productsViewModel.productExistsByBarcode(code) }
        )
    }

    // Edit Product Dialog
    if (selectedProductForEdit != null) {
        val prod = selectedProductForEdit!!
        AddEditProductDialog(
            isEdit = true,
            canAdjustStock = canAdjustStock,
            existingBarcodes = allProductsList.map { it.barcode }.toSet(),
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
            onSave = { name, barcode, cat, pPrice, sPrice, mrp, disc, qty, reorder, imageUri, mfgType, mfgDate, expDate, unit, wPrice, reason, note, onResult ->
                val prevStock = prod.stockQty
                val diff = qty - prevStock
                scope.launch {
                    val currentDb = productsViewModel.getProductByBarcode(prod.barcode)
                    val liveStock = if (!canAdjustStock) (currentDb?.stockQty ?: prod.stockQty) else qty
                    val p = Product(
                        barcode = barcode,
                        name = name,
                        category = cat,
                        purchasePrice = pPrice,
                        sellingPrice = sPrice,
                        stockQty = liveStock,
                        reorderLevel = reorder,
                        imageUri = imageUri,
                        mfgOrPkgType = mfgType,
                        mfgOrPkgDate = mfgDate,
                        expiryDate = expDate,
                        unit = unit,
                        wholesalePrice = wPrice,
                        mrpPrice = mrp,
                        discountedPrice = disc,
                        createdAt = prod.createdAt
                    )
                    val adjustment = if (canAdjustStock && kotlin.math.abs(diff) > 0.0001) {
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
                            onResult?.invoke(false)
                            val message = error.message ?: context.getString(R.string.products_unable_to_update)
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                        }
                }
            },
            onCategoryRenamed = { old, new -> productsViewModel.renameCategory(old, new) },
            checkBarcodeExists = { code -> productsViewModel.productExistsByBarcode(code) }
        )
    }

    importFailureResult?.let { result ->
        AlertDialog(
            onDismissRequest = { importFailureResult = null },
            title = { Text(stringResource(R.string.import_failure_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        stringResource(R.string.import_failure_dialog_summary, result.importedProducts.size, result.failCount),
                        fontWeight = FontWeight.SemiBold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    result.failures.take(50).forEach { failure ->
                        Text(localizeImportError(context, failure), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importFailureResult = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (showInventoryAlertsModal) {
        InventoryAlertsCenterBottomSheet(
            alertsData = allAlerts,
            onEditProduct = { prod ->
                selectedProductForEdit = prod
            },
            onQuickAddStock = { prod, addedQty ->
                if (!canAdjustStock) {
                    Toast.makeText(context, context.getString(R.string.perm_inventory_adjust_stock_desc), Toast.LENGTH_SHORT).show()
                    return@InventoryAlertsCenterBottomSheet
                }
                val prevStock = prod.stockQty
                val newStock = prevStock + addedQty
                productsViewModel.updateProductWithAdjustment(
                    updatedProduct = prod.copy(stockQty = newStock),
                    previousStock = prevStock,
                    newStock = newStock,
                    difference = addedQty.toDouble(),
                    reason = "Restock",
                    note = context.getString(R.string.products_quick_restock_note),
                    onResult = { result ->
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.products_added_stock_toast, addedQty, prod.name), Toast.LENGTH_SHORT).show()
                        } else {
                            val msg = result.exceptionOrNull()?.message ?: context.getString(R.string.error_generic)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
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
                if (!canAdjustStock) {
                    Toast.makeText(context, context.getString(R.string.perm_inventory_adjust_stock_desc), Toast.LENGTH_SHORT).show()
                    stockCheckProduct = null
                    return@HardwareStockCheckDialog
                }
                val prevStock = product.stockQty
                val newStock = newQuantity.coerceAtLeast(0.0)
                val diff = newStock - prevStock
                val updated = product.copy(stockQty = newStock)
                stockCheckProduct = null
                if (kotlin.math.abs(diff) > 0.0001) {
                    productsViewModel.updateProductWithAdjustment(
                        updatedProduct = updated,
                        previousStock = prevStock,
                        newStock = newStock,
                        difference = diff,
                        reason = reason,
                        note = note,
                        onResult = { result ->
                            if (result.isSuccess) {
                                val rawStockStr = if (updated.stockQty % 1.0 == 0.0) updated.stockQty.toLong().toString() else String.format(Locale.US, "%.2f", updated.stockQty).trimEnd('0').trimEnd('.')
                                val sStr = if (isAppBangla()) toBanglaDigits(rawStockStr) else rawStockStr
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
                            } else {
                                val msg = com.example.techyfipos.utils.getLocalizedRepositoryErrorMessage(context, result.exceptionOrNull())
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } else {
                    val rawStockStr = if (updated.stockQty % 1.0 == 0.0) updated.stockQty.toLong().toString() else String.format(Locale.US, "%.2f", updated.stockQty).trimEnd('0').trimEnd('.')
                    val sStr = if (isAppBangla()) toBanglaDigits(rawStockStr) else rawStockStr
                    Toast.makeText(
                        context,
                        context.getString(R.string.products_stock_saved_toast, product.name, sStr, product.unit),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    stockNotFoundBarcode?.let { barcode ->
        val englishBarcode = toEnglishDigits(barcode)
        StockCheckNotFoundDialog(
            barcode = englishBarcode,
            onDismiss = { stockNotFoundBarcode = null },
            onAddNow = if (canEdit) {
                {
                    scannedNewBarcodeForDialog = englishBarcode
                    showAddDialog = true
                    stockNotFoundBarcode = null
                }
            } else null
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
        if (canPrintLabels) {
            BatchProductCodePrintDialog(
                products = allProductsList,
                initiallySelectedBarcodes = selectedBarcodes.toSet(),
                onDismiss = { showBatchPrintDialog = false }
            )
        } else {
            showBatchPrintDialog = false
        }
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
                        Toast.makeText(context, context.getString(R.string.products_bulk_price_updated, count), Toast.LENGTH_SHORT).show()
                        selectedBarcodes.clear()
                        batchSelectMode = false
                        showBulkPriceDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.products_bulk_price_failed, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, context.getString(R.string.products_bulk_category_updated, count), Toast.LENGTH_SHORT).show()
                        selectedBarcodes.clear()
                        batchSelectMode = false
                        showBulkCategoryDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.products_bulk_category_failed, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_LONG).show()
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
                        Toast.makeText(context, context.getString(R.string.products_bulk_deleted, count), Toast.LENGTH_SHORT).show()
                        selectedBarcodes.clear()
                        batchSelectMode = false
                        showBulkDeleteDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.products_bulk_delete_failed, getLocalizedRepositoryErrorMessage(context, e)), Toast.LENGTH_LONG).show()
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
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        val rawVal = valueText.toDoubleOrNull() ?: 0.0
        val factor = if (isIncrease) 1.0 else -1.0
        val finalVal = rawVal * factor
        val changeDesc = if (mode == 0) "${if (isIncrease) "+" else "-"}${rawVal}%" else "${if (isIncrease) "+" else "-"}৳${rawVal}"

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            properties = DialogProperties(dismissOnClickOutside = false),
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.bulk_price_confirm_title)) },
            text = {
                Text(stringResource(R.string.bulk_price_confirm_message, selectedCount, changeDesc))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        if (mode == 0) {
                            onApply(finalVal, null)
                        } else {
                            onApply(null, finalVal)
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(stringResource(R.string.bulk_price_dialog_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.bulk_price_adjust_desc, selectedCount),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text(stringResource(R.string.bulk_price_mode_percentage)) }
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text(stringResource(R.string.bulk_price_mode_fixed)) }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isIncrease,
                        onClick = { isIncrease = true },
                        label = { Text(stringResource(R.string.bulk_price_dir_increase)) }
                    )
                    FilterChip(
                        selected = !isIncrease,
                        onClick = { isIncrease = false },
                        label = { Text(stringResource(R.string.bulk_price_dir_decrease)) }
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
                    onValueChange = { valueText = com.example.techyfipos.utils.sanitizeNumericInput(it) },
                    label = { Text(if (mode == 0) stringResource(R.string.bulk_price_label_percentage) else stringResource(R.string.bulk_price_label_fixed)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showConfirmDialog = true
                },
                enabled = valueText.toDoubleOrNull() != null && (valueText.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text(stringResource(R.string.bulk_price_apply_button, selectedCount))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(stringResource(R.string.bulk_category_dialog_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.bulk_category_dialog_desc, selectedCount),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (existingCategories.isNotEmpty()) {
                    Text(stringResource(R.string.bulk_category_select_existing), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                    label = { Text(stringResource(R.string.bulk_category_new_chip)) }
                )

                if (isCustomCat) {
                    OutlinedTextField(
                        value = customCatText,
                        onValueChange = { customCatText = it },
                        label = { Text(stringResource(R.string.bulk_category_name_label)) },
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
                Text(stringResource(R.string.bulk_category_reassign_button, selectedCount))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(stringResource(R.string.bulk_delete_dialog_title, selectedCount), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(
                stringResource(R.string.bulk_delete_dialog_message, selectedCount),
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.bulk_delete_confirm_button, selectedCount), color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}




@Composable
fun StockCheckNotFoundDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onAddNow: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
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
            if (onAddNow != null) {
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
    val reasonsList = listOf("Restock", "Damage", "Expired", "Discrepancy", "Sample", "Store Use", "Internal Use", "Other")
    var selectedReason by remember(reasonsList) { mutableStateOf(reasonsList.first()) }
    var adjustmentNote by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
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
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = getLocalizedCategoryName(product.category),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Inventory Level & Health Status Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                stringResource(R.string.products_quantity_units, quantity.toString()),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
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
                                val filtered = com.example.techyfipos.utils.sanitizeNumericInput(input)
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

suspend fun copyUriToInternalStorage(context: android.content.Context, uri: Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val sampledBitmap = decodeSampledBitmapFromUri(context, uri.toString(), reqWidth = 600, reqHeight = 600) ?: return@withContext null
        val fileName = "product_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        file.outputStream().use { outputStream ->
            sampledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        }
        sampledBitmap.recycle()
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

private fun rotateBitmap(bitmap: android.graphics.Bitmap, orientation: Int): android.graphics.Bitmap {
    val matrix = android.graphics.Matrix()
    when (orientation) {
        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) {
        bitmap.recycle()
    }
    return rotated
}

private fun decodeSampledBitmapFromUri(context: android.content.Context, uriString: String, reqWidth: Int = 256, reqHeight: Int = 256): android.graphics.Bitmap? {
    try {
        val file = File(uriString)
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val orientation = try {
            if (file.exists()) {
                val exif = android.media.ExifInterface(file.absolutePath)
                exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            } else {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { s ->
                    android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            }
        } catch (_: Exception) {
            android.media.ExifInterface.ORIENTATION_NORMAL
        }

        val rawBitmap = if (file.exists()) {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
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
            if (is2 != null) {
                is2.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, options)
                }
            } else null
        }

        return if (rawBitmap != null) {
            rotateBitmap(rawBitmap, orientation)
        } else null
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
        modifier = modifier.height(48.dp)
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
    val rawStockStr = if (product.stockQty % 1.0 == 0.0) product.stockQty.toLong().toString() else String.format(Locale.US, "%.1f", product.stockQty)
    val sStr = if (isAppBangla()) toBanglaDigits(rawStockStr) else rawStockStr

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
                                text = getLocalizedCategoryName(product.category),
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.widthIn(min = 100.dp)
                ) {
                    Text(
                        text = formatTaka(product.sellingPrice),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF047857),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                    Text(
                        text = "/ ${product.unit.ifBlank { stringResource(R.string.unit_pcs_default) }}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
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
                        modifier = Modifier.height(48.dp)
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
                        modifier = Modifier.height(48.dp)
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
                                    text = getLocalizedCategoryName(product.category),
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
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatTaka(product.sellingPrice),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF047857),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                    Text(
                        text = "/ ${product.unit.ifBlank { stringResource(R.string.unit_pcs_default) }}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
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
                        modifier = Modifier.height(48.dp)
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
                        modifier = Modifier.height(48.dp)
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

fun getLocalizedCategoryName(category: String, isBn: Boolean = com.example.techyfipos.utils.isAppBangla()): String {
    if (!isBn || category.isBlank()) return category
    val trimmed = category.trim()
    val match = com.example.techyfipos.data.CategoryItem.defaultCategories().find {
        it.nameEn.equals(trimmed, ignoreCase = true)
    }
    return match?.nameBn?.takeIf { it.isNotBlank() } ?: category
}

fun getCategoryEmoji(category: String): String {
    val trimmed = category.trim()
    val categoryMatch = com.example.techyfipos.data.CategoryItem.defaultCategories().find {
        it.nameEn.equals(trimmed, ignoreCase = true) || it.nameBn.equals(trimmed, ignoreCase = true)
    }
    if (categoryMatch != null) return categoryMatch.icon
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val productImage = rememberUriImagePainter(product.imageUri)
    val isOut = product.stockQty <= 0.0
    val isLow = product.stockQty > 0.0 && product.stockQty <= product.reorderLevel
    val rawStockStr = if (product.stockQty % 1.0 == 0.0) product.stockQty.toLong().toString() else String.format(Locale.US, "%.1f", product.stockQty)
    val sStr = if (isAppBangla()) toBanglaDigits(rawStockStr) else rawStockStr

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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Barcode Capsule [#846656005286]
                Surface(
                    onClick = onGenerateCode,
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1.2f, fill = false)
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
                        text = getLocalizedCategoryName(product.category),
                        color = Color(0xFF4F46E5),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.18f else 0.25f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(vertical = 1.dp)
            )

            // ==========================================
            // 3. PRICE ROW: Prominent, clear, horizontal & zero-wrapping
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatTaka(product.sellingPrice),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF047857),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
                Text(
                    text = "/ ${product.unit.ifBlank { stringResource(R.string.unit_pcs_default) }}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
            }

            // ==========================================
            // 4. ACTION ROW: [🛒 Cart] + [📦 ± Stock] + [🏷️ Code] + [⋮ Menu]
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Realistic3DSupermarketCartIcon(size = 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.cart),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // 2. 3D ± Stock Button
                Surface(
                    onClick = onAdjustStock,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEF2FF),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier.height(48.dp)
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
                            color = Color(0xFF4F46E5),
                            maxLines = 1
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

private fun localizeImportError(context: Context, failure: String): String {
    val missingMatch = Regex("""Missing required columns: (.*)""").matchEntire(failure)
    if (missingMatch != null) {
        return context.getString(R.string.excel_import_err_missing_columns, missingMatch.groupValues[1])
    }
    val blankMatch = Regex("""Row (\d+): Barcode and Name cannot be blank\.""").matchEntire(failure)
    if (blankMatch != null) {
        val row = blankMatch.groupValues[1].toIntOrNull() ?: 1
        return context.getString(R.string.excel_import_err_row_blank, row)
    }
    val pPriceMatch = Regex("""Row (\d+): Invalid purchase price '(.*)'\.""").matchEntire(failure)
    if (pPriceMatch != null) {
        val row = pPriceMatch.groupValues[1].toIntOrNull() ?: 1
        return context.getString(R.string.excel_import_err_purchase_price, row, pPriceMatch.groupValues[2])
    }
    val sPriceMatch = Regex("""Row (\d+): Invalid selling price '(.*)'\. Must be zero or greater\.""").matchEntire(failure)
    if (sPriceMatch != null) {
        val row = sPriceMatch.groupValues[1].toIntOrNull() ?: 1
        return context.getString(R.string.excel_import_err_selling_price, row, sPriceMatch.groupValues[2])
    }
    val stockMatch = Regex("""Row (\d+): Invalid stock quantity '(.*)'\.""").matchEntire(failure)
    if (stockMatch != null) {
        val row = stockMatch.groupValues[1].toIntOrNull() ?: 1
        return context.getString(R.string.excel_import_err_stock_qty, row, stockMatch.groupValues[2])
    }
    val expiryMatch = Regex("""Row (\d+): Invalid expiry date '(.*)'\. Expected dd/MM/yyyy\.""").matchEntire(failure)
    if (expiryMatch != null) {
        val row = expiryMatch.groupValues[1].toIntOrNull() ?: 1
        return context.getString(R.string.excel_import_err_expiry_date, row, expiryMatch.groupValues[2])
    }
    return failure
}


