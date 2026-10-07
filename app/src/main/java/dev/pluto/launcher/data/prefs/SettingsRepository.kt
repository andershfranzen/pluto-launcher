package dev.pluto.launcher.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.RotationPreference
import dev.pluto.launcher.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<LauncherSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::fromPreferences)

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
        val ROTATION = stringPreferencesKey("rotation")
        val HANDHELD = stringPreferencesKey("handheld_appearance")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val DEAD_ZONE = floatPreferencesKey("stick_dead_zone")
        val REPEAT_DELAY = intPreferencesKey("repeat_delay_ms")
        val REPEAT_INTERVAL = intPreferencesKey("repeat_interval_ms")
        val DEFAULT_MAPPING = stringPreferencesKey("default_mapping")
        /** Entries are "<controllerKey>\t<encoded mapping>". */
        val CONTROLLER_MAPPINGS = stringSetPreferencesKey("controller_mappings")
    }

    companion object {
        const val SCHEMA_VERSION = 1

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
                rotation = enumOr<RotationPreference>(p[Keys.ROTATION], d.rotation),
                handheldAppearance = enumOr<HandheldAppearance>(p[Keys.HANDHELD], d.handheldAppearance),
                historyEnabled = p[Keys.HISTORY] ?: d.historyEnabled,
                onboardingComplete = p[Keys.ONBOARDED] ?: d.onboardingComplete,
                stickDeadZone = (p[Keys.DEAD_ZONE] ?: d.stickDeadZone).coerceIn(LauncherSettings.DEAD_ZONE_RANGE),
                repeatDelayMs = (p[Keys.REPEAT_DELAY] ?: d.repeatDelayMs).coerceIn(150, 1000),
                repeatIntervalMs = (p[Keys.REPEAT_INTERVAL] ?: d.repeatIntervalMs).coerceIn(40, 500),
                defaultMapping = ButtonMapping.decode(p[Keys.DEFAULT_MAPPING]),
                controllerMappings = p[Keys.CONTROLLER_MAPPINGS].orEmpty().mapNotNull { entry ->
                    val tab = entry.indexOf('\t')
                    if (tab <= 0) null else entry.substring(0, tab) to ButtonMapping.decode(entry.substring(tab + 1))
                }.toMap(),
            )
        }

        private fun writeTo(p: androidx.datastore.preferences.core.MutablePreferences, s: LauncherSettings) {
            p[Keys.SCHEMA] = SCHEMA_VERSION
            p[Keys.THEME] = s.theme.name
            p[Keys.SCRIM] = s.scrimAlpha.coerceIn(0f, LauncherSettings.SCRIM_MAX)
            p[Keys.ICON_SCALE] = s.iconScale.coerceIn(LauncherSettings.ICON_SCALE_RANGE)
            p[Keys.TEXT_SCALE] = s.textScale.coerceIn(LauncherSettings.TEXT_SCALE_RANGE)
            p[Keys.REDUCED_MOTION] = s.reducedMotion
            p[Keys.ROTATION] = s.rotation.name
            p[Keys.HANDHELD] = s.handheldAppearance.name
            p[Keys.HISTORY] = s.historyEnabled
            p[Keys.ONBOARDED] = s.onboardingComplete
            p[Keys.DEAD_ZONE] = s.stickDeadZone
            p[Keys.REPEAT_DELAY] = s.repeatDelayMs
            p[Keys.REPEAT_INTERVAL] = s.repeatIntervalMs
            p[Keys.DEFAULT_MAPPING] = s.defaultMapping.encode()
            p[Keys.CONTROLLER_MAPPINGS] = s.controllerMappings.map { (k, v) -> "$k\t${v.encode()}" }.toSet()
        }
    }
}
