package dev.pluto.launcher.ui.console

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
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
 * a small pre-faded mirror image for the reflection, and the icon's dominant colour for the
 * card face and the ambient backdrop glow. Built once per app off the main thread.
 */
class ConsoleArt(val icon: ImageBitmap, val reflection: ImageBitmap, val color: Int) {
    internal val bytes: Int get() = (icon.width * icon.height + reflection.width * reflection.height) * 4
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
        val source = icon.asAndroidBitmap()
        val color = dominantColor(source)
        val reflection = reflectionOf(source)
        // Uploaded to the GPU now (off the first draw of the card).
        reflection.prepareToDraw()
        return ConsoleArt(icon, reflection.asImageBitmap(), color)
    }

    /** Dominant colour from a 16 x 16 downscale (cheap; see [ConsoleColors.dominant]). */
    private fun dominantColor(source: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(source.toSoftware(), SAMPLE, SAMPLE, true)
        val pixels = IntArray(SAMPLE * SAMPLE)
        small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        if (small !== source) small.recycle()
        return ConsoleColors.dominant(pixels)
    }

    /**
     * The lower part of the icon mirrored, at half resolution (a soft reflection is nicer
     * anyway), with a top-to-bottom fade baked into its alpha so drawing it needs no mask
     * or offscreen layer.
     */
    private fun reflectionOf(source: Bitmap): Bitmap {
        val soft = source.toSoftware()
        val w = (soft.width / 2).coerceAtLeast(1)
        val h = (soft.height * REFLECTION_FRACTION / 2f).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = Matrix().apply {
            // Scale to half size and flip vertically around the bottom edge of the icon.
            setScale(0.5f, -0.5f)
            postTranslate(0f, soft.height / 2f)
        }
        canvas.drawBitmap(soft, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val fade = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x8C000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fade)
        return out
    }

    private fun Bitmap.toSoftware(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    private fun cacheKey(key: AppKey, sizePx: Int, version: Int) = "$sizePx|$version|${key.encode()}"

    private const val SAMPLE = 16
    private const val REFLECTION_FRACTION = 0.42f

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
