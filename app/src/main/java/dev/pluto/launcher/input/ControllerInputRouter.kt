package dev.pluto.launcher.input

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import dev.pluto.launcher.data.prefs.ButtonMapping
import dev.pluto.launcher.data.prefs.ControllerAction
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.domain.ControllerClassifier
import dev.pluto.launcher.domain.InputDeduper
import dev.pluto.launcher.domain.StickRepeater
import kotlin.math.abs
import kotlin.math.max

/**
 * Converts raw gamepad KeyEvents / MotionEvents into [LauncherAction]s.
 * Called from the Activity's dispatchKeyEvent / dispatchGenericMotionEvent.
 *
 * - Only handles events whose source is a gamepad/joystick (ControllerClassifier);
 *   returns false for everything else so keyboards and touch behave normally.
 * - D-pad keys, HAT axes and the left stick all produce Move, deduplicated across sources
 *   (InputDeduper) and with stick repeat (StickRepeater, driven by a main-thread Handler).
 *   D-pad *key* repeats use the same delay/interval rather than the system key repeat.
 * - Buttons map through settings.mappingFor(device): action fires on ACTION_UP of a key whose
 *   ACTION_DOWN was seen (prevents a launch carried over from a previous screen / app),
 *   except movement which fires on down. Long-press does not trigger anything.
 * - System Home / Recents (KEYCODE_HOME, KEYCODE_APP_SWITCH) are never consumed.
 * - [onRawKey], if set, receives every controller key-down before mapping (used by the
 *   remapping/diagnostics screen); if it returns true the event is consumed and not mapped.
 *
 * Must be used from the main thread only.
 */
