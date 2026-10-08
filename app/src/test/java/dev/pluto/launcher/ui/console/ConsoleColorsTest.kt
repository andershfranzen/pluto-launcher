package dev.pluto.launcher.ui.console

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleColorsTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun saturatedLogoWinsOverWhitePlate() {
        val white = argb(255, 255, 255, 255)
        val red = argb(255, 220, 20, 30)
        val pixels = IntArray(100) { if (it < 20) red else white }
        val c = ConsoleColors.dominant(pixels)
        assertEquals(220, (c shr 16) and 0xFF)
        assertEquals(20, (c shr 8) and 0xFF)
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val blue = argb(255, 10, 40, 200)
        val pixels = IntArray(50) { if (it % 2 == 0) blue else argb(0, 255, 0, 0) }
        assertEquals(blue, ConsoleColors.dominant(pixels))
    }

    @Test
    fun monochromeIconFallsBackToAverage() {
        val grey = argb(255, 100, 100, 100)
        assertEquals(grey, ConsoleColors.dominant(IntArray(10) { grey }))
    }

    @Test
    fun emptyImageIsNeutral() {
        assertEquals(ConsoleColors.NEUTRAL, ConsoleColors.dominant(IntArray(4)))
    }

    @Test
    fun darkenScalesChannels() {
        assertEquals(argb(255, 50, 25, 10), ConsoleColors.darken(argb(255, 100, 50, 20), 0.5f))
    }
}
