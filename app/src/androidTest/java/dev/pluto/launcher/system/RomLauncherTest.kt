package dev.pluto.launcher.system

import android.content.Intent
import dev.pluto.launcher.model.*
import org.junit.Assert.*
import org.junit.Test

class RomLauncherTest {
    private val game = RomGame("content://documents/spyro.cue", "spyro.cue", "Spyro", 0)
    private val library = RomLibrary(1, "PS1", "content://documents/tree/ps1", "org.example.emulator")
    @Test fun preferredEmulatorReceivesDocumentWithReadGrant() {
        val intent = RomLauncher.intent(library, game)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(library.emulatorPackage, intent.`package`)
        assertEquals(game.uri, intent.data.toString())
        assertEquals(game.uri, intent.clipData!!.getItemAt(0).uri.toString())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
        assertEquals(library.treeUri, intent.clipData!!.getItemAt(1).uri.toString())
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    @Test fun duckStationReceivesBootPathAndDocumentGrant() {
        val intent = RomLauncher.intent(library.copy(emulatorPackage = RomLauncher.DUCKSTATION_PACKAGE), game)
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", intent.component!!.className)
        assertEquals(game.uri, intent.getStringExtra("bootPath"))
        assertFalse(intent.getBooleanExtra("resumeState", true))
        assertEquals(game.uri, intent.clipData!!.getItemAt(0).uri.toString())
    }
    @Test fun askEachTimeDoesNotPinTheChooserToOneEmulator() {
        val chooser = RomLauncher.intent(library.copy(launchMode = RomLaunchMode.OPEN_WITH), game)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val open = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertNull(open.`package`)
        assertNull(open.component)
        assertEquals(game.uri, open.data.toString())
    }
    @Test fun aGamesOwnEmulatorWinsOverTheTabEvenWhenTheTabAsks() {
        val intent = RomLauncher.intent(library.copy(launchMode = RomLaunchMode.OPEN_WITH), game, "org.other.emulator")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("org.other.emulator", intent.`package`)
        val duck = RomLauncher.intent(library, game, RomLauncher.DUCKSTATION_PACKAGE)
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", duck.component!!.className)
    }
    @Test fun unverifiedDirectLaunchesFromAPreReleaseOpenTheFileInstead() {
        listOf(RomLaunchMode.RETROARCH, RomLaunchMode.DOLPHIN, RomLaunchMode.PPSSPP).forEach { mode ->
            val intent = RomLauncher.intent(library.copy(emulatorPackage = "com.retroarch.aarch64", launchMode = mode), game)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("com.retroarch.aarch64", intent.`package`)
            assertNull(intent.component)
            assertEquals(game.uri, intent.data.toString())
        }
        // Automatic never picks an adapter for those emulators either.
        assertNull(RomLauncher.intent(library.copy(emulatorPackage = "org.dolphinemu.dolphinemu"), game).component)
    }
}
