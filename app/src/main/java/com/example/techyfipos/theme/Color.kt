package com.example.techyfipos.theme

import androidx.compose.ui.graphics.Color

val EmeraldPrimary = Color(0xFF10B981)
val EmeraldSecondary = Color(0xFF059669)
val EmeraldDark = Color(0xFF047857)
val ElectricIndigo = Color(0xFF4F46E5)
val ElectricIndigoLight = Color(0xFFA5B4FC)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonDanger = Color(0xFFEF4444)
val CrimsonDark = Color(0xFFDC2626)
val SapphireInfo = Color(0xFF3B82F6)

// Legacy alias mappings for backwards compatibility across existing screen references
val GreenPrimary = Color(0xFF10B981)
val GreenSecondary = Color(0xFF059669)
val GoldAccent = Color(0xFFF59E0B)
val CrimsonWarning = Color(0xFFEF4444)

val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF0F172A)
val LightOnSurface = Color(0xFF0F172A)
val EmeraldContainerLight = Color(0xFFD1FAE5)
val EmeraldOnContainerLight = Color(0xFF065F46)
val IndigoContainerLight = Color(0xFFEEF2FF)
val IndigoOnContainerLight = Color(0xFF3730A3)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightOnSurfaceVariant = Color(0xFF64748B)
val LightOutline = Color(0xFF94A3B8)
val LightOutlineVariant = Color(0xFFE2E8F0)
val AmberContainerLight = Color(0xFFFEF3C7)
val AmberOnContainerLight = Color(0xFF92400E)
val CrimsonContainerLight = Color(0xFFFEE2E2)
val CrimsonOnContainerLight = Color(0xFF991B1B)

val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155)
val DarkOnPrimary = Color(0xFF022C22)
val DarkOnBackground = Color(0xFFF8FAFC)
val DarkOnSurface = Color(0xFFF8FAFC)
val EmeraldContainerDark = Color(0xFF064E3B)
val EmeraldOnContainerDark = Color(0xFFA7F3D0)
val IndigoContainerDark = Color(0xFF312E81)
val IndigoOnContainerDark = Color(0xFFC7D2FE)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF64748B)
val DarkOutlineVariant = Color(0xFF334155)
val AmberContainerDark = Color(0xFF78350F)
val AmberOnContainerDark = Color(0xFFFDE68A)
val CrimsonContainerDark = Color(0xFF7F1D1D)
val CrimsonOnContainerDark = Color(0xFFFECDD3)

data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

val LightExtendedColors = ExtendedColors(
    success = EmeraldDark,
    onSuccess = Color.White,
    successContainer = EmeraldContainerLight,
    onSuccessContainer = EmeraldOnContainerLight,
    warning = AmberWarning,
    onWarning = Color(0xFF451A03),
    warningContainer = AmberContainerLight,
    onWarningContainer = AmberOnContainerLight,
    info = SapphireInfo,
    onInfo = Color.White,
    infoContainer = Color(0xFFDBEAFE),
    onInfoContainer = Color(0xFF1E40AF)
)

val DarkExtendedColors = ExtendedColors(
    success = EmeraldPrimary,
    onSuccess = DarkOnPrimary,
    successContainer = EmeraldContainerDark,
    onSuccessContainer = EmeraldOnContainerDark,
    warning = AmberWarning,
    onWarning = Color(0xFF451A03),
    warningContainer = AmberContainerDark,
    onWarningContainer = AmberOnContainerDark,
    info = SapphireInfo,
    onInfo = Color.White,
    infoContainer = Color(0xFF1E3A8A),
    onInfoContainer = Color(0xFFBFDBFE)
)

val LocalExtendedColors = androidx.compose.runtime.staticCompositionLocalOf { LightExtendedColors }

val androidx.compose.material3.MaterialTheme.extendedColors: ExtendedColors
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalExtendedColors.current


