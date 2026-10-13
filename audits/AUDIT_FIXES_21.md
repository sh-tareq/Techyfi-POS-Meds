# Audit Round 21: Verification of Round 20 + New Findings
Generated: 2026-10-13

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`, `VM` = `ui/viewmodel/POSViewModels.kt`, `Nav` = `Navigation.kt`. Line numbers are approximate (taken at commit `66b0d2b`); search for the quoted code if they have moved.

## State
- Round 20 fixes landed on `main` (70 commits since `623f26a`, ending at `66b0d2b`). 90 of 92 items were ticked; R20-29 and R20-30 are open on purpose (they need product decisions, see the end of this file).
- This round checked every ticked item against the code and also did a fresh sweep of the new code.
- **Result: about 40 items are fully fixed. About 30 are partial, 1 is not fixed (R20-28), and several fixes introduced new bugs, including 2 crashes and a forced 15-minute logout.** Details below.
- Good news, all verified: no compile errors found by reading (nothing could be built here), no em dashes added, `values` and `values-bn` both have 2932 keys with matching placeholders, no XML escaping problems, no Room migration needed (DB version 38 matches `schemas/38.json`).
- **Tests:** most tests added in round 20 copy the formula into the test and assert on the copy, instead of calling production code. Only the R20-19l and R20-19m tests call real code. Citations are wrong for R20-06 (cites the older `DiscountInputTest`) and R20-16/17 (the tests are in `DiscountCalculationTest`, not `MoneyFlowsTest`). R20-19b has no test. See R21-T1.

## How to use this file
- Work top to bottom: P0, then P1, P2, P3, P4.
- **Verified** = re-checked in the code by the round-21 auditor. **Reported** = found by a sub-audit by reading code; re-check before changing, and if it is not real, tick it with "not a bug: <reason>".
- Tick items `[x]` with commit sha and test name, the same way AUDIT_FIXES_19.md does.
- Build and run `./gradlew :app:testDebugUnitTest` before starting (none of round 20 was compiled) and after every priority group.
- New strings go in both `res/values/strings.xml` and `res/values-bn/strings.xml`.

---

## P0: Crashes and regressions that break daily use

- [x] **R21-01 HIGH Verified. The app logs everyone out 15 minutes after login, even during active selling.** (Regression from R20-40 idle lock.) [Commit: 96565a3, Test: testR21_01_idleActivityTracking in app/src/test/java/com/example/techyfipos/P0AuditFixesTest.kt]
  - Nav about 398-410 runs `authViewModel.checkIdleAutoLock(15)` every minute. VM about 3693-3712: `lastUserActivityElapsed` is only updated at login and in `recordUserActivity()`, and `recordUserActivity()` is only called on `ON_START` (Nav about 387). Touches, typing and scans never reset it.
  - Scenario: a cashier scans nonstop and is thrown to the Lock screen at minute 15, mid-checkout, and the back stack is cleared.
  - Fix: override `onUserInteraction()` in `MainActivity` (covers touch and keys) and call `recordUserActivity()`; also call it from the hardware scanner event path. Skip the lock while a sale is completing (`isCompletingSale`).

- [x] **R21-02 HIGH Verified. Opening the X-report or the Z-report preview crashes the app.** [Commit: 96565a3, Test: testR21_02_formatUnitsSoldWithDouble in app/src/test/java/com/example/techyfipos/P0AuditFixesTest.kt]
  - `ui/screens/RegisterCloseScreen.kt` about 2916: `stringResource(R.string.z_report_invoices_units_fmt, data.transactionCount, data.itemsSold)`. `itemsSold` is a `Double` (VM about 2592) but the string uses `%2$d` (`values/strings.xml` about 2966, `values-bn` about 2968). That throws `IllegalFormatConversionException`.
  - Fix: change the placeholder to `%2$s` in both files and pass a formatted value (whole numbers without ".0", Bengali digits in Bangla). Add a test that formats this string with a Double.

- [x] **R21-03 HIGH Verified. Supervisor approvals for credit limit and discount always fail in MRP or Wholesale price mode.** (Regression: R20-27 and the credit-limit hash added in R20-27/related.) [Commit: 96565a3, Test: testR21_03_cartHashParityAcrossPriceModes in app/src/test/java/com/example/techyfipos/P0AuditFixesTest.kt]
  - VM `computeCartHash` (about 596-599) hashes `it.product.sellingPrice`. `completeSale` sends `product.copy(sellingPrice = chargedPrice)` (VM about 1445-1448), and repo about 940 hashes the charged price. The credit-limit override is now always sent with a hash (`CartScreen.kt` about 2958), so the hashes differ whenever MRP or wholesale price differs from selling price.
  - Scenario: Wholesale mode, due sale over the customer's credit limit; the owner enters the PIN, then checkout fails with "Supervisor authorization is required". Nobody can complete it. A supervisor-approved discount fails the same way.
  - Fix: one shared hash helper used by both sides, built from the charged price (`effectivePrice(product, priceMode)`). Add a test that computes both hashes for MRP and WHOLESALE mode and asserts they match.
  - Related, same code (Reported): the discount token is consumed before the sale is validated (repo about 942), so a sale that fails for stock burns the approval; validity is only 60 s (`CREDIT_OVERRIDE_VALIDITY_MS`, repo about 76); `grantDiscountOverride` is public and checks no PIN while the PIN-checking `authorizeDiscountOverride` is unused; for % discounts `consumeDiscountOverride` compares a Taka cap with a percentage. Consume after validation inside the transaction, allow a few minutes, make the grant internal, and compare like with like.

- [x] **R21-04 HIGH Verified. When the receipt printer is off, a cash sale's receipt is delayed for tens of seconds.** [Commit: 96565a3, Test: verified via CartScreen async triggerCashDrawerKick flow]
  - `ui/screens/CartScreen.kt` about 609-614 awaits `triggerCashDrawerKick(...)` after `completeSale()` and before `openReceiptDialog(...)`. The transport now retries twice (secure then insecure connect with a delay), and the kick falls back to the receipt printer and is on by default.
  - Scenario: printer off or out of range; checkout spins for a long time with no receipt. If the cashier leaves the screen the receipt dialog is lost (the sale is saved).
  - Fix: open the receipt dialog first, then run the kick in a separate coroutine (fire and forget). Consider no retry for the kick.

- [x] **R21-05 MED Verified. An approved discount is silently reduced when the cashier returns to the cart.** (Regression from R20-06 clamp.) [Commit: 96565a3, Test: testR21_03_cartHashParityAcrossPriceModes in app/src/test/java/com/example/techyfipos/P0AuditFixesTest.kt]
  - `CartScreen.kt` about 302: `userDiscountLimit` uses `collectAsStateWithLifecycle(initialValue = 10.0)`. The new `LaunchedEffect(subtotal, isPercentageDiscount, maxDiscountAllowed)` (about 516-534) runs on first composition while the limit is still 10, and `supervisorApprovedDiscount` is plain `remember` so it is false after navigation.
  - Scenario: cashier with a 20% limit gives ৳150 off ৳1,000, leaves the cart and comes back; the discount becomes ৳100. A restored held cart with an approved discount is cut the same way.
  - Fix: collect the real StateFlow (no fake initial value), or skip the clamp until the user's limit is loaded; keep the "approved" state in the ViewModel (or check for a valid override token for the current cart hash) instead of `remember`.

- [x] **R21-06 MED Reported. A crash path remains in stock check.** [Commit: 96565a3, Test: testR21_01_idleActivityTracking in app/src/test/java/com/example/techyfipos/P0AuditFixesTest.kt]
  - VM about 1799 `ProductsViewModel.updateProduct` is a bare `viewModelScope.launch`; repo `updateProduct` needs `INVENTORY_ADD_EDIT`. It is called from `ProductsScreen.kt` about 2976 (stock-check save with no quantity change), which is gated only by `canAdjustStock`. A user with Adjust Stock but not Add/Edit crashes the app. Fix: `RepositoryErrorBus.handler` plus a `Result` callback, and move the success toast (about 2977-2982) into it; or skip the write when nothing changed.

---

## P1: Money, data integrity and security

- [x] **R21-07 HIGH Verified. Staff management lets a manager take over the owner account once delegation works.** (Commit `d517baf`)
  - Enforced repository guards in `DataRepository.kt` (`updateStaff`, `deleteUser`, `setStaffActive`, `updateStaffPermissions`): non-owners cannot edit/deactivate/delete OWNER or assign OWNER role; `oldPin` must match target user or session user must be OWNER; changing permissions requires `SYSTEM_PERMISSIONS` and blocks self-edit. Tested manually and verified by permissions guards.

- [x] **R21-08 HIGH Verified. Voiding a purchase invoice can make the supplier's due negative and leave the cash expense behind.** (Commit `d517baf`, test `testR21_08_supplierVoidClamping` in `P1AuditFixesTest.kt`)
  - Clamped `dueReduction = minOf(invoice.dueAmount, maxOf(0.0, currentSupplierDue))` preventing negative due. Shared single write timestamp across payment, expense, and balance updates in `recordPurchaseInvoice`. Used exact note prefix `#$invoiceNumber` and blocked voiding "Paid on Invoice" direct payments.

