package dev.pluto.launcher.data.prefs

import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.RotationPreference
import dev.pluto.launcher.model.ThemePreference

/** What is drawn behind the launcher; normal and console (handheld) mode each have their own. */
enum class BackgroundChoice {
    /** The system wallpaper (with the contrast scrim). */
    WALLPAPER,
    /** Pluto from New Horizons' colour map, turning slowly among stars and drifting particles. */
    PLUTO,
    /** PS3 XMB-style animated waves in an [XmbColor]. */
    XMB,
}

/** How often the Pluto background's stars and dust animate: smoother costs more battery. */
enum class BackgroundFrameRate(val effectsFps: Int) {
    /** Stars and dust at 60 fps. */
    SMOOTH(60),
    /** Stars and dust at 30 fps. */
    BATTERY_SAVER(30),
}

/** How the console's carousel lays out its cards (in the spirit of Aurora's coverflow styles). */
enum class CarouselStyle {
    /** Side cards tilt towards the centre like the walls of a corridor. */
    COVERFLOW,
    /** A flat, evenly spaced row; neighbours a little smaller. */
    SHOWCASE,
    /** Cards follow a gentle arc, tipping as they go. */
    ARC,
    /** Cards stand on a turning ring, facing outwards. */
    RING,
    /** What comes next waits in a stack behind the selection; what is passed slides away. */
    DECK,
}

/** Base colour of the XMB background. AUTO changes with the month, like the PS3 did. */
enum class XmbColor(val argb: Long) {
    AUTO(0),
    BLUE(0xFF1D58B5),
    PURPLE(0xFF6B2FA3),
    PINK(0xFFC24680),
    RED(0xFFAE2230),
    ORANGE(0xFFCE6519),
    GOLD(0xFFB8922A),
    GREEN(0xFF3D8C32),
    TEAL(0xFF1C8A8C),
    SILVER(0xFF878D98),
    BLACK(0xFF17191F),
    ;

    companion object {
        /** Month (1-12) to colour for AUTO: an approximation of the PS3's monthly themes. */
        fun forMonth(month: Int): XmbColor = when (month) {
            1 -> SILVER
            2 -> GOLD
            3 -> GREEN
            4 -> PINK
            5 -> TEAL
            6 -> PURPLE
            7 -> BLUE
            8 -> BLUE
            9 -> PURPLE
            10 -> ORANGE
            11 -> GOLD
            else -> RED
        }
    }
}

/** What a swipe down over Home does. */
enum class SwipeDownAction {
    /** Pull down the notification shade, as stock launchers do. */
    NOTIFICATIONS,
    /** Open the app drawer with the keyboard up. */
    SEARCH,
    NOTHING,
}

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
    val normalBackground: BackgroundChoice = BackgroundChoice.PLUTO,
    val consoleBackground: BackgroundChoice = BackgroundChoice.XMB,
    val xmbColor: XmbColor = XmbColor.AUTO,
    val backgroundFrameRate: BackgroundFrameRate = BackgroundFrameRate.SMOOTH,
    val carouselStyle: CarouselStyle = CarouselStyle.COVERFLOW,
    /** Package of the chosen icon pack; null for the apps' own icons. */
    val iconPack: String? = null,
    val rotation: RotationPreference = RotationPreference.FOLLOW_SYSTEM,
    val handheldAppearance: HandheldAppearance = HandheldAppearance.AUTOMATIC,
    val historyEnabled: Boolean = true,
    val swipeDownAction: SwipeDownAction = SwipeDownAction.NOTIFICATIONS,
    /** Double-tap empty space on Home to lock the screen (needs Pluto's accessibility service). */
    val doubleTapToLock: Boolean = false,
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
    /** Set once the first-run default layout was seeded (or found unnecessary); never seeded again. */
    val layoutSeeded: Boolean = false,
    /**
     * Set when a damaged settings file was replaced. The launcher tells the user once and
     * clears it; it is persisted so the notice survives a crash before it was shown.
     */
    val recoveredFromCorruption: Boolean = false,
    /**
     * Not persisted: true when the settings could not be read and these are fail-closed
     * fallback values ([SettingsRepository.UNREADABLE]). History is never recorded then.
     */
    val readFailed: Boolean = false,
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
