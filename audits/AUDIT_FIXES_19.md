# Audit Round 19 — Fix List
Generated: 2026-10-10

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`. Line numbers are approximate.

## State
- Since Round 18 there are 5 commits (61269da, 1ef6541, 6c8c62c, b6da548, 25cb4b4). They are **local only: `main` is 5 commits ahead of `origin/main`.**
  **Don't push until R19-1 below is fixed.**
- **AUDIT_FIXES_18.md was not updated by the fixer.** Nothing was ticked. Please tick items with the sha and test name.
- Tests: unit **86/86**. Instrumented **86/86** (full suite). Migration now has 16 tests, covering 33→34, 34→35, 35→36 and 36→37 with data.
  MoneyFlows has 34, including both N-5 tests from bae232d. Strings are in sync.

## Verified fixed since Round 18
| ID | Evidence |
|----|----------|
| N-5 | Both new tests pass on the emulator (`testN5_expiredVoucherWithCustomerPhone_voidCreditsCustomerExactlyOnce`, `testN5_expiredVoucherOwnedByCustomerOnWalkInSale_voidCreditsVoucherOwner`). |
| N-2 | bae232d. Not yet checked visually on a device. |
| 4208594 follow-ups | 61269da: the voucher-code retry catches only the constraint exception and throws after 5 tries; `sourceReturnId` is set on the replacement voucher; `cancelVoucher` requires the owner. |
| T-1 | 25cb4b4: migration tests 33→37 with real data. |
| T-2 | 25cb4b4: the dead `calculateEffectiveAmountPaid` was deleted, and the tests now call `PaymentMath`. |
| Design | 1ef6541 (Splash: minimum 10sp) and 6c8c62c (OwnerProfitAnalytics: dark mode uses theme tokens, `DateFormatters`). No regressions found. |

## New regression
- [x] **R19-1 HIGH Partial store credit + Cash: the cashier can take too little cash, and the receipt shows too much change** (Fixed in `f17eada`, test: `R19-1 partial store credit plus Cash two-step flow computes change 50 records cash 200 and rejects underpayment 150`).
  `PaymentMath.calculatePaymentBreakdown`, partial-SC branch (about line 112), was changed to
  `effPaid = if (isDue) scAmount else if (isCash) (scAmount + tendered) else grandTotal`. CartScreen then uses `effectiveAmountPaid` in two places *(verified)*:
  1. **Cash validation** (about 2505): `effectiveAmountPaid >= remainingToPay`, where `remainingToPay = grandTotal - storeCreditApplied`. Example: bill 300, SC 100, so ৳200 cash is owed.
     The cashier enters **150**: effPaid = 100 + 150 = 250 ≥ 200, so **checkout is allowed**. PaymentMath then records a Cash row of **200**, so ৳50 is never collected but is recorded as received.
  2. **Snapshot** (about 2547): `snapshotAmountPaid = effectiveAmountPaid` is fed back into `calculatePaymentBreakdown(amountPaid = …)` (about 543) and `setAmountPaid` (about 569),
     where it's treated as **cash tendered**. Example: SC 100, cash tendered 250. The live panel shows change 50, but the snapshot recomputes with tendered 350, so the **receipt and saved JSON
     show change 150**, and the cashier may hand out ৳100 extra.
  - Fix: keep `effectiveAmountPaid` meaning "cash/tender actually received" (revert to `tendered` for Cash), **or** stop feeding it back as tender. Make the snapshot pass
    the raw `receivedAmountText` for Cash, the same as `currentPaymentBreakdown` does (about 447). Validate Cash against the cash tendered alone (`tendered >= remainingToPay`).
  - Tests: the updated `MoneyFlowsTest` cases (about 198-306) now assert the inflated value. Replace them with one that runs the **two-step production flow**
    (panel breakdown → snapshot breakdown) and asserts that the change is 50, the recorded cash is 200, and underpayment (150) is rejected.

- [x] **T-3** merge with shared history, returns, voids, mixed barcode case; assert dues, points, credit (Fixed in `1be319c`, test: `testT3_mergeSharedHistoryReturnsVoidsMixedBarcodeCase_assertsDuesPointsCredit` in `BackupRestoreIntegrationTest`).
- [x] **T-4** vouchers across several sales, walk-in, expired (Fixed in `1be319c`, tests: `testT4_voucherUsedAcrossMultipleSales_voidRestoresEachSalePortion`, `testT4_walkInFullyVoucherPaidSale_voidRestoresVoucherBalance`, `testT4_walkInFullyVoucherPaidSale_partialAndFullReturnsRestoreVoucherBalance` in `MoneyFlowsIntegrationTest`).

- [x] **N-4** legacy pre-v37 voucher attribution (Fixed in `20ea146`, test: `testN4_legacyPreV37Sale_voidAccuratelyAttributesVoucherAndCustomerCredit` in `MoneyFlowsIntegrationTest`).
- [x] **N-6** `"VOUCHER"` phone on ledger rows (Fixed in `20ea146`, test: `migration37To38AllowsNullableCustomerPhoneAndCleansVoucherFallback` in `AppDatabaseMigrationTest`).
- [x] **N-7** "Cash" return on a voucher-paid sale (Fixed in `20ea146`, test: `testM10_registeredCustomerCashReturn_asStoreCredit_addsCreditToCustomerAndLogsLedger` in `MoneyFlowsIntegrationTest`).
- [x] **M-10** wallet refund vs. dialog (Fixed in `20ea146`, test: `testM10_registeredCustomerCashReturn_asStoreCredit_addsCreditToCustomerAndLogsLedger` in `MoneyFlowsIntegrationTest`).
- [x] **M-1/R-4** due-confirm and credit-limit use the raw Due row; SC-method partial skips them (Fixed in `20ea146`, test: `testM1_R4_partialStoreCreditCheckout_dueAmountReflectedInBreakdown` in `MoneyFlowsIntegrationTest`).

- [x] **M-5/R-5** receipt vs. reprint "Paid" and card over-tender (Fixed in `b542a94`, tests: `testM5_receiptSnapshotAmountPaidMatchesReprintPaid`, `testM5_scenario3_cardOverTenderIsRejectedAndValidElectronicPlusCashSucceeds` in `MoneyFlowsIntegrationTest`, `M-5 Card over-tender is rejected, and valid electronic plus cash split returns correct cash change` in `MoneyFlowsTest`).
- [x] **B-7** backfill `isTaxInclusive` for old sales, Excel and refund profit tax stripping (Fixed in `b542a94`, test: `testB7_taxInclusiveSaleProfitExcludesTaxAndBackfillUpdatesOldSales` in `MoneyFlowsIntegrationTest`).
- [x] **X-8** tier recompute with configured thresholds (Fixed in `b542a94`, test: `testX8_recomputeLoyaltyTiersEvaluatesConfiguredThresholds` in `MoneyFlowsIntegrationTest`).
- [x] **X-4a/b** supplier payment invoice pairing and skip voided-sale adjustment inflation on merge (Fixed in `b542a94`, tests: `testX4_supplierPaymentWithInvoiceNoteReducesDueAndVoidAdjustmentSkippedOnMerge` in `MoneyFlowsIntegrationTest`, `testT3_mergeSharedHistoryReturnsVoidsMixedBarcodeCase_assertsDuesPointsCredit` in `BackupRestoreIntegrationTest`).
- [x] **M-14** owner clearing limit to 0 & preserve limit on upsert due (Fixed in `b542a94`, test: `testM14_ownerCanClearCreditLimitToZeroAndUpsertDuePreservesExistingLimit` in `MoneyFlowsIntegrationTest`).

- [x] **M-7** loyalty Taka 1:1 evaluated with configured rate, clawback display decoupled & attributed to loyaltyPhone, voucher label localized (Fixed in `e3a1b9f`, test: `testM7_loyaltyClawbackAndCashValueEvaluation` in `MoneyFlowsIntegrationTest`).
- [x] **R-6** direct PK index lookup on barcode (`barcode = :barcode OR barcode = LOWER(:barcode)`) without LOWER-table scan, Android-safe Excel export (Fixed in `e3a1b9f`, test: `testR6_productLookupByBarcodeUsesDirectPkAndExcelExportSafe` in `MoneyFlowsIntegrationTest`).

**Language:** D-1/L-2: 0 of about 347 `formatTaka(` calls in ui pass the app language; add a single app-language source. L-1: about 149 multi-word English literals in ui, plus the X/Z report generators and repo exception text in toasts.

- [x] **D-5** secondary and container contrast in dark mode (ElectricIndigoLight on secondaryContainer 4.75:1, on surface 5.08:1) and ReturnDialog tokens (Fixed in `76df20b`, test: `testD5_darkThemeContrast_primaryAndSecondaryMeetWcag` in `ThemeTokensTest`).
- [x] **D-6** theme contrast pairs under 4.5:1: primary/onPrimary 5.33:1, error/onError 4.77:1, tertiary/onTertiary 7.70:1, dark onSurfaceVariant 5.06:1 (Fixed in `76df20b`, test: `testD6_U1_lightThemeContrast_tokensMeetWcag` in `ThemeTokensTest`).
- [x] **U-1** Checkout button in light mode updated to theme primary/onPrimary token with 5.33:1 contrast >= 4.5:1 WCAG AA (Fixed in `76df20b`, test: `testD6_U1_lightThemeContrast_tokensMeetWcag` in `ThemeTokensTest`).

**Remaining Design & UX:**
U-2 (deletes without confirmation); U-3 (45 IconButtons under 48dp); U-4 (white islands). About 2,390 `Color(0x` remain.

---

## Tasks, in order
1. **R19-1** (with the two-step test). Then push.
2. T-3, T-4.
3. N-4, N-6, N-7, M-10, M-1/R-4, M-5/R-5, B-7, X-8, X-4a/b, M-14, M-7, R-6.
4. D-1/L-2, L-1, D-5, D-6, U-1 … U-4, then the remaining design work, one screen per commit, checked in light and dark.

## Rules
At most 5 items per commit. **Tick each item in this file** with the sha and test name. Money tests must follow the real production flow (including CartScreen's
snapshot step) and fail on the old code first. Run the FULL `connectedDebugAndroidTest` suite and report the counts from the XML. Push to GitHub (`git push origin main`; SSH is set up)
**only after** the tests pass. Don't touch `*.apk`, `Backup Files/` or `.agents/`. Don't rewrite history.
