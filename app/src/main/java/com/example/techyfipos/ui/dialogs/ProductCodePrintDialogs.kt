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
import com.example.techyfipos.theme.LocalDarkTheme
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
    val isDark = isSystemInDarkTheme() || LocalDarkTheme.current
    val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val fieldBg = if (isDark) Color(0xFF111A2C) else Color(0xFFF8FAFC)
    val textMain = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val primaryColor = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
    val primaryContainer = if (isDark) Color(0xFF0F766E).copy(alpha = 0.28f) else Color(0xFFCCFBF1)
    val primaryBorder = Color(0xFF14B8A6)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, cardBorder)
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
                        tint = primaryColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.products_price_tier_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textMain,
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
                            checkedColor = primaryColor
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.show_price),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = textMain,
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
                        color = if (isSellingActive) primaryContainer else fieldBg,
                        border = BorderStroke(
                            if (isSellingActive) 1.5.dp else 1.dp,
                            if (isSellingActive) primaryBorder else cardBorder
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
                                fontWeight = if (isSellingActive) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSellingActive) primaryColor else textMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = sellingPriceDisplay,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSellingActive) primaryColor else textMain,
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
                        color = if (isMrpActive) primaryContainer else fieldBg,
                        border = BorderStroke(
                            if (isMrpActive) 1.5.dp else 1.dp,
                            if (isMrpActive) primaryBorder else cardBorder
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
                                fontWeight = if (isMrpActive) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isMrpActive) primaryColor else textMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mrpPriceDisplay,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMrpActive) primaryColor else textMain,
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
                        color = if (isWholesaleActive) primaryContainer else fieldBg,
                        border = BorderStroke(
                            if (isWholesaleActive) 1.5.dp else 1.dp,
                            if (isWholesaleActive) primaryBorder else cardBorder
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
                                fontWeight = if (isWholesaleActive) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isWholesaleActive) primaryColor else textMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = wholesalePriceDisplay,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isWholesaleActive) primaryColor else textMain,
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
                            if (isDualActive) (if (isDark) Color(0xFF312E81).copy(alpha = 0.45f) else Color(0xFFEEF2FF))
                            else fieldBg
                        )
                        .border(
                            if (isDualActive) 1.5.dp else 1.dp,
                            if (isDualActive) Color(0xFF6366F1) else cardBorder,
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
                            color = if (isDualActive) (if (isDark) Color(0xFFC7D2FE) else Color(0xFF3730A3)) else textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        val notSetText = stringResource(R.string.products_price_tier_not_set)
                        val hasMrp = mrpPriceDisplay.isNotBlank() && mrpPriceDisplay != notSetText && mrpPriceDisplay != "-"

                        if (hasMrp) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, cardBorder)
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
                                        color = textMuted,
                                        textDecoration = TextDecoration.LineThrough,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = sellingPriceDisplay,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
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

                HorizontalDivider(color = cardBorder)

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
                        color = if (showUnit) primaryContainer else fieldBg,
                        border = BorderStroke(
                            1.dp,
                            if (showUnit) primaryBorder else cardBorder
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
                                color = if (showUnit) textMain else textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showUnit) stringResource(R.string.opt_on) else stringResource(R.string.opt_off),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (showUnit) primaryColor else textMuted
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
                        color = if (showBanglaDigits) primaryContainer else fieldBg,
                        border = BorderStroke(
                            1.dp,
                            if (showBanglaDigits) primaryBorder else cardBorder
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
                                color = if (showBanglaDigits) textMain else textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (showBanglaDigits) stringResource(R.string.opt_on) else stringResource(R.string.opt_off),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (showBanglaDigits) primaryColor else textMuted
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
                                                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_decrease), modifier = Modifier.size(14.dp))
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
                                                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_increase), modifier = Modifier.size(14.dp))
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
    onDismiss: () -> Unit,
    allProducts: List<Product> = emptyList(),
    onProductSelected: ((Product) -> Unit)? = null
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

    // In-dialog Product Switching State
    var currentProduct by remember(product) { mutableStateOf(product) }
    val productIndex = remember(currentProduct, allProducts) {
        allProducts.indexOfFirst { it.barcode == currentProduct.barcode }
    }
    val hasPrev = productIndex > 0
    val hasNext = productIndex in 0 until allProducts.lastIndex

    // Code Format & Settings State
    val recommendedCodeType = remember(currentProduct.barcode) { getRecommendedCodeType(currentProduct.barcode) }
    var codeTypeName by rememberSaveable {
        val stored = preferences.getString("code_type", null)
        mutableStateOf(stored ?: recommendedCodeType.name)
    }
    var codeFormatMenuExpanded by remember { mutableStateOf(false) }

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
    var labelSizeDropdownExpanded by remember { mutableStateOf(false) }

    var showPrice by rememberSaveable { mutableStateOf(preferences.getBoolean("show_price", true)) }
    var priceTierName by rememberSaveable {
        mutableStateOf(preferences.getString("price_tier", ProductCodePriceTier.SELLING.name) ?: ProductCodePriceTier.SELLING.name)
    }
    val priceTier = ProductCodePriceTier.entries.firstOrNull { it.name == priceTierName } ?: ProductCodePriceTier.SELLING
    var isDualPrice by rememberSaveable { mutableStateOf(priceTierName == ProductCodePriceTier.DUAL.name) }

    var showUnit by rememberSaveable { mutableStateOf(preferences.getBoolean("show_unit", preferences.getBoolean("show_price_unit", false))) }
    var showBanglaDigits by rememberSaveable { mutableStateOf(preferences.getBoolean("show_bangla_digits", false)) }
    var showCodeNumber by rememberSaveable { mutableStateOf(preferences.getBoolean("show_code_number", true)) }
    var showStoreName by rememberSaveable { mutableStateOf(preferences.getBoolean("show_store_name", true)) }
    var showLogo by rememberSaveable { mutableStateOf(preferences.getBoolean("show_logo", false)) }
    var showDateTracking by rememberSaveable { mutableStateOf(preferences.getBoolean("show_date_tracking", false)) }
    var mfgOrPkgType by rememberSaveable { mutableStateOf(currentProduct.mfgOrPkgType ?: "Mfg") }
    var mfgOrPkgDate by rememberSaveable { mutableStateOf(currentProduct.mfgOrPkgDate ?: "") }
    var expDate by rememberSaveable { mutableStateOf(currentProduct.expiryDate ?: "") }
    var extraFieldTitle by rememberSaveable {
        mutableStateOf(preferences.getString("extra_field_title", preferences.getString("extra_field_label", "") ?: "") ?: "")
    }
    var extraFieldValue by rememberSaveable {
        mutableStateOf(preferences.getString("extra_field_value", "") ?: "")
    }

    // Copies Mode & Persistence (Auto Stock vs Manual)
    var copiesMode by rememberSaveable {
        mutableStateOf(preferences.getString("pos_label_copies_mode", "MANUAL") ?: "MANUAL")
    }
    var manualCopies by rememberSaveable {
        mutableIntStateOf(preferences.getInt("pos_label_manual_copies", 1))
    }
    var printCopies by remember(copiesMode, manualCopies, currentProduct.stockQty) {
        mutableIntStateOf(
            if (copiesMode == "AUTO") {
                currentProduct.stockQty.toInt().coerceAtLeast(1)
            } else {
                manualCopies.coerceIn(1, 500)
            }
        )
    }

    // Active Segmented Tab (0 = Label Setup, 1 = Store & Fields, 2 = Dates & Scale)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showZoomModal by remember { mutableStateOf(false) }
    var activePresetKey by rememberSaveable { mutableStateOf<String?>("standard") }
    var selectedDpi by rememberSaveable { mutableIntStateOf(invoiceSettings.labelDpi.takeIf { it in listOf(203, 300) } ?: 203) }

    var storeNameInput by rememberSaveable {
        mutableStateOf(preferences.getString("store_name", "") ?: "")
    }
    var customWidthInput by rememberSaveable {
        mutableStateOf(preferences.getFloat("custom_width_mm", 50f).toString().removeSuffix(".0"))
    }
    var customHeightInput by rememberSaveable {
        mutableStateOf(preferences.getFloat("custom_height_mm", 30f).toString().removeSuffix(".0"))
    }

    // All 8 Scalers (60% to 180%)
    var productNameTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("product_name_text_scale", 1f))
    }
    var priceTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("price_text_scale", 1f))
    }
    var codeNumberTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("code_number_text_scale", 1f))
    }
    var storeLogoScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("store_logo_scale", 1f))
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
    var extraFieldTextScale by rememberSaveable {
        mutableFloatStateOf(preferences.getFloat("extra_field_text_scale", 1f))
    }

    var actionStatus by remember { mutableStateOf<ActionStatus?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isPrinting by remember { mutableStateOf(false) }
    var isSingleCopiesError by remember { mutableStateOf(false) }
    var singlePrintJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isSharingPdf by remember { mutableStateOf(false) }
    var sharePdfJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val codeType = ProductCodeType.entries.firstOrNull { it.name == codeTypeName } ?: ProductCodeType.BARCODE
    val labelSize = ProductCodeLabelSize.entries.firstOrNull { it.name == labelSizeName } ?: ProductCodeLabelSize.SIZE_50_X_30
    val parsedCustomWidth = customWidthInput.toFloatOrNull()
    val isCustomWidthError = labelSize == ProductCodeLabelSize.CUSTOM && (parsedCustomWidth == null || parsedCustomWidth !in 15f..150f)
    val parsedCustomHeight = customHeightInput.toFloatOrNull()
    val isCustomHeightError = labelSize == ProductCodeLabelSize.CUSTOM && (parsedCustomHeight == null || parsedCustomHeight !in 10f..200f)

    val customWidthMm = parsedCustomWidth?.coerceIn(15f, 150f) ?: 50f
    val customHeightMm = parsedCustomHeight?.coerceIn(10f, 200f) ?: 30f
    val (selectedLabelWidthMm, selectedLabelHeightMm) = resolveLabelDimensionsMm(labelSize, customWidthMm, customHeightMm)
    val effectiveStoreName = storeNameInput.ifBlank { configuredStoreName }

    val formattedPriceString = formatProductCodeLabelPriceString(
        tier = if (isDualPrice) ProductCodePriceTier.DUAL else priceTier,
        sellingPrice = currentProduct.sellingPrice,
        mrpPrice = currentProduct.mrpPrice,
        wholesalePrice = currentProduct.wholesalePrice,
        showUnit = showUnit,
        showBanglaDigits = showBanglaDigits
    )

    LaunchedEffect(configuredStoreName) {
        if (!hadSavedStoreName && configuredStoreName.isNotBlank() && storeNameInput.isBlank()) {
            storeNameInput = configuredStoreName
        }
    }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(
        currentProduct, codeType, labelSize, showPrice, priceTierName, isDualPrice, showUnit, showBanglaDigits,
        showCodeNumber, showStoreName, showLogo, effectiveStoreName,
        showDateTracking, mfgOrPkgType, mfgOrPkgDate, expDate,
        customWidthMm, customHeightMm, productNameTextScale, priceTextScale,
        codeNumberTextScale, storeLogoScale, storeNameTextScale, dateTrackingTextScale, barcodeGraphicScale,
        extraFieldTitle, extraFieldValue, extraFieldTextScale, selectedDpi
    ) {
        val old = bitmap
        val newBmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            generateProductCodeLabelBitmap(
                productName = currentProduct.name,
                barcode = currentProduct.barcode,
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
                extraFieldTextScale = extraFieldTextScale,
                showLogo = showLogo,
                dpi = selectedDpi,
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
        codeTypeName, labelSizeName, showPrice, priceTierName, isDualPrice, showUnit, showBanglaDigits,
        showCodeNumber, showStoreName, showLogo, storeNameInput,
        showDateTracking, mfgOrPkgType, mfgOrPkgDate, expDate,
        customWidthInput, customHeightInput, productNameTextScale, priceTextScale,
        codeNumberTextScale, storeLogoScale, storeNameTextScale, dateTrackingTextScale, barcodeGraphicScale,
        extraFieldTitle, extraFieldValue, extraFieldTextScale, copiesMode, manualCopies
    ) {
        val editor = preferences.edit()
            .putString("code_type", codeTypeName)
            .putString("label_size", labelSizeName)
            .putBoolean("show_price", showPrice)
            .putString("price_tier", if (isDualPrice) ProductCodePriceTier.DUAL.name else priceTierName)
            .putBoolean("show_price_unit", showUnit)
            .putBoolean("show_unit", showUnit)
            .putBoolean("show_bangla_digits", showBanglaDigits)
            .putBoolean("show_code_number", showCodeNumber)
            .putBoolean("show_store_name", showStoreName)
            .putBoolean("show_logo", showLogo)
            .putBoolean("show_date_tracking", showDateTracking)
            .putString("store_name", storeNameInput.trim())
            .putFloat("product_name_text_scale", productNameTextScale)
            .putFloat("price_text_scale", priceTextScale)
            .putFloat("code_number_text_scale", codeNumberTextScale)
            .putFloat("store_logo_scale", storeLogoScale)
            .putFloat("store_name_text_scale", storeNameTextScale)
            .putFloat("date_tracking_text_scale", dateTrackingTextScale)
            .putFloat("barcode_graphic_scale", barcodeGraphicScale)
            .putFloat("extra_field_text_scale", extraFieldTextScale)
            .putString("extra_field_title", extraFieldTitle.trim())
            .putString("extra_field_label", extraFieldTitle.trim())
            .putString("extra_field_value", extraFieldValue.trim())
            .putString("pos_label_copies_mode", copiesMode)
            .putInt("pos_label_manual_copies", manualCopies)
        if (parsedCustomWidth != null && parsedCustomWidth in 15f..150f) {
            editor.putFloat("custom_width_mm", parsedCustomWidth)
        }
        if (parsedCustomHeight != null && parsedCustomHeight in 10f..200f) {
            editor.putFloat("custom_height_mm", parsedCustomHeight)
        }
        editor.apply()
        actionStatus = null
    }

    val isDark = isSystemInDarkTheme() || LocalDarkTheme.current
    val studioDialogBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val studioCardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val studioCardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val studioFieldBg = if (isDark) Color(0xFF111A2C) else Color(0xFFF8FAFC)
    val studioTextMain = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val studioTextMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val studioPrimaryGradient = Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6)))
    val studioPrimary = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
    val studioPrimaryContainer = if (isDark) Color(0xFF0F766E).copy(alpha = 0.28f) else Color(0xFFCCFBF1)
    val studioPrimaryBorder = Color(0xFF14B8A6)

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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.94f)
                .trackUserActivity(),
            shape = RoundedCornerShape(24.dp),
            color = studioDialogBg,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, studioCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. DIALOG HEADER WITH 3D ICON BADGE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(studioPrimaryGradient)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                                    )
                                )
                        )
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.generate_code_image),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = studioTextMain
                        )
                        Text(
                            text = "Barcode & QR Label Studio",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = studioPrimary
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        enabled = !isPrinting && !isSharingPdf,
                        shape = CircleShape,
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, studioCardBorder),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.dismiss),
                                tint = studioTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 2. UNIFIED SCROLLABLE DIALOG BODY (Matching preview: Hero, Preview with Presets, Tabs, and Tab Controls)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // A. PRODUCT HERO CARD & CAROUSEL
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = studioCardBg,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, studioCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // Top Carousel Navigation Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Category Badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isDark) Color(0xFF0369A1).copy(alpha = 0.25f) else Color(0xFFE0F2FE)
                                ) {
                                    Text(
                                        text = currentProduct.category.ifBlank { "General" }.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                        fontSize = 9.5.sp
                                    )
                                }

                                // Carousel Controls (If products available)
                                if (allProducts.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            onClick = {
                                                if (hasPrev) {
                                                    val prev = allProducts[productIndex - 1]
                                                    currentProduct = prev
                                                    onProductSelected?.invoke(prev)
                                                }
                                            },
                                            enabled = hasPrev,
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, studioCardBorder),
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.ChevronLeft,
                                                    contentDescription = stringResource(R.string.product_prev),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (hasPrev) studioTextMain else studioTextMuted.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                        Text(
                                            text = stringResource(R.string.product_carousel_counter, productIndex + 1, allProducts.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = studioTextMuted,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        Surface(
                                            onClick = {
                                                if (hasNext) {
                                                    val next = allProducts[productIndex + 1]
                                                    currentProduct = next
                                                    onProductSelected?.invoke(next)
                                                }
                                            },
                                            enabled = hasNext,
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, studioCardBorder),
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.ChevronRight,
                                                    contentDescription = stringResource(R.string.product_next),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if (hasNext) studioTextMain else studioTextMuted.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Product Details Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 3D Product Avatar Box
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF4338CA), Color(0xFF6366F1))
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.5f)
                                            .align(Alignment.TopCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)
                                                )
                                            )
                                    )
                                    Text(
                                        text = "📦",
                                        fontSize = 22.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentProduct.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = studioTextMain,
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                                        ) {
                                            Text(
                                                text = currentProduct.barcode,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp,
                                                color = studioTextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (currentProduct.stockQty > 0) Color(0xFF10B981) else Color(0xFFEF4444))
                                            )
                                            Text(
                                                text = if (currentProduct.stockQty > 0) "In Stock: ${currentProduct.stockQty.toInt()} pcs" else "Out of Stock",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentProduct.stockQty > 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. PINNED STICKER PREVIEW WITH ZOOM INSPECT
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = studioCardBg,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.5.dp, Color(0xFF14B8A6).copy(alpha = if (isDark) 0.40f else 0.45f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        if (isDark) listOf(Color(0xFF0F766E).copy(alpha = 0.16f), Color(0xFF1E293B))
                                        else listOf(Color(0xFF0F766E).copy(alpha = 0.06f), Color(0xFFFFFFFF))
                                    )
                                )
                                .padding(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Preview Header (Dimensions + Zoom Inspect Button)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = studioPrimaryContainer,
                                        border = BorderStroke(1.dp, Color(0xFF14B8A6).copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "📐 ${selectedLabelWidthMm.toInt()} × ${selectedLabelHeightMm.toInt()} mm • $selectedDpi DPI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.5.sp,
                                            color = studioPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    // Zoom Inspect Button
                                    Surface(
                                        onClick = { showZoomModal = true },
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9),
                                        border = BorderStroke(1.dp, studioCardBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ZoomIn,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = studioTextMain
                                            )
                                            Text(
                                                text = stringResource(R.string.zoom_inspect_label),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp,
                                                color = studioTextMain
                                            )
                                        }
                                    }
                                }

                                // REALISTIC PHYSICAL STICKER CANVAS WITH PAPER DROP SHADOW
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    shadowElevation = 8.dp,
                                    border = BorderStroke(1.5.dp, Color.Black.copy(alpha = 0.14f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 96.dp, max = 155.dp)
                                            .clickable { showZoomModal = true }
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val currentBmp = bitmap
                                        if (currentBmp != null) {
                                            Image(
                                                bitmap = currentBmp.asImageBitmap(),
                                                contentDescription = stringResource(R.string.product_code_image_preview),
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Text(
                                                stringResource(R.string.code_generation_failed),
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                // Quick Template Presets Carousel (LBL-18: Standard, Grocery, Jewelry, Apparel, Warehouse)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val presetPills = listOf(
                                        Triple("standard", "🏷️", "Standard Retail"),
                                        Triple("grocery", "🥫", "Grocery & Expiry"),
                                        Triple("jewelry", "💍", "Jewelry Mini"),
                                        Triple("apparel", "👗", "Apparel Dual-Price"),
                                        Triple("warehouse", "📦", "Warehouse Code 128")
                                    )
                                    presetPills.forEach { (key, icon, name) ->
                                        val isActive = activePresetKey == key
                                        Surface(
                                            onClick = {
                                                activePresetKey = key
                                                when (key) {
                                                    "standard" -> {
                                                        labelSizeName = ProductCodeLabelSize.SIZE_50_X_30.name
                                                        priceTierName = ProductCodePriceTier.SELLING.name
                                                        showPrice = true
                                                        showCodeNumber = true
                                                        showStoreName = true
                                                        showDateTracking = false
                                                        isDualPrice = false
                                                        productNameTextScale = 1.0f
                                                        priceTextScale = 1.0f
                                                        codeNumberTextScale = 1.0f
                                                        storeNameTextScale = 1.0f
                                                        dateTrackingTextScale = 1.0f
                                                        barcodeGraphicScale = 1.0f
                                                        extraFieldTextScale = 1.0f
                                                    }
                                                    "grocery" -> {
                                                        labelSizeName = ProductCodeLabelSize.SIZE_40_X_25.name
                                                        priceTierName = ProductCodePriceTier.SELLING.name
                                                        showPrice = true
                                                        showCodeNumber = true
                                                        showDateTracking = true
                                                        isDualPrice = false
                                                        productNameTextScale = 1.0f
                                                        priceTextScale = 1.0f
                                                        codeNumberTextScale = 1.0f
                                                        storeNameTextScale = 1.0f
                                                        dateTrackingTextScale = 1.0f
                                                        barcodeGraphicScale = 1.0f
                                                        extraFieldTextScale = 1.0f
                                                    }
                                                    "jewelry" -> {
                                                        labelSizeName = ProductCodeLabelSize.SIZE_25_X_15.name
                                                        priceTierName = ProductCodePriceTier.SELLING.name
                                                        showPrice = true
                                                        showCodeNumber = false
                                                        showDateTracking = false
                                                        isDualPrice = false
                                                        productNameTextScale = 1.0f
                                                        priceTextScale = 1.0f
                                                        codeNumberTextScale = 1.0f
                                                        storeNameTextScale = 1.0f
                                                        dateTrackingTextScale = 1.0f
                                                        barcodeGraphicScale = 0.8f
                                                        extraFieldTextScale = 1.0f
                                                    }
                                                    "apparel" -> {
                                                        labelSizeName = ProductCodeLabelSize.SIZE_60_X_40.name
                                                        isDualPrice = true
                                                        showPrice = true
                                                        showUnit = true
                                                        showStoreName = true
                                                        showCodeNumber = true
                                                        showDateTracking = false
                                                        productNameTextScale = 1.0f
                                                        priceTextScale = 1.0f
                                                        codeNumberTextScale = 1.0f
                                                        storeNameTextScale = 1.0f
                                                        dateTrackingTextScale = 1.0f
                                                        barcodeGraphicScale = 1.0f
                                                        extraFieldTextScale = 1.0f
                                                    }
                                                    "warehouse" -> {
                                                        labelSizeName = ProductCodeLabelSize.SIZE_100_X_150.name
                                                        codeTypeName = ProductCodeType.BARCODE.name
                                                        showPrice = false
                                                        showStoreName = true
                                                        showCodeNumber = true
                                                        showDateTracking = true
                                                        isDualPrice = false
                                                        productNameTextScale = 1.0f
                                                        priceTextScale = 1.0f
                                                        codeNumberTextScale = 1.0f
                                                        storeNameTextScale = 1.0f
                                                        dateTrackingTextScale = 1.0f
                                                        barcodeGraphicScale = 1.0f
                                                        extraFieldTextScale = 1.0f
                                                    }
                                                }
                                                Toast.makeText(context, context.getString(R.string.label_preset_saved_toast), Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (isActive) studioPrimaryContainer else (if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)),
                                            border = BorderStroke(
                                                if (isActive) 1.5.dp else 1.dp,
                                                if (isActive) studioPrimaryBorder else studioCardBorder
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(icon, fontSize = 12.sp)
                                                Text(
                                                    text = name,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                    color = if (isActive) studioPrimary else studioTextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // High Density Barcode Warning (LBL-21)
                    val isSingleDense = remember(currentProduct.barcode, codeType, selectedLabelWidthMm, selectedDpi) {
                        isBarcodeTooDenseForLabel(currentProduct.barcode, codeType, selectedLabelWidthMm, selectedDpi)
                    }
                    if (isSingleDense) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Text(
                                    text = stringResource(R.string.barcode_too_dense_warning),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { codeTypeName = ProductCodeType.QR_CODE.name }) {
                                    Text(stringResource(R.string.switch_to_qr), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 5. SEGMENTED 3-TAB SELECTOR (Vibrant Pill Tabs)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF111A2C) else Color(0xFFE2E8F0).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, studioCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val tabs = listOf(
                                "🏷️" to stringResource(R.string.tab_label_setup),
                                "🏢" to stringResource(R.string.tab_store_info),
                                "📅" to stringResource(R.string.tab_dates_scale)
                            )
                            tabs.forEachIndexed { index, (icon, title) ->
                                val isSelected = selectedTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isSelected) studioPrimaryGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                        .clickable { selectedTab = index }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = icon, fontSize = 12.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSelected) Color.White else studioTextMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // D. ACTIVE TAB PANE CONTENT
                    when (selectedTab) {
                        0 -> {
                            // TAB 1: LABEL SETUP

                            // A. Code Format Dropdown Card
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = studioCardBg,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "SELECT CODE FORMAT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = studioPrimary
                                    )

                                    // Dropdown Trigger Box
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Surface(
                                            onClick = { codeFormatMenuExpanded = true },
                                            shape = RoundedCornerShape(10.dp),
                                            color = studioFieldBg,
                                            border = BorderStroke(1.5.dp, studioPrimaryBorder.copy(alpha = 0.6f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (codeType == ProductCodeType.QR_CODE) Icons.Default.QrCode2 else Icons.Default.ViewWeek,
                                                        contentDescription = null,
                                                        tint = studioPrimary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = when (codeType) {
                                                                ProductCodeType.BARCODE -> "Code 128"
                                                                ProductCodeType.EAN_13 -> "EAN-13"
                                                                ProductCodeType.EAN_8 -> "EAN-8"
                                                                ProductCodeType.CODE_39 -> "Code 39"
                                                                ProductCodeType.QR_CODE -> "QR Code"
                                                            },
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = studioTextMain
                                                        )
                                                        Text(
                                                            text = when (codeType) {
                                                                ProductCodeType.BARCODE -> "Standard Barcode"
                                                                ProductCodeType.EAN_13 -> "Global Retail"
                                                                ProductCodeType.EAN_8 -> "Compact 8-Digit"
                                                                ProductCodeType.CODE_39 -> "Alphanumeric"
                                                                ProductCodeType.QR_CODE -> "2D Matrix"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = studioTextMuted
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = if (codeFormatMenuExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = studioPrimary
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = codeFormatMenuExpanded,
                                            onDismissRequest = { codeFormatMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("Code 128", fontWeight = FontWeight.Bold)
                                                        Text("Standard Barcode", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null) },
                                                trailingIcon = if (codeType == ProductCodeType.BARCODE) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                } else null,
                                                onClick = {
                                                    codeTypeName = ProductCodeType.BARCODE.name
                                                    codeFormatMenuExpanded = false
                                                }
                                            )
                                            val isEan13Valid = remember(currentProduct.barcode) { isValidEan13(currentProduct.barcode) }
                                            if (isEan13Valid) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Text("EAN-13", fontWeight = FontWeight.Bold)
                                                                if (recommendedCodeType == ProductCodeType.EAN_13) {
                                                                    Text("★ Recommended", fontSize = 10.sp, color = Color(0xFFFBBF24))
                                                                }
                                                            }
                                                            Text("Global Retail", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null) },
                                                    trailingIcon = if (codeType == ProductCodeType.EAN_13) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                    } else null,
                                                    onClick = {
                                                        codeTypeName = ProductCodeType.EAN_13.name
                                                        codeFormatMenuExpanded = false
                                                    }
                                                )
                                            }
                                            val isEan8Valid = remember(currentProduct.barcode) { isValidEan8(currentProduct.barcode) }
                                            if (isEan8Valid) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text("EAN-8", fontWeight = FontWeight.Bold)
                                                            Text("Compact 8-Digit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null) },
                                                    trailingIcon = if (codeType == ProductCodeType.EAN_8) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                    } else null,
                                                    onClick = {
                                                        codeTypeName = ProductCodeType.EAN_8.name
                                                        codeFormatMenuExpanded = false
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("Code 39", fontWeight = FontWeight.Bold)
                                                        Text("Alphanumeric", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.ViewWeek, contentDescription = null) },
                                                trailingIcon = if (codeType == ProductCodeType.CODE_39) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                } else null,
                                                onClick = {
                                                    codeTypeName = ProductCodeType.CODE_39.name
                                                    codeFormatMenuExpanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("QR Code", fontWeight = FontWeight.Bold)
                                                        Text("2D Matrix", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null) },
                                                trailingIcon = if (codeType == ProductCodeType.QR_CODE) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                } else null,
                                                onClick = {
                                                    codeTypeName = ProductCodeType.QR_CODE.name
                                                    codeFormatMenuExpanded = false
                                                }
                                            )
                                        }
                                    }

                                    // Thermal DPI Resolution Selector
                                    HorizontalDivider(color = studioCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Print Resolution:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = studioTextMuted,
                                            fontSize = 11.sp
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val dpis = listOf(203 to "203 DPI (Std)", 300 to "300 DPI (Hi-Res)")
                                            dpis.forEach { (dpiVal, dpiLabel) ->
                                                val isDpiSelected = selectedDpi == dpiVal
                                                Surface(
                                                    onClick = { selectedDpi = dpiVal },
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = if (isDpiSelected) studioPrimaryContainer else studioFieldBg,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isDpiSelected) studioPrimaryBorder else studioCardBorder
                                                    )
                                                ) {
                                                    Text(
                                                        text = dpiLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isDpiSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isDpiSelected) studioPrimary else studioTextMuted,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // B. Price Tier & Dual Price Controls Card
                            val singleSellingDisplay = if (showBanglaDigits) toBanglaDigits(formatTaka(currentProduct.sellingPrice)) else formatTaka(currentProduct.sellingPrice)
                            val singleMrpDisplay = if (currentProduct.mrpPrice > 0) {
                                val base = formatTaka(currentProduct.mrpPrice)
                                if (showBanglaDigits) toBanglaDigits(base) else base
                            } else stringResource(R.string.products_price_tier_not_set)
                            val singleWholesaleDisplay = if (currentProduct.wholesalePrice > 0) {
                                val base = formatTaka(currentProduct.wholesalePrice)
                                if (showBanglaDigits) toBanglaDigits(base) else base
                            } else stringResource(R.string.products_price_tier_not_set)

                            PriceTierSwitcherCard(
                                showPrice = showPrice,
                                onShowPriceChange = { showPrice = it },
                                selectedTier = if (isDualPrice) ProductCodePriceTier.DUAL else priceTier,
                                onTierSelect = {
                                    isDualPrice = (it == ProductCodePriceTier.DUAL)
                                    priceTierName = it.name
                                },
                                sellingPriceDisplay = singleSellingDisplay,
                                mrpPriceDisplay = singleMrpDisplay,
                                wholesalePriceDisplay = singleWholesaleDisplay,
                                showUnit = showUnit,
                                onShowUnitChange = { showUnit = it },
                                showBanglaDigits = showBanglaDigits,
                                onShowBanglaDigitsChange = { showBanglaDigits = it }
                            )

                            // Show Code Number Row
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = studioCardBg,
                                shadowElevation = 1.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showCodeNumber = !showCodeNumber }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = showCodeNumber,
                                        onCheckedChange = { showCodeNumber = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = studioPrimary,
                                            checkmarkColor = Color.White
                                        )
                                    )
                                    Text(
                                        text = stringResource(R.string.show_code_number),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = studioTextMain
                                    )
                                }
                            }

                            // C. Print Copies Card (Auto vs Manual + Stepper + Quick Chips)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = studioCardBg,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Row: Section Title + Mode Toggle Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "PRINT COPIES",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = studioPrimary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = studioFieldBg,
                                            border = BorderStroke(1.dp, studioCardBorder)
                                        ) {
                                            Row(modifier = Modifier.padding(2.dp)) {
                                                Surface(
                                                    onClick = {
                                                        copiesMode = "AUTO"
                                                        printCopies = currentProduct.stockQty.toInt().coerceAtLeast(1)
                                                    },
                                                    shape = RoundedCornerShape(18.dp),
                                                    color = if (copiesMode == "AUTO") studioPrimaryContainer else Color.Transparent
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text("⚡", fontSize = 10.sp)
                                                        Text(
                                                            text = "Auto (${currentProduct.stockQty.toInt()})",
                                                            fontSize = 10.sp,
                                                            fontWeight = if (copiesMode == "AUTO") FontWeight.ExtraBold else FontWeight.Medium,
                                                            color = if (copiesMode == "AUTO") studioPrimary else studioTextMuted
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    onClick = {
                                                        copiesMode = "MANUAL"
                                                        printCopies = manualCopies.coerceIn(1, 500)
                                                    },
                                                    shape = RoundedCornerShape(18.dp),
                                                    color = if (copiesMode == "MANUAL") studioPrimaryContainer else Color.Transparent
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text("✏️", fontSize = 10.sp)
                                                        Text(
                                                            text = "Manual",
                                                            fontSize = 10.sp,
                                                            fontWeight = if (copiesMode == "MANUAL") FontWeight.ExtraBold else FontWeight.Medium,
                                                            color = if (copiesMode == "MANUAL") studioPrimary else studioTextMuted
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Stepper Row + Chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = studioFieldBg,
                                            border = BorderStroke(1.dp, studioCardBorder)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (printCopies > 1) {
                                                            val c = printCopies - 1
                                                            printCopies = c
                                                            manualCopies = c
                                                            copiesMode = "MANUAL"
                                                        }
                                                    },
                                                    enabled = printCopies > 1,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Remove,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (printCopies > 1) studioTextMain else studioTextMuted.copy(alpha = 0.3f)
                                                    )
                                                }
                                                BasicTextField(
                                                    value = "$printCopies",
                                                    onValueChange = { str ->
                                                        val n = str.filter { it.isDigit() }.toIntOrNull()
                                                        if (n != null && n in 1..9999) {
                                                            printCopies = n.coerceIn(1, 500)
                                                            manualCopies = printCopies
                                                            copiesMode = "MANUAL"
                                                        }
                                                    },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    singleLine = true,
                                                    textStyle = TextStyle(
                                                        textAlign = TextAlign.Center,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 13.5.sp,
                                                        color = studioTextMain
                                                    ),
                                                    modifier = Modifier.width(36.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        if (printCopies < 500) {
                                                            val c = printCopies + 1
                                                            printCopies = c
                                                            manualCopies = c
                                                            copiesMode = "MANUAL"
                                                        }
                                                    },
                                                    enabled = printCopies < 500,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = if (printCopies < 500) studioTextMain else studioTextMuted.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val chips = listOf(1, 2, 3, 5, 12, 50, 100)
                                            chips.forEach { chipVal ->
                                                val isChipSelected = printCopies == chipVal && copiesMode == "MANUAL"
                                                Surface(
                                                    onClick = {
                                                        printCopies = chipVal
                                                        manualCopies = chipVal
                                                        copiesMode = "MANUAL"
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isChipSelected) studioPrimaryContainer else studioFieldBg,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isChipSelected) studioPrimaryBorder else studioCardBorder
                                                    )
                                                ) {
                                                    Text(
                                                        text = "$chipVal",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isChipSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isChipSelected) studioPrimary else studioTextMuted,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                            if (currentProduct.stockQty > 0) {
                                                val isStockSelected = copiesMode == "AUTO"
                                                Surface(
                                                    onClick = {
                                                        copiesMode = "AUTO"
                                                        printCopies = currentProduct.stockQty.toInt().coerceAtLeast(1)
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isStockSelected) studioPrimaryContainer else studioFieldBg,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isStockSelected) studioPrimaryBorder else studioCardBorder
                                                    )
                                                ) {
                                                    Text(
                                                        text = "Stock (${currentProduct.stockQty.toInt()})",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isStockSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isStockSelected) studioPrimary else studioTextMuted,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // TAB 2: STORE & FIELDS

                            // A. Store Branding Card
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = studioCardBg,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "STORE BRANDING",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = studioPrimary
                                        )
                                        Text(
                                            text = "Top Label Header",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = studioPrimary,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { showStoreName = !showStoreName },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (showStoreName) studioPrimaryContainer else studioFieldBg,
                                            border = BorderStroke(
                                                if (showStoreName) 1.5.dp else 1.dp,
                                                if (showStoreName) studioPrimaryBorder else studioCardBorder
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                if (showStoreName) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = studioPrimary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = stringResource(R.string.products_show_store_name),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (showStoreName) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (showStoreName) studioPrimary else studioTextMuted,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { showLogo = !showLogo },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (showLogo) studioPrimaryContainer else studioFieldBg,
                                            border = BorderStroke(
                                                if (showLogo) 1.5.dp else 1.dp,
                                                if (showLogo) studioPrimaryBorder else studioCardBorder
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                if (showLogo) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = studioPrimary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(
                                                    text = stringResource(R.string.show_store_logo),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (showLogo) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (showLogo) studioPrimary else studioTextMuted,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(visible = showStoreName) {
                                        OutlinedTextField(
                                            value = storeNameInput,
                                            onValueChange = { storeNameInput = it.take(80) },
                                            label = { Text(stringResource(R.string.products_store_name_label)) },
                                            placeholder = { Text(configuredStoreName.ifBlank { "Techyfi POS" }) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            // B. Label Dimensions Dropdown Card (All 8 Thermal Sizes)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = studioCardBg,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "LABEL DIMENSIONS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = studioPrimary
                                        )
                                        Text(
                                            text = "Standard Thermal Sizes",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 10.sp
                                        )
                                    }

                                    // Dimensions Dropdown Trigger
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Surface(
                                            onClick = { labelSizeDropdownExpanded = true },
                                            shape = RoundedCornerShape(10.dp),
                                            color = studioFieldBg,
                                            border = BorderStroke(1.5.dp, studioPrimaryBorder.copy(alpha = 0.6f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(text = "📐", fontSize = 16.sp)
                                                    Column {
                                                        Text(
                                                            text = when (labelSize) {
                                                                ProductCodeLabelSize.SIZE_25_X_15 -> "25 × 15 mm"
                                                                ProductCodeLabelSize.SIZE_30_X_20 -> "30 × 20 mm"
                                                                ProductCodeLabelSize.SIZE_40_X_25 -> "40 × 25 mm"
                                                                ProductCodeLabelSize.SIZE_45_X_35 -> "45 × 35 mm"
                                                                ProductCodeLabelSize.SIZE_50_X_30 -> "50 × 30 mm"
                                                                ProductCodeLabelSize.SIZE_60_X_40 -> "60 × 40 mm"
                                                                ProductCodeLabelSize.SIZE_100_X_150 -> "100 × 150 mm"
                                                                ProductCodeLabelSize.CUSTOM -> "Custom Dimensions"
                                                            },
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = studioTextMain
                                                        )
                                                        Text(
                                                            text = when (labelSize) {
                                                                ProductCodeLabelSize.SIZE_25_X_15 -> "Jewelry Mini"
                                                                ProductCodeLabelSize.SIZE_30_X_20 -> "Small Item"
                                                                ProductCodeLabelSize.SIZE_40_X_25 -> "Compact"
                                                                ProductCodeLabelSize.SIZE_45_X_35 -> "Square / Box"
                                                                ProductCodeLabelSize.SIZE_50_X_30 -> "Wide Standard"
                                                                ProductCodeLabelSize.SIZE_60_X_40 -> "Apparel Hangtag"
                                                                ProductCodeLabelSize.SIZE_100_X_150 -> "Shipping Roll"
                                                                ProductCodeLabelSize.CUSTOM -> "User Defined Width × Height"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = studioTextMuted
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    imageVector = if (labelSizeDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = studioPrimary
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = labelSizeDropdownExpanded,
                                            onDismissRequest = { labelSizeDropdownExpanded = false }
                                        ) {
                                            ProductCodeLabelSize.entries.forEach { size ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = when (size) {
                                                                    ProductCodeLabelSize.SIZE_25_X_15 -> "25 × 15 mm"
                                                                    ProductCodeLabelSize.SIZE_30_X_20 -> "30 × 20 mm"
                                                                    ProductCodeLabelSize.SIZE_40_X_25 -> "40 × 25 mm"
                                                                    ProductCodeLabelSize.SIZE_45_X_35 -> "45 × 35 mm"
                                                                    ProductCodeLabelSize.SIZE_50_X_30 -> "50 × 30 mm"
                                                                    ProductCodeLabelSize.SIZE_60_X_40 -> "60 × 40 mm"
                                                                    ProductCodeLabelSize.SIZE_100_X_150 -> "100 × 150 mm"
                                                                    ProductCodeLabelSize.CUSTOM -> "Custom Dimensions"
                                                                },
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = when (size) {
                                                                    ProductCodeLabelSize.SIZE_25_X_15 -> "Jewelry Mini"
                                                                    ProductCodeLabelSize.SIZE_30_X_20 -> "Small Item"
                                                                    ProductCodeLabelSize.SIZE_40_X_25 -> "Compact"
                                                                    ProductCodeLabelSize.SIZE_45_X_35 -> "Square / Box"
                                                                    ProductCodeLabelSize.SIZE_50_X_30 -> "Wide Standard"
                                                                    ProductCodeLabelSize.SIZE_60_X_40 -> "Apparel Hangtag"
                                                                    ProductCodeLabelSize.SIZE_100_X_150 -> "Shipping Roll"
                                                                    ProductCodeLabelSize.CUSTOM -> "User Defined Width × Height"
                                                                },
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    leadingIcon = { Text("📐") },
                                                    trailingIcon = if (size == labelSize) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                                    } else null,
                                                    onClick = {
                                                        labelSizeName = size.name
                                                        labelSizeDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Custom mm inputs when Custom is selected
                                    AnimatedVisibility(visible = labelSize == ProductCodeLabelSize.CUSTOM) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = customWidthInput,
                                                onValueChange = { value ->
                                                    if (value.matches(Regex("\\d{0,3}(\\.\\d?)?"))) customWidthInput = value
                                                },
                                                label = { Text(stringResource(R.string.products_width_mm_label), fontSize = 11.sp) },
                                                isError = isCustomWidthError,
                                                supportingText = {
                                                    if (isCustomWidthError) {
                                                        Text(stringResource(R.string.custom_width_range_error), color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
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
                                                label = { Text(stringResource(R.string.products_height_mm_label), fontSize = 11.sp) },
                                                isError = isCustomHeightError,
                                                supportingText = {
                                                    if (isCustomHeightError) {
                                                        Text(stringResource(R.string.custom_height_range_error), color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                                    }
                                                },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // C. Additional Custom Field Card (Symmetrical 2-Column + Presets)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = studioCardBg,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ADDITIONAL CUSTOM FIELD",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = studioPrimary
                                        )
                                        Text(
                                            text = "Optional",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = studioTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }

                                    // 2-Column Balanced Grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = extraFieldTitle,
                                            onValueChange = { extraFieldTitle = it.take(30) },
                                            label = { Text("Field Label", fontSize = 11.sp) },
                                            placeholder = { Text("e.g. Batch", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = extraFieldValue,
                                            onValueChange = { extraFieldValue = it.take(40) },
                                            label = { Text("Field Value", fontSize = 11.sp) },
                                            placeholder = { Text("e.g. #B2026", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Quick Preset Buttons
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Presets:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = studioTextMuted,
                                            fontSize = 10.sp
                                        )
                                        val quickPresets = listOf(
                                            "Batch" to "#B2026",
                                            "Size" to "XL",
                                            "Weight" to "500g",
                                            "Origin" to "BD"
                                        )
                                        quickPresets.forEach { (lbl, defaultVal) ->
                                            Surface(
                                                onClick = {
                                                    extraFieldTitle = lbl
                                                    if (extraFieldValue.isBlank()) extraFieldValue = defaultVal
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = studioFieldBg,
                                                border = BorderStroke(1.dp, studioCardBorder)
                                            ) {
                                                Text(
                                                    text = "+ $lbl",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = studioPrimary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        if (extraFieldTitle.isNotBlank() || extraFieldValue.isNotBlank()) {
                                            Surface(
                                                onClick = {
                                                    extraFieldTitle = ""
                                                    extraFieldValue = ""
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            ) {
                                                Text(
                                                    text = "✕",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // TAB 3: DATES & SCALE

                            // A. Dates & Tracking Card
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = studioCardBg,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "DATES & TRACKING",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = studioPrimary
                                        )
                                        FilterChip(
                                            selected = showDateTracking,
                                            onClick = { showDateTracking = !showDateTracking },
                                            label = { Text("Enable Dates", fontSize = 11.sp) },
                                            leadingIcon = if (showDateTracking) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            modifier = Modifier.height(28.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = showDateTracking) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.products_date_tracking_info_label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = studioTextMuted
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    FilterChip(
                                                        selected = mfgOrPkgType == "Mfg",
                                                        onClick = { mfgOrPkgType = "Mfg" },
                                                        label = { Text(stringResource(R.string.products_mfg_abbr), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                        leadingIcon = { Animated3DFactoryIcon() },
                                                        modifier = Modifier.height(28.dp)
                                                    )
                                                    FilterChip(
                                                        selected = mfgOrPkgType == "Pkg",
                                                        onClick = { mfgOrPkgType = "Pkg" },
                                                        label = { Text(stringResource(R.string.products_pkg_abbr), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                        leadingIcon = { Animated3DPackageIcon(fontSize = 12.sp) },
                                                        modifier = Modifier.height(28.dp)
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

                            // B. Element Scaling Card (Compact Clean Header)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = studioCardBg,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, studioCardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.element_scaling_title).uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = studioPrimary
                                        )
                                        TextButton(
                                            onClick = {
                                                productNameTextScale = 1.0f
                                                priceTextScale = 1.0f
                                                codeNumberTextScale = 1.0f
                                                storeLogoScale = 1.0f
                                                storeNameTextScale = 1.0f
                                                dateTrackingTextScale = 1.0f
                                                barcodeGraphicScale = 1.0f
                                                extraFieldTextScale = 1.0f
                                            }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.reset_all_sizes),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF38BDF8)
                                            )
                                        }
                                    }

                                    // All 8 Scalers
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
                                        label = stringResource(R.string.store_logo_scale),
                                        scale = storeLogoScale,
                                        enabled = showLogo,
                                        onScaleChange = { storeLogoScale = it },
                                        onReset = { storeLogoScale = 1.0f }
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
                                    LabelTextSizeControl(
                                        label = stringResource(R.string.extra_field_scale),
                                        scale = extraFieldTextScale,
                                        enabled = extraFieldTitle.isNotBlank() || extraFieldValue.isNotBlank(),
                                        onScaleChange = { extraFieldTextScale = it },
                                        onReset = { extraFieldTextScale = 1.0f }
                                    )
                                }
                            }
                        }
                    }
                } // End of scrollable tab Column

                HorizontalDivider(color = studioCardBorder)

                // 7. BOTTOM ACTION BAR
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
                            val printEnabled = !isPrinting && !isSharingPdf && !isSingleCopiesError && !isCustomWidthError && !isCustomHeightError
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = if (printEnabled) 4.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (printEnabled) studioPrimaryGradient
                                            else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f)))
                                        )
                                        .clickable(enabled = printEnabled) {
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
                                                                        productName = currentProduct.name,
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
                                                            jobName = context.getString(R.string.print_job_product_label_fmt, currentProduct.name),
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
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.print_copies_button_fmt, printCopies),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Share Button
                        val shareEnabled = !isPrinting && !isSingleCopiesError && !isCustomWidthError && !isCustomHeightError
                        Surface(
                            onClick = {
                                if (isSharingPdf) {
                                    sharePdfJob?.cancel()
                                    isSharingPdf = false
                                    return@Surface
                                }
                                val currentBmp = bitmap ?: return@Surface
                                sharePdfJob = scope.launch {
                                    isSharingPdf = true
                                    try {
                                        val cached = withContext(Dispatchers.IO) {
                                            cacheProductCodeLabelPdf(
                                                context = context,
                                                bitmap = currentBmp,
                                                productName = currentProduct.name,
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
                                                currentProduct.name
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
                            enabled = shareEnabled,
                            shape = RoundedCornerShape(12.dp),
                            color = studioCardBg,
                            border = BorderStroke(1.dp, studioCardBorder),
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isSharingPdf) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = studioPrimary)
                                } else {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = stringResource(R.string.share_code_image),
                                        tint = if (shareEnabled) studioTextMain else studioTextMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Download PNG Button
                        val saveEnabled = !isSaving
                        Surface(
                            onClick = {
                                val currentBmp = bitmap ?: return@Surface
                                scope.launch {
                                    isSaving = true
                                    val savedUri = withContext(Dispatchers.IO) {
                                        saveProductCodeLabelToPictures(context, currentBmp, currentProduct.name, codeType)
                                    }
                                    isSaving = false
                                    actionStatus = if (savedUri != null) {
                                        ActionStatus(context.getString(R.string.code_image_saved), isError = false)
                                    } else {
                                        ActionStatus(context.getString(R.string.code_image_save_failed), isError = true)
                                    }
                                }
                            },
                            enabled = saveEnabled,
                            shape = RoundedCornerShape(12.dp),
                            color = studioCardBg,
                            border = BorderStroke(1.dp, studioCardBorder),
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = studioPrimary)
                                } else {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = stringResource(R.string.save_png),
                                        tint = if (saveEnabled) studioTextMain else studioTextMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                actionStatus?.let { status ->
                    Text(
                        status.text,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = if (status.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // 8. HIGH RESOLUTION ZOOM INSPECT MODAL
    if (showZoomModal && bitmap != null) {
        Dialog(
            onDismissRequest = { showZoomModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.80f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.zoom_inspect_dialog_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedLabelWidthMm.toInt()} × ${selectedLabelHeightMm.toInt()} mm • ${invoiceSettings.labelDpi} DPI Dot Simulation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showZoomModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.product_code_image_preview),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Button(
                        onClick = { showZoomModal = false },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.dismiss), fontWeight = FontWeight.Bold)
                    }
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

