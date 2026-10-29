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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.techyfiposmeds.LocaleWrapper
import com.techyfiposmeds.R
import com.techyfiposmeds.data.ClinicalGeneric
import com.techyfiposmeds.data.MedicineItem
import com.techyfiposmeds.ui.components.Realistic3DProductsBoxIcon
import com.techyfiposmeds.utils.formatTaka
import com.techyfiposmeds.utils.isAppBangla
import com.techyfiposmeds.utils.toBanglaDigits
import com.techyfiposmeds.utils.toEnglishDigits
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMedicineDialog(
    medicine: MedicineItem,
    availableShelfLocations: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onGetClinicalInfoByName: suspend (String) -> ClinicalGeneric? = { null },
    onSave: (
        stockQty: Double,
        purchasePrice: Double,
        mrpPrice: Double,
        stripPrice: Double,
        packPrice: Double,
        stripSize: Int,
        shelfLocation: String?,
        expiryDate: String?,
        batchNumber: String?,
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

    var rackNumber by remember { mutableStateOf(medicine.shelfLocation.orEmpty()) }
    var stockUnitType by remember { mutableStateOf(if (medicine.stripSize > 1) "strip" else "pc") }

    // Clinical states
    var indication by remember { mutableStateOf("") }
    var adultDose by remember { mutableStateOf("") }
    var childDose by remember { mutableStateOf("") }
    var contraIndication by remember { mutableStateOf("") }
    var sideEffect by remember { mutableStateOf("") }
    var precaution by remember { mutableStateOf("") }
    var isClinicalExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(medicine.genericName) {
        val gen = medicine.genericName?.trim()
        if (!gen.isNullOrBlank()) {
            val clinical = onGetClinicalInfoByName(gen)
            if (clinical != null) {
                indication = clinical.indication.orEmpty()
                adultDose = clinical.adultDose.orEmpty()
                childDose = clinical.childDose.orEmpty()
                contraIndication = clinical.contraIndication.orEmpty()
                sideEffect = clinical.sideEffect.orEmpty()
                precaution = clinical.precaution.orEmpty()
            }
        }
    }

    // Stock & Packaging states
    val initialStrips = if (medicine.stripSize > 1) (medicine.stockQty / medicine.stripSize).toInt() else 0
    var stockText by remember {
        mutableStateOf(
            if (stockUnitType == "strip" && medicine.stripSize > 1) {
                if (medicine.stockQty % medicine.stripSize == 0.0) initialStrips.toString()
                else "%.1f".format(Locale.US, medicine.stockQty / medicine.stripSize).trimEnd('0').trimEnd('.')
            } else {
                if (medicine.stockQty % 1.0 == 0.0) medicine.stockQty.toInt().toString()
                else "%.1f".format(Locale.US, medicine.stockQty).trimEnd('0').trimEnd('.')
            }
        )
    }

    var stripSizeText by remember { mutableStateOf((if (medicine.stripSize > 0) medicine.stripSize else 10).toString()) }

    // Pricing states
    val initialTp = (medicine.unitPrice * 0.85)
    var tpText by remember { mutableStateOf(if (initialTp > 0.0) "%.2f".format(Locale.US, initialTp).trimEnd('0').trimEnd('.') else "") }
    var isTpManuallyEdited by remember { mutableStateOf(false) }
    var mrpText by remember { mutableStateOf(if (medicine.unitPrice > 0.0) "%.2f".format(Locale.US, medicine.unitPrice).trimEnd('0').trimEnd('.') else "") }
    var stripPriceText by remember {
        mutableStateOf(
            if (medicine.stripPrice > 0.0) "%.2f".format(Locale.US, medicine.stripPrice).trimEnd('0').trimEnd('.')
            else if (medicine.unitPrice > 0.0) "%.2f".format(Locale.US, medicine.unitPrice * (if (medicine.stripSize > 0) medicine.stripSize else 10)).trimEnd('0').trimEnd('.')
            else ""
        )
    }
    var packPriceText by remember {
        mutableStateOf(if (medicine.packPrice > 0.0) "%.2f".format(Locale.US, medicine.packPrice).trimEnd('0').trimEnd('.') else "")
    }

    // Batch & Expiry states
    var batchNumber by remember { mutableStateOf(medicine.batchNumber.orEmpty().ifBlank { "BX-${(System.currentTimeMillis() % 100000).toString().padStart(5, '0')}" }) }
    var expiryDateText by remember { mutableStateOf(medicine.expiryDate.orEmpty()) }
    var isAntibiotic by remember { mutableStateOf(medicine.isAntibiotic) }

    fun addMonthsToExpiry(months: Int) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, months)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        expiryDateText = sdf.format(cal.time)
    }

    val parsedMrp = toEnglishDigits(mrpText).toDoubleOrNull() ?: 0.0
    val parsedTp = toEnglishDigits(tpText).toDoubleOrNull() ?: 0.0
    val profitPerUnit = (parsedMrp - parsedTp).coerceAtLeast(0.0)
    val marginPercent = if (parsedMrp > 0.0) (profitPerUnit / parsedMrp) * 100.0 else 0.0

    // Auto-recalculate TP and strip price if MRP changes and TP wasn't manually edited
    fun handleMrpChange(newMrp: String) {
        mrpText = newMrp
        val m = toEnglishDigits(newMrp).toDoubleOrNull()
        val sSize = toEnglishDigits(stripSizeText).toIntOrNull() ?: 10
        if (m != null && m > 0.0) {
            if (!isTpManuallyEdited) {
                tpText = "%.2f".format(Locale.US, m * 0.85).trimEnd('0').trimEnd('.')
            }
            stripPriceText = "%.2f".format(Locale.US, m * sSize).trimEnd('0').trimEnd('.')
            if (packPriceText.isBlank() || packPriceText == "0") {
                packPriceText = "%.2f".format(Locale.US, m * sSize * 10).trimEnd('0').trimEnd('.')
            }
        }
    }

    fun handleStripSizeChange(newSize: String) {
        stripSizeText = newSize
        val sSize = toEnglishDigits(newSize).toIntOrNull()
        val m = toEnglishDigits(mrpText).toDoubleOrNull()
        if (sSize != null && sSize > 0 && m != null && m > 0.0) {
            stripPriceText = "%.2f".format(Locale.US, m * sSize).trimEnd('0').trimEnd('.')
        }
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
                    .background(Color.Black.copy(alpha = 0.55f)),
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
                                .width(if (screenWidth >= 1000.dp) 940.dp else 840.dp)
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
                                .padding(horizontal = 6.dp, vertical = 8.dp)
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
                                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Realistic3DProductsBoxIcon(size = 26.dp, animate = false)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = medicine.brandName + if (!medicine.strength.isNullOrBlank()) " ${medicine.strength}" else "",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF059669).copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, Color(0xFF059669).copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = medicine.dosageForm,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF047857),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Medication,
                                            contentDescription = null,
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (!medicine.genericName.isNullOrBlank()) medicine.genericName!! else if (isBn) "জেনেরিক তথ্য অনুপলব্ধ" else "Generic info n/a",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF4F46E5),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!medicine.companyName.isNullOrBlank()) {
                                            Text(
                                                text = "• ${medicine.companyName}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
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

                        // Form Body (Dual-pane on wide tablets, single scrollable column on phones/portrait tablets)
                        if (isDualPaneTablet) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Left Pane: Location, Inventory & Pricing
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RackLocationCard(
                                        isBn = isBn,
                                        rackNumber = rackNumber,
                                        onRackNumberChange = { rackNumber = it },
                                        availableShelfLocations = availableShelfLocations
                                    )

                                    StockInventoryCard(
                                        isBn = isBn,
                                        stockUnitType = stockUnitType,
                                        onStockUnitTypeChange = { stockUnitType = it },
                                        stockText = stockText,
                                        onStockTextChange = { stockText = it },
                                        stripSizeText = stripSizeText,
                                        onStripSizeChange = ::handleStripSizeChange
                                    )

                                    PricingCard(
                                        isBn = isBn,
                                        tpText = tpText,
                                        onTpChange = {
                                            tpText = it
                                            isTpManuallyEdited = true
                                        },
                                        mrpText = mrpText,
                                        onMrpChange = ::handleMrpChange,
                                        stripPriceText = stripPriceText,
                                        onStripPriceChange = { stripPriceText = it },
                                        packPriceText = packPriceText,
                                        onPackPriceChange = { packPriceText = it },
                                        profitPerUnit = profitPerUnit,
                                        marginPercent = marginPercent,
                                        parsedMrp = parsedMrp
                                    )
                                }

                                // Right Pane: Expiry/Batch, Antibiotic & Clinical Drug Reference
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ExpiryBatchCard(
                                        isBn = isBn,
                                        batchNumber = batchNumber,
                                        onBatchNumberChange = { batchNumber = it },
                                        expiryDateText = expiryDateText,
                                        onExpiryDateChange = { expiryDateText = it },
                                        onAddMonths = ::addMonthsToExpiry
                                    )

                                    AntibioticCard(
                                        isBn = isBn,
                                        isAntibiotic = isAntibiotic,
                                        onAntibioticChange = { isAntibiotic = it }
                                    )

                                    ClinicalReferenceCard(
                                        isBn = isBn,
                                        genericName = medicine.genericName,
                                        isClinicalExpanded = isClinicalExpanded,
                                        onToggleClinical = { isClinicalExpanded = !isClinicalExpanded },
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
                            // Phone / Single-column tablet layout
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RackLocationCard(
                                    isBn = isBn,
                                    rackNumber = rackNumber,
                                    onRackNumberChange = { rackNumber = it },
                                    availableShelfLocations = availableShelfLocations
                                )

                                StockInventoryCard(
                                    isBn = isBn,
                                    stockUnitType = stockUnitType,
                                    onStockUnitTypeChange = { stockUnitType = it },
                                    stockText = stockText,
                                    onStockTextChange = { stockText = it },
                                    stripSizeText = stripSizeText,
                                    onStripSizeChange = ::handleStripSizeChange
                                )

                                PricingCard(
                                    isBn = isBn,
                                    tpText = tpText,
                                    onTpChange = {
                                        tpText = it
                                        isTpManuallyEdited = true
                                    },
                                    mrpText = mrpText,
                                    onMrpChange = ::handleMrpChange,
                                    stripPriceText = stripPriceText,
                                    onStripPriceChange = { stripPriceText = it },
                                    packPriceText = packPriceText,
                                    onPackPriceChange = { packPriceText = it },
                                    profitPerUnit = profitPerUnit,
                                    marginPercent = marginPercent,
                                    parsedMrp = parsedMrp
                                )

                                ExpiryBatchCard(
                                    isBn = isBn,
                                    batchNumber = batchNumber,
                                    onBatchNumberChange = { batchNumber = it },
                                    expiryDateText = expiryDateText,
                                    onExpiryDateChange = { expiryDateText = it },
                                    onAddMonths = ::addMonthsToExpiry
                                )

                                AntibioticCard(
                                    isBn = isBn,
                                    isAntibiotic = isAntibiotic,
                                    onAntibioticChange = { isAntibiotic = it }
                                )

                                ClinicalReferenceCard(
                                    isBn = isBn,
                                    genericName = medicine.genericName,
                                    isClinicalExpanded = isClinicalExpanded,
                                    onToggleClinical = { isClinicalExpanded = !isClinicalExpanded },
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

                        HorizontalDivider(modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))

                        // Bottom Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Button(
                                onClick = {
                                    val rawStock = toEnglishDigits(stockText).toDoubleOrNull() ?: 0.0
                                    val stripSize = toEnglishDigits(stripSizeText).toIntOrNull() ?: 10
                                    val finalBaseStock = if (stockUnitType == "strip") rawStock * stripSize else rawStock
                                    val tp = toEnglishDigits(tpText).toDoubleOrNull() ?: (parsedMrp * 0.85)
                                    val mrp = parsedMrp
                                    val stripPrice = toEnglishDigits(stripPriceText).toDoubleOrNull() ?: (mrp * stripSize)
                                    val packPrice = toEnglishDigits(packPriceText).toDoubleOrNull() ?: 0.0

                                    onSave(
                                        finalBaseStock,
                                        tp,
                                        mrp,
                                        stripPrice,
                                        packPrice,
                                        stripSize,
                                        rackNumber.trim().ifBlank { null },
                                        expiryDateText.trim().ifBlank { null },
                                        batchNumber.trim().ifBlank { null },
                                        isAntibiotic,
                                        indication.trim().ifBlank { null },
                                        adultDose.trim().ifBlank { null },
                                        childDose.trim().ifBlank { null },
                                        contraIndication.trim().ifBlank { null },
                                        sideEffect.trim().ifBlank { null },
                                        precaution.trim().ifBlank { null }
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isBn) "পরিবর্তন সংরক্ষণ করুন" else "Save Changes",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
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
// SUB-COMPONENTS (Cards)
// =============================================================================

@Composable
private fun RackLocationCard(
    isBn: Boolean,
    rackNumber: String,
    onRackNumberChange: (String) -> Unit,
    availableShelfLocations: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0284C7).copy(alpha = 0.12f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = if (isBn) "র্যাক / তাকের অবস্থান" else "Rack / Shelf Location",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            OutlinedTextField(
                value = rackNumber,
                onValueChange = onRackNumberChange,
                label = {
                    Text(
                        text = if (isBn) "র্যাক / ড্রয়ারের নাম" else "Rack / Drawer",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                placeholder = {
                    Text(
                        text = if (isBn) "যেমন: Rack A-1, Drawer 3, ফ্রিজ" else "e.g. Rack A-1, Drawer 3, Cold Storage",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = compactFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            // Quick Suggestions from existing store racks
            val rackPresets = remember(availableShelfLocations) {
                (availableShelfLocations.filter { it.isNotBlank() } + listOf("Rack A-1", "Rack A-2", "Rack B-1", "Drawer C", "ফ্রিজ (2-8°C)")).distinct().take(6)
            }
            if (rackPresets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "পরামর্শ:" else "Quick:",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                    rackPresets.forEach { preset ->
                        val isSel = rackNumber.trim().equals(preset.trim(), ignoreCase = true)
                        Surface(
                            onClick = { onRackNumberChange(preset) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Color(0xFF0284C7).copy(alpha = 0.12f) else Color.White,
                            border = BorderStroke(1.dp, if (isSel) Color(0xFF0284C7) else Color(0xFFCBD5E1))
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color(0xFF0369A1) else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StockInventoryCard(
    isBn: Boolean,
    stockUnitType: String,
    onStockUnitTypeChange: (String) -> Unit,
    stockText: String,
    onStockTextChange: (String) -> Unit,
    stripSizeText: String,
    onStripSizeChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isBn) "মজুদ / স্টক আপডেট" else "Stock Inventory Update",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                // Strip / Pcs Unit Segmented Control
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isStrip = stockUnitType == "strip"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isStrip) Color.White else Color.Transparent,
                        modifier = Modifier.clickable { onStockUnitTypeChange("strip") }
                    ) {
                        Text(
                            text = if (isBn) "পাতা" else "Strip",
                            fontSize = 11.sp,
                            fontWeight = if (isStrip) FontWeight.Bold else FontWeight.Medium,
                            color = if (isStrip) Color(0xFF0F172A) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (!isStrip) Color.White else Color.Transparent,
                        modifier = Modifier.clickable { onStockUnitTypeChange("pc") }
                    ) {
                        Text(
                            text = if (isBn) "পিস" else "Pcs",
                            fontSize = 11.sp,
                            fontWeight = if (!isStrip) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isStrip) Color(0xFF0F172A) else Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

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
                            text = if (isBn) "স্টক পরিমাণ (${if (stockUnitType == "strip") "পাতা" else "পিস"}) *" else "Stock Qty (${if (stockUnitType == "strip") "Strips" else "Pcs"}) *",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(1.2f)
                )

                OutlinedTextField(
                    value = stripSizeText,
                    onValueChange = onStripSizeChange,
                    label = {
                        Text(
                            text = if (isBn) "পাতা সাইজ (পিস)" else "Strip Size (Pcs)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = compactFieldColors(),
                    modifier = Modifier.weight(0.9f)
                )
            }

            // Quick Stock Increments
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "+ দ্রুত যোগ:" else "+ Quick Add:",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
                listOf(5, 10, 20, 50, 100).forEach { inc ->
                    Surface(
                        onClick = {
                            val current = toEnglishDigits(stockText).toDoubleOrNull() ?: 0.0
                            val updated = current + inc
                            val formatted = if (updated % 1.0 == 0.0) updated.toInt().toString() else "%.1f".format(Locale.US, updated)
                            onStockTextChange(formatted)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF059669).copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "+$inc",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}

@Composable
private fun PricingCard(
    isBn: Boolean,
    tpText: String,
    onTpChange: (String) -> Unit,
    mrpText: String,
    onMrpChange: (String) -> Unit,
    stripPriceText: String,
    onStripPriceChange: (String) -> Unit,
    packPriceText: String,
    onPackPriceChange: (String) -> Unit,
    profitPerUnit: Double,
    marginPercent: Double,
    parsedMrp: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        modifier = Modifier.size(26.dp)
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
                        text = if (isBn) "টিপি ও এমআরপি মূল্য" else "TP & MRP Pricing",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                if (parsedMrp > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (profitPerUnit > 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        border = BorderStroke(0.6.dp, if (profitPerUnit > 0) Color(0xFF10B981) else Color(0xFFEF4444))
                    ) {
                        val marginStr = "%.1f".format(Locale.US, marginPercent)
                        val formattedProfit = formatTaka(profitPerUnit)
                        Text(
                            text = if (isBn) "লাভ: $formattedProfit (${toBanglaDigits(marginStr)}%)" else "Profit: $formattedProfit ($marginStr%)",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (profitPerUnit > 0) Color(0xFF047857) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Row 1: Cost (TP) and MRP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tpText,
                    onValueChange = onTpChange,
                    label = {
                        Text(
                            text = if (isBn) "ক্রয়মূল্য / টিপি (৳)" else "Cost / TP (৳)",
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
                    value = mrpText,
                    onValueChange = onMrpChange,
                    label = {
                        Text(
                            text = if (isBn) "বিক্রয়মূল্য / এমআরপি *" else "MRP (per pc ৳) *",
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

            // Row 2: Strip Price and Pack Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = stripPriceText,
                    onValueChange = onStripPriceChange,
                    label = {
                        Text(
                            text = if (isBn) "পাতা মূল্য (৳)" else "Strip Price (৳)",
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

@Composable
private fun ExpiryBatchCard(
    isBn: Boolean,
    batchNumber: String,
    onBatchNumberChange: (String) -> Unit,
    expiryDateText: String,
    onExpiryDateChange: (String) -> Unit,
    onAddMonths: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEA580C).copy(alpha = 0.12f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFFC2410C),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = if (isBn) "মেয়াদ ও ব্যাচ ট্র্যাকিং" else "Expiry & Batch Tracking",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

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
                    placeholder = { Text("BX-00123", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
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
                            text = if (isBn) "মেয়াদ উত্তীর্ণের তারিখ" else "Expiry (YYYY-MM-DD)",
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

            // 1-Tap Expiry Quick Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "দ্রুত মেয়াদ:" else "Quick Expiry:",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
                listOf(
                    Pair(6, if (isBn) "+৬ মাস" else "+6 Mos"),
                    Pair(12, if (isBn) "+১ বছর" else "+1 Yr"),
                    Pair(24, if (isBn) "+২ বছর" else "+2 Yrs"),
                    Pair(36, if (isBn) "+৩ বছর" else "+3 Yrs")
                ).forEach { (m, label) ->
                    Surface(
                        onClick = { onAddMonths(m) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEA580C).copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}

@Composable
private fun AntibioticCard(
    isBn: Boolean,
    isAntibiotic: Boolean,
    onAntibioticChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isAntibiotic) Color(0xFFFEF2F2) else Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, if (isAntibiotic) Color(0xFFFECACA) else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    color = if (isAntibiotic) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.12f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isAntibiotic) Icons.Default.Warning else Icons.Default.Medication,
                            contentDescription = null,
                            tint = if (isAntibiotic) Color(0xFFDC2626) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = if (isBn) "অ্যান্টিবায়োটিক ওষুধ (Rx Prescription)" else "Antibiotic Medicine (Rx Required)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAntibiotic) Color(0xFFDC2626) else Color(0xFF0F172A)
                    )
                    Text(
                        text = if (isBn) "প্রেসক্রিপশন ছাড়া বিক্রি নিষিদ্ধ সতর্কতা দেখাবে" else "Enforces prescription/doctor record warning",
                        fontSize = 10.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Switch(
                checked = isAntibiotic,
                onCheckedChange = onAntibioticChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFDC2626)
                )
            )
        }
    }
}

@Composable
private fun ClinicalReferenceCard(
    isBn: Boolean,
    genericName: String?,
    isClinicalExpanded: Boolean,
    onToggleClinical: () -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Clickable header row with >= 48dp touch target
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleClinical() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isBn) "ক্লিনিক্যাল ড্রাগ রেফারেন্স" else "Clinical Drug Reference",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = genericName ?: (if (isBn) "জেনেরিক নির্দেশিকা" else "Generic clinical data"),
                            fontSize = 11.sp,
                            color = Color(0xFF7C3AED),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onToggleClinical,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isClinicalExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF64748B)
                    )
                }
            }

            AnimatedVisibility(visible = isClinicalExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
