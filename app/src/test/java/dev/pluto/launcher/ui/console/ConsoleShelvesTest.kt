package dev.pluto.launcher.ui.console

import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.BuiltInCategory
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.model.Folder
import dev.pluto.launcher.ui.FolderUi
import dev.pluto.launcher.ui.HomeTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsoleShelvesTest {
    private val all = Category(1, "All apps", BuiltInCategory.ALL, 0)
    private val games = Category(2, "Games", BuiltInCategory.GAMES, 1)
    private val tools = Category(3, "Tools", BuiltInCategory.TOOLS, 2)
    private val mine = Category(7, "Mine", null, 3)
    private val categories = listOf(all, games, tools, mine)

    private fun app(name: String) = AppEntry(AppKey("pkg.$name/.Main", 0), name)

    @Test
    fun orderIsRecentFavouritesThenCategories() {
        assertEquals(
            listOf(
                ShelfId.Recent,
                ShelfId.Favourites,
                ShelfId.OfCategory(1),
                ShelfId.OfCategory(2),
                ShelfId.OfCategory(3),
                ShelfId.OfCategory(7),
            ),
            ConsoleShelves.order(categories, historyEnabled = true),
        )
    }

    @Test
    fun disabledHistoryHasNoRecentShelf() {
        assertEquals(ShelfId.Favourites, ConsoleShelves.order(categories, historyEnabled = false).first())
    }

    @Test
    fun defaultPrefersRecentsThenFavouritesThenAllApps() {
        val order = ConsoleShelves.order(categories, historyEnabled = true)
        assertEquals(ShelfId.Recent, ConsoleShelves.defaultShelf(order, recentsNonEmpty = true, favouritesNonEmpty = true, allCategoryId = 1))
        assertEquals(ShelfId.Favourites, ConsoleShelves.defaultShelf(order, recentsNonEmpty = false, favouritesNonEmpty = true, allCategoryId = 1))
        assertEquals(ShelfId.OfCategory(1), ConsoleShelves.defaultShelf(order, recentsNonEmpty = false, favouritesNonEmpty = false, allCategoryId = 1))
    }

    @Test
    fun defaultIgnoresRecentsWhenHistoryIsOff() {
        val order = ConsoleShelves.order(categories, historyEnabled = false)
        assertEquals(ShelfId.OfCategory(1), ConsoleShelves.defaultShelf(order, recentsNonEmpty = true, favouritesNonEmpty = false, allCategoryId = 1))
    }

    @Test
    fun resolveFallsBackWhenSavedShelfIsGone() {
        val order = ConsoleShelves.order(listOf(all, games), historyEnabled = false)
        assertEquals(ShelfId.OfCategory(2), ConsoleShelves.resolve(ShelfId.OfCategory(2), order, ShelfId.Favourites))
        assertEquals(ShelfId.Favourites, ConsoleShelves.resolve(ShelfId.OfCategory(7), order, ShelfId.Favourites))
        assertEquals(ShelfId.Favourites, ConsoleShelves.resolve(ShelfId.Recent, order, ShelfId.Favourites))
        assertEquals(ShelfId.Favourites, ConsoleShelves.resolve(null, order, ShelfId.Favourites))
    }

    @Test
    fun stepWrapsBothWays() {
        val order = ConsoleShelves.order(listOf(all, games), historyEnabled = true)
        assertEquals(ShelfId.Favourites, ConsoleShelves.step(order, ShelfId.Recent, +1))
        assertEquals(ShelfId.OfCategory(2), ConsoleShelves.step(order, ShelfId.Recent, -1))
        assertEquals(ShelfId.Recent, ConsoleShelves.step(order, ShelfId.OfCategory(2), +1))
        assertEquals(ShelfId.Recent, ConsoleShelves.step(order, ShelfId.OfCategory(99), +1))
        assertNull(ConsoleShelves.step(emptyList(), ShelfId.Recent, 1))
    }

    @Test
    fun directionFollowsTabOrder() {
        val order = ConsoleShelves.order(categories, historyEnabled = true)
        assertEquals(1, ConsoleShelves.direction(order, ShelfId.Recent, ShelfId.OfCategory(2)))
        assertEquals(-1, ConsoleShelves.direction(order, ShelfId.OfCategory(2), ShelfId.Favourites))
    }

    @Test
    fun keysRoundTrip() {
        listOf(ShelfId.Recent, ShelfId.Favourites, ShelfId.OfCategory(42)).forEach {
            assertEquals(it, ShelfId.decode(it.key))
        }
        assertNull(ShelfId.decode("cat:x"))
        assertNull(ShelfId.decode("bogus"))
        assertNull(ShelfId.decode(null))
    }

    @Test
    fun favouritesFlattenFoldersInPlace() {
        val a = app("a")
        val b = app("b")
        val c = app("c")
        val d = app("d")
        val folder = FolderUi(Folder(5, "Games", listOf(b.key, c.key)), listOf(b, c))
        val tiles = listOf(HomeTile.App(a), HomeTile.FolderTile(folder), HomeTile.App(d))
        assertEquals(listOf(a, b, c, d), ConsoleShelves.favouriteApps(tiles))
    }

    @Test
    fun shelfContents() {
        val a = app("a")
        val b = app("b")
        val c = app("c")
        val visible = listOf(a, b, c)
        val members = mapOf(2L to setOf(b.key))
        fun shelf(id: ShelfId) = ConsoleShelves.shelf(id, categories, members, visible, listOf(c), listOf(b, a))
        assertEquals(visible, shelf(ShelfId.OfCategory(1)).apps)
        assertEquals(listOf(b), shelf(ShelfId.OfCategory(2)).apps)
        assertEquals(emptyList<AppEntry>(), shelf(ShelfId.OfCategory(3)).apps)
        assertEquals(listOf(c), shelf(ShelfId.Favourites).apps)
        val recent = shelf(ShelfId.Recent)
        assertEquals(listOf(b, a), recent.apps)
        assertEquals("Recent launches", recent.title)
        assertEquals("Games", shelf(ShelfId.OfCategory(2)).title)
    }

    @Test
    fun initialIndexPrefersShelfMemoryThenGlobalSelection() {
        val a = app("a")
        val b = app("b")
        val c = app("c")
        val apps = listOf(a, b, c)
        assertEquals(2, ConsoleShelves.initialIndex(apps, remembered = c.key, globalSelection = b.key))
        assertEquals(1, ConsoleShelves.initialIndex(apps, remembered = app("gone").key, globalSelection = b.key))
        assertEquals(0, ConsoleShelves.initialIndex(apps, remembered = null, globalSelection = null))
        assertEquals(0, ConsoleShelves.initialIndex(emptyList(), remembered = a.key, globalSelection = a.key))
    }
}