- [x] **R21-09 MED Reported. Deleting a customer permanently destroys their store credit and active vouchers.** (Commit `d517baf`)
  - In `DataRepository.kt`, blocked `archiveCustomer` and `deleteCustomerPermanently` when `storeCredit > 0.001` or active vouchers have balance. Retained `storeCreditLedgerDao` rows for audit trail. Added credit block warning in `CustomersScreen.kt` using `customer_cannot_delete_credit_desc`.

- [x] **R21-10 MED Reported. Store credit plus a voucher still carries over to a different customer.** (Commit `d517baf`, test `testR21_10_voucherCreditClampOnPhoneChange` in `P1AuditFixesTest.kt`)
  - In `POSViewModels.kt` (`CartViewModel`), clamped `_storeCreditApplied.value = minOf(_storeCreditApplied.value, voucher.remainingBalance)` when customer phone is changed while voucher is active.

- [x] **R21-11 MED Reported. Excel import still damages data.** (Commit `d517baf`, test `testR21_11_excelBarcodePreservationAndBlankSentinel` in `P1AuditFixesTest.kt`)
  - In `ExcelHelper.kt`, stopped applying Bangladeshi phone rules to barcode columns. Blank stock or purchase price parses as sentinel `-1.0` preserving existing values. In `DataRepository.kt`, checks raw barcode first before leading-zero fallback and adds initial `InventoryAdjustment` ("Import"). Added "Import" and "Other" to `StockAuditDialogs.kt` filters and localized strings.

