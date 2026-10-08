package dev.pluto.launcher.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.model.ControllerInfo
import dev.pluto.launcher.model.Folder
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.LauncherMode

/** A layer stacked above the home surface. Back pops the top layer. */
@Immutable
sealed interface Layer {
    data object Drawer : Layer
    data class FolderLayer(val folderId: Long) : Layer
    data class AppActions(val key: AppKey) : Layer
    data class CategoryMembership(val key: AppKey) : Layer
    data class MoveToFolder(val key: AppKey) : Layer
    data object Edit : Layer
    data object Settings : Layer
    data object HiddenApps : Layer
    data object Categories : Layer
    data object ControllerSettings : Layer
    data object Onboarding : Layer

    companion object {
        fun encode(layer: Layer): String = when (layer) {
            Drawer -> "drawer"
            is FolderLayer -> "folder:${layer.folderId}"
            is AppActions -> "actions:${layer.key.encode()}"
            is CategoryMembership -> "membership:${layer.key.encode()}"
            is MoveToFolder -> "movefolder:${layer.key.encode()}"
            Edit -> "edit"
            Settings -> "settings"
            HiddenApps -> "hidden"
            Categories -> "categories"
            ControllerSettings -> "controller"
            Onboarding -> "onboarding"
        }

        fun decode(value: String): Layer? {
            val arg = value.substringAfter(':', "")
            return when (value.substringBefore(':')) {
                "drawer" -> Drawer
                "folder" -> arg.toLongOrNull()?.let(::FolderLayer)
                "actions" -> AppKey.decode(arg)?.let(::AppActions)
                "membership" -> AppKey.decode(arg)?.let(::CategoryMembership)
                "movefolder" -> AppKey.decode(arg)?.let(::MoveToFolder)
                "edit" -> Edit
                "settings" -> Settings
                "hidden" -> HiddenApps
                "categories" -> Categories
                "controller" -> ControllerSettings
                "onboarding" -> Onboarding
                else -> null
            }
        }
    }
}

/**
 * Transient navigation state. Survives rotation (ViewModel) and process death
 * (SavedStateHandle) but is not part of persisted organisation.
 *
 * Only what changes the structure of the screen is part of the value (layers, search,
 * category, edit selection). The high-frequency memory of where the user is (selected app,
 * focused control, scroll anchors) lives in [memory]: it is written on every focus move or
 * scrolled line and must never rebuild or re-emit the UI state (see [SessionMemory]).
 */
@Stable
data class SessionState(
    val layers: List<Layer> = emptyList(),
    val searchText: String = "",
    /** True when the search field is showing in the drawer. */
    val searchActive: Boolean = false,
    /** Active category in drawer / handheld browsing; null = the ALL category. */
    val activeCategoryId: Long? = null,
    /** Edit mode: the item currently picked up for moving (HomeItem.id / "dock:<slot>" / app key). */
    val editSelection: String? = null,
    /** Selection, focus and scroll memory: one shared instance per ViewModel (compared by identity). */
    val memory: SessionMemory = SessionMemory(),
) {
    val topLayer: Layer? get() = layers.lastOrNull()

    /** Identity of the selected/focused app (controller selection), kept across reflow. Snapshot state. */
    val selectedApp: AppKey? get() = memory.selectedApp

    /** Identity of the focused non-app control (e.g. "folder:3", "dock:2", "settings:theme"). Not observed. */
    val focusedControlId: String? get() = memory.focusedControlId

    /** Logical scroll anchors: first visible item identity per scrollable surface. Not observed. */
    val scrollAnchors: Map<String, String> get() = memory.scrollAnchors

    /** True when [other] has the same inputs for the derived lists (drawer results, category apps). */
    fun sameContentInputs(other: SessionState): Boolean =
        searchText == other.searchText && activeCategoryId == other.activeCategoryId
}

/**
 * Where the user is, remembered by identity across reflow, rotation and process death,
 * outside the UI state: a D-pad step or a scrolled line costs a field write, not a state
 * rebuild and a recomposition of every surface.
 *
 * - [selectedApp] is snapshot state (Handheld shows the selected app's name), so only the
 *   composables that read it recompose.
 * - [focusedControlId] and [scrollAnchors] are plain fields, read when a surface is
 *   (re)created to restore focus and position (after rotation, a closed layer, ...).
 *
 * Main thread only.
 */
@Stable
class SessionMemory(
    selectedApp: AppKey? = null,
    focusedControlId: String? = null,
    scrollAnchors: Map<String, String> = emptyMap(),
) {
    var selectedApp: AppKey? by mutableStateOf(selectedApp)

    var focusedControlId: String? = focusedControlId

    private val anchors = HashMap(scrollAnchors)

    /** Read-only view of the anchors (surface -> item key). */
    val scrollAnchors: Map<String, String> get() = anchors

    /** Records (or, with null, clears) the anchor of [surface]. Returns true when it changed. */
    fun setScrollAnchor(surface: String, itemId: String?): Boolean =
        if (itemId == null) anchors.remove(surface) != null else anchors.put(surface, itemId) != itemId
}

