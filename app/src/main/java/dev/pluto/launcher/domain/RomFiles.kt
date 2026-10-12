package dev.pluto.launcher.domain

/** Folder indexing rules; archives remain intact for emulators that support them. */
object RomFiles {
    private val annotation = Regex("""\s*\(([^()]*)\)""")
    private val filenameMetadata = Regex(
        """(?:[^()\s]+\.(?:com|net|org)|Rev(?:ision)?\s+[\w.]+|v\d+(?:\.\d+)*|(?:USA|Europe|Japan|World|Australia|Canada|Asia|Korea|China|Brazil|France|Germany|Italy|Spain|UK)(?:\s*[,/]\s*(?:USA|Europe|Japan|World|Australia|Canada|Asia|Korea|China|Brazil|France|Germany|Italy|Spain|UK))*|(?:En|Fr|De|Es|It|Ja|Ko|Zh|Pt|Nl|Sv|No|Da|Fi|Ru)(?:\s*,\s*(?:En|Fr|De|Es|It|Ja|Ko|Zh|Pt|Nl|Sv|No|Da|Fi|Ru))*|PAL|NTSC(?:-[JU])?)""",
        RegexOption.IGNORE_CASE,
    )
    // Also Switch title IDs and versions: "Game [01007EF00011E000][v0].nsp".
    private val dumpTag = Regex("""\s*\[(?:!|[bohtfp]\d*|[0-9a-f]{16}|v\d+)]""", RegexOption.IGNORE_CASE)
    /**
     * Files offered as games. Track/data halves of multi-file dumps (.img/.sub, .mdf, .raw) are
     * left out on purpose: their .ccd/.mds/.gdi/.cue is the entry an emulator opens.
     */
    val extensions = setOf(
        // Discs and playlists (PlayStation 1/2, PSP, Saturn, Sega CD, PC Engine CD, 3DO, Neo Geo CD…)
        "iso", "bin", "cue", "chd", "ccd", "mds", "cdi", "gdi", "pbp", "cso", "zso", "m3u",
        // Nintendo home consoles
        "nes", "fds", "unf", "unif", "sfc", "smc", "fig", "swc", "bs", "n64", "z64", "v64", "ndd",
        "gcm", "gcz", "rvz", "wia", "wbfs", "ciso", "wad", "wud", "wux", "wua",
        "nsp", "xci", "nsz", "xcz",
        // Nintendo handhelds
        "gb", "gbc", "sgb", "gba", "vb", "vboy", "min", "nds", "3ds", "3dsx", "cia", "cci", "cxi",
        // Sega
        "sg", "sc", "sms", "gg", "md", "gen", "smd", "32x",
        // NEC, SNK, Bandai, Watara
        "pce", "sgx", "ngp", "ngc", "ws", "wsc", "sv",
        // Atari
        "a26", "a52", "a78", "lnx", "lyx", "j64", "jag", "atr", "xex", "xfd", "st", "stx", "msa",
        // Other classic consoles
        "col", "int", "vec", "o2", "chf",
        // Home computers: MSX, Commodore 64 / Amiga, ZX Spectrum, Amstrad CPC
        "rom", "mx1", "mx2", "cas", "dsk", "d64", "d71", "d81", "g64", "t64", "prg", "crt", "tap",
        "adf", "adz", "dms", "ipf", "tzx", "z80", "sna", "cdt",
        // Fantasy consoles
        "tic",
        // Archives (arcade sets, zipped cartridges)
        "zip", "7z",
    )

    /** Sheets whose referenced track files are hidden from the shelf. */
    val sheets = setOf("cue", "m3u", "gdi")

    fun supported(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in extensions
    fun title(name: String): String = name.substringBeforeLast('.', name)
        .replace('_', ' ')
        // Keep actual subtitles, editions and disc numbers; hide dump annotations.
        .replace(annotation) { if (filenameMetadata.matches(it.groupValues[1].trim())) "" else it.value }
        .replace(dumpTag, "")
        .replace(Regex("""\s+"""), " ").trim()
    fun referencedFiles(name: String, text: String): List<String> = when (name.substringAfterLast('.').lowercase()) {
        "cue" -> Regex("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))\\s+").findAll(text)
            .map { it.groupValues[1].ifBlank { it.groupValues[2] } }.toList()
        "m3u" -> text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        // "<track> <lba> <type> <sector size> <file> <offset>"; the first line is the track count.
        "gdi" -> Regex("(?m)^[ \\t]*\\d+[ \\t]+\\d+[ \\t]+\\d+[ \\t]+\\d+[ \\t]+(?:\"([^\"]+)\"|(\\S+))").findAll(text)
            .map { it.groupValues[1].ifBlank { it.groupValues[2] } }.toList()
        else -> emptyList()
    }
}
