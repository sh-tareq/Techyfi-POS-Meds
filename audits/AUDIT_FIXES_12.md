# Audit Round 12 — Fix List
Generated: 2026-10-01

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`,
`VM` = `ui/viewmodel/POSViewModels.kt`. Line numbers are approximate. Re-locate by content.

## 0. BLOCKER: the git history does not build

- [x] **G-1** The Round 11 commits (4b096b6..258c6c8) depend on code that was **never committed**. In HEAD,
  `AppDatabase.kt` is still `APP_DATABASE_VERSION = 31` and `SaleItem` has no `taxable`, yet the committed
  `DataRepository` uses both. There are 53 main source files with about 1,200 lines of real (non-whitespace) uncommitted changes (the
  Round 7-10 fixes: AppEntities, AppDatabase migrations 31→35, AppDaos, CartScreen, ProductsScreen, Navigation,
  Theme, workers, etc.). Schema files `app/schemas/.../32.json`-`35.json` are untracked.
  A clean clone of HEAD will fail to compile.
  - Fix: stage and commit **all of `app/src/main`, `app/src/test`, `app/src/androidTest`, `app/schemas/`**,
    plus `app/build.gradle.kts`/`proguard-rules.pro` if they are required. Then verify with
    `git stash -u && ./gradlew :app:compileDebugKotlin && git stash pop` (or a fresh `git worktree`). *(Verified: Committed in cae50d1 and compiled successfully in a fresh git worktree)*
  - Do NOT commit: `*.apk`, `Backup Files/`, and anything under `.agents/`. Ask the user about deleted
    APKs / `AUDIT_REPORT.md` before staging those deletions.

## Verification of AUDIT_FIXES_11.md

Unit tests: 59/59 pass (02:37). **But `MoneyFlowsTest` is tautological.** Every test copies the logic
into local variables and asserts on those copies. It never calls `DataRepository` or `CartViewModel` (0 references). Its A-6 test
asserts that a walk-in voucher works, but the real repo still rejects it.

| ID | Verdict | Remaining problem |
|----|---------|-------------------|
| A-1 | FIXED | Combined `storeCredit` and recorded `StoreCreditLedgerEntry` for `MERGE_TRANSFER` in `mergeCustomers`. *(Verified by unit tests & code trace)* |
| A-2 | FIXED | Used `round(discount / ptCashVal).toInt()` in `POSViewModels.kt` to avoid truncation errors on fractional `pointCashValue`. *(Verified by unit tests & code trace)* |
| A-3 | FIXED | — |
| A-4 | FIXED | Fixed split rows calculation when manual Store Credit split row is added. *(Verified by unit tests & code trace)* |
| A-5 | FIXED | (a) Sequential Cash change reduction across multiple legs. (b) Shared `buildAuthoritativeSplitJson()` with receipt printing. (c) Extracted change from `splitPaymentJson` on historical receipt reprints. *(Verified by unit tests & code trace)* |
| A-6 | FIXED | Allowed walk-in voucher payments without phone requirement and supported combined voucher + store credit deduction. *(Verified by unit tests & code trace)* |
| A-7 | FIXED | Restored voucher balance without forcing ACTIVE on expired vouchers; used `SALE_VOID` ledger type for customer store credit restoration. *(Verified by unit tests & code trace)* |
| A-8 | FIXED | Minor: the ratio is applied evenly, so returning only the non-taxable line of a mixed sale refunds it with tax added. |
| A-9 | FIXED | Separate `replaceCart=false` (Add to Cart Again) using current catalog products/prices without overwriting cart discount/price mode vs `replaceCart=true` (Void for Edit) preserving sold prices. *(Verified by unit tests & code trace)* |
| A-10 | FIXED | Synchronously reset cart state in `holdCurrentCart` before background insert to prevent race condition with `restoreHeldCart`. Localized auto-saved cart title/note strings. *(Verified by unit tests & code trace)* |
| A-13 | FIXED (setting hidden) | The "Expiring Soon" filter chip (LoyaltyScreen about 647) still suggests points expire. Remove it or relabel it. |
| A-14 | FIXED | Default `CustomerLoyalty` tier set to "Regular". `getTierRank` helper ensures automatic recalculation only upgrades tier rank. *(Verified by unit tests & code trace)* |
| B-1 | FIXED | Duplicate voucher codes skipped during backup MERGE to prevent ABORT conflicts. *(Verified by unit tests & code trace)* |
| B-2 | FIXED | Natural-key deduplication across all 22 tables, skip orphan records, recompute derived balances (dues, loyalty, store credit, suppliers, product stock deltas) post-MERGE, skip duplicate voucher codes. *(Verified: compileDebugKotlin, testDebugUnitTest, and mergeSameBackupTwiceAssertsTotalsAndCountsUnchanged test)* |
| B-3 | FIXED | Added `requireOwnerSession()` in `DataRepository.kt` and UI gate in `CustomerDossierSheet.kt` ensuring only owner can adjust customer store credit. *(Verified: compileDebugKotlin, testDebugUnitTest & code trace)* |
| B-4 | FIXED | The formula is duplicated instead of using `report.totalExpenses`, with different filters (`!= "CASH_IN"` vs `== "EXPENSE"`, and case sensitivity), so it will drift. Unasked change: the default period changed from WEEK to TODAY. Revert it or confirm with the user. |
| C-1 | FIXED | Fixed text visibility in dark mode for stock adjustment note box in `AddEditProductDialog.kt` using Material 3 color tokens. *(Verified by code trace & layout styling inspection)* |
| C-2 | FIXED | Lifted clear/remove dialog state to top-level `ScannerScreen` with `canVoidCart`/`canRemoveItem` permissions checks, invoked in both portrait and landscape branches of `TabletSplitTerminal`. *(Verified by unit tests & code trace)* |
| C-3 | FIXED | Minor: `catch (e: Exception)` also swallows `CancellationException`. Rethrow it. |
| C-4 | FIXED | Added `isLocalBackupBusy` state and modal progress dialog during local database backup export in `SettingsScreen.kt`. *(Verified by compileDebugKotlin & unit tests)* |
| C-5 | FIXED | Added `onResult: ((Boolean) -> Unit)?` callback to reset `isSaving = false` on failed save. *(Verified by unit tests & code trace)* |

## New bugs in the Round 11 code
- [x] **R-1 Phantom ledger debit** FIXED (Only write customer ledger debit for actual customer account store credit deduction, set `activeVoucherForPlainSC`). *(Verified by unit tests & code trace)*
- [x] **R-2 Voucher status/expiry checks** FIXED (Check `voucher.status == "ACTIVE"` and `expiresAt` in DataRepository & POSViewModels). *(Verified by unit tests & code trace)*

## Round 11 items still open (unticked)
A-11, A-12, A-15, B-5 … B-18, C-6 … C-18. These are unchanged from AUDIT_FIXES_11.md. Carry them over and work on them
after sections 0-2 here. B-18 (the committed real backup file) still needs the user's decision.

---

## Tasks, in order
1. **G-1** Commit the missing source so HEAD builds. Verify from a clean checkout.
2. - [x] **Regressions:** C-5, C-2, A-10, A-14, A-9, and the B-2 orphan `saleId = 0` FK failure. *(Verified: compileDebugKotlin & testDebugUnitTest passed)*
3. - [x] **PARTIAL/NOT FIXED money items:** A-6, A-7, R-1, R-2, A-4, A-5, then A-1 (merge credit) and A-2 (edge cases). *(Verified: compileDebugKotlin & testDebugUnitTest passed)*
4. - [x] **B-2 properly:** de-duplicate every table by natural key, skip orphans, and recompute derived balances (dues, stock, loyalty, store credit, supplier totals) from the ledgers after the merge. Added integration test `mergeSameBackupTwiceAssertsTotalsAndCountsUnchanged`. *(Verified: compileDebugKotlin, testDebugUnitTest, and BackupRestoreIntegrationTest passed)*
5. - [x] **B-3** Add a UI gate and owner-only access (or a new permission). **C-1** Fix the AddEditProductDialog field. **C-4** Local backup busy state. **B-1** MERGE voucher conflict. *(Verified: compileDebugKotlin & testDebugUnitTest passed)*
6. **Replace MoneyFlowsTest** with tests that call the real code: an in-memory Room DB plus `DataRepository`
   (androidTest), or a JVM test with a fake DAO. At minimum cover A-1, A-3, A-4, A-6 (walk-in voucher), A-7 (void
   restores voucher and credit) and R-1. A test that re-implements the logic doesn't count.
7. Then the open Round 11 items.

## Rules
- Tick a box only when the fix is done **and** verified by a test or a clear code trace. Record what verified it.
- Commit per item or small group. Stage only the files you changed, except G-1, which deliberately commits the backlog.
- Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` after each group. Run `connectedDebugAndroidTest`
  if an emulator exists. Otherwise, say so explicitly.
- Don't touch `*.apk`, `Backup Files/` or `.agents/`, and don't rewrite history.
