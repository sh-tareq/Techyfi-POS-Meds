# Techyfi POS — Fifth Pass Fix List
Generated: 2026-09-23 | Verification of AUDIT_FIXES_4.md batch
60 items: 2 partially-applied fixes + 5 High + 32 Medium + 21 Low.

---

## HOW TO USE THIS FILE
Same as previous — work in order, mark [x] when done.
All paths are relative to `app/src/main/java/com/example/techyfipos/`.

---

## VERIFICATION STATUS — AUDIT_FIXES_4.md

| # | Item | Result |
|---|------|--------|
| PA-1 | CustomerDossierSheet fallback store name localized | PASS |
| PA-2 | LoyaltyScreen CompactLoyaltyRow "Options" contentDescription localized | PASS |
| PA-3 | DueReminderScheduler CHANNEL_NAME constant deleted | PASS |
| PA-4 | CustomersScreen 4 hardcoded contentDescriptions localized | PASS |
| PA-5 | LoyaltyScreen "Clear search", "Options", "Max" localized | PASS |
| PA-6 | SuppliersScreen Pay Due uses ViewModel payment methods | PASS |
| PA-7 | CustomerDossierSheet 7 hardcoded strings localized | PASS |
| N-1 | POSViewModels loyalty cap taxInclusive gate | PASS |
| N-2 | CartScreen split seed from remainingToPay | PASS |
| N-3 | SplitPaymentBuilder receives adjusted grandTotal | PASS |
| N-4 | Cart row +/− buttons 48dp | PASS |
| N-5 | Cashier dropdown 48dp | PASS |
| N-6 | ProductsScreen 7 touch targets 48dp | PASS |
| N-7 | ProductsScreen nowTimestamp as remember key | PASS |
| N-8 | SalesHistoryScreen 2 touch targets + contentDescription | PASS |
| N-9 | SalesHistoryScreen payment filter includes Store Credit + Split | PASS |
| N-10 | Navigation onUnlocked permission checks | PASS |
| N-11 | CustomerDossierSheet tab row heightIn(min=48.dp) | PASS |
| N-12 | CustomerDossierSheet SimpleDateFormat in remember {} | PARTIAL |
| N-13 | ProductsScreen inventory reasons localized | PASS |
| N-14 | CartScreen expired product badges localized | PASS |
| N-15 | CustomItemDialogs complimentary note localized | PASS |
| N-16 | CartScreen quick sale note localized | PASS |
| N-17 | CartItemDialogs button heights 48dp | PASS |
| N-18 | ProductsScreen bulk dialogs localized | PASS |
| N-19 | ProductsScreen stock adjustment reasons string-array | PASS |
| N-20 | ProductsScreen "Pcs" fallback localized | PASS |
| N-21 | SalesHistoryScreen "Net after returns" localized | PASS |
| N-22 | SalesHistoryScreen "Voided for Edit" localized | PASS |
| N-23 | SalesHistoryScreen hardcoded Dhaka/phone removed | PASS |
| N-24 | Navigation canManageStaff = isOwner | PASS |
| N-25 | Navigation tablet sidebar strings localized | PASS |
| N-26 | DataRepository 4 "Admin" fallbacks fixed | PARTIAL |
| N-27 | CustomerDossierSheet isBangla inline strings → stringResource | PASS |
| N-28 | LoyaltyScreen tier display names localized | PASS |
| N-29 | DueReminderScheduler CHANNEL_NAME deleted | PASS |

---

## PART A — PARTIALLY-APPLIED FIXES

- [x] **PA-1** `ui/screens/CustomerDossierSheet.kt:813`
  **N-12 residual — SimpleDateFormat inside invoice-reprint dialog still not in `remember {}`**
  The outer composable's `SimpleDateFormat` at line 84 was correctly hoisted into `remember {}`, but the secondary `CustomerReceiptPreviewDialog` composable at line 813 still contains `val dtFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())` with no `remember` wrapper. A new formatter is allocated on every recomposition of that dialog.
  FIX: `val dtFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }`

- [x] **PA-2** `data/DataRepository.kt:2580, 2680, 2738, 2879, 2908, 3534, 3633, 3739, 3776, 3828`
  **N-26 residual — 10 additional `?: "Admin"` fallbacks remain**
  The 4 targeted sites (lines 212, 415, 515, 576) were fixed. Ten more remain:
  - **Live-operation sites** (lines 3534, 3633, 3739, 3776, 3828): `processReturn()`, `deleteExpense()` audit log, `recordPurchaseInvoice()` cash expense, `recordPurchaseInvoice()` inventory adjustment, `recordSupplierPayment()` cash expense. These write to records during active POS operations.
  - **Backup-import sites** (lines 2580, 2680, 2738, 2879, 2908): Historical records from backups created before multi-user support. The fallback here makes historical attribution misleading.
  FIX: Live-operation sites — replace `?: "Admin"` with `.orEmpty()` and add `Log.w(TAG, "sessionUser null at write time")`. Backup-import sites — replace `?: "Admin"` with `?: ""` (truthful "unknown").

