package dev.pluto.launcher.domain

import dev.pluto.launcher.model.*
import org.junit.Assert.*
import org.junit.Test

class RicherSearchTest {
    private val browser = AppEntry(AppKey("org.example.browser/.Main", 0), "Browser")
    private val hidden = AppEntry(AppKey("org.example.hidden/.Main", 0), "Hidden browser")
    private fun game(uri: String, title: String, file: String = "$title.cue") = RomGame(uri, file, title, 0)
    private fun library(id: Long, vararg games: RomGame) = RomLibrary(id, "PlayStation 1", "content://tree/$id", "emulator", games = games.toList())

    @Test fun blankSearchDoesNotReplaceNormalBrowsing() {
        assertTrue(RicherSearch.results(listOf(browser), listOf(library(1, game("a", "Spyro"))), emptyList(), "  ").isEmpty())
    }

    @Test fun matchesAccentsAndRanksGameTitleBeforeFilenameOrShelf() {
        val games = listOf(library(1, game("a", "Spyro Returns"), game("b", "The Spyro Adventure"),
            game("c", "Other game", "Spyro (Europe).cue"), game("d", "Spyrø")))
        val results = RicherSearch.results(emptyList(), games, emptyList(), "SPYRO").map { it.label }
        assertEquals(listOf("Spyrø", "Spyro Returns", "The Spyro Adventure", "Other game"), results)
        assertEquals(4, RicherSearch.results(emptyList(), games, emptyList(), "playstation").size)
    }

    @Test fun separatesDuplicateTitlesAcrossLibrariesAndDeduplicatesDocumentsWithinOne() {
        val same = game("content://disc", "Spyro")
        val results = RicherSearch.results(emptyList(), listOf(library(1, same, same), library(2, same)), emptyList(), "spyro")
        assertEquals(2, results.size)
        assertEquals(2, results.map { it.key }.toSet().size)
    }

    @Test fun scopedAppsExcludeHiddenOrOtherCategoryShortcuts() {
        val actions = listOf(SearchShortcut(browser, "new", "New tab"), SearchShortcut(hidden, "secret", "New tab"))
        val results = RicherSearch.results(listOf(browser), emptyList(), actions, "new")
        assertEquals(1, results.size)
        assertEquals(browser.key, (results.single() as SearchResult.Shortcut).shortcut.app.key)
        assertTrue(RicherSearch.results(emptyList(), emptyList(), actions, "new").isEmpty())
    }

    @Test fun shortcutLabelRanksBeforeParentAppMatchAndDuplicatesCollapse() {
        val actions = listOf(SearchShortcut(browser, "new", "New tab"), SearchShortcut(browser, "exact", "Browser settings"),
            SearchShortcut(browser, "new", "New tab"))
        val results = RicherSearch.results(listOf(browser), emptyList(), actions, "browser")
        assertEquals(listOf("Browser", "Browser settings", "New tab"), results.map { it.label })
        assertEquals(listOf(SearchSection.APPS, SearchSection.SHORTCUTS, SearchSection.SHORTCUTS), results.map { it.section })
    }

    @Test fun appPackageMatchingIsPreserved() {
        assertEquals(browser, (RicherSearch.results(listOf(browser), emptyList(), emptyList(), "example").single() as SearchResult.App).entry)
        assertTrue(RicherSearch.results(listOf(browser), emptyList(), emptyList(), "ample").isEmpty())
    }

    @Test fun sameShortcutIdInDifferentProfilesHasDistinctIdentity() {
        val work = browser.copy(key = browser.key.copy(userSerial = 10))
        val results = RicherSearch.results(listOf(browser, work), emptyList(),
            listOf(SearchShortcut(browser, "new", "New tab"), SearchShortcut(work, "new", "New tab")), "new")
        assertEquals(2, results.map { it.key }.toSet().size)
    }
    @Test fun retainedIndexSupportsRepeatedQueriesAndPreservesSourceScope() {
        val shelves = listOf(library(1, game("a", "Spyrø"), game("b", "Crash Bandicoot")))
        val actions = listOf(SearchShortcut(browser, "new", "New tab"), SearchShortcut(hidden, "new", "New tab"))
        val index = RicherSearch.Index(listOf(browser), shelves, actions)
        assertEquals(listOf("Spyrø"), index.results("spyro").map { it.label })
        assertEquals(listOf("Crash Bandicoot"), index.results("crash").map { it.label })
        assertEquals(listOf("New tab"), index.results("new").map { it.label })
        assertEquals(listOf("Browser", "New tab"), index.results("browser").map { it.label })
        assertTrue(index.results(" ").isEmpty())
    }

    @Test fun duplicateDocumentsAreDeduplicatedAfterMatching() {
        val index = RicherSearch.Index(emptyList(), listOf(library(1,
            game("same", "Unrelated", "other.cue"), game("same", "Spyro", "spyro.cue"))), emptyList())
        assertEquals(listOf("Spyro"), index.results("spyro").map { it.label })
    }
}
