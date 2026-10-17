# Techyfi POS — Fix Instructions Round 2
**Date:** 2026-09-16  
**Branch to work on:** `fix/audit-batch-5` (continue on same branch)  
**Total items:** 12 (1 Critical · 3 High · 2 Medium · 6 Low)  

Read every section fully before touching any file. Fix in the order listed. After every section marked **[COMMIT]**, commit with the message shown. Do not squash commits. Do not implement anything not listed here.

---

## SECTION 1 — Critical: Backup Data Loss

### Fix A6 — store_credit_vouchers and store_credit_ledger missing from JSON backup
**File:** `app/src/main/java/com/example/techyfipos/data/DataRepository.kt`

**Problem:** `exportBackup()` serialises 28+ table groups but completely omits `store_credit_vouchers` and `store_credit_ledger`. When a user restores from a JSON backup (Settings → Restore, or `AutoBackupWorker`), both tables are wiped and never repopulated. All SC-XXXX-XXXX voucher codes, remaining balances, statuses, and the full store credit transaction ledger are permanently destroyed.

**Note:** The Google Drive path (`BackupPackageManager.createEncryptedBackupPackage()`) packages a raw SQLite snapshot and is NOT affected. Only the JSON path (`exportBackup()` / `importBackup()`) is broken.

**Fix — export side:** In `exportBackup()`, after the last existing table serialisation block, add:
```kotlin
// Store credit vouchers
val vouchers = storeCreditVoucherDao.getAllVouchers()
backupJson.put("storeCreditVouchers", JSONArray().apply {
    vouchers.forEach { v ->
        put(JSONObject().apply {
            put("code", v.code)
            put("customerPhone", v.customerPhone)
            put("originalAmount", v.originalAmount)
            put("remainingBalance", v.remainingBalance)
            put("status", v.status)
            put("issuedAt", v.issuedAt)
            put("expiresAt", v.expiresAt ?: JSONObject.NULL)
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
            put("customerPhone", e.customerPhone)
            put("type", e.type)
            put("amount", e.amount)
            put("balanceAfter", e.balanceAfter)
            put("voucherCode", e.voucherCode ?: JSONObject.NULL)
            put("saleId", e.saleId ?: JSONObject.NULL)
            put("notes", e.notes ?: JSONObject.NULL)
            put("createdAt", e.createdAt)
        })
    }
})
```
Adjust field names to match the actual entity properties.

**Fix — import side:** In `importBackup()`, after the last existing import block, add:
```kotlin
// Restore store credit vouchers
backupJson.optJSONArray("storeCreditVouchers")?.let { arr ->
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        storeCreditVoucherDao.upsertVoucher(StoreCreditVoucher(
            code = obj.getString("code"),
            customerPhone = obj.getString("customerPhone"),
            originalAmount = obj.getDouble("originalAmount"),
            remainingBalance = obj.getDouble("remainingBalance"),
            status = obj.getString("status"),
            issuedAt = obj.getLong("issuedAt"),
            expiresAt = if (obj.isNull("expiresAt")) null else obj.getLong("expiresAt"),
            notes = if (obj.isNull("notes")) null else obj.getString("notes")
        ))
    }
}

// Restore store credit ledger
backupJson.optJSONArray("storeCreditLedger")?.let { arr ->
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        storeCreditLedgerDao.insert(StoreCreditLedgerEntry(
            id = obj.getLong("id"),
            customerPhone = obj.getString("customerPhone"),
            type = obj.getString("type"),
            amount = obj.getDouble("amount"),
            balanceAfter = obj.getDouble("balanceAfter"),
            voucherCode = if (obj.isNull("voucherCode")) null else obj.getString("voucherCode"),
            saleId = if (obj.isNull("saleId")) null else obj.getLong("saleId"),
            notes = if (obj.isNull("notes")) null else obj.getString("notes"),
            createdAt = obj.getLong("createdAt")
        ))
    }
}
```
Use `OnConflictStrategy.REPLACE` on the upsert/insert if not already present. Adjust class names and field names to match your actual entities.

---

**[COMMIT]** `fix(backup): include store_credit_vouchers and store_credit_ledger in JSON backup export/import`

---

## SECTION 2 — High: Data Integrity

### Fix A10 — Plain "Store Credit" payment path does not update voucher balance
**File:** `app/src/main/java/com/example/techyfipos/data/DataRepository.kt` around line 786

