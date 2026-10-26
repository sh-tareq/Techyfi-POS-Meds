package com.techyfiposmeds.ui.dialogs

import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techyfiposmeds.data.clinical.OtcCategory
import com.techyfiposmeds.data.clinical.OtcRecommendation
import com.techyfiposmeds.data.clinical.OtcTriageEngine
import com.techyfiposmeds.data.db.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtcSymptomAssistantDialog(
    allProducts: List<Product>,
    onDismiss: () -> Unit,
    onAddToCart: (Product, Double) -> Unit
) {
    val context = LocalContext.current
    val categories = OtcTriageEngine.categories
    var selectedCategoryId by remember { mutableStateOf(categories.first().id) }
    val currentCategory = remember(selectedCategoryId) {
        categories.first { it.id == selectedCategoryId }
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
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF059669).copy(alpha = 0.12f))
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
                                .background(Color(0xFF059669).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "জরুরি প্রাথমিক চিকিৎসা ও ওটিসি সহকারী",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "উপসর্গভিত্তিক ক্লিনিক্যাল সমাধান, রেড ফ্ল্যাগ ও ১-ট্যাপ কার্ট",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Category Chips Scroll Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = cat.id == selectedCategoryId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = cat.id },
                            label = {
                                Text(
                                    cat.titleBn,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF059669).copy(alpha = 0.18f),
                                selectedLabelColor = Color(0xFF059669)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Color(0xFF059669) else MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }
                }

                // Main Content for Selected Category
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Category Subtitle
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${currentCategory.titleBn} (${currentCategory.titleEn})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentCategory.subtitleBn,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Red Flags Card (Critical Patient Safety)
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Emergency,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "🚨 রেড ফ্ল্যাগ সতর্কতা: কখন ডাক্তার দেখাবেন?",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }

                                currentCategory.redFlagsBn.forEach { flag ->
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text("•", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                        Text(
                                            text = flag,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Recommended Items Section Header
                    item {
                        Text(
                            text = "💊 ক্লিনিক্যাল ওটিসি সুপারিশ ও ওষুধ তালিকা:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // List of Recommendations
                    items(currentCategory.recommendations) { rec ->
                        OtcRecommendationCard(
                            recommendation = rec,
                            allProducts = allProducts,
                            onAddToCart = onAddToCart
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OtcRecommendationCard(
    recommendation: OtcRecommendation,
    allProducts: List<Product>,
    onAddToCart: (Product, Double) -> Unit
) {
    val context = LocalContext.current
    // Search inventory for matching products
    val matchingProducts = remember(allProducts, recommendation) {
        allProducts.filter { prod ->
            val pName = prod.name.lowercase()
            val pGen = prod.genericName?.lowercase() ?: ""
            recommendation.commonBrands.any { brand -> pName.contains(brand.lowercase()) } ||
            pGen.contains(recommendation.genericName.lowercase().substringBefore(" "))
        }
    }

    var selectedProduct by remember(matchingProducts) {
        mutableStateOf(matchingProducts.firstOrNull())
    }
    var quantity by remember(recommendation) {
        mutableDoubleStateOf(recommendation.defaultQty)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title & Form
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            color = if (recommendation.isPrimary) Color(0xFF059669).copy(alpha = 0.15f)
                            else Color(0xFF64748B).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (recommendation.isPrimary) "ফার্স্ট-লাইন" else "সহায়ক",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (recommendation.isPrimary) Color(0xFF059669) else Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = recommendation.genericName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "ডোজ রূপ: ${recommendation.dosageForm} • উদাহরণ: ${recommendation.commonBrands.joinToString(", ")}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Dosage & Clinical Tip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "সেবনবিধি: ${recommendation.standardDosageBn}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "পরামর্শ: ${recommendation.clinicalTipBn}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Matching Products in Pharmacy Inventory
            if (matchingProducts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "দোকানের স্টকে বিদ্যমান ব্র্যান্ড (${matchingProducts.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Brand chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        matchingProducts.forEach { prod ->
                            val isChosen = selectedProduct?.barcode == prod.barcode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) Color(0xFF059669).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isChosen) Color(0xFF059669) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.clickable { selectedProduct = prod }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = prod.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChosen) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "স্টক: ${prod.stockQty.toInt()}",
                                        fontSize = 10.sp,
                                        color = if (prod.stockQty <= 5) Color(0xFFEF4444) else Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 1-Tap Add to Cart Row
                    selectedProduct?.let { prod ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${prod.name} (৳ ${prod.sellingPrice}/${prod.unit})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "মোট: ৳ ${(prod.sellingPrice * quantity).toInt()}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Quantity decrement
                                IconButton(
                                    onClick = { if (quantity > 1) quantity -= 1 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = "${quantity.toInt()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Quantity increment
                                IconButton(
                                    onClick = { quantity += 1 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(16.dp))
                                }

                                Spacer(Modifier.width(4.dp))

                                Button(
                                    onClick = {
                                        onAddToCart(prod, quantity)
                                        Toast.makeText(context, "${prod.name} (${quantity.toInt()} টি) কার্টে যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("কার্টে যোগ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                    Text(
                        text = "দোকানের স্টকে এই ব্র্যান্ড সরাসরি পাওয়া যায়নি। যেকোনো সমমানের জেনেরিক সরবরাহ করুন।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
