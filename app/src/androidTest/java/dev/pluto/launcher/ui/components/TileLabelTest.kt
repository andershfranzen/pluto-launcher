package dev.pluto.launcher.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pluto.launcher.ui.theme.LocalDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Screenshot check: a tile label is drawn centred in its tile (under the centred icon). */
@RunWith(AndroidJUnit4::class)
class TileLabelTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shortLabelIsHorizontallyCentred() {
        rule.setContent {
            CompositionLocalProvider(LocalDarkTheme provides false) {
                MaterialTheme(colorScheme = lightColorScheme(onSurface = Color.Black)) {
                    Box(Modifier.width(160.dp).background(Color.White).testTag("tile")) {
                        TileLabel("Clock")
                    }
                }
            }
        }
        val pixels = rule.onNodeWithTag("tile").captureToImage().toPixelMap()
        var left = Int.MAX_VALUE
        var right = -1
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val c = pixels[x, y]
                // Ink: the dark glyphs (the white halo shadow is ignored).
                if (c.red < 0.5f && c.green < 0.5f && c.blue < 0.5f) {
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        assertTrue("no text drawn", right >= 0)
        val inkCentre = (left + right) / 2f
        val tileCentre = pixels.width / 2f
        assertTrue(
            "label ink centred at $inkCentre, tile centre $tileCentre (ink $left..$right of ${pixels.width})",
            abs(inkCentre - tileCentre) <= pixels.width * 0.03f,
        )
    }
}
