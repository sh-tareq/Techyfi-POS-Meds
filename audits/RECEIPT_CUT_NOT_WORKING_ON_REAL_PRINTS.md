# Bug: Auto-cut works in "Test cut" but not when printing a real receipt

Work order for an implementing agent. Read it fully before changing code.

Style rules: never use the em dash character in code, comments or strings. Add every new user-facing string to BOTH `app/src/main/res/values/strings.xml` and `app/src/main/res/values-bn/strings.xml`. Match the surrounding code style.

## Symptom (reported by the owner, tested on a real device)

- Settings > Receipt Printer > "Test cut" cuts the paper correctly.
- Printing a real receipt (sales receipt, repayment slip, voucher, X/Z report) through the same printer does NOT cut.
- Not yet known: whether a very short real receipt (one cheap item) cuts, which printer model it is, and whether the connection is Bluetooth or USB. Ask the owner or treat the cases below as open.

## Relevant code (under `app/src/main/java/com/example/techyfipos/`)

- `printer/EscPosPrinterDriver.kt`: `printReceiptDirect` (real receipts) and `printCutTestDirect` / `buildCutTestSegments` (test cut). Both end with `buildReceiptEndSegment` (feed, cut, optional drawer kick), so the bytes sent at the end are identical.
- `printer/BluetoothPrinterTransport.kt`: `sendToBluetoothPrinter` writes the segments in 512 byte chunks with a 15 ms pause, flushes, waits, then closes the RFCOMM socket.
- `printer/UsbPrinterTransport.kt`: `sendToUsbPrinter` (bulk transfer, then a 150 ms drain and close).
- `utils/BillActions.kt`: `printReceiptImage` (routing, settings to cut mode, AUTO fallback to the system print dialog), `executeReceiptCutTest`.
- `ui/dialogs/ReceiptSummaryDialog.kt` (`printReceiptWithBluetoothFallback(settings = activeBill.settingsData)`), `ui/screens/CartScreen.kt` (where `BillData.settingsData` is set), `ui/screens/SettingsScreen.kt` (test cut button uses the on-screen `currentSettings`).
- Tests: `app/src/test/java/com/example/techyfipos/printer/PrinterRoutingAndDriverTest.kt`.

## What is already done (do not redo)

Commit `08e7809` on this branch added `bluetoothPostPrintDrainMs(totalBytes)` in `BluetoothPrinterTransport.kt`: the wait before closing the Bluetooth socket now scales from 300 ms to 4 s with the payload size (it was a fixed 300 ms). Reasoning: the cut test is one small image, a real receipt is a long raster stream (tens of KB). Closing the socket drops bytes still queued in the phone's Bluetooth stack, and the last bytes of the job are the feed and cut command. A blank receipt footer hides a truncated print.

This is a hypothesis and it has NOT been verified on hardware. Treat it as a first mitigation, not the confirmed fix. Your job is to make the cut reliable and to prove or disprove each cause below.

## Candidate causes, in the order to investigate

### 1. Tail of the job lost when the socket closes (Bluetooth)

A wait proportional to size is a guess. Make it deterministic instead:
- After the end segment, ask the printer for a reply and wait for it before closing. Commands are processed in order, so a reply to a buffered status command proves everything before it, including the cut, was consumed. Use a non real-time query such as `GS r 1` (`1D 72 01`, transmit paper sensor status) or `GS I 1` (`1D 49 01`, printer ID) and read the response byte from `socket.inputStream` with a timeout (about 3 to 8 s, scaled by size). Do NOT use `DLE EOT` (`10 04 n`), it is real-time and answers immediately.
- Many cheap printers do not answer. If no reply arrives in the timeout, fall back to the scaled drain already in place. Never fail the print because the reply is missing.
- Do the read on the IO dispatcher with a hard timeout, and always close the socket in `finally`.
- Keep the shared hardware mutex held until the socket is closed.
- Add a pure function for the timeout/drain calculation with unit tests.

### 2. Cut is sent while the printer is still busy or is ignored after raster data

