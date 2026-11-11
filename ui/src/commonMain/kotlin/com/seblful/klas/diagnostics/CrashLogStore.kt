package com.seblful.klas.diagnostics

/**
 * Where the crash log is kept. A seam under [CrashLogger] rather than beside it: everything worth
 * getting wrong about crash logging — the format, the size cap, chaining to the platform's previous
 * handler, never letting the logging itself mask the crash — is in [CrashLogger], and this is only
 * the bytes.
 */
interface CrashLogStore {

    /** The whole log, or "" when nothing has been recorded. */
    fun read(): String

    /** Replaces the log with [text]. */
    fun write(text: String)

    /** Removes the log entirely. */
    fun delete()

    /** Whether anything has been recorded — cheaper than [read] when only the answer is wanted. */
    fun isEmpty(): Boolean
}

/**
 * The facts about this build and device that go in a crash header. A plain value, resolved once at
 * the composition root, so [CrashLogger] needs no `Context` and a test can state them outright.
 */
data class DeviceInfo(
    val appVersion: String,
    val androidSdk: Int,
    val device: String,
)
