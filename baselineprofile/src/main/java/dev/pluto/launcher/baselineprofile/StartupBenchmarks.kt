package dev.pluto.launcher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that the profile pays off: cold start and the drawer journey without any AOT
 * compilation versus with the baseline profile. Run on emulator-5554:
 *
 *     ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *
 * Emulator numbers are only good for comparing the two modes, not for the phone's targets.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun prepareFixtures() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        androidx.test.uiautomator.Configurator.getInstance().waitForIdleTimeout = 1_000L
        val result = device.executeShellCommand("content call --uri content://$PACKAGE.profile-fixtures --method seed")
        check(result.contains("status=ready")) { "Benchmark fixture setup failed: $result" }
        val home = device.executeShellCommand("cmd package set-home-activity $PACKAGE/.MainActivity")
        check(!home.contains("Error", ignoreCase = true)) { "Could not set benchmark HOME: $home" }
    }

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun drawerNoCompilation() = drawer(CompilationMode.None())

    @Test
    fun drawerBaselineProfile() = drawer(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = {
            // Pluto is default HOME for shortcuts. Keep a different activity foreground
            // before Macrobenchmark's cold-process kill so HOME cannot prelaunch Pluto.
            device.executeShellCommand("am start -W -a android.settings.SETTINGS")
        },
    ) {
        startPluto()
    }

    private fun drawer(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startPluto()
            finishOnboarding()
        },
    ) {
        drawerJourney()
    }
}
