# Techyfi POS — Full Audit Fix List
Generated: 2026-09-16 | Branch: fix/audit-batch-5
55 issues — ordered Critical → Minor within each area.

---

## HOW TO USE THIS FILE
Work through each issue in order. Mark each one `[x]` when done.
Every issue has: the exact file path (relative to `app/src/main/java/com/example/techyfipos/`), the relevant line range, what is wrong, and what to do.

---

## CART & CHECKOUT

### CRITICAL

- [x] **C-1** `ui/viewmodel/POSViewModels.kt:797–799, 984–985`
  **Loyalty discount capped in wrong unit (points vs ৳)**
  In both `restoreHeldCart` and `restoreCartSnapshot`, `loyaltyDiscount` is capped by `pointsBalance` (an integer point count) instead of `pointsBalance × pointCashValue`.
  FIX: Replace `coerceAtMost(pointsBalance.toDouble())` with `coerceAtMost(pointsBalance * pointCashValue)`. Read `pointCashValue` from `LoyaltySettingsHelper` (it is already available on the ViewModel via the loyalty settings flow).

- [x] **C-2** `ui/screens/CartScreen.kt:1456–1461`
  **Store Credit payment method hidden when voucher redeemed but storeCredit is 0**
  `availablePaymentMethods` is keyed on `resolvedCustomer?.storeCredit`. A customer with a redeemed voucher but zero stored credit never sees the "Store Credit" chip.
  FIX: Change the `remember` key to `resolvedCustomer?.storeCredit to (redeemedVoucher != null)` so the chip appears whenever either a stored balance or a redeemed voucher is present.

### HIGH

- [x] **H-1** `ui/viewmodel/POSViewModels.kt:1129–1149`
  **`getGrandTotal()` omits loyaltyDiscount — scan snackbar shows inflated total**
  `getGrandTotal()` re-implements the grand-total formula but forgets to subtract `loyaltyDiscount`.
  FIX: Delete `getGrandTotal()` entirely and change all callers (e.g. `ScannerViewModel.onBarcodeScanned`) to read `cartViewModel.grandTotal.value` instead.

- [x] **H-2** `ui/screens/CartScreen.kt:2319`
  **`isStoreCreditValid` ignores voucher balance**
  The check only reads `resolvedCustomer.storeCredit`, missing `redeemedVoucher?.remainingBalance`.
  FIX: Change the condition to: `(resolvedCustomer?.storeCredit ?: 0.0) + (redeemedVoucher?.remainingBalance ?: 0.0) >= grandTotal`.

- [x] **H-3** `ui/screens/CartScreen.kt:1975`
  **Cash-received field uses `KeyboardType.Number` — blocks decimal entry**
  FIX: Change `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)` to `KeyboardType.Decimal`.

- [x] **H-4** `ui/screens/CartScreen.kt:2063–2069`
  **Hardcoded Bengali strings in the Due payment notice**
  Two Bengali literals are the only strings in the checkout flow that bypass `stringResource`.
  FIX: Move both strings to `res/values/strings.xml` (and a `res/values-bn/strings.xml` variant), then replace the raw literals with `stringResource(R.string.xxx)`.

- [x] **H-5** `ui/viewmodel/POSViewModels.kt:1266–1268` and around `completeSale()`
  **Zero-amount split leg saved when store credit exactly covers the bill**
  When `storeCreditApplied == grandTotal` and payment method is "Cash", a split entry with `amount = 0.0` is stored.
  FIX: Before inserting each split leg, skip any entry whose amount is `<= 0.0`.

### MEDIUM

- [x] **M-1** `ui/screens/CartScreen.kt:567–573`
  **No void-cart confirmation for owners and supervisors**
  Cashiers get supervisor auth; owners/supervisors get nothing — one tap destroys the cart.
  FIX: Wrap the `clearCart()` call for `canVoidCart == true` in a simple confirmation dialog ("Clear entire cart?" with Cancel/Confirm).

