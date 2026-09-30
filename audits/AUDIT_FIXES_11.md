# Audit Round 11 — Fix List
Generated: 2026-10-01

Paths are relative to `app/src/main/java/com/example/techyfipos/`. `repo` = `data/DataRepository.kt`,
`VM` = `ui/viewmodel/POSViewModels.kt`, `Cart` = `ui/screens/CartScreen.kt`. Line numbers are approximate.
Re-locate by content before editing.

## Verification of AUDIT_FIXES_10.md

| ID | Result |
|----|--------|
| N-1 84 hardcoded strings | PASS. The grep leaves only allowed items (deprecation message, clip label, Drive file description). en/bn have 2542 keys each, with no missing, duplicate or placeholder mismatches. |
| N-2 receipt `_bn` keys | Accepted. |
| N-3 instrumented tests | **NOT VERIFIED.** It is ticked, but no androidTest results exist anywhere under `app/build`. Treat it as not run. |
| Build | 51/51 unit tests pass (run at 01:12). `values-bn/strings.xml` was edited at 01:13, so rebuild. |
| File hygiene | N-2 and N-3 are duplicated at the bottom of AUDIT_FIXES_10.md. Harmless. |

---

## A. Money and stock flows

### HIGH
- [x] **A-1 Every checkout with a phone number wipes the customer's store credit.** In `repo` (about 1052-1072),
  `customerDao.upsertCustomer(Customer(...))` omits `storeCredit`, which defaults to 0.0. The upsert
  overwrites every column. *(Verified.)* Fix: build from `existingCust?.copy(...)`, or pass
  `storeCredit = existingCust?.storeCredit ?: 0.0`. Check other `Customer(` constructions in repo for the same bug.
- [x] **A-2 Loyalty redemption fails the sale.** (a) In `repo` (about 806), `payableBeforeLoyalty = subtotal - discount + taxAmount`
  always adds tax. With tax-inclusive VAT, every redemption throws "Loyalty redemption does not match the
  sale total" *(verified)*. Add tax only when `!taxInclusive`, matching VM (about 1249). (b) The VM 50%-cap path
  produces fractional discounts (VM about 552-557, 1257). Points get rounded, so the repo check fails (e.g. a ৳555 bill with
  400 pts gives ৳277.50, which rounds to 278 pts, which is more than 277.55). Floor the redemption to whole points × `pointCashValue`, and enforce
  `minRedeemPoints` in the VM.
- [x] **A-3 Split payment with a Due leg always throws.** In VM (about 1264), the cash guard checks `paymentMethod.value == "Cash"`,
  which stays "Cash" when split is on *(verified)*. Skip the guard when `isSplitPayment.value`.
- [x] **A-4 Split plus store credit/voucher always throws.** The split builder targets `grandTotal - storeCreditApplied`
  (Cart about 1525), but the saved split JSON (VM about 1207-1222) has no Store Credit row. The repo (about 771) requires the rows to sum to
  grandTotal. Add `{"method":"Store Credit","amount":…,"voucherCode":…}` in the split branch.
- [x] **A-5 A split cash leg that is overtendered passes the UI but fails the repo.** Cart (about 2468-2471) vs repo (about 771). Save the
  cash leg as the amount owed, and keep the tendered amount only for calculating change.
- [x] **A-6 Return vouchers can't be redeemed, and credit is double-deducted.** Voucher redemption sets `storeCreditApplied`
  (Cart about 1489). The repo (about 832-839) then requires a phone and `customer.storeCredit >= amount`, and (about 939-951) deducts from
  both the customer and the voucher. Walk-in vouchers (issued with no phone, ReturnDialog about 1385) always fail. Treat
  voucher tender separately: decrement only the voucher and don't touch `customer.storeCredit`.
- [x] **A-7 Void/edit and delete sale don't refund store credit or voucher balance.** In repo (about 500-617) there is no
  storeCredit/voucher reversal *(verified)*. Add the credit back plus a `SALE_VOID` ledger entry, and restore voucher
  balances from the split JSON.

