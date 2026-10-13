package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.model.RomLibrary

/** Shortcut metadata only; Android's launch handle stays in the app catalog/view model. */
data class SearchShortcut(val app: AppEntry, val id: String, val label: String)

/** The heading a search result is listed under; the UI turns it into text. */
enum class SearchSection { APPS, GAMES, SHORTCUTS }

sealed interface SearchResult {
    val key: String
    val label: String
    val section: SearchSection

    data class App(val entry: AppEntry) : SearchResult {
        override val key = "drawer:${entry.key.encode()}"
        override val label get() = entry.label
        override val section = SearchSection.APPS
    }
    data class Game(val library: RomLibrary, val game: RomGame) : SearchResult {
        override val key = "search:rom:${library.id}:${game.uri}"
        override val label get() = game.title
        override val section = SearchSection.GAMES
    }
    data class Shortcut(val shortcut: SearchShortcut) : SearchResult {
        override val key = "search:shortcut:${shortcut.app.key.encode()}:${shortcut.id}"
        override val label get() = shortcut.label
        override val section = SearchSection.SHORTCUTS
    }
}

/** Offline search. The caller supplies the visible/category-scoped apps and eligible ROM tabs. */
object RicherSearch {
    /** Convenience for one-shot callers. Interactive search retains [Index] between keystrokes. */
    fun results(apps: List<AppEntry>, libraries: List<RomLibrary>, shortcuts: List<SearchShortcut>, query: String): List<SearchResult> =
        Index(apps, libraries, shortcuts).results(query)

    /** Immutable source snapshot: normalize once, rather than for every typed character or sort comparison. */
    class Index(apps: List<AppEntry>, libraries: List<RomLibrary>, shortcuts: List<SearchShortcut>) {
        private data class IndexedApp(val result: SearchResult.App, val label: String, val packageName: String)
        private data class IndexedGame(val result: SearchResult.Game, val title: String, val fileName: String, val libraryName: String)
        private data class IndexedShortcut(val result: SearchResult.Shortcut, val label: String, val appLabel: String)
        private data class Ranked<T>(val entry: T, val rank: Int)

        private val indexedApps = apps.map { IndexedApp(SearchResult.App(it), SearchMatcher.normalize(it.label), SearchMatcher.normalize(it.packageName)) }
        private val indexedGames = libraries.flatMap { library ->
            val libraryName = SearchMatcher.normalize(library.name)
            library.games.map { game -> IndexedGame(SearchResult.Game(library, game), SearchMatcher.normalize(game.title), SearchMatcher.normalize(game.fileName), libraryName) }
        }
        private val indexedShortcuts: List<IndexedShortcut>

        init {
            val visibleKeys = apps.mapTo(HashSet()) { it.key }
            indexedShortcuts = shortcuts.asSequence().filter { it.app.key in visibleKeys }.map {
                IndexedShortcut(SearchResult.Shortcut(it), SearchMatcher.normalize(it.label), SearchMatcher.normalize(it.app.label))
            }.toList()
        }

        fun results(query: String): List<SearchResult> {
            val q = SearchMatcher.normalize(query)
            if (q.isEmpty()) return emptyList()
            val apps = Array(4) { ArrayList<SearchResult.App>() }
            for (entry in indexedApps) {
                SearchMatcher.normalizedAppRank(entry.label, entry.packageName, q)?.let { apps[it].add(entry.result) }
            }
            val games = indexedGames.mapNotNull { entry ->
                val rank = SearchMatcher.normalizedLabelRank(entry.title, q)
                    ?: SearchMatcher.normalizedLabelRank(entry.fileName, q)?.plus(3)
                    ?: SearchMatcher.normalizedLabelRank(entry.libraryName, q)?.plus(6)
                rank?.let { Ranked(entry, it) }
            }.distinctBy { it.entry.result.key }.sortedWith(compareBy({ it.rank }, { it.entry.title }, { it.entry.result.library.name }, { it.entry.result.key }))
            val actions = indexedShortcuts.mapNotNull { entry ->
                val rank = SearchMatcher.normalizedLabelRank(entry.label, q)
                    ?: SearchMatcher.normalizedLabelRank(entry.appLabel, q)?.plus(3)
                rank?.let { Ranked(entry, it) }
            }.distinctBy { it.entry.result.key }.sortedWith(compareBy({ it.rank }, { it.entry.label }, { it.entry.result.shortcut.app.label }, { it.entry.result.key }))
            return buildList {
                for (bucket in apps) addAll(bucket)
                for (game in games) add(game.entry.result)
                for (action in actions) add(action.entry.result)
            }
        }
    }
}
