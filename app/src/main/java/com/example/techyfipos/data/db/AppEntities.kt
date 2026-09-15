package com.example.techyfipos.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: String,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val stockQty: Double,
    val reorderLevel: Int,
    val imageUri: String? = null,
    val mfgOrPkgType: String? = "Mfg",
    val mfgOrPkgDate: String? = null,
    val expiryDate: String? = null,
    val unit: String = "Pcs",
    val wholesalePrice: Double = 0.0,
    val mrpPrice: Double = 0.0,
    val discountedPrice: Double = 0.0
)

val Product.hasValidExpiryDate: Boolean
    get() = expiryDate.isNullOrBlank() || com.example.techyfipos.utils.parseExpiryDateToMillis(expiryDate) != null

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // "Tea & Refreshments", "Staff Meal", "Electricity (DESCO/DPDC)", "Transport", "Rent", "Cleaning", "Float In", "Other"
    val type: String, // "EXPENSE" or "CASH_IN"
    val amount: Double,
    val paymentSource: String = "Cash", // "Cash", "bKash", "Nagad", "Bank"
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "Admin"
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val companyName: String,
    val phone: String,
    val address: String? = null,
    val totalPurchased: Double = 0.0,
    val totalDue: Double = 0.0,
    val notes: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_invoices",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["supplierId"])]
)
data class PurchaseInvoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val invoiceNumber: String,
    val totalAmount: Double,
    val paidAmount: Double,
    val dueAmount: Double,
    val itemsSummary: String,
    val paymentMethod: String = "Cash",
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)

@Entity(
    tableName = "purchase_invoice_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseInvoice::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["invoiceId"]), Index(value = ["productBarcode"])]
)
data class PurchaseInvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productBarcode: String,
    val productName: String,
    val quantity: Double,
    val unitCost: Double
)

/** Input line used when receiving supplier stock before the invoice id exists. */
data class PurchaseStockLine(
    val productBarcode: String,
    val quantity: Double,
    val unitCost: Double
)

@Entity(
    tableName = "supplier_payments",
    foreignKeys = [
        ForeignKey(
            entity = Supplier::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["supplierId"])]
)
data class SupplierPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long,
    val amountPaid: Double,
    val paymentMethod: String = "Cash",
    val referenceNote: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val performedByName: String? = null
)

@Entity(tableName = "register_closings")
data class RegisterClosing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startingFloat: Double,
    val cashSales: Double,
    val digitalSales: Double,
    val cashIn: Double,
    val cashOut: Double,
    val cashRefunds: Double = 0.0,
    val shiftStartedAt: Long = 0L,
    val expectedCash: Double,
    val actualCountedCash: Double,
    val variance: Double,
    val notes: String? = null,
    val discrepancyReason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "Admin",
    val bkashSales: Double = 0.0,
    val nagadSales: Double = 0.0,
    val rocketSales: Double = 0.0,
    val cardSales: Double = 0.0,
    val dueSales: Double = 0.0
)

@Entity(tableName = "customer_loyalty")
data class CustomerLoyalty(
    @PrimaryKey val phone: String,
    val name: String,
    val pointsBalance: Int = 0,
    val tier: String = "Silver",
    val lifetimeSpent: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(tableName = "loyalty_transactions", indices = [Index(value = ["customerPhone"])])
data class LoyaltyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerPhone: String,
    val pointsChange: Int,
    val spendChange: Double = 0.0,
    val type: String,
    val reason: String,
    val saleId: Long? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val performedByName: String? = null
)

@Entity(tableName = "register_shift_state")
data class RegisterShiftState(
    @PrimaryKey val id: Int = 1,
    val startedAt: Long,
    val startingFloat: Double = 0.0
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val subtotal: Double,
    val discountAmount: Double,
    val isPercentageDiscount: Boolean,
    val grandTotal: Double,
    val paymentMethod: String, // "Cash", "bKash", "Nagad", "Rocket", "Card", "Due", "Split"
    val customerName: String? = null,
    val customerPhone: String? = null,
    val amountPaid: Double = 0.0, // Cash received from customer
    val walletPhone: String? = null,
    val cardType: String? = null,
    val cardLastFour: String? = null,
    val taxRate: Double = 0.0, // Percentage applied at sale time
    val taxAmount: Double = 0.0, // Absolute tax value applied at sale time
    val splitPaymentJson: String? = null,
    val performedByUserId: Long? = null,
    val performedByName: String? = null,
    val loyaltyPhone: String? = null,
    val loyaltyPointsRedeemed: Int = 0,
    val storeCreditApplied: Double = 0.0
)

