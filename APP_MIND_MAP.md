# Techyfi POS — App Mind Map

Last updated: 2026-09-11 (app version 2.6.33-V11-09-2026, build `Techyfi-POS-Final-V11-09-2026.apk` / `Techyfi-POS-Final-V28.apk`; versionCode 220911; DB schema version 26)

This document is the durable project memory for the app's current structure, workflows, data model, and critical business rules.

```mermaid
mindmap
  root((Techyfi POS))
    App shell
      Splash screen
      PIN lock screen
        First run creates the owner account
          Owner name
          4-digit PIN with confirmation step
        Returning launch requires PIN
        Adaptive layout with width-constrained numpad (<= 380dp) & scroll-safe container (prevents bottom row cutoffs)
        Multi-Tier Role-Based Access Control (RBAC)
          Admin / Owner
            Full master access across all screens, ledgers, settings, and danger zone
            Can toggle on/off any of the 28 permissions per staff account
            Add, edit, deactivate, or delete staff members
          Store Manager
            Operational oversight: inventory, stock inwarding, suppliers, customer CRM, shift register closing, and P&L reports
            Acts as Supervisor for PIN-elevation authorization
          Frontline Cashier
            Streamlined POS checkout with safety guardrails
            Max discount limit (default 10%, supervisor PIN required for higher)
            Cost valuations, profit margins, danger zone, and settings locked
          Supervisor PIN Elevation
            Privileged cashier operations (discounts > cap, cart void, returns, credit limit bypass, negative inventory) prompt for Manager/Owner PIN elevation without switching session
            Scoped Single-Use Elevation Tokens (phone, maxAmount, cartHash, auto-expiry)
          Granular 28-Permission Matrix across 8 Categories
            Sales & Checkout, Returns & Refunds, Inventory & Catalog, Cash & Register, Customers & Dues, Suppliers, Staff & Security, System Settings
          Repository-enforced mutation security
            Mutations check authenticated user permissions and active status
            PINs must be unique across all accounts
        Terminal Security & LockScreen Integrity
          System back gesture intercepted via moveTaskToBack preventing terminal bypass
          Unauthenticated remote backup restore backdoor removed from MainActivity
          Strict PIN authentication enforced for database restore routines
          Low-stock notification deep-link route preserved across terminal lock and unlock
      Adaptive Layout Resolution Engine
        Breakpoints & Form Factor Detection
          Compact Phone (< 600dp)
          Medium/Expanded Tablet (>= 600dp, e.g. 10"-12" counter POS tablets)
        Policy Modes (Configurable in Settings)
          AUTO: Dynamic Responsive Detection (Default)
          TABLET: Force Tablet Mode (NavigationRail & Split Terminal)
          PHONE: Force Phone Mode (Bottom Navigation & Single Column)
        Orientation & Configuration Change Resilience
          android:screenOrientation="unspecified"
          Fluid runtime rotation between Portrait and Landscape without session or cart loss
      Dual Persistent Navigation Shells
        Phone Shell (< 600dp or Phone Mode)
          3D Animated Bottom Navigation (4 Tabs: Sell, Products, History, More Hub)
          More Operations Hub Bottom Sheet with Dual View Mode Switcher
            Interactive Material 3 Segmented Toggle: List View [ ☰ ] vs Grid View [ ⊞ ]
            Zero-Cutoff 2-Line Subheadings (11sp / 14sp line height, no ellipsis truncation)
            2-Column Compact Grid Tiles with Status Badges + 1-Column Full-Width Featured Settings Banner
            Full-Width Row Cards with 42dp Icon Container, Title, 2-Line Subtitle, Status Pill, and Chevron
            Scroll-safe layout with preserved view mode preference via rememberSaveable
        Tablet Shell (>= 600dp or Tablet Mode)
          Ergonomic Left-Docked NavigationRail (84dp Width, 8 Core Domains)
            Official App Logo Header & Outlet Home Shortcut (Electric T Mark)
            1. Sell Mode (Dual-Pane Split Cashier Terminal)
            2. Products Catalog & Inventory Valuation
            3. Sales History & Invoices Ledger
            4. Dues & Customer Khata
            5. Expenses & Petty Cash Disbursements
            6. Analytics & P&L Reports
            7. System Settings & Hardware Peripherals (3D Animated Category Icons & Isolated Subpage Scroll)
            8. More Operations Hub Dialog (House for Cash Register, Suppliers, Customer Loyalty, Customers & CRM)
              Interactive Material 3 Segmented Toggle: List View [ ☰ ] vs Grid View [ ⊞ ]
              Expanded 760dp Max-Width Dialog Constraint eliminating all title truncation
              2-Column Horizontal Cards in Grid Mode (354dp per card) with Zero-Cutoff 2-Line Subheadings
              Executive Full-Width Ledger Rows in List Mode with generous touch padding
              Persistent rememberSaveable view state & bilingual accessibility labels
      Bilingual Localization Engine & Strict Language Separation
        Zero Hardcoded Bilingual Slash Strings (Clean 100% English vs 100% Natural Retail Bangla)
        Android Resource-Backed Strings (res/values/strings.xml & res/values-bn/strings.xml)
        Navigation and permission denial toasts localized to natural retail Bangla
        Dynamic Runtime Switching via LocaleWrapper & LocalConfiguration
        Bengali Sans-Serif font rendering eliminates complex conjunct glyph clipping (যুক্তাক্ষর)
        PDF Invoice and X/Z report canvas headers use Typeface.SANS_SERIF preventing tofu glyphs
      Accessibility & Ergonomic Touch Architecture
        Minimum 48dp touch target sizing enforced across icon buttons, chips, and steppers
        High-contrast Material 3 surfaces adhering to WCAG AA guidelines
        Universal 3D Navigation Icon & Header Harmonization (`PosSectionHeader.kt`)
          Every top header across all 12 POS sections embeds the unified `PosTopAppBarTitle` with photorealistic 3D squircle badges:
            - Products: Realistic3DProductsBoxIcon (Indigo #4F46E5 badge, dynamic inventory SKU counter)
            - Customers & CRM: Realistic3DCustomerHubIcon (Violet #8B5CF6 badge, active customer counter)
            - Suppliers: Realistic3DSupplierTruckIcon (Teal #0D9488 badge, vendor & payment terms ledger)
            - Expenses: Realistic3DExpensesCashIcon (Rose #F43F5E badge, daily disbursements tracking)
            - Dues & Khata: Realistic3DClipboardDuesIcon (Red #EF4444 badge, debtor counts & receivables)
            - Sales Reports & Analytics: Realistic3DReportChartIcon (Sky Blue #0284C7 badge, revenue insights)
            - Sales History: Realistic3DHistoryClockIcon (Blue #3B82F6 badge, completed transactions ledger)
            - Cash Register: Realistic3DRegisterReceiptIcon (Amber #D97706 badge, shift reconciliation & Z-Report)
            - Cart & Checkout: Realistic3DSupermarketCartIcon / Realistic3DSellTerminalIcon (Emerald #10B981 badge)
            - Customer Loyalty: Realistic3DLoyaltyStarIcon (Gold #F59E0B badge, VIP tiers & rewards)
            - System Settings: Realistic3DSettingsGearIcon (Slate #64748B badge, POS hardware config)
            - Staff Management: Realistic3DCustomerHubIcon (Emerald #059669 badge, RBAC permissions)
            - More Operations Hub: Realistic3DMoreZapIcon (Indigo #6366F1 badge, management shortcuts)
        Ergonomic Responsive Header Typography & Zero-Cutoff Architecture
          Phone (<600dp, e.g. 360dp Galaxy A & 412dp Pixel):
            - 40dp Squircle Container (RoundedCornerShape(12.dp)) with 25dp Photorealistic 3D Canvas Icon
            - 18sp ExtraBold Title (lineHeight = 24.sp preventing Bengali ascender/descender clipping)
            - 11.5sp Medium Subtitle (lineHeight = (fontSize * 1.30f).sp) with concise single-line standard (subtitleMaxLines = 1, softWrap = false) and text-ellipsis fallback preventing vertical layout stretching and awkward orphan word wrapping (e.g. 'Petty cash & daily expenses', 'Management & financial ledgers', 'Shift float & day close audit')
            - Modifier.weight(1f, fill = false) to prevent collision with action buttons
          Tablet (>=600dp, e.g. 10"-12" POS Tablets):
            - 48dp Squircle Container (RoundedCornerShape(16.dp)) with 32dp Photorealistic 3D Canvas Icon
            - 22sp ExtraBold Title (lineHeight = 28.sp) and 13sp Subtitle
            - Prominent Inline Metric Pill Badge (RoundedCornerShape(50)) alongside title row
    Selling workflow
      Selling Form Factors
        Phone POS Terminal
          Top camera scanner viewfinder
          Manual barcode entry
          Category chips
          Sell vs Stock check mode
        Tablet POS Split Terminal (TabletSplitTerminal)
          Left Catalog & Scanner Pane (58% Width)
            Fast search bar & barcode toggle
            Category filter chips
            Adaptive 3-5 column product grid (135dp minSize)
            Live stock badges & tap-to-add haptic feedback
            Collapsible PIP Camera Preview (Torch & Pause controls)
          Right Live Cart Pane (42% Width)
            Pinned live cart with Realistic3DSupermarketCartIcon (24dp) header & items count badge
            Interactive line steppers ([-] qty [+]) & delete
            Hold Cart & Clear Cart actions
            Grand Total BDT (৳) summary banner
            Emerald Green Checkout CTA Button with Realistic3DSellTerminalIcon (22dp)
      Scanner Engine & Hardware Interception
        Camera barcode scan
        Simultaneous hardware scanner input without a source switch
          USB
          Bluetooth
          POS terminal laser gun
        Camera and hardware input share duplicate protection
          Hardware/USB/Bluetooth input completes a scan on Enter, or after a short idle pause for scanners with no terminator key configured
          Hardware scanner burst interception (<38ms inter-key delta) bypasses focused text input fields
          Duplicate-scan guard is centralized so Sell, Cart, and Products screens all benefit
        Manual barcode entry
        Sell mode
        Stock check mode
          Direct numeric stock quantity typing
          Numeric soft keyboard input alongside minus and plus buttons
        Low stock warning
        Product not found
          Same "Product Not Found" dialog used everywhere else in the app (Sell, Products, Cart)
          Add Product Now opens add product form with barcode prefilled
          Keep Scanning dismisses and resumes
      Cart review
        Cart badge shows total units
        Hardware barcode scanner adds or increments products
        Live scanned-product update shows quantity and running total
        Unknown products and stock limits show clear scan feedback
        Product Not Found dialog on unrecognized barcode
          Add Product Now opens Products screen with barcode prefilled
          Keep Scanning dismisses and resumes
          Same dialog (`StockCheckNotFoundDialog`) used by Sell and Products screens too — one shared design, no more per-screen bottom sheets
        No success snackbar when scanning/adding real products (only the inline scan-update banner); adding a Quick Sale custom item does show a confirmation Toast
        Increase or decrease quantity
          Fractional/decimal quantities for weight or volume units (Kg, Gm, Ltr, ml, Mtr and variants)
            0.25-unit steppers instead of whole-number steppers for those units
            Manual decimal entry dialog with quick-add buttons and quantity presets, values rounded to 2 decimals
            Mirrored in the Scanner screen's scan-and-adjust stepper
          Ergonomic 38dp Stepper Buttons with minimum interactive touch padding (>= 48dp target)
          Negative Inventory Policy: enforces stock sufficiency unless enabled in Settings or elevated by Supervisor PIN
        Enter quantity manually
        Remove product
        Clear cart
        Quick Sale (ad-hoc custom line item)
          Add Custom Quick Sale button (empty cart) and Quick Actions row (non-empty cart)
          4-Category Architecture with 3D Animated Category Icons
            Product (Realistic3DProductPackageIcon)
            Service (Realistic3DServiceWrenchIcon)
            Fee (Realistic3DDeliveryTruckIcon)
            Free Gift (Realistic3DSparkleGiftIcon)
          Synchronized Header Badge: dynamic 3D category icon in header reactively syncs with category selection
          100% Free Item / ৳0 Complimentary Toggle
            Dedicated toggle switch with emerald glow and live status description
            Enables free customer gifts, promotional samples, and complimentary services
            Automatically zeroes price and safely bypasses zero-price validation
          Symmetrical 52dp Input Architecture
            Standardized 52.dp container height across Item Description, Price, Quantity, and Note/Warranty
            Standardized RoundedCornerShape(14.dp) and subtle border styling across all input boxes
            Standardized 10.5.sp extra-bold uppercase section labels with 0.4.sp letter spacing
          Optimized Note / Warranty Field
            Unified 52.dp Surface box with leading 3D Holographic Warranty Shield (Realistic3DWarrantyShieldIcon)
            Single-line BasicTextField with clean text truncation/ellipsis and interactive clear button (×)
            Quick-select warranty & note preset chips (Complimentary Gift, 7 Days Replacement, 30 Days Free Service, No Warranty, etc.)
          Unit of Measure & Quantity System
            14 Unit presets (Pcs, Kg, Gm, Ltr, Mtr, Box, Service, Month, Year, Unit, Set, Pkt, Bundle, Pair)
            Quick Quantity chips (1 Pcs, 2 Pcs, 3 Pcs, 5 Pcs, 10 Pcs, +0.25, +0.50, +1.00)
          Live Subtotal Preview Card
            Real-time subtotal calculation with emerald glow indicator for free items
          One-tap Quick Sale Presets (editable, with reset-to-defaults)
          Adds an ephemeral synthetic Product (barcode `CUSTOM-<timestamp>`, stockQty 999999) — never written to the products table, does not affect real inventory, and never matches a prior quick-sale line by barcode
        Per-line item note (warranty/serial/instructions)
          Editable per cart line, printed on thermal receipts and PDF invoices
          Manageable Note Presets chip picker (add/edit/delete), also embedded in the Quick Sale dialog's note field
        Hold cart
          Hold current cart for later
          Held carts bottom sheet
          Restore held cart
            Restored quantity capped to current stock; lines that can't be restored (out of stock) are skipped from the active cart but remain saved in the held-cart record (unavailableLines count shown in a toast), not deleted
            Preserves Quick Sale items, line notes, price mode, prepared payment/split rows, and loyalty state
            Legacy array-only held-cart records remain compatible
          Delete held cart
        Continue to checkout
      Complete Sale
        Review items
        Price Mode selector (Sale / MRP / Bulk)
          Sale = selling price, MRP = product's MRP price (falls back to selling price), Bulk = wholesale price (falls back to selling price)
          Live-recomputes cart subtotal for every line item when switched
          Subtotal label and savings/premium chip adapt per mode
        Select cashier
        Apply discount
          Taka amount
          Percentage
        Select payment
          Cash
            Smart Cash Tender Quick-Add Denomination Chips
              ⚡ Exact (fills exact grand total in one tap)
              +৳50, +৳100, +৳500, +৳1000 increment chips
              ✕ Clear chip (appears when cash is entered)
            Real-time Change Due vs Shortage Due Calculator
              💵 Change ৳... (emerald green container when cash received >= grand total)
              ⚠️ Short ৳... (rose red container when cash received < grand total)
            Change printed on thermal receipts and PDF invoices
            Received amount manual input with Taka (৳) prefix
          Card
            Card brand
              Visa
              Mastercard
              American Express
              Other
            Last four digits
            Invoice text e.g. Visa 1234
          bKash
            Wallet mobile number
            Invoice text e.g. bKash 0167176376
          Nagad
            Wallet mobile number
            Invoice text e.g. Nagad 0167176376
          Rocket
            Wallet mobile number
            Invoice text e.g. Rocket 0167176376
          Due
            Customer name required
            Customer phone required
            Shows the customer's previous balance, this sale's new due, and resulting total before confirmation
            Full Due treats amount paid as zero; Split treats only non-Due allocations as paid, so confirmation and receipt balances match the ledger
            Credit-limit overage is blocked with a dedicated warning
          Store Credit
            Customer must be selected with positive credit balance (from returns or overpayments)
            Redeems balance directly against checkout total
            Displays available store credit and automatic balance deduction
          Split payment
            Combine two or more methods in one sale
              Cash
              Card
              bKash
              Nagad
              Rocket
              Due
            Per-method amount and wallet or card details
            Quick fill-remainder chips (Cash/bKash/Due) add a new split row for the outstanding balance in one tap
            Live balance bar shows collected total and remaining/settled/overpaid (change to return) status
        Customer loyalty
          Cashback or points balance shown at checkout
          Redeem toward sale total
          Redeemed points are deducted from the customer's balance on sale completion
        Customer details
          Optional for paid sales
          Required for due sales
          Full width name and phone fields
          Phonebook icon in phone field
          Contact picker fills name and phone
          Master Customer Profile lookup (name/due/loyalty autofill card) cancels any still-in-flight lookup before starting a new one, so a slower older keystroke can never overwrite a newer result with stale due/balance data
          Re-resolves fresh against the DB the moment Complete Sale opens, so a due collected or added elsewhere earlier in the session can't still show stale on the checkout screen
        Totals
          Subtotal
          Discount
          Tax
            Only shown when tax is enabled in Settings
            Applied to subtotal minus discount
          Grand total
          Change
      Complete transaction
        Validate payment details
        Atomic Room transaction
          Deduct product stock
          Save sale and line items
          Create or update customer due
          Redeem loyalty points
          Roll back every change if any step fails
        Clear saved cart
      Receipt & Post-Checkout Dialog (ReceiptSummaryDialog)
        Expanded Ergonomic Geometry
          Unconstrained Dialog (usePlatformDefaultWidth = false)
          Expanded to 95% screen width and 88% screen height with 26dp elevation card
        Transaction Summary Ribbon
          Total (bold green), Payment Method, Paid/Due status badge, and dynamic Change/Due
        Dual-Roll Format Engine & Interactive Viewport
          Live 80mm vs 58mm thermal roll toggle chips dynamically resizing canvas aspect ratio
          Generous 380dp–520dp scrollable contrast canvas (#0F172A container)
          Interactive Zoom Inspector (1.0x to 1.2x scale toggle)
          Continuous-roll thermal print layout without trailing page whitespace
        Ergonomic Action Deck
          Prominent full-width hero "Print (XX mm)" button (46dp height, emerald gradient)
          Secondary action row: Share (JPEG/PDF), Save, Add to Cart Again
          Tertiary overflow row: Edit (void & reload), Return Items, Delete, Done
        Add to Cart Again and Edit
          Add to Cart Again reloads the just-completed sale's line items into the cart without navigating away
          Edit (owner only) voids the just-completed sale — restoring stock, due balance, and loyalty points — then reloads its items into the cart for changes and re-checkout
          Both resolve the completed sale via SalesViewModel.salesWithItems by the sale ID captured on BillData at checkout time
        Receipt State Retention & Direct ESC/POS Driver
          Receipt dialog state and completedBillSnapshot hoisted into CartViewModel surviving screen rotation
          Direct Bluetooth ESC/POS SPP printing with automatic cash drawer kick and system spooler fallback
    Products & Inventory Architecture
      Executive Inventory Valuation & Stock Health Strip
        Real-time Total Selling Valuation & Purchase Cost
        Live Gross Margin Percentage (+28.4%)
        3-Color Segmented Stock Health Progress Bar (🟢 In Stock, 🟠 Low, 🔴 Out)
        Collapsible Details Drawer (In-Stock Units & Low/Out Stock Alerts)
        Inventory Alerts Center (InventoryAlertsCenterBottomSheet) — modal bottom sheet with 3 tabs (Expired / Expiring Soon / Low Stock); each item card has Edit and, for Low Stock, a one-tap +10 Quick Restock button
      Fast-Action Toolbar
        Integrated search input with clear button
        1-Tap 📷 Barcode Scanner toggle
        Single "Tune" filter icon opening a Filter & Sort bottom sheet (categories, 2×2 stock-health grid, date/expiry filters, 6 sort options, price range) — the toolbar row itself does not contain filter chips or category pills
        Dual View Switcher (☰ List vs ⊞ 2-Column Grid)
        Add Product is a separate floating action button, not part of the toolbar row
      High-Density Product Cards & Workflows
        Compact List View (ProductItemRow)
          3D Animated Thumbnail with real-time stock status indicator dot
          Bilingual Product Name, #SKU/Barcode monospace tag, Category tag
          Selling Price, Stock Status Pill (no purchase cost or margin tag shown)
          4 In-Row 1-Tap Action Buttons
            ➕ Cart (Immediate add to cart with badge increment)
            📦 ± Stock (Immediate quick stock adjustment modal)
            ⊞ Code (1-tap direct barcode/QR label generator)
            ⋮ More (Menu: Adjust Stock, Edit Product, Generate Code Image, Delete)
        Responsive 2-Column Grid View (ProductGridCard)
          Thumbnail with floating stock badge (🟢 In Stock, ⚠️ Low, 🚨 Out)
          Dual-line Title, Selling Price (no margin tag shown)
          3 Action Buttons (➕ Cart, 📦 ±, ⋮ More) — Code/label generation is accessed by tapping the SKU capsule, not a 4th action button
      Custom Category Manager (inside Add/Edit Product form)
        User-defined, icon + bilingual (English/Bengali) categories, editable and resettable to defaults
        Only shapes the dropdown offered when creating/editing a product
        Does not affect the Category Filter Pills Row, which is still derived live from whatever category strings already exist on products
      Generate Code Image (Barcode & QR Label Generator)
        1D Barcode (Code128/EAN) & 2D QR Code toggle
        Store Name branding (M/S AL KAWSAR STORE)
        Live visual thermal label preview
        Show/Hide Price, Code Number, Store Name, Date Tracking
        Mfg/Pkg & Expiry Date pickers
        Physical label dimensions (25×15, 30×20, 40×25, 45×35, 50×30, 60×40, 100×150mm, plus custom — 7 presets)
        Thermal label direct printing & high-resolution PNG/PDF image export
      Quick Stock Check & Audit Adjustments
        ±1 stepper buttons plus a chip row of +1/+5/+10/Set-to-0/Reset-to-original (no -5/-10 decrement chips), direct keyboard entry
        Date tracking info (Mfg/Pkg & Exp)
        Automatic Room InventoryAdjustment audit logging
      Product editing & Creation
        Live Pricing & Profit Intelligence (AddEditProductDialog)
          Real-time Unit Profit (৳)
          Gross Margin % = (Selling - Purchase) / Selling * 100
          Markup % = (Selling - Purchase) / Purchase * 100
          Real-time Loss Alert warning badge if Selling Price < Purchase Price
        Barcode changes use one Room transaction
        Duplicate target barcodes are rejected without deleting or changing the original product
        Success feedback appears only after the database update succeeds
      Photorealistic 3D Animated Canvas Icons
        Realistic3DInventoryBoxIcon (Floating isometric package box with tape reflection)
        Realistic3DBarcodeScannerIcon (Laser beam scan animation with viewfinder frame)
        Realistic3DAddProductIcon (Emerald cylinder with gold specular highlight and pulse)
        Realistic3DProductPackageIcon (Isometric product parcel with 3D ribbon and specular glow)
        Realistic3DServiceWrenchIcon (Rotating 3D chrome wrench and metallic gear)
        Realistic3DDeliveryTruckIcon (3D logistics delivery freight truck with spinning hubcaps)
        Realistic3DSparkleGiftIcon (Emerald & gold gift package with radiating sparkles)
        Realistic3DWarrantyShieldIcon (Holographic blue-indigo security shield with checkmark)
        Realistic3DCategoryIcon (Reactive category dispatcher rendering appropriate 3D canvas icon)
      High-Performance Image & Camera Engine
        ProductImageMemoryCache with LruCache memory pool (1/8 max heap) eliminating OOM
        Async produceState thumbnail loading with 256x256 RGB_565 downsampling
        CameraX analyzer executor lifecycle cleanup via DisposableEffect in Products & Scanner
    Sales history
      Compact transaction summary
        Est. Margin uses the same real gross-margin calculation as Sales reports
      Opens on All Time so restored transactions are immediately visible
      Quick periods
        Today
        This Week
        This Month
        This Year
        All Time
      Combined date range selector
      Search
        Invoice
        Customer
        Phone
        Product
        Barcode
        Payment method
      Advanced filters
        Payment method
        Saved or walk-in customer
        Minimum and maximum total
      Sorting
        Newest
        Highest total
        Lowest total
      Sale details
      Reprint or share receipt
      Delete sale
        Owner only
        Available inside protected detail flow
        Restore product stock
          For a partially returned sale, restore only sold quantity that was not already restocked by a return
        Adjust related customer due
          Also applies to the due portion of a Split payment sale
          Subtract only the remaining due after earlier returns already reconciled their proportional share
      Return items
        Owner only
        Select returned quantity per line item
          Plus/minus steppers or direct numeric entry, both capped at quantity sold minus already returned
        Caps at quantity sold minus already returned
        Refund method
          Cash
          Original method
          (Store credit option was removed — no ledger ever backed it)
          Only Cash Refunds cash-drawer accounting differs between methods; return/stock/due handling is otherwise identical
        Restocks returned quantity
        Refund total computed live
        Due-first settlement: reduces the customer's due balance before any cash refund when the original sale had a Due or Split-Due component, apportioned across sale lines by the sale's discount/tax paid-ratio
    Sales reports
      Quick periods and combined date range
      Period comparison
      Metrics
        Net sales
        Gross margin
          Selling price minus purchase price per item, net of returns
        Net profit
          Gross margin minus discounts given minus operational expenses in the period
          Supplier payments excluded from expense deduction to prevent COGS double-counting
          Visible line-item breakdown: Gross Margin, Discounts, Operating Expenses, Net Profit
        Transactions
        Average sale
        Items sold
        Discounts
        Margin percentage
          Net profit divided by net revenue
      Overview
        Sales and profit trend
        Best day
      Top products
        Expandable product breakdown
      Payment mix
      Hourly
        Peak-hour KPIs and a 24-hour bar chart
      Improved empty state
      CSV share and export
    Customer dues
      Owner-controlled mutations
        Owner can add due, collect payment, change credit limit, or delete ledger — "deleting" archives the due (isArchived) rather than cascading away its DuePayment history
        Cashier can search, inspect, call, remind, and share statements but cannot mutate balances
        Repository authorization protects every balance mutation even if called outside the screen UI
        Every write path normalizes the customer's phone (normalizePhone) before touching customer_dues/due_payments/due_charges/customers/customer_loyalty, so the same customer always resolves to one canonical ledger row regardless of how the phone was typed, autofilled, or imported
      Digital Khata Ledger — unified Transaction History (DuePayment + DueCharge)
        Two-sided ledger: DuePayment is the credit side (collections), DueCharge is the debit side (amounts that increased the balance)
          DueCharge reason "Sale" — auto-logged when a Due (or Split-Due) sale completes, linked to the sale via saleId
          DueCharge reason "Manual" — logged when the Add Due dialog is used to add a standalone charge; the dialog's Note field is saved onto the charge instead of being discarded
        Transaction History merges both sides by timestamp: collections shown in green (+৳), charges shown in red (−৳), each labeled "Due Sale" or "Due Added"
        Delete Payment (Owner only) — reverses a mistaken collection: removes the DuePayment row and adds the amount back to the outstanding balance
        Delete Charge (Owner only) — reverses a charge: removes the DueCharge row and subtracts the amount from the outstanding balance
          Deleting a "Due Added" (manual) charge is a full undo
          Deleting a "Due Sale" charge only undoes the ledger entry and its share of the balance — the underlying sale itself is untouched; the confirmation dialog points to Delete Sale (History screen) for a full undo (which also restocks items and reverses loyalty)
          Delete Sale (History screen) already reverses the due amount on its own; it also deletes any DueCharge row linked to that sale by saleId so history doesn't keep showing a charge for a sale that no longer exists
        due_charges table added in DB schema version 23; existing straight-Due sales were backfilled with charge rows on upgrade (Split-Due portions are not backfilled — JSON-encoded, not practical in plain SQL — but are logged for every Due/Split sale going forward)
      Due Aging Analysis Breakdown Header
        < 7 Days bucket (recent credit)
        7–30 Days bucket (intermediate credit)
        > 30d Overdue bucket (high-risk / urgent recovery)
        Color-coded status indicators and dividing lines for instant debtor risk prioritization
      Compact outstanding and collection summary
      Midnight Indigo Gradient Hero Card
        Customer Name, Phone, Outstanding Due banner
        Dynamic Status Pill (⚠️ Due Active / ✅ All Settled)
        Total Repaid and Credit Limit utilization percentage bar
      Balanced 4-Action Button Grid
        Collect Payment (Emerald Green)
        + Add Due (Indigo with Pre-filled Customer Name & Phone)
        Remind (Outlined with SendDueReminderDialog)
        Call (Outlined with System Phone Dialer & Clipboard Fallback)
      Send Due Reminder Dialog (SendDueReminderDialog)
        Dynamic Store Name Pre-fill (From SettingsDataStore.shopNameFlow, e.g. "My Shop")
        Instant vs Schedule mode switch
        Instant mode
        4-Way Language / Template Selector Segmented Bar
          বাংলা (Polite Bengali reminder template)
          English (Polite English reminder template)
          উভয় (Bilingual Bangla + English combined template)
          কাস্টম (Auto-activated when user edits text)
        User-Defined Custom Remind Text Multiline Editor (Character Counter & Store Badge)
        Multi-Channel Dispatch
          WhatsApp (Direct api.whatsapp.com deep link with 880 country code normalization)
          SMS (Direct smsto: composer intent)
          Share (System Share Sheet)
        Schedule mode
          Editable, reusable Due Reminder Templates with placeholder substitution (customer name, due amount, store name, bKash number, phone)
            Manage Templates dialog: create/edit/delete, reset to defaults
          Schedule presets: Tomorrow 10 AM, In 3 Days, Weekly, Custom (Custom uses an in-dialog +/- hour stepper, 1–720 hrs, default 12, that sets the real delay)
          Weekly reminders recur indefinitely via WorkManager PeriodicWork until the due is settled or deleted, at which point the worker cancels itself; each firing re-checks the live due amount and updates the message accordingly
          Delivery channel: WhatsApp, SMS, or Both
          WorkManager-based scheduling; only one pending scheduled reminder per customer phone number — scheduling a new one silently replaces the prior one
          Scheduling requests Android notification permission and, for SMS/Both, SMS permission at runtime
          SMS/Both auto-sends via SmsManager when SMS permission is granted; WhatsApp/Both posts a tap-through notification when notification permission is granted
          The worker safely skips a notification when permission was revoked after scheduling
          Pending reminders are persisted in Room, shown in a Scheduled Reminders sheet, and can be cancelled before firing
      Collect payment (RecordPaymentDialog)
        Dynamic percentage quick chips (25%, 50%, 75%, Full Balance)
        Prevent overpayment
        Payment method (Cash, bKash, Nagad, Rocket, Card)
        Optional reference or note
        Money Receipt & Due Repayment Voucher Redesign
          Thermal Printer Friendly 80mm (576px) vs 58mm (384px) dynamic roll layout
          Store Logo toggle in settings & thermal generators
          Live Interactive Thermal Receipt Slip Preview directly inside Settings with instant EN/বাংলা switcher
          Custom Section & Text toggle + editable Section Title & Multi-line Notice / Policy / Terms text
          Bilingual Money Receipt & Payment Voucher Generator (BillPdfGenerator.kt) with high-contrast monochrome thermal slip formatting
          Symmetrical 3-action buttons (Print Slip, Save JPEG, WhatsApp/SMS Share)
          Strict Language Decoupling (EN/বাং toggle strictly scopes to receipt slip template; dialog chrome and outer interface strictly adhere to global App Language)
          Zero-Lag Asynchronous Rendering (Dispatchers.IO offloading for instantaneous background thermal slip generation and decoding)
        Save every repayment separately and update outstanding balance
      Advanced Filter & Sort Modal Bottom Sheet (DuesFilterBottomSheet)
        Date Tracking (Sort by Date Newest / Oldest)
        Alphabetical Sorting (Name A to Z & Name Z to A)
        Minimum Due Presets (All Amounts, ≥ ৳500, ≥ ৳1,000, ≥ ৳2,000, ≥ ৳5,000)
        Custom Minimum Due Amount Input & Active Filter Badges
      Views (Outstanding, Overdue, Settled, Payments) — Overdue = outstanding balance untouched 14+ days or at/above 80% of credit limit
      Customer-Centric Search (Name or Phone only)
      Period Filter Chips (All Dates, Today, This Week, This Month, Custom Range)
      Itemized Digital Khata Statement Export & Share
      Protected ledger deletion (Owner only)
      Payment history date range & settled customer history
    Suppliers and inward stock
      Supplier directory and outstanding balance
      Multi-Supplier Quick Inward Vendor Picker
        Interactive vendor selector dialog with live search, phone, and outstanding dues
        Replaces arbitrary first-supplier selection with explicit cashier vendor choice
      Inward-stock invoice
        Select one or more existing products
        Capture decimal quantity and unit cost per product
        Invoice total is calculated from stock lines and may be adjusted for invoice-level charges
        One Room transaction saves invoice and line items, increases product stock, updates purchase cost, writes inventory adjustment audit, and updates supplier totals/due
      Supplier due payment
        Validates against outstanding balance
        Saves payment method and optional reference note in a durable payment ledger
        Balance reduction and payment record commit atomically
    Settings
      Owner only
        Sell tab shows a snackbar and blocks navigation for cashiers
      Categorized navigation
        Settings overview
        Invoice and receipt
        Business rules
          Tax and VAT
          Staff and PINs
        Backup and restore
        Import and export
        About
        Protected danger zone
      Business rules
        Tax and VAT
          Enable or disable charging tax on sales
          Custom tax label
          Tax rate percentage
          Rate applied to subtotal minus discount
        Staff and PINs
          Add staff accounts with name, unique 4-digit PIN, assigned role (Owner, Manager, Cashier), and max discount cap
          Role badges (Gold Admin/Owner, Purple Store Manager, Emerald Cashier)
          Active / Suspended toggle switch for instant access revocation without account deletion
          Granular Permissions Matrix modal: Customize all 28 permissions across 8 functional groups per user
          Duplicate PIN protection prevents PIN collisions
          Owner safety lock: cannot deactivate or delete the last remaining owner account
          Log out returns to the PIN lock screen
      Hardware and cash drawer
        Enable or disable automatic drawer kick for Cash and Split sales
        Select an already-paired Bluetooth thermal printer
        Requests Android Bluetooth Connect permission when required
        Test Kick sends the ESC/POS pulse through Bluetooth SPP
      Shop and invoice setup
        Shop name
        Address
        Phone
        Logo
        Facebook URL
        QR information
        Cashier name and presets
      Invoice customization
        Fonts and visible fields
        Payment status
        Customer information
        Custom information blocks
          Printed on every receipt output: text share, image, and PDF/print
        Money Receipt & Voucher Settings
          80mm vs 58mm roll width selector
          Shop logo on money receipt toggle
          Custom section title and multiline notes / terms editor
          Live Interactive Thermal Preview card with real-time English / Bengali preview toggle
        Return policy
      Language
        English
        Bengali
      Backup and restore
        Master Cloud Status Card
          Connection state badge
          Live backup timestamp
          Quick manual backup trigger
        AES-256-GCM Authenticated Encryption
          PBKDF2 key derivation from stable Google subject ID + a random per-backup salt
            Salt stored alongside the ciphertext, not fixed in source code
            Legacy fixed-salt backups still decrypt via automatic fallback
          Cross-device disaster recovery on replacement devices
          SHA-256 integrity checksums
        Google Drive Cloud Integration
          Isolated appDataFolder sandbox
          drive.appdata scope
          Pre-flight Google Drive connection testing with live diagnostic status
          Auto-backup failure alert notifications on dedicated channel ('techyfi_backup_alerts')
          Current Google Identity Services AuthorizationClient flow
            OpenID and email scopes identify the Google account
            Stable Google subject ID remains the encryption identity across devices
            Silent authorization refresh for automatic backups
            Interactive reauthorization deferred to Settings when required
          Retention pruning 7, 14, 30 backups limit
        Safe Restore with Auto-Rollback
          PRAGMA wal_checkpoint FULL consistency
          Automatic pre-restore emergency snapshot
          Atomic file rename swaps in the restored database, not a streamed copy
          Atomic rollback if restore validation fails
          Pre-restore metadata inspection modal
          After a full encrypted-database restore, the UI reload clears the Activity's ViewModelStore before calling activity.recreate()
            Plain recreate() alone is a config-change-style restart: the ViewModelStore survives it, so viewModel() just returns the pre-existing CartViewModel/SalesViewModel/etc still bound to the DataRepository that reloadRepositoryAfterDatabaseRestore() already closed and replaced
            Without the clear, every screen (Cart's post-checkout receipt and History's sale-detail dialog alike) silently keeps querying the orphaned closed database until the process is fully killed, which can present as "Add to Cart Again"/"Edit" (and other data-driven UI) disappearing after a restore
        WorkManager Automated Cloud Sync
          Configurable sync schedule
          Network policy Wi-Fi Unmetered vs Any Connected
        Local Backup & Restore
          Local JSON and encrypted SQLite backup
          Encrypted Standalone Vault (.posenc)
            Password-protected AES-256-GCM + PBKDF2WithHmacSHA256 standalone local backup export and import
            Secure binary envelope format ('POSENC1') with 128-bit tag and 12-byte IV
          Portable JSON restore normalizes every phone-keyed row on import (dues, duePayments, dueCharges, customerLoyalty, loyaltyTransactions, scheduledDueReminders, dueReminderLogs, customers) so re-importing an older backup can't reintroduce a split (raw-phone vs. normalized-phone) ledger — see Repair Duplicate Customer Ledgers in Data management for cleaning up ledgers already split before this fix
          Encrypted SQLite restore includes all 19 Room tables
            Products
            Sales and line items
            Dues, repayment history, and due-charge (added-due) history
            Returns and return items
            Users and held carts (encrypted SQLite package); portable JSON deliberately excludes authentication secrets
            Expenses and suppliers
            Purchase invoice stock lines and supplier payment ledger
            Inventory adjustment audit log
            Purchase invoices and register closings
            Customer loyalty
          Bundled modern SQLite engine on Android 7 and newer
            VACUUM INTO creates a consistent database snapshot
            Does not depend on the device manufacturer's SQLite version
          Local restore with backward-compatibility and rollback
          Local JSON Restore File (Replace/Merge) writes through Room DAOs on the same live database connection — no close/reopen, no ViewModelStore/recreate() involvement, so it isn't subject to the encrypted-restore staleness issue above
      Import and export
        Products Excel
          Re-importing an existing barcode preserves its image, wholesale price, unit, and mfg/expiry dates
          Per-row import failures are shown to the user in a dialog listing the specific reason (missing columns, blank barcode/name, invalid price), not only logged
        Sales Excel
        Dues Excel
      Data management
        Reset data
        Erase all data
        Acknowledgment required before factory reset
        Factory reset restores generic placeholder shop identity (not real seeded data) and revokes any connected Google Drive authorization
        Repair Duplicate Customer Ledgers (Owner only, Danger Zone)
          Preview-then-confirm tool: finds customer_dues/due_payments/due_charges/customers rows split across a raw (un-normalized) vs. normalized phone key for the same customer — a legacy data-corruption class fixed going forward by normalizing every write path, but pre-existing on upgrade
          Preview lists each affected customer, how many rows, and the resulting merged balance before anything is touched
          Merge sums outstanding balances into the canonical (normalized) phone, re-keys due_payments/due_charges onto it (re-keyed before the stale row is deleted, to satisfy the ON DELETE CASCADE foreign key), then deletes the stale row
          Idempotent — re-running after a clean merge reports "No Duplicate Ledgers Found"
      App help and setup information
      Diagnostics
        Keeps up to 10 local uncaught-crash reports
        About screen can inspect, share, or clear crash logs
    Register Close (Day Close)
      Owner only
      Today's sales summary
        Cash sales
        Digital sales
        Sales by Payment Method breakdown (bKash, Nagad, Rocket, Card, Due)
        Cash in (+ float)
        Cash out (expenses)
        Cash refunds (returns that took cash out of the drawer today)
        Expected system cash: starting float + cash sales + cash in - cash out - cash refunds
      Starting float entry
      Count Drawer & Close Register
        Denomination entry: ৳1000, ৳500, ৳100, ৳50, ৳20, coins
        Physical cash counted vs expected cash
        Matched, shortage, or surplus indicator
        Discrepancy Reason Note input field (captured and stored for audit trail whenever physical counted cash != expected system drawer cash)
        Optional notes
        Persists a RegisterClosing record on completion
      Closing history
        Date, expected vs counted, variance, cashier
      Z-Report (Daily Closing P&L)
        Read-only printable summary for today only
          Gross sales, gross margin, discounts, expenses, net profit, payment breakdown
        Print, save as JPEG, share
        Share format picker
          JPEG image
          PDF document
          CSV spreadsheet
        Moved out of Sales Report screen; lives only in Register Close now
      Mid-Shift X-Report
        Interim, non-final read-only summary of the current shift-in-progress
        Printable/saveable/shareable (JPEG/PDF/CSV) exactly like Z-Report but without closing the register
    Data architecture
      Presentation
        Jetpack Compose screens
        Navigation
        ViewModels
      Repository
        DataRepository
        Sale completion orchestration
          Atomic sale, loyalty, supplier purchase, and stock mutations
        Backup import and export
      Room database
        Product
          stockQty is REAL (Double), not Int — supports fractional quantities
          mrpPrice, discountedPrice — back the Price Mode selector (Sale/MRP/Bulk)
        Sale
          Tax rate and tax amount applied at sale time
        SaleItem
          quantity is REAL (Double)
          note — per-line item note (warranty/serial/instructions), printed on receipts
        CustomerDue
          creditLimit
          isArchived — "deleting" a due now archives it (soft-delete) rather than cascading away its DuePayment history
        DuePayment
          Payment method
          Reference note
        DueCharge
          The debit side of the due ledger, paired with DuePayment (credit side) for a full Transaction History statement
          reason — "Sale" (linked via saleId, auto-logged on Due/Split-Due sale completion) or "Manual" (from the Add Due dialog, note field included)
          FK to CustomerDue.customerPhone, ON DELETE CASCADE
        User
          Name
          PIN hash
          Role: Owner or Cashier
        Return
          Refund method
          refundAmount split into externalRefundAmount (cash/wallet actually paid back) and dueAdjustmentAmount (portion settled by reducing the sale's due)
          Return amounts are apportioned across sale lines by the sale's discount/tax paid-ratio; due is reduced due-first before any cash refund
        ReturnItem
          quantity is REAL (Double)
        InventoryAdjustment
          Audit log: productBarcode, previousStock, newStock, difference, reason, note, timestamp, cashierName
        HeldCart
          Held cart line items
          Held cart metadata
        Expense
        Supplier
        PurchaseInvoice
          Created by supplier stock intake
          Updates supplier totals and product stock atomically
        PurchaseInvoiceItem
          Product, quantity, and unit cost line captured for each inward-stock invoice
        SupplierPayment
          Amount, payment method, reference note, timestamp, and operator ledger
        ScheduledDueReminder
          One pending reminder-tracking row per normalized customer phone
          Stores customer, amount, channel, scheduled time, and fire time for list/cancel UI
        RegisterClosing
          Wired into the Register Close screen (More hub)
          cashRefunds — cash refunded during the shift
          shiftStartedAt — start time of the shift this closing settles, from RegisterShiftState
          Closing the register resets RegisterShiftState to start a fresh shift with float 0
        CustomerLoyalty
          isActive — soft-deactivation flag filtering the active-members list
        SplitPaymentEntry
        LoyaltyTransaction
          Append-only audit ledger of every point earn/redeem/void/manual-adjust event
          pointsChange, spendChange, type, reason, saleId, performedByName
          Every loyalty point change (sale-time earn/redeem, sale-deletion reversal, and manual owner adjustments) writes a row here in the same DB transaction as the balance update
        RegisterShiftState
          Singleton row (id=1) tracking the currently open shift's startedAt and startingFloat
          Consumed by Register Close to compute shiftStartedAt
        SaleAuditLog
          Immutable audit record of every voided or deleted sale: invoiceNo, cashierName, deletedAt, totalAmount, paymentMethod, reason
        ProductDao
        SaleDao
        CustomerDueDao
        UserDao
        ReturnDao
        HeldCartDao
        ExpenseDao
        SupplierDao
        PurchaseInvoiceDao
        RegisterClosingDao
        CustomerLoyaltyDao
        LoyaltyTransactionDao
        RegisterShiftStateDao
        ScheduledDueReminderDao
        DueReminderLogDao
        CustomerDao
        SaleAuditLogDao
        Database migrations
          Current schema version 25
          Migration 11→12: adds sale_items.note, customer_dues.creditLimit; creates inventory_adjustments table
          Migration 12→13: converts products.stockQty, sale_items.quantity, return_items.quantity from INTEGER to REAL (full table rebuild) — the DB-level enabler of fractional quantities
          Migration 13→14: adds products.mrpPrice and products.discountedPrice (default 0.0) — backs the Price Mode selector
          Migration 14→15: creates purchase_invoice_items and supplier_payments with supplier/invoice cascade relationships
          Migration 15→16: creates scheduled_due_reminders for persistent persistent-reminder tracking and cancellation
          Migration 17→18: adds returns.externalRefundAmount/dueAdjustmentAmount, backfilling legacy proportional due-reduction so upgrades from ≤17 don't double-apply it; new returns use due-first settlement instead
          Migration 18→19: adds register_closings.cashRefunds/shiftStartedAt, scheduled_due_reminders.messageText/isRecurring, customer_loyalty.isActive, customer_dues.isArchived; creates loyalty_transactions and register_shift_state
          Migration 19→20: creates due_reminder_logs for persistent SMS/WhatsApp reminder dispatch history
          Migration 20→21: creates unified customers CRM table with 3-way automatic sync from customer_dues, customer_loyalty, and past sales contacts
          Migration 21→22: upgrades users table with role, customPermissions JSON blob, isActive boolean flag, and maxDiscountPercent cap for multi-tier RBAC
          Migration 22→23: creates due_charges (the debit side of the due ledger); backfills charge rows for existing straight-Due sales (Split-Due portions, JSON-encoded on the sale, are not backfilled but are logged going forward)
          Migration 23→24: adds customers.storeCredit REAL NOT NULL DEFAULT 0.0; adds register_closings.bkashSales, nagadSales, rocketSales, cardSales, dueSales REAL NOT NULL DEFAULT 0.0
          Migration 24→25: creates sale_audit_logs table (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceNo TEXT NOT NULL, cashierName TEXT NOT NULL, deletedAt INTEGER NOT NULL, totalAmount REAL NOT NULL, paymentMethod TEXT NOT NULL, reason TEXT NOT NULL)
          Explicit migrations preserve existing business data
          BundledSQLiteDriver supplies consistent SQLite capabilities across supported Android versions
      Preferences
        SettingsDataStore
        Invoice settings (58mm / 80mm roll width, directBluetoothPrintEnabled, directBluetoothPrinterAddress, allowNegativeInventory, showDueReceiptVerificationQr, shop identity, logo, QR links, footer notes, showDueReceiptLogo, showDueReceiptCustomNotes, dueReceiptCustomTitle, dueReceiptCustomNotes)
        Shop settings
        CategoryItem list, QuickSalePreset list, DueTemplate list, Note presets list
          Plain Kotlin data classes with hand-rolled JSON (de)serialization, persisted as JSON strings inside Preferences DataStore — not Room tables
          Each has a seeded default list restored when the stored value is blank or corrupt
      Cart persistence
        Restore unfinished cart
          Restored quantity capped to current stock
        Save cart changes
    Device and external integrations
      Camera and barcode scanner
        ML Kit component registration protected from R8 full-mode shrinking
        Minified release camera initialization regression verified
      Contact picker
      Printing
        Android System Print service for every receipt (58mm and 80mm thermal roll sizing)
        Direct Bluetooth Raw ESC/POS SPP thermal raster printing (auto-reconnect, 58mm/80mm, paired device picker)
        Thermal receipt memory optimization (RGB_565 bitmap configuration + explicit recycling prevents OOM)
        100% Bangla numeral consistency on Bengali receipts and vouchers
        Wholesale customer savings breakdown banner
        Due Repayment Money Receipt verification QR code embedding
        Android System Print service for every product label
      Android Downloads
      File sharing
      QR generation
      Excel files
        EAN-13 barcodes and 11-digit Bangladeshi mobile numbers preserve leading zeros during parsing
        Apache POI and XMLBeans reflection metadata protected from R8
          Spreadsheet schemas
          Extended workbook properties
          Custom workbook properties
          Core workbook properties
      Google Drive
        Modern AuthorizationClient permission flow
        App-data-only Drive access
      WorkManager automatic backup
      WorkManager daily inventory & expiry alert worker (StockAlertWorker)
      Boot receiver (BootReceiver)
        On device boot, package replace, or quickboot, reschedules all automatic backup WorkManager jobs, stock alert worker, and runs an immediate backup if overdue
        Re-schedules every persisted due reminder
```

