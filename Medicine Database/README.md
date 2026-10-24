# Techyfi POS Medicine Database

Production-grade pharmaceutical product database formatted specifically for **Pharmacy Point of Sale (POS)** and billing applications in Bangladesh.

---

## 📁 Package Contents

| File / Folder | Format | Description | Records |
| :--- | :--- | :--- | :--- |
| **`techyfi_pos_meds.db`** | SQLite 3 | Ready-to-embed SQLite database with indexes & FTS5 full-text search | 29,834 medicines |
| **`pos_medicines_master.csv`** | CSV (UTF-8 BOM) | Master POS products table for bulk import into MySQL, PostgreSQL, etc. | 29,834 medicines |
| **`pos_medicines_master.json`** | JSON | Full medicine catalog for mobile apps (Flutter, React Native) and web APIs | 29,834 medicines |
| **`pos_generics_master.csv`** | CSV (UTF-8 BOM) | Clinical reference (indications, doses, contraindications, side effects) | 1,421 generics |
| **`pos_generics_master.json`** | JSON | Clinical reference in JSON format | 1,421 generics |
| **`pos_companies_master.csv`** | CSV (UTF-8 BOM) | Pharmaceutical manufacturers and distributors directory | 692 companies |
| **`schema.sql`** | SQL | Database initialization script for SQLite, PostgreSQL, and MySQL | DDL script |
| **`raw_pharma_tables/`** | Directory | All 18 raw pharmaceutical relational tables exported as individual CSVs | Full raw schema |

---

## 🗄️ Database Architecture (`techyfi_pos_meds.db`)

### 1. `pos_medicines` (Main POS Catalog)
This is the primary table queried during cashier checkout, search, and inventory management:

| Column | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `id` | INTEGER | Auto-increment Primary Key | `14520` |
| `sku` | TEXT | Unique POS SKU identifier | `MED-117477` |
| `barcode` | TEXT | EAN-13/Code-128 scanner barcode | `890117477` |
| `brand_id` | INTEGER | Original brand identifier | `14520` |
| `brand_name` | TEXT | Trade/Brand name (Indexed) | `Napa` |
| `display_name` | TEXT | Full descriptive item name | `Napa 500mg Tablet` |
| `dosage_form` | TEXT | Form (Tablet, Syrup, Injection, etc.) | `Tablet` |
| `strength` | TEXT | Dosage strength | `500mg` |
| `generic_id` | INTEGER | Reference to `pos_generics` | `1124` |
| `generic_name` | TEXT | Active pharmaceutical generic (Indexed) | `Paracetamol` |
| `company_id` | INTEGER | Reference to `pos_companies` | `26` |
| `company_name` | TEXT | Manufacturer name (Indexed) | `Beximco Pharmaceuticals Ltd.` |
| `unit_price` | REAL | Retail price per tablet/piece/bottle | `1.20` |
| `strip_size` | INTEGER | Tablets/capsules per blister strip | `10` |
| `strip_price` | REAL | Price per strip (`unit_price * strip_size`) | `12.00` |
| `pack_size` | TEXT | Original packaging description | `500's pack` |
| `units_per_pack` | INTEGER | Total loose units in a full box | `500` |
| `pack_price` | REAL | Full box price (`unit_price * units`) | `600.00` |
| `category` | TEXT | `Allopathic` or `Herbal` | `Allopathic` |
| `is_antibiotic` | INTEGER | `1` if antibiotic (requires prescription warning), `0` otherwise | `0` |
| `status` | TEXT | Product status (`active` / `inactive`) | `active` |

---

### 2. `pos_generics` (Clinical & Regulatory Data)
Contains therapeutic guidelines and clinical reference data:
- `generic_id`: Primary Key
- `generic_name`: e.g. *Paracetamol*, *Cefixime*, *Omeprazole*
- `indication`: Medical conditions treated
- `adult_dose`, `child_dose`, `renal_dose`: Dosage guidelines
- `contra_indication`: Conditions when the medicine must not be taken
- `side_effect`: Documented adverse effects
- `precaution`: Warnings and monitoring recommendations
- `mode_of_action`: Pharmacological mechanism
- `interaction`: Known drug-drug interactions

