package dev.pluto.launcher.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.input.Direction
import dev.pluto.launcher.input.LauncherAction
import dev.pluto.launcher.model.RomGame
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.ProvideControllerFocus
import dev.pluto.launcher.ui.overlay.ConsoleOverviewGrid
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConsoleOverviewFocusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun controllerScrollsBeyondTheViewportAndChoosesARom() {
        val entries = List(48) { i -> ConsoleEntry.Rom(1, RomGame("content://test/game$i", "game$i.cue", "Game $i", 0)) }
        val focus = ControllerFocusController()
        var chosen: ConsoleEntry? = null
        val icons = IconCache(rule.activity)
        rule.setContent {
            MaterialTheme {
                ProvideControllerFocus(focus) {
                    CompositionLocalProvider(LocalIconCache provides icons) {
                        ConsoleOverviewGrid(entries, entries.first().key, Modifier.fillMaxSize()) { chosen = it }
                    }
                }
            }
        }
        rule.runOnIdle { focus.onControllerConnected(); assertTrue(focus.requestFocus("overview:entry:${entries.first().key}")) }
        fun focusedIndex() = entries.indexOfFirst { "overview:entry:${it.key}" == focus.focusedId }
        rule.runOnIdle { focus.handle(LauncherAction.Move(Direction.DOWN)) }
        rule.waitForIdle()
        var columns = 0
        rule.runOnIdle { columns = focusedIndex(); assertTrue(columns > 0) }
        rule.runOnIdle { focus.handle(LauncherAction.Move(Direction.UP)) }
        rule.waitForIdle()
        repeat(columns - 1) {
            rule.runOnIdle { focus.handle(LauncherAction.Move(Direction.RIGHT)) }
            rule.waitForIdle()
        }
        var last = columns - 1
        // Move down enough rows to bring initially uncomposed entries into view.
        repeat(entries.size) {
            if (last < entries.lastIndex) {
                rule.runOnIdle { focus.handle(LauncherAction.Move(Direction.DOWN)) }
                rule.waitForIdle()
                rule.runOnIdle {
                    val index = entries.indexOfFirst { "overview:entry:${it.key}" == focus.focusedId }
                    assertTrue("Focus must move down within the ROM grid", index > last)
                    last = index
                }
            }
        }
        rule.runOnIdle {
            assertEquals(entries.lastIndex, last)
            focus.handle(LauncherAction.Confirm)
            assertEquals(entries.last(), chosen)
        }
    }
}
