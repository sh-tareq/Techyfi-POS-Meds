# Techyfi POS — Third Pass Fix List
Generated: 2026-09-19 | Verification of AUDIT_FIXES_2.md batch
29 items: all pass-2 fixes verified + new issues found.

---

## HOW TO USE THIS FILE
Same as previous — work in order, mark [x] when done.
All paths are relative to `app/src/main/java/com/example/techyfipos/`.

---

## VERIFICATION STATUS — All AUDIT_FIXES_2.md items: PASS

| # | Item | Result |
|---|------|--------|
| PA-1 | ProductsScreen "Clear All" includes highMarginOnly | PASS |
| PA-2 | Batch select uses barcode PK | PASS |
| PA-3 | DuesScreen "+ Add Due" 48dp | PASS |
| PA-4 | HistoryRevenueSummaryCard localized; return toasts localized | PASS |
| PA-5 | CartScreen/CustomItemDialogs remaining strings localized | PASS |
| PA-6 | ManageUsersScreen access-denied fallback added | PASS |
| N-1 | Redeem Max passes pointsBalance × pointCashValue | PASS |
| N-2 | Credit override token cleared unconditionally | PASS |
| N-3 | Navigation guards synchronous (no LaunchedEffect race) | PASS |
| N-6 | CustomersScreen toolbar icons 48dp | PASS |
| N-7 | searchCustomerSuggestions fallback uses filtered DAO | PASS |
| N-8 | DueReminderScheduler PendingIntents use different requestCodes | PASS |
| N-9 | CartItemDialogs editableAmount() returns "0" for 0.0 | PASS |
| N-12 | "Enter next item." only shown when onAddAnother != null | PASS |
| N-14 | SalesReportScreen sub-panel strings localized | PASS |
| N-16 | rangeForPeriod accepts now: Long parameter | PASS |
| N-17 | scheduleWorkManagerReminder dead code deleted | PASS |
| N-18 | deleteSale() audit log reordered after stock restore | PASS |

---

## NEW ISSUES

### HIGH

- [x] **H3-1** `ui/screens/CartScreen.kt:1541–1565`
  **Store Credit payment banner shows ৳0 when a voucher is active**
  The banner that shows the cashier the available store credit reads `resolvedCustomer?.storeCredit` alone. When a voucher has been redeemed, the available balance should be `storeCredit + redeemedVoucher?.remainingBalance`, but the voucher portion is never added. The cashier sees ৳0 and may wrongly conclude the customer has no credit.
  FIX: Change the displayed amount to:
  ```kotlin
  val availableCredit = (resolvedCustomer?.storeCredit ?: 0.0) + (redeemedVoucher?.remainingBalance ?: 0.0)
  ```
  Use `availableCredit` wherever the banner currently reads `resolvedCustomer?.storeCredit`.

- [x] **H3-2** `ui/screens/CartScreen.kt:2341–2346`
  **`isSplitValid` allows over-collection when store credit + split are combined**
  The second OR branch `abs(splitCollected - grandTotal) < 0.01` can fire even when `expectedSplitTotal` (grandTotal minus store-credit contribution) is less than `grandTotal`. A cashier can charge the full `grandTotal` in other payment methods while store credit is also applied, collecting more than the sale requires.
  FIX: Remove the loose second OR branch entirely. The only acceptable condition: `abs(splitCollected - expectedSplitTotal) < 0.01`, where `expectedSplitTotal = grandTotal - storeCreditContribution`.

- [x] **H3-3** `Navigation.kt:1079–1085` vs `ui/screens/ManageUsersScreen.kt:51`
  **Supervisor nav guard mismatch — silent navigation dead-end**
  The nav guard for the ManageUsers route passes both Owners and Supervisors through. But the screen itself only renders content when `isOwner == true`. A Supervisor lands on the access-denied screen with no backstack pop, leaving them stuck with no way back via the system back button.
  FIX: Align the nav guard to only admit Owners (remove the `|| isSupervisor` from the ManageUsers guard). If supervisors should eventually have access, also add a `navController.popBackStack()` in the screen's `if (!isOwner)` branch before returning, so back navigation still works.

- [x] **H3-4** `Navigation.kt:362–385`
  **Deep link handler pushes restricted routes without permission checks**
  The destination handler dispatches to Expenses, Suppliers, SalesReport, Settings, and RegisterClose unconditionally. A deep link from a notification, widget, or external app can drop any user (including Cashier) directly into these screens, bypassing all role-based access.
  FIX: Add the same role checks used in the synchronous nav guards before each `navController.navigate(...)` call in the deep link handler. If the user lacks permission, navigate to the Scanner/home route instead.

