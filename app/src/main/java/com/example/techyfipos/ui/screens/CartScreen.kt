@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package com.example.techyfipos.ui.screens
import com.example.techyfipos.ui.dialogs.*

import android.Manifest
import java.util.Locale
import android.content.Context
import android.content.pm.PackageManager
import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.utils.parseExpiryDateToMillis
import com.example.techyfipos.utils.PaymentMath
import com.example.techyfipos.utils.CartAuthHelper
import com.example.techyfipos.ui.viewmodel.AuthViewModel
import com.example.techyfipos.ui.viewmodel.isSyntheticSaleBarcode
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
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.res.stringArrayResource
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
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import com.example.techyfipos.R
import com.example.techyfipos.data.QuickSalePreset
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
import com.example.techyfipos.utils.isFractionalUnit
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
import com.example.techyfipos.data.db.SplitPaymentEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CartScanUpdate(
    val result: CartBarcodeResult,
    val timestamp: Long = System.currentTimeMillis()
)


internal fun calculateEffectiveAmountPaid(
    isSplitPayment: Boolean,
    paymentMethod: String,
    enteredAmountPaid: Double,
    grandTotal: Double,
    splitPayments: List<SplitPaymentEntry>
): Double = when {
    isSplitPayment -> splitPayments.filterNot { it.method == "Due" }.sumOf { it.amount }
    paymentMethod == "Due" -> 0.0
    paymentMethod == "Cash" -> enteredAmountPaid
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

@Composable
private fun CartTotalSummaryCard(
    subtotal: Double,
    discountAmount: Double,
    grandTotal: Double,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cardBg = if (isDark) Color(0xFF0A0E18) else Color(0xFFF8FAFC)
    val cardBorder = if (isDark) Color(0x1FFFFFFF) else Color(0xFFE2E8F0)
    val textSec = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val textMain = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val emeraldColor = Color(0xFF00C48C)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Subtotal Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.tablet_subtotal),
                    fontSize = 13.sp,
                    color = textSec,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTaka(subtotal),
                    fontSize = 13.5.sp,
                    color = textMain,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Optional Discount Row
            if (discountAmount > 0.0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏷️", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cart_discount_applied_badge),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFDC2626)
                            )
                        }
                        Text(
                            text = "-${formatTaka(discountAmount)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            // Order Total Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.total),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = textMain
                )
                Text(
                    text = formatTaka(grandTotal),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = emeraldColor
                )
            }
        }
    }
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
    isTablet: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSupervisorAuthDialog by remember { mutableStateOf(false) }
    var pendingSupervisorAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSupervisorDesc by remember { mutableStateOf("") }

    val userDiscountLimit by remember(authViewModel) { authViewModel?.maxDiscountPercent ?: kotlinx.coroutines.flow.MutableStateFlow(10.0) }.collectAsStateWithLifecycle(initialValue = 10.0)
    val canVoidCart = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_VOID_CART)
    val canRemoveItem = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_VOID_CART)
    val canApplyDiscount = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_APPLY_DISCOUNT)
    val canSell = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_SELL)
    val canHoldCart = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_HOLD_CART)
    val canChangePriceTier = isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_PRICE_TIER)
    val maxDiscountAllowed: Double = if (isOwner || isSupervisor || activePermissions.contains(AppPermissions.SALES_HIGH_DISCOUNT)) 100.0 else userDiscountLimit
    val isCompletingSale by cartViewModel.isCompletingSale.collectAsStateWithLifecycle()

    val shopName by settingsViewModel.shopName.collectAsStateWithLifecycle()
    val shopAddress by settingsViewModel.shopAddress.collectAsStateWithLifecycle()
    val shopPhone by settingsViewModel.shopPhone.collectAsStateWithLifecycle()
    val shopLogoUri by settingsViewModel.shopLogoUri.collectAsStateWithLifecycle()
    val facebookUrl by settingsViewModel.facebookUrl.collectAsStateWithLifecycle()
    val qrCodeInfo by settingsViewModel.qrCodeInfo.collectAsStateWithLifecycle()
    val cashierName by settingsViewModel.cashierName.collectAsStateWithLifecycle()
    val invoiceSettingsData by settingsViewModel.invoiceSettingsData.collectAsStateWithLifecycle()
    val quickSalePresets by settingsViewModel.quickSalePresets.collectAsStateWithLifecycle()

    val cartItems by cartViewModel.cartItems.collectAsStateWithLifecycle()
    val subtotal by cartViewModel.subtotal.collectAsStateWithLifecycle()
    val discountVal by cartViewModel.discountValue.collectAsStateWithLifecycle()
    val taxVal by cartViewModel.taxValue.collectAsStateWithLifecycle()
    val grandTotal by cartViewModel.grandTotal.collectAsStateWithLifecycle()
    val loyaltySettings by cartViewModel.loyaltySettings.collectAsStateWithLifecycle()

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
    val defaultCashier = invoiceSettingsData.cashierName.ifBlank { cashierName.ifBlank { stringResource(R.string.default_cashier_admin) } }
    val currentDisplayCashier = checkoutCashierName.ifBlank { defaultCashier }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri?.let { uri ->
            scope.launch(Dispatchers.IO) {
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

                    withContext(Dispatchers.Main) {
                        if (phone.isNotBlank()) {
                            val cleanPhone = phone.replace("[^0-9+]".toRegex(), "")
                            cartViewModel.onCustomerPhoneChanged(cleanPhone)
                            if (name.isNotBlank() && cartViewModel.customerName.value.isBlank()) {
                                cartViewModel.setCustomerName(name)
                            }
                        } else if (name.isNotBlank()) {
                            cartViewModel.onCustomerNameChanged(name)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.cart_could_not_read_contact), Toast.LENGTH_SHORT).show()
                    }
                }
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
    var editingNoteBarcode by remember { mutableStateOf<String?>(null) }
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
        } else {
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
    val hasDueInPayment = (isSplitPayment && splitPayments.any { it.method == "Due" && it.amount > 0 }) || (!isSplitPayment && paymentMethod == "Due")

    suspend fun doCompleteSale(snapshotAmountPaid: Double, snapshotChange: Double) {
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
                purchasePrice = item.product.purchasePrice,
                taxable = item.taxable
            )
        }
        val snapshotSubtotal = subtotal
        val snapshotMrpSubtotal = mrpSubtotal
        val snapshotMrpSavings = saleModeMrpSavings
        val snapshotIsSaleMode = (priceMode == PriceMode.DISCOUNTED)
        val snapshotWholesaleSavings = if (priceMode == PriceMode.WHOLESALE) priceModesSavings else 0.0
        val snapshotDiscount = discountVal
        val snapshotTax = taxVal
        val snapshotGrandTotal = grandTotal
        val snapshotStoreCreditApplied = cartViewModel.storeCreditApplied.value
        val snapshotSplitJson = cartViewModel.buildAuthoritativeSplitJson()
        val snapshotPaymentMethod = PaymentMath.resolveMethodToSave(
            isSplitPayment = isSplitPayment,
            storeCreditApplied = snapshotStoreCreditApplied,
            grandTotal = snapshotGrandTotal,
            paymentMethod = paymentMethod
        )
        val authoritativeChange = PaymentMath.calculateChange(
            grandTotal = snapshotGrandTotal,
            amountPaid = snapshotAmountPaid,
            paymentMethod = snapshotPaymentMethod,
            splitJson = snapshotSplitJson,
            storeCreditApplied = snapshotStoreCreditApplied
        )
        val snapshotCustomerName = customerName
        val snapshotCustomerPhone = customerPhone
        val snapshotHasDueInPayment = hasDueInPayment
        try {
            val snapshotRedeemedVoucher = cartViewModel.redeemedVoucher.value
            val snapshotVoucherCode = snapshotRedeemedVoucher?.code
            val snapshotVoucherAmount = if (snapshotRedeemedVoucher != null) {
                snapshotStoreCreditApplied.coerceAtMost(snapshotRedeemedVoucher.remainingBalance)
            } else null
            val postSaleRemainingVoucherBalance = if (snapshotRedeemedVoucher != null && snapshotVoucherAmount != null) {
                (snapshotRedeemedVoucher.remainingBalance - snapshotVoucherAmount).coerceAtLeast(0.0)
            } else null

            // Update ViewModel's amountPaid and cashierName before completeSale
            cartViewModel.setAmountPaid(snapshotAmountPaid)
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
                changeAmount = authoritativeChange,
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
                wholesaleSavings = snapshotWholesaleSavings,
                showItemWiseMrpDiscount = invoiceSettingsData.showItemWiseMrpDiscount,
                storeCreditApplied = snapshotStoreCreditApplied.takeIf { it > 0.0 },
                voucherCode = snapshotVoucherCode,
                voucherAmount = snapshotVoucherAmount,
                customerRemainingStoreCredit = postSaleRemainingStoreCredit,
                voucherRemainingBalance = postSaleRemainingVoucherBalance
            ))
        } catch (e: Exception) {
            android.util.Log.e("CartScreen", "Error completing sale", e)
            Toast.makeText(context, context.getString(R.string.cart_sale_error_toast, e.localizedMessage ?: ""), Toast.LENGTH_LONG).show()
        } finally {
            cartViewModel.setCompletingSale(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    PosTopAppBarTitle(
                        title = if (showCheckout) stringResource(R.string.complete_sale) else stringResource(R.string.cart),
                        metricBadge = if (cartItems.isNotEmpty() && !showCheckout) stringResource(R.string.cart_items_badge_count, cartItems.size) else null,
                        badgeColor = PosSectionColors.Cart,
                        isTablet = isTablet,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (cartItems.isNotEmpty() && !showCheckout) {
                        IconButton(
                            onClick = {
                                if (canHoldCart) {
                                    cartViewModel.holdCurrentCart()
                                    Toast.makeText(context, heldCartHoldSuccessMessage, Toast.LENGTH_SHORT).show()
                                } else if (authViewModel != null) {
                                    pendingSupervisorDesc = context.getString(R.string.supervisor_auth_desc_hold_cart)
                                    pendingSupervisorAction = {
                                        cartViewModel.holdCurrentCart()
                                        Toast.makeText(context, heldCartHoldSuccessMessage, Toast.LENGTH_SHORT).show()
                                    }
                                    showSupervisorAuthDialog = true
                                } else {
                                    Toast.makeText(context, context.getString(R.string.permission_required_hold_cart), Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("⏸️", fontSize = 18.sp)
                        }
                        val trashAnim = remember { Animatable(0f) }
                        var showVoidConfirmDialog by remember { mutableStateOf(false) }
                        if (showVoidConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showVoidConfirmDialog = false },
                                title = { Text(stringResource(R.string.cart_clear_confirm_title)) },
                                text = { Text(stringResource(R.string.cart_clear_confirm_message)) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showVoidConfirmDialog = false
                                            scope.launch {
                                                trashAnim.animateTo(-15f, tween(60))
                                                trashAnim.animateTo(15f, tween(60))
                                                trashAnim.animateTo(0f, tween(60))
                                            }
                                            cartViewModel.clearCart()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(stringResource(R.string.confirm))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVoidConfirmDialog = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }
                        IconButton(
                            onClick = {
                                CartAuthHelper.handleVoidCartRequest(
                                    context = context,
                                    isOwner = isOwner,
                                    isSupervisor = isSupervisor,
                                    activePermissions = activePermissions,
                                    authViewModel = authViewModel,
                                    onAllowed = { showVoidConfirmDialog = true },
                                    onRequestSupervisorAuth = { desc ->
                                        pendingSupervisorDesc = desc
                                        pendingSupervisorAction = {
                                            scope.launch {
                                                trashAnim.animateTo(-15f, tween(60))
                                                trashAnim.animateTo(15f, tween(60))
                                                trashAnim.animateTo(0f, tween(60))
                                            }
                                            cartViewModel.clearCart()
                                        }
                                        showSupervisorAuthDialog = true
                                    }
                                )
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
                onConfirm = { title, price, qty, note, u, taxable ->
                    cartViewModel.addCustomItem(title, price, qty, note, u, taxable)
                    showCustomItemDialog = false
                    Toast.makeText(context, context.getString(R.string.cart_custom_item_added_toast, title), Toast.LENGTH_SHORT).show()
                },
                onAddAnother = { title, price, qty, note, u, taxable ->
                    cartViewModel.addCustomItem(title, price, qty, note, u, taxable)
                }
            )
        }

        if (editingNoteBarcode != null) {
            val item = cartItems.find { it.product.barcode == editingNoteBarcode }
            val idx = cartItems.indexOfFirst { it.product.barcode == editingNoteBarcode }
            if (item != null && idx != -1) {
                EditItemNoteDialog(
                    initialNote = item.note,
                    productName = item.product.name,
                    onDismiss = { editingNoteBarcode = null },
                    onSave = { newNote ->
                        cartViewModel.setItemNote(idx, newNote)
                        editingNoteBarcode = null
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
                                text = stringResource(R.string.cart_quick_add_popular_title),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                letterSpacing = 0.5.sp
                            )

                            val effectiveQuickPresets = if (quickSalePresets.isNotEmpty()) {
                                quickSalePresets.take(4)
                            } else {
                                QuickSalePreset.defaultPresets().take(4)
                            }

                            effectiveQuickPresets.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { preset ->
                                        Surface(
                                            onClick = {
                                                cartViewModel.addCustomItem(preset.name, preset.defaultPrice, 1.0, context.getString(R.string.cart_quick_sale_note), preset.defaultUnit)
                                                Toast.makeText(context, context.getString(R.string.cart_custom_item_added_toast, preset.name), Toast.LENGTH_SHORT).show()
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
                                                Text(preset.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(formatTaka(preset.defaultPrice), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF059669).copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.common_add_chip),
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
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
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
                            onQuantityChanged = { qty ->
                                cartViewModel.setQuantity(
                                    product = item.product,
                                    newQty = qty,
                                    allowNegative = invoiceSettingsData.allowNegativeInventory || isOwner || isSupervisor
                                )
                            },
                            onRemove = {
                                CartAuthHelper.handleRemoveItemRequest(
                                    context = context,
                                    productName = item.product.name,
                                    isOwner = isOwner,
                                    isSupervisor = isSupervisor,
                                    activePermissions = activePermissions,
                                    authViewModel = authViewModel,
                                    onAllowed = { cartViewModel.removeFromCart(item.product) },
                                    onRequestSupervisorAuth = { desc ->
                                        pendingSupervisorDesc = desc
                                        pendingSupervisorAction = { cartViewModel.removeFromCart(item.product) }
                                        showSupervisorAuthDialog = true
                                    }
                                )
                            },
                            onEditNote = { editingNoteBarcode = item.product.barcode }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Cart Value Summary Card (Subtotal & Estimated Total)
                        CartTotalSummaryCard(
                            subtotal = subtotal,
                            discountAmount = discountAmount,
                            grandTotal = grandTotal
                        )

                        // Action Buttons: Hold & Checkout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Hold Cart Button
                            Button(
                                onClick = {
                                    if (canHoldCart) {
                                        showHoldCartDialog = true
                                    } else if (authViewModel != null) {
                                        pendingSupervisorDesc = context.getString(R.string.supervisor_auth_desc_hold_cart)
                                        pendingSupervisorAction = { showHoldCartDialog = true }
                                        showSupervisorAuthDialog = true
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.permission_required_hold_cart), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = cartItems.isNotEmpty(),
                                modifier = Modifier
                                    .weight(0.34f)
                                    .height(52.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD97706),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFFD97706).copy(alpha = 0.4f),
                                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("⏸️", fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.held_cart_hold_button), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                            }

                            // Continue to Checkout Button
                            Button(
                                onClick = { showCheckout = true },
                                enabled = cartItems.isNotEmpty(),
                                modifier = Modifier
                                    .weight(0.66f)
                                    .height(52.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFF10B981).copy(alpha = 0.4f),
                                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("🛍️", fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.cart_checkout_button, formatTaka(grandTotal)),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
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
                                    val rawUnitStr = if (unitCount % 1.0 == 0.0) unitCount.toLong().toString() else String.format(Locale.US, "%.2f", unitCount).trimEnd('0').trimEnd('.')
                                    val unitCountStr = if (Locale.getDefault().language == "bn") com.example.techyfipos.utils.toBanglaDigits(rawUnitStr) else rawUnitStr
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
                                    } else if (authViewModel != null) {
                                        pendingSupervisorDesc = context.getString(R.string.supervisor_auth_desc_price_tier, it.name)
                                        pendingSupervisorAction = { cartViewModel.setPriceMode(it) }
                                        showSupervisorAuthDialog = true
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.permission_required_price_tier), Toast.LENGTH_SHORT).show()
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
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = stringResource(R.string.cd_select_cashier),
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
                                    val defaultCashiers = stringArrayResource(R.array.default_cashier_names).toList()
                                    val rawPresets = invoiceSettingsData.cashierPresets.ifEmpty { defaultCashiers }
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
                                            val discountFormatted = if (isPercentageDiscount) "$sanitized%" else formatTaka(parsed)
                                            pendingSupervisorDesc = context.getString(R.string.supervisor_auth_desc_discount, discountFormatted, maxDiscountAllowed.toInt())
                                            pendingSupervisorAction = {
                                                discountInput = sanitized
                                                cartViewModel.setDiscountAmount(parsed)
                                            }
                                            showSupervisorAuthDialog = true
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.permission_required_discount_limit, maxDiscountAllowed.toInt()), Toast.LENGTH_SHORT).show()
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
                                                contentDescription = stringResource(R.string.cd_switch_discount_unit),
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
                                onClick = { cartViewModel.setSplitPayment(false) },
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
                                    cartViewModel.setSplitPayment(true)
                                    if (splitPayments.isEmpty()) {
                                        val remainingToPay = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
                                        val firstLeg = if (remainingToPay >= 2.0) (remainingToPay / 2).coerceAtLeast(1.0) else remainingToPay
                                        val secondLeg = (remainingToPay - firstLeg).coerceAtLeast(0.0)
                                        cartViewModel.setSplitPayments(
                                            listOf(
                                                com.example.techyfipos.data.db.SplitPaymentEntry("Cash", firstLeg),
                                                com.example.techyfipos.data.db.SplitPaymentEntry("bKash", secondLeg)
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
                                                else stringResource(R.string.cart_bill_covered_by_store_credit),
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
                                        modifier = Modifier.heightIn(min = 48.dp)
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
                                            val credAmt = (redeemedVoucher?.remainingBalance ?: 0.0) + availableCustCredit
                                            Text(
                                                text = stringResource(R.string.cart_store_credit_available, formatTaka(credAmt)),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val credAmt = (redeemedVoucher?.remainingBalance ?: 0.0) + availableCustCredit
                                            val applyAmt = kotlin.math.min(credAmt, grandTotal)
                                            cartViewModel.setStoreCreditApplied(applyAmt)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        modifier = Modifier.heightIn(min = 48.dp)
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
                                            stringResource(R.string.cart_enter_store_credit_voucher_code_desc),
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
                                                voucherError = context.getString(R.string.voucher_err_please_enter)
                                                return@Button
                                            }
                                            isCheckingVoucher = true
                                            cartViewModel.redeemVoucherCode(
                                                code = code,
                                                onSuccess = { voucher ->
                                                    isCheckingVoucher = false
                                                    val applyAmt = kotlin.math.min(voucher.remainingBalance, grandTotal)
                                                    cartViewModel.setStoreCreditApplied(applyAmt)
                                                    showVoucherRedeemDialog = false
                                                    Toast.makeText(context, context.getString(R.string.voucher_toast_applied, formatTaka(applyAmt)), Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { errResId ->
                                                    isCheckingVoucher = false
                                                    voucherError = context.getString(errResId)
                                                }
                                            )
                                        },
                                        enabled = !isCheckingVoucher && voucherInput.isNotBlank()
                                    ) {
                                        if (isCheckingVoucher) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Text(stringResource(R.string.cart_redeem_voucher_button))
                                            }
                                        } else {
                                            Text(stringResource(R.string.cart_redeem_voucher_button))
                                        }
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
                            val targetSplitTotal = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
                            SplitPaymentBuilder(
                                grandTotal = targetSplitTotal,
                                splitEntries = splitPayments,
                                onEntriesChanged = { cartViewModel.setSplitPayments(it) }
                            )

                            val hasCashSplit = splitPayments.any { it.method == "Cash" }
                            val totalSplitCollected = splitPayments.sumOf { it.amount }
                            val splitChangeDue = if (hasCashSplit && totalSplitCollected > targetSplitTotal) {
                                (totalSplitCollected - targetSplitTotal)
                            } else 0.0

                            if (splitChangeDue > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF059669).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("💵", fontSize = 16.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = stringResource(R.string.change),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF059669)
                                                )
                                                Text(
                                                    text = stringResource(R.string.split_payment_cash_change_desc),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatTaka(splitChangeDue),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💳", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.cart_payment_method_label), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val availablePaymentMethods = remember(resolvedCustomer?.storeCredit to (redeemedVoucher != null)) {
                                if ((resolvedCustomer?.storeCredit ?: 0.0) > 0.0 || redeemedVoucher != null) {
                                    listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due", "Store Credit")
                                } else {
                                    listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due")
                                }
                            }

                            val chipAnims = remember(availablePaymentMethods) {
                                availablePaymentMethods.associateWith { Animatable(1f) }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                maxItemsInEachRow = 3
                            ) {
                                availablePaymentMethods.forEach { method ->
                                    val chipScaleAnim = chipAnims[method] ?: Animatable(1f)
                                    FilterChip(
                                        selected = paymentMethod == method,
                                        onClick = {
                                            scope.launch {
                                                chipScaleAnim.animateTo(0.82f, tween(70))
                                                chipScaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                            }
                                            cartViewModel.setPaymentMethod(method)
                                            if (method != "Cash") {
                                                receivedAmountText = ""
                                                cartViewModel.setAmountPaid(0.0)
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
                                                        "Cash" -> stringResource(R.string.cash)
                                                        "bKash" -> stringResource(R.string.bkash)
                                                        "Nagad" -> stringResource(R.string.nagad)
                                                        "Rocket" -> stringResource(R.string.rocket)
                                                        "Card" -> stringResource(R.string.card)
                                                        "Bank" -> stringResource(R.string.bank)
                                                        "Due" -> stringResource(R.string.due)
                                                        "Store Credit" -> stringResource(R.string.store_credit)
                                                        "Split" -> stringResource(R.string.cart_split_payment)
                                                        else -> method.ifBlank { stringResource(R.string.cash) }
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
                                val availableCredit = (resolvedCustomer?.storeCredit ?: 0.0) + (redeemedVoucher?.remainingBalance ?: 0.0)
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
                                                stringResource(R.string.cart_available_store_credit, formatTaka(availableCredit)),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1B5E20)
                                            )
                                            Text(
                                                stringResource(R.string.cart_store_credit_direct_deduct_desc, formatTaka(grandTotal)),
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
                                    onValueChange = { cartViewModel.setWalletPhone(it.filter(Char::isDigit).take(11)) },
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
                                    val cardTypes = remember { listOf("Visa", "Mastercard", "Amex", "Other") }
                                    val cardChipAnims = remember(cardTypes) {
                                        cardTypes.associateWith { Animatable(1f) }
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        cardTypes.forEach { type ->
                                            val cardChipScaleAnim = cardChipAnims[type] ?: Animatable(1f)
                                            FilterChip(
                                                selected = cardType == type,
                                                onClick = {
                                                    scope.launch {
                                                        cardChipScaleAnim.animateTo(0.82f, tween(70))
                                                        cardChipScaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                                    }
                                                    cartViewModel.setCardType(type)
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
                                        onValueChange = { cartViewModel.setCardLastFour(it.filter(Char::isDigit).take(4)) },
                                        label = { Text(stringResource(R.string.cart_last_4_digits_label)) },
                                        leadingIcon = { Text("🔢", fontSize = 16.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }


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
                                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear), modifier = Modifier.size(16.dp))
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
                                                contentDescription = stringResource(R.string.cd_pick_from_contacts),
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
                                                    Text(suggestion.name.ifBlank { stringResource(R.string.customer) }, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                    Text(
                                                        "${suggestion.phone}${if (!suggestion.tag.isNullOrBlank()) " • ${suggestion.tag}" else ""}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (suggestion.dueAmount > 0) {
                                                    Text(
                                                        stringResource(R.string.cart_customer_due_prefix, formatTaka(suggestion.dueAmount)),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.cd_select),
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
                                                    stringResource(R.string.cart_badge_tier, resolvedCustomer!!.tier),
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
                                                        text = stringResource(R.string.cart_customer_credit_badge, formatTaka(resolvedCustomer!!.storeCredit)),
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
                                                        text = stringResource(R.string.cart_badge_due, formatTaka(resolvedCustomer!!.dueAmount)),
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
                                                        text = stringResource(R.string.cart_badge_loyalty_pts, resolvedCustomer!!.loyaltyPoints.toString()),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (resolvedCustomer!!.totalOrders > 0) {
                                                Text(
                                                    text = stringResource(R.string.cart_badge_orders, resolvedCustomer!!.totalOrders),
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
                                            val pointCashValue = loyaltySettings.pointCashValue
                                            cartViewModel.setLoyaltyDiscount(loyaltyCustomer!!.pointsBalance * pointCashValue, loyaltySettings)
                                        } else {
                                            cartViewModel.setLoyaltyDiscount(0.0, loyaltySettings)
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
                        val remainingAfterSC = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
                        val effectiveChangeAmt = (effectiveAmountPaid - remainingAfterSC).coerceAtLeast(0.0)

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
                                            cartViewModel.setAmountPaid(exactAmt)
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
                                                cartViewModel.setAmountPaid(updated)
                                                receivedAmountText = if (updated % 1.0 == 0.0) updated.toLong().toString() else updated.toString()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Text(
                                                text = "+${formatTaka(inc)}",
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
                                                cartViewModel.setAmountPaid(0.0)
                                                receivedAmountText = ""
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.cart_clear_chip),
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
                                        cartViewModel.setAmountPaid(input.toDoubleOrNull() ?: 0.0)
                                    },
                                    label = { Text(stringResource(R.string.cart_cash_received_label), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.5.sp) },
                                    leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                                } else if (paidVal > 0.0 && paidVal < remainingAfterSC) {
                                    val shortAmt = remainingAfterSC - paidVal
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
                                                stringResource(R.string.cart_short_amount_warning),
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
                                            text = stringResource(R.string.cart_due_sale_title),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = stringResource(R.string.cart_due_sale_notice, formatTaka((grandTotal - (amountPaid + storeCreditApplied)).coerceAtLeast(0.0))),
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
                                                stringResource(R.string.cart_percent_off, pctStr),
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
                                        Text(stringResource(R.string.cart_savings_with_pct, formatTaka(totalSavings), pctStr), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF15803D))
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
                        val isCashValid = isSplitPayment || paymentMethod != "Cash" || remainingToPay == 0.0 || (effectiveAmountPaid >= remainingToPay && effectiveAmountPaid > 0.0)
                        val isStoreCreditValid = isSplitPayment || paymentMethod != "Store Credit" || remainingToPay == 0.0 || (((resolvedCustomer?.storeCredit ?: 0.0) + (redeemedVoucher?.remainingBalance ?: 0.0)) >= grandTotal)
                        val expectedSplitTotal = if (storeCreditApplied > 0.0) remainingToPay else grandTotal
                        val hasCashInSplit = isSplitPayment && splitPayments.any { it.method == "Cash" }
                        val splitCollected = splitPayments.sumOf { it.amount }
                        val isSplitValid = !isSplitPayment || (splitPayments.isNotEmpty() && (
                            kotlin.math.abs(splitCollected - expectedSplitTotal) < 0.01 ||
                            (hasCashInSplit && splitCollected >= expectedSplitTotal)
                        ))
                        val isSplitDetailsValid = !isSplitPayment || splitPayments.all { sp ->
                            when (sp.method) {
                                "bKash", "Nagad", "Rocket" -> sp.walletPhone?.length == 11
                                "Card" -> sp.cardLastFour?.length == 4
                                else -> true
                            }
                        }
                        val isTotalValid = grandTotal > 0.0
                        val isCheckoutEnabled = cartItems.isNotEmpty() && isTotalValid && isDueValid && isWalletValid && isCardValid && isCashValid && isStoreCreditValid && isSplitValid && isSplitDetailsValid && canSell

                        if (!canSell) {
                            Text(stringResource(R.string.perm_checkout_required, stringResource(R.string.perm_sales_sell_title)), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        } else if (!isCheckoutEnabled) {
                            val reason = when {
                                !isTotalValid -> stringResource(R.string.cart_reason_total_zero)
                                !isDueValid -> stringResource(R.string.cart_reason_due_details_required)
                                isSplitPayment && !isSplitValid -> stringResource(R.string.cart_reason_split_total_mismatch)
                                isSplitPayment && !isSplitDetailsValid -> stringResource(R.string.cart_reason_split_details_required)
                                !isWalletValid -> stringResource(R.string.cart_reason_wallet_number_required, paymentMethod)
                                !isCardValid -> stringResource(R.string.cart_reason_card_digits_required)
                                !isCashValid -> stringResource(R.string.cart_reason_cash_insufficient)
                                !isStoreCreditValid -> {
                                    if (customerPhone.isBlank() && redeemedVoucher == null) stringResource(R.string.cart_reason_store_credit_phone_or_voucher)
                                    else stringResource(R.string.cart_reason_store_credit_insufficient, formatTaka(if (redeemedVoucher != null) redeemedVoucher!!.remainingBalance else (resolvedCustomer?.storeCredit ?: 0.0)))
                                }
                                else -> stringResource(R.string.cart_reason_generic_incomplete)
                            }
                            Text(reason, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }

                        val executeSaleAction = {
                            if (!isCompletingSale && grandTotal > 0.0) {
                                cartViewModel.setCompletingSale(true)
                                val snapshotSplitJson = cartViewModel.buildAuthoritativeSplitJson()
                                val snapshotPaymentMethod = PaymentMath.resolveMethodToSave(
                                    isSplitPayment = isSplitPayment,
                                    storeCreditApplied = storeCreditApplied,
                                    grandTotal = grandTotal,
                                    paymentMethod = paymentMethod
                                )
                                val snapshotAmountPaid = effectiveAmountPaid
                                val snapshotChange = PaymentMath.calculateChange(
                                    grandTotal = grandTotal,
                                    amountPaid = snapshotAmountPaid,
                                    paymentMethod = snapshotPaymentMethod,
                                    splitJson = snapshotSplitJson,
                                    storeCreditApplied = storeCreditApplied
                                )
                                if (snapshotPaymentMethod == "Cash" && remainingToPay > 0.0 && snapshotAmountPaid < remainingToPay) {
                                    cartViewModel.setCompletingSale(false)
                                    android.widget.Toast.makeText(context, context.getString(R.string.cart_reason_cash_insufficient), android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        doCompleteSale(snapshotAmountPaid, snapshotChange)
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
                                        (grandTotal - storeCreditApplied - effectiveAmountPaid).coerceAtLeast(0.0)
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

                                    val hasNegativeStock = cartItems.any { it.quantity > it.product.stockQty && !isSyntheticSaleBarcode(it.product.barcode) }
                                    if (hasNegativeStock && !invoiceSettingsData.allowNegativeInventory && !isOwner && !isSupervisor) {
                                        if (authViewModel != null) {
                                            pendingSupervisorDesc = context.getString(R.string.supervisor_auth_desc_negative_stock)
                                            pendingSupervisorAction = { proceedWithSale() }
                                            showSupervisorAuthDialog = true
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.permission_required_negative_stock), Toast.LENGTH_LONG).show()
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
                val snapshotSplitJson = cartViewModel.buildAuthoritativeSplitJson()
                val snapshotPaymentMethod = PaymentMath.resolveMethodToSave(
                    isSplitPayment = isSplitPayment,
                    storeCreditApplied = storeCreditApplied,
                    grandTotal = grandTotal,
                    paymentMethod = paymentMethod
                )
                val snapshotAmountPaid = calculateEffectiveAmountPaid(
                    isSplitPayment = isSplitPayment,
                    paymentMethod = paymentMethod,
                    enteredAmountPaid = if (paymentMethod == "Cash") (receivedAmountText.toDoubleOrNull() ?: 0.0) else 0.0,
                    grandTotal = grandTotal,
                    splitPayments = splitPayments
                )
                val snapshotChange = PaymentMath.calculateChange(
                    grandTotal = grandTotal,
                    amountPaid = snapshotAmountPaid,
                    paymentMethod = snapshotPaymentMethod,
                    splitJson = snapshotSplitJson,
                    storeCreditApplied = storeCreditApplied
                )
                val triggerRemainingToPay = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
                if (snapshotPaymentMethod == "Cash" && triggerRemainingToPay > 0.0 && snapshotAmountPaid < triggerRemainingToPay) {
                    android.widget.Toast.makeText(context, context.getString(R.string.cart_reason_cash_insufficient), android.widget.Toast.LENGTH_SHORT).show()
                } else if (!isCompletingSale && grandTotal > 0.0) {
                    cartViewModel.setCompletingSale(true)
                    scope.launch {
                        doCompleteSale(snapshotAmountPaid, snapshotChange)
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
                        Toast.makeText(context, context.getString(R.string.held_cart_held_toast, finalTitle), Toast.LENGTH_SHORT).show()
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
                        if (result.unavailableLines > 0) context.getString(R.string.held_cart_restored_partial_toast, result.unavailableLines)
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
                discountInput = editableAmount(discountAmount)
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
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            value = System.currentTimeMillis()
        }
    }

    LaunchedEffect(item.quantity) {
        qtyBounceAnim.animateTo(1.35f, tween(90, easing = FastOutSlowInEasing))
        qtyBounceAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    if (showQuantityDialog) {
        val isCustomItem = isSyntheticSaleBarcode(item.product.barcode) ||
                item.product.barcode.startsWith("CUSTOM-") ||
                item.product.barcode.startsWith("ADHOC-") ||
                item.product.stockQty >= 999999.0

        ManualQuantityDialog(
            currentQuantity = item.quantity,
            availableStock = item.product.stockQty,
            allowNegativeInventory = allowNegativeInventory,
            isCustomItem = isCustomItem,
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
                        if (expMillis != null && expMillis > 0) {
                            if (expMillis < now) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEE2E2),
                                    border = BorderStroke(0.8.dp, Color(0xFFEF4444))
                                ) {
                                    Text(
                                        text = stringResource(R.string.cart_expired_badge, item.product.expiryDate ?: ""),
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
                                        text = stringResource(R.string.cart_expiring_badge, daysLeft, item.product.expiryDate ?: ""),
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
                val isFractionalUnit = isFractionalUnit(item.product.unit)
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
                        modifier = Modifier.size(48.dp)
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
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 2.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            val rawDisplayQty = if (item.quantity % 1.0 == 0.0) item.quantity.toLong().toString() else String.format(Locale.US, "%.2f", item.quantity).trimEnd('0').trimEnd('.')
                            val displayQty = if (Locale.getDefault().language == "bn") com.example.techyfipos.utils.toBanglaDigits(rawDisplayQty) else rawDisplayQty
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
                            if (allowNegativeInventory || next <= item.product.stockQty) {
                                onQuantityChanged(next)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981),
                        contentColor = Color.White,
                        modifier = Modifier.size(48.dp)
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
                        modifier = Modifier.size(48.dp)
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
