package com.example.techyfipos.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    private fun calculateContrast(c1: Color, c2: Color): Double {
        val lum1 = c1.luminance().toDouble()
        val lum2 = c2.luminance().toDouble()
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testD6_themeCompleteness_containersAndSurfacesDefined() {
        // Assert surfaceContainer is explicitly neutral slate, not BOM default lavender
        assertEquals(DarkSurface, DarkColorScheme.surfaceContainer)
        assertEquals(Color(0xFFFFFFFF), LightColorScheme.surfaceContainer)

        // Tertiary container tokens defined
        assertEquals(AmberContainerDark, DarkColorScheme.tertiaryContainer)
        assertEquals(AmberContainerLight, LightColorScheme.tertiaryContainer)

        // Error container tokens defined
        assertEquals(CrimsonContainerDark, DarkColorScheme.errorContainer)
        assertEquals(CrimsonContainerLight, LightColorScheme.errorContainer)
        assertEquals(CrimsonOnContainerDark, DarkColorScheme.onErrorContainer)
        assertEquals(CrimsonOnContainerLight, LightColorScheme.onErrorContainer)

        // Inverse tokens defined
        assertEquals(EmeraldSecondary, DarkColorScheme.inversePrimary)
        assertEquals(EmeraldPrimary, LightColorScheme.inversePrimary)
    }

    @Test
    fun testD5_darkThemeContrast_primaryAndSecondaryMeetWcag() {
        // WCAG AA requires at least 4.5:1 contrast for normal text
        val darkPrimaryContrast = calculateContrast(DarkColorScheme.primary, DarkColorScheme.onPrimary)
        assertTrue(
            "Dark primary contrast should be >= 4.5:1 but was $darkPrimaryContrast",
            darkPrimaryContrast >= 4.5
        )

        val darkSecondaryContrast = calculateContrast(DarkColorScheme.secondary, DarkColorScheme.onSecondary)
        assertTrue(
            "Dark secondary contrast should be >= 4.5:1 but was $darkSecondaryContrast",
            darkSecondaryContrast >= 4.5
        )

        // D-5: Secondary text on secondaryContainer in dark mode
        val darkSecondaryOnContainer = calculateContrast(DarkColorScheme.secondary, DarkColorScheme.secondaryContainer)
        assertTrue(
            "Dark secondary on secondaryContainer should be >= 4.5:1 but was $darkSecondaryOnContainer",
            darkSecondaryOnContainer >= 4.5
        )

        // D-5: Secondary text on dark surface
        val darkSecondaryOnSurface = calculateContrast(DarkColorScheme.secondary, DarkColorScheme.surface)
        assertTrue(
            "Dark secondary on surface should be >= 4.5:1 but was $darkSecondaryOnSurface",
            darkSecondaryOnSurface >= 4.5
        )
    }

    @Test
    fun testD6_U1_lightThemeContrast_tokensMeetWcag() {
        // U-1: Light primary checkout button contrast (was 2.54:1 on #10B981)
        val lightPrimaryContrast = calculateContrast(LightColorScheme.primary, LightColorScheme.onPrimary)
        assertTrue(
            "Light primary contrast (U-1) should be >= 4.5:1 but was $lightPrimaryContrast",
            lightPrimaryContrast >= 4.5
        )

        // D-6: Light error on onError
        val lightErrorContrast = calculateContrast(LightColorScheme.error, LightColorScheme.onError)
        assertTrue(
            "Light error contrast should be >= 4.5:1 but was $lightErrorContrast",
            lightErrorContrast >= 4.5
        )

        // D-6: Light tertiary on onTertiary
        val lightTertiaryContrast = calculateContrast(LightColorScheme.tertiary, LightColorScheme.onTertiary)
        assertTrue(
            "Light tertiary contrast should be >= 4.5:1 but was $lightTertiaryContrast",
            lightTertiaryContrast >= 4.5
        )

        // D-6: Dark onSurfaceVariant on surfaceContainerHighest
        val darkSurfaceVariantOnHighest = calculateContrast(DarkColorScheme.onSurfaceVariant, DarkColorScheme.surfaceContainerHighest)
        assertTrue(
            "Dark onSurfaceVariant on surfaceContainerHighest should be >= 4.5:1 but was $darkSurfaceVariantOnHighest",
            darkSurfaceVariantOnHighest >= 4.5
        )
    }
}
