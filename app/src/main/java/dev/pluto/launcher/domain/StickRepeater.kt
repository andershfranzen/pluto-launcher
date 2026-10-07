package dev.pluto.launcher.domain

import dev.pluto.launcher.input.Direction
import kotlin.math.abs

/**
 * Turns analog stick samples into discrete, repeating moves.
 *
 * - Magnitudes inside max([deadZone], device flat) are neutral.
 * - Diagonals resolve to the dominant axis; to avoid flicker the current direction is
 *   kept until the other axis exceeds it by a clear margin (hysteresis ~15%).
 * - First move fires immediately on leaving neutral; repeats begin after [repeatDelayMs]
 *   and continue every [repeatIntervalMs] while held. Changing direction restarts this.
 * - Returning to neutral resets.
 *
 * Axes follow Android conventions: positive x is right, positive y is down.
 * Time is passed in so this is deterministic and unit-testable.
 */
class StickRepeater(
    var deadZone: Float = 0.25f,
    var repeatDelayMs: Long = 350,
    var repeatIntervalMs: Long = 100,
) {
    private var held: Direction? = null
    private var deadline: Long = 0L

    /** Feed a sample. Returns a direction to emit now, or null. [flat] is the device-reported neutral range. */
    fun onSample(x: Float, y: Float, nowMs: Long, flat: Float = 0f): Direction? {
        val direction = resolveDirection(x, y, maxOf(deadZone, flat))
        if (direction == null) {
            reset()
            return null
        }
        if (direction != held) {
            held = direction
            deadline = nowMs + repeatDelayMs
            return direction
        }
        // Same direction still held: a sample past the deadline counts as a tick (covers late timers).
        return onTick(nowMs)
    }

    /** Call when a scheduled deadline passes; returns a repeat direction to emit, or null. */
    fun onTick(nowMs: Long): Direction? {
        val direction = held ?: return null
        if (nowMs < deadline) return null
        // Keep a steady cadence, but never burst to catch up after a long stall.
        val interval = repeatIntervalMs.coerceAtLeast(1)
        val next = deadline + interval
        deadline = if (next > nowMs) next else nowMs + interval
        return direction
    }

    /** Absolute time of the next repeat, or null while neutral. */
    fun nextDeadlineMs(): Long? = if (held != null) deadline else null

    /** Currently held direction, or null while neutral. */
    val heldDirection: Direction? get() = held

    fun reset() {
        held = null
        deadline = 0L
    }

    private fun resolveDirection(x: Float, y: Float, threshold: Float): Direction? {
        val ax = abs(x)
        val ay = abs(y)
        if (ax.isNaN() || ay.isNaN() || maxOf(ax, ay) <= threshold) return null

        val horizontal = when (held) {
            // Stay horizontal unless vertical clearly dominates (or horizontal fell into the dead zone).
            Direction.LEFT, Direction.RIGHT -> ax > threshold && ay <= ax * (1 + HYSTERESIS)
            // Switch to horizontal only when it clearly dominates.
            Direction.UP, Direction.DOWN -> !(ay > threshold && ax <= ay * (1 + HYSTERESIS))
            // Fresh from neutral: plain dominant axis; an exact diagonal resolves horizontally.
            null -> ax >= ay
        }
        return if (horizontal) {
            if (x > 0) Direction.RIGHT else Direction.LEFT
        } else {
            if (y > 0) Direction.DOWN else Direction.UP
        }
    }

    private companion object {
        const val HYSTERESIS = 0.15f
    }
}
