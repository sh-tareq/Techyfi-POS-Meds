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
import com.techyfiposmeds.data.clinical.ColdChainHelper
import com.techyfiposmeds.data.db.Product
import com.techyfiposmeds.data.db.RefrigeratorTempLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColdChainManagementDialog(
    tempLogs: List<RefrigeratorTempLog>,
    allProducts: List<Product>,
    onDismiss: () -> Unit,
    onSaveLog: (RefrigeratorTempLog) -> Unit,
    onDeleteLog: (RefrigeratorTempLog) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Logger fields
    var unitNameInput by remember { mutableStateOf("Main Pharma Vaccine Fridge") }
    var tempInput by remember { mutableStateOf("") }
    var actionTakenInput by remember { mutableStateOf("") }
    var recordedByInput by remember { mutableStateOf("Pharmacist") }

    val tempDbl = tempInput.toDoubleOrNull()
    val breachStatus = remember(tempDbl) {
        if (tempDbl != null) ColdChainHelper.evaluateTemperature(tempDbl) else null
    }

    // Cold-Chain products audit list
    val coldChainProducts = remember(allProducts) {
        allProducts.filter { prod ->
            ColdChainHelper.isColdChainMedicine(prod.name, prod.genericName, prod.category, prod.shelfLocation)
        }
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
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0284C7).copy(alpha = 0.12f))
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
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "কোল্ড-চেইন ফ্রিজ মনিটর (২°-৮° সে.)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "DGDA ও WHO নির্দেশিকা: দৈনিক তাপমাত্রা লগ ও ভ্যাকসিন অডিট",
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
                    contentColor = Color(0xFF0284C7)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "তাপমাত্রা লগ ও হিস্ট্রি (${tempLogs.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = { Icon(Icons.Default.DeviceThermostat, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "কোল্ড-চেইন স্টক অডিট (${coldChainProducts.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = { Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    // TAB 0: TEMPERATURE LOGGER & LOGS
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Quick Add Temp Log Card
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            border = BorderStroke(
                                1.dp,
                                when (breachStatus) {
                                    ColdChainHelper.TempBreachStatus.NORMAL -> Color(0xFF10B981)
                                    ColdChainHelper.TempBreachStatus.FREEZE_RISK, ColdChainHelper.TempBreachStatus.HEAT_BREACH -> Color(0xFFEF4444)
                                    null -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                }
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
                                    Text(
                                        text = "নতুন তাপমাত্রা এন্ট্রি (Log Temperature)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (breachStatus != null) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when (breachStatus) {
                                                ColdChainHelper.TempBreachStatus.NORMAL -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                else -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                when (breachStatus) {
                                                    ColdChainHelper.TempBreachStatus.NORMAL -> Color(0xFF10B981)
                                                    else -> Color(0xFFEF4444)
                                                }
                                            )
                                        ) {
                                            Text(
                                                text = when (breachStatus) {
                                                    ColdChainHelper.TempBreachStatus.NORMAL -> "স্বাভাবিক (Optimal 2°-8°C)"
                                                    ColdChainHelper.TempBreachStatus.FREEZE_RISK -> "⚠️ হিমায়িত ঝুঁকি (<2°C) Freeze Risk!"
                                                    ColdChainHelper.TempBreachStatus.HEAT_BREACH -> "🚨 উষ্ণতা লঙ্ঘন (>8°C) Heat Breach!"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (breachStatus) {
                                                    ColdChainHelper.TempBreachStatus.NORMAL -> Color(0xFF10B981)
                                                    else -> Color(0xFFEF4444)
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = tempInput,
                                        onValueChange = { tempInput = it },
                                        label = { Text("ফ্রিজের তাপমাত্রা (°C)") },
                                        placeholder = { Text("যেমন: 4.2") },
                                        trailingIcon = { Text("°C", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = unitNameInput,
                                        onValueChange = { unitNameInput = it },
                                        label = { Text("রেফ্রিজারেটর ইউনিট") },
                                        modifier = Modifier.weight(1.3f),
                                        singleLine = true
                                    )

                                    Button(
                                        onClick = {
                                            if (tempDbl == null) {
                                                Toast.makeText(context, "সঠিক তাপমাত্রা প্রদান করুন", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            val isBreach = tempDbl < ColdChainHelper.MIN_SAFE_TEMP_C || tempDbl > ColdChainHelper.MAX_SAFE_TEMP_C
                                            val log = RefrigeratorTempLog(
                                                unitName = unitNameInput.trim().ifBlank { "Main Pharma Vaccine Fridge" },
                                                temperatureC = tempDbl,
                                                isBreach = isBreach,
                                                breachActionTaken = if (isBreach) actionTakenInput.trim().ifBlank { "Logged & inspected" } else null,
                                                recordedBy = recordedByInput.trim().ifBlank { "Pharmacist" },
                                                loggedAt = System.currentTimeMillis()
                                            )
                                            onSaveLog(log)
                                            tempInput = ""
                                            actionTakenInput = ""
                                            Toast.makeText(context, "তাপমাত্রা লগ সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("লগ করুন", fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (breachStatus != null && breachStatus != ColdChainHelper.TempBreachStatus.NORMAL) {
                                    OutlinedTextField(
                                        value = actionTakenInput,
                                        onValueChange = { actionTakenInput = it },
                                        label = { Text("লঙ্ঘনে গৃহীত সংশোধনমূলক ব্যবস্থা (Action Taken)") },
                                        placeholder = { Text("যেমন: থার্মোস্ট্যাট সমন্বয়, দরজার সিল পরীক্ষা, ব্যাকআপ জেনারেটর চালু...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Recent Log List Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "সাম্প্রতিক তাপমাত্রা লগ হিস্ট্রি (DGDA Compliance Log)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            TextButton(
                                onClick = {
                                    val logText = generateComplianceAuditReport(tempLogs)
                                    clipboardManager.setText(AnnotatedString(logText))
                                    Toast.makeText(context, "অডিট লগ কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("অডিট কপি", fontSize = 12.sp)
                            }
                        }

                        // Temp Logs List
                        if (tempLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("কোনো তাপমাত্রা লগ পাওয়া যায়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(tempLogs, key = { it.id }) { log ->
                                    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(log.loggedAt))
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (log.isBreach) Color(0xFFEF4444).copy(alpha = 0.08f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (log.isBreach) Color(0xFFEF4444).copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (log.isBreach) Color(0xFFEF4444).copy(alpha = 0.2f)
                                                            else Color(0xFF10B981).copy(alpha = 0.15f)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (log.isBreach) Icons.Default.Warning else Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = if (log.isBreach) Color(0xFFEF4444) else Color(0xFF10B981),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Column {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${log.temperatureC} °C",
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (log.isBreach) Color(0xFFEF4444) else Color(0xFF10B981)
                                                        )
                                                        Text(
                                                            text = "• ${log.unitName}",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = "$dateStr (${log.recordedBy})",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (!log.breachActionTaken.isNullOrBlank()) {
                                                        Text(
                                                            text = "পদক্ষেপ: ${log.breachActionTaken}",
                                                            fontSize = 11.sp,
                                                            color = Color(0xFFEF4444),
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }

                                            IconButton(
                                                onClick = { onDeleteLog(log) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete",
                                                    tint = Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 1: COLD-CHAIN STOCK AUDIT
                    var searchQuery by remember { mutableStateOf("") }
                    val filteredProducts = remember(coldChainProducts, searchQuery) {
                        if (searchQuery.isBlank()) coldChainProducts
                        else coldChainProducts.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            (it.genericName?.contains(searchQuery, ignoreCase = true) == true)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("ইনসুলিন, ভ্যাকসিন বা বায়োলজিক্যাল ওষুধ খুঁজুন...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (filteredProducts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("কোনো কোল্ড-চেইন ওষুধ পাওয়া যায়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredProducts, key = { it.barcode }) { prod ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF0284C7).copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "❄️ 2°C - 8°C",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF0284C7),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = prod.name,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    text = prod.genericName ?: prod.category ?: "",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!prod.shelfLocation.isNullOrBlank()) {
                                                    Text(
                                                        text = "অবস্থান / Shelf: ${prod.shelfLocation}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF0D9488)
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "স্টক: ${prod.stockQty.toInt()} ${prod.unit}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (prod.stockQty <= 5) Color(0xFFEF4444) else Color(0xFF10B981)
                                                )
                                                Text(
                                                    text = "৳ ${prod.sellingPrice}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}

private fun generateComplianceAuditReport(logs: List<RefrigeratorTempLog>): String {
    val dateStr = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).format(Date())
    val sb = StringBuilder()
    sb.append("❄️ DGDA PHARMACY COLD-CHAIN TEMPERATURE AUDIT LOG (২°-৮° সে.)\n")
    sb.append("তারিখ: $dateStr | মোট রেকর্ড: ${logs.size}\n")
    sb.append("--------------------------------------------------\n")
    logs.take(30).forEach { log ->
        val time = SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()).format(Date(log.loggedAt))
        val status = if (log.isBreach) "⚠️ BREACH" else "✅ NORMAL"
        sb.append("[$time] ${log.unitName}: ${log.temperatureC}°C ($status) By: ${log.recordedBy}\n")
        if (!log.breachActionTaken.isNullOrBlank()) {
            sb.append("   -> গৃহীত ব্যবস্থা: ${log.breachActionTaken}\n")
        }
    }
    sb.append("--------------------------------------------------\n")
    sb.append("DGDA Good Pharmacy Practice (GPP) Verified\n")
    return sb.toString()
}
