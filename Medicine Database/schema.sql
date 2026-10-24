-- ====================================================================
-- Techyfi POS Medicine Database Schema (SQLite / PostgreSQL / MySQL)
-- Designed for High-Performance Pharmacy Point of Sale Applications
-- ====================================================================

-- 1. Master Companies / Manufacturers
CREATE TABLE IF NOT EXISTS pos_companies (
    company_id INTEGER PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    company_order INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_comp_name ON pos_companies(company_name);

-- 2. Master Generics & Clinical Reference
CREATE TABLE IF NOT EXISTS pos_generics (
    generic_id INTEGER PRIMARY KEY,
    generic_name VARCHAR(255) NOT NULL,
    category VARCHAR(50) DEFAULT 'Allopathic',
    precaution TEXT,
    indication TEXT,
    contra_indication TEXT,
    side_effect TEXT,
    pregnancy_category_id INTEGER,
    mode_of_action TEXT,
    interaction TEXT,
    pregnancy_category_note TEXT,
    adult_dose TEXT,
    child_dose TEXT,
    renal_dose TEXT,
    administration TEXT
);
CREATE INDEX IF NOT EXISTS idx_gen_name ON pos_generics(generic_name);

-- 3. Master Products / Medicines Catalog
CREATE TABLE IF NOT EXISTS pos_medicines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sku VARCHAR(64) UNIQUE NOT NULL,
    barcode VARCHAR(64),
    brand_id INTEGER,
    brand_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    dosage_form VARCHAR(100) NOT NULL,
    strength VARCHAR(100),
    generic_id INTEGER,
    generic_name VARCHAR(255),
    company_id INTEGER,
    company_name VARCHAR(255),
    unit_price DECIMAL(10, 2) DEFAULT 0.00,
    strip_size INTEGER DEFAULT 10,
    strip_price DECIMAL(10, 2) DEFAULT 0.00,
    pack_size VARCHAR(100),
    units_per_pack INTEGER DEFAULT 1,
    pack_price DECIMAL(10, 2) DEFAULT 0.00,
    category VARCHAR(50) DEFAULT 'Allopathic',
    is_antibiotic SMALLINT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active'
);

CREATE INDEX IF NOT EXISTS idx_med_brand ON pos_medicines(brand_name);
CREATE INDEX IF NOT EXISTS idx_med_generic ON pos_medicines(generic_name);
CREATE INDEX IF NOT EXISTS idx_med_company ON pos_medicines(company_name);
CREATE INDEX IF NOT EXISTS idx_med_form ON pos_medicines(dosage_form);
CREATE INDEX IF NOT EXISTS idx_med_barcode ON pos_medicines(barcode);
CREATE INDEX IF NOT EXISTS idx_med_sku ON pos_medicines(sku);

-- 4. Full Text Search 5 (FTS5) Table (SQLite only)
CREATE VIRTUAL TABLE IF NOT EXISTS pos_medicines_fts USING fts5(
    brand_name,
    generic_name,
    company_name,
    strength,
    dosage_form,
    display_name,
    content='pos_medicines',
    content_rowid='id'
);
