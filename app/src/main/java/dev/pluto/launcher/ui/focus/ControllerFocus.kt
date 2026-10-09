package dev.pluto.launcher.ui.focus

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.input.Direction
import dev.pluto.launcher.input.LauncherAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
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
        /** Requests focus on the control; false when it can't take focus now. */
        val focus: () -> Boolean,
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
    private val grids = HashMap<String, GridNavigation>()

    /** Last grid move: its target id and the column it is keeping (survives short rows). */
    private var gridAnchorId: String? = null
    private var gridAnchorColumn = 0

    /** A grid move whose target was off screen: where focus is going (base for the next press). */
    private var pendingGridTarget: String? = null

    private var _inputMode by mutableStateOf(InputMode.TOUCH)
    private var _focusedId by mutableStateOf<String?>(null)
    private var _lastFocusedId by mutableStateOf<String?>(null)
    private var _lastActivatedId by mutableStateOf<String?>(null)

    val inputMode: InputMode get() = _inputMode
    val focusedId: String? get() = _focusedId
    val lastFocusedId: String? get() = _lastFocusedId

    /**
     * The control that held focus just before the current one (plain, not observed). Lets a
     * control tell a move along its own row (e.g. tab to tab) from focus arriving elsewhere.
     */
    var previousFocusedId: String? = null
        private set

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
            entry.focus()
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
        if (id == null) return
        _lastFocusedId = id
        // While touch is in control, focus callbacks from the still-focused control (it keeps
        // Compose focus under touch) must not overwrite an explicit choice.
        if (_inputMode == InputMode.TOUCH) touchRestoreId = id
    }

    /** Explicit restore target chosen while touch was in control; wins over [_lastFocusedId]. */
    private var touchRestoreId: String? = null

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

    /**
     * Registers the lazy grid of [surface] for logical Up/Down movement ([GridNavigation]);
     * null unregisters it.
     */
    fun setGrid(surface: String, grid: GridNavigation?) {
        if (grid == null) grids.remove(surface) else grids[surface] = grid
    }

    /**
     * Up/Down inside a registered lazy grid moves by one row in the same (remembered) column,
     * computed from item indices rather than on-screen geometry: during a held D-pad the grid
     * is still animating the previous bring-into-view, and a spatial search over moving bounds
     * lost the column. Returns null when the move leaves the grid (spatial search takes over).
     */
    private fun moveInGrid(direction: Direction): Boolean? {
        if (direction != Direction.UP && direction != Direction.DOWN) return null
        val from = pendingGridTarget?.takeIf { it !in registry || _focusedId == null } ?: _focusedId ?: return null
        pendingGridTarget = null
        val grid = grids.values.firstOrNull { from in it.ids } ?: return null
        val pos = grid.ids.indexOf(from)
        val columns = grid.state.layoutInfo.maxSpan
        if (pos < 0 || columns <= 0) return null
        val column = if (gridAnchorId == from) gridAnchorColumn else pos % columns
        val row = pos / columns + if (direction == Direction.DOWN) 1 else -1
        val lastRow = (grid.ids.size - 1) / columns
        if (row < 0 || row > lastRow) return null
        val target = minOf(row * columns + column, grid.ids.size - 1)
        val id = grid.ids[target]
        gridAnchorId = id
        gridAnchorColumn = column
        if (requestFocus(id)) return true
        // Not composed yet (beyond the viewport): scroll it in, then focus it.
        pendingGridTarget = id
        grid.scope.launch {
            grid.reveal(grid.firstIndex + target, down = direction == Direction.DOWN)
            if (requestFocusWhenReady(id) && pendingGridTarget == id) pendingGridTarget = null
        }
        return true
    }

    internal fun moveFocus(direction: Direction): Boolean {
        moveInGrid(direction)?.let { return it }
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
        val last = touchRestoreId ?: _lastFocusedId
        touchRestoreId = null
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

    internal fun register(id: String, focus: () -> Boolean, activate: () -> Unit, secondary: () -> (() -> Unit)?): Any {
        val entry = Entry(focus, activate, secondary)
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
        if (id != _lastFocusedId) previousFocusedId = _lastFocusedId
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

/**
 * A lazy grid registered for logical Up/Down movement: [ids] are the focus ids of its
 * single-span items, which start on a line boundary at grid index [firstIndex] (after any
 * full-width header items).
 */
class GridNavigation(
    val state: LazyGridState,
    val ids: List<String>,
    val firstIndex: Int,
    val scope: CoroutineScope,
) {
    /** Scrolls by about one line so the item at grid [index] gets composed. */
    suspend fun reveal(index: Int, down: Boolean) {
        val info = state.layoutInfo
        if (info.visibleItemsInfo.any { it.index == index }) return
        val line = info.visibleItemsInfo.lastOrNull()?.size?.height ?: 0
        if (line <= 0) {
            state.scrollToItem(index)
            return
        }
        state.scrollBy(((line + info.mainAxisItemSpacing) * if (down) 1 else -1).toFloat())
        if (state.layoutInfo.visibleItemsInfo.none { it.index == index }) state.scrollToItem(index)
    }
}

val LocalControllerFocus = staticCompositionLocalOf<ControllerFocusController> {
    error("ControllerFocusController not provided")
}

/**
 * Whether content sits beneath the active layer (or is leaving). Controls inside it stay
 * visible but cannot take focus, so spatial navigation never escapes into a covered surface.
 *
 * A chain of snapshot-backed nodes rather than a Boolean composition local: a layer turning
 * inert or active (the drawer opening, a page covering home) only re-evaluates the focus
 * properties of its controls, instead of recomposing every tile that reads it. Read [value]
 * where a Boolean is needed; create a child with [focusInert].
 */
@Stable
class FocusInert internal constructor(private val parent: FocusInert?) {
    internal var own by mutableStateOf(false)

    /** True when this node or any ancestor is inert (snapshot state: observed where read). */
    val value: Boolean get() = own || parent?.value == true

    companion object {
        val None = FocusInert(null)
    }
}

val LocalFocusInert = staticCompositionLocalOf { FocusInert.None }

/** A child of the current [LocalFocusInert] that is additionally inert when [own] is true. */
@Composable
fun focusInert(own: Boolean): FocusInert {
    val parent = LocalFocusInert.current
    val node = remember(parent) { FocusInert(parent) }
    if (Snapshot.withoutReadObservation { node.own } != own) node.own = own
    return node
}

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
 *
 * Implemented as one Modifier.Node (plus Compose's own focusable / clickable nodes): no
 * composition work per control, no coroutine or effect per control until it is focused,
 * registration in the controller on attach/detach, stable accessibility semantics (the
 * custom action is only rebuilt when its label changes), and position tracking for the
 * gliding ring only while this control carries it.
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
): Modifier {
    val link = ControlLink(id, onActivate, onSecondary, onFocused)
    return this
        .then(ControllerFocusableElement(link, shape, contentDescription, secondaryLabel))
        .focusable()
        // The clickable's own (touch-mode dependent) focus target is disabled; the focusable above owns focus.
        .focusProperties(DisableFocus)
        .clip(shape)
        .combinedClickable(
            interactionSource = interactionSource,
            role = Role.Button,
            onLongClickLabel = if (onSecondary != null) secondaryLabel ?: DEFAULT_SECONDARY_LABEL else null,
            onLongClick = if (onSecondary != null) link::secondary else null,
            onClick = link::activate,
        )
}

private val DisableFocus: FocusProperties.() -> Unit = { canFocus = false }

/** The theme's accent for focus tint, selection mark and glow, provided by [ProvideControllerFocus]. */
internal val LocalFocusAccent = staticCompositionLocalOf { Color(0xFF6750A4) }

private const val DEFAULT_SECONDARY_LABEL = "More actions"

/**
 * The per-call callbacks of one control, shared by its focus node and its clickable. The
 * node keeps [controller] current, so a click notes the activation like Confirm does.
 */
private class ControlLink(
    val id: String,
    val onActivate: () -> Unit,
    val onSecondary: (() -> Unit)?,
    val onFocused: (() -> Unit)?,
) {
    var controller: ControllerFocusController? = null

    fun activate() {
        controller?.noteActivated(id)
        onActivate()
    }

    fun secondary() {
        controller?.noteActivated(id)
        onSecondary?.invoke()
    }
}

private class ControllerFocusableElement(
    val link: ControlLink,
    val shape: Shape,
    val contentDescription: String?,
    val secondaryLabel: String?,
) : ModifierNodeElement<ControllerFocusableNode>() {
    override fun create() = ControllerFocusableNode(link, shape, contentDescription, secondaryLabel)

    override fun update(node: ControllerFocusableNode) = node.update(link, shape, contentDescription, secondaryLabel)

    override fun InspectorInfo.inspectableProperties() {
        name = "controllerFocusable"
        properties["id"] = link.id
    }

    // A new link per call carries the newest callbacks; update() is cheap and only touches
    // registration / semantics when the id or the label really changed.
    override fun equals(other: Any?): Boolean =
        other is ControllerFocusableElement && other.link === link && other.shape == shape &&
            other.contentDescription == contentDescription && other.secondaryLabel == secondaryLabel

    override fun hashCode(): Int = System.identityHashCode(link)
}

private class ControllerFocusableNode(
    private var link: ControlLink,
    private var shape: Shape,
    private var label: String?,
    private var secondaryLabel: String?,
) : DelegatingNode(),
    CompositionLocalConsumerModifierNode,
    FocusEventModifierNode,
    FocusPropertiesModifierNode,
    FocusRequesterModifierNode,
    KeyInputModifierNode,
    SemanticsModifierNode,
    DrawModifierNode,
    LayoutAwareModifierNode,
    ObserverModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private var controller: ControllerFocusController? = null
    private var inert: FocusInert = FocusInert.None
    private var token: Any? = null
    private var focused = false
    private var shown = false
    private var size = IntSize.Zero
    private var coordinates: LayoutCoordinates? = null
    private var ring: FocusRingAnimation? = null
    private var ringJob: Job? = null
    private var overlay: FocusRingOverlayState? = null
    private var overlayTarget: FocusRingTarget? = null
    private var tracker: PositionTracker? = null
    private var actions: List<CustomAccessibilityAction>? = null

    override fun onAttach() {
        val c = currentValueOf(LocalControllerFocus)
        controller = c
        inert = currentValueOf(LocalFocusInert)
        // The gliding overlay only covers controls in its own window (not dialogs).
        overlay = currentValueOf(LocalFocusRingOverlay)?.takeIf { it.view === currentValueOf(LocalView) }
        overlayTarget = overlay?.let { FocusRingTarget(shape, currentValueOf(LocalFocusRingVisibility)) }
            ?.also { it.coordinates = coordinates; it.placed = coordinates?.isAttached == true }
        link.controller = c
        register()
    }

    override fun onDetach() {
        unregister()
        stopTracking()
        overlayTarget?.let { overlay?.hide(it) }
        overlayTarget = null
        overlay = null
        ringJob = null
        ring = null
        shown = false
        focused = false
    }

    fun update(link: ControlLink, shape: Shape, contentDescription: String?, secondaryLabel: String?) {
        val old = this.link
        val changedId = isAttached && old.id != link.id
        // Unregister while the OLD id is still attached to its token.
        if (changedId) unregister()
        this.link = link
        link.controller = controller
        if (changedId) {
            register()
            if (focused) controller?.onFocused(link.id)
        }
        if (shape != this.shape) {
            this.shape = shape
            if (isAttached && overlay != null) {
                val wasShown = overlayTarget?.let { overlay?.target === it } == true
                overlayTarget?.let { overlay?.hide(it) }
                overlayTarget = FocusRingTarget(shape, currentValueOf(LocalFocusRingVisibility)).also {
                    it.coordinates = coordinates
                    it.placed = coordinates?.isAttached == true
                    if (wasShown) overlay?.show(it)
                }
                stopTracking()
                if (wasShown) startTracking()
            }
            invalidateDraw()
        }
        if (contentDescription != this.label || secondaryLabel != this.secondaryLabel ||
            (old.onSecondary == null) != (link.onSecondary == null)
        ) {
            this.label = contentDescription
            this.secondaryLabel = secondaryLabel
            actions = null
            invalidateSemantics()
        }
    }

    private fun register() {
        val c = controller ?: return
        token = c.register(
            id = link.id,
            focus = ::focusNow,
            activate = { link.onActivate() },
            secondary = { link.onSecondary },
        )
    }

    private fun unregister() {
        val t = token ?: return
        controller?.unregister(link.id, t)
        token = null
    }

    private fun focusNow(): Boolean =
        try {
            isAttached && requestFocus()
        } catch (e: IllegalStateException) {
            false
        }

    // --- Focus --------------------------------------------------------------------------

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = !inert.value
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == focused) return
        focused = focusState.isFocused
        val c = controller ?: return
        if (focused) {
            c.onFocused(link.id)
            link.onFocused?.invoke()
            val margin = with(requireDensity()) { BringIntoViewMargin.toPx() }
            coroutineScope.launch {
                bringIntoView {
                    Rect(-margin, -margin, size.width + margin, size.height + margin)
                }
            }
        } else {
            c.onBlurred(link.id)
        }
        updateRing()
    }

    override fun onObservedReadsChanged() = updateRing()

    /** Shows the ring while focused in controller mode; observes the input mode only while focused. */
    private fun updateRing() {
        if (!isAttached) return
        val c = controller ?: return
        var show = false
        observeReads { show = focused && c.inputMode == InputMode.CONTROLLER }
        if (show == shown) return
        shown = show
        val target = overlayTarget
        val o = overlay
        if (o != null && target != null) {
            if (show) {
                target.coordinates = coordinates
                target.placed = coordinates?.isAttached == true
                o.show(target)
                startTracking()
            } else {
                o.hide(target)
                stopTracking()
            }
        }
        val r = ring ?: FocusRingAnimation().also { ring = it }
        ringJob?.cancel()
        ringJob = coroutineScope.launch { r.animateTo(show) }
        invalidateDraw()
    }

    /** Reports moves to the overlay while it draws this control's ring (scrolling, reflow). */
    private fun startTracking() {
        if (tracker != null) return
        tracker = delegate(
            PositionTracker { coords ->
                val target = overlayTarget ?: return@PositionTracker
                target.coordinates = coords
                target.placed = true
                overlay?.moved(target)
            },
        )
    }

    private fun stopTracking() {
        val t = tracker ?: return
        tracker = null
        undelegate(t)
    }

    // --- Input, semantics, layout ----------------------------------------------------------

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Keyboard Enter / centre behaves like A. Gamepad buttons arrive via the router instead.
        if (event.key !in ActivationKeys) return false
        if (event.type == KeyEventType.KeyUp) link.activate()
        return true
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false

    override fun SemanticsPropertyReceiver.applySemantics() {
        this@ControllerFocusableNode.label?.let { contentDescription = it }
        if (link.onSecondary != null) {
            customActions = actions ?: listOf(
                CustomAccessibilityAction(this@ControllerFocusableNode.secondaryLabel ?: DEFAULT_SECONDARY_LABEL) {
                    link.onSecondary?.invoke()
                    true
                },
            ).also { actions = it }
        }
    }

    override fun onRemeasured(size: IntSize) {
        this.size = size
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        overlayTarget?.coordinates = coordinates
        overlayTarget?.placed = true
    }

    // --- Drawing -------------------------------------------------------------------------

    override fun ContentDrawScope.draw() {
        drawContent()
        val c = controller ?: return
        if (currentValueOf(LocalShowTouchSelection)) {
            val controllerMode = c.inputMode == InputMode.CONTROLLER
            if (!(focused && controllerMode) && !controllerMode && c.lastFocusedId == link.id) {
                // Persistent, lighter selection mark while touch drives (no geometry change).
                val width = TouchSelectionWidth.toPx()
                drawRing(shape, expand = width / 2f, width = width, color = currentValueOf(LocalFocusAccent))
            }
        }
        val alpha = ring?.alpha?.value ?: 0f
        if (alpha > 0f) {
            val primary = currentValueOf(LocalFocusAccent)
            // Tonal container tint over the content (the focused control's "lift"), then a light
            // inner and dark outer ring with a soft glow, all outside the bounds.
            drawOutline(shape.createOutline(size, layoutDirection, this), primary.copy(alpha = 0.16f), alpha = alpha)
            if (overlayTarget == null) {
                val grow = (ring?.scale?.value ?: 1f) - 1f
                drawFocusRing(
                    shape = shape,
                    topLeft = Offset.Zero,
                    size = size,
                    alpha = alpha,
                    glow = primary,
                    growX = size.width * grow / 2f,
                    growY = size.height * grow / 2f,
                )
            }
        }
    }
}

