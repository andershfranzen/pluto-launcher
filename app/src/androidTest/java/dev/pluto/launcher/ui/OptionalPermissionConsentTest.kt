package dev.pluto.launcher.ui

import android.content.IntentFilter
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import dev.pluto.launcher.MainActivity
import dev.pluto.launcher.data.prefs.LauncherSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Verifies that only the affirmative button enables a feature and opens Android settings. */
class OptionalPermissionConsentTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private var original: LauncherSettings? = null

    @After fun restore() {
        original?.let { saved -> rule.runOnUiThread {
            rule.activity.viewModel.closeAllLayers()
            rule.activity.viewModel.updateSettings { saved }
        } }
    }

    private fun prepare(layer: Layer) {
        val vm = rule.activity.viewModel
        rule.waitUntil(10_000) { !vm.uiState.value.loading }
        original = vm.uiState.value.settings
        rule.runOnUiThread {
            vm.updateSettings { it.copy(onboardingComplete = true, updateCheckChoiceMade = true,
                automaticUpdateChecks = false, doubleTapToLock = false, notificationDots = false) }
        }
        rule.waitUntil(5_000) { vm.uiState.value.settings.onboardingComplete && vm.uiState.value.settings.updateCheckChoiceMade }
        rule.runOnUiThread { vm.closeAllLayers(); vm.openLayer(layer) }
        rule.waitUntil(5_000) { vm.uiState.value.session.topLayer == layer }
        rule.waitForIdle()
    }

    @Test fun cancellingScreenLockDoesNotEnableIt() {
        prepare(Layer.ScreenLockPermission)
        rule.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitUntil(5_000) { rule.activity.viewModel.uiState.value.session.topLayer == null }
        assertFalse(rule.activity.viewModel.uiState.value.settings.doubleTapToLock)
    }

    @Test fun cancellingNotificationDotsDoesNotEnableThem() {
        prepare(Layer.NotificationPermission)
        rule.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitUntil(5_000) { rule.activity.viewModel.uiState.value.session.topLayer == null }
        assertFalse(rule.activity.viewModel.uiState.value.settings.notificationDots)
    }

    @Test fun backFromDisclosureDoesNotEnableEitherFeature() {
        prepare(Layer.ScreenLockPermission)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { rule.activity.viewModel.uiState.value.session.topLayer == null }
        assertFalse(rule.activity.viewModel.uiState.value.settings.doubleTapToLock)
        assertFalse(rule.activity.viewModel.uiState.value.settings.notificationDots)
    }

    @Test fun agreeingToNotificationDotsEnablesFeatureAndOpensItsAndroidSettings() {
        prepare(Layer.NotificationPermission)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS), null, true)
        try {
            rule.onNodeWithText("Agree and continue").performSemanticsAction(SemanticsActions.OnClick) { it() }
            rule.waitUntil(5_000) { rule.activity.viewModel.uiState.value.settings.notificationDots }
            assertEquals(1, monitor.hits)
            assertFalse(rule.activity.viewModel.uiState.value.settings.doubleTapToLock)
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
