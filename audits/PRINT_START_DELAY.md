# Bug: It takes about 14.5 seconds from tapping Print until the printer starts printing

Work order for an implementing agent. Read it fully before changing code. Measure first, then fix what the numbers show, not what is guessed.

Style rules: never use the em dash character in code, comments or strings. Add every new user-facing string to BOTH `app/src/main/res/values/strings.xml` and `app/src/main/res/values-bn/strings.xml`. Match the surrounding code style.

## Symptom (reported by the owner, real device)

After tapping Print on a receipt, the printer starts printing only after about 14.5 seconds. Printer model, tablet model, Bluetooth versus USB, and receipt length are not known. Ask the owner or log them.

## Where the time can go (receipt print path)

All paths below are under `app/src/main/java/com/example/techyfipos/`.

1. `ui/dialogs/ReceiptSummaryDialog.kt`: Print button calls `utils/BillActions.kt` `printReceiptWithBluetoothFallback`.
2. `generateBillJpeg` in `utils/BillPdfGenerator.kt` (IO thread): draws the whole receipt, logo, QR code, then JPEG-encodes it at quality 95 to a cache file.
3. `printReceiptImage` in `utils/BillActions.kt`: decodes that JPEG file again with `BitmapFactory.decodeFile`.
4. `EscPosPrinterDriver.printReceiptDirect`: converts the whole bitmap to ESC/POS raster bands (`bitmapToEscPosBands`) BEFORE the connection is even opened. Nothing is sent until all bands exist.
5. `printer/BluetoothPrinterTransport.kt` `sendToBluetoothPrinter`: waits for the shared hardware mutex, `cancelDiscovery`, then connects (secure RFCOMM first, insecure fallback, two attempts with a 500 ms pause), then a 250 ms settle, then writes.
6. Writes: 512 byte chunks with a 15 to 35 ms pause (`bluetoothChunkDelayMs`). A printer may only start moving paper after it has received a full buffer or the first bands.
7. Anything holding the hardware mutex ahead of the receipt: a cash drawer pulse, a previous receipt still waiting for its status reply (`GS r 1`, up to 8 s) or draining, a label job.

## Already done on this branch (do not redo)

- Commit `a3de8bf`: the `GS r 1` status query is now opt-in (`awaitStatusReply`), used only for receipt and cut-test jobs, and printers that never reply are remembered for the process lifetime. Before this, every Bluetooth job (including cash drawer pulses and label printing) waited 3 to 8 s for a reply that never came and held the mutex, which delays the next receipt.
- Commit `767cef8`: `bitmapToEscPosBands` now reads one 64 row band per `Bitmap.getPixels` call and fills the output array directly (it used one JNI `getPixel` call per dot and a `ByteArrayOutputStream.write` per byte). New pure helper `fillRasterBandRows` with a unit test.
- Commit `767cef8` also added `PrintTiming` log lines: receipt image generation time (`BillActions`), rasterization time (`EscPosPrinterDriver`), Bluetooth connect time (`BtPrinterTransport`).
- The cash drawer pulse in `CartScreen.kt` is already fire-and-forget (it does not block the receipt dialog).

Remember that the owner may have tested a build older than `a3de8bf`, where the status-query bug alone could add 3 to 8 s per job. Re-measure on the current branch first.

## Step 1: Measure (do this before changing anything else)

1. Add a single end-to-end timeline per print job, logged with tag `PrintTiming`, from the moment the Print button is tapped to the moment the first byte is written. Stages and their timestamps: button tap, JPEG generated, JPEG decoded, raster done, mutex acquired, bluetooth/USB connected, settle done, first chunk written, last chunk written, status reply or drain done, socket closed. Log the delta between consecutive stages and the total. Keep it cheap (plain `System.currentTimeMillis`), no UI changes needed.
2. Include in the log: receipt row count and bitmap size, paper width, number of bands, total bytes, target type (Bluetooth or USB), whether the mutex was contended, whether the connect needed the insecure fallback or a second attempt.
3. Add a "PrintTiming" summary line the owner can read from a bug report, for example `TOTAL=14500ms generate=2100 decode=150 raster=900 mutexWait=0 connect=9300 settle=250 firstWrite=...`.
4. Ask the owner (or document in the final report) to print one long receipt and send logcat filtered on `PrintTiming`. If you cannot get device logs, implement the fixes below that are safe regardless, and say which numbers are still unknown.

## Step 2: Fix by measured cause (do the ones the numbers justify)

### A. Bluetooth connect is the slow part

