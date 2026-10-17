# Techyfi POS — Fourth Pass Fix List
Generated: 2026-09-21 | Verification of AUDIT_FIXES_3.md batch
37 items: 7 partially-applied fixes + 1 High + 13 Medium + 16 Low.

---

## HOW TO USE THIS FILE
Same as previous — work in order, mark [x] when done.
All paths are relative to `app/src/main/java/com/example/techyfipos/`.

---

## VERIFICATION STATUS — AUDIT_FIXES_3.md

| # | Item | Result |
|---|------|--------|
| H3-1 | Store Credit banner includes voucher balance | PASS |
| H3-2 | isSplitValid only compares expectedSplitTotal | PASS |
| H3-3 | ManageUsers nav guard — Owners only | PASS |
| H3-4 | Deep link handler checks permissions per route | PASS |
| H3-5 | LoyaltyScreen onStartSale looks up full Customer | PASS |
| H3-6 | CustomerDossierSheet reads store name from settings | PARTIAL |
| M3-1 | ProductsScreen hasActiveFilters includes dateFilter/sortOption | PASS |
| M3-2 | CustomersScreen bulk WhatsApp disabled for >1 selection | PASS |
| M3-3 | CSV export escapes double-quotes | PASS |
| M3-4 | CustomerDataTable buttons 48dp | PASS |
| M3-5 | CustomerCard buttons 48dp | PASS |
| M3-6 | CustomerCard Checkbox 48dp | PASS |
| M3-7 | LoyaltyScreen back button 48dp | PASS |
| M3-8 | CompactLoyaltyRow three-dot menu 48dp | PARTIAL |
| M3-9 | LoyaltyAuditLedgerSheet buttons 48dp | PASS |
| M3-10 | SuppliersScreen uses rememberCoroutineScope | PASS |
| M3-11 | ManageUsersScreen dead block removed | PASS |
| L3-1 | CartScreen remaining strings localized | PASS |
| L3-2 | CustomItemDialogs remaining strings localized | PASS |
| L3-3 | SalesHistoryScreen strings localized | PASS |
| L3-4 | SalesReportScreen panel strings localized | PASS |
| L3-5 | ProductsScreen contentDescriptions / count localized | PASS |
| L3-6 | ManageUsersScreen strings localized | PASS |
| L3-7 | getAllPastCustomerContacts() deleted | PASS |
| L3-8 | DueReminderScheduler notification strings localized | PARTIAL |
| L3-9 | CustomersScreen strings / contentDescriptions localized | PARTIAL |
| L3-10 | LoyaltyScreen dialog strings localized | PARTIAL |
| L3-11 | SuppliersScreen strings and back button fixed | PARTIAL |
| L3-12 | CustomerDossierSheet strings and height cap fixed | PARTIAL |

---

## PART A — PARTIALLY-APPLIED FIXES

- [x] **PA-1** `ui/screens/CustomerDossierSheet.kt:811`
  **H3-6 residual — fallback store name still hardcoded**
  `configuredStoreName.ifBlank { "TECHYFI RETAIL POS" }` — the fallback is still a hardcoded English literal. If a store has not set a name in Settings, the hardcoded string appears on reprinted receipts.
  FIX: Replace with `configuredStoreName.ifBlank { stringResource(R.string.default_store_name) }` (or `context.getString()`). Add `R.string.default_store_name = "Techyfi POS"` to `strings.xml`.

- [x] **PA-2** `ui/screens/LoyaltyScreen.kt:804`
  **M3-8 residual — contentDescription "Options" still hardcoded**
  The CompactLoyaltyRow three-dot menu had its size fixed to 48dp, but `contentDescription = "Options"` at line 804 was not localized.
  FIX: Replace with `contentDescription = stringResource(R.string.options_cd)`.

- [x] **PA-3** `utils/DueReminderScheduler.kt:30`
  **L3-8 residual — dead `CHANNEL_NAME` constant still exported**
  `const val CHANNEL_NAME = "Due Payment Reminders"` is no longer used (channel creation now uses `context.getString()`), but the constant is still exported. It could mislead future callers into using the English string directly.
  FIX: Delete the `CHANNEL_NAME` constant. Confirm zero external references first.

