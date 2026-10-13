package dev.pluto.launcher.ui.console

import dev.pluto.launcher.R
import dev.pluto.launcher.data.prefs.ControllerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLegendTest {
    @Test
    fun cardsOfferOpenAndActions() {
        assertEquals(ConsoleLegendKind.CARDS, consoleLegendKindFor("${CARD_ID_PREFIX}all:0|com.example/.Main", hasApps = true))
        val legend = consoleLegendLabels(ConsoleLegendKind.CARDS).toMap()
        assertEquals(R.string.console_legend_open, legend[ControllerAction.CONFIRM])
        assertEquals(R.string.console_legend_actions, legend[ControllerAction.ACTIONS])
        assertEquals(R.string.console_legend_shelves, legend[ControllerAction.NEXT_CATEGORY])
    }

    @Test
    fun tabsAndButtonsOnlySelect() {
        for (id in listOf("${TAB_ID_PREFIX}all", "hh:search", "hh:empty:allapps")) {
            val kind = consoleLegendKindFor(id, hasApps = true)
            assertEquals(ConsoleLegendKind.CONTROLS, kind)
            val legend = consoleLegendLabels(kind)
            assertEquals(R.string.console_legend_select, legend.first { it.first == ControllerAction.CONFIRM }.second)
            assertTrue(legend.none { it.first == ControllerAction.ACTIONS })
        }
    }

    @Test
    fun beforeFocusLandsTheShelfDecides() {
        assertEquals(ConsoleLegendKind.CARDS, consoleLegendKindFor(null, hasApps = true))
        assertEquals(ConsoleLegendKind.EMPTY, consoleLegendKindFor(null, hasApps = false))
    }

    @Test
    fun anEmptyShelfHasNothingToOpenOrActOn() {
        val legend = consoleLegendLabels(ConsoleLegendKind.EMPTY).map { it.first }
        assertTrue(ControllerAction.CONFIRM !in legend && ControllerAction.ACTIONS !in legend)
        assertTrue(ControllerAction.SEARCH in legend && ControllerAction.SETTINGS in legend)
    }
}
