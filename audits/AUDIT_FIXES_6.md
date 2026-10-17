# AUDIT_FIXES_6.md — Round 6

Generated: 2026-09-27
Verifying: AUDIT_FIXES_5.md (60 items) — **60 PASS · 0 PARTIAL**
New issues: **10 HIGH · 23 MEDIUM · 16 LOW = 49 total**

---

## Verification — AUDIT_FIXES_5.md (60 items)

| ID | Description | Result |
|----|-------------|--------|
| PA-1 | CustomerDossierSheet:816 SimpleDateFormat in remember {} | PASS |
| PA-2 | DataRepository all remaining `?: "Admin"` fallbacks removed | PASS |
| N-1 | CartScreen onAuthorize snapshotChange subtracts storeCreditApplied | PASS |
| N-2 | CartScreen isSyntheticSaleBarcode() covers CUSTOM- and ADHOC- | PASS |
| N-3 | CartScreen doCompleteSale() shared helper called from both paths | PASS |
| N-4 | Navigation Dues entry guard + deep-link + onUnlocked all check canViewDues | PASS |
| N-5 | DataRepository voucher uses ABORT + 5-attempt retry loop | PASS |
| N-6 | CartScreen Apply Store Credit sums voucher balance + customer credit | PASS |
| N-7 | CartScreen CartItemRow uses produceState for current time | PASS |
| N-8 | CartScreen 6 hardcoded contentDescriptions localized | PASS |
| N-9 | CustomItemDialogs 10 touch targets → 48dp | PASS |
| N-10 | CustomItemDialogs editing-preset remember keyed on target.id | PASS |
| N-11 | ProductsScreen chips and batch buttons → 48dp | PASS |
| N-12 | ProductsScreen stockFilter.name → localized label | PASS |
| N-13 | SalesHistoryScreen 4 interactive elements → 48dp | PASS |
| N-14 | SalesHistoryScreen Payment3DBadge filter labels localized | PASS |
| N-15 | SalesReportScreen TrendChart SimpleDateFormat in remember {} | PASS |
| N-16 | SalesReportScreen period chips heightIn(min=48.dp) | PASS |
| N-17 | SalesReportScreen "View product details" contentDescription localized | PASS |
| N-18 | ScannerScreen snackbar messages localized | PASS |
| N-19 | ScannerScreen TabletCameraPipView permission strings localized | PASS |
| N-20 | ScannerScreen Hold/Clear contentDescriptions localized | PASS |
| N-21 | ScannerScreen low-stock uses product.reorderLevel | PASS |
| N-22 | Navigation isSupervisor added to 4 ProductsScreen permission params | PASS |
| N-23 | CustomerDossierSheet qty/items/Bal strings localized | PASS |
| N-24 | CustomerDossierSheet Adjust/Note buttons → 48dp | PASS |
| N-25 | CustomerDossierSheet SettingsDataStore → SettingsViewModel flow | PASS |
| N-26 | CustomersScreen CustomerDataTable + MergeDialog strings localized | PASS |
| N-27 | CustomersScreen Review & Merge button → 48dp | PASS |
| N-28 | LoyaltyScreen spent/Points/Cash value strings localized | PASS |
| N-29 | LoyaltyScreen View Existing button → 48dp | PASS |
| N-30 | LoyaltyScreen tier thresholds validated (silver < gold < platinum) | PASS |
| N-31 | SuppliersScreen hero CTAs → 48dp + Pay Due defaults to first method | PASS |
| N-32 | ManageUsersScreen PermissionsMatrix shows error on save failure | PASS |
| N-33 | ManageUsersScreen RoleSelectorRow heightIn(min=48.dp) | PASS |
| N-34 | SettingsScreen "Thermal" localized | PASS |
| N-35 | DueReminderScheduler → SupervisorJob CoroutineScope | PASS |
| N-36 | BillPdfGenerator buildBillReceiptText() includes item notes | PASS |
| N-37 | InventoryAdjustment.productBarcode indexed + migration | PASS |
| N-38 | SalesHistoryScreen Reset uses draft/apply flow | PASS |
| N-39 | SalesHistoryScreen Custom period chip highlighted | PASS |
| N-40 | SalesHistoryScreen groupSalesByDay SimpleDateFormat hoisted | PASS |
| N-41 | SalesReportScreen export onClick SimpleDateFormat hoisted | PASS |
| N-42 | CartScreen cashier fallback list → string-array resource | PASS |
| N-43 | CartScreen savings display → string resource | PASS |
| N-44 | CartScreen heldCartToastTemplate uses context.getString() | PASS |
| N-45 | POSViewModels redeemVoucherCode calls setStoreCreditApplied once | PASS |
| N-46 | CustomerDossierSheet unknown credit type → localized string | PASS |
| N-47 | CustomersScreen tag display uses localized key-label mapping | PASS |
| N-48 | LoyaltyScreen "pts" suffix → string resource | PASS |
| N-49 | DuesScreen 3 contentDescriptions localized | PASS |
| N-50 | ManageUsersScreen StaffUserCard buttons → size(48.dp) | PASS |
| N-51 | DueReminderScheduler expired one-shots not rescheduled | PASS |
| N-52 | LoyaltySettingsHelper.getSettings() → ViewModel StateFlow | PASS |
| N-53 | AppDaos getAllSalesOnce() ORDER BY timestamp DESC | PASS |
| N-54 | AppDaos duplicate deleteLoyaltyByPhone removed | PASS |
| N-55 | Navigation SalesReport expense/supplier nav checks permission | PASS |
| N-56 | AppEntities 3 cashierName defaults changed to "" | PASS |
| N-57 | DueReminderLog.customerPhone indexed + migration | PASS |
| N-58 | ProductsScreen export onClick SimpleDateFormat hoisted | PASS |

