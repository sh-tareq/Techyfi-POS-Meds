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

- [ ] **R21-01 HIGH Verified. The app logs everyone out 15 minutes after login, even during active selling.** (Regression from R20-40 idle lock.)
  - Nav about 398-410 runs `authViewModel.checkIdleAutoLock(15)` every minute. VM about 3693-3712: `lastUserActivityElapsed` is only updated at login and in `recordUserActivity()`, and `recordUserActivity()` is only called on `ON_START` (Nav about 387). Touches, typing and scans never reset it.
  - Scenario: a cashier scans nonstop and is thrown to the Lock screen at minute 15, mid-checkout, and the back stack is cleared.
  - Fix: override `onUserInteraction()` in `MainActivity` (covers touch and keys) and call `recordUserActivity()`; also call it from the hardware scanner event path. Skip the lock while a sale is completing (`isCompletingSale`).

- [ ] **R21-02 HIGH Verified. Opening the X-report or the Z-report preview crashes the app.**
  - `ui/screens/RegisterCloseScreen.kt` about 2916: `stringResource(R.string.z_report_invoices_units_fmt, data.transactionCount, data.itemsSold)`. `itemsSold` is a `Double` (VM about 2592) but the string uses `%2$d` (`values/strings.xml` about 2966, `values-bn` about 2968). That throws `IllegalFormatConversionException`.
  - Fix: change the placeholder to `%2$s` in both files and pass a formatted value (whole numbers without ".0", Bengali digits in Bangla). Add a test that formats this string with a Double.

- [ ] **R21-03 HIGH Verified. Supervisor approvals for credit limit and discount always fail in MRP or Wholesale price mode.** (Regression: R20-27 and the credit-limit hash added in R20-27/related.)
  - VM `computeCartHash` (about 596-599) hashes `it.product.sellingPrice`. `completeSale` sends `product.copy(sellingPrice = chargedPrice)` (VM about 1445-1448), and repo about 940 hashes the charged price. The credit-limit override is now always sent with a hash (`CartScreen.kt` about 2958), so the hashes differ whenever MRP or wholesale price differs from selling price.
  - Scenario: Wholesale mode, due sale over the customer's credit limit; the owner enters the PIN, then checkout fails with "Supervisor authorization is required". Nobody can complete it. A supervisor-approved discount fails the same way.
  - Fix: one shared hash helper used by both sides, built from the charged price (`effectivePrice(product, priceMode)`). Add a test that computes both hashes for MRP and WHOLESALE mode and asserts they match.
  - Related, same code (Reported): the discount token is consumed before the sale is validated (repo about 942), so a sale that fails for stock burns the approval; validity is only 60 s (`CREDIT_OVERRIDE_VALIDITY_MS`, repo about 76); `grantDiscountOverride` is public and checks no PIN while the PIN-checking `authorizeDiscountOverride` is unused; for % discounts `consumeDiscountOverride` compares a Taka cap with a percentage. Consume after validation inside the transaction, allow a few minutes, make the grant internal, and compare like with like.

- [ ] **R21-04 HIGH Verified. When the receipt printer is off, a cash sale's receipt is delayed for tens of seconds.**
  - `ui/screens/CartScreen.kt` about 609-614 awaits `triggerCashDrawerKick(...)` after `completeSale()` and before `openReceiptDialog(...)`. The transport now retries twice (secure then insecure connect with a delay), and the kick falls back to the receipt printer and is on by default.
  - Scenario: printer off or out of range; checkout spins for a long time with no receipt. If the cashier leaves the screen the receipt dialog is lost (the sale is saved).
  - Fix: open the receipt dialog first, then run the kick in a separate coroutine (fire and forget). Consider no retry for the kick.

- [ ] **R21-05 MED Verified. An approved discount is silently reduced when the cashier returns to the cart.** (Regression from R20-06 clamp.)
  - `CartScreen.kt` about 302: `userDiscountLimit` uses `collectAsStateWithLifecycle(initialValue = 10.0)`. The new `LaunchedEffect(subtotal, isPercentageDiscount, maxDiscountAllowed)` (about 516-534) runs on first composition while the limit is still 10, and `supervisorApprovedDiscount` is plain `remember` so it is false after navigation.
  - Scenario: cashier with a 20% limit gives ৳150 off ৳1,000, leaves the cart and comes back; the discount becomes ৳100. A restored held cart with an approved discount is cut the same way.
  - Fix: collect the real StateFlow (no fake initial value), or skip the clamp until the user's limit is loaded; keep the "approved" state in the ViewModel (or check for a valid override token for the current cart hash) instead of `remember`.

