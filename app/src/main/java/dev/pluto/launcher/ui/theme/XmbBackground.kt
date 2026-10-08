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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.pluto.launcher.data.prefs.BackgroundStyle
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.data.prefs.XmbColor
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

/** The XMB base colour in effect now (AUTO follows the month). */
fun LauncherSettings.xmbBaseColor(month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1): Color {
    val c = if (xmbColor == XmbColor.AUTO) XmbColor.forMonth(month) else xmbColor
    return Color(c.argb)
}

/** True when the XMB background replaces the wallpaper in this place. */
fun LauncherSettings.xmbIn(console: Boolean): Boolean = when (backgroundStyle) {
    BackgroundStyle.WALLPAPER -> false
    BackgroundStyle.XMB_CONSOLE -> console
    BackgroundStyle.XMB_EVERYWHERE -> true
}

/**
 * A PS3 XMB-style animated background: a vertical gradient in [base] with a soft light
 * from the upper left, and a bundle of thin translucent ribbons flowing across the middle.
 *
 * Everything moves in the draw phase from one time value (nothing recomposes), inside its
 * own layer so only this layer redraws each frame. The clock runs only while the launcher is
 * started; with [animate] false (Reduce motion) it is a still frame.
 */
@Composable
fun XmbBackground(base: Color, animate: Boolean, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current
    if (animate) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val start = withFrameNanos { it } - (time.floatValue * 1e9f).toLong()
                while (true) withFrameNanos { now -> time.floatValue = (now - start) / 1e9f }
            }
        }
    }
    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        // Static part, in its own layer: drawn once, then only composited.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    // Slightly lighter at the top like the XMB, but dark enough for white text.
                    val top = lerp(base, Color.White, 0.06f)
                    val bottom = lerp(base, Color.Black, 0.6f)
                    val background = Brush.verticalGradient(0f to top, 0.4f to lerp(base, Color.Black, 0.12f), 1f to bottom)
                    val light = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        1f to Color.Transparent,
                        center = Offset(w * 0.18f, h * 0.12f),
                        radius = maxOf(w, h) * 0.75f,
                    )
                    onDrawBehind {
                        drawRect(background)
                        drawRect(light)
                    }
                },
        )
        // Moving part: only the soft band and the thin ribbon lines redraw each frame.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer { }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val bandBrush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.09f),
                        1f to Color.White.copy(alpha = 0f),
                        startY = h * 0.35f,
                        endY = h * 0.85f,
                    )
                    val path = Path()
                    val band = Path()
                    val stroke = Stroke(width = 1.2f * density)
                    val steps = XMB_STEPS
                    val ys = FloatArray(steps + 1)
                    val ys2 = FloatArray(steps + 1)

                    // One ribbon line: two slow sine waves travelling in opposite directions.
                    fun wave(out: FloatArray, t: Float, phase: Float, amp: Float) {
                        for (i in 0..steps) {
                            val x = i / steps.toFloat()
                            out[i] = h * 0.58f +
                                amp * h * (
                                    0.11f * sin(2f * PI.toFloat() * (x * 0.9f + t * 0.025f) + phase) +
                                        0.05f * sin(2f * PI.toFloat() * (x * 1.7f - t * 0.04f) + phase * 1.9f)
                                    )
                        }
                    }
                    fun line(out: FloatArray): Path {
                        path.reset()
                        path.moveTo(0f, out[0])
                        for (i in 1..steps) path.lineTo(w * i / steps, out[i])
                        return path
                    }

                    onDrawBehind {
                        val t = time.floatValue
                        wave(ys, t, 0f, 1f)
                        wave(ys2, t, 0.9f, 1.25f)
                        band.reset()
                        band.moveTo(0f, ys[0])
                        for (i in 1..steps) band.lineTo(w * i / steps, ys[i])
                        for (i in steps downTo 0) band.lineTo(w * i / steps, ys2[i])
                        band.close()
                        drawPath(band, bandBrush)
                        for (k in 0 until XMB_LINES) {
                            val f = k / (XMB_LINES - 1f)
                            wave(ys, t, f * 1.1f, 0.85f + f * 0.5f)
                            val alpha = 0.10f + 0.16f * (1f - kotlin.math.abs(f - 0.5f) * 2f)
                            drawPath(line(ys), Color.White.copy(alpha = alpha), style = stroke)
                        }
                    }
                },
        )
    }
}

private const val XMB_STEPS = 48
private const val XMB_LINES = 9
