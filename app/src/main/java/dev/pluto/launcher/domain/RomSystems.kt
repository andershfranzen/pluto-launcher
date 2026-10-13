package dev.pluto.launcher.domain

/**
 * Which console or computer a ROM belongs to. Read from the folder it sits in when a folder
 * is named after a system (the ES-DE / Batocera layout, `roms/snes/…`), and from the file
 * extension otherwise. A shelf that holds more than one system — one `/ROMs` tree — can then
 * be browsed a system at a time instead of all mixed together.
 */
object RomSystems {
    /** Shown when nothing identifies the system better. */
    const val OTHER = "Other"

    /** Disc formats shared by several systems; the folder, when it names one, decides. */
    const val DISC = "Disc image"

    /** Folder names front ends use, punctuation and case stripped. */
    private val folders: Map<String, String> = buildMap {
        fun add(label: String, vararg aliases: String) = aliases.forEach { putIfAbsent(it, label) }
        add("NES", "nes", "famicom", "nintendoentertainmentsystem", "fds")
        add("SNES", "snes", "supernintendo", "superfamicom", "sfc")
        add("Nintendo 64", "n64", "nintendo64")
        add("GameCube", "gc", "gamecube", "ngc")
        add("Wii", "wii")
        add("Nintendo Switch", "switch", "nsw", "nintendoswitch")
        add("Game Boy", "gb", "gameboy")
        add("Game Boy Color", "gbc", "gameboycolor")
        add("Game Boy Advance", "gba", "gameboyadvance")
        add("Virtual Boy", "vb", "virtualboy")
        add("Nintendo DS", "nds", "nintendods", "ds")
        add("Nintendo 3DS", "3ds", "n3ds", "nintendo3ds")
        add("Master System", "mastersystem", "sms", "sg1000")
        add("Game Gear", "gamegear", "gg")
        add("Mega Drive", "megadrive", "genesis", "md", "segamegadrive")
        add("Sega 32X", "32x", "sega32x")
        add("Sega Saturn", "saturn", "segasaturn")
        add("Dreamcast", "dreamcast", "dc")
        add("PlayStation", "psx", "ps1", "playstation", "playstation1", "sonyplaystation")
        add("PlayStation 2", "ps2", "playstation2", "sonyplaystation2")
        add("PlayStation Portable", "psp", "playstationportable")
        add("PC Engine", "pcengine", "turbografx16", "turbografx", "pce", "tg16")
        add("Neo Geo", "neogeo", "neogeocd")
        add("Neo Geo Pocket", "neogeopocket", "ngp", "ngpc")
        add("WonderSwan", "wonderswan", "ws", "wsc")
        add("Arcade", "arcade", "mame", "fbneo", "fba", "cps1", "cps2", "cps3", "naomi")
        add("Atari 2600", "atari2600", "a2600", "atari")
        add("Commodore 64", "c64", "commodore64")
        add("Amiga", "amiga", "amigacd32")
        add("MSX", "msx", "msx2")
        add("ZX Spectrum", "zxspectrum", "spectrum", "sinclairzxspectrum")
        add("Amstrad CPC", "amstradcpc", "cpc")
    }

    /** Extensions that name one system on their own. */
    private val extensions: Map<String, String> = buildMap {
        fun add(label: String, vararg list: String) = list.forEach { putIfAbsent(it, label) }
        add("NES", "nes", "fds", "unf", "unif")
        add("SNES", "sfc", "smc", "fig", "swc", "bs")
        add("Nintendo 64", "n64", "z64", "v64", "ndd")
        add("GameCube", "gcm")
        add("Wii", "gcz", "rvz", "wia", "wbfs", "ciso", "wad", "wud", "wux", "wua")
        add("Nintendo Switch", "nsp", "xci", "nsz", "xcz")
        add("Game Boy", "gb", "sgb")
        add("Game Boy Color", "gbc")
        add("Game Boy Advance", "gba")
        add("Virtual Boy", "vb", "vboy")
        add("Nintendo DS", "nds")
        add("Nintendo 3DS", "3ds", "3dsx", "cia", "cci", "cxi")
        add("Master System", "sg", "sc", "sms")
        add("Game Gear", "gg")
        add("Mega Drive", "md", "gen", "smd", "32x")
        add("PlayStation Portable", "pbp", "cso", "zso")
        add("Dreamcast", "gdi")
        add("PC Engine", "pce", "sgx")
        add("Neo Geo Pocket", "ngp", "ngc")
        add("WonderSwan", "ws", "wsc", "sv")
        add("Atari 2600", "a26", "a52", "a78", "lnx", "lyx", "j64", "jag")
        add("Atari ST", "st", "stx", "msa")
        add("Commodore 64", "d64", "d71", "d81", "g64", "t64", "prg", "crt")
        add("Amiga", "adf", "adz", "dms", "ipf")
        add("MSX", "mx1", "mx2", "cas")
        add("ZX Spectrum", "tzx", "z80", "sna", "tap", "cdt")
        add(DISC, "iso", "bin", "cue", "chd", "ccd", "mds", "cdi", "m3u")
    }

    private fun normalize(segment: String): String = segment.lowercase().filter(Char::isLetterOrDigit)

    /**
     * The system of a file, from its tree-relative path (`roms/snes/usa/Game.sfc`), or just
     * from a file name ([of]). Folders are checked deepest first, so a region folder such as
     * `usa` never hides the system folder above it.
     */
    fun ofPath(relativePath: String): String {
        val folder = relativePath.substringBeforeLast('/', "")
        if (folder.isNotEmpty()) {
            folder.split('/').asReversed().forEach { segment ->
                folders[normalize(segment)]?.let { return it }
            }
        }
        return of(relativePath)
    }

    /** The system an extension names on its own, else [OTHER]. */
    fun of(fileName: String): String =
        extensions[fileName.substringAfterLast('.', "").lowercase()] ?: OTHER
}
