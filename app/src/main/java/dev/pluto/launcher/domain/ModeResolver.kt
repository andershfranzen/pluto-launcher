package dev.pluto.launcher.domain

import dev.pluto.launcher.model.HandheldAppearance
import dev.pluto.launcher.model.LauncherMode

object ModeResolver {
    /**
     * Picks the presentation from the current window shape (not device orientation)
     * and whether a recognised gamepad is connected.
     *
     * Portrait (height >= width) -> PHONE, always.
     * Landscape -> HANDHELD when [appearance] is ALWAYS, or AUTOMATIC with a controller;
     * otherwise LANDSCAPE.
     */
    fun resolve(
        widthDp: Int,
        heightDp: Int,
        controllerConnected: Boolean,
        appearance: HandheldAppearance,
    ): LauncherMode {
        if (heightDp >= widthDp) return LauncherMode.PHONE
        val handheld = when (appearance) {
            HandheldAppearance.ALWAYS -> true
            HandheldAppearance.AUTOMATIC -> controllerConnected
            HandheldAppearance.NEVER -> false
        }
        return if (handheld) LauncherMode.HANDHELD else LauncherMode.LANDSCAPE
    }
}