class ControllerInputRouter(private val onAction: (LauncherAction) -> Unit) {
    var settings: LauncherSettings = LauncherSettings()
    var onRawKey: ((KeyEvent) -> Boolean)? = null
    /** Diagnostics: last raw axis values seen (x, y, hatX, hatY) for the diagnostics screen. */
    var onRawMotion: ((MotionEvent) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val deduper = InputDeduper()
    private val stick = StickRepeater()

    /** Keys (per device) whose ACTION_DOWN reached this router since the last [reset]. */
    private val downKeys = HashSet<Long>()
    /** Keys whose DOWN was swallowed by [onRawKey]; their UP must not be mapped either. */
    private val rawConsumedKeys = HashSet<Long>()
    /** Per-device classification cache (hasKeys is an IPC); cleared on [reset]. */
    private val deviceIsController = HashMap<Int, Boolean>()

    /** The digital (D-pad key or HAT) direction currently repeating, if any. */
    private var digitalDirection: Direction? = null
    private var digitalSource: MoveSource? = null
    private var digitalKey: Long? = null
    private var hatDirection: Direction? = null

    /** Last button action, so one physical press reported under two key codes acts once. */
    private var lastButtonAction: ControllerAction? = null
    private var lastButtonKeyCode: Int = 0
    private var lastButtonAtMs: Long = 0

    private val digitalRepeat = object : Runnable {
        override fun run() {
            val direction = digitalDirection ?: return
            val source = digitalSource ?: return
            emitMove(direction, source)
            handler.postDelayed(this, settings.repeatIntervalMs.toLong().coerceAtLeast(MIN_INTERVAL_MS))
        }
    }

    private val stickTick = Runnable {
        stick.onTick(now())?.let { emitMove(it, MoveSource.STICK) }
        scheduleStickTick()
    }

    /** Returns true if consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode in NEVER_CONSUMED) return false
        if (!isFromController(event.source, event.device)) return false

        val id = keyId(event.deviceId, keyCode)
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> onKeyDown(event, id)
            KeyEvent.ACTION_UP -> onKeyUp(event, id)
            // ACTION_MULTIPLE is legacy; swallow it for gamepad keys so nothing double-handles.
            else -> isGamepadKey(keyCode)
        }
    }

    private fun onKeyDown(event: KeyEvent, id: Long): Boolean {
        val keyCode = event.keyCode
        if (event.repeatCount > 0) {
            // We drive our own repeat; swallow system repeats of keys we own.
            return id in downKeys || id in rawConsumedKeys
        }
        if (onRawKey?.invoke(event) == true) {
            rawConsumedKeys += id
            return true
        }
        val mapping = mappingFor(event)
        val action = mapping.actionFor(keyCode)
        val direction = dpadDirection(keyCode)
        if (action == null && direction != null) {
            downKeys += id
            startDigital(direction, MoveSource.KEY, id)
            return true
        }
        if (action != null || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || isGamepadKey(keyCode)) {
            // Remember the press; the action itself fires on release.
            downKeys += id
            return true
        }
        return false
    }

    private fun onKeyUp(event: KeyEvent, id: Long): Boolean {
        val keyCode = event.keyCode
        if (rawConsumedKeys.remove(id)) return true
        val wasDown = downKeys.remove(id)

        if (digitalKey == id) stopDigital()

        val mapping = mappingFor(event)
        val action = mapping.actionFor(keyCode)
            ?: ControllerAction.CONFIRM.takeIf { keyCode == KeyEvent.KEYCODE_DPAD_CENTER }
        if (action != null) {
            val canceled = (event.flags and KeyEvent.FLAG_CANCELED) != 0
            if (wasDown && !canceled) fireButton(action, keyCode)
            return true
        }
        // Unmapped gamepad buttons and D-pad releases are consumed so the system doesn't
        // synthesize fallback keys (e.g. BUTTON_A -> DPAD_CENTER) from them.
        return wasDown || dpadDirection(keyCode) != null || isGamepadKey(keyCode)
    }

    private fun fireButton(action: ControllerAction, keyCode: Int) {
        val now = now()
        val duplicate = action == lastButtonAction && keyCode != lastButtonKeyCode &&
            now - lastButtonAtMs < DUPLICATE_BUTTON_WINDOW_MS
        lastButtonAction = action
        lastButtonKeyCode = keyCode
        lastButtonAtMs = now
        if (duplicate) return
        onAction(action.toLauncherAction())
    }

    /** Returns true if consumed. */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Only joystick-class motion (sticks, HAT); touch, mouse and trackpads pass through.
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) return false
        if (!isFromController(event.source, event.device)) return false
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return false
        onRawMotion?.invoke(event)
        handleHat(event)
        handleStick(event)
        return true
    }

    private fun handleHat(event: MotionEvent) {
        val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val horizontal = when {
            x <= -HAT_THRESHOLD -> Direction.LEFT
            x >= HAT_THRESHOLD -> Direction.RIGHT
            else -> null
        }
        val vertical = when {
            y <= -HAT_THRESHOLD -> Direction.UP
            y >= HAT_THRESHOLD -> Direction.DOWN
            else -> null
        }
        // Diagonals: keep the direction already held if it's still pressed, else prefer vertical.
        val current = hatDirection
        val next = when {
            current != null && (current == horizontal || current == vertical) -> current
            vertical != null -> vertical
            else -> horizontal
        }
        if (next == current) return
        hatDirection = next
        if (next == null) {
            if (digitalSource == MoveSource.HAT) stopDigital()
        } else {
            startDigital(next, MoveSource.HAT, null)
        }
    }

    private fun handleStick(event: MotionEvent) {
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val device = event.device
        val flat = max(
            device.flatFor(MotionEvent.AXIS_X, event.source),
            device.flatFor(MotionEvent.AXIS_Y, event.source),
        )
        applyStickSettings()
        stick.onSample(x, y, now(), flat)?.let { emitMove(it, MoveSource.STICK) }
        scheduleStickTick()
    }

    private fun InputDevice?.flatFor(axis: Int, source: Int): Float =
        this?.getMotionRange(axis, source)?.flat?.let(::abs) ?: 0f

    private fun applyStickSettings() {
        stick.deadZone = settings.stickDeadZone
        stick.repeatDelayMs = settings.repeatDelayMs.toLong()
        stick.repeatIntervalMs = settings.repeatIntervalMs.toLong().coerceAtLeast(MIN_INTERVAL_MS)
    }

    private fun scheduleStickTick() {
        handler.removeCallbacks(stickTick)
        // StickRepeater works in uptime milliseconds, the same clock postAtTime uses.
        stick.nextDeadlineMs()?.let { handler.postAtTime(stickTick, it) }
    }

    /** Emits a digital move immediately and starts our own repeat for it. */
    private fun startDigital(direction: Direction, source: MoveSource, key: Long?) {
        handler.removeCallbacks(digitalRepeat)
        digitalDirection = direction
        digitalSource = source
        digitalKey = key
        emitMove(direction, source)
        handler.postDelayed(digitalRepeat, settings.repeatDelayMs.toLong().coerceAtLeast(MIN_INTERVAL_MS))
    }

    private fun stopDigital() {
        handler.removeCallbacks(digitalRepeat)
        digitalDirection = null
        digitalSource = null
        digitalKey = null
    }

    private fun emitMove(direction: Direction, source: MoveSource) {
        if (deduper.shouldEmit(direction, source, now())) onAction(LauncherAction.Move(direction))
    }

    /** Cancels pending repeats (call on pause / focus loss / controller removal). */
    fun reset() {
        stopDigital()
        handler.removeCallbacks(stickTick)
        stick.reset()
        deduper.reset()
        hatDirection = null
        downKeys.clear()
        rawConsumedKeys.clear()
        deviceIsController.clear()
        lastButtonAction = null
    }

    /**
     * Some gamepads tag D-pad key events only as SOURCE_DPAD | SOURCE_KEYBOARD, so also accept
     * events whose device classifies as a game controller. Plain keyboards and remotes still fail.
     */
    private fun isFromController(source: Int, device: InputDevice?): Boolean {
        if (ControllerClassifier.isControllerEventSource(source)) return true
        if (device == null) return false
        return deviceIsController.getOrPut(device.id) {
            val hasButtons = runCatching {
                device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B).any { it }
            }.getOrDefault(false)
            ControllerClassifier.isGameController(device.sources, device.isVirtual, hasButtons)
        }
    }

    private fun mappingFor(event: KeyEvent): ButtonMapping {
        val device = event.device
        return settings.mappingFor(device?.descriptor, device?.vendorId ?: 0, device?.productId ?: 0)
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    private companion object {
        const val HAT_THRESHOLD = 0.5f
        const val MIN_INTERVAL_MS = 16L
        /** Two different key codes reporting the same action this close together are one press. */
        const val DUPLICATE_BUTTON_WINDOW_MS = 90L

        /** System-reserved keys the launcher must never swallow or remap. */
        val NEVER_CONSUMED = setOf(
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
        )

        fun keyId(deviceId: Int, keyCode: Int): Long = (deviceId.toLong() shl 32) or (keyCode.toLong() and 0xffffffffL)

        fun isGamepadKey(keyCode: Int): Boolean = KeyEvent.isGamepadButton(keyCode)

        /** Cardinal D-pad keys; diagonal key codes resolve to their vertical component. */
        fun dpadDirection(keyCode: Int): Direction? = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP_LEFT, KeyEvent.KEYCODE_DPAD_UP_RIGHT -> Direction.UP
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN_LEFT, KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> Direction.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
            else -> null
        }

        fun ControllerAction.toLauncherAction(): LauncherAction = when (this) {
            ControllerAction.CONFIRM -> LauncherAction.Confirm
            ControllerAction.BACK -> LauncherAction.Back
            ControllerAction.ACTIONS -> LauncherAction.Actions
            ControllerAction.SEARCH -> LauncherAction.Search
            ControllerAction.SETTINGS -> LauncherAction.Settings
            ControllerAction.PREV_CATEGORY -> LauncherAction.PrevCategory
            ControllerAction.NEXT_CATEGORY -> LauncherAction.NextCategory
        }
    }
}