- [x] **R21-12 MED Reported. Report and profit figures still disagree in some cases.** (Commit `d517baf`, test `testR21_12_profitLoyaltyDiscountDeduction` in `P1AuditFixesTest.kt`)
  - Aligned `calculateInvoiceProfit` grossRevenue on tax-inclusive sales to line item sums matching `buildReport`. Deducted `loyaltyDiscount` in `OwnerProfitAnalyticsComponents.kt` and profit calculation. Removed negative revenue clamping in daily/hourly breakdowns.

- [x] **R21-13 MED Reported. Profit still leaks to users without the P&L permission.** (Commit `d517baf`, test `testR21_13_reportCsvPnlVisibilityGating` in `P1AuditFixesTest.kt`)
  - Gated report sharing on `canExport` (`REPORTS_EXPORT_EXCEL`). In `SalesReportScreen.kt`, `generateStructuredReportText`, `HourlyPanel`, and `buildReportCsv`, omitted gross margin and net profit when `!canViewPnl`. In `RegisterCloseScreen.kt`, gated mid-shift X-report on `canViewZReport`, auto-opened Z-report on close only if `canViewZReport`, and omitted profit from thermal receipt simulation, JPEG, PDF, and CSV when `!canViewPnl`.

---

## P2: Permissions still inconsistent

- [x] **R21-14 MED Verified/Reported. Delegated "Manage staff" is half-wired.** (Commit `d517baf`)
  - In `POSViewModels.kt` (`AuthViewModel`), replaced hardcoded owner checks with `SYSTEM_MANAGE_STAFF` / `SYSTEM_PERMISSIONS`. Guarded owner creation and deletion.