---

## PART B — NEW ISSUES

### HIGH

- [x] **N-1** `ui/screens/CartScreen.kt:~2681`
  **Change amount in credit-limit-override sale path calculated against wrong total**
  Inside the `onAuthorize` block (credit-limit override flow), change is computed as:
  `val snapshotChange = (snapshotAmountPaid - grandTotal).coerceAtLeast(0.0)`
  The normal `executeSaleAction` path correctly uses `effectiveAmountPaid - (grandTotal - storeCreditApplied)`. When store credit is applied and the cashier overpays in cash, the override path prints a change figure inflated by the store-credit amount — the cashier hands back too little change.
  FIX: Replace with:
  ```kotlin
  val snapshotChange = (snapshotAmountPaid - (grandTotal - storeCreditApplied.value).coerceAtLeast(0.0)).coerceAtLeast(0.0)
  ```

- [x] **N-2** `ui/screens/CartScreen.kt:2519`
  **Negative-stock guard ignores `"ADHOC-"` barcodes — unnecessary supervisor-auth dialog on history replay**
  `hasNegativeStock` checks `!it.product.barcode.startsWith("CUSTOM-")`. Items restored from sale history carry `"ADHOC-REPLAY-…"` barcodes that also escape stock checks but are not synthetic enough to be excluded by `startsWith("CUSTOM-")`. The supervisor-auth dialog fires unnecessarily for these items.
  FIX: Extend the exclusion to cover all synthetic barcodes:
  ```kotlin
  val isSynthetic = it.product.barcode.startsWith("CUSTOM-") || it.product.barcode.startsWith("ADHOC-")
  val hasNegativeStock = cartItems.any { it.quantity > it.product.stockQty && !isSynthetic }
  ```
  Or extract a shared `fun isSyntheticBarcode(barcode: String) = barcode.startsWith("CUSTOM-") || barcode.startsWith("ADHOC-")`.

- [x] **N-3** `ui/screens/CartScreen.kt:2378–2488, 2645–2758`
  **Sale-completion logic fully duplicated between `executeSaleAction` and credit-limit `onAuthorize`**
  Both blocks independently build `lineItems`, snapshot all state variables, call `completeSale()`, and construct `BillData`. N-1 above is a direct consequence — the bug was fixed in one branch but not the other. Any future patch to sale logic faces the same split.
  FIX: Extract a single `suspend fun doCompleteSale(lineItems, amountPaid, change, ...)` helper and call it from both `executeSaleAction` and `onAuthorize`. The two call sites differ only in the `amountPaid`/`change` calculation, which is already encapsulated after N-1's fix.

- [x] **N-4** `Navigation.kt:~285, 1081`
  **DuesScreen has no DUES_VIEW permission guard — cashiers can access it without permission**
  In both the `pendingNavDestination` LaunchedEffect handler and the `onUnlocked` callback, `"dues"` navigates directly to the `Dues` destination with no `canViewDues` check. The `entry<Dues>` composable also has no synchronous entry guard. Every other permission-gated screen (SalesReport, Expenses, Suppliers, Settings, RegisterClose, Loyalty, Customers) has an entry guard. A Cashier tapping a due-reminder notification (`navigate_to = "dues"`) lands directly on DuesScreen regardless of role.
  FIX: Add a synchronous entry guard inside `entry<Dues>` following the existing pattern (e.g., alongside `entry<Expenses>`). Also add `"dues" -> if (canViewDues) Dues else Scanner` to both the deep-link handler and `onUnlocked`.

- [x] **N-5** `data/DataRepository.kt:~3553`
  **Store credit voucher code generation uses `REPLACE` — collision silently destroys existing voucher balance**
  `val code = "SC-" + (1000..9999).random() + "-" + (1000..9999).random()` has a 1 in ~81 million collision probability per call but `insertVoucher` uses `OnConflictStrategy.REPLACE`. On collision the original voucher (which may have a partially-redeemed balance) is silently overwritten with the new one — permanent data loss.
  FIX: Change insert to `OnConflictStrategy.ABORT`. Wrap code generation in a retry loop (up to 5 attempts). Throw a descriptive exception only if all attempts collide:
  ```kotlin
  repeat(5) {
      val code = "SC-${(1000..9999).random()}-${(1000..9999).random()}"
      try { storeCreditVoucherDao.insertVoucher(StoreCreditVoucher(code = code, ...)); return } catch (_: SQLiteConstraintException) {}
  }
  throw IllegalStateException("Failed to generate unique voucher code after 5 attempts")
  ```

