package dev.pluto.launcher.input

import android.view.KeyEvent
import android.view.MotionEvent
import dev.pluto.launcher.data.prefs.LauncherSettings

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
 */
class ControllerInputRouter(private val onAction: (LauncherAction) -> Unit) {
    var settings: LauncherSettings = LauncherSettings()
    var onRawKey: ((KeyEvent) -> Boolean)? = null
    /** Diagnostics: last raw axis values seen (x, y, hatX, hatY) for the diagnostics screen. */
    var onRawMotion: ((MotionEvent) -> Unit)? = null

    /** Returns true if consumed. */
    fun onKeyEvent(event: KeyEvent): Boolean = TODO()

    /** Returns true if consumed. */
    fun onGenericMotionEvent(event: MotionEvent): Boolean = TODO()

    /** Cancels pending repeats (call on pause / focus loss / controller removal). */
    fun reset(): Unit = TODO()
}
