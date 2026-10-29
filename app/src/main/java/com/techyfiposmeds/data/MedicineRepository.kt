package com.techyfiposmeds.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class MedicineItem(
    val id: Long,
    val sku: String,
    val barcode: String?,
    val brandName: String,
    val displayName: String,
    val dosageForm: String,
    val strength: String?,
    val genericId: Long?,
    val genericName: String?,
    val companyId: Long?,
    val companyName: String?,
    val unitPrice: Double,
    val stripSize: Int,
    val stripPrice: Double,
    val packSize: String?,
    val unitsPerPack: Int,
    val packPrice: Double,
    val category: String,
    val isAntibiotic: Boolean,
    val shelfLocation: String?,
    val batchNumber: String?,
    val expiryDate: String?,
    val stockQty: Double,
    val reorderLevel: Int
) {
    val isStripApplicable: Boolean
        get() = !com.techyfiposmeds.data.db.isLiquidOrTopicalDosageForm(dosageForm) && (stripSize > 1 || stripPrice > 0.0)
}

data class ClinicalGeneric(
    val genericId: Long,
    val genericName: String,
    val category: String?,
    val indication: String?,
    val adultDose: String?,
    val childDose: String?,
    val renalDose: String?,
    val contraIndication: String?,
    val sideEffect: String?,
    val precaution: String?,
    val modeOfAction: String?,
    val interaction: String?,
    val administration: String?
)

class MedicineRepository private constructor(private val context: Context) {

    private var db: SQLiteDatabase? = null