---

### MEDIUM

- [x] **N-6** `ui/screens/CartScreen.kt:1293–1307`
  **"Apply Store Credit" banner applies only voucher balance when both voucher and customer credit are present**
  `val credAmt = if (redeemedVoucher != null) redeemedVoucher!!.remainingBalance else availableCustCredit` — when both a redeemed voucher and the customer's own store credit exist, only the voucher balance is applied. The customer's credit is silently ignored, leaving money on the table.
  FIX:
  ```kotlin
  val credAmt = (redeemedVoucher?.remainingBalance ?: 0.0) + availableCustCredit
  val applyAmt = min(credAmt, grandTotal)
  ```

- [x] **N-7** `ui/screens/CartScreen.kt:~3075`
  **`System.currentTimeMillis()` called bare inside `CartItemRow` composable**
  `val now = System.currentTimeMillis()` is evaluated on every recomposition of `CartItemRow` without `remember`. Use `produceState` so the value updates periodically without triggering constant recomposition:
  ```kotlin
  val now by produceState(System.currentTimeMillis()) {
      while (true) { delay(60_000L); value = System.currentTimeMillis() }
  }
  ```

- [x] **N-8** `ui/screens/CartScreen.kt:521, 1055, 1139, 1681, 1691, 1748`
  **6 hardcoded English `contentDescription` values**
  | Line | Value |
  |------|-------|
  | 521 | `"Back"` |
  | 1055 | `"Select Cashier"` |
  | 1139 | `"Switch Discount Unit"` |
  | 1681 | `"Clear"` |
  | 1691 | `"Pick from Contacts"` |
  | 1748 | `"Select"` |
  FIX: Add string resources for each and replace with `stringResource(R.string.cd_xxx)`.

- [x] **N-9** `ui/dialogs/CustomItemDialogs.kt:187, 571, 709, 745, 981, 1378, 1446, 1457, 1935, 1944`
  **8+ touch targets below 48dp minimum across three dialogs**
  | Location | Size |
  |----------|------|
  | `AddCustomItemDialog` close button (line 187) | 32 dp |
  | Title clear `IconButton` (line 571) | 24 dp |
  | Note clear `IconButton` (line 981) | 24 dp |
  | Quantity stepper − button (line 709) | 34 dp |
  | Quantity stepper + button (line 745) | 34 dp |
  | `ManageQuickSalePresetsDialog` close button (line 1378) | 30 dp |
  | Preset Edit `IconButton` (line 1446) | 28 dp |
  | Preset Delete `IconButton` (line 1457) | 28 dp |
  | `ManageNotePresetsDialog` Edit `IconButton` (line 1935) | 28 dp |
  | `ManageNotePresetsDialog` Delete `IconButton` (line 1944) | 28 dp |
  FIX: All to `Modifier.size(48.dp)`. Keep the icon itself smaller with a nested `Modifier.size(…)` if needed.

- [x] **N-10** `ui/dialogs/CustomItemDialogs.kt:1603–1607`
  **`remember` without key for editing-preset state — stale values shown when target changes**
  `var editName by remember { mutableStateOf(target.name) }` has no key. Rapid taps can change `editingPreset` while the `remember` retains the previous preset's values.
  FIX: Add the preset's stable id as the key: `remember(target.id) { mutableStateOf(target.name) }` (and likewise for price and unit).

- [x] **N-11** `ui/screens/ProductsScreen.kt:858–1004, 1296–1327`
  **Quick-tool ribbon chips 34dp and batch-action buttons 32dp — both below 48dp minimum**
  - Lines 858–1004: All four `Surface` chips (Excel Tools, Batch Select, Stock Audit History, Print Labels) use `Modifier.height(34.dp)`.
  - Lines 1296–1327: `FilledTonalButton` (Price %, Category) and delete `Button` inside the batch bar use `Modifier.height(32.dp)`.
  FIX: Change all to `Modifier.height(48.dp)` or wrap with `Modifier.heightIn(min = 48.dp)`.

- [x] **N-12** `ui/screens/ProductsScreen.kt:~1777`
  **`stockFilter.name` (raw enum name) shown in active filter summary strip**
  The filter summary buildList adds `stockFilter.name` directly, rendering "LOW", "OUT", "AVAILABLE" in the UI rather than the localized chip labels.
  FIX: Switch on the enum value and use the same localized string used on the filter chips.

