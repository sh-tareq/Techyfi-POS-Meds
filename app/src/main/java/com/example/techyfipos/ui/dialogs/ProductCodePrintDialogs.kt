package com.example.techyfipos.ui.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.example.techyfipos.utils.getLocalizedName
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.techyfipos.ui.components.trackUserActivity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.techyfipos.data.db.Product
import com.example.techyfipos.data.InvoiceSettingsData
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.printer.*
import com.example.techyfipos.utils.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.example.techyfipos.ui.components.PosSearchBox
import com.example.techyfipos.ui.components.PosSectionColors

data class ActionStatus(val text: String, val isError: Boolean)

@Composable
fun CopiesStepper(
    copies: Int,
    onCopiesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 500,
    presets: List<Int> = listOf(1, 2, 3, 5, 10, 20, 50, 100),
    stockQuantity: Double? = null,
    onErrorChange: ((Boolean) -> Unit)? = null
) {
    var text by remember(copies) { mutableStateOf(copies.toString()) }
    var isFocused by remember { mutableStateOf(false) }
    val parsed = text.toIntOrNull()
    val isError = parsed == null || parsed !in min..max
    LaunchedEffect(isError) {
        onErrorChange?.invoke(isError)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.label_copies_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val clamped = (copies - 1).coerceIn(min, max)
                        text = clamped.toString()
                        onCopiesChange(clamped)
                    },
                    enabled = copies > min,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.decrease_copies))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.width(68.dp).height(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        BasicTextField(
                            value = text,
                            onValueChange = { newVal ->
                                val filtered = toEnglishDigits(newVal).filter { it.isDigit() }.take(4)
                                text = filtered
                                val p = filtered.toIntOrNull()
                                if (p != null && p in min..max) {
                                    onCopiesChange(p)
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (isFocused && !focusState.isFocused) {
                                        val clamped = clampCopies(text.toIntOrNull(), min, max)
                                        text = clamped.toString()
                                        onCopiesChange(clamped)
                                    }
                                    isFocused = focusState.isFocused
                                }
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val clamped = (copies + 1).coerceIn(min, max)
                        text = clamped.toString()
                        onCopiesChange(clamped)
                    },
                    enabled = copies < max,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.increase_copies))
                }
            }
        }

        if (isError) {
            Text(
                text = stringResource(R.string.copies_range_error, min, max),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Quick Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = copies == preset,
                    onClick = {
                        text = preset.toString()
                        onCopiesChange(preset)
                    },
                    label = { Text("$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.height(32.dp)
                )
            }

            if (stockQuantity != null) {
                val stockCount = stockToPrintCopies(stockQuantity, min, max)
                if (stockCount !in presets) {
                    FilterChip(
                        selected = copies == stockCount,
                        onClick = {
                            text = stockCount.toString()
                            onCopiesChange(stockCount)
                        },
                        label = { Text(stringResource(R.string.preset_stock_copies_fmt, stockCount), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.height(32.dp)
                    )
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Price Tier on Label | Show Price Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.products_price_tier_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShowPriceChange(!showPrice) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showPrice,
                        onCheckedChange = { onShowPriceChange(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.show_price),
                        style = MaterialTheme.typography.bodySmall,
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
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = showPrice) { onTierSelect(ProductCodePriceTier.SELLING) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSellingActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (isSellingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSellingActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sellingPriceDisplay,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSellingActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 2. MRP Price
                    val isMrpActive = selectedTier == ProductCodePriceTier.MRP
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = showPrice) { onTierSelect(ProductCodePriceTier.MRP) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMrpActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (isMrpActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMrpActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mrpPriceDisplay,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMrpActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 3. Wholesale Price
                    val isWholesaleActive = selectedTier == ProductCodePriceTier.WHOLESALE
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = showPrice) { onTierSelect(ProductCodePriceTier.WHOLESALE) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isWholesaleActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (isWholesaleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isWholesaleActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = wholesalePriceDisplay,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isWholesaleActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isDualActive) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                        .border(
                            1.dp,
                            if (isDualActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
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
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDualActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        val notSetText = stringResource(R.string.products_price_tier_not_set)
                        val hasMrp = mrpPriceDisplay.isNotBlank() && mrpPriceDisplay != notSetText && mrpPriceDisplay != "-"

                        if (hasMrp) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.product_code_mrp_strikethrough_fmt, mrpPriceDisplay),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textDecoration = TextDecoration.LineThrough,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = sellingPriceDisplay,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.products_price_tier_mrp_not_set_hint),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(10.dp))

                // Micro-customizations (Unit & Language options)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Show Unit (/Pcs)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = showPrice) { onShowUnitChange(!showUnit) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (showUnit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(
                            1.dp,
                            if (showUnit) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (showUnit) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (showUnit) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showUnit) stringResource(R.string.opt_on) else stringResource(R.string.opt_off),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (showUnit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Option 2: Bangla Digits
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = showPrice) { onShowBanglaDigitsChange(!showBanglaDigits) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (showBanglaDigits) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(
                            1.dp,
                            if (showBanglaDigits) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (showBanglaDigits) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (showBanglaDigits) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showBanglaDigits) stringResource(R.string.opt_on) else stringResource(R.string.opt_off),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (showBanglaDigits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class BatchPrintScope {
    ALL,
    IN_STOCK,
    CUSTOM
}

@Composable
fun BatchProductPickerSheet(
    allProducts: List<Product>,
    selectedBarcodes: Set<String>,
    initialQuantities: Map<String, Int> = emptyMap(),
    onSelectionChange: (Set<String>) -> Unit = {},
    onSelectionAndQuantitiesChange: ((Set<String>, Map<String, Int>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var tempSelected by remember { mutableStateOf(selectedBarcodes) }
    var tempQuantities by remember { mutableStateOf(initialQuantities.toMutableMap()) }

    val validProducts = remember(allProducts) {
        allProducts.filter { it.barcode.isNotBlank() }
    }

    val filteredProducts = remember(validProducts, searchQuery) {
        if (searchQuery.isBlank()) {
            validProducts
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            validProducts.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                it.barcode.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = {
            onSelectionAndQuantitiesChange?.invoke(tempSelected, tempQuantities)
            onSelectionChange(tempSelected)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f)
                .trackUserActivity(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
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
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Checklist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.batch_print_picker_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.batch_picker_ratio_fmt, tempSelected.size, validProducts.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onSelectionChange(tempSelected)
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field
                PosSearchBox(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = stringResource(R.string.batch_print_search_placeholder),
                    accentColor = PosSectionColors.Products,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Action Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {
                            tempSelected = tempSelected + filteredProducts.map { it.barcode }
                        },
                        label = { Text(stringResource(R.string.batch_print_select_all), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            tempSelected = tempSelected - filteredProducts.map { it.barcode }.toSet()
                        },
                        label = { Text(stringResource(R.string.batch_print_deselect_all), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            tempSelected = tempSelected + filteredProducts.filter { it.stockQty > 0.0 }.map { it.barcode }
                        },
                        label = { Text(stringResource(R.string.in_stock), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Product items list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredProducts, key = { it.barcode }) { prod ->
                        val isChecked = prod.barcode in tempSelected
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tempSelected = if (isChecked) {
                                        tempSelected - prod.barcode
                                    } else {
                                        tempSelected + prod.barcode
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        tempSelected = if (checked) {
                                            tempSelected + prod.barcode
                                        } else {
                                            tempSelected - prod.barcode
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = prod.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        val stockColor = if (prod.stockQty <= 0.0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary
                                        Surface(
                                            color = stockColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.stock_badge_fmt, formatStockQuantityBadge(prod.stockQty)),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = stockColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = prod.barcode,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = formatTaka(prod.sellingPrice),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp
                                            )
                                            if (isChecked) {
                                                val qty = tempQuantities[prod.barcode] ?: 1
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            val current = tempQuantities[prod.barcode] ?: 1
                                                            if (current > 1) {
                                                                tempQuantities = tempQuantities.toMutableMap().apply { put(prod.barcode, current - 1) }
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                                                    }
                                                    Text(
                                                        text = "$qty",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            val current = tempQuantities[prod.barcode] ?: 1
                                                            if (current < 500) {
                                                                tempQuantities = tempQuantities.toMutableMap().apply { put(prod.barcode, current + 1) }
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
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

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.batch_picker_selected_fmt, tempSelected.size),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = {
                            onSelectionAndQuantitiesChange?.invoke(tempSelected, tempQuantities)
                            onSelectionChange(tempSelected)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.done), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BatchProductCodePrintDialog(
    products: List<Product>,
    initiallySelectedBarcodes: Set<String> = emptySet(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: context.findActivity()
    val scope = rememberCoroutineScope()
    val invoiceSettings by remember(context) { SettingsDataStore(context).invoiceSettingsDataFlow }
        .collectAsStateWithLifecycle(initialValue = InvoiceSettingsData())
    val configuredStoreName by remember(context) { SettingsDataStore(context).shopNameFlow }
        .collectAsStateWithLifecycle(initialValue = "")

    val preferences = remember {
        context.getSharedPreferences("product_code_image_settings", Context.MODE_PRIVATE)
    }
    val hadSavedStoreName = remember { preferences.contains("store_name") }

    var scopeMode by rememberSaveable {
        mutableStateOf(if (initiallySelectedBarcodes.isNotEmpty()) BatchPrintScope.CUSTOM else BatchPrintScope.ALL)
    }
    var customSelectedBarcodes by rememberSaveable { mutableStateOf(initiallySelectedBarcodes) }
    var showProductPickerSheet by remember { mutableStateOf(false) }

    var copiesMode by rememberSaveable { mutableStateOf("FIXED") }
    var customQuantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var batchCopies by rememberSaveable { mutableIntStateOf(1) }
    var extraFieldTitle by rememberSaveable {
        mutableStateOf(preferences.getString("extra_field_title", preferences.getString("extra_field_label", "") ?: "") ?: "")
    }
    var extraFieldValue by rememberSaveable {
        mutableStateOf(preferences.getString("extra_field_value", "") ?: "")
    }
    var failedRemainingItems by remember { mutableStateOf<List<ProductLabelItem>?>(null) }
    var lastPrintedItems by remember { mutableStateOf<List<ProductLabelItem>?>(null) }

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
    var customWidthInput by rememberSaveable {
        mutableStateOf(preferences.getFloat("custom_width_mm", 50f).toString().removeSuffix(".0"))
    }
    var customHeightInput by rememberSaveable {
        mutableStateOf(preferences.getFloat("custom_height_mm", 30f).toString().removeSuffix(".0"))
    }
    var showPrice by rememberSaveable { mutableStateOf(preferences.getBoolean("show_price", true)) }
    var priceTierName by rememberSaveable {
        mutableStateOf(preferences.getString("price_tier", ProductCodePriceTier.SELLING.name) ?: ProductCodePriceTier.SELLING.name)
    }
    val priceTier = ProductCodePriceTier.entries.firstOrNull { it.name == priceTierName } ?: ProductCodePriceTier.SELLING
    var showUnit by rememberSaveable { mutableStateOf(preferences.getBoolean("show_unit", preferences.getBoolean("show_price_unit", false))) }
    var showBanglaDigits by rememberSaveable { mutableStateOf(preferences.getBoolean("show_bangla_digits", false)) }
    var showCodeNumber by rememberSaveable { mutableStateOf(preferences.getBoolean("show_code_number", true)) }
    var showStoreName by rememberSaveable { mutableStateOf(preferences.getBoolean("show_store_name", true)) }
    var storeNameInput by rememberSaveable {
        mutableStateOf(preferences.getString("store_name", "") ?: "")
    }
    var showDateTracking by rememberSaveable { mutableStateOf(preferences.getBoolean("show_date_tracking", false)) }
    var useProductDates by rememberSaveable { mutableStateOf(true) }
    var mfgOrPkgType by rememberSaveable { mutableStateOf("Mfg") }
    var mfgOrPkgDate by rememberSaveable { mutableStateOf("") }
    var expDate by rememberSaveable { mutableStateOf("") }

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

    var showAdvancedStyling by rememberSaveable { mutableStateOf(false) }
    var labelSizeMenuExpanded by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var isBatchCopiesError by remember { mutableStateOf(false) }
    var printingProgressText by remember { mutableStateOf("") }
    var totalSentSoFar by remember { mutableIntStateOf(0) }
    var printJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            printJob?.cancel()
        }
    }

    val codeType = ProductCodeType.entries.firstOrNull { it.name == codeTypeName } ?: ProductCodeType.BARCODE
    val labelSize = ProductCodeLabelSize.entries.firstOrNull { it.name == labelSizeName }
        ?: ProductCodeLabelSize.SIZE_50_X_30
    val parsedCustomWidth = customWidthInput.toFloatOrNull()
    val isCustomWidthError = labelSize == ProductCodeLabelSize.CUSTOM && (parsedCustomWidth == null || parsedCustomWidth !in 15f..150f)
    val parsedCustomHeight = customHeightInput.toFloatOrNull()
    val isCustomHeightError = labelSize == ProductCodeLabelSize.CUSTOM && (parsedCustomHeight == null || parsedCustomHeight !in 10f..200f)

    val customWidthMm = parsedCustomWidth?.coerceIn(15f, 150f) ?: 50f
    val customHeightMm = parsedCustomHeight?.coerceIn(10f, 200f) ?: 30f
    val (effectiveWidthMm, effectiveHeightMm) = resolveLabelDimensionsMm(labelSize, customWidthMm, customHeightMm)
    val effectiveStoreName = storeNameInput.ifBlank { configuredStoreName }

    LaunchedEffect(configuredStoreName) {
        if (!hadSavedStoreName && configuredStoreName.isNotBlank() && storeNameInput.isBlank()) {
            storeNameInput = configuredStoreName
        }
    }

    LaunchedEffect(
        codeTypeName, labelSizeName, showPrice, priceTierName, showUnit, showBanglaDigits, showCodeNumber,
        showStoreName, storeNameInput, showDateTracking, customWidthInput, customHeightInput,
        productNameTextScale, priceTextScale, codeNumberTextScale, storeNameTextScale,
        dateTrackingTextScale, barcodeGraphicScale, extraFieldTitle, extraFieldValue
    ) {
        val editor = preferences.edit()
            .putString("code_type", codeTypeName)
            .putString("label_size", labelSizeName)
            .putBoolean("show_price", showPrice)
            .putString("price_tier", priceTierName)
            .putBoolean("show_price_unit", showUnit)
            .putBoolean("show_unit", showUnit)
            .putBoolean("show_bangla_digits", showBanglaDigits)
            .putBoolean("show_code_number", showCodeNumber)
            .putBoolean("show_store_name", showStoreName)
            .putBoolean("show_date_tracking", showDateTracking)
            .putString("store_name", storeNameInput.trim())
            .putFloat("product_name_text_scale", productNameTextScale)
            .putFloat("price_text_scale", priceTextScale)
            .putFloat("code_number_text_scale", codeNumberTextScale)
            .putFloat("store_name_text_scale", storeNameTextScale)
            .putFloat("date_tracking_text_scale", dateTrackingTextScale)
            .putFloat("barcode_graphic_scale", barcodeGraphicScale)
            .putString("extra_field_title", extraFieldTitle.trim())
            .putString("extra_field_label", extraFieldTitle.trim())
            .putString("extra_field_value", extraFieldValue.trim())
        if (parsedCustomWidth != null && parsedCustomWidth in 15f..150f) {
            editor.putFloat("custom_width_mm", parsedCustomWidth)
        }
        if (parsedCustomHeight != null && parsedCustomHeight in 10f..200f) {
            editor.putFloat("custom_height_mm", parsedCustomHeight)
        }
        editor.apply()
    }

    val candidateProducts = remember(products, scopeMode, customSelectedBarcodes) {
        when (scopeMode) {
            BatchPrintScope.ALL -> products.filter { it.barcode.isNotBlank() }
            BatchPrintScope.IN_STOCK -> products.filter { it.barcode.isNotBlank() && it.stockQty > 0 }
            BatchPrintScope.CUSTOM -> products.filter { it.barcode.isNotBlank() && it.barcode in customSelectedBarcodes }
        }
    }

    val unconstrainedLabelCount = remember(candidateProducts, copiesMode, batchCopies, customQuantities) {
        when (copiesMode) {
            "STOCK" -> candidateProducts.sumOf { stockToPrintCopies(it.stockQty) }
            "PER_PRODUCT" -> candidateProducts.sumOf { customQuantities[it.barcode] ?: 1 }
            else -> candidateProducts.size * batchCopies
        }
    }
    val isBatchTruncated = unconstrainedLabelCount > 500
    val totalLabelCount = remember(unconstrainedLabelCount) {
        unconstrainedLabelCount.coerceAtMost(500)
    }

    // Live preview sample label
    var sampleIndex by rememberSaveable { mutableIntStateOf(0) }
    val sampleProd = remember(candidateProducts, sampleIndex) {
        if (candidateProducts.isNotEmpty()) {
            val safeIdx = sampleIndex.coerceIn(0, candidateProducts.size - 1)
            candidateProducts[safeIdx]
        } else {
            products.firstOrNull { it.barcode.isNotBlank() }
        }
    }
    val samplePriceString = remember(sampleProd, priceTier, showUnit, showBanglaDigits) {
        if (sampleProd != null) {
            formatProductCodeLabelPriceString(
                tier = priceTier,
                sellingPrice = sampleProd.sellingPrice,
                mrpPrice = sampleProd.mrpPrice,
                wholesalePrice = sampleProd.wholesalePrice,
                showUnit = showUnit,
                showBanglaDigits = showBanglaDigits
            )
        } else ""
    }
    var sampleBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        sampleProd, samplePriceString, codeType, labelSize, showPrice, showCodeNumber,
        customWidthMm, customHeightMm, productNameTextScale, priceTextScale,
        codeNumberTextScale, showStoreName, effectiveStoreName, storeNameTextScale,
        showDateTracking, useProductDates, mfgOrPkgType, mfgOrPkgDate, expDate, dateTrackingTextScale, barcodeGraphicScale,
        extraFieldTitle, extraFieldValue
    ) {
        val old = sampleBitmap
        val newBmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            if (sampleProd != null) {
                val sampleMfgType = if (useProductDates) (sampleProd.mfgOrPkgType ?: "Mfg") else mfgOrPkgType
                val sampleMfgDate = if (useProductDates) sampleProd.mfgOrPkgDate.orEmpty() else mfgOrPkgDate
                val sampleExpDate = if (useProductDates) sampleProd.expiryDate.orEmpty() else expDate
                generateProductCodeLabelBitmap(
                    productName = sampleProd.name,
                    barcode = sampleProd.barcode,
                    formattedPrice = samplePriceString,
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
                    storeName = effectiveStoreName,
                    storeNameTextScale = storeNameTextScale,
                    showDateTracking = showDateTracking,
                    mfgOrPkgType = sampleMfgType,
                    mfgOrPkgDate = sampleMfgDate,
                    expDate = sampleExpDate,
                    dateTrackingTextScale = dateTrackingTextScale,
                    barcodeGraphicScale = barcodeGraphicScale,
                    showExtraField = extraFieldTitle.isNotBlank() || extraFieldValue.isNotBlank(),
                    extraFieldLabel = extraFieldTitle,
                    extraFieldValue = extraFieldValue,
                    dpi = invoiceSettings.labelDpi,
                    showBanglaDigits = showBanglaDigits
                )
            } else null
        }
        sampleBitmap = newBmp
        if (old != null && !old.isRecycled && old != newBmp) {
            old.recycle()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sampleBitmap?.recycle()
        }
    }

    val isBatchSampleDense = remember(sampleProd?.barcode, codeType, effectiveWidthMm, invoiceSettings.labelDpi) {
        sampleProd?.barcode?.let { isBarcodeTooDenseForLabel(it, codeType, effectiveWidthMm, invoiceSettings.labelDpi) } ?: false
    }

    val currentStyle = remember(
        codeType, labelSize, customWidthMm, customHeightMm, showPrice, priceTier,
        showUnit, showBanglaDigits, showCodeNumber, showStoreName, effectiveStoreName,
        showDateTracking, productNameTextScale, priceTextScale, codeNumberTextScale,
        storeNameTextScale, dateTrackingTextScale, barcodeGraphicScale,
        extraFieldTitle, extraFieldValue
    ) {
        LabelStyleSettings(
            labelSize = labelSize,
            customWidthMm = customWidthMm,
            customHeightMm = customHeightMm,
            showPrice = showPrice,
            priceTier = priceTier,
            showUnit = showUnit,
            showBanglaDigits = showBanglaDigits,
            showCodeNumber = showCodeNumber,
            showStoreName = showStoreName,
            storeName = effectiveStoreName,
            showDateTracking = showDateTracking,
            productNameTextScale = productNameTextScale,
            priceTextScale = priceTextScale,
            codeNumberTextScale = codeNumberTextScale,
            storeNameTextScale = storeNameTextScale,
            dateTrackingTextScale = dateTrackingTextScale,
            barcodeGraphicScale = barcodeGraphicScale,
            showExtraField = extraFieldTitle.isNotBlank() || extraFieldValue.isNotBlank(),
            extraFieldLabel = extraFieldTitle,
            extraFieldValue = extraFieldValue
        )
    }

    suspend fun executeBatchDirectPrint(items: List<ProductLabelItem>) {
        try {
            val mode = invoiceSettings.labelPrintMode.trim().uppercase()
            val labelTarget = invoiceSettings.resolvedLabelPrinterTarget()
            val receiptTarget = invoiceSettings.resolvedReceiptPrinterTarget()

            val goDirect = mode != "SYSTEM" && labelTarget !is PrinterTarget.None && labelTarget.identifier.isNotBlank()

            if (goDirect && isPrinterConflict(labelTarget, receiptTarget)) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.direct_print_error_label_same_as_receipt), Toast.LENGTH_LONG).show()
                }
                return
            }

            if (goDirect) {
                var totalSent = 0
                var failure: DirectPrintResult? = null
                try {
                    val safeAcross = invoiceSettings.labelLabelsAcross.coerceIn(1, 3)
                    val (payloads, payloadCopies) = if (safeAcross > 1) {
                        val payloadsList = mutableListOf<ByteArray>()
                        val copiesList = mutableListOf<Int>()
                        val currentRowBitmaps = mutableListOf<Bitmap>()
                        val activeBitmaps = mutableSetOf<Bitmap>()
                        try {
                            for ((idx, item) in items.withIndex()) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                withContext(Dispatchers.Main) {
                                    printingProgressText = context.getString(R.string.batch_print_printing, idx + 1, items.size)
                                }
                                val itemMfgType = item.mfgOrPkgType.ifBlank { "Mfg" }
                                val itemMfgDate = item.mfgOrPkgDate
                                val itemExpDate = item.expDate
                                val itemExtraLabel = item.extraFieldLabel.ifBlank { extraFieldTitle }
                                val itemExtraVal = item.extraFieldValue.ifBlank { extraFieldValue }
                                val bitmap = generateProductCodeLabelBitmap(
                                    productName = item.productName,
                                    barcode = item.barcode,
                                    formattedPrice = item.formattedPrice,
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
                                    storeName = effectiveStoreName,
                                    storeNameTextScale = storeNameTextScale,
                                    showDateTracking = showDateTracking,
                                    mfgOrPkgType = itemMfgType,
                                    mfgOrPkgDate = itemMfgDate,
                                    expDate = itemExpDate,
                                    dateTrackingTextScale = dateTrackingTextScale,
                                    barcodeGraphicScale = barcodeGraphicScale,
                                    showExtraField = extraFieldTitle.isNotBlank() || extraFieldValue.isNotBlank(),
                                    extraFieldLabel = itemExtraLabel,
                                    extraFieldValue = itemExtraVal,
                                    dpi = invoiceSettings.labelDpi,
                                    showBanglaDigits = showBanglaDigits
                                )
                                if (bitmap != null) {
                                    activeBitmaps.add(bitmap)
                                    var remainingCopies = item.quantity
                                    while (remainingCopies > 0) {
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        currentRowBitmaps.add(bitmap)
                                        remainingCopies--
                                        if (currentRowBitmaps.size == safeAcross) {
                                            val (compositeBitmap, totalRowWidthMm) = composeMultiUpRowBitmap(
                                                rowBitmaps = currentRowBitmaps,
                                                labelsAcross = safeAcross,
                                                columnGapMm = invoiceSettings.labelColumnGapMm,
                                                labelWidthMm = effectiveWidthMm,
                                                labelHeightMm = effectiveHeightMm,
                                                dpi = invoiceSettings.labelDpi
                                            )
                                            val payload = buildTsplPayload(
                                                labelBitmap = compositeBitmap,
                                                widthMm = totalRowWidthMm,
                                                heightMm = effectiveHeightMm,
                                                gapMm = invoiceSettings.labelGapMm,
                                                copies = 1,
                                                darkness = invoiceSettings.labelDarkness,
                                                dpi = invoiceSettings.labelDpi,
                                                speed = invoiceSettings.labelPrintSpeed,
                                                xOffsetDots = invoiceSettings.labelXOffsetDots,
                                                yOffsetDots = invoiceSettings.labelYOffsetDots,
                                                direction = invoiceSettings.labelDirection,
                                                paperType = invoiceSettings.labelPaperType
                                            )
                                            if (compositeBitmap != currentRowBitmaps.firstOrNull()) {
                                                compositeBitmap.recycle()
                                            }
                                            payloadsList.add(payload)
                                            copiesList.add(currentRowBitmaps.size)
                                            currentRowBitmaps.clear()
                                            val iterator = activeBitmaps.iterator()
                                            while (iterator.hasNext()) {
                                                val b = iterator.next()
                                                if (b != bitmap || remainingCopies == 0) {
                                                    if (!currentRowBitmaps.contains(b)) {
                                                        b.recycle()
                                                        iterator.remove()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (currentRowBitmaps.isNotEmpty()) {
                                val (compositeBitmap, totalRowWidthMm) = composeMultiUpRowBitmap(
                                    rowBitmaps = currentRowBitmaps,
                                    labelsAcross = safeAcross,
                                    columnGapMm = invoiceSettings.labelColumnGapMm,
                                    labelWidthMm = effectiveWidthMm,
                                    labelHeightMm = effectiveHeightMm,
                                    dpi = invoiceSettings.labelDpi
                                )
                                val payload = buildTsplPayload(
                                    labelBitmap = compositeBitmap,
                                    widthMm = totalRowWidthMm,
                                    heightMm = effectiveHeightMm,
                                    gapMm = invoiceSettings.labelGapMm,
                                    copies = 1,
                                    darkness = invoiceSettings.labelDarkness,
                                    dpi = invoiceSettings.labelDpi,
                                    speed = invoiceSettings.labelPrintSpeed,
                                    xOffsetDots = invoiceSettings.labelXOffsetDots,
                                    yOffsetDots = invoiceSettings.labelYOffsetDots,
                                    direction = invoiceSettings.labelDirection,
                                    paperType = invoiceSettings.labelPaperType
                                )
                                if (compositeBitmap != currentRowBitmaps.firstOrNull()) {
                                    compositeBitmap.recycle()
                                }
                                payloadsList.add(payload)
                                copiesList.add(currentRowBitmaps.size)
                                currentRowBitmaps.clear()
                            }
                            payloadsList to copiesList
                        } finally {
                            activeBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                            activeBitmaps.clear()
                        }
                    } else {
                        val singlePayloads = mutableListOf<ByteArray>()
                        val singleCopies = mutableListOf<Int>()
                        for ((idx, item) in items.withIndex()) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            withContext(Dispatchers.Main) {
                                printingProgressText = context.getString(R.string.batch_print_printing, idx + 1, items.size)
                            }
                            val itemMfgType = item.mfgOrPkgType.ifBlank { "Mfg" }
                            val itemMfgDate = item.mfgOrPkgDate
                            val itemExpDate = item.expDate
                            val itemExtraLabel = item.extraFieldLabel.ifBlank { extraFieldTitle }
                            val itemExtraVal = item.extraFieldValue.ifBlank { extraFieldValue }
                            val bitmap = generateProductCodeLabelBitmap(
                                productName = item.productName,
                                barcode = item.barcode,
                                formattedPrice = item.formattedPrice,
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
                                storeName = effectiveStoreName,
                                storeNameTextScale = storeNameTextScale,
                                showDateTracking = showDateTracking,
                                mfgOrPkgType = itemMfgType,
                                mfgOrPkgDate = itemMfgDate,
                                expDate = itemExpDate,
                                dateTrackingTextScale = dateTrackingTextScale,
                                barcodeGraphicScale = barcodeGraphicScale,
                                showExtraField = extraFieldTitle.isNotBlank() || extraFieldValue.isNotBlank(),
                                extraFieldLabel = itemExtraLabel,
                                extraFieldValue = itemExtraVal,
                                dpi = invoiceSettings.labelDpi,
                                showBanglaDigits = showBanglaDigits
                            )
                            if (bitmap != null) {
                                try {
                                    val payload = buildTsplPayload(
                                        labelBitmap = bitmap,
                                        widthMm = effectiveWidthMm,
                                        heightMm = effectiveHeightMm,
                                        gapMm = invoiceSettings.labelGapMm,
                                        copies = item.quantity,
                                        darkness = invoiceSettings.labelDarkness,
                                        dpi = invoiceSettings.labelDpi,
                                        speed = invoiceSettings.labelPrintSpeed,
                                        xOffsetDots = invoiceSettings.labelXOffsetDots,
                                        yOffsetDots = invoiceSettings.labelYOffsetDots,
                                        direction = invoiceSettings.labelDirection,
                                        paperType = invoiceSettings.labelPaperType
                                    )
                                    singlePayloads.add(payload)
                                    singleCopies.add(item.quantity)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                        singlePayloads to singleCopies
                    }

                    if (payloads.isNotEmpty()) {
                        val batchRes = com.example.techyfipos.printer.printLabelPayloadsBatch(
                            context = context,
                            target = labelTarget,
                            payloads = payloads,
                            copiesPerPayload = payloadCopies,
                            onProgress = { sent, total ->
                                totalSentSoFar = sent
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    printingProgressText = context.getString(R.string.batch_print_printing, sent, total)
                                }
                            }
                        )
                        totalSent = batchRes.successfulCount
                        if (batchRes.result !is DirectPrintResult.Success) {
                            failure = batchRes.result
                        }
                    } else {
                        failure = DirectPrintResult.Failed(context.getString(R.string.batch_print_bitmaps_failed))
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    android.util.Log.e("ProductCodePrintDialogs", "Batch label print failed", e)
                    failure = DirectPrintResult.Failed(e.localizedMessage ?: "Unknown error")
                }

                if (failure != null) {
                    var sentCounter = totalSent
                    val remaining = mutableListOf<ProductLabelItem>()
                    for (item in items) {
                        if (sentCounter >= item.quantity) {
                            sentCounter -= item.quantity
                        } else if (sentCounter > 0) {
                            val remQty = item.quantity - sentCounter
                            sentCounter = 0
                            remaining.add(item.copy(quantity = remQty))
                        } else {
                            remaining.add(item)
                        }
                    }
                    failedRemainingItems = if (remaining.isNotEmpty()) remaining else null
                } else {
                    failedRemainingItems = null
                    lastPrintedItems = items
                }

                val decision = decidePrintRouting(mode, labelTarget.identifier, failure, totalSent)
                when (decision) {
                    RoutingDecision.DIRECT_OK -> {
                        withContext(Dispatchers.Main) {
                            val msg = context.getString(R.string.batch_print_success_fmt, totalSent)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    RoutingDecision.SHOW_ERROR -> {
                        if (totalSent > 0) {
                            withContext(Dispatchers.Main) {
                                val partialMsg = context.getString(R.string.batch_print_partial_failure, totalSent)
                                Toast.makeText(context, partialMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        failure?.let { showDirectPrintToast(context, it) }
                    }
                    RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG -> {
                        if (totalSent > 0) {
                            withContext(Dispatchers.Main) {
                                val partialMsg = context.getString(R.string.batch_print_partial_failure, totalSent)
                                Toast.makeText(context, partialMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                        failure?.let { showDirectPrintToast(context, it) }
                        val pdfResult = cacheBatchProductCodeLabelsPdf(
                            context = context,
                            items = items,
                            codeType = codeType,
                            style = currentStyle
                        )
                        withContext(Dispatchers.Main) {
                            if (pdfResult != null) {
                                val opened = printPdfDocument(
                                    context = activity ?: context,
                                    pdfFile = pdfResult.first,
                                    jobName = context.getString(R.string.print_job_batch_labels),
                                    widthMm = effectiveWidthMm,
                                    heightMm = effectiveHeightMm
                                )
                                if (opened) onDismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_failed_batch_labels_pdf), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    RoutingDecision.SYSTEM_DIALOG -> {
                        val pdfResult = cacheBatchProductCodeLabelsPdf(
                            context = context,
                            items = items,
                            codeType = codeType,
                            style = currentStyle
                        )
                        withContext(Dispatchers.Main) {
                            if (pdfResult != null) {
                                val opened = printPdfDocument(
                                    context = activity ?: context,
                                    pdfFile = pdfResult.first,
                                    jobName = context.getString(R.string.print_job_batch_labels),
                                    widthMm = effectiveWidthMm,
                                    heightMm = effectiveHeightMm
                                )
                                if (opened) onDismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_failed_batch_labels_pdf), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                val decision = decidePrintRouting(mode, "", DirectPrintResult.NoAddress)
                when (decision) {
                    RoutingDecision.SHOW_ERROR -> {
                        showDirectPrintToast(context, DirectPrintResult.NoAddress)
                    }
                    RoutingDecision.SYSTEM_DIALOG,
                    RoutingDecision.SHOW_ERROR_THEN_SYSTEM_DIALOG -> {
                        val pdfResult = cacheBatchProductCodeLabelsPdf(
                            context = context,
                            items = items,
                            codeType = codeType,
                            style = currentStyle
                        )
                        withContext(Dispatchers.Main) {
                            if (pdfResult != null) {
                                val opened = printPdfDocument(
                                    context = activity ?: context,
                                    pdfFile = pdfResult.first,
                                    jobName = context.getString(R.string.print_job_batch_labels),
                                    widthMm = effectiveWidthMm,
                                    heightMm = effectiveHeightMm
                                )
                                if (opened) onDismiss()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_failed_batch_labels_pdf), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    RoutingDecision.DIRECT_OK -> {}
                }
            }
        } finally {
            val executingJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                if (printJob == null || printJob === executingJob) {
                    isPrinting = false
                    printingProgressText = ""
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating && !isPrinting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = !isPrinting && !isGenerating
        )
    ) {
        BackHandler(enabled = isPrinting || isGenerating) {
            // Prevent back dismiss while printing or generating
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .trackUserActivity(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                stringResource(R.string.batch_print_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isGenerating && !isPrinting,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                // Pinned Sample Label Preview Card (LBL-21)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.batch_print_preview_sample),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (candidateProducts.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            sampleIndex = if (sampleIndex > 0) sampleIndex - 1 else candidateProducts.size - 1
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Icon(
                                            Icons.Default.NavigateBefore,
                                            contentDescription = stringResource(R.string.batch_preview_prev_sample),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        "${(sampleIndex.coerceIn(0, candidateProducts.size - 1)) + 1}/${candidateProducts.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = {
                                            sampleIndex = (sampleIndex + 1) % candidateProducts.size
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        @Suppress("DEPRECATION")
                                        Icon(
                                            Icons.Default.NavigateNext,
                                            contentDescription = stringResource(R.string.batch_preview_next_sample),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        sampleProd?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val currentSampleBmp = sampleBitmap
                            if (currentSampleBmp != null) {
                                Image(
                                    bitmap = currentSampleBmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.product_code_image_preview),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 70.dp, max = 120.dp)
                                        .background(Color.White),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    stringResource(R.string.batch_print_no_products),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isBatchSampleDense) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.barcode_too_dense_warning),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(onClick = { codeTypeName = ProductCodeType.QR_CODE.name }) {
                                Text(stringResource(R.string.switch_to_qr), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                failedRemainingItems?.let { remaining ->
                    val remainingQty = remaining.sumOf { it.quantity }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.batch_print_failed_banner, totalSentSoFar, remainingQty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    isPrinting = true
                                    totalSentSoFar = 0
                                    printJob = scope.launch(Dispatchers.IO) {
                                        executeBatchDirectPrint(remaining)
                                    }
                                },
                                enabled = !isPrinting,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    stringResource(R.string.batch_print_retry_remaining, remainingQty),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Scrollable Settings
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Templates Row (LBL-18)
                    Text(
                        stringResource(R.string.label_presets_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = remember { getDefaultLabelPresets() }
                        presets.forEach { preset ->
                            val presetName = preset.nameRes?.let { stringResource(it) } ?: preset.name
                            FilterChip(
                                selected = false,
                                onClick = {
                                    labelSizeName = preset.style.labelSize.name
                                    customWidthInput = preset.style.customWidthMm.toString().removeSuffix(".0")
                                    customHeightInput = preset.style.customHeightMm.toString().removeSuffix(".0")
                                    showPrice = preset.style.showPrice
                                    priceTierName = preset.style.priceTier.name
                                    showCodeNumber = preset.style.showCodeNumber
                                    showStoreName = preset.style.showStoreName
                                    showDateTracking = preset.style.showDateTracking
                                    extraFieldTitle = preset.style.extraFieldLabel
                                    extraFieldValue = preset.style.extraFieldValue
                                    productNameTextScale = preset.style.productNameTextScale
                                    priceTextScale = preset.style.priceTextScale
                                    codeNumberTextScale = preset.style.codeNumberTextScale
                                    storeNameTextScale = preset.style.storeNameTextScale
                                    dateTrackingTextScale = preset.style.dateTrackingTextScale
                                    barcodeGraphicScale = preset.style.barcodeGraphicScale
                                    Toast.makeText(context, context.getString(R.string.label_preset_saved_toast), Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(presetName, fontSize = 11.5.sp) },
                                leadingIcon = { Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }

                    // Scope: All vs In-Stock Only vs Custom
                    Text(
                        stringResource(R.string.batch_print_scope),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val isBn = isAppBangla()
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = scopeMode == BatchPrintScope.ALL,
                            onClick = { scopeMode = BatchPrintScope.ALL },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Text(
                                stringResource(R.string.batch_print_scope_all),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            val allCount = products.count { it.barcode.isNotBlank() }
                            Text(
                                "(${if (isBn) toBanglaDigits(allCount.toString()) else allCount})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SegmentedButton(
                        selected = scopeMode == BatchPrintScope.IN_STOCK,
                        onClick = { scopeMode = BatchPrintScope.IN_STOCK },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Text(
                                stringResource(R.string.batch_print_scope_in_stock),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            val inStockCount = products.count { it.barcode.isNotBlank() && it.stockQty > 0 }
                            Text(
                                "(${if (isBn) toBanglaDigits(inStockCount.toString()) else inStockCount})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SegmentedButton(
                        selected = scopeMode == BatchPrintScope.CUSTOM,
                        onClick = {
                            scopeMode = BatchPrintScope.CUSTOM
                            if (customSelectedBarcodes.isEmpty()) {
                                showProductPickerSheet = true
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 3.dp)
                        ) {
                            Text(
                                stringResource(R.string.batch_print_scope_custom),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                            val customCount = customSelectedBarcodes.size
                            Text(
                                "(${if (isBn) toBanglaDigits(customCount.toString()) else customCount})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = scopeMode == BatchPrintScope.CUSTOM) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProductPickerSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Checklist,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (customSelectedBarcodes.isEmpty()) {
                                        stringResource(R.string.batch_print_no_custom_selected)
                                    } else {
                                        stringResource(R.string.batch_print_select_products_btn, customSelectedBarcodes.size)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (customSelectedBarcodes.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Copies Mode (LBL-15)
                Text(
                    stringResource(R.string.batch_print_copies_mode),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = copiesMode == "FIXED",
                        onClick = { copiesMode = "FIXED" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.batch_print_fixed_copies), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = copiesMode == "STOCK",
                        onClick = { copiesMode = "STOCK" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.batch_print_stock_qty), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    SegmentedButton(
                        selected = copiesMode == "PER_PRODUCT",
                        onClick = {
                            copiesMode = "PER_PRODUCT"
                            if (customQuantities.isEmpty()) {
                                customQuantities = candidateProducts.associate { it.barcode to 1 }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.batch_print_custom_per_product), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(visible = copiesMode == "FIXED") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CopiesStepper(
                                copies = batchCopies,
                                onCopiesChange = { batchCopies = it },
                                onErrorChange = { isBatchCopiesError = it }
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = copiesMode == "PER_PRODUCT") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showProductPickerSheet = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.batch_print_custom_per_product),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.batch_picker_qty_title) + ": " + candidateProducts.sumOf { customQuantities[it.barcode] ?: 1 } + " " + stringResource(R.string.product_code_total_pages_labels_label).lowercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            TextButton(onClick = { showProductPickerSheet = true }) {
                                Text(stringResource(R.string.edit), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Code Type (LBL-16)
                Text(
                    stringResource(R.string.batch_print_code_type),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = codeType == ProductCodeType.BARCODE,
                        onClick = { codeTypeName = ProductCodeType.BARCODE.name },
                        label = { Text("Code 128") },
                        leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = codeType == ProductCodeType.EAN_13,
                        onClick = { codeTypeName = ProductCodeType.EAN_13.name },
                        label = { Text(stringResource(R.string.code_type_ean13)) },
                        leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = codeType == ProductCodeType.EAN_8,
                        onClick = { codeTypeName = ProductCodeType.EAN_8.name },
                        label = { Text(stringResource(R.string.code_type_ean8)) },
                        leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = codeType == ProductCodeType.CODE_39,
                        onClick = { codeTypeName = ProductCodeType.CODE_39.name },
                        label = { Text(stringResource(R.string.code_type_code39)) },
                        leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = codeType == ProductCodeType.QR_CODE,
                        onClick = { codeTypeName = ProductCodeType.QR_CODE.name },
                        label = { Text(stringResource(R.string.qr_code)) },
                        leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // Label Size Dropdown (Supports Custom Size)
                Text(
                    stringResource(R.string.label_size),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { labelSizeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            labelSize.getLocalizedName(context),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = labelSizeMenuExpanded,
                        onDismissRequest = { labelSizeMenuExpanded = false }
                    ) {
                        ProductCodeLabelSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text = { Text(size.getLocalizedName(context)) },
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customWidthInput,
                            onValueChange = { value ->
                                if (value.matches(Regex("\\d{0,3}(\\.\\d?)?"))) customWidthInput = value
                            },
                            label = { Text(stringResource(R.string.products_width_mm_label)) },
                            isError = isCustomWidthError,
                            supportingText = {
                                if (isCustomWidthError) {
                                    Text(stringResource(R.string.custom_width_range_error), color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(stringResource(R.string.products_width_range_hint))
                                }
                            },
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
                            isError = isCustomHeightError,
                            supportingText = {
                                if (isCustomHeightError) {
                                    Text(stringResource(R.string.custom_height_range_error), color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(stringResource(R.string.products_height_range_hint))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Price Tier Switcher Card
                val batchSellingDisplay = if (sampleProd != null) {
                    val base = formatTaka(sampleProd.sellingPrice)
                    if (showBanglaDigits) toBanglaDigits(base) else base
                } else "-"
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

                if (showPrice) {
                    val missingMrpCount = if (priceTier == ProductCodePriceTier.DUAL || priceTier == ProductCodePriceTier.MRP) {
                        candidateProducts.count { it.mrpPrice <= 0.0 }
                    } else 0
                    val missingWholesaleCount = if (priceTier == ProductCodePriceTier.WHOLESALE) {
                        candidateProducts.count { it.wholesalePrice <= 0.0 }
                    } else 0

                    if (missingMrpCount > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.batch_print_mrp_missing_warning, missingMrpCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    } else if (missingWholesaleCount > 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.batch_print_wholesale_missing_warning, missingWholesaleCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Show Code Number Switch
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
                        Text(stringResource(R.string.show_code_number), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Show Store Name Checkbox and Editable Store Name Input
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
                        placeholder = { Text(configuredStoreName.ifBlank { "Techyfi POS" }) },
                        supportingText = { Text(stringResource(R.string.products_store_name_supporting_text)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Show Date Tracking Checkbox and Inputs
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
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { useProductDates = !useProductDates }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = useProductDates, onCheckedChange = { useProductDates = it })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.batch_print_use_product_dates),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (useProductDates) {
                                        Text(
                                            text = stringResource(R.string.batch_print_use_product_dates_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = !useProductDates) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
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
                                            label = { Text(stringResource(R.string.products_mfg_abbr), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            leadingIcon = { Animated3DFactoryIcon() },
                                            modifier = Modifier.height(32.dp)
                                        )
                                        FilterChip(
                                            selected = mfgOrPkgType == "Pkg",
                                            onClick = { mfgOrPkgType = "Pkg" },
                                            label = { Text(stringResource(R.string.products_pkg_abbr), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
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
                    }
                }

                // Additional Field Section (LBL-18)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_extra_field_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = extraFieldTitle,
                                onValueChange = { extraFieldTitle = it.take(30) },
                                label = { Text(stringResource(R.string.label_extra_field_name), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = extraFieldValue,
                                onValueChange = { extraFieldValue = it.take(40) },
                                label = { Text(stringResource(R.string.label_extra_field_value), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Collapsible Label Styling & Sizing Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedStyling = !showAdvancedStyling },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.batch_print_advanced_settings),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                if (showAdvancedStyling) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showAdvancedStyling) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.reset_size_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            productNameTextScale = 1.0f
                                            priceTextScale = 1.0f
                                            codeNumberTextScale = 1.0f
                                            storeNameTextScale = 1.0f
                                            dateTrackingTextScale = 1.0f
                                            barcodeGraphicScale = 1.0f
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.reset_all_sizes), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_product_name),
                                    scale = productNameTextScale,
                                    onScaleChange = { productNameTextScale = it },
                                    onReset = { productNameTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_price),
                                    scale = priceTextScale,
                                    enabled = showPrice,
                                    onScaleChange = { priceTextScale = it },
                                    onReset = { priceTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_code_number),
                                    scale = codeNumberTextScale,
                                    enabled = showCodeNumber,
                                    onScaleChange = { codeNumberTextScale = it },
                                    onReset = { codeNumberTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_store_name),
                                    scale = storeNameTextScale,
                                    enabled = showStoreName && storeNameInput.isNotBlank(),
                                    onScaleChange = { storeNameTextScale = it },
                                    onReset = { storeNameTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_date_tracking_text),
                                    scale = dateTrackingTextScale,
                                    enabled = showDateTracking && (mfgOrPkgDate.isNotBlank() || expDate.isNotBlank()),
                                    onScaleChange = { dateTrackingTextScale = it },
                                    onReset = { dateTrackingTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_barcode_graphic_height),
                                    scale = barcodeGraphicScale,
                                    enabled = true,
                                    onScaleChange = { barcodeGraphicScale = it },
                                    onReset = { barcodeGraphicScale = 1.0f }
                                )
                            }
                        }
                    }
                }

                // Summary Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.product_code_total_pages_labels_label),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                stringResource(R.string.product_code_labels_count_fmt, totalLabelCount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val safeAcross = invoiceSettings.labelLabelsAcross.coerceIn(1, 3)
                        val totalMultiUpWidthMm = com.example.techyfipos.printer.calculateMultiUpRowWidthMm(
                            labelsAcross = safeAcross,
                            columnGapMm = invoiceSettings.labelColumnGapMm,
                            labelWidthMm = effectiveWidthMm
                        )
                        val isHeadWidthExceeded = safeAcross > 1 && totalMultiUpWidthMm > 108f
                        if (isHeadWidthExceeded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(
                                    R.string.batch_print_head_width_exceeded_warning,
                                    String.format(java.util.Locale.US, "%.1f", totalMultiUpWidthMm)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (isBatchTruncated) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.batch_print_capped_500_note, unconstrainedLabelCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } // End of scrollable settings Column

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if ((isPrinting || isGenerating) && (printingProgressText.isNotBlank() || isGenerating)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (printingProgressText.isNotBlank()) printingProgressText else stringResource(R.string.batch_print_generating),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!isPrinting && !isGenerating && lastPrintedItems != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.batch_print_reprint_last_success,
                                lastPrintedItems?.sumOf { it.quantity } ?: 0
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        OutlinedButton(
                            onClick = {
                                lastPrintedItems?.let { lastItems ->
                                    isPrinting = true
                                    totalSentSoFar = 0
                                    printJob = scope.launch(Dispatchers.IO) {
                                        executeBatchDirectPrint(lastItems)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.batch_print_reprint_last), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Action Buttons: Direct Print and Share PDF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                    Button(
                        onClick = {
                            if (candidateProducts.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.batch_print_no_products), Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (isBatchTruncated) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.batch_print_capped_500_toast, unconstrainedLabelCount),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            isPrinting = true
                            totalSentSoFar = 0
                            printJob = scope.launch(Dispatchers.IO) {
                                val batchBuild = buildBatchLabelItemsResult(
                                    candidateProducts = candidateProducts,
                                    copiesModeByStock = copiesMode == "STOCK",
                                    batchCopies = batchCopies,
                                    priceTier = priceTier,
                                    showUnit = showUnit,
                                    showBanglaDigits = showBanglaDigits,
                                    useProductDates = useProductDates,
                                    mfgOrPkgType = mfgOrPkgType,
                                    mfgOrPkgDate = mfgOrPkgDate,
                                    expDate = expDate,
                                    customQuantities = if (copiesMode == "PER_PRODUCT") customQuantities else emptyMap(),
                                    maxTotalLabels = 500,
                                    isCustomPerProduct = copiesMode == "PER_PRODUCT",
                                    cutFormat = context.getString(R.string.product_label_cut_count_fmt)
                                )
                                if (batchBuild.cutProducts.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        val cutNames = batchBuild.cutProducts.take(3).joinToString(", ")
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.batch_print_cut_products_toast, batchBuild.cutProducts.size, cutNames),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                                executeBatchDirectPrint(batchBuild.items)
                            }
                        },
                        modifier = Modifier.weight(1.2f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isGenerating && !isPrinting && candidateProducts.isNotEmpty() && !isBatchCopiesError && !isCustomWidthError && !isCustomHeightError
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.batch_print_direct_print_btn), fontWeight = FontWeight.Bold)
                    }

                    if (isPrinting || isGenerating) {
                        Button(
                            onClick = {
                                val sent = totalSentSoFar
                                val wasPrinting = isPrinting
                                printJob?.cancel()
                                isPrinting = false
                                isGenerating = false
                                printingProgressText = ""
                                val msg = if (wasPrinting && sent > 0) {
                                    context.getString(R.string.batch_print_cancelled_with_count, sent)
                                } else {
                                    context.getString(R.string.batch_print_cancelled)
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.batch_print_cancel),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (candidateProducts.isEmpty()) {
                                    Toast.makeText(context, context.getString(R.string.batch_print_no_products), Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                if (isBatchTruncated) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.batch_print_capped_500_toast, unconstrainedLabelCount),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                isGenerating = true
                                printJob = scope.launch(Dispatchers.IO) {
                                    try {
                                        val batchBuild = buildBatchLabelItemsResult(
                                            candidateProducts = candidateProducts,
                                            copiesModeByStock = copiesMode == "STOCK",
                                            batchCopies = batchCopies,
                                            priceTier = priceTier,
                                            showUnit = showUnit,
                                            showBanglaDigits = showBanglaDigits,
                                            useProductDates = useProductDates,
                                            mfgOrPkgType = mfgOrPkgType,
                                            mfgOrPkgDate = mfgOrPkgDate,
                                            expDate = expDate,
                                            customQuantities = if (copiesMode == "PER_PRODUCT") customQuantities else emptyMap(),
                                            maxTotalLabels = 500,
                                            isCustomPerProduct = copiesMode == "PER_PRODUCT",
                                            cutFormat = context.getString(R.string.product_label_cut_count_fmt)
                                        )
                                        if (batchBuild.cutProducts.isNotEmpty()) {
                                            withContext(Dispatchers.Main) {
                                                val cutNames = batchBuild.cutProducts.take(3).joinToString(", ")
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.batch_print_cut_products_toast, batchBuild.cutProducts.size, cutNames),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                        val result = cacheBatchProductCodeLabelsPdf(
                                            context = context,
                                            items = batchBuild.items,
                                            codeType = codeType,
                                            style = currentStyle
                                        )
                                        withContext(Dispatchers.Main) {
                                            if (result != null) {
                                                shareProductCodeLabelPdf(context, result.second, context.getString(R.string.batch_labels_share_subject))
                                                onDismiss()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.toast_failed_batch_labels_pdf), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } finally {
                                        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                                            isGenerating = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isGenerating && !isPrinting && candidateProducts.isNotEmpty() && !isBatchCopiesError && !isCustomWidthError && !isCustomHeightError
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.batch_print_share_pdf_btn), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showProductPickerSheet) {
        BatchProductPickerSheet(
            allProducts = products,
            selectedBarcodes = customSelectedBarcodes,
            initialQuantities = customQuantities,
            onSelectionChange = { customSelectedBarcodes = it },
            onSelectionAndQuantitiesChange = { selected, quantities ->
                customSelectedBarcodes = selected
                customQuantities = quantities
            },
            onDismiss = { showProductPickerSheet = false }
        )
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
    val invoiceSettings by remember(context) { SettingsDataStore(context).invoiceSettingsDataFlow }
        .collectAsStateWithLifecycle(initialValue = InvoiceSettingsData())
    val configuredStoreName by remember(context) { SettingsDataStore(context).shopNameFlow }
        .collectAsStateWithLifecycle(initialValue = "")
    val preferences = remember {
        context.getSharedPreferences("product_code_image_settings", android.content.Context.MODE_PRIVATE)
    }
    val hadSavedStoreName = remember { preferences.contains("store_name") }
    val recommendedCodeType = remember(product.barcode) { getRecommendedCodeType(product.barcode) }
    var codeTypeName by rememberSaveable {
        val stored = preferences.getString("code_type", null)
        mutableStateOf(stored ?: recommendedCodeType.name)
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
    var showUnit by rememberSaveable { mutableStateOf(preferences.getBoolean("show_unit", preferences.getBoolean("show_price_unit", false))) }
    var showBanglaDigits by rememberSaveable { mutableStateOf(preferences.getBoolean("show_bangla_digits", false)) }
    var showCodeNumber by rememberSaveable { mutableStateOf(preferences.getBoolean("show_code_number", true)) }
    var showStoreName by rememberSaveable { mutableStateOf(preferences.getBoolean("show_store_name", true)) }
    var showDateTracking by rememberSaveable { mutableStateOf(preferences.getBoolean("show_date_tracking", false)) }
    var mfgOrPkgType by rememberSaveable { mutableStateOf(product.mfgOrPkgType ?: "Mfg") }
    var mfgOrPkgDate by rememberSaveable { mutableStateOf(product.mfgOrPkgDate ?: "") }
    var expDate by rememberSaveable { mutableStateOf(product.expiryDate ?: "") }
    var extraFieldTitle by rememberSaveable {
        mutableStateOf(preferences.getString("extra_field_title", preferences.getString("extra_field_label", "") ?: "") ?: "")
    }
    var extraFieldValue by rememberSaveable {
        mutableStateOf(preferences.getString("extra_field_value", "") ?: "")
    }

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
    var showAdvancedStyling by rememberSaveable { mutableStateOf(false) }
    var actionStatus by remember { mutableStateOf<ActionStatus?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var isSingleCopiesError by remember { mutableStateOf(false) }
    var singlePrintJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isSharingPdf by remember { mutableStateOf(false) }
    var sharePdfJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var printCopies by rememberSaveable { mutableIntStateOf(1) }

    val codeType = ProductCodeType.entries.firstOrNull { it.name == codeTypeName } ?: ProductCodeType.BARCODE
    val labelSize = ProductCodeLabelSize.entries.firstOrNull { it.name == labelSizeName }
        ?: ProductCodeLabelSize.SIZE_50_X_30
    val parsedCustomWidth = customWidthInput.toFloatOrNull()
    val isCustomWidthError = labelSize == ProductCodeLabelSize.CUSTOM && (parsedCustomWidth == null || parsedCustomWidth !in 15f..150f)
    val parsedCustomHeight = customHeightInput.toFloatOrNull()
    val isCustomHeightError = labelSize == ProductCodeLabelSize.CUSTOM && (parsedCustomHeight == null || parsedCustomHeight !in 10f..200f)

    val customWidthMm = parsedCustomWidth?.coerceIn(15f, 150f) ?: 50f
    val customHeightMm = parsedCustomHeight?.coerceIn(10f, 200f) ?: 30f
    val (selectedLabelWidthMm, selectedLabelHeightMm) = resolveLabelDimensionsMm(labelSize, customWidthMm, customHeightMm)
    val effectiveStoreName = storeNameInput.ifBlank { configuredStoreName }

    LaunchedEffect(configuredStoreName) {
        if (!hadSavedStoreName && configuredStoreName.isNotBlank() && storeNameInput.isBlank()) {
            storeNameInput = configuredStoreName
        }
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        product, codeType, labelSize, showPrice, priceTierName, showUnit, showBanglaDigits, showCodeNumber, showStoreName, effectiveStoreName,
        showDateTracking, mfgOrPkgType, mfgOrPkgDate, expDate,
        customWidthMm, customHeightMm, productNameTextScale, priceTextScale,
        codeNumberTextScale, storeNameTextScale, dateTrackingTextScale, barcodeGraphicScale,
        extraFieldTitle, extraFieldValue
    ) {
        val old = bitmap
        val newBmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
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
                storeName = effectiveStoreName,
                storeNameTextScale = storeNameTextScale,
                showDateTracking = showDateTracking,
                mfgOrPkgType = mfgOrPkgType,
                mfgOrPkgDate = mfgOrPkgDate,
                expDate = expDate,
                dateTrackingTextScale = dateTrackingTextScale,
                barcodeGraphicScale = barcodeGraphicScale,
                showExtraField = extraFieldTitle.isNotBlank() || extraFieldValue.isNotBlank(),
                extraFieldLabel = extraFieldTitle,
                extraFieldValue = extraFieldValue,
                dpi = invoiceSettings.labelDpi,
                showBanglaDigits = showBanglaDigits
            )
        }
        bitmap = newBmp
        if (old != null && !old.isRecycled && old != newBmp) {
            old.recycle()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bitmap?.recycle()
            singlePrintJob?.cancel()
            sharePdfJob?.cancel()
        }
    }

    LaunchedEffect(
        codeTypeName, labelSizeName, showPrice, priceTierName, showUnit, showBanglaDigits, showCodeNumber, showStoreName, storeNameInput,
        showDateTracking, mfgOrPkgType, mfgOrPkgDate, expDate,
        customWidthInput, customHeightInput, productNameTextScale, priceTextScale,
        codeNumberTextScale, storeNameTextScale, dateTrackingTextScale, barcodeGraphicScale,
        extraFieldTitle, extraFieldValue
    ) {
        val editor = preferences.edit()
            .putString("code_type", codeTypeName)
            .putString("label_size", labelSizeName)
            .putBoolean("show_price", showPrice)
            .putString("price_tier", priceTierName)
            .putBoolean("show_price_unit", showUnit)
            .putBoolean("show_unit", showUnit)
            .putBoolean("show_bangla_digits", showBanglaDigits)
            .putBoolean("show_code_number", showCodeNumber)
            .putBoolean("show_store_name", showStoreName)
            .putBoolean("show_date_tracking", showDateTracking)
            .putString("store_name", storeNameInput.trim())
            .putFloat("product_name_text_scale", productNameTextScale)
            .putFloat("price_text_scale", priceTextScale)
            .putFloat("code_number_text_scale", codeNumberTextScale)
            .putFloat("store_name_text_scale", storeNameTextScale)
            .putFloat("date_tracking_text_scale", dateTrackingTextScale)
            .putFloat("barcode_graphic_scale", barcodeGraphicScale)
            .putString("extra_field_title", extraFieldTitle.trim())
            .putString("extra_field_label", extraFieldTitle.trim())
            .putString("extra_field_value", extraFieldValue.trim())
        if (parsedCustomWidth != null && parsedCustomWidth in 15f..150f) {
            editor.putFloat("custom_width_mm", parsedCustomWidth)
        }
        if (parsedCustomHeight != null && parsedCustomHeight in 10f..200f) {
            editor.putFloat("custom_height_mm", parsedCustomHeight)
        }
        editor.apply()
        actionStatus = null
    }

    Dialog(
        onDismissRequest = { if (!isPrinting && !isSharingPdf) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = !isPrinting && !isSharingPdf
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .trackUserActivity(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
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
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isPrinting && !isSharingPdf
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss))
                    }
                }

                // Pinned Preview Card (LBL-21)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val currentBmp = bitmap
                        if (currentBmp != null) {
                            Image(
                                bitmap = currentBmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.product_code_image_preview),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 70.dp, max = 130.dp)
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

                val isSingleDense = remember(product.barcode, codeType, selectedLabelWidthMm, invoiceSettings.labelDpi) {
                    isBarcodeTooDenseForLabel(product.barcode, codeType, selectedLabelWidthMm, invoiceSettings.labelDpi)
                }
                if (isSingleDense) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.barcode_too_dense_warning),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(onClick = { codeTypeName = ProductCodeType.QR_CODE.name }) {
                                Text(stringResource(R.string.switch_to_qr), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Scrollable Settings
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Templates Row (LBL-18)
                    Text(
                        stringResource(R.string.label_presets_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = remember { getDefaultLabelPresets() }
                        presets.forEach { preset ->
                            val presetName = preset.nameRes?.let { stringResource(it) } ?: preset.name
                            FilterChip(
                                selected = false,
                                onClick = {
                                    labelSizeName = preset.style.labelSize.name
                                    customWidthInput = preset.style.customWidthMm.toString().removeSuffix(".0")
                                    customHeightInput = preset.style.customHeightMm.toString().removeSuffix(".0")
                                    showPrice = preset.style.showPrice
                                    priceTierName = preset.style.priceTier.name
                                    showCodeNumber = preset.style.showCodeNumber
                                    showStoreName = preset.style.showStoreName
                                    showDateTracking = preset.style.showDateTracking
                                    extraFieldTitle = preset.style.extraFieldLabel
                                    extraFieldValue = preset.style.extraFieldValue
                                    productNameTextScale = preset.style.productNameTextScale
                                    priceTextScale = preset.style.priceTextScale
                                    codeNumberTextScale = preset.style.codeNumberTextScale
                                    storeNameTextScale = preset.style.storeNameTextScale
                                    dateTrackingTextScale = preset.style.dateTrackingTextScale
                                    barcodeGraphicScale = preset.style.barcodeGraphicScale
                                    Toast.makeText(context, context.getString(R.string.label_preset_saved_toast), Toast.LENGTH_SHORT).show()
                                },
                                label = { Text(presetName, fontSize = 11.5.sp) },
                                leadingIcon = { Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }

                    // Code Type (LBL-16)
                    val isEan13Valid = remember(product.barcode) { isValidEan13(product.barcode) }
                    val isEan8Valid = remember(product.barcode) { isValidEan8(product.barcode) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = codeType == ProductCodeType.BARCODE,
                            onClick = { codeTypeName = ProductCodeType.BARCODE.name },
                            label = { Text("Code 128") },
                            leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        if (isEan13Valid) {
                            FilterChip(
                                selected = codeType == ProductCodeType.EAN_13,
                                onClick = { codeTypeName = ProductCodeType.EAN_13.name },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.code_type_ean13))
                                        if (recommendedCodeType == ProductCodeType.EAN_13) {
                                            Text("★", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                        if (isEan8Valid) {
                            FilterChip(
                                selected = codeType == ProductCodeType.EAN_8,
                                onClick = { codeTypeName = ProductCodeType.EAN_8.name },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.code_type_ean8))
                                        if (recommendedCodeType == ProductCodeType.EAN_8) {
                                            Text("★", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                        FilterChip(
                            selected = codeType == ProductCodeType.CODE_39,
                            onClick = { codeTypeName = ProductCodeType.CODE_39.name },
                            label = { Text(stringResource(R.string.code_type_code39)) },
                            leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = codeType == ProductCodeType.QR_CODE,
                            onClick = { codeTypeName = ProductCodeType.QR_CODE.name },
                            label = { Text(stringResource(R.string.qr_code)) },
                            leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

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
                            placeholder = { Text(configuredStoreName.ifBlank { "Techyfi POS" }) },
                            supportingText = { Text(stringResource(R.string.products_store_name_supporting_text)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }

                    // Label Copies Control Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CopiesStepper(
                                copies = printCopies,
                                onCopiesChange = { printCopies = it },
                                stockQuantity = product.stockQty,
                                onErrorChange = { isSingleCopiesError = it }
                            )
                        }
                    }
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
                                    label = { Text(stringResource(R.string.products_mfg_abbr), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Animated3DFactoryIcon() },
                                    modifier = Modifier.height(32.dp)
                                )
                                FilterChip(
                                    selected = mfgOrPkgType == "Pkg",
                                    onClick = { mfgOrPkgType = "Pkg" },
                                    label = { Text(stringResource(R.string.products_pkg_abbr), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
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

                // Additional Field Section (LBL-18)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.label_extra_field_title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = extraFieldTitle,
                                onValueChange = { extraFieldTitle = it.take(30) },
                                label = { Text(stringResource(R.string.label_extra_field_name), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = extraFieldValue,
                                onValueChange = { extraFieldValue = it.take(40) },
                                label = { Text(stringResource(R.string.label_extra_field_value), fontSize = 11.sp) },
                                singleLine = true,
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
                            labelSize.getLocalizedName(context),
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
                                    Text(size.getLocalizedName(context))
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
                            isError = isCustomWidthError,
                            supportingText = {
                                if (isCustomWidthError) {
                                    Text(stringResource(R.string.custom_width_range_error), color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(stringResource(R.string.products_width_range_hint))
                                }
                            },
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
                            isError = isCustomHeightError,
                            supportingText = {
                                if (isCustomHeightError) {
                                    Text(stringResource(R.string.custom_height_range_error), color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(stringResource(R.string.products_height_range_hint))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                // Advanced text & graphic scale section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedStyling = !showAdvancedStyling },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.batch_print_advanced_settings),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                if (showAdvancedStyling) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showAdvancedStyling) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.reset_size_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            productNameTextScale = 1.0f
                                            priceTextScale = 1.0f
                                            codeNumberTextScale = 1.0f
                                            storeNameTextScale = 1.0f
                                            dateTrackingTextScale = 1.0f
                                            barcodeGraphicScale = 1.0f
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.reset_all_sizes), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_product_name),
                                    scale = productNameTextScale,
                                    onScaleChange = { productNameTextScale = it },
                                    onReset = { productNameTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_price),
                                    scale = priceTextScale,
                                    enabled = showPrice,
                                    onScaleChange = { priceTextScale = it },
                                    onReset = { priceTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_code_number),
                                    scale = codeNumberTextScale,
                                    enabled = showCodeNumber,
                                    onScaleChange = { codeNumberTextScale = it },
                                    onReset = { codeNumberTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_store_name),
                                    scale = storeNameTextScale,
                                    enabled = showStoreName && storeNameInput.isNotBlank(),
                                    onScaleChange = { storeNameTextScale = it },
                                    onReset = { storeNameTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_date_tracking_text),
                                    scale = dateTrackingTextScale,
                                    enabled = showDateTracking && (mfgOrPkgDate.isNotBlank() || expDate.isNotBlank()),
                                    onScaleChange = { dateTrackingTextScale = it },
                                    onReset = { dateTrackingTextScale = 1.0f }
                                )
                                LabelTextSizeControl(
                                    label = stringResource(R.string.products_label_barcode_graphic_height),
                                    scale = barcodeGraphicScale,
                                    enabled = true,
                                    onScaleChange = { barcodeGraphicScale = it },
                                    onReset = { barcodeGraphicScale = 1.0f }
                                )
                            }
                        }
                    }
                }
            } // End of scrollable settings Column

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Spacer(modifier = Modifier.height(14.dp))
                if (bitmap != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPrinting) {
                            Button(
                                onClick = {
                                    singlePrintJob?.cancel()
                                    isPrinting = false
                                    val msg = context.getString(R.string.batch_print_cancelled)
                                    actionStatus = ActionStatus(msg, isError = false)
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1.3f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onError)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.batch_print_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                            }
                        } else {
                            Button(
                                onClick = {
                                    singlePrintJob = scope.launch {
                                        val currentBmp = bitmap
                                        if (currentBmp != null) {
                                            isPrinting = true
                                            try {
                                                val outcome = printLabelImages(
                                                    context = context,
                                                    activity = activity ?: context.findActivity(),
                                                    labels = listOf(currentBmp),
                                                    pdfFallbackBuilder = {
                                                        withContext(Dispatchers.IO) {
                                                            cacheProductCodeLabelPdf(
                                                                context = context,
                                                                bitmap = currentBmp,
                                                                productName = product.name,
                                                                codeType = codeType,
                                                                widthMm = selectedLabelWidthMm,
                                                                heightMm = selectedLabelHeightMm,
                                                                copies = printCopies
                                                            )?.first
                                                        }
                                                    },
                                                    widthMm = selectedLabelWidthMm,
                                                    heightMm = selectedLabelHeightMm,
                                                    copies = printCopies,
                                                    jobName = context.getString(R.string.print_job_product_label_fmt, product.name),
                                                    settings = invoiceSettings
                                                )
                                                when (outcome) {
                                                    com.example.techyfipos.utils.LabelPrintOutcome.SENT_DIRECT -> {
                                                        val msg = context.getString(R.string.labels_sent_to_printer_fmt, printCopies)
                                                        actionStatus = ActionStatus(msg, isError = false)
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                    com.example.techyfipos.utils.LabelPrintOutcome.OPENED_SYSTEM_DIALOG -> {
                                                        val msg = context.getString(R.string.print_system_dialog_opened)
                                                        actionStatus = ActionStatus(msg, isError = false)
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                    com.example.techyfipos.utils.LabelPrintOutcome.FAILED -> {
                                                        actionStatus = ActionStatus(context.getString(R.string.code_image_print_failed), isError = true)
                                                    }
                                                }
                                            } finally {
                                                isPrinting = false
                                            }
                                        } else {
                                            actionStatus = ActionStatus(context.getString(R.string.code_image_print_failed), isError = true)
                                        }
                                    }
                                },
                                enabled = !isPrinting && !isSharingPdf && !isSingleCopiesError && !isCustomWidthError && !isCustomHeightError,
                                modifier = Modifier.weight(1.3f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.print_copies_button_fmt, printCopies), fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedIconButton(
                            onClick = {
                                if (isSharingPdf) {
                                    sharePdfJob?.cancel()
                                    isSharingPdf = false
                                    return@OutlinedIconButton
                                }
                                val currentBmp = bitmap ?: return@OutlinedIconButton
                                sharePdfJob = scope.launch {
                                    isSharingPdf = true
                                    try {
                                        val cached = withContext(Dispatchers.IO) {
                                            cacheProductCodeLabelPdf(
                                                context = context,
                                                bitmap = currentBmp,
                                                productName = product.name,
                                                codeType = codeType,
                                                widthMm = selectedLabelWidthMm,
                                                heightMm = selectedLabelHeightMm,
                                                copies = printCopies
                                            )
                                        }
                                        if (cached != null) {
                                            val shared = shareProductCodeLabelPdf(
                                                activity ?: context,
                                                cached.second,
                                                product.name
                                            )
                                            if (!shared) {
                                                actionStatus = ActionStatus(context.getString(R.string.code_image_share_failed), isError = true)
                                            }
                                        } else {
                                            actionStatus = ActionStatus(context.getString(R.string.code_image_share_failed), isError = true)
                                        }
                                    } finally {
                                        isSharingPdf = false
                                    }
                                }
                            },
                            enabled = !isPrinting && !isSingleCopiesError && !isCustomWidthError && !isCustomHeightError,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSharingPdf) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_code_image))
                            }
                        }

                        OutlinedIconButton(
                            onClick = {
                                val currentBmp = bitmap ?: return@OutlinedIconButton
                                scope.launch {
                                    isSaving = true
                                    val savedUri = withContext(Dispatchers.IO) {
                                        saveProductCodeLabelToPictures(context, currentBmp, product.name, codeType)
                                    }
                                    isSaving = false
                                    actionStatus = if (savedUri != null) {
                                        ActionStatus(context.getString(R.string.code_image_saved), isError = false)
                                    } else {
                                        ActionStatus(context.getString(R.string.code_image_save_failed), isError = true)
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = stringResource(R.string.save_png))
                            }
                        }
                    }
                }

                actionStatus?.let { status ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        status.text,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (status.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
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
    onScaleChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
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
            enabled = enabled && scale > 0.65f
        ) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.products_decrease_text_size_cd, label))
        }
        Text(
            text = "${(scale * 100).roundToInt()}%",
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge
        )
        IconButton(
            onClick = { onScaleChange((scale + 0.1f).coerceAtMost(1.8f)) },
            enabled = enabled && scale < 1.75f
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.products_increase_text_size_cd, label))
        }
        if (onReset != null) {
            val isModified = (scale * 100).roundToInt() != 100
            IconButton(
                onClick = onReset,
                enabled = enabled && isModified,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.reset),
                    tint = if (enabled && isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

