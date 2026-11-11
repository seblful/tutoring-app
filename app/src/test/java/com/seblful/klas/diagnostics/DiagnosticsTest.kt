package com.seblful.klas.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class DiagnosticsTest {

    @Test
    fun `entry carries time, versions, device and trace`() {
        val entry = Diagnostics.formatEntry(
            time = Instant.parse("2026-06-20T10:15:30Z"),
            appVersion = "1.0",
            system = "Android SDK 34",
            device = "Google Pixel 8",
            stackTrace = "java.lang.IllegalStateException: boom\n\tat Foo.bar(Foo.kt:1)",
        )
        assertTrue(entry.startsWith("${Diagnostics.ENTRY_MARKER}2026-06-20T10:15:30Z"))
        assertTrue(entry.contains("app 1.0"))
        assertTrue(entry.contains("Android SDK 34"))
        assertTrue(entry.contains("Google Pixel 8"))
        assertTrue(entry.contains("IllegalStateException: boom"))
        assertTrue("entries are blank-line separated", entry.endsWith("\n\n"))
    }

    @Test
    fun `appendBounded keeps everything while under the cap`() {
        val combined = Diagnostics.appendBounded(existing = "old\n", entry = "new\n", max = 100)
        assertEquals("old\nnew\n", combined)
    }

    @Test
    fun `appendBounded drops oldest whole entries past the cap`() {
        val a = Diagnostics.formatEntry(Instant.parse("2026-01-01T00:00:00Z"), "1.0", "Android SDK 30", "A", "trace-A")
        val b = Diagnostics.formatEntry(Instant.parse("2026-02-01T00:00:00Z"), "1.0", "Android SDK 30", "B", "trace-B")
        val c = Diagnostics.formatEntry(Instant.parse("2026-03-01T00:00:00Z"), "1.0", "Android SDK 30", "C", "trace-C")

        val log = Diagnostics.appendBounded(existing = a + b, entry = c, max = b.length + c.length + 10)

        assertTrue("oldest entry dropped", !log.contains("trace-A"))
        assertTrue(log.contains("trace-B"))
        assertTrue(log.contains("trace-C"))
        assertTrue("trim snaps to an entry boundary", log.startsWith(Diagnostics.ENTRY_MARKER))
    }

    @Test
    fun `appendBounded keeps a single oversized entry whole rather than truncating`() {
        val huge = Diagnostics.formatEntry(Clock.System.now(), "1.0", "Android SDK 30", "X", "x".repeat(500))
        val log = Diagnostics.appendBounded(existing = "stale\n", entry = huge, max = 100)
        assertEquals(huge, log)
    }
}
