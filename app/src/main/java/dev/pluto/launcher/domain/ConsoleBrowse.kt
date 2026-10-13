package dev.pluto.launcher.domain

import dev.pluto.launcher.model.RomGame

/** How the console browse grid orders the games of one shelf. */
enum class RomSort {
    TITLE,
    /** Last opened from Pluto; a game never opened here uses when its file changed. */
    RECENT,
    FILENAME,
}

/**
 * Filter and sort rules for the console browse grid (the "Browse shelf" screen). Pure and
 * Android-free so the ordering is unit tested; the grid only renders the result.
 *
 * [systems] comes from the last scan (`RomScanReport.systems`) and is only populated when a
 * shelf holds more than one system; an entry missing there falls back to the file type.
 */
object ConsoleBrowse {
    /**
     * System chips to offer: every system present, alphabetical. Empty unless the last scan saw
     * several systems by folder: file types alone mislabel a one-system folder (a `.pbp` in a
     * PlayStation folder is not a PSP game, and arcade `.zip` sets are not "Other").
     */
    fun systems(games: List<RomGame>, systems: Map<String, String>): List<String> {
        if (systems.isEmpty()) return emptyList()
        return games.map { systemOf(it, systems) }.distinct().sorted().takeIf { it.size > 1 }.orEmpty()
    }

    /** The system of [game], preferring what the scan saw over the file type alone. */
    fun systemOf(game: RomGame, systems: Map<String, String>): String =
        systems[game.uri] ?: RomSystems.of(game.fileName)

    /** Games matching [system] (null is every system) and [favouritesOnly], in their saved order. */
    fun select(
        games: List<RomGame>,
        system: String?,
        systems: Map<String, String>,
        favouritesOnly: Boolean,
        favourites: Set<String>,
    ): List<RomGame> = games.filter { game ->
        (!favouritesOnly || game.uri in favourites) &&
            (system == null || systemOf(game, systems) == system)
    }

    /**
     * Orders [games] for display. [played] is the per-game play date from the settings file;
     * without one a game falls back to when its file changed. Ties break on title so a rescan
     * never reshuffles the grid.
     */
    fun sort(games: List<RomGame>, sort: RomSort, played: Map<String, Long> = emptyMap()): List<RomGame> = when (sort) {
        RomSort.TITLE -> games.sortedWith(compareBy<RomGame, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
        RomSort.RECENT -> games.sortedWith(
            compareByDescending<RomGame> { RomHistory.lastPlayed(played, it) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        RomSort.FILENAME -> games.sortedWith(compareBy<RomGame, String>(String.CASE_INSENSITIVE_ORDER) { it.fileName })
    }
}
