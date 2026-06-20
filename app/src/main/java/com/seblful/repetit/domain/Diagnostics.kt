package com.seblful.repetit.domain

import java.time.Instant

/**
 * Pure formatting + size-bounding for the on-device crash log. No Android deps, so it's
 * JVM-unit-tested. The Android-bound file I/O and the uncaught-exception handler that feed it live
 * in `diagnostics/CrashLogger`.
 */
object Diagnostics {

    /** Marks the start of one crash entry; also the boundary [appendBounded] trims on. */
    const val ENTRY_MARKER = "===== "

    /** Hard cap on the saved log so a crash loop can't grow the file without bound. */
    const val MAX_LOG_CHARS = 64_000

    /** One crash entry: a header line (time, app + Android version, device) then the stack trace. */
    fun formatEntry(
        time: Instant,
        appVersion: String,
        androidSdk: Int,
        device: String,
        stackTrace: String,
    ): String = buildString {
        append(ENTRY_MARKER).append(time).append(" =====\n")
        append("app ").append(appVersion)
            .append("  ·  Android SDK ").append(androidSdk)
            .append("  ·  ").append(device).append('\n')
        append(stackTrace.trimEnd()).append("\n\n")
    }

    /**
     * Appends [entry] to [existing] and trims the result from the front to [max] chars, keeping the
     * **most recent** crashes. Trimming snaps forward to the next entry boundary so a report is never
     * cut mid-stack-trace; if a single entry alone exceeds [max] it's kept whole (truncation would
     * lose the very crash we're recording).
     */
    fun appendBounded(existing: String, entry: String, max: Int = MAX_LOG_CHARS): String {
        val combined = existing + entry
        if (combined.length <= max) return combined
        val from = combined.length - max
        val boundary = combined.indexOf(ENTRY_MARKER, startIndex = from)
        return if (boundary >= 0) combined.substring(boundary) else entry
    }
}
