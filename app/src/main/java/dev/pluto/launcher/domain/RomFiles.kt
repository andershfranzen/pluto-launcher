package dev.pluto.launcher.domain

/** Folder indexing rules; archives remain intact for emulators that support them. */
object RomFiles {
    private val annotation = Regex("""\s*\(([^()]*)\)""")
    private val filenameMetadata = Regex(
        """(?:[^()\s]+\.(?:com|net|org)|Rev(?:ision)?\s+[\w.]+|v\d+(?:\.\d+)*|(?:USA|Europe|Japan|World|Australia|Canada|Asia|Korea|China|Brazil|France|Germany|Italy|Spain|UK)(?:\s*[,/]\s*(?:USA|Europe|Japan|World|Australia|Canada|Asia|Korea|China|Brazil|France|Germany|Italy|Spain|UK))*|(?:En|Fr|De|Es|It|Ja|Ko|Zh|Pt|Nl|Sv|No|Da|Fi|Ru)(?:\s*,\s*(?:En|Fr|De|Es|It|Ja|Ko|Zh|Pt|Nl|Sv|No|Da|Fi|Ru))*|PAL|NTSC(?:-[JU])?)""",
        RegexOption.IGNORE_CASE,
    )
    private val dumpTag = Regex("""\s*\[(?:!|[bohtfp]\d*)]""", RegexOption.IGNORE_CASE)
    val extensions = setOf("iso", "bin", "cue", "chd", "pbp", "m3u", "cso", "gcm", "rvz", "wbfs", "nes", "fds", "sfc", "smc", "gb", "gbc", "gba", "nds", "3ds", "n64", "z64", "v64", "md", "gen", "sms", "gg", "pce", "a26", "a78", "rom", "zip", "7z")
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
        else -> emptyList()
    }
}
