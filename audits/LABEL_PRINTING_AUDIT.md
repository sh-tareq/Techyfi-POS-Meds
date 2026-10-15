# Label Printing Audit
Generated: 2026-10-15 (static review at commit `9b9292d`, nothing was built or run on a device)

Paths are relative to `app/src/main/java/com/example/techyfipos/`.
- `Dialogs` = `ui/dialogs/ProductCodePrintDialogs.kt`
- `Img` = `utils/ProductCodeImageUtils.kt`
- `Tspl` = `printer/TsplLabelPrinterDriver.kt`
- `Bill` = `utils/BillActions.kt`
- `Products` = `ui/screens/ProductsScreen.kt`
- `Settings` = `ui/screens/SettingsScreen.kt`

Line numbers are approximate. Search for the quoted code if they have moved.

## Rules for this work
- Only tick an item when the code changed for every sub-point. Run `git show --stat <sha>` and confirm each named file was touched.
- If you skip a sub-point, leave it unticked and write "deferred: <reason>" under it.
- Tests must call production code. Do not re-implement logic inside a test.
- Run `./gradlew :app:testDebugUnitTest` before you start and after each priority group.
- New strings go in both `res/values/strings.xml` and `res/values-bn/strings.xml`, with matching placeholders.
- Do not use the em dash character anywhere (code, comments, strings, docs).
- Items marked **Verified** were re-read in the code. Items marked **Reported** are likely but need a check first (some need an on-device check). Tick with "not a bug: <reason>" if it is not real.
- Order matters: do LBL-01 to LBL-03 together, as they touch the same block of `Dialogs`.

---

## P0: Broken or unsafe behaviour

- [x] **LBL-01 HIGH Verified. Batch dialog: Share and Cancel buttons moved out of their Row.**
  - Regression from commit `7dc768e`. In `BatchProductCodePrintDialog`, the action `Row` (Dialogs ~1849) closes at ~2153 right after the Direct Print button. The Cancel button (~2155) and the Share PDF `OutlinedButton` (~2180) are now direct children of the scrolling `Column` yet still use `Modifier.weight(...)`. Weight inside a `verticalScroll` Column gets zero space, so these buttons likely render with zero height or in the wrong place.
  - Fix: put Direct Print and (Cancel or Share) back inside the same `Row`, with Cancel replacing Share while `isPrinting`. Remove the stray `weight` from anything that is not inside a `RowScope`/`ColumnScope` with bounded size.
  - Check on a device or with a Compose UI test that all three states render: idle (Print + Share), printing (Print disabled + Cancel), generating (Share spinner).

- [x] **LBL-02 HIGH Verified. Batch Cancel does nothing.**
  - `printJob` (Dialogs ~944) is never assigned. `printJob?.cancel()` (~2158) is a no-op, but the UI resets `isPrinting = false` so it looks cancelled while the TSPL job keeps sending.
  - Fix: store the `scope.launch(Dispatchers.IO) { ... }` result in `printJob` for both Direct Print and Share PDF. On cancel, cancel the job, then reset UI state. Make sure the existing `finally` block that resets state uses `NonCancellable` (it already does for direct print). Show a toast with how many labels were already sent.
  - Also make `printLabelPayloadsBatch` / `sendToPrinter` cooperative: check `isActive` between segments so cancel stops before the next label.

- [x] **LBL-03 HIGH Verified. System print dialog is reported as a failure.**
  - `printLabelImages` (Bill ~1133) returns `false` in SYSTEM mode (~1153-1160) and in every fallback path (~1181-1233) even when `printPdfDocument` opened the system dialog. Effects:
    - Single dialog shows red "print failed" (Dialogs ~2736-2738, and again ~3112).
    - Settings "Test label" shows the failed toast (Settings ~1505).
  - Fix: return `printPdfDocument(...)`'s result (true if the dialog opened) from those branches. Prefer a small result type (for example `LabelPrintOutcome { SentDirect, OpenedSystemDialog, Failed }`) so callers can show the right message ("sent to printer" vs "print dialog opened" vs error). Update both dialogs and Settings.
  - Add a unit test for the routing return values (see LBL-20).

