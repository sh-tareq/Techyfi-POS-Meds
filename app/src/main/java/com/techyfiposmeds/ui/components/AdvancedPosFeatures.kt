package com.techyfiposmeds.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.data.db.*
import com.techyfiposmeds.ui.viewmodel.InventoryAlertsData
import com.techyfiposmeds.ui.viewmodel.PriceMode
import com.techyfiposmeds.utils.formatTaka
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PaymentMethodBrandIcon(
    method: String,
    modifier: Modifier = Modifier,
    size: Int = 20
) {
    when (method) {
        "bKash" -> Image(
            painter = painterResource(R.drawable.logo_bkash),
            contentDescription = stringResource(R.string.bkash),
            modifier = modifier.size(width = (size * 1.5).dp, height = size.dp),
            contentScale = ContentScale.Fit
        )
        "Nagad" -> Image(
            painter = painterResource(R.drawable.logo_nagad),
            contentDescription = stringResource(R.string.nagad),
            modifier = modifier.size(width = (size * 1.5).dp, height = size.dp),
            contentScale = ContentScale.Fit
        )
        "Rocket" -> Image(
            painter = painterResource(R.drawable.logo_rocket),
            contentDescription = stringResource(R.string.rocket),
            modifier = modifier.size(width = (size * 1.5).dp, height = size.dp),
            contentScale = ContentScale.Fit
        )
        "Cash" -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFDCFCE7),
            modifier = modifier.size(size.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("💵", fontSize = (size * 0.7).sp)
            }
        }
        "Card" -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFE0F2FE),
            modifier = modifier.size(size.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("💳", fontSize = (size * 0.7).sp)
            }
        }
        "Store Credit" -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFD1FAE5),
            modifier = modifier.size(size.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🪙", fontSize = (size * 0.7).sp)
            }
        }
        else -> Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFFFEF3C7),
            modifier = modifier.size(size.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("📝", fontSize = (size * 0.7).sp)
            }
        }
    }
}

/**
 * Maps a business-logic payment method/source key (e.g. "Cash", "bKash", "Due")
 * to its localized display label, without altering the underlying stored value.
 */
@Composable
private fun paymentMethodDisplayName(method: String): String = when (method) {
    "Cash" -> stringResource(R.string.cash)
    "bKash" -> stringResource(R.string.bkash)
    "Nagad" -> stringResource(R.string.nagad)
    "Rocket" -> stringResource(R.string.rocket)
    "Card" -> stringResource(R.string.card)
    "Due" -> stringResource(R.string.due)
    "Bank" -> stringResource(R.string.bank)
    "Store Credit" -> stringResource(R.string.payment_store_credit)
    else -> method
}

/**
 * Maps a business-logic expense/cash-in category key to its localized display label,
 * without altering the underlying stored value.
 */
@Composable
private fun expenseCategoryDisplayName(category: String): String = when (category) {
    "Float In" -> stringResource(R.string.category_float_in)
    "Owner Capital" -> stringResource(R.string.category_owner_capital)
    "Change Exchange" -> stringResource(R.string.category_change_exchange)
    "Tea & Refreshments" -> stringResource(R.string.category_tea_refreshments)
    "Staff Meal" -> stringResource(R.string.category_staff_meal)
    "Electricity (Token)" -> stringResource(R.string.category_electricity_token)
    "Transport" -> stringResource(R.string.category_transport)
    "Shop Rent" -> stringResource(R.string.category_shop_rent)
    "Cleaning" -> stringResource(R.string.category_cleaning)
    "Supplier Payment" -> stringResource(R.string.expense_category_supplier_payment)
    "Other" -> stringResource(R.string.category_other)
    else -> category
}

// ==========================================
// 1. HELD CARTS BOTTOM SHEET & BANNER
// ==========================================

