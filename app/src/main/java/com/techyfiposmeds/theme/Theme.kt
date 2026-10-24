package com.techyfiposmeds.theme

import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color

internal val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = ElectricIndigoLight,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = IndigoContainerDark,
    onSecondaryContainer = IndigoOnContainerDark,
    tertiary = AmberWarning,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberOnContainerDark,
    error = CrimsonDanger,
    onError = Color.White,
    errorContainer = CrimsonContainerDark,
    onErrorContainer = CrimsonOnContainerDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceDim = Color(0xFF0F172A),
    surfaceBright = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFF0F172A),
    surfaceContainerLow = Color(0xFF1E293B),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF334155),
    surfaceContainerHighest = Color(0xFF475569),
    inverseSurface = Color(0xFFF8FAFC),
    inverseOnSurface = Color(0xFF0F172A),
    inversePrimary = EmeraldSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

internal val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = LightOnPrimary,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = ElectricIndigo,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainerLight,
    onSecondaryContainer = IndigoOnContainerLight,
    tertiary = AmberWarning,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,
    error = CrimsonDark,
    onError = Color.White,
    errorContainer = CrimsonContainerLight,
    onErrorContainer = CrimsonOnContainerLight,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceDim = Color(0xFFE2E8F0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = EmeraldPrimary,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

val LocalDarkTheme = androidx.compose.runtime.compositionLocalOf { false }

object SystemBarManager {
    private var forceDarkCount = 0

    fun enterForceDark(activity: androidx.activity.ComponentActivity) {
        forceDarkCount++
        activity.enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
    }

    fun exitForceDark(activity: androidx.activity.ComponentActivity, isAppDark: Boolean) {
        forceDarkCount = (forceDarkCount - 1).coerceAtLeast(0)
        if (forceDarkCount == 0) {
            activity.enableEdgeToEdge(
                statusBarStyle = if (isAppDark) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                },
                navigationBarStyle = if (isAppDark) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
            )
        }
    }
}

@Composable
fun ForceDarkSystemBars() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAppDark = LocalDarkTheme.current
    val localActivity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(isAppDark, localActivity) {
        val activity = localActivity
            ?: generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                .filterIsInstance<androidx.activity.ComponentActivity>()
                .firstOrNull()
            ?: (com.techyfiposmeds.MainActivity.currentActivity?.get() as? androidx.activity.ComponentActivity)

        if (activity != null) {
            SystemBarManager.enterForceDark(activity)
        }
        onDispose {
            if (activity != null) {
                SystemBarManager.exitForceDark(activity, isAppDark)
            }
        }
    }
}

@Composable
fun TechyfiPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
