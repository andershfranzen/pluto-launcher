package dev.pluto.launcher.domain

/**
 * Metadata that already sits beside the games: an EmulationStation `gamelist.xml` (written by
 * ES-DE, Batocera, RetroPie and friends) and the media layout those frontends use.
 *
 * Both are parsed as text, in the same spirit as [RomFiles] reading `.cue`/`.m3u` sheets: the
 * files are machine-generated and regular, and this keeps the rules testable off-device.
 */
object RomMetadata {
    /** The gamelist file name written by EmulationStation-compatible front ends. */
    const val GAMELIST = "gamelist.xml"

    /** One `<game>` entry: a path relative to the list's folder, plus optional name and art. */
    data class Entry(val path: String, val name: String?, val image: String?)

    private val gameBlock = Regex("""(?is)<game(?:\s[^>]*)?>(.*?)</game>""")

    /** Every `<game>` entry in [xml]; entries without a usable `<path>` are dropped. */
    fun gamelist(xml: String): List<Entry> = gameBlock.findAll(xml).mapNotNull { match ->
        val block = match.groupValues[1]
        val path = tag(block, "path") ?: return@mapNotNull null
        Entry(path, tag(block, "name"), tag(block, "image") ?: tag(block, "thumbnail"))
    }.toList()

    private fun tag(block: String, name: String): String? =
        Regex("""(?is)<$name(?:\s[^>]*)?>(.*?)</$name>""").find(block)?.groupValues?.get(1)
            ?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }

    /** Resolves a referenced path (often "./name.png") inside [baseDir] into a tree-relative path. */
    fun resolve(baseDir: String, reference: String): String =
        normalize(if (baseDir.isEmpty()) reference else "$baseDir/$reference")

    /**
     * A comparable form of a tree-relative path: backslashes become slashes, `.`/`..` segments
     * collapse, and the result is lower case so lookups ignore the case of the folder names.
     */
    fun normalize(path: String): String {
        val segments = ArrayList<String>()
        path.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments += segment
            }
        }
        return segments.joinToString("/").lowercase()
    }

    /** Decodes the XML entities front ends actually emit; unknown entities are left untouched. */
    private fun unescape(value: String): String =
        Regex("""&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos);""").replace(value) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16)
                    else entity.drop(1).toIntOrNull()
                    code?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: match.value
                }
            }
        }
}
