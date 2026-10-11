package dev.pluto.launcher.domain

import org.junit.Assert.*
import org.junit.Test

class RomFilesTest {
    @Test fun recognizesRomFormatsWithoutTreatingArtworkOrSavesAsGames() {
        listOf("Spyro.CHD", "disc.cue", "game.gba", "collection.7z", "discs.m3u").forEach { assertTrue(RomFiles.supported(it)) }
        listOf("cover.jpg", "game.sav", "README", "game.iso.txt").forEach { assertFalse(RomFiles.supported(it)) }
    }
    @Test fun cueReferencesQuotedAndUnquotedTracks() {
        assertEquals(listOf("Spyro track 1.bin", "track02.bin"), RomFiles.referencedFiles("game.cue", """
            FILE "Spyro track 1.bin" BINARY
              TRACK 01 MODE2/2352
            FILE track02.bin BINARY
        """.trimIndent()))
    }
    @Test fun playlistsIgnoreCommentsAndEmptyLines() {
        assertEquals(listOf("Disc 1/game.cue", "Disc 2/game.chd"), RomFiles.referencedFiles("game.M3U", "# discs\r\nDisc 1/game.cue\n\n Disc 2/game.chd \n"))
    }
    @Test fun titlesHideDumpMetadataButKeepDiscInformation() {
        assertEquals("Spyro - Year of the Dragon", RomFiles.title("Spyro - Year of the Dragon   (Rev 1) (axekin.com).cue"))
        assertEquals("Spyro - Year of the Dragon", RomFiles.title("Spyro - Year of the Dragon (Europe) (En,Fr,De,Es,It) (Rev 1) [!].cue"))
        assertEquals("Spyro 3 (Disc 1)", RomFiles.title("Spyro_3_(Disc_1).chd"))
        assertEquals("Final Fantasy VII (Disc 2)", RomFiles.title("Final Fantasy VII (USA) (Disc 2) (v1.1).chd"))
        assertEquals("Game (Special Edition)", RomFiles.title("Game (Special Edition) (Japan).iso"))
    }
}
