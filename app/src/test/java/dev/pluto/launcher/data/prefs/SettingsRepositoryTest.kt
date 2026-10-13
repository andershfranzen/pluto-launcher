package dev.pluto.launcher.data.prefs

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        assertFalse(recovered.automaticUpdateChecks)
        assertTrue(recovered.updateCheckChoiceMade)
        assertTrue(recovered.onboardingComplete)
        assertTrue(recovered.layoutSeeded)
        assertTrue(recovered.recoveredFromCorruption)
        assertFalse(recovered.readFailed)
    }

    @Test
    fun unreadableSettingsFailClosed() {
        val unreadable = SettingsRepository.UNREADABLE
        assertFalse(unreadable.historyEnabled)
        assertFalse(unreadable.automaticUpdateChecks)
        assertTrue(unreadable.readFailed)
        assertTrue(unreadable.onboardingComplete)
        assertTrue(unreadable.layoutSeeded)
    }

    @Test
    fun freshInstallIsNotSeededAndHasNoNotice() {
        val fresh = SettingsRepository.fromPreferences(emptyPreferences())
        assertFalse(fresh.notificationDots)
        assertTrue(fresh.drawerAlphabetRail)
        assertTrue(fresh.automaticUpdateChecks)
        assertFalse(fresh.updateCheckChoiceMade)
        assertFalse(fresh.layoutSeeded)
        assertFalse(fresh.recoveredFromCorruption)
        assertFalse(fresh.readFailed)
    }

    @Test
    fun newFlagsRoundTripAndReadFailedIsNeverPersisted() {
        val prefs = mutablePreferencesOf()
        val settings = LauncherSettings(
            theme = ThemePreference.DARK,
            notificationDots = true,
            historyEnabled = false,
            consoleFavourites = false,
            drawerAlphabetRail = false,
            romFavourites = setOf("content://rom/Spyro.cue"),
            romSort = dev.pluto.launcher.domain.RomSort.RECENT,
            romLastPlayed = mapOf("content://rom/Spyro.cue" to 1_700_000_000_000L),
            romEmulators = mapOf("content://rom/Zelda.nsp" to "org.dolphinemu.dolphinemu"),
            automaticUpdateChecks = false,
            updateCheckChoiceMade = true,
            layoutSeeded = true,
            recoveredFromCorruption = true,
            readFailed = true,
        )
        SettingsRepository.writeTo(prefs, settings)
        assertEquals(settings.copy(readFailed = false), SettingsRepository.fromPreferences(prefs))
    }

    @Test
    fun damagedPerGameEntriesAreDroppedAndTheRestStillRead() {
        val prefs = mutablePreferencesOf(
            stringSetPreferencesKey("rom_last_played") to
                setOf("content://rom/a.sfc\t111", "no-separator", "content://rom/c.sfc\tsoon"),
            stringSetPreferencesKey("rom_emulators") to setOf("no-separator"),
        )
        val read = SettingsRepository.fromPreferences(prefs)
        assertEquals(mapOf("content://rom/a.sfc" to 111L), read.romLastPlayed)
        assertEquals(emptyMap<String, String>(), read.romEmulators)
    }
}
