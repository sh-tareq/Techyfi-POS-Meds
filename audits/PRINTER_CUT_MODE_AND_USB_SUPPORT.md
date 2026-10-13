# Printing: Auto-Cut Mode and USB Printer Support

Two missing features in the printing system. This file is a work order for an implementing agent. Nothing here has been implemented yet.

Style rules: never use the em dash character in code, comments or strings. Add every new user-facing string to BOTH `app/src/main/res/values/strings.xml` and `app/src/main/res/values-bn/strings.xml`. Match the surrounding code style. Keep settings JSON backward compatible (old backups must still load with sensible defaults).

Key files (under `app/src/main/java/com/example/techyfipos/`):
- `printer/EscPosPrinterDriver.kt` (receipt bytes, cut and drawer commands)
- `printer/BluetoothPrinterTransport.kt` (the only transport today)
- `printer/TsplLabelPrinterDriver.kt` (label bytes)
- `utils/BillActions.kt` (routing: `printReceiptImage`, `printLabelImages`, `triggerCashDrawerKick`, `showDirectPrintToast`)
- `data/InvoiceSettings.kt` (settings model and JSON)
- `ui/screens/SettingsScreen.kt` (printer cards, `BluetoothPrinterPickerDialog`, cash drawer section)
- `app/src/main/AndroidManifest.xml`
- Tests: `app/src/test/java/com/example/techyfipos/printer/PrinterRoutingAndDriverTest.kt`

---

## Feature 1: Paper auto-cut mode (Full cut / Half cut / No cut)

### Issue

- `EscPosPrinterDriver.printReceiptDirect` always writes `CMD_FEED_4_LINES` followed by `CMD_PARTIAL_CUT` (`0x1D 0x56 0x01`). There is no setting.
- Users with printers that need a full cut cannot get one. Users with a printer that has no cutter, or who tear manually, get a wasted cut command and a fixed 4-line feed. Some printers misbehave on an unexpected cut command.
- Every receipt-type output goes through this one path (sales receipts, due repayment slips, store credit vouchers, X/Z reports, Settings test receipt), so one setting covers all of them.
- The cut is only relevant to direct Bluetooth ESC/POS printing. The Android system print service path has no cut control and is out of scope.

### What needs to be done

1. **Model.** In `InvoiceSettingsData` add:
   - `receiptCutMode: String = "PARTIAL"` with allowed values `"FULL"`, `"PARTIAL"`, `"NONE"`. Default `PARTIAL` keeps current behaviour.
   - `receiptFeedLinesBeforeCut: Int = 4` (coerce 0 to 10 on load).
   - Serialize in `toJson()` and read in `fromJson()` with the defaults above; unknown or blank values fall back to `PARTIAL`.
2. **Driver.** Add an enum or sealed type (for example `ReceiptCutMode { FULL, PARTIAL, NONE }`) in the printer package with a pure function mapping a mode to bytes:
   - FULL: `0x1D 0x56 0x00`
   - PARTIAL: `0x1D 0x56 0x01`
   - NONE: empty (no cut bytes)
   - Optional but recommended: an "advanced" cut command style for printers that ignore `GS V`: legacy `ESC i` (`0x1B 0x69`) for full and `ESC m` (`0x1B 0x6D`) for partial, and the `GS V 66 n` form (`0x1D 0x56 0x42 n`, feed then cut). If you add it, expose it as one extra setting (`receiptCutCommandStyle`), default `GS_V`.
   - Change `printReceiptDirect` to take `cutMode` and `feedLines`, build the feed command from `feedLines` (`ESC d n`, `0x1B 0x64 n`, skip if 0), and append the cut bytes only when mode is not NONE.
   - Order of the final segment: feed, cut, then drawer kick (as today).
   - With NONE, keep a sensible feed (still honour `feedLines`) so the user can tear the paper past the print head.
3. **Wire through.** Pass `settings.receiptCutMode` and `settings.receiptFeedLinesBeforeCut` from the two `EscPosPrinterDriver.printReceiptDirect` call sites in `BillActions.kt` (`printReceiptDirectBluetooth` and `printReceiptImage`).
4. **UI.** In the Receipt Printer card in `SettingsScreen.kt` (below the paper width chips, only when `receiptPrintMode != "SYSTEM"`), add:
   - A 3-option `SingleChoiceSegmentedButtonRow` labelled "Paper cut": Full cut, Half cut, No cut. Follow the exact pattern used by the print method selector in the same card, including `maxLines = 1` and ellipsis.
   - A small stepper or slider for "Feed before cut" (0 to 10 lines), disabled or hidden when cut mode is NONE is NOT required (feed still matters), keep it always visible.
   - A one-line helper text: "Only works on printers with an auto-cutter. Other printers ignore the cut command."
   - Persist instantly via `onUpdateSettings(currentSettings.copy(...))` like the other printer settings.
