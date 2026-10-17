# Techyfi POS — Remaining Fix Instructions
**Date:** 2026-09-16  
**Branch to work on:** `fix/audit-batch-5` (already exists — continue here)  
**Total items:** 29 (13 Critical · 5 Partial · 5 Missing · 6 Low)  

Read every section fully before touching any file. Fix in the order listed. After every section marked **[COMMIT]**, commit with the message shown. Do not squash commits. Do not implement anything not listed here.

---

## SECTION 1 — Critical: ViewModel & Checkout Bugs

### Fix N1 — `storeCreditApplied` never written in `completeSale()`
**File:** `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`

**Problem:** The `store_credit_applied` column was added to `SaleEntity` in the DB migration (v28→v29), but when `completeSale()` constructs the `SaleEntity` object to insert, it never reads `_storeCreditApplied.value`. Every sale saves `storeCreditApplied = 0.0` regardless of how much store credit was used.

**Fix:** Find the `SaleEntity(...)` constructor call inside `completeSale()`. Add the missing field:
```kotlin
storeCreditApplied = _storeCreditApplied.value,
```
Make sure `_storeCreditApplied` is the private backing field (not the public `StateFlow`). If the field is named differently (e.g. `storeCreditAmount`), grep for it and use the correct name.

---

### Fix N2 — `isCompletingSale` not reset on error
**File:** `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`

**Problem:** `completeSale()` sets `_isCompletingSale.value = true` at the start. If an exception is thrown during the sale (DB error, validation failure, etc.), the `catch` block logs the error but does NOT reset `_isCompletingSale.value = false`. The checkout button stays permanently disabled for the rest of the session.

**Fix:** Wrap the body of `completeSale()` in a `try/finally`:
```kotlin
_isCompletingSale.value = true
try {
    // ... existing sale logic ...
} finally {
    _isCompletingSale.value = false
}
```
The `finally` block must be the innermost scope that wraps the entire operation so it runs on both success and failure.

---

**[COMMIT]** `fix(viewmodel): populate storeCreditApplied on sale save and reset isCompletingSale in finally`

---

## SECTION 2 — Critical: Security

### Fix N3 — Lockout state cleared on screen rotation
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/LockScreen.kt`

**Problem:** `failedAttempts` and `lockedUntil` (or their equivalents in `AuthViewModel`) are held in `remember {}` composable state. Screen rotation destroys and recreates the composable, resetting both to 0 — an attacker can rotate the device to clear the lockout instantly.

**Fix:** If `failedAttempts` and `lockedUntil` live in the composable, move them to `AuthViewModel` as `MutableStateFlow` fields persisted through rotation. If they already live in `AuthViewModel`, verify they are `StateFlow` (not `remember`) and that `LockScreen` collects them via `collectAsStateWithLifecycle()`. The values must survive rotation.

Also change any `remember { mutableStateOf(0) }` / `remember { mutableStateOf(0L) }` for these two fields to be driven from the ViewModel, not local composable state.

---

### Fix N4 — `SALES_EDIT_INVOICE` not in default permission sets
**File:** `app/src/main/java/com/example/techyfipos/data/AppPermissions.kt` (or wherever role defaults are defined)

**Problem:** A new `AppPermissions.SALES_EDIT_INVOICE` permission was created and used in `SalesHistoryScreen` to gate invoice editing. However, it was never added to the default permission sets for the Manager and Cashier roles. Any existing user with a Manager or Cashier role will silently lose the ability to edit invoices after update.

**Fix:** Find where default permissions are defined for each role (look for a `val managerDefaultPermissions` / `val cashierDefaultPermissions` set or similar). Add `AppPermissions.SALES_EDIT_INVOICE` to the Manager set. Decide (with product context) whether Cashier should also have it — if previously Cashiers could edit (which the old `RETURNS_DELETE_SALE` check allowed any delete-capable cashier to do), add it to Cashier as well. Also add it to the seed/default permission grants applied on first app launch and on role reset.

---

### Fix C4 — Customers screen has no permission gate
**File:** `app/src/main/java/com/example/techyfipos/Navigation.kt`

**Problem:** Every screen in the More Hub (Products, Dues, Reports, Expenses, Suppliers, Settings, Loyalty) checks a permission before navigating. The Customers entry does not — it navigates unconditionally. Customer data (phone numbers, purchase history, dues) is equally sensitive.

**Fix:** Find the navigation action that pushes Customers onto the back stack. Add a permission check identical in structure to the adjacent screens. Use `AppPermissions.CUSTOMERS_VIEW` (or create that constant if it doesn't exist). If no `CUSTOMERS_VIEW` permission exists, create it in `AppPermissions.kt`, add it to the appropriate default role sets (Owner, Manager: yes; Cashier: configurable), and use it in the navigation gate.

---

**[COMMIT]** `fix(security): lockout rotation-proof, SALES_EDIT_INVOICE in default roles, Customers permission gate`

---

## SECTION 3 — Critical: Data Integrity

### Fix N5 — Customer dues "override delete" orphans due records
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/CustomersScreen.kt`