- [x] **N-13** `ui/screens/SalesHistoryScreen.kt:419, 487, 506–533, 573–593, 607–618`
  **4 interactive elements below 48dp in SalesHistoryScreen**
  | Location | Size |
  |----------|------|
  | Date pill `Surface` (line 419) | 42 dp |
  | Filter funnel `FilledTonalIconButton` (line 487) | 42 dp |
  | Period chips `Surface` × multiple (lines 506–533) | 26 dp |
  | Returns chip + sort `TextButton` (lines 573–618) | 26 dp |
  FIX: All to `Modifier.size(48.dp)` / `Modifier.height(48.dp)`. Period chips with text content can use `Modifier.heightIn(min = 48.dp)` so they expand naturally.

- [x] **N-14** `ui/screens/SalesHistoryScreen.kt:1002–1010`
  **Payment method filter chip labels display raw English storage keys**
  ```kotlin
  listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due", "Store Credit", "Split").forEach { method ->
      FilterChip(label = { Text(method) }, ...)
  }
  ```
  `Text(method)` passes the storage key directly to the UI. Bengali users see the English key. The existing `getFormattedPaymentMethod()` function already maps these to localized labels.
  FIX: Replace `Text(method)` with `Text(getFormattedPaymentMethod(method, stringResource))` or an equivalent mapping through `stringResource`.

- [x] **N-15** `ui/screens/SalesReportScreen.kt:1623`
  **`SimpleDateFormat` constructed inside `TrendChart` composable without `remember {}`**
  `val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())` is inside the composable body and allocates a new formatter on every recomposition.
  FIX: `val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }`

- [x] **N-16** `ui/screens/SalesReportScreen.kt:291–311`
  **Period filter chips render at ~38dp — no minimum height specified**
  Each period chip `Surface` has `vertical = 8.dp` content padding but no explicit `height`. At default font scale the touch target is approximately 30–38dp.
  FIX: Add `Modifier.heightIn(min = 48.dp)` to each `Surface`.

- [x] **N-17** `ui/screens/SalesReportScreen.kt:1765`
  **`"View product details"` contentDescription hardcoded English**
  ```kotlin
  Icon(Icons.Default.ChevronRight, "View product details", ...)
  ```
  FIX: Replace with `stringResource(R.string.sales_report_view_product_details_cd)`.

- [x] **N-18** `ui/screens/ScannerScreen.kt:191–197, 470–478`
  **Hardcoded English substrings in scanner snackbar messages**
  ```kotlin
  "$name - ${formatTaka(price)} ${context.getString(R.string.add_to_cart)} | Stock: $stock (Total: ${formatTaka(total)})"
  "$name - OUT OF STOCK! | Stock: $stock"
  ```
  Substrings `"| Stock:"`, `"(Total:"`, `"OUT OF STOCK!"` are hardcoded English.
  FIX: Add `R.string.scanner_added_snackbar` and `R.string.scanner_out_of_stock_snackbar` as full format strings with `%1$s`, `%2$s`, etc. placeholders. Use `context.getString(R.string.scanner_added_snackbar, name, formatTaka(price), stock, formatTaka(total))`.

- [x] **N-19** `ui/screens/ScannerScreen.kt:1850–1854`
  **`TabletCameraPipView` uses hardcoded English camera-permission strings**
  ```kotlin
  Text("Camera permission required", ...)
  Button { Text("Grant Permission", ...) }
  ```
  The phone layout already uses `R.string.camera_permission_required` and `R.string.grant_permission` (lines 340, 347). The tablet pip view bypasses those.
  FIX: Replace both strings with `stringResource(R.string.camera_permission_required)` and `stringResource(R.string.grant_permission)`.

- [x] **N-20** `ui/screens/ScannerScreen.kt:1071, 1082, 1372, 1392`
  **"Hold" and "Clear" contentDescriptions hardcoded English in tablet cart panels**
  FIX: Use existing string resource keys (e.g., `R.string.tablet_cart_hold`, `R.string.tablet_cart_clear`) — both already exist for the button labels.

- [x] **N-21** `ui/screens/ScannerScreen.kt:1898`
  **`TabletProductGridCard` uses hardcoded low-stock threshold of 5.0 instead of `product.reorderLevel`**
  `val isLowStock = !isOutOfStock && product.stockQty <= 5.0`. The rest of the app (ProductsScreen, inventory alerts) compares against `product.reorderLevel`.
  FIX: `val isLowStock = !isOutOfStock && product.stockQty <= product.reorderLevel`

