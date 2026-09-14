package com.example.techyfipos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("language")
        val SHOP_NAME_KEY = stringPreferencesKey("shop_name")
        val SHOP_ADDRESS_KEY = stringPreferencesKey("shop_address")
        val SHOP_PHONE_KEY = stringPreferencesKey("shop_phone")
        val AUTO_BACKUP_KEY = booleanPreferencesKey("auto_backup")
        val LOCAL_AUTO_BACKUP_KEY = booleanPreferencesKey("local_auto_backup")
        val AUTO_BACKUP_FREQUENCY_KEY = stringPreferencesKey("auto_backup_frequency")
        val DRIVE_AUTO_BACKUP_FREQUENCY_KEY = stringPreferencesKey("drive_auto_backup_frequency")
        val CART_SNAPSHOT_KEY = stringPreferencesKey("cart_snapshot")
        val SHOP_LOGO_URI_KEY = stringPreferencesKey("shop_logo_uri")
        val FACEBOOK_URL_KEY = stringPreferencesKey("facebook_url")
        val QR_CODE_INFO_KEY = stringPreferencesKey("qr_code_info")
        val CASHIER_NAME_KEY = stringPreferencesKey("cashier_name")
        val INVOICE_SETTINGS_DATA_KEY = stringPreferencesKey("invoice_settings_data_json")
        val LAST_BACKUP_TIMESTAMP_KEY = longPreferencesKey("last_backup_timestamp")
        val LAST_LOCAL_BACKUP_TIMESTAMP_KEY = longPreferencesKey("last_local_backup_timestamp")
        val LAST_DRIVE_BACKUP_TIMESTAMP_KEY = longPreferencesKey("last_drive_backup_timestamp")
        val DRIVE_RETENTION_COUNT_KEY = androidx.datastore.preferences.core.intPreferencesKey("drive_retention_count")
        val DRIVE_NETWORK_POLICY_KEY = stringPreferencesKey("drive_network_policy")
        val DRIVE_BACKUP_TIME_KEY = stringPreferencesKey("drive_backup_time")
        val DRIVE_ACCOUNT_ID_KEY = stringPreferencesKey("drive_account_id")
        val DRIVE_ACCOUNT_EMAIL_KEY = stringPreferencesKey("drive_account_email")
        val QUICK_SALE_PRESETS_KEY = stringPreferencesKey("quick_sale_presets_json")
        val NOTE_PRESETS_KEY = stringPreferencesKey("note_presets_list_json")
        val DUE_REMINDER_TEMPLATES_KEY = stringPreferencesKey("due_reminder_templates_json")
        val PRODUCT_CATEGORIES_KEY = stringPreferencesKey("product_categories_list_json")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val UI_LAYOUT_MODE_KEY = stringPreferencesKey("ui_layout_mode")
        val RETURN_AUDIT_PRESETS_KEY = stringPreferencesKey("custom_audit_presets_json")
        val DEFAULT_COST_PERCENTAGE_KEY = androidx.datastore.preferences.core.doublePreferencesKey("default_cost_percentage")
        val DEFAULT_NOTE_PRESETS = listOf(
            "No Warranty",
            "7 Days Replacement",
            "30 Days Service",
            "6 Months Warranty",
            "1 Year Warranty",
            "2 Years Warranty",
            "Fragile / Handle With Care"
        )
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE_KEY] ?: "SYSTEM"
    }

    val uiLayoutModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[UI_LAYOUT_MODE_KEY] ?: "AUTO"
    }

    val defaultCostPercentageFlow: Flow<Double> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_COST_PERCENTAGE_KEY] ?: 100.0
    }

    val driveRetentionCountFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DRIVE_RETENTION_COUNT_KEY] ?: 7
    }

    val driveNetworkPolicyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DRIVE_NETWORK_POLICY_KEY] ?: "WIFI_ONLY"
    }

    val driveBackupTimeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DRIVE_BACKUP_TIME_KEY] ?: "02:00"
    }

    val driveAccountIdFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DRIVE_ACCOUNT_ID_KEY] ?: ""
    }

    val driveAccountEmailFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DRIVE_ACCOUNT_EMAIL_KEY] ?: ""
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "en"
    }

    val invoiceSettingsDataFlow: Flow<InvoiceSettingsData> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[INVOICE_SETTINGS_DATA_KEY] ?: ""
        if (jsonStr.isNotBlank()) {
            InvoiceSettingsData.fromJson(jsonStr)
        } else {
            // Fallback sync with legacy keys
            val defaults = InvoiceSettingsData()
            val name = preferences[SHOP_NAME_KEY] ?: defaults.storeName
            val addr = preferences[SHOP_ADDRESS_KEY] ?: defaults.address
            val phone = preferences[SHOP_PHONE_KEY] ?: defaults.phonePrimary
            val logo = preferences[SHOP_LOGO_URI_KEY] ?: defaults.logoUri
            val fb = preferences[FACEBOOK_URL_KEY] ?: defaults.facebookUrl
            val qrInfo = preferences[QR_CODE_INFO_KEY] ?: defaults.qrCustomUrl
            val cashier = preferences[CASHIER_NAME_KEY] ?: defaults.cashierName
            InvoiceSettingsData(
                storeName = name,
                address = addr,
                phonePrimary = phone,
                logoUri = logo,
                facebookUrl = fb,
                qrCustomUrl = qrInfo,
                cashierName = cashier
            )
        }
    }

    val shopNameFlow: Flow<String> = invoiceSettingsDataFlow.map { it.storeName }

    val shopAddressFlow: Flow<String> = invoiceSettingsDataFlow.map { it.address }

    val shopPhoneFlow: Flow<String> = invoiceSettingsDataFlow.map {
        if (it.phoneSecondary.isNotBlank()) "${it.phonePrimary} | ${it.phoneSecondary}" else it.phonePrimary
    }

    val shopLogoUriFlow: Flow<String> = invoiceSettingsDataFlow.map { it.logoUri }

    val facebookUrlFlow: Flow<String> = invoiceSettingsDataFlow.map { it.facebookUrl }

    val qrCodeInfoFlow: Flow<String> = invoiceSettingsDataFlow.map { it.qrCustomUrl }

    val cashierNameFlow: Flow<String> = invoiceSettingsDataFlow.map { it.cashierName }

    val autoBackupFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_BACKUP_KEY] ?: false
    }

    val localAutoBackupFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCAL_AUTO_BACKUP_KEY] ?: true
    }

    val autoBackupFrequencyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AUTO_BACKUP_FREQUENCY_KEY] ?: "DAILY"
    }

    val driveAutoBackupFrequencyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DRIVE_AUTO_BACKUP_FREQUENCY_KEY] ?: "DAILY"
    }

    val lastBackupTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_BACKUP_TIMESTAMP_KEY] ?: 0L
    }

    val lastLocalBackupTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_LOCAL_BACKUP_TIMESTAMP_KEY]
            ?: preferences[LAST_BACKUP_TIMESTAMP_KEY]
            ?: 0L
    }

    val lastDriveBackupTimestampFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_DRIVE_BACKUP_TIMESTAMP_KEY]
            ?: preferences[LAST_BACKUP_TIMESTAMP_KEY]
            ?: 0L
    }

    val cartSnapshotFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CART_SNAPSHOT_KEY] ?: ""
    }

    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    suspend fun saveUiLayoutMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[UI_LAYOUT_MODE_KEY] = mode
        }
    }

    suspend fun saveShopInfo(name: String, address: String, phone: String) {
        context.dataStore.edit { preferences ->
            preferences[SHOP_NAME_KEY] = name
            preferences[SHOP_ADDRESS_KEY] = address
            preferences[SHOP_PHONE_KEY] = phone
        }
    }

    suspend fun saveInvoiceSettings(logoUri: String, facebookUrl: String, qrCodeInfo: String, cashierName: String) {
        context.dataStore.edit { preferences ->
            preferences[SHOP_LOGO_URI_KEY] = logoUri
            preferences[FACEBOOK_URL_KEY] = facebookUrl
            preferences[QR_CODE_INFO_KEY] = qrCodeInfo
            preferences[CASHIER_NAME_KEY] = cashierName
        }
    }

    suspend fun saveInvoiceSettingsData(settings: InvoiceSettingsData) {
        context.dataStore.edit { preferences ->
            preferences[INVOICE_SETTINGS_DATA_KEY] = settings.toJson()
            // Sync top level legacy keys as well
            preferences[SHOP_NAME_KEY] = settings.storeName
            preferences[SHOP_ADDRESS_KEY] = settings.address
            preferences[SHOP_PHONE_KEY] = if (settings.phoneSecondary.isNotBlank()) "${settings.phonePrimary} | ${settings.phoneSecondary}" else settings.phonePrimary
            preferences[SHOP_LOGO_URI_KEY] = settings.logoUri
            preferences[FACEBOOK_URL_KEY] = settings.facebookUrl
            preferences[CASHIER_NAME_KEY] = settings.cashierName
        }
    }

    suspend fun saveAutoBackup(enabled: Boolean, frequency: String) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_KEY] = enabled
            preferences[DRIVE_AUTO_BACKUP_FREQUENCY_KEY] = frequency
        }
    }

    suspend fun saveLocalAutoBackup(enabled: Boolean, frequency: String) {
        context.dataStore.edit { preferences ->
            preferences[LOCAL_AUTO_BACKUP_KEY] = enabled
            preferences[AUTO_BACKUP_FREQUENCY_KEY] = frequency
        }
    }

    suspend fun saveLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_BACKUP_TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun saveLastLocalBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_LOCAL_BACKUP_TIMESTAMP_KEY] = timestamp
            preferences[LAST_BACKUP_TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun saveLastDriveBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_DRIVE_BACKUP_TIMESTAMP_KEY] = timestamp
            preferences[LAST_BACKUP_TIMESTAMP_KEY] = timestamp
        }
    }

    suspend fun saveDriveRetentionCount(retentionCount: Int) {
        context.dataStore.edit { preferences ->
            preferences[DRIVE_RETENTION_COUNT_KEY] = retentionCount
        }
    }

    suspend fun saveDriveNetworkPolicy(policy: String) {
        context.dataStore.edit { preferences ->
            preferences[DRIVE_NETWORK_POLICY_KEY] = policy
        }
    }

    suspend fun saveDriveBackupTime(timeStr: String) {
        context.dataStore.edit { preferences ->
            preferences[DRIVE_BACKUP_TIME_KEY] = timeStr
        }
    }

    suspend fun saveDriveIdentity(accountId: String, email: String) {
        context.dataStore.edit { preferences ->
            preferences[DRIVE_ACCOUNT_ID_KEY] = accountId
            preferences[DRIVE_ACCOUNT_EMAIL_KEY] = email
        }
    }

    suspend fun clearDriveIdentity() {
        context.dataStore.edit { preferences ->
            preferences.remove(DRIVE_ACCOUNT_ID_KEY)
            preferences.remove(DRIVE_ACCOUNT_EMAIL_KEY)
        }
    }

    suspend fun saveCartSnapshot(json: String) {
        context.dataStore.edit { preferences ->
            preferences[CART_SNAPSHOT_KEY] = json
        }
    }

    val quickSalePresetsFlow: Flow<List<QuickSalePreset>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[QUICK_SALE_PRESETS_KEY] ?: ""
        QuickSalePreset.listFromJson(jsonStr)
    }

    suspend fun saveQuickSalePresets(presets: List<QuickSalePreset>) {
        context.dataStore.edit { preferences ->
            preferences[QUICK_SALE_PRESETS_KEY] = QuickSalePreset.listToJson(presets)
        }
    }

    val notePresetsFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[NOTE_PRESETS_KEY] ?: ""
        if (jsonStr.isBlank()) {
            DEFAULT_NOTE_PRESETS
        } else {
            try {
                val array = org.json.JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val str = array.optString(i)
                    if (str.isNotBlank()) list.add(str)
                }
                if (list.isEmpty()) DEFAULT_NOTE_PRESETS else list
            } catch (e: Exception) {
                DEFAULT_NOTE_PRESETS
            }
        }
    }

    suspend fun saveNotePresets(presets: List<String>) {
        context.dataStore.edit { preferences ->
            val array = org.json.JSONArray()
            presets.forEach { array.put(it) }
            preferences[NOTE_PRESETS_KEY] = array.toString()
        }
    }

    val dueReminderTemplatesFlow: Flow<List<DueTemplate>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[DUE_REMINDER_TEMPLATES_KEY] ?: ""
        DueTemplate.listFromJson(jsonStr)
    }

    suspend fun saveDueReminderTemplates(templates: List<DueTemplate>) {
        context.dataStore.edit { preferences ->
            preferences[DUE_REMINDER_TEMPLATES_KEY] = DueTemplate.listToJson(templates)
        }
    }

    val categoriesFlow: Flow<List<CategoryItem>> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[PRODUCT_CATEGORIES_KEY] ?: ""
        CategoryItem.listFromJson(jsonStr)
    }

    suspend fun saveCategories(categories: List<CategoryItem>) {
        context.dataStore.edit { preferences ->
            preferences[PRODUCT_CATEGORIES_KEY] = CategoryItem.listToJson(categories)
        }
    }

    suspend fun resetDefaultCategories() {
        context.dataStore.edit { preferences ->
            preferences[PRODUCT_CATEGORIES_KEY] = CategoryItem.listToJson(CategoryItem.defaultCategories())
        }
    }

    val returnAuditPresetsFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[RETURN_AUDIT_PRESETS_KEY]
    }

    suspend fun saveReturnAuditPresets(json: String) {
        context.dataStore.edit { preferences ->
            preferences[RETURN_AUDIT_PRESETS_KEY] = json
        }
    }

    suspend fun migrateLegacyReturnAuditPresetsIfNeeded() {
        try {
            val legacyPrefs = context.getSharedPreferences("techyfi_return_dialog_prefs", Context.MODE_PRIVATE)
            val legacyJson = legacyPrefs.getString("custom_audit_presets_json", null)
            if (!legacyJson.isNullOrBlank()) {
                context.dataStore.edit { prefs ->
                    if (prefs[RETURN_AUDIT_PRESETS_KEY].isNullOrBlank()) {
                        prefs[RETURN_AUDIT_PRESETS_KEY] = legacyJson
                    }
                }
                legacyPrefs.edit().clear().apply()
            }
        } catch (_: Exception) {}
    }

    suspend fun saveDefaultCostPercentage(percentage: Double) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_COST_PERCENTAGE_KEY] = percentage.coerceIn(0.0, 100.0)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
