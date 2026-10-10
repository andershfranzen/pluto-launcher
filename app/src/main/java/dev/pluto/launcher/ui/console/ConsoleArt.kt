package dev.pluto.launcher.ui.console

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.model.AppKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What a Coverflow card draws for one app: the icon (shared with [IconCache], not copied),
 * the icon's part of the card's reflection ([reflection]: the visible strip of the icon,
 * mirrored and already faded, see [CardLook]) and its dominant colour for the ambient
 * backdrop glow. Built once per app off the main thread, so a card's reflection is one plain
 * bitmap draw (no offscreen layer per card per frame).
 */
class ConsoleArt(val icon: ImageBitmap, val reflection: ImageBitmap?, val color: Int) {
    internal val bytes: Int get() = icon.width * icon.height * 4 + (reflection?.let { it.width * it.height * 4 } ?: 0)
}

/**
 * Proportions of a Coverflow card (square) and its reflection, as fractions of the card side.
 * The reflection is the whole card face mirrored below a small gap, fading smoothly (eased,
 * no hard edge) from [REFLECTION_ALPHA] at the card's edge to nothing at [REFLECTION_DEPTH].
 */
internal object CardLook {
    /** Icon inset on every side: the icon is centred and the dark-glass face shows around it. */
    const val ICON_INSET = 0.18f
    const val ICON_SIDE = 1f - 2f * ICON_INSET
    const val REFLECTION_GAP = 0.035f
    // A short, faint mirror: on a black sky a deep one read as a dark smudge under the card.
    const val REFLECTION_DEPTH = 0.22f
    const val REFLECTION_ALPHA = 0.16f

    /** Reflection opacity [depth] below the card's bottom edge (card fractions): eased to zero. */
    fun fade(depth: Float): Float {
        val t = (1f - depth / REFLECTION_DEPTH).coerceIn(0f, 1f)
        return REFLECTION_ALPHA * t * t
    }

    /** How much of the icon's height shows in the reflection before it has faded out. */
    const val ICON_REFLECTED = (REFLECTION_DEPTH - ICON_INSET) / ICON_SIDE

    /** Gradient stops (position in the reflected strip 0..1, alpha) approximating [fade] over the icon strip. */
    fun iconFadeStops(steps: Int = 6): Pair<FloatArray, FloatArray> {
        val positions = FloatArray(steps + 1) { it / steps.toFloat() }
        val alphas = FloatArray(steps + 1) { i -> fade(ICON_INSET + positions[i] * ICON_REFLECTED * ICON_SIDE) }
        return positions to alphas
    }
}

/**
 * Process-wide cache of [ConsoleArt], keyed by app, size and the icon cache's package
 * version (an app update brings new art). Sized in bytes like [IconCache].
 */
object ConsoleArtCache {
    private val cache = object : LruCache<String, ConsoleArt>(cacheBytes()) {
        override fun sizeOf(key: String, value: ConsoleArt): Int = value.bytes
    }

    /** Cheap memory lookup; safe on the main thread and inside draw. */
    fun peek(key: AppKey, sizePx: Int, version: Int): ConsoleArt? = cache.get(cacheKey(key, sizePx, version))

    /** Loads the icon (IconCache, off main) and builds the art on Dispatchers.Default. Null if the app has no icon. */
    suspend fun load(icons: IconCache, key: AppKey, sizePx: Int, version: Int): ConsoleArt? {
        val cacheKey = cacheKey(key, sizePx, version)
        cache.get(cacheKey)?.let { return it }
        val icon = try {
            icons.load(key, sizePx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return withContext(Dispatchers.Default) {
            val art = build(icon)
            cache.put(cacheKey, art)
            art
        }
    }

    private fun build(icon: ImageBitmap): ConsoleArt {
        val source = icon.asAndroidBitmap().toSoftware()
        return ConsoleArt(icon, reflectionOf(source)?.asImageBitmap(), dominantColor(source))
    }

    /**
     * The icon's strip of the reflection: its bottom [CardLook.ICON_REFLECTED] mirrored, with
     * the card's fade baked in (DST_IN gradient), so drawing it needs no layer.
     */
    private fun reflectionOf(source: Bitmap): Bitmap? {
        val w = source.width
        val h = (source.height * CardLook.ICON_REFLECTED).toInt()
        if (w <= 0 || h <= 0) return null
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.save()
        // Mirror: the icon's bottom row lands on the strip's top row.
        canvas.scale(1f, -1f)
        canvas.drawBitmap(source, 0f, -source.height.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        val (positions, alphas) = CardLook.iconFadeStops()
        val colors = IntArray(alphas.size) { ((alphas[it] * 255f + 0.5f).toInt().coerceIn(0, 255)) shl 24 }
        val mask = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), colors, positions, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), mask)
        return out
    }

    /** Dominant colour from a 16 x 16 downscale (cheap; see [ConsoleColors.dominant]). */
    private fun dominantColor(source: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(source, SAMPLE, SAMPLE, true)
        val pixels = IntArray(SAMPLE * SAMPLE)
        small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        if (small !== source) small.recycle()
        return ConsoleColors.dominant(pixels)
    }

    private fun Bitmap.toSoftware(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    private fun cacheKey(key: AppKey, sizePx: Int, version: Int) = "$sizePx|$version|${key.encode()}"

    private const val SAMPLE = 16

    private fun cacheBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 12).coerceIn(4L * 1024 * 1024, 48L * 1024 * 1024).toInt()
}

/** Pure colour helpers (Android-free, unit tested). Colours are ARGB ints. */
object ConsoleColors {
    /**
     * A representative "brand" colour for an icon: the average of its opaque, reasonably
     * saturated pixels weighted by saturation, so a coloured logo on a white plate wins over
     * the plate. Falls back to the plain average of opaque pixels for monochrome icons, and
     * to a neutral grey for an empty image.
     */
    fun dominant(pixels: IntArray): Int {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var weight = 0.0
        var plainR = 0.0
        var plainG = 0.0
        var plainB = 0.0
        var plainCount = 0
        for (p in pixels) {
            val a = (p ushr 24) and 0xFF
            if (a < 128) continue
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            plainR += pr
            plainG += pg
            plainB += pb
            plainCount++
            val max = maxOf(pr, pg, pb)
            val min = minOf(pr, pg, pb)
            if (max < 24) continue
            val saturation = (max - min).toDouble() / max
            if (saturation < 0.18) continue
            val w = saturation * saturation
            r += pr * w
            g += pg * w
            b += pb * w
            weight += w
        }
        return when {
            weight > 0.0 -> argb(r / weight, g / weight, b / weight)
            plainCount > 0 -> argb(plainR / plainCount, plainG / plainCount, plainB / plainCount)
            else -> NEUTRAL
        }
    }

    /** [color] scaled toward black by [factor] (0 = black, 1 = unchanged), opaque. */
    fun darken(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        return argb(((color shr 16) and 0xFF) * f.toDouble(), ((color shr 8) and 0xFF) * f.toDouble(), (color and 0xFF) * f.toDouble())
    }

    const val NEUTRAL: Int = 0xFF6E7681.toInt()

    private fun argb(r: Double, g: Double, b: Double): Int =
        (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)

    private fun channel(v: Double): Int = (v + 0.5).toInt().coerceIn(0, 255)
}
