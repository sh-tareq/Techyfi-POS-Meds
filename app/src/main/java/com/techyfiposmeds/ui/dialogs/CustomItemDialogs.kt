package com.techyfiposmeds.ui.dialogs

import java.util.Locale
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techyfiposmeds.R
import com.techyfiposmeds.data.QuickSalePreset
import com.techyfiposmeds.data.SettingsDataStore
import com.techyfiposmeds.ui.components.*
import com.techyfiposmeds.ui.screens.sanitizeDiscountInput
import com.techyfiposmeds.ui.viewmodel.CartItem
import com.techyfiposmeds.utils.formatTaka
import com.techyfiposmeds.utils.isFractionalUnit
import com.techyfiposmeds.utils.toEnglishDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, price: Double, quantity: Double, note: String?, unit: String, taxable: Boolean) -> Unit,
    onAddAnother: ((title: String, price: Double, quantity: Double, note: String?, unit: String, taxable: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val titleFocusRequester = remember { FocusRequester() }
    val presets by settingsDataStore.quickSalePresetsFlow.collectAsStateWithLifecycle(initialValue = QuickSalePreset.defaultPresets())
    val notePresets by settingsDataStore.notePresetsFlow.collectAsStateWithLifecycle(initialValue = SettingsDataStore.DEFAULT_NOTE_PRESETS)

    var title by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var qtyText by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf("Pcs") }
    var isTaxable by rememberSaveable { mutableStateOf(true) }
    var customUnitText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var isFreeMode by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("Product") }
    var sessionAddedCount by rememberSaveable { mutableIntStateOf(0) }

    var showManagePresets by remember { mutableStateOf(false) }
    var showManageNotePresets by remember { mutableStateOf(false) }

    val standardUnits = remember { listOf("Pcs", "Kg", "Gm", "Ltr", "Mtr", "Box", "Service", "Trip", "Other") }

    val rawPrice = toEnglishDigits(priceText).toDoubleOrNull()
    val isPriceNegative = rawPrice != null && rawPrice < 0.0
    val isPriceValid = if (isFreeMode) true else (rawPrice != null && rawPrice > 0.0)
    val parsedPrice = if (isFreeMode) 0.0 else (rawPrice ?: 0.0)
    val parsedQty = toEnglishDigits(qtyText).toDoubleOrNull() ?: 1.0
    val totalAmount = parsedPrice * parsedQty
    val activeUnit = if (unit == "Other") customUnitText.trim().ifBlank { "Pcs" } else unit
    val isValid = title.isNotBlank() && isPriceValid && !isPriceNegative && parsedPrice >= 0.0 && parsedQty > 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 10.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                // 1. Header with Dynamic 3D Animated Category Icon and Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isFreeMode) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, if (isFreeMode) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Realistic3DCategoryIcon(
                                    category = selectedCategory,
                                    isFreeMode = isFreeMode,
                                    size = 30.dp
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.cart_custom_quick_sale_title),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (sessionAddedCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFDCFCE7),
                                        border = BorderStroke(1.dp, Color(0xFF10B981))
                                    ) {
                                        Text(
                                            text = stringResource(R.string.custom_item_in_cart_badge, sessionAddedCount),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF047857),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (sessionAddedCount > 0) {
                                    stringResource(R.string.custom_item_added_another_hint)
                                } else {
                                    stringResource(R.string.cart_custom_quick_sale_subtitle)
                                },
                                fontSize = 11.sp,
                                color = if (sessionAddedCount > 0) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. Hero "Free Item / ৳0 Complimentary" One-Tap Card
                Surface(
                    onClick = {
                        isFreeMode = !isFreeMode
                        if (isFreeMode) {
                            priceText = "0"
                            selectedCategory = "Freebie"
                            if (note.isBlank()) note = context.getString(R.string.custom_item_complimentary_note)
                        } else {
                            if (priceText == "0") priceText = ""
                            selectedCategory = "Product"
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isFreeMode) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        1.2.dp,
                        if (isFreeMode) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Realistic3DSparkleGiftIcon(modifier = Modifier.size(24.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.cart_free_item_toggle_title),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isFreeMode) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.cart_free_item_toggle_desc),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isFreeMode) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isFreeMode,
                            onCheckedChange = { checked ->
                                isFreeMode = checked
                                if (isFreeMode) {
                                    priceText = "0"
                                    selectedCategory = "Freebie"
                                    if (note.isBlank()) note = context.getString(R.string.custom_item_complimentary_note)
                                } else {
                                    if (priceText == "0") priceText = ""
                                    selectedCategory = "Product"
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            )
                        )
                    }
                }

                // 3. Item Category Classification Pills (Product / Service / Fee / Free Gift)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.cart_category_label).uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val categories = listOf(
                            Triple("Product", stringResource(R.string.cart_category_product), "Pcs"),
                            Triple("Service", stringResource(R.string.cart_category_service), "Service"),
                            Triple("Fee", stringResource(R.string.cart_category_fee), "Trip"),
                            Triple("Freebie", stringResource(R.string.cart_category_freebie), "Pcs")
                        )

                        categories.forEach { (catKey, catLabel, defaultCatUnit) ->
                            val isSelected = (catKey == "Freebie" && isFreeMode) || (!isFreeMode && selectedCategory == catKey)
                            Surface(
                                onClick = {
                                    if (catKey == "Freebie") {
                                        isFreeMode = true
                                        priceText = "0"
                                        selectedCategory = "Freebie"
                                    } else {
                                        isFreeMode = false
                                        selectedCategory = catKey
                                        unit = defaultCatUnit
                                        if (priceText == "0") priceText = ""
                                    }
                                },
                                shape = RoundedCornerShape(11.dp),
                                color = when {
                                    isSelected && catKey == "Freebie" -> Color(0xFFDCFCE7)
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected && catKey == "Freebie" -> Color(0xFF10B981)
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    }
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    when (catKey) {
                                        "Product" -> Realistic3DProductPackageIcon(size = 18.dp)
                                        "Service" -> Realistic3DServiceWrenchIcon(size = 18.dp)
                                        "Fee" -> Realistic3DDeliveryTruckIcon(size = 18.dp)
                                        "Freebie" -> Realistic3DSparkleGiftIcon(modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = catLabel,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = when {
                                            isSelected && catKey == "Freebie" -> Color(0xFF047857)
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Quick Preset Chips Row with 3D Flash Tag & Manage Button
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Realistic3DPresetFlashIcon(modifier = Modifier.size(15.dp))
                            Text(
                                text = stringResource(R.string.cart_quick_presets_label),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.4.sp
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showManagePresets = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.custom_item_edit_presets_cd),
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.cart_edit_presets_button),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        presets.forEach { preset ->
                            val isPresetFree = preset.defaultPrice == 0.0
                            val isSelected = title == preset.name
                            Surface(
                                onClick = {
                                    title = preset.name
                                    if (isPresetFree) {
                                        isFreeMode = true
                                        priceText = "0"
                                        selectedCategory = "Freebie"
                                    } else {
                                        isFreeMode = false
                                        priceText = if (preset.defaultPrice % 1.0 == 0.0) preset.defaultPrice.toLong().toString() else preset.defaultPrice.toString()
                                    }
                                    if (preset.defaultUnit in standardUnits) {
                                        unit = preset.defaultUnit
                                    } else {
                                        unit = "Other"
                                        customUnitText = preset.defaultUnit
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isSelected && isPresetFree -> Color(0xFFDCFCE7)
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isPresetFree -> Color(0xFFF0FDF4)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected && isPresetFree -> Color(0xFF10B981)
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isPresetFree -> Color(0xFF86EFAC)
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = preset.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = when {
                                            isSelected && isPresetFree -> Color(0xFF047857)
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isPresetFree -> Color(0xFF059669)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when {
                                            isSelected && isPresetFree -> Color(0xFF10B981)
                                            isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                            isPresetFree -> Color(0xFFDCFCE7)
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        modifier = Modifier.padding(start = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isPresetFree) stringResource(R.string.cart_free_badge) else "৳${if (preset.defaultPrice % 1.0 == 0.0) preset.defaultPrice.toLong().toString() else preset.defaultPrice}",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = when {
                                                isSelected && isPresetFree -> Color.White
                                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                                isPresetFree -> Color(0xFF059669)
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Add new preset shortcut button
                        Surface(
                            onClick = { showManagePresets = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.custom_item_add_preset_cd),
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.cart_add_button),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 5. Item Description Field with Clean Label & Harmonized 52dp Container
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.cart_item_description_label).uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isFreeMode) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, if (isFreeMode) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isFreeMode) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (title.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.cart_item_description_placeholder),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                BasicTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(if (isFreeMode) Color(0xFF10B981) else MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(titleFocusRequester)
                                )
                            }
                            if (title.isNotEmpty()) {
                                IconButton(onClick = { title = "" }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // 6. Price & Quantity Stepper Grid (Perfectly Symmetrical 52dp Row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Price Box (52dp)
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cart_price_label).uppercase(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isFreeMode) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.2.dp, if (isFreeMode) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Realistic3DPriceCoinIcon(modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    val currentDisplayPrice = if (isFreeMode) "0" else priceText
                                    if (currentDisplayPrice.isEmpty()) {
                                        Text(
                                            text = "0.00",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    BasicTextField(
                                        value = currentDisplayPrice,
                                        onValueChange = { input ->
                                            val sanitized = sanitizeDiscountInput(input)
                                            priceText = sanitized
                                            if (sanitized == "0") {
                                                isFreeMode = true
                                            } else if (sanitized.toDoubleOrNull() != null && (sanitized.toDoubleOrNull() ?: 0.0) > 0.0) {
                                                isFreeMode = false
                                            }
                                        },
                                        textStyle = TextStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isFreeMode) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        cursorBrush = SolidColor(if (isFreeMode) Color(0xFF10B981) else MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (isFreeMode) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFDCFCE7),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.cart_free_badge),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF047857),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (isPriceNegative) {
                            Text(
                                text = stringResource(R.string.custom_item_price_negative_error),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                            )
                        }
                    }

                    // Quantity Stepper Container (52dp)
                    val isWeightOrVolume = isFractionalUnit(activeUnit)
                    val stepValue = if (isWeightOrVolume) 0.25 else 1.0

                    Column(
                        modifier = Modifier.weight(1.05f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cart_quantity_label, activeUnit).uppercase(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        val cur = com.techyfiposmeds.utils.sanitizeNumericInput(qtyText).toDoubleOrNull() ?: 1.0
                                        val next = (Math.round((cur - stepValue) * 100.0) / 100.0).coerceAtLeast(0.01)
                                        qtyText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = stringResource(R.string.cd_decrease),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                BasicTextField(
                                    value = qtyText,
                                    onValueChange = { input ->
                                        val filtered = com.techyfiposmeds.utils.sanitizeNumericInput(input)
                                        if (filtered.count { it == '.' } <= 1) {
                                            qtyText = filtered.take(6)
                                        }
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        val cur = com.techyfiposmeds.utils.sanitizeNumericInput(qtyText).toDoubleOrNull() ?: 1.0
                                        val next = Math.round((cur + stepValue) * 100.0) / 100.0
                                        qtyText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.cd_increase),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Enclosed Quick Quantity Preset Chips with 3D Balance Scale
                val isWeightOrVolumeUnit = isFractionalUnit(activeUnit)
                val fractionalPresets = if (isWeightOrVolumeUnit) {
                    listOf(0.25, 0.5, 1.0, 1.5, 2.75, 5.0)
                } else {
                    listOf(1.0, 2.0, 3.0, 5.0, 10.0)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Realistic3DBalanceScaleIcon(modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.cart_qty_label_short),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        fractionalPresets.forEach { presetVal ->
                            val presetStr = if (presetVal % 1.0 == 0.0) presetVal.toLong().toString() else String.format(Locale.US, "%.2f", presetVal).trimEnd('0').trimEnd('.')
                            val isPresetActive = qtyText == presetStr
                            Surface(
                                onClick = { qtyText = presetStr },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isPresetActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPresetActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Text(
                                    text = "$presetStr $activeUnit",
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isPresetActive) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isPresetActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Increment chip +0.25
                        Surface(
                            onClick = {
                                val cur = com.techyfiposmeds.utils.sanitizeNumericInput(qtyText).toDoubleOrNull() ?: 0.0
                                val next = Math.round((cur + 0.25) * 100.0) / 100.0
                                qtyText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "+0.25",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        // Increment chip +0.50
                        Surface(
                            onClick = {
                                val cur = com.techyfiposmeds.utils.sanitizeNumericInput(qtyText).toDoubleOrNull() ?: 0.0
                                val next = Math.round((cur + 0.50) * 100.0) / 100.0
                                qtyText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "+0.50",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 8. Segmented Unit Selector with 3D Ruler Icon
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Realistic3DMeasureUnitIcon(modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.cart_unit_of_measure_label),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        standardUnits.forEach { u ->
                            val isSelected = unit == u
                            Surface(
                                onClick = { unit = u },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                            ) {
                                Text(
                                    text = u,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                    if (unit == "Other") {
                        OutlinedTextField(
                            value = customUnitText,
                            onValueChange = { customUnitText = it.take(8) },
                            placeholder = { Text(stringResource(R.string.cart_custom_unit_placeholder)) },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }

                // 9. Note / Warranty Input + 3D Hologram Shield
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Realistic3DWarrantyShieldIcon(modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.cart_note_warranty_label).uppercase(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.4.sp,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.cart_edit_presets_with_icon),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { showManageNotePresets = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Realistic3DWarrantyShieldIcon(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (note.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.cart_note_placeholder_example),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (note.isNotEmpty()) {
                                IconButton(onClick = { note = "" }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.cd_clear),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Quick Warranty / Info Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        notePresets.forEach { wNote ->
                            val isSelected = note == wNote
                            Surface(
                                onClick = { note = if (isSelected) "" else wNote },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(0.8.dp, if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = wNote,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = { showManageNotePresets = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = stringResource(R.string.cart_add_preset_button),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 10. Taxable Item Toggle Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.custom_item_taxable_label),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTaxable) stringResource(R.string.custom_item_taxable_desc) else stringResource(R.string.custom_item_non_taxable_desc),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isTaxable,
                            onCheckedChange = { isTaxable = it }
                        )
                    }
                }

                // 11. Real-Time Live Subtotal Card with 3D Calculator
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isFreeMode || parsedPrice == 0.0) Color(0xFFECFDF5) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(
                        1.2.dp,
                        if (isFreeMode || parsedPrice == 0.0) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Realistic3DSubtotalCalculatorIcon(
                                modifier = Modifier.size(28.dp),
                                isFree = (isFreeMode || parsedPrice == 0.0)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.cart_subtotal_preview_label),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatTaka(parsedPrice)} × $qtyText $activeUnit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (isFreeMode || parsedPrice == 0.0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "৳0",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF059669)
                                ) {
                                    Text(
                                        text = stringResource(R.string.cart_free_badge),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = formatTaka(totalAmount),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 11. Action Buttons: Secondary Row (Cancel & Add Another) + Full-Width Primary CTA
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Secondary Actions Row: Cancel and Add Another
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (sessionAddedCount > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (sessionAddedCount > 0) Color(0xFFECFDF5) else Color.Transparent
                            )
                        ) {
                            Text(
                                text = if (sessionAddedCount > 0) stringResource(R.string.custom_item_done_count, sessionAddedCount) else stringResource(R.string.cancel),
                                fontWeight = if (sessionAddedCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (sessionAddedCount > 0) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (isValid) {
                                    val itemTitle = title.trim()
                                    val itemPrice = parsedPrice
                                    val itemQty = parsedQty
                                    val itemNote = note.trim().takeIf { it.isNotBlank() }
                                    val itemUnit = activeUnit

                                    if (onAddAnother != null) {
                                        onAddAnother(itemTitle, itemPrice, itemQty, itemNote, itemUnit, isTaxable)
                                    } else {
                                        onConfirm(itemTitle, itemPrice, itemQty, itemNote, itemUnit, isTaxable)
                                    }

                                    sessionAddedCount++
                                    val msg = if (onAddAnother != null) {
                                        context.getString(R.string.custom_item_added_enter_next, itemTitle)
                                    } else {
                                        context.getString(R.string.custom_item_added_to_cart, itemTitle)
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                                    // Reset inputs to allow immediate entry of next item
                                    title = ""
                                    priceText = ""
                                    qtyText = "1"
                                    note = ""
                                    isTaxable = true
                                    isFreeMode = false
                                    selectedCategory = "Product"
                                    customUnitText = ""
                                    unit = "Pcs"

                                    scope.launch {
                                        scrollState.animateScrollTo(0)
                                        delay(100)
                                        try {
                                            titleFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            enabled = isValid,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            border = BorderStroke(
                                1.2.dp,
                                if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.custom_item_add_another),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Primary Hero Button (Full Width, Never Truncates)
                    Button(
                        onClick = {
                            if (isValid) {
                                val itemNote = note.trim().takeIf { it.isNotBlank() }
                                onConfirm(title.trim(), parsedPrice, parsedQty, itemNote, activeUnit, isTaxable)
                            }
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFreeMode || parsedPrice == 0.0) Color(0xFF10B981) else Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Realistic3DCartLaunchIcon(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isFreeMode || parsedPrice == 0.0) {
                                    stringResource(R.string.cart_add_free_item_button)
                                } else {
                                    stringResource(R.string.cart_add_to_cart_with_total, formatTaka(totalAmount))
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManagePresets) {
        ManageQuickSalePresetsDialog(
            currentPresets = presets,
            onDismiss = { showManagePresets = false },
            onSavePresets = { updated ->
                scope.launch {
                    settingsDataStore.saveQuickSalePresets(updated)
                    Toast.makeText(context, context.getString(R.string.cart_presets_updated_toast), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showManageNotePresets) {
        ManageNotePresetsDialog(
            currentPresets = notePresets,
            onDismiss = { showManageNotePresets = false },
            onSavePresets = { updated ->
                scope.launch {
                    settingsDataStore.saveNotePresets(updated)
                    Toast.makeText(context, context.getString(R.string.cart_note_presets_updated_toast), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun ManageQuickSalePresetsDialog(
    currentPresets: List<QuickSalePreset>,
    onDismiss: () -> Unit,
    onSavePresets: (List<QuickSalePreset>) -> Unit
) {
    var presetsList by remember { mutableStateOf(currentPresets.toMutableList()) }
    var showAddInline by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<QuickSalePreset?>(null) }

    var newName by remember { mutableStateOf("") }
    var newPriceText by remember { mutableStateOf("") }
    var newUnit by remember { mutableStateOf("Pcs") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚙️", fontSize = 20.sp)
                        Text(
                            text = stringResource(R.string.cart_manage_quick_presets_title),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(15.dp))
                    }
                }

                Text(
                    text = stringResource(R.string.cart_manage_presets_subtitle),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Presets List
                if (presetsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.custom_item_no_presets_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(presetsList, key = { it.id }) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "৳${if (item.defaultPrice % 1.0 == 0.0) item.defaultPrice.toLong().toString() else item.defaultPrice} • ${item.defaultUnit}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { editingPreset = item },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.cd_edit),
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val updated = presetsList.toMutableList().apply { remove(item) }
                                                presetsList = updated
                                                onSavePresets(updated)
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = stringResource(R.string.delete),
                                                modifier = Modifier.size(15.dp),
                                                tint = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Preset Section
                if (showAddInline) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.cart_add_new_preset_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                placeholder = { Text(stringResource(R.string.cart_preset_name_placeholder)) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPriceText,
                                    onValueChange = { newPriceText = sanitizeDiscountInput(it) },
                                    placeholder = { Text(stringResource(R.string.cart_preset_price_placeholder)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newUnit,
                                    onValueChange = { newUnit = it.take(8) },
                                    placeholder = { Text(stringResource(R.string.cart_preset_unit_placeholder)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAddInline = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                                Button(
                                    onClick = {
                                        val price = newPriceText.toDoubleOrNull() ?: 0.0
                                        if (newName.isNotBlank() && price >= 0.0) {
                                            val created = QuickSalePreset(
                                                name = newName.trim(),
                                                defaultPrice = price,
                                                defaultUnit = newUnit.trim().ifBlank { "Pcs" }
                                            )
                                            val updated = presetsList.toMutableList().apply { add(created) }
                                            presetsList = updated
                                            onSavePresets(updated)
                                            newName = ""
                                            newPriceText = ""
                                            showAddInline = false
                                        }
                                    },
                                    enabled = newName.isNotBlank() && (newPriceText.toDoubleOrNull() ?: 0.0) >= 0.0,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.cart_save_preset_button))
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showAddInline = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cart_create_new_preset_button), fontWeight = FontWeight.Bold)
                    }
                }

                // Reset & Done Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val def = QuickSalePreset.defaultPresets()
                            presetsList = def.toMutableList()
                            onSavePresets(def)
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.cart_reset_defaults_button), fontSize = 11.5.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cart_done_button))
                    }
                }
            }
        }
    }

    // Edit Preset Sub-Dialog
    editingPreset?.let { target ->
        var editName by remember(target.id) { mutableStateOf(target.name) }
        var editPriceText by remember(target.id) {
            mutableStateOf(if (target.defaultPrice % 1.0 == 0.0) target.defaultPrice.toLong().toString() else target.defaultPrice.toString())
        }
        var editUnit by remember(target.id) { mutableStateOf(target.defaultUnit) }

        AlertDialog(
            onDismissRequest = { editingPreset = null },
            title = { Text(stringResource(R.string.cart_edit_preset_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.cart_preset_name_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPriceText,
                        onValueChange = { editPriceText = sanitizeDiscountInput(it) },
                        label = { Text(stringResource(R.string.cart_default_price_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUnit,
                        onValueChange = { editUnit = it.take(8) },
                        label = { Text(stringResource(R.string.cart_unit_pcs_kg_service_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = editPriceText.toDoubleOrNull() ?: 0.0
                        if (editName.isNotBlank() && p >= 0.0) {
                            val updatedList = presetsList.map {
                                if (it.id == target.id) {
                                    it.copy(
                                        name = editName.trim(),
                                        defaultPrice = p,
                                        defaultUnit = editUnit.trim().ifBlank { "Pcs" }
                                    )
                                } else it
                            }
                            presetsList = updatedList.toMutableList()
                            onSavePresets(updatedList)
                            editingPreset = null
                        }
                    },
                    enabled = editName.isNotBlank() && (editPriceText.toDoubleOrNull() ?: 0.0) >= 0.0
                ) {
                    Text(stringResource(R.string.cart_save_changes_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPreset = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun EditItemNoteDialog(
    initialNote: String?,
    productName: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val notePresets by settingsDataStore.notePresetsFlow.collectAsStateWithLifecycle(initialValue = SettingsDataStore.DEFAULT_NOTE_PRESETS)
    var showManageNotePresets by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(initialNote ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📝", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cart_item_note_warranty_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    productName,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 21.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None
                        )
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.cart_item_note_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.cart_custom_line_note_label)) },
                    placeholder = { Text(stringResource(R.string.cart_custom_line_note_placeholder)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Presets Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.cart_quick_presets_plain_label),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.cart_edit_with_gear_icon),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { showManageNotePresets = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    notePresets.forEach { wNote ->
                        val isSelected = note == wNote
                        Surface(
                            onClick = { note = if (isSelected) "" else wNote },
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.8.dp, if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = wNote,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Surface(
                        onClick = { showManageNotePresets = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = stringResource(R.string.cart_add_plus_button),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(note.trim().takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.cart_save_note_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showManageNotePresets) {
        ManageNotePresetsDialog(
            currentPresets = notePresets,
            onDismiss = { showManageNotePresets = false },
            onSavePresets = { updated ->
                scope.launch {
                    settingsDataStore.saveNotePresets(updated)
                    Toast.makeText(context, context.getString(R.string.cart_note_presets_updated_toast), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun ManageNotePresetsDialog(
    currentPresets: List<String>,
    onDismiss: () -> Unit,
    onSavePresets: (List<String>) -> Unit
) {
    var presetsList by remember(currentPresets) { mutableStateOf(currentPresets.toMutableList()) }
    var newPresetText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚙️", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cart_manage_note_presets_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.cart_manage_note_presets_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Add New Preset Form
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPresetText,
                        onValueChange = { newPresetText = it },
                        placeholder = { Text(stringResource(R.string.cart_note_preset_placeholder), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val trimmed = newPresetText.trim()
                            if (trimmed.isNotBlank() && !presetsList.contains(trimmed)) {
                                val updated = presetsList.toMutableList().apply { add(trimmed) }
                                presetsList = updated
                                onSavePresets(updated)
                                newPresetText = ""
                            }
                        },
                        enabled = newPresetText.trim().isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(stringResource(R.string.cart_add_plus_button), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // List of current presets
                Text(
                    text = stringResource(R.string.cart_saved_presets_count, presetsList.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetsList.forEachIndexed { index, preset ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingIndex = index
                                            editingText = preset
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Text("✏️", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            val updated = presetsList.toMutableList().apply { removeAt(index) }
                                            presetsList = updated
                                            onSavePresets(updated)
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Text("🗑️", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.cart_done_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    val defaults = SettingsDataStore.DEFAULT_NOTE_PRESETS
                    presetsList = defaults.toMutableList()
                    onSavePresets(defaults)
                }
            ) {
                Text(stringResource(R.string.cart_reset_defaults_button), color = MaterialTheme.colorScheme.error)
            }
        }
    )

    if (editingIndex != null) {
        val idx = editingIndex ?: 0
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            title = { Text(stringResource(R.string.cart_edit_preset_with_icon_title), fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editingText.trim()
                        if (trimmed.isNotBlank()) {
                            if (idx in presetsList.indices) {
                                val updated = presetsList.toMutableList().apply { set(idx, trimmed) }
                                presetsList = updated
                                onSavePresets(updated)
                            }
                            editingIndex = null
                        }
                    },
                    enabled = editingText.trim().isNotBlank()
                ) {
                    Text(stringResource(R.string.cart_save_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingIndex = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