    companion object {
        private const val DB_NAME = "techyfi_pos_meds.db"
        private const val TAG = "MedicineRepository"

        @Volatile
        private var INSTANCE: MedicineRepository? = null

        fun getInstance(context: Context): MedicineRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MedicineRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    @Synchronized
    private fun getReadableDb(): SQLiteDatabase {
        if (db == null || !db!!.isOpen) {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists() || dbFile.length() < 1000L) {
                dbFile.parentFile?.mkdirs()
                try {
                    context.assets.open(DB_NAME).use { input ->
                        FileOutputStream(dbFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Successfully copied $DB_NAME (${dbFile.length()} bytes) from assets.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed copying asset database: ${e.message}", e)
                }
            }
            try {
                db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                ensurePharmacyColumns(db!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening SQLite database: ${e.message}", e)
                throw e
            }
        }
        return db!!
    }

    suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        try {
            getReadableDb()
        } catch (e: Exception) {
            Log.e(TAG, "Database initialization error: ${e.message}", e)
        }
    }

    private fun ensurePharmacyColumns(database: SQLiteDatabase) {
        try {
            val cursor = database.rawQuery("PRAGMA table_info(pos_medicines)", null)
            val columns = mutableSetOf<String>()
            cursor.use {
                val nameIdx = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIdx >= 0) columns.add(cursor.getString(nameIdx))
                }
            }

            if (!columns.contains("shelf_location")) {
                database.execSQL("ALTER TABLE pos_medicines ADD COLUMN shelf_location TEXT")
            }
            if (!columns.contains("batch_number")) {
                database.execSQL("ALTER TABLE pos_medicines ADD COLUMN batch_number TEXT")
            }
            if (!columns.contains("expiry_date")) {
                database.execSQL("ALTER TABLE pos_medicines ADD COLUMN expiry_date TEXT")
            }
            if (!columns.contains("stock_qty")) {
                database.execSQL("ALTER TABLE pos_medicines ADD COLUMN stock_qty REAL DEFAULT 100.0")
            }
            if (!columns.contains("reorder_level")) {
                database.execSQL("ALTER TABLE pos_medicines ADD COLUMN reorder_level INTEGER DEFAULT 20")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed adding columns to pos_medicines: ${e.message}", e)
        }
    }

    suspend fun searchMedicines(
        query: String = "",
        dosageForm: String? = null,
        isAntibiotic: Boolean? = null,
        nearExpiryOnly: Boolean = false,
        company: String? = null,
        limit: Int = 100
    ): List<MedicineItem> = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val results = mutableListOf<MedicineItem>()

        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        val orderArgs = mutableListOf<String>()

        val trimmed = query.trim()
        val tokens = if (trimmed.isNotBlank()) {
            trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        if (tokens.isNotEmpty()) {
            // Each token must match at least one of brand_name, generic_name, strength, company_name, dosage_form, display_name
            // This accurately matches queries like "Napa 500", "Beximco Napa", "Paracetamol 500", "Square Napa"
            for (token in tokens) {
                val wildcard = "%$token%"
                whereClauses.add("(m.brand_name LIKE ? OR m.generic_name LIKE ? OR m.strength LIKE ? OR m.company_name LIKE ? OR m.dosage_form LIKE ? OR m.display_name LIKE ?)")
                args.add(wildcard)
                args.add(wildcard)
                args.add(wildcard)
                args.add(wildcard)
                args.add(wildcard)
                args.add(wildcard)
            }
        }

        if (!dosageForm.isNullOrBlank() && dosageForm != "all") {
            whereClauses.add("m.dosage_form = ?")
            args.add(dosageForm)
        }

        if (isAntibiotic != null && isAntibiotic) {
            whereClauses.add("m.is_antibiotic = 1")
        }

        if (!company.isNullOrBlank() && company != "all") {
            whereClauses.add("m.company_name = ?")
            args.add(company)
        }

        if (nearExpiryOnly) {
            whereClauses.add("m.expiry_date IS NOT NULL AND m.expiry_date != ''")
        }

        val whereSql = if (whereClauses.isNotEmpty()) "WHERE " + whereClauses.joinToString(" AND ") else ""

        val orderByClause = if (trimmed.isNotBlank()) {
            val exact = trimmed.lowercase()
            val prefix = "$exact%"
            orderArgs.add(exact)
            orderArgs.add(prefix)
            orderArgs.add(prefix)
            """
            ORDER BY 
              CASE 
                WHEN LOWER(m.brand_name) = ? THEN 1
                WHEN LOWER(m.brand_name) LIKE ? THEN 2
                WHEN LOWER(m.generic_name) LIKE ? THEN 3
                ELSE 4
              END,
              m.brand_name COLLATE NOCASE ASC,
              m.strength ASC,
              m.unit_price ASC
            """.trimIndent()
        } else {
            // By default, sort medicines alphabetically A-Z
            "ORDER BY m.brand_name COLLATE NOCASE ASC, m.strength ASC"
        }

        val sql = "SELECT m.* FROM pos_medicines m $whereSql $orderByClause LIMIT $limit"
        val allArgs = (args + orderArgs).toTypedArray()

        try {
            val cursor = d.rawQuery(sql, allArgs)
            cursor.use {
                while (cursor.moveToNext()) {
                    results.add(parseMedicine(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing searchMedicines: ${e.message}", e)
        }

        results
    }

    suspend fun getMedicineByBarcodeOrSku(code: String): MedicineItem? = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val trimmed = code.trim()
        if (trimmed.isBlank()) return@withContext null

        val sql = "SELECT * FROM pos_medicines WHERE barcode = ? OR sku = ? LIMIT 1"
        try {
            d.rawQuery(sql, arrayOf(trimmed, trimmed)).use { cursor ->
                if (cursor.moveToNext()) {
                    return@withContext parseMedicine(cursor)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up medicine by barcode: ${e.message}", e)
        }
        null
    }

    suspend fun getMedicineById(id: Long): MedicineItem? = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val sql = "SELECT * FROM pos_medicines WHERE id = ? LIMIT 1"
        try {
            d.rawQuery(sql, arrayOf(id.toString())).use { cursor ->
                if (cursor.moveToNext()) {
                    return@withContext parseMedicine(cursor)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up medicine by id: ${e.message}", e)
        }
        null
    }

    suspend fun findSubstitutes(
        genericName: String,
        dosageForm: String,
        strength: String?,
        excludeId: Long,
        limit: Int = 10
    ): List<MedicineItem> = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val results = mutableListOf<MedicineItem>()

        if (genericName.isBlank()) return@withContext emptyList()

        val whereClauses = mutableListOf("generic_name = ?", "dosage_form = ?", "id != ?")
        val args = mutableListOf(genericName, dosageForm, excludeId.toString())

        if (!strength.isNullOrBlank()) {
            whereClauses.add("strength = ?")
            args.add(strength)
        }

        val sql = """
            SELECT * FROM pos_medicines 
            WHERE ${whereClauses.joinToString(" AND ")}
            ORDER BY unit_price ASC
            LIMIT $limit
        """.trimIndent()

        try {
            d.rawQuery(sql, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(parseMedicine(cursor))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding substitutes: ${e.message}", e)
        }

        results
    }

    suspend fun getClinicalGenericInfo(genericId: Long): ClinicalGeneric? = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val sql = "SELECT * FROM pos_generics WHERE generic_id = ? LIMIT 1"
        try {
            d.rawQuery(sql, arrayOf(genericId.toString())).use { cursor ->
                if (cursor.moveToNext()) {
                    return@withContext ClinicalGeneric(
                        genericId = cursor.getLong(cursor.getColumnIndexOrThrow("generic_id")),
                        genericName = cursor.getString(cursor.getColumnIndexOrThrow("generic_name")),
                        category = cursor.getStringOrNull("category"),
                        indication = cursor.getStringOrNull("indication"),
                        adultDose = cursor.getStringOrNull("adult_dose"),
                        childDose = cursor.getStringOrNull("child_dose"),
                        renalDose = cursor.getStringOrNull("renal_dose"),
                        contraIndication = cursor.getStringOrNull("contra_indication"),
                        sideEffect = cursor.getStringOrNull("side_effect"),
                        precaution = cursor.getStringOrNull("precaution"),
                        modeOfAction = cursor.getStringOrNull("mode_of_action"),
                        interaction = cursor.getStringOrNull("interaction"),
                        administration = cursor.getStringOrNull("administration")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading generic info: ${e.message}", e)
        }
        null
    }

    suspend fun getClinicalGenericInfoByName(genericName: String): ClinicalGeneric? = withContext(Dispatchers.IO) {
        if (genericName.isBlank()) return@withContext null
        initializeDatabase()
        val d = getReadableDb()
        val cleanName = genericName.trim().lowercase()
        val sql = "SELECT * FROM pos_generics WHERE LOWER(generic_name) = ? OR LOWER(generic_name) LIKE ? LIMIT 1"
        try {
            d.rawQuery(sql, arrayOf(cleanName, "%$cleanName%")).use { cursor ->
                if (cursor.moveToNext()) {
                    return@withContext ClinicalGeneric(
                        genericId = cursor.getLong(cursor.getColumnIndexOrThrow("generic_id")),
                        genericName = cursor.getString(cursor.getColumnIndexOrThrow("generic_name")),
                        category = cursor.getStringOrNull("category"),
                        indication = cursor.getStringOrNull("indication"),
                        adultDose = cursor.getStringOrNull("adult_dose"),
                        childDose = cursor.getStringOrNull("child_dose"),
                        renalDose = cursor.getStringOrNull("renal_dose"),
                        contraIndication = cursor.getStringOrNull("contra_indication"),
                        sideEffect = cursor.getStringOrNull("side_effect"),
                        precaution = cursor.getStringOrNull("precaution"),
                        modeOfAction = cursor.getStringOrNull("mode_of_action"),
                        interaction = cursor.getStringOrNull("interaction"),
                        administration = cursor.getStringOrNull("administration")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading generic info by name: ${e.message}", e)
        }
        null
    }

    suspend fun getAllDosageForms(): List<String> = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val list = mutableListOf<String>()
        try {
            d.rawQuery("SELECT dosage_form, count(*) as cnt FROM pos_medicines GROUP BY dosage_form ORDER BY cnt DESC LIMIT 15", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val form = cursor.getString(0)
                    if (!form.isNullOrBlank()) list.add(form)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading dosage forms: ${e.message}", e)
        }
        list
    }

    suspend fun getAllCompanies(): List<String> = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val list = mutableListOf<String>()
        try {
            d.rawQuery("SELECT company_name FROM pos_companies ORDER BY company_order ASC, company_name ASC LIMIT 30", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val comp = cursor.getString(0)
                    if (!comp.isNullOrBlank()) list.add(comp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading companies: ${e.message}", e)
        }
        list
    }

    suspend fun searchGenerics(query: String, limit: Int = 12): List<String> = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val list = mutableListOf<String>()
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        try {
            d.rawQuery(
                "SELECT generic_name FROM pos_generics WHERE generic_name LIKE ? ORDER BY generic_name ASC LIMIT ?",
                arrayOf("%$trimmed%", limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank() && !list.contains(name)) list.add(name)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching generics: ${e.message}", e)
        }
        list
    }

    suspend fun searchCompanies(query: String = "", limit: Int = 15): List<String> = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val list = mutableListOf<String>()
        val trimmed = query.trim()
        try {
            val sql = if (trimmed.isBlank()) {
                "SELECT company_name FROM (SELECT company_name, count(*) as cnt FROM pos_medicines WHERE company_name IS NOT NULL AND company_name != '' GROUP BY company_name ORDER BY cnt DESC LIMIT ?)"
            } else {
                "SELECT DISTINCT company_name FROM pos_medicines WHERE company_name LIKE ? ORDER BY company_name ASC LIMIT ?"
            }
            val args = if (trimmed.isBlank()) arrayOf(limit.toString()) else arrayOf("%$trimmed%", limit.toString())
            d.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    val comp = cursor.getString(0)
                    if (!comp.isNullOrBlank() && !list.contains(comp)) list.add(comp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching companies: ${e.message}", e)
        }
        list
    }

    suspend fun getDistinctShelfLocations(limit: Int = 20): List<String> = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val list = mutableListOf<String>()
        try {
            d.rawQuery(
                "SELECT DISTINCT shelf_location FROM pos_medicines WHERE shelf_location IS NOT NULL AND TRIM(shelf_location) != '' ORDER BY shelf_location ASC LIMIT ?",
                arrayOf(limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val loc = cursor.getString(0)
                    if (!loc.isNullOrBlank() && !list.contains(loc)) list.add(loc)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading shelf locations: ${e.message}", e)
        }
        list
    }

    suspend fun updateMedicineDetails(
        id: Long,
        unitPrice: Double,
        stripSize: Int,
        stripPrice: Double,
        packPrice: Double,
        shelfLocation: String?,
        batchNumber: String?,
        expiryDate: String?,
        stockQty: Double? = null
    ): Boolean = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        try {
            val cv = ContentValues().apply {
                put("unit_price", unitPrice)
                put("strip_size", stripSize)
                put("strip_price", stripPrice)
                put("pack_price", packPrice)
                put("shelf_location", shelfLocation)
                put("batch_number", batchNumber)
                put("expiry_date", expiryDate)
                if (stockQty != null) {
                    put("stock_qty", stockQty)
                }
            }
            d.update("pos_medicines", cv, "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error updating medicine details: ${e.message}", e)
            false
        }
    }

    suspend fun adjustStock(id: Long, delta: Double): Boolean = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        try {
            d.execSQL("UPDATE pos_medicines SET stock_qty = MAX(0.0, stock_qty + ?) WHERE id = ?", arrayOf(delta, id))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting stock: ${e.message}", e)
            false
        }
    }

    suspend fun adjustStockByBarcodeOrSku(code: String, delta: Double): Boolean = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        val trimmed = code.trim()
        if (trimmed.isBlank()) return@withContext false
        try {
            d.execSQL("UPDATE pos_medicines SET stock_qty = MAX(0.0, stock_qty + ?) WHERE barcode = ? OR sku = ?", arrayOf(delta, trimmed, trimmed))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting stock by barcode/sku: ${e.message}", e)
            false
        }
    }

    suspend fun upsertClinicalGeneric(
        genericName: String,
        indication: String?,
        adultDose: String?,
        childDose: String?,
        contraIndication: String?,
        sideEffect: String?,
        precaution: String?
    ): Long = withContext(Dispatchers.IO) {
        if (genericName.isBlank()) return@withContext -1L
        initializeDatabase()
        val d = getReadableDb()
        val trimmedName = genericName.trim()
        try {
            var existingId: Long? = null
            d.rawQuery("SELECT generic_id FROM pos_generics WHERE LOWER(generic_name) = ? LIMIT 1", arrayOf(trimmedName.lowercase())).use { cursor ->
                if (cursor.moveToNext()) {
                    existingId = cursor.getLong(0)
                }
            }

            if (existingId != null && existingId!! > 0L) {
                val cv = ContentValues().apply {
                    if (!indication.isNullOrBlank()) put("indication", indication.trim())
                    if (!adultDose.isNullOrBlank()) put("adult_dose", adultDose.trim())
                    if (!childDose.isNullOrBlank()) put("child_dose", childDose.trim())
                    if (!contraIndication.isNullOrBlank()) put("contra_indication", contraIndication.trim())
                    if (!sideEffect.isNullOrBlank()) put("side_effect", sideEffect.trim())
                    if (!precaution.isNullOrBlank()) put("precaution", precaution.trim())
                }
                if (cv.size() > 0) {
                    d.update("pos_generics", cv, "generic_id = ?", arrayOf(existingId.toString()))
                }
                existingId!!
            } else {
                val cv = ContentValues().apply {
                    put("generic_name", trimmedName)
                    put("category", "Allopathic")
                    put("indication", indication?.trim())
                    put("adult_dose", adultDose?.trim())
                    put("child_dose", childDose?.trim())
                    put("contra_indication", contraIndication?.trim())
                    put("side_effect", sideEffect?.trim())
                    put("precaution", precaution?.trim())
                }
                d.insert("pos_generics", null, cv)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting clinical generic: ${e.message}", e)
            -1L
        }
    }

    suspend fun getOrCreateCompanyId(companyName: String): Long? = withContext(Dispatchers.IO) {
        if (companyName.isBlank()) return@withContext null
        initializeDatabase()
        val d = getReadableDb()
        val trimmed = companyName.trim()
        try {
            d.rawQuery("SELECT company_id FROM pos_companies WHERE LOWER(company_name) = ? LIMIT 1", arrayOf(trimmed.lowercase())).use { cursor ->
                if (cursor.moveToNext()) {
                    return@withContext cursor.getLong(0)
                }
            }
            val cv = ContentValues().apply {
                put("company_name", trimmed)
                put("company_order", 999)
            }
            val newId = d.insert("pos_companies", null, cv)
            if (newId > 0) newId else null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving company id: ${e.message}", e)
            null
        }
    }

    suspend fun insertCustomMedicine(item: MedicineItem): Long = withContext(Dispatchers.IO) {
        initializeDatabase()
        val d = getReadableDb()
        try {
            val cv = ContentValues().apply {
                put("sku", item.sku)
                put("barcode", item.barcode)
                put("brand_name", item.brandName)
                put("display_name", item.displayName)
                put("dosage_form", item.dosageForm)
                put("strength", item.strength)
                if (item.genericId != null && item.genericId > 0) {
                    put("generic_id", item.genericId)
                }
                put("generic_name", item.genericName)
                if (item.companyId != null && item.companyId > 0) {
                    put("company_id", item.companyId)
                }
                put("company_name", item.companyName)
                put("unit_price", item.unitPrice)
                put("strip_size", item.stripSize)
                put("strip_price", item.stripPrice)
                put("pack_size", item.packSize)
                put("units_per_pack", item.unitsPerPack)
                put("pack_price", item.packPrice)
                put("category", item.category)
                put("is_antibiotic", if (item.isAntibiotic) 1 else 0)
                put("shelf_location", item.shelfLocation)
                put("batch_number", item.batchNumber)
                put("expiry_date", item.expiryDate)
                put("stock_qty", item.stockQty)
                put("reorder_level", item.reorderLevel)
                put("status", "active")
            }
            d.insert("pos_medicines", null, cv)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting custom medicine: ${e.message}", e)
            -1L
        }
    }

    private fun parseMedicine(cursor: Cursor): MedicineItem {
        return MedicineItem(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            sku = cursor.getString(cursor.getColumnIndexOrThrow("sku")),
            barcode = cursor.getStringOrNull("barcode"),
            brandName = cursor.getString(cursor.getColumnIndexOrThrow("brand_name")),
            displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
            dosageForm = cursor.getString(cursor.getColumnIndexOrThrow("dosage_form")),
            strength = cursor.getStringOrNull("strength"),
            genericId = cursor.getLongOrNull("generic_id"),
            genericName = cursor.getStringOrNull("generic_name"),
            companyId = cursor.getLongOrNull("company_id"),
            companyName = cursor.getStringOrNull("company_name"),
            unitPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("unit_price")),
            stripSize = cursor.getIntOrZero("strip_size").let { if (it <= 0) 10 else it },
            stripPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("strip_price")),
            packSize = cursor.getStringOrNull("pack_size"),
            unitsPerPack = cursor.getIntOrZero("units_per_pack").let { if (it <= 0) 100 else it },
            packPrice = cursor.getDouble(cursor.getColumnIndexOrThrow("pack_price")),
            category = cursor.getStringOrNull("category") ?: "Allopathic",
            isAntibiotic = cursor.getIntOrZero("is_antibiotic") == 1,
            shelfLocation = cursor.getStringOrNull("shelf_location"),
            batchNumber = cursor.getStringOrNull("batch_number"),
            expiryDate = cursor.getStringOrNull("expiry_date"),
            stockQty = cursor.getDoubleOrZero("stock_qty"),
            reorderLevel = cursor.getIntOrZero("reorder_level").let { if (it <= 0) 20 else it }
        )
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else null
    }

    private fun Cursor.getIntOrZero(column: String): Int {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else 0
    }

    private fun Cursor.getDoubleOrZero(column: String): Double {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getDouble(idx) else 0.0
    }
}