**Problem:** When a customer is blocked from deletion due to outstanding dues, the UI shows a blocking error dialog — correct. However, if there is an "override" or "force delete" path (a second dialog allowing the user to proceed anyway), deleting the customer does NOT cascade-delete the orphaned due records. The due records remain in the DB with a phone number that has no customer, making them invisible in the Dues screen but still occupying the table.

**Fix:** Find the override/force-delete path. Before calling `deleteCustomer(customer)`, also call `deleteDuesForPhone(customer.phone)` (or equivalent) to remove all due records associated with that customer. If no such DAO method exists, add one:
```kotlin
@Query("DELETE FROM customer_dues WHERE customerPhone = :phone")
suspend fun deleteDuesByPhone(phone: String)
```
Both deletes should happen in a single repository transaction so they are atomic.

If there is NO override path (the delete is hard-blocked), this fix is not needed — confirm by reading the dialog code.

---

### Fix C17 — Auto-barcode uniqueness not checked at generation point
**File:** `app/src/main/java/com/example/techyfipos/ui/dialogs/ProductMediaDialogs.kt` **line ~180**

**Problem:** The "Generate Barcode" action calls:
```kotlin
val newBarcode = (100000000000..999999999999).random().toString()
onBarcodeScanned(newBarcode)
```
No database lookup is performed. If this barcode already exists for another product and the insert uses `OnConflictStrategy.REPLACE`, the existing product is silently overwritten.

**Fix:** The generation must loop until a unique barcode is found. Since this is a composable context, the check must happen in a coroutine. Inject or receive a suspend lambda `checkBarcodeExists: suspend (String) -> Boolean` as a parameter, or call a ViewModel method. Replace the generation with:
```kotlin
scope.launch {
    var candidate = (100000000000..999999999999).random().toString()
    while (checkBarcodeExists(candidate)) {
        candidate = (100000000000..999999999999).random().toString()
    }
    onBarcodeScanned(candidate)
}
```
Add a corresponding repository/ViewModel method:
```kotlin
suspend fun productExistsByBarcode(barcode: String): Boolean =
    productDao.getProductByBarcode(barcode) != null
```

---

### Fix C24 — `paidRatio` missing upper clamp in return calculation
**Files:**
- `app/src/main/java/com/example/techyfipos/ui/screens/ReturnDialog.kt` **line ~149**
- `app/src/main/java/com/example/techyfipos/data/DataRepository.kt` **line ~3135**

**Problem:** The code has:
```kotlin
val paidRatio = if (subtotal > 0) (grandTotal / subtotal).coerceAtLeast(0.0) else 1.0
```
`coerceAtMost(1.0)` is absent. If `grandTotal > subtotal` (possible with exclusive tax configurations), `paidRatio` exceeds 1.0 and per-item refund amounts are inflated beyond the original item price.

