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
    /** A short breakdown of [skipped] by extension, most common first (shown as ".sav×12, .txt×3"). */
    val skippedExtensions: List<ExtensionCount>,
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

/** How many skipped files share one extension; [extension] is lower case without the dot, empty for none. */
data class ExtensionCount(val extension: String, val count: Int)

/**
 * How long ago something happened, in the steps the UI words it: "today", "yesterday",
 * "5 days ago", "a week ago", "3 weeks ago", "2 months ago", "a year ago". Data, not text, so the
 * steps are tested here and the words come from string resources.
 */
sealed interface RomAge {
    data object Today : RomAge
    data object Yesterday : RomAge
    data class Days(val days: Int) : RomAge
    data object AWeek : RomAge
    data class Weeks(val weeks: Int) : RomAge
    data class Months(val months: Int) : RomAge
    data object AYear : RomAge
}

/** Summary helpers for [RomScanReport], kept off-device so they can be tested. */
object RomScanSummary {
    private const val MAX_TYPES = 4

    /**
     * A short, stable breakdown of file extensions, most common first, at most four types.
     * Files without an extension count under the empty extension. Empty when [names] is.
     */
    fun extensions(names: List<String>): List<ExtensionCount> =
        names.map { it.substringAfterLast('.', "").lowercase() }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_TYPES)
            .map { (extension, count) -> ExtensionCount(extension, count) }

    /** How long ago a save was written or a game was played, as a [RomAge] step. */
    fun age(now: Long, then: Long): RomAge {
        val days = ((now - then).coerceAtLeast(0L) / 86_400_000L)
        return when {
            days <= 0 -> RomAge.Today
            days == 1L -> RomAge.Yesterday
            days < 7 -> RomAge.Days(days.toInt())
            days < 14 -> RomAge.AWeek
            days < 60 -> RomAge.Weeks((days / 7).toInt())
            days < 365 -> RomAge.Months((days / 30).toInt())
            else -> RomAge.AYear
        }
    }
}
