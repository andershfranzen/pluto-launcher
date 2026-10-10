package dev.pluto.launcher.domain

import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.LauncherMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ModeResolverTest {
    private val portrait = 411 to 891
    private val landscape = 891 to 411

    private fun resolve(window: Pair<Int, Int>, controller: Boolean, appearance: HandheldAppearance) =
        ModeResolver.resolve(window.first, window.second, controller, appearance)

    @Test
    fun portraitIsPhoneUnlessConsoleEverywhere() {
        for (appearance in HandheldAppearance.entries - HandheldAppearance.EVERYWHERE) {
            for (controller in listOf(false, true)) {
                assertEquals("$appearance controller=$controller", LauncherMode.PHONE, resolve(portrait, controller, appearance))
            }
        }
    }

    @Test
    fun squareWindowCountsAsPortrait() {
        assertEquals(LauncherMode.PHONE, ModeResolver.resolve(600, 600, true, HandheldAppearance.ALWAYS))
    }

    @Test
    fun landscapeAutomaticFollowsController() {
        assertEquals(LauncherMode.LANDSCAPE, resolve(landscape, false, HandheldAppearance.AUTOMATIC))
        assertEquals(LauncherMode.HANDHELD, resolve(landscape, true, HandheldAppearance.AUTOMATIC))
    }

    @Test
    fun landscapeAlwaysIsHandheld() {
        assertEquals(LauncherMode.HANDHELD, resolve(landscape, false, HandheldAppearance.ALWAYS))
        assertEquals(LauncherMode.HANDHELD, resolve(landscape, true, HandheldAppearance.ALWAYS))
    }

    @Test
    fun landscapeNeverStaysTouchLayout() {
        assertEquals(LauncherMode.LANDSCAPE, resolve(landscape, false, HandheldAppearance.NEVER))
        assertEquals(LauncherMode.LANDSCAPE, resolve(landscape, true, HandheldAppearance.NEVER))
    }

    @Test
    fun usesWindowShapeNotAbsoluteSize() {
        // A small landscape split-screen window is still landscape.
        assertEquals(LauncherMode.HANDHELD, ModeResolver.resolve(400, 300, true, HandheldAppearance.AUTOMATIC))
        // A tall window on a large screen is portrait.
        assertEquals(LauncherMode.PHONE, ModeResolver.resolve(1000, 1001, true, HandheldAppearance.AUTOMATIC))
    }
}