**Fix:** Add `.coerceAtMost(1.0)` to both locations:
```kotlin
val paidRatio = if (subtotal > 0) (grandTotal / subtotal).coerceIn(0.0, 1.0) else 1.0
```
Apply the identical fix in both `ReturnDialog.kt` and `DataRepository.kt`.

---

### Fix D10 — No DB index on `sales.timestamp`
**File:** `app/src/main/java/com/example/techyfipos/data/db/AppEntities.kt`

**Problem:** All date-range queries on the `sales` table filter by `timestamp` (the column name in the entity). There is no index on this column. On large datasets this causes full table scans.

**Fix:** Add an index to `SaleEntity`:
```kotlin
@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["timestamp"]),       // add this
        Index(value = ["customer_phone"])   // keep existing if present
    ]
)
```
This requires a DB migration. The current schema is version 29. Add migration 29→30:
```kotlin
val MIGRATION_29_30 = Migration(29, 30) {
    it.execSQL("CREATE INDEX IF NOT EXISTS index_sales_timestamp ON sales (timestamp)")
}
```
Update `APP_DATABASE_VERSION` to `30`. Register the migration in `addMigrations(...)`. Update the schema JSON by running `./gradlew generateRoomSchemas` or letting the build generate `30.json`.

---

**[COMMIT]** `fix(data): N5 cascade dues on customer override-delete, C17 barcode uniqueness loop, C24 paidRatio clamp, D10 index on sales.timestamp with migration 29→30`

---

## SECTION 4 — Critical: Remaining Unfixed Issues

### Fix C23 — `onAddNote` / `onAdjustCredit` not wired from LoyaltyScreen
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/LoyaltyScreen.kt`

**Problem:** `CustomerDossierSheet` accepts `onAddNote: (customerId, noteText) -> Unit` and `onAdjustCredit: (customerId, amount) -> Unit` parameters, both defaulting to no-ops. When the dossier is opened from `LoyaltyScreen`, neither callback is passed — real implementations remain no-ops. Notes typed in the Notes tab are silently discarded. The Credit Adjust action does nothing.

**Fix:** In `LoyaltyScreen.kt`, find the call site where `CustomerDossierSheet(...)` is invoked. Pass real implementations:
```kotlin
CustomerDossierSheet(
    // ... existing params ...
    onAddNote = { customerId, text ->
        loyaltyViewModel.addCustomerNote(customerId, text) // or the appropriate ViewModel call
    },
    onAdjustCredit = { customerId, amount ->
        loyaltyViewModel.adjustStoreCredit(customerId, amount)
    }
)
```
If the ViewModel does not have these methods, add them, delegating to the appropriate repository calls.

---

### Fix G14 — Barcode lookup is case-sensitive
**Files:**
- `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt` — `getProductByBarcode()` method (~line 1551)
- `app/src/main/java/com/example/techyfipos/data/DataRepository.kt` — `getProductByBarcode()` method (~line 318)

**Problem:** The barcode passed to the lookup is only processed with `toEnglishDigits(barcode.trim())` — no lowercase normalisation. A QR code scan returning "ABC123" will not match a stored barcode "abc123".

**Fix:** Apply `.lowercase()` to the barcode at the repository level (single point of truth):
```kotlin
suspend fun getProductByBarcode(barcode: String) =
    productDao.getProductByBarcode(barcode.trim().toEnglishDigits().lowercase())
```
Also ensure the DAO query uses `LOWER()` or that barcodes are stored normalised. The safest approach: normalise to lowercase on both sides:
```kotlin
@Query("SELECT * FROM products WHERE LOWER(barcode) = LOWER(:barcode) LIMIT 1")
suspend fun getProductByBarcode(barcode: String): Product?
```
Normalise on save too: when a product is saved with a manually typed barcode, apply `.lowercase()` before storing it.

---

### Fix U25 — App version shows static "Pro" badge
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/SettingsScreen.kt`

**Problem:** The About section shows a static string resource `settings_version_pro_pill` ("Pro") instead of the actual app version. Users and support cannot identify the build.