5. **Test receipt.** The existing "Test Receipt" button must honour the new setting so the user can verify the cut.
6. **Strings.** Add English and Bengali strings for the section title, the three options, the feed label and the helper text.
7. **Tests** (in `PrinterRoutingAndDriverTest.kt`): bytes for each cut mode, feed line clamping, final segment ends with the cut bytes (or has none for NONE), kick appended after the cut, and JSON round trip plus default for old JSON without the new keys.

### Acceptance

- Choosing Full cut sends `1D 56 00`, Half cut sends `1D 56 01`, No cut sends no cut bytes.
- Old backups load as Half cut with 4 feed lines.
- Unit tests pass.

---

## Feature 2: USB printer support

### Issue

- All direct printing is Bluetooth SPP only. `BluetoothPrinterTransport.kt` is the sole transport, settings store only a Bluetooth MAC address (`directBluetoothPrinterAddress`, `labelPrinterAddress`, `cashDrawerPrinterAddress`), and the picker (`BluetoothPrinterPickerDialog`) only lists bonded Bluetooth devices.
- There is no USB code anywhere in the app (the only USB mentions are about USB barcode scanners). The manifest has no `android.hardware.usb.host` feature.
- Many shops run wired USB thermal receipt printers and USB label printers from a tablet with an OTG cable. Today they can only use the slow system print dialog.

### What needs to be done

1. **Transport abstraction.** Introduce a small interface in the printer package, for example:
   ```kotlin
   interface PrinterTransport {
       suspend fun send(context: Context, segments: List<ByteArray>, perSegmentDelaysMs: List<Long>?, onSegmentSent: ((Int) -> Unit)?): DirectPrintResult
   }
   ```
   Make the existing Bluetooth code one implementation and add a USB one. Keep the shared mutex so two jobs never talk to hardware at once. Keep `DirectPrintResult` and add USB cases (for example `UsbNoPermission`, `UsbDeviceNotFound`, `UsbNoPrinterInterface`) or reuse `Failed` with clear messages, and update `showDirectPrintToast` and the strings accordingly.
2. **USB implementation** (`UsbPrinterTransport.kt`):
   - Use `UsbManager`. Find the device by vendorId/productId (and serial number when available, since two identical printers share IDs).
   - Pick the interface with `UsbConstants.USB_CLASS_PRINTER` (7) that has a bulk OUT endpoint. If none, fall back to the first interface with a bulk OUT endpoint (some cheap printers report class 255 vendor specific).
   - `openDevice`, `claimInterface(iface, true)`, send with `bulkTransfer` in chunks (start with 4096 bytes or the endpoint `maxPacketSize` multiple), check the return value (negative means failure), use a timeout (about 5 s), always `releaseInterface` and `close` in `finally`.
   - Honour the same per-segment delays and `onSegmentSent` callback as Bluetooth so batch label printing and receipt banding keep working.
   - USB has no connect settle delay requirement; keep a short drain delay after the last segment.
3. **Permission flow.** Android requires explicit USB permission per device:
   - Check `usbManager.hasPermission(device)`; if missing, call `requestPermission` with a `PendingIntent` (use `FLAG_MUTABLE` on API 31+ as required for this broadcast, explicit package, and a dedicated action string) and a `BroadcastReceiver` that resumes the waiting coroutine with granted or denied (use `suspendCancellableCoroutine` and unregister the receiver in all paths).
   - If the permission dialog is shown, the print continues automatically once granted; if denied, return a clear result and toast.
   - Optional: declare a `USB_DEVICE_ATTACHED` intent filter with a `device_filter.xml` limited to printer class so Android can remember "always open with this app" and grant permission persistently. Not required for v1.
