# Techyfi POS — Full App Audit Report
**Date:** 2026-09-15  
**Version audited:** V67 (versionCode 220943, DB schema v28)  
**Total findings:** 176  

This report is the output of a comprehensive code audit across all screens, dialogs, ViewModel, data layer, and DB entities. Issues are grouped by severity. Every finding includes the file path and approximate line number for direct navigation.

---

## 🔴 CRITICAL — 24 issues
*Security holes, silent data corruption, or features that are completely broken.*

| # | Issue | File : Line |
|---|---|---|
| C1 | **Wrong permission gates invoice-edit** — `canEdit` checks `RETURNS_DELETE_SALE`; any cashier who can delete sales can also re-open and modify invoices | `SalesHistoryScreen.kt:1663` |
| C2 | **UI mutates `MutableStateFlow.value` directly on ViewModel in ~7 places** — bypasses all business-logic guards, breaks testability | `CartScreen.kt:383,1141,1397–1503` |
| C3 | **Custom date range doesn't swap start/end** — if user picks end-date before start-date the query silently returns zero results | `SalesHistoryScreen.kt:101` |
| C4 | **Customers screen has no permission gate** — every other sensitive section (Reports, Expenses, Suppliers, Settings) is gated; customer PII is not | `Navigation.kt:532–535` |
| C5 | **৳0 checkout is allowed** — 100% discount brings `grandTotal` to 0.0 with no guard; a ৳0 sale completes silently | `CartScreen.kt` |
| C6 | **PIN change doesn't verify the old PIN** — anyone with device access can change the owner PIN | `SettingsScreen.kt` |
| C7 | **Lock screen dismissible with hardware Back button** on some Android versions — bypasses security entirely | `LockScreen.kt` |
| C8 | **No failed-PIN-attempt lockout** — brute force is unconstrained | `LockScreen.kt` |
| C9 | **Double-tap on "Confirm Payment" creates duplicate sales** — `completeSale()` exposes no loading/in-progress state to the UI | `POSViewModels.kt` |
| C10 | **Backup restore has no integrity check** — a corrupt `.posenc` file overwrites the live DB silently | `DataRepository.kt` |
| C11 | **Return does not restock inventory** — stock levels stay decremented after a return is processed | `ReturnDialog.kt` |
| C12 | **Loyalty points earned on a returned sale are not reversed** | `ReturnDialog.kt` / `POSViewModels.kt` |
| C13 | **Expired loyalty points are still redeemable in cart** — expiry date is stored but never checked at checkout | `LoyaltyScreen.kt` / `CartScreen.kt` |
| C14 | **`fillMaxWidth(1.25f)` in receipt zoom** — Compose clamps fraction to 1.0; zoom feature is silently non-functional | `ReceiptSummaryDialog.kt:674` |
| C15 | **`adjustStockQty` SQL always clamps to `MAX(0, …)`** — hard-coded SQL ignores the app's "Allow Negative Inventory" setting; businesses using overselling get silently wrong stock | `AppDaos.kt:26–27` |
| C16 | **Owner PIN shown in plain text** in credit-limit override dialog — no `PasswordVisualTransformation`; anyone at the counter can see it | `CartItemDialogs.kt:262–275` |
| C17 | **Auto-generated barcode has no uniqueness check** — `(100000000000..999999999999).random()` with no DB lookup; a collision silently overwrites an existing product | `AddEditProductDialog.kt:211` |
| C18 | **`recordDuePayment` queries include archived due accounts** — a payment can be silently applied to an archived (wrong) record instead of the active one | `AppDaos.kt:234–235` |
| C19 | **`Sale` entity missing `storeCreditApplied` column** — the store-credit portion of a split payment is not persisted; receipt regeneration and profit reports are wrong for any such sale | `AppEntities.kt:187–209` |
| C20 | **Delete product fires with no confirmation dialog** — calls `onDelete` immediately; cascades away sale-history references irreversibly | `AddEditProductDialog.kt:1487–1496` |
| C21 | **Cash In flow is completely inaccessible** — `showAddCashInDialog` state and dialog are wired, but no button in the UI ever sets it to `true` | `ExpensesScreen.kt:76,446` |
| C22 | **`SnackbarHost` not wired in `RegisterCloseScreen` Scaffold** — `snackbarHostState` created but not passed to Scaffold; all snackbars on that screen are silently swallowed | `RegisterCloseScreen.kt:153,267` |
| C23 | **`onAddNote` and `onAdjustCredit` are no-ops when dossier opened from LoyaltyScreen** — both callbacks default to `{ _, _ -> }` and LoyaltyScreen caller doesn't override them | `CustomerDossierSheet.kt:65–66` |
| C24 | **Return `paidRatio` can exceed 1.0** — if `grandTotal > subtotal` due to rounding, the ratio inflates and the refund amount is overcalculated | `ReturnDialog.kt:147–149` |

