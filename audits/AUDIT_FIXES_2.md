# Techyfi POS — Second Pass Fix List
Generated: 2026-09-19 | Verification of AUDIT_FIXES.md batch
25 items: 6 partially-applied fixes + 19 new issues.

---

## HOW TO USE THIS FILE
Same as AUDIT_FIXES.md — work in order, mark [x] when done.
All paths are relative to `app/src/main/java/com/example/techyfipos/`.

---

## PART A — PARTIALLY-APPLIED FIXES (follow-up on original issues)

- [x] **PA-1** `ui/screens/ProductsScreen.kt:2056–2061, 2119–2126`
  **Filter bottom sheet "Clear All" still misses `highMarginOnly`** (was M-6 PARTIAL)
  The active-filter bar, the icon dot, and the empty-state reset were all fixed. But the filter sheet's *internal* `hasActiveFilters` variable and its "Clear All" click handler both still omit `highMarginOnly`.
  FIX: Add `|| highMarginOnly` to the filter-sheet's local `hasActiveFilters` condition. Add `highMarginOnly = false` to the "Clear All" onClick inside the sheet.

- [x] **PA-2** `ui/screens/ProductsScreen.kt:1954, 2006`
  **Batch select still uses barcode fallback as list key, not the true DB primary key** (was L-9 PARTIAL)
  Current: `key = { it.barcode.ifBlank { "prod_${it.name}_${it.createdAt}" } }` — the composite fallback can still collide.
  FIX: Change both LazyColumn and LazyVerticalGrid to `key = { it.id }` where `it.id` is the auto-generated Room primary key (Int/Long). The barcode is not unique and must not be a Compose list key.
  *Note: In `AppEntities.kt`, `Product` entity's Room `@PrimaryKey` is `barcode: String` (there is no `id` column on Product). Changed both LazyColumn and LazyVerticalGrid item keys to directly use the true Room `@PrimaryKey` `it.barcode` without the composite fallback string.*

- [x] **PA-3** `ui/screens/DuesScreen.kt:400–414`
  **"+ Add Due" FilledIconButton still 36dp** (was L-24 PARTIAL)
  FIX: Change `Modifier.size(36.dp)` to `Modifier.size(48.dp)` on the "+ Add Due" button in the DuesScreen topbar.

- [x] **PA-4** `ui/screens/SalesHistoryScreen.kt:876, 883, 1057–1335`
  **HistoryRevenueSummaryCard entirely hardcoded; return toasts hardcoded** (was L-31 PARTIAL)
  The entire `HistoryRevenueSummaryCard` composable (lines 1057–1335) has zero `stringResource` calls. All labels ("REVENUE SUMMARY", "Invoices:", "Units:", "Cash Split:", "Gross Margin Rate:", "AOV (Avg Ticket):", etc.) are raw English strings. Return toasts at lines 876 and 883 ("Returned · refunded ..." and "Could not process the return") are also hardcoded.
  FIX: Add every user-visible string in this card to `res/values/strings.xml`. Replace raw literals with `stringResource(R.string.xxx)`. Do the same for both return toast messages.

- [x] **PA-5** `ui/screens/CartScreen.kt:1294, 1784, 1816, 1830, 1840, 2357, 2368–2369` + `ui/dialogs/CustomItemDialogs.kt`
  **Remaining hardcoded English strings in CartScreen and CustomItemDialogs** (was L-7 PARTIAL)
  Still hardcoded (verified):
  - `CartScreen:1294` — `"Available: ${formatTaka(credAmt)}"` in the store-credit banner
  - `CartScreen:1784` — `"👑 ${resolvedCustomer!!.tier}"`
  - `CartScreen:1816` — `"⚠️ Due: ${formatTaka(...)}"`
  - `CartScreen:1830` — `"⭐ ${resolvedCustomer!!.loyaltyPoints} Pts"`
  - `CartScreen:1840` — `"📦 ${resolvedCustomer!!.totalOrders} orders"`
  - `CartScreen:2357` — `stringResource(...) + " required to checkout"` (hardcoded suffix)
  - `CartScreen:2368` — `"Customer phone or voucher required for Store Credit"`
  - `CartScreen:2369` — `"Insufficient store credit (Available: ...)"`
  - `CustomItemDialogs:~1172` — `"Done ($sessionAddedCount)"`
  - `CustomItemDialogs:~1197` — `"Added \"$itemTitle\" to cart! Enter next item."`
  - `CustomItemDialogs:~1243` — `"Add Another"`
  FIX: Move all to `res/values/strings.xml`. For the suffix at 2357, create a combined string resource like `R.string.perm_checkout_required` that contains the full message.