- [x] **M-2** `ui/screens/CartScreen.kt:1178–1184`
  **Split auto-init breaks when grandTotal < ৳2**
  `coerceAtLeast(1.0)` on the first leg can force the sum above grandTotal, immediately invalidating the checkout button.
  FIX: Only apply `coerceAtLeast` when `grandTotal >= 2.0`; for smaller totals, set the first leg to `grandTotal` and the second to `0.0`.

- [x] **M-3** `ui/viewmodel/POSViewModels.kt:1039` — `addCustomItem()`
  **Custom items missing `mrpPrice` and `discountedPrice`**
  These fields stay at `0.0`, so MRP-mode receipts show ৳0 in the MRP column for custom items.
  FIX: Set `mrpPrice = cleanPrice` and `discountedPrice = cleanPrice` when constructing the custom cart item (same value as `sellingPrice`/`wholesalePrice` unless the caller provides overrides).

- [x] **M-4** `ui/screens/CartScreen.kt:1367–1393`
  **Voucher redeem dialog shows no loading indicator**
  `isCheckingVoucher` disables the button but shows no spinner — the UI looks frozen.
  FIX: Add a `CircularProgressIndicator` that is `visible` when `isCheckingVoucher == true`, positioned next to or replacing the confirm button label.

- [x] **M-5** `ui/dialogs/CartItemDialogs.kt:460`
  **`CartItemDiscountDialog` passes unclamped discount to caller**
  A flat discount larger than the unit price can be submitted.
  FIX: Before calling `onConfirm(discountValue, isPercentage)`, clamp flat discounts: `val safeValue = if (!isPercentage) discountValue.coerceAtMost(originalPrice) else discountValue.coerceAtMost(100.0)`. Update the confirm button's enabled state to reflect this.

### LOW

- [x] **L-1** `ui/screens/CartScreen.kt:228–276`
  **Redundant double discount display in cart summary card**
  Both a "Discount Applied" badge and a "Discount" row show the same `−amount`.
  FIX: Remove one. Keep the badge; remove the plain row (or vice versa — pick one consistently).

- [x] **L-2** `ui/screens/CartScreen.kt` — `CartTotalSummaryCard`
  **Label reads "Estimated Total" when the discount is already applied**
  FIX: Change the label to "Order Total" or just "Total".

- [x] **L-3** `ui/dialogs/CustomItemDialogs.kt:1186–1192`
  **`isTaxable` stored only as appended note text, not a structured field**
  FIX: Add a `taxable: Boolean` field to the `CartItem` model. Pass it from `addCustomItem`. Update receipt and tax calculation logic to read this field rather than parsing the note string.

- [x] **L-4** `ui/dialogs/CustomItemDialogs.kt:1398–1463` — `ManageQuickSalePresetsDialog`
  **No empty state when preset list is empty**
  FIX: When `presetsList.isEmpty()`, show a centred message: "No presets yet — tap + to add one."

- [x] **L-5** `ui/dialogs/CartItemDialogs.kt:388`
  **Discount initial value shows trailing ".0" for whole numbers**
  FIX: Replace `initialDiscount.toString()` with the `editableAmount()` helper already used elsewhere, which strips the trailing `.0`.

- [x] **L-6** `ui/dialogs/CartItemDialogs.kt:282` — `CreditLimitWarningDialog`
  **PIN field hard-limited to exactly 4 digits regardless of actual PIN length**
  FIX: Remove the `.take(4)` cap and change the `enabled` guard from `ownerPin.length == 4` to `ownerPin.isNotEmpty()`. The actual verification logic already handles wrong PINs — just let the user type the full PIN.

- [x] **L-7** `ui/screens/CartScreen.kt` — multiple lines
  **~10 hardcoded English strings bypassing `stringResource`**
  Strings to move to `strings.xml`: "Bill fully covered by store credit!", "⚠️ Short Amount", "✕ Clear", "Available Store Credit: …", "Credit: …", "Enter or scan the customer's store credit voucher code…", the credit-limit and permission error messages, and unit-count strings.
  FIX: Add each to `res/values/strings.xml` and replace the literals with `stringResource(R.string.xxx)`.