---

## 🟠 DATA & LOGIC BUGS — 37 issues
*Wrong behavior that silently corrupts data or produces incorrect results.*

| # | Issue | File : Line |
|---|---|---|
| D1 | **Customer with outstanding dues can be deleted** — orphans due records | `CustomersScreen.kt` |
| D2 | **Supplier with non-zero balance can be deleted** — balance lost permanently | `SuppliersScreen.kt:599` |
| D3 | **Supplier delete has no confirmation dialog** — fires immediately unlike every other destructive action | `SuppliersScreen.kt:599` |
| D4 | **Due delete doesn't check linked sale** — sale shows a balance that no longer exists in dues table | `DuesScreen.kt` |
| D5 | **Opening float not subtracted in register close** — over/short figure is wrong | `RegisterCloseScreen.kt:226–235` |
| D6 | **Starting float field accepts negative values** — corrupts expected cash calculation | `RegisterCloseScreen.kt:226–235` |
| D7 | **Duplicate SKU possible** — no uniqueness check when manually entering product codes | `AddEditProductDialog.kt` |
| D8 | **Two customers can share the same phone number** — no duplicate guard on add | `CustomersScreen.kt` |
| D9 | **No soft delete / void flag on sales** — no audit trail for voided transactions | `AppEntities.kt` |
| D10 | **Missing DB index on `saleDate`** — large datasets cause full table scans in all date-range queries | `AppDaos.kt` |
| D11 | **`getProductByBarcode()` returns only the first match** — a duplicate barcode makes the second product invisible to the scanner | `AppDaos.kt` |
| D12 | **Expense default category "Uncategorized"** — if that category is ever deleted, new expense creation fails | `POSViewModels.kt` |
| D13 | **`CustomerEntity` has no `updatedAt` timestamp** — changes can't be audited or synced | `AppEntities.kt` |
| D14 | **Loyalty points update runs in a separate coroutine after sale save** — an app crash between the two loses points with no reconciliation | `POSViewModels.kt` |
| D15 | **Unit spelling mismatch breaks fractional quantity** — dialog stores "Gram" but fractional-unit check looks for `"gm"`, `"g"`, `"gram"` (no capital-G match); fractional presets never trigger for Gram products | `AddEditProductDialog.kt:1108` vs `CartItemDialogs.kt:46` |
| D16 | **Held cart restoration shows empty-name / zero-price placeholder** when original product was deleted between hold and restore — cart total wrong, receipt shows blank product name, no user-facing error | `POSViewModels.kt:190–201` |
| D17 | **Note preset edit state tracked by list index, not value** — deleting another preset while edit dialog is open writes the save to the wrong preset | `CustomItemDialogs.kt:1862–1879` |
| D18 | **Quick sale preset delete commits to DataStore immediately** — every delete is permanent before "Done" is tapped; accidental deletes can't be recovered | `CustomItemDialogs.kt:1388–1392` |
| D19 | **Category duplicate name not checked** — two categories can share the same name, causing ambiguous filtering and wrong product counts | `ManageCategoriesDialog.kt:62–92` |
| D20 | **Paper roll width not persisted back to settings** — user's preferred selection is forgotten on next sale | `ReceiptSummaryDialog.kt:68,571–593` |
| D21 | **`decrementStockIfAvailable` return value not checked by callers** — a return of 0 (out-of-stock mid-transaction) is a silent failure; inventory can be over-decremented | `AppDaos.kt:29–31` |
| D22 | **`Product` has no `createdAt` timestamp** — "recently added" sort and new-arrivals reports are impossible | `AppEntities.kt` |
| D23 | **`selectedCategories` is `remember`, not `rememberSaveable`** — category filter resets on screen rotation while other filters survive | `ProductsScreen.kt:265` |
| D24 | **Expiry filter timestamps captured once at composition** — stale after midnight for "Expiring Soon" and "Expired" filters if screen stays open | `ProductsScreen.kt:228–229` |
| D25 | **Voided sale creates no audit log entry** | `SalesHistoryScreen.kt` |
| D26 | **`paidRatio` apportion logic in returns can exceed 1.0** — discount apportionment math not clamped | `ReturnDialog.kt:147–149` |
| D27 | **`collisionMergeCustomer` state declared but never shown** — suggests an incomplete merge-on-collision feature left half-built | `DuesScreen.kt:233` |
| D28 | **`shiftDurationMillis` computed once, never refreshed** — stale shift duration if screen stays open for hours | `RegisterCloseScreen.kt:170–172` |
| D29 | **ViewModel StateFlow mutated directly from LoyaltyScreen UI** — same anti-pattern as CartScreen, bypasses encapsulation | `LoyaltyScreen.kt:155,165,344` |
| D30 | **All core ViewModel state is publicly `MutableStateFlow`** — any composable can write to it, bypassing business-logic guards entirely | `POSViewModels.kt:251–305` |
| D31 | **`Supplier pay-due` dialog missing "Rocket" payment method** — present in `ReturnDialog` but omitted here | `SuppliersScreen.kt:1044` |
| D32 | **Supplier ledger capped at 20 rows with no "View All"** — older transactions silently hidden | `SuppliersScreen.kt:613,644` |
| D33 | **Supplier ledger shows no timestamps** — payment/invoice rows have no date; ledger is unauditable | `SuppliersScreen.kt:613–662` |
| D34 | **`adjustStockQty` ignores `allowNegativeInventory` setting** — `MAX(0, …)` SQL hard-capped regardless (also listed as C15 for severity) | `AppDaos.kt:26–27` |
| D35 | **`nowTimestamp` and `in30DaysTimestamp` captured once** — see D24 | `ProductsScreen.kt:228–229` |
| D36 | **MFG/PKG date field has no format validation** — only expiry date is parsed; a malformed manufacture date saves silently | `AddEditProductDialog.kt:1430–1441` |
| D37 | **`isBangla` toggle on voucher slip defaults to `false` regardless of app language** — Bangla-UI user gets English receipt content by default | `StoreCreditVoucherSlipDialog.kt:84` |