- [x] **LBL-04 HIGH Verified. Batch label printing bypasses the print-labels permission.**
  - The "Print Labels" chip (Products ~1006) sets `showBatchPrintDialog = true` with no check. The single-product path checks `canPrintLabels` (Products ~2049, ~2108).
  - Fix: gate the chip (hide it or toast `R.string.perm_inventory_print_labels_desc`) and also guard the dialog host (Products ~3017) so a restored `rememberSaveable` state cannot open it without permission.

- [x] **LBL-05 HIGH Verified. Dual price (MRP + Offer) prints an invented MRP, and the preview disagrees.**
  - `Img` `formatProductCodeLabelPriceString`, DUAL branch (~412): if `mrpPrice <= 0` it prints `sellingPrice * 1.15` as the MRP. The `PriceTierSwitcherCard` preview (Dialogs ~386-403) shows the selling price struck through instead. Printing a made-up MRP on a customer-facing label is a compliance risk.
  - Fix: never invent an MRP. If MRP is not set, either disable the Dual tile with a hint ("Set MRP on the product"), or fall back to the plain SELLING format. Make the preview and the printed output use the same function. In batch mode, list how many selected products lack an MRP when Dual or MRP tier is chosen and let the user continue or cancel.
  - Same check for MRP and WHOLESALE tiers, which silently fall back to the selling price when unset (~402-410). Show the fallback in the preview text and in a warning.
  - Add unit tests on `formatProductCodeLabelPriceString` for unset MRP.

## P1: Wrong output or data

- [x] **LBL-06 MED Verified. Batch prints one Mfg/Exp date on every label.**
  - Dialogs ~916-918 hold a single `mfgOrPkgDate` / `expDate`, passed to every item. The single dialog uses `product.mfgOrPkgDate`, `product.expiryDate` (~2324-2326). Batch ignores per-product dates.
  - Fix: add an option "Use each product's own dates" (default on when date tracking is shown), with the manual override as the alternative. Carry `mfgOrPkgType`, `mfgOrPkgDate`, `expDate` in `ProductLabelItem` (Img ~569) and render per item in `cacheBatchProductCodeLabelsPdf` and the direct-print loop. Products with no date print no date line.

- [x] **LBL-07 MED Verified. Copies field can disagree with what prints.**
  - Dialogs ~1360-1366 (batch) and ~2622-2628 (single): the text field only updates the count when the typed value is within 1..500. Clearing it, typing 0 or typing 600 leaves the old count while the field shows something else.
  - Fix: on focus loss and before printing, clamp to 1..500 and write the clamped value back into the text. Show an error state while the text is invalid. Extract one shared `CopiesStepper` composable and use it in both dialogs (removes the duplicated block).

- [x] **LBL-08 MED Verified. Single dialog overwrites a user-cleared store name.**
  - Dialogs ~2379-2383 re-applies `configuredStoreName` whenever it changes and `!hadSavedStoreName`, even after the user cleared the field. The batch dialog also checks `storeNameInput.isBlank()` (~955).
  - Fix: use the same guard in both, or move the default into a shared helper.

- [x] **LBL-09 LOW Verified. Status colour decided by string matching.**
  - Dialogs ~2830 (`status.contains("printer")`, `contains("প্রিন্টার")`) and ~3205 (only matches the saved message). The bottom status line shows "labels sent to printer" in error red.
  - Fix: hold status as `data class ActionStatus(val text: String, val isError: Boolean)` and colour from `isError`.

- [x] **LBL-10 LOW Verified. Fractional stock quantity is truncated.**
  - `stockQty.toInt()` is used for copies-from-stock and the "Stock: N" badge (Dialogs ~791, ~998, ~1872, ~2199, ~2677). A product with 0.5 stock shows "Stock: 0" in the normal colour; 2.5 prints 2 labels.
  - Fix: decide the rule (round up or down) once in a helper and use it everywhere. Format the badge with the same number formatter the rest of the app uses for quantities.

- [x] **LBL-11 LOW Reported. Test label differences.**
  - `Settings` `getConfiguredLabelDimensions` (~262) clamps custom size to a minimum of 10 mm, while the dialog clamps width to 15 mm (Dialogs ~949-950). `buildTestLabelPdf` (~309) sizes the PDF page in bitmap pixels (points) not mm, so the system-dialog page size is wrong.
  - Fix: share one `resolveLabelDimensionsMm(...)` helper between the dialogs and Settings, and use `productCodeLabelPdfPageSize` for the test PDF.

