package dev.pluto.launcher.domain

/**
 * The text of a local crash record and the prefilled GitHub issue built from it. Pure and
 * Android-free so the wording, the caps and the encoding can be unit tested.
 *
 * Nothing here sends anything: [issueUrl] opens a browser page the user reviews and submits
 * themselves, which is the only way a record leaves the device.
 */
object CrashReport {
    const val ISSUE_URL = "https://github.com/andershfranzen/pluto-launcher/issues/new"

    /** Per-record cap, so a runaway trace cannot fill the disk. */
    const val MAX_CHARS = 16_000

    /** How much of a record goes into the prefilled issue; keeps the URL reliably openable. */
    const val MAX_URL_BODY = 5_500

    private const val MAX_ERROR_CHARS = 140

    /** The stored record: build facts first, then the trace. */
    fun format(headline: String, timestamp: String, thread: String, trace: String): String =
        "$headline\n$timestamp · $thread\n\n${trace.take(MAX_CHARS)}"

    /** The exception line of a stored record, for a list row and the issue title. */
    fun errorLine(record: String): String =
        record.substringAfter("\n\n", "").lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(MAX_ERROR_CHARS)

    /** Issue title: the exception type is enough to search for, the rest stays in the body. */
    fun issueTitle(versionName: String, record: String): String =
        "Crash in Pluto $versionName: ${errorLine(record).substringBefore(':').take(80)}"

    /** A GitHub "new issue" link with the record prefilled. */
    fun issueUrl(title: String, body: String): String =
        "$ISSUE_URL?title=${encode(title.take(200))}&body=${encode(body.take(MAX_URL_BODY))}"

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