---

## 🟡 MISSING CORE FUNCTIONALITY — 26 issues
*Features that are entirely absent from the current implementation.*

| # | Issue | Screen / Component |
|---|---|---|
| F1 | Partial returns not supported — return dialog handles full sale only; can't return 1 of 3 items | `ReturnDialog.kt` |
| F2 | Partial due payments not supported — dues are all-or-nothing | `DuesScreen.kt` |
| F3 | Expense export missing — sales have Excel export; expenses have nothing | `ExpensesScreen.kt` |
| F4 | Stock audit history not viewable — audits written to DB, no UI to read them | `StockAuditDialogs.kt` |
| F5 | Register close report can't be printed (thermal or PDF) | `RegisterCloseScreen.kt` |
| F6 | Previous register closes inaccessible — no history of past close records | `RegisterCloseScreen.kt` |
| F7 | Scanner screen not in the navigation graph — back navigation from scanner is unreliable | `Navigation.kt` / `ScannerScreen.kt` |
| F8 | No torch/flashlight toggle on scanner screen | `ScannerScreen.kt` |
| F9 | No manual barcode entry fallback on scanner screen | `ScannerScreen.kt` |
| F10 | Bulk barcode label printing has no quantity selector — only prints one label at a time | `AdvancedPosFeatures.kt` |
| F11 | Supplier payment history/ledger not accessible with timestamps — balance shown but no transaction log | `SuppliersScreen.kt` |
| F12 | Products not linked to suppliers — no purchase order or "products by supplier" view | `SuppliersScreen.kt` |
| F13 | Loyalty points history per customer not viewable | `LoyaltyScreen.kt` |
| F14 | No loyalty report (points issued vs. redeemed, redemption rate) | `LoyaltyScreen.kt` |
| F15 | Recurring expenses not supported — monthly rent etc. must be re-entered manually | `ExpensesScreen.kt` |
| F16 | Void/return indication missing on receipts — no "VOID" or "RETURN" watermark | `ReceiptSummaryDialog.kt` |
| F17 | No test-print button in printer settings | `SettingsScreen.kt` |
| F18 | Denomination cash count at register close — only lump sum entry, no note-by-note breakdown | `RegisterCloseScreen.kt` |
| F19 | Sales by category not shown on register close screen | `RegisterCloseScreen.kt` |
| F20 | Store credit partial redemption not supported — if purchase < credit, excess is lost | `StoreCreditVoucherSlipDialog.kt` |
| F21 | Bulk loyalty points adjustment not available — can't run promotions that add points to all customers | `LoyaltyScreen.kt` |
| F22 | "Add New Category" shortcut missing inside Add Product dialog — must exit → Manage Categories → return | `AddEditProductDialog.kt` |
| F23 | Customer purchase history capped at 10 with no "View All" or pagination | `CustomerDossierSheet.kt` |
| F24 | Expense receipt attachment can't be previewed — icon shows, tap does nothing | `ExpensesScreen.kt` |
| F25 | Batch stock audit not available — one product at a time | `StockAuditDialogs.kt` |
| F26 | No "Return" quick-action from sales history list — must open detail view first | `SalesHistoryScreen.kt` |