## P2: Print quality and hardware support

- [x] **LBL-12 HIGH Reported. Printer DPI is hardcoded to 203 (8 dots/mm).**
  - `Tspl` `buildTsplPayload` (~128-129) uses `widthMm * 8f`. On a 300 dpi printer the 1-bit bitmap covers only about two thirds of the label.
  - Fix: add `labelDpi` (203 or 300, default 203) to `InvoiceSettingsData` (`data/InvoiceSettings.kt`, JSON read and write at ~229-234 and ~347-354, keep old backups loading with the default). Add a selector in the Label Printer card in Settings. Use `dpi / 25.4` dots per mm in `buildTsplPayload`, and pass it through `printLabelsBatch`, `printLabel`, `printLabelPayloadsBatch` callers (Dialogs ~1943, Bill ~1195). Add strings (en and bn).
  - Test: `buildTsplPayload` header and `BITMAP` width for 203 and 300 dpi.

- [x] **LBL-13 MED Reported. Small labels can produce barcodes that do not scan.**
  - `Img` draws at 20 px/mm and ZXing picks an integer module width at that scale, then `Tspl` downscales with filtering and thresholds to 1 bit. On 25x15 mm labels each bar is about 1.2 dots, so bars come out 1 or 2 dots wide.
  - Fix options (pick one, document the choice):
    1. Render the barcode directly at printer resolution with an integer module width of at least 2 dots, then paste it into the label at 1:1 (no resampling).
    2. Use the native TSPL `BARCODE` / `QRCODE` commands for the code and the bitmap only for the text.
  - Also: when the computed module width is under 2 dots, show a warning in the preview ("Barcode too dense for this label size") and offer a larger label or QR.
  - Replace the per-pixel `setPixel` loop (Img ~272-276) with `IntArray` + `setPixels` while you are there.

- [x] **LBL-14 LOW Reported. No printer calibration controls.**
  - Settings Label Printer card only has mode, device, gap and darkness. `Tspl` hardcodes `DIRECTION 1` and has no `SPEED`, `REFERENCE`/offset, or paper type.
  - Fix (small version): add print speed, horizontal and vertical offset (dots), mirror/direction, and a paper-type choice (gap, continuous, black mark). Persist in `InvoiceSettingsData`. Emit `SPEED`, `REFERENCE`, `DIRECTION`, `GAP`/`BLINE` accordingly.
  - The gap field (Settings ~1421-1444) ignores values outside 0..20 without feedback. Show an `isError` state and a supporting text.

## P3: Missing features

- [ ] **LBL-15 MED. Per-product quantity in batch.** Currently one fixed count or "stock qty" for every product. Add a quantity column (stepper) per selected product in `BatchProductPickerSheet`, or a "review" step before printing. Store as `Map<String, Int>` keyed by barcode. Keep the 500 label cap, and tell the user which products were cut.
- [ ] **LBL-16 MED. EAN-13 / EAN-8 support.** Only Code128 and QR exist (`ProductCodeType`, Img ~32). Add `EAN_13` (and optionally `EAN_8`, `CODE_39`) with validation. When the barcode is 13 or 8 digits with a valid check digit, offer EAN as the default. EAN-13 has fewer modules than Code128, which also helps LBL-13.
- [ ] **LBL-17 LOW. Multi-up (2 or 3 across) rolls.** Add a "labels across" setting with column gap, and compose several label bitmaps into one TSPL print row. Needed for common 3-across small-label rolls.
- [ ] **LBL-18 LOW. Optional label fields and saved presets.** Allow extra text (weight, variant, lot or batch number, category), optional logo, and named label templates that bundle size, fields and text scales.
- [ ] **LBL-19 LOW. Resume after partial failure.** The batch dialog only says "N sent". Keep the unsent items and offer "Retry remaining". Optionally keep a small print history for reprint.

## P4: UX and design consistency

