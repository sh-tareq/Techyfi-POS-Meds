# Audit Round 8 — Fix List
Generated: 2026-09-29

## Verification of AUDIT_FIXES_7.md (16 items)

N-1 to N-16 were checked against the code. All are PASS except N-10, which was only half done
(see N-1 below). N-17 was a duplicate of N-15 and is PASS.

## Changes already applied during the Round 7 check

These edits are uncompiled. No JDK was available, so the agent must build and verify them.

1. `SaleItem.taxable` is now persisted end to end.
   - `DataRepository.completeSaleWithNotes` has a new param `nonTaxableBarcodes: Set<String> = emptySet()`,
     used when building `SaleItem` (`taxable = product.barcode !in nonTaxableBarcodes`).
   - `CartViewModel` (POSViewModels.kt, near line 1281) passes the barcodes of non-taxable cart items.
   - Backup export writes `taxable` and import reads it with default `true`
     (DataRepository.kt, near the `purchasePriceAtSale` put and the `buildBackwardCompatibleSaleItem` call).
   - `buildBackwardCompatibleSaleItem` in BackupCompatibility.kt takes `taxable: Boolean = true`.
   - The SalesHistoryScreen reprint (near line 1911) passes `taxable = item.taxable` into `BillLineItem`.
2. Touch targets raised to 48dp across `ui/screens`. Every `.height(30..47.dp)` is now `.height(48.dp)`
   in ProductsScreen, DuesScreen, ScannerScreen, ExpensesScreen, RegisterCloseScreen, ReturnDialog,
   SettingsScreen and StoreCreditVoucherSlipDialog. ReturnDialog qty stepper boxes (about lines 500-570)
   changed from 30dp to 48dp.

## Tasks

### HIGH
- [x] **N-1** Build and verify the changes above.
  - Run `./gradlew :app:compileDebugKotlin` and then `:app:testDebugUnitTest`. Fix any compile errors.
  - Add a test: complete a sale with a non-taxable custom item, then confirm `sale_items.taxable == 0`.
    Export a JSON backup, restore it, and confirm `taxable` survives. Confirm an old backup with no
    `taxable` key restores as `true`.
  - Confirm the reprint receipt from Sales History now excludes non-taxable items from the tax base
    (check how `BillPdfGenerator` uses `BillLineItem.taxable`).

### MEDIUM
- [x] **N-2** Visually check the layouts that got taller controls.
  - Screens: DuesScreen (weighted 4-button rows, about lines 2040-2470, 3930-3980, 4975-5110),
    ScannerScreen toolbar (about lines 1530-1700), ReturnDialog stepper row.
  - Look for text clipped or rows overflowing on a small phone (360dp wide).
  - If a row is too tight, use `defaultMinSize(minHeight = 48.dp)` plus smaller vertical `contentPadding`.
    Do not go back below 48dp.
- [x] **N-3** Hard-coded English "All" chip text in `ui/screens/ReturnDialog.kt`, about line 580.
  - Add a string resource in `values/strings.xml` and `values-bn/strings.xml`
    (Bangla: সব), then use `stringResource`.
  - Grep ReturnDialog.kt for other bare literals, such as "−" (fine) or English words in `Text("...")`.

### LOW
- [x] **N-4** Remaining `.size(30.dp)` tappable icon boxes: ReturnDialog.kt lines 313 and 1668,
  DuesScreen.kt lines 3190 and 4101. Check whether each is tappable. If it is, make it 48dp,
  or keep the icon at 30dp and wrap it in a 48dp clickable Box.
- [x] **N-5** CartScreen payment chip label `else -> method` (about line 1650) falls back to raw
  English for unknown methods. Add explicit cases for any payment method that is added in future,
  or map the fallback through a string resource.

## Out of scope. Do not touch:
The working tree has about 236 unrelated modified or deleted files (APKs, `.agents/`, schemas, tests).
Do not revert or commit them. Stage only the files you change.