- [x] **PA-4** `ui/screens/CustomersScreen.kt:258, 412, 1053, 1065`
  **L3-9 residual — 4 hardcoded contentDescriptions remain**
  - L258: `contentDescription = "Sort"`
  - L412: `contentDescription = "Clear"`
  - L1053: `contentDescription = "Start Sale"`
  - L1065: `contentDescription = "Customer Profile"`
  FIX: Add four string entries to `strings.xml` and replace with `stringResource()`.

- [x] **PA-5** `ui/screens/LoyaltyScreen.kt:590, 804, 1016`
  **L3-10 residual — contentDescriptions and "Max" label hardcoded**
  - L590: `contentDescription = "Clear search"` — hardcoded
  - L804: `contentDescription = "Options"` — (same as PA-2; fix together)
  - L1016: `"Max (${member.pointsBalance})"` in QuickRedeemDialog — "Max" is hardcoded
  FIX: Add `R.string.clear_search_cd`, `R.string.options_cd`, `R.string.loyalty_max_label` (format string with `%1$d`) to `strings.xml`. Replace all three.

- [x] **PA-6** `ui/screens/SuppliersScreen.kt:1222`
  **L3-11 residual — payment method list in Pay Due dialog still hardcoded**
  `DataRepository.DEFAULT_PAYMENT_METHODS.forEach { ... }` uses a compile-time static list. User-configured payment methods set in Settings do not appear here.
  FIX: Read payment methods from the ViewModel/settings flow (the same source CartScreen uses) and iterate that instead.

- [x] **PA-7** `ui/screens/CustomerDossierSheet.kt:196, 202, 227, 233, 928, 935, 961`
  **L3-12 residual — hardcoded strings remain in CustomerDossierSheet**
  - L196: `contentDescription = "Edit Customer"` — not localized
  - L202: `contentDescription = "Close"` — not localized
  - L227: `"Due:"` label on credit-limit progress bar — not localized
  - L233: `"Limit:"` label on credit-limit progress bar — not localized
  - L928: `"Paid (${sale.paymentMethod}):"` in receipt slip — not localized
  - L935: `"Due Balance:"` in receipt slip — not localized
  - L961: Full share text `"Invoice #${sale.id}\nCustomer: …\nTotal: …\nThank you for shopping with us!"` — all hardcoded English
  FIX: Add all to `strings.xml` with format args where needed. For L928/L935, use `stringResource()` or `context.getString()`. For L961, build via a multi-line format string resource.

---

## PART B — NEW ISSUES

### HIGH

- [x] **N-1** `ui/viewmodel/POSViewModels.kt:545`
  **Loyalty discount cap is inflated when tax is inclusive — over-discounting possible**
  `val payableBeforeLoyalty = (subtotal.value - discountValue.value + taxValue.value).coerceAtLeast(0.0)` unconditionally adds `taxValue`. When `taxInclusive = true`, the tax is already embedded in the subtotal — adding it again inflates the cap. Users can redeem more loyalty cash-value than the actual payable amount. The `completeSale()` function at line 1246 correctly gates on `!settings.taxInclusive`; this function must do the same.
  FIX:
  ```kotlin
  val payableBeforeLoyalty = if (settings.taxInclusive) {
      (subtotal.value - discountValue.value).coerceAtLeast(0.0)
  } else {
      (subtotal.value - discountValue.value + taxValue.value).coerceAtLeast(0.0)
  }
  ```

---

### MEDIUM

- [x] **N-2** `ui/screens/CartScreen.kt:1184–1185`
  **Split payment auto-seed uses `grandTotal` instead of `remainingToPay` when store credit is applied**
  When store credit is already applied and the cashier switches to split mode, the initial Cash/bKash leg amounts are seeded from `grandTotal`, not `grandTotal - storeCreditApplied`. The checkout button stays disabled until the cashier manually corrects the entries, causing silent UX confusion.
  FIX: Seed the first leg from `remainingToPay` (already computed in scope): `val firstLeg = if (remainingToPay >= 2.0) (remainingToPay / 2).coerceAtLeast(1.0) else remainingToPay`.

- [x] **N-3** `ui/screens/CartScreen.kt:1413–1418`
  **`SplitPaymentBuilder` shows wrong "remaining" total when store credit is applied**
  `SplitPaymentBuilder(grandTotal = grandTotal, ...)` passes `grandTotal` as the target. The builder's internal `remaining = grandTotal - totalCollected` shows the cashier they are short by `storeCreditApplied`, even when the split is actually complete. Same root cause as N-2.
  FIX: Pass `(grandTotal - storeCreditApplied).coerceAtLeast(0.0)` when `storeCreditApplied > 0.0`:
  ```kotlin
  SplitPaymentBuilder(
      grandTotal = (grandTotal - storeCreditApplied).coerceAtLeast(0.0),
      ...
  )
  ```

