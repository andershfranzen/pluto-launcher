package dev.pluto.launcher.ui

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.data.prefs.BackgroundChoice
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.ui.console.ShelfId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** Real console layers: choosing and cancelling the overview must preserve shelf selection. */
class ConsoleRefinementTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = rule.activity.viewModel
    private var original: LauncherSettings? = null
    private lateinit var shelf: ShelfId

    @Before fun ready() {
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        original = vm.uiState.value.settings
        rule.runOnUiThread {
            vm.closeAllLayers()
            vm.updateSettings { it.copy(onboardingComplete = true, updateCheckChoiceMade = true,
                automaticUpdateChecks = false, reducedMotion = true, handheldAppearance = HandheldAppearance.EVERYWHERE) }
        }
        rule.waitUntil(10_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            vm.uiState.value.settings.handheldAppearance == HandheldAppearance.EVERYWHERE }
        shelf = ShelfId.OfCategory(vm.uiState.value.categories.first { it.isAll }.id)
        rule.runOnUiThread { vm.selectShelf(shelf) }
        rule.waitForIdle()
    }

    @After fun restore() {
        rule.runOnUiThread { vm.closeAllLayers(); original?.let { settings -> vm.updateSettings { settings } } }
        original?.let { settings -> rule.waitUntil(5_000) { vm.uiState.value.settings == settings } }
    }

    @Test fun gridChoiceReturnsToTheSelectedCard() {
        val app = vm.uiState.value.apps.last()
        rule.onNodeWithContentDescription("Browse All apps").performClick()
        rule.waitUntil(5_000) { vm.uiState.value.session.topLayer == Layer.ConsoleOverview }
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("${app.label}, select in carousel"))
        rule.onNodeWithContentDescription("${app.label}, select in carousel").performClick()
        rule.waitUntil(5_000) { vm.uiState.value.session.layers.isEmpty() }
        rule.runOnIdle { assertEquals(app.key, vm.shelfSelection(shelf)); assertEquals(app.key, vm.uiState.value.session.selectedApp) }
        rule.onNode(hasContentDescription("${app.label},", substring = true)).assertIsDisplayed()
        // Reopening the overview keeps the chosen item visible and marked selected.
        rule.onNodeWithContentDescription("Browse All apps").performClick()
        rule.onNodeWithContentDescription("${app.label}, select in carousel").assertIsDisplayed().assertIsSelected()
    }

    @Test fun backFromGridPreservesSelection() {
        val app = vm.uiState.value.apps.first()
        rule.runOnUiThread { vm.rememberShelfSelection(shelf, app.key); vm.onAppSelected(app.key); vm.openLayer(Layer.ConsoleOverview) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Close").assertIsDisplayed()
        rule.runOnUiThread { vm.back() }
        rule.waitUntil(5_000) { vm.uiState.value.session.layers.isEmpty() }
        rule.runOnIdle { assertEquals(app.key, vm.shelfSelection(shelf)) }
        rule.onNode(hasContentDescription("${app.label},", substring = true)).assertIsDisplayed()
    }

    @Test fun rapidShelfChangesLeaveTheFinalShelfUsable() {
        val shelves = vm.uiState.value.categories.map { ShelfId.OfCategory(it.id) }
        rule.runOnUiThread {
            vm.updateSettings { it.copy(reducedMotion = false, consoleBackground = BackgroundChoice.WALLPAPER) }
            shelves.forEach(vm::selectShelf)
            vm.selectShelf(shelf)
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Browse All apps").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { vm.uiState.value.session.topLayer == Layer.ConsoleOverview }
    }
}
