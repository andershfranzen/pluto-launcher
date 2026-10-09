package dev.pluto.launcher.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.ui.focus.ControllerFocusController
import dev.pluto.launcher.ui.focus.ProvideControllerFocus
import dev.pluto.launcher.ui.focus.controllerFocusable
import dev.pluto.launcher.ui.focus.controllerFocusTarget
import dev.pluto.launcher.ui.home.Stagger
import dev.pluto.launcher.ui.motion.DrawerRevealState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MotionRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun replacingFocusableIdDoesNotLeaveAStaleTarget() = checkIdChange(targetOnly = false)
    @Test fun replacingFocusTargetIdDoesNotLeaveAStaleTarget() = checkIdChange(targetOnly = true)

    private fun checkIdChange(targetOnly: Boolean) {
        val focus = ControllerFocusController()
        var id by mutableStateOf("old")
        var visible by mutableStateOf(true)
        rule.setContent {
            ProvideControllerFocus(focus) {
                if (visible) Box(
                    Modifier.size(48.dp).then(
                        if (targetOnly) Modifier.controllerFocusTarget(id, onActivate = {}).focusable()
                        else Modifier.controllerFocusable(id, onActivate = {}),
                    ),
                )
            }
        }
        rule.runOnIdle { assertTrue(focus.requestFocus("old")); id = "new" }
        rule.runOnIdle {
            assertFalse("old id must have been unregistered", focus.requestFocus("old"))
            assertTrue(focus.requestFocus("new"))
            assertEquals("new", focus.focusedId)
            visible = false
        }
        rule.runOnIdle {
            assertFalse(focus.requestFocus("old"))
            assertFalse(focus.requestFocus("new"))
        }
    }

    @Test
    fun interruptedStaggerCanFinishAndReplayWithoutHiddenRows() {
        val stagger = Stagger(0, 24, 220, 5, animate = false)
        lateinit var scope: CoroutineScope
        rule.setContent { scope = rememberCoroutineScope() }
        rule.mainClock.autoAdvance = false
        lateinit var job: Job
        rule.runOnUiThread { stagger.arm(); job = scope.launch { stagger.replay() } }
        rule.mainClock.advanceTimeBy(80)
        rule.runOnUiThread { job.cancel(); scope.launch { stagger.finish() } }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnUiThread { assertEquals(1f, stagger.progress(4), 0f) }
        rule.runOnUiThread { stagger.arm(); scope.launch { stagger.replay() } }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnUiThread { assertEquals(1f, stagger.progress(4), 0f) }
    }

    @Test
    fun strongFlingNeverMovesDrawerPastEitherRestingEdge() {
        val reveal = DrawerRevealState()
        lateinit var scope: CoroutineScope
        rule.setContent { scope = rememberCoroutineScope() }
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            scope.launch {
                reveal.beginDrag()
                reveal.dragBy(950f, 1000f)
                reveal.release(50_000f, 1000f)
                reveal.settle(open = true)
            }
        }
        repeat(60) {
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread { assertTrue("progress=${reveal.progress}", reveal.progress in 0f..1f) }
        }
        rule.runOnUiThread { assertEquals(1f, reveal.progress, 0.001f) }
        rule.runOnUiThread {
            scope.launch {
                reveal.beginDrag()
                reveal.dragBy(-900f, 1000f)
                reveal.release(-50_000f, 1000f)
                reveal.settle(open = false)
            }
        }
        repeat(60) {
            rule.mainClock.advanceTimeByFrame()
            rule.runOnUiThread { assertTrue("progress=${reveal.progress}", reveal.progress in 0f..1f) }
        }
        rule.runOnUiThread { assertEquals(0f, reveal.progress, 0.001f) }
    }
}
