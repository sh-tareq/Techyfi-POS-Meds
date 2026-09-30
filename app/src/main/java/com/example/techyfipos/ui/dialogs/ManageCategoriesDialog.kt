package com.example.techyfipos.ui.dialogs

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.CategoryItem

/**
 * Manage Categories Dialog allowing adding, editing (with full ✏️ Edit mode), deleting, and resetting categories.
 */
@Composable
fun ManageCategoriesDialog(
    categories: List<CategoryItem>,
    onDismiss: () -> Unit,
    onSave: (List<CategoryItem>) -> Unit,
    onReset: () -> Unit
) {
    var localCategories by remember(categories) { mutableStateOf(categories) }
    var editingCategory by remember { mutableStateOf<CategoryItem?>(null) }
    var iconInput by remember { mutableStateOf("🏷️") }
    var nameEnInput by remember { mutableStateOf("") }
    var nameBnInput by remember { mutableStateOf("") }

    val context = LocalContext.current

    val startEditing = { item: CategoryItem ->
        editingCategory = item
        iconInput = item.icon
        nameEnInput = item.nameEn
        nameBnInput = item.nameBn
    }

    val cancelEditing = {
        editingCategory = null
        iconInput = "🏷️"
        nameEnInput = ""
        nameBnInput = ""
    }

    val handleSubmit = {
        val trimEn = nameEnInput.trim()
        val trimBn = nameBnInput.trim()
        val trimIcon = iconInput.trim().ifBlank { "🏷️" }

        if (trimEn.isBlank()) {
            Toast.makeText(context, context.getString(R.string.products_enter_category_name_warning), Toast.LENGTH_SHORT).show()
        } else {
            val currentEditing = editingCategory
            if (currentEditing != null) {
                // Update existing category
                localCategories = localCategories.map {
                    if (it.id == currentEditing.id) {
                        it.copy(icon = trimIcon, nameEn = trimEn, nameBn = if (trimBn.isNotBlank()) trimBn else trimEn)
                    } else it
                }
                cancelEditing()
            } else {
                // Add new category
                val newItem = CategoryItem(
                    icon = trimIcon,
                    nameEn = trimEn,
                    nameBn = if (trimBn.isNotBlank()) trimBn else trimEn
                )
                localCategories = localCategories + newItem
                nameEnInput = ""
                nameBnInput = ""
                iconInput = "🏷️"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Gradient Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF4F46E5), Color(0xFF4338CA))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚙️", fontSize = 16.sp)
                            Text(
                                text = stringResource(R.string.products_manage_categories_title),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.20f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close_content_description), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Body content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Add / Edit Card Box
                    val isEditing = editingCategory != null
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isEditing) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (isEditing) Color(0xFF93C5FD) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isEditing) stringResource(R.string.products_edit_category_title, editingCategory?.nameEn ?: "") else stringResource(R.string.products_add_new_category_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isEditing) Color(0xFF1D4ED8) else Color(0xFF1E293B)
                            )

                            // Row 1: Icon Emoji Box + English Name
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = iconInput,
                                    onValueChange = { iconInput = it.take(4) },
                                    placeholder = { Text("🏷️", fontSize = 12.sp) },
                                    singleLine = true,
                                    textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 14.sp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(52.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF334155),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedBorderColor = Color(0xFF4F46E5)
                                    )
                                )

                                OutlinedTextField(
                                    value = nameEnInput,
                                    onValueChange = { nameEnInput = it },
                                    placeholder = { Text(stringResource(R.string.products_category_name_en_placeholder), fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF334155),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedBorderColor = Color(0xFF4F46E5)
                                    )
                                )
                            }

                            // Row 2: Bangla Name + Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = nameBnInput,
                                    onValueChange = { nameBnInput = it },
                                    placeholder = { Text(stringResource(R.string.products_category_name_bn_placeholder), fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White,
                                        focusedTextColor = Color(0xFF0F172A),
                                        unfocusedTextColor = Color(0xFF334155),
                                        unfocusedBorderColor = Color(0xFFCBD5E1),
                                        focusedBorderColor = Color(0xFF4F46E5)
                                    )
                                )

                                if (isEditing) {
                                    OutlinedButton(
                                        onClick = { cancelEditing() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Text(stringResource(R.string.products_cancel_action), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                    }
                                }

                                Button(
                                    onClick = { handleSubmit() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isEditing) Color(0xFF2563EB) else Color(0xFF4F46E5)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(44.dp)
                                ) {
                                    Text(
                                        text = if (isEditing) stringResource(R.string.products_update_action) else stringResource(R.string.products_plus_add_action),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Section Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.products_all_categories_tap_edit, localCategories.size),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    // Categories List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(localCategories, key = { it.id }) { catItem ->
                            val isItemBeingEdited = editingCategory?.id == catItem.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isItemBeingEdited) Color(0xFFEEF2FF) else Color.White,
                                border = BorderStroke(
                                    1.dp,
                                    if (isItemBeingEdited) Color(0xFF6366F1) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { startEditing(catItem) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(catItem.icon, fontSize = 16.sp)
                                        Column {
                                            Text(
                                                catItem.nameEn,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            if (catItem.nameBn.isNotBlank()) {
                                                Text(
                                                    catItem.nameBn,
                                                    fontSize = 10.5.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Edit Button
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                            modifier = Modifier.clickable { startEditing(catItem) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text("✏️", fontSize = 10.sp)
                                                Text(
                                                    stringResource(R.string.products_edit_action),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4F46E5)
                                                )
                                            }
                                        }

                                        // Delete Button
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF2F2),
                                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                            modifier = Modifier.clickable {
                                                if (localCategories.size <= 1) {
                                                    Toast.makeText(context, context.getString(R.string.products_keep_at_least_one_category_warning), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    localCategories = localCategories.filter { it.id != catItem.id }
                                                    if (editingCategory?.id == catItem.id) {
                                                        cancelEditing()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                "🗑️",
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer Bar
                Surface(
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                localCategories = CategoryItem.defaultCategories()
                                cancelEditing()
                                onReset()
                            }
                        ) {
                            Text(
                                stringResource(R.string.products_reset_defaults),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }

                        Button(
                            onClick = {
                                onSave(localCategories)
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.products_save_apply_action),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