- [x] **H3-5** `ui/screens/LoyaltyScreen.kt:374`
  **`onStartSale` from Loyalty screen creates a partial Customer object — loyalty points and credit show as 0**
  The `Customer` passed to `onStartSale` is constructed inline with only `phone`, `name`, and `tag` (tier). All other fields — `loyaltyPoints`, `creditLimit`, `storeCredit`, `totalDue`, `totalOrders`, `lifetimeSpent`, `email`, `address` — default to zero or empty. In the cart, this customer will appear to have no redeemable points and no credit limit, even if they actually do.
  FIX: Look up the full `Customer` row by phone number from `CustomerDao` in a coroutine before calling `onStartSale`. Pass the complete entity. Alternatively, pass only the customer's phone and let the cart resolve it through its existing customer-search flow.

- [x] **H3-6** `ui/screens/CustomerDossierSheet.kt:862`
  **Receipt preview always shows "TECHYFI RETAIL POS" regardless of configured store name**
  The store name in reprinted receipts is a hardcoded string literal. If the owner has set a different store name in Settings, it is never reflected on receipts shown from this screen.
  FIX: Read the store name from `SettingsViewModel` or an equivalent DataRepository preference and pass it to the receipt generation call at line 862.

---

### MEDIUM

- [x] **M3-1** `ui/screens/ProductsScreen.kt:1435–1439`
  **Filter active-dot missing `dateFilter` and `sortOption` in outer `hasActiveFilters`**
  The expression driving the filter indicator dot does not include `dateFilter != DateFilter.ALL` or `sortOption != SortOption.DEFAULT`. When only a date filter or non-default sort is active, the dot does not appear — no visual feedback that filters are applied.
  FIX: Add `|| dateFilter != DateFilter.ALL || sortOption != SortOption.DEFAULT` to the outer `hasActiveFilters` expression used by the filter FAB / toolbar icon.

- [x] **M3-2** `ui/screens/CustomersScreen.kt:691–699`
  **Bulk WhatsApp action sends to only the first selected customer — others silently skipped**
  `openWhatsApp(context, selectedPhones.firstOrNull() ?: return)` only opens WhatsApp for one customer. When multiple customers are selected, the rest receive nothing. There is no warning about this limitation.
  FIX: Either (a) loop and launch a WhatsApp intent for each phone, (b) show a phone-picker so the cashier chooses one, or (c) disable bulk WhatsApp (button visible only when exactly one customer is selected) with a tooltip explaining why.

- [x] **M3-3** `ui/screens/CustomersScreen.kt:1576–1578`
  **CSV export does not escape double-quotes in customer names — breaks Excel**
  Names containing `"` (e.g. `O'Brien "Bob"`) are wrapped in `"..."` but the inner `"` is not doubled. The resulting CSV is malformed and parses incorrectly in Excel and LibreOffice.
  FIX: Apply standard CSV escaping: replace each `"` in a field with `""` before wrapping in outer quotes. Example helper:
  ```kotlin
  fun csvEscape(s: String) = "\"${s.replace("\"", "\"\"")}\""
  ```

- [x] **M3-4** `ui/screens/CustomersScreen.kt:1044–1060`
  **CustomerDataTable row action buttons below 48dp minimum touch target**
  Start Sale, WhatsApp, and Profile `IconButton`s use `Modifier.size(38.dp)`.
  FIX: Change all three to `Modifier.size(48.dp)`.

- [x] **M3-5** `ui/screens/CustomersScreen.kt:1211–1223`
  **CustomerCard action buttons below 48dp minimum touch target**
  Start Sale and WhatsApp `IconButton`s use `Modifier.size(40.dp)`.
  FIX: Change to `Modifier.size(48.dp)`.

- [x] **M3-6** `ui/screens/CustomersScreen.kt:1130`
  **CustomerCard selection Checkbox is 28dp — effectively untappable**
  `Modifier.size(28.dp)` makes the checkbox nearly impossible to tap accurately.
  FIX: Change to `Modifier.size(48.dp)` or use a larger touch-target padding with a smaller visual.

- [x] **M3-7** `ui/screens/LoyaltyScreen.kt:112`
  **Back button 44dp — 4dp below minimum touch target**
  FIX: Change `Modifier.size(44.dp)` to `Modifier.size(48.dp)`.

- [x] **M3-8** `ui/screens/LoyaltyScreen.kt:787`
  **CompactLoyaltyRow three-dot menu button 36dp — 12dp below minimum touch target**
  FIX: Change `Modifier.size(36.dp)` to `Modifier.size(48.dp)`.