fun Sale.getFormattedPaymentMethod(): String {
    val method = paymentMethod.trim()
    val phone = walletPhone?.trim() ?: ""
    val type = cardType?.trim() ?: ""
    val lastFour = cardLastFour?.trim() ?: ""

    return when {
        method.equals("bKash", ignoreCase = true) ||
        method.equals("Rocket", ignoreCase = true) ||
        method.equals("Nagad", ignoreCase = true) -> {
            if (phone.isNotBlank()) "$method ($phone)" else method
        }
        method.equals("Card", ignoreCase = true) -> {
            when {
                type.isNotBlank() && lastFour.isNotBlank() -> "$type ($lastFour)"
                lastFour.isNotBlank() -> "Card ($lastFour)"
                type.isNotBlank() -> type
                else -> "Card"
            }
        }
        method.equals("Visa", ignoreCase = true) ||
        method.equals("Mastercard", ignoreCase = true) ||
        method.equals("Amex", ignoreCase = true) -> {
            if (lastFour.isNotBlank()) "$method ($lastFour)" else method
        }
        phone.isNotBlank() -> "$method ($phone)"
        lastFour.isNotBlank() -> {
            val cardBrand = if (type.isNotBlank()) type else method
            "$cardBrand ($lastFour)"
        }
        else -> method
    }
}

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["saleId"])]
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productBarcode: String,
    val productName: String,
    val price: Double,
    val quantity: Double,
    val note: String? = null,
    // The product's purchasePrice at the moment of sale, so historical profit reports stay stable
    // even if the product's cost is edited (or the product is deleted) afterward. Null on rows
    // written before this field existed; reports fall back to a live/estimated cost for those.
    val purchasePriceAtSale: Double? = null
)

@Entity(tableName = "customer_dues")
data class CustomerDue(
    @PrimaryKey val customerPhone: String,
    val customerName: String,
    val outstandingAmount: Double,
    val creditLimit: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(tableName = "inventory_adjustments")
data class InventoryAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productBarcode: String,
    val productName: String,
    val previousStock: Double,
    val newStock: Double,
    val difference: Double,
    val reason: String, // "Damage", "Expired", "Discrepancy", "Sample", "Internal", "Other"
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val cashierName: String = "Admin"
)

