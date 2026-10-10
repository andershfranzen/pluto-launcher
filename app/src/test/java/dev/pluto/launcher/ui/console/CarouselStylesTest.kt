package dev.pluto.launcher.ui.console

import dev.pluto.launcher.data.prefs.CarouselStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselStylesTest {
    private val eps = 1e-4f

    @Test
    fun everyStyleShowsTheSelectionCentredAtFullSize() {
        for (style in CarouselStyle.entries) {
            val t = CoverflowMath.transform(0f, style)
            assertEquals("$style x", 0f, t.offsetX, eps)
            assertEquals("$style scale", 1f, t.scale, eps)
            assertEquals("$style alpha", 1f, t.alpha, eps)
            assertEquals("$style dim", 0f, t.dim, eps)
        }
    }

    @Test
    fun laterCardsSitToTheRightAndOffsetsAreContinuous() {
        for (style in CarouselStyle.entries) {
            var previous = CoverflowMath.offset(-0.5f, style)
            var d = -0.5f
            while (d < 3f) {
                d += 0.05f
                val x = CoverflowMath.offset(d, style)
                assertTrue("$style jumps at $d", kotlin.math.abs(x - previous) < 0.2f)
                previous = x
            }
            assertTrue("$style right", CoverflowMath.offset(1f, style) > 0f)
        }
    }

    @Test
    fun ringHidesCardsTurnedAwayAndDrawsTheFrontOnTop() {
        assertEquals(0f, CoverflowMath.transform(3.7f, CarouselStyle.RING).alpha, eps)
        assertTrue(CoverflowMath.transform(0f, CarouselStyle.RING).z > CoverflowMath.transform(1f, CarouselStyle.RING).z)
    }

    @Test
    fun deckPassedCardsFadeAway() {
        assertEquals(0f, CoverflowMath.transform(-1.2f, CarouselStyle.DECK).alpha, eps)
        assertTrue(CoverflowMath.transform(1f, CarouselStyle.DECK).alpha > 0.9f)
    }
}
