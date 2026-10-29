package com.techyfiposmeds.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
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
import com.techyfiposmeds.utils.isAppBangla
import com.techyfiposmeds.utils.toBanglaDigits
import com.techyfiposmeds.data.clinical.AlertSeverity
import com.techyfiposmeds.data.clinical.ClinicalInteractionAlert

@Composable
fun ClinicalInteractionAdvisoryDialog(
    alerts: List<ClinicalInteractionAlert>,
    onDismiss: () -> Unit,
    onAcknowledge: () -> Unit,
    onRemoveItem: ((String) -> Unit)? = null
) {
    val isBn = isAppBangla()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFDC2626).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isBn) "ক্লিনিক্যাল ড্রাগ মিথস্ক্রিয়া সতর্কতা" else "Clinical Drug Interaction Advisory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val countStr = if (isBn) "${toBanglaDigits(alerts.size.toString())}টি" else "${alerts.size}"
                            Text(
                                text = if (isBn) "ওষুধের নিরাপত্তা ও ড্রাগ মিথস্ক্রিয়া সতর্কবার্তা ($countStr সনাক্ত)" else "Drug safety & clinical interaction alerts ($countStr detected)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Alerts List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        AlertCard(
                            alert = alert,
                            isBn = isBn,
                            onRemoveItem = onRemoveItem
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isBn) "কার্ট পরিবর্তন" else "Modify Cart", fontSize = 12.5.sp)
                    }

                    Button(
                        onClick = onAcknowledge,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text(if (isBn) "বোঝা গেছে" else "Acknowledge", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: ClinicalInteractionAlert,
    isBn: Boolean = isAppBangla(),
    onRemoveItem: ((String) -> Unit)?
) {
    val (badgeBg, badgeText, badgeLabel) = when (alert.severity) {
        AlertSeverity.HIGH_RISK -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), if (isBn) "🔴 মারাত্মক ঝুঁকি (HIGH RISK)" else "🔴 HIGH RISK")
        AlertSeverity.MODERATE -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), if (isBn) "🟡 মাঝারি ঝুঁকি (MODERATE)" else "🟡 MODERATE RISK")
        AlertSeverity.DUPLICATE_THERAPY -> Triple(Color(0xFFFFEDD5), Color(0xFFEA580C), if (isBn) "🟠 দ্বৈত উপাদান (DUPLICATE)" else "🟠 DUPLICATE THERAPY")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = badgeText.copy(alpha = 0.05f)
        ),
        border = BorderStroke(1.dp, badgeText.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Severity Chip & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeLabel,
                        color = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = if (isBn) alert.titleBn.ifBlank { alert.titleEn } else alert.titleEn,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Interacting Medicines Clash Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alert.medicineA,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "(${alert.genericA})",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Text(
                        text = "⚡",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = alert.medicineB,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "(${alert.genericB})",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Clinical Mechanism
            Text(
                text = if (isBn) alert.mechanismBn.ifBlank { alert.mechanismEn } else alert.mechanismEn.ifBlank { alert.mechanismBn },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isBn && alert.mechanismEn.isNotBlank() && alert.mechanismEn != alert.mechanismBn) {
                Text(
                    text = alert.mechanismEn,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Recommendation Box
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF10B981).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("💡", fontSize = 14.sp)
                    Column {
                        Text(
                            text = if (isBn) "ফার্মাসিস্টের পরামর্শ:" else "Pharmacist Advisory:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF047857)
                        )
                        Text(
                            text = if (isBn) alert.recommendationBn.ifBlank { alert.recommendationEn } else alert.recommendationEn.ifBlank { alert.recommendationBn },
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Quick Remove Button if supported
            if (onRemoveItem != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onRemoveItem(alert.medicineB) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove ${alert.medicineB.take(15)}", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
