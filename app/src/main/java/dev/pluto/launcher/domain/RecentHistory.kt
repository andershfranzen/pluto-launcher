package dev.pluto.launcher.domain

object RecentHistory {
    const val MAX_ENTRIES = 12

    /** Moves [key] to the front, keeps entries distinct, truncates to [MAX_ENTRIES]. */
    fun <K> record(current: List<K>, key: K): List<K> =
        (sequenceOf(key) + current.asSequence())
            .distinct() // first occurrence wins, so the older copy of [key] disappears
            .take(MAX_ENTRIES)
            .toList()
}
