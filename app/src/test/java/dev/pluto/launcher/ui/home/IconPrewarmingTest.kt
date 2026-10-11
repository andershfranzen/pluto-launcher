package dev.pluto.launcher.ui.home

import dev.pluto.launcher.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPrewarmingTest {
    @Test fun largeCatalogOnlyVisitsViewportAndLookAhead() {
        val visited = mutableListOf<Int>()
        val result = viewportIconKeys((0..9999).toList(), 500, 509, 64) {
            visited += it
            listOf(AppKey("pkg/$it", 0))
        }
        assertEquals((500..521).toList(), visited)
        assertEquals(22, result.size)
    }
    @Test fun budgetCapsExpandedFoldersAndDeduplicates() {
        val key = AppKey("pkg/shared", 0)
        val result = viewportIconKeys(listOf(1, 2, 3), 0, 2, 3) {
            listOf(key, AppKey("pkg/$it", 0))
        }
        assertEquals(listOf(key, AppKey("pkg/1", 0), AppKey("pkg/2", 0)), result)
        assertTrue(viewportIconKeys(listOf(1), 0, 0, 0) { error("No budget") }.isEmpty())
        assertTrue(viewportIconKeys(listOf(1), 5, 6, 3) { error("Stale viewport") }.isEmpty())
    }
}
