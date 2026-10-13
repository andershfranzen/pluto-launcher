package dev.pluto.launcher.domain

import dev.pluto.launcher.model.RomGame
import org.junit.Assert.assertEquals
import org.junit.Test

class RomHistoryTest {
    @Test fun stampsAGameAndKeepsTheOthers() {
        assertEquals(mapOf("a" to 1L, "b" to 2L), RomHistory.record(mapOf("a" to 1L), "b", 2L))
    }

    @Test fun openingAGameAgainMovesItToItsNewDate() {
        assertEquals(mapOf("a" to 5L), RomHistory.record(mapOf("a" to 1L), "a", 5L))
    }

    @Test fun dropsTheOldestGamesOnceTheCapIsReached() {
        val full = (1..3).associate { "g$it" to it.toLong() }
        val played = RomHistory.record(full, "new", 4L, limit = 3)
        assertEquals(setOf("g2", "g3", "new"), played.keys)
        assertEquals(4L, played.getValue("new"))
    }

    @Test fun aKnownGameHasAPlayDateAndAnUnknownOneUsesItsFileDate() {
        val played = mapOf("content://rom/a" to 99L)
        val opened = RomGame(uri = "content://rom/a", fileName = "a.sfc", title = "A", modified = 1L)
        val untouched = RomGame(uri = "content://rom/b", fileName = "b.sfc", title = "B", modified = 7L)
        assertEquals(99L, RomHistory.lastPlayed(played, opened))
        assertEquals(7L, RomHistory.lastPlayed(played, untouched))
    }
}
