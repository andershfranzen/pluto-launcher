package dev.pluto.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Pluto's own identity on top of Material 3 (and of wallpaper-based dynamic colour on
 * Android 12+): deep-space navy and pale tan from the logo. Surfaces that carry the brand
 * (the drawer sheet, folders, panels) blend the theme's surface towards these, so the
 * launcher keeps its character whatever the wallpaper's palette is. Accents (selection,
 * focus) stay on the theme's primary so contrast rules hold in both themes.
 */
object PlutoBrand {
    /** Night sky behind the planet. */
    val Navy = Color(0xFF0B1022)

    /** Slightly lifted navy for raised elements on a sheet (search pill, chips). */
    val NavyRaised = Color(0xFF1C2444)

    /** The planet's pale tan. */
    val Tan = Color(0xFFE9CBA7)

    /** Warm paper used for light surfaces. */
    val Paper = Color(0xFFFBF8F4)

    /** Warm, slightly darker paper for raised elements in the light theme. */
    val PaperRaised = Color(0xFFEFE8DD)

    /** Share of the brand colour mixed into the theme's (possibly dynamic) surfaces. */
    internal const val SURFACE_BLEND = 0.55f
}

/** Body colour of sheets and panels (drawer, folder, dock): the theme surface tinted towards the brand. */
@Composable
@ReadOnlyComposable
fun sheetColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val brand = if (LocalDarkTheme.current) PlutoBrand.Navy else PlutoBrand.Paper
    return lerp(scheme.surfaceContainerLow, brand, PlutoBrand.SURFACE_BLEND).copy(alpha = PlutoDimens.SheetAlpha)
}

/** Opaque body colour of panels (dock, folder, menus): consistent with [sheetColor], one step up. */
@Composable
@ReadOnlyComposable
fun panelColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val brand = if (LocalDarkTheme.current) PlutoBrand.Navy else PlutoBrand.Paper
    return lerp(scheme.surfaceContainer, brand, PlutoBrand.SURFACE_BLEND * 0.8f).copy(alpha = PlutoDimens.PanelAlpha)
}

/** Fill of raised elements on a sheet: the search pill and resting category chips. */
@Composable
@ReadOnlyComposable
fun sheetRaisedColor(): Color {
    val scheme = MaterialTheme.colorScheme
    val brand = if (LocalDarkTheme.current) PlutoBrand.NavyRaised else PlutoBrand.PaperRaised
    return lerp(scheme.surfaceContainerHigh, brand, PlutoBrand.SURFACE_BLEND)
}

/**
 * A faint light rim along a sheet's top edge (a frosted-glass edge) that fades out
 * downwards. Used as the sheet's border brush, so it follows the rounded corners.
 */
@Composable
@ReadOnlyComposable
fun sheetRimBrush(): Brush {
    val rim = if (LocalDarkTheme.current) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
    return Brush.verticalGradient(0f to rim, 0.1f to Color.Transparent)
}
