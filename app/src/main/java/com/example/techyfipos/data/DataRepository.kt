package com.example.techyfipos.data

import android.content.Context
import com.example.techyfipos.data.db.*
import com.example.techyfipos.utils.normalizePhone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import android.util.Base64
import androidx.room.immediateTransaction
import androidx.room.support.getSupportWrapper
import androidx.room.useWriterConnection
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.floor
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private fun JSONObject.optionalDouble(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

private fun JSONObject.optionalInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONObject.optionalBoolean(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null

/**
 * Room's Android-only withTransaction extension depends on SupportSQLiteOpenHelper.
 * This app uses BundledSQLiteDriver, so transactions must use the driver connection API.
 * DAO calls made inside this block reuse the transaction connection through Room's
 * coroutine context and are committed or rolled back as one unit.
 */
private suspend fun <T> AppDatabase.withDriverTransaction(block: suspend () -> T): T =
    useWriterConnection { transactor ->
        transactor.immediateTransaction { block() }
    }

enum class BackupImportMode { REPLACE, MERGE }

class DataRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsDataStore: SettingsDataStore
) {
    val appContext: Context get() = context
    fun getLoyaltySettings(): com.example.techyfipos.utils.LoyaltyProgramSettings =
        com.example.techyfipos.utils.LoyaltySettingsHelper.getSettings(context)

    companion object {
        // 1 loyalty point (redeemable 1:1 as taka off a future sale) per 100 taka of grand total.
        private const val LOYALTY_TAKA_PER_POINT = 100.0
        private const val CREDIT_OVERRIDE_VALIDITY_MS = 60_000L
    }

    private val productDao = database.productDao()
    private val saleDao = database.saleDao()
    private val customerDueDao = database.customerDueDao()
    private val userDao = database.userDao()
    private val returnDao = database.returnDao()
    private val heldCartDao = database.heldCartDao()
    private val expenseDao = database.expenseDao()
    private val supplierDao = database.supplierDao()
    private val purchaseInvoiceDao = database.purchaseInvoiceDao()
    private val registerClosingDao = database.registerClosingDao()
    private val customerLoyaltyDao = database.customerLoyaltyDao()
    private val inventoryAdjustmentDao = database.inventoryAdjustmentDao()
    private val scheduledDueReminderDao = database.scheduledDueReminderDao()
    private val loyaltyTransactionDao = database.loyaltyTransactionDao()
    private val registerShiftStateDao = database.registerShiftStateDao()
    private val dueReminderLogDao = database.dueReminderLogDao()
    private val customerDao = database.customerDao()
    private val saleAuditLogDao = database.saleAuditLogDao()

    val allSaleAuditLogs: Flow<List<com.example.techyfipos.data.db.SaleAuditLog>> = saleAuditLogDao.getAllAuditLogs()

    val allScheduledDueReminders: Flow<List<ScheduledDueReminder>> = scheduledDueReminderDao.getAll()

    val allDueReminderLogs: Flow<List<DueReminderLog>> = dueReminderLogDao.getAllLogs()

    suspend fun logDueReminder(log: DueReminderLog): Long = withContext(Dispatchers.IO) {
        dueReminderLogDao.insertLog(log)
    }

    suspend fun deleteDueReminderLog(id: Long) = withContext(Dispatchers.IO) {
        dueReminderLogDao.deleteLogById(id)
    }

    suspend fun clearDueReminderLogs() = withContext(Dispatchers.IO) {
        dueReminderLogDao.clearAllLogs()
    }

    suspend fun cancelScheduledDueReminder(context: Context, phone: String) = withContext(Dispatchers.IO) {
        com.example.techyfipos.utils.DueReminderScheduler.cancelDueReminder(context, phone)
    }

    val allInventoryAdjustments: Flow<List<InventoryAdjustment>> = inventoryAdjustmentDao.getAllAdjustments()

    fun getAdjustmentsForProduct(barcode: String): Flow<List<InventoryAdjustment>> =
        inventoryAdjustmentDao.getAdjustmentsForProduct(barcode)

    suspend fun recordInventoryAdjustment(adjustment: InventoryAdjustment): Long = withContext(Dispatchers.IO) {
        requireOwnerSession()
        inventoryAdjustmentDao.insertAdjustment(adjustment)
    }

    // Current session's signed-in user. This is the single shared DataRepository instance every
    // ViewModel is constructed with, so gating owner-only mutations here (rather than only in the
    // UI's `isOwner` checks) enforces the role regardless of which screen/ViewModel calls in.
    @Volatile private var sessionUser: User? = null
    data class CreditOverrideToken(
        val phone: String,
        val maxAmount: Double = Double.MAX_VALUE,
        val cartHash: String? = null,
        val expiresAt: Long
    )

    @Volatile private var pendingCreditOverride: CreditOverrideToken? = null

    fun setSessionUser(user: User?) {
        sessionUser = user
    }

    val currentSessionUser: User? get() = sessionUser
    val sessionUserName: String get() = sessionUser?.name ?: "Admin"

    fun isSessionOwner(): Boolean = sessionUser?.role == AppRoles.OWNER
    fun isSessionManager(): Boolean = sessionUser?.role == AppRoles.MANAGER
    fun isSessionManagerOrOwner(): Boolean = AppPermissions.isSupervisor(sessionUser)
    fun hasPermission(permissionKey: String): Boolean = AppPermissions.hasPermission(sessionUser, permissionKey)

    class UnauthorizedException(message: String) : Exception(message)

    fun requireOwnerSession() {
        if (!isSessionOwner()) throw UnauthorizedException("Owner privileges required for this action")
    }

    fun requireSupervisorSession() {
        if (!isSessionManagerOrOwner()) throw UnauthorizedException("Supervisor (Manager or Owner) privileges required for this action")
    }

    fun requirePermission(permissionKey: String) {
        if (!hasPermission(permissionKey)) throw UnauthorizedException("Permission '$permissionKey' required for this action")
    }

    /**
     * Verifies an owner or manager PIN without changing the signed-in cashier session, then grants one short-lived
     * scoped override for the specified customer, amount, and cart signature.
     */
    suspend fun authorizeCreditLimitOverride(
        pin: String,
        phoneRaw: String,
        maxAmount: Double = Double.MAX_VALUE,
        cartHash: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val supervisor = verifySupervisorPin(pin) ?: return@withContext false
        val phone = normalizePhone(phoneRaw)
        if (phone.isBlank()) return@withContext false
        synchronized(this@DataRepository) {
            pendingCreditOverride = CreditOverrideToken(
                phone = phone,
                maxAmount = maxAmount,
                cartHash = cartHash,
                expiresAt = System.currentTimeMillis() + CREDIT_OVERRIDE_VALIDITY_MS
            )
        }
        true
    }

    suspend fun verifySupervisorPin(pin: String): User? = withContext(Dispatchers.IO) {
        val user = verifyPin(pin)
        if (user != null && AppPermissions.isSupervisor(user)) {
            user
        } else {
            null
        }
    }

    private fun consumeCreditLimitOverride(
        phone: String,
        requestedAmount: Double = 0.0,
        cartHash: String? = null
    ): Boolean = synchronized(this) {
        val grant = pendingCreditOverride
        val valid = grant != null &&
            grant.phone == phone &&
            grant.expiresAt >= System.currentTimeMillis() &&
            (grant.maxAmount >= requestedAmount - 0.01) &&
            (grant.cartHash == null || cartHash == null || grant.cartHash == cartHash)

        if (grant?.phone == phone || grant?.expiresAt?.let { it < System.currentTimeMillis() } == true) {
            pendingCreditOverride = null
        }
        valid
    }

    // Held Carts
    val allHeldCarts: Flow<List<HeldCart>> = heldCartDao.getAllHeldCarts()
    val heldCartCount: Flow<Int> = heldCartDao.getHeldCartCount()

    suspend fun getHeldCartById(id: Long): HeldCart? = withContext(Dispatchers.IO) {
        heldCartDao.getHeldCartById(id)
    }

    suspend fun insertHeldCart(heldCart: HeldCart): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.SALES_HOLD_CART)
        heldCartDao.insertHeldCart(heldCart)
    }

    suspend fun deleteHeldCart(id: Long) = withContext(Dispatchers.IO) {
        heldCartDao.deleteHeldCartById(id)
    }

    suspend fun clearAllHeldCarts() = withContext(Dispatchers.IO) {
        heldCartDao.clearAllHeldCarts()
    }

    // Products
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    
    fun searchProducts(query: String): Flow<List<Product>> {
        return productDao.searchProducts("%$query%")
    }

    suspend fun getProductByBarcode(barcode: String): Product? = withContext(Dispatchers.IO) {
        productDao.getProductByBarcode(barcode)
    }

    suspend fun insertProduct(product: Product) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        productDao.insertProduct(product)
    }

    suspend fun updateProduct(product: Product) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        productDao.updateProduct(product)
    }

    /** Updates a product and records its inventory audit entry as a single transaction, so a manual stock edit can never leave stock changed without an audit row (or vice versa). */
    suspend fun updateProductWithInventoryAdjustment(product: Product, adjustment: InventoryAdjustment) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        database.withDriverTransaction {
            productDao.updateProduct(product)
            inventoryAdjustmentDao.insertAdjustment(adjustment)
        }
    }

    /** Changes a barcode without exposing the delete/insert gap to callers, optionally recording its inventory audit row in the same transaction. */
    suspend fun replaceProductBarcode(
        oldBarcode: String,
        updatedProduct: Product,
        adjustment: InventoryAdjustment? = null
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        if (oldBarcode != updatedProduct.barcode) requireProductNotInSavedCarts(oldBarcode)
        database.withDriverTransaction {
            if (oldBarcode == updatedProduct.barcode) {
                productDao.updateProduct(updatedProduct)
            } else {
                val oldProduct = productDao.getProductByBarcode(oldBarcode)
                    ?: throw IllegalArgumentException("Product no longer exists")
                require(productDao.getProductByBarcode(updatedProduct.barcode) == null) {
                    "Product barcode already exists"
                }
                productDao.deleteProduct(oldProduct)
                productDao.insertProduct(updatedProduct)
            }
            if (adjustment != null) {
                inventoryAdjustmentDao.insertAdjustment(adjustment)
            }
        }
    }

    /** Re-tags every product carrying [oldCategory] to [newCategory], keeping the category manager's rename in sync with existing inventory. */
    suspend fun renameProductCategory(oldCategory: String, newCategory: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        if (oldCategory.isNotBlank() && oldCategory != newCategory) {
            productDao.renameCategory(oldCategory, newCategory)
        }
    }

    /** Atomically adjusts stock by [delta] in the DB, avoiding read-modify-write races between concurrent calls. */
    /**
     * Adjusts a product's stock and records the audit trail entry in one transaction, so a
     * scanner quick-adjust can never change stock without a matching [InventoryAdjustment] row
     * (or vice versa) even if the process dies partway through.
     */
    suspend fun adjustProductStock(
        barcode: String,
        delta: Double,
        reason: String = "Scanner Adjustment",
        note: String? = null
    ): Product? = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADJUST_STOCK)
        database.withDriverTransaction {
            val before = productDao.getProductByBarcode(barcode) ?: return@withDriverTransaction null
            productDao.adjustStockQty(barcode, delta)
            val after = productDao.getProductByBarcode(barcode) ?: return@withDriverTransaction null
            if (after.stockQty != before.stockQty) {
                inventoryAdjustmentDao.insertAdjustment(
                    InventoryAdjustment(
                        productBarcode = after.barcode,
                        productName = after.name,
                        previousStock = before.stockQty,
                        newStock = after.stockQty,
                        difference = after.stockQty - before.stockQty,
                        reason = reason,
                        note = note,
                        cashierName = sessionUser?.name ?: "Admin"
                    )
                )
            }
            after
        }
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_DELETE)
        requireProductNotInSavedCarts(product.barcode)
        productDao.deleteProduct(product)
    }

    // Sales
    val allSales: Flow<List<Sale>> = saleDao.getAllSales()
    val allSalesWithItems: Flow<List<SaleWithItems>> = saleDao.getAllSalesWithItems()

    suspend fun getItemsForSale(saleId: Long): List<SaleItem> = withContext(Dispatchers.IO) {
        saleDao.getItemsForSale(saleId)
    }

    /** The portion of a sale's grand total that was placed on a customer's due/credit balance. */
    private fun calculateDueAddition(
        paymentMethod: String,
        grandTotal: Double,
        amountPaid: Double,
        splitPaymentJson: String?
    ): Double {
        var dueAddition = 0.0
        if (paymentMethod == "Due") {
            dueAddition = (grandTotal - amountPaid).coerceAtLeast(0.0)
        } else if (paymentMethod == "Split" && !splitPaymentJson.isNullOrBlank()) {
            try {
                val array = JSONArray(splitPaymentJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optString("method", "").equals("Due", ignoreCase = true)) {
                        dueAddition += obj.optDouble("amount", 0.0)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return dueAddition
    }

    private suspend fun reduceCustomerDue(phoneRaw: String, amount: Double) {
        if (amount <= 0.0) return
        val phone = normalizePhone(phoneRaw)
        val currentDue = customerDueDao.getCustomerDueByPhone(phone) ?: return
        val updatedDue = (currentDue.outstandingAmount - amount).coerceAtLeast(0.0)
        // Keep the settled row so its repayment ledger is not cascade-deleted.
        customerDueDao.upsertCustomerDue(
            currentDue.copy(outstandingAmount = updatedDue, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteSale(saleId: Long, reason: String = "Sale Cancelled/Deleted") = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.RETURNS_DELETE_SALE)
        database.withDriverTransaction {
            val returnsForSale = returnDao.getReturnsForSaleOnce(saleId)
            if (returnsForSale.isNotEmpty()) {
                throw IllegalStateException("Cannot delete sale #$saleId because it has ${returnsForSale.size} active return(s). Please review return history first.")
            }
            val saleWithItems = saleDao.getSaleWithItemsById(saleId)
            if (saleWithItems != null) {
                val sale = saleWithItems.sale
                saleAuditLogDao.insertAuditLog(
                    com.example.techyfipos.data.db.SaleAuditLog(
                        saleId = saleId,
                        action = "DELETED",
                        reason = reason,
                        grandTotal = sale.grandTotal,
                        paymentMethod = sale.paymentMethod,
                        cashierName = sessionUser?.name ?: "Admin",
                        timestamp = System.currentTimeMillis()
                    )
                )
                val returnedItems = returnDao.getReturnItemsForSale(saleId)
                val returnedByBarcode = returnedItems
                    .groupBy { it.productBarcode }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } }
                saleWithItems.items.groupBy { it.productBarcode }.forEach { (barcode, soldItems) ->
                    val product = productDao.getProductByBarcode(barcode)
                    if (product != null) {
                        val soldQuantity = soldItems.sumOf { it.quantity }
                        val returnedQuantity = returnedByBarcode[barcode] ?: 0.0
                        val netQuantitySold = (soldQuantity - returnedQuantity).coerceAtLeast(0.0)
                        if (netQuantitySold > 0.0) {
                            productDao.adjustStockQty(barcode, netQuantitySold)
                            inventoryAdjustmentDao.insertAdjustment(
                                InventoryAdjustment(
                                    productBarcode = barcode,
                                    productName = product.name,
                                    previousStock = product.stockQty,
                                    newStock = product.stockQty + netQuantitySold,
                                    difference = netQuantitySold,
                                    reason = "Sale Cancelled/Deleted",
                                    note = "Sale #$saleId deleted",
                                    cashierName = sessionUser?.name ?: "Admin"
                                )
                            )
                        }
                    }
                }
                val phone = saleWithItems.sale.customerPhone
                if (!phone.isNullOrBlank() && saleWithItems.sale.grandTotal > 0.0) {
                    val dueForSale = calculateDueAddition(
                        saleWithItems.sale.paymentMethod,
                        saleWithItems.sale.grandTotal,
                        saleWithItems.sale.amountPaid,
                        saleWithItems.sale.splitPaymentJson
                    )
                    val dueAlreadyReconciled = returnDao.getReturnsForSaleOnce(saleId)
                        .sumOf { it.dueAdjustmentAmount }
                    reduceCustomerDue(phone, (dueForSale - dueAlreadyReconciled).coerceAtLeast(0.0))

                    val alreadyRefunded = returnDao.getRefundTotalForSale(saleId)
                    val remainingSpend = (saleWithItems.sale.grandTotal - alreadyRefunded).coerceAtLeast(0.0)
                    val takaPerPt = getLoyaltySettings().takaPerPoint.takeIf { it > 0.0 } ?: LOYALTY_TAKA_PER_POINT
                    val reversedPoints = floor(remainingSpend / takaPerPt).toInt()
                    customerLoyaltyDao.reverseEarnedPoints(
                        normalizePhone(phone),
                        reversedPoints,
                        remainingSpend,
                        System.currentTimeMillis()
                    )
                    loyaltyTransactionDao.insert(LoyaltyTransaction(
                        customerPhone = normalizePhone(phone), pointsChange = -reversedPoints,
                        spendChange = -remainingSpend, type = "SALE_VOID",
                        reason = "Invoice #$saleId deleted", saleId = saleId,
                        performedByName = sessionUser?.name
                    ))
                }
                val loyaltyPhone = saleWithItems.sale.loyaltyPhone
                val loyaltyPoints = saleWithItems.sale.loyaltyPointsRedeemed
                if (!loyaltyPhone.isNullOrBlank() && loyaltyPoints > 0) {
                    customerLoyaltyDao.restoreRedeemedPoints(
                        loyaltyPhone,
                        loyaltyPoints,
                        System.currentTimeMillis()
                    )
                    loyaltyTransactionDao.insert(LoyaltyTransaction(
                        customerPhone = loyaltyPhone, pointsChange = loyaltyPoints,
                        type = "REDEEM_RESTORE", reason = "Points restored after invoice #$saleId deletion",
                        saleId = saleId, performedByName = sessionUser?.name
                    ))
                }
            }
            // The due amount itself was already reversed above via reduceCustomerDue — this only
            // removes the now-stale ledger entry so Transaction History doesn't keep showing a
            // "Due Sale" charge for a sale that no longer exists.
            customerDueDao.deleteDueChargesBySaleId(saleId)
            val customerPhone = saleWithItems?.sale?.customerPhone
            saleDao.deleteSale(saleId)
            if (!customerPhone.isNullOrBlank()) syncCustomerProfileFromLedgers(customerPhone)
        }
    }

    suspend fun updateCustomerCreditLimit(phoneRaw: String, creditLimit: Double) = withContext(Dispatchers.IO) {
        requireOwnerSession()
        val phone = normalizePhone(phoneRaw)
        val safeLimit = creditLimit.takeIf(Double::isFinite)?.coerceAtLeast(0.0)
            ?: throw IllegalArgumentException("Credit limit must be finite")
        database.withDriverTransaction {
            val timestamp = System.currentTimeMillis()
            customerDueDao.getCustomerDueByPhone(phone)?.let { current ->
                customerDueDao.upsertCustomerDue(current.copy(creditLimit = safeLimit, updatedAt = timestamp))
            }
            customerDao.getCustomerByPhone(phone)?.let { current ->
                customerDao.upsertCustomer(current.copy(creditLimit = safeLimit, updatedAt = timestamp))
            }
        }
    }

    /** Resolves the authoritative due balance plus a credit limit stored in either customer table. */
    suspend fun getCustomerCreditAccount(phoneRaw: String): CustomerDue? = withContext(Dispatchers.IO) {
        val phone = normalizePhone(phoneRaw)
        val due = customerDueDao.getCustomerDueByPhone(phone)
        val customer = customerDao.getCustomerByPhone(phone)
        when {
            due != null -> due.copy(
                customerName = due.customerName.ifBlank { customer?.name ?: "Customer" },
                creditLimit = if (due.creditLimit > 0.0) due.creditLimit else customer?.creditLimit ?: 0.0
            )
            customer != null -> CustomerDue(
                customerPhone = phone,
                customerName = customer.name,
                outstandingAmount = customer.totalDue,
                creditLimit = customer.creditLimit,
                updatedAt = customer.updatedAt
            )
            else -> null
        }
    }

    suspend fun completeSale(
        subtotal: Double,
        discountAmount: Double,
        isPercentage: Boolean,
        grandTotal: Double,
        paymentMethod: String,
        customerName: String?,
        customerPhone: String?,
        amountPaid: Double,
        walletPhone: String?,
        cardType: String?,
        cardLastFour: String?,
        cartItems: List<Pair<Product, Double>>,
        taxRate: Double = 0.0,
        taxAmount: Double = 0.0,
        splitPaymentJson: String? = null,
        loyaltyPhone: String? = null,
        loyaltyPointsToRedeem: Int = 0
    ): Long = completeSaleWithNotes(
        subtotal = subtotal,
        discountAmount = discountAmount,
        isPercentage = isPercentage,
        grandTotal = grandTotal,
        paymentMethod = paymentMethod,
        customerName = customerName,
        customerPhone = customerPhone,
        amountPaid = amountPaid,
        walletPhone = walletPhone,
        cardType = cardType,
        cardLastFour = cardLastFour,
        cartItemsWithNotes = cartItems.map { Triple(it.first, it.second, null) },
        taxRate = taxRate,
        taxAmount = taxAmount,
        splitPaymentJson = splitPaymentJson,
        loyaltyPhone = loyaltyPhone,
        loyaltyPointsToRedeem = loyaltyPointsToRedeem
    )

    suspend fun completeSaleWithNotes(
        subtotal: Double,
        discountAmount: Double,
        isPercentage: Boolean,
        grandTotal: Double,
        paymentMethod: String,
        customerName: String?,
        customerPhone: String?,
        amountPaid: Double,
        walletPhone: String?,
        cardType: String?,
        cardLastFour: String?,
        cartItemsWithNotes: List<Triple<Product, Double, String?>>,
        taxRate: Double = 0.0,
        taxAmount: Double = 0.0,
        splitPaymentJson: String? = null,
        loyaltyPhone: String? = null,
        loyaltyPointsToRedeem: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.SALES_SELL)
        if (discountAmount > 0.0) {
            requirePermission(AppPermissions.SALES_APPLY_DISCOUNT)
        }
        val normalizedCustomerPhone = customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) }
        val normalizedLoyaltyPhone = loyaltyPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) }
        database.withDriverTransaction {
            require(
                subtotal.isFinite() && discountAmount.isFinite() && grandTotal.isFinite() &&
                    amountPaid.isFinite() && taxRate.isFinite() && taxAmount.isFinite()
            ) { "Sale amounts must be finite" }
            require(
                subtotal >= 0.0 && discountAmount >= 0.0 && grandTotal >= 0.0 &&
                    amountPaid >= 0.0 && taxRate >= 0.0 && taxAmount >= 0.0
            ) { "Sale amounts cannot be negative" }
            require(cartItemsWithNotes.isNotEmpty()) { "A sale must contain at least one product" }
            require(loyaltyPointsToRedeem >= 0) { "Loyalty redemption cannot be negative" }
            require(loyaltyPointsToRedeem == 0 || !normalizedLoyaltyPhone.isNullOrBlank()) {
                "A loyalty customer is required to redeem points"
            }
            cartItemsWithNotes.forEach { (product, quantity, _) ->
                require(quantity.isFinite() && quantity > 0.0) { "Sale quantity must be finite and positive" }
                require(product.sellingPrice.isFinite() && product.sellingPrice >= 0.0) {
                    "Sale price must be finite and non-negative"
                }
            }

            // Payment-method-specific completeness checks. The current UI already enforces
            // these client-side, but the repository must not trust a caller (a future screen,
            // a scripted import) to have done so before persisting a sale.
            val splitEntries: List<JSONObject>? = if (paymentMethod == "Split" && !splitPaymentJson.isNullOrBlank()) {
                val array = JSONArray(splitPaymentJson)
                (0 until array.length()).map { array.getJSONObject(it) }
            } else null

            when (paymentMethod) {
                "Due" -> require(customerName?.isNotBlank() == true && !normalizedCustomerPhone.isNullOrBlank()) {
                    "Customer name and phone are required for a Due sale"
                }
                "Store Credit" -> require(customerName?.isNotBlank() == true && !normalizedCustomerPhone.isNullOrBlank()) {
                    "Customer name and phone are required for a Store Credit sale"
                }
                "bKash", "Nagad", "Rocket" -> require(!walletPhone.isNullOrBlank()) {
                    "A wallet number is required for $paymentMethod payments"
                }
                "Card" -> require(!cardLastFour.isNullOrBlank()) {
                    "The card's last 4 digits are required for Card payments"
                }
                "Split" -> {
                    require(splitEntries != null && splitEntries.isNotEmpty()) { "A split sale must list at least one payment row" }
                    val splitTotal = splitEntries!!.sumOf { it.optDouble("amount", 0.0) }
                    require(abs(splitTotal - grandTotal) < 0.01) { "Split payment rows do not add up to the grand total" }
                    splitEntries.forEach { entry ->
                        val method = entry.optString("method", "")
                        val amount = entry.optDouble("amount", 0.0)
                        when (method) {
                            "Due" -> if (amount > 0.0) {
                                require(customerName?.isNotBlank() == true && !normalizedCustomerPhone.isNullOrBlank()) {
                                    "Customer name and phone are required for a Due split row"
                                }
                            }
                            "Store Credit" -> if (amount > 0.0) {
                                require(customerName?.isNotBlank() == true && !normalizedCustomerPhone.isNullOrBlank()) {
                                    "Customer name and phone are required for a Store Credit split row"
                                }
                            }
                            "bKash", "Nagad", "Rocket" -> require(!entry.optString("walletPhone").isNullOrBlank()) {
                                "A wallet number is required for the $method split row"
                            }
                            "Card" -> require(!entry.optString("cardLastFour").isNullOrBlank()) {
                                "The card's last 4 digits are required for the Card split row"
                            }
                        }
                    }
                }
            }
            if (loyaltyPointsToRedeem > 0) {
                val loyaltySettings = getLoyaltySettings()
                require(loyaltyPointsToRedeem >= loyaltySettings.minRedeemPoints) {
                    "Minimum redemption is ${loyaltySettings.minRedeemPoints} points"
                }
                val regularDiscount = if (isPercentage) {
                    subtotal * discountAmount.coerceIn(0.0, 100.0) / 100.0
                } else {
                    discountAmount.coerceAtMost(subtotal)
                }
                val payableBeforeLoyalty = (subtotal - regularDiscount + taxAmount).coerceAtLeast(0.0)
                val ptCashVal = loyaltySettings.pointCashValue.takeIf { it > 0.0 } ?: 1.0
                val loyaltyDiscountAmount = loyaltyPointsToRedeem * ptCashVal
                val maxAllowedPercent = loyaltySettings.maxBillPercentPayable.coerceIn(1, 100) / 100.0
                val maxAllowedDiscount = payableBeforeLoyalty * maxAllowedPercent
                require(loyaltyDiscountAmount <= maxAllowedDiscount + 0.05) {
                    "Loyalty redemption exceeds maximum allowed bill percentage (${loyaltySettings.maxBillPercentPayable}%)"
                }
                val expectedGrandTotal = (payableBeforeLoyalty - loyaltyDiscountAmount).coerceAtLeast(0.0)
                require(abs(grandTotal - expectedGrandTotal) < 0.05) {
                    "Loyalty redemption does not match the sale total"
                }
            }

            // Deduct Store Credit if tender used
            var storeCreditToUse = 0.0
            if (paymentMethod.equals("Store Credit", ignoreCase = true)) {
                storeCreditToUse = grandTotal
            } else if (paymentMethod == "Split" && splitEntries != null) {
                storeCreditToUse = splitEntries.filter { it.optString("method").equals("Store Credit", ignoreCase = true) }
                    .sumOf { it.optDouble("amount", 0.0) }
            }
            if (storeCreditToUse > 0.0) {
                require(!normalizedCustomerPhone.isNullOrBlank()) { "Customer phone number is required for Store Credit payment" }
                val currentCust = customerDao.getCustomerByPhone(normalizedCustomerPhone)
                val availableCredit = currentCust?.storeCredit ?: 0.0
                require(availableCredit >= storeCreditToUse - 0.01) {
                    "Insufficient store credit balance (Available: ৳${availableCredit})"
                }
                customerDao.adjustCustomerStoreCredit(normalizedCustomerPhone, -storeCreditToUse, System.currentTimeMillis())
            }

            val cartHash = cartItemsWithNotes.sortedBy { it.first.barcode }
                .joinToString("|") { "${it.first.barcode}:${it.second}:${it.first.sellingPrice}" }

            val dueAddition = calculateDueAddition(paymentMethod, grandTotal, amountPaid, splitPaymentJson)
            if (dueAddition > 0.0) {
                require(!normalizedCustomerPhone.isNullOrBlank()) {
                    "Customer phone number is required for credit/due sales"
                }
            }
            if (dueAddition > 0.0 && !normalizedCustomerPhone.isNullOrBlank()) {
                val currentDue = customerDueDao.getCustomerDueByPhone(normalizedCustomerPhone)
                val customer = customerDao.getCustomerByPhone(normalizedCustomerPhone)
                val outstanding = currentDue?.outstandingAmount ?: customer?.totalDue ?: 0.0
                val creditLimit = when {
                    (currentDue?.creditLimit ?: 0.0) > 0.0 -> currentDue!!.creditLimit
                    (customer?.creditLimit ?: 0.0) > 0.0 -> customer!!.creditLimit
                    else -> 0.0
                }
                if (creditLimit > 0.0 && outstanding + dueAddition > creditLimit + 0.001) {
                    require(consumeCreditLimitOverride(normalizedCustomerPhone, dueAddition, cartHash)) {
                        "Supervisor authorization is required to exceed the customer's credit limit"
                    }
                }
            }

            // Reserve inventory. Support Negative Inventory Policy setting or Manager elevation:
            val currentSettings = settingsDataStore.invoiceSettingsDataFlow.first()
            val allowNegative = currentSettings.allowNegativeInventory || isSessionManagerOrOwner()
            cartItemsWithNotes
                .filterNot { (product, _, _) -> product.barcode.startsWith("CUSTOM-") || product.barcode.startsWith("ADHOC-") }
                .groupBy { it.first.barcode }
                .forEach { (barcode, lines) ->
                    val requested = lines.sumOf { it.second }
                    if (productDao.decrementStockIfAvailable(barcode, requested) != 1) {
                        require(allowNegative) {
                            "Insufficient stock for ${lines.first().first.name}. Enable 'Allow Negative Stock' or elevate with Manager PIN."
                        }
                        productDao.adjustStockQty(barcode, -requested)
                    }
                }

            val timestamp = System.currentTimeMillis()
            val sale = Sale(
                timestamp = timestamp,
                subtotal = subtotal,
                discountAmount = discountAmount,
                isPercentageDiscount = isPercentage,
                grandTotal = grandTotal,
                paymentMethod = paymentMethod,
                customerName = customerName,
                customerPhone = normalizedCustomerPhone,
                amountPaid = amountPaid,
                walletPhone = walletPhone,
                cardType = cardType,
                cardLastFour = cardLastFour,
                taxRate = taxRate,
                taxAmount = taxAmount,
                splitPaymentJson = splitPaymentJson,
                performedByUserId = sessionUser?.id,
                performedByName = sessionUser?.name,
                loyaltyPhone = normalizedLoyaltyPhone?.takeIf { loyaltyPointsToRedeem > 0 },
                loyaltyPointsRedeemed = loyaltyPointsToRedeem
            )
            val saleId = saleDao.insertSale(sale)
        
            val saleItems = cartItemsWithNotes.map { (product, quantity, note) ->
                SaleItem(
                    saleId = saleId,
                    productBarcode = product.barcode,
                    productName = product.name,
                    price = product.sellingPrice,
                    quantity = quantity,
                    note = note,
                    purchasePriceAtSale = product.purchasePrice
                )
            }
            saleDao.insertSaleItems(saleItems)

        // Handle Due payments/registrations
            if (dueAddition > 0.0 && !normalizedCustomerPhone.isNullOrBlank()) {
                val name = customerName?.takeIf { it.isNotBlank() } ?: "Customer"
                val currentDue = customerDueDao.getCustomerDueByPhone(normalizedCustomerPhone)
                val customer = customerDao.getCustomerByPhone(normalizedCustomerPhone)
                val newOutstanding = (currentDue?.outstandingAmount ?: 0.0) + dueAddition
                customerDueDao.upsertCustomerDue(
                    CustomerDue(
                        customerPhone = normalizedCustomerPhone,
                        customerName = name,
                        outstandingAmount = newOutstanding,
                        creditLimit = when {
                            (currentDue?.creditLimit ?: 0.0) > 0.0 -> currentDue!!.creditLimit
                            else -> customer?.creditLimit ?: 0.0
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                )
                customerDueDao.insertDueCharge(
                    DueCharge(
                        customerPhone = normalizedCustomerPhone,
                        amount = dueAddition,
                        timestamp = timestamp,
                        reason = "Sale",
                        saleId = saleId
                    )
                )
            }

            if (!normalizedLoyaltyPhone.isNullOrBlank() && loyaltyPointsToRedeem > 0) {
                require(
                    customerLoyaltyDao.redeemPoints(
                        normalizedLoyaltyPhone,
                        loyaltyPointsToRedeem,
                        timestamp
                    ) == 1
                ) { "Loyalty balance changed. Review the points and try again." }
                loyaltyTransactionDao.insert(LoyaltyTransaction(
                    customerPhone = normalizedLoyaltyPhone, pointsChange = -loyaltyPointsToRedeem,
                    type = "SALE_REDEEM", reason = "Redeemed on invoice #$saleId", saleId = saleId,
                    timestamp = timestamp, performedByName = sessionUser?.name
                ))
            }

            // Automatic loyalty enrollment and earning: any customer who leaves a phone number
            // at checkout accrues points, rather than only being able to redeem an existing
            // balance that nothing else in the app ever populates.
            if (!normalizedCustomerPhone.isNullOrBlank() && grandTotal > 0.0) {
                val loyaltySettings = getLoyaltySettings()
                val takaPerPt = loyaltySettings.takaPerPoint.takeIf { it > 0.0 } ?: LOYALTY_TAKA_PER_POINT
                val earnedPoints = floor(grandTotal / takaPerPt).toInt()
                if (customerLoyaltyDao.getLoyaltyByPhone(normalizedCustomerPhone) == null) {
                    customerLoyaltyDao.upsertLoyalty(
                        CustomerLoyalty(
                            phone = normalizedCustomerPhone,
                            name = customerName?.takeIf { it.isNotBlank() } ?: "Customer"
                        )
                    )
                }
                customerLoyaltyDao.addPoints(normalizedCustomerPhone, earnedPoints, grandTotal, timestamp)
                loyaltyTransactionDao.insert(LoyaltyTransaction(
                    customerPhone = normalizedCustomerPhone, pointsChange = earnedPoints,
                    spendChange = grandTotal, type = "SALE_EARN", reason = "Earned on invoice #$saleId",
                    saleId = saleId, timestamp = timestamp, performedByName = sessionUser?.name
                ))
            }

            // Sync Customer Profile in customers table
            if (!normalizedCustomerPhone.isNullOrBlank()) {
                val existingCust = customerDao.getCustomerByPhone(normalizedCustomerPhone)
                val resolvedName = customerName?.takeIf { it.isNotBlank() } ?: existingCust?.name ?: "Customer"
                val firstDate = existingCust?.firstPurchaseDate ?: timestamp
                val updatedOrders = (existingCust?.totalOrders ?: 0) + 1
                val updatedLifetime = (existingCust?.lifetimeSpent ?: 0.0) + grandTotal
                val currentDue = customerDueDao.getCustomerDueByPhone(normalizedCustomerPhone)?.outstandingAmount ?: 0.0
                val loyalty = customerLoyaltyDao.getLoyaltyByPhone(normalizedCustomerPhone)

                customerDao.upsertCustomer(
                    Customer(
                        phone = normalizedCustomerPhone,
                        name = resolvedName,
                        email = existingCust?.email,
                        address = existingCust?.address,
                        notes = existingCust?.notes,
                        tag = existingCust?.tag ?: "Retail",
                        creditLimit = existingCust?.creditLimit ?: 0.0,
                        totalOrders = updatedOrders,
                        lifetimeSpent = updatedLifetime,
                        totalDue = currentDue,
                        loyaltyPoints = loyalty?.pointsBalance ?: 0,
                        loyaltyTier = loyalty?.tier ?: "Silver",
                        firstPurchaseDate = firstDate,
                        lastPurchaseDate = timestamp,
                        createdAt = existingCust?.createdAt ?: timestamp,
                        updatedAt = timestamp,
                        isArchived = false
                    )
                )
            }

            saleId
        }
    }

    // Customer CRM & Auto-Fill Resolution
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()

    fun observeCustomerByPhone(phone: String): Flow<Customer?> =
        customerDao.observeCustomerByPhone(normalizePhone(phone))

    suspend fun getCustomerByPhone(phone: String): Customer? = withContext(Dispatchers.IO) {
        customerDao.getCustomerByPhone(normalizePhone(phone))
    }

    fun searchCustomers(query: String): Flow<List<Customer>> =
        customerDao.searchCustomers(query.trim())

    suspend fun resolveCustomerDetails(phone: String): CustomerLookupResult? = withContext(Dispatchers.IO) {
        val cleanPhone = normalizePhone(phone)
        if (cleanPhone.isBlank()) return@withContext null

        val customer = customerDao.getCustomerByPhone(cleanPhone)
        val due = customerDueDao.getCustomerDueByPhone(cleanPhone)
        val loyalty = customerLoyaltyDao.getLoyaltyByPhone(cleanPhone)

        val dueAmount = due?.outstandingAmount ?: customer?.totalDue ?: 0.0
        val creditLimit = due?.creditLimit ?: customer?.creditLimit ?: 0.0
        val loyaltyPoints = loyalty?.pointsBalance ?: customer?.loyaltyPoints ?: 0
        val loyaltyTier = loyalty?.tier ?: customer?.loyaltyTier ?: "Silver"

        // 1. Priority: Customer Database
        if (customer != null && customer.name.isNotBlank()) {
            return@withContext CustomerLookupResult(
                phone = cleanPhone,
                name = customer.name,
                source = "CUSTOMER_DB",
                dueAmount = dueAmount,
                loyaltyPoints = loyaltyPoints,
                tier = loyaltyTier,
                tag = customer.tag,
                creditLimit = creditLimit,
                storeCredit = customer.storeCredit,
                lifetimeSpent = customer.lifetimeSpent,
                totalOrders = customer.totalOrders,
                address = customer.address,
                notes = customer.notes
            )
        }

        // 2. Priority: Customer Due Record
        if (due != null && due.customerName.isNotBlank()) {
            return@withContext CustomerLookupResult(
                phone = cleanPhone,
                name = due.customerName,
                source = "DUE_RECORD",
                dueAmount = dueAmount,
                loyaltyPoints = loyaltyPoints,
                tier = loyaltyTier,
                tag = customer?.tag ?: "Due Customer",
                creditLimit = creditLimit,
                storeCredit = customer?.storeCredit ?: 0.0,
                lifetimeSpent = customer?.lifetimeSpent ?: 0.0,
                totalOrders = customer?.totalOrders ?: 0,
                address = customer?.address,
                notes = customer?.notes
            )
        }

        // 3. Priority: Customer Loyalty Record
        if (loyalty != null && loyalty.name.isNotBlank() && loyalty.name != "Customer") {
            return@withContext CustomerLookupResult(
                phone = cleanPhone,
                name = loyalty.name,
                source = "LOYALTY",
                dueAmount = dueAmount,
                loyaltyPoints = loyaltyPoints,
                tier = loyaltyTier,
                tag = customer?.tag ?: "Loyalty Member",
                creditLimit = creditLimit,
                storeCredit = customer?.storeCredit ?: 0.0,
                lifetimeSpent = loyalty.lifetimeSpent,
                totalOrders = customer?.totalOrders ?: 0,
                address = customer?.address,
                notes = customer?.notes
            )
        }

        // 4. Priority: Past Sales History
        val pastName = saleDao.getLatestCustomerNameByPhone(cleanPhone)
        if (!pastName.isNullOrBlank()) {
            return@withContext CustomerLookupResult(
                phone = cleanPhone,
                name = pastName,
                source = "PAST_SALE",
                dueAmount = dueAmount,
                loyaltyPoints = loyaltyPoints,
                tier = loyaltyTier,
                tag = customer?.tag ?: "Repeat Customer",
                creditLimit = creditLimit,
                storeCredit = customer?.storeCredit ?: 0.0,
                lifetimeSpent = customer?.lifetimeSpent ?: 0.0,
                totalOrders = customer?.totalOrders ?: 1,
                address = customer?.address,
                notes = customer?.notes
            )
        }

        // Return partial info if any due/loyalty exists even without custom name
        if (due != null || loyalty != null) {
            return@withContext CustomerLookupResult(
                phone = cleanPhone,
                name = due?.customerName ?: loyalty?.name ?: "",
                source = if (due != null) "DUE_RECORD" else "LOYALTY",
                dueAmount = dueAmount,
                loyaltyPoints = loyaltyPoints,
                tier = loyaltyTier,
                tag = customer?.tag,
                creditLimit = creditLimit,
                storeCredit = customer?.storeCredit ?: 0.0,
                lifetimeSpent = loyalty?.lifetimeSpent ?: customer?.lifetimeSpent ?: 0.0,
                totalOrders = customer?.totalOrders ?: 0,
                address = customer?.address,
                notes = customer?.notes
            )
        }

        null
    }

    suspend fun searchCustomerSuggestions(query: String): List<CustomerLookupResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val customers = customerDao.searchCustomerSuggestions(clean)
        if (customers.isNotEmpty()) {
            return@withContext customers.map { c ->
                val due = customerDueDao.getCustomerDueByPhone(c.phone)
                val loyalty = customerLoyaltyDao.getLoyaltyByPhone(c.phone)
                CustomerLookupResult(
                    phone = c.phone,
                    name = c.name,
                    source = "CUSTOMER_DB",
                    dueAmount = due?.outstandingAmount ?: c.totalDue,
                    loyaltyPoints = loyalty?.pointsBalance ?: c.loyaltyPoints,
                    tier = loyalty?.tier ?: c.loyaltyTier,
                    tag = c.tag,
                    creditLimit = due?.creditLimit ?: c.creditLimit,
                    storeCredit = c.storeCredit,
                    lifetimeSpent = c.lifetimeSpent,
                    totalOrders = c.totalOrders,
                    address = c.address,
                    notes = c.notes
                )
            }
        }
        val salesContacts = saleDao.getAllPastCustomerContacts()
            .filter { it.customerPhone.contains(clean) || (it.customerName?.contains(clean, ignoreCase = true) == true) }
            .take(5)
        salesContacts.map { sc ->
            val due = customerDueDao.getCustomerDueByPhone(sc.customerPhone)
            val loyalty = customerLoyaltyDao.getLoyaltyByPhone(sc.customerPhone)
            CustomerLookupResult(
                phone = sc.customerPhone,
                name = sc.customerName ?: "",
                source = "PAST_SALE",
                dueAmount = due?.outstandingAmount ?: 0.0,
                loyaltyPoints = loyalty?.pointsBalance ?: 0,
                tier = loyalty?.tier ?: "Silver",
                tag = "Past Customer"
            )
        }
    }

    suspend fun upsertCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        val cleanPhone = normalizePhone(customer.phone)
        database.withDriverTransaction {
            val timestamp = System.currentTimeMillis()
            customerDao.upsertCustomer(customer.copy(phone = cleanPhone, updatedAt = timestamp))
            customerDueDao.getCustomerDueByPhone(cleanPhone)?.let { due ->
                customerDueDao.upsertCustomerDue(
                    due.copy(
                        customerName = customer.name.trim().ifBlank { due.customerName },
                        creditLimit = customer.creditLimit.coerceAtLeast(0.0),
                        updatedAt = timestamp
                    )
                )
            }
        }
    }

    suspend fun archiveCustomer(phone: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        customerDao.archiveCustomer(normalizePhone(phone), System.currentTimeMillis())
    }

    suspend fun deleteCustomerPermanently(phone: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        val normalized = normalizePhone(phone)
        database.withDriverTransaction {
            customerDao.deleteCustomerPermanently(normalized)
            customerDueDao.deleteCustomerDue(normalized)
            customerLoyaltyDao.delete(normalized)
            scheduledDueReminderDao.deleteByPhone(normalized)
        }
    }

    suspend fun mergeCustomers(masterPhone: String, secondaryPhone: String): Boolean = withContext(Dispatchers.IO) {
        val masterNorm = normalizePhone(masterPhone)
        val secNorm = normalizePhone(secondaryPhone)
        if (masterNorm == secNorm) return@withContext false

        database.withDriverTransaction {
            val master = customerDao.getCustomerByPhone(masterNorm) ?: return@withDriverTransaction
            val secondary = customerDao.getCustomerByPhone(secNorm) ?: return@withDriverTransaction

            // 1. Reassign sales
            saleDao.reassignCustomerForSales(secNorm, masterNorm, master.name)

            // 2. Reassign dues, charges and payments
            customerDueDao.reassignDueChargesPhone(secNorm, masterNorm)
            customerDueDao.reassignDuePaymentsPhone(secNorm, masterNorm)
            val secDue = customerDueDao.getCustomerDueByPhone(secNorm)
            if (secDue != null) {
                val masterDue = customerDueDao.getCustomerDueByPhone(masterNorm)
                val newDueAmount = (masterDue?.outstandingAmount ?: 0.0) + secDue.outstandingAmount
                val newCreditLimit = maxOf(masterDue?.creditLimit ?: 0.0, secDue.creditLimit)
                customerDueDao.upsertCustomerDue(
                    (masterDue ?: CustomerDue(customerPhone = masterNorm, customerName = master.name, outstandingAmount = 0.0))
                        .copy(outstandingAmount = newDueAmount, creditLimit = newCreditLimit, updatedAt = System.currentTimeMillis())
                )
                customerDueDao.archiveCustomerDue(secNorm, System.currentTimeMillis())
            }

            // 3. Reassign loyalty
            loyaltyTransactionDao.reassignCustomerPhone(secNorm, masterNorm)
            val secLoyalty = customerLoyaltyDao.getLoyaltyByPhone(secNorm)
            if (secLoyalty != null) {
                val masterLoyalty = customerLoyaltyDao.getLoyaltyByPhone(masterNorm)
                val newPoints = (masterLoyalty?.pointsBalance ?: 0) + secLoyalty.pointsBalance
                val newLifetime = (masterLoyalty?.lifetimeSpent ?: 0.0) + secLoyalty.lifetimeSpent
                customerLoyaltyDao.upsertLoyalty(
                    (masterLoyalty ?: CustomerLoyalty(phone = masterNorm, name = master.name))
                        .copy(pointsBalance = newPoints, lifetimeSpent = newLifetime, updatedAt = System.currentTimeMillis())
                )
                customerLoyaltyDao.setActive(secNorm, false, System.currentTimeMillis())
            }

            // 4. Combine customer notes and metrics
            val combinedNotes = listOfNotNull(
                master.notes?.takeIf { it.isNotBlank() },
                secondary.notes?.takeIf { it.isNotBlank() }?.let { "[Merged from ${secondary.phone} (${secondary.name})]: $it" }
            ).joinToString("\n\n")

            val combinedSpent = master.lifetimeSpent + secondary.lifetimeSpent
            val combinedOrders = master.totalOrders + secondary.totalOrders
            val combinedDue = master.totalDue + secondary.totalDue
            val combinedPoints = master.loyaltyPoints + secondary.loyaltyPoints
            val combinedLimit = maxOf(master.creditLimit, secondary.creditLimit)

            val updatedMaster = master.copy(
                notes = combinedNotes.ifBlank { null },
                lifetimeSpent = combinedSpent,
                totalOrders = combinedOrders,
                totalDue = combinedDue,
                creditLimit = combinedLimit,
                loyaltyPoints = combinedPoints,
                updatedAt = System.currentTimeMillis()
            )
            customerDao.upsertCustomer(updatedMaster)

            // 5. Archive secondary customer
            customerDao.archiveCustomer(secNorm, System.currentTimeMillis())
        }
        true
    }

    suspend fun addCustomerNote(phone: String, noteText: String, category: String = "General"): Boolean = withContext(Dispatchers.IO) {
        val cleanPhone = normalizePhone(phone)
        val customer = customerDao.getCustomerByPhone(cleanPhone) ?: return@withContext false
        val timeStr = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val newEntry = "• [$timeStr | $category] ${noteText.trim()}"
        val updatedNotes = if (customer.notes.isNullOrBlank()) newEntry else "$newEntry\n\n${customer.notes}"
        customerDao.upsertCustomer(customer.copy(notes = updatedNotes, updatedAt = System.currentTimeMillis()))
        true
    }

    suspend fun getTopProductsForCustomer(phone: String, limit: Int = 10): List<CustomerProductPurchaseSummary> = withContext(Dispatchers.IO) {
        saleDao.getTopProductsForCustomer(normalizePhone(phone), limit)
    }

    fun getCustomerSalesWithItems(phone: String): Flow<List<SaleWithItems>> =
        saleDao.getSalesWithItemsForCustomer(normalizePhone(phone))

    suspend fun getCustomerSalesWithItemsOnce(phone: String): List<SaleWithItems> = withContext(Dispatchers.IO) {
        saleDao.getSalesWithItemsForCustomerOnce(normalizePhone(phone))
    }

    suspend fun syncCustomerProfileFromLedgers(phoneRaw: String) = withContext(Dispatchers.IO) {
        val phone = normalizePhone(phoneRaw)
        if (phone.isBlank()) return@withContext
        database.withDriverTransaction {
            val sales = saleDao.getSalesWithItemsForCustomerOnce(phone)
            val totalPurchased = sales.sumOf { it.sale.grandTotal }
            val totalReturned = sales.sumOf { returnDao.getRefundTotalForSale(it.sale.id) }
            val lifetimeSpend = (totalPurchased - totalReturned).coerceAtLeast(0.0)
            val orderCount = sales.size
            val lastSaleTimestamp = sales.maxOfOrNull { it.sale.timestamp } ?: 0L
            val firstSaleTimestamp = sales.minOfOrNull { it.sale.timestamp }
            val due = customerDueDao.getCustomerDueByPhone(phone)
            val loyalty = customerLoyaltyDao.getLoyaltyByPhone(phone)
            val existing = customerDao.getCustomerByPhone(phone)

            val name = listOfNotNull(
                existing?.name?.takeIf { it.isNotBlank() },
                due?.customerName?.takeIf { it.isNotBlank() },
                loyalty?.name?.takeIf { it.isNotBlank() },
                sales.firstOrNull()?.sale?.customerName?.takeIf { !it.isNullOrBlank() }
            ).firstOrNull() ?: "Customer"

            val tag = when {
                existing?.tag?.isNotBlank() == true -> existing.tag
                loyalty != null -> "Loyalty ${loyalty.tier}"
                (due?.outstandingAmount ?: 0.0) > 0.0 -> "Due Customer"
                orderCount >= 5 -> "Repeat Customer"
                else -> "Walk-in"
            }

            customerDao.upsertCustomer(
                (existing ?: Customer(phone = phone, name = name)).copy(
                    phone = phone,
                    name = name,
                    lifetimeSpent = lifetimeSpend,
                    totalDue = due?.outstandingAmount ?: 0.0,
                    creditLimit = due?.creditLimit ?: existing?.creditLimit ?: 0.0,
                    totalOrders = orderCount,
                    firstPurchaseDate = firstSaleTimestamp ?: existing?.firstPurchaseDate,
                    lastPurchaseDate = if (lastSaleTimestamp > 0) lastSaleTimestamp else existing?.lastPurchaseDate,
                    loyaltyPoints = loyalty?.pointsBalance ?: 0,
                    loyaltyTier = loyalty?.tier ?: existing?.loyaltyTier ?: "Silver",
                    tag = tag,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // Dues
    val allOutstandingDues: Flow<List<CustomerDue>> = customerDueDao.getAllOutstandingDues()
    val allDues: Flow<List<CustomerDue>> = customerDueDao.getAllDues()
    val allDuePayments: Flow<List<DuePayment>> = customerDueDao.getAllDuePayments()

    fun getPaymentsForCustomer(phone: String): Flow<List<DuePayment>> {
        return customerDueDao.getPaymentsForCustomer(normalizePhone(phone))
    }

    fun getChargesForCustomer(phone: String): Flow<List<DueCharge>> {
        return customerDueDao.getChargesForCustomer(normalizePhone(phone))
    }

    suspend fun upsertCustomerDue(customerDue: CustomerDue) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        val cleanPhone = normalizePhone(customerDue.customerPhone)
        customerDueDao.upsertCustomerDue(customerDue.copy(customerPhone = cleanPhone))
        val existingCust = customerDao.getCustomerByPhone(cleanPhone)
        if (existingCust != null) {
            customerDao.upsertCustomer(
                existingCust.copy(
                    name = if (customerDue.customerName.isNotBlank()) customerDue.customerName else existingCust.name,
                    totalDue = customerDue.outstandingAmount,
                    creditLimit = customerDue.creditLimit,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            customerDao.upsertCustomer(
                Customer(
                    phone = cleanPhone,
                    name = customerDue.customerName,
                    totalDue = customerDue.outstandingAmount,
                    creditLimit = customerDue.creditLimit,
                    tag = "Due Customer",
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Adds a standalone charge to a customer's due balance (the Add Due dialog) and records it on
     * the debit side of the ledger so it shows up in Transaction History alongside collections —
     * previously only a payment left a trace; an added due only ever bumped the aggregate balance. */
    suspend fun addManualDueCharge(
        phoneRaw: String,
        customerName: String,
        amount: Double,
        creditLimit: Double? = null,
        note: String? = null
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        require(amount.isFinite() && amount > 0.0) { "Due amount must be positive" }
        val cleanPhone = normalizePhone(phoneRaw)
        val cleanName = customerName.trim()
        val timestamp = System.currentTimeMillis()
        database.withDriverTransaction {
            val existing = customerDueDao.getCustomerDueByPhone(cleanPhone)
            val effectiveLimit = creditLimit?.takeIf { it > 0.0 } ?: existing?.creditLimit ?: 0.0
            val existingDue = existing?.outstandingAmount ?: 0.0
            if (effectiveLimit > 0.0 && existingDue + amount > effectiveLimit + 0.001) {
                require(isSessionManagerOrOwner() || consumeCreditLimitOverride(cleanPhone)) {
                    "Supervisor authorization required to exceed customer credit limit"
                }
            }
            val updated = existing?.copy(
                customerName = cleanName.ifBlank { existing.customerName },
                outstandingAmount = existing.outstandingAmount + amount,
                creditLimit = creditLimit?.takeIf { it > 0.0 } ?: existing.creditLimit,
                updatedAt = timestamp
            ) ?: CustomerDue(
                customerPhone = cleanPhone,
                customerName = cleanName,
                outstandingAmount = amount,
                creditLimit = creditLimit?.coerceAtLeast(0.0) ?: 0.0,
                updatedAt = timestamp
            )
            customerDueDao.upsertCustomerDue(updated)
            customerDueDao.insertDueCharge(
                DueCharge(
                    customerPhone = cleanPhone,
                    amount = amount,
                    timestamp = timestamp,
                    reason = "Manual",
                    note = note?.trim()?.takeIf { it.isNotEmpty() }
                )
            )
        }
        syncCustomerProfileFromLedgers(cleanPhone)
    }

    suspend fun recordDuePayment(
        phone: String,
        amountPaid: Double,
        paymentMethod: String = "Cash",
        referenceNote: String? = null
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_COLLECT_PAYMENT)
        val cleanPhone = normalizePhone(phone)
        customerDueDao.recordDuePayment(
            phone = cleanPhone,
            amountPaid = amountPaid,
            timestamp = System.currentTimeMillis(),
            paymentMethod = paymentMethod,
            referenceNote = referenceNote?.trim()?.takeIf { it.isNotEmpty() }
        )
        syncCustomerProfileFromLedgers(cleanPhone)
    }

    /** Reverses a previously recorded collection: restores the amount to the customer's outstanding
     * balance and removes it from history. Owner-only, mirroring the other ledger-altering actions
     * (delete due, delete sale) that are already gated to the owner role. */
    suspend fun deleteDuePayment(paymentId: Long, phoneRaw: String) = withContext(Dispatchers.IO) {
        requireOwnerSession()
        val cleanPhone = normalizePhone(phoneRaw)
        database.withDriverTransaction {
            val payment = customerDueDao.getDuePaymentById(paymentId)
                ?: return@withDriverTransaction
            require(payment.customerPhone == cleanPhone) { "Payment does not belong to this customer" }
            val currentDue = customerDueDao.getCustomerDueByPhone(cleanPhone)
            if (currentDue != null) {
                customerDueDao.upsertCustomerDue(
                    currentDue.copy(
                        outstandingAmount = currentDue.outstandingAmount + payment.amountPaid,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            customerDueDao.deleteDuePaymentById(paymentId)
        }
        syncCustomerProfileFromLedgers(cleanPhone)
    }

    /** Reverses a charge (a Due sale or a manual Add Due entry) — reduces the outstanding balance
     * back and removes it from history. For a sale-linked charge this only undoes the ledger entry
     * and its share of the balance; the sale itself is untouched (use Delete Sale in History for a
     * full undo, which cleans up this same row automatically). Owner-only. */
    suspend fun deleteDueCharge(chargeId: Long, phoneRaw: String) = withContext(Dispatchers.IO) {
        requireOwnerSession()
        val cleanPhone = normalizePhone(phoneRaw)
        database.withDriverTransaction {
            val charge = customerDueDao.getDueChargeById(chargeId)
                ?: return@withDriverTransaction
            require(charge.customerPhone == cleanPhone) { "Charge does not belong to this customer" }
            val currentDue = customerDueDao.getCustomerDueByPhone(cleanPhone)
            if (currentDue != null) {
                customerDueDao.upsertCustomerDue(
                    currentDue.copy(
                        outstandingAmount = (currentDue.outstandingAmount - charge.amount).coerceAtLeast(0.0),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            customerDueDao.deleteDueChargeById(chargeId)
        }
        syncCustomerProfileFromLedgers(cleanPhone)
    }

    suspend fun getCustomerDueByPhone(phone: String): CustomerDue? = withContext(Dispatchers.IO) {
        customerDueDao.getActiveCustomerDueByPhone(normalizePhone(phone))
    }

    suspend fun deleteCustomerDue(phone: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        val normalized = normalizePhone(phone)
        val now = System.currentTimeMillis()
        database.withDriverTransaction {
            require(customerDueDao.archiveCustomerDue(normalized, now) == 1) {
                "Due ledger no longer exists"
            }
            customerDao.updateCustomerTotalDue(normalized, 0.0, now)
        }
    }

    val allDueCharges: Flow<List<DueCharge>> = customerDueDao.getAllDueCharges()

    fun getDueChargesForCustomer(phone: String): Flow<List<DueCharge>> =
        customerDueDao.getChargesForCustomer(normalizePhone(phone))

    suspend fun getDueCount(): Int = withContext(Dispatchers.IO) {
        customerDueDao.getDueCount()
    }

    // Older app versions (and the backup importer, before it was fixed to call normalizePhone)
    // could persist a CustomerDue/DuePayment under the customer's raw, un-normalized phone string
    // (e.g. "+8801670492978") while every current write path keys off normalizePhone() (e.g.
    // "01670492978"). That leaves two ledgers for one real customer: new activity lands on the
    // normalized row while the UI (and the customer's real balance) is stuck on the stale one.
    data class DuplicateDueLedgerGroup(
        val canonicalPhone: String,
        val customerName: String,
        val rows: List<CustomerDue>,
        val mergedOutstanding: Double
    )

    suspend fun previewDuplicatePhoneDueLedgers(): List<DuplicateDueLedgerGroup> = withContext(Dispatchers.IO) {
        customerDueDao.getAllDuesIncludingArchived()
            .groupBy { normalizePhone(it.customerPhone) }
            .filter { (canonicalPhone, rows) -> rows.size > 1 || rows.any { it.customerPhone != canonicalPhone } }
            .map { (canonicalPhone, rows) ->
                DuplicateDueLedgerGroup(
                    canonicalPhone = canonicalPhone,
                    customerName = rows.maxByOrNull { it.updatedAt }?.customerName ?: canonicalPhone,
                    rows = rows,
                    mergedOutstanding = rows.sumOf { it.outstandingAmount }
                )
            }
    }

    /** Merges every duplicate-phone ledger found by [previewDuplicatePhoneDueLedgers] into one row
     * keyed by the normalized phone: outstanding balances are summed, payment history is re-keyed
     * onto the canonical phone (not deleted), and the stale rows are removed. Returns how many
     * customers were merged. */
    suspend fun mergeDuplicatePhoneDueLedgers(): Int = withContext(Dispatchers.IO) {
        requireOwnerSession()
        val groups = previewDuplicatePhoneDueLedgers()
        if (groups.isEmpty()) return@withContext 0
        database.withDriverTransaction {
            groups.forEach { group ->
                val canonicalPhone = group.canonicalPhone
                val latest = group.rows.maxByOrNull { it.updatedAt }!!
                // The canonical row must exist before any due_payments row is re-keyed onto it —
                // due_payments.customerPhone has an ON DELETE CASCADE foreign key into
                // customer_dues.customerPhone, so re-pointing a payment at a phone with no parent
                // row yet trips a foreign-key-constraint failure.
                customerDueDao.upsertCustomerDue(
                    CustomerDue(
                        customerPhone = canonicalPhone,
                        customerName = latest.customerName,
                        outstandingAmount = group.mergedOutstanding,
                        creditLimit = group.rows.maxOf { it.creditLimit },
                        updatedAt = System.currentTimeMillis(),
                        isArchived = group.rows.all { it.isArchived }
                    )
                )
                group.rows.filter { it.customerPhone != canonicalPhone }.forEach { stale ->
                    customerDueDao.reassignDuePaymentsPhone(stale.customerPhone, canonicalPhone)
                    customerDueDao.reassignDueChargesPhone(stale.customerPhone, canonicalPhone)
                    customerDueDao.deleteCustomerDueByPhone(stale.customerPhone)
                }
            }
        }
        groups.forEach { syncCustomerProfileFromLedgers(it.canonicalPhone) }
        groups.size
    }

    /** Removes only the exact demo debts shipped by older builds; modified/customer-owned rows are preserved. */
    suspend fun removeLegacyDemoDuesIfUntouched() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("pos_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("legacy_demo_dues_cleanup_v1", false)) return@withContext
        val candidates = listOf(
            Triple("01711223344", "Rahim Store", Pair(900.0, 300.0)),
            Triple("01899887766", "Karim Traders", Pair(350.0, 500.0))
        )
        candidates.forEach { (phone, name, amounts) ->
            val due = customerDueDao.getCustomerDueByPhone(phone)
            val payments = customerDueDao.getPaymentsForCustomer(phone).first()
            val untouched = due?.customerName == name &&
                kotlin.math.abs(due.outstandingAmount - amounts.first) < 0.001 &&
                payments.size == 1 && kotlin.math.abs(payments.single().amountPaid - amounts.second) < 0.001 &&
                payments.single().paymentMethod == "Cash" && payments.single().referenceNote.isNullOrBlank()
            if (untouched) customerDueDao.deleteCustomerDue(phone)
        }
        prefs.edit().putBoolean("legacy_demo_dues_cleanup_v1", true).apply()
    }

    // Settings
    val languageFlow: Flow<String> = settingsDataStore.languageFlow
    val themeModeFlow: Flow<String> = settingsDataStore.themeModeFlow
    val uiLayoutModeFlow: Flow<String> = settingsDataStore.uiLayoutModeFlow
    val invoiceSettingsDataFlow: Flow<InvoiceSettingsData> = settingsDataStore.invoiceSettingsDataFlow
    val shopNameFlow: Flow<String> = settingsDataStore.shopNameFlow
    val shopAddressFlow: Flow<String> = settingsDataStore.shopAddressFlow
    val shopPhoneFlow: Flow<String> = settingsDataStore.shopPhoneFlow
    val shopLogoUriFlow: Flow<String> = settingsDataStore.shopLogoUriFlow
    val facebookUrlFlow: Flow<String> = settingsDataStore.facebookUrlFlow
    val qrCodeInfoFlow: Flow<String> = settingsDataStore.qrCodeInfoFlow
    val cashierNameFlow: Flow<String> = settingsDataStore.cashierNameFlow
    val autoBackupFlow: Flow<Boolean> = settingsDataStore.autoBackupFlow
    val localAutoBackupFlow: Flow<Boolean> = settingsDataStore.localAutoBackupFlow
    val autoBackupFrequencyFlow: Flow<String> = settingsDataStore.autoBackupFrequencyFlow
    val driveAutoBackupFrequencyFlow: Flow<String> = settingsDataStore.driveAutoBackupFrequencyFlow
    val lastLocalBackupTimestampFlow: Flow<Long> = settingsDataStore.lastLocalBackupTimestampFlow
    val cartSnapshotFlow: Flow<String> = settingsDataStore.cartSnapshotFlow

    suspend fun getCartSnapshot(): String = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, "cart_snapshot.json")
            if (file.exists() && file.length() > 0) {
                file.readText()
            } else {
                settingsDataStore.cartSnapshotFlow.first()
            }
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun saveCartSnapshot(json: String) = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.filesDir, "cart_snapshot.json.tmp")
            val targetFile = File(context.filesDir, "cart_snapshot.json")
            tempFile.writeText(json)
            if (tempFile.exists()) {
                tempFile.renameTo(targetFile)
            }
            settingsDataStore.saveCartSnapshot(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveLanguage(language: String) {
        settingsDataStore.saveLanguage(language)
    }

    suspend fun saveThemeMode(mode: String) {
        settingsDataStore.saveThemeMode(mode)
    }

    suspend fun saveUiLayoutMode(mode: String) {
        settingsDataStore.saveUiLayoutMode(mode)
    }

    suspend fun saveShopInfo(name: String, address: String, phone: String) {
        requirePermission(AppPermissions.SYSTEM_SHOP_SETTINGS)
        settingsDataStore.saveShopInfo(name, address, phone)
    }

    suspend fun saveInvoiceSettings(logoUri: String, facebookUrl: String, qrCodeInfo: String, cashierName: String) {
        requirePermission(AppPermissions.SYSTEM_SHOP_SETTINGS)
        settingsDataStore.saveInvoiceSettings(logoUri, facebookUrl, qrCodeInfo, cashierName)
    }

    suspend fun saveInvoiceSettingsData(settings: InvoiceSettingsData) {
        requirePermission(AppPermissions.SYSTEM_SHOP_SETTINGS)
        settingsDataStore.saveInvoiceSettingsData(settings)
    }

    val driveRetentionCountFlow: Flow<Int> = settingsDataStore.driveRetentionCountFlow
    val driveNetworkPolicyFlow: Flow<String> = settingsDataStore.driveNetworkPolicyFlow
    val driveBackupTimeFlow: Flow<String> = settingsDataStore.driveBackupTimeFlow
    val lastDriveBackupTimestampFlow: Flow<Long> = settingsDataStore.lastDriveBackupTimestampFlow

    suspend fun saveDriveRetentionCount(count: Int) {
        requirePermission(AppPermissions.SYSTEM_BACKUP_RESTORE)
        settingsDataStore.saveDriveRetentionCount(count)
    }

    suspend fun saveDriveNetworkPolicy(policy: String) {
        requirePermission(AppPermissions.SYSTEM_BACKUP_RESTORE)
        settingsDataStore.saveDriveNetworkPolicy(policy)
    }

    suspend fun saveDriveBackupTime(time: String) {
        requirePermission(AppPermissions.SYSTEM_BACKUP_RESTORE)
        settingsDataStore.saveDriveBackupTime(time)
    }

    suspend fun saveLastDriveBackupTimestamp(timestamp: Long) {
        settingsDataStore.saveLastDriveBackupTimestamp(timestamp)
    }

    suspend fun saveAutoBackup(enabled: Boolean, frequency: String) {
        requirePermission(AppPermissions.SYSTEM_BACKUP_RESTORE)
        settingsDataStore.saveAutoBackup(enabled, frequency)
    }

    suspend fun saveLocalAutoBackup(enabled: Boolean, frequency: String) {
        requirePermission(AppPermissions.SYSTEM_BACKUP_RESTORE)
        settingsDataStore.saveLocalAutoBackup(enabled, frequency)
    }

    // Backup & Restore Database as JSON
    suspend fun exportBackup(outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val productsList = productDao.getAllProducts().first()
            val salesList = saleDao.getAllSalesWithItems().first()
            val duesList = customerDueDao.getAllDuesIncludingArchived()
            val returnsList = returnDao.getAllReturns().first()
            val returnItemsList = returnDao.getAllReturnItems().first()
            val heldCartsList = heldCartDao.getAllHeldCarts().first()
            val expensesList = expenseDao.getAllExpenses().first()
            val suppliersList = supplierDao.getAllSuppliers().first()
            val purchaseInvoicesList = purchaseInvoiceDao.getAllPurchaseInvoices().first()
            val purchaseInvoiceItemsList = purchaseInvoiceDao.getAllPurchaseInvoiceItems().first()
            val supplierPaymentsList = supplierDao.getAllSupplierPayments().first()
            val registerClosingsList = registerClosingDao.getAllClosings().first()
            val loyaltyList = customerLoyaltyDao.getAllIncludingInactive()
            val loyaltyTransactionsList = loyaltyTransactionDao.getAll().first()
            val registerShiftState = registerShiftStateDao.get()
            val inventoryAdjustmentsList = inventoryAdjustmentDao.getAllAdjustments().first()
            val scheduledRemindersList = scheduledDueReminderDao.getAll().first()
            val dueReminderLogsList = dueReminderLogDao.getAllLogs().first()
            val customersList = customerDao.getAllCustomers().first()
            val quickSalePresetsList = settingsDataStore.quickSalePresetsFlow.first()
            val notePresetsList = settingsDataStore.notePresetsFlow.first()
            val dueTemplatesList = settingsDataStore.dueReminderTemplatesFlow.first()
            val categoriesList = settingsDataStore.categoriesFlow.first()

            val backupJson = JSONObject()
            backupJson.put("schemaVersion", 6)
            backupJson.put("createdAt", System.currentTimeMillis())

            // Products JSON
            val productsArray = JSONArray()
            for (p in productsList) {
                productsArray.put(JSONObject().apply {
                    put("barcode", p.barcode)
                    put("name", p.name)
                    put("category", p.category)
                    put("purchasePrice", p.purchasePrice)
                    put("sellingPrice", p.sellingPrice)
                    put("stockQty", p.stockQty)
                    put("reorderLevel", p.reorderLevel)
                    put("imageUri", p.imageUri ?: "")
                    encodeProductImage(p.imageUri)?.let { put("imageBase64", it) }
                    put("mfgOrPkgType", p.mfgOrPkgType ?: "")
                    put("mfgOrPkgDate", p.mfgOrPkgDate ?: "")
                    put("expiryDate", p.expiryDate ?: "")
                    put("unit", p.unit)
                    put("wholesalePrice", p.wholesalePrice)
                    put("mrpPrice", p.mrpPrice)
                    put("discountedPrice", p.discountedPrice)
                })
            }
            backupJson.put("products", productsArray)

            // Sales JSON
            val salesArray = JSONArray()
            for (swi in salesList) {
                val saleJson = JSONObject().apply {
                    put("id", swi.sale.id)
                    put("timestamp", swi.sale.timestamp)
                    put("subtotal", swi.sale.subtotal)
                    put("discountAmount", swi.sale.discountAmount)
                    put("isPercentageDiscount", swi.sale.isPercentageDiscount)
                    put("grandTotal", swi.sale.grandTotal)
                    put("paymentMethod", swi.sale.paymentMethod)
                    put("customerName", swi.sale.customerName ?: "")
                    put("customerPhone", swi.sale.customerPhone ?: "")
                    put("amountPaid", swi.sale.amountPaid)
                    put("walletPhone", swi.sale.walletPhone ?: "")
                    put("cardType", swi.sale.cardType ?: "")
                    put("cardLastFour", swi.sale.cardLastFour ?: "")
                    put("taxRate", swi.sale.taxRate)
                    put("taxAmount", swi.sale.taxAmount)
                    put("splitPaymentJson", swi.sale.splitPaymentJson ?: "")
                    put("performedByUserId", swi.sale.performedByUserId ?: JSONObject.NULL)
                    put("performedByName", swi.sale.performedByName ?: "")
                    put("loyaltyPhone", swi.sale.loyaltyPhone ?: "")
                    put("loyaltyPointsRedeemed", swi.sale.loyaltyPointsRedeemed)
                }
                val itemsArray = JSONArray()
                for (item in swi.items) {
                    itemsArray.put(JSONObject().apply {
                        put("productBarcode", item.productBarcode)
                        put("productName", item.productName)
                        put("price", item.price)
                        put("quantity", item.quantity)
                        put("note", item.note ?: "")
                        item.purchasePriceAtSale?.let { put("purchasePriceAtSale", it) }
                    })
                }
                saleJson.put("items", itemsArray)
                salesArray.put(saleJson)
            }
            backupJson.put("sales", salesArray)

            // Dues JSON
            val duesArray = JSONArray()
            for (due in duesList) {
                duesArray.put(JSONObject().apply {
                    put("customerPhone", due.customerPhone)
                    put("customerName", due.customerName)
                    put("outstandingAmount", due.outstandingAmount)
                    put("isArchived", due.isArchived)
                    put("creditLimit", due.creditLimit)
                    put("updatedAt", due.updatedAt)
                })
            }
            backupJson.put("dues", duesArray)

            // Due Payments JSON
            val duePaymentsList = customerDueDao.getAllDuePayments().first()
            val duePaymentsArray = JSONArray()
            for (p in duePaymentsList) {
                duePaymentsArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("customerPhone", p.customerPhone)
                    put("amountPaid", p.amountPaid)
                    put("timestamp", p.timestamp)
                    put("paymentMethod", p.paymentMethod)
                    put("referenceNote", p.referenceNote ?: "")
                })
            }
            backupJson.put("duePayments", duePaymentsArray)

            // Due Charges JSON (debit side of the ledger — see DueCharge)
            val dueChargesList = customerDueDao.getAllDueCharges().first()
            val dueChargesArray = JSONArray()
            for (c in dueChargesList) {
                dueChargesArray.put(JSONObject().apply {
                    put("id", c.id)
                    put("customerPhone", c.customerPhone)
                    put("amount", c.amount)
                    put("timestamp", c.timestamp)
                    put("reason", c.reason)
                    put("saleId", c.saleId ?: JSONObject.NULL)
                    put("note", c.note ?: "")
                })
            }
            backupJson.put("dueCharges", dueChargesArray)

            // Authentication secrets are deliberately excluded from portable JSON. Four-digit
            // PIN hashes are low entropy even with PBKDF2 and should never leave app-private storage.
            backupJson.put("returns", JSONArray().apply {
                returnsList.forEach { record -> put(JSONObject().apply {
                    put("id", record.id); put("saleId", record.saleId); put("timestamp", record.timestamp)
                    put("refundMethod", record.refundMethod); put("refundAmount", record.refundAmount)
                    put("externalRefundAmount", record.externalRefundAmount)
                    put("dueAdjustmentAmount", record.dueAdjustmentAmount)
                    put("note", record.note ?: "")
                    put("performedByUserId", record.performedByUserId ?: JSONObject.NULL)
                    put("performedByName", record.performedByName ?: "")
                }) }
            })
            backupJson.put("returnItems", JSONArray().apply {
                returnItemsList.forEach { item -> put(JSONObject().apply {
                    put("id", item.id); put("returnId", item.returnId); put("productBarcode", item.productBarcode)
                    put("productName", item.productName); put("price", item.price); put("quantity", item.quantity)
                }) }
            })
            backupJson.put("heldCarts", JSONArray().apply {
                heldCartsList.forEach { cart -> put(JSONObject().apply {
                    put("id", cart.id); put("title", cart.title); put("customerName", cart.customerName ?: "")
                    put("customerPhone", cart.customerPhone ?: ""); put("itemsJson", cart.itemsJson)
                    put("discountAmount", cart.discountAmount); put("isPercentageDiscount", cart.isPercentageDiscount)
                    put("timestamp", cart.timestamp); put("note", cart.note ?: "")
                }) }
            })
            backupJson.put("expenses", JSONArray().apply {
                expensesList.forEach { expense -> put(JSONObject().apply {
                    put("id", expense.id); put("title", expense.title); put("category", expense.category)
                    put("type", expense.type); put("amount", expense.amount); put("paymentSource", expense.paymentSource)
                    put("note", expense.note ?: ""); put("timestamp", expense.timestamp); put("cashierName", expense.cashierName)
                }) }
            })
            backupJson.put("suppliers", JSONArray().apply {
                suppliersList.forEach { supplier -> put(JSONObject().apply {
                    put("id", supplier.id); put("name", supplier.name); put("companyName", supplier.companyName)
                    put("phone", supplier.phone); put("address", supplier.address ?: "")
                    put("totalPurchased", supplier.totalPurchased); put("totalDue", supplier.totalDue)
                    put("notes", supplier.notes ?: ""); put("updatedAt", supplier.updatedAt)
                }) }
            })
            backupJson.put("purchaseInvoices", JSONArray().apply {
                purchaseInvoicesList.forEach { invoice -> put(JSONObject().apply {
                    put("id", invoice.id); put("supplierId", invoice.supplierId); put("invoiceNumber", invoice.invoiceNumber)
                    put("totalAmount", invoice.totalAmount); put("paidAmount", invoice.paidAmount); put("dueAmount", invoice.dueAmount)
                    put("itemsSummary", invoice.itemsSummary); put("paymentMethod", invoice.paymentMethod)
                    put("timestamp", invoice.timestamp); put("note", invoice.note ?: "")
                }) }
            })
            backupJson.put("purchaseInvoiceItems", JSONArray().apply {
                purchaseInvoiceItemsList.forEach { item -> put(JSONObject().apply {
                    put("id", item.id); put("invoiceId", item.invoiceId)
                    put("productBarcode", item.productBarcode); put("productName", item.productName)
                    put("quantity", item.quantity); put("unitCost", item.unitCost)
                }) }
            })
            backupJson.put("supplierPayments", JSONArray().apply {
                supplierPaymentsList.forEach { payment -> put(JSONObject().apply {
                    put("id", payment.id); put("supplierId", payment.supplierId)
                    put("amountPaid", payment.amountPaid); put("paymentMethod", payment.paymentMethod)
                    put("referenceNote", payment.referenceNote ?: ""); put("timestamp", payment.timestamp)
                    put("performedByName", payment.performedByName ?: "")
                }) }
            })
            backupJson.put("registerClosings", JSONArray().apply {
                registerClosingsList.forEach { closing -> put(JSONObject().apply {
                    put("id", closing.id); put("date", closing.date); put("startingFloat", closing.startingFloat)
                    put("cashSales", closing.cashSales); put("digitalSales", closing.digitalSales); put("cashIn", closing.cashIn)
                    put("cashOut", closing.cashOut); put("expectedCash", closing.expectedCash)
                    put("cashRefunds", closing.cashRefunds); put("shiftStartedAt", closing.shiftStartedAt)
                    put("actualCountedCash", closing.actualCountedCash); put("variance", closing.variance)
                    put("notes", closing.notes ?: ""); put("timestamp", closing.timestamp); put("cashierName", closing.cashierName)
                    put("bkashSales", closing.bkashSales); put("nagadSales", closing.nagadSales)
                    put("rocketSales", closing.rocketSales); put("cardSales", closing.cardSales); put("dueSales", closing.dueSales)
                }) }
            })
            backupJson.put("customerLoyalty", JSONArray().apply {
                loyaltyList.forEach { loyalty -> put(JSONObject().apply {
                    put("phone", loyalty.phone); put("name", loyalty.name); put("pointsBalance", loyalty.pointsBalance)
                    put("tier", loyalty.tier); put("lifetimeSpent", loyalty.lifetimeSpent); put("updatedAt", loyalty.updatedAt)
                    put("isActive", loyalty.isActive)
                }) }
            })
            backupJson.put("loyaltyTransactions", JSONArray().apply {
                loyaltyTransactionsList.forEach { tx -> put(JSONObject().apply {
                    put("id", tx.id); put("customerPhone", tx.customerPhone); put("pointsChange", tx.pointsChange)
                    put("spendChange", tx.spendChange); put("type", tx.type); put("reason", tx.reason)
                    put("saleId", tx.saleId ?: JSONObject.NULL); put("timestamp", tx.timestamp)
                    put("performedByName", tx.performedByName ?: "")
                }) }
            })
            registerShiftState?.let { shift -> backupJson.put("registerShiftState", JSONObject().apply {
                put("startedAt", shift.startedAt); put("startingFloat", shift.startingFloat)
            }) }
            backupJson.put("inventoryAdjustments", JSONArray().apply {
                inventoryAdjustmentsList.forEach { adj -> put(JSONObject().apply {
                    put("id", adj.id); put("productBarcode", adj.productBarcode); put("productName", adj.productName)
                    put("previousStock", adj.previousStock); put("newStock", adj.newStock); put("difference", adj.difference)
                    put("reason", adj.reason); put("note", adj.note ?: ""); put("timestamp", adj.timestamp)
                    put("cashierName", adj.cashierName)
                }) }
            })
            backupJson.put("scheduledDueReminders", JSONArray().apply {
                scheduledRemindersList.forEach { reminder -> put(JSONObject().apply {
                    put("customerPhone", reminder.customerPhone); put("customerName", reminder.customerName)
                    put("dueAmount", reminder.dueAmount); put("channel", reminder.channel)
                    put("scheduledAt", reminder.scheduledAt); put("fireAt", reminder.fireAt)
                    put("messageText", reminder.messageText); put("isRecurring", reminder.isRecurring)
                }) }
            })
            backupJson.put("dueReminderLogs", JSONArray().apply {
                dueReminderLogsList.forEach { log -> put(JSONObject().apply {
                    put("id", log.id); put("customerPhone", log.customerPhone); put("customerName", log.customerName)
                    put("dueAmount", log.dueAmount); put("channel", log.channel); put("messageText", log.messageText)
                    put("timestamp", log.timestamp); put("status", log.status)
                }) }
            })
            backupJson.put("customers", JSONArray().apply {
                customersList.forEach { c -> put(JSONObject().apply {
                    put("phone", c.phone); put("name", c.name); put("email", c.email ?: "")
                    put("address", c.address ?: ""); put("notes", c.notes ?: ""); put("tag", c.tag)
                    put("creditLimit", c.creditLimit); put("totalDue", c.totalDue)
                    put("lifetimeSpent", c.lifetimeSpent); put("totalOrders", c.totalOrders)
                    put("loyaltyPoints", c.loyaltyPoints); put("loyaltyTier", c.loyaltyTier)
                    put("firstPurchaseDate", c.firstPurchaseDate ?: JSONObject.NULL)
                    put("lastPurchaseDate", c.lastPurchaseDate ?: JSONObject.NULL)
                    put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
                    put("isArchived", c.isArchived); put("storeCredit", c.storeCredit)
                }) }
            })
            // Preferences-backed business configuration (not part of the Room database): quick-sale
            // presets, note presets, due-reminder message templates, and custom product categories.
            backupJson.put("quickSalePresets", QuickSalePreset.listToJson(quickSalePresetsList))
            backupJson.put("notePresets", JSONArray().apply { notePresetsList.forEach { put(it) } })
            backupJson.put("dueReminderTemplates", DueTemplate.listToJson(dueTemplatesList))
            backupJson.put("customCategories", CategoryItem.listToJson(categoriesList))

            // Settings & Logo Image JSON
            try {
                val currentSettings = settingsDataStore.invoiceSettingsDataFlow.first()
                val settingsObj = JSONObject(currentSettings.toJson())
                if (currentSettings.logoUri.isNotBlank()) {
                    try {
                        val logoUri = Uri.parse(currentSettings.logoUri)
                        val inputStream = context.contentResolver.openInputStream(logoUri)
                        if (inputStream != null) {
                            val bytes = inputStream.readBytes()
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            settingsObj.put("logoBase64", base64)
                            inputStream.close()
                        } else {
                            val logoFile = File(currentSettings.logoUri)
                            if (logoFile.exists()) {
                                val bytes = logoFile.readBytes()
                                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                settingsObj.put("logoBase64", base64)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                backupJson.put("settings", settingsObj)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            outputFile.writeText(backupJson.toString(2))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackup(
        inputFile: File,
        mode: BackupImportMode = BackupImportMode.REPLACE
    ): Boolean = withContext(Dispatchers.IO) {
        requireOwnerSession()
        try {
            val jsonText = inputFile.readText()
            val backupJson = JSONObject(jsonText)

            if (mode == BackupImportMode.REPLACE) createPortableRestoreSnapshot()

            database.withDriverTransaction {
              if (mode == BackupImportMode.REPLACE) clearBusinessTablesForRestore()
              // Products import
              if (backupJson.has("products")) {
                val productsArray = backupJson.getJSONArray("products")
                for (i in 0 until productsArray.length()) {
                    val p = productsArray.getJSONObject(i)
                    val product = Product(
                        barcode = p.getString("barcode"),
                        name = p.optString("name", p.getString("barcode")),
                        category = p.optString("category", "Uncategorized"),
                        purchasePrice = p.optDouble("purchasePrice", 0.0),
                        sellingPrice = p.optDouble("sellingPrice", p.optDouble("price", 0.0)),
                        stockQty = p.optDouble("stockQty", p.optDouble("stock", 0.0)),
                        reorderLevel = p.optInt("reorderLevel", 5),
                        imageUri = restoreProductImage(p)
                        ,mfgOrPkgType = p.optString("mfgOrPkgType", "Mfg").takeIf { it.isNotBlank() }
                        ,mfgOrPkgDate = p.optString("mfgOrPkgDate").takeIf { it.isNotBlank() }
                        ,expiryDate = p.optString("expiryDate").takeIf { it.isNotBlank() }
                        ,unit = p.optString("unit", "Pcs")
                        ,wholesalePrice = p.optDouble("wholesalePrice", 0.0)
                        ,mrpPrice = p.optDouble("mrpPrice", 0.0)
                        ,discountedPrice = p.optDouble("discountedPrice", 0.0)
                    )
                    val existing = productDao.getProductByBarcode(product.barcode)
                    if (existing != null) {
                        productDao.updateProduct(product)
                    } else {
                        productDao.insertProduct(product)
                    }
                }
              }

              // Dues import
              if (backupJson.has("dues")) {
                val duesArray = backupJson.getJSONArray("dues")
                for (i in 0 until duesArray.length()) {
                    val d = duesArray.getJSONObject(i)
                    val due = CustomerDue(
                        customerPhone = normalizePhone(d.getString("customerPhone")),
                        customerName = d.optString("customerName", d.getString("customerPhone")),
                        outstandingAmount = d.optDouble("outstandingAmount", d.optDouble("amount", 0.0)),
                        creditLimit = d.optDouble("creditLimit", 0.0),
                        updatedAt = d.optLong("updatedAt", System.currentTimeMillis()),
                        isArchived = d.optBoolean("isArchived", false)
                    )
                    customerDueDao.upsertCustomerDue(due)
                }
              }

              // Due Payments import
              if (backupJson.has("duePayments")) {
                val paymentsArray = backupJson.getJSONArray("duePayments")
                for (i in 0 until paymentsArray.length()) {
                    val p = paymentsArray.getJSONObject(i)
                    val payment = DuePayment(
                        id = if (mode == BackupImportMode.MERGE) 0L else p.optLong("id", 0L),
                        customerPhone = normalizePhone(p.getString("customerPhone")),
                        amountPaid = p.optDouble("amountPaid", p.optDouble("amount", 0.0)),
                        timestamp = p.optLong("timestamp", System.currentTimeMillis()),
                        paymentMethod = p.optString("paymentMethod", "Cash"),
                        referenceNote = p.optString("referenceNote").takeIf { it.isNotBlank() }
                    )
                    customerDueDao.insertDuePayment(payment)
                }
              }

              // Sales import. REPLACE mode preserves backup IDs so repeated restores replace the
              // same transactions instead of creating duplicate History rows. MERGE mode forces a
              // fresh auto-generated ID instead and deduplicates existing sales by timestamp & total.
              // Every FK that referenced a sale by its backup ID is remapped to the freshly generated one via saleIdMap.
              val saleIdMap = mutableMapOf<Long, Long>()
              val existingSalesInDb = if (mode == BackupImportMode.MERGE) saleDao.getAllSalesOnce() else emptyList()
              if (backupJson.has("sales")) {
                val salesArray = backupJson.getJSONArray("sales")
                for (i in 0 until salesArray.length()) {
                    val s = salesArray.getJSONObject(i)
                    val backupSaleId = s.optLong("id", 0L)
                    val sTimestamp = s.optLong("timestamp", s.optLong("date", System.currentTimeMillis()))
                    val sGrandTotal = s.optionalDouble("grandTotal") ?: s.optionalDouble("total") ?: 0.0
                    val sCustomerPhone = s.optString("customerPhone")

                    if (mode == BackupImportMode.MERGE) {
                        val duplicate = existingSalesInDb.firstOrNull {
                            it.timestamp == sTimestamp && kotlin.math.abs(it.grandTotal - sGrandTotal) < 0.01 &&
                            (it.customerPhone.orEmpty() == sCustomerPhone)
                        }
                        if (duplicate != null) {
                            saleIdMap[backupSaleId] = duplicate.id
                            continue
                        }
                    }

                    val sale = buildBackwardCompatibleSale(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupSaleId,
                        timestamp = sTimestamp,
                        subtotal = s.optionalDouble("subtotal"),
                        discountAmount = s.optionalDouble("discountAmount"),
                        isPercentageDiscount = s.optionalBoolean("isPercentageDiscount"),
                        grandTotal = s.optionalDouble("grandTotal"),
                        legacyTotal = s.optionalDouble("total"),
                        paymentMethod = s.optString("paymentMethod", s.optString("paymentType", "Cash")),
                        customerName = s.optString("customerName"),
                        customerPhone = s.optString("customerPhone"),
                        amountPaid = s.optionalDouble("amountPaid"),
                        walletPhone = s.optString("walletPhone"),
                        cardType = s.optString("cardType"),
                        cardLastFour = s.optString("cardLastFour"),
                        taxRate = s.optionalDouble("taxRate"),
                        taxAmount = s.optionalDouble("taxAmount"),
                        splitPaymentJson = s.optString("splitPaymentJson"),
                        performedByUserId = s.optLong("performedByUserId").takeIf { s.has("performedByUserId") && !s.isNull("performedByUserId") },
                        performedByName = s.optString("performedByName"),
                        loyaltyPhone = s.optString("loyaltyPhone"),
                        loyaltyPointsRedeemed = s.optInt("loyaltyPointsRedeemed", 0)
                    )
                    val saleId = saleDao.insertSale(sale)
                    if (mode == BackupImportMode.MERGE) saleIdMap[backupSaleId] = saleId
                    val itemsArray = s.optJSONArray("items") ?: s.optJSONArray("saleItems") ?: JSONArray()
                    val items = mutableListOf<SaleItem>()
                    for (j in 0 until itemsArray.length()) {
                        val itemObj = itemsArray.getJSONObject(j)
                        items.add(buildBackwardCompatibleSaleItem(
                            saleId = saleId,
                            productBarcode = itemObj.optString("productBarcode", itemObj.optString("barcode")),
                            productName = itemObj.optString("productName", itemObj.optString("name")),
                            price = itemObj.optionalDouble("price") ?: itemObj.optionalDouble("sellingPrice"),
                            quantity = itemObj.optionalDouble("quantity") ?: itemObj.optionalDouble("qty"),
                            note = itemObj.optString("note").takeIf { it.isNotBlank() },
                            purchasePriceAtSale = itemObj.optionalDouble("purchasePriceAtSale")
                        ))
                    }
                    if (items.isNotEmpty()) saleDao.insertSaleItems(items)
                }
              }

              // Due Charges import (debit side of the ledger — see DueCharge)
              if (backupJson.has("dueCharges")) {
                val chargesArray = backupJson.getJSONArray("dueCharges")
                for (i in 0 until chargesArray.length()) {
                    val c = chargesArray.getJSONObject(i)
                    val backupSaleId = c.optLong("saleId").takeIf { c.has("saleId") && !c.isNull("saleId") }
                    val resolvedSaleId = if (mode == BackupImportMode.MERGE && backupSaleId != null) {
                        saleIdMap[backupSaleId] ?: backupSaleId
                    } else backupSaleId
                    val charge = DueCharge(
                        id = if (mode == BackupImportMode.MERGE) 0L else c.optLong("id", 0L),
                        customerPhone = normalizePhone(c.getString("customerPhone")),
                        amount = c.optDouble("amount", 0.0),
                        timestamp = c.optLong("timestamp", System.currentTimeMillis()),
                        reason = c.optString("reason", "Manual"),
                        saleId = resolvedSaleId,
                        note = c.optString("note").takeIf { it.isNotBlank() }
                    )
                    customerDueDao.insertDueCharge(charge)
                }
              }

              // Legacy JSON may contain users, but PIN hashes are intentionally ignored. The
              // device's existing owner/staff accounts remain authoritative after portable restore.

              // Same MERGE-safety approach as sales above: force a fresh ID and remap referencing
              // FKs (return -> sale, returnItem -> return) via id maps instead of reusing backup IDs.
              val returnIdMap = mutableMapOf<Long, Long>()
              if (backupJson.has("returns")) {
                val array = backupJson.getJSONArray("returns")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupReturnId = value.optLong("id", 0L)
                    val backupSaleId = value.getLong("saleId")
                    val resolvedSaleId = if (mode == BackupImportMode.MERGE) saleIdMap[backupSaleId] ?: backupSaleId else backupSaleId
                    val newReturnId = returnDao.insertReturn(Return(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupReturnId, saleId = resolvedSaleId,
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        refundMethod = value.optString("refundMethod", "Cash"),
                        refundAmount = value.optDouble("refundAmount", 0.0),
                        externalRefundAmount = if (value.has("externalRefundAmount")) {
                            value.optDouble("externalRefundAmount", 0.0)
                        } else value.optDouble("refundAmount", 0.0),
                        dueAdjustmentAmount = value.optDouble("dueAdjustmentAmount", 0.0),
                        note = value.optString("note").takeIf { it.isNotBlank() },
                        performedByUserId = value.optLong("performedByUserId").takeIf { value.has("performedByUserId") && !value.isNull("performedByUserId") },
                        performedByName = value.optString("performedByName").takeIf { it.isNotBlank() }
                    ))
                    if (mode == BackupImportMode.MERGE) returnIdMap[backupReturnId] = newReturnId
                }
              }
              if (backupJson.has("returnItems")) {
                val array = backupJson.getJSONArray("returnItems")
                val records = mutableListOf<ReturnItem>()
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupReturnId = value.getLong("returnId")
                    val resolvedReturnId = if (mode == BackupImportMode.MERGE) returnIdMap[backupReturnId] ?: backupReturnId else backupReturnId
                    records += ReturnItem(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), returnId = resolvedReturnId,
                        productBarcode = value.optString("productBarcode"), productName = value.optString("productName"),
                        price = value.optDouble("price", 0.0), quantity = value.optDouble("quantity", 1.0)
                    )
                }
                if (records.isNotEmpty()) returnDao.insertReturnItems(records)
              }

              if (backupJson.has("heldCarts")) {
                val array = backupJson.getJSONArray("heldCarts")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    heldCartDao.insertHeldCart(HeldCart(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), title = value.optString("title", "Held cart"),
                        customerName = value.optString("customerName").takeIf { it.isNotBlank() },
                        customerPhone = value.optString("customerPhone").takeIf { it.isNotBlank() },
                        itemsJson = value.optString("itemsJson", "[]"),
                        discountAmount = value.optDouble("discountAmount", 0.0),
                        isPercentageDiscount = value.optBoolean("isPercentageDiscount", false),
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        note = value.optString("note").takeIf { it.isNotBlank() }
                    ))
                }
              }

              if (backupJson.has("expenses")) {
                val array = backupJson.getJSONArray("expenses")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    expenseDao.insertExpense(Expense(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), title = value.optString("title", "Expense"),
                        category = value.optString("category", "Other"), type = value.optString("type", "EXPENSE"),
                        amount = value.optDouble("amount", 0.0), paymentSource = value.optString("paymentSource", "Cash"),
                        note = value.optString("note").takeIf { it.isNotBlank() },
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        cashierName = value.optString("cashierName", "Admin")
                    ))
                }
              }

              // Suppliers -> purchase invoices/payments follow the same id-map remap pattern as
              // sales/returns above.
              val supplierIdMap = mutableMapOf<Long, Long>()
              if (backupJson.has("suppliers")) {
                val array = backupJson.getJSONArray("suppliers")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupSupplierId = value.optLong("id", 0L)
                    val newSupplierId = supplierDao.upsertSupplier(Supplier(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupSupplierId, name = value.optString("name", "Supplier"),
                        companyName = value.optString("companyName"), phone = value.optString("phone"),
                        address = value.optString("address").takeIf { it.isNotBlank() },
                        totalPurchased = value.optDouble("totalPurchased", 0.0), totalDue = value.optDouble("totalDue", 0.0),
                        notes = value.optString("notes").takeIf { it.isNotBlank() },
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis())
                    ))
                    if (mode == BackupImportMode.MERGE) supplierIdMap[backupSupplierId] = newSupplierId
                }
              }
              val purchaseInvoiceIdMap = mutableMapOf<Long, Long>()
              if (backupJson.has("purchaseInvoices")) {
                val array = backupJson.getJSONArray("purchaseInvoices")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupInvoiceId = value.optLong("id", 0L)
                    val backupSupplierId = value.getLong("supplierId")
                    val resolvedSupplierId = if (mode == BackupImportMode.MERGE) supplierIdMap[backupSupplierId] ?: backupSupplierId else backupSupplierId
                    val newInvoiceId = purchaseInvoiceDao.insertPurchaseInvoice(PurchaseInvoice(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupInvoiceId, supplierId = resolvedSupplierId,
                        invoiceNumber = value.optString("invoiceNumber"), totalAmount = value.optDouble("totalAmount", 0.0),
                        paidAmount = value.optDouble("paidAmount", 0.0), dueAmount = value.optDouble("dueAmount", 0.0),
                        itemsSummary = value.optString("itemsSummary"), paymentMethod = value.optString("paymentMethod", "Cash"),
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        note = value.optString("note").takeIf { it.isNotBlank() }
                  ))
                    if (mode == BackupImportMode.MERGE) purchaseInvoiceIdMap[backupInvoiceId] = newInvoiceId
                }
              }
              if (backupJson.has("purchaseInvoiceItems")) {
                val array = backupJson.getJSONArray("purchaseInvoiceItems")
                val records = mutableListOf<PurchaseInvoiceItem>()
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupInvoiceId = value.getLong("invoiceId")
                    val resolvedInvoiceId = if (mode == BackupImportMode.MERGE) purchaseInvoiceIdMap[backupInvoiceId] ?: backupInvoiceId else backupInvoiceId
                    records += PurchaseInvoiceItem(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), invoiceId = resolvedInvoiceId,
                        productBarcode = value.optString("productBarcode"),
                        productName = value.optString("productName"),
                        quantity = value.optDouble("quantity", 0.0), unitCost = value.optDouble("unitCost", 0.0)
                    )
                }
                if (records.isNotEmpty()) purchaseInvoiceDao.insertPurchaseInvoiceItems(records)
              }
              if (backupJson.has("supplierPayments")) {
                val array = backupJson.getJSONArray("supplierPayments")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupSupplierId = value.getLong("supplierId")
                    val resolvedSupplierId = if (mode == BackupImportMode.MERGE) supplierIdMap[backupSupplierId] ?: backupSupplierId else backupSupplierId
                    supplierDao.insertSupplierPayment(SupplierPayment(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), supplierId = resolvedSupplierId,
                        amountPaid = value.optDouble("amountPaid", 0.0),
                        paymentMethod = value.optString("paymentMethod", "Cash"),
                        referenceNote = value.optString("referenceNote").takeIf { it.isNotBlank() },
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        performedByName = value.optString("performedByName").takeIf { it.isNotBlank() }
                    ))
                }
              }

              if (backupJson.has("registerClosings")) {
                val array = backupJson.getJSONArray("registerClosings")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    registerClosingDao.insertClosing(RegisterClosing(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), date = value.optString("date"),
                        startingFloat = value.optDouble("startingFloat", 0.0), cashSales = value.optDouble("cashSales", 0.0),
                        digitalSales = value.optDouble("digitalSales", 0.0),
                        bkashSales = value.optDouble("bkashSales", 0.0),
                        nagadSales = value.optDouble("nagadSales", 0.0),
                        rocketSales = value.optDouble("rocketSales", 0.0),
                        cardSales = value.optDouble("cardSales", 0.0),
                        dueSales = value.optDouble("dueSales", 0.0),
                        cashIn = value.optDouble("cashIn", 0.0),
                        cashOut = value.optDouble("cashOut", 0.0), expectedCash = value.optDouble("expectedCash", 0.0),
                        cashRefunds = value.optDouble("cashRefunds", 0.0),
                        shiftStartedAt = value.optLong("shiftStartedAt", 0L),
                        actualCountedCash = value.optDouble("actualCountedCash", 0.0), variance = value.optDouble("variance", 0.0),
                        notes = value.optString("notes").takeIf { it.isNotBlank() },
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        cashierName = value.optString("cashierName", "Admin")
                    ))
                }
              }

              if (backupJson.has("customerLoyalty")) {
                val array = backupJson.getJSONArray("customerLoyalty")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    customerLoyaltyDao.upsertLoyalty(CustomerLoyalty(
                        phone = normalizePhone(value.getString("phone")), name = value.optString("name", "Customer"),
                        pointsBalance = value.optInt("pointsBalance", 0), tier = value.optString("tier", "Silver"),
                        lifetimeSpent = value.optDouble("lifetimeSpent", 0.0),
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis()),
                        isActive = value.optBoolean("isActive", true)
                    ))
                }
              }
              if (backupJson.has("loyaltyTransactions")) {
                val array = backupJson.getJSONArray("loyaltyTransactions")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupSaleId = value.optLong("saleId").takeIf { value.has("saleId") && !value.isNull("saleId") }
                    val resolvedSaleId = if (mode == BackupImportMode.MERGE && backupSaleId != null) {
                        saleIdMap[backupSaleId] ?: backupSaleId
                    } else backupSaleId
                    loyaltyTransactionDao.insert(LoyaltyTransaction(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L),
                        customerPhone = normalizePhone(value.optString("customerPhone")),
                        pointsChange = value.optInt("pointsChange", 0),
                        spendChange = value.optDouble("spendChange", 0.0),
                        type = value.optString("type", "ADJUSTMENT"),
                        reason = value.optString("reason", "Imported adjustment"),
                        saleId = resolvedSaleId,
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        performedByName = value.optString("performedByName").takeIf { it.isNotBlank() }
                    ))
                }
              }
              if (backupJson.has("registerShiftState")) {
                  val value = backupJson.getJSONObject("registerShiftState")
                  registerShiftStateDao.upsert(RegisterShiftState(
                      startedAt = value.optLong("startedAt", System.currentTimeMillis()),
                      startingFloat = value.optDouble("startingFloat", 0.0)
                  ))
              }

              if (backupJson.has("inventoryAdjustments")) {
                val array = backupJson.getJSONArray("inventoryAdjustments")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    inventoryAdjustmentDao.insertAdjustment(InventoryAdjustment(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L),
                        productBarcode = value.optString("productBarcode"), productName = value.optString("productName"),
                        previousStock = value.optDouble("previousStock", 0.0), newStock = value.optDouble("newStock", 0.0),
                        difference = value.optDouble("difference", 0.0), reason = value.optString("reason", "Other"),
                        note = value.optString("note").takeIf { it.isNotBlank() },
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        cashierName = value.optString("cashierName", "Admin")
                    ))
                }
              }
              if (backupJson.has("scheduledDueReminders")) {
                val array = backupJson.getJSONArray("scheduledDueReminders")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    scheduledDueReminderDao.upsert(ScheduledDueReminder(
                        customerPhone = normalizePhone(value.getString("customerPhone")), customerName = value.optString("customerName", "Customer"),
                        dueAmount = value.optDouble("dueAmount", 0.0), channel = value.optString("channel", "both"),
                        scheduledAt = value.optLong("scheduledAt", System.currentTimeMillis()),
                        fireAt = value.optLong("fireAt", System.currentTimeMillis()),
                        messageText = value.optString("messageText", ""),
                        isRecurring = value.optBoolean("isRecurring", false)
                    ))
                }
              }
              if (backupJson.has("dueReminderLogs")) {
                val array = backupJson.getJSONArray("dueReminderLogs")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    dueReminderLogDao.insertLog(DueReminderLog(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L),
                        customerPhone = normalizePhone(value.getString("customerPhone")), customerName = value.optString("customerName", "Customer"),
                        dueAmount = value.optDouble("dueAmount", 0.0), channel = value.optString("channel", "WHATSAPP"),
                        messageText = value.optString("messageText", ""),
                        timestamp = value.optLong("timestamp", System.currentTimeMillis()),
                        status = value.optString("status", "SENT")
                    ))
                }
              }
              if (backupJson.has("customers")) {
                val array = backupJson.getJSONArray("customers")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    customerDao.upsertCustomer(Customer(
                        phone = normalizePhone(value.getString("phone")),
                        name = value.optString("name", "Customer"),
                        email = value.optString("email").takeIf { it.isNotBlank() },
                        address = value.optString("address").takeIf { it.isNotBlank() },
                        notes = value.optString("notes").takeIf { it.isNotBlank() },
                        tag = value.optString("tag", "Retail"),
                        creditLimit = value.optDouble("creditLimit", 0.0),
                        storeCredit = value.optDouble("storeCredit", 0.0),
                        totalOrders = value.optInt("totalOrders", 0),
                        lifetimeSpent = value.optDouble("lifetimeSpent", 0.0),
                        totalDue = value.optDouble("totalDue", 0.0),
                        loyaltyPoints = value.optInt("loyaltyPoints", 0),
                        loyaltyTier = value.optString("loyaltyTier", "Silver"),
                        firstPurchaseDate = value.optLong("firstPurchaseDate").takeIf { value.has("firstPurchaseDate") && !value.isNull("firstPurchaseDate") },
                        lastPurchaseDate = value.optLong("lastPurchaseDate").takeIf { value.has("lastPurchaseDate") && !value.isNull("lastPurchaseDate") },
                        createdAt = value.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis()),
                        isArchived = value.optBoolean("isArchived", false)
                    ))
                }
              }
            }

            // Preferences-backed business configuration import (quick-sale presets, note presets,
            // due-reminder templates, custom categories) — these live in DataStore, not Room, so
            // they're restored outside the database transaction above.
            if (backupJson.has("quickSalePresets")) {
                try {
                    settingsDataStore.saveQuickSalePresets(QuickSalePreset.listFromJson(backupJson.getString("quickSalePresets")))
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("notePresets")) {
                try {
                    val array = backupJson.getJSONArray("notePresets")
                    val presets = (0 until array.length()).map { array.getString(it) }
                    settingsDataStore.saveNotePresets(presets)
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("dueReminderTemplates")) {
                try {
                    settingsDataStore.saveDueReminderTemplates(DueTemplate.listFromJson(backupJson.getString("dueReminderTemplates")))
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("scheduledDueReminders")) {
                try {
                    com.example.techyfipos.utils.DueReminderScheduler.reschedulePersistedReminders(context)
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("customCategories")) {
                try {
                    settingsDataStore.saveCategories(CategoryItem.listFromJson(backupJson.getString("customCategories")))
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Settings import
            if (backupJson.has("settings")) {
                try {
                    val settingsObj = backupJson.getJSONObject("settings")
                    var restoredSettings = InvoiceSettingsData.fromJson(settingsObj.toString())

                    if (settingsObj.has("logoBase64")) {
                        try {
                            val base64Str = settingsObj.getString("logoBase64")
                            if (base64Str.isNotBlank()) {
                                val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
                                val savedLogoFile = File(context.filesDir, "restored_logo.png")
                                savedLogoFile.writeBytes(imageBytes)
                                restoredSettings = restoredSettings.copy(logoUri = Uri.fromFile(savedLogoFile).toString())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    settingsDataStore.saveInvoiceSettingsData(restoredSettings)
                    settingsDataStore.saveShopInfo(restoredSettings.storeName, restoredSettings.address, restoredSettings.phonePrimary)
                    settingsDataStore.saveInvoiceSettings(restoredSettings.logoUri, restoredSettings.facebookUrl, restoredSettings.qrCustomUrl, restoredSettings.cashierName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun upsertProducts(products: List<Product>, presentFields: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        database.withDriverTransaction {
            for (p in products) {
                val existing = productDao.getProductByBarcode(p.barcode)
                if (existing != null) {
                    productDao.updateProduct(
                        p.copy(
                            category = if ("category" in presentFields) p.category else existing.category,
                            purchasePrice = if ("purchasePrice" in presentFields) p.purchasePrice else existing.purchasePrice,
                            stockQty = if ("stockQty" in presentFields) p.stockQty else existing.stockQty,
                            reorderLevel = if ("reorderLevel" in presentFields) p.reorderLevel else existing.reorderLevel,
                            imageUri = if ("imageUri" in presentFields) p.imageUri else existing.imageUri,
                            mfgOrPkgType = if ("mfgOrPkgType" in presentFields) p.mfgOrPkgType else existing.mfgOrPkgType,
                            mfgOrPkgDate = if ("mfgOrPkgDate" in presentFields) p.mfgOrPkgDate else existing.mfgOrPkgDate,
                            expiryDate = if ("expiryDate" in presentFields) p.expiryDate else existing.expiryDate,
                            unit = if ("unit" in presentFields) p.unit else existing.unit,
                            wholesalePrice = if ("wholesalePrice" in presentFields) p.wholesalePrice else existing.wholesalePrice,
                            mrpPrice = if ("mrpPrice" in presentFields) p.mrpPrice else existing.mrpPrice,
                            discountedPrice = if ("discountedPrice" in presentFields) p.discountedPrice else existing.discountedPrice
                        )
                    )
                } else {
                    productDao.insertProduct(p)
                }
            }
        }
    }

    suspend fun bulkUpdateProductPrices(
        barcodes: List<String>,
        percentDelta: Double? = null,
        fixedDelta: Double? = null
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        database.withDriverTransaction {
            for (barcode in barcodes) {
                val p = productDao.getProductByBarcode(barcode) ?: continue
                var newPrice = p.sellingPrice
                if (percentDelta != null) {
                    newPrice = (newPrice * (1.0 + percentDelta / 100.0)).coerceAtLeast(0.0)
                } else if (fixedDelta != null) {
                    newPrice = (newPrice + fixedDelta).coerceAtLeast(0.0)
                }
                productDao.updateProduct(p.copy(sellingPrice = kotlin.math.round(newPrice * 100.0) / 100.0))
            }
        }
    }

    suspend fun bulkUpdateProductCategory(
        barcodes: List<String>,
        newCategory: String
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        database.withDriverTransaction {
            for (barcode in barcodes) {
                val p = productDao.getProductByBarcode(barcode) ?: continue
                productDao.updateProduct(p.copy(category = newCategory))
            }
        }
    }

    suspend fun bulkDeleteProducts(
        barcodes: List<String>
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_DELETE)
        database.withDriverTransaction {
            for (barcode in barcodes) {
                val p = productDao.getProductByBarcode(barcode) ?: continue
                requireProductNotInSavedCarts(barcode)
                productDao.deleteProduct(p)
            }
        }
    }

    suspend fun exportEncryptedLocalBackup(outputFile: File, passphrase: String): Boolean = withContext(Dispatchers.IO) {
        requireOwnerSession()
        require(passphrase.length >= 4) { "Password must be at least 4 characters" }
        try {
            val tempJsonFile = File(context.cacheDir, "backup_plain_${System.currentTimeMillis()}.json")
            val success = exportBackup(tempJsonFile)
            if (!success || !tempJsonFile.exists()) return@withContext false
            val plainBytes = tempJsonFile.readBytes()
            tempJsonFile.delete()

            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            val key = SecretKeySpec(keyBytes, "AES")
            spec.clearPassword()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(plainBytes)

            outputFile.outputStream().use { out ->
                out.write("POSENC1".toByteArray(Charsets.UTF_8))
                out.write(salt)
                out.write(iv)
                out.write(ciphertext)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importEncryptedLocalBackup(
        inputFile: File,
        passphrase: String,
        mode: BackupImportMode = BackupImportMode.REPLACE
    ): Boolean = withContext(Dispatchers.IO) {
        requireOwnerSession()
        try {
            val fileBytes = inputFile.readBytes()
            require(fileBytes.size > 35) { "Invalid backup file size" }
            val magic = String(fileBytes.copyOfRange(0, 7), Charsets.UTF_8)
            require(magic == "POSENC1") { "Not a valid encrypted Techyfi POS backup file" }

            val salt = fileBytes.copyOfRange(7, 23)
            val iv = fileBytes.copyOfRange(23, 35)
            val ciphertext = fileBytes.copyOfRange(35, fileBytes.size)

            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            val key = SecretKeySpec(keyBytes, "AES")
            spec.clearPassword()

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(ciphertext)

            val tempJsonFile = File(context.cacheDir, "backup_dec_${System.currentTimeMillis()}.json")
            tempJsonFile.writeBytes(plainBytes)
            val res = importBackup(tempJsonFile, mode)
            tempJsonFile.delete()
            res
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Users & PIN authentication
    val allUsers: Flow<List<User>> = userDao.getAllUsers()

    private fun hashPin(pin: String): String {
        val iterations = 120_000
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return "pbkdf2\$$iterations\$${Base64.encodeToString(salt, Base64.NO_WRAP)}\$${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }

    private fun legacyHashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun pinMatches(pin: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("pbkdf2$")) {
            return MessageDigest.isEqual(
                legacyHashPin(pin).toByteArray(Charsets.UTF_8),
                storedHash.toByteArray(Charsets.UTF_8)
            )
        }
        return try {
            val parts = storedHash.split('$', limit = 4)
            if (parts.size != 4) return false
            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, expected.size * 8)
            val actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun hasAnyUsers(): Boolean = withContext(Dispatchers.IO) {
        userDao.getUserCount() > 0
    }

    /** Throws if [pin] is already in use by another user, so two accounts can never share a PIN. */
    suspend fun createUser(
        name: String,
        pin: String,
        role: String = AppRoles.CASHIER,
        customPermissions: Set<String>? = null,
        maxDiscount: Double = 10.0
    ): Long = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "User name cannot be blank" }
        require(pin.length == 4 && pin.all(Char::isDigit)) { "PIN must be 4 digits" }
        require(role == AppRoles.OWNER || role == AppRoles.MANAGER || role == AppRoles.CASHIER) { "Unsupported user role: $role" }
        val users = userDao.getAllUsersOnce()
        if (users.isEmpty()) {
            require(role == AppRoles.OWNER) { "The first account must be an owner" }
        } else {
            requireOwnerSession()
        }
        if (role == AppRoles.OWNER && users.any { it.role == AppRoles.OWNER }) {
            throw IllegalStateException("An owner account already exists")
        }
        if (users.any { pinMatches(pin, it.pinHash) }) {
            throw IllegalStateException("PIN already in use by another user")
        }
        val permissionsJson = AppPermissions.serializePermissions(customPermissions)
        userDao.insertUser(
            User(
                name = name.trim(),
                pinHash = hashPin(pin),
                role = role,
                customPermissions = permissionsJson,
                isActive = true,
                maxDiscountPercent = maxDiscount
            )
        )
    }

    /** Called only after Android has confirmed the device owner's lock-screen credential. */
    suspend fun resetOwnerPinAfterDeviceCredential(newPin: String): Boolean = withContext(Dispatchers.IO) {
        if (newPin.length != 4 || !newPin.all(Char::isDigit)) return@withContext false
        val users = userDao.getAllUsersOnce()
        val owner = users.firstOrNull { it.role == AppRoles.OWNER } ?: return@withContext false
        if (users.any { it.id != owner.id && pinMatches(newPin, it.pinHash) }) return@withContext false
        userDao.updatePinHash(owner.id, hashPin(newPin))
        context.getSharedPreferences("auth_security", Context.MODE_PRIVATE).edit().clear().apply()
        true
    }

    /** Returns the matching user for [pin], or null if no user has that PIN or account is inactive. */
    suspend fun verifyPin(pin: String): User? = withContext(Dispatchers.IO) {
        val securityPrefs = context.getSharedPreferences("auth_security", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (securityPrefs.getLong("lock_until", 0L) > now) return@withContext null

        val all = userDao.getAllUsersOnce()
        val matchingUser = all.firstOrNull { pinMatches(pin, it.pinHash) }
        if (matchingUser != null) {
            if (!matchingUser.isActive) {
                return@withContext null
            }
            if (!matchingUser.pinHash.startsWith("pbkdf2$")) {
                userDao.updatePinHash(matchingUser.id, hashPin(pin))
            }
            securityPrefs.edit().clear().apply()
            return@withContext matchingUser
        }

        val failures = securityPrefs.getInt("failed_attempts", 0) + 1
        val editor = securityPrefs.edit().putInt("failed_attempts", failures)
        if (failures >= 5) {
            editor.putLong("lock_until", now + 30_000L).putInt("failed_attempts", 0)
        }
        editor.apply()
        null
    }

    fun loginLockRemainingMillis(): Long {
        val lockUntil = context.getSharedPreferences("auth_security", Context.MODE_PRIVATE)
            .getLong("lock_until", 0L)
        return (lockUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    suspend fun deleteUser(user: User) = withContext(Dispatchers.IO) {
        requireOwnerSession()
        if (user.role == AppRoles.OWNER && userDao.getOwnerCount() <= 1) {
            throw IllegalStateException("The last owner account cannot be deleted")
        }
        userDao.deleteUser(user)
    }

    suspend fun updateStaff(
        userId: Long,
        newName: String,
        newPin: String? = null,
        newRole: String? = null,
        customPermissions: Set<String>? = null,
        isActive: Boolean? = null,
        maxDiscount: Double? = null
    ) = withContext(Dispatchers.IO) {
        requireOwnerSession()
        val all = userDao.getAllUsersOnce()
        val existing = all.firstOrNull { it.id == userId } ?: error("User not found")
        require(newName.isNotBlank()) { "Staff name cannot be blank" }

        val targetRole = newRole ?: existing.role
        require(targetRole == AppRoles.OWNER || targetRole == AppRoles.MANAGER || targetRole == AppRoles.CASHIER) {
            "Unsupported user role: $targetRole"
        }

        if (existing.role == AppRoles.OWNER && targetRole != AppRoles.OWNER && userDao.getOwnerCount() <= 1) {
            throw IllegalStateException("The only owner account cannot be changed to another role")
        }

        val targetActive = isActive ?: existing.isActive
        if (existing.role == AppRoles.OWNER && !targetActive && userDao.getActiveOwnerCount() <= 1) {
            throw IllegalStateException("The only owner account cannot be deactivated")
        }

        val updatedPinHash = if (!newPin.isNullOrBlank()) {
            require(newPin.length == 4 && newPin.all(Char::isDigit)) { "PIN must be 4 digits" }
            require(all.none { it.id != userId && pinMatches(newPin, it.pinHash) }) {
                "PIN already in use by another user"
            }
            hashPin(newPin)
        } else {
            existing.pinHash
        }

        val updatedPermissionsJson = if (customPermissions != null) {
            AppPermissions.serializePermissions(customPermissions)
        } else {
            existing.customPermissions
        }

        userDao.updateUser(
            existing.copy(
                name = newName.trim(),
                pinHash = updatedPinHash,
                role = targetRole,
                customPermissions = updatedPermissionsJson,
                isActive = targetActive,
                maxDiscountPercent = maxDiscount ?: existing.maxDiscountPercent
            )
        )
    }

    suspend fun updateCashier(userId: Long, newName: String, newPin: String? = null) = withContext(Dispatchers.IO) {
        updateStaff(userId = userId, newName = newName, newPin = newPin)
    }

    suspend fun setStaffActive(userId: Long, isActive: Boolean) = withContext(Dispatchers.IO) {
        requireOwnerSession()
        val all = userDao.getAllUsersOnce()
        val existing = all.firstOrNull { it.id == userId } ?: error("User not found")
        if (existing.role == AppRoles.OWNER && !isActive && userDao.getActiveOwnerCount() <= 1) {
            throw IllegalStateException("The only owner account cannot be deactivated")
        }
        userDao.updateActiveStatus(userId, isActive)
    }

    suspend fun updateStaffPermissions(userId: Long, permissions: Set<String>?) = withContext(Dispatchers.IO) {
        requireOwnerSession()
        val permissionsJson = AppPermissions.serializePermissions(permissions)
        userDao.updatePermissions(userId, permissionsJson)
    }

    suspend fun getOwnerCount(): Int = withContext(Dispatchers.IO) {
        userDao.getOwnerCount()
    }

    suspend fun getActiveOwnerCount(): Int = withContext(Dispatchers.IO) {
        userDao.getActiveOwnerCount()
    }

    // Returns & refunds
    val allReturns: Flow<List<Return>> = returnDao.getAllReturns()
    val allReturnItems: Flow<List<ReturnItem>> = returnDao.getAllReturnItems()

    suspend fun getReturnedQuantities(saleId: Long): Map<String, Double> = withContext(Dispatchers.IO) {
        returnDao.getReturnItemsForSale(saleId)
            .groupBy { it.productBarcode }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }
    }

    suspend fun processReturn(
        saleId: Long,
        lines: List<ReturnLine>,
        refundMethod: String,
        note: String?,
        restock: Boolean = true
    ): Double = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.RETURNS_PROCESS)
        if (refundMethod.equals("Cash", ignoreCase = true)) {
            requirePermission(AppPermissions.RETURNS_CASH_REFUND)
        }
        database.withDriverTransaction {
            require(lines.isNotEmpty()) { "Select at least one item to return" }
            val originalSale = saleDao.getSaleWithItemsById(saleId)
                ?: throw IllegalArgumentException("Sale not found")
            val alreadyReturned = returnDao.getReturnItemsForSale(saleId)
                .groupBy { it.productBarcode }
                .mapValues { (_, items) -> items.sumOf { it.quantity } }
            val requestedTotals = lines.groupBy { it.barcode }.mapValues { (_, items) -> items.sumOf { it.quantity } }
            val sanitizedLines = lines.map { requested ->
                val sold = originalSale.items.firstOrNull { it.productBarcode == requested.barcode }
                    ?: throw IllegalArgumentException("Product was not part of this sale")
                val soldQuantity = originalSale.items.filter { it.productBarcode == requested.barcode }.sumOf { it.quantity }
                val remaining = soldQuantity - (alreadyReturned[requested.barcode] ?: 0.0)
                require(requested.quantity > 0.0 && (requestedTotals[requested.barcode] ?: 0.0) <= remaining) {
                    "Return quantity exceeds quantity sold"
                }
                requested.copy(name = sold.productName, price = sold.price)
            }
            // Apportion the sale's discount/tax/loyalty effect proportionally across every line,
            // rather than refunding raw price x quantity, so a returned item never refunds more
            // than the customer actually paid for it.
            val originalSaleForRatio = originalSale.sale
            val paidRatio = if (originalSaleForRatio.subtotal > 0.0) {
                (originalSaleForRatio.grandTotal / originalSaleForRatio.subtotal).coerceAtLeast(0.0)
            } else 1.0
            val refundTotal = sanitizedLines.sumOf { it.price * it.quantity * paidRatio }
            val alreadyRefunded = returnDao.getRefundTotalForSale(saleId)
            val previousReturns = returnDao.getReturnsForSaleOnce(saleId)
            sanitizedLines.forEach { line ->
                val product = productDao.getProductByBarcode(line.barcode)
                if (product != null) {
                    val prevStock = product.stockQty
                    val newStock = if (restock) product.stockQty + line.quantity else prevStock
                    if (restock) {
                        productDao.updateProduct(product.copy(stockQty = newStock))
                    }
                    inventoryAdjustmentDao.insertAdjustment(
                        InventoryAdjustment(
                            productBarcode = product.barcode,
                            productName = product.name,
                            previousStock = prevStock,
                            newStock = newStock,
                            difference = if (restock) line.quantity else 0.0,
                            reason = if (restock) "Return" else "Damaged Write-off",
                            note = if (restock) {
                                "Return for invoice #$saleId${if (!note.isNullOrBlank()) ": $note" else ""}"
                            } else {
                                "Damaged Write-off (not restocked) for invoice #$saleId${if (!note.isNullOrBlank()) ": $note" else ""}"
                            }
                        )
                    )
                }
            }

            val sale = originalSale.sale
            val phone = sale.customerPhone
            if (!phone.isNullOrBlank() && sale.grandTotal > 0.0) {
                val dueForSale = calculateDueAddition(
                    sale.paymentMethod, sale.grandTotal, sale.amountPaid, sale.splitPaymentJson
                )
                val remainingDueForSale = (
                    dueForSale - previousReturns.sumOf { it.dueAdjustmentAmount }
                ).coerceAtLeast(0.0)
                val dueReduction = kotlin.math.min(refundTotal, remainingDueForSale)
                if (dueReduction > 0.0) {
                    reduceCustomerDue(phone, dueReduction)
                    customerDueDao.insertDuePayment(
                        DuePayment(
                            customerPhone = normalizePhone(phone),
                            amountPaid = dueReduction,
                            timestamp = System.currentTimeMillis(),
                            paymentMethod = "Return Adjustment",
                            referenceNote = "Due reduced by return on invoice #$saleId"
                        )
                    )
                }

                if (refundMethod.equals("Store Credit", ignoreCase = true)) {
                    val excessCredit = (refundTotal - dueReduction).coerceAtLeast(0.0)
                    if (excessCredit > 0.0) {
                        customerDao.adjustCustomerStoreCredit(normalizePhone(phone), excessCredit, System.currentTimeMillis())
                    }
                }
                val loyaltySettings = getLoyaltySettings()
                val takaPerPt = loyaltySettings.takaPerPoint.takeIf { it > 0.0 } ?: LOYALTY_TAKA_PER_POINT
                val pointsBefore = floor(
                    (sale.grandTotal - alreadyRefunded).coerceAtLeast(0.0) / takaPerPt
                ).toInt()
                val pointsAfter = floor(
                    (sale.grandTotal - alreadyRefunded - refundTotal).coerceAtLeast(0.0) / takaPerPt
                ).toInt()
                val pointsReversed = (pointsBefore - pointsAfter).coerceAtLeast(0)
                customerLoyaltyDao.reverseEarnedPoints(
                    normalizePhone(phone),
                    pointsReversed,
                    refundTotal,
                    System.currentTimeMillis()
                )
                loyaltyTransactionDao.insert(LoyaltyTransaction(
                    customerPhone = normalizePhone(phone), pointsChange = -pointsReversed,
                    spendChange = -refundTotal, type = "RETURN",
                    reason = "Return for invoice #$saleId", saleId = saleId,
                    performedByName = sessionUser?.name
                ))
            }

            val dueAdj = if (!phone.isNullOrBlank()) kotlin.math.min(
                refundTotal,
                (calculateDueAddition(sale.paymentMethod, sale.grandTotal, sale.amountPaid, sale.splitPaymentJson) -
                    previousReturns.sumOf { it.dueAdjustmentAmount }).coerceAtLeast(0.0)
            ) else 0.0

            val returnRecord = Return(
                saleId = saleId,
                timestamp = System.currentTimeMillis(),
                refundMethod = refundMethod,
                refundAmount = refundTotal,
                externalRefundAmount = (refundTotal - dueAdj).coerceAtLeast(0.0),
                dueAdjustmentAmount = dueAdj,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                performedByUserId = sessionUser?.id,
                performedByName = sessionUser?.name ?: "Admin"
            )
            val returnId = returnDao.insertReturn(returnRecord)
            returnDao.insertReturnItems(
                sanitizedLines.map { line ->
                    ReturnItem(
                        returnId = returnId,
                        productBarcode = line.barcode,
                        productName = line.name,
                        price = line.price,
                        quantity = line.quantity
                    )
                }
            )
            if (!sale.customerPhone.isNullOrBlank()) syncCustomerProfileFromLedgers(sale.customerPhone)
            refundTotal
        }
    }

    // Expenses & Petty Cash
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()

    fun getExpensesBetween(startTime: Long, endTime: Long): Flow<List<Expense>> =
        expenseDao.getExpensesBetween(startTime, endTime)

    suspend fun insertExpense(expense: Expense): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.EXPENSES_MANAGE)
        expenseDao.insertExpense(expense.copy(cashierName = sessionUser?.name ?: expense.cashierName))
    }

    suspend fun updateExpense(expense: Expense) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.EXPENSES_MANAGE)
        expenseDao.updateExpense(expense)
    }

    suspend fun deleteExpense(id: Long) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.EXPENSES_MANAGE)
        expenseDao.deleteExpenseById(id)
    }

    suspend fun getCashExpensesSince(startTime: Long): Double = withContext(Dispatchers.IO) {
        expenseDao.getCashExpensesSince(startTime)
    }

    suspend fun getCashInSince(startTime: Long): Double = withContext(Dispatchers.IO) {
        expenseDao.getCashInSince(startTime)
    }

    // Suppliers & Purchases
    val allSuppliers: Flow<List<Supplier>> = supplierDao.getAllSuppliers()
    val allPurchaseInvoices: Flow<List<PurchaseInvoice>> = purchaseInvoiceDao.getAllPurchaseInvoices()
    val allSupplierPayments: Flow<List<SupplierPayment>> = supplierDao.getAllSupplierPayments()

    suspend fun getSupplierById(id: Long): Supplier? = withContext(Dispatchers.IO) {
        supplierDao.getSupplierById(id)
    }

    suspend fun upsertSupplier(supplier: Supplier): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.SUPPLIERS_MANAGE)
        supplierDao.upsertSupplier(supplier)
    }

    suspend fun deleteSupplier(supplier: Supplier) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.SUPPLIERS_MANAGE)
        supplierDao.deleteSupplier(supplier)
    }

    fun getInvoicesForSupplier(supplierId: Long): Flow<List<PurchaseInvoice>> =
        purchaseInvoiceDao.getInvoicesForSupplier(supplierId)

    suspend fun recordPurchaseInvoice(
        supplierId: Long,
        invoiceNumber: String,
        totalAmount: Double,
        paidAmount: Double,
        itemsSummary: String,
        paymentMethod: String = "Cash",
        note: String? = null,
        stockLines: List<PurchaseStockLine> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.SUPPLIERS_MANAGE)
        require(totalAmount.isFinite() && paidAmount.isFinite()) { "Purchase amounts must be finite" }
        require(totalAmount >= 0.0 && paidAmount >= 0.0 && paidAmount <= totalAmount) {
            "Paid amount must be between zero and the invoice total"
        }
        database.withDriverTransaction {
            require(supplierDao.getSupplierById(supplierId) != null) { "Supplier no longer exists" }
            stockLines.forEach { line ->
                require(line.productBarcode.isNotBlank()) { "A stock item is missing its product" }
                require(line.quantity.isFinite() && line.quantity > 0.0) { "Stock quantity must be greater than zero" }
                require(line.unitCost.isFinite() && line.unitCost >= 0.0) { "Unit cost cannot be negative" }
            }
            val due = (totalAmount - paidAmount).coerceAtLeast(0.0)
            val invId = purchaseInvoiceDao.insertPurchaseInvoice(
                PurchaseInvoice(
                    supplierId = supplierId,
                    invoiceNumber = invoiceNumber,
                    totalAmount = totalAmount,
                    paidAmount = paidAmount,
                    dueAmount = due,
                    itemsSummary = itemsSummary,
                    paymentMethod = paymentMethod,
                    note = note
                )
            )
            require(
                supplierDao.updateSupplierBalances(
                    supplierId = supplierId,
                    purchaseAmount = totalAmount,
                    dueChange = due,
                    timestamp = System.currentTimeMillis()
                ) == 1
            ) { "Supplier no longer exists" }
            if (paidAmount > 0.0) {
                supplierDao.insertSupplierPayment(
                    SupplierPayment(
                        supplierId = supplierId,
                        amountPaid = paidAmount,
                        paymentMethod = paymentMethod,
                        referenceNote = "Paid on Invoice #$invoiceNumber${if (!note.isNullOrBlank()) " - $note" else ""}",
                        timestamp = System.currentTimeMillis(),
                        performedByName = sessionUser?.name
                    )
                )
                if (paymentMethod.equals("Cash", ignoreCase = true)) {
                    expenseDao.insertExpense(
                        Expense(
                            title = "Supplier Invoice #$invoiceNumber",
                            category = "Supplier Payment",
                            type = "EXPENSE",
                            amount = paidAmount,
                            paymentSource = "Cash",
                            note = "Paid to supplier on Invoice #$invoiceNumber${if (!note.isNullOrBlank()) " - $note" else ""}",
                            timestamp = System.currentTimeMillis(),
                            cashierName = sessionUser?.name ?: "Admin"
                        )
                    )
                }
            }
            if (stockLines.isNotEmpty()) {
                val invoiceItems = stockLines.map { line ->
                    val product = productDao.getProductByBarcode(line.productBarcode)
                        ?: throw IllegalArgumentException("Product ${line.productBarcode} no longer exists")
                    val previousStock = product.stockQty
                    val newStock = previousStock + line.quantity
                    val newPurchasePrice = if (line.unitCost > 0.0) {
                        if (previousStock > 0.0 && product.purchasePrice > 0.0) {
                            val totalCost = (previousStock * product.purchasePrice) + (line.quantity * line.unitCost)
                            val totalQty = previousStock + line.quantity
                            if (totalQty > 0.0) totalCost / totalQty else line.unitCost
                        } else {
                            line.unitCost
                        }
                    } else {
                        product.purchasePrice
                    }
                    productDao.updateProduct(
                        product.copy(
                            stockQty = newStock,
                            purchasePrice = newPurchasePrice
                        )
                    )
                    inventoryAdjustmentDao.insertAdjustment(
                        InventoryAdjustment(
                            productBarcode = line.productBarcode,
                            productName = product.name,
                            previousStock = previousStock,
                            newStock = newStock,
                            difference = line.quantity,
                            reason = "Purchase Invoice",
                            note = "Purchase Invoice #$invoiceNumber",
                            cashierName = sessionUser?.name ?: "Admin"
                        )
                    )
                    PurchaseInvoiceItem(
                        invoiceId = invId,
                        productBarcode = line.productBarcode,
                        productName = product.name,
                        quantity = line.quantity,
                        unitCost = line.unitCost
                    )
                }
                purchaseInvoiceDao.insertPurchaseInvoiceItems(invoiceItems)
            }
            invId
        }
    }

    suspend fun recordSupplierPayment(
        supplierId: Long,
        amountPaid: Double,
        paymentMethod: String = "Cash",
        referenceNote: String? = null
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.SUPPLIERS_MANAGE)
        require(amountPaid.isFinite() && amountPaid > 0.0) { "Payment must be greater than zero" }
        database.withDriverTransaction {
            val timestamp = System.currentTimeMillis()
            require(supplierDao.recordSupplierPayment(supplierId, amountPaid, timestamp) == 1) {
                "Payment exceeds the outstanding supplier balance"
            }
            val paymentId = supplierDao.insertSupplierPayment(
                SupplierPayment(
                    supplierId = supplierId,
                    amountPaid = amountPaid,
                    paymentMethod = paymentMethod,
                    referenceNote = referenceNote?.trim()?.takeIf { it.isNotEmpty() },
                    timestamp = timestamp,
                    performedByName = sessionUser?.name
                )
            )
            if (paymentMethod.equals("Cash", ignoreCase = true)) {
                val supplier = supplierDao.getSupplierById(supplierId)
                val supplierName = supplier?.name ?: "Supplier #$supplierId"
                expenseDao.insertExpense(
                    Expense(
                        title = "Supplier Payment: $supplierName",
                        category = "Supplier Payment",
                        type = "EXPENSE",
                        amount = amountPaid,
                        paymentSource = "Cash",
                        note = referenceNote?.trim()?.takeIf { it.isNotEmpty() } ?: "Payment to $supplierName",
                        timestamp = timestamp,
                        cashierName = sessionUser?.name ?: "Admin"
                    )
                )
            }
            paymentId
        }
    }

    fun getPurchaseItemsForInvoice(invoiceId: Long): Flow<List<PurchaseInvoiceItem>> =
        purchaseInvoiceDao.getItemsForInvoice(invoiceId)

    fun getSupplierPayments(supplierId: Long): Flow<List<SupplierPayment>> =
        supplierDao.getPaymentsForSupplier(supplierId)

    // Register Closings & Cash Shifts
    val allRegisterClosings: Flow<List<RegisterClosing>> = registerClosingDao.getAllClosings()
    val registerShiftState: Flow<RegisterShiftState?> = registerShiftStateDao.observe()

    suspend fun getClosingByDate(date: String): RegisterClosing? = withContext(Dispatchers.IO) {
        registerClosingDao.getClosingByDate(date)
    }

    suspend fun insertRegisterClosing(closing: RegisterClosing): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.REPORTS_CLOSE_REGISTER)
        database.withDriverTransaction {
            val timestamp = System.currentTimeMillis()
            val id = registerClosingDao.insertClosing(
                closing.copy(timestamp = timestamp, cashierName = sessionUser?.name ?: closing.cashierName)
            )
            registerShiftStateDao.upsert(RegisterShiftState(startedAt = timestamp, startingFloat = 0.0))
            id
        }
    }

    suspend fun updateRegisterShiftStartingFloat(amount: Double) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.REPORTS_CLOSE_REGISTER)
        require(amount.isFinite() && amount >= 0.0) { "Starting float must be zero or greater" }
        val current = registerShiftStateDao.get()
        registerShiftStateDao.upsert(
            (current ?: RegisterShiftState(startedAt = System.currentTimeMillis())).copy(startingFloat = amount)
        )
    }

    // Customer Loyalty
    val allLoyaltyMembers: Flow<List<CustomerLoyalty>> = customerLoyaltyDao.getAllLoyaltyMembers()
    val allLoyaltyTransactions: Flow<List<LoyaltyTransaction>> = loyaltyTransactionDao.getAll()

    suspend fun getLoyaltyByPhone(phone: String): CustomerLoyalty? = withContext(Dispatchers.IO) {
        customerLoyaltyDao.getLoyaltyByPhone(normalizePhone(phone))
    }

    suspend fun upsertLoyalty(loyalty: CustomerLoyalty) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        val phone = normalizePhone(loyalty.phone)
        val existing = customerLoyaltyDao.getLoyaltyByPhone(phone)
        customerLoyaltyDao.upsertLoyalty(
            if (existing == null) loyalty.copy(phone = phone)
            else existing.copy(
                name = loyalty.name.trim().ifBlank { existing.name },
                tier = loyalty.tier.ifBlank { existing.tier },
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )
        )
        syncCustomerProfileFromLedgers(phone)
    }

    suspend fun addLoyaltyPoints(phone: String, pts: Int, spend: Double, reason: String = "Manual points adjustment") = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        require(pts > 0 && spend.isFinite() && spend >= 0.0) { "Invalid loyalty adjustment" }
        val normalized = normalizePhone(phone)
        val timestamp = System.currentTimeMillis()
        database.withDriverTransaction {
            customerLoyaltyDao.addPoints(normalized, pts, spend, timestamp)
            loyaltyTransactionDao.insert(LoyaltyTransaction(
                customerPhone = normalized, pointsChange = pts, spendChange = spend,
                type = "MANUAL_ADD", reason = reason.ifBlank { "Manual points adjustment" }, timestamp = timestamp,
                performedByName = sessionUser?.name
            ))
            syncCustomerProfileFromLedgers(normalized)
        }
    }

    suspend fun redeemLoyaltyPoints(phone: String, pts: Int, reason: String = "Manual redemption"): Boolean = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        if (pts <= 0) return@withContext false
        val normalized = normalizePhone(phone)
        val timestamp = System.currentTimeMillis()
        database.withDriverTransaction {
            val changed = customerLoyaltyDao.redeemPoints(normalized, pts, timestamp) == 1
            if (changed) loyaltyTransactionDao.insert(LoyaltyTransaction(
                customerPhone = normalized, pointsChange = -pts, type = "REDEEM",
                reason = reason.ifBlank { "Manual redemption" }, timestamp = timestamp,
                performedByName = sessionUser?.name
            ))
            if (changed) syncCustomerProfileFromLedgers(normalized)
            changed
        }
    }

    suspend fun setLoyaltyMemberActive(phone: String, active: Boolean): Boolean = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        val normalized = normalizePhone(phone)
        val changed = customerLoyaltyDao.setActive(normalized, active, System.currentTimeMillis()) == 1
        if (changed) syncCustomerProfileFromLedgers(normalized)
        changed
    }

    fun getLoyaltyTransactionsForCustomer(phone: String): Flow<List<LoyaltyTransaction>> =
        loyaltyTransactionDao.getForCustomer(normalizePhone(phone))

    suspend fun deleteLoyaltyMember(phone: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.DUES_ADD_DUE)
        val normalized = normalizePhone(phone)
        database.withDriverTransaction {
            customerLoyaltyDao.deleteLoyaltyByPhone(normalized)
            syncCustomerProfileFromLedgers(normalized)
        }
    }

    suspend fun resetFactoryDefaults() = withContext(Dispatchers.IO) {
        requireOwnerSession()
        requirePermission(AppPermissions.SYSTEM_FACTORY_RESET)
        val connectedDriveEmail = settingsDataStore.driveAccountEmailFlow.first().takeIf { it.isNotBlank() }
        database.clearAllTables()
        settingsDataStore.clearAll()
        if (connectedDriveEmail != null) {
            try {
                com.example.techyfipos.utils.GoogleDriveHelper.revoke(context, connectedDriveEmail)
            } catch (e: Exception) {
                // Revocation can fail if the grant was already invalidated elsewhere; the local
                // identity is already cleared above, so the account will simply need to
                // re-authorize (interactively) on the next Drive connection attempt.
                e.printStackTrace()
            }
        }
        try {
            val logoFile = File(context.filesDir, "custom_logo.png")
            if (logoFile.exists()) logoFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun encodeProductImage(uriOrPath: String?): String? {
        val value = uriOrPath?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val bytes = when {
                value.startsWith("content://") || value.startsWith("file://") ->
                    context.contentResolver.openInputStream(Uri.parse(value))?.use { it.readBytes() }
                else -> File(value).takeIf { it.isFile }?.readBytes()
            } ?: return null
            if (bytes.size > 10 * 1024 * 1024) return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun restoreProductImage(productJson: JSONObject): String? {
        val encoded = productJson.optString("imageBase64")
        if (encoded.isNotBlank()) {
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                if (bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024) {
                    val dir = File(context.filesDir, "restored_product_images").apply { mkdirs() }
                    val safeName = productJson.optString("barcode", "product").replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val target = File(dir, "${safeName}_${System.currentTimeMillis()}.jpg")
                    target.writeBytes(bytes)
                    return target.absolutePath
                }
            } catch (_: Exception) { }
        }
        // Legacy paths only remain usable when restoring on the same device.
        return productJson.optString("imageUri").takeIf { it.isNotBlank() && (it.startsWith("content://") || File(it.removePrefix("file://")).exists()) }
    }

    private fun jsonContainsBarcode(json: String, barcode: String): Boolean = try {
        fun contains(value: Any?): Boolean = when (value) {
            is JSONObject -> value.optString("barcode") == barcode || value.keys().asSequence().any { contains(value.opt(it)) }
            is JSONArray -> (0 until value.length()).any { contains(value.opt(it)) }
            else -> false
        }
        val trimmed = json.trim()
        contains(if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed))
    } catch (_: Exception) { false }

    private suspend fun requireProductNotInSavedCarts(barcode: String) {
        val activeSnapshot = settingsDataStore.cartSnapshotFlow.first()
        val heldReference = heldCartDao.getAllHeldCartsOnce().any { jsonContainsBarcode(it.itemsJson, barcode) }
        require(!jsonContainsBarcode(activeSnapshot, barcode) && !heldReference) {
            "Remove this product from the active and held carts before deleting or changing its barcode"
        }
    }

    private fun clearBusinessTablesForRestore() {
        val sql = database.getSupportWrapper()
        listOf(
            "return_items", "returns", "sale_items", "sales", "due_payments", "due_charges", "customer_dues",
            "customers", "due_reminder_logs", "supplier_payments", "purchase_invoice_items", "purchase_invoices",
            "suppliers", "held_carts", "expenses", "register_closings", "register_shift_state",
            "loyalty_transactions", "customer_loyalty", "products", "inventory_adjustments", "scheduled_due_reminders"
        ).forEach { table -> sql.execSQL("DELETE FROM `$table`") }
    }

    private suspend fun createPortableRestoreSnapshot() {
        val dir = File(context.filesDir, "safety_rollback").apply { mkdirs() }
        val snapshot = File(dir, "pre_restore_portable_${System.currentTimeMillis()}.json")
        exportBackup(snapshot)
        val snapshots = dir.listFiles { file -> file.name.startsWith("pre_restore_portable_") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        snapshots.drop(3).forEach { it.delete() }
    }
}
