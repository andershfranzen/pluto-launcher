package dev.pluto.launcher.domain

import dev.pluto.launcher.domain.DefaultLayout.Handler
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLayoutTest {
    private fun entry(component: String, label: String, enabled: Boolean = true) =
        AppEntry(AppKey(component, 0), label, enabled)

    private val dialer = entry("com.phone/.Dialer", "Phone")
    private val messages = entry("com.sms/.Main", "Messages")
    private val browser = entry("com.web/.Browser", "Browser")
    private val browserIncognito = entry("com.web/.Incognito", "Browser Incognito")
    private val camera = entry("com.cam/.Camera", "Camera")
    private val settings = entry("com.settings/.Settings", "Settings")
    private val catalog = listOf(dialer, messages, browser, browserIncognito, camera, settings)

    @Test
    fun resolvedLauncherActivitiesFillDockInOrder() {
        val plan = DefaultLayout.choose(
            dockSlots = listOf(
                listOf(Handler("com.phone", "com.phone/.Dialer")),
                listOf(Handler("com.sms", "com.sms/.Compose")), // handler is not a launcher activity
                listOf(Handler("com.web", "com.web/.Incognito")),
                listOf(Handler("com.cam", "com.cam/.Camera")),
            ),
            homeSlots = listOf(listOf(Handler("com.settings", "com.settings/.Settings"))),
            catalog = catalog,
        )
        assertEquals(listOf(dialer.key, messages.key, browserIncognito.key, camera.key, null), plan.dock)
        assertEquals(listOf(settings.key), plan.home)
    }

    @Test
    fun unresolvedOrUnlaunchableSlotsAreSkippedAndDockIsPacked() {
        val disabledCamera = entry("com.cam/.Camera", "Camera", enabled = false)
        val plan = DefaultLayout.choose(
            dockSlots = listOf(
                emptyList(), // nothing resolved (or only the chooser)
                listOf(Handler("com.missing")), // not in the catalog
                listOf(Handler("com.cam")),
                listOf(Handler("com.web")),
            ),
            homeSlots = listOf(emptyList()),
            catalog = listOf(browser, browserIncognito, disabledCamera),
        )
        // The package's first launcher activity by label is used when the handler isn't one.
        assertEquals(listOf(browser.key, null, null, null, null), plan.dock)
        assertTrue(plan.home.isEmpty())
    }

    @Test
    fun laterCandidatesAreFallbacksAndPackagesAreUsedOnce() {
        val plan = DefaultLayout.choose(
            dockSlots = listOf(
                listOf(Handler("com.missing"), Handler("com.phone")),
                listOf(Handler("com.phone")), // already in the dock
            ),
            homeSlots = listOf(listOf(Handler("com.phone"), Handler("com.settings"))),
            catalog = catalog,
        )
        assertEquals(listOf(dialer.key, null, null, null, null), plan.dock)
        assertEquals(listOf(settings.key), plan.home)
    }

    @Test
    fun emptyCatalogGivesEmptyPlan() {
        val plan = DefaultLayout.choose(listOf(listOf(Handler("com.phone"))), listOf(listOf(Handler("com.settings"))), emptyList())
        assertTrue(plan.isEmpty)
    }
}