**Problem:** When `paymentMethod == "Store Credit"` (the non-split code path inside `completeSaleWithNotes()`), the code correctly:
- Deducts from `customers.storeCredit` via `adjustCustomerStoreCredit()`
- Writes a `StoreCreditLedgerEntry` of type `"SALE_REDEMPTION"`

But it does NOT update `store_credit_vouchers.remainingBalance`. The split payment loop (inside the split path) correctly calls `storeCreditVoucherDao.updateVoucherBalance(code, newBal, newStatus)` for each SC entry. The plain path skips this entirely, leaving the voucher showing its full original balance forever even though the customer's aggregate balance is correctly reduced.

**Fix:** In the plain "Store Credit" payment block, after the `adjustCustomerStoreCredit()` call, resolve and update the active voucher for this customer. If a `voucherCode` is already passed into the function or stored on the payment object:
```kotlin
// After adjustCustomerStoreCredit(phone, -amountUsed, ...)
val activeVoucher = storeCreditVoucherDao.getActiveVoucherForPhone(customerPhone)
if (activeVoucher != null) {
    val newBalance = (activeVoucher.remainingBalance - amountUsed).coerceAtLeast(0.0)
    val newStatus = if (newBalance < 0.01) "REDEEMED" else "ACTIVE"
    storeCreditVoucherDao.updateVoucherBalance(activeVoucher.code, newBalance, newStatus)
}
```
If the DAO does not have `getActiveVoucherForPhone()`, add:
```kotlin
@Query("SELECT * FROM store_credit_vouchers WHERE customerPhone = :phone AND status = 'ACTIVE' LIMIT 1")
suspend fun getActiveVoucherForPhone(phone: String): StoreCreditVoucher?
```

---

### Fix A5 — `referenceNote` dropped when saving split payments to the sale record
**File:** `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt` around line 639

**Problem:** `getSplitPaymentsJson()` builds a JSONObject per split-payment entry with `method`, `amount`, `walletPhone`, `cardType`, `cardLastFour` — but never writes `referenceNote`. A cashier who enters a bKash or Nagad transaction ID sees it captured in the UI and in held-cart saves, but after the sale is completed the reference note is gone from the DB permanently. Support cannot look up the external transaction ID.

**Fix:** Inside the per-entry JSONObject builder in `getSplitPaymentsJson()`, add one line:
```kotlin
if (!sp.referenceNote.isNullOrBlank()) put("referenceNote", sp.referenceNote)
```
Place it alongside the other conditional fields (`walletPhone`, `cardType`, `cardLastFour`).

---

**[COMMIT]** `fix(data): update voucher balance on plain Store Credit sale, persist referenceNote in split payment JSON`

---

## SECTION 3 — Medium: Permissions & Backup

### Fix A8 — Wrong permission guards customer edit, archive, and delete
**File:** `app/src/main/java/com/example/techyfipos/data/DataRepository.kt` lines ~1187, ~1205, ~1218  
**File:** `app/src/main/java/com/example/techyfipos/data/AppPermissions.kt`

**Problem:** `upsertCustomer()`, `archiveCustomer()`, and `deleteCustomerPermanently()` all call `requirePermission(AppPermissions.DUES_ADD_DUE)`. This is semantically wrong:
- A cashier with `CUSTOMERS_VIEW` but without `DUES_ADD_DUE` cannot update a customer's name, address, tag, or notes — even operations that have nothing to do with dues.
- Anyone with `DUES_ADD_DUE` (due collection cashier) can edit or delete customers even if they have no customer management role.

**Fix step 1:** Add a new constant to `AppPermissions.kt`:
```kotlin
const val CUSTOMERS_EDIT = "customers.edit"
```
Add `CUSTOMERS_EDIT` to `DEFAULT_MANAGER_PERMISSIONS`. Decide whether Cashier should have it (safe default: no, matching the existing locked-down cashier role).

**Fix step 2:** In `DataRepository.kt`, replace the three calls:
```kotlin
// Before (wrong):
requirePermission(AppPermissions.DUES_ADD_DUE)

// After (correct):
requirePermission(AppPermissions.CUSTOMERS_EDIT)
```
Apply to all three functions: `upsertCustomer()`, `archiveCustomer()`, `deleteCustomerPermanently()`.

