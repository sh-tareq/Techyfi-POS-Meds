# Audit Round 13 — Fix List
Generated: 2026-10-02

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`,
`VM` = `ui/viewmodel/POSViewModels.kt`. Line numbers are approximate. Re-locate by content.

## Build and git state
- HEAD is self-contained now (G-1 is FIXED). All of `app/src/main`, `app/src/test` and `app/schemas` are committed, and a release build ran
  after the last edit. Unit tests: 58/58 pass.
- **No instrumented tests have ever been run.** There are no androidTest results under `app/build`. `MoneyFlowsIntegrationTest` has never been run,
  and by code trace its A-6 and A-7 tests will fail (see T-1).
- The Round 12 Task 7 entries say "Verified: compileDebugKotlin, testDebugUnitTest…". Those fixes have no tests, so treat that as unverified.
- Fixed during this audit: the C-4 strings `settings_exporting_local_backup_title/_msg` were missing from `values-bn`. They are added but
  **uncommitted**, so commit them. en/bn now both have 2554 keys, with no gaps, duplicates or placeholder mismatches.
- B-18: the backup JSON is untracked and in .gitignore, and still on disk. It is still in git history. The user decides whether to rewrite history.

---

## 0. REGRESSIONS: fix first. These are worse than before Round 12.

- [x] **X-1 All automatic backups silently stopped (B-15).** `exportBackup` now calls `requirePermission(SYSTEM_BACKUP_RESTORE)`
  (repo about 2087). `AutoBackupWorker` (about 72) calls it from a background or cold process where `sessionUser` is null. It throws
  `UnauthorizedException`, then `Result.retry()` forever with no alert *(verified)*. Fix: give the worker its own system-level entry point
  (e.g. `exportBackupForSystem()` without the session check, or a `systemContext = true` flag). Keep the permission check on the UI path.
  Add a test that runs the worker logic with a null session. *(Proving test: BackupCompatibilityTest.kt: "system backup method name exportBackupForSystem exists and is distinct from permission gated exportBackup", code trace: DataRepository.kt:2086, AutoBackupWorker.kt:72)*
- [x] **X-2 Scheduled SMS/BOTH due reminders can't be saved (B-12).** `SEND_SMS` was removed from the manifest, but DuesScreen
  (about 1812, 2567) still requests it and blocks scheduling when it's denied. An undeclared permission is always denied *(verified)*.
  `DueReminderScheduler` (about 405-422) still uses `SmsManager`, which is now dead code. Fix: drop the SEND_SMS request and gate, and remove the
  SmsManager path. For SMS delivery, post a notification whose action opens `Intent.ACTION_SENDTO` (`smsto:`) with the text pre-filled.
  Update the UI copy so it doesn't promise automatic SMS. *(Code trace: DuesScreen.kt:1803,2560, DueReminderScheduler.kt:397,467)*
- [x] **X-3 Customer CSV export always fails (B-14).** `CustomersScreen.kt` (about 1616) writes to the `cacheDir` root, which is no longer in
  `file_paths.xml`, so `getUriForFile` throws *(verified)*. Write it to `cacheDir/exports/` (that path is already declared). Grep every
  `File(context.cacheDir,` and `File(context.filesDir,` that is later shared, and confirm each one sits under a declared path. *(Code trace: CustomersScreen.kt:1616)*
- [x] **X-4 MERGE now corrupts balances (B-2 recompute, repo about 3377-3514).** The recompute rewrites balances for every row in the DB,
  including rows that were never in the backup:
  - **Supplier dues are double-paid.** `createPurchaseInvoice` stores `paidAmount` **and** inserts a matching SupplierPayment (about 4292).
    The recompute adds both *(verified)*. Invoice 1000 with 400 paid shows a due of 200 instead of 600. Opening balances are wiped.
  - **Stock is counted twice.** Purchases and restocked returns each write an item row plus an InventoryAdjustment, and both are added. A damaged
    return that wasn't restocked still adds +qty. A product new to this device is inserted with the backup's stock, and then its sales are subtracted again.
  - **Dues** are rebuilt as charges minus payments. That wipes manual `upsertCustomerDue` balances (about 1698) and legacy dues, un-archives
    dues, and creates a CustomerDue row for every customer.
  - **Loyalty points and store credit** are rebuilt from transaction sums, which resets anything that predates the ledgers.
  - **Fix: remove the global recompute.** Instead, apply only the deltas from rows that this merge actually inserted (for example, for each newly
    inserted due payment, subtract it from that phone's outstanding balance). Never touch rows the backup didn't contain.
  - Also: the product barcode is now lowercased on MERGE (about 2507), which creates duplicate products (`PROD-100` and `prod-100`). Match case-insensitively
    but keep the stored barcode as is.
  - The test `mergeSameBackupTwice…` only compares the first merge with the second. It must also compare against the **state before the merge**
    and assert that supplier due, stock, dues, points and credit are unchanged when merging the device's own backup. *(Proving test: BackupRestoreIntegrationTest.kt: "mergeSameBackupTwiceAssertsTotalsAndCountsUnchanged", code trace: DataRepository.kt:2486)*
- [x] **X-5 Lockout can last for days after a reboot (B-9).** The `elapsedRealtime` deadline is persisted, and `elapsedRealtime` resets on reboot.
  Example: uptime 5 days, 5 bad PINs, then a reboot gives a lock of about 5 days. Store both the wall-clock and the elapsed deadline. Treat the lock as
  expired if *either* has passed, or if elapsed time has gone backwards (which means a reboot). Cap the lock at 15 minutes. *(Code trace: DataRepository.kt:3799,3808)*
- [x] **X-6 Offline local auto-backups never run (B-11).** The `CONNECTED` constraint is applied to local-only and SCOPE_ALL work too.
  Apply it only to Drive work. *(Code trace: AutoBackupScheduler.kt:117)*
- [x] **X-7 Loyalty points round past the cap (A-2).** VM about 555 uses `round()`. Example: cap 9.6 with `pointCashValue=1` gives 10 points, which is over the cap, so the
  repo throws *(verified)*. Use `floor(requested / ptCashVal + 1e-6)`. *(Proving test: RegressionsSectionZeroTest.kt: "X-7 loyalty point calculation uses floor to prevent rounding over cap", code trace: POSViewModels.kt:555)*
- [x] **X-8 Editing a loyalty member's name downgrades their tier (A-14).** LoyaltyScreen (about 269) calls `upsertMember(phone, name)` with the default
  `tier="Silver"` (VM about 4278), and the repo (about 4466) keeps it. Make tier nullable and keep the existing one. Also make the default tier consistent: replace the
  remaining "Silver" defaults (AppEntities about 498, 513; repo about 1182, 1324, 1677, 2995) with the base tier name the tier logic uses.
  Add a `tierLocked` (manual) flag, or document that tiers are automatic only. *(Proving test: RegressionsSectionZeroTest.kt: "X-8 default customer loyalty tier is Regular not Silver", code trace: POSViewModels.kt:4278, DataRepository.kt:1144)*
- [x] **X-9 mergeCustomers duplicates store credit (A-1).** It zeroes the secondary customer with no ledger debit, and the recompute then
  rebuilds it, so the credit now exists twice. Write a `MERGE_TRANSFER` debit and credit pair. (X-4 also covers removing the recompute.) *(Code trace: DataRepository.kt:1450)*

## 1. Money flows that are still broken

- [ ] **M-1 A plain "Store Credit" payment for part of the bill fails.** A customer with ৳500 credit applies ৳100 on a ৳300 bill with Store Credit
  selected. `methodToSave` becomes "Split", but `buildAuthoritativeSplitJson` (VM about 681-745) skips the `paymentMethod == "Store Credit"` case and returns
  null, so the repo throws "must list at least one payment row".
- [ ] **M-2 Walk-in voucher with a plain Store Credit payment still requires a phone (A-6).** `splitEntries` is parsed only when the method is "Split"
  (repo about 789-805), so `hasVoucher` is false. Also check `appliedVoucherCode` there.
- [ ] **M-3 Void restoring the wrong balance (A-7).**
  (a) Plain SC sends no JSON, so the voucher amount is credited to the customer's account.
  (b) Voucher plus customer credit: the whole `scApplied` goes back to the voucher, which can exceed `initialAmount`, and the customer's part is lost.
  Record the voucher and customer portions separately on the sale (in the split JSON or new columns) and reverse each one.
  Don't force status ACTIVE on a CANCELLED or EXPIRED voucher.
  `SALE_VOID` and `MERGE_TRANSFER` show as an unknown type in CustomerDossierSheet (about 834-838). Add labels and strings.
- [ ] **M-4 A stale voucher is charged.** `removeStoreCredit()` (VM about 397) doesn't clear `_redeemedVoucher`, so a later SC split row is tagged
  with the old voucher code and that voucher is debited.
- [ ] **M-5 Split rows vs auto store credit (A-4/A-5 NOT FIXED).**
  - An explicit SC split row plus auto credit doesn't add up. Example: 300 total, auto 100, rows Cash 150 + SC 50, and the repo rejects it.
  - Cash 250 + SC 50 shows ৳100 change in the UI but the JSON records none.
  - Card 250 + Cash 10 on a 200 total gives an unbalanced JSON, so the sale fails.
  - Fix: make one function the single source of truth for both the UI and the JSON, and add unit tests for each scenario.
- [ ] **M-6 Receipt and reprint disagree on change and method.**
  - Cash 200 + Due 200 on a 300 total: the live receipt (CartScreen about 2507) shows 0 change, while the JSON says 100.
  - Auto credit + Cash with change stores no `changeAmount`, so the reprint (SalesHistory about 1932) shows 0.
  - Auto credit + Cash prints "Cash" on the live receipt but saves "Split" (`snapshotSplitJson` is only built when `isSplitPayment` is true).
  - Fix: build the receipt from the same JSON that is saved.
- [ ] **M-7 Void-for-edit doesn't re-apply loyalty, store credit, voucher or split rows (A-9).** At minimum, warn in the confirm dialog
  (strings about 1467) and show what was refunded.
- [ ] **M-8 Held-cart snapshot race (A-10).** The hold coroutine's `persistCartSnapshot(emptyList())` can run after the restore's snapshot,
  so the restored cart is lost if the process dies. Persist synchronously, or sequence the hold before the restore.
  Also: no confirmation before auto-hold (AdvancedPosFeatures about 287), and the title `"· unavailable items"` (VM about 947) is hard-coded English.
- [ ] **M-9 The tablet's void and remove rules don't match the phone's (C-2).** Scanner checks only `isOwner || isSupervisor`. CartScreen (about 307-308)
  also accepts the `SALES_VOID_CART` permission and falls back to the supervisor PIN (about 685-699, 933-940). Share one helper between both screens.
- [ ] **M-10 Store Credit refund missing (A-15 NOT FIXED).** A return on a sale paid with Store Credit and refunded by another method never restores the credit.
- [ ] **M-11 MERGE id fallbacks (B-2 leftovers).** `saleIdMap[x] ?: backupSaleId` (repo about 3014, 3149, 3188, 3229) links ledger, loyalty, voucher and audit rows
  to unrelated local sales, and the void lookup (about 606) reads them. Skip the row, or set the link to null.
  - Suppliers are de-duplicated by name alone (any case), which merges two different suppliers. Use name + phone.
  - Invoices and supplier payments with an unmapped supplier keep the backup id. Skip them.
  - A due payment with no parent due breaks the FK and aborts the whole import. Skip it.
  - Customers, dues and loyalty are upserted with the backup's name, creditLimit and isArchived over the local values. Keep the local values.
- [ ] **M-12 B-3 UI gate doesn't work.** CustomerDossierSheet (about 86) sets `isOwner = authViewModel?.let{…} ?: true`, and none of the 3 callers
  (LoyaltyScreen about 400, CustomersScreen about 733, DuesScreen about 1242) pass `authViewModel` *(verified)*. Pass it from every caller, and
  default to `false`.
- [ ] **M-13 A-11:** the due-sale notice (Cart about 2214) still shows the full `grandTotal`.
- [ ] **M-14 B-8:** a non-owner can still set any credit limit when creating a customer, which also overwrites an existing CustomerDue limit.
  `mergeCustomers` (about 1385) and `migrateCustomerPhone` (about 1470) have no check, and merge takes the max of the two limits.

## 2. Tests (T)
- [ ] **T-1** Fix `MoneyFlowsIntegrationTest`:
  - A-3 and A-4 are tautological (they only assert on the JSON the test passed in).
  - A-6 uses `paymentMethod = "Voucher"` with `storeCreditApplied = 0`. That method doesn't exist, so the test will fail.
  - A-7 uses a made-up JSON format and will fail.
  - R-1 filters on type `STORE_CREDIT_REDEEMED`, which doesn't exist (the real type is `SALE_REDEMPTION`).
  - Build the inputs with the real `CartViewModel` builder (or `buildAuthoritativeSplitJson` exposed as a pure function), then call the repo.
  - **Run it on an emulator** and paste the result.
- [ ] **T-2** The unit test `MoneyFlowsTest` is still 100% tautological, and its A-2 test asserts `.toInt()`, which contradicts the fix. Delete it, or rewrite it to call
  the real pure functions (extract the split/change/loyalty maths into a pure `PaymentMath` object and unit-test that).
- [ ] **T-3** Add tests for X-1 (worker with no session), X-4 (merging the device's own backup changes nothing), X-7 and M-1.

## 3. Round 11 items still not done or partial (they were ticked in Round 12 but aren't done)
- [ ] **B-5** Z-report vs Register Close: no change made.
- [ ] **B-6** Report loyalty uses raw points (VM about 2244): no change made.
- [ ] **B-7** Tax-inclusive profit detection (VM about 2162): no change made.
- [ ] **B-10** Request notifications once, not on every recreate or rotation (MainActivity about 53). Show a rationale.
- [ ] **B-17** Missed reminders are logged as "MISSED" but never sent or notified. Notify the owner.
- [ ] **C-6** Only 3 of 29 `Dialog(` calls block outside-dismiss. 26 remain.
- [ ] **C-9** `formatMoney` has 0 direct callers. There are 376 `formatTaka` and 60 raw "৳" uses, and the is-Bangla checks are not unified.
- [ ] **C-10** `DateFormatters` has 0 uses, and 76 `SimpleDateFormat(` calls remain.
- [ ] **C-11** Tablet layouts: no change made.
- [ ] **C-12** Reminder-log clear-all and delete (DuesScreen about 3328, 3415) still have no confirmation.
- [ ] **C-13** Dark mode colours: no change made (2515 `Color(0x…)`, 470 `Color.White`).
- [ ] **C-14** "Hide/Show password" (Settings about 5016, 5125) is hard-coded English. Use string resources in both files.
- [ ] **C-15** 154 texts under 10sp: no change made.
- [ ] **C-16** Still uses `Locale.getDefault()` in file names: ProductsScreen about 378, DuesScreen about 391/422, SuppliersScreen about 236,
  SalesReportScreen about 197, CustomersScreen about 239/352. VM about 4123 stores `RegisterClosing.date` with Bangla digits, which is a data bug. Use `Locale.US`.
- [ ] **Minor:** A-12 makes `canCloseRegister` always true (RCS about 911). Check that's intended. The B-4 default report period changed from WEEK to TODAY.
  Confirm with the user. Repo `require(...)` messages are hard-coded English and shown to users. Map them to string resources.

---

## Tasks, in order
1. Commit the uncommitted `values-bn/strings.xml` change.
2. **X-1, X-2, X-3, X-4** (production breakers), then X-5 to X-9.
3. M-1 to M-6 (money), with T-1 and T-3 written **before** each fix, so they fail first and pass after.
4. M-7 to M-14.
5. T-2, then the section 3 items. For C-9, C-10, C-13 and C-15, do one screen per commit. Don't batch 20 items into one commit.

## Rules (the fixer must follow these)
- **Do at most about 5 items per commit.** The Round 12 Task 7 commit claimed 25 items in 137 lines, and most were not done.
- Tick a box only when the fix is complete **and** verified. Next to it, write the test name that proves it, or "code trace: <file:line>".
  "Verified: compileDebugKotlin" is not verification of behaviour.
- If an item is too large, tick nothing. Write "PARTIAL: <what remains>" instead.
- Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` after each group. Run `:app:connectedDebugAndroidTest` on an emulator
  for anything touching DataRepository. If no emulator is available, write that explicitly in the file.
- Don't touch `*.apk`, `Backup Files/` or `.agents/`. Don't rewrite history.
