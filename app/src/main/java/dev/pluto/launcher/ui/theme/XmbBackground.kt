package dev.pluto.launcher.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.pluto.launcher.data.prefs.BackgroundChoice
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.data.prefs.XmbColor
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The XMB base colour in effect now (AUTO follows the month). */
fun LauncherSettings.xmbBaseColor(month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1): Color {
    val c = if (xmbColor == XmbColor.AUTO) XmbColor.forMonth(month) else xmbColor
    return Color(c.argb)
}

/** The background chosen for normal or console (handheld) mode. */
fun LauncherSettings.backgroundFor(console: Boolean): BackgroundChoice = if (console) consoleBackground else normalBackground

/**
 * A PS3 XMB-style animated background.
 *
 * Static layer (drawn once, then only composited): the vertical gradient in [base], a soft
 * light from the upper left and a vignette. Animated layer: two slowly wandering pools of
 * light, a translucent band, two ribbon bundles moving out of phase (a bright main one with
 * a glowing crest line and a fainter echo), and sparkles drifting along the wave.
 *
 * Everything moves in the draw phase from one time value (nothing recomposes); the clock
 * runs only while the launcher is started, and with [animate] false (Reduce motion) the
 * background is a still frame.
 */
@Composable
fun XmbBackground(base: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(XMB_STILL_TIME) }
    val lifecycle = LocalLifecycleOwner.current
    if (animate) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val start = withFrameNanos { it } - (time.floatValue * 1e9f).toLong()
                // The waves move slowly: redrawing every other frame (72 fps on a 144 Hz
                // screen) looks the same and halves the GPU work.
                var frame = 0
                while (true) {
                    withFrameNanos { now ->
                        if (frame++ % XMB_FRAME_DIVISOR == 0) time.floatValue = (now - start) / 1e9f
                    }
                }
            }
        }
    }
    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val background = Brush.verticalGradient(
                        0f to lerp(base, Color.White, 0.08f),
                        0.45f to lerp(base, Color.Black, 0.10f),
                        1f to lerp(base, Color.Black, 0.62f),
                    )
                    val light = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                        center = Offset(w * 0.15f, h * 0.08f),
                        radius = maxOf(w, h) * 0.8f,
                    )
                    val vignette = Brush.radialGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.38f),
                        center = Offset(w / 2f, h * 0.45f),
                        radius = maxOf(w, h) * 0.72f,
                    )
                    onDrawBehind {
                        drawRect(background)
                        drawRect(light)
                        drawRect(vignette)
                    }
                },
        )
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val d = density
                    val glowA = lerp(base, Color.White, 0.55f)
                    val glowB = lerp(base, Color.White, 0.35f)
                    val poolRadius = maxOf(w, h) * 0.42f
                    val bandBrush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.35f to Color.White.copy(alpha = 0.13f),
                        1f to Color.White.copy(alpha = 0f),
                        startY = h * (WAVE_Y - 0.18f),
                        endY = h * (WAVE_Y + 0.3f),
                    )
                    val path = Path()
                    val band = Path()
                    val thin = Stroke(width = 1.1f * d, cap = StrokeCap.Round)
                    val crest = Stroke(width = 2.2f * d, cap = StrokeCap.Round)
                    val crestGlow = Stroke(width = 12f * d, cap = StrokeCap.Round)
                    val steps = XMB_STEPS
                    val ys = FloatArray(steps + 1)
                    val ys2 = FloatArray(steps + 1)
                    val sparkles = Sparkles.create(XMB_SPARKLES)

                    // One ribbon line: three slow sine terms, partly travelling in opposite
                    // directions, so the bundle breathes and twists rather than just sliding.
                    fun wave(out: FloatArray, t: Float, phase: Float, amp: Float, centre: Float) {
                        val tau = 2f * PI.toFloat()
                        for (i in 0..steps) {
                            val x = i / steps.toFloat()
                            out[i] = h * centre + amp * h * (
                                0.10f * sin(tau * (x * 0.85f + t * 0.022f) + phase) +
                                    0.045f * sin(tau * (x * 1.9f - t * 0.035f) + phase * 1.7f) +
                                    0.02f * sin(tau * (x * 3.1f + t * 0.05f) + phase * 2.3f)
                                )
                        }
                    }
                    fun line(out: FloatArray): Path {
                        path.reset()
                        path.moveTo(0f, out[0])
                        for (i in 1..steps) path.lineTo(w * i / steps, out[i])
                        return path
                    }
                    fun waveAt(x: Float, t: Float, phase: Float, amp: Float, centre: Float): Float {
                        val tau = 2f * PI.toFloat()
                        return h * centre + amp * h * (
                            0.10f * sin(tau * (x * 0.85f + t * 0.022f) + phase) +
                                0.045f * sin(tau * (x * 1.9f - t * 0.035f) + phase * 1.7f) +
                                0.02f * sin(tau * (x * 3.1f + t * 0.05f) + phase * 2.3f)
                            )
                    }

                    onDrawBehind {
                        val t = time.floatValue

                        // Two pools of light wandering slowly across the gradient.
                        val p1 = Offset(w * (0.3f + 0.2f * sin(t * 0.05f)), h * (0.3f + 0.12f * cos(t * 0.04f)))
                        val p2 = Offset(w * (0.72f + 0.18f * cos(t * 0.035f)), h * (0.62f + 0.1f * sin(t * 0.045f)))
                        drawCircle(
                            Brush.radialGradient(0f to glowA.copy(alpha = 0.16f), 1f to Color.Transparent, center = p1, radius = poolRadius),
                            radius = poolRadius, center = p1, blendMode = BlendMode.Screen,
                        )
                        drawCircle(
                            Brush.radialGradient(0f to glowB.copy(alpha = 0.12f), 1f to Color.Transparent, center = p2, radius = poolRadius * 0.8f),
                            radius = poolRadius * 0.8f, center = p2, blendMode = BlendMode.Screen,
                        )

                        // The translucent band under the main bundle.
                        wave(ys, t, 0f, 1f, WAVE_Y)
                        wave(ys2, t, 1.0f, 1.35f, WAVE_Y + 0.06f)
                        band.reset()
                        band.moveTo(0f, ys[0])
                        for (i in 1..steps) band.lineTo(w * i / steps, ys[i])
                        for (i in steps downTo 0) band.lineTo(w * i / steps, ys2[i])
                        band.close()
                        drawPath(band, bandBrush)

                        // Echo bundle: fainter, out of phase, a little lower.
                        for (k in 0 until XMB_ECHO_LINES) {
                            val f = k / (XMB_ECHO_LINES - 1f)
                            wave(ys, t * 0.8f + 40f, 2.4f + f * 0.9f, 0.7f + f * 0.4f, WAVE_Y + 0.08f)
                            drawPath(line(ys), Color.White.copy(alpha = 0.05f + 0.06f * (1f - abs(f - 0.5f) * 2f)), style = thin)
                        }
                        // Main bundle: lines fanning out from a bright crest.
                        for (k in 0 until XMB_LINES) {
                            val f = k / (XMB_LINES - 1f)
                            wave(ys, t, f * 1.2f, 0.82f + f * 0.55f, WAVE_Y)
                            val alpha = 0.08f + 0.20f * (1f - abs(f - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            drawPath(line(ys), Color.White.copy(alpha = alpha), style = thin)
                        }
                        wave(ys, t, 0.42f, 1.01f, WAVE_Y)
                        val crestPath = line(ys)
                        drawPath(crestPath, Color.White.copy(alpha = 0.07f), style = crestGlow, blendMode = BlendMode.Screen)
                        drawPath(crestPath, Color.White.copy(alpha = 0.45f), style = crest)

                        // Sparkles drifting along the wave, twinkling.
                        for (s in sparkles) {
                            val x = ((s.x + t * s.speed) % 1f + 1f) % 1f
                            val y = waveAt(x, t, 0.42f + s.lane, 1.0f, WAVE_Y) + s.offset * h
                            val twinkle = 0.5f + 0.5f * sin(t * s.twinkle + s.seed)
                            val a = s.alpha * twinkle * edgeFade(x)
                            if (a < 0.02f) continue
                            val c = Offset(x * w, y)
                            // A pinpoint with a faint, tight halo (bigger halos read as bubbles).
                            drawCircle(Color.White.copy(alpha = a * 0.14f), radius = s.size * d * 2f, center = c, blendMode = BlendMode.Screen)
                            drawCircle(Color.White.copy(alpha = a), radius = s.size * d * 0.6f, center = c)
                        }
                    }
                },
        )
    }
}

