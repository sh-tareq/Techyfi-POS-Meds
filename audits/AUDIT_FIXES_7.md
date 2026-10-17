# Audit Round 7 — Fix List
Generated: 2026-09-27

## Verification of AUDIT_FIXES_6.md (49 items)

| ID | Description | Result |
|----|-------------|--------|
| N-1 | POSViewModels completeSale() cash guard uses remainingCash | PASS |
| N-2 | ProductsScreen Import Excel guarded by canEdit | PASS |
| N-3 | SalesHistoryScreen dayGroupFormatter hoisted before conditional | PASS |
| N-4 | DuesScreen collectAsStateWithLifecycle unconditional (5 flows) | PASS |
| N-5 | LoyaltyScreen quick-% integer division fixed to /100.0 | PASS |
| N-6 | SalesReportScreen effectiveNetProfit uses grossProfit, no double-subtraction | PASS |
| N-7 | SalesReportScreen currentRangeLabel via rememberUpdatedState | PASS |
| N-8 | CustomersScreen exportCustomerCsv uses withContext(Dispatchers.IO) | PASS |
| N-9 | ScannerScreen dismiss IconButton defaultMinSize 48dp | PASS |
| N-10 | AppDaos insertSale/insertSaleItems/insertDuePayment/insertUser all ABORT | PASS |
| N-11 | CartScreen 3 touch targets heightIn(min=48.dp) | PASS |
| N-12 | CartScreen Animatable remember(method)/remember(type) keyed | PASS |
| N-13 | getLoyaltySettings exposed as StateFlow in CartViewModel | PASS |
| N-14 | changeAmount StateFlow removed; dead collectAsStateWithLifecycle removed | PASS |
| N-15 | ProductsScreen cart capsule height(48.dp) | PASS |
| N-16 | ProductsScreen Batch Select chip hidden when !canEdit && !canDelete | PASS |
| N-17 | SalesHistoryScreen historyCurrentDayEndUtcMillis remember(now) | PASS |
| N-18 | SalesHistoryScreen Payment3DBadge localized via when mapping | PASS |
| N-19 | SalesHistoryScreen dayGroupFormatter remember(currentLocale) | PASS |
| N-20 | SalesReportScreen currentDayEndUtcMillis remember(now) | PASS |
| N-21 | SalesReportScreen TrendChart hour labels locale-aware | PASS |
| N-22 | ScannerScreen 6 contentDescriptions localized; touch targets corrected | PASS |
| N-23 | LoyaltyScreen FilterChipItem defaultMinSize(minHeight=48.dp) | PASS |
| N-24 | LoyaltyScreen LoyaltySettingsDialog remember(currentSettings) | PASS |
| N-25 | LoyaltyScreen getCustomerByPhone via ViewModel, not direct repository | PASS |
| N-26 | CustomersScreen email OutlinedTextField added to CustomerAddEditDialog | PASS |
| N-27 | CustomersScreen Checkbox size(48.dp) in header and rows | PASS |
| N-28 | SuppliersScreen filter tabs defaultMinSize(minHeight=48.dp) | PASS |
| N-29 | SuppliersScreen payment amount capped at totalDue with validation | PASS |
| N-30 | ManageUsersScreen Permissions button defaultMinSize(minHeight=48.dp) | PASS |
| N-31 | CustomerDossierSheet notes tab has per-note delete IconButton | PASS |
| N-32 | CustomerDossierSheet notes delimiter changed to \u001E | PASS |
| N-33 | AppDaos getTopProductsForCustomer GROUP BY barcode only, MAX(name) | PASS |
| N-34 | CartScreen cash tender chips use formatTaka() | PASS |
| N-35 | POSViewModels CartViewModel cashierName else branch returns "" | PASS |
| N-36 | POSViewModels held-cart title uses context.getString() | PASS |
| N-37 | CartScreen quantity display piped through toBanglaDigits() | PASS |
| N-38 | ProductsScreen import error fallback uses stringResource | PASS |
| N-39 | SalesReportScreen emoji inside string resource with format arg | PASS |
| N-40 | ScannerScreen null sentinel replaces "All" string | PASS |
| N-41 | CustomersScreen "pts" suffix via stringResource | PASS |
| N-42 | CustomersScreen tag uses getLocalizedCustomerTag() | PASS |
| N-43 | CustomerDossierSheet LinearProgressIndicator padding before clip | PASS |
| N-44 | CustomerDossierSheet Due Ledger shows both payments and charges | PASS |
| N-45 | LoyaltyScreen Redeem/Add both in confirmButton Row | PASS |
| N-46 | ManageUsersScreen discount field validated; Save disabled while invalid | PASS |
| N-47 | SuppliersScreen SimpleDateFormat remember(Locale.getDefault()) | PASS |
| N-48 | AppDaos getTopProductsForCustomer default limit raised to 25 | PASS |
| N-49 | AppEntities customers Index(value=["name"]) added | PASS |