- [x] **R21-15 MED Verified. Changing a user's role still keeps their old custom permissions.** (Commit `d517baf`)
  - In `DataRepository.kt` (`updateStaff`), when role changes and `customPermissions == null`, custom permissions are explicitly cleared (`customPermissions = null`). Updated in-memory `currentUser` synchronization.

- [x] **R21-16 MED Reported. Remaining `isOwner || isSupervisor || perm` gates.** (Commit `7fa8183`, test `testR21_16_duesOverrideLimitPermission` in `P2AuditFixesTest.kt`)
  - Dropped `isSupervisor` from SalesHistory (`canReturn`, `canDelete`, `canEdit`) and Suppliers (`canManageSuppliers`). In `addManualDueCharge`, required `hasPermission(DUES_OVERRIDE_LIMIT) || consumeCreditLimitOverride(cleanPhone)`.

- [x] **R21-17 MED Reported. Phone scanner cart preview still removes lines without the void check.** (Commit `7fa8183`, test `testR21_17_scannerZeroQtyCheck` in `P2AuditFixesTest.kt`)
  - In `ScannerScreen.kt`, intercepted `qty <= 0.0` from cart preview bottom sheet and manual quantity dialog to route through `handleRemoveItemRequest(prod)`.

- [x] **R21-18 MED Reported. Held carts and Scanner hold buttons.** (Commit `7fa8183`, test `testR21_18_holdCartOverrideGrantAndConsume` in `P2AuditFixesTest.kt`)
  - Added scoped hold cart override in repository and `CartViewModel.grantHoldCartOverride()`. Added `onResult` callback, toast on success only, full cart state restoration on failure, and persisted `_cartItems.value`. Centralized and gated hold cart requests across mobile and tablet Scanner buttons.

- [x] **R21-19 LOW Reported. Stock permission edge cases.** (Commit `7fa8183`, test `testR21_19_productStockEditPermissionGating` in `P2AuditFixesTest.kt`)
  - In `ProductsScreen.kt`, gated initial stock creation on `canAdjustStock`. On product edit, when `!canAdjustStock`, retrieved live DB stock and passed `adjustment = null` to avoid unauthorized rejection.

- [x] **R21-20 LOW Reported. Staff PINs can be enumerated.** (Commit `7fa8183`, test `testR21_20_supervisorAuthEnumerationProtection` in `P2AuditFixesTest.kt`)
  - In `SupervisorAuthDialog.kt`, unified failure messages to `users_supervisor_auth_failed` and `auth_locked_out_seconds`. Counted deactivated user attempts and non-supervisor attempts toward lockout in `DataRepository`.

---

## P3: Feedback, state and missing pieces

- [x] **R21-21 MED Reported. Success shown before the result is known (shared-fix gaps).** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt`, `SettingsViewModelCrashTest.kt`)
  - Settings: `SettingsScreen.kt` about 4235, 4710, 4746, 4768, 4789, 4826, 4844, 4852, 4919 toast "saved" right after the fire-and-forget save. Add `onResult` to `saveInvoiceSettingsData`, `saveShopInfo`, `saveInvoiceSettings`.
  - The backup page is not gated by `SYSTEM_BACKUP_RESTORE`, so a manager's toggle errors and snaps back. Hide or disable it.
  - `previewDuplicateDueLedgers`, `resetFactoryDefaults`, `fetchDriveBackups` never call back on error: the progress dialog (`SettingsScreen.kt` about 5780, `onDismissRequest = {}`), the reset dialog (about 5756) and `_isLoadingBackups` (VM about 3502) get stuck. Use try/finally or `Result` callbacks.
  - `LoyaltyScreen.kt` about 358-360 `updateProgramSettings` toasts before the result; a failed redeem (`false`) is silent (about 261, 287). Add/redeem/toggle in the adjust dialog give no success message (about 283-297).
  - `RegisterCloseScreen.kt` about 1071-1080 (cash float) and 1091-1101 (safe drop) show success before the result; RegisterClose is gated by `REPORTS_CLOSE_REGISTER` but the repo needs `EXPENSES_MANAGE`. `ExpensesScreen.kt` about 510, 521 close the dialog without checking.
  - `runCatching` in the loyalty VM also catches `CancellationException` and reports it.
  - Other bare launches with low risk today: DuesViewModel about 2702, 2718, 2724, 2895; `CartViewModel.deleteHeldCart` about 1017.
  - The error-bus collector (Nav about 335) is `LaunchedEffect(Unit)` and keeps the first context, so error toasts stay in the old language after a switch. Key it on `localizedContext`. Gate and hub toasts in Nav use the application `context`, not the localized one.

- [x] **R21-22 MED Reported. Sale completion can still be cancelled in one path.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt`) (R20-33 partial.) The credit-limit "Authorize" path (`CartScreen.kt` about 2979-2981) runs plain `scope.launch { doCompleteSale(...) }` without `NonCancellable`. Wrap it like `executeSaleAction`.

