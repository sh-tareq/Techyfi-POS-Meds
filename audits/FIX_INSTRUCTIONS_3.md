# Techyfi POS — Fix Instructions Round 3
**Date:** 2026-09-16  
**Branch to work on:** `fix/audit-batch-5` (continue on same branch)  
**Total items:** 6 (1 Critical · 1 High · 4 Medium)  

Read every section fully before touching any file. Fix in the order listed. After every section marked **[COMMIT]**, commit with the message shown. Do not squash commits. Do not implement anything not listed here.

---

## SECTION 1 — Critical: clearCart() loyalty state leak corrupts next sale

### Fix B3 — clearCart() does not reset loyalty fields
**File:** `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`  
**Function:** `clearCart()` (around line 1161)

**Problem:** After `completeSale()` calls `clearCart()`, five loyalty/customer fields are NOT reset:
- `_loyaltyCustomer` — stale value causes the next `completeSale()` to deduct loyalty points from the **previous** customer's account
- `_loyaltyDiscount` — stale discount silently reduces the next sale's `grandTotal`
- `_resolvedCustomer` — previous customer name/phone lingers in the cart UI
- `_customerSuggestions` — stale suggestions list
- `_isAutoFilledName` — stale flag

This also affects the "Edit Invoice" flow via `loadFromPastSale()`, which calls `clearCart()` before loading the historic sale.

**Fix:** Inside `clearCart()`, after the existing reset statements, add:
```kotlin
_loyaltyCustomer.value = null
_loyaltyDiscount.value = 0.0
_resolvedCustomer.value = null
_customerSuggestions.value = emptyList()
_isAutoFilledName.value = false
```
Place these five lines with the other state resets in `clearCart()`. Do NOT change any other logic in the function.

---

**[COMMIT]** `fix(viewmodel): reset loyalty and customer state in clearCart to prevent next-sale corruption`

---

## SECTION 2 — Medium (financial): Cash + Store Credit records amountPaid = 0 and shows wrong change

### Fix B6 — Non-split Store Credit path stores amountPaid = 0 and miscalculates change
**Files:**  
- `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt` around line 1273  
- `app/src/main/java/com/example/techyfipos/ui/screens/CartScreen.kt` around line 1875

**Problem:** When a cashier applies store credit via the SC slider (not a formal split payment, i.e. `isSplitPayment = false`), the ViewModel sets `methodToSave = "Split"` and builds a synthetic `splitPaymentJson`. But the branch at line 1273 that sums `amountPaid` checks `if (methodToSave == "Split")` and then sums `splitPayments.value` — which is **empty** because `isSplitPayment = false`. Result: `sale.amountPaid = 0.0` is written to the database for every such sale.

In CartScreen, `effectiveChangeAmt` subtracts `grandTotal` from `cashEntered`, ignoring the store credit already applied — so a ৳1000 order with ৳600 SC and ৳500 cash entered shows "SHORT BY ৳500" instead of "CHANGE ৳100".

**Fix 1 — ViewModel (`POSViewModels.kt`):**  
Find the branch (around line 1273) that determines `amountPaid`. Change the condition from:
```kotlin
if (methodToSave == "Split") {
    // sums splitPayments.value
}
```
To:
```kotlin
if (isSplitPayment.value && splitPayments.value.isNotEmpty()) {
    // sums splitPayments.value
}
```
This ensures the non-split SC path falls into the `else` branch and uses the correct single-payment `amountPaid` (cash entered or full amount).

**Fix 2 — CartScreen (`CartScreen.kt`):**  
Find the `effectiveChangeAmt` calculation (around line 1875). Replace:
```kotlin
val effectiveChangeAmt = (effectiveAmountPaid - grandTotal).coerceAtLeast(0.0)
```
With:
```kotlin
val remainingAfterSC = (grandTotal - storeCreditApplied).coerceAtLeast(0.0)
val effectiveChangeAmt = (effectiveAmountPaid - remainingAfterSC).coerceAtLeast(0.0)
```
Also find the "short by" threshold check (around line 2011) that compares against `grandTotal` and change it to compare against `remainingAfterSC` using the same variable.

---

**[COMMIT]** `fix(cart): correct amountPaid for non-split Store Credit sales and fix change/short calculation`

---

## SECTION 3 — High + Medium: Navigation permission + Receipt reprint

### Fix B1 — ExpensesScreen → RegisterClose blocked for managers with REPORTS_CLOSE_REGISTER
**File:** `app/src/main/java/com/example/techyfipos/Navigation.kt` around line 1184

**Problem:** The `onNavigateToDayStartClose` callback wired to ExpensesScreen's "View Drawer" banner checks only `isOwner`. Every other route to RegisterClose uses:
```kotlin
isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)
```
The ExpensesScreen path uses a narrower guard, blocking managers and supervisors who legitimately have the `REPORTS_CLOSE_REGISTER` permission.

