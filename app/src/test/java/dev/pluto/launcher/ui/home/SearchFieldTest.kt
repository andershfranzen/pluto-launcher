package dev.pluto.launcher.ui.home

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchFieldTest {
    @Test fun delayedEchoDoesNotRewindTheEditor() {
        val field = SearchField("")
        field.state.setTextAndPlaceCursorAtEnd("s")
        field.onUserText("s")
        field.state.setTextAndPlaceCursorAtEnd("settings")
        field.onUserText("settings")
        field.onVmText("s")
        assertEquals("settings", field.state.text.toString())
        field.onVmText("settings")
        assertEquals("settings", field.state.text.toString())
    }

    @Test fun clearIsImmediateAndAnOutstandingEchoCannotUndoIt() {
        val field = SearchField("")
        field.state.setTextAndPlaceCursorAtEnd("settings")
        field.onUserText("settings")
        field.clear()
        field.onVmText("settings")
        assertEquals("", field.state.text.toString())
        field.onVmText("")
        assertEquals("", field.state.text.toString())
    }

    @Test fun programmaticSearchRestorationStillUpdatesTheEditor() {
        val field = SearchField("")
        field.onVmText("camera")
        assertEquals("camera", field.state.text.toString())
        assertEquals(6, field.state.selection.end)
    }
}
