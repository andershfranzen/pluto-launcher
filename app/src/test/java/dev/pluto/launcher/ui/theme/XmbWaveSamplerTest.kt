package dev.pluto.launcher.ui.theme

import dev.pluto.launcher.data.prefs.BackgroundFrameRate
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class XmbWaveSamplerTest {
    @Test
    fun cachedWavesPreserveOriginalCurvesAcrossTimeAndSizes() {
        val steps = 56
        val sampler = XmbWaveSampler(steps)
        val out = FloatArray(steps + 1)
        val tau = 2f * PI.toFloat()
        for (height in listOf(720f, 2160f)) {
            for (t in listOf(0f, 37f, 60f, 600f, 3600f)) {
                for (phase in listOf(0f, 0.42f, 1.2f, 3.3f)) {
                    sampler.fill(out, t, phase, 1.35f, 0.47f, height)
                    for (i in 0..steps) {
                        val x = i / steps.toFloat()
                        val expected = height * 0.47f + 1.35f * height * (
                            0.10f * sin(tau * (x * 0.85f + t * 0.022f) + phase) +
                                0.045f * sin(tau * (x * 1.9f - t * 0.035f) + phase * 1.7f) +
                                0.02f * sin(tau * (x * 3.1f + t * 0.05f) + phase * 2.3f)
                            )
                        // Float angle rearrangement stays below a quarter pixel even at 4K
                        // after an hour of animation; the wave geometry remains unchanged.
                        assertEquals("t=$t, point=$i", expected, out[i], 0.25f)
                    }
                }
            }
        }
    }

    @Test
    fun waveClockHonorsBatteryPreferenceAndDisplayRefresh() {
        assertEquals(30f, xmbEffectsFps(144f, BackgroundFrameRate.BATTERY_SAVER))
        assertEquals(60f, xmbEffectsFps(144f, BackgroundFrameRate.SMOOTH))
        assertEquals(30f, xmbEffectsFps(60f, BackgroundFrameRate.SMOOTH))
        assertEquals(30f, xmbEffectsFps(0f, BackgroundFrameRate.BATTERY_SAVER))
    }
}
