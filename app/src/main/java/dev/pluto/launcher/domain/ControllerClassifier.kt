package dev.pluto.launcher.domain

object ControllerClassifier {
    // android.view.InputDevice source constants, inlined for JVM tests.
    const val SOURCE_KEYBOARD = 0x00000101
    const val SOURCE_DPAD = 0x00000201
    const val SOURCE_GAMEPAD = 0x00000401
    const val SOURCE_JOYSTICK = 0x01000010

    /**
     * True for gamepads/joysticks; false for ordinary keyboards (including ones that
     * report SOURCE_DPAD, like TV remotes or keyboards with arrow keys) and virtual devices.
     * [hasGamepadButtons] = device reports KEYCODE_BUTTON_A or BUTTON_B (InputDevice.hasKeys).
     * A device qualifies with SOURCE_GAMEPAD, or SOURCE_JOYSTICK together with gamepad buttons.
     */
    fun isGameController(sources: Int, isVirtual: Boolean, hasGamepadButtons: Boolean): Boolean = TODO()

    /** True when an event's source bits indicate it came from a gamepad/joystick rather than a keyboard. */
    fun isControllerEventSource(eventSource: Int): Boolean = TODO()
}