**Fix:** Find the About/version row in `SettingsScreen`. Replace the static string with:
```kotlin
Text(
    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    style = MaterialTheme.typography.bodyMedium
)
```
Keep any "Pro" badge if it is a separate UI element — just add the version number alongside it. The import `import com.example.techyfipos.BuildConfig` may be needed.

---

### Fix G22 — No deep-link handling exists at all
**File:** `app/src/main/java/com/example/techyfipos/Navigation.kt`  
**File:** `app/src/main/AndroidManifest.xml`

**Problem:** The app has no deep-link infrastructure. Notification taps that carry an intent extra (e.g. from due reminders) cannot navigate to the correct screen.

**Fix (minimal viable):** In `MainActivity`, read the intent on launch and translate it to a navigation destination. Add to `MainActivity.onCreate()` or via a `LaunchedEffect` in the root composable:
```kotlin
val destination = intent?.getStringExtra("navigate_to")
if (destination != null) {
    // push destination onto the back stack after the app initialises
}
```
Support at minimum these destinations used by the WorkManager workers:
- `"dues"` → DuesScreen
- `"customers"` → CustomersScreen  
- `"sales_history"` → SalesHistoryScreen
- `"products"` → ProductsScreen

This does not require `AndroidManifest.xml` deep-link intent filters unless external apps need to open the POS — for internal notification routing, the intent extra approach is sufficient.

---

**[COMMIT]** `fix(ux): wire LoyaltyScreen dossier callbacks, case-insensitive barcode lookup, version in Settings, intent-based deep-link routing`

---

## SECTION 5 — Partial Fixes: Complete Them

### Fix U9 — Date period not cleared by "Clear filters"
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/SalesHistoryScreen.kt`

**Problem:** The "Clear search and filters" button resets search query, payment filters, customer filter, and amount range — but NOT `selectedPeriod` or `customStart`/`customEnd`. A user who applied a custom date range has no single-tap way to reset it.

**Fix:** Find the clear-filters action lambda. Add:
```kotlin
selectedPeriod = SalesPeriod.TODAY  // or whatever the default is
customStart = null
customEnd = null
```
alongside the existing resets.

---

### Fix D32/D33 — Supplier ledger missing timestamps and "View All"
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/SuppliersScreen.kt`

**Problem:**
- D33: Payment and invoice rows in the supplier ledger show no date/time.
- D32: The ledger is capped at `.take(20)` with no affordance to see older records.

**Fix for D33:** In the ledger row composable, display the `createdAt` / `date` / `timestamp` field of each payment or invoice record. Format it with your existing date formatter (e.g. `formatDate(payment.timestamp)`). Place it as a secondary line or trailing text on each row.

**Fix for D32:** Replace `.take(20)` with a paginated approach or remove the cap. The simplest fix: remove `.take(20)` entirely (the `LazyColumn` inside the bottom sheet already virtualises rows). Alternatively, keep `.take(20)` and add a "View all X transactions →" `TextButton` below the list that expands to show all.

---

### Fix C20/E7 — Product delete fires without in-dialog confirmation
**File:** `app/src/main/java/com/example/techyfipos/ui/dialogs/AddEditProductDialog.kt`

**Problem:** The delete `IconButton` calls `onDelete` directly without an `AlertDialog` confirmation step inside the dialog itself.

**Fix:** Add a local boolean state variable `showDeleteConfirm`. Wire the delete button to set it to `true`. Show an `AlertDialog` when `showDeleteConfirm == true`:
```kotlin
var showDeleteConfirm by remember { mutableStateOf(false) }

if (showDeleteConfirm) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = { Text(stringResource(R.string.dialog_delete_product_title)) },
        text = { Text(stringResource(R.string.dialog_delete_product_body)) },
        confirmButton = {
            TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteConfirm = false }) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
```
Add the string resources to both `values/strings.xml` and `values-bn/strings.xml`.

---

### Fix C15/D21 — Held-cart restore path ignores stock decrement result
**File:** `app/src/main/java/com/example/techyfipos/data/DataRepository.kt`