- [x] **PA-6** `ui/screens/ManageUsersScreen.kt`
  **ManageUsersScreen has no in-screen access-denied fallback** (was H-8 PARTIAL)
  The screen takes `isOwner: Boolean` and the nav guard pops unauthorised users, but the screen itself has no `if (!isOwner) { AccessDeniedPlaceholder() }` safety net.
  FIX: At the top of the `ManageUsersScreen` composable, add:
  ```kotlin
  if (!isOwner) {
      Box(Modifier.fillMaxSize(), Alignment.Center) {
          Text(stringResource(R.string.access_denied), style = MaterialTheme.typography.titleMedium)
      }
      return
  }
  ```

---

## PART B — NEW ISSUES

### HIGH

- [x] **N-1** `ui/screens/CartScreen.kt:1873`
  **"Redeem Max Loyalty" button passes raw `pointsBalance` not cash value — wrong discount applied**
  `cartViewModel.setLoyaltyDiscount(loyaltyCustomer!!.pointsBalance.toDouble())` passes the raw point count. The ViewModel cap works only by coincidence when `pointCashValue == 1.0`. When `pointCashValue < 1.0`, the caller overstates the discount and the VM clamps it to the wrong cap; when `pointCashValue > 1.0`, the maximum cashback is under-applied.
  FIX: Replace with:
  ```kotlin
  val pointCashValue = loyaltySettingsHelper.getSettings().pointCashValue
  cartViewModel.setLoyaltyDiscount(loyaltyCustomer!!.pointsBalance * pointCashValue)
  ```

- [x] **N-2** `data/DataRepository.kt:~267`
  **Credit override token persists when `cartHash` mismatches — partial bypass window**
  After fix H-9, the token is correctly cleared on `valid == true` or expiry. But if validation fails because `cartHash` doesn't match (phone and amount are correct, grant is not expired), the token stays live. A retry with the corrected `cartHash` then succeeds, defeating single-use semantics.
  FIX: Clear the token on ANY failed attempt, not just expired ones:
  ```kotlin
  pendingCreditOverride = null  // always clear after one attempt
  ```
  If re-authorization is needed after a failure, the manager must re-authenticate — this is the correct security behaviour.

- [x] **N-3** `Navigation.kt:~1082 (all six guarded routes)`
  **Permission guards briefly render the unauthorised screen before popping**
  `LaunchedEffect(Unit)` fires after the first composition frame, so ManageUsers/SalesReport/Expenses/Suppliers/Settings/RegisterClose are momentarily visible to unauthorised users.
  FIX: Move the permission check to before the composable call in the entry lambda — if not authorised, immediately call `backStack.removeLastOrNull()` (or navigate to Scanner) and `return@entry` without calling the screen composable at all. No LaunchedEffect needed.

### MEDIUM

- [x] **N-4** `ui/screens/ProductsScreen.kt:2056–2061, 2119–2126`
  *(Same as PA-1 above — included here for completeness as it was also found as a new issue in the filter sheet specifically.)*
  FIX: Same as PA-1.

- [x] **N-5** `ui/screens/SalesHistoryScreen.kt:1057–1335`
  *(Same as PA-4 above — included for completeness.)*
  FIX: Same as PA-4.

- [x] **N-6** `ui/screens/CustomersScreen.kt:224, 258, 323, 381`
  **Toolbar touch targets still 36dp and 38dp in CustomersScreen**
  Phone layout: Sort IconButton (line 258), MoreVert overflow (line 323), and add-customer FilledIconButton (line 381) are all `.size(36.dp)`. Tablet layout: export IconButton (line 224) is `.size(38.dp)`.
  FIX: Change all four to `.size(48.dp)`.

- [x] **N-7** `data/DataRepository.kt:~1186`
  **`searchCustomerSuggestions` fallback still does a full-table-scan**
  When the primary customer search returns no results, `saleDao.getAllPastCustomerContacts()` loads every distinct row from the entire sales table, then filters in memory. The N+1 batch fix only applied to the primary path.
  FIX: Replace `getAllPastCustomerContacts()` with a filtered DAO query:
  ```kotlin
  @Query("SELECT DISTINCT customerPhone, customerName FROM sales WHERE customerName LIKE :q OR customerPhone LIKE :q LIMIT 5")
  suspend fun searchPastCustomerContacts(q: String): List<CustomerContactInfo>
  ```
  Call it with `searchPastCustomerContacts("%$clean%")` instead.

- [x] **N-8** `utils/DueReminderScheduler.kt:498–514`
  **WhatsApp and app-open `PendingIntent`s share the same `requestCode`**
  Both `waPendingIntent` and `appPendingIntent` use `(customerPhone.hashCode() and 0x7FFFFFFF)`. For positive hash values these are identical. Two `getActivity` `PendingIntent`s with the same requestCode and different `Intent`s overwrite each other — one will always be wrong.
  FIX: Use offset codes: `whatsApp uses hashCode`, `appOpen uses hashCode + 1`. For example:
  ```kotlin
  val baseCode = customerPhone.hashCode() and 0x7FFFFFFF
  // waPendingIntent  → requestCode = baseCode
  // appPendingIntent → requestCode = baseCode + 1
  ```

