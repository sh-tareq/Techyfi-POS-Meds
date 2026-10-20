package com.example.techyfipos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.techyfipos.R
import com.example.techyfipos.data.SupervisorVerifyResult
import com.example.techyfipos.data.db.User
import com.example.techyfipos.ui.viewmodel.AuthViewModel

@Composable
fun SupervisorAuthDialog(
    title: String = stringResource(R.string.users_supervisor_auth_title),
    actionDescription: String = stringResource(R.string.users_supervisor_auth_desc),
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit,
    onAuthorizedWithPin: ((User, String) -> Unit)? = null,
    onAuthorized: (User) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    fun submitPin(currentPin: String) {
        if (currentPin.length != 4 || isChecking) return
        isChecking = true
        errorMessage = null
        authViewModel.verifySupervisorPinDetailed(currentPin) { result ->
            isChecking = false
            when (result) {
                is SupervisorVerifyResult.Success -> {
                    onAuthorized(result.user)
                    onAuthorizedWithPin?.invoke(result.user, currentPin)
                }
                is SupervisorVerifyResult.Locked -> {
                    pin = ""
                    val s = ((result.remainingMillis + 999L) / 1000L).toInt()
                    errorMessage = context.getString(R.string.auth_locked_out_seconds, s)
                }
                is SupervisorVerifyResult.Deactivated -> {
                    pin = ""
                    errorMessage = context.getString(R.string.users_supervisor_auth_failed)
                }
                SupervisorVerifyResult.NotSupervisor -> {
                    pin = ""
                    errorMessage = context.getString(R.string.users_supervisor_auth_failed)
                }
                is SupervisorVerifyResult.Invalid -> {
                    pin = ""
                    val s = ((result.remainingMillis + 999L) / 1000L).toInt()
                    errorMessage = if (s > 0) {
                        context.getString(R.string.auth_locked_out_seconds, s)
                    } else {
                        context.getString(R.string.users_supervisor_auth_failed)
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .wrapContentHeight()
                .padding(16.dp)
                .trackUserActivity()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.requires_manager_or_owner),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = actionDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < pin.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Numeric Keypad
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )

                for (row in keys) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (key in row) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = when (key) {
                                    "C" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    "⌫" -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = !isChecking) {
                                        when (key) {
                                            "C" -> {
                                                pin = ""
                                                errorMessage = null
                                            }
                                            "⌫" -> {
                                                if (pin.isNotEmpty()) {
                                                    pin = pin.dropLast(1)
                                                    errorMessage = null
                                                }
                                            }
                                            else -> {
                                                if (pin.length < 4) {
                                                    val newPin = pin + key
                                                    pin = newPin
                                                    errorMessage = null
                                                    if (newPin.length == 4) {
                                                        submitPin(newPin)
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (key == "⌫") {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = stringResource(R.string.cd_backspace),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = if (key == "C") MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }
    }
}
