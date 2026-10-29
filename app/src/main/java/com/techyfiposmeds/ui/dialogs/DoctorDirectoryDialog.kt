package com.techyfiposmeds.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.utils.isAppBangla
import com.techyfiposmeds.utils.toBanglaDigits
import com.techyfiposmeds.data.db.PrescribingDoctor
import com.techyfiposmeds.utils.DateFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDirectoryDialog(
    doctors: List<PrescribingDoctor>,
    onDismiss: () -> Unit,
    onSelectDoctor: ((PrescribingDoctor) -> Unit)? = null,
    onSaveDoctor: (PrescribingDoctor) -> Unit,
    onDeleteDoctor: (PrescribingDoctor) -> Unit
) {
    val context = LocalContext.current
    val isBn = isAppBangla()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSpecialty by remember { mutableStateOf<String?>(null) }
    var isAddingOrEditing by remember { mutableStateOf(false) }
    var editingDoctor by remember { mutableStateOf<PrescribingDoctor?>(null) }

    // Form fields
    var nameField by remember { mutableStateOf("") }
    var bmdcField by remember { mutableStateOf("") }
    var degreesField by remember { mutableStateOf("") }
    var specialtyField by remember { mutableStateOf("") }
    var clinicField by remember { mutableStateOf("") }
    var phoneField by remember { mutableStateOf("") }

    val filteredDoctors = remember(doctors, searchQuery, selectedSpecialty) {
        doctors.filter { doc ->
            val matchQuery = searchQuery.isBlank() ||
                doc.name.contains(searchQuery, ignoreCase = true) ||
                doc.bmdcRegNo.contains(searchQuery, ignoreCase = true) ||
                doc.clinicOrHospital.contains(searchQuery, ignoreCase = true) ||
                doc.specialty.contains(searchQuery, ignoreCase = true)
            val matchSpecialty = selectedSpecialty == null || doc.specialty.contains(selectedSpecialty!!, ignoreCase = true)
            matchQuery && matchSpecialty
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE11D48).copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🩺", fontSize = 20.sp)
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.doctor_directory_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.doctor_directory_subtitle, doctors.size),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!isAddingOrEditing) {
                            FilledTonalButton(
                                onClick = {
                                    editingDoctor = null
                                    nameField = ""
                                    bmdcField = ""
                                    degreesField = ""
                                    specialtyField = ""
                                    clinicField = ""
                                    phoneField = ""
                                    isAddingOrEditing = true
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.add_doctor_btn), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (isAddingOrEditing) {
                    // Add/Edit Form
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (editingDoctor != null) stringResource(R.string.edit_doctor_title) else stringResource(R.string.new_doctor_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = nameField,
                                onValueChange = { nameField = it },
                                label = { Text(stringResource(R.string.doctor_name_label), fontSize = 11.sp) },
                                placeholder = { Text(if (isBn) "ডা. মোঃ রফিকুল ইসলাম" else "Dr. Md. Rafiqul Islam", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = bmdcField,
                                    onValueChange = { bmdcField = it },
                                    label = { Text(stringResource(R.string.doctor_bmdc_label), fontSize = 11.sp) },
                                    placeholder = { Text("A-12345", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = phoneField,
                                    onValueChange = { phoneField = it },
                                    label = { Text(stringResource(R.string.doctor_phone_label), fontSize = 11.sp) },
                                    placeholder = { Text("017XXXXXXXX", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1.2f),
                                    singleLine = true
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = degreesField,
                                    onValueChange = { degreesField = it },
                                    label = { Text(stringResource(R.string.doctor_degrees_label), fontSize = 11.sp) },
                                    placeholder = { Text("MBBS, FCPS", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = specialtyField,
                                    onValueChange = { specialtyField = it },
                                    label = { Text(stringResource(R.string.doctor_specialty_label), fontSize = 11.sp) },
                                    placeholder = { Text("Medicine", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = clinicField,
                                onValueChange = { clinicField = it },
                                label = { Text(stringResource(R.string.doctor_clinic_label), fontSize = 11.sp) },
                                placeholder = { Text(if (isBn) "ঢাকা মেডিকেল কলেজ হাসপাতাল / স্থানীয় চেম্বার" else "Dhaka Medical College / Local Chamber", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(Modifier.weight(1f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { isAddingOrEditing = false },
                                    modifier = Modifier.weight(1f).height(46.dp).minimumInteractiveComponentSize(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(stringResource(R.string.cancel), fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        if (nameField.isNotBlank()) {
                                            val doc = (editingDoctor ?: PrescribingDoctor(name = "", bmdcRegNo = "")).copy(
                                                name = nameField.trim(),
                                                bmdcRegNo = bmdcField.trim().ifBlank { "A-${(10000..99999).random()}" },
                                                degrees = degreesField.trim(),
                                                specialty = specialtyField.trim(),
                                                clinicOrHospital = clinicField.trim(),
                                                phone = phoneField.trim()
                                            )
                                            onSaveDoctor(doc)
                                            isAddingOrEditing = false
                                        }
                                    },
                                    enabled = nameField.isNotBlank(),
                                    modifier = Modifier.weight(1f).height(46.dp).minimumInteractiveComponentSize(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48))
                                ) {
                                    Text(stringResource(R.string.btn_save_doctor), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Search & Filter
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_doctor_hint), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    // Specialty filter chips
                    val specialties = listOf("Medicine", "Pediatrics", "Cardiology", "Gynecology", "General")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedSpecialty == null,
                            onClick = { selectedSpecialty = null },
                            label = { Text(if (isBn) "সব বিভাগ" else "All Specialties", fontSize = 11.sp) }
                        )
                        specialties.forEach { spec ->
                            val isSel = selectedSpecialty?.contains(spec, ignoreCase = true) == true
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedSpecialty = if (isSel) null else spec },
                                label = { Text(spec, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFE11D48),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Doctors List
                    if (filteredDoctors.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("👨‍⚕️", fontSize = 36.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.no_doctors_found),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredDoctors, key = { it.id }) { doc ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = doc.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFFEE2E2),
                                                    border = BorderStroke(0.6.dp, Color(0xFFFECACA))
                                                ) {
                                                    Text(
                                                        text = "BMDC: ${doc.bmdcRegNo}",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFFBE123C),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }

                                            val detailsText = listOfNotNull(
                                                doc.degrees.takeIf { it.isNotBlank() },
                                                doc.specialty.takeIf { it.isNotBlank() }
                                            ).joinToString(" • ")
                                            if (detailsText.isNotBlank()) {
                                                Text(detailsText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            if (doc.clinicOrHospital.isNotBlank()) {
                                                Text(
                                                    text = "🏥 ${doc.clinicOrHospital}",
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(top = 2.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFECFDF5)
                                                ) {
                                                    Text(
                                                        text = if (isBn) "📋 ${toBanglaDigits(doc.prescriptionCount.toString())}টি প্রেসক্রিপশন" else "📋 ${doc.prescriptionCount} Prescriptions",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF047857),
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                    )
                                                }
                                                if (doc.phone.isNotBlank()) {
                                                    Text("📞 ${doc.phone}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (onSelectDoctor != null) {
                                                Button(
                                                    onClick = {
                                                        onSelectDoctor(doc)
                                                        onDismiss()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                                                    modifier = Modifier.height(34.dp).minimumInteractiveComponentSize()
                                                ) {
                                                    Text(stringResource(R.string.select_doctor_btn), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    editingDoctor = doc
                                                    nameField = doc.name
                                                    bmdcField = doc.bmdcRegNo
                                                    degreesField = doc.degrees
                                                    specialtyField = doc.specialty
                                                    clinicField = doc.clinicOrHospital
                                                    phoneField = doc.phone
                                                    isAddingOrEditing = true
                                                },
                                                modifier = Modifier.size(32.dp).minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }

                                            if (doc.phone.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${doc.phone}"))
                                                        context.startActivity(intent)
                                                    },
                                                    modifier = Modifier.size(32.dp).minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color(0xFF0D9488), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
