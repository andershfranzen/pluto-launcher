package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.BuiltInCategory

/**
 * Suggests apps for a built-in category so the console's "Add apps" picker can list likely
 * candidates first. Suggestions are only a sort order: the user still ticks every app
 * (spec: automatic assignment must always be correctable, so nothing is added on its own).
 */
object AppSuggestions {
    // android.content.pm.ApplicationInfo.CATEGORY_* (inlined for JVM tests).
    const val CATEGORY_GAME = 0
    const val CATEGORY_MAPS = 6
    const val CATEGORY_PRODUCTIVITY = 7
    const val CATEGORY_ACCESSIBILITY = 8

    /** What Android says about an app: its declared category (or -1) and the legacy game flag. */
    data class Traits(val category: Int, val isGame: Boolean)

    /** Words in a label or package name that mark everyday utilities. */
    private val TOOL_WORDS = listOf(
        "calculator", "clock", "alarm", "calendar", "files", "file manager", "notes", "recorder",
        "weather", "compass", "flashlight", "torch", "scanner", "translate", "settings", "camera",
        "contacts", "keep", "drive", "authenticator", "vpn",
    )

    /**
     * Apps to list first for [builtIn], in [apps] order. User categories and All apps get no
     * suggestions. [traits] may return null when the system has nothing to say about a package.
     */
    fun suggest(builtIn: BuiltInCategory?, apps: List<AppEntry>, traits: (String) -> Traits?): List<AppEntry> = when (builtIn) {
        BuiltInCategory.GAMES -> apps.filter { app ->
            traits(app.packageName)?.let { it.isGame || it.category == CATEGORY_GAME } == true
        }
        BuiltInCategory.TOOLS -> apps.filter { app ->
            val t = traits(app.packageName)
            val byCategory = t != null && t.category in setOf(CATEGORY_PRODUCTIVITY, CATEGORY_MAPS, CATEGORY_ACCESSIBILITY)
            val isGame = t != null && (t.isGame || t.category == CATEGORY_GAME)
            !isGame && (byCategory || looksLikeTool(app))
        }
        BuiltInCategory.ALL, null -> emptyList()
    }

    internal fun looksLikeTool(app: AppEntry): Boolean {
        val label = app.label.lowercase()
        val pkg = app.packageName.lowercase()
        return TOOL_WORDS.any { word -> label.contains(word) || pkg.contains(word.replace(" ", "")) }
    }
}
