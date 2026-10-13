package dev.pluto.launcher.domain

/**
 * What one scan of a ROM folder saw. Shown by the ROM diagnostics view so a folder that
 * looks empty, or a game that will not launch, explains itself instead of staying a mystery.
 */
data class RomScanReport(
    /** Documents walked in the folder and its subfolders. */
    val documents: Int,
    /** Files offered as games. */
    val games: Int,
    /** Games that found box art. */
    val gamesWithArt: Int,
    /** Files in the folder that are not games, artwork, playlists or referenced tracks. */
    val skipped: Int,
    /** A short breakdown of [skipped] by extension, e.g. ".sav×12, .txt×3". */
    val skippedExtensions: String?,
    /** Disc sheets and playlists read for track/disc references. */
    val sheets: Int,
    /** Tracks or discs a sheet names but the folder does not contain: these games cannot open. */
    val missingTracks: Int,
    /** `<game>` entries read from any gamelist.xml in the tree. */
    val gamelistEntries: Int,
    /** Wall-clock time of the scan, for the "last checked" line. */
    val at: Long,
    /** Games with save data beside them (battery saves or emulator states), by document URI. */
    val saves: Map<String, Long> = emptyMap(),
    /** The system of each game, present only when the folder holds more than one. */
    val systems: Map<String, String> = emptyMap(),
)

/** Formatting helpers for [RomScanReport], kept off-device so they can be tested. */
object RomScanSummary {
    private const val MAX_TYPES = 4

    /**
     * A short, stable breakdown of file extensions, most common first:
     * ".sav×12, .txt×3". Files without an extension are counted as "no extension".
     */
    fun extensions(names: List<String>): String? {
        if (names.isEmpty()) return null
        return names.map { it.substringAfterLast('.', "").lowercase() }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_TYPES)
            .joinToString(", ") { (extension, count) ->
                if (extension.isEmpty()) "no extension×$count" else ".$extension×$count"
            }
    }

    /**
     * How long ago a save was written, in words: "today", "yesterday", "5 days ago",
     * "3 weeks ago", "2 months ago", "a year ago". Kept here so the wording is tested.
     */
    fun age(now: Long, then: Long): String {
        val days = ((now - then).coerceAtLeast(0L) / 86_400_000L)
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "a week ago"
            days < 60 -> "${days / 7} weeks ago"
            days < 365 -> "${days / 30} months ago"
            else -> "a year ago"
        }
    }
}