## Main workflows

1. **Standard sale:** scan or select product → review cart → continue to checkout → select payment and enter conditional details → complete sale → stock is reduced → receipt is generated.
2. **Unknown barcode:** scan barcode → product not found → open Products → add product with the barcode prefilled → return to selling.
3. **Stock check mode:** scan barcode or enter manual barcode → open stock check sheet → view product details and prices → adjust stock using `-` / `+` or type exact stock quantity directly via soft numeric keypad → update database instantly.
4. **Due sale:** select Due → enter customer name and phone → complete sale → create or update the customer's due balance.
5. **Repayment:** open Dues → select customer → record repayment → add a new `DuePayment` entry → retain all earlier repayment entries.
6. **Sale deletion:** delete a sale from History → restore its product quantities → reconcile any associated due → remove the sale.
7. **Google Drive encrypted backup:** Settings → Backup & Restore → current Android `AuthorizationClient` grants app-data-only access → trigger manual backup or WorkManager trigger → a consistent SQLite snapshot containing all 18 Room tables is packaged with summary-count `metadata.json`, `preferences.json`, product images, and logo → encrypted with AES-256-GCM (key derived via PBKDF2 from the stable Google subject ID and a random per-backup salt) → uploaded to Google Drive `appDataFolder` → excess backups pruned according to retention policy (7/14/30).
8. **Safe cloud restore with auto-rollback:** Settings → Backup & Restore → tap View Backups → select cloud backup → inspect table counts in confirmation dialog → app creates emergency `pre_restore_backup` snapshot → local database safely closed → backup decrypted, checksum verified, and unpacked → Room reopened → if any failure occurs, app instantly reverts to pre-restore snapshot.
9. **Printing:** tap Print on a receipt or product label → the app supplies a PDF to Android's print service → select any printer exposed by an installed Android print service. Product labels preserve the selected physical dimensions and zero margins.
10. **First app launch:** splash screen → no users exist yet → Lock screen prompts to create the owner account (name + 4-digit PIN, entered twice to confirm) → owner is signed in and lands on Sell.
11. **Return app launch:** splash screen → Lock screen requires an existing user's PIN → matching user is signed in and lands on Sell.
12. **Adding & Managing Staff:** Settings → Staff & PINs (owner only) → tap "+ Add Staff Account" → specify Name, unique 4-digit PIN, Role (`Admin / Owner`, `Store Manager`, `Frontline Cashier`), and Cashier Max Discount Cap → customize any of the 28 permissions via the Permissions Matrix dialog → staff member can sign in with their unique PIN.
13. **Sale return:** open a sale in History → Return items (Manager / Owner or Supervisor authorized) → choose returned quantity per line item, capped at quantity sold minus already returned → choose refund method → process return → stock is restocked and the refund is recorded against the sale.
14. **Hold and resume cart:** build a cart → Hold cart → cart is saved to `HeldCart` and cleared from the active session → later reopen the held carts sheet from Cart or Scanner → restore a held cart back into the active cart, or delete it.
15. **Split payment sale:** at checkout, select Split payment → allocate the total across two or more methods (Cash, Card, bKash, Nagad, Rocket, Due) with per-method amount and wallet/card details → complete sale → receipt itemizes each payment method and amount.
16. **Loyalty management and redemption:** More hub → Customer Loyalty → search/add members or adjust points. At checkout, view the customer's points balance → redeem some or all against the sale total. Purchases earn one point per ৳100; partial returns and sale deletion reverse the earned points and lifetime spend that no longer apply, while previously redeemed points are restored when a sale is deleted. Every point change (sale-time earn/redeem, sale-deletion reversal, manual owner adjustment) is recorded as an immutable `LoyaltyTransaction` audit row in the same DB transaction as the balance update.
17. **Register close (shift cash reconciliation):** More hub → Register Close (Manager / Owner) → review cash sales, digital sales, cash in/out, and cash refunds since today's latest close → enter starting float → Count Drawer & Close Register → enter physical denomination notes counted → app computes expected system cash and counted-vs-expected variance → complete closing → a `RegisterClosing` record is saved with the authenticated operator's name and appears in history. A later close begins immediately after the previous close instead of recounting the entire day. Closing resets the persisted `RegisterShiftState` singleton to start a fresh shift with float 0.
18. **Z-Report (daily closing P&L):** Register Close screen → tap Z-Report → view today's read-only gross sales, gross margin, discounts, expenses, net profit, and payment breakdown as a simulated thermal receipt → Print, Save as JPEG, or Share → Share opens a format picker (JPEG, PDF, or CSV) and hands off to the system share sheet.
19. **Mid-Shift X-Report:** Register Close screen → tap X-Report → same interim summary/print/save/share flow as Z-Report, but non-final and does not close the register — useful for a shift-in-progress checkpoint.
20. **Supervisor PIN Elevation:** When a frontline cashier attempts a privileged action (discount exceeding personal limit, cart void, returns/refunds, deleting items, credit limit override), a `SupervisorAuthDialog` modal is triggered. Any active Store Manager or Admin/Owner can enter their 4-digit PIN to authorize the single transaction without logging out the cashier or interrupting checkout.

