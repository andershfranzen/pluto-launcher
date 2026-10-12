package dev.pluto.launcher.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.data.prefs.LauncherSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayOnboardingTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private var original: LauncherSettings? = null

    @After fun restore() {
        original?.let { saved -> rule.runOnUiThread { rule.activity.viewModel.updateSettings { saved } } }
    }

    @Test fun completedOnboardingDoesNotReopenForMissingGithubConsent() {
        val vm = rule.activity.viewModel
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        original = vm.uiState.value.settings
        rule.runOnUiThread { vm.updateSettings {
            it.copy(onboardingComplete = true, updateCheckChoiceMade = false, automaticUpdateChecks = true)
        } }
        rule.waitUntil(5_000) { vm.uiState.value.settings.onboardingComplete && !vm.uiState.value.settings.updateCheckChoiceMade }
        rule.waitForIdle()
        assertFalse(Layer.Onboarding in vm.uiState.value.session.layers)
        assertTrue(rule.onAllNodesWithText("Keep Pluto up to date?").fetchSemanticsNodes().isEmpty())
        assertEquals(0L, vm.updateState.value.lastChecked)
    }

    @Test fun firstRunShowsLauncherSetupWithoutGithubConsent() {
        val vm = rule.activity.viewModel
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        original = vm.uiState.value.settings
        rule.runOnUiThread { vm.updateSettings {
            it.copy(onboardingComplete = false, updateCheckChoiceMade = false)
        } }
        rule.waitUntil(5_000) { vm.uiState.value.session.topLayer == Layer.Onboarding }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Keep Pluto up to date?").fetchSemanticsNodes().isEmpty())
        assertFalse(rule.onAllNodesWithText("Welcome to Pluto").fetchSemanticsNodes().isEmpty())
    }
}
