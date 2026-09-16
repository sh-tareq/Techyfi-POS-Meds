package com.example.techyfipos.data

import com.example.techyfipos.R
import com.example.techyfipos.data.db.User
import org.json.JSONArray

object AppRoles {
    const val OWNER = "OWNER"
    const val MANAGER = "MANAGER"
    const val CASHIER = "CASHIER"
}

data class PermissionItem(
    val key: String,
    val titleRes: Int,
    val descRes: Int
)

data class PermissionCategory(
    val id: String,
    val titleRes: Int,
    val permissions: List<PermissionItem>
)

object AppPermissions {
    // 1. Sales & POS Checkout
    const val SALES_SELL = "sales.sell"
    const val SALES_APPLY_DISCOUNT = "sales.apply_discount"
    const val SALES_HIGH_DISCOUNT = "sales.high_discount"
    const val SALES_PRICE_TIER = "sales.price_tier"
    const val SALES_HOLD_CART = "sales.hold_cart"
    const val SALES_VOID_CART = "sales.void_cart"
    const val SALES_EDIT_INVOICE = "sales.edit_invoice"

    // 2. Returns & Refunds
    const val RETURNS_PROCESS = "returns.process"
    const val RETURNS_CASH_REFUND = "returns.cash_refund"
    const val RETURNS_DELETE_SALE = "returns.delete_sale"

    // 3. Inventory & Products
    const val INVENTORY_VIEW = "inventory.view"
    const val INVENTORY_ADD_EDIT = "inventory.add_edit"
    const val INVENTORY_ADJUST_STOCK = "inventory.adjust_stock"
    const val INVENTORY_VIEW_COSTS = "inventory.view_costs"
    const val INVENTORY_PRINT_LABELS = "inventory.print_labels"
    const val INVENTORY_DELETE = "inventory.delete"

    // 4. Customer Dues & Credit
    const val CUSTOMERS_VIEW = "customers.view"
    const val DUES_VIEW = "dues.view"
    const val DUES_ADD_DUE = "dues.add_due"
    const val DUES_COLLECT_PAYMENT = "dues.collect_payment"
    const val DUES_OVERRIDE_LIMIT = "dues.override_limit"
    const val DUES_SEND_REMINDERS = "dues.send_reminders"

    // 5. Reports & Cash Register
    const val REPORTS_VIEW_DAILY = "reports.view_daily"
    const val REPORTS_VIEW_PNL = "reports.view_pnl"
    const val REPORTS_CLOSE_REGISTER = "reports.close_register"
    const val REPORTS_VIEW_Z_REPORT = "reports.view_z_report"
    const val REPORTS_EXPORT_EXCEL = "reports.export_excel"

    // 6. Customer Loyalty & Rewards
    const val LOYALTY_MANAGE = "loyalty.manage"

    // 7. Suppliers & Inward Stock
    const val SUPPLIERS_MANAGE = "suppliers.manage"

    // 8. Expenses & Petty Cash
    const val EXPENSES_MANAGE = "expenses.manage"

    // 9. System Administration
    const val SYSTEM_MANAGE_STAFF = "system.manage_staff"
    const val SYSTEM_PERMISSIONS = "system.permissions"
    const val SYSTEM_BACKUP_RESTORE = "system.backup_restore"
    const val SYSTEM_SHOP_SETTINGS = "system.shop_settings"
    const val SYSTEM_FACTORY_RESET = "system.factory_reset"

    val ALL_PERMISSIONS = listOf(
        SALES_SELL, SALES_APPLY_DISCOUNT, SALES_HIGH_DISCOUNT, SALES_PRICE_TIER, SALES_HOLD_CART, SALES_VOID_CART, SALES_EDIT_INVOICE,
        RETURNS_PROCESS, RETURNS_CASH_REFUND, RETURNS_DELETE_SALE,
        INVENTORY_VIEW, INVENTORY_ADD_EDIT, INVENTORY_ADJUST_STOCK, INVENTORY_VIEW_COSTS, INVENTORY_PRINT_LABELS, INVENTORY_DELETE,
        CUSTOMERS_VIEW, DUES_VIEW, DUES_ADD_DUE, DUES_COLLECT_PAYMENT, DUES_OVERRIDE_LIMIT, DUES_SEND_REMINDERS,
        REPORTS_VIEW_DAILY, REPORTS_VIEW_PNL, REPORTS_CLOSE_REGISTER, REPORTS_VIEW_Z_REPORT, REPORTS_EXPORT_EXCEL,
        LOYALTY_MANAGE,
        SUPPLIERS_MANAGE,
        EXPENSES_MANAGE,
        SYSTEM_MANAGE_STAFF, SYSTEM_PERMISSIONS, SYSTEM_BACKUP_RESTORE, SYSTEM_SHOP_SETTINGS, SYSTEM_FACTORY_RESET
    )

