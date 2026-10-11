# Audit Round 20: Fix List
Generated: 2026-10-10

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`, `VM` = `ui/viewmodel/POSViewModels.kt`. Line numbers are approximate (taken at commit `623f26a`); search for the quoted code if they have moved.

## How to use this file
- This round is a full-app sweep: bugs, missing features, UX, i18n and design. Nothing in it is fixed yet.
- Work top to bottom. P0 and P1 first, one commit per item or per small group, with a test where the item says so.
- Tick each item as `[x]` with the commit sha and test name, the same way AUDIT_FIXES_19.md does.
- **Verified** means the round-20 auditor re-read the code and confirmed the issue. **Reported** means a sub-audit found it by reading the code; re-check it before changing anything, and if it turns out not to be real, tick it with a one-line "not a bug: ..." note instead of changing code.
- The Android SDK was not available when this file was written, so nothing here was built or run. Build and run the unit tests after every group.
- Keep strings in both `res/values/strings.xml` and `res/values-bn/strings.xml`. Both files currently have 2683 keys and are in sync; keep them that way.

## Shared fix for several crash items (do this first)
Many ViewModel functions run repository writes inside a bare `viewModelScope.launch { ... }` or `launch(Dispatchers.IO) { ... }` with no try/catch. The repository throws `UnauthorizedException` (and database errors), `CrashLogger` forwards to the default handler, and **the app crashes**. Items R20-01, R20-02, R20-03, R20-04 and R20-05 are all instances.

Suggested shape (one small new file plus a collector):
1. Add `utils/RepositoryErrorBus.kt`: an `object` holding a `MutableSharedFlow<Throwable>(extraBufferCapacity = 8, onBufferOverflow = DROP_OLDEST)`, a `report(e)` function that logs and `tryEmit`s, and `val handler = CoroutineExceptionHandler { _, e -> report(e) }`.
2. In `MainNavigation` (Navigation.kt, right after `val scope = rememberCoroutineScope()`, which is inside `LocaleWrapper` so `LocalContext.current` is the localized context), add a `LaunchedEffect(Unit)` that collects the bus and shows `getLocalizedRepositoryErrorMessage(localizedContext, error)` as a Toast. Note: Navigation.kt uses fully qualified `android.widget.Toast`.
3. Launch the fire-and-forget writes with the handler: `viewModelScope.launch(RepositoryErrorBus.handler)` or `launch(Dispatchers.IO + RepositoryErrorBus.handler)`.
4. Where the UI shows a success toast before the result is known, switch that function to a `Result` callback instead, so the toast only appears on success.

---

## P0: Crashes

- [x] **R20-01 HIGH Verified. Settings saves crash for anyone without `SYSTEM_SHOP_SETTINGS`.** (Fixed in `41ae6c0`, test: `testR20_01_saveInvoiceSettingsDataWithoutPermissionCatchesErrorAndEmitsToBus` in `SettingsViewModelCrashTest`).
  - `CartScreen.kt` about 2470 and 2485: the "item-by-item / summary only" FilterChips call `settingsViewModel.saveInvoiceSettingsData(...)`. Default cashiers and managers can see these chips.
  - VM about 3053: `saveInvoiceSettingsData` is a bare `viewModelScope.launch`. repo about 2316 calls `requirePermission(SYSTEM_SHOP_SETTINGS)`, which default cashiers and managers lack (`data/AppPermissions.kt` 94-110). Uncaught, so the app crashes.
  - Managers can open Settings (Navigation.kt about 551, 791, 1337 allow `isSupervisor`), so every toggle there (SettingsScreen about 4405-4431) crashes the same way. Also `saveShopInfo`, `saveInvoiceSettings`, `saveAutoBackup`, `resetFactoryDefaults`, `mergeDuplicateDueLedgers` (owner-only in repo about 2130, but the "Repair dues" row in SettingsScreen about 1251, 4456, 5433 has no owner check).
  - Fix: apply the shared fix to every `viewModelScope.launch` in `SettingsViewModel` (VM 2941 to 3431, 16 launches). Hide the two cart chips unless the user has `SYSTEM_SHOP_SETTINGS`. Hide or disable the Repair Dues row when `!isOwner`. See also R20-20 (Settings gate).
  - Test: unit or instrumented test that calls `saveInvoiceSettingsData` as a cashier session and asserts no crash and an error emitted.

- [x] **R20-02 HIGH Verified. Hold cart crashes when the user lacks `SALES_HOLD_CART`, and loses the cart.** (Fixed in `9764426`, test: `testR20_02_holdCurrentCartFailureRollsBackStateAndEmitsToErrorBus` in `CartViewModelHoldCrashTest`).
  - VM `holdCurrentCart` (about 717-787): it clears cart state first, then runs `launch(Dispatchers.IO) { repository.insertHeldCart(heldCart) }` with no try/catch. repo about 318 requires `SALES_HOLD_CART` of the signed-in user. CartScreen about 662-672 and 1023-1030 let a supervisor approve a hold for a cashier who lacks it, so the insert throws and the app crashes.
  - Also: the reset is partial. It does not reset `_paymentMethod`, `_cardType`, `_manualCashierName`, `_resolvedCustomer`, `_customerSuggestions`, `_isAutoFilledName` (compare `clearCart()` about 1264). After holding customer A's cart, the next walk-in sale shows A's store credit and starts in A's payment method.
  - Fix: do the same full reset as `clearCart()` (without `persistCartSnapshot`). Wrap the insert in try/catch (rethrow `CancellationException`); on failure, if the cart is still empty, restore `items`, discount, percent flag, price mode, customer name and phone, call `persistCartSnapshot(items)`, and report the error. Longer term, give a supervisor-approved hold a scoped override token, like `authorizeCreditLimitOverride`.

- [x] **R20-03 HIGH Reported. Loyalty, expense and dues writes crash on permission errors and show success anyway.** (Fixed in `518d6cf`, test: `LoyaltyExpenseDuesCrashTest`).
  - VM about 4362-4394: `LoyaltyViewModel.upsertMember`, `addPoints`, `redeemPoints`, `deactivateMember`, `deleteMember` use `launch(Dispatchers.IO)` with no catch. The repo methods (about 4932-4992) check `DUES_ADD_DUE`, but the Loyalty screen is gated by `LOYALTY_MANAGE` (Navigation.kt about 1387), so a user with only `LOYALTY_MANAGE` crashes the app on Add Points.
  - VM about 3961 `ExpenseViewModel.addExpense`: same; Expenses is gated by `isSupervisor` but the repo requires `EXPENSES_MANAGE`.
  - VM about 2882 `DuesViewModel.deleteCustomerDue`: bare launch; repo about 2078-2086 throws when the balance is above 0 or the row is archived.
  - `LoyaltyScreen.kt` about 260, 272, 301, 324 show success toasts before the result. `redeemPoints` returning `false` is ignored.
  - Fix: shared fix plus `Result` callbacks; toast only on success. Change the loyalty repo methods to check `AppPermissions.LOYALTY_MANAGE`.

- [x] **R20-04 HIGH Reported. Taking a product photo crashes if camera permission was never granted.** (Fixed in `4b61513`).
  - `ui/dialogs/AddEditProductDialog.kt` about 799 calls `cameraLauncher.launch(null)` with no permission check. The manifest declares CAMERA, so `ACTION_IMAGE_CAPTURE` throws `SecurityException` until it is granted.
  - Fix: check `CAMERA` and request it with `rememberLauncherForActivityResult(RequestPermission())` before launching; wrap `launch` in try/catch.
  - Same family, not a crash: `ui/dialogs/ProductMediaDialogs.kt` about 111-160, the barcode scan dialog binds CameraX without a permission check and shows a black box. Request permission and show a rationale.

- [x] **R20-05 MED Reported. Receipt generation errors crash the app; double tap prints twice.** (Fixed in `559af8d`).
  - `utils/BillActions.kt` about 956: `printReceiptWithBluetoothFallback` no longer catches around `generateBillJpeg` (it did before 38720a3). It is called from `scope.launch` in `ui/dialogs/ReceiptSummaryDialog.kt` about 709 with no handler.
  - The Print button has no in-flight guard, so a double tap opens two RFCOMM connections; the second fails and in AUTO mode falls back to the system print dialog.
  - Fix: in the dialog add `var isPrinting by remember { mutableStateOf(false) }`, skip the click while true, set `enabled = !isPrinting`, wrap the call in try/catch (rethrow `CancellationException`, toast `R.string.print_error_message`), reset in `finally`. Optionally also add a process-wide `Mutex` in `sendToBluetoothPrinter` (`printer/BluetoothPrinterTransport.kt`).

---

## P1: Money, stock and data integrity

- [x] **R20-06 HIGH Verified. The %/৳ discount toggle bypasses the discount limit.** (Fixed in `a95f026`, test: `DiscountInputTest`).
  - `CartScreen.kt` about 1283: the trailing toggle calls `cartViewModel.toggleDiscountUnit()` (VM about 1256), which only flips the flag. The limit and supervisor check only run in the text field's `onValueChange` (about 1258-1278).
  - Scenario: cashier with a 10% limit types 50 in ৳ mode on a ৳1,000 bill (5%, allowed) and taps the toggle; the discount becomes 50%. Typing 100 then toggling gives 100%.
  - Fix: in the toggle's onClick set `discountInput = ""`, call `cartViewModel.setDiscountAmount(0.0)`, then toggle; the next entry re-runs the check. Also re-check the limit when the subtotal changes (removing items or changing price mode after a fixed ৳ discount raises its percentage).

- [x] **R20-07 HIGH Reported. Safe drops are counted as business expenses and lower net profit.** (Fixed in `45d8778`, test: `MoneyFlowsTest`).
  - `RegisterCloseScreen.kt` about 1075-1082 saves a safe drop as `type = "EXPENSE", category = "Safe Drop"`.
  - VM `buildReport` (about 2071 and 2291, both `.filter { it.type == "EXPENSE" && it.category != "Supplier Payment" }`) and `SalesReportScreen.kt` about 186 count it as an expense. A ৳20,000 drop to the vault lowers Net Profit by ৳20,000 in the report, Z-report and CSV.
  - Fix: also exclude `category == "Safe Drop"` (case-insensitive in SalesReportScreen) in those three places. Keep it in the register `cashOut` (VM about 3878), which is correct. Better long term: a separate `type = "SAFE_DROP"` with a migration.

- [x] **R20-08 HIGH Verified. The Z-report shown right after "Close register" describes the new, empty shift.** (Fixed in `4121358`, test: `MoneyFlowsTest`).
  - `RegisterCloseScreen.kt` about 1119-1145: `onConfirmClose` calls `closeRegister`, which (repo about 4909) writes a new shift state (`startedAt = now`). That changes `shiftStart`, so the `LaunchedEffect` rebuilds `report` for an empty shift and `rangeLabel` changes. `onSuccess` then opens the Z dialog (about 1211), which reads the new `report`. The printed "DAILY CLOSING Z-REPORT" shows 0 sales.
  - Fix: add `var closedShiftZReport by remember { mutableStateOf<Pair<SalesReportData, String>?>(null) }`. In `onConfirmClose` capture `report?.let { it to rangeLabel }` before calling `closeRegister`, and store it in `onSuccess`. The Z dialog, its print/save, and the share-format dialog (about 1256) use `closedShiftZReport ?: report?.let { it to rangeLabel }`. Clear it when both dialogs are dismissed.

- [x] **R20-09 HIGH Verified. Printing or reprinting any receipt opens the cash drawer.** (Fixed in `559af8d`).
  - `utils/BillActions.kt` about 965 passes `kickDrawer = settings.cashDrawerKickEnabled`. The only caller is `ReceiptSummaryDialog.kt` about 714, which is also used for reprints from Sales History. Checkout already kicks the drawer for Cash and Split sales (`CartScreen.kt` about 575).
  - Fix: pass `kickDrawer = false` there.

- [x] **R20-10 HIGH Reported. One invoice shows three different profit figures.** (Fixed in `41331c1` and `b6e2d4a`, test: `MoneyFlowsTest`).
  - Sales History rows use `calculateInvoiceProfit` (VM about 1882); the invoice detail uses `OwnerProfitAnalyticsContent` (`ui/components/OwnerProfitAnalyticsComponents.kt` about 62-108); the report uses `buildReport`.
  - Owner view: counts tax as profit (`bill.subtotal`), uses a fixed 75% cost fallback instead of `defaultCostPercentage`, matches returns by name not barcode, subtracts refunds including tax, estimates reversed cost as `refund * 0.75`.
  - `calculateInvoiceProfit`: ignores the loyalty discount; for tax-exclusive sales subtracts refunds including tax (`refundTaxRatio = 1.0`, about 1936).
  - Fix: make `calculateInvoiceProfit` the single source (subtract `loyaltyPointsRedeemed * pointValue`, strip tax from refunds as `buildReport` does) and pass its result into `OwnerProfitAnalyticsContent`.
  - Test: one invoice with tax, discount, loyalty and a partial return; assert all three views agree.

- [x] **R20-11 HIGH Reported. Excel import does not lowercase barcodes.** (Fixed in `929f92f`, test: `ProductValidationTest`).
  - `utils/ExcelHelper.kt` about 342; repo `upsertProducts` about 3779-3805. Other write paths lowercase, and the R-6 lookup assumes it.
  - Existing product: sheet "ABC-1", stored "abc-1"; lookup finds it but `updateProduct(p.copy(...))` keeps key "ABC-1", so `@Update` matches 0 rows while the toast reports success.
  - New product: inserted as "ABC-1", never found by scanner or cart; the user adds it again and gets a duplicate.
  - Fix: normalise with `toEnglishDigits(trim).lowercase()` in `upsertProducts`; update with `p.copy(barcode = existing.barcode, createdAt = existing.createdAt, ...)`.

- [x] **R20-12 HIGH Reported. Excel import's leading-zero guess rewrites real barcodes.** (Fixed in `929f92f`, test: `ProductValidationTest`).
  - `ExcelHelper.kt` about 59-70, applied to every string and numeric cell (about 76-81). Every valid 12-digit UPC-A passes `isValidEan13WithLeadingZero`, so "036000291452" becomes "0036000291452" after export then import, creating a duplicate. 10-digit codes starting with "1" are treated as phone numbers.
  - Fix: never apply it to string cells. For numeric cells use `DataFormatter`, and only consider the leading-zero rule for the barcode column.

- [x] **R20-13 HIGH Reported. Excel import silently zeroes stock, writes no audit row and hides row errors.** (Fixed in `929f92f`, test: `ProductValidationTest`).
  - `ExcelHelper.kt` about 354, 365: unparseable values ("12 pcs", "N/A", Bengali "১০") become 0 and overwrite stock. `upsertProducts` writes no `InventoryAdjustment`. `ProductsScreen.kt` about 349 shows only "Imported N"; `failCount` and `failures` are never shown. No preview before overwriting.
  - Fix: run `toEnglishDigits` in `parseDouble`; treat non-blank unparseable stock or price as a row failure; insert an `InventoryAdjustment` (reason "Import") when stock changes; show failures in a result dialog; add a confirmation ("N new, M updated, K stock changes").
  - Related: `ExcelHelper.kt` about 76-84 never uses `DateUtil.isCellDateFormatted`, so expiry dates import as serial numbers like "46387" and expiry alerts never fire. Format date cells as `dd/MM/yyyy` and reject unparseable expiry values.

- [x] **R20-14 MED Verified. The "Exact" cash chip ignores store credit.** (Fixed in `a95f026`, test: `DiscountCalculationTest`).
  - `CartScreen.kt` about 2078: `val exactAmt = grandTotal`. With a ৳300 bill and ৳100 store credit, Exact fills ৳300 and shows ৳100 change. `remainingAfterSC` is already computed at about 2066.
  - Fix: `val exactAmt = remainingAfterSC`.

- [x] **R20-15 MED Verified. The cart summary card shows the raw discount input, not the discount amount.** (Fixed in `a95f026`, test: `DiscountCalculationTest`).
  - `CartScreen.kt` about 1011 passes `discountAmount` (for example `10` meaning 10%) to `CartTotalSummaryCard`. 10% off ৳500 shows "-৳10" while the total uses ৳50.
  - Fix: pass `discountVal` (collected at about 319).

- [x] **R20-16 MED Verified. Store credit applied for one customer is charged to another.** (Fixed in `442eb1a`, test: `MoneyFlowsTest`).
  - VM `onCustomerPhoneChanged` (about 455), `selectCustomerSuggestion` (about 520) and `clearCustomerDetails` (about 526) never reset `_storeCreditApplied`. Apply ৳500 of A's credit, change the phone to B, and checkout takes ৳500 from B. Clearing the phone makes the sale fail.
  - Fix: when the trimmed phone actually changes (note `refreshResolvedCustomer` calls `onCustomerPhoneChanged` with the same phone) and `_redeemedVoucher.value == null`, set `_storeCreditApplied.value = 0.0`. Same in the other two functions.

- [x] **R20-17 MED Reported. Store credit, loyalty and voucher amounts are not re-capped when the total drops.** (Fixed in `442eb1a`, test: `MoneyFlowsTest`).
  - `storeCreditApplied` is never re-capped after a later discount, price-mode change or removed line (`CartScreen.kt` about 537-541, 582-584, 620-624; VM about 393-396). The receipt shows ৳500 credit on a ৳300 bill and prints the voucher's remaining balance too low (the database is correct).
  - `loyaltyDiscount` is fixed when redeemed (VM about 548-564); if the cart shrinks it can exceed `maxBillPercentPayable` and the repo rejects the sale.
  - `restoreHeldCart` (about 897-921): an expired or used-up voucher leaves the cap at `Double.MAX_VALUE`, so the full credit is restored without a voucher. `restoreCartSnapshot` (about 1109) restores it with no check.
  - Fix: re-clamp both in a `combine` on subtotal, discount and tax (or at snapshot time with `min(value, grandTotal)`). On restore with an invalid voucher, set credit to 0 and tell the cashier.

- [x] **R20-18 MED Reported. Edit dialog logs stock decreases as "Restock"; clearing the stock field sets it to 0.** (Fixed in `1d66e41`, test: `ProductValidationTest`).
  - `AddEditProductDialog.kt` about 173 defaults `editStockReason` to "Restock" and about 275 always passes it, so the fallback in `ProductsScreen.kt` about 2799 never runs. `stockAltered` requires a parseable value, so an empty field hides the reason panel but saves 0.
  - Fix: default the reason to null and require one when stock changes; treat a blank stock field as unchanged in edit mode.

- [x] **R20-19 MED Reported. Stock-adjustment reasons are stored in the UI language.** (Fixed in `1d66e41`, test: `ProductValidationTest`).
  - `ProductsScreen.kt` about 2709, 2799, 2838 store `context.getString(R.string.products_reason_restock)` etc.; about 3374 uses `stringArrayResource(R.array.stock_adjustment_reasons)`. In Bangla the DB stores "পুনরায় মজুদ", so `StockAuditDialogs.kt` about 49 (filter by "Restock") and about 200 (badge colour) fail.
  - Fix: store canonical English keys; show labels via `localizedAdjustmentReasonLabel`.

- [x] **R20-19b MED Reported. Bulk price change does not update `discountedPrice`, so the next edit undoes it.** (Fixed in `929f92f`, test: `ProductValidationTest`). repo about 3808-3825 updates only `sellingPrice`; the edit form prefills from `discountedPrice` (`ProductsScreen.kt` about 2731). Fix: also set `discountedPrice = newPrice` when it is above 0.

- [x] **R20-19c MED Reported. Editing or importing a product overwrites `createdAt`.** (Fixed in `929f92f`, test: `ProductValidationTest`). `ProductsScreen.kt` about 2774 builds `Product(...)` without `createdAt`; repo about 3786 does not keep `existing.createdAt`. Fix: copy the existing value. Then a "Newest" sort (`ProductSortOption`, about 167) becomes possible.

- [x] **R20-19d MED Reported. Selling price can be higher than MRP.** (Fixed in `1d66e41`, test: `ProductValidationTest`). `AddEditProductDialog.kt` about 228-262 has no check. Fix: block saving or confirm when `mPrice > 0 && dPrice > mPrice`.

- [x] **R20-19e MED Reported. Counted cash in the close dialog drops decimals; invalid input closes with ৳0; failure leaves the dialog stuck.** (Fixed in `4121358`, test: `MoneyFlowsTest`).
  - `RegisterCloseScreen.kt` about 2334: `initialCountedCash.toInt().toString()` turns 1234.50 into "1234" and shows a ৳0.50 shortage.
  - About 2339: `actualCashText.toDoubleOrNull() ?: 0.0` lets a blank field submit as 0.
  - `isSubmitting` is never reset on failure (about 2491); Confirm and Cancel stay disabled. There is one caller (about 1114).
  - Fix: format with decimals (trim trailing zeros); disable Confirm when the text does not parse; add a failure callback parameter to `onConfirmClose` that resets `isSubmitting`.

- [x] **R20-19f MED Reported. Supplier "Pay Due" can be submitted twice.** (Fixed in `eb400cb`, test: `MoneyFlowsTest`). `SuppliersScreen.kt` about 1272-1290 has no in-flight guard; a double tap records two payments and two expenses. Fix: `isSaving` flag as in `RecordPaymentDialog`.

- [x] **R20-19g HIGH Reported. "Supplier Payment" expenses break the books.** (Fixed in `eb400cb`, test: `MoneyFlowsTest`).
  - `ui/components/AdvancedPosFeatures.kt` about 1224 offers "Supplier Payment" as a manual expense category. VM about 3838 and `RegisterCloseScreen.kt` about 202 drop that category from `cashOut`, assuming a `SupplierPayment` row exists. A manual one never reduces drawer cash or the supplier's due.
  - Deleting the auto-created expense (`ExpensesScreen.kt` about 434, repo about 4662) leaves the supplier payment; re-categorising it counts it twice.
  - Fix: remove the category from the manual list (or route it to `recordSupplierPayment`); make auto-generated supplier expenses read-only.

- [x] **R20-19h HIGH Reported. Dues Audit History lists due sales twice and ages debts wrongly.** (Fixed in `ec65f52`, test: `MoneyFlowsTest`).
  - VM `allDuesAuditEntries` (about 2566-2631) adds a CREDIT_SALE entry from sales and a MANUAL_DUE entry from the `DueCharge(reason="Sale")` row that repo about 1197 now writes for each due sale. It also uses `grandTotal` instead of `grandTotal - amountPaid`. `customerDebtOriginTimestamps` (about 2636-2680) double counts the same way, so customers move into 30+ day overdue too early (`DuesScreen.kt` about 866-876).
  - Fix: build credit-sale entries from `DueCharge` rows with `reason == "Sale"`; use sales only for legacy sales with no charge row; exclude Sale charges from MANUAL_DUE and from aging.

- [x] **R20-19i MED Reported. Deleting a charge clamps the balance to 0 and loses the overpayment.** (Fixed in `ec65f52`, test: `MoneyFlowsTest`). repo about 2048-2066 `(outstanding - charge.amount).coerceAtLeast(0.0)`. Fix: move the excess to store credit with a ledger row, or block the delete until payments are reversed.

- [x] **R20-19j MED Reported. "Add customer" with an existing phone silently overwrites that customer.** (Fixed in `ec65f52`, test: `MoneyFlowsTest`). `CustomersScreen.kt` about 813-835 then VM about 4936-4947. Fix: in add mode, if the normalised phone exists, show an error or the merge prompt.

- [x] **R20-19k MED Reported. Report totals disagree with each other.** (Fixed in `ec65f52`, test: `MoneyFlowsTest`).
  - `SalesReportScreen.kt` about 542 vs 872: header net profit subtracts discounts and loyalty; the Overview card's `netOperatingProfit` does not. The two expense filters also differ (any non-`CASH_IN` vs `type == "EXPENSE"`).
  - `SalesHistoryScreen.kt` about 259-264: header profit with a filter active still subtracts all returns in the range (VM about 2047).
  - VM about 2222-2250: returns, due collections and expenses on days, hours or products with no sales are dropped from the breakdowns (`?: return@forEach`) but still counted in the totals.
  - Fix: use `data.netProfit` everywhere with one expense filter in the VM; when `sourceList` is passed, keep only returns whose `saleId` is in it; create missing breakdown entries instead of skipping.

- [x] **R20-19l MED Reported. Custom date range shifts one day earlier in UTC+6.** (Fixed in `ec65f52`, test: `MoneyFlowsTest`). `SalesReportScreen.kt` about 320-323, 491-500 and `SalesHistoryScreen.kt` about 977-985 pass local-midnight millis to `DatePicker`, which reads UTC. Fix: convert local Y/M/D to UTC midnight for `initialSelectedDateMillis`, and back to local midnight on confirm.

- [x] **R20-19m MED Reported. Reprinted repayment receipts show today's balance as "Remaining Due".** (Fixed in `ec65f52`, test: `MoneyFlowsTest`). `DuesScreen.kt` about 469-478 and 6194, 6210. Fix: store `balanceAfter` on `DuePayment`, or replay the ledger, or relabel as "Current Due".

- [x] **R20-19n LOW Reported. Bank refunds are left out of register digital refunds.** (Fixed in `4121358`, test: `MoneyFlowsTest`). `RegisterCloseScreen.kt` about 222: the `startsWith` list (MFS, Card, bKash, Nagad, Rocket) lacks "Bank", which `ReturnDialog.kt` about 103 offers and stores as the refund method. Fix: add `method.startsWith("Bank")` or test against `REGISTER_CLOSE_DIGITAL_METHODS`.

---

## P2: Permissions and access control

- [x] **R20-20 HIGH Reported. The MANAGER role ignores the permission matrix in the UI but not in the repository.** (Fixed in `9681f86`, test: `AccessControlP2Test`).
  - Every UI gate is `isOwner || isSupervisor || perm`, and `isSupervisor` is true for any MANAGER (`AppPermissions.kt` about 254-257). Examples: Navigation.kt about 384-404, 1164, 1202-1206, 1271-1387; `utils/CartAuthHelper.kt` 12-16; `CartScreen.kt` 298-304. The repository uses `hasPermission`.
  - If the owner unticks a permission for a manager, the manager still sees the screen and every write fails (and crashes where uncaught).
  - Fix: gate on `activePermissions.contains(...)` alone (owners already get all permissions from `getEffectivePermissions`); keep `isSupervisor` only for the supervisor PIN override.

- [x] **R20-21 HIGH Reported. Reports show profit and allow Excel export to anyone with the daily-report permission.** (Fixed in `9681f86`, test: `AccessControlP2Test`). Navigation.kt about 1239 admits `REPORTS_VIEW_DAILY` or `REPORTS_VIEW_PNL`; `SalesReportScreen` (about 139) takes no permission parameters. Fix: pass `canViewPnl` and `canExport`, hide profit/COGS and export when false, check in the repository too.

- [x] **R20-22 HIGH Reported. Dues ignores the permission system.** (Fixed in `9681f86`, test: `AccessControlP2Test`). `DuesScreen.kt` about 465 (`canMutate = isOwner`), 977 and 1072 (`canCollect = isOwner`), 5037. Default cashiers have `DUES_COLLECT_PAYMENT` and the repo (about 2002) would allow it, but only the owner sees Collect. Fix: `canCollect` from `DUES_COLLECT_PAYMENT`, `canAddDue` from `DUES_ADD_DUE`; keep deletes owner-only.

- [x] **R20-23 MED Verified. Removing the last unit with the minus button bypasses the void permission.** (Fixed in `9681f86`, test: `AccessControlP2Test`).
  - `CartScreen.kt` about 3333: the minus stepper calls `onQuantityChanged(next)` with 0, and `setQuantity(0)` (VM about 1225) quietly removes the line. The trash button goes through `onRemove`, which checks `SALES_VOID_CART`.
  - `ScannerScreen.kt` about 1286 and 1632: the tablet rows' `onQuantityChange` do the same.
  - Fix: if the next quantity is 0 or less, call `onRemove()` / `onRemoveItemRequested(item.product)` instead.

- [x] **R20-24 MED Reported. Stock changes ignore `INVENTORY_ADJUST_STOCK`, and failures still show "Stock saved".** (Fixed in `9681f86`, test: `AccessControlP2Test`).
  - `ProductsScreen.kt` about 196 declares `canAdjustStock` but never uses it; about 2010 and 2063 open the stock dialog for everyone; quick add (about 2829-2843) and stock check (about 2854-2884) are ungated. The repository edit, quick-add and stock-check paths only require `INVENTORY_ADD_EDIT` (about 367, 382).
  - `updateProductWithAdjustment` only logs errors (VM about 1717) and the success toast always shows.
  - Fix: gate those entry points on `canAdjustStock`; disable the stock field in the edit dialog without it; require `INVENTORY_ADJUST_STOCK` in the repo whenever an adjustment is passed; return a `Result` and toast only on success.

- [x] **R20-25 MED Reported. Products can be opened without `INVENTORY_VIEW`.** (Fixed in `9681f86`, test: `AccessControlP2Test`). The deep link "products" (Navigation.kt about 382, 1099; sent by `StockAlertWorker.kt` about 81), Cart "Add now" for an unknown barcode (`CartScreen.kt` about 3033), and Scanner (Navigation.kt about 1167) reach `entry<Products>`, which has no gate. `prefilledBarcode` opens the Add dialog even when `canEdit = false` (`ProductsScreen.kt` about 580-583). Fix: gate the deep link and the entry; hide "Add now" without `INVENTORY_ADD_EDIT`; only auto-open Add when `canEdit`.

- [x] **R20-26 MED Reported. Four permissions are never checked.** (Fixed in `9681f86`, test: `AccessControlP2Test`). `SYSTEM_MANAGE_STAFF`, `SYSTEM_PERMISSIONS`, `REPORTS_VIEW_Z_REPORT`, `REPORTS_EXPORT_EXCEL` (ManageUsers is hard-gated on `isOwner`, Navigation.kt about 1139, 1350). Fix: enforce them or remove them from `CATEGORIES`.

- [x] **R20-27 MED Reported. Discount approved by a supervisor still fails at checkout.** (Fixed in `9681f86`, test: `AccessControlP2Test`). repo about 881-883 checks `SALES_APPLY_DISCOUNT` against the signed-in cashier; `CartScreen.kt` about 1262-1270 allows supervisor approval. Fix: record the approval as a token (like `authorizeCreditLimitOverride`) and accept it in the repo. Related LOW: `CartScreen.kt` about 2867 calls `authorizeCreditLimitOverride` without a `cartHash`, so the override is not tied to the cart.

- [x] **R20-28 MED Reported. Changing a user's role keeps their old custom permissions.** (Fixed in `9681f86`, test: `AccessControlP2Test`). `ManageUsersScreen.kt` about 649. Fix: pass `null` (role defaults) when the role changes, or ask.

- [ ] **R20-29 MED Reported. OWNER role handling is inconsistent.** Add dialog offers OWNER but `createUser` always throws (repo about 3981); Edit allows promoting to a second owner (repo about 4106-4110); `resetOwnerPinAfterDeviceCredential` resets an arbitrary first owner (about 4013). Fix: hide OWNER in Add and pick one rule for multiple owners.

- [ ] **R20-30 MED Reported, uncertain. Anyone who knows the device screen lock can reset the owner PIN.** `LockScreen.kt` about 157-170 with repo about 4010-4018. Fix: require a recovery code shown once at owner setup, or at least log an audit entry and notify the owner.

---

## P3: Session, navigation and missing features

- [x] **R20-31 HIGH Reported. After process death the app returns signed out on the last screen with no lock.** (Fixed in `e57bede`). `rememberNavBackStack(Splash)` (Navigation.kt about 315) is restored, but `_currentUser` (VM about 3435) and `repo.sessionUser` (about 216) are in memory only. Auto-lock (about 341-362) only fires after 5 minutes in the background. Every gated save then fails. Fix: `LaunchedEffect(currentUser, currentKey) { if (currentUser == null && currentKey != Splash && currentKey != Lock) { backStack.clear(); backStack.add(Lock) } }`.

- [x] **R20-32 HIGH Reported. Phone cashiers cannot log out or lock the terminal.** (Fixed in `e57bede`). The only entry points are the tablet rail (Navigation.kt about 995-1037) and the Settings logout card, and Settings needs `SYSTEM_SHOP_SETTINGS`. `ScannerScreen`'s `onNavigateToSettings` (about 130) is never called. Fix: add "Lock / Switch user" for every role on phone (for example in `MoreOperationsHubSheet` or the Scanner top bar) using the same logic as Navigation.kt about 626-628, including `HardwareBarcodeScanner.clearPendingInput()`. Route the Settings logout through the same confirm dialog as the rail.

- [x] **R20-33 MED Reported. System back on checkout leaves the Cart screen.** (Fixed in `e57bede`). `CartScreen.kt` has no BackHandler; Navigation.kt about 434 pops the screen. If back is pressed during a save, the coroutine is cancelled after the DB commit and before `clearCart()`, so the cashier may sell again. Fix: `BackHandler(enabled = showCheckout || isCompletingSale) { if (!isCompletingSale) showCheckout = false }`, and move sale completion into `viewModelScope` (or `NonCancellable`) with an atomic guard at the start of `executeSaleAction` (about 2557-2563, 2879-2885).

- [x] **R20-34 MED Reported. Manual barcode entry of the same code is silently dropped.** (Fixed in `e57bede`). `ScannerScreen.kt` about 516 calls `onBarcodeScanned(...)` without `fromHardwareScanner = true`; the lock (VM about 1475-1479) is only released by camera frames. Fix: pass `fromHardwareScanner = true` or add a `manual` flag.

- [x] **R20-35 MED Reported. Screen gates run side effects during composition.** (Fixed in `e57bede`). Navigation.kt about 1140, 1240, 1272, 1293, 1311, 1338, 1368, 1388, 1412 call `Toast.show()` and `backStack.removeLastOrNull()` in the composable body; a recomposition during the exit animation pops twice. Fix: move into `LaunchedEffect(Unit)` and only pop if the top is still that key.

- [x] **R20-36 MED Reported. Backup and restore gaps.** (Fixed in `10263fc`).
  - Drive backup (`utils/BackupPackageManager.kt` about 444-458 `capturePreferences`) omits quick-sale presets, note presets, due-reminder templates, custom categories, return-audit presets, theme/layout, default cost %, and the loyalty settings in SharedPreferences `loyalty_program_prefs` (`utils/LoyaltySettingsHelper.kt` about 37). The JSON backup also omits loyalty settings. Fix: add them to capture/apply and to JSON export/import; call `repository.updateLoyaltySettings` after restore.
  - Offline shows a false "Reconnect Google Drive" alert and drops the run: `AutoBackupWorker.kt` about 93-98 treats any exception from `authorizeWithoutUi` as needing auth. Fix: only when `result.hasResolution()`; treat `IOException` as retry; add a network constraint for Drive scope.
  - `AutoBackupScheduler.kt` about 127-131 uses `ExistingWorkPolicy.REPLACE` on every `MainActivity.onStop`, which cancels a running upload. Fix: `KEEP` for the `respectDueTime = true` path.
  - `saveLocalAutoBackup` (VM about 3129) forces a full SCOPE_ALL run (including Drive) on every chip tap. Fix: pass the matching scope; only force on off to on.
  - `AutoBackupWorker.kt` about 69-76 always builds the JSON export even for Drive-only runs. Fix: only when local is due.
  - Local auto-backups in `filesDir/AutoBackups` cannot be restored from the app. Fix: add a "Restore from automatic backup" list and an export/share action.
  - Encrypted import defaults to replace with no second confirmation (SettingsScreen about 5622). Fix: default to merge or confirm.
  - Drive restore does not reschedule backups. Fix: `AutoBackupScheduler.scheduleAllAutoBackups(context, forceUpdate = true)` after restore.
  - Drive retention prunes manual backups too, and `listBackups` turns errors into an empty list (`GoogleDriveBackupManager.kt` about 151-203). Fix: prune only `-auto-` files; return a `Result`.
  - Offline, the Drive switch shows OFF and "Not connected" (SettingsScreen about 3101, 4162). Fix: use the stored `driveAccountEmail` for connected state and show "offline" separately.

- [x] **R20-37 MED Reported. Factory reset leaves data behind.** (Fixed in `10263fc`). repo `resetFactoryDefaults` (about 5000-5022) deletes `custom_logo.png` but the logo is saved as `store_logo.png` (SettingsScreen about 4008). It also leaves `filesDir/AutoBackups/*.json` (plaintext customer and sales data), `safety_rollback/*.sqlite`, `restored_product_images/`, `restored_*logo.png`, the `loyalty_program_prefs` and `auth_security` prefs, and WorkManager/alarm jobs. Fix: delete them and cancel the unique works and alarm.

- [x] **R20-38 MED Reported. Settings sub-page edits are saved even when the user backs out.** (Fixed in `10263fc`). SettingsScreen about 4486-4615 write into the screen-level `currentSettings`; any Home toggle (about 4405-4426) or the logo picker (about 4012) then saves it all. Fix: keep a draft per sub-page and reset it on back, or prompt about unsaved changes.

- [x] **R20-39 MED Reported. Settings toggles that do nothing.** (Fixed in `10263fc`).
  - Due receipt QR: printer checks `showDueReceiptQr || showDueReceiptVerificationQr` (`utils/BillPdfGenerator.kt` about 1627, 1889); the second has no UI and defaults to true. Fix: use one flag.
  - Due receipt logo: `(settings.showDueReceiptLogo || settings.showLogo)` (about 1559). Fix: `settings.showDueReceiptLogo && logoUri.isNotBlank()`.
  - Loyalty "Points expiry (days)" is saved (`LoyaltyScreen.kt` about 1339, 1469) but unused; EXPIRING_SOON is hard-coded to 90 days (VM about 4340). Fix: use it, or hide the field.
  - Dead fields: `driveBackupTime`, `websiteUrl`, `directBluetoothPrintEnabled`, `directBluetoothPrinterAddress`, `decimalQuantitiesEnabled`. Fix: wire up or remove.

- [x] **R20-40 MED Reported. Missing features worth building.** (Fixed in `f3bf87d`).
  - Per-item cart discount: `CartItemDiscountDialog` (`ui/dialogs/CartItemDialogs.kt` about 389) is never called and `CartItem` has no line discount. Build it end to end or delete the dead code.
  - Supplier ledger: `getSupplierPayments`, `getInvoicesForSupplier`, `getPurchaseItemsForInvoice` (repo about 4715, 4887, 4891) are unused; no way to void a supplier payment or invoice. Add a supplier detail sheet and an owner-only void.
  - Batch label printing ignores the selected products (`ProductsScreen.kt` about 2911 passes `allProductsList`); "by stock" count ignores the 500 cap (`ProductCodePrintDialogs.kt` about 592).
  - Z-report has no counted cash, variance or reason (`SalesReportData` VM about 2400-2427, `generateZReportJpeg`); the top-bar "Z-Report" button prints a final-looking Z mid-shift (`RegisterCloseScreen.kt` about 342). Add the fields; rename the button "Preview Z".
  - Shared customer statement lists only payments, in English only (`DuesScreen.kt` about 1629-1667). Merge in charges with a running balance.
  - Idle auto-lock while the app stays open; current lock uses `System.currentTimeMillis()` (use `SystemClock.elapsedRealtime()`).
  - Stock audit history never shows who made the change, and most rows have an empty `cashierName` (VM about 1678, 1707; `ProductsScreen.kt` about 2793). Set it from `sessionUser` in the repo and display it.
  - Expense edit writes no audit log (repo about 4657) and fails silently (`ExpensesScreen.kt` about 472-484).

- [x] **R20-41 MED Reported. Due reminders.** (Fixed in `f3bf87d`).
  - `utils/DueReminderScheduler.kt` about 327-390 logs "NOTIFIED" and deletes one-shot rows even when the notification was blocked. Fix: log "FAILED" and keep or reschedule.
  - Reminder Hub "Send now" (`DuesScreen.kt` about 1230-1234) always opens WhatsApp, cancels weekly series, and uses the stale amount. Fix: honour `reminder.channel`, refresh the amount, only cancel one-shots.
  - Notification WhatsApp link keeps a raw "+" (about 432-436). Use digits only, as `BillActions` does.

- [x] **R20-42 MED Reported. Bluetooth.** (Fixed in `f3bf87d`).
  - `printer/BluetoothPrinterTransport.kt` about 92-94: if `insecureSocket.connect()` throws, `socket` is still null, so `finally` never closes it. Fix: close the insecure socket in its own catch and rethrow.
  - Bluetooth permission is only requested in the device picker (SettingsScreen about 1369); printing shows a toast and does nothing if it was revoked. Fix: request on `NoPermission` or link to app settings.

- [x] **R20-43 MED Reported. "Delete" and "Edit sale" are offered on invoices with returns and always fail with a generic message.** (Fixed in `f3bf87d`). `ReceiptSummaryDialog.kt` about 853, 885; `SalesHistoryScreen.kt` about 852-862, 2197-2206; repo about 504. Fix: hide them when `returnSummary?.hasReturns == true`, or show `getLocalizedRepositoryErrorMessage`.

- [x] **R20-44 MED Reported. Voucher slip prints cancelled, expired or used vouchers as valid.** (Fixed in `f3bf87d`). `ui/screens/StoreCreditVoucherSlipDialog.kt` about 455-462. Fix: show a status badge and disable print/share when not ACTIVE or past expiry.

---

## P4: UX polish (LOW unless noted)

- [x] **R20-45** (Fixed in `e7d4eb5`). Cart line removal is one tap with no undo; supervisor-approved "Clear cart" skips the confirm (`CartScreen.kt` about 983, 723-731). Add a Snackbar with Undo; route the supervisor path to the confirm dialog.
- [x] **R20-46** (Fixed in `e7d4eb5`). Cash-received field does not sanitise Bengali digits or comma (`CartScreen.kt` about 2158-2161, chips about 2114). Use `sanitizeDiscountInput` or `toEnglishDigits`. Same pattern: `AddEditProductDialog.kt` about 913, 951, 996, 1257; `ProductsScreen.kt` about 3373, 3617; expense amount (`AdvancedPosFeatures.kt` about 1280, 1322); `DuesScreen.kt` about 5674; `CustomerDossierSheet.kt` about 1144; `CustomItemDialogs.kt` about 96, 705, 741, 813, 833.
- [x] **R20-47** (Fixed in `e7d4eb5`). Scanner snackbar total is stale (reads `grandTotal.value` right after changing items, VM about 1220, 1495) and shows "2.0" and `sellingPrice` regardless of price mode (`ScannerScreen.kt` about 240, 479, 523).
- [x] **R20-48** (Fixed in `e7d4eb5`). Supervisor dialog says "Invalid PIN" during a lockout (`SupervisorAuthDialog.kt` about 51; repo about 4029). Show "locked for N s".
- [x] **R20-49** (Fixed in `e7d4eb5`). Deactivated user sees "Incorrect PIN" (repo about 4034-4036 returns null; VM about 3676 branch never runs).
- [x] **R20-50** (Fixed in `e7d4eb5`). PIN recovery success shows in the red error box; both fields are labelled "PIN"; mismatch gives no feedback (`LockScreen.kt` about 474-492, 547, 563, 586).
- [x] **R20-51** (Fixed in `e7d4eb5`). User-management errors are raw English or wrong; every toggle/delete failure says "The only owner account cannot be removed" (`ManageUsersScreen.kt` about 134, 193, 495, 656). Map with `getLocalizedRepositoryErrorMessage`.
- [x] **R20-52** (Fixed in `24b5659`). Splash waits about 2.6 s doing nothing; progress is chosen by comparing English literals (`SplashScreen.kt` about 73-82, 164-167).
- [x] **R20-53** (Fixed in `24b5659`). Product form loses everything on rotation (`AddEditProductDialog.kt` about 119-176 use `remember`). Use `rememberSaveable`.
- [x] **R20-54** (Fixed in `24b5659`). A hardware scan during stock check replaces the open product and discards the count (`ProductsScreen.kt` about 588-600).
- [x] **R20-55** (Fixed in `24b5659`). Products empty state flashes before data loads (VM about 1580-1584 start as `emptyList()`).
- [x] **R20-56** (Fixed in `24b5659`). Delete from the edit dialog asks twice (`AddEditProductDialog.kt` about 1593-1612 then `ProductsScreen.kt` about 2758).
- [x] **R20-57 MED** (Fixed in `24b5659`). Category manager: "Reset defaults" writes immediately with no confirm, delete has no confirm, duplicate names allowed and renaming merges silently (`ManageCategoriesDialog.kt` about 62-92, 365-375, 399-404).
- [x] **R20-58 MED** (Fixed in `24b5659`). Picked product images are decoded full size on the main thread with no EXIF rotation (`ProductsScreen.kt` about 3771-3800). Use `decodeSampledBitmapFromUri` (about 3835), apply orientation, run on `Dispatchers.IO`.
- [x] **R20-59** (Fixed in `24b5659`). `StockAlertWorker` repeats forever for expired and 0-stock items and uses 7 days while the UI uses 30 (`utils/StockAlertWorker.kt` about 37-41).
- [x] **R20-60** (Fixed in `24b5659`). Sales Report shows "no sales" (and disables CSV) on a returns-only or expenses-only day (`SalesReportScreen.kt` about 248, 382); Sales History custom range with end before start is silently empty (`SalesHistoryScreen.kt` about 102).
- [x] **R20-61** (Fixed in `24b5659`). Printed Z-report is clipped when returns and tax are both present (`BillPdfGenerator.kt` about 1104-1110); loyalty redeemed is subtracted from net profit but not printed (about 1194-1198; `RegisterCloseScreen.kt` about 2788-2794).
- [x] **R20-62** (Fixed in `24b5659`). Loyalty: Deactivate has no confirm and no Reactivate; "Save profile" silently reactivates (`LoyaltyScreen.kt` about 1152; repo about 4935-4941). Settings accept `takaPerPoint = 0`, `maxBillPercent = 150`, negative values (about 1460-1470).
- [x] **R20-63** (Fixed in `24b5659`). Expense rows show only the time though the list covers all dates (`AdvancedPosFeatures.kt` about 1433). Show date or day headers; add a date filter.
- [x] **R20-64** (Fixed in `24b5659`). Replacing the shop logo does not refresh the preview (same filename, `SettingsScreen.kt` about 1860); Backup hub shows fixed "100% Verified" / "AES-256 Armed" (about 2905-2913); version labels are hard-coded ("v2.6 POS" about 4344; `BackupPackageManager.kt` about 38). Use `BuildConfig.VERSION_NAME`.
- [x] **R20-65** (Fixed in `24b5659`). Crash Logs list shows the file name instead of the exception: `utils/CrashLogger.kt` about 77 reads line index 6 (blank). Use index 7.
- [x] **R20-66** (Fixed in `24b5659`). `android:windowSoftInputMode` is set on `<application>` in the manifest, where it is ignored. Move it to the `<activity>`.

---

## P5: Localisation (i18n)
Move each literal into `values/strings.xml` and `values-bn/strings.xml`.

- [x] **R20-67 LockScreen and Splash**: (Fixed in `d7d9cbb`). `LockScreen.kt` about 160, 165-166, 169, 214, 219, 231, 475, 661, 954, 970; AuthViewModel messages VM about 3672, 3677, 3685; `SplashScreen.kt` about 73, 77, 79.
- [x] **R20-68 Return and register close**: (Fixed in `293d67a`). `ReturnDialog.kt` about 164, 694, 858, 958, 975-977, 1150, 1276, 1341, 1353, 1455-1476, 1674, 1741; `RegisterCloseScreen.kt` about 286, 311, 339, 718, 884, 1144, 2379, 2398-2400, 2555, 2562, 2616, 2653, 2683-2783 (inline `isBn` ternaries), 2999-3071 (CSV and share titles).
- [ ] **R20-69 Printing and history**: `BillActions.kt` about 444, 477, 504, 530, 750, 757, 1009; `SalesHistoryScreen.kt` about 1979, 1990, 2013; `OwnerProfitAnalyticsComponents.kt` about 226, 239, 365, 390, 404, 414-416, 581; `ReceiptSummaryDialog.kt` about 62 (default receipt language should follow the app language), 671, 914.
- [ ] **R20-70 Products**: `AddEditProductDialog.kt` about 1106, 1149; `ProductCodePrintDialogs.kt` about 400, 805, 811; `ProductCodeImageUtils.kt` about 52, 544; `StockAlertWorker.kt` about 24, 93-103; category chips show the English name and a guessed emoji (`ProductsScreen.kt` about 1728-1749, 4779).
- [ ] **R20-71 Settings and backup**: `SettingsScreen.kt` about 3093, 3354-3355 (`lowercase()` frequency), 4662, 5524, 5561, 5565, 5584, 5634, 5680; `BackupPackageManager.kt` about 293, 302, 309, 316, 412; channel names `AutoBackupWorker.kt` about 218. Workers use the system locale; build a localized context from `languageFlow`.
- [ ] **R20-72 Dues, customers, vouchers**: `DuesScreen.kt` about 896, 901, 906, 1814-1818, 3031-3036, 3239, 4743, 4779, 6426, 6487, 6626-6855 (48 inline `isAppBangla` pairs); enums at about 1668-1685 and `DueReminderScheduler.kt` about 37-42 (use `@StringRes`); CustomerViewModel errors (VM about 4925-4931); `StoreCreditVoucherSlipDialog.kt` about 84, 198, 455, 740; reminder `{due}` uses the app language not the template language (`DuesScreen.kt` about 1737).
- [ ] **R20-73 Scanner and cart**: number formatting "12.0" in Latin digits (`ScannerScreen.kt` about 240, 479, 523).

---

## P6: Design consistency and dark mode

Cross-app counts in `ui/`: `Color(0x...)` 2360 uses in 34 files; `Color.White/Black/Gray/DarkGray/LightGray` 540; raw `fontSize = N.sp` 1900. The most common hex values copy existing tokens in `theme/Color.kt` (`0xFF059669` 150 times, `0xFF10B981` 139, `0xFFF59E0B` 104, `0xFFEF4444` 60).

- [x] **R20-74 MED Add semantic tokens first.** (Fixed in `b92f510`). Add an `ExtendedColors` CompositionLocal (success, warning, info, each with container and on-container, light and dark) in `theme/`. Add the missing typography styles in `theme/Type.kt` (bodySmall, labelSmall, labelMedium, titleSmall). Then replace hex values one screen per commit, as round 19 did.
- [x] **R20-75 MED Unreadable dark-mode panels (fix these first).** (Fixed in `b92f510`).
  - `ReturnDialog.kt` about 928-933: DUE and MFS refund panels use fixed light backgrounds with theme text that is light in dark mode. Also about 673, 736, 961, 1146, 1263.
  - `RegisterCloseScreen.kt` about 2397-2400 (variance badge).
  - `SettingsScreen.kt` about 5309, 5411 (`Color.DarkGray` dialog text) and 28 `Color.Gray` uses.
  - Cart: `CartScreen.kt` about 1174, 1379, 1431, 1719, 1994, 2399, 2450, `CartItemRow` `0xFFFEE2E2`; `CartTotalSummaryCard` (about 182-187) builds its own palette; `CartItemDialogs.kt` about 255; `ReceiptSummaryDialog.kt` about 549, 600, 643, 731.
  - Products: `ProductsScreen.kt` about 3438-3470; `ManageCategoriesDialog.kt` about 157-162, 297-301, 382; `AddEditProductDialog.kt` about 1527; `StockAuditDialogs.kt` about 200-207.
  - Dues and customers: `DuesScreen.kt` about 3032-3035; `AdvancedPosFeatures.kt` about 1382, 1412-1415; `ExpensesScreen.kt` about 704-705; `CustomerDossierSheet.kt` about 366-367, 1166; `SuppliersScreen.kt` about 776, 1215.
  - Users: `ManageUsersScreen.kt` about 216-226, 674-676 (amber on `#FEF3C7` about 1.9:1 contrast).
- [x] **R20-76 MED Status bar icons do not follow the in-app theme.** (Fixed in `b92f510`). `MainActivity.kt` about 59 calls `enableEdgeToEdge()` with defaults. Pass `SystemBarStyle.auto(..., detectDarkMode = { isDark })` or set `isAppearanceLightStatusBars` in a `SideEffect`.
- [x] **R20-77 LOW** (Fixed in `b92f510`). `themes.xml` sets window, status and nav bar backgrounds to `#070709` in every mode with no `values-night`, so light mode flashes black on cold start.
- [x] **R20-78 LOW** (Fixed in `b92f510`). Tablet rail hard-codes colours (Navigation.kt about 669-672, 848-851, 893-902, 928-957) while the phone bar uses the theme.
- [x] **R20-79 LOW** (Fixed in `b92f510`). Low-contrast and tiny text: Splash footer `#475569` on `#070709` (about 2.6:1, `SplashScreen.kt` about 188); LockScreen tagline 7.5 to 8.5sp (`LockScreen.kt` about 660-664). Use at least 10sp and 4.5:1.

---

## Checked and found fine
- Room migrations 1 to 38 have no gaps; schemas 9 to 38 are exported.
- R8/proguard rules cover POI, XMLBeans, Google API client/Drive and ML Kit.
- Manifest: receivers not exported; `allowBackup=false`; Bluetooth permissions correct for Android 12+.
- Restore guards against zip-slip and size bombs and verifies checksums.
- ESC/POS GS v 0 banding, TSPL syntax and `decidePrintRouting` look correct.
- `normalizePhone`, due-payment caps, supplier payment-exceeds-due check, customer delete with dues, expense delete confirm and audit.
- R19-1 (partial store credit plus cash) is fixed in the current code.
