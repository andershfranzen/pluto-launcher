package dev.pluto.launcher.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import dev.pluto.launcher.apps.IconCache
import dev.pluto.launcher.model.AppEntry
import dev.pluto.launcher.model.AppKey
import dev.pluto.launcher.system.NotificationApp
import dev.pluto.launcher.ui.components.AppIcon
import dev.pluto.launcher.ui.components.LocalIconCache
import dev.pluto.launcher.ui.components.LocalNotificationApps
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NotificationDotIconTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun dotRendersAtIconCornerAndClearsItsAccessibleState() {
        val apps = mutableStateOf(setOf(NotificationApp("dev.pluto.launcher", 0)))
        val entry = AppEntry(AppKey("dev.pluto.launcher/dev.pluto.launcher.MainActivity", 0), "Pluto")
        val icons = IconCache(rule.activity)
        rule.setContent {
            CompositionLocalProvider(LocalIconCache provides icons, LocalNotificationApps provides apps.value) {
                AppIcon(entry, 64.dp, Modifier.testTag("dot-icon"))
            }
        }
        val icon = rule.onNodeWithTag("dot-icon")
        icon.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Has notifications"))
        val pixels = icon.captureToImage().toPixelMap()
        val density = rule.activity.resources.displayMetrics.density
        val radius = (pixels.width * 0.095f).coerceIn(3 * density, 7 * density)
        assertEquals(Color(0xFFFF453A), pixels[(pixels.width - radius - density).toInt(), (radius + density).toInt()])
        rule.runOnIdle { apps.value = emptySet() }
        icon.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }
}
