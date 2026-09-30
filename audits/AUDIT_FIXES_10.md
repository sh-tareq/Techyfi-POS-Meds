# Audit Round 10 — Fix List
Generated: 2026-10-01

## Verification of AUDIT_FIXES_9.md (3 items)

| ID | Description | Result |
|----|-------------|--------|
| N-1 | About 170 hardcoded strings moved to resources | PASS for the round 9 grep. The 14 remaining matches are allowed exceptions (VISA/AMEX, WhatsApp, SMS, bKash/Nagad/Rocket, the URL placeholder, the package id and the "SHA1" clip label). BUT 56 of the new keys were added only to `values/strings.xml`, with no Bangla entry, so Bengali users still saw English. **Fixed during this check.** All 56 now have Bangla values in `values-bn/strings.xml` (block "Audit round 10"). |
| N-2 | 48dp controls checked on device | Accepted. I can't verify this from code. |
| N-3 | Taxable round-trip androidTest | PASS. It is in `BackupRestoreIntegrationTest.kt:608`. It is an instrumented test, so it needs an emulator run. |

Unit tests: 51/51 pass. The last run is newer than every main/test source file, except the `values-bn/strings.xml` edit above. **Re-run the build.**
Strings: en and bn key sets match. There are no duplicate keys, and the placeholders match.

---

## New Issues — Round 10

### HIGH

- [x] **N-1** 84 more hardcoded English user-visible strings. The round 9 grep only matched `Text("...")`,
...
- [x] **N-2** The `receipt_paid_label_bn` / `receipt_due_balance_label_bn` keys in `values/strings.xml` are
...
- [x] **N-3** Run the instrumented tests on an emulator (`./gradlew :app:connectedDebugAndroidTest`),
  so it missed `text = "..."`, `title =`, `message =`, `Toast.makeText`, `showSnackbar`, and notification
  titles in the viewmodel and workers.
  - Re-find with (from `app/src/main/java/com/example/techyfipos`):
    ```
    grep -rnE '(text|title|message|label|placeholder|contentDescription|subtitle|description) = "[A-Z][a-z]+[ ,:.!?]|Toast\.makeText\([^,]+, "[A-Za-z]|showSnackbar\("[A-Za-z]|Text\("[A-Z][a-z]+ [a-z]' ui Navigation.kt MainActivity.kt utils | grep -vE "Preview|Log\.|Exception\(|require\(|error\("
    ```
  - Also check string templates such as `"Credit Limit: ${formatTaka(x)}"` and `"Shift Total: ${...}"`.
    Convert them to `stringResource(R.string.x, formatTaka(x))` with `%1$s` placeholders.
  - Add every key to BOTH `values/strings.xml` and `values-bn/strings.xml` in the same change.
    Before finishing, confirm the bn key set equals the en key set (excluding `translatable="false"`).
  - Outside composables (ViewModel, Workers, notifications) use `context.getString(...)`.
  - Leave alone: `BillPdfGenerator` lines that sit in the English branch of an `isBn` check (the
    receipt language is chosen separately), log and exception text, and brand names.
  - Current matches (84):