- [x] **N-4** `ui/screens/CartScreen.kt:3163, 3218, 3246`
  **Cart row quantity and remove buttons 38dp — below 48dp minimum touch target**
  The subtract (➖), add (➕), and remove (🗑️) `Surface` buttons on every cart item row use `Modifier.size(38.dp)`. These are the most frequently tapped interactive elements in the checkout flow.
  FIX: Change all three to `Modifier.size(48.dp)`.

- [x] **N-5** `ui/screens/CartScreen.kt:1051`
  **Cashier dropdown icon button 38dp — below 48dp minimum touch target**
  FIX: Change `Modifier.size(38.dp)` to `Modifier.size(48.dp)`.

- [x] **N-6** `ui/screens/ProductsScreen.kt:740, 1273, 1332, 1404, 1425, 1488, 1504`
  **7 touch targets below 48dp minimum in ProductsScreen**
  - L740: Inventory-alerts bell `IconButton` — `size(38.dp)`
  - L1273: Select-all `IconButton` in batch bar — `size(32.dp)`
  - L1332: Close-batch `IconButton` — `size(28.dp)`
  - L1404: Clear-search `IconButton` — `size(24.dp)`
  - L1425: Scanner-toggle `Surface` — `size(30.dp)`
  - L1488: Grid-view `Surface` — `size(36.dp)`
  - L1504: List-view `Surface` — `size(36.dp)`
  FIX: Change all to `Modifier.size(48.dp)`.

- [x] **N-7** `ui/screens/ProductsScreen.kt:408–421`
  **Stale timestamp in `visibleProducts` remember block — date filters wrong after midnight**
  `val now = System.currentTimeMillis()` is captured once inside `remember(productsList, stockFilter, dateFilter, …)`. The reactive `nowTimestamp` from `produceState` (line 228) is not included as a remember key. After midnight, EXPIRING_SOON and EXPIRED filters use a stale date until a DB-triggered recomposition fires.
  FIX: Add `nowTimestamp` to the remember key list on line 408.

- [x] **N-8** `ui/screens/SalesHistoryScreen.kt:391, 1198`
  **Two touch targets below 48dp in SalesHistoryScreen**
  - L391: Clear-search `IconButton` — `size(20.dp)`; also `contentDescription = "Clear"` hardcoded at L395
  - L1198: Collapse/expand daily-summary `Surface` — `size(24.dp)`
  FIX: Change both to `Modifier.size(48.dp)`. Localize the contentDescription at L395.

- [x] **N-9** `ui/screens/SalesHistoryScreen.kt:998`
  **Payment-method filter list missing "Store Credit" and split variants — filters silently drop those sales**
  The filter bottom sheet's payment method list is `listOf("Cash", "bKash", "Nagad", "Rocket", "Card", "Due")`. Sales settled with Store Credit or split payment combinations are not filterable, and users receive no results with no explanation.
  FIX: Include `"Store Credit"` and any split-payment method identifiers in the list. Ideally read from the same shared source CartScreen uses so the list stays in sync.

- [x] **N-10** `Navigation.kt:1083–1099`
  **Lock screen `onUnlocked` navigates to restricted routes without checking permissions**
  The `when` block inside `LockScreen`'s `onUnlocked` lambda maps the pending destination directly to nav routes (Expenses, Suppliers, SalesReport, Settings, RegisterClose) without consulting the authenticated user's role. The per-screen entry guards will still reject unauthorized users, but the navigation fires first — resulting in a brief flash of restricted content before the guard pops the stack. The deep-link handler (H3-4) was fixed but this parallel path was not.
  FIX: Replicate the same role-conditional checks from the `LaunchedEffect` at lines 366–401 inside `onUnlocked`. Navigate to Scanner if the user lacks permission.

- [x] **N-11** `ui/screens/CustomerDossierSheet.kt:396–410`
  **Custom tab row touch target ~28–30dp — below 48dp minimum**
  The tab row uses `.clickable { selectedTab = tab }.padding(horizontal = 10.dp, vertical = 6.dp)`. The resulting touch height is approximately 28–30dp (text height + vertical padding), well below the 48dp minimum.
  FIX: Add `Modifier.heightIn(min = 48.dp)` to each tab `Box`, or switch to a standard `TabRow` / `ScrollableTabRow`.

