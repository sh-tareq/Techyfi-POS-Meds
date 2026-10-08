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
    }
}
