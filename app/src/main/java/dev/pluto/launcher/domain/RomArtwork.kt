package dev.pluto.launcher.domain

/**
 * Finds box art for a game among the images already in the same folder or tree.
 *
 * Beyond the sibling image (`Spyro.cue` + `Spyro.png`) that Pluto has always used, this
 * understands the media layouts the established front ends write:
 * ES-DE/Batocera (`boxart/`, `covers/`, `media/`), RetroArch's thumbnail sets
 * (`Named_Boxarts/`, `Named_Snaps/`), and the `-thumb`/`-boxart` suffixed variants.
 */
object RomArtwork {
    /** Extensions treated as artwork. */
    val extensions = setOf("png", "jpg", "jpeg", "webp")

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in extensions

    /** Folder names that conventionally hold art, best first. */
    private val folderRank = listOf("named_boxarts", "boxart", "covers", "named_snaps", "named_titles", "images", "media", "thumbnails")
    private const val OTHER_FOLDER_RANK = 50
    private const val TITLE_MATCH_PENALTY = 100

    private fun folderScore(directory: String): Int {
        val leaf = directory.substringAfterLast('/')
        val index = folderRank.indexOf(leaf)
        return if (index >= 0) index else OTHER_FOLDER_RANK
    }

    /** Suffixes front ends append to the art file name; index the shortened base as well. */
    private val suffixes = listOf("-thumb", "_thumb", "-thumbnail", "-boxart", "_boxart", "-box", "-art")

    /** The artwork files of one tree, indexed for lookup by [art]. */
    class Index internal constructor(
        private val byBase: Map<String, List<String>>,
        private val byFolderAndBase: Map<String, String>,
        private val byTitle: Map<String, List<String>>,
    ) {
        /** The best image for [relativePath] (a ROM file) or null. */
        fun art(relativePath: String, fileName: String): String? {
            val directory = relativePath.substringBeforeLast('/', "")
            val base = fileName.substringBeforeLast('.', fileName)
            val key = base.lowercase()
            // 1. The image beside the game, exactly as before: same folder, same name.
            byFolderAndBase["$directory/$key"]?.let { return it }
            // 2. Any folder, ranked so box art folders win over generic ones.
            byBase[key]?.minByOrNull { rank(it, directory) }?.let { return it }
            // 3. Artwork named after the cleaned-up title, e.g. "Spyro.png" for "Spyro (Europe).cue".
            val title = RomFiles.title(base).lowercase()
            if (title == key || title.isEmpty()) return null
            return byTitle[title]?.minByOrNull { rank(it, directory) + TITLE_MATCH_PENALTY }
        }

        private fun rank(path: String, directory: String): Int {
            val pathDirectory = path.substringBeforeLast('/', "")
            val sameFolder = if (pathDirectory == directory) 0 else 1
            return sameFolder * 1000 + folderScore(pathDirectory)
        }
    }

    /** Indexes [paths] (tree-relative image paths) for artwork lookup. */
    fun index(paths: List<String>): Index {
        val byBase = LinkedHashMap<String, MutableList<String>>()
        val byFolderAndBase = HashMap<String, String>()
        val byTitle = LinkedHashMap<String, MutableList<String>>()
        for (path in paths) {
            val name = path.substringAfterLast('/')
            if (!isImage(name)) continue
            val directory = path.substringBeforeLast('/', "")
            val rawBase = name.substringBeforeLast('.', name).lowercase()
            byFolderAndBase.putIfAbsent("$directory/$rawBase", path)
            val bases = buildList {
                add(rawBase)
                suffixes.firstOrNull { rawBase.endsWith(it) && rawBase.length > it.length }
                    ?.let { add(rawBase.dropLast(it.length)) }
            }
            bases.forEach { byBase.getOrPut(it) { ArrayList() }.add(path) }
            RomFiles.title(name.substringBeforeLast('.', name)).lowercase()
                .takeIf { it.isNotEmpty() }
                ?.let { byTitle.getOrPut(it) { ArrayList() }.add(path) }
        }
        return Index(byBase, byFolderAndBase, byTitle)
    }
}