**Verification summary: 49 PASS · 0 PARTIAL · 0 FAIL**

---

## New Issues — Round 7

### HIGH

- [x] **N-1** `remember` count changes across recompositions in payment chip `forEach` — slot-table crash risk
  - File: `ui/screens/CartScreen.kt:1594`
  - Problem: `availablePaymentMethods.forEach { method -> val chipScaleAnim = remember(method) { Animatable(1f) } }` — the list grows from 6 to 7 entries when a customer with store credit is attached (or a voucher is redeemed). Compose tracks `remember` calls by slot-table position; a list size change mid-session causes a runtime `IllegalStateException` (slot-table corruption crash).
  - Fix: Pre-create a remembered map above the loop:
    ```kotlin
    val chipAnims = remember(availablePaymentMethods) {
        availablePaymentMethods.associateWith { Animatable(1f) }
    }
    availablePaymentMethods.forEach { method ->
        val chipScaleAnim = chipAnims[method]!!
        ...
    }
    ```

- [x] **N-2** Contact picker ContentResolver queries on main thread — ANR risk
  - File: `ui/screens/CartScreen.kt:362–405`
  - Problem: The `contactPickerLauncher` activity-result lambda calls two `context.contentResolver.query()` calls synchronously on the main thread. On devices with thousands of contacts this can block the main thread hundreds of milliseconds, causing an ANR.
  - Fix: Wrap entire block in `scope.launch(Dispatchers.IO) { ... }` and post ViewModel calls back with `withContext(Dispatchers.Main)`.

### MEDIUM

- [x] **N-3** Unstable fallback `MutableStateFlow` constructed on every recomposition
  - File: `ui/screens/CartScreen.kt:306`
  - Problem: `(authViewModel?.maxDiscountPercent ?: kotlinx.coroutines.flow.MutableStateFlow(10.0)).collectAsStateWithLifecycle(initialValue = 10.0)` — when `authViewModel` is null a new `MutableStateFlow` is allocated on every recomposition; `collectAsStateWithLifecycle` re-subscribes each time causing unnecessary work and potential flicker.
  - Fix: `val userDiscountLimit by remember(authViewModel) { authViewModel?.maxDiscountPercent ?: MutableStateFlow(10.0) }.collectAsStateWithLifecycle(initialValue = 10.0)`

- [x] **N-4** `editingNoteItemIndex` captures stale LazyColumn display index — wrong-item note shown
  - File: `ui/screens/CartScreen.kt:937`
  - Problem: `onEditNote = { editingNoteItemIndex = index }` stores the LazyColumn display index. If an item is removed or the list reorders between click and dialog render, `cartItems.getOrNull(idx)` returns the wrong item's note silently.
  - Fix: Capture the stable barcode instead: `var editingNoteBarcode by remember { mutableStateOf<String?>(null) }`. Set via `onEditNote = { editingNoteBarcode = item.product.barcode }`. Look up with `cartItems.find { it.product.barcode == editingNoteBarcode }`.

- [x] **N-5** Payment method chip labels "Cash", "bKash", "Nagad", "Rocket", "Card" not localized
  - File: `ui/screens/CartScreen.kt:1633–1640`
  - Problem: `else -> method` in the payment method `when` expression renders raw English strings for all non-special payment methods. Only "Due" and "Store Credit" are localized.
  - Fix: Add explicit `when` cases for each method mapping to `stringResource(R.string.payment_cash)` etc.

- [x] **N-6** Discount input field shows unapplied value when supervisor auth is dismissed
  - File: `ui/screens/CartScreen.kt:1202–1216`
  - Problem: If the supervisor auth dialog is dismissed without approving, `pendingSupervisorAction` is cleared but `discountInput` retains the unapplied value (e.g. "60%"), showing a value that is not applied to the cart.
  - Fix: Reset `discountInput = editableAmount(discountAmount)` in the `onDismissRequest` / dismiss callback of `SupervisorAuthDialog`.

- [x] **N-7** `remember(type)` inside card-type chip `forEach` — will crash if list ever grows
  - File: `ui/screens/CartScreen.kt:1702`
  - Problem: Same anti-pattern as N-1. `listOf("Visa","Mastercard","Amex","Other").forEach { type -> remember(type) { Animatable(1f) } }` — harmless today (fixed 4 items) but crashes the moment any card type is added.
  - Fix: Same pre-computed map pattern as N-1.

