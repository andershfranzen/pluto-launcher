package dev.pluto.launcher.ui.theme

import dev.pluto.launcher.data.prefs.BackgroundFrameRate
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pluto over black space: the New Horizons global colour map (NASA/JHUAPL/SwRI, PIA11707)
 * wrapped on a sphere by an AGSL shader, lit from the upper left with a soft terminator and
 * a blue haze at the limb like Pluto's real atmosphere. The globe holds still with the
 * heart (Tombaugh Regio) facing the screen: the side New Horizons saw sharply. The far side
 * was only seen from afar (and the south not at all), so it never turns into view.
 *
 * Around it: true black (OLED pixels stay off) with very faint nebulae (static layer, drawn
 * once), a star field with a twinkling subset, slowly drifting dust and an occasional
 * shooting star, all behind the globe. They move in the draw phase from one time value,
 * updated at [rate]; the clock runs only while the launcher is started, and with [animate]
 * false (Reduce motion) it is a still frame. Before Android 13 (no runtime shaders) the
 * globe is left out and the space scene remains.
 */
@Composable
fun PlutoBackground(rate: BackgroundFrameRate, animate: Boolean, modifier: Modifier = Modifier) {
    // One clock for the twinkles, dust and shooting stars; the globe is drawn once.
    val fxTime = remember { mutableFloatStateOf(PLUTO_STILL_TIME) }
    val lifecycle = LocalLifecycleOwner.current
    if (animate) {
        LaunchedEffect(lifecycle, rate) {
            val fxNanos = 1_000_000_000L / rate.effectsFps
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val start = withFrameNanos { it } - (fxTime.floatValue * 1e9f).toLong()
                var next = System.nanoTime()
                while (true) {
                    withFrameNanos { now -> fxTime.floatValue = (now - start) / 1e9f }
                    // Sleep until the next update is due, so every frame requested here is one
                    // update. Due times follow each other (not the frame that took them), so the
                    // average rate holds on any refresh rate; after a stall it restarts from now.
                    next += fxNanos
                    val nowNanos = System.nanoTime()
                    if (nowNanos - next > fxNanos) next = nowNanos
                    val waitMs = (next - nowNanos) / 1_000_000L
                    if (waitMs > 0) delay(waitMs)
                }
            }
        }
    }
    val globe = rememberGlobeShader()
    val stars = remember { Starfield.create(Random(1930)) }
    // Cached as a texture: redrawn only when its own content changes, not on every frame.
    val cached = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }

    Box(modifier.fillMaxSize().clearAndSetSemantics { }) {
        // Static: black space, faint nebulae and the steady stars.
        Spacer(
            cached.drawWithCache {
                val w = size.width
                val h = size.height
                val nebulaA = Brush.radialGradient(
                    0f to Color(0xFF4A2F7A).copy(alpha = 0.06f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.18f, h * 0.22f),
                    radius = max(w, h) * 0.55f,
                )
                val nebulaB = Brush.radialGradient(
                    0f to Color(0xFF6E3A78).copy(alpha = 0.04f),
                    1f to Color.Transparent,
                    center = Offset(w * 0.92f, h * 0.48f),
                    radius = max(w, h) * 0.45f,
                )
                val placement = GlobePlacement.of(size)
                val glow = Brush.radialGradient(
                    0f to Color(0xFF7A6CFF).copy(alpha = 0.05f),
                    1f to Color.Transparent,
                    center = placement.center,
                    radius = placement.radius * 2.1f,
                )
                onDrawBehind {
                    drawRect(Color.Black)
                    drawRect(nebulaA)
                    drawRect(nebulaB)
                    stars.drawSteady(this)
                    drawCircle(glow, radius = placement.radius * 2.1f, center = placement.center)
                }
            },
        )
        // Everything that moves, behind the globe: twinkles, dust and shooting stars.
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = fxTime.floatValue
                stars.drawTwinkling(this, t)
                stars.drawDust(this, t, density)
                stars.drawShootingStar(this, t, density)
            },
        )
        // The globe: its own cached layer, rendered once (it holds still) and then only composited.
        if (globe != null) {
            Spacer(cached.drawBehind { globe.draw(this, GlobePlacement.of(size)) })
        }
    }
}

/** Where the globe sits: centred in portrait, to the right in landscape. */
private class GlobePlacement(val center: Offset, val radius: Float) {
    companion object {
        fun of(size: Size): GlobePlacement = if (size.height >= size.width) {
            GlobePlacement(Offset(size.width * 0.5f, size.height * 0.5f), size.width * 0.42f)
        } else {
            GlobePlacement(Offset(size.width * 0.7f, size.height * 0.55f), size.height * 0.38f)
        }
    }
}

