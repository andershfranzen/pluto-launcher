package dev.pluto.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Pluto's baseline profile (hot code compiled ahead of time at install) and its
 * startup profile (dex layout). Run on emulator-5554 or a rooted device:
 *
 *     ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
 *
 * Output lands in app/src/main/generated/baselineProfiles/ and is committed.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun prepareFixtures() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 1_000L
        val result = device.executeShellCommand("content call --uri content://$PACKAGE.profile-fixtures --method seed")
        check(result.contains("status=ready")) { "Profile fixture setup failed: $result" }
        // Shortcut APIs require Pluto to be the default HOME; this is a disposable emulator.
        val home = device.executeShellCommand("cmd package set-home-activity $PACKAGE/.MainActivity")
        check(!home.contains("Error", ignoreCase = true)) { "Could not set fixture HOME: $home" }
        device.executeShellCommand("am start -W -a android.settings.SETTINGS")
    }

    /** Home launch code, also included in the startup profile for dex layout. */
    @Test
    fun startup() = rule.collect(packageName = PACKAGE, maxIterations = 5, stableIterations = 2, includeInStartupProfile = true,
        filterPredicate = { !it.contains("Ldev/pluto/launcher/profile/") }) {
        startPluto()
        finishOnboarding()
        device.waitForIdle()
        device.executeShellCommand("am start -W -a android.settings.SETTINGS")
    }

    /** Drawer, apps/ROM/shortcut search, pages and Console: the interaction hot paths. */
    @Test
    fun journeys() = rule.collect(packageName = PACKAGE, maxIterations = 5, stableIterations = 2, includeInStartupProfile = false,
        filterPredicate = { !it.contains("Ldev/pluto/launcher/profile/") }) {
        startPluto()
        finishOnboarding()
        drawerJourney()
        richerSearchJourney()
        pagesJourney()
        consoleJourney()
        device.executeShellCommand("am start -W -a android.settings.SETTINGS")
    }
}