---

## New Issues — Round 6

### HIGH

- [x] **N-1** · `POSViewModels.kt:~1265` — Cash validity guard in `completeSale()` compares `amountPaid.value` against the full `grandTotal.value`, ignoring `storeCreditApplied`. When store credit covers part of the bill and the cashier collects only the remaining cash amount (a correct, valid payment), the guard throws `IllegalStateException` and blocks the sale entirely.
  **Fix:** `val remainingCash = (grandTotal.value - _storeCreditApplied.value).coerceAtLeast(0.0)`. Guard `amountPaid.value < remainingCash` not `< grandTotal.value`.

- [x] **N-2** · `ProductsScreen.kt:860–924, 338–358` — "Excel Tools" chip exposes the Import Excel action with zero permission check. A Cashier with `canEdit = false` can overwrite the entire product database via Excel import — no dialog, no confirmation, no guard.
  **Fix:** Hide or disable the Import `DropdownMenuItem` when `!canEdit`. Show an access-denied Toast if tapped without permission.

- [x] **N-3** · `SalesHistoryScreen.kt:702–703` — `val dayGroupFormatter = remember { SimpleDateFormat(...) }` is declared inside the `else` branch of a conditional. Calling `remember {}` inside a conditional violates Compose's calling-order invariant; on transitions between an empty and non-empty list the slot table shifts, causing stale-state reuse or a runtime exception.
  **Fix:** Hoist `dayGroupFormatter` to the top of `SalesHistoryScreen`, alongside `dateFormat` at line ~200.

- [x] **N-4** · `DuesScreen.kt:149–153` — `collectAsStateWithLifecycle()` (a `@Composable` function) is called inside a `?.` safe-call chain: `settingsViewModel?.shopName?.collectAsStateWithLifecycle(...)?.value`. A composable inside a nullable chain is a conditional composable invocation — violates Compose rules and can cause slot table mismatches producing wrong state or runtime crashes. The same pattern repeats for `storeAddress`, `storePhone`, `invoiceSettingsData`, and `appLanguage` on adjacent lines.
  **Fix:** Call unconditionally for each flow: `val shopName by (settingsViewModel?.shopName ?: flowOf("")).collectAsStateWithLifecycle("")`. Repeat for all 5 occurrences.