    val DEFAULT_OWNER_PERMISSIONS: Set<String> = ALL_PERMISSIONS.toSet()

    val DEFAULT_MANAGER_PERMISSIONS: Set<String> = setOf(
        SALES_SELL, SALES_APPLY_DISCOUNT, SALES_HIGH_DISCOUNT, SALES_PRICE_TIER, SALES_HOLD_CART, SALES_VOID_CART, SALES_EDIT_INVOICE,
        RETURNS_PROCESS, RETURNS_CASH_REFUND,
        INVENTORY_VIEW, INVENTORY_ADD_EDIT, INVENTORY_ADJUST_STOCK, INVENTORY_VIEW_COSTS, INVENTORY_PRINT_LABELS,
        CUSTOMERS_VIEW, DUES_VIEW, DUES_ADD_DUE, DUES_COLLECT_PAYMENT, DUES_OVERRIDE_LIMIT, DUES_SEND_REMINDERS,
        REPORTS_VIEW_DAILY, REPORTS_VIEW_PNL, REPORTS_CLOSE_REGISTER, REPORTS_VIEW_Z_REPORT, REPORTS_EXPORT_EXCEL,
        LOYALTY_MANAGE,
        SUPPLIERS_MANAGE,
        EXPENSES_MANAGE
    )

    val DEFAULT_CASHIER_PERMISSIONS: Set<String> = setOf(
        SALES_SELL, SALES_APPLY_DISCOUNT, SALES_HOLD_CART,
        INVENTORY_VIEW,
        DUES_VIEW, DUES_COLLECT_PAYMENT,
        REPORTS_VIEW_DAILY
    )

    val CATEGORIES: List<PermissionCategory> = listOf(
        PermissionCategory(
            id = "sales",
            titleRes = R.string.perm_cat_sales,
            permissions = listOf(
                PermissionItem(SALES_SELL, R.string.perm_sales_sell_title, R.string.perm_sales_sell_desc),
                PermissionItem(SALES_APPLY_DISCOUNT, R.string.perm_sales_discount_title, R.string.perm_sales_discount_desc),
                PermissionItem(SALES_HIGH_DISCOUNT, R.string.perm_sales_high_discount_title, R.string.perm_sales_high_discount_desc),
                PermissionItem(SALES_PRICE_TIER, R.string.perm_sales_price_tier_title, R.string.perm_sales_price_tier_desc),
                PermissionItem(SALES_HOLD_CART, R.string.perm_sales_hold_cart_title, R.string.perm_sales_hold_cart_desc),
                PermissionItem(SALES_VOID_CART, R.string.perm_sales_void_cart_title, R.string.perm_sales_void_cart_desc),
                PermissionItem(SALES_EDIT_INVOICE, R.string.perm_sales_edit_invoice_title, R.string.perm_sales_edit_invoice_desc)
            )
        ),
        PermissionCategory(
            id = "returns",
            titleRes = R.string.perm_cat_returns,
            permissions = listOf(
                PermissionItem(RETURNS_PROCESS, R.string.perm_returns_process_title, R.string.perm_returns_process_desc),
                PermissionItem(RETURNS_CASH_REFUND, R.string.perm_returns_cash_refund_title, R.string.perm_returns_cash_refund_desc),
                PermissionItem(RETURNS_DELETE_SALE, R.string.perm_returns_delete_sale_title, R.string.perm_returns_delete_sale_desc)
            )
        ),
        PermissionCategory(
            id = "inventory",
            titleRes = R.string.perm_cat_inventory,
            permissions = listOf(
                PermissionItem(INVENTORY_VIEW, R.string.perm_inventory_view_title, R.string.perm_inventory_view_desc),
                PermissionItem(INVENTORY_ADD_EDIT, R.string.perm_inventory_add_edit_title, R.string.perm_inventory_add_edit_desc),
                PermissionItem(INVENTORY_ADJUST_STOCK, R.string.perm_inventory_adjust_stock_title, R.string.perm_inventory_adjust_stock_desc),
                PermissionItem(INVENTORY_VIEW_COSTS, R.string.perm_inventory_view_costs_title, R.string.perm_inventory_view_costs_desc),
                PermissionItem(INVENTORY_PRINT_LABELS, R.string.perm_inventory_print_labels_title, R.string.perm_inventory_print_labels_desc),
                PermissionItem(INVENTORY_DELETE, R.string.perm_inventory_delete_title, R.string.perm_inventory_delete_desc)
            )
        ),
        PermissionCategory(
            id = "dues",
            titleRes = R.string.perm_cat_dues,
            permissions = listOf(
                PermissionItem(CUSTOMERS_VIEW, R.string.perm_customers_view_title, R.string.perm_customers_view_desc),
                PermissionItem(DUES_VIEW, R.string.perm_dues_view_title, R.string.perm_dues_view_desc),
                PermissionItem(DUES_ADD_DUE, R.string.perm_dues_add_due_title, R.string.perm_dues_add_due_desc),
                PermissionItem(DUES_COLLECT_PAYMENT, R.string.perm_dues_collect_payment_title, R.string.perm_dues_collect_payment_desc),
                PermissionItem(DUES_OVERRIDE_LIMIT, R.string.perm_dues_override_limit_title, R.string.perm_dues_override_limit_desc),
                PermissionItem(DUES_SEND_REMINDERS, R.string.perm_dues_send_reminders_title, R.string.perm_dues_send_reminders_desc)
            )
        ),
        PermissionCategory(
            id = "reports",
            titleRes = R.string.perm_cat_reports,
            permissions = listOf(
                PermissionItem(REPORTS_VIEW_DAILY, R.string.perm_reports_view_daily_title, R.string.perm_reports_view_daily_desc),
                PermissionItem(REPORTS_VIEW_PNL, R.string.perm_reports_view_pnl_title, R.string.perm_reports_view_pnl_desc),
                PermissionItem(REPORTS_CLOSE_REGISTER, R.string.perm_reports_close_register_title, R.string.perm_reports_close_register_desc),
                PermissionItem(REPORTS_VIEW_Z_REPORT, R.string.perm_reports_view_z_report_title, R.string.perm_reports_view_z_report_desc),
                PermissionItem(REPORTS_EXPORT_EXCEL, R.string.perm_reports_export_excel_title, R.string.perm_reports_export_excel_desc)
            )
        ),
        PermissionCategory(
            id = "loyalty",
            titleRes = R.string.perm_cat_loyalty,
            permissions = listOf(
                PermissionItem(LOYALTY_MANAGE, R.string.perm_loyalty_manage_title, R.string.perm_loyalty_manage_desc)
            )
        ),
        PermissionCategory(
            id = "suppliers",
            titleRes = R.string.perm_cat_suppliers,
            permissions = listOf(
                PermissionItem(SUPPLIERS_MANAGE, R.string.perm_suppliers_manage_title, R.string.perm_suppliers_manage_desc)
            )
        ),
        PermissionCategory(
            id = "expenses",
            titleRes = R.string.perm_cat_expenses,
            permissions = listOf(
                PermissionItem(EXPENSES_MANAGE, R.string.perm_expenses_manage_title, R.string.perm_expenses_manage_desc)
            )
        ),
        PermissionCategory(
            id = "system",
            titleRes = R.string.perm_cat_system,
            permissions = listOf(
                PermissionItem(SYSTEM_MANAGE_STAFF, R.string.perm_system_manage_staff_title, R.string.perm_system_manage_staff_desc),
                PermissionItem(SYSTEM_PERMISSIONS, R.string.perm_system_permissions_title, R.string.perm_system_permissions_desc),
                PermissionItem(SYSTEM_BACKUP_RESTORE, R.string.perm_system_backup_restore_title, R.string.perm_system_backup_restore_desc),
                PermissionItem(SYSTEM_SHOP_SETTINGS, R.string.perm_system_shop_settings_title, R.string.perm_system_shop_settings_desc),
                PermissionItem(SYSTEM_FACTORY_RESET, R.string.perm_system_factory_reset_title, R.string.perm_system_factory_reset_desc)
            )
        )
    )