- [x] **N-22** `Navigation.kt:1188–1192`
  **`isSupervisor` missing from 4 of 5 `ProductsScreen` permission params — role hierarchy inversion**
  `canPrintLabels` correctly includes `|| isSupervisor` but the other four do not:
  ```kotlin
  canViewCosts    = isOwner || activePermissions.contains(INVENTORY_VIEW_COSTS),
  canEdit         = isOwner || activePermissions.contains(INVENTORY_ADD_EDIT),
  canDelete       = isOwner || activePermissions.contains(INVENTORY_DELETE),
  canAdjustStock  = isOwner || activePermissions.contains(INVENTORY_ADJUST_STOCK),
  canPrintLabels  = isOwner || isSupervisor || activePermissions.contains(INVENTORY_PRINT_LABELS),
  ```
  A Supervisor cannot view costs or adjust stock but can print labels — a lower-trust action than stock editing.
  FIX: Add `|| isSupervisor` to `canViewCosts`, `canEdit`, `canDelete`, and `canAdjustStock`.

- [x] **N-23** `ui/screens/CustomerDossierSheet.kt:445, 539, 759`
  **3 hardcoded English strings in dossier tab bodies**
  - L445: `"${item.totalQty} qty"` — "qty" hardcoded
  - L539: `"${saleWithItems.items.size} items"` — "items" hardcoded, should use `pluralStringResource`
  - L759: `"Bal: ${formatTaka(entry.balanceAfter)}"` — "Bal:" hardcoded
  FIX: Add `R.string.dossier_qty_suffix`, `R.plurals.items_count`, and `R.string.dossier_balance_abbrev`. Use `stringResource()` / `pluralStringResource()`.

- [x] **N-24** `ui/screens/CustomerDossierSheet.kt:367, 614`
  **Two touch targets below 48dp in CustomerDossierSheet**
  - L367: "Adjust Credit" `OutlinedButton` — `Modifier.height(32.dp)` (16dp below minimum)
  - L614: Save Note button — `Modifier.defaultMinSize(minHeight = 44.dp)` (4dp below minimum)
  FIX: L367 → `Modifier.heightIn(min = 48.dp)`. L614 → `Modifier.defaultMinSize(minHeight = 48.dp)`.

- [x] **N-25** `ui/screens/CustomerDossierSheet.kt:807`
  **`SettingsDataStore` instantiated directly in composable scope**
  `val configuredStoreName by remember(context) { SettingsDataStore(context).shopNameFlow }`. `DataStore` specification prohibits multiple instances on the same file path; creating one inside a composable (which re-runs on configuration changes) risks `IllegalStateException`.
  FIX: Expose `shopName` from `SettingsViewModel` as a `StateFlow` and collect it in the composable with `collectAsStateWithLifecycle()`.

- [x] **N-26** `ui/screens/CustomersScreen.kt:1019–1041, 563`
  **Hardcoded English strings in `CustomerDataTable` and duplicate-pair banner**
  - Lines 1019–1041: `"Due: ${formatTaka(...)}"`, `"৳0 due"`, `"${customer.totalOrders} ord"`, `"⭐ ${customer.loyaltyPoints}p"` — none localized.
  - Lines 1323–1324, 1340–1341 (`CustomerMergeDialog`): `"📞 ${...} • Tag: ${...}"` and `"Orders: ${...} • Spend: ${formatTaka(...)}"` — hardcoded English.
  FIX: Add string resources for all labels and use `stringResource()`.

- [x] **N-27** `ui/screens/CustomersScreen.kt:563`
  **"Review & Merge" button in duplicate-pair banner 40dp — below 48dp minimum**
  `Modifier.defaultMinSize(minHeight = 40.dp)`.
  FIX: Change to `Modifier.defaultMinSize(minHeight = 48.dp)`.

- [x] **N-28** `ui/screens/LoyaltyScreen.kt:757, 1638–1642`
  **3 hardcoded English strings in LoyaltyScreen**
  - L757: `"${formatTaka(member.lifetimeSpent)} spent"` — "spent" hardcoded in `CompactLoyaltyRow`
  - L1638: `"⭐ ${member.pointsBalance} Points"` — "Points" hardcoded
  - L1642: `"Cash value: ${formatTaka(member.pointsBalance * pointCashValue)}"` — "Cash value:" hardcoded
  FIX: Add `R.string.loyalty_spent_suffix`, `R.string.loyalty_points_balance_display`, `R.string.loyalty_cash_value_display` with format args and use `stringResource()`.

- [x] **N-29** `ui/screens/LoyaltyScreen.kt:1238`
  **"View Existing" duplicate-warning button 28dp — 20dp below minimum**
  `Modifier.height(28.dp)`.
  FIX: `Modifier.heightIn(min = 48.dp)`.

