package dev.pluto.launcher.domain

import dev.pluto.launcher.input.Direction

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
 * Time is passed in so this is deterministic and unit-testable.
 */
class StickRepeater(
    var deadZone: Float = 0.25f,
    var repeatDelayMs: Long = 350,
    var repeatIntervalMs: Long = 100,
) {
    /** Feed a sample. Returns a direction to emit now, or null. [flat] is the device-reported neutral range. */
    fun onSample(x: Float, y: Float, nowMs: Long, flat: Float = 0f): Direction? = TODO()

    /** Call when a scheduled deadline passes; returns a repeat direction to emit, or null. */
    fun onTick(nowMs: Long): Direction? = TODO()

    /** Absolute time of the next repeat, or null while neutral. */
    fun nextDeadlineMs(): Long? = TODO()

    /** Currently held direction, or null while neutral. */
    val heldDirection: Direction? get() = TODO()

    fun reset(): Unit = TODO()
}