/** Sparkles fade in and out at the screen edges instead of popping. */
private fun edgeFade(x: Float): Float = (minOf(x, 1f - x) / 0.08f).coerceIn(0f, 1f)

/** Fixed, pseudo-random sparkle parameters (deterministic, so every launch looks alike). */
private class Sparkles(
    val x: Float,
    val speed: Float,
    val lane: Float,
    val offset: Float,
    val size: Float,
    val alpha: Float,
    val twinkle: Float,
    val seed: Float,
) {
    companion object {
        fun create(count: Int): List<Sparkles> {
            var state = 0x2F6E2B1
            fun next(): Float {
                state = state * 1103515245 + 12345
                return ((state ushr 8) and 0xFFFF) / 65535f
            }
            return List(count) {
                Sparkles(
                    x = next(),
                    speed = 0.004f + next() * 0.012f,
                    lane = (next() - 0.5f) * 2.5f,
                    offset = (next() - 0.5f) * 0.18f,
                    size = 0.7f + next() * 1.1f,
                    alpha = 0.35f + next() * 0.55f,
                    twinkle = 0.6f + next() * 1.8f,
                    seed = next() * 6.28f,
                )
            }
        }
    }
}

/** Redraw the moving layer on every n-th display frame. */
private const val XMB_FRAME_DIVISOR = 2

/** Time shown when animation is off: a pleasant, settled frame. */
private const val XMB_STILL_TIME = 37f
/** Vertical centre of the wave, as a fraction of the height: behind the cards, above titles. */
private const val WAVE_Y = 0.47f
private const val XMB_STEPS = 56
private const val XMB_LINES = 14
private const val XMB_ECHO_LINES = 8
private const val XMB_SPARKLES = 50
