package dev.pluto.launcher.ui.state

import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.domain.AppSorting
import dev.pluto.launcher.domain.ModeResolver
import dev.pluto.launcher.domain.RecentHistory
import dev.pluto.launcher.domain.SearchMatcher
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.model.ControllerInfo
import dev.pluto.launcher.model.Folder
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.model.RecentLaunch
import dev.pluto.launcher.ui.FixedSession
import dev.pluto.launcher.ui.FolderUi
import dev.pluto.launcher.ui.HomeTile
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.SessionSource
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
    /** Non-null when the organisation or settings store could not be read. */
    val storageError: String? = null,
)

/**
 * Pure projection of the launcher's inputs into [LauncherUiState]. Not thread-safe:
 * a single instance is meant to be driven by one sequential flow collector.
 *
 * Rules: unavailable (uninstalled) keys never produce tiles; hidden apps are kept out of
 * every ordinary list (drawer, search, categories, recents, home grid, folders and dock)
 * but remain in [LauncherUiState.allApps] for the hidden-apps management screen.
 *
 * Identity: every derived list is memoised on its own inputs and, when recomputed with an
 * equal result, the previous instance is kept. So a change that does not affect a list
 * (a search keystroke for the home grid, a recent launch for the drawer, a layer opening
 * for everything) hands composables the very same list instance and they skip; and a
 * build whose result equals the previous state returns the previous state itself.
 */
class LauncherStateBuilder {
    // Sorting ~200 labels with a Collator is the heaviest step; only redo it when the catalog changes.
    private val sortedMemo = Memo<List<AppEntry>?, List<AppEntry>>(identity = true)
    private val byKeyMemo = Memo<List<AppEntry>, Map<AppKey, AppEntry>>(identity = true)
    private val appsMemo = Memo<Pair<List<AppEntry>, Set<AppKey>>, List<AppEntry>>()
    private val foldersMemo = Memo<Triple<Map<Long, Folder>, List<AppEntry>, Set<AppKey>>, Map<Long, FolderUi>>()
    private val homeMemo = Memo<Triple<List<HomeItem>, Map<Long, FolderUi>, List<AppEntry>>, List<HomeTile>>()
    private val dockMemo = Memo<Triple<List<AppKey?>, List<AppEntry>, Set<AppKey>>, List<AppEntry?>>()
    private val categoriesMemo = Memo<List<Category>, List<Category>>()
    private val categoryAppsMemo = Memo<Triple<List<AppEntry>, Long?, Set<AppKey>?>, List<AppEntry>>()
    private val drawerMemo = Memo<Pair<List<AppEntry>, String>, List<AppEntry>>()
    private val recentsMemo = Memo<Triple<List<RecentLaunch>, List<AppEntry>, Boolean>, List<AppEntry>>()

    private var previous: LauncherUiState? = null

    /** Number of times the expensive alphabetical sort ran (for tests). */
    internal var sortCount = 0
        private set

    /**
     * State while organisation or settings have not loaded: the loading placeholder, or the
     * storage error screen when [EnvironmentInputs.storageError] is set.
     */
    fun notLoaded(
        env: EnvironmentInputs,
        session: SessionState,
        source: SessionSource = FixedSession(session),
    ): LauncherUiState = stabilize(
        LauncherUiState(
            loading = true,
            controllers = env.controllers,
            otherProfilesPresent = env.otherProfilesPresent,
            isDefaultHome = env.isDefaultHome,
            message = env.message,
            storageError = env.storageError,
            sessionSource = source,
        ),
    )

