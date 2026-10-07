package dev.pluto.launcher.ui.state

import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.domain.AppSorting
import dev.pluto.launcher.domain.ModeResolver
import dev.pluto.launcher.domain.RecentHistory
import dev.pluto.launcher.domain.SearchMatcher
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.ControllerInfo
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.ui.FolderUi
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.SessionState
import dev.pluto.launcher.ui.UserMessage

/** Window size in dp as last reported by the layout. */
data class WindowSizeDp(val widthDp: Int, val heightDp: Int)

/** Persisted / system inputs that change rarely. */
data class LibraryInputs(
    /** Null until the catalog has loaded once. */
    val catalog: List<AppEntry>?,
    val organization: Organization,
    val settings: LauncherSettings,
)

/** Device and window environment plus transient UI flags. */
data class EnvironmentInputs(
    val controllers: List<ControllerInfo>,
    val otherProfilesPresent: Boolean,
    val window: WindowSizeDp?,
    val message: UserMessage?,
    val isDefaultHome: Boolean,
)

/**
 * Pure projection of the launcher's inputs into [LauncherUiState]. Not thread-safe:
 * a single instance is meant to be driven by one sequential flow collector.
 *
 * Rules: unavailable (uninstalled) keys never produce tiles; hidden apps are kept out of
 * every ordinary list (drawer, search, categories, recents, home grid, folders and dock)
 * but remain in [LauncherUiState.allApps] for the hidden-apps management screen.
 */
class LauncherStateBuilder {
    // Sorting ~200 labels with a Collator is the heaviest step; only redo it when the catalog changes.
    private var sortedFor: List<AppEntry>? = null
    private var sorted: List<AppEntry> = emptyList()

    fun build(library: LibraryInputs, env: EnvironmentInputs, session: SessionState): LauncherUiState {
        val org = library.organization
        val settings = library.settings

        val allApps = alphabetical(library.catalog.orEmpty())
        val byKey = allApps.associateBy { it.key }
        val hidden = org.hidden
        fun visible(key: AppKey?): AppEntry? = key?.takeIf { it !in hidden }?.let(byKey::get)

        val apps = allApps.filter { it.key !in hidden }

        val folders = org.folders.mapValues { (_, folder) -> FolderUi(folder, folder.apps.mapNotNull(::visible)) }
        val homeTiles = org.homeItems.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> visible(item.key)?.let(HomeTile::App)
                is HomeItem.FolderRef -> folders[item.folderId]?.let(HomeTile::FolderTile)
            }
        }

        val categories = org.categories.sortedWith(compareBy({ it.position }, { it.id }))
        val active = categories.firstOrNull { it.id == session.activeCategoryId } ?: categories.firstOrNull { it.isAll }
        val categoryApps = if (active == null || active.isAll) {
            apps
        } else {
            val members = org.categoryMembers[active.id].orEmpty()
            apps.filter { it.key in members }
        }
        val drawerApps = SearchMatcher.filter(categoryApps, session.searchText)

        val recents = if (settings.historyEnabled) {
            org.recents.mapNotNull { visible(it.key) }.take(RecentHistory.MAX_ENTRIES)
        } else {
            emptyList()
        }

        val controllerConnected = env.controllers.isNotEmpty()
        val mode = env.window?.let {
            ModeResolver.resolve(it.widthDp, it.heightDp, controllerConnected, settings.handheldAppearance)
        } ?: LauncherMode.PHONE

        return LauncherUiState(
            loading = library.catalog == null,
            mode = mode,
            apps = apps,
            allApps = allApps,
            drawerApps = drawerApps,
            homeTiles = homeTiles,
            dock = List(Organization.DOCK_SLOTS) { i -> visible(org.dock.getOrNull(i)) },
            folders = folders,
            categories = categories,
            categoryMembers = org.categoryMembers,
            categoryApps = categoryApps,
            hidden = hidden,
            recents = recents,
            settings = settings,
            controllers = env.controllers,
            otherProfilesPresent = env.otherProfilesPresent,
            isDefaultHome = env.isDefaultHome,
            session = session,
            message = env.message,
        )
    }

    private fun alphabetical(catalog: List<AppEntry>): List<AppEntry> {
        if (catalog !== sortedFor) {
            sorted = AppSorting.alphabetical(catalog)
            sortedFor = catalog
        }
        return sorted
    }
}
