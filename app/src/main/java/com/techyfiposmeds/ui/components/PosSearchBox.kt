package com.techyfiposmeds.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techyfiposmeds.R

/**
 * Standardized, compact, accessible search box used across all Techyfi POS screens & dialogs.
 *
 * Implements:
 * - Vertical text/placeholder alignment with zero clipping.
 * - Single-line placeholder truncation with safe ellipsis for English and Bangla.
 * - Consistent 46dp compact height across phone and tablet breakpoints.
 * - Dynamic theme & section-specific accent color support.
 * - Automatic keyboard dismissal and IME Search action execution.
 * - Smooth animated clear (✕) button with accessible touch target.
 */
@Composable
fun PosSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: ImageVector = Icons.Default.Search,
    leadingIconTint: Color? = null,
    focusRequester: FocusRequester? = null,
    onSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val actualLeadingTint = leadingIconTint ?: if (isFocused || query.isNotBlank()) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) accentColor.copy(alpha = 0.85f)
            else if (query.isNotBlank()) accentColor.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = if (isFocused) 2.dp else 0.5.dp,
        modifier = modifier.height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = if (trailingContent != null || query.isNotEmpty()) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = stringResource(R.string.cd_search),
                tint = actualLeadingTint,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val textFieldModifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(accentColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onSearch?.invoke()
                        }
                    ),
                    modifier = textFieldModifier
                )
            }

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        onClear?.invoke()
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