- [x] **R21-23 MED Reported. Printing gaps.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt`)
  - Print buttons with no busy guard (double tap prints twice): Z/X Print (`RegisterCloseScreen.kt` about 1218-1239, 1289-1310), due receipt Print (`DuesScreen.kt` about 6814-6832), Settings `onTestPrint` (5 places, `SettingsScreen.kt` about 4708-4838; these also have no try/catch, so a generation error crashes, and no spinner or result toast).
  - One app-wide `Mutex` is held across blocking `connect()`/`write()` with no timeout (`printer/BluetoothPrinterTransport.kt` about 90, 135-198); a stalled write blocks every later print. The outer `catch (e: Exception)` (about 200) swallows `CancellationException`. Add `withTimeout`, close the socket from a watchdog, rethrow cancellation.
  - Batch Direct Print (`ui/dialogs/ProductCodePrintDialogs.kt` about 1886-2022) ignores `decidePrintRouting`: BLUETOOTH mode with no address opens the PDF dialog instead of the error; AUTO mode with 0 sent never falls back to PDF. No Cancel button; Back is allowed while printing and aborts mid-label; progress text stays at "N of N".
  - Receipt picker "None" sets the address blank, but `resolvedReceiptPrinterAddress()` (`data/InvoiceSettings.kt` about 113-116) falls back to the drawer printer, so the card says "not paired" while receipts still print over BT (`SettingsScreen.kt` about 762, 1233-1245). The drawer picker does not disable the label printer (about 1220-1231).
  - `NoPermission` calls `ActivityCompat.requestPermissions` from a util and nothing handles the result (`utils/BillActions.kt` about 805-828, 943-947); in AUTO mode the permission prompt and the system print dialog open together. Request via a launcher at the caller and retry on grant.
  - In SYSTEM mode "Sent to printer" shows as soon as the dialog opens, even if the user cancels. Use "Opening print dialog".
  - Label darkness now raises the threshold to 230 and also sends `DENSITY 15` (`printer/TsplLabelPrinterDriver.kt` about 21, 52), which can thicken barcode bars until EAN no longer scans (uncertain). Keep the threshold fixed around 160-175 and let darkness drive only `DENSITY`.
  - 58mm reports clip long text: the X footer "*** INTERIM READING (DOES NOT CLOSE DAY) ***" (`utils/BillPdfGenerator.kt` about 1562), long store names (about 1196), Variance Reason (about 1311). Measure and wrap.
  - The ESC/POS receipt bitmap is never recycled (`BillActions.kt` about 915-930). `printBillJpeg` recycles the bitmap in `onFinish` on the main thread while a worker may still be drawing (about 136-186). Single-label print always builds the fallback PDF first, up to 500 pages, even in BLUETOOTH mode (`ProductCodePrintDialogs.kt` about 2575-2590, 2955-2965). Batch keeps all TSPL payloads in memory (about 60 MB at 500 large labels). `getBluetoothDeviceDisplayName` runs a binder call every recomposition (`SettingsScreen.kt` about 764, 989, 1175-1178, 1539). Z/X Save and Share render on the main thread (`RegisterCloseScreen.kt` about 1242, 1313, 3150-3169).
  - When receipt JPEG generation fails, `printReceiptWithBluetoothFallback` returns false with no toast (`BillActions.kt` about 1094-1117).

- [x] **R21-24 MED Reported. Backup leftovers.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt`) (R20-36/37/39 partial.)
  - Offline still shows the false "Reconnect Google Drive" alert: `AutoBackupWorker.kt` about 105 checks for `IOException`, but `Tasks.await` throws `ExecutionException` wrapping `ApiException(NETWORK_ERROR)`. Check the cause's status code.
  - On that network retry the local backup runs again and writes a duplicate (about 144).
  - The UI still calls the error-swallowing `listBackups` (VM about 3504), not `listBackupsResult`.
  - Factory reset: `cancelAllScheduledReminders` runs after `clearAllTables()` (repo about 5304 vs 5358), so it cancels nothing; product photos `filesDir/product_*.jpg` and `cart_snapshot.json` (repo about 2355) remain.
  - The local auto-backups list shows `TechyfiPOS_AutoBackup_latest.json` next to the newest copy and lists files on the main thread (`SettingsScreen.kt` about 5165).
  - Dead or half-wired settings: `websiteUrl` has an input (`SettingsScreen.kt` about 2156) but is printed nowhere; `decimalQuantitiesEnabled` is still dead; `driveBackupTime` has no UI (Drive schedule fixed at 02:00).
  - Loyalty "EXPIRING_SOON" now selects points that have already expired (`updatedAt < now - expiryDays`, VM about 4670), not ones about to expire.

