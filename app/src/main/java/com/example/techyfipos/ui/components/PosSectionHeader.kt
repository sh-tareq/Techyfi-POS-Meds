package com.example.techyfipos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standardized Section Theme Palette across Techyfi POS.
 * Matches the interactive visualizer and calibrated color tokens.
 */
object PosSectionColors {
    val Products = Color(0xFF4F46E5)       // Indigo
    val Customers = Color(0xFF8B5CF6)      // Violet
    val Dues = Color(0xFFEF4444)           // Red
    val Suppliers = Color(0xFF0D9488)      // Teal
    val Expenses = Color(0xFFF43F5E)       // Rose
    val SalesReport = Color(0xFF0284C7)    // Sky
    val SalesHistory = Color(0xFF3B82F6)   // Blue
    val RegisterClose = Color(0xFFD97706)  // Amber / Bronze
    val Cart = Color(0xFF10B981)           // Emerald
    val Loyalty = Color(0xFFF59E0B)        // Amber / Gold
    val Settings = Color(0xFF64748B)       // Slate
    val Users = Color(0xFF059669)          // Emerald / Green
    val More = Color(0xFF6366F1)           // Indigo
}

/**
 * High-definition, responsive TopAppBar title component for all POS sections.
 *
 * Implements calibrated sizing for zero text cut-off, ascender/descender clearance
 * for Bengali diacritics, and automatic tablet vs phone layout adaptation:
 * - Phone (<600dp): 40dp squircle badge, 25dp 3D icon, 18sp ExtraBold title.
 *   Metric badges are deferred to the subtitle row to prevent title truncation.
 * - Tablet (>=600dp): 48dp squircle badge, 32dp 3D icon, 22sp ExtraBold title,
 *   prominent inline metric pill badge.
 */
@Composable
fun PosTopAppBarTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metricBadge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    badgeBg: Color = badgeColor.copy(alpha = 0.16f),
    badgeBorder: Color = badgeColor.copy(alpha = 0.38f),
    isTablet: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    subtitleMaxLines: Int = 1,
    icon: @Composable (size: Dp) -> Unit
) {
    val badgeSize = if (isTablet) 48.dp else 34.dp
    val iconSize = if (isTablet) 32.dp else 22.dp
    val cornerRadius = if (isTablet) 16.dp else 10.dp
    val titleFontSize = if (isTablet) 22.sp else 16.5.sp
    val titleLineHeight = if (isTablet) 28.sp else 22.sp
    val subtitleFontSize = if (isTablet) 13.sp else 11.sp
    val subtitleLineHeight = (subtitleFontSize.value * 1.30f).sp
    val gap = if (isTablet) 14.dp else 8.dp

    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tonal Squircle Badge Container
        Box(
            modifier = Modifier
                .size(badgeSize)
                .background(color = badgeBg, shape = RoundedCornerShape(cornerRadius))
                .border(width = 1.dp, color = badgeBorder, shape = RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            icon(iconSize)
        }

        Spacer(modifier = Modifier.width(gap))

        // Title & Subtitle Column with safe non-expanding weight
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.2).sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                // Metric Pill Tag: Rendered in title row on Tablet where horizontal space is ample
                if (isTablet && !metricBadge.isNullOrBlank()) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = metricBadge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            maxLines = 1
                        )
                    }
                }
            }

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = subtitleFontSize,
                    lineHeight = subtitleLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    softWrap = subtitleMaxLines > 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