- [x] **N-5** · `LoyaltyScreen.kt:1001` — Quick-percentage redemption chips compute `(member.pointsBalance * pct / 100)` where all operands are `Int`. Integer division means `pct / 100 = 0` for pct = 25, 50, or 75. Every quick-percent button always sets the redemption field to "0 points" regardless of balance.
  **Fix:** `val pts = (member.pointsBalance * pct / 100.0).toInt().coerceAtLeast(0)`

- [x] **N-6** · `SalesReportScreen.kt:537–538` — `effectiveNetProfit` subtracts `data.totalDiscount` and `loyaltyDiscount` from `data.totalProfit`. If `totalProfit` in `SalesViewModel.buildReport()` is already computed as post-discount revenue minus COGS (i.e., discounts already netted), this double-subtracts discounts and understates profit reported to the business owner.
  **Fix:** Audit `SalesViewModel.buildReport()`. If `totalProfit = (revenue - discount) - COGS`, remove the `- data.totalDiscount - loyaltyDiscount` terms from `effectiveNetProfit`. If `totalProfit = grossRevenue - COGS`, the subtraction is correct — rename `totalProfit` to `grossProfit` in the ViewModel and its callers to make the convention clear.

- [x] **N-7** · `SalesReportScreen.kt:203–226` — `rememberLauncherForActivityResult` captures `rangeLabel` (a `val`, not a `State<>`) at registration. If the user changes the date range while the system file picker is open, the export embeds the stale label in the spreadsheet filename and header row.
  **Fix:** `val currentRangeLabel by rememberUpdatedState(rangeLabel)` at the top of the composable. Use `currentRangeLabel` inside the launcher callback instead of `rangeLabel`.

- [x] **N-8** · `CustomersScreen.kt:1596–1619` — `exportCustomerCsv()` is a plain (non-`suspend`) function that opens a `FileWriter` and iterates all customers on the calling (main) thread. On stores with thousands of customers this blocks the UI thread.
  **Fix:** Wrap the body in `withContext(Dispatchers.IO)` and make it `suspend`, or move to `CustomerViewModel` as a `viewModelScope.launch(Dispatchers.IO)` call.

- [x] **N-9** · `ScannerScreen.kt:~3864–3872` — `StockCheckNotFoundDialog` dismiss `IconButton` has `Modifier.size(28.dp)` — 20dp below the 48dp minimum.
  **Fix:** Remove the explicit `size` modifier (default `IconButton` is 48dp) or replace with `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`.

- [x] **N-10** · `AppDaos.kt:71, 75, 181, 296` — Four DAO insert methods use `OnConflictStrategy.REPLACE`:
  - `insertSale` (L71) + `insertSaleItems` (L75): a collision would cascade-delete all historical line items and silently re-insert.
  - `insertDuePayment` (L181): a collision silently overwrites a payment record — permanent ledger corruption.
  - `insertUser` (L296): a collision deletes the existing user row including their PIN hash — a silent credential reset.
  **Fix:** Change all four to `OnConflictStrategy.ABORT`. Catch `SQLiteConstraintException` at each call site and surface a meaningful error to the UI.

---

### MEDIUM

- [x] **N-11** · `CartScreen.kt:1361, 1415, 3103` — Three touch targets below 48dp: "Remove Store Credit" `OutlinedButton` 30dp (L1361), "Apply Store Credit" `Button` 32dp (L1415), quantity counter badge `Surface` 38dp (L3103).
  **Fix:** All three to `Modifier.heightIn(min = 48.dp)`.

