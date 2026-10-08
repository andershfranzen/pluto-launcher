package dev.pluto.launcher.ui.focus

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.input.Direction
import dev.pluto.launcher.input.LauncherAction
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.input.InputMode as ComposeInputMode

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
 * - Controller connection: [onControllerConnected] switches to controller presentation
 *   (without moving or activating anything); [onControllerDisconnected] switches back to
 *   touch presentation and keeps the current control as the remembered selection.
 * - Vanished controls: lists report their ordered ids per surface ([setOrder]); when the
 *   remembered control no longer exists, focus goes to its nearest surviving neighbour
 *   ([FocusOrders]) instead of the screen default.
 *
 * All members must be used from the main thread (they touch Compose focus).
 */
class ControllerFocusController {
    /** A registered control. Callbacks are read through providers so they always see the latest lambdas. */
    private class Entry(
        val requester: FocusRequester,
        val activate: () -> Unit,
        val secondary: () -> (() -> Unit)?,
    )

    /** One window's focus manager (the activity, or a dialog that re-provides the controller). */
    private class Attachment(val focusManager: FocusManager, val inputModeManager: InputModeManager?) {
        var hasFocus by mutableStateOf(false)
    }

    private val registry = HashMap<String, Entry>()
    private val attachments = ArrayList<Attachment>()
    private val layerOpeners = ArrayList<String?>()
    private var defaultFocusId: String? = null
    private val orders = FocusOrders()
    private val scrollers = HashMap<String, suspend (Int) -> Unit>()

    private var _inputMode by mutableStateOf(InputMode.TOUCH)
    private var _focusedId by mutableStateOf<String?>(null)
    private var _lastFocusedId by mutableStateOf<String?>(null)
    private var _lastActivatedId by mutableStateOf<String?>(null)

    val inputMode: InputMode get() = _inputMode
    val focusedId: String? get() = _focusedId
    val lastFocusedId: String? get() = _lastFocusedId

    /** The control most recently activated by touch, Confirm or Enter. */
    val lastActivatedId: String? get() = _lastActivatedId

    /**
     * Best guess at the control that is opening a new layer: the focused control while
     * a controller drives, otherwise the control that was just tapped.
     */
    val openerId: String?
        get() = if (_inputMode == InputMode.CONTROLLER) _focusedId ?: _lastActivatedId else _lastActivatedId ?: _focusedId

    private val currentAttachment: Attachment? get() = attachments.lastOrNull()

    /** True when any Compose element (including unregistered ones such as text fields) holds focus. */
    private val anythingFocused: Boolean
        get() = currentAttachment?.hasFocus ?: (_focusedId != null)

    fun onTouch() {
        if (_inputMode != InputMode.TOUCH) _inputMode = InputMode.TOUCH
    }

    /**
     * A controller was attached: show controller presentation (focus ring, hints) right away.
     * Nothing is moved or activated; layouts restore focus to the selection themselves.
     */
    fun onControllerConnected() {
        enterControllerMode()
    }

    /**
     * The last controller was removed: switch to touch presentation immediately and keep the
     * current control as the selection the next controller/keyboard input returns to.
     */
    fun onControllerDisconnected() {
        rememberFocusTarget(_focusedId ?: _lastFocusedId)
        if (_inputMode != InputMode.TOUCH) _inputMode = InputMode.TOUCH
        try {
            currentAttachment?.inputModeManager?.requestInputMode(ComposeInputMode.Touch)
        } catch (e: RuntimeException) {
            // Best effort only.
        }
    }

    /**
     * A hardware keyboard navigation key (arrows / Tab) was pressed. Compose moves focus
     * itself; we only make the focus outline visible again.
     */
    fun onKeyboardNavigation() {
        enterControllerMode()
    }

