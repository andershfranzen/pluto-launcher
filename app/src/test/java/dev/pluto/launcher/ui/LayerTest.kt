package dev.pluto.launcher.ui

import dev.pluto.launcher.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayerTest {
    private val key = AppKey("com.example/.Main", 0)

    private val allLayers = listOf(
        Layer.Drawer,
        Layer.FolderLayer(7),
        Layer.AppActions(key),
        Layer.CategoryMembership(key),
        Layer.MoveToFolder(key),
        Layer.Edit,
        Layer.Settings,
        Layer.HiddenApps,
        Layer.Categories,
        Layer.ControllerSettings,
        Layer.Onboarding,
    )

    @Test
    fun everyLayerRoundTrips() {
        for (layer in allLayers) {
            assertEquals(layer, Layer.decode(Layer.encode(layer)))
        }
    }

    @Test
    fun encodedFormsAreDistinct() {
        assertEquals(allLayers.size, allLayers.map(Layer::encode).toSet().size)
    }

    @Test
    fun decodeRejectsMalformed() {
        assertNull(Layer.decode(""))
        assertNull(Layer.decode("unknown"))
        assertNull(Layer.decode("folder:x"))
        assertNull(Layer.decode("actions:bad"))
        assertNull(Layer.decode("membership:"))
    }

    @Test
    fun sessionTopLayerIsLast() {
        assertNull(SessionState().topLayer)
        assertEquals(Layer.Settings, SessionState(layers = listOf(Layer.Drawer, Layer.Settings)).topLayer)
    }
}