### MEDIUM
- [x] **A-8 Returns on tax-exclusive sales under-refund VAT.** The refund ratio `grandTotal/subtotal` is clamped to ≤1.0
  (repo about 3479-3482, ReturnDialog about 147-148). ৳1000+15% = ৳1150, but a full return refunds ৳1000. Remove the clamp, or add tax back per taxable line.
- [x] **A-9 Void-for-edit rebuilds the cart at current retail prices.** In VM (about 581-609), `clearCart()` resets priceMode, and the
  discount, wholesale mode and loyalty are not restored. Restore priceMode, discount and the sold unit prices.
- [x] **A-10 Resuming a held cart silently discards the current cart.** VM (about 787), called from Cart (about 2780) and ScannerScreen (about 512).
  Confirm first, or auto-hold the current cart.

### LOW
- [ ] **A-11** The Due + store credit credit-limit check and confirm dialog use the full `grandTotal`, not
  `grandTotal - storeCreditApplied` (Cart about 2524-2525, 2214).
- [ ] **A-12** The next shift's opening float resets to 0 on close (repo about 3958). Carry over the counted cash left in the drawer, or prompt for it.

### Half-built features (decide: implement or hide the setting)
- [ ] **A-13** `pointsExpiryDays` is saved (LoyaltyScreen about 1332) but never read. Points never expire.
- [ ] **A-14** Loyalty tiers never auto-upgrade. The thresholds are only used for the progress UI (LoyaltyScreen about 1700). `addPoints`
  (AppDaos about 501) never changes the tier.
- [ ] **A-15** `processReturn` for a sale paid with Store Credit doesn't restore the credit. `cashRefundAmount` only counts the cash share.

---

## B. Data, backup, reports, security

### HIGH
- [x] **B-1 REPLACE restore doesn't clear `store_credit_vouchers`, `store_credit_ledger`, `sale_audit_logs` or
  `expense_audit_logs`.** See `clearBusinessTablesForRestore` in repo (about 4124) *(verified)*. Vouchers insert with ABORT
  (AppDaos about 701), so restoring the device's own backup throws and rolls back. Add the tables to the clear list.
- [x] **B-2 MERGE import double-counts and clobbers.** Only sales are de-duplicated. Returns (about 2586), due payments (about 2449),
  due charges (about 2561), expenses, suppliers (about 2663), ledger (about 2921) and loyalty transactions are re-inserted on every merge.
  Dues (about 2439), customers (about 2841) and loyalty (about 2756) are upserted over live balances. The `?: backupSaleId` fallback
  (about 2558, 2585, 2871) links orphans to unrelated local sales. De-dupe by natural key, skip rows whose parent is missing, and
  recompute balances from the ledgers.
- [x] **B-3 `adjustCustomerCredit` (repo about 121) has no permission check and no transaction** *(verified)*. The UI
  (CustomerDossierSheet about 79, 898) doesn't gate it either. A cashier can grant themselves credit and spend it. Require the owner or a new
  `CUSTOMERS_ADJUST_CREDIT` permission, and wrap the update and ledger insert in one transaction.
- [x] **B-4 Net profit differs between SalesReportScreen and the Z-report.** SalesReportScreen (about 183-186, 540) subtracts the
  auto-created "Supplier Payment" expenses (repo about 3828, 3921), which double-counts COGS. `buildReport` (VM about 1937) excludes them.
  Use `report.totalExpenses` on the screen.

### MEDIUM
- [ ] **B-5** The Z-report doesn't reconcile with Register Close. Returns are attributed to the original sale's date (VM about 1950)
  but the drawer uses the return's timestamp (RegisterCloseScreen about 195-198). Refunds are deducted from the original method even when refunded
  as store credit (VM about 2000). There are no refund or tax lines, "Gross Sales" is really net of refunds, and all Split sales fall into one bucket.
