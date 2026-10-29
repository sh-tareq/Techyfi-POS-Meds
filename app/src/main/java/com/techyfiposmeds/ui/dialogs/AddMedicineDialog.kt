package com.techyfiposmeds.ui.dialogs

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.LocaleWrapper
import com.techyfiposmeds.data.ClinicalGeneric
import com.techyfiposmeds.ui.components.Realistic3DProductsBoxIcon
import com.techyfiposmeds.utils.formatTaka
import com.techyfiposmeds.utils.isAppBangla
import com.techyfiposmeds.utils.toBanglaDigits
import com.techyfiposmeds.utils.toEnglishDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicineDialog(
    availableShelfLocations: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSearchGenerics: suspend (String) -> List<String> = { emptyList() },
    onSearchCompanies: suspend (String) -> List<String> = { emptyList() },
    onGetClinicalInfoByName: suspend (String) -> ClinicalGeneric? = { null },
    onSave: (
        brandName: String,
        strength: String?,
        dosageForm: String,
        genericName: String?,
        companyName: String?,
        mrpPrice: Double,
        purchasePrice: Double,
        stripSize: Int,
        stripPrice: Double,
        packSize: String?,
        unitsPerPack: Int,
        packPrice: Double,
        shelfLocation: String?,
        batchNumber: String?,
        expiryDate: String?,
        stockQty: Double,
        reorderLevel: Int,
        isAntibiotic: Boolean,
        indication: String?,
        adultDose: String?,
        childDose: String?,
        contraIndication: String?,
        sideEffect: String?,
        precaution: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val isBn = isAppBangla()
    val scope = rememberCoroutineScope()

    // =========================================================================
    // 1. Medicine Basics State
    // =========================================================================
    var brandName by remember { mutableStateOf("") }
    var strength by remember { mutableStateOf("") }
    var dosageForm by remember { mutableStateOf("Tablet") }
    var genericName by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var isAntibiotic by remember { mutableStateOf(false) }

    // Autocomplete states
    var genericSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showGenericDropdown by remember { mutableStateOf(false) }
    var companySuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCompanyDropdown by remember { mutableStateOf(false) }
    var showDosageDropdown by remember { mutableStateOf(false) }

    // Load initial top companies
    LaunchedEffect(Unit) {
        companySuggestions = onSearchCompanies("")
    }

    // Debounced generic search
    LaunchedEffect(genericName) {
        val q = genericName.trim()
        if (q.length >= 2) {
            delay(200)
            genericSuggestions = onSearchGenerics(q)
            showGenericDropdown = genericSuggestions.isNotEmpty()
        } else {
            genericSuggestions = emptyList()
            showGenericDropdown = false
        }
    }

    // =========================================================================
    // 2. Clinical Information State
    // =========================================================================
    var indication by remember { mutableStateOf("") }
    var adultDose by remember { mutableStateOf("") }
    var childDose by remember { mutableStateOf("") }
    var contraIndication by remember { mutableStateOf("") }
    var sideEffect by remember { mutableStateOf("") }
    var precaution by remember { mutableStateOf("") }
    var isClinicalExpanded by remember { mutableStateOf(false) }

    fun loadClinicalForGeneric(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val clinical = onGetClinicalInfoByName(name.trim())
            if (clinical != null) {
                if (indication.isBlank()) indication = clinical.indication.orEmpty()
                if (adultDose.isBlank()) adultDose = clinical.adultDose.orEmpty()
                if (childDose.isBlank()) childDose = clinical.childDose.orEmpty()
                if (contraIndication.isBlank()) contraIndication = clinical.contraIndication.orEmpty()
                if (sideEffect.isBlank()) sideEffect = clinical.sideEffect.orEmpty()
                if (precaution.isBlank()) precaution = clinical.precaution.orEmpty()
                isClinicalExpanded = true
            }
        }
    }

    // =========================================================================
    // 3. Packaging & Pricing State
    // =========================================================================
    val isLiquidOrTopical = remember(dosageForm) {
        dosageForm in listOf("Syrup", "Suspension", "Eye Drops", "Ear Drops", "Cream", "Ointment", "Gel", "Inhaler", "Oral Solution", "Nasal Spray")
    }

    var stripSizeText by remember { mutableStateOf(if (isLiquidOrTopical) "1" else "10") }
    var unitsPerPackText by remember { mutableStateOf(if (isLiquidOrTopical) "1" else "100") }

    LaunchedEffect(dosageForm) {
        if (isLiquidOrTopical) {
            stripSizeText = "1"
            unitsPerPackText = "1"
        } else if (stripSizeText == "1") {
            stripSizeText = "10"
            unitsPerPackText = "100"
        }
    }

    var mrpText by remember { mutableStateOf("") }
    var tpText by remember { mutableStateOf("") }
    var isTpManuallyEdited by remember { mutableStateOf(false) }
    var stripPriceText by remember { mutableStateOf("") }
    var packPriceText by remember { mutableStateOf("") }

    // =========================================================================
    // 4. Stock, Rack & Batch State
    // =========================================================================
    var stockUnitType by remember { mutableStateOf(if (isLiquidOrTopical) "pc" else "strip") }
    var stockText by remember { mutableStateOf("0") }
    var rackNumber by remember { mutableStateOf("") }
    var batchNumber by remember {
        mutableStateOf("BX-${(System.currentTimeMillis() % 100000).toString().padStart(5, '0')}")
    }
    var expiryDateText by remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, 1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        mutableStateOf(sdf.format(cal.time))
    }
    var reorderLevelText by remember { mutableStateOf("20") }

    fun addMonthsToExpiry(months: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, months)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        expiryDateText = sdf.format(cal.time)
    }

    // Live Financial Calculations
    val parsedMrp = toEnglishDigits(mrpText).toDoubleOrNull() ?: 0.0
    val parsedTp = toEnglishDigits(tpText).toDoubleOrNull() ?: (parsedMrp * 0.85)
    val profitPerUnit = (parsedMrp - parsedTp).coerceAtLeast(0.0)
    val marginPercent = if (parsedMrp > 0.0) (profitPerUnit / parsedMrp) * 100.0 else 0.0

    // Common Dosage Forms
    val dosageFormsList = listOf(
        "Tablet", "Capsule", "Syrup", "Suspension", "Injection",
        "Eye Drops", "Ointment", "Cream", "Gel", "Suppository",
        "Inhaler", "Oral Solution", "Nasal Spray", "Powder for Suspension"
    )

    // Popular Bangladeshi Pharma Companies for 1-Tap Selection
    val popularCompanies = listOf(
        "Square Pharmaceuticals PLC",
        "Beximco Pharmaceuticals Ltd.",
        "Incepta Pharmaceuticals Ltd.",
        "Renata PLC",
        "Eskayef Pharmaceuticals Ltd.",
        "Opsonin Pharma Limited",
        "ACI Limited",
        "Acme Laboratories Ltd.",
        "Healthcare Pharmaceuticals Ltd.",
        "Popular Pharmaceuticals Ltd.",
        "Aristopharma Limited",
        "Ibn Sina Pharmaceutical Ind. Ltd."
    )

    val rackPresets = remember(availableShelfLocations) {
        (availableShelfLocations.filter { it.isNotBlank() } + listOf("Rack A-1", "Rack A-2", "Rack B-1", "Drawer C", "ফ্রিজ (2-8°C)")).distinct().take(6)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        LocaleWrapper(language = if (isBn) "bn" else "en") {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                val screenWidth = maxWidth
                val screenHeight = maxHeight
                val isTabletWidth = screenWidth >= 600.dp
                val isDualPaneTablet = screenWidth >= 780.dp

                Surface(
                    modifier = when {
                        isDualPaneTablet -> {
                            Modifier
                                .width(if (screenWidth >= 1000.dp) 920.dp else 840.dp)
                                .heightIn(max = screenHeight * 0.94f)
                                .padding(16.dp)
                        }
                        isTabletWidth -> {
                            Modifier
                                .width(620.dp)
                                .heightIn(max = screenHeight * 0.94f)
                                .padding(16.dp)
                        }
                        else -> {
                            Modifier
                                .fillMaxWidth(0.96f)
                                .heightIn(max = screenHeight * 0.96f)
                                .padding(horizontal = 6.dp, vertical = 10.dp)
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isTabletWidth) 18.dp else 12.dp, vertical = 12.dp)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF059669).copy(alpha = 0.14f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Realistic3DProductsBoxIcon(size = 24.dp, animate = false)
                                    }
                                }
                                Column {
                                    Text(
                                        text = if (isBn) "নতুন ওষুধ যুক্ত করুন" else "Add New Medicine",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isBn) "ফার্মেসি ডেটাবেজ ও স্টক ইনভেন্টরি" else "Pharmacy Database & Stock Inventory",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Form Body (Adaptive: Dual-column on wide tablets, single-column on phones/portrait tablets)
                        if (isDualPaneTablet) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Left Column: Identification & Pricing
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MedicineIdentificationCard(
                                        isBn = isBn,
                                        brandName = brandName,
                                        onBrandNameChange = { brandName = it },
                                        strength = strength,
                                        onStrengthChange = { strength = it },
                                        dosageForm = dosageForm,
                                        onDosageFormChange = { dosageForm = it },
                                        showDosageDropdown = showDosageDropdown,
                                        onShowDosageDropdownChange = { showDosageDropdown = it },
                                        dosageFormsList = dosageFormsList,
                                        genericName = genericName,
                                        onGenericNameChange = { genericName = it },
                                        genericSuggestions = genericSuggestions,
                                        showGenericDropdown = showGenericDropdown,
                                        onSelectGeneric = { gen ->
                                            genericName = gen
                                            showGenericDropdown = false
                                            loadClinicalForGeneric(gen)
                                        },
                                        onDismissGenericDropdown = { showGenericDropdown = false },
                                        companyName = companyName,
                                        onCompanyNameChange = {
                                            companyName = it
                                            val q = it.trim()
                                            scope.launch {
                                                companySuggestions = onSearchCompanies(q)
                                                showCompanyDropdown = companySuggestions.isNotEmpty() && q.length >= 2
                                            }
                                        },
                                        companySuggestions = companySuggestions,
                                        showCompanyDropdown = showCompanyDropdown,
                                        onSelectCompany = { comp ->
                                            companyName = comp
                                            showCompanyDropdown = false
                                        },
                                        onDismissCompanyDropdown = { showCompanyDropdown = false },
                                        popularCompanies = popularCompanies,
                                        isAntibiotic = isAntibiotic,
                                        onAntibioticChange = { isAntibiotic = it }
                                    )

                                    PackagingPricingCard(
                                        isBn = isBn,
                                        isLiquidOrTopical = isLiquidOrTopical,
                                        stripSizeText = stripSizeText,
                                        onStripSizeChange = {
                                            stripSizeText = it
                                            val sSize = toEnglishDigits(it).toIntOrNull() ?: 10
                                            val m = toEnglishDigits(mrpText).toDoubleOrNull()
                                            if (m != null && m > 0.0) {
                                                stripPriceText = "%.2f".format(Locale.US, m * sSize).trimEnd('0').trimEnd('.')
                                            }
                                        },
                                        unitsPerPackText = unitsPerPackText,
                                        onUnitsPerPackChange = {
                                            unitsPerPackText = it
                                            val uPack = toEnglishDigits(it).toIntOrNull() ?: 100
                                            val m = toEnglishDigits(mrpText).toDoubleOrNull()
                                            if (m != null && m > 0.0) {
                                                packPriceText = "%.2f".format(Locale.US, m * uPack).trimEnd('0').trimEnd('.')
                                            }
                                        },
                                        mrpText = mrpText,
                                        onMrpChange = {
                                            mrpText = it
                                            val m = toEnglishDigits(it).toDoubleOrNull()
                                            if (m != null && m > 0.0) {
                                                if (!isTpManuallyEdited) {
                                                    tpText = "%.2f".format(Locale.US, m * 0.85).trimEnd('0').trimEnd('.')
                                                }
                                                val sSize = toEnglishDigits(stripSizeText).toIntOrNull() ?: (if (isLiquidOrTopical) 1 else 10)
                                                val uPack = toEnglishDigits(unitsPerPackText).toIntOrNull() ?: (if (isLiquidOrTopical) 1 else 100)
                                                stripPriceText = "%.2f".format(Locale.US, m * sSize).trimEnd('0').trimEnd('.')
                                                packPriceText = "%.2f".format(Locale.US, m * uPack).trimEnd('0').trimEnd('.')
                                            }
                                        },
                                        tpText = tpText,
                                        onTpChange = {
                                            tpText = it
                                            isTpManuallyEdited = it.isNotBlank()
                                        },
                                        stripPriceText = stripPriceText,
                                        onStripPriceChange = { stripPriceText = it },
                                        packPriceText = packPriceText,
                                        onPackPriceChange = { packPriceText = it },
                                        parsedMrp = parsedMrp,
                                        profitPerUnit = profitPerUnit,
                                        marginPercent = marginPercent
                                    )
                                }

                                // Right Column: Stock & Clinical
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StockBatchesCard(
                                        isBn = isBn,
                                        isLiquidOrTopical = isLiquidOrTopical,
                                        stockUnitType = stockUnitType,
                                        onStockUnitTypeChange = { stockUnitType = it },
                                        stockText = stockText,
                                        onStockTextChange = { stockText = it },
                                        reorderLevelText = reorderLevelText,
                                        onReorderLevelChange = { reorderLevelText = it },
                                        onQuickStockAdd = { inc ->
                                            val current = toEnglishDigits(stockText).toDoubleOrNull() ?: 0.0
                                            stockText = (current + inc).toInt().toString()
                                        },
                                        rackNumber = rackNumber,
                                        onRackNumberChange = { rackNumber = it },
                                        rackPresets = rackPresets,
                                        batchNumber = batchNumber,
                                        onBatchNumberChange = { batchNumber = it },
                                        expiryDateText = expiryDateText,
                                        onExpiryDateChange = { expiryDateText = it },
                                        onAddMonthsToExpiry = { addMonthsToExpiry(it) }
                                    )

                                    ClinicalReferenceCard(
                                        isBn = isBn,
                                        isExpanded = isClinicalExpanded,
                                        onToggleExpanded = { isClinicalExpanded = !isClinicalExpanded },
                                        indication = indication,
                                        onIndicationChange = { indication = it },
                                        adultDose = adultDose,
                                        onAdultDoseChange = { adultDose = it },
                                        childDose = childDose,
                                        onChildDoseChange = { childDose = it },
                                        contraIndication = contraIndication,
                                        onContraIndicationChange = { contraIndication = it },
                                        sideEffect = sideEffect,
                                        onSideEffectChange = { sideEffect = it },
                                        precaution = precaution,
                                        onPrecautionChange = { precaution = it }
                                    )
                                }
                            }
                        } else {
                            // Phone & Portrait Tablet Layout: Single continuous scrollable column
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MedicineIdentificationCard(
                                    isBn = isBn,
                                    brandName = brandName,
                                    onBrandNameChange = { brandName = it },
                                    strength = strength,
                                    onStrengthChange = { strength = it },
                                    dosageForm = dosageForm,
                                    onDosageFormChange = { dosageForm = it },
                                    showDosageDropdown = showDosageDropdown,
                                    onShowDosageDropdownChange = { showDosageDropdown = it },
                                    dosageFormsList = dosageFormsList,
                                    genericName = genericName,
                                    onGenericNameChange = { genericName = it },
                                    genericSuggestions = genericSuggestions,
                                    showGenericDropdown = showGenericDropdown,
                                    onSelectGeneric = { gen ->
                                        genericName = gen
                                        showGenericDropdown = false
                                        loadClinicalForGeneric(gen)
                                    },
                                    onDismissGenericDropdown = { showGenericDropdown = false },
                                    companyName = companyName,
                                    onCompanyNameChange = {
                                        companyName = it
                                        val q = it.trim()
                                        scope.launch {
                                            companySuggestions = onSearchCompanies(q)
                                            showCompanyDropdown = companySuggestions.isNotEmpty() && q.length >= 2
                                        }
                                    },
                                    companySuggestions = companySuggestions,
                                    showCompanyDropdown = showCompanyDropdown,
                                    onSelectCompany = { comp ->
                                        companyName = comp
                                        showCompanyDropdown = false
                                    },
                                    onDismissCompanyDropdown = { showCompanyDropdown = false },
                                    popularCompanies = popularCompanies,
                                    isAntibiotic = isAntibiotic,
                                    onAntibioticChange = { isAntibiotic = it }
                                )

                                PackagingPricingCard(
                                    isBn = isBn,
                                    isLiquidOrTopical = isLiquidOrTopical,
                                    stripSizeText = stripSizeText,
                                    onStripSizeChange = {
                                        stripSizeText = it
                                        val sSize = toEnglishDigits(it).toIntOrNull() ?: 10
                                        val m = toEnglishDigits(mrpText).toDoubleOrNull()
                                        if (m != null && m > 0.0) {
                                            stripPriceText = "%.2f".format(Locale.US, m * sSize).trimEnd('0').trimEnd('.')
                                        }
                                    },
                                    unitsPerPackText = unitsPerPackText,
                                    onUnitsPerPackChange = {
                                        unitsPerPackText = it
                                        val uPack = toEnglishDigits(it).toIntOrNull() ?: 100
                                        val m = toEnglishDigits(mrpText).toDoubleOrNull()
                                        if (m != null && m > 0.0) {
                                            packPriceText = "%.2f".format(Locale.US, m * uPack).trimEnd('0').trimEnd('.')
                                        }
                                    },
                                    mrpText = mrpText,
                                    onMrpChange = {
                                        mrpText = it
                                        val m = toEnglishDigits(it).toDoubleOrNull()
                                        if (m != null && m > 0.0) {
                                            if (!isTpManuallyEdited) {
                                                tpText = "%.2f".format(Locale.US, m * 0.85).trimEnd('0').trimEnd('.')
                                            }
                                            val sSize = toEnglishDigits(stripSizeText).toIntOrNull() ?: (if (isLiquidOrTopical) 1 else 10)
                                            val uPack = toEnglishDigits(unitsPerPackText).toIntOrNull() ?: (if (isLiquidOrTopical) 1 else 100)
                                            stripPriceText = "%.2f".format(Locale.US, m * sSize).trimEnd('0').trimEnd('.')
                                            packPriceText = "%.2f".format(Locale.US, m * uPack).trimEnd('0').trimEnd('.')
                                        }
                                    },
                                    tpText = tpText,
                                    onTpChange = {
                                        tpText = it
                                        isTpManuallyEdited = it.isNotBlank()
                                    },
                                    stripPriceText = stripPriceText,
                                    onStripPriceChange = { stripPriceText = it },
                                    packPriceText = packPriceText,
                                    onPackPriceChange = { packPriceText = it },
                                    parsedMrp = parsedMrp,
                                    profitPerUnit = profitPerUnit,
                                    marginPercent = marginPercent
                                )

                                StockBatchesCard(
                                    isBn = isBn,
                                    isLiquidOrTopical = isLiquidOrTopical,
                                    stockUnitType = stockUnitType,
                                    onStockUnitTypeChange = { stockUnitType = it },
                                    stockText = stockText,
                                    onStockTextChange = { stockText = it },
                                    reorderLevelText = reorderLevelText,
                                    onReorderLevelChange = { reorderLevelText = it },
                                    onQuickStockAdd = { inc ->
                                        val current = toEnglishDigits(stockText).toDoubleOrNull() ?: 0.0
                                        stockText = (current + inc).toInt().toString()
                                    },
                                    rackNumber = rackNumber,
                                    onRackNumberChange = { rackNumber = it },
                                    rackPresets = rackPresets,
                                    batchNumber = batchNumber,
                                    onBatchNumberChange = { batchNumber = it },
                                    expiryDateText = expiryDateText,
                                    onExpiryDateChange = { expiryDateText = it },
                                    onAddMonthsToExpiry = { addMonthsToExpiry(it) }
                                )

                                ClinicalReferenceCard(
                                    isBn = isBn,
                                    isExpanded = isClinicalExpanded,
                                    onToggleExpanded = { isClinicalExpanded = !isClinicalExpanded },
                                    indication = indication,
                                    onIndicationChange = { indication = it },
                                    adultDose = adultDose,
                                    onAdultDoseChange = { adultDose = it },
                                    childDose = childDose,
                                    onChildDoseChange = { childDose = it },
                                    contraIndication = contraIndication,
                                    onContraIndicationChange = { contraIndication = it },
                                    sideEffect = sideEffect,
                                    onSideEffectChange = { sideEffect = it },
                                    precaution = precaution,
                                    onPrecautionChange = { precaution = it }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Action Buttons Bar (Always fixed at bottom, minimum 48dp touch target)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            val canSave = brandName.isNotBlank() && parsedMrp > 0.0

                            Button(
                                onClick = {
                                    if (brandName.isBlank()) {
                                        Toast.makeText(context, if (isBn) "ওষুধের ব্র্যান্ডের নাম দিন" else "Please enter brand name", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (parsedMrp <= 0.0) {
                                        Toast.makeText(context, if (isBn) "ওষুধের বিক্রয়মূল্য / এমআরপি দিন" else "Please enter valid MRP", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    val rawStock = toEnglishDigits(stockText).toDoubleOrNull() ?: 0.0
                                    val stripSize = toEnglishDigits(stripSizeText).toIntOrNull() ?: (if (isLiquidOrTopical) 1 else 10)
                                    val finalBaseStock = if (stockUnitType == "strip" && stripSize > 1) rawStock * stripSize else rawStock
                                    val tp = toEnglishDigits(tpText).toDoubleOrNull() ?: (parsedMrp * 0.85)
                                    val stripPrice = toEnglishDigits(stripPriceText).toDoubleOrNull() ?: (parsedMrp * stripSize)
                                    val unitsPerPack = toEnglishDigits(unitsPerPackText).toIntOrNull() ?: (if (isLiquidOrTopical) 1 else 100)
                                    val packPrice = toEnglishDigits(packPriceText).toDoubleOrNull() ?: (parsedMrp * unitsPerPack)
                                    val reorder = toEnglishDigits(reorderLevelText).toIntOrNull() ?: 20

                                    onSave(
                                        brandName.trim(),
                                        strength.trim().ifBlank { null },
                                        dosageForm.trim(),
                                        genericName.trim().ifBlank { null },
                                        companyName.trim().ifBlank { null },
                                        parsedMrp,
                                        tp,
                                        stripSize,
                                        stripPrice,
                                        if (stripSize > 1) "${unitsPerPack / stripSize} x ${stripSize}'s" else "1 Unit",
                                        unitsPerPack,
                                        packPrice,
                                        rackNumber.trim().ifBlank { null },
                                        batchNumber.trim().ifBlank { null },
                                        expiryDateText.trim().ifBlank { null },
                                        finalBaseStock,
                                        reorder,
                                        isAntibiotic,
                                        indication.trim().ifBlank { null },
                                        adultDose.trim().ifBlank { null },
                                        childDose.trim().ifBlank { null },
                                        contraIndication.trim().ifBlank { null },
                                        sideEffect.trim().ifBlank { null },
                                        precaution.trim().ifBlank { null }
                                    )
                                },
                                enabled = canSave,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF059669),
                                    disabledContainerColor = Color(0xFF059669).copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isBn) "ওষুধ সংরক্ষণ করুন" else "Save Medicine",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Helper Composables for Compact, Usable Cards
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineIdentificationCard(
    isBn: Boolean,
    brandName: String,
    onBrandNameChange: (String) -> Unit,
    strength: String,
    onStrengthChange: (String) -> Unit,
    dosageForm: String,
    onDosageFormChange: (String) -> Unit,
    showDosageDropdown: Boolean,
    onShowDosageDropdownChange: (Boolean) -> Unit,
    dosageFormsList: List<String>,
    genericName: String,
    onGenericNameChange: (String) -> Unit,
    genericSuggestions: List<String>,
    showGenericDropdown: Boolean,
    onSelectGeneric: (String) -> Unit,
    onDismissGenericDropdown: () -> Unit,
    companyName: String,
    onCompanyNameChange: (String) -> Unit,
    companySuggestions: List<String>,
    showCompanyDropdown: Boolean,
    onSelectCompany: (String) -> Unit,
    onDismissCompanyDropdown: () -> Unit,
    popularCompanies: List<String>,
    isAntibiotic: Boolean,
    onAntibioticChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card Title Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF059669).copy(alpha = 0.12f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Medication,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = if (isBn) "ওষুধের প্রাথমিক পরিচিতি" else "Medicine Identification",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            // Brand Name (Required)
            OutlinedTextField(
                value = brandName,
                onValueChange = onBrandNameChange,
                label = {
                    Text(
                        text = if (isBn) "ব্র্যান্ডের নাম *" else "Brand Name *",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                placeholder = {
                    Text(
                        text = if (isBn) "যেমন: Napa, Seclo, Maxpro" else "e.g. Napa, Seclo, Maxpro",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = compactFieldColors(),
                trailingIcon = {
                    if (brandName.isNotBlank()) {
                        IconButton(
                            onClick = { onBrandNameChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Row: Strength & Dosage Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = strength,
                    onValueChange = onStrengthChange,
                    label = {
                        Text(
                            text = if (isBn) "স্ট্রেন্থ" else "Strength",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("500 mg, 20 mg", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )

                // Dosage Form Dropdown
                ExposedDropdownMenuBox(
                    expanded = showDosageDropdown,
                    onExpandedChange = onShowDosageDropdownChange,
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = dosageForm,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                text = if (isBn) "ডোজ ফর্ম *" else "Dosage Form *",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDosageDropdown)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = compactFieldColors(),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = showDosageDropdown,
                        onDismissRequest = { onShowDosageDropdownChange(false) }
                    ) {
                        dosageFormsList.forEach { form ->
                            DropdownMenuItem(
                                text = { Text(form, fontSize = 13.sp) },
                                onClick = {
                                    onDosageFormChange(form)
                                    onShowDosageDropdownChange(false)
                                }
                            )
                        }
                    }
                }
            }

            // Quick Strength Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "স্ট্রেন্থ:" else "Quick:",
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B)
                )
                listOf("500 mg", "20 mg", "40 mg", "10 mg", "650 mg", "100 ml", "1 g", "10 mg/ml").forEach { str ->
                    val isSel = strength.trim().equals(str, ignoreCase = true)
                    Surface(
                        onClick = { onStrengthChange(str) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) Color(0xFFDCFCE7) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isSel) Color(0xFF059669) else Color(0xFFCBD5E1)
                        )
                    ) {
                        Text(
                            text = str,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color(0xFF047857) else Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }

            // Generic Name with Live Autocomplete
            Column {
                OutlinedTextField(
                    value = genericName,
                    onValueChange = onGenericNameChange,
                    label = {
                        Text(
                            text = if (isBn) "জেনেরিক নাম" else "Generic Name",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = {
                        Text(
                            text = if (isBn) "যেমন: Paracetamol, Omeprazole" else "e.g. Paracetamol, Omeprazole",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    trailingIcon = {
                        if (genericName.isNotBlank()) {
                            IconButton(
                                onClick = { onGenericNameChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showGenericDropdown && genericSuggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .heightIn(max = 160.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            genericSuggestions.forEach { gen ->
                                DropdownMenuItem(
                                    text = { Text(gen, fontSize = 12.5.sp) },
                                    onClick = { onSelectGeneric(gen) }
                                )
                            }
                        }
                    }
                }
            }

            // Manufacturer / Company Name
            Column {
                OutlinedTextField(
                    value = companyName,
                    onValueChange = onCompanyNameChange,
                    label = {
                        Text(
                            text = if (isBn) "প্রস্তুতকারক কোম্পানি" else "Manufacturer / Company",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = {
                        Text(
                            text = if (isBn) "যেমন: Square, Beximco" else "e.g. Square, Beximco",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    trailingIcon = {
                        if (companyName.isNotBlank()) {
                            IconButton(
                                onClick = { onCompanyNameChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showCompanyDropdown && companySuggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .heightIn(max = 160.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp)
                        ) {
                            companySuggestions.take(6).forEach { comp ->
                                DropdownMenuItem(
                                    text = { Text(comp, fontSize = 12.5.sp) },
                                    onClick = { onSelectCompany(comp) }
                                )
                            }
                        }
                    }
                }
            }

            // Top Pharma 1-Tap Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "জনপ্রিয়:" else "Top:",
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B)
                )
                popularCompanies.take(6).forEach { comp ->
                    val shortName = comp.replace(" Pharmaceuticals Ltd.", "").replace(" Pharmaceuticals PLC", "").replace(" Limited", "").replace(" PLC", "")
                    val isSel = companyName.contains(shortName, ignoreCase = true)
                    Surface(
                        onClick = { onCompanyNameChange(comp) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) Color(0xFFDCFCE7) else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (isSel) Color(0xFF059669) else Color(0xFFCBD5E1)
                        )
                    ) {
                        Text(
                            text = shortName,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color(0xFF047857) else Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }

            // Antibiotic Rx Switch Banner
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isAntibiotic) Color(0xFFFEE2E2).copy(alpha = 0.6f) else Color.White,
                border = BorderStroke(1.dp, if (isAntibiotic) Color(0xFFEF4444) else Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isAntibiotic) Icons.Default.Warning else Icons.Default.Medication,
                            contentDescription = null,
                            tint = if (isAntibiotic) Color(0xFFDC2626) else Color(0xFF059669),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = if (isBn) "অ্যান্টিবায়োটিক (Rx রিকোয়ার্ড)" else "Antibiotic (Rx Required)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAntibiotic) Color(0xFFDC2626) else Color(0xFF0F172A)
                            )
                            Text(
                                text = if (isBn) "বিক্রিতে প্রেসক্রিপশন সতর্কতা সক্রিয় থাকবে" else "Enforces prescription/doctor warning",
                                fontSize = 9.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Switch(
                        checked = isAntibiotic,
                        onCheckedChange = onAntibioticChange,
                        modifier = Modifier.height(28.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFDC2626)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PackagingPricingCard(
    isBn: Boolean,
    isLiquidOrTopical: Boolean,
    stripSizeText: String,
    onStripSizeChange: (String) -> Unit,
    unitsPerPackText: String,
    onUnitsPerPackChange: (String) -> Unit,
    mrpText: String,
    onMrpChange: (String) -> Unit,
    tpText: String,
    onTpChange: (String) -> Unit,
    stripPriceText: String,
    onStripPriceChange: (String) -> Unit,
    packPriceText: String,
    onPackPriceChange: (String) -> Unit,
    parsedMrp: Double,
    profitPerUnit: Double,
    marginPercent: Double
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Title & Live Profit Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF059669).copy(alpha = 0.12f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isBn) "প্যাকেজিং ও মূল্য নির্ধারণ" else "Packaging & Pricing",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (parsedMrp > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (profitPerUnit > 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        border = BorderStroke(0.8.dp, if (profitPerUnit > 0) Color(0xFF10B981) else Color(0xFFEF4444))
                    ) {
                        val marginStr = if (isBn) toBanglaDigits("%.1f".format(marginPercent)) else "%.1f".format(marginPercent)
                        Text(
                            text = if (isBn) "লাভ: ${formatTaka(profitPerUnit)} ($marginStr%)" else "Profit: ${formatTaka(profitPerUnit)} ($marginStr%)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (profitPerUnit > 0) Color(0xFF047857) else Color(0xFFDC2626),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Packaging: Strip Size & Units Per Pack
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = stripSizeText,
                    onValueChange = onStripSizeChange,
                    label = {
                        Text(
                            text = if (isBn) (if (isLiquidOrTopical) "ইউনিট সাইজ" else "পাতা সাইজ (পিস)") else "Strip Size (Pcs)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("10", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = unitsPerPackText,
                    onValueChange = onUnitsPerPackChange,
                    label = {
                        Text(
                            text = if (isBn) "বক্স সাইজ (পিস)" else "Box Size (Total)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("100", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Pricing: MRP & Cost / TP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = mrpText,
                    onValueChange = onMrpChange,
                    label = {
                        Text(
                            text = if (isBn) "এমআরপি (পিস ৳) *" else "MRP (per pc ৳) *",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("0.00", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = tpText,
                    onValueChange = onTpChange,
                    label = {
                        Text(
                            text = if (isBn) "টিপি / কেনাদর (৳)" else "Cost / TP (৳)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("0.00", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Derived Strip Price & Box Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = stripPriceText,
                    onValueChange = onStripPriceChange,
                    label = {
                        Text(
                            text = if (isBn) (if (isLiquidOrTopical) "ইউনিট মূল্য (৳)" else "পাতা মূল্য (৳)") else "Strip Price (৳)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("0.00", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = packPriceText,
                    onValueChange = onPackPriceChange,
                    label = {
                        Text(
                            text = if (isBn) "বক্স মূল্য (৳)" else "Box Price (৳)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("0.00", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockBatchesCard(
    isBn: Boolean,
    isLiquidOrTopical: Boolean,
    stockUnitType: String,
    onStockUnitTypeChange: (String) -> Unit,
    stockText: String,
    onStockTextChange: (String) -> Unit,
    reorderLevelText: String,
    onReorderLevelChange: (String) -> Unit,
    onQuickStockAdd: (Int) -> Unit,
    rackNumber: String,
    onRackNumberChange: (String) -> Unit,
    rackPresets: List<String>,
    batchNumber: String,
    onBatchNumberChange: (String) -> Unit,
    expiryDateText: String,
    onExpiryDateChange: (String) -> Unit,
    onAddMonthsToExpiry: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Title & Unit Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF059669).copy(alpha = 0.12f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isBn) "স্টক ও ব্যাচ ট্র্যাকিং" else "Stock & Batches",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isLiquidOrTopical) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = stockUnitType == "strip",
                            onClick = { onStockUnitTypeChange("strip") },
                            label = { Text(if (isBn) "পাতা" else "Strip", fontSize = 10.5.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        )
                        FilterChip(
                            selected = stockUnitType == "pc",
                            onClick = { onStockUnitTypeChange("pc") },
                            label = { Text(if (isBn) "পিস" else "Pcs", fontSize = 10.5.sp, maxLines = 1) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
            }

            // Initial Stock & Reorder Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stockText,
                    onValueChange = onStockTextChange,
                    label = {
                        Text(
                            text = if (isBn) "প্রারম্ভিক মজুদ (${if (stockUnitType == "strip") "পাতা" else "পিস"})"
                            else "Initial Stock (${if (stockUnitType == "strip") "Strips" else "Pcs"})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("100", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1.3f)
                )

                OutlinedTextField(
                    value = reorderLevelText,
                    onValueChange = onReorderLevelChange,
                    label = {
                        Text(
                            text = if (isBn) "রি-অর্ডার সতর্কতা" else "Reorder Level",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("20", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(0.9f)
                )
            }

            // Quick Stock Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "+ স্টক:" else "+ Stock:",
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B)
                )
                listOf(10, 20, 50, 100, 200).forEach { inc ->
                    Surface(
                        onClick = { onQuickStockAdd(inc) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "+$inc",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }

            // Shelf Location / Rack
            Column {
                OutlinedTextField(
                    value = rackNumber,
                    onValueChange = onRackNumberChange,
                    label = {
                        Text(
                            text = if (isBn) "র্যাক / সেলফ লোকেশন" else "Rack / Shelf Location",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = {
                        Text(
                            text = if (isBn) "যেমন: Rack A-1, ফ্রিজ" else "e.g. Rack A-1, Cold",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    trailingIcon = {
                        if (rackNumber.isNotBlank()) {
                            IconButton(
                                onClick = { onRackNumberChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (rackPresets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBn) "র্যাক:" else "Shelf:",
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B)
                        )
                        rackPresets.forEach { preset ->
                            val isSel = rackNumber.trim().equals(preset.trim(), ignoreCase = true)
                            Surface(
                                onClick = { onRackNumberChange(preset) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color(0xFFDCFCE7) else Color.White,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) Color(0xFF059669) else Color(0xFFCBD5E1)
                                )
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color(0xFF047857) else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }

            // Batch Number & Expiry Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = batchNumber,
                    onValueChange = onBatchNumberChange,
                    label = {
                        Text(
                            text = if (isBn) "ব্যাচ নম্বর" else "Batch Number",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = expiryDateText,
                    onValueChange = onExpiryDateChange,
                    label = {
                        Text(
                            text = if (isBn) "মেয়াদ (YYYY-MM-DD)" else "Expiry (YYYY-MM-DD)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    placeholder = { Text("2027-12-31", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Expiry Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "মেয়াদ:" else "Expiry:",
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B)
                )
                listOf(
                    Pair(6, if (isBn) "+৬ মাস" else "+6 Mos"),
                    Pair(12, if (isBn) "+১ বছর" else "+1 Yr"),
                    Pair(24, if (isBn) "+২ বছর" else "+2 Yrs"),
                    Pair(36, if (isBn) "+৩ বছর" else "+3 Yrs")
                ).forEach { (m, label) ->
                    Surface(
                        onClick = { onAddMonthsToExpiry(m) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun ClinicalReferenceCard(
    isBn: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    indication: String,
    onIndicationChange: (String) -> Unit,
    adultDose: String,
    onAdultDoseChange: (String) -> Unit,
    childDose: String,
    onChildDoseChange: (String) -> Unit,
    contraIndication: String,
    onContraIndicationChange: (String) -> Unit,
    sideEffect: String,
    onSideEffectChange: (String) -> Unit,
    precaution: String,
    onPrecautionChange: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Expand/Collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpanded() }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF059669).copy(alpha = 0.12f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isBn) "ক্লিনিক্যাল ড্রাগ রেফারেন্স (ঐচ্ছিক)" else "Clinical Drug Reference (Optional)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (isBn) "জেনেরিক থেকে স্বয়ংক্রিয় তথ্য বসে" else "Auto-filled from generic database",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier
                        .size(32.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Indications
                    OutlinedTextField(
                        value = indication,
                        onValueChange = onIndicationChange,
                        label = {
                            Text(
                                text = if (isBn) "চিকিৎসা নির্দেশনা (Indications)" else "Medical Indications",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (isBn) "যেমন: জ্বর, মাথাব্যথা, ব্যথানাশক" else "e.g. Fever, headache, mild pain",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF0F172A)),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = compactFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Row: Adult Dose & Child Dose
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = adultDose,
                            onValueChange = onAdultDoseChange,
                            label = {
                                Text(
                                    text = if (isBn) "প্রাপ্তবয়স্কদের মাত্রা" else "Adult Dose",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            placeholder = { Text("500-1000mg q4-6h", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                            textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF0F172A)),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            colors = compactFieldColors(),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = childDose,
                            onValueChange = onChildDoseChange,
                            label = {
                                Text(
                                    text = if (isBn) "শিশুদের মাত্রা" else "Child Dose",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            placeholder = { Text("10-15 mg/kg", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                            textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF0F172A)),
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(10.dp),
                            colors = compactFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Contraindications
                    OutlinedTextField(
                        value = contraIndication,
                        onValueChange = onContraIndicationChange,
                        label = {
                            Text(
                                text = if (isBn) "প্রতিনির্দেশনা (Contraindications)" else "Contraindications",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (isBn) "যে ক্ষেত্রে সেবন নিষিদ্ধ" else "Hypersensitivity, severe impairment",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF0F172A)),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = compactFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Side Effects
                    OutlinedTextField(
                        value = sideEffect,
                        onValueChange = onSideEffectChange,
                        label = {
                            Text(
                                text = if (isBn) "পার্শ্বপ্রতিক্রিয়া (Side Effects)" else "Side Effects",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (isBn) "সাধারণ পার্শ্বপ্রতিক্রিয়া" else "Nausea, rash, mild dizziness",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF0F172A)),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = compactFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Precautions & Warnings
                    OutlinedTextField(
                        value = precaution,
                        onValueChange = onPrecautionChange,
                        label = {
                            Text(
                                text = if (isBn) "সতর্কতা ও গর্ভাবস্থা" else "Precautions & Pregnancy",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (isBn) "লিভার রোগী ও গর্ভবতীদের ক্ষেত্রে সতর্কতা" else "Caution in hepatic/renal impairment",
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        textStyle = TextStyle(fontSize = 12.5.sp, color = Color(0xFF0F172A)),
                        minLines = 1,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = compactFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun compactFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFF059669),
        focusedLabelColor = Color(0xFF059669),
        unfocusedBorderColor = Color(0xFFCBD5E1),
        unfocusedLabelColor = Color(0xFF64748B),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        focusedTextColor = Color(0xFF0F172A),
        unfocusedTextColor = Color(0xFF0F172A),
        cursorColor = Color(0xFF059669)
    )
}
