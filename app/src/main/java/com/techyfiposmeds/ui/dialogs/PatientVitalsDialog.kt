package com.techyfiposmeds.ui.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.data.clinical.PatientVitalsHelper
import com.techyfiposmeds.data.db.PatientVitalRecord
import com.techyfiposmeds.utils.DateFormatters
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientVitalsDialog(
    allVitals: List<PatientVitalRecord>,
    prefilledPhone: String? = null,
    prefilledName: String? = null,
    onDismiss: () -> Unit,
    onSaveVital: (PatientVitalRecord) -> Unit,
    onDeleteVital: (PatientVitalRecord) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Form inputs
    var phoneInput by remember { mutableStateOf(prefilledPhone ?: "") }
    var nameInput by remember { mutableStateOf(prefilledName ?: "") }
    var systolicInput by remember { mutableStateOf("") }
    var diastolicInput by remember { mutableStateOf("") }
    var pulseInput by remember { mutableStateOf("") }
    var sugarInput by remember { mutableStateOf("") }
    var sugarType by remember { mutableStateOf("RBS") } // "RBS", "FBS", "2HABF"
    var weightInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var tempInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    // Evaluated metrics
    val sysInt = systolicInput.toIntOrNull()
    val diaInt = diastolicInput.toIntOrNull()
    val pulseInt = pulseInput.toIntOrNull()
    val bpClass = remember(sysInt, diaInt) {
        if (sysInt != null && diaInt != null) {
            PatientVitalsHelper.classifyBloodPressure(sysInt, diaInt)
        } else null
    }

    val sugarDbl = sugarInput.toDoubleOrNull()
    val sugarClass = remember(sugarDbl, sugarType) {
        if (sugarDbl != null) {
            PatientVitalsHelper.classifyBloodSugar(sugarDbl, sugarType)
        } else null
    }

    val weightDbl = weightInput.toDoubleOrNull()
    val heightDbl = heightInput.toDoubleOrNull()
    val calculatedBmi = remember(weightDbl, heightDbl) {
        if (weightDbl != null && heightDbl != null && heightDbl > 0) {
            PatientVitalsHelper.calculateBmi(weightDbl, heightDbl)
        } else null
    }
    val bmiClass = remember(calculatedBmi) {
        if (calculatedBmi != null && calculatedBmi > 0) {
            PatientVitalsHelper.classifyBmi(calculatedBmi)
        } else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE11D48).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "রোগীর স্বাস্থ্য ভাইটালস ট্র্যাকার",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "রক্তচাপ (BP), সুগার (Sugar), পালস ও বিএমআই (BMI)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "নতুন পরিমাপ রেকর্ড (New)",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "ইতিহাস ও ট্রেন্ড (${allVitals.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = { Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    // TAB 0: RECORD NEW VITALS
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Patient Identifiers
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "রোগীর তথ্য (Patient Profile)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = phoneInput,
                                        onValueChange = { phoneInput = it },
                                        label = { Text("মোবাইল নম্বর (Phone)") },
                                        placeholder = { Text("017XXXXXXXX") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = nameInput,
                                        onValueChange = { nameInput = it },
                                        label = { Text("রোগীর নাম (Name)") },
                                        placeholder = { Text("ঐচ্ছিক / Optional") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Blood Pressure & Pulse Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(
                                1.dp,
                                bpClass?.let { Color(it.colorHex).copy(alpha = 0.6f) }
                                    ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFE11D48),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "রক্তচাপ ও পালস (Blood Pressure & Pulse)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (bpClass != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(bpClass.colorHex).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(bpClass.colorHex))
                                        ) {
                                            Text(
                                                text = bpClass.labelBn,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(bpClass.colorHex),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = systolicInput,
                                        onValueChange = { systolicInput = it },
                                        label = { Text("সিস্টোলিক (Sys)") },
                                        placeholder = { Text("120") },
                                        trailingIcon = { Text("mmHg", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = diastolicInput,
                                        onValueChange = { diastolicInput = it },
                                        label = { Text("ডায়াস্টোলিক (Dia)") },
                                        placeholder = { Text("80") },
                                        trailingIcon = { Text("mmHg", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = pulseInput,
                                        onValueChange = { pulseInput = it },
                                        label = { Text("পালস (Pulse)") },
                                        placeholder = { Text("72") },
                                        trailingIcon = { Text("BPM", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                if (bpClass != null) {
                                    Text(
                                        text = "💡 পরামর্শ: ${bpClass.adviceBn}",
                                        fontSize = 11.sp,
                                        color = Color(bpClass.colorHex),
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }

                        // Blood Glucose Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(
                                1.dp,
                                sugarClass?.let { Color(it.colorHex).copy(alpha = 0.6f) }
                                    ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.WaterDrop,
                                            contentDescription = null,
                                            tint = Color(0xFF3B82F6),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "ব্লাড গ্লুকোজ / সুগার (Blood Sugar)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (sugarClass != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(sugarClass.colorHex).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(sugarClass.colorHex))
                                        ) {
                                            Text(
                                                text = sugarClass.labelBn,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(sugarClass.colorHex),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // Test Type Selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("RBS" to "র‍্যান্ডম (RBS)", "FBS" to "খালি পেটে (FBS)", "2HABF" to "নাস্তার ২ ঘণ্টা পর").forEach { (type, label) ->
                                        FilterChip(
                                            selected = sugarType == type,
                                            onClick = { sugarType = type },
                                            label = { Text(label, fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = sugarInput,
                                        onValueChange = { sugarInput = it },
                                        label = { Text("সুগার মান (Blood Sugar)") },
                                        placeholder = { Text("e.g. 6.4") },
                                        trailingIcon = { Text("mmol/L", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    if (sugarDbl != null && sugarDbl > 0) {
                                        val mgDl = PatientVitalsHelper.mmolToMgDl(sugarDbl)
                                        Column(
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .background(
                                                    MaterialTheme.colorScheme.surface,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text("সমপরিমাণ (Equivalent):", fontSize = 10.sp, color = Color.Gray)
                                            Text(
                                                text = "$mgDl mg/dL",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                if (sugarClass != null) {
                                    Text(
                                        text = "💡 পরামর্শ: ${sugarClass.adviceBn}",
                                        fontSize = 11.sp,
                                        color = Color(sugarClass.colorHex),
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }

                        // Physical Vitals: Weight, Height, BMI, Temperature
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "শারীরিক গঠন ও তাপমাত্রা (Weight, Height & BMI)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (bmiClass != null && calculatedBmi != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(bmiClass.colorHex).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(bmiClass.colorHex))
                                        ) {
                                            Text(
                                                text = "BMI $calculatedBmi: ${bmiClass.labelBn}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(bmiClass.colorHex),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = weightInput,
                                        onValueChange = { weightInput = it },
                                        label = { Text("ওজন (Weight)") },
                                        placeholder = { Text("65") },
                                        trailingIcon = { Text("kg", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = heightInput,
                                        onValueChange = { heightInput = it },
                                        label = { Text("উচ্চতা (Height)") },
                                        placeholder = { Text("165") },
                                        trailingIcon = { Text("cm", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = tempInput,
                                        onValueChange = { tempInput = it },
                                        label = { Text("তাপমাত্রা (Temp)") },
                                        placeholder = { Text("98.4") },
                                        trailingIcon = { Text("°F", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(end = 4.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Clinical Notes
                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text("ফার্মাসিস্টের বিশেষ মন্তব্য ও উপদেশ (Clinical Advice)") },
                            placeholder = { Text("যেমন: কাঁচা লবণ বর্জন, দিনে ৩০ মিনিট হাঁটা, ৩ দিন পর পুনঃনিরীক্ষণ...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                        )
                    }

                    // Action Bottom Bar
                    Surface(
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // WhatsApp Share Button
                            OutlinedButton(
                                onClick = {
                                    val summary = generateVitalsSummaryText(
                                        phone = phoneInput,
                                        name = nameInput,
                                        sys = sysInt,
                                        dia = diaInt,
                                        pulse = pulseInt,
                                        sugar = sugarDbl,
                                        sugarType = sugarType,
                                        weight = weightDbl,
                                        height = heightDbl,
                                        temp = tempInput.toDoubleOrNull(),
                                        notes = notesInput
                                    )
                                    shareViaWhatsApp(context, summary)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("হোয়াটসঅ্যাপ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Copy Slip Text Button
                            OutlinedButton(
                                onClick = {
                                    val summary = generateVitalsSummaryText(
                                        phone = phoneInput,
                                        name = nameInput,
                                        sys = sysInt,
                                        dia = diaInt,
                                        pulse = pulseInt,
                                        sugar = sugarDbl,
                                        sugarType = sugarType,
                                        weight = weightDbl,
                                        height = heightDbl,
                                        temp = tempInput.toDoubleOrNull(),
                                        notes = notesInput
                                    )
                                    clipboardManager.setText(AnnotatedString(summary))
                                    Toast.makeText(context, "রিপোর্ট কপি হয়েছে (Copied)", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("কপি স্লিপ", fontSize = 13.sp)
                            }

                            // Save Vital Record
                            Button(
                                onClick = {
                                    val record = PatientVitalRecord(
                                        customerPhone = phoneInput.trim().ifBlank { null },
                                        customerName = nameInput.trim().ifBlank { null },
                                        systolicBp = sysInt,
                                        diastolicBp = diaInt,
                                        pulseBpm = pulseInt,
                                        bloodSugarMmol = sugarDbl,
                                        sugarTestType = sugarType,
                                        weightKg = weightDbl,
                                        temperatureF = tempInput.toDoubleOrNull(),
                                        clinicalNotes = notesInput.trim().ifBlank { null },
                                        recordedAt = System.currentTimeMillis()
                                    )
                                    onSaveVital(record)
                                    Toast.makeText(context, "ভাইটালস সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                                    selectedTab = 1
                                },
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("সংরক্ষণ করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // TAB 1: HISTORY & TRENDS
                    var filterByPhoneOnly by remember { mutableStateOf(phoneInput.isNotBlank()) }
                    val displayedVitals = remember(allVitals, filterByPhoneOnly, phoneInput) {
                        if (filterByPhoneOnly && phoneInput.isNotBlank()) {
                            allVitals.filter { it.customerPhone?.contains(phoneInput.trim()) == true }
                        } else {
                            allVitals
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (phoneInput.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (filterByPhoneOnly) "কেবল $phoneInput এর হিস্ট্রি" else "সকল রোগীর হিস্ট্রি (${allVitals.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { filterByPhoneOnly = !filterByPhoneOnly }
                                ) {
                                    Text(if (filterByPhoneOnly) "সব দেখুন" else "রোগীর নম্বর ফিল্টার", fontSize = 12.sp)
                                }
                            }
                        }

                        if (displayedVitals.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.HistoryEdu,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "কোনো পূর্ববর্তী ভাইটাল রেকর্ড পাওয়া যায়নি",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(displayedVitals, key = { it.id }) { record ->
                                    VitalRecordHistoryCard(
                                        record = record,
                                        onShare = {
                                            val text = generateVitalsSummaryText(
                                                phone = record.customerPhone ?: "",
                                                name = record.customerName ?: "",
                                                sys = record.systolicBp,
                                                dia = record.diastolicBp,
                                                pulse = record.pulseBpm,
                                                sugar = record.bloodSugarMmol,
                                                sugarType = record.sugarTestType,
                                                weight = record.weightKg,
                                                height = null,
                                                temp = record.temperatureF,
                                                notes = record.clinicalNotes
                                            )
                                            shareViaWhatsApp(context, text)
                                        },
                                        onDelete = { onDeleteVital(record) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalRecordHistoryCard(
    record: PatientVitalRecord,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(record.recordedAt) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(record.recordedAt))
    }

    val bpClass = remember(record.systolicBp, record.diastolicBp) {
        if (record.systolicBp != null && record.diastolicBp != null) {
            PatientVitalsHelper.classifyBloodPressure(record.systolicBp, record.diastolicBp)
        } else null
    }

    val sugarClass = remember(record.bloodSugarMmol, record.sugarTestType) {
        if (record.bloodSugarMmol != null) {
            PatientVitalsHelper.classifyBloodSugar(record.bloodSugarMmol, record.sugarTestType)
        } else null
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = record.customerName?.ifBlank { null } ?: record.customerPhone ?: "রোগী (Walk-in)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Vitals Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // BP
                if (record.systolicBp != null && record.diastolicBp != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bpClass?.let { Color(it.colorHex).copy(alpha = 0.12f) } ?: MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, bpClass?.let { Color(it.colorHex).copy(alpha = 0.4f) } ?: Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("রক্তচাপ (BP)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${record.systolicBp}/${record.diastolicBp} mmHg",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = bpClass?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.onSurface
                            )
                            if (bpClass != null) {
                                Text(bpClass.labelBn, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(bpClass.colorHex))
                            }
                        }
                    }
                }

                // Sugar
                if (record.bloodSugarMmol != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sugarClass?.let { Color(it.colorHex).copy(alpha = 0.12f) } ?: MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, sugarClass?.let { Color(it.colorHex).copy(alpha = 0.4f) } ?: Color.Gray),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("সুগার (${record.sugarTestType})", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${record.bloodSugarMmol} mmol/L",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = sugarClass?.let { Color(it.colorHex) } ?: MaterialTheme.colorScheme.onSurface
                            )
                            if (sugarClass != null) {
                                Text(sugarClass.labelBn, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(sugarClass.colorHex))
                            }
                        }
                    }
                }

                // Weight / Pulse
                if (record.weightKg != null || record.pulseBpm != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            if (record.weightKg != null) {
                                Text("ওজন: ${record.weightKg} kg", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                            if (record.pulseBpm != null) {
                                Text("পালস: ${record.pulseBpm} BPM", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            if (!record.clinicalNotes.isNullOrBlank()) {
                Text(
                    text = "উপদেশ: ${record.clinicalNotes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private fun generateVitalsSummaryText(
    phone: String,
    name: String,
    sys: Int?,
    dia: Int?,
    pulse: Int?,
    sugar: Double?,
    sugarType: String,
    weight: Double?,
    height: Double?,
    temp: Double?,
    notes: String?
): String {
    val dateStr = SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.getDefault()).format(Date())
    val sb = StringBuilder()
    sb.append("📋 রোগীর স্বাস্থ্য ভাইটালস রিপোর্ট (Patient Vitals Report)\n")
    sb.append("তারিখ: $dateStr\n")
    if (name.isNotBlank()) sb.append("রোগীর নাম: $name\n")
    if (phone.isNotBlank()) sb.append("মোবাইল: $phone\n")
    sb.append("------------------------------------\n")

    if (sys != null && dia != null) {
        val bpClass = PatientVitalsHelper.classifyBloodPressure(sys, dia)
        sb.append("❤️ রক্তচাপ (Blood Pressure):\n")
        sb.append("  মান: $sys / $dia mmHg")
        if (pulse != null) sb.append(" (পালস: $pulse BPM)")
        sb.append("\n  অবস্থা: ${bpClass.labelBn} (${bpClass.labelEn})\n")
    }

    if (sugar != null) {
        val sugarClass = PatientVitalsHelper.classifyBloodSugar(sugar, sugarType)
        val mgDl = PatientVitalsHelper.mmolToMgDl(sugar)
        sb.append("🩸 ব্লাড সুগার (Blood Sugar - $sugarType):\n")
        sb.append("  মান: $sugar mmol/L ($mgDl mg/dL)\n")
        sb.append("  অবস্থা: ${sugarClass.labelBn}\n")
    }

    if (weight != null) {
        sb.append("⚖️ শারীরিক গঠন: ওজন $weight kg")
        if (height != null && height > 0) {
            val bmi = PatientVitalsHelper.calculateBmi(weight, height)
            val bClass = PatientVitalsHelper.classifyBmi(bmi)
            sb.append(", উচ্চতা $height cm, BMI: $bmi (${bClass.labelBn})")
        }
        sb.append("\n")
    }

    if (temp != null) {
        sb.append("🌡️ তাপমাত্রা: $temp °F\n")
    }

    if (!notes.isNullOrBlank()) {
        sb.append("\n💡 ফার্মাসিস্ট পরামর্শ: $notes\n")
    }

    sb.append("------------------------------------\n")
    sb.append("⚕️ Techyfi Pharma & Community Healthcare\n")
    return sb.toString()
}

private fun shareViaWhatsApp(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "রোগীর স্বাস্থ্য রিপোর্ট শেয়ার করুন"))
    }
}