/** The globe shader with its map loaded, or null before Android 13 / while loading / on failure. */
@Composable
private fun rememberGlobeShader(): GlobeShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val context = LocalContext.current.applicationContext
    val shader by produceState<GlobeShader?>(null, context) {
        value = withContext(Dispatchers.IO) {
            try {
                // The visible half of the map spans the globe (about 0.84 of the short side):
                // decode at about twice that width, not the full 3072 px (19 MB) everywhere.
                val metrics = context.resources.displayMetrics
                val needed = 2f * GLOBE_SHORT_SIDE * minOf(metrics.widthPixels, metrics.heightPixels)
                var sample = 1
                while (PLUTO_MAP_WIDTH / (sample * 2) >= needed) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = context.assets.open(PLUTO_MAP_ASSET).use { BitmapFactory.decodeStream(it, null, options) }
                bitmap?.let { GlobeShader(it) }
            } catch (e: Exception) {
                Log.w("PlutoBackground", "Couldn't load the Pluto map", e)
                null
            }
        }
    }
    return shader
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class GlobeShader(bitmap: android.graphics.Bitmap) {
    private val shader = RuntimeShader(GLOBE_AGSL)
    private val brush = ShaderBrush(shader)

    init {
        bitmap.setHasMipMap(true)
        bitmap.prepareToDraw()
        val map = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        map.filterMode = BitmapShader.FILTER_MODE_LINEAR
        shader.setInputShader("map", map)
        shader.setFloatUniform("mapSize", bitmap.width.toFloat(), bitmap.height.toFloat())
        shader.setFloatUniform("tilt", GLOBE_TILT)
        shader.setFloatUniform("light", -0.62f, 0.42f, 0.66f)
    }

    fun draw(scope: DrawScope, placement: GlobePlacement) {
        val c = placement.center
        val r = placement.radius
        shader.setFloatUniform("center", c.x, c.y)
        shader.setFloatUniform("radius", r)
        shader.setFloatUniform("spin", GLOBE_SPIN)
        // Only the globe and its haze: the shader never runs over the whole screen.
        val extent = r * HAZE_EXTENT
        scope.drawRect(brush, topLeft = Offset(c.x - extent, c.y - extent), size = Size(extent * 2f, extent * 2f))
    }
}

