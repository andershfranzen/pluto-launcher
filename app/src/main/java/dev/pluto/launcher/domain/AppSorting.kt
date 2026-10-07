package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry
import java.util.Locale

object AppSorting {
    /** Locale-aware, case- and accent-insensitive alphabetical order; ties broken by package then component. */
    fun alphabetical(apps: Collection<AppEntry>, locale: Locale = Locale.getDefault()): List<AppEntry> = TODO()
}