- [x] **M3-9** `ui/screens/LoyaltyScreen.kt:1566–1569`
  **LoyaltyAuditLedgerSheet Call and Close buttons 42dp — below minimum touch target**
  FIX: Change both `Modifier.size(42.dp)` to `Modifier.size(48.dp)`.

- [x] **M3-10** `ui/screens/SuppliersScreen.kt:128–129`
  **Excel export uses a leaked bare `CoroutineScope` not tied to lifecycle**
  `kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { ... }` has no owner. If the user navigates away during export, the coroutine continues running with captured references and cannot be cancelled.
  FIX: Replace with `val scope = rememberCoroutineScope()` declared at composable scope, and use `scope.launch { ... }` for the export.

- [x] **M3-11** `ui/screens/ManageUsersScreen.kt:105–135`
  **Dead unreachable code — second `if (!isOwner)` block inside Scaffold**
  The early return at line 51–61 already exits when `!isOwner`. The second block inside the Scaffold body is unreachable on every code path.
  FIX: Delete the dead block entirely.

---

### LOW

- [x] **L3-1** `ui/screens/CartScreen.kt:1529, 1726, 1735, 2252`
  **Remaining hardcoded English strings in CartScreen**
  - L1529: `"Store Credit"` payment chip label
  - L1726: `"Customer"` fallback name in suggestion row
  - L1735: `"Due:"` prefix in customer suggestion dropdown
  - L2252: `"% OFF"` savings badge suffix
  FIX: Add four entries to `res/values/strings.xml` and replace with `stringResource(R.string.xxx)`.

- [x] **L3-2** `ui/dialogs/CustomItemDialogs.kt:165, 176, 1403`
  **Hardcoded strings in CustomItemDialogs**
  - L165/176: Badge text and added-to-cart confirmation message
  - L1403: Empty-state placeholder text
  FIX: Add to `strings.xml` and use `stringResource`.

- [x] **L3-3** `ui/screens/SalesHistoryScreen.kt:597, 685, 811–815, 826, 953–984, 1598–1599, 1614–1615`
  **Hardcoded English strings throughout SalesHistoryScreen**
  - L597: `"${filteredSales.size} sales"` count label
  - L685: `"Clear search and filters"` button
  - L811–815: Delete dialog label and placeholder
  - L826: `"Invoice #${id} Deleted"` void reason fallback
  - L953–984: Date picker button and title strings
  - L1598–1599: `"Customer: "` prefix and `"Walk-in"` fallback in SaleCard
  - L1614–1615: `"items"` suffix and `"+N more"` overflow label in SaleCard
  FIX: Add all to `strings.xml` and replace with `stringResource`.

- [x] **L3-4** `ui/screens/SalesReportScreen.kt:715, 1157, 1696–1697, 1761, 1777, 1781, 1810–1811, 1835`
  **Hardcoded strings throughout SalesReportScreen panels**
  - L715: `"Loyalty Points Redeemed"` overview stat label
  - L1157: `"invoices • … units sold"` summary line
  - L1696–1697: `"Top Selling Products"` panel header
  - L1761, 1777, 1781: `"units sold"`, `"BARCODE / SKU"`, `"AVG UNIT PRICE"` column headers
  - L1810–1811, 1835: `"Payment Channel Mix"` and payment-row format string
  FIX: Add to `strings.xml` and use `stringResource`.

- [x] **L3-5** `ui/screens/ProductsScreen.kt:939, 1277, 1283, 1337`
  **Hardcoded contentDescriptions and selected-count label in ProductsScreen**
  - L939, 1277, 1337: contentDescription strings not using `stringResource`
  - L1283: `"${selectedBarcodes.size} selected"` count label hardcoded
  FIX: Add to `strings.xml`.

- [x] **L3-6** `ui/screens/ManageUsersScreen.kt:334, 741`
  **Hardcoded strings in ManageUsersScreen**
  - L334: `"Max Disc: ${user.maxDiscountPercent.toInt()}%"` not localized
  - L741: Raw `user.role` enum name shown directly; `"Close"` contentDescription hardcoded
  FIX: Add format strings to `strings.xml`. Map role enum to localized display name.

- [x] **L3-7** `data/db/AppDaos.kt:116–118`
  **Dead query `getAllPastCustomerContacts()` still exists — full-table-scan, no callers**
  The filtered replacement (`searchPastCustomerContacts`) from N-7 made this obsolete. It is dead code with a full-table-scan risk if accidentally re-invoked.
  FIX: Confirm zero call sites with a project-wide search, then delete the function.

