package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusAnchorTest {
    private val before = listOf("a", "b", "c", "d", "e")

    @Test
    fun keepsFocusedItemWhenStillPresent() {
        assertEquals("c", FocusAnchor.resolve(before, listOf("e", "c", "a"), "c"))
    }

    @Test
    fun keepsIdentityAcrossReorderNotIndex() {
        assertEquals("b", FocusAnchor.resolve(before, before.reversed(), "b"))
    }

    @Test
    fun removedItemFocusesNextNeighbour() {
        assertEquals("d", FocusAnchor.resolve(before, listOf("a", "b", "d", "e"), "c"))
    }

    @Test
    fun removedLastItemFocusesPreviousNeighbour() {
        assertEquals("d", FocusAnchor.resolve(before, listOf("a", "b", "c", "d"), "e"))
    }

    @Test
    fun prefersNextOverPreviousAtSameDistance() {
        assertEquals("d", FocusAnchor.resolve(before, listOf("b", "d"), "c"))
    }

    @Test
    fun walksOutwardWhenNeighboursAlsoRemoved() {
        // c, d removed and b removed: next at distance 2 is e.
        assertEquals("e", FocusAnchor.resolve(before, listOf("a", "e"), "c"))
        // c, d, e removed: previous at distance 1 is b.
        assertEquals("b", FocusAnchor.resolve(before, listOf("a", "b"), "c"))
        // Only the far end survives.
        assertEquals("a", FocusAnchor.resolve(before, listOf("a"), "e"))
    }

    @Test
    fun fallsBackToClampedOldIndexWhenNoOldNeighbourSurvives() {
        // Everything replaced by new installs.
        assertEquals("z", FocusAnchor.resolve(before, listOf("x", "y", "z"), "e"))
        assertEquals("y", FocusAnchor.resolve(before, listOf("x", "y", "z"), "b"))
    }

    @Test
    fun emptyNewListGivesNull() {
        assertNull(FocusAnchor.resolve(before, emptyList(), "c"))
    }

    @Test
    fun nullFocusStaysNull() {
        assertNull(FocusAnchor.resolve(before, before, null))
    }

    @Test
    fun unknownFocusFallsBackToFirst() {
        assertEquals("a", FocusAnchor.resolve(before, before, "q"))
    }
}
