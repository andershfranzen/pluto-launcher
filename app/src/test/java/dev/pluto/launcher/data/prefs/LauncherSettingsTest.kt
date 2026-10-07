package dev.pluto.launcher.data.prefs

import dev.pluto.launcher.data.prefs.ButtonMapping.Companion.KEYCODE_BACK
import dev.pluto.launcher.data.prefs.ButtonMapping.Companion.KEYCODE_BUTTON_A
import dev.pluto.launcher.data.prefs.ButtonMapping.Companion.KEYCODE_BUTTON_B
import dev.pluto.launcher.data.prefs.ButtonMapping.Companion.KEYCODE_BUTTON_X
import dev.pluto.launcher.data.prefs.ButtonMapping.Companion.KEYCODE_BUTTON_Y
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonMappingTest {
    private val default = ButtonMapping.DEFAULT

    @Test
    fun defaultMappingFollowsSpec() {
        assertEquals(ControllerAction.CONFIRM, default.actionFor(KEYCODE_BUTTON_A))
        assertEquals(ControllerAction.BACK, default.actionFor(KEYCODE_BUTTON_B))
        assertEquals(ControllerAction.BACK, default.actionFor(KEYCODE_BACK))
        assertEquals(ControllerAction.ACTIONS, default.actionFor(KEYCODE_BUTTON_X))
        assertEquals(ControllerAction.SEARCH, default.actionFor(KEYCODE_BUTTON_Y))
        assertEquals(ControllerAction.SETTINGS, default.actionFor(ButtonMapping.KEYCODE_BUTTON_START))
        assertEquals(ControllerAction.SETTINGS, default.actionFor(ButtonMapping.KEYCODE_MENU))
        assertEquals(ControllerAction.PREV_CATEGORY, default.actionFor(ButtonMapping.KEYCODE_BUTTON_L1))
        assertEquals(ControllerAction.NEXT_CATEGORY, default.actionFor(ButtonMapping.KEYCODE_BUTTON_R1))
        assertNull(default.actionFor(ButtonMapping.KEYCODE_BUTTON_SELECT))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals(default, ButtonMapping.decode(default.encode()))
        val custom = default.rebind(ControllerAction.CONFIRM, KEYCODE_BUTTON_B)
        assertEquals(custom, ButtonMapping.decode(custom.encode()))
    }

    @Test
    fun encodeIsSortedAndComplete() {
        val encoded = default.encode()
        assertEquals(ControllerAction.entries.size, encoded.split(';').size)
        assertEquals(true, encoded.contains("BACK=4,97"))
    }

    @Test
    fun decodeBlankGivesDefault() {
        assertEquals(default, ButtonMapping.decode(null))
        assertEquals(default, ButtonMapping.decode(""))
        assertEquals(default, ButtonMapping.decode("   "))
    }

    @Test
    fun decodeIsLenient() {
        val decoded = ButtonMapping.decode("CONFIRM=99, x ,;BOGUS=1;SEARCH=")
        assertEquals(setOf(KEYCODE_BUTTON_X), decoded.keysFor(ControllerAction.CONFIRM))
        // Explicitly empty stays empty; missing actions fall back to defaults.
        assertEquals(emptySet<Int>(), decoded.keysFor(ControllerAction.SEARCH))
        assertEquals(default.keysFor(ControllerAction.BACK), decoded.keysFor(ControllerAction.BACK))
    }

    @Test
    fun rebindMovesKeyToOneActionAndKeepsOthers() {
        val mapping = default.rebind(ControllerAction.CONFIRM, KEYCODE_BUTTON_B)
        assertEquals(setOf(KEYCODE_BUTTON_A, KEYCODE_BUTTON_B), mapping.keysFor(ControllerAction.CONFIRM))
        assertEquals(setOf(KEYCODE_BACK), mapping.keysFor(ControllerAction.BACK))
        assertEquals(ControllerAction.CONFIRM, mapping.actionFor(KEYCODE_BUTTON_B))
    }

    @Test
    fun replaceSetsSingleKeyAndRemovesItElsewhere() {
        val mapping = default.replace(ControllerAction.CONFIRM, KEYCODE_BUTTON_B)
        assertEquals(setOf(KEYCODE_BUTTON_B), mapping.keysFor(ControllerAction.CONFIRM))
        assertEquals(setOf(KEYCODE_BACK), mapping.keysFor(ControllerAction.BACK))
        assertNull(mapping.actionFor(KEYCODE_BUTTON_A))
    }

    @Test
    fun keyMapsToAtMostOneAction() {
        var mapping = default
        for (action in ControllerAction.entries) mapping = mapping.rebind(action, KEYCODE_BUTTON_Y)
        val owners = ControllerAction.entries.filter { KEYCODE_BUTTON_Y in mapping.keysFor(it) }
        assertEquals(listOf(ControllerAction.entries.last()), owners)
    }
}

class LauncherSettingsTest {
    private val descriptorMapping = ButtonMapping.DEFAULT.replace(ControllerAction.CONFIRM, 1)
    private val vendorMapping = ButtonMapping.DEFAULT.replace(ControllerAction.CONFIRM, 2)
    private val customDefault = ButtonMapping.DEFAULT.replace(ControllerAction.CONFIRM, 3)

    private val settings = LauncherSettings(
        defaultMapping = customDefault,
        controllerMappings = mapOf(
            "desc-abc" to descriptorMapping,
            LauncherSettings.vendorProductKey(0x3537, 0x1004) to vendorMapping,
        ),
    )

    @Test
    fun descriptorWinsOverVendorProduct() {
        assertEquals(descriptorMapping, settings.mappingFor("desc-abc", 0x3537, 0x1004))
    }

    @Test
    fun fallsBackToVendorProductWhenDescriptorUnknownOrBlank() {
        assertEquals(vendorMapping, settings.mappingFor("desc-new", 0x3537, 0x1004))
        assertEquals(vendorMapping, settings.mappingFor("", 0x3537, 0x1004))
        assertEquals(vendorMapping, settings.mappingFor(null, 0x3537, 0x1004))
    }

    @Test
    fun fallsBackToDefaultMapping() {
        assertEquals(customDefault, settings.mappingFor("other", 1, 2))
        assertEquals(customDefault, settings.mappingFor(null, 0, 0))
        assertEquals(ButtonMapping.DEFAULT, LauncherSettings().mappingFor("x", 1, 2))
    }

    @Test
    fun controllerMappingKeyPrefersDescriptor() {
        assertEquals("desc", LauncherSettings.controllerMappingKey("desc", 1, 2))
        assertEquals("vp:1:2", LauncherSettings.controllerMappingKey(" ", 1, 2))
        assertEquals("vp:1:2", LauncherSettings.controllerMappingKey(null, 1, 2))
    }

    @Test
    fun defaultsMatchSpec() {
        val defaults = LauncherSettings()
        assertEquals(0.25f, defaults.stickDeadZone)
        assertEquals(350, defaults.repeatDelayMs)
        assertEquals(100, defaults.repeatIntervalMs)
        assertEquals(true, defaults.historyEnabled)
    }
}
