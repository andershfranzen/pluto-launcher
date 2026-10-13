package dev.pluto.launcher.ui.home

import dev.pluto.launcher.R
import dev.pluto.launcher.ui.console.ShelfId
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The empty-shelf guidance names controls that exist, in the words of the current input. */
class ConsoleEmptyCopyTest {
    /** The English source text of a string resource, read from the values file (no Android needed). */
    private fun english(id: Int): String {
        val name = R.string::class.java.fields.first { it.getInt(null) == id }.name
        val file = listOf("src/main/res/values/strings_roms.xml", "app/src/main/res/values/strings_roms.xml")
            .map(::File).first { it.exists() }
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }
            .first { it.getAttribute("name") == name }.textContent.replace("\\'", "'")
    }

    @Test
    fun categoryCopyPointsAtAddApps() {
        val copy = emptyCopy(ShelfId.OfCategory(3), "Games", actionsKey = "X")
        assertEquals(R.string.console_empty_category_title, copy.title)
        assertEquals("Games", copy.name)
        assertEquals("No apps in %1\$s yet", english(copy.title))
        assertTrue(english(copy.detail).contains(english(R.string.console_add_apps)))
    }

    @Test
    fun controllerCopyNamesTheMappedButton() {
        val copy = emptyCopy(ShelfId.Favourites, "Favourites", actionsKey = "X")
        assertEquals("X", copy.actionsKey)
        val detail = english(copy.detail)
        assertTrue(detail, detail.startsWith("Press %1\$s on an app"))
        assertFalse(detail, detail.contains("press and hold", ignoreCase = true))
    }

    @Test
    fun touchCopyWithoutController() {
        val copy = emptyCopy(ShelfId.Favourites, "Favourites", actionsKey = null)
        assertNull(copy.actionsKey)
        val detail = english(copy.detail)
        assertTrue(detail, detail.startsWith("Press and hold an app"))
    }

    @Test
    fun neverMentionsTheRemovedActionsButton() {
        for (key in listOf("X", null)) {
            for (id in listOf(ShelfId.Recent, ShelfId.Favourites, ShelfId.OfCategory(3), ShelfId.OfRomLibrary(1))) {
                val detail = english(emptyCopy(id, "Games", key).detail)
                assertFalse(detail, detail.contains("Actions in All apps"))
            }
        }
    }
}
