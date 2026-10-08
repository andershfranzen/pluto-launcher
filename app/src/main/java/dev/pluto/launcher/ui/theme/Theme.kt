package dev.pluto.launcher.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.pluto.launcher.data.prefs.LauncherSettings
import dev.pluto.launcher.model.ThemePreference
import dev.pluto.launcher.ui.motion.PlutoMotion

/**
 * True when the user asked for reduced motion; skip non-essential animation. Animations are
 * already scaled to zero window-wide (see [LauncherMotionDurationScale]); read this for any
 * motion that is not a Compose animation.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** True when the launcher is currently rendered in its dark theme. */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** Shared shape/size tokens so every surface feels like one product. */
object PlutoDimens {
    val PanelCorner = 24.dp
    val TileCorner = 16.dp
    val MinTouchTarget = 48.dp
    /**
     * Panels (drawer, sheets, dialogs, folders) are opaque: content beneath must never show
     * through text. Folder tile backgrounds over the wallpaper keep a slight translucency.
     */
    const val PanelAlpha = 1f
    const val TileBackdropAlpha = 0.92f

    /**
     * The drawer sheet is near-opaque: just enough to hint at the wallpaper behind the
     * brand-tinted surface (a frosted look) without anything competing with text.
     */
    const val SheetAlpha = 0.97f
}

// A calm, space-inspired fallback palette: deep navy night with pale tan accents echoing the Pluto icon.
private val PlutoDark = darkColorScheme(
    primary = Color(0xFFE9CBA7),
    onPrimary = Color(0xFF3B2814),
    primaryContainer = Color(0xFF5A4129),
    onPrimaryContainer = Color(0xFFFFE6CC),
    secondary = Color(0xFFBCC6EA),
    onSecondary = Color(0xFF232E4F),
    secondaryContainer = Color(0xFF3A4568),
    onSecondaryContainer = Color(0xFFDDE3FF),
    tertiary = Color(0xFFE7B3A3),
    onTertiary = Color(0xFF452A20),
    tertiaryContainer = Color(0xFF703A2B),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF0B1022),
    onBackground = Color(0xFFE4E3EC),
    surface = Color(0xFF0B1022),
    onSurface = Color(0xFFE4E3EC),
    surfaceVariant = Color(0xFF2D3247),
    onSurfaceVariant = Color(0xFFC5C6D8),
    surfaceContainerLowest = Color(0xFF070B19),
    surfaceContainerLow = Color(0xFF121831),
    surfaceContainer = Color(0xFF161D38),
    surfaceContainerHigh = Color(0xFF1F2643),
    surfaceContainerHighest = Color(0xFF2A314F),
    outline = Color(0xFF8E90A6),
    outlineVariant = Color(0xFF444960),
)

private val PlutoLight = lightColorScheme(
    primary = Color(0xFF7A5531),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBC),
    onPrimaryContainer = Color(0xFF2C1704),
    secondary = Color(0xFF4A5684),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE1FF),
    onSecondaryContainer = Color(0xFF041542),
    tertiary = Color(0xFF8E4A39),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBD1),
    onTertiaryContainer = Color(0xFF3A0B02),
    background = Color(0xFFFBF8F4),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8F4),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE6E1DA),
    onSurfaceVariant = Color(0xFF4A4640),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F1EB),
    surfaceContainer = Color(0xFFF0EBE5),
    surfaceContainerHigh = Color(0xFFEAE5DF),
    surfaceContainerHighest = Color(0xFFE4DFD9),
    outline = Color(0xFF7B766F),
    outlineVariant = Color(0xFFCBC5BD),
)

/**
 * Pluto's Material 3 theme. Dark/light follows [LauncherSettings.theme]; wallpaper-based
 * dynamic colour is used on Android 12+, otherwise the Pluto palette. Text is scaled by
 * [LauncherSettings.textScale] on top of the system font size, keeping Android 14+'s
 * non-linear font scaling (large text grows less than small text). With reduced motion,
 * press feedback is a static state layer instead of the animated ripple.
 *
 * The status and navigation bar icons follow the effective theme (dark icons over the
 * light scrim, light icons over the dark one). This re-runs whenever the setting or the
 * system night mode changes, since uiMode is handled in-process and the activity is never
 * recreated for it.
 */
@Composable
fun PlutoTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }
    val context = LocalContext.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val colorScheme: ColorScheme = remember(dark, context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (dark) PlutoDark else PlutoLight
        }
    }
    val density = LocalDensity.current
    val textScale = settings.textScale.coerceIn(LauncherSettings.TEXT_SCALE_RANGE)
    val scaledDensity = remember(density, textScale) {
        if (textScale == 1f) density else TextScaledDensity(density, textScale)
    }
    val reducedMotion = settings.reducedMotion
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalReducedMotion provides reducedMotion,
        LocalDarkTheme provides dark,
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            if (reducedMotion) {
                CompositionLocalProvider(LocalIndication provides ReducedMotionIndication, content = content)
            } else {
                content()
            }
        }
    }
}

/**
 * Console (handheld) mode is an immersive dark stage whatever the app theme: the dark
 * colour scheme (dynamic on Android 12+) and light text, over [ConsoleScrimAlpha] of black.
 * Only the console's own content is wrapped; layers above it keep the user's theme.
 */
@Composable
fun ConsoleTheme(content: @Composable () -> Unit) {
    if (LocalDarkTheme.current) {
        content()
        return
    }
    val context = LocalContext.current
    val scheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else PlutoDark
    }
    CompositionLocalProvider(LocalDarkTheme provides true) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}

/** Black over the wallpaper behind the console stage. */
const val ConsoleScrimAlpha = 0.7f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * [base] with Pluto's text-size multiplier applied *after* the platform's sp -> dp
 * conversion, so the system's (possibly non-linear) font scale curve is kept. Only the
 * sp conversions change; [fontScale] reports the overall factor at 1sp for code that reads it.
 */
internal class TextScaledDensity(private val base: Density, private val textScale: Float) : Density {
    override val density: Float get() = base.density
    override val fontScale: Float get() = base.fontScale * textScale

    override fun TextUnit.toDp(): Dp = with(base) { this@toDp.toDp() } * textScale

    override fun Dp.toSp(): TextUnit = with(base) { (this@toSp / textScale).toSp() }

    override fun equals(other: Any?): Boolean =
        other is TextScaledDensity && other.base == base && other.textScale == textScale

    override fun hashCode(): Int = 31 * base.hashCode() + textScale.hashCode()
}

/**
 * Full-screen contrast scrim over the system wallpaper (black in dark, white in light).
 * Strength and theme changes glide instead of flashing; drawn in the draw phase only.
 */
@Composable
fun WallpaperScrim(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val alpha = settings.scrimAlpha.coerceIn(0f, LauncherSettings.SCRIM_MAX)
    val base = if (LocalDarkTheme.current) Color.Black else Color.White
    val color by animateColorAsState(base.copy(alpha = alpha), PlutoMotion.effects(), label = "wallpaperScrim")
    Box(modifier.fillMaxSize().drawBehind { drawRect(color) })
}

/**
 * Text style for labels drawn directly on the wallpaper (clock, home tile labels):
 * the theme's on-surface colour plus a soft shadow so it stays legible on any image.
 */
@Composable
fun TextStyle.overWallpaper(): TextStyle {
    val dark = LocalDarkTheme.current
    return copy(
        color = MaterialTheme.colorScheme.onSurface,
        shadow = Shadow(
            color = if (dark) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f),
            offset = Offset(0f, 1.5f),
            blurRadius = 6f,
        ),
    )
}
