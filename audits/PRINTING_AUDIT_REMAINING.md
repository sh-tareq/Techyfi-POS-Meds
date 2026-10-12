# Printing System Audit: Remaining Work

Scope: receipts (ESC/POS over Bluetooth, Android print service), labels (TSPL over Bluetooth, PDF via print service), cash drawer kick, Z/X reports, Settings printer UI.

Key files (all under `app/src/main/java/com/example/techyfipos/`):
- `printer/BluetoothPrinterTransport.kt`, `printer/EscPosPrinterDriver.kt`, `printer/TsplLabelPrinterDriver.kt`
- `utils/BillActions.kt` (routing: `printReceiptImage`, `printLabelImages`, `printBillJpeg`, `printPdfDocument`, `triggerCashDrawerKick`)
- `utils/BillPdfGenerator.kt` (receipt, Z/X report, due repayment, voucher generators)
- `ui/screens/SettingsScreen.kt` (printer cards, picker dialog, cash drawer section)
- `ui/dialogs/ProductCodePrintDialogs.kt` (single and batch label printing)
- `ui/dialogs/ReceiptSummaryDialog.kt`, `ui/screens/DuesScreen.kt`, `ui/screens/RegisterCloseScreen.kt`, `ui/screens/StoreCreditVoucherSlipDialog.kt` (callers)
- Tests: `app/src/test/java/com/example/techyfipos/printer/PrinterRoutingAndDriverTest.kt`

Already fixed in commit on branch `claude/modest-bardeen-ak8pl3` (do not redo): batch label direct-print routing and same-printer guard, drawer kick address fallback in `CartScreen.kt`, TSPL/ESC-POS input clamping, label gap field retyping bug, test label spinner, 58mm media size name.

Note: no Android SDK was available when this was written, so none of the earlier fixes were compiled. Build and run unit tests first (`./gradlew :app:testDebugUnitTest`) and fix any compile errors in those edits before starting.

Style rules: never use the em dash character in code, comments or strings. Add every new user-facing string to BOTH `res/values/strings.xml` and `res/values-bn/strings.xml`. Match surrounding code style.

---

## P1: Z and X reports ignore the 58mm roll setting

- **Issue:** `generateZReportJpeg` and `generateXReportJpeg` in `BillPdfGenerator.kt` hard-code `width = 576`, 42-character divider strings, and `generateZReportPdf`/`generateXReportPdf` hard-code an 80mm PDF page. On a 58mm printer the ESC/POS driver scales 576 down to 384 dots, shrinking text by a third. Invoices, due receipts and vouchers already use `if (is58) 384 else 576`.
- **Fix:**
  1. Add a `settings: InvoiceSettingsData` (or `paperRollWidthMm: Int`) parameter to the four Z/X functions; pass `invoiceSettings` from the callers in `RegisterCloseScreen.kt` (lines near 1221, 1238, 1284, 1301, 3125).
  2. Derive `width` (384 or 576), divider length (about 32 vs 42 chars) and font sizes/column positions from it. Follow how `generateBillJpeg` handles `is58`.
  3. Use `paperRollWidthMm` for the PDF page width instead of the literal `80f`.
  4. Verify by rendering both widths (emulator, or a unit/instrumented test that checks `bitmap.width`).

## P1: Z and X reports hard-code the brand name

- **Issue:** Both reports draw `"TECHYFI POS"` and a footer `"Techyfi POS • Smart Store System"` instead of the shop name used by invoices (`bill.shopName.ifBlank { settings.storeName }`).
- **Fix:** Pass the store name (from `InvoiceSettingsData.storeName`) into the report functions and draw it in the header. Keep the "Powered by" style footer consistent with the invoice (`settings.poweredByText`, respecting `showPoweredBy`). Fold this into the same signature change as the item above.

## P1: Print results are ignored, so the user gets no feedback

- **Issue:**
  - `printReceiptImage` returns `true` in SYSTEM mode and in fallbacks even if `printBillJpeg` failed, because `printBillJpeg` returns `Unit`.
  - Callers (`ReceiptSummaryDialog.kt` around line 722, `DuesScreen.kt` around 6819, `RegisterCloseScreen.kt`, the Settings test receipt, `StoreCreditVoucherSlipDialog.kt`) ignore the Boolean and show nothing on success. Only the test label shows a result.
  - The Settings "Test Receipt" button has no busy indicator.