- [x] **N-12** · `CartScreen.kt:1593, 1700` — `remember { Animatable(1f) }` inside `availablePaymentMethods.forEach { method ->` (L1593) and `listOf("Visa",...).forEach { type ->` (L1700) without a key. When the list changes (e.g., Store Credit added after phone entry), Animatable instances shift and each chip inherits the wrong animation state.
  **Fix:** `remember(method) { Animatable(1f) }` and `remember(type) { Animatable(1f) }`.

- [x] **N-13** · `POSViewModels.kt:542` + `CartScreen.kt:1978` — `getLoyaltySettings()` reads SharedPreferences synchronously on the main thread in two places: inside `setLoyaltyDiscount()` (called from a click lambda) and directly inside a click lambda at CartScreen:1978. On a cold device start this can block the UI thread long enough to drop frames. (A similar issue was N-52 in Round 5 for LoyaltyScreen; this is a separate occurrence in the cart layer.)
  **Fix:** Expose loyalty settings as a `StateFlow` in `CartViewModel` backed by a repository flow. Collect with `collectAsStateWithLifecycle()` in CartScreen and pass the collected value to `setLoyaltyDiscount`.

- [x] **N-14** · `POSViewModels.kt:929–931` + `CartScreen.kt:331` — `changeAmount` StateFlow uses `(amountPaid - grandTotal)` ignoring `storeCreditApplied` — formula is incorrect. Additionally, the `changeAmt` collected at CartScreen:331 is never referenced anywhere else in the file (dead code); the UI correctly uses a locally-computed `effectiveChangeAmt` instead.
  **Fix:** Either (a) delete `changeAmount` from ViewModel and remove the dead `collectAsStateWithLifecycle` call, or (b) fix the formula to `(amountPaid - (grandTotal - storeCreditApplied).coerceAtLeast(0.0)).coerceAtLeast(0.0)` and use it as the single source of truth, removing the inline duplicate.

- [x] **N-15** · `ProductsScreen.kt:788` — Cart action-bar capsule `Surface` has `Modifier.height(36.dp)` — 12dp below the 48dp minimum.
  **Fix:** `Modifier.height(48.dp)` and adjust internal vertical padding accordingly.

- [x] **N-16** · `ProductsScreen.kt:927–955` — Batch Select chip is always visible and activatable, including for Cashiers who have neither `canEdit` nor `canDelete`. They enter batch-selection mode and see correctly-disabled batch action buttons — confusing UX that implies a feature they cannot use.
  **Fix:** Hide the Batch Select chip entirely when `!canEdit && !canDelete`.

- [x] **N-17** · `SalesHistoryScreen.kt:933–944` — `historyCurrentDayEndUtcMillis` computed with `remember { }` and no key; never refreshes after midnight. Future dates remain blocked by the previous day's cutoff if the app stays open overnight.
  **Fix:** `remember(now) { ... }` using the `now` value from the `produceState` block already present at line ~145.

- [x] **N-18** · `SalesHistoryScreen.kt:1441–1447` (`Payment3DBadge` composable) — `Text(paymentMethod)` renders the raw stored English key ("Store Credit", "Split", "bKash", etc.) directly as visible text to Bengali users. The filter sheet at lines 1012–1030 correctly localises these with a `when` mapping.
  **Fix:** Apply the same `when(paymentMethod) { "Store Credit" -> stringResource(R.string.payment_store_credit) ... }` mapping inside `Payment3DBadge`.

- [x] **N-19** · `SalesHistoryScreen.kt:702` — Even after the N-3 hoist fix, `remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }` has no locale key. Day-name group headers ("Saturday" vs "শনিবার") will not update if the device locale changes while the app is in the foreground.
  **Fix:** `remember(LocalConfiguration.current.locales[0]) { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }`.

- [x] **N-20** · `SalesReportScreen.kt:467–479` — `currentDayEndUtcMillis` computed with `remember { }` and no key; never refreshes after midnight — same class of bug as N-17.
  **Fix:** `remember(now) { ... }` using the report screen's `now` `produceState` value.