---

## 🔵 UI / UX ISSUES — 57 issues

| # | Issue | File |
|---|---|---|
| U1 | "Checkout" button enabled on empty cart | `CartScreen.kt` |
| U2 | Overdue dues not visually highlighted (past due date, still unpaid) | `DuesScreen.kt` |
| U3 | No "Overdue Only" filter in dues | `DuesScreen.kt` |
| U4 | No date-range filter on expenses — must scroll to find a period | `ExpensesScreen.kt` |
| U5 | Expense screen shows no running total for current filter | `ExpensesScreen.kt` |
| U6 | Split payment shows no "change due" for cash portion | `CartScreen.kt` |
| U7 | Date picker allows future dates in sales report — always returns zero, confusing | `SalesReportScreen.kt` |
| U8 | Sales report export shows no progress indicator | `SalesReportScreen.kt` |
| U9 | No date filter reset button in sales history | `SalesHistoryScreen.kt` |
| U10 | Sale detail view doesn't show full split-payment breakdown | `SalesHistoryScreen.kt` |
| U11 | Empty state for filtered results says "No sales" not "No sales match your filters" | `SalesHistoryScreen.kt` |
| U12 | Payment method totals table has no Grand Total row | `SalesReportScreen.kt` |
| U13 | Category sales chart truncates long names with no tooltip | `SalesReportScreen.kt` |
| U14 | Profit report gives no disclaimer when cost price is missing — shows full revenue as profit | `SalesReportScreen.kt` |
| U15 | Customer search doesn't match phone numbers | `CustomersScreen.kt` |
| U16 | Due balance in customer dossier not highlighted red when non-zero | `CustomerDossierSheet.kt` |
| U17 | Call button shown on dossier even with no phone number — opens dialer with empty number | `CustomerDossierSheet.kt` |
| U18 | Average order value not shown in customer dossier | `CustomerDossierSheet.kt` |
| U19 | Most-purchased product not surfaced in customer dossier | `CustomerDossierSheet.kt` |
| U20 | WhatsApp reminder template not editable from customer view — must go to Settings | `CustomersScreen.kt` |
| U21 | Google Drive backup gives no success/failure feedback after trigger | `SettingsScreen.kt` |
| U22 | Invoice logo preview doesn't refresh after upload — need to reopen screen | `SettingsScreen.kt` |
| U23 | Language toggle doesn't prompt for app restart — partial locale change | `SettingsScreen.kt` |
| U24 | Auto-backup next scheduled time not displayed | `SettingsScreen.kt` |
| U25 | App version not shown in About/Settings | `SettingsScreen.kt` |
| U26 | Held cart list shows no creation timestamp — can't tell which hold is oldest | `AdvancedPosFeatures.kt` |
| U27 | Quick sale presets can't be reordered | `CartScreen.kt` |
| U28 | No discount confirmation chip/badge when discount is applied | `CartScreen.kt` |
| U29 | No warning shown when navigating away and holding the cart | `CartScreen.kt` |
| U30 | Loyalty points redemption has no max-spend validation against sale total | `CartScreen.kt` |
| U31 | WhatsApp share opens system chooser instead of launching WhatsApp directly | `ReceiptSummaryDialog.kt` |
| U32 | Receipt shows blank customer name instead of "Walk-in customer" | `ReceiptSummaryDialog.kt` |
| U33 | Thermal print failure shows no "Retry" button | `ReceiptSummaryDialog.kt` |
| U34 | Stock audit dialog doesn't show current recorded stock for reference | `StockAuditDialogs.kt` |
| U35 | Supplier search doesn't include address or notes fields | `SuppliersScreen.kt` |
| U36 | No WhatsApp quick-action on supplier contact | `SuppliersScreen.kt` |
| U37 | Loyalty tier badge not shown on customer card | `LoyaltyScreen.kt` |
| U38 | User cards don't show last login timestamp | `ManageUsersScreen.kt` |
| U39 | User activity log not viewable (who did what, when) | `ManageUsersScreen.kt` |
| U40 | Biometric (fingerprint) auth not offered on lock screen | `LockScreen.kt` |
| U41 | No PIN visibility toggle (eye icon) on lock screen | `LockScreen.kt` |
| U42 | Store credit voucher slip doesn't show expiry date | `StoreCreditVoucherSlipDialog.kt` |
| U43 | Voucher code has no copy-to-clipboard button | `StoreCreditVoucherSlipDialog.kt` |
| U44 | Return reason is free-text — no dropdown of standard reasons | `ReturnDialog.kt` |
| U45 | Store credit balance not shown before issuing new return credit | `ReturnDialog.kt` |
| U46 | Quick sale preset has no description/notes field | `AdvancedPosFeatures.kt` |
| U47 | PIN recovery has no self-service option — owner must reset manually | `ManageUsersScreen.kt` |
| U48 | Deleting a user doesn't check if they're currently active | `ManageUsersScreen.kt` |
| U49 | No search or filter on user list | `ManageUsersScreen.kt` |
| U50 | Empty state in ManageUsersScreen is text-only with no CTA | `ManageUsersScreen.kt` |
| U51 | No loading states anywhere — all screens rely on empty-state appearing first | All screens |
| U52 | `walkinMode` defaults to "REGISTER" with no explanation of what modes mean | `ReturnDialog.kt:167` |
| U53 | Settings sub-page navigation has no back-stack — system Back exits Settings entirely | `SettingsScreen.kt:84–96` |
| U54 | "Offline Terminal" badge is always green — not dynamic | `SettingsScreen.kt:149–170` |
| U55 | No loading spinner while receipt bitmap renders — raw text snaps to bitmap abruptly | `ReceiptSummaryDialog.kt:115–124` |
| U56 | Quantity field not auto-focused on ManualQuantityDialog open | `CartItemDialogs.kt:63–76` |
| U57 | `Add Member` dialog in Loyalty blocks on duplicate with no merge path | `LoyaltyScreen.kt:1235` |