    /**
     * Handles focus-level actions: Move, Confirm, Actions. Returns false for actions
     * the caller must handle itself (Back, Search, Settings, Prev/NextCategory), and for
     * Confirm/Actions when nothing suitable is focused. Every controller action switches to
     * controller presentation (so hints appear), but only Move/Confirm/Actions touch focus.
     */
    fun handle(action: LauncherAction): Boolean = when (action) {
        is LauncherAction.Move -> {
            if (_inputMode == InputMode.TOUCH || !anythingFocused) {
                // First controller input after touch (or with nothing focused) only restores focus.
                enterControllerMode()
                restoreFocus()
            } else {
                moveFocus(action.direction)
            }
            true
        }

        LauncherAction.Confirm, LauncherAction.Actions -> {
            if (_inputMode == InputMode.TOUCH || !anythingFocused) {
                // Switching input mode never activates anything.
                enterControllerMode()
                restoreFocus()
                true
            } else {
                val id = _focusedId
                val entry = id?.let(registry::get)
                when {
                    entry == null -> false
                    action == LauncherAction.Confirm -> {
                        _lastActivatedId = id
                        entry.activate()
                        true
                    }
                    else -> {
                        val secondary = entry.secondary()
                        if (secondary != null) {
                            _lastActivatedId = id
                            secondary()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }

        else -> {
            enterControllerMode()
            false
        }
    }

    /** Requests focus on a registered id. Returns false if not currently composed. */
    fun requestFocus(id: String): Boolean {
        val entry = registry[id] ?: return false
        return try {
            entry.requester.requestFocus(FocusDirection.Enter)
        } catch (e: IllegalStateException) {
            // Requester registered but its node is not attached (yet / any more).
            false
        }
    }

    /**
     * Like [requestFocus] but retries for a few frames, for targets that are about to be
     * composed (a layer that just closed, a lazy item that was just scrolled to).
     * Must be called from a coroutine with a frame clock (LaunchedEffect / composition scope).
     */
    suspend fun requestFocusWhenReady(id: String, maxFrames: Int = 8): Boolean {
        repeat(maxFrames) {
            if (requestFocus(id)) return true
            withFrameNanos { }
        }
        return requestFocus(id)
    }

    /** Sets which id should receive focus when nothing better is known (per screen). */
    fun setDefaultFocus(id: String?) {
        defaultFocusId = id
    }

    /**
     * Remembers [id] as the place the next controller input should restore focus to,
     * without focusing it now (used while touch is in control, e.g. after rotation).
     */
    fun rememberFocusTarget(id: String?) {
        if (id != null) _lastFocusedId = id
    }

    /** True when a composable currently owns [id]. */
    fun isRegistered(id: String): Boolean = id in registry

    /**
     * Records the on-screen order of the focus ids in one list ([surface]), and optionally how
     * to scroll that list to an index so an off-screen neighbour can be composed and focused.
     */
    fun setOrder(surface: String, ids: List<String>, scrollTo: (suspend (Int) -> Unit)? = null) {
        orders.set(surface, ids)
        if (scrollTo != null) scrollers[surface] = scrollTo else scrollers.remove(surface)
    }

    /** Forgets how to scroll [surface] (its list left composition); the order is kept. */
    fun clearScroller(surface: String) {
        scrollers.remove(surface)
    }

    /**
     * The id that stands in for [id]: [id] itself if it still exists in its list, else its
     * nearest surviving neighbour. Null if [id] belongs to no known list.
     */
    fun replacementFor(id: String): String? = orders.replacementFor(id)?.id

    /**
     * Focuses [id], or, if it vanished, its nearest surviving neighbour in the same list
     * (scrolling the list first when needed). Returns false when neither could be focused.
     */
    suspend fun focusOrNeighbour(id: String): Boolean {
        if (requestFocus(id)) return true
        val target = orders.replacementFor(id) ?: return requestFocusWhenReady(id)
        if (target.id == id) {
            // Still listed but not composed yet (layer just closed) or scrolled away.
            if (requestFocusWhenReady(id, maxFrames = 3)) return true
        } else if (requestFocus(target.id)) {
            return true
        }
        if (target.index >= 0) scrollers[target.surface]?.invoke(target.index)
        return requestFocusWhenReady(target.id)
    }

    /**
     * After a list changed: if the remembered control in [surface] vanished while nothing
     * holds focus, move controller focus to its nearest surviving neighbour.
     */
    suspend fun followVanished(surface: String) {
        if (_inputMode != InputMode.CONTROLLER || _focusedId != null) return
        val last = _lastFocusedId ?: return
        if (last in registry || orders.surfaceOf(last) != surface) return
        focusOrNeighbour(last)
    }

    fun pushLayer(openerId: String?) {
        layerOpeners += openerId
    }

    fun popLayer(): String? {
        val opener = if (layerOpeners.isEmpty()) null else layerOpeners.removeAt(layerOpeners.lastIndex)
        // Whatever happens next, the next controller action should return to the opener.
        if (opener != null) _lastFocusedId = opener
        return opener
    }

    internal fun moveFocus(direction: Direction): Boolean {
        val manager = currentAttachment?.focusManager ?: return false
        val focusDirection = when (direction) {
            Direction.UP -> FocusDirection.Up
            Direction.DOWN -> FocusDirection.Down
            Direction.LEFT -> FocusDirection.Left
            Direction.RIGHT -> FocusDirection.Right
        }
        return try {
            manager.moveFocus(focusDirection)
        } catch (e: IllegalStateException) {
            false
        }
    }

    /**
     * Restores focus to the last meaningful control (or, if it vanished, its nearest surviving
     * neighbour), the screen default, or the first focusable.
     */
    private fun restoreFocus(): Boolean {
        val last = _lastFocusedId
        val neighbour = last?.takeIf { it !in registry }?.let(::replacementFor)
        listOfNotNull(last, neighbour, defaultFocusId).distinct().forEach { if (requestFocus(it)) return true }
        val manager = currentAttachment?.focusManager ?: return false
        return try {
            manager.moveFocus(FocusDirection.Next) || manager.moveFocus(FocusDirection.Enter)
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun enterControllerMode() {
        if (_inputMode != InputMode.CONTROLLER) _inputMode = InputMode.CONTROLLER
        // Leave Android touch mode so system-defined focusables (text fields, Material controls)
        // accept focus again. Harmless if already in keyboard mode.
        try {
            currentAttachment?.inputModeManager?.requestInputMode(ComposeInputMode.Keyboard)
        } catch (e: RuntimeException) {
            // Best effort only.
        }
    }

    // --- Registration (used by the modifiers below) ------------------------

    internal fun register(id: String, requester: FocusRequester, activate: () -> Unit, secondary: () -> (() -> Unit)?): Any {
        val entry = Entry(requester, activate, secondary)
        registry[id] = entry
        return entry
    }

    internal fun unregister(id: String, token: Any) {
        // A newer composable may have taken over the id (e.g. during a layout switch); keep it.
        if (registry[id] === token) {
            registry.remove(id)
            if (_focusedId == id) _focusedId = null
        }
    }

    internal fun onFocused(id: String) {
        _focusedId = id
        _lastFocusedId = id
    }

    internal fun onBlurred(id: String) {
        if (_focusedId == id) _focusedId = null
    }

    internal fun noteActivated(id: String) {
        _lastActivatedId = id
    }

    internal fun newAttachment(focusManager: FocusManager, inputModeManager: InputModeManager?): Any =
        Attachment(focusManager, inputModeManager)

    internal fun attach(token: Any) {
        if (token is Attachment && token !in attachments) attachments += token
    }

    internal fun detach(token: Any) {
        attachments.remove(token)
    }

    internal fun setHasFocus(token: Any, hasFocus: Boolean) {
        (token as? Attachment)?.hasFocus = hasFocus
    }
}

val LocalControllerFocus = staticCompositionLocalOf<ControllerFocusController> {
    error("ControllerFocusController not provided")
}

/**
 * True for content that sits beneath the active layer. Controls inside it stay visible
 * but cannot take focus, so spatial navigation never escapes into a covered surface.
 */
val LocalFocusInert = compositionLocalOf { false }

/**
 * True where the remembered selection should stay visible while touch is in control
 * (Handheld mode): the selected control gets a thin outline instead of the controller ring.
 */
val LocalShowTouchSelection = compositionLocalOf { false }

private val TouchSelectionWidth = 2.dp

/** Extra clearance requested around a focused control when scrolling it into view. */
private val BringIntoViewMargin = 12.dp

/**
 * Makes a control controller-focusable and touch-clickable with identical behaviour.
 * [onActivate] runs for touch click, A/Confirm, Enter. [onSecondary] runs for the
 * X/Actions button, a touch long press (a shortcut only: every screen also offers a
 * visible route) and is exposed as an accessibility custom action. [contentDescription]
 * becomes the screen-reader label.
 * Minimum touch target 48dp is the caller's layout responsibility (tiles are larger).
 *
 * The focus ring animates (see FocusRing.kt): it fades in while settling from slightly
 * larger, fades out on blur or when touch takes over, and glides between controls when the
 * root enables [FocusRingOverlayHost]. [interactionSource] receives the touch press
 * interactions (e.g. for press-scale feedback).
 */
fun Modifier.controllerFocusable(
    id: String,
    onActivate: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    contentDescription: String? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier = composed {
    val controller = LocalControllerFocus.current
    val inert = LocalFocusInert.current
    val showTouchSelection = LocalShowTouchSelection.current
    val requester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val clickInteraction = interactionSource ?: remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val currentActivate by rememberUpdatedState(onActivate)
    val currentSecondary by rememberUpdatedState(onSecondary)
    val currentOnFocused by rememberUpdatedState(onFocused)
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val selectionColor = MaterialTheme.colorScheme.primary
    val marginPx = with(LocalDensity.current) { BringIntoViewMargin.toPx() }
    val glow = MaterialTheme.colorScheme.primary
    val ring = remember { FocusRingAnimation() }
    // The gliding overlay only covers controls in its own window (not dialogs).
    val overlay = LocalFocusRingOverlay.current?.takeIf { it.view === LocalView.current }
    val overlayTarget = remember(overlay, shape) { overlay?.let { FocusRingTarget(shape) } }

    LaunchedEffect(controller, overlay, overlayTarget) {
        snapshotFlow { focused && controller.inputMode == InputMode.CONTROLLER }.collectLatest { show ->
            if (overlay != null && overlayTarget != null) {
                if (show) overlay.show(overlayTarget) else overlay.hide(overlayTarget)
            }
            ring.animateTo(show)
        }
    }
    if (overlay != null && overlayTarget != null) {
        DisposableEffect(overlay, overlayTarget) {
            onDispose { overlay.hide(overlayTarget) }
        }
    }

    DisposableEffect(controller, id) {
        val token = controller.register(
            id = id,
            requester = requester,
            activate = { currentActivate() },
            secondary = { currentSecondary },
        )
        onDispose { controller.unregister(id, token) }
    }

    val activate = {
        controller.noteActivated(id)
        currentActivate()
    }
    val longPress: (() -> Unit)? = if (onSecondary == null) {
        null
    } else {
        {
            controller.noteActivated(id)
            currentSecondary?.invoke()
        }
    }

    this
        .onSizeChanged { size = it }
        .bringIntoViewRequester(bringIntoView)
        .onFocusChanged { state ->
            if (state.isFocused == focused) return@onFocusChanged
            focused = state.isFocused
            if (state.isFocused) {
                controller.onFocused(id)
                currentOnFocused?.invoke()
                scope.launch {
                    bringIntoView.bringIntoView(
                        Rect(-marginPx, -marginPx, size.width + marginPx, size.height + marginPx),
                    )
                }
            } else {
                controller.onBlurred(id)
            }
        }
        .focusRequester(requester)
        .focusProperties { canFocus = !inert }
        .onKeyEvent { event ->
            // Keyboard Enter / centre behaves like A. Gamepad buttons arrive via the router instead.
            if (event.key in ActivationKeys) {
                if (event.type == KeyEventType.KeyUp) activate()
                true
            } else {
                false
            }
        }
        .focusable()
        .semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
            val secondary = onSecondary
            if (secondary != null) {
                customActions = listOf(
                    CustomAccessibilityAction(secondaryLabel ?: "More actions") { secondary(); true },
                )
            }
        }
        .then(
            if (overlayTarget != null) {
                Modifier.onGloballyPositioned { coordinates ->
                    overlayTarget.coordinates = coordinates
                    overlay?.moved(overlayTarget)
                }
            } else {
                Modifier
            },
        )
        .drawWithContent {
            val controllerMode = controller.inputMode == InputMode.CONTROLLER
            val show = focused && controllerMode
            drawContent()
            if (!show && showTouchSelection && !controllerMode && controller.lastFocusedId == id) {
                // Persistent, lighter selection mark while touch drives (no geometry change).
                val width = TouchSelectionWidth.toPx()
                drawRing(shape, expand = width / 2f, width = width, color = selectionColor)
            }
            val alpha = ring.alpha.value
            if (alpha > 0f) {
                // Tonal container tint over the content (the focused control's "lift"), then a light
                // inner and dark outer ring with a soft glow, all outside the bounds.
                drawOutline(shape.createOutline(this.size, layoutDirection, this), tint, alpha = alpha)
                if (overlayTarget == null) {
                    val grow = ring.scale.value - 1f
                    drawFocusRing(
                        shape = shape,
                        topLeft = Offset.Zero,
                        size = this.size,
                        alpha = alpha,
                        glow = glow,
                        growX = this.size.width * grow / 2f,
                        growY = this.size.height * grow / 2f,
                    )
                }
            }
        }
        // The clickable's own (touch-mode dependent) focus target is disabled; the focusable above owns focus.
        .focusProperties { canFocus = false }
        .clip(shape)
        .combinedClickable(
            interactionSource = clickInteraction,
            indication = LocalIndication.current,
            role = Role.Button,
            onLongClickLabel = if (longPress != null) secondaryLabel ?: "More actions" else null,
            onLongClick = longPress,
            onClick = activate,
        )
}

/**
 * Registers an element that owns its own focus target (e.g. a text field) under [id] so it
 * participates in default focus, layer restore and [ControllerFocusController.requestFocus].
 * [onActivate] runs on A/Confirm while it is focused (e.g. show the keyboard).
 */
fun Modifier.controllerFocusTarget(
    id: String,
    onActivate: () -> Unit = {},
    onFocused: (() -> Unit)? = null,
): Modifier = composed {
    val controller = LocalControllerFocus.current
    val inert = LocalFocusInert.current
    val requester = remember { FocusRequester() }
    val currentActivate by rememberUpdatedState(onActivate)
    val currentOnFocused by rememberUpdatedState(onFocused)
    var focused by remember { mutableStateOf(false) }

    DisposableEffect(controller, id) {
        val token = controller.register(id, requester, activate = { currentActivate() }, secondary = { null })
        onDispose { controller.unregister(id, token) }
    }

    this
        .onFocusChanged { state ->
            if (state.isFocused == focused) return@onFocusChanged
            focused = state.isFocused
            if (state.isFocused) {
                controller.onFocused(id)
                currentOnFocused?.invoke()
            } else {
                controller.onBlurred(id)
            }
        }
        .focusRequester(requester)
        .focusProperties { canFocus = !inert }
}

/**
 * Provides a [ControllerFocusController] to [content]. May be called again with the same
 * controller inside a separate window (e.g. a Dialog) so movement uses that window's focus.
 */
@Composable
fun ProvideControllerFocus(controller: ControllerFocusController, content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val token = remember(controller, focusManager, inputModeManager) {
        controller.newAttachment(focusManager, inputModeManager)
    }
    DisposableEffect(token) {
        controller.attach(token)
        onDispose { controller.detach(token) }
    }
    CompositionLocalProvider(LocalControllerFocus provides controller) {
        Box(Modifier.onFocusChanged { controller.setHasFocus(token, it.hasFocus) }, propagateMinConstraints = true) {
            content()
        }
    }
}

private val ActivationKeys = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

private fun DrawScope.drawRing(shape: Shape, expand: Float, width: Float, color: Color) {
    val ringSize = Size(size.width + expand * 2f, size.height + expand * 2f)
    val outline = shape.createOutline(ringSize, layoutDirection, this)
    translate(left = -expand, top = -expand) {
        drawOutline(outline, color, style = Stroke(width))
    }
}