```
ui/screens/LockScreen.kt:422: text = "Terminal Locked · Cash Register",
ui/screens/LockScreen.kt:462: text = "Enter your 4-digit cashier/owner PIN",
ui/screens/StoreCreditVoucherSlipDialog.kt:520: text = "Ready for Cashier Barcode Scanner",
ui/screens/StoreCreditVoucherSlipDialog.kt:545: text = "Rendering ${selectedRollWidth}mm thermal voucher...",
ui/screens/StoreCreditVoucherSlipDialog.kt:631: Toast.makeText(context, "Print failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
ui/screens/StoreCreditVoucherSlipDialog.kt:637: Toast.makeText(context, "Voucher slip not ready", Toast.LENGTH_SHORT).show()
ui/screens/StoreCreditVoucherSlipDialog.kt:792: text = "Send formatted voucher to customer's WhatsApp",
ui/screens/StoreCreditVoucherSlipDialog.kt:808: Toast.makeText(context, "Voucher slip image not ready", Toast.LENGTH_SHORT).show()
ui/screens/StoreCreditVoucherSlipDialog.kt:871: text = "Send voucher code and details via SMS app",
ui/screens/SalesHistoryScreen.kt:1957: title = "Invoice #${saleWithItems.sale.id}",
ui/viewmodel/POSViewModels.kt:3154: message = "Could not download backup file from Google Drive."
ui/viewmodel/POSViewModels.kt:3168: message = "Restore error: ${e.localizedMessage ?: "Unknown"}"
ui/viewmodel/POSViewModels.kt:4605: title = "Customer Registered",
ui/viewmodel/POSViewModels.kt:4606: description = "Registered as ${customer.tag} account",
ui/viewmodel/POSViewModels.kt:4618: title = "Invoice #${s.id}",
ui/viewmodel/POSViewModels.kt:4630: title = "Due Created: #${s.id}",
ui/viewmodel/POSViewModels.kt:4631: description = "Unpaid credit balance",
ui/viewmodel/POSViewModels.kt:4646: title = "Repayment: ${p.paymentMethod}",
ui/screens/CustomerDossierSheet.kt:996: text = "Invoice #${sale.id} • ${dtFormat.format(Date(sale.timestamp))}",
ui/screens/ReturnDialog.kt:258: text = "Return Items",
ui/screens/ReturnDialog.kt:298: text = "Due: ${formatTaka(remainingDue)}",
ui/screens/ReturnDialog.kt:430: text = "Return: $qtyStr",
ui/screens/ReturnDialog.kt:488: text = "Fully Returned",
ui/screens/ReturnDialog.kt:725: text = "Manage Presets",
ui/screens/ReturnDialog.kt:880: label = "Due Debt",
ui/screens/ReturnDialog.kt:888: label = "Digital / MFS",
ui/screens/ReturnDialog.kt:896: label = "Store Credit",
ui/screens/ReturnDialog.kt:1002: text = "Due Debt:",
ui/screens/ReturnDialog.kt:1050: text = "Return will deduct from customer's due ledger",
ui/screens/ReturnDialog.kt:1057: text = "Cash register drawer remains untouched (no cash payout)",
ui/screens/ReturnDialog.kt:1520: text = "Enter Return Quantity",
ui/screens/ReturnDialog.kt:1595: text = "All ($maxStr)",
ui/screens/SettingsScreen.kt:3060: title = "Crash Logs",
ui/screens/SettingsScreen.kt:4116: Toast.makeText(context, "Only the shop owner can restore database backups", Toast.LENGTH_LONG).show()
ui/screens/SettingsScreen.kt:4122: Toast.makeText(context, "Testing Google Drive connection...", Toast.LENGTH_SHORT).show()
ui/screens/SettingsScreen.kt:4221: Toast.makeText(context, "Only the shop owner can restore database backups", Toast.LENGTH_LONG).show()
ui/screens/SettingsScreen.kt:4243: Toast.makeText(context, "Only the shop owner can restore database backups", Toast.LENGTH_LONG).show()
ui/screens/SettingsScreen.kt:4478: Toast.makeText(context, "Only the shop owner can restore database backups", Toast.LENGTH_LONG).show()
ui/screens/SettingsScreen.kt:4679: Toast.makeText(context, "Only the shop owner can perform a factory reset", Toast.LENGTH_LONG).show()
ui/components/AdvancedPosFeatures.kt:957: text = "Barcode: ${product.barcode}",
ui/dialogs/AddEditProductDialog.kt:256: Toast.makeText(context, "Prices and stock quantities cannot be negative", Toast.LENGTH_SHORT).show()
ui/dialogs/AddEditProductDialog.kt:1100: text = "Margin ${String.format(Locale.US, "%.1f", marginPct)}%",
ui/dialogs/AddEditProductDialog.kt:1113: text = "Markup ${String.format(Locale.US, "%.1f", markupPct)}%",
ui/screens/DuesScreen.kt:2167: text = "Exact Alarm Engine",
ui/screens/DuesScreen.kt:2443: text = "Auto SMS + WhatsApp Prompt",
ui/screens/DuesScreen.kt:4332: text = "Enter or Tap Preset",
ui/screens/DuesScreen.kt:4505: text = "Credit Limit: ${formatTaka(limitVal)}",
ui/screens/DuesScreen.kt:4618: text = "No limit",
ui/screens/SplashScreen.kt:187: text = "Enterprise POS • Secure Offline Engine",
ui/screens/RegisterCloseScreen.kt:468: text = "Shift active for ${shiftHours}h (>24h)",
ui/screens/RegisterCloseScreen.kt:474: text = "Recommend closing register for daily reconciliation.",
ui/screens/RegisterCloseScreen.kt:674: snackbarHostState.showSnackbar("Opening float is locked once transactions exist in the shift.")
ui/screens/RegisterCloseScreen.kt:763: text = "Shift Total: ${formatTaka(providerSales.cash + providerSales.bkash + providerSales.nagad + providerSales.rocket + provider
ui/screens/RegisterCloseScreen.kt:1021: snackbarHostState.showSnackbar("Physical cash counted: ${formatTaka(countedTotal)}")
ui/screens/RegisterCloseScreen.kt:1040: snackbarHostState.showSnackbar("Cash float added: +${formatTaka(amount)}")
ui/screens/RegisterCloseScreen.kt:1062: snackbarHostState.showSnackbar("Safe drop recorded: -${formatTaka(amount)}")
ui/screens/RegisterCloseScreen.kt:1078: snackbarHostState.showSnackbar("Opening float updated: ${formatTaka(newFloat)}")
ui/screens/RegisterCloseScreen.kt:1142: Toast.makeText(context, "Print error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
ui/screens/RegisterCloseScreen.kt:1226: message = "Use RegisterCloseScreen instead to match file name and navigation route",
ui/screens/RegisterCloseScreen.kt:2099: text = "Current in Drawer: ${formatTaka(currentDrawerCash)}",
ui/screens/RegisterCloseScreen.kt:2201: text = "Edit Opening Float (৳)",
ui/screens/RegisterCloseScreen.kt:2290: text = "Close Register & Audit",
ui/screens/RegisterCloseScreen.kt:2681: text = "Period: $rangeLabel\n" +
ui/screens/RegisterCloseScreen.kt:2935: Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
ui/components/SupervisorAuthDialog.kt:106: text = "Requires Manager or Owner",
utils/BillPdfGenerator.kt:1210: canvas.drawText("No payments recorded", 24f, y, paint)
utils/BillPdfGenerator.kt:1392: canvas.drawText("No payments recorded", 24f, y, paint)
ui/dialogs/ProductCodePrintDialogs.kt:859: Toast.makeText(context, "Failed to generate batch labels PDF", Toast.LENGTH_SHORT).show()
ui/screens/ScannerScreen.kt:2494: label = "Enter Code",
ui/screens/ScannerScreen.kt:2825: text = "Total: $totalAmountStr",
ui/components/OwnerProfitAnalyticsComponents.kt:142: text = "Invoice #${bill.saleId} · ${bill.items.size} SKUs",
ui/components/OwnerProfitAnalyticsComponents.kt:566: text = "Qty: $qStr · Sale: ${formatTaka(item.unitPrice)} · Cost: ${formatTaka(unitCost)}",
utils/AutoBackupWorker.kt:140: title = "Google Drive Reconnect Required",
utils/AutoBackupWorker.kt:141: message = "Google Drive authorization has expired or needs reconnecting. Please open Settings to re-authorize."
utils/AutoBackupWorker.kt:148: title = "Google Drive Auto-Backup Failed",
utils/AutoBackupWorker.kt:149: message = "Automatic backup upload to Google Drive could not complete. Check your connection or Google account."
utils/AutoBackupWorker.kt:220: description = "Alerts about failed or disconnected backups"
utils/BillActions.kt:286: Toast.makeText(context, "Print error: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
utils/BillActions.kt:556: clipboard?.setPrimaryClip(ClipData.newPlainText("Customer phone", normalizedPhone))
utils/BillActions.kt:668: Toast.makeText(context, "Phone copied to clipboard. WhatsApp not available.", Toast.LENGTH_SHORT).show()
utils/BillActions.kt:855: Toast.makeText(context, "Could not open share menu", Toast.LENGTH_SHORT).show()
utils/GoogleDriveBackupManager.kt:88: description = "Techyfi POS Encrypted Backup Package"
utils/StockAlertWorker.kt:73: description = "Daily alerts for low stock and expiring inventory"
utils/BackupPackageManager.kt:383: message = "Restore successful! Restored ${metadata.productCount} products & ${metadata.salesCount} sales records.",
```

### LOW

- [x] **N-2** The `receipt_paid_label_bn` / `receipt_due_balance_label_bn` keys in `values/strings.xml` are
  a workaround so the receipt language can differ from the app locale (CustomerDossierSheet.kt:1043/1050).
  That works, but check it is consistent with how `BillPdfGenerator` picks its receipt language, and
  use one mechanism for both.

- [x] **N-3** Run the instrumented tests on an emulator (`./gradlew :app:connectedDebugAndroidTest`),
  especially `BackupRestoreIntegrationTest` (the taxable round trip) and `AppDatabaseMigrationTest`
  (the 34→35 migration). They have not been run.

## Out of scope. Do not touch:
The working tree has many unrelated modified or deleted files (APKs, `.agents/`, schemas, tests).
Do not revert or commit them. Stage only the files you change.
