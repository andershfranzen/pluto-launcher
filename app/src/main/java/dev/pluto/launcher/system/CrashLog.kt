package dev.pluto.launcher.system

import android.content.Context
import android.os.Build
import dev.pluto.launcher.BuildConfig
import dev.pluto.launcher.R
import dev.pluto.launcher.ui.UiText
import dev.pluto.launcher.domain.CrashReport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small crash log kept only on this device. Pluto is normally the Home app, so a crash in
 * it takes the user's home screen down with it; the trace is worth keeping. Records hold a
 * stack trace and build information only — never what was on screen, and never notification
 * or search content. Nothing is uploaded: Settings → General lists the records and prefills an
 * issue that the user reviews in the browser before anything leaves the device.
 *
 * The handler writes the record synchronously and then hands the crash on to Android, so the
 * process still dies and Home restarts normally: logging must never keep a broken Pluto alive.
 */
object CrashLog {
    private const val DIRECTORY = "crashes"
    private const val MAX_RECORDS = 5
    private const val FILE_PREFIX = "crash-"
    // A new formatter per use: SimpleDateFormat is not thread-safe, and two threads can crash at once.
    private fun fileFormat() = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US)

    /** Installs the handler. Call once, as early as possible in [android.app.Application.onCreate]. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Writes one record and trims the oldest. Never throws: a failed log must not hide the crash. */
    fun write(context: Context, threadName: String, error: Throwable) {
        runCatching {
            val directory = directory(context).apply { mkdirs() }
            if (!directory.isDirectory) return
            File(directory, "$FILE_PREFIX${fileFormat().format(Date())}.txt").writeText(record(threadName, error))
            records(context).drop(MAX_RECORDS).forEach { it.delete() }
        }
    }

    /** What one record contains, with the facts a maintainer needs to place it. */
    internal fun record(threadName: String, error: Throwable): String = CrashReport.format(
        headline = "Pluto ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
        thread = threadName,
        trace = error.stackTraceToString(),
    )

    /** Records newest first. */
    fun records(context: Context): List<File> =
        directory(context).listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) }
            .sortedByDescending { it.name }

    fun text(context: Context, file: File): String =
        runCatching { file.readText().take(CrashReport.MAX_CHARS) }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { directory(context).listFiles().orEmpty().forEach { it.delete() } }
    }

    /** "12 Oct, 21:04" for a record file, falling back to its name. */
    fun label(file: File): String {
        val stamp = file.name.removePrefix(FILE_PREFIX).removeSuffix(".txt")
        val parsed = runCatching { fileFormat().parse(stamp) }.getOrNull() ?: return stamp
        return SimpleDateFormat(UiText.get(R.string.settings_crash_label_date_pattern), Locale.getDefault()).format(parsed)
    }

    private fun directory(context: Context) = File(context.filesDir, DIRECTORY)
}
