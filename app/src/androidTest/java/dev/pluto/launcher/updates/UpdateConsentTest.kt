package dev.pluto.launcher.updates

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.data.prefs.LauncherSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateConsentTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private var original: LauncherSettings? = null

    @After fun restore() {
        original?.let { settings -> rule.runOnUiThread { rule.activity.viewModel.updateSettings { settings } } }
    }

    @Test fun defaultAutomaticChoiceNeedsConsentAndDecliningKeepsNetworkChecksOff() {
        val vm = rule.activity.viewModel
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        original = vm.uiState.value.settings
        val checked = vm.updateState.value.lastChecked
        rule.runOnUiThread { vm.updateSettings { it.copy(onboardingComplete = true, automaticUpdateChecks = true, updateCheckChoiceMade = false) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Keep Pluto up to date?").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertTrue(vm.uiState.value.settings.automaticUpdateChecks)
        assertEquals(checked, vm.updateState.value.lastChecked)
        assertEquals(UpdateTask.NONE, vm.updateState.value.task)
        // Exercise the accessible button action directly: its contract is the
        // consent choice, independent of the layer's animated touch geometry.
        rule.onNodeWithText("No thanks").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitUntil(5_000) { vm.uiState.value.settings.updateCheckChoiceMade && !vm.uiState.value.settings.automaticUpdateChecks }
        assertEquals(checked, vm.updateState.value.lastChecked)
        assertEquals(UpdateTask.NONE, vm.updateState.value.task)
    }
}