- [ ] **R21-06 MED Reported. A crash path remains in stock check.** VM about 1799 `ProductsViewModel.updateProduct` is a bare `viewModelScope.launch`; repo `updateProduct` needs `INVENTORY_ADD_EDIT`. It is called from `ProductsScreen.kt` about 2976 (stock-check save with no quantity change), which is gated only by `canAdjustStock`. A user with Adjust Stock but not Add/Edit crashes the app. Fix: `RepositoryErrorBus.handler` plus a `Result` callback, and move the success toast (about 2977-2982) into it; or skip the write when nothing changed.

---

## P1: Money, data integrity and security

- [ ] **R21-07 HIGH Verified. Staff management lets a manager take over the owner account once delegation works.** (Latent; becomes live when R21-14 is done, so fix together.)
  - repo `updateStaff` about 4267-4330 now accepts `SYSTEM_MANAGE_STAFF`. The PIN-change check (about 4300-4304) accepts `oldPin` if it matches the **signed-in user's** PIN (`matchesSessionOwner`), so a manager can reset the owner's PIN using their own PIN. `updateStaff` also lets anyone with the permission promote to OWNER and write custom permissions without `SYSTEM_PERMISSIONS`. `deleteUser` (about 4336) can delete an owner while more than one exists.
  - Fix in the repository: non-owners may not edit, deactivate or delete OWNER accounts or assign the OWNER role; accept `oldPin` only if it matches the target user (or the session user is OWNER); require `SYSTEM_PERMISSIONS` to change custom permissions; no one may edit their own permissions.

- [ ] **R21-08 HIGH Verified. Voiding a purchase invoice can make the supplier's due negative and leave the cash expense behind.**
  - repo `voidPurchaseInvoice` (about 5128) applies `dueChange = -invoice.dueAmount` with no floor and ignores later payments. Example: unpaid ৳1,000 invoice, later a separate ৳1,000 payment brings due to 0, then voiding the invoice sets due to -৳1,000.
  - repo `recordPurchaseInvoice` (about 4960-4985) stamps the `SupplierPayment` and the cash `Expense` with two separate `System.currentTimeMillis()` calls; the void looks up the expense by exact timestamp (`getSupplierExpenseByTimestamp`, about 5107 and 5171), so it is often missed and the cash outflow stays in reports and register close. (`recordSupplierPayment` uses one shared timestamp and is fine.)
  - The invoice payment is matched with `contains("#$invoiceNumber")`, so "#12" matches "#123". The weighted purchase price is not restored on void.
  - `voidSupplierPayment` (about 5092) adds `amountPaid` back to due even for "Paid on Invoice" payments, which never reduced due in the first place, so voiding one inflates the due.
  - Fix: one timestamp per invoice write, or better store the payment id / invoice id on the expense; match by id not note text; clamp or refuse when later payments exist; block voiding "Paid on Invoice" payments (void the invoice instead). Add an integration test for each scenario.

- [ ] **R21-09 MED Reported. Deleting a customer permanently destroys their store credit and active vouchers.** repo `deleteCustomerPermanently` (about 1585-1602) now deletes all vouchers including ACTIVE ones with balance, and the store-credit ledger; it only checks dues, and so does `CustomersScreen.kt` about 1565. Fix: block when `storeCredit > 0` or an active voucher has balance; keep ledger rows for audit.

- [ ] **R21-10 MED Reported. Store credit plus a voucher still carries over to a different customer.** (R20-16 partial.) VM about 455-461: the reset is skipped whenever a voucher is redeemed. A's ৳500 credit plus a ৳100 voucher (৳600 applied), phone changed to B, checkout debits B ৳500 (`CartScreen.kt` about 576). Fix: on phone change with a voucher, set applied credit to `min(applied, voucher.remainingBalance)`.

