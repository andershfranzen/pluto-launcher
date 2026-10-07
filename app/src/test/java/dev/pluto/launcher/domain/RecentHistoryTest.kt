package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentHistoryTest {
    @Test
    fun recordsIntoEmptyHistory() {
        assertEquals(listOf("a"), RecentHistory.record(emptyList(), "a"))
    }

    @Test
    fun newEntryGoesToFront() {
        assertEquals(listOf("c", "a", "b"), RecentHistory.record(listOf("a", "b"), "c"))
    }

    @Test
    fun relaunchMovesToFrontWithoutDuplicating() {
        assertEquals(listOf("b", "a", "c"), RecentHistory.record(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("a", "b", "c"), RecentHistory.record(listOf("a", "b", "c"), "a"))
    }

    @Test
    fun removesPreexistingDuplicates() {
        assertEquals(listOf("x", "a", "b"), RecentHistory.record(listOf("a", "b", "a", "b"), "x"))
    }

    @Test
    fun capsAtTwelveDroppingOldest() {
        val full = (1..12).map { "app$it" }
        val result = RecentHistory.record(full, "new")
        assertEquals(RecentHistory.MAX_ENTRIES, result.size)
        assertEquals("new", result.first())
        assertEquals("app11", result.last())
    }

    @Test
    fun relaunchInFullHistoryKeepsAllOthers() {
        val full = (1..12).map { "app$it" }
        val result = RecentHistory.record(full, "app12")
        assertEquals(listOf("app12") + (1..11).map { "app$it" }, result)
    }

    @Test
    fun oversizedInputIsTruncated() {
        val result = RecentHistory.record((1..30).map { it }, 0)
        assertEquals((0..11).toList(), result)
    }
}
