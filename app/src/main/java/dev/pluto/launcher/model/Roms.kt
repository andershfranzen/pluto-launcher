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

enum class RomLaunchMode { AUTOMATIC, OPEN_WITH, VIEW, DUCKSTATION }

data class RomGame(
    val uri: String,
    val fileName: String,
    val title: String,
    val modified: Long,
    val coverUri: String? = null,
)
