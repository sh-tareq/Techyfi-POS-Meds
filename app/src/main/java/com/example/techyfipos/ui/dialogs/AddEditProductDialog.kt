package com.example.techyfipos.ui.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.techyfipos.theme.extendedColors
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.CategoryItem
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.ui.screens.UriImage
import com.example.techyfipos.ui.screens.copyUriToInternalStorage
import com.example.techyfipos.ui.screens.localizedAdjustmentReasonLabel
import com.example.techyfipos.ui.screens.rememberUriImagePainter
import com.example.techyfipos.ui.components.*
import com.example.techyfipos.utils.HardwareBarcodeScanner
import com.example.techyfipos.utils.beepAndVibrate
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.toEnglishDigits
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductDialog(
    isEdit: Boolean = false,
    existingBarcodes: Set<String> = emptySet(),
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
    canAdjustStock: Boolean = true,
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
        adjustmentNote: String?,
        onResult: ((Boolean) -> Unit)?
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCategoryRenamed: suspend (oldName: String, newName: String) -> Unit = { _, _ -> },
    checkBarcodeExists: (suspend (String) -> Boolean)? = null
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var barcode by rememberSaveable { mutableStateOf(toEnglishDigits(initialBarcode)) }
    var showBarcodeScanModal by rememberSaveable { mutableStateOf(false) }

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
    var isSaving by remember { mutableStateOf(false) }
    var showCategoryManagerDialog by rememberSaveable { mutableStateOf(false) }
    var categoryDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var purchasePrice by rememberSaveable { mutableStateOf(if (initialPurchasePrice == 0.0) "" else initialPurchasePrice.toString()) }
    var mrpPrice by rememberSaveable {
        mutableStateOf(
            if (initialMrpPrice > 0.0) initialMrpPrice.toString()
            else if (initialSellingPrice > 0.0) initialSellingPrice.toString()
            else ""
        )
    }
    var discountedPrice by rememberSaveable {
        mutableStateOf(
            if (initialDiscountedPrice > 0.0) initialDiscountedPrice.toString()
            else if (initialSellingPrice > 0.0) initialSellingPrice.toString()
            else ""
        )
    }
    var wholesalePrice by rememberSaveable { mutableStateOf(if (initialWholesalePrice == 0.0) "" else initialWholesalePrice.toString()) }
    var stockQty by rememberSaveable {
        mutableStateOf(
            if (initialStock == 0.0) ""
            else if (initialStock % 1.0 == 0.0) initialStock.toLong().toString()
            else String.format(Locale.US, "%.2f", initialStock).trimEnd('0').trimEnd('.')
        )
    }
    var reorderLevel by rememberSaveable { mutableStateOf(initialReorder.toString()) }
    var unit by rememberSaveable { mutableStateOf(initialUnit) }
    var imageUri by rememberSaveable { mutableStateOf(initialImageUri) }
    var mfgOrPkgType by rememberSaveable { mutableStateOf(initialMfgOrPkgType) }
    var mfgOrPkgDate by rememberSaveable { mutableStateOf(initialMfgOrPkgDate) }
    var expiryDate by rememberSaveable { mutableStateOf(initialExpiryDate) }
    var editStockReason by rememberSaveable { mutableStateOf<String?>(null) }
    var editStockNote by rememberSaveable { mutableStateOf("") }

    var showEnlargedView by rememberSaveable { mutableStateOf(false) }
    var isError by rememberSaveable { mutableStateOf(false) }
    var showPastExpiryConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            val isImage = mimeType == null || mimeType.startsWith("image/")
            if (!isImage) {
                Toast.makeText(context, context.getString(R.string.products_invalid_image_type), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val fileSize = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (_: Exception) { -1L }
            if (fileSize > 5 * 1024 * 1024L) {
                Toast.makeText(context, context.getString(R.string.products_image_too_large), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val savedPath = copyUriToInternalStorage(context, uri)
                if (savedPath != null) {
                    imageUri = savedPath
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val file = File(context.filesDir, "product_${System.currentTimeMillis()}.jpg")
                try {
                    file.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        imageUri = file.absolutePath
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.camera_permission_photo_required), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.camera_permission_photo_required), Toast.LENGTH_SHORT).show()
        }
    }

    val submitProduct = {
        if (!isSaving) {
        val pPrice = purchasePrice.toDoubleOrNull() ?: 0.0
        val mPrice = mrpPrice.toDoubleOrNull() ?: 0.0
        val dPrice = discountedPrice.toDoubleOrNull() ?: 0.0
        val wPrice = wholesalePrice.toDoubleOrNull() ?: 0.0
        val effectiveSelling = if (dPrice > 0.0) dPrice else if (mPrice > 0.0) mPrice else 0.0
        val effectiveMrp = if (mPrice > 0.0) mPrice else effectiveSelling
        val effectiveDisc = if (dPrice > 0.0) dPrice else effectiveSelling
        val qty = if (isEdit && stockQty.isBlank()) initialStock else (stockQty.toDoubleOrNull() ?: if (isEdit) initialStock else 0.0)
        val isStockAltered = isEdit && kotlin.math.abs(qty - initialStock) > 0.0001
        val reorder = reorderLevel.toIntOrNull() ?: 0
        val finalBarcode = toEnglishDigits(barcode).trim().ifBlank {
            var candidate: String
            var attempts = 0
            do {
                candidate = (100000000000L..999999999999L).random().toString()
                attempts++
            } while (existingBarcodes.contains(candidate) && attempts < 100)
            candidate
        }

        val initialTrimmed = toEnglishDigits(initialBarcode).trim()
        val isBarcodeCollision = (!isEdit && existingBarcodes.contains(finalBarcode)) ||
                (isEdit && finalBarcode != initialTrimmed && existingBarcodes.contains(finalBarcode))

        when {
            name.isBlank() -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_enter_product_name_warning), Toast.LENGTH_SHORT).show()
            }
            isBarcodeCollision -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_barcode_already_exists), Toast.LENGTH_SHORT).show()
            }
            effectiveSelling <= 0 -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_enter_valid_selling_price_warning), Toast.LENGTH_SHORT).show()
            }
            mPrice > 0.0 && effectiveSelling > mPrice -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_selling_price_exceeds_mrp_warning), Toast.LENGTH_SHORT).show()
            }
            isStockAltered && editStockReason.isNullOrBlank() -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_select_stock_adjustment_reason_warning), Toast.LENGTH_SHORT).show()
            }
            pPrice < 0.0 || wPrice < 0.0 || qty < 0.0 || reorder < 0 -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.toast_negative_price_stock_error), Toast.LENGTH_SHORT).show()
            }
            expiryDate.isNotBlank() && com.example.techyfipos.utils.parseExpiryDateToMillis(expiryDate) == null -> {
                isError = true
                Toast.makeText(context, context.getString(R.string.products_invalid_expiry_date_warning), Toast.LENGTH_SHORT).show()
            }
            expiryDate.isNotBlank() && (com.example.techyfipos.utils.parseExpiryDateToMillis(expiryDate) ?: Long.MAX_VALUE) < System.currentTimeMillis() && !showPastExpiryConfirmDialog -> {
                showPastExpiryConfirmDialog = true
            }
            else -> {
                isError = false
                isSaving = true
                onSave(
                    name, finalBarcode, category, pPrice, effectiveSelling, effectiveMrp,
                    effectiveDisc, qty, reorder, imageUri, mfgOrPkgType, mfgOrPkgDate,
                    expiryDate, unit, wPrice, editStockReason.takeIf { isEdit },
                    editStockNote.trim().takeIf { isEdit && it.isNotBlank() },
                    { success -> if (!success) isSaving = false }
                )
            }
        }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.products_product_name_required_label), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                    BasicTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        textStyle = TextStyle(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                        Text(stringResource(R.string.products_barcode_sku_optional_label), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(stringResource(R.string.products_auto_generated_hint), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    BasicTextField(
                                        value = barcode,
                                        onValueChange = { barcode = toEnglishDigits(it) },
                                        textStyle = TextStyle(
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    if (barcode.isBlank()) {
                                                        Surface(
                                                            onClick = {
                                                                var candidate: String
                                                                var attempts = 0
                                                                do {
                                                                    candidate = "SKU" + System.currentTimeMillis().toString().takeLast(8)
                                                                    attempts++
                                                                } while (existingBarcodes.contains(candidate) && attempts < 100)
                                                                barcode = candidate
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                                                color = MaterialTheme.colorScheme.primary
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                    .border(
                                                        1.dp,
                                                        if (categoryDropdownExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
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
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        innerTextField()
                                                    }
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = stringResource(R.string.cd_expand_category),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
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
                                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (item.nameBn.isNotBlank() && item.nameBn != item.nameEn) {
                                                                    Text(
                                                                        "(${item.nameBn})",
                                                                        fontSize = 11.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                            if (isSelected) {
                                                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
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
                                            onClick = {
                                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.CAMERA
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    try {
                                                        cameraLauncher.launch(null)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, context.getString(R.string.camera_permission_photo_required), Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                                }
                                            },
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            stringResource(R.string.products_4tier_badge),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                        Text(stringResource(R.string.products_quick_markup_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    "+$pct%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                val filtered = com.example.techyfipos.utils.sanitizeNumericInput(input)
                                                if (filtered.count { it == '.' } <= 1) purchasePrice = filtered.take(8)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
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
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (purchasePrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                val filtered = com.example.techyfipos.utils.sanitizeNumericInput(input)
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
                                                color = MaterialTheme.colorScheme.onSurface
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
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (mrpPrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                val filtered = com.example.techyfipos.utils.sanitizeNumericInput(input)
                                                if (filtered.count { it == '.' } <= 1) discountedPrice = filtered.take(8)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
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
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (discountedPrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                val filtered = com.example.techyfipos.utils.sanitizeNumericInput(input)
                                                if (filtered.count { it == '.' } <= 1) wholesalePrice = filtered.take(8)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
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
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (wholesalePrice.isEmpty()) {
                                                        Text("0.00", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (profitPerUnit >= 0) {
                                            if (isDark) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFFECFDF5)
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (profitPerUnit >= 0) {
                                                if (isDark) Color(0xFF059669) else Color(0xFFA7F3D0)
                                            } else {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                            }
                                        ),
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
                                                        text = if (profitPerUnit >= 0) {
                                                            stringResource(R.string.product_profit_per_unit_fmt, formatTaka(profitPerUnit))
                                                        } else {
                                                            stringResource(R.string.product_loss_per_unit_fmt, formatTaka(-profitPerUnit))
                                                        },
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 12.sp,
                                                        color = if (profitPerUnit >= 0) {
                                                            if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)
                                                        } else {
                                                            MaterialTheme.colorScheme.onErrorContainer
                                                        }
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Surface(
                                                        shape = RoundedCornerShape(5.dp),
                                                        color = if (profitPerUnit >= 0) Color(0xFF059669) else Color(0xFFE11D48)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.product_margin_fmt, String.format(Locale.US, "%.1f", marginPct)),
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
                                                                text = stringResource(R.string.product_markup_fmt, String.format(Locale.US, "%.1f", markupPct)),
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
                                                    text = stringResource(R.string.product_selling_price_below_cost_alert),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
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
                        val isDarkStock = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val (healthText, healthBg, healthColor) = when {
                            currentStockVal <= 0.0 -> Triple(
                                stringResource(R.string.out_of_stock),
                                if (isDarkStock) MaterialTheme.colorScheme.errorContainer else Color(0xFFFEF2F2),
                                if (isDarkStock) MaterialTheme.colorScheme.onErrorContainer else Color(0xFFDC2626)
                            )
                            currentStockVal <= currentMinAlert -> Triple(
                                stringResource(R.string.low_stock),
                                if (isDarkStock) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFFFFFBEB),
                                if (isDarkStock) Color(0xFFFDE68A) else Color(0xFFD97706)
                            )
                            else -> Triple(
                                stringResource(R.string.in_stock),
                                if (isDarkStock) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFFECFDF5),
                                if (isDarkStock) Color(0xFFA7F3D0) else Color(0xFF059669)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = healthBg
                                    ) {
                                        Text(
                                            healthText,
                                            fontSize = 11.sp,
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val stockEditable = canAdjustStock
                                        BasicTextField(
                                            value = stockQty,
                                            readOnly = !stockEditable,
                                            enabled = stockEditable,
                                            onValueChange = { input ->
                                                if (stockEditable) {
                                                    val filtered = com.example.techyfipos.utils.sanitizeNumericInput(input)
                                                    if (filtered.count { it == '.' } <= 1) {
                                                        stockQty = filtered.take(7)
                                                    }
                                                }
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (stockEditable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(
                                                            if (stockEditable) MaterialTheme.colorScheme.surface
                                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (stockQty.isEmpty()) {
                                                        Text("0", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        BasicTextField(
                                            value = reorderLevel,
                                            onValueChange = { input ->
                                                val filtered = com.example.techyfipos.utils.toEnglishDigits(input).filter { it.isDigit() }
                                                reorderLevel = filtered.take(5)
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(42.dp)
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (reorderLevel.isEmpty()) {
                                                        Text("5", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Surface(
                                                onClick = { showUnitDropdown = true },
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = stringResource(R.string.cd_select_unit),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = showUnitDropdown,
                                                onDismissRequest = { showUnitDropdown = false },
                                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                unitOptions.forEach { u ->
                                                    val isSelected = unit == u
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = u,
                                                                fontSize = 13.sp,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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

                                val qtyNumber = if (stockQty.isBlank()) initialStock else (stockQty.toDoubleOrNull() ?: initialStock)
                                val stockAltered = isEdit && kotlin.math.abs(qtyNumber - initialStock) > 0.0001
                                AnimatedVisibility(
                                    visible = stockAltered,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.products_reason_for_stock_adjustment_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            listOf("Restock", "Damage", "Expired", "Discrepancy", "Sample", "Store Use", "Internal Use", "Other").forEach { r ->
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
                                            placeholder = { Text(stringResource(R.string.products_audit_note_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                                            maxLines = 2,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        val isDarkTracking = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDarkTracking) Color(0xFF78350F).copy(alpha = 0.35f) else Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, if (isDarkTracking) Color(0xFFB45309) else Color(0xFFFDE68A)),
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
                                        color = if (isDarkTracking) Color(0xFFFDE68A) else Color(0xFF92400E)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        FilterChip(
                                            selected = mfgOrPkgType == "Mfg",
                                            onClick = { mfgOrPkgType = "Mfg" },
                                            label = { Text(stringResource(R.string.products_mfg_abbr), fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Animated3DFactoryIcon() },
                                            shape = RoundedCornerShape(7.dp),
                                            modifier = Modifier.height(28.dp)
                                        )
                                        FilterChip(
                                            selected = mfgOrPkgType == "Pkg",
                                            onClick = { mfgOrPkgType = "Pkg" },
                                            label = { Text(stringResource(R.string.products_pkg_abbr), fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
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
                                val expiryMillis = com.example.techyfipos.utils.parseExpiryDateToMillis(expiryDate)
                                val isExpired = expiryMillis != null && expiryMillis < System.currentTimeMillis()
                                if (isExpired) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.extendedColors.warningContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.extendedColors.warning.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("⚠️", fontSize = 12.sp)
                                            Text(
                                                text = stringResource(R.string.product_expiry_past_warning),
                                                color = MaterialTheme.extendedColors.onWarningContainer,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
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
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            Text(stringResource(R.string.products_cancel_action), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                        }

                        if (isEdit && onDelete != null) {
                            OutlinedButton(
                                onClick = { onDelete() },
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
                            enabled = !isSaving,
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
                            Text(stringResource(R.string.products_save_product_button), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
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
            onDismiss = { showBarcodeScanModal = false },
            checkBarcodeExists = checkBarcodeExists ?: { candidate -> existingBarcodes.contains(candidate) }
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

    if (showPastExpiryConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPastExpiryConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706)) },
            title = { Text(stringResource(R.string.product_expiry_past_confirm_title)) },
            text = { Text(stringResource(R.string.product_expiry_past_confirm_message, expiryDate)) },
            confirmButton = {
                Button(
                    onClick = {
                        showPastExpiryConfirmDialog = false
                        val pPrice = purchasePrice.toDoubleOrNull() ?: 0.0
                        val mPrice = mrpPrice.toDoubleOrNull() ?: 0.0
                        val dPrice = discountedPrice.toDoubleOrNull() ?: 0.0
                        val wPrice = wholesalePrice.toDoubleOrNull() ?: 0.0
                        val effectiveSelling = if (dPrice > 0.0) dPrice else if (mPrice > 0.0) mPrice else 0.0
                        val effectiveMrp = if (mPrice > 0.0) mPrice else effectiveSelling
                        val effectiveDisc = if (dPrice > 0.0) dPrice else effectiveSelling
                        val qty = if (isEdit && stockQty.isBlank()) initialStock else (stockQty.toDoubleOrNull() ?: if (isEdit) initialStock else 0.0)
                        val reorder = reorderLevel.toIntOrNull() ?: 0
                        val finalBarcode = toEnglishDigits(barcode).trim().ifBlank {
                            var candidate: String
                            var attempts = 0
                            do {
                                candidate = (100000000000L..999999999999L).random().toString()
                                attempts++
                            } while (existingBarcodes.contains(candidate) && attempts < 100)
                            candidate
                        }
                        isError = false
                        isSaving = true
                        onSave(
                            name, finalBarcode, category, pPrice, effectiveSelling, effectiveMrp,
                            effectiveDisc, qty, reorder, imageUri, mfgOrPkgType, mfgOrPkgDate,
                            expiryDate, unit, wPrice, editStockReason.takeIf { isEdit },
                            editStockNote.trim().takeIf { isEdit && it.isNotBlank() },
                            { success -> if (!success) isSaving = false }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text(stringResource(R.string.save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPastExpiryConfirmDialog = false }) {
                    Text(stringResource(R.string.products_cancel_action))
                }
            }
        )
    }
}

