package dev.pluto.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start to an interactive home: also the startup profile. */
    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        startPluto()
        finishOnboarding()
        device.waitForIdle()
    }

    /** Drawer, search, pages, landscape and Handheld: the interaction hot paths. */
    @Test
    fun journeys() = rule.collect(packageName = PACKAGE, includeInStartupProfile = false) {
        startPluto()
        finishOnboarding()
        drawerJourney()
        pagesJourney()
        handheldJourney()
    }
}