### LOW

- [x] **N-9** `ui/dialogs/CartItemDialogs.kt:~378–382`
  **`editableAmount()` returns `""` for `0.0`, inconsistent with CartScreen version**
  The CartScreen version returns `"0"` for 0.0; CartItemDialogs returns empty string. When the discount dialog opens with `initialDiscount = 0.0`, the field appears blank rather than showing `"0"`.
  FIX: Change the return value in the CartItemDialogs version: `if (value <= 0.0) return "0"`.

- [x] **N-10** `ui/screens/CartScreen.kt:2357`
  **`" required to checkout"` suffix hardcoded English** (also covered in PA-5 — fixable together)
  FIX: Create `R.string.perm_checkout_required` as a full string (e.g. `"%1$s required to checkout"`) with a format argument, and replace the concatenation.

- [x] **N-11** `ui/screens/CartScreen.kt:2368–2369`
  **Two store-credit validation error strings not localised** (also covered in PA-5)
  FIX: Add to `strings.xml` and use `stringResource(R.string.xxx)`.

- [x] **N-12** `ui/dialogs/CustomItemDialogs.kt:1197`
  **"Added to cart! Enter next item." toast misleads when `onAddAnother` is null**
  When `onAddAnother == null`, the dialog falls through to `onConfirm`, but still shows "Enter next item." — incorrect instructions.
  FIX: Only show "Enter next item." when `onAddAnother != null`. When null, show "Added \"$itemTitle\" to cart!" without the follow-up instruction.

- [x] **N-13** `ui/screens/CartScreen.kt:1784, 1816, 1830, 1840`
  **Customer badge chips hardcoded emoji + English** (also covered in PA-5)
  FIX: Add string resources for these badge formats. Example: `R.string.cart_badge_tier` = `"👑 %1\$s"`, `R.string.cart_badge_loyalty_pts` = `"⭐ %1\$s Pts"`, etc. Replace inline string building with `stringResource(R.string.xxx, value)`.

- [x] **N-14** `ui/screens/SalesReportScreen.kt:1003–1106, 1282–1598`
  **SalesReportScreen sub-panel strings entirely hardcoded**
  OverviewPanel: "Performance Trend", "Daily sales & profit volume", "24-hour hourly distribution breakdown", "Best Day:". HourlyPanel: "PEAK HOUR", "BUSIEST HOUR", "ACTIVE HOURS", "Hourly Volume Curve", "Time-Slot Breakdown", "Active Only", "All 24h", "None", "Invoices", "Tracked". EmptyReportState: "💡 View Historical Period Analytics:".
  FIX: Add all to `strings.xml` and replace with `stringResource`.

- [x] **N-15** `ui/screens/SalesHistoryScreen.kt:876, 883`
  **Return toast messages hardcoded** (also in PA-4 — fixable together)
  FIX: Add `R.string.return_success` and `R.string.return_failed` to `strings.xml`. Use `stringResource`.

- [x] **N-16** `ui/screens/SalesHistoryScreen.kt:81`
  **`rangeForPeriod` calls `System.currentTimeMillis()` directly — not reactive to `now`**
  The function calls `currentTimeMillis()` on each invocation instead of reading from the reactive `now` produceState that was added to fix M-9. After midnight, the TODAY boundary reported by this function diverges from the `now` the composable uses for display.
  FIX: Pass `now: Long` as a parameter to `rangeForPeriod` and thread the produceState value through to it. Alternatively, make `rangeForPeriod` a composable lambda that reads the produceState directly.

- [x] **N-17** `utils/DueReminderScheduler.kt:199–244`
  **`scheduleWorkManagerReminder` is unreachable dead code**
  Since fix M-10 migrated reminder scheduling to AlarmManager-only, this function has no callers. It should be deleted to prevent accidental future re-activation and confusion.
  FIX: Delete the `scheduleWorkManagerReminder` function (lines 199–244). Run a project-wide search to confirm zero call sites before deleting.

- [x] **N-18** `data/DataRepository.kt:491–529` — `deleteSale()`
  **Audit log row written before stock is restored**
  The `InventoryAdjustment` insert (lines 491–499) precedes the `adjustStockQty` calls (lines 505–529). Within a single transaction this is safe from data corruption, but the ordering is logically incorrect — an audit row should record what happened, not what is about to happen.
  FIX: Move the `inventoryAdjustmentDao.insertAdjustment(...)` call to after all `adjustStockQty` calls complete. No functional change is needed — just reorder within the same transaction block.
  *Note: Both `inventoryAdjustmentDao.insertAdjustment` and `saleAuditLogDao.insertAuditLog` have been reordered to execute after all `adjustStockQty` calls and customer dues/loyalty reversals complete.*

---

## SUMMARY
| Category | Count |
|---|---|
| Partially-applied fixes (PA) | 6 |
| New — High | 3 |
| New — Medium | 5 |
| New — Low | 10 |
| **Total** | **25** |