- [x] **N-21** · `SalesReportScreen.kt:1255–1260` — Hour-axis labels in the canvas `TrendChart` are hardcoded English 12-hour format: `"12A"`, `"12P"`, `"${index}A"`, `"${index-12}P"`. These labels are drawn via `drawText()` inside a `Canvas` — `stringResource` is not available there. Bengali locale uses 12-hour but with Bengali digit expectations.
  **Fix:** Derive labels from `SimpleDateFormat("ha", Locale.getDefault()).format(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, index) }.time)`, or at minimum format using `Locale.getDefault()` and pass through `toBanglaDigits()` / `toEnglishDigits()`.

- [x] **N-22** · `ScannerScreen.kt` — Multiple accessibility issues in tablet panels:
  - **6 hardcoded English contentDescriptions:** "Decrease" (L2151), "Increase" (L2183), "Remove" (L2197), "Decrease stock" (L3415), "Increase stock" (L3447), "Dismiss" (L3870). TalkBack on Bengali Android reads these as English words.
  - **Touch targets below 48dp:** tablet cart stepper ±/delete 36dp (L2147, 2179, 2193), portrait Hold/Clear 32dp (L1068, 1080), landscape Hold/Clear 38dp (L1362, 1382), PIP torch/pause 36dp (L1867, 1877).
  **Fix:** All 6 contentDescriptions → `stringResource(R.string.cd_...)`. All touch targets → `Modifier.size(44.dp)` minimum for dense tablet panels, or `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)` where layout permits.

- [x] **N-23** · `LoyaltyScreen.kt:662–676` — `FilterChipItem` `Surface` has only `padding(horizontal = 10.dp, vertical = 6.dp)` with no `minHeight`; rendered height ~30dp at default text scale — 18dp below minimum.
  **Fix:** Add `Modifier.defaultMinSize(minHeight = 48.dp)` to the `Surface`.

- [x] **N-24** · `LoyaltyScreen.kt:1304–1311` — `LoyaltySettingsDialog` fields initialized with `remember { mutableStateOf(currentSettings.xxx) }` without `currentSettings` as a key. If settings change externally while the dialog is open, all fields retain their stale opening values.
  **Fix:** `remember(currentSettings) { mutableStateOf(currentSettings.xxx) }` for every field in the dialog.

- [x] **N-25** · `LoyaltyScreen.kt:376–388` — `(context.applicationContext as? TechyfiPOSApplication)?.repository?.getCustomerByPhone(member.phone)` is called inside a `rememberCoroutineScope` launch directly in a composable, bypassing the ViewModel layer. If the application cast fails, a synthetic `Customer` with `0` spend/orders is passed to `onStartSaleForCustomer`, silently corrupting defaults for the new sale.
  **Fix:** Add `fun loadCustomerByPhone(phone: String)` to `CustomerViewModel`. Call it from the composable and observe the result as a `StateFlow`.

- [x] **N-26** · `CustomersScreen.kt:1441–1521` — `CustomerAddEditDialog` declares `var email` state and passes it to `onSave`, but there is no `OutlinedTextField` for email in the dialog body. Users have no way to view or set a customer's email address through the UI.
  **Fix:** Add an email `OutlinedTextField` between the name and address fields.

- [x] **N-27** · `CustomersScreen.kt:940, 963` — `Checkbox(modifier = Modifier.size(32.dp))` in both the table header and each data row is 32dp — 16dp below the minimum.
  **Fix:** Remove the explicit size modifier (Material3 Checkbox default is 48dp) or change to `Modifier.size(48.dp)`.

- [x] **N-28** · `SuppliersScreen.kt:508, 539, 577` — Three segmented filter tabs use `Modifier.defaultMinSize(minHeight = 44.dp)` — 4dp below minimum.
  **Fix:** `Modifier.defaultMinSize(minHeight = 48.dp)` on all three tabs.

