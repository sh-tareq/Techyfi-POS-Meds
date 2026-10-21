# Audit Round 24: Verification of Round 23 + Label Printing + UI Polish
Generated: 2026-10-21

Work branch: `claude/modest-bardeen-ak8pl3` at `0407194` (59 commits on top of `main` `9b9292d`, which **is not merged to main yet**). Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`, `VM` = `ui/viewmodel/POSViewModels.kt`. Line numbers are approximate; search for the quoted code if they moved.

## State
- Round 23 (`a9adc82..0407194`): 31 of 32 items ticked, R23-28 honestly deferred. This was the best round so far: most core fixes are real and many tests now call production code.
  - **Fully fixed:** R23-02, R23-04, R23-08, R23-09, R23-10, R23-12, R23-13, R23-14, R23-19, R23-21, and most of R23-07.
  - **Partial:** R23-01, R23-03, R23-05, R23-06, R23-11, R23-15, R23-16, R23-17, R23-18, R23-20, R23-22, R23-23, R23-24, R23-25, R23-26, R23-27, R23-T1.
- Also reviewed for the first time: the label printing work (LBL-01..30, `c731411..e7a7798`): 21 of 30 done, 8 partial, LBL-30 mostly not done. And the UI polish / Settings search work (`846e30b..b81f891`): no permission bypass, but several wrong or dead search results.
- **Owner decisions applied in `80a48b1`** (please confirm with the owner; they were not answered in this chat):
  - Managers have an editable max discount, default 20%; owners 100%; cashiers default 10%.
  - Owner-to-owner PIN/status changes require the target owner's current PIN or the device credential, and alert the target (option b).
  - One owner cannot delete another directly.
  - Weighed units (kg, g, litre, ml and Bangla units) keep decimals when "decimal quantities" is off.
- Clean (verified): no compile errors found by reading (still nothing built here), no em dashes added, `values` and `values-bn` both 3175 keys with matching placeholders, every `%d` gets an Int/Long, no Room schema change (DB version 38).

## Rules (same as round 23, plus two)
- Only tick an item when every sub-point is done in code; check with `git show --stat <sha>`. Write "deferred: <reason>" for anything skipped.
- Never delete an existing test. Tests must call production code. **Delete or convert the fake duplicates in `R23RegressionAndIntegrityTest.kt`** (testR23_01, _06, _10, _12, _13, _14, part of _16) and the local re-implementations in `P1bAuditFixesTest.kt` (testR22_34, barcode half of testR22_35).
- Security and money fixes go in `DataRepository`, each with a repository-level test.
- **New: do not add behaviour nobody asked for.** Commit `713e691` added `sweepStaleHeldCarts(24L)` on every app start, which silently deletes held carts and the cart snapshot (R24-05).
- **New: actually run the tests.** At least one new test will fail as written (R24-T1). Report the exact pass/fail count.
- New strings in both `values` and `values-bn`; no new inline `if (isAppBangla) "..." else "..."` pairs (they went up again, 234 to 235).
- **Verified** = re-checked by the round-24 auditor in the code. **Reported** = from a sub-audit; re-check first.

---

## P0: Security

- [x] **R24-01 HIGH Verified. A Manager can approve their own over-limit discount.** `authorizeDiscountOverride` (repo about 260-268) only calls `verifySupervisorPin(pin)` and grants; it doesn't check who approves or their limit. A Manager capped at 20% enters 60%, the supervisor dialog opens, they type their own PIN, the sale completes. Any Manager can approve a cashier's 100% discount. This makes the new editable Manager limit meaningless. Fix: pass the requested percent; require `supervisor.id != sessionUser?.id` unless the approver is an owner, and `getEffectiveMaxDiscount(supervisor) >= requestedPct`. Test: repository test for self-approval rejected and for a 20% manager approving 30% rejected.

- [x] **R24-02 HIGH Verified. Another owner's PIN can be guessed without limit.** `updateStaff` (repo about 4781-4789): when owner A changes owner B's role or status, `require(pinMatches(oldPin, existing.pinHash))` fails without calling `recordFailedPinAttempt()`. A can guess B's PIN by retrying deactivate/demote; a correct guess lets A then change B's PIN. This defeats owner decision (b). Fix: record a failure and throw a neutral message, as the PIN-change path at about 4810 already does. Also: `setStaffActive(ownerB, true)` re-activates another owner with no PIN and no alert (about 4926).

- [x] **R24-03 MED Reported. Owner-to-owner flow is broken in the UI and the alert is unreliable.** (R23-06 partial.)
  - The Edit dialog shows "Current PIN" only when a new PIN is typed (`ui/screens/ManageUsersScreen.kt` about 618-628, 709-728), so a consented deactivate/demote of a co-owner always fails with an unmapped English error. Show a "Target owner's PIN" field whenever role or status changes on another owner, or offer the device-credential prompt.
  - The alert is one overwritten prefs slot (repo about 4876-4888): editing owner C wipes the pending alert for B. Any edit, even a rename, writes "PIN reset using device lock credentials" (`owner_pin_reset_alert_message`, `owner_pin_reset_audit_info`). The actor isn't shown. A demoted owner never sees it (Navigation about 492 checks only `isOwner`). A device-credential reset alerts only the reset owner, which is the account an attacker now controls. Fix: append-only audit list {target, actor, action, time, acknowledged by}; separate wording per action; show to the target regardless of role; show device resets to **all** owners.

- [x] **R24-04 MED Reported. PIN lockout leftovers.** (R23-01, R23-05 partial.)
  - Slow brute force never locks: the window is 5 minutes since the last failure (repo about 4591-4595), so 4 guesses every 5 minutes is about 1,150 per day. Add a daily cap (e.g. 10 failures in 24 h by wall clock).
  - A reboot resets the window and drops an active lock (`loginLockRemainingMillis` about 4667). Keep `lock_until` across reboots (wall clock, capped).
  - `total_failures` never decays (only factory reset clears it), so after 20 lifetime typos every lock lasts 15 minutes. Decay after 24 h without failures.
  - The PIN-uniqueness oracle is still usable: only collisions count, and successful PIN changes are free, so a Manage Staff holder can cycle their own PIN through 0000-9999 and the first "Invalid or unavailable PIN" reveals someone's PIN. Rate-limit all PIN changes (e.g. 3 per user per hour), or move to user + PIN login.
  - `R23RegressionAndIntegrityTest.testR23_01` is a local simulation (the real one in `P0AuditFixesTest` is fine).

- [x] **R24-05 MED Verified. Held carts are silently deleted on every app start.** repo `init` about 119-122 calls `sweepStaleHeldCarts(24L)` (added in `713e691`, not requested by any audit). A layaway cart held yesterday disappears, plus the cart crash-recovery snapshot. Fix: remove it, or make it an owner setting (default off) with a confirmation and an audit entry.

- [x] **R24-06 MED Reported. Existing Managers drop to a 10% discount after the update.** `80a48b1` removed `SALES_HIGH_DISCOUNT` from Manager defaults; old dialogs saved `maxDiscountPercent = 10.0` for every non-cashier and the column default is 10 (`data/db/AppDatabase.kt` about 200). Managers on role defaults go from 100% to 10%, not the decided 20%. Fix: one-time migration setting 20 where `role = MANAGER AND customPermissions IS NULL AND maxDiscountPercent = 10` (or ask the owner). The delegate caps in `createUser` (about 4474) and `updateStaff` (about 4794) use raw `maxDiscountPercent`, not `getEffectiveMaxDiscount`. `supervisor_auth_desc_discount` truncates 12.5 to 12 (`%2$d`). EditStaffDialog doesn't reset the discount field when the role changes. No test completes a sale as a 20% Manager.

- [x] **R24-07 LOW-MED Reported. Other permission leftovers.**
  - Permission matrix: the subset check runs on the whole set, so a Manager can't save any change for a target who already holds a permission the Manager lacks. Validate only `granted - existing`.
  - A cart-scoped credit token is still accepted by manual Add Due (null `cartHash` matches any token); require `grant.cartHash == null` there.
  - `remember(discountTaka, ...)` around `hasValidDiscountOverride` (`CartScreen.kt` about 476) ignores expiry; a restored **percentage** discount over the limit is neither clamped nor re-prompted and fails at checkout (about 537). Prompt for the supervisor at checkout when the token is missing.
  - Staff with "Backup & Disaster Recovery" see Restore but it always fails (`importBackup` requires owner, repo about 3101; error swallowed, VM about 3696-3708). Honour the permission or hide Restore.
  - Settings Import/Export lets any `SYSTEM_SHOP_SETTINGS` holder export products with purchase prices (no `INVENTORY_VIEW_COSTS`) and the dues list (no `DUES_VIEW`) (`SettingsScreen.kt` about 6982-6997; VM about 3719-3780).

---

## P1: Label printing (LBL follow-up)

Short names: `Dlg` = `ui/dialogs/ProductCodePrintDialogs.kt`, `Img` = `utils/ProductCodeImageUtils.kt`, `Tspl` = `printer/TsplLabelPrinterDriver.kt`.

- [x] **R24-08 HIGH Verified. Multi-up batches can run out of memory and crash.** Dlg about 1253-1310 keeps every label bitmap in `bitmapsWithCopies` and recycles only at the end. 2- or 3-across with 300+ products at 50x30 mm is about 720 MB; `OutOfMemoryError` is not an `Exception`, so the app crashes. Fix: compose and convert each row as you go, then recycle.

- [x] **R24-09 HIGH Reported. Barcodes may not scan on small labels.** (LBL-13 partial.) Labels render at 20 px/mm with whole-pixel bars, then are bilinear-scaled to 8 dots/mm and thresholded at 170 (Img about 351, Tspl about 174, 197); multi-up resamples again (Tspl about 266-268). Example: 25x15 mm with a 13-digit Code128 is about 1.2 dots per bar. Fix: render the barcode at printer dots with a module of at least 2 whole dots, or use native TSPL `BARCODE`. `isBarcodeTooDenseForLabel` (Img about 1160) ignores Code128 numeric compression, so 13 digits on 50x30 is wrongly flagged. Verify on a real printer.

- [x] **R24-10 HIGH Reported. Multi-up counts are wrong, so "Retry remaining" reprints labels.** Each multi-up payload is a row of 2-3 labels but its copies are recorded as 1 (Dlg about 1306), so `successfulCount` counts rows and "remaining" (about 1384-1396) reprints labels already printed; `printLabelsBatch` uses `safeAcross` (Tspl about 509), over-counting a partial last row. `totalSentSoFar` counts products, not labels, for the cancel toast and failure banner (about 1743, 2725-2730). In PER_PRODUCT mode a product with no entered quantity is shown as 1 but prints the hidden `batchCopies` (about 1123, 2055; Img about 916-918). Fix: count labels everywhere; one default.

- [x] **R24-11 MED Reported. Label cancel and retry.**
  - Cancelling inside the `delay()` between chunks of one segment closes the socket mid-`BITMAP` (`BluetoothPrinterTransport.kt` about 428), so the next job can print garbage. Write each segment under `NonCancellable`, cancel only between segments; disable the batch X and single-dialog close while printing (Dlg about 1053, 1595, 3047, 3069).
  - Cancel sets `isPrinting = false` immediately, and the old job's `finally` (about 1524-1527) sets it again later, resetting a new batch (R23-11 race). Clear state only from `printJob.invokeOnCompletion` or when the job is still current.
  - "Retry remaining" (about 1750) has no `enabled = !isPrinting`; a double tap starts two jobs.
  - "Reprint last" (about 2644) can never appear: `lastPrintedItems` is set only on success, which also dismisses the dialog.
  - Single-label dialog has no Cancel; Share PDF has no cancel or progress.

- [x] **R24-12 MED Reported. Label layout limits.** No print-head width check: 3-across 40 mm with 2 mm gap gives `SIZE 124 mm`, wider than a 104 mm head (Tspl about 228; Settings about 2442-2480). Custom height clamp is 150 in dialogs but 200 in `resolveLabelDimensionsMm`/`productCodeLabelPixelSize` (Img about 105, 707, 834). Copies field (LBL-07) clamps only on focus loss: typing 600 shows red but prints the last valid 60; disable Print on error.

- [x] **R24-13 LOW-MED Reported. Label leftovers.**
  - LBL-30 not done: `LabelStyleSettings`/`loadLabelStyleSettings`/`saveLabelStyleSettings` (Img about 723-894) are never called; the two dialogs keep separate state and duplicate the render loop (Dlg about 1262, 1324) with different pref keys (`show_price_unit` vs `show_unit`, `extra_field_title` vs `extra_field_label`).
  - LBL-18: no saved templates, `showLogo` (Img about 145) never drawn. LBL-24: no "Reset all", no auto-fit indicator. LBL-28: Bangla label text hard-coded in Kotlin (Img about 175-182, 233) and follows `isAppBangla()` instead of `showBanglaDigits`; new English "(N cut)" (about 931) and "Techyfi POS" in the test label.
  - Previews are generated in `remember{}` on the main thread on every keystroke and never recycled (Dlg about 1154, 2967); preview doesn't show the 1-bit result or multi-up layout.
  - "Stock qty" mode prints 1 label for zero-stock products (Img about 686). Batch EAN-13 silently falls back to Code128.
  - Receipt reprint logo bitmap never recycled; "Techyfi POS"/"My Shop" sentinels hard-coded (`BillPdfGenerator.kt` about 451-486).
  - The DPI test computes dot counts itself; `buildMultiUpTsplPayloads`, retry-remaining and cancellation are untested.

---

## P2: Printing and selling leftovers from R23

- [x] **R24-14 MED Verified. USB: one printer without a serial can be both receipt and label printer.** (R23-22 regression.) `isPrinterConflict` (`printer/PrinterTarget.kt` about 153-169) returns false when both serials are blank, even for identical keys, so TSPL can go to the ESC/POS printer with no warning; `testR23_22` asserts this wrong behaviour. Return true when the keys are equal ignoring case; use the same check on the single-label path (`BillActions.kt` about 1229 still uses `isSamePrinterTarget`).

- [x] **R24-15 MED Verified. Idle lock values 1 or 2 minutes are clamped to "Never".** `data/InvoiceSettings.kt` about 395-399 picks the nearest of 0/5/15/30/60, so 1 or 2 becomes 0 and turns the lock off; the test asserts 1 to 0. Clamp positive values to the nearest non-zero option.

- [x] **R24-16 MED Reported. Small selling bugs.**
  - `setQuantity` (VM about 1524-1532) floors a discrete-unit 0.5 to 0 and removes the line; clamp to 1 when `newQty > 0`.
  - Expense dialog: `localSaving` (`ui/components/AdvancedPosFeatures.kt` about 1222-1226) stays true after a fast failure, so Save and Cancel stay disabled. Drop `localSaving` or reset it in the result callback.
  - Due reminder "Send now" looks up the due by exact phone in `duesList` (`DuesScreen.kt` about 1172); a format mismatch or unloaded list now **cancels** a valid reminder. Use `getCustomerDueByPhone(normalizePhone(...))`.
  - ReceiptSummaryDialog FAILED shows "could not decode receipt image" (about 776-781), wrong for BT/USB failures and duplicates the transport toast.
  - Bluetooth: if the watchdog fires during the status query the result is still Success (`BluetoothPrinterTransport.kt` about 477-480). "Not connected" hint doesn't react to plug/unplug. Batch "all bitmaps failed" message says "receipt image" (Dlg about 1397). Batch payloads still all in memory (not marked deferred).
  - Preview Z prints and shares as a normal Z with no "preview" marker (`isInterimX = false`).

- [x] **R24-17 MED Reported. Supplier ledger leftovers.** (R23-18 partial.) `[SYS_INVOICE_PAYMENT]` is note text shown raw in `SuppliersScreen.kt` about 782, 1669; use a real flag. Legacy match depends on `invoice.paidAmount == payment amount`. Installs that voided an invoice before this round still under-report due; add a one-time recalculation. The void-guard error is unmapped English with raw doubles (repo about 5783). Expense matched by timestamp, weighted purchase price not restored, no id-link: (deferred: linking expense by foreign key ID and restoring pre-invoice weighted purchase price require schema changes and DB migration, deferred to next schema revision). `importBackup` doesn't re-run the Bangla stock-reason migration with `force = true` (R23-19).

---

## P3: Settings search and UI polish (new code, first review)

- [x] **R24-18 MED Reported. Settings search results land in the wrong place or do nothing.** (`cfb911e`; `ui/screens/SettingsScreen.kt`.) No permission bypass was found: every result reuses the gated home-page callbacks.
  - "Idle auto-lock" opens the **Tax** page (about 854-863, `onClick = onTaxClick`); the control is a home-page card (about 1296-1370).
  - Language, Theme, Layout and Default cost do nothing when tapped (about 927, 943, 959, 975, `/* already in preferences */`).
  - Cash drawer kick (about 719) and Allow negative inventory (about 831) flip the setting on one tap from a navigation-looking card; show a real Switch or navigate.
  - Printer results open pickers that do nothing in System print mode; the "Connected" pill only means "configured".
  - Back from a deep-linked page goes to the Receipt overview and loses the query (about 6434-6480).
  - A long store name in the status pill squeezes the title to nothing (about 590-610, 749); cap width and ellipsize.
  - All breadcrumbs and statuses are English literals ("Connected", "Setup", "Owner Only", "Kick ON/OFF", "Blocked (Safe)", "Not Set", "Local Ready", "Ready", "Audit", "Protected", "N% Cost"; raw "SYSTEM"/"AUTO"); about 20 hex status colours.

- [x] **R24-19 LOW Reported. Search consistency.** (`be93c99`, `b81f891`.) The Dues Payments tab now filters by the shared search query, so a query from Outstanding hides payments and says "No payments in this range"; method match is on raw values, so "বিকাশ" finds nothing (`DuesScreen.kt` about 331-342, 4849-4851). Dues list, Dues payments, Dues audit and Sales History don't normalise Bangla digits or +880/01 phone formats (Products does); run `toEnglishDigits` and `normalizePhone` on queries. `PosSearchBox` clears focus on IME Search/Enter (`ui/components/PosSearchBox.kt` about 128-135), which may break keyboard-wedge scanners on Products/batch print/stock audit (verify on a device); clear/filter touch targets shrank to 36dp.

---

## P4: Errors, feedback, localisation

- [ ] **R24-20 MED Verified. Error messages.** (R23-24 partial.)
  - **Regression:** the lockout rule maps to `auth_locked_out_seconds` but `getLocalizedRepositoryErrorMessage` calls `getString(resId)` with no argument (`utils/AppUtils.kt` about 497-500, 608), so users see a literal "%1$s". Parse the seconds and pass them.
  - "Cannot permanently delete customer with store credit/active voucher balance" (repo about 1813, 1817) don't match the rules (about 489, 491); match "delete".
  - Mis-maps: "Failed to generate unique voucher code for expired replacement" shows "voucher expired" (about 468); the generic "Permission" rule (about 458) swallows the specific user-management permission messages; "Non-owners cannot delete/create/modify owner accounts" maps to an approximate sentence.
  - Still unmapped (raw English): owner deletion/role/deactivation messages, target-owner PIN messages, "Invalid or unavailable PIN" (no rule maps to `error_pin_in_use`), PIN format, blank names, unsupported role, first account must be owner, loyalty redemption messages, amount paid below total, split with no rows, phone required for due, due/credit validation, return item selection, supplier void guard and purchase validation, backup file/password errors, starting float, loyalty adjustment, voucher code generation.
  - Dead rules: "Cannot promote staff to owner" (about 523), "Passwords do not match" (about 552), "Terminal locked for" (about 499); `error_supplier_no_longer_exists` unused.
  - Screens still show raw `localizedMessage`: `SuppliersScreen.kt` about 839, 949; `SalesHistoryScreen.kt` about 884, 2272; `ProductsScreen.kt` about 2616, 2782; `ExpensesScreen.kt` about 484; `CustomersScreen.kt` about 120.
  - **Recommended:** stop matching English text. Throw typed exceptions that carry a string resource id (and format args), and map once.
  - Tests: about half the inputs in `testR23_24_exactRepoErrorMessageMappings` and `P3AuditFixesTest.kt` 15-50 are not real repo messages; many rules untested; add a negative test that "Supplier no longer exists" doesn't map to product.

- [ ] **R24-21 LOW-MED Reported. Feedback leftovers.** (R23-25 partial.) `previewDuplicateDueLedgers` still returns `emptyList()` on error (VM about 3591-3600), so Settings says "none found" (about 6703, 7840). Failures still show twice (bus toast plus screen message) in `ExpensesScreen.kt` (about 484, 509, 531, 562; VM about 4655, 4666, 4677), `RegisterCloseScreen.kt` (about 1090, 1120, 1224) and `migrateCustomerDuePhone` (VM about 3273-3276). ProductsViewModel still uses plain `runCatching` (about 1985, 1989, 2041, 2067). Excel import still has no confirmation step and isn't marked deferred.

- [ ] **R24-22 LOW Reported. Localisation leftovers.** (R23-27 partial.)
  - Cash and Nagad both render as "নগদ" in Bangla share text (`BillPdfGenerator.kt` about 252-254, `formatPaymentMethodBangla`); use "নগদ টাকা"/"ক্যাশ" for cash or keep "Nagad" as a brand name.
  - DuesScreen inline pairs unchanged (52); repo-wide 234 to 235; POSViewModels 9 to 12 and ExcelHelper 0 to 7 (new). Scanner "টি পণ্য" literal instead of a plural (about 2902-2910). SalesReport share body still inline pairs (about 577-619). Drive time labels built by string replace (about 5318-5321). Timeline "items" and "Loyalty Points"/"+N pts" (VM about 5590, 5637).
  - Still English: `SettingsScreen.kt` about 7272, 7279, 7297, 1798, 1855; `RegisterCloseScreen.kt` about 3363-3379; `ProductCodePrintDialogs.kt` about 891, 909; `ScannerScreen.kt` about 2862 "Qty"; `DuesScreen.kt` about 3886 `Locale.US` digits.
  - `audit_by_cashier_fmt` uses `"\ - By:"`; use ` - By:` to be safe.
  - Unused: `batch_print_all/in_stock/custom`, `DuesViewModel.allReturns` (VM about 2902).

- [ ] **R24-23 LOW. Design tokens (R23-28 carried over, deferred).** Counts in `ui/`: `Color(0x...)` lines about 2176-2203, `Color.White/Black/Gray/...` about 530-541 (counting methods differ), SettingsScreen `Color.Gray` 30, files using `extendedColors` 11. Nothing moved this round. Plan it as its own pass: one screen per commit, starting with StockCheckComponents, DuesScreen, ProductsScreen, RegisterCloseScreen, CartScreen, then the new Settings search cards.

---

## R24-T1 Tests
- [ ] **Run the suite and report the exact count.** `testR23_19_banglaStockReasonMigration` (`P1AuditFixesTest.kt`) will likely fail: `Mockito.when(dao.updateReason(anyString(), anyString()))` on a `suspend` function has a hidden Continuation (3 args, 2 matchers, `InvalidUseOfMatchersException`). Use a fake DAO or mockito-kotlin `any()` for every argument. It may also race with the repo `init` migration. `testR21_30_cartItemRestoreIndex` should call `Dispatchers.setMain`.
- [ ] Delete or convert the fake tests listed in Rules. Extract and test the khata statement builder (`DuesScreen.shareCustomerStatement`) and the expense date label (`AdvancedPosFeatures.kt` about 1465) instead of `testR21_26_customerStatement...` and `testR21_30_expenseDateLabel...` local arithmetic. Rename `testR21_19_localMillisToUtcMidnightRoundtrip` (wrong item id).
- [ ] R22-09: make the 60 s USB permission timeout injectable and test it. R22-15: call the real height function (the current test passes because `measureText` returns 0).
- [ ] Add tests: R24-01 self-approval and approver limit, R24-02 owner PIN throttle, R24-05 held carts survive restart, R24-06 manager migration and a 20% Manager completing a sale, R24-08 multi-up memory (row-by-row), R24-10 multi-up label counts, R24-14 serial-less conflict, R24-15 clamp, R24-20 lockout message with seconds, plus one test per error rule with the exact repo message.

## Owner questions
- Please confirm the decisions applied in `80a48b1` (listed in State): Manager default 20% and editable; owner-to-owner changes need the target's PIN or device unlock; no direct owner deletion; weighed units keep decimals.
- **R24-05:** Should held carts ever be cleaned up automatically? If yes, after how many days, and should it ask first?
- **R24-06:** Existing Managers were effectively 100% before. After this update should they be 20% (the new default) or stay 100%?