**Problem:** The main sale flow correctly checks the return value of `decrementStockIfAvailable`. The held-cart restore path (where a previously held cart is converted to a sale) calls `adjustStockQty` or a similar function without checking whether the decrement succeeded.

**Fix:** Find the held-cart restore → sale conversion path in `DataRepository`. Apply the same pattern as the main sale flow:
```kotlin
val rows = productDao.decrementStockIfAvailable(barcode, qty)
if (rows == 0 && !allowNegativeInventory) {
    throw InsufficientStockException("Insufficient stock for $productName")
}
```
Surface the `InsufficientStockException` to the UI so the cashier sees a meaningful error rather than a silent failure.

---

**[COMMIT]** `fix(ux): clear date period in filter reset, supplier ledger timestamps and view-all, product delete confirmation, held-cart stock check`

---

## SECTION 6 — Never Fixed Items

### Fix D31 — "Rocket" missing from supplier pay-due payment list
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/SuppliersScreen.kt` **line ~1095**

**Problem:** The pay-due dialog offers `listOf("Cash", "bKash", "Nagad", "Bank")`. "Rocket" is present in `ReturnDialog` but missing here.

**Fix:** Change to:
```kotlin
listOf("Cash", "bKash", "Nagad", "Rocket", "Bank")
```

---

### Fix G8 — Category filter flashes old results on clear
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/ProductsScreen.kt`

**Problem:** When `selectedCategories` is cleared, the derived product list recomputes synchronously but there is a brief visual flash of the old filtered state before the new empty-filter results render.

**Fix:** Wrap the product grid in `AnimatedContent` keyed on the filter state, or use `Crossfade`. The simplest fix is to add a `key(selectedCategories)` to the `LazyVerticalGrid` so Compose fully recomposes it on filter change rather than diffing the old list:
```kotlin
key(selectedCategories.hashCode()) {
    LazyVerticalGrid(...) { ... }
}
```

---

### Fix G11 — Per-item discount shows no live line-total preview in dialog
**File:** `app/src/main/java/com/example/techyfipos/ui/dialogs/CartItemDialogs.kt`

**Problem:** When a cashier edits a per-item discount in the cart item edit dialog, the displayed line total does not update live to show the discounted price. The user cannot verify the result before confirming.

