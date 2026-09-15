package com.example.techyfipos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.techyfipos.R
import com.example.techyfipos.data.DataRepository
import com.example.techyfipos.data.BackupImportMode
import com.example.techyfipos.data.AppPermissions
import com.example.techyfipos.data.AppRoles
import com.example.techyfipos.data.db.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.content.Context
import com.example.techyfipos.utils.parseExpiryDateToMillis
import com.example.techyfipos.utils.roundMoney
import com.example.techyfipos.utils.toEnglishDigits
import com.example.techyfipos.utils.BillData

// Cart Item Wrapper
data class CartItem(
    val product: Product,
    val quantity: Double = 1.0,
    val note: String? = null
)

enum class CartBarcodeStatus {
    ADDED,
    STOCK_LIMIT,
    NOT_FOUND
}

/** The active price tier used in the cart and checkout. */
enum class PriceMode {
    /** Use sellingPrice (default retail / discounted price) */
    DISCOUNTED,
    /** Use mrpPrice (Maximum Retail Price); falls back to sellingPrice if mrpPrice == 0 */
    MRP,
    /** Use wholesalePrice; falls back to sellingPrice if wholesalePrice == 0 */
    WHOLESALE
}

data class CartBarcodeResult(
    val barcode: String,
    val productName: String? = null,
    val quantityInCart: Double = 0.0,
    val grandTotal: Double = 0.0,
    val status: CartBarcodeStatus
)

internal fun calculateDiscountValue(
    subtotal: Double,
    discountAmount: Double,
    isPercentage: Boolean
): Double {
    val safeSubtotal = subtotal.coerceAtLeast(0.0)
    val safeAmount = discountAmount.coerceAtLeast(0.0)
    val calculated = if (isPercentage) {
        safeSubtotal * safeAmount.coerceAtMost(100.0) / 100.0
    } else {
        safeAmount
    }
    return calculated.coerceIn(0.0, safeSubtotal).roundMoney()
}

/** Quick Sale / ad-hoc cart items use a synthetic, never-persisted product with this barcode prefix. */
internal fun isSyntheticSaleBarcode(barcode: String): Boolean =
    barcode.startsWith("CUSTOM-") || barcode.startsWith("ADHOC-")

/**
 * Estimated cost basis for a sold line item whose product record can no longer be found (deleted
 * product, or a synthetic Quick Sale item that was never persisted). Quick Sale items are pure
 * margin (delivery fees, service charges, etc.) with a real cost of 0. Genuinely deleted products
 * without a recorded purchasePriceAtSale default conservatively to break-even (cost = sale price)
 * rather than fabricating an arbitrary 30% profit that distorts low-margin grocery reports.
 */
internal fun fallbackPurchasePrice(barcode: String, price: Double, defaultCostPercentage: Double = 100.0): Double =
    if (isSyntheticSaleBarcode(barcode)) 0.0 else price * (defaultCostPercentage.coerceIn(0.0, 100.0) / 100.0)

internal data class HeldCartPayload(
    val items: List<CartItem>,
    val customerName: String = "",
    val customerPhone: String = "",
    val discountAmount: Double = 0.0,
    val isPercentageDiscount: Boolean = false,
    val priceMode: PriceMode = PriceMode.DISCOUNTED,
    val paymentMethod: String = "Cash",
    val walletPhone: String = "",
    val cardType: String = "Visa",
    val cardLastFour: String = "",
    val amountPaid: Double = 0.0,
    val isSplitPayment: Boolean = false,
    val splitPayments: List<SplitPaymentEntry> = emptyList(),
    val loyaltyPhone: String? = null,
    val loyaltyDiscount: Double = 0.0,
    val storeCreditApplied: Double = 0.0,
    val voucherCode: String? = null,
    val voucherOriginalBalance: Double? = null,
    val voucherRemainingBalance: Double? = null,
    val voucherCustomerPhone: String? = null
)

/** Versioned payload stored inside HeldCart.itemsJson. Legacy array-only carts remain readable. */
internal fun encodeHeldCartPayload(payload: HeldCartPayload): String = JSONObject().apply {
    put("version", 2)
    put("customerName", payload.customerName)
    put("customerPhone", payload.customerPhone)
    put("discountAmount", payload.discountAmount)
    put("isPercentageDiscount", payload.isPercentageDiscount)
    put("priceMode", payload.priceMode.name)
    put("paymentMethod", payload.paymentMethod)
    put("walletPhone", payload.walletPhone)
    put("cardType", payload.cardType)
    put("cardLastFour", payload.cardLastFour)
    put("amountPaid", payload.amountPaid)
    put("isSplitPayment", payload.isSplitPayment)
    put("loyaltyPhone", payload.loyaltyPhone ?: "")
    put("loyaltyDiscount", payload.loyaltyDiscount)
    put("storeCreditApplied", payload.storeCreditApplied)
    if (!payload.voucherCode.isNullOrBlank()) {
        put("voucherCode", payload.voucherCode)
        payload.voucherOriginalBalance?.let { put("voucherOriginalBalance", it) }
        payload.voucherRemainingBalance?.let { put("voucherRemainingBalance", it) }
        payload.voucherCustomerPhone?.let { put("voucherCustomerPhone", it) }
    }
    put("items", JSONArray().apply {
        payload.items.forEach { item ->
            put(JSONObject().apply {
                put("barcode", item.product.barcode)
                put("quantity", item.quantity)
                put("note", item.note ?: "")
                if (isSyntheticSaleBarcode(item.product.barcode)) {
                    put("name", item.product.name)
                    put("category", item.product.category)
                    put("purchasePrice", item.product.purchasePrice)
                    put("sellingPrice", item.product.sellingPrice)
                    put("unit", item.product.unit)
                    put("wholesalePrice", item.product.wholesalePrice)
                    put("mrpPrice", item.product.mrpPrice)
                    put("discountedPrice", item.product.discountedPrice)
                }
            })
        }
    })
    put("splitPayments", JSONArray().apply {
        payload.splitPayments.forEach { payment ->
            put(JSONObject().apply {
                put("method", payment.method)
                put("amount", payment.amount)
                put("walletPhone", payment.walletPhone ?: "")
                put("cardType", payment.cardType ?: "")
                put("cardLastFour", payment.cardLastFour ?: "")
                put("referenceNote", payment.referenceNote ?: "")
            })
        }
    })
}.toString()

internal fun decodeHeldCartPayload(json: String): HeldCartPayload {
    val root = json.trim()
    val objectPayload = root.startsWith("{")
    val objectJson = if (objectPayload) JSONObject(root) else null
    val itemArray = objectJson?.optJSONArray("items") ?: JSONArray(root)
    val items = buildList {
        for (index in 0 until itemArray.length()) {
            val item = itemArray.getJSONObject(index)
            val barcode = item.optString("barcode").trim()
            if (barcode.isBlank()) continue
            val synthetic = isSyntheticSaleBarcode(barcode) && item.has("name")
            val snapshot = if (synthetic) {
                Product(
                    barcode = barcode,
                    name = item.optString("name", "Quick Sale"),
                    category = item.optString("category", "Quick Sale"),
                    purchasePrice = item.optDouble("purchasePrice", 0.0),
                    sellingPrice = item.optDouble("sellingPrice", item.optDouble("price", 0.0)),
                    stockQty = 999999.0,
                    reorderLevel = 0,
                    unit = item.optString("unit", "Pcs"),
                    wholesalePrice = item.optDouble("wholesalePrice", 0.0),
                    mrpPrice = item.optDouble("mrpPrice", 0.0),
                    discountedPrice = item.optDouble("discountedPrice", 0.0)
                )
            } else {
                // A placeholder is resolved against the current product row during restore.
                Product(barcode, "", "", 0.0, 0.0, 0.0, 0)
            }
            add(
                CartItem(
                    product = snapshot,
                    quantity = item.optDouble("quantity", 1.0).coerceAtLeast(0.0),
                    note = item.optString("note").takeIf(String::isNotBlank)
                )
            )
        }
    }
    val split = objectJson?.optJSONArray("splitPayments")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SplitPaymentEntry(
                        method = item.optString("method", "Cash"),
                        amount = item.optDouble("amount", 0.0),
                        walletPhone = item.optString("walletPhone").takeIf(String::isNotBlank),
                        cardType = item.optString("cardType").takeIf(String::isNotBlank),
                        cardLastFour = item.optString("cardLastFour").takeIf(String::isNotBlank),
                        referenceNote = item.optString("referenceNote").takeIf(String::isNotBlank)
                    )
                )
            }
        }
    }.orEmpty()
    return HeldCartPayload(
        items = items,
        customerName = objectJson?.optString("customerName", "") ?: "",
        customerPhone = objectJson?.optString("customerPhone", "") ?: "",
        discountAmount = objectJson?.optDouble("discountAmount", 0.0) ?: 0.0,
        isPercentageDiscount = objectJson?.optBoolean("isPercentageDiscount", false) ?: false,
        priceMode = objectJson?.optString("priceMode")
            ?.let { runCatching { PriceMode.valueOf(it) }.getOrNull() } ?: PriceMode.DISCOUNTED,
        paymentMethod = objectJson?.optString("paymentMethod", "Cash") ?: "Cash",
        walletPhone = objectJson?.optString("walletPhone", "") ?: "",
        cardType = objectJson?.optString("cardType", "Visa") ?: "Visa",
        cardLastFour = objectJson?.optString("cardLastFour", "") ?: "",
        amountPaid = objectJson?.optDouble("amountPaid", 0.0) ?: 0.0,
        isSplitPayment = objectJson?.optBoolean("isSplitPayment", false) ?: false,
        splitPayments = split,
        loyaltyPhone = objectJson?.optString("loyaltyPhone")?.takeIf(String::isNotBlank),
        loyaltyDiscount = objectJson?.optDouble("loyaltyDiscount", 0.0) ?: 0.0,
        storeCreditApplied = objectJson?.optDouble("storeCreditApplied", 0.0) ?: 0.0,
        voucherCode = objectJson?.optString("voucherCode")?.takeIf(String::isNotBlank),
        voucherOriginalBalance = if (objectJson?.has("voucherOriginalBalance") == true) objectJson.optDouble("voucherOriginalBalance") else null,
        voucherRemainingBalance = if (objectJson?.has("voucherRemainingBalance") == true) objectJson.optDouble("voucherRemainingBalance") else null,
        voucherCustomerPhone = objectJson?.optString("voucherCustomerPhone")?.takeIf(String::isNotBlank)
    )
}

class CartViewModel(private val repository: DataRepository) : ViewModel() {
    private val saleCompletionMutex = kotlinx.coroutines.sync.Mutex()
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _discountAmount = MutableStateFlow(0.0)
    val discountAmount: StateFlow<Double> = _discountAmount.asStateFlow()

    private val _isPercentageDiscount = MutableStateFlow(false)
    val isPercentageDiscount: StateFlow<Boolean> = _isPercentageDiscount.asStateFlow()

    private val _paymentMethod = MutableStateFlow("Cash") // "Cash", "bKash", "Nagad", "Rocket", "Card", "Due"
    val paymentMethod: StateFlow<String> = _paymentMethod.asStateFlow()

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _customerPhone = MutableStateFlow("")
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    private val _walletPhone = MutableStateFlow("")
    val walletPhone: StateFlow<String> = _walletPhone.asStateFlow()

    private val _cardType = MutableStateFlow("Visa")
    val cardType: StateFlow<String> = _cardType.asStateFlow()

    private val _cardLastFour = MutableStateFlow("")
    val cardLastFour: StateFlow<String> = _cardLastFour.asStateFlow()

    private val _manualCashierName = MutableStateFlow<String?>(null)
    val cashierName: StateFlow<String> = combine(
        repository.cashierNameFlow,
        _manualCashierName
    ) { defaultCashier, manual ->
        if (!manual.isNullOrBlank()) {
            manual
        } else if (defaultCashier.isNotBlank()) {
            defaultCashier
        } else {
            "Admin"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setCashierName(name: String) {
        _manualCashierName.value = name.takeIf { it.isNotBlank() }
    }

    private val _showReceiptDialog = MutableStateFlow(false)
    val showReceiptDialog: StateFlow<Boolean> = _showReceiptDialog.asStateFlow()

    private val _completedBillSnapshot = MutableStateFlow<BillData?>(null)
    val completedBillSnapshot: StateFlow<BillData?> = _completedBillSnapshot.asStateFlow()

    fun openReceiptDialog(data: BillData) {
        _completedBillSnapshot.value = data
        _showReceiptDialog.value = true
    }

    fun dismissReceiptDialog() {
        _showReceiptDialog.value = false
        _completedBillSnapshot.value = null
    }

    private val _amountPaid = MutableStateFlow(0.0) // Cash received
    val amountPaid: StateFlow<Double> = _amountPaid.asStateFlow()

    // Price mode (MRP / DISCOUNTED / WHOLESALE)
    private val _priceMode = MutableStateFlow(PriceMode.DISCOUNTED)
    val priceMode: StateFlow<PriceMode> = _priceMode.asStateFlow()

    fun setPriceMode(mode: PriceMode) {
        _priceMode.value = mode
    }

    // Loyalty points state
    private val _loyaltyDiscount = MutableStateFlow(0.0)
    val loyaltyDiscount: StateFlow<Double> = _loyaltyDiscount.asStateFlow()

    private val _loyaltyCustomer = MutableStateFlow<CustomerLoyalty?>(null)
    val loyaltyCustomer: StateFlow<CustomerLoyalty?> = _loyaltyCustomer.asStateFlow()

    // Store Credit and Voucher state
    private val _storeCreditApplied = MutableStateFlow(0.0)
    val storeCreditApplied: StateFlow<Double> = _storeCreditApplied.asStateFlow()

    private val _redeemedVoucher = MutableStateFlow<StoreCreditVoucher?>(null)
    val redeemedVoucher: StateFlow<StoreCreditVoucher?> = _redeemedVoucher.asStateFlow()

    private val _isCompletingSale = MutableStateFlow(false)
    val isCompletingSale: StateFlow<Boolean> = _isCompletingSale.asStateFlow()

    fun setDiscount(amount: Double, isPercentage: Boolean) {
        _discountAmount.value = amount.coerceAtLeast(0.0)
        _isPercentageDiscount.value = isPercentage
    }

    fun setDiscountAmount(amount: Double) {
        _discountAmount.value = amount.coerceAtLeast(0.0)
    }

    fun setIsPercentageDiscount(isPercentage: Boolean) {
        _isPercentageDiscount.value = isPercentage
    }

    fun setPaymentMethod(method: String) {
        _paymentMethod.value = method
    }

    fun setCustomerName(name: String) {
        _customerName.value = name
    }

    fun setCustomerPhone(phone: String) {
        _customerPhone.value = phone
    }

    fun setWalletPhone(phone: String) {
        _walletPhone.value = phone
    }

    fun setCardType(type: String) {
        _cardType.value = type
    }

    fun setCardLastFour(lastFour: String) {
        _cardLastFour.value = lastFour
    }

    fun setAmountPaid(amount: Double) {
        _amountPaid.value = amount.coerceAtLeast(0.0)
    }

    fun setLoyaltyDiscount(discount: Double) {
        _loyaltyDiscount.value = discount.coerceAtLeast(0.0)
    }

    fun setLoyaltyCustomer(customer: CustomerLoyalty?) {
        _loyaltyCustomer.value = customer
    }

    fun setStoreCreditApplied(amount: Double) {
        _storeCreditApplied.value = amount.coerceAtLeast(0.0)
    }

    fun setRedeemedVoucher(voucher: StoreCreditVoucher?) {
        _redeemedVoucher.value = voucher
    }

    fun applyStoreCredit(amount: Double) {
        val maxAvailable = (_resolvedCustomer.value?.storeCredit ?: 0.0) + (_redeemedVoucher.value?.remainingBalance ?: 0.0)
        _storeCreditApplied.value = amount.coerceIn(0.0, maxAvailable)
    }

    fun removeStoreCredit() {
        _storeCreditApplied.value = 0.0
    }

    fun redeemVoucherCode(
        code: String,
        onSuccess: (StoreCreditVoucher) -> Unit,
        onError: (Int) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val voucher = repository.getStoreCreditVoucher(code)
                if (voucher == null) {
                    withContext(Dispatchers.Main) { onError(R.string.voucher_err_not_found) }
                    return@launch
                }
                if (voucher.status != "ACTIVE") {
                    withContext(Dispatchers.Main) { onError(R.string.voucher_err_inactive) }
                    return@launch
                }
                if (voucher.expiresAt != null && voucher.expiresAt < System.currentTimeMillis()) {
                    withContext(Dispatchers.Main) { onError(R.string.voucher_err_expired) }
                    return@launch
                }
                if (voucher.remainingBalance <= 0.01) {
                    withContext(Dispatchers.Main) { onError(R.string.voucher_err_zero_balance) }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    _redeemedVoucher.value = voucher
                    applyStoreCredit(voucher.remainingBalance)
                    onSuccess(voucher)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(R.string.voucher_err_failed)
                }
            }
        }
    }

    // Customer Auto-Fill, Resolution and Suggestions
    private val _resolvedCustomer = MutableStateFlow<CustomerLookupResult?>(null)
    val resolvedCustomer: StateFlow<CustomerLookupResult?> = _resolvedCustomer.asStateFlow()

    private val _isAutoFilledName = MutableStateFlow(false)
    val isAutoFilledName: StateFlow<Boolean> = _isAutoFilledName.asStateFlow()

    private val _customerSuggestions = MutableStateFlow<List<CustomerLookupResult>>(emptyList())
    val customerSuggestions: StateFlow<List<CustomerLookupResult>> = _customerSuggestions.asStateFlow()

    private var customerLookupJob: Job? = null

    fun onCustomerPhoneChanged(phone: String) {
        _customerPhone.value = phone
        val clean = phone.trim()
        customerLookupJob?.cancel()
        if (clean.isBlank()) {
            _resolvedCustomer.value = null
            _customerSuggestions.value = emptyList()
            if (isAutoFilledName.value) {
                _customerName.value = ""
                _isAutoFilledName.value = false
            }
            lookupLoyalty("")
            return
        }

        lookupLoyalty(clean)

        // Cancelling any lookup already in flight (above) keeps an old, slower-to-resolve keystroke
        // from overwriting resolvedCustomer after a newer one has already landed — otherwise the
        // "Master Customer Profile" card can freeze on a stale due/balance that no longer matches
        // what a fresh read (e.g. the credit-limit check at Complete Sale) would return.
        customerLookupJob = viewModelScope.launch(Dispatchers.IO) {
            val resolved = repository.resolveCustomerDetails(clean)
            _resolvedCustomer.value = resolved
            if (resolved != null && resolved.name.isNotBlank()) {
                if (customerName.value.isBlank() || isAutoFilledName.value) {
                    _customerName.value = resolved.name
                    _isAutoFilledName.value = true
                }
            }

            if (clean.length >= 3) {
                _customerSuggestions.value = repository.searchCustomerSuggestions(clean)
            } else {
                _customerSuggestions.value = emptyList()
            }
        }
    }

    /** Re-resolves the current phone against the DB so a due/balance shown earlier in the cart
     * session (e.g. before a payment was collected elsewhere) doesn't go stale by the time the
     * cashier reaches Complete Sale. */
    fun refreshResolvedCustomer() {
        val phone = customerPhone.value
        if (phone.isNotBlank()) {
            onCustomerPhoneChanged(phone)
        }
    }

