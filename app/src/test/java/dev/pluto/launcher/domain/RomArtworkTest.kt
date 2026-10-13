package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomArtworkTest {
    @Test fun recognizesImageExtensions() {
        listOf("a.png", "a.JPG", "a.jpeg", "a.WebP").forEach { assertTrue(it, RomArtwork.isImage(it)) }
        listOf("a.cue", "a.txt", "a").forEach { assertFalse(it, RomArtwork.isImage(it)) }
    }

    @Test fun prefersTheImageBesideTheGame() {
        val index = RomArtwork.index(listOf("ps1/Spyro.png", "ps1/boxart/Spyro.png"))
        assertEquals("ps1/Spyro.png", index.art("ps1/Spyro.cue", "Spyro.cue"))
    }

    @Test fun findsBoxArtInAnEsDeMediaFolder() {
        val index = RomArtwork.index(listOf("ps1/media/boxart/Spyro - Year of the Dragon (Europe).png"))
        assertEquals(
            "ps1/media/boxart/Spyro - Year of the Dragon (Europe).png",
            index.art("ps1/Spyro - Year of the Dragon (Europe).cue", "Spyro - Year of the Dragon (Europe).cue"),
        )
    }

    @Test fun prefersBoxartFoldersOverGenericOnes() {
        val index = RomArtwork.index(listOf("ps1/screenshots/Spyro.png", "ps1/boxart/Spyro.png"))
        assertEquals("ps1/boxart/Spyro.png", index.art("ps1/Spyro.cue", "Spyro.cue"))
    }

    @Test fun readsRetroArchThumbnailSetsAndSuffixedNames() {
        val retro = RomArtwork.index(listOf("ps1/retroarch/Named_Boxarts/Spyro.png"))
        assertEquals("ps1/retroarch/Named_Boxarts/Spyro.png", retro.art("ps1/Spyro.cue", "Spyro.cue"))
        val suffixed = RomArtwork.index(listOf("ps1/Spyro-thumb.png"))
        assertEquals("ps1/Spyro-thumb.png", suffixed.art("ps1/Spyro.cue", "Spyro.cue"))
    }

    @Test fun fallsBackToArtworkNamedAfterTheCleanedTitle() {
        val index = RomArtwork.index(listOf("ps1/boxart/Spyro - Year of the Dragon.png"))
        assertEquals(
            "ps1/boxart/Spyro - Year of the Dragon.png",
            index.art("ps1/Spyro - Year of the Dragon (Europe) (Rev 1).cue", "Spyro - Year of the Dragon (Europe) (Rev 1).cue"),
        )
    }

    @Test fun ignoresNonImageFilesAndReturnsNullWhenThereIsNoArt() {
        val index = RomArtwork.index(listOf("ps1/Spyro.cue", "ps1/README.txt"))
        assertNull(index.art("ps1/Spyro.cue", "Spyro.cue"))
        assertNull(index.art("ps1/Other.cue", "Other.cue"))
    }
}
