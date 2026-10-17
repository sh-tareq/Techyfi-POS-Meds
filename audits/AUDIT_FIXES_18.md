# Audit Round 18 — Fix List
Generated: 2026-10-09

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`. Line numbers are approximate.

## State
- **One commit** since Round 17 (4208594), covering N-1, N-3 and N-5. **No boxes were ticked in AUDIT_FIXES_17.md**, and 7 items are still open there.
- Unit tests: 85/85. Instrumented: **80/80** (full suite; Migration 12, MoneyFlows 32). Strings: en and bn are in sync.
- Tests T-1 … T-4 from Rounds 16 and 17 are **still not done**, except one good new migration test (N-3). The dead-code tests
  (`calculateEffectiveAmountPaid` in DueSaleAmountTest and MoneyFlowsTest) are still there.

## Verification of Round 17

| ID | Verdict | Evidence / remaining problem |
|----|---------|------------------------------|
| N-1 | FIXED | processReturn (repo about 4433): walk-in or new-voucher credit = `refund - dueAdj - voucherRestored`. Test `testN1_walkInVoucherReturn_voucherShareGoesBackToVoucherOnly`. |
| N-3 | FIXED | MIGRATION_36_37 is now a code migration: it merges case-variant duplicates (sums stock, keeps the newest row, logs an InventoryAdjustment), then lowercases. All the columns it reads are NOT NULL, so it's safe. A real test (`testN3_migration36To37_mergesCollidingBarcodeProducts`) builds a v36 DB with a colliding pair. `jsonContainsBarcode` is now case-insensitive. |
| N-5 | **REGRESSION** | Walk-in with no phone: a fresh voucher is issued (good, tested). **But the expired-voucher case with a phone double-credits the customer:** `redirectedExpiredVoucherCredit = voucherPortion; voucherPortion = 0.0`, then `customerPortion = scApplied - voucherPortion + redirectedExpiredVoucherCredit` (repo about 698 in deleteSale, about 4342 in processReturn) *(verified)*. Sale 80 = expired V30 + C50, then void: the customer gets **110**. Fix: `customerPortion = scApplied - voucherPortion` (voucherPortion is already 0 after the redirect), or stop zeroing voucherPortion. **Also:** when the target phone comes from `voucher.customerPhone` (or `walkinPhone`) but `phone` is blank, the share is "redirected", yet the customer credit is gated on `!phone.isNullOrBlank()`, so **it's lost**. Credit `targetCustPhone`, not `phone`. Add tests for both. |
| N-2 | **NOT FIXED** | RegisterCloseScreen: the card is still `Color(0xFF0F172A)` (about 847), and `SummaryRow(starting float)` (about 882) has no colour, so it defaults to `onSurface` (#0F172A in light). **The value is invisible in light mode** *(verified)*. |
| N-4 | NOT FIXED | Legacy pre-v37 sales still misattribute voucher vs. customer credit. |
| N-6 | NOT FIXED | Still `customerPhone = "VOUCHER"` on new VOUCHER_RESTORE rows (now also in the fresh-voucher path). The customer dossier shows voucher balances as the customer's `balanceAfter`. |
| N-7 | NOT FIXED | — |

### Minor issues in 4208594
- The fresh-voucher loop uses `catch (_: Exception) {}` inside the DB transaction. Catch only the constraint exception (code collision). If all 5 attempts fail, **throw** rather than silently dropping the share.
- The fresh replacement voucher has `customerPhone = null` and `sourceReturnId = null` on the return path. Set `sourceReturnId = returnId` so it can be traced.
- `cancelVoucher` was added but has no caller and no permission check. Either wire it to an owner-only UI action, or remove it.

---

## Open items (everything below is carried over and still open)

### HIGH
- [x] **N-5** double credit and lost share. Fixed in the working tree (uncommitted): `customerPortion = scApplied - voucherPortion` in deleteSale and processReturn; the credit goes to `phone ?: redirectTargetPhone` (processReturn uses the fallback only for non-Store-Credit refunds, because the walk-in/new-voucher block already credits the remainder). Tests: `testN5_expiredVoucherWithCustomerPhone_voidCreditsCustomerExactlyOnce`, `testN5_expiredVoucherOwnedByCustomerOnWalkInSale_voidCreditsVoucherOwner` (both fail on the old code by trace: 110 vs 80, and 0 vs 100). **Not yet run: no JDK in the audit shell.**
- [x] **N-2** invisible starting float. Fixed in the working tree (uncommitted): `SummaryRow` (only used on the always-dark shift-math card) now uses white values and #CBD5E1 labels.
- **T-1 … T-4 (tests).** Do these before any more feature work:
  - T-1: migration tests 33→37 with data (taxable default, isTaxInclusive, tier, voucher columns). Only 36→37 exists so far.
  - T-2: delete or retarget the tests that call the dead `calculateEffectiveAmountPaid`. Payment tests must go through `CartViewModel` / `PaymentMath` as production does.
  - T-3: merge with shared history, returns, voids and mixed barcode case. Assert dues, points and credit.
  - T-4: vouchers across several sales, walk-in, expired-with-phone (the N-5 regression), and expired with `voucher.customerPhone` only.

### MEDIUM: money (from AUDIT_FIXES_16/17)
N-4 (legacy attribution), M-10 (wallet refund vs. dialog), M-1/R-4 (due-confirm and credit-limit use the raw Due row; SC-method partial skips them),
M-5/R-5 (receipt vs. reprint "Paid"; card over-tender absorbed), B-7 (backfill `isTaxInclusive`; Excel profit includes tax), X-8 (tier recompute
with the configured thresholds), X-4a/b (supplier payment pairing by note text; voided-sale adjustments inflate stock on merge), M-14 (owner can't
clear a limit to 0), M-7 (loyalty Taka 1:1; clawback display mismatch), R-6 (revert XSSF; `LOWER()` in `getProductByBarcode` defeats the PK index, and since barcodes are
now always lowercase, lowercase the input instead), N-6, N-7.

### MEDIUM: language and design
- D-1/L-2: **0 of 347 `formatTaka(` calls in ui pass the app language.** There's one app-language source to add (`LocalAppLanguage`).
- L-1: about 149 multi-word English literals in ui, plus the X/Z report generators and repo exception text in toasts.
- D-5, D-6 contrast pairs; U-1 (Checkout 2.54:1); U-2 (deletes without confirmation); U-3 (45 IconButtons under 48dp); U-4 (white islands).
- `Color(0x` 2393 remaining (StockCheckComponents 506, DuesScreen about 222, ProductsScreen about 169).

---

## Tasks, in order
1. N-5 (double credit and lost share, with tests), N-2.
2. T-1 … T-4.
3. Money: N-4, M-10, M-1/R-4, M-5/R-5, B-7, X-8, X-4a/b, M-14, M-7, R-6, N-6, N-7, plus the 4208594 minor issues.
4. D-1/L-2, L-1, D-5, D-6, U-1 … U-4, then the remaining design work, one screen per commit, checked in light AND dark.

## Rules
Same as before: at most 5 items per commit; **tick each item in this file** with the sha plus the test name when done (the last two rounds ticked nothing);
money tests go through the real production path and must fail first; run the FULL `connectedDebugAndroidTest` suite and report the counts from the XML;
don't touch `*.apk`, `Backup Files/` or `.agents/`; don't rewrite history.
