package com.example.techyfipos.data

import android.content.Context
import com.example.techyfipos.data.db.*
import com.example.techyfipos.utils.normalizePhone
import com.example.techyfipos.utils.toEnglishDigits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import android.util.Base64
import android.util.Log
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

sealed class CustomerPhoneUpdateResult {
    data class Success(val finalPhone: String) : CustomerPhoneUpdateResult()
    data class Collision(val existingCustomer: Customer) : CustomerPhoneUpdateResult()
    data class Error(val message: String) : CustomerPhoneUpdateResult()
}

data class ReturnProcessResult(
    val refundTotal: Double,
    val voucherCode: String? = null,
    val voucher: StoreCreditVoucher? = null
)

class DataRepository(
    val context: Context,
    private val database: AppDatabase,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        val DEFAULT_PAYMENT_METHODS = listOf("Cash", "bKash", "Nagad", "Rocket", "Bank")
        // 1 loyalty point (redeemable 1:1 as taka off a future sale) per 100 taka of grand total.
        private const val LOYALTY_TAKA_PER_POINT = 100.0
        private const val CREDIT_OVERRIDE_VALIDITY_MS = 5 * 60_000L
    }
    val appContext: Context get() = context
    private val _loyaltySettingsFlow = MutableStateFlow(com.example.techyfipos.utils.LoyaltySettingsHelper.getSettings(context))
    val loyaltySettingsFlow: StateFlow<com.example.techyfipos.utils.LoyaltyProgramSettings> = _loyaltySettingsFlow.asStateFlow()

    fun getLoyaltySettings(): com.example.techyfipos.utils.LoyaltyProgramSettings =
        _loyaltySettingsFlow.value

    fun updateLoyaltySettings(settings: com.example.techyfipos.utils.LoyaltyProgramSettings) {
        _loyaltySettingsFlow.value = settings
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
    private val expenseAuditLogDao = database.expenseAuditLogDao()
    private val storeCreditVoucherDao = database.storeCreditVoucherDao()
    private val storeCreditLedgerDao = database.storeCreditLedgerDao()

    val allSaleAuditLogs: Flow<List<com.example.techyfipos.data.db.SaleAuditLog>> = saleAuditLogDao.getAllAuditLogs()
    val allExpenseAuditLogs: Flow<List<com.example.techyfipos.data.db.ExpenseAuditLog>> = expenseAuditLogDao.getAllAuditLogs()

    fun getCustomerStoreCreditLedger(phone: String): Flow<List<StoreCreditLedgerEntry>> =
        storeCreditLedgerDao.getLedgerForCustomer(normalizePhone(phone))

    suspend fun getCustomerStoreCreditLedgerOnce(phone: String): List<StoreCreditLedgerEntry> = withContext(Dispatchers.IO) {
        storeCreditLedgerDao.getLedgerForCustomerOnce(normalizePhone(phone))
    }

    suspend fun adjustCustomerCredit(phone: String, delta: Double, note: String? = null): Customer = withContext(Dispatchers.IO) {
        requireOwnerSession()
        database.withDriverTransaction {
            val norm = normalizePhone(phone)
            var customer = customerDao.getCustomerByPhone(norm)
            if (customer == null) {
                customer = Customer(
                    phone = norm,
                    name = "Customer ${norm.takeLast(4)}",
                    storeCredit = 0.0
                )
                customerDao.upsertCustomer(customer)
            }
            customerDao.adjustCustomerStoreCredit(norm, delta, System.currentTimeMillis())
            val updated = customerDao.getCustomerByPhone(norm) ?: customer
            storeCreditLedgerDao.insertEntry(
                StoreCreditLedgerEntry(
                    customerPhone = norm,
                    deltaAmount = delta,
                    balanceAfter = updated.storeCredit,
                    type = "MANUAL_ADJUST",
                    note = note ?: "Manual store credit adjustment"
                )
            )
            updated
        }
    }

    suspend fun getStoreCreditVoucher(code: String): StoreCreditVoucher? = withContext(Dispatchers.IO) {
        storeCreditVoucherDao.getVoucherByCode(code.trim().uppercase())
    }

    suspend fun getStoreCreditVoucherBySaleId(saleId: Long): StoreCreditVoucher? = withContext(Dispatchers.IO) {
        storeCreditVoucherDao.getVoucherBySaleId(saleId)
    }

    suspend fun getStoreCreditVoucherByReturnId(returnId: Long): StoreCreditVoucher? = withContext(Dispatchers.IO) {
        storeCreditVoucherDao.getVoucherByReturnId(returnId)
    }

    suspend fun redeemStoreCreditVoucher(code: String, amountToRedeem: Double): Double = withContext(Dispatchers.IO) {
        val clean = code.trim().uppercase()
        val voucher = storeCreditVoucherDao.getVoucherByCode(clean)
            ?: throw IllegalArgumentException("Voucher $clean not found")
        require(voucher.status == "ACTIVE") { "Voucher $clean is ${voucher.status.lowercase()}" }
        if (voucher.expiresAt != null && voucher.expiresAt < System.currentTimeMillis()) {
            storeCreditVoucherDao.updateVoucherBalance(clean, voucher.remainingBalance, "EXPIRED")
            throw IllegalArgumentException("Voucher $clean has expired")
        }
        val actualDeduction = kotlin.math.min(amountToRedeem, voucher.remainingBalance)
        require(actualDeduction > 0.0) { "Voucher has no remaining balance" }
        val newBal = (voucher.remainingBalance - actualDeduction).coerceAtLeast(0.0)
        val newStatus = if (newBal <= 0.01) "EXHAUSTED" else "ACTIVE"
        storeCreditVoucherDao.updateVoucherBalance(clean, newBal, newStatus)
        actualDeduction
    }

    suspend fun cancelVoucher(code: String): Boolean = withContext(Dispatchers.IO) {
        requireOwnerSession()
        storeCreditVoucherDao.cancelVoucher(code.trim().uppercase()) > 0
    }

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
        inventoryAdjustmentDao.getAdjustmentsForProduct(toEnglishDigits(barcode.trim()))

    suspend fun recordInventoryAdjustment(adjustment: InventoryAdjustment): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADJUST_STOCK)
        val cashier = adjustment.cashierName.ifBlank { sessionUser?.name ?: "Admin" }
        inventoryAdjustmentDao.insertAdjustment(adjustment.copy(cashierName = cashier))
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

    data class DiscountOverrideToken(
        val maxDiscountAmount: Double,
        val cartHash: String? = null,
        val expiresAt: Long
    )

    @Volatile private var pendingDiscountOverride: DiscountOverrideToken? = null

    internal fun grantDiscountOverride(maxDiscountAmount: Double, cartHash: String? = null) {
        synchronized(this) {
            pendingDiscountOverride = DiscountOverrideToken(
                maxDiscountAmount = maxDiscountAmount,
                cartHash = cartHash,
                expiresAt = System.currentTimeMillis() + CREDIT_OVERRIDE_VALIDITY_MS
            )
        }
    }

    suspend fun authorizeDiscountOverride(
        pin: String,
        maxDiscountAmount: Double,
        cartHash: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val supervisor = verifySupervisorPin(pin) ?: return@withContext false
        grantDiscountOverride(maxDiscountAmount, cartHash)
        true
    }

    internal fun hasValidDiscountOverride(
        discountAmount: Double,
        cartHash: String? = null
    ): Boolean = synchronized(this) {
        val grant = pendingDiscountOverride
        grant != null &&
            grant.expiresAt >= System.currentTimeMillis() &&
            (grant.maxDiscountAmount >= discountAmount - 0.01) &&
            (grant.cartHash == null || cartHash == null || grant.cartHash == cartHash)
    }

    internal fun consumeDiscountOverride(
        discountAmount: Double,
        cartHash: String? = null
    ): Boolean = synchronized(this) {
        val valid = hasValidDiscountOverride(discountAmount, cartHash)
        if (valid) {
            pendingDiscountOverride = null
        }
        valid
    }

    fun setSessionUser(user: User?) {
        sessionUser = user
    }

    val currentSessionUser: User? get() = sessionUser
    val sessionUserName: String
        get() {
            val user = sessionUser
            if (user == null) {
                Log.w("DataRepository", "sessionUser is null when accessing sessionUserName")
                return "Unknown"
            }
            return user.name
        }

    fun isSessionOwner(): Boolean = sessionUser?.role == AppRoles.OWNER
    fun isSessionManager(): Boolean = sessionUser?.role == AppRoles.MANAGER
    fun isSessionManagerOrOwner(): Boolean = AppPermissions.isSupervisor(sessionUser)
    fun hasPermission(permissionKey: String): Boolean = AppPermissions.hasPermission(sessionUser, permissionKey)

    class UnauthorizedException(message: String) : Exception(message)
    class InsufficientStockException(message: String) : Exception(message)

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

    suspend fun verifySupervisorPinDetailed(pin: String): SupervisorVerifyResult = withContext(Dispatchers.IO) {
        when (val res = verifyPinDetailed(pin)) {
            is PinVerifyResult.Success -> {
                if (AppPermissions.isSupervisor(res.user)) {
                    SupervisorVerifyResult.Success(res.user)
                } else {
                    SupervisorVerifyResult.NotSupervisor
                }
            }
            is PinVerifyResult.Locked -> SupervisorVerifyResult.Locked(res.remainingMillis)
            is PinVerifyResult.Deactivated -> SupervisorVerifyResult.Deactivated(res.user)
            is PinVerifyResult.Invalid -> SupervisorVerifyResult.Invalid(res.remainingMillis)
        }
    }

    suspend fun verifySupervisorPin(pin: String): User? = withContext(Dispatchers.IO) {
        (verifySupervisorPinDetailed(pin) as? SupervisorVerifyResult.Success)?.user
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

        pendingCreditOverride = null
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
        val clean = query.trim()
        val english = toEnglishDigits(clean)
        return if (english != clean) {
            productDao.searchProductsWithAlt("%$clean%", "%$english%")
        } else {
            productDao.searchProducts("%$clean%")
        }
    }

    suspend fun getProductByBarcode(barcode: String): Product? = withContext(Dispatchers.IO) {
        val normalized = toEnglishDigits(barcode.trim()).lowercase()
        productDao.getProductByBarcode(normalized)
    }

    suspend fun productExistsByBarcode(barcode: String): Boolean = withContext(Dispatchers.IO) {
        val normalized = toEnglishDigits(barcode.trim()).lowercase()
        productDao.getProductByBarcode(normalized) != null
    }

    suspend fun insertProduct(product: Product) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        val normalized = product.copy(barcode = toEnglishDigits(product.barcode.trim()).lowercase())
        productDao.insertProduct(normalized)
    }

    suspend fun updateProduct(product: Product) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        val normalized = product.copy(barcode = toEnglishDigits(product.barcode.trim()).lowercase())
        productDao.updateProduct(normalized)
    }

    /** Updates a product and records its inventory audit entry as a single transaction, so a manual stock edit can never leave stock changed without an audit row (or vice versa). */
    suspend fun updateProductWithInventoryAdjustment(product: Product, adjustment: InventoryAdjustment) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        requirePermission(AppPermissions.INVENTORY_ADJUST_STOCK)
        val normalized = product.copy(barcode = toEnglishDigits(product.barcode.trim()).lowercase())
        val cashier = adjustment.cashierName.ifBlank { sessionUser?.name ?: "Admin" }
        database.withDriverTransaction {
            productDao.updateProduct(normalized)
            inventoryAdjustmentDao.insertAdjustment(adjustment.copy(cashierName = cashier))
        }
    }

    /** Changes a barcode without exposing the delete/insert gap to callers, optionally recording its inventory audit row in the same transaction. */
    suspend fun replaceProductBarcode(
        oldBarcode: String,
        updatedProduct: Product,
        adjustment: InventoryAdjustment? = null
    ) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.INVENTORY_ADD_EDIT)
        if (adjustment != null) {
            requirePermission(AppPermissions.INVENTORY_ADJUST_STOCK)
        }
        val cleanOldBarcode = toEnglishDigits(oldBarcode.trim()).lowercase()
        val normalizedProduct = updatedProduct.copy(barcode = toEnglishDigits(updatedProduct.barcode.trim()).lowercase())
        if (cleanOldBarcode != normalizedProduct.barcode) requireProductNotInSavedCarts(cleanOldBarcode)
        database.withDriverTransaction {
            if (cleanOldBarcode == normalizedProduct.barcode) {
                productDao.updateProduct(normalizedProduct)
            } else {
                val oldProduct = productDao.getProductByBarcode(cleanOldBarcode)
                    ?: productDao.getProductByBarcodeCaseInsensitive(cleanOldBarcode)
                    ?: throw IllegalArgumentException("Product no longer exists")
                val existingTarget = productDao.getProductByBarcode(normalizedProduct.barcode)
                    ?: productDao.getProductByBarcodeCaseInsensitive(normalizedProduct.barcode)
                require(existingTarget == null) {
                    "Product barcode already exists"
                }
                productDao.deleteProduct(oldProduct)
                productDao.insertProduct(normalizedProduct)
            }
            if (adjustment != null) {
                val cashier = adjustment.cashierName.ifBlank { sessionUser?.name ?: "Admin" }
                inventoryAdjustmentDao.insertAdjustment(adjustment.copy(cashierName = cashier))
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
        val cleanBarcode = toEnglishDigits(barcode.trim())
        database.withDriverTransaction {
            val before = productDao.getProductByBarcode(cleanBarcode) ?: return@withDriverTransaction null
            val currentSettings = settingsDataStore.invoiceSettingsDataFlow.first()
            val allowNegative = currentSettings.allowNegativeInventory || isSessionManagerOrOwner()
            productDao.adjustStockQty(cleanBarcode, delta, allowNegative)
            val after = productDao.getProductByBarcode(cleanBarcode) ?: return@withDriverTransaction null
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
                        cashierName = sessionUser?.name ?: run {
                            Log.w("DataRepository", "sessionUser is null when recording inventory adjustment")
                            "Unknown"
                        }
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

    fun getSalesWithItemsBetween(startTime: Long, endTime: Long): Flow<List<SaleWithItems>> {
        return saleDao.getSalesWithItemsBetween(startTime, endTime)
    }

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
        return com.example.techyfipos.utils.PaymentMath.calculateDueAddition(
            paymentMethod,
            grandTotal,
            amountPaid,
            splitPaymentJson
        )
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
                val returnedItems = returnDao.getReturnItemsForSale(saleId)
                val returnedByBarcode = returnedItems
                    .groupBy { it.productBarcode }
                    .mapValues { (_, items) -> items.sumOf { it.quantity } }
                val pendingAdjustments = mutableListOf<InventoryAdjustment>()
                saleWithItems.items.groupBy { it.productBarcode }.forEach { (barcode, soldItems) ->
                    val product = productDao.getProductByBarcode(barcode)
                    if (product != null) {
                        val soldQuantity = soldItems.sumOf { it.quantity }
                        val returnedQuantity = returnedByBarcode[barcode] ?: 0.0
                        val netQuantitySold = (soldQuantity - returnedQuantity).coerceAtLeast(0.0)
                        if (netQuantitySold > 0.0) {
                            val currentSettings = settingsDataStore.invoiceSettingsDataFlow.first()
                            val allowNegative = currentSettings.allowNegativeInventory || isSessionManagerOrOwner()
                            productDao.adjustStockQty(barcode, netQuantitySold, allowNegative)
                            val after = productDao.getProductByBarcode(barcode)
                            val finalNewStock = after?.stockQty ?: (product.stockQty + netQuantitySold)
                            val finalDiff = if (after != null) (after.stockQty - product.stockQty) else netQuantitySold
                            pendingAdjustments.add(
                                InventoryAdjustment(
                                    productBarcode = barcode,
                                    productName = product.name,
                                    previousStock = product.stockQty,
                                    newStock = finalNewStock,
                                    difference = finalDiff,
                                    reason = "Sale Cancelled/Deleted",
                                    note = "Sale #$saleId deleted",
                                    cashierName = sessionUser?.name ?: run {
                                        Log.w("DataRepository", "sessionUser is null when recording sale deletion adjustment")
                                        "Unknown"
                                    }
                                )
                            )
                        }
                    }
                }
                // Record inventory adjustments after all adjustStockQty calls complete
                pendingAdjustments.forEach { inventoryAdjustmentDao.insertAdjustment(it) }

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
                }

                val loyaltyEarnTarget = saleWithItems.sale.loyaltyPhone?.takeIf { it.isNotBlank() } ?: phone
                if (!loyaltyEarnTarget.isNullOrBlank() && saleWithItems.sale.grandTotal > 0.0) {
                    val alreadyRefunded = returnDao.getRefundTotalForSale(saleId)
                    val remainingSpend = (saleWithItems.sale.grandTotal - alreadyRefunded).coerceAtLeast(0.0)
                    val takaPerPt = getLoyaltySettings().takaPerPoint.takeIf { it > 0.0 } ?: LOYALTY_TAKA_PER_POINT
                    val reversedPoints = floor(remainingSpend / takaPerPt).toInt()
                    if (reversedPoints > 0) {
                        customerLoyaltyDao.reverseEarnedPoints(
                            normalizePhone(loyaltyEarnTarget),
                            reversedPoints,
                            remainingSpend,
                            System.currentTimeMillis()
                        )
                        loyaltyTransactionDao.insert(LoyaltyTransaction(
                            customerPhone = normalizePhone(loyaltyEarnTarget), pointsChange = -reversedPoints,
                            spendChange = -remainingSpend, type = "SALE_VOID",
                            reason = "Invoice #$saleId deleted", saleId = saleId,
                            performedByName = sessionUser?.name
                        ))
                    }
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

                val scApplied = sale.storeCreditApplied
                if (scApplied > 0.0) {
                    val vCodeFromSale = sale.voucherCode ?: sale.splitPaymentJson?.let { json ->
                        try {
                            val array = JSONArray(json)
                            (0 until array.length()).firstNotNullOfOrNull { i ->
                                array.getJSONObject(i).optString("voucherCode", "").takeIf { it.isNotBlank() }
                            }
                        } catch (_: Exception) { null }
                    } ?: storeCreditLedgerDao.getEntriesForInvoiceOnce(saleId).firstNotNullOfOrNull { it.voucherCode?.takeIf { c -> c.isNotBlank() } }

                    val voucherDeductionFromSplit = sale.splitPaymentJson?.let { json ->
                        try {
                            val array = JSONArray(json)
                            (0 until array.length()).map { array.getJSONObject(it) }
                                .filter { it.optString("voucherCode", "").isNotBlank() }
                                .sumOf {
                                    if (it.has("voucherAmount")) it.optDouble("voucherAmount", 0.0)
                                    else it.optDouble("amount", 0.0)
                                }
                        } catch (_: Exception) { null }
                    }?.takeIf { it > 0.0 }

                    var voucherPortion = 0.0
                    var redirectTargetPhone: String? = null
                    if (!vCodeFromSale.isNullOrBlank()) {
                        val voucher = storeCreditVoucherDao.getVoucherByCode(vCodeFromSale.trim().uppercase())
                        if (voucher != null && voucher.status != "CANCELLED") {
                            val maxRestoreToVoucher = (voucher.initialAmount - voucher.remainingBalance).coerceAtLeast(0.0)
                            val voucherSpentInThisSale = if (sale.voucherAmount > 0.0) {
                                sale.voucherAmount
                            } else if (voucherDeductionFromSplit != null) {
                                voucherDeductionFromSplit
                            } else {
                                val ledgerEntriesForSale = storeCreditLedgerDao.getEntriesForInvoiceOnce(saleId)
                                val custCreditFromLedger = ledgerEntriesForSale
                                    .filter { it.customerPhone != null && (it.type == "SALE_REDEMPTION" || it.deltaAmount < 0.0) }
                                    .sumOf { kotlin.math.abs(it.deltaAmount) }
                                if (custCreditFromLedger > 0.0) {
                                    (scApplied - custCreditFromLedger).coerceAtLeast(0.0)
                                } else if (!sale.customerPhone.isNullOrBlank() || !phone.isNullOrBlank()) {
                                    0.0 // Prefer customer credit when ambiguous on legacy pre-v37 sales
                                } else {
                                    minOf(scApplied, maxRestoreToVoucher)
                                }
                            }
                            voucherPortion = minOf(scApplied, voucherSpentInThisSale, maxRestoreToVoucher)

                            val isExpired = voucher.expiresAt != null && voucher.expiresAt > 0L && voucher.expiresAt < System.currentTimeMillis()
                            if (isExpired || voucher.status == "EXPIRED") {
                                val targetCustPhone = listOfNotNull(
                                    sale.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                    phone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                    voucher.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) }
                                ).firstOrNull()

                                if (!targetCustPhone.isNullOrBlank()) {
                                    redirectTargetPhone = targetCustPhone
                                    voucherPortion = 0.0
                                } else {
                                    // Walk-in sale with no phone on sale or voucher (N-5):
                                    // Issue a fresh replacement voucher so the share is not lost
                                    var inserted = false
                                    for (attempt in 1..5) {
                                        val code = "SC-${(1000..9999).random()}-${(1000..9999).random()}"
                                        val fresh = StoreCreditVoucher(
                                            code = code,
                                            initialAmount = voucherPortion,
                                            remainingBalance = voucherPortion,
                                            customerPhone = null,
                                            sourceSaleId = saleId,
                                            issuedAt = System.currentTimeMillis(),
                                            expiresAt = System.currentTimeMillis() + (90L * 24 * 3600 * 1000),
                                            status = "ACTIVE",
                                            notes = "Fresh replacement for expired voucher ${voucher.code} from void of sale #$saleId"
                                        )
                                        try {
                                            storeCreditVoucherDao.insertVoucher(fresh)
                                            storeCreditLedgerDao.insertEntry(
                                                StoreCreditLedgerEntry(
                                                    customerPhone = null,
                                                    deltaAmount = voucherPortion,
                                                    balanceAfter = voucherPortion,
                                                    type = "VOUCHER_RESTORE",
                                                    referenceInvoiceId = saleId,
                                                    voucherCode = fresh.code,
                                                    note = "Issued fresh voucher ${fresh.code} replacing expired ${voucher.code} on void of sale #$saleId",
                                                    timestamp = System.currentTimeMillis()
                                                )
                                            )
                                            inserted = true
                                            break
                                        } catch (_: android.database.sqlite.SQLiteConstraintException) {
                                        } catch (_: androidx.sqlite.SQLiteException) {
                                        }
                                    }
                                    if (!inserted) {
                                        throw IllegalStateException("Failed to generate unique voucher code for expired replacement on void of sale #$saleId")
                                    }
                                    voucherPortion = 0.0
                                }
                            }

                            if (voucherPortion > 0.0) {
                                val restoredBal = voucher.remainingBalance + voucherPortion
                                val newStatus = when {
                                    isExpired || voucher.status == "EXPIRED" -> "EXPIRED"
                                    restoredBal > 0.0 -> "ACTIVE"
                                    else -> "EXHAUSTED"
                                }
                                storeCreditVoucherDao.updateVoucherBalance(voucher.code, restoredBal, newStatus)
                                val targetPhone = listOfNotNull(
                                    sale.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                    phone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                    voucher.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) }
                                ).firstOrNull()
                                val custBalAfter = targetPhone?.let { customerDao.getCustomerByPhone(it)?.storeCredit } ?: restoredBal
                                storeCreditLedgerDao.insertEntry(
                                    StoreCreditLedgerEntry(
                                        customerPhone = targetPhone,
                                        deltaAmount = voucherPortion,
                                        balanceAfter = custBalAfter,
                                        type = "VOUCHER_RESTORE",
                                        referenceInvoiceId = saleId,
                                        voucherCode = voucher.code,
                                        note = "Voucher balance restored from voided sale #$saleId",
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }

                    // voucherPortion is already 0 when an expired voucher's share was redirected, so the
                    // redirected share is included here exactly once (N-5: it used to be added a second time).
                    val customerPortion = (scApplied - voucherPortion).coerceAtLeast(0.0)
                    val creditPhone = phone?.takeIf { it.isNotBlank() } ?: redirectTargetPhone
                    if (customerPortion > 0.0 && !creditPhone.isNullOrBlank()) {
                        customerDao.adjustCustomerStoreCredit(normalizePhone(creditPhone), customerPortion, System.currentTimeMillis())
                        val newBal = (customerDao.getCustomerByPhone(normalizePhone(creditPhone))?.storeCredit ?: 0.0)
                        storeCreditLedgerDao.insertEntry(
                            StoreCreditLedgerEntry(
                                customerPhone = normalizePhone(creditPhone),
                                deltaAmount = customerPortion,
                                balanceAfter = newBal,
                                type = "SALE_VOID",
                                referenceInvoiceId = saleId,
                                note = "Store credit restored from voided sale #$saleId",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }

                // Audit log recorded after stock, dues, and loyalty restorations complete
                saleAuditLogDao.insertAuditLog(
                    com.example.techyfipos.data.db.SaleAuditLog(
                        saleId = saleId,
                        action = "DELETED",
                        reason = reason,
                        grandTotal = sale.grandTotal,
                        paymentMethod = sale.paymentMethod,
                        cashierName = sessionUser?.name ?: run {
                            Log.w("DataRepository", "sessionUser is null when recording sale audit log")
                            "Unknown"
                        },
                        timestamp = System.currentTimeMillis()
                    )
                )
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
        loyaltyPointsToRedeem: Int = 0,
        storeCreditApplied: Double = 0.0,
        isTaxInclusive: Boolean = false
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
        loyaltyPointsToRedeem = loyaltyPointsToRedeem,
        storeCreditApplied = storeCreditApplied,
        isTaxInclusive = isTaxInclusive
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
        loyaltyPointsToRedeem: Int = 0,
        storeCreditApplied: Double = 0.0,
        appliedVoucherCode: String? = null,
        nonTaxableBarcodes: Set<String> = emptySet(),
        isTaxInclusive: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.SALES_SELL)
        val computedCartHash = com.example.techyfipos.ui.viewmodel.CartViewModel.computeCartHashFromTriples(cartItemsWithNotes)
        val regularDiscountTaka = if (isPercentage) {
            (subtotal * (discountAmount.coerceIn(0.0, 100.0) / 100.0)).coerceAtLeast(0.0)
        } else {
            discountAmount.coerceAtMost(subtotal).coerceAtLeast(0.0)
        }
        val hasDiscountPermission = hasPermission(AppPermissions.SALES_APPLY_DISCOUNT)
        if (regularDiscountTaka > 0.0 && !hasDiscountPermission) {
            if (!hasValidDiscountOverride(regularDiscountTaka, computedCartHash)) {
                requirePermission(AppPermissions.SALES_APPLY_DISCOUNT)
            }
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
            val splitEntries: List<JSONObject>? = if (!splitPaymentJson.isNullOrBlank()) {
                val array = JSONArray(splitPaymentJson)
                (0 until array.length()).map { array.getJSONObject(it) }
            } else null

            when (paymentMethod) {
                "Due" -> require(customerName?.isNotBlank() == true && !normalizedCustomerPhone.isNullOrBlank()) {
                    "Customer name and phone are required for a Due sale"
                }
                "Store Credit" -> {
                    val hasVoucher = !appliedVoucherCode.isNullOrBlank() || splitEntries?.any { !it.optString("voucherCode").isNullOrBlank() } == true
                    if (!hasVoucher) {
                        require(customerName?.isNotBlank() == true && !normalizedCustomerPhone.isNullOrBlank()) {
                            "Customer name and phone are required for a Store Credit sale"
                        }
                    }
                }
                "bKash", "Nagad", "Rocket" -> require(!walletPhone.isNullOrBlank()) {
                    "A wallet number is required for $paymentMethod payments"
                }
                "Card" -> require(!cardLastFour.isNullOrBlank()) {
                    "The card's last 4 digits are required for Card payments"
                }
                "Cash" -> require(grandTotal == 0.0 || amountPaid >= grandTotal) {
                    "Amount paid ($amountPaid) cannot be less than grand total ($grandTotal) for Cash sale"
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
                                val hasVoucherInRow = !entry.optString("voucherCode").isNullOrBlank()
                                if (!hasVoucherInRow) {
                                    require(customerName?.isNotBlank() == true && !normalizedCustomerPhone.isNullOrBlank()) {
                                        "Customer name and phone are required for a Store Credit split row"
                                    }
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
                val isTaxInclusive = settingsDataStore.invoiceSettingsDataFlow.first().taxInclusive
                val payableBeforeLoyalty = if (isTaxInclusive) {
                    (subtotal - regularDiscount).coerceAtLeast(0.0)
                } else {
                    (subtotal - regularDiscount + taxAmount).coerceAtLeast(0.0)
                }
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
            var storeCreditToUse = if (storeCreditApplied > 0.0) storeCreditApplied.coerceAtMost(grandTotal) else 0.0
            if (paymentMethod.equals("Store Credit", ignoreCase = true) && storeCreditToUse <= 0.0) {
                storeCreditToUse = grandTotal
            } else if (paymentMethod == "Split" && splitEntries != null) {
                val splitStoreCredit = splitEntries.filter { it.optString("method").equals("Store Credit", ignoreCase = true) }
                    .sumOf { it.optDouble("amount", 0.0) }
                if (splitStoreCredit > 0.0) {
                    storeCreditToUse = splitStoreCredit
                }
            }
            var activeVoucherForPlainSC: StoreCreditVoucher? = null
            var customerCreditToDeduct = 0.0
            var finalVoucherCode: String? = null
            var finalVoucherAmount = 0.0
            if (storeCreditToUse > 0.0) {
                val vCodeInSplit = splitEntries?.firstNotNullOfOrNull { it.optString("voucherCode", "").takeIf { c -> c.isNotBlank() } }
                val targetVCode = vCodeInSplit ?: appliedVoucherCode
                if (!targetVCode.isNullOrBlank()) {
                    val cleanCode = targetVCode.trim().uppercase()
                    val voucher = storeCreditVoucherDao.getVoucherByCode(cleanCode)
                    require(voucher != null) { "Invalid store credit voucher code: $cleanCode" }
                    require(voucher.status == "ACTIVE") { "Store credit voucher $cleanCode is not active (Status: ${voucher.status})" }
                    if (voucher.expiresAt != null && voucher.expiresAt > 0L) {
                        require(voucher.expiresAt >= System.currentTimeMillis()) { "Store credit voucher $cleanCode has expired" }
                    }

                    val voucherDeductLimit = splitEntries?.filter {
                        it.optString("voucherCode", "").equals(cleanCode, ignoreCase = true)
                    }?.sumOf {
                        if (it.has("voucherAmount")) it.optDouble("voucherAmount", 0.0)
                        else it.optDouble("amount", 0.0)
                    }?.takeIf { it > 0.0 } ?: storeCreditToUse

                    val voucherDeduct = voucherDeductLimit.coerceAtMost(voucher.remainingBalance)
                    customerCreditToDeduct = (storeCreditToUse - voucherDeduct).coerceAtLeast(0.0)

                    if (voucherDeduct > 0.0) {
                        finalVoucherCode = cleanCode
                        finalVoucherAmount = voucherDeduct
                        val newBalance = (voucher.remainingBalance - voucherDeduct).coerceAtLeast(0.0)
                        val newStatus = if (newBalance < 0.01) "EXHAUSTED" else "ACTIVE"
                        storeCreditVoucherDao.updateVoucherBalance(voucher.code, newBalance, newStatus)
                        activeVoucherForPlainSC = voucher
                    }
                    if (customerCreditToDeduct > 0.0) {
                        require(!normalizedCustomerPhone.isNullOrBlank()) { "Customer phone number is required for store credit balance above voucher amount" }
                        val currentCust = customerDao.getCustomerByPhone(normalizedCustomerPhone)
                        val availableCredit = currentCust?.storeCredit ?: 0.0
                        require(availableCredit >= customerCreditToDeduct - 0.01) {
                            "Insufficient store credit balance (Available: ৳${availableCredit})"
                        }
                        customerDao.adjustCustomerStoreCredit(normalizedCustomerPhone, -customerCreditToDeduct, System.currentTimeMillis())
                    }
                } else {
                    customerCreditToDeduct = storeCreditToUse
                    require(!normalizedCustomerPhone.isNullOrBlank()) { "Customer phone number is required for Store Credit payment" }
                    val currentCust = customerDao.getCustomerByPhone(normalizedCustomerPhone)
                    val availableCredit = currentCust?.storeCredit ?: 0.0
                    require(availableCredit >= customerCreditToDeduct - 0.01) {
                        "Insufficient store credit balance (Available: ৳${availableCredit})"
                    }
                    customerDao.adjustCustomerStoreCredit(normalizedCustomerPhone, -customerCreditToDeduct, System.currentTimeMillis())
                }
            }

            val cartHash = computedCartHash

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
                    val rows = productDao.decrementStockIfAvailable(barcode, requested)
                    if (rows == 0 && !allowNegative) {
                        throw InsufficientStockException("Insufficient stock for ${lines.first().first.name}")
                    }
                    if (rows != 1) {
                        productDao.adjustStockQty(barcode, -requested, allowNegative)
                    }
                }

            if (regularDiscountTaka > 0.0 && !hasDiscountPermission) {
                require(consumeDiscountOverride(regularDiscountTaka, cartHash)) {
                    "Supervisor authorization is required to apply discount"
                }
            }

            val timestamp = System.currentTimeMillis()
            val finalCustomerCreditAmount = customerCreditToDeduct

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
                splitPaymentJson = if (paymentMethod.equals("Split", ignoreCase = true)) splitPaymentJson else null,
                performedByUserId = sessionUser?.id,
                performedByName = sessionUser?.name,
                loyaltyPhone = normalizedLoyaltyPhone,
                loyaltyPointsRedeemed = loyaltyPointsToRedeem,
                storeCreditApplied = storeCreditToUse,
                isTaxInclusive = isTaxInclusive,
                voucherCode = finalVoucherCode,
                voucherAmount = finalVoucherAmount,
                customerCreditAmount = finalCustomerCreditAmount
            )
            val saleId = try {
                saleDao.insertSale(sale)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                throw IllegalStateException("Failed to record sale: unique constraint conflict", e)
            } catch (e: androidx.sqlite.SQLiteException) {
                throw IllegalStateException("Failed to record sale: unique constraint conflict", e)
            }

            if (customerCreditToDeduct > 0.0 && !normalizedCustomerPhone.isNullOrBlank()) {
                val updatedCust = customerDao.getCustomerByPhone(normalizedCustomerPhone)
                storeCreditLedgerDao.insertEntry(
                    StoreCreditLedgerEntry(
                        customerPhone = normalizedCustomerPhone,
                        deltaAmount = -customerCreditToDeduct,
                        balanceAfter = updatedCust?.storeCredit ?: 0.0,
                        type = "SALE_REDEMPTION",
                        referenceInvoiceId = saleId,
                        voucherCode = activeVoucherForPlainSC?.code,
                        note = "Redeemed at sale #$saleId"
                    )
                )
            }


        
            val saleItems = cartItemsWithNotes.map { (product, quantity, note) ->
                SaleItem(
                    saleId = saleId,
                    productBarcode = product.barcode,
                    productName = product.name,
                    price = product.sellingPrice,
                    quantity = quantity,
                    note = note,
                    purchasePriceAtSale = product.purchasePrice,
                    taxable = product.barcode !in nonTaxableBarcodes
                )
            }
            try {
                saleDao.insertSaleItems(saleItems)
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                throw IllegalStateException("Failed to record sale items: unique constraint conflict", e)
            } catch (e: androidx.sqlite.SQLiteException) {
                throw IllegalStateException("Failed to record sale items: unique constraint conflict", e)
            }

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
                        createdAt = currentDue?.createdAt ?: System.currentTimeMillis(),
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
            val loyaltyEarnTarget = normalizedLoyaltyPhone?.takeIf { it.isNotBlank() } ?: normalizedCustomerPhone
            if (!loyaltyEarnTarget.isNullOrBlank() && grandTotal > 0.0) {
                val loyaltySettings = getLoyaltySettings()
                val takaPerPt = loyaltySettings.takaPerPoint.takeIf { it > 0.0 } ?: LOYALTY_TAKA_PER_POINT
                val earnedPoints = floor(grandTotal / takaPerPt).toInt()
                if (customerLoyaltyDao.getLoyaltyByPhone(loyaltyEarnTarget) == null) {
                    customerLoyaltyDao.upsertLoyalty(
                        CustomerLoyalty(
                            phone = loyaltyEarnTarget,
                            name = customerName?.takeIf { it.isNotBlank() } ?: "Customer"
                        )
                    )
                }
                customerLoyaltyDao.addPoints(loyaltyEarnTarget, earnedPoints, grandTotal, timestamp)
                val updatedLoyalty = customerLoyaltyDao.getLoyaltyByPhone(loyaltyEarnTarget)
                if (updatedLoyalty != null && loyaltySettings.isTiersEnabled) {
                    val newTier = when {
                        updatedLoyalty.lifetimeSpent >= loyaltySettings.platinumSpendThreshold -> "Platinum"
                        updatedLoyalty.lifetimeSpent >= loyaltySettings.goldSpendThreshold -> "Gold"
                        updatedLoyalty.lifetimeSpent >= loyaltySettings.silverSpendThreshold -> "Silver"
                        else -> "Regular"
                    }
                    if (getTierRank(newTier) > getTierRank(updatedLoyalty.tier)) {
                        customerLoyaltyDao.upsertLoyalty(updatedLoyalty.copy(tier = newTier, updatedAt = timestamp))
                    }
                }
                loyaltyTransactionDao.insert(LoyaltyTransaction(
                    customerPhone = loyaltyEarnTarget, pointsChange = earnedPoints,
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
                        storeCredit = existingCust?.storeCredit ?: 0.0,
                        totalOrders = updatedOrders,
                        lifetimeSpent = updatedLifetime,
                        totalDue = currentDue,
                        loyaltyPoints = loyalty?.pointsBalance ?: 0,
                        loyaltyTier = loyalty?.tier ?: "Regular",
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
        val cleanPhone = normalizePhone(phone)
        val customer = customerDao.getCustomerByPhone(cleanPhone) ?: return@withContext null
        val dueLimit = customerDueDao.getCustomerDueByPhone(cleanPhone)?.creditLimit ?: 0.0
        if (dueLimit > customer.creditLimit) {
            customer.copy(creditLimit = dueLimit)
        } else {
            customer
        }
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
        val loyaltyTier = loyalty?.tier ?: customer?.loyaltyTier ?: "Regular"

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
            val phones = customers.map { it.phone }
            val duesByPhone = customerDueDao.getCustomerDuesByPhones(phones).associateBy { it.customerPhone }
            val loyaltyByPhone = customerLoyaltyDao.getLoyaltyByPhones(phones).associateBy { it.phone }
            return@withContext customers.map { c ->
                val due = duesByPhone[c.phone]
                val loyalty = loyaltyByPhone[c.phone]
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
        val salesContacts = saleDao.searchPastCustomerContacts("%$clean%")
        val scPhones = salesContacts.map { it.customerPhone }
        val duesByPhone = customerDueDao.getCustomerDuesByPhones(scPhones).associateBy { it.customerPhone }
        val loyaltyByPhone = customerLoyaltyDao.getLoyaltyByPhones(scPhones).associateBy { it.phone }
        salesContacts.map { sc ->
            val due = duesByPhone[sc.customerPhone]
            val loyalty = loyaltyByPhone[sc.customerPhone]
            CustomerLookupResult(
                phone = sc.customerPhone,
                name = sc.customerName ?: "",
                source = "PAST_SALE",
                dueAmount = due?.outstandingAmount ?: 0.0,
                loyaltyPoints = loyalty?.pointsBalance ?: 0,
                tier = loyalty?.tier ?: "Regular",
                tag = "Past Customer"
            )
        }
    }

    suspend fun upsertCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.CUSTOMERS_EDIT)
        val cleanPhone = normalizePhone(customer.phone)
        database.withDriverTransaction {
            val timestamp = System.currentTimeMillis()
            val existing = customerDao.getCustomerByPhone(cleanPhone)
            val currentDueLimit = customerDueDao.getCustomerDueByPhone(cleanPhone)?.creditLimit ?: 0.0
            val effectiveCreditLimit = if (!isSessionOwner()) {
                maxOf(existing?.creditLimit ?: 0.0, currentDueLimit)
            } else {
                customer.creditLimit.coerceAtLeast(0.0)
            }
            customerDao.upsertCustomer(customer.copy(phone = cleanPhone, creditLimit = effectiveCreditLimit, updatedAt = timestamp))
            val existingDue = customerDueDao.getCustomerDueByPhone(cleanPhone)
            if (existingDue != null) {
                customerDueDao.upsertCustomerDue(
                    existingDue.copy(
                        customerName = customer.name.trim().ifBlank { existingDue.customerName },
                        creditLimit = effectiveCreditLimit,
                        updatedAt = timestamp
                    )
                )
            } else if (effectiveCreditLimit > 0.0 || customer.totalDue > 0.0) {
                customerDueDao.upsertCustomerDue(
                    CustomerDue(
                        customerPhone = cleanPhone,
                        customerName = customer.name.trim().ifBlank { "Customer" },
                        outstandingAmount = customer.totalDue,
                        creditLimit = effectiveCreditLimit,
                        updatedAt = timestamp
                    )
                )
            }
        }
    }

    suspend fun archiveCustomer(phone: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.CUSTOMERS_EDIT)
        val normalized = normalizePhone(phone)
        val activeDue = customerDueDao.getActiveCustomerDueByPhone(normalized)
        val outstanding = activeDue?.outstandingAmount ?: 0.0
        val customer = customerDao.getCustomerByPhone(normalized)
        val totalDue = customer?.totalDue ?: 0.0
        if (outstanding > 0.001 || totalDue > 0.001) {
            throw IllegalStateException("Cannot delete or archive customer with outstanding dues")
        }
        val storeCredit = customer?.storeCredit ?: 0.0
        if (storeCredit > 0.001) {
            throw IllegalStateException("Cannot delete or archive customer with store credit balance")
        }
        val vouchers = storeCreditVoucherDao.getVouchersByPhone(normalized)
        if (vouchers.any { it.status == "ACTIVE" && it.remainingBalance > 0.001 }) {
            throw IllegalStateException("Cannot delete or archive customer with active voucher balance")
        }
        customerDao.archiveCustomer(normalized, System.currentTimeMillis())
    }

    suspend fun deleteCustomerPermanently(phone: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.CUSTOMERS_EDIT)
        val normalized = normalizePhone(phone)
        val activeDue = customerDueDao.getActiveCustomerDueByPhone(normalized)
        val outstanding = activeDue?.outstandingAmount ?: 0.0
        val customer = customerDao.getCustomerByPhone(normalized)
        val totalDue = customer?.totalDue ?: 0.0
        if (outstanding > 0.001 || totalDue > 0.001) {
            throw IllegalStateException("Cannot permanently delete customer with outstanding dues")
        }
        val storeCredit = customer?.storeCredit ?: 0.0
        if (storeCredit > 0.001) {
            throw IllegalStateException("Cannot permanently delete customer with store credit balance")
        }
        val customerVouchers = storeCreditVoucherDao.getVouchersByPhone(normalized)
        if (customerVouchers.any { it.status == "ACTIVE" && it.remainingBalance > 0.001 }) {
            throw IllegalStateException("Cannot permanently delete customer with active voucher balance")
        }
        database.withDriverTransaction {
            customerDao.deleteCustomerPermanently(normalized)
            customerDueDao.deleteCustomerDue(normalized)
            customerLoyaltyDao.delete(normalized)
            scheduledDueReminderDao.deleteByPhone(normalized)
            storeCreditVoucherDao.deleteVouchersByPhone(normalized)
            // Note: storeCreditLedgerDao rows are retained to preserve audit trail
        }
    }

    suspend fun mergeCustomers(masterPhone: String, secondaryPhone: String): Boolean = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.CUSTOMERS_EDIT)
        val masterNorm = normalizePhone(masterPhone)
        val secNorm = normalizePhone(secondaryPhone)
        if (masterNorm == secNorm) return@withContext false

        val masterCustomer = customerDao.getCustomerByPhone(masterNorm)
        val masterDue = customerDueDao.getCustomerDueByPhone(masterNorm)
        val secondaryCustomer = customerDao.getCustomerByPhone(secNorm)
        val secondaryDue = customerDueDao.getCustomerDueByPhone(secNorm)

        if ((masterCustomer == null && masterDue == null) || (secondaryCustomer == null && secondaryDue == null)) {
            return@withContext false
        }

        database.withDriverTransaction {
            val masterCust = customerDao.getCustomerByPhone(masterNorm)
            val masterDueRec = customerDueDao.getCustomerDueByPhone(masterNorm)
            val master = masterCust ?: Customer(
                phone = masterNorm,
                name = masterDueRec?.customerName ?: "Customer",
                totalDue = masterDueRec?.outstandingAmount ?: 0.0,
                creditLimit = masterDueRec?.creditLimit ?: 0.0,
                updatedAt = System.currentTimeMillis()
            )

            val secCust = customerDao.getCustomerByPhone(secNorm)
            val secDueRec = customerDueDao.getCustomerDueByPhone(secNorm)
            val secondary = secCust ?: Customer(
                phone = secNorm,
                name = secDueRec?.customerName ?: "Customer",
                totalDue = secDueRec?.outstandingAmount ?: 0.0,
                creditLimit = secDueRec?.creditLimit ?: 0.0,
                updatedAt = System.currentTimeMillis()
            )

            // 1. Reassign sales
            saleDao.reassignCustomerForSales(secNorm, masterNorm, master.name)

            // 2. Reassign dues, charges and payments
            customerDueDao.reassignDueChargesPhone(secNorm, masterNorm)
            customerDueDao.reassignDuePaymentsPhone(secNorm, masterNorm)
            val secDue = customerDueDao.getCustomerDueByPhone(secNorm)
            if (secDue != null) {
                val masterDue = customerDueDao.getCustomerDueByPhone(masterNorm)
                val newDueAmount = (masterDue?.outstandingAmount ?: 0.0) + secDue.outstandingAmount
                val newCreditLimit = if (!isSessionOwner()) (masterDue?.creditLimit ?: 0.0) else maxOf(masterDue?.creditLimit ?: 0.0, secDue.creditLimit)
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
            val combinedLimit = if (!isSessionOwner()) master.creditLimit else maxOf(master.creditLimit, secondary.creditLimit)
            val combinedStoreCredit = master.storeCredit + secondary.storeCredit

            val updatedMaster = master.copy(
                notes = combinedNotes.ifBlank { null },
                lifetimeSpent = combinedSpent,
                totalOrders = combinedOrders,
                totalDue = combinedDue,
                creditLimit = combinedLimit,
                loyaltyPoints = combinedPoints,
                storeCredit = combinedStoreCredit,
                updatedAt = System.currentTimeMillis()
            )
            customerDao.upsertCustomer(updatedMaster)
            if (secondary.storeCredit > 0.0) {
                val transferredAmt = secondary.storeCredit
                customerDao.adjustCustomerStoreCredit(secNorm, -transferredAmt, System.currentTimeMillis())
                storeCreditLedgerDao.insertEntry(
                    StoreCreditLedgerEntry(
                        customerPhone = secNorm,
                        deltaAmount = -transferredAmt,
                        balanceAfter = 0.0,
                        type = "MERGE_TRANSFER",
                        note = "Store credit merged to master account $masterNorm",
                        timestamp = System.currentTimeMillis()
                    )
                )
                storeCreditLedgerDao.insertEntry(
                    StoreCreditLedgerEntry(
                        customerPhone = masterNorm,
                        deltaAmount = transferredAmt,
                        balanceAfter = combinedStoreCredit,
                        type = "MERGE_TRANSFER",
                        note = "Store credit merged from archived account $secNorm",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // 5. Archive secondary customer
            if (secCust != null) {
                customerDao.archiveCustomer(secNorm, System.currentTimeMillis())
            }
        }
        true
    }

    suspend fun migrateCustomerPhone(
        oldPhone: String,
        newPhone: String,
        newName: String? = null,
        creditLimit: Double? = null,
        notes: String? = null
    ): CustomerPhoneUpdateResult = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.CUSTOMERS_EDIT)
        val oldNorm = normalizePhone(oldPhone)
        val newNorm = normalizePhone(newPhone)

        if (newNorm.isBlank() || newNorm.replace(Regex("[^0-9]"), "").length < 5) {
            return@withContext CustomerPhoneUpdateResult.Error("Invalid phone number (minimum 5 digits required)")
        }

        if (oldNorm == newNorm) {
            val existing = customerDao.getCustomerByPhone(oldNorm)
            val existingDue = customerDueDao.getCustomerDueByPhone(oldNorm)
            val effectiveCreditLimit = if (!isSessionOwner()) {
                maxOf(existing?.creditLimit ?: 0.0, existingDue?.creditLimit ?: 0.0)
            } else {
                creditLimit ?: existing?.creditLimit ?: existingDue?.creditLimit ?: 0.0
            }
            if (existing != null) {
                customerDao.upsertCustomer(
                    existing.copy(
                        name = newName?.ifBlank { null } ?: existing.name,
                        creditLimit = effectiveCreditLimit,
                        notes = notes ?: existing.notes,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            if (existingDue != null) {
                customerDueDao.upsertCustomerDue(
                    existingDue.copy(
                        customerName = newName?.ifBlank { null } ?: existingDue.customerName,
                        creditLimit = effectiveCreditLimit,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            return@withContext CustomerPhoneUpdateResult.Success(newNorm)
        }

        // Check if newPhone already belongs to an existing active customer
        val collisionCustomer = customerDao.getCustomerByPhone(newNorm)
        if (collisionCustomer != null && !collisionCustomer.isArchived) {
            return@withContext CustomerPhoneUpdateResult.Collision(collisionCustomer)
        }

        val collisionDue = customerDueDao.getActiveCustomerDueByPhone(newNorm)
        if (collisionDue != null) {
            val dummy = Customer(phone = collisionDue.customerPhone, name = collisionDue.customerName, totalDue = collisionDue.outstandingAmount)
            return@withContext CustomerPhoneUpdateResult.Collision(dummy)
        }

        database.withDriverTransaction {
            val oldCustomer = customerDao.getCustomerByPhone(oldNorm)
            val oldDue = customerDueDao.getCustomerDueByPhone(oldNorm)
            val finalName = newName?.ifBlank { null } ?: oldCustomer?.name ?: oldDue?.customerName ?: "Customer"
            val finalCreditLimit = if (!isSessionOwner()) {
                maxOf(oldCustomer?.creditLimit ?: 0.0, oldDue?.creditLimit ?: 0.0)
            } else {
                creditLimit ?: oldCustomer?.creditLimit ?: oldDue?.creditLimit ?: 0.0
            }
            val finalNotes = notes ?: oldCustomer?.notes

            // 1. Insert new CustomerDue first so foreign key constraints on newNorm are satisfied
            val dueAmount = oldDue?.outstandingAmount ?: oldCustomer?.totalDue ?: 0.0
            customerDueDao.upsertCustomerDue(
                CustomerDue(
                    customerPhone = newNorm,
                    customerName = finalName,
                    outstandingAmount = dueAmount,
                    creditLimit = finalCreditLimit,
                    createdAt = oldDue?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            // 2. Reassign dues payments and charges to newNorm
            customerDueDao.reassignDuePaymentsPhone(oldNorm, newNorm)
            customerDueDao.reassignDueChargesPhone(oldNorm, newNorm)

            // 3. Delete old CustomerDue row (safe: payments/charges no longer point to oldNorm)
            customerDueDao.deleteCustomerDueByPhone(oldNorm)

            // 4. Reassign sales records
            saleDao.reassignCustomerForSales(oldNorm, newNorm, finalName)

            // 5. Reassign loyalty
            loyaltyTransactionDao.reassignCustomerPhone(oldNorm, newNorm)
            val oldLoyalty = customerLoyaltyDao.getLoyaltyByPhone(oldNorm)
            if (oldLoyalty != null) {
                customerLoyaltyDao.upsertLoyalty(
                    oldLoyalty.copy(
                        phone = newNorm,
                        name = finalName,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                customerLoyaltyDao.delete(oldNorm)
            }

            // 6. Reassign held carts & due reminders (authoritative DB row and itemsJson payload kept 100% in sync)
            heldCartDao.reassignHeldCartsCustomer(oldNorm, newNorm, finalName)
            val existingHeldCarts = runCatching { heldCartDao.getAllHeldCartsOnce().filter { it.customerPhone == newNorm } }.getOrDefault(emptyList())
            existingHeldCarts.forEach { cart ->
                try {
                    val payload = com.example.techyfipos.ui.viewmodel.decodeHeldCartPayload(cart.itemsJson)
                    if (payload.customerPhone != newNorm || payload.customerName != finalName) {
                        val syncedPayload = payload.copy(customerPhone = newNorm, customerName = finalName)
                        heldCartDao.insertHeldCart(cart.copy(
                            customerPhone = newNorm,
                            customerName = finalName,
                            itemsJson = com.example.techyfipos.ui.viewmodel.encodeHeldCartPayload(syncedPayload)
                        ))
                    }
                } catch (_: Exception) {}
            }
            scheduledDueReminderDao.reassignScheduledRemindersPhone(oldNorm, newNorm)
            dueReminderLogDao.reassignDueReminderLogsPhone(oldNorm, newNorm)

            // 7. Upsert Customer with newNorm & clean up old customer record
            val updatedCustomer = (oldCustomer ?: Customer(phone = newNorm, name = finalName))
                .copy(
                    phone = newNorm,
                    name = finalName,
                    creditLimit = finalCreditLimit,
                    notes = finalNotes,
                    updatedAt = System.currentTimeMillis()
                )
            customerDao.upsertCustomer(updatedCustomer)
            customerDao.deleteCustomerPermanently(oldNorm)
        }

        CustomerPhoneUpdateResult.Success(newNorm)
    }

    suspend fun addCustomerNote(phone: String, noteText: String, category: String = "General"): Boolean = withContext(Dispatchers.IO) {
        val cleanPhone = normalizePhone(phone)
        val customer = customerDao.getCustomerByPhone(cleanPhone) ?: return@withContext false
        val timeStr = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val newEntry = "• [$timeStr | $category] ${noteText.trim()}"
        val updatedNotes = if (customer.notes.isNullOrBlank()) newEntry else "$newEntry\u001E${customer.notes}"
        customerDao.upsertCustomer(customer.copy(notes = updatedNotes, updatedAt = System.currentTimeMillis()))
        true
    }

    suspend fun deleteCustomerNote(phone: String, noteIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val cleanPhone = normalizePhone(phone)
        val customer = customerDao.getCustomerByPhone(cleanPhone) ?: return@withContext false
        val currentNotes = customer.notes ?: return@withContext false
        val notesList = currentNotes.split("\u001E").flatMap { it.split("\n\n") }.filter { it.isNotBlank() }.toMutableList()
        if (noteIndex in notesList.indices) {
            notesList.removeAt(noteIndex)
            val updatedNotes = notesList.joinToString("\u001E")
            customerDao.upsertCustomer(customer.copy(notes = updatedNotes, updatedAt = System.currentTimeMillis()))
            true
        } else {
            false
        }
    }

    suspend fun getTopProductsForCustomer(phone: String, limit: Int = 25): List<CustomerProductPurchaseSummary> = withContext(Dispatchers.IO) {
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
                    loyaltyTier = loyalty?.tier ?: existing?.loyaltyTier ?: "Regular",
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
        val existingCust = customerDao.getCustomerByPhone(cleanPhone)
        val existingDue = customerDueDao.getCustomerDueByPhone(cleanPhone)
        val existingLimit = maxOf(existingCust?.creditLimit ?: 0.0, existingDue?.creditLimit ?: 0.0)
        val effectiveLimit = if (!isSessionOwner() || customerDue.creditLimit == 0.0) {
            existingLimit
        } else {
            customerDue.creditLimit
        }
        customerDueDao.upsertCustomerDue(customerDue.copy(customerPhone = cleanPhone, creditLimit = effectiveLimit))
        if (existingCust != null) {
            customerDao.upsertCustomer(
                existingCust.copy(
                    name = if (customerDue.customerName.isNotBlank()) customerDue.customerName else existingCust.name,
                    totalDue = customerDue.outstandingAmount,
                    creditLimit = effectiveLimit,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            customerDao.upsertCustomer(
                Customer(
                    phone = cleanPhone,
                    name = customerDue.customerName,
                    totalDue = customerDue.outstandingAmount,
                    creditLimit = effectiveLimit,
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
            val existingCust = customerDao.getCustomerByPhone(cleanPhone)
            val effectiveLimit = if (isSessionOwner()) {
                creditLimit?.takeIf { it >= 0.0 } ?: existing?.creditLimit ?: existingCust?.creditLimit ?: 0.0
            } else {
                maxOf(existing?.creditLimit ?: 0.0, existingCust?.creditLimit ?: 0.0)
            }
            val existingDue = existing?.outstandingAmount ?: 0.0
            if (effectiveLimit > 0.0 && existingDue + amount > effectiveLimit + 0.001) {
                require(isSessionManagerOrOwner() || consumeCreditLimitOverride(cleanPhone)) {
                    "Supervisor authorization required to exceed customer credit limit"
                }
            }
            val updated = existing?.copy(
                customerName = cleanName.ifBlank { existing.customerName },
                outstandingAmount = existing.outstandingAmount + amount,
                creditLimit = effectiveLimit,
                updatedAt = timestamp
            ) ?: CustomerDue(
                customerPhone = cleanPhone,
                customerName = cleanName,
                outstandingAmount = amount,
                creditLimit = effectiveLimit,
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
        try {
            customerDueDao.recordDuePayment(
                phone = cleanPhone,
                amountPaid = amountPaid,
                timestamp = System.currentTimeMillis(),
                paymentMethod = paymentMethod,
                referenceNote = referenceNote?.trim()?.takeIf { it.isNotEmpty() }
            )
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw IllegalStateException("Failed to record due payment: constraint conflict", e)
        } catch (e: androidx.sqlite.SQLiteException) {
            throw IllegalStateException("Failed to record due payment: constraint conflict", e)
        }
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
                val newOutstanding = currentDue.outstandingAmount - charge.amount
                val excess = if (newOutstanding < 0.0) -newOutstanding else 0.0
                customerDueDao.upsertCustomerDue(
                    currentDue.copy(
                        outstandingAmount = newOutstanding.coerceAtLeast(0.0),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                if (excess > 0.001) {
                    var customer = customerDao.getCustomerByPhone(cleanPhone)
                    if (customer == null) {
                        customer = Customer(
                            phone = cleanPhone,
                            name = currentDue.customerName.ifBlank { "Customer ${cleanPhone.takeLast(4)}" },
                            storeCredit = 0.0
                        )
                        customerDao.upsertCustomer(customer)
                    }
                    customerDao.adjustCustomerStoreCredit(cleanPhone, excess, System.currentTimeMillis())
                    val updated = customerDao.getCustomerByPhone(cleanPhone) ?: customer
                    storeCreditLedgerDao.insertEntry(
                        StoreCreditLedgerEntry(
                            customerPhone = cleanPhone,
                            deltaAmount = excess,
                            balanceAfter = updated.storeCredit,
                            type = "CHARGE_REVERSAL_OVERPAYMENT",
                            note = "Store credit from due charge reversal overpayment (#CHG-$chargeId)"
                        )
                    )
                }
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
        val activeDue = customerDueDao.getActiveCustomerDueByPhone(normalized)
        val outstanding = activeDue?.outstandingAmount ?: 0.0
        if (outstanding > 0.001) {
            throw IllegalStateException("Cannot delete due ledger with outstanding dues of ৳$outstanding")
        }
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
                        createdAt = group.rows.mapNotNull { it.createdAt.takeIf { c -> c > 0 } }.minOrNull() ?: System.currentTimeMillis(),
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

    suspend fun backfillLegacyTaxInclusiveIfConfigured(): Int = withContext(Dispatchers.IO) {
        val isTaxInclusive = settingsDataStore.invoiceSettingsDataFlow.first().taxInclusive
        if (isTaxInclusive) {
            saleDao.backfillTaxInclusiveForLegacySales()
        } else {
            0
        }
    }

    suspend fun recomputeCustomerLoyaltyTiersWithConfiguredThresholds(): Int = withContext(Dispatchers.IO) {
        val loyaltySettings = getLoyaltySettings()
        if (!loyaltySettings.isTiersEnabled) return@withContext 0

        val plat = loyaltySettings.platinumSpendThreshold
        val gold = loyaltySettings.goldSpendThreshold
        val silver = loyaltySettings.silverSpendThreshold

        var updatedCount = 0
        database.withDriverTransaction {
            val loyalties = customerLoyaltyDao.getAllIncludingInactive()
            for (loyalty in loyalties) {
                val correctTier = when {
                    loyalty.lifetimeSpent >= plat -> "Platinum"
                    loyalty.lifetimeSpent >= gold -> "Gold"
                    loyalty.lifetimeSpent >= silver -> "Silver"
                    else -> "Regular"
                }
                if (loyalty.tier != correctTier) {
                    customerLoyaltyDao.upsertLoyalty(loyalty.copy(tier = correctTier, updatedAt = System.currentTimeMillis()))
                    updatedCount++
                }
            }

            val customers = customerDao.getAllCustomersOnce()
            for (customer in customers) {
                val correctTier = when {
                    customer.lifetimeSpent >= plat -> "Platinum"
                    customer.lifetimeSpent >= gold -> "Gold"
                    customer.lifetimeSpent >= silver -> "Silver"
                    else -> "Regular"
                }
                if (customer.loyaltyTier != correctTier) {
                    customerDao.upsertCustomer(customer.copy(loyaltyTier = correctTier, updatedAt = System.currentTimeMillis()))
                    updatedCount++
                }
            }
        }
        updatedCount
    }

    // Settings
    val languageFlow: Flow<String> = settingsDataStore.languageFlow
    val themeModeFlow: Flow<String> = settingsDataStore.themeModeFlow
    val uiLayoutModeFlow: Flow<String> = settingsDataStore.uiLayoutModeFlow
    val defaultCostPercentageFlow: Flow<Double> = settingsDataStore.defaultCostPercentageFlow
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
    val quickSalePresetsFlow: Flow<List<QuickSalePreset>> = settingsDataStore.quickSalePresetsFlow
    val dueReminderTemplatesFlow: Flow<List<DueTemplate>> = settingsDataStore.dueReminderTemplatesFlow

    suspend fun saveDefaultCostPercentage(percentage: Double) = withContext(Dispatchers.IO) {
        settingsDataStore.saveDefaultCostPercentage(percentage)
    }

    suspend fun saveQuickSalePresets(presets: List<QuickSalePreset>) = withContext(Dispatchers.IO) {
        settingsDataStore.saveQuickSalePresets(presets)
    }

    suspend fun saveDueReminderTemplates(templates: List<DueTemplate>) = withContext(Dispatchers.IO) {
        settingsDataStore.saveDueReminderTemplates(templates)
    }

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
    suspend fun exportBackupForSystem(outputFile: File): Boolean = performExportBackup(outputFile)

    suspend fun exportBackup(outputFile: File): Boolean {
        requirePermission(AppPermissions.SYSTEM_BACKUP_RESTORE)
        return performExportBackup(outputFile)
    }

    private suspend fun performExportBackup(outputFile: File): Boolean = withContext(Dispatchers.IO) {
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
                    put("createdAt", p.createdAt)
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
                    put("storeCreditApplied", swi.sale.storeCreditApplied)
                    put("isTaxInclusive", swi.sale.isTaxInclusive)
                    put("voucherCode", swi.sale.voucherCode ?: JSONObject.NULL)
                    put("voucherAmount", swi.sale.voucherAmount)
                    put("customerCreditAmount", swi.sale.customerCreditAmount)
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
                        put("taxable", item.taxable)
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
                    put("createdAt", due.createdAt)
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

            // Sale audit logs
            val saleAuditLogs = saleAuditLogDao.getAll()
            backupJson.put("saleAuditLogs", JSONArray().apply {
                saleAuditLogs.forEach { log ->
                    put(JSONObject().apply {
                        put("id", log.id)
                        put("saleId", log.saleId)
                        put("action", log.action)
                        put("performedBy", log.cashierName)
                        put("cashierName", log.cashierName)
                        put("reason", log.reason)
                        put("grandTotal", log.grandTotal)
                        put("paymentMethod", log.paymentMethod)
                        put("timestamp", log.timestamp)
                    })
                }
            })

            // Expense audit logs  
            val expenseAuditLogs = expenseAuditLogDao.getAll()
            backupJson.put("expenseAuditLogs", JSONArray().apply {
                expenseAuditLogs.forEach { log ->
                    put(JSONObject().apply {
                        put("id", log.id)
                        put("expenseId", log.expenseId)
                        put("action", log.action)
                        put("performedBy", log.userName)
                        put("userName", log.userName)
                        put("reason", log.reason)
                        put("amount", log.amount)
                        put("category", log.category)
                        put("paymentSource", log.paymentSource)
                        put("timestamp", log.timestamp)
                    })
                }
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
                    put("notes", closing.notes ?: ""); put("discrepancyReason", closing.discrepancyReason ?: "")
                    put("timestamp", closing.timestamp); put("cashierName", closing.cashierName)
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

            // Store credit vouchers
            val vouchers = storeCreditVoucherDao.getAllVouchersOnce()
            backupJson.put("storeCreditVouchers", JSONArray().apply {
                vouchers.forEach { v ->
                    put(JSONObject().apply {
                        put("code", v.code)
                        put("initialAmount", v.initialAmount)
                        put("remainingBalance", v.remainingBalance)
                        put("customerPhone", v.customerPhone ?: JSONObject.NULL)
                        put("sourceReturnId", v.sourceReturnId ?: JSONObject.NULL)
                        put("sourceSaleId", v.sourceSaleId ?: JSONObject.NULL)
                        put("issuedAt", v.issuedAt)
                        put("expiresAt", v.expiresAt ?: JSONObject.NULL)
                        put("status", v.status)
                        put("notes", v.notes ?: JSONObject.NULL)
                    })
                }
            })

            // Store credit ledger
            val scLedger = storeCreditLedgerDao.getAllEntries()
            backupJson.put("storeCreditLedger", JSONArray().apply {
                scLedger.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id)
                        put("customerPhone", e.customerPhone ?: JSONObject.NULL)
                        put("deltaAmount", e.deltaAmount)
                        put("balanceAfter", e.balanceAfter)
                        put("type", e.type)
                        put("referenceInvoiceId", e.referenceInvoiceId ?: JSONObject.NULL)
                        put("voucherCode", e.voucherCode ?: JSONObject.NULL)
                        put("note", e.note ?: JSONObject.NULL)
                        put("timestamp", e.timestamp)
                    })
                }
            })
            // Preferences-backed business configuration (not part of the Room database): quick-sale
            // presets, note presets, due-reminder message templates, and custom product categories.
            backupJson.put("quickSalePresets", QuickSalePreset.listToJson(quickSalePresetsList))
            backupJson.put("notePresets", JSONArray().apply { notePresetsList.forEach { put(it) } })
            backupJson.put("dueReminderTemplates", DueTemplate.listToJson(dueTemplatesList))
            backupJson.put("customCategories", CategoryItem.listToJson(categoriesList))
            settingsDataStore.returnAuditPresetsFlow.first()?.let { backupJson.put("returnAuditPresets", it) }
            backupJson.put("themeMode", settingsDataStore.themeModeFlow.first())
            backupJson.put("uiLayoutMode", settingsDataStore.uiLayoutModeFlow.first())
            backupJson.put("defaultCostPercentage", settingsDataStore.defaultCostPercentageFlow.first())
            val loyaltySettings = com.example.techyfipos.utils.LoyaltySettingsHelper.getSettings(context)
            backupJson.put("loyaltyProgramSettings", JSONObject().apply {
                put("takaPerPoint", loyaltySettings.takaPerPoint)
                put("pointCashValue", loyaltySettings.pointCashValue)
                put("minRedeemPoints", loyaltySettings.minRedeemPoints)
                put("maxBillPercentPayable", loyaltySettings.maxBillPercentPayable)
                put("silverSpendThreshold", loyaltySettings.silverSpendThreshold)
                put("goldSpendThreshold", loyaltySettings.goldSpendThreshold)
                put("platinumSpendThreshold", loyaltySettings.platinumSpendThreshold)
                put("pointsExpiryDays", loyaltySettings.pointsExpiryDays)
                put("isTiersEnabled", loyaltySettings.isTiersEnabled)
            })

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
            val currentShopTaxInclusive = settingsDataStore.invoiceSettingsDataFlow.first().taxInclusive

            if (mode == BackupImportMode.REPLACE) createPortableRestoreSnapshot()

            database.withDriverTransaction {
              if (mode == BackupImportMode.REPLACE) clearBusinessTablesForRestore()

              val newlyInsertedProductBarcodes = mutableSetOf<String>()
              val newlyInsertedDuePhones = mutableSetOf<String>()
              val newlyInsertedCustomerPhones = mutableSetOf<String>()
              val newlyInsertedLoyaltyPhones = mutableSetOf<String>()
              val newlyInsertedSupplierIds = mutableSetOf<Long>()
              val newlyInsertedInvoiceNumbersBySupplier = mutableMapOf<Long, MutableSet<String>>()

              val newlyInsertedSaleItems = mutableListOf<SaleItem>()
              val newlyInsertedReturnItems = mutableListOf<ReturnItem>()
              val newlyInsertedPurchaseItems = mutableListOf<PurchaseInvoiceItem>()
              val newlyInsertedAdjustments = mutableListOf<InventoryAdjustment>()

              // Products import
              if (backupJson.has("products")) {
                val productsArray = backupJson.getJSONArray("products")
                for (i in 0 until productsArray.length()) {
                    val p = productsArray.getJSONObject(i)
                    val rawBarcode = p.getString("barcode")
                    val canonicalBarcode = rawBarcode.trim().lowercase()
                    val existing = productDao.getProductByBarcodeCaseInsensitive(canonicalBarcode)
                    val product = Product(
                        barcode = canonicalBarcode,
                        name = p.optString("name", rawBarcode),
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
                        ,createdAt = p.optLong("createdAt", p.optLong("created_at", System.currentTimeMillis()))
                    )
                    if (existing != null) {
                        if (mode != BackupImportMode.MERGE) {
                            productDao.updateProduct(product.copy(barcode = existing.barcode.lowercase()))
                        }
                    } else {
                        productDao.insertProduct(product.copy(barcode = canonicalBarcode))
                        if (mode == BackupImportMode.MERGE) {
                            newlyInsertedProductBarcodes.add(canonicalBarcode)
                        }
                    }
                }
              }

              // Dues import
              if (backupJson.has("dues")) {
                val duesArray = backupJson.getJSONArray("dues")
                for (i in 0 until duesArray.length()) {
                    val d = duesArray.getJSONObject(i)
                    val phone = normalizePhone(d.getString("customerPhone"))
                    val due = CustomerDue(
                        customerPhone = phone,
                        customerName = d.optString("customerName", phone),
                        outstandingAmount = d.optDouble("outstandingAmount", d.optDouble("amount", 0.0)),
                        creditLimit = d.optDouble("creditLimit", 0.0),
                        createdAt = d.optLong("createdAt", d.optLong("updatedAt", System.currentTimeMillis())),
                        updatedAt = d.optLong("updatedAt", System.currentTimeMillis()),
                        isArchived = d.optBoolean("isArchived", false)
                    )
                    val existingDue = customerDueDao.getCustomerDueByPhone(phone)
                    if (mode == BackupImportMode.MERGE) {
                        if (existingDue == null) {
                            customerDueDao.upsertCustomerDue(due)
                            newlyInsertedDuePhones.add(phone)
                            val existingCustomer = customerDao.getCustomerByPhone(phone)
                            if (existingCustomer != null) {
                                customerDao.updateCustomerTotalDue(phone, due.outstandingAmount, System.currentTimeMillis())
                            }
                        }
                    } else {
                        customerDueDao.upsertCustomerDue(due)
                    }
                }
              }

              // Due Payments import
              val existingDuePaymentsInDb = if (mode == BackupImportMode.MERGE) customerDueDao.getAllDuePaymentsOnce() else emptyList()
              if (backupJson.has("duePayments")) {
                val paymentsArray = backupJson.getJSONArray("duePayments")
                for (i in 0 until paymentsArray.length()) {
                    val p = paymentsArray.getJSONObject(i)
                    val phone = normalizePhone(p.getString("customerPhone"))
                    val amt = p.optDouble("amountPaid", p.optDouble("amount", 0.0))
                    val ts = p.optLong("timestamp", System.currentTimeMillis())
                    val payment = DuePayment(
                        id = if (mode == BackupImportMode.MERGE) 0L else p.optLong("id", 0L),
                        customerPhone = phone,
                        amountPaid = amt,
                        timestamp = ts,
                        paymentMethod = p.optString("paymentMethod", "Cash"),
                        referenceNote = p.optString("referenceNote").takeIf { it.isNotBlank() }
                    )
                    val existingDue = customerDueDao.getCustomerDueByPhone(phone)
                    if (mode == BackupImportMode.MERGE && existingDue == null && phone !in newlyInsertedDuePhones) {
                        continue
                    }
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingDuePaymentsInDb.any {
                            it.customerPhone == phone && kotlin.math.abs(it.amountPaid - amt) < 0.01 && it.timestamp == ts
                        }
                        if (isDuplicate) continue
                    }
                    try {
                        customerDueDao.insertDuePayment(payment)
                        if (mode == BackupImportMode.MERGE && phone !in newlyInsertedDuePhones) {
                            customerDueDao.adjustOutstandingDue(phone, -amt, ts)
                            customerDao.adjustCustomerTotalDue(phone, -amt, ts)
                        }
                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                        if (mode != BackupImportMode.MERGE) throw IllegalStateException("Failed to restore due payment: duplicate record conflict", e)
                    }
                }
              }

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
                        loyaltyPointsRedeemed = s.optInt("loyaltyPointsRedeemed", 0),
                        storeCreditApplied = s.optionalDouble("storeCreditApplied"),
                        isTaxInclusive = if (s.has("isTaxInclusive")) s.optionalBoolean("isTaxInclusive") else currentShopTaxInclusive,
                        voucherCode = if (s.has("voucherCode") && !s.isNull("voucherCode")) s.optString("voucherCode").takeIf { it.isNotBlank() } else null,
                        voucherAmount = s.optionalDouble("voucherAmount"),
                        customerCreditAmount = s.optionalDouble("customerCreditAmount")
                    )
                    val saleId = try {
                        saleDao.insertSale(sale)
                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                        throw IllegalStateException("Failed to restore sale: duplicate record conflict", e)
                    } catch (e: androidx.sqlite.SQLiteException) {
                        throw IllegalStateException("Failed to restore sale: duplicate record conflict", e)
                    }
                    if (mode == BackupImportMode.MERGE) saleIdMap[backupSaleId] = saleId
                    val itemsArray = s.optJSONArray("items") ?: s.optJSONArray("saleItems") ?: JSONArray()
                    val items = mutableListOf<SaleItem>()
                    for (j in 0 until itemsArray.length()) {
                        val itemObj = itemsArray.getJSONObject(j)
                        items.add(buildBackwardCompatibleSaleItem(
                            saleId = saleId,
                            productBarcode = itemObj.optString("productBarcode", itemObj.optString("barcode")).trim().lowercase(),
                            productName = itemObj.optString("productName", itemObj.optString("name")),
                            price = itemObj.optionalDouble("price") ?: itemObj.optionalDouble("sellingPrice"),
                            quantity = itemObj.optionalDouble("quantity") ?: itemObj.optionalDouble("qty"),
                            note = itemObj.optString("note").takeIf { it.isNotBlank() },
                            purchasePriceAtSale = itemObj.optionalDouble("purchasePriceAtSale"),
                            taxable = itemObj.optBoolean("taxable", true)
                        ))
                    }
                    if (items.isNotEmpty()) {
                        try {
                            saleDao.insertSaleItems(items)
                            if (mode == BackupImportMode.MERGE) newlyInsertedSaleItems.addAll(items)
                        } catch (e: android.database.sqlite.SQLiteConstraintException) {
                            throw IllegalStateException("Failed to restore sale items: duplicate record conflict", e)
                        } catch (e: androidx.sqlite.SQLiteException) {
                            throw IllegalStateException("Failed to restore sale items: duplicate record conflict", e)
                        }
                    }
                }
              }

              // Due Charges import
              val existingDueChargesInDb = if (mode == BackupImportMode.MERGE) customerDueDao.getAllDueChargesOnce() else emptyList()
              if (backupJson.has("dueCharges")) {
                val chargesArray = backupJson.getJSONArray("dueCharges")
                for (i in 0 until chargesArray.length()) {
                    val c = chargesArray.getJSONObject(i)
                    val phone = normalizePhone(c.getString("customerPhone"))
                    val amt = c.optDouble("amount", 0.0)
                    val ts = c.optLong("timestamp", System.currentTimeMillis())
                    val backupSaleId = c.optLong("saleId").takeIf { c.has("saleId") && !c.isNull("saleId") }
                    val resolvedSaleId = if (mode == BackupImportMode.MERGE && backupSaleId != null) {
                        saleIdMap[backupSaleId]
                    } else backupSaleId
                    val charge = DueCharge(
                        id = if (mode == BackupImportMode.MERGE) 0L else c.optLong("id", 0L),
                        customerPhone = phone,
                        amount = amt,
                        timestamp = ts,
                        reason = c.optString("reason", "Manual"),
                        saleId = resolvedSaleId,
                        note = c.optString("note").takeIf { it.isNotBlank() }
                    )
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingDueChargesInDb.any {
                            it.customerPhone == phone && kotlin.math.abs(it.amount - amt) < 0.01 &&
                            it.timestamp == ts && it.saleId == resolvedSaleId
                        }
                        if (isDuplicate) continue
                    }
                    customerDueDao.insertDueCharge(charge)
                    if (mode == BackupImportMode.MERGE && phone !in newlyInsertedDuePhones) {
                        customerDueDao.adjustOutstandingDue(phone, amt, ts)
                        customerDao.adjustCustomerTotalDue(phone, amt, ts)
                    }
                }
              }

              val returnIdMap = mutableMapOf<Long, Long>()
              val existingReturnsInDb = if (mode == BackupImportMode.MERGE) returnDao.getAllReturnsOnce() else emptyList()
              if (backupJson.has("returns")) {
                val array = backupJson.getJSONArray("returns")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupReturnId = value.optLong("id", 0L)
                    val backupSaleId = value.getLong("saleId")
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    val refundAmt = value.optDouble("refundAmount", 0.0)
                    val resolvedSaleId = if (mode == BackupImportMode.MERGE) (saleIdMap[backupSaleId] ?: 0L) else backupSaleId
                    if (mode == BackupImportMode.MERGE && (resolvedSaleId == 0L)) {
                        continue // Skip orphan return whose parent sale is missing
                    }
                    if (mode == BackupImportMode.MERGE) {
                        val duplicate = existingReturnsInDb.firstOrNull {
                            it.saleId == resolvedSaleId && it.timestamp == ts &&
                            kotlin.math.abs(it.refundAmount - refundAmt) < 0.01
                        }
                        if (duplicate != null) {
                            returnIdMap[backupReturnId] = duplicate.id
                            continue
                        }
                    }
                    val newReturnId = returnDao.insertReturn(Return(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupReturnId, saleId = resolvedSaleId,
                        timestamp = ts,
                        refundMethod = value.optString("refundMethod", "Cash"),
                        refundAmount = refundAmt,
                        externalRefundAmount = if (value.has("externalRefundAmount")) {
                            value.optDouble("externalRefundAmount", 0.0)
                        } else refundAmt,
                        dueAdjustmentAmount = value.optDouble("dueAdjustmentAmount", 0.0),
                        note = value.optString("note").takeIf { it.isNotBlank() },
                        performedByUserId = value.optLong("performedByUserId").takeIf { value.has("performedByUserId") && !value.isNull("performedByUserId") },
                        performedByName = value.optString("performedByName").takeIf { it.isNotBlank() }
                    ))
                    if (mode == BackupImportMode.MERGE) returnIdMap[backupReturnId] = newReturnId
                }
              }

              val existingReturnItemsInDb = if (mode == BackupImportMode.MERGE) returnDao.getAllReturnItemsOnce() else emptyList()
              if (backupJson.has("returnItems")) {
                val array = backupJson.getJSONArray("returnItems")
                val records = mutableListOf<ReturnItem>()
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupReturnId = value.getLong("returnId")
                    val resolvedReturnId = if (mode == BackupImportMode.MERGE) returnIdMap[backupReturnId] else backupReturnId
                    if (resolvedReturnId == null || resolvedReturnId == 0L) {
                        continue // Skip orphan return item whose parent return was skipped or missing
                    }
                    val barcode = value.optString("productBarcode").trim().lowercase()
                    val price = value.optDouble("price", 0.0)
                    val qty = value.optDouble("quantity", 1.0)
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingReturnItemsInDb.any {
                            it.returnId == resolvedReturnId && it.productBarcode.equals(barcode, ignoreCase = true) &&
                            kotlin.math.abs(it.price - price) < 0.01 && kotlin.math.abs(it.quantity - qty) < 0.01
                        }
                        if (isDuplicate) continue
                    }
                    records += ReturnItem(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), returnId = resolvedReturnId,
                        productBarcode = barcode, productName = value.optString("productName"),
                        price = price, quantity = qty
                    )
                }
                if (records.isNotEmpty()) {
                    returnDao.insertReturnItems(records)
                    if (mode == BackupImportMode.MERGE) newlyInsertedReturnItems.addAll(records)
                }
              }

              val existingHeldCartsInDb = if (mode == BackupImportMode.MERGE) heldCartDao.getAllHeldCartsOnce() else emptyList()
              if (backupJson.has("heldCarts")) {
                val array = backupJson.getJSONArray("heldCarts")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val title = value.optString("title", "Held cart")
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingHeldCartsInDb.any { it.title == title && it.timestamp == ts }
                        if (isDuplicate) continue
                    }
                    heldCartDao.insertHeldCart(HeldCart(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), title = title,
                        customerName = value.optString("customerName").takeIf { it.isNotBlank() },
                        customerPhone = value.optString("customerPhone").takeIf { it.isNotBlank() },
                        itemsJson = value.optString("itemsJson", "[]"),
                        discountAmount = value.optDouble("discountAmount", 0.0),
                        isPercentageDiscount = value.optBoolean("isPercentageDiscount", false),
                        timestamp = ts,
                        note = value.optString("note").takeIf { it.isNotBlank() }
                    ))
                }
              }

              val expenseIdMap = mutableMapOf<Long, Long>()
              val existingExpensesInDb = if (mode == BackupImportMode.MERGE) expenseDao.getAllExpensesOnce() else emptyList()
              if (backupJson.has("expenses")) {
                val array = backupJson.getJSONArray("expenses")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupExpenseId = value.optLong("id", 0L)
                    val title = value.optString("title", "Expense")
                    val amt = value.optDouble("amount", 0.0)
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    if (mode == BackupImportMode.MERGE) {
                        val duplicate = existingExpensesInDb.firstOrNull {
                            it.title == title && kotlin.math.abs(it.amount - amt) < 0.01 && it.timestamp == ts
                        }
                        if (duplicate != null) {
                            expenseIdMap[backupExpenseId] = duplicate.id
                            continue
                        }
                    }
                    val newExpenseId = expenseDao.insertExpense(Expense(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupExpenseId, title = title,
                        category = value.optString("category", "Other"), type = value.optString("type", "EXPENSE"),
                        amount = amt, paymentSource = value.optString("paymentSource", "Cash"),
                        note = value.optString("note").takeIf { it.isNotBlank() },
                        timestamp = ts,
                        cashierName = value.optString("cashierName", "")
                    ))
                    if (mode == BackupImportMode.MERGE && backupExpenseId != 0L) {
                        expenseIdMap[backupExpenseId] = newExpenseId
                    }
                }
              }

              val supplierIdMap = mutableMapOf<Long, Long>()
              val existingSuppliersInDb = if (mode == BackupImportMode.MERGE) supplierDao.getAllSuppliersOnce() else emptyList()
              if (backupJson.has("suppliers")) {
                val array = backupJson.getJSONArray("suppliers")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupSupplierId = value.optLong("id", 0L)
                    val sName = value.optString("name", "Supplier")
                    val sPhone = value.optString("phone")
                    if (mode == BackupImportMode.MERGE) {
                        val duplicate = existingSuppliersInDb.firstOrNull {
                            if (it.phone.isNotBlank() && sPhone.isNotBlank()) {
                                normalizePhone(it.phone) == normalizePhone(sPhone) && it.name.equals(sName, ignoreCase = true)
                            } else {
                                it.name.equals(sName, ignoreCase = true) && it.phone.isBlank() && sPhone.isBlank()
                            }
                        }
                        if (duplicate != null) {
                            supplierIdMap[backupSupplierId] = duplicate.id
                            continue
                        }
                    }
                    val newSupplierId = supplierDao.upsertSupplier(Supplier(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupSupplierId, name = sName,
                        companyName = value.optString("companyName"), phone = sPhone,
                        address = value.optString("address").takeIf { it.isNotBlank() },
                        totalPurchased = value.optDouble("totalPurchased", 0.0), totalDue = value.optDouble("totalDue", 0.0),
                        notes = value.optString("notes").takeIf { it.isNotBlank() },
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis())
                    ))
                    if (mode == BackupImportMode.MERGE) {
                        supplierIdMap[backupSupplierId] = newSupplierId
                        newlyInsertedSupplierIds.add(newSupplierId)
                    }
                }
              }
              val purchaseInvoiceIdMap = mutableMapOf<Long, Long>()
              val existingInvoicesInDb = if (mode == BackupImportMode.MERGE) purchaseInvoiceDao.getAllInvoicesOnce() else emptyList()
              if (backupJson.has("purchaseInvoices")) {
                val array = backupJson.getJSONArray("purchaseInvoices")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupInvoiceId = value.optLong("id", 0L)
                    val backupSupplierId = value.getLong("supplierId")
                    val invNum = value.optString("invoiceNumber")
                    val totalAmt = value.optDouble("totalAmount", 0.0)
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    val resolvedSupplierId = if (mode == BackupImportMode.MERGE) supplierIdMap[backupSupplierId] else backupSupplierId
                    if (mode == BackupImportMode.MERGE && resolvedSupplierId == null) continue
                    if (mode == BackupImportMode.MERGE) {
                        val duplicate = existingInvoicesInDb.firstOrNull {
                            it.supplierId == resolvedSupplierId &&
                            ((invNum.isNotBlank() && it.invoiceNumber == invNum) || (it.timestamp == ts && kotlin.math.abs(it.totalAmount - totalAmt) < 0.01))
                        }
                        if (duplicate != null) {
                            purchaseInvoiceIdMap[backupInvoiceId] = duplicate.id
                            continue
                        }
                    }
                    val newInvoiceId = purchaseInvoiceDao.insertPurchaseInvoice(PurchaseInvoice(
                        id = if (mode == BackupImportMode.MERGE) 0L else backupInvoiceId, supplierId = resolvedSupplierId!!,
                        invoiceNumber = invNum, totalAmount = totalAmt,
                        paidAmount = value.optDouble("paidAmount", 0.0), dueAmount = value.optDouble("dueAmount", 0.0),
                        itemsSummary = value.optString("itemsSummary"), paymentMethod = value.optString("paymentMethod", "Cash"),
                        timestamp = ts,
                        note = value.optString("note").takeIf { it.isNotBlank() }
                  ))
                    if (mode == BackupImportMode.MERGE) {
                        purchaseInvoiceIdMap[backupInvoiceId] = newInvoiceId
                        if (invNum.isNotBlank() && resolvedSupplierId != null) {
                            newlyInsertedInvoiceNumbersBySupplier.getOrPut(resolvedSupplierId) { mutableSetOf() }.add(invNum)
                        }
                        if (resolvedSupplierId !in newlyInsertedSupplierIds) {
                            val dueChange = value.optDouble("dueAmount", 0.0)
                            supplierDao.updateSupplierBalances(resolvedSupplierId, purchaseAmount = totalAmt, dueChange = dueChange, timestamp = ts)
                        }
                    }
                }
              }

              val existingInvoiceItemsInDb = if (mode == BackupImportMode.MERGE) purchaseInvoiceDao.getAllPurchaseInvoiceItemsOnce() else emptyList()
              if (backupJson.has("purchaseInvoiceItems")) {
                val array = backupJson.getJSONArray("purchaseInvoiceItems")
                val records = mutableListOf<PurchaseInvoiceItem>()
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupInvoiceId = value.getLong("invoiceId")
                    val resolvedInvoiceId = if (mode == BackupImportMode.MERGE) purchaseInvoiceIdMap[backupInvoiceId] ?: 0L else backupInvoiceId
                    if (mode == BackupImportMode.MERGE && resolvedInvoiceId == 0L) continue
                    val barcode = value.optString("productBarcode")
                    val qty = value.optDouble("quantity", 0.0)
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingInvoiceItemsInDb.any {
                            it.invoiceId == resolvedInvoiceId && it.productBarcode.equals(barcode, ignoreCase = true) &&
                            kotlin.math.abs(it.quantity - qty) < 0.01
                        }
                        if (isDuplicate) continue
                    }
                    records += PurchaseInvoiceItem(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), invoiceId = resolvedInvoiceId,
                        productBarcode = barcode,
                        productName = value.optString("productName"),
                        quantity = qty, unitCost = value.optDouble("unitCost", 0.0)
                    )
                }
                if (records.isNotEmpty()) {
                    purchaseInvoiceDao.insertPurchaseInvoiceItems(records)
                    if (mode == BackupImportMode.MERGE) newlyInsertedPurchaseItems.addAll(records)
                }
              }

              val existingSupplierPaymentsInDb = if (mode == BackupImportMode.MERGE) supplierDao.getAllSupplierPaymentsOnce() else emptyList()
              if (backupJson.has("supplierPayments")) {
                val array = backupJson.getJSONArray("supplierPayments")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val backupSupplierId = value.getLong("supplierId")
                    val amtPaid = value.optDouble("amountPaid", 0.0)
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    val resolvedSupplierId = if (mode == BackupImportMode.MERGE) supplierIdMap[backupSupplierId] else backupSupplierId
                    if (mode == BackupImportMode.MERGE && resolvedSupplierId == null) continue
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingSupplierPaymentsInDb.any {
                            it.supplierId == resolvedSupplierId && kotlin.math.abs(it.amountPaid - amtPaid) < 0.01 &&
                            it.timestamp == ts
                        }
                        if (isDuplicate) continue
                    }
                    supplierDao.insertSupplierPayment(SupplierPayment(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), supplierId = resolvedSupplierId!!,
                        amountPaid = amtPaid,
                        paymentMethod = value.optString("paymentMethod", "Cash"),
                        referenceNote = value.optString("referenceNote").takeIf { it.isNotBlank() },
                        timestamp = ts,
                        performedByName = value.optString("performedByName").takeIf { it.isNotBlank() }
                    ))
                    val refNote = value.optString("referenceNote").takeIf { it.isNotBlank() }
                    val matchedRestoredInvoice = if (refNote != null && refNote.contains("Paid on Invoice", ignoreCase = true)) {
                        val candidateInvoices = newlyInsertedInvoiceNumbersBySupplier[resolvedSupplierId].orEmpty()
                        candidateInvoices.any { inv -> refNote.contains(inv, ignoreCase = true) }
                    } else {
                        false
                    }
                    if (mode == BackupImportMode.MERGE && resolvedSupplierId !in newlyInsertedSupplierIds && !matchedRestoredInvoice) {
                        supplierDao.recordSupplierPayment(resolvedSupplierId, amtPaid, ts)
                    }
                }
              }

              val existingClosingsInDb = if (mode == BackupImportMode.MERGE) registerClosingDao.getAllClosingsOnce() else emptyList()
              if (backupJson.has("registerClosings")) {
                val array = backupJson.getJSONArray("registerClosings")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val dateStr = value.optString("date")
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingClosingsInDb.any { it.date == dateStr && it.timestamp == ts }
                        if (isDuplicate) continue
                    }
                    registerClosingDao.insertClosing(RegisterClosing(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L), date = dateStr,
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
                        discrepancyReason = value.optString("discrepancyReason").takeIf { it.isNotBlank() },
                        timestamp = ts,
                        cashierName = value.optString("cashierName", "")
                    ))
                }
              }

              if (backupJson.has("customerLoyalty")) {
                val array = backupJson.getJSONArray("customerLoyalty")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val phone = normalizePhone(value.getString("phone"))
                    val loyalty = CustomerLoyalty(
                        phone = phone, name = value.optString("name", "Customer"),
                        pointsBalance = value.optInt("pointsBalance", 0), tier = value.optString("tier", "Regular"),
                        lifetimeSpent = value.optDouble("lifetimeSpent", 0.0),
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis()),
                        isActive = value.optBoolean("isActive", true)
                    )
                    val existingLoyalty = customerLoyaltyDao.getLoyaltyByPhone(phone)
                    if (mode == BackupImportMode.MERGE) {
                        if (existingLoyalty == null) {
                            customerLoyaltyDao.upsertLoyalty(loyalty)
                            newlyInsertedLoyaltyPhones.add(phone)
                        }
                    } else {
                        customerLoyaltyDao.upsertLoyalty(loyalty)
                    }
                }
              }

              val existingLoyaltyTxnsInDb = if (mode == BackupImportMode.MERGE) loyaltyTransactionDao.getAllOnce() else emptyList()
              if (backupJson.has("loyaltyTransactions")) {
                val array = backupJson.getJSONArray("loyaltyTransactions")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val phone = normalizePhone(value.optString("customerPhone"))
                    val pts = value.optInt("pointsChange", 0)
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    val typeStr = value.optString("type", "ADJUSTMENT")
                    val backupSaleId = value.optLong("saleId").takeIf { value.has("saleId") && !value.isNull("saleId") }
                    val resolvedSaleId = if (mode == BackupImportMode.MERGE && backupSaleId != null) {
                        saleIdMap[backupSaleId]
                    } else backupSaleId
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingLoyaltyTxnsInDb.any {
                            it.customerPhone == phone && it.pointsChange == pts &&
                            it.timestamp == ts && it.type == typeStr
                        }
                        if (isDuplicate) continue
                    }
                    loyaltyTransactionDao.insert(LoyaltyTransaction(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L),
                        customerPhone = phone,
                        pointsChange = pts,
                        spendChange = value.optDouble("spendChange", 0.0),
                        type = typeStr,
                        reason = value.optString("reason", "Imported adjustment"),
                        saleId = resolvedSaleId,
                        timestamp = ts,
                        performedByName = value.optString("performedByName").takeIf { it.isNotBlank() }
                    ))
                    if (mode == BackupImportMode.MERGE && phone !in newlyInsertedLoyaltyPhones) {
                        customerLoyaltyDao.addPoints(phone, pts, value.optDouble("spendChange", 0.0), ts)
                        customerDao.adjustCustomerLoyaltyPoints(phone, pts, ts)
                    }
                }
              }
              if (backupJson.has("registerShiftState")) {
                  val value = backupJson.getJSONObject("registerShiftState")
                  registerShiftStateDao.upsert(RegisterShiftState(
                      startedAt = value.optLong("startedAt", System.currentTimeMillis()),
                      startingFloat = value.optDouble("startingFloat", 0.0)
                  ))
              }

              val existingAdjustmentsInDb = if (mode == BackupImportMode.MERGE) inventoryAdjustmentDao.getAllAdjustmentsOnce() else emptyList()
              if (backupJson.has("inventoryAdjustments")) {
                val array = backupJson.getJSONArray("inventoryAdjustments")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val barcode = value.optString("productBarcode")
                    val diff = value.optDouble("difference", 0.0)
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingAdjustmentsInDb.any {
                            it.productBarcode.equals(barcode, ignoreCase = true) &&
                            kotlin.math.abs(it.difference - diff) < 0.0001 &&
                            it.timestamp == ts
                        }
                        if (isDuplicate) continue
                    }
                    val adjustment = InventoryAdjustment(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L),
                        productBarcode = barcode, productName = value.optString("productName"),
                        previousStock = value.optDouble("previousStock", 0.0), newStock = value.optDouble("newStock", 0.0),
                        difference = diff, reason = value.optString("reason", "Other"),
                        note = value.optString("note").takeIf { it.isNotBlank() },
                        timestamp = ts,
                        cashierName = value.optString("cashierName", "")
                    )
                    inventoryAdjustmentDao.insertAdjustment(adjustment)
                    if (mode == BackupImportMode.MERGE) newlyInsertedAdjustments.add(adjustment)
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

              val existingLogsInDb = if (mode == BackupImportMode.MERGE) dueReminderLogDao.getAllLogsOnce() else emptyList()
              if (backupJson.has("dueReminderLogs")) {
                val array = backupJson.getJSONArray("dueReminderLogs")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val phone = normalizePhone(value.getString("customerPhone"))
                    val ts = value.optLong("timestamp", System.currentTimeMillis())
                    if (mode == BackupImportMode.MERGE) {
                        val isDuplicate = existingLogsInDb.any { it.customerPhone == phone && it.timestamp == ts }
                        if (isDuplicate) continue
                    }
                    dueReminderLogDao.insertLog(DueReminderLog(
                        id = if (mode == BackupImportMode.MERGE) 0L else value.optLong("id", 0L),
                        customerPhone = phone, customerName = value.optString("customerName", "Customer"),
                        dueAmount = value.optDouble("dueAmount", 0.0), channel = value.optString("channel", "WHATSAPP"),
                        messageText = value.optString("messageText", ""),
                        timestamp = ts,
                        status = value.optString("status", "SENT")
                    ))
                }
              }
              if (backupJson.has("customers")) {
                val array = backupJson.getJSONArray("customers")
                for (i in 0 until array.length()) {
                    val value = array.getJSONObject(i)
                    val phone = normalizePhone(value.getString("phone"))
                    val customer = Customer(
                        phone = phone,
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
                        loyaltyTier = value.optString("loyaltyTier", "Regular"),
                        firstPurchaseDate = value.optLong("firstPurchaseDate").takeIf { value.has("firstPurchaseDate") && !value.isNull("firstPurchaseDate") },
                        lastPurchaseDate = value.optLong("lastPurchaseDate").takeIf { value.has("lastPurchaseDate") && !value.isNull("lastPurchaseDate") },
                        createdAt = value.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = value.optLong("updatedAt", System.currentTimeMillis()),
                        isArchived = value.optBoolean("isArchived", false)
                    )
                    val existingCustomer = customerDao.getCustomerByPhone(phone)
                    if (mode == BackupImportMode.MERGE) {
                        if (existingCustomer == null) {
                            customerDao.upsertCustomer(customer)
                            newlyInsertedCustomerPhones.add(phone)
                        }
                    } else {
                        customerDao.upsertCustomer(customer)
                    }
                }
              }

              // Restore store credit vouchers
              backupJson.optJSONArray("storeCreditVouchers")?.let { arr ->
                  for (i in 0 until arr.length()) {
                      val obj = arr.getJSONObject(i)
                      val codeStr = obj.getString("code")
                      if (mode == BackupImportMode.MERGE) {
                          val existingVoucher = storeCreditVoucherDao.getVoucherByCode(codeStr)
                          if (existingVoucher != null) continue // Skip duplicate voucher code (Fixes B-1)
                      }

                      val backupSaleId = obj.optLong("sourceSaleId").takeIf { obj.has("sourceSaleId") && !obj.isNull("sourceSaleId") }
                          ?: obj.optLong("saleId").takeIf { obj.has("saleId") && !obj.isNull("saleId") }
                      val resolvedSaleId = if (mode == BackupImportMode.MERGE && backupSaleId != null) {
                          saleIdMap[backupSaleId]
                      } else backupSaleId

                      val backupReturnId = obj.optLong("sourceReturnId").takeIf { obj.has("sourceReturnId") && !obj.isNull("sourceReturnId") }
                          ?: obj.optLong("returnId").takeIf { obj.has("returnId") && !obj.isNull("returnId") }
                      val resolvedReturnId = if (mode == BackupImportMode.MERGE && backupReturnId != null) {
                          returnIdMap[backupReturnId]
                      } else backupReturnId

                      val initialAmt = if (obj.has("initialAmount")) obj.optDouble("initialAmount", 0.0) else obj.optDouble("originalAmount", 0.0)
                      val phone = if (obj.isNull("customerPhone")) null else normalizePhone(obj.getString("customerPhone"))
                      storeCreditVoucherDao.insertVoucher(StoreCreditVoucher(
                          code = codeStr,
                          initialAmount = initialAmt,
                          remainingBalance = obj.optDouble("remainingBalance", initialAmt),
                          customerPhone = phone,
                          sourceReturnId = resolvedReturnId,
                          sourceSaleId = resolvedSaleId,
                          issuedAt = obj.optLong("issuedAt", System.currentTimeMillis()),
                          expiresAt = if (obj.isNull("expiresAt")) null else obj.optLong("expiresAt"),
                          status = obj.optString("status", "ACTIVE"),
                          notes = if (obj.isNull("notes")) null else obj.optString("notes")
                      ))
                  }
              }

              // Restore store credit ledger
              val existingEntriesInDb = if (mode == BackupImportMode.MERGE) storeCreditLedgerDao.getAllEntries() else emptyList()
              backupJson.optJSONArray("storeCreditLedger")?.let { arr ->
                  for (i in 0 until arr.length()) {
                      val obj = arr.getJSONObject(i)
                      val delta = if (obj.has("deltaAmount")) obj.optDouble("deltaAmount", 0.0) else obj.optDouble("amount", 0.0)
                      val phone = if (obj.isNull("customerPhone") || obj.optString("customerPhone") == "VOUCHER" || obj.optString("customerPhone").isBlank()) null else normalizePhone(obj.getString("customerPhone"))
                      val typeStr = obj.optString("type", "MANUAL_ADJUST")
                      val backupSaleId = when {
                          obj.has("referenceInvoiceId") && !obj.isNull("referenceInvoiceId") -> obj.optLong("referenceInvoiceId")
                          obj.has("saleId") && !obj.isNull("saleId") -> obj.optLong("saleId")
                          else -> null
                      }
                      val resolvedSaleId = if (mode == BackupImportMode.MERGE && backupSaleId != null) {
                          saleIdMap[backupSaleId]
                      } else backupSaleId

                      val noteText = when {
                          obj.has("note") && !obj.isNull("note") -> obj.optString("note")
                          obj.has("notes") && !obj.isNull("notes") -> obj.optString("notes")
                          else -> null
                      }
                      val ts = when {
                          obj.has("timestamp") -> obj.optLong("timestamp", System.currentTimeMillis())
                          obj.has("createdAt") -> obj.optLong("createdAt", System.currentTimeMillis())
                          else -> System.currentTimeMillis()
                      }
                      if (mode == BackupImportMode.MERGE) {
                          val isDuplicate = existingEntriesInDb.any {
                              it.customerPhone == phone && kotlin.math.abs(it.deltaAmount - delta) < 0.01 &&
                              it.timestamp == ts && it.type == typeStr
                          }
                          if (isDuplicate) continue
                      }
                      storeCreditLedgerDao.insertEntry(StoreCreditLedgerEntry(
                          id = if (mode == BackupImportMode.MERGE) 0L else obj.optLong("id", 0L),
                          customerPhone = phone,
                          deltaAmount = delta,
                          balanceAfter = obj.optDouble("balanceAfter", 0.0),
                          type = typeStr,
                          referenceInvoiceId = resolvedSaleId,
                          voucherCode = if (obj.isNull("voucherCode")) null else obj.optString("voucherCode"),
                          note = noteText,
                          timestamp = ts
                      ))
                      if (mode == BackupImportMode.MERGE && phone != null && phone !in newlyInsertedCustomerPhones && typeStr != "VOUCHER_RESTORE") {
                          customerDao.adjustCustomerStoreCredit(phone, delta, ts)
                      }
                  }
              }

              // Restore sale audit logs
              val existingSaleAuditLogsInDb = if (mode == BackupImportMode.MERGE) saleAuditLogDao.getAllAuditLogsOnce() else emptyList()
              backupJson.optJSONArray("saleAuditLogs")?.let { arr ->
                  for (i in 0 until arr.length()) {
                      val obj = arr.getJSONObject(i)
                      val backupSaleId = obj.optLong("saleId")
                      val resolvedSaleId = if (mode == BackupImportMode.MERGE) {
                          saleIdMap[backupSaleId]
                      } else backupSaleId
                      if (mode == BackupImportMode.MERGE && resolvedSaleId == null) continue
                      val actionStr = obj.optString("action", "DELETED")
                      val ts = obj.optLong("timestamp", System.currentTimeMillis())

                      if (mode == BackupImportMode.MERGE) {
                          val isDuplicate = existingSaleAuditLogsInDb.any {
                              it.saleId == resolvedSaleId && it.timestamp == ts && it.action == actionStr
                          }
                          if (isDuplicate) continue
                      }

                      val cashier = when {
                          obj.has("cashierName") && !obj.isNull("cashierName") -> obj.optString("cashierName")
                          obj.has("performedBy") && !obj.isNull("performedBy") -> obj.optString("performedBy")
                          else -> ""
                      }
                      val reason = if (obj.isNull("reason")) "" else obj.optString("reason", "")

                      saleAuditLogDao.insertAuditLogIgnore(SaleAuditLog(
                          id = if (mode == BackupImportMode.MERGE) 0L else obj.optLong("id", 0L),
                          saleId = resolvedSaleId!!,
                          action = actionStr,
                          reason = reason,
                          grandTotal = obj.optDouble("grandTotal", 0.0),
                          paymentMethod = obj.optString("paymentMethod", "Cash"),
                          cashierName = cashier,
                          timestamp = ts
                      ))
                  }
              }

              // Restore expense audit logs
              val existingExpenseAuditLogsOnceInDb = if (mode == BackupImportMode.MERGE) expenseAuditLogDao.getAllAuditLogsOnce() else emptyList()
              backupJson.optJSONArray("expenseAuditLogs")?.let { arr ->
                  for (i in 0 until arr.length()) {
                      val obj = arr.getJSONObject(i)
                      val backupExpenseId = obj.optLong("expenseId")
                      val resolvedExpenseId = if (mode == BackupImportMode.MERGE) {
                          expenseIdMap[backupExpenseId]
                      } else backupExpenseId
                      if (mode == BackupImportMode.MERGE && resolvedExpenseId == null) continue
                      val actionStr = obj.optString("action", "DELETED")
                      val ts = obj.optLong("timestamp", System.currentTimeMillis())

                      if (mode == BackupImportMode.MERGE) {
                          val isDuplicate = existingExpenseAuditLogsOnceInDb.any {
                              it.expenseId == resolvedExpenseId && it.timestamp == ts && it.action == actionStr
                          }
                          if (isDuplicate) continue
                      }

                      val user = when {
                          obj.has("userName") && !obj.isNull("userName") -> obj.optString("userName")
                          obj.has("performedBy") && !obj.isNull("performedBy") -> obj.optString("performedBy")
                          else -> ""
                      }
                      val reason = if (obj.isNull("reason")) "" else obj.optString("reason", "")

                      expenseAuditLogDao.insertAuditLogIgnore(ExpenseAuditLog(
                          id = if (mode == BackupImportMode.MERGE) 0L else obj.optLong("id", 0L),
                          expenseId = resolvedExpenseId!!,
                          action = actionStr,
                          reason = reason,
                          amount = obj.optDouble("amount", 0.0),
                          category = obj.optString("category", "Other"),
                          paymentSource = obj.optString("paymentSource", "Cash"),
                          userName = user,
                          timestamp = ts
                      ))
                  }
              }

              if (mode == BackupImportMode.MERGE) {
                  val barcodeDeltas = mutableMapOf<String, Double>()
                  for (item in newlyInsertedPurchaseItems) {
                      val code = item.productBarcode.lowercase()
                      if (code !in newlyInsertedProductBarcodes) {
                          barcodeDeltas[code] = (barcodeDeltas[code] ?: 0.0) + item.quantity
                      }
                  }
                  for (item in newlyInsertedSaleItems) {
                      val code = item.productBarcode.lowercase()
                      if (code !in newlyInsertedProductBarcodes) {
                          barcodeDeltas[code] = (barcodeDeltas[code] ?: 0.0) - item.quantity
                      }
                  }
                  for (adj in newlyInsertedAdjustments) {
                      if (adj.reason.contains("Purchase", ignoreCase = true) ||
                          adj.reason.contains("Sale Cancelled", ignoreCase = true) ||
                          adj.reason.contains("Sale Void", ignoreCase = true) ||
                          adj.reason.contains("Cancelled/Deleted", ignoreCase = true)) {
                          continue // Paired inventory adjustment already counted via newlyInsertedPurchaseItems or void cancellation delta
                      }
                      val code = adj.productBarcode.lowercase()
                      if (code !in newlyInsertedProductBarcodes) {
                          barcodeDeltas[code] = (barcodeDeltas[code] ?: 0.0) + adj.difference
                      }
                  }
                  for ((barcode, delta) in barcodeDeltas) {
                      if (kotlin.math.abs(delta) > 0.0001) {
                          val canonicalBarcode = productDao.getProductByBarcodeCaseInsensitive(barcode)?.barcode ?: barcode
                          productDao.adjustStockQty(canonicalBarcode, delta, allowNegative = true)
                      }
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
            if (backupJson.has("returnAuditPresets")) {
                try {
                    settingsDataStore.saveReturnAuditPresets(backupJson.getString("returnAuditPresets"))
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("themeMode")) {
                try {
                    settingsDataStore.saveThemeMode(backupJson.getString("themeMode"))
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("uiLayoutMode")) {
                try {
                    settingsDataStore.saveUiLayoutMode(backupJson.getString("uiLayoutMode"))
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("defaultCostPercentage")) {
                try {
                    settingsDataStore.saveDefaultCostPercentage(backupJson.getDouble("defaultCostPercentage"))
                } catch (e: Exception) { e.printStackTrace() }
            }
            if (backupJson.has("loyaltyProgramSettings")) {
                try {
                    val lObj = backupJson.getJSONObject("loyaltyProgramSettings")
                    val restoredLoyalty = com.example.techyfipos.utils.LoyaltyProgramSettings(
                        takaPerPoint = lObj.optDouble("takaPerPoint", 100.0),
                        pointCashValue = lObj.optDouble("pointCashValue", 1.0),
                        minRedeemPoints = lObj.optInt("minRedeemPoints", 20),
                        maxBillPercentPayable = lObj.optInt("maxBillPercentPayable", 50),
                        silverSpendThreshold = lObj.optDouble("silverSpendThreshold", 3000.0),
                        goldSpendThreshold = lObj.optDouble("goldSpendThreshold", 7500.0),
                        platinumSpendThreshold = lObj.optDouble("platinumSpendThreshold", 15000.0),
                        pointsExpiryDays = lObj.optInt("pointsExpiryDays", 365),
                        isTiersEnabled = lObj.optBoolean("isTiersEnabled", true)
                    )
                    com.example.techyfipos.utils.LoyaltySettingsHelper.saveSettings(context, restoredLoyalty)
                    updateLoyaltySettings(restoredLoyalty)
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

            com.example.techyfipos.utils.AutoBackupScheduler.scheduleAllAutoBackups(context.applicationContext, forceUpdate = true)
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
                val normBarcode = com.example.techyfipos.utils.toEnglishDigits(p.barcode.trim()).lowercase()
                var existing = productDao.getProductByBarcode(normBarcode)
                if (existing == null && normBarcode.length == 12 && normBarcode.all { it.isDigit() }) {
                    existing = productDao.getProductByBarcode("0$normBarcode")
                }
                if (existing != null) {
                    val updatedStock = if ("stockQty" in presentFields && p.stockQty >= 0.0) p.stockQty else existing.stockQty
                    val updatedPurchasePrice = if ("purchasePrice" in presentFields && p.purchasePrice >= 0.0) p.purchasePrice else existing.purchasePrice
                    if (kotlin.math.abs(updatedStock - existing.stockQty) > 0.0001) {
                        inventoryAdjustmentDao.insertAdjustment(
                            InventoryAdjustment(
                                productBarcode = existing.barcode,
                                productName = existing.name,
                                previousStock = existing.stockQty,
                                newStock = updatedStock,
                                difference = updatedStock - existing.stockQty,
                                reason = "Import",
                                timestamp = System.currentTimeMillis(),
                                cashierName = sessionUser?.name ?: ""
                            )
                        )
                    }
                    productDao.updateProduct(
                        p.copy(
                            barcode = existing.barcode,
                            createdAt = existing.createdAt,
                            category = if ("category" in presentFields) p.category else existing.category,
                            purchasePrice = updatedPurchasePrice,
                            sellingPrice = if ("sellingPrice" in presentFields) p.sellingPrice else existing.sellingPrice,
                            stockQty = updatedStock,
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
                    val finalStock = p.stockQty.coerceAtLeast(0.0)
                    val finalPurchase = p.purchasePrice.coerceAtLeast(0.0)
                    val newProduct = p.copy(
                        barcode = normBarcode,
                        stockQty = finalStock,
                        purchasePrice = finalPurchase
                    )
                    productDao.insertProduct(newProduct)
                    if (finalStock > 0.0001) {
                        inventoryAdjustmentDao.insertAdjustment(
                            InventoryAdjustment(
                                productBarcode = newProduct.barcode,
                                productName = newProduct.name,
                                previousStock = 0.0,
                                newStock = finalStock,
                                difference = finalStock,
                                reason = "Import",
                                timestamp = System.currentTimeMillis(),
                                cashierName = sessionUser?.name ?: ""
                            )
                        )
                    }
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
                val rounded = kotlin.math.round(newPrice * 100.0) / 100.0
                val newDiscounted = if (p.discountedPrice > 0.0) rounded else 0.0
                productDao.updateProduct(p.copy(sellingPrice = rounded, discountedPrice = newDiscounted))
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
            if (!hasPermission(AppPermissions.SYSTEM_MANAGE_STAFF)) {
                requireOwnerSession()
            }
        }
        if (role == AppRoles.OWNER && users.any { it.role == AppRoles.OWNER }) {
            throw IllegalStateException("An owner account already exists")
        }
        if (users.any { pinMatches(pin, it.pinHash) }) {
            throw IllegalStateException("PIN already in use by another user")
        }
        val permissionsJson = AppPermissions.serializePermissions(customPermissions)
        try {
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
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw IllegalStateException("Failed to create user: account constraint conflict", e)
        } catch (e: androidx.sqlite.SQLiteException) {
            throw IllegalStateException("Failed to create user: account constraint conflict", e)
        }
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

    suspend fun verifyPinDetailed(pin: String): PinVerifyResult = withContext(Dispatchers.IO) {
        val remainingLock = loginLockRemainingMillis()
        if (remainingLock > 0L) return@withContext PinVerifyResult.Locked(remainingLock)

        val all = userDao.getAllUsersOnce()
        val matchingUser = all.firstOrNull { pinMatches(pin, it.pinHash) }
        if (matchingUser != null) {
            if (!matchingUser.isActive) {
                return@withContext PinVerifyResult.Deactivated(matchingUser)
            }
            if (!matchingUser.pinHash.startsWith("pbkdf2$")) {
                userDao.updatePinHash(matchingUser.id, hashPin(pin))
            }
            context.getSharedPreferences("auth_security", Context.MODE_PRIVATE).edit().clear().apply()
            return@withContext PinVerifyResult.Success(matchingUser)
        }

        val securityPrefs = context.getSharedPreferences("auth_security", Context.MODE_PRIVATE)
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val totalFailures = securityPrefs.getInt("total_failures", 0) + 1
        val failuresInWindow = securityPrefs.getInt("failed_attempts", 0) + 1
        val editor = securityPrefs.edit()
            .putInt("total_failures", totalFailures)
            .putInt("failed_attempts", failuresInWindow)

        var newLockMs = 0L
        if (failuresInWindow >= 5) {
            newLockMs = when {
                totalFailures >= 20 -> 15 * 60 * 1000L // 15 mins
                totalFailures >= 15 -> 5 * 60 * 1000L  // 5 mins
                totalFailures >= 10 -> 2 * 60 * 1000L  // 2 mins
                else -> 30 * 1000L                      // 30 secs
            }.coerceAtMost(15 * 60 * 1000L)
            editor.putLong("lock_until_elapsed", nowElapsed + newLockMs)
                .putLong("lock_until", nowWall + newLockMs)
                .putInt("failed_attempts", 0)
        }
        editor.apply()
        PinVerifyResult.Invalid(newLockMs)
    }

    /** Returns the matching user for [pin], or null if no user has that PIN or account is inactive. */
    suspend fun verifyPin(pin: String): User? = withContext(Dispatchers.IO) {
        when (val res = verifyPinDetailed(pin)) {
            is PinVerifyResult.Success -> res.user
            else -> null
        }
    }

    fun loginLockRemainingMillis(): Long {
        val securityPrefs = context.getSharedPreferences("auth_security", Context.MODE_PRIVATE)
        val lockUntilElapsed = securityPrefs.getLong("lock_until_elapsed", 0L)
        val lockUntilWall = securityPrefs.getLong("lock_until", 0L)
        if (lockUntilElapsed == 0L && lockUntilWall == 0L) return 0L

        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()

        val remainingElapsed = lockUntilElapsed - nowElapsed
        val remainingWall = lockUntilWall - nowWall
        val maxLockMs = 15 * 60 * 1000L

        // If either deadline has passed, or if reboot occurred (remainingElapsed > maxLockMs): treat as expired
        if (remainingElapsed <= 0L || remainingWall <= 0L || remainingElapsed > maxLockMs) {
            securityPrefs.edit().remove("lock_until_elapsed").remove("lock_until").apply()
            return 0L
        }

        val remaining = minOf(remainingElapsed, remainingWall).coerceAtMost(maxLockMs)
        return maxOf(0L, remaining)
    }

    suspend fun deleteUser(user: User) = withContext(Dispatchers.IO) {
        if (!hasPermission(AppPermissions.SYSTEM_MANAGE_STAFF)) {
            requireOwnerSession()
        }
        if (user.role == AppRoles.OWNER && !isSessionOwner()) {
            throw UnauthorizedException("Non-owners cannot delete owner accounts")
        }
        if (sessionUser?.id == user.id) {
            throw IllegalStateException("Cannot delete your own account")
        }
        if (user.role == AppRoles.OWNER && userDao.getOwnerCount() <= 1) {
            throw IllegalStateException("The last owner account cannot be deleted")
        }
        userDao.deleteUser(user)
    }

    suspend fun updateStaff(
        userId: Long,
        newName: String,
        newPin: String? = null,
        oldPin: String? = null,
        newRole: String? = null,
        customPermissions: Set<String>? = null,
        isActive: Boolean? = null,
        maxDiscount: Double? = null
    ) = withContext(Dispatchers.IO) {
        if (!hasPermission(AppPermissions.SYSTEM_MANAGE_STAFF)) {
            requireOwnerSession()
        }
        val all = userDao.getAllUsersOnce()
        val existing = all.firstOrNull { it.id == userId } ?: error("User not found")
        require(newName.isNotBlank()) { "Staff name cannot be blank" }

        if (existing.role == AppRoles.OWNER && !isSessionOwner()) {
            throw UnauthorizedException("Non-owners cannot modify owner accounts")
        }

        val targetRole = newRole ?: existing.role
        require(targetRole == AppRoles.OWNER || targetRole == AppRoles.MANAGER || targetRole == AppRoles.CASHIER) {
            "Unsupported user role: $targetRole"
        }

        if (targetRole == AppRoles.OWNER && !isSessionOwner()) {
            throw UnauthorizedException("Non-owners cannot assign the owner role")
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
            require(!oldPin.isNullOrBlank()) { "Current PIN is required to change PIN" }
            val currentOwner = sessionUser
            val matchesTarget = pinMatches(oldPin, existing.pinHash)
            val matchesSessionOwner = isSessionOwner() && currentOwner != null && pinMatches(oldPin, currentOwner.pinHash)
            require(matchesTarget || matchesSessionOwner) { "Current PIN is incorrect" }
            require(all.none { it.id != userId && pinMatches(newPin, it.pinHash) }) {
                "PIN already in use by another user"
            }
            hashPin(newPin)
        } else {
            existing.pinHash
        }

        if (customPermissions != null) {
            if (!hasPermission(AppPermissions.SYSTEM_PERMISSIONS) && !isSessionOwner()) {
                throw UnauthorizedException("Permission SYSTEM_PERMISSIONS required to change custom permissions")
            }
            if (sessionUser?.id == userId) {
                throw UnauthorizedException("Users cannot edit their own permissions")
            }
        }

        val updatedPermissionsJson = if (customPermissions != null) {
            AppPermissions.serializePermissions(customPermissions)
        } else if (targetRole != existing.role) {
            null
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

    suspend fun updateCashier(userId: Long, newName: String, newPin: String? = null, oldPin: String? = null) = withContext(Dispatchers.IO) {
        updateStaff(userId = userId, newName = newName, newPin = newPin, oldPin = oldPin)
    }

    suspend fun setStaffActive(userId: Long, isActive: Boolean) = withContext(Dispatchers.IO) {
        if (!hasPermission(AppPermissions.SYSTEM_MANAGE_STAFF)) {
            requireOwnerSession()
        }
        val all = userDao.getAllUsersOnce()
        val existing = all.firstOrNull { it.id == userId } ?: error("User not found")
        if (existing.role == AppRoles.OWNER && !isSessionOwner()) {
            throw UnauthorizedException("Non-owners cannot modify owner accounts")
        }
        if (sessionUser?.id == userId && !isActive) {
            throw IllegalStateException("Cannot deactivate your own account")
        }
        if (existing.role == AppRoles.OWNER && !isActive && userDao.getActiveOwnerCount() <= 1) {
            throw IllegalStateException("The only owner account cannot be deactivated")
        }
        userDao.updateActiveStatus(userId, isActive)
    }

    suspend fun updateStaffPermissions(userId: Long, permissions: Set<String>?) = withContext(Dispatchers.IO) {
        if (!hasPermission(AppPermissions.SYSTEM_PERMISSIONS)) {
            requireOwnerSession()
        }
        val all = userDao.getAllUsersOnce()
        val existing = all.firstOrNull { it.id == userId } ?: error("User not found")
        if (existing.role == AppRoles.OWNER && !isSessionOwner()) {
            throw UnauthorizedException("Non-owners cannot modify owner permissions")
        }
        if (sessionUser?.id == userId) {
            throw UnauthorizedException("Users cannot edit their own permissions")
        }
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
        restock: Boolean = true,
        walkinPhone: String? = null,
        walkinName: String? = null,
        issueVoucher: Boolean = false
    ): ReturnProcessResult = withContext(Dispatchers.IO) {
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
            val dueReduction = if (!phone.isNullOrBlank() && sale.grandTotal > 0.0) {
                val dueForSale = calculateDueAddition(
                    sale.paymentMethod, sale.grandTotal, sale.amountPaid, sale.splitPaymentJson
                )
                val remainingDueForSale = (
                    dueForSale - previousReturns.sumOf { it.dueAdjustmentAmount }
                ).coerceAtLeast(0.0)
                val red = kotlin.math.min(refundTotal, remainingDueForSale)
                if (red > 0.0) {
                    reduceCustomerDue(phone, red)
                    try {
                        customerDueDao.insertDuePayment(
                            DuePayment(
                                customerPhone = normalizePhone(phone),
                                amountPaid = red,
                                timestamp = System.currentTimeMillis(),
                                paymentMethod = "Return Adjustment",
                                referenceNote = "Due reduced by return on invoice #$saleId"
                            )
                        )
                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                        throw IllegalStateException("Failed to record return due adjustment: constraint conflict", e)
                    } catch (e: androidx.sqlite.SQLiteException) {
                        throw IllegalStateException("Failed to record return due adjustment: constraint conflict", e)
                    }
                }
                red
            } else 0.0

            val ledgerRestored = storeCreditLedgerDao.getEntriesForInvoiceOnce(saleId)
                .filter { it.type == "RETURN_REFUND" || it.type == "VOUCHER_RESTORE" }
                .sumOf { it.deltaAmount }
            val returnsRestored = previousReturns.sumOf {
                (it.refundAmount - it.dueAdjustmentAmount - it.externalRefundAmount).coerceAtLeast(0.0)
            }
            val storeCreditRestoredSoFar = maxOf(ledgerRestored, returnsRestored)

            val isStoreCreditRefund = refundMethod.equals("Store Credit", ignoreCase = true)
            val (scToRestore, externalRefund) = if (isStoreCreditRefund && !issueVoucher) {
                val excessCredit = (refundTotal - dueReduction).coerceAtLeast(0.0)
                Pair(excessCredit, 0.0)
            } else if (isStoreCreditRefund && issueVoucher) {
                Pair(0.0, 0.0)
            } else {
                val app = com.example.techyfipos.utils.PaymentMath.calculateReturnRefundApportionment(
                    refundTotal = refundTotal,
                    saleStoreCreditApplied = sale.storeCreditApplied,
                    storeCreditRestoredSoFar = storeCreditRestoredSoFar,
                    dueReduction = dueReduction,
                    refundMethod = refundMethod
                )
                Pair(app.storeCreditToRestore, app.externalRefundAmount)
            }

            var vCodeFromSale: String? = null
            var voucherPortion = 0.0
            var replacementFreshVoucher: StoreCreditVoucher? = null

            if (scToRestore > 0.0) {
                vCodeFromSale = sale.voucherCode ?: sale.splitPaymentJson?.let { json ->
                    try {
                        val array = org.json.JSONArray(json)
                        (0 until array.length()).firstNotNullOfOrNull { i ->
                            array.getJSONObject(i).optString("voucherCode", "").takeIf { it.isNotBlank() }
                        }
                    } catch (_: Exception) { null }
                } ?: storeCreditLedgerDao.getEntriesForInvoiceOnce(saleId).firstNotNullOfOrNull { it.voucherCode?.takeIf { c -> c.isNotBlank() } }

                val voucherDeductionFromSplit = sale.splitPaymentJson?.let { json ->
                    try {
                        val array = org.json.JSONArray(json)
                        (0 until array.length()).map { array.getJSONObject(it) }
                            .filter { it.optString("voucherCode", "").isNotBlank() }
                            .sumOf {
                                if (it.has("voucherAmount")) it.optDouble("voucherAmount", 0.0)
                                else it.optDouble("amount", 0.0)
                            }
                    } catch (_: Exception) { null }
                }?.takeIf { it > 0.0 }

                val voucherRestoredSoFar = if (!vCodeFromSale.isNullOrBlank()) {
                    storeCreditLedgerDao.getEntriesForInvoiceOnce(saleId)
                        .filter { it.type == "VOUCHER_RESTORE" && it.voucherCode.equals(vCodeFromSale.trim(), ignoreCase = true) }
                        .sumOf { it.deltaAmount }
                } else 0.0

                var redirectTargetPhone: String? = null
                if (!vCodeFromSale.isNullOrBlank()) {
                    val voucher = storeCreditVoucherDao.getVoucherByCode(vCodeFromSale.trim().uppercase())
                    if (voucher != null && voucher.status != "CANCELLED") {
                        val maxRestoreToVoucher = (voucher.initialAmount - voucher.remainingBalance).coerceAtLeast(0.0)
                        val voucherSpentInThisSale = if (sale.voucherAmount > 0.0) {
                            sale.voucherAmount
                        } else if (voucherDeductionFromSplit != null) {
                            voucherDeductionFromSplit
                        } else {
                            val ledgerEntriesForSale = storeCreditLedgerDao.getEntriesForInvoiceOnce(saleId)
                            val custCreditFromLedger = ledgerEntriesForSale
                                .filter { it.customerPhone != null && (it.type == "SALE_REDEMPTION" || it.deltaAmount < 0.0) }
                                .sumOf { kotlin.math.abs(it.deltaAmount) }
                            if (custCreditFromLedger > 0.0) {
                                (sale.storeCreditApplied - custCreditFromLedger).coerceAtLeast(0.0)
                            } else if (!sale.customerPhone.isNullOrBlank() || !phone.isNullOrBlank()) {
                                0.0 // Prefer customer credit when ambiguous on legacy pre-v37 sales
                            } else {
                                minOf(sale.storeCreditApplied, maxRestoreToVoucher)
                            }
                        }
                        val voucherRemainingToRestore = (voucherSpentInThisSale - voucherRestoredSoFar).coerceAtLeast(0.0)
                        voucherPortion = minOf(scToRestore, voucherRemainingToRestore, maxRestoreToVoucher)

                        val isExpired = voucher.expiresAt != null && voucher.expiresAt > 0L && voucher.expiresAt < System.currentTimeMillis()
                        if (isExpired || voucher.status == "EXPIRED") {
                            val targetCustPhone = listOfNotNull(
                                sale.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                phone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                voucher.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                walkinPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) }
                            ).firstOrNull()

                            if (!targetCustPhone.isNullOrBlank()) {
                                redirectTargetPhone = targetCustPhone
                                voucherPortion = 0.0
                            } else {
                                // Walk-in return, voucher had no customer phone (N-5):
                                // Issue a fresh replacement voucher so the share is not lost
                                var inserted = false
                                for (attempt in 1..5) {
                                    val code = "SC-${(1000..9999).random()}-${(1000..9999).random()}"
                                    val fresh = StoreCreditVoucher(
                                        code = code,
                                        initialAmount = voucherPortion,
                                        remainingBalance = voucherPortion,
                                        customerPhone = null,
                                        sourceReturnId = null,
                                        sourceSaleId = saleId,
                                        issuedAt = System.currentTimeMillis(),
                                        expiresAt = System.currentTimeMillis() + (90L * 24 * 3600 * 1000),
                                        status = "ACTIVE",
                                        notes = "Fresh replacement for expired voucher ${voucher.code} on return of sale #$saleId"
                                    )
                                    try {
                                        storeCreditVoucherDao.insertVoucher(fresh)
                                        storeCreditLedgerDao.insertEntry(
                                            StoreCreditLedgerEntry(
                                                customerPhone = null,
                                                deltaAmount = voucherPortion,
                                                balanceAfter = voucherPortion,
                                                type = "VOUCHER_RESTORE",
                                                referenceInvoiceId = saleId,
                                                voucherCode = fresh.code,
                                                note = "Issued fresh voucher ${fresh.code} replacing expired ${voucher.code} on return of sale #$saleId",
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                        replacementFreshVoucher = fresh
                                        inserted = true
                                        break
                                    } catch (_: android.database.sqlite.SQLiteConstraintException) {
                                    } catch (_: androidx.sqlite.SQLiteException) {
                                    }
                                }
                                if (!inserted) {
                                    throw IllegalStateException("Failed to generate unique voucher code for expired replacement on return of sale #$saleId")
                                }
                                voucherPortion = 0.0
                            }
                        }

                        if (voucherPortion > 0.0) {
                            val restoredBal = voucher.remainingBalance + voucherPortion
                            val newStatus = when {
                                isExpired || voucher.status == "EXPIRED" -> "EXPIRED"
                                restoredBal > 0.0 -> "ACTIVE"
                                else -> "EXHAUSTED"
                            }
                            storeCreditVoucherDao.updateVoucherBalance(voucher.code, restoredBal, newStatus)
                            val targetPhone = listOfNotNull(
                                sale.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                phone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) },
                                voucher.customerPhone?.takeIf { it.isNotBlank() }?.let { normalizePhone(it) }
                            ).firstOrNull()
                            val custBalAfter = targetPhone?.let { customerDao.getCustomerByPhone(it)?.storeCredit } ?: restoredBal
                            storeCreditLedgerDao.insertEntry(
                                StoreCreditLedgerEntry(
                                    customerPhone = targetPhone,
                                    deltaAmount = voucherPortion,
                                    balanceAfter = custBalAfter,
                                    type = "VOUCHER_RESTORE",
                                    referenceInvoiceId = saleId,
                                    voucherCode = voucher.code,
                                    note = "Voucher balance restored for return on invoice #$saleId",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                // voucherPortion is already 0 when an expired voucher's share was redirected, so the
                // redirected share is included here exactly once (N-5: it used to be added a second time).
                val customerPortion = (scToRestore - voucherPortion).coerceAtLeast(0.0)
                // For a Store Credit refund with no sale phone, the walk-in / new-voucher block below already
                // credits the remainder, so only fall back to the redirect target for other refund methods.
                val creditPhone = phone?.takeIf { it.isNotBlank() }
                    ?: redirectTargetPhone.takeIf { !isStoreCreditRefund }
                if (customerPortion > 0.0 && !creditPhone.isNullOrBlank()) {
                    customerDao.adjustCustomerStoreCredit(normalizePhone(creditPhone), customerPortion, System.currentTimeMillis())
                    val updated = customerDao.getCustomerByPhone(normalizePhone(creditPhone))
                    storeCreditLedgerDao.insertEntry(
                        StoreCreditLedgerEntry(
                            customerPhone = normalizePhone(creditPhone),
                            deltaAmount = customerPortion,
                            balanceAfter = updated?.storeCredit ?: customerPortion,
                            type = "RETURN_REFUND",
                            referenceInvoiceId = saleId,
                            note = "Store credit restored for return on invoice #$saleId"
                        )
                    )
                }
            }

            val loyaltyPhone = sale.loyaltyPhone?.takeIf { it.isNotBlank() } ?: phone?.takeIf { it.isNotBlank() }
            if (!loyaltyPhone.isNullOrBlank() && sale.grandTotal > 0.0) {
                val cleanLoyaltyPhone = normalizePhone(loyaltyPhone)
                val loyaltySettings = getLoyaltySettings()
                val takaPerPt = loyaltySettings.takaPerPoint.takeIf { it > 0.0 } ?: LOYALTY_TAKA_PER_POINT
                val pointsBefore = floor(
                    (sale.grandTotal - alreadyRefunded).coerceAtLeast(0.0) / takaPerPt
                ).toInt()
                val pointsAfter = floor(
                    (sale.grandTotal - alreadyRefunded - refundTotal).coerceAtLeast(0.0) / takaPerPt
                ).toInt()
                val pointsReversed = (pointsBefore - pointsAfter).coerceAtLeast(0)
                if (pointsReversed > 0) {
                    customerLoyaltyDao.reverseEarnedPoints(
                        cleanLoyaltyPhone,
                        pointsReversed,
                        refundTotal,
                        System.currentTimeMillis()
                    )
                    loyaltyTransactionDao.insert(LoyaltyTransaction(
                        customerPhone = cleanLoyaltyPhone, pointsChange = -pointsReversed,
                        spendChange = -refundTotal, type = "RETURN",
                        reason = "Return for invoice #$saleId", saleId = saleId,
                        performedByName = sessionUser?.name
                    ))
                }
                val updatedLoyalty = customerLoyaltyDao.getLoyaltyByPhone(cleanLoyaltyPhone)
                val existingCustomer = customerDao.getCustomerByPhone(cleanLoyaltyPhone)
                if (existingCustomer != null) {
                    val updatedLifetime = (existingCustomer.lifetimeSpent - refundTotal).coerceAtLeast(0.0)
                    customerDao.upsertCustomer(
                        existingCustomer.copy(
                            loyaltyPoints = updatedLoyalty?.pointsBalance ?: 0,
                            lifetimeSpent = updatedLifetime
                        )
                    )
                }
            }

            val dueAdj = dueReduction
            val computedExternalRefund = if (isStoreCreditRefund) 0.0 else externalRefund

            val returnRecord = Return(
                saleId = saleId,
                timestamp = System.currentTimeMillis(),
                refundMethod = refundMethod,
                refundAmount = refundTotal,
                externalRefundAmount = computedExternalRefund,
                dueAdjustmentAmount = dueAdj,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                performedByUserId = sessionUser?.id,
                performedByName = sessionUser?.name.orEmpty().also {
                    if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                }
            )
            val returnId = returnDao.insertReturn(returnRecord)
            if (replacementFreshVoucher != null) {
                storeCreditVoucherDao.updateVoucherSourceReturnId(replacementFreshVoucher.code, returnId)
            }
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

            var generatedVoucherCode: String? = replacementFreshVoucher?.code ?: if (voucherPortion > 0.0) vCodeFromSale else null
            var generatedVoucher: StoreCreditVoucher? = replacementFreshVoucher
            if (refundMethod.equals("Store Credit", ignoreCase = true)) {
                // N-1: The voucher share goes back to the voucher only.
                // The walk-in or new-voucher credit must be (refundTotal - dueAdj) - voucherShareRestored.
                val totalVoucherRestoredOrReplaced = voucherPortion + (replacementFreshVoucher?.initialAmount ?: 0.0)
                val remainingRefundToCredit = (refundTotal - dueAdj - totalVoucherRestoredOrReplaced).coerceAtLeast(0.0)

                if (remainingRefundToCredit > 0.0) {
                    if (issueVoucher || (phone.isNullOrBlank() && walkinPhone.isNullOrBlank())) {
                        var inserted = false
                        for (attempt in 1..5) {
                            val code = "SC-${(1000..9999).random()}-${(1000..9999).random()}"
                            val voucherAmount = remainingRefundToCredit
                            val voucherToInsert = StoreCreditVoucher(
                                code = code,
                                initialAmount = voucherAmount,
                                remainingBalance = voucherAmount,
                                customerPhone = phone ?: walkinPhone?.trim()?.takeIf { it.isNotBlank() },
                                sourceReturnId = returnId,
                                sourceSaleId = saleId,
                                issuedAt = System.currentTimeMillis(),
                                expiresAt = System.currentTimeMillis() + (90L * 24 * 3600 * 1000),
                                status = "ACTIVE",
                                notes = "Return voucher on invoice #$saleId"
                            )
                            try {
                                storeCreditVoucherDao.insertVoucher(voucherToInsert)
                                generatedVoucherCode = code
                                generatedVoucher = voucherToInsert
                                inserted = true
                                break
                            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                            } catch (_: androidx.sqlite.SQLiteException) {
                            }
                        }
                        if (!inserted) {
                            throw IllegalStateException("Failed to generate unique voucher code after 5 attempts")
                        }
                    } else if (phone.isNullOrBlank() && !walkinPhone.isNullOrBlank()) {
                        // Walk-in customer registered with phone
                        val normWalkin = normalizePhone(walkinPhone)
                        var cust = customerDao.getCustomerByPhone(normWalkin)
                        if (cust == null) {
                            cust = Customer(
                                phone = normWalkin,
                                name = walkinName?.trim()?.ifBlank { "Customer ${normWalkin.takeLast(4)}" } ?: "Customer ${normWalkin.takeLast(4)}",
                                storeCredit = 0.0
                            )
                            customerDao.upsertCustomer(cust)
                        }
                        val excessCredit = remainingRefundToCredit
                        if (excessCredit > 0.0) {
                            customerDao.adjustCustomerStoreCredit(normWalkin, excessCredit, System.currentTimeMillis())
                            val updatedCust = customerDao.getCustomerByPhone(normWalkin)
                            storeCreditLedgerDao.insertEntry(
                                StoreCreditLedgerEntry(
                                    customerPhone = normWalkin,
                                    deltaAmount = excessCredit,
                                    balanceAfter = updatedCust?.storeCredit ?: excessCredit,
                                    type = "RETURN_REFUND",
                                    referenceInvoiceId = saleId,
                                    note = "Return refund for walk-in on invoice #$saleId"
                                )
                            )
                            syncCustomerProfileFromLedgers(normWalkin)
                        }
                    }
                }
            }

            if (!sale.customerPhone.isNullOrBlank()) syncCustomerProfileFromLedgers(sale.customerPhone)
            ReturnProcessResult(refundTotal = refundTotal, voucherCode = generatedVoucherCode, voucher = generatedVoucher)
        }
    }

    suspend fun getStoreCreditRestoredSoFar(saleId: Long): Double = withContext(Dispatchers.IO) {
        val previousReturns = returnDao.getReturnsForSaleOnce(saleId)
        val ledgerRestored = storeCreditLedgerDao.getEntriesForInvoiceOnce(saleId)
            .filter { it.type == "RETURN_REFUND" || it.type == "VOUCHER_RESTORE" }
            .sumOf { it.deltaAmount }
        val returnsRestored = previousReturns.sumOf {
            (it.refundAmount - it.dueAdjustmentAmount - it.externalRefundAmount).coerceAtLeast(0.0)
        }
        maxOf(ledgerRestored, returnsRestored)
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
        val existing = expenseDao.getExpenseById(expense.id)
        if (existing != null && existing.category.equals("Supplier Payment", ignoreCase = true)) {
            throw IllegalStateException("Supplier payment expenses are auto-generated and cannot be edited directly.")
        }
        expenseDao.updateExpense(expense)
        expenseAuditLogDao.insertAuditLog(
            com.example.techyfipos.data.db.ExpenseAuditLog(
                expenseId = expense.id,
                action = "UPDATED",
                reason = "Expense updated",
                amount = expense.amount,
                category = expense.category,
                paymentSource = expense.paymentSource,
                userName = sessionUser?.name.orEmpty().also {
                    if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                },
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteExpense(id: Long, reason: String = "Expense Deleted") = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.EXPENSES_MANAGE)
        val expense = expenseDao.getExpenseById(id)
        if (expense != null && expense.category.equals("Supplier Payment", ignoreCase = true)) {
            throw IllegalStateException("Supplier payment expenses are auto-generated and cannot be deleted directly. Manage them from Suppliers.")
        }
        if (expense != null) {
            expenseAuditLogDao.insertAuditLog(
                com.example.techyfipos.data.db.ExpenseAuditLog(
                    expenseId = id,
                    action = "DELETED",
                    reason = reason,
                    amount = expense.amount,
                    category = expense.category,
                    paymentSource = expense.paymentSource,
                    userName = sessionUser?.name.orEmpty().also {
                        if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                    },
                    timestamp = System.currentTimeMillis()
                )
            )
        }
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
        val current = supplierDao.getSupplierById(supplier.id) ?: supplier
        if (kotlin.math.abs(current.totalDue) > 0.001) {
            throw IllegalStateException("Cannot delete supplier with active due balance of ৳${current.totalDue}")
        }
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
            val writeTimestamp = System.currentTimeMillis()
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
                    timestamp = writeTimestamp
                ) == 1
            ) { "Supplier no longer exists" }
            if (paidAmount > 0.0) {
                supplierDao.insertSupplierPayment(
                    SupplierPayment(
                        supplierId = supplierId,
                        amountPaid = paidAmount,
                        paymentMethod = paymentMethod,
                        referenceNote = "Paid on Invoice #$invoiceNumber${if (!note.isNullOrBlank()) " - $note" else ""}",
                        timestamp = writeTimestamp,
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
                            timestamp = writeTimestamp,
                            cashierName = sessionUser?.name.orEmpty().also {
                                if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                            }
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
                            cashierName = sessionUser?.name.orEmpty().also {
                                if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                            }
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
            val supplier = supplierDao.getSupplierById(supplierId)
                ?: throw IllegalArgumentException("Supplier not found")
            require(supplier.totalDue >= amountPaid - 0.001) {
                "Payment exceeds the outstanding supplier balance"
            }
            supplierDao.recordSupplierPayment(supplierId, amountPaid, timestamp)
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
                        cashierName = sessionUser?.name.orEmpty().also {
                            if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                        }
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

    suspend fun voidSupplierPayment(paymentId: Long, reason: String = "Voided by Owner") = withContext(Dispatchers.IO) {
        requireOwnerSession()
        database.withDriverTransaction {
            val payment = supplierDao.getSupplierPaymentById(paymentId)
                ?: throw IllegalArgumentException("Supplier payment not found")
            if (payment.referenceNote?.startsWith("Paid on Invoice #") == true) {
                throw IllegalStateException("Cannot void invoice payments directly. Void the purchase invoice instead.")
            }
            val supplier = supplierDao.getSupplierById(payment.supplierId)
            if (supplier != null) {
                supplierDao.updateSupplierBalances(
                    supplierId = payment.supplierId,
                    purchaseAmount = 0.0,
                    dueChange = payment.amountPaid,
                    timestamp = System.currentTimeMillis()
                )
            }
            supplierDao.deleteSupplierPaymentById(paymentId)
            val linkedExpense = expenseDao.getSupplierExpenseByTimestamp(payment.timestamp)
            if (linkedExpense != null) {
                expenseAuditLogDao.insertAuditLog(
                    com.example.techyfipos.data.db.ExpenseAuditLog(
                        expenseId = linkedExpense.id,
                        action = "DELETED",
                        reason = "Voided supplier payment: $reason",
                        amount = linkedExpense.amount,
                        category = linkedExpense.category,
                        paymentSource = linkedExpense.paymentSource,
                        userName = sessionUser?.name.orEmpty().also {
                            if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                        },
                        timestamp = System.currentTimeMillis()
                    )
                )
                expenseDao.deleteExpenseById(linkedExpense.id)
            }
        }
    }

    suspend fun voidPurchaseInvoice(invoiceId: Long, reason: String = "Voided by Owner") = withContext(Dispatchers.IO) {
        requireOwnerSession()
        database.withDriverTransaction {
            val invoice = purchaseInvoiceDao.getInvoiceById(invoiceId)
                ?: throw IllegalArgumentException("Invoice not found")
            val items = purchaseInvoiceDao.getItemsForInvoiceOnce(invoiceId)
            items.forEach { item ->
                val product = productDao.getProductByBarcode(item.productBarcode)
                    ?: productDao.getProductByBarcodeCaseInsensitive(item.productBarcode)
                if (product != null) {
                    val newStock = (product.stockQty - item.quantity).coerceAtLeast(0.0)
                    productDao.updateProduct(product.copy(stockQty = newStock))
                    inventoryAdjustmentDao.insertAdjustment(
                        InventoryAdjustment(
                            productBarcode = item.productBarcode,
                            productName = product.name,
                            previousStock = product.stockQty,
                            newStock = newStock,
                            difference = -item.quantity,
                            reason = "Void Purchase Invoice",
                            note = "Void Invoice #${invoice.invoiceNumber}: $reason",
                            cashierName = sessionUser?.name.orEmpty().ifBlank { "Admin" }
                        )
                    )
                }
            }
            val supplier = supplierDao.getSupplierById(invoice.supplierId)
            val currentSupplierDue = supplier?.totalDue ?: 0.0
            val dueReduction = minOf(invoice.dueAmount, maxOf(0.0, currentSupplierDue))
            supplierDao.updateSupplierBalances(
                supplierId = invoice.supplierId,
                purchaseAmount = -invoice.totalAmount,
                dueChange = -dueReduction,
                timestamp = System.currentTimeMillis()
            )
            purchaseInvoiceDao.deleteItemsForInvoice(invoiceId)
            purchaseInvoiceDao.deletePurchaseInvoice(invoice)

            if (invoice.paidAmount > 0.0) {
                val payments = supplierDao.getPaymentsForSupplierOnce(invoice.supplierId)
                val targetRef = "Paid on Invoice #${invoice.invoiceNumber}"
                val matchingPayment = payments.firstOrNull {
                    val ref = it.referenceNote.orEmpty()
                    val matchesPrefix = ref == targetRef || ref.startsWith("$targetRef - ")
                    matchesPrefix && kotlin.math.abs(it.amountPaid - invoice.paidAmount) < 0.01
                }
                if (matchingPayment != null) {
                    supplierDao.deleteSupplierPaymentById(matchingPayment.id)
                    val linkedExpense = expenseDao.getSupplierExpenseByTimestamp(matchingPayment.timestamp)
                    if (linkedExpense != null) {
                        expenseAuditLogDao.insertAuditLog(
                            com.example.techyfipos.data.db.ExpenseAuditLog(
                                expenseId = linkedExpense.id,
                                action = "DELETED",
                                reason = "Voided invoice #${invoice.invoiceNumber}: $reason",
                                amount = linkedExpense.amount,
                                category = linkedExpense.category,
                                paymentSource = linkedExpense.paymentSource,
                                userName = sessionUser?.name.orEmpty().also {
                                    if (sessionUser == null) Log.w("DataRepository", "sessionUser null at write time")
                                },
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        expenseDao.deleteExpenseById(linkedExpense.id)
                    }
                }
            }
        }
    }

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
            registerShiftStateDao.upsert(RegisterShiftState(startedAt = timestamp, startingFloat = closing.actualCountedCash))
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
        requirePermission(AppPermissions.LOYALTY_MANAGE)
        val phone = normalizePhone(loyalty.phone)
        val existing = customerLoyaltyDao.getLoyaltyByPhone(phone)
        customerLoyaltyDao.upsertLoyalty(
            if (existing == null) loyalty.copy(phone = phone)
            else existing.copy(
                name = loyalty.name.trim().ifBlank { existing.name },
                tier = loyalty.tier.ifBlank { existing.tier },
                updatedAt = System.currentTimeMillis(),
                isActive = existing.isActive
            )
        )
        syncCustomerProfileFromLedgers(phone)
    }

    suspend fun addLoyaltyPoints(phone: String, pts: Int, spend: Double, reason: String = "Manual points adjustment") = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.LOYALTY_MANAGE)
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
        requirePermission(AppPermissions.LOYALTY_MANAGE)
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
        requirePermission(AppPermissions.LOYALTY_MANAGE)
        val normalized = normalizePhone(phone)
        val changed = customerLoyaltyDao.setActive(normalized, active, System.currentTimeMillis()) == 1
        if (changed) syncCustomerProfileFromLedgers(normalized)
        changed
    }

    fun getLoyaltyTransactionsForCustomer(phone: String): Flow<List<LoyaltyTransaction>> =
        loyaltyTransactionDao.getForCustomer(normalizePhone(phone))

    suspend fun deleteLoyaltyMember(phone: String) = withContext(Dispatchers.IO) {
        requirePermission(AppPermissions.LOYALTY_MANAGE)
        val normalized = normalizePhone(phone)
        database.withDriverTransaction {
            customerLoyaltyDao.delete(normalized)
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

        // Cleanup all persistent files and leftover caches (R20-37)
        try {
            val filesToDelete = listOf(
                "store_logo.png",
                "custom_logo.png",
                "restored_logo.png",
                "restored_shop_logo.png"
            )
            filesToDelete.forEach { name ->
                val f = File(context.filesDir, name)
                if (f.exists()) f.delete()
            }
            context.filesDir.listFiles { _, name -> name.startsWith("restored_") && name.endsWith(".png") }
                ?.forEach { it.delete() }
            File(context.filesDir, "AutoBackups").deleteRecursively()
            File(context.filesDir, "safety_rollback").deleteRecursively()
            File(context.filesDir, "restored_product_images").deleteRecursively()
            context.filesDir.listFiles { _, name -> name.endsWith(".sqlite") || name.endsWith(".sqlite-wal") || name.endsWith(".sqlite-shm") }
                ?.forEach { it.delete() }
            context.cacheDir.listFiles { _, name -> name.startsWith("auto_backup_") || name.startsWith("restore_") || name.endsWith(".posenc") }
                ?.forEach { it.deleteRecursively() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Clear shared preferences
        try {
            context.getSharedPreferences("loyalty_program_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("auth_security", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("techyfi_return_dialog_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Cancel background workers and alarms
        try {
            val workManager = androidx.work.WorkManager.getInstance(context)
            workManager.cancelUniqueWork(com.example.techyfipos.utils.AutoBackupScheduler.WORK_LOCAL_BACKUP)
            workManager.cancelUniqueWork(com.example.techyfipos.utils.AutoBackupScheduler.WORK_DRIVE_BACKUP)
            workManager.cancelUniqueWork(com.example.techyfipos.utils.AutoBackupScheduler.WORK_ONETIME_BACKUP)
            com.example.techyfipos.utils.AutoBackupScheduler.scheduleAlarmManagerBackup(context, enabled = false)
            com.example.techyfipos.utils.DueReminderScheduler.cancelAllScheduledReminders(context)
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
        val target = barcode.trim()
        fun contains(value: Any?): Boolean = when (value) {
            is JSONObject -> value.optString("barcode").trim().equals(target, ignoreCase = true) || value.keys().asSequence().any { contains(value.opt(it)) }
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
            "loyalty_transactions", "customer_loyalty", "products", "inventory_adjustments", "scheduled_due_reminders",
            "store_credit_vouchers", "store_credit_ledger", "sale_audit_logs", "expense_audit_logs"
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

    private fun getTierRank(tier: String?): Int = when (tier?.lowercase(java.util.Locale.ROOT)) {
        "platinum" -> 3
        "gold" -> 2
        "silver" -> 1
        else -> 0
    }
}

sealed class PinVerifyResult {
    data class Success(val user: User) : PinVerifyResult()
    data class Locked(val remainingMillis: Long) : PinVerifyResult()
    data class Deactivated(val user: User) : PinVerifyResult()
    data class Invalid(val remainingMillis: Long = 0L) : PinVerifyResult()
}

sealed class SupervisorVerifyResult {
    data class Success(val user: User) : SupervisorVerifyResult()
    data class Locked(val remainingMillis: Long) : SupervisorVerifyResult()
    data class Deactivated(val user: User) : SupervisorVerifyResult()
    object NotSupervisor : SupervisorVerifyResult()
    data class Invalid(val remainingMillis: Long = 0L) : SupervisorVerifyResult()
}