- [ ] **R21-11 MED Reported. Excel import still damages data.**
  - (R20-12 partial) Numeric barcode cells still go through `preserveLeadingZeros` (`utils/ExcelHelper.kt` about 85, 104), so a numeric 12-digit UPC-A imports with an extra "0" and creates a duplicate; 10-digit codes starting with "1" are still treated as phone numbers. Fix: in `upsertProducts` look up the raw value first and only use the padded one if raw doesn't exist; no phone rule for the barcode column.
  - (R20-13 partial) A blank stock or purchase-price cell (column present) parses as 0 and overwrites existing data (`ExcelHelper.kt` about 383-390, repo about 3927). Fix: blank means "keep existing" per row. Still no confirmation step ("N new, M updated, K stock changes"). The "(N failed)" suffix and failure messages are English only. New imported products get no initial `InventoryAdjustment`; the "Import" reason is not localized and not in the audit filter (`StockAuditDialogs.kt` about 42, which also lacks "Other").

- [ ] **R21-12 MED Reported. Report and profit figures still disagree in some cases.**
  - (R20-10 partial) Tax-inclusive sale with a discount: `calculateInvoiceProfit` nets revenue as (S - T) - D, `buildReport` as sum of price/(1+r) - D; they differ by D*r/(1+r). Example S=1150, 15% inclusive, D=115: invoice 900, report 885. Fix: one formula. The owner view's "Original profit" (`OwnerProfitAnalyticsComponents.kt` about 123) and "Refund & Discount" card (about 426) leave out the loyalty discount. Line profit is matched by barcode with `associateBy` (about 67), so duplicate-barcode lines show the last line's numbers; zip by index.
  - (R20-19k partial) Breakdown rows clamp revenue at 0 (`(0 - refund).coerceAtLeast(0)`, VM about 2390-2420), so refunds on days or products with no sales vanish and the breakdowns still don't sum to the totals; empty "Unknown (barcode)" rows appear in top products. Fix: don't clamp; fill `DailyReportData.refunds`.
  - Loyalty is valued at the current `pointCashValue`, not the value at sale time (LOW). Store it on the sale.

- [ ] **R21-13 MED Reported. Profit still leaks to users without the P&L permission.** (R20-21 partial.) The Sales Report Share button (`SalesReportScreen.kt` about 256-265) calls `generateStructuredReportText` (about 566-587), which always writes Gross Profit and Net Operating Profit, is not gated by `canExport`, and is all English. `HourlyPanel` (about 1680) shows hourly profit without `canViewPnl`. Closing the register auto-opens the Z-report (`RegisterCloseScreen.kt` about 1196) and the X-report (about 323) for users without `REPORTS_VIEW_Z_REPORT`; both show margin and net profit. Fix: gate the share button on `canExport`, omit profit lines when `!canViewPnl`, set `showPnlZReportDialog = canViewZReport`, gate or strip profit from the X-report.

---

## P2: Permissions still inconsistent

- [ ] **R21-14 MED Verified/Reported. Delegated "Manage staff" is half-wired.** (R20-26 wrong.) `AuthViewModel.addStaff`, `updateStaff`, `updateCashier`, `setStaffActive`, `updateStaffPermissions` still hard-check `isSessionOwner()` (VM about 3790, 3826, 3862, 3878, 3894), while `deleteUser` (about 3927) has no check. A manager given Manage Staff opens the screen and every action fails except Delete. Fix: replace the VM owner checks with the permission checks, **after** R21-07 is in place.

- [ ] **R21-15 MED Verified. Changing a user's role still keeps their old custom permissions.** (R20-28 not fixed.) `ManageUsersScreen.kt` about 663 passes `null` on role change, but repo `updateStaff` (about 4313-4317) treats `null` as "keep existing". VM about 3845 also writes `null` into the in-memory `currentUser`, so editing yourself diverges from the DB. Fix: in `updateStaff`, when the role changes and `customPermissions == null`, clear the stored custom set explicitly (or add an explicit `resetPermissions` flag).

- [ ] **R21-16 MED Reported. Remaining `isOwner || isSupervisor || perm` gates.** (R20-20 partial.) `ui/screens/SalesHistoryScreen.kt` about 149-150 and 1821-1823 (`canReturn`, `canDelete`, `canEdit`), `SuppliersScreen.kt` about 91. Default managers lack `RETURNS_DELETE_SALE` but still see Delete, then the repo rejects it (repo about 560). repo `addDue` (about 2028) uses `isSessionManagerOrOwner()` and ignores `DUES_OVERRIDE_LIMIT`. Fix: drop `isSupervisor`; in the repo use `hasPermission(DUES_OVERRIDE_LIMIT) || consumeCreditLimitOverride(...)`. (The `allowNegativeInventory` uses of `isSupervisor` are a role policy and fine.)