    fun onCustomerNameChanged(name: String) {
        _customerName.value = name
        if (resolvedCustomer.value?.name != name) {
            _isAutoFilledName.value = false
        }
        val clean = name.trim()
        if (clean.length >= 2 && customerPhone.value.isBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                _customerSuggestions.value = repository.searchCustomerSuggestions(clean)
            }
        }
    }

    fun selectCustomerSuggestion(suggestion: CustomerLookupResult) {
        _customerPhone.value = suggestion.phone
        _customerName.value = suggestion.name
        _isAutoFilledName.value = true
        _resolvedCustomer.value = suggestion
        _customerSuggestions.value = emptyList()
        lookupLoyalty(suggestion.phone)
    }

    fun clearCustomerDetails() {
        _customerPhone.value = ""
        _customerName.value = ""
        _isAutoFilledName.value = false
        _resolvedCustomer.value = null
        _customerSuggestions.value = emptyList()
        lookupLoyalty("")
    }

    fun lookupLoyalty(phone: String) {
        val clean = phone.trim()
        if (clean.isBlank()) {
            _loyaltyCustomer.value = null
            _loyaltyDiscount.value = 0.0
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val customer = repository.getLoyaltyByPhone(clean)
            _loyaltyCustomer.value = customer
        }
    }

    fun applyLoyaltyDiscount(discount: Double) {
        val settings = repository.getLoyaltySettings()
        val ptCashVal = settings.pointCashValue.takeIf { it > 0.0 } ?: 1.0
        val availablePoints = loyaltyCustomer.value?.pointsBalance ?: 0
        val maxDiscountFromPoints = availablePoints * ptCashVal
        val payableBeforeLoyalty = (subtotal.value - discountValue.value + taxValue.value).coerceAtLeast(0.0)
        val maxAllowedBillDiscount = payableBeforeLoyalty * (settings.maxBillPercentPayable.coerceIn(1, 100) / 100.0)
        val maxPossibleDiscount = minOf(maxDiscountFromPoints, maxAllowedBillDiscount)
        _loyaltyDiscount.value = discount
            .takeIf(Double::isFinite)
            ?.coerceIn(0.0, maxPossibleDiscount)
            ?: 0.0
    }

    suspend fun getCustomerDue(phone: String): CustomerDue? = repository.getCustomerDueByPhone(phone)
    suspend fun getCustomerByPhone(phone: String): Customer? = repository.getCustomerByPhone(phone)

    suspend fun getCustomerCreditAccount(phone: String): CustomerDue? =
        repository.getCustomerCreditAccount(phone)

    suspend fun authorizeCreditLimitOverride(
        pin: String,
        phone: String,
        maxAmount: Double = Double.MAX_VALUE,
        cartHash: String? = null
    ): Boolean =
        repository.authorizeCreditLimitOverride(pin, phone, maxAmount, cartHash)

    /**
     * Loads a past sale's items (looked up fresh by barcode, so current stock/price applies) plus
     * its customer details into the cart, for "Edit" (void + rebuild) and "Add to Cart Again" on a
     * historical receipt. If a product was deleted (or was a Quick Sale line), an ad-hoc snapshot
     * product is reconstructed from the immutable SaleItem so void-for-edit can never lose a line.
     * Returns the number of lines that couldn't be re-added.
     */
    suspend fun loadFromPastSale(saleWithItems: SaleWithItems, replaceCart: Boolean): Int {
        if (replaceCart) clearCart()
        var skipped = 0
        for (item in saleWithItems.items) {
            val product = repository.getProductByBarcode(item.productBarcode)
            val cartProduct = product ?: Product(
                barcode = item.productBarcode.takeIf(::isSyntheticSaleBarcode)
                    ?: "ADHOC-REPLAY-${saleWithItems.sale.id}-${item.id}",
                name = item.productName,
                category = "Historical sale",
                purchasePrice = item.purchasePriceAtSale ?: fallbackPurchasePrice(item.productBarcode, item.price),
                sellingPrice = item.price,
                stockQty = item.quantity,
                reorderLevel = 0,
                unit = "Pcs",
                wholesalePrice = item.price,
                mrpPrice = item.price,
                discountedPrice = item.price
            )
            addToCart(cartProduct, item.quantity)
            if (_cartItems.value.none { it.product.barcode == cartProduct.barcode }) skipped++
        }
        val sale = saleWithItems.sale
        _customerName.value = sale.customerName ?: ""
        _customerPhone.value = sale.customerPhone ?: ""
        // Split payments aren't reconstructed (the original component breakdown isn't guaranteed
        // to still make sense against current prices/stock) — default back to Cash for Split.
        _paymentMethod.value = sale.paymentMethod.takeIf { it != "Split" } ?: "Cash"
        return skipped
    }

    suspend fun verifyPin(pin: String): Boolean = repository.verifyPin(pin) != null

    // Split Payment state
    private val _isSplitPayment = MutableStateFlow(false)
    val isSplitPayment: StateFlow<Boolean> = _isSplitPayment.asStateFlow()

    private val _splitPayments = MutableStateFlow<List<SplitPaymentEntry>>(emptyList())
    val splitPayments: StateFlow<List<SplitPaymentEntry>> = _splitPayments.asStateFlow()

    fun setSplitPayment(enabled: Boolean) {
        _isSplitPayment.value = enabled
    }

    fun setSplitPayments(list: List<SplitPaymentEntry>) {
        _splitPayments.value = list
    }

    fun addSplitPayment(entry: SplitPaymentEntry) {
        _splitPayments.value = _splitPayments.value + entry
    }

    fun removeSplitPayment(index: Int) {
        val current = _splitPayments.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _splitPayments.value = current
        }
    }

    fun getSplitPaymentsJson(): String? {
        if (!isSplitPayment.value || splitPayments.value.isEmpty()) return null
        val arr = org.json.JSONArray()
        splitPayments.value.forEach { sp ->
            val obj = org.json.JSONObject().apply {
                put("method", sp.method)
                put("amount", sp.amount)
                if (!sp.walletPhone.isNullOrBlank()) put("walletPhone", sp.walletPhone)
                if (!sp.cardType.isNullOrBlank()) put("cardType", sp.cardType)
                if (!sp.cardLastFour.isNullOrBlank()) put("cardLastFour", sp.cardLastFour)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    // Held Carts
    val allHeldCarts: StateFlow<List<HeldCart>> = repository.allHeldCarts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val heldCartCount: StateFlow<Int> = repository.heldCartCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun holdCurrentCart(title: String? = null, note: String? = null) {
        val items = _cartItems.value
        if (items.isEmpty()) return

        val curCustomerName = customerName.value.trim()
        val curCustomerPhone = customerPhone.value.trim()

        val payloadJson = encodeHeldCartPayload(
            HeldCartPayload(
                items = items,
                customerName = curCustomerName,
                customerPhone = curCustomerPhone,
                priceMode = priceMode.value,
                paymentMethod = paymentMethod.value,
                walletPhone = walletPhone.value,
                cardType = cardType.value,
                cardLastFour = cardLastFour.value,
                amountPaid = amountPaid.value,
                isSplitPayment = isSplitPayment.value,
                splitPayments = splitPayments.value,
                loyaltyPhone = loyaltyCustomer.value?.phone ?: curCustomerPhone.takeIf { it.isNotBlank() },
                loyaltyDiscount = loyaltyDiscount.value,
                storeCreditApplied = storeCreditApplied.value,
                voucherCode = redeemedVoucher.value?.code,
                voucherOriginalBalance = redeemedVoucher.value?.initialAmount,
                voucherRemainingBalance = redeemedVoucher.value?.remainingBalance,
                voucherCustomerPhone = redeemedVoucher.value?.customerPhone
            )
        )

        val effectiveTitle = if (!title.isNullOrBlank()) {
            title.trim()
        } else if (curCustomerName.isNotBlank()) {
            curCustomerName
        } else {
            "Cart #${System.currentTimeMillis() % 10000}"
        }

        val heldCart = HeldCart(
            title = effectiveTitle,
            customerName = curCustomerName.takeIf { it.isNotBlank() },
            customerPhone = curCustomerPhone.takeIf { it.isNotBlank() },
            itemsJson = payloadJson,
            discountAmount = discountAmount.value,
            isPercentageDiscount = isPercentageDiscount.value,
            timestamp = System.currentTimeMillis(),
            note = note?.takeIf { it.isNotBlank() }
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.insertHeldCart(heldCart)
            _cartItems.value = emptyList()
            _discountAmount.value = 0.0
            _isPercentageDiscount.value = false
            _customerName.value = ""
            _customerPhone.value = ""
            _walletPhone.value = ""
            _cardLastFour.value = ""
            _amountPaid.value = 0.0
            _isSplitPayment.value = false
            _splitPayments.value = emptyList()
            _priceMode.value = PriceMode.DISCOUNTED
            _loyaltyDiscount.value = 0.0
            _loyaltyCustomer.value = null
            _storeCreditApplied.value = 0.0
            _redeemedVoucher.value = null
            persistCartSnapshot(emptyList())
        }
    }

    data class RestoreResult(val restoredLines: Int, val unavailableLines: Int, val retainedHeldCart: Boolean)

    fun restoreHeldCart(heldCart: HeldCart, onResult: (RestoreResult) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = decodeHeldCartPayload(heldCart.itemsJson)
                val restored = mutableListOf<CartItem>()
                val remaining = mutableListOf<CartItem>()
                var unavailable = 0

                val stockAllocationMap = mutableMapOf<String, Double>()
                val productsByBarcode = mutableMapOf<String, Product>()

                payload.items.forEach { heldItem ->
                    val barcode = heldItem.product.barcode
                    if (isSyntheticSaleBarcode(barcode) && heldItem.product.name.isNotBlank()) {
                        productsByBarcode[barcode] = heldItem.product
                        stockAllocationMap.putIfAbsent(barcode, Double.MAX_VALUE)
                    } else if (!productsByBarcode.containsKey(barcode)) {
                        val liveProd = repository.getProductByBarcode(barcode)
                        if (liveProd != null) {
                            productsByBarcode[barcode] = liveProd
                            stockAllocationMap[barcode] = liveProd.stockQty
                        }
                    }
                }

                payload.items.forEach { heldItem ->
                    val barcode = heldItem.product.barcode
                    val product = productsByBarcode[barcode] ?: run {
                        unavailable++
                        remaining += heldItem
                        return@forEach
                    }
                    val currentAvailable = stockAllocationMap[barcode] ?: 0.0
                    val cappedQuantity = heldItem.quantity.coerceAtMost(currentAvailable)
                    if (cappedQuantity <= 0.0) {
                        unavailable++
                        remaining += heldItem
                        return@forEach
                    }
                    if (cappedQuantity < heldItem.quantity) {
                        unavailable++
                        remaining += heldItem.copy(quantity = heldItem.quantity - cappedQuantity)
                    }
                    stockAllocationMap[barcode] = (currentAvailable - cappedQuantity).coerceAtLeast(0.0)
                    restored.add(CartItem(product, cappedQuantity, heldItem.note))
                }
                _cartItems.value = restored
                _discountAmount.value = heldCart.discountAmount
                _isPercentageDiscount.value = heldCart.isPercentageDiscount
                // Authoritative customer identity: DB entity row is the single source of truth.
                // Fallback to legacy payload JSON only if the DB row column is null/blank (backwards-compatibility).
                val authoritativeCustName = heldCart.customerName?.takeIf { it.isNotBlank() }
                    ?: payload.customerName.takeIf { it.isNotBlank() } ?: ""
                val authoritativeCustPhone = heldCart.customerPhone?.takeIf { it.isNotBlank() }
                    ?: payload.customerPhone.takeIf { it.isNotBlank() } ?: ""
                _customerName.value = authoritativeCustName
                _customerPhone.value = authoritativeCustPhone
                _priceMode.value = payload.priceMode
                _paymentMethod.value = payload.paymentMethod
                _walletPhone.value = payload.walletPhone
                _cardType.value = payload.cardType
                _cardLastFour.value = payload.cardLastFour
                _amountPaid.value = payload.amountPaid
                _isSplitPayment.value = payload.isSplitPayment
                _splitPayments.value = payload.splitPayments
                val effectiveLoyaltyPhone = payload.loyaltyPhone?.takeIf { it.isNotBlank() }
                    ?: authoritativeCustPhone.takeIf { it.isNotBlank() }
                _loyaltyCustomer.value = effectiveLoyaltyPhone?.let { repository.getLoyaltyByPhone(it) }
                _loyaltyDiscount.value = payload.loyaltyDiscount.coerceAtMost(
                    _loyaltyCustomer.value?.pointsBalance?.toDouble() ?: 0.0
                )
                val restoredVoucher = payload.voucherCode?.let { repository.getStoreCreditVoucher(it) }
                val fallbackVoucher = if (restoredVoucher == null && !payload.voucherCode.isNullOrBlank() && payload.storeCreditApplied > 0.0) {
                    StoreCreditVoucher(
                        code = payload.voucherCode,
                        initialAmount = payload.voucherOriginalBalance ?: payload.storeCreditApplied,
                        remainingBalance = payload.voucherRemainingBalance ?: payload.storeCreditApplied,
                        customerPhone = payload.voucherCustomerPhone ?: authoritativeCustPhone.takeIf { it.isNotBlank() } ?: "01700000000",
                        status = "ACTIVE"
                    )
                } else null
                _redeemedVoucher.value = restoredVoucher ?: fallbackVoucher
                _storeCreditApplied.value = payload.storeCreditApplied
                persistCartSnapshot(restored)
                if (unavailable == 0) repository.deleteHeldCart(heldCart.id) else {
                    val remainingPayload = HeldCartPayload(
                        items = remaining,
                        customerName = authoritativeCustName,
                        customerPhone = authoritativeCustPhone
                    )
                    repository.insertHeldCart(heldCart.copy(
                        title = "${heldCart.title} · unavailable items",
                        customerName = authoritativeCustName.takeIf { it.isNotBlank() },
                        customerPhone = authoritativeCustPhone.takeIf { it.isNotBlank() },
                        discountAmount = 0.0,
                        isPercentageDiscount = false,
                        itemsJson = encodeHeldCartPayload(remainingPayload)
                    ))
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(RestoreResult(restored.size, unavailable, unavailable > 0))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(RestoreResult(0, 1, true)) }
            }
        }
    }

    fun deleteHeldCart(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHeldCart(id)
        }
    }

    // Calculated fields
    val subtotal: StateFlow<Double> = combine(_cartItems, priceMode) { items, mode ->
        items.sumOf { item -> effectivePrice(item.product, mode) * item.quantity }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Total MRP price of all items currently in cart (using product.mrpPrice if > 0 else sellingPrice). */
    val mrpSubtotal: StateFlow<Double> = _cartItems.map { items ->
        items.sumOf { item ->
            val baseMrp = if (item.product.mrpPrice > 0.0) item.product.mrpPrice else item.product.sellingPrice
            baseMrp * item.quantity
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /**
     * Customer savings vs MRP strictly in Sale mode (DISCOUNTED).
     * Strictly evaluates to 0.0 in MRP mode and Wholesale/Bulk mode.
     */
    val saleModeMrpSavings: StateFlow<Double> = combine(_cartItems, priceMode) { items, mode ->
        if (mode == PriceMode.DISCOUNTED) {
            items.sumOf { item ->
                val mrp = if (item.product.mrpPrice > 0.0) item.product.mrpPrice else item.product.sellingPrice
                val sale = item.product.sellingPrice
                (mrp - sale).coerceAtLeast(0.0) * item.quantity
            }
        } else {
            0.0 // Strictly 0 in MRP or Wholesale/Bulk mode!
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    /** Savings compared to the default sellingPrice (only positive when mode gives a lower price). */
    val priceModesSavings: StateFlow<Double> = combine(_cartItems, priceMode) { items, mode ->
        val retailTotal = items.sumOf { it.product.sellingPrice * it.quantity }
        val modeTotal   = items.sumOf { item -> effectivePrice(item.product, mode) * item.quantity }
        (retailTotal - modeTotal).coerceAtLeast(0.0)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Keep legacy name as alias so any other callers keep compiling
    val wholesaleSavings: StateFlow<Double> get() = priceModesSavings

    val discountValue: StateFlow<Double> = combine(subtotal, discountAmount, isPercentageDiscount) { sub, disc, isPct ->
        calculateDiscountValue(sub, disc, isPct)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val taxSettings: StateFlow<com.example.techyfipos.data.InvoiceSettingsData> = repository.invoiceSettingsDataFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.techyfipos.data.InvoiceSettingsData())

    val taxValue: StateFlow<Double> = combine(subtotal, discountValue, taxSettings) { sub, discVal, settings ->
        if (settings.taxEnabled && settings.taxRate > 0.0) {
            val taxableBase = (sub - discVal).coerceAtLeast(0.0)
            val rawTax = if (settings.taxInclusive) {
                // MRP Inclusive: Extract VAT component
                taxableBase * (settings.taxRate / (100.0 + settings.taxRate))
            } else {
                // Add-on Exclusive VAT
                taxableBase * settings.taxRate / 100.0
            }
            rawTax.roundMoney()
        } else {
            0.0
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val grandTotal: StateFlow<Double> = combine(subtotal, discountValue, taxValue, loyaltyDiscount, taxSettings) { sub, discVal, tax, loyaltyDisc, settings ->
        val base = (sub - discVal).coerceAtLeast(0.0)
        val withTax = if (settings.taxEnabled && !settings.taxInclusive) {
            base + tax
        } else {
            base
        }
        (withTax - loyaltyDisc).coerceAtLeast(0.0).roundMoney()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val changeAmount: StateFlow<Double> = combine(grandTotal, amountPaid) { total, paid ->
        (paid - total).coerceAtLeast(0.0).roundMoney()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    init {
        // Restore whatever was in the cart before the app was last closed.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            restoreCartSnapshot()
        }
        viewModelScope.launch {
            merge(
                _cartItems.map { Unit }, discountAmount.map { Unit }, isPercentageDiscount.map { Unit },
                customerName.map { Unit }, customerPhone.map { Unit }, priceMode.map { Unit },
                paymentMethod.map { Unit }, walletPhone.map { Unit }, cardType.map { Unit },
                cardLastFour.map { Unit }, amountPaid.map { Unit }, isSplitPayment.map { Unit },
                splitPayments.map { Unit }, loyaltyDiscount.map { Unit }, loyaltyCustomer.map { Unit }
            ).collectLatest {
                kotlinx.coroutines.delay(100)
                persistCartSnapshot(_cartItems.value)
            }
        }
    }

    private suspend fun restoreCartSnapshot() {
        val json = repository.getCartSnapshot()
        if (json.isBlank()) return
        try {
            val payload = decodeHeldCartPayload(json)
            val restored = mutableListOf<CartItem>()
            val stockAllocationMap = mutableMapOf<String, Double>()
            val productsByBarcode = mutableMapOf<String, Product>()

            payload.items.forEach { saved ->
                val barcode = saved.product.barcode
                if (isSyntheticSaleBarcode(barcode) && saved.product.name.isNotBlank()) {
                    productsByBarcode[barcode] = saved.product
                    stockAllocationMap.putIfAbsent(barcode, Double.MAX_VALUE)
                } else if (!productsByBarcode.containsKey(barcode)) {
                    val liveProd = repository.getProductByBarcode(barcode)
                    if (liveProd != null) {
                        productsByBarcode[barcode] = liveProd
                        stockAllocationMap[barcode] = liveProd.stockQty
                    }
                }
            }

            payload.items.forEach { saved ->
                val barcode = saved.product.barcode
                val product = productsByBarcode[barcode] ?: return@forEach
                val currentAvailable = stockAllocationMap[barcode] ?: 0.0
                val cappedQuantity = saved.quantity.coerceAtMost(currentAvailable)
                if (cappedQuantity <= 0.0) return@forEach
                stockAllocationMap[barcode] = (currentAvailable - cappedQuantity).coerceAtLeast(0.0)
                restored.add(CartItem(product, cappedQuantity, saved.note))
            }
            _cartItems.value = restored
            _customerName.value = payload.customerName
            _customerPhone.value = payload.customerPhone
            _discountAmount.value = payload.discountAmount
            _isPercentageDiscount.value = payload.isPercentageDiscount
            _priceMode.value = payload.priceMode
            _paymentMethod.value = payload.paymentMethod
            _walletPhone.value = payload.walletPhone
            _cardType.value = payload.cardType
            _cardLastFour.value = payload.cardLastFour
            _amountPaid.value = payload.amountPaid
            _isSplitPayment.value = payload.isSplitPayment
            _splitPayments.value = payload.splitPayments
            _loyaltyCustomer.value = payload.loyaltyPhone?.let { repository.getLoyaltyByPhone(it) }
            _loyaltyDiscount.value = payload.loyaltyDiscount.coerceAtMost(_loyaltyCustomer.value?.pointsBalance?.toDouble() ?: 0.0)
            val restoredVoucher = payload.voucherCode?.let { repository.getStoreCreditVoucher(it) }
            val fallbackVoucher = if (restoredVoucher == null && !payload.voucherCode.isNullOrBlank() && payload.storeCreditApplied > 0.0) {
                StoreCreditVoucher(
                    code = payload.voucherCode,
                    initialAmount = payload.voucherOriginalBalance ?: payload.storeCreditApplied,
                    remainingBalance = payload.voucherRemainingBalance ?: payload.storeCreditApplied,
                    customerPhone = payload.voucherCustomerPhone ?: payload.customerPhone.takeIf { it.isNotBlank() } ?: "01700000000",
                    status = "ACTIVE"
                )
            } else null
            _redeemedVoucher.value = restoredVoucher ?: fallbackVoucher
            _storeCreditApplied.value = payload.storeCreditApplied
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistCartSnapshot(items: List<CartItem>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            try {
                repository.saveCartSnapshot(encodeHeldCartPayload(HeldCartPayload(
                    items = items, customerName = customerName.value, customerPhone = customerPhone.value,
                    discountAmount = discountAmount.value, isPercentageDiscount = isPercentageDiscount.value,
                    priceMode = priceMode.value, paymentMethod = paymentMethod.value, walletPhone = walletPhone.value,
                    cardType = cardType.value, cardLastFour = cardLastFour.value, amountPaid = amountPaid.value,
                    isSplitPayment = isSplitPayment.value, splitPayments = splitPayments.value,
                    loyaltyPhone = loyaltyCustomer.value?.phone, loyaltyDiscount = loyaltyDiscount.value,
                    storeCreditApplied = storeCreditApplied.value, voucherCode = redeemedVoucher.value?.code,
                    voucherOriginalBalance = redeemedVoucher.value?.initialAmount,
                    voucherRemainingBalance = redeemedVoucher.value?.remainingBalance,
                    voucherCustomerPhone = redeemedVoucher.value?.customerPhone
                )))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addCustomItem(title: String, price: Double, quantity: Double = 1.0, note: String? = null, unit: String = "Pcs") {
        val cleanTitle = title.trim().ifBlank { "Custom Item" }
        val cleanPrice = price.coerceAtLeast(0.0)
        // A UUID suffix (rather than a timestamp alone) guarantees no two quick-sale items ever
        // collide on barcode, even when added within the same millisecond.
        val customBarcode = "CUSTOM-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}"
        val product = Product(
            barcode = customBarcode,
            name = cleanTitle,
            category = "Custom",
            purchasePrice = 0.0,
            sellingPrice = cleanPrice,
            stockQty = 999999.0,
            reorderLevel = 0,
            unit = unit.ifBlank { "Pcs" },
            wholesalePrice = cleanPrice
        )
        val current = _cartItems.value.toMutableList()
        current.add(CartItem(product, quantity.coerceAtLeast(0.01), note?.takeIf { it.isNotBlank() }))
        _cartItems.value = current
        persistCartSnapshot(current)
    }

    fun updateItemNote(index: Int, note: String?) {
        val current = _cartItems.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(note = note?.takeIf { it.isNotBlank() })
            _cartItems.value = current
            persistCartSnapshot(current)
        }
    }

    fun addToCart(product: Product, quantity: Double = 1.0): Boolean {
        if (!quantity.isFinite() || quantity <= 0.0) return false
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.barcode == product.barcode }
        val isSynthetic = isSyntheticSaleBarcode(product.barcode)
        val currentQuantity = currentList.getOrNull(index)?.quantity ?: 0.0
        if (!isSynthetic && currentQuantity + quantity > product.stockQty + 0.000001) return false
        val added = if (index != -1) {
            currentList[index] = currentList[index].copy(quantity = currentList[index].quantity + quantity)
            _cartItems.value = currentList
            true
        } else {
            currentList.add(CartItem(product, quantity))
            _cartItems.value = currentList
            true
        }
        if (added) {
            persistCartSnapshot(_cartItems.value)
        }
        return added
    }

    /** Looks up a hardware-scanned barcode and updates the live cart atomically. */
    suspend fun addBarcodeToCart(barcode: String): CartBarcodeResult {
        val normalizedBarcode = toEnglishDigits(barcode.trim())
        val product = repository.getProductByBarcode(normalizedBarcode)
            ?: return CartBarcodeResult(
                barcode = normalizedBarcode,
                status = CartBarcodeStatus.NOT_FOUND
            )

        val added = addToCart(product)
        val quantity = _cartItems.value
            .firstOrNull { it.product.barcode == product.barcode }
            ?.quantity ?: 0.0
        return CartBarcodeResult(
            barcode = normalizedBarcode,
            productName = product.name,
            quantityInCart = quantity,
            grandTotal = getGrandTotal(),
            status = if (added) CartBarcodeStatus.ADDED else CartBarcodeStatus.STOCK_LIMIT
        )
    }

    fun updateQuantity(product: Product, newQty: Double, allowNegative: Boolean = false) {
        if (newQty <= 0.0) {
            removeFromCart(product)
            return
        }
        val currentList = _cartItems.value.toMutableList()
        val index = currentList.indexOfFirst { it.product.barcode == product.barcode }
        if (index != -1) {
            val safeQuantity = if (isSyntheticSaleBarcode(product.barcode) || allowNegative) {
                newQty
            } else {
                newQty.coerceAtMost(product.stockQty)
            }
            if (safeQuantity <= 0.0) {
                removeFromCart(product)
                return
            }
            currentList[index] = currentList[index].copy(quantity = safeQuantity)
            _cartItems.value = currentList
            persistCartSnapshot(currentList)
        }
    }

    fun removeFromCart(product: Product) {
        val currentList = _cartItems.value.filter { it.product.barcode != product.barcode }
        _cartItems.value = currentList
        persistCartSnapshot(currentList)
    }

    fun getGrandTotal(): Double {
        val mode = priceMode.value
        val sub = _cartItems.value.sumOf { effectivePrice(it.product, mode) * it.quantity }
        val discVal = calculateDiscountValue(sub, discountAmount.value, isPercentageDiscount.value)
        val settings = taxSettings.value
        val taxVal = if (settings.taxEnabled && settings.taxRate > 0.0) {
            val base = (sub - discVal).coerceAtLeast(0.0)
            if (settings.taxInclusive) {
                base * (settings.taxRate / (100.0 + settings.taxRate))
            } else {
                base * settings.taxRate / 100.0
            }
        } else {
            0.0
        }
        val base = (sub - discVal).coerceAtLeast(0.0)
        return if (settings.taxEnabled && !settings.taxInclusive) {
            (base + taxVal).coerceAtLeast(0.0)
        } else {
            base.coerceAtLeast(0.0)
        }
    }


    fun toggleDiscountUnit() {
        _isPercentageDiscount.value = !_isPercentageDiscount.value
    }

    fun setCompletingSale(value: Boolean) {
        _isCompletingSale.value = value
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        _discountAmount.value = 0.0
        _isPercentageDiscount.value = false
        _paymentMethod.value = "Cash"
        _customerName.value = ""
        _customerPhone.value = ""
        _walletPhone.value = ""
        _cardType.value = "Visa"
        _cardLastFour.value = ""
        _manualCashierName.value = null
        _amountPaid.value = 0.0
        _isSplitPayment.value = false
        _splitPayments.value = emptyList()
        _priceMode.value = PriceMode.DISCOUNTED
        _storeCreditApplied.value = 0.0
        _redeemedVoucher.value = null
        persistCartSnapshot(emptyList())
    }

    /** Resolves the effective unit price for a product given the active price mode. */
    private fun effectivePrice(product: Product, mode: PriceMode): Double = when (mode) {
        PriceMode.MRP        -> if (product.mrpPrice > 0) product.mrpPrice else product.sellingPrice
        PriceMode.WHOLESALE  -> if (product.wholesalePrice > 0) product.wholesalePrice else product.sellingPrice
        PriceMode.DISCOUNTED -> product.sellingPrice
    }

    /** Public accessor so UI (cart rows, receipt line items) can display the same price mode-aware unit price used for the cart total. */
    fun effectivePriceFor(item: CartItem): Double = effectivePrice(item.product, priceMode.value)

    suspend fun completeSale(): Long = saleCompletionMutex.withLock {
        if (_cartItems.value.isEmpty()) {
            throw IllegalStateException("Cart is empty")
        }
        if (grandTotal.value <= 0.0) {
            throw IllegalStateException("Cannot complete sale with ৳0 total")
        }
        _isCompletingSale.value = true
        try {
            val mode = priceMode.value
            val itemsToSave = _cartItems.value.map {
                val chargedPrice = effectivePrice(it.product, mode)
                Triple(it.product.copy(sellingPrice = chargedPrice), it.quantity, it.note)
            }
            val settings = taxSettings.value
            
            var splitJson: String? = null
            var methodToSave: String
            if (isSplitPayment.value && splitPayments.value.isNotEmpty()) {
                methodToSave = "Split"
                val array = JSONArray()
                splitPayments.value.forEach { sp ->
                    array.put(JSONObject().apply {
                        put("method", sp.method)
                        put("amount", sp.amount)
                        if (!sp.walletPhone.isNullOrBlank()) put("walletPhone", sp.walletPhone)
                        if (!sp.cardType.isNullOrBlank()) put("cardType", sp.cardType)
                        if (!sp.cardLastFour.isNullOrBlank()) put("cardLastFour", sp.cardLastFour)
                        if (!sp.referenceNote.isNullOrBlank()) put("referenceNote", sp.referenceNote)
                    })
                }
                splitJson = array.toString()
            } else if (storeCreditApplied.value > 0.0 && paymentMethod.value != "Store Credit") {
                val scAmount = storeCreditApplied.value.coerceAtMost(grandTotal.value)
                val remainingAmt = (grandTotal.value - scAmount).coerceAtLeast(0.0)
                val array = JSONArray()
                array.put(JSONObject().apply {
                    put("method", "Store Credit")
                    put("amount", scAmount)
                    redeemedVoucher.value?.code?.let { put("voucherCode", it) }
                })
                if (remainingAmt > 0.0) {
                    array.put(JSONObject().apply {
                        put("method", paymentMethod.value)
                        put("amount", remainingAmt)
                        if (!walletPhone.value.isNullOrBlank()) put("walletPhone", walletPhone.value)
                        if (!cardLastFour.value.isNullOrBlank()) put("cardLastFour", cardLastFour.value)
                    })
                }
                splitJson = array.toString()
                methodToSave = "Split"
            } else {
                methodToSave = paymentMethod.value
            }

            val redeemedCustomer = loyaltyCustomer.value
            val payableBeforeLoyalty = if (settings.taxEnabled && !settings.taxInclusive) {
                (subtotal.value - discountValue.value + taxValue.value).coerceAtLeast(0.0)
            } else {
                (subtotal.value - discountValue.value).coerceAtLeast(0.0)
            }
            val appliedLoyaltyPoints = if (redeemedCustomer != null && loyaltyDiscount.value > 0.0) {
                val loyaltySettings = repository.getLoyaltySettings()
                val ptCashVal = loyaltySettings.pointCashValue.takeIf { it > 0.0 } ?: 1.0
                val neededPoints = kotlin.math.round(loyaltyDiscount.value / ptCashVal).toInt()
                neededPoints.coerceIn(0, redeemedCustomer.pointsBalance)
            } else {
                0
            }

            if (methodToSave == "Cash" && grandTotal.value > 0.0 && amountPaid.value < grandTotal.value) {
                throw IllegalStateException("Amount paid (${amountPaid.value}) cannot be less than grand total (${grandTotal.value}) for Cash payment")
            }

            val saleId = repository.completeSaleWithNotes(
                subtotal = subtotal.value,
                discountAmount = discountAmount.value,
                isPercentage = isPercentageDiscount.value,
                grandTotal = grandTotal.value,
                paymentMethod = methodToSave,
                customerName = customerName.value.takeIf { it.isNotBlank() },
                customerPhone = customerPhone.value.takeIf { it.isNotBlank() },
                amountPaid = if (methodToSave == "Split") splitPayments.value.filterNot { it.method.equals("Due", ignoreCase = true) }.sumOf { it.amount } else amountPaid.value,
                walletPhone = walletPhone.value.takeIf { it.isNotBlank() },
                cardType = cardType.value.takeIf { paymentMethod.value == "Card" },
                cardLastFour = cardLastFour.value.takeIf { it.isNotBlank() },
                cartItemsWithNotes = itemsToSave,
                taxRate = if (settings.taxEnabled) settings.taxRate else 0.0,
                taxAmount = taxValue.value,
                splitPaymentJson = splitJson,
                loyaltyPhone = redeemedCustomer?.phone,
                loyaltyPointsToRedeem = appliedLoyaltyPoints
            )

            clearCart()
            return@withLock saleId
        } finally {
            _isCompletingSale.value = false
        }
    }
}

data class AddedProductConfirmation(
    val barcode: String,
    val productName: String,
    val price: Double,
    val stockQty: Double,
    val grandTotal: Double,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ScannerViewModel(private val repository: DataRepository) : ViewModel() {
    private val _isSellMode = MutableStateFlow(true)
    val isSellMode: StateFlow<Boolean> = _isSellMode.asStateFlow()

    private val _scannedProduct = MutableStateFlow<Product?>(null)
    val scannedProduct: StateFlow<Product?> = _scannedProduct.asStateFlow()

    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    private val _addedProductConfirmation = MutableStateFlow<AddedProductConfirmation?>(null)
    val addedProductConfirmation: StateFlow<AddedProductConfirmation?> = _addedProductConfirmation.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _showManualDialog = MutableStateFlow(false)
    val showManualDialog: StateFlow<Boolean> = _showManualDialog.asStateFlow()

    fun setTorchEnabled(enabled: Boolean) {
        _torchEnabled.value = enabled
    }

    fun toggleTorch() {
        _torchEnabled.value = !_torchEnabled.value
    }

    fun setShowManualDialog(show: Boolean) {
        _showManualDialog.value = show
    }

    fun dismissAddedConfirmation() {
        _addedProductConfirmation.value = null
    }

    // The barcode currently "locked" as in-view. A locked barcode is ignored on
    // repeat detections; it only unlocks once the camera has seen empty frames
    // (the code left the frame) for MISS_FRAMES_TO_UNLOCK consecutive frames AND
    // at least MIN_MISS_DURATION_MS has passed. The time gate matters because a
    // shaky hand or motion blur can cause several consecutive "no barcode" frames
    // in well under a second while the code is still physically in view — a pure
    // frame-count debounce unlocks during that blur and double-adds on the next
    // successful read.
    private var lockedBarcode: String? = null
    private var missFrameCount: Int = 0
    private var lastDetectionTime: Long = 0
    private var lastAcceptedBarcode: String? = null
    private var lastAcceptedTime: Long = 0L

    fun setMode(isSell: Boolean) {
        _isSellMode.value = isSell
        clearScanned()
    }

    fun selectProductForStockCheck(product: Product) {
        _scannedProduct.value = product
    }

    // Called every camera frame in which no barcode was detected at all.
    fun onNoBarcodeInFrame() {
        if (lockedBarcode == null) return
        missFrameCount++
        val elapsed = System.currentTimeMillis() - lastDetectionTime
        if (missFrameCount >= MISS_FRAMES_TO_UNLOCK && elapsed >= MIN_MISS_DURATION_MS) {
            lockedBarcode = null
            missFrameCount = 0
        }
    }

    fun unlockBarcode() {
        lockedBarcode = null
        missFrameCount = 0
    }

    fun onBarcodeScanned(
        barcode: String,
        cartViewModel: CartViewModel,
        onSoundVibrate: () -> Unit,
        onShowSnackbar: (String, Double, Double, Double, Boolean) -> Unit,
        fromHardwareScanner: Boolean = false
    ) {
        val cleanBarcode = toEnglishDigits(barcode.trim())
        if (cleanBarcode.isBlank()) return
        val now = System.currentTimeMillis()
        // The camera and a HID scanner can report the same visible code almost
        // simultaneously. Treat that as one scan while still allowing deliberate,
        // repeated hardware trigger presses after the short guard window.
        if (cleanBarcode == lastAcceptedBarcode && now - lastAcceptedTime < CROSS_SOURCE_DUPLICATE_WINDOW_MS) {
            return
        }
        // Same code still in view (or re-shown before it fully left frame): ignore.
        if (!fromHardwareScanner && cleanBarcode == lockedBarcode) {
            missFrameCount = 0
            lastDetectionTime = now
            return
        }
        lockedBarcode = cleanBarcode
        missFrameCount = 0
        lastDetectionTime = now
        lastAcceptedBarcode = cleanBarcode
        lastAcceptedTime = now

        onSoundVibrate()

        viewModelScope.launch {
            val product = repository.getProductByBarcode(cleanBarcode)
            if (product != null) {
                if (_isSellMode.value) {
                    val added = cartViewModel.addToCart(product)
                    _addedProductConfirmation.value = AddedProductConfirmation(
                        barcode = product.barcode,
                        productName = product.name,
                        price = product.sellingPrice,
                        stockQty = product.stockQty,
                        grandTotal = cartViewModel.getGrandTotal(),
                        isSuccess = added
                    )
                    onShowSnackbar(product.name, product.sellingPrice, product.stockQty, cartViewModel.getGrandTotal(), added)
                } else {
                    _scannedProduct.value = product
                }
            } else {
                _scannedBarcode.value = cleanBarcode
                _scannedProduct.value = null
            }
        }
    }

    fun quickAdjustStock(product: Product, delta: Double) {
        viewModelScope.launch {
            try {
                val updated = repository.adjustProductStock(product.barcode, delta)
                if (updated != null) {
                    _scannedProduct.value = updated
                }
            } catch (e: DataRepository.UnauthorizedException) {
                android.util.Log.w("ScannerViewModel", "Stock adjustment denied: ${e.message}")
            }
        }
    }

    fun setStockQty(product: Product, newQty: Double) {
        viewModelScope.launch {
            try {
                val updated = product.copy(stockQty = newQty.coerceAtLeast(0.0))
                repository.updateProduct(updated)
                if (_scannedProduct.value != null) {
                    _scannedProduct.value = updated
                }
            } catch (e: DataRepository.UnauthorizedException) {
                android.util.Log.w("ScannerViewModel", "Stock update denied: ${e.message}")
            }
        }
    }

    fun saveStockQty(product: Product, newQty: Double) {
        viewModelScope.launch {
            try {
                val updated = product.copy(stockQty = newQty.coerceAtLeast(0.0))
                repository.updateProduct(updated)
            } catch (e: DataRepository.UnauthorizedException) {
                android.util.Log.w("ScannerViewModel", "Stock save denied: ${e.message}")
            }
        }
        clearScanned()
    }

    fun clearScanned() {
        _scannedProduct.value = null
        _scannedBarcode.value = null
        lockedBarcode = null
        missFrameCount = 0
    }

    companion object {
        // Consecutive empty-detection frames required before a barcode is
        // considered "out of sight" and eligible to scan again.
        private const val MISS_FRAMES_TO_UNLOCK = 6
        // Minimum time with no detection before unlocking, regardless of frame
        // count — guards against motion blur/hand shake producing several
        // "no barcode" frames in quick succession while still pointed at the code.
        private const val MIN_MISS_DURATION_MS = 700L
        private const val CROSS_SOURCE_DUPLICATE_WINDOW_MS = 450L
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProductsViewModel(private val repository: DataRepository) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val products: StateFlow<List<Product>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allProducts
            } else {
                repository.searchProducts(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allProducts: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAlerts: StateFlow<InventoryAlertsData> = allProducts.map { products ->
        val now = System.currentTimeMillis()
        val expired = mutableListOf<Product>()
        val expiringSoon = mutableListOf<Product>()
        val lowStock = mutableListOf<Product>()

        val in30Days = now + (30L * 24 * 60 * 60 * 1000)

        for (p in products) {
            // Check stock (out-of-stock products are reported separately, not as "low stock")
            if (p.stockQty > 0.0 && p.stockQty <= p.reorderLevel) {
                lowStock.add(p)
            }
            // Check expiry
            if (!p.expiryDate.isNullOrBlank()) {
                val expDate = parseExpiryDateToMillis(p.expiryDate)
                if (expDate != null) {
                    if (expDate <= now) {
                        expired.add(p)
                    } else if (expDate <= in30Days) {
                        expiringSoon.add(p)
                    }
                }
            }
        }
        InventoryAlertsData(
            expiredProducts = expired,
            expiringSoonProducts = expiringSoon,
            lowStockProducts = lowStock
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryAlertsData())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getProductByBarcode(barcode: String): Product? {
        return repository.getProductByBarcode(toEnglishDigits(barcode.trim()))
    }

    /** Re-tags every product carrying [oldCategory] to [newCategory] so a category-manager rename doesn't orphan existing inventory. */
    suspend fun renameCategory(oldCategory: String, newCategory: String) {
        repository.renameProductCategory(oldCategory, newCategory)
    }

    suspend fun addProduct(product: Product): Boolean {
        return try {
            val existing = repository.getProductByBarcode(product.barcode)
            if (existing != null) {
                false // Duplicate
            } else {
                repository.insertProduct(product)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateProduct(
        oldBarcode: String,
        updatedProduct: Product,
        adjustment: InventoryAdjustment? = null
    ): Result<Unit> =
        runCatching { repository.replaceProductBarcode(oldBarcode, updatedProduct, adjustment) }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    val allInventoryAdjustments: StateFlow<List<InventoryAdjustment>> = repository.allInventoryAdjustments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Standalone audit entry with no paired stock mutation (e.g. logging the initial stock a brand-new product was created with). */
    fun recordInventoryAdjustment(
        productBarcode: String,
        productName: String,
        previousStock: Double,
        newStock: Double,
        difference: Double,
        reason: String,
        note: String? = null
    ) {
        viewModelScope.launch {
            try {
                repository.recordInventoryAdjustment(
                    InventoryAdjustment(
                        productBarcode = productBarcode,
                        productName = productName,
                        previousStock = previousStock,
                        newStock = newStock,
                        difference = difference,
                        reason = reason,
                        note = note
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("ProductsViewModel", "Failed to record inventory adjustment", e)
            }
        }
    }

    /** Updates the product and writes its audit row atomically, so the two can never diverge if this coroutine is cancelled partway through. */
    fun updateProductWithAdjustment(
        updatedProduct: Product,
        previousStock: Double,
        newStock: Double,
        difference: Double,
        reason: String,
        note: String? = null
    ) {
        viewModelScope.launch {
            try {
                repository.updateProductWithInventoryAdjustment(
                    updatedProduct,
                    InventoryAdjustment(
                        productBarcode = updatedProduct.barcode,
                        productName = updatedProduct.name,
                        previousStock = previousStock,
                        newStock = newStock,
                        difference = difference,
                        reason = reason,
                        note = note
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("ProductsViewModel", "Failed to update product with inventory adjustment", e)
            }
        }
    }

    fun deleteProduct(product: Product, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repository.deleteProduct(product) }
            onResult(result)
        }
    }

    suspend fun exportProductsToExcel(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val products = repository.allProducts.first()
        return@withContext try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportProducts(products, buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importProductsFromExcel(inputStream: java.io.InputStream): com.example.techyfipos.utils.ExcelHelper.ImportResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = com.example.techyfipos.utils.ExcelHelper.importProducts(inputStream)
        if (result.importedProducts.isNotEmpty()) {
            repository.upsertProducts(result.importedProducts, result.presentFields)
        }
        result
    }

    suspend fun bulkUpdateProductPrices(
        barcodes: List<String>,
        percentDelta: Double? = null,
        fixedDelta: Double? = null
    ) {
        repository.bulkUpdateProductPrices(barcodes, percentDelta, fixedDelta)
    }

    suspend fun bulkUpdateProductCategory(
        barcodes: List<String>,
        newCategory: String
    ) {
        repository.bulkUpdateProductCategory(barcodes, newCategory)
    }

    suspend fun bulkDeleteProducts(
        barcodes: List<String>
    ) {
        repository.bulkDeleteProducts(barcodes)
    }
}

data class InventoryAlertsData(
    val expiredProducts: List<Product> = emptyList(),
    val expiringSoonProducts: List<Product> = emptyList(),
    val lowStockProducts: List<Product> = emptyList()
) {
    val totalCount: Int get() = expiredProducts.size + expiringSoonProducts.size + lowStockProducts.size
}

data class SaleReturnSummary(
    val hasReturns: Boolean = false,
    val returnCount: Int = 0,
    val totalRefundAmount: Double = 0.0,
    val returnedItemsCount: Double = 0.0,
    val latestReturnTimestamp: Long? = null,
    val performedByName: String? = null,
    val refundMethod: String? = null,
    val note: String? = null,
    val returns: List<com.example.techyfipos.data.db.Return> = emptyList(),
    val returnItems: List<com.example.techyfipos.data.db.ReturnItem> = emptyList()
)

data class InvoiceItemProfit(
    val barcode: String,
    val name: String,
    val quantity: Double,
    val unitPrice: Double,
    val unitCost: Double,
    val lineRevenue: Double,
    val lineCost: Double,
    val lineProfit: Double,
    val lineMarginPercent: Double,
    val isTopContributor: Boolean = false
)

data class InvoiceProfitSummary(
    val grossRevenue: Double,
    val discountAmount: Double,
    val netRevenue: Double,
    val grossCogs: Double,
    val returnRefundTotal: Double,
    val returnCogsReversal: Double,
    val netCogs: Double,
    val netProfit: Double,
    val netMarginPercent: Double,
    val markupPercent: Double,
    val hasReturns: Boolean,
    val itemsProfit: List<InvoiceItemProfit>
)

class SalesViewModel(private val repository: DataRepository) : ViewModel() {
    val defaultCostPercentage: StateFlow<Double> = repository.defaultCostPercentageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100.0)

    fun getFallbackCost(barcode: String, price: Double): Double =
        fallbackPurchasePrice(barcode, price, defaultCostPercentage.value)

    val salesWithItems: StateFlow<List<SaleWithItems>> = repository.allSalesWithItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _historyDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val historySalesWithItems: StateFlow<List<SaleWithItems>> = _historyDateRange.flatMapLatest { range ->
        if (range != null && range.first > 0L) {
            repository.getSalesWithItemsBetween(range.first, range.second)
        } else {
            repository.allSalesWithItems
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setHistoryDateRange(start: Long, end: Long) {
        _historyDateRange.value = Pair(start, end)
    }

    fun clearHistoryDateRange() {
        _historyDateRange.value = null
    }

    private val allReturns: StateFlow<List<com.example.techyfipos.data.db.Return>> = repository.allReturns
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val allReturnItems: StateFlow<List<com.example.techyfipos.data.db.ReturnItem>> = repository.allReturnItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allReturnsFlow: StateFlow<List<com.example.techyfipos.data.db.Return>> = allReturns
    val allReturnItemsFlow: StateFlow<List<com.example.techyfipos.data.db.ReturnItem>> = allReturnItems

    val returnsBySaleId: StateFlow<Map<Long, SaleReturnSummary>> = combine(allReturns, allReturnItems) { returns, items ->
        val returnsBySale = returns.groupBy { it.saleId }
        returnsBySale.mapValues { (saleId, saleReturns) ->
            val saleReturnIds = saleReturns.map { it.id }.toSet()
            val saleReturnItems = items.filter { it.returnId in saleReturnIds }
            val latest = saleReturns.maxByOrNull { it.timestamp }
            SaleReturnSummary(
                hasReturns = saleReturns.isNotEmpty(),
                returnCount = saleReturns.size,
                totalRefundAmount = saleReturns.sumOf { it.refundAmount },
                returnedItemsCount = saleReturnItems.sumOf { it.quantity },
                latestReturnTimestamp = latest?.timestamp,
                performedByName = latest?.performedByName,
                refundMethod = latest?.refundMethod,
                note = latest?.note,
                returns = saleReturns,
                returnItems = saleReturnItems
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun calculateInvoiceProfit(saleWithItems: SaleWithItems, returnSummary: SaleReturnSummary? = null): InvoiceProfitSummary {
        val sale = saleWithItems.sale
        val grossRevenue = sale.subtotal
        val discount = calculateDiscountValue(sale.subtotal, sale.discountAmount, sale.isPercentageDiscount)
        val netRevBeforeRefund = (grossRevenue - discount).coerceAtLeast(0.0)

        val productMap = repository.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value.associateBy { it.barcode }
        var totalGrossCogs = 0.0
        val itemsProfitList = saleWithItems.items.map { item ->
            val liveCost = productMap[item.productBarcode]?.purchasePrice
            val cost = item.purchasePriceAtSale ?: liveCost ?: getFallbackCost(item.productBarcode, item.price)
            val lineRev = item.price * item.quantity
            val lineC = cost * item.quantity
            val lineP = lineRev - lineC
            val lineMargin = if (lineRev > 0) (lineP / lineRev) * 100.0 else 0.0
            totalGrossCogs += lineC
            InvoiceItemProfit(
                barcode = item.productBarcode,
                name = item.productName,
                quantity = item.quantity,
                unitPrice = item.price,
                unitCost = cost,
                lineRevenue = lineRev,
                lineCost = lineC,
                lineProfit = lineP,
                lineMarginPercent = lineMargin
            )
        }

        val maxProfit = itemsProfitList.maxOfOrNull { it.lineProfit } ?: 0.0
        val taggedItems = itemsProfitList.map {
            if (maxProfit > 0 && it.lineProfit == maxProfit) it.copy(isTopContributor = true) else it
        }

        val refundAmount = returnSummary?.totalRefundAmount ?: 0.0
        val returnedItems = returnSummary?.returnItems ?: emptyList()
        var returnCogsReversal = 0.0
        for (retItem in returnedItems) {
            val originalSaleItem = saleWithItems.items.find { it.productBarcode == retItem.productBarcode }
            val liveCost = productMap[retItem.productBarcode]?.purchasePrice
            val unitCost = originalSaleItem?.purchasePriceAtSale ?: liveCost ?: getFallbackCost(retItem.productBarcode, retItem.price)
            returnCogsReversal += unitCost * retItem.quantity
        }

        val netRev = (netRevBeforeRefund - refundAmount).coerceAtLeast(0.0)
        val netCogs = (totalGrossCogs - returnCogsReversal).coerceAtLeast(0.0)
        val netProfit = netRev - netCogs
        val netMargin = if (netRev > 0) (netProfit / netRev) * 100.0 else 0.0
        val markup = if (netCogs > 0) (netProfit / netCogs) * 100.0 else 0.0

        return InvoiceProfitSummary(
            grossRevenue = grossRevenue,
            discountAmount = discount,
            netRevenue = netRev,
            grossCogs = totalGrossCogs,
            returnRefundTotal = refundAmount,
            returnCogsReversal = returnCogsReversal,
            netCogs = netCogs,
            netProfit = netProfit,
            netMarginPercent = netMargin,
            markupPercent = markup,
            hasReturns = returnSummary?.hasReturns == true,
            itemsProfit = taggedItems
        )
    }

    /** Current outstanding due for a customer — used to show the live "Total Due" on a reopened historical receipt, since that figure isn't stored on the Sale row itself. */
    suspend fun getCustomerDue(phone: String) = repository.getCustomerDueByPhone(phone)

    val registerShiftState: StateFlow<RegisterShiftState?> = repository.registerShiftState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val salesSummary: StateFlow<Triple<Double, Double, Int>> = combine(salesWithItems, registerShiftState) { list, shift ->
        // Use active shift start or fallback to today midnight
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayStart = shift?.startedAt?.takeIf { it > 0L } ?: calendar.timeInMillis
        val todaySalesList = list.filter { it.sale.timestamp >= todayStart }

        val totalSales = todaySalesList.sumOf { it.sale.grandTotal }
        Triple(totalSales, 0.0, todaySalesList.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0.0, 0.0, 0))

    // Real profit flow
    val todayProfit: StateFlow<Double> = combine(salesWithItems, registerShiftState) { list, shift ->
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayStart = shift?.startedAt?.takeIf { it > 0L } ?: calendar.timeInMillis
        val todaySalesList = list.filter { it.sale.timestamp >= todayStart }
        
        var profitSum = 0.0
        val missingProductBarcodes = mutableSetOf<String>()
        for (swi in todaySalesList) {
            for (item in swi.items) {
                if (item.purchasePriceAtSale == null) {
                    missingProductBarcodes.add(item.productBarcode)
                }
            }
        }
        val fallbackProducts = mutableMapOf<String, Double>()
        for (barcode in missingProductBarcodes) {
            val prod = repository.getProductByBarcode(barcode)
            if (prod != null) fallbackProducts[barcode] = prod.purchasePrice
        }
        for (swi in todaySalesList) {
            for (item in swi.items) {
                val purchase = item.purchasePriceAtSale
                    ?: fallbackProducts[item.productBarcode]
                    ?: getFallbackCost(item.productBarcode, item.price)
                profitSum += (item.price - purchase) * item.quantity
            }
        }
        profitSum
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Aggregates sales within [startMillis, endMillis) for the reports screen. */
    suspend fun buildReport(startMillis: Long, endMillis: Long, sourceList: List<SaleWithItems>? = null): SalesReportData = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val rawList = sourceList ?: salesWithItems.value
        val list = rawList.filter { it.sale.timestamp in startMillis until endMillis }
        val totalExpenses = repository.getExpensesBetween(startMillis, endMillis).first()
            .filter { it.type == "EXPENSE" && it.category != "Supplier Payment" }
            .sumOf { it.amount }
        val totalRevenue = list.sumOf { it.sale.grandTotal }
        val totalDiscount = list.sumOf {
            calculateDiscountValue(it.sale.subtotal, it.sale.discountAmount, it.sale.isPercentageDiscount)
        }
        val totalTax = list.sumOf { it.sale.taxAmount }

        // Reports are sale-cohort based: every return belonging to a sale in this range is applied,
        // even if the return happened later. This keeps the headline and every breakdown on the
        // same basis and prevents a return for an older invoice making today's revenue negative.
        val salesByIdAll = salesWithItems.value.associateBy { it.sale.id }
        val salesByIdInPeriod = list.associateBy { it.sale.id }
        val returnsInPeriod = allReturns.value.filter { it.saleId in salesByIdInPeriod }
        val totalRefunds = returnsInPeriod.sumOf { it.refundAmount }

        // Returns must reduce every breakdown they touched originally (product rankings, payment
        // mix, item counts, tax, daily/hourly), not just the headline revenue/profit figures —
        // otherwise a heavily-returned product still looks like a best-seller and the sub-totals
        // stop adding up to the headline numbers. Each return is attributed back to the day/hour/
        // payment-method bucket of the ORIGINAL sale (where the revenue was first counted), not the
        // day the return happened.
        var refundedProfit = 0.0
        var refundedTax = 0.0
        val returnedQtyByBarcode = mutableMapOf<String, Double>()
        val returnedRevenueByBarcode = mutableMapOf<String, Double>()
        val returnedAmountByPaymentMethod = mutableMapOf<String, Double>()
        val returnedByDay = mutableMapOf<Long, Pair<Double, Double>>() // dayStart -> (revenue, profit)
        val returnedByHour = mutableMapOf<Int, Triple<Double, Double, Double>>() // hour -> (revenue, profit, itemsSold)
        for (ret in returnsInPeriod) {
            val originalSaleAll = salesByIdAll[ret.saleId]
            val originalSaleItems = originalSaleAll?.items.orEmpty()
            val itemsForThisReturn = allReturnItems.value.filter { it.returnId == ret.id }
            var returnedCost = 0.0
            for (item in itemsForThisReturn) {
                val matchingSaleItem = originalSaleItems.firstOrNull { it.productBarcode == item.productBarcode }
                val purchase = matchingSaleItem?.purchasePriceAtSale
                    ?: repository.getProductByBarcode(item.productBarcode)?.purchasePrice
                    ?: getFallbackCost(item.productBarcode, item.price)
                returnedCost += purchase * item.quantity
            }
            // Profit reversed is the net revenue actually reversed minus the returned inventory's
            // cost. This correctly accounts for sale-level discounts and tax.
            val retTaxPortion = if (originalSaleAll != null && originalSaleAll.sale.grandTotal > 0.0) {
                ret.refundAmount / originalSaleAll.sale.grandTotal * originalSaleAll.sale.taxAmount
            } else 0.0
            val thisReturnProfit = (ret.refundAmount - retTaxPortion) - returnedCost
            refundedProfit += thisReturnProfit

            // Only adjust the period's own breakdowns when the original sale is itself part of
            // this period (and thus already contributing to these same maps) — otherwise there is
            // no existing bucket to subtract from.
            val originalSaleInPeriod = salesByIdInPeriod[ret.saleId] ?: continue
            val rawReturnedValue = itemsForThisReturn.sumOf { it.price * it.quantity }
            for (item in itemsForThisReturn) {
                val rawItemValue = item.price * item.quantity
                val itemRevenue = if (rawReturnedValue > 0.0) {
                    ret.refundAmount * rawItemValue / rawReturnedValue
                } else 0.0
                returnedQtyByBarcode[item.productBarcode] = (returnedQtyByBarcode[item.productBarcode] ?: 0.0) + item.quantity
                returnedRevenueByBarcode[item.productBarcode] = (returnedRevenueByBarcode[item.productBarcode] ?: 0.0) + itemRevenue
            }
            val paymentKey = originalSaleInPeriod.sale.paymentMethod.ifBlank { "Other" }
            returnedAmountByPaymentMethod[paymentKey] = (returnedAmountByPaymentMethod[paymentKey] ?: 0.0) + ret.refundAmount
            if (originalSaleInPeriod.sale.grandTotal > 0.0) {
                refundedTax += ret.refundAmount / originalSaleInPeriod.sale.grandTotal * originalSaleInPeriod.sale.taxAmount
            }
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = originalSaleInPeriod.sale.timestamp }
            val dayStart = (cal.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val returnedQtyThisReturn = itemsForThisReturn.sumOf { it.quantity }
            val existingDay = returnedByDay[dayStart] ?: (0.0 to 0.0)
            returnedByDay[dayStart] = (existingDay.first + ret.refundAmount) to (existingDay.second + thisReturnProfit)
            val existingHour = returnedByHour[hour] ?: Triple(0.0, 0.0, 0.0)
            returnedByHour[hour] = Triple(
                existingHour.first + ret.refundAmount,
                existingHour.second + thisReturnProfit,
                existingHour.third + returnedQtyThisReturn
            )
        }
        val totalReturnedQty = returnedQtyByBarcode.values.sum()

        var profitSum = 0.0
        val productTotals = linkedMapOf<String, ProductReportData>()
        val paymentTotals = linkedMapOf<String, PaymentReportData>()
        val dailyTotals = sortedMapOf<Long, DailyReportData>()
        val hourlyTotals = (0..23).associateWith { hour ->
            val hourLabel = when (hour) {
                0 -> "12 AM"
                in 1..11 -> "$hour AM"
                12 -> "12 PM"
                else -> "${hour - 12} PM"
            }
            val startHourStr = when (hour) {
                0 -> "12:00 AM"
                in 1..9 -> "0$hour:00 AM"
                in 10..11 -> "$hour:00 AM"
                12 -> "12:00 PM"
                in 13..21 -> "0${hour - 12}:00 PM"
                else -> "${hour - 12}:00 PM"
            }
            val nextHour = (hour + 1) % 24
            val endHourStr = when (nextHour) {
                0 -> "12:00 AM"
                in 1..9 -> "0$nextHour:00 AM"
                in 10..11 -> "$nextHour:00 AM"
                12 -> "12:00 PM"
                in 13..21 -> "0${nextHour - 12}:00 PM"
                else -> "${nextHour - 12}:00 PM"
            }
            HourlyReportData(
                hour = hour,
                hourLabel = hourLabel,
                timeSlotLabel = "$startHourStr – $endHourStr",
                revenue = 0.0,
                profit = 0.0,
                transactionCount = 0,
                itemsSold = 0.0
            )
        }.toMutableMap()
        val productCosts = mutableMapOf<String, Double>()
        var itemsSold = 0.0

        for (swi in list) {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = swi.sale.timestamp
            }
            val dayStart = (cal.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            var saleProfit = 0.0
            var saleItemsQty = 0.0
            val isTaxInclusive = swi.sale.taxAmount > 0.0 && swi.sale.taxRate > 0.0 && kotlin.math.abs(swi.sale.subtotal - swi.sale.grandTotal) < 0.01
            for (item in swi.items) {
                // The cost recorded at the moment of this exact sale, when available, so editing a
                // product's current purchase price (or deleting it) later never changes what a past
                // report shows. Older rows written before this field existed fall back to a live
                // lookup / estimate, same as before.
                val purchase = item.purchasePriceAtSale ?: productCosts.getOrPut(item.productBarcode) {
                    repository.getProductByBarcode(item.productBarcode)?.purchasePrice
                        ?: getFallbackCost(item.productBarcode, item.price)
                }
                val effectivePrice = if (isTaxInclusive) item.price / (1.0 + swi.sale.taxRate / 100.0) else item.price
                val itemProfit = (effectivePrice - purchase) * item.quantity
                val itemRevenue = item.price * item.quantity
                saleProfit += itemProfit
                saleItemsQty += item.quantity
                itemsSold += item.quantity

                val existingProduct = productTotals[item.productBarcode]
                productTotals[item.productBarcode] = ProductReportData(
                    barcode = item.productBarcode,
                    name = item.productName,
                    quantity = (existingProduct?.quantity ?: 0.0) + item.quantity,
                    revenue = (existingProduct?.revenue ?: 0.0) + itemRevenue
                )
            }
            profitSum += saleProfit

            val paymentKey = swi.sale.paymentMethod.ifBlank { "Other" }
            val existingPayment = paymentTotals[paymentKey]
            paymentTotals[paymentKey] = PaymentReportData(
                method = paymentKey,
                transactionCount = (existingPayment?.transactionCount ?: 0) + 1,
                amount = (existingPayment?.amount ?: 0.0) + swi.sale.grandTotal
            )

            val existingDay = dailyTotals[dayStart]
            dailyTotals[dayStart] = DailyReportData(
                timestamp = dayStart,
                revenue = (existingDay?.revenue ?: 0.0) + swi.sale.grandTotal,
                profit = (existingDay?.profit ?: 0.0) + saleProfit,
                transactionCount = (existingDay?.transactionCount ?: 0) + 1
            )

            val existingHour = hourlyTotals[hour]
            if (existingHour != null) {
                hourlyTotals[hour] = existingHour.copy(
                    revenue = existingHour.revenue + swi.sale.grandTotal,
                    profit = existingHour.profit + saleProfit,
                    transactionCount = existingHour.transactionCount + 1,
                    itemsSold = existingHour.itemsSold + saleItemsQty
                )
            }
        }

        // Apply the return adjustments computed above to every breakdown they touched originally.
        returnedQtyByBarcode.forEach { (barcode, qty) ->
            val existing = productTotals[barcode] ?: return@forEach
            productTotals[barcode] = existing.copy(
                quantity = (existing.quantity - qty).coerceAtLeast(0.0),
                revenue = (existing.revenue - (returnedRevenueByBarcode[barcode] ?: 0.0)).coerceAtLeast(0.0)
            )
        }
        returnedAmountByPaymentMethod.forEach { (method, amount) ->
            val existing = paymentTotals[method] ?: return@forEach
            paymentTotals[method] = existing.copy(amount = (existing.amount - amount).coerceAtLeast(0.0))
        }
        returnedByDay.forEach { (day, revenueProfit) ->
            val existing = dailyTotals[day] ?: return@forEach
            dailyTotals[day] = existing.copy(
                revenue = (existing.revenue - revenueProfit.first).coerceAtLeast(0.0),
                profit = existing.profit - revenueProfit.second
            )
        }
        returnedByHour.forEach { (hour, adjustment) ->
            val existing = hourlyTotals[hour] ?: return@forEach
            hourlyTotals[hour] = existing.copy(
                revenue = (existing.revenue - adjustment.first).coerceAtLeast(0.0),
                profit = existing.profit - adjustment.second,
                itemsSold = (existing.itemsSold - adjustment.third).coerceAtLeast(0.0)
            )
        }

        val totalLoyaltyDiscount = list.sumOf { it.sale.loyaltyPointsRedeemed.toDouble() }
        val grossMargin = profitSum - refundedProfit
        SalesReportData(
            transactionCount = list.size,
            totalRevenue = totalRevenue - totalRefunds,
            totalDiscount = totalDiscount,
            totalTax = (totalTax - refundedTax).coerceAtLeast(0.0),
            totalProfit = grossMargin,
            totalExpenses = totalExpenses,
            totalLoyaltyDiscount = totalLoyaltyDiscount,
            netProfit = grossMargin - totalDiscount - totalLoyaltyDiscount - totalExpenses,
            totalRefunds = totalRefunds,
            itemsSold = (itemsSold - totalReturnedQty).coerceAtLeast(0.0),
            topProducts = productTotals.values.sortedByDescending { it.revenue },
            paymentBreakdown = paymentTotals.values.sortedByDescending { it.amount },
            dailyBreakdown = dailyTotals.values.toList(),
            hourlyBreakdown = hourlyTotals.values.toList()
        )
    }

    fun deleteSale(saleId: Long, reason: String = "Sale Cancelled/Deleted") {
        viewModelScope.launch {
            try {
                repository.deleteSale(saleId, reason)
            } catch (error: Exception) {
                android.util.Log.e("SalesViewModel", "Unable to delete sale", error)
            }
        }
    }

    /** Suspend variant so a caller (e.g. "Edit sale") can wait for the void to finish before rebuilding the cart. Returns true on success. */
    suspend fun deleteSaleAndAwait(saleId: Long, reason: String = "Sale Cancelled/Deleted"): Boolean = try {
        repository.deleteSale(saleId, reason)
        true
    } catch (error: Exception) {
        android.util.Log.e("SalesViewModel", "Unable to delete sale", error)
        false
    }

    suspend fun getReturnedQuantities(saleId: Long): Map<String, Double> {
        return repository.getReturnedQuantities(saleId)
    }

    suspend fun getVoucherByCode(code: String): StoreCreditVoucher? = repository.getStoreCreditVoucher(code)
    suspend fun getVoucherBySaleId(saleId: Long): StoreCreditVoucher? = repository.getStoreCreditVoucherBySaleId(saleId)
    suspend fun getVoucherByReturnId(returnId: Long): StoreCreditVoucher? = repository.getStoreCreditVoucherByReturnId(returnId)

    fun processReturn(
        saleId: Long,
        lines: List<ReturnLine>,
        refundMethod: String,
        note: String? = null,
        restock: Boolean = true,
        walkinPhone: String? = null,
        walkinName: String? = null,
        issueVoucher: Boolean = false,
        onComplete: (Boolean, Double, String?, StoreCreditVoucher?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = repository.processReturn(
                    saleId, lines, refundMethod, note, restock, walkinPhone, walkinName, issueVoucher
                )
                onComplete(true, result.refundTotal, result.voucherCode, result.voucher)
            } catch (error: Exception) {
                android.util.Log.e("SalesViewModel", "Unable to process return", error)
                onComplete(false, 0.0, null, null)
            }
        }
    }

    suspend fun exportSalesReportToExcel(
        report: SalesReportData,
        dateRangeLabel: String,
        outputStream: java.io.OutputStream
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportSalesReport(report, dateRangeLabel, buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            android.util.Log.e("SalesViewModel", "Failed to export sales report", e)
            false
        }
    }
}

data class SalesReportData(
    val transactionCount: Int,
    val totalRevenue: Double,
    val totalDiscount: Double,
    val totalTax: Double = 0.0,
    val totalProfit: Double,
    val totalExpenses: Double = 0.0,
    val totalLoyaltyDiscount: Double = 0.0,
    val netProfit: Double = totalProfit,
    val totalRefunds: Double = 0.0,
    val itemsSold: Double = 0.0,
    val topProducts: List<ProductReportData> = emptyList(),
    val paymentBreakdown: List<PaymentReportData> = emptyList(),
    val dailyBreakdown: List<DailyReportData> = emptyList(),
    val hourlyBreakdown: List<HourlyReportData> = emptyList()
)

data class HourlyReportData(
    val hour: Int,
    val hourLabel: String,
    val timeSlotLabel: String,
    val revenue: Double,
    val profit: Double,
    val transactionCount: Int,
    val itemsSold: Double
)

data class ProductReportData(
    val barcode: String,
    val name: String,
    val quantity: Double,
    val revenue: Double
)

data class PaymentReportData(
    val method: String,
    val transactionCount: Int,
    val amount: Double
)

data class DailyReportData(
    val timestamp: Long,
    val revenue: Double,
    val profit: Double,
    val transactionCount: Int
)

enum class DuesAuditType {
    REPAYMENT,
    CREDIT_SALE,
    RETURN_ADJUSTMENT,
    MANUAL_DUE
}

data class DuesAuditEntry(
    val id: String,
    val type: DuesAuditType,
    val customerPhone: String,
    val customerName: String,
    val amount: Double,
    val timestamp: Long,
    val channelOrMethod: String,
    val note: String? = null,
    val referenceId: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DuesViewModel(private val repository: DataRepository) : ViewModel() {
    val duesList: StateFlow<List<CustomerDue>> = repository.allOutstandingDues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalOutstandingDue: StateFlow<Double> = duesList
        .map { list -> list.sumOf { it.outstandingAmount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val allDuesList: StateFlow<List<CustomerDue>> = repository.allDues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRepaidAmount: StateFlow<Double> = repository.allDuePayments
        .map { list -> list.sumOf { it.amountPaid } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val allDuePayments: StateFlow<List<DuePayment>> = repository.allDuePayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allScheduledReminders: StateFlow<List<ScheduledDueReminder>> = repository.allScheduledDueReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDueReminderLogs: StateFlow<List<com.example.techyfipos.data.db.DueReminderLog>> = repository.allDueReminderLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logReminder(
        customerPhone: String,
        customerName: String,
        dueAmount: Double,
        channel: String,
        messageText: String = "",
        status: String = "SENT"
    ) {
        viewModelScope.launch {
            repository.logDueReminder(
                com.example.techyfipos.data.db.DueReminderLog(
                    customerPhone = customerPhone,
                    customerName = customerName,
                    dueAmount = dueAmount,
                    channel = channel,
                    messageText = messageText,
                    timestamp = System.currentTimeMillis(),
                    status = status
                )
            )
        }
    }

    fun deleteReminderLog(id: Long) {
        viewModelScope.launch {
            repository.deleteDueReminderLog(id)
        }
    }

    fun clearAllReminderLogs() {
        viewModelScope.launch {
            repository.clearDueReminderLogs()
        }
    }

    val allDuesAuditEntries: StateFlow<List<DuesAuditEntry>> = kotlinx.coroutines.flow.combine(
        repository.allDuePayments,
        repository.allSales,
        repository.allReturns,
        repository.allDues,
        repository.allDueCharges
    ) { payments, sales, returns, dues, charges ->
        val customerNameMap = dues.associate { it.customerPhone to it.customerName }
        val list = mutableListOf<DuesAuditEntry>()

        // 1. Repayments
        payments.forEach { p ->
            val name = customerNameMap[p.customerPhone] ?: p.customerPhone
            list.add(
                DuesAuditEntry(
                    id = "payment_${p.id}",
                    type = DuesAuditType.REPAYMENT,
                    customerPhone = p.customerPhone,
                    customerName = name,
                    amount = p.amountPaid,
                    timestamp = p.timestamp,
                    channelOrMethod = p.paymentMethod,
                    note = p.referenceNote,
                    referenceId = "#PAY-${p.id}"
                )
            )
        }

        // 2. Credit Sales (where paymentMethod is Due or grandTotal > amountPaid)
        sales.filter { s ->
            val due = (s.grandTotal - s.amountPaid).coerceAtLeast(0.0)
            s.paymentMethod.equals("Due", ignoreCase = true) || due > 0.001
        }.forEach { s ->
            val due = if (s.paymentMethod.equals("Due", ignoreCase = true)) s.grandTotal else (s.grandTotal - s.amountPaid).coerceAtLeast(0.0)
            val phone = s.customerPhone ?: ""
            val name = s.customerName ?: (customerNameMap[phone] ?: phone)
            list.add(
                DuesAuditEntry(
                    id = "sale_${s.id}",
                    type = DuesAuditType.CREDIT_SALE,
                    customerPhone = phone,
                    customerName = name,
                    amount = due,
                    timestamp = s.timestamp,
                    channelOrMethod = s.paymentMethod,
                    note = "Invoice #INV-${s.id}",
                    referenceId = "#INV-${s.id}"
                )
            )
        }

        // 3. Return Due Adjustments (dueAdjustmentAmount > 0)
        returns.filter { it.dueAdjustmentAmount > 0.0 }.forEach { r ->
            val linkedSale = sales.firstOrNull { it.id == r.saleId }
            val phone = linkedSale?.customerPhone ?: ""
            val name = linkedSale?.customerName ?: (customerNameMap[phone] ?: phone)
            list.add(
                DuesAuditEntry(
                    id = "return_${r.id}",
                    type = DuesAuditType.RETURN_ADJUSTMENT,
                    customerPhone = phone,
                    customerName = name,
                    amount = r.dueAdjustmentAmount,
                    timestamp = r.timestamp,
                    channelOrMethod = r.refundMethod,
                    note = r.note ?: "Settled against due",
                    referenceId = if (linkedSale != null) "#INV-${linkedSale.id}" else "#RET-${r.id}"
                )
            )
        }

        // 4. Manual Due Charges
        charges.forEach { c ->
            val name = customerNameMap[c.customerPhone] ?: c.customerPhone
            list.add(
                DuesAuditEntry(
                    id = "charge_${c.id}",
                    type = DuesAuditType.MANUAL_DUE,
                    customerPhone = c.customerPhone,
                    customerName = name,
                    amount = c.amount,
                    timestamp = c.timestamp,
                    channelOrMethod = c.reason,
                    note = c.note ?: "Manual Due Added",
                    referenceId = "#CHG-${c.id}"
                )
            )
        }

        list.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerDebtOriginTimestamps: StateFlow<Map<String, Long>> = kotlinx.coroutines.flow.combine(
        repository.allDues,
        repository.allSales,
        repository.allDueCharges,
        repository.allDuePayments
    ) { dues, sales, charges, payments ->
        val paymentsByPhone = payments.groupBy { it.customerPhone }
            .mapValues { (_, pList) -> pList.sumOf { it.amountPaid } }

        val debtsByPhone = mutableMapOf<String, MutableList<Pair<Long, Double>>>()

        sales.filter { s ->
            val due = (s.grandTotal - s.amountPaid).coerceAtLeast(0.0)
            s.paymentMethod.equals("Due", ignoreCase = true) || due > 0.001
        }.forEach { s ->
            val due = if (s.paymentMethod.equals("Due", ignoreCase = true)) s.grandTotal else (s.grandTotal - s.amountPaid).coerceAtLeast(0.0)
            val phone = s.customerPhone
            if (!phone.isNullOrBlank() && due > 0.0) {
                debtsByPhone.getOrPut(phone) { mutableListOf() }.add(Pair(s.timestamp, due))
            }
        }

        charges.filter { it.amount > 0.0 }.forEach { c ->
            debtsByPhone.getOrPut(c.customerPhone) { mutableListOf() }.add(Pair(c.timestamp, c.amount))
        }

        val resultMap = mutableMapOf<String, Long>()
        dues.filter { it.outstandingAmount > 0.0 }.forEach { due ->
            val phone = due.customerPhone
            var remainingPayment = paymentsByPhone[phone] ?: 0.0
            val debtList = debtsByPhone[phone]?.sortedBy { it.first } ?: emptyList()

            var oldestUnpaidTimestamp: Long? = null
            for ((ts, debtAmt) in debtList) {
                if (remainingPayment >= debtAmt) {
                    remainingPayment -= debtAmt
                } else {
                    oldestUnpaidTimestamp = ts
                    break
                }
            }
            resultMap[phone] = oldestUnpaidTimestamp ?: due.updatedAt
        }
        resultMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun cancelScheduledReminder(context: Context, phone: String) {
        viewModelScope.launch {
            repository.cancelScheduledDueReminder(context, phone)
        }
    }

    private val _selectedCustomer = MutableStateFlow<CustomerDue?>(null)
    val selectedCustomer: StateFlow<CustomerDue?> = _selectedCustomer.asStateFlow()

    val selectedCustomerPayments: StateFlow<List<DuePayment>> = _selectedCustomer
        .flatMapLatest { customer ->
            if (customer == null) flowOf(emptyList())
            else repository.getPaymentsForCustomer(customer.customerPhone)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedCustomerCharges: StateFlow<List<com.example.techyfipos.data.db.DueCharge>> = _selectedCustomer
        .flatMapLatest { customer ->
            if (customer == null) flowOf(emptyList())
            else repository.getChargesForCustomer(customer.customerPhone)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCustomer(customer: CustomerDue?) {
        _selectedCustomer.value = customer
    }

    fun recordPayment(
        phone: String,
        amount: Double,
        paymentMethod: String = "Cash",
        referenceNote: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repository.recordDuePayment(phone, amount, paymentMethod, referenceNote)
                val fresh = repository.getCustomerDueByPhone(phone)
                if (fresh != null) {
                    _selectedCustomer.value = fresh
                } else {
                    val current = _selectedCustomer.value
                    if (current != null && current.customerPhone == phone) {
                        val updatedDue = (current.outstandingAmount - amount).coerceAtLeast(0.0)
                        _selectedCustomer.value = current.copy(outstandingAmount = updatedDue)
                    }
                }
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("DuesViewModel", "Unable to record due payment", error)
                onComplete(false)
            }
        }
    }

    fun updateCustomerCreditLimit(phone: String, creditLimit: Double, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateCustomerCreditLimit(phone, creditLimit)
                val fresh = repository.getCustomerDueByPhone(phone)
                if (fresh != null) {
                    _selectedCustomer.value = fresh
                }
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("DuesViewModel", "Unable to update credit limit", error)
                onComplete(false)
            }
        }
    }

    fun addOrUpdateDue(
        name: String,
        phone: String,
        amount: Double,
        creditLimit: Double = 0.0,
        note: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val cleanPhone = phone.trim()
                val cleanName = name.trim()
                if (amount > 0.0) {
                    // Recorded as a charge (debit) so it shows up in Transaction History, not just
                    // as an invisible bump to the aggregate balance.
                    repository.addManualDueCharge(cleanPhone, cleanName, amount, creditLimit.takeIf { it > 0.0 }, note)
                } else {
                    val existing = repository.getCustomerDueByPhone(cleanPhone)
                    val updated = existing?.copy(
                        customerName = if (cleanName.isNotBlank()) cleanName else existing.customerName,
                        creditLimit = if (creditLimit > 0) creditLimit else existing.creditLimit,
                        updatedAt = System.currentTimeMillis()
                    ) ?: com.example.techyfipos.data.db.CustomerDue(
                        customerName = cleanName,
                        customerPhone = cleanPhone,
                        outstandingAmount = 0.0,
                        creditLimit = creditLimit.coerceAtLeast(0.0),
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.upsertCustomerDue(updated)
                }
                val fresh = repository.getCustomerDueByPhone(cleanPhone)
                if (fresh != null && _selectedCustomer.value?.customerPhone == fresh.customerPhone) {
                    _selectedCustomer.value = fresh
                }
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("DuesViewModel", "Unable to add/update customer due", error)
                onComplete(false)
            }
        }
    }

    fun migrateCustomerDuePhone(
        oldPhone: String,
        newPhone: String,
        newName: String,
        creditLimit: Double,
        note: String?,
        onSuccess: (newPhone: String) -> Unit,
        onCollision: (existingCustomer: com.example.techyfipos.data.db.Customer) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                when (val res = repository.migrateCustomerPhone(oldPhone, newPhone, newName, creditLimit, note)) {
                    is com.example.techyfipos.data.CustomerPhoneUpdateResult.Success -> {
                        val fresh = repository.getCustomerDueByPhone(res.finalPhone)
                        if (fresh != null) {
                            _selectedCustomer.value = fresh
                        }
                        onSuccess(res.finalPhone)
                    }
                    is com.example.techyfipos.data.CustomerPhoneUpdateResult.Collision -> {
                        onCollision(res.existingCustomer)
                    }
                    is com.example.techyfipos.data.CustomerPhoneUpdateResult.Error -> {
                        onError(res.message)
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update phone number")
            }
        }
    }

    fun mergeCustomers(masterPhone: String, secondaryPhone: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val ok = repository.mergeCustomers(masterPhone, secondaryPhone)
                if (ok) {
                    val fresh = repository.getCustomerDueByPhone(masterPhone)
                    if (fresh != null) {
                        _selectedCustomer.value = fresh
                    }
                    onSuccess()
                } else {
                    onError("Could not merge customer accounts")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Merge failed")
            }
        }
    }

    /** Reverses a mistakenly recorded collection — restores the amount to the outstanding balance
     * and removes it from Transaction History. */
    fun deletePayment(phone: String, paymentId: Long, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteDuePayment(paymentId, phone)
                val fresh = repository.getCustomerDueByPhone(phone)
                if (fresh != null) {
                    _selectedCustomer.value = fresh
                }
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("DuesViewModel", "Unable to delete due payment", error)
                onComplete(false)
            }
        }
    }

    fun deleteCharge(phone: String, chargeId: Long, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.deleteDueCharge(chargeId, phone)
                val fresh = repository.getCustomerDueByPhone(phone)
                if (fresh != null) {
                    _selectedCustomer.value = fresh
                }
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("DuesViewModel", "Unable to delete due charge", error)
                onComplete(false)
            }
        }
    }

    fun deleteCustomerDue(phone: String) {
        viewModelScope.launch {
            repository.deleteCustomerDue(phone)
            if (_selectedCustomer.value?.customerPhone == phone) {
                _selectedCustomer.value = null
            }
        }
    }

    suspend fun exportDuesToExcel(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val dues = repository.allDues.first()
            val payments = repository.allDuePayments.first()
            val reminders = repository.allScheduledDueReminders.first()
            val sales = repository.allSales.first()
            val returns = repository.allReturns.first()
            val charges = repository.allDueCharges.first()
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportComprehensiveDues(
                duesList = dues,
                paymentsList = payments,
                remindersList = reminders,
                salesList = sales,
                returnsList = returns,
                chargesList = charges,
                outputStream = buffer
            )
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportCustomerStatementToExcel(customer: CustomerDue, outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val payments = repository.getPaymentsForCustomer(customer.customerPhone).first()
            val charges = repository.getDueChargesForCustomer(customer.customerPhone).first()
            val sales = repository.allSales.first()
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportCustomerStatementExcel(
                customer = customer,
                paymentsList = payments,
                salesList = sales,
                dueChargesList = charges,
                outputStream = buffer
            )
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

class SettingsViewModel(private val repository: DataRepository) : ViewModel() {
    val language: StateFlow<String> = repository.languageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val themeMode: StateFlow<String> = repository.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val uiLayoutMode: StateFlow<String> = repository.uiLayoutModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")

    val defaultCostPercentage: StateFlow<Double> = repository.defaultCostPercentageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100.0)

    val shopName: StateFlow<String> = repository.shopNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "My Shop")

    val shopAddress: StateFlow<String> = repository.shopAddressFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val shopPhone: StateFlow<String> = repository.shopPhoneFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val shopLogoUri: StateFlow<String> = repository.shopLogoUriFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val facebookUrl: StateFlow<String> = repository.facebookUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val qrCodeInfo: StateFlow<String> = repository.qrCodeInfoFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val cashierName: StateFlow<String> = repository.cashierNameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Admin")

    val invoiceSettingsData: StateFlow<com.example.techyfipos.data.InvoiceSettingsData> = repository.invoiceSettingsDataFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.techyfipos.data.InvoiceSettingsData())

    val quickSalePresets: StateFlow<List<com.example.techyfipos.data.QuickSalePreset>> = repository.quickSalePresetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.techyfipos.data.QuickSalePreset.defaultPresets())

    val dueReminderTemplates: StateFlow<List<com.example.techyfipos.data.DueTemplate>> = repository.dueReminderTemplatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.techyfipos.data.DueTemplate.defaultTemplates())

    val autoBackup: StateFlow<Boolean> = repository.autoBackupFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val localAutoBackup: StateFlow<Boolean> = repository.localAutoBackupFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoBackupFrequency: StateFlow<String> = repository.autoBackupFrequencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DAILY")

    val driveAutoBackupFrequency: StateFlow<String> = repository.driveAutoBackupFrequencyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DAILY")

    val lastLocalBackupTimestamp: StateFlow<Long> = repository.lastLocalBackupTimestampFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lastDriveBackupTimestamp: StateFlow<Long> = repository.lastDriveBackupTimestampFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val driveRetentionCount: StateFlow<Int> = repository.driveRetentionCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val driveNetworkPolicy: StateFlow<String> = repository.driveNetworkPolicyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "WIFI_ONLY")

    val driveBackupTime: StateFlow<String> = repository.driveBackupTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "02:00")

    private val _driveBackups = MutableStateFlow<List<com.example.techyfipos.utils.GoogleDriveHelper.DriveBackupFile>>(emptyList())
    val driveBackups: StateFlow<List<com.example.techyfipos.utils.GoogleDriveHelper.DriveBackupFile>> = _driveBackups.asStateFlow()

    private val _isLoadingBackups = MutableStateFlow(false)
    val isLoadingBackups: StateFlow<Boolean> = _isLoadingBackups.asStateFlow()

    fun saveLanguage(lang: String) {
        viewModelScope.launch {
            repository.saveLanguage(lang)
        }
    }

    fun saveThemeMode(mode: String) {
        viewModelScope.launch {
            repository.saveThemeMode(mode)
        }
    }

    fun saveUiLayoutMode(mode: String) {
        viewModelScope.launch {
            repository.saveUiLayoutMode(mode)
        }
    }

    fun saveDefaultCostPercentage(percentage: Double) {
        viewModelScope.launch {
            repository.saveDefaultCostPercentage(percentage)
        }
    }

    fun saveShopInfo(name: String, address: String, phone: String) {
        viewModelScope.launch {
            repository.saveShopInfo(name, address, phone)
        }
    }

    fun saveInvoiceSettings(logoUri: String, facebookUrl: String, qrCodeInfo: String, cashierName: String) {
        viewModelScope.launch {
            repository.saveInvoiceSettings(logoUri, facebookUrl, qrCodeInfo, cashierName)
        }
    }

    fun saveInvoiceSettingsData(settings: com.example.techyfipos.data.InvoiceSettingsData) {
        viewModelScope.launch {
            repository.saveInvoiceSettingsData(settings)
        }
    }

    fun saveQuickSalePresets(presets: List<com.example.techyfipos.data.QuickSalePreset>) {
        viewModelScope.launch {
            repository.saveQuickSalePresets(presets)
        }
    }

    fun saveDueReminderTemplates(templates: List<com.example.techyfipos.data.DueTemplate>) {
        viewModelScope.launch {
            repository.saveDueReminderTemplates(templates)
        }
    }

    fun resetFactoryDefaults(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.resetFactoryDefaults()
            onComplete()
        }
    }

    fun previewDuplicateDueLedgers(onResult: (List<DataRepository.DuplicateDueLedgerGroup>) -> Unit) {
        viewModelScope.launch {
            onResult(repository.previewDuplicatePhoneDueLedgers())
        }
    }

    fun mergeDuplicateDueLedgers(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val merged = repository.mergeDuplicatePhoneDueLedgers()
            onComplete(merged)
        }
    }

    fun saveAutoBackup(enabled: Boolean, frequency: String, context: Context) {
        viewModelScope.launch {
            repository.saveAutoBackup(enabled, frequency)
            val netPolicy = repository.driveNetworkPolicyFlow.first()
            com.example.techyfipos.utils.AutoBackupScheduler.scheduleDriveBackup(
                context,
                enabled,
                frequency,
                networkPolicy = netPolicy,
                forceUpdate = true
            )
            if (enabled) {
                com.example.techyfipos.utils.AutoBackupScheduler.performImmediateBackupIfDue(context, force = true)
            }
        }
    }

    fun saveDriveSettings(
        enabled: Boolean,
        frequency: String,
        networkPolicy: String,
        retentionCount: Int,
        context: Context
    ) {
        viewModelScope.launch {
            repository.saveAutoBackup(enabled, frequency)
            repository.saveDriveNetworkPolicy(networkPolicy)
            repository.saveDriveRetentionCount(retentionCount)
            com.example.techyfipos.utils.AutoBackupScheduler.scheduleDriveBackup(
                context,
                enabled,
                frequency,
                networkPolicy = networkPolicy,
                forceUpdate = true
            )
        }
    }

    fun saveLocalAutoBackup(enabled: Boolean, frequency: String, context: Context) {
        viewModelScope.launch {
            repository.saveLocalAutoBackup(enabled, frequency)
            com.example.techyfipos.utils.AutoBackupScheduler.scheduleLocalBackup(context, enabled, frequency, forceUpdate = true)
            if (enabled) {
                com.example.techyfipos.utils.AutoBackupScheduler.performImmediateBackupIfDue(context, force = true)
            }
        }
    }

    suspend fun backupDatabase(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempFile = File.createTempFile("backup", ".json")
        try {
            val ok = repository.exportBackup(tempFile)
            if (ok && tempFile.exists()) {
                tempFile.inputStream().use { input -> input.copyTo(outputStream) }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempFile.delete()
        }
    }

    suspend fun restoreDatabase(
        inputStream: java.io.InputStream,
        mode: BackupImportMode = BackupImportMode.REPLACE
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempFile = File.createTempFile("restore", ".json")
        try {
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            repository.importBackup(tempFile, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempFile.delete()
        }
    }

    // Excel Actions
    //
    // Each export first builds the full workbook into an in-memory buffer, then copies the
    // complete bytes to the destination stream in one shot. Writing POI's multi-step zip/XML
    // output directly into the SAF-provided stream left a 0-byte file behind whenever that
    // stream got interrupted partway through (observed intermittently in production); building
    // in memory first means the destination is only ever touched with the finished, complete file.
    suspend fun exportProductsToExcel(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val products = repository.allProducts.first()
        return@withContext try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportProducts(products, buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun generateTemplate(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.generateProductsTemplate(buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importProductsFromExcel(inputStream: java.io.InputStream): com.example.techyfipos.utils.ExcelHelper.ImportResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = com.example.techyfipos.utils.ExcelHelper.importProducts(inputStream)
        if (result.importedProducts.isNotEmpty()) {
            repository.upsertProducts(result.importedProducts, result.presentFields)
        }
        result
    }

    suspend fun exportSalesReport(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val sales = repository.allSalesWithItems.first()
        val products = repository.allProducts.first()
        val returns = repository.allReturns.first()
        val returnItems = repository.allReturnItems.first()
        val purchasePrices = products.associate { it.barcode to it.purchasePrice }
        return@withContext try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportSales(sales, purchasePrices, returns, returnItems, buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportDuesReport(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dues = repository.allOutstandingDues.first()
        return@withContext try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportDues(dues, buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportExpensesReport(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val expenses = repository.allExpenses.first()
        return@withContext try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportExpenses(expenses, buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Google Drive Actions
    fun fetchDriveBackups(driveService: com.google.api.services.drive.Drive) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingBackups.value = true
            val list = com.example.techyfipos.utils.GoogleDriveHelper.listBackups(driveService)
            _driveBackups.value = list
            _isLoadingBackups.value = false
        }
    }

    suspend fun backupToDriveEncrypted(
        context: Context,
        connection: com.example.techyfipos.utils.GoogleDriveHelper.DriveConnection
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempEncryptedFile = File(context.cacheDir, "manual_enc_backup_${System.currentTimeMillis()}.enc")
        try {
            val meta = com.example.techyfipos.utils.BackupPackageManager.createEncryptedBackupPackage(
                context = context,
                destinationFile = tempEncryptedFile,
                accountIdOrPassphrase = connection.accountId
            )
            if (meta != null && tempEncryptedFile.exists()) {
                val driveService = com.example.techyfipos.utils.GoogleDriveHelper.getDriveService(connection)
                val retention = repository.driveRetentionCountFlow.first()
                val uploadResult = com.example.techyfipos.utils.GoogleDriveBackupManager.uploadEncryptedBackup(
                    driveService = driveService,
                    localEncryptedFile = tempEncryptedFile,
                    isAutoBackup = false,
                    retentionCount = retention
                )
                if (uploadResult.isSuccess) {
                    repository.saveLastDriveBackupTimestamp(System.currentTimeMillis())
                    fetchDriveBackups(driveService)
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempEncryptedFile.delete()
        }
    }

    suspend fun restoreFromDriveEncrypted(
        context: Context,
        connection: com.example.techyfipos.utils.GoogleDriveHelper.DriveConnection,
        fileId: String
    ): com.example.techyfipos.utils.BackupPackageManager.RestoreResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        repository.requireOwnerSession()
        val tempEncryptedFile = File(context.cacheDir, "download_restore_${System.currentTimeMillis()}.enc")
        try {
            val driveService = com.example.techyfipos.utils.GoogleDriveHelper.getDriveService(connection)
            val downloaded = com.example.techyfipos.utils.GoogleDriveBackupManager.downloadBackup(
                driveService = driveService,
                fileId = fileId,
                destinationFile = tempEncryptedFile
            )
            if (!downloaded || !tempEncryptedFile.exists()) {
                return@withContext com.example.techyfipos.utils.BackupPackageManager.RestoreResult(
                    isSuccess = false,
                    message = "Could not download backup file from Google Drive."
                )
            }

            val result = com.example.techyfipos.utils.BackupPackageManager.restoreEncryptedBackupPackage(
                context = context,
                encryptedFile = tempEncryptedFile,
                accountIdOrPassphrase = connection.accountId
            )
            result
        } catch (e: Exception) {
            e.printStackTrace()
            com.example.techyfipos.utils.BackupPackageManager.RestoreResult(
                isSuccess = false,
                message = "Restore error: ${e.localizedMessage ?: "Unknown"}"
            )
        } finally {
            tempEncryptedFile.delete()
        }
    }

    suspend fun inspectDriveBackup(
        context: Context,
        connection: com.example.techyfipos.utils.GoogleDriveHelper.DriveConnection,
        fileId: String
    ): com.example.techyfipos.utils.BackupPackageManager.BackupMetadata? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempEncryptedFile = File(context.cacheDir, "inspect_${System.currentTimeMillis()}.enc")
        try {
            val driveService = com.example.techyfipos.utils.GoogleDriveHelper.getDriveService(connection)
            val downloaded = com.example.techyfipos.utils.GoogleDriveBackupManager.downloadBackup(
                driveService = driveService,
                fileId = fileId,
                destinationFile = tempEncryptedFile
            )
            if (downloaded && tempEncryptedFile.exists()) {
                return@withContext com.example.techyfipos.utils.BackupPackageManager.readMetadataFromEncryptedPackage(
                    context = context,
                    encryptedFile = tempEncryptedFile,
                    accountIdOrPassphrase = connection.accountId
                )
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempEncryptedFile.delete()
        }
    }

    suspend fun restoreFromDrive(
        driveService: com.google.api.services.drive.Drive,
        fileId: String,
        cacheDir: File
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempFile = File(cacheDir, "temp_drive_restore.json")
        val downloadSuccess = com.example.techyfipos.utils.GoogleDriveHelper.downloadBackup(driveService, fileId, tempFile)
        if (downloadSuccess && tempFile.exists()) {
            val restoreSuccess = repository.importBackup(tempFile)
            tempFile.delete()
            return@withContext restoreSuccess
        }
        return@withContext false
    }

    suspend fun exportEncryptedLocalBackup(
        outputStream: java.io.OutputStream,
        passphrase: String
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempFile = java.io.File.createTempFile("temp_export_", ".posenc")
        try {
            val ok = repository.exportEncryptedLocalBackup(tempFile, passphrase)
            if (ok && tempFile.exists()) {
                tempFile.inputStream().use { input ->
                    input.copyTo(outputStream)
                }
                outputStream.flush()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importEncryptedLocalBackup(
        inputStream: java.io.InputStream,
        passphrase: String,
        mode: com.example.techyfipos.data.BackupImportMode = com.example.techyfipos.data.BackupImportMode.REPLACE
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tempFile = java.io.File.createTempFile("temp_import_", ".posenc")
        try {
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            repository.importEncryptedLocalBackup(tempFile, passphrase, mode)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            tempFile.delete()
        }
    }
}

/** Role-based access: "OWNER" (master), "MANAGER" (store operations), "CASHIER" (frontline sales) */
class AuthViewModel(private val repository: DataRepository) : ViewModel() {
    val users: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private fun setCurrentUser(user: User?) {
        _currentUser.value = user
        repository.setSessionUser(user)
    }

    val isOwner: StateFlow<Boolean> = _currentUser
        .map { it?.role == AppRoles.OWNER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isManager: StateFlow<Boolean> = _currentUser
        .map { it?.role == AppRoles.MANAGER }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isSupervisor: StateFlow<Boolean> = _currentUser
        .map { it?.let { u -> AppPermissions.isSupervisor(u) } == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val activePermissions: StateFlow<Set<String>> = _currentUser
        .map { u -> AppPermissions.getEffectivePermissions(u) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val maxDiscountPercent: StateFlow<Double> = _currentUser
        .map { it?.maxDiscountPercent ?: 10.0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 10.0)

    private val _lockoutSeconds = MutableStateFlow(0L)
    val lockoutSeconds: StateFlow<Long> = _lockoutSeconds.asStateFlow()

    fun refreshLockout() {
        val remaining = (repository.loginLockRemainingMillis() + 999L) / 1000L
        _lockoutSeconds.value = remaining
    }

    fun hasPermission(permissionKey: String): Boolean {
        return AppPermissions.hasPermission(_currentUser.value, permissionKey)
    }

    suspend fun hasAnyUsers(): Boolean = repository.hasAnyUsers()

    fun createOwnerAccount(name: String, pin: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (repository.hasAnyUsers()) {
                    onComplete(false)
                    return@launch
                }
                val id = repository.createUser(
                    name = name.ifBlank { "Owner" },
                    pin = pin,
                    role = AppRoles.OWNER,
                    customPermissions = null,
                    maxDiscount = 100.0
                )
                setCurrentUser(
                    User(
                        id = id,
                        name = name.ifBlank { "Owner" },
                        pinHash = "",
                        role = AppRoles.OWNER,
                        customPermissions = null,
                        isActive = true,
                        maxDiscountPercent = 100.0
                    )
                )
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("AuthViewModel", "Unable to create owner account", error)
                onComplete(false)
            }
        }
    }

    fun addStaff(
        name: String,
        pin: String,
        role: String = AppRoles.CASHIER,
        customPermissions: Set<String>? = null,
        maxDiscount: Double = 10.0,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (!repository.isSessionOwner()) {
                    onComplete(false, "Owner privileges required")
                    return@launch
                }
                repository.createUser(
                    name = name.trim(),
                    pin = pin,
                    role = role,
                    customPermissions = customPermissions,
                    maxDiscount = maxDiscount
                )
                onComplete(true, null)
            } catch (error: Exception) {
                android.util.Log.e("AuthViewModel", "Unable to add staff", error)
                onComplete(false, error.message ?: "Could not add staff")
            }
        }
    }

    fun addCashier(name: String, pin: String, onComplete: (Boolean) -> Unit) {
        addStaff(name = name, pin = pin, role = AppRoles.CASHIER) { success, _ -> onComplete(success) }
    }

    fun updateStaff(
        userId: Long,
        name: String,
        pin: String?,
        oldPin: String? = null,
        role: String,
        customPermissions: Set<String>?,
        isActive: Boolean,
        maxDiscount: Double,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                if (!repository.isSessionOwner()) {
                    onComplete(false, "Owner privileges required")
                    return@launch
                }
                repository.updateStaff(
                    userId = userId,
                    newName = name,
                    newPin = pin,
                    oldPin = oldPin,
                    newRole = role,
                    customPermissions = customPermissions,
                    isActive = isActive,
                    maxDiscount = maxDiscount
                )
                // If editing current logged-in user, refresh currentUser state
                if (_currentUser.value?.id == userId) {
                    val updated = _currentUser.value?.copy(
                        name = name.trim(),
                        role = role,
                        customPermissions = AppPermissions.serializePermissions(customPermissions),
                        isActive = isActive,
                        maxDiscountPercent = maxDiscount
                    )
                    setCurrentUser(updated)
                }
                onComplete(true, null)
            } catch (error: Exception) {
                android.util.Log.e("AuthViewModel", "Unable to update staff", error)
                onComplete(false, error.message ?: "Could not update staff")
            }
        }
    }

    fun updateCashier(userId: Long, newName: String, newPin: String?, oldPin: String? = null, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (!repository.isSessionOwner()) {
                    onComplete(false)
                    return@launch
                }
                repository.updateCashier(userId, newName, newPin, oldPin)
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("AuthViewModel", "Unable to update cashier", error)
                onComplete(false)
            }
        }
    }

    fun setStaffActive(userId: Long, isActive: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (!repository.isSessionOwner()) {
                    onComplete(false)
                    return@launch
                }
                repository.setStaffActive(userId, isActive)
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("AuthViewModel", "Unable to toggle staff status", error)
                onComplete(false)
            }
        }
    }

    fun updateStaffPermissions(userId: Long, permissions: Set<String>?, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (!repository.isSessionOwner()) {
                    onComplete(false)
                    return@launch
                }
                repository.updateStaffPermissions(userId, permissions)
                if (_currentUser.value?.id == userId) {
                    val updated = _currentUser.value?.copy(
                        customPermissions = AppPermissions.serializePermissions(permissions)
                    )
                    setCurrentUser(updated)
                }
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("AuthViewModel", "Unable to update permissions", error)
                onComplete(false)
            }
        }
    }

    fun verifySupervisorPin(pin: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val supervisor = repository.verifySupervisorPin(pin)
            onResult(supervisor)
        }
    }

    fun deleteUser(user: User, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (user.role == AppRoles.OWNER && repository.getOwnerCount() <= 1) {
                onComplete(false)
                return@launch
            }
            try {
                repository.deleteUser(user)
                onComplete(true)
            } catch (error: Exception) {
                android.util.Log.e("AuthViewModel", "Unable to delete user", error)
                onComplete(false)
            }
        }
    }

    fun attemptLogin(pin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val remainingBefore = repository.loginLockRemainingMillis()
            if (remainingBefore > 0L) {
                val s = (remainingBefore + 999L) / 1000L
                _lockoutSeconds.value = s
                onResult(false, "Too many failed attempts. Try again in $s seconds.")
                return@launch
            }
            val user = repository.verifyPin(pin)
            if (user != null && !user.isActive) {
                onResult(false, "This account is deactivated. Contact store owner.")
                return@launch
            }
            setCurrentUser(user)
            val lockMillis = repository.loginLockRemainingMillis()
            val lockSec = (lockMillis + 999L) / 1000L
            _lockoutSeconds.value = lockSec
            val message = if (user == null && lockSec > 0L) {
                "Too many failed attempts. Try again in $lockSec seconds."
            } else null
            onResult(user != null, message)
        }
    }

    fun resetOwnerPinAfterDeviceCredential(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(repository.resetOwnerPinAfterDeviceCredential(pin))
        }
    }

    fun logout() {
        setCurrentUser(null)
    }
}

data class ProviderSalesBreakdown(
    val cash: Double = 0.0,
    val bkash: Double = 0.0,
    val nagad: Double = 0.0,
    val rocket: Double = 0.0,
    val card: Double = 0.0,
    val due: Double = 0.0
)

internal fun calculateSaleProviderAmounts(sale: Sale): ProviderSalesBreakdown {
    val nonSplitMethod = sale.paymentMethod.trim().lowercase()
    if (sale.paymentMethod != "Split" || sale.splitPaymentJson.isNullOrBlank()) {
        return when (nonSplitMethod) {
            "cash" -> ProviderSalesBreakdown(cash = sale.grandTotal)
            "bkash" -> ProviderSalesBreakdown(bkash = sale.grandTotal)
            "nagad" -> ProviderSalesBreakdown(nagad = sale.grandTotal)
            "rocket" -> ProviderSalesBreakdown(rocket = sale.grandTotal)
            "card", "bank" -> ProviderSalesBreakdown(card = sale.grandTotal)
            "due" -> ProviderSalesBreakdown(due = (sale.grandTotal - sale.amountPaid).coerceAtLeast(0.0), cash = sale.amountPaid.coerceAtLeast(0.0))
            "store credit", "loyalty" -> ProviderSalesBreakdown()
            else -> ProviderSalesBreakdown()
        }
    }
    return try {
        val arr = JSONArray(sale.splitPaymentJson)
        var cash = 0.0
        var bkash = 0.0
        var nagad = 0.0
        var rocket = 0.0
        var card = 0.0
        var due = 0.0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            val m = obj.optString("method", "").trim().lowercase()
            when (m) {
                "cash" -> cash += amount
                "bkash" -> bkash += amount
                "nagad" -> nagad += amount
                "rocket" -> rocket += amount
                "card", "bank" -> card += amount
                "due" -> due += amount
                "store credit", "loyalty" -> { /* store credit brings no cash or digital currency */ }
                else -> { /* do not default unknown methods to cash */ }
            }
        }
        ProviderSalesBreakdown(cash = cash, bkash = bkash, nagad = nagad, rocket = rocket, card = card, due = due)
    } catch (_: Exception) {
        ProviderSalesBreakdown()
    }
}

internal val REGISTER_CLOSE_DIGITAL_METHODS = setOf("bkash", "nagad", "rocket", "card", "bank", "mfs", "digital")

/** Cash/digital portion of a sale's grandTotal, apportioning a Split sale's components from `splitPaymentJson` instead of dropping it entirely. Due portions count toward neither (not yet collected). */
internal fun splitCashAndDigitalAmounts(sale: Sale): Pair<Double, Double> {
    val nonSplitMethod = sale.paymentMethod.trim()
    if (sale.paymentMethod != "Split" || sale.splitPaymentJson.isNullOrBlank()) {
        return when {
            nonSplitMethod.equals("Cash", ignoreCase = true) -> sale.grandTotal to 0.0
            nonSplitMethod.equals("Due", ignoreCase = true) -> sale.amountPaid.coerceAtLeast(0.0) to 0.0
            REGISTER_CLOSE_DIGITAL_METHODS.contains(nonSplitMethod.lowercase()) -> 0.0 to sale.grandTotal
            else -> 0.0 to 0.0
        }
    }
    return try {
        val arr = JSONArray(sale.splitPaymentJson)
        var cash = 0.0
        var digital = 0.0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val amount = obj.optDouble("amount", 0.0)
            val method = obj.optString("method", "").trim().lowercase()
            when {
                method == "cash" -> cash += amount
                REGISTER_CLOSE_DIGITAL_METHODS.contains(method) -> digital += amount
            }
        }
        cash to digital
    } catch (e: Exception) {
        0.0 to 0.0
    }
}

internal fun calculateSaleDueAmount(sale: Sale): Double {
    if (sale.paymentMethod == "Due") {
        return (sale.grandTotal - sale.amountPaid).coerceAtLeast(0.0)
    }
    if (sale.paymentMethod != "Split" || sale.splitPaymentJson.isNullOrBlank()) return 0.0
    return try {
        val arr = JSONArray(sale.splitPaymentJson)
        (0 until arr.length()).sumOf { index ->
            arr.getJSONObject(index).takeIf {
                it.optString("method", "").equals("Due", ignoreCase = true)
            }?.optDouble("amount", 0.0) ?: 0.0
        }.coerceAtLeast(0.0)
    } catch (_: Exception) {
        0.0
    }
}

internal fun cashRefundAmount(refund: Return, sale: Sale?): Double = when {
    refund.refundMethod.startsWith("Cash") -> refund.externalRefundAmount
    refund.refundMethod.startsWith("Original method") && sale != null -> {
        val (cashPortion, _) = splitCashAndDigitalAmounts(sale)
        val originalCollected = (sale.grandTotal - calculateSaleDueAmount(sale)).coerceAtLeast(0.0)
        if (originalCollected > 0.0) {
            refund.externalRefundAmount * (cashPortion / originalCollected).coerceIn(0.0, 1.0)
        } else 0.0
    }
    else -> 0.0
}

class ExpenseViewModel(private val repository: DataRepository) : ViewModel() {
    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val registerShiftState: StateFlow<RegisterShiftState?> = repository.registerShiftState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun todayStartMillis(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    val todayExpenses: StateFlow<List<Expense>> = combine(allExpenses, registerShiftState) { list, shift ->
        val start = shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()
        list.filter { it.timestamp >= start }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCashIn: StateFlow<Double> = todayExpenses.map { list ->
        list.filter { it.type == "CASH_IN" && it.paymentSource == "Cash" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCashOut: StateFlow<Double> = todayExpenses.map { list ->
        list.filter { it.type == "EXPENSE" && it.paymentSource == "Cash" && it.category != "Supplier Payment" }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val allSales: StateFlow<List<Sale>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allReturns: StateFlow<List<Return>> = repository.allReturns
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allDuePayments: StateFlow<List<com.example.techyfipos.data.db.DuePayment>> = repository.allDuePayments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allSupplierPayments: StateFlow<List<com.example.techyfipos.data.db.SupplierPayment>> = repository.allSupplierPayments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalCashSales: StateFlow<Double> = combine(allSales, registerShiftState) { sales, shift ->
        val start = shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()
        sales.filter { it.timestamp >= start }.sumOf { splitCashAndDigitalAmounts(it).first }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalDigitalSales: StateFlow<Double> = combine(allSales, allDuePayments, registerShiftState) { sales, duePayments, shift ->
        val start = shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()
        val salesDigital = sales.filter { it.timestamp >= start }.sumOf { splitCashAndDigitalAmounts(it).second }
        val dueDigital = duePayments.filter {
            it.timestamp >= start && REGISTER_CLOSE_DIGITAL_METHODS.contains(it.paymentMethod.trim().lowercase())
        }.sumOf { it.amountPaid }
        salesDigital + dueDigital
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val providerSalesBreakdown: StateFlow<ProviderSalesBreakdown> = combine(allSales, allDuePayments, registerShiftState) { sales, duePayments, shift ->
        val start = shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()
        var cash = 0.0
        var bkash = 0.0
        var nagad = 0.0
        var rocket = 0.0
        var card = 0.0
        var due = 0.0
        sales.filter { it.timestamp >= start }.forEach { sale ->
            val b = calculateSaleProviderAmounts(sale)
            cash += b.cash
            bkash += b.bkash
            nagad += b.nagad
            rocket += b.rocket
            card += b.card
            due += b.due
        }
        duePayments.filter { it.timestamp >= start }.forEach { dp ->
            val m = dp.paymentMethod.trim().lowercase()
            when (m) {
                "bkash" -> bkash += dp.amountPaid
                "nagad" -> nagad += dp.amountPaid
                "rocket" -> rocket += dp.amountPaid
                "card", "bank" -> card += dp.amountPaid
            }
        }
        ProviderSalesBreakdown(cash = cash, bkash = bkash, nagad = nagad, rocket = rocket, card = card, due = due)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProviderSalesBreakdown())

    val totalDueCashCollected: StateFlow<Double> = combine(allDuePayments, registerShiftState) { payments, shift ->
        val start = shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()
        payments.filter { it.timestamp >= start && it.paymentMethod.equals("Cash", ignoreCase = true) }
            .sumOf { it.amountPaid }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalSupplierCashPaid: StateFlow<Double> = combine(allSupplierPayments, registerShiftState) { payments, shift ->
        val start = shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()
        payments.filter { it.timestamp >= start && it.paymentMethod.equals("Cash", ignoreCase = true) }
            .sumOf { it.amountPaid }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // "Cash" refunds always take physical cash out of the drawer. "Original method" refunds are
    // apportioned by the original sale's cash fraction — for a plain Cash sale that's 100%, for a
    // plain digital sale it's 0%, and for a Split sale it's whatever fraction of that sale was
    // actually paid in cash (parsed from splitPaymentJson via splitCashAndDigitalAmounts).
    // "Store credit" never affects the drawer.
    val totalCashRefunds: StateFlow<Double> = combine(allReturns, allSales, registerShiftState) { returns, sales, shift ->
        val start = shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()
        val salesById = sales.associateBy { it.id }
        returns.filter { it.timestamp >= start }
            .sumOf { refund -> cashRefundAmount(refund, salesById[refund.saleId]) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val netDrawerCash: StateFlow<Double> = combine(
        combine(totalCashSales, totalCashIn, totalCashOut, totalCashRefunds) { sales, cIn, cOut, cRef ->
            sales + cIn - cOut - cRef
        },
        combine(totalDueCashCollected, totalSupplierCashPaid, registerShiftState) { dueCash, supplierCash, shift ->
            (shift?.startingFloat ?: 0.0) + dueCash - supplierCash
        }
    ) { salesNet, shiftNet ->
        salesNet + shiftNet
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val liveDrawerCash: StateFlow<Double> = netDrawerCash

    fun recordExpense(
        title: String,
        category: String,
        amount: Double,
        paymentSource: String = "Cash",
        note: String? = null
    ) {
        addExpense(title, category, "EXPENSE", amount, paymentSource, note)
    }

    fun recordCashIn(
        title: String,
        amount: Double,
        paymentSource: String = "Cash",
        note: String? = null
    ) {
        addExpense(title, "Cash In", "CASH_IN", amount, paymentSource, note)
    }

    fun deleteExpense(expense: Expense, onResult: (Result<Unit>) -> Unit = {}) {
        deleteExpense(expense.id, onResult)
    }

    fun addExpense(
        title: String,
        category: String,
        type: String,
        amount: Double,
        paymentSource: String = "Cash",
        note: String? = null,
        cashierName: String = "Admin"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertExpense(
                Expense(
                    title = title.trim(),
                    category = category,
                    type = type,
                    amount = amount,
                    paymentSource = paymentSource,
                    note = note?.takeIf { it.isNotBlank() },
                    timestamp = System.currentTimeMillis(),
                    cashierName = cashierName
                )
            )
        }
    }

    fun deleteExpense(id: Long, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) { repository.deleteExpense(id) }
            }
            onResult(result)
        }
    }

    fun updateExpense(expense: Expense, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) { repository.updateExpense(expense) }
            }
            onResult(result)
        }
    }

    suspend fun exportExpensesReport(outputStream: java.io.OutputStream): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val expenses = repository.allExpenses.first()
        return@withContext try {
            val buffer = java.io.ByteArrayOutputStream()
            com.example.techyfipos.utils.ExcelHelper.exportExpenses(expenses, buffer)
            outputStream.write(buffer.toByteArray())
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

class SupplierViewModel(private val repository: DataRepository) : ViewModel() {
    val allProducts: StateFlow<List<Product>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<Supplier>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchaseInvoices: StateFlow<List<PurchaseInvoice>> = repository.allPurchaseInvoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSupplierPayments: StateFlow<List<SupplierPayment>> = repository.allSupplierPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSupplierDue: StateFlow<Double> = allSuppliers.map { list ->
        list.sumOf { it.totalDue }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    fun addSupplier(
        name: String,
        companyName: String,
        phone: String,
        address: String? = null,
        notes: String? = null,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        upsertSupplier(name, companyName, phone, address, notes, onResult = onResult)
    }

    fun recordPurchase(
        supplierId: Long,
        invoiceNumber: String,
        totalAmount: Double,
        paidAmount: Double,
        itemsSummary: String,
        paymentMethod: String = "Cash",
        note: String? = null,
        stockLines: List<PurchaseStockLine> = emptyList(),
        onResult: (Result<Long>) -> Unit = {}
    ) {
        receiveStockIn(supplierId, invoiceNumber, totalAmount, paidAmount, itemsSummary, paymentMethod, note, stockLines, onResult)
    }

    fun paySupplierDue(
        supplier: Supplier,
        amountPaid: Double,
        paymentMethod: String = "Cash",
        referenceNote: String? = null,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        paySupplierDue(supplier.id, amountPaid, paymentMethod, referenceNote, onResult)
    }

    fun upsertSupplier(
        name: String,
        companyName: String,
        phone: String,
        address: String? = null,
        notes: String? = null,
        id: Long = 0,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.upsertSupplier(
                        Supplier(
                            id = id,
                            name = name.trim(),
                            companyName = companyName.trim(),
                            phone = phone.trim(),
                            address = address?.takeIf { it.isNotBlank() },
                            notes = notes?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            onResult(result)
        }
    }

    fun updateSupplier(supplier: Supplier, onResult: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.upsertSupplier(supplier)
                }
            }
            onResult(result)
        }
    }

    fun deleteSupplier(supplier: Supplier, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                repository.deleteSupplier(supplier)
            }
            onResult(result)
        }
    }

    fun receiveStockIn(
        supplierId: Long,
        invoiceNumber: String,
        totalAmount: Double,
        paidAmount: Double,
        itemsSummary: String,
        paymentMethod: String = "Cash",
        note: String? = null,
        stockLines: List<PurchaseStockLine> = emptyList(),
        onResult: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.recordPurchaseInvoice(
                        supplierId = supplierId,
                        invoiceNumber = invoiceNumber.trim(),
                        totalAmount = totalAmount,
                        paidAmount = paidAmount,
                        itemsSummary = itemsSummary.trim(),
                        paymentMethod = paymentMethod,
                        note = note,
                        stockLines = stockLines
                    )
                }
            }
            onResult(result)
        }
    }

    fun paySupplierDue(
        supplierId: Long,
        amountPaid: Double,
        paymentMethod: String = "Cash",
        referenceNote: String? = null,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.recordSupplierPayment(supplierId, amountPaid, paymentMethod, referenceNote)
                }
            }
            onResult(result)
        }
    }
}

class RegisterClosingViewModel(private val repository: DataRepository) : ViewModel() {
    val activeCashierName: String get() = repository.sessionUserName
    val allClosings: StateFlow<List<RegisterClosing>> = repository.allRegisterClosings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val shiftState: StateFlow<RegisterShiftState?> = repository.registerShiftState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updateStartingFloat(amount: Double) {
        viewModelScope.launch(Dispatchers.IO) { repository.updateRegisterShiftStartingFloat(amount) }
    }

    fun closeRegister(
        startingFloat: Double,
        cashSales: Double,
        digitalSales: Double,
        cashIn: Double,
        cashOut: Double,
        cashRefunds: Double,
        shiftStartedAt: Long,
        expectedCash: Double,
        actualCountedCash: Double,
        notes: String? = null,
        discrepancyReason: String? = null,
        bkashSales: Double = 0.0,
        nagadSales: Double = 0.0,
        rocketSales: Double = 0.0,
        cardSales: Double = 0.0,
        dueSales: Double = 0.0,
        onResult: (Result<Long>) -> Unit = {}
    ) {
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val variance = actualCountedCash - expectedCash
        viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.insertRegisterClosing(RegisterClosing(
                    date = dateStr,
                    startingFloat = startingFloat,
                    cashSales = cashSales,
                    digitalSales = digitalSales,
                    cashIn = cashIn,
                    cashOut = cashOut,
                    cashRefunds = cashRefunds,
                    shiftStartedAt = shiftStartedAt,
                    expectedCash = expectedCash,
                    actualCountedCash = actualCountedCash,
                    variance = variance,
                    notes = notes,
                    discrepancyReason = discrepancyReason,
                    timestamp = System.currentTimeMillis(),
                    bkashSales = bkashSales,
                    nagadSales = nagadSales,
                    rocketSales = rocketSales,
                    cardSales = cardSales,
                    dueSales = dueSales
                    ))
                }
            }
            onResult(result)
        }
    }
}

enum class LoyaltyFilterOption {
    ALL,
    VIP,
    WITH_POINTS,
    ACTIVE,
    EXPIRING_SOON,
    ZERO_POINTS
}

enum class LoyaltySortOption {
    POINTS_DESC,
    POINTS_ASC,
    SPEND_DESC,
    RECENTLY_ACTIVE,
    NAME_ASC,
    RECENTLY_ADDED
}

data class LoyaltyKpiData(
    val totalMembers: Int = 0,
    val outstandingPoints: Int = 0,
    val redeemedThisMonth: Int = 0,
    val vipMembers: Int = 0
)

class LoyaltyViewModel(private val repository: DataRepository) : ViewModel() {
    val allMembers: StateFlow<List<CustomerLoyalty>> = repository.allLoyaltyMembers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTransactions: StateFlow<List<LoyaltyTransaction>> = repository.allLoyaltyTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(LoyaltyFilterOption.ALL)
    val selectedFilter: StateFlow<LoyaltyFilterOption> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(LoyaltySortOption.POINTS_DESC)
    val selectedSort: StateFlow<LoyaltySortOption> = _selectedSort.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: LoyaltyFilterOption) {
        _selectedFilter.value = filter
    }

    fun setSelectedSort(sort: LoyaltySortOption) {
        _selectedSort.value = sort
    }

    val kpiStats: StateFlow<LoyaltyKpiData> = combine(allMembers, allTransactions) { members, transactions ->
        val totalMembers = members.size
        val outstanding = members.sumOf { it.pointsBalance }
        val now = System.currentTimeMillis()
        val oneMonthAgo = now - 30L * 24 * 60 * 60 * 1000
        val redeemed = transactions
            .filter { it.timestamp >= oneMonthAgo && it.pointsChange < 0 }
            .sumOf { -it.pointsChange }
        val vipCount = members.count { it.tier.equals("Gold", ignoreCase = true) || it.tier.equals("Platinum", ignoreCase = true) }
        LoyaltyKpiData(totalMembers, outstanding, redeemed, vipCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoyaltyKpiData())

    val filteredMembers: StateFlow<List<CustomerLoyalty>> = combine(
        allMembers,
        searchQuery,
        selectedFilter,
        selectedSort
    ) { members, query, filter, sort ->
        val q = query.trim()
        var list = if (q.isBlank()) members else members.filter {
            it.name.contains(q, ignoreCase = true) || it.phone.contains(q)
        }
        val now = System.currentTimeMillis()
        val ninetyDaysAgo = now - 90L * 24 * 60 * 60 * 1000
        list = when (filter) {
            LoyaltyFilterOption.ALL -> list
            LoyaltyFilterOption.VIP -> list.filter { it.tier.equals("Gold", ignoreCase = true) || it.tier.equals("Platinum", ignoreCase = true) }
            LoyaltyFilterOption.WITH_POINTS -> list.filter { it.pointsBalance > 0 }
            LoyaltyFilterOption.ACTIVE -> list.filter { it.isActive && it.updatedAt >= ninetyDaysAgo }
            LoyaltyFilterOption.EXPIRING_SOON -> list.filter { it.pointsBalance > 0 && it.updatedAt < ninetyDaysAgo }
            LoyaltyFilterOption.ZERO_POINTS -> list.filter { it.pointsBalance == 0 }
        }
        when (sort) {
            LoyaltySortOption.POINTS_DESC -> list.sortedByDescending { it.pointsBalance }
            LoyaltySortOption.POINTS_ASC -> list.sortedBy { it.pointsBalance }
            LoyaltySortOption.SPEND_DESC -> list.sortedByDescending { it.lifetimeSpent }
            LoyaltySortOption.RECENTLY_ACTIVE -> list.sortedByDescending { it.updatedAt }
            LoyaltySortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            LoyaltySortOption.RECENTLY_ADDED -> list.sortedByDescending { it.updatedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun findMemberByPhone(phone: String): CustomerLoyalty? {
        val clean = phone.trim().filter(Char::isDigit)
        if (clean.length < 5) return null
        return allMembers.value.firstOrNull {
            val memberClean = it.phone.filter(Char::isDigit)
            memberClean == clean || memberClean.endsWith(clean) || clean.endsWith(memberClean)
        }
    }

    fun upsertMember(phone: String, name: String, tier: String = "Silver") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertLoyalty(
                CustomerLoyalty(
                    phone = phone.trim(),
                    name = name.trim(),
                    tier = tier
                )
            )
        }
    }

    fun addPoints(phone: String, pts: Int, spend: Double, reason: String = "Manual points adjustment") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addLoyaltyPoints(phone.trim(), pts, spend, reason)
        }
    }

    fun redeemPoints(phone: String, pts: Int, reason: String = "Manual redemption") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.redeemLoyaltyPoints(phone.trim(), pts, reason)
        }
    }

    fun deactivateMember(phone: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.setLoyaltyMemberActive(phone, false) }
    }

    fun deleteMember(phone: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteLoyaltyMember(phone) }
    }

    fun transactionsForCustomer(phone: String): Flow<List<LoyaltyTransaction>> {
        return repository.getLoyaltyTransactionsForCustomer(phone)
    }
}

enum class CustomerFilterSegment {
    ALL,
    WITH_DUES,
    WITH_STORE_CREDIT,
    VIP,
    FREQUENT,
    WHOLESALE,
    RECENT,
    NEW_CUSTOMERS,
    DEFAULTER
}

enum class CustomerSortOption {
    RECENT_VISIT,
    LIFETIME_SPEND,
    HIGHEST_DUE,
    STORE_CREDIT,
    ORDERS_COUNT,
    NAME_ASC,
    LOYALTY_POINTS
}

enum class CustomerTimelineEventType {
    SALE,
    DUE_CREATED,
    PAYMENT_RECEIVED,
    LOYALTY,
    NOTE,
    ACCOUNT_CREATED
}

data class CustomerTimelineEvent(
    val id: String,
    val type: CustomerTimelineEventType,
    val title: String,
    val description: String,
    val amount: Double? = null,
    val timestamp: Long,
    val referenceId: String? = null
)

data class DuplicateCustomerPair(
    val customerA: Customer,
    val customerB: Customer,
    val matchReason: String
)

data class CustomerDossierData(
    val customer: Customer,
    val topProducts: List<CustomerProductPurchaseSummary>,
    val sales: List<SaleWithItems>,
    val duePayments: List<DuePayment>,
    val loyaltyTransactions: List<LoyaltyTransaction>,
    val timelineEvents: List<CustomerTimelineEvent> = emptyList(),
    val storeCreditLedger: List<StoreCreditLedgerEntry> = emptyList()
)

class CustomerViewModel(private val repository: DataRepository) : ViewModel() {
    val allCustomers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(CustomerFilterSegment.ALL)
    val selectedFilter: StateFlow<CustomerFilterSegment> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(CustomerSortOption.RECENT_VISIT)
    val selectedSort: StateFlow<CustomerSortOption> = _selectedSort.asStateFlow()

    private val _isTableView = MutableStateFlow(false)
    val isTableView: StateFlow<Boolean> = _isTableView.asStateFlow()

    private val _selectedCustomerPhones = MutableStateFlow<Set<String>>(emptySet())
    val selectedCustomerPhones: StateFlow<Set<String>> = _selectedCustomerPhones.asStateFlow()

    private val _selectedCustomerDossier = MutableStateFlow<CustomerDossierData?>(null)
    val selectedCustomerDossier: StateFlow<CustomerDossierData?> = _selectedCustomerDossier.asStateFlow()

    private val _isLoadingDossier = MutableStateFlow(false)
    val isLoadingDossier: StateFlow<Boolean> = _isLoadingDossier.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFilter(filter: CustomerFilterSegment) {
        _selectedFilter.value = filter
    }

    fun setSelectedSort(sort: CustomerSortOption) {
        _selectedSort.value = sort
    }

    fun toggleTableView() {
        _isTableView.value = !_isTableView.value
    }

    fun setTableView(table: Boolean) {
        _isTableView.value = table
    }

    val duplicatePairs: StateFlow<List<DuplicateCustomerPair>> = allCustomers.map { list ->
        findDuplicatePairs(list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCustomers: StateFlow<List<Customer>> = combine(
        allCustomers,
        _searchQuery,
        _selectedFilter,
        _selectedSort
    ) { customers, query, filter, sort ->
        val q = query.trim().lowercase()
        val searched = if (q.isBlank()) customers else {
            customers.filter {
                it.name.lowercase().contains(q) ||
                it.phone.contains(q) ||
                (it.address?.lowercase()?.contains(q) == true) ||
                (it.notes?.lowercase()?.contains(q) == true) ||
                (it.tag.lowercase().contains(q))
            }
        }

        val filtered = when (filter) {
            CustomerFilterSegment.ALL -> searched
            CustomerFilterSegment.WITH_DUES -> searched.filter { it.totalDue > 0 }
            CustomerFilterSegment.WITH_STORE_CREDIT -> searched.filter { it.storeCredit > 0 }
            CustomerFilterSegment.VIP -> searched.filter { it.tag.equals("VIP", ignoreCase = true) || it.loyaltyTier.equals("Gold", ignoreCase = true) || it.loyaltyTier.equals("Platinum", ignoreCase = true) || it.lifetimeSpent >= 20000.0 }
            CustomerFilterSegment.FREQUENT -> searched.filter { it.totalOrders >= 5 }
            CustomerFilterSegment.WHOLESALE -> searched.filter { it.tag.equals("Wholesale", ignoreCase = true) }
            CustomerFilterSegment.RECENT -> {
                val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                searched.filter { (it.lastPurchaseDate ?: it.updatedAt) >= sevenDaysAgo }
            }
            CustomerFilterSegment.NEW_CUSTOMERS -> {
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                searched.filter { it.totalOrders <= 2 || it.createdAt >= thirtyDaysAgo }
            }
            CustomerFilterSegment.DEFAULTER -> {
                searched.filter { it.tag.equals("Defaulter", ignoreCase = true) || (it.creditLimit > 0 && it.totalDue > it.creditLimit) }
            }
        }

        when (sort) {
            CustomerSortOption.RECENT_VISIT -> filtered.sortedByDescending { it.lastPurchaseDate ?: it.updatedAt }
            CustomerSortOption.LIFETIME_SPEND -> filtered.sortedByDescending { it.lifetimeSpent }
            CustomerSortOption.HIGHEST_DUE -> filtered.sortedByDescending { it.totalDue }
            CustomerSortOption.STORE_CREDIT -> filtered.sortedByDescending { it.storeCredit }
            CustomerSortOption.ORDERS_COUNT -> filtered.sortedByDescending { it.totalOrders }
            CustomerSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            CustomerSortOption.LOYALTY_POINTS -> filtered.sortedByDescending { it.loyaltyPoints }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // KPI Metrics
    val totalCustomersCount: StateFlow<Int> = allCustomers.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDuesAmount: StateFlow<Double> = allCustomers.map { it.sumOf { c -> c.totalDue } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalStoreCreditAmount: StateFlow<Double> = allCustomers.map { it.sumOf { c -> c.storeCredit } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val vipCustomersCount: StateFlow<Int> = allCustomers.map {
        it.count { c -> c.tag.equals("VIP", ignoreCase = true) || c.loyaltyTier in listOf("Gold", "Platinum") || c.lifetimeSpent >= 20000.0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val repeatCustomersPercent: StateFlow<Int> = allCustomers.map {
        if (it.isEmpty()) 0 else {
            val repeatCount = it.count { c -> c.totalOrders > 1 }
            ((repeatCount.toDouble() / it.size.toDouble()) * 100).toInt()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val averageCustomerSpend: StateFlow<Double> = allCustomers.map { list ->
        if (list.isEmpty()) 0.0 else list.sumOf { it.lifetimeSpent } / list.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun toggleCustomerSelection(phone: String) {
        val current = _selectedCustomerPhones.value
        _selectedCustomerPhones.value = if (current.contains(phone)) current - phone else current + phone
    }

    fun selectAll(phones: Collection<String>) {
        _selectedCustomerPhones.value = phones.toSet()
    }

    fun clearCustomerSelection() {
        _selectedCustomerPhones.value = emptySet()
    }

    fun bulkAssignTag(tag: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val targets = _selectedCustomerPhones.value
                targets.forEach { phone ->
                    val c = repository.getCustomerByPhone(phone)
                    if (c != null) {
                        repository.upsertCustomer(c.copy(tag = tag, updatedAt = System.currentTimeMillis()))
                    }
                }
                clearCustomerSelection()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to assign bulk tag")
            }
        }
    }

    fun mergeCustomers(masterPhone: String, secondaryPhone: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val success = repository.mergeCustomers(masterPhone, secondaryPhone)
                if (success) {
                    loadCustomerDossier(masterPhone)
                    onSuccess()
                } else {
                    onError("Could not merge selected customer accounts")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Merge failed")
            }
        }
    }

    fun migrateCustomerPhone(
        oldPhone: String,
        newPhone: String,
        name: String,
        email: String?,
        address: String?,
        notes: String?,
        tag: String,
        creditLimit: Double,
        onSuccess: (newPhone: String) -> Unit,
        onCollision: (existingCustomer: com.example.techyfipos.data.db.Customer) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                when (val res = repository.migrateCustomerPhone(oldPhone, newPhone, name, creditLimit, notes)) {
                    is com.example.techyfipos.data.CustomerPhoneUpdateResult.Success -> {
                        val created = repository.getCustomerByPhone(res.finalPhone)
                        if (created != null) {
                            repository.upsertCustomer(
                                created.copy(
                                    email = email,
                                    address = address,
                                    tag = tag,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        loadCustomerDossier(res.finalPhone)
                        onSuccess(res.finalPhone)
                    }
                    is com.example.techyfipos.data.CustomerPhoneUpdateResult.Collision -> {
                        onCollision(res.existingCustomer)
                    }
                    is com.example.techyfipos.data.CustomerPhoneUpdateResult.Error -> {
                        onError(res.message)
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "Failed to migrate customer phone")
            }
        }
    }

    fun addCustomerNote(phone: String, noteText: String, category: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ok = repository.addCustomerNote(phone, noteText, category)
                if (ok) {
                    loadCustomerDossier(phone)
                    onSuccess()
                } else {
                    onError("Failed to add note")
                }
            } catch (e: Exception) {
                onError(e.message ?: "Error saving note")
            }
        }
    }

    private fun findDuplicatePairs(customers: List<Customer>): List<DuplicateCustomerPair> {
        val active = customers.filter { !it.isArchived }
        val pairs = mutableListOf<DuplicateCustomerPair>()
        val seenPairKeys = mutableSetOf<String>()

        val phoneGroups = mutableMapOf<String, MutableList<Customer>>()
        val nameGroups = mutableMapOf<String, MutableList<Customer>>()

        for (c in active) {
            val cleanDigits = c.phone.filter { it.isDigit() }.takeLast(10)
            if (cleanDigits.length >= 10) {
                phoneGroups.getOrPut(cleanDigits) { mutableListOf() }.add(c)
            }
            val cleanName = c.name.trim().lowercase()
            if (cleanName.length >= 3) {
                nameGroups.getOrPut(cleanName) { mutableListOf() }.add(c)
            }
        }

        fun addPair(a: Customer, b: Customer, reason: String) {
            if (a.phone == b.phone) return
            val pairKey = if (a.phone < b.phone) "${a.phone}___${b.phone}" else "${b.phone}___${a.phone}"
            if (seenPairKeys.add(pairKey)) {
                pairs.add(DuplicateCustomerPair(a, b, reason))
            }
        }

        for ((phoneDigits, list) in phoneGroups) {
            if (list.size > 1) {
                for (i in 0 until list.size) {
                    for (j in i + 1 until list.size) {
                        addPair(list[i], list[j], "Matching phone digits ($phoneDigits)")
                    }
                }
            }
        }

        for ((_, list) in nameGroups) {
            if (list.size > 1) {
                for (i in 0 until list.size) {
                    for (j in i + 1 until list.size) {
                        addPair(list[i], list[j], "Identical name ('${list[i].name.trim()}')")
                    }
                }
            }
        }

        return pairs
    }

    fun loadCustomerDossier(phone: String) {
        _isLoadingDossier.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val customer = repository.getCustomerByPhone(phone)
                if (customer != null) {
                    val topProducts = repository.getTopProductsForCustomer(phone, 15)
                    val sales = repository.getCustomerSalesWithItemsOnce(phone)
                    val duePayments = repository.getPaymentsForCustomer(phone).firstOrNull() ?: emptyList()
                    val loyaltyTransactions = repository.getLoyaltyTransactionsForCustomer(phone).firstOrNull() ?: emptyList()

                    val events = mutableListOf<CustomerTimelineEvent>()

                    // Account created
                    events.add(
                        CustomerTimelineEvent(
                            id = "created_${customer.phone}",
                            type = CustomerTimelineEventType.ACCOUNT_CREATED,
                            title = "Customer Registered",
                            description = "Registered as ${customer.tag} account",
                            timestamp = customer.createdAt
                        )
                    )

                    // Sales and Dues
                    sales.forEach { saleWithItems ->
                        val s = saleWithItems.sale
                        events.add(
                            CustomerTimelineEvent(
                                id = "sale_${s.id}",
                                type = CustomerTimelineEventType.SALE,
                                title = "Invoice #${s.id}",
                                description = "${s.paymentMethod} • ${saleWithItems.items.size} items",
                                amount = s.grandTotal,
                                timestamp = s.timestamp,
                                referenceId = s.id.toString()
                            )
                        )
                        if (s.grandTotal > s.amountPaid) {
                            events.add(
                                CustomerTimelineEvent(
                                    id = "due_${s.id}",
                                    type = CustomerTimelineEventType.DUE_CREATED,
                                    title = "Due Created: #${s.id}",
                                    description = "Unpaid credit balance",
                                    amount = s.grandTotal - s.amountPaid,
                                    timestamp = s.timestamp + 1,
                                    referenceId = s.id.toString()
                                )
                            )
                        }
                    }

                    // Payments
                    duePayments.forEach { p ->
                        events.add(
                            CustomerTimelineEvent(
                                id = "payment_${p.id}",
                                type = CustomerTimelineEventType.PAYMENT_RECEIVED,
                                title = "Repayment: ${p.paymentMethod}",
                                description = p.referenceNote?.takeIf { it.isNotBlank() } ?: "Due payment received",
                                amount = p.amountPaid,
                                timestamp = p.timestamp,
                                referenceId = p.id.toString()
                            )
                        )
                    }

                    // Loyalty
                    loyaltyTransactions.forEach { l ->
                        events.add(
                            CustomerTimelineEvent(
                                id = "loyalty_${l.id}",
                                type = CustomerTimelineEventType.LOYALTY,
                                title = l.reason.ifBlank { "Loyalty Points" },
                                description = if (l.pointsChange >= 0) "+${l.pointsChange} pts" else "${l.pointsChange} pts",
                                timestamp = l.timestamp,
                                referenceId = l.id.toString()
                            )
                        )
                    }

                    // Notes
                    customer.notes?.takeIf { it.isNotBlank() }?.split("\n\n")?.forEachIndexed { idx, noteBlock ->
                        events.add(
                            CustomerTimelineEvent(
                                id = "note_${customer.phone}_$idx",
                                type = CustomerTimelineEventType.NOTE,
                                title = "CRM Note",
                                description = noteBlock.trim(),
                                timestamp = customer.updatedAt - (idx * 60000L)
                            )
                        )
                    }

                    val storeCreditLedger = repository.getCustomerStoreCreditLedgerOnce(phone)

                    // Credit ledger events
                    storeCreditLedger.forEach { c ->
                        events.add(
                            CustomerTimelineEvent(
                                id = "credit_${c.id}",
                                type = CustomerTimelineEventType.NOTE,
                                title = if (c.deltaAmount >= 0) "Store Credit: +৳${c.deltaAmount}" else "Credit Redeemed: -৳${-c.deltaAmount}",
                                description = c.note ?: c.type,
                                amount = c.deltaAmount,
                                timestamp = c.timestamp,
                                referenceId = c.referenceInvoiceId?.toString() ?: c.voucherCode
                            )
                        )
                    }

                    _selectedCustomerDossier.value = CustomerDossierData(
                        customer = customer,
                        topProducts = topProducts,
                        sales = sales,
                        duePayments = duePayments,
                        loyaltyTransactions = loyaltyTransactions,
                        timelineEvents = events.sortedByDescending { it.timestamp },
                        storeCreditLedger = storeCreditLedger
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingDossier.value = false
            }
        }
    }

    fun getStoreCreditLedger(phone: String): Flow<List<StoreCreditLedgerEntry>> =
        repository.getCustomerStoreCreditLedger(phone)

    fun adjustCustomerStoreCredit(
        phone: String,
        delta: Double,
        note: String? = null,
        onSuccess: (Customer) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val updated = repository.adjustCustomerCredit(phone, delta, note)
                loadCustomerDossier(phone)
                onSuccess(updated)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Failed to adjust store credit")
            }
        }
    }

    fun clearDossier() {
        _selectedCustomerDossier.value = null
    }

    fun upsertCustomer(
        phone: String,
        name: String,
        email: String? = null,
        address: String? = null,
        notes: String? = null,
        tag: String = "Retail",
        creditLimit: Double = 0.0,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanPhone = phone.trim()
                val cleanName = name.trim()
                if (cleanPhone.isBlank()) {
                    onError("Phone number cannot be empty")
                    return@launch
                }
                if (cleanName.isBlank()) {
                    onError("Customer name cannot be empty")
                    return@launch
                }
                val existing = repository.getCustomerByPhone(cleanPhone)
                val customer = (existing ?: Customer(phone = cleanPhone, name = cleanName)).copy(
                    phone = cleanPhone,
                    name = cleanName,
                    email = email?.trim()?.takeIf { it.isNotBlank() },
                    address = address?.trim()?.takeIf { it.isNotBlank() },
                    notes = notes?.trim()?.takeIf { it.isNotBlank() },
                    tag = tag,
                    creditLimit = creditLimit.coerceAtLeast(0.0),
                    updatedAt = System.currentTimeMillis()
                )
                repository.upsertCustomer(customer)
                if (_selectedCustomerDossier.value?.customer?.phone == cleanPhone) {
                    loadCustomerDossier(cleanPhone)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to save customer")
            }
        }
    }

    fun archiveCustomer(phone: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.archiveCustomer(phone)
                clearDossier()
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to delete customer")
            }
        }
    }

    suspend fun exportCustomersExcel(outputStream: java.io.OutputStream): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.example.techyfipos.utils.ExcelHelper.exportCustomers(allCustomers.value, outputStream)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}


class POSViewModelFactory(private val repository: DataRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ScannerViewModel::class.java) -> ScannerViewModel(repository) as T
            modelClass.isAssignableFrom(CartViewModel::class.java) -> CartViewModel(repository) as T
            modelClass.isAssignableFrom(ProductsViewModel::class.java) -> ProductsViewModel(repository) as T
            modelClass.isAssignableFrom(SalesViewModel::class.java) -> SalesViewModel(repository) as T
            modelClass.isAssignableFrom(DuesViewModel::class.java) -> DuesViewModel(repository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(repository) as T
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repository) as T
            modelClass.isAssignableFrom(ExpenseViewModel::class.java) -> ExpenseViewModel(repository) as T
            modelClass.isAssignableFrom(SupplierViewModel::class.java) -> SupplierViewModel(repository) as T
            modelClass.isAssignableFrom(RegisterClosingViewModel::class.java) -> RegisterClosingViewModel(repository) as T
            modelClass.isAssignableFrom(LoyaltyViewModel::class.java) -> LoyaltyViewModel(repository) as T
            modelClass.isAssignableFrom(CustomerViewModel::class.java) -> CustomerViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