/** Stars, dust and shooting stars, all from one seeded random source (the sky is the same every launch). */
private class Starfield(
    private val steady: FloatArray,
    private val twinkling: FloatArray,
    private val dust: FloatArray,
) {
    // Each star: x, y (0..1), radius (px at 1x), brightness, [phase, speed for twinkling].

    fun drawSteady(scope: DrawScope) = with(scope) {
        for (i in steady.indices step 4) {
            drawCircle(
                StarColor.copy(alpha = steady[i + 3]),
                radius = steady[i + 2] * density,
                center = Offset(steady[i] * size.width, steady[i + 1] * size.height),
            )
        }
    }

    fun drawTwinkling(scope: DrawScope, t: Float) = with(scope) {
        for (i in twinkling.indices step 6) {
            val pulse = 0.5f + 0.5f * sin(t * twinkling[i + 5] + twinkling[i + 4])
            val a = twinkling[i + 3] * (0.25f + 0.75f * pulse)
            val center = Offset(twinkling[i] * size.width, twinkling[i + 1] * size.height)
            val r = twinkling[i + 2] * density
            drawCircle(StarGlow.copy(alpha = a * 0.16f), radius = r * 2.2f, center = center)
            drawCircle(StarColor.copy(alpha = a), radius = r, center = center)
        }
    }

    /** Dust: x, y, radius, alpha, drift speed, phase. Drifts up and slightly right, wrapping. */
    fun drawDust(scope: DrawScope, t: Float, density: Float) = with(scope) {
        for (i in dust.indices step 6) {
            val speed = dust[i + 4]
            val y = wrap(dust[i + 1] - t * speed * 0.012f)
            val x = wrap(dust[i] + t * speed * 0.004f + 0.01f * sin(t * 0.3f + dust[i + 5]))
            val fade = 0.5f + 0.5f * sin(t * 0.7f * speed + dust[i + 5])
            // Fade out near the top and bottom edges so wrapping never pops.
            val edge = min(1f, min(y, 1f - y) * 8f)
            val a = dust[i + 3] * fade * edge
            val center = Offset(x * size.width, y * size.height)
            val r = dust[i + 2] * density
            drawCircle(DustColor.copy(alpha = a * 0.18f), radius = r * 2.4f, center = center, blendMode = BlendMode.Plus)
            drawCircle(DustColor.copy(alpha = a), radius = r, center = center, blendMode = BlendMode.Plus)
        }
    }

    /** A shooting star every [SHOOTING_PERIOD] seconds, at a place derived from its number. */
    fun drawShootingStar(scope: DrawScope, t: Float, density: Float) = with(scope) {
        val n = floor(t / SHOOTING_PERIOD)
        val phase = (t - n * SHOOTING_PERIOD) / SHOOTING_DURATION
        if (phase !in 0f..1f) return@with
        val rnd = Random(n.toInt() * 7919 + 17)
        val start = Offset(size.width * (0.15f + 0.75f * rnd.nextFloat()), size.height * (0.05f + 0.35f * rnd.nextFloat()))
        val dir = Offset(-0.82f, 0.57f)
        val travel = size.width * 0.45f
        val head = start + dir * (travel * phase)
        val tail = head - dir * (size.width * 0.16f * (1f - phase * 0.5f))
        val a = sin(phase * PI.toFloat())
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * a)), start = tail, end = head),
            start = tail,
            end = head,
            strokeWidth = 1.6f * density,
            cap = StrokeCap.Round,
        )
        drawCircle(Color.White.copy(alpha = 0.9f * a), radius = 1.6f * density, center = head)
    }

    companion object {
        fun create(rnd: Random): Starfield {
            val steady = FloatArray(STEADY_STARS * 4)
            for (i in 0 until STEADY_STARS) {
                steady[i * 4] = rnd.nextFloat()
                steady[i * 4 + 1] = rnd.nextFloat()
                steady[i * 4 + 2] = 0.35f + 0.6f * rnd.nextFloat() * rnd.nextFloat()
                steady[i * 4 + 3] = 0.25f + 0.5f * rnd.nextFloat()
            }
            val twinkling = FloatArray(TWINKLING_STARS * 6)
            for (i in 0 until TWINKLING_STARS) {
                twinkling[i * 6] = rnd.nextFloat()
                twinkling[i * 6 + 1] = rnd.nextFloat()
                twinkling[i * 6 + 2] = 0.5f + 0.6f * rnd.nextFloat()
                twinkling[i * 6 + 3] = 0.6f + 0.4f * rnd.nextFloat()
                twinkling[i * 6 + 4] = rnd.nextFloat() * 2f * PI.toFloat()
                twinkling[i * 6 + 5] = 0.6f + 1.8f * rnd.nextFloat()
            }
            val dust = FloatArray(DUST_PARTICLES * 6)
            for (i in 0 until DUST_PARTICLES) {
                dust[i * 6] = rnd.nextFloat()
                dust[i * 6 + 1] = rnd.nextFloat()
                dust[i * 6 + 2] = 0.6f + 1.4f * rnd.nextFloat() * rnd.nextFloat()
                dust[i * 6 + 3] = 0.15f + 0.45f * rnd.nextFloat()
                dust[i * 6 + 4] = 0.4f + 1.2f * rnd.nextFloat()
                dust[i * 6 + 5] = rnd.nextFloat() * 2f * PI.toFloat()
            }
            return Starfield(steady, twinkling, dust)
        }

        private fun wrap(v: Float): Float = v - floor(v)
    }
}

private val StarColor = Color(0xFFF2F4FF)
private val StarGlow = Color(0xFF9DB4FF)
private val DustColor = Color(0xFFC9D6FF)

private const val PLUTO_MAP_ASSET = "pluto_map.jpg"
private const val PLUTO_MAP_WIDTH = 3072

/** The globe's diameter as a share of the screen's short side (portrait placement). */
private const val GLOBE_SHORT_SIDE = 0.84f
private const val STEADY_STARS = 260
private const val TWINKLING_STARS = 40
private const val DUST_PARTICLES = 46
private const val SHOOTING_PERIOD = 11f
private const val SHOOTING_DURATION = 0.9f
private const val HAZE_EXTENT = 1.12f

/** North pole tipped towards the viewer (radians): the well-imaged north faces the screen. */
private const val GLOBE_TILT = 0.38f


/** The still frame's effects time for Reduce motion. */
private const val PLUTO_STILL_TIME = 2f

/** Longitude turned to face the screen (radians): the heart (Tombaugh Regio), sharply imaged. */
private const val GLOBE_SPIN = 0.0785f

/**
 * Sphere mapping of an equirectangular map. The view normal n is turned into body
 * coordinates (tilt about x), then latitude/longitude pick the texel.
 *
 * Visual assumptions on top of the data: relief from the map's brightness (bright ice and
 * dark tholins read as height, bump-mapping the normal along east/north), a fine fractal
 * grain fixed to the surface so the far side (seen only from afar by New Horizons, hence
 * blurry) still has texture, a faint glint on bright nitrogen ice, and layered haze, as in
 * the New Horizons departure images. Lighting: soft terminator and gentle limb darkening;
 * outside the disc the haze fades out over a thin shell.
 */
