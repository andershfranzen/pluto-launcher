package dev.pluto.launcher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollSectionsTest {
    @Test
    fun lettersFollowListOrderWithFirstIndices() {
        val s = ScrollSections.of(listOf("Amazon", "Apps", "Bank", "Calendar", "Camera", "Zoom"))
        assertEquals(listOf("A", "B", "C", "Z"), s.letters)
        assertEquals(0, s.firstItemOf(0))
        assertEquals(2, s.firstItemOf(1))
        assertEquals(3, s.firstItemOf(2))
        assertEquals(5, s.firstItemOf(3))
        assertEquals(2, s.sectionOf(4))
        assertEquals(-1, s.sectionOf(6))
    }

    @Test
    fun accentsDigitsAndCaseAreNormalised() {
        assertEquals("E", ScrollSections.sectionLetter("Écran"))
        assertEquals("E", ScrollSections.sectionLetter("eBay"))
        assertEquals("#", ScrollSections.sectionLetter("1Password"))
        assertEquals("#", ScrollSections.sectionLetter(""))
        assertEquals("S", ScrollSections.sectionLetter("  Settings"))
    }

    @Test
    fun aLetterSeenAgainJoinsItsFirstSection() {
        // A collator may place "Éclair" after "Ezra"; both belong to E, which keeps one rail entry.
        val s = ScrollSections.of(listOf("Ezra", "Fun", "Éclair"))
        assertEquals(listOf("E", "F"), s.letters)
        assertEquals(0, s.sectionOf(2))
    }

    @Test
    fun outOfRangeSectionsAreClamped() {
        val s = ScrollSections.of(listOf("A", "B"))
        assertEquals(1, s.firstItemOf(5))
        assertEquals(0, s.firstItemOf(-1))
    }
}
