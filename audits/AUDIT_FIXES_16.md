# Audit Round 16 — Fix List
Generated: 2026-10-09

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`,
`VM` = `ui/viewmodel/POSViewModels.kt`. Line numbers are approximate. Re-locate by content.

## State
- HEAD is clean. 84/84 unit tests pass. **The full instrumented suite ran for the first time: 75/75 passed** across 10 classes
  (BackupRestore 25, MoneyFlows 28, Migration 11, …). DB is v36. Strings: en and bn have 2572+ keys each, with no gaps or mismatches.
- **Correction to AUDIT_FIXES_15:** it said "the only hard-coded English left in `ui` is a deprecation message". That was wrong: the grep was too
  narrow. There are about 100 multi-word English literals in `ui` (DuesScreen 24, ReturnDialog 21, RegisterCloseScreen 16, SettingsScreen 10,
  ScannerScreen 7, …), and the X/Z report generators are entirely English. See L-1.
- **Test quality is still the main weakness:**
  - `AppDatabaseMigrationTest` covers only up to 20→21. **Migrations 21→36 are untested**, including 34→35 (`taxable`) and 35→36 (`isTaxInclusive` plus the tier update).
  - The unit tests for M-1, M-5, M-6 and N-1 call `calculateEffectiveAmountPaid`, which **has no production callers any more** *(verified)*, so they test dead code.
  - All 28 `MoneyFlowsIntegrationTest` cases call `completeSaleWithNotes` with hand-picked `amountPaid` and JSON. None go through `CartViewModel` / `PaymentMath` as production does.
  - The merge test `mergeBackupFromDifferentStateAssertsExactBalances` shares no history between backup and device (so de-dupe never runs), has no returns or voids, never mixes barcode case, and doesn't assert dues, points or credit.

## Verification of Round 15

| ID | Verdict | Remaining problem |
|----|---------|-------------------|
| D-1 | PARTIAL | The default is Latin, and the printer and SMS paths are safe. But **0 of 344 `formatTaka(` calls in `ui` pass the app language**, so Bangla mode shows Latin amounts. The language source is inconsistent: DuesScreen uses the app setting, CartScreen (about 1181, 2403) uses `Locale.getDefault()` (device), and `LocaleWrapper` never calls `Locale.setDefault`. 29 of 36 `DateFormatters` calls default to the device locale. Bangla digits still leak into files on a Bangla-locale phone: ExcelHelper export dates (about 453, 578, 925, 1101, 1193, 1274, 1458), `String.format("#INV-%05d")` with no locale (about 805, 1042), and the X/Z PDF "Generated" date (BillPdfGenerator about 1176, 1398). The Bangla text receipt shows Latin digits while the Bangla PDF shows Bangla. |
| D-2 | FIXED | — |
| D-3 | FIXED | But see U-1. |
| D-4 | FIXED | — |
| D-5 | **REGRESSION (dark)** | Secondary text on `secondaryContainer` is 1.82:1 in dark mode (it was 3.83). Affected: ReturnDialog about 448, 596, 1088. Text on `surface` is 2.33:1 (about 556). There are 61 hard-coded `0xFF4F46E5` uses at 2.33:1 on the dark surface. |
| D-6 | PARTIAL | Pairs under 4.5:1: onPrimary/primary is 3.77 in light; onError/error is 3.76 in both modes; onTertiary/tertiary is 2.15 in light; onSurfaceVariant/surfaceVariant is 4.34 (light) and 4.04 (dark); onSurfaceVariant/surfaceContainerHighest is 3.86 (light) and **2.96** (dark); inversePrimary/inverseSurface is 3.6 in dark. |
| M-1 | FIXED (narrow) | The VM path saves `[SC100, Due200]`. But a due created via a Store Credit method with partial credit (e.g. the cart grows after credit is applied) **skips the due-confirm and credit-limit dialogs** (`hasDueInPayment` is false). |
| M-5 | PARTIAL | Case 1: the sale receipt shows Paid 300 but the reprint shows `amountPaidToRecord` 400. Case 2: the ৳50 card over-tender is silently absorbed. Over-tender on a card or wallet should be blocked. |
| M-6 / N-1 | PARTIAL | The panel, receipt and VM all use `calculatePaymentBreakdown` (good). **The due-confirm and credit-limit step (Cart about 2580) still uses the raw Due row**, showing a new due of 200 while 100 is recorded. |
| M-10 | PARTIAL | Cash refund is correct. Wallet refund: the dialog shows +100 restored but the repo restores 300. Walk-in sale paid fully by voucher: the dialog promises a 300 restore, but the repo restores nothing and refunds 0. Walk-in "register phone" return of voucher100 + cash200 credits 400 for a 300 return. |
| M-9 | FIXED | The test only re-implements the if-statement. |
| M-3 | **REGRESSION** | N-5 nulls the JSON for sales paid fully by SC, and the split auto-SC row has no `voucherAmount`, so **sales paid fully by store credit lose all voucher tracking**. Example: voucher 100, sale A uses 70, sale B (80) = V30 + customer 50. Voiding B gives V+80 and the customer 0. Voiding walk-in sale A restores nothing. The `"VOUCHER"` phone fallback is still there (repo about 637, 4233). Expired vouchers are still restored. **Fix:** add `voucherCode`/`voucherAmount`/`customerCreditAmount` columns on Sale (plus a migration and backup), written at sale time for every SC sale. |
| N-2, N-3 | FIXED | — |
| N-4 | FIXED | The error message is generic English. |
| N-5 | FIXED | It caused the M-3 regression above. |
| M-14 | FIXED for non-owners | The owner can no longer clear a limit to 0 (0 is treated as "keep"). An owner `upsertCustomerDue` on a new due row writes 0 over the Customer limit. |
| B-7 | PARTIAL | Written at sale time, exported and imported, and used by the reports. **The migration defaults `isTaxInclusive=0` with no backfill, so an inclusive-tax shop's historical profit is now overstated.** Backfill from the current setting at migration time (do it in code after the migration, from SettingsDataStore). Old backups have the same problem. The Excel export uses `grandTotal - cost` (tax included). Invoice-profit refunds aren't tax-stripped. |
| X-8 | PARTIAL / REGRESSION | The migration demotes "Silver" below a hard-coded 3000, but the configured `silverSpendThreshold` lives in preferences. With it set to 2000, a customer at 2500 is wrongly demoted. Recompute the tier in code using the configured thresholds. |
| M-8 | FIXED | Mutex plus a version number. No test. |
| X-4 | PARTIAL | Purchases, returns, sales and new or existing products are now correct. Still wrong: (a) supplier payments are paired only by a note starting "Paid on Invoice", so a genuine payment typed with that text is skipped; the DAO guard `totalDue >= amount` (AppDaos about 451) can also skip one silently. (b) **Voided sales:** a void writes a "+qty Sale Cancelled" adjustment. A backup with a voided sale has no sale items but does have that adjustment, so the merge inflates stock. |
| B-5 | PARTIAL | The cash numbers reconcile: in a worked day, both Register Close and the Z-report give expected cash 2100, and the payment lines sum to net. Still wrong: (a) **the daily totals key due, supplier and expense rows by UTC day (`ts/86400000`, VM about 2234-2248) while sales use local days**, so at UTC+6 those rows don't line up *(verified)*; (b) `refunds` is added to revenue that is already net of refunds (about 2230), and `netCollections` (about 2458) subtracts refunds again; (c) legacy Due-with-partial-cash sales show the full amount as Due, because `amountPaid` isn't passed; (d) the Z/X dialog and the PDF/JPEG are hard-coded English (RegisterCloseScreen about 2699-2723, BillPdfGenerator about 1160-1440); (e) the X JPEG has no cash section; (f) the negative "Store Credit −200" line is confusing, so label it "Store credit refunds". |
| M-7 | PARTIAL | Loyalty Taka uses 1 point = ৳1 instead of `pointCashValue` (SalesHistory about 2165, Cart about 2818). The clawback shown is `floor(grandTotal/100)` (hard-coded, keyed on customerPhone, not loyaltyPhone), which doesn't match what the void actually does (repo about 553-560). The "Voucher (…)" label is hard-coded English. |

## New bugs and regressions
- [ ] **R-1 HIGH Imported product barcodes are no longer lowercased** (repo about 2665, both REPLACE and MERGE) *(verified)*. The barcode is the primary key, and the live
  update path lowercases before `@Update` (about 357), so edits to an imported "ABC-1" match no rows and are **silently lost**. Lowercase on import.
  Add a one-off migration or startup fix that lowercases existing uppercase barcodes, and their references in sale_items and others, if any exist.
- [ ] **R-2 HIGH Return can be double-submitted.** ReturnDialog (about 1373, 1417): `isConfirmEnabled` ignores in-flight processing *(verified)*, so a double tap
  refunds twice. Add `isProcessing`.
- [ ] **R-3 HIGH** AddEditProductDialog (about 1073, 1174, 1459) uses `isSystemInDarkTheme()`, which ignores the app's own theme setting. With the app forced Light on a dark phone,
  the profit, stock-health and date panels show light text on near-white, which is unreadable. About 1151: BE123C on the dark errorContainer is 1.59:1.
- [ ] **R-4** The due-confirm dialog and credit-limit pre-check use the raw split Due amount instead of `breakdown.dueAmount` (see M-6).
- [ ] **R-5** The sale receipt's "Paid" (`effectiveAmountPaid`) differs from the reprint (`amountPaidToRecord`, which includes auto SC).
- [ ] **R-6** 191971b side effects: `ExcelHelper` switched from SXSSF back to XSSF, which uses more memory on large exports (revert). `getProductByBarcode` now uses
  `LOWER()`, which defeats the primary-key index. Since barcodes are stored lowercase, lowercase the input instead.

## UI/UX
- [ ] **U-1** The main "Continue to Checkout" button in light mode is white on #10B981, 2.54:1 (Cart about 1074, 3399). Darken `primary` (e.g. #047857) or use dark text.
- [ ] **U-2** Deletes with no confirmation: held cart (AdvancedPosFeatures about 374), customer note (CustomerDossierSheet about 667, a 36dp button), reminder log
  (DuesScreen about 3440, 24dp). The note-delete success toast just says "Delete" (LoyaltyScreen about 426).
- [ ] **U-3** 45 IconButtons are under 48dp: DuesScreen 13 (e.g. about 591 at 20dp, 2652 at 26dp, 2962 at 24dp), AdvancedPosFeatures 6, ReturnDialog 5, Scanner 5.
- [ ] **U-4** Seven light "white islands" in dark mode (e.g. DuesScreen about 2271-2299, ReceiptSummary about 484, Scanner about 3557).

## Localisation
- [ ] **L-1** About 100 multi-word English literals in `ui` (find them with
  `grep -rnE '"[A-Z][a-z]+( [A-Za-z]+){1,}[.!?:]?"' ui | grep -vE 'Log\.|Exception|require|Preview|R\.string'`), for example
  ReturnDialog about 358 "1. SELECT RETURN QUANTITIES", LockScreen about 334, 398 (PIN setup), the DuesScreen Add Due form (about 4121-4623), RegisterClose about 767, 948, 1333.
  Plus the X/Z report generators, and 39 English repo exceptions shown via `e.localizedMessage` in about 30 toasts. Map them to typed errors with string resources.
- [ ] **L-2** Make the app language the single source: expose `LocalAppLanguage` (from the settings `languageFlow`), use it for formatTaka, formatMoney and
  DateFormatters in UI, and keep files, exports, PDFs-for-records, SMS and printer output on explicit locales.

## Tests (do these first; they gate the rest)
- [ ] **T-1** Migration tests for 21→36, at least 33→34, 34→35 and 35→36 with real data (taxable default, isTaxInclusive backfill, tier recompute).
- [ ] **T-2** Delete or retarget the tests that call the dead `calculateEffectiveAmountPaid`. Payment tests must go through `CartViewModel` (or the exact CartScreen →
  PaymentMath → VM inputs) and then the repo.
- [ ] **T-3** Merge test with shared history, returns, voids, mixed barcode case, and assertions on dues, points and credit.
- [ ] **T-4** Voucher tests: a voucher used across two sales, then void each; a walk-in fully voucher-paid sale, then void and return.

## Design work remaining
`Color(0x` 2494 (it **went up** by 89), `Color.White` 436, under 10sp 66, about 71 dialogs without `dismissOnClickOutside`, 61 `SimpleDateFormat(` (24 in ui), 41 raw "৳".
Untouched: StockCheckComponents (506), RegisterCloseScreen, ScannerScreen, SalesReportScreen (18 under 10sp), SettingsScreen (17 dialogs, 14 under 10sp), LoyaltyScreen, CustomersScreen.
**New code must use theme tokens.** Don't add `Color(0x…)`.

---

## Tasks, in order
1. R-1, R-2, R-3, M-3 (Sale voucher columns plus a migration), B-5a (UTC bucketing) and B-5b (double refund).
2. T-1 … T-4.
3. M-10, M-1/R-4, M-5/R-5, B-7 backfill, X-8 tier recompute, X-4a/b, M-14, M-7, R-6.
4. D-1 + L-2 (app-language source), then L-1, D-5, D-6, U-1 … U-4.
5. Design work, one screen per commit, with before/after counts.

## Rules
Same as before: at most 5 items per commit; tick only with the sha plus the test name; money tests go through the real production path and must fail first;
run the FULL `connectedDebugAndroidTest` suite and report the counts from the XML; never claim "no hard-coded English" without running the L-1 grep; don't touch
`*.apk`, `Backup Files/` or `.agents/`; don't rewrite history.
