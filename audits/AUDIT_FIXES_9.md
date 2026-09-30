# Audit Round 9 — Fix List
Generated: 2026-09-30

## Verification of AUDIT_FIXES_8.md (5 items)

| ID | Description | Result |
|----|-------------|--------|
| N-1 | Build and verify the taxable persistence and 48dp changes | PASS, with a test gap (see N-3 below). `testDebugUnitTest` ran 7/7 green. The run is newer than every main source file. Reprints use the stored `sale.taxAmount`, so an unused `BillLineItem.taxable` does not affect the receipt. |
| N-2 | Taller controls do not overflow | PASS. The code was reviewed but not tested on a device. Check on a 360dp phone. |
| N-3 | ReturnDialog "All" chip localized | PARTIAL. "All" now uses `R.string.common_all`, but the second bullet ("grep ReturnDialog.kt for other bare literals") was not done. 7 English literals remain in that file. |
| N-4 | 30dp tappable icon boxes | PASS. The IconButtons are 48dp. The remaining 30dp boxes are decorative. |
| N-5 | Payment chip fallback | PASS. Unknown methods fall back to `method.ifBlank { cash }`, which is acceptable. |

---

## New Issues — Round 9

### HIGH

- [x] **N-1** About 170 hardcoded English UI strings. Bengali users see English on these screens.
  - Find them with (run from `app/src/main/java/com/example/techyfipos/ui`):
    ```
    grep -nE 'Text\("[A-Za-z][^"]{2,}"|contentDescription = "[A-Za-z][^"]*"|label = \{ Text\("[A-Z]' screens/*.kt dialogs/*.kt
    ```
  - Counts per file: RegisterCloseScreen 42, SettingsScreen 35, DuesScreen 33, ScannerScreen 14,
    CustomItemDialogs 11, ReturnDialog 8, AddEditProductDialog 5, StoreCreditVoucherSlipDialog 4,
    LockScreen 3, ProductCodePrintDialogs 3, CartScreen 2, Product3DIcons 2, SplashScreen 1,
    CustomerDossierSheet 1.
  - Examples: RegisterClose "Amount exceeds current drawer cash balance!", "Please enter a valid amount";
    Scanner "Current Sale", "Out Of Stock", "LAST SCANNED", "Undo", "Review Cart";
    ReturnDialog "Select Payment Gateway:", "Gross Item Total", "Due Debt Cleared", "Set Quantity",
    "Cancel", "Manage Audit Presets", "Quantity (Max: …)"; Dues "Cannot delete due ledger for …";
    many `contentDescription = "Close" / "Clear" / "Remove"`.
  - Fix: add each string to `res/values/strings.xml` and `res/values-bn/strings.xml`, then use
    `stringResource(...)` (or `context.getString` outside composables). Reuse existing keys first
    (`common_all`, `cancel`, `close`, etc.; grep strings.xml before adding). Follow
    `.agents/skills/bangla-language` for Bangla wording and the glossary.
  - Leave these alone: brand/proper names ("WhatsApp", "SMS", "bKash"), URL placeholders like
    `https://yourdomain.com/invoice`, `@Preview` and sample data such as the SettingsScreen demo bill
    items, and log/exception messages that users never see.
  - Keep format args as `%1$s` / `%1$d` placeholders. Do not concatenate strings.

### LOW

- [x] **N-2** Check the 48dp controls on a device: DuesScreen 4-button weighted rows, the ScannerScreen
  toolbar, and the ReturnDialog stepper on a 360dp phone, in both English and Bangla (Bangla text is
  wider). If a label clips, reduce the font or use `contentPadding`. Do not shrink the height below 48dp.

- [x] **N-3** The taxable test only exercises `buildBackwardCompatibleSaleItem`. Add a real
  round-trip test in `androidTest/.../BackupRestoreIntegrationTest.kt`: call `completeSaleWithNotes`
  with `nonTaxableBarcodes = setOf("CUSTOM-X")`, assert the stored `SaleItem.taxable == false`,
  export JSON, restore, and assert it is still false.

## Out of scope. Do not touch:
The working tree has many unrelated modified or deleted files (APKs, `.agents/`, schemas, tests).
Do not revert or commit them. Stage only the files you change.
