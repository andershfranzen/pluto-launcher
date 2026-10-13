package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomScanSummaryTest {
    @Test fun countsExtensionsMostCommonFirst() {
        assertEquals(
            listOf(ExtensionCount("sav", 2), ExtensionCount("txt", 1)),
            RomScanSummary.extensions(listOf("a.sav", "b.sav", "c.txt")),
        )
        assertEquals(
            listOf(ExtensionCount("nfo", 1), ExtensionCount("sav", 1)),
            RomScanSummary.extensions(listOf("a.SAV", "b.nfo")),
        )
    }

    @Test fun namesFilesWithoutAnExtension() {
        assertEquals(
            listOf(ExtensionCount("", 1), ExtensionCount("dat", 1)),
            RomScanSummary.extensions(listOf("README", "x.dat")),
        )
    }

    @Test fun keepsTheBreakdownShort() {
        val many = listOf("a.a", "b.b", "c.c", "d.d", "e.e", "f.f")
        assertEquals(listOf("a", "b", "c", "d"), RomScanSummary.extensions(many).map { it.extension })
    }

    @Test fun isEmptyWhenThereIsNothingSkipped() {
        assertTrue(RomScanSummary.extensions(emptyList()).isEmpty())
    }

    @Test fun agesStepFromDaysToWeeksToMonthsToAYear() {
        val day = 86_400_000L
        val now = 1000 * day
        fun age(daysAgo: Long) = RomScanSummary.age(now, now - daysAgo * day)
        assertEquals(RomAge.Today, age(0))
        assertEquals(RomAge.Yesterday, age(1))
        assertEquals(RomAge.Days(2), age(2))
        assertEquals(RomAge.Days(6), age(6))
        assertEquals(RomAge.AWeek, age(7))
        assertEquals(RomAge.AWeek, age(13))
        assertEquals(RomAge.Weeks(2), age(14))
        assertEquals(RomAge.Weeks(8), age(59))
        assertEquals(RomAge.Months(2), age(60))
        assertEquals(RomAge.Months(12), age(364))
        assertEquals(RomAge.AYear, age(365))
        assertEquals(RomAge.AYear, age(900))
    }

    @Test fun aClockSetBackIsToday() {
        assertEquals(RomAge.Today, RomScanSummary.age(now = 0L, then = 86_400_000L))
    }
}