## Critical business rules

- **Offline-First Guarantee:** POS operations (scanning, selling, checkout, inventory, dues, receipt printing) must never require an internet connection. Google Drive is utilized strictly for backup and restore.
- **Multi-Tier Role-Based Access Control (RBAC):**
  - **Admin / Owner:** Complete administrative sovereignty. Can customize all permissions across all roles, manage staff accounts, access system settings, cloud backups, and the danger zone.
  - **Store Manager:** Manages daily operations including inventory stock, inward purchase invoices, supplier ledgers, customer CRM, shift closings, and P&L analytics. Empowered to act as a Supervisor for frontline overrides.
  - **Frontline Cashier:** Focused frontline POS checkout. Sensitive cost prices and profit margins are masked; administrative and system configuration zones are locked.
  - **28 Granular Permissions Matrix:** Functional permissions divided across 8 categories (Sales & POS, Returns & Refunds, Inventory & Catalog, Cash & Register, Customers & Dues, Suppliers, Staff Administration, System Settings). Can be toggled per account by Admin.
  - **Supervisor Authorization Elevation:** Privileged cashier actions trigger a modal PIN elevation allowing any active Manager or Owner to authorize the action on the spot.
  - **Active / Suspended Flag:** Accounts can be suspended immediately without deletion, revoking login access while preserving transaction audit history.
