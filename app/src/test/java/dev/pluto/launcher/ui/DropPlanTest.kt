package dev.pluto.launcher.ui

import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropPlanTest {
    private fun key(n: String) = AppKey("pkg.$n/.Main", 0)
    private val a = key("a")
    private val b = key("b")
    private val c = key("c")
    private val d = key("d")
    private val home = listOf(HomeItem.App(a), HomeItem.App(b), HomeItem.FolderRef(7), HomeItem.App(c))
    private val dock = listOf(d, null, null, null, null)

    @Test
    fun reorderMovesTheItemCountingWithoutIt() {
        val plan = DropPlan.of(home, dock, emptySet(), DragSource.Home(HomeItem.App(a)), DropTarget.HomeIndex(2))
        assertEquals(listOf(HomeItem.App(b), HomeItem.FolderRef(7), HomeItem.App(a), HomeItem.App(c)), plan.home)
        assertNull(plan.unpin)
    }

    @Test
    fun droppingInPlaceChangesNothing() {
        assertTrue(DropPlan.of(home, dock, emptySet(), DragSource.Home(HomeItem.App(a)), DropTarget.HomeIndex(0)).isEmpty)
    }

    @Test
    fun homeAppToDockSwapsWithTheDockedApp() {
        val plan = DropPlan.of(home, dock, emptySet(), DragSource.Home(HomeItem.App(b)), DropTarget.DockSlot(0))
        assertEquals(b, plan.unpin)
        assertEquals(listOf(b, null, null, null, null), plan.dock)
        assertEquals(listOf(HomeItem.App(a), HomeItem.App(d), HomeItem.FolderRef(7), HomeItem.App(c)), plan.home)
    }

    @Test
    fun dockToDockSwaps() {
        val plan = DropPlan.of(home, listOf(d, b, null, null, null), emptySet(), DragSource.Dock(0, d), DropTarget.DockSlot(1))
        assertEquals(listOf(b, d, null, null, null), plan.dock)
    }

    @Test
    fun drawerAppOntoAnAppMakesAFolderWhereThatAppWas() {
        val plan = DropPlan.of(home, dock, emptySet(), DragSource.Drawer(key("x")), DropTarget.Merge(HomeItem.App(c).id))
        assertEquals(DropPlan.NewFolder(listOf(c, key("x")), 3), plan.newFolder)
    }

    @Test
    fun dockAppIntoAFolderLeavesTheDock() {
        val plan = DropPlan.of(home, dock, emptySet(), DragSource.Dock(0, d), DropTarget.Merge(HomeItem.FolderRef(7).id))
        assertEquals(d to 7L, plan.intoFolder)
        assertEquals(listOf(null, null, null, null, null), plan.dock)
    }

    @Test
    fun drawerAppInAFolderComesOutToSitOnHome() {
        val plan = DropPlan.of(home, dock, setOf(key("x")), DragSource.Drawer(key("x")), DropTarget.HomeIndex(1))
        assertEquals(key("x"), plan.unpin)
        assertEquals(HomeItem.App(key("x")), plan.home!![1])
    }

    @Test
    fun removeUnpinsOrEmptiesTheSlotButNeverTouchesFolders() {
        assertEquals(a, DropPlan.of(home, dock, emptySet(), DragSource.Home(HomeItem.App(a)), DropTarget.Remove).unpin)
        assertEquals(listOf(null, null, null, null, null), DropPlan.of(home, dock, emptySet(), DragSource.Dock(0, d), DropTarget.Remove).dock)
        assertTrue(DropPlan.of(home, dock, emptySet(), DragSource.Home(HomeItem.FolderRef(7)), DropTarget.Remove).isEmpty)
    }
}
