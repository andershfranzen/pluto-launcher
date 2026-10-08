package dev.pluto.launcher.ui

import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.model.ControllerInfo
import dev.pluto.launcher.model.Folder
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.LauncherMode

/** A layer stacked above the home surface. Back pops the top layer. */
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
 */
data class SessionState(
    val layers: List<Layer> = emptyList(),
    val searchText: String = "",
    /** True when the search field is showing in the drawer. */
    val searchActive: Boolean = false,
    /** Active category in drawer / handheld browsing; null = the ALL category. */
    val activeCategoryId: Long? = null,
    /** Identity of the selected/focused app (controller selection), kept across reflow. */
    val selectedApp: AppKey? = null,
    /** Identity of the focused non-app control (e.g. "folder:3", "dock:2", "settings:theme"). */
    val focusedControlId: String? = null,
    /** Logical scroll anchors: first visible item identity per scrollable surface. */
    val scrollAnchors: Map<String, String> = emptyMap(),
    /** Edit mode: the item currently picked up for moving (HomeItem.id / "dock:<slot>" / app key). */
    val editSelection: String? = null,
    // --- Handheld shelves
    /** Active Handheld (console) shelf, ShelfId.key ("recent", "fav", "cat:<id>"); null = default landing shelf. */
    val handheldShelf: String? = null,
    // --- end Handheld shelves
) {
    val topLayer: Layer? get() = layers.lastOrNull()
}

data class FolderUi(val folder: Folder, val apps: List<AppEntry>)

sealed interface HomeTile {
    val id: String

    data class App(val entry: AppEntry) : HomeTile {
        override val id: String get() = HomeItem.App(entry.key).id
    }

    data class FolderTile(val folder: FolderUi) : HomeTile {
        override val id: String get() = HomeItem.FolderRef(folder.folder.id).id
    }
}

data class UserMessage(val id: Long, val text: String)

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
    val dock: List<AppEntry?> = List(5) { null },
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
    val session: SessionState = SessionState(),
    val message: UserMessage? = null,
    /**
     * Non-null when the launcher's database or settings could not be read (e.g. a missing
     * migration). The UI shows a recovery screen instead of crashing; data is left untouched.
     */
    val storageError: String? = null,
) {
    val controllerConnected: Boolean get() = controllers.isNotEmpty()
    val activeCategory: Category?
        get() = categories.firstOrNull { it.id == session.activeCategoryId } ?: categories.firstOrNull { it.isAll }
    fun isPinned(key: AppKey): Boolean =
        homeTiles.any { (it is HomeTile.App && it.entry.key == key) || (it is HomeTile.FolderTile && it.folder.folder.apps.contains(key)) }
    fun isInDock(key: AppKey): Boolean = dock.any { it?.key == key }
    fun folderOf(key: AppKey): FolderUi? = folders.values.firstOrNull { key in it.folder.apps }
}
