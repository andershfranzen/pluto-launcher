package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomDiscoveryTest {
    @Test fun offersTheFoldersOfInstalledEmulatorsFirst() {
        val suggestions = RomDiscovery.suggestions(setOf("com.retroarch.aarch64", "org.ppsspp.ppsspp"))
        assertEquals(listOf("RetroArch", "PSP", "ROMs", "Games"), suggestions.map { it.folder })
        assertEquals("RetroArch", suggestions.first().app)
    }

    @Test fun ignoresEmulatorsThatAreNotInstalled() {
        val suggestions = RomDiscovery.suggestions(setOf("com.github.stenzek.duckstation"))
        assertEquals(listOf("DuckStation", "ROMs", "Games"), suggestions.map { it.folder })
    }

    @Test fun neverRepeatsAFolder() {
        // RetroArch 64 and the 32-bit build share one folder.
        val suggestions = RomDiscovery.suggestions(setOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32"))
        assertEquals(suggestions.map { it.folder }.distinct(), suggestions.map { it.folder })
    }

    @Test fun stillOffersCommonFoldersWithNoEmulatorInstalled() {
        val suggestions = RomDiscovery.suggestions(emptySet())
        assertEquals(listOf("ROMs", "Games"), suggestions.map { it.folder })
        assertTrue(suggestions.all { it.packageName == null })
    }
}
