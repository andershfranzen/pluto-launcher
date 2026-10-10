package dev.pluto.launcher.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverflowMathTest {
    private val eps = 1e-4f

    @Test
    fun centreCardFacesTheViewer() {
        val t = CoverflowMath.transform(0f)
        assertEquals(0f, t.offsetX, eps)
        assertEquals(1f, t.scale, eps)
        assertEquals(0f, t.rotationY, eps)
        assertEquals(0f, t.dim, eps)
        assertEquals(1f, t.alpha, eps)
    }

    @Test
    fun neighboursTiltAwaySymmetrically() {
        val right = CoverflowMath.transform(1f)
        val left = CoverflowMath.transform(-1f)
        assertEquals(CoverflowMath.FIRST_GAP, right.offsetX, eps)
        assertEquals(-CoverflowMath.FIRST_GAP, left.offsetX, eps)
        assertEquals(-CoverflowMath.SIDE_ANGLE, right.rotationY, eps)
        assertEquals(CoverflowMath.SIDE_ANGLE, left.rotationY, eps)
        assertEquals(CoverflowMath.SIDE_SCALE, right.scale, eps)
        assertEquals(right.scale, left.scale, eps)
        assertTrue(right.dim > 0f)
    }

    @Test
    fun offsetIsContinuousAndMonotonic() {
        var previous = Float.NEGATIVE_INFINITY
        var d = -6f
        while (d <= 6f) {
            val x = CoverflowMath.offset(d)
            assertTrue("offset must increase at d=$d", x > previous)
            // No jumps: a small step in d is a small step in x.
            if (previous.isFinite()) assertTrue(x - previous < 0.05f)
            previous = x
            d += 0.01f
        }
    }

    @Test
    fun cardsShrinkDimAndSinkWithDistance() {
        val near = CoverflowMath.transform(1f)
        val far = CoverflowMath.transform(3f)
        assertTrue(far.scale < near.scale)
        assertTrue(far.dim > near.dim)
        assertTrue(far.z < near.z)
        assertTrue(far.scale >= CoverflowMath.MIN_SCALE)
        assertTrue(far.dim <= CoverflowMath.MAX_DIM)
    }

    @Test
    fun edgeCardsFadeOutBeforeLeavingTheWindow() {
        // The selection and its neighbours are solid; further out the flow thins gradually,
        // so the outermost resting card is already translucent rather than a hard end.
        assertEquals(1f, CoverflowMath.transform(0f).alpha, eps)
        assertEquals(1f, CoverflowMath.transform(1f).alpha, eps)
        val second = CoverflowMath.transform(2f).alpha
        val third = CoverflowMath.transform(3f).alpha
        assertTrue(second in 0f..1f && third < second && third > 0f)
        assertEquals(0f, CoverflowMath.transform(CoverflowMath.RADIUS.toFloat()).alpha, eps)
    }

    @Test
    fun windowCoversSelectionAndLaggingPosition() {
        assertEquals(0..4, CoverflowMath.window(0f, 0, 20))
        assertEquals(6..14, CoverflowMath.window(10f, 10, 20))
        // Held D-pad: the selection runs ahead of the gliding position; both neighbourhoods stay composed.
        assertEquals(5..16, CoverflowMath.window(9.2f, 12, 20))
        assertEquals(15..19, CoverflowMath.window(19f, 19, 20))
        assertTrue(CoverflowMath.window(0f, 0, 0).isEmpty())
    }

    @Test
    fun snapGoesToNearestCardWithoutVelocity() {
        assertEquals(3, CoverflowMath.snapTarget(3.4f, 0f, 10))
        assertEquals(4, CoverflowMath.snapTarget(3.6f, 0f, 10))
    }

    @Test
    fun flingsCarryFurtherAndClamp() {
        assertTrue(CoverflowMath.snapTarget(3f, 20f, 50) > 4)
        assertTrue(CoverflowMath.snapTarget(10f, -20f, 50) < 9)
        assertEquals(9, CoverflowMath.snapTarget(8f, 500f, 10))
        assertEquals(0, CoverflowMath.snapTarget(1f, -500f, 10))
        assertEquals(0, CoverflowMath.snapTarget(-0.3f, 0f, 10))
    }

    @Test
    fun dragIsOneToOneWithTheFirstGap() {
        val card = 200f
        // Dragging left by one gap brings the next card to the centre.
        assertEquals(1f, CoverflowMath.dragDelta(-card * CoverflowMath.FIRST_GAP, card), eps)
        assertEquals(-0.5f, CoverflowMath.dragDelta(card * CoverflowMath.FIRST_GAP / 2f, card), eps)
        assertEquals(0f, CoverflowMath.dragDelta(10f, 0f), eps)
    }

    @Test
    fun ghostSlotsFillShortShelvesOnly() {
        assertEquals(IntRange.EMPTY, CoverflowMath.ghostSlots(0))
        assertEquals(1..3, CoverflowMath.ghostSlots(1))
        assertEquals(3..3, CoverflowMath.ghostSlots(3))
        assertEquals(IntRange.EMPTY, CoverflowMath.ghostSlots(CoverflowMath.MIN_SLOTS))
        assertEquals(IntRange.EMPTY, CoverflowMath.ghostSlots(20))
    }

    @Test
    fun sideTiltKeepsIconsRound() {
        // A side card keeps at least ~3/4 of its width (cos of the tilt): icons stay round.
        assertTrue(kotlin.math.cos(Math.toRadians(CoverflowMath.SIDE_ANGLE.toDouble())) >= 0.76)
    }
}
