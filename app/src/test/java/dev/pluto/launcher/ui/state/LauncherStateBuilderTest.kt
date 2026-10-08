package dev.pluto.launcher.ui.state

import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.model.BuiltInCategory
import dev.pluto.launcher.model.Category
import dev.pluto.launcher.model.Folder
import dev.pluto.launcher.model.HomeItem
import dev.pluto.launcher.model.Organization
import dev.pluto.launcher.model.RecentLaunch
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LiveSession
import dev.pluto.launcher.ui.SessionMemory
import dev.pluto.launcher.ui.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherStateBuilderTest {
    private fun app(label: String) = AppEntry(AppKey("com.example.${label.lowercase()}/.Main", 0), label)

    private val apps = listOf("Calendar", "Camera", "Clock", "Games", "Gallery", "Maps", "Music").map(::app)
    private val games = Category(2, "Games", BuiltInCategory.GAMES, 1)

    private fun organization() = Organization(
        homeItems = listOf(HomeItem.App(apps[0].key), HomeItem.FolderRef(7)),
        dock = listOf(apps[1].key, null, null, null, null),
        folders = mapOf(7L to Folder(7, "Media", listOf(apps[5].key, apps[6].key))),
        categories = listOf(Category(1, "All", BuiltInCategory.ALL, 0), games),
        categoryMembers = mapOf(2L to setOf(apps[3].key, apps[4].key)),
        recents = listOf(RecentLaunch(apps[2].key, 10)),
    )

    private val env = EnvironmentInputs(emptyList(), false, WindowSizeDp(400, 800), null, true)

    private fun library(org: Organization = organization(), catalog: List<AppEntry> = apps) =
        LibraryInputs(catalog, org, LauncherSettings())

    @Test
    fun layerChangeKeepsTheSameStateAndLists() {
        val builder = LauncherStateBuilder()
        val live = LiveSession(SessionState())
        val lib = library()
        val first = builder.build(lib, env, live.value, live)
        val opened = live.value.copy(layers = listOf(Layer.Drawer))
        val second = builder.build(lib, env, opened, live)
        // Nothing shown is derived from the layers: no new state at all.
        assertSame(first, second)
    }

    @Test
    fun searchChangesOnlyTheDrawerResults() {
        val builder = LauncherStateBuilder()
        val lib = library()
        val first = builder.build(lib, env, SessionState())
        val second = builder.build(lib, env, SessionState(searchText = "ca", searchActive = true))
        assertEquals(listOf("Calendar", "Camera"), second.drawerApps.map { it.label })
        assertNotSame(first.drawerApps, second.drawerApps)
        assertSame(first.apps, second.apps)
        assertSame(first.allApps, second.allApps)
        assertSame(first.homeTiles, second.homeTiles)
        assertSame(first.dock, second.dock)
        assertSame(first.folders, second.folders)
        assertSame(first.categoryApps, second.categoryApps)
        assertSame(first.recents, second.recents)
    }

    @Test
    fun equalOrganisationFromANewEmissionKeepsIdentity() {
        val builder = LauncherStateBuilder()
        val session = SessionState()
        val first = builder.build(library(organization()), env, session)
        // The database emits a new, equal Organization (new collection instances).
        val second = builder.build(library(organization()), env, session)
        assertSame(first, second)
        assertSame(first.homeTiles, second.homeTiles)
    }

    @Test
    fun recentLaunchChangesRecentsOnly() {
        val builder = LauncherStateBuilder()
        val first = builder.build(library(), env, SessionState())
        val org = organization().copy(recents = listOf(RecentLaunch(apps[6].key, 20), RecentLaunch(apps[2].key, 10)))
        val second = builder.build(library(org), env, SessionState())
        assertEquals(listOf("Music", "Clock"), second.recents.map { it.label })
        assertSame(first.drawerApps, second.drawerApps)
        assertSame(first.homeTiles, second.homeTiles)
        assertSame(first.categoryApps, second.categoryApps)
    }

    @Test
    fun categoryChangeRebuildsCategoryAndDrawerLists() {
        val builder = LauncherStateBuilder()
        val base = SessionState()
        val first = builder.build(library(), env, base)
        val second = builder.build(library(), env, base.copy(activeCategoryId = games.id))
        assertEquals(listOf("Gallery", "Games"), second.categoryApps.map { it.label })
        assertEquals(second.categoryApps, second.drawerApps)
        assertSame(first.homeTiles, second.homeTiles)
        // Back to ALL: an equal list, and the state built before is equal again.
        val third = builder.build(library(), env, base)
        assertEquals(first, third)
    }

    @Test
    fun catalogIsSortedOncePerCatalogInstance() {
        val builder = LauncherStateBuilder()
        val lib = library()
        builder.build(lib, env, SessionState())
        builder.build(lib, env, SessionState(searchText = "c"))
        builder.build(lib.copy(organization = organization().copy(hidden = setOf(apps[0].key))), env, SessionState())
        assertEquals(1, builder.sortCount)
        builder.build(library(catalog = apps.reversed()), env, SessionState())
        assertEquals(2, builder.sortCount)
    }

    @Test
    fun hiddenAppLeavesListsButNotAllApps() {
        val builder = LauncherStateBuilder()
        val state = builder.build(library(organization().copy(hidden = setOf(apps[0].key))), env, SessionState())
        assertTrue(state.apps.none { it.key == apps[0].key })
        assertTrue(state.homeTiles.size == 1)
        assertTrue(state.allApps.any { it.key == apps[0].key })
    }

    @Test
    fun memoryChangesNeverAffectTheState() {
        val builder = LauncherStateBuilder()
        val memory = SessionMemory()
        val live = LiveSession(SessionState(memory = memory))
        val first = builder.build(library(), env, live.value, live)
        memory.selectedApp = apps[3].key
        memory.focusedControlId = "dock:1"
        memory.setScrollAnchor("drawer", "app:x")
        val second = builder.build(library(), env, live.value, live)
        assertSame(first, second)
        assertEquals(apps[3].key, second.session.selectedApp)
    }
}
