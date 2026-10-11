package dev.pluto.launcher.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.LauncherMode
import dev.pluto.launcher.model.RotationPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real keyboard and rotation checks for the short landscape layouts found in review. */
class ScreenReviewRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = rule.activity.viewModel
    private var keyboardSetting = "0"
    private var reducedMotion = false
    private val createdFolders = mutableSetOf<Long>()
    private val createdLibraries = mutableSetOf<Long>()

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).use { String(it.readBytes()).trim() }

    @Before
    fun ready() {
        keyboardSetting = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        rule.waitUntil(10_000) { !vm.state.value.loading }
        reducedMotion = vm.state.value.settings.reducedMotion
        rule.runOnUiThread {
            vm.completeOnboarding()
            vm.closeAllLayers()
            vm.updateSettings { it.copy(rotation = RotationPreference.LANDSCAPE, handheldAppearance = HandheldAppearance.AUTOMATIC, reducedMotion = true) }
        }
        waitOrientation(Configuration.ORIENTATION_LANDSCAPE)
    }

    @After
    fun restore() {
        shell("settings put secure show_ime_with_hard_keyboard $keyboardSetting")
        shell("settings put system accelerometer_rotation 1")
        rule.runOnUiThread {
            vm.closeAllLayers()
            createdFolders.forEach(vm::deleteFolder)
            createdLibraries.forEach(vm::deleteRomLibrary)
            vm.updateSettings { it.copy(rotation = RotationPreference.FOLLOW_SYSTEM, handheldAppearance = HandheldAppearance.AUTOMATIC, reducedMotion = reducedMotion) }
        }
    }

    private fun waitOrientation(orientation: Int) {
        rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == orientation }
        rule.waitForIdle()
    }

    private fun assertWholeNameFieldAboveKeyboard() {
        rule.waitUntil(10_000) {
            ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        assertTrue("Name field clipped to ${bounds.height / density} dp", bounds.height >= 56f * density)
        // IME visibility precedes the end of its platform animation. Wait for the
        // final layout instead of sampling its first visible frame.
        rule.waitUntil(5_000) {
            val decor = rule.activity.window.decorView
            val imeBottom = ViewCompat.getRootWindowInsets(decor)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val windowHeight = rule.activity.windowManager.currentWindowMetrics.bounds.height()
            val field = rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
            field.bottom <= windowHeight - imeBottom + density
        }
    }

    @Test
    fun newFolderNameFitsAboveLandscapeKeyboardAndDoneCreatesIt() {
        val name = "Keyboard ${System.nanoTime()}"
        rule.runOnUiThread { vm.openLayer(Layer.Edit) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("New folder…").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("New folder…").performClick()
        assertWholeNameFieldAboveKeyboard()
        rule.onNode(hasSetTextAction()).apply {
            performTextClearance()
            performTextInput(name)
            performImeAction()
        }
        rule.waitUntil(5_000) { vm.uiState.value.folders.values.any { it.folder.name == name } }
        createdFolders += vm.uiState.value.folders.values.first { it.folder.name == name }.folder.id
    }

    @Test
    fun folderRenameFitsAboveLandscapeKeyboardAndDoneSavesIt() {
        val name = "Rename ${System.nanoTime()}"
        rule.runOnUiThread { vm.createFolder(name, emptyList()) }
        rule.waitUntil(5_000) { vm.uiState.value.folders.values.any { it.folder.name == name } }
        val id = vm.uiState.value.folders.values.first { it.folder.name == name }.folder.id
        createdFolders += id
        rule.runOnUiThread { vm.openLayer(Layer.FolderLayer(id)) }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Rename").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Rename").performClick()
        assertWholeNameFieldAboveKeyboard()
        rule.onNode(hasSetTextAction()).apply {
            performTextClearance()
            performTextInput("Renamed review")
            performImeAction()
        }
        rule.waitUntil(5_000) { vm.uiState.value.folders[id]?.folder?.name == "Renamed review" }
    }

    @Test
    fun romShelfNameDoneClosesDialogAndKeepsTheName() {
        rule.runOnUiThread { vm.openLayer(Layer.RomLibraries) }
        rule.onNodeWithContentDescription("Add ROM tab").performClick()
        rule.waitUntil(5_000) { rule.onNode(hasContentDescription("Create tab", substring = true)).isDisplayed() }
        rule.onNode(hasContentDescription("Name this tab", substring = true)).performScrollTo().performClick()
        assertWholeNameFieldAboveKeyboard()
        rule.onNode(hasSetTextAction()).apply {
            performTextInput("PlayStation 1")
            performImeAction()
        }
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasContentDescription("PlayStation 1", substring = true)).assertExists()
    }

    @Test
    fun unsavedRomTabCanBeKeptOrDiscarded() {
        rule.runOnUiThread { vm.openLayer(Layer.RomLibraries) }
        rule.waitUntil(5_000) { rule.onNodeWithContentDescription("Add ROM tab").isDisplayed() }
        rule.onNodeWithContentDescription("Add ROM tab").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasContentDescription("Name this tab", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasContentDescription("Name this tab", substring = true)).performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).apply {
            performTextInput("Unsaved collection")
            performImeAction()
        }
        rule.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Discard changes?").assertExists()
        rule.onNodeWithContentDescription("Cancel").performClick()
        rule.onNode(hasContentDescription("Unsaved collection", substring = true)).assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithContentDescription("Discard").performClick()
        rule.onNodeWithContentDescription("Add ROM tab").assertExists()
        rule.onAllNodesWithText("Unsaved collection").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun romDefaultEmulatorCanBeSearchedAndSavedWithoutAdvancedOptions() {
        var id = 0L
        rule.runOnUiThread {
            vm.saveRomLibrary(dev.pluto.launcher.model.RomLibrary(0, "Emulator choice review", "content://review/tree/games", null)) { saved ->
                id = saved
                createdLibraries += saved
                vm.openLayer(Layer.RomLibraries)
            }
        }
        rule.waitUntil(5_000) { id != 0L && vm.romLibraries.value.any { it.id == id } }
        rule.onNode(hasContentDescription("Emulator choice review · Folder", substring = true)).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onNodeWithContentDescription("Save changes").isDisplayed() }
        assertTrue(rule.onAllNodesWithText("Android Open file").fetchSemanticsNodes().isEmpty())
        rule.onNode(hasContentDescription("Choose a default to open games with one tap", substring = true)).performScrollTo().performClick()
        val candidate = vm.uiState.value.apps.first { it.isEnabled && it.key.packageName != "dev.pluto.launcher" }
        rule.onNode(hasSetTextAction()).performTextInput(candidate.key.packageName)
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.onNodeWithContentDescription(candidate.label).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Save changes").performClick()
        rule.waitUntil(5_000) { vm.romLibraries.value.firstOrNull { it.id == id }?.emulatorPackage == candidate.key.packageName }
        assertEquals(dev.pluto.launcher.model.RomLaunchMode.AUTOMATIC, vm.romLibraries.value.first { it.id == id }.launchMode)
    }

    @Test
    fun alwaysHoldsLandscapeAndAutomaticRestoresSystemRotation() {
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 0")
        rule.runOnUiThread {
            vm.updateSettings { it.copy(rotation = RotationPreference.FOLLOW_SYSTEM, handheldAppearance = HandheldAppearance.EVERYWHERE) }
        }
        rule.waitUntil(5_000) { rule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE }
        waitOrientation(Configuration.ORIENTATION_LANDSCAPE)
        rule.waitUntil(5_000) { vm.uiState.value.mode == LauncherMode.HANDHELD }
        rule.runOnUiThread { vm.updateSettings { it.copy(handheldAppearance = HandheldAppearance.AUTOMATIC) } }
        rule.waitUntil(5_000) { rule.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_USER }
        waitOrientation(Configuration.ORIENTATION_PORTRAIT)
        assertEquals(LauncherMode.PHONE, vm.uiState.value.mode)
        shell("settings put system user_rotation 1")
        waitOrientation(Configuration.ORIENTATION_LANDSCAPE)
        assertEquals(LauncherMode.LANDSCAPE, vm.uiState.value.mode)
    }
}
