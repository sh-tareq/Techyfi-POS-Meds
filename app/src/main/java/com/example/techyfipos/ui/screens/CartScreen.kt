@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package com.example.techyfipos.ui.screens

import android.Manifest
import java.util.Locale
import android.content.Context
import android.content.pm.PackageManager
import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.utils.parseExpiryDateToMillis
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import android.database.Cursor
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.example.techyfipos.data.db.CustomerDue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.techyfipos.ui.components.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import com.example.techyfipos.R
import com.example.techyfipos.ui.viewmodel.CartItem
import com.example.techyfipos.ui.viewmodel.CartBarcodeResult
import com.example.techyfipos.ui.viewmodel.CartBarcodeStatus
import com.example.techyfipos.ui.viewmodel.CartViewModel
import com.example.techyfipos.ui.viewmodel.PriceMode
import com.example.techyfipos.utils.BillData
import com.example.techyfipos.utils.BillLineItem
import com.example.techyfipos.utils.MESSENGER_PACKAGE
import com.example.techyfipos.utils.WHATSAPP_PACKAGE
import com.example.techyfipos.utils.buildBillReceiptText
import com.example.techyfipos.utils.generateBillJpeg
import com.example.techyfipos.utils.copyPdfUriToClipboard
import com.example.techyfipos.utils.copyImageUriToClipboard
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.generateBillPdf
import com.example.techyfipos.utils.getBillImageUri
import com.example.techyfipos.utils.getBillPdfUri
import com.example.techyfipos.utils.printBillPdf
import com.example.techyfipos.utils.saveBillPdfToDownloads
import com.example.techyfipos.utils.saveBillJpegToDownloads
import com.example.techyfipos.utils.shareBillPdf
import com.example.techyfipos.utils.shareBillJpeg
import com.example.techyfipos.utils.shareBillPdfAndJpeg
import com.example.techyfipos.utils.HardwareBarcodeScanner
import com.example.techyfipos.utils.toEnglishDigits
import com.example.techyfipos.utils.beepAndVibrate
import com.example.techyfipos.utils.triggerCashDrawerKick
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.data.SettingsDataStore
import com.example.techyfipos.data.QuickSalePreset
import com.example.techyfipos.data.db.SplitPaymentEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CartScanUpdate(
    val result: CartBarcodeResult,
    val timestamp: Long = System.currentTimeMillis()
)

private enum class InvoiceShareFormat { PDF, JPEG, BOTH }

internal fun calculateEffectiveAmountPaid(
    isSplitPayment: Boolean,
    paymentMethod: String,
    enteredAmountPaid: Double,
    grandTotal: Double,
    splitPayments: List<SplitPaymentEntry>
): Double = when {
    isSplitPayment -> splitPayments.filterNot { it.method == "Due" }.sumOf { it.amount }
    paymentMethod == "Due" -> 0.0
    paymentMethod == "Cash" -> if (enteredAmountPaid > 0.0) enteredAmountPaid else grandTotal
    else -> grandTotal
}

internal fun sanitizeDiscountInput(rawValue: String): String {
    var hasDecimalSeparator = false
    val sanitized = buildString {
        rawValue.forEach { character ->
            when {
                character.isDigit() -> {
                    val digit = Character.getNumericValue(character)
                    if (digit in 0..9) append(digit)
                }
                (character == '.' || character == ',') && !hasDecimalSeparator -> {
                    append('.')
                    hasDecimalSeparator = true
                }
            }
        }
    }
    if (sanitized.isEmpty()) return ""

    val decimalIndex = sanitized.indexOf('.')
    val integerPart = (if (decimalIndex >= 0) sanitized.substring(0, decimalIndex) else sanitized)
        .trimStart('0')
        .ifEmpty { "0" }
    return if (decimalIndex >= 0) {
        "$integerPart.${sanitized.substring(decimalIndex + 1)}"
    } else {
        integerPart
    }
}