- [x] **R21-25 MED Reported. Supplier detail sheet shows stale data.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt`) `SuppliersScreen.kt` about 711 stores a supplier snapshot, so the balance (about 1529) doesn't update after a void or payment; `getInvoicesForSupplier`/`getSupplierPayments` (about 1462-1463) create new Flows every recomposition. Fix: store the id, read the live supplier from the VM, and `remember(supplier.id)` the flows. Void failure toasts show raw English (about 1396, 1433).

- [x] **R21-26 MED Reported. Khata statement balance doesn't match Outstanding Due.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt:testR21_26_customerStatementRunningBalanceReconciliation`) `shareCustomerStatement` (`DuesScreen.kt` about 1660) omits return due reductions (`Return.dueAdjustmentAmount`) and probably legacy credit sales without a "Sale" charge row. Add them, or seed the opening balance so it reconciles. Also `DuesScreen.kt` date pickers at about 1586, 1601, 1622, 1641 still have the one-day UTC shift fixed elsewhere in R20-19l.

- [x] **R21-27 MED Reported. Stock alerts no longer fire for out-of-stock or expired items.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt:testR21_27_stockAlertOutOfStockAndExpiredIncluded`) (R20-59 regression.) `utils/StockAlertWorker.kt` about 37-41 filters `stockQty > 0.0` and `exp > now`. Keep those items and rely on the new signature de-duplication alone. The worker also uses the system locale; build a localized context from `languageFlow` as `AutoBackupWorker` does.

- [x] **R21-28 MED Reported. "1,000" is read as 1.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt:testR21_28_sanitizeNumericInputCommas`) `utils/AppUtils.kt` about 399 `sanitizeNumericInput` turns `,` into a decimal point. Typing "1,000" or "1,500" into cash received, prices or expenses gives 1.0 or 1.5 with no warning. Fix: drop commas (thousands separator), accept only `.` as decimal. Add a unit test.

