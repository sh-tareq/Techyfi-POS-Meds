package com.example.techyfipos.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsList(): List<Product>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET stockQty = MAX(0.0, stockQty + :delta) WHERE barcode = :barcode")
    suspend fun adjustStockQty(barcode: String, delta: Double)

    @Query("UPDATE products SET stockQty = stockQty - :quantity WHERE barcode = :barcode AND stockQty >= :quantity")
    suspend fun decrementStockIfAvailable(barcode: String, quantity: Double): Int

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM products WHERE name LIKE :query OR barcode LIKE :query OR category LIKE :query ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE name LIKE :query OR barcode LIKE :query OR barcode LIKE :altQuery OR category LIKE :query ORDER BY name ASC")
    fun searchProductsWithAlt(query: String, altQuery: String): Flow<List<Product>>

    @Query("UPDATE products SET category = :newCategory WHERE category = :oldCategory")
    suspend fun renameCategory(oldCategory: String, newCategory: String)
}

data class ReturnLine(
    val barcode: String,
    val name: String,
    val price: Double,
    val quantity: Double
)

data class SaleWithItems(
    @Embedded val sale: Sale,
    @Relation(
        parentColumn = "id",
        entityColumn = "saleId"
    )
    val items: List<SaleItem>
)

@Dao
interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Transaction
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSalesWithItems(): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :saleId")
    suspend fun getSaleWithItemsById(saleId: Long): SaleWithItems?

    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales")
    suspend fun getAllSalesOnce(): List<Sale>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getItemsForSale(saleId: Long): List<SaleItem>

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun deleteSaleById(saleId: Long)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItemsBySaleId(saleId: Long)

    @Transaction
    suspend fun deleteSale(saleId: Long) {
        deleteSaleItemsBySaleId(saleId)
        deleteSaleById(saleId)
    }

    @Query("SELECT customerName FROM sales WHERE customerPhone = :phone AND customerName IS NOT NULL AND TRIM(customerName) != '' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCustomerNameByPhone(phone: String): String?

    @Query("SELECT DISTINCT customerPhone, customerName FROM sales WHERE customerPhone IS NOT NULL AND TRIM(customerPhone) != '' ORDER BY timestamp DESC")
    suspend fun getAllPastCustomerContacts(): List<CustomerContactInfo>

    @Query("SELECT * FROM sales WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getSalesForCustomer(phone: String): Flow<List<Sale>>

    @Transaction
    @Query("SELECT * FROM sales WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getSalesWithItemsForCustomer(phone: String): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE customerPhone = :phone ORDER BY timestamp DESC")
    suspend fun getSalesWithItemsForCustomerOnce(phone: String): List<SaleWithItems>

    @Query("UPDATE sales SET customerPhone = :newPhone, customerName = :newName WHERE customerPhone = :oldPhone")
    suspend fun reassignCustomerForSales(oldPhone: String, newPhone: String, newName: String): Int

    @Query("""
        SELECT si.productBarcode AS barcode, si.productName AS name, 
               SUM(si.quantity) AS totalQty, 
               SUM(si.price * si.quantity) AS totalSpent, 
               COUNT(si.id) AS purchaseCount 
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id 
        WHERE s.customerPhone = :phone 
        GROUP BY si.productBarcode, si.productName 
        ORDER BY totalQty DESC, totalSpent DESC 
        LIMIT :limit
    """)
    suspend fun getTopProductsForCustomer(phone: String, limit: Int = 10): List<CustomerProductPurchaseSummary>
}

@Dao
interface CustomerDueDao {
    @Query("SELECT * FROM customer_dues WHERE outstandingAmount > 0 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllOutstandingDues(): Flow<List<CustomerDue>>

    @Query("SELECT * FROM customer_dues WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllDues(): Flow<List<CustomerDue>>

    @Query("SELECT * FROM customer_dues ORDER BY updatedAt DESC")
    suspend fun getAllDuesIncludingArchived(): List<CustomerDue>

    @Query("SELECT * FROM customer_dues WHERE customerPhone = :phone LIMIT 1")
    suspend fun getCustomerDueByPhone(phone: String): CustomerDue?

    @Query("SELECT * FROM customer_dues WHERE customerPhone = :phone AND isArchived = 0 LIMIT 1")
    suspend fun getActiveCustomerDueByPhone(phone: String): CustomerDue?

    // A REPLACE here deletes the existing parent row before inserting it again.
    // Because due_payments references this table with ON DELETE CASCADE, that
    // behavior erased the customer's previous payment history on every update.
    @Upsert
    suspend fun upsertCustomerDue(customerDue: CustomerDue)

    @Query("SELECT * FROM due_payments WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getPaymentsForCustomer(phone: String): Flow<List<DuePayment>>

    @Query("SELECT * FROM due_payments")
    fun getAllDuePayments(): Flow<List<DuePayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuePayment(payment: DuePayment)

    // Used only by the duplicate-ledger repair tool to move a stale (unnormalized-phone) row's
    // payment history onto the canonical row before the stale row is deleted.
    @Query("UPDATE due_payments SET customerPhone = :newPhone WHERE customerPhone = :oldPhone")
    suspend fun reassignDuePaymentsPhone(oldPhone: String, newPhone: String)

    @Query("SELECT * FROM due_payments WHERE id = :id LIMIT 1")
    suspend fun getDuePaymentById(id: Long): DuePayment?

    @Query("DELETE FROM due_payments WHERE id = :id")
    suspend fun deleteDuePaymentById(id: Long)

    // The debit side of the ledger (see DueCharge) — charges that increased a customer's balance,
    // from a Due sale (reason "Sale") or a standalone entry via the Add Due dialog ("Manual").
    @Query("SELECT * FROM due_charges WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getChargesForCustomer(phone: String): Flow<List<DueCharge>>

    @Query("SELECT * FROM due_charges")
    fun getAllDueCharges(): Flow<List<DueCharge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDueCharge(charge: DueCharge)

    @Query("SELECT * FROM due_charges WHERE id = :id LIMIT 1")
    suspend fun getDueChargeById(id: Long): DueCharge?

    @Query("DELETE FROM due_charges WHERE id = :id")
    suspend fun deleteDueChargeById(id: Long)

    // Cleans up the ledger entry when the underlying sale is deleted; deleteSale() already
    // reverses the due amount itself via reduceCustomerDue, so this must not touch the balance.
    @Query("DELETE FROM due_charges WHERE saleId = :saleId")
    suspend fun deleteDueChargesBySaleId(saleId: Long)

    @Query("UPDATE due_charges SET customerPhone = :newPhone WHERE customerPhone = :oldPhone")
    suspend fun reassignDueChargesPhone(oldPhone: String, newPhone: String)

    @Query("SELECT COUNT(*) FROM customer_dues")
    suspend fun getDueCount(): Int

    @Query("DELETE FROM customer_dues WHERE customerPhone = :phone")
    suspend fun deleteCustomerDueByPhone(phone: String)

    @Query("DELETE FROM due_payments WHERE customerPhone = :phone")
    suspend fun deleteDuePaymentsByPhone(phone: String)

    @Query("DELETE FROM due_charges WHERE customerPhone = :phone")
    suspend fun deleteDueChargesByPhone(phone: String)

    @Transaction
    suspend fun deleteCustomerDue(phone: String) {
        deleteCustomerDueByPhone(phone)
        deleteDuePaymentsByPhone(phone)
        deleteDueChargesByPhone(phone)
    }

    @Query("UPDATE customer_dues SET isArchived = 1, updatedAt = :timestamp WHERE customerPhone = :phone")
    suspend fun archiveCustomerDue(phone: String, timestamp: Long): Int

    @Transaction
    suspend fun recordDuePayment(
        phone: String,
        amountPaid: Double,
        timestamp: Long,
        paymentMethod: String = "Cash",
        referenceNote: String? = null
    ) {
        val currentDue = getCustomerDueByPhone(phone)
            ?: throw IllegalArgumentException("No active due account found for customer $phone")
        require(amountPaid.isFinite() && amountPaid > 0.0) { "Payment amount must be positive and finite" }
        val appliedAmount = amountPaid.coerceAtMost(currentDue.outstandingAmount)
        val newAmount = (currentDue.outstandingAmount - appliedAmount).coerceAtLeast(0.0)
        upsertCustomerDue(currentDue.copy(outstandingAmount = newAmount, updatedAt = timestamp))
        insertDuePayment(
            DuePayment(
                customerPhone = phone,
                amountPaid = appliedAmount,
                timestamp = timestamp,
                paymentMethod = paymentMethod,
                referenceNote = referenceNote
            )
        )
    }
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY createdAt ASC")
    suspend fun getAllUsersOnce(): List<User>

    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getActiveUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY createdAt ASC")
    suspend fun getActiveUsersOnce(): List<User>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'OWNER'")
    suspend fun getOwnerCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'OWNER' AND isActive = 1")
    suspend fun getActiveOwnerCount(): Int

    @Query("SELECT * FROM users WHERE (role = 'OWNER' OR role = 'MANAGER') AND isActive = 1")
    suspend fun getActiveSupervisors(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("UPDATE users SET pinHash = :pinHash WHERE id = :userId")
    suspend fun updatePinHash(userId: Long, pinHash: String)

    @Query("UPDATE users SET customPermissions = :customPermissions WHERE id = :userId")
    suspend fun updatePermissions(userId: Long, customPermissions: String?)

    @Query("UPDATE users SET isActive = :isActive WHERE id = :userId")
    suspend fun updateActiveStatus(userId: Long, isActive: Boolean)

    @Query("UPDATE users SET role = :role WHERE id = :userId")
    suspend fun updateRole(userId: Long, role: String)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)
}

@Dao
interface ReturnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturn(returnRecord: Return): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReturnItems(items: List<ReturnItem>)

    @Query("SELECT * FROM return_items WHERE returnId IN (SELECT id FROM returns WHERE saleId = :saleId)")
    suspend fun getReturnItemsForSale(saleId: Long): List<ReturnItem>

    @Query("SELECT * FROM returns WHERE saleId = :saleId ORDER BY timestamp DESC")
    fun getReturnsForSale(saleId: Long): Flow<List<Return>>

    @Query("SELECT * FROM returns WHERE saleId = :saleId ORDER BY timestamp DESC")
    suspend fun getReturnsForSaleOnce(saleId: Long): List<Return>

    @Query("SELECT COALESCE(SUM(refundAmount), 0.0) FROM returns WHERE saleId = :saleId")
    suspend fun getRefundTotalForSale(saleId: Long): Double

    @Query("SELECT * FROM returns")
    fun getAllReturns(): Flow<List<Return>>

    @Query("SELECT * FROM return_items")
    fun getAllReturnItems(): Flow<List<ReturnItem>>
}

@Dao
interface HeldCartDao {
    @Query("SELECT * FROM held_carts ORDER BY timestamp DESC")
    fun getAllHeldCarts(): Flow<List<HeldCart>>

    @Query("SELECT * FROM held_carts ORDER BY timestamp DESC")
    suspend fun getAllHeldCartsOnce(): List<HeldCart>

    @Query("SELECT * FROM held_carts WHERE id = :id LIMIT 1")
    suspend fun getHeldCartById(id: Long): HeldCart?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeldCart(heldCart: HeldCart): Long

    @Query("DELETE FROM held_carts WHERE id = :id")
    suspend fun deleteHeldCartById(id: Long)

    @Query("DELETE FROM held_carts")
    suspend fun clearAllHeldCarts()

    @Query("SELECT COUNT(*) FROM held_carts")
    fun getHeldCartCount(): Flow<Int>
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getExpensesBetween(startTime: Long, endTime: Long): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE type = 'EXPENSE' AND paymentSource = 'Cash' AND timestamp >= :startTime")
    suspend fun getCashExpensesSince(startTime: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE type = 'CASH_IN' AND paymentSource = 'Cash' AND timestamp >= :startTime")
    suspend fun getCashInSince(startTime: Long): Double
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun getSupplierById(id: Long): Supplier?

    @Upsert
    suspend fun upsertSupplier(supplier: Supplier): Long

    @Delete
    suspend fun deleteSupplier(supplier: Supplier)

    @Query("UPDATE suppliers SET totalPurchased = totalPurchased + :purchaseAmount, totalDue = totalDue + :dueChange, updatedAt = :timestamp WHERE id = :supplierId")
    suspend fun updateSupplierBalances(supplierId: Long, purchaseAmount: Double, dueChange: Double, timestamp: Long): Int

    @Query("UPDATE suppliers SET totalDue = totalDue - :amountPaid, updatedAt = :timestamp WHERE id = :supplierId AND :amountPaid > 0.0 AND totalDue >= :amountPaid")
    suspend fun recordSupplierPayment(supplierId: Long, amountPaid: Double, timestamp: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPayment(payment: SupplierPayment): Long

    @Query("SELECT * FROM supplier_payments WHERE supplierId = :supplierId ORDER BY timestamp DESC")
    fun getPaymentsForSupplier(supplierId: Long): Flow<List<SupplierPayment>>

    @Query("SELECT * FROM supplier_payments ORDER BY timestamp DESC")
    fun getAllSupplierPayments(): Flow<List<SupplierPayment>>
}

@Dao
interface PurchaseInvoiceDao {
    @Query("SELECT * FROM purchase_invoices ORDER BY timestamp DESC")
    fun getAllPurchaseInvoices(): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE supplierId = :supplierId ORDER BY timestamp DESC")
    fun getInvoicesForSupplier(supplierId: Long): Flow<List<PurchaseInvoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseInvoice(invoice: PurchaseInvoice): Long

    @Delete
    suspend fun deletePurchaseInvoice(invoice: PurchaseInvoice)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseInvoiceItems(items: List<PurchaseInvoiceItem>)

    @Query("SELECT * FROM purchase_invoice_items WHERE invoiceId = :invoiceId ORDER BY id ASC")
    fun getItemsForInvoice(invoiceId: Long): Flow<List<PurchaseInvoiceItem>>

    @Query("SELECT * FROM purchase_invoice_items ORDER BY invoiceId ASC, id ASC")
    fun getAllPurchaseInvoiceItems(): Flow<List<PurchaseInvoiceItem>>
}

@Dao
interface RegisterClosingDao {
    @Query("SELECT * FROM register_closings ORDER BY timestamp DESC")
    fun getAllClosings(): Flow<List<RegisterClosing>>

    @Query("SELECT * FROM register_closings WHERE date = :date LIMIT 1")
    suspend fun getClosingByDate(date: String): RegisterClosing?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClosing(closing: RegisterClosing): Long
}

@Dao
interface RegisterShiftStateDao {
    @Query("SELECT * FROM register_shift_state WHERE id = 1")
    fun observe(): Flow<RegisterShiftState?>

    @Query("SELECT * FROM register_shift_state WHERE id = 1")
    suspend fun get(): RegisterShiftState?

    @Upsert
    suspend fun upsert(state: RegisterShiftState)
}

@Dao
interface CustomerLoyaltyDao {
    @Query("SELECT * FROM customer_loyalty WHERE isActive = 1 ORDER BY lifetimeSpent DESC")
    fun getAllLoyaltyMembers(): Flow<List<CustomerLoyalty>>

    @Query("SELECT * FROM customer_loyalty ORDER BY lifetimeSpent DESC")
    suspend fun getAllIncludingInactive(): List<CustomerLoyalty>

    @Query("SELECT * FROM customer_loyalty WHERE phone = :phone LIMIT 1")
    suspend fun getLoyaltyByPhone(phone: String): CustomerLoyalty?

    @Upsert
    suspend fun upsertLoyalty(loyalty: CustomerLoyalty)

    @Query("UPDATE customer_loyalty SET pointsBalance = pointsBalance + :pts, lifetimeSpent = lifetimeSpent + :spend, updatedAt = :timestamp WHERE phone = :phone")
    suspend fun addPoints(phone: String, pts: Int, spend: Double, timestamp: Long)

    @Query("UPDATE customer_loyalty SET pointsBalance = MAX(0, pointsBalance - :pts), lifetimeSpent = MAX(0.0, lifetimeSpent - :spend), updatedAt = :timestamp WHERE phone = :phone AND :pts >= 0 AND :spend >= 0.0")
    suspend fun reverseEarnedPoints(phone: String, pts: Int, spend: Double, timestamp: Long): Int

    @Query("UPDATE customer_loyalty SET pointsBalance = pointsBalance - :pts, updatedAt = :timestamp WHERE phone = :phone AND :pts > 0 AND pointsBalance >= :pts")
    suspend fun redeemPoints(phone: String, pts: Int, timestamp: Long): Int

    @Query("UPDATE customer_loyalty SET pointsBalance = pointsBalance + :pts, updatedAt = :timestamp WHERE phone = :phone AND :pts > 0")
    suspend fun restoreRedeemedPoints(phone: String, pts: Int, timestamp: Long): Int

    @Query("UPDATE customer_loyalty SET isActive = :active, updatedAt = :timestamp WHERE phone = :phone")
    suspend fun setActive(phone: String, active: Boolean, timestamp: Long): Int

    @Query("DELETE FROM customer_loyalty WHERE phone = :phone")
    suspend fun delete(phone: String): Int

    @Delete
    suspend fun deleteLoyalty(loyalty: CustomerLoyalty)

    @Query("DELETE FROM customer_loyalty WHERE phone = :phone")
    suspend fun deleteLoyaltyByPhone(phone: String)
}

@Dao
interface LoyaltyTransactionDao {
    @Query("SELECT * FROM loyalty_transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LoyaltyTransaction>>

    @Query("SELECT * FROM loyalty_transactions WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getForCustomer(phone: String): Flow<List<LoyaltyTransaction>>

    @Query("UPDATE loyalty_transactions SET customerPhone = :newPhone WHERE customerPhone = :oldPhone")
    suspend fun reassignCustomerPhone(oldPhone: String, newPhone: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: LoyaltyTransaction): Long
}

@Dao
interface InventoryAdjustmentDao {
    @Query("SELECT * FROM inventory_adjustments ORDER BY timestamp DESC")
    fun getAllAdjustments(): Flow<List<InventoryAdjustment>>

    @Query("SELECT * FROM inventory_adjustments WHERE productBarcode = :barcode ORDER BY timestamp DESC")
    fun getAdjustmentsForProduct(barcode: String): Flow<List<InventoryAdjustment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(adjustment: InventoryAdjustment): Long
}

@Dao
interface ScheduledDueReminderDao {
    @Query("SELECT * FROM scheduled_due_reminders ORDER BY fireAt ASC")
    fun getAll(): Flow<List<ScheduledDueReminder>>

    @Query("SELECT * FROM scheduled_due_reminders ORDER BY fireAt ASC")
    suspend fun getAllOnce(): List<ScheduledDueReminder>

    @Upsert
    suspend fun upsert(reminder: ScheduledDueReminder)

    @Query("DELETE FROM scheduled_due_reminders WHERE customerPhone = :phone")
    suspend fun deleteByPhone(phone: String)
}

@Dao
interface DueReminderLogDao {
    @Query("SELECT * FROM due_reminder_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<DueReminderLog>>

    @Query("SELECT * FROM due_reminder_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsOnce(): List<DueReminderLog>

    @Query("SELECT * FROM due_reminder_logs WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getLogsForCustomer(phone: String): Flow<List<DueReminderLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DueReminderLog): Long

    @Query("DELETE FROM due_reminder_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM due_reminder_logs")
    suspend fun clearAllLogs()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getAllCustomersOnce(): List<Customer>

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): Customer?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    fun observeCustomerByPhone(phone: String): Flow<Customer?>

    @Query("""
        SELECT * FROM customers 
        WHERE isArchived = 0 AND (
            name LIKE '%' || :query || '%' OR 
            phone LIKE '%' || :query || '%' OR 
            address LIKE '%' || :query || '%' OR 
            notes LIKE '%' || :query || '%' OR 
            tag LIKE '%' || :query || '%'
        ) 
        ORDER BY updatedAt DESC
    """)
    fun searchCustomers(query: String): Flow<List<Customer>>

    @Query("""
        SELECT * FROM customers 
        WHERE isArchived = 0 AND (
            phone LIKE '%' || :query || '%' OR 
            name LIKE '%' || :query || '%'
        ) 
        ORDER BY lifetimeSpent DESC 
        LIMIT 10
    """)
    suspend fun searchCustomerSuggestions(query: String): List<Customer>

    @Upsert
    suspend fun upsertCustomer(customer: Customer)

    @Query("UPDATE customers SET isArchived = 1, updatedAt = :timestamp WHERE phone = :phone")
    suspend fun archiveCustomer(phone: String, timestamp: Long): Int

    @Query("UPDATE customers SET totalDue = :totalDue, updatedAt = :timestamp WHERE phone = :phone")
    suspend fun updateCustomerTotalDue(phone: String, totalDue: Double, timestamp: Long): Int

    @Query("DELETE FROM customers WHERE phone = :phone")
    suspend fun deleteCustomerPermanently(phone: String)

    @Query("SELECT COUNT(*) FROM customers WHERE isArchived = 0")
    suspend fun getCustomerCount(): Int

    @Query("UPDATE customers SET totalDue = :dueAmount, updatedAt = :timestamp WHERE phone = :phone")
    suspend fun updateCustomerDue(phone: String, dueAmount: Double, timestamp: Long): Int

    @Query("UPDATE customers SET loyaltyPoints = :points, loyaltyTier = :tier, updatedAt = :timestamp WHERE phone = :phone")
    suspend fun updateCustomerLoyalty(phone: String, points: Int, tier: String, timestamp: Long): Int

    @Query("UPDATE customers SET storeCredit = :credit, updatedAt = :timestamp WHERE phone = :phone")
    suspend fun updateCustomerStoreCredit(phone: String, credit: Double, timestamp: Long): Int

    @Query("UPDATE customers SET storeCredit = MAX(0.0, storeCredit + :delta), updatedAt = :timestamp WHERE phone = :phone")
    suspend fun adjustCustomerStoreCredit(phone: String, delta: Double, timestamp: Long): Int
}

@Dao
interface SaleAuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: SaleAuditLog): Long

    @Query("SELECT * FROM sale_audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<SaleAuditLog>>

    @Query("SELECT * FROM sale_audit_logs WHERE saleId = :saleId ORDER BY timestamp DESC")
    suspend fun getAuditLogsForSale(saleId: Long): List<SaleAuditLog>
}

@Dao
interface ExpenseAuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: ExpenseAuditLog): Long

    @Query("SELECT * FROM expense_audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<ExpenseAuditLog>>

    @Query("SELECT * FROM expense_audit_logs WHERE expenseId = :expenseId ORDER BY timestamp DESC")
    suspend fun getAuditLogsForExpense(expenseId: Long): List<ExpenseAuditLog>
}