- **Fix:**
  1. Make `printBillJpeg` return `Boolean` (true only when `printManager.print` was handed the job) and have `printReceiptImage` return that value.
  2. Add a short success toast (new string, e.g. "Sent to printer") at the callers, using the Boolean result. Failure toasts for direct Bluetooth already exist via `showDirectPrintToast`.
  3. Add an `isTestingReceipt` state and spinner to the Settings test receipt button, mirroring the test label button.
  4. In `printBillJpeg`, decode the bitmap off the main thread and avoid leaking it (recycle in the adapter's `onFinish`).

## P2: Batch label printing reconnects Bluetooth for every product

- **Issue:** `TsplLabelPrinterDriver.printLabel` opens a fresh RFCOMM socket per call (about 250 ms settle plus 300 ms drain plus connect). `printLabelImages` and the batch loop in `ProductCodePrintDialogs.kt` call it once per label, so a 100-product batch is very slow.
- **Fix:** Add a driver function that takes `List<LabelJob(bitmap, copies)>`, builds one TSPL payload per job (use `buildTsplPayload`), and sends them all as segments in a single `sendToBluetoothPrinter(context, address, segments, perSegmentDelaysMs)` call. Use it from both `printLabelImages` and the batch dialog. Report how many labels were sent if a mid-batch write fails.

## P2: Auto mode can print a batch twice

- **Issue:** In `printLabelImages`, if the direct loop fails after some labels printed, `SHOW_ERROR_THEN_SYSTEM_DIALOG` opens the PDF fallback containing every label, so already printed labels print again.
- **Fix:** Track how many labels were sent. If at least one succeeded, do not fall back; show the error with the count. Only fall back to the PDF when zero labels were sent. Do the same check for receipts if relevant.

## P2: Bluetooth transport robustness

- **Issue:** `sendToBluetoothPrinter` makes one connect attempt per socket type with no retry. A missing `BLUETOOTH_CONNECT` permission makes `showDirectPrintToast` jump straight to app settings instead of prompting.
- **Fix:**
  1. Retry the whole connect sequence once (about 500 ms apart) before returning `Failed`.
  2. For `NoPermission`, request the runtime permission through the activity result API where a composable is available (the picker dialog already does this), and only deep-link to settings after a permanent denial.

## P2: Printer picker and printer cards

- **Issue:**
  - Receipt and label cards in `SettingsScreen.kt` show only the raw MAC address, not the device name.
  - There is no way to clear a receipt or label printer once chosen (`BluetoothPrinterPickerDialog` only offers "same as receipt" for the drawer).
  - The label picker allows selecting the receipt printer, warning only afterwards.
  - Address comparison in the picker uses case-sensitive `==`.
- **Fix:**
  1. Resolve the bonded device name from the address (guard `SecurityException`) and show `name (address)`.
  2. Add a "None / not set" row to the picker when `showSameAsReceiptOption` is false and wire it to set the address to `""`.
  3. In the label picker, disable or hide the receipt printer device (or block selection with the existing `settings_label_printer_same_as_receipt_warning` text).
  4. Use `equals(ignoreCase = true)` for the selected check.

## P3: Label print quality

- **Issue:**
  - `tsplBitmapBytes` uses a fixed luminance cutoff of 170, so the label darkness slider only changes printer density; the receipt side maps darkness to a threshold.
  - `buildTsplPayload` scales to the target size without checking aspect ratio, which stretches labels with unusual sizes.
  - The Settings test label is always 40x30 mm regardless of the configured label size.
- **Fix:** Optionally derive the threshold from `labelDarkness` (reuse `EscPosPrinterDriver.receiptLuminanceThreshold` or a similar pure function with a unit test). Scale with aspect-preserving fit and center, or log and reject mismatched aspect ratios. Let the test label use the label size configured in the label settings.

## P3: Dead setting

- **Issue:** `InvoiceSettingsData.directBluetoothPrintEnabled` is serialized but never read; `receiptPrintMode` replaced it.
- **Fix:** Keep the JSON key for backup compatibility but remove it from the UI/logic paths, or migrate: if an old backup has `directBluetoothPrintEnabled=true` and no `receiptPrintMode`, map it to `BLUETOOTH`. Add a unit test for the migration.

## P3: Save to gallery reports false success

- **Issue:** `saveBillJpegToDownloads` and `saveBillPdfToDownloads` fall back to the app-private external folder and still return `true`, so the user believes the file is in Pictures/Downloads.
- **Fix:** Return a result type (public saved, private fallback, failed) and show a different toast for the private fallback that includes the path.

## P3: Hard-coded English strings and housekeeping

- **Issue:**
  - Hard-coded strings: `"Invoice JPEG"`, `"Voucher Slip"`, `"Due Reminder"`, `"Customer phone"`, `"Print destination is unavailable"`, `"Could not decode receipt image file"`, job names like `"Invoice #..."`.
  - Cache folders `bills`, `reports`, `receipts` and label PDFs are never cleaned.
  - Batch label total is capped at 500 copies in `ProductCodePrintDialogs.kt` without telling the user (the `remainingAllowed = 500` loops).
- **Fix:** Move user-visible strings to `strings.xml` (EN and BN). Add a cleanup that deletes cache files older than 7 days at app start or after each print. Show a toast or inline note when the 500-label cap truncates the batch.

## Tests to add

- Pure-function tests in `PrinterRoutingAndDriverTest.kt` for: TSPL payload clamping (`DENSITY`, `GAP`, `PRINT 1,n`) if a Bitmap-free overload is extracted, any new threshold function, settings migration, and the "do not fall back after partial success" routing rule (extract it into a pure function like `decidePrintRouting`).
- A Z/X report width test (384 vs 576) if the generators can run in an instrumented test (`app/src/androidTest/.../BillPdfPrintIntegrationTest.kt` is a good place).
