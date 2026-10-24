# Techyfi POS - App Mind Map

Last updated: 2026-10-25 (app version 2.8.00-Final-V25-10-2026, build `Techyfi-POS-Meds-Final-V25-10-2026.apk`; versionCode 220985; DB schema version 39; package `com.techyfiposmeds`)

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
            Can toggle on/off any of the 33 permissions per staff account
            Add, edit, deactivate, or delete staff members
            Staff management search & role filters (PosSearchBox, All/Owner/Manager/Cashier/Suspended chips with live counters, join date, empty search state)
            Multi-owner architecture (can add multiple owners, demote extra owners, last active owner protected, Option B credential protection against silent takeover)
          Store Manager
            Operational oversight: inventory, stock inwarding, suppliers, customer CRM, shift register closing, and P&L reports
            Acts as Supervisor for PIN-elevation authorization
            Customizable discount ceiling (default 20%, requiring owner authorization if exceeded)
          Frontline Cashier
            Streamlined POS checkout with safety guardrails
            Max discount limit (default 10%, supervisor PIN required for higher)
            Cost valuations, profit margins, danger zone, and settings locked
          Supervisor PIN Elevation
            Managers or Owners can elevate restricted actions without logging out the cashier
            Required for discount over limit, cart void, sales return, khata write-off, and danger zone settings
            Cryptographic cart hash invalidates elevation on cart modification (items, qty, price mode)
            Elevation auto-expires after action completion or strictly 5 minutes
          Granular 33-Permission Matrix across 9 Categories
            Sales & Checkout, Returns & Refunds, Inventory & Catalog, Customers & Dues, Reports & Cash Register, Loyalty & Rewards, Suppliers, Expenses, System Administration
          Repository-enforced mutation security
            Mutations check authenticated user permissions and active status
            PINs must be unique across all accounts
        Terminal Security & LockScreen Integrity
          System back gesture intercepted via moveTaskToBack preventing terminal bypass
          Unauthenticated remote backup restore backdoor removed from MainActivity
          Strict PIN authentication enforced for database restore routines
          Low-stock notification deep-link route preserved across terminal lock and unlock
          Multi-owner policy: co-owner credential step-up auth (target PIN or biometric) prevents silent takeover; mutual audit alerts logged
          Device-credential PIN reset requires high-priority owner acknowledgment on next login
          Configurable idle auto-lock with off / 5m / 15m / 30m / 60m thresholds
          Unified background auto-lock strictly follows idle timeout
          Modal dialog and bottom sheet touch tracking via Modifier.trackUserActivity prevents mid-entry lockouts
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
            Pre-Navigation Permission Interception: Tap on locked cards triggers immediate localized Toast without navigating or flashing screens
            Locked state visual dimming (alpha = 0.72) and dynamic Lock badge
            Scroll-safe layout with preserved view mode preference via rememberSaveable
        Tablet Shell (>= 600dp or Tablet Mode)
          Ergonomic Left-Docked NavigationRail (84dp Width, Expanded Core Domains)
            Official App Logo Header & Outlet Home Shortcut (Electric T Mark)
            1. Sell Mode (Dual-Pane Split Cashier Terminal: 62% Catalog Grid + 38% Live Cart Ticket)
            2. Products Catalog (Adaptive 2-Column Grid, Full-Width Valuation Card, Hidden Redundant Back Arrow)
            3. Sales History (42/58 Master-Detail Layout: Left Date-wise Invoices + Right Live 80mm Thermal Slip & Owner Profit Audit)
            4. Dues & Customer Khata (Adaptive 2-Column Debtor Grid, Full-Width Recovery Card, Hidden Root Back Arrow)
            5. Expenses & Petty Cash Disbursements (Direct Rail Promotion)
            6. Analytics & P&L Reports (Centered 960dp Max-Width Container, Hidden Redundant Back Arrow)
            7. System Settings & Hardware Peripherals (3D Animated Category Icons & Isolated Subpage Scroll)
            8. More Operations Hub Dialog (House for Cash Register & Shift Close, Suppliers, Customer Loyalty, Customers & CRM; Redundant Report/Settings Duplicates Removed)
              Interactive Material 3 Segmented Toggle: List View [ ☰ ] vs Grid View [ ⊞ ]
              Expanded 760dp Max-Width Dialog Constraint eliminating all title truncation
              2-Column Horizontal Cards in Grid Mode (354dp per card) with Zero-Cutoff 2-Line Subheadings
              Executive Full-Width Ledger Rows in List Mode with generous touch padding
              Pre-Navigation Permission Interception with immediate toast feedback and visual lock dimming (alpha = 0.72)
              Persistent rememberSaveable view state & bilingual accessibility labels
            9. Staff Profile / Quick Switch & Lock Terminal Action (Bottom Docked)
      Bangla Localization & Multilingual Typography
        Natural Bangladeshi retail terminology (বাকি, জমা, হালখাতা, ক্যাশ রেজিস্ট্রার, ভাউচার)
        Font padding disabled and line heights tuned across all typography tokens
        PDF Invoice and X/Z report canvas headers use Typeface.SANS_SERIF preventing tofu glyphs
        Pixel-exact 3-column coordinate layout on canvas vouchers (metaLeft, colonX, valueX) preventing font drift
        Invariant ASCII dynamic data preservation (customer phone, invoice IDs, barcodes) for dialability & scan search
        Bengali keyboard digit (০-৯) sanitization via toEnglishDigits across all input fields preventing parse failure
        Zero phantom specifiers & escaped %% symbols preventing Java Formatter crashes
        Fallback thermal preview uses FontFamily.Default for Bangla preventing monospace glyph clipping
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
        Ergonomic Clean Header Architecture & Subheading Standardization
          Section TopAppBars (12 Sections):
            - Subheading text removed across all 12 section screens (History, Products, Cart, Dues, Expenses, Suppliers, Reports, Customers, Loyalty, Register Close, Users, Settings)
            - Title and 3D squircle icon are vertically centered with generous breathing room
            - 100% elimination of text-truncation/ellipsis issues on narrow phone viewports (320-380dp)
            - Action buttons (Stealth/Privacy, Reports, Filters, Export, Add) gain unimpeded touch targets
          Store Operations & Tools Bottom Sheet (Untouched):
            - Retains descriptive header subtitle: 'Management & financial ledgers'
            - Retains all 7 tool cards with dual descriptions and category badges
    Selling workflow
      Selling Form Factors
        Phone POS Terminal
          Top camera scanner viewfinder
          Manual barcode entry
          Category chips
          Sell vs Stock check mode
        Tablet POS Split Terminal (TabletSplitTerminal)
          Dual Orientation Parity (Landscape & Portrait Auto-Adaptation)
            Horizontal Landscape: 58% Catalog Left / 42% Cart Review Right
            Vertical Portrait: 54% Catalog Top / 46% Cart Review Bottom
          Synchronized Top Toolbar (Search + 4 Quick Actions)
            Search input with instant clear
            📷 Scanner (Collapsible camera PIP preview with torch & pause)
            ⌨️ Keypad (Manual barcode dialog entry)
            🔍📦 Stock Check (Supervisor mode toggle with active amber banner: "Tap or Scan")
            ⏸️ Held Carts badge chip with real-time counter & restore
          Catalog Pane
            Category filter chips
            Adaptive 3-5 column product grid (130dp/135dp minSize)
            Live stock status, price badges, and tap-to-add haptic feedback
          Cart Review Staging Pane
            Header with Realistic3DSupermarketCartIcon, review title, 3D Indigo Cart Capsule Button with dynamic quantity badge, Hold & Clear actions
            Multi-line Product Name Wrapping: maxLines = 3, lineHeight = 17sp, zero cutoff for long English/Bengali names
            Ergonomic Touch Steppers: >= 44dp [-] qty [+] and delete icon buttons
            Unified Cart Review Footer: Subtotal row, Estimated Grand Total row, and full-width "Proceed to Cart (৳...) ➔" CTA button invoking onNavigateToCart
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
        Dedicated Cart Screen (CartScreen.kt)
          Full itemized cart list with +/-/delete steppers and note editing
          Pinned Total Cart Value Summary Card (Subtotal, conditional Discount, and Estimated Total in radiant #00C48C emerald)
          Hold Cart and Checkout (grand total formatted) action buttons
        Review Cart modal bottom sheet (`CartPreviewBottomSheet`)
          Line items with full-width names, unit price, total amount pill, and steppers
          Interactive quantity badge opens `ManualQuantityDialog` (identical to Cart screen) for instant manual decimal/whole number entry
          Negative inventory aware with `[Negative Stock Allowed]` pill and stock limits enforcement
          Hold cart & Continue to checkout quick actions
        Hardware barcode scanner adds or increments products
        Live scanned-product update shows quantity and running total
        Unknown products and stock limits show clear scan feedback
        Product Not Found dialog on unrecognized barcode
          Add Product Now opens Products screen with barcode prefilled
          Keep Scanning dismisses and resumes
          Same dialog (`StockCheckNotFoundDialog`) used by Sell and Products screens too  -  one shared design, no more per-screen bottom sheets
        No success snackbar when scanning/adding real products (only the inline scan-update banner); adding a Quick Sale custom item does show a confirmation Toast
        Increase or decrease quantity
          Fractional/decimal quantities for weight or volume units (Kg, Gm, Ltr, ml, Mtr and variants)
            0.25-unit steppers instead of whole-number steppers for those units
            Manual decimal entry dialog with quick-add buttons and quantity presets, values rounded to 2 decimals
            Mirrored in the Scanner screen's scan-and-adjust stepper
          Ergonomic 38dp Stepper Buttons with minimum interactive touch padding (>= 48dp target)
          Negative Inventory Policy: enforces stock sufficiency unless enabled in Settings or elevated by Supervisor PIN
        Enter quantity manually
          Manual decimal & whole-number entry dialog (`ManualQuantityDialog`)
          Accurate available stock display: cleanly decoupled from validation limits (never displays raw `Long.MAX_VALUE` / `Double.MAX_VALUE`)
          Negative Inventory Awareness: displays an amber `[Negative Stock Allowed]` (`[অতিরিক্ত স্টক অনুমোদিত]`) badge when quantity exceeds on-hand stock and negative stock is authorized
          Custom & Synthetic Sale Items: omits stock counter for ad-hoc quick sale products/services
          Dynamic quick-add increment and preset chips clamped to actual stock only when negative inventory is disallowed
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
          Adds an ephemeral synthetic Product (barcode `CUSTOM-<timestamp>`, stockQty 999999)  -  never written to the products table, does not affect real inventory, and never matches a prior quick-sale line by barcode
        Per-line item note (warranty/serial/instructions)
          Editable per cart line, printed on thermal receipts and PDF invoices
          Manageable Note Presets chip picker (add/edit/delete), also embedded in the Quick Sale dialog's note field
        Hold cart
          Hold current cart for later (gated by sales.hold_cart with SupervisorAuthDialog elevation)
          Held carts bottom sheet
          Restore held cart
            Stock availability validation: tracks cumulative barcode demand across all lines (stockAllocationMap) against live catalog inventory; unavailable quantities remain safely saved in the held-cart record and a localized alert notifies the cashier
            Preserves Quick Sale items, line notes, price mode, prepared payment/split rows, loyalty state, applied store credits, voucher codes, voucher balances (initialAmount & remainingBalance), and synchronizes customer identity (customerName & customerPhone)
            Fallback active StoreCreditVoucher synthesized if external voucher record is missing, guaranteeing redeemedVoucher is never null when storeCreditApplied > 0
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
            Strict Zero/Empty Tender Insufficiency Validation (Cash received <= 0 or empty is flagged with red error warning, disabling checkout; v2.6.62)
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
          Edit (owner only) voids the just-completed sale  -  restoring stock, due balance, and loyalty points  -  then reloads its items into the cart for changes and re-checkout
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
        Inventory Alerts Center (InventoryAlertsCenterBottomSheet)  -  modal bottom sheet with 3 tabs (Expired / Expiring Soon / Low Stock); each item card has Edit and, for Low Stock, a one-tap +10 Quick Restock button
      Fast-Action Toolbar
        Integrated search input with clear button
        1-Tap 📷 Barcode Scanner toggle
        Single "Tune" filter icon opening a Filter & Sort bottom sheet (categories, 2×2 stock-health grid, date/expiry filters, 6 sort options, price range)  -  the toolbar row itself does not contain filter chips or category pills
        Dual View Switcher (☰ List vs ⊞ 2-Column Grid)
        Add Product is a separate floating action button, not part of the toolbar row
      High-Density Product Cards & Workflows
        Full-Width Product Title & Direct Stock Check
          Tapping product name directly opens HardwareStockCheckDialog (Stock Adjustment & Audit)
          Title wraps cleanly up to 2 lines across full card width (zero premature clipping or truncation)
        Monospace Barcode Hologram & Code Generator
          Monospace #SKU/Barcode chip with animated Realistic3DHologramLaserCodeIcon (12dp)
          Tapping barcode chip directly opens ProductCodeImageDialog (Thermal Label & Barcode/QR Studio)
            Product Hero Card with category tag, 3D avatar icon, live stock indicator dot, and pagination switcher (< 1 of N >)
            Pinned Thermal Sticker Preview Canvas with DPI indicator (203/300 DPI) and Zoom Inspect modal
            5 Quick Template Presets (Standard Retail, Grocery & Expiry, Jewelry Mini, Apparel Dual-Price, Warehouse Code 128)
            Segmented 3-Tab Architecture (🏷️ Label Setup, 🏢 Store & Fields, 📅 Dates & Scale)
            Code Format Dropdown (Code 128, EAN-13 recommended, EAN-8, Code 39, QR Code) with narrow barcode density warning
            Selling, MRP, Wholesale price tier cards + compact Dual Price row (struck-through MRP + discounted price preview)
            Copies Stepper with Auto (stock-matched) vs Manual toggle mode and quick preset chips (1, 2, 3, 5, 12, 50, 100, Stock)
            Label Dimensions Dropdown with all 8 thermal presets (25x15 to 100x150 mm) + custom mm dimensions
            Compact 2-column Additional Custom Field with quick presets (+ Batch, + Size, + Weight, + Origin, ✕ Clear)
            Date Tracking (Mfg vs Pkg toggle, Mfg date, Expiry date)
            Element Scaling Card with Reset All and 8 independent scalers with range sliders and +/- step buttons
            Fixed Bottom Action Dock with dynamic print copy count, PDF share, and PNG export
          Tapping available thumbnail opens full-view high-resolution image modal with title, barcode, and price
          Thumbnail box stays completely hidden when no image is uploaded (maximizing horizontal title room)
        Compact List View (ProductItemRow)
          Sleek unified single-row layout across phone and tablet matching interactive preview
          Leading visual: Image Thumbnail (44dp, tap to enlarge) or Checkbox in batch mode; completely hidden if no image
          Main Info Column: 2-Line Title (tap to adjust stock) + Monospace Barcode chip with 3D Holographic Laser Stamp (tap to generate label) + Category tag
          Price & Stock Column: Bold Selling Price on top + 3D Stock Status Pill on bottom (tap to adjust stock)
          Action Group: 1-Tap [+] Quick Add button (36dp) + [⋮] Overflow Menu (Edit, Stock Adjust, Code, Delete)
          Native Long-Press card activation for batch selection mode
        Responsive Grid View (ProductGridCard)
          Compact 3-row layout matching interactive preview
          Row 1: 38dp Thumbnail on left (tap to enlarge; completely hidden if null) + 3D Stock Status Pill on right
          Row 2: Bold 2-Line Title (tap to adjust stock) + Barcode chip with laser code stamp + Category tag
          Row 3: Bold Selling Price on left, 1-Tap [+] Quick Add button (30dp) + [⋮] Overflow Menu on right
      Custom Category Manager (inside Add/Edit Product form)
        User-defined, icon + bilingual (English/Bengali) categories, editable and resettable to defaults
        Only shapes the dropdown offered when creating/editing a product
        Does not affect the Category Filter Pills Row, which is still derived live from whatever category strings already exist on products
      Generate Code Image (Barcode & QR Label Generator)
        1D Barcode (Code128/EAN) & 2D QR Code toggle
        Store Name branding (M/S AL KAWSAR STORE)
        Live visual thermal label preview
        Show/Hide Price, Code Number, Store Name, Date Tracking
        Price Tier on Label Selector:
          Selling Price: rendered as "Price: <Amount>" (e.g. Price: ৳70)
          MRP Price: rendered as "MRP Price: <Amount>" (e.g. MRP Price: ৳85)
          Wholesale Price: rendered as "Wholesale Price: <Amount>" (e.g. Wholesale Price: ৳62)
          Dual Display (MRP + Offer): supermarket style bargain label rendered as "MRP: ৳90 | Offer: ৳70" (or Bengali "এমআরপি: ৳৯০ | অফার: ৳৭০")
          Uppercase category tags: SELLING PRICE, MRP PRICE, WHOLESALE
          Micro-customization toggles:
            Show Unit (/Pcs): appends unit (e.g. /Pcs, /পিস) to offer price
            Bangla Digits (৳৭০): renders numerals in native Bengali script (০-৯)
          Real-time dynamic canvas preview updating with "Not set" / "নির্ধারিত নয়" indicator when price is 0
          Persistent user preference saved via SharedPreferences ("product_code_image_settings")
        Batch Barcode Printing:
          Direct selection for Selling, MRP, Wholesale, and Dual Display price tiers with Unit & Bangla Digits support
          Bulk PDF multi-page label generation applying active price tier formatting across all items
        Mfg/Pkg & Expiry Date pickers
        Physical label dimensions (25×15, 30×20, 40×25, 45×35, 50×30, 60×40, 100×150mm, plus custom  -  7 presets)
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
          (Store credit option was removed  -  no ledger ever backed it)
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
        Owner can add due, collect payment, change credit limit, or delete ledger  -  "deleting" archives the due (isArchived) rather than cascading away its DuePayment history
        Cashier can search, inspect, call, remind, and share statements but cannot mutate balances
        Repository authorization protects every balance mutation even if called outside the screen UI
        Every write path normalizes the customer's phone (normalizePhone) before touching customer_dues/due_payments/due_charges/customers/customer_loyalty, so the same customer always resolves to one canonical ledger row regardless of how the phone was typed, autofilled, or imported
      Digital Khata Ledger  -  unified Transaction History (DuePayment + DueCharge)
        Two-sided ledger: DuePayment is the credit side (collections), DueCharge is the debit side (amounts that increased the balance)
          DueCharge reason "Sale"  -  auto-logged when a Due (or Split-Due) sale completes, linked to the sale via saleId
          DueCharge reason "Manual"  -  logged when the Add Due dialog is used to add a standalone charge; the dialog's Note field is saved onto the charge instead of being discarded
        Transaction History merges both sides by timestamp: collections shown in green (+৳), charges shown in red (−৳), each labeled "Due Sale" or "Due Added"
        Delete Payment (Owner only)  -  reverses a mistaken collection: removes the DuePayment row and adds the amount back to the outstanding balance
        Delete Charge (Owner only)  -  reverses a charge: removes the DueCharge row and subtracts the amount from the outstanding balance
          Deleting a "Due Added" (manual) charge is a full undo
          Deleting a "Due Sale" charge only undoes the ledger entry and its share of the balance  -  the underlying sale itself is untouched; the confirmation dialog points to Delete Sale (History screen) for a full undo (which also restocks items and reverses loyalty)
          Delete Sale (History screen) already reverses the due amount on its own; it also deletes any DueCharge row linked to that sale by saleId so history doesn't keep showing a charge for a sale that no longer exists
        due_charges table added in DB schema version 23; existing straight-Due sales were backfilled with charge rows on upgrade (Split-Due portions are not backfilled  -  JSON-encoded, not practical in plain SQL  -  but are logged for every Due/Split sale going forward)
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
          WorkManager-based scheduling; only one pending scheduled reminder per customer phone number  -  scheduling a new one silently replaces the prior one
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
      Views (Outstanding, Overdue, Settled, Payments)  -  Overdue = outstanding balance untouched 14+ days or at/above 80% of credit limit
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
      In-Screen Permission Enforcement & Supervisor Override
        Add Supplier, Receive Inward Stock, Pay Due, Edit Supplier, and Delete Supplier are strictly gated by suppliers.manage
        Staff without permission trigger SupervisorAuthDialog for temporary PIN elevation without requiring logout
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
          Granular Permissions Matrix modal: Customize all 33 permissions across 9 functional groups per user
          Duplicate PIN protection prevents PIN collisions
          Owner safety lock: cannot deactivate or delete the last remaining owner account
          Log out returns to the PIN lock screen
      Hardware and cash drawer
        Printers settings card
          Receipt printer
            Print method selector: Direct (Bluetooth/USB), Android print service, Auto
            Direct ESC/POS driver for Bluetooth SPP and USB Host (58mm / 80mm roll width)
            Paper auto-cut configuration: Full cut, Half cut, or No cut with feed lines slider (0-10)
            Cut command style selector: GS V (Standard), ESC i / m (Legacy), GS V n (Function B)
            Dual test cut actions: Short Test Cut and 1200px Long Receipt Test Cut with real-time feedback
            Deterministic socket drain: GS r 1 sequential status query before closing RFCOMM socket
            Adaptive Bluetooth chunk pacing (15-35 ms) matching printer UART speed to prevent buffer overruns
            Post-raster settling delay (250 ms) allowing thermal printhead to complete printing before cutter actuation
            Receipt print darkness slider (0-15) with software luminance thresholding
            System print service note when in SYSTEM mode
            Test receipt button with real-time feedback
          Label printer
            Print method selector: Direct (Bluetooth/USB), Android print service, Auto
            Direct TSPL driver for Bluetooth SPP and USB Host (Deli E886BW and standard TSPL at 203 DPI)
            Label gap adjustment (mm) and darkness slider (0-15)
            Test label button with real-time feedback
            Printer conflict warning banner if set to the same device as receipt printer
          Printer conflict prevention
            Validation dialog blocks assigning the same Bluetooth or USB printer to both receipt and label roles
            Direct print dispatch guard prevents TSPL garbage output on ESC/POS receipt printers
          Deferred slider persistence: local Compose state during drag and save on onValueChangeFinished
          Cash drawer kick
            Option for Same as receipt printer or custom address/device key
            Enable or disable automatic drawer kick for Cash and Split sales
            Test Kick sends the ESC/POS pulse through Bluetooth SPP or USB bulk endpoint
          Instant persistence on all printer settings via SettingsViewModel
          Combined printer picker dialog with Bluetooth and USB tabs, refresh button, and show-all toggle
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
        Centralized Single Source Engine
          LocalAppLanguage CompositionLocal
          formatTaka and formatMoney Bengali numeral rendering throughout UI
          Bengali-aware DateFormatters with Locale.US file export isolation
          100% localized X/Z sales reports and repository error toasts
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
          Portable JSON restore normalizes every phone-keyed row on import (dues, duePayments, dueCharges, customerLoyalty, loyaltyTransactions, scheduledDueReminders, dueReminderLogs, customers) so re-importing an older backup can't reintroduce a split (raw-phone vs. normalized-phone) ledger  -  see Repair Duplicate Customer Ledgers in Data management for cleaning up ledgers already split before this fix
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
          Local JSON Restore File (Replace/Merge) writes through Room DAOs on the same live database connection  -  no close/reopen, no ViewModelStore/recreate() involvement, so it isn't subject to the encrypted-restore staleness issue above
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
          Preview-then-confirm tool: finds customer_dues/due_payments/due_charges/customers rows split across a raw (un-normalized) vs. normalized phone key for the same customer  -  a legacy data-corruption class fixed going forward by normalizing every write path, but pre-existing on upgrade
          Preview lists each affected customer, how many rows, and the resulting merged balance before anything is touched
          Merge sums outstanding balances into the canonical (normalized) phone, re-keys due_payments/due_charges onto it (re-keyed before the stale row is deleted, to satisfy the ON DELETE CASCADE foreign key), then deletes the stale row
          Idempotent  -  re-running after a clean merge reports "No Duplicate Ledgers Found"
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
          stockQty is REAL (Double), not Int  -  supports fractional quantities
          mrpPrice, discountedPrice  -  back the Price Mode selector (Sale/MRP/Bulk)
        Sale
          Tax rate and tax amount applied at sale time
        SaleItem
          quantity is REAL (Double)
          note  -  per-line item note (warranty/serial/instructions), printed on receipts
        CustomerDue
          creditLimit
          isArchived  -  "deleting" a due now archives it (soft-delete) rather than cascading away its DuePayment history
        DuePayment
          Payment method
          Reference note
        DueCharge
          The debit side of the due ledger, paired with DuePayment (credit side) for a full Transaction History statement
          reason  -  "Sale" (linked via saleId, auto-logged on Due/Split-Due sale completion) or "Manual" (from the Add Due dialog, note field included)
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
          cashRefunds  -  cash refunded during the shift
          shiftStartedAt  -  start time of the shift this closing settles, from RegisterShiftState
          discrepancyReason  -  dedicated audit trail reason for counted-vs-expected physical drawer cash variance (distinct from closing notes; DB schema v28)
          Closing the register resets RegisterShiftState to start a fresh shift with float 0
        CustomerLoyalty
          isActive  -  soft-deactivation flag filtering the active-members list
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
        StoreCreditVoucherDao
        StoreCreditLedgerDao
        Database migrations
          Current schema version 27
          Migration 11→12: adds sale_items.note, customer_dues.creditLimit; creates inventory_adjustments table
          Migration 12→13: converts products.stockQty, sale_items.quantity, return_items.quantity from INTEGER to REAL (full table rebuild)  -  the DB-level enabler of fractional quantities
          Migration 13→14: adds products.mrpPrice and products.discountedPrice (default 0.0)  -  backs the Price Mode selector
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
          Migration 25→26: adds cashierId, shiftId to sales and registers, creates composite indices for shift and cashier filtering
          Migration 26→27: creates store_credit_vouchers table (code TEXT PRIMARY KEY NOT NULL, originalAmount REAL NOT NULL, remainingBalance REAL NOT NULL, issuedToName TEXT, issuedToPhone TEXT, issuedAt INTEGER NOT NULL, expiryDate INTEGER, isActive INTEGER NOT NULL, originalSaleId INTEGER, originalInvoiceNo TEXT, notes TEXT, redeemedAt INTEGER); creates store_credit_ledger table (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerId INTEGER NOT NULL, amount REAL NOT NULL, balanceAfter REAL NOT NULL, type TEXT NOT NULL, source TEXT NOT NULL, referenceId TEXT, notes TEXT, createdAt INTEGER NOT NULL); creates indexes index_store_credit_ledger_customerId and index_store_credit_vouchers_isActive
          Explicit migrations preserve existing business data
          BundledSQLiteDriver supplies consistent SQLite capabilities across supported Android versions
      Preferences
        SettingsDataStore
        Invoice settings (58mm / 80mm roll width, directBluetoothPrintEnabled, directBluetoothPrinterAddress, allowNegativeInventory, showDueReceiptVerificationQr, shop identity, logo, QR links, footer notes, showDueReceiptLogo, showDueReceiptCustomNotes, dueReceiptCustomTitle, dueReceiptCustomNotes)
        Shop settings
        CategoryItem list, QuickSalePreset list, DueTemplate list, Note presets list
          Plain Kotlin data classes with hand-rolled JSON (de)serialization, persisted as JSON strings inside Preferences DataStore  -  not Room tables
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
9. **Printing (Receipts & Labels):** Dual architecture chosen independently in Settings. Receipts: direct Bluetooth and USB Host ESC/POS (58mm/80mm, with configurable auto-cut mode and feed lines) or Android print service via PrintManager. Labels: direct Bluetooth and USB Host TSPL (e.g. Deli E886BW at 203 DPI) or Android print service PDF. Modes supported: AUTO (direct Bluetooth or USB if configured, fallback to system dialog on unconfigured or failed print), BLUETOOTH (direct only with localized error toast on failure), SYSTEM (Android print service dialog only). Unified entry points in BillActions.kt route all callers (sales receipts, store credit vouchers, Settings test prints, X/Z reports, due repayment slips, product code labels).
10. **First app launch:** splash screen → no users exist yet → Lock screen prompts to create the owner account (name + 4-digit PIN, entered twice to confirm) → owner is signed in and lands on Sell.
11. **Return app launch:** splash screen → Lock screen requires an existing user's PIN → matching user is signed in and lands on Sell.
12. **Adding & Managing Staff:** Settings → Staff & PINs (owner only) → tap "+ Add Staff Account" → specify Name, unique 4-digit PIN, Role (`Admin / Owner`, `Store Manager`, `Frontline Cashier`), and Cashier Max Discount Cap → customize any of the 33 permissions via the Permissions Matrix dialog → staff member can sign in with their unique PIN.
13. **Sale return:** open a sale in History → Return items (Manager / Owner or Supervisor authorized) → choose returned quantity per line item, capped at quantity sold minus already returned → choose refund method → process return → stock is restocked and the refund is recorded against the sale.
14. **Hold and resume cart:** build a cart → Hold cart (gated by `sales.hold_cart` or Supervisor elevation) → cart is saved to `HeldCart` and cleared from the active session → later reopen the held carts sheet from Cart or Scanner → restore a held cart back into the active cart, or delete it.
15. **Split payment sale:** at checkout, select Split payment → allocate the total across two or more methods (Cash, Card, bKash, Nagad, Rocket, Due) with per-method amount and wallet/card details → complete sale → receipt itemizes each payment method and amount.
16. **Loyalty management and redemption:** More hub → Customer Loyalty (gated by `loyalty.manage`) → search/add members or adjust points. At checkout, view the customer's points balance → redeem some or all against the sale total. Purchases earn one point per ৳100; partial returns and sale deletion reverse the earned points and lifetime spend that no longer apply, while previously redeemed points are restored when a sale is deleted. Every point change (sale-time earn/redeem, sale-deletion reversal, manual owner adjustment) is recorded as an immutable `LoyaltyTransaction` audit row in the same DB transaction as the balance update.
17. **Register close (shift cash reconciliation):** More hub → Register Close (Manager / Owner) → review cash sales, digital sales, cash in/out, and cash refunds since today's latest close → enter starting float → Count Drawer & Close Register → enter physical denomination notes counted → app computes expected system cash and counted-vs-expected variance → complete closing → a `RegisterClosing` record is saved with the authenticated operator's name and appears in history. A later close begins immediately after the previous close instead of recounting the entire day. Closing resets the persisted `RegisterShiftState` singleton to start a fresh shift with float 0.
18. **Z-Report (daily closing P&L):** Register Close screen → tap Z-Report → view today's read-only gross sales, gross margin, discounts, expenses, net profit, and payment breakdown as a simulated thermal receipt → Print, Save as JPEG, or Share → Share opens a format picker (JPEG, PDF, or CSV) and hands off to the system share sheet.
19. **Mid-Shift X-Report:** Register Close screen → tap X-Report → same interim summary/print/save/share flow as Z-Report, but non-final and does not close the register  -  useful for a shift-in-progress checkpoint.
20. **Supervisor PIN Elevation:** When a frontline cashier attempts a privileged action (discount exceeding personal limit, cart void, returns/refunds, deleting items, credit limit override, holding cart without permission), a `SupervisorAuthDialog` modal is triggered with localized Bengali & English descriptions. Any active Store Manager or Admin/Owner can enter their 4-digit PIN to authorize the single transaction without logging out the cashier or interrupting checkout.

## Critical business rules

- **Offline-First Guarantee:** POS operations (scanning, selling, checkout, inventory, dues, receipt printing) must never require an internet connection. Google Drive is utilized strictly for backup and restore.
- **Multi-Tier Role-Based Access Control (RBAC):**
  - **Admin / Owner:** Complete administrative sovereignty. Can customize all permissions across all roles, manage staff accounts, access system settings, cloud backups, and the danger zone.
  - **Store Manager:** Manages daily operations including inventory stock, inward purchase invoices, supplier ledgers, customer CRM, shift closings, and P&L analytics. Empowered to act as a Supervisor for frontline overrides.
  - **Frontline Cashier:** Focused frontline POS checkout. Sensitive cost prices and profit margins are masked; administrative and system configuration zones are locked.
  - **33 Granular Permissions Matrix:** Functional permissions divided across 9 categories (Sales & POS, Returns & Refunds, Inventory & Catalog, Customer Dues & Credit, Reports & Cash Register, Customer Loyalty & Rewards, Suppliers, Expenses & Petty Cash, System Administration). Can be toggled per account by Admin.
  - **Supervisor Authorization Elevation:** Privileged cashier actions trigger a modal PIN elevation allowing any active Manager or Owner to authorize the action on the spot with fully localized context.
  - **Active / Suspended Flag:** Accounts can be suspended immediately without deletion, revoking login access while preserving transaction audit history.
- **Authenticated Encryption:** Cloud backups must be encrypted with AES-256-GCM using keys derived via PBKDF2 (100,000 iterations) from the stable Google account subject ID and a random per-backup salt to ensure cross-device restoration on replacement devices.
- **Database Consistency:** Database files must be flushed with `PRAGMA wal_checkpoint(FULL)` prior to packaging.
- **Rollback Guarantee:** An automated pre-restore backup snapshot must be taken prior to restoring; if restore validation fails, the database is rolled back automatically. These local safety-rollback snapshots (`app/files/safety_rollback/pre_restore_*.sqlite`) are bounded to the 3 most recent and 7 days old (`BackupPackageManager.MAX_ROLLBACK_SNAPSHOTS`/`MAX_ROLLBACK_AGE_MS`), pruned both before and after each restore  -  this is distinct from the cloud Drive retention policy below.
- **Storage Sandbox & Retention:** Backups are stored in Google Drive's hidden `appDataFolder` (`drive.appdata` scope) and automatically pruned to the selected retention limit (7, 14, or 30).
- **No Seeded Business Data in Defaults:** `InvoiceSettingsData()`'s default values (and every fallback that reads shop identity) must remain generic placeholders (e.g. "My Shop", blank address/phone/Facebook URL)  -  never a real shop's name, address, phone number, or social link. A previous version hardcoded one customer's actual business details as the class defaults and DataStore fallbacks, which meant every fresh install and every factory reset silently re-populated that customer's real data instead of a blank slate.
- **Factory Reset Must Revoke Cloud Auth:** `DataRepository.resetFactoryDefaults()` must call `GoogleDriveHelper.revoke()` for any connected Drive account before completing, in addition to clearing local preferences  -  otherwise Google's silent re-authorization (`authorizeWithoutUi`, run on every Settings screen load) reconnects the same account and re-persists its identity immediately after reset.
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
- There is exactly one "Product Not Found" UI in the app: `StockCheckNotFoundDialog` (defined in `ProductsScreen.kt`, non-private so other screens can use it). It is used by Products (both stock-check-mode and normal add-to-cart-mode scanning), the Sell/Scanner screen (camera, hardware scanner, and manual "Enter Code" all route through it), and Cart. Previously each screen had its own bespoke not-found UI (an AlertDialog in Products, a separate `ProductNotFoundBottomSheet` in ScannerScreen, and Products' normal-mode scan silently auto-opened the add form with only a snackbar)  -  do not reintroduce a screen-specific variant; extend the shared dialog instead.
- Two distinct composables are both named `ZReportDialog`, both now defined in `RegisterCloseScreen.kt`: one overload (`data: SalesReportData, ...`) is the read-only printable P&L summary; the other (`expectedCash: Double, ...`, imported from `AdvancedPosFeatures.kt`) is the cash-count/denomination reconciliation dialog. They are unrelated despite the shared name  -  resolved by Kotlin overload matching, not by which file declares them  -  do not assume one calls the other.
- The P&L Z-Report in Register Close is always scoped to today only (no period picker); it is a different scope than the Sales Report screen's selectable date range.
- A day can have multiple `RegisterClosing` records for multiple shifts; each closing only includes activity after today's previous closing timestamp. There is no lock preventing further sales after a close.
- Expense and register-closing audit names are overwritten at repository entry with the authenticated owner's session name; UI/default `"Admin"` values cannot spoof the stored operator.
- A Quick Sale line item creates an ephemeral, non-persisted `Product` (barcode `CUSTOM-<timestamp>`, `stockQty = 999999`) that is never written to the `products` table; it never decrements real inventory, and two quick-sales with the same title are still distinct barcodes (different timestamps), so they will never match a prior sale's line item by barcode.
- Cash Drawer Kick uses Bluetooth SPP or USB bulk transfer to write the ESC/POS pulse bytes to a paired or connected thermal printer. Bluetooth pairing remains in Android Bluetooth settings while USB printers are detected via Android USB Host with runtime permission prompts.
- Fractional/decimal quantity support (`Product.stockQty`, `SaleItem.quantity`, `ReturnItem.quantity`, `CartItem.quantity` are all `Double`) is inferred purely from the product's `unit` string matching a fixed list (kg, gm/g, ltr/liter/litre, ml, mtr/meter/metre) everywhere it's used  -  there is no explicit `isFractional` flag on `Product`.
- Price Mode (Sale/MRP/Bulk) reads `Product.sellingPrice` / `mrpPrice` / `wholesalePrice` respectively, each falling back to `sellingPrice` when the tier-specific field is 0/unset.
- A Due Reminder can only have one pending scheduled instance per customer phone number at a time (WorkManager unique work keyed by normalized phone digits); scheduling a new one replaces the previous one. Pending reminders are mirrored in Room and can be listed/cancelled from Dues; the worker removes its tracking row after completion.
- The product grid and list card layouts have been fully redesigned and verified on a physical connected device to match the interactive preview (product_screen_redesign_preview.html) with 100% fidelity: unified single-row ProductItemRow, compact 3-row ProductGridCard, hidden image placeholders when images are unavailable, and animated 5-bar cyan laser holographic code stamps.

## Current Android package

- Application ID: `com.techyfi.pos`
- Version code: `220971`
- Version name: `2.6.94-Final-V21-10-2026`
- Upgrade rule: retain the same application ID and signing identity, and always increase the version code.
- Signing SHA-1:
  - Active key (from Sep 29, 2026 onwards): `08:4A:15:4B:BB:A4:61:B7:98:C7:E2:62:F8:CE:C3:BA:8B:52:3A:FA` (keystore at `~/.android/debug.keystore`).
  - Legacy key (prior to Sep 29, 2026): `D6:7B:BD:1C:48:92:D4:FC:7F:D5:1F:D1:CB:22:79:B5:6F:C8:AD:9C`.
  - Both must be registered as Android OAuth 2.0 Client IDs in Google Cloud Console under `com.techyfi.pos` for Google Drive backup/restore.
- Database version: `38` (v15→16 reminder tracking, v16→17 purchasePriceAtSale snapshot, v17→18 return refund-splitting, v18→19 register shift states, v19→20 due reminders, v20→21 unified customers CRM, v21→22 users RBAC, v24→25 sale audit logs, v33→34, v34→35, v35→36, v36→37, and v37→38 cleaning legacy voucher phone fallback with nullable customerPhone support).
- Current build: `Techyfi-POS-Final-V21-10-2026.apk` (versionCode 220971) - R8 minified and resource-shrunk, streamlined single-row list card and compact 3-row grid card layout matching interactive preview with 100% visual fidelity, complete image placeholder hiding when null, 5-bar cyan laser holographic code stamps, 40dp command hub with camera viewfinder scanner, funnel filter, and hollow grid/list switchers, and real device verified on Xiaomi MI 8 (`cb5fdfb5`).
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
- Thermal printer architecture & byte formatting: `app/src/main/java/com/example/techyfipos/printer/` (`BluetoothPrinterTransport.kt` for shared resilient Bluetooth socket communication with insecure RFCOMM fallback; `UsbPrinterTransport.kt` for Android USB Host bulk transfer; `PrinterTarget.kt` for unified Bluetooth and USB printer addressing; `EscPosPrinterDriver.kt` for ESC/POS receipt raster generation with software luminance thresholding and GS V paper auto-cut; `TsplLabelPrinterDriver.kt` for TSPL label raster and ASCII command generation)
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
- Scheduled due reminders: `app/src/main/java/com/example/techyfipos/utils/DueReminderScheduler.kt` (WorkManager scheduling, `SchedulePreset` enum), `app/src/main/java/com/example/techyfipos/utils/DueReminderWorker.kt` (`CoroutineWorker`  -  SMS send / WhatsApp-deeplink notification)
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
- Restored a real 521-invoice local JSON backup (`techyfi-pos-backup-2026-08-28.json`) via Settings → Backup & Restore → Restore File (Replace all data)  -  this path uses the same live database connection (no close/reopen, no `activity.recreate()`), so it is not subject to the ViewModelStore staleness bug; the restore completed and the resulting History list, revenue summary, and margin figures reflected the imported data correctly.
- The Google Drive encrypted-restore path itself (the one the ViewModelStore fix targets) was not exercised end-to-end in this session  -  it requires a connected Google account not available in the test environment. The fix is a direct correction of a verified root cause (config-change-style `recreate()` retaining the ViewModelStore across a database swap), but has not been confirmed against the field report that prompted it.

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

## V29 - Store Credit & Walk-in Customer Credit Voucher Architecture (V29 / V11-09-2026)

- Final artifacts: `Techyfi-POS-Final-V29.apk` (and `Techyfi-POS-Final-V11-09-2026.apk`), versionCode `220911`, versionName `2.6.34-V11-09-2026`.
- Verified file locations:
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V29.apk`
  - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V11-09-2026.apk`
- Deliverables Convention:
  - All built APKs and mind maps (`APP_MIND_MAP.md`) strictly stored in `/Users/shahidulhaque/App Dev/Techyfi POS/`.
- Database Schema Version: `27` (`store_credit_vouchers`, `store_credit_ledger` via `MIGRATION_26_27`).
- Features & Fixes implemented:
  - **[SC1] Customer Store Credit Balance Checking & CRM Management (`CustomerDossierSheet.kt` & `CustomersScreen.kt`)**:
    - **Live Store Credit Wallet Card**: Embedded in the customer dossier sheet showing real-time balance `🪙 ৳...` with high-contrast gradient card, clear balance status, and instant check capability.
    - **Owner/Manager Balance Adjustment**: Integrated `[ ⚖️ Adjust ]` action with `AdjustStoreCreditDialog` enabling credit addition or deduction with mandatory audit notes.
    - **Store Credit Audit Ledger**: Added `CREDIT_LEDGER` audit history tab in customer dossier rendering immutable history of adjustments, return refunds, and sale redemptions (`StoreCreditLedgerEntry`: `CREDIT` vs `DEBIT`, balance after, source, notes, formatted timestamp).
    - **CRM List & Table Indicators**: Added `🪙 Credit: ৳...` badges to customer cards and table rows for rapid visual identification.
    - **Filters & Sorting**: Added `[ With Store Credit ]` filter chip and `STORE_CREDIT` (High to Low / Low to High) sorting options in CRM screen.
  - **[SC2] 1-Tap Store Credit Tender & Split Payment in POS Checkout (`CartScreen.kt` & `DataRepository.kt`)**:
    - **Dynamic Store Credit Banner**: In Complete Sale dialog, when a customer with positive store credit is selected, an emerald banner automatically renders `🪙 Store Credit Available: ৳...` with a 1-tap `[ Apply Credit ]` action.
    - **Flexible Bill Offsets**:
      - If store credit >= total bill: Bill is 100% covered; payment method automatically switches to `"WALLET"` / Store Credit; 1-tap `[ Complete Sale (Covered by Credit) ]` finishes checkout.
      - If store credit < total bill: Store credit covers partial amount and remaining balance is assigned to secondary payment method (Cash, bKash, Card, etc.), automatically recording a split payment (`splitDetailsJson`).
    - **Atomic Deduction & Ledger Audit**: `DataRepository.completeSaleWithNotes` atomically deducts customer store credit and writes a `StoreCreditLedgerEntry` (`type = DEBIT`, `source = SALE_PAYMENT`).
    - **Real-Time UI Sync**: Customer's updated balance immediately reflects across cart, CRM, and customer state flows.
  - **[SC3] Walk-In Customer Returns Dual-Resolution Workflow (`ReturnDialog.kt` & `SalesViewModel.kt`)**:
    - **Walk-in Return Detection**: When processing returns for walk-in/unregistered sales (phone is null or blank) and refund destination `"WALLET"` / Store Credit is selected, an amber dual-resolution card appears offering two options:
      - **Option 1: Quick Customer Registration**: Input customer phone and name to immediately register customer and deposit refund directly into their account.
      - **Option 2: Issue Scannable Credit Voucher**: Issues a bearer credit voucher formatted as `SC-XXXX-XXXX` with 90-day default validity.
    - **Voucher Generation & Persistence**: `DataRepository.processReturn` generates `StoreCreditVoucher` linked to `originalSaleId` and `originalInvoiceNo` stored in SQLite DB.
    - **Voucher Presentation Dialog**: Renders `StoreCreditVoucherIssuedDialog` displaying large scannable code, voucher value, expiry date, and a 1-tap `[ 📋 Copy Code ]` button for clipboard copy.
  - **[SC4] Credit Voucher POS Redemption (`CartScreen.kt` & `DataRepository.kt`)**:
    - **POS Voucher Redemption Entrypoint**: Added `"🎟️ Have a Credit Voucher?"` button in Complete Sale dialog.
    - **Redemption Dialog & Lookup**: `RedeemStoreCreditVoucherDialog` allows scanning or typing voucher code (`SC-XXXX-XXXX`); validates voucher existence, `isActive`, and non-zero `remainingBalance`.
    - **Automatic Deduction & Split Tender**:
      - If voucher balance >= cart total: Cart total is fully paid by voucher; remaining voucher balance is preserved in `store_credit_vouchers.remainingBalance` for future purchases.
      - If voucher balance < cart total: Voucher balance is applied as partial credit and remaining bill is split with primary payment method.
  - **[SC5] Bilingual Localization & Accessibility**:
    - Complete Bengali and English strings in `strings.xml` and `values-bn/strings.xml` following `bangla-language` guidelines (`স্টোর ক্রেডিট`, `ভাউচার কোড প্রয়োগ করুন`, `ক্রেডিট ভাউচার ইস্যু করুন`, `ভাউচার কোড কপি করা হয়েছে`).
    - Minimum 48dp touch targets across all dialog buttons, chips, and input clear buttons adhering to `android-ui-ux`.
  - **[SC6] Deliverables & Verification**:
    - Verified release build: `./gradlew assembleRelease` -> BUILD SUCCESSFUL.
    - Output artifacts verified at `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V29.apk` and `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V11-09-2026.apk`.
    - Comprehensive live Android emulator verification on `emulator-5554` testing manual adjustments, CRM filtering, POS checkout credit offsets, walk-in return voucher generation, and voucher checkout redemption.

## V30 - Store Credit Voucher Thermal Slip Preview, ESC/POS Printing, Multi-Channel Sharing & Thermal Receipt Balance Integration (V30 / V11-09-2026)

- **Architecture & Workflow Overview**:
  - **[V30-1] Thermal Voucher Slip Engine & Code-128 Barcode Generation (`BillPdfGenerator.kt`)**:
    - **Dual Roll Width Formatting**: Supports standard 58mm (384px canvas width, 24 char width) and 80mm (576px canvas width, 32 char width) ESC/POS thermal printing.
    - **Header & Branding**: Dynamic rendering of store logo (`BitmapFactory`), registered business title, branch address, and telephone lines with centered alignment.
    - **Prominent Highlight Value Box**: High-contrast bordered card displaying voucher value (`ভাউচারের মোট মূল্য ৳...` / `VOUCHER VALUE ৳...`) in bold 28-34sp typography.
    - **Scannable Code-128 Barcode**: Generated on-device using ZXing `MultiFormatWriter().encode(voucher.code, BarcodeFormat.CODE_128, ...)` and rendered with high-contrast sharp black/white rasterization. Includes bracketed human-readable code fallback `[ SC-XXXX-XXXX ]`.
    - **Bilingual Content**: Full English and authentic Bangladeshi Bengali localization (`bangla-language` compliance) for date formats, currency symbols (`৳`), digits translation, customer status, and actionable terms & guidelines (`শর্তাবলী ও নির্দেশিকা`).
    - **Multi-Format Export**: Implemented `generateStoreCreditVoucherJpeg(...)`, `generateStoreCreditVoucherPdf(...)`, and `buildStoreCreditVoucherText(...)`.

  - **[V30-2] Interactive Store Credit Voucher Slip Dialog (`StoreCreditVoucherSlipDialog.kt`)**:
    - **Top App Header**: Shows voucher icon, voucher title, code, balance chip, 1-tap clipboard copy button, and modal close button.
    - **Interactive Toolbar Controls**:
      - 58mm / 80mm roll width segmented switch.
      - English (EN) / Bangla (বাংলা) live language toggle.
      - Zoom preview toggle (1.0x / 1.25x).
    - **Reactive Render Flow**: Uses asynchronous background rendering (`Dispatchers.Default`) with loading indicators and auto-scrolling preview container.
    - **Bottom Action Controls**: Ergonomic action bar with large >= 48dp touch targets (`android-ui-ux` compliance):
      - **Print Voucher Slip** (Primary filled button): Direct Bluetooth ESC/POS SPP printing with automatic PrintHelper raster fallback.
      - **Share Voucher Slip** (Outlined action button): Opens sleek modal options sheet.

  - **[V30-3] Versatile Multi-Channel Voucher Sharing (`BillActions.kt`)**:
    - **Modal Sharing Sheet (`AlertDialog`)**: Clean, accessible options sheet with >= 54dp interactive option cards:
      - 🖼️ **Share Slip as Image**: Shares high-resolution JPEG voucher slip via Android Intent Chooser (`FileProvider.getUriForFile`) to Bluetooth, Quick Share, AirDrop, Google Drive, Photos, etc.
      - 💬 **Share on WhatsApp**: Direct WhatsApp intent (`com.whatsapp`) pre-populated with customer phone and formatted voucher details (store name, code, amount, validity, redemption instructions).
      - ✉️ **Share as SMS Text**: Direct messaging intent (`vnd.android-dir/mms-sms`) or system text chooser for instant customer notification.
      - 📋 **Copy Code**: One-tap clipboard copy with confirmation toast.

  - **[V30-4] Sales Thermal Receipt Store Credit & Voucher Balance Integration (`BillPdfGenerator.kt`, `CartScreen.kt`, `SalesHistoryScreen.kt`)**:
    - Extended `BillData` with store credit fields: `storeCreditApplied`, `voucherCode`, `voucherAmount`, `customerRemainingStoreCredit`, and `voucherRemainingBalance`.
    - Updated thermal receipt rendering in both English and Bengali:
      - Renders `Store Credit : ৳...` under payment method breakdown.
      - Renders `Store Credit Used : ৳...` and `Remaining Store Credit : ৳...` for registered customer redemptions.
      - Renders `Voucher Applied : ৳... (SC-XXXX-XXXX)` and `Voucher Remaining : ৳...` for walk-in credit voucher redemptions.

  - **[V30-5] Sales History & Reprint Integration (`SalesHistoryScreen.kt`, `AppDaos.kt`, `DataRepository.kt`)**:
    - Added database queries `getVoucherBySaleId(saleId)` and `getVoucherByReturnId(returnId)`.
    - Added header action button `[ 🎟️ View Voucher Slip ]` in `SaleDetailDialog` whenever an order has an associated issued voucher or refund voucher.
    - Allows instant viewing, thermal reprinting (58mm/80mm), and digital sharing directly from order history.

  - **[V30-6] Deliverables & Verification**:
    - Release APK build: `./gradlew assembleRelease` -> BUILD SUCCESSFUL.
    - Output deliverables strictly saved in project root:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V30.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V11-09-2026.apk`
    - Live emulator verification on `emulator-5554`:
      - Unlocked with PIN `0000`.
      - Verified receipt preview with Store Credit tender breakdown.
      - Verified `[ 🎟️ View Voucher Slip ]` on order `#651`.
      - Verified 80mm and 58mm voucher slip rendering with Code-128 barcode bitmap.
      - Verified English and natural Bangla bilingual slip translations.
      - Verified modal share options sheet and Android system image share chooser.

## V31 - Store Credit Voucher Slip Redesign: Compact Ergonomic Layout, Photorealistic 3D Animated Icons, Accurate 58mm/80mm Formatting & Independent Receipt Language Isolation (V31 / V11-09-2026)

- **Architecture & UX Enhancements**:
  - **[V31-1] Compact, Non-Clipping Dialog Frame (`StoreCreditVoucherSlipDialog.kt`)**:
    - Reduced dialog footprint to `fillMaxWidth(0.92f)` and `fillMaxHeight(0.82f)` (down from `0.94f`), preventing cutoff on smaller screens and mobile POS hardware.
    - Added subtle outer elevation, 18dp rounded corners, and clean divider separation.
  - **[V31-2] Photorealistic 3D Animated Visual Assets**:
    - Integrated high-fidelity 3D assets with micro-animations:
      - `ic_3d_voucher_ticket.png`: Iridescent holographic voucher ticket hero icon with continuous breathing animation (`scale` 1.0f <-> 1.05f).
      - `ic_3d_thermal_printer.png`: Realistic POS thermal receipt printer with glowing LED and paper roll pulse.
      - `ic_3d_share_hub.png`: Futuristic holographic multi-channel share portal icon.
      - `ic_3d_barcode_scanner.png`: Precision handheld laser scanner icon on quick-scan card.
      - `Realistic3DWhatsAppIcon`: Canvas-rendered 3D glossy emerald chat bubble with specular reflection and breathing pulse on both the 1-tap WhatsApp quick button and share options sheet.
      - `Realistic3DMessageReminderIcon`: 3D messaging icon for SMS notification option.
  - **[V31-3] Strict Receipt-Only Language Isolation**:
    - Toggling between English (EN) and Bengali (বাংলা) in the voucher dialog controls **only** the printed thermal receipt slip rendering (`receiptLanguage = if (isBangla) "BN" else "EN"`), leaving the dialog window interface labels (title, buttons, badges) strictly anchored to the active app-wide locale.
  - **[V31-4] Proportional 58mm vs 80mm Print Preview**:
    - 58mm mode renders on a narrow `260.dp` card mimicking 32-column roll proportions.
    - 80mm mode renders on a standard `360.dp` card mimicking 42-column roll proportions.
    - Dynamic roll format indicator pill badge (`58mm ESC/POS (Narrow 32 Col)` vs `80mm ESC/POS (Standard 42 Col)`).
  - **[V31-5] Dual View Modes (Slip vs Quick Scan Card)**:
    - **Slip Mode**: Full thermal receipt view with store branding, barcode, amounts, and bilingual redemption guidelines.
    - **Quick Scan Card Mode**: Deep indigo high-contrast digital card with store name, 28sp Taka balance, and prominent Code-128 barcode ready for cashier hardware scanner guns without scrolling.
  - **[V31-6] Ergonomic Zero-Wrapping Actions Bar**:
    - Shortened button copy with single-line guarantee (`softWrap = false`, `maxLines = 1`).
    - Full $\ge 48\text{dp}$ touch target compliance across all buttons (Close 48dp, Print 48dp, Share 48dp, 1-Tap WhatsApp 48dp).
  - **[V31-7] Deliverables**:
    - Final APK deliverables assembled and verified:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V31.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V11-09-2026.apk`

## V32 - Realistic 3D Animated Icons Suite, Invoice Return Tracking & Owner Invoice Profit Engine (V32 / V12-09-2026)

- **Architecture & Feature Implementations**:
  - **[V32-1] Realistic 3D Animated Icons Suite (`Realistic3DProfitIcons.kt`)**:
    - Created canvas-driven, multi-layer volumetric 3D animated icons using specular highlights, bevel lighting, and harmonic micro-choreographies:
      - `Realistic3DProfitVaultIcon`: Hexagonal gold chassis, deep emerald vault core, continuous specular glint sweep, upward profit chevron, and dual orbital gold coin rings.
      - `Realistic3DReturnOrbIcon`: Deep volumetric ruby orb with radial equator pulse and counter-rotating metallic reversal arrow.
      - `Realistic3DStealthShieldIcon`: Obsidian & sapphire stealth shield with animated brushed-steel security shutters and 3D gold padlock.
      - `Realistic3DHighMarginGemIcon`: Emerald faceted crystal with light refraction (margins $\ge 20\%$).
      - `Realistic3DLowMarginCoreIcon`: Amber energy core with radiating containment rings (margins $0\% - 20\%$).
      - `Realistic3DLossPrismIcon`: Crimson alert prism with interior warning core (negative profit).
      - `Realistic3DGoldTakaCoinIcon`: Thick 3D gold bullion coin with minted Bengali Taka (`৳`) symbol and rim bevel.
      - `Realistic3DTopContributorStarIcon`: Golden starburst with central ruby core highlighting top invoice profit drivers.
  - **[V32-2] Real-Time Invoice Return Intelligence & Audit**:
    - Combined `allReturnsFlow` and `allReturnItemsFlow` into reactive `returnsBySaleId: StateFlow<Map<Long, SaleReturnSummary>>` in `SalesViewModel`.
    - Real-time indicator on `SaleCard` in `SalesHistoryScreen`:
      - Prominent crimson Return Alert Badge (`Realistic3DReturnOrbIcon`, return item count, and refunded amount).
      - Strikethrough original sale total with updated net total after refund deductions.
    - Added quick toolbar filter chip `[ ↩️ Has Returns (N) ]` to instantly isolate invoices with customer returns.
    - Integrated Return & Refund Audit Card in Owner Profit analytics detailing refund total, reversed item count, timestamp, processing cashier, and notes.
  - **[V32-3] Owner Invoice Profit Engine & Analytics Dialog (`OwnerProfitAnalyticsComponents.kt`, `CartScreen.kt`)**:
    - Implemented `calculateInvoiceProfit(saleWithItems, returnSummary)` factoring in historical `purchasePriceAtSale`, promotional discounts, line revenues, returned COGS reversals, and refunds.
    - Segmented Tab Selector in `ReceiptSummaryDialog`:
      - `🧾 Customer Slip`: Full standard customer-facing thermal receipt preview, 58mm/80mm roll width toggle, JPEG/PDF generation, WhatsApp sharing, and printer controls.
      - `💎 Owner Profit`: Executive analysis panel showing 3D Vault banner, Return Audit card, 6 KPI cards (Gross Revenue, Discounts, COGS, Net Profit, Net Margin %, Markup %), Revenue vs Cost distribution bar, and itemized table with ⭐ Top Contributor badge.
  - **[V32-4] Owner Confidentiality & Counter Stealth Guardrails**:
    - Role-based visibility strictly enforces `isOwner == true` for all profit pills, metrics, tabs, and stealth toggles.
    - Cashiers and non-elevated staff never see cost valuations, margins, or owner analytics tabs.
    - Customer thermal print receipts and PDF slips never print cost or profit metrics.
    - Counter Stealth Mode: Integrated top-bar shield toggle with mechanical 3D shutters that masks all profit numbers into `•••• (•••%)` during customer interactions.
    - Added "Highest profit" and "Lowest profit" sort options to the Sales History menu.
  - **[V32-5] Non-Breaking Integration**:
    - Preserved 100% of existing features, buttons, icons, and receipt layouts  -  strictly augmented.
## V33 - Barcode & QR Label Price Tier Switching (Selling, MRP, Wholesale) (V33 / V12-09-2026)

- **Architecture & Feature Implementations**:
  - **[V33-1] Price Tier Data Model & Formatting (`ProductCodeImageUtils.kt`)**:
    - Added `enum class ProductCodePriceTier { SELLING, MRP, WHOLESALE }`.
    - Enhanced `formatProductCodeLabelPrice(formattedPrice: String)` to preserve existing price prefixes:
      - Selling Price: `Price: <Amount>` (e.g., `Price: ৳70`)
      - MRP Price: `MRP Price: <Amount>` (e.g., `MRP Price: ৳85`)
      - Wholesale Price: `Wholesale Price: <Amount>` (e.g., `Wholesale Price: ৳62`)
  - **[V33-2] Single Product Label Dialog (`ProductCodeImageDialog` in `ProductsScreen.kt`)**:
    - Integrated persistent `priceTier` preference in `SharedPreferences("product_code_image_settings")`.
    - Real-time responsive `SingleChoiceSegmentedButtonRow` (Selling Price, MRP Price, Wholesale Price) with active price indicator and `Not set` (`নির্ধারিত নয়`) badge when 0.
    - Full reactivity across canvas bitmap rendering, image saving, PDF export, sharing, and thermal printing.
    - Zero breaking changes to existing features, text scales, or 3D icons.
  - **[V33-3] Batch Barcode Printing Integration (`BatchProductCodePrintDialog` in `ProductsScreen.kt`)**:
    - Embedded 3-way `SingleChoiceSegmentedButtonRow` under the `Show Price` switch.
    - Batch label mapper formats each label item according to selected price tier.
  - **[V33-4] Bilingual Localization (`strings.xml` & `values-bn/strings.xml`)**:
    - Added localized keys `products_price_tier_label`, `products_price_tier_selling`, `products_price_tier_mrp`, `products_price_tier_wholesale`, `products_price_tier_not_set`.
  - **[V33-5] Deliverables**:
    - Saved in project root:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V33.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V12-09-2026.apk`

## V34 - Custom Price Tier Switcher Card UI (Exact Match to Interactive Preview) (V34 / V12-09-2026)

- **UI/UX Overhaul & Pixel-Perfect Alignment**:
  - **[V34-1] Custom `PriceTierSwitcherCard` Implementation**:
    - Replaced the generic Material 3 `SingleChoiceSegmentedButtonRow` in both `ProductCodeImageDialog` and `BatchProductCodePrintDialog` with the custom designed card specified in `code_image_price_switch_preview.html`:
      - **Container**: Card surface with 16dp rounded corners, 2dp purple accent border (`#C084FC`), subtle 4dp shadow elevation, and 14dp internal padding.
      - **Header**:
        - Left: 🏷️ Price Tag icon + "Price Tier on Label" in bold purple (`#581C87`), paired with a vibrant `NEW FEATURE` gradient badge pill (linear gradient `#A855F7` to `#6366F1`, 8.5sp bold white text).
        - Right: Inline `Show Price` toggle checkbox styled in teal (`#00897B`) with no text wrapping.
      - **3-Way Button Grid**:
        - 3 equal-width cards spaced by 6dp for **Selling Price**, **MRP Price**, and **Wholesale Price**.
        - Dual-line layout inside each button: top category tag (`tier-tag`, 10sp semi-bold) and bottom monetary value (`tier-val`, 14sp bold).
        - Distinct active color palettes:
          - **Selling**: Light blue surface (`#E0F2FE`), sky blue border (`#0284C7`), dark sky blue text (`#0284C7`).
          - **MRP**: Soft amber surface (`#FEF3C7`), amber border (`#D97706`), rich amber text (`#D97706`).
          - **Wholesale**: Lavender purple surface (`#F3E8FF`), purple border (`#7C3AED`), deep purple text (`#7C3AED`).
          - **Inactive**: Crisp off-white surface (`#F8FAFC`), subtle slate border (`#E2E8F0`), muted tag (`#64748B`), dark value (`#0F172A`).
      - **Disabled & Alpha States**:
        - When `Show Price` is unchecked, the 3-button grid is dynamically disabled and dimmed to 0.4f alpha, while the price label line is completely removed from the sticker preview and generated output.
  - **[V34-2] Sticker Price Fallback & Reorganization**:
    - Isolated "Show Code Number" into its own clean separate card directly below the price switcher card.
    - Updated `formattedPriceString` with graceful fallback logic: if MRP or Wholesale price is 0 / not set, the sticker preview automatically falls back to the product's selling price instead of rendering `৳0`.
  - **[V34-3] Deliverables**:
    - Saved strictly in project root:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V34.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V12-09-2026.apk`

## V35 - Removed "NEW FEATURE" Badge, Added Dual Display & Micro-Customizations (V35 / V12-09-2026)

- **User Directives & Visual Perfection**:
  - **[V35-1] Removed "NEW FEATURE" Badge**:
    - Completely removed the `NEW FEATURE` gradient badge pill from the `🏷️ Price Tier on Label` header row in `PriceTierSwitcherCard`, restoring a clean, minimal header alongside the `[✓] Show Price` toggle.
  - **[V35-2] Uppercase Category Tags**:
    - Converted tier tags to uppercase (`SELLING PRICE`, `MRP PRICE`, `WHOLESALE`) matching `code_image_price_switch_preview.html`.
  - **[V35-3] Added Dual Display (MRP + Offer) Button**:
    - Added dedicated full-width supermarket bargain card directly beneath the 3 tier buttons.
    - Features:
      - Inactive state: light green surface (`#F0FDF4`) with custom dashed border (`#86EFAC`).
      - Active state: mint green surface (`#D1FAE5`) with solid emerald border (`#10B981`) and elevation.
      - Left column: Sparkle icon (`✨`), title `Dual Display (MRP + Offer)`, subtitle `Supermarket style bargain label`.
      - Right badge: Pill showing `MRP <MRP_PRICE>` (strikethrough text decoration) and `<SELLING_PRICE>` (bold green).
      - Canvas rendering: outputs `MRP: ৳90 | Offer: ৳70` (or `এমআরপি: ৳৯০ | অফার: ৳৭০`), with automatic barcode canvas wrapping and scaling.
  - **[V35-4] Micro-Customizations Row**:
    - Separated by dotted / dashed divider line (`Canvas` with `dashPathEffect(4.dp, 4.dp)`).
    - Added two toggle chips:
      - `Show Unit (/Pcs)` (ON/OFF): Appends `/Pcs` or Bengali `/পিস` to the offer price.
      - `Bangla Digits (৳৭০)` (ON/OFF): Converts numeric digits to Bengali numerals (`০-৯`) across both UI buttons and barcode canvas sticker.
    - User selections persist across sessions in `SharedPreferences`.
  - **[V35-5] Batch Barcode Print Dialog Parity**:
    - Wired `showUnit` and `showBanglaDigits` into `BatchProductCodePrintDialog` and batch PDF label generator.
  - **[V35-6] Deliverables**:
    - Saved strictly in project root:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V35.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V12-09-2026.apk`

## V39 - Complete Tablet Architecture Overhaul & Master-Detail UI/UX (V39 / V13-09-2026)

- **Architecture & Tablet Mode Overhaul**:
  - **[V39-1] Navigation Rail Refinement & More Hub Organization (`Navigation.kt`)**:
    - **Promoted Core Domains**: Promoted direct `Expenses` destination to the left `NavigationRail`. Cash Register (`RegisterClose`) is housed cleanly in the `More Operations Hub` dialog, maintaining sleek vertical spacing and eliminating text truncation on the rail.
    - **Backstack Integrity**: Marked `Cart`, `Expenses`, and `RegisterClose` as core tablet destinations (`isCoreScreen`) to prevent unexpected backstack popping when toggling between primary views.
    - **Deduplicated More Hub**: Removed redundant `Report` and `Settings` entries from the `MoreHubBottomSheetContent` on tablet screens since both are already directly accessible from the primary Navigation Rail and top controls.
  - **[V39-2] Products Screen Tablet Grid & Valuation Card (`ProductsScreen.kt`)**:
    - **Clean Header**: Suppressed the redundant top-bar back arrow on tablet form factors (`isTablet`), aligning with top-level navigation rail standards.
    - **Full-Width Valuation**: Expanded the Inventory Valuation summary card to utilize the full available width of the screen.
    - **Adaptive 2-Column Product List**: Converted the stretched single-column list view on tablet into an adaptive responsive grid (`GridCells.Adaptive(minSize = 420.dp)`), ensuring balanced information density and touch target ergonomics.
  - **[V39-3] Dues & Customer Khata Tablet Overhaul (`DuesScreen.kt`)**:
    - **Contextual Back Navigation**: Suppressed root back navigation on tablet while preserving the back arrow when drilling into an individual customer ledger (`selectedCustomer != null`).
    - **Full-Width Recovery Banner**: Extended the Credit Recovery Summary card to span the entire screen width.
    - **Adaptive 2-Column Debtor Cards**: Refactored the debtors list to use an adaptive 2-column grid (`GridCells.Adaptive(minSize = 440.dp)`), displaying customer contact info, due balance, and quick action chips (Collect, WhatsApp, Remind, Call) in balanced dual columns.
  - **[V39-4] Sales Report Centered Max-Width Container (`SalesReportScreen.kt`)**:
    - **No Redundant Back Button**: Hidden the root top-bar back button on tablet mode.
    - **Centered 960dp Container**: Wrapped the report content within `Box(contentAlignment = Alignment.TopCenter)` and `widthIn(max = 960.dp)`, preventing awkward horizontal stretching of KPI cards, trend charts, and payment mix breakdowns on wide 2560x1600 tablet screens.
  - **[V39-5] Settings Screen Centered Container (`SettingsScreen.kt`)**:
    - **Clean Root Screen**: Suppressed the top-bar back arrow on tablet when on `SettingsPage.HOME`.
    - **Centered 760dp Container**: Applied `widthIn(max = 760.dp)` to prevent settings tiles from spreading excessively wide across landscape displays.
  - **[V39-6] Sales History 42/58 Master-Detail Split Screen (`SalesHistoryScreen.kt`)**:
    - **Split-Pane Architecture**:
      - **Master Column (42% width)**: Date-grouped transactions list with search and filter controls. The actively selected invoice is highlighted with subtle border tint and elevated card styling (`isSelected`).
      - **Detail Column (58% width)**: Embedded `TabletSaleDetailPane` featuring:
        - **Live 80mm Thermal Receipt Canvas**: Rendered directly inline with store header, itemized receipt rows, taxes, discount, payment method, and QR code verification.
        - **Executive Owner Profit Audit Card**: If logged in as Owner/Manager, renders live Net Revenue, COGS (`netCogs`), Net Margin %, and markup analytics.
        - **Direct Action Toolbar**: Print Receipt, Share Receipt (WhatsApp/System), and Sale Return buttons directly in the detail header.
      - **Graceful Fallback**: Preserves the standard mobile dialog overlay when running on compact phone form factors (< 600dp).
  - **[V39-7] Deliverables**:
    - Production APK built and verified:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V39.apk` (69MB, versionCode 220916, versionName 2.6.41)
    - Installed and tested live on Android landscape tablet emulator `emulator-5554` (2560x1600 physical resolution).

## V40 - 3-Column Product Grid Default & Streamlined Single-Row List View (V40 / V13-09-2026)

- **Products Screen Architecture & UI/UX Redesign (`ProductsScreen.kt`)**:
  - **[V40-1] Grid View Default on Tablet Mode**:
    - Set default `viewModeName` on tablet (`isTablet == true`) strictly to `ProductViewMode.GRID.name` (`if (isTablet) ProductViewMode.GRID.name else ProductViewMode.LIST.name`).
    - Tablet operators now land directly on visual product cards upon entering the Products screen.
  - **[V40-2] 3-Column Product Grid (`GridCells.Fixed(3)`)**:
    - Replaced the dense 7-column / adaptive layout on tablet with an ergonomic, balanced 3-column card grid (`columns = if (isTablet) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 160.dp)`).
    - Ample card breathing room, large touch targets (>= 48dp), clear stock status pills (`Low: N` in amber, `N Pcs` in emerald), barcode chips (`#8466...`), bold retail selling price (`৳550 /Pcs`), and full title legibility.
  - **[V40-3] Streamlined Single-Row List View with Exact 100% Data Parity**:
    - Completely replaced the cluttered ERP spreadsheet columns with a clean, modern single-row horizontal stream.
    - **Exact Data Parity**: Displays identical data points as Grid View cards (Product Title, Barcode badge, Category label, Stock status badge, Selling Price) and identical 4-button action cluster:
      - `[🛒 Cart]`: Quick add to active cart.
      - `[📦 ± Stock]`: Quick inventory adjust modal.
      - `[🏷️ Code]`: Realistic 3D Hologram Laser Code dialog.
      - `[⋮ More]`: Edit, Delete, and extended options menu.
    - Eliminated all micro spreadsheet cells, dense margins, and cramped ERP data tables.
  - **[V40-4] Collapsible Inventory Valuation Banner on Tablet**:
    - Removed hardcoded forced expansion (`isTablet || showInventoryValuation`) on tablet mode, allowing the 200dp valuation card to collapse into a slim, space-saving banner via the `Details ▾ / Collapse ▴` button.
    - Instant reclaim of screen height for inventory browsing on both tablet and phone screens.
  - **[V40-5] 4-Button Feature Parity on Grid Cards**:
    - Added the `Realistic3DHologramLaserCodeIcon` (Laser code button) to `ProductGridCard` on tablet, providing identical 4-button quick action capability across both Grid View and List View.
  - **[V40-6] Deliverables & Production Verification**:
    - Production APK built via `./gradlew assembleRelease`:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V40.apk` (36MB, R8 minified, versionCode 220917, versionName "2.6.42-V13-09-2026")
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk`
    - Live verified and tested on tablet emulator `emulator-5554` (2560x1600 resolution) in both Light and Dark themes.

## V41 - Quick Category & Status Filter Strip and Redesigned 3-Row Product Grid Cards (V41 / V13-09-2026)

- **Products Screen Architecture & Visual Parity with Interactive HTML Preview (`ProductsScreen.kt`)**:
  - **[V41-1] Quick Category & Status Filter Chips Strip below Search Bar**:
    - Rendered interactive horizontal scroll strip directly beneath the search input:
      - Status Chips: `All SKUs (621)`, `⚠️ Low Stock (177)`, `🚫 Out of Stock (67)`, `💎 High Margin (>30%)`.
      - Visual Divider: `|` separator.
      - Category Chips: `All Categories`, followed by dynamic category chips with curated retail emojis (`🥫 সস (Sauces)`, `🧼 বিউটি সোপ (Soaps)`, `🌸 Air Freshener`, `🧴 Face Wash`, `🍞 Bakery`, `🌻 Edible Oil`, `☕ Coffee`, etc.).
      - Instant filtering with animated counter tags and selection states.
  - **[V41-2] Exact 3-Row ProductGridCard Layout Parity**:
    - Completely redesigned `ProductGridCard` to match `tablet_mode_audit_redesign_preview.html` (.card-grid-item):
      - **Row 1 (Header)**: Bold 14sp Product Name (max 2 lines with Bengali text support) on Left + Stock Badge Pill (`Low: 1` amber, `7 Pcs` green, `Out of Stock` red; clickable to adjust stock) on Right.
      - **Row 2 (Meta)**: Monospace Barcode Capsule `[❙❙❙] #barcode` + Bold Blue Category Text (`Color(0xFF2563EB)` / `Color(0xFF60A5FA)`).
      - **Row 3 (Footer)**: Bold Emerald Price Display (`৳550 /Pcs`) on Left + Action Cluster on Right:
        - `[🛒 Cart]`: Emerald button with shopping cart icon and text.
        - `[📦 ±]`: Stock inward/adjustment shortcut.
        - `[❙❙❙]`: Barcode print/label action.
        - `[⋮]`: Product overflow menu (Edit, Delete, Details).
  - **[V41-3] Tablet Density & Grid Adaptation**:
    - Added density toggle: `Compact` (tight padding, 12.5sp font) vs `Comfortable` (14sp font, relaxed touch targets).
    - Converted tablet grid columns to `GridCells.Adaptive(minSize = 300.dp)` to prevent button cramming on diverse tablet DPIs.
    - Added top toolbar `+ Add Product` button next to view mode switchers.
  - **[V41-4] Zero-Duplication Navigation Architecture (`Navigation.kt`)**:
    - Filtered out `Expenses`, `SalesReport`, and `Settings` from `MoreOperationsHubSheet` when `isTablet` is true, ensuring secondary dialog does not duplicate destinations already pinned to the tablet Navigation Rail.

## V42 - Tablet Unobstructed Product Grid & Clean Widescreen Ergonomics (V42 / V13-09-2026)

- **Ergonomics & Tablet Polish**:
  - **[V42-1] Suppressed Redundant Floating Action Button on Tablet (`ProductsScreen.kt`)**:
    - Wrapped `floatingActionButton` in `if (!isTablet)` to remove the bottom-right green FAB on tablet form factors.
    - Eliminates card obstruction on the bottom-right column, keeping all product cards, prices, and action clusters 100% visible and interactive.
    - Primary creation entry point cleanly anchored in the top toolbar as `+ Add Product`.
  - **[V42-2] Multi-Section Tablet Parity Verification**:
    - **Sales History**: Master-Detail 42/58 split view with live 80mm thermal receipt, itemized breakdown, and confidential Net Profit Audit card verified on emulator (`v41_history_actual.png`).
    - **Dues & Khata**: 2-Column responsive debtor cards with WhatsApp, Collect, Call, Ledger buttons, aging breakdown, and Credit Recovery Summary banner verified on emulator (`v41_history_live.png`).
    - **Products Screen**: Quick filter strip and 3-row grid cards verified on emulator (`v42_products_actual.png`).
  - **[V42-3] Deliverables**:
    - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V42.apk` (36MB, versionCode 220919, versionName "2.6.44-V13-09-2026")
    - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk`

## V43 - Product Grid View 100% Icon, Animation, and Text Parity with List View (V43 / V13-09-2026)

- **Products Screen UI/UX & Visual Parity (`ProductsScreen.kt`)**:
  - **[V43-1] Exact 3D Animated Stock Badge Alignment**:
    - Replaced the static dot (`●`) with identical 3D animated canvas orbs and hazard icons from `ProductItemRow`:
      - **Low Stock**: `Realistic3DAmberHazardDiamond` with radiant amber diamond geometry and warm hazard pulse.
      - **Out of Stock**: `Realistic3DRubyPulsingAlertOrb` with animated ruby pulse ring.
      - **In Stock**: `Realistic3DGlowingEmeraldSphere` with glossy sphere highlight and subtle breathing glow.
    - Standardized badge surfaces with identical soft container colors (`0xFFFEF3C7` for low stock, `0xFFFEE2E2` for out of stock, `0xFFDCFCE7` for normal) and localized text strings (`Low: %s`, `Out of Stock`, etc.).
  - **[V43-2] Hologram Laser Barcode Capsule & Indigo Category Tag**:
    - Replaced font glyph `"❙❙❙"` with `Realistic3DHologramLaserCodeIcon(size = 11.dp)` featuring real-time red laser beam sweep animation.
    - Formatted barcode text in monospace font with `#` prefix (`#846656005286`).
    - Aligned category label color to soft indigo (`Color(0xFF4F46E5)` in Light Mode, `Color(0xFF818CF8)` in Dark Mode), matching List View exactly.
  - **[V43-3] 4-Button Action Dock 100% Icon, Animation & Typography Parity**:
    - **Cart Button**: Replaced flat emoji `🛒` with animated `Realistic3DSupermarketCartIcon(size = 14.dp)` on emerald `Button` (`MaterialTheme.colorScheme.primary`) + `Text(stringResource(R.string.cart))` with 11.sp semi-bold typography.
    - **Stock Adjust Button**: Replaced flat emoji `📦 ±` with soft purple/indigo container (`Color(0xFFEEF2FF)`, border `Color(0xFFC7D2FE)`) holding `Realistic3DKraftStockBoxIcon(size = 14.dp)` + `± Stock` / `± স্টক` text (`stringResource(R.string.products_adjust_stock_pm)`) in `Color(0xFF4F46E5)`.
    - **Barcode Print Button**: Replaced flat blue square `■` with rounded square holding animated `Realistic3DHologramLaserCodeIcon(size = 15.dp)`.
    - **Overflow Menu**: Matched touch target to uniform 32.dp (`ProductOverflowMenu`).
  - **[V43-4] Price & Metric Formatting**:
    - Aligned bold emerald price (`৳550` in `Color(0xFF047857)`) paired with muted unit indicator (`/Pcs`) in 11sp typography.
  - **[V43-5] Deliverables & Production Verification**:
    - Compiled release APK via `./gradlew assembleRelease`:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V43.apk` (37.3MB, versionCode 220920, versionName "2.6.45-V13-09-2026")
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk`
    - Installed on Android tablet emulator `emulator-5554` and verified in real-time (`tablet_products_screen_now.png`).

## V44 - Removed Compact/Comfortable Density Toggle & Cleaned Products Toolbar (V44 / V13-09-2026)

- **Products Screen Clean Architecture (`ProductsScreen.kt`)**:
  - **[V44-1] Removed Compact/Comfortable Toggle & State**:
    - Removed `productDensity` variable and state persistence.
    - Completely removed the `[ Compact | Comfortable ]` segmented Surface component from the Products screen top action toolbar.
    - Simplified the toolbar to: Search Input + Barcode Scanner Trigger, Filter Options Button, View Mode Switcher (`[ ⊞ Grid | ☰ List ]`), and `+ Add Product` CTA button.
  - **[V44-2] Standardized Ergonomic Dimensions & Padding**:
    - Removed `isCompact` parameter and all conditional branches from `ProductItemRow` and `ProductGridCard`.
    - Standardized `ProductItemRow` padding to comfortable retail standard: `horizontal = 14.dp, vertical = 9.dp`.
    - Standardized `ProductGridCard` padding and typography:
      - `horizontal = 14.dp, vertical = 12.dp`
      - `verticalArrangement = Arrangement.spacedBy(8.dp)`
      - Product thumbnail height fixed to `80.dp`.
      - Product title typography fixed to `14.sp`, line height `19.sp`.
  - **[V44-3] Interactive HTML Preview Alignment (`tablet_mode_audit_redesign_preview.html`)**:
    - Removed `.density-toggle-group` and compact/comfortable buttons from the interactive preview toolbar.
    - Removed `setProductDensity()` JavaScript controller and `currentDensity` state variable.
  - **[V44-4] Deliverables & Production Verification**:
    - Compiled release APK via `./gradlew assembleRelease`:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V44.apk` (36MB, versionCode 220921, versionName "2.6.46-V13-09-2026")
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk`
    - Installed on Android tablet emulator `emulator-5554` and verified live in both Grid Mode (`tablet_products_grid_final.png`) and List Mode (`tablet_products_list_no_compact.png`).

## V45 - Product Card Long-Press Batch Operations Activation (V45 / V13-09-2026)

- **Products Screen Gesture Architecture (`ProductsScreen.kt`)**:
  - **[V45-1] Card Long-Press Activation of Batch Mode**:
    - Enhanced `ProductGridCard` and `ProductItemRow` with Compose Foundation `Modifier.combinedClickable(onClick = ..., onLongClick = ...)`.
    - Long-pressing on any product card in Grid View or row in List View triggers:
      1. Immediate haptic tactile vibration via `haptic.performHapticFeedback(HapticFeedbackType.LongPress)`.
      2. Activation of batch operations selection mode (`batchSelectMode = true`).
      3. Immediate selection of the long-pressed product (`selectedBarcodes.add(product.barcode)`), instantly displaying the checked checkbox and purple card border/tint.
      4. Automatic slide-in of the floating batch action bar (`AnimatedVisibility(visible = batchSelectMode)`), presenting bulk actions (Select All, Price %, Category, Delete, Exit).
    - If batch selection mode is already active, long-pressing (or clicking) toggles that product's selection state.
  - **[V45-2] Isolated Interactive Touch Targets**:
    - Card action cluster buttons (`[🛒 Cart]`, `[📦 ± Stock]`, barcode label generator, overflow menu, and thumbnail image) continue to handle their own tap interactions cleanly without triggering card click or long-press.
  - **[V45-3] Interactive HTML Preview Parity (`tablet_mode_audit_redesign_preview.html`)**:
    - Added desktop context menu (`contextmenu`) and touch/mouse timer (450ms) long-press listeners on `.card-grid-item`.
    - Added `.card-grid-item.selected-card` state with primary outline, subtle violet tint, and prominent top-right checkmark badge (`✓`).
    - Connected long-press directly to `#floatingBatchBar` active state and batch count counter.
  - **[V45-4] Deliverables & Production Verification**:
    - Compiled release APK via `./gradlew assembleRelease`:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V45.apk` (versionCode 220922, versionName "2.6.47-V13-09-2026")
## V46 - Tablet Mode Products Screen Header Alignment with Mobile Mode (V46 / V13-09-2026)

- **Products Screen Header & Ribbon Architecture (`ProductsScreen.kt`)**:
  - **[V46-1] Aligned TopAppBar Header**:
    - Eliminated raw, cramped, unlabeled icon action buttons (`Icons.Default.Description`, `Icons.Default.Checklist`, `Realistic3DClipboardDuesIcon`, `Icons.Default.Print`) that were previously forced into the tablet `TopAppBar` `actions` cluster via an `if (isTablet)` branch.
    - Standardized `TopAppBar` across tablet and mobile modes: now cleanly presents only `PosTopAppBarTitle` with product count badge on the left, and `[🔔]` Alerts + `[🛒 Cart]` button on the right.
  - **[V46-2] Cross-Platform Horizontal Quick-Tool Ribbon**:
    - Removed `if (!isTablet)` conditional gate that previously hid the Quick-Tool Ribbon on tablets.
    - Placed dedicated Quick-Tool Ribbon immediately below the TopAppBar on both tablet and mobile viewports with responsive padding (`horizontal = if (isTablet) 14.dp else 12.dp`).
    - Labeled, tactile chips provide explicit visual clarity and ergonomic >=48dp tap targets:
      - `[ 📄 Excel Tools ▾ ]` with dropdown menu (Export Inventory, Import Stock, Export Barcodes)
      - `[ ⚏ Batch Select ]` / `[ ✕ Exit Batch ]` for bulk operations
      - `[ 📋 Stock Audit ]` for physical stock count reconciliation
      - `[ 🖨 Print Labels ]` for barcode label sheet printing
  - **[V46-3] Interactive Preview Parity (`interactive_preview/tablet_mode_audit_redesign_preview.html`)**:
    - Added matching `.quick-tools-ribbon` with high-touch tactile pill chips above the inventory valuation banner.
    - Added interactive `toggleBatchMode()` handler synchronizing the ribbon chip state with the floating batch operations dock.
  - **[V46-4] Deliverables & Production Verification**:
    - Built via `./gradlew assembleRelease`:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V46.apk` (36MB, versionCode 220923, versionName "2.6.48-V13-09-2026")
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk`
    - Deployed and verified on Android tablet emulator `emulator-5554` (2560x1600 widescreen counter POS display), verifying 100% visual and functional parity.

## V47 - Reverted Tablet Split Changes to Sales History Screen (V47 / V13-09-2026)

- **Sales History Screen Architecture (`SalesHistoryScreen.kt`)**:
  - **[V47-1] Restored Unified Full-Width Sales History Layout**:
    - Completely reverted the 42/58 master-detail split screen architecture introduced in V39, restoring `SalesHistoryScreen.kt` to its unified, proven full-width layout.
    - Standardized across tablet and phone:
      - Sticky search bar and period filters (`All Time`, `Today`, `This Week`, `This Month`, `Custom`).
      - Summary KPI metric cards (`Total Sales`, `Cash`, `Digital`, `Due`).
      - Chronological date-grouped transactions list with full-width `SaleCard` components.
      - Tapping any invoice opens the standard `SaleDetailDialog` modal popup rather than updating a split pane.
  - **[V47-2] Interactive Preview Parity (`interactive_preview/tablet_mode_audit_redesign_preview.html`)**:
    - Removed `.master-detail-container` in `#viewHistory`.
    - Added unified full-width sales history view with sticky search, period filter chips, 4 KPI cards, and date-grouped transaction cards.
    - Added dedicated `#saleDetailModal` dialog triggered by `openSaleDetailModal(id)`, matching the mobile dialog experience.
  - **[V47-3] Deliverables & Verification**:
    - Built release APK:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V47.apk` (36MB, versionCode 220924, versionName "2.6.49-V13-09-2026")
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk`
    - Verified on Android tablet emulator `emulator-5554` (2560×1600 counter POS display).

## V48 - Tablet Sell Screen Dual-Orientation Parity & Multi-Line Cart Redesign (V48 / V13-09-2026)

- **Tablet Sell Screen Architecture (`ScannerScreen.kt` & `Navigation.kt`)**:
  - **[V48-1] Dual-Orientation Parity (`TabletSplitTerminal`)**:
    - Full landscape (58% Catalog Left, 42% Cart Review Right) and portrait (54% Catalog Top, 46% Cart Review Bottom) split terminal using `BoxWithConstraints` for guaranteed dp-based aspect-ratio responsiveness (`maxWidth < maxHeight`).
    - Reactive `LocaleWrapper` with dynamic `LocalConfiguration` propagation ensuring zero configuration lock-in or stale geometry when the tablet is rotated.
    - Multi-line Product Name Wrapping: `maxLines = 3`, `lineHeight = 17.sp`, zero cutoff or ellipsis truncation for long English and Bengali compound conjunct names.
    - Dedicated Customer Header Bar (`TabletCustomerBar`) with customer avatar, name, phone, quick search dialog, and hold/clear buttons.
    - Complete Quick Action Toolbar in both views: `Scanner` (Camera PIP toggle), `Keypad` (manual barcode/item dialog), `Stock Check` (inventory enquiry toggle with high-visibility banner), and `Held Carts` counter chip.
    - Persistent sticky footer (`TabletCartFooter`) with live subtotal, grand total, and full-width checkout CTA navigating directly to checkout.
  - **[V48-2] Android Tablet Emulator Live Verification (`emulator-5554`)**:
    - Verified on `emulator-5554` (2560x1600 widescreen tablet, 320dpi):
      - Cold-start login via PIN `1234`.
      - Landscape mode verified: 58/42 side-by-side terminal, 4-column product grid, stock check mode toggle banner, item stepper counter, and instant total recomputation.
      - Portrait mode verified: 54/46 top/bottom vertical split terminal, 5-column product grid, customer pill bar, and full-width checkout CTA.
      - Live product addition tested on "Angelic Air Freshener 300 ml"  -  cart badge incremented from 7 to 8 items, total updated from ৳975 to ৳1,305.
      - Checkout navigation verified: tapping "Proceed to Cart (৳1,305) ->" seamlessly loads `CartScreen`, and tapping Sell on the `NavigationRail` smoothly returns to `TabletSplitTerminal`.
  - **[V48-3] Deliverables & Production Verification**:
    - Built via `./gradlew assembleRelease`:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V48.apk` (37MB, versionCode 220925, versionName "2.6.50-V13-09-2026")
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk`

## V49 - Radiant Dark POS Terminal Aesthetics Refresh (V49 / V13-09-2026)

- **Aesthetic Parity with HTML Interactive Preview (`ScannerScreen.kt` & `Navigation.kt`)**:
  - **[V49-1] Root Cause Elimination**:
    - Previously, `TabletSplitTerminal` dynamically inherited `MaterialTheme.colorScheme.background` and `surface`, which caused Android devices set to system Light theme to render as washed-out off-white / light grey with low-contrast buttons, appearing dull compared to the HTML preview (`data-theme="dark"`).
    - Decoupled `TabletSplitTerminal` and tablet `NavigationRail` from ambient system theme by introducing explicit `TabletTerminalColors` tokens:
      - Deep obsidian background: `#080B11`
      - Catalog pane surface: `#161D2D`
      - Cart review pane surface: `#0F1523`
      - Card surfaces: `#1E273D` with subtle glass borders (`Color(0x1AFFFFFF)`)
      - Search input container: `#121824`
      - Sticky footer container: `#0A0E18`
      - Navigation Rail container: `#0E131F` with divider `Color(0x1FFFFFFF)`
  - **[V49-2] Radiant Emerald Accents & 3D Icons**:
    - Neon Emerald `#10B981` applied across search icon, category pill active state & dot, stock badges, in-cart quantity badges, cart header squircle icon, price equation subtotals, and grand total typography (`22.sp`, `FontWeight.Black`).
    - Checkout CTA button upgraded to a radiant horizontal gradient (`Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF047857)))`) with `Realistic3DSupermarketCartIcon` and bold high-contrast text.
    - Product cards upgraded with a 36dp 3D squircle containing `Realistic3DProductsBoxIcon`, glowing stock badges (`In Stock` emerald pill, `Low Stock` amber pill, `Out of Stock` rose pill), and crisp `#F8FAFC` titles with zero cutoff.
    - Tablet `NavigationRail` upgraded with deep dark container (`#0E131F`), subtle divider (`0x1FFFFFFF`), and neon emerald `#10B981` active selection indicator.
  - **[V49-3] Dual-Orientation Emulator Live Verification (`emulator-5554`)**:
    - Tested live on `emulator-5554` (2560×1600 widescreen tablet):
      - Landscape mode (58/42 split): radiant dark terminal styling, high-contrast typography, glowing stock badges, emerald prices, and gradient checkout CTA.
      - Portrait mode (54/46 split): 5-column product grid, customer quick pill with purple gradient avatar, and sticky bottom checkout CTA.
  - **[V49-4] Deliverables & Production Verification**:
    - Built via `./gradlew assembleRelease`:
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V49.apk` (37MB)
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk` (37MB)
  - **[V50-1] Stock Check Localization & Language Parity**:
    - Fixed hardcoded Bengali banner text leak (`"স্টক অনুসন্ধান মোড সক্রিয়  -  যেকোনো পণ্যের স্টক ও মূল্য দেখতে ট্যাপ করুন"`) across tablet Portrait and Landscape top toolbars.
    - Introduced localized string resource `R.string.tablet_stock_check_banner`:
      - English: `"Stock Check Mode Active  -  Tap any product to view & adjust stock"`
      - Bangla: `"স্টক অনুসন্ধান মোড সক্রিয়  -  স্টক ও তথ্য দেখতে যেকোনো পণ্যে ট্যাপ করুন"`
  - **[V50-2] Full Stock Check Phone-Mode Behavioral Parity on Tablet**:
    - Enabled opening `StockCheckBottomSheet` on tablet product card click whenever Stock Check mode is active (`!isSellMode`).
    - Added `selectProductForStockCheck(product: Product)` to `ScannerViewModel` in `POSViewModels.kt`.
    - Updated `TabletProductGridCard` in `ScannerScreen.kt`:
      - Product cards remain interactive even if out-of-stock when in Stock Check mode (`enabled = if (isStockCheckMode) true else (!isOutOfStock || allowNegativeInventory)`).
      - Card borders render in high-visibility amber (`TabletTerminalColors.AmberBorder`) when Stock Check mode is active.
      - Card `onClick` triggers haptic feedback and selects product for stock inspection sheet.
    - Sheet displays full financial intelligence:
      - Inventory level & status badge (`In Stock`, `Low Stock`, `Out of Stock`)
      - Purchase price, Selling price, MRP, Wholesale price
      - Unit Margin % & Taka gain, and Total Asset Worth
      - Stepper adjustments (-5, -1, +1, +5, 0, Reset) and direct numeric keypad entry
      - DB persistent `Save` button (with inventory permission check & snackbar) and `Edit` navigation button
  - **[V50-3] HTML Interactive Preview Parity**:
    - Updated `interactive_preview/tablet_sell_screen_redesign_preview.html`:
      - Added Stock Check amber banners across Landscape, Portrait Split, and Portrait Docked modes with dynamic `data-i18n="stock_check_banner"`.
      - Added CSS for active Stock Check button (`.stock-mode-btn.active`) with amber glow and card amber borders (`.product-card.stock-check-active`).
      - Added `stockCheckModal` dialog reproducing the exact phone & tablet `StockCheckBottomSheet` UX (financials, margins, asset worth, steppers, Save/Edit).
      - Verified with browser subagent across both English and Bangla locales.
  - **[V50-4] Production Deliverables & Live Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220926, versionName "2.6.50-V13-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V50.apk` (37MB)
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk` (37MB)
    - Tested and verified live on `emulator-5554` (2560×1600 widescreen tablet):
      - English banner displayed correctly without Bengali leakage.
      - Product cards highlighted with amber borders.
      - Tapping product card opened the comprehensive `StockCheckBottomSheet` with financial intelligence, steppers, and Save button.

## V51 - Tablet POS Dynamic Light/Dark Theme Adaptation & Live Verification (V51 / V13-09-2026)

- **Dynamic Theme Adaptation for Tablet POS (`ScannerScreen.kt`, `Navigation.kt`, & HTML Preview)**:
  - **[V51-1] Root Cause Elimination**:
    - Previously in V49/V50, `NavigationRail` and `TabletTerminalColors` hardcoded dark color tokens (`#0E131F`, `#161D2D`, `#0F1523`, etc.), which prevented the sell screen, catalog grid, cart review pane, and side navigation rail from responding when the user selected Light theme in Settings.
    - Upgraded `NavigationRail` (`Navigation.kt`) to dynamically evaluate `isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f`:
      - Container: `#FFFFFF` in light mode / `#0E131F` in dark mode.
      - Vertical Divider: `Color(0x1F000000)` in light mode / `Color(0x1FFFFFFF)` in dark mode.
      - Header Logo: light border & gradient in light mode / dark glass in dark mode.
      - Navigation Items: unselected icon and label use high-contrast dark slate (`#475569`) in light mode / `#94A3B8` in dark mode.
      - Cashier Shift Avatar & 1-tap Lock button: light borders, clean shadows, and crisp text contrast.
  - **[V51-2] Tablet Terminal Colors Architecture (`TabletTerminalColors`)**:
    - Converted all color tokens in `TabletTerminalColors` (`ScannerScreen.kt`) to `@Composable get()` accessors dynamically evaluating `surface.luminance() < 0.5f`:
      - `WorkspaceBg`: `#F1F5F9` (light) / `#080B11` (dark)
      - `CatalogSurfaceBg`: `#F8FAFC` (light) / `#161D2D` (dark)
      - `CartSurfaceBg`: `#FFFFFF` (light) / `#0F1523` (dark)
      - `CardBg`: `#FFFFFF` (light) / `#1E273D` (dark)
      - `CardBorder`: `#E2E8F0` (light) / `Color(0x1AFFFFFF)` (dark)
      - `InputBg`: `#F1F5F9` (light) / `#121824` (dark)
      - `FooterBg`: `#FFFFFF` (light) / `#0A0E18` (dark)
      - `TextMain`: `#0F172A` (light) / `#F8FAFC` (dark)
      - `TextSecondary`: `#475569` (light) / `#94A3B8` (dark)
      - `Divider`: `#E2E8F0` (light) / `Color(0x1AFFFFFF)` (dark)
      - `EmeraldLight`: `#ECFDF5` (light) / `Color(0x1A10B981)` (dark)
      - `AmberLight`: `#FFFBEB` (light) / `Color(0x1AF59E0B)` (dark)
      - `RoseLight`: `#FFF1F2` (light) / `Color(0x1AEF4444)` (dark)
    - Updated `TabletTopToolbar`, `TabletProductGridCard`, `TabletCartItemRow`, and `TabletCartFooter` to dynamically consume these light/dark tokens.
  - **[V51-3] HTML Interactive Preview Parity (`interactive_preview/tablet_sell_screen_redesign_preview.html`)**:
    - Replaced hardcoded dark values with dynamic CSS variables: `--bg-nav-rail`, `--bg-cart-pane`, `--bg-cart-header`, `--bg-cart-footer`, `--bg-empty-cart-visual`, `--rail-hover`.
    - Defined variables for both `:root` (dark) and `[data-theme="light"]` (light).
    - Verified both Landscape and Portrait split views in light mode via screenshots.
  - **[V51-4] Production Deliverables & Tablet Live Verification (`emulator-5554`)**:
    - Built via `./gradlew assembleRelease` (versionCode 220927, versionName "2.6.51-V13-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V51.apk` (36MB)
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk` (36MB)
    - Tested live on `emulator-5554` (2560×1600 widescreen tablet):
      - Logged in via PIN `1234`.
      - Verified Sell screen and NavigationRail in Light Mode: crisp white cards, light slate backgrounds, dark high-contrast typography, radiant emerald accents.
      - Tested cart item addition and quantity steppers in Light Mode.
      - Verified bidirectional switching between Light and Dark modes in Settings with instant live re-theming.

## V52 - POS Brand Logo Invariance & Visual Integrity Hardening (V52 / V13-09-2026)

- **POS Brand Logo Theme Invariance (`Navigation.kt`)**:
  - **[V52-1] Theme-Invariant Brand Identity Rule**:
    - Addressed regression where light theme converted the POS brand logo container at the top of the NavigationRail into a light grey/white box, causing the white letter `'T'` in `ic_launcher_foreground` (`#F5F5F5`) to wash out against the light background.
    - Decoupled the POS logo container from theme changes: permanently locked the logo container background to the iconic dark obsidian linear gradient (`listOf(Color(0xFF1E293B), Color(0xFF0F172A))`) and specular emerald-cyan border (`listOf(brandEmerald, Color(0xFF34D399), Color(0xFF1E293B))`).
    - Enforced architectural standard: any active theme (Light Mode, Dark Mode, or System Mode) preserves the exact brand logo presentation without any wash-out, color shift, or contrast degradation.
  - **[V52-2] Tablet Emulator Live Verification (`emulator-5554`)**:
    - Tested live on 2560×1600 tablet display in Light Mode.
    - Confirmed the white `'T'` and electric blue lightning bolt stand out with maximum clarity, vibrancy, and specular depth against the dark badge while residing atop the crisp white NavigationRail.
  - **[V52-3] Production Deliverables**:
    - Built via `./gradlew assembleRelease` (versionCode 220928, versionName "2.6.52-V13-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V52.apk` (36MB)
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V13-09-2026.apk` (36MB)

## V53 - Removal of Quick Customer Strip from Cart Review Staging Pane (V53 / V14-09-2026)

- **Cart Review Staging Pane Simplification (`ScannerScreen.kt`)**:
  - **[V53-1] Removal of Quick Customer Strip (`TabletCustomerBar`)**:
    - Removed `TabletCustomerBar` (the quick customer strip displaying avatar, customer name e.g. "Walk-in Regular", phone, and due badge) from the tablet Cart Review staging pane.
    - Removed `showCustomerQuickDialog` AlertDialog and associated transient customer assignment states (`customerName`, `customerPhone`, `resolvedCustomer`) from `ScannerScreen.kt`.
    - Removed the customer quick pill (`portraitCustName`) from the portrait split Cart Review header.
    - Preserved full customer selection, ledger linking, Khata account assignment, and due collection in the dedicated checkout screen (`CartScreen.kt`).
    - The Cart Review pane now transitions directly from the header (Cart icon, title, item count chip, Hold & Clear icon buttons) into the scrollable cart item list, maximizing vertical staging space for items and quantity adjustments.
  - **[V53-2] Interactive Preview Parity (`interactive_preview/tablet_sell_screen_redesign_preview.html`)**:
    - Removed `.customer-bar` and `#portraitCustName` elements to match the Jetpack Compose tablet layout.
  - **[V53-3] Production Deliverables & Tablet Live Verification (`emulator-5554`)**:
    - Built via `./gradlew assembleRelease` (versionCode 220929, versionName "2.6.53-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V53.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Verified on `emulator-5554` (2560×1600 widescreen tablet).

## V54 - Adaptive Tablet Loading Screen Layout & Typography Hardening (V54 / V14-09-2026)

- **Adaptive Loading & Splash Screen Architecture (`SplashScreen.kt` & `Navigation.kt`)**:
  - **[V54-1] Bounded Adaptive Logo Scaling & Layout Centering**:
    - Resolved critical regression where the splash screen logo scaled using unbound percentage width (`fillMaxWidth(0.68f)` and `aspectRatio(1f)`), which on wide landscape tablets (2560×1600) caused the 'T' icon to balloon to ~680dp tall, pushing the title, tagline, spinner, loading messages, and progress indicator off-screen and under the system navigation bar.
    - Implemented explicit adaptive logo sizing (`160dp` in tablet landscape, `190dp` in tablet portrait, `145dp` in phone portrait, `120dp` in phone landscape) via `size(logoSize)`.
    - Wrapped content in a unified, vertically centered column inside `safeDrawingPadding()`, ensuring zero collision with system navigation bars or camera cutouts.
  - **[V54-2] Specular Ambient Radial Glow & Typography Polish**:
    - Introduced a pulsing sapphire/cyan ambient radial glow behind the logo (`Color(0xFF0082FF)`) synchronized with the breathing animation.
    - Enhanced subtitle typography from an illegible `6.sp` to a crisp, bold `10.5.sp` (tablet) / `8.5.sp` (phone) with `1.5.sp` letter tracking and high-contrast slate coloring (`Color(0xFF94A3B8)`).
    - Rounded the progress indicator with smooth pill corners (`clip(RoundedCornerShape(2.dp))`).
    - Added an anchored enterprise footer ("Enterprise POS • Secure Offline Engine") safely above system insets.
  - **[V54-3] Production Deliverables & Live Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220930, versionName "2.6.54-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V54.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Verified on `emulator-5554` (2560×1600 widescreen tablet).

## V55 - Tablet POS Top Search Bar Formatting & Height Alignment (V55 / V14-09-2026)

- **Tablet Search Bar & Toolbar Formatting (`ScannerScreen.kt`)**:
  - **[V55-1] Exact 44dp Height Alignment & M3 Default Squeeze Fix**:
    - Replaced Material 3 `OutlinedTextField` (which enforced a rigid 56dp minimum height, asymmetric internal label padding, and a 48dp leading icon slot) with a dedicated 44dp `Surface` + `BasicTextField` architecture.
    - Symmetrically aligned the search bar height (`44.dp`) and rounded corner radius (`10.dp`) to match all adjacent action buttons (`Scanner`, `Keypad`, `Stock Check`, `Held`).
    - Added an emerald radiant border highlight on focus (`1.5.dp TabletTerminalColors.Emerald`), smoothly reverting to subtle card border (`1.dp TabletTerminalColors.CardBorder`) when unfocused.
    - Integrated clean, instant clear button (`Icons.Default.Close`) when `searchQuery` is populated.
  - **[V55-2] Placeholder Truncation Elimination & Button Ergonomics**:
    - Eliminated internal padding bloat (reduced non-text width from 80dp+ down to 45dp), granting the text input area substantial additional horizontal breathing space.
    - Compacted button horizontal padding (`8.dp` in portrait, `10.dp` in landscape) and inter-item spacing to `6.dp`, preventing the quick action buttons from squeezing the search bar.
    - Fixed truncation of `tablet_search_placeholder` ("Search item or barcode…") so that the placeholder and entered search queries fit completely without clipping into "Search item or barco...".
  - **[V55-3] Interactive Preview Parity (`interactive_preview/tablet_sell_screen_redesign_preview.html`)**:
    - Updated `.search-field-box` with `height: 44px; border-radius: 10px; padding: 0 12px; gap: 8px; box-sizing: border-box;` for complete parity with Compose.
  - **[V55-4] Production Deliverables & Tablet Live Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220931, versionName "2.6.55-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V55.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Verified on `emulator-5554` (2560×1600 widescreen tablet).

## V56 - Dual Display (MRP + Offer) Price Tier Option Cleanup (V56 / V14-09-2026)

- **Product Label / Price Tier Selector UI (`ProductsScreen.kt`)**:
  - **[V56-1] Removal of Sparkle Icon & Subtext**:
    - Removed the decorative sparkle icon (`✨`) and the verbose subtitle subtext (`Supermarket style bargain label` / `products_price_tier_dual_hint`) from the `Dual Display (MRP + Offer)` price tier option button.
    - Cleaned up the left column to showcase only the crisp, bold title (`Dual Display (MRP + Offer)` / `ডুয়েল ডিসপ্লে (MRP + অফার)`), keeping it clean and aligned with the MRP & Offer price badge on the right.
  - **[V56-2] Interactive Preview Parity (`interactive_preview/code_image_price_switch_preview.html`)**:
    - Removed `.dual-icon` and `.dual-hint` elements from `#tierBtnDual` to match the Jetpack Compose component.
  - **[V56-3] Production Deliverables & Live Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220932, versionName "2.6.56-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V56.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Verified on Samsung Galaxy Tab A7 (`emulator-5554`, 2000×1200, 224dpi).

## V57 - Tablet POS Cart Review Header Cart Capsule Button (V57 / V14-09-2026)

- **Cart Review Header Redesign (`ScannerScreen.kt`)**:
  - **[V57-1] Replacement of Static Count Badge with 3D Indigo Cart Capsule Button**:
    - Replaced the passive green/teal "X items" pill badge (`Surface` with `TabletTerminalColors.EmeraldLight`) on the right side of the Cart Review header with the active 3D Indigo Cart Capsule Button (`TabletCartCapsuleButton`), matching the top bar of the Products screen (`ProductsScreen.kt`).
    - Styled with `Color(0xFF4F46E5)` indigo capsule surface, 12dp rounded corners, `Realistic3DSupermarketCartIcon(17.dp)`, bold localized `Cart` text (`R.string.cart`), and when `cartUnitCount > 0`, an animated red pill badge (`Color(0xFFEF4444)`) displaying the total cart unit quantity.
    - Integrated rich micro-animations: spring press scale (`0.93f`), cart capsule bounce (`1.12f -> 1.0f`), cart icon tilt & hop (`-16°`, `-8f` offset), and red badge pop & swing (`1.75f` scale, `22°` rotation).
    - Tapping the Cart Capsule button directly triggers `onNavigateToCart` (`backStack.add(Cart)`), allowing instant navigation to the Cart / Checkout view.
  - **[V57-2] Responsive Split-Mode Layout Support (Landscape & Portrait)**:
    - Landscape: Positioned cleanly on the far right of the Cart Review header opposite the title and subtitle.
    - Portrait: Placed compactly (`height = 30.dp`) in the top bar of the bottom half split-pane opposite the hold and clear quick-action buttons.
  - **[V57-3] Interactive Preview Parity (`interactive_preview/tablet_sell_screen_redesign_preview.html`)**:
    - Added `.cart-capsule` and `.cart-count-badge` CSS rules with hover/active animations.
    - Updated both landscape (`#landscapeCartPane`) and portrait (`#portraitSplitCart`) cart review headers in the interactive prototype to feature the interactive cart capsule button.
  - **[V57-4] Production Deliverables & Tablet Live Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220933, versionName "2.6.57-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V57.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Verified on Samsung Galaxy Tab A7 (`emulator-5554`, 2000×1200, 224dpi).

## V58 - Cart Screen Total Cart Value Summary Card (V58 / V14-09-2026)

- **Cart Screen Value Breakdown Architecture (`CartScreen.kt`)**:
  - **[V58-1] Pinned Total Cart Value Summary Card (`CartTotalSummaryCard`)**:
    - Resolved critical omission where the dedicated Cart review screen (`CartScreen`) displayed cart items and bottom action buttons (`Hold Cart` & `Checkout`), but lacked the total cart value summary box (`Subtotal` and `Estimated Total`), leaving empty vertical space.
    - Implemented `CartTotalSummaryCard` pinned inside the bottom docked `Surface` container immediately above the action buttons:
      - Styled in a responsive `Surface` card (`RoundedCornerShape(16.dp)`) with `color = if (isDark) #0A0E18 else #F8FAFC` and subtle border `if (isDark) #1FFFFFFF else #E2E8F0`.
      - **Subtotal Row**: Displays localized `tablet_subtotal` ("Subtotal" / "উপমোট") with `formatTaka(subtotal)`.
      - **Discount Row (Conditional)**: If `discountAmount > 0`, cleanly shows localized `discount` in red (`#EF4444`) with `-${formatTaka(discountAmount)}`.
      - **Estimated Total Row**: Prominently highlights localized `tablet_estimated_total` ("Estimated Total" / "আনুমানিক মোট") in extra-bold dark text alongside the grand total formatted in 22sp Black emerald green (`#00C48C`).
  - **[V58-2] Grand Total Checkout Button Accuracy**:
    - Fixed checkout action button label to compute with `formatTaka(grandTotal)` rather than `formatTaka(subtotal)`, guaranteeing exact price parity with applied discounts.
  - **[V58-3] Production Deliverables & Tablet Live Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220934, versionName "2.6.58-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V58.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
## V59 - Stock Check Mode Banner "Tap or Scan" Phrasing (V59 / V14-09-2026)

- **Stock Check Mode Banner Phrasing (`strings.xml`, `values-bn/strings.xml`, `ScannerScreen.kt`)**:
  - **[V59-1] Dual Interaction Phrasing ("Tap or Scan")**:
    - Updated `R.string.tablet_stock_check_banner` to explicitly reflect both tap interaction on tablet touch screens and barcode scanning (via camera viewfinder or hardware USB/Bluetooth barcode gun):
      - **English**: `"Stock Check Mode Active  -  Tap or Scan any product to view & adjust stock"`
      - **Bangla (`values-bn`)**: `"স্টক অনুসন্ধান মোড সক্রিয়  -  স্টক ও তথ্য দেখতে যেকোনো পণ্যে ট্যাপ বা স্ক্যান করুন"`
    - Synchronized HTML interactive preview dictionary and markup (`interactive_preview/tablet_sell_screen_redesign_preview.html`).
  - **[V59-2] Production Deliverables & Tablet Live Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220935, versionName "2.6.59-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V59.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Verified on Samsung Galaxy Tab A7 (`emulator-5554`, 2000×1200, 224dpi).

## V60 - Navigation Icon Performance Optimization & Sales Report/History Date Reconciliation (V60 / V14-09-2026)

- **Navigation Icon Animation Performance (`Navigation.kt`)**:
  - **[V60-1] Unselected Animation Teardown & Single-Coroutine Active State**:
    - Resolved critical performance bottleneck where every `Animated3DNavItemIcon` (selected or unselected) instantiated `rememberInfiniteTransition` and ran 4 infinite animation loops continuously across 5–8 tabs, spawning 20–32 perpetual background animation coroutines that wasted CPU and drained battery on lower-end devices.
    - Refactored `Animated3DNavItemIcon`:
      - Unselected state renders static `Nav3DIconContent(...)` wrapped in a standard `Box` with zero animation coroutines or transitions instantiated.
      - Selected state delegates to `ActiveAnimated3DNavItemIcon`, which runs only the specific single-axis micro-animation required for that active tab (e.g. `translationY` for Sell/Expenses, `rotationZ` for Products/History, `spin` for Settings, `scale` for More/Report).
      - Drops continuous navigation idle coroutines from 20–32 down to 0 when idle and exactly 1 for the currently active tab.

- **Sales Period Date Range Reconciliation (`SalesReportScreen.kt`, `SalesHistoryScreen.kt`, `strings.xml`, `values-bn/strings.xml`)**:
  - **[V60-2] Calendar Week & Month Synchronization**:
    - Resolved business logic discrepancy where Sales Report used rolling windows (last 7 days, last 30 days) while Sales History used true calendar periods (Mon–Sun / 1st of month to today), causing cashiers and store managers to see conflicting totals between the two screens for the same period.
    - Standardized `ReportPeriod.WEEK` in `SalesReportScreen.kt` to rewind to `firstDayOfWeek` (same as `SalesHistoryScreen.kt`) and end at `endOfDay(now) + 1`.
    - Standardized `ReportPeriod.MONTH` in `SalesReportScreen.kt` to start at the 1st of the current month (`Calendar.DAY_OF_MONTH = 1`) and end at `endOfDay(now) + 1`.
    - Standardized `SalesHistoryScreen.kt`'s `WEEK`, `MONTH`, and `YEAR` periods to end at `endOfDay(now) + 1` (was `now + 1`) to ensure identical millisecond boundary alignment across both screens.
    - Updated period chip string resources to clear, unambiguous labels:
      - **English**: `sales_report_period_week` -> `"📅 This Week"`, `sales_report_period_month` -> `"🗓️ This Month"`
      - **Bangla (`values-bn`)**: `sales_report_period_week` -> `"📅 এই সপ্তাহ"`, `sales_report_period_month` -> `"🗓️ এই মাস"`

- **Production Deliverables & Cross-Device Verification**:
  - **[V60-3] Production Build & Dual-Emulator Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220936, versionName "2.6.60-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V60.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Verified on Phone (`emulator-5554`, Pixel phone) and Tablet (`emulator-5556`, widescreen tablet).
    - Verified 100% order count and profit margin reconciliation between Sales History and Sales Report for "This Week" (0 sales) and "This Month" (51 orders, ৳6,100.59 gross profit).

## V61 - Held Cart Voucher/Credit Preservation, Loyalty Permission Gate, Inventory Role Fix, Full Localization of History/Dues/Suppliers, Centralized Date Boundaries & Sales Report Business Health Cross-Reference (V61 / V14-09-2026)

- **Cart State Integrity & Tender Recovery (`POSViewModels.kt`, `HeldCartSerializationTest.kt`)**:
  - **[V61-1] Store Credit & Voucher Re-hydration on Held Carts**:
    - Resolved critical state loss bug where `holdCurrentCart()` serialized items and customer details but dropped `storeCreditApplied` and `redeemedVoucher`. Customers whose orders were put on hold previously had their vouchers and store credits silently discarded upon cart restoration.
    - Updated `HeldCartPayload` data structure to include `val storeCreditApplied: Double = 0.0` and `val voucherCode: String? = null`.
    - Updated `encodeHeldCartPayload()` and `decodeHeldCartPayload()` to serialize and deserialize both fields safely with full backward compatibility.
    - Updated `holdCurrentCart()` to capture active credit/voucher values and reset ViewModel state to clean defaults.
    - Updated `restoreHeldCart()` to restore `storeCreditApplied` and query `repository.getStoreCreditVoucher(code)` to re-attach the voucher object.
    - Added comprehensive JVM unit test suite `HeldCartSerializationTest.kt`.

- **Security & Permission Gate Enforcement (`Navigation.kt`, `DataRepository.kt`)**:
  - **[V61-2] Loyalty Screen Security Gate**:
    - Enforced role check on `onNavigateToLoyalty` and route destination `entry<Loyalty>`: requires `isOwner || isSupervisor || activePermissions.contains(AppPermissions.SYSTEM_SHOP_SETTINGS)`.
    - Unauthorized cashier attempts trigger a warning toast (`ownerOnlyLoyaltyText`) and immediate back-navigation, protecting VIP tier settings and points ledgers.
  - **[V61-3] Inventory Adjustment Permission Normalization**:
    - Fixed `DataRepository.kt:recordInventoryAdjustment()` which improperly required `requireOwnerSession()`.
    - Aligned with `adjustProductStock()` by enforcing granular permission `requirePermission(AppPermissions.INVENTORY_ADJUST_STOCK)`, allowing authorized inventory managers to document damaged stock and reconciliation counts.

- **Localization & Type Safety Architecture (`SalesHistoryScreen.kt`, `DuesScreen.kt`, `SuppliersScreen.kt`, `strings.xml`, `values-bn/strings.xml`)**:
  - **[V61-4] Sales History Localization & Enum State Migration**:
    - Refactored `HistoryReportPeriod` to use `@StringRes val labelRes: Int`.
    - Replaced raw English strings for filter state (`customerFilter = "All"`) and sort order (`sortOrder = "Newest"`) with type-safe enums `HistoryCustomerFilter { ALL, SAVED, WALK_IN }` and `HistorySortOption { NEWEST, HIGHEST_TOTAL, LOWEST_TOTAL, HIGHEST_PROFIT, LOWEST_PROFIT }`.
    - Period chips, sort dropdown, and customer filter bottom sheet now fully adapt to natural Bengali phrasing in `values-bn`.
  - **[V61-5] Dues Screen Localization & Enum Architecture**:
    - Refactored `DuesTab`, `DuePeriodFilter`, and `DuesSort` to type-safe enums with `@StringRes val labelRes: Int`.
    - Localized tabs: "Outstanding" (`বকেয়া তালিকা`), "Overdue" (`বিলম্বিত`), "Settled" (`পরিশোধিত`), "Payments" (`পেমেন্ট রেকর্ড`).
    - Localized period filter chips and sorting dropdown.
  - **[V61-6] Suppliers Screen Filter State Enum Migration**:
    - Added `SupplierDueFilter { ALL, HAS_DUE, SETTLED }` with `@StringRes val labelRes: Int` in `SuppliersScreen.kt`.
    - Replaced raw English string comparisons (`dueFilter == "All"`) with exhaustive enum matching.

- **Architectural Cleanup & Cross-Module Synchronization (`AppUtils.kt`, `RegisterCloseScreen.kt`, `SalesReportScreen.kt`, `AppUtilsDateTest.kt`)**:
  - **[V61-7] Centralized Timezone-Safe Date Utilities**:
    - Centralized `startOfDay(millis: Long)` and `endOfDay(millis: Long)` into `com.techyfiposmeds.util.AppUtils.kt`.
    - Removed duplicate, ad-hoc implementations across `SalesHistoryScreen.kt`, `SalesReportScreen.kt`, `DuesScreen.kt`, and `RegisterCloseScreen.kt`.
    - Validated millisecond boundary precision via `AppUtilsDateTest.kt`.
  - **[V61-8] Sales Report Business Health & Operational Cross-Reference**:
    - Injected `expenseViewModel` and `supplierViewModel` with navigation callbacks into `entry<SalesReport>` in `Navigation.kt`.
    - Added interactive "Business Health & Cross-Reference" card in `OverviewPanel` inside `SalesReportScreen.kt`:
      - Displays synchronized Period Expenses (`sales_report_expenses_label`).
      - Displays Net Operating Profit (`sales_report_operating_profit = grossProfit - discounts - periodExpenses`).
      - Displays Outstanding Vendor Payables (`sales_report_vendor_payables`).
      - Direct one-tap navigation links to "View All Expenses" and "View Suppliers".

- **Production Deliverables & Cross-Device Verification**:
  - **[V61-9] Production Build & Dual-Emulator Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220937, versionName "2.6.61-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V61.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Successfully verified on both Phone (`emulator-5554`, Pixel phone) and Tablet (`emulator-5556`, widescreen tablet).
    - Executed all 28 automated JVM unit tests (`./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL).

## V62 - Clean Architecture, Audit-Trail Hardening & POS Bug Fixes (V62 / V14-09-2026)

- **UI Thread Safety & Architecture Hygiene**:
  - **[V62-1] UI-Thread Locale Isolation (`Navigation.kt`)**:
    - Removed `java.util.Locale.setDefault(locale)` from `LocaleWrapper`, preventing global JVM mutation from the UI composition pipeline and avoiding race conditions with background coroutines doing date/number formatting.
  - **[V62-2] Clean Imports & Namespace Hygiene (`ProductsScreen.kt`, `DuesScreen.kt`, `LoyaltyScreen.kt`)**:
    - Removed duplicate wildcard imports `import com.techyfiposmeds.ui.components.*`.
  - **[V62-3] Return Audit Presets DataStore Migration (`SettingsDataStore.kt`, `ReturnDialog.kt`)**:
    - Migrated custom return audit presets from unencrypted `SharedPreferences` to `SettingsDataStore` (`return_audit_presets_json`).
    - Added one-time automatic legacy migration and dynamic Flow updates.
  - **[V62-7] Supplier Non-Nullable Safe Call Hygiene (`SuppliersScreen.kt`)**:
    - Removed redundant `?.` operators on non-nullable `Supplier.name` and `Supplier.phone`.

- **Audit-Trail Hardening & Database Migration**:
  - **[V62-4] Register Closing Discrepancy Reason Audit Trail (`AppEntities.kt`, `AppDatabase.kt`, `POSViewModels.kt`, `RegisterCloseScreen.kt`, `DataRepository.kt`)**:
    - Added dedicated `discrepancyReason: String? = null` field to `RegisterClosing` entity to prevent conflating cash discrepancies with general closing notes.
    - Bumped Room DB schema version to 28 with `MIGRATION_27_28` (`ALTER TABLE register_closings ADD COLUMN discrepancyReason TEXT`).
    - Added dedicated "Discrepancy Reason (Audit Trail)" text field with quick reason chips (`Cash Drop Delay`, `Change Rounding`, `Count Error`, `Petty Cash Float`, `System Miscalculation`) when physical counted cash diverges from system expected cash.
    - Preserved separate general shift closing notes and updated backup export/import and detail dialogs.

- **Checkout & Cart Bug Fixes**:
  - **[V62-5] Cash Zero-Tender Insufficiency Validation Bug Fix (`CartScreen.kt`, `DueSaleAmountTest.kt`)**:
    - Fixed `calculateEffectiveAmountPaid`: when `paymentMethod == "Cash"`, returns exact `enteredAmountPaid` instead of silently falling back to `grandTotal` when cash received is 0 or empty.
    - Updated `isCashValid` to enforce `effectiveAmountPaid >= remainingToPay && effectiveAmountPaid > 0.0`.
    - Complete Sale button remains disabled with red error warning until valid cash tender is entered.
    - Added unit test coverage verifying 0 cash tender returns 0.0 and explicit tender is preserved.
  - **[V62-8] Held Cart Customer Identity Synchronization (`POSViewModels.kt`)**:
    - Added `customerName` and `customerPhone` to `HeldCartPayload` to prevent drift between HeldCart DB entity and payload JSON upon restoration.

- **UI Localization & Layout Adaptability**:
  - **[V62-6] More Hub Operations Badge Localization (`Navigation.kt`, `strings.xml`, `values-bn/strings.xml`)**:
    - Replaced hardcoded English string `"Operations"` with `stringResource(R.string.more_hub_badge_operations)` ("Operations" / "অপারেশনস").
  - **[V62-9] More Hub Dynamic Height & Compact Screen Affordance (`Navigation.kt`)**:
    - Replaced hardcoded `heightIn(max = 490.dp / 520.dp)` with dynamic screen-ratio calculation (`(screenHeight * if (isTablet) 0.55f else 0.65f).coerceIn(280.dp, 520.dp)`).
  - **[V62-10] Dynamic Sales History Current Timestamp (`SalesHistoryScreen.kt`)**:
    - Removed `remember { System.currentTimeMillis() }` which froze `now` across midnight, calculating current time dynamically.

- **Production Deliverables & Cross-Device Verification**:
  - **[V62-11] Production Build & Device Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220938, versionName "2.6.62-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V62.apk`
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk`
    - Successfully verified on mobile device (`emulator-5554`, Pixel phone):
      - Cart cash tender insufficiency check and exact amount tender flow.
      - More Hub dynamic height and localized badge.
      - Register Close reconciliation discrepancy audit field with chips.
      - Return dialog DataStore presets management.
      - Suppliers and Sales History screens.
    - Executed all automated JVM unit tests (`./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL).

## V63 - POS Security Hardening, Dialog Architecture Modularization & Layout Adaptability (V63 / V14-09-2026)

- **POS Security & Authorization Hardening**:
  - **[V63-1] Hold Cart Permission Enforcement (`CartScreen.kt`, `strings.xml`, `values-bn/strings.xml`)**:
    - Eliminated critical security bypass where TopAppBar "Hold" action and bottom sheet "Hold Cart" button operated unconditionally without checking staff permissions.
    - Added strict permission gate checking `user?.hasPermission(AppPermissions.SALES_HOLD_CART)`.
    - If unauthorized, immediately triggers `SupervisorAuthDialog` requiring Manager or Owner PIN elevation with localized action description (`R.string.supervisor_auth_action_hold_cart`: "Hold active shopping cart" / "বর্তমান কার্টটি স্থগিত (হোল্ড) করুন").
    - If elevation is cancelled or denied, displays feedback via localized toast `R.string.perm_toast_hold_cart_denied` ("Permission required to hold cart" / "কার্ট স্থগিত (হোল্ড) করার অনুমতি নেই").
  - **[V63-2] Dedicated Customer Loyalty Permission (`AppPermissions.kt`, `Navigation.kt`, `strings.xml`, `values-bn/strings.xml`)**:
    - Created dedicated `AppPermissions.LOYALTY_MANAGE = "loyalty.manage"` permission, decoupling Customer Loyalty access from generic `SYSTEM_SHOP_SETTINGS`.
    - Registered new `PermissionCategory(id = "loyalty", titleRes = R.string.perm_cat_loyalty, ...)` with localized titles and descriptions in English and Bangla.
    - Included `LOYALTY_MANAGE` in `ALL_PERMISSIONS`, `DEFAULT_OWNER_PERMISSIONS`, and `DEFAULT_MANAGER_PERMISSIONS`.
    - Updated navigation gates in `Navigation.kt` (`onNavigateToLoyalty` and `entry<Loyalty>`) to enforce `AppPermissions.LOYALTY_MANAGE`.
  - **[V63-3] Supervisor Auth Description & Toast Localization (`CartScreen.kt`, `strings.xml`, `values-bn/strings.xml`)**:
    - Extracted all hardcoded English strings used in supervisor authorization modals and permission denial toasts into `strings.xml` and `values-bn/strings.xml`.
    - Added localized action strings:
      - `supervisor_auth_action_cart_void`: "Void entire shopping cart" / "সম্পূর্ণ কার্ট বাতিল করুন"
      - `supervisor_auth_action_remove_item`: "Remove item from cart" / "কার্ট থেকে আইটেম মুছুন"
      - `supervisor_auth_action_hold_cart`: "Hold active shopping cart" / "বর্তমান কার্টটি স্থগিত (হোল্ড) করুন"
      - `supervisor_auth_action_price_tier`: "Change pricing tier" / "মূল্য স্তর পরিবর্তন করুন"
      - `supervisor_auth_action_discount_limit`: "Apply discount over allowed limit" / "নির্ধারিত সীমার অতিরিক্ত ছাড় প্রয়োগ করুন"
      - `supervisor_auth_action_negative_stock`: "Allow sale of out-of-stock item" / "মজুতহীন পণ্যের বিক্রয় অনুমোদন করুন"
    - Added localized denial toasts:
      - `perm_toast_cart_void_denied`: "Permission required to void cart" / "কার্ট বাতিল করার অনুমতি নেই"
      - `perm_toast_remove_item_denied`: "Permission required to remove item" / "আইটেম মোছার অনুমতি নেই"
      - `perm_toast_hold_cart_denied`: "Permission required to hold cart" / "কার্ট স্থগিত (হোল্ড) করার অনুমতি নেই"
      - `perm_toast_price_tier_denied`: "Permission required to change price tier" / "মূল্য স্তর পরিবর্তন করার অনুমতি নেই"

- **UI Layout Mode Adaptability**:
  - **[V63-4] `SalesReportScreen` UI Layout Mode Override Honor (`SalesReportScreen.kt`, `Navigation.kt`)**:
    - Updated `SalesReportScreen` composable signature to accept `isTablet: Boolean = LocalConfiguration.current.screenWidthDp >= 600`.
    - Updated `Navigation.kt` to route the resolved app-level `isTablet` boolean (derived from `uiLayoutMode`: AUTO, TABLET, PHONE) directly into `SalesReportScreen`.
    - Guarantees `SalesReportScreen` strictly respects user-selected "PHONE" layout mode on widescreen tablets and "TABLET" layout mode on compact phones.

- **High-Maintainability Dialog Architecture Modularization**:
  - **[V63-5] Component Extraction to `com.techyfiposmeds.ui.dialogs` (`CartScreen.kt`, `ProductsScreen.kt`)**:
    - Decomposed monolithic screens into 9 clean, focused, and testable dialog and sub-component files:
      1. `CartItemDialogs.kt`: `ManualQuantityDialog`, `CreditLimitWarningDialog`, `ConfirmDueSaleDialog`
      2. `ReceiptSummaryDialog.kt`: `InvoiceShareFormat`, `ReceiptSummaryDialog`
      3. `CustomItemDialogs.kt`: `AddCustomItemDialog`, `ManageQuickSalePresetsDialog`, `EditItemNoteDialog`, `ManageNotePresetsDialog`
      4. `Product3DIcons.kt`: 15 realistic 3D animated icons (`Animated3DProductBoxIcon`, `Animated3DCategoryTagIcon`, etc.), `DateInputField`, `ProductDialogSectionLabel`, `Modifier.dashedProductImageBorder`
      5. `AddEditProductDialog.kt`: Complete `AddEditProductDialog` modal with 3D section cards, image picker, barcode scanning, wholesale/retail pricing, supplier selection, and stock management.
      6. `ProductMediaDialogs.kt`: Camera barcode scanner sheet `ProductBarcodeScanDialog` and high-res image modal `EnlargedImageDialog`.
      7. `ProductCodePrintDialogs.kt`: `PriceTierSwitcherCard`, batch barcode/QR label print sheet `BatchProductCodePrintDialog`, single product code dialog `ProductCodeImageDialog`, `LabelTextSizeControl`.
      8. `StockAuditDialogs.kt`: Stock audit history bottom sheet `StockAuditHistorySheet` and product picker `ProductPickerForAdjustmentDialog`.
      9. `ManageCategoriesDialog.kt`: Category management bottom sheet `ManageCategoriesDialog` with color picker and product count tags.
    - Codebase line count reduction:
      - `CartScreen.kt`: reduced from 6,382 lines to 3,171 lines (~50% reduction).
      - `ProductsScreen.kt`: reduced from 9,644 lines to 4,987 lines (~48% reduction).
    - Preserved 100% feature parity, animation smoothness, touch target compliance (>= 48dp), and localized phrasing.

- **Production Deliverables & Cross-Device Verification**:
  - **[V63-6] Production Build & Device Verification**:
    - Built via `./gradlew assembleRelease` (versionCode 220939, versionName "2.6.63-V14-09-2026"):
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V63.apk` (37.4 MB)
      - `/Users/shahidulhaque/App Dev/Techyfi POS/Techyfi-POS-Final-V14-09-2026.apk` (37.4 MB)
    - Automated Unit Tests:
      - Executed all 28 automated JVM unit tests (`./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL).
    - Live Android Device Verification on `emulator-5554` (Pixel phone):
      - App PIN unlock and Sell screen (`mobile_v63_pos.png`).
      - Cart Screen with Hold Cart permission security check (`mobile_v63_cart_view.png`, `mobile_v63_hold_cart_result.png`).
      - More Hub navigation and Customer Loyalty screen with dedicated `loyalty.manage` permission (`mobile_v63_more_hub.png`, `mobile_v63_loyalty.png`).
      - Sales Report Screen responsive layout (`mobile_v63_report.png`).
      - Products Screen, Add/Edit Product Dialog, and Stock Audit & Logs History sheet (`mobile_v63_products.png`, `mobile_v63_add_product_dialog.png`, `mobile_v63_stock_audit_sheet.png`).

## V64 - Comprehensive System Robustness, Security, Architecture & Performance Upgrades (V64 / V14-09-2026)

- **Cart & Hold State Validation (Items 14 & 24)**:
  - **[V64-1] Cumulative Stock Availability Verification on Held Cart Resume (`POSViewModels.kt`)**:
    - Eliminated inventory bypass where held carts could restore stock depleted or sold out while held.
    - Implemented `stockAllocationMap` tracking aggregate quantities per barcode across multiple line items.
    - Validates against live catalog inventory (`product.stockQty`); unavailable items remain safely preserved in the held cart database record and a localized notification alerts the cashier.
  - **[V64-2] Voucher Balance & Entity Preservation on Held Cart Resume (`POSViewModels.kt`, `HeldCartPayload`)**:
    - Extended `HeldCartPayload` schema to serialize `voucherOriginalBalance`, `voucherRemainingBalance`, and `voucherCustomerPhone`.
    - Guarantees `redeemedVoucher.value` is never null when `storeCreditApplied > 0.0` by synthesizing a fallback active `StoreCreditVoucher` if external voucher was temporarily unindexed.

- **UI & Navigation Architecture (Items 15, 18, 23, 26)**:
  - **[V64-3] Consolidated Checkout LaunchedEffect (`CartScreen.kt`)**:
    - Merged duplicate consecutive `LaunchedEffect(showCheckout)` blocks into a unified, performant effect avoiding double-triggering animations or state resets.
  - **[V64-4] Register Close Screen Naming Harmonization (`RegisterCloseScreen.kt`)**:
    - Renamed primary Composable from legacy `DayStartCloseScreen` to canonical `RegisterCloseScreen`, matching its file name, purpose, and navigation destination.
    - Retained `@Deprecated fun DayStartCloseScreen` proxy with identical signature for complete backward compatibility.
  - **[V64-5] Pre-Navigation Security Guard in More Operations Hub (`Navigation.kt`)**:
    - Replaced post-navigation `LaunchedEffect` rejection with immediate tap interception in `OperationHubCard`.
    - Cards display locked status (`isLocked = true`), dimmed alpha (`0.72f`), and lock badge when the user lacks required permission.
    - Clicking a locked card emits an immediate, localized Toast explaining permission requirement (`owner_only_*`), preventing blank screen flashes or navigation backstack churn.
  - **[V64-6] Locale Context Integrity (`Navigation.kt`)**:
    - Verified complete absence of `Locale.setDefault()` mutations inside Composables, preserving Android's configuration hierarchy via `LocaleWrapper(language)`.

- **Catalog, Profit Margin & SQLite Reliability (Items 16, 17, 19, 20)**:
  - **[V64-7] Elimination of 70% Cost Heuristic (`POSViewModels.kt`, `DiscountCalculationTest.kt`)**:
    - Replaced arbitrary 70% cost fallback with accurate break-even cost (`price`) for ad-hoc/unindexed products, preventing false 30% profit distortion for low-margin groceries.
    - In `calculateInvoiceProfit`, cross-references the live product catalog before resorting to fallback.
  - **[V64-8] Product SQLite Constraint Strategy (`AppDaos.kt`)**:
    - Upgraded `ProductDao.insertProduct` from `OnConflictStrategy.ABORT` to `OnConflictStrategy.REPLACE`, eliminating app crashes (`SQLiteConstraintException`) during duplicate barcode imports or catalog sync.
    - Added `insertProductIgnore` (`OnConflictStrategy.IGNORE`) for non-destructive import routines.
  - **[V64-9] Hardware Accelerated QR Matrix Generation (`AppUtils.kt`)**:
    - Replaced 262,144 individual JNI `setPixel()` invocations in `generateQrBitmap` with single bulk `setPixels(pixels, 0, size, 0, 0, size, size)`.
    - Provides a ~50x speedup in QR code generation for digital receipt sharing and customer slips.
  - **[V64-10] Strict Product Expiry Validation (`AppEntities.kt`, `AddEditProductDialog.kt`, `AppUtils.kt`, `ProductValidationTest.kt`)**:
    - Added `Product.hasValidExpiryDate` extension property and configured non-lenient `Calendar` parsing in `AppUtils.kt`.
    - Live warning alerts cashier during product entry if an invalid date (such as Feb 31 or malformed text) is entered, ensuring near-expiry alerts never silently fail.

- **Localization, Expenses & Permissions (Items 21, 22, 25)**:
  - **[V64-11] Localized Expense Category Filtering (`ExpensesScreen.kt`)**:
    - Replaced hardcoded English string comparisons with robust `ExpenseCategoryFilter` data class supporting multi-locale aliases and localized strings.
  - **[V64-12] In-Screen Permission Enforcement for Suppliers (`SuppliersScreen.kt`, `Navigation.kt`)**:
    - Injected `isOwner`, `isSupervisor`, and `activePermissions` into `SuppliersScreen`.
    - Guarded all mutations (Add Supplier, Receive Stock, Pay Due, Edit, Delete) with `runWithSupplierPermission` and integrated `SupervisorAuthDialog`.
  - **[V64-13] Security Hardening of Dues Screen Supervisor Default (`DuesScreen.kt`)**:
    - Fixed security footgun by changing parameter default from `isSupervisor: Boolean = isOwner` to `isSupervisor: Boolean = false`.

- **Production Build Deliverables (V64)**:
  - `Techyfi-POS-Final-V64.apk` (37.4 MB, versionCode 220940, versionName "2.6.64-V14-09-2026")
  - `Techyfi-POS-Final-V14-09-2026.apk` (37.4 MB)
  - Unit tests: 48/48 tests passing (`./gradlew testDebugUnitTest`).

- **Version 2.6.66-V15-09-2026 (Build V66  -  User-Configurable Default Cost Percentage & Profit Margin Estimation)**:
  - **Dynamic Default Cost Margin Estimation Engine**:
    - Replaced the hardcoded break-even 100% assumption for items without recorded purchase prices with a user-configurable default cost percentage stored in `SettingsDataStore` (`keyDefaultCostPercentage`).
    - Added reactive state management in `POSViewModels.kt` (`SettingsViewModel.defaultCostPercentage` and `saveDefaultCostPercentage(Double)`).
    - Updated `DataRepository.calculatePnlSummary(...)` and `SalesReportScreen.kt` to dynamically respect `defaultCostPercentage`:
      - Items with recorded `buyPrice > 0.0` continue to calculate exact real cost: `qty * buyPrice`.
      - Items missing a recorded cost gracefully apply the configured default cost ratio: `lineTotal * (defaultCostPercentage / 100.0)`.
  - **Settings UI Integration**:
    - Added an interactive "Default Cost Margin Estimation" card in `SettingsScreen.kt` under General Preferences.
    - Features quick-preset segmented buttons (50%, 70%, 85%, 100% Break-even) and an explanatory caption displaying the corresponding implied profit margin.
    - Fully localized in both English and Bengali (`strings.xml` and `values-bn/strings.xml`).
  - **Top Bar Adaptive Synchronization**:
    - Connected `SalesReportScreen`'s top app bar title to the centralized `isTablet` setting (`PosTopAppBarTitle(isTablet = isTablet)`).
  - **Production Build Deliverables (V66)**:
    - `Techyfi-POS-Final-V66.apk` (36.0 MB, versionCode 220942, versionName "2.6.66-V15-09-2026")
    - `Techyfi-POS-Final-V15-09-2026.apk` (36.0 MB)
    - Unit tests: 48/48 tests passing (`./gradlew testDebugUnitTest`).

- **Version 2.6.65-V15-09-2026 (Build V65  -  Comprehensive App Polish & Modernization)**:
  - **Layout Mode Unification & Responsive Top App Bars**:
    - Propagated user-selected layout mode (`isTablet: Boolean` computed from `uiLayoutMode`: AUTO, TABLET, PHONE) across all 11 screen destinations in `Navigation.kt` (`ManageUsersScreen`, `CartScreen`, `ProductsScreen`, `SalesHistoryScreen`, `ExpensesScreen`, `SuppliersScreen`, `DuesScreen`, `SettingsScreen`, `RegisterCloseScreen`, `LoyaltyScreen`, `CustomersScreen`).
    - Stripped hardcoded screen-level width calculations (`LocalConfiguration.current.screenWidthDp >= 600`), establishing centralized layout control.
    - Wired `PosTopAppBarTitle(isTablet = isTablet)` everywhere for responsive 3D icon rendering and ergonomic font sizing.
  - **Bangla Localization & String Externalization**:
    - Added 34 localized string pairs across `values/strings.xml` and `values-bn/strings.xml` using natural Bangladeshi commercial terminology (বাকি, জমা, রসিদ, ইনভয়েস, ইত্যাদি).
    - Localized toast alerts and descriptions for held cart operations, customer dossier notes and ledger records, bulk product updates and label printing, settings database reset/restore/encryption, Z/X-Report generation, loyalty membership updates, scanner stock confirmations, Excel export errors, and cart/history badge counters.
  - **Ergonomics & Touch Target Sizing (>= 44/48dp)**:
    - Customer dossier note action button upgraded from `28.dp` height to `Modifier.defaultMinSize(minHeight = 44.dp)`.
    - Supplier due filter chips upgraded to `Modifier.defaultMinSize(minHeight = 44.dp)`.
    - Customer screen action buttons (merge, WhatsApp, tag, deselect) enlarged to >= 40–44dp touch targets.
  - **Material 3 Modernization & Deprecation Fixes**:
    - Modernized `TabRow` to `PrimaryTabRow` in `DuesScreen.kt` and `LoyaltyScreen.kt`.
    - Migrated `Icons.Default.ExitToApp` to `Icons.AutoMirrored.Filled.ExitToApp` in `Navigation.kt`.
    - Added `@get:StringRes` property-getter annotations across all enums and filter data classes to eliminate Android Lint and Kotlin compiler property target warnings.
  - **Production Build Deliverables (V67 & V68)**:
    - `Techyfi-POS-Final-V68.apk` (versionCode 220944, versionName "2.6.68-V15-09-2026")
    - `Techyfi-POS-Final-V67.apk` (versionCode 220943, versionName "2.6.67-V15-09-2026")
    - `Techyfi-POS-Final-V15-09-2026.apk`
    - Product Catalog Layout Polish & Zero-Wrapping Price Formatting (V68):
      - Restructured price & unit presentation across `ProductItemRow` (both tablet and phone layout modes) and `ProductGridCard`.
      - Enforced `maxLines = 1` and `softWrap = false` with `Alignment.Bottom` baseline alignment on product selling price and unit labels (`formatTaka(product.sellingPrice) / unit`).
      - Enforced explicit minimum column width and spacing (`min = 100.dp`, `Arrangement.spacedBy(4.dp)`) so large currency values never wrap or truncate awkwardly on small phone screens or dense tablet columns.
      - Fallback default unit string to `"Pcs"` when product unit is blank or empty.
    - Comprehensive UI/UX Touch Target Ergonomics (V67):
      - Customer CRM table action buttons enlarged to 38dp touch targets within widened 120dp action column.
      - Mobile customer card action buttons upgraded to 40dp touch targets.
      - Loyalty bottom sheet call and dismiss actions upgraded to 42dp touch targets.
      - Supplier due tabs and customer dossier note actions upgraded to min 44dp height.
    - Deep Bangla Localization:
      - Localized customer CRM table column headers (Customer, Tag, Spend / Due, Orders, Actions).
      - Localized merged customer preview stats (Combined Orders, Combined Spend, Combined Dues).
      - Localized customer bulk tag dialog and tag count selection strings.
      - Localized Add/Edit Customer form field labels (Phone, Name, Address, Credit Limit, Tag Segment, Notes).
      - Localized Customer Dossier payment method labels, customer/phone ledger details, and Share/Print receipt action.
      - Localized staff account PIN reset placeholder in Manage Users.
    - Database & Query Performance Optimization:
      - Added Room `@Query("SELECT * FROM sales WHERE customerPhone = :phone ORDER BY timestamp DESC")` in `SaleDao` to fetch customer-specific sales directly by indexed phone number, replacing full in-memory table filtering.
    - Unit tests: 48/48 tests passing (`./gradlew testDebugUnitTest`).

- **Version 2.6.68-V15-09-2026 (Build V71  -  Audit Round 19 Complete Remediation, WCAG AA Accessibility, Single App-Language Engine & R8 Minification)**:
  - **Financial Math & Partial Store Credit Integrity (R19-1, M-1/R-4, M-5/R-5)**:
    - Fixed partial store credit + Cash two-step flow (`PaymentMath.calculatePaymentBreakdown`): cashier underpayment rejected, change computed strictly on cash tendered without inflating receipt snapshot.
    - Due confirmation and credit limits evaluated directly from authoritative split payment due rows.
    - Card over-tender rejected, and electronic + cash split accurately calculates cash change.
    - Receipt snapshot amount paid matches reprint and invoice detail records.
  - **Database Backup, Restore & Migrations (T-1, T-3, N-6, X-4b)**:
    - Room migrations 33→34, 34→35, 35→36, 36→37, and 37→38 verified with real data suites.
    - Full database backup merge across shared history, returns, voids, and mixed-case barcode handling without corrupting dues, loyalty points, or store credit.
    - Migration 37→38 cleaning legacy `"VOUCHER"` phone fallback and supporting nullable customer phone in ledger.
    - Suppressed voided-sale inventory adjustment inflation during backup merge.
  - **Vouchers, Loyalty & Wallets (T-4, N-4, N-7, M-10, M-7, X-8)**:
    - Vouchers across multiple sales, walk-in sales, and expired states correctly restored during void/return.
    - Legacy pre-v37 voucher attribution preserved during sale voids.
    - Registered customer cash returns on voucher-paid sales credited accurately to customer digital wallet.
    - Loyalty points conversion to cash value evaluated with user-configured conversion rate; voucher labels localized.
    - Loyalty tier recomputation with user-configured tier thresholds.
  - **Catalog, Pricing & Supplier Dues (M-14, R-6, B-7, X-4a)**:
    - Owner account permitted to clear customer credit limit to 0; upsert due preserves existing limits.
    - Direct primary key index lookup on barcode (`barcode = :barcode OR barcode = LOWER(:barcode)`) eliminating full-table scans.
    - Android-safe Excel export with proper stream flushing and memory cleanup.
    - `isTaxInclusive` backfill for legacy sales; Excel exports and refund profit calculations accurately strip tax.
    - Supplier payment invoice pairing and note ledger linkage.
  - **UI/UX Ergonomics, Contrast & Theme Tokens (D-5, D-6, U-1, U-2, U-3, U-4)**:
    - WCAG AA contrast compliance (>= 4.5:1) across light and dark theme tokens (primary/onPrimary 5.33:1, error/onError 4.77:1, tertiary/onTertiary 7.70:1, dark onSurfaceVariant 5.06:1, secondaryContainer 4.75:1, surface 5.08:1) and light Checkout button.
    - Confirmation dialogs for held cart deletion, customer note deletion, and reminder log deletion.
    - Ergonomic touch targets: all IconButtons enlarged to >= 48dp across DuesScreen, AdvancedPosFeatures, ReturnDialog, ScannerScreen, and CustomerDossierSheet.
    - Dark mode white islands eliminated across date/time pickers, nudges, ReceiptSummaryDialog ribbon, and scanner badges.
  - **Single App-Language Architecture & Full Localization (D-1/L-2, L-1)**:
    - Centralized `LocalAppLanguage` CompositionLocal and `@Volatile var currentAppLanguage = "en"` with `setAppLanguage(lang)`.
    - `formatTaka` and `formatMoney` default to `isAppBangla()`, dynamically rendering Bengali numerals in UI while keeping Latin digits for printers, SMS, and exports (`isBangla = false`).
    - `DateFormatters.getAppLocale()` provides Bengali dates in UI while preserving `Locale.US` for file exports.
    - Complete Bengali localization for ReturnDialog, LockScreen PIN setup, Dues Khata form, RegisterCloseScreen, interim mid-shift X-report, closing daily Z-report, and repository exception messages in error toasts.
  - **R8 Minification & Resource Shrinking Deliverable**:
    - `Techyfi-POS-Final-V71.apk` (37.9 MB, versionCode 220944, versionName "2.6.68-V15-09-2026")
    - Full R8 tree shaking, dead code elimination, and resource shrinking verified with `proguard-rules.pro` keeping POI, XMLBeans, and MLKit models.
    - Unit tests: 88/88 passing (`testDebugUnitTest`).
    - Connected tests: 104/104 passing on `emulator-5554` (`connectedDebugAndroidTest`).

- **Version 2.6.69-V10-10-2026 (Build Techyfi-POS-Final-V10-10-2026.apk, versionCode 220945 - Dual Printer Architecture: Direct Bluetooth ESC/POS Receipts, TSPL Labels & Android Print Service, Unified Settings & R8 Minification)**:
  - **Dual Printer Selection & Routing Engine (`BillActions.kt`, `InvoiceSettings.kt`)**:
    - Support for both direct Bluetooth printers (no print service needed) and Android print service apps (e.g. Rongta RP328-BU print service) configured independently for receipts and labels.
    - Added fields in `InvoiceSettingsData`: `receiptPrintMode` (AUTO/BLUETOOTH/SYSTEM), `labelPrintMode` (AUTO/BLUETOOTH/SYSTEM), `labelPrinterAddress`, `labelGapMm`, `labelDarkness`.
    - Backward compatibility helper `resolvedReceiptPrinterAddress()` falling back to `cashDrawerPrinterAddress` if `directBluetoothPrinterAddress` is unset.
    - Pure routing function `decidePrintRouting(mode, address, directResult)` returning `DIRECT_OK`, `SHOW_ERROR`, `SYSTEM_DIALOG`, or `SHOW_ERROR_THEN_SYSTEM_DIALOG`.
    - Actionable localized toast feedback on Bluetooth errors (Bluetooth off, permission missing, printer not paired, connection failed) replacing silent fallback.
    - Unified dispatch entry points `printReceiptImage()` and `printLabelImages()` routing sales receipts, store credit vouchers, Settings test prints, X/Z reports, Dues repayment receipts, and barcode/shelf labels.
  - **Resilient Bluetooth Transport (`BluetoothPrinterTransport.kt`)**:
    - `sendToBluetoothPrinter`: Android 12+ `BLUETOOTH_CONNECT` permission handling, adapter enabled check, bonded devices lookup, discovery cancellation.
    - Standard RFCOMM SPP socket (`00001101-0000-1000-8000-00805F9B34FB`) with automatic fallback to insecure RFCOMM socket for printers requiring insecure connections.
    - Payload transmission in 512-byte chunks with 15ms delays and 300ms post-flush drain before socket close.
    - Executes on `Dispatchers.IO` returning strongly typed `DirectPrintResult`.
  - **TSPL Label Driver (`TsplLabelPrinterDriver.kt`)**:
    - Direct Bluetooth label printing for Deli E886BW and standard TSPL printers.
    - Generates TSPL ASCII commands (`SIZE`, `GAP`, `DIRECTION 1`, `DENSITY`, `CLS`, `BITMAP`, `PRINT 1,<copies>`).
    - 203 DPI scaling (`dots = round(mm * 8)`).
    - Pure function `tsplBitmapBytes()` with bit 0 = black, bit 1 = white and polarity inversion constant (`TSPL_INVERT_BITS = false`).
  - **Unified Settings UI (`SettingsScreen.kt`)**:
    - Consolidated "Printers" settings section with dedicated Receipt Printer and Label Printer cards.
    - Instant persistence on all changes via `settingsViewModel.saveInvoiceSettingsData(updated)` without requiring a manual Save button.
    - Cash drawer kick picker updated with a "Same as receipt printer" option, selected by default when blank.
    - `BluetoothPrinterPickerDialog` displays clear guidance when Bluetooth is disabled.
    - Fully bilingual in English (`res/values/strings.xml`) and Bengali (`res/values-bn/strings.xml`) with zero em dashes.
  - **Receipt Print Darkness & Software Luminance Thresholding**:
    - Added `receiptDarkness: Int = 8` to `InvoiceSettingsData` with backward-compatible JSON save/load.
    - Pure function `receiptLuminanceThreshold(darkness: Int): Int = 110 + darkness.coerceIn(0, 15) * 8` (0 -> 110 lightest, 8 -> 174 default, 15 -> 230 darkest) avoiding vendor-incompatible hardware density commands.
    - Receipt darkness slider (0-15, steps = 14) with "Lighter" and "Darker" captions, or system print service note when in SYSTEM mode.
    - Local Compose state for drag interaction, saving to settings only on `onValueChangeFinished` for both receipt and label sliders.
  - **Printer Concurrency & Resource Leak Hardening**:
    - Thread safety: Wrapped all `printBillJpeg` and `printPdfDocument` invocations in `withContext(Dispatchers.Main)` inside `printReceiptImage` and `printLabelImages`.
    - Rasterization offload: Wrapped bitmap scaling, luminance conversion, and ESC/POS payload generation inside `withContext(Dispatchers.Default)` in `EscPosPrinterDriver.printReceiptDirect`.
    - Socket leak prevention: Safely closed the initial secure Bluetooth socket upon `connect()` failure before creating and connecting the insecure fallback socket in `BluetoothPrinterTransport.kt`.
  - **Printer Conflict Prevention & Garbage Output Guard**:
    - Pure helper `isSamePrinter(a: String, b: String): Boolean`: trims MAC addresses, compares ignoring case, and safely returns false if either address is blank or empty.
    - Settings picker validation: prevents selecting the configured receipt printer as the label printer (or vice versa), displaying an explanatory AlertDialog with OK confirmation.
    - Warning indicator: renders a persistent red error caption beneath the Label Printer device selector when both roles are configured to the same Bluetooth device.
    - Direct print dispatch guard: in `BillActions.printLabelImages()`, intercepts `BLUETOOTH` and `AUTO` dispatch if `isSamePrinter(settings.labelPrinterAddress, settings.resolvedReceiptPrinterAddress())`, showing an informative Toast on `Dispatchers.Main` ("Label printer is set to the receipt printer. Change it in Settings > Printers.") and returning `false`, eliminating TSPL command garbage output on ESC/POS receipt printers while preserving Android Print Service `SYSTEM` mode.
  - **Sunmi V2s & Budget ESC/POS Printer Initialization & Band Sizing Fix**:
    - Post-connect settle delay: Introduced `CONNECT_SETTLE_MS = 250L` in `BluetoothPrinterTransport.kt` after RFCOMM socket connect before dispatching initial bytes, preventing printers from dropping command headers during startup.
    - Segmented payload transport: Added `sendToBluetoothPrinter` overload supporting `List<ByteArray>` with configurable inter-segment delays, maintaining 512-byte chunking and 15ms delays while enabling distinct pause intervals between command phases.
    - 64-row band decomposition: Reduced ESC/POS raster `maxBandHeight` from 256 to 64 rows, guaranteeing `yH = 0` across all band headers and limiting packet loss impact. Refactored `bitmapToEscPosBands` returning a list of discrete bands with `bitmapToEscPosRaster` as a backward-compatible concatenating wrapper.
    - Sequenced receipt dispatch: Dispatches `ESC @` (100ms settle delay), `ESC a 1` (20ms delay), individual raster bands (20ms delay between bands), and terminal feed/cut/kick (0ms delay), completely eliminating garbage text at receipt head and preserving logo printing on Sunmi V2s built-in printers.
  - **R8 Minification & Deliverable**:
    - `Techyfi-POS-Final-V10-10-2026.apk` (36 MB, versionCode 220946, versionName "2.6.70-V10-10-2026") - R8 minified release build with dead-code stripping and resource optimization.
    - Unit tests: 108/108 passing (`testDebugUnitTest`).
  - **Round 20 Master Audit & Polish (v2.6.71-V11-10-2026)**:
    - **P0 Critical Crashes & Concurrency**:
      - Fixed `ConcurrentModificationException` in `ProductRepository` cache by swapping with `ConcurrentHashMap`.
      - Resolved `IllegalStateException` on duplicate notification channels by registering channels once in Application initialization.
      - Fixed Room transaction deadlocks during background auto-backup synchronization.
    - **P1 Financial Transactions & Register Ledgers**:
      - Fixed refund and return ledger rounding mismatch ensuring penny-perfect register reconciliation.
      - Resolved stock inwarding double-counting on quick re-order submissions.
      - Corrected credit sale invoice ledger balance calculations when store credit vouchers are partially redeemed.
    - **P2 Sync, Backup & Data Integrity**:
      - Localized backup package manager verification error codes, corruption alerts, and checksum mismatch notifications.
      - Prevented auto-backup worker silent failures by passing active locale configuration contexts to system notifications.
    - **P3 RBAC Security & Elevation Guardrails**:
      - Fixed phone cashier terminal lock / switch user accessibility.
      - Enforced supervisor elevation timeouts (5-minute hard limit) across cart clearance and discount overrides.
      - Intercepted manual barcode duplicate entry lockouts.
    - **P4 Input Sanitization & Error Handling**:
      - Sanitized monetary and quantity inputs against floating-point precision artifacts in `CartScreen` and `ScannerScreen`.
      - Guarded against SQL injection patterns and invalid character sets in barcode and customer search fields.
    - **P5 100% Bilingual Localization & String Parity**:
      - Achieved complete key-for-key parity between `values/strings.xml` and `values-bn/strings.xml` across all 2,901 keys.
      - Localized dues aging cards, reminder schedule presets, store credit vouchers, and customer audit error messages.
      - Preserved Latin numeric formatting for barcodes and scanner counters while ensuring thermal receipts and UI dialogs respect user language selection.
    - **P6 Material 3 Design Tokens, Contrast & Accessibility**:
      - Introduced `ExtendedColors` semantic tokens for success, warning, and info states.
      - Eliminated unstyled raw hex colors across dialogs and cards, ensuring WCAG AA contrast compliance in dark mode.
      - Ensured touch targets meet the minimum 48dp standard across all interactive buttons, chips, and steppers.
    - **R8 Minification & Deliverable**:
      - `Techyfi-POS-Final-V12-10-2026.apk` (versionCode 220949, versionName "2.6.73-V12-10-2026") - R8 minified release build with comprehensive label copies controls across single-product (ProductCodeImageDialog) and batch printing (BatchProductCodePrintDialog), direct Bluetooth TSPL print dispatch, multi-page PrintDocumentAdapter layout support, and Settings test label feedback.
      - Unit tests: 108/108 passing (`testDebugUnitTest`).

### Batch Print Feature Completeness, Custom Product Picker & Preset Copies (Version 2.6.74-V12-10-2026, Build V12-10-2026)
- **3-Way Scope & Custom Product Selection**:
  - Upgraded Batch Print scope from binary (All / In-Stock) to a 3-way segmented control: `All Products`, `In-Stock Only`, and `Custom Selection`.
  - Added dedicated `BatchProductPickerSheet` modal with instant search (name or barcode), quick action assist chips (`Select All`, `Clear Selection`, `In-Stock Only`), interactive product checkboxes with stock pills and price indicators, and a live selection counter.
  - Pre-populates custom selection automatically when launching batch print with products already selected in `ProductsScreen`.
- **Complete Parity with Single-Product Code Image Settings**:
  - Code Type toggle: seamlessly switch between Barcode and QR Code generation.
  - Store Name: toggleable switch plus editable store name text field, persisting to `product_code_image_settings` SharedPreferences.
  - Date Tracking: toggleable switch with Manufacturing vs Packaging type chips, Mfg/Pkg date input, and Expiry date input.
  - Label Dimensions: dropdown supporting all standard sizes plus `Custom Size` with Width (mm) and Height (mm) inputs.
  - Expandable Typography & Graphic Sizing: collapsible section with fine-grained sliders for product name, price, code number, store name, date tracking text scale, and barcode graphic height.
  - Live Sample Label Preview: real-time bitmap preview dynamically rendering the candidate label with all active styles and custom sizes.
- **Scrollable Preset Copies**:
  - Transformed preset copies chip rows in both `BatchProductCodePrintDialog` and `ProductCodeImageDialog` into smooth horizontally scrollable rows (`Modifier.horizontalScroll(rememberScrollState())`).
  - Expanded quick presets to `1, 2, 3, 5, 10, 20, 50, 100` eliminating screen edge clipping and ensuring copies after 10 are readily accessible on all devices.
- **Bilingual String Parity & Zero Em Dashes**:
  - Full 100% key parity (2,925 keys) across `values/strings.xml` and `values-bn/strings.xml`.
  - Strictly zero em dash characters throughout the repository.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V12-10-2026.apk` (versionCode 220950, versionName "2.6.74-V12-10-2026"), R8 minified, passing 108/108 unit tests.

### Layout Truncation Remediation & Dialog Formatting Polish (Version 2.6.75-V12-10-2026, Build V12-10-2026)
- **Zero-Truncation Batch Print Scope Segmented Buttons**:
  - Restructured the 3-button segmented scope row in `BatchProductCodePrintDialog` into a 2-line vertical arrangement: title label (`All` / `In-Stock` / `Custom`) on line 1, and count badge (`(621)` / `(548)` / `(3)`) on line 2.
  - Eliminated single-line string truncation (`All Products (6...`, `In-Stock Only (...`) across both English and Bengali locales.
  - Optimized dialog container spacing (16dp padding, 10dp vertical item spacing) allowing the dialog content to display cleanly without awkward clipping on mobile screens.
- **Top App Bar Title & Action Button Ergonomics**:
  - Resolved `Dues & ...` top app bar truncation on phones by dynamically presenting `Dues` (`dues_list` / `বাকি`) on compact screens while retaining `Dues & Credit` on tablets.
  - Optimized action button touch footprint to 40dp (from 48dp) across the 4 top bar action icons (Khata, Reminders, Excel, Add Due), liberating 32dp of horizontal clearance.
  - Streamlined `PosTopAppBarTitle` squircle badge container to 34dp (from 40dp) and 16.5sp font size on compact screens, ensuring clean presentation for customer names and section headers.
- **Bilingual String Parity & Zero Em Dashes**:
  - Full 100% key parity (2,928 keys) between `values/strings.xml` and `values-bn/strings.xml`.
  - Strictly zero em dash characters throughout the repository.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V12-10-2026.apk` (versionCode 220951, versionName "2.6.75-V12-10-2026"), R8 minified, passing 108/108 unit tests.

### Comprehensive Printing System Hardening, Thermal Z/X Scaling & Bluetooth Batch Optimization (Version 2.6.76-V12-10-2026, Build V12-10-2026)
- **Thermal Receipt Scaling & Store Branding on Z and X Reports**:
  - Derived canvas width (`reportCanvasWidth`: 384 dots for 58mm vs 576 dots for 80mm) and PDF page width (`reportPdfPageWidthPoints`: 58mm vs 80mm points) dynamically in `generateZReportJpeg`, `generateXReportJpeg`, `generateZReportPdf`, and `generateXReportPdf`.
  - Replaced hard-coded store brand header with `reportStoreName` (`storeName.ifBlank { "Techyfi POS" }`) and added configurable powered-by footer (`reportPoweredByText` honoring `settings.showPoweredBy` and `settings.poweredByText`).
- **Printer Feedback Hardening & Test Receipt Busy Indicator**:
  - `printBillJpeg` returns `Boolean` confirming print job submission, with asynchronous bitmap decoding and safe recycling in `PrintDocumentAdapter`.
  - Added user feedback toasts across all callers (`ReceiptSummaryDialog`, `DuesScreen`, `RegisterCloseScreen`, `StoreCreditVoucherSlipDialog`).
  - Added `isTestingReceipt` loading state and circular progress indicator to Settings test receipt button, matching test label behavior.
- **Single Bluetooth Session Batch Label Printing**:
  - Added `LabelJob`, `BatchLabelPrintResult`, and `printLabelsBatch` in `TsplLabelPrinterDriver` transmitting multi-product label batches in a single RFCOMM connection session with `onSegmentSent` callback.
  - Eliminated repetitive Bluetooth reconnect overhead across `printLabelImages` and `ProductCodePrintDialogs`.
- **Duplicate Print Prevention in Auto Mode**:
  - Updated `decidePrintRouting` with `partialSuccessCount` tracking. If at least one label was dispatched successfully before connection failure, suppresses automatic PDF spooler fallback to prevent duplicate printing.
- **Bluetooth Transport Robustness & RFCOMM Retries**:
  - Added automatic connect retries (secure RFCOMM -> insecure RFCOMM fallback -> 500ms delay -> second attempt) before declaring connection failure in `BluetoothPrinterTransport`.
  - Enhanced permission error handling to request `BLUETOOTH_CONNECT` runtime permission via `ActivityCompat.requestPermissions`, only deep-linking to app settings on permanent denial.
- **Printer Picker Ergonomics & Configuration Safety**:
  - Resolved bonded Bluetooth device names (`formatBluetoothDeviceDisplay`) displaying `name (address)` across picker dialogs and Settings printer cards.
  - Added "None / not set" row to printer picker dialog allowing users to clear receipt/label printer selection.
  - Case-insensitive address comparison (`equals(..., ignoreCase = true)`).
  - Conflicting receipt printer disabled in label picker with clear warning note.
- **TSPL Label Quality, Aspect-Ratio Scaling & Dynamic Test Labels**:
  - Pure `labelLuminanceThreshold(darkness)` function mapping darkness slider (0-100) to luminance cutoff (110-230).
  - Aspect-ratio preserving scaling with centered white letterboxing in `buildTsplPayload` preventing barcode stretching.
  - Settings test label dynamically rendered using configured label dimensions.
- **Legacy Settings Migration**:
  - `InvoiceSettingsData.fromJson` seamlessly migrates legacy `directBluetoothPrintEnabled = true` to `receiptPrintMode = "BLUETOOTH"` when `receiptPrintMode` is absent, preserving JSON keys for backward backup compatibility.
- **Public Storage vs App-Private Fallback Clarification**:
  - `FileSaveResult` sealed hierarchy (`PublicSaved`, `PrivateFallback`, `Failed`) returned by `saveBillJpegToDownloads` and `saveBillPdfToDownloads`.
  - Informs users with exact destination path when public MediaStore save falls back to app-private storage.
- **Housekeeping, Cache Auto-Cleanup & 500-Label Batch Cap**:
  - Added `cleanOldPrintCacheFiles` pruning cache folders (`bills`, `reports`, `receipts`, `product_codes`, loose PDFs/JPEGs) older than 7 days, executed automatically at app launch in `TechyfiPOSApplication` and after printing.
  - Localized hardcoded strings (`"Invoice JPEG"`, `"Voucher Slip"`, `"Due Reminder"`, `"Customer phone"`, `"Print destination is unavailable"`, `"Could not decode receipt image file"`, job names) to both English and Bengali with strictly zero em dashes.
  - Added inline note in summary badge and user warning toast in `ProductCodePrintDialogs` when batch label count exceeds the 500-label cap.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V12-10-2026.apk` (versionCode 220952, versionName "2.6.76-V12-10-2026"), R8 minified, passing 108/108 unit tests.

### Receipt Auto-Cut Mode & USB Thermal/TSPL Printer Support (Version 2.6.77-V13-10-2026, Build V13-10-2026)
- **Receipt Paper Auto-Cut & Feed Line Controls**:
  - Added `ReceiptAutoCutMode` enum (`NONE`, `PARTIAL`, `FULL`) with standard ESC/POS GS V 0 / GS V 1 byte commands in `EscPosPrinterDriver.kt`.
  - Added configurable `receiptAutoCutMode` and `receiptFeedLines` (0-10, default 4) to `InvoiceSettingsData`.
  - Preserved legacy backup compatibility: `InvoiceSettingsData.fromJson` safely deserializes older backups and defaults gracefully when cut mode or feed lines are missing.
  - Added auto-cut mode selector, feed lines slider, and instant "Test Receipt Cut" button in Settings printer management.
  - All receipt print callers (`BillPdfGenerator.printBillJpeg`, summary dialogs, reports, and vouchers) append configurable feed lines and cut command to native ESC/POS print jobs.
- **Android USB Host Thermal & TSPL Printer Support**:
  - Added `PrinterTarget` domain model supporting both `Bluetooth(macAddress)` and `Usb(vendorId, productId, serialNumber, portPath)` targets with unified display names and serialization format (`usb:VID:PID[:SERIAL]`).
  - Added `UsbPrinterTransport.kt` implementing robust USB printer communication via `UsbManager`:
    - Automatic interface and bulk endpoint discovery (supports interface 0 or fallback printer class 7).
    - Connection error recovery and safe device connection lifecycle management.
    - Chunked bulk transfer throttling (`transferChunkSize = 16384` bytes) with timeout protection.
    - Android USB runtime permission dispatch via `PendingIntent` and broadcast receiver with persistent permission checking.
  - Unified printer picker dialogs for both ESC/POS receipt printers and TSPL label printers, showing both paired Bluetooth devices and plugged USB printers with device vendor/product metadata and connection icons.
  - Updated `AndroidManifest.xml` with `android.hardware.usb.host` feature (optional) and USB permission intent filters.
  - Settings printer status cards display active USB connection details, device vendor/product identifiers, and quick disconnect/test actions.
  - Preserves existing Bluetooth printer configurations and backup compatibility transparently.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V13-10-2026.apk` (versionCode 220953, versionName "2.6.77-V13-10-2026"), R8 minified, passing all unit tests.

### Audit Round 21 Comprehensive Hardening & Security Governance (Version 2.6.78-V13-10-2026, Build V13-10-2026)
- **Financial Integrity & Mathematical Parity**:
  - Extracted centralized pure financial calculation logic into `PaymentMath.kt` with strictly unified Round-Half-Up banker calculations.
  - Eliminated profit calculation discrepancies between cash/credit sales and ensured consistent rounding for unit prices, taxes, and margins.
  - Fixed inventory and sales report profit parity under diverse discount and tax regimes.
  - Hardened numeric sanitization in `AppUtils.sanitizeNumericInput` to cleanly drop thousands separators (commas) while strictly preserving decimal points.
- **Transaction Safety & Mutability Safeguards**:
  - Enforced `NonCancellable` coroutine scope boundaries around sales completion routines in `CartScreen` to prevent orphaned sales records or partial database writes on accidental dismissals or process interruptions.
  - Implemented supplier invoice/payment transaction voiding with full atomic rollback, ledger reconciliation, and localized error feedback.
  - Bound customer due statements with accurate initial balance seeding so that running balances match live receivables down to the paisa.
  - Aligned Material 3 DatePicker UTC-midnight timestamps with local midnight across all Khata, Dues, and Report filter ranges.
- **RBAC & Security Governance**:
  - Enforced strict single-owner account architecture in `DataRepository.updateStaff`, hiding the Owner role from `AddStaffDialog` and preventing elevation of staff to Owner.
  - Implemented device-credential PIN reset tracking: resets via Android lock screen now trigger a persistent unacknowledged flag and display an inescapable security warning dialog on the owner's next login until explicitly acknowledged.
  - Added configurable idle screen lock (Off, 5m, 15m, 30m, 60m) in System Settings, serialized in `InvoiceSettingsData` and dynamically monitored by `AuthViewModel`.
- **UI/UX Polish & Cold Start Resilience**:
  - Eliminated cold-start white flash by provisioning dark `#070709` window background in `values/themes.xml` and moving `windowLightNavigationBar` into `values-v27/themes.xml` for minSdk 24 compatibility.
  - Added `ForceDarkSystemBars` Compose component enforcing dark system bars and light icons on Splash and Lock screens.
  - Replaced low-contrast color tokens and raw `sp` sizes across `ReturnDialog`, `CartScreen`, `SuppliersScreen`, `ProductsScreen`, `DuesScreen`, `ExpensesScreen`, and `ManageUsersScreen` to meet WCAG AA standards.
  - Reconciled 100% of bilingual string keys across English (`values/strings.xml`) and Bengali (`values-bn/strings.xml`) with zero em dashes.
- **Verification & Test Suite**:
  - Passing all 184 unit tests across `P0AuditFixesTest`, `P1AuditFixesTest`, `P2AuditFixesTest`, `P3AuditFixesTest`, and `OwnerDecisionsTest`, calling production code directly.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V13-10-2026.apk` (versionCode 220954, versionName "2.6.78-V13-10-2026"), R8 minified and resource-shrunk, ready as an update to the existing installed app.

### Receipt Auto-Cut Reliability Hardening & Adaptive Bluetooth Pacing (Version 2.6.79-V14-10-2026, Build V14-10-2026)
- **Deterministic Socket Drain via Status Query (`GS r 1`)**:
  - Diagnosed real receipt cut failures where closing the RFCOMM socket dropped queued tail bytes (line feeds and cut command) on large (60-120 KB) receipts while short 3.6 KB test receipts succeeded.
  - Implemented sequential paper sensor query `GS r 1` (`1D 72 01`) after the payload and polled `socket.inputStream.available()` up to a dynamic deadline (`bluetoothResponseTimeoutMs`, scaling 3 to 8 seconds).
  - Receiving a reply byte proves the printer consumed all preceding raster bands, feed lines, and cutter commands; allows 150 ms settle for blade retraction.
  - Falls back gracefully to payload-scaled drain delay (`bluetoothPostPrintDrainMs`) if the printer firmware lacks bidirectional status support.
- **Post-Raster Settling Delay & Cut Command Style UI**:
  - Added `POST_RASTER_DELAY_MS = 250L` after the final raster graphics band before dispatching `endSegment` (feed lines and cut command), giving thermal printheads time to finish burning and stepper motors time to settle before the cutter solenoid fires.
  - Added user-facing "Cut Command Style" segmented control in Settings (`SettingsScreen.kt`) with choices for `GS V` (Standard, `1D 56`), `ESC i / m` (Legacy, `1B 69` / `1B 6D`), and `GS V n` (Function B, `1D 56 42 n`).
  - Added bilingual strings in both English and Bengali with zero em dashes.
- **Settings Persistence & RBAC Security Governance**:
  - Gated printer cut settings edits behind `canEditSettings` (`SYSTEM_SHOP_SETTINGS` permission).
  - Unauthenticated edits or DataStore persistence failures now display an error toast (`settings_save_failed`) and roll back in-memory state to persisted `InvoiceSettingsData`, preventing desync between test prints and production receipt generation.
  - Added unit test coverage for `InvoiceSettingsData` JSON round-trip serialization and fallback sanitization.
- **Adaptive Bluetooth Chunk Pacing**:
  - Added pure function `bluetoothChunkDelayMs` dynamically scaling chunk delay from 15 ms (short cut test jobs <= 8 KB, ~34 KB/s) up to 35 ms (full receipt jobs ~60-120 KB, ~14.6 KB/s).
  - Matches 115200 baud UART reception limits of thermal printer microcontrollers, preventing buffer overflow drops at the tail of long print jobs.
- **Diagnostics & Testing Infrastructure**:
  - Added comprehensive tag-based logging (`BtPrinterTransport`, `EscPosDriver`, `BillActions`) tracing route, target, cut mode, cut style, feed lines, band counts, total payload bytes, chunk delays, and end segment hex.
  - Added "Test Cut (Long Receipt)" button in Settings generating a 1200 px test receipt with diagnostic rulers to simulate full receipt payload conditions.
  - Pure logic unit tests in `PrinterRoutingAndDriverTest.kt` passing 100%.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V14-10-2026.apk` (versionCode 220955, versionName "2.6.79-V14-10-2026"), R8 minified and resource-shrunk, prepared as an in-place update for the existing installed app.

### Audit Round 22 Comprehensive Platform Hardening & Multi-Owner Architecture (Version 2.6.80-V15-10-2026, Build V15-10-2026)
- **Multi-Owner Account Architecture & RBAC Security (R22-07, R22-22, R22-29, R22-30, R22-31)**:
  - Enabled full Multi-Owner account support: existing Owners can create multiple Owner accounts, promote staff members to Owner, and demote extra Owner accounts to Manager or Cashier.
  - Guarded against orphaned systems by strictly preventing the deletion, deactivation, or demotion of the last remaining active Owner account.
  - Gated Shop Settings and idle lock configuration with `canEditSettings` permission checks and unauthorized access toasts.
  - Resolved manager delegated user edit regression: editing staff accounts with custom permissions or self-editing now properly sends null for unchanged permissions instead of triggering unauthorized permission errors.
- **Supervisor Authorization, PIN Lockout & Idle Auto-Lock Integrity (R22-01, R22-02, R22-03, R22-04, R22-09, R22-19, R22-20)**:
  - Cashiers locked out by supervisor auth failures cannot bypass lockout using their own PIN; lockout state survives user login/logout.
  - Discount elevation approvals compute and validate a cryptographic cart hash (items, quantities, and price mode); any cart alteration immediately invalidates approval.
  - Elevated permissions strictly expire after 5 minutes or upon checkout completion.
  - Unified background auto-lock with `idleLockDurationMinutes`: minimizing the app locks upon return only if the configured idle period (5m, 15m, 30m, 60m) has elapsed; disabled when set to Off (0m).
  - Added shared `Modifier.trackUserActivity()` detecting touch input on dialog and bottom-sheet windows to prevent premature idle lockouts during extensive modal form entry.
  - Normalized non-standard restored timeout values from legacy backups to ensure an option button is always selected in Settings.
  - Fixed status-bar theming across Splash and Lock screens to prevent white-flash crossfades.
- **Thermal & TSPL Printer Subsystem Hardening (R22-08, R22-10, R22-11, R22-12, R22-13, R22-14, R22-15)**:
  - Fixed batch label printing ignoring USB label printers by routing jobs through `resolvedLabelPrinterTarget()` and `isSamePrinterTarget` verification.
  - Resolved label and receipt conflict detection to properly inspect both Bluetooth and USB connection targets.
  - Handled USB permission grants, device disconnects, and status updates gracefully without app freezing.
  - Corrected label height calculation and TSPL bitmap rasterization to prevent clipping on 1-column, 2-column, and custom label formats.
- **Accounting, Tax, Margin & Profit Parity (R22-32, R22-33, R22-34, R22-35)**:
  - Fixed realized profit calculation for partial due sales to accurately reflect collected cash fractions.
  - Apportioned overall invoice discounts and taxes proportionally across line items to guarantee strict ledger and report parity.
  - Clamped bulk-adjusted catalog prices to maximum retail price (MRP) when configured.
  - Preserved leading zeros during barcode Excel spreadsheet import via Apache POI.
- **Data Safety, Recovery & Clean State Management (R22-16, R22-17, R22-18, R22-21, R22-25, R22-26)**:
  - Google Drive backup error reporting now unwraps underlying network/offline causes to display actionable localized error messages.
  - Hardened Factory Reset routine to cancel scheduled alarms before wiping database tables and prune orphaned image files and cached cart snapshots.
  - Filtered internal `_latest.json` markers and offloaded local backup directory scanning from the main thread.
  - Allowed inventory adjustment users without full catalog edit rights to update stock levels without modifying other product metadata.
  - Added Cash Reconciliation rows to Shift Z-Reports and CSV exports.
  - Clearly stamped reprinted receipts with localized reprint notices to avoid confusing historical records with new full payments.
- **Localization, Design Tokens & Ergonomics (R22-23, R22-24, R22-27, R22-28)**:
  - Centralized repository error bus to map raw SQLite and data exceptions into polished, human-friendly Bengali and English messages.
  - Replaced lingering hardcoded colors with Material 3 semantic theme tokens and verified touch targets exceed 48dp across all screens.
  - Standardized all strings across `strings.xml` and `values-bn/strings.xml` with strictly zero em dash characters.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V15-10-2026.apk` (versionCode 220956, versionName "2.6.80-V15-10-2026"), R8 minified and resource-shrunk, fully tested and ready as an in-place upgrade.

### Label Printing System Audit Hardening (Version 2.6.81-V16-10-2026, Build V16-10-2026)
- **High-Resolution Thermal Rasterization & DPI Support (LBL-01, LBL-12)**:
  - Bitmaps now render at exact physical printhead resolution (8 dots/mm at 203 DPI, 11.81 dots/mm at 300 DPI) using dynamic DPI scaling.
  - Added 203 DPI and 300 DPI printhead resolution toggle in Settings and InvoiceSettingsData, calculating byte widths and bitmap coordinates dynamically.
- **Data Integrity & Pricing Safeguards (LBL-02, LBL-05, LBL-07, LBL-10, LBL-11)**:
  - Guarded empty or blank product names with localized fallback `R.string.unnamed_product_label`.
  - Format helper `formatProductCodeLabelPriceString` safely falls back to selling price whenever MRP or wholesale price is unset (0.0), preventing bogus "৳0" prices on printed labels.
  - Copies inputs clamped to valid 1..500 range with supporting error texts.
  - Stock-to-copies helper `stockToPrintCopies` applies ceiling rounding and protects against fractional/zero stock.
  - Custom label dimensions enforce physical limits (width: 15..100 mm, height: 10..150 mm) with real-time error states.
- **Async Batch Printing Architecture & Memory Safety (LBL-04, LBL-06, LBL-15, LBL-19)**:
  - Background streaming via `printLabelPayloadsBatch` converts bitmaps to TSPL byte arrays on Dispatchers.IO and immediately recycles them.
  - Batch printing strictly enforces a 500-label safety cap and alerts users with localized toasts detailing which products were excluded.
  - Added per-product quantity stepper in `BatchProductPickerSheet` with 3-mode segmented button (Fixed Copies, Stock Quantity, Per Product).
  - Added partial print failure tracking with "Retry Remaining" and "Reprint Last Batch" recovery options.
- **Barcode Formats & Thermal Density Analysis (LBL-13, LBL-16)**:
  - Added checksum-validated EAN-13 and EAN-8 barcode generation alongside Code 128, QR Code, and Code 39.
  - Automatic recommendation badges suggest EAN-13/8 for valid 13-digit and 8-digit GTINs.
  - Thermal density check warns when barcode data contains too many modules for narrow label sizes (<= 25 mm).
- **Multi-Up Rolls, Templates & Hardware Calibration (LBL-14, LBL-17, LBL-18)**:
  - Added multi-up roll compositor `composeMultiUpRowBitmap` supporting 1, 2, or 3 labels across the roll with configurable column gap.
  - Added 5 industry preset templates (Standard Retail, Grocery & Expiry, Jewelry Mini Tag, Apparel & Dual Price, Warehouse & Shipping) plus custom extra text fields and logo toggle.
  - Added TSPL calibration settings: print speed (1.5 - 6.0 ips), direction/mirroring, reference offset (x, y dots), and paper type (GAP, CONTINUOUS, BLACK_MARK).
- **UX Polish, Token Alignment & Complete Test Coverage (LBL-20 to LBL-30)**:
  - Sticky live preview header in single and batch dialogs with sample product switching.
  - Removed duplicate action rows and unified collapsible advanced sections and date tracking defaults.
  - Synchronized 100% of strings across `values/strings.xml` and `values-bn/strings.xml` with zero em dashes.
  - Material 3 semantic theme tokens and >= 48dp touch targets applied across all label dialogs.
  - Added dedicated test suite `LabelPrintingAuditUnitTest.kt` (18 tests) verifying routing, fallbacks, 203/300 DPI calculations, copies clamping, batch caps, EAN checksums, and multi-up geometry.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V16-10-2026.apk` (versionCode 220957, versionName "2.6.81-V16-10-2026"), R8 minified and resource-shrunk, prepared as an in-place upgrade.

### UI/UX & Layout Formatting Hardening (Version 2.6.82-V16-10-2026, Build V16-10-2026)
- **TopAppBar Responsive Space Reclamation (`SuppliersScreen.kt`)**:
  - Reclaimed ~90dp of horizontal TopAppBar width on compact phone screens by rendering the `Active Hub` status pill conditionally on `isTablet`.
  - Guarantees the primary screen title "Suppliers & Purchases" renders fully without ellipsis clipping on mobile handsets.
- **Analytics Period Switch Layout Polish (`SalesReportScreen.kt`)**:
  - Eliminated duplicate emoji prefixes in historical period switch actions across English and Bengali resource files.
  - Applied compact touch-padded button sizing (`PaddingValues(horizontal = 8.dp, vertical = 6.dp)`) with `maxLines = 1` and `softWrap = false`, preventing multi-line text wrapping on all screen widths.
- **Denomination Counter Interaction & Formatting Ergonomics (`RegisterCloseScreen.kt`)**:
  - Replaced constrained horizontal row in `DenomCounterDialog` with a centered column structure consisting of helper text and a dedicated, rounded pill button.
  - Completely eliminated awkward mid-phrase line breaks ("directly" wrapping on a second line) while providing an intuitive, touch-friendly direct total input shortcut.
- **Cost Margin Estimation Segmented Controls Symmetry (`SettingsScreen.kt`)**:
  - Stripped default Material 3 checkmark icons from `SegmentedButton` to give full horizontal width to percentage labels.
  - Standardized all preset labels to clean percentage values ("50%", "70%", "85%", "100%"), eliminating text truncation and ensuring perfect visual symmetry across the 4 segment buttons.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V16-10-2026.apk` (versionCode 220958, versionName "2.6.82-V16-10-2026"), R8 minified and resource-shrunk, prepared as an in-place update for existing installations.

### UI/UX & Layout Formatting Hardening V2 (Version 2.6.83-V16-10-2026, Build V16-10-2026)
- **Floating Action Button Compact Footprint & Category Row Proportions (`ProductsScreen.kt`)**:
  - Replaced wide `ExtendedFloatingActionButton` on mobile handsets (`!isTablet`) with a standard 56dp circular `FloatingActionButton` (shape = CircleShape).
  - Eliminates FAB obstruction over the right-hand column's product card action buttons (`± Stock`, `Barcode`, `More Menu`) while keeping full accessibility and tactile elevation.
  - Balanced `ProductGridCard` meta row with proportional `fill = false` weights between barcode capsule (`1.2f`) and category text (`1.0f`), preventing premature category truncation (`বিউটি...`) on long barcodes.
- **Data Table Horizontal Panning Architecture (`CustomersScreen.kt`)**:
  - Implemented `Modifier.horizontalScroll(rememberScrollState())` on `CustomerDataTable` with a guaranteed `688.dp` minimum content width.
  - Replaced fragile fractional weights with dedicated, fixed column widths for Checkbox (48dp), Customer Name/Phone (170dp), Tag (100dp), Spend/Due (140dp), Orders/Points (90dp), and Action Shortcuts (140dp).
  - Eliminates vertical letter-splitting and line-wrapping artifacts on mobile displays (e.g. `Ret \n ail`, `016717663 \n 76`, `৳10,07 \n 9.75`).
- **Loyalty Member Card Ergonomics & FAB List Clearance (`LoyaltyScreen.kt`)**:
  - Relocated lifetime spent metrics (`৳2,340 spent`) to a dedicated third row within the member info column.
  - Completely prevents the 4-line vertical text squishing bug (`৳2,3 \n 40 \n spen \n t`) caused by cramming tier badges, phone numbers, and currency values into a narrow row.
  - Increased `LazyColumn` bottom content padding to `96.dp`, ensuring bottom member cards (e.g. "মাদ্রাসা") remain fully visible above the floating action button and system navigation bars.
- **Custom Cost Percentage Input Formatting (`SettingsScreen.kt`)**:
  - Replaced remote right-aligned `suffix = { Text("%") }` in `OutlinedTextField` with an inline `PercentVisualTransformation()`.
  - Positions the percentage symbol directly adjacent to typed values (`100%`) without an unnatural 300dp gap, matching the preset chips and report description text.
- **Dual Expense Action Button Alignment (`ExpensesScreen.kt`)**:
  - Removed duplicate emoji prefixes (`➕` and `💵`) that conflicted with pre-existing string resource prefixes (`+ Record Expense` and `+ Cash In Float`).
  - Added compact `PaddingValues(horizontal = 8.dp, vertical = 0.dp)` ensuring both action buttons fit comfortably on compact screens without ellipsis truncation (`+ Record Expe...`).
- **Release Deliverable**:
  - `Techyfi-POS-Final-V16-10-2026.apk` (versionCode 220959, versionName "2.6.83-V16-10-2026"), R8 minified and resource-shrunk, prepared as an in-place update for existing installations.

### Global Text Formatting, Localization & Numeral System Hardening (Version 2.6.84-Final-V1, Build Techyfi-POS-Final-V1.apk)
- **String Resource Specifier Parity & Crash Prevention**:
  - Escaped literal `%` characters to `%%` (e.g. `dues_full_amount_chip`: `100%% Full (%1$s)`) eliminating runtime `UnknownFormatConversionException: Conversion = 'F'` crashes.
  - Added `formatted="false"` or `%%` across all strings with phantom format specifiers in English and Bengali resources, dropping placeholder count mismatches between English and Bengali from 8 to 0.
  - Replaced archaic term `ঋণগ্রহীতা` with natural Bangladeshi retail phrasing `বকেয়াদার কাস্টমার` in `dues_total_outstanding_borrowers`.
- **Bengali Soft Keyboard Numeral Parsing (`০-৯`)**:
  - Kotlin's `Char.isDigit()` evaluates to `true` for Bengali numerals (`০-৯`), but JVM `toDoubleOrNull()`, `toIntOrNull()`, and `Long.parseLong()` strictly require ASCII `0-9`.
  - Added `toEnglishDigits()` input sanitization before digit filtering across all numerical inputs:
    - Sales History price range filters (`draftMinimumTotal`, `draftMaximumTotal`).
    - Products catalog price range filters (`minimumPrice`, `maximumPrice`).
    - Dues & Khata debt limits, repayment amounts, and dossier limit editors.
    - Settings custom cost presets and VAT rates.
    - Cash Register closing direct total, safe drop, and cash count fields.
    - Scanner manual stock adjustment inputs.
    - Product editor wholesale prices and reorder levels.
    - Supervisor/Owner elevation PIN inputs.
    - Barcode and product code label copy counts.
- **Date Input Sanitization & Expiry Parsing**:
  - Expanded `DateInputField` intake from `take(8)` to `take(10)` in `Product3DIcons.kt`, resolving the bug where 4-digit years (`2026`) truncated to `20` (interpreted as year 2020).
  - Sanitized raw date inputs in `AppUtils.parseExpiryDateToMillis` with `toEnglishDigits()`, allowing dates typed with Bengali keyboards to parse correctly into epoch milliseconds.
- **Thermal Receipts, Store Vouchers & Z-Reports Alignment (`BillPdfGenerator.kt`)**:
  - Re-engineered `drawMetaRow` in store credit voucher bitmap generator to draw label (`metaLeft`), colon (`colonX`), and value (`valueX`) at deterministic pixel X coordinates, eliminating colon misalignment caused by proportional Bengali typography.
  - Invariant business data preserved in ASCII digits: customer phone numbers, invoice IDs, and transaction barcodes remain in ASCII for dialability, WhatsApp links, and scanner search.
  - Standardized date formatting on Bengali receipts via `DateFormatters.formatDateTime(timestamp, Locale.forLanguageTag("bn"))`, eliminating hybrid English month strings.
  - Standardized transaction counts and units sold so English and Bengali digits never mix within the same string.
  - Updated commercial terminology: `নীট বিক্রয়` -> `প্রকৃত বিক্রয়` (Net Sales), `প্রদತ್ತ ছাড়` -> `মোট ছাড়` (Discounts Given).
- **Monospace Font Glyph Clipping Fix (`ReceiptSummaryDialog.kt`)**:
  - Dynamically configured fallback receipt typography to `FontFamily.Default` when receipt language is `"BN"`, falling back to `FontFamily.Monospace` only for English receipts, preventing broken conjuncts and font clipping on Android handsets.
- **Compose Text Overflow & Ellipsis Polish**:
  - Added `overflow = TextOverflow.Ellipsis` to single-line text composables across `ProductsScreen.kt` (tablet/phone rows, grid cards, price/unit badges) and `CartScreen.kt` (preset chips, held cart hold button, checkout CTA button, item unit prices).
  - Localized unit margin percentages and stock asset quantities in `StockCheckComponents.kt` using `toBanglaDigits()`.
- **Sales Report Executive Summary Responsive Metrics Grid (`SalesReportScreen.kt`)**:
  - Replaced cramped 4-column horizontal `Row(Arrangement.SpaceBetween)` with a responsive architecture:
    - Compact Mobile (< 600dp): 2x2 Grid of frosted translucent `ExecutiveMetricCell` tiles (`Color.Black.copy(alpha = 0.22f)` with subtle 1dp border).
    - Tablet (>= 600dp): Single row of 4 equally-weighted (`Modifier.weight(1f)`) tiles with `spacedBy(10.dp)`.
  - Completely eliminated the severe text collision and character wrapping bug where labels collided into `COMPLETED ORDERSUNITS SOLDDISCOUNTS GIVENORDER` and currency values wrapped characters onto a second line (`৳1,161.9 \n 9`).
  - Localized transaction counts, units sold, and margin percentages using `toBanglaDigits()` and `isAppBangla()`.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V1.apk` (versionCode 220960, versionName "2.6.84-Final-V1"), R8 minified and resource-shrunk, fully tested and prepared in project root directory as a direct in-place update.

### Release Final-V2: Dues Screen WhatsApp Button & "Collected This Month" Layout Overhaul (Version 2.6.85-Final-V2, Build Final-V2)
- **WhatsApp Action Button Truncation Fix (`DuesScreen.kt`)**:
  - Fixed horizontal button clipping in `CustomerDueRow` where "WhatsApp" truncated to "WhatsA" on mobile handsets.
  - Rebalanced row action weights: WhatsApp button given `weight(1.35f)` with `fontSize = 9.5.sp`, `softWrap = false`, and `maxLines = 1`; Call button reduced to `weight(0.75f)`; Remind button to `weight(0.90f)`; and Collect button at `weight(1.05f)`.
  - Replaced system emoji `💬` with photorealistic `Realistic3DWhatsAppIcon(size = 13.dp, animate = false)`, preserving tight horizontal bounding boxes.
  - Constrained 3-dots `IconButton` to `Modifier.size(36.dp)` with `spacedBy(4.dp)`, freeing 20dp+ of horizontal space for button content.
  - All buttons strictly maintain `height(48.dp)` complying with Material 3 touch target standards.
- **WhatsApp Bengali Numeral Sanitization & Resilient Fallback (`BillActions.kt`)**:
  - Resolved critical silent failure in `openWhatsAppReminder` and `openWhatsAppChat` where customer phone numbers containing Bengali numerals (`০-৯`) had all digits stripped by `Regex("[^0-9+]")`, failing with "No phone number available".
  - Sanitized phone input via `toEnglishDigits(phone)` before stripping non-digits across `openWhatsAppReminder`, `openWhatsAppChat`, `openPhoneDialer`, and `openPaymentReminder`.
  - Upgraded WhatsApp dispatching to a 4-tier resilient URI fallback sequence: `whatsapp://send` -> `https://api.whatsapp.com/send` -> `https://wa.me/` -> system text share sheet or clipboard.
  - Localized `onWhatsApp` dispatch reminder messages bilingually according to `isAppBangla()` and `formatTaka(..., isBangla)`.
- **"Collected This Month" Header Formatting & Collision Fix (`DuesScreen.kt`, `strings.xml`)**:
  - Updated string resource `dues_collected_this_month_label` from shouting all-caps `COLLECTED THIS MONTH` (20 uppercase characters) to title-case `Collected This Month` in `strings.xml`.
  - Assigned `Modifier.weight(1f).padding(end = 8.dp)` with `maxLines = 1, overflow = TextOverflow.Ellipsis` to the left `Column` in the credit recovery summary card header, preventing text like `Total Outstanding (1 Borrowers)` / `মোট বকেয়া (১ জন বকেয়াদার কাস্টমার)` from colliding into the right surface border or leaving orphan clipped text (`s)`).
  - Formatted the right `Surface` with `fontSize = 10.sp`, `softWrap = false`, and `maxLines = 1` for `Collected This Month`, with amount `+৳...` neatly right-aligned directly beneath it, eliminating awkward 2-line wrapped text (`COLLECTED THIS \n MONTH`).
- **Release Deliverable**:
  - `Techyfi-POS-Final-V2.apk` (versionCode 220961, versionName "2.6.85-Final-V2"), R8 minified and resource-shrunk, fully tested and prepared in project root directory as a direct in-place update.

### Release Final-V3: Business Health Layout Formatting & Customer Due Action Icons (Version 2.6.86-Final-V3, Build Final-V3)
- **Business Health & Cross-Reference Box Formatting Fix (`SalesReportScreen.kt`, `strings.xml`, `values-bn/strings.xml`)**:
  - Enforced uniform `Modifier.height(IntrinsicSize.Max)` on the container `Row` with each card set to `Modifier.weight(1f).fillMaxHeight()`, completely resolving the ragged, unequal card heights.
  - Aligned metric value baselines across all 3 cards by constraining titles to `minLines = 2, maxLines = 2, lineHeight = 13.sp`.
  - Replaced long embedded-chevron strings with compact single-line labels (`Expenses` / `খরচ` and `Vendors` / `সাপ্লায়ার`) coupled with non-wrapping `Icon(Icons.Default.ChevronRight, modifier = Modifier.size(12.dp))`, eliminating the awkward text wrapping where `>` broke onto its own line (`View Expenses \n >`).
  - Added an aligned status badge (`🟢 Profitable` / `🔴 Net Loss`) to the Net Operating Profit card footer, establishing perfect symmetry and balance across all three cards.
- **Relevant Action Button Icons for Collect, Call & Remind (`DuesScreen.kt`, `StockCheckComponents.kt`)**:
  - **Collect Action**: Redesigned `Realistic3DWalletCollectIcon` in `StockCheckComponents.kt` into a razor-sharp banknote icon with white/slate gradient body, emerald center currency seal, and security dots, replacing the previous unidentifiable white crescent.
  - **Remind Action**: Integrated official `Icons.AutoMirrored.Filled.Message` vector icon in indigo (`Color(0xFF4F46E5)`), replacing the indistinct purple dot with an unmistakable message/reminder glyph.
  - **Call Action**: Integrated official `Icons.Default.Phone` vector icon in emerald (`Color(0xFF059669)`), replacing the abstract green bullseye dot with an unmistakable phone handset glyph.
  - Updated both `CustomerDueRow` and `CustomerDetailHeader` in `DuesScreen.kt` for cohesive, professional UI standards across the entire dues workflow.
### Release Final-V4: Hourly Sales Peak & Activity Metric Boxes Formatting Fix (Version 2.6.87-Final-V4, Build Final-V4)
- **Hourly Sales 3-KPI Highlight Cards Formatting & Alignment Fix (`SalesReportScreen.kt`, `strings.xml`, `values-bn/strings.xml`)**:
  - **Eliminated Disoriented Baseline Offsets**: Fixed the issue where Tile 1 ("Peak Hour") was 1 line while Tile 2 ("Busiest Hour") and Tile 3 ("Active Hours") wrapped to 2 lines, pushing primary values (`8 PM`, `9 PM`, `20 Hours`) and subtitles out of horizontal alignment.
  - **Equal Height Constraints**: Added `Modifier.height(IntrinsicSize.Max)` to the parent `Row` and `Modifier.weight(1f).fillMaxHeight()` to all three `Surface` tiles, guaranteeing identical card heights and balanced borders across the entire 3-column deck.
  - **Fixed Vertically Disoriented Emojis**: Converted badge titles from uppercase (`BUSIEST HOUR`, `ACTIVE HOURS`) to clean Title Case (`Peak Hour`, `Busiest Hour`, `Active Hours`; Bangla: `পিক আওয়ার`, `ব্যস্ততম সময়`, `সক্রিয় সময়`). Tuned card padding to `horizontal = 9.dp, vertical = 11.dp` and badge typography to `fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, maxLines = 1`, ensuring the leading emojis (`🔥`, `⚡`, `⏱️`) sit crisply beside their titles on a single line instead of floating awkwardly in the vertical center of wrapped multi-line text.
  - **Baseline Aligned Metric Values & Spacing**: Standardized primary metric values (`14.sp`, `FontWeight.ExtraBold`, `maxLines = 1, softWrap = false`) and secondary metric values (`10.5.sp`, `FontWeight.Bold`, `maxLines = 1, softWrap = false`) with unified `Spacer(Modifier.height(5.dp))` and `Spacer(Modifier.height(2.dp))` vertical rhythm across all 3 cards.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V4.apk` (versionCode 220963, versionName "2.6.87-Final-V4"), R8 minified and resource-shrunk, fully tested and prepared in project root directory as a direct in-place update.

### Release Final-V5: Global Settings Search & Deep-Linking System (Version 2.6.88-Final-V5, Build Final-V5)
- **Global Settings Search Bar & Quick Filter Chips (`SettingsScreen.kt`, `strings.xml`, `values-bn/strings.xml`)**:
  - **Omni-Search Engine**: Added an integrated search header at the top of the Settings home screen with instant search capability across all 19 configuration areas, preferences, hardware setups, and danger zone utilities.
  - **Bilingual Keyword Indexing**: Indexed all settings with comprehensive English and natural Bangla retail vocabulary (e.g., searching for "bkash", "বিকাশ", "প্রিন্টার", "ভ্যাট", "পাসওয়ার্ড", "ড্রয়ার", "রশিদ", "ব্যাকআপ" instantly surfaces the exact matching setting tile).
  - **Category Filter Chips**: Fast horizontal scroll row with chips for `All`, `Printers` (`প্রিন্টার`), `Receipt` (`রশিদ`), `Security` (`নিরাপত্তা`), `Backup` (`ব্যাকআপ`), and `General` (`সাধারণ`) with active selection indicators and match counts.
  - **Live Setting Interactions & Deep-Linking**:
    - Direct toggles inline for Negative Stock / Stock Enforcement and Cash Drawer Kick.
    - Direct modal launchers for Receipt Printer Picker and Barcode Label Printer Picker.
    - Deep-links straight into nested subpages: Shop Identity, Shop Logo, QR Payment Links, Due Receipt Voucher Layout, Receipt Paper Layout, Tax & VAT Rules, Staff PIN Access Control, Google Drive Backup, and Database Migration.
  - **Empty-State Assist Deck**: Provides suggestions and popular presets (`Printers`, `Backup`, `VAT`, `Staff PIN`, `Logo`, `Cash Drawer`) when query returns no matches or when discovering settings.
- **Release Deliverable**:
### Release Final-V6: App-Wide Search Box Overhaul & Unified PosSearchBox System (Version 2.6.89-Final-V6, Build Final-V6)
- **Unified Standardized Search Component (`PosSearchBox.kt`)**:
  - Implemented `PosSearchBox` in `com.techyfiposmeds.ui.components` featuring:
    - **Zero Vertical Baseline Clipping**: Vertically-centered `BasicTextField` wrapped inside a compact 46dp `Surface`, eliminating Material 3's minimum 56dp height constraints that previously clipped text baselines in constrained text fields.
    - **Safe Single-Line Placeholder Truncation**: Enforced `maxLines = 1` and `TextOverflow.Ellipsis` for both English and Bangla placeholder texts, preventing multi-line text wrapping behind input content.
    - **Section-Calibrated Accent Borders**: Subtle `BorderStroke` transitioning dynamically to section themes (`PosSectionColors.Products`, `PosSectionColors.SalesHistory`, `PosSectionColors.Dues`, `PosSectionColors.Customers`, `PosSectionColors.Suppliers`, `PosSectionColors.Expenses`, `PosSectionColors.Loyalty`, `PosSectionColors.Settings`) upon focus or text entry.
    - **Animated Clear (`✕`) Button**: Accessible touch target with smooth enter/exit animations (`fadeIn + scaleIn` / `fadeOut + scaleOut`), resetting query and firing `onClear` callback.
    - **Keyboard & Action Management**: Integrated `KeyboardOptions(imeAction = ImeAction.Search)` with automatic keyboard hiding and focus clearance via `LocalSoftwareKeyboardController` and `LocalFocusManager`.
    - **Trailing Content Slot**: Flexible trailing slot supporting embedded secondary actions (such as the 3D hardware barcode scanner toggle button in `ProductsScreen`).
- **Complete App-Wide Search Box Standardization**:
  - **Products Screen (`ProductsScreen.kt`)**: Replaced ad-hoc search surface with `PosSearchBox(accentColor = PosSectionColors.Products)` while preserving the embedded 3D optical barcode laser toggle button inside its trailing slot.
  - **Sales History Screen (`SalesHistoryScreen.kt`)**: Replaced un-truncated search box with `PosSearchBox(accentColor = PosSectionColors.SalesHistory)`.
  - **Customer Dues Screen (`DuesScreen.kt`)**: Replaced both main dues search box and dues audit log search box (`OutlinedTextField` forced to 48dp) with `PosSearchBox(accentColor = PosSectionColors.Dues)`.
  - **Customers & CRM Screen (`CustomersScreen.kt`)**: Replaced `OutlinedTextField` with `PosSearchBox(accentColor = PosSectionColors.Customers)`.
  - **Suppliers & Inflow Screen (`SuppliersScreen.kt`)**: Replaced both main vendor directory search box and inward stock supplier picker search dialog with `PosSearchBox(accentColor = PosSectionColors.Suppliers)`.
  - **Expenses Screen (`ExpensesScreen.kt`)**: Replaced baseline-clipping `OutlinedTextField` (forced to 50dp) with `PosSearchBox(accentColor = PosSectionColors.Expenses)`.
  - **Customer Loyalty Screen (`LoyaltyScreen.kt`)**: Replaced clipped `OutlinedTextField` (forced to 50dp) with `PosSearchBox(accentColor = PosSectionColors.Loyalty)`.
  - **Stock Audit Dialogs (`StockAuditDialogs.kt`)**: Standardized both stock audit logs search bar and product picker search dialog with `PosSearchBox(accentColor = PosSectionColors.Products)`.
  - **Product Code Print Dialogs (`ProductCodePrintDialogs.kt`)**: Standardized batch print product search bar with `PosSearchBox(accentColor = PosSectionColors.Products)`.
  - **Settings Screen (`SettingsScreen.kt`)**: Standardized global settings search input with `PosSearchBox(accentColor = MaterialTheme.colorScheme.primary)`.
- **Bilingual & Typography Verification**:
  - Validated live on physical Xiaomi phone (`cb5fdfb5`) in both English and Bengali locales.
  - Verified complex Bengali vowel diacritics, conjuncts, ascenders, and descenders (`চন্দ্রবিন্দু`, `হ্রস্ব উ-কার`, `হ্রস্ব ই-কার`, `র-ফলা`, `যুক্তাক্ষর`) render with zero clipping across all search bars.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V6.apk` (versionCode 220965, versionName "2.6.89-Final-V6"), R8 minified and resource-shrunk, fully tested and prepared in project root directory as a direct in-place update.

### Release Final-V7: Dues Layout Harmonization & Complete Physical Device Live Verification (Version 2.6.90-Final-V7, Build Final-V7)
- **Harmonized Dues Filter Button & Date Period Chips (`DuesScreen.kt`)**:
  - Standardized Dues Filter button from 48dp `FilledTonalIconButton` to a 46dp `Surface` with 12dp squircle corners and dynamic red border, perfectly aligning with the 46dp `PosSearchBox`.
  - Upgraded Dues Date Period Chips (`All`, `Today`, `Week`, `Month`, `Custom 📅`) with horizontal scrolling, custom date range support, and unified 28dp pill heights with emerald selection theme.
  - Aligned debtors sort dropdown menu trigger button.
- **Physical Phone Verification & Interactive Testing (`cb5fdfb5`)**:
  - Full end-to-end verification of `Techyfi-POS-Final-V7.apk` on physical Xiaomi MI 8.
  - Verified PIN lock & unlock (`1234`).
  - Tested live text entry, focused accent border animation, single-line placeholder ellipsis, animated clear (`✕`) action, and keyboard dismissal across Products, Sales History, Dues, Expenses, Suppliers, Loyalty, and Settings.
  - Validated English and Bengali locale rendering (`বাংলা`) with complete vowel and conjunct diacritic clearance.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V7.apk` (versionCode 220966, versionName "2.6.90-Final-V7"), 37MB release build located in `/Users/shahidulhaque/App Dev/Techyfi POS/`.

### Release Final-V21-10-2026: Round 23 Platform Hardening, Multi-Owner Security & Owner Decisions (Version 2.6.91-Final-V21-10-2026, Build Final-V21-10-2026)
- **Role-Based Access Control, Discount Ceilings & Self-Edits (R23-03, R23-04)**:
  - **Manager Customizable Discount Ceiling (R23-03)**: Removed `SALES_HIGH_DISCOUNT` from `DEFAULT_MANAGER_PERMISSIONS`. Managers now operate under an owner-customizable discount ceiling (default 20.0%), requiring supervisor authorization if exceeded, rather than receiving an unconstrained 100% discount.
  - **Unified Checkout Limit Verification**: Unified discount limits across both UI (`CartScreen.kt`) and database repository (`DataRepository.kt`) via shared `AppPermissions.getEffectiveMaxDiscount()`.
  - **Self-Account Editing Resilience**: Fixed bug where editing an owner's own name or PIN threw "Users cannot change their own maximum discount"; dialogs now safely send `null` when discount limits remain unchanged.
  - **Staff Delegate Permission Boundaries (R23-04)**: Enforced strict subset intersection when non-owners create or update accounts. Non-owners cannot assign permissions they do not hold or assign higher roles.
- **Multi-Owner Security & Mutual Protection Architecture (R23-06 - Option B)**:
  - **Step-Up Co-Owner Authentication**: In multi-owner setups, one owner cannot silently take over, change the PIN of, demote, or deactivate another owner. Credential modification strictly requires the target owner's current PIN or device master biometric authentication.
  - **Co-Owner Mutual Audit Alerts**: Any administrative modification to another owner's account writes a persistent audit record to `owner_pin_reset_audit` (`"Modified by <Caller Name>"`), alerting the target owner upon their next login.
  - **Orphan & Direct Deletion Protection**: Direct one-tap deletion of owner accounts is disabled in user management; owners must be demoted with credential verification first. Deletion or deactivation of the last active owner remains strictly blocked.
- **Weighed Fractional Units & Sales Decimal Quantities (R23-16)**:
  - **Bilingual Weighed & Measured Units**: Extended `isFractionalUnit` in `AppUtils.kt` to cover both English and Bengali unit designations (`kg`, `g`, `gm`, `litre`, `ltr`, `ml`, `meter`, `কেজি`, `গ্রাম`, `লিটার`, `মিটার`, `মিলি`).
  - **Fractional Preservation When Decimal Toggle is Off**: When general decimal quantities are turned off, weighed and liquid items preserve exact fractional quantities (e.g., 0.250 kg, 1.5 L) without truncation or stock-check refusal, while discrete units (`pcs`, `box`, `pack`, `টি`) cleanly round/clamp to positive integers (>= 1).
  - **Settings Relocation**: Moved the Decimal Quantities configuration toggle from the Receipt Layout dialog into the core Shop & Selling Policy card in `SettingsScreen.kt`.
- **Security & PIN Brute-Force Hardening (R23-01, R23-02, R23-05, R23-07)**:
  - **Persistent Failure Counters (R23-01)**: Prevented PIN brute-forcing by ensuring `total_failures` does not clear when a different user logs in; failures decay gradually over a sliding window with escalating lockouts (30s, 2m, 5m, 15m).
  - **Permission Matrix Subset Enforcement (R23-02)**: Prevented non-owners from elevating permissions beyond their active set via the permissions matrix dialog; restricted system administrative permissions to owners only.
  - **PIN Oracle Mitigation (R23-05)**: Rate-limited PIN verification and sanitized conflict error messages to generic neutral responses to prevent username and PIN enumeration.
  - **Override Token Lifecycle (R23-07)**: Automated purge of discount and credit limit override tokens upon user switch or logout, preventing token hijacking across cashier sessions.
- **Subsystem & Ledger Reliability (R23-08 to R23-26)**:
  - **Scanner Supervisor Hold (R23-08)**: Fixed race condition where supervisor hold authorization on the scanner screen discarded callbacks.
  - **Z-Report Preview (R23-09)**: Enabled real-time preview of Register Close Z-Reports before shift closure confirmation.
  - **Excel Barcode Preservation (R23-10)**: Prevented Apache POI from altering text barcode values with leading zeroes or EAN-13 padding on import.
  - **Printer Watchdog & Batch Printing (R23-11, R23-12, R23-13, R23-21, R23-22)**: Fixed batch label dialog action buttons; dynamically computed Bluetooth timeout budgets for large batches (> 1.2 MB); keyed receipt cache on active bill hash to eliminate stale reprints; added tri-state print dialog status; and handled dual-USB identical VID:PID printer replug conflicts.
  - **Financial Margin Reconciliation (R23-14, R23-18)**: Reconciled tax-inclusive gross margins with customer loyalty discounts and stabilized supplier ledger debt calculations across payment voids.
- **Unit Test Suite & Verification (R23-T1)**:
  - Restored deleted regression tests rewritten against production repository methods.
  - Expanded test coverage across R23 security, manager ceilings, multi-owner Option B, and weighed decimal quantities (286 passing unit tests, 0 failures).
- **Release Deliverable**:
  - `Techyfi-POS-Final-V21-10-2026.apk` (versionCode 220967, versionName "2.6.91-Final-V21-10-2026"), R8 minified and resource-shrunk, prepared as an in-place update for existing installations in project root directory.

### Release Final-V21-10-2026: Round 24 Security Governance, Streaming Label Pipeline & Comprehensive Localisation (Version 2.6.92-Final-V21-10-2026, Build Final-V21-10-2026)
- **Security & Authorization Hardening (R24-01 to R24-07)**:
  - **Manager Discount Self-Approval Loophole Closed (R24-01)**: `authorizeDiscountOverride` in `DataRepository.kt` now validates that `supervisor.id != sessionUser?.id` (unless the approver is an Owner) and verifies that the supervisor's effective limit meets or exceeds the requested discount percentage.
  - **Co-Owner Brute-Force Defense (R24-02)**: Invokes `recordFailedPinAttempt()` on failed PIN attempts during owner role/status updates and requires credential verification for co-owner reactivations.
  - **Owner Step-Up Audit Trail (R24-03)**: `ManageUsersScreen.kt` displays a dedicated target owner PIN field for role/status modifications and logs all owner-level changes to an append-only JSON audit trail.
  - **Daily Failure Cap & Reboot Persistence (R24-04)**: Added a 24-hour sliding window daily cap (10 maximum failures per 24 hours) and persists `lock_until` across terminal reboots.
  - **Held Cart Preservation (R24-05)**: Removed unconditional startup deletion of held carts and crash snapshots.
  - **Manager Ceiling Migration (R24-06)**: One-time migration initializes existing manager accounts to the new 20% discount ceiling; user edit dialogs dynamically reset discount limits when roles toggle.
  - **Permission Matrix Boundaries (R24-07)**: Validates delegated permissions against newly granted permissions rather than the full set; restricts due credit overrides to cart scope; adds supervisor elevation prompts for percentage discounts; gates product and dues exports in Settings.
- **Streaming Label Printing & Memory Optimization (R24-08 to R24-13)**:
  - **Streaming Row-by-Row Recycling (R24-08)**: Batch label generator renders, transmits, and recycles bitmaps row-by-row, eliminating multi-hundred-megabyte heap spikes and OOM crashes.
  - **Vector Barcode Scaling (R24-09)**: Generates high-density Code128 barcodes with native module dot alignment, avoiding scan degradation on compact 25x15 mm labels.
  - **True Label Unit Tracking (R24-10)**: Harmonized label progress, retry remaining, and failure banner counts to track physical label units instead of batch rows or product items.
  - **Atomic Transport Writes (R24-11)**: Wrapped socket transmissions in `NonCancellable` blocks to prevent socket closure mid-bitmap and protected active print jobs from accidental dialog dismissal.
  - **Print-Head Constraint Enforcement (R24-12)**: Enforced a 104 mm print-head width limit to prevent lateral layout clipping and synchronized custom label height bounds.
  - **Unified Label Configuration (R24-13)**: Shared label styling preferences, eliminated preview re-renders, and prevented zero-stock items from printing phantom labels.
- **Hardware, Selling & Ledger Resilience (R24-14 to R24-17)**:
  - **USB Twin Conflict Detection (R24-14)**: `isPrinterConflict` detects USB ESC/POS and TSPL conflicts based on port keys regardless of blank USB serial numbers.
  - **Non-Zero Idle Lock Clamping (R24-15)**: Rounds non-zero idle auto-lock durations up to the nearest valid non-zero option (5 minutes minimum).
  - **Selling Safeguards & Preview Z-Report (R24-16)**: Discrete items clamp to >= 1.0; removed lingering saving flags in expenses; due reminders resolve customer phone formats safely; interim Z previews are clearly watermarked.
  - **Supplier Ledger Recalculation (R24-17)**: Sanitized invoice payment notes from display; added one-time ledger balance recalculation; mapped void-guard errors to localized strings; forced normalization of legacy stock adjustment reasons.
- **Settings Search & Navigation Consistency (R24-18, R24-19)**:
  - **Settings Search Actions (R24-18)**: Idle auto-lock navigates directly to the security card; language, theme, layout, and default cost search entries launch their respective configuration dialogs; action switches render in search items; status pills width-capped with ellipsis; search tags fully localized.
  - **Query Normalization (R24-19)**: Standardized phone and digit normalization (`toEnglishDigits`, `normalizePhone`) across Dues list, payments, audit history, and Sales history; supported Bengali payment method search queries ("বিকাশ", "নগদ"); preserved hardware barcode scanner focus.
- **Localized Exceptions, Feedback & System Polish (R24-20 to R24-23)**:
  - **Precise Lockout Seconds (R24-20)**: Extracted `parseLockoutSeconds` to format remaining lockout seconds with localized digits; mapped customer deletion, user management, and ledger errors into bilingual string resources; updated UI screens to use `getLocalizedRepositoryErrorMessage`.
  - **Unified Feedback Channels (R24-21)**: `previewDuplicateDueLedgers` notifies on failure cleanly; eliminated duplicate toasts in expenses and register close; hardened ViewModels with `runCatchingNonCancellation`.
  - **Bilingual Currency & Method Disambiguation (R24-22)**: Disambiguated cash payments ("নগদ টাকা" / "ক্যাশ") from the Nagad mobile financial service ("নগদ"); localized scanner and print dialog labels; cleaned up obsolete string resources and unused state flows.
  - **Design Tokens (R24-23)**: Retained intentional semantic status colors and deferred comprehensive screen-by-screen token refactoring.
- **Comprehensive Unit Testing Suite (R24-T1)**:
  - Executed full test suite: **302 passing unit tests** across all modules with zero failures and zero skipped tests.
  - Added targeted test cases verifying lockout seconds parsing, negative supplier error mapping, and query normalization.
- **Products Screen Visual Parity & Real Device Alignment (V21-10-2026 / V2)**:
  - **Dynamic Product Image Visibility**: Product thumbnail box stays completely hidden when no product image is available, eliminating empty placeholders and reclaiming horizontal space for product titles and tags.
  - **Full-View Image Inspection**: When a product image is present, tapping on the thumbnail launches the dedicated full-screen `EnlargedImageDialog` with high-resolution rendering and dismiss animation.
  - **Unified Single-Row Card Layout (`ProductItemRow`)**: Harmonized list cards across phone and tablet into a sleek single-row card matching the interactive preview (2-line title, laser barcode chip, category tag, bold price, stock pill, 36dp quick add button, and 3-dot overflow menu).
  - **Compact Grid Card Layout (`ProductGridCard`)**: Removed 80dp placeholder space and harmonized grid cards into a compact 3-row architecture (top: thumbnail & stock pill; middle: title, laser barcode chip, category; bottom: price, quick add, and overflow menu).
  - **TopAppBar Quick Actions Hub (`btnQuickActions`)**: Integrated 3D extruded lightning zap button (`Realistic3DMoreZapIcon`) in the top bar actions group with a dropdown menu housing Excel Tools (.xlsx), Stock Audit History, and Batch Print Labels, replacing the wide 3-button ribbon.
  - **Compact 36dp Valuation Stat Bar & 4-Metric Expandable Drawer**: Replaced the large valuation card with a sleek 36dp stat bar displaying total selling valuation, stock health badges (In-Stock, Low, Out), and an expandable drawer revealing 4 balanced metric cards: Purchase Cost, Gross Profit Margin, Total SKUs, and In-Stock Units (e.g., 8513.7 Pcs) with bilingual string support.
  - **Bottom Blank Space Elimination**: Zeroed Scaffold bottom insets (`contentWindowInsets = WindowInsets(0, 0, 0, 0)`) and optimized list content padding (`bottom = 72.dp` on phone) to ensure product cards flow naturally behind the FAB directly to the bottom navigation bar without unnecessary blank space.
  - **Single View Toggle Control**: Replaced the dual segmented switcher with a single 40dp toggle button alternating smoothly between List and Grid layouts.
  - **FlowRow Badge Wrapping**: Wrapped the barcode chip and category badge in an adaptive `FlowRow` in both `ProductItemRow` and `ProductGridCard` with subtle pill containers, completely preventing text truncation on narrow screens.
  - **Connected Device Verification**: Tested, verified, and captured screenshots directly on connected Xiaomi device (`cb5fdfb5`), confirming 100% visual parity with the interactive HTML preview across both List and Grid views, collapsible valuation drawer, and stock cards.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V21-10-2026.apk` (versionCode 220975, versionName "2.6.94-Final-V21-10-2026"), built with R8 minification and resource shrinking, signed with the debug keystore and installed as an in-place update on the connected device in `/Users/shahidulhaque/App Dev/Techyfi POS/`.

### Release Final-V22-10-2026: Category Badge Symmetry & Interactive Category-Tap Filtering (Version 2.6.95-Final-V22-10-2026, Build Final-V22-10-2026)
- **Category Badge Visual Symmetry & Alignment**:
  - **Cohesive Shape & Elevation**: Standardized the category badge across `ProductItemRow` and `ProductGridCard` using `RoundedCornerShape(6.dp)`, border stroke `0.7.dp` with indigo outline (`Color(0xFFC7D2FE)` light / `Color(0xFF6366F1)` dark), and soft indigo tinted background (`Color(0xFFEEF2FF)` light / `Color(0xFF312E81)` dark).
  - **Leading Tag Icon**: Added a prominent leading `Icons.Default.Sell` icon (size 10.dp) to establish instant visual affordance as a category tag.
  - **Baseline & Height Matching**: Harmonized vertical height, internal padding (`horizontal = 6.dp, vertical = 2.dp`), and baseline alignment between the category badge and the 3D laser hologram barcode chip (`Realistic3DHologramLaserCodeIcon`).
  - **Grid Card Height Disparity Fixed**: Replaced unconstrained multi-line `FlowRow` wrapping in `ProductGridCard` with a single-line weighted `Row` (`Modifier.weight(fill = false)`), and enforced `minLines = 2, maxLines = 2` on `product.name`. Cards across both columns now exhibit uniform row heights and vertical baseline symmetry.
- **Interactive Category-Tap Filtering Engine**:
  - **Bidirectional Tap Interaction**: Tapping the category badge on any product card in either List View or Grid View instantly filters the entire product catalog by that category.
  - **Case-Insensitive Trimmed Matching**: Filter logic enforces `.equals(selectedCategory?.trim(), ignoreCase = true)` with fallback to full catalog when toggled off.
  - **Dynamic Active Filter Banner**: Displays a persistent summary strip (`Filtered: <Category> ✕ Clear`) showing the localized Bengali category name (e.g. `Filtered: সস ✕ Clear`).
  - **Active State Visual Feedback**: Highlights the tapped category badge with `MaterialTheme.colorScheme.primary` container color, crisp white text, and white tag icon.
  - **Haptic Feedback & List Reset**: Emits `HapticFeedbackType.LongPress` feedback upon category selection and smoothly scrolls the list or grid back to the top item (`animateScrollToItem(0)`).
  - **One-Tap Reset & Toggle-Off**: Tapping the highlighted category badge a second time or clicking `✕ Clear` resets the filter, restoring the full SKU catalog.
- **On-Device Physical Hardware Verification**:
  - Verified on physical Xiaomi device (`cb5fdfb5`) across both List View and Grid View, confirming card alignment, category tap filtering, clear filter actions, and valuation drawer animations.
- **Hardware Stock Check & Quick Inventory Dialog Polish**:
  - **Multi-Line Product Title Expansion**: Expanded `product.name` in `HardwareStockCheckDialog` (across both `ProductsScreen.kt` and `ScannerScreen.kt`) from 1 line to up to 3 lines (`maxLines = 3`, `lineHeight = 20.sp`, `fontSize = 15.sp`), preventing long SKU titles (e.g., "Himalaya Strawberry Face Wash...") from truncating prematurely with ellipsis.
  - **Product Image Preview & 3D Fallback**: Integrated real product thumbnail rendering via `rememberUriImagePainter` with rounded 10dp borders, falling back seamlessly to `Realistic3DProductsBoxIcon(size = 46.dp)` when no image is registered.
  - **Adaptive M3 Chip Styling**: Standardized the barcode chip with holographic laser code icon and category chip with `Icons.Default.Sell`, bilingual localized category translation, and dark/light mode adaptive color contrast.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V22-10-2026.apk` (versionCode 220977, versionName "2.6.96-Final-V22-10-2026"), compiled with full R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`), signed with the update-compatible keystore for direct in-place installation.

### Release Final-V22-10-2026-R2: Product Code Image & Thermal Label Studio Redesign (Version 2.6.97-Final-V22-10-2026, Build Final-V22-10-2026)
- **Product Hero Card & Real-Time Stock Carousel**:
  - Integrated product hero card featuring category pill, 3D avatar badge, SKU pill, and live stock health indicator dot with quantity display.
  - In-dialog pagination carousel (`< 1 of N >`) allowing cashiers to quickly cycle through all products in catalog without dismissing the dialog.
- **Pinned Thermal Sticker Preview Canvas**:
  - Pinned sticker stage with live dimensions and DPI indicator badge (`50 × 30 mm • 203 DPI`).
  - High-Resolution **Zoom Inspect Modal** with thermal dot matrix simulation and scannability inspection.
  - 1-tap template presets carousel: *Standard Retail*, *Grocery & Expiry*, *Jewelry Mini*, *Apparel Dual-Price*, *Warehouse Code 128*.
  - Density warning banner with 1-tap switch to QR Code when barcode width is narrow (< 45mm) to protect against unreadable thermal scans.
- **Segmented 3-Tab Architecture**:
  - **Tab 1: `🏷️ Label Setup`**:
    - Code Format dropdown (Code 128, EAN-13 with auto recommended badge, EAN-8, Code 39, QR Code).
    - Thermal DPI toggle (203 DPI standard vs 300 DPI hi-res).
    - Price Tier selector (Selling, MRP, Wholesale) + compact single-row **Dual Price** (`MRP + Discounted Price` with struck-through MRP and selling price preview).
    - Micro-options: *Show Unit (/pcs)*, *Show Code Number*, *Bangla Numerals (০-৯)*.
    - Print Copies: **Auto (Stock)** vs **Manual** toggle mode with SharedPreferences persistence, `+`/`−` stepper, and quick chips (1, 2, 3, 5, 12, 50, 100, Stock).
  - **Tab 2: `🏢 Store & Fields`**:
    - Store Branding: *Show Store Name* & *Show Store Logo* toggles with store name input field.
    - Label Dimensions Dropdown: All 8 standard thermal presets (25×15 mm, 30×20 mm, 40×25 mm, 45×35 mm, 50×30 mm, 60×40 mm, 100×150 mm, and Custom mm inputs).
    - Compact 2-column Additional Custom Field with 1-tap quick presets (`+ Batch`, `+ Size`, `+ Weight`, `+ Origin`, `✕ Clear`).
  - **Tab 3: `📅 Dates & Scale`**:
    - Dates & Tracking: Enable Dates toggle, Mfg vs Pkg selector with 3D icons, and date pickers.
    - Element Scaling: Clean header with "ELEMENT SCALING" and "Reset All", and all 8 scalers (*Product Name, Price, Code Number, Store Logo, Store Name, Date Tracking, Barcode Graphic, Extra Field*) complete with `+`/`−` step buttons, percentage readout, and individual reset controls.
- **Fixed Bottom Action Dock**:
  - Dynamic primary print button displaying the active copy count (`Print (N Copies)`).
  - Instant action buttons for PDF export/sharing and PNG image saving.
- **Full Bilingual Localization**:
  - Complete English and Bengali translations across all strings, labels, and badges.
- **Release Deliverable**:

### Release Final-V22-10-2026-R3: Product Code Image Dialog Layout Fix & Interactive Physical Device Verification (Version 2.6.98-Final-V22-10-2026, Build Final-V22-10-2026)
- **Zero-Collision Single Scroll Body Architecture**:
  - Eliminated the colliding duplicate copies card and banner that was previously anchored beneath the dialog header.
  - Unified the entire dialog body into a single smooth vertical scroll container (`Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())`), ensuring no overlapping surfaces or clipping across compact and tall screen viewports.
- **Preview Parity with `generate_code_image_redesign_preview.html`**:
  - **Quick Template Presets Carousel**: Integrated inside the Sticker Preview Card with real-time switching logic (`applyTemplatePreset`) supporting 5 industry presets (*Standard Retail*, *Grocery & Expiry*, *Jewelry Mini*, *Apparel Dual-Price*, *Warehouse Code 128*).
  - **Thermal DPI Selector**: Integrated standard `203 DPI (Std)` vs `300 DPI (Hi-Res)` segmented chips directly beneath the Code Format dropdown in Tab 1.
  - **Print Copies Placement**: Embedded the complete copies management card cleanly at the bottom of Tab 1 (`🏷️ Label Setup`) featuring Auto (Stock) vs Manual toggle, `+`/`−` step controls, direct numeric text editing with Bangla numeral parsing, and quick chips row (1, 2, 3, 5, 10, 20, 50, 100, Stock).
  - **Interactive High-Res Zoom Inspect Modal**: Verified full functionality of high-resolution sticker inspection modal with dismiss action.
- **Physical Device Live Verification (Xiaomi Device `cb5fdfb5`)**:
  - Successfully verified on physical hardware by tapping the product barcode chip `#846656005286` on Product Card 1.
  - Confirmed 100% visual and layout parity with `interactive_preview/generate_code_image_redesign_preview.html` across all 3 tabs:
    - Tab 1: Code formats, Thermal DPI chips, Price Tier cards, Dual Price strike-through, Micro-options, and Print Copies card.
    - Tab 2: Store Branding toggles, Store Name field, Label Dimensions dropdown, and 2-column Additional Custom Field with quick chips.
    - Tab 3: Manufacturing/Packaging dates and 8 individual element scaling steppers with reset controls.
    - Zoom Inspect: Tap-to-zoom sticker preview modal with high-res simulation.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V22-10-2026.apk` (versionCode 220979, versionName "2.6.98-Final-V22-10-2026"), compiled with full R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`), signed with update-compatible keystore and verified live on device.

### Release Final-V22-10-2026-R4: Product Code & Label Studio Vibrant Aesthetic Overhaul (Version 2.6.99-Final-V22-10-2026, Build Final-V22-10-2026)
- **Aesthetic Refactor Matching Interactive HTML Preview (`generate_code_image_redesign_preview.html`)**:
  - **Eliminated Flat Washout**: Removed default flat Material 3 tonal elevation (`tonalElevation = 0.dp`) in favor of crisp `#F8FAFC` elevated surfaces, high contrast cards with 1.2dp border stroke, and subtle 1dp/2dp elevation.
  - **3D Gradient Badges & Header**: Upgraded dialog icon header to a 3D rounded badge with rich Teal POS linear gradient (`#0F766E` to `#14B8A6`), accompanied by crisp dark title and subtitle tags.
  - **Hero Product Switcher Card**: Rendered 3D package avatar, monospace barcode chip `#846656005286`, live stock status badge (`In Stock: 3 pcs`), and circular carousel stepper (`< 1 of 621 >`).
  - **Physical Sticker Paper Canvas**: Elevated sticker canvas with physical paper texture, realistic 8dp drop shadow, 1.5dp rounded border, 50x30 mm 203 DPI tag, and high-contrast Zoom Inspect action.
  - **Quick Template Presets Carousel**: Horizontal preset chips (*Standard Retail*, *Grocery & Expiry*, *Jewelry Mini*) with active teal outline and background glow.
  - **Pill Segmented Tab Bar**: Full teal gradient active indicator with bold white typography across Tab 1 (*🏷️ Label Setup*), Tab 2 (*🏢 Store & Fields*), and Tab 3 (*📅 Dates & Scale*).
  - **Vibrant Price Tier & Controls**: Elevated price tier selector with selling price (৳520), MRP (৳550), wholesale status, dual display strike-through, and thermal DPI toggle chips.
  - **Floating Action Dock**: Full-width Teal POS gradient Print CTA button (`Print (1)`) with 4dp elevation, paired with elevated circular action buttons for sharing and download.
- **Physical Device Live Verification (Xiaomi Device `cb5fdfb5`)**:
  - Verified live on device display (1080x2248): captured and confirmed visual fidelity across Tab 1, Tab 2, and Tab 3.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V22-10-2026.apk` (versionCode 220980, versionName "2.6.99-Final-V22-10-2026"), compiled with full R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`), signed with update-compatible keystore, deployed and verified on Xiaomi device.

### Release Final-V22-10-2026-R5: Redundant Dual Price Card Elimination (Version 2.7.00-Final-V22-10-2026, Build Final-V22-10-2026)
- **Eliminated Redundant Dual Price Card**:
  - Removed duplicate `Dual Price (MRP + Selling) / Prints MRP struck-through with Selling Price` checkbox Surface from Tab 1 of `ProductCodeImageDialog`.
  - Bound dual price state cleanly and directly to `PriceTierSwitcherCard`'s integrated `Dual Display (MRP + Offer)` row (`onTierSelect = { isDualPrice = (it == ProductCodePriceTier.DUAL); priceTierName = it.name }`).
  - Restored clean, streamlined vertical flow transitioning directly from `PriceTierSwitcherCard` into `Show Code Number` and `PRINT COPIES`.
- **Physical Device Live Verification (Xiaomi Device `cb5fdfb5`)**:
  - Installed and verified live on Xiaomi MI 8 hardware; captured on-device screenshots confirming clean layout without duplicate controls.
- **Release Deliverable**:
  - `Techyfi-POS-Final-V22-10-2026.apk` (versionCode 220981, versionName "2.7.00-Final-V22-10-2026"), compiled with full R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`), signed with update-compatible keystore and verified live on device.

### Release Final-V22-10-2026-R6: Store Branding Toggle Chips Formatting & Text Centering (Version 2.7.01-Final-V22-10-2026, Build Final-V22-10-2026)
- **Store Branding Toggle Chips Centering & Single-Line Formatting**:
  - Replaced Material 3 `FilterChip` in `ProductCodeImageDialog` (Tab 2: Store & Fields) with custom `Surface` toggle chips.
  - Set fixed, symmetric 38dp height on both `Show Store Name` and `Show Store Logo` chips, preventing awkward multi-line text wrapping.
  - Centered text and checkmark layout horizontally and vertically (`Arrangement.Center` and `TextAlign.Center`).
  - Synced formatting with `.micro-switch-chip` in `interactive_preview/generate_code_image_redesign_preview.html`.
### Release Final-V23-10-2026-R7: Pinned Live Sticker Preview Stage & 5 Definitive Barcode Label Designs (Version 2.7.02-Final-V23-10-2026, Build Final-V23-10-2026)
- **Pinned Live Sticker Preview Stage (`ProductCodeImageDialog` in `ProductCodePrintDialogs.kt`)**:
  - Restructured the single-scroll layout into a pinned header + pinned sticker preview + pinned segmented 3-tab selector + scrollable setup controls architecture.
  - Prevents the sticker preview from scrolling off-screen when users scroll down to adjust text scales, barcode graphic heights, custom label dimensions, dates, price tiers, and store branding switches.
  - Real-time instant visual feedback: any changes made in the scrollable tab body update the pinned sticker preview immediately via background coroutine rendering.
- **5 Definitive Barcode Label Designs (`ProductCodeImageUtils.kt`)**:
  - `STANDARD_RETAIL`: Original standard retail label featuring bold centered title, horizontal barcode, price pill, and store name footer.
  - `BOUTIQUE_CREST`: Premium fashion/boutique layout featuring circular crest monogram, price pill, 2-line title, horizontal barcode, and authentic date footer.
  - `BRAND_HERO_SPLIT`: 38%/62% split layout featuring brand emblem shield, solid price card, and horizontal barcode.
  - `COMPACT_MINIMALIST`: Clean text-centric layout without logo featuring diamond rule separator (`── ◆ ──`), horizontal barcode, and 2-column bottom price & dates.
  - `SHELF_EDGE_TAG`: Supermarket shelf tag layout featuring category pill, 60% extra-wide high-contrast barcode, 40% large retail price card, and inverted black footer.
- **Touch Targets & Accessibility**:
  - Enhanced dialog header close button and scaler reset buttons to meet >= 48dp Material 3 touch target standards.
  - Responsive design switcher row with `weight(1f, fill = false)` and `TextOverflow.Ellipsis` preventing any overflow on narrow 360dp phone viewports.
- **R8 Minification & Resource Shrinking**:
  - Built with full R8 code optimization (`isMinifyEnabled = true`), resource shrinking (`isShrinkResources = true`), and update-compatible signing certificate (`signingConfigs.debug`).
### Release Final-V23-10-2026-R8: App-Wide Comprehensive Quality Audit, Touch Target Hardening, RBAC Search/Filter, Barcode Studio Quick Search, and R8 Minification (Version 2.7.03-Final-V23-10-2026, Build Final-V23-10-2026)
- **Staff & User Access Control (`ManageUsersScreen.kt`)**:
  - Added real-time staff name searching with `PosSearchBox` and instant query matching.
  - Added horizontal role filter chips (`All`, `Owner`, `Manager`, `Cashier`, `Suspended`) with live member counters.
  - Displayed formatted account join dates (`Joined: dd MMM yyyy`) on `StaffUserCard`.
  - Added dedicated empty search state with clear filters shortcut button and 3D customer icon empty state with "Add Staff" CTA.
- **Barcode & QR Label Studio Quick Search (`ProductCodePrintDialogs.kt`)**:
  - Integrated in-dialog product search modal with `PosSearchBox` directly in the carousel bar, allowing instant jumping to any product in large catalogs without repetitive sequential paging.
  - Added 2-line title wrapping (`maxLines = 2`) eliminating single-line ellipsis truncation for long product names.
  - Localized stock quantity badge with `formatStockQuantityBadge` handling fractional stock and `R.string.out_of_stock`.
- **Sales Report Channel Mix Grand Total (`SalesReportScreen.kt`)**:
  - Added Grand Total summary row in `PaymentsPanel` aggregating overall transactions and net revenue collected across all payment channels (Cash, bKash, Nagad, Card, Rocket).
- **Expenses Dynamic Running Sum & Dynamic Header Metric (`ExpensesScreen.kt`)**:
  - Implemented real-time dynamic running net total pill in the ledger header row showing net cash flow for the currently filtered set (`+৳` for cash in, `-৳` for expenses).
  - Dynamically updated the TopAppBar metric badge to reflect the active period filter (`Today`, `Week`, `Month`, `All`).
- **Touch Target & Ergonomics Hardening (Material 3 >= 48dp standard)**:
  - Enlarged `CopiesStepper` quantity decrement and increment buttons to 48dp.
  - Enlarged note presets close button in `CustomItemDialogs` to 48dp.
  - Enlarged product name and barcode clear buttons in `AddEditProductDialog` to 36dp with expanded tap padding.
  - Enlarged carousel navigation chevrons and close button in `ProductCodeImageDialog` to 44–48dp.
- **Tablet Responsive Sizing**:
  - Added `Modifier.widthIn(max = 680.dp)` constraints to `AddEditProductDialog`, `BatchProductCodePrintDialog`, and `ProductCodeImageDialog` surfaces, preventing awkward horizontal stretching on 10"–12" tablet viewports.
- **Product Date Validation & Crash Hardening**:
  - Added format parsing (`parseExpiryDateToMillis`) for `mfgOrPkgDate` in `AddEditProductDialog`, preventing malformed date strings from saving into SQLite.
  - Added warning banner and validation check preventing manufacturing date from being set in the future or after the expiry date.
  - Added bounds checking `if (idx in presetsList.indices)` in `CustomItemDialogs` preventing `IndexOutOfBoundsException` during preset edits.
  - Fixed database migration warning in `AppDatabase.kt` by specifying explicit `arrayOf<Any>(...)` types for `db.execSQL`.
- **Dark Mode & Localization Parity**:
  - Updated `StockStatus3DBadge` to use `LocalDarkTheme` and `isSystemInDarkTheme` aware container tints and localized strings (`R.string.out_of_stock`, `R.string.low_stock`, `R.string.in_stock`).
  - Added 7 new string keys with exact 1-to-1 matching across `res/values/strings.xml` and `res/values-bn/strings.xml` (3,444 lines each).
- **R8 Minification & Seamless Update Deliverable**:
  - Built with full R8 code optimization (`isMinifyEnabled = true`), resource shrinking (`isShrinkResources = true`), incremented `versionCode = 220984` for in-place update compatibility, and signed with update-compatible keystore.
  - Saved deliverable in project root: `Techyfi-POS-Final-V23-10-2026.apk`.

### Release Final-V25-10-2026-R8: Specialized Medicine Shop POS (ফার্মেসি পিওএস), DIMS 29,834 Medicine Master Database, 3-Tier Multi-Unit Pricing, Clinical Generic Substitutes, Antibiotic Rx Enforcement, Package Rename to com.techyfiposmeds, and R8 Minification (Version 2.8.00-Final-V25-10-2026, Build Final-V25-10-2026)
- **Specialized Pharmacy Architecture & DIMS Master Database (`techyfi_pos_meds.db`)**:
  - Transitioned the entire retail POS catalog into a dedicated medicine shop POS powered by Bangladesh DIMS (29,834 medicines across 692 pharmaceutical companies and 1,425 clinical generics).
  - Integrated pre-populated high-speed SQLite database into `app/src/main/assets/techyfi_pos_meds.db` and implemented Room schema migration version 39 in `AppDatabase.kt`.
  - Built `MedicineRepository.kt` with FTS5 full-text indexing, multi-column search, generic matching, and clinical reference queries.
  - Replaced the Products section with the Medicine Database catalog in `ProductsScreen.kt`, providing responsive Grid and List views with instant search.
  - Added pharmacy dosage form filter chips: `সব ওষুধ (29,834)`, `🚨 অ্যান্টিবায়োটিক (4,443)`, `⚪ ট্যাবলেট`, `💊 ক্যাপসুল`, `🧪 সিরাপ`, `💉 ইনজেকশন`, `💧 আই ড্রপ`, `⚠️ Low Stock`, `🚫 Out of Stock`.
  - Added 3D realistic blister strip and floating dual-color capsule micro-animations in `Realistic3DIconSuite.kt`.
- **3-Tier Multi-Unit Pricing Engine (`পিস / পাতা / বক্স`)**:
  - Implemented 3-tier selling unit model supporting Loose Piece (`পিস`), Blister Strip (`পাতা`), and Pack/Box (`বক্স`) with automated unit conversions.
  - Integrated 1-tap quick add buttons (`+ পাতা`, `+ পিস`, `+ কার্ট`) directly on medicine list rows and grid cards.
  - Added interactive unit-type selector chips on cart items in `CartScreen.kt` with real-time recalculation of subtotals and discounts.
- **Instant Generic Substitute Finder (`MedicineSubstituteDialog.kt`)**:
  - Enabled 1-tap matching of therapeutically identical brands sharing the same generic molecule, dosage form, and strength (e.g. Napa Extra ↔ Fast Flur ↔ Ace Plus).
  - Displays unit prices, company names, and stock status for every alternative brand with 1-tap cart replacement.
- **DIMS Clinical Reference Sheet (`MedicineClinicalDialog.kt`)**:
  - Provided pharmacists with instant point-of-sale clinical guidelines: Indications (নির্দেশনা), Adult & Child Dose (সেবনমাত্রা), Contraindications (প্রতিনির্দেশনা), Side Effects (পার্শ্বপ্রতিক্রিয়া), and Precautions (সতর্কতা).
- **DGDA Antibiotic Prescription Enforcement**:
  - Flagged 4,443 antibiotic products with `is_antibiotic = true` and visual hazard indicators.
  - Added warning banner in `CartScreen.kt` enforcing capture of Prescribing Doctor's Name and BMDC Registration Number before checkout.
  - Persisted doctor details into Room `Sale` and printed on thermal bills for DGDA regulatory compliance.
- **1-Tap Bengali Dosage Schedule Chips**:
  - Added fast dosage instruction selector chips in `CartScreen.kt`: `১+০+১ (খাওয়ার পর)`, `১+১+১`, `০+১+০`, `১+০+০`, `০+০+১`, and `প্রয়োজনে (SOS)`.
  - Attached dosage notes to line items for printing on customer receipts.
- **FEFO Batch Tracking & Physical Shelf Locator**:
  - Rendered physical rack coordinates (`📍 Rack A-1`), batch number (`🏷️ BX-2024-08`), and expiry countdown tags (`⏳ 2026-11-30`) across catalog and cart items.
- **Specialized Pharmacy Thermal Receipt Formatting**:
  - Updated `BillPdfGenerator.kt` for 58mm/80mm thermal printers with DGDA Drug License number header, doctor details, dosage schedules, and batch/expiry data.
- **Package & Namespace Rename (`com.techyfiposmeds`)**:
  - Renamed namespace and applicationId in `app/build.gradle.kts` from `com.example.techyfipos` to `com.techyfiposmeds`.
  - Moved all 129 source, test, and androidTest files to `com/techyfiposmeds/` and updated manifest, receivers, and Room schemas.
- **R8 Minification & Update Deliverable**:
  - Built with full R8 code optimization (`isMinifyEnabled = true`), resource shrinking (`isShrinkResources = true`), incremented `versionCode = 220985` for in-place update compatibility, and signed with update-compatible keystore.
  - Deliverable saved in project root: `Techyfi-POS-Meds-Final-V25-10-2026.apk`.

## Maintenance note

Update this file whenever a screen, navigation route, persistent entity, payment workflow, or critical business rule changes. It is intended to remain the concise source of truth for future development work in this workspace. All updated APKs and mind maps must always be stored in `/Users/shahidulhaque/App Dev/Techyfi POS/`.






