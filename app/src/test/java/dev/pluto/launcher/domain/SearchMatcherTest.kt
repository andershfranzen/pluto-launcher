package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    private fun app(label: String, pkg: String = "com.example." + label.lowercase().filter { it.isLetter() }) =
        AppEntry(AppKey("$pkg/.Main", 0), label)

    @Test
    fun normalizeLowercasesStripsAccentsAndCollapsesWhitespace() {
        assertEquals("cafe", SearchMatcher.normalize("Café"))
        assertEquals("creme brulee", SearchMatcher.normalize("  Crème   Brûlée \t"))
        assertEquals("naive", SearchMatcher.normalize("NAÏVE"))
        assertEquals("", SearchMatcher.normalize("   "))
    }

    @Test
    fun normalizeHandlesDecomposedInput() {
        // "e" followed by a combining acute accent.
        assertEquals("cafe", SearchMatcher.normalize("Café"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val chrome = app("Chrome")
        assertTrue(SearchMatcher.matches(chrome, "CHROME"))
        assertTrue(SearchMatcher.matches(chrome, "chr"))
        assertTrue(SearchMatcher.matches(chrome, "RoMe"))
        assertFalse(SearchMatcher.matches(chrome, "firefox"))
    }

    @Test
    fun matchingIsAccentInsensitiveBothWays() {
        assertTrue(SearchMatcher.matches(app("Café"), "cafe"))
        assertTrue(SearchMatcher.matches(app("Cafe"), "café"))
        assertTrue(SearchMatcher.matches(app("Café"), "CAFÉ"))
    }

    @Test
    fun matchesPackageName() {
        val app = AppEntry(AppKey("org.mozilla.firefox/.App", 0), "Browser")
        assertTrue(SearchMatcher.matches(app, "mozilla"))
        assertTrue(SearchMatcher.matches(app, "org.mozilla"))
        // Activity class name is not part of the package.
        assertFalse(SearchMatcher.matches(app, ".App"))
    }

    @Test
    fun blankQueryMatchesEverythingAndReturnsSameList() {
        val apps = listOf(app("B"), app("A"))
        assertTrue(SearchMatcher.matches(apps[0], ""))
        assertTrue(SearchMatcher.matches(apps[0], "   "))
        assertSame(apps, SearchMatcher.filter(apps, ""))
        assertSame(apps, SearchMatcher.filter(apps, "  "))
    }

    @Test
    fun queryWhitespaceIsNormalized() {
        assertTrue(SearchMatcher.matches(app("Google Play Store"), "  play   store "))
    }

    @Test
    fun ranksPrefixThenWordPrefixThenSubstringThenPackage() {
        val packageOnly = AppEntry(AppKey("com.play.games/.Main", 0), "Arcade")
        val substring = app("Display")
        val wordPrefix = app("Google Play")
        val prefix = app("Playground")
        val unrelated = app("Camera")
        // Input is alphabetical; ranking reorders by match quality.
        val input = listOf(packageOnly, unrelated, substring, wordPrefix, prefix)
        assertEquals(listOf(prefix, wordPrefix, substring, packageOnly), SearchMatcher.filter(input, "play"))
    }

    @Test
    fun wordPrefixAfterPunctuation() {
        val dashed = app("Tic-Tac-Toe")
        val inner = app("Stacks")
        assertEquals(listOf(dashed, inner), SearchMatcher.filter(listOf(inner, dashed), "tac"))
    }

    @Test
    fun tiesKeepInputOrder() {
        val a = app("Alpha Mail")
        val b = app("Beta Mail")
        val c = app("Gamma Mail")
        assertEquals(listOf(a, b, c), SearchMatcher.filter(listOf(a, b, c), "mail"))
        assertEquals(listOf(c, a, b), SearchMatcher.filter(listOf(c, a, b), "mail"))
    }

    @Test
    fun noMatchesGivesEmptyList() {
        assertTrue(SearchMatcher.filter(listOf(app("Clock"), app("Maps")), "zzz").isEmpty())
    }

    @Test
    fun labelMatchBeatsPackageMatchEvenWhenBothMatch() {
        val both = AppEntry(AppKey("com.maps.app/.Main", 0), "Old Maps")
        val packageOnly = AppEntry(AppKey("com.maps.lite/.Main", 0), "Navigator")
        assertEquals(listOf(both, packageOnly), SearchMatcher.filter(listOf(packageOnly, both), "maps"))
    }
}