- [x] **N-30** `ui/screens/LoyaltyScreen.kt:1424–1432`
  **`LoyaltySettingsDialog` does not validate that tier thresholds are ascending**
  The dialog saves whatever the user typed. If `silverThresh > goldThresh` or `goldThresh > platinumThresh`, tier-progression bars and spend-to-next calculations display incorrect results.
  FIX: Before calling `onSave`, assert `silver < gold < platinum`. Show an inline error `Text` if the condition fails, and disable the save button while invalid.

- [x] **N-31** `ui/screens/SuppliersScreen.kt:424, 450, 1179–1180`
  **Two issues in SuppliersScreen**
  1. Hero CTA buttons 40dp (lines 424, 450): `Modifier.weight(1f).height(40.dp)` on "Add Vendor" and "Quick Inward" — 8dp below minimum. FIX: `Modifier.weight(1f).heightIn(min = 48.dp)`.
  2. Pay Due dialog initializes `payMethod = "Cash"` (lines 1179–1180), which may not exist in the configured payment methods list. If "Cash" is absent, no chip is selected and the confirm button will pass an unconfigured payment method to the database. FIX: `var payMethod by remember { mutableStateOf(supplierViewModel.paymentMethods.firstOrNull() ?: "") }` and disable the confirm button when `payMethod.isBlank()`.

- [x] **N-32** `ui/screens/ManageUsersScreen.kt:796–801`
  **`PermissionsMatrixDialog` gives no feedback when permission save fails**
  `authViewModel.updateStaffPermissions(...) { success -> if (success) { onSaved() } }` — if `success` is false, the dialog stays open silently. The user has no indication of what went wrong and may tap Save repeatedly.
  FIX: Add an `else` branch: `else { errorMessage = context.getString(R.string.users_permissions_save_failed) }` and display `errorMessage` as a red `Text` below the buttons.

- [x] **N-33** `ui/screens/ManageUsersScreen.kt:641–666`
  **`RoleSelectorRow` items render at ~32–36dp — below 48dp minimum**
  `Surface { Box(Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) { Text(...) } }` with `labelSmall` text.
  FIX: Add `Modifier.heightIn(min = 48.dp)` to the outer `Surface`.

- [x] **N-34** `ui/screens/SettingsScreen.kt:209`
  **"Thermal" hardcoded English in printer status card**
  `Text("${paperRollWidthMm}mm Thermal", ...)` — "Thermal" is not localized.
  FIX: Add `R.string.settings_printer_type_thermal` and use `stringResource()`.

- [x] **N-35** `utils/DueReminderScheduler.kt:124, 226`
  **Bare `CoroutineScope(Dispatchers.IO)` — uncancellable, leaks on process kill**
  Database writes launched on anonymous `CoroutineScope(Dispatchers.IO)` cannot be cancelled if the process is killed mid-write.
  FIX:
  ```kotlin
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  ```
  Use `scope.launch { ... }` throughout. Add `fun cancel() { scope.cancel() }` if the scheduler is ever torn down explicitly.

- [x] **N-36** `utils/BillPdfGenerator.kt:283–305`
  **`buildBillReceiptText()` omits item notes that the JPEG renderer includes**
  The text-receipt builder iterates `bill.items` but never outputs `item.note`. The JPEG renderer (line ~485) prints `noteLines` for each item. Text receipts shared via WhatsApp/SMS are therefore incomplete when cashiers add item-level notes.
  FIX: Inside the item loop in `buildBillReceiptText`, add:
  ```kotlin
  if (!item.note.isNullOrBlank()) {
      sb.appendLine(if (isBn) "  • ${item.note}" else "  • Note: ${item.note}")
  }
  ```

- [x] **N-37** `data/db/AppEntities.kt:301` / `data/db/AppDaos.kt:546`
  **`InventoryAdjustment.productBarcode` has no index — full table scan on every product audit**
  `InventoryAdjustmentDao.getAdjustmentsForProduct(barcode)` scans the entire `inventory_adjustments` table. In high-volume stores this degrades over time.
  FIX: Add `indices = [Index(value = ["productBarcode"])]` to `@Entity(tableName = "inventory_adjustments")`. Create `MIGRATION_X_Y` with `CREATE INDEX IF NOT EXISTS index_inventory_adjustments_productBarcode ON inventory_adjustments(productBarcode)`.

---

### LOW

