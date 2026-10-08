package dev.pluto.launcher.ui.focus

import dev.pluto.launcher.domain.FocusAnchor

/**
 * Ordered focus ids per surface ("drawer", "home", "hh:fav", ...), as last laid out.
 * Used to find the nearest surviving neighbour of a control that vanished (uninstalled,
 * hidden, unpinned, moved into a folder) so focus never falls back to the top of a list.
 *
 * Each surface keeps its current order and the last *different* order, so a lookup still
 * works right after the list changed. Pure Kotlin; main thread only.
 */
class FocusOrders {
    private class Orders(var current: List<String>, var previous: List<String>)

    private val surfaces = LinkedHashMap<String, Orders>()

    fun set(surface: String, ids: List<String>) {
        val orders = surfaces[surface]
        if (orders == null) {
            surfaces[surface] = Orders(ids, emptyList())
        } else if (orders.current != ids) {
            orders.previous = orders.current
            orders.current = ids
        }
    }

    /** Surface whose current or previous order contains [id], or null. */
    fun surfaceOf(id: String): String? =
        surfaces.entries.firstOrNull { id in it.value.current }?.key
            ?: surfaces.entries.firstOrNull { id in it.value.previous }?.key

    /**
     * The id to focus instead of [id] together with its index in the surface's current
     * order. Returns [id] itself when it is still present, the nearest surviving neighbour
     * when it vanished, or null when [id] was never part of a known surface (or the
     * surface is now empty).
     */
    fun replacementFor(id: String): Replacement? {
        val surface = surfaceOf(id) ?: return null
        val orders = surfaces.getValue(surface)
        val current = orders.current
        val index = current.indexOf(id)
        if (index >= 0) return Replacement(surface, id, index)
        val resolved = FocusAnchor.resolve(orders.previous, current, id) ?: return null
        return Replacement(surface, resolved, current.indexOf(resolved))
    }

    data class Replacement(val surface: String, val id: String, val index: Int)
}