---

## 🟣 DESIGN INCONSISTENCIES — 22 issues

| # | Issue | File |
|---|---|---|
| G1 | Taka symbol inconsistency — some places use "৳" prefix, others "Tk" suffix on same screen | `DuesScreen.kt` |
| G2 | Low-stock badge threshold uses `<=` in one place and `<` in another — 0-stock items sometimes labeled "Low Stock" | `ProductsScreen.kt` |
| G3 | Deleted product image shows blank instead of generic placeholder icon | `ProductsScreen.kt` |
| G4 | Payment method logo order is arbitrary — no consistent ordering rule | `CartScreen.kt` |
| G5 | Back-navigation inconsistency — some screens use `popBackStack()`, others `navigateUp()` | `Navigation.kt` |
| G6 | Date filter default resets to epoch (1970-01-01) instead of start of current month | `POSViewModels.kt` |
| G7 | Search bar doesn't dismiss soft keyboard on scroll | `ProductsScreen.kt` |
| G8 | Category filter shows brief flash of old results after clearing | `ProductsScreen.kt` |
| G9 | Unit field is free-text with no standardization — "kg", "Kg", "KG" are distinct entries | `AddEditProductDialog.kt` |
| G10 | Category color picker has no custom hex input — fixed palette only | `ManageCategoriesDialog.kt` |
| G11 | Cart item per-item discount doesn't update line total within dialog | `CartItemDialogs.kt` |
| G12 | Note character limit not enforced or counted in cart item note field | `CartItemDialogs.kt` |
| G13 | Stock audit entry point buried in FAB overflow — no shortcut from product card | `ProductsScreen.kt` |
| G14 | Barcode search is case-sensitive — scanned lowercase may not match stored uppercase | `ProductsScreen.kt` |
| G15 | Raw `Color(0xFF…)` hex literals used in ExpensesScreen, SuppliersScreen, LoyaltyScreen — won't adapt to dark mode | Multiple |
| G16 | DuesScreen.kt is 368 KB — IDE performance hazard; should be split into subfiles | `DuesScreen.kt` |
| G17 | `Switch` uses `Modifier.scale(0.85f)` — visually small but full touch target; visual/touch mismatch | `ManageUsersScreen.kt:846` |
| G18 | Emoji used directly in `Text` (⚠️, 💎, etc.) — rendering varies by device/Android version; no content descriptions | Multiple |
| G19 | `@Suppress("DEPRECATION")` on ClipboardManager with no comment on migration path | `StoreCreditVoucherSlipDialog.kt:77` |
| G20 | Shop name defaults to `"Techyfi POS"` in voucher slip if caller forgets to pass it | `StoreCreditVoucherSlipDialog.kt:69` |
| G21 | Logout dialog and cashier role labels use inline hardcoded Bangla strings instead of string resources | `Navigation.kt:561–594,824–835` |
| G22 | Deep-link handler only covers `"products"` destination — all other destinations silently discarded | `Navigation.kt:365` |

