package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomScanSummaryTest {
    @Test fun countsExtensionsMostCommonFirst() {
        assertEquals(".sav×2, .txt×1", RomScanSummary.extensions(listOf("a.sav", "b.sav", "c.txt")))
        assertEquals(".nfo×1, .sav×1", RomScanSummary.extensions(listOf("a.SAV", "b.nfo")))
    }

    @Test fun namesFilesWithoutAnExtension() {
        assertEquals("no extension×1, .dat×1", RomScanSummary.extensions(listOf("README", "x.dat")))
    }

    @Test fun keepsTheBreakdownShort() {
        val many = listOf("a.a", "b.b", "c.c", "d.d", "e.e", "f.f")
        assertEquals(".a×1, .b×1, .c×1, .d×1", RomScanSummary.extensions(many))
    }

    @Test fun returnsNullWhenThereIsNothingSkipped() {
        assertNull(RomScanSummary.extensions(emptyList()))
    }
}
