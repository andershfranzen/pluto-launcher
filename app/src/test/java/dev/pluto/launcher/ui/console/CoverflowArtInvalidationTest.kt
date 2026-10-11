package dev.pluto.launcher.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CoverflowArtInvalidationTest {
    @Test
    fun loadingOneCoverDoesNotInvalidateOtherCards() {
        val stage = CoverflowState(0, emptyList())
        val first = stage.artRevision("first")
        val second = stage.artRevision("second")
        stage.artLoaded("first", 1)
        assertEquals(1, first.intValue)
        assertEquals(0, second.intValue)
        assertSame(first, stage.artRevision("first"))
        stage.artLoaded("first", 1)
        assertEquals(1, first.intValue)
        stage.artLoaded("first", 3)
        assertEquals(2, first.intValue)
    }

    @Test
    fun prewarmingUnobservedCoverDoesNotInvalidateVisibleCards() {
        val stage = CoverflowState(0, emptyList())
        val visible = stage.artRevision("visible")
        stage.artLoaded("offscreen", 2)
        assertEquals(0, visible.intValue)
        assertEquals(0, stage.artRevision("offscreen").intValue)
    }
}
