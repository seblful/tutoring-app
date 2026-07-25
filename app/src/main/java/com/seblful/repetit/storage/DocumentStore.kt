package com.seblful.repetit.storage

/**
 * A document the user picked through the system file picker, as an opaque handle.
 *
 * Deliberately a string rather than an `android.net.Uri`: the handle is created at the one place a
 * `Uri` exists (the SAF launcher callback in a composable) and travels from there through view
 * models and [DocumentStore] as plain data. That is what keeps the export/import paths reachable
 * from a JVM test — a `Uri` cannot even be constructed in one. Round-tripping through
 * `Uri.toString()`/`Uri.parse` is how SAF handles are persisted anyway.
 */
@JvmInline
value class DocumentRef(val value: String)

/**
 * Reads and writes the whole text of a user-picked document.
 *
 * A port, so the two things the app exports — the backup snapshot and the crash log — share one
 * implementation of "open the stream, write it all, close it" instead of restating it each. Both
 * methods **throw** when the document can't be opened or read; deciding what a failure means to the
 * user belongs to the caller, which is the only part that actually differs between them.
 *
 * Implementations move the I/O off the main thread themselves, so callers never have to remember to.
 */
interface DocumentStore {

    /** The document's entire contents as text. Throws if it cannot be opened or read. */
    suspend fun read(ref: DocumentRef): String

    /** Replaces the document's contents with [text]. Throws if it cannot be opened or written. */
    suspend fun write(ref: DocumentRef, text: String)
}
