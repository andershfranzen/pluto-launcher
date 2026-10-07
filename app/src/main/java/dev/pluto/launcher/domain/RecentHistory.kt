package dev.pluto.launcher.domain

object RecentHistory {
    const val MAX_ENTRIES = 12

    /** Moves [key] to the front, keeps entries distinct, truncates to [MAX_ENTRIES]. */
    fun <K> record(current: List<K>, key: K): List<K> = TODO()
}