Typical causes: the secure RFCOMM attempt fails or times out before the insecure fallback succeeds; the printer is still releasing the previous connection; a stale discovery or bonding state.
- Remember, per printer address, which socket type worked last (secure or insecure) in memory (and optionally in preferences) and try that one first. Do not pay the failing attempt on every print.
- Do not retry the whole sequence with a fixed 500 ms pause when the first full attempt failed with a quick error; only retry on timeout style failures.
- Consider a short per-address connect timeout so a dead attempt cannot take 10 s plus. `BluetoothSocket.connect()` has no timeout parameter, so run it with `withTimeout` on a separate thread and close the socket on timeout to unblock it.
- Do the connect in parallel with rasterization (see C) so connect latency overlaps CPU work instead of adding to it.
- Optionally keep the RFCOMM socket open for a short idle window (for example 20 to 30 s) and reuse it for the next job to the same printer, closing it on idle, on Bluetooth off, on error, and before label printing or any other target. This removes the connect cost for back to back receipts but is riskier (printers that drop idle links); make it a guarded, well tested change and keep a safe fallback to reconnect.

### B. Receipt image generation (`generateBillJpeg`) is slow

- Look for per print work that can be cached: decoding the shop logo (decode and scale once per settings change), QR generation for the same bill, Typeface and Paint creation.
- Avoid the JPEG round trip for printing. Today the bitmap is encoded to a quality 95 JPEG on disk and decoded again just to be rasterized. Add an in-memory path (return the `Bitmap` or keep it in a small LRU keyed by sale id) used by direct printing, and keep the file only for share and save. Make sure bitmaps are recycled correctly.
- Generate the receipt image before the user taps Print: the receipt dialog already knows the bill when it opens. Build the printable bitmap (and the ESC/POS bands if the receipt printer is configured) on a background coroutine when the dialog opens, cache it, and make Print send the cached bytes. Invalidate the cache when the roll width, language, or bill changes. This can make the delay after the tap close to just the connection.

### C. Rasterization is still slow

- Verify the new `getPixels` code with the timing log. If it is still large on a low end tablet, convert one band at a time while the previous band is being written (stream bands into the transport instead of building all of them first), and/or start the Bluetooth connect before rasterization finishes.
- Avoid `Bitmap.createScaledBitmap` when the image is already the target width (the JPEG is generated at 576 or 384 wide, check it really matches), and avoid a second full copy.
- Consider dithering or thresholding on the generator side only if it removes work; do not change print output quality without the owner's approval.

### D. The mutex is held by something else

- Log who held the hardware mutex and for how long (job type, address). Make sure label printing, drawer pulses and cut tests never hold it longer than needed, and that the receipt job does not queue behind a slow previous job.
- Check that the post-print wait of the PREVIOUS receipt (status reply wait of 3 to 8 s the first time for printers that never reply, then the scaled drain up to 4 s) is not the delay. If it is, release the mutex as soon as all bytes are written and let the socket close in the background, as long as the next job cannot connect before the previous socket is closed (use a per-printer "closing" state instead of holding the global lock for the whole drain).

### E. The printer starts late even though bytes are sent early

- Some printers wait for a certain amount of data before printing. Compare "first byte written" with when paper actually moves. If there is a large gap, try sending the first bands without per-chunk pacing and apply the slower pacing only after the first N KB, or check whether `ESC @` followed by a 100 ms delay and the first-band 20 ms delays matter.
- Re-check `bluetoothChunkDelayMs` (15 ms up to 35 ms per 512 bytes). It scales the transfer time of long receipts and was added without hardware proof. If total print time got worse, reduce it or only apply it after the first N KB.

## Constraints

- Do not break the cut fix: the final feed and cut must still be delivered, and the status reply / drain logic from `a3de8bf` must stay. The end segment (feed, cut, optional drawer kick) must still be sent last.
- Keep USB printing working (same `printerHardwareMutex`, same segments and delays).
- Label (TSPL) printing and the cash drawer pulse must not get slower or start querying printer status.
- Keep the Android system print dialog fallback behaviour unchanged.

## Tests

- Unit tests for any new pure logic: stage timeline formatting, socket type preference selection, connect timeout handling, cache key and invalidation rules, band streaming order.
- Run `./gradlew :app:testDebugUnitTest` (and `./gradlew :app:assembleDebug` if the Android SDK is available).

## Acceptance

- A `PrintTiming` summary line is logged for every receipt print with each stage's duration.
- On the owner's printer, the time from tapping Print to the first paper movement is clearly lower than 14.5 s for a typical long receipt, and the report shows before and after numbers. Target: a few seconds at most for the first print after idle, and close to instant for back to back prints.
- The cut still works for short and long receipts.
- No regressions for label printing, the cash drawer pulse, USB printing or the system print dialog.

## Final report

State the measured numbers (before and after, per stage), which cause(s) you confirmed, what you changed, and what could only be verified on the owner's real printer. Do not claim a hardware speed result from reading code.
