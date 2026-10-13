package dev.pluto.launcher.model

/** A console-only shelf. Documents stay in the user's selected folder. */
data class RomLibrary(
    val id: Long,
    val name: String,
    val treeUri: String,
    val emulatorPackage: String?,
    val launchMode: RomLaunchMode = RomLaunchMode.AUTOMATIC,
    val games: List<RomGame> = emptyList(),
)

/**
 * How a tab opens its games. AUTOMATIC picks the adapter Pluto has for the chosen emulator
 * (see `RomLauncher.automaticMode`) and otherwise uses Android's Open file handling.
 */
enum class RomLaunchMode {
    AUTOMATIC,
    OPEN_WITH,
    VIEW,
    DUCKSTATION,
    // Unverified direct launches from a pre-release build; kept so stored tabs still decode,
    // and opened like VIEW (see RomLauncher.intent).
    RETROARCH,
    DOLPHIN,
    PPSSPP,
}

data class RomGame(
    val uri: String,
    val fileName: String,
    val title: String,
    val modified: Long,
    val coverUri: String? = null,
)
