package com.example.techyfipos.ui.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
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
import com.example.techyfipos.utils.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
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
                                    Toast.makeText(context, context.getString(R.string.toast_failed_batch_labels_pdf), Toast.LENGTH_SHORT).show()
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
    val invoiceSettings by remember(context) { SettingsDataStore(context).invoiceSettingsDataFlow }
        .collectAsStateWithLifecycle(initialValue = InvoiceSettingsData())
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
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
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
                                    if (bitmap != null) {
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
                                        val pdfFallback = cached?.first
                                        if (pdfFallback != null) {
                                            val opened = printLabelImages(
                                                context = context,
                                                activity = activity ?: context.findActivity(),
                                                labels = listOf(bitmap),
                                                pdfFallback = pdfFallback,
                                                widthMm = selectedLabelWidthMm,
                                                heightMm = selectedLabelHeightMm,
                                                copies = 1,
                                                jobName = "${product.name} label",
                                                settings = invoiceSettings
                                            )
                                            if (!opened) {
                                                actionStatus = context.getString(R.string.code_image_print_failed)
                                            }
                                        } else {
                                            actionStatus = context.getString(R.string.code_image_print_failed)
                                        }
                                    } else {
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