- [x] **N-12** `ui/screens/CustomerDossierSheet.kt:459, 515, 652, 694`
  **`SimpleDateFormat` constructed inside composable lambdas — recreated on every recomposition**
  Four instances of `SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())` are created directly inside `when (selectedTab)` branches with no `remember {}`. Each recomposition allocates a new formatter.
  FIX: Hoist each into `val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }` at composable scope.

- [x] **N-13** `ui/screens/ProductsScreen.kt:2685–2686, 2773, 2810–2811`
  **Hardcoded inventory adjustment reasons written permanently to the database**
  - L2685: `reason = reason ?: "Restock"`
  - L2686: `note = note ?: "Initial stock recorded upon product creation"`
  - L2773: `reason = reason ?: if (diff > 0) "Restock" else "Discrepancy"`
  - L2810: `reason = "Restock"`; L2811: `note = "Quick restock from Inventory Alerts"`
  These strings appear in audit-trail records visible to users and cannot be changed post-write. They will remain English in a Bengali-locale installation.
  FIX: Pass these strings from call sites using `context.getString(R.string.xxx)` so they come from string resources.

---

### LOW

- [x] **N-14** `ui/screens/CartScreen.kt:3082, 3097`
  **Expired product warning badges use hardcoded English**
  - L3082: `text = "❌ Expired (${item.product.expiryDate})"`
  - L3097: `text = "⚠️ Exp: ${daysLeft}d left (${item.product.expiryDate})"`
  FIX: Add format strings to `strings.xml` and use `stringResource()`.

- [x] **N-15** `ui/dialogs/CustomItemDialogs.kt:208, 258`
  **Auto-applied freebie note is hardcoded English — stored in the database and printed on receipts**
  `if (note.isBlank()) note = "Customer Complimentary Gift"`. This default is set programmatically (not inside a `Text()`), so it must use `context.getString(R.string.xxx)` passed from the call site.
  FIX: Add `R.string.custom_item_complimentary_note` to `strings.xml`. Inject it at the call site via a parameter or pass `context` to the function.

- [x] **N-16** `ui/screens/CartScreen.kt:721`
  **Quick-sale preset note is hardcoded — stored in the database and may appear on receipts**
  `cartViewModel.addCustomItem(preset.name, preset.defaultPrice, 1.0, "Quick Sale", preset.defaultUnit)` — "Quick Sale" is stored as the item note.
  FIX: Replace `"Quick Sale"` with `context.getString(R.string.cart_quick_sale_note)`.

- [x] **N-17** `ui/dialogs/CartItemDialogs.kt:117, 152`
  **ManualQuantityDialog increment and preset buttons 36dp — below 48dp**
  `OutlinedButton`/`Surface` elements with `Modifier.height(36.dp)`.
  FIX: Change to `Modifier.height(48.dp)`.

- [x] **N-18** `ui/screens/ProductsScreen.kt:3036–3223`
  **Three bulk-action dialogs entirely unlocalised**
  `BulkPriceUpdateDialog` (lines 3036–3108), `BulkCategoryReassignmentDialog` (3129–3189), and `BulkDeleteConfirmationDialog` (3204–3223) contain zero `stringResource()` calls. All titles, body text, chip labels, field labels, and button text are hardcoded English.
  FIX: Add entries to `strings.xml` for all user-visible strings in each dialog and replace with `stringResource()`.

- [x] **N-19** `ui/screens/ProductsScreen.kt:3342, 3344`
  **Stock adjustment reason list hardcoded — not in string resources**
  `listOf("Restock", "Damage", "Expired", "Discrepancy", "Sample", "Internal Use")` and its default value.
  FIX: Move to `strings.xml` as a string-array or define each as individual string resources. Use `stringArrayResource(R.array.stock_adjustment_reasons)` in the composable.

- [x] **N-20** `ui/screens/ProductsScreen.kt:4434, 4669, 4987`
  **Hardcoded `"Pcs"` unit fallback — three occurrences**
  `"/ ${product.unit.ifBlank { "Pcs" }}"` — the fallback unit is not in string resources.
  FIX: Add `R.string.unit_pcs_default = "Pcs"` and use `stringResource()`.