Some printers need a short pause before the final feed and cut, or need the cut as a separate write:
- Add a delay (about 150 to 300 ms) between the last raster band and the end segment in `printReceiptDirect` (the delay list entry for the last band is 20 ms today). The cut test has only one tiny band so this gap never mattered there.
- Make sure the end segment is flushed as its own write and not merged with the final band.
- If the printer uses a different command set, the already existing `receiptCutCommandStyle` (`GS_V`, `LEGACY_ESC`, `GS_V_FEED`) may be needed. There is no UI for it yet. Add a small "Cut command style" selector under the cut mode in the Receipt Printer card (three options, English and Bengali strings, persist like the other printer settings) so the owner can try another style with the Test cut button. Mention in a hint that this is only for printers that do not cut with the default.

### 3. Real receipts use different settings than the test

- Test cut uses the on-screen `currentSettings`. Receipts use `BillData.settingsData` captured when the bill was created (`CartScreen.kt`, `SalesHistoryScreen.kt`). Verify the cut mode, feed lines, cut style and printer target really reach `printReceiptImage` for every receipt path (sales receipt, repayment slip, voucher, X/Z report, reprints from history, Settings test receipt).
- Saving printer settings goes through `DataRepository.saveInvoiceSettingsData`, which calls `requirePermission(AppPermissions.SYSTEM_SHOP_SETTINGS)`. If the logged-in role lacks it, the on-screen value changes (so Test cut works) but nothing is persisted (so receipts keep the old value). Confirm what happens in that case and make it visible (a toast or disabled controls) instead of a silent failure.
- Check that `receiptCutMode`, `receiptFeedLinesBeforeCut` and `receiptCutCommandStyle` survive `toJson` / `fromJson`, the DataStore round trip, backup and restore.

### 4. Real receipts are not going through direct printing at all

In `printReceiptImage`, AUTO mode falls back to the Android print dialog when direct printing fails, and SYSTEM mode never sends cut bytes. The Test cut button only does direct printing. Check, with logging, which route a real receipt actually takes (receipt print mode, resolved target, direct result). If the receipt is silently falling back to the system dialog, there will be no cut by design. Surface the failure reason to the user (the existing toast already exists for most cases, verify it shows).

### 5. Bluetooth throughput or buffer overrun on long raster data

- Compare the bytes written per second against what a printer can accept. 512 byte chunks every 15 ms is about 34 KB/s requested. Some printers overflow their input buffer and drop data (the end of the stream is lost first).
- If so, lower throughput for large jobs (smaller chunk or longer chunk pause, scaled by `receiptDarkness` and paper width) or add a "Slow printing for reliability" safety option. Keep short jobs fast.

## Diagnostics to add first (so the cause can be proven)

1. In `printReceiptImage` and `EscPosPrinterDriver.printReceiptDirect`, add one `Log.d` line per print with: route (direct or system), target type and identifier, cut mode, cut style, feed lines, number of bands, total bytes, and the hex of the final end segment.
2. In `sendToBluetoothPrinter`, log the total bytes, elapsed write time, the drain used, and whether a status reply was received (once cause 1 is implemented).
3. Add a "Test cut (long receipt)" option to the Settings test cut area, or a second tall test image mode in `printCutTestDirect` (for example 1200 rows of text and a pattern, about the length of a 10 item receipt). This reproduces the problem without making a sale and is the main way to compare short versus long behaviour. Reuse the existing spinner and toast pattern, never kick the drawer, English and Bengali strings.

## Acceptance

- A long real receipt (10 or more items) and a short one both cut with Full cut and Half cut on the owner's printer, over Bluetooth (and USB if available).
- The "Test cut (long receipt)" button reproduces and then passes the same scenario.
- Logs show the route, cut bytes and drain for each print, so a future failure can be diagnosed from logcat.
- If the cause turns out to be settings not saved without permission, the UI tells the user.
- Unit tests pass for all new pure logic (drain/timeout calculation, any new byte building, settings round trip). Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` if the Android SDK is available.

## Final report

State which cause(s) you confirmed, which you ruled out and how, what you changed, and exactly what still needs a real printer to verify (do not claim a hardware fix is verified from reading code).