**Fix:** Find the guard at line ~1184. Change:
```kotlin
if (isOwner) {
    onNavigateToDayStartClose()
} else {
    // toast: owner only
}
```
To:
```kotlin
if (isOwner || isSupervisor || activePermissions.contains(AppPermissions.REPORTS_CLOSE_REGISTER)) {
    onNavigateToDayStartClose()
} else {
    // toast: permission required
}
```
Update the toast text to match the generic permission-denied message used elsewhere in the app (not "Owner only").

---

### Fix B4 — Receipt reprint omits Store Credit line for pure Store Credit payment
**File:** `app/src/main/java/com/example/techyfipos/ui/screens/SalesHistoryScreen.kt`  
**Function:** `buildBillData()` around line 1812

**Problem:** `appliedSc` is only populated from `splitPaymentJson`. For a sale where `paymentMethod == "Store Credit"` (no split), `splitPaymentJson` is null, so `appliedSc` stays null. The correctly stored `sale.storeCreditApplied` value is never used as a fallback. The reprint receipt shows ৳0 store credit.

**Fix:** After the block that parses `splitPaymentJson` and populates `appliedSc`, add a fallback:
```kotlin
// Fallback for pure Store Credit payment (no split JSON)
if ((appliedSc == null || appliedSc == 0.0) &&
    saleWithItems.sale.paymentMethod.equals("Store Credit", ignoreCase = true)) {
    val sc = saleWithItems.sale.storeCreditApplied
    if (sc > 0.0) appliedSc = sc
}
```

---

**[COMMIT]** `fix(ux): fix RegisterClose nav permission for managers, fix Store Credit line in receipt reprint`

---

## SECTION 4 — Medium: Profit calculation and wholesale savings

### Fix B5 — calculateInvoiceProfit() leaks coroutine subscriptions and always uses fallback cost
**File:** `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`  
**Class:** `SalesViewModel` — `calculateInvoiceProfit()` around line 1809

**Problem:** The function calls:
```kotlin
repository.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
```
This creates a **new** upstream `StateFlow` subscription on every invocation. Because Room's cold Flow emits on a background thread, `.value` always reads the initial empty list — so `productMap` is always empty, `liveCost` is always null, and the fallback estimated cost is always used. Every call also leaves a dangling coroutine subscription in `viewModelScope`.

**Fix step 1:** Add a class-level property to `SalesViewModel`:
```kotlin
private val allProductsForProfit: StateFlow<List<Product>> =
    repository.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

**Fix step 2:** In `calculateInvoiceProfit()`, replace the inline `stateIn(...)` call with:
```kotlin
val productMap = allProductsForProfit.value.associateBy { it.barcode }
```

---

### Fix B2 — wholesaleSavings never passed to BillData — receipt wholesale banner is dead code
**Files:**  
- `app/src/main/java/com/example/techyfipos/ui/screens/CartScreen.kt` around lines 2377 and 2641

**Problem:** `priceModesSavings` (the wholesale discount total) is collected as state in CartScreen but neither BillData construction call passes it as `wholesaleSavings`. The "WHOLESALE SAVINGS" banner in `BillPdfGenerator.kt` is permanently blank for all wholesale sales.

There are two BillData construction sites in CartScreen:
1. Normal completion path (around line 2419)
2. Credit-limit override path (around line 2688)

**Fix:** In both BillData construction sites, add `wholesaleSavings` to the parameter list:

First, capture the value in the snapshot section of each path (just before the BillData block):
```kotlin
val snapshotWholesaleSavings = if (priceMode == PriceMode.WHOLESALE) priceModesSavings else 0.0
```

Then in the BillData(...) call, add:
```kotlin
wholesaleSavings = snapshotWholesaleSavings,
```

Apply this to both the normal completion path and the credit-limit override path.

---

**[COMMIT]** `fix(viewmodel+cart): fix profit calculation subscription leak, wire wholesaleSavings to receipt`

---

## Final Checklist Before Merging

- [ ] App compiles with no errors (`./gradlew assembleDebug`)
- [ ] `clearCart()` resets all 5 loyalty/customer fields
- [ ] After a completed sale, the cart shows no lingering customer name, phone, or discount
- [ ] Non-split Store Credit sale writes correct `amountPaid` to DB (verify with a test sale: apply SC slider, pay remaining cash, check sale record)
- [ ] Change display shows correct amount when SC is applied via slider
- [ ] Manager/supervisor with `REPORTS_CLOSE_REGISTER` can tap "View Drawer" from Expenses screen
- [ ] Reprint of a pure Store Credit sale shows the "Store Credit: ৳XXX" line
- [ ] `SalesViewModel.allProductsForProfit` is a class-level property; `calculateInvoiceProfit()` uses it
- [ ] Profit figures in Sales History show purchase-price-based cost (not estimated fallback) for recent products
- [ ] Wholesale sale receipt shows "WHOLESALE SAVINGS: ৳XXX" line when price mode is WHOLESALE

---

*End of fix instructions. 6 items across 4 sections, 4 commits.*