- [x] **N-21** `ui/screens/SalesHistoryScreen.kt:1736`
  **`"Net after returns"` label hardcoded**
  FIX: Add to `strings.xml` and use `stringResource()`.

- [x] **N-22** `ui/screens/SalesHistoryScreen.kt:1991`
  **`"Voided for Edit"` void reason written permanently to the database — hardcoded English**
  FIX: Use `context.getString(R.string.void_reason_for_edit)` at call site.

- [x] **N-23** `ui/screens/SalesHistoryScreen.kt:1798–1799`
  **Hardcoded `"Dhaka, Bangladesh"` and `"01700000000"` as initial receipt defaults**
  Used as fallback values for shop address and phone when settings have not been configured. These placeholders are passed into receipt PDF generation and will appear literally on printed receipts if settings are not filled in.
  FIX: Use empty string or a localized `stringResource(R.string.receipt_default_address)` / `R.string.receipt_default_phone` placeholder. Alternatively, prompt the user to configure these in Settings before their first receipt is printed.

- [x] **N-24** `Navigation.kt:1297`
  **`onManageUsersClick` allows Supervisors and SYSTEM_MANAGE_STAFF to attempt navigation — entry guard then rejects them**
  `val canManageStaff = isOwner || isSupervisor || activePermissions.contains(SYSTEM_MANAGE_STAFF)`. The ManageUsers entry guard (`val canManageStaff = isOwner`) is narrower. These users see a "Manage Staff" button, tap it, and are immediately rejected with a Toast — a confusing UX loop.
  FIX: Change the SettingsScreen call-site check to `val canManageStaff = isOwner` to match the entry guard. Or adjust the entry guard to also allow Supervisors (with a screen-level fallback as per H3-3 fix pattern).

- [x] **N-25** `Navigation.kt:591, 604, 610–613, 627, 634, 867–877, 969, 975, 1000, 1006, 1037`
  **Tablet sidebar and logout dialog use inline `if (currentLang == "bn") "..." else "..."` instead of `stringResource()`**
  Multiple user-visible strings in the tablet-only sidebar and logout confirmation dialog bypass the string resource system entirely, making future language additions require source changes.
  Affected: user label, logout dialog title/body/buttons, role labels (Owner/Manager/Cashier/Staff), "Lock Terminal", "Keep active session locked", "Sign out of account", and "Lock Terminal" contentDescription.
  FIX: Add all to `strings.xml` and use `stringResource()`. Remove the `if (currentLang == "bn")` conditional checks.

- [x] **N-26** `data/DataRepository.kt:212, 415, 515, 576`
  **`"Admin"` hardcoded as cashier-name fallback in permanent audit records**
  `sessionUser?.name ?: "Admin"` is written into `InventoryAdjustment` and `SaleAuditLog` records. If `sessionUser` is ever null at write time, the resulting audit row shows `"Admin"` and cannot be corrected.
  FIX: If `sessionUser` is null, log a warning or throw — do not silently write a misleading fallback name into permanent audit records.

- [x] **N-27** `ui/screens/CustomerDossierSheet.kt` (see PA-7 for context)
  **Receipt slip uses hardcoded inline `if (isBangla)` instead of string resources**
  Lines 928 and 935 use `if (isBangla) "পরিশোধিত:" else "Paid (${sale.paymentMethod}):"`. The Bangla path bypasses the i18n system entirely; neither branch uses `stringResource`.
  FIX: Add `R.string.receipt_paid_label` and `R.string.receipt_due_balance_label` with format arguments, and replace both branches with `stringResource()`.

- [x] **N-28** `ui/screens/LoyaltyScreen.kt:1662–1665`
  **Tier display names hardcoded in LoyaltyAuditLedgerSheet**
  `"Silver"`, `"Gold"`, `"Platinum"`, `"Max Level (Platinum)"` appear in the tier-progression section and are not localized.
  FIX: Add to `strings.xml` and use `stringResource()`.

- [x] **N-29** `utils/DueReminderScheduler.kt:30` — (same as PA-3, fixable together)
  Delete the `const val CHANNEL_NAME = "Due Payment Reminders"` constant.

---

## SUMMARY
| Category | Count |
|---|---|
| Partially-applied fixes (PA) | 7 |
| New — High | 1 |
| New — Medium | 13 |
| New — Low | 16 |
| **Total** | **37** |