---

## PRODUCTS & INVENTORY

### MEDIUM

- [x] **M-6** `ui/screens/ProductsScreen.kt:1407–1411, 1712–1717, 1897–1905`
  **`highMarginOnly` filter excluded from all three active-filter indicators**
  The blue strip, the filter-icon dot, and the "Reset Filters" button in the empty state all miss `highMarginOnly`.
  FIX: Add `|| highMarginOnly` to `hasActiveFilterBar` and `hasActiveFilters`. Add `highMarginOnly = false` to the reset handler in both the strip clear button and the empty-state reset button.

- [x] **M-7** `ui/screens/ProductsScreen.kt:1631–1655`
  **High Margin chip reveals cost/margin data to non-owner roles**
  FIX: Wrap the chip's visibility in `if (canViewCosts)`. Also wrap the `highMarginOnly` filter branch in `visibleProducts` (around line 409–411) in the same guard so the filter has no effect even if somehow activated without the chip.

- [x] **M-8** `ui/screens/ProductsScreen.kt:228–229`
  **`nowTimestamp` captured once — expiry counts diverge from filter after midnight**
  (See also M-9 for the same pattern in SalesReportScreen.)
  FIX: Replace `remember { System.currentTimeMillis() }` with a `produceState` that emits the current time and refreshes at midnight, or at minimum recompute it inside `derivedStateOf` so each recomposition gets the real current time.

### LOW

- [x] **L-8** `ui/screens/ProductsScreen.kt:320–338`
  **Excel import gives no progress indicator**
  FIX: Set a `isImporting` state to `true` before launching the coroutine and `false` in `finally`. Show a `LinearProgressIndicator` or disable the import button while `isImporting`.

- [x] **L-9** `ui/screens/ProductsScreen.kt:1920, 1972`
  **Batch select uses `barcode` as list key — unsafe with duplicate or empty barcodes**
  FIX: Change `key = { it.barcode }` to `key = { it.id }` (or whatever unique integer/UUID the `Product` entity exposes).
  <!-- Rationale: Product entity has no separate integer id column (@PrimaryKey is barcode). Used it.barcode.ifBlank { "prod_${it.name}_${it.createdAt}" } to guarantee uniqueness across all list and grid views. -->

- [x] **L-10** `ui/screens/ProductsScreen.kt` — multiple lines
  **Hardcoded English strings not in `strings.xml`**
  Includes: "Excel Tools", "Exit Batch"/"Batch Select", "Stock Audit", "Print Labels", "Price %", "Category", "Delete" (batch bar), "All SKUs", "All Categories", "⚠️ Low Stock", "🚫 Out of Stock", "💎 High Margin (>30%)", Excel export error toasts, OUT OF STOCK snackbar.
  FIX: Move all to `strings.xml`.

---

## CUSTOMERS, DUES & LOYALTY

### MEDIUM

- [x] **M-10** `utils/DueReminderScheduler.kt:388–414`
  **Duplicate due reminders — both WorkManager and AlarmManager fire**
  After a reminder fires, `executeReminder` schedules the next alarm via AlarmManager. The existing `PeriodicWorkRequest` also fires at the same interval, producing duplicate notifications and duplicate SMS.
  FIX: Pick one mechanism. Recommended: keep AlarmManager for precision (POS reminders need exact timing) and cancel/remove the WorkManager `PeriodicWorkRequest` for reminders. Or keep WorkManager and do not re-schedule via AlarmManager inside `executeReminder`.

- [x] **M-11** `utils/DueReminderScheduler.kt:515`
  **Due reminder PendingIntent always uses request code 0**
  FIX: Replace the literal `0` with a stable unique code per customer, e.g. `customerPhone.hashCode()` or the customer's DB row ID, for both the notification `PendingIntent` and the app-open `PendingIntent`.

