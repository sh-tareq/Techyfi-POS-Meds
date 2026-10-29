package com.techyfiposmeds.ui.dialogs

import com.techyfiposmeds.utils.isAppBangla

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
import androidx.compose.material.icons.filled.Share
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
    val isBn = isAppBangla()
    val context = androidx.compose.ui.platform.LocalContext.current
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
                        title = if (isBn) "🩺 নির্দেশনা (Medical Indications)" else "🩺 Medical Indications",
                        content = clinicalInfo?.indication ?: "Indicated for fever, headache, body aches, neuralgia, arthralgia, and common cold symptoms.",
                        accentColor = Color(0xFF0284C7)
                    )

                    ClinicalSectionCard(
                        title = if (isBn) "💊 সেবন মাত্রা (Dosage & Administration)" else "💊 Dosage & Administration",
                        content = "Adult Dose:\n${clinicalInfo?.adultDose ?: "500mg-1000mg every 4-6 hours (Maximum 4000mg/day)"}\n\nChild Dose:\n${clinicalInfo?.childDose ?: "10-15 mg/kg body weight every 4-6 hours"}",
                        accentColor = Color(0xFF10B981)
                    )

                    ClinicalSectionCard(
                        title = if (isBn) "⛔ প্রতিনির্দেশনা (Contraindications)" else "⛔ Contraindications",
                        content = clinicalInfo?.contraIndication ?: "Severe hepatic or renal impairment, hypersensitivity to active compound.",
                        accentColor = Color(0xFFEF4444)
                    )

                    ClinicalSectionCard(
                        title = if (isBn) "⚠️ পার্শ্বপ্রতিক্রিয়া (Adverse Effects)" else "⚠️ Adverse Effects",
                        content = clinicalInfo?.sideEffect ?: "Skin rash, epigastric distress, transient leukopenia. Generally very well tolerated at therapeutic doses.",
                        accentColor = Color(0xFFF59E0B)
                    )

                    ClinicalSectionCard(
                        title = if (isBn) "🤰 গর্ভাবস্থায় ও সতর্কতা (Pregnancy & Precaution)" else "🤰 Pregnancy & Precaution",
                        content = clinicalInfo?.precaution ?: "Pregnancy Category B. Safe in recommended therapeutic doses. Avoid prolonged high-dose administration.",
                        accentColor = Color(0xFF8B5CF6)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val counselingText = buildString {
                            if (isBn) {
                                appendLine("📋 রোগীর সেবন নির্দেশিকা (PATIENT COUNSELING NOTE)")
                                appendLine("ওষুধ: ${medicine.brandName} (${medicine.dosageForm ?: ""})")
                                appendLine("জেনেরিক: ${medicine.genericName ?: ""}")
                                appendLine("----------------------------------------")
                                appendLine("🩺 নির্দেশনা: ${clinicalInfo?.indication ?: "চিকিৎসকের পরামর্শ অনুযায়ী সেব্য"}")
                                appendLine("💊 সেবন মাত্রা: ${clinicalInfo?.adultDose ?: "১টি করে দিনে ২ বার খাওয়ার পর"}")
                                if (!clinicalInfo?.administration.isNullOrBlank()) {
                                    appendLine("🍽️ নিয়ম: ${clinicalInfo?.administration}")
                                }
                                if (!clinicalInfo?.precaution.isNullOrBlank()) {
                                    appendLine("⚠️ সতর্কতা: ${clinicalInfo?.precaution}")
                                }
                                appendLine("----------------------------------------")
                                appendLine("সুস্থ থাকুন - ফার্মেসি পেশাদার সেবা")
                            } else {
                                appendLine("📋 PATIENT COUNSELING NOTE")
                                appendLine("Medicine: ${medicine.brandName} (${medicine.dosageForm ?: ""})")
                                appendLine("Generic: ${medicine.genericName ?: ""}")
                                appendLine("----------------------------------------")
                                appendLine("🩺 Indication: ${clinicalInfo?.indication ?: "Use as directed by physician"}")
                                appendLine("💊 Dosage: ${clinicalInfo?.adultDose ?: "1 unit 2 times daily after meal"}")
                                if (!clinicalInfo?.administration.isNullOrBlank()) {
                                    appendLine("🍽️ Directions: ${clinicalInfo?.administration}")
                                }
                                if (!clinicalInfo?.precaution.isNullOrBlank()) {
                                    appendLine("⚠️ Precaution: ${clinicalInfo?.precaution}")
                                }
                                appendLine("----------------------------------------")
                                appendLine("Stay Well - Professional Pharmacy Care")
                            }
                        }
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, counselingText)
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, if (isBn) "রোগীর সেবন নির্দেশিকা শেয়ার করুন" else "Share Patient Counseling Note"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isBn) "রোগীর সেবন নির্দেশিকা শেয়ার করুন" else "Share Patient Counseling Note", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
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