- [x] **N-8** "Add Product" button on tablet is 44dp — below 48dp minimum touch target
  - File: `ui/screens/ProductsScreen.kt:1542`
  - Problem: `Modifier.height(44.dp)` on the tablet "+ Add Product" `Button`.
  - Fix: `Modifier.height(48.dp)` or `Modifier.defaultMinSize(minHeight = 48.dp)`.

- [x] **N-9** Six inline product card action buttons 32dp tall — far below 48dp minimum
  - File: `ui/screens/ProductsScreen.kt:4482, 4494, 4718, 4731, 5041, 5059`
  - Problem: Six `Button`/`Surface` elements in product card expanded rows use `.height(32.dp)`.
  - Fix: All six to `Modifier.height(48.dp)` or wrap in `Box(Modifier.height(48.dp), contentAlignment = Alignment.Center)`.

- [x] **N-10** `SaleItem` entity missing `taxable` column — per-item taxability lost on persistence
  - File: `data/db/AppEntities.kt:276`
  - Problem: `CartItem.taxable: Boolean` is used to compute the tax line on receipts, but `SaleItem` has no `taxable` field. The information is discarded when the sale is saved — returns, historical profit reports, and reprints cannot reconstruct which items were taxable.
  - Fix: Add `val taxable: Boolean = true` to `SaleItem`. Add migration: `ALTER TABLE sale_items ADD COLUMN taxable INTEGER NOT NULL DEFAULT 1`. Increment `DATABASE_VERSION`.

- [x] **N-11** Missing indices on `customers.tag` and `customers.phone` used in filter queries
  - File: `data/db/AppEntities.kt:477`, `data/db/AppDaos.kt:606`
  - Problem: `CustomerDao.searchCustomers` uses `LIKE '%..%'` on `address`, `notes`, `tag`. No indices on `tag` or `phone` (aside from the primary key). Covering `tag` enables index-only scans for tag-equality filters. Phone lookups (e.g. dues, loyalty) do full scans.
  - Fix: Add `Index(value = ["tag"])` and `Index(value = ["phone"])` to the `Customer` entity's `indices` array. Add to next migration.

### LOW

- [x] **N-12** `showMarginMode` uses `remember` instead of `rememberSaveable` — lost on rotation
  - File: `ui/screens/SalesHistoryScreen.kt:1117`
  - Problem: The revenue/margin toggle resets to "Revenue" on every configuration change, while the adjacent `isCollapsed` correctly uses `rememberSaveable`.
  - Fix: `var showMarginMode by rememberSaveable { mutableStateOf(false) }`

- [x] **N-13** Category selection typed as `Set<String>` but behaves as single-select
  - File: `ui/screens/ProductsScreen.kt:1735`
  - Problem: `selectedCategories = if (isCatActive) emptySet() else setOf(category)` — single-select behaviour implemented with a Set type, misleading to future maintainers and wastes object allocation.
  - Fix: `var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }`. Update all usages to compare against `selectedCategory == category`.

- [x] **N-14** Cart bounce animation fires when cart is empty (spurious animation)
  - File: `ui/screens/ProductsScreen.kt:782`
  - Problem: `cartAnimKey = System.currentTimeMillis()` is set on every cart-button click regardless of cart contents; the `LaunchedEffect(cartUnitCount, cartAnimKey)` fires the bounce animation even when `cartUnitCount == 0`.
  - Fix: Guard the key update: `if (cartUnitCount > 0) { cartAnimKey = System.currentTimeMillis() }`.

- [x] **N-15** `DueReminderLogDao.insertLog` uses `OnConflictStrategy.REPLACE` — misleading intent
  - File: `data/db/AppDaos.kt:579`
  - Problem: `DueReminderLog` uses `@PrimaryKey(autoGenerate = true)` with default `id = 0`, so REPLACE effectively always inserts (no conflict today), but the strategy silently destroys a real row if a non-zero id is ever passed in future.
  - Fix: Change to `OnConflictStrategy.ABORT`.

- [x] **N-16** Hardcoded English "Admin" fallback in CartScreen display layer
  - File: `ui/screens/CartScreen.kt:349`
  - Problem: `cashierName.ifBlank { "Admin" }` — bare English literal shown to Bengali users when the cashier name is blank. (CartViewModel already returns `""` from its else branch; this is a separate display-layer fallback.)
  - Fix: Add `<string name="default_cashier_admin">Admin</string>` to `strings.xml` (translatable). Use `stringResource(R.string.default_cashier_admin)` as the fallback.

- [x] **N-17** `L-6` `DueReminderLogDao.insertLog` REPLACE strategy (duplicate of N-15 intent note — covered above)
  - Already addressed in N-15.