**Fix:** The cart item edit dialog (wherever it lives — may need to be created if it doesn't exist yet) should derive and display:
```kotlin
val discountedPrice = if (isPercentage) {
    originalPrice * (1.0 - discountValue / 100.0)
} else {
    originalPrice - discountValue
}.coerceAtLeast(0.0)
val lineTotal = discountedPrice * quantity
Text("Line total: ${formatTaka(lineTotal)}")
```
Update this derived value inside the dialog composable so it recomputes every time `discountValue` or `quantity` changes.

---

### Fix D22 — `Product` entity has no `createdAt` timestamp
**File:** `app/src/main/java/com/example/techyfipos/data/db/AppEntities.kt`

**Problem:** Products have no creation timestamp. "Recently added" sorting and new-arrivals reports are impossible.

**Fix:** Add the field to `Product`:
```kotlin
@ColumnInfo(name = "created_at")
val createdAt: Long = System.currentTimeMillis(),
```
Add a migration (append to whichever is the current migration — 29→30 or 30→31 depending on D10):
```kotlin
it.execSQL("ALTER TABLE products ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
```
DEFAULT 0 is acceptable for existing products (they'll sort before newly added ones). Bump `APP_DATABASE_VERSION` and register the migration. When creating a new product, pass `createdAt = System.currentTimeMillis()`.

---

### Fix P2 — Excel export loads all records into memory (OOM risk)
**File:** `app/src/main/java/com/example/techyfipos/utils/ExcelHelper.kt`

**Problem:** `ExcelHelper` uses `XSSFWorkbook` (Apache POI in-memory). The ViewModel passes a fully materialised `List<SaleWithItems>` to it. On devices with thousands of records this can cause OOM.

**Fix:** Switch to `SXSSFWorkbook` (Apache POI's streaming variant):
```kotlin
import org.apache.poi.xssf.streaming.SXSSFWorkbook
val workbook = SXSSFWorkbook(100) // keep 100 rows in memory, flush the rest
```
Also change the ViewModel to pass records in pages using a `Flow<PagingData<...>>` or a chunked DAO query rather than loading all at once. If full migration to `SXSSFWorkbook` is complex, at minimum chunk the DAO query:
```kotlin
// In DAO
@Query("SELECT * FROM sales ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
suspend fun getSalesPaged(limit: Int, offset: Int): List<Sale>
```
And write rows in batches of 500 inside `ExcelHelper`.

---

**[COMMIT]** `fix(ux+data): Rocket payment in supplier dues, G8 category filter flash, G11 live discount preview, D22 product createdAt, P2 streaming Excel export`

---

## SECTION 7 — Low Priority

### Fix N6 — Inconsistent setter method names in ViewModel
**File:** `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`

Rename all ViewModel setter methods to follow a single convention: `fun setFieldName(value: T)`. Replace any `updateFieldName` or `applyFieldName` variants with `setFieldName`. Update all call sites in CartScreen and other UI files.

---

### Fix N7 — `fallbackToDestructiveMigration()` still present
**File:** `app/src/main/java/com/example/techyfipos/data/db/AppDatabase.kt`

Remove the `fallbackToDestructiveMigration()` call. If a migration path is missing, it is better to crash with a clear error than to silently wipe user data. Add `.fallbackToDestructiveMigrationFrom()` only for very old versions (e.g. 1–5) that pre-date any migration and would realistically never be encountered.

---

### Fix N8 — Lockout countdown not cancelled on login success
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/LockScreen.kt`

The `LaunchedEffect` that ticks down `lockedUntil` runs as long as `lockedUntil > 0`. On successful login the screen navigates away, which cancels the effect automatically. This is likely already handled by composition lifecycle. Verify — if the navigation leaves the composable in place momentarily, add a `successfulLogin` flag to short-circuit the countdown early.

---

### Fix N9 — Starting float input accepts bare `"."`
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/RegisterCloseScreen.kt` **EditStartingFloatDialog ~line 2207**

The filter `{ it.isDigit() || it == '.' }` permits `"."` as the entire input, which `toDoubleOrNull()` parses as `null`, falling back silently to `0.0`. Add a guard: if `floatText == "."`, treat it as `"0."` or show an inline error until a digit is typed.

---

### Fix X1 — Duplicate import in CartScreen
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/CartScreen.kt`

Remove the duplicate `import androidx.lifecycle.compose.collectAsStateWithLifecycle` (one of lines 34 / 84).

---

**[COMMIT]** `fix(polish): consistent ViewModel setter names, remove fallbackToDestructiveMigration, cleanup minor issues`

---

## Final Checklist Before Merging

- [ ] App compiles with no errors (`./gradlew assembleDebug`)
- [ ] Room schema JSON for the final version is generated and committed
- [ ] `APP_DATABASE_VERSION` matches the highest migration number
- [ ] All migrations are registered in `addMigrations(...)`
- [ ] No hardcoded `0.0` default for `storeCreditApplied` in any sale constructor call
- [ ] `SALES_EDIT_INVOICE` permission exists in `AppPermissions.kt` and in default Manager/Cashier sets
- [ ] `CUSTOMERS_VIEW` permission exists and is gated in `Navigation.kt`
- [ ] `BuildConfig.VERSION_NAME` displays correctly in Settings About section
- [ ] `paidRatio` uses `.coerceIn(0.0, 1.0)` in both `ReturnDialog.kt` and `DataRepository.kt`
- [ ] Barcode lookup uses `LOWER()` or `.lowercase()` consistently in DAO and on save
- [ ] `ProductMediaDialogs.kt` barcode generation loops until unique

---

*End of fix instructions. 29 items across 7 sections, 6 commits.*