- [x] **N-38** `ui/screens/SalesHistoryScreen.kt:~1050`
  **Reset button in filter sheet applies `selectedPeriod` immediately, bypassing the draft/apply pattern**
  All other filter fields use draft variables applied together on "Apply". The period reset takes effect instantly, creating inconsistent UX.
  FIX: Store `draftPeriod` alongside the other draft state variables; reset all drafts together and apply on "Apply".

- [x] **N-39** `ui/screens/SalesHistoryScreen.kt:506–533`
  **No chip highlighted after user picks a custom date range**
  After setting a custom date range, none of the standard period chips is selected. Users cannot see what range is active.
  FIX: Add a "Custom" chip to the list that becomes selected when `selectedPeriod == HistoryReportPeriod.CUSTOM` (or similar custom-range state), showing the active range dates in its label.

- [x] **N-40** `ui/screens/SalesHistoryScreen.kt:1072–1075`
  **`groupSalesByDay()` creates a new `SimpleDateFormat` inside the function body**
  The function is called inside `remember(filteredSales)` so it only runs on list changes, but extracting the formatter to a `remember {}` at call site or module-level val makes the intent explicit and avoids future regression.
  FIX: Extract `SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())` to a `remember {}` at the call site or a module-level `val`.

- [x] **N-41** `ui/screens/SalesReportScreen.kt:252`
  **`SimpleDateFormat` constructed inside export `onClick` handler**
  `SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(rangeStart))` inside an `onClick` lambda. Hoist to `remember {}` for consistency with the codebase's own pattern (line 195).

- [x] **N-42** `ui/screens/CartScreen.kt:1072`
  **Hardcoded fallback cashier name list baked into UI code**
  `listOf("Admin", "Ahmed", "Fatema", "Karim", "Rahim")` — English/Bengali names embedded in source. Move to `arrays.xml` / a string-array resource so they can be changed without recompiling.

- [x] **N-43** `ui/screens/CartScreen.kt:2289`
  **Savings display uses direct string interpolation, not a string resource**
  `"${formatTaka(totalSavings)} ($pctStr%)"` — the bracket-percent format cannot be adapted for Bengali or RTL locales.
  FIX: Add `R.string.cart_savings_with_pct` (e.g., `%1$s (%2$s%%)`) and use `stringResource(R.string.cart_savings_with_pct, formatTaka(totalSavings), pctStr)`.

- [x] **N-44** `ui/screens/CartScreen.kt:2824`
  **`heldCartToastTemplate.format(finalTitle)` uses Kotlin `String.format` instead of `context.getString()`**
  Every other parameterised toast uses `context.getString(R.string.x, arg)`. `String.format` relies on `%s` in the resource string and skips Android's locale-aware formatting.
  FIX: `context.getString(R.string.held_cart_held_toast, finalTitle)` (ensure resource uses `%1$s`).

- [x] **N-45** `ui/viewmodel/POSViewModels.kt:~424`
  **`redeemVoucherCode` calls `setStoreCreditApplied` twice — momentary over-limit before UI corrects**
  `redeemVoucherCode` calls `setStoreCreditApplied(voucher.remainingBalance)` internally; then `onSuccess` calls it again with `min(voucher.remainingBalance, grandTotal)`. The first call can exceed `grandTotal` before the second corrects it.
  FIX: Remove the `setStoreCreditApplied` call inside `redeemVoucherCode`. Leave only the capped call in `onSuccess`.

- [x] **N-46** `ui/screens/CustomerDossierSheet.kt:716`
  **Unknown `StoreCreditLedger` entry type falls through with hardcoded emoji**
  `else -> "🪙 ${entry.type}"` displays the raw database enum name to the user.
  FIX: Add `R.string.dossier_credit_unknown_type` and use `stringResource()`.

- [x] **N-47** `ui/screens/CustomersScreen.kt:1382–1383, 1438`
  **Customer tag values hardcoded English in UI (BulkAssignTagDialog and CustomerAddEditDialog)**
  `listOf("Retail", "Wholesale", "VIP", "Corporate", "Staff", "Defaulter")` shown directly as Text to Bengali users.
  FIX: Create a paired `(key, stringResId)` list. Display the localized label in the UI while persisting the canonical English key to the database.

- [x] **N-48** `ui/screens/LoyaltyScreen.kt:1855–1856`
  **"pts" suffix hardcoded English in audit ledger transaction rows**
  `"+${tx.pointsChange} pts"` and `"${tx.pointsChange} pts"`.
  FIX: Add `R.string.loyalty_pts_suffix` and use `stringResource()`.

