package dev.pluto.launcher.ui.console

import dev.pluto.launcher.data.prefs.CarouselStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Pure Coverflow geometry (Android-free, unit tested). A card's look depends only on its
 * distance d = index - position from the continuous flow position: the centre card (d = 0)
 * faces the viewer at full size; neighbours tilt away, shrink, dim and stack toward the centre.
 */
object CoverflowMath {
    /** Centre to first neighbour, in card widths. */
    const val FIRST_GAP = 0.74f

    /**
     * Each further neighbour, in card widths. They still overlap (classic Coverflow stacking)
     * but about three quarters of each side card stays visible.
     */
    const val SIDE_GAP = 0.44f

    /**
     * Tilt of every side card, degrees. The whole card turns as one plane (the icon is part of
     * it, never scaled on its own), and 38° keeps about 4/5 of its width (cos 38° ≈ 0.79), so
     * a round icon still reads as round instead of an oval.
     */
    const val SIDE_ANGLE = 38f

    /** Scale of the first neighbour, and how much each further one shrinks. */
    const val SIDE_SCALE = 0.8f
    const val SCALE_STEP = 0.05f
    const val MIN_SCALE = 0.5f

    /** Dim over the first neighbour, plus this much per further card. */
    const val SIDE_DIM = 0.38f
    const val DIM_STEP = 0.08f
    const val MAX_DIM = 0.65f

    /**
     * Cards further than this are not composed; they fade out from [FADE_START], so the flow
     * thins out gradually instead of ending on a fully opaque card.
     */
    const val RADIUS = 4
    const val FADE_START = 1.6f

    /**
     * How far a fling carries the flow: about the distance a decaying fling covers, in seconds
     * of its release velocity (cards per second). Tuned so a casual flick moves 2-4 cards.
     */
    const val FLING_PROJECTION_S = 0.16f

    /**
     * Visual transform of one card. [offsetX] and [offsetY] are in card widths from the stage
     * centre (y down); [z] orders drawing (higher in front).
     */
    data class CardTransform(
        val offsetX: Float,
        val scale: Float,
        val rotationY: Float,
        val dim: Float,
        val alpha: Float,
        val z: Float,
        val offsetY: Float = 0f,
        val rotationZ: Float = 0f,
    )

    /** Horizontal offset of a card at distance [d], in card widths, for [style]. */
    fun offset(d: Float, style: CarouselStyle = CarouselStyle.COVERFLOW): Float =
        if (style == CarouselStyle.COVERFLOW) coverflowOffset(d) else transform(d, style).offsetX

    /** The classic flow's offset (continuous and monotonic in d). */
    private fun coverflowOffset(d: Float): Float {
        val a = abs(d)
        val near = minOf(a, 1f) * FIRST_GAP
        val far = (a - 1f).coerceAtLeast(0f) * SIDE_GAP
        return sign(d) * (near + far)
    }

    fun transform(d: Float, style: CarouselStyle = CarouselStyle.COVERFLOW): CardTransform = when (style) {
        CarouselStyle.COVERFLOW -> coverflow(d)
        CarouselStyle.SHOWCASE -> showcase(d)
        CarouselStyle.ARC -> arc(d)
        CarouselStyle.RING -> ring(d)
        CarouselStyle.DECK -> deck(d)
    }

    /** Fade towards the edge of the composed window, shared by every style. */
    private fun edgeAlpha(a: Float): Float = when {
        a <= FADE_START -> 1f
        a >= RADIUS -> 0f
        else -> 1f - (a - FADE_START) / (RADIUS - FADE_START)
    }

    private fun showcase(d: Float): CardTransform {
        val a = abs(d)
        val near = minOf(a, 1f)
        val far = (a - 1f).coerceAtLeast(0f)
        val scale = (1f - 0.2f * near - 0.05f * far).coerceAtLeast(MIN_SCALE)
        // Spacing follows the shrinking cards, so the gaps between them stay even and airy.
        val x = sign(d) * (near * 1.04f + far * 0.9f)
        val dim = (0.32f * near + 0.07f * far).coerceAtMost(MAX_DIM)
        return CardTransform(x, scale, 0f, dim, edgeAlpha(a), -a)
    }

    private fun arc(d: Float): CardTransform {
        val a = abs(d)
        val near = minOf(a, 1f)
        val far = (a - 1f).coerceAtLeast(0f)
        val scale = (1f - 0.18f * near - 0.05f * far).coerceAtLeast(MIN_SCALE)
        val x = sign(d) * (near * 0.92f + far * 0.8f)
        // Down a gentle curve, tipping outwards with it; it flattens out so far cards stay clear of the title.
        val y = 0.07f * a * a / (1f + 0.15f * a)
        val dim = (0.3f * near + 0.08f * far).coerceAtMost(MAX_DIM)
        return CardTransform(x, scale, 0f, dim, edgeAlpha(a), -a, offsetY = y, rotationZ = d * RING_TIP)
    }

