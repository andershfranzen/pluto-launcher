package dev.pluto.launcher.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SideDockTest {
    @Test
    fun keepsPreferredIconWhenThereIsRoom() {
        assertEquals(46.8.dp.value, sideDockIconSize(400.dp, 46.8.dp)!!.value, 0.01f)
    }

    @Test
    fun shrinksSlotsToFitPhoneLandscapeHeight() {
        // Emulator 1080x2400 landscape: about 303dp below the header. Five 58.8dp slots do not fit.
        val icon = sideDockIconSize(303.dp, 46.8.dp)!!
        val slot = icon + 12.dp
        val needed = slot * 5 + 2.dp * 4 + 28.dp
        assert(needed <= 303.dp) { "dock needs $needed" }
        assert(slot >= 48.dp)
    }

    @Test
    fun fallsBackToBottomDockWhenTouchTargetsCannotFit() {
        assertNull(sideDockIconSize(250.dp, 46.8.dp))
    }
}
