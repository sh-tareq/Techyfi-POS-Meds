# Audit Round 17 — Fix List
Generated: 2026-10-09

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`. Line numbers are approximate.

## State
- 10 commits since Round 16: 2 fixes (88b76a4, ee6b37a) and 8 one-screen design-polish commits. **No boxes were ticked in AUDIT_FIXES_16.md,**
  which is honest, because most of it was not attempted.
- DB is v37. Unit tests: 85/85. Instrumented: **77/77** (full suite, 10 classes). Strings: en and bn have 2572 keys each, with no gaps.
- Design progress since Round 16:
  - Text under 10sp: 66 → **13**.
  - `SimpleDateFormat(` in ui: 24 → **3**.
  - `DateFormatters` uses: 36 → 58.
  - `dismissOnClickOutside=false`: 50 → 100.
  - `isSystemInDarkTheme()` in ui: **0**.
  - `Color(0x`: 2494 → 2393. `Color.White`: 436 → 428. Raw "৳": 41 → 28.
  - No new hard-coded English was added (149 multi-word literals with the broad grep, unchanged).

## Verified fixed this round
| ID | Evidence |
|----|----------|
| R-1 | Import lowercases barcodes (repo about 2665), plus a v37 migration lowercases existing ones. See N-3 for side effects. |
| R-2 | `isProcessing` guard (ReturnDialog about 1373). The dialog closes on success and on failure, so the flag can't get stuck. |
| R-3 | AddEditProductDialog uses surface luminance (the app theme) instead of `isSystemInDarkTheme()`. |
| M-3 | Sale stores `voucherCode`/`voucherAmount`/`customerCreditAmount`, which are backed up. Traced: V=100, sale A uses 70 (walk-in), sale B = V30 + C50. Void B then A gives V=100, C=50 in either order. Partial returns 40+40 give V+30, C+50. **Correct for new sales.** |
| B-5a | Daily totals use local `startOfDay` (88b76a4). |
| B-5b | `netCollections` no longer subtracts refunds twice. |

## New bugs
- [ ] **N-1 HIGH Walk-in voucher sale, Store Credit "register" return pays out twice.** Walk-in sale of 100 paid fully by voucher, full return
  as Store Credit with walk-in register: V is restored by 100 (repo about 4204-4245) **and** the new customer is credited 100 (about 4385-4402), so 200 is paid out for 100 *(verified)*.
  The no-phone, no-issueVoucher path also restores V and then issues a second voucher of 100 (about 4344). Fix: the voucher share goes back to the voucher only. The
  walk-in or new-voucher credit must be `refund - voucherShareRestored`.
- [ ] **N-2 HIGH RegisterCloseScreen: the "Starting float" value is invisible in light mode** (141a6ef). The card background is hard-coded `0xFF0F172A` (about 847), and
  `SummaryRow`'s default text is now `onSurface` (#0F172A in light) *(verified)*. Labels in `onSurfaceVariant` are about 3.6:1 there and on the hero gradient (about 552, 581).
  Fix: make that card use `inverseSurface`/`inverseOnSurface`, or pass explicit light text.
- [ ] **N-3 MIGRATION_36_37 barcode collapse.** `UPDATE OR REPLACE products SET barcode = LOWER(TRIM(barcode))` **silently deletes** one product when
  "ABC-1" and "abc-1" both exist, and its stock is lost. Fix: in code, after migrating, merge duplicates (sum stock, keep the newest name and prices, log an
  InventoryAdjustment) before lowercasing. Also, `jsonContainsBarcode` (repo about 4831) compares exact case, so a product referenced by an old
  uppercase held cart can now be deleted. Compare case-insensitively.
- [ ] **N-4 Legacy (pre-v37) sales still misattribute voucher and customer credit.** With voucherAmount=0, the fallback
  `min(storeCreditApplied, initial - remaining)` reproduces the original M-3 bug (legacy sale B gives voucher 80, customer 0). Options: backfill from
  the split JSON and ledger rows where possible, or, when the split is ambiguous, prefer the customer's credit (it's auditable) and log it.
- [ ] **N-5 Expired voucher on a walk-in sale: that share is lost on void or return** (about 618-623, 4218-4223). Fall back to `voucher.customerPhone`, or
  issue a fresh voucher. `"CANCELLED"` is never set anywhere, so either implement voucher cancellation or remove the dead check.
- [ ] **N-6** VOUCHER_RESTORE ledger rows land in the customer's credit history with `balanceAfter` set to the *voucher's* balance, which is confusing in the
  dossier. Store them in a separate voucher ledger, or label them clearly. The `"VOUCHER"` phone fallback (about 639, 4239) is harmless to balances
  (MERGE skips it), but replace it with null and a proper voucher reference.
- [ ] **N-7** Return with "Cash" selected on a fully voucher-paid sale restores the voucher and pays no cash, but the UI says "Cash". Show the actual outcome.

## Carried over from AUDIT_FIXES_16 (not attempted; all still open)
**Tests (do first):** T-1 (migration tests 33→37 with data, including the N-3 collision case), T-2 (the tests still call the dead
`calculateEffectiveAmountPaid`: DueSaleAmountTest, MoneyFlowsTest), T-3 (merge with shared history, returns, voids, mixed case), T-4 (voucher multi-sale,
walk-in, expired, and N-1). `testM3_*` hand-picks values and covers none of the walk-in, return or expired cases.

**Money:** M-10 (wallet refund vs. what the dialog shows), M-1/R-4 (the due-confirm and credit-limit checks use the raw Due row; SC-method partial skips them),
M-5/R-5 (receipt vs. reprint Paid; card over-tender absorbed), B-7 (backfill `isTaxInclusive` for old sales; Excel profit includes tax),
X-8 (tier recompute with the configured thresholds), X-4a/b (supplier payment pairing by note text; voided-sale adjustments inflate stock on merge),
M-14 (owner can't clear a limit to 0), M-7 (loyalty Taka uses 1:1; clawback display mismatch), R-6 (revert the XSSF switch; `LOWER()` in `getProductByBarcode`
defeats the PK index; now that barcodes are lowercase, lowercase the input instead).

**Language:** D-1/L-2: **0 of 347 `formatTaka(` calls in ui pass the app language.** The app-language source is still split (device locale vs. setting).
L-1: about 149 multi-word English literals in ui, the X/Z report generators, and repo exception text shown in toasts.

**Design:** D-5 (secondary on secondaryContainer is 1.82:1 in dark), D-6 (pairs under 4.5:1: onPrimary/primary 3.77 in light, onError 3.76, onTertiary 2.15 in light,
onSurfaceVariant on surfaceContainerHighest 2.96 in dark), U-1 (Checkout button 2.54:1 in light), U-2 (deletes without confirmation), U-3 (45 IconButtons under 48dp),
U-4 (white islands in dark). Remaining: `Color(0x` 2393 (StockCheckComponents 506, DuesScreen about 222, ProductsScreen about 169, …).

---

## Tasks, in order
1. N-1, N-2, N-3 (with a migration test for the collision), N-5.
2. T-1 … T-4. Each must fail on the old code first.
3. N-4, M-10, M-1/R-4, M-5/R-5, B-7, X-8, X-4a/b, M-14, M-7, R-6, N-6, N-7.
4. D-1/L-2 (one app-language source), L-1, D-5, D-6, U-1 … U-4.
5. Remaining design work, one screen per commit. Every style commit must be checked in BOTH light and dark mode (see N-2).

## Rules
Same as before: at most 5 items per commit; tick only with the sha plus the test name; money tests go through the real production path and must fail first;
run the FULL `connectedDebugAndroidTest` suite and report the counts from the XML; never claim "no hard-coded English" without the L-1 grep; don't touch `*.apk`,
`Backup Files/` or `.agents/`; don't rewrite history.
