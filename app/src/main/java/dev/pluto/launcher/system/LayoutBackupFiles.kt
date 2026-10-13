package dev.pluto.launcher.system

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.IOException

/**
 * Reads and writes a layout backup through a document the user picked in the system file
 * picker. Going through the picker means Pluto needs no storage permission, the file lands
 * where the user chose — Downloads, a cloud provider, a USB stick — and comes back the same way.
 */
class LayoutBackupFiles(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    /** Writes [text] to [uri], replacing whatever was there. */
    fun write(uri: Uri, text: String) {
        // "wt" truncates: overwriting a file must not leave the old tail behind a shorter write.
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: throw IOException("Could not open the file for writing")
    }

    /**
     * Reads [uri] as UTF-8 text. Anything past [MAX_BYTES] is refused rather than read: a file
     * that size is not a layout backup, and the user will want to know they picked wrong.
     */
    fun read(uri: Uri): String {
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(MAX_BYTES)
            var read = 0
            while (read < buffer.size) {
                val count = stream.read(buffer, read, buffer.size - read)
                if (count < 0) break
                read += count
            }
            // A byte still waiting means the file is bigger than any layout.
            if (read == buffer.size && stream.read() >= 0) throw IOException("That file is too large to be a layout backup")
            buffer.copyOf(read)
        } ?: throw IOException("Could not open the file")
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
        /** Past this, the file is not one of ours: a whole layout is a few kilobytes at most. */
        const val MAX_BYTES = 1_000_000

        /** File name offered in the picker. */
        const val FILE_NAME = "pluto-layout.txt"
    }
}
