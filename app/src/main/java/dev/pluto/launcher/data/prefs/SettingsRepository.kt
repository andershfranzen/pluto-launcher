package dev.pluto.launcher.data.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.RotationPreference
import dev.pluto.launcher.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    // A damaged file is replaced, never silently: privacy-related flags fail closed and the
    // user is told once (recoveredFromCorruption). The old file's contents cannot be read.
    corruptionHandler = ReplaceFileCorruptionHandler { e ->
        Log.e("SettingsRepository", "settings file corrupt; replacing it with fail-closed defaults", e)
        SettingsRepository.recoveryPreferences()
    },
)

/**
 * DataStore-backed launcher settings. A damaged file is replaced by [recoveryPreferences]
 * (history off, onboarding done, user notified). While the file cannot be read at all
 * (I/O error) the flow emits [UNREADABLE] and keeps retrying with back-off; it never
 * silently falls back to defaults that would re-enable history.
 */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<LauncherSettings> = store.data
        .map(::fromPreferences)
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            Log.e(TAG, "settings unreadable (attempt ${attempt + 1})", cause)
            emit(UNREADABLE)
            delay(minOf(RETRY_BASE_MS shl attempt.coerceAtMost(6).toInt(), RETRY_MAX_MS))
            true
        }

    /** Applies [transform] and persists the result immediately. */
    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.edit { prefs ->
            val next = transform(fromPreferences(prefs))
            writeTo(prefs, next)
        }
    }

    private object Keys {
        // Bump SCHEMA and add a step in migrate() whenever a key's meaning changes.
        val SCHEMA = intPreferencesKey("schema")
        val THEME = stringPreferencesKey("theme")
        val SCRIM = floatPreferencesKey("scrim_alpha")
        val ICON_SCALE = floatPreferencesKey("icon_scale")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        /** Before separate normal/console backgrounds: WALLPAPER, XMB_CONSOLE or XMB_EVERYWHERE. Read only. */
        val LEGACY_BACKGROUND = stringPreferencesKey("background_style")
        val BACKGROUND_NORMAL = stringPreferencesKey("background_normal")
        val BACKGROUND_CONSOLE = stringPreferencesKey("background_console")
        val XMB_COLOR = stringPreferencesKey("xmb_color")
        val BACKGROUND_FPS = stringPreferencesKey("background_frame_rate")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val CAROUSEL = stringPreferencesKey("carousel_style")
        val ROTATION = stringPreferencesKey("rotation")
        val HANDHELD = stringPreferencesKey("handheld_appearance")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val SWIPE_DOWN = stringPreferencesKey("swipe_down_action")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_to_lock")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val DEAD_ZONE = floatPreferencesKey("stick_dead_zone")
        val REPEAT_DELAY = intPreferencesKey("repeat_delay_ms")
        val REPEAT_INTERVAL = intPreferencesKey("repeat_interval_ms")
        val DEFAULT_MAPPING = stringPreferencesKey("default_mapping")
        /** Entries are "<controllerKey>\t<encoded mapping>". */
        val CONTROLLER_MAPPINGS = stringSetPreferencesKey("controller_mappings")
        val LAYOUT_SEEDED = booleanPreferencesKey("layout_seeded")
        val RECOVERED = booleanPreferencesKey("recovered_from_corruption")
    }

    companion object {
        const val SCHEMA_VERSION = 1
        private const val TAG = "SettingsRepository"
        private const val RETRY_BASE_MS = 500L
        private const val RETRY_MAX_MS = 30_000L

        /**
         * Emitted while the settings file cannot be read: fail closed for privacy (history
         * off, so nothing is recorded) and never re-run first-run onboarding or seeding.
         */
        val UNREADABLE = LauncherSettings(
            historyEnabled = false,
            onboardingComplete = true,
            layoutSeeded = true,
            readFailed = true,
        )

        /** Contents written in place of a corrupt settings file. */
        internal fun recoveryPreferences(): Preferences = mutablePreferencesOf(
            Keys.SCHEMA to SCHEMA_VERSION,
            Keys.HISTORY to false,
            Keys.ONBOARDED to true,
            Keys.LAYOUT_SEEDED to true,
            Keys.RECOVERED to true,
        )

        /** The old single background setting, split into its normal or console half. */
        private fun legacyBackground(value: String?, console: Boolean): BackgroundChoice? = when (value) {
            "WALLPAPER" -> BackgroundChoice.WALLPAPER
            "XMB_CONSOLE" -> if (console) BackgroundChoice.XMB else BackgroundChoice.WALLPAPER
            "XMB_EVERYWHERE" -> BackgroundChoice.XMB
            else -> null
        }

        private inline fun <reified E : Enum<E>> enumOr(value: String?, fallback: E): E =
            value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: fallback

        internal fun fromPreferences(p: Preferences): LauncherSettings {
            val d = LauncherSettings()
            return LauncherSettings(
                theme = enumOr(p[Keys.THEME], d.theme),
                scrimAlpha = (p[Keys.SCRIM] ?: d.scrimAlpha).coerceIn(0f, LauncherSettings.SCRIM_MAX),
                iconScale = (p[Keys.ICON_SCALE] ?: d.iconScale).coerceIn(LauncherSettings.ICON_SCALE_RANGE),
                textScale = (p[Keys.TEXT_SCALE] ?: d.textScale).coerceIn(LauncherSettings.TEXT_SCALE_RANGE),
                reducedMotion = p[Keys.REDUCED_MOTION] ?: d.reducedMotion,
                normalBackground = enumOr(p[Keys.BACKGROUND_NORMAL], legacyBackground(p[Keys.LEGACY_BACKGROUND], console = false) ?: d.normalBackground),
                consoleBackground = enumOr(p[Keys.BACKGROUND_CONSOLE], legacyBackground(p[Keys.LEGACY_BACKGROUND], console = true) ?: d.consoleBackground),
                xmbColor = enumOr<XmbColor>(p[Keys.XMB_COLOR], d.xmbColor),
                backgroundFrameRate = enumOr<BackgroundFrameRate>(p[Keys.BACKGROUND_FPS], d.backgroundFrameRate),
                iconPack = p[Keys.ICON_PACK]?.takeIf { it.isNotBlank() },
                carouselStyle = enumOr<CarouselStyle>(p[Keys.CAROUSEL], d.carouselStyle),
                rotation = enumOr<RotationPreference>(p[Keys.ROTATION], d.rotation),
                handheldAppearance = enumOr<HandheldAppearance>(p[Keys.HANDHELD], d.handheldAppearance),
                historyEnabled = p[Keys.HISTORY] ?: d.historyEnabled,
                swipeDownAction = enumOr<SwipeDownAction>(p[Keys.SWIPE_DOWN], d.swipeDownAction),
                doubleTapToLock = p[Keys.DOUBLE_TAP_LOCK] ?: d.doubleTapToLock,
                onboardingComplete = p[Keys.ONBOARDED] ?: d.onboardingComplete,
                stickDeadZone = (p[Keys.DEAD_ZONE] ?: d.stickDeadZone).coerceIn(LauncherSettings.DEAD_ZONE_RANGE),
                repeatDelayMs = (p[Keys.REPEAT_DELAY] ?: d.repeatDelayMs).coerceIn(150, 1000),
                repeatIntervalMs = (p[Keys.REPEAT_INTERVAL] ?: d.repeatIntervalMs).coerceIn(40, 500),
                defaultMapping = ButtonMapping.decode(p[Keys.DEFAULT_MAPPING]),
                controllerMappings = p[Keys.CONTROLLER_MAPPINGS].orEmpty().mapNotNull { entry ->
                    val tab = entry.indexOf('\t')
                    if (tab <= 0) null else entry.substring(0, tab) to ButtonMapping.decode(entry.substring(tab + 1))
                }.toMap(),
                layoutSeeded = p[Keys.LAYOUT_SEEDED] ?: d.layoutSeeded,
                recoveredFromCorruption = p[Keys.RECOVERED] ?: d.recoveredFromCorruption,
            )
        }

        internal fun writeTo(p: androidx.datastore.preferences.core.MutablePreferences, s: LauncherSettings) {
            p[Keys.SCHEMA] = SCHEMA_VERSION
            p[Keys.THEME] = s.theme.name
            p[Keys.SCRIM] = s.scrimAlpha.coerceIn(0f, LauncherSettings.SCRIM_MAX)
            p[Keys.ICON_SCALE] = s.iconScale.coerceIn(LauncherSettings.ICON_SCALE_RANGE)
            p[Keys.TEXT_SCALE] = s.textScale.coerceIn(LauncherSettings.TEXT_SCALE_RANGE)
            p[Keys.REDUCED_MOTION] = s.reducedMotion
            p[Keys.BACKGROUND_NORMAL] = s.normalBackground.name
            p[Keys.BACKGROUND_CONSOLE] = s.consoleBackground.name
            p[Keys.XMB_COLOR] = s.xmbColor.name
            p[Keys.BACKGROUND_FPS] = s.backgroundFrameRate.name
            if (s.iconPack != null) p[Keys.ICON_PACK] = s.iconPack else p.remove(Keys.ICON_PACK)
            p[Keys.CAROUSEL] = s.carouselStyle.name
            p[Keys.ROTATION] = s.rotation.name
            p[Keys.HANDHELD] = s.handheldAppearance.name
            p[Keys.HISTORY] = s.historyEnabled
            p[Keys.SWIPE_DOWN] = s.swipeDownAction.name
            p[Keys.DOUBLE_TAP_LOCK] = s.doubleTapToLock
            p[Keys.ONBOARDED] = s.onboardingComplete
            p[Keys.DEAD_ZONE] = s.stickDeadZone
            p[Keys.REPEAT_DELAY] = s.repeatDelayMs
            p[Keys.REPEAT_INTERVAL] = s.repeatIntervalMs
            p[Keys.DEFAULT_MAPPING] = s.defaultMapping.encode()
            p[Keys.CONTROLLER_MAPPINGS] = s.controllerMappings.map { (k, v) -> "$k\t${v.encode()}" }.toSet()
            p[Keys.LAYOUT_SEEDED] = s.layoutSeeded
            p[Keys.RECOVERED] = s.recoveredFromCorruption
            // readFailed is transient and never persisted.
        }
    }
}
