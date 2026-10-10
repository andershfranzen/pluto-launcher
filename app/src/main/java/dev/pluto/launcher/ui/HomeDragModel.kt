package dev.pluto.launcher.ui

import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization

/** Where a drag started, and what it carries. */
sealed interface DragSource {
    /** The id of what is being dragged on the home grid (or the id it would get there). */
    val homeId: String

    /** The app being dragged, or null for a folder. */
    val app: AppKey?

    data class Home(val item: HomeItem) : DragSource {
        override val homeId: String get() = item.id
        override val app: AppKey? get() = (item as? HomeItem.App)?.key
    }

    data class Dock(val slot: Int, val key: AppKey) : DragSource {
        override val homeId: String get() = HomeItem.App(key).id
        override val app: AppKey get() = key
    }

    data class Drawer(val key: AppKey) : DragSource {
        override val homeId: String get() = HomeItem.App(key).id
        override val app: AppKey get() = key
    }
}

/** Where a drag would land if released now. */
sealed interface DropTarget {
    /** A place in the home grid, counted without the dragged item. */
    data class HomeIndex(val index: Int) : DropTarget

    /** Onto a home item: an app (makes a folder of the two) or a folder (adds to it). */
    data class Merge(val itemId: String) : DropTarget

    data class DockSlot(val slot: Int) : DropTarget

    /** The Remove bar: off the home grid or out of the dock (never uninstalls). */
    data object Remove : DropTarget
}

/**
 * What a drop changes, worked out from the organisation as it is (pure, unit tested). Steps
 * run in order: [unpin] (takes the app off home and out of any folder), then [dock], then
 * [home] (the full new order), then [newFolder] or [intoFolder].
 */
data class DropPlan(
    val unpin: AppKey? = null,
    val dock: List<AppKey?>? = null,
    val home: List<HomeItem>? = null,
    val newFolder: NewFolder? = null,
    val intoFolder: Pair<AppKey, Long>? = null,
) {
    /** A folder of [apps] placed at [homeIndex] (counted after the apps leave the grid). */
    data class NewFolder(val apps: List<AppKey>, val homeIndex: Int)

    val isEmpty: Boolean get() = unpin == null && dock == null && home == null && newFolder == null && intoFolder == null

    companion object {
        val NONE = DropPlan()

        fun of(home: List<HomeItem>, dock: List<AppKey?>, inFolder: Set<AppKey>, source: DragSource, target: DropTarget): DropPlan {
            val app = source.app
            val dragged: HomeItem = (source as? DragSource.Home)?.item ?: HomeItem.App(app!!)
            val withoutDragged = home.filterNot { it.id == dragged.id }
            // Leaving the dock: that slot empties.
            val dockWithoutSource = (source as? DragSource.Dock)?.let { s -> dock.toMutableList().also { it[s.slot] = null } }
            return when (target) {
                is DropTarget.HomeIndex -> {
                    val next = withoutDragged.toMutableList().apply { add(target.index.coerceIn(0, size), dragged) }
                    if (next == home && dockWithoutSource == null) return NONE
                    DropPlan(
                        // An app in a folder comes out of it to sit on the grid itself.
                        unpin = app?.takeIf { it in inFolder },
                        dock = dockWithoutSource,
                        home = next,
                    )
                }
                is DropTarget.DockSlot -> {
                    if (app == null || target.slot !in dock.indices) return NONE
                    val occupant = dock[target.slot]
                    if (occupant == app) return NONE
                    val nextDock = dock.toMutableList()
                    val from = nextDock.indexOf(app)
                    if (from >= 0) nextDock[from] = null
                    nextDock[target.slot] = app
                    when (source) {
                        // Dock to dock (or an app already docked): the two swap places.
                        is DragSource.Dock -> {
                            nextDock[source.slot] = occupant
                            DropPlan(dock = nextDock)
                        }
                        is DragSource.Drawer -> {
                            if (from >= 0) nextDock[from] = occupant
                            DropPlan(dock = nextDock)
                        }
                        // Home to dock: the app leaves the grid and the dock's app takes its place there.
                        is DragSource.Home -> {
                            val index = home.indexOfFirst { it.id == dragged.id }
                            val nextHome = withoutDragged.toMutableList()
                            if (occupant != null && nextHome.none { it is HomeItem.App && it.key == occupant } && occupant !in inFolder) {
                                nextHome.add(index.coerceIn(0, nextHome.size), HomeItem.App(occupant))
                            }
                            DropPlan(unpin = app, dock = nextDock, home = nextHome)
                        }
                    }
                }
                is DropTarget.Merge -> {
                    if (app == null || target.itemId == dragged.id) return NONE
                    when (val onto = home.firstOrNull { it.id == target.itemId }) {
                        is HomeItem.App -> {
                            val index = withoutDragged.indexOfFirst { it.id == onto.id }.coerceAtLeast(0)
                            DropPlan(dock = dockWithoutSource, newFolder = NewFolder(listOf(onto.key, app), index))
                        }
                        is HomeItem.FolderRef -> DropPlan(dock = dockWithoutSource, intoFolder = app to onto.folderId)
                        is HomeItem.Widget, null -> NONE
                    }
                }
                DropTarget.Remove -> when (source) {
                    is DragSource.Home -> if (app != null) DropPlan(unpin = app) else NONE
                    is DragSource.Dock -> DropPlan(dock = dockWithoutSource)
                    is DragSource.Drawer -> NONE
                }
            }
        }

        /** A dock list padded to [Organization.DOCK_SLOTS]. */
        fun padded(dock: List<AppKey?>): List<AppKey?> = List(Organization.DOCK_SLOTS) { dock.getOrNull(it) }
    }
}
