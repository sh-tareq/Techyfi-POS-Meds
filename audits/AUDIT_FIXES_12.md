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
| A-1 | FIXED | `mergeCustomers` (repo about 1407-1416) doesn't move the secondary customer's `storeCredit`, so the credit is stranded on the archived record. |
| A-2 | FIXED | Edge cases: held carts saved before the fix carry a fractional loyalty discount (VM about 822). `(discount / ptCashVal).toInt()` (VM about 1297) can truncate to n-1 when `pointCashValue` is fractional (e.g. 0.1), which exceeds the 0.05 tolerance. Use `floor(x + 1e-6)` or round. |
| A-3 | FIXED | — |
| A-4 | PARTIAL | When the cashier also adds an explicit "Store Credit" split row, the VM skips its own row, the rows sum to `grandTotal - SC`, and the repo (about 820) throws. |
| A-5 | PARTIAL | (a) With two Cash legs, each leg is reduced by the full change, so the sum breaks and the sale throws. (b) The printed receipt uses `getSplitPaymentsJson()` (Cart about 533), which shows tendered amounts and no Store Credit row. (c) The reprint's change = `amountPaid - grandTotal` (SalesHistory about 1932) is wrong for split + store credit or a Due leg. For Cash 400 + Due 200 on a ৳500 bill, ৳100 change is never shown. |
| A-6 | PARTIAL | Double deduction is fixed, but **walk-in vouchers still fail**: the "Store Credit" method (repo about 800) and the split row (about 825) still require a name and phone. Customer credit plus a voucher: Apply (Cart about 1415) applies the combined amount, and the repo throws "Insufficient voucher balance". |
| A-7 | PARTIAL | The voucher is restored only when the split JSON has a `voucherCode`. A plain "Store Credit" payment or a manual split row has no code, so a walk-in gets nothing back and a customer with a phone gets credit they never had. The ledger type is "VOID" (the spec says SALE_VOID; pick one and use it consistently). Restoring forces status ACTIVE even on an expired voucher. |
| A-8 | FIXED | Minor: the ratio is applied evenly, so returning only the non-taxable line of a mixed sale refunds it with tax added. |
| A-9 | FIXED | Separate `replaceCart=false` (Add to Cart Again) using current catalog products/prices without overwriting cart discount/price mode vs `replaceCart=true` (Void for Edit) preserving sold prices. *(Verified by unit tests & code trace)* |
| A-10 | FIXED | Synchronously reset cart state in `holdCurrentCart` before background insert to prevent race condition with `restoreHeldCart`. Localized auto-saved cart title/note strings. *(Verified by unit tests & code trace)* |
| A-13 | FIXED (setting hidden) | The "Expiring Soon" filter chip (LoyaltyScreen about 647) still suggests points expire. Remove it or relabel it. |
| A-14 | FIXED | Default `CustomerLoyalty` tier set to "Regular". `getTierRank` helper ensures automatic recalculation only upgrades tier rank. *(Verified by unit tests & code trace)* |
| B-1 | FIXED (REPLACE) | MERGE still aborts on a duplicate voucher code (`insertVoucher` ABORT, repo about 2935 / AppDaos about 701). |
| B-2 | **PARTIAL (Orphan FK Fixed)** | Skip orphan returns/returnItems with missing `saleId` FK mapping during backup MERGE. Natural-key de-duplication across all tables and derived balance recomputation pending in Task 4. *(Verified by unit tests & code trace)* |
| B-3 | PARTIAL | The repo check and transaction are present, but it reuses `CUSTOMERS_EDIT` (managers have it by default). The audit asked for owner-only or a new permission. The UI (CustomerDossierSheet about 370) always shows Adjust, so cashiers only find out after submitting. |
| B-4 | FIXED | The formula is duplicated instead of using `report.totalExpenses`, with different filters (`!= "CASH_IN"` vs `== "EXPENSE"`, and case sensitivity), so it will drift. Unasked change: the default period changed from WEEK to TODAY. Revert it or confirm with the user. |
| C-1 | PARTIAL | The AddEditProductDialog stock-note field (about 1408 on box 1377) is untouched, so its text is still invisible in dark mode. |
| C-2 | FIXED | Lifted clear/remove dialog state to top-level `ScannerScreen` with `canVoidCart`/`canRemoveItem` permissions checks, invoked in both portrait and landscape branches of `TabletSplitTerminal`. *(Verified by unit tests & code trace)* |
| C-3 | FIXED | Minor: `catch (e: Exception)` also swallows `CancellationException`. Rethrow it. |
| C-4 | **NOT FIXED** | Commit 5974e54 didn't touch SettingsScreen. There is still no busy state or progress on local backup (about 3530-3551). |
| C-5 | FIXED | Added `onResult: ((Boolean) -> Unit)?` callback to reset `isSaving = false` on failed save. *(Verified by unit tests & code trace)* |

## New bugs in the Round 11 code
- [ ] **R-1 Phantom ledger debit** (repo about 979-992). A customer with a phone pays by voucher. SALE_REDEMPTION -200 is written, but
  `balanceAfter` is unchanged and `voucherCode` is null (`activeVoucherForPlainSC` is never assigned). Deleting the sale restores the voucher
  with no matching ledger entry, so the customer's ledger is permanently off.
- [ ] **R-2 The repo never checks voucher status or expiry.** An expired voucher restored from a held cart (VM about 826-834, including the
  fallback voucher object) is accepted and spent.

## Round 11 items still open (unticked)
A-11, A-12, A-15, B-5 … B-18, C-6 … C-18. These are unchanged from AUDIT_FIXES_11.md. Carry them over and work on them
after sections 0-2 here. B-18 (the committed real backup file) still needs the user's decision.

---

## Tasks, in order
1. **G-1** Commit the missing source so HEAD builds. Verify from a clean checkout.
2. - [x] **Regressions:** C-5, C-2, A-10, A-14, A-9, and the B-2 orphan `saleId = 0` FK failure. *(Verified: compileDebugKotlin & testDebugUnitTest passed)*
3. **PARTIAL/NOT FIXED money items:** A-6, A-7, R-1, R-2, A-4, A-5, then A-1 (merge credit) and A-2 (edge cases).
4. **B-2 properly:** de-duplicate every table by natural key (e.g. due payment = phone+amount+timestamp; return = backup id
   mapped through the saleId map; supplier = name+phone), skip orphans, and recompute derived balances (dues, stock,
   loyalty, store credit, supplier totals) from the ledgers after the merge. Add an instrumented test that merges the
   same backup twice and asserts that the totals don't change.
5. **B-3** Add a UI gate and owner-only access (or a new permission). **C-1** Fix the AddEditProductDialog field. **C-4** Local backup busy state. **B-1** MERGE voucher conflict.
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