- [ ] **R21-17 MED Reported. Phone scanner cart preview still removes lines without the void check.** (R20-23 partial.) `ScannerScreen.kt` about 552 passes `CartPreviewBottomSheet`'s minus button (about 3048) and `ManualQuantityDialog` (about 2913) straight to `setQuantity`, which removes the line at 0 (VM about 1361). Fix: `if (qty <= 0) handleRemoveItemRequest(prod) else setQuantity(...)`. (The "Undo last scan" at about 450 is arguably fine.)

- [ ] **R21-18 MED Reported. Held carts and Scanner hold buttons.** (R20-02 partial.) Every hold call site shows "Cart held" before the result (`CartScreen.kt` about 723, 728, 3045; `ScannerScreen.kt` about 560, 1253, 1582). The supervisor-approved hold (`CartScreen.kt` about 726-728) can never succeed because the repo checks the cashier's own `SALES_HOLD_CART`. The three Scanner hold buttons have no `canHoldCart` check. On insert failure the rollback restores only items, discount, price mode, name and phone (VM about 822-845); payment method, wallet/card, store credit, voucher and loyalty are lost. After a successful insert, `persistCartSnapshotSync(emptyList())` can overwrite a new cart the cashier already started. Fix: `onResult` callback and toast on success; gate Scanner holds; add a scoped hold override or hide the supervisor path; restore the full state; persist `_cartItems.value` instead of `emptyList()`.

- [ ] **R21-19 LOW Reported. Stock permission edge cases.** Adding a product with initial stock as a user with Add/Edit but not Adjust Stock skips the audit row silently (`ProductsScreen.kt` about 2741-2752; repo about 412-413 needs both). A stale stock value in the edit dialog for a user without Adjust Stock creates a non-zero diff if a sale happened meanwhile, and the edit fails as Unauthorized. Fix: when `!canAdjustStock`, read current DB stock and pass `adjustment = null`.

- [ ] **R21-20 LOW Reported. Staff PINs can be enumerated.** `ui/components/SupervisorAuthDialog.kt` about 55-69 distinguishes "not a supervisor" from "deactivated", so a cashier can find which 4-digit PINs belong to staff; the `Deactivated` path in `verifyPinDetailed` returns before counting a failed attempt. Fix: one generic message, count Deactivated as a failure. Also move the 5 hardcoded English messages to strings (`auth_locked_out_seconds` already exists, unused).

---

## P3: Feedback, state and missing pieces

- [ ] **R21-21 MED Reported. Success shown before the result is known (shared-fix gaps).**
  - Settings: `SettingsScreen.kt` about 4235, 4710, 4746, 4768, 4789, 4826, 4844, 4852, 4919 toast "saved" right after the fire-and-forget save. Add `onResult` to `saveInvoiceSettingsData`, `saveShopInfo`, `saveInvoiceSettings`.
  - The backup page is not gated by `SYSTEM_BACKUP_RESTORE`, so a manager's toggle errors and snaps back. Hide or disable it.
  - `previewDuplicateDueLedgers`, `resetFactoryDefaults`, `fetchDriveBackups` never call back on error: the progress dialog (`SettingsScreen.kt` about 5780, `onDismissRequest = {}`), the reset dialog (about 5756) and `_isLoadingBackups` (VM about 3502) get stuck. Use try/finally or `Result` callbacks.
  - `LoyaltyScreen.kt` about 358-360 `updateProgramSettings` toasts before the result; a failed redeem (`false`) is silent (about 261, 287). Add/redeem/toggle in the adjust dialog give no success message (about 283-297).
  - `RegisterCloseScreen.kt` about 1071-1080 (cash float) and 1091-1101 (safe drop) show success before the result; RegisterClose is gated by `REPORTS_CLOSE_REGISTER` but the repo needs `EXPENSES_MANAGE`. `ExpensesScreen.kt` about 510, 521 close the dialog without checking.
  - `runCatching` in the loyalty VM also catches `CancellationException` and reports it.
  - Other bare launches with low risk today: DuesViewModel about 2702, 2718, 2724, 2895; `CartViewModel.deleteHeldCart` about 1017.
  - The error-bus collector (Nav about 335) is `LaunchedEffect(Unit)` and keeps the first context, so error toasts stay in the old language after a switch. Key it on `localizedContext`. Gate and hub toasts in Nav use the application `context`, not the localized one.