---

### 3. `pos_companies`
Directory of all pharmaceutical companies in Bangladesh:
- `company_id`: Primary Key
- `company_name`: e.g. *Square Pharmaceuticals Ltd.*, *Beximco*, *Incepta*, *Renata*

---

## ⚡ High-Performance POS Queries

### 1. Instant Barcode Scanner Lookup
Triggered when a barcode scanner reads a product:
```sql
SELECT sku, display_name, generic_name, company_name, unit_price, strip_price, pack_price
FROM pos_medicines
WHERE barcode = ? OR sku = ?;
```

---

### 2. Sub-Millisecond Cashier Autocomplete (FTS5 Search)
Uses SQLite's Full-Text Search index for instant, typo-tolerant search across brand names, generics, forms, and companies:
```sql
SELECT m.id, m.sku, m.display_name, m.generic_name, m.company_name, m.unit_price, m.strip_price
FROM pos_medicines_fts f
JOIN pos_medicines m ON f.rowid = m.id
WHERE pos_medicines_fts MATCH 'napa*'
ORDER BY m.brand_name
LIMIT 15;
```

---

### 3. Generic Substitute Finder (Alternative Brands)
When a customer requests an out-of-stock medicine or asks for a cheaper alternative:
```sql
-- Find all alternative brands with the exact same generic, form, and strength
SELECT 
    brand_name,
    display_name,
    company_name,
    unit_price,
    strip_price
FROM pos_medicines
WHERE generic_name = 'Cefixime' 
  AND dosage_form = 'Capsule' 
  AND strength = '200mg'
ORDER BY unit_price ASC;
```

---

### 4. Cashier Selling Quantity Calculation
In Bangladesh pharmacies, medicines are sold in 3 standard quantities:
1. **Loose Pieces / Tablets**: `quantity * unit_price`
2. **Blister Strips**: `strip_quantity * strip_price`
3. **Full Box / Pack**: `pack_quantity * pack_price`

```dart
// Flutter / Dart Pricing Helper Example
double calculateItemTotal({
  required double unitPrice,
  required double stripPrice,
  required double packPrice,
  int looseUnits = 0,
  int strips = 0,
  int boxes = 0,
}) {
  return (looseUnits * unitPrice) + (strips * stripPrice) + (boxes * packPrice);
}
```

---

### 5. Antibiotic Prescription Warning
Show a prominent alert in the POS UI when scanning an antibiotic:
```sql
SELECT brand_name, is_antibiotic 
FROM pos_medicines 
WHERE id = ?;
-- If is_antibiotic == 1, prompt cashier: "⚠️ Antibiotic: Verify Doctor's Prescription (Rx)"
```

---

## 🚀 Integration Guide

### Flutter (sqflite / sqlite3)
1. Copy `techyfi_pos_meds.db` into your Flutter app's `assets/` folder.
2. In `pubspec.yaml`, add:
   ```yaml
   flutter:
     assets:
       - assets/techyfi_pos_meds.db
   ```
3. Use the `sqflite` package to open and query the database locally on the POS tablet/terminal.

### Node.js / Electron / React
```javascript
const Database = require('better-sqlite3');
const db = new Database('Techyfi POS Meds/techyfi_pos_meds.db', { readonly: true });

// Search medicines
function searchMedicines(keyword) {
  const stmt = db.prepare(`
    SELECT m.sku, m.display_name, m.generic_name, m.company_name, m.unit_price, m.strip_price
    FROM pos_medicines_fts f
    JOIN pos_medicines m ON f.rowid = m.id
    WHERE pos_medicines_fts MATCH ?
    LIMIT 20
  `);
  return stmt.all(`${keyword}*`);
}
```

### PostgreSQL / MySQL Import
To import the data into an online cloud POS server (PostgreSQL or MySQL):
1. Run `schema.sql` to create the tables.
2. Import `pos_medicines_master.csv`, `pos_generics_master.csv`, and `pos_companies_master.csv` using `COPY` or `LOAD DATA INFILE`.
