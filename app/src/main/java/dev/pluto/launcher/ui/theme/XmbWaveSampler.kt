package dev.pluto.launcher.ui.theme

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Cached spatial terms of the three sine waves; no allocation in the frame loop. */
internal class XmbWaveSampler(private val steps: Int) {
    private val tau = 2f * PI.toFloat()
    private val sinA = FloatArray(steps + 1) { sin(tau * (it / steps.toFloat() * 0.85f)) }
    private val cosA = FloatArray(steps + 1) { cos(tau * (it / steps.toFloat() * 0.85f)) }
    private val sinB = FloatArray(steps + 1) { sin(tau * (it / steps.toFloat() * 1.9f)) }
    private val cosB = FloatArray(steps + 1) { cos(tau * (it / steps.toFloat() * 1.9f)) }
    private val sinC = FloatArray(steps + 1) { sin(tau * (it / steps.toFloat() * 3.1f)) }
    private val cosC = FloatArray(steps + 1) { cos(tau * (it / steps.toFloat() * 3.1f)) }

    fun fill(out: FloatArray, t: Float, phase: Float, amp: Float, centre: Float, height: Float) {
        val a = tau * t * 0.022f + phase
        val b = -tau * t * 0.035f + phase * 1.7f
        val c = tau * t * 0.05f + phase * 2.3f
        val sa = sin(a)
        val ca = cos(a)
        val sb = sin(b)
        val cb = cos(b)
        val sc = sin(c)
        val cc = cos(c)
        val base = height * centre
        val amplitude = amp * height
        for (i in 0..steps) {
            out[i] = base + amplitude * (
                0.10f * (sinA[i] * ca + cosA[i] * sa) +
                    0.045f * (sinB[i] * cb + cosB[i] * sb) +
                    0.02f * (sinC[i] * cc + cosC[i] * sc)
                )
        }
    }
}
