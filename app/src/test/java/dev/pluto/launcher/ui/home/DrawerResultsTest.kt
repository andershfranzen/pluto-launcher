package dev.pluto.launcher.ui.home

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerResultsTest {
    private fun app(name: String) = AppEntry(AppKey("pkg.$name/.Main", 0), name)

    @Test
    fun partialResultsKeepShownAppsAndAddNewOnesTopFirst() {
        val all = listOf("a", "b", "c", "d", "e", "f").map(::app)
        val keep = setOf(all[4].key) // "e" was already showing
        val first = partialResults(all, keep, budget = 2)
        assertEquals(listOf("a", "b", "e"), first.map { it.label })
        val more = partialResults(all, keep, budget = 4)
        assertEquals(listOf("a", "b", "c", "d", "e"), more.map { it.label })
        assertEquals(all, partialResults(all, keep, budget = 5))
    }

    @Test
    fun adaptiveCountCellsFitsColumnsWithinBounds() {
        val density = Density(1f)
        val cells = AdaptiveCountCells(minCell = 88.dp, minCount = 3, maxCount = 6, extra = 24.dp)
        with(cells) {
            // 400 + 24 = 424 / 88 = 4 columns, sizes sum to the space left after spacing.
            val sizes = density.calculateCrossAxisCellSizes(400, 4)
            assertEquals(4, sizes.size)
            assertEquals(400 - 3 * 4, sizes.sum())
            assertEquals(3, density.calculateCrossAxisCellSizes(100, 4).size)
            assertEquals(6, density.calculateCrossAxisCellSizes(2000, 4).size)
        }
    }
}