**Fix step 3:** Add the seed grant for `CUSTOMERS_EDIT` wherever first-launch default permissions are inserted into the DB (look for the block that inserts all DEFAULT_MANAGER_PERMISSIONS as `UserPermission` rows).

---

### Fix A7 — Audit logs missing from JSON backup
**File:** `app/src/main/java/com/example/techyfipos/data/DataRepository.kt`

**Problem:** `sale_audit_logs` and `expense_audit_logs` are not included in `exportBackup()`. After a JSON restore, the complete audit trail of every deleted/voided sale and every deleted or edited expense is permanently gone.

**Fix — export:** After the existing sale and expense blocks in `exportBackup()`, add:
```kotlin
// Sale audit logs
val saleAuditLogs = saleAuditLogDao.getAll()
backupJson.put("saleAuditLogs", JSONArray().apply {
    saleAuditLogs.forEach { log ->
        put(JSONObject().apply {
            put("id", log.id)
            put("saleId", log.saleId)
            put("action", log.action)
            put("performedBy", log.performedBy)
            put("reason", log.reason ?: JSONObject.NULL)
            put("timestamp", log.timestamp)
            put("snapshotJson", log.snapshotJson ?: JSONObject.NULL)
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
            put("performedBy", log.performedBy)
            put("reason", log.reason ?: JSONObject.NULL)
            put("timestamp", log.timestamp)
        })
    }
})
```

**Fix — import:** Add corresponding restore blocks in `importBackup()` using the same pattern as A6. Use `OnConflictStrategy.IGNORE` on insert to avoid overwriting existing audit records.

---

**[COMMIT]** `fix(backup+rbac): add audit logs to JSON backup, fix CUSTOMERS_EDIT permission guard`

---

## SECTION 4 — High (Partial): Complete P2 Excel Streaming

### Fix P2 (completion) — Remaining 6 export functions still use XSSFWorkbook
**File:** `app/src/main/java/com/example/techyfipos/utils/ExcelHelper.kt`

**Problem:** Only `exportSales` was migrated to `SXSSFWorkbook(100)`. The following six functions still use `XSSFWorkbook` and will OOM on large datasets:
- `exportProducts` (line ~230)
- `exportComprehensiveDues` (line ~571)
- `exportCustomerStatementExcel` (line ~913)
- `exportCustomers` (line ~1083)
- `exportExpenses` (line ~1170)
- `exportSalesReport` (line ~1243)

**Fix:** For each of the six functions, replace the workbook instantiation:
```kotlin
// Before:
val workbook = XSSFWorkbook()

// After:
val workbook = SXSSFWorkbook(100) // flush to disk after 100 rows in memory
```
Also add `workbook.dispose()` in the `finally` block of each function (after `workbook.write(outputStream)`), to release the temp files `SXSSFWorkbook` writes to disk:
```kotlin
finally {
    workbook.dispose() // required for SXSSFWorkbook to clean up temp files
    outputStream.close()
}
```
The import `import org.apache.poi.xssf.streaming.SXSSFWorkbook` should already be present from the `exportSales` migration; verify it's at the top of the file.

---

**[COMMIT]** `fix(perf): migrate remaining 6 Excel export functions to SXSSFWorkbook streaming`

---

## SECTION 5 — Low Priority

### Fix A9 — Redundant fallbackToDestructiveMigrationFrom(1–5) in AppDatabase
**File:** `app/src/main/java/com/example/techyfipos/data/db/AppDatabase.kt` line ~363

**Problem:** `.fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)` is still present. All migration paths from v1–v5 already exist in the migration chain, so this call is a dead leftover. If a migration from v1–v5 ever failed, it would silently wipe all user data instead of surfacing the failure.

**Fix:** Remove the single line:
```kotlin
.fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
```
No other change needed.

---

### Fix N6 (completion) — Remove deprecated ViewModel setter wrappers
**File:** `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`

**Problem:** The old setter names (`applyStoreCredit`, `applyLoyaltyDiscount`, `updateItemNote`, `updateQuantity`, `updateStartingFloat`) were kept as `@Deprecated` wrappers delegating to the new `setX` names. They should be removed to complete the refactor.

