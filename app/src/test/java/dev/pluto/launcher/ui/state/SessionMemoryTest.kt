package dev.pluto.launcher.ui.state

import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.ui.FixedSession
import dev.pluto.launcher.ui.Layer
import dev.pluto.launcher.ui.LauncherUiState
import dev.pluto.launcher.ui.LiveSession
import dev.pluto.launcher.ui.SessionMemory
import dev.pluto.launcher.ui.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMemoryTest {
    @Test
    fun anchorsAreRecordedAndClearedPerSurface() {
        val memory = SessionMemory()
        assertTrue(memory.setScrollAnchor("drawer", "a"))
        assertFalse(memory.setScrollAnchor("drawer", "a"))
        assertTrue(memory.setScrollAnchor("drawer", "b"))
        assertTrue(memory.setScrollAnchor("home", "x"))
        assertEquals(mapOf("drawer" to "b", "home" to "x"), memory.scrollAnchors)
        assertTrue(memory.setScrollAnchor("drawer", null))
        assertFalse(memory.setScrollAnchor("drawer", null))
        assertEquals(mapOf("home" to "x"), memory.scrollAnchors)
    }

    @Test
    fun sessionCopiesShareMemoryAndCompareWithoutIt() {
        val memory = SessionMemory()
        val a = SessionState(memory = memory)
        val b = a.copy(layers = listOf(Layer.Drawer))
        memory.setScrollAnchor("drawer", "app:1")
        memory.selectedApp = AppKey("p/.A", 0)
        // Anchor and selection updates are not session changes (no new state, no recomposition).
        assertEquals(a, a.copy())
        assertSame(a.memory, b.memory)
        assertEquals("app:1", b.scrollAnchors["drawer"])
        assertEquals(AppKey("p/.A", 0), b.selectedApp)
        assertNotEquals(a, b)
    }

    @Test
    fun contentInputsAreSearchAndCategoryOnly() {
        val s = SessionState()
        assertTrue(s.sameContentInputs(s.copy(layers = listOf(Layer.Drawer), searchActive = true, editSelection = "x")))
        assertFalse(s.sameContentInputs(s.copy(searchText = "a")))
        assertFalse(s.sameContentInputs(s.copy(activeCategoryId = 3)))
    }

    @Test
    fun liveSessionIsReadThroughTheStateAndFrozenCopiesStop() {
        val live = LiveSession(SessionState())
        val state = LauncherUiState(sessionSource = live)
        val frozen = state.frozen()
        live.value = live.value.copy(layers = listOf(Layer.Settings))
        assertEquals(Layer.Settings, state.session.topLayer)
        assertNull(frozen.session.topLayer)
        assertTrue(frozen.sessionSource is FixedSession)
        assertSame(frozen, frozen.frozen())
    }

    @Test
    fun savedSessionDecodesAnchorsSelectionAndDropsUnknownLayers() {
        val session = SessionSaver.decode(
            layers = listOf("drawer", "bogus:1", "settings"),
            searchText = "cal",
            searchActive = true,
            category = 4L,
            selectedApp = AppKey("com.example/.Main", 0).encode(),
            focusedControl = "dock:2",
            surfaces = arrayOf("drawer", "home"),
            items = arrayOf("app:a", "app:b"),
            editSelection = null,
        )
        assertEquals(listOf(Layer.Drawer, Layer.Settings), session.layers)
        assertEquals("cal", session.searchText)
        assertTrue(session.searchActive)
        assertEquals(4L, session.activeCategoryId)
        assertEquals(AppKey("com.example/.Main", 0), session.selectedApp)
        assertEquals("dock:2", session.focusedControlId)
        assertEquals(mapOf("drawer" to "app:a", "home" to "app:b"), session.scrollAnchors)
    }

    @Test
    fun memoKeepsEqualResultsAndRecomputesOnlyOnNewKeys() {
        val memo = Memo<String, List<Int>>()
        var computed = 0
        val first = memo.get("a") { computed++; listOf(1, 2) }
        val again = memo.get("a") { computed++; listOf(9) }
        val equalResult = memo.get("b") { computed++; listOf(1, 2) }
        assertEquals(2, computed)
        assertSame(first, again)
        assertSame(first, equalResult)
    }
}
