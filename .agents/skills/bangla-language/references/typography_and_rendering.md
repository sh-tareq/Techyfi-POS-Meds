# Bangla Typography, Unicode & Thermal Print Rendering

This reference provides technical guidance on rendering Bengali script cleanly across Android Jetpack Compose, custom Android `Canvas`, HTML/CSS web applications, and ESC/POS thermal receipt printers.

---

## 1. Bengali Script Characteristics & Unicode

Bengali (বাংলা) is an abugida script written left-to-right. It features:
- **Independent Vowels (স্বরবর্ণ)**: অ, আ, ই, ঈ, উ, ঊ, ঋ, এ, ঐ, ও, ঔ
- **Consonants (ব্যঞ্জনবর্ণ)**: ক, খ, গ, ঘ, ঙ ...
- **Dependent Vowel Signs (কারচিহ্ন)**: া, ি, ী, ু, ূ, ৃ, ে, ৈ, ো, ৌ
- **Modifiers & Diacritics**: ং (অনুস্বার), ঃ (বিসর্গ), ঁ (চন্দ্রবিন্দু), ৎ (খণ্ড ত)
- **Hasant / Virama (্ - `\u09CD`)**: Combines consonants into conjuncts (যুক্তাক্ষর, e.g. ক + ্ + ষ = ক্ষ, স + ্ + ত = স্ত).
- **Ya-phala (্য - `\u09CD\u09AF`) & Reph (র্ - `\u09B0\u09CD`)**: Crucial conjunct extensions.

### Common Rendering Pitfalls
1. **Broken Conjuncts**: When font engines or rasterizers do not support Indic shaping, characters render with broken hasant dots (e.g. ক্ ষ instead of ক্ষ).
2. **Missing Glyphs / "Tofu" Boxes (□)**: Occurs when fonts lack Bengali Unicode coverage (`U+0980` to `U+09FF`).
3. **Clipping of Ascenders & Descenders**: Bengali vowel signs like `ী`, `ৈ`, `র্` extend significantly above the cap-height, while `ু`, `ূ`, `ৃ`, `্র` extend below the baseline. Insufficient vertical line spacing clips these diacritics.

---

## 2. Recommended Fonts

| Environment | Primary Recommended Font | Secondary / Web Fallback | Notes |
| :--- | :--- | :--- | :--- |
| **Android System** | **Noto Sans Bengali** (`Typeface.DEFAULT`) | Roboto + Noto fallback | Bundled in Android 5.0+; zero apk overhead. |
| **Android App Assets** | **Hind Siliguri** | SolaimanLipi, Kalpurush | Modern, clean geometry, excellent for high-DPI screens. |
| **Web / Interactive Preview**| `'Hind Siliguri', 'Noto Sans Bengali', sans-serif` | Google Fonts import | Import `@import url('https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;500;600;700&display=swap');` |
| **Thermal Receipts** | **System Sans-Serif Bold** rendered to Canvas Bitmap | Noto Sans Bengali | Never send raw text bytes to ESC/POS; always rasterize to Bitmap. |

---

## 3. Jetpack Compose Typography & Line Height

When using Jetpack Compose, always grant sufficient `lineHeight` when Bengali text may wrap:

```kotlin
val BanglaBodyStyle = TextStyle(
    fontFamily = FontFamily.Default, // Or custom Hind Siliguri FontFamily
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp, // ~1.4x font size to prevent ascender/descender collision
    letterSpacing = 0.sp
)

val BanglaTitleStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp
)
```

### Avoiding Character Truncation in Chips & Badges
When using small containers (`Text` inside a badge or chip):
- Add explicit vertical padding (`Modifier.padding(horizontal = 8.dp, vertical = 4.dp)`).
- Avoid restrictive hardcoded heights like `Modifier.height(18.dp)` which clip the top of `ে` or bottom of `ু`.

---

## 4. Android Canvas & Thermal Receipt Bitmap Generation

Because ESC/POS receipt printers (Xprinter, Rongta, Epson, Sunmi) typically do not contain native hardware code pages for Bengali Unicode, printing Bengali receipts **must be done via raster image generation**.

### Safe Canvas Rendering Pipeline
```kotlin
val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = 20f
    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
}

// Line height calculation for Bengali (at least 1.35x text size)
val lineHeight = paint.textSize * 1.35f
```

### Thermal Receipt Dimensions
| Roll Width | Canvas Width (px) | Printable Width (px) | Characters Per Line (Approx) |
| :--- | :--- | :--- | :--- |
| **80 mm** (Standard POS) | **576 px** (or 384 px on low-res) | 540 px | 42 - 48 chars |
| **58 mm** (Mobile POS) | **384 px** | 356 px | 30 - 32 chars |

### Column Alignment & Width Safeguards
For narrow rolls (58mm):
```kotlin
// Divide canvas into safe proportional columns
val colItemMaxW = 150f  // Item name truncated or wrapped
val colQtyX = 185f      // Centered quantity
val colPriceX = 265f    // Unit price
val colTotalX = 370f    // Right-aligned item total
```
Always use `paint.measureText(text)` to ensure right-aligned numbers or totals do not overflow off the canvas edge.

---

## 5. Currency Symbol (৳) & Number Formatting Standards

### Currency Formatting
- Standard Bangladeshi Taka symbol: **`৳`** (Unicode `U+09F3`).
- Placement: Prefix before the amount with or without thin space (e.g., `৳550.00` or `৳ 550`).
- Alternative: `Tk.` or `Tk` in plain ASCII contexts.

### English Numerals vs Bengali Numerals in Retail
- **Default Retail Standard**: Use English numerals (`0-9`, e.g., `৳550.00`, `Qty: 3`).
  - Barcode scanners, product serials, thermal receipts, and accounting ledgers in Bangladesh standardly use English digits for scannability and quick arithmetic.
- **Full Localization (When Requested)**:
  - If the client specifically requests Bengali numerals:
  ```kotlin
  fun toBanglaDigits(input: String): String {
      val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
      return input.map { ch ->
          if (ch in '0'..'9') banglaDigits[ch - '0'] else ch
      }.joinToString("")
  }
  ```
- **South Asian Comma Grouping (Lakh / Crore)**:
  - Format large amounts with Bangladeshi grouping when presenting formal financial reports:
    - 1,00,000 (1 Lakh / এক লক্ষ)
    - 1,00,00,000 (1 Crore / এক কোটি)
  - Standard regex / formatter:
    `#,##,##,##0.00`
