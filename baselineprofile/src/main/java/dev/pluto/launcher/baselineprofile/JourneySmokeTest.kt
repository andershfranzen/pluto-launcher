package dev.pluto.launcher.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Validate the strict journey once before spending time collecting stable profiles. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class JourneySmokeTest {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun journeys() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 1,
        setupBlock = {
            androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 1_000L
            configureFixtures("seed")
            val home = device.executeShellCommand("cmd package set-home-activity $PACKAGE/.MainActivity")
            check(!home.contains("Error", ignoreCase = true)) { "Could not set fixture HOME: $home" }
            device.executeShellCommand("am start -W -a android.settings.SETTINGS")
        },
    ) {
        startPluto()
        drawerJourney()
        richerSearchJourney()
        pagesJourney()
        consoleJourney()
    }
}
