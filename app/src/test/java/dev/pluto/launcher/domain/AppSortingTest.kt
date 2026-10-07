package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppSortingTest {
    private fun app(label: String, component: String, serial: Long = 0) = AppEntry(AppKey(component, serial), label)

    private fun labels(apps: List<AppEntry>) = apps.map { it.label }

    @Test
    fun sortsCaseInsensitively() {
        val apps = listOf(app("zoom", "z/.A"), app("Alarm", "a/.A"), app("browser", "b/.A"), app("Camera", "c/.A"))
        assertEquals(listOf("Alarm", "browser", "Camera", "zoom"), labels(AppSorting.alphabetical(apps, Locale.ENGLISH)))
    }

    @Test
    fun accentsSortWithTheirBaseLetter() {
        val apps = listOf(app("Duo", "d/.A"), app("Éclair", "e/.A"), app("Ebook", "eb/.A"), app("Files", "f/.A"))
        assertEquals(listOf("Duo", "Ebook", "Éclair", "Files"), labels(AppSorting.alphabetical(apps, Locale.ENGLISH)))
    }

    @Test
    fun tiesBrokenByPackageThenComponentThenProfile() {
        val b = app("Notes", "com.b/.Main")
        val a2 = app("notes", "com.a/.Second")
        val a1 = app("NOTES", "com.a/.First")
        val a1Work = app("Notes", "com.a/.First", serial = 10)
        val sorted = AppSorting.alphabetical(listOf(b, a1Work, a2, a1), Locale.ENGLISH)
        assertEquals(listOf(a1, a1Work, a2, b), sorted)
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        val apps = listOf(app("  Zebra", "z/.A"), app("Apple", "a/.A"))
        assertEquals(listOf("Apple", "  Zebra"), labels(AppSorting.alphabetical(apps, Locale.ENGLISH)))
    }

    @Test
    fun emptyInput() {
        assertTrue(AppSorting.alphabetical(emptyList()).isEmpty())
    }

    @Test
    fun isStableAcrossInputOrders() {
        val apps = listOf(app("b", "p.b/.A"), app("A", "p.a/.A"), app("c", "p.c/.A"), app("a", "p.a2/.A"))
        val expected = AppSorting.alphabetical(apps, Locale.ENGLISH)
        assertEquals(expected, AppSorting.alphabetical(apps.reversed(), Locale.ENGLISH))
        assertEquals(expected, AppSorting.alphabetical(apps.toSet(), Locale.ENGLISH))
    }
}
