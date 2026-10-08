package dev.pluto.launcher.ui.motion

/**
 * Pure bookkeeping for the animated layer stack: which layers are drawn, in which order,
 * and which of them are only still on screen because they are animating out.
 *
 * A [StackSlot] is one rendered layer, identified by a stable [StackSlot.key]
 * (Layer.encode). [StackSlot.present] is false while the layer is leaving: it was removed
 * from the navigation stack but stays composed until its exit animation completes.
 */
data class StackSlot(val key: String, val present: Boolean)

object LayerStack {
    /**
     * Merges the navigation stack [target] (bottom to top) into the previously rendered
     * [previous] slots. Target layers come out present and in target order. Layers that left
     * keep animating out at their old height: each sits directly above the nearest layer
     * that was below it before and is still present (or at the very bottom), so a layer
     * pushed while another is leaving is drawn above the leaving one. A key that comes back
     * while leaving is present again (its exit reverses).
     */
    fun reconcile(previous: List<StackSlot>, target: List<String>): List<StackSlot> {
        val targetSet = target.toHashSet()
        val result = ArrayList<StackSlot>(target.size + previous.size)
        target.mapTo(result) { StackSlot(it, present = true) }
        var cursor = 0
        for (slot in previous) {
            if (slot.key in targetSet) {
                cursor = result.indexOfFirst { it.key == slot.key } + 1
            } else {
                result.add(cursor, StackSlot(slot.key, present = false))
                cursor++
            }
        }
        return result
    }

    /** Drops leaving slots whose exit animation has finished. */
    fun withoutFinished(slots: List<StackSlot>, finished: Set<String>): List<StackSlot> =
        if (finished.isEmpty()) slots else slots.filter { it.present || it.key !in finished }

    /**
     * Index of the lowest slot that must be composed. Everything below the topmost slot
     * for which [coversBelow] is true (a full-screen layer that has finished entering) is
     * invisible and need not be composed. 0 when nothing covers; then the base layout is
     * composed too.
     */
    fun firstComposed(slots: List<StackSlot>, coversBelow: (StackSlot) -> Boolean): Int =
        slots.indexOfLast { it.present && coversBelow(it) }.coerceAtLeast(0)

    /**
     * Leaving slots hidden beneath [firstComposed] would never finish animating (they are
     * not composed); they are simply dropped.
     */
    fun withoutHiddenLeaving(slots: List<StackSlot>, firstComposed: Int): List<StackSlot> =
        if (slots.withIndex().none { (i, s) -> i < firstComposed && !s.present }) {
            slots
        } else {
            slots.filterIndexed { i, s -> i >= firstComposed || s.present }
        }

    /** True when the base (home) layout must be composed. */
    fun baseComposed(slots: List<StackSlot>, coversBelow: (StackSlot) -> Boolean): Boolean =
        slots.none { it.present && coversBelow(it) }

    /** Index of the top present slot (the one that receives focus), or -1. */
    fun topPresent(slots: List<StackSlot>): Int = slots.indexOfLast { it.present }

    /**
     * Index of the nearest slot above [index] that is full-screen according to [fullScreen]
     * (its motion makes the layers beneath recede / shift), or -1. [index] = -1 asks for the
     * base layout's cover.
     */
    fun coverOf(slots: List<StackSlot>, index: Int, fullScreen: (StackSlot) -> Boolean): Int {
        for (i in index + 1 until slots.size) if (fullScreen(slots[i])) return i
        return -1
    }
}
