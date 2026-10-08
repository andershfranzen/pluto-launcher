package dev.pluto.launcher.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pluto.launcher.data.db.LauncherDatabase
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.BuiltInCategory
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizationRepositoryTest {
    private lateinit var db: LauncherDatabase
    private lateinit var repo: OrganizationRepository

    private fun app(name: String, user: Long = 0) = AppKey("com.example.$name/.Main", user)
    private val a = app("a")
    private val b = app("b")
    private val c = app("c")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LauncherDatabase::class.java).build()
        repo = OrganizationRepository(db.dao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun org(): Organization = repo.organization.first()

    private fun homeApps(org: Organization) = org.homeItems.filterIsInstance<HomeItem.App>().map { it.key }

    @Test
    fun defaultsAreSeededOnce() = runTest {
        repo.ensureDefaults()
        repo.ensureDefaults()
        val categories = org().categories
        assertEquals(listOf("All apps", "Games", "Tools"), categories.map { it.name })
        assertEquals(listOf(BuiltInCategory.ALL, BuiltInCategory.GAMES, BuiltInCategory.TOOLS), categories.map { it.builtIn })
        assertEquals(List(Organization.DOCK_SLOTS) { null }, org().dock)
    }

    @Test
    fun defaultLayoutIsSeededOnlyIntoAnEmptyLayout() = runTest {
        repo.ensureDefaults()
        assertTrue(repo.seedDefaultLayout(listOf(a, b, null), listOf(c, a)))
        val o = org()
        assertEquals(listOf(a, b, null, null, null), o.dock)
        assertEquals(listOf(c, a), homeApps(o))

        // Any existing organisation blocks a second seed.
        assertFalse(repo.seedDefaultLayout(listOf(c), listOf(b)))
        assertEquals(listOf(a, b, null, null, null), org().dock)
    }

    @Test
    fun defaultLayoutIsNotSeededWhenOnlyAFolderExists() = runTest {
        val folder = repo.createFolder("F", listOf(a))
        repo.removeAppFromFolder(a)
        repo.unpin(a)
        assertTrue(org().folders.containsKey(folder))
        assertFalse(repo.seedDefaultLayout(listOf(b), listOf(c)))
        assertEquals(List(Organization.DOCK_SLOTS) { null }, org().dock)
    }

    @Test
    fun setHomeOrderKeepsRowsAddedSinceTheCallerRead() = runTest {
        repo.pin(a)
        repo.pin(b)
        val staleRead = org().homeItems
        repo.pin(c) // a concurrent edit the reorder did not see
        repo.setHomeOrder(staleRead.reversed())
        assertEquals(listOf(b, a, c), homeApps(org()))
    }

    @Test
    fun pinIsIdempotentAndUnpinRemoves() = runTest {
        repo.pin(a)
        repo.pin(b)
        repo.pin(a)
        assertEquals(listOf(a, b), homeApps(org()))

        repo.unpin(a)
        assertEquals(listOf(b), homeApps(org()))
    }

    @Test
    fun pinDoesNotDuplicateAppInFolder() = runTest {
        repo.createFolder("F", listOf(a))
        repo.pin(a)
        assertTrue(homeApps(org()).isEmpty())
    }

    @Test
    fun unpinRemovesFolderMembershipButKeepsDock() = runTest {
        val folder = repo.createFolder("F", listOf(a, b))
        repo.setDock(listOf(a, null, null, null, null))
        repo.unpin(a)
        val o = org()
        assertEquals(listOf(b), o.folders.getValue(folder).apps)
        assertEquals(a, o.dock[0])
    }

    @Test
    fun createFolderTakesAppsOffHomeAtIndex() = runTest {
        listOf(a, b, c).forEach { repo.pin(it) }
        val folder = repo.createFolder("Games", listOf(b, c), homeIndex = 1)
        val o = org()
        assertEquals(listOf(HomeItem.App(a), HomeItem.FolderRef(folder)), o.homeItems)
        assertEquals(listOf(b, c), o.folders.getValue(folder).apps)
    }

    @Test
    fun deleteFolderReturnsAppsWhereFolderWas() = runTest {
        repo.pin(a)
        val folder = repo.createFolder("F", listOf(b, c))
        repo.pin(app("d"))
        repo.setHomeOrder(listOf(HomeItem.App(a), HomeItem.FolderRef(folder), HomeItem.App(app("d"))))

        repo.deleteFolder(folder)
        val o = org()
        assertTrue(o.folders.isEmpty())
        assertEquals(listOf(a, b, c, app("d")), homeApps(o))
    }

    @Test
    fun appIsInAtMostOneFolder() = runTest {
        val f1 = repo.createFolder("One", listOf(a, b))
        val f2 = repo.createFolder("Two", listOf(c))
        repo.moveAppToFolder(a, f2)
        var o = org()
        assertEquals(listOf(b), o.folders.getValue(f1).apps)
        assertEquals(listOf(c, a), o.folders.getValue(f2).apps)

        // Creating a folder steals apps from other folders too.
        val f3 = repo.createFolder("Three", listOf(a, b))
        o = org()
        assertEquals(listOf(a, b), o.folders.getValue(f3).apps)
        assertTrue(o.folders.getValue(f1).apps.isEmpty())
        assertEquals(listOf(c), o.folders.getValue(f2).apps)
        assertEquals(1, o.folders.values.count { a in it.apps })
    }

    @Test
    fun moveIntoFolderRemovesFromHomeAndBackOut() = runTest {
        repo.pin(a)
        val folder = repo.createFolder("F", listOf(b))
        repo.moveAppToFolder(a, folder)
        assertFalse(HomeItem.App(a) in org().homeItems)

        repo.removeAppFromFolder(a)
        val o = org()
        assertEquals(listOf(HomeItem.FolderRef(folder), HomeItem.App(a)), o.homeItems)
        assertEquals(listOf(b), o.folders.getValue(folder).apps)
    }

    @Test
    fun dockIsCappedAndDistinct() = runTest {
        val keys = (1..7).map { app("d$it") }
        repo.setDock(keys)
        assertEquals(keys.take(Organization.DOCK_SLOTS), org().dock)

        repo.setDock(listOf(a, a, null, b, a))
        val dock = org().dock
        assertEquals(Organization.DOCK_SLOTS, dock.size)
        assertEquals(listOf(a, null, null, b, null), dock)

        repo.setDock(listOf(null, b))
        assertEquals(listOf(null, b, null, null, null), org().dock)
    }

    @Test
    fun recentsCapAtTwelveAndMoveToFront() = runTest {
        val keys = (1..15).map { app("r$it") }
        keys.forEachIndexed { i, key -> repo.recordLaunch(key, 1_000L + i) }
        var recents = org().recents.map { it.key }
        assertEquals(12, recents.size)
        assertEquals(keys.takeLast(12).reversed(), recents)

        // Relaunching moves to the front without duplicating.
        repo.recordLaunch(keys[5], 5_000)
        recents = org().recents.map { it.key }
        assertEquals(keys[5], recents.first())
        assertEquals(12, recents.size)
        assertEquals(recents.distinct(), recents)

        // Even with a clock that went backwards.
        repo.recordLaunch(keys[10], 10)
        assertEquals(keys[10], org().recents.first().key)

        repo.clearRecents()
        assertTrue(org().recents.isEmpty())
    }

    @Test
    fun forgetAppRemovesAllReferences() = runTest {
        repo.ensureDefaults()
        val games = org().categories.first { it.builtIn == BuiltInCategory.GAMES }.id
        repo.pin(a)
        repo.createFolder("F", listOf(b, app("other")))
        repo.setDock(listOf(a, b, null, null, null))
        repo.setCategoryMembership(games, a, true)
        repo.setCategoryMembership(games, b, true)
        repo.hide(a)
        repo.recordLaunch(a, 1)
        repo.recordLaunch(b, 2)

        repo.forgetApp(a)
        repo.forgetApp(b)
        val o = org()
        assertTrue(homeApps(o).isEmpty())
        assertTrue(o.folders.values.none { a in it.apps || b in it.apps })
        assertEquals(List(Organization.DOCK_SLOTS) { null }, o.dock)
        assertTrue(o.categoryMembers[games].orEmpty().isEmpty())
        assertTrue(o.hidden.isEmpty())
        assertTrue(o.recents.isEmpty())
        // Unrelated folder contents survive.
        assertEquals(listOf(app("other")), o.folders.values.single().apps)
    }

    @Test
    fun allCategoryCannotBeDeleted() = runTest {
        repo.ensureDefaults()
        val categories = org().categories
        val all = categories.first { it.isAll }
        val games = categories.first { it.builtIn == BuiltInCategory.GAMES }
        repo.deleteCategory(all.id)
        repo.deleteCategory(games.id)
        val remaining = org().categories
        assertTrue(remaining.any { it.isAll })
        assertFalse(remaining.any { it.id == games.id })
    }

    @Test
    fun allCategoryHasNoExplicitMembers() = runTest {
        repo.ensureDefaults()
        val all = org().categories.first { it.isAll }
        repo.setCategoryMembership(all.id, a, true)
        assertTrue(org().categoryMembers[all.id].orEmpty().isEmpty())
    }

    @Test
    fun userCategoriesAddRenameReorderAndMembership() = runTest {
        repo.ensureDefaults()
        val dev = repo.addCategory("Dev")
        repo.renameCategory(dev, "Development")
        repo.setCategoryMembership(dev, a, true)
        repo.setCategoryMembership(dev, a, true)
        repo.setCategoryMembership(dev, b, true)
        repo.setCategoryMembership(dev, b, false)
        var o = org()
        assertEquals("Development", o.categories.last().name)
        assertEquals(setOf(a), o.categoryMembers[dev])

        val ids = o.categories.map { it.id }
        repo.reorderCategories(listOf(dev) + ids.dropLast(1))
        o = org()
        assertEquals(dev, o.categories.first().id)
    }

    @Test
    fun hideAndUnhide() = runTest {
        repo.hide(a)
        repo.hide(a)
        assertEquals(setOf(a), org().hidden)
        repo.unhide(a)
        assertTrue(org().hidden.isEmpty())
    }

    @Test
    fun profilesDoNotCollide() = runTest {
        val personal = app("x", user = 0)
        val work = app("x", user = 10)
        repo.pin(personal)
        repo.pin(work)
        repo.forgetApp(work)
        assertEquals(listOf(personal), homeApps(org()))
    }
}
