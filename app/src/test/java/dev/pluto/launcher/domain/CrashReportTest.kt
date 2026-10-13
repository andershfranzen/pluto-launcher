package dev.pluto.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    private val record = CrashReport.format(
        headline = "Pluto 0.1.7 (8) · OnePlus CPH2 · Android 16 (API 36)",
        timestamp = "2026-10-12 21:04",
        thread = "main",
        trace = "java.lang.NullPointerException: Boom\n\tat dev.pluto.launcher.Foo.bar(Foo.kt:1)",
    )

    @Test fun recordKeepsFactsThenTheTrace() {
        assertTrue(record.startsWith("Pluto 0.1.7 (8)"))
        assertTrue(record.contains("2026-10-12 21:04 · main"))
        assertTrue(record.endsWith("at dev.pluto.launcher.Foo.bar(Foo.kt:1)"))
    }

    @Test fun errorLineReadsTheExceptionNotTheHeadline() {
        assertEquals("java.lang.NullPointerException: Boom", CrashReport.errorLine(record))
    }

    @Test fun titleNamesTheExceptionType() {
        assertEquals("Crash in Pluto 0.1.7: java.lang.NullPointerException", CrashReport.issueTitle("0.1.7", record))
    }

    @Test fun issueUrlEncodesAndCapsTheBody() {
        val url = CrashReport.issueUrl("Crash in Pluto 0.1.7: Boom", record)
        assertTrue(url.startsWith("${CrashReport.ISSUE_URL}?title=Crash%20in%20Pluto%200.1.7%3A%20Boom&body="))
        assertTrue("newlines are encoded", url.contains("%0A"))
        assertTrue("no raw spaces", !url.contains(" "))

        val huge = CrashReport.issueUrl("t", "x".repeat(50_000))
        assertTrue(huge.length < 50_000)
        assertTrue(huge.contains("x".repeat(CrashReport.MAX_URL_BODY - 10)))
    }

    @Test fun aRecordWithNoTraceStillReportsSomething() {
        assertEquals("", CrashReport.errorLine("Pluto 0.1.7 (8) · Android 16 (API 36)\n2026-10-12 21:04 · main"))
    }
}
