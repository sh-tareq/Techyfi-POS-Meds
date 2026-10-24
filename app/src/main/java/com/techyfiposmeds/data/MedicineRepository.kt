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
)

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

        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            // Check if FTS5 search is usable
            val sanitized = trimmed.replace("'", "''").replace("\"", "")
            whereClauses.add("(m.brand_name LIKE ? OR m.generic_name LIKE ? OR m.company_name LIKE ? OR m.barcode LIKE ? OR m.sku LIKE ?)")
            val wildcard = "%$sanitized%"
            args.add(wildcard)
            args.add(wildcard)
            args.add(wildcard)
            args.add(wildcard)
            args.add(wildcard)
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
        val sql = "SELECT m.* FROM pos_medicines m $whereSql ORDER BY m.brand_name ASC, m.unit_price ASC LIMIT $limit"

        try {
            val cursor = d.rawQuery(sql, args.toTypedArray())
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
                put("generic_name", item.genericName)
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
