package dev.pluto.launcher.ui.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerMotionTest {
    private val eps = 1e-4f

    @Test
    fun endpointsAreIdentityWhenShown() {
        assertEquals(1f, LayerMotion.folderScale(1f), eps)
        assertEquals(1f, LayerMotion.sheetScale(1f, entering = true), eps)
        assertEquals(1f, LayerMotion.sheetScale(1f, entering = false), eps)
        assertEquals(1f, LayerMotion.onboardingScale(1f), eps)
        assertEquals(0f, LayerMotion.drawerOffset(1f, 2000f), eps)
        assertEquals(1f, LayerMotion.recedeScale(0f), eps)
        assertEquals(1f, LayerMotion.recedeAlpha(0f), eps)
        assertEquals(1f, LayerMotion.beneathPageAlpha(0f), eps)
        assertEquals(1f, LayerMotion.fadeEarly(1f, 0.2f), eps)
        assertEquals(1f, LayerMotion.fadeLate(1f, 0.15f), eps)
    }

    @Test
    fun absentEndpoints() {
        assertEquals(LayerMotion.FOLDER_START_SCALE, LayerMotion.folderScale(0f), eps)
        assertEquals(2000f, LayerMotion.drawerOffset(0f, 2000f), eps)
        assertEquals(PlutoMotion.RECEDE_SCALE, LayerMotion.recedeScale(1f), eps)
        assertEquals(LayerMotion.RECEDE_ALPHA, LayerMotion.recedeAlpha(1f), eps)
        assertEquals(0f, LayerMotion.beneathPageAlpha(1f), eps)
        assertEquals(0f, LayerMotion.fadeLate(0.1f, 0.15f), eps)
        // Sheets never shrink below full size, so their full-screen scrim covers the window.
        assertTrue(LayerMotion.sheetScale(0f, entering = true) >= 1f)
        assertTrue(LayerMotion.sheetScale(-0.2f, entering = false) >= 1f)
    }

    @Test
    fun alphasStayInRangeOnSpringOvershoot() {
        for (p in listOf(-0.1f, 1.1f)) {
            val values = listOf(
                LayerMotion.fadeEarly(p, 0.2f), LayerMotion.fadeLate(p, 0.15f), LayerMotion.beneathPageAlpha(p),
                LayerMotion.recedeAlpha(p),
            )
            values.forEach { assertTrue("$it at $p", it in 0f..1f) }
        }
    }

    @Test
    fun pageTravelIsCapped() {
        assertEquals(200f, LayerMotion.pageTravel(1000f, density = 3f), eps)
        assertEquals(360f, LayerMotion.pageTravel(2400f, density = 3f), eps)
        assertTrue(LayerMotion.beneathShift(2400f, 3f) < LayerMotion.pageTravel(2400f, 3f))
    }

    @Test
    fun backScrubOnlyCoversPartOfTheExit() {
        assertEquals(1f, LayerMotion.backScrub(0f), eps)
        assertEquals(1f - LayerMotion.BACK_SCRUB, LayerMotion.backScrub(1f), eps)
        assertEquals(1f - LayerMotion.BACK_SCRUB, LayerMotion.backScrub(3f), eps)
    }

    @Test
    fun originIsTileCentreRelativeToLayer() {
        val origin = LayerMotion.originOf(Rect(100f, 200f, 200f, 400f), Offset(0f, 100f), Size(1000f, 1000f))
        assertEquals(0.15f, origin.pivotFractionX, eps)
        assertEquals(0.2f, origin.pivotFractionY, eps)
        assertEquals(TransformOrigin.Center, LayerMotion.originOf(null, Offset.Zero, Size(10f, 10f)))
        assertEquals(TransformOrigin.Center, LayerMotion.originOf(Rect(0f, 0f, 1f, 1f), Offset.Zero, Size.Zero))
        // Off-window bounds are clamped onto the layer.
        val clamped = LayerMotion.originOf(Rect(-500f, 5000f, -400f, 5100f), Offset.Zero, Size(1000f, 1000f))
        assertEquals(0f, clamped.pivotFractionX, eps)
        assertEquals(1f, clamped.pivotFractionY, eps)
    }
}