- [x] **N-29** · `SuppliersScreen.kt:1251–1266` — Supplier due payment dialog accepts any positive amount with no upper bound. If `amount > supplier.totalDue`, the supplier's balance goes negative — a phantom credit that has no real basis.
  **Fix:** Cap the amount field at `supplier.totalDue` in validation; show an inline error or auto-correct the value. Disable the Confirm button while invalid.

- [x] **N-30** · `ManageUsersScreen.kt:339–353` — "Permissions" `OutlinedButton` in `StaffUserCard` uses `Modifier.defaultMinSize(minHeight = 40.dp)` — 8dp below minimum.
  **Fix:** `Modifier.defaultMinSize(minHeight = 48.dp)`.

- [x] **N-31** · `CustomerDossierSheet.kt:629–644` — NOTES tab renders each note as static `Text` with no delete or edit action. A note can never be removed from the UI once added.
  **Fix:** Add a trailing delete `IconButton` per note item. Back the delete action with a method on `CustomerViewModel` that removes the specific entry and persists the updated notes.

- [x] **N-32** · `CustomerDossierSheet.kt:630` — Notes are stored as a single concatenated string split by `"\n\n"`. A note containing a single newline will merge with the next note; a note containing `"\n\n"` will appear split into two.
  **Fix:** Short-term: use a delimiter that cannot appear in user text (e.g., Unicode unit separator `\u001F`). Long-term: add a `customer_notes` table with `customerPhone`, `text`, `category`, `timestamp` columns.

- [x] **N-33** · `AppDaos.kt:133–145` — `getTopProductsForCustomer` groups by `(si.productBarcode, si.productName)`. A renamed product generates two separate groups in the customer's purchase history, inflating purchase count and splitting spend across two rows.
  **Fix:** Group by `si.productBarcode` only; use `MAX(si.productName)` (or a subquery against the current `products` table) for the display name.

---

### LOW

- [x] **N-34** · `CartScreen.kt:2058–2064` — Cash tender quick-add chip labels display `"+৳${inc.toInt()}"` using ASCII digits, bypassing the app-wide `formatTaka()` / `toBanglaDigits()` pipeline. Bengali-locale users see inconsistent numeral styles in these chips vs the rest of the checkout screen.
  **Fix:** `text = "+${formatTaka(inc)}"`.

- [x] **N-35** · `POSViewModels.kt:290` — `cashierName` StateFlow emits the hardcoded string `"Admin"` when no cashier is configured. This English string is written into sale records and receipt PDFs, the same misleading attribution addressed in PA-2 for DataRepository in Round 5.
  **Fix:** Return `""` from the else branch and let the display layer show a localized placeholder (e.g., `stringResource(R.string.cashier_unassigned)`).

- [x] **N-36** · `POSViewModels.kt:702` — Held-cart fallback title `"Cart #${System.currentTimeMillis() % 10000}"` is a hardcoded English string stored in the DB and displayed in `HeldCartsBottomSheet`.
  **Fix:** Accept a `Context` in `holdCurrentCart()` (or a pre-formatted string from the CartScreen call site) and use `context.getString(R.string.held_cart_fallback_title, System.currentTimeMillis() % 10000)`.

- [x] **N-37** · `CartScreen.kt:1056, 3110` — `String.format(Locale.US, "%.2f", quantity)` bypasses the `toEnglishDigits()` / `toBanglaDigits()` pipeline used throughout the rest of the app. Bengali-locale users see Arabic digits in the checkout summary badge and in-cart item quantity display.
  **Fix:** Use the app's locale-aware quantity-formatting helper (or `formatTaka` for currency values), then pass through the digit-conversion pipeline.

- [x] **N-38** · `ProductsScreen.kt:352` — Excel import error fallback: `e.localizedMessage ?: "File could not be parsed"` — bare English literal.
  **Fix:** `e.localizedMessage ?: context.getString(R.string.products_import_parse_error)`.