- [ ] **B-6** The report's loyalty discount sums raw points, not points × `pointCashValue` (VM about 2160). Store the Taka amount on Sale.
- [ ] **B-7** Tax-inclusive profit is detected only when `subtotal == grandTotal` (VM about 2078), so any discount counts VAT as profit.
  Non-taxable items are also divided by the tax rate.
- [ ] **B-8 Credit-limit bypass.** `migrateCustomerPhone` (about 1370, no check), `upsertCustomer` (about 1251, only CUSTOMERS_EDIT)
  and `mergeCustomers` (about 1300, no check) all write `creditLimit`, getting around the owner-only `updateCustomerCreditLimit` (about 619).
- [ ] **B-9 The PIN lockout is weak** (repo about 3316-3322). It is a flat 30s after 5 failures with no escalation, it uses the wall clock (so changing
  the device time bypasses it), and each guess tests every user's PIN. Escalate the lockout, use `SystemClock.elapsedRealtime` plus a persisted counter,
  and cap the attempts.
- [ ] **B-10 POST_NOTIFICATIONS is only requested in the Dues flow** (DuesScreen about 1807, 2560). On Android 13+, stock alerts
  (StockAlertWorker about 113) and backup-failure alerts (AutoBackupWorker about 245) are silently dropped. Request it at first launch or in Settings.
- [ ] **B-11 Backup worker.** The one-time work has no network constraint (AutoBackupScheduler about 117-130), which causes false "failed"
  alerts. The needs-reauth state returns `Result.retry()` forever (AutoBackupWorker about 144) and alerts each time. Add a network
  constraint, and return `failure()` plus a single alert when reauth is needed.
- [ ] **B-12 Play policy.** The manifest declares `USE_EXACT_ALARM` and `SEND_SMS` *(verified)*. Both are restricted for this app category,
  so rejection is likely. Remove them: use `SCHEDULE_EXACT_ALARM` with the existing fallback, and send SMS through an intent.

### LOW
- [ ] **B-13** The due export omits `createdAt` (repo about 2088-2093).
- [ ] **B-14** `res/xml/file_paths.xml` exposes `files-path "."`, `cache-path "."` and `external-path "."`. Narrow it to `cache/reports`.
- [ ] **B-15** `exportBackup` (repo about 1986) has no repository-level permission check.
- [ ] **B-16** The dashboard's today sales/profit (VM about 1881-1930) ignore refunds, discounts and tax.
- [ ] **B-17** Missed one-shot due reminders are deleted without being sent or logged (DueReminderScheduler about 247).
- [ ] **B-18 Privacy.** A real shop backup (642 sales, 25 due customers) is tracked in git:
  `Backup Files/techyfi-pos-backup-2026-09-02.json` *(verified)*. **Ask the user before changing it.** Recommend
  `git rm --cached` plus a .gitignore entry. It stays in history unless the history is rewritten.

---

## C. UI/UX and design consistency

### HIGH
- [x] **C-1 Typed text is invisible in dark mode.** Text fields sit on a hard-coded white container but use the theme's
  near-white text: AddEditProductDialog (about 1377/1403), ManageCategoriesDialog (about 186-235), DuesScreen (about 2096-2112).
  Set `focusedTextColor`/`unfocusedTextColor`, or use `colorScheme.surface`. Also grep
  `unfocusedContainerColor = Color.White` in `ui` for other cases.
- [x] **C-2 The tablet terminal clears the cart and removes items with no confirmation and no permission or supervisor check.**
  ScannerScreen about 1106 and 1439 (clear), about 1138 and 1484 (remove) *(verified)*. Reuse the phone-cart confirm and supervisor flow
  (Cart about 684-700, 933-940). The clear button at about 1106 is also `.size(44.dp)`. Make it 48.
- [x] **C-3 Local restore** (SettingsScreen about 4224-4256): `openInputStream` has no try/catch, the dialog dismisses
  instantly with no progress, and "Replace all data" is one tap next to Merge with no Cancel *(verified)*. Add try/catch, a blocking
  progress dialog, a Cancel button and a second confirmation for REPLACE.
