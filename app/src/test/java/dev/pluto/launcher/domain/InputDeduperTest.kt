package dev.pluto.launcher.domain

import dev.pluto.launcher.input.Direction
import dev.pluto.launcher.input.MoveSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDeduperTest {
    private val deduper = InputDeduper(windowMs = 90)

    @Test
    fun suppressesSameDirectionFromOtherSourceWithinWindow() {
        assertTrue(deduper.shouldEmit(Direction.UP, MoveSource.KEY, 1000))
        assertFalse(deduper.shouldEmit(Direction.UP, MoveSource.HAT, 1010))
        assertFalse(deduper.shouldEmit(Direction.UP, MoveSource.STICK, 1089))
    }

    @Test
    fun allowsOtherSourceAfterWindow() {
        assertTrue(deduper.shouldEmit(Direction.UP, MoveSource.KEY, 1000))
        assertTrue(deduper.shouldEmit(Direction.UP, MoveSource.HAT, 1090))
    }

    @Test
    fun suppressedEventsDoNotExtendWindow() {
        assertTrue(deduper.shouldEmit(Direction.LEFT, MoveSource.HAT, 0))
        assertFalse(deduper.shouldEmit(Direction.LEFT, MoveSource.KEY, 80))
        assertTrue(deduper.shouldEmit(Direction.LEFT, MoveSource.KEY, 95))
    }

    @Test
    fun sameSourceRepeatsAlwaysAllowed() {
        assertTrue(deduper.shouldEmit(Direction.DOWN, MoveSource.KEY, 0))
        assertTrue(deduper.shouldEmit(Direction.DOWN, MoveSource.KEY, 1))
        assertTrue(deduper.shouldEmit(Direction.DOWN, MoveSource.KEY, 2))
    }

    @Test
    fun differentDirectionsAreIndependent() {
        assertTrue(deduper.shouldEmit(Direction.UP, MoveSource.KEY, 0))
        assertTrue(deduper.shouldEmit(Direction.RIGHT, MoveSource.HAT, 5))
        assertTrue(deduper.shouldEmit(Direction.DOWN, MoveSource.STICK, 10))
    }

    @Test
    fun interleavedMoveDoesNotHideDuplicate() {
        assertTrue(deduper.shouldEmit(Direction.UP, MoveSource.KEY, 0))
        assertTrue(deduper.shouldEmit(Direction.RIGHT, MoveSource.STICK, 5))
        assertFalse(deduper.shouldEmit(Direction.UP, MoveSource.HAT, 10))
    }

    @Test
    fun resetForgetsHistory() {
        assertTrue(deduper.shouldEmit(Direction.UP, MoveSource.KEY, 0))
        deduper.reset()
        assertTrue(deduper.shouldEmit(Direction.UP, MoveSource.HAT, 10))
    }
}