    fun getDefaultPermissions(role: String): Set<String> {
        return when (role) {
            AppRoles.OWNER -> DEFAULT_OWNER_PERMISSIONS
            AppRoles.MANAGER -> DEFAULT_MANAGER_PERMISSIONS
            AppRoles.CASHIER -> DEFAULT_CASHIER_PERMISSIONS
            else -> DEFAULT_CASHIER_PERMISSIONS
        }
    }

    fun getDefaultPermissionsForRole(role: String): Set<String> = getDefaultPermissions(role)

    fun parsePermissionsJson(json: String?): Set<String>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            val result = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                result.add(arr.getString(i))
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    fun deserializePermissions(json: String?): Set<String>? = parsePermissionsJson(json)

    fun serializePermissions(permissions: Set<String>?): String? {
        if (permissions == null) return null
        val arr = JSONArray()
        permissions.forEach { arr.put(it) }
        return arr.toString()
    }

    fun getEffectivePermissions(user: User?): Set<String> {
        if (user == null) return emptySet()
        if (user.role == AppRoles.OWNER) return DEFAULT_OWNER_PERMISSIONS
        val custom = parsePermissionsJson(user.customPermissions)
        return custom ?: getDefaultPermissions(user.role)
    }

    fun hasPermission(user: User?, permissionKey: String): Boolean {
        if (user == null) return false
        if (!user.isActive) return false
        if (user.role == AppRoles.OWNER) return true
        val effective = getEffectivePermissions(user)
        return effective.contains(permissionKey)
    }

    fun isSupervisor(user: User?): Boolean {
        if (user == null || !user.isActive) return false
        return user.role == AppRoles.OWNER || user.role == AppRoles.MANAGER
    }
}
