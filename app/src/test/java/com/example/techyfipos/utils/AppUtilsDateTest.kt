package com.example.techyfipos.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AppUtilsDateTest {

    @Test
    fun startOfDay_setsTimeToMidnight() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 14, 15, 30, 45)
            set(Calendar.MILLISECOND, 500)
        }
        val start = startOfDay(cal.timeInMillis)

        val resultCal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(14, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
        assertEquals(0, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun endOfDay_setsTimeToEndOfMidnight() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 14, 9, 15, 0)
            set(Calendar.MILLISECOND, 123)
        }
        val end = endOfDay(cal.timeInMillis)

        val resultCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(14, resultCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, resultCal.get(Calendar.MINUTE))
        assertEquals(59, resultCal.get(Calendar.SECOND))
        assertEquals(999, resultCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun startAndEndOfDay_spanFullDay() {
        val now = System.currentTimeMillis()
        val start = startOfDay(now)
        val end = endOfDay(now)

        assertTrue(end > start)
        assertEquals(86400000L - 1L, end - start)
    }

    @Test
    fun testD1_L2_appLanguageSingleSourceFormatTakaAndDateFormatters() {
        try {
            setAppLanguage("bn")
            assertTrue(isAppBangla())
            assertEquals("৳১,৫০০", formatTaka(1500.0))
            assertEquals("৳১,৫০০", formatMoney(1500.0))
            assertEquals("৳1,500", formatTaka(1500.0, isBangla = false))
            assertEquals("৳1,500", formatMoney(1500.0, isBangla = false))
            assertEquals("bn", DateFormatters.getAppLocale().language)

            val cal = Calendar.getInstance().apply {
                set(2026, Calendar.SEPTEMBER, 14, 15, 30, 0)
            }
            // Export date must always use Locale.US even when app is in Bangla
            val exportDate = DateFormatters.formatExportDate(cal.timeInMillis)
            assertTrue(exportDate.contains("2026"))
            assertEquals("2026-09-14", DateFormatters.formatISO(cal.timeInMillis))

            setAppLanguage("en")
            assertTrue(!isAppBangla())
            assertEquals("৳1,500", formatTaka(1500.0))
            assertEquals("৳1,500", formatMoney(1500.0))
            assertEquals("en", DateFormatters.getAppLocale().language)
        } finally {
            setAppLanguage("en")
        }
    }
}

