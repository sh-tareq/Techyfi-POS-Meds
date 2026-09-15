package com.example.techyfipos.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.techyfipos.R
import com.example.techyfipos.data.db.CustomerDue
import com.example.techyfipos.utils.formatTaka
import com.example.techyfipos.utils.isFractionalUnit
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ManualQuantityDialog(
    currentQuantity: Double,
    availableStock: Double,
    allowNegativeInventory: Boolean = false,
    isCustomItem: Boolean = false,
    unit: String = "Pcs",
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(
            if (currentQuantity % 1.0 == 0.0) currentQuantity.toLong().toString()
            else String.format(Locale.US, "%.2f", currentQuantity).trimEnd('0').trimEnd('.')
        )
    }
    val parsed = text.toDoubleOrNull()
    val isStockTracked = !isCustomItem
    val isExceedingStock = isStockTracked && parsed != null && parsed > availableStock
    val isValid = parsed != null && parsed > 0.0 && (!isStockTracked || allowNegativeInventory || !isExceedingStock)

    val cleanUnit = unit.trim()
    val isFractional = isFractionalUnit(cleanUnit)
    val increments = if (isFractional) listOf(0.25, 0.5, 1.0, 5.0) else listOf(1.0, 5.0, 10.0, 20.0)
    val presets = if (isFractional) listOf(0.25, 0.5, 1.0, 2.75) else listOf(1.0, 2.0, 5.0, 10.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding(),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚖️", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cart_set_quantity_title, cleanUnit), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            text = filtered.take(7)
                        }
                    },
                    label = { Text(stringResource(R.string.cart_quantity_label, cleanUnit)) },
                    placeholder = { Text(if (isFractional) stringResource(R.string.cart_quantity_placeholder_fractional) else stringResource(R.string.cart_quantity_placeholder_whole)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(onDone = { if (isValid) onConfirm(parsed!!) }),
                    isError = text.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Increment Buttons (+0.25, +0.5, +1, +5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    increments.forEach { inc ->
                        OutlinedButton(
                            onClick = {
                                val cur = text.toDoubleOrNull() ?: 0.0
                                val added = Math.round((cur + inc) * 100.0) / 100.0
                                val n = if (isStockTracked && !allowNegativeInventory && availableStock > 0.0) {
                                    added.coerceAtMost(availableStock)
                                } else {
                                    added
                                }
                                text = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
                            },
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val incStr = if (inc % 1.0 == 0.0) "+${inc.toInt()}" else "+$inc"
                            Text(
                                text = incStr,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Quick Presets (0.25 Kg, 0.5 Kg, 1 Kg, 2.75 Kg)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            onClick = {
                                val n = if (isStockTracked && !allowNegativeInventory && availableStock > 0.0) {
                                    preset.coerceAtMost(availableStock)
                                } else {
                                    preset
                                }
                                text = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n).trimEnd('0').trimEnd('.')
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 1.dp)) {
                                val pNum = if (preset % 1.0 == 0.0) "${preset.toInt()}" else "$preset"
                                val pStr = if (cleanUnit.isNotBlank()) "$pNum $cleanUnit" else pNum
                                Text(
                                    text = pStr,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isStockTracked) {
                    val stockStr = if (availableStock % 1.0 == 0.0) availableStock.toLong().toString() else String.format(Locale.US, "%.2f", availableStock).trimEnd('0').trimEnd('.')
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.cart_available_stock, stockStr, cleanUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (availableStock <= 0.0) MaterialTheme.colorScheme.error else Color.Gray
                        )
                        if (allowNegativeInventory && isExceedingStock) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = stringResource(R.string.cart_negative_stock_allowed_badge),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (text.isNotEmpty() && !isValid && !allowNegativeInventory && isExceedingStock) {
                        Text(
                            stringResource(R.string.cart_quantity_range_error, stockStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (isValid) onConfirm(parsed!!) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color(0xFF059669))
            }
        }
    )
}

@Composable
fun CreditLimitWarningDialog(
    customer: CustomerDue,
    newDue: Double,
    onVerifyOwnerPin: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
    onAuthorize: () -> Unit
) {
    var ownerPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val totalDue = customer.outstandingAmount + newDue
    val excess = (totalDue - customer.creditLimit).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cart_credit_limit_exceeded_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFF1F2),
                    border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.cart_credit_ceiling_reached, customer.customerName),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBE123C),
                            fontSize = 13.sp
                        )
                        Text(stringResource(R.string.cart_credit_limit_line, formatTaka(customer.creditLimit)), fontSize = 11.5.sp)
                        Text(stringResource(R.string.cart_current_outstanding_line, formatTaka(customer.outstandingAmount)), fontSize = 11.5.sp)
                        Text(stringResource(R.string.cart_new_due_line, formatTaka(newDue)), fontSize = 11.5.sp)
                        Text(
                            text = stringResource(R.string.cart_new_total_balance_line, formatTaka(totalDue), formatTaka(excess)),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE11D48),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.cart_owner_authorization_required),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = ownerPin,
                    onValueChange = { input ->
                        ownerPin = input.filter { it.isDigit() }.take(4)
                        pinError = false
                    },
                    label = { Text(stringResource(R.string.cart_owner_pin_label)) },
                    placeholder = { Text(stringResource(R.string.cart_enter_pin_placeholder)) },
                    singleLine = true,
                    isError = pinError,
                    supportingText = if (pinError) {
                        { Text(stringResource(R.string.invalid_pin), color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        isVerifying = true
                        val authorized = onVerifyOwnerPin(ownerPin)
                        isVerifying = false
                        if (authorized) onAuthorize() else pinError = true
                    }
                },
                enabled = ownerPin.length == 4 && !isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.cart_authorize_proceed_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Shown before completing any Due (or split-with-Due) sale under the credit limit, so the cashier sees the customer's total balance before committing. */
@Composable
fun ConfirmDueSaleDialog(
    customerName: String,
    previousDue: Double,
    newDueAmount: Double,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val totalDue = previousDue + newDueAmount
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧾", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cart_confirm_due_sale_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(customerName.ifBlank { stringResource(R.string.cart_confirm_due_sale_unknown_customer) }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(stringResource(R.string.cart_confirm_due_sale_previous_due, formatTaka(previousDue)), fontSize = 12.5.sp)
                    Text(stringResource(R.string.cart_confirm_due_sale_new_due, formatTaka(newDueAmount)), fontSize = 12.5.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.cart_confirm_due_sale_total_due, formatTaka(totalDue)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.cart_confirm_due_sale_confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
