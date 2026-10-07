package dev.pluto.launcher.domain

import dev.pluto.launcher.input.Direction
import dev.pluto.launcher.input.MoveSource

/**
 * Some controllers report the same D-pad press as both a key event and a HAT axis
 * motion (and Android may synthesize one from the other). Suppresses a move when the
 * same direction arrived from a *different* source within [windowMs]. Repeats from the
 * same source are always allowed (their pacing is handled upstream).
 */
class InputDeduper(private val windowMs: Long = 90) {
    private class Emitted(val source: MoveSource, val atMs: Long)

    // Last emitted move per direction, so an unrelated move in between can't hide a duplicate.
    private val lastByDirection = HashMap<Direction, Emitted>()

    fun shouldEmit(direction: Direction, source: MoveSource, nowMs: Long): Boolean {
        val last = lastByDirection[direction]
        if (last != null && last.source != source && nowMs - last.atMs < windowMs) {
            // Duplicate report of a move already emitted; keep the window anchored to the original.
            return false
        }
        lastByDirection[direction] = Emitted(source, nowMs)
        return true
    }

    fun reset() {
        lastByDirection.clear()
    }
}
