package dev.pluto.launcher.ui.theme

import kotlin.math.PI
import kotlin.math.sin
import kotlin.system.measureNanoTime
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host CPU sampling benchmark, excluding Canvas/GPU work; timings are diagnostics, not pass thresholds. */
class XmbWavePerformanceTest {
    @Test fun cachedSamplingBenchmark() {
        val steps = 56
        val sampler = XmbWaveSampler(steps)
        val out = FloatArray(steps + 1)
        val tau = 2f * PI.toFloat()
        var checksum = 0f
        fun run(cached: Boolean, frames: Int) {
            repeat(frames) { frame ->
                val t = frame / 60f
                repeat(25) { curve ->
                    val phase = curve * 0.13f
                    if (cached) sampler.fill(out, t, phase, 1.2f, 0.47f, 1080f)
                    else for (i in 0..steps) {
                        val x = i / steps.toFloat()
                        out[i] = 1080f * 0.47f + 1.2f * 1080f * (
                            0.10f * sin(tau * (x * 0.85f + t * 0.022f) + phase) +
                                0.045f * sin(tau * (x * 1.9f - t * 0.035f) + phase * 1.7f) +
                                0.02f * sin(tau * (x * 3.1f + t * 0.05f) + phase * 2.3f))
                    }
                    checksum += out[frame % out.size]
                }
            }
        }
        repeat(3) { run(false, 50); run(true, 50) }
        val previous = ArrayList<Long>()
        val cached = ArrayList<Long>()
        repeat(5) { round ->
            fun old() { previous += measureNanoTime { run(false, 200) } }
            fun new() { cached += measureNanoTime { run(true, 200) } }
            if (round % 2 == 0) { old(); new() } else { new(); old() }
        }
        assertTrue(checksum.isFinite())
        println("XMB_BENCHMARK: 200 frames, 25 curves, 57 samples per curve; 3 warmups, 5 alternating rounds")
        println("XMB_BENCHMARK: previous median=${previous.sorted()[2] / 1_000_000.0}ms; cached sampler median=${cached.sorted()[2] / 1_000_000.0}ms; excludes Canvas/GPU work")
    }
}