- [x] **C-4 Local backup** (SettingsScreen about 3528-3549) has no progress or busy flag, unlike Drive backup. Add one.
- [x] **C-5 Product save allows a double submit** (AddEditProductDialog about 1584; ProductsScreen about 2676, 2784). Add `isSaving`
  and disable the button while saving.

### MEDIUM
- [ ] **C-6** None of the 27 custom `Dialog(` form dialogs set `dismissOnClickOutside = false`, so an outside tap loses the input.
  The worst case is the product form. Only ManageUsersScreen sets it. Find them with `grep -rnE "^\s*Dialog\(" ui`.
- [ ] **C-7** The theme is incomplete (`ui/theme/Theme.kt` about 9-32). `primaryContainer` (57 uses), `secondaryContainer` and light
  `surfaceVariant` (206 uses) fall back to Material purple. Define the full brand container and variant set.
- [ ] **C-8** ProductsScreen (about 4803) and ProductCodePrintDialogs (about 75) use `isSystemInDarkTheme()`, which ignores the in-app
  Light/Dark setting. Use the theme's value, as CartScreen (about 191) does.
- [ ] **C-9** Bangla digits are only partly applied. `formatTaka` (utils/AppUtils about 37, 356 uses) is always Latin. Only
  Cart, SalesReport and Dues convert, and 53 places concatenate "৳" by hand. Create a single locale-aware `formatMoney()` and use it
  everywhere. Also unify the three different is-Bangla checks.
- [ ] **C-10** Seven different date formats for the same kind of date (e.g. `dd MMM yyyy, hh:mm a` vs `MMM d, yyyy • h:mm a`).
  Add shared formatters.
- [ ] **C-11** Tablet layout: Cart, Expenses, Loyalty, ManageUsers, Suppliers and SalesHistory are stretched phone layouts. Add
  `widthIn(max = …)` or a two-pane layout, as Customers, Products and Dues have.
- [ ] **C-12** There's no confirmation for clearing or deleting reminder logs (DuesScreen about 3326, 3413) or clearing crash logs (SettingsScreen about 3156).
- [ ] **C-13** Dark mode, general: there are about 2,800 hard-coded `Color(0x…)` values and 553 `Color.White`. Worst files:
  StockCheckComponents (506), DuesScreen (220), ProductsScreen (182), AddEditProductDialog (118), RegisterCloseScreen (104),
  ReturnDialog (99). Do C-1 and C-7 first. Then move the most-used literals into theme tokens, file by file.
  This is a longer project: do one screen per commit.

### LOW
- [ ] **C-14** The password-visibility toggles (SettingsScreen about 4876, 4982) have `contentDescription = null`.
- [ ] **C-15** 154 texts are under 10.sp, including key figures such as stock and recovery labels at 8.5sp (ProductsScreen about 1233,
  DuesScreen about 3787, 890-900, 2283). Find them with `grep -rnE "fontSize = [89](\.5)?\.sp" ui`. Raise key numbers to 11sp or more.
- [ ] **C-16** Backup and export file names use `Locale.getDefault()`, which gives Bangla digits in file names (SettingsScreen about 4098-4195,
  ExpensesScreen about 177). Use `Locale.US`.
- [ ] **C-17** The register closing history (RegisterCloseScreen about 991) uses `items(closings)` with no key and no date filter.
- [ ] **C-18** The Z/X report PDFs (utils/BillPdfGenerator `generateZReport*`, `generateXReport*`) are English-only, with 22
  hardcoded `drawText` labels, while receipts support a language setting. Add a language parameter.

## Suggested order
A-1, A-3, A-2, A-6, A-4, A-5, A-7 → B-1, B-3, B-4 → C-2, C-3, C-1 → the other HIGH items → MEDIUM → LOW.
Commit each item (or small group) separately, with a test for A-1 to A-7 where practical.

## Out of scope. Do not touch:
The working tree has many unrelated modified or deleted files (APKs, `.agents/`, schemas, tests). Do not revert or commit them.
Stage only the files you change. Don't rewrite git history.
