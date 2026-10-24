#!/usr/bin/env python3
"""Build the production-ready Techyfi POS Meds database package."""

import csv
import json
import os
import re
import sqlite3
import sys

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TARGET_DIR = os.path.dirname(os.path.abspath(__file__))
RAW_PHARMA_DIR = os.path.join(TARGET_DIR, "raw_pharma_tables")
SRC_DB_PATH = os.path.join(TARGET_DIR, "techyfi_pos_meds.db")
POS_DB_PATH = os.path.join(TARGET_DIR, "techyfi_pos_meds.db")

ANTIBIOTIC_KEYWORDS = {
    "cefixime", "ceftriaxone", "ciprofloxacin", "azithromycin", "amoxicillin",
    "cefuroxime", "levofloxacin", "metronidazole", "doxycycline", "clarithromycin",
    "cloxacillin", "flucloxacillin", "cephalexin", "meropenem", "ceftazidime",
    "gentamicin", "amikacin", "erythromycin", "cotrimoxazole", "linezolid",
    "vancomycin", "colistin", "moxifloxacin", "ofloxacin", "cefpodoxime",
    "cefaclor", "nitrofurantoin", "ampicillin", "chloramphenicol", "tetracycline"
}


def is_antibiotic_generic(gname):
    if not gname:
        return 0
    gn = gname.lower()
    for kw in ANTIBIOTIC_KEYWORDS:
        if kw in gn:
            return 1
    return 0


