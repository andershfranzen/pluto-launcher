package dev.pluto.launcher.domain

import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.LauncherMode

object ModeResolver {
    /**
     * Picks the presentation from the current window shape (not device orientation)
     * and whether a recognised gamepad is connected.
     *
     * EVERYWHERE -> HANDHELD (the activity also holds the launcher in landscape, so the
     *   window is landscape; a portrait window, e.g. split screen, still gets the console).
     * Otherwise portrait (height >= width) -> PHONE; landscape -> HANDHELD when [appearance]
     * is ALWAYS, or AUTOMATIC with a controller; otherwise LANDSCAPE.
     */
    fun resolve(
        widthDp: Int,
        heightDp: Int,
        controllerConnected: Boolean,
        appearance: HandheldAppearance,
    ): LauncherMode {
        if (appearance == HandheldAppearance.EVERYWHERE) return LauncherMode.HANDHELD
        if (heightDp >= widthDp) return LauncherMode.PHONE
        val handheld = when (appearance) {
            HandheldAppearance.ALWAYS -> true
            HandheldAppearance.AUTOMATIC -> controllerConnected
            HandheldAppearance.NEVER -> false
            HandheldAppearance.EVERYWHERE -> true
        }
        return if (handheld) LauncherMode.HANDHELD else LauncherMode.LANDSCAPE
    }
}
