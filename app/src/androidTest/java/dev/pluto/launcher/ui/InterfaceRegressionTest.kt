package dev.pluto.launcher.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.model.RotationPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real launcher flows: search stays in place and settings has navigable sections. */
class InterfaceRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = rule.activity.viewModel

    @Before
    fun ready() {
        rule.waitUntil(10_000) { !vm.state.value.loading }
        rule.runOnUiThread {
            vm.completeOnboarding()
            vm.closeAllLayers()
            vm.updateSettings { it.copy(rotation = RotationPreference.PORTRAIT) }
        }
        rule.waitUntil(5_000) { vm.state.value.session.topLayer == null }
    }

    @Test
    fun typingAndClearingDoNotReplaceSearchOrCategoryHeader() {
        rule.runOnUiThread { vm.openDrawer() }
        rule.waitUntil(5_000) { vm.state.value.session.topLayer == Layer.Drawer }
        val search = rule.onNode(hasSetTextAction() and hasContentDescription("Search apps"))
        search.assertIsDisplayed()
        val before = search.fetchSemanticsNode().boundsInRoot
        search.performClick().performTextInput("settings")
        rule.waitUntil(5_000) { vm.state.value.session.searchText == "settings" }
        rule.onNodeWithText("Games").assertIsDisplayed()
        val after = search.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.width, after.width, 1f)
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.waitUntil(5_000) { vm.state.value.session.searchText.isEmpty() }
        search.assertIsDisplayed()
        rule.onNodeWithText("Games").assertIsDisplayed()
    }

    @Test
    fun rapidNativeKeyEventsKeepEveryCharacterExactlyOnce() {
        rule.runOnUiThread { vm.openDrawer(withSearch = true) }
        rule.waitUntil(5_000) { vm.state.value.session.searchActive }
        val search = rule.onNode(hasSetTextAction() and hasContentDescription("Search apps"))
        search.performClick()
        rule.waitForIdle()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("input text settings"),
        ).use { it.readBytes() }
        rule.waitUntil(10_000) { vm.state.value.session.searchText == "settings" }
        assertEquals("settings", vm.state.value.session.searchText)
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.waitUntil(5_000) { vm.state.value.session.searchText.isEmpty() }
    }

    @Test
    fun backExitsAnEmptySearchBeforeClosingTheDrawer() {
        rule.runOnUiThread { vm.openDrawer(withSearch = true) }
        rule.waitUntil(5_000) { vm.state.value.session.searchActive }
        rule.runOnUiThread {
            assertTrue(vm.back())
            assertEquals(Layer.Drawer, vm.uiState.value.session.topLayer)
            assertFalse(vm.uiState.value.session.searchActive)
            assertTrue(vm.back())
            assertEquals(null, vm.uiState.value.session.topLayer)
        }
    }

    @Test
    fun settingsSectionsKeepNavigationVisibleAndShowOnlyRelevantControls() {
        rule.runOnUiThread { vm.openLayer(Layer.Settings) }
        rule.onNodeWithText("Theme & background").assertIsDisplayed()
        rule.onNodeWithText("Apps").performClick()
        rule.onNodeWithText("Organisation").assertIsDisplayed()
        rule.onNodeWithText("Theme & background").assertDoesNotExist()
        rule.onNodeWithText("Layout").performClick()
        rule.onNodeWithText("Layout & rotation").assertIsDisplayed()
        rule.onNodeWithText("Organisation").assertDoesNotExist()
        rule.onNodeWithText("General").assertIsDisplayed().performClick()
        rule.onNodeWithText("Default launcher").assertIsDisplayed()
    }
}
