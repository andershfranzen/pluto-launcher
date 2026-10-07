package dev.pluto.launcher.domain

import dev.pluto.launcher.domain.ControllerClassifier.SOURCE_DPAD
import dev.pluto.launcher.domain.ControllerClassifier.SOURCE_GAMEPAD
import dev.pluto.launcher.domain.ControllerClassifier.SOURCE_JOYSTICK
import dev.pluto.launcher.domain.ControllerClassifier.SOURCE_KEYBOARD
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerClassifierTest {
    // Other android.view.InputDevice sources, for realistic combinations.
    private val sourceTouchscreen = 0x00001002
    private val sourceMouse = 0x00002002

    private fun classify(sources: Int, virtual: Boolean = false, buttons: Boolean = false) =
        ControllerClassifier.isGameController(sources, virtual, buttons)

    @Test
    fun typicalGamepadQualifies() {
        // GameSir-style: keyboard + dpad + gamepad + joystick.
        val sources = SOURCE_KEYBOARD or SOURCE_DPAD or SOURCE_GAMEPAD or SOURCE_JOYSTICK
        assertTrue(classify(sources, buttons = true))
        assertTrue(classify(SOURCE_GAMEPAD))
    }

    @Test
    fun joystickNeedsGamepadButtons() {
        assertTrue(classify(SOURCE_JOYSTICK, buttons = true))
        assertFalse(classify(SOURCE_JOYSTICK, buttons = false))
    }

    @Test
    fun plainKeyboardNeverQualifies() {
        assertFalse(classify(SOURCE_KEYBOARD))
        assertFalse(classify(SOURCE_KEYBOARD, buttons = true))
    }

    @Test
    fun keyboardWithDpadNeverQualifies() {
        // Keyboards with arrow keys and TV remotes report SOURCE_DPAD; it shares the button class bit with gamepads.
        assertFalse(classify(SOURCE_KEYBOARD or SOURCE_DPAD))
        assertFalse(classify(SOURCE_DPAD, buttons = true))
        assertFalse(classify(SOURCE_KEYBOARD or SOURCE_DPAD or sourceMouse))
    }

    @Test
    fun virtualDevicesNeverQualify() {
        assertFalse(classify(SOURCE_GAMEPAD, virtual = true, buttons = true))
        assertFalse(classify(SOURCE_JOYSTICK or SOURCE_GAMEPAD, virtual = true, buttons = true))
    }

    @Test
    fun touchscreenDoesNotQualify() {
        assertFalse(classify(sourceTouchscreen))
    }

    @Test
    fun eventSourceRequiresFullMask() {
        assertTrue(ControllerClassifier.isControllerEventSource(SOURCE_GAMEPAD))
        assertTrue(ControllerClassifier.isControllerEventSource(SOURCE_KEYBOARD or SOURCE_GAMEPAD))
        assertTrue(ControllerClassifier.isControllerEventSource(SOURCE_JOYSTICK))
        assertTrue(ControllerClassifier.isControllerEventSource(SOURCE_DPAD or SOURCE_GAMEPAD))

        assertFalse(ControllerClassifier.isControllerEventSource(SOURCE_KEYBOARD))
        assertFalse(ControllerClassifier.isControllerEventSource(SOURCE_DPAD))
        assertFalse(ControllerClassifier.isControllerEventSource(SOURCE_KEYBOARD or SOURCE_DPAD))
        assertFalse(ControllerClassifier.isControllerEventSource(sourceTouchscreen))
        // Joystick class bit alone (e.g. another class-joystick source) isn't a joystick.
        assertFalse(ControllerClassifier.isControllerEventSource(0x00000010))
        assertFalse(ControllerClassifier.isControllerEventSource(0))
    }
}
