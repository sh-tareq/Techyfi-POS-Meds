package com.techyfiposmeds.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.techyfiposmeds.R
import com.techyfiposmeds.utils.isAppBangla
import com.techyfiposmeds.data.InvoiceSettingsData
import com.techyfiposmeds.utils.DosageLabelBitmapGenerator
import com.techyfiposmeds.utils.printLabelImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DosageLabelDialog(
    storeName: String,
    storePhone: String,
    initialPatientName: String,
    medicineName: String,
    genericName: String? = null,
    initialDosageSchedule: String,
    batchNumber: String? = null,
    expiryDate: String? = null,
    settings: InvoiceSettingsData = InvoiceSettingsData(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isBn = isAppBangla()
    var patientName by remember { mutableStateOf(initialPatientName) }
    var dosageSchedule by remember {
        mutableStateOf(if (initialDosageSchedule.isNotBlank()) initialDosageSchedule else if (isBn) "১ + ০ + ১" else "1 + 0 + 1")
    }
    var foodTiming by remember { mutableStateOf(if (isBn) "খাওয়ার পর ভরা পেটে" else "After meals (Full stomach)") }
    var copies by remember { mutableIntStateOf(1) }
    var isPrinting by remember { mutableStateOf(false) }

    val labelBitmap = remember(storeName, storePhone, patientName, medicineName, genericName, dosageSchedule, foodTiming, batchNumber, expiryDate) {
        DosageLabelBitmapGenerator.generateDosageLabelBitmap(
            context = context,
            storeName = storeName,
            storePhone = storePhone,
            patientName = patientName,
            medicineName = medicineName,
            genericName = genericName,
            dosageSchedule = dosageSchedule,
            foodTiming = foodTiming,
            batchNumber = batchNumber,
            expiryDate = expiryDate
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
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
                            color = Color(0xFF0284C7).copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🏷️", fontSize = 20.sp)
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.dosage_label_dialog_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.dosage_label_dialog_subtitle),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Realistic Sticker Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Image(
                                bitmap = labelBitmap.asImageBitmap(),
                                contentDescription = "50x30mm Adhesive Sticker Preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(384f / 240f)
                                    .padding(4.dp)
                            )
                        }
                    }

                    // Patient Name Input
                    OutlinedTextField(
                        value = patientName,
                        onValueChange = { patientName = it },
                        label = { Text(stringResource(R.string.patient_name_label), fontSize = 11.5.sp) },
                        placeholder = { Text(if (isBn) "রোগীর নাম লিখুন" else "Enter patient name", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Quick Dosage Selector Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "💊 " + stringResource(R.string.dosage_quick_select),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val dosagePresets = if (isBn) listOf("১ + ০ + ১", "১ + ১ + ১", "০ + ১ + ০", "১ + ০ + ০", "০ + ০ + ১", "১ + ০ + ১ (৩ দিন)", "প্রয়োজনে (SOS)") else listOf("1 + 0 + 1", "1 + 1 + 1", "0 + 1 + 0", "1 + 0 + 0", "0 + 0 + 1", "1 + 0 + 1 (3 days)", "As needed (SOS)")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dosagePresets.forEach { preset ->
                                val isSelected = dosageSchedule == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { dosageSchedule = preset },
                                    label = { Text(preset, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0284C7),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Food Timing Selector Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "🍽️ " + stringResource(R.string.timing_quick_select),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val timingPresets = if (isBn) listOf("খাওয়ার পর ভরা পেটে", "খাওয়ার ৩০ মিনিট আগে", "সকালে খালি পেটে", "রাতে শোবার আগে", "পর্যাপ্ত জল পান করুন") else listOf("After meals (Full stomach)", "30 mins before meals", "Empty stomach in morning", "Before bedtime", "Drink plenty of water")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            timingPresets.forEach { preset ->
                                val isSelected = foodTiming == preset
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { foodTiming = preset },
                                    label = { Text(preset, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0D9488),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Copies Stepper
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_copies_count),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { if (copies > 1) copies-- },
                                modifier = Modifier.size(34.dp).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = "$copies",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { if (copies < 50) copies++ },
                                modifier = Modifier.size(34.dp).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Share / Save Button
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val cacheDir = File(context.cacheDir, "labels").apply { mkdirs() }
                                    val file = File(cacheDir, "dosage_sticker_${System.currentTimeMillis()}.png")
                                    FileOutputStream(file).use { out ->
                                        labelBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_label_title)))
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.42f).height(48.dp).minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.share_label_btn), fontSize = 12.sp)
                    }

                    // Direct Print Button
                    Button(
                        onClick = {
                            isPrinting = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val outcome = printLabelImages(
                                        context = context,
                                        activity = null,
                                        labels = listOf(labelBitmap),
                                        widthMm = 50f,
                                        heightMm = 30f,
                                        copies = copies,
                                        jobName = "DosageLabel_${medicineName.take(10)}",
                                        settings = settings
                                    )
                                    withContext(Dispatchers.Main) {
                                        isPrinting = false
                                        Toast.makeText(context, context.getString(R.string.label_print_sent_toast), Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isPrinting = false
                                        Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isPrinting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.weight(0.58f).height(48.dp).minimumInteractiveComponentSize()
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.print_dosage_sticker_btn), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
