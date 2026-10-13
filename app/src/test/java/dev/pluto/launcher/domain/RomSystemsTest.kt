package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RomSystemsTest {
    @Test fun readsTheSystemFromASystemFolder() {
        assertEquals("SNES", RomSystems.ofPath("snes/Super Mario World.sfc"))
        assertEquals("PlayStation", RomSystems.ofPath("ps1/Spyro (Europe).cue"))
        assertEquals("Nintendo Switch", RomSystems.ofPath("switch/Game [0100].nsp"))
        assertEquals("Game Boy Advance", RomSystems.ofPath("Game Boy Advance/Game.gba"))
    }

    @Test fun folderWinsOverAnAmbiguousExtension() {
        // .iso names no single system, so the folder decides.
        assertEquals("PlayStation 2", RomSystems.ofPath("ps2/Game.iso"))
        assertEquals("Sega Saturn", RomSystems.ofPath("saturn/Game.iso"))
    }

    @Test fun systemFolderWinsOverARegionFolder() {
        assertEquals("SNES", RomSystems.ofPath("roms/snes/usa/Game.sfc"))
        assertEquals("Mega Drive", RomSystems.ofPath("roms/megadrive/Japan/Game.md"))
    }

    @Test fun fallsBackToTheExtension() {
        assertEquals("NES", RomSystems.of("Game.nes"))
        assertEquals("Game Boy", RomSystems.of("Game.GB"))
        assertEquals("Nintendo Switch", RomSystems.of("Game [0100].xci"))
        assertEquals(RomSystems.OTHER, RomSystems.of("readme.txt"))
    }

    @Test fun unknownFoldersDoNotInventASystem() {
        assertEquals(RomSystems.OTHER, RomSystems.ofPath("My Gamedata/Game.unknownext"))
    }

    @Test fun discExtensionsWithoutASystemFolderAreHonest() {
        assertEquals(RomSystems.DISC, RomSystems.of("Game.cue"))
        assertEquals("PlayStation Portable", RomSystems.of("Game.cso"))
    }
}