- [x] **M-12** `ui/screens/DuesScreen.kt:244`
  **`now` captured once — overdue counts wrong after midnight**
  FIX: Replace `val now = remember { System.currentTimeMillis() }` with a `produceState` that updates at least once per minute, or move the "is overdue" computation into the ViewModel where it can be re-triggered by a timer.

- [x] **M-13** `ui/screens/DuesScreen.kt:300`
  **Date-range filter checks `updatedAt` instead of due-creation timestamp**
  FIX: Change the filter predicate to use the due's creation timestamp field (whichever column records when the due was first created). If no such field exists, add `createdAt: Long` to the entity and populate it on insert.

### LOW

- [x] **L-11** `ui/screens/CustomersScreen.kt:691–695`
  **Bulk WhatsApp silently contacts only the first selected customer**
  FIX: Either (a) open WhatsApp for each selected customer sequentially with a chooser, or (b) disable/hide the bulk WhatsApp button when more than one customer is selected and show a tooltip explaining it supports single-customer messaging only.

- [x] **L-12** `ui/screens/CustomersScreen.kt:422–516`
  **Average Order Value KPI missing from phone layout**
  FIX: Expand the phone grid to 2×3 (or scroll the KPI row horizontally) to include the AoV card alongside the existing four metrics.

- [x] **L-13** `ui/screens/CustomersScreen.kt:604–619`
  **Empty state does not distinguish "no customers" from "none match filter"**
  FIX: Check `hasActiveFilters` and show a different message: "No customers match this filter — try clearing filters" (with a "Clear Filters" button) vs. the default "No customers yet" message.

- [x] **L-14** `ui/screens/CustomersScreen.kt:1374, 1430`
  **Tag list order inconsistent between `BulkAssignTagDialog` and `CustomerAddEditDialog`**
  FIX: Extract the canonical tag list to a single `val CUSTOMER_TAGS = listOf("Retail","VIP","Wholesale","Corporate","Staff","Defaulter")` constant and reference it in both dialogs.

- [x] **L-15** `ui/screens/LoyaltyScreen.kt:1113–1115`
  **"Save Profile" in `LoyaltyAdjustPointsDialog` silently discards typed points**
  Saving the member name dismisses the entire dialog, losing any adjustment in progress.
  FIX: Make the name save and the points adjustment independent operations (separate buttons, separate callbacks). The dialog should not close after a name save.

- [x] **L-16** `ui/screens/LoyaltyScreen.kt:1603–1636`
  **Platinum tier shows "Spend ৳0 more to upgrade to Max Level (Platinum)"**
  FIX: When the member is already Platinum (`nextTier == "Max Level (Platinum)"` and `remaining == 0`), replace the progress prompt with a congratulatory message such as "Highest tier reached!".

- [x] **L-17** `ui/screens/LoyaltyScreen.kt` + `utils/LoyaltySettingsHelper.kt`
  **`pointsExpiryDays` and `isTiersEnabled` saved but never surfaced in the settings dialog**
  FIX: Add two controls to `LoyaltySettingsDialog`: a numeric field for "Points expiry (days, 0 = never)" bound to `pointsExpiryDays`, and a toggle for "Enable membership tiers" bound to `isTiersEnabled`.

- [x] **L-18** `utils/LoyaltySettingsHelper.kt:24–31`
  **`Double` loyalty settings stored as `Float` — precision drift on repeated save/load**
  FIX: Replace `putFloat`/`getFloat` with `putLong(key, value.toBits())`/`java.lang.Double.longBitsToDouble(getLong(key, defaultValue.toBits()))` for all `Double` fields (`pointCashValue`, tier thresholds, etc.).

- [x] **L-19** `data/DueTemplate.kt:42–67`
  **`{bkash}` placeholder in all default SMS templates may never be substituted**
  FIX: Verify that the reminder-sending code (in `DueReminderScheduler` or `DueReminderWorker`) substitutes `{bkash}` with the store's configured bKash number. If no such substitution exists, add it. If the store has no bKash configured, replace `{bkash}` with an empty string or omit the line.

