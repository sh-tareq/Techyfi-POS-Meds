# Techyfi POS ⚡

A modern, offline-first mobile Point of Sale (POS) and inventory management system built with Kotlin and Jetpack Compose, tailored for retail stores and commercial operations in Bangladesh.

---

## 🌟 Key Features

* **Instant Barcode Scanning**:
  * Unified camera scanning and simultaneous hardware scanner input (USB, Bluetooth, terminal laser guns).
  * Centralized duplicate-scan protection and live cart update feedback.
  * Rapid "Stock Check Mode" with live inventory adjustments via numeric keypad.

* **Multi-Tier Role-Based Access Control (RBAC)**:
  * 3 standard roles: **Admin / Owner**, **Store Manager**, and **Frontline Cashier**.
  * 28 granular functional permissions across 8 operational categories.
  * Modal **Supervisor PIN Elevation** allowing managers/owners to authorize privileged cashier actions on the spot.
  * Account active/suspended flag with repository-level security enforcement.

* **Bilingual Thermal Receipt & Voucher Engine**:
  * Scoped language switching: toggling **[ EN | বাং ]** updates *strictly* the receipt slip canvas without altering the app interface.
  * 1:1 structural twin Bangla receipts with natural Bangladeshi commercial phrasing (`চালান নং`, `আইটেম`, `উপমোট`, `সর্বমোট`, `নগদ`, `বকেয়া`).
  * Adaptive layout formatting for both **80mm** and **58mm** thermal receipt rolls.
  * Android Print Service integration and direct image/PDF sharing.

* **Flexible Checkout & Split Payments**:
  * Multi-method split payments: Cash, Card, bKash, Nagad, Rocket, and Due ledger.
  * Smart Cash Tender quick-add denomination chips (`⚡ Exact`, `+৳50`, `+৳100`, `+৳500`, `+৳1000`).
  * Real-time Change Due vs Shortage Due calculation.
  * Tiered price modes: Sale, MRP, and Wholesale/Bulk.
  * Ad-hoc Quick Sale custom items with 3D animated category icons and ৳0 complimentary sample toggle.

* **Customer CRM & Due Ledger**:
  * 360° Customer Profile (order history, top products, due ledger, loyalty points).
  * Customer Due Aging Analysis (Current, 1–30d, 31–60d, 61–90d, 90d+).
  * WorkManager scheduled due reminders with SMS and WhatsApp deep-linking.
  * Due Repayment Vouchers with printable thermal slips.

* **Cash Drawer & Register Shift Closing**:
  * Daily/shift cash reconciliation with physical note counting.
  * Discrepancy reason audit logging.
  * Mid-shift **X-Report** and end-of-day **Z-Report** printable P&L summaries (gross sales, gross margin, discounts, expenses, net profit).

* **Offline-First Resilience & Encrypted Cloud Backup**:
  * Guaranteed 100% offline functionality for daily operations.
  * Atomic Room database transactions with conditional stock reservation to prevent overselling.
  * Google Drive encrypted backups (AES-256-GCM with PBKDF2 key derivation).
  * Automated pre-restore local snapshots with instant auto-rollback on failure.
  * Excel catalog import and export (Apache POI).

---

## 🛠️ Technology Stack

* **Language**: Kotlin 2.0+
* **UI Framework**: Jetpack Compose with Material 3
* **Architecture**: MVVM, Repository pattern, Kotlin Coroutines, and reactive `StateFlow`
* **Local Storage**: Room (SQLite bundled driver) & Jetpack DataStore Preferences
* **Background Tasks**: Android WorkManager
* **PDF & Thermal Printing**: Android `PdfDocument`, `Canvas`, and bitmap ESC/POS generation
* **Security**: PBKDF2 password hashing, AES-256-GCM encrypted backup archives
* **Excel Processing**: Apache POI / XMLBeans

---

## 📖 Architecture & Mind Map

For exhaustive documentation on data models, business rules, migration history, and primary code mappings, see [`APP_MIND_MAP.md`](./APP_MIND_MAP.md).

---

## 📱 Build & Installation

### Prerequisites
* Android Studio Ladybug / Meerkat or Android CLI
* JDK 17
* Android SDK (API 24 to 36)

### Run & Build
```bash
# Assemble debug APK
./gradlew assembleDebug

# Assemble optimized release APK with R8 minification
./gradlew assembleRelease

# Install directly to connected device / emulator
adb install -r app/build/outputs/apk/release/app-release.apk
```
