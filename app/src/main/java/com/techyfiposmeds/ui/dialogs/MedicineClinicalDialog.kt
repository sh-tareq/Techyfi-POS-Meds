package com.techyfiposmeds.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.R
import com.techyfiposmeds.data.ClinicalGeneric
import com.techyfiposmeds.data.MedicineItem

@Composable
fun MedicineClinicalDialog(
    medicine: MedicineItem,
    clinicalInfo: ClinicalGeneric?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Clinical Drug Reference",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = medicine.genericName ?: "Generic N/A",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B5CF6),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClinicalSectionCard(
                        title = "🩺 Medical Indications (নির্দেশনা)",
                        content = clinicalInfo?.indication ?: "Indicated for fever, headache, body aches, neuralgia, arthralgia, and common cold symptoms.",
                        accentColor = Color(0xFF0284C7)
                    )

                    ClinicalSectionCard(
                        title = "💊 Dosage & Administration (সেবন মাত্রা)",
                        content = "Adult Dose:\n${clinicalInfo?.adultDose ?: "500mg-1000mg every 4-6 hours (Maximum 4000mg/day)"}\n\nChild Dose:\n${clinicalInfo?.childDose ?: "10-15 mg/kg body weight every 4-6 hours"}",
                        accentColor = Color(0xFF10B981)
                    )

                    ClinicalSectionCard(
                        title = "⛔ Contraindications (প্রতিনির্দেশনা)",
                        content = clinicalInfo?.contraIndication ?: "Severe hepatic or renal impairment, hypersensitivity to active compound.",
                        accentColor = Color(0xFFEF4444)
                    )

                    ClinicalSectionCard(
                        title = "⚠️ Adverse Effects (পার্শ্বপ্রতিক্রিয়া)",
                        content = clinicalInfo?.sideEffect ?: "Skin rash, epigastric distress, transient leukopenia. Generally very well tolerated at therapeutic doses.",
                        accentColor = Color(0xFFF59E0B)
                    )

                    ClinicalSectionCard(
                        title = "🤰 Pregnancy & Precaution (গর্ভাবস্থায় ও সতর্কতা)",
                        content = clinicalInfo?.precaution ?: "Pregnancy Category B. Safe in recommended therapeutic doses. Avoid prolonged high-dose administration.",
                        accentColor = Color(0xFF8B5CF6)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClinicalSectionCard(
    title: String,
    content: String,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = 0.35f))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
        }
    }
}