---

## ⚪ VALIDATION GAPS — 8 issues

| # | Issue | File |
|---|---|---|
| V1 | Tax/VAT field accepts values >100% | `AddEditProductDialog.kt` |
| V2 | Default cost margin field accepts negative values and >100% | `SettingsScreen.kt` |
| V3 | Product expiry date can be saved in the past without warning | `AddEditProductDialog.kt` |
| V4 | Custom item price accepts negative values — corrupts cart total | `CustomItemDialogs.kt` |
| V5 | Cart quantity edit doesn't validate against available stock | `CartItemDialogs.kt` |
| V6 | Product image upload has no file size or type validation | `AddEditProductDialog.kt` |
| V7 | Bulk price edit has no confirmation step — one misclick changes many products | `ProductsScreen.kt` |
| V8 | Custom item has no per-item tax toggle | `CustomItemDialogs.kt` |

---

## ⚡ PERFORMANCE — 2 issues

| # | Issue | File |
|---|---|---|
| P1 | Product search debounce is 500ms — feels sluggish; 300ms is standard for POS | `POSViewModels.kt` |
| P2 | Excel export loads all records into memory at once — OOM risk on large datasets | `DataRepository.kt` |

---

## 📋 ADDITIONAL CODE-LEVEL FINDINGS

| # | Issue | File : Line |
|---|---|---|
| X1 | **Duplicate import** `androidx.lifecycle.compose.collectAsStateWithLifecycle` imported twice | `CartScreen.kt:34,84` |
| X2 | **Duplicate wildcard imports** `androidx.compose.foundation.layout.*` and `Image` import | `ScannerScreen.kt:28,63` |
| X3 | **`paymentMethodDisplayName` and `expenseCategoryDisplayName` are `private`** — same mapping logic duplicated across screens instead of being shared | `AdvancedPosFeatures.kt:116–146` |
| X4 | **`TakePicturePreview` contract captures low-res thumbnails** (~128×96px) instead of full-res; product images are blurry at 54dp | `AddEditProductDialog.kt:184` |
| X5 | **`canEdit` uses wrong permission** — `RETURNS_DELETE_SALE` gates invoice edit (also C1) | `SalesHistoryScreen.kt:1663` |
| X6 | **Export-error prefix extracted via string-resource hack** — `trimEnd()` on empty-arg string is fragile | `CustomersScreen.kt:92` |
| X7 | **Cart `LazyColumn` key uses barcode** — custom items sharing empty/synthetic barcodes cause duplicate keys and rendering crashes | `CartScreen.kt:777` |
| X8 | **Hardcoded non-localised strings throughout** — CartScreen, ProductsScreen, SalesHistoryScreen, ReturnDialog, LoyaltyScreen dialogs, ManageUsersScreen, SuppliersScreen all have dozens of English literals not using `stringResource` | Multiple |
| X9 | **`Room` internal APIs used** (`useWriterConnection`, `immediateTransaction`) — not stable public API; may break on Room upgrades | `DataRepository.kt:44–47` |

