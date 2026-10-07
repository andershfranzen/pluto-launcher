package dev.pluto.launcher.domain

object FocusAnchor {
    /**
     * Keeps focus by identity across list changes (reorder, install, uninstall).
     * If [focused] still exists in [newOrder] return it. Otherwise walk outward from
     * its index in [previousOrder], preferring the next item then the previous one at
     * each distance, and return the first that survives in [newOrder]. Falls back to the
     * item now at the old index (clamped), or null if [newOrder] is empty.
     */
    fun <K> resolve(previousOrder: List<K>, newOrder: List<K>, focused: K?): K? = TODO()
}
