package dev.pluto.launcher.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import dev.pluto.launcher.model.ThemePreference
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pluto.launcher.data.prefs.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlutoThemeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBarIconsFollowTheEffectiveTheme() {
        var theme by mutableStateOf(ThemePreference.LIGHT)
        rule.setContent { PlutoTheme(LauncherSettings(theme = theme)) {} }
        fun controller() = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
        rule.runOnIdle {
            // Light theme: dark icons over the light scrim.
            assertTrue(controller().isAppearanceLightStatusBars)
            assertTrue(controller().isAppearanceLightNavigationBars)
        }
        theme = ThemePreference.DARK
        rule.runOnIdle {
            assertFalse(controller().isAppearanceLightStatusBars)
            assertFalse(controller().isAppearanceLightNavigationBars)
        }
    }

    @Test
    fun reducedMotionReplacesTheRipple() {
        var indication: Indication? = null
        var reduced = false
        rule.setContent {
            PlutoTheme(LauncherSettings(reducedMotion = true)) {
                indication = LocalIndication.current
                reduced = LocalReducedMotion.current
            }
        }
        rule.runOnIdle {
            assertSame(ReducedMotionIndication, indication)
            assertTrue(reduced)
        }
    }

    @Test
    fun normalMotionKeepsTheMaterialIndication() {
        var indication: Indication? = null
        var reduced = true
        rule.setContent {
            PlutoTheme(LauncherSettings(reducedMotion = false)) {
                indication = LocalIndication.current
                reduced = LocalReducedMotion.current
            }
        }
        rule.runOnIdle {
            assertNotSame(ReducedMotionIndication, indication)
            assertFalse(reduced)
        }
    }

    @Test
    fun defaultTextSizeKeepsThePlatformDensity() {
        var outer: Density? = null
        var inner: Density? = null
        rule.setContent {
            outer = LocalDensity.current
            PlutoTheme(LauncherSettings(textScale = 1f)) { inner = LocalDensity.current }
        }
        // The platform density carries Android 14+'s non-linear font scale converter; it must not be replaced.
        rule.runOnIdle { assertSame(outer, inner) }
    }

    @Test
    fun textSizeMultipliesThePlatformConversion() {
        var outer: Density? = null
        var inner: Density? = null
        rule.setContent {
            outer = LocalDensity.current
            PlutoTheme(LauncherSettings(textScale = 1.5f)) { inner = LocalDensity.current }
        }
        rule.runOnIdle {
            val base = with(outer!!) { 45.sp.toDp() }.value
            val scaled = with(inner!!) { 45.sp.toDp() }.value
            assertEquals(base * 1.5f, scaled, 0.01f)
        }
    }
}
