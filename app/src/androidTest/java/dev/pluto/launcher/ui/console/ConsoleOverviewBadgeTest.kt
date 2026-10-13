package dev.pluto.launcher.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.focus.ProvideControllerFocus
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.overlay.ConsoleOverviewGrid
import org.junit.Rule
import org.junit.Test

/** The tile pill reports what Pluto knows: a play date first, else the save data's own date. */
class ConsoleOverviewBadgeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun aTileShowsAPlayDateOrASaveDateButNeverBoth() {
        val now = 1_700_000_000_000L
        val played = RomGame("content://test/played", "played.cue", "Played", 0)
        val saved = RomGame("content://test/saved", "saved.cue", "Saved", 0)
        val plain = RomGame("content://test/plain", "plain.cue", "Plain", 0)
        val entries = listOf(played, saved, plain).map { ConsoleEntry.Rom(1, it) }
        val icons = IconCache(rule.activity)
        rule.setContent {
            MaterialTheme {
                ProvideControllerFocus(ControllerFocusController()) {
                    CompositionLocalProvider(LocalIconCache provides icons) {
                        ConsoleOverviewGrid(
                            entries = entries, selectedKey = null, modifier = Modifier.fillMaxSize(),
                            saves = mapOf(saved.uri to now), played = mapOf(played.uri to now), now = now,
                        ) {}
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onAllNodesWithText("Played today").assertCountEquals(1)
        rule.onAllNodesWithText("Save · today").assertCountEquals(1)
    }
}