- [x] **L3-8** `utils/DueReminderScheduler.kt:29, 431, 467–489`
  **Notification channel name/description and all notification text are hardcoded English**
  - L29: `CHANNEL_NAME = "Due Payment Reminders"`
  - L431: channel description string
  - L467: notification title
  - L469, 471, 473: notification body variants
  - L489: `"💬 Send WhatsApp"` action button label
  FIX: Use `context.getString(R.string.xxx)` for all. Note: notification channel name/description can only be set at channel creation time — existing channels on user devices won't update automatically, but new installs will be correct.

- [x] **L3-9** `ui/screens/CustomersScreen.kt:159, 321, 384, 785, 821, 1217–1222, 1293, 1374, 1537, 1551`
  **Multiple hardcoded strings and consistency issue in CustomersScreen**
  - L159, L321, L384: contentDescription strings (`"Sort"`, `"More Options"`, `"Add Customer"`)
  - L785: `matchReason = "Phone Collision (${existingCust.phone})"` merge reason
  - L821: `"Customer deleted"` success toast
  - L1217–1222: WhatsApp emoji `Text("💬")` inside `IconButton` with no `contentDescription` — screen readers get nothing
  - L1293: `"Match Reason: ${pair.matchReason}"` label
  - L1374 vs L1430: Tag order differs between BulkAssignTagDialog (`Retail, VIP, Wholesale…`) and CustomerAddEditDialog (`Retail, Wholesale, VIP…`) — VIP and Wholesale swapped; pick one canonical order and apply to both
  - L1537: `"Cannot Delete Customer"` alert title and body
  - L1551: Delete confirmation dialog body text
  FIX: Move all string literals to `strings.xml`. Add `contentDescription` to WhatsApp emoji button. Align tag order to match one canonical source.

- [x] **L3-10** `ui/screens/LoyaltyScreen.kt:941, 1025–1026, 1036, 1051, 1125, 1133, 1228`
  **Hardcoded English strings throughout LoyaltyScreen dialogs**
  - L941: `"Points redemption at counter"` default reason in QuickRedeemDialog
  - L1025–1026: Validation feedback strings (`"✓ Discount: …"`, `"⚠️ Must be between …"`)
  - L1036: `"Reason / Invoice Ref"` label
  - L1051: `"Redeem ${...}"` button text
  - L1125: `"Manual points addition"` fallback reason
  - L1133: `"Manual adjustment deduction"` fallback reason
  - L1228: `"Membership Tier:"` label in LoyaltyAddMemberDialog
  FIX: Add all to `strings.xml` and use `stringResource` / `context.getString`.

- [x] **L3-11** `ui/screens/SuppliersScreen.kt:188, 412, 428, 677, 691–694, 912, 1218`
  **Hardcoded strings and minor issues in SuppliersScreen**
  - L188: Back `IconButton` has no `Modifier.size(48.dp)` — touch target defaults to icon size
  - L412, 428, 677, 691–694: Auth dialog description strings (`"Add Supplier"`, `"Receive Stock"`, `"Record Supplier Payment"`, etc.) hardcoded
  - L912: `phone.ifBlank { "N/A" }` stores the literal `"N/A"` — breaks any future WhatsApp/dial intent
  - L1218: `listOf("Cash", "bKash", "Nagad", "Rocket", "Bank")` payment methods hardcoded and not synced with settings
  FIX: Add size modifier to back button. Add auth strings to `strings.xml`. Change `"N/A"` storage to `""` or `null`. Read payment methods from a shared source (settings/DataRepository).

- [x] **L3-12** `ui/screens/CustomerDossierSheet.kt:366, 412, 710–713, 960, 1072, 1079, 1090, 1097`
  **Hardcoded strings and fixed height constraint in CustomerDossierSheet**
  - L366: `"⚖️ Adjust"` button label
  - L412: `heightIn(max = 290.dp)` caps tab content — wastes space on tablets, may truncate ledger lists on small phones
  - L710–713: `"🔄 Return Refund"`, `"🛒 Checkout Redemption"`, `"⚖️ Supervisor Adjustment"` credit type labels
  - L960: `"Share Receipt"` Intent chooser title
  - L1072: AdjustStoreCreditDialog placeholder text
  - L1079: Error message `"Deduction cannot exceed current credit (${formatTaka(currentCredit)})"`
  - L1090: `"Manual credit adjustment"` default note
  - L1097: `"Add Credit"` / `"Deduct Credit"` button text
  FIX: Move all string literals to `strings.xml`. Consider replacing `heightIn(max = 290.dp)` with a fraction of the screen height or `fillMaxHeight()`.

---

## SUMMARY
| Category | Count |
|---|---|
| High | 6 |
| Medium | 11 |
| Low | 12 |
| **Total** | **29** |
