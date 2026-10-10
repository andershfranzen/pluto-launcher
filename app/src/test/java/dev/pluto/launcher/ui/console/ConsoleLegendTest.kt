package dev.pluto.launcher.ui.console

import dev.pluto.launcher.data.prefs.ControllerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLegendTest {
    @Test
    fun cardsOfferOpenAndActions() {
        val legend = consoleLegendFor("${CARD_ID_PREFIX}all:0|com.example/.Main", hasApps = true)
        assertEquals(ConsoleLegend, legend)
    }

    @Test
    fun tabsAndButtonsOnlySelect() {
        for (id in listOf("${TAB_ID_PREFIX}all", "hh:search", "hh:empty:allapps")) {
            val legend = consoleLegendFor(id, hasApps = true)
            assertEquals("Select", legend.first { it.action == ControllerAction.CONFIRM }.label)
            assertTrue(legend.none { it.action == ControllerAction.ACTIONS })
        }
    }

    @Test
    fun beforeFocusLandsTheShelfDecides() {
        assertEquals(ConsoleLegend, consoleLegendFor(null, hasApps = true))
        assertEquals(ConsoleLegendEmpty, consoleLegendFor(null, hasApps = false))
    }
}
