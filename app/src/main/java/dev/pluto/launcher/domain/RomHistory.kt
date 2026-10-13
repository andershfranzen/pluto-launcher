package dev.pluto.launcher.domain

import dev.pluto.launcher.model.RomGame

/**
 * Remembers when each game was last opened from Pluto. It lives in the settings file, so it is
 * capped: a date matters only for the games still in reach of the browse grid's Recent order,
 * and a game dropped from the map simply falls back to when its file changed.
 */
object RomHistory {
    /** Games kept with a play date; the oldest are dropped past this. */
    const val LIMIT = 200

    /** [played] with [uri] stamped at [at], keeping the [limit] most recently played games. */
    fun record(played: Map<String, Long>, uri: String, at: Long, limit: Int = LIMIT): Map<String, Long> {
        val updated = played + (uri to at)
        if (updated.size <= limit) return updated
        return updated.entries.sortedByDescending { it.value }.take(limit).associate { it.key to it.value }
    }

    /** The date a game counts as played: when Pluto opened it, else when its file changed. */
    fun lastPlayed(played: Map<String, Long>, game: RomGame): Long = played[game.uri] ?: game.modified
}