private fun editableAmount(value: Double): String = when {
    value == 0.0 -> "0"
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    cartViewModel: CartViewModel,
    settingsViewModel: com.example.techyfipos.ui.viewmodel.SettingsViewModel,
    salesViewModel: com.example.techyfipos.ui.viewmodel.SalesViewModel,
    authViewModel: AuthViewModel? = null,
    isOwner: Boolean = true,
    isSupervisor: Boolean = false,
    activePermissions: Set<String> = emptySet(),
    onBack: () -> Unit,
    onNavigateToProducts: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSupervisorAuthDialog by remember { mutableStateOf(false) }
    var pendingSupervisorAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSupervisorDesc by remember { mutableStateOf("") }

    val userDiscountLimit by (authViewModel?.maxDiscountPercent ?: kotlinx.coroutines.flow.MutableStateFlow(10.0)).collectAsStateWithLifecycle(initialValue = 10.0)
    val canVoidCart = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_VOID_CART)
    val canRemoveItem = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_VOID_CART)
    val canApplyDiscount = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_APPLY_DISCOUNT)
    val canSell = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_SELL)
    val canHoldCart = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_HOLD_CART)
    val canChangePriceTier = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_PRICE_TIER)
    val maxDiscountAllowed: Double = if (isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_HIGH_DISCOUNT)) 100.0 else userDiscountLimit
    var isCompletingSale by remember { mutableStateOf(false) }

    val shopName by settingsViewModel.shopName.collectAsStateWithLifecycle()
    val shopAddress by settingsViewModel.shopAddress.collectAsStateWithLifecycle()
    val shopPhone by settingsViewModel.shopPhone.collectAsStateWithLifecycle()
    val shopLogoUri by settingsViewModel.shopLogoUri.collectAsStateWithLifecycle()
    val facebookUrl by settingsViewModel.facebookUrl.collectAsStateWithLifecycle()
    val qrCodeInfo by settingsViewModel.qrCodeInfo.collectAsStateWithLifecycle()
    val cashierName by settingsViewModel.cashierName.collectAsStateWithLifecycle()
    val invoiceSettingsData by settingsViewModel.invoiceSettingsData.collectAsStateWithLifecycle()

    val cartItems by cartViewModel.cartItems.collectAsStateWithLifecycle()
    val subtotal by cartViewModel.subtotal.collectAsStateWithLifecycle()
    val discountVal by cartViewModel.discountValue.collectAsStateWithLifecycle()
    val taxVal by cartViewModel.taxValue.collectAsStateWithLifecycle()
    val grandTotal by cartViewModel.grandTotal.collectAsStateWithLifecycle()
    val changeAmt by cartViewModel.changeAmount.collectAsStateWithLifecycle()

    val discountAmount by cartViewModel.discountAmount.collectAsStateWithLifecycle()
    val isPercentageDiscount by cartViewModel.isPercentageDiscount.collectAsStateWithLifecycle()
    val paymentMethod by cartViewModel.paymentMethod.collectAsStateWithLifecycle()
    val customerName by cartViewModel.customerName.collectAsStateWithLifecycle()
    val customerPhone by cartViewModel.customerPhone.collectAsStateWithLifecycle()
    val resolvedCustomer by cartViewModel.resolvedCustomer.collectAsStateWithLifecycle()
    val isAutoFilledName by cartViewModel.isAutoFilledName.collectAsStateWithLifecycle()
    val customerSuggestions by cartViewModel.customerSuggestions.collectAsStateWithLifecycle()
    val walletPhone by cartViewModel.walletPhone.collectAsStateWithLifecycle()
    val cardType by cartViewModel.cardType.collectAsStateWithLifecycle()
    val cardLastFour by cartViewModel.cardLastFour.collectAsStateWithLifecycle()
    val checkoutCashierName by cartViewModel.cashierName.collectAsStateWithLifecycle()
    val amountPaid by cartViewModel.amountPaid.collectAsStateWithLifecycle()
    val storeCreditApplied by cartViewModel.storeCreditApplied.collectAsStateWithLifecycle()
    val redeemedVoucher by cartViewModel.redeemedVoucher.collectAsStateWithLifecycle()
    var showVoucherRedeemDialog by remember { mutableStateOf(false) }
    val defaultCashier = invoiceSettingsData.cashierName.ifBlank { cashierName.ifBlank { "Admin" } }
    val currentDisplayCashier = checkoutCashierName.ifBlank { defaultCashier }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri?.let { uri ->
            try {
                var name = ""
                var phone = ""
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            name = c.getString(nameIdx) ?: ""
                        }
                        val hasPhoneIdx = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        val hasPhone = if (hasPhoneIdx >= 0) c.getInt(hasPhoneIdx) else 0
                        val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val contactId = if (idIdx >= 0) c.getString(idIdx) else ""

                        if (hasPhone > 0 && contactId.isNotEmpty()) {
                            val phoneCursor: Cursor? = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )
                            phoneCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val pIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (pIdx >= 0) {
                                        phone = pc.getString(pIdx) ?: ""
                                    }
                                }
                            }
                        }
                    }
                }

                if (phone.isNotBlank()) {
                    val cleanPhone = phone.replace("[^0-9+]".toRegex(), "")
                    cartViewModel.onCustomerPhoneChanged(cleanPhone)
                    if (name.isNotBlank() && cartViewModel.customerName.value.isBlank()) {
                        cartViewModel.customerName.value = name
                    }
                } else if (name.isNotBlank()) {
                    cartViewModel.onCustomerNameChanged(name)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_could_not_read_contact), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Permission launcher for READ_CONTACTS
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, context.getString(R.string.cart_contact_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    // Receipt details for sharing - persisted in CartViewModel across rotation
    val showReceiptDialog by cartViewModel.showReceiptDialog.collectAsStateWithLifecycle()
    val billSnapshot by cartViewModel.completedBillSnapshot.collectAsStateWithLifecycle()
    var showEditConfirmForCompletedSale by remember { mutableStateOf(false) }
    val salesWithItemsForCompletedSale by salesViewModel.salesWithItems.collectAsStateWithLifecycle()
    var showCheckout by rememberSaveable { mutableStateOf(false) }
    var showCustomerDetails by rememberSaveable { mutableStateOf(false) }

    val heldCarts by cartViewModel.allHeldCarts.collectAsStateWithLifecycle()
    val heldCartCount by cartViewModel.heldCartCount.collectAsStateWithLifecycle()
    var showHeldCartsSheet by remember { mutableStateOf(false) }
    var showHoldCartDialog by remember { mutableStateOf(false) }
    val heldCartHoldSuccessMessage = stringResource(R.string.held_cart_hold_success)
    val heldCartDefaultCustomer = stringResource(R.string.held_cart_default_customer)
    val heldCartQuickHoldTag = stringResource(R.string.held_cart_tag_quick_hold)
    val isSplitPayment by cartViewModel.isSplitPayment.collectAsStateWithLifecycle()
    val splitPayments by cartViewModel.splitPayments.collectAsStateWithLifecycle()
    val priceMode by cartViewModel.priceMode.collectAsStateWithLifecycle()
    val priceModesSavings by cartViewModel.priceModesSavings.collectAsStateWithLifecycle()
    val saleModeMrpSavings by cartViewModel.saleModeMrpSavings.collectAsStateWithLifecycle()
    val mrpSubtotal by cartViewModel.mrpSubtotal.collectAsStateWithLifecycle()
    val loyaltyCustomer by cartViewModel.loyaltyCustomer.collectAsStateWithLifecycle()
    val loyaltyDiscount by cartViewModel.loyaltyDiscount.collectAsStateWithLifecycle()

    var receivedAmountText by remember { mutableStateOf("") }
    var discountInput by rememberSaveable { mutableStateOf(editableAmount(discountAmount)) }
    var scanUpdate by remember { mutableStateOf<CartScanUpdate?>(null) }
    var notFoundBarcode by remember { mutableStateOf<String?>(null) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    var editingNoteItemIndex by remember { mutableStateOf<Int?>(null) }
    var showCreditLimitWarningDialog by remember { mutableStateOf(false) }
    var exceededCustomerDue by remember { mutableStateOf<CustomerDue?>(null) }
    var pendingDueAmount by remember { mutableStateOf(0.0) }
    var showDueConfirmDialog by remember { mutableStateOf(false) }
    var pendingDueCustomerName by remember { mutableStateOf("") }
    var pendingDuePreviousAmount by remember { mutableStateOf(0.0) }

    // The Activity captures USB/Bluetooth HID scanner keystrokes and publishes a
    // completed barcode here. Keep checkout forms isolated so a scan cannot type
    // into payment, phone, or reference fields.
    // The customer's due/balance card can have been resolved earlier in this cart session (or even
    // reused from before the cart was last cleared) — re-check it against the DB every time the
    // cashier reaches Complete Sale so a payment collected elsewhere isn't shown as stale here.
    LaunchedEffect(showCheckout) {
        if (showCheckout) {
            cartViewModel.refreshResolvedCustomer()
        }
    }

    LaunchedEffect(showCheckout) {
        if (showCheckout) return@LaunchedEffect
        HardwareBarcodeScanner.barcodeEvents.collect { barcode ->
            val englishBarcode = toEnglishDigits(barcode.trim())
            if (englishBarcode.isBlank()) return@collect
            beepAndVibrate(context)
            val result = cartViewModel.addBarcodeToCart(englishBarcode)
            if (result.status == CartBarcodeStatus.NOT_FOUND) {
                notFoundBarcode = result.barcode
            } else {
                scanUpdate = CartScanUpdate(result)
            }
        }
    }

    LaunchedEffect(scanUpdate?.timestamp) {
        if (scanUpdate == null) return@LaunchedEffect
        delay(5_000)
        scanUpdate = null
    }

    LaunchedEffect(discountAmount) {
        val displayedAmount = discountInput.toDoubleOrNull() ?: 0.0
        if (discountAmount != displayedAmount) {
            discountInput = editableAmount(discountAmount)
        }
    }

    LaunchedEffect(amountPaid) {
        val currentParsed = receivedAmountText.toDoubleOrNull() ?: 0.0
        if (amountPaid != currentParsed) {
            receivedAmountText = if (amountPaid == 0.0) "" else if (amountPaid % 1.0 == 0.0) amountPaid.toLong().toString() else amountPaid.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = if (showCheckout) stringResource(R.string.complete_sale) else stringResource(R.string.cart),
                        metricBadge = if (cartItems.isNotEmpty() && !showCheckout) "${cartItems.size} Items" else null,
                        badgeColor = PosSectionColors.Cart,
                        icon = { size ->
                            if (showCheckout) {
                                Realistic3DSellTerminalIcon(size = size)
                            } else {
                                Realistic3DSupermarketCartIcon(size = size)
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showCheckout) showCheckout = false else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (cartItems.isNotEmpty() && !showCheckout) {
                        IconButton(
                            onClick = {
                                cartViewModel.holdCurrentCart()
                                Toast.makeText(context, heldCartHoldSuccessMessage, Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("⏸️", fontSize = 18.sp)
                        }
                        val trashAnim = remember { Animatable(0f) }
                        IconButton(
                            onClick = {
                                if (canVoidCart) {
                                    scope.launch {
                                        trashAnim.animateTo(-15f, tween(60))
                                        trashAnim.animateTo(15f, tween(60))
                                        trashAnim.animateTo(0f, tween(60))
                                    }
                                    cartViewModel.clearCart()
                                } else if (authViewModel != null) {
                                    pendingSupervisorDesc = "Authorize voiding active cart"
                                    pendingSupervisorAction = {
                                        scope.launch {
                                            trashAnim.animateTo(-15f, tween(60))
                                            trashAnim.animateTo(15f, tween(60))
                                            trashAnim.animateTo(0f, tween(60))
                                        }
                                        cartViewModel.clearCart()
                                    }
                                    showSupervisorAuthDialog = true
                                } else {
                                    Toast.makeText(context, "Permission required to void cart", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(
                                text = "🗑️",
                                fontSize = 18.sp,
                                modifier = Modifier.graphicsLayer { rotationZ = trashAnim.value }
                            )
                        }
                    } else if (heldCartCount > 0 && !showCheckout) {
                        IconButton(onClick = { showHeldCartsSheet = true }) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = Color(0xFFF59E0B)) {
                                        Text(heldCartCount.toString(), fontWeight = FontWeight.Bold, color = Color.Black)
                                    }
                                }
                            ) {
                                Text("⏸️", fontSize = 18.sp)
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showCustomItemDialog) {
            AddCustomItemDialog(
                onDismiss = { showCustomItemDialog = false },
                onConfirm = { title, price, qty, note, u ->
                    cartViewModel.addCustomItem(title, price, qty, note, u)
                    showCustomItemDialog = false
                    Toast.makeText(context, context.getString(R.string.cart_custom_item_added_toast, title), Toast.LENGTH_SHORT).show()
                },
                onAddAnother = { title, price, qty, note, u ->
                    cartViewModel.addCustomItem(title, price, qty, note, u)
                }
            )
        }

        if (editingNoteItemIndex != null) {
            val idx = editingNoteItemIndex ?: 0
            val item = cartItems.getOrNull(idx)
            if (item != null) {
                EditItemNoteDialog(
                    initialNote = item.note,
                    productName = item.product.name,
                    onDismiss = { editingNoteItemIndex = null },
                    onSave = { newNote ->
                        cartViewModel.updateItemNote(idx, newNote)
                        editingNoteItemIndex = null
                    }
                )
            }
        }

        if (cartItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                CartScanUpdateBanner(scanUpdate = scanUpdate)

                if (heldCartCount > 0) {
                    HeldCartsTopBanner(
                        count = heldCartCount,
                        onClick = { showHeldCartsSheet = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.cart_empty_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCustomItemDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Realistic3DCustomItemIcon(size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.cart_add_custom_quick_sale))
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // Fast 1-Tap Popular Items Shortcuts
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚡ Quick Add Popular Items",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                letterSpacing = 0.5.sp
                            )

                            val quickItems = listOf(
                                Triple("🛍️ Shopping Bag", 5.0, "Shopping Bag"),
                                Triple("🥚 Farm Egg 1 Pc", 14.0, "Farm Egg 1 Pc"),
                                Triple("🥤 Water 500ml", 20.0, "Mineral Water 500ml"),
                                Triple("☕ Tea & Biscuit", 15.0, "Tea & Biscuit")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickItems.take(2).forEach { (displayLabel, price, itemName) ->
                                    Surface(
                                        onClick = {
                                            cartViewModel.addCustomItem(itemName, price, 1.0, "Quick Sale", "Pcs")
                                            Toast.makeText(context, context.getString(R.string.cart_custom_item_added_toast, itemName), Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(displayLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(formatTaka(price), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF059669).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "+ Add",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF059669),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickItems.drop(2).forEach { (displayLabel, price, itemName) ->
                                    Surface(
                                        onClick = {
                                            cartViewModel.addCustomItem(itemName, price, 1.0, "Quick Sale", "Pcs")
                                            Toast.makeText(context, context.getString(R.string.cart_custom_item_added_toast, itemName), Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(displayLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(formatTaka(price), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF059669).copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = "+ Add",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF059669),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
        } else if (!showCheckout) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                CartScanUpdateBanner(scanUpdate = scanUpdate)

                // Quick Actions Header Row (Custom Item & Held Carts)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showCustomItemDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Realistic3DCustomItemIcon(size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.cart_custom_button), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (heldCartCount > 0) {
                        OutlinedButton(
                            onClick = { showHeldCartsSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706))
                        ) {
                            Text(stringResource(R.string.cart_held_count_button, heldCartCount), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Cart Items list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(cartItems, key = { _, it -> it.product.barcode }) { index, item ->
                        CartItemRow(
                            item = item,
                            unitPrice = cartViewModel.effectivePriceFor(item),
                            allowNegativeInventory = invoiceSettingsData.allowNegativeInventory || isOwner || isSupervisor,
                            onQuantityChanged = { qty -> cartViewModel.updateQuantity(item.product, qty) },
                            onRemove = {
                                if (canRemoveItem) {
                                    cartViewModel.removeFromCart(item.product)
                                } else if (authViewModel != null) {
                                    pendingSupervisorDesc = "Authorize removing item: ${item.product.name}"
                                    pendingSupervisorAction = { cartViewModel.removeFromCart(item.product) }
                                    showSupervisorAuthDialog = true
                                } else {
                                    Toast.makeText(context, "Permission required to remove items", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEditNote = { editingNoteItemIndex = index }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hold Cart Button
                        Button(
                            onClick = {
                                if (canHoldCart) {
                                    showHoldCartDialog = true
                                } else {
                                    Toast.makeText(context, "Permission required to hold carts", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(0.34f)
                                .height(52.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD97706),
                                contentColor = Color.White
                            )
                        ) {
                            Text("⏸️", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.held_cart_hold_button), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        }

                        // Continue to Checkout Button
                        Button(
                            onClick = { showCheckout = true },
                            modifier = Modifier
                                .weight(0.66f)
                                .height(52.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            )
                        ) {
                            Text("🛍️", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.cart_checkout_button, formatTaka(subtotal)),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                            .padding(16.dp)
                ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("🛍️", fontSize = 18.sp)
                                    Spacer(Modifier.width(6.dp))
                                    val unitCount = cartItems.sumOf { it.quantity }
                                    val unitCountStr = if (unitCount % 1.0 == 0.0) unitCount.toLong().toString() else String.format(Locale.US, "%.2f", unitCount).trimEnd('0').trimEnd('.')
                                    val productWordStr = if (cartItems.size == 1) stringResource(R.string.cart_product_singular) else stringResource(R.string.cart_product_plural)
                                    val unitWordStr = if (unitCount == 1.0) stringResource(R.string.cart_unit_singular) else stringResource(R.string.cart_unit_plural)
                                    Text(
                                        "${cartItems.size} $productWordStr • $unitCountStr $unitWordStr • ${formatTaka(grandTotal)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                                TextButton(onClick = { showCheckout = false }) {
                                    Text("👁️", fontSize = 14.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.cart_review_button), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Price mode selector — visible in checkout too
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💰", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.cart_price_mode_label),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Gray
                                )
                            }
                            PriceModeSelector(
                                currentMode = priceMode,
                                onModeSelected = {
                                    if (canChangePriceTier) {
                                        cartViewModel.setPriceMode(it)
                                    } else {
                                        Toast.makeText(context, "Permission required to change price tier", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        if (priceMode == PriceMode.DISCOUNTED && saleModeMrpSavings > 0.0) {
                            val isBn = Locale.getDefault().language == "bn"
                            val totalSavings = saleModeMrpSavings + discountVal
                            val pct = if (mrpSubtotal > 0.0) Math.round((totalSavings / mrpSubtotal) * 100).toInt() else 0
                            val pctStr = if (isBn) com.example.techyfipos.utils.toBanglaDigits(pct.toString()) else "$pct"
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        stringResource(R.string.cart_mrp_savings_badge, formatTaka(totalSavings), pctStr),
                                        color = Color(0xFF15803D),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cashier and discount
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Cashier with Dropdown Menu from presets
                            var cashierMenuExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = currentDisplayCashier,
                                    onValueChange = { cartViewModel.setCashierName(it) },
                                    label = { Text(stringResource(R.string.cart_cashier_label), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.5.sp) },
                                    leadingIcon = { Text("💼", fontSize = 16.sp) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { cashierMenuExpanded = !cashierMenuExpanded },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Select Cashier",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                )

                                DropdownMenu(
                                    expanded = cashierMenuExpanded,
                                    onDismissRequest = { cashierMenuExpanded = false }
                                ) {
                                    val rawPresets = invoiceSettingsData.cashierPresets.ifEmpty { listOf("Admin", "Ahmed", "Fatema", "Karim", "Rahim") }
                                    val presets = (listOf(defaultCashier) + rawPresets).distinct()
                                    presets.forEach { preset ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(preset, fontWeight = if (currentDisplayCashier == preset) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            },
                                            onClick = {
                                                cartViewModel.setCashierName(preset)
                                                cashierMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = discountInput,
                                onValueChange = { newValue ->
                                    val sanitized = sanitizeDiscountInput(newValue)
                                    val parsed = sanitized.toDoubleOrNull() ?: 0.0
                                    val pctEquiv = if (isPercentageDiscount) parsed else if (subtotal > 0.0) (parsed / subtotal) * 100.0 else 0.0
                                    if (parsed > 0.0 && (!canApplyDiscount || pctEquiv > maxDiscountAllowed)) {
                                        if (authViewModel != null) {
                                            pendingSupervisorDesc = "Authorize discount of ${if (isPercentageDiscount) "$sanitized%" else formatTaka(parsed)} (Max limit: ${maxDiscountAllowed.toInt()}%)"
                                            pendingSupervisorAction = {
                                                discountInput = sanitized
                                                cartViewModel.setDiscountAmount(parsed)
                                            }
                                            showSupervisorAuthDialog = true
                                        } else {
                                            Toast.makeText(context, "Discount limit is ${maxDiscountAllowed.toInt()}%", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        discountInput = sanitized
                                        cartViewModel.setDiscountAmount(parsed)
                                    }
                                },
                                label = { Text(stringResource(R.string.cart_discount_label), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.5.sp) },
                                placeholder = { Text(if (isPercentageDiscount) "%" else "৳") },
                                trailingIcon = {
                                    Surface(
                                        onClick = { cartViewModel.toggleDiscountUnit() },
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(width = 38.dp, height = 32.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = if (isPercentageDiscount) "%" else "৳",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = "Switch Discount Unit",
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .onFocusChanged { focusState ->
                                        if (!focusState.isFocused && discountInput.isBlank()) {
                                            discountInput = "0"
                                            cartViewModel.setDiscountAmount(0.0)
                                        }
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Payment Mode Switcher (Single Method vs Split Payment)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isSplitPayment,
                                onClick = { cartViewModel.isSplitPayment.value = false },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💳", fontSize = 14.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.cart_single_method), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = isSplitPayment,
                                onClick = {
                                    cartViewModel.isSplitPayment.value = true
                                    if (splitPayments.isEmpty()) {
                                        val half = (grandTotal / 2).coerceAtLeast(1.0)
                                        cartViewModel.setSplitPayments(
                                            listOf(
                                                com.example.techyfipos.data.db.SplitPaymentEntry("Cash", half),
                                                com.example.techyfipos.data.db.SplitPaymentEntry("bKash", (grandTotal - half).coerceAtLeast(0.0))
                                            )
                                        )
                                    }
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💸", fontSize = 14.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.cart_split_payment), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // --- STORE CREDIT & VOUCHER APPLICATION BANNER ---
                        val availableCustCredit = (resolvedCustomer?.storeCredit ?: 0.0)
                        val hasAvailableCredit = availableCustCredit > 0.0 || redeemedVoucher != null

                        if (storeCreditApplied > 0.0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFECFDF5),
                                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🪙", fontSize = 20.sp)
                                        Column {
                                            Text(
                                                text = stringResource(R.string.cart_store_credit_applied_label, formatTaka(storeCreditApplied)),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF065F46)
                                            )
                                            val remainingToPay = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
                                            Text(
                                                text = if (remainingToPay > 0.0) stringResource(R.string.cart_remaining_to_pay, formatTaka(remainingToPay))
                                                else "Bill fully covered by store credit!",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (remainingToPay > 0.0) Color(0xFF047857) else Color(0xFF059669)
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { cartViewModel.removeStoreCredit() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE11D48)),
                                        border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(stringResource(R.string.cart_remove_store_credit), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (hasAvailableCredit) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFFFBEB),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("🪙", fontSize = 20.sp)
                                        Column {
                                            Text(
                                                text = stringResource(R.string.cart_store_credit_banner_title),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF92400E)
                                            )
                                            val credAmt = if (redeemedVoucher != null) redeemedVoucher!!.remainingBalance else availableCustCredit
                                            Text(
                                                text = "Available: ${formatTaka(credAmt)}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val credAmt = if (redeemedVoucher != null) redeemedVoucher!!.remainingBalance else availableCustCredit
                                            val applyAmt = kotlin.math.min(credAmt, grandTotal)
                                            cartViewModel.applyStoreCredit(applyAmt)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(stringResource(R.string.cart_apply_store_credit), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        // Have a Store Credit Voucher Button
                        if (storeCreditApplied <= 0.0 && redeemedVoucher == null) {
                            TextButton(
                                onClick = { showVoucherRedeemDialog = true },
                                modifier = Modifier.align(Alignment.End).padding(bottom = 2.dp)
                            ) {
                                Text("🎟️ " + stringResource(R.string.cart_have_voucher), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (showVoucherRedeemDialog) {
                            var voucherInput by remember { mutableStateOf("") }
                            var voucherError by remember { mutableStateOf<String?>(null) }
                            var isCheckingVoucher by remember { mutableStateOf(false) }

                            AlertDialog(
                                onDismissRequest = { showVoucherRedeemDialog = false },
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("🎟️", fontSize = 22.sp)
                                        Text(stringResource(R.string.cart_redeem_voucher_title), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Enter or scan the customer's store credit voucher code (e.g., SC-XXXX-XXXX).",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = voucherInput,
                                            onValueChange = {
                                                voucherInput = it.uppercase()
                                                voucherError = null
                                            },
                                            label = { Text(stringResource(R.string.cart_redeem_voucher_hint)) },
                                            singleLine = true,
                                            isError = voucherError != null,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (voucherError != null) {
                                            Text(voucherError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            val code = voucherInput.trim()
                                            if (code.isBlank()) {
                                                voucherError = "Please enter a voucher code"
                                                return@Button
                                            }
                                            isCheckingVoucher = true
                                            cartViewModel.redeemVoucherCode(
                                                code = code,
                                                onSuccess = { voucher ->
                                                    isCheckingVoucher = false
                                                    val applyAmt = kotlin.math.min(voucher.remainingBalance, grandTotal)
                                                    cartViewModel.applyStoreCredit(applyAmt)
                                                    showVoucherRedeemDialog = false
                                                    Toast.makeText(context, "Voucher applied: ${formatTaka(applyAmt)}", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { err ->
                                                    isCheckingVoucher = false
                                                    voucherError = err
                                                }
                                            )
                                        },
                                        enabled = !isCheckingVoucher && voucherInput.isNotBlank()
                                    ) {
                                        Text(stringResource(R.string.cart_redeem_voucher_button))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVoucherRedeemDialog = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }

                        if (isSplitPayment) {
                            SplitPaymentBuilder(
                                grandTotal = grandTotal,
                                splitEntries = splitPayments,
                                onEntriesChanged = { cartViewModel.setSplitPayments(it) }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💳", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.cart_payment_method_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val availablePaymentMethods = remember(resolvedCustomer?.storeCredit) {
                                if ((resolvedCustomer?.storeCredit ?: 0.0) > 0.0) {
                                    listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due", "Store Credit")
                                } else {
                                    listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due")
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                maxItemsInEachRow = 3
                            ) {
                                availablePaymentMethods.forEach { method ->
                                    val chipScaleAnim = remember { Animatable(1f) }
                                    FilterChip(
                                        selected = paymentMethod == method,
                                        onClick = {
                                            scope.launch {
                                                chipScaleAnim.animateTo(0.82f, tween(70))
                                                chipScaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            }
                                            cartViewModel.paymentMethod.value = method
                                            if (method != "Cash") {
                                                receivedAmountText = ""
                                                cartViewModel.amountPaid.value = 0.0
                                            }
                                        },
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = chipScaleAnim.value
                                            scaleY = chipScaleAnim.value
                                        },
                                        label = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                when (method) {
                                                    "Cash" -> Text("💵", fontSize = 16.sp)
                                                    "Card" -> Text("💳", fontSize = 16.sp)
                                                    "Store Credit" -> Text("🏷️", fontSize = 16.sp)
                                                    "bKash", "Nagad", "Rocket" -> Image(
                                                        painter = painterResource(
                                                            when (method) {
                                                                "bKash" -> R.drawable.logo_bkash
                                                                "Nagad" -> R.drawable.logo_nagad
                                                                else -> R.drawable.logo_rocket
                                                            }
                                                        ),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(width = 28.dp, height = 18.dp)
                                                    )
                                                    else -> Text("⏳", fontSize = 16.sp)
                                                }
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = when (method) {
                                                        "Due" -> stringResource(R.string.due)
                                                        "Store Credit" -> "Store Credit"
                                                        else -> method
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            AnimatedVisibility(visible = paymentMethod == "Store Credit") {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE8F5E9),
                                    border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🏷️", fontSize = 22.sp)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Available Store Credit: ${formatTaka(resolvedCustomer?.storeCredit ?: 0.0)}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1B5E20)
                                            )
                                            Text(
                                                "Amount ${formatTaka(grandTotal)} will be deducted directly from store credit upon sale completion.",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = paymentMethod in listOf("bKash", "Nagad", "Rocket")) {
                                OutlinedTextField(
                                    value = walletPhone,
                                    onValueChange = { cartViewModel.walletPhone.value = it.filter(Char::isDigit).take(11) },
                                    label = { Text(stringResource(R.string.cart_wallet_mobile_number_label, paymentMethod)) },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }

                            AnimatedVisibility(visible = paymentMethod == "Card") {
                                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💳", fontSize = 15.sp)
                                        Spacer(Modifier.width(5.dp))
                                        Text(stringResource(R.string.cart_card_type_label), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("Visa", "Mastercard", "Amex", "Other").forEach { type ->
                                            val cardChipScaleAnim = remember { Animatable(1f) }
                                            FilterChip(
                                                selected = cardType == type,
                                                onClick = {
                                                    scope.launch {
                                                        cardChipScaleAnim.animateTo(0.82f, tween(70))
                                                        cardChipScaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                    }
                                                    cartViewModel.cardType.value = type
                                                },
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = cardChipScaleAnim.value
                                                    scaleY = cardChipScaleAnim.value
                                                },
                                                label = {
                                                    when (type) {
                                                        "Visa" -> Text("VISA", color = Color(0xFF17357E), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                                        "Mastercard" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Box(Modifier.size(15.dp).background(Color(0xFFEB001B), androidx.compose.foundation.shape.CircleShape))
                                                            Box(Modifier.offset(x = (-5).dp).size(15.dp).background(Color(0xFFF79E1B).copy(alpha = .95f), androidx.compose.foundation.shape.CircleShape))
                                                        }
                                                        "Amex" -> Surface(color = Color(0xFF1976BA), shape = RoundedCornerShape(4.dp)) {
                                                            Text("AMEX", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                        }
                                                        else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("💳", fontSize = 14.sp)
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(stringResource(R.string.cart_card_other_label), fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = cardLastFour,
                                        onValueChange = { cartViewModel.cardLastFour.value = it.filter(Char::isDigit).take(4) },
                                        label = { Text(stringResource(R.string.cart_last_4_digits_label)) },
                                        leadingIcon = { Text("🔢", fontSize = 16.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        val hasDueInPayment = (isSplitPayment && splitPayments.any { it.method == "Due" && it.amount > 0 }) || (!isSplitPayment && paymentMethod == "Due")

                        TextButton(
                            onClick = { if (!hasDueInPayment) showCustomerDetails = !showCustomerDetails },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("👤", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (hasDueInPayment) stringResource(R.string.cart_customer_details_required_due)
                                else if (showCustomerDetails) stringResource(R.string.cart_hide_customer_details)
                                else stringResource(R.string.cart_add_customer_details)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(if (showCustomerDetails || hasDueInPayment) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }

                        // Customer details row
                        AnimatedVisibility(visible = showCustomerDetails || hasDueInPayment) {
                          Column(
                              modifier = Modifier.fillMaxWidth(),
                              verticalArrangement = Arrangement.spacedBy(8.dp)
                          ) {
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = {
                                    cartViewModel.onCustomerPhoneChanged(it)
                                },
                                label = { Text(stringResource(R.string.customer_phone), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.5.sp) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (customerPhone.isNotBlank()) {
                                            IconButton(onClick = { cartViewModel.clearCustomerDetails() }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        IconButton(onClick = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                                contactPickerLauncher.launch(null)
                                            } else {
                                                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                            }
                                        }) {
                                            Icon(
                                                Icons.Default.ContactPhone,
                                                contentDescription = "Pick from Contacts",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Customer Autocomplete Suggestions Dropdown
                            if (customerSuggestions.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = stringResource(R.string.customer_suggestions_title),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                        customerSuggestions.forEach { suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { cartViewModel.selectCustomerSuggestion(suggestion) }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(suggestion.name.ifBlank { "Customer" }, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                    Text(
                                                        "${suggestion.phone}${if (!suggestion.tag.isNullOrBlank()) " • ${suggestion.tag}" else ""}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (suggestion.dueAmount > 0) {
                                                    Text(
                                                        "Due: ${formatTaka(suggestion.dueAmount)}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Select",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Resolved Customer Status Badge
                            if (resolvedCustomer != null && resolvedCustomer!!.name.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = when (resolvedCustomer!!.source) {
                                                        "CUSTOMER_DB" -> stringResource(R.string.customer_source_db)
                                                        "DUE_RECORD" -> stringResource(R.string.customer_source_due)
                                                        "LOYALTY" -> stringResource(R.string.customer_source_loyalty)
                                                        else -> stringResource(R.string.customer_source_history)
                                                    },
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (resolvedCustomer!!.tier.isNotBlank() && resolvedCustomer!!.tier != "Silver") {
                                                Text(
                                                    "👑 ${resolvedCustomer!!.tier}",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (resolvedCustomer!!.storeCredit > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFE8F5E9)
                                                ) {
                                                    Text(
                                                        text = "🏷️ Credit: ${formatTaka(resolvedCustomer!!.storeCredit)}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (resolvedCustomer!!.dueAmount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        text = "⚠️ Due: ${formatTaka(resolvedCustomer!!.dueAmount)}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (resolvedCustomer!!.loyaltyPoints > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        text = "⭐ ${resolvedCustomer!!.loyaltyPoints} Pts",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (resolvedCustomer!!.totalOrders > 0) {
                                                Text(
                                                    text = "📦 ${resolvedCustomer!!.totalOrders} orders",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { cartViewModel.onCustomerNameChanged(it) },
                                label = {
                                    Text(
                                        text = if (isAutoFilledName) stringResource(R.string.customer_name_autofilled) else stringResource(R.string.customer_name),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 11.5.sp
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (loyaltyCustomer != null) {
                                LoyaltyRedeemCard(
                                    customerLoyalty = loyaltyCustomer!!,
                                    isApplied = loyaltyDiscount > 0.0,
                                    onToggle = { apply ->
                                        if (apply) {
                                            cartViewModel.applyLoyaltyDiscount(loyaltyCustomer!!.pointsBalance.toDouble())
                                        } else {
                                            cartViewModel.applyLoyaltyDiscount(0.0)
                                        }
                                    }
                                )
                            }
                          }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Effective Received Amount & Change calculation for Cash
                        val effectiveAmountPaid = calculateEffectiveAmountPaid(
                            isSplitPayment = isSplitPayment,
                            paymentMethod = paymentMethod,
                            enteredAmountPaid = amountPaid,
                            grandTotal = grandTotal,
                            splitPayments = splitPayments
                        )
                        val effectiveChangeAmt = (effectiveAmountPaid - grandTotal).coerceAtLeast(0.0)

                        // Cash Paid & Change calculation for Cash Payment
                        AnimatedVisibility(visible = !isSplitPayment && paymentMethod == "Cash") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(8.dp)
                            ) {
                                // Smart Cash Tender Chips Row
                                val exactAmt = grandTotal
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Exact Total Chip
                                    Surface(
                                        onClick = {
                                            cartViewModel.amountPaid.value = exactAmt
                                            receivedAmountText = if (exactAmt % 1.0 == 0.0) exactAmt.toLong().toString() else exactAmt.toString()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text("⚡", fontSize = 11.sp)
                                            Text(
                                                text = stringResource(R.string.cart_exact_amount_label),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    // Incremental +৳50, +৳100, +৳500, +৳1000 chips
                                    listOf(50.0, 100.0, 500.0, 1000.0).forEach { inc ->
                                        Surface(
                                            onClick = {
                                                val curr = receivedAmountText.toDoubleOrNull() ?: 0.0
                                                val updated = curr + inc
                                                cartViewModel.amountPaid.value = updated
                                                receivedAmountText = if (updated % 1.0 == 0.0) updated.toLong().toString() else updated.toString()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = "+৳${inc.toInt()}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    // Clear chip
                                    if (receivedAmountText.isNotBlank()) {
                                        Surface(
                                            onClick = {
                                                cartViewModel.amountPaid.value = 0.0
                                                receivedAmountText = ""
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "✕ Clear",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = receivedAmountText,
                                    onValueChange = { input ->
                                        receivedAmountText = input
                                        cartViewModel.amountPaid.value = input.toDoubleOrNull() ?: 0.0
                                    },
                                    label = { Text(stringResource(R.string.cart_cash_received_label), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.5.sp) },
                                    leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                val paidVal = amountPaid
                                if (effectiveChangeAmt > 0.0) {
                                    Surface(
                                        color = Color(0xFF059669).copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("💵", fontSize = 14.sp)
                                                Text(
                                                    stringResource(R.string.change),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF059669),
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Text(
                                                formatTaka(effectiveChangeAmt),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF059669),
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                } else if (paidVal > 0.0 && paidVal < grandTotal) {
                                    val shortAmt = grandTotal - paidVal
                                    Surface(
                                        color = Color(0xFFDC2626).copy(alpha = 0.10f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "⚠️ Short Amount",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626),
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                "-${formatTaka(shortAmt)}",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Customer Due Confirmation Notice
                        AnimatedVisibility(visible = !isSplitPayment && paymentMethod == "Due") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⏳", fontSize = 18.sp)
                                    Column {
                                        Text(
                                            text = "বকেয়া বিক্রয় (Customer Due)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "সর্বমোট ${formatTaka(grandTotal)} ক্রেতার বকেয়া হিসেবে যুক্ত হবে।",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        // Pricing summaries
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🧾", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when (priceMode) {
                                        PriceMode.WHOLESALE  -> stringResource(R.string.cart_subtotal_wholesale)
                                        PriceMode.MRP        -> stringResource(R.string.cart_subtotal_mrp)
                                        PriceMode.DISCOUNTED -> stringResource(R.string.subtotal)
                                    },
                                    color = Color.Gray
                                )
                            }
                            val displaySubtotal = if (priceMode == PriceMode.DISCOUNTED && saleModeMrpSavings > 0.0) mrpSubtotal else subtotal
                            Text(formatTaka(displaySubtotal), fontWeight = FontWeight.Bold)
                        }
                        if (priceMode == PriceMode.DISCOUNTED && saleModeMrpSavings > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🏷️", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.cart_sale_savings),
                                        color = Color(0xFF15803D),
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "-${formatTaka(saleModeMrpSavings)}",
                                    color = Color(0xFF16A34A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (priceMode == PriceMode.WHOLESALE && priceModesSavings > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📦", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.cart_wholesale_bulk_savings),
                                        color = Color(0xFFB45309),
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "-${formatTaka(priceModesSavings)}",
                                    color = Color(0xFFD97706),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (priceMode == PriceMode.MRP && priceModesSavings > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💹", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.cart_mrp_premium),
                                        color = Color(0xFF1D4ED8),
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "+${formatTaka(priceModesSavings)}",
                                    color = Color(0xFF2563EB),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (discountVal > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocalOffer, null, Modifier.size(17.dp), tint = Color.Gray)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.discount), color = Color.Gray)
                                }
                                Text("-${formatTaka(discountVal)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (loyaltyDiscount > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⭐", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.cart_vip_loyalty_cashback), color = Color(0xFFB45309), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("-${formatTaka(loyaltyDiscount)}", color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (taxVal > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val rateStr = invoiceSettingsData.taxRate.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                                val modeStr = if (invoiceSettingsData.taxInclusive) stringResource(R.string.cart_tax_mode_inclusive) else stringResource(R.string.cart_tax_mode_exclusive)
                                Text(
                                    "${invoiceSettingsData.taxLabel.ifBlank { stringResource(R.string.cart_default_tax_label) }} ($rateStr% • $modeStr)",
                                    color = Color.Gray
                                )
                                Text(formatTaka(taxVal), fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💵", fontSize = 18.sp)
                                Spacer(Modifier.width(7.dp))
                                Text(stringResource(R.string.grand_total), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Text(
                                formatTaka(grandTotal),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (priceMode == PriceMode.DISCOUNTED && saleModeMrpSavings > 0.0) {
                            val totalSavings = saleModeMrpSavings + discountVal
                            val pct = if (mrpSubtotal > 0.0) Math.round((totalSavings / mrpSubtotal) * 100).toInt() else 0
                            val isBn = Locale.getDefault().language == "bn"
                            val pctStr = if (isBn) com.example.techyfipos.utils.toBanglaDigits(pct.toString()) else "$pct"
                            val isItemWise = invoiceSettingsData.showItemWiseMrpDiscount

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("✨", fontSize = 14.sp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                stringResource(R.string.cart_customer_savings_breakdown),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF166534)
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFF16A34A),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                "$pctStr% OFF",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.cart_original_mrp_subtotal), fontSize = 12.sp, color = Color(0xFF4B5563))
                                        Text(formatTaka(mrpSubtotal), fontSize = 12.sp, color = Color(0xFF4B5563))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.cart_store_sale_discount), fontSize = 12.sp, color = Color(0xFF15803D), fontWeight = FontWeight.SemiBold)
                                        Text("-${formatTaka(saleModeMrpSavings)}", fontSize = 12.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                                    }

                                    if (discountVal > 0.0) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(stringResource(R.string.discount), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                            Text("-${formatTaka(discountVal)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFDCFCE7), thickness = 1.dp)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.cart_total_customer_savings), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF14532D))
                                        Text("${formatTaka(totalSavings)} ($pctStr%)", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF15803D))
                                    }

                                    // Switch between item-by-item discount and summary only
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = isItemWise,
                                            onClick = {
                                                settingsViewModel.saveInvoiceSettingsData(invoiceSettingsData.copy(showItemWiseMrpDiscount = true))
                                            },
                                            label = { Text(stringResource(R.string.cart_item_by_item_switch), fontSize = 11.sp, fontWeight = if (isItemWise) FontWeight.Bold else FontWeight.Normal) },
                                            leadingIcon = if (isItemWise) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) } } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF16A34A),
                                                selectedLabelColor = Color.White,
                                                selectedLeadingIconColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        FilterChip(
                                            selected = !isItemWise,
                                            onClick = {
                                                settingsViewModel.saveInvoiceSettingsData(invoiceSettingsData.copy(showItemWiseMrpDiscount = false))
                                            },
                                            label = { Text(stringResource(R.string.cart_summary_only_switch), fontSize = 11.sp, fontWeight = if (!isItemWise) FontWeight.Bold else FontWeight.Normal) },
                                            leadingIcon = if (!isItemWise) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) } } else null,
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF16A34A),
                                                selectedLabelColor = Color.White,
                                                selectedLeadingIconColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Checkout trigger validation
                        val remainingToPay = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
                        val isDueValid = (!hasDueInPayment) || (customerName.isNotBlank() && customerPhone.isNotBlank())
                        val isWalletValid = isSplitPayment || paymentMethod !in listOf("bKash", "Nagad", "Rocket") || walletPhone.length == 11
                        val isCardValid = isSplitPayment || paymentMethod != "Card" || cardLastFour.length == 4
                        val isCashValid = isSplitPayment || paymentMethod != "Cash" || remainingToPay == 0.0 || effectiveAmountPaid >= remainingToPay
                        val isStoreCreditValid = isSplitPayment || paymentMethod != "Store Credit" || remainingToPay == 0.0 || ((resolvedCustomer?.storeCredit ?: 0.0) >= grandTotal)
                        val expectedSplitTotal = if (storeCreditApplied > 0.0) remainingToPay else grandTotal
                        val isSplitValid = !isSplitPayment || (splitPayments.isNotEmpty() && (kotlin.math.abs(splitPayments.sumOf { it.amount } - expectedSplitTotal) < 0.01 || kotlin.math.abs(splitPayments.sumOf { it.amount } - grandTotal) < 0.01))
                        val isSplitDetailsValid = !isSplitPayment || splitPayments.all { sp ->
                            when (sp.method) {
                                "bKash", "Nagad", "Rocket" -> sp.walletPhone?.length == 11
                                "Card" -> sp.cardLastFour?.length == 4
                                else -> true
                            }
                        }
                        val isCheckoutEnabled = cartItems.isNotEmpty() && isDueValid && isWalletValid && isCardValid && isCashValid && isStoreCreditValid && isSplitValid && isSplitDetailsValid && canSell

                        if (!canSell) {
                            Text(stringResource(R.string.perm_sales_sell_title) + " required to checkout", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        } else if (!isCheckoutEnabled) {
                            val reason = when {
                                !isDueValid -> stringResource(R.string.cart_reason_due_details_required)
                                isSplitPayment && !isSplitValid -> stringResource(R.string.cart_reason_split_total_mismatch)
                                isSplitPayment && !isSplitDetailsValid -> stringResource(R.string.cart_reason_split_details_required)
                                !isWalletValid -> stringResource(R.string.cart_reason_wallet_number_required, paymentMethod)
                                !isCardValid -> stringResource(R.string.cart_reason_card_digits_required)
                                !isCashValid -> stringResource(R.string.cart_reason_cash_insufficient)
                                !isStoreCreditValid -> {
                                    if (customerPhone.isBlank() && redeemedVoucher == null) "Customer phone or voucher required for Store Credit"
                                    else "Insufficient store credit (Available: ${formatTaka(if (redeemedVoucher != null) redeemedVoucher!!.remainingBalance else (resolvedCustomer?.storeCredit ?: 0.0))})"
                                }
                                else -> stringResource(R.string.cart_reason_generic_incomplete)
                            }
                            Text(reason, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        val executeSaleAction = {
                            if (!isCompletingSale) {
                                isCompletingSale = true
                                val lineItems = cartItems.map { item ->
                                    val chargedUnitPrice = cartViewModel.effectivePriceFor(item)
                                    BillLineItem(
                                        name = item.product.name,
                                        quantity = item.quantity,
                                        unitPrice = chargedUnitPrice,
                                        lineTotal = chargedUnitPrice * item.quantity,
                                        unit = item.product.unit,
                                        note = item.note,
                                        mrpPrice = if (item.product.mrpPrice > 0.0) item.product.mrpPrice else item.product.sellingPrice,
                                        purchasePrice = item.product.purchasePrice
                                    )
                                }
                                val snapshotSubtotal = subtotal
                                val snapshotMrpSubtotal = mrpSubtotal
                                val snapshotMrpSavings = saleModeMrpSavings
                                val snapshotIsSaleMode = (priceMode == PriceMode.DISCOUNTED)
                                val snapshotDiscount = discountVal
                                val snapshotTax = taxVal
                                val snapshotGrandTotal = grandTotal
                                val snapshotPaymentMethod = if (isSplitPayment) "Split" else paymentMethod
                                val snapshotCustomerName = customerName
                                val snapshotCustomerPhone = customerPhone
                                val snapshotAmountPaid = effectiveAmountPaid
                                val snapshotChange = effectiveChangeAmt
                                val snapshotSplitJson = if (isSplitPayment) cartViewModel.getSplitPaymentsJson() else null
                                val snapshotHasDueInPayment = hasDueInPayment
                                scope.launch {
                                    try {
                                        val snapshotStoreCreditApplied = cartViewModel.storeCreditApplied.value
                                        val snapshotRedeemedVoucher = cartViewModel.redeemedVoucher.value
                                        val snapshotVoucherCode = snapshotRedeemedVoucher?.code
                                        val snapshotVoucherAmount = if (snapshotRedeemedVoucher != null) {
                                            snapshotStoreCreditApplied.coerceAtMost(snapshotRedeemedVoucher.remainingBalance)
                                        } else null
                                        val postSaleRemainingVoucherBalance = if (snapshotRedeemedVoucher != null && snapshotVoucherAmount != null) {
                                            (snapshotRedeemedVoucher.remainingBalance - snapshotVoucherAmount).coerceAtLeast(0.0)
                                        } else null

                                        // Update ViewModel's amountPaid and cashierName before completeSale
                                        cartViewModel.amountPaid.value = effectiveAmountPaid
                                        cartViewModel.setCashierName(currentDisplayCashier)
                                        val saleId = cartViewModel.completeSale()
                                        if (invoiceSettingsData.cashDrawerKickEnabled && (snapshotPaymentMethod == "Cash" || snapshotPaymentMethod == "Split")) {
                                            triggerCashDrawerKick(context, invoiceSettingsData.cashDrawerPrinterAddress)
                                        }
                                        // Re-fetch the authoritative post-sale balance so the receipt shows the true total due.
                                        val updatedTotalDue = if (snapshotHasDueInPayment && snapshotCustomerPhone.isNotBlank()) {
                                            cartViewModel.getCustomerDue(snapshotCustomerPhone)?.outstandingAmount ?: 0.0
                                        } else 0.0
                                        val postSaleRemainingStoreCredit = if (snapshotCustomerPhone.isNotBlank()) {
                                            cartViewModel.getCustomerByPhone(snapshotCustomerPhone)?.storeCredit
                                        } else null

                                        cartViewModel.openReceiptDialog(BillData(
                                            saleId = saleId,
                                            timestampMillis = System.currentTimeMillis(),
                                            shopName = shopName,
                                            shopAddress = shopAddress,
                                            shopPhone = shopPhone,
                                            items = lineItems,
                                            subtotal = snapshotSubtotal,
                                            discountValue = snapshotDiscount,
                                            grandTotal = snapshotGrandTotal,
                                            paymentMethod = snapshotPaymentMethod,
                                            customerName = snapshotCustomerName,
                                            customerPhone = snapshotCustomerPhone,
                                            amountPaid = snapshotAmountPaid,
                                            changeAmount = snapshotChange,
                                            walletPhone = walletPhone,
                                            cardType = cardType,
                                            cardLastFour = cardLastFour,
                                            splitPaymentJson = snapshotSplitJson,
                                            logoUri = shopLogoUri,
                                            facebookUrl = facebookUrl,
                                            qrCodeInfo = qrCodeInfo,
                                            cashierName = currentDisplayCashier,
                                            settingsData = invoiceSettingsData,
                                            taxLabel = invoiceSettingsData.taxLabel,
                                            taxValue = snapshotTax,
                                            totalOutstandingDue = updatedTotalDue,
                                            mrpSubtotal = snapshotMrpSubtotal,
                                            mrpSavings = snapshotMrpSavings,
                                            isSaleMode = snapshotIsSaleMode,
                                            showItemWiseMrpDiscount = invoiceSettingsData.showItemWiseMrpDiscount,
                                            storeCreditApplied = snapshotStoreCreditApplied.takeIf { it > 0.0 },
                                            voucherCode = snapshotVoucherCode,
                                            voucherAmount = snapshotVoucherAmount,
                                            customerRemainingStoreCredit = postSaleRemainingStoreCredit,
                                            voucherRemainingBalance = postSaleRemainingVoucherBalance
                                        ))
                                    } catch (e: Exception) {
                                        android.util.Log.e("CartScreen", "Error completing sale", e)
                                        Toast.makeText(context, "Error completing sale: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isCompletingSale = false
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (!isCompletingSale) {
                                    val newDue = if (isSplitPayment) {
                                        splitPayments.firstOrNull { it.method == "Due" }?.amount ?: 0.0
                                    } else if (paymentMethod == "Due") {
                                        (grandTotal - effectiveAmountPaid).coerceAtLeast(0.0)
                                    } else 0.0

                                    val proceedWithSale = {
                                        if (hasDueInPayment && customerPhone.isNotBlank()) {
                                            scope.launch {
                                                val custDue = cartViewModel.getCustomerCreditAccount(customerPhone)
                                                if (custDue != null && custDue.creditLimit > 0.0 && (custDue.outstandingAmount + newDue) > custDue.creditLimit) {
                                                    exceededCustomerDue = custDue
                                                    pendingDueAmount = newDue
                                                    showCreditLimitWarningDialog = true
                                                } else {
                                                    pendingDueCustomerName = custDue?.customerName?.takeIf { it.isNotBlank() } ?: customerName
                                                    pendingDuePreviousAmount = custDue?.outstandingAmount ?: 0.0
                                                    pendingDueAmount = newDue
                                                    showDueConfirmDialog = true
                                                }
                                            }
                                        } else {
                                            executeSaleAction()
                                        }
                                    }

                                    val hasNegativeStock = cartItems.any { it.quantity > it.product.stockQty && !it.product.barcode.startsWith("CUSTOM-") }
                                    if (hasNegativeStock && !invoiceSettingsData.allowNegativeInventory && !isOwner && !isSupervisor) {
                                        if (authViewModel != null) {
                                            pendingSupervisorDesc = "Authorize sale with negative inventory / insufficient stock"
                                            pendingSupervisorAction = { proceedWithSale() }
                                            showSupervisorAuthDialog = true
                                        } else {
                                            Toast.makeText(context, "Negative inventory is disabled. Supervisor authorization required.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        proceedWithSale()
                                    }
                                }
                            },
                            enabled = isCheckoutEnabled && !isCompletingSale,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCompletingSale) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.complete_sale), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            } else {
                                Text("💳", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.complete_sale), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("➡️", fontSize = 16.sp)
                            }
                        }

                        if (showDueConfirmDialog) {
                            ConfirmDueSaleDialog(
                                customerName = pendingDueCustomerName,
                                previousDue = pendingDuePreviousAmount,
                                newDueAmount = pendingDueAmount,
                                onConfirm = {
                                    showDueConfirmDialog = false
                                    executeSaleAction()
                                },
                                onCancel = { showDueConfirmDialog = false }
                            )
                        }
                    }
                }
            }
        }
    val snapshot = billSnapshot
    if (showReceiptDialog && snapshot != null) {
        val completedSale = salesWithItemsForCompletedSale.find { it.sale.id == snapshot.saleId }
        ReceiptSummaryDialog(
            bill = snapshot,
            onDismiss = {
                cartViewModel.dismissReceiptDialog()
                onBack()
            },
            onAddToCartAgain = if (completedSale != null) {
                {
                    scope.launch {
                        val skipped = cartViewModel.loadFromPastSale(completedSale, replaceCart = false)
                        if (skipped > 0) {
                            Toast.makeText(context, context.getString(R.string.cart_add_to_cart_again_skipped_toast, skipped), Toast.LENGTH_LONG).show()
                        }
                        cartViewModel.dismissReceiptDialog()
                    }
                }
            } else null,
            onEdit = if (isOwner && completedSale != null) {
                { showEditConfirmForCompletedSale = true }
            } else null,
            isOwner = isOwner
        )
    }

    if (showEditConfirmForCompletedSale) {
        val saleToEdit = salesWithItemsForCompletedSale.find { it.sale.id == snapshot?.saleId }
        AlertDialog(
            onDismissRequest = { showEditConfirmForCompletedSale = false },
            title = { Text(stringResource(R.string.cart_edit_sale_confirm_title)) },
            text = { Text(stringResource(R.string.cart_edit_sale_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showEditConfirmForCompletedSale = false
                        if (saleToEdit != null) {
                            scope.launch {
                                val voided = salesViewModel.deleteSaleAndAwait(saleToEdit.sale.id)
                                if (voided) {
                                    val skipped = cartViewModel.loadFromPastSale(saleToEdit, replaceCart = true)
                                    if (skipped > 0) {
                                        Toast.makeText(context, context.getString(R.string.cart_add_to_cart_again_skipped_toast, skipped), Toast.LENGTH_LONG).show()
                                    }
                                    cartViewModel.dismissReceiptDialog()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.sales_history_void_for_edit_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) { Text(stringResource(R.string.cart_edit_sale_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditConfirmForCompletedSale = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val custDueToWarn = exceededCustomerDue
    if (showCreditLimitWarningDialog && custDueToWarn != null) {
        CreditLimitWarningDialog(
            customer = custDueToWarn,
            newDue = pendingDueAmount,
            onVerifyOwnerPin = { pin ->
                cartViewModel.authorizeCreditLimitOverride(pin, custDueToWarn.customerPhone, maxAmount = pendingDueAmount)
            },
            onDismiss = {
                showCreditLimitWarningDialog = false
                exceededCustomerDue = null
            },
            onAuthorize = {
                showCreditLimitWarningDialog = false
                exceededCustomerDue = null
                // Complete the sale upon authorization
                val lineItems = cartItems.map { item ->
                    val chargedUnitPrice = cartViewModel.effectivePriceFor(item)
                    BillLineItem(
                        name = item.product.name,
                        quantity = item.quantity,
                        unitPrice = chargedUnitPrice,
                        lineTotal = chargedUnitPrice * item.quantity,
                        unit = item.product.unit,
                        note = item.note,
                        mrpPrice = if (item.product.mrpPrice > 0.0) item.product.mrpPrice else item.product.sellingPrice,
                        purchasePrice = item.product.purchasePrice
                    )
                }
                val snapshotSubtotal = subtotal
                val snapshotMrpSubtotal = mrpSubtotal
                val snapshotMrpSavings = saleModeMrpSavings
                val snapshotIsSaleMode = (priceMode == PriceMode.DISCOUNTED)
                val snapshotDiscount = discountVal
                val snapshotTax = taxVal
                val snapshotGrandTotal = grandTotal
                val snapshotPaymentMethod = if (isSplitPayment) "Split" else paymentMethod
                val snapshotCustomerName = customerName
                val snapshotCustomerPhone = customerPhone
                val snapshotAmountPaid = calculateEffectiveAmountPaid(
                    isSplitPayment = isSplitPayment,
                    paymentMethod = paymentMethod,
                    enteredAmountPaid = if (paymentMethod == "Cash") (receivedAmountText.toDoubleOrNull() ?: 0.0) else 0.0,
                    grandTotal = grandTotal,
                    splitPayments = splitPayments
                )
                val snapshotChange = (snapshotAmountPaid - grandTotal).coerceAtLeast(0.0)
                val snapshotSplitJson = if (isSplitPayment) cartViewModel.getSplitPaymentsJson() else null
                scope.launch {
                    try {
                        val snapshotStoreCreditApplied = cartViewModel.storeCreditApplied.value
                        val snapshotRedeemedVoucher = cartViewModel.redeemedVoucher.value
                        val snapshotVoucherCode = snapshotRedeemedVoucher?.code
                        val snapshotVoucherAmount = if (snapshotRedeemedVoucher != null) {
                            snapshotStoreCreditApplied.coerceAtMost(snapshotRedeemedVoucher.remainingBalance)
                        } else null
                        val postSaleRemainingVoucherBalance = if (snapshotRedeemedVoucher != null && snapshotVoucherAmount != null) {
                            (snapshotRedeemedVoucher.remainingBalance - snapshotVoucherAmount).coerceAtLeast(0.0)
                        } else null

                        cartViewModel.amountPaid.value = snapshotAmountPaid
                        cartViewModel.setCashierName(currentDisplayCashier)
                        val saleId = cartViewModel.completeSale()
                        if (invoiceSettingsData.cashDrawerKickEnabled && (snapshotPaymentMethod == "Cash" || snapshotPaymentMethod == "Split")) {
                            triggerCashDrawerKick(context, invoiceSettingsData.cashDrawerPrinterAddress)
                        }
                        val updatedTotalDue = if (snapshotCustomerPhone.isNotBlank()) {
                            cartViewModel.getCustomerDue(snapshotCustomerPhone)?.outstandingAmount ?: 0.0
                        } else 0.0
                        val postSaleRemainingStoreCredit = if (snapshotCustomerPhone.isNotBlank()) {
                            cartViewModel.getCustomerByPhone(snapshotCustomerPhone)?.storeCredit
                        } else null

                        cartViewModel.openReceiptDialog(BillData(
                            saleId = saleId,
                            timestampMillis = System.currentTimeMillis(),
                            shopName = shopName,
                            shopAddress = shopAddress,
                            shopPhone = shopPhone,
                            items = lineItems,
                            subtotal = snapshotSubtotal,
                            discountValue = snapshotDiscount,
                            grandTotal = snapshotGrandTotal,
                            paymentMethod = snapshotPaymentMethod,
                            customerName = snapshotCustomerName,
                            customerPhone = snapshotCustomerPhone,
                            amountPaid = snapshotAmountPaid,
                            changeAmount = snapshotChange,
                            walletPhone = walletPhone,
                            cardType = cardType,
                            cardLastFour = cardLastFour,
                            splitPaymentJson = snapshotSplitJson,
                            logoUri = shopLogoUri,
                            facebookUrl = facebookUrl,
                            qrCodeInfo = qrCodeInfo,
                            cashierName = currentDisplayCashier,
                            settingsData = invoiceSettingsData,
                            taxLabel = invoiceSettingsData.taxLabel,
                            taxValue = snapshotTax,
                            totalOutstandingDue = updatedTotalDue,
                            mrpSubtotal = snapshotMrpSubtotal,
                            mrpSavings = snapshotMrpSavings,
                            isSaleMode = snapshotIsSaleMode,
                            showItemWiseMrpDiscount = invoiceSettingsData.showItemWiseMrpDiscount,
                            storeCreditApplied = snapshotStoreCreditApplied.takeIf { it > 0.0 },
                            voucherCode = snapshotVoucherCode,
                            voucherAmount = snapshotVoucherAmount,
                            customerRemainingStoreCredit = postSaleRemainingStoreCredit,
                            voucherRemainingBalance = postSaleRemainingVoucherBalance
                        ))
                    } catch (e: Exception) {
                        android.util.Log.e("CartScreen", "Error completing sale", e)
                        Toast.makeText(context, "Error completing sale: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showHoldCartDialog) {
        var holdCustomerName by remember { mutableStateOf(customerName.ifBlank { heldCartDefaultCustomer }) }
        val heldCartTags = listOf(
            heldCartQuickHoldTag,
            stringResource(R.string.held_cart_tag_customer_1),
            stringResource(R.string.held_cart_tag_customer_2),
            stringResource(R.string.held_cart_tag_table_1),
            stringResource(R.string.held_cart_tag_phone_order)
        )
        val heldCartToastTemplate = stringResource(R.string.held_cart_held_toast)
        AlertDialog(
            onDismissRequest = { showHoldCartDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏸️", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.held_cart_dialog_title), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val heldUnitCount = cartItems.sumOf { it.quantity }
                    val heldUnitCountStr = if (heldUnitCount % 1.0 == 0.0) heldUnitCount.toLong().toString() else String.format(Locale.US, "%.2f", heldUnitCount).trimEnd('0').trimEnd('.')
                    Text(
                        pluralStringResource(
                            R.plurals.held_cart_dialog_message,
                            heldUnitCount.toInt(),
                            heldUnitCountStr,
                            formatTaka(subtotal)
                        ),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = holdCustomerName,
                        onValueChange = { holdCustomerName = it },
                        label = { Text(stringResource(R.string.held_cart_name_label)) },
                        placeholder = { Text(stringResource(R.string.held_cart_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        heldCartTags.forEach { tag ->
                            FilterChip(
                                selected = holdCustomerName == tag,
                                onClick = { holdCustomerName = tag },
                                label = { Text(tag, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = holdCustomerName.ifBlank { heldCartQuickHoldTag }
                        cartViewModel.holdCurrentCart(title = finalTitle)
                        showHoldCartDialog = false
                        Toast.makeText(context, heldCartToastTemplate.format(finalTitle), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.held_cart_confirm_button), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHoldCartDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showHeldCartsSheet) {
        val heldCartRestoredToastTemplate = stringResource(R.string.held_cart_restored_toast)
        HeldCartsBottomSheet(
            heldCarts = heldCarts,
            onResume = { cart ->
                cartViewModel.restoreHeldCart(cart) { result ->
                    Toast.makeText(context,
                        if (result.unavailableLines > 0) "Restored available items; ${result.unavailableLines} unavailable line(s) remain saved"
                        else heldCartRestoredToastTemplate.format(cart.title),
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

    notFoundBarcode?.let { barcode ->
        StockCheckNotFoundDialog(
            barcode = barcode,
            onDismiss = { notFoundBarcode = null },
            onAddNow = {
                notFoundBarcode = null
                onNavigateToProducts(barcode)
            }
        )
    }

    if (showSupervisorAuthDialog && authViewModel != null) {
        SupervisorAuthDialog(
            title = stringResource(R.string.users_supervisor_auth_title),
            actionDescription = pendingSupervisorDesc.ifBlank { stringResource(R.string.users_supervisor_auth_desc) },
            authViewModel = authViewModel,
            onDismiss = {
                showSupervisorAuthDialog = false
                pendingSupervisorAction = null
            },
            onAuthorized = { supervisor ->
                showSupervisorAuthDialog = false
                val action = pendingSupervisorAction
                pendingSupervisorAction = null
                action?.invoke()
            }
        )
    }
}

@Composable
private fun CartScanUpdateBanner(scanUpdate: CartScanUpdate?) {
    AnimatedVisibility(
        visible = scanUpdate != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val result = scanUpdate?.result ?: return@AnimatedVisibility
        val isSuccess = result.status == CartBarcodeStatus.ADDED
        val containerColor = if (isSuccess) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        val contentColor = if (isSuccess) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.QrCodeScanner else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (result.status) {
                            CartBarcodeStatus.ADDED -> stringResource(R.string.cart_scan_product_added, result.productName ?: "")
                            CartBarcodeStatus.STOCK_LIMIT -> stringResource(R.string.cart_scan_stock_limit_reached)
                            CartBarcodeStatus.NOT_FOUND -> stringResource(R.string.cart_scan_product_not_found)
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (result.status) {
                            CartBarcodeStatus.ADDED ->
                                stringResource(R.string.cart_scan_in_cart_total, result.quantityInCart.toString(), formatTaka(result.grandTotal))
                            CartBarcodeStatus.STOCK_LIMIT ->
                                stringResource(R.string.cart_scan_stock_limit_detail, result.productName ?: "")
                            CartBarcodeStatus.NOT_FOUND -> stringResource(R.string.cart_scan_barcode_not_found_detail, result.barcode)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    onQuantityChanged: (Double) -> Unit,
    onRemove: () -> Unit,
    onEditNote: () -> Unit = {},
    unitPrice: Double = item.product.sellingPrice,
    allowNegativeInventory: Boolean = false
) {
    var showQuantityDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val minusAnim = remember { Animatable(1f) }
    val plusAnim = remember { Animatable(1f) }
    val removeAnim = remember { Animatable(0f) }
    val qtyBounceAnim = remember { Animatable(1f) }

    LaunchedEffect(item.quantity) {
        qtyBounceAnim.animateTo(1.35f, tween(90, easing = FastOutSlowInEasing))
        qtyBounceAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    if (showQuantityDialog) {
        ManualQuantityDialog(
            currentQuantity = item.quantity,
            maxQuantity = if (allowNegativeInventory) Double.MAX_VALUE else item.product.stockQty,
            unit = item.product.unit,
            onConfirm = { qty ->
                onQuantityChanged(qty)
                showQuantityDialog = false
            },
            onDismiss = { showQuantityDialog = false }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Full Width Product Name on Top (Optimized for Bangla and English scripts)
            Text(
                text = item.product.name,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
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
                // Left Column: Price & Line Total & Note
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val unitPriceStr = formatTaka(unitPrice)
                    val totalAmountStr = formatTaka(unitPrice * item.quantity)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "$unitPriceStr/${item.product.unit}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (unitPrice == 0.0) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = if (unitPrice == 0.0) BorderStroke(0.8.dp, Color(0xFF10B981)) else null
                        ) {
                            Text(
                                text = if (unitPrice == 0.0) "🎁 " + stringResource(R.string.cart_free_badge) else stringResource(R.string.cart_item_line_total, totalAmountStr),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (unitPrice == 0.0) Color(0xFF047857) else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            )
                        }

                        val expMillis = parseExpiryDateToMillis(item.product.expiryDate)
                        val now = System.currentTimeMillis()
                        if (expMillis != null && expMillis > 0) {
                            if (expMillis < now) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEE2E2),
                                    border = BorderStroke(0.8.dp, Color(0xFFEF4444))
                                ) {
                                    Text(
                                        text = "❌ Expired (${item.product.expiryDate})",
                                        color = Color(0xFFB91C1C),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                    )
                                }
                            } else if (expMillis <= now + (7L * 24 * 60 * 60 * 1000)) {
                                val daysLeft = ((expMillis - now) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEF3C7),
                                    border = BorderStroke(0.8.dp, Color(0xFFF59E0B))
                                ) {
                                    Text(
                                        text = "⚠️ Exp: ${daysLeft}d left (${item.product.expiryDate})",
                                        color = Color(0xFFB45309),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!item.note.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { onEditNote() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("📝", fontSize = 10.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    item.note,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.cart_add_note_label),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable { onEditNote() }
                                .padding(vertical = 1.dp)
                        )
                    }
                }

                // Stepper controls (Right side)
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
                            onQuantityChanged(next)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "➖",
                                fontSize = 13.sp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = minusAnim.value
                                    scaleY = minusAnim.value
                                }
                            )
                        }
                    }

                    // Quantity counter badge
                    Surface(
                        onClick = { showQuantityDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .height(38.dp)
                            .padding(horizontal = 2.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            val displayQty = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
                            Text(
                                text = displayQty,
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
                            scope.launch {
                                plusAnim.animateTo(1.35f, tween(70))
                                plusAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                            val next = Math.round((item.quantity + stepVal) * 100.0) / 100.0
                            if (allowNegativeInventory || item.product.stockQty <= 0.0 || next <= item.product.stockQty) {
                                onQuantityChanged(next)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981),
                        contentColor = Color.White,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "➕",
                                fontSize = 13.sp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = plusAnim.value
                                    scaleY = plusAnim.value
                                }
                            )
                        }
                    }

                    Spacer(Modifier.width(2.dp))

                    // Remove Single Item Button
                    Surface(
                        onClick = {
                            scope.launch {
                                removeAnim.animateTo(-20f, tween(50))
                                removeAnim.animateTo(20f, tween(50))
                                removeAnim.animateTo(0f, tween(50))
                            }
                            onRemove()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "🗑️",
                                fontSize = 14.sp,
                                modifier = Modifier.graphicsLayer { rotationZ = removeAnim.value }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManualQuantityDialog(
    currentQuantity: Double,
    maxQuantity: Double,
    unit: String = "Pcs",
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(
            if (currentQuantity % 1.0 == 0.0) currentQuantity.toLong().toString()
            else String.format(Locale.US, "%.2f", currentQuantity).trimEnd('0').trimEnd('.')
        )
    }
    val parsed = text.toDoubleOrNull()
    val isStockCheckEnabled = maxQuantity > 0.0
    val isValid = parsed != null && parsed > 0.0 && (!isStockCheckEnabled || parsed <= maxQuantity)

    val cleanUnit = unit.trim()
    val isFractional = cleanUnit.lowercase() in listOf("kg", "gm", "g", "ltr", "liter", "litre", "mtr", "meter", "ml")
    val increments = if (isFractional) listOf(0.25, 0.5, 1.0, 5.0) else listOf(1.0, 5.0, 10.0, 20.0)
    val presets = if (isFractional) listOf(0.25, 0.5, 1.0, 2.75) else listOf(1.0, 2.0, 5.0, 10.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚖️", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cart_set_quantity_title, cleanUnit), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            text = filtered.take(7)
                        }
                    },
                    label = { Text(stringResource(R.string.cart_quantity_label, cleanUnit)) },
                    placeholder = { Text(if (isFractional) stringResource(R.string.cart_quantity_placeholder_fractional) else stringResource(R.string.cart_quantity_placeholder_whole)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(onDone = { if (isValid) onConfirm(parsed!!) }),
                    isError = text.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Increment Buttons (+0.25, +0.5, +1, +5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    increments.forEach { inc ->
                        OutlinedButton(
                            onClick = {
                                val cur = text.toDoubleOrNull() ?: 0.0
                                val added = Math.round((cur + inc) * 100.0) / 100.0
                                val n = if (isStockCheckEnabled) added.coerceAtMost(maxQuantity) else added
                                text = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
                            },
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val incStr = if (inc % 1.0 == 0.0) "+${inc.toInt()}" else "+$inc"
                            Text(
                                text = incStr,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Quick Presets (0.25 Kg, 0.5 Kg, 1 Kg, 2.75 Kg)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            onClick = {
                                val n = if (isStockCheckEnabled) preset.coerceAtMost(maxQuantity) else preset
                                text = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 1.dp)) {
                                val pNum = if (preset % 1.0 == 0.0) "${preset.toInt()}" else "$preset"
                                val pStr = if (cleanUnit.isNotBlank()) "$pNum $cleanUnit" else pNum
                                Text(
                                    text = pStr,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isStockCheckEnabled) {
                    val maxStr = if (maxQuantity % 1.0 == 0.0) maxQuantity.toLong().toString() else String.format(Locale.US, "%.2f", maxQuantity).trimEnd('0').trimEnd('.')
                    Text(
                        stringResource(R.string.cart_available_stock, maxStr, cleanUnit),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    if (text.isNotEmpty() && !isValid) {
                        Text(
                            stringResource(R.string.cart_quantity_range_error, maxStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onConfirm(parsed!!) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color(0xFF059669))
            }
        }
    )
}

@Composable
fun CreditLimitWarningDialog(
    customer: CustomerDue,
    newDue: Double,
    onVerifyOwnerPin: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
    onAuthorize: () -> Unit
) {
    var ownerPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val totalDue = customer.outstandingAmount + newDue
    val excess = (totalDue - customer.creditLimit).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cart_credit_limit_exceeded_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF1F2),
                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.cart_credit_ceiling_reached, customer.customerName),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBE123C),
                            fontSize = 13.sp
                        )
                        Text(stringResource(R.string.cart_credit_limit_line, formatTaka(customer.creditLimit)), fontSize = 11.5.sp)
                        Text(stringResource(R.string.cart_current_outstanding_line, formatTaka(customer.outstandingAmount)), fontSize = 11.5.sp)
                        Text(stringResource(R.string.cart_new_due_line, formatTaka(newDue)), fontSize = 11.5.sp)
                        Text(
                            text = stringResource(R.string.cart_new_total_balance_line, formatTaka(totalDue), formatTaka(excess)),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE11D48),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.cart_owner_authorization_required),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ownerPin,
                    onValueChange = { input ->
                        ownerPin = input.filter { it.isDigit() }.take(4)
                        pinError = false
                    },
                    label = { Text(stringResource(R.string.cart_owner_pin_label)) },
                    placeholder = { Text(stringResource(R.string.cart_enter_pin_placeholder)) },
                    singleLine = true,
                    isError = pinError,
                    supportingText = if (pinError) {
                        { Text(stringResource(R.string.invalid_pin), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        isVerifying = true
                        val authorized = onVerifyOwnerPin(ownerPin)
                        isVerifying = false
                        if (authorized) onAuthorize() else pinError = true
                    }
                },
                enabled = ownerPin.length == 4 && !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.cart_authorize_proceed_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Shown before completing any Due (or split-with-Due) sale under the credit limit, so the cashier sees the customer's total balance before committing. */
@Composable
fun ConfirmDueSaleDialog(
    customerName: String,
    previousDue: Double,
    newDueAmount: Double,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val totalDue = previousDue + newDueAmount
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧾", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cart_confirm_due_sale_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(customerName.ifBlank { stringResource(R.string.cart_confirm_due_sale_unknown_customer) }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(stringResource(R.string.cart_confirm_due_sale_previous_due, formatTaka(previousDue)), fontSize = 12.5.sp)
                    Text(stringResource(R.string.cart_confirm_due_sale_new_due, formatTaka(newDueAmount)), fontSize = 12.5.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.cart_confirm_due_sale_total_due, formatTaka(totalDue)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.cart_confirm_due_sale_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ReceiptSummaryDialog(
    bill: BillData,
    title: String = stringResource(R.string.cart_sale_completed_title),
    onDismiss: () -> Unit,
    onReturn: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onAddToCartAgain: (() -> Unit)? = null,
    onViewVoucherSlip: (() -> Unit)? = null,
    isOwner: Boolean = false,
    returnSummary: com.example.techyfipos.ui.viewmodel.SaleReturnSummary? = null
) {
    val context = LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current
    val scope = rememberCoroutineScope()
    var selectedDialogTab by rememberSaveable { mutableIntStateOf(0) }
    val receiptLanguageState = rememberSaveable { mutableStateOf("EN") }
    var receiptLanguage by receiptLanguageState
    val receiptText = remember(bill, receiptLanguage) { buildBillReceiptText(bill, receiptLanguage = receiptLanguage) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var selectedShareFormat by rememberSaveable { mutableStateOf(InvoiceShareFormat.JPEG) }

    var selectedRollWidth by rememberSaveable { mutableIntStateOf(bill.settingsData.paperRollWidthMm.takeIf { it == 58 || it == 80 } ?: 80) }
    var isZoomed by rememberSaveable { mutableStateOf(false) }

    val activeBill = remember(bill, selectedRollWidth) {
        bill.copy(settingsData = bill.settingsData.copy(paperRollWidthMm = selectedRollWidth))
    }

    fun withPdf(action: (java.io.File) -> Unit) {
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    generateBillPdf(context, activeBill, receiptLanguage = receiptLanguage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_failed_generate_pdf_toast), Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                action(file)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_action_failed_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun withJpeg(action: (java.io.File) -> Unit) {
        scope.launch {
            val file = try {
                withContext(Dispatchers.IO) {
                    generateBillJpeg(context, activeBill, receiptLanguage = receiptLanguage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_failed_generate_jpeg_toast), Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                action(file)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, context.getString(R.string.cart_action_failed_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val receiptBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, activeBill, receiptLanguage) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = generateBillJpeg(context, activeBill, receiptLanguage = receiptLanguage)
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val infiniteFloatTransition = rememberInfiniteTransition(label = "receiptIconFloat")
    val iconFloatY by infiniteFloatTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconFloatY"
    )

    var iconPressed by remember { mutableStateOf(false) }
    val iconScale by animateFloatAsState(
        targetValue = if (iconPressed) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconScale"
    )

    var printPressed by remember { mutableStateOf(false) }
    val printScale by animateFloatAsState(
        targetValue = if (printPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "printScale"
    )

    var savePressed by remember { mutableStateOf(false) }
    val saveScale by animateFloatAsState(
        targetValue = if (savePressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "saveScale"
    )

    var deletePressed by remember { mutableStateOf(false) }
    val deleteScale by animateFloatAsState(
        targetValue = if (deletePressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "deleteScale"
    )

    var sharePressed by remember { mutableStateOf(false) }
    val shareScale by animateFloatAsState(
        targetValue = if (sharePressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shareScale"
    )

    val infiniteShimmerTransition = rememberInfiniteTransition(label = "textShimmerSweep")
    val shimmerOffset by infiniteShimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF059669),
            Color(0xFF10B981),
            Color(0xFF059669),
            Color(0xFF0F172A)
        ),
        start = Offset(shimmerOffset - 400f, shimmerOffset - 400f),
        end = Offset(shimmerOffset, shimmerOffset)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Top Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Animated 3D Receipt Icon
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = iconFloatY
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        iconPressed = true
                                        delay(120)
                                        iconPressed = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF10B981),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF34D399), Color(0xFF059669))
                                            )
                                        )
                                ) {
                                    Text("🧾", fontSize = 22.sp)
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF15803D),
                                border = BorderStroke(1.5.dp, Color.White),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .size(15.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("✓", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Column {
                            Text(
                                text = title,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(brush = shimmerBrush)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                                Text(
                                    text = stringResource(R.string.cart_thermal_receipt_preview).replace("80mm", "$selectedRollWidth mm"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (onViewVoucherSlip != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF818CF8)),
                                modifier = Modifier.clickable { onViewVoucherSlip() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("🎟️", fontSize = 12.sp)
                                    Text(
                                        text = stringResource(R.string.store_credit_view_voucher_slip),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }
                        }

                        // Zoom inspect toggle
                        IconButton(
                            onClick = { isZoomed = !isZoomed },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Text(if (isZoomed) "🔎" else "🔍", fontSize = 15.sp)
                        }

                        // Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cart_close_button),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Segmented Tabs: 🧾 Customer Slip vs 💎 Owner Profit
                if (isOwner) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { selectedDialogTab = 0 },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedDialogTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (selectedDialogTab == 0) 2.dp else 0.dp,
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("🧾", fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.invoice_tab_receipt_slip),
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedDialogTab == 0) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedDialogTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                onClick = { selectedDialogTab = 1 },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selectedDialogTab == 1) Color(0xFF0F172A) else Color.Transparent,
                                shadowElevation = if (selectedDialogTab == 1) 2.dp else 0.dp,
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Realistic3DProfitVaultIcon(size = 14.dp, animated = selectedDialogTab == 1)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.invoice_tab_profit_analytics),
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedDialogTab == 1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedDialogTab == 1) Color(0xFFFDE68A) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (isOwner && selectedDialogTab == 1) {
                    OwnerProfitAnalyticsContent(
                        bill = activeBill,
                        returnSummary = returnSummary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                // 2. Part 1: Quick Financial Metrics Ribbon (Photorealistic 3D Animated Icons)
                Surface(
                    color = Color(0xFFE8F8F5),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFD1FAE5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 3D Gold Coins + Total
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Realistic3DGoldCoinsIcon(size = 28.dp)
                            Column {
                                Text(
                                    text = stringResource(R.string.total),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = formatTaka(bill.grandTotal),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }

                        // 3D Cash Note + Method
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Realistic3DCashBillIcon(size = 26.dp)
                            Column {
                                Text(
                                    text = stringResource(R.string.payment_method),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = bill.paymentMethod.ifBlank { "Cash" },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        val remainingDue = (bill.grandTotal - bill.amountPaid).coerceAtLeast(0.0)
                        if (bill.changeAmount > 0.0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.change),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = formatTaka(bill.changeAmount),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        } else if (remainingDue > 0.0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.due),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = formatTaka(remainingDue),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (remainingDue > 0.0) MaterialTheme.colorScheme.errorContainer else Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, if (remainingDue > 0.0) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else Color(0xFF86EFAC)),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                            ) {
                                if (remainingDue <= 0.0) {
                                    Realistic3DRepaymentSealIcon(size = 13.dp, animate = true)
                                }
                                Text(
                                    text = if (remainingDue > 0.0) "DUE" else "PAID",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (remainingDue > 0.0) MaterialTheme.colorScheme.error else Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }

                // 3. Expansive Receipt Viewport (.weight(1f))
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Viewport Top Control Bar (Roll switch, [ EN | বাং ] language toggle & Zoom chip)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = selectedRollWidth == 80,
                                    onClick = { selectedRollWidth = 80 },
                                    label = { Text("80mm", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF059669),
                                        selectedLabelColor = Color.White
                                    ),
                                    border = null,
                                    modifier = Modifier.height(28.dp)
                                )
                                FilterChip(
                                    selected = selectedRollWidth == 58,
                                    onClick = { selectedRollWidth = 58 },
                                    label = { Text("58mm", fontSize = 10.5.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF059669),
                                        selectedLabelColor = Color.White
                                    ),
                                    border = null,
                                    modifier = Modifier.height(28.dp)
                                )
                            }

                            // Sliding Pill Language Toggle [ EN | বাং ]
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                shadowElevation = 2.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (receiptLanguage == "EN") Color(0xFF10B981) else Color.Transparent)
                                            .clickable { receiptLanguage = "EN" }
                                            .padding(horizontal = 9.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "EN",
                                            color = if (receiptLanguage == "EN") Color.White else Color(0xFF475569),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (receiptLanguage == "BN") Color(0xFF10B981) else Color.Transparent)
                                            .clickable { receiptLanguage = "BN" }
                                            .padding(horizontal = 9.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "বাং",
                                            color = if (receiptLanguage == "BN") Color.White else Color(0xFF475569),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier
                                    .clickable { isZoomed = !isZoomed }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isZoomed) "🔎 Reset 1x" else "🔍 Zoom 1.2x",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Scrollable Receipt Content Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val currentBmp = receiptBitmap
                            if (currentBmp != null) {
                                Image(
                                    bitmap = currentBmp,
                                    contentDescription = "$selectedRollWidth mm Thermal Receipt",
                                    modifier = Modifier
                                        .then(
                                            if (isZoomed) Modifier.fillMaxWidth(1.25f)
                                            else if (selectedRollWidth == 58) Modifier.fillMaxWidth(0.78f)
                                            else Modifier.fillMaxWidth(0.94f)
                                        )
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Text(
                                    receiptText,
                                    color = Color.White,
                                    modifier = Modifier.padding(6.dp),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
                }

                // 4. Ergonomic Bottom Action Deck
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Hero Primary Action: PRINT
                    Button(
                        onClick = {
                            scope.launch {
                                printPressed = true
                                delay(100)
                                printPressed = false
                                com.example.techyfipos.utils.printReceiptWithBluetoothFallback(
                                    context = context,
                                    activity = activity,
                                    bill = activeBill,
                                    settings = activeBill.settingsData,
                                    receiptLanguage = receiptLanguage
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = printScale
                                scaleY = printScale
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Realistic3DPrinterIcon(size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.cart_print_button)} ($selectedRollWidth mm)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // 2. Secondary Row: Share, Save, Repeat Sale
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Share Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    sharePressed = true
                                    delay(100)
                                    sharePressed = false
                                    shareMenuExpanded = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .graphicsLayer {
                                    scaleX = shareScale
                                    scaleY = shareScale
                                },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Realistic3DShareIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cart_share_button),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Save Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    savePressed = true
                                    delay(100)
                                    savePressed = false
                                    withJpeg { file ->
                                        val saved = saveBillJpegToDownloads(context, file)
                                        if (saved) {
                                            copyImageUriToClipboard(context, getBillImageUri(context, file), "Invoice #${bill.saleId}")
                                            Toast.makeText(context, context.getString(R.string.cart_saved_jpeg_toast), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.cart_failed_save_jpeg_toast), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .graphicsLayer {
                                    scaleX = saveScale
                                    scaleY = saveScale
                                },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Realistic3DDownloadIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cart_save_button),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Repeat Sale / Add to Cart Again (if present)
                        if (onAddToCartAgain != null) {
                            OutlinedButton(
                                onClick = onAddToCartAgain,
                                modifier = Modifier
                                    .weight(1.15f)
                                    .height(40.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.cart_add_to_cart_again_button),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 3. Overflow Row (Edit, Return, Delete, Done)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onEdit != null) {
                                TextButton(
                                    onClick = onEdit,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Realistic3DStylusPenIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cart_edit_sale_button),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706)
                                    )
                                }
                            }

                            if (onReturn != null) {
                                TextButton(
                                    onClick = onReturn,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Realistic3DReturnBoxIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cart_return_items_button),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }

                            if (onDelete != null) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            deletePressed = true
                                            delay(100)
                                            deletePressed = false
                                            onDelete()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Realistic3DTrashCanIcon(size = 14.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.cart_delete_button),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Realistic3DForwardArrowIcon(size = 14.dp)
                        }
                    }
                }
            }
        }
    }

    if (shareMenuExpanded) {
        AlertDialog(
            onDismissRequest = { shareMenuExpanded = false },
            title = { Text(stringResource(R.string.cart_share_invoice_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.cart_choose_file_format),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InvoiceShareFormat.entries.forEach { format ->
                        val (shareTitle, shareSubtitle) = when (format) {
                            InvoiceShareFormat.PDF -> stringResource(R.string.cart_format_pdf_title) to stringResource(R.string.cart_format_pdf_subtitle)
                            InvoiceShareFormat.JPEG -> stringResource(R.string.cart_format_jpeg_title) to stringResource(R.string.cart_format_jpeg_subtitle)
                            InvoiceShareFormat.BOTH -> stringResource(R.string.cart_format_both_title) to stringResource(R.string.cart_format_both_subtitle)
                        }
                        Surface(
                            onClick = { selectedShareFormat = format },
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (selectedShareFormat == format) 2.dp else 1.dp,
                                if (selectedShareFormat == format) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            color = if (selectedShareFormat == format) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (format) {
                                        InvoiceShareFormat.PDF -> Icons.Default.PictureAsPdf
                                        InvoiceShareFormat.JPEG -> Icons.Default.Image
                                        InvoiceShareFormat.BOTH -> Icons.Default.FileCopy
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(shareTitle, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        shareSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = selectedShareFormat == format,
                                    onClick = { selectedShareFormat = format }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val format = selectedShareFormat
                        shareMenuExpanded = false
                        scope.launch {
                            try {
                                val files = withContext(Dispatchers.IO) {
                                    val pdf = if (format != InvoiceShareFormat.JPEG) generateBillPdf(context, activeBill, receiptLanguage = receiptLanguage) else null
                                    val jpeg = if (format != InvoiceShareFormat.PDF) generateBillJpeg(context, activeBill, receiptLanguage = receiptLanguage) else null
                                    pdf to jpeg
                                }
                                val shared = when (format) {
                                    InvoiceShareFormat.PDF -> shareBillPdf(
                                        activity ?: context,
                                        getBillPdfUri(context, requireNotNull(files.first))
                                    )
                                    InvoiceShareFormat.JPEG -> shareBillJpeg(
                                        activity ?: context,
                                        getBillImageUri(context, requireNotNull(files.second))
                                    )
                                    InvoiceShareFormat.BOTH -> shareBillPdfAndJpeg(
                                        activity ?: context,
                                        getBillPdfUri(context, requireNotNull(files.first)),
                                        getBillImageUri(context, requireNotNull(files.second))
                                    )
                                }
                                if (!shared) {
                                    Toast.makeText(context, context.getString(R.string.cart_unable_open_sharing_toast), Toast.LENGTH_SHORT).show()
                                }
                            } catch (error: Exception) {
                                error.printStackTrace()
                                Toast.makeText(context, context.getString(R.string.cart_failed_prepare_invoice_toast), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cart_continue_to_share_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { shareMenuExpanded = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, price: Double, quantity: Double, note: String?, unit: String) -> Unit,
    onAddAnother: ((title: String, price: Double, quantity: Double, note: String?, unit: String) -> Unit)? = null
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
    var customUnitText by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var isFreeMode by rememberSaveable { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf("Product") }
    var sessionAddedCount by rememberSaveable { mutableIntStateOf(0) }

    var showManagePresets by remember { mutableStateOf(false) }
    var showManageNotePresets by remember { mutableStateOf(false) }

    val standardUnits = remember { listOf("Pcs", "Kg", "Gm", "Ltr", "Mtr", "Box", "Service", "Trip", "Other") }

    val parsedPrice = if (isFreeMode) 0.0 else (priceText.toDoubleOrNull() ?: 0.0)
    val parsedQty = qtyText.toDoubleOrNull() ?: 1.0
    val totalAmount = parsedPrice * parsedQty
    val activeUnit = if (unit == "Other") customUnitText.trim().ifBlank { "Pcs" } else unit
    val isValid = title.isNotBlank() && parsedPrice >= 0.0 && parsedQty > 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                                            text = "✓ $sessionAddedCount in Cart",
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
                                    "Added to cart! Add another or tap Done when finished"
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
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
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
                            if (note.isBlank()) note = "Customer Complimentary Gift"
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
                                    if (note.isBlank()) note = "Customer Complimentary Gift"
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
                                contentDescription = "Edit Presets",
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
                                    contentDescription = "Add Preset",
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
                                IconButton(onClick = { title = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    }

                    // Quantity Stepper Container (52dp)
                    val isWeightOrVolume = activeUnit.lowercase() in listOf("kg", "gm", "ltr", "liter", "litre", "mtr", "meter", "metre", "g", "ml")
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
                                        val cur = qtyText.toDoubleOrNull() ?: 1.0
                                        val next = (Math.round((cur - stepValue) * 100.0) / 100.0).coerceAtLeast(0.01)
                                        qtyText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Remove,
                                        contentDescription = "Decrease",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                BasicTextField(
                                    value = qtyText,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() || it == '.' }
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
                                        val cur = qtyText.toDoubleOrNull() ?: 1.0
                                        val next = Math.round((cur + stepValue) * 100.0) / 100.0
                                        qtyText = if (next % 1.0 == 0.0) next.toLong().toString() else String.format(Locale.US, "%.2f", next).trimEnd('0').trimEnd('.')
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Increase",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Enclosed Quick Quantity Preset Chips with 3D Balance Scale
                val isWeightOrVolumeUnit = activeUnit.lowercase() in listOf("kg", "gm", "ltr", "liter", "litre", "mtr", "meter", "metre", "g", "ml")
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
                                val cur = qtyText.toDoubleOrNull() ?: 0.0
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
                                val cur = qtyText.toDoubleOrNull() ?: 0.0
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
                                IconButton(onClick = { note = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
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

                // 10. Real-Time Live Subtotal Card with 3D Calculator
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
                                text = if (sessionAddedCount > 0) "Done ($sessionAddedCount)" else stringResource(R.string.cancel),
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
                                        onAddAnother(itemTitle, itemPrice, itemQty, itemNote, itemUnit)
                                    } else {
                                        onConfirm(itemTitle, itemPrice, itemQty, itemNote, itemUnit)
                                    }

                                    sessionAddedCount++
                                    Toast.makeText(context, "Added \"$itemTitle\" to cart! Enter next item.", Toast.LENGTH_SHORT).show()

                                    // Reset inputs to allow immediate entry of next item
                                    title = ""
                                    priceText = ""
                                    qtyText = "1"
                                    note = ""
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
                                    text = "Add Another",
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
                                onConfirm(title.trim(), parsedPrice, parsedQty, note.trim().takeIf { it.isNotBlank() }, activeUnit)
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
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(15.dp))
                    }
                }

                Text(
                    text = stringResource(R.string.cart_manage_presets_subtitle),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Presets List
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit",
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            modifier = Modifier.size(15.dp),
                                            tint = Color(0xFFEF4444)
                                        )
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
        var editName by remember { mutableStateOf(target.name) }
        var editPriceText by remember {
            mutableStateOf(if (target.defaultPrice % 1.0 == 0.0) target.defaultPrice.toLong().toString() else target.defaultPrice.toString())
        }
        var editUnit by remember { mutableStateOf(target.defaultUnit) }

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
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
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
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Text("✏️", fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            val updated = presetsList.toMutableList().apply { removeAt(index) }
                                            presetsList = updated
                                            onSavePresets(updated)
                                        },
                                        modifier = Modifier.size(28.dp)
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
                            val updated = presetsList.toMutableList().apply { set(idx, trimmed) }
                            presetsList = updated
                            onSavePresets(updated)
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
