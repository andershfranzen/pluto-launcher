package dev.pluto.launcher.ui.focus

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.input.Direction
import dev.pluto.launcher.input.LauncherAction

enum class InputMode { TOUCH, CONTROLLER }

/**
 * Controller focus model shared by every screen.
 *
 * Each focusable control registers under a stable string id (app key, "dock:2",
 * "folder:5", "settings:theme", ...). Movement uses Compose's spatial focus search
 * (FocusManager.moveFocus), so focus order follows on-screen geometry in every layout.
 *
 * Rules (from the spec):
 * - Focus is drawn as a thick outline + contrast change *outside the layout bounds*
 *   (no size/geometry change), using shape and contrast, not colour alone.
 * - Touch takes over immediately: [onTouch] switches to TOUCH mode and hides focus
 *   indication; [lastFocusedId] is remembered. The next controller *movement* first
 *   restores focus to [lastFocusedId] (or the screen's default) without moving or
 *   activating anything. Confirm with nothing focused only restores focus too.
 * - Layers: [pushLayer] records the opener id; [popLayer] returns it so the caller
 *   can restore focus to the control that opened the layer.
 * - Focused items scroll fully into view (bring-into-view) including clearance for
 *   docks / system bars, via content padding in each layout.
 */
class ControllerFocusController {
    val inputMode: InputMode get() = TODO()
    val focusedId: String? get() = TODO()
    val lastFocusedId: String? get() = TODO()

    fun onTouch(): Unit = TODO()

    /**
     * Handles focus-level actions: Move, Confirm, Actions. Returns false for actions
     * the caller must handle itself (Back, Search, Settings, Prev/NextCategory), and for
     * Confirm/Actions when nothing suitable is focused.
     */
    fun handle(action: LauncherAction): Boolean = TODO()

    /** Requests focus on a registered id. Returns false if not currently composed. */
    fun requestFocus(id: String): Boolean = TODO()

    /** Sets which id should receive focus when nothing better is known (per screen). */
    fun setDefaultFocus(id: String?): Unit = TODO()

    fun pushLayer(openerId: String?): Unit = TODO()
    fun popLayer(): String? = TODO()

    internal fun moveFocus(direction: Direction): Boolean = TODO()
}

val LocalControllerFocus = staticCompositionLocalOf<ControllerFocusController> {
    error("ControllerFocusController not provided")
}

/**
 * Makes a control controller-focusable and touch-clickable with identical behaviour.
 * [onActivate] runs for touch click, A/Confirm, Enter. [onSecondary] runs for the
 * X/Actions button and is also exposed as an accessibility custom action (no long-press
 * dependency). [contentDescription] becomes the screen-reader label.
 * Minimum touch target 48dp is the caller's layout responsibility (tiles are larger).
 */
fun Modifier.controllerFocusable(
    id: String,
    onActivate: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    onFocused: (() -> Unit)? = null,
): Modifier = TODO()

/** Provides a [ControllerFocusController] to [content]. */
@Composable
fun ProvideControllerFocus(controller: ControllerFocusController, content: @Composable () -> Unit): Unit = TODO()
