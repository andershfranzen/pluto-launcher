package dev.pluto.launcher.ui.console

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

    /** Tilt of every side card, degrees: shallow enough that side icons keep about 3/4 of their width (cos 42° ≈ 0.74). */
    const val SIDE_ANGLE = 42f

    /** Scale of the first neighbour, and how much each further one shrinks. */
    const val SIDE_SCALE = 0.8f
    const val SCALE_STEP = 0.05f
    const val MIN_SCALE = 0.5f

    /** Dim over the first neighbour, plus this much per further card. */
    const val SIDE_DIM = 0.38f
    const val DIM_STEP = 0.08f
    const val MAX_DIM = 0.65f

    /** Cards further than this are not composed; they fade out from [FADE_START]. */
    const val RADIUS = 4
    const val FADE_START = 3.1f

    /**
     * How far a fling carries the flow: about the distance a decaying fling covers, in seconds
     * of its release velocity (cards per second). Tuned so a casual flick moves 2-4 cards.
     */
    const val FLING_PROJECTION_S = 0.16f

    /** Visual transform of one card. [offsetX] is in card widths from the stage centre. */
    data class CardTransform(
        val offsetX: Float,
        val scale: Float,
        val rotationY: Float,
        val dim: Float,
        val alpha: Float,
        val z: Float,
    )

    /** Horizontal offset of a card at distance [d], in card widths (continuous and monotonic in d). */
    fun offset(d: Float): Float {
        val a = abs(d)
        val near = minOf(a, 1f) * FIRST_GAP
        val far = (a - 1f).coerceAtLeast(0f) * SIDE_GAP
        return sign(d) * (near + far)
    }

    fun transform(d: Float): CardTransform {
        val a = abs(d)
        val near = minOf(a, 1f)
        val far = (a - 1f).coerceAtLeast(0f)
        val scale = (1f - (1f - SIDE_SCALE) * near - SCALE_STEP * far).coerceAtLeast(MIN_SCALE)
        // Inner edges recede: side cards face the centre like the walls of a corridor.
        val rotation = -sign(d) * near * SIDE_ANGLE
        val dim = (SIDE_DIM * near + DIM_STEP * far).coerceAtMost(MAX_DIM)
        val alpha = when {
            a <= FADE_START -> 1f
            a >= RADIUS -> 0f
            else -> 1f - (a - FADE_START) / (RADIUS - FADE_START)
        }
        return CardTransform(offset(d), scale, rotation, dim, alpha, -a)
    }

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

    /** Touch drag: pixels to flow distance (dragging one first gap moves one card). Finger right = earlier cards. */
    fun dragDelta(dxPx: Float, cardWidthPx: Float): Float =
        if (cardWidthPx <= 0f) 0f else -dxPx / (cardWidthPx * FIRST_GAP)
}
