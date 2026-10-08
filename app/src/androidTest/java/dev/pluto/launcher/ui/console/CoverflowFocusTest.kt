package dev.pluto.launcher.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.input.Direction
import dev.pluto.launcher.input.LauncherAction
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.ProvideControllerFocus
import dev.pluto.launcher.ui.focus.controllerFocusable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Controller traversal of the Coverflow: a held D-pad must walk card to card. Regression for
 * focus escaping to the first header control once the composed window of cards advanced
 * (the cards were disposed and recreated, the newly focused one included).
 */
@RunWith(AndroidJUnit4::class)
class CoverflowFocusTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val apps = List(20) { i -> AppEntry(AppKey("com.example.app$i/.Main", 0), "App $i") }
    private fun cardId(i: Int) = cardFocusId(SHELF, apps[i].key)

    private fun setUp(stage: CoverflowState, focus: ControllerFocusController) {
        val icons = IconCache(InstrumentationRegistry.getInstrumentation().targetContext)
        rule.setContent {
            MaterialTheme {
                ProvideControllerFocus(focus) {
                    val scope = rememberCoroutineScope()
                    Column {
                        // Stands in for the header's first tab: where focus used to escape to.
                        Box(Modifier.size(48.dp).controllerFocusable(id = HEADER, onActivate = {}))
                        CoverflowStage(
                            stage = stage,
                            shelfKey = SHELF,
                            icons = icons,
                            versions = emptyMap(),
                            page = PageMotion.Static,
                            hero = HeroEmphasis.Static,
                            onActivate = { _, _ -> },
                            onActions = {},
                            onFocused = { index, _ -> stage.select(index, scope) },
                            onTouchSelect = {},
                            upTarget = { FocusRequester.Default },
                            downTarget = { FocusRequester.Default },
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun heldRightWalksCardToCard() {
        val focus = ControllerFocusController()
        val stage = CoverflowState(0, apps)
        setUp(stage, focus)
        rule.runOnIdle {
            focus.onControllerConnected()
            assertTrue(focus.requestFocus(cardId(0)))
        }
        // Held D-pad: one step per 100 ms repeat, the flow never settles in between.
        rule.mainClock.autoAdvance = false
        for (step in 1..12) {
            rule.runOnUiThread { focus.handle(LauncherAction.Move(Direction.RIGHT)) }
            rule.mainClock.advanceTimeBy(100)
            rule.runOnUiThread { assertEquals("after RIGHT #$step", cardId(step), focus.focusedId) }
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(cardId(12), focus.focusedId)
            assertEquals(12, stage.selectedIndex)
        }
    }

    @Test
    fun singleStepsBothWaysKeepFocusInTheFlow() {
        val focus = ControllerFocusController()
        val stage = CoverflowState(8, apps)
        setUp(stage, focus)
        rule.runOnIdle {
            focus.onControllerConnected()
            assertTrue(focus.requestFocus(cardId(8)))
        }
        val expected = listOf(9, 10, 11, 10, 9, 8, 7, 6, 5)
        val moves = listOf(Direction.RIGHT, Direction.RIGHT, Direction.RIGHT) + List(6) { Direction.LEFT }
        moves.zip(expected).forEach { (direction, index) ->
            rule.runOnIdle { focus.handle(LauncherAction.Move(direction)) }
            rule.waitForIdle()
            rule.runOnIdle { assertEquals(cardId(index), focus.focusedId) }
        }
    }

    private companion object {
        const val SHELF = "test"
        const val HEADER = "header"
    }
}
