package dev.pluto.launcher.domain

import dev.pluto.launcher.input.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StickRepeaterTest {
    // Spec defaults: dead zone 0.25, delay 350 ms, interval 100 ms.
    private val repeater = StickRepeater()

    @Test
    fun defaultsMatchSpec() {
        assertEquals(0.25f, repeater.deadZone)
        assertEquals(350L, repeater.repeatDelayMs)
        assertEquals(100L, repeater.repeatIntervalMs)
    }

    @Test
    fun insideDeadZoneIsNeutral() {
        assertNull(repeater.onSample(0.2f, -0.24f, 0))
        assertNull(repeater.onSample(0.25f, 0f, 0))
        assertNull(repeater.heldDirection)
        assertNull(repeater.nextDeadlineMs())
    }

    @Test
    fun deviceFlatWidensNeutralZone() {
        assertNull(repeater.onSample(0.35f, 0f, 0, flat = 0.4f))
        assertEquals(Direction.RIGHT, repeater.onSample(0.45f, 0f, 10, flat = 0.4f))
    }

    @Test
    fun smallFlatDoesNotShrinkDeadZone() {
        assertNull(repeater.onSample(0.2f, 0f, 0, flat = 0.05f))
    }

    @Test
    fun axisSignsMapToDirections() {
        assertEquals(Direction.RIGHT, StickRepeater().onSample(1f, 0f, 0))
        assertEquals(Direction.LEFT, StickRepeater().onSample(-1f, 0f, 0))
        assertEquals(Direction.DOWN, StickRepeater().onSample(0f, 1f, 0))
        assertEquals(Direction.UP, StickRepeater().onSample(0f, -1f, 0))
    }

    @Test
    fun firstMoveImmediateThenDelayThenInterval() {
        assertEquals(Direction.RIGHT, repeater.onSample(0.9f, 0f, 1000))
        assertEquals(1350L, repeater.nextDeadlineMs())
        // Continued samples before the delay don't emit.
        assertNull(repeater.onSample(0.95f, 0.05f, 1100))
        assertNull(repeater.onTick(1349))
        assertEquals(Direction.RIGHT, repeater.onTick(1350))
        assertEquals(1450L, repeater.nextDeadlineMs())
        assertNull(repeater.onTick(1400))
        assertEquals(Direction.RIGHT, repeater.onTick(1450))
        assertEquals(Direction.RIGHT, repeater.onTick(1550))
        assertEquals(1650L, repeater.nextDeadlineMs())
    }

    @Test
    fun countOfMovesOverOneSecondHold() {
        var moves = 0
        if (repeater.onSample(0f, 1f, 0) != null) moves++
        var t = 0L
        while (t <= 1000) {
            if (repeater.onTick(t) != null) moves++
            t += 10
        }
        // Immediate + repeats at 350, 450, ..., 950 = 1 + 7.
        assertEquals(8, moves)
    }

    @Test
    fun samplesPastDeadlineAlsoRepeat() {
        repeater.onSample(-1f, 0f, 0)
        assertEquals(Direction.LEFT, repeater.onSample(-1f, 0f, 360))
        assertNull(repeater.onSample(-1f, 0f, 370))
    }

    @Test
    fun lateTickDoesNotBurst() {
        repeater.onSample(1f, 0f, 0)
        assertEquals(Direction.RIGHT, repeater.onTick(2000))
        assertNull(repeater.onTick(2000))
        assertEquals(2100L, repeater.nextDeadlineMs())
    }

    @Test
    fun neutralResets() {
        repeater.onSample(1f, 0f, 0)
        assertNull(repeater.onSample(0.1f, 0f, 100))
        assertNull(repeater.heldDirection)
        assertNull(repeater.nextDeadlineMs())
        assertNull(repeater.onTick(400))
        // Leaving neutral again fires immediately.
        assertEquals(Direction.RIGHT, repeater.onSample(1f, 0f, 120))
        assertEquals(470L, repeater.nextDeadlineMs())
    }

    @Test
    fun directionChangeRestartsTiming() {
        repeater.onSample(1f, 0f, 0)
        repeater.onTick(350)
        assertEquals(Direction.DOWN, repeater.onSample(0f, 1f, 400))
        assertEquals(750L, repeater.nextDeadlineMs())
        assertNull(repeater.onTick(450))
        assertEquals(Direction.DOWN, repeater.onTick(750))
    }

    @Test
    fun flickToOppositeSideFiresImmediately() {
        assertEquals(Direction.RIGHT, repeater.onSample(1f, 0f, 0))
        assertEquals(Direction.LEFT, repeater.onSample(-1f, 0f, 30))
    }

    @Test
    fun diagonalResolvesToDominantAxis() {
        assertEquals(Direction.RIGHT, StickRepeater().onSample(0.8f, 0.5f, 0))
        assertEquals(Direction.UP, StickRepeater().onSample(0.5f, -0.8f, 0))
        // Exact diagonal resolves predictably (horizontal).
        assertEquals(Direction.LEFT, StickRepeater().onSample(-0.7f, 0.7f, 0))
    }

    @Test
    fun hysteresisKeepsCurrentDirectionNearDiagonal() {
        assertEquals(Direction.RIGHT, repeater.onSample(0.8f, 0.6f, 0))
        // Vertical slightly larger but within 15% margin: stay RIGHT, no new move.
        assertNull(repeater.onSample(0.7f, 0.75f, 20))
        assertEquals(Direction.RIGHT, repeater.heldDirection)
        // Vertical clearly dominant: switch to DOWN.
        assertEquals(Direction.DOWN, repeater.onSample(0.6f, 0.8f, 40))
        // Back near the diagonal: stay DOWN.
        assertNull(repeater.onSample(0.75f, 0.7f, 60))
        assertEquals(Direction.DOWN, repeater.heldDirection)
    }

    @Test
    fun wobblingAroundDiagonalEmitsOnlyOnce() {
        var moves = 0
        val samples = listOf(0.70f to 0.72f, 0.72f to 0.70f, 0.71f to 0.73f, 0.73f to 0.71f, 0.70f to 0.70f)
        samples.forEachIndexed { i, (x, y) -> if (repeater.onSample(x, y, i * 10L) != null) moves++ }
        assertEquals(1, moves)
    }

    @Test
    fun heldAxisFallingIntoDeadZoneSwitchesToOther() {
        repeater.onSample(0.5f, 0f, 0)
        assertEquals(Direction.DOWN, repeater.onSample(0.1f, 0.3f, 10))
    }

    @Test
    fun resetClearsState() {
        repeater.onSample(1f, 0f, 0)
        repeater.reset()
        assertNull(repeater.heldDirection)
        assertNull(repeater.onTick(1000))
        assertEquals(Direction.RIGHT, repeater.onSample(1f, 0f, 1000))
    }

    @Test
    fun settingsCanBeTunedAtRuntime() {
        val custom = StickRepeater(deadZone = 0.5f, repeatDelayMs = 200, repeatIntervalMs = 50)
        assertNull(custom.onSample(0.45f, 0f, 0))
        assertEquals(Direction.RIGHT, custom.onSample(0.6f, 0f, 0))
        assertEquals(Direction.RIGHT, custom.onTick(200))
        assertEquals(250L, custom.nextDeadlineMs())
    }
}