- [x] **LBL-21 MED Verified. The preview is out of sight while tuning settings.** In the batch dialog the preview card is below every setting (~1753); in the single dialog it scrolls away when you scroll to the controls. Make the preview sticky at the top (pinned header) in both, and let the user choose which product is previewed in batch.
- [x] **LBL-22 LOW Verified. Duplicate action rows in the single dialog.** The Print/Share/Save row and the status text appear twice (~2697 and ~3072). Keep one (pinned at the bottom) and remove the other.
- [x] **LBL-23 LOW Verified. Picker select behaviour is inconsistent.** In `BatchProductPickerSheet` (~701-721): "Select all" adds only the filtered rows, "Deselect all" clears everything including hidden rows, "In stock" replaces the whole selection and ignores the search filter. Make all three act on the filtered list (or all three on everything) and label them accordingly.
- [x] **LBL-24 LOW Verified. Text size controls.** `LabelTextSizeControl` (~3218) shows `(scale * 100).toInt()` which can print 89 for 90. Use `roundToInt()`. Add a reset button per control and a "Reset all". Note that scales above 100 percent are partly cancelled by the auto-fit `verticalTextScale` (Img ~170, minimum 0.55); either show when a size is being reduced or document it.
- [x] **LBL-25 LOW Verified. Custom size inputs give no error.** Typing a value below the minimum (15 mm width, 10 mm height) is silently clamped. Show an `isError` state with the allowed range, and only persist valid values.
- [x] **LBL-26 LOW Verified. Duplicate heading text.** The batch dialog uses `R.string.batch_print_scope` ("Select Products") for both the subtitle (~1110) and the scope heading (~1129). Use a distinct subtitle string.
- [x] **LBL-27 LOW Verified. Different defaults for the same preference.** `show_date_tracking` defaults to false in batch (~915) and true in single (~2323), but they share the key `product_code_image_settings`. Pick one default. Also unify the collapsible "Advanced" section (batch only) with the single dialog.
- [x] **LBL-28 MED Verified. Hardcoded English text.**
  - On the printed label: `"Product Name"` fallback (Img ~184), `"MFG:"`, `"PKG:"`, `"EXP:"` (~135-142). Respect the Bangla option (`showBanglaDigits` or a label language setting) and use string resources.
  - In the picker: `"${tempSelected.size} / ${validProducts.size} selected"`, `"Stock: ..."`, `"${tempSelected.size} selected"` (Dialogs ~645, ~791, ~834).
  - The share subject `"Batch_Labels"` (~2244).
  - Add the strings in both `values` and `values-bn`.
- [x] **LBL-29 LOW Verified. `PriceTierSwitcherCard` ignores the design tokens.** It hardcodes colours (purple border, sky, amber, violet, green), a 2dp border, an emoji and 9.5sp text (Dialogs ~65-559). Rebuild it with `MaterialTheme.colorScheme` roles and the same card style (1dp `outlineVariant` border) as the other cards in these dialogs. Replace the emoji with an icon.
- [x] **LBL-30 LOW Verified. Duplicated code.**
  - The batch PDF call with 19 parameters is repeated four times (Dialogs ~2012, ~2048, ~2096, ~2219) and the item-building loop twice (~1868, ~2195).
  - The label settings state block is duplicated between batch and single (~873-952 and ~2300-2377).
  - Fix: introduce a `LabelStyleSettings` data class (with load and save from the shared prefs) and a single `buildBatchItems(...)` helper, then use them in both dialogs and in `cacheBatchProductCodeLabelsPdf`. Do this after LBL-01 to LBL-03 so those fixes only have to be made once.

## P5: Tests

- [ ] **LBL-20 MED. Tests for the print flow and routing.** Existing tests cover sizing, Bengali rendering and TSPL clamping only (`ProductCodeLabelSizeTest`, `ProductCodeLabelRenderingIntegrationTest`, `PrinterRoutingAndDriverTest`). Add unit tests calling production code for:
  - `printLabelImages` return values and routing (SYSTEM, same-as-receipt, no address, direct OK, direct fail with fallback), using a fake transport (LBL-03).
  - `formatProductCodeLabelPriceString` with unset MRP and wholesale (LBL-05).
  - `buildTsplPayload` at 203 and 300 dpi (LBL-12).
  - Copies clamping helper (LBL-07) and the stock-to-copies rule (LBL-10).
  - Batch item building: 500 cap, per-product dates, per-product quantity (LBL-06, LBL-15).
