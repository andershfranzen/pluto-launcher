package dev.pluto.launcher.model

/**
 * Stable identity of a launchable activity: flattened component name plus the
 * owning user profile's serial number. Two activities in one package, or one
 * activity in two profiles, never collide.
 */
data class AppKey(val component: String, val userSerial: Long) {
    val packageName: String get() = component.substringBefore('/')

    /** Compact string form used for persistence and saved state. */
    fun encode(): String = "$userSerial|$component"

    override fun toString(): String = encode()

    companion object {
        fun decode(value: String): AppKey? {
            val sep = value.indexOf('|')
            if (sep <= 0 || sep == value.lastIndex) return null
            val serial = value.substring(0, sep).toLongOrNull() ?: return null
            val component = value.substring(sep + 1)
            if ('/' !in component) return null
            return AppKey(component, serial)
        }
    }
}

/** A launchable activity as currently reported by the system. */
data class AppEntry(
    val key: AppKey,
    val label: String,
    /** False while the package is suspended or otherwise not launchable. */
    val isEnabled: Boolean = true,
) {
    val packageName: String get() = key.packageName
}

enum class LauncherMode { PHONE, LANDSCAPE, HANDHELD }

enum class RotationPreference { FOLLOW_SYSTEM, PORTRAIT, LANDSCAPE, LANDSCAPE_WHEN_CONTROLLER }

enum class ThemePreference { SYSTEM, LIGHT, DARK }

/**
 * How Handheld presentation is chosen while the launcher window is landscape.
 * Portrait always uses Phone mode.
 */
enum class HandheldAppearance {
    /** Handheld when a gamepad is connected, Landscape otherwise. */
    AUTOMATIC,
    /** Always Handheld in landscape, with or without a controller. */
    ALWAYS,
    /** Never switch to Handheld automatically; landscape stays the touch layout. */
    NEVER,
}

/** Built-in categories. ALL is virtual (every visible app); others hold explicit members. */
enum class BuiltInCategory { ALL, GAMES, TOOLS }

data class Category(
    val id: Long,
    val name: String,
    val builtIn: BuiltInCategory?,
    val position: Int,
) {
    val isAll: Boolean get() = builtIn == BuiltInCategory.ALL
}

data class Folder(
    val id: Long,
    val name: String,
    val apps: List<AppKey>,
)

/** One entry in the home favourites grid. */
sealed interface HomeItem {
    /** Stable id used for persistence, ordering and focus identity. */
    val id: String

    data class App(val key: AppKey) : HomeItem {
        override val id: String get() = APP_PREFIX + key.encode()
    }

    data class FolderRef(val folderId: Long) : HomeItem {
        override val id: String get() = FOLDER_PREFIX + folderId
    }

    /** An app widget hosted on the grid; its provider and size live in [Organization.widgets]. */
    data class Widget(val appWidgetId: Int) : HomeItem {
        override val id: String get() = WIDGET_PREFIX + appWidgetId
    }

    companion object {
        const val APP_PREFIX = "app:"
        const val FOLDER_PREFIX = "folder:"
        const val WIDGET_PREFIX = "widget:"

        fun decode(id: String): HomeItem? = when {
            id.startsWith(APP_PREFIX) -> AppKey.decode(id.removePrefix(APP_PREFIX))?.let(::App)
            id.startsWith(FOLDER_PREFIX) -> id.removePrefix(FOLDER_PREFIX).toLongOrNull()?.let(::FolderRef)
            id.startsWith(WIDGET_PREFIX) -> id.removePrefix(WIDGET_PREFIX).toIntOrNull()?.let(::Widget)
            else -> null
        }
    }
}

/** A widget on Home: the host's id, the provider (flattened ComponentName) and its height in grid rows. */
data class HomeWidget(val appWidgetId: Int, val provider: String, val rows: Int) {
    companion object {
        const val MIN_ROWS = 1
        const val MAX_ROWS = 5
    }
}

data class RecentLaunch(val key: AppKey, val launchedAtMillis: Long)

/** Explicit reorder operations used by Edit mode (touch and controller alike). */
sealed interface ReorderOp<out T> {
    data object Up : ReorderOp<Nothing>
    data object Down : ReorderOp<Nothing>
    data class Before<T>(val target: T) : ReorderOp<T>
    data class After<T>(val target: T) : ReorderOp<T>
}

/** Everything the user has organised, as persisted. References may point at uninstalled apps. */
data class Organization(
    val homeItems: List<HomeItem> = emptyList(),
    /** Exactly [DOCK_SLOTS] entries; null means an empty slot. */
    val dock: List<AppKey?> = List(DOCK_SLOTS) { null },
    val folders: Map<Long, Folder> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val categoryMembers: Map<Long, Set<AppKey>> = emptyMap(),
    val hidden: Set<AppKey> = emptySet(),
    /** Most recent first, at most 12 entries. */
    val recents: List<RecentLaunch> = emptyList(),
    /** Widgets on Home, by app widget id. */
    val widgets: Map<Int, HomeWidget> = emptyMap(),
) {
    companion object {
        const val DOCK_SLOTS = 5
    }
}

/** A connected input device recognised as a game controller. */
data class ControllerInfo(
    val deviceId: Int,
    val name: String,
    /** InputDevice.descriptor; stable across reconnects on most devices. */
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val sources: Int,
)