- **Authenticated Encryption:** Cloud backups must be encrypted with AES-256-GCM using keys derived via PBKDF2 (100,000 iterations) from the stable Google account subject ID and a random per-backup salt to ensure cross-device restoration on replacement devices.
- **Database Consistency:** Database files must be flushed with `PRAGMA wal_checkpoint(FULL)` prior to packaging.
- **Rollback Guarantee:** An automated pre-restore backup snapshot must be taken prior to restoring; if restore validation fails, the database is rolled back automatically. These local safety-rollback snapshots (`app/files/safety_rollback/pre_restore_*.sqlite`) are bounded to the 3 most recent and 7 days old (`BackupPackageManager.MAX_ROLLBACK_SNAPSHOTS`/`MAX_ROLLBACK_AGE_MS`), pruned both before and after each restore — this is distinct from the cloud Drive retention policy below.
- **Storage Sandbox & Retention:** Backups are stored in Google Drive's hidden `appDataFolder` (`drive.appdata` scope) and automatically pruned to the selected retention limit (7, 14, or 30).
- **No Seeded Business Data in Defaults:** `InvoiceSettingsData()`'s default values (and every fallback that reads shop identity) must remain generic placeholders (e.g. "My Shop", blank address/phone/Facebook URL) — never a real shop's name, address, phone number, or social link. A previous version hardcoded one customer's actual business details as the class defaults and DataStore fallbacks, which meant every fresh install and every factory reset silently re-populated that customer's real data instead of a blank slate.
- **Factory Reset Must Revoke Cloud Auth:** `DataRepository.resetFactoryDefaults()` must call `GoogleDriveHelper.revoke()` for any connected Drive account before completing, in addition to clearing local preferences — otherwise Google's silent re-authorization (`authorizeWithoutUi`, run on every Settings screen load) reconnects the same account and re-persists its identity immediately after reset.
- Product stock is reserved with a conditional database update inside the sale transaction; checkout is rejected atomically if any real product lacks sufficient stock, so concurrent sales cannot oversell or clamp stock to zero.
- Deleting a completed sale restores its product stock.
- A Due payment method requires both customer name and phone number.
- Every due repayment is stored as a separate ledger entry; a new repayment must never overwrite an earlier one.
- bKash, Nagad, and Rocket require the corresponding wallet mobile number and present the number on receipts (e.g. `bKash (0167176376)`).
- Card payments require a card type and last four card digits and present them on receipts (e.g. `Visa (1234)`).
- Database migrations must preserve existing products, sales, dues, repayment history, expenses, suppliers, and payment metadata.
- An unfinished cart is persisted and restored after app restart.
- A PIN unlock is required every cold start; there is no persisted "stay signed in" session.
- The first user ever created becomes the Owner; only an Owner can add or remove staff, and the last remaining Owner account cannot be deleted or suspended.
- Tax is a single global rate configured in Settings; when enabled it is computed on (subtotal − discount) and stored on the `Sale` row (`taxRate`, `taxAmount`) at the moment of sale.
- A return can never exceed a line item's `quantity sold − quantity already returned` for that sale; returned stock is added back to the product immediately. Return quantity per line can be entered via +/− steppers or typed directly, both clamped to the same remaining-returnable cap.
- Refund method is Cash or Original method only (Store credit option was removed since no ledger ever backed it). Refund method only affects cash-drawer reconciliation, not the return/stock/due mechanics: a "Cash" refund, or an "Original method" refund against a sale that was originally paid in Cash, reduces today's reconciled drawer cash (`ExpenseViewModel.totalCashRefunds`, subtracted in `netDrawerCash` and in Register Close's Expected System Cash); "Original method" against a non-cash original sale never reduces drawer cash. Split-original sales are treated conservatively as non-cash-impacting on refund (no apportionment of the split's cash slice).
- A split payment sale's per-method amounts must sum exactly to the sale's grand total; a Due portion within a split still requires customer name and phone.
- Loyalty redemption can never exceed the customer's current points balance, reduces the amount otherwise due at checkout, and is deducted from the stored balance when the sale completes. Earned points and lifetime spend must be reduced proportionally when items are returned or the remaining sale is deleted.
- Holding a cart removes it from the active session without deducting stock; stock is only deducted when a held cart is restored and the sale is completed. A held cart preserves custom items, line notes, price/payment/split/loyalty preparation, and never restores more real-product units than are currently in stock.
- **Net Profit = Gross Margin − Discounts Given − Expenses** for the reporting period; Gross Margin is (selling price − purchase price) per item, net of returns. Expenses only include `EXPENSE`-type ledger entries, not `CASH_IN`.
- Deleting a sale or processing a return reconciles the customer's due balance for both plain Due sales and the Due portion of a Split payment sale.
- No two user accounts may share the same PIN; both account creation and cashier PIN updates reject a PIN already assigned to another user.
- Exceeding a customer's credit limit requires a verified Owner PIN and a short-lived, single-customer authorization that is consumed by the sale; limits stored in Customer Hub also apply to the customer's first Due sale.
- Customer Hub order count, lifetime spend, due balance, and loyalty values are rebuilt from the authoritative sales/returns/due/loyalty ledgers after returns, sale deletion, due payments, and manual loyalty mutations.
- Owner-only mutations (inventory/product changes, due and customer changes, returns/sale deletion, staff administration, settings, backup import, and factory reset) are checked against the signed-in session inside the repository layer, not only hidden in the UI.
- `DueReminderAlarmReceiver` is not exported and accepts only the app's explicit due-reminder action, so another installed app cannot invoke it to send an SMS.
- Cloud backup encryption uses a random salt generated per backup (not a fixed constant), stored alongside the ciphertext; restoring swaps in the database file via an atomic rename rather than a streamed copy.
- Local and encrypted backup snapshots use the bundled SQLite engine so `VACUUM INTO` is available consistently on every supported Android version, including Android 7 devices with older system SQLite builds.
- Every Room migration implements both `SupportSQLiteDatabase` and `SQLiteConnection`; configuring `BundledSQLiteDriver` makes Room call the latter exclusively. This keeps databases restored from older local or Google Drive backups migratable without data loss.
- Repository transactions use Room's driver-native `useWriterConnection` + `immediateTransaction` path. The Android-only `withTransaction` compatibility extension must not be used because it requires a `SupportSQLiteOpenHelper`, which is unavailable when `BundledSQLiteDriver` is configured.
- Minified releases must retain Apache POI/XMLBeans schema registries plus the spreadsheet, extended-property, custom-property, and core-property OOXML classes. XMLBeans resolves these names from generated metadata, so removing or renaming them causes release-only Excel export crashes.
- Sale completion, loyalty redemption, supplier stock intake, and related balance changes must run inside Room transactions so partial business mutations are rolled back on failure.
- Re-importing Products Excel for an existing barcode only overwrites name/category/prices/stock/reorder fields; image, wholesale price, unit, and mfg/expiry dates are preserved from the existing record.
- Every Excel export (Products, Sales, Dues, the import template) must build the full workbook into an in-memory `ByteArrayOutputStream` first, then copy the finished bytes to the destination stream in a single write + flush. Writing Apache POI's multi-step zip/XML output directly into the SAF-provided `OutputStream` from `CreateDocument` was observed intermittently producing a 0-byte file (roughly 3 of 4 attempts, reproduced on-device); building in memory first means the destination is only ever touched with the complete file.
- There is exactly one "Product Not Found" UI in the app: `StockCheckNotFoundDialog` (defined in `ProductsScreen.kt`, non-private so other screens can use it). It is used by Products (both stock-check-mode and normal add-to-cart-mode scanning), the Sell/Scanner screen (camera, hardware scanner, and manual "Enter Code" all route through it), and Cart. Previously each screen had its own bespoke not-found UI (an AlertDialog in Products, a separate `ProductNotFoundBottomSheet` in ScannerScreen, and Products' normal-mode scan silently auto-opened the add form with only a snackbar) — do not reintroduce a screen-specific variant; extend the shared dialog instead.
- Two distinct composables are both named `ZReportDialog`, both now defined in `RegisterCloseScreen.kt`: one overload (`data: SalesReportData, ...`) is the read-only printable P&L summary; the other (`expectedCash: Double, ...`, imported from `AdvancedPosFeatures.kt`) is the cash-count/denomination reconciliation dialog. They are unrelated despite the shared name — resolved by Kotlin overload matching, not by which file declares them — do not assume one calls the other.
- The P&L Z-Report in Register Close is always scoped to today only (no period picker); it is a different scope than the Sales Report screen's selectable date range.
- A day can have multiple `RegisterClosing` records for multiple shifts; each closing only includes activity after today's previous closing timestamp. There is no lock preventing further sales after a close.
- Expense and register-closing audit names are overwritten at repository entry with the authenticated owner's session name; UI/default `"Admin"` values cannot spoof the stored operator.
- A Quick Sale line item creates an ephemeral, non-persisted `Product` (barcode `CUSTOM-<timestamp>`, `stockQty = 999999`) that is never written to the `products` table; it never decrements real inventory, and two quick-sales with the same title are still distinct barcodes (different timestamps), so they will never match a prior sale's line item by barcode.
- Cash Drawer Kick uses Bluetooth SPP to write the ESC/POS pulse bytes to a paired thermal printer. Pairing itself remains in Android Bluetooth settings; the app only lists bonded devices and stores the selected MAC address.
- Fractional/decimal quantity support (`Product.stockQty`, `SaleItem.quantity`, `ReturnItem.quantity`, `CartItem.quantity` are all `Double`) is inferred purely from the product's `unit` string matching a fixed list (kg, gm/g, ltr/liter/litre, ml, mtr/meter/metre) everywhere it's used — there is no explicit `isFractional` flag on `Product`.
- Price Mode (Sale/MRP/Bulk) reads `Product.sellingPrice` / `mrpPrice` / `wholesalePrice` respectively, each falling back to `sellingPrice` when the tier-specific field is 0/unset.
- A Due Reminder can only have one pending scheduled instance per customer phone number at a time (WorkManager unique work keyed by normalized phone digits); scheduling a new one replaces the previous one. Pending reminders are mirrored in Room and can be listed/cancelled from Dues; the worker removes its tracking row after completion.
- The product grid/list card layouts have not been redesigned despite a `product_card_redesign_preview.html` mockup existing in the project root — treat that file as a design reference only, not as shipped UI, unless the actual `ProductGridCard`/`ProductItemRow` composables are verified to have changed.

## Current Android package

- Application ID: `com.techyfi.pos`
- Version code: `220907`
- Version name: `2.6.28-V06-09-2026`
- Upgrade rule: retain the same application ID and signing identity, and always increase the version code.
- Signing SHA-1: `D6:7B:BD:1C:48:92:D4:FC:7F:D5:1F:D1:CB:22:79:B5:6F:C8:AD:9C` (this is the project's default AGP debug keystore at `~/.android/debug.keystore` — intentionally reused as the production signing key for this app; do not regenerate or replace it, or updates will stop installing over the existing app). **Not suitable for a real Google Play release as-is** — a debug keystore with a universally-known password must never be the production signing key, and this same certificate's SHA-1 is what Google Cloud Console has registered for the Google Drive OAuth client (no explicit client ID in code — the Identity Services Authorization API resolves it by package name + this certificate). Enrolling in Play App Signing would re-sign the app with a different certificate and silently break Drive backup/restore for Play-installed copies unless that new certificate is also added to the OAuth client first.
- Database version: `25` (v15→16 reminder tracking, v16→17 immutable `purchasePriceAtSale` cost-snapshot for historical margin reports, v17→18 return refund-splitting/due-first settlement, v18→19 register-shift-state/loyalty-audit-trail/soft-delete, v19→20 due reminder log history, v20→21 unified customers CRM table, v21→22 users table RBAC roles/permissions/caps, and v24→25 `sale_audit_logs` table are explicitly tested)
- Current build: `Techyfi-POS-Final-V06-09-2026.apk` (versionCode 220907) — includes Store Operations & Tools (More Hub) dual view mode switcher (interactive Material 3 segmented List vs Grid toggle for both Phone and Tablet), zero-cutoff 2-line wrapped subheadings, 760dp expanded tablet dialog geometry, Settings 3D holographic animated category icons, isolated subpage scroll states, customer CRM dossier sheet, and dynamic loyalty settings.
- This is the project's only mind map and the single source of truth for architecture, workflows, limitations, business rules, release metadata, and verification history.

## Primary code map

- Navigation and app shell: `app/src/main/java/com/example/techyfipos/Navigation.kt` (includes phone bottom nav, tablet navigation rail, and `MoreOperationsHubSheet` with List/Grid toggle)
- Screens: `app/src/main/java/com/example/techyfipos/ui/screens/`
- ViewModels: `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`
- Repository: `app/src/main/java/com/example/techyfipos/data/DataRepository.kt`
- Room database, DAOs, and entities: `app/src/main/java/com/example/techyfipos/data/db/`
- Standardized money utilities: `app/src/main/java/com/example/techyfipos/utils/MoneyUtils.kt` (Banker's rounding `HALF_EVEN` & Bangladeshi Lakh-Crore digit grouping)
- Backup encryption engine: `app/src/main/java/com/example/techyfipos/utils/BackupEncryptionHelper.kt`
- Backup packaging & rollback manager: `app/src/main/java/com/example/techyfipos/utils/BackupPackageManager.kt`
- Google Drive API manager: `app/src/main/java/com/example/techyfipos/utils/GoogleDriveBackupManager.kt`
- Google authorization and Drive connection manager: `app/src/main/java/com/example/techyfipos/utils/GoogleDriveHelper.kt`
- Automatic backup WorkManager: `app/src/main/java/com/example/techyfipos/utils/AutoBackupWorker.kt`
- Backup scheduler: `app/src/main/java/com/example/techyfipos/utils/AutoBackupScheduler.kt`
- Receipt generation: `app/src/main/java/com/example/techyfipos/utils/BillPdfGenerator.kt` (standard 80mm thermal bills, 80mm/58mm thermal Money Receipt vouchers, and PDF invoice generators)
- Thermal printer architecture & byte formatting: `app/src/main/java/com/example/techyfipos/printer/`
- Excel import and export: `app/src/main/java/com/example/techyfipos/utils/ExcelHelper.kt`
- PIN lock / first-run setup: `app/src/main/java/com/example/techyfipos/ui/screens/LockScreen.kt`
- Staff management: `app/src/main/java/com/example/techyfipos/ui/screens/ManageUsersScreen.kt`
- Auth session/roles: `AuthViewModel` in `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`
- Expenses: `app/src/main/java/com/example/techyfipos/ui/screens/ExpensesScreen.kt`
- Suppliers: `app/src/main/java/com/example/techyfipos/ui/screens/SuppliersScreen.kt`
- Customer CRM & Khata Dossier: `app/src/main/java/com/example/techyfipos/ui/screens/CustomersScreen.kt` and `app/src/main/java/com/example/techyfipos/ui/screens/CustomerDossierSheet.kt`
- Hold cart, split payment, loyalty redemption UI: `app/src/main/java/com/example/techyfipos/ui/components/AdvancedPosFeatures.kt`
- Stock check mode components: `app/src/main/java/com/example/techyfipos/ui/components/StockCheckComponents.kt`
- Invoice/shop settings storage: `app/src/main/java/com/example/techyfipos/data/SettingsDataStore.kt`, `data/InvoiceSettings.kt` (includes `InvoiceSettingsData` with Money Receipt customization fields)
- Settings screen & live preview: `app/src/main/java/com/example/techyfipos/ui/screens/SettingsScreen.kt` (includes `MoneyReceiptVoucherSettings` composable with interactive thermal slip canvas)
- Settings 3D animated holographic icons: `app/src/main/java/com/example/techyfipos/ui/components/Settings3DIcons.kt`
- Loyalty program settings & calculator: `app/src/main/java/com/example/techyfipos/utils/LoyaltySettingsHelper.kt`
- Backup schema compatibility: `app/src/main/java/com/example/techyfipos/data/BackupCompatibility.kt`
- Hardware/USB/Bluetooth scanner input handling: `app/src/main/java/com/example/techyfipos/utils/HardwareBarcodeScanner.kt`
- Register Close (day-end cash reconciliation) screen, including both the cash-count and P&L Z-Report dialogs, the share-format picker, and CSV builder: `app/src/main/java/com/example/techyfipos/ui/screens/RegisterCloseScreen.kt`
- Cash-count Z-Report dialog UI (imported into Register Close): `ZReportDialog(expectedCash, ...)` in `app/src/main/java/com/example/techyfipos/ui/components/AdvancedPosFeatures.kt`
- Register closing persistence: `RegisterClosingViewModel` in `app/src/main/java/com/example/techyfipos/ui/viewmodel/POSViewModels.kt`, `RegisterClosingDao` in `app/src/main/java/com/example/techyfipos/data/db/AppDaos.kt`
- Loyalty management: `app/src/main/java/com/example/techyfipos/ui/screens/LoyaltyScreen.kt`, `LoyaltyViewModel` in `POSViewModels.kt`, and `CustomerLoyaltyDao` in `AppDaos.kt`
- Custom category data model: `app/src/main/java/com/example/techyfipos/data/CategoryItem.kt`; management UI (`ManageCategoriesDialog`) and dropdown are inside `ProductsScreen.kt`'s `AddEditProductDialog`
- Quick Sale preset data model: `app/src/main/java/com/example/techyfipos/data/QuickSalePreset.kt`; custom item dialog (`AddCustomItemDialog`), symmetrical 52dp layout, and preset manager (`ManageQuickSalePresetsDialog`) are in `CartScreen.kt`
- Photorealistic 3D animated canvas icons & Category Dispatcher: `Realistic3DProductPackageIcon`, `Realistic3DServiceWrenchIcon`, `Realistic3DDeliveryTruckIcon`, `Realistic3DSparkleGiftIcon`, `Realistic3DWarrantyShieldIcon`, `Realistic3DCategoryIcon`, `Realistic3DInventoryBoxIcon`, `Realistic3DBarcodeScannerIcon`, `Realistic3DAddProductIcon` in `app/src/main/java/com/example/techyfipos/ui/components/StockCheckComponents.kt`
- Due reminder template data model: `app/src/main/java/com/example/techyfipos/data/DueTemplate.kt`; manager (`ManageDueTemplatesDialog`) and the reworked `SendDueReminderDialog` are in `DuesScreen.kt`
- Scheduled due reminders: `app/src/main/java/com/example/techyfipos/utils/DueReminderScheduler.kt` (WorkManager scheduling, `SchedulePreset` enum), `app/src/main/java/com/example/techyfipos/utils/DueReminderWorker.kt` (`CoroutineWorker` — SMS send / WhatsApp-deeplink notification)
- Price Mode selector: `PriceModeSelector` composable in `app/src/main/java/com/example/techyfipos/ui/components/AdvancedPosFeatures.kt`; `PriceMode` enum and cart pricing logic in `POSViewModels.kt`'s `CartViewModel`

## V8 verification baseline

- 22 JVM unit tests passed.
- 20 Android emulator integration tests passed, including a real v9 → v11 bundled-driver restore that preserves sale history.
- Android lint completed with zero errors.
- Release APK verified as `com.techyfi.pos`, version `2.2.7` (`220834`).
- APK signing certificate matches the existing update/OAuth identity.
- Update installation and cold-start smoke test succeeded without an app crash.
- Minified release completed successfully and cold-started on the emulator with no AndroidRuntime crash.
- Actual minified APK Excel export completed without a crash and produced a nonzero `.xlsx` file (3,627 bytes); the earlier R8-only XMLBeans initializer and class-cast failures are covered by targeted keep rules.
- Actual minified APK completed a cash sale atomically through the bundled SQLite driver, reduced stock from 10 to 9, rendered invoice #1, and logged no AndroidRuntime crash.
- Actual minified APK opened the invoice PDF share chooser and Android Print Manager without crashing.
- Actual minified APK initialized the simultaneous camera/hardware scanner screen and opened the Google Drive account authorization chooser without crashing.
- Automatic local backup executed successfully in the background on the emulator and reported a verified backup at 9:05 AM.

## V30 release verification (2026-08-30)

- Due-sale regression tests passed: a full Due sale records zero paid and the full grand total as due; split payments count only non-Due allocations as paid.
- Receipt `Total Due` is rendered as separate left-aligned label and right-aligned amount columns for JPEG and PDF output.
- Debug JVM unit tests and Android lint completed successfully with zero lint errors.
- The minified dated release APK built successfully and was verified as `com.techyfi.pos`, version `2.6.3-2026.08.30` (`220876`).
- APK signer SHA-1 matches the existing update/OAuth identity, and `adb install -r` preserved the original emulator installation timestamp.
- The updated app cold-started to `MainActivity` and displayed the existing PIN lock screen with no fatal, Room verification, or migration error in the checked logs.

## Medium-priority remediation verification (2026-08-30)

- Held-cart versioned-payload tests cover full Quick Sale/note/checkout-state fidelity and backward compatibility with legacy array-only records.
- Room migration tests now exercise v14 through the current v17 schema, direct v16→v17 preservation, and restored v9→v17 history preservation.
- Eight targeted emulator tests passed: all database migration tests plus held-cart compatibility/shift-boundary tests.
- JVM unit tests, Android-test compilation, debug assembly, and Android lint completed successfully with zero lint errors.
- Release signing intentionally remains on the existing certificate so this build line can update installed copies. Replacing that certificate is a separate migration project requiring a new install identity (or Play App Signing migration) and matching Google Drive OAuth certificate registration.

## V30 final update APK verification (2026-08-30)

- Final artifact: `app/build/outputs/apk/final/Techyfi-POS-Final-V30-08-2026.apk`.
- Verified package `com.techyfi.pos`, version `2.6.4-2026.08.30`, versionCode `220877`.
- Verified signer SHA-1 `D6:7B:BD:1C:48:92:D4:FC:7F:D5:1F:D1:CB:22:79:B5:6F:C8:AD:9C`, matching the established update/OAuth identity.
- SHA-256: `285c18fcfe79b4be25f972a265024174297b0d57b91f33db49892c334b9987cc`.
- Emulator update test passed from versionCode `220837` via `adb install -r`; `firstInstallTime` remained unchanged, the updated app launched, and no Techyfi POS fatal crash appeared in the checked log.

## V31 update APK verification (2026-08-31)

- Final artifact: `app/build/outputs/apk/final/Techyfi-POS-Final-V31-08-2026.apk`, versionCode `220878`, versionName `2.6.5-2026.08.31`.
- Changes in this build:
  - `CartScreen.kt`'s post-checkout receipt dialog now wires `onAddToCartAgain`/`onEdit` (previously only the History sale-detail dialog had these); resolves the completed sale via `SalesViewModel.salesWithItems` by the `BillData.saleId` captured at checkout.
  - `SettingsScreen.kt`'s Google Drive encrypted-restore UI reload now clears the Activity's `ViewModelStore` before calling `activity.recreate()`, so ViewModels are rebuilt against the freshly restored `DataRepository` instead of continuing to reference the closed pre-restore one.
- Verified live on emulator: completed a fresh sale → both buttons appeared on the post-checkout receipt; tapped Edit → "Void & Edit" correctly reversed the sale and reloaded it into the cart. Opened the same invoice later from History → both buttons also present there, confirming that code path's existing wiring is intact.
- Restored a real 521-invoice local JSON backup (`techyfi-pos-backup-2026-08-28.json`) via Settings → Backup & Restore → Restore File (Replace all data) — this path uses the same live database connection (no close/reopen, no `activity.recreate()`), so it is not subject to the ViewModelStore staleness bug; the restore completed and the resulting History list, revenue summary, and margin figures reflected the imported data correctly.
- The Google Drive encrypted-restore path itself (the one the ViewModelStore fix targets) was not exercised end-to-end in this session — it requires a connected Google account not available in the test environment. The fix is a direct correction of a verified root cause (config-change-style `recreate()` retaining the ViewModelStore across a database swap), but has not been confirmed against the field report that prompted it.

## V01-09-2026 update APK verification (2026-09-01)

- Final artifact: `Techyfi-POS-Final-V01-09-2026.apk`, versionCode `220886`, versionName `2.6.12-2026.09.01`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi-POS-Final-V01-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V01-09-2026.apk`
- Features & Architectural Upgrades in this Build:
  - **Symmetrical 52dp Input Architecture**: `Item Description`, `Price (৳)`, `Quantity`, and `Note / Warranty` all formatted to identical `52.dp` height, `14.dp` rounded corner radius, and uppercase label typography (`10.5.sp` extra-bold).
  - **Dynamic 3D Category Icon Sync**: Custom Quick Sale dialog header dynamically reacts and renders synchronized 3D animated canvas icons for Product, Service, Fee, and Free Gift categories.
  - **Full ৳0 Free Item / Complimentary Support**: Added interactive toggle with emerald styling allowing zero-price products, gifts, and repair fees with validation bypass.
  - **Note & Warranty Input Refactor**: Single-line Surface container with leading 3D holographic shield, ellipsis placeholder, interactive clear button, and quick-select presets.
- Verified live on Android emulator:
  - Clean compilation with `./gradlew compileDebugKotlin` and release build `./gradlew assembleRelease` with R8 minification and resource shrinking enabled.
  - Seamless in-place update tested on live emulator via `adb install -r` resulting in `Success` without certificate conflict or data loss.
  - Navigated and verified visual polish across all custom item dialog states and active preset chips.

## V02-09-2026 update APK verification (2026-09-02)

- Final artifact: `Techyfi-POS-Final-V02-09-2026.apk`, versionCode `220887`, versionName `2.6.13-2026.09.02`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi-POS-Final-V02-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V02-09-2026.apk`
- Features & Architectural Upgrades in this Build:
  - **Smart Cash Tender & Instant Change Calculator** (`CartScreen.kt`):
    - Added quick denomination addition chips: `⚡ Exact`, `+৳50`, `+৳100`, `+৳500`, `+৳1000`, and dynamic `✕ Clear` chip.
    - Dynamic real-time calculation and display of **Change Due (💵 Change ৳...)** in soft emerald green container when cash received >= grand total, or **Shortage Due (⚠️ Short ৳...)** in soft rose container when cash received < grand total.
  - **Live Pricing & Profit Margin Intelligence** (`ProductsScreen.kt`):
    - Added real-time reactive calculation of **Unit Profit (৳)**, **Gross Margin %**, and **Markup %** inside `AddEditProductDialog`.
    - Real-time **Loss Alert** warning badge displayed if Selling Price is entered lower than Purchase Cost.
  - **Customer Due Aging Breakdown Analysis** (`DuesScreen.kt`):
    - Added 3-tier customer due aging analysis card: `< 7 Days`, `7–30 Days`, and `> 30d Overdue`.
    - Color-coded badges and dividing indicators for risk assessment and debt recovery prioritization.
  - **Day Close Cash Discrepancy Note Audit Trail** (`AdvancedPosFeatures.kt` / `RegisterCloseScreen.kt`):
    - Added discrepancy note input field on the Z-Report day closing dialog (`ZReportDialog`) whenever physical counted cash differs from expected system cash.
- Verified live on Android emulator:
  - Release build with `./gradlew assembleRelease` with R8 minification and resource shrinking enabled: `BUILD SUCCESSFUL in 1m 5s` (0 errors).
  - Deployed and installed via `adb` onto `emulator-5554` (`Success`) without data loss.
  - Live testing verified: PIN unlock (`1111`), Products catalog, Add Product dialog profit intelligence, Dues aging cards, Cart checkout tender chips, and Change Due calculations.

## V02-09-2026 (Part 2) Money Receipt Redesign & Update APK verification (2026-09-02)

- Final artifact: `Techyfi-POS-Final-V02-09-2026.apk`, versionCode `220889`, versionName `2.6.14-V02-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi-POS-Final-V02-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V02-09-2026.apk`
- Features & Architectural Upgrades in this Build:
  - **Thermal Printer Friendly Money Receipt Voucher Redesign**:
    - Complete overhaul of the Due Repayment / Money Receipt slip in `BillPdfGenerator.kt` supporting both 80mm (`576px`) and 58mm (`384px`) thermal roll formats.
    - Symmetrical header layout with store branding, optional logo rendering, voucher #, customer info, and bilingual transaction summary.
    - Previous Due, Current Paid, and Remaining Balance breakdown with clear high-contrast borders and dividers.
    - Optional Custom Notice / Policy / Terms section with user-defined title and multi-line message.
    - Dual bilingual generation supporting both English and Bengali typography natively.
  - **Live Interactive Thermal Preview in Settings** (`SettingsScreen.kt`):
    - Added dedicated `Money Receipt & Voucher Settings` section with live interactive thermal preview canvas.
    - 80mm vs 58mm selector, shop logo toggle, custom section toggle, and custom text inputs.
    - Instant live English (EN) vs Bengali (বাং) toggle to preview thermal slip rendering immediately.
  - **On-Screen Due Repayment Dialog Integration** (`DuesScreen.kt`):
    - Connected live merchant settings to on-screen due collection dialog.
    - Direct generation of thermal slip bitmap, PDF, and WhatsApp/SMS share text payload.
- Verified live on Android emulator:
  - Release build with `./gradlew assembleRelease` with R8 minification and resource shrinking enabled: `BUILD SUCCESSFUL in 3m 23s` (0 errors).
  - Deployed and installed via `adb` onto `emulator-5554` (`Success`) without data loss.
  - Live testing verified: PIN unlock (`1111`), Settings screen navigation, Money Receipt settings, live thermal slip preview with EN/বাংলা switching, and Dues screen repayment voucher generation.

## Critical/high/medium review remediation (2026-09-02)

- Critical/high protections now enforce verified Owner credit overrides, atomic no-oversell checkout, first-Due-sale credit limits, ledger-derived Customer Hub metrics, private due-reminder alarm dispatch, unique cashier PINs, and repository-level Owner authorization.
- Medium-priority Compose/resource issues were fixed without lint suppressions: callback messages are resolved during composition, cash tender state is lifecycle-collected instead of reading `StateFlow.value`, the literal `100%` resource is marked non-formatting, and the scheduled-reminder status has a Bengali translation.
- Debug Kotlin, JVM unit tests, Android-test compilation, debug assembly, and the full Android lint task all complete successfully with zero lint errors.

## V04-09-2026 Receipt Language Scoping, Dialog Localization & Release Update APK (2026-09-04)

- Final artifact: `Techyfi-POS-Final-V04-09-2026.apk`, versionCode `220891`, versionName `2.6.16-V04-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/app/build/outputs/apk/final/Techyfi-POS-Final-V04-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V04-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi-POS-Final-V04-09-2026.apk`
- Features & Architectural Upgrades in this Build:
  - **Receipt Language Decoupling & Scoping (`DueRepaymentReceiptDialog`)**:
    - Scoped the **EN / বাং** segmented toggle strictly to receipt template generation (`isReceiptBangla`).
    - Outer dialog window chrome (title "Money Receipt" / "টাকা জমার রশিদ", subtitle "DUE REPAYMENT VOUCHER" / "বাকি পরিশোধের ভাউচার", summary status pill, settlement badge, SMS/WhatsApp action buttons, Print Slip, Share Slip, Save Slip buttons, and toast feedback) strictly bound to global App Language (`appLanguage == "bn"`).
    - When the app is in English, toggling to `বাং` updates *only* the thermal receipt slip preview, printed slip, and share text without altering the English UI.
    - When the app language is set to Bangla, the dialog window chrome displays authentically in Bengali.
  - **Zero-Lag Asynchronous Receipt Rendering**:
    - Receipt bitmap generation (`generateDueRepaymentJpeg`) and file decoding (`BitmapFactory.decodeFile`) offloaded to `Dispatchers.IO` background coroutine context.
    - Eliminates UI thread hitching and frame drops when the voucher dialog opens or when toggling between English and Bangla slips.
  - **Continuous In-Place Update Compatibility**:
    - Version bumped to `220891` (`versionName: 2.6.16-V04-09-2026`).
    - Retains debug signing certificate configuration for seamless in-place APK updates without data loss.

## V04-09-2026 (v2.6.17) Complete Quality & Feature Audit Remediation, Theme Mode & Batch Printing (2026-09-04)

- Final artifact: `Techyfi-POS-Final-V04-09-2026.apk`, versionCode `220892`, versionName `2.6.17-V04-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/app/build/outputs/apk/final/Techyfi-POS-Final-V04-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V04-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi-POS-Final-V04-09-2026.apk`
- Features & Architectural Upgrades in this Build:
  - **Issue 1.1: Return/Refund on Due Sales Over-Refund Fix (`ReturnDialog.kt` & `DataRepository.kt`)**:
    - Returns on credit/due sales calculate outstanding remaining balance first; defaults refund settlement to "DUE Adjustment" if debt remains.
    - Capped due adjustment at `min(refundTotal, remainingDue)` with exact debt clearance status and net cash payout calculations, preventing over-disbursement of store cash.
  - **Issue 1.2: Split Payment Digital Drawer Reconciliation (`POSViewModels.kt`)**:
    - Expanded `REGISTER_CLOSE_DIGITAL_METHODS` case-insensitive matching (`bkash`, `nagad`, `rocket`, `card`, `bank`, `mfs`, `digital`).
    - Corrected split cash vs digital drawer math in `splitCashAndDigitalAmounts` and `calculateSaleDueAmount` ensuring cash registers reconcile accurately upon close.
  - **Issue 1.3: SMS Permission Inversion Bug in DuesScreen (`DuesScreen.kt`)**:
    - Fixed inverted logic: `if (notificationGranted && (!smsNeeded || smsGranted))` allowing SMS/due reminders to be safely sent when appropriate permissions are granted.
  - **Issue 1.4: Mandatory Customer Phone for Due/Credit Sales (`DataRepository.kt`)**:
    - Enforced validation: `require(!normalizedCustomerPhone.isNullOrBlank()) { "Customer phone number is required for credit/due sales" }` ensuring credit sales never drop from the due ledger.
  - **Issue 1.5: Overnight Register Shift Midnight Truncation Fix (`POSViewModels.kt`)**:
    - In `ExpenseViewModel`, replaced shift timestamp clamp with `shift?.startedAt?.takeIf { it > 0L } ?: todayStartMillis()`, preserving register shift transactions that span across midnight.
  - **Issue 2.1: 58mm Thermal Receipt Formatting (`BillPdfGenerator.kt`)**:
    - Complete layout scaling for 58mm rolls (384px width, 14px padding, 17sp/18sp/25sp fonts, 32-character dividers, dynamic column widths `colItemMaxW = 150f`, `colQtyX = 185f`, `colPriceX = 265f`, `colTotalX = 370f`).
  - **Issue 4.2: Direct "Start Sale" Flow to Scanner Screen (`Navigation.kt`)**:
    - Routed "Start Sale" shortcuts in DuesScreen, LoyaltyScreen, and CustomersScreen directly to the camera barcode scanner (`Scanner`) rather than an empty cart.
  - **Issue 4.3: Dark / Light / System Theme Mode Infrastructure & UI (`SettingsDataStore.kt`, `DataRepository.kt`, `POSViewModels.kt`, `MainActivity.kt`, `SettingsScreen.kt`, `StockCheckComponents.kt`)**:
    - Added reactive `themeModeFlow` persisted in DataStore ("SYSTEM", "LIGHT", "DARK").
    - Wired to root `TechyfiPOSTheme` in `MainActivity.kt`.
    - Added executive Theme Mode SegmentedButton card in `SettingsScreen.kt` with custom `Realistic3DThemeIcon`.
  - **Feature 5.2: Batch Barcode & Price Tag Printing (`ProductCodeImageUtils.kt`, `ProductsScreen.kt`)**:
    - Added `cacheBatchProductCodeLabelsPdf` supporting multi-page PDF generation of standard thermal labels (50x30, 40x25, 30x20, etc.).
    - Added `BatchProductCodePrintDialog` accessible via Products screen top bar Excel/Print menu with All vs In-Stock filter, 1-each vs match-stock quantity copies, and direct PDF sharing/printing.
  - **Issues 6.1–6.3: Clean Code & Deprecation Hardening**:
    - Replaced deprecated `Locale(lang)` constructors with modern `Locale.forLanguageTag(lang)`.
    - Replaced runtime `Color.parseColor` hex parsing with compile-time hex integer constants.
    - Added `limit = 4` to PBKDF2 hash splitting preventing unbounded array allocations.

## V04-09-2026 (v2.6.18) Enhanced Receipt Summary Dialog Window Design & Bilingual Receipt (2026-09-04)

- Final artifact: `Techyfi-POS-Final-V04-09-2026.apk`, versionCode `220893`, versionName `2.6.18-V04-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/app/build/outputs/apk/final/Techyfi-POS-Final-V04-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V04-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi-POS-Final-V04-09-2026.apk`
- Features & Architectural Upgrades in this Build:
  - **New Receipt Summary Dialog Window Design (`CartScreen.kt`)**:
    - Expanded dialog size to 96% width and 92% height with rounded 26dp elevation card.
    - Animated 3D Receipt Icon with live green status pulse dot and shimmer title.
    - Financial Metrics Ribbon with mint background (`#E8F8F5`), realistic 3D gold coins (`Realistic3DGoldCoinsIcon`), cash bill icon, and repayment seal.
    - Viewport Control Bar with 80mm/58mm roll width chips, `[ EN | বাং ]` sliding pill toggle, and Zoom Inspector.
    - Deep slate container (`#0F172A`) with authentic white thermal paper texture.
    - Bottom Action Deck with prominent hero `Print` button, symmetrical `Share`/`Save`/`Repeat` buttons, and clean overflow row with ~60px whitespace before `Done ➜`.
  - **100% Untouched Baseline English Receipt (`BillPdfGenerator.kt`)**:
    - Strict line-for-line preservation of original English receipt formatting, typography, column widths, dividers, and totals.
  - **1:1 Structural Twin Bangla Receipt (`BillPdfGenerator.kt`)**:
    - Translates only static retail labels (`চালান নং`, `তারিখ`, `ক্যাশিয়ার`, `আইটেম`, `পরিমাণ`, `দর`, `মোট`, `উপমোট`, `সর্বমোট`, `নগদ`, `জমা`, `ফেরত`, `অবস্থা : পরিশোধিত`).
    - Dynamic values (IDs `#651`, timestamps `04-Sep-2026 07:59 AM`, amounts `৳550`, product names) remain strictly untranslated.
    - Window UI and app interface remain strictly in English.
  - **Continuous In-Place Update Compatibility**:
    - Version bumped to `220893` (`versionName: 2.6.18-V04-09-2026`).
    - Signed using debug certificate configuration for seamless continuous in-place APK upgrade.

## V05-09-2026 (v2.6.20) Sale Mode MRP Pricing, Subtotal Alignment & Thermal Receipt Redesign (2026-09-05)

- Final artifact: `Techyfi-POS-Final-V05-09-2026.apk`, versionCode `220895`, versionName `2.6.20-V05-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V05-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/app/build/outputs/apk/final/Techyfi-POS-Final-V05-09-2026.apk`
- Features & Architectural Upgrades in this Build:
  - **Cart & Complete Sale MRP Subtotal Alignment (`CartScreen.kt`)**:
    - In Sale Mode (`PriceMode.DISCOUNTED` with MRP savings), the pricing summary `Subtotal` row displays `mrpSubtotal` (e.g. `৳640`), followed by `Sale Savings: -৳70` and `Grand Total: ৳570`.
    - Eliminates arithmetic confusion where a discounted subtotal previously appeared as `Subtotal 570 - Savings 70 = Grand Total 570`.
    - Perfectly mirrors the **Customer Savings Breakdown** hero card (`Original MRP Subtotal: ৳640`, `Store Sale Discount: -৳70`, `Total Customer Savings: ৳70 (11%)`).
  - **Thermal Receipts MRP Pricing Table & Sublines (`BillPdfGenerator.kt`)**:
    - Table column `Price` displays the item's **MRP Price** (`550`, `90` / `৫৫০`, `৯০`).
    - Table column `Total` displays the item's **MRP Line Total** (`550`, `90` / `৫৫০`, `৯০`).
    - When item-wise discount display is enabled (`showItemWiseMrpDiscount`), an authentic subline renders the store's offered price and customer savings:
      - English: `  Offer: ৳500 (Save ৳50)`
      - Bangla: `  অফার: ৳৫০০ (সাশ্রয় ৳৫০)` (with localized Bengali digits)
    - When toggled to `Without Item-by-Item`, the subline is omitted for a clean single-line slip.
    - Receipt `Subtotal` displays `mrpSubtotal` (`৳640`), followed by `MRP Discount: -৳70` / `এমআরপি সাশ্রয় (ছাড়): -৳70`, and `GRAND TOTAL: ৳570` / `সর্বমোট: ৳570`.
    - Zero price mode tags (`[SALE MODE]`, `[MRP MODE]`, etc.) appear on the receipt.
  - **Interactive HTML Preview Prototype (`sale_mrp_discount_preview.html`)**:
    - Prototype updated in lockstep: initial DOM and reactive recomputations reflect MRP prices, offer sublines, and MRP subtotals.
  - **Continuous In-Place Update Compatibility**:
    - Version bumped to `220895` (`versionName: 2.6.20-V05-09-2026`).
    - Signed with established debug keystore identity, verified installing cleanly over existing versions via `adb install -r` without data loss.

## V06-09-2026 (v2.6.21) Phase 1, Phase 2 & Phase 3 Comprehensive Architecture Upgrades (2026-09-06)

- Final artifact: `Techyfi-POS-Final-V06-09-2026.apk`, versionCode `220896`, versionName `2.6.21-V06-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V06-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/app/build/outputs/apk/release/app-release.apk`
- Features & Architectural Upgrades in this Build:
  - **Phase 1: Critical & High Priority Core Enhancements**:
    - **Google Drive Backup Failure Alerts & Diagnostic Pre-Flight**:
      - Added automated failure notifications on dedicated Android notification channel (`"techyfi_backup_alerts"`) with tap intent directing to `MainActivity`.
      - Added manual "Test Drive Connection Status" pre-flight verification button in Backup Settings.
    - **Single-Use Scoped Elevation Tokens for Credit Limit Overrides**:
      - Replaced global boolean override flags with `CreditOverrideToken(phone, maxAmount, cartHash, expiresAt)`.
      - Scoped strictly to the customer phone and verified amount, auto-invalidated after checkout.
    - **Store Credit Returns Tracking & Checkout Redemption**:
      - Added `Customer.storeCredit` (Room schema migration 23→24).
      - Return cash refunds exceeding initial tender can be diverted directly to customer store credit balance.
      - Cart Screen dynamically exposes `"Store Credit"` payment tender chip when customer has positive credit balance; validates sufficiency and records deductions.
    - **Direct Bluetooth Raw ESC/POS SPP Thermal Printing Driver**:
      - Built native raster ESC/POS driver (`EscPosPrinterDriver.kt`) for direct SPP socket printing to 58mm/80mm Bluetooth printers alongside the Android System Print service.
      - Includes paired device picker dialog and MAC address persistence.
    - **Excel Import EAN-13 & Phone Leading-Zero Preservation**:
      - Enhanced workbook cell parsing in `ExcelHelper.kt` to preserve leading zeros on EAN-13 barcodes and 11-digit Bangladeshi mobile numbers.
    - **Register Shift Closing Provider Breakdown**:
      - Added `bkashSales`, `nagadSales`, `rocketSales`, `cardSales`, `dueSales` columns to `register_closings` table (Room migration 23→24).
      - Added "Sales By Payment Method" provider breakdown card on Register Close Screen.
  - **Phase 2: Medium Priority Architectural Improvements**:
    - **Thermal Receipt Bitmap Memory Optimization**:
      - Upgraded receipt rendering in `BillPdfGenerator.kt` to `Bitmap.Config.RGB_565` (50% memory saving) and added explicit `.recycle()` on temporary monochrome bitmaps to prevent peak billing OOM crashes.
    - **Negative Inventory Policy Setting & Supervisor Elevation**:
      - Added `allowNegativeInventory` setting in `InvoiceSettingsData` with toggle in Settings.
      - Enforced during checkout; if negative stock occurs and setting is disabled, prompts supervisor PIN authorization dialog.
    - **Hardware Barcode Scanner Rapid Timing Burst Interception**:
      - Implemented Window key dispatch interception for keystroke bursts with inter-key delta < 38ms, routing directly to scan handling even when an input field is focused.
    - **Batch Product Operations in Inventory**:
      - Added multi-select mode in `ProductsScreen.kt` supporting bulk price adjustments (flat or %), category reassignment, and bulk deletion.
    - **Wi-Fi Only POS Terminal Fallback for Due Reminders**:
      - Checked `TelephonyManager.simState == SIM_STATE_READY` before invoking SMS manager, gracefully preventing crashes on cellular-less Wi-Fi tablets.
    - **Encrypted Local Backup Standalone Vault (`.posenc`)**:
      - Implemented password-protected AES-256-GCM + PBKDF2WithHmacSHA256 (`POSENC1` envelope format) standalone backup export and import.
  - **Phase 3: Polish & Localization**:
    - **100% Consistent Bangla Numerals on Bengali Receipts**:
      - Standardized all thermal receipts and vouchers to use localized Bengali numerals via `toBanglaDigits` and `formatTakaBn`.
    - **Wholesale Savings Breakdown**:
      - Receipts display an emerald savings banner when wholesale pricing discounts apply.
    - **Due Repayment Money Receipt Verification QR Code**:
      - Generates authentic verification QR code payload on payment receipts.
    - **Ergonomic Touch Targets**:
      - Cart stepper buttons enlarged from 32dp to 38dp with touch padding exceeding the 48dp guideline.

## V06-09-2026 Pass 1 Audit Fixes: Financial, Cash Drawer & Shift Reconciliation (M1 - M10)

- Final artifact: `Techyfi-POS-Final-V1.apk`, versionCode `220896`, versionName `2.6.21-V06-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V1.apk`
- Database Schema Version: `25` (added `sale_audit_logs` table via `MIGRATION_24_25`).
- Fixes implemented:
  - **[M1] Due Down Payments in Cash Drawer & Cash Sales**: Captured partial down payments on Due sales into `Sale.amountPaid` and included in `liveDrawerCash` and cash sales reconciliation.
  - **[M2] Store Credit Treated as Non-Cash**: Excluded non-cash store credit and voucher tenders from liquid physical cash drawer calculations.
  - **[M3] Sale Deletion Audit Logging & Return History Protection**: Created `SaleAuditLog` entity, `SaleAuditLogDao`, and required void/cancellation reason dialog; blocked deletion of sales that have active returns.
  - **[M4 & M9] Standardized Money Utilities & Bangladeshi Lakh-Crore Digit Grouping**: Created `MoneyUtils.kt` with Banker's Rounding `HALF_EVEN` and Bangladeshi comma grouping (`৳1,00,000.00`) applied across thermal receipts, PDF, and UI.
  - **[M5] Shift Time Scope for Z-Report & X-Report**: Scoped financial figures strictly between active `shiftStartedAt` and closing timestamp.
  - **[M6] Digital Due Repayments in Shift Reconciliation**: Aggregated non-cash due collections (bKash, Nagad, Card) into digital shift sales and provider totals on register close.
  - **[M7] Historical Closing Inspection & Double-Tap Debounce**: Added `HistoricalClosingDetailDialog` to inspect past shift breakdowns, over/short variances, and shift notes; debounced register close submission.
  - **[M8] Non-Negative Price & Stock Validation**: Sanitized and clamped Excel product import and manual forms to `>= 0.0`.
  - **[M10] Unclamping Live Drawer Cash**: Removed artificial `.coerceAtLeast(0.0)` clamp on `liveDrawerCash` so cash drawer deficits accurately match physical reality and expected cash.

## V06-09-2026 Pass 2 Audit Fixes: General Operations, Concurrency, Security & UI/UX (G1 - G10)

- Final artifact: `Techyfi-POS-Final-V2.apk`, versionCode `220896`, versionName `2.6.21-V06-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V2.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/app/build/outputs/apk/release/app-release.apk`
- Fixes implemented:
  - **[G1] Supplier Cash Outflows Linked to Expenses & Cash Drawer**:
    - Automatically records an `Expense` with category `"Supplier Payment"` and `paymentSource = "Cash"` when recording cash purchase invoices or cash supplier due payments in `DataRepository.kt`.
    - Added Bengali & English localization (`🚚 Supplier Payment` / `🚚 মহাজন / সাপ্লায়ার পেমেন্ট`) in `strings.xml`, `values-bn/strings.xml`, `ExpensesScreen.kt`, and `AdvancedPosFeatures.kt`.
  - **[G2] Profit Calculation N+1 Query Elimination & Accurate Historical Cost**:
    - Prioritizes `item.purchasePriceAtSale` in `todayProfit` to preserve historical profit margins against future cost changes.
    - Batch-fetches missing legacy product barcodes in a single database lookup map, eliminating N+1 roundtrips.
  - **[G3] Factory Reset Session Teardown**:
    - Factory reset routine invokes `onLogout()`, purges in-memory session state in `DataRepository`, and clears the backstack to lock the app into initial setup.
  - **[G4] Removal of Plaintext JSON Backups from External Storage**:
    - `AutoBackupWorker` stops writing unencrypted JSON backups to public external storage and automatically cleans up legacy plaintext `.json` files; backups are retained exclusively in encrypted app-private storage.
  - **[G5] Dynamic Loyalty Settings Integration**:
    - Synchronized `DataRepository` and `CartViewModel` with user-configured `LoyaltyProgramSettings` (`takaPerPoint`, `pointCashValue`, `minRedeemPoints`, `maxBillPercentPayable`) instead of hardcoded 100/1 constants.
  - **[G6] Orientation & Configuration Resilience (Upgraded in V3)**:
    - Initially locked to portrait, then upgraded in V3 to `android:screenOrientation="unspecified"` with `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|uiMode"` to allow responsive tablet landscape counter operation without destroying active session state.
  - **[G7] Thermal PDF/JPEG Offloaded from Main Thread**:
    - Converted `receiptBitmap` in `CartScreen.kt` to `produceState<ImageBitmap?>` on `Dispatchers.IO`, and offloaded `withPdf` and `withJpeg` generation to `Dispatchers.IO` to eliminate UI thread lag on receipt preview dialog opening.
  - **[G8] Excel Product Import Error Handling**:
    - Added `runCatching` with localized error toasts in `ProductsScreen.kt` to notify cashiers/owners of invalid or corrupted spreadsheet formats.
  - **[G9] Customer Duplicate Detection O(N) Optimization**:
    - Refactored `CustomerViewModel.findDuplicatePairs` from $O(N^2)$ pairwise comparisons to an $O(N)$ hash map grouping by normalized phone number.
  - **[G10] UI/UX Polish, Bengali Typography & Touch Targets**:
    - Linked `RegisterCloseScreen` to dynamic authenticated cashier (`Cashier: Owner`) and dynamic shift counter (`Shift #2`).
    - Standardized `BillPdfGenerator.kt` Bengali canvas rendering to Sans-Serif (`Typeface.SANS_SERIF`) to prevent vertical clipping of complex conjuncts (যুক্তাক্ষর).
    - Added supervisor permission bypass for stock checking in `ScannerScreen.kt` and `Navigation.kt`.
    - Ensured all expense filter chips have $\ge 44\text{dp}$ touch targets.
  - **[V3] Phone & Tablet Adaptive Architecture & Layout Controls**:
    - Auto-detect policy (`screenWidthDp >= 600dp`) and manual override (AUTO, TABLET, PHONE) in Settings.
    - Tablet Left Persistent `NavigationRail` (84dp width) with 8+ operational domains vs Phone Bottom `NavigationBar` (4 tabs).
    - Dual-Pane Tablet Split Cashier Terminal (`TabletSplitTerminal` in `ScannerScreen.kt`): Left 58% Catalog & Camera PIP + Right 42% Pinned Live Cart with steppers, Grand Total, and Checkout.
    - Widescreen geometry constraints: PIN pad (`widthIn(max = 380.dp)`), products catalog (`GridCells.Adaptive(minSize = 160.dp)`), denomination counter dialog (`widthIn(max = 560.dp)`).
  - **[V4] Bilingual Localization Engine & Zero-Slash Language Separation**:
    - Clean, complete separation between English and Bangla across all POS screens and split cashier terminal components.
    - 100% elimination of hardcoded bilingual slash strings (`" / "`) in `ScannerScreen.kt` and UI composables.
    - Added 16+ dedicated string resources in `res/values/strings.xml` and `res/values-bn/strings.xml` for POS split terminal.
    - Natural Bengali retail terminology adhering to the `bangla-language` guidelines (`চলমান বিল`, `পণ্য বা বারকোড খুঁজুন…`, `সব`, `সর্বমোট:`, `বিল ও পরিশোধ ➔`, `০টি পণ্য`, `স্টক নেই`, `কার্টে কোনো পণ্য নেই`).
  - **[V5] Navigation Rail App Logo & More Hub Optimization**:
    - Added official Techyfi POS app logo (`R.drawable.ic_pos_launcher_round`) to the top of the tablet `NavigationRail` with an active outlet indicator.
    - Moved Day Start and Day Close register operations into the centralized `More Hub` (`/more_hub`) under "Cash Register & Shifts", decluttering the primary tablet rail down to 8 core operational domains.
  - **[V6] Seamless Automatic App Restart on Backup Restore & Large-Scale Catalog Ingestion**:
    - Implemented `restartApp(context)` in `AppUtils.kt`: rebinds database DAOs (`TechyfiPOSApplication.reloadRepositoryAfterDatabaseRestore()`), completely flushes stale `ComponentActivity.viewModelStore` instances, and relaunches `MainActivity` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`.
    - Integrated automatic restart into all restore entrypoints: Local JSON REPLACE, Local JSON MERGE, Encrypted Standalone Vault (`.posenc`), Google Drive Cloud Restore, and headless automated intent restore (`RESTORE_BACKUP_PATH`).
    - Successfully validated with full retail production backup `techyfi-pos-backup-2026-09-02.json`: restored 621 inventory SKUs (৳10,93,650.60 catalog valuation), 642 sales transactions (৳7,46,697.40 revenue), 25 customer khata dues, and custom store profiles automatically rebooting into the terminal without requiring manual user intervention.
  - **[V7] Material 3 Window Insets Safe Area & System Bars Ergonomics Engine (VersionCode 220900)**:
    - Resolved critical top status bar overlap (clock, date, mute, Wi-Fi, cellular, battery icons) and bottom taskbar/navigation bar cutoff (`Checkout ➔` button, Grand Total, and bottom catalog items).
    - Fixed root cause in `Navigation.kt`: replaced zero-inset tablet override (`PaddingValues(0.dp)`) with dynamic window insets derived from `Scaffold`'s `innerPadding` (`innerPadding.calculateTopPadding()`, `bottomPadding()`, `startPadding()`, `endPadding()`).
    - Added safe insets padding to the tablet `NavigationRail` top header logo (`topInset + 6.dp`) and bottom scroll column (`bottomInset + 8.dp`), ensuring ergonomic touch targets ($\ge 48\text{dp}$) on both 3-button navigation and gesture taskbars.
    - Verified live on Pixel Tablet (`emulator-5554`, 2560x1600): confirmed zero overlap on `Live Cart` title and `3 items` badge, clear margin around the top search field, and fully visible `Grand Total` + `Checkout ➔` buttons above the system navigation bar.
  - **[V8] Camera Viewfinder Cutout Mask & Universal Barcode Recognition (VersionCode 220901)**:
    - Eliminated opaque black mark obstructing the camera preview in `ScannerScreen.kt`: replaced broken `BlendMode.SrcOut` drawing with hardware-safe `PathFillType.EvenOdd` cutout scrim (outer rect + inner rounded rect), leaving the viewfinder area 100% crystal clear.
    - Fixed viewfinder viewport geometry: responsive scaling ensures scan box never overflows banner camera previews on wide tablet layouts (`boxHeight = minOf(height * 0.78f, width * 0.75f, 260.dp)`).
    - Replaced rigid bounding-box coordinate rejection with `isBarcodeInVisiblePreview`, ensuring any barcode visible in the preview window decodes immediately without false drops.
  - **[V9] Backup & Restore Top Scroll Alignment & Subpage Scroll Isolation (VersionCode 220902)**:
    - Resolved critical scroll jump where opening "Backup & Restore" opened scrolled down past the Google Drive cloud status card, landing directly on lower sync switches.
    - Root cause: The settings content `Column` in `SettingsScreen.kt` previously utilized a single unkeyed `rememberScrollState()`. Navigating from the Settings Home menu (scrolled down to ~800px to reach the "Backup & Restore" row) caused `OptimizedBackupSettings` to inherit the ~800px scroll offset, immediately hiding the dark-blue Google Drive Connected Account Card and telemetry hub.
    - Solution: Isolated the scroll state into `homeScrollState` (preserving user scroll position on the main settings list) and `subpageScrollState = key(settingsPage) { rememberScrollState() }`, accompanied by a `LaunchedEffect(settingsPage)` that unconditionally executes `currentScrollState.scrollTo(0)` for any sub-screen.
    - Verified live on `emulator-5554`: Backup & Restore opens reliably at the absolute top (offset 0), prominently displaying `<- Backup & Restore`, `Protect your business data`, and the full Google Drive Connected Hub without any auto-scrolling. Pressing Back cleanly restores the user's exact scroll position on the main Settings list.
  - **[V10 - V15] Settings 3D Animated Holographic Icons & Subpage Navigation Isolation (VersionCode 220903 - 220905)**:
    - Designed high-fidelity canvas-drawn 3D icons (`Settings3DIcons.kt`) with isometric lighting, depth gradients, and glowing accents across all Settings categories (Store Profile, Cloud Backup & Google Drive, Thermal Printer & Hardware, Barcode Scanner, Tax & Discounts, Danger Zone, Staff RBAC, and Language).
    - Subpage Scroll Isolation: Isolated settings home scroll state from subpage scroll states (`key(settingsPage) { rememberScrollState() }`), guaranteeing that entering any subpage opens smoothly at the absolute top (y=0).
  - **[V16 - V17] Customer CRM Dossier, Loyalty Redesign & 58mm/80mm Thermal Receipt Enhancements (VersionCode 220906)**:
    - Customer CRM & Khata Dossier: Interactive customer detail sheet (`CustomerDossierSheet.kt`) with purchase history, khata ledger, direct WhatsApp/SMS reminders, credit limit alerts, and VIP loyalty status.
    - Repayment Receipt Zoom & Width Preview: Multi-width thermal printing preview (58mm, 80mm, PDF, Canvas) with verified verification QR code and zero text distortion.
  - **[V18 - V06-09-2026] Store Operations More Hub Dual View Mode & Zero-Cutoff Subheadings (VersionCode 220907)**:
    - Subheading & Title Cutoff Elimination: Upgraded all operation cards in `Navigation.kt` to support 2-line wrapped subtitles (`maxLines = 2`, `fontSize = 11.sp`, `lineHeight = 14.sp`), eliminating unsightly ellipsis clipping across Phone and Tablet.
    - Expanded dialog constraint ceiling to `widthIn(max = 760.dp)` on tablet, providing 354dp per card in the 2-column grid so titles ("Cash Register", "Customer Loyalty") render 100% visible.
    - Interactive Material 3 Segmented Toggle `[ ☰ List ] [ ⊞ Grid ]` integrated directly into the header row next to the close button on both Phone bottom sheet and Tablet dialog, backed by `rememberSaveable`.
    - Phone Grid view with 2-column compact tiles and 1-column full-width featured Settings banner; Phone List view with 42dp icon, title, 2-line subtitle, status pill, and chevron.
    - Tablet Grid view with horizontal 2-column layout and Tablet List view with full-width ledger rows.
    - Bilingual string resources in `res/values/strings.xml` and `res/values-bn/strings.xml` (`more_view_list_cd`, `more_view_grid_cd`, `লিস্ট ভিউতে পরিবর্তন করুন`, `গ্রিড ভিউতে পরিবর্তন করুন`).
    - Verified on live Android emulator across all 4 modes (Tablet Grid, Tablet List, Phone Grid, Phone List) with zero ellipsis and zero truncation.
    - Delivered and verified as an update APK: `Techyfi-POS-Final-V06-09-2026.apk` with incremented `versionCode = 220907`.

## V07-09-2026 Comprehensive Audit Fixes & Platform Hardening (Pass 1 & Pass 2, V19 - V21, V07-09-2026)

- Final artifact: `Techyfi-POS-Final-V07-09-2026.apk` (also archived as `Techyfi-POS-Final-V21.apk`), versionCode `220908`, versionName `2.6.29-V07-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V07-09-2026.apk`
- Database Schema Version: `26` (added `cashierId`, `shiftId`, enhanced audit logging & indexes via `MIGRATION_25_26`).
- **Pass 1 Audit Fixes (M1 - M12: Financial, Cash Drawer & Shift Integrity)**:
  - **[M1] Due Down Payments in Cash Drawer & Cash Sales**: Captured partial down payments on Due sales into `Sale.amountPaid` and included in `liveDrawerCash` and cash sales reconciliation.
  - **[M2] Store Credit Treated as Non-Cash**: Excluded non-cash store credit and voucher tenders from liquid physical cash drawer calculations.
  - **[M3] Sale Deletion Audit Logging & Return History Protection**: Created `SaleAuditLog` entity, `SaleAuditLogDao`, and required void/cancellation reason dialog; blocked deletion of sales that have active returns.
  - **[M4 & M9] Standardized Money Utilities & Bangladeshi Lakh-Crore Digit Grouping**: Created `MoneyUtils.kt` with Banker's Rounding `HALF_EVEN` and Bangladeshi comma grouping (`৳1,00,000.00`) applied across thermal receipts, PDF, and UI.
  - **[M5] Shift Time Scope for Z-Report & X-Report**: Scoped financial figures strictly between active `shiftStartedAt` and closing timestamp.
  - **[M6] Digital Due Repayments in Shift Reconciliation**: Aggregated non-cash due collections (bKash, Nagad, Card) into digital shift sales and provider totals on register close.
  - **[M7] Shift Register Closing Workflow: Blind Close, Double-Tap Debounce & Historical Inspection**: Added `HistoricalClosingDetailDialog` to inspect past shift breakdowns, over/short variances, and shift notes; debounced register close submission.
  - **[M8] Non-Negative Price & Stock Validation**: Sanitized and clamped Excel product import and manual forms to `>= 0.0`.
  - **[M10] Unclamping Live Drawer Cash**: Removed artificial `.coerceAtLeast(0.0)` clamp on `liveDrawerCash` so cash drawer deficits accurately match physical reality and expected cash.
  - **[M11] Supplier Payment Due Deductions**: Prevented double deduction on supplier payments against purchase invoices and supplier ledger balances.
  - **[M12] Due Khata Collection Lifecycle**: Ensured atomic update across `CustomerDue`, `DuePayment`, and digital payment split registers.

- **Pass 2 Audit Fixes (G1 - G12: Security, Performance, UI/UX & Localization)**:
  - **[G1] P&L Analytics - Supplier Payment Double-Counting Fix (CRITICAL)**:
    - In `POSViewModels.kt`, excluded expenses categorized as `"Supplier Payment"` from `totalExpenses` in `SalesViewModel.buildReport()`, ensuring COGS inventory costs are not deducted a second time and Net Profit matches accounting standards.
  - **[G2] Security - Unauthenticated Remote Restore Backdoor Removed (HIGH)**:
    - Completely removed unauthenticated intent action `"com.techyfi.pos.RESTORE_BACKUP"` from `MainActivity.kt`. All database restoration now strictly requires owner PIN authentication inside Settings.
  - **[G3] Security - LockScreen Back Gesture Interception (HIGH)**:
    - Enforced `BackHandler(enabled = true)` inside `LockScreen.kt` utilizing `moveTaskToBack(true)`. Pressing Android system back button or gesture safely backgrounds the app without bypassing the terminal lock.
  - **[G4] Performance - Async Bitmap Downsampling & LruCache (HIGH)**:
    - Implemented `ProductImageMemoryCache` (1/8th heap max size) with background async downsampling to 256x256 `RGB_565` via `produceState` in `ProductsScreen.kt`, eliminating main-thread jank and OOM in 600+ SKU catalogs.
  - **[G5] Concurrency - CameraX Analyzer Executor Cleanup (HIGH)**:
    - Resolved CameraX background thread leak in `ProductsScreen.kt` and `ScannerScreen.kt` by migrating initialization to Compose `factory` and disposing single-thread executors cleanly in `DisposableEffect`.
  - **[G6] Navigation - Notification Deep Link Preservation (MEDIUM)**:
    - Preserved `pendingNavigationDestination` StateFlow in `MainActivity.kt` so tapping low-stock notifications navigates directly to Products screen upon cashier unlock instead of being discarded.
  - **[G7] Lifecycle - Thermal Receipt Preview State Survival (MEDIUM)**:
    - Hoisted `showReceiptDialog` and `completedBillSnapshot` into `CartViewModel` in `POSViewModels.kt`, preventing the receipt preview from vanishing on device screen rotation.
  - **[G8] Hardware/Peripherals - Direct Bluetooth ESC/POS Printing Driver (MEDIUM)**:
    - Implemented `printReceiptWithBluetoothFallback()` in `BillActions.kt`, attempting direct SPP socket printing to connected 58mm/80mm thermal printers with automatic cash drawer kick before falling back to Android PrintManager.
  - **[G9] UI/Workflow - Multi-Supplier Quick Inward Vendor Picker (MEDIUM)**:
    - Built interactive `showSupplierPickerForInflow` modal in `SuppliersScreen.kt` with live search, supplier name, representative, phone number, and outstanding due balance badges.
  - **[G10] Localization - Bangla Navigation & Permission Toasts (LOW)**:
    - Added dedicated string resources to `values/strings.xml` and `values-bn/strings.xml` adhering to Bangladeshi retail conventions for all role permission denials and navigation alerts.
  - **[G11] Accessibility - 48dp Minimum Touch Targets (LOW)**:
    - Enforced `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` across all action and navigation icon buttons in `DuesScreen.kt`, `LoyaltyScreen.kt`, and `CustomersScreen.kt`.
  - **[G12] Typography - Bengali Unicode Sans-Serif Rendering (LOW)**:
    - Standardized PDF invoice and X/Z report canvas headers to `Typeface.SANS_SERIF` in `BillPdfGenerator.kt`, preventing Bengali store names from rendering as tofu square blocks.

## V08-09-2026 Hardware Barcode Scanner & English Barcode Guarantee (V25)

- Final artifact: `Techyfi-POS-Final-V08-09-2026.apk` (also indexed as `Techyfi-POS-Final-V25.apk`), versionCode `220911`, versionName `2.6.32-V08-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V08-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V25.apk`
- Deliverables Convention:
  - All updated APKs and mind maps (`APP_MIND_MAP.md`) must reside strictly in `/Users/shahidulhaque/App Dev/Techyfi POS/`.
- Features & Fixes implemented:
  - **[B1] Hardware Barcode Scanner Keystroke Interceptor (`HardwareBarcodeScanner.kt`)**:
    - Replaced raw ASCII range check with `extractAsciiBarcodeChar` mapping hardware keycodes (`KEYCODE_0..9`, `KEYCODE_NUMPAD_0..9`), Bengali Unicode digits (`0x09E6..0x09EF`), Arabic digits, and ASCII symbols directly to English ASCII digits `'0'..'9'`.
    - Added `isTerminatorKey` supporting `KEYCODE_ENTER`, `KEYCODE_NUMPAD_ENTER`, and `KEYCODE_TAB` (`\t`, ASCII 9).
    - Fixed dangling `ACTION_UP` events by consuming `ACTION_UP` for consumed keys and terminators, preventing unwanted dialog dismissals or accidental button clicks.
    - Added burst handling in `dispatchKeyEventWithEditorCheck` that dispatches synthetic `KEYCODE_DEL` to cleanly erase the leaked first character in focused input fields.
  - **[B2] Universal English Digit Normalization Across Entire POS (`toEnglishDigits`)**:
    - Created `toEnglishDigits` in `AppUtils.kt` to normalize Bengali (`০-৯`), Arabic-Indic (`٠-٩`), and Persian (`۰-۹`) numerals to English ASCII (`0-9`).
    - Enforced in `AddEditProductDialog`, `ManualBarcodeDialog`, `CartScreen`, `ScannerScreen`, `POSViewModels` (`ScannerViewModel`, `CartViewModel`, `ProductsViewModel`).
    - Enforced in `DataRepository.kt` & `AppDaos.kt` (`searchProductsWithAlt` allows dual search with Bengali or English digits).
    - Guaranteed Code-128 barcode generation (`ProductCodeImageUtils.kt`), Excel product imports (`ExcelHelper.kt`), and database backup restoration (`BackupCompatibility.kt`) strictly preserve English barcodes.
  - **[B3] English Barcode Display Guarantee in Bangla Mode**:
    - Verified on live Android emulator that when app language is set to **বাংলা (Bangla)**, all barcode tags (`#846656005286`, etc.) and barcode input fields strictly remain in English digits.

## V08-09-2026 Top Header Icons & Title Redesign (3D Squircle Badges & Zero-Cutoff Typography)

- Final artifact: `Techyfi-POS-Final-V08-09-2026.apk` (also indexed as `Techyfi-POS-Final-V25.apk`), versionCode `220911`, versionName `2.6.32-V08-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V08-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V25.apk`
- Deliverables Convention:
  - All updated APKs and mind maps (`APP_MIND_MAP.md`) must reside strictly in `/Users/shahidulhaque/App Dev/Techyfi POS/`.
- Features & Enhancements implemented:
  - **[H1] Unified Master Component (`PosSectionHeader.kt`)**:
    - Created `PosTopAppBarTitle` with responsive layout sizing, photorealistic 3D squircle badges, and zero-cutoff typography.
    - Phone (<600dp): 40dp squircle badge (`RoundedCornerShape(12.dp)`), 25dp realistic 3D icon, 18sp ExtraBold title (`lineHeight = 24.sp`), 11.5sp Medium subtitle (`lineHeight = 15.5.sp`).
    - Tablet (>=600dp): 48dp squircle badge (`RoundedCornerShape(16.dp)`), 32dp realistic 3D icon, 22sp ExtraBold title (`lineHeight = 28.sp`), 13sp Medium subtitle, and prominent inline metric pill badge (`RoundedCornerShape(50)`).
    - `PosSectionColors` with calibrated brand tokens: Products (`#4F46E5`), Customers (`#8B5CF6`), Dues (`#EF4444`), Suppliers (`#0D9488`), Expenses (`#F43F5E`), SalesReport (`#0284C7`), SalesHistory (`#3B82F6`), RegisterClose (`#D97706`), Cart (`#10B981`), Loyalty (`#F59E0B`), Settings (`#64748B`), Users (`#059669`), More Hub (`#6366F1`).
  - **[H2] Zero-Cutoff & Zero Diacritic Clipping Guarantee**:
    - Title column constrained with `Modifier.weight(1f, fill = false)`, `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`.
    - Ample vertical line heights (`24.sp` on phone, `28.sp` on tablet) completely eliminate truncation of Bengali ascender and descender glyphs (রেফ, হ্রস্ব-উ কার, ঋ-কার, ও-কার).
    - Horizontal metric pill badge omitted from title row on phone viewports (<600dp) and neatly integrated into the subtitle row, leaving full width for titles even when up to 4 action buttons are present.
  - **[H3] Full POS Suite Rollout across 12 Screens**:
    - Replaced disjointed 22dp/24dp raw icons across `ProductsScreen`, `CustomersScreen`, `SuppliersScreen`, `ExpensesScreen`, `DuesScreen`, `SalesReportScreen`, `SalesHistoryScreen`, `RegisterCloseScreen`, `CartScreen`, `LoyaltyScreen`, `SettingsScreen`, `ManageUsersScreen`, and `Navigation.kt` (`MoreOperationsHubContent`).
    - Standardized bilingual subtitle strings in `strings.xml` and `values-bn/strings.xml`.
  - **[H4] Interactive Redesign Preview**:
    - Created high-fidelity interactive browser preview `interactive_preview/top_headers_redesign_preview.html` allowing real-time switching across all 12 sections in English/Bangla and Phone (360dp, 412dp) vs Tablet (1280dp) modes.

## V11-09-2026 Comprehensive App Audit & UX Ergonomics Improvements (V26)

- Final artifacts: `Techyfi-POS-Final-V11-09-2026.apk` (also indexed as `Techyfi-POS-Final-V26.apk`), versionCode `220911`, versionName `2.6.33-V11-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V11-09-2026.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V26.apk`
- Deliverables Convention:
  - All built APKs and mind maps (`APP_MIND_MAP.md`) strictly stored in `/Users/shahidulhaque/App Dev/Techyfi POS/`.
- Features & Fixes implemented:
  - **[A1] System Back Gesture & Bottom Sheet BackHandler (`Navigation.kt`)**:
    - Integrated root `BackHandler(enabled = showMoreHubSheet || (backStack.size > 1 && currentKey != Splash && currentKey != Lock))` in `Navigation.kt`.
    - If `showMoreHubSheet` is visible, back gesture smoothly dismisses the sheet without altering the screen backstack.
    - If user is inside a secondary subscreen (e.g. Customers, Loyalty, Sales Report, Settings), back gesture pops the backstack to the previous screen or Sell terminal instead of abruptly closing or backgrounding the POS app.
  - **[A2] Dynamic Products FAB Auto-Collapse on Scroll (`ProductsScreen.kt`)**:
    - Added `rememberLazyListState()` and `rememberLazyGridState()`, with derived `isScrolling` state.
    - Wrapped `+ Add Product` floating action button in `AnimatedContent(targetState = isScrolling)`:
      - When scrolling: collapses into a compact 52dp circular emerald jewel (`+` icon) with subtle elevation, keeping catalog items fully visible.
      - When idle: gracefully expands back into the full pill button (`+ Add Product`).
    - Extended bottom content clearance padding from `80.dp` to `140.dp` across list and grid modes, preventing bottom SKU row actions from being obscured by the bottom navigation bar or FAB.
  - **[A3] Direct WhatsApp Customer Khata Dispatch Button (`DuesScreen.kt`)**:
    - Added dedicated `[ 💬 WhatsApp ]` action button next to `[ 💷 Collect ]` and `[ 🟣 Remind ]` in `CustomerDueRow`.
    - Automatically builds an encoded WhatsApp intent (`https://api.whatsapp.com/send?phone=+880...&text=...`) with polite Bengali retail khata messaging (`আসসালামু আলাইকুম [Name] ভাই... আপনার বকেয়া ৳[Amount]... পরিশোধের অনুরোধ করা হচ্ছে`).
    - Uses safe `Intent(Intent.ACTION_VIEW)` with activity check and fallback toast if WhatsApp is not installed.
    - Increased list bottom content padding to `100.dp`.
  - **[A4] Empty Cart 2x2 Fast-Add Popular Retail Shortcuts (`CartScreen.kt`)**:
    - Replaced static blank empty cart state with interactive `Quick Add Popular Items` section.
    - Features a 2x2 responsive grid of common Bangladeshi retail essentials:
      - 🛍️ Shopping Bag (৳5)
      - 🥚 Farm Egg 1 Pc (৳14)
      - 🥤 Water 500ml (৳20)
      - ☕ Tea & Biscuit (৳15)
    - Tapping `[ + Add ]` immediately dispatches `cartViewModel.addCustomItem(...)` into active cart with instant tactile feedback.
  - **[A5] Customers & CRM 2x2 Balanced KPI Metrics Grid (`CustomersScreen.kt`)**:
    - Refactored single-row overflowing horizontal carousel on compact phone viewports into a clean, balanced 2x2 grid (`Row` with two `Modifier.weight(1f)` cards per row):
      - Total Customers & Total Dues
      - VIP Spenders & Repeat Rate
    - Updated `KpiMetricCard` to support external `modifier` passing for uniform card widths and vertical heights.
    - Increased list bottom padding to `100.dp`.
  - **[A6] Sales Report Empty State Smart Timeframe Switcher (`SalesReportScreen.kt`)**:
    - Transformed passive "No Sales in This Period" card into an actionable smart period switcher.
    - Prominently displays `💡 View Historical Period Analytics:` with quick-filter buttons (`[ 🗓️ 30 Days ]`, `[ 📊 This Year ]`).
    - Tapping buttons instantly switches `selectedDateFilter` and loads executive P&L analytics without needing manual top-bar navigation.
    - Increased bottom clearance padding to `96.dp`.
  - **[A7] Cash Register Active Shift >24h Reconcile Warning Banner (`RegisterCloseScreen.kt`)**:
    - Computed active shift duration (`shiftHours = (System.currentTimeMillis() - activeShift.openedAt) / 3600000L`).
    - When an active register shift exceeds 24 hours, renders a high-visibility amber warning banner (`⚠️ Shift active for Xh (>24h). Recommend closing register for daily reconciliation.`).
    - Extended bottom scroll clearance to `110.dp`.
  - **[A8] Expenses Guidance Copy Cleanup & Bottom Clearance (`ExpensesScreen.kt` & `strings.xml`)**:
    - Corrected empty state subtitle in `strings.xml` and `values-bn/strings.xml` to remove outdated reference to `+ Cash In Float` and accurately point users to `+ Record Expense`.
    - Increased list bottom content clearance padding to `100.dp`.

## Maintenance note

Update this file whenever a screen, navigation route, persistent entity, payment workflow, or critical business rule changes. It is intended to remain the concise source of truth for future development work in this workspace. All updated APKs and mind maps must always be stored in `/Users/shahidulhaque/App Dev/Techyfi POS/`.

