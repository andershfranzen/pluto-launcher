package dev.pluto.launcher.domain

import org.junit.Assert.*
import org.junit.Test

class RomFilesTest {
    @Test fun recognizesRomFormatsWithoutTreatingArtworkOrSavesAsGames() {
        listOf("Spyro.CHD", "disc.cue", "game.gba", "collection.7z", "discs.m3u", "zelda.nsp", "Mario.XCI", "kirby.nsz", "metroid.xcz").forEach { assertTrue(RomFiles.supported(it)) }
        listOf("cover.jpg", "game.sav", "README", "game.iso.txt").forEach { assertFalse(RomFiles.supported(it)) }
    }
    @Test fun recognizesClassicFormatsAcrossSystems() {
        listOf("Sonic.md", "Contra.nes", "Metroid.sfc", "Goldeneye.z64", "Pokemon.gbc", "Zelda.3ds", "Mario Galaxy.rvz",
            "Lynx game.lnx", "Pitfall.a26", "Turrican.adf", "Elite.d64", "Manic Miner.tzx", "Soulcalibur.gdi", "Pac-Man.col")
            .forEach { assertTrue(it, RomFiles.supported(it)) }
        // Data halves of multi-file dumps are reached through their sheet, not listed twice.
        listOf("game.img", "game.sub", "game.mdf", "track03.raw").forEach { assertFalse(it, RomFiles.supported(it)) }
    }
    @Test fun gdiReferencesQuotedAndUnquotedTracks() {
        assertEquals(listOf("track01.bin", "track02.raw", "track 03.bin"), RomFiles.referencedFiles("Soulcalibur.GDI", """
            3
            1 0 4 2352 track01.bin 0
            2 756 0 2352 track02.raw 0
            3 45000 4 2352 "track 03.bin" 0
        """.trimIndent()))
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
        assertEquals("The Legend of Zelda - Tears of the Kingdom", RomFiles.title("The Legend of Zelda - Tears of the Kingdom [0100F2C0115B6000][v0].nsp"))
    }
}