@Composable
fun HeldCartsTopBanner(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = count.toString(),
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = pluralStringResource(R.plurals.held_carts_banner_title, count, count),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = stringResource(R.string.held_carts_banner_subtitle),
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0072FF).copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0072FF).copy(alpha = 0.5f))
            ) {
                Text(
                    text = stringResource(R.string.held_carts_banner_resume),
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeldCartsBottomSheet(
    heldCarts: List<HeldCart>,
    onResume: (HeldCart) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var cartToDelete by remember { mutableStateOf<HeldCart?>(null) }

    if (cartToDelete != null) {
        val target = cartToDelete!!
        AlertDialog(
            onDismissRequest = { cartToDelete = null },
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(stringResource(R.string.confirm_delete_held_cart_title)) },
            text = { Text(stringResource(R.string.confirm_delete_held_cart_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(target.id)
                        cartToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { cartToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .trackUserActivity()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏸️", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.held_carts_sheet_title, heldCarts.size),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description))
                }
            }
            Text(
                text = stringResource(R.string.held_carts_sheet_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (heldCarts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛒", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.held_carts_empty_message),
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(heldCarts, key = { it.id }) { cart ->
                        HeldCartCard(
                            cart = cart,
                            onResume = {
                                onResume(cart)
                                onDismiss()
                            },
                            onDelete = { cartToDelete = cart }
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeldCartCard(
    cart: HeldCart,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val itemCount = remember(cart.itemsJson) {
        try {
            JSONArray(cart.itemsJson).length()
        } catch (e: Exception) {
            0
        }
    }
    val justNowText = stringResource(R.string.held_cart_time_just_now)
    val minsAgo = remember(cart.timestamp) {
        val diff = System.currentTimeMillis() - cart.timestamp
        (diff / 60000).toInt()
    }
    val timeAgo = if (minsAgo < 1) {
        justNowText
    } else {
        pluralStringResource(R.plurals.held_cart_time_mins_ago, minsAgo, minsAgo)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = cart.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!cart.customerPhone.isNullOrBlank()) {
                    Text(
                        text = "📞 ${cart.customerPhone}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pluralStringResource(R.plurals.held_cart_items, itemCount, itemCount),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(" • ", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = "⏱️ $timeAgo",
                        fontSize = 11.5.sp,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.held_cart_delete_content_description),
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0072FF),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.held_cart_resume_button), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ==========================================
// 2. SPLIT PAYMENT CHECKOUT COMPONENT
// ==========================================

@Composable
fun SplitPaymentBuilder(
    grandTotal: Double,
    splitEntries: List<SplitPaymentEntry>,
    onEntriesChanged: (List<SplitPaymentEntry>) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCollected = splitEntries.sumOf { it.amount }
    val remaining = grandTotal - totalCollected

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💸", fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.split_payment_breakdown), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFECFDF5)
                ) {
                    Text(
                        text = stringResource(R.string.split_payment_total, formatTaka(grandTotal)),
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Payment Rows
            splitEntries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Method Selector
                    var methodExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { methodExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PaymentMethodBrandIcon(entry.method, size = 18)
                                Text(
                                    paymentMethodDisplayName(entry.method),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(
                            expanded = methodExpanded,
                            onDismissRequest = { methodExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due", "Store Credit").forEach { m ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        PaymentMethodBrandIcon(m, size = 20)
                                    },
                                    text = {
                                        Text(
                                            paymentMethodDisplayName(m),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    },
                                    onClick = {
                                        val updated = splitEntries.toMutableList()
                                        updated[index] = entry.copy(method = m)
                                        onEntriesChanged(updated)
                                        methodExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Amount input (numbers will never be cut off)
                    OutlinedTextField(
                        value = if (entry.amount == 0.0) "" else if (entry.amount % 1.0 == 0.0) entry.amount.toInt().toString() else entry.amount.toString(),
                        onValueChange = { str ->
                            val filtered = str.filter { it.isDigit() || it == '.' }
                            val amt = filtered.toDoubleOrNull() ?: 0.0
                            val updated = splitEntries.toMutableList()
                            updated[index] = entry.copy(amount = amt)
                            onEntriesChanged(updated)
                        },
                        placeholder = { Text(stringResource(R.string.split_payment_amount_placeholder), fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    // Remove button with clean surface
                    IconButton(
                        onClick = {
                            val updated = splitEntries.toMutableList()
                            updated.removeAt(index)
                            onEntriesChanged(updated)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, null, tint = Color(0xFF64748B), modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }

                // Method-specific reference details, mirroring the single-payment flow so a
                // split "Card"/wallet row is not left without the digits the receipt/ledger show.
                when (entry.method) {
                    "bKash", "Nagad", "Rocket" -> {
                        OutlinedTextField(
                            value = entry.walletPhone ?: "",
                            onValueChange = { str ->
                                val updated = splitEntries.toMutableList()
                                updated[index] = entry.copy(walletPhone = str.filter(Char::isDigit).take(11))
                                onEntriesChanged(updated)
                            },
                            label = { Text(stringResource(R.string.split_payment_wallet_number), fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, start = 2.dp, end = 2.dp)
                        )
                    }
                    "Card" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var cardTypeExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                Surface(
                                    onClick = { cardTypeExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            entry.cardType?.takeIf { it.isNotBlank() } ?: stringResource(R.string.split_payment_card_type),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                DropdownMenu(
                                    expanded = cardTypeExpanded,
                                    onDismissRequest = { cardTypeExpanded = false }
                                ) {
                                    listOf("VISA", "Mastercard", "AMEX", "Other").forEach { ct ->
                                        DropdownMenuItem(
                                            text = { Text(ct, fontSize = 13.sp) },
                                            onClick = {
                                                val updated = splitEntries.toMutableList()
                                                updated[index] = entry.copy(cardType = ct)
                                                onEntriesChanged(updated)
                                                cardTypeExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = entry.cardLastFour ?: "",
                                onValueChange = { str ->
                                    val updated = splitEntries.toMutableList()
                                    updated[index] = entry.copy(cardLastFour = str.filter(Char::isDigit).take(4))
                                    onEntriesChanged(updated)
                                },
                                label = { Text(stringResource(R.string.split_payment_last_four), fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Add Payment Row Button (only clickable on the button itself, not entire row)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = {
                        val defaultMethod = if (splitEntries.any { it.method == "Cash" }) "bKash" else "Cash"
                        val defaultAmt = if (remaining > 0) remaining else 0.0
                        onEntriesChanged(splitEntries + SplitPaymentEntry(defaultMethod, defaultAmt))
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.split_payment_add_method), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Quick Remainder Chips
            if (remaining > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.split_payment_fill_remainder), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    listOf("Cash", "bKash", "Due").forEach { m ->
                        Surface(
                            onClick = {
                                onEntriesChanged(splitEntries + SplitPaymentEntry(m, remaining))
                            },
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    stringResource(R.string.split_payment_quick_fill, paymentMethodDisplayName(m), formatTaka(remaining)),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Balance Summary
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.split_payment_collected, formatTaka(totalCollected)), color = Color.White, fontSize = 12.sp)
                    }
                    Text(
                        text = when {
                            remaining == 0.0 -> stringResource(R.string.split_payment_settled_exact)
                            remaining > 0 -> stringResource(R.string.split_payment_remaining, formatTaka(remaining))
                            else -> stringResource(R.string.split_payment_return_change, formatTaka(-remaining))
                        },
                        color = when {
                            remaining == 0.0 -> Color(0xFF34D399)
                            remaining > 0 -> Color(0xFFF87171)
                            else -> Color(0xFF38BDF8)
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. INVENTORY ALERTS CENTER BOTTOM SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryAlertsCenterBottomSheet(
    alertsData: InventoryAlertsData,
    onEditProduct: (Product) -> Unit,
    onQuickAddStock: (Product, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Expired, 1: Expiring Soon, 2: Low Stock

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .trackUserActivity()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚨", fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inventory_alerts_title, alertsData.totalCount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description))
                }
            }
            Text(
                text = stringResource(R.string.inventory_alerts_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Tabs
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.inventory_alerts_tab_expired, alertsData.expiredProducts.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.inventory_alerts_tab_expiring, alertsData.expiringSoonProducts.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            stringResource(R.string.inventory_alerts_tab_low_stock, alertsData.lowStockProducts.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            Spacer(Modifier.height(14.dp))

            val currentList = when (selectedTab) {
                0 -> alertsData.expiredProducts
                1 -> alertsData.expiringSoonProducts
                else -> alertsData.lowStockProducts
            }

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✨", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (selectedTab) {
                                0 -> stringResource(R.string.inventory_alerts_empty_expired)
                                1 -> stringResource(R.string.inventory_alerts_empty_expiring)
                                else -> stringResource(R.string.inventory_alerts_empty_low_stock)
                            },
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentList, key = { it.barcode }) { product ->
                        InventoryAlertItemCard(
                            product = product,
                            alertType = selectedTab,
                            onEdit = {
                                onEditProduct(product)
                                onDismiss()
                            },
                            onQuickRestock = { onQuickAddStock(product, 10) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun InventoryAlertItemCard(
    product: Product,
    alertType: Int, // 0: Expired, 1: Expiring Soon, 2: Low Stock
    onEdit: () -> Unit,
    onQuickRestock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (alertType) {
                0 -> Color(0xFFE11D48)
                1 -> Color(0xFFD97706)
                else -> Color(0xFF0072FF)
            }.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Full Product Name with multi-line support
            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            // 2. Alert Info Pill
            val notAvailable = stringResource(R.string.common_not_available)
            val qtyStr = if (product.stockQty % 1.0 == 0.0) product.stockQty.toLong().toString() else String.format(Locale.US, "%.2f", product.stockQty).trimEnd('0').trimEnd('.')
            val reorderStr = product.reorderLevel.toString()

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (alertType) {
                    0 -> Color(0xFFFFF1F2)
                    1 -> Color(0xFFFFFBEB)
                    else -> Color(0xFFEFF6FF)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (alertType) {
                            0 -> stringResource(R.string.inventory_alert_expired_on, product.expiryDate ?: notAvailable)
                            1 -> stringResource(R.string.inventory_alert_expiring_on, product.expiryDate ?: notAvailable)
                            else -> stringResource(R.string.inventory_alert_stock_info, qtyStr, product.unit, reorderStr)
                        },
                        fontSize = 12.sp,
                        color = when (alertType) {
                            0 -> Color(0xFFE11D48)
                            1 -> Color(0xFFD97706)
                            else -> Color(0xFF1D4ED8)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 3. Price, Barcode & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "${formatTaka(product.sellingPrice)}${if (product.unit.isNotBlank()) "/${product.unit}" else ""}",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (product.barcode.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.barcode_label_fmt, product.barcode),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Text(stringResource(R.string.inventory_alert_edit_button), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (alertType == 2) {
                        Button(
                            onClick = onQuickRestock,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF059669),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(stringResource(R.string.inventory_alert_add_stock_button), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 1. PETTY CASH & STORE EXPENSES (CASH IN / OUT)
// =========================================================================

@Composable
fun ExpenseSummaryCard(
    drawerCash: Double,
    totalCashIn: Double,
    totalCashOut: Double,
    onAddExpense: () -> Unit,
    onAddCashIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF172554))
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Realistic3DCashVaultRegisterIcon(size = 46.dp)
                        Column {
                            Text(
                                text = stringResource(R.string.expense_today_drawer_cash),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = formatTaka(drawerCash),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF34D399), CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.expense_live_synced),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }

                // 2-Column Cash Velocity Flow Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Inflow Tile
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Realistic3DCashInStashIcon(size = 28.dp)
                            Column {
                                Text(stringResource(R.string.expense_cash_in_label), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                Text(
                                    text = "+${formatTaka(totalCashIn)}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    }

                    // Outflow Tile
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Realistic3DExpenseReceiptIcon(size = 28.dp)
                            Column {
                                Text(stringResource(R.string.expense_cash_out_label), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                Text(
                                    text = "-${formatTaka(totalCashOut)}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFB7185)
                                )
                            }
                        }
                    }
                }

                // Dual Ergonomic Action CTAs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddCashIn,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(stringResource(R.string.expense_add_cash_in_button), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }

                    Button(
                        onClick = onAddExpense,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(stringResource(R.string.expense_add_expense_button), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AddExpenseDialog(
    initialExpense: Expense? = null,
    isCashIn: Boolean = false,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (title: String, category: String, amount: Double, source: String, note: String?) -> Unit
) {
    var title by remember(initialExpense) { mutableStateOf(initialExpense?.title ?: "") }
    var amountText by remember(initialExpense) {
        mutableStateOf(
            initialExpense?.let {
                if (it.amount == it.amount.toLong().toDouble()) it.amount.toLong().toString() else it.amount.toString()
            } ?: ""
        )
    }
    var selectedCategory by remember(initialExpense) {
        mutableStateOf(
            initialExpense?.category ?: if (isCashIn) "Float In" else "Tea & Refreshments"
        )
    }
    var selectedSource by remember(initialExpense) { mutableStateOf(initialExpense?.paymentSource ?: "Cash") }
    var note by remember(initialExpense) { mutableStateOf(initialExpense?.note ?: "") }
    val savingGuard = isSaving

    val categories = if (isCashIn) {
        listOf("Float In", "Owner Capital", "Change Exchange", "Other")
    } else {
        MANUAL_EXPENSE_CATEGORIES
    }

    val sources = listOf("Cash", "bKash", "Nagad", "Bank")

    AlertDialog(
        modifier = Modifier.trackUserActivity(),
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialExpense != null) {
                    Realistic3DExpenseReceiptIcon(size = 32.dp)
                    Text(stringResource(R.string.expense_edit_title), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                } else if (isCashIn) {
                    Realistic3DCashInStashIcon(size = 32.dp)
                    Text(stringResource(R.string.expense_dialog_title_cash_in), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                } else {
                    Realistic3DExpenseReceiptIcon(size = 32.dp)
                    Text(stringResource(R.string.expense_dialog_title_expense), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Category Selector Chips
                Text(stringResource(R.string.expense_category_label), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = cat
                                if (title.isBlank()) title = cat
                            },
                            label = { Text(expenseCategoryDisplayName(cat), fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.expense_title_label)) },
                    placeholder = { Text(stringResource(R.string.expense_title_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = com.techyfiposmeds.utils.sanitizeNumericInput(it) },
                    label = { Text(stringResource(R.string.expense_amount_label)) },
                    placeholder = { Text(stringResource(R.string.expense_amount_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.expense_payment_source_label), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sources.forEach { src ->
                        FilterChip(
                            selected = selectedSource == src,
                            onClick = { selectedSource = src },
                            label = { Text(paymentMethodDisplayName(src), fontSize = 11.5.sp, fontWeight = if (selectedSource == src) FontWeight.Bold else FontWeight.Normal, maxLines = 1) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.expense_optional_note_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (savingGuard) return@Button
                    val amt = com.techyfiposmeds.utils.sanitizeNumericInput(amountText).toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        onSave(
                            if (title.isNotBlank()) title else selectedCategory,
                            selectedCategory,
                            amt,
                            selectedSource,
                            note.takeIf { it.isNotBlank() }
                        )
                    }
                },
                enabled = !savingGuard,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCashIn) Color(0xFF059669) else Color(0xFFE11D48)
                )
            ) {
                if (savingGuard) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (initialExpense != null) stringResource(R.string.save)
                        else if (isCashIn) stringResource(R.string.expense_save_cash_in)
                        else stringResource(R.string.expense_save_expense),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !savingGuard
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ExpenseItemRow(
    expense: Expense,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val isCashIn = expense.type == "CASH_IN"
    val isSupplierPayment = expense.category.equals("Supplier Payment", ignoreCase = true) &&
        (expense.title.startsWith("Supplier Invoice #") || expense.title.startsWith("Supplier Payment: "))
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        onClick = { if (!isSupplierPayment) onEdit?.invoke() },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCashIn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isCashIn) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCashIn) {
                            Realistic3DCashInStashIcon(size = 28.dp)
                        } else {
                            Realistic3DExpenseReceiptIcon(size = 28.dp)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = expense.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Source Badge
                        val isDarkExpense = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (expense.paymentSource.lowercase()) {
                                "bkash" -> if (isDarkExpense) Color(0xFF831843).copy(alpha = 0.35f) else Color(0xFFFDF2F8)
                                "nagad" -> if (isDarkExpense) Color(0xFF7C2D12).copy(alpha = 0.35f) else Color(0xFFFFF7ED)
                                "bank" -> if (isDarkExpense) Color(0xFF1E3A8A).copy(alpha = 0.35f) else Color(0xFFEFF6FF)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = paymentMethodDisplayName(expense.paymentSource),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (expense.paymentSource.lowercase()) {
                                    "bkash" -> if (isDarkExpense) Color(0xFFF472B6) else Color(0xFFDB2777)
                                    "nagad" -> if (isDarkExpense) Color(0xFFFB923C) else Color(0xFFEA580C)
                                    "bank" -> if (isDarkExpense) Color(0xFF93C5FD) else Color(0xFF2563EB)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    val isBangla = com.techyfiposmeds.utils.isAppBangla()
                    val expenseDateLabel = remember(expense.timestamp, isBangla) {
                        val expenseInstant = java.time.Instant.ofEpochMilli(expense.timestamp)
                        val expenseDate = expenseInstant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        val today = java.time.LocalDate.now()
                        val diffDays = java.time.temporal.ChronoUnit.DAYS.between(expenseDate, today)
                        val timeStr = SimpleDateFormat("hh:mm a", if (isBangla) Locale.forLanguageTag("bn") else Locale.getDefault()).format(Date(expense.timestamp))
                        when (diffDays) {
                            0L -> timeStr
                            1L -> context.getString(R.string.date_yesterday_fmt, timeStr)
                            else -> "${SimpleDateFormat("dd MMM", if (isBangla) Locale.forLanguageTag("bn") else Locale.getDefault()).format(Date(expense.timestamp))}, $timeStr"
                        }
                    }
                    Text(
                        text = "${expenseCategoryDisplayName(expense.category)} • $expenseDateLabel",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${if (isCashIn) "+" else "-"}${formatTaka(expense.amount)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (isCashIn) Color(0xFF059669) else Color(0xFFE11D48)
                )
                if (!isSupplierPayment && onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.expense_edit_title),
                            tint = Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (!isSupplierPayment && onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.expense_delete_confirm_title)) },
            text = { Text(stringResource(R.string.expense_delete_confirm_message, expense.title, formatTaka(expense.amount))) },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete?.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// =========================================================================
// 2. SUPPLIER / VENDOR INFLOW & PURCHASE MANAGEMENT
// =========================================================================

@Composable
fun SupplierCard(
    supplier: Supplier,
    onAddInflow: () -> Unit,
    onPayDue: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3D Avatar Box
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F9FF))
                            .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Realistic3DCargoInflowTruckIcon(size = 28.dp)
                    }

                    Column {
                        Text(
                            text = supplier.companyName.ifBlank { supplier.name },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (supplier.name.isNotBlank() && supplier.name != supplier.companyName) {
                            Text(
                                text = stringResource(R.string.supplier_rep_format, supplier.name, supplier.phone),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.supplier_phone_format, supplier.phone),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Due / Settled Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (supplier.totalDue > 0) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, if (supplier.totalDue > 0) Color(0xFFFDE68A) else Color(0xFFBBF7D0))
                ) {
                    Text(
                        text = if (supplier.totalDue > 0) stringResource(R.string.supplier_due_format, formatTaka(supplier.totalDue)) else stringResource(R.string.supplier_all_clear),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (supplier.totalDue > 0) Color(0xFF92400E) else Color(0xFF166534),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Purchase Metric Row
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.supplier_total_inward_purchases),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTaka(supplier.totalPurchased),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAddInflow,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF0F9FF),
                        contentColor = Color(0xFF0284C7)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.supplier_add_inward_bill), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }

                if (supplier.totalDue > 0) {
                    Button(
                        onClick = onPayDue,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.pay_due), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                if (onEdit != null) {
                    FilledTonalIconButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.supplier_edit_title),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (supplier.phone.isNotBlank()) {
                    FilledTonalIconButton(
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:${supplier.phone.trim()}")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = stringResource(R.string.supplier_call_vendor_content_description),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InwardStockDialog(
    supplier: Supplier,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (invoiceNum: String, total: Double, paid: Double, itemsSummary: String, method: String, stockLines: List<PurchaseStockLine>) -> Unit
) {
    var invoiceNumber by remember { mutableStateOf("INV-${System.currentTimeMillis() % 100000}") }
    var totalAmountText by remember { mutableStateOf("") }
    var paidAmountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var selectedBarcode by remember(products) { mutableStateOf(products.firstOrNull()?.barcode.orEmpty()) }
    var productSearchQuery by remember { mutableStateOf("") }
    var productMenuExpanded by remember { mutableStateOf(false) }

    var quantityText by remember { mutableStateOf("") }
    var selectedUnitType by remember { mutableStateOf("strip") } // "pc", "strip", "box"
    var unitCostText by remember { mutableStateOf("") }
    var tradeDiscountText by remember { mutableStateOf("0") }
    var batchNumberText by remember { mutableStateOf("BX-${(System.currentTimeMillis() % 10000).toString().padStart(4, '0')}") }
    var expiryDateText by remember { mutableStateOf("2027-12-31") }

    var stockLines by remember { mutableStateOf<List<PurchaseStockLine>>(emptyList()) }

    val filteredProducts = remember(products, productSearchQuery) {
        if (productSearchQuery.isBlank()) products.take(50)
        else products.filter {
            it.name.contains(productSearchQuery, ignoreCase = true) ||
            it.barcode.contains(productSearchQuery, ignoreCase = true) ||
            (it.genericName?.contains(productSearchQuery, ignoreCase = true) == true)
        }.take(50)
    }

    val selectedProduct = remember(products, selectedBarcode) {
        products.firstOrNull { it.barcode == selectedBarcode }
    }

    fun recalculateTotal(lines: List<PurchaseStockLine>) {
        val total = lines.sumOf { line ->
            val discFactor = (1.0 - (line.tradeDiscountPercent / 100.0)).coerceIn(0.0, 1.0)
            line.quantity * line.unitCost * discFactor
        }
        totalAmountText = if (total > 0.0) String.format(java.util.Locale.US, "%.2f", total) else ""
        if (paidAmountText.isBlank() || (paidAmountText.toDoubleOrNull() ?: 0.0) > total) {
            paidAmountText = totalAmountText
        }
    }

    AlertDialog(
        modifier = Modifier.trackUserActivity(),
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.supplier_receive_stock_title, supplier.companyName),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "ফার্মাসিউটিক্যাল চালান ও স্টক ইনওয়ার্ড (ব্যাচ ও টিপি সহ)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text(stringResource(R.string.supplier_invoice_number_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Product Selection
                Text(stringResource(R.string.supplier_purchase_stock_items), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { productMenuExpanded = true },
                        enabled = products.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedProduct?.let { "${it.name} (${it.barcode})" }
                                ?: stringResource(R.string.supplier_no_products_available),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = productMenuExpanded,
                        onDismissRequest = { productMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 350.dp).fillMaxWidth(0.9f)
                    ) {
                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            placeholder = { Text("ওষুধ অনুসন্ধান করুন...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        filteredProducts.forEach { product ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(
                                            "${product.dosageForm ?: ""} · ${product.strength ?: ""} · ${formatTaka(product.purchasePrice)}",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                },
                                onClick = {
                                    selectedBarcode = product.barcode
                                    unitCostText = product.purchasePrice.takeIf { it > 0.0 }?.toString().orEmpty()
                                    productMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Unit Selector & Quantity Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val units = listOf("pc" to "পিস", "strip" to "পাতা", "box" to "বক্স")
                    units.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedUnitType == key,
                            onClick = { selectedUnitType = key },
                            label = { Text(label, fontSize = 11.5.sp) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("পরিমাণ (${if (selectedUnitType == "strip") "পাতা" else if (selectedUnitType == "box") "বক্স" else "পিস"})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unitCostText,
                        onValueChange = { unitCostText = it },
                        label = { Text("টিপি / ক্রয়মূল্য (৳/পিস)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Batch Number & Expiry Date Row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = batchNumberText,
                        onValueChange = { batchNumberText = it },
                        label = { Text("ব্যাচ নং (Batch)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.1f),
                        trailingIcon = {
                            IconButton(onClick = {
                                batchNumberText = "BX-${(System.currentTimeMillis() % 100000).toString().padStart(5, '0')}"
                            }) {
                                Text("🎲", fontSize = 14.sp)
                            }
                        }
                    )
                    OutlinedTextField(
                        value = expiryDateText,
                        onValueChange = { expiryDateText = it },
                        label = { Text("মেয়াদ (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.1f)
                    )
                }

                // Trade Discount Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tradeDiscountText,
                        onValueChange = { tradeDiscountText = it },
                        label = { Text("টিপি ডিসকাউন্ট (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    val rawQty = quantityText.toDoubleOrNull() ?: 0.0
                    val mult = when (selectedUnitType) {
                        "strip" -> selectedProduct?.stripSize?.toDouble() ?: 10.0
                        "box" -> {
                            val bp = selectedProduct?.mrpPrice?.takeIf { it > 0.0 } ?: selectedProduct?.purchasePrice?.takeIf { it > 0.0 } ?: 0.0
                            if ((selectedProduct?.packPrice ?: 0.0) > 0.0 && bp > 0.0) ((selectedProduct!!.packPrice / bp)).coerceAtLeast(10.0)
                            else (selectedProduct?.stripSize ?: 10) * 10.0
                        }
                        else -> 1.0
                    }
                    val basePcs = rawQty * mult
                    val cost = unitCostText.toDoubleOrNull() ?: 0.0
                    val disc = (tradeDiscountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                    val estLineTotal = basePcs * cost * (1.0 - (disc / 100.0))

                    Column(Modifier.weight(1f)) {
                        Text("মোট পিস: ${if (basePcs % 1.0 == 0.0) basePcs.toLong().toString() else "%.1f".format(basePcs)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("লাইন মূল্য: ${formatTaka(estLineTotal)}", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val rawQty = quantityText.toDoubleOrNull() ?: 0.0
                        val unitCost = unitCostText.toDoubleOrNull() ?: 0.0
                        val disc = (tradeDiscountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                        if (selectedBarcode.isNotBlank() && rawQty > 0.0 && unitCost >= 0.0) {
                            val mult = when (selectedUnitType) {
                                "strip" -> selectedProduct?.stripSize?.toDouble() ?: 10.0
                                "box" -> {
                                    val bp = selectedProduct?.mrpPrice?.takeIf { it > 0.0 } ?: selectedProduct?.purchasePrice?.takeIf { it > 0.0 } ?: 0.0
                                    if ((selectedProduct?.packPrice ?: 0.0) > 0.0 && bp > 0.0) ((selectedProduct!!.packPrice / bp)).coerceAtLeast(10.0)
                                    else (selectedProduct?.stripSize ?: 10) * 10.0
                                }
                                else -> 1.0
                            }
                            val baseQuantity = rawQty * mult
                            val newLine = PurchaseStockLine(
                                productBarcode = selectedBarcode,
                                quantity = baseQuantity,
                                unitCost = unitCost,
                                batchNumber = batchNumberText.trim().ifBlank { "BATCH-${System.currentTimeMillis() % 10000}" },
                                expiryDate = expiryDateText.trim().ifBlank { "2027-12-31" },
                                tradeDiscountPercent = disc
                            )
                            val updated = stockLines + newLine
                            stockLines = updated
                            recalculateTotal(updated)
                            quantityText = ""
                            batchNumberText = "BX-${(System.currentTimeMillis() % 10000).toString().padStart(4, '0')}"
                        }
                    },
                    enabled = products.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ তালিকায় ইনওয়ার্ড আইটেম যোগ করুন")
                }

                // Inwarded Stock Lines List
                if (stockLines.isNotEmpty()) {
                    Text("সংযুক্ত আইটেমসমূহ (${stockLines.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                stockLines.forEachIndexed { index, line ->
                    val productName = products.firstOrNull { it.barcode == line.productBarcode }?.name ?: line.productBarcode
                    val discFactor = (1.0 - (line.tradeDiscountPercent / 100.0)).coerceIn(0.0, 1.0)
                    val lineTotal = line.quantity * line.unitCost * discFactor

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(productName, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "পরিমাণ: ${if (line.quantity % 1.0 == 0.0) line.quantity.toLong().toString() else "%.1f".format(line.quantity)} পিস · টিপি: ${formatTaka(line.unitCost)} ${if (line.tradeDiscountPercent > 0) "(-${line.tradeDiscountPercent}%)" else ""}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "🏷️ ব্যাচ: ${line.batchNumber ?: "N/A"} · ⏳ মেয়াদ: ${line.expiryDate ?: "N/A"} · মোট: ${formatTaka(lineTotal)}",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                val updated = stockLines.toMutableList().also { it.removeAt(index) }
                                stockLines = updated
                                recalculateTotal(updated)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove_stock_line), tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = totalAmountText,
                    onValueChange = {
                        totalAmountText = it
                        if (paidAmountText.isBlank()) paidAmountText = it
                    },
                    label = { Text(stringResource(R.string.supplier_total_bill_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "bKash", "Nagad", "Bank").forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it },
                    label = { Text(stringResource(R.string.supplier_paid_now_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val total = totalAmountText.toDoubleOrNull() ?: 0.0
                val paid = paidAmountText.toDoubleOrNull() ?: 0.0
                val due = (total - paid).coerceAtLeast(0.0)

                if (due > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.supplier_remaining_due, formatTaka(due)),
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tot = totalAmountText.toDoubleOrNull() ?: 0.0
                    val pd = paidAmountText.toDoubleOrNull() ?: 0.0
                    if (tot > 0 && pd in 0.0..tot && stockLines.isNotEmpty()) {
                        val itemsSummary = stockLines.joinToString(", ") { line ->
                            val name = products.firstOrNull { it.barcode == line.productBarcode }?.name ?: line.productBarcode
                            "$name × ${line.quantity} (${line.batchNumber ?: "B"})"
                        }
                        onSave(invoiceNumber, tot, pd, itemsSummary, paymentMethod, stockLines)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text(stringResource(R.string.supplier_record_inflow_button), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// =========================================================================
// 3. WHOLESALE VS RETAIL PRICING TOGGLE
// =========================================================================


/**
 * Segmented 3-chip price-mode selector shown in the cart toolbar and checkout header.
 * Chips: Discounted (🏷️) | MRP (💹) | Wholesale (📦)
 */
@Composable
fun PriceModeSelector(
    currentMode: PriceMode,
    onModeSelected: (PriceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    data class Chip(
        val mode: PriceMode,
        val label: String,
        val icon: String,
        val activeColor: Color,
        val activeBorder: Color,
        val activeText: Color
    )
    val chips = listOf(
        Chip(PriceMode.DISCOUNTED, "Sale",  "🏷️", Color(0xFFECFDF5), Color(0xFF6EE7B7), Color(0xFF047857)),
        Chip(PriceMode.MRP,        "MRP",   "💹", Color(0xFFEFF6FF), Color(0xFF93C5FD), Color(0xFF1D4ED8)),
        Chip(PriceMode.WHOLESALE,  "Bulk",  "📦", Color(0xFFFEF3C7), Color(0xFFFDE68A), Color(0xFFB45309))
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1E2E).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            chips.forEach { chip ->
                val isSelected = currentMode == chip.mode
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) chip.activeColor else Color.Transparent,
                    animationSpec = tween(200), label = "chipBg_${chip.mode}"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) chip.activeText else Color.Gray,
                    animationSpec = tween(200), label = "chipText_${chip.mode}"
                )
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = bgColor,
                    border = if (isSelected) BorderStroke(1.dp, chip.activeBorder) else null,
                    modifier = Modifier.clickable { onModeSelected(chip.mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(chip.icon, fontSize = 11.sp)
                        Text(
                            text = chip.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/** Legacy shim - kept for any call-sites that haven't migrated yet. Use [PriceModeSelector] instead. */
@Deprecated("Use PriceModeSelector", ReplaceWith("PriceModeSelector"))
@Composable
fun WholesalePricingCartSwitch(
    isWholesale: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PriceModeSelector(
        currentMode = if (isWholesale) PriceMode.WHOLESALE else PriceMode.DISCOUNTED,
        onModeSelected = { mode -> onToggle(mode == PriceMode.WHOLESALE) },
        modifier = modifier
    )
}


// =========================================================================
// 4. DAY-END REGISTER CLOSE & CASH RECONCILIATION (Z-REPORT)
// =========================================================================

@Composable
fun ZReportDialog(
    expectedCash: Double,
    cashSales: Double,
    digitalSales: Double,
    cashIn: Double,
    cashOut: Double,
    onDismiss: () -> Unit,
    onCloseRegister: (actualCash: Double, notes: String?) -> Unit
) {
    var n1000 by remember { mutableStateOf("") }
    var n500 by remember { mutableStateOf("") }
    var n200 by remember { mutableStateOf("") }
    var n100 by remember { mutableStateOf("") }
    var n50 by remember { mutableStateOf("") }
    var n20 by remember { mutableStateOf("") }
    var coins by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val actualCash = ((n1000.toIntOrNull() ?: 0) * 1000) +
            ((n500.toIntOrNull() ?: 0) * 500) +
            ((n200.toIntOrNull() ?: 0) * 200) +
            ((n100.toIntOrNull() ?: 0) * 100) +
            ((n50.toIntOrNull() ?: 0) * 50) +
            ((n20.toIntOrNull() ?: 0) * 20) +
            (coins.toIntOrNull() ?: 0)

    val variance = actualCash - expectedCash

    AlertDialog(
        modifier = Modifier.trackUserActivity(),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.zreport_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.zreport_expected_cash_label), fontSize = 10.5.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            Text(formatTaka(expectedCash), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF34D399))
                            Text(stringResource(R.string.zreport_expected_cash_formula), fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }

                item {
                    Text(stringResource(R.string.zreport_denomination_label), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = n1000,
                            onValueChange = { n1000 = it },
                            label = { Text("৳1,000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = n500,
                            onValueChange = { n500 = it },
                            label = { Text("৳500") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = n200,
                            onValueChange = { n200 = it },
                            label = { Text("৳200") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = n100,
                            onValueChange = { n100 = it },
                            label = { Text("৳100") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = n50,
                            onValueChange = { n50 = it },
                            label = { Text("৳50") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = n20,
                            onValueChange = { n20 = it },
                            label = { Text("৳20") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = coins,
                            onValueChange = { coins = it },
                            label = { Text(stringResource(R.string.zreport_coins_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.weight(2f))
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            variance == 0.0 -> Color(0xFFDCFCE7)
                            variance < 0.0 -> Color(0xFFFEE2E2)
                            else -> Color(0xFFE0F2FE)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(stringResource(R.string.zreport_physical_cash_label), fontSize = 11.sp, color = Color.DarkGray)
                                Text(formatTaka(actualCash.toDouble()), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = when {
                                    variance == 0.0 -> stringResource(R.string.zreport_matched)
                                    variance < 0.0 -> stringResource(R.string.zreport_shortage, formatTaka(Math.abs(variance)))
                                    else -> stringResource(R.string.zreport_surplus, formatTaka(variance))
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = when {
                                    variance == 0.0 -> Color(0xFF166534)
                                    variance < 0.0 -> Color(0xFF991B1B)
                                    else -> Color(0xFF075985)
                                }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(if (variance != 0.0) "Discrepancy Reason / Audit Note" else "Closing Note (Optional)") },
                        placeholder = { Text(if (variance != 0.0) "e.g. Rounding change, tip taken, error" else "Add any closing note...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCloseRegister(actualCash.toDouble(), notes.takeIf { it.isNotBlank() })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Text(stringResource(R.string.zreport_complete_button), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// =========================================================================
// 5. CUSTOMER LOYALTY POINTS REDEMPTION WIDGET
// =========================================================================

@Composable
fun LoyaltyRedeemCard(
    customerLoyalty: CustomerLoyalty,
    isApplied: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.loyalty_vip_tier, customerLoyalty.tier), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFBBF24)) {
                        Text(stringResource(R.string.loyalty_points_balance, customerLoyalty.pointsBalance), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF78350F), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                Text(stringResource(R.string.loyalty_redeem_discount, formatTaka(customerLoyalty.pointsBalance.toDouble())), fontSize = 11.sp, color = Color(0xFFB45309))
            }

            Button(
                onClick = { onToggle(!isApplied) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isApplied) Color(0xFFEF4444) else Color(0xFFF59E0B)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    if (isApplied) stringResource(R.string.loyalty_remove_button) else stringResource(R.string.loyalty_apply_button, customerLoyalty.pointsBalance),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

val MANUAL_EXPENSE_CATEGORIES = listOf(
    "Tea & Refreshments",
    "Staff Meal",
    "Electricity (Token)",
    "Transport",
    "Shop Rent",
    "Cleaning",
    "Other"
)
