# Audit Round 23: Verification of Round 22 + New Findings
Generated: 2026-10-19

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`, `VM` = `ui/viewmodel/POSViewModels.kt`, `Nav` = `Navigation.kt`. Line numbers are approximate (taken at commit `9b9292d`); search for the quoted code if they have moved.

## State
- Round 22 landed on `main` (commits `1c9e0e9..9b9292d`), plus more printing work (cut reliability, print start delay, Bluetooth pacing, status query, PrintTiming).
- All 39 R22 items were ticked `[x]`, none marked deferred. Verification found:
  - **Fully fixed (15):** R22-05, R22-06, R22-07, R22-12, R22-13, R22-15, R22-16, R22-17, R22-18, R22-20, R22-21, R22-22, R22-25, R22-33, and the core of R22-01 (supervisor dialog only).
  - **Partial:** R22-02, R22-03, R22-04, R22-08, R22-09, R22-10, R22-11, R22-14, R22-19, R22-23, R22-24, R22-26, R22-27, R22-28, R22-29, R22-30, R22-31, R22-32, R22-34, R22-35, R22-T1.
  - **New regressions introduced this round** (all verified): dead "Preview Z" button, Scanner supervisor hold does nothing, Excel import now rewrites text barcodes, batch label Cancel/Share buttons moved out of their row, Bluetooth watchdog kills large label batches, receipt bitmap cache prints stale receipts, Manager discounts rejected at checkout, owner can't edit their own account, inclusive-tax profit ignores loyalty, 8 existing tests deleted.
- **Multi-owner support is wanted by the owner** (confirmed 2026-10-19). Keep it. The items below make it safe; do not revert to single owner.
- Clean, all verified: no compile errors found by reading (still nothing built here), no em dashes added, `values` and `values-bn` both 3036 strings / 12 plurals / 2 arrays with matching keys, no `%d` receives a Double, no Room schema change (DB version 38).

## Rules for this round (read first)
- **Only tick an item when every sub-point is done in code.** Run `git show --stat <sha>` and confirm each file the item names was changed. Write "deferred: <reason>" for anything you skip and leave the item unticked.
- **Never delete an existing test to make the suite pass.** Commit `aaabf13` deleted `testR21_28_sanitizeNumericInputCommas`, `testR20_19b_bulkPriceUpdatesDiscountedPrice`, `testR21_26_datePickerUtcMidnightConversionRoundTrip`, `testR21_26_customerStatementRunningBalanceReconciliation`, `testR21_27_stockAlertOutOfStockAndExpiredIncluded`, `testR21_30_cartItemRestoreIndex`, `testR21_30_expenseDateLabelDiffAcrossYearBoundary`, `testR20_29_singleOwnerPromotionPolicy` (the last is fine to drop since multi-owner replaced it). Restore the others, rewritten to call production code (R23-T1).
- Tests must call production code. A test that checks a library function (`withTimeoutOrNull`) or local arithmetic does not count.
- Fix the root cause in `DataRepository` for security and money items; UI checks alone are not enough.
- Build and run `./gradlew :app:testDebugUnitTest` before you start and after each priority group.
- New strings go in both `res/values/strings.xml` and `res/values-bn/strings.xml`. Prefer string resources over new inline `if (isAppBangla) "..." else "..."` pairs (inline pairs went from 212 to 229 this round).
- **Verified** = re-checked in the code by the round-23 auditor. **Reported** = found by a sub-audit; re-check first, tick with "not a bug: <reason>" if it isn't real.

---

## P0: Security

- [x] **R23-01 HIGH Verified. PINs can still be brute-forced at the lock screen.** (R22-01 only fixed the supervisor dialog.) `attemptLogin` uses `verifyPinDetailed(clearOnSuccess = true)`, and a successful login clears all of `auth_security` (repo about 4515-4517), including `total_failures`. Any staff member: 4 guesses at the owner's PIN, log in with their own PIN, repeat; never locked. A Manager can do the same in the supervisor dialog, because a successful supervisor check also clears everything (repo about 356). Fix: never reset `total_failures` on success; let `failed_attempts` decay over time (or track per source) instead of clearing when a different user succeeds. Test: repository test doing 4 wrong + own PIN twice, asserting lock on the next failure.

- [x] **R23-02 HIGH Verified. The permission matrix lets non-owners grant anything.** (R22-30 partial.) `updateStaffPermissions` (repo about 4739-4753) is the path the matrix dialog uses (`ui/screens/ManageUsersScreen.kt` about 926). It has no subset check and no `SYSTEM_*` check, and the matrix UI doesn't restrict toggles. A Manager with `SYSTEM_PERMISSIONS` grants another account `SYSTEM_PERMISSIONS`, `SYSTEM_MANAGE_STAFF`, `SYSTEM_BACKUP_RESTORE`, `SYSTEM_FACTORY_RESET`, P&L, then logs in as it and grants them back. Fix: same rules as `updateStaff` (granted set must be a subset of the caller's permissions; only owners grant `SYSTEM_*`), and disable those toggles in the matrix for non-owners.

- [ ] **R23-03 HIGH Verified. Managers' discounts are rejected at checkout; owners can't edit their own account.** (R22-04 / R22-31 regressions.)
  - The Add and Edit staff dialogs save `maxDiscount = 10.0` for every non-cashier (`ManageUsersScreen.kt` about 529, 714). The screen treats `SALES_HIGH_DISCOUNT` as a 100% limit (`ui/screens/CartScreen.kt` about 317) but the repo uses `sessionUser.maxDiscountPercent` only (repo about 1301), so a Manager giving 20% gets no prompt and then Complete Sale fails.
  - Editing yourself sends that 10.0, and the repo throws "Users cannot change their own maximum discount" (repo about 4646) for anyone whose limit isn't exactly 10, so owners (100%) can't change their own name or PIN. That also means the R22-02 alert can never be cleared (R23-06). Cashier limits with decimals are truncated by `toInt()`.
  - Fix: one shared helper for the effective discount limit (owner or `SALES_HIGH_DISCOUNT` = 100, otherwise `maxDiscountPercent`) used by both screen and repo; show the max-discount field for managers too (prefilled with the real value); send `maxDiscount = null` when unchanged.
  deferred: pending owner decision on whether Managers have an editable max discount or always 100% via SALES_HIGH_DISCOUNT. Helper unified and owner self-edit fixed in commit 3e02a3d.

- [x] **R23-04 HIGH Reported. Staff delegates can still exceed their own rights.** (R22-03 / R22-30 partial.)
  - `createUser` with `customPermissions = null` gives the new account the role's default permissions with no subset check; a restricted manager creates a MANAGER with full defaults, a cashier with only Manage Staff creates a cashier with default discount/hold/dues rights and a PIN they know. Same for role changes, which reset the target to role defaults.
  - Delegates can deactivate or delete users above their own role (`setStaffActive` about 4718, `deleteUser` about 4556 only protect owners).
  - Fix: when a non-owner creates or edits a user, store an explicit custom set = role defaults intersected with the caller's permissions; block any action on a target whose role is above the caller's.

- [x] **R23-05 MED Reported. PIN-check oracles remain.** (R22-29 partial.) The uniqueness throttle only counts collisions, which are rare, so it never locks; a non-colliding try tells the attacker the PIN is free. The "generic" message maps back to "PIN already in use by another user" (`utils/AppUtils.kt` about 474, `error_pin_in_use`). `updateStaff` "Current PIN is incorrect" (repo about 4659) is unthrottled, so a Manage Staff holder can guess a Manager's PIN via the old-PIN field and take the account over. Fix: rate-limit every PIN-set attempt and record a failure on a wrong old PIN; map to a neutral message. Longer term consider login by user + PIN so PINs need not be unique.

- [ ] **R23-06 MED Verified/Reported. Owner PIN-reset alert and multi-owner safety.** (R22-02 partial; multi-owner from `f803c19`.)
  - The alert clears only after acknowledgement **and** a PIN change (repo about 4448-4455), but owners can't change their own PIN (R23-03), so it returns on every owner login forever. One global flag is set by any owner-PIN change and shown to every owner without saying which owner was reset. Fix: store `resetOwnerId`, keep a list of resets (who, when, acknowledged by whom), and treat the PIN set during recovery as the change.
  - The device-credential reset (`LockScreen.kt` about 619, VM about 4324-4327) passes no target, so the repo resets the oldest owner by `createdAt`, including a deactivated one, and silently falls back when an id doesn't match (repo about 4429-4434). Fix: show a picker of active owners and fail on an invalid id.
  - Owner A can change owner B's PIN using A's own PIN (repo about 4657-4659), and deactivate, demote or delete B, with nothing logged or shown to B. Fix: require B's current PIN (or the device credential) for another owner's PIN change, and log owner-to-owner actions in the audit shown to B.
  - An inactive owner can't be edited while only one owner is active: `updateStaff` (about 4633, 4642) defaults `targetActive` to false and throws "cannot be deactivated" on any edit. Only run those checks when the change would deactivate or demote an active owner.
  - Stale strings: `users_cannot_promote_to_owner`, `error_owner_exists`, and `users_owner_role_fixed_note` ("permanently assigned to the shop proprietor") no longer match multi-owner.
  deferred: pending owner decision on whether one owner can change another owner's PIN/deactivate them (options a/b/c). Sub-points 1, 2, 4, and 5 implemented in code.

- [x] **R23-07 LOW-MED Reported. Discount/credit approval leftovers.** (R22-04 partial.) Restoring a held cart restores the VM "approved" state (VM about 1094-1095) but not the repo token (memory only, 5 minutes), so the sale fails with no re-prompt; don't restore the approval, or re-prompt when the token is missing. The credit-limit token can be used by manual Add Due for any amount because no amount or hash is passed (repo about 2203). Override tokens aren't cleared on logout or user switch. `hasValidDiscountOverride` still runs every recomposition (`CartScreen.kt` about 476). The PIN is verified twice (dialog then `authorizeDiscountOverride`); if the second fails the discount stays set. Public `grantHoldCartOverride` (VM about 852) is dead code with no PIN check; remove it. The hold token lasts 60 s, so a slow title entry fails; authorize at confirm time. `authorizeHoldCartOverride` results are ignored (Cart about 743, 1123; Scanner about 220).

---

## P1: Broken features and regressions

- [x] **R23-08 HIGH Verified. Supervisor-approved Hold on the Scanner does nothing.** `ScannerScreen.kt` about 717-733: `SupervisorAuthDialog` calls `onAuthorized` first (`SupervisorAuthDialog.kt` about 52-53), which sets `pendingSupervisorActionPin = null`, then `onAuthorizedWithPin` finds it null. Fix: copy both lambdas before clearing, like `CartScreen.kt` about 3176, or use only `onAuthorizedWithPin`.

- [x] **R23-09 HIGH Verified. "Preview Z" button does nothing before the shift is closed.** `RegisterCloseScreen.kt` about 1310 is now `val zTarget = closedShiftZReport` (fallback dropped in `f33fb9e`); the button (about 355) only sets `showPnlZReportDialog = true`, and the flag stays true. The share-format dialog also depends on `zTarget`. Fix: use `closedShiftZReport ?: report?.let { it to rangeLabel }` for preview, and only the snapshot after Confirm.

- [x] **R23-10 HIGH Verified. Excel import now changes text barcodes.** `utils/ExcelHelper.kt` about 74-91: `preserveLeadingZeros` is applied to STRING cells as well as numeric ones (`isBarcode` at about 358). Every valid 12-digit UPC-A passes the EAN-13 check and gets a "0", and 10-digit codes starting with "1" get a "0". The repo fallback (about 4099-4110) only adds zeros, never removes one, so existing products aren't matched, duplicates are created, and scans (12 digits, exact match) can't find the imported items. Re-importing the app's own export duplicates products. Fix: never pad STRING cells; for numeric cells only pad when the shop opts in; in `upsertProducts` also try the barcode with one leading zero removed. Add a test that imports the app's own export and asserts no new products.

- [ ] **R23-11 HIGH Verified. Batch label dialog: Cancel and Share PDF moved outside their row, and Cancel doesn't cancel.** `ui/dialogs/ProductCodePrintDialogs.kt`: the Row opened about 1849 closes about 2153, so the Cancel button (about 2155) and Share PDF (about 2180) now sit in a `verticalScroll` Column with `Modifier.weight(...)`, which likely renders them at zero height. `printJob` (about 944) is never assigned (`scope.launch` about 1867 is not stored), so Cancel only sets `isPrinting = false`, re-enabling Print while the job keeps running (double batch). Progress text is computed but no longer shown. Fix: move the `if (isPrinting) ... else ...` block back inside the Row, `printJob = scope.launch { ... }`, check `isActive` between payloads, show progress.

- [ ] **R23-12 HIGH Verified. The Bluetooth watchdog kills large label batches.** `printer/BluetoothPrinterTransport.kt` about 384-394: budget `25 s + 200 ms per KB`, capped at 60 s. Large jobs are paced at about 14.6 KB/s plus 100 ms per label segment, so a 100-label batch (about 1.2 MB) needs about 90 s and is cut at 60 s; batches over about 65 labels fail partway. Fix: make it an inactivity watchdog reset on each chunk write, or compute the budget from bytes, chunk delay and segment delays with no 60 s cap.

- [ ] **R23-13 HIGH Verified. The receipt bitmap cache can print a stale receipt.** `printer/ReceiptBitmapCache.kt` about 20 keys on `saleId + lang + paper` only; `ui/dialogs/ReceiptSummaryDialog.kt` about 200-214 pre-renders once per key. In Sales History the bill is first built with `liveOutstandingDue = 0.0` and `voucherForSale = null` (loaded async, `SalesHistoryScreen.kt` about 1847, 1864, 1891) and rebuilt after a return from the open dialog; the preview updates but Print uses the cached old bitmap (wrong due, voucher, return lines). Closing the dialog before pre-render finishes leaves a stale entry (`put` after the dispose-time `clear`). `put`/`clear` also recycle a bitmap an in-flight print may still be reading, so toggling 58/80 mm or language during a print can fail it. Fix: key on `activeBill.hashCode()` too, key the effect on `activeBill`, check `isActive` before `put`, and don't recycle (drop the reference).

- [ ] **R23-14 MED Verified. Inclusive-tax profit ignores loyalty.** (R22-32 partial.) `utils/PaymentMath.kt` about 633 `netRevBeforeRefund = grandTotal + loyaltyDiscount - taxAmount`. With the R22-32 sale plus ৳50 loyalty (cost 600): invoice view shows profit 300, report shows 250 (correct). Fix: `grandTotal - taxAmount` (grand total is already net of loyalty), in both places. Add a test with these numbers. `PaymentMath.calculateExTaxDiscount` (about 602) is only used by the test; have `buildReport` call it or delete it.

- [ ] **R23-15 MED Reported. Expenses: double tap adds two expenses.** `ExpensesScreen.kt` about 509-536 now keeps the dialog open until the result, but the AddExpenseDialog confirm (`ui/components/AdvancedPosFeatures.kt` about 1321) has no `isSaving` guard. Add one.

- [ ] **R23-16 MED Reported. With "decimal quantities" off, weighed items break.** VM about 1471-1473, 1520-1522 floor quantities regardless of `isFractionalUnit(product.unit)`: 0.5 kg becomes 1 (refused if stock is 0.5), and setting 0.5 in the cart rounds to 0 and removes the line. Fix: keep fractions for fractional units, clamp to at least 1 otherwise, and move the toggle from Receipt Layout to selling settings.

- [ ] **R23-17 MED Reported. Due reminders.** "Send now" still sends to settled customers: `duesList` only holds outstanding dues, so `currentDue` falls back to the stale `reminder.dueAmount` (`DuesScreen.kt` about 1243); read the live due and treat missing as 0. With notifications denied, failed reminders retry every 15 minutes forever and add a FAILED log row each time, and recurring reminders become one-shots (`utils/DueReminderScheduler.kt` about 355-375, 409-434); cap retries, skip when the permission is missing, tell the user. Channel "both" launches WhatsApp and SMS back to back (about 1256-1266); use a chooser or chain them.

- [ ] **R23-18 MED Reported. Supplier ledger edge cases.** (R22-34 partial.) Invoice A 1000, payment P 1000, void A (P stays live, due 0); invoice B 500 (due 500), pay Q 500 (due 0), void Q: recalculation gives max(0, 500 - 1500) = 0, correct is 500, because add/pay still update due incrementally and ignore P while the recalculation counts it. Fix: block voiding an invoice when standalone payments would exceed remaining invoice dues, or keep supplier credit explicit and recalculate on every write. A payment whose typed note starts with "Paid on Invoice #" is excluded from the recalculation and can't be voided (repo about 5510, 5525); mark invoice payments with a flag, not note text. Still deferred without a note: expense matched by timestamp, weighted purchase price not restored, no id-link migration. `testR22_34` re-implements the sum locally; needs a repository test with fakes.

- [ ] **R23-19 LOW-MED Verified. Bangla stock-reason migration uses the wrong words.** repo about 583-598 maps "পুনরায় মজুত" and "অসঙ্গতি"/"পার্থক্য", but the app stored "পুনরায় মজুদ" and "গরমিল" (`values-bn/strings.xml` 1484, 1487), so Restock and Discrepancy rows are never migrated. Map from the actual historical strings, and run it once (store a done flag) instead of on every start.

- [ ] **R23-20 LOW-MED Reported. Idle lock leftovers.** (R22-19 partial.) `Modifier.trackUserActivity()` (`utils/AppUtils.kt` about 536) is never used, so long entry inside dialogs and sheets can still be locked mid-work; apply it to dialog and sheet roots (it also makes utils depend on `ui.viewmodel`; move it). A restored value not in the options shows the nearest option but locks at the stored value (`SettingsScreen.kt` about 556); clamp on load. No test.

---

## P2: Printing leftovers

- [ ] **R23-21 MED Reported. "Print dialog opened" now reports as failure.** `printReceiptImage` and `printLabelImages` return `false` when the system dialog opens (`utils/BillActions.kt` about 950-956, 1050-1060, 1152-1159, 1222-1243), so single label print shows "failed" (`ProductCodePrintDialogs.kt` about 2732, 3107) and Settings test receipt/label toast "failed" (`SettingsScreen.kt` about 1265, 1510). Fix: return a tri-state (`DIRECT_SENT` / `DIALOG_OPENED` / `FAILED`) and show nothing or "Opening print dialog" for the dialog case.

- [ ] **R23-22 MED Reported. USB twin printers and replug.** (R22-11 partial.) A second printer with the same VID:PID and no permission yet has a serial-less key, which `isSamePrinter` treats as "same", so it is disabled in the picker and can never be tapped to grant permission (`SettingsScreen.kt` about 2287-2306). After a replug or reboot both serials are unreadable, `findUsbDeviceByKey` (`printer/UsbPrinterTransport.kt` about 54-58) picks the first, and `sendToUsbPrinter` (about 292-297) never re-checks the serial after permission is granted, so TSPL can still reach the ESC/POS printer. Fix: compute conflicts only when both serials are known; after a grant, re-read the serial and try the next candidate on mismatch. `isLikelyUsbPrinter` (about 122-125) rejects a device on the first HID or mass-storage interface before checking for a printer-class interface; return true if any interface is class 7 first.

- [ ] **R23-23 LOW-MED Reported. Other printing leftovers.**
  - SYSTEM mode is blocked for the batch when a stale identical target exists, because the same-as-receipt check (`ProductCodePrintDialogs.kt` about 1897) runs before the mode check. `isPrinting` resets before the PDF fallback is built (about 1981), re-enabling the button.
  - Settings `launchTestReceiptPrint` (about 5156-5192): `isPrintingTestReceipt` is never shown (no spinner), no success toast, English "Generation failed". The older test receipt and test label buttons (about 1244, 1484) still set busy inside `launch`.
  - JPEG generation failure in `printReceiptWithBluetoothFallback` still returns false silently (`BillActions.kt` about 1293-1294).
  - All batch payloads still held in memory.
  - Bluetooth connect timeout 4.5 s (`BluetoothPrinterTransport.kt` about 227) includes SDP lookup; slow or sleeping printers may fail all attempts (uncertain); consider 8-10 s for the first attempt. `tryConnectSocket` (about 258) and the status query (about 450) swallow cancellation; if the watchdog fires during the status query the result is still Success.
  - "Not connected" hint cached by `remember(receiptTarget)` (`SettingsScreen.kt` about 898, 1394), doesn't update on plug/unplug. USB tab "None" row wording. Batch where every bitmap fails shows no message (`ProductCodePrintDialogs.kt` about 1986, 2002).

---

## P3: Feedback, errors and data leftovers

- [ ] **R23-24 MED Reported. Error messages still wrong or English.** (R22-24 partial.)
  - "Supplier no longer exists" (repo about 5344) and "Due ledger no longer exists" (about 2345) hit the generic `no longer exists` rule (`AppUtils.kt` about 485) and show "Product not found"; match only "Product ... no longer exists".
  - "Supervisor authorization required to exceed customer credit limit" (repo about 1279, 2203) should use `error_credit_limit_exceeded_auth`.
  - Still unmapped (raw English): voucher errors (repo about 174-181, 1214-1217), customer delete credit/voucher blocks (about 1737-1762), "Insufficient store credit balance" (about 1242, 1252), "Authentication locked out for N seconds" (about 4366, 4585), all user-management messages (about 4382-4749: "User not found", own account/role/status/discount, every "Non-owners cannot ..."), auto supplier expense edit/delete (about 5247, 5273), supplier overpayment, return quantity, product not in sale, barcode exists/in cart, sale validation messages (about 1092-1188), backup/password (about 4229-4271), AuthViewModel literals (VM about 4266, 4270).
  - Remove dead rules: "An owner account already exists", "PIN already in use" (after R23-05), "Cannot promote staff to owner", "Cannot void payment created automatically", "Void failed".
  - Long term: throw typed exceptions with a resource id instead of matching English text.
  - Tests: one test per rule using the **exact** repo message; current inputs like "Invoice not found in system" are not real messages.

- [ ] **R23-25 LOW-MED Reported. Feedback leftovers.** (R22-23 partial.) `previewDuplicateDueLedgers` still calls `onResult(emptyList())` on error (VM about 3570), so the screen says "No duplicate ledgers found". Every failure now shows twice (error-bus toast plus the screen's own toast or snackbar) in Expenses, RegisterClose, Loyalty redeem and Dues; pick one. `DuesViewModel.deleteCustomerDue` (VM about 3313) and SupplierViewModel calls (about 4734-4880) still use plain `runCatching`. Expenses delete toast uses raw `localizedMessage`. The `LaunchedEffect` at `SettingsScreen.kt` about 5023 still shows `owner_only_settings` for a backup-permission denial; the Settings export denial too (about 5857). Stock-only update with `adjustment == null` changes stock without an audit row (repo about 529-531).

- [ ] **R23-26 LOW Reported. R22-26 leftovers.**
  - Khata statement still lumps returns into one opening row (`DuesScreen.kt` about 1703-1716); `DuesViewModel.allReturns` (VM about 2880) was added but is unused.
  - `calculateInvoiceProfit` still runs `associateBy` and `getLoyaltySettings()` per call (VM about 2223); `CartScreen.kt` about 2784 calls it every recomposition without `remember`.
  - `appContext.getString` (not localized) at VM about 906-907, 1007-1009, 1150; new English in the customer timeline (VM about 5551 "items", 5579 "Due payment received", 5607 "CRM Note").
  - Camera "Don't ask again": `shouldShowRequestPermissionRationale` read in composition is also false before the first request, and isn't updated after a denial (`ScannerScreen.kt` about 315, 433-449).
  - Unused `batch_print_all/in_stock/custom` and `print_unable_to_write_document`; "Print error: " shows nothing after it when `localizedMessage` is null (`BillActions.kt` about 308).
  - Reprint badge and JPEG use different Bangla wording.
  - Excel import: blank unit becomes "Pcs" and blank Mfg/Pkg type becomes "Mfg" (`ExcelHelper.kt` about 421-427); generic "Row N: ${e.message}" still English; no confirmation step (not marked deferred).
  - Cart undo merge doesn't check stock on the merged quantity.
  - `isAuditSupplierExpense` by title prefix: auto rows from older installs with different titles become editable.
  - "Cash In" expense with type EXPENSE passes the R22-06 check and counts in P&L; also require `type == "CASH_IN"`.

---

## P4: Localisation and design

- [ ] **R23-27 LOW Reported. English left.** (R22-27 partial.)
  - AuthViewModel literals at VM about 4207, 4262-4274; `SupervisorAuthDialog.kt` about 58, 72 pass Latin digits into `auth_locked_out_seconds`.
  - `SettingsScreen.kt`: about 4559 (crash logs subtitle), 6127, 6134, 6152, 6828, 6865, 6869, 6888, 6964, 7010; Drive backup time labels "12:00 AM" etc. (about 4206); "Generation failed" (about 730).
  - `RegisterCloseScreen.kt` about 812-859 "Cash"/"Nagad"/"Card"/"Due"; about 2967 "Reason:".
  - `DuesScreen.kt`: 50 inline literal pairs (77 ternary lines); "% used" is another inline pair; contentDescription "bKash"/"Nagad"/"Rocket".
  - SalesReport share body is inline ternaries; payment method names untranslated.
  - Print job names: 10 inline ternaries (`SettingsScreen.kt` about 1254, 1500, 5598, 5632, 5651, 5668, 5701; `RegisterCloseScreen.kt` about 1253, 1337; `DuesScreen.kt` about 6856).
  - Scanner: the item plural uses `coerceAtLeast(1)` (about 2889), so an empty cart shows "1 item"; Latin digits in Bangla; wording differs from the fractional branch ("টি আইটেম" vs "টি পণ্য").
  - `audit_by_cashier_fmt` is `" - By: %1$s"`; aapt2 strips the leading space, so it renders "...AM- By: X". Use ` - By:`.
  - Phone-update catch path still shows raw `e.message`.
  - Unused `dues_full_amount_chip` EN has an unescaped `%` (would crash if formatted).

- [ ] **R23-28 LOW Reported. Design tokens barely moved.** (R22-28 partial.) In `ui/`: `Color(0x...)` lines 2212 to 2203; `Color.White/Black/Gray/...` 543 to 541; SettingsScreen `Color.Gray` 32 to 30; files using `extendedColors` 10 to 11. The listed spots were fixed. Next: replace the hex copies of theme tokens (`0xFF059669`, `0xFF10B981`, `0xFFF59E0B`, `0xFFEF4444`, `0xFF047857`) with `extendedColors`/`colorScheme`, one screen per commit, starting with StockCheckComponents, DuesScreen, ProductsScreen, RegisterCloseScreen, CartScreen.

---

## R23-T1 Tests
- [ ] Restore the deleted tests listed in the rules, rewritten to call production code (`sanitizeNumericInput`, `computeBulkUpdatedPrices`, `localMillisToUtcMidnight`/`utcMidnightToLocalMidnight`, the statement builder, `StockAlertWorker` filters, `restoreCartItem`, the expense date label).
- [ ] Replace fake tests: `testR21_01` (SystemClock returns 0 so `0 in 0..0` always passes; inject a clock), `testR21_02` (read the format strings from resources), R22-09 (tests `withTimeoutOrNull` itself; test `UsbPrinterTransport` with a fake), R22-15 (local arithmetic; call the height function), R22-04 (only checks the hash changes; test `hasValidDiscountOverride` and the repo check), `testR22_34` and the barcode part of `testR22_35` (re-implement logic locally).
- [ ] Add tests: R23-01 lock-screen brute force, R23-02 matrix subset, R23-03 manager 20% discount completes and owner self-edit saves, R23-06 reset with two owners, R23-10 export-then-import creates no products, R23-12 watchdog budget for 1.2 MB, R23-13 cache key changes with bill content, R23-14 loyalty numbers, R23-19 migration with the real Bangla strings, R23-24 one test per mapping with exact repo messages.
- [ ] Fix citations in `audits/AUDIT_FIXES_21.md` (about lines 140, 142, 144, 148) that point at deleted tests.

## Owner questions (answer before the agent implements)
- **R23-06:** Should one owner be allowed to change another owner's PIN or deactivate them? Options: (a) yes, but log it and alert the other owner; (b) only with the other owner's current PIN or the device credential; (c) never.
- **R23-16:** With "decimal quantities" off, should weighed units (kg, g, litre) still allow decimals? (Recommended: yes.)
- **R23-03:** Should Managers have an editable max discount like cashiers, or always 100% via `SALES_HIGH_DISCOUNT`?