- [x] **N-39** · `SalesReportScreen.kt:822, 832, 842` — Emoji prepended to localised period labels via string concatenation: `"🔄 ${stringResource(R.string.sales_report_period_week)}"`. Translators cannot control the emoji or its position relative to the label; in RTL layouts the ordering is reversed.
  **Fix:** Move the emoji inside the string resource: `<string name="sales_report_empty_week">🔄 %1$s</string>` with the period label as the format arg, so translators control both.

- [x] **N-40** · `ScannerScreen.kt:815, 924, 927` — `"All"` is used as a hardcoded sentinel value for the category filter. A product whose category is stored as the literal string `"All"` in the DB would be treated as "show everything" rather than filtered to that category.
  **Fix:** Use `null` as the sentinel (`selectedCategory: String?`). Render the "All" option as an explicit chip outside the dynamic categories list; remove all `== "All"` string comparisons.

- [x] **N-41** · `CustomersScreen.kt:1259` — `"⭐ ${customer.loyaltyPoints} pts"` — the "pts" suffix is a hardcoded English string in the customer card footer.
  **Fix:** `stringResource(R.string.customer_pts_display, customer.loyaltyPoints)`.

- [x] **N-42** · `CustomersScreen.kt:1266–1267` — `"🏷️ ${customer.tag}"` shows the raw DB key ("Retail", "Wholesale", etc.) in the customer card footer. `getLocalizedCustomerTag()` exists and is used elsewhere.
  **Fix:** `"🏷️ ${getLocalizedCustomerTag(customer.tag)}"`.

- [x] **N-43** · `CustomerDossierSheet.kt:245–251` — `LinearProgressIndicator` modifier chain: `.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).padding(top = 4.dp)`. The `.padding(top = 4.dp)` applied after `.clip()` draws outside the clip region — the effective visible bar height becomes 1dp.
  **Fix:** Move `.padding(top = 4.dp)` before `.height(5.dp)`, or apply it on the enclosing `Column` item instead.

- [x] **N-44** · `CustomerDossierSheet.kt:648–687` — "Due Ledger" tab lists only `dossier.duePayments` (credit entries). The debit side (charges, invoices that created the due) is never shown. A complete ledger requires both sides interleaved by timestamp.
  **Fix:** Include debit charge entries and render them with a distinct colour/icon from payments. Sort all entries by timestamp descending.

- [x] **N-45** · `LoyaltyScreen.kt:1152–1159` — `LoyaltyAdjustPointsDialog` places "Redeem Points" in the `dismissButton` slot (visually de-emphasised, trailing position) and "Add Points" in the `confirmButton` slot (primary, leading). A user intending to redeem may accidentally add points.
  **Fix:** Place both actions in `confirmButton` as a `Row` with equal weight and clear labelling, or use a custom dialog layout.

- [x] **N-46** · `ManageUsersScreen.kt:600` — `val disc = editDiscount.toDoubleOrNull() ?: 10.0` silently resets the discount to 10% when the field is blank or non-numeric, with no error shown to the user.
  **Fix:** Validate before saving. Show an inline error `Text` when the field is blank or out of range; disable the Save button while invalid.

- [x] **N-47** · `SuppliersScreen.kt:148` — `remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }` has no locale key. Formatted dates will not update if the device locale changes at runtime.
  **Fix:** `remember(Locale.getDefault()) { SimpleDateFormat(..., Locale.getDefault()) }`.

- [x] **N-48** · `AppDaos.kt:145` — `getTopProductsForCustomer` is hard-limited to 10 items. The dossier "Top Products" tab shows at most 10 products with no "load more" affordance or indication that the list is truncated.
  **Fix:** Add a "View all" option that opens a full scrollable product-history sheet, or increase the limit to 25.

- [x] **N-49** · `AppEntities.kt` / `AppDatabase.kt` — The `customers` table has no index on `name`. Customer search and sort-by-name queries perform a full table scan.
  **Fix:** Add `Index(value = ["name"])` to the `@Entity(tableName = "customers")` annotation. Add `CREATE INDEX IF NOT EXISTS index_customers_name ON customers(name)` to the next migration.
