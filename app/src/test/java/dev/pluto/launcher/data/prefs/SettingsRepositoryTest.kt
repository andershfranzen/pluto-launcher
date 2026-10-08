package dev.pluto.launcher.data.prefs

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import dev.pluto.launcher.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun corruptFileIsReplacedWithFailClosedValuesAndANotice() {
        val recovered = SettingsRepository.fromPreferences(SettingsRepository.recoveryPreferences())
        assertFalse("history must stay off after a reset", recovered.historyEnabled)
        assertTrue(recovered.onboardingComplete)
        assertTrue(recovered.layoutSeeded)
        assertTrue(recovered.recoveredFromCorruption)
        assertFalse(recovered.readFailed)
    }

    @Test
    fun unreadableSettingsFailClosed() {
        val unreadable = SettingsRepository.UNREADABLE
        assertFalse(unreadable.historyEnabled)
        assertTrue(unreadable.readFailed)
        assertTrue(unreadable.onboardingComplete)
        assertTrue(unreadable.layoutSeeded)
    }

    @Test
    fun freshInstallIsNotSeededAndHasNoNotice() {
        val fresh = SettingsRepository.fromPreferences(emptyPreferences())
        assertFalse(fresh.layoutSeeded)
        assertFalse(fresh.recoveredFromCorruption)
        assertFalse(fresh.readFailed)
    }

    @Test
    fun newFlagsRoundTripAndReadFailedIsNeverPersisted() {
        val prefs = mutablePreferencesOf()
        val settings = LauncherSettings(
            theme = ThemePreference.DARK,
            historyEnabled = false,
            layoutSeeded = true,
            recoveredFromCorruption = true,
            readFailed = true,
        )
        SettingsRepository.writeTo(prefs, settings)
        assertEquals(settings.copy(readFailed = false), SettingsRepository.fromPreferences(prefs))
    }
}
