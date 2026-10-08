package dev.pluto.launcher.data.prefs

import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.RotationPreference
import dev.pluto.launcher.model.ThemePreference

/** Launcher actions a controller button can trigger. Movement (D-pad / stick) is not remappable. */
enum class ControllerAction { CONFIRM, BACK, ACTIONS, SEARCH, SETTINGS, PREV_CATEGORY, NEXT_CATEGORY }

/**
 * Maps Android key codes to launcher actions. A key code maps to at most one action.
 * Face-button lettering differs between vendors, so prompts should use the bound key's
 * Android name (e.g. "A" for KEYCODE_BUTTON_A) rather than assume physical labels.
 */
data class ButtonMapping(val bindings: Map<ControllerAction, Set<Int>>) {
    /**
     * The action bound to [keyCode]. System-reserved keys (Home, Recents, the controller's
     * Guide/Home button) never resolve, even if an older mapping persisted them.
     */
    fun actionFor(keyCode: Int): ControllerAction? =
        if (keyCode in SYSTEM_RESERVED_KEYS) null else bindings.entries.firstOrNull { keyCode in it.value }?.key

    fun keysFor(action: ControllerAction): Set<Int> = bindings[action].orEmpty()

    /** Binds [keyCode] to [action] only, removing it from any other action. */
    fun rebind(action: ControllerAction, keyCode: Int): ButtonMapping =
        ButtonMapping(
            ControllerAction.entries.associateWith { a ->
                val keys = bindings[a].orEmpty() - keyCode
                if (a == action) keys + keyCode else keys
            },
        )

    /** Replaces all keys for [action] with [keyCode]. */
    fun replace(action: ControllerAction, keyCode: Int): ButtonMapping =
        ButtonMapping(
            ControllerAction.entries.associateWith { a ->
                if (a == action) setOf(keyCode) else bindings[a].orEmpty() - keyCode
            },
        )

    fun encode(): String = ControllerAction.entries.joinToString(";") { a ->
        a.name + "=" + keysFor(a).sorted().joinToString(",")
    }

    companion object {
        // Android KeyEvent constants, inlined so this file stays JVM-testable.
        const val KEYCODE_BACK = 4
        const val KEYCODE_MENU = 82
        const val KEYCODE_BUTTON_A = 96
        const val KEYCODE_BUTTON_B = 97
        const val KEYCODE_BUTTON_X = 99
        const val KEYCODE_BUTTON_Y = 100
        const val KEYCODE_BUTTON_L1 = 102
        const val KEYCODE_BUTTON_R1 = 103
        const val KEYCODE_BUTTON_START = 108
        const val KEYCODE_BUTTON_SELECT = 109
        const val KEYCODE_HOME = 3
        const val KEYCODE_APP_SWITCH = 187
        /** The controller's Guide / Home button; Android's fallback for it is HOME. */
        const val KEYCODE_BUTTON_MODE = 110

        /** Keys the system owns. They are never consumed, captured or bound to a launcher action. */
        val SYSTEM_RESERVED_KEYS: Set<Int> = setOf(KEYCODE_HOME, KEYCODE_APP_SWITCH, KEYCODE_BUTTON_MODE)

        val DEFAULT = ButtonMapping(
            mapOf(
                ControllerAction.CONFIRM to setOf(KEYCODE_BUTTON_A),
                ControllerAction.BACK to setOf(KEYCODE_BUTTON_B, KEYCODE_BACK),
                ControllerAction.ACTIONS to setOf(KEYCODE_BUTTON_X),
                ControllerAction.SEARCH to setOf(KEYCODE_BUTTON_Y),
                ControllerAction.SETTINGS to setOf(KEYCODE_BUTTON_START, KEYCODE_MENU),
                ControllerAction.PREV_CATEGORY to setOf(KEYCODE_BUTTON_L1),
                ControllerAction.NEXT_CATEGORY to setOf(KEYCODE_BUTTON_R1),
            ),
        )

        /** Lenient decode; unknown actions/keys are skipped, missing actions fall back to defaults. */
        fun decode(value: String?): ButtonMapping {
            if (value.isNullOrBlank()) return DEFAULT
            val parsed = HashMap<ControllerAction, Set<Int>>()
            for (part in value.split(';')) {
                val name = part.substringBefore('=', "")
                val action = ControllerAction.entries.firstOrNull { it.name == name } ?: continue
                parsed[action] = part.substringAfter('=', "").split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filterNot { it in SYSTEM_RESERVED_KEYS }
                    .toSet()
            }
            return ButtonMapping(ControllerAction.entries.associateWith { parsed[it] ?: DEFAULT.keysFor(it) })
        }
    }
}

/**
 * Simple preferences, persisted in DataStore. Organisation lives in Room.
 * Defaults are readable with no setup.
 */
data class LauncherSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /** Opacity of the scrim drawn over the wallpaper, 0..0.85. */
    val scrimAlpha: Float = 0.35f,
    /** Icon size multiplier, 0.8..1.4. */
    val iconScale: Float = 1f,
    /** Launcher text multiplier on top of the system font scale, 0.85..1.5. */
    val textScale: Float = 1f,
    val reducedMotion: Boolean = false,
    val rotation: RotationPreference = RotationPreference.FOLLOW_SYSTEM,
    val handheldAppearance: HandheldAppearance = HandheldAppearance.AUTOMATIC,
    val historyEnabled: Boolean = true,
    val onboardingComplete: Boolean = false,
    val stickDeadZone: Float = 0.25f,
    val repeatDelayMs: Int = 350,
    val repeatIntervalMs: Int = 100,
    /** Mapping used when no per-controller override exists. */
    val defaultMapping: ButtonMapping = ButtonMapping.DEFAULT,
    /**
     * Per-controller overrides. Key is a stable controller key from
     * [controllerMappingKey]: the InputDevice descriptor, or "vp:<vendor>:<product>" fallback.
     */
    val controllerMappings: Map<String, ButtonMapping> = emptyMap(),
) {
    /** Resolves the mapping for a controller: descriptor, then vendor/product, then default. */
    fun mappingFor(descriptor: String?, vendorId: Int, productId: Int): ButtonMapping =
        descriptor?.takeIf { it.isNotBlank() }?.let { controllerMappings[it] }
            ?: controllerMappings[vendorProductKey(vendorId, productId)]
            ?: defaultMapping

    companion object {
        const val SCRIM_MAX = 0.85f
        val ICON_SCALE_RANGE = 0.8f..1.4f
        val TEXT_SCALE_RANGE = 0.85f..1.5f
        val DEAD_ZONE_RANGE = 0.05f..0.6f

        fun vendorProductKey(vendorId: Int, productId: Int) = "vp:$vendorId:$productId"

        /** Key to store a per-controller mapping under. Prefers the descriptor when present. */
        fun controllerMappingKey(descriptor: String?, vendorId: Int, productId: Int): String =
            descriptor?.takeIf { it.isNotBlank() } ?: vendorProductKey(vendorId, productId)
    }
}