- [x] **N-49** `ui/screens/DuesScreen.kt:352, 440, 451`
  **3 hardcoded contentDescriptions in DuesScreen TopAppBar**
  - L352: `contentDescription = "Back"`
  - L440: `contentDescription = "Share customer statement"`
  - L451: `contentDescription = "Delete Due Account"`
  FIX: Replace with `stringResource(R.string.cd_back)`, `R.string.dues_share_statement_cd`, `R.string.dues_delete_account_cd`.

- [x] **N-50** `ui/screens/ManageUsersScreen.kt:356, 363`
  **`StaffUserCard` Edit and Delete `IconButton`s have no explicit size**
  Material3 defaults to 48dp but the project's convention requires explicit `Modifier.size(48.dp)` to prevent regressions.
  FIX: Add `Modifier.size(48.dp)` to both.

- [x] **N-51** `utils/DueReminderScheduler.kt:239–251`
  **Non-recurring reminders that fired while device was off reschedule with 3-second delay on boot**
  `remainingDelay` is clamped to 3 seconds for past `fireAt` values. Non-recurring (one-shot) reminders that already fired will re-fire 3 seconds after boot, sending duplicate stale reminders.
  FIX: In `reschedulePersistedReminders`, skip one-shot reminders where `reminder.fireAt < now` and call `deleteByPhone` (or equivalent cleanup) instead of rescheduling.

- [x] **N-52** `utils/LoyaltySettingsHelper.kt` / `ui/screens/LoyaltyScreen.kt:79`
  **`LoyaltySettingsHelper.getSettings(context)` called synchronously on main thread inside a composable**
  SharedPreferences reads are blocking and on first cold start of an encrypted device can drop frames.
  FIX: Move settings loading into `LoyaltyViewModel` as a `StateFlow<LoyaltyProgramSettings>` initialized in `init { }` via a coroutine. Collect in the composable with `collectAsStateWithLifecycle()`.

- [x] **N-53** `data/db/AppDaos.kt:93`
  **`getAllSalesOnce()` missing `ORDER BY` — returns sales in undefined order**
  ```kotlin
  @Query("SELECT * FROM sales")
  suspend fun getAllSalesOnce(): List<Sale>
  ```
  The companion Flow version `getAllSales()` correctly uses `ORDER BY timestamp DESC`.
  FIX: Add `ORDER BY timestamp DESC`.

- [x] **N-54** `data/db/AppDaos.kt:516, 522`
  **Duplicate DAO methods for deleting customer loyalty records**
  `delete(phone: String)` and `deleteLoyaltyByPhone(phone: String)` execute identical SQL with different return types and names.
  FIX: Remove `deleteLoyaltyByPhone` and update all callers to use `delete`. (Or vice versa — whichever name is more consistent with the DAO's style.)

- [x] **N-55** `Navigation.kt:1236–1243`
  **`SalesReportScreen` expense/supplier nav callbacks skip permission check**
  `onNavigateToExpenses` and `onNavigateToSuppliers` inside the SalesReport composable call do not check `canExpense` / `canSuppliers` before adding to the backstack. Users lacking those permissions are bounced by the entry guard — correct security outcome but jarring UX (flash of rejected screen).
  FIX: Mirror the MoreHub pattern: check the relevant permission flag inside the callback; show a Toast and skip `backStack.add()` if denied.

- [x] **N-56** `data/db/AppEntities.kt:49, 157, 312`
  **Entity `cashierName` defaults to `"Admin"` — silent misleading audit records if caller forgets the field**
  Three entities (`Expense`, `RegisterClosing`, `InventoryAdjustment`) use `val cashierName: String = "Admin"`. Call sites that omit `cashierName` compile without error but write "Admin" to permanent records.
  FIX: Change each default to `cashierName: String = ""` (or make nullable). The repository always supplies the session user's name; the entity default is only a trap.

- [x] **N-57** `data/db/AppEntities.kt:459` / `data/db/AppDaos.kt:579`
  **`DueReminderLog.customerPhone` has no index**
  `DueReminderLogDao.getLogsForCustomer(phone)` queries by `customerPhone` without an index. Add `indices = [Index(value = ["customerPhone"])]` to the entity and a `CREATE INDEX` in the migration alongside N-37's index.

- [x] **N-58** `ui/screens/ProductsScreen.kt:917`
  **`SimpleDateFormat` constructed inside an `onClick` handler — allocates on every tap**
  `SimpleDateFormat("yyyyMMdd", Locale.getDefault())` inside the Export Excel `onClick`. Hoist to `remember {}` at composable scope alongside the existing `dateFormat` values in the file.

---

## SUMMARY
| Category | Count |
|---|---|
| Partially-applied fixes (PA) | 2 |
| New — High | 5 |
| New — Medium | 32 |
| New — Low | 21 |
| **Total** | **60** |
