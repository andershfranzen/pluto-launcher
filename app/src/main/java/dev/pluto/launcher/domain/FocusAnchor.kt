package dev.pluto.launcher.domain

object FocusAnchor {
    /**
     * Keeps focus by identity across list changes (reorder, install, uninstall).
     * If [focused] still exists in [newOrder] return it. Otherwise walk outward from
     * its index in [previousOrder], preferring the next item then the previous one at
     * each distance, and return the first that survives in [newOrder]. Falls back to the
     * item now at the old index (clamped), or null if [newOrder] is empty.
     *
     * A null [focused] means there was nothing to keep, so the result is null too.
     * A [focused] item unknown to [previousOrder] falls back to the first item.
     */
    fun <K> resolve(previousOrder: List<K>, newOrder: List<K>, focused: K?): K? {
        if (focused == null || newOrder.isEmpty()) return null
        val survivors = newOrder.toHashSet()
        if (focused in survivors) return focused

        val oldIndex = previousOrder.indexOf(focused)
        if (oldIndex < 0) return newOrder.first()

        val maxDistance = maxOf(oldIndex, previousOrder.lastIndex - oldIndex)
        for (distance in 1..maxDistance) {
            previousOrder.getOrNull(oldIndex + distance)?.takeIf { it in survivors }?.let { return it }
            previousOrder.getOrNull(oldIndex - distance)?.takeIf { it in survivors }?.let { return it }
        }
        return newOrder[oldIndex.coerceIn(0, newOrder.lastIndex)]
    }
}
