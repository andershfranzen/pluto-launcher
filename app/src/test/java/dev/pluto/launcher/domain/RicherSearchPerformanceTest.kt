package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.model.RomLibrary
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

/** Reproducible host-JVM workload; timings are diagnostic, never correctness thresholds. */
class RicherSearchPerformanceTest {
    @Test fun retainedIndexMatchesPreviousAlgorithmOnLargeMixedWorkload() {
        val apps = (0 until 200).map { AppEntry(AppKey("org.example.app$it/.Main", 0), "App $it") }
        val libraries = (0 until 10).map { shelf ->
            RomLibrary(shelf.toLong(), "Console $shelf", "content://tree/$shelf", "emulator", games = (0 until 200).map { game ->
                val title = when (game % 4) {
                    0 -> "Spyrø Adventure $game"
                    1 -> "Crash Bandicoot $game"
                    2 -> "The Legend of Zelda $game"
                    else -> "Racing Championship $game"
                }
                RomGame("content://game/$shelf/$game", "$title (Europe).cue", title, 0)
            })
        }
        val shortcuts = apps.flatMap { listOf(SearchShortcut(it, "new", "New item"), SearchShortcut(it, "settings", "Settings")) }
        val queries = listOf("spy", "spyro", "crash", "legend", "console", "europe", "new", "app", "example", "zzz")
        val index = RicherSearch.Index(apps, libraries, shortcuts)
        for (query in queries) assertEquals("Query $query", previousResults(apps, libraries, shortcuts, query), index.results(query))
        repeat(3) {
            for (query in queries) { previousResults(apps, libraries, shortcuts, query); index.results(query) }
        }
        val previousTimes = ArrayList<Long>()
        val indexedTimes = ArrayList<Long>()
        repeat(5) { iteration ->
            // Alternate order to reduce systematic warmup/GC bias.
            fun previous() { previousTimes += measureNanoTime { for (q in queries) previousResults(apps, libraries, shortcuts, q) } }
            fun indexed() { indexedTimes += measureNanoTime { for (q in queries) index.results(q) } }
            if (iteration % 2 == 0) { previous(); indexed() } else { indexed(); previous() }
        }
        fun List<Long>.medianMs() = sorted()[size / 2] / 1_000_000.0
        println("SEARCH_BENCHMARK: 200 apps, 2000 ROMs, 400 shortcuts, ${queries.size} mixed queries; 3 warmups, 5 alternating measurements")
        println("SEARCH_BENCHMARK: previous median=${previousTimes.medianMs()}ms; retained index median=${indexedTimes.medianMs()}ms; source preparation excluded")
        println("SEARCH_BENCHMARK: fresh index preparation=${measureNanoTime { RicherSearch.Index(apps, libraries, shortcuts) } / 1_000_000.0}ms")
    }

    /** Pre-index implementation retained as an independent matching/ordering oracle. */
    private fun previousResults(apps: List<AppEntry>, libraries: List<RomLibrary>, shortcuts: List<SearchShortcut>, query: String): List<SearchResult> {
        val q = SearchMatcher.normalize(query)
        if (q.isEmpty()) return emptyList()
        val visibleKeys = apps.mapTo(HashSet()) { it.key }
        val games = libraries.flatMap { library ->
            library.games.mapNotNull { game ->
                val rank = SearchMatcher.labelRank(game.title, q)
                    ?: SearchMatcher.labelRank(game.fileName, q)?.plus(3)
                    ?: SearchMatcher.labelRank(library.name, q)?.plus(6)
                rank?.let { SearchResult.Game(library, game) to it }
            }
        }.distinctBy { it.first.key }
            .sortedWith(compareBy({ it.second }, { SearchMatcher.normalize(it.first.label) }, { it.first.library.name }, { it.first.key }))
        val actions = shortcuts.filter { it.app.key in visibleKeys }.mapNotNull { shortcut ->
            val rank = SearchMatcher.labelRank(shortcut.label, q)
                ?: SearchMatcher.labelRank(shortcut.app.label, q)?.plus(3)
            rank?.let { SearchResult.Shortcut(shortcut) to it }
        }.distinctBy { it.first.key }
            .sortedWith(compareBy({ it.second }, { SearchMatcher.normalize(it.first.label) }, { it.first.shortcut.app.label }, { it.first.key }))
        return SearchMatcher.filter(apps, query).map(SearchResult::App) + games.map { it.first } + actions.map { it.first }
    }
}