- [ ] **R21-22 MED Reported. Sale completion can still be cancelled in one path.** (R20-33 partial.) The credit-limit "Authorize" path (`CartScreen.kt` about 2979-2981) runs plain `scope.launch { doCompleteSale(...) }` without `NonCancellable`. Wrap it like `executeSaleAction`.

- [ ] **R21-23 MED Reported. Printing gaps.**
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

- [ ] **R21-24 MED Reported. Backup leftovers.** (R20-36/37/39 partial.)
  - Offline still shows the false "Reconnect Google Drive" alert: `AutoBackupWorker.kt` about 105 checks for `IOException`, but `Tasks.await` throws `ExecutionException` wrapping `ApiException(NETWORK_ERROR)`. Check the cause's status code.
  - On that network retry the local backup runs again and writes a duplicate (about 144).
  - The UI still calls the error-swallowing `listBackups` (VM about 3504), not `listBackupsResult`.
  - Factory reset: `cancelAllScheduledReminders` runs after `clearAllTables()` (repo about 5304 vs 5358), so it cancels nothing; product photos `filesDir/product_*.jpg` and `cart_snapshot.json` (repo about 2355) remain.
  - The local auto-backups list shows `TechyfiPOS_AutoBackup_latest.json` next to the newest copy and lists files on the main thread (`SettingsScreen.kt` about 5165).
  - Dead or half-wired settings: `websiteUrl` has an input (`SettingsScreen.kt` about 2156) but is printed nowhere; `decimalQuantitiesEnabled` is still dead; `driveBackupTime` has no UI (Drive schedule fixed at 02:00).
  - Loyalty "EXPIRING_SOON" now selects points that have already expired (`updatedAt < now - expiryDays`, VM about 4670), not ones about to expire.

- [ ] **R21-25 MED Reported. Supplier detail sheet shows stale data.** `SuppliersScreen.kt` about 711 stores a supplier snapshot, so the balance (about 1529) doesn't update after a void or payment; `getInvoicesForSupplier`/`getSupplierPayments` (about 1462-1463) create new Flows every recomposition. Fix: store the id, read the live supplier from the VM, and `remember(supplier.id)` the flows. Void failure toasts show raw English (about 1396, 1433).

- [ ] **R21-26 MED Reported. Khata statement balance doesn't match Outstanding Due.** `shareCustomerStatement` (`DuesScreen.kt` about 1660) omits return due reductions (`Return.dueAdjustmentAmount`) and probably legacy credit sales without a "Sale" charge row. Add them, or seed the opening balance so it reconciles. Also `DuesScreen.kt` date pickers at about 1586, 1601, 1622, 1641 still have the one-day UTC shift fixed elsewhere in R20-19l.

- [ ] **R21-27 MED Reported. Stock alerts no longer fire for out-of-stock or expired items.** (R20-59 regression.) `utils/StockAlertWorker.kt` about 37-41 filters `stockQty > 0.0` and `exp > now`. Keep those items and rely on the new signature de-duplication alone. The worker also uses the system locale; build a localized context from `languageFlow` as `AutoBackupWorker` does.

- [ ] **R21-28 MED Reported. "1,000" is read as 1.** `utils/AppUtils.kt` about 399 `sanitizeNumericInput` turns `,` into a decimal point. Typing "1,000" or "1,500" into cash received, prices or expenses gives 1.0 or 1.5 with no warning. Fix: drop commas (thousands separator), accept only `.` as decimal. Add a unit test.

- [ ] **R21-29 MED Reported. Light mode: status bar icons invisible on Splash and Lock.** The bars now follow the app theme (R20-76), but `SplashScreen.kt` about 95 and `LockScreen.kt` about 270 always draw dark backgrounds, so in light mode the icons are dark on near black. Force light icons while those routes show. Also the window background is now light in light mode, so cold start goes white, then dark splash, then light app.

- [ ] **R21-30 LOW Reported. Smaller leftovers from round 20.**
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