/** Forwards global position changes; delegated only while the gliding ring follows a control. */
private class PositionTracker(private val onMoved: (LayoutCoordinates) -> Unit) :
    Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onMoved(coordinates)
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
): Modifier = this then ControllerFocusTargetElement(ControlLink(id, onActivate, null, onFocused))

private class ControllerFocusTargetElement(val link: ControlLink) : ModifierNodeElement<ControllerFocusTargetNode>() {
    override fun create() = ControllerFocusTargetNode(link)

    override fun update(node: ControllerFocusTargetNode) = node.update(link)

    override fun InspectorInfo.inspectableProperties() {
        name = "controllerFocusTarget"
        properties["id"] = link.id
    }

    override fun equals(other: Any?): Boolean = other is ControllerFocusTargetElement && other.link === link
    override fun hashCode(): Int = System.identityHashCode(link)
}

private class ControllerFocusTargetNode(private var link: ControlLink) :
    Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    FocusEventModifierNode,
    FocusPropertiesModifierNode,
    FocusRequesterModifierNode {

    override val shouldAutoInvalidate: Boolean get() = false

    private var controller: ControllerFocusController? = null
    private var inert: FocusInert = FocusInert.None
    private var token: Any? = null
    private var focused = false

    override fun onAttach() {
        controller = currentValueOf(LocalControllerFocus)
        inert = currentValueOf(LocalFocusInert)
        link.controller = controller
        register()
    }

    override fun onDetach() {
        unregister()
        focused = false
    }

    fun update(link: ControlLink) {
        val old = this.link
        val changedId = isAttached && old.id != link.id
        // Unregister while the OLD id is still attached to its token.
        if (changedId) unregister()
        this.link = link
        link.controller = controller
        if (changedId) {
            register()
            if (focused) controller?.onFocused(link.id)
        }
    }

    private fun register() {
        val c = controller ?: return
        token = c.register(link.id, focus = ::focusNow, activate = { link.onActivate() }, secondary = { null })
    }

    private fun unregister() {
        val t = token ?: return
        controller?.unregister(link.id, t)
        token = null
    }

    private fun focusNow(): Boolean =
        try {
            isAttached && requestFocus()
        } catch (e: IllegalStateException) {
            false
        }

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = !inert.value
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == focused) return
        focused = focusState.isFocused
        val c = controller ?: return
        if (focused) {
            c.onFocused(link.id)
            link.onFocused?.invoke()
        } else {
            c.onBlurred(link.id)
        }
    }
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
    CompositionLocalProvider(
        LocalControllerFocus provides controller,
        LocalFocusAccent provides MaterialTheme.colorScheme.primary,
    ) {
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

