package dev.pluto.launcher.domain

/**
 * Where games usually already are. Pluto cannot look inside a folder before the user grants
 * access, so instead of guessing it offers the folders the installed emulators and front ends
 * use by default, and opens the folder picker already pointing at them.
 */
object RomDiscovery {
    /** A folder worth offering: the app that uses it (when known) and its name on shared storage. */
    data class Suggestion(val app: String?, val packageName: String?, val folder: String)

    private val known = listOf(
        Suggestion("RetroArch", "com.retroarch", "RetroArch"),
        Suggestion("RetroArch", "com.retroarch.aarch64", "RetroArch"),
        Suggestion("RetroArch", "com.retroarch.ra32", "RetroArch"),
        Suggestion("Dolphin", "org.dolphinemu.dolphinemu", "Dolphin"),
        Suggestion("PPSSPP", "org.ppsspp.ppsspp", "PSP"),
        Suggestion("PPSSPP Gold", "org.ppsspp.ppssppgold", "PSP"),
        Suggestion("DuckStation", "com.github.stenzek.duckstation", "DuckStation"),
        Suggestion("Lemuroid", "com.swordfish.lemuroid", "Lemuroid"),
        Suggestion("AetherSX2", "xyz.aethersx2.android", "AetherSX2"),
        Suggestion("NetherSX2", "com.nethersx2.android", "NetherSX2"),
        Suggestion("M64Plus FZ", "org.mupen64plusae.v3.fz", "Mupen64Plus"),
        Suggestion("Flycast", "com.flycast.emulator", "Flycast"),
    )

    /**
     * Folders many people already keep games in, offered whether or not an emulator is known.
     * `ROMs` is also ES-DE's default. Download is left out: Android 11+ does not let an app
     * be granted the Download folder itself, so the suggestion could never be used.
     */
    private val common = listOf(
        Suggestion(null, null, "ROMs"),
        Suggestion(null, null, "Games"),
    )

    /**
     * Suggestions for [installedPackages], most specific first: one per folder, with the
     * emulators the user actually has first, then the common folder names.
     */
    fun suggestions(installedPackages: Set<String>): List<Suggestion> {
        val byFolder = LinkedHashMap<String, Suggestion>()
        known.filter { it.packageName in installedPackages }.forEach { byFolder.putIfAbsent(it.folder, it) }
        common.forEach { byFolder.putIfAbsent(it.folder, it) }
        return byFolder.values.toList()
    }
}
