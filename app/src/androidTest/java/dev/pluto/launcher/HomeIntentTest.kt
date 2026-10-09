package dev.pluto.launcher

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherViewModel
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home pressed while Pluto is already in front closes open layers; returning home from
 * another app keeps them. The framework pauses the singleTask activity before delivering the
 * Home intent, so this guards the "already in front" detection in MainActivity.onNewIntent.
 *
 * Android only routes a real Home press to the default Home app, so the test makes Pluto
 * the Home role holder for its duration and restores the previous holder afterwards.
 */
@RunWith(AndroidJUnit4::class)
class HomeIntentTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val packageName = instrumentation.targetContext.packageName
    private var previousHome: String? = null

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }.trim()
    }

    @Before
    fun becomeHome() {
        previousHome = shell("cmd role get-role-holders android.app.role.HOME").lineSequence().firstOrNull()?.trim()
        if (previousHome != packageName) shell("cmd role add-role-holder android.app.role.HOME $packageName 0")
    }

    @After
    fun restoreHome() {
        val previous = previousHome
        if (!previous.isNullOrBlank() && previous != packageName) {
            shell("cmd role add-role-holder android.app.role.HOME $previous 0")
        }
    }

    private fun pressHome() = shell("input keyevent KEYCODE_HOME")

    private fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    private fun resumedPluto(): MainActivity? {
        var found: MainActivity? = null
        instrumentation.runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
                .firstOrNull()
        }
        return found
    }

    private fun showPluto(): LauncherViewModel {
        pressHome()
        var activity: MainActivity? = null
        assertTrue("Pluto did not come to the front", awaitCondition { resumedPluto()?.also { activity = it } != null })
        val vm = activity!!.viewModel
        assertNotNull(vm)
        return vm
    }

    private fun LauncherViewModel.layers() = state.value.session.layers

    @Test
    fun homeWhileInFrontClosesTheDrawer() {
        val vm = showPluto()
        instrumentation.runOnMainSync { vm.openDrawer(withSearch = true) }
        assertTrue(awaitCondition { Layer.Drawer in vm.layers() })
        SystemClock.sleep(300)

        pressHome()

        assertTrue(
            "Home did not close the drawer",
            awaitCondition { Layer.Drawer !in vm.layers() && !vm.state.value.session.searchActive },
        )
        // Onboarding (fresh install) is the only layer Home keeps.
        assertTrue(vm.layers().all { it == Layer.Onboarding })
    }

    @Test
    fun returningHomeFromAnotherAppKeepsLayers() {
        val vm = showPluto()
        instrumentation.runOnMainSync { vm.openDrawer() }
        assertTrue(awaitCondition { Layer.Drawer in vm.layers() })

        // Open another app (Settings) so Pluto is stopped, then come back with Home.
        shell("am start -W -a android.settings.SETTINGS")
        assertTrue(awaitCondition { resumedPluto() == null })
        // Paused is not stopped: on a software-rendered emulator the Settings window
        // can take longer than a fixed sleep to draw and cover Pluto. Wait for the
        // lifecycle state this test's returning-from-another-app contract requires.
        assertTrue("Pluto was not stopped behind Settings", awaitCondition(10_000) {
            var stopped = false
            instrumentation.runOnMainSync {
                stopped = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.STOPPED)
                    .filterIsInstance<MainActivity>()
                    .any { it.viewModel === vm }
            }
            stopped
        })
        pressHome()
        assertTrue(awaitCondition { resumedPluto() != null })
        SystemClock.sleep(500)

        assertTrue("Returning home closed the drawer", Layer.Drawer in vm.layers())
        instrumentation.runOnMainSync { vm.closeAllLayers() }
    }
}