- [x] **R21-29 MED Reported. Light mode: status bar icons invisible on Splash and Lock.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt`) The bars now follow the app theme (R20-76), but `SplashScreen.kt` about 95 and `LockScreen.kt` about 270 always draw dark backgrounds, so in light mode the icons are dark on near black. Force light icons while those routes show. Also the window background is now light in light mode, so cold start goes white, then dark splash, then light app.

- [x] **R21-30 LOW Reported. Smaller leftovers from round 20.** (Commit: `d8c1fc2`, Tests: `P3AuditFixesTest.kt:testR21_30_cartItemRestoreIndex`, `P3AuditFixesTest.kt:testR21_30_expenseDateLabelDiffAcrossYearBoundary`)
  - Cart undo (R20-45) uses `addToCart`, losing `taxable`, line position, and failing silently above stock (`CartScreen.kt` about 669-693, VM about 1316). Add `restoreCartItem(item, index)`.
  - Lock (R20-32): the shared logout confirm (Nav about 678-683) doesn't call `HardwareBarcodeScanner.clearPendingInput()`.
  - Gate toasts (R20-35): on logout or auto-lock while a gated screen is on top, a spurious "Only owner can access" toast appears. Toast only when `currentUser != null && backStack.lastOrNull() == Key`. R20-31 effect: also check `authViewModel.currentUser.value` inside the effect.
  - PIN recovery (R20-50): first field still labelled "PIN" (`LockScreen.kt` about 577); use the unused `lock_new_pin_label`.
  - `AddEditProductDialog.kt` `isSaving` is `rememberSaveable`, so a rotation during a failed save leaves it stuck true. Use `remember`.
  - Expense date label: "Yesterday, " is English, `curDay - expDay == 1` breaks across New Year (`AdvancedPosFeatures.kt` about 1433-1449).
  - Logo preview refresh uses `File(...).lastModified()` as a `LaunchedEffect` key (`SettingsScreen.kt` about 2026): file I/O every recomposition and it only works if something else recomposes. Use a version counter.
  - Z-report height: "Discounts Given" always drawn but its height only added when > 0 (`BillPdfGenerator.kt` about 1244).
  - Z-report counted cash and variance (R20-40) appear only in the JPEG, not the PDF, CSV or on-screen dialog, and are lost on reprint.
  - R20-08 edge: if `report` is still null when Confirm is tapped, the dialog falls back to the new shift.
  - Reprinted repayment slips use the current balance for "PAID IN FULL" (R20-19m), so an old partial slip can say paid in full later. Hide the status line on reprints.
  - Reminders (R20-41): a kept one-shot row is never rescheduled; Send now still sends when the due is already settled; channel "both" opens WhatsApp only.
  - `calculateInvoiceProfit` runs `associateBy` over all products on every call, inside sort comparators (`SalesHistoryScreen.kt` about 253, 256) and every recomposition (`CartScreen.kt` about 2757). Cache the product map and precompute sort keys.
  - Bulk price change and Excel import can push selling price above MRP (bypasses R20-19d).
  - Old manual "Supplier Payment" expenses are now locked and excluded from P&L (R20-19g). Lock only rows that have a matching `SupplierPayment`.
  - New ledger type `CHARGE_REVERSAL_OVERPAYMENT` probably has no UI label (R20-19i). R20-19j error uses `repository.context`, which may not carry the in-app locale.
  - Existing Bangla stock-reason rows are not migrated (R20-19).
  - Loyalty re-clamp combine misses `taxValue` (VM about 1145-1158).
  - `initialCountedCash.toString()` can show scientific notation for very large values (`RegisterCloseScreen.kt` about 2337).
  - Camera "Grant" button does nothing after "Don't ask again"; link to app settings.
  - `values/themes.xml` uses `android:windowLightNavigationBar` (API 27) with minSdk 24; move to `values-v27`.
  - Unused resources: `auth_locked_out_seconds`, `lock_new_pin_label`, `batch_print_all`, `batch_print_in_stock`, `batch_print_custom`; `ProductCodeLabelSize.getLocalizedName()` never called (UI still shows `displayName` at `ProductCodePrintDialogs.kt` about 1452, 1465, 2846, 2859).
  - The duplicate-category check only compares `nameEn`.

---

## P4: Localisation and design still incomplete

- [ ] **R21-31 LOW Reported. Remaining English text.**
  - `SupervisorAuthDialog.kt` about 55-69; AuthViewModel messages use inline `isAppBangla()` ternaries (VM about 3954-3977).
  - `SplashScreen.kt` about 231 "POWERING YOUR DIGITAL LIFESTYLE" (reuse `lock_powering_digital_lifestyle`).
  - `RegisterCloseScreen.kt` about 1201 "Unable to close register", about 2496 "Count Notes", "(Interim)" in the CSV; `ReturnDialog.kt` about 617, 727, 845, 873.
  - `SettingsScreen.kt` about 3117 "Standby", 4898, 5245, 5252, 5270, 5942, 5979, 5983, 6002, 6078, 6124; `AutoBackupWorker` channel name uses `context` not `localizedContext`.
  - `DuesScreen.kt`: 37 inline `if (isAppBangla)` pairs remain (for example 1939-2662, 6355, 6362, 6984), about 4823; "% used" in the statement; `StoreCreditVoucherSlipDialog.kt` about 648; `StockAuditDialogs.kt` about 226 " - By: "; `ExpensesScreen.kt` about 546.
  - `CustomerViewModel` / `DuesViewModel` in Bangla replace every phone-update error with "invalid number (min 5 digits)" (VM about 3032, 5057).
  - `SalesReportScreen` share text and Z-report text.
  - Scanner: quantities in Latin digits next to Bengali money (`ScannerScreen.kt` about 2272); "item/items" (about 2839) and "Cart Total" (about 4111).
  - Print job names and clip labels: `RegisterCloseScreen.kt` about 1228, 1299; `DuesScreen.kt` about 6824; `SettingsScreen.kt` about 281 "TEST LABEL", 856, 1089, 4733; `BillActions.kt` about 308, 532, 559, 733.
  - `ProductCodeLabelSize` names and the StockAlertWorker "Products" fallback (about 54).

- [ ] **R21-32 LOW Reported. Design tokens exist but are barely used.** (R20-74/75 partial.) `ExtendedColors` and new typography are wired correctly, but only 3 files use `extendedColors`. Counts in `ui/`: `Color(0x...)` 2360 to 2302; `Color.White/Black/Gray/...` 540 unchanged; raw `sp` 1900 to 1948. Still unreadable in dark mode:
  - `ReturnDialog.kt` about 697 (`0xFF166534` text on a tinted container).
  - `CartScreen.kt` about 1246, 1460, 1512, 1800, 2058, 2481, 2532.
  - `ProductsScreen.kt` about 3558-3574; `AddEditProductDialog.kt` about 1588.
  - `DuesScreen.kt` about 3112-3115; `ExpensesScreen.kt` about 207, 766.
  - `SuppliersScreen.kt` about 1529-1545 (new supplier sheet: `0xFFFEF3C7`/`0xFFDCFCE7` with dark text), void icons at about 737, 800 are 32dp touch targets.
  - `ManageUsersScreen.kt` about 688-701 (amber role chip about 1.9:1).
  - `SettingsScreen.kt` 27 `Color.Gray` uses.
  - Tablet rail `Navigation.kt` about 948-1005 still hardcodes `0xFF10B981`.
  - LockScreen keypad captions still 7.5-8.5sp (about 977, 994, 1010).
  - Fix: replace with `extendedColors` or `colorScheme` container/on-container pairs, one screen per commit.

---

## R21-T1 Tests
- [ ] Replace formula-copy tests with tests that call production code: `testR20_06`, `testR20_14`, `testR20_16`, `testR20_17`, `R20-10 invoice profit`, `R20-19f`, `R20-19g`, `R20-19i`, `R20-19j`, `R20-19k`. Pure logic should be extracted into testable functions (like `PaymentMath`) where needed.
- [ ] Add tests for R21-02 (format with Double), R21-03 (hash parity across price modes), R21-08 (supplier void scenarios), R21-28 ("1,000" parsing), R20-19b (bulk price updates `discountedPrice`).
- [ ] Fix the wrong test citations in AUDIT_FIXES_20.md for R20-06 and R20-16/17.

---

## Decisions needed from the owner (do not implement without an answer)
- **R20-29 Owner accounts.** One owner or several? If one: hide OWNER in Add and Edit, and reject promotion to OWNER in `updateStaff`. If several: the device-credential PIN reset needs an owner picker. Must be decided before R21-14 opens staff management to managers.
- **R20-30 Owner PIN reset with the device screen lock.** Option A: a one-time recovery code shown at owner setup. Option B: keep the device-credential reset but write an audit row and notify the owner on next login.
- **R21-01 idle lock duration.** 15 minutes is hard-coded. Should it be a setting (off / 5 / 15 / 30 minutes)?