4. **Manifest.** Add `<uses-feature android:name="android.hardware.usb.host" android:required="false" />` so the app still installs on devices without USB host.
5. **Settings model.** Today a printer is identified by a plain string MAC. Add a connection type and a USB identity while staying backward compatible. Recommended approach: keep the existing address fields as they are for Bluetooth, and add parallel fields per role:
   - `receiptPrinterConnection: String = "BLUETOOTH"` (`"BLUETOOTH"` or `"USB"`), `receiptUsbDeviceKey: String = ""`
   - `labelPrinterConnection`, `labelUsbDeviceKey`
   - `cashDrawerPrinterConnection` can be dropped by making the drawer follow the receipt printer when blank (it already falls back to the receipt printer); if you keep a separate drawer printer, give it the same pair of fields.
   - A "device key" string such as `usb:<vendorId>:<productId>:<serial or empty>`. Do not store the Android `deviceName` path (`/dev/bus/usb/001/004`), it changes when replugged.
   - Update `resolvedReceiptPrinterAddress()` callers or add a `resolvedReceiptPrinterTarget()` returning a sealed type (Bluetooth(address) or Usb(key) or None) and use it in the routing code. Every place that checks `address.isBlank()` in `BillActions.kt` (`printReceiptImage`, `printLabelImages`, `triggerCashDrawerKick`, `decidePrintRouting` inputs) must work with either type.
   - Update `toJson()` and `fromJson()`, defaulting everything to Bluetooth/empty so old backups behave exactly as before.
6. **Same-printer guard.** `isSamePrinter` compares MACs only. Extend it (or add a target-level equality) so the "label printer is the same as the receipt printer" guard and picker blocking also work for USB devices (same vendorId/productId/serial). Keep the existing unit tests passing.
7. **Picker UI.** In `SettingsScreen.kt` replace the Bluetooth-only picker with a combined picker:
   - Two sections or tabs: Bluetooth (bonded devices, existing behaviour) and USB (devices from `usbManager.deviceList` that look like printers, with a "Show all USB devices" toggle for printers that report an unusual class).
   - Show product name and manufacturer when available (`getProductName` and `getManufacturerName` need no permission before API 29 but may be null; fall back to `VID:PID`).
   - Show a refresh button for USB since devices can be plugged in while the dialog is open (re-read `deviceList`, or listen to `USB_DEVICE_ATTACHED` and `USB_DEVICE_DETACHED` while the dialog is open).
   - Keep the "None / not set" row and the conflict blocking added for Bluetooth.
   - Update the printer cards (receipt, label, cash drawer) to show the selected device name, a Bluetooth or USB icon/badge, and a "not connected" hint for USB when the saved device is not currently attached.
   - Keep the print method selector (Auto, Bluetooth, System) but rename the middle option to something like "Direct" or "Direct (Bluetooth/USB)" so it is no longer Bluetooth specific. Update strings in both languages. Keep the stored value `"BLUETOOTH"` for backward compatibility with existing settings and `decidePrintRouting`, or migrate to `"DIRECT"` with a loader that maps the old value (preferred only if you update every comparison and the tests).
8. **Receipt and label drivers.** Change `EscPosPrinterDriver.printReceiptDirect`, `TsplLabelPrinterDriver.printLabel`, `printLabelsBatch` and `printLabelPayloadsBatch`, and `triggerCashDrawerKick` to take a printer target and dispatch to the right transport instead of calling `sendToBluetoothPrinter` directly. The byte generation stays identical.
9. **Error messages.** Add strings (English and Bengali) for: USB device not found, USB permission denied, no printer interface on this USB device, USB transfer failed, and the USB host not supported on this device. Surface them through `showDirectPrintToast`. Do not auto-open app settings for USB permission; the USB permission dialog is its own flow.
10. **Cash drawer.** The kick pulse must also work over USB (same bytes, USB transport) when the drawer follows a USB receipt printer.
11. **Docs.** Update `APP_MIND_MAP.md` printing sections (Printers settings card, Printing paragraph) to mention USB and the cut mode setting.
12. **Tests** (unit, pure logic only, no hardware): USB device key creation and parsing, target equality and same-printer guard across Bluetooth and USB, settings JSON round trip and old-JSON defaults, routing decisions with a USB target (blank, success, failure, partial success), endpoint selection helper if you extract it as a pure function over a simple data model.

### Acceptance

- A USB receipt printer can be selected in Settings, a test receipt prints, and the cut and drawer kick work.
- A USB label printer (TSPL) can be selected for labels, single and batch label printing work, and the same-printer guard blocks assigning the same USB device to both roles.
- The USB permission prompt appears the first time, and printing resumes after the user allows it.
- Existing Bluetooth setups, old backups and the system print dialog fallback keep working unchanged.
- App still installs and runs on devices without USB host support.

---

## Notes for the implementing agent

- Do both features in separate commits. Do the cut mode first (it is small and independent), then USB.
- Recommended order for USB: abstraction and Bluetooth refactor first with all existing tests green, then the USB transport, then settings and routing, then picker UI.
- This code cannot be fully verified without hardware. Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` if the Android SDK is available, and list clearly in your final report what could only be reviewed by reading and needs a device check (USB permission flow, real bulk transfer, real cutter behaviour on 58mm and 80mm printers).