@Entity(
    tableName = "due_payments",
    foreignKeys = [
        ForeignKey(
            entity = CustomerDue::class,
            parentColumns = ["customerPhone"],
            childColumns = ["customerPhone"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerPhone"])]
)
data class DuePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerPhone: String,
    val amountPaid: Double,
    val timestamp: Long,
    val paymentMethod: String = "Cash",
    val referenceNote: String? = null
)

// The debit side of a customer's due ledger — a charge that increased their outstanding balance.
// Pairs with DuePayment (the credit side) to give the Transaction History a full statement instead
// of only ever showing collections. reason "Sale" links back to the sale that created the due
// (saleId set); "Manual" is a standalone charge entered via the Add Due dialog.
@Entity(
    tableName = "due_charges",
    foreignKeys = [
        ForeignKey(
            entity = CustomerDue::class,
            parentColumns = ["customerPhone"],
            childColumns = ["customerPhone"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerPhone"])]
)
data class DueCharge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerPhone: String,
    val amount: Double,
    val timestamp: Long,
    val reason: String = "Manual", // "Manual" or "Sale"
    val saleId: Long? = null,
    val note: String? = null
)

// Role-based access: "OWNER" (full master), "MANAGER" (store operations), "CASHIER" (frontline sales)
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pinHash: String,
    val role: String, // "OWNER", "MANAGER", or "CASHIER"
    val customPermissions: String? = null,
    val isActive: Boolean = true,
    val maxDiscountPercent: Double = 10.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "returns",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["saleId"])]
)
data class Return(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val timestamp: Long,
    val refundMethod: String, // "Cash", "Original method", "Store credit"
    val refundAmount: Double,
    /** Portion of [refundAmount] actually paid back to the customer. */
    val externalRefundAmount: Double = refundAmount,
    /** Portion of [refundAmount] settled by reducing the sale's unpaid due. */
    val dueAdjustmentAmount: Double = 0.0,
    val note: String? = null,
    val performedByUserId: Long? = null,
    val performedByName: String? = null
)

@Entity(
    tableName = "return_items",
    foreignKeys = [
        ForeignKey(
            entity = Return::class,
            parentColumns = ["id"],
            childColumns = ["returnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["returnId"])]
)
data class ReturnItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val productBarcode: String,
    val productName: String,
    val price: Double,
    val quantity: Double
)

@Entity(tableName = "held_carts")
data class HeldCart(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val itemsJson: String,
    val discountAmount: Double = 0.0,
    val isPercentageDiscount: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)

data class SplitPaymentEntry(
    val method: String, // "Cash", "bKash", "Nagad", "Rocket", "Card", "Due"
    val amount: Double,
    val walletPhone: String? = null,
    val cardType: String? = null,
    val cardLastFour: String? = null,
    val referenceNote: String? = null
)

// One row per pending WorkManager due reminder (primary key mirrors the unique work name's
// digits-only phone), so Settings/Dues can list and cancel a scheduled reminder before it fires.
@Entity(tableName = "scheduled_due_reminders")
data class ScheduledDueReminder(
    @PrimaryKey val customerPhone: String,
    val customerName: String,
    val dueAmount: Double,
    val channel: String,
    val scheduledAt: Long,
    val fireAt: Long,
    val messageText: String = "",
    val isRecurring: Boolean = false
)

@Entity(tableName = "due_reminder_logs")
data class DueReminderLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerPhone: String,
    val customerName: String,
    val dueAmount: Double,
    val channel: String, // "WHATSAPP", "SMS", "SCHEDULED", "DIRECT"
    val messageText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SENT" // "SENT", "SCHEDULED", "CANCELLED", "FIRED"
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val phone: String,
    val name: String,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val tag: String = "Retail", // "Retail", "Wholesale", "VIP", "Corporate", "Staff", "Defaulter"
    val creditLimit: Double = 0.0,
    val totalOrders: Int = 0,
    val lifetimeSpent: Double = 0.0,
    val totalDue: Double = 0.0,
    val loyaltyPoints: Int = 0,
    val loyaltyTier: String = "Silver",
    val firstPurchaseDate: Long? = null,
    val lastPurchaseDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val storeCredit: Double = 0.0
)

data class CustomerLookupResult(
    val phone: String,
    val name: String,
    val source: String, // "CUSTOMER_DB", "DUE_RECORD", "LOYALTY", "PAST_SALE"
    val dueAmount: Double = 0.0,
    val loyaltyPoints: Int = 0,
    val tier: String = "Silver",
    val tag: String? = null,
    val creditLimit: Double = 0.0,
    val lifetimeSpent: Double = 0.0,
    val totalOrders: Int = 0,
    val address: String? = null,
    val notes: String? = null,
    val storeCredit: Double = 0.0
)

data class CustomerProductPurchaseSummary(
    val barcode: String,
    val name: String,
    val totalQty: Double,
    val totalSpent: Double,
    val purchaseCount: Int
)

data class CustomerContactInfo(
    val customerPhone: String,
    val customerName: String?
)

@Entity(tableName = "sale_audit_logs", indices = [Index(value = ["saleId"])])
data class SaleAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val action: String, // "DELETED", "VOIDED_FOR_EDIT"
    val reason: String,
    val grandTotal: Double,
    val paymentMethod: String,
    val cashierName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "expense_audit_logs", indices = [Index(value = ["expenseId"])])
data class ExpenseAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val action: String, // "DELETED", "EDITED"
    val reason: String,
    val amount: Double,
    val category: String,
    val paymentSource: String,
    val userName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "store_credit_vouchers",
    indices = [Index(value = ["code"], unique = true), Index(value = ["customerPhone"])]
)
data class StoreCreditVoucher(
    @PrimaryKey val code: String, // e.g. "SC-7492-9182"
    val initialAmount: Double,
    val remainingBalance: Double,
    val customerPhone: String? = null,
    val sourceReturnId: Long? = null,
    val sourceSaleId: Long? = null,
    val issuedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val status: String = "ACTIVE", // "ACTIVE", "EXHAUSTED", "EXPIRED", "VOID"
    val notes: String? = null
)

@Entity(
    tableName = "store_credit_ledger",
    indices = [Index(value = ["customerPhone"]), Index(value = ["timestamp"])]
)
data class StoreCreditLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerPhone: String,
    val deltaAmount: Double, // positive for additions (refund/adjustment), negative for redemptions
    val balanceAfter: Double,
    val type: String, // "RETURN_REFUND", "SALE_REDEMPTION", "MANUAL_ADJUST", "VOID"
    val referenceInvoiceId: Long? = null,
    val voucherCode: String? = null,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