private const val GLOBE_AGSL = """
uniform shader map;
uniform float2 mapSize;
uniform float2 center;
uniform float radius;
uniform float spin;
uniform float tilt;
uniform float3 light;

const half3 HAZE = half3(0.45, 0.64, 1.0);

float luma(half3 c) { return dot(float3(c), float3(0.299, 0.587, 0.114)); }

half3 sampleMap(float u, float v) {
    return map.eval(float2(fract(u) * mapSize.x, clamp(v, 0.001, 0.999) * mapSize.y)).rgb;
}

float hash(float3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(float3 x) {
    float3 i = floor(x);
    float3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash(i), hash(i + float3(1, 0, 0)), f.x), mix(hash(i + float3(0, 1, 0)), hash(i + float3(1, 1, 0)), f.x), f.y),
        mix(mix(hash(i + float3(0, 0, 1)), hash(i + float3(1, 0, 1)), f.x), mix(hash(i + float3(0, 1, 1)), hash(i + float3(1, 1, 1)), f.x), f.y),
        f.z);
}

float grain(float3 q) { return 0.6 * vnoise(q * 36.0) + 0.4 * vnoise(q * 90.0); }

half4 main(float2 p) {
    float2 d = (p - center) / radius;
    float r2 = dot(d, d);
    float r = sqrt(r2);
    float3 L = normalize(light);
    if (r2 > 1.0) {
        float h = r - 1.0;
        float bands = 0.82 + 0.18 * sin(h * radius * 0.55);
        float glow = (exp(-h * 42.0) * 0.75 + exp(-h * 11.0) * 0.18) * bands * (1.0 - smoothstep(0.04, 0.11, h));
        float2 dir = d / r;
        float lit = clamp(dot(float3(dir.x, -dir.y, 0.0), L) * 0.9 + 0.3, 0.0, 1.0);
        float a = clamp(glow * lit, 0.0, 1.0);
        return half4(HAZE * a, a);
    }
    float z = sqrt(1.0 - r2);
    float3 n = float3(d.x, -d.y, z);
    float ct = cos(tilt);
    float st = sin(tilt);
    float3 b = float3(n.x, n.y * ct + n.z * st, n.z * ct - n.y * st);
    float lat = asin(clamp(b.y, -1.0, 1.0));
    float lon = atan(b.x, b.z) + spin;
    float u = lon / 6.2831853 + 0.5;
    float v = 0.5 - lat / 3.14159265;
    // A point fixed on the surface (turns with Pluto), for the grain.
    float3 q = float3(sin(lon) * cos(lat), sin(lat), cos(lon) * cos(lat));

    half3 albedo = sampleMap(u, v);
    float h0 = luma(albedo);
    float du = 1.5 / mapSize.x;
    float dv = 1.5 / mapSize.y;
    float g0 = grain(q);
    float gE = luma(sampleMap(u + du, v)) - h0 + 0.05 * (grain(q + float3(0.012, 0.0, 0.0)) - g0);
    float gN = luma(sampleMap(u, v - dv)) - h0 + 0.05 * (grain(q + float3(0.0, 0.012, 0.0)) - g0);

    // East and north at this point, in view space (pole tipped by the tilt).
    float3 pole = float3(0.0, ct, st);
    float3 east = normalize(cross(pole, n) + float3(0.00001, 0.0, 0.0));
    float3 north = cross(n, east);
    float3 nb = normalize(n - 5.0 * (gE * east + gN * north));

    albedo = half3(pow(float3(albedo), float3(1.12)) * 1.1);
    albedo *= half(0.9 + 0.2 * g0);

    float ndl = dot(n, L);
    float day = smoothstep(-0.06, 0.32, ndl);
    float diffuse = max(dot(nb, L), 0.0);
    float shade = mix(0.035, 1.0, day) * (0.4 + 0.75 * diffuse);
    float limb = pow(1.0 - z, 3.0);
    half3 col = albedo * shade * (1.0 - 0.25 * limb);
    float ice = smoothstep(0.62, 0.85, h0);
    float spec = pow(max(dot(reflect(-L, nb), float3(0.0, 0.0, 1.0)), 0.0), 28.0) * ice * 0.22 * day;
    col += half3(spec);
    col += HAZE * limb * 0.6 * day;
    // Antialiased edge: blend into the haze as it is just outside the disc.
    float edge = clamp((1.0 - r) * radius, 0.0, 1.0);
    float rimA = 0.93 * clamp(dot(float3(d.x, -d.y, 0.0) / max(r, 0.0001), L) * 0.9 + 0.3, 0.0, 1.0);
    return mix(half4(HAZE * rimA, rimA), half4(col, 1.0), edge);
}
"""