**Fix:** Delete the `@Deprecated` wrapper functions. Grep for any remaining call sites:
```
grep -rn "applyStoreCredit\|applyLoyaltyDiscount\|updateItemNote\|updateQuantity\|updateStartingFloat" app/src/main/java/
```
If any call site still uses the old names, update it to the new `setX` name before deleting the wrapper.

Note: Do NOT rename `updateProduct()` or `updateProductWithAdjustment()` — these are DB operations (update-in-database), not simple state setters, and their `update` prefix is semantically correct.

---

### Fix A1 — Hardcoded "X Vendors" badge in SuppliersScreen
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/SuppliersScreen.kt` line ~141

**Problem:** `"${suppliers.size} Vendors"` is a hardcoded English string. The plural resource `R.plurals.supplier_active_vendors_count` already exists and is used later in this file (line ~242).

**Fix:** Replace:
```kotlin
"${suppliers.size} Vendors"
```
With:
```kotlin
pluralStringResource(R.plurals.supplier_active_vendors_count, suppliers.size, suppliers.size)
```

---

### Fix A2 — Hardcoded "X SKUs" badge in ProductsScreen
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/ProductsScreen.kt` line ~696

**Problem:** `"${allProductsList.size} SKUs"` is hardcoded English.

**Fix:** Add to `values/strings.xml`:
```xml
<plurals name="products_sku_count">
    <item quantity="one">%d SKU</item>
    <item quantity="other">%d SKUs</item>
</plurals>
```
Add to `values-bn/strings.xml`:
```xml
<plurals name="products_sku_count">
    <item quantity="one">%d স্কু</item>
    <item quantity="other">%d স্কু</item>
</plurals>
```
Replace the hardcoded string with:
```kotlin
pluralStringResource(R.plurals.products_sku_count, allProductsList.size, allProductsList.size)
```

---

### Fix A3 — Hardcoded "Live Report" badge in SalesReportScreen
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/SalesReportScreen.kt` line ~221

**Problem:** `"Live Report"` is hardcoded English.

**Fix:** Add to `values/strings.xml`:
```xml
<string name="sales_report_live_badge">Live Report</string>
```
Add to `values-bn/strings.xml`:
```xml
<string name="sales_report_live_badge">লাইভ রিপোর্ট</string>
```
Replace with `stringResource(R.string.sales_report_live_badge)`.

---

### Fix A4 — Hardcoded "Z-Report" badge in RegisterCloseScreen
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/RegisterCloseScreen.kt` line ~273

**Problem:** `"Z-Report"` is hardcoded English.

**Fix:** Add to `values/strings.xml`:
```xml
<string name="register_close_z_report_badge">Z-Report</string>
```
Add to `values-bn/strings.xml`:
```xml
<string name="register_close_z_report_badge">জেড-রিপোর্ট</string>
```
Replace with `stringResource(R.string.register_close_z_report_badge)`.

---

**[COMMIT]** `fix(polish): remove redundant fallback migration, remove deprecated ViewModel wrappers, localise hardcoded badge strings`

---

## Final Checklist Before Merging

- [ ] App compiles with no errors (`./gradlew assembleDebug`)
- [ ] `exportBackup()` JSON output contains `storeCreditVouchers` and `storeCreditLedger` arrays
- [ ] `importBackup()` restores both voucher tables without throwing on a backup that lacks those keys (`optJSONArray` not `getJSONArray`)
- [ ] `importBackup()` restores `saleAuditLogs` and `expenseAuditLogs`
- [ ] Plain "Store Credit" sale path calls `storeCreditVoucherDao.updateVoucherBalance()` after deducting
- [ ] `getSplitPaymentsJson()` includes `referenceNote` in the JSON output
- [ ] `AppPermissions.CUSTOMERS_EDIT` constant exists and is in `DEFAULT_MANAGER_PERMISSIONS`
- [ ] All three DataRepository customer functions use `requirePermission(CUSTOMERS_EDIT)`
- [ ] All 6 remaining Excel export functions use `SXSSFWorkbook(100)` with `workbook.dispose()` in finally
- [ ] `fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)` removed from AppDatabase.kt
- [ ] `@Deprecated` setter wrappers removed; no remaining call sites using old names
- [ ] A1–A4 hardcoded strings replaced with `stringResource`/`pluralStringResource`
- [ ] Bengali translations added for A2, A3, A4 new string keys

---

*End of fix instructions. 12 items across 5 sections, 4 commits.*