---

## 🗺️ STALE MIND MAP ITEMS (APP_MIND_MAP.md)

These are inaccuracies in the documentation that could mislead future development:

1. **Version numbers wrong** — mind map shows versionCode `220907`, schema v25; actual is `220943`, schema v28
2. **"Store credit was removed" note is stale** — true before V29, but Store Credit was fully implemented in V29; note was never updated
3. **Migrations list stops at v26→v27** — the v27→v28 migration (`discrepancyReason` column on `register_closings`) is undocumented
4. **Product card redesign not shipped** — `product_card_redesign_preview.html` exists as a mockup but `ProductGridCard` and `ProductItemRow` composables have NOT been updated to match it
5. **Debug keystore used for production signing** — `~/.android/debug.keystore` is also the certificate registered with Google OAuth for Drive backup; switching to Play App Signing would silently break Drive backup for all installed copies

---

## RECOMMENDED FIX ORDER

### Batch 1 — Fix First (Silent Data Corruption)
1. **C19** — Add `storeCreditApplied` column to `Sale` entity (DB migration v28→v29)
2. **C15** — Fix `adjustStockQty` SQL to respect `allowNegativeInventory` setting
3. **C18** — Fix `recordDuePayment` to query only active due accounts
4. **C17** — Add uniqueness check before saving auto-generated barcode
5. **D15** — Unify unit spelling between dialog ("Gram") and fractional-quantity detector ("gm")

### Batch 2 — Security Fixes
6. **C6** — Require old PIN verification before PIN change
7. **C7** — Block hardware Back on lock screen
8. **C8** — Add failed-attempt lockout (e.g. 5 attempts → 30s delay)
9. **C16** — Add `PasswordVisualTransformation` to PIN field in credit-limit dialog
10. **C1** — Fix `canEdit` permission check in SalesHistoryScreen

### Batch 3 — Broken Core Features
11. **C21** — Wire Cash In button in ExpensesScreen
12. **C22** — Wire `SnackbarHost` in `RegisterCloseScreen` Scaffold
13. **C23** — Pass real `onAddNote`/`onAdjustCredit` callbacks from LoyaltyScreen to dossier
14. **C14** — Fix `fillMaxWidth(1.25f)` in receipt zoom (use `graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }`)
15. **C11** — Restore stock after return
16. **C12** — Deduct loyalty points on return

### Batch 4 — Data Integrity
17. **D1** — Block customer delete if outstanding dues exist
18. **D2 / D3** — Add confirmation + balance check before supplier delete
19. **C5** — Guard against ৳0 checkout
20. **C9** — Disable "Complete Sale" button while `isCompletingSale` is true
21. **D23** — Change `selectedCategories` to `rememberSaveable`
22. **D30** — Make all ViewModel `MutableStateFlow` private; expose via methods

### Batch 5 — UX Improvements
*(These are lower risk; can be done incrementally)*
- U1, U6, U7, U9, U28, U32, U41, U56, G1, G2, G14, V1–V8, P1

---

*End of audit report. 176 findings total.*
