package dev.pluto.launcher.ui.state

import dev.pluto.launcher.domain.Reorder
import dev.pluto.launcher.model.ReorderOp

/**
 * Applies [Reorder] to the items the user can actually see, leaving invisible entries
 * (hidden or currently unavailable apps) in their original slots. Without this, "move up"
 * could swap with an invisible neighbour and appear to do nothing.
 */
internal fun <T> reorderVisible(full: List<T>, isVisible: (T) -> Boolean, item: T, op: ReorderOp<T>): List<T> {
    val visible = full.filter(isVisible)
    if (item !in visible) return Reorder.apply(full, item, op)
    val reordered = Reorder.apply(visible, item, op).iterator()
    // Refill the visible slots in their new order; invisible entries keep their positions.
    return full.map { if (isVisible(it)) reordered.next() else it }
}
