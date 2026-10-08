package dev.pluto.launcher.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerRevealReleaseTest {
    @Test
    fun releaseRecordsTheOutcomeUntilTheNextDrag() {
        val reveal = DrawerRevealState()
        assertNull(reveal.releaseTarget)
        reveal.beginDrag()
        assertTrue(reveal.isDragging)
        // Fast upward fling opens even from closed.
        assertTrue(reveal.release(velocityPxPerSec = 5000f, travelPx = 1000f))
        assertFalse(reveal.isDragging)
        assertEquals(true, reveal.releaseTarget)
        reveal.beginDrag()
        assertNull(reveal.releaseTarget)
        // Slow release below half way stays closed.
        assertFalse(reveal.release(velocityPxPerSec = 0f, travelPx = 1000f))
        assertEquals(false, reveal.releaseTarget)
    }

    @Test
    fun endDragDecidesWithoutVelocity() {
        val reveal = DrawerRevealState()
        reveal.beginDrag()
        reveal.endDrag(open = true)
        assertFalse(reveal.isDragging)
        assertEquals(true, reveal.releaseTarget)
    }
}
