package dev.pluto.launcher.domain

import dev.pluto.launcher.model.ReorderOp

object Reorder {
    /**
     * Returns a new list with [item] moved per [op]. Up/Down swap with the neighbour
     * (no-op at the ends). Before/After place it relative to target. Returns the list
     * unchanged when [item] or the target is absent, or when target == item.
     */
    fun <T> apply(list: List<T>, item: T, op: ReorderOp<T>): List<T> = TODO()
}