    private fun ring(d: Float): CardTransform {
        val a = abs(d)
        val angle = (d * RING_STEP).coerceIn(-RING_MAX, RING_MAX)
        val radians = Math.toRadians(angle.toDouble())
        val depth = kotlin.math.cos(radians).toFloat() // 1 in front, 0 at the side
        val x = kotlin.math.sin(radians).toFloat() * RING_RADIUS
        val scale = (0.58f + 0.42f * depth).coerceAtLeast(MIN_SCALE)
        // Faces outwards, like cards stood around a drum.
        val dim = ((1f - depth) * 0.9f).coerceAtMost(MAX_DIM)
        val alpha = edgeAlpha(a) * ((depth - 0.1f) / 0.3f).coerceIn(0f, 1f)
        return CardTransform(x, scale, angle, dim, alpha, depth, offsetY = -0.08f * (1f - depth))
    }

    private fun deck(d: Float): CardTransform {
        val a = abs(d)
        return if (d >= 0f) {
            // Waiting cards: a stack behind and to the right, each a little smaller and higher.
            val scale = (1f - 0.08f * d).coerceAtLeast(MIN_SCALE)
            val x = 0.36f * d
            val dim = (0.2f * d).coerceAtMost(MAX_DIM)
            CardTransform(x, scale, 0f, dim, edgeAlpha(a), -a, offsetY = -0.035f * d)
        } else {
            // Passed cards slide away to the left and fade out.
            val x = d * 1.15f
            val alpha = (1f + d * 0.9f).coerceIn(0f, 1f)
            CardTransform(x, 1f + 0.04f * d, 0f, (0.4f * a).coerceAtMost(MAX_DIM), alpha, -a)
        }
    }

    private fun coverflow(d: Float): CardTransform {
        val a = abs(d)
        val near = minOf(a, 1f)
        val far = (a - 1f).coerceAtLeast(0f)
        val scale = (1f - (1f - SIDE_SCALE) * near - SCALE_STEP * far).coerceAtLeast(MIN_SCALE)
        // Inner edges recede: side cards face the centre like the walls of a corridor.
        val rotation = -sign(d) * near * SIDE_ANGLE
        val dim = (SIDE_DIM * near + DIM_STEP * far).coerceAtMost(MAX_DIM)
        return CardTransform(coverflowOffset(d), scale, rotation, dim, edgeAlpha(a), -a)
    }

    /** Ring: degrees between neighbours, the furthest turn shown, and the drum radius in card widths. */
    private const val RING_STEP = 25f
    private const val RING_MAX = 88f
    private const val RING_RADIUS = 2.15f

    /** Arc: degrees each card tips per step from the centre. */
    private const val RING_TIP = 5f

    /** Nearest card to a flow position. */
    fun nearest(position: Float, count: Int): Int =
        if (count <= 0) 0 else position.roundToInt().coerceIn(0, count - 1)

    /**
     * Where a touch release settles: the card nearest to the position a fling with
     * [velocity] (cards per second, positive toward later cards) would coast to.
     */
    fun snapTarget(position: Float, velocity: Float, count: Int): Int =
        nearest(position + velocity * FLING_PROJECTION_S, count)

    /**
     * The indices to compose: [RADIUS] cards either side of both the selection and the
     * (possibly lagging) flow position, so the next card is always composed for a held D-pad
     * and nothing visible is missing while the flow glides.
     */
    fun window(position: Float, selected: Int, count: Int): IntRange {
        if (count <= 0) return IntRange.EMPTY
        val p = nearest(position, count)
        val lo = (minOf(p, selected) - RADIUS).coerceAtLeast(0)
        val hi = (maxOf(p, selected) + RADIUS).coerceAtMost(count - 1)
        return lo..hi
    }

    /**
     * A shelf with fewer cards than this still reads as a shelf: the empty places after its
     * last card show faint placeholder ("ghost") slots.
     */
    const val MIN_SLOTS = 4

    /** Indices of the ghost slots after the last of [count] cards (none for an empty or full shelf). */
    fun ghostSlots(count: Int): IntRange = if (count in 1 until MIN_SLOTS) count until MIN_SLOTS else IntRange.EMPTY

    /** Touch drag: pixels to flow distance (dragging one first gap moves one card). Finger right = earlier cards. */
    fun dragDelta(dxPx: Float, cardWidthPx: Float): Float =
        if (cardWidthPx <= 0f) 0f else -dxPx / (cardWidthPx * FIRST_GAP)
}