- [x] **L-20** `ui/screens/SuppliersScreen.kt:342–349`
  **"Receive Stock" silently opens Add Supplier when no suppliers exist**
  FIX: Show a Toast or `Snackbar` explaining "Please add a supplier before receiving stock" before opening the add-supplier dialog.

- [x] **L-21** `ui/screens/SuppliersScreen.kt`
  **No sort options and no export for Suppliers**
  FIX: Add a sort menu (by name, total purchased, due amount — same pattern as CustomersScreen). Add an Excel export option matching the Customers export.

- [x] **L-22** `ui/screens/DuesScreen.kt:216–218`
  **Sort/filter state resets on rotation but search query does not**
  FIX: Change `sort`, `duePeriod`, and the custom date range variables from `remember` to `rememberSaveable`.

- [x] **L-23** Multiple screens — `ui/screens/DuesScreen.kt:181, 205` · `CustomersScreen.kt:821, 1535–1544, 1551` · `SuppliersScreen.kt:707–714`
  **Hardcoded English strings in delete/error dialogs bypassing `stringResource`**
  FIX: Move "Customer deleted", "Cannot Delete Customer", "Are you sure you want to delete…", "Cannot Delete Supplier", "Failed to delete supplier" etc. to `strings.xml`.

- [x] **L-24** `ui/screens/CustomerDossierSheet.kt` + `ui/screens/DuesScreen.kt:355–356`
  **Touch targets below Material 3's 48dp minimum**
  FIX: Ensure every `IconButton` has at least `Modifier.size(48.dp)` or wraps its icon with `Modifier.padding(...)` to reach the minimum. Affected: DossierSheet edit/close buttons (lines 191, 197); DuesScreen toolbar icons (lines 355–356).

- [x] **L-25** `ui/screens/LoyaltyScreen.kt` — multiple lines
  **Large block of hardcoded English strings**
  Includes: "Active program", "Last 30 days", "Gold & Platinum", "Quick Preset Amount:", "Points to Redeem (Min …)", tier labels ("Silver (৳)", "Gold (৳)", "Plat (৳)"), "Current Balance", "Lifetime Spend", "Tier Progression", "No loyalty transactions recorded yet".
  FIX: Add all to `strings.xml` and use `stringResource(R.string.xxx)`.

---

## SETTINGS & REPORTS

### HIGH

- [x] **H-6** `ui/screens/SalesReportScreen.kt:700, 847–924`
  **Two diverging expense totals on the same report screen**
  `data.totalExpenses` (stale snapshot) and `periodExpenseTotal` (live StateFlow) are both displayed and can show different values.
  FIX: Remove one source. Prefer the live StateFlow `periodExpenseTotal` for the displayed figure. Remove `data.totalExpenses` from `SalesReportData` or stop rendering it, so there is only ever one expense number on screen.

- [x] **H-7** `ui/screens/SalesHistoryScreen.kt:231–234`
  **`historyProfit` builds report with all-time date range instead of selected period**
  FIX: Change the `buildReport` call to pass the actual selected range: `buildReport(rangeStart, rangeEnd, filteredSales)`.

### MEDIUM

- [x] **M-9** `ui/screens/SalesReportScreen.kt:153`
  **`now` captured once at composition — report stale after midnight**
  (Same pattern as M-8 in ProductsScreen.)
  FIX: Same as M-8: use a `produceState` or a ViewModel-held `StateFlow<Long>` that emits `System.currentTimeMillis()` and refreshes on a daily tick.

- [x] **M-14** `ui/screens/SettingsScreen.kt:210–215`
  **Settings master card shows hardcoded static status strings**
  "Local & Drive Ready" and "Stock Check ON" are literals with no binding to real state.
  FIX: Bind the backup status to the actual Google Drive connection state (expose a `driveConnected: Boolean` from the backup ViewModel). Bind the stock-check status to the actual stock-alert toggle setting (or remove the display if no such setting exists).

### LOW

