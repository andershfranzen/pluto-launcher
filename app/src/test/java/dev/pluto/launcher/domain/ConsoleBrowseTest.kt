package dev.pluto.launcher.domain

import dev.pluto.launcher.model.RomGame
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleBrowseTest {
    private fun game(name: String, title: String = name, modified: Long = 0L) =
        RomGame(uri = "content://rom/$name", fileName = name, title = title, modified = modified)

    private val games = listOf(game("Zelda.nsp", "Zelda"), game("Mario.sfc", "Mario"), game("Spyro.cue", "Spyro"))

    @Test fun offersSystemChipsOnlyWhenAScanSawSeveralSystems() {
        assertEquals(emptyList<String>(), ConsoleBrowse.systems(listOf(game("a.nes"), game("b.nes")), emptyMap()))
        // Mixed file types alone are no evidence: a one-system folder (or a shelf not rescanned
        // since Pluto restarted) gets no chips rather than chips guessed from extensions.
        assertEquals(emptyList<String>(), ConsoleBrowse.systems(games + game("Metroid.nes"), emptyMap()))
        val psx = listOf(game("Crash.cue"), game("Spyro.pbp"))
        assertEquals(emptyList<String>(), ConsoleBrowse.systems(psx, emptyMap()))
    }

    @Test fun aScanSaysWhichSystemAGameIsIn() {
        val systems = mapOf("content://rom/Spyro.cue" to "PlayStation")
        assertEquals(listOf("Nintendo Switch", "PlayStation", "SNES"), ConsoleBrowse.systems(games, systems))
        assertEquals("PlayStation", ConsoleBrowse.systemOf(games[2], systems))
    }

    @Test fun filtersBySystemAndFavourites() {
        assertEquals(listOf("Zelda.nsp"), ConsoleBrowse.select(games, "Nintendo Switch", emptyMap(), false, emptySet()).map { it.fileName })
        assertEquals(
            listOf("Mario.sfc"),
            ConsoleBrowse.select(games, null, emptyMap(), true, setOf("content://rom/Mario.sfc")).map { it.fileName },
        )
        // A system filter and the favourites filter apply together.
        assertEquals(
            emptyList<String>(),
            ConsoleBrowse.select(games, "NES", emptyMap(), true, setOf("content://rom/Mario.sfc")).map { it.fileName },
        )
    }

    @Test fun sortsByTitleRecentOrFileName() {
        val recent = listOf(game("a.sfc", "Alpha", 5L), game("z.sfc", "Zulu", 50L), game("m.sfc", "Mike", 9L))
        assertEquals(listOf("Alpha", "Mike", "Zulu"), ConsoleBrowse.sort(recent, RomSort.TITLE).map { it.title })
        assertEquals(listOf("Zulu", "Mike", "Alpha"), ConsoleBrowse.sort(recent, RomSort.RECENT).map { it.title })
        val names = listOf(game("z.sfc", "Same"), game("a.sfc", "Same"))
        assertEquals(listOf("a.sfc", "z.sfc"), ConsoleBrowse.sort(names, RomSort.FILENAME).map { it.fileName })
        // Equal titles keep a stable order rather than reshuffling on every rescan.
        assertEquals(listOf("z.sfc", "a.sfc"), ConsoleBrowse.select(names, null, emptyMap(), false, emptySet()).map { it.fileName })
        assertEquals(listOf("z.sfc", "a.sfc"), ConsoleBrowse.sort(names, RomSort.TITLE).map { it.fileName })
    }

    @Test fun recentPrefersWhenAGameWasLastOpened() {
        val recent = listOf(game("a.sfc", "Alpha", 5L), game("z.sfc", "Zulu", 50L), game("m.sfc", "Mike", 9L))
        // No play dates yet: the order is the files' own dates.
        assertEquals(listOf("Zulu", "Mike", "Alpha"), ConsoleBrowse.sort(recent, RomSort.RECENT).map { it.title })
        // Alpha was opened most recently, so it leads; the others keep their file dates.
        assertEquals(
            listOf("Alpha", "Zulu", "Mike"),
            ConsoleBrowse.sort(recent, RomSort.RECENT, mapOf("content://rom/a.sfc" to 900L)).map { it.title },
        )
    }
}
