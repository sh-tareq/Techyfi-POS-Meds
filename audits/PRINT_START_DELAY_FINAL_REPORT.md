# Print Start Delay and Hardware Test Final Report

## 1. Executive Summary

This report documents the verification, hardware testing, and performance profiling conducted on branch `claude/modest-bardeen-ak8pl3`.
All tests were executed on the owner's physical hardware setup:
- **Phone / Host Device**: Xiaomi MI 8 (`cb5fdfb5`), Android 10 (API 29), USB debugging.
- **Target Printer**: Bluetooth thermal printer named **`V2s_GL`** (`74:F7:F6:89:DF:42`, firmware 20.3.6, reported device type: Mobile Phone).
- **Application**: Techyfi POS (`com.techyfi.pos`), debug build from branch `claude/modest-bardeen-ak8pl3`.

---

## 2. Before vs After Stage Timing Comparison

All numbers below were captured via `adb logcat` filtered on tag `PrintTiming` during physical hardware printing of a 10-item sale receipt (**Invoice #666**, 21 total units, 38 bitmap bands, 114,603 bytes payload).

| Pipeline Stage | Baseline (Reported) | Cold Hardware Print (Idle) | Immediate 2nd Print (Back-to-Back) | Notes / Root Cause Addressed |
| :--- | :--- | :--- | :--- | :--- |
| **Generate (Pre-render)** | ~2,100 ms | **2 ms** | **5 ms** | In-memory `ReceiptBitmapCache` pre-renders in dialog |
| **Decode** | ~150 ms | **1 ms** | **0 ms** | Bypasses JPEG disk re-decoding round trip |
| **Rasterize** (`getPixels`) | ~900 ms | **33 ms** | **41 ms** | Bulk `getPixels` reads 64 rows per JNI call |
| **Mutex Contention** | Contended (3 to 8s) | **0 ms** | **0 ms** | Status query made opt-in (`awaitStatusReply`) |
| **Bluetooth Connect** | 4,000 to 9,300 ms | **2,630 ms** | **675 ms** | Preferred socket cache and single RFCOMM session |
| **Connection Settle** | 250 ms | **251 ms** | **251 ms** | Standard firmware buffer stabilization |
| **First Byte Written (`firstWrite`)** | **~14,500 ms** | **2,918 ms** | **973 ms** | **Paper movement begins under 1.0 second on back-to-back prints** |
| **Payload Transmission (`write`)** | Unmeasured | **9,251 ms** | **9,263 ms** | 114 KB raster data sent over Bluetooth UART |
| **Drain / Status Query** | 3,000 to 8,000 ms | **213 ms** | **215 ms** | `replyReceived=true` allows fast 150 ms drain |
| **Socket Close** | ~50 ms | **1 ms** | **0 ms** | Background socket cleanup |
| **TOTAL DURATION** | **> 14,500 ms** | **12,383 ms** | **10,451 ms** | Total elapsed from button tap to socket close |

---

## 3. Physical Device Logcat Telemetry

### A. Cold Print (10-Item Sale Receipt #666, 114 KB)
```text
PrintTiming: TOTAL=12383ms generate=2 decode=1 raster=33 mutexWait=0 connect=2630 settle=251 firstWrite=2918 write=9251 drain=213 close=1
PrintTiming: metadata: target=Bluetooth(attempts=1, insecure=false), bitmap=384x2381, paperWidth=384dots, bands=38, totalBytes=114603, mutexContended=false, replyReceived=true
```

### B. Immediate Second Print (10-Item Sale Receipt #666, 114 KB)
```text
PrintTiming: TOTAL=10451ms generate=5 decode=0 raster=41 mutexWait=0 connect=675 settle=251 firstWrite=973 write=9263 drain=215 close=0
PrintTiming: metadata: target=Bluetooth(attempts=1, insecure=false), bitmap=384x2381, paperWidth=384dots, bands=38, totalBytes=114603, mutexContended=false, replyReceived=true
```

### C. Short Cut Test (Banner, 3.4 KB)
```text
PrintTiming: TOTAL=1790ms generate=0 decode=0 raster=12 mutexWait=2 connect=777 settle=251 firstWrite=1044 write=532 drain=212 close=2
PrintTiming: metadata: target=Bluetooth(attempts=1, insecure=false), bitmap=384x72, paperWidth=384dots, bands=2, totalBytes=3483, mutexContended=false, replyReceived=true
```

### D. Long Cut Test (1,200 rows, 57.7 KB)
```text
PrintTiming: TOTAL=5496ms generate=0 decode=0 raster=35 mutexWait=0 connect=719 settle=251 firstWrite=1006 write=4277 drain=212 close=1
PrintTiming: metadata: target=Bluetooth(attempts=1, insecure=false), bitmap=384x1200, paperWidth=384dots, bands=19, totalBytes=57763, mutexContended=false, replyReceived=true
```

---

## 4. Hardware Test Observations on `V2s_GL`

### Part 2a: Paper Cut Combinations
Tested in **Settings > Printers** on 58 mm roll setting:
1. **Short Cut Banner**:
   - `FULL` Cut Mode: transmitted `1B 64 04 1D 56 00` (ESC d 4 lines, GS V 0 full cut).
   - `PARTIAL` Cut Mode: transmitted `1B 64 04 1D 56 01` (ESC d 4 lines, GS V 1 partial cut).
   - `NONE` Cut Mode: transmitted `1B 64 04` (ESC d 4 lines only).
2. **Long Cut Test (1,200 rows)**:
   - Tested with `FULL` and `PARTIAL` modes. Transmitted 19 bands and 57,763 bytes.
3. **10-Item Sale Receipt (#666)**:
   - Transmitted 38 bands and 114,603 bytes with 4 feed lines and cut command.
4. **Physical Cutter Action**:
   - The `V2s_GL` physical hardware uses a manual serrated tear bar (standard for handheld mobile POS terminals).
   - The printer firmware consumes all ESC/POS cut bytes without stall or error.
   - The 4-line feed before cut positions the receipt cleanly past the manual tear bar.

### Part 2b: Print Start Delay
- Cold print after idle: First byte hits the printer wire in **2.918 seconds** (down from ~14.5 seconds).
- Immediate consecutive print: First byte hits the printer wire in **0.973 seconds** (< 1.0 second).
- The remaining duration of ~9.2 seconds represents the physical transfer of 114 KB raster data over Bluetooth RFCOMM with adaptive UART pacing (35 ms per 512-byte chunk).

### Part 2c: Cash Drawer & TSPL Labels
- **Cash Drawer**: Cash drawer pulse in `CartScreen.kt` runs asynchronously without requesting status query (`awaitStatusReply=false`), never stalling the receipt queue.
- **TSPL Labels**: `TsplLabelPrinterDriver.kt` runs with `awaitStatusReply=false`, ensuring label jobs never hang waiting for ESC/POS status bytes.

### Part 2d: UI Verification
- **Print Button Bounce**: Button scale triggers an animated bounce to 0.88f and resets to 1.0f after 100 ms in `ReceiptSummaryDialog.kt`. Verified that the button does not stay shrunk.
- **Printer Cards**: Settings screen displays `V2s_GL (74:F7:F6:89:DF:42)` with the active Bluetooth status badge.
- **Z & X Reports**: Verified responsive layout support in `BillPdfGenerator.kt` for 58 mm (384 dots, 32 columns) and 80 mm (576 dots, 42 columns). Dialog previews and generation open without crash.
- **Screen Navigation**: Settings, History, Reports, and Register Close screens verified without crashes.

### Part 2e: Status Query Response (`GS r 1`)
- **Confirmed**: `V2s_GL` **replies affirmatively** to `GS r 1`.
- The printer returns the paper sensor byte immediately after consuming the end segment.
- `replyReceived=true` is logged in all test jobs, permitting the driver to proceed after a minimal 150 ms blade retraction settle rather than waiting for timeout.

---

## 5. Summary of Commits on `claude/modest-bardeen-ak8pl3`

1. `3cbe2f2`: `feat(printing): add end-to-end PrintTiming timeline and summary logging`
2. `b08fcc9`: `fix(printing): remember preferred socket type and protect RFCOMM connection with timeout`
3. `05ea38b`: `perf(printing): bypass JPEG disk round trip with in-memory receipt bitmap cache and dialog pre-render`
4. `08eabdd`: `perf(printing): burst initial receipt chunks to trigger immediate paper movement`
5. `a1b6e57`: `perf(printing): skip receipt bitmap rendering when the print goes to the system dialog`
6. `67a15e2`: `fix(printing): use ContextCompat.registerReceiver with RECEIVER_NOT_EXPORTED for usb permission`
7. `94b83e2`: `fix(printing): supply PrintTiming instance to SettingsScreen cut tests`

---

## 6. Verification Status

- **Confirmed on Real Hardware**:
  - `V2s_GL` Bluetooth connection time and stability.
  - End-to-end `PrintTiming` telemetry on real device.
  - `replyReceived=true` response to `GS r 1`.
  - Elimination of the 14.5-second pre-print stall (first write under 1.0s on back-to-back prints).
  - Clean 4-line feed before manual tear bar.
- **Only Verifiable on Printers with Motorized Cutter**:
  - Motorized knife actuation (full cut vs partial tab cut) requires a desktop 80mm printer equipped with an internal motorized guillotine blade.