- [x] **L-26** `ui/screens/SalesReportScreen.kt:152`
  **Default report period is WEEK instead of TODAY**
  FIX: Change the initial value of the period state from `ReportPeriod.WEEK` to `ReportPeriod.TODAY`.

- [x] **L-27** `ui/screens/SalesHistoryScreen.kt:265–268`
  **Back button shown unconditionally on tablets**
  `ProductsScreen` and `SalesReportScreen` gate the top-bar back button on `!isTablet`.
  FIX: Wrap the back `IconButton` in `if (!isTablet)` to match the other two screens.

- [x] **L-28** `ui/screens/SettingsScreen.kt:644–646`
  **"Test Kick" fires even when printer address is blank**
  FIX: Disable the Test Kick button (or show an inline error) when `cashDrawerPrinterAddress.isBlank()`.

- [x] **L-29** `ui/screens/SettingsScreen.kt:1293–1319`
  **Logo bitmaps decoded synchronously on the UI thread**
  FIX: Move both `BitmapFactory.decodeStream` calls out of `remember {}` and into `LaunchedEffect(logoUri) { withContext(Dispatchers.IO) { … } }`, storing the result in `var logoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }`.

- [x] **L-30** `ui/screens/SalesReportScreen.kt:235–249`
  **Export button enabled when there are no transactions — shows a toast instead**
  FIX: Disable the export `IconButton` (set `enabled = report != null && report.transactionCount > 0`) rather than letting the user reach the toast dead-end.

- [x] **L-31** `ui/screens/SettingsScreen.kt` · `ui/screens/SalesHistoryScreen.kt` — multiple lines
  **Hardcoded English strings in Settings and SalesHistory**
  Includes in SettingsScreen: "Allow Negative Inventory", "Lock register session & return to cashier PIN entry", "Direct Bluetooth ESC/POS Driver", "Admin" fallback, "Empty" status, paper width strings.
  Includes in SalesHistoryScreen: "Search invoice, customer…" placeholder, "Reports" button label, "Techyfi POS" fallback shop name, "Admin" fallback cashier name, "📅 Daily"/"⏱️ Hourly" in SalesReportScreen.
  FIX: Move all to `strings.xml`.

---

## NAVIGATION & INFRASTRUCTURE

### HIGH

- [x] **H-8** `Navigation.kt:1069, 1163, 1179, 1194, 1226, 1250`
  **Missing in-route permission guards on 6 routes**
  ManageUsers, SalesReport, Expenses, Suppliers, Settings, and RegisterClose rely solely on call-site permission checks. Any secondary navigation path bypasses the gate.
  FIX: Mirror the pattern used on the Loyalty and Customers routes — add an in-`entry<>` permission check that pops the backstack (and navigates to Scanner or shows an error) if the current user lacks the required permission.
  Additionally: `ManageUsersScreen.kt` takes no permission parameter. Add an `isOwner: Boolean` parameter and render an "Access denied" state when it is false.

- [x] **H-9** `data/DataRepository.kt:279–282`
  **`consumeCreditLimitOverride` clears supervisor token on failed validation**
  The token is consumed whenever `grant.phone == phone`, even when `valid == false`.
  FIX: Only clear `pendingCreditOverride` when `valid == true` (or when the grant is expired). Change the condition to: `if (valid || grant.expiresAt < System.currentTimeMillis()) { pendingCreditOverride = null }`.

### MEDIUM

- [x] **M-15** `Navigation.kt:365–378, 1053–1063`
  **Deep-link destination map incomplete**
  `navigate_to` values of "expenses", "suppliers", "reports", "settings", "register_close" all fall through to `null` and are silently discarded.
  FIX: Add the missing mappings to both copies of the destination map. Example: `"expenses" -> Expenses`, `"suppliers" -> Suppliers`, `"reports" -> SalesReport`, `"settings" -> Settings`, `"register_close" -> RegisterClose`.

