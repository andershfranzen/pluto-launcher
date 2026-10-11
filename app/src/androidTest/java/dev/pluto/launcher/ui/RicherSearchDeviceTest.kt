package dev.pluto.launcher.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.pluto.launcher.LauncherApplication
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.data.db.RomGameEntity
import dev.pluto.launcher.data.db.RomLibraryEntity
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.RotationPreference
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals

class RicherSearchDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = rule.activity.viewModel
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = (context.applicationContext as LauncherApplication).container.database.romDao()
    private var original: LauncherSettings? = null
    private var libraryId: Long? = null
    private var categoryId: Long? = null
    private val shortcutId = "pluto-search-review"
    private val title = "Searchfixture Adventure"
    private val tree = "content://pluto-search-fixture/tree/games"

    @Before fun ready() {
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        original = vm.uiState.value.settings
        rule.runOnUiThread {
            vm.closeAllLayers()
            vm.updateSettings { it.copy(onboardingComplete = true, updateCheckChoiceMade = true,
                automaticUpdateChecks = false, reducedMotion = true,
                handheldAppearance = HandheldAppearance.NEVER, rotation = RotationPreference.PORTRAIT) }
            vm.selectCategory(null)
            vm.openDrawer()
        }
        rule.waitUntil(5_000) { vm.uiState.value.session.topLayer == Layer.Drawer }
    }

    @After fun restore() {
        context.getSystemService(ShortcutManager::class.java).removeDynamicShortcuts(listOf(shortcutId))
        rule.runOnUiThread {
            vm.closeAllLayers()
            categoryId?.let(vm::deleteCategory)
            original?.let { settings -> vm.updateSettings { settings } }
        }
        libraryId?.let { runBlocking { dao.deleteLibrary(it) } }
        original?.let { settings -> rule.waitUntil(5_000) { vm.uiState.value.settings == settings } }
    }

    private fun search(query: String) {
        rule.onNodeWithContentDescription("Search apps").performTextReplacement(query)
        rule.waitUntil(5_000) { vm.uiState.value.session.searchText == query }
    }
    private fun waitDescription(description: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription(description).assertIsDisplayed()
    }
    private fun seedGame() {
        val id = runBlocking { dao.saveLibrary(RomLibraryEntity(name = "Search fixture", treeUri = tree,
            emulatorPackage = "dev.pluto.missing.emulator", launchMode = "AUTO")) }
        libraryId = id
        rule.waitUntil(10_000) { vm.romErrors.value.containsKey(id) }
        runBlocking { dao.replaceGames(id, tree, listOf(RomGameEntity(id, "$tree/adventure.iso",
            "adventure.iso", title, 0, null))) }
        rule.waitUntil(5_000) { vm.romLibraries.value.any { it.id == id && it.games.size == 1 } }
    }

    @Test fun appResultsUpdateAfterReplacementAndClearRestoresGrid() {
        search("settings")
        val app = vm.uiState.value.drawerApps.first()
        waitDescription("${app.label}, app")
        search("zzzz-no-search-result")
        rule.onNodeWithContentDescription("${app.label}, app").assertDoesNotExist()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("No results for “zzzz-no-search-result”").fetchSemanticsNodes().isNotEmpty() }
        search("")
        rule.waitUntil(5_000) { vm.uiState.value.drawerApps.size > 1 }
        rule.onNodeWithContentDescription("${app.label}, app").assertDoesNotExist()
    }

    @Test fun gamesRespectCategoryAndFailedLaunchPreservesQuery() {
        seedGame()
        val name = "Search scope ${System.nanoTime()}"
        rule.runOnUiThread { vm.addCategory(name) }
        rule.waitUntil(5_000) { vm.uiState.value.categories.any { it.name == name } }
        categoryId = vm.uiState.value.categories.single { it.name == name }.id
        rule.runOnUiThread { vm.selectCategory(categoryId) }
        search("searchfixture")
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Search everything").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("$title, game in Search fixture").assertDoesNotExist()
        rule.onNodeWithContentDescription("Search everything").performClick()
        waitDescription("$title, game in Search fixture")
        rule.onNodeWithContentDescription("$title, game in Search fixture").performClick()
        rule.waitForIdle()
        assertEquals("searchfixture", vm.uiState.value.session.searchText)
        assertEquals(Layer.Drawer, vm.uiState.value.session.topLayer)
        waitDescription("$title, game in Search fixture")
    }

    @Test fun dynamicShortcutAppearsAndRemovalInvalidatesSearch() {
        val manager = context.getSystemService(ShortcutManager::class.java)
        val label = "Shortcutfixture action"
        val shortcut = ShortcutInfo.Builder(context, shortcutId).setShortLabel(label)
            .setActivity(ComponentName(context, MainActivity::class.java))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)).build()
        Assert.assertTrue(manager.addDynamicShortcuts(listOf(shortcut)))
        rule.runOnUiThread { vm.refreshSearchShortcuts() }
        rule.waitUntil(10_000) { vm.searchShortcuts.value.any { it.id == shortcutId } }
        val parent = vm.searchShortcuts.value.single { it.id == shortcutId }.app.label
        search("shortcutfixture")
        waitDescription("$label, $parent shortcut")
        manager.removeDynamicShortcuts(listOf(shortcutId))
        rule.waitUntil(10_000) { vm.searchShortcuts.value.none { it.id == shortcutId } }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("$label, $parent shortcut").fetchSemanticsNodes().isEmpty() }
    }
}