    /**
     * Builds the state. [session] supplies the content inputs (search text, category);
     * [source] is what the state's [LauncherUiState.session] reads (the ViewModel passes its
     * live session, so session-only changes don't need a new state).
     */
    fun build(
        library: LibraryInputs,
        env: EnvironmentInputs,
        session: SessionState,
        source: SessionSource = FixedSession(session),
    ): LauncherUiState {
        val org = library.organization
        val settings = library.settings
        val hidden = org.hidden

        val allApps = sortedMemo.get(library.catalog) {
            sortCount++
            AppSorting.alphabetical(library.catalog.orEmpty())
        }
        val byKey = byKeyMemo.get(allApps) { allApps.associateBy { it.key } }
        fun visible(key: AppKey?): AppEntry? = key?.takeIf { it !in hidden }?.let(byKey::get)

        val apps = appsMemo.get(allApps to hidden) { allApps.filter { it.key !in hidden } }

        val folders = foldersMemo.get(Triple(org.folders, allApps, hidden)) {
            org.folders.mapValues { (_, folder) -> FolderUi(folder, folder.apps.mapNotNull(::visible)) }
        }
        val homeTiles = homeMemo.get(Triple(org.homeItems, folders, apps)) {
            org.homeItems.mapNotNull { item ->
                when (item) {
                    is HomeItem.App -> visible(item.key)?.let(HomeTile::App)
                    is HomeItem.FolderRef -> folders[item.folderId]?.let(HomeTile::FolderTile)
                }
            }
        }
        val dock = dockMemo.get(Triple(org.dock, allApps, hidden)) {
            List(Organization.DOCK_SLOTS) { i -> visible(org.dock.getOrNull(i)) }
        }

        val categories = categoriesMemo.get(org.categories) {
            org.categories.sortedWith(compareBy({ it.position }, { it.id }))
        }
        val active = categories.firstOrNull { it.id == session.activeCategoryId } ?: categories.firstOrNull { it.isAll }
        val activeId = active?.takeUnless { it.isAll }?.id
        val members = activeId?.let { org.categoryMembers[it].orEmpty() }
        val categoryApps = categoryAppsMemo.get(Triple(apps, activeId, members)) {
            if (members == null) apps else apps.filter { it.key in members }
        }
        val drawerApps = drawerMemo.get(categoryApps to session.searchText) {
            SearchMatcher.filter(categoryApps, session.searchText)
        }

        val recents = recentsMemo.get(Triple(org.recents, apps, settings.historyEnabled)) {
            if (settings.historyEnabled) {
                org.recents.mapNotNull { visible(it.key) }.take(RecentHistory.MAX_ENTRIES)
            } else {
                emptyList()
            }
        }

        val controllerConnected = env.controllers.isNotEmpty()
        val mode = env.window?.let {
            ModeResolver.resolve(it.widthDp, it.heightDp, controllerConnected, settings.handheldAppearance)
        } ?: LauncherMode.PHONE

        return stabilize(
            LauncherUiState(
                loading = library.catalog == null,
                mode = mode,
                apps = apps,
                allApps = allApps,
                drawerApps = drawerApps,
                homeTiles = homeTiles,
                dock = dock,
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
                message = env.message,
                storageError = env.storageError,
                sessionSource = source,
            ),
        )
    }

    /**
     * Keeps the previous instance of every field whose value is equal (persisted collections
     * arrive as new instances on every database emission), and the previous state itself
     * when nothing changed.
     */
    private fun stabilize(next: LauncherUiState): LauncherUiState {
        val prev = previous
        if (prev == null) {
            previous = next
            return next
        }
        val result = LauncherUiState(
            loading = next.loading,
            mode = next.mode,
            apps = keep(prev.apps, next.apps),
            allApps = keep(prev.allApps, next.allApps),
            drawerApps = keep(prev.drawerApps, next.drawerApps),
            homeTiles = keep(prev.homeTiles, next.homeTiles),
            dock = keep(prev.dock, next.dock),
            folders = keep(prev.folders, next.folders),
            categories = keep(prev.categories, next.categories),
            categoryMembers = keep(prev.categoryMembers, next.categoryMembers),
            categoryApps = keep(prev.categoryApps, next.categoryApps),
            hidden = keep(prev.hidden, next.hidden),
            recents = keep(prev.recents, next.recents),
            settings = keep(prev.settings, next.settings),
            controllers = keep(prev.controllers, next.controllers),
            otherProfilesPresent = next.otherProfilesPresent,
            isDefaultHome = next.isDefaultHome,
            message = next.message,
            storageError = next.storageError,
            sessionSource = keep(prev.sessionSource, next.sessionSource),
        )
        val stable = if (result == prev) prev else result
        previous = stable
        return stable
    }

    private fun <T> keep(prev: T, next: T): T = if (prev === next || prev == next) prev else next
}

/**
 * One memoised derivation: recomputes only when [key] changed (by identity, or by equality
 * unless [identity]), and keeps the previous result instance when the recomputed one is equal.
 */
internal class Memo<K, V>(private val identity: Boolean = false) {
    private var hasValue = false
    private var lastKey: K? = null
    private var lastValue: V? = null

    @Suppress("UNCHECKED_CAST")
    fun get(key: K, compute: () -> V): V {
        if (hasValue && (lastKey === key || (!identity && lastKey == key))) return lastValue as V
        val value = compute()
        val kept = if (hasValue && lastValue == value) lastValue as V else value
        lastKey = key
        lastValue = kept
        hasValue = true
        return kept
    }
}
