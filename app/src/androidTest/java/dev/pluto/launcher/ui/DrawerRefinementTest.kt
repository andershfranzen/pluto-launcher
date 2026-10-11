package dev.pluto.launcher.ui

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.RotationPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Drawer recovery paths with a real keyboard, without modifying the user's categories. */
class DrawerRefinementTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = rule.activity.viewModel
    private var original: LauncherSettings? = null
    private var keyboardSetting = "0"
    private var categoryId: Long? = null

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).use { String(it.readBytes()).trim() }

    @Before fun ready() {
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        original = vm.uiState.value.settings
        keyboardSetting = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        rule.runOnUiThread {
            vm.closeAllLayers()
            vm.updateSettings { it.copy(onboardingComplete = true, updateCheckChoiceMade = true,
                automaticUpdateChecks = false, reducedMotion = true,
                handheldAppearance = HandheldAppearance.NEVER, rotation = RotationPreference.PORTRAIT) }
        }
        rule.waitUntil(5_000) {
            vm.uiState.value.settings.rotation == RotationPreference.PORTRAIT &&
                vm.uiState.value.settings.handheldAppearance == HandheldAppearance.NEVER &&
                vm.uiState.value.settings.updateCheckChoiceMade
        }
        waitOrientation(Configuration.ORIENTATION_PORTRAIT)
        val name = "Drawer review ${System.nanoTime()}"
        rule.runOnUiThread { vm.addCategory(name) }
        rule.waitUntil(5_000) { vm.uiState.value.categories.any { it.name == name } }
        categoryId = vm.uiState.value.categories.single { it.name == name }.id
        rule.runOnUiThread { vm.selectCategory(categoryId); vm.openDrawer() }
        rule.waitUntil(5_000) { vm.uiState.value.session.topLayer == Layer.Drawer }
        rule.waitForIdle()
    }

    @After fun restore() {
        shell("settings put secure show_ime_with_hard_keyboard $keyboardSetting")
        rule.runOnUiThread {
            vm.closeAllLayers()
            categoryId?.let(vm::deleteCategory)
            original?.let { settings -> vm.updateSettings { settings } }
        }
        original?.let { settings -> rule.waitUntil(5_000) { vm.uiState.value.settings == settings } }
        categoryId?.let { id -> rule.waitUntil(5_000) { vm.uiState.value.categories.none { it.id == id } } }
    }

    private fun waitOrientation(orientation: Int) {
        rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == orientation }
        rule.waitForIdle()
    }

    private fun landscapeSearch() {
        rule.runOnUiThread { vm.updateSettings { it.copy(rotation = RotationPreference.LANDSCAPE) } }
        waitOrientation(Configuration.ORIENTATION_LANDSCAPE)
        rule.onNodeWithContentDescription("Search apps").performClick().performTextInput("settings")
        rule.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        rule.waitUntil(5_000) { vm.uiState.value.session.searchText == "settings" }
        rule.waitUntil(5_000) {
            val decor = rule.activity.window.decorView
            val ime = ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val height = rule.activity.windowManager.currentWindowMetrics.bounds.height()
            val field = rule.onNodeWithContentDescription("Search apps").fetchSemanticsNode().boundsInRoot
            ime > height / 3 && field.bottom <= height - ime + rule.activity.resources.displayMetrics.density
        }
        rule.waitForIdle()
    }

    @Test fun emptyCategoryOpensItsAppPickerDirectly() {
        rule.onNodeWithContentDescription("Add apps").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { vm.uiState.value.session.topLayer == Layer.ShelfPicker(categoryId!!) }
    }

    @Test fun landscapeNoResultsCanBroadenSearchWithoutLosingQuery() {
        landscapeSearch()
        rule.onNodeWithContentDescription("Search everything").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { vm.uiState.value.activeCategory?.isAll == true && vm.uiState.value.drawerApps.isNotEmpty() }
        assertEquals("settings", vm.uiState.value.session.searchText)
        rule.onNodeWithContentDescription("Search apps").assertIsDisplayed()
    }

    @Test fun actionsModeStaysVisibleAndCanEndAboveLandscapeKeyboard() {
        rule.onNodeWithContentDescription("Choose an app to see its actions").performClick()
        landscapeSearch()
        rule.onNodeWithContentDescription("App actions mode", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("No apps match “settings”").assertIsDisplayed()
        rule.onNodeWithContentDescription("Done choosing apps").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("App actions mode", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithContentDescription("Done choosing apps").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(vm.uiState.value.session.topLayer == Layer.Drawer)
        assertEquals("settings", vm.uiState.value.session.searchText)
    }
}
