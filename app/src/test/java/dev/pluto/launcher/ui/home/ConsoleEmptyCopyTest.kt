package dev.pluto.launcher.ui.home

import dev.pluto.launcher.ui.console.ShelfId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The empty-shelf guidance names controls that exist, in the words of the current input. */
class ConsoleEmptyCopyTest {
    @Test
    fun controllerCopyNamesTheMappedButton() {
        val (title, detail) = emptyTitle(ShelfId.OfCategory(3), "Games", actionsKey = "X")
        assertTrue(title.contains("Games"))
        assertTrue(detail, detail.startsWith("Press X on an app"))
        assertFalse(detail, detail.contains("press and hold", ignoreCase = true))
    }

    @Test
    fun touchCopyWithoutController() {
        val (_, detail) = emptyTitle(ShelfId.Favourites, "Favourites", actionsKey = null)
        assertTrue(detail, detail.startsWith("Press and hold an app"))
    }

    @Test
    fun neverMentionsTheRemovedActionsButton() {
        for (key in listOf("X", null)) {
            for (id in listOf(ShelfId.Recent, ShelfId.Favourites, ShelfId.OfCategory(3))) {
                val (_, detail) = emptyTitle(id, "Games", key)
                assertFalse(detail, detail.contains("Actions in All apps"))
            }
        }
    }
}
