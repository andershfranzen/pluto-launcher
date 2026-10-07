package dev.pluto.launcher.domain

import dev.pluto.launcher.model.ReorderOp

object Reorder {
    /**
     * Returns a new list with [item] moved per [op]. Up/Down swap with the neighbour
     * (no-op at the ends). Before/After place it relative to target. Returns the list
     * unchanged when [item] or the target is absent, or when target == item.
     */
    fun <T> apply(list: List<T>, item: T, op: ReorderOp<T>): List<T> {
        val index = list.indexOf(item)
        if (index < 0) return list
        return when (op) {
            ReorderOp.Up -> if (index == 0) list else list.swapped(index, index - 1)
            ReorderOp.Down -> if (index == list.lastIndex) list else list.swapped(index, index + 1)
            is ReorderOp.Before -> moveRelative(list, index, op.target, after = false)
            is ReorderOp.After -> moveRelative(list, index, op.target, after = true)
        }
    }

    private fun <T> List<T>.swapped(a: Int, b: Int): List<T> =
        toMutableList().also { it[a] = this[b]; it[b] = this[a] }

    private fun <T> moveRelative(list: List<T>, index: Int, target: T, after: Boolean): List<T> {
        if (target == list[index] || target !in list) return list
        val result = list.toMutableList()
        val moved = result.removeAt(index)
        // Locate the target after removal so its index accounts for the shift.
        val targetIndex = result.indexOf(target)
        result.add(if (after) targetIndex + 1 else targetIndex, moved)
        return result
    }
}
