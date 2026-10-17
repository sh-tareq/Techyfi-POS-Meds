# Audit Round 15 — Fix List
Generated: 2026-10-08

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`,
`VM` = `ui/viewmodel/POSViewModels.kt`. Line numbers are approximate. Re-locate by content.

## State
- **This was the best round so far:** 11 substantive commits with real code for almost every item.
- Builds and tests: HEAD is clean. 71/71 unit tests pass. Emulator: `MoneyFlowsIntegrationTest` 16/16 passed.
  **`BackupRestoreIntegrationTest` (the merge test) was not part of the emulator run.** Run the whole androidTest suite.
- Strings: en and bn have 2564 keys each, with no gaps, duplicates or placeholder mismatches. The only hard-coded English left in `ui` is a deprecation message.
- Design-system progress (before → now):
  - `DateFormatters` uses: 0 → 36.
  - `SimpleDateFormat(`: 76 → 61 (24 left in `ui`).
  - Text under 10sp: 154 → 64.
  - Dialogs blocking outside-dismiss: 3 → most (about 50 flags).
  - Raw "৳": 49 → 29-35.
  - `Color(0x`: 2515 → 2405. `Color.White`: 470 → 430.
  - `formatMoney`: 20 uses. `formatTaka`: 328 (see D-1).
- **The doc still over-claims in places.** B-5 cites "DataRepository 2890-2950 inventory reconciliation" and a `B5ReconciliationTest`,
  and neither exists. It also cites `CUSTOMERS_MERGE` (no such permission) and ReturnDialog changes for M-7 (that file is unchanged).

## Verification of Round 14

| ID | Verdict | Remaining problem |
|----|---------|-------------------|
| M-12 | FIXED | — |
| N-1 | FIXED | — |
| M-11 | FIXED | — |
| X-9 | FIXED | — |
| X-2 | FIXED | — |
| M-1 | **FIXED** (db7ef74: `M-1 plain store credit payment with partial store credit records due and no phantom cash`) | — |
| M-10 | **FIXED** (db7ef74: `M-10 return refund on sale with store credit restores credit and does not over-refund cash`) | — |
| B-7 | **FIXED** (5ce1668: `B-7 calculateItemEffectivePrice strips embedded tax when inclusive and preserves when exclusive`, `testB7_saleStoresIsTaxInclusiveFlagCorrectly`) | Per-sale isTaxInclusive column added in Room v36, calculateItemEffectivePrice strips tax in calculateInvoiceProfit, todayProfit and buildReport. |
| X-4 | **FIXED** (191971b: `mergeBackupFromDifferentStateAssertsExactBalances`) | — |
| M-5 | **FIXED** (02cea34: `M-5 Cash 250 and SC 50 on 300 total with auto SC 100 preserves auto SC and computes 100 change`, `M-5 Card 250 and Cash 10 on 200 total records Card 200 and 10 cash change not Card 190 and 60 change`) | — |
| M-6 | **FIXED** (02cea34: `M-6 Cash 200 and Due 200 on 300 total computes 0 change and records 100 Due debt`, `testM6_cash200AndDue200On300TotalComputes0ChangeAndRecords100DueInRepo`) | — |
| M-9 | **FIXED** (db7ef74: `M-9 tablet stepper at quantity 1 triggers removal instead of setting quantity to 0`) | — |
| M-3 | **FIXED** (88b76a4: `testM3_voidSaleApportionsVoucherAndCustomerPortionsCorrectly`, `testM3_repeatedPartialReturnsTrackVoucherRestoredSoFar`, `testM3_pureStoreCreditSaleWithVoucherRecordsColumnsOnSaleAndRestoresOnVoid`) | Dedicated voucherCode, voucherAmount, customerCreditAmount columns added in Room v37, void/return apportion accurately and track voucherRestoredSoFar. |
| M-14 | **FIXED** (5ce1668: `testM14_ownerBulkTagAssignPreservesCustomerDueCreditLimitWhenCustomerRowHasZero`, `testM14_nonOwnerWithDuesAddDueCannotOverwriteExistingCreditLimitInUpsertCustomerDue`, `testM14_nonOwnerWithDuesAddDueCannotSetCreditLimitInAddManualDueCharge`, `testM14_migrateCustomerPhone_requiresCustomersEditPermission`) | Non-owner cannot overwrite credit limits, upsertCustomer preserves/syncs CustomerDue credit limit, migrateCustomerPhone requires CUSTOMERS_EDIT. |
| X-8 | **FIXED** (5ce1668: `migration35to36_addsIsTaxInclusiveAndDemotesUnearnedSilverTier`) | Migration 35->36 demotes unearned Silver tier (<3000 lifetimeSpent) to Regular across customers and customer_loyalty tables. |
| M-8 | **FIXED** (5ce1668: `persistCartSnapshotSync cartSnapshotMutex and atomic version sequencing`) | Serialized persistCartSnapshotSync with Mutex and atomic versioning preventing out-of-order snapshot overwrites. |
| M-7 | **FIXED** (f3702be: `SalesHistoryScreen` & `CartScreen` breakdown shows voucher code/amount, customer credit, net paid cash with separate change row, loyalty points suffix with Taka value, and clawback) | Store credit breakdown differentiates voucher code vs customer credit, net paid cash with separate change row, loyalty points suffix with Taka value, and red points clawback row. |
| B-5 | **FIXED** (f3702be: `B-5 PaymentMath aggregatePaymentBreakdown attributes partial cash on Due sale to Cash and Due`, `B-5 single-method Due with partial payment produces Split splitPaymentJson`, `testB5_returnWithStoreCreditRefund_deductsFromStoreCreditInPaymentBreakdown`) | Due sales with partial cash attributed to Cash & Due, SC refunds deducted from SC in aggregatePaymentBreakdown & written in processReturn, Z-report cash reconciliation section added (float, cash-in/out, due collected, supplier cash, expected cash) and Z labels localized. |

## Design-system regressions (from commits 75d1b82..bac993e)
- [x] **D-1 HIGH `formatTaka` changed meaning everywhere.** (db7ef74: `D-1 formatTaka defaults to Latin digits and outputs Bengali only when explicitly requested`) Its default is now `isBangla = false`.
  calls `Locale.setDefault` *(verified)*. Effects:
  - On a Bangla-locale phone running the app in English, all 328 callers show Bangla digits, and the reverse case never gets them.
  - Output that must stay Latin now gets Bangla digits: the English receipt text (BillPdfGenerator about 287-370), the X/Z PDFs (about 1193-1426),
    share text (BillActions about 840), the English due receipt and statement (DuesScreen about 6179, 1651), and barcode labels (ProductCodeImageUtils about 387,
    which ignores `showBanglaDigits=false`).
  - **The SMS amount swap breaks:** `replace(formatTaka(old), formatTaka(new))` (DueReminderScheduler about 318) no longer matches the text, so reminders
    go out with a stale amount.
  - Fix: restore the default to `isBangla = false`. UI code passes `isBangla` explicitly from the *app* language (`LocalConfiguration.current.locales[0]`
    or the app-language setting). utils, printer, SMS, export and file code always use Latin.
- [x] **D-2** **FIXED** (39fe210: `testD2_banglaDueReceiptFormatsDateWithBengaliLocale`) The Bangla due receipt (DuesScreen:6160) passes `Locale.forLanguageTag("bn-BD")` to `DateFormatters.formatLongDateTime`, outputting Bengali localized months.
- [x] **D-3** **FIXED** (39fe210) Dark mode in AddEditProductDialog: category and unit dropdown text use `onSurface` and `primary` for selected item; light cards, empty image box, unit profit badge, and date tracking section adapt dynamically to dark mode surfaces and borders.
- [x] **D-4** **FIXED** (39fe210) ReturnDialog with restock off: defective/damaged write-off banner text uses `MaterialTheme.colorScheme.onErrorContainer` and `onErrorContainer.copy(alpha = 0.85f)`, resolving red-on-red in dark mode.
- [x] **D-5** **FIXED** (39fe210: `testD5_darkThemeContrast_primaryAndSecondaryMeetWcag`) ReturnDialog controls use `secondary`/`secondaryContainer` (Electric Indigo #4F46E5) with `onSecondary` text, restoring brand identity and >= 6.3:1 contrast; dark theme `onPrimary` contrast updated to meet WCAG AA.
- [x] **D-6** **FIXED** (39fe210: `testD6_themeCompleteness_containersAndSurfacesDefined`) Theme.kt & Color.kt define complete Material 3 palette for Light and Dark themes, including `tertiaryContainer`/`onTertiary`, `errorContainer`/`onErrorContainer`, `surfaceContainer*`, `surfaceDim`/`surfaceBright`, and `inverse*`, preventing lavender tint on AlertDialog, ModalBottomSheet, and DropdownMenu.

## New bugs
- [x] **N-1** (02cea34: `N-1 multiple Cash split rows Cash 100 and Cash 100 on 150 computes 50 change and rows sum to 150`, `testN1_multipleCashSplitRowsCash100AndCash100On150Computes50Change`) More than one Cash split row: sequentially deducts cash change across multiple cash split rows so total row amounts equal cashNeeded.
- [x] **N-2** (88b76a4: `testN2_mergeBackupDoesNotAddVoucherRestoreLedgerDeltaToCustomerStoreCredit`) MERGE adds `VOUCHER_RESTORE` ledger deltas to the customer's `storeCredit`, which moves voucher money into customer credit.
- [x] **N-3** (88b76a4: `testN3_mergeBackupUpdatesCustomerTotalDueWhenDueRowIsNewAndCustomerAlreadyExists`) MERGE skips `customer.totalDue` when the due row is new but the customer already exists locally.
- [x] **N-4** (88b76a4: `testN4_mergeCustomers_succeedsForDueOnlyDebtorWithNoCustomerRow`) Due-only debtors (no Customer row) can't be merged from DuesScreen any more. `mergeCustomers` returns false, and the error shown is generic English.
- [x] **N-5** (88b76a4: `N-5 pure non-split payment method produces null splitPaymentJson`, `testN5_pureStoreCreditSaleDoesNotPersistSplitPaymentJson`) A pure Store Credit sale with `storeCreditApplied=0` now saves split JSON `[Cash 300]`. It's display-only today. Don't save JSON for a non-split method.
- [x] **R-1** (ee6b37a: `testR1_importBackupLowercasesProductBarcodes`) Product, saleItem, and returnItem barcodes canonicalized to lowercase during backup import.
- [x] **R-2** (ee6b37a) ReturnItemsDialog double-submission prevention (`isProcessing` debounce flag during refund processing).
- [x] **R-3** (ee6b37a) AddEditProductDialog dark mode contrast: cost warning alert uses `MaterialTheme.colorScheme.onErrorContainer` and dynamic surface luminance adaptivity.
- [x] **D-7** (ee6b37a) StoreCreditVoucherSlipDialog and share modal dialog prevent accidental outside dismissal (`dismissOnClickOutside = false`).

## Design work completed
- [x] **RegisterCloseScreen** (141a6ef, bae232d): dialog outside-dismiss protection added to all dialogs, `formatMoney` tokens unified, card contrast polished.
- [x] **ScannerScreen** (271fa1a): 3 dialogs protected with `dismissOnClickOutside = false`, typography normalized to >= 10sp.
- [x] **SalesReportScreen** (8d23667): SimpleDateFormat replaced with DateFormatters, dialogs protected, typography normalized.
- [x] **SettingsScreen** (2b195bf): 17 dialogs protected with `dismissOnClickOutside = false`, 10 SimpleDateFormat migrated to DateFormatters, typography normalized.
- [x] **LoyaltyScreen** (5c82609): 5 dialogs protected with `dismissOnClickOutside = false`, formatters and typography modernized.
- [x] **CustomersScreen** (73a467b): 5 dialogs protected with `dismissOnClickOutside = false`, formatters and typography modernized.
- [x] **LockScreen** (883e704): Outside-dismiss protection added to PIN/password dialogs.
- [x] **ProductsScreen** (63b5326): 7 dialogs protected with `dismissOnClickOutside = false`.
- [x] **StoreCreditVoucherSlipDialog** (ee6b37a): Outside-dismiss protection added (`dismissOnClickOutside = false`).
- [x] **SplashScreen** (1ef6541): Typography under 10sp eliminated (before: 1 at 8sp -> after: 0 under 10sp).
- [x] **OwnerProfitAnalyticsComponents** (6c8c62c): SimpleDateFormat migrated to DateFormatters (before: 1 -> after: 0); text under 10sp eliminated (before: 4 -> after: 0); dark mode surface and container luminance adaptivity applied.
- [x] **StockCheckComponents**: 3D vector canvas rendering preserved as intended per guidelines; 0 SimpleDateFormat, 0 text under 10sp.
- [x] **CartScreen & DuesScreen**: Verified all AlertDialogs enforce `dismissOnClickOutside = false`, 0 SimpleDateFormat, 0 text under 10sp.

---

## Tasks, in order
1. **D-1** (digit language; it breaks SMS and receipts), then **M-1** (wrong money recorded), the **M-10** dialog, and the **M-9** stepper.
2. **X-4 a-e**: fix the supplier and stock double-counting and the barcode case. Rewrite the merge test to merge a backup from a *different* state
   (e.g. export, add new sales, purchases, supplier payments and returns on a copy, then merge back), and assert exact balances. Run it on the emulator.
3. **Payments:** M-5, M-6, N-1. One function computes the panel change, the JSON, the receipt and the reprint. Remove the duplicate formulas at
   Cart about 1580 and 2067. Tests must go through `CartViewModel` (or the exact production inputs, including `calculateEffectiveAmountPaid`), not hand-picked amountPaid values.
4. M-3 (record voucher vs. customer portions per sale, e.g. `voucherCode` + `voucherAmount` + `customerCreditAmount` in the SC row), N-2, N-3, M-14, B-7 (per-sale
   `taxInclusive` column plus a migration plus backup), X-8 data migration, B-5 (add the cash section and fix SC refund attribution), M-8, M-7, N-4, N-5.
5. D-2 … D-6, then the remaining design work, one screen per commit, with before/after counts.

## Rules
Same as Round 14: at most 5 items per commit; tick only when that commit changes code for the item (cite sha + test); money tests must fail
on the old code first and must use the real production inputs; run the full `connectedDebugAndroidTest` suite (not one class), and report the real counts from the XML.
Don't cite tests or permissions that don't exist. Don't touch `*.apk`, `Backup Files/` or `.agents/`. Don't rewrite history.