/**
 * Where [LauncherUiState.session] is read from. The ViewModel publishes a [LiveSession]: a
 * session change then needs no new UI state, and only the composables that read the
 * session recompose. [FixedSession] pins a session for a held copy (a leaving layer, the
 * parked drawer), see [LauncherUiState.frozen].
 */
@Stable
interface SessionSource {
    val value: SessionState
}

/** A session that never changes. */
@Immutable
data class FixedSession(override val value: SessionState) : SessionSource

/** The ViewModel's published session: snapshot state, observed where it is read. */
@Stable
class LiveSession(initial: SessionState) : SessionSource {
    override var value: SessionState by mutableStateOf(initial)
        internal set
}

@Immutable
data class FolderUi(val folder: Folder, val apps: List<AppEntry>)

@Immutable
sealed interface HomeTile {
    val id: String

    data class App(val entry: AppEntry) : HomeTile {
        override val id: String get() = HomeItem.App(entry.key).id
    }

    data class FolderTile(val folder: FolderUi) : HomeTile {
        override val id: String get() = HomeItem.FolderRef(folder.folder.id).id
    }
}

@Immutable
data class UserMessage(val id: Long, val text: String)

/**
 * Everything the launcher shows, derived from the app catalog, organisation, settings,
 * environment and session. Lists keep their identity across emissions while their content
 * is unchanged (LauncherStateBuilder), so equality is cheap and composables taking a state
 * skip when nothing they show changed.
 *
 * The session is read through [sessionSource] rather than held as a value, so session-only
 * changes (layers, search activation, edit selection) create no new state at all.
 */
@Stable
data class LauncherUiState(
    /** True until the app catalog and organisation have loaded once. */
    val loading: Boolean = true,
    val mode: LauncherMode = LauncherMode.PHONE,
    /** Visible (not hidden), available apps, alphabetical. */
    val apps: List<AppEntry> = emptyList(),
    /** Every available app including hidden ones, alphabetical. */
    val allApps: List<AppEntry> = emptyList(),
    /** Drawer contents after category filter and search, ranked. */
    val drawerApps: List<AppEntry> = emptyList(),
    /** Home grid, available entries only; folders with no available apps are still shown (editable). */
    val homeTiles: List<HomeTile> = emptyList(),
    /** Five slots; null where empty or app unavailable. */
    val dock: List<AppEntry?> = EmptyDock,
    val folders: Map<Long, FolderUi> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val categoryMembers: Map<Long, Set<AppKey>> = emptyMap(),
    /** Apps in the active category (ALL = all visible apps), alphabetical. Used by handheld browsing. */
    val categoryApps: List<AppEntry> = emptyList(),
    val hidden: Set<AppKey> = emptySet(),
    /** Visible, available recent launches, most recent first (max 12). */
    val recents: List<AppEntry> = emptyList(),
    val settings: LauncherSettings = LauncherSettings(),
    val controllers: List<ControllerInfo> = emptyList(),
    val otherProfilesPresent: Boolean = false,
    val isDefaultHome: Boolean = false,
    val message: UserMessage? = null,
    /**
     * Non-null when the launcher's database or settings could not be read (e.g. a missing
     * migration). The UI shows a recovery screen instead of crashing; data is left untouched.
     */
    val storageError: String? = null,
    /** Where [session] is read from (a live source in the ViewModel's state). */
    val sessionSource: SessionSource = EmptySession,
) {
    /** Navigation state. Snapshot state for the ViewModel's state: readers recompose when it changes. */
    val session: SessionState get() = sessionSource.value

    val controllerConnected: Boolean get() = controllers.isNotEmpty()
    val activeCategory: Category?
        get() = categories.firstOrNull { it.id == session.activeCategoryId } ?: categories.firstOrNull { it.isAll }
    fun isPinned(key: AppKey): Boolean =
        homeTiles.any { (it is HomeTile.App && it.entry.key == key) || (it is HomeTile.FolderTile && it.folder.folder.apps.contains(key)) }
    fun isInDock(key: AppKey): Boolean = dock.any { it?.key == key }
    fun folderOf(key: AppKey): FolderUi? = folders.values.firstOrNull { key in it.folder.apps }

    /**
     * A copy whose session no longer follows the live one, for content that must keep showing
     * what it showed (a leaving layer, the parked drawer) without recomposing on session changes.
     */
    fun frozen(): LauncherUiState =
        if (sessionSource is FixedSession) this else copy(sessionSource = FixedSession(session))

    /** A copy whose session is [session] (tests, previews, held copies). */
    fun withSession(session: SessionState): LauncherUiState = copy(sessionSource = FixedSession(session))
}

private val EmptySession = FixedSession(SessionState())
private val EmptyDock: List<AppEntry?> = List(5) { null }
