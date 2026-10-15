# Audit Round 22: Verification of Round 21 + New Findings
Generated: 2026-10-15

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`, `VM` = `ui/viewmodel/POSViewModels.kt`, `Nav` = `Navigation.kt`. Line numbers are approximate (taken at commit `1c9e0e9`); search for the quoted code if they have moved.

## State
- Round 21 landed on `main` (commits `66b0d2b..1c9e0e9`), plus new printer features: receipt cut mode and USB receipt/label printers.
- All 38 R21 items were ticked `[x]`. Verification found:
  - **Fully fixed (16):** R21-01 (idle activity tracking), R21-02, R21-03 (hash parity), R21-04, R21-06 (crash), R21-09, R21-10, R21-15, R21-16, R21-17, R21-19, R21-22, R21-26, R21-27, R21-28, R20-29 (single owner).
  - **Partial (ticked but sub-points left):** R21-05, R21-07, R21-08, R21-11, R21-13, R21-14, R21-18, R21-20, R21-21, R21-25, R21-30, R21-31, R21-32, R21-T1, and the R21-01 owner decision (background lock).
  - **Wrong direction:** R21-12 now makes both profit views agree on the **wrong** figure for tax-inclusive sales with a discount (R22-32).
  - **Ticked but essentially not done:** **R21-23** (the commit that ticked it did not touch `BluetoothPrinterTransport.kt`, `TsplLabelPrinterDriver.kt`, `BillActions.kt` routing or `ProductCodePrintDialogs.kt`; verified with `git show --stat d8c1fc2`) and most of **R21-24**.
  - **Wrong or no-op fixes:** R21-29 (status bar fix never runs), R21-24 item 1 (offline Drive check tests the wrong exception type), R21-24 item 2 (stops retrying failed local backups), R20-30 alert (wiped on every login).
- Clean, all verified: no compile errors found by reading (still nothing built here), no em dashes added, `values` and `values-bn` both 2995 keys with matching placeholders, every new `%d` gets an Int, no Room schema change.

## Rules for this round (important)
- **Only tick an item when the code actually changed for every sub-point.** Several R21 items were ticked with no code change in the files they name. Before ticking, run `git show --stat <sha>` and confirm each file named in the item was touched.
- If you decide not to do a sub-point, leave it unticked and write "deferred: <reason>" under it.
- Tests must call production code. A test that re-implements the logic with local variables does not count. Delete helpers that no production code calls.
- Build and run `./gradlew :app:testDebugUnitTest` before you start and after each priority group.
- New strings go in both `res/values/strings.xml` and `res/values-bn/strings.xml`.
- **Verified** = re-checked in the code by the round-22 auditor. **Reported** = found by a sub-audit; re-check before changing, and tick with "not a bug: <reason>" if it isn't real.

---

## P0: Security holes

- [x] **R22-01 HIGH Verified. A cashier can brute-force supervisor and owner PINs without ever being locked out.**
  - repo `verifySupervisorPinDetailed` (about 342-352) calls `verifyPinDetailed`. When the PIN typed is the cashier's own, `verifyPinDetailed` returns Success and clears the whole `auth_security` prefs (about 4330), resetting `failed_attempts`, `total_failures` and any lock. Only then is NotSupervisor counted as 1 failure.
  - Scenario: 4 wrong guesses, then the cashier's own PIN, repeat. The counter never reaches 5.
  - Fix: give `verifyPinDetailed` a `clearOnSuccess: Boolean` parameter (false from the supervisor path), and clear counters only on a real login or a real supervisor success. Use the lock time returned by `recordFailedPinAttempt()` on the NotSupervisor path too, so the "locked" message shows.
  - Test: a repository test (Robolectric or fake prefs) that does 4 wrong + own PIN, twice, and asserts the 5th failure locks.

- [x] **R22-02 HIGH Verified. The owner PIN-reset alert (R20-30 decision) can never appear.**
  - repo `resetOwnerPinAfterDeviceCredential` (about 4262-4276) writes `owner_device_pin_reset_timestamp` / `_acknowledged` into `auth_security`, and the next successful login wipes that file (about 4330, `.clear()`). The same happens on any supervisor approval. Nav about 487 then reads nothing.
  - Even when fixed, the person who reset the PIN is usually the one who logs in next and can acknowledge it.
  - Fix: store the reset record in a separate prefs file (or a Room audit table) that login never clears; show the alert until the owner acknowledges it **and** changes the PIN, and keep a permanent audit entry (time, device) visible in Settings.
  - Test: reset, then log in as a cashier, then as owner; assert the alert is still pending.

- [x] **R22-03 MED Reported. A "Manage staff" delegate can create or promote Manager accounts.** (R21-07 gap.)
  - `ui/screens/ManageUsersScreen.kt` about 473 hides only OWNER, so MANAGER is selectable. `AuthViewModel.addStaff` (VM about 3936), repo `createUser` (about 4219-4237) and `updateStaff` (about 4407-4419) only require `SYSTEM_MANAGE_STAFF`. A cashier given that permission creates a MANAGER with a PIN they know and logs in with full manager defaults (P&L, cash refunds, dues override, 100% discount).
  - `createUser` accepts `customPermissions` with no `SYSTEM_PERMISSIONS` check.
  - Fix in the repository: a non-owner may only create or assign roles at or below their own, and only grant permissions they hold; require `SYSTEM_PERMISSIONS` for any `customPermissions` in `createUser`.

- [x] **R22-04 MED Verified. A supervisor-approved discount stays approved after the cart changes.** (R21-05 partial and new hole.)
  - VM about 621-636: `_supervisorApprovedDiscount` is a plain Boolean, reset only on discount-field edit, unit toggle and `clearCart`. Not reset on add/remove/quantity change, price-mode change, `holdCurrentCart` (about 866-887) or `restoreHeldCart` (about 1015-1016). `CartScreen.kt` about 473-475 and 524-536 skip the limit clamp while it is true.
  - Scenario A: 10% limit cashier gets ৳150 approved on ৳1,000, removes items down to ৳160; the clamp is skipped. For users who have `SALES_APPLY_DISCOUNT`, the repo never checks the per-user `maxDiscountPercent` (repo about 982-986, 1207), so the sale completes at about 94% off.
  - Scenario B: user without discount permission gets approval, scans another item; the hash no longer matches, the UI still shows "approved", and Complete Sale fails with raw English "Supervisor authorization is required to apply discount" with no re-prompt.
  - Fix: store `approvedForHash` and `approvedMaxTaka` in the VM; treat the discount as approved only while `computeCartHash()` matches and `discountValue <= approvedMaxTaka`; save them with held carts. In the repo, enforce `discountTaka / subtotal * 100 <= sessionUser.maxDiscountPercent` unless a valid token is consumed. On mismatch, show the supervisor prompt again; localise the error.
  - Same area (Reported): `CartViewModel.grantDiscountOverride` (VM about 632) is public and checks no PIN, and the PIN-checking `authorizeDiscountOverride` is still never called (making the repo method `internal` changes nothing inside one module). Make `SupervisorAuthDialog` return the verified PIN and call `authorizeDiscountOverride(pin, maxTaka, hash)`; remove the public grant. The discount token is consumed before later failure points (repo about 1207 vs 1245, 1282, 1318), and the credit-limit token is consumed before the stock check (about 1184 vs 1190) and cleared on mismatch (about 362-376); consume both as the last step, or restore them in a catch. `hasValidDiscountOverride` runs on every recomposition (`CartScreen.kt` about 474) and receives the raw percent in % mode.

- [x] **R22-05 MED Reported. Held cart supervisor approval still fails on the main Hold button.** (R21-18 partial.) `CartScreen.kt` about 1113-1118: the supervisor action only opens the hold dialog and never calls `grantHoldCartOverride()`, so the confirm (about 3061) is rejected for a cashier without `SALES_HOLD_CART`. `ScannerScreen.kt` about 169 reintroduces `isOwner || isSupervisor || ...` for `canHoldCart`. repo `grantHoldCartOverride` (about 381) is a public grant with no PIN check. `_manualCashierName` is not restored on rollback (VM about 876). Fix: verify the PIN in the repo like the credit override and grant there; drop `isSupervisor`; restore the cashier name.

- [x] **R22-06 LOW-MED Reported. `insertExpense` accepts `REPORTS_CLOSE_REGISTER` for any expense.** A close-register-only user can create arbitrary expenses or cash-ins through the repo. Accept that permission only for categories "Cash In" and "Safe Drop".

- [x] **R22-29 HIGH Verified. The owner PIN can be guessed through the "PIN already in use" error.** repo `createUser` (about 4240-4241) and `updateStaff` (about 4431-4432) compare a candidate PIN against every user, including the owner, with no throttling. A `SYSTEM_MANAGE_STAFF` holder tries PINs on a throwaway account; each "PIN already in use" reveals a live PIN (at most 10,000 tries), then logs in as that user. Fix: on a conflict call `recordFailedPinAttempt()` and respect `loginLockRemainingMillis()` (same lockout as login), and return a generic error.

- [x] **R22-30 HIGH Reported. Users can raise their own role.** repo `updateStaff` has no `sessionUser.id == userId` check for role (about 4406) or `isActive`; since R21-15 a role change wipes custom permissions (about 4450). `ManageUsersScreen.kt` about 134 (`canEditUser = isOwner || !isTargetOwner`) includes yourself, and about 697 passes `null` on role change so the VM gate (about 3965) is skipped. A cashier with Manage Staff edits themselves to MANAGER and gets full manager defaults; doing MANAGER to CASHIER to MANAGER on someone else wipes the owner's restrictions. Fix: forbid changing your own role, `isActive` and `maxDiscount`; require `SYSTEM_PERMISSIONS` (or owner) for any role change; non-owners may assign only roles at or below their own. Together with R22-03 (create) and these: a `SYSTEM_PERMISSIONS` holder can grant `SYSTEM_PERMISSIONS`/`SYSTEM_MANAGE_STAFF` to a second account and have it grant them back; granted sets must be a subset of the granter's permissions, and only the owner may grant `SYSTEM_*` keys. `deleteUser` (about 4369-4382) trusts the role and id on the passed `User`; reload by id. `updateStaff(isActive = false)` on yourself still works.

- [x] **R22-31 MED Reported. Delegated managers can't save edits for users with custom permissions, or for themselves.** (R21-14 regression.) `EditStaffDialog` (`ManageUsersScreen.kt` about 697) re-sends the target's existing `customPermissions` when the role is unchanged, so a manager without `SYSTEM_PERMISSIONS` is refused on any edit (name, PIN, discount), and editing yourself always hits "Users cannot edit their own permissions" (repo about 4444). Fix: send `null` when permissions are unchanged (they are edited only in the matrix dialog) and treat an unchanged set as no change in the repo.

- [ ] **R22-07 LOW-MED Reported. Anyone with Shop Settings can turn off the idle lock.** `SettingsScreen.kt` about 516-582 saves through `saveInvoiceSettingsData` (needs only `SYSTEM_SHOP_SETTINGS`). Gate the setting on `isOwner` (read-only for others), matching Reset and Repair Dues on the same page.
  deferred: awaiting owner decision (see Owner questions)

---

## P1: Printing (new USB feature and R21-23 re-opened)

- [x] **R22-08 HIGH Verified. Batch label printing ignores the USB label printer.** `ui/dialogs/ProductCodePrintDialogs.kt` about 1886-1890 and 1950-1955 read only `invoiceSettings.labelPrinterAddress` (Bluetooth) and call the String overload of `printLabelPayloadsBatch`. With a USB label printer, jobs silently go to the PDF dialog, or to an old stored Bluetooth MAC. Fix: use `resolvedLabelPrinterTarget()`, `isSamePrinterTarget(labelTarget, resolvedReceiptPrinterTarget())` and the target overload; also route through `decidePrintRouting` (BLUETOOTH/USB mode with no printer shows the error; AUTO with 0 sent falls back to PDF).

- [x] **R22-09 HIGH Verified. A stuck USB permission request can block all printing forever.** `printer/UsbPrinterTransport.kt` about 179-228: `suspendCancellableCoroutine` has no timeout, runs inside `printerHardwareMutex.withLock` (about 241, 254), and only resumes on a matching `deviceId` (about 192). Unplug and replug during the dialog, or a lost broadcast, holds the mutex shared by Bluetooth and USB. Fix: `withTimeoutOrNull(60_000)` treated as denied; resume (denied) on any broadcast for this action. Better: request USB permission when the device is picked in Settings, not mid-checkout.

- [x] **R22-10 MED Verified. Bluetooth printing still has no timeout.** (R21-23, not done.) `printer/BluetoothPrinterTransport.kt` about 147 holds the app-wide mutex across blocking `connect()`/`write()` with no `withTimeout` and no watchdog; the outer `catch (e: Exception)` (about 252) still swallows `CancellationException`. The mutex is now shared with USB, so a stalled BT write blocks USB printing too. Fix: watchdog that closes the socket on timeout; rethrow cancellation. Same for USB: `UsbPrinterTransport.kt` about 297 catches `CancellationException`.

- [x] **R22-11 MED Reported. USB printer identity.** On Android 10+ `getSerialNumber()` throws until permission is granted, and the picker builds the key before any permission request (`printer/PrinterTarget.kt` about 401-403), so keys are VID:PID only. Two printers with the same bridge chip (common, e.g. 0416:5011) count as the same and can't be receipt + label; at print time `findUsbDeviceByKey` (`UsbPrinterTransport.kt` about 53-56) falls back to the first VID:PID match even when a stored serial differs, so TSPL can go to the ESC/POS printer. Fix: request permission on pick and store the serial; no VID:PID fallback when the stored serial differs from a readable one.

- [x] **R22-12 MED Reported. Stale Bluetooth addresses after switching to USB.** `SettingsScreen.kt` about 1483-1486 and 1515-1518 keep the old `directBluetoothPrinterAddress` / `labelPrinterAddress` when USB is chosen; combined with the legacy `|| isSamePrinter(settings.labelPrinterAddress, ...)` check (`utils/BillActions.kt` about 1105), every label print can be blocked as "same as receipt". Fix: clear the BT address when USB is picked and drop the legacy clause.

- [x] **R22-13 MED Reported. Cut command style can't be chosen.** `receiptCutCommandStyle` (LEGACY_ESC, GS_V_FEED) has no selector (`SettingsScreen.kt` about 909) and the work-order hint "If the paper does not cut, try another cut command style" is missing. Note Epson treats both ESC i and ESC m as partial cuts (`printer/EscPosPrinterDriver.kt` about 135, 140); say so in the hint. Test cut button: set the busy flag before `launch`, reset in `finally` (about 899-931).

- [x] **R22-14 MED Reported. R21-23 sub-points still open.**
  - Busy guard (double tap prints twice): Z/X Print (`RegisterCloseScreen.kt` about 1223-1247, 1294-1318), due receipt Print (`DuesScreen.kt` about 6833-6850), Settings `onTestPrint` in 5 places (about 5215-5239 and others; also no try/catch, so a generation error crashes, and no spinner or result toast).
  - Batch print: no Cancel, Back allowed while printing, progress stuck at "N of N", all payloads held in memory.
  - Receipt picker "None" still falls back to the drawer printer (`data/InvoiceSettings.kt` about 124-145); the drawer picker does not disable the label printer; the drawer picker never shows "Same as receipt" as selected (about 1444).
  - `NoPermission` still calls `ActivityCompat.requestPermissions` from a util with no result handling (`BillActions.kt` about 813-824).
  - SYSTEM mode still toasts "Sent to printer" when the dialog merely opens (`BillActions.kt` about 947-950).
  - TSPL threshold still `110 + d*8` (230 at 15) while DENSITY also rises (`printer/TsplLabelPrinterDriver.kt` about 21).
  - ESC/POS bitmap in `printReceiptImage` never recycled (`BillActions.kt` about 970-986); `printBillJpeg` recycles in `onFinish` while the worker may draw (about 136-190); single-label print builds the PDF first; `formatPrinterTargetDisplay` / `getBluetoothDeviceDisplayName` run IPC every recomposition (`SettingsScreen.kt` about 843, 1198, 1391, 1395); Z/X Save renders on the main thread (`RegisterCloseScreen.kt` about 1250, 1321).
  - JPEG generation failure still returns false with no toast (`BillActions.kt` about 1187-1195).
  - USB: unplug mid-receipt then AUTO opens the system dialog and prints twice; picker lists flash drives and serial adapters as "likely printers" (`UsbPrinterTransport.kt` about 112-121, exclude classes 3, 8, 9); USB failure messages are English (`UsbPrinterTransport.kt` about 267, 272, 285, 299; add result cases and strings); USB device list is read in composition and doesn't update on plug/unplug (use a `USB_DEVICE_ATTACHED/DETACHED` receiver in a `DisposableEffect`); picker shows raw decimal keys, Bluetooth wording on the USB tab, and the Bluetooth icon for USB cards; dead `cashDrawerPrinterAddress` / `onCashDrawerPrinterSelected` params (`SettingsScreen.kt` about 335-336, 5152-5157).

- [x] **R22-15 MED Verified. Receipts and reports are cut off when text wraps.** The new wrapping of shop name, variance reason and X footer (`utils/BillPdfGenerator.kt` about 629-637, 1201-1205, 1324-1331, 1484-1488, 1586-1591) is not counted in the height pass (about 461, 1127, 1159-1161, 1433, 1468); the receipt has only a 10px bottom margin (about 592). A two-line shop name cuts off the footer and QR. Fix: call `wrapItemNameByPixelWidth` with the same paint and width in the height pass and add `lines * lineHeight`. Also `BillPdfGenerator.kt` about 1223 prints "Units Sold: 15.0"; use `formatUnitsSold`.

---

## P1b: Money and profit

- [x] **R22-32 MED Verified. Invoice profit is now wrong for tax-inclusive sales with a discount.** (R21-12 regression: both views agree, on the wrong number.) Example: one taxable line 1150, 15% inclusive, discount 115: tax T = 135, grand total 1035, correct net revenue 1035 - 135 = **900**. The old `calculateInvoiceProfit` gave 900; the new one (VM about 2162-2167: ex-tax line revenue 1000 minus the full discount 115) gives **885**, and `buildReport` (about 2420-2427, 2563) also gives 885. With cost 600 both show 285 profit instead of 300. Fix: for inclusive sales subtract the discount excluding its tax share, `D_ex = D - (sum of taxable price*r/(1+r) - T)` (here 115 - 15 = 100), in both places; equivalently net revenue = grandTotal + loyalty - T. Add a test with exactly these numbers calling `calculateInvoiceProfitSummary` and `buildReport`.

- [x] **R22-33 MED Verified/Reported. Profit still leaks through Excel exports.** (R21-13 partial.) `SettingsViewModel.exportSalesReport` (VM about 3566) has **no permission check** and writes a per-sale Profit column (`utils/ExcelHelper.kt` about 473, 563); it is reachable from Settings Import/Export (`SettingsScreen.kt` about 5177, 5475). `ExcelHelper.exportSalesReport` (about 1419, 1421, 1486, 1500) always writes Gross Profit, Net Profit and daily profit, gated only by `REPORTS_EXPORT_EXCEL`. Fix: require `REPORTS_EXPORT_EXCEL` for the Settings export and drop profit columns unless the user has `REPORTS_VIEW_PNL`.

- [x] **R22-34 MED Reported. Supplier due is wrong when a payment is voided after its invoice.** (R21-08 partial.) Unpaid ৳1,000 invoice, separate ৳1,000 payment (due 0), void the invoice (due stays 0, correct), then void the payment: `voidSupplierPayment` (repo about 5256) adds 1,000 back, so due is 1,000 with no purchases (correct: 0). Reverse order works. Fix: after any void, recompute supplier due from the ledger (sum of live invoice dues minus live standalone payments, floored at 0), or refuse to void an invoice that has later standalone payments. Still open: the expense is matched by timestamp only (`AppDaos.kt` about 427), so invoices recorded before the fix still leave the expense; a manual payment typed as "Paid on Invoice #12" can't be voided; weighted purchase price is not restored. Long term add `supplierPaymentId`/`purchaseInvoiceId` columns (Room migration to 39).

- [x] **R22-35 LOW-MED Reported. Excel import leftovers.** (R21-11 partial.) Barcodes starting with "0" arrive from numeric cells as 11 digits, so no fallback runs and a duplicate is created; for any all-digit value not found, also try `"0" + raw` and `"00" + raw`, and use it only if exactly one product matches. Blank category, reorder level, wholesale, MRP and discounted price cells still overwrite existing values with ""/0 (`optionalDouble`, `ExcelHelper.kt` about 395); treat blank as keep. "(N failed)" and row failure texts still English (`ProductsScreen.kt` about 354). No confirmation step yet. Customer-delete blocks for credit/vouchers show English (map them); the UI pre-check (`CustomersScreen.kt` about 1568) covers credit only. `selectCustomerSuggestion` (VM about 527-538) doesn't apply the voucher clamp from R21-10.

---

## P2: Backup, session and data

- [x] **R22-16 MED Verified. Offline still shows the false "Reconnect Google Drive" alert.** (R21-24 item 1, wrong fix.) `utils/AutoBackupWorker.kt` about 105-108 checks `e is ApiException`, but `Tasks.await` throws `ExecutionException` wrapping it. Fix: `val root = (e as? java.util.concurrent.ExecutionException)?.cause ?: e`, then test `(root as? ApiException)?.statusCode in setOf(NETWORK_ERROR, TIMEOUT)` or `root is IOException`.

- [x] **R22-17 MED Reported. A failed local auto-backup is never retried.** (R21-24 item 2 regression.) `AutoBackupWorker.kt` about 43 `localDue = ... && runAttemptCount == 0`. If the local export fails and returns `retry()`, the next attempt skips local and, with Drive off, returns `success()`; the backup is lost silently. Fix: skip local only if `lastLocalBackupTimestamp` is newer than this work chain's first attempt.

- [x] **R22-18 MED Reported. Factory reset leftovers.** (R21-24 item 4, not fixed.) `cancelAllScheduledReminders` still runs after `clearAllTables()` (repo about 5464 vs 5516) and cancels nothing; `filesDir/product_*.jpg` and `cart_snapshot.json` remain. `SettingsViewModel.resetFactoryDefaults` (VM about 3393-3400) calls `onComplete()` in `finally`, so a failed reset still shows "Factory reset done" and logs out. Fix: read reminders before the wipe, delete those files, `onComplete(success: Boolean)`.

- [ ] **R22-19 MED Reported. Background logout ignores the idle-lock setting.** (deferred: awaiting owner decision on whether background lock follows idle-lock setting or stays separate fixed rule.) Nav about 378-386 still logs out after a fixed 5 minutes in the background, even when idle lock is Off or 60 min. Use `idleLockDurationMinutes` (0 = never) there too. Activity inside dialogs and bottom sheets doesn't reach `Activity.onUserInteraction` (separate windows), so a long form in a dialog can be locked mid-entry: add a shared modifier that records activity on the dialog/sheet roots. A restored value not in the option list (e.g. 10) selects no button.

- [x] **R22-20 MED Verified. The Splash/Lock status-bar fix never runs.** (R21-29 no-op.) `theme/Theme.kt` about 88-115 `ForceDarkSystemBars` finds the Activity by walking `LocalContext`, but inside `LocaleWrapper` the context comes from `createConfigurationContext(config)` (Nav about 260-262), which never leads to the Activity, so `activity` is always null. Fix: use `LocalActivity.current`, or better, let `MainActivity` own the bar style and derive it from the current route (also avoids the Splash `onDispose` restoring light bars over Lock during the crossfade).

- [x] **R22-21 LOW-MED Reported. R21-24 leftovers.** Local auto-backups list still shows `_latest.json` and calls `listFiles` on the main thread (`SettingsScreen.kt` about 5671-5675). `websiteUrl` is printed nowhere; `decimalQuantitiesEnabled` is dead; `driveBackupTime` has no UI. Loyalty EXPIRING_SOON still selects already-expired points (VM about 4849, 4856).

- [ ] **R22-22 LOW Reported. Existing installs with two owners get stuck** (deferred: awaiting owner decision on whether to let owner demote extra owners or demote automatically.) (R20-29 decision). `updateStaff` forbids any owner role change (repo about 4415) and the device PIN reset picks `firstOrNull` owner (about 4266). Add a one-time cleanup (let the owner demote extra owners) or an owner picker for the reset.

---

## P3: Feedback, errors and leftovers (re-opened from R21-21, R21-25, R21-30)

- [x] **R22-23 MED Reported. "Saved" toasts still appear before the save result.** `SettingsScreen.kt` about 4731-4732 (logo), 5212-5213 (due receipt), 5246-5249 (shop identity, three saves), 5295-5296 (QR), 5332-5333 (layout), 5350-5351 (reset form), 5358-5359 (tax): use the new `onResult` like about 5271 does. `previewDuplicateDueLedgers` (VM about 3403-3410) swallows errors and reports "no duplicates". `LoyaltyScreen.kt` about 358-360 toasts before `updateProgramSettings` returns; failed redeem is silent; add/redeem/toggle give no success message; loyalty VM `runCatching` (about 4784, 4910, 4925) and Settings save `catch (e: Throwable)` (about 3342-3379) swallow `CancellationException`. `RegisterCloseScreen.kt` about 1071-1103 (cash float, safe drop) and `ExpensesScreen.kt` about 510-523 still don't wait for the result. DuesViewModel bare launches and `CartViewModel.deleteHeldCart` (VM about 1091-1095) unchanged. Nav toasts at about 1253, 1291, 1313, 1324, 1368 use the application context.

- [x] **R22-24 MED Verified/Reported. Error messages lost their translation.** `utils/AppUtils.kt` about 475 matches "Cannot void payment created automatically with purchase invoice", but the repo throws "Cannot void invoice payments directly. Void the purchase invoice instead." (repo about 5250); the "Void failed" rule (about 477) matches nothing. The same commit **removed** the "constraint conflict" / "duplicate record" rule, so those errors (repo about 1245, 1282, 2140, 3061, 4256) now show raw English and `error_duplicate_record` is unused. Unmapped: "Invoice not found", "Supplier payment not found", "Cannot promote staff to owner" (repo about 4412; `users_cannot_promote_to_owner` exists but is unused), "The only owner account cannot be changed" (about 4416), "Supervisor authorization is required to apply discount". Stock-check failure shows the raw permission message (`ProductsScreen.kt` about 2974). Add a unit test per mapping that uses the exact repo message.

- [x] **R22-25 MED Reported. Stock-only users still can't save a count.** (R21-06 remainder.) `updateProductWithInventoryAdjustment` requires `INVENTORY_ADD_EDIT` (repo about 453). Add a stock-only path that needs `INVENTORY_ADJUST_STOCK` alone. Also enforce R21-19 in the repo: when `adjustment == null` and the user lacks Adjust Stock, keep the DB `stockQty` inside the transaction (`replaceProductBarcode`, repo about 468-498).

- [x] **R22-26 LOW Reported. R21-30 leftovers still open.**
  - Z-report counted cash and variance only in the JPEG, not the PDF, CSV, dialog or reprint (`BillPdfGenerator.kt` about 1316).
  - `report` null on Confirm still falls back to the new shift (`RegisterCloseScreen.kt` about 1171).
  - Reprinted repayment slips still show "PAID IN FULL" from the current balance (`DuesScreen.kt` about 6303, 6325, 6491; `BillPdfGenerator.kt` about 1947-1951).
  - Reminders: kept one-shot rows never rescheduled; Send now still sends when the due is settled; channel "both" opens WhatsApp only.
  - `calculateInvoiceProfit` still runs `associateBy` per call inside sort comparators (`SalesHistoryScreen.kt` about 253, 256) and on recomposition (`CartScreen.kt` about 2773).
  - Bulk price change and Excel import still allow selling price above MRP (`PaymentMath.computeBulkUpdatedPrices`, `ExcelHelper.kt` about 405-421).
  - Manual "Supplier Payment" expenses still locked by category alone (`AdvancedPosFeatures.kt` about 1364, `ExpensesScreen.kt` about 488).
  - `CHARGE_REVERSAL_OVERPAYMENT` shows as "unknown" (`CustomerDossierSheet.kt` about 861-869).
  - 7 `repository.context` uses in VMs for user-facing text.
  - Bangla stock-reason rows not migrated.
  - Loyalty re-clamp combine still misses `taxValue` (VM about 1228).
  - Counted cash scientific notation (`RegisterCloseScreen.kt` about 2416).
  - Camera Grant after "Don't ask again" (`ScannerScreen.kt` about 428).
  - `batch_print_all/in_stock/custom` unused.
  - Duplicate-category check only on `nameEn` (`ManageCategoriesDialog.kt` about 74, 76).
  - Cart undo overwrites a line re-scanned during the snackbar instead of merging.
  - Expense "Yesterday" label: hardcoded strings and integer day division (breaks on 23h DST days).
  - Khata statement lumps all return reductions into one row before the first transaction; add real rows from `Return.dueAdjustmentAmount` with their timestamps.
  - Unused `isOwner` parameters in `AddStaffDialog`/`EditStaffDialog`; `ExcelHelper.preserveLeadingZeros` is test-only and its `isBarcode` param unused; duplicate `extendedColors` import in `ReturnDialog.kt` about 28, 52.
  - Wrong toast for users without backup permission (`owner_only_settings`, `SettingsScreen.kt` about 4636, 5102).

---

## P4: Localisation and design (re-opened R21-31, R21-32)

- [ ] **R22-27 LOW Reported. English still left.**
  - AuthViewModel inline ternaries (VM about 4132-4155), Bangla lockout shows Latin digits.
  - `RegisterCloseScreen.kt` about 1205 shows `it.localizedMessage` before the localized fallback.
  - `SettingsScreen.kt` about 5405, 5752, 5759, 5777, 6449, 6486, 6490, 6509, 6585, 6631.
  - `DuesScreen.kt`: 37 `if (isAppBangla) "..." else "..."` pairs unchanged (50 ternaries total); about 4841 "Credit limit exceeded"; about 1722 "% used"; statement opening-balance row (about 1697-1710).
  - `StoreCreditVoucherSlipDialog.kt` about 648; `StockAuditDialogs.kt` about 226; `ExpensesScreen.kt` about 547.
  - Phone-update errors still replaced by "invalid number" in Bangla (VM about 3116, 5235).
  - `SalesReportScreen` share subject, chooser title and body.
  - `ScannerScreen.kt` about 2294 (Latin digits), 2861 "item/items" (use plurals), 4133 "Cart Total".
  - `BillActions.kt` about 308. Many print job names and clip labels are now inline ternaries: move them to strings.

- [ ] **R22-28 LOW Reported. Design tokens.** Counts in `ui/`: `Color(0x...)` 2302 to 2212; `Color.White/Black/Gray/...` 540 to 543 (not reduced); SettingsScreen `Color.Gray` 27 to 32 (new USB UI added more); files using `extendedColors` 3 to 13. Still unreadable in dark mode: `CartScreen.kt` about 2548, 2555, 2556, 2574 (dark green text on the `successContainer` card), `DuesScreen.kt` about 3189-3194. `ReturnDialog.kt` about 1145 uses `extendedColors.warning` as text on a light surface (low contrast); use `onWarningContainer`. New code: idle-lock card uses raw 14sp and 10sp (`SettingsScreen.kt` about 541-543, 572); hourly profit text hardcodes `0xFF059669` (`SalesReportScreen.kt` about 1688-1691).

---

## R22-T1 Tests
- [ ] Delete or wire up dead helpers in `utils/PaymentMath.kt`: `computeRemainingAfterStoreCredit`, `resetDiscountOnUnitToggle`, `resolveStoreCreditOnPhoneChange` (contradicts the production clamp at VM about 460-466), `computeEffectiveStoreCredit`, `computeVoucherDeduction`, `computePostSaleVoucherBalance`, `canSubmitSupplierPayment`. Their tests (`testR20_06`, `testR20_14`, `testR20_16`, `testR20_17`, `R20-19f`) currently test dead code.
- [ ] Rewrite tests that re-implement logic locally: `testR21_01_idleActivityTracking` (asserts 0 == 0 because `SystemClock` returns 0), `testR21_02` (hard-codes the format strings), `testR21_03` (extract `CartViewModel.buildSaleTriples(items, mode)`, use it in `completeSale`, assert hash parity per mode), `OwnerDecisionsTest` (R21-01, R20-29, R20-30), `P1AuditFixesTest` about 45-58, the new P3 tests (`testR21_26`, `testR21_27`, `testR21_30_*`). The "Dec 31" constant 1735689600000 is 1 Jan 2025 UTC.
- [ ] Add tests: R22-01 lockout, R22-02 alert survives login, R22-04 approval invalid after cart change, R22-08 label target routing, R22-09 permission timeout, R22-15 wrapped height, R22-16 offline exception unwrapping, R22-24 each error mapping with the exact repo message.
- [ ] Correct the R21 test citations that point at tests which don't exist (R21-25, 29, 31, 32 "verified in P3AuditFixesTest.kt"; R21-05 cites `testR21_03`; R21-06 cites `testR21_01`).

## Owner questions (answer before the agent implements)
- **R22-07:** Should only the owner be able to change the idle lock, or anyone with Shop Settings?
- **R22-19:** Should the background lock follow the idle-lock setting (one setting), or stay a separate fixed rule?
- **R22-22:** If a backup or old install has two owners, should the owner be able to demote one, or should the app keep the first and demote the rest automatically?
