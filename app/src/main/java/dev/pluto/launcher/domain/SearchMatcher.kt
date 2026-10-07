package dev.pluto.launcher.domain

import dev.pluto.launcher.model.AppEntry

object SearchMatcher {
    /** Lower-cases (Locale.ROOT), strips accents/diacritics (NFD + remove combining marks), trims, collapses whitespace. */
    fun normalize(text: String): String = TODO()

    /** True if the normalized query is contained in the normalized label or package name. Blank query matches all. */
    fun matches(entry: AppEntry, query: String): Boolean = TODO()

    /**
     * Filters and ranks: label prefix, then label word-prefix, then label substring,
     * then package-name substring; ties keep the input (alphabetical) order.
     * Blank query returns [apps] unchanged.
     */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> = TODO()
}