def main():
    if not os.path.exists(SRC_DB_PATH):
        print(f"Error: {SRC_DB_PATH} not found.", file=sys.stderr)
        sys.exit(1)

    os.makedirs(TARGET_DIR, exist_ok=True)
    os.makedirs(RAW_PHARMA_DIR, exist_ok=True)

    src_conn = sqlite3.connect(SRC_DB_PATH)
    src_cur = src_conn.cursor()

    dst_conn = sqlite3.connect(POS_DB_PATH)
    dst_cur = dst_conn.cursor()

    print("1. Creating POS database schema...")

    # 1. Companies Table
    dst_cur.execute("""
    CREATE TABLE pos_companies (
        company_id INTEGER PRIMARY KEY,
        company_name TEXT NOT NULL,
        company_order INTEGER DEFAULT 0
    );
    """)
    dst_cur.execute("CREATE INDEX idx_comp_name ON pos_companies(company_name);")

    # 2. Generics Table
    dst_cur.execute("""
    CREATE TABLE pos_generics (
        generic_id INTEGER PRIMARY KEY,
        generic_name TEXT NOT NULL,
        category TEXT DEFAULT 'Allopathic',
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
    """)
    dst_cur.execute("CREATE INDEX idx_gen_name ON pos_generics(generic_name);")

    # 3. Dosage Forms Table
    dst_cur.execute("""
    CREATE TABLE pos_dosage_forms (
        form_id INTEGER PRIMARY KEY AUTOINCREMENT,
        form_name TEXT UNIQUE NOT NULL,
        total_products INTEGER DEFAULT 0
    );
    """)

    # 4. Master Products / Medicines Table
    dst_cur.execute("""
    CREATE TABLE pos_medicines (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sku TEXT UNIQUE NOT NULL,
        barcode TEXT,
        brand_id INTEGER,
        brand_name TEXT NOT NULL,
        display_name TEXT NOT NULL,
        dosage_form TEXT NOT NULL,
        strength TEXT,
        generic_id INTEGER,
        generic_name TEXT,
        company_id INTEGER,
        company_name TEXT,
        unit_price REAL DEFAULT 0.0,
        strip_size INTEGER DEFAULT 10,
        strip_price REAL DEFAULT 0.0,
        pack_size TEXT,
        units_per_pack INTEGER DEFAULT 1,
        pack_price REAL DEFAULT 0.0,
        category TEXT DEFAULT 'Allopathic',
        is_antibiotic INTEGER DEFAULT 0,
        status TEXT DEFAULT 'active'
    );
    """)

    dst_cur.execute("CREATE INDEX idx_med_brand ON pos_medicines(brand_name);")
    dst_cur.execute("CREATE INDEX idx_med_generic ON pos_medicines(generic_name);")
    dst_cur.execute("CREATE INDEX idx_med_company ON pos_medicines(company_name);")
    dst_cur.execute("CREATE INDEX idx_med_form ON pos_medicines(dosage_form);")
    dst_cur.execute("CREATE INDEX idx_med_barcode ON pos_medicines(barcode);")
    dst_cur.execute("CREATE INDEX idx_med_sku ON pos_medicines(sku);")

    # 5. Full Text Search 5 (FTS5) Table for instant search in POS
    dst_cur.execute("""
    CREATE VIRTUAL TABLE pos_medicines_fts USING fts5(
        brand_name,
        generic_name,
        company_name,
        strength,
        dosage_form,
        display_name,
        content='pos_medicines',
        content_rowid='id'
    );
    """)

    # Triggers to keep FTS in sync
    dst_cur.execute("""
    CREATE TRIGGER pos_med_ai AFTER INSERT ON pos_medicines BEGIN
      INSERT INTO pos_medicines_fts(rowid, brand_name, generic_name, company_name, strength, dosage_form, display_name)
      VALUES (new.id, new.brand_name, new.generic_name, new.company_name, new.strength, new.dosage_form, new.display_name);
    END;
    """)

    print("2. Populating Companies and Generics...")
    src_cur.execute("SELECT company_id, company_name, company_order FROM t_company_name")
    companies_data = src_cur.fetchall()
    dst_cur.executemany("INSERT INTO pos_companies VALUES (?,?,?)", companies_data)

    # Insert Allopathic Generics
    src_cur.execute("""
    SELECT generic_id, generic_name, precaution, indication, contra_indication, side_effect,
           pregnancy_category_id, mode_of_action, interaction, pregnancy_category_note,
           adult_dose, child_dose, renal_dose, administration
    FROM t_drug_generic
    """)
    allopathic_generics = src_cur.fetchall()
    dst_cur.executemany("""
    INSERT INTO pos_generics (
        generic_id, generic_name, category, precaution, indication, contra_indication, side_effect,
        pregnancy_category_id, mode_of_action, interaction, pregnancy_category_note,
        adult_dose, child_dose, renal_dose, administration
    ) VALUES (?,?,'Allopathic',?,?,?,?,?,?,?,?,?,?,?,?)
    """, allopathic_generics)

    # Insert Herbal Generics
    src_cur.execute("""
    SELECT generic_id, generic_name, precaution, indication, contraindication, side_effects,
           NULL, mode_of_actions, drug_interaction, NULL,
           dosage, NULL, NULL, NULL
    FROM t_herbal_generic
    """)
    herbal_generics = src_cur.fetchall()
    dst_cur.executemany("""
    INSERT OR IGNORE INTO pos_generics (
        generic_id, generic_name, category, precaution, indication, contra_indication, side_effect,
        pregnancy_category_id, mode_of_action, interaction, pregnancy_category_note,
        adult_dose, child_dose, renal_dose, administration
    ) VALUES (?,?,'Herbal',?,?,?,?,?,?,?,?,?,?,?,?)
    """, herbal_generics)

    # Lookup dictionaries keyed by STRINGS
    company_map = {str(c[0]): c[1] for c in companies_data}
    generic_map = {str(g[0]): g[1] for g in allopathic_generics}
    for hg in herbal_generics:
        generic_map[str(hg[0])] = hg[1]

    def parse_units(pack):
        if not pack:
            return 1
        m = re.search(r"(\d+)\s*[xX*]\s*(\d+)", pack)
        if m:
            return int(m.group(1)) * int(m.group(2))
        m = re.search(r"(\d+)\s*(?:['`]?s)?\s*pack", pack, re.I)
        if m:
            return int(m.group(1))
        m = re.search(r"(\d+)\s*(?:vials?|amps?|bottles?|bots?)", pack, re.I)
        if m:
            return int(m.group(1))
        return 1

    def determine_strip_size(form, units):
        form_lower = (form or "").lower()
        if "tablet" in form_lower or "capsule" in form_lower:
            if units >= 10:
                if units % 10 == 0:
                    return 10
                if units % 14 == 0:
                    return 14
                if units % 6 == 0:
                    return 6
                if units % 7 == 0:
                    return 7
                if units % 8 == 0:
                    return 8
                if units % 4 == 0:
                    return 4
                return 10
            elif units > 1:
                return units
        return 1

    print("3. Processing and normalizing Allopathic & Herbal medicines...")
    src_cur.execute("""
    SELECT brand_id, generic_id, company_id, brand_name, form, strength, price, packsize
    FROM t_drug_brand
    ORDER BY brand_name COLLATE NOCASE
    """)
    allopathic_rows = src_cur.fetchall()

    src_cur.execute("""
    SELECT brand_id, brand_name, generic_id, form, strength, price, packsize, company_id
    FROM t_herbal_brand
    ORDER BY brand_name COLLATE NOCASE
    """)
    herbal_rows = src_cur.fetchall()

    medicines_to_insert = []
    sku_counter = 100000
    form_counts = {}

    for r in allopathic_rows:
        bid, gid, cid, name, form, strength, price_str, pack_str = r
        gname = generic_map.get(str(gid), "")
        cname = company_map.get(str(cid), "")
        is_ab = is_antibiotic_generic(gname)

        prices = [p.strip() for p in (price_str or "").split(",") if p.strip()]
        packs = [p.strip() for p in (pack_str or "").split(",") if p.strip()]

        st_str = strength or ""
        fm_str = form or ""

        if len(prices) > 1 and len(prices) == len(packs):
            for p_sub, pack_sub in zip(prices, packs):
                sku_counter += 1
                sku = f"MED-{sku_counter}"
                barcode = f"890{sku_counter}"
                try:
                    u_price = round(float(p_sub), 2)
                except Exception:
                    u_price = 0.0
                units = parse_units(pack_sub)
                p_price = round(u_price * units, 2)
                s_size = determine_strip_size(form, units)
                s_price = round(u_price * s_size, 2) if s_size > 1 else u_price

                disp = f"{name} {st_str} {fm_str} ({pack_sub})".replace("  ", " ").strip()
                medicines_to_insert.append((
                    sku, barcode, int(bid) if str(bid).isdigit() else None, name, disp, fm_str, st_str,
                    int(gid) if str(gid).isdigit() else None, gname,
                    int(cid) if str(cid).isdigit() else None, cname,
                    u_price, s_size, s_price, pack_sub, units, p_price,
                    "Allopathic", is_ab, "active"
                ))
                form_counts[fm_str or "Other"] = form_counts.get(fm_str or "Other", 0) + 1
        else:
            sku_counter += 1
            sku = f"MED-{sku_counter}"
            barcode = f"890{sku_counter}"
            first_price = prices[0] if prices else ""
            try:
                u_price = round(float(first_price), 2)
            except Exception:
                u_price = 0.0
            units = parse_units(pack_str)
            p_price = round(u_price * units, 2)
            s_size = determine_strip_size(form, units)
            s_price = round(u_price * s_size, 2) if s_size > 1 else u_price

            disp = f"{name} {st_str} {fm_str}".replace("  ", " ").strip()
            medicines_to_insert.append((
                sku, barcode, int(bid) if str(bid).isdigit() else None, name, disp, fm_str, st_str,
                int(gid) if str(gid).isdigit() else None, gname,
                int(cid) if str(cid).isdigit() else None, cname,
                u_price, s_size, s_price, pack_str or "", units, p_price,
                "Allopathic", is_ab, "active"
            ))
            form_counts[fm_str or "Other"] = form_counts.get(fm_str or "Other", 0) + 1

    for r in herbal_rows:
        bid, name, gid, form, strength, price_str, pack_str, cid = r
        gname = generic_map.get(str(gid), "Herbal Formulation")
        cname = company_map.get(str(cid), "")

        prices = [p.strip() for p in (price_str or "").split(",") if p.strip()]
        packs = [p.strip() for p in (pack_str or "").split(",") if p.strip()]

        st_str = strength or ""
        fm_str = form or ""

        if len(prices) > 1 and len(prices) == len(packs):
            for p_sub, pack_sub in zip(prices, packs):
                sku_counter += 1
                sku = f"HERB-{sku_counter}"
                barcode = f"891{sku_counter}"
                try:
                    u_price = round(float(p_sub), 2)
                except Exception:
                    u_price = 0.0
                units = parse_units(pack_sub)
                p_price = round(u_price * units, 2)
                disp = f"{name} {st_str} {fm_str} ({pack_sub})".replace("  ", " ").strip()
                medicines_to_insert.append((
                    sku, barcode, int(bid) if str(bid).isdigit() else None, name, disp, fm_str, st_str,
                    int(gid) if str(gid).isdigit() else None, gname,
                    int(cid) if str(cid).isdigit() else None, cname,
                    u_price, 1, u_price, pack_sub, units, p_price,
                    "Herbal", 0, "active"
                ))
                form_counts[fm_str or "Other"] = form_counts.get(fm_str or "Other", 0) + 1
        else:
            sku_counter += 1
            sku = f"HERB-{sku_counter}"
            barcode = f"891{sku_counter}"
            first_price = prices[0] if prices else ""
            try:
                u_price = round(float(first_price), 2)
            except Exception:
                u_price = 0.0
            units = parse_units(pack_str)
            p_price = round(u_price * units, 2)
            disp = f"{name} {st_str} {fm_str}".replace("  ", " ").strip()
            medicines_to_insert.append((
                sku, barcode, int(bid) if str(bid).isdigit() else None, name, disp, fm_str, st_str,
                int(gid) if str(gid).isdigit() else None, gname,
                int(cid) if str(cid).isdigit() else None, cname,
                u_price, 1, u_price, pack_str or "", units, p_price,
                "Herbal", 0, "active"
            ))
            form_counts[fm_str or "Other"] = form_counts.get(fm_str or "Other", 0) + 1

    dst_cur.executemany("""
    INSERT INTO pos_medicines (
        sku, barcode, brand_id, brand_name, display_name, dosage_form, strength,
        generic_id, generic_name, company_id, company_name, unit_price, strip_size,
        strip_price, pack_size, units_per_pack, pack_price, category, is_antibiotic, status
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    """, medicines_to_insert)

    for f, count in sorted(form_counts.items(), key=lambda x: -x[1]):
        dst_cur.execute("INSERT INTO pos_dosage_forms (form_name, total_products) VALUES (?,?)", (f, count))

    dst_conn.commit()
    print(f"  Inserted {len(medicines_to_insert):,} products into pos_medicines.")

    # 4. Exports
    print("4. Exporting POS CSV & JSON formats...")
    dst_cur.execute("SELECT * FROM pos_medicines ORDER BY brand_name COLLATE NOCASE")
    all_meds = dst_cur.fetchall()
    cols = [desc[0] for desc in dst_cur.description]

    master_csv = os.path.join(TARGET_DIR, "pos_medicines_master.csv")
    with open(master_csv, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(cols)
        for row in all_meds:
            w.writerow(list(row))
    print(f"  Exported {master_csv}")

    master_json = os.path.join(TARGET_DIR, "pos_medicines_master.json")
    json_records = [dict(zip(cols, row)) for row in all_meds]
    with open(master_json, "w", encoding="utf-8") as f:
        json.dump(json_records, f, indent=2, ensure_ascii=False)
    print(f"  Exported {master_json}")

    dst_cur.execute("SELECT * FROM pos_generics ORDER BY generic_name COLLATE NOCASE")
    gen_rows = dst_cur.fetchall()
    gen_cols = [desc[0] for desc in dst_cur.description]
    with open(os.path.join(TARGET_DIR, "pos_generics_master.csv"), "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(gen_cols)
        for row in gen_rows:
            w.writerow(list(row))

    with open(os.path.join(TARGET_DIR, "pos_generics_master.json"), "w", encoding="utf-8") as f:
        json.dump([dict(zip(gen_cols, row)) for row in gen_rows], f, indent=2, ensure_ascii=False)

    dst_cur.execute("SELECT * FROM pos_companies ORDER BY company_name COLLATE NOCASE")
    comp_rows = dst_cur.fetchall()
    comp_cols = [desc[0] for desc in dst_cur.description]
    with open(os.path.join(TARGET_DIR, "pos_companies_master.csv"), "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(comp_cols)
        for row in comp_rows:
            w.writerow(list(row))

    print("5. Exporting raw pharmaceutical tables...")
    src_tables = [t[0] for t in src_cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name != 'android_metadata'").fetchall()]
    for t in src_tables:
        src_cur.execute(f"SELECT * FROM {t}")
        t_rows = src_cur.fetchall()
        if not t_rows:
            continue
        t_cols = [desc[0] for desc in src_cur.description]
        with open(os.path.join(RAW_PHARMA_DIR, f"{t}.csv"), "w", newline="", encoding="utf-8-sig") as f:
            w = csv.writer(f)
            w.writerow(t_cols)
            for r in t_rows:
                w.writerow(list(r))

    # 6. Export schema.sql
    schema_sql_path = os.path.join(TARGET_DIR, "schema.sql")
    with open(schema_sql_path, "w", encoding="utf-8") as f:
        f.write("""-- ====================================================================
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
""")
    print(f"  Exported {schema_sql_path}")

    src_conn.close()
    dst_conn.close()
    print("Done building Techyfi POS Meds database package!")


if __name__ == "__main__":
    main()