- [x] **M-16** `data/DataRepository.kt:1154–1194`
  **N+1 query pattern in `searchCustomerSuggestions`**
  For each customer returned, two more DB queries fire (dues, loyalty) — 2N queries per keystroke.
  FIX: Replace with a single JOIN query in the DAO (or two `IN (phones)` batch queries) to fetch dues and loyalty data for all returned customers in one round-trip.

- [x] **M-17** `data/db/AppDatabase.kt` + `data/db/AppDaos.kt`
  **Missing DB indexes on 4 high-traffic columns**
  - `products.name` and `products.category` — searched with `LIKE '%query%'` on every keystroke
  - `sales.customerPhone` — filtered on every customer-history lookup
  - `sale_items.productBarcode` — grouped on in top-products aggregation
  FIX: Add a migration that creates the following indexes:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);
  CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
  CREATE INDEX IF NOT EXISTS idx_sales_customer_phone ON sales(customerPhone);
  CREATE INDEX IF NOT EXISTS idx_sale_items_barcode ON sale_items(productBarcode);
  ```
  Note: `LIKE '%x%'` (leading wildcard) will not benefit from a B-tree index. Consider switching product search to `LIKE 'x%'` (prefix match) or using SQLite FTS for full-text product search.
  <!-- Comment: Added MIGRATION_31_32 creating the 4 indexes and updated corresponding @Entity definitions in AppEntities.kt. Preserved infix LIKE matching in ProductDao to ensure existing substring search behavior remains intact. -->

### LOW

- [x] **L-32** `Navigation.kt:333`
  **Auto-lock timer resets on process death**
  `backgroundedAt` uses `remember` — after the OS kills and restores the process, the counter resets to 0 and the lock is bypassed.
  FIX: Change `remember` to `rememberSaveable`, or persist the timestamp to DataStore in the `ON_STOP` lifecycle callback and read it back in `ON_START` to compute elapsed time correctly.

- [x] **L-33** `Navigation.kt:1148–1151`
  **Back from SalesReport does not restore SalesHistory**
  SalesHistory is removed before SalesReport is pushed, so pressing Back skips History.
  FIX: Do not remove SalesHistory before pushing SalesReport. Let the normal backstack handle the Back press. If SalesReport should replace History in the "from tab bar" case, add a flag or separate navigation action that distinguishes the two entry paths.

- [x] **L-34** `data/DataRepository.kt:515–528` — `deleteSale()`
  **Inventory adjustment log records stale `newStock`**
  `product.stockQty` is read before `adjustStockQty` runs; the audit entry may be wrong under concurrent access.
  FIX: Re-read the product after the stock adjustment (same pattern used in `adjustProductStock` around line 406) and use the fresh `stockQty` for `newStock` in the audit record.

### MINOR

- [x] **Min-1** `data/db/AppDaos.kt:14, 33, 36`
  **`LOWER(barcode)` on column side prevents PK index use**
  Barcodes are already normalized to lowercase by the repository before the query is made.
  FIX: Remove `LOWER()` from the column side of these queries: `WHERE barcode = :barcode` (the parameter should already be lowercase). Remove `COLLATE NOCASE` from `adjustStockQty` and `decrementStockIfAvailable` for the same reason. Add a code comment that the repository contract is: barcodes must be lowercased before passing to the DAO.

- [x] **Min-2** `MainActivity.kt:35`
  **`currentActivity` is a publicly mutable companion object var**
  Any code can null it or replace it.
  FIX: Add `private set` to restrict external writes. Longer-term, replace with a `LocalActivity` composition local so components get the activity reference through Compose's DI rather than a global.

- [x] **Min-3** `utils/LoyaltySettingsHelper.kt:24–31`
  *(Already listed as L-18 above — same fix applies here as the minor precision variant.)*
  **Double settings stored as Float — already covered in L-18.**

---

## SUMMARY COUNTS
| Severity | Count |
|---|---|
| Critical | 2 |
| High | 9 |
| Medium | 17 |
| Low | 34 |
| Minor | 3 |
| **Total** | **55** |
