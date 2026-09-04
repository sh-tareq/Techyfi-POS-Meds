---
name: bangla-language
description: >-
  Bangla (Bengali) language localization, translation standards, natural retail/colloquial phrasing,
  POS and billing terminology, typography, Unicode rendering, and bilingual UI/UX best practices for
  Bangladeshi software and apps. Activate whenever translating, generating, formatting, reviewing,
  or debugging Bangla text, receipts, strings.xml, or Bengali localized interfaces.
---

# Bangla Language Localization & Retail POS Standards

This skill provides comprehensive rules, terminology standards, typography guidelines, and architectural principles for delivering natural, production-grade Bangla (Bengali) localization in software applications, mobile apps (Android/iOS), web interfaces, and Point of Sale (POS) / thermal receipt systems tailored specifically for Bangladesh.

---

## 1. Natural Phrasing Principles (Bangladesh Retail & Commercial Context)

When localizing interfaces for Bangladeshi users, avoid literal machine translations (e.g. raw Google Translate output) and overly archaic or formal Sanskritized Bengali (সাধু বা অতিরিক্ত গুরুগম্ভীর বইয়ের ভাষা). Bangladeshi users and store owners expect clear, professional, and commonly spoken commercial terms (চলতি ও বাস্তবসম্মত বাণিজ্যিক ভাষা).

### Core Translation Guidelines
1. **Colloquial Commercial Naturalism**: Use phrasing that shopkeepers, cashiers, customers, and accountants actually use daily in Dhaka, Chittagong, and across Bangladesh.
2. **Context-Aware Vocabulary**:
   - For an invoice/bill header: use **চালান** or **বিল** (Invoice # -> **চালান নং**), never *পণ্য চালানপত্র* or *তালিকা পত্র*.
   - For subtotal: use **উপমোট** or **সাবটোটাল**, never *উপ-যোগফল* or *মধ্যবর্তী মোট*.
   - For grand total: use **সর্বমোট** or **মোট টাকা**, never *চূড়ান্ত মোট অঙ্ক*.
   - For change returned to customer: use **ফেরত**, never *উদ্বৃত্ত অর্থ* or *অবশিষ্ট টাকা*.
   - For customer credit / due debt: use **বকেয়া** or **বাকি**, never *ঋণ* or *ধার্য*.
   - For paid / collected amount: use **পরিশোধিত** or **জমা**, never *প্রদত্ত*.
   - For cash payment: use **নগদ**, never *মুদ্রা* or *ক্যাশ ক্যাশ*.
   - For discounts: use **ছাড়** or **ডিসকাউন্ট**, never *বাট্টা* or *মূল্য হ্রাস*.
   - For sales return: use **বিক্রয় ফেরত**, never *বিক্রয় প্রত্যর্পণ*.
   - For cashier / operator: use **ক্যাশিয়ার**, never *কোষাধ্যক্ষ* or *হিসাব রক্ষক*.
3. **Reference Glossary**: Refer to [references/retail_pos_glossary.md](./references/retail_pos_glossary.md) for an exhaustive dictionary of standard terms, approved alternatives, and forbidden awkward translations.

---

## 2. Invariant Rule: Preservation of Dynamic & Technical Data

Never perform wholesale or blind translation on dynamic, technical, or customer-specific identifiers:

1. **Product & Brand Names**:
   - Keep product names, brand labels, and model numbers exactly as entered (e.g., *"Coca-Cola 500ml"*, *"Samsung Galaxy A15"*, *"Aarong Dairy Milk"*) unless explicitly marked for translation.
2. **Identifiers & Numbers**:
   - Preserve invoice numbers, order tokens, and serials (e.g., `#INV-651`, `POS-2026-09`).
   - Preserve customer phone numbers (e.g., `01712-345678`), URLs, email addresses, barcodes (EAN-13, Code-128), and QR codes.
3. **Numerals & Currency Standard**:
   - Currency symbol: Use Bengali Taka symbol **`৳`** (`\u09F3`) or **`Tk.`**. Avoid `BDT` or `RS` unless ISO code is specifically requested.
   - Digits in Retail & POS: Thermal receipt printers, barcodes, and calculators in Bangladesh standardly use English digits (`0-9`, e.g., `৳550.00`, `Qty: 2`) for universal scannability and retail legibility. Only convert digits to Bengali numerals (`০-৯`, e.g., `৳৫৫০`) if the user or setting explicitly requires full Bengali numerals.

---

## 3. Bilingual UI Architecture & Separation of Concerns

### The "Receipt vs Interface" Rule
When an application provides language toggles (e.g. `[ EN | বাং ]` on a receipt preview or checkout screen):
- **Receipt Scope Only**: Toggling receipt language must **strictly affect only the receipt canvas / document**. It must **never** modify the language of dialog window chromes, top app bars, bottom navigation, floating action buttons, or system menus.
- **App Language Scope**: The global interface text must switch to Bangla **only** when the user explicitly changes the **App Language** in Settings (or via system locale).

### Android Localization Conventions
- Default English strings: `res/values/strings.xml`
- Localized Bangla strings: `res/values-bn/strings.xml`
- Format Arguments: Always preserve string format tokens (`%1$s`, `%2$d`, `%1$.2f`) without modifying the placeholder syntax or argument order.
- Plurals: In Bengali, nouns do not change form drastically for plurals (e.g., "২টি আইটেম" vs "১টি আইটেম"). Ensure format strings match natural usage with standard classifiers (টি, টা, জোড়া, কেজি, প্যাকেট).

---

## 4. Typography, Fonts, & Rendering (Android / Web / Thermal)

Bangla is an Indic script featuring complex conjuncts (যুক্তাক্ষর), dependent vowel signs (কারচিহ্ন: ি, ী, ু, ূ, ৃ, ে, ৈ, ো, ৌ), hasant (্), ref (র্), and ya-phala (্য).

1. **Font Recommendations**:
   - **Android System**: `Noto Sans Bengali` (pre-installed across modern Android versions).
   - **Custom Branding**: `Hind Siliguri`, `SolaimanLipi`, `Kalpurush`.
2. **Canvas / Paint Line Height**:
   - Bengali glyphs require greater vertical clearance due to ascenders (কারচিহ্ন/রেফ) and descenders (হসন্ত/উ-কার).
   - In custom `Canvas` drawing or thermal receipt rendering (`Paint`), apply a line height multiplier of **1.25x to 1.4x** compared to Latin text to prevent vertical clipping.
3. **Thermal Printer (ESC/POS) Rendering**:
   - Standard thermal receipt printers (80mm and 58mm) lack hardware code pages for Bengali Unicode.
   - **Always** render Bengali receipts as an Android `Bitmap` image via `android.graphics.Canvas` and stream the raster bitmap to the printer.
   - See [references/typography_and_rendering.md](./references/typography_and_rendering.md) for implementation patterns.

---

## 5. Quality Audit Checklist for Bangla Features

Before submitting any Bangla language update, verify:
- [ ] Are commercial and retail terms natural, colloquial, and widely understood in Bangladesh?
- [ ] Are dynamic tokens (invoice IDs, brand names, phone numbers) left intact?
- [ ] Are conjuncts (যুক্তাক্ষর) rendered cleanly without dotted circles (◌্) or broken glyphs?
- [ ] Did toggling the receipt language isolate changes strictly to the receipt without affecting the main UI?
- [ ] Does `values-bn/strings.xml` have matching keys with `values/strings.xml` to prevent missing resource crashes?
