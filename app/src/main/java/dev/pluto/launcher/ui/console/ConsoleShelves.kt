package dev.pluto.launcher.ui.console

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.ui.HomeTile

/**
 * Identity of one Handheld ("console") shelf: the tab row reads
 * Recent launches · Favourites · then every category (All apps, Games, Tools, user categories).
 * [key] is the stable string form kept in SessionState / SavedStateHandle.
 */
sealed interface ShelfId {
    val key: String

    data object Recent : ShelfId {
        override val key: String = "recent"
    }

    data object Favourites : ShelfId {
        override val key: String = "fav"
    }

    data class OfCategory(val categoryId: Long) : ShelfId {
        override val key: String get() = "cat:$categoryId"
    }

    companion object {
        fun decode(value: String?): ShelfId? = when {
            value == null -> null
            value == Recent.key -> Recent
            value == Favourites.key -> Favourites
            value.startsWith("cat:") -> value.removePrefix("cat:").toLongOrNull()?.let(::OfCategory)
            else -> null
        }
    }
}

/** One shelf as shown: its identity, tab title, the subtitle under the selected app, and its apps. */
data class Shelf(val id: ShelfId, val title: String, val subtitle: String, val apps: List<AppEntry>)

/**
 * Pure shelf rules for the Coverflow console layout (Android-free, unit tested).
 */
object ConsoleShelves {
    /** Spec wording: the row is called "Recent launches" (not playtime or system Recents). */
    const val RECENT_TITLE = "Recent launches"
    const val FAVOURITES_TITLE = "Favourites"

    /**
     * Tab order. Recent launches only exists while history is enabled (disabled history keeps
     * no records, so the shelf would only ever be empty); Favourites always exists (it has a
     * helpful empty state); then the categories in their persisted order.
     */
    fun order(categories: List<Category>, historyEnabled: Boolean): List<ShelfId> = buildList {
        if (historyEnabled) add(ShelfId.Recent)
        add(ShelfId.Favourites)
        categories.forEach { add(ShelfId.OfCategory(it.id)) }
    }

    /**
     * Where the console lands when no shelf was chosen yet: Recent launches when history is
     * enabled and has entries, else Favourites if there are any, else All apps (else the first shelf).
     */
    fun defaultShelf(
        order: List<ShelfId>,
        recentsNonEmpty: Boolean,
        favouritesNonEmpty: Boolean,
        allCategoryId: Long?,
    ): ShelfId {
        if (recentsNonEmpty && ShelfId.Recent in order) return ShelfId.Recent
        if (favouritesNonEmpty && ShelfId.Favourites in order) return ShelfId.Favourites
        val all = allCategoryId?.let(ShelfId::OfCategory)
        if (all != null && all in order) return all
        return order.firstOrNull() ?: ShelfId.Favourites
    }

    /** The saved shelf if it still exists (category deleted, history turned off), else [default]. */
    fun resolve(saved: ShelfId?, order: List<ShelfId>, default: ShelfId): ShelfId =
        saved?.takeIf { it in order } ?: default

    /** L1/R1: the previous / next shelf, wrapping around. An unknown [current] steps from the first shelf. */
    fun step(order: List<ShelfId>, current: ShelfId, delta: Int): ShelfId? {
        if (order.isEmpty()) return null
        val index = order.indexOf(current)
        if (index < 0) return order.first()
        return order[Math.floorMod(index + delta, order.size)]
    }

    /** -1 / +1: which way the flow slides when moving from [from] to [to] (later shelves come in from the end). */
    fun direction(order: List<ShelfId>, from: ShelfId?, to: ShelfId): Int {
        val a = from?.let(order::indexOf) ?: -1
        val b = order.indexOf(to)
        return if (a < 0 || b < 0 || b >= a) 1 else -1
    }

    /**
     * Favourites as one flat flow: home apps in their order, with a folder's apps in place of
     * the folder (a card per app; folders do not nest in the flow).
     */
    fun favouriteApps(homeTiles: List<HomeTile>): List<AppEntry> {
        val seen = HashSet<AppKey>()
        return buildList {
            for (tile in homeTiles) {
                when (tile) {
                    is HomeTile.App -> if (seen.add(tile.entry.key)) add(tile.entry)
                    is HomeTile.FolderTile -> tile.folder.apps.forEach { if (seen.add(it.key)) add(it) }
                }
            }
        }
    }

    /** Builds a shelf's contents from the launcher's (already visibility-filtered) lists. */
    fun shelf(
        id: ShelfId,
        categories: List<Category>,
        categoryMembers: Map<Long, Set<AppKey>>,
        visibleApps: List<AppEntry>,
        favourites: List<AppEntry>,
        recents: List<AppEntry>,
    ): Shelf = when (id) {
        ShelfId.Recent -> Shelf(id, RECENT_TITLE, RECENT_TITLE, recents)
        ShelfId.Favourites -> Shelf(id, FAVOURITES_TITLE, FAVOURITES_TITLE, favourites)
        is ShelfId.OfCategory -> {
            val category = categories.firstOrNull { it.id == id.categoryId }
            val name = category?.name ?: "All apps"
            val apps = if (category == null || category.isAll) {
                visibleApps
            } else {
                val members = categoryMembers[category.id].orEmpty()
                visibleApps.filter { it.key in members }
            }
            Shelf(id, name, name, apps)
        }
    }

    /** Tab title of [id] (without building its app list). */
    fun title(id: ShelfId, categories: List<Category>): String = when (id) {
        ShelfId.Recent -> RECENT_TITLE
        ShelfId.Favourites -> FAVOURITES_TITLE
        is ShelfId.OfCategory -> categories.firstOrNull { it.id == id.categoryId }?.name ?: "All apps"
    }

    /**
     * The card a shelf opens on: the app it last had selected (by identity), else the
     * launcher-wide selected app if it is on this shelf, else the first card.
     */
    fun initialIndex(apps: List<AppEntry>, remembered: AppKey?, globalSelection: AppKey?): Int {
        if (apps.isEmpty()) return 0
        remembered?.let { key -> apps.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let { return it } }
        globalSelection?.let { key -> apps.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let { return it } }
        return 0
    }
}
