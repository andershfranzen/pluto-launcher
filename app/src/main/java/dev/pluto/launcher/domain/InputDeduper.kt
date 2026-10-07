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
    fun shouldEmit(direction: Direction, source: MoveSource, nowMs: Long): Boolean = TODO()
    fun reset(): Unit = TODO()
}
