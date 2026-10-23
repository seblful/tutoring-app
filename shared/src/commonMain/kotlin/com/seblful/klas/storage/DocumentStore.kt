package com.seblful.klas.storage

import kotlin.jvm.JvmInline

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

/** Reads and writes the whole of a user-picked document. */
interface DocumentStore {

    /** The document's entire contents as text. Throws if it cannot be opened or read. */
    suspend fun read(ref: DocumentRef): String

    /** Replaces the document's contents with [text]. Throws if it cannot be opened or written. */
    suspend fun write(ref: DocumentRef, text: String)

    /** Replaces the document's contents with [bytes] — a file that is not text, a PDF. */
    suspend fun write(ref: DocumentRef, bytes: ByteArray)
}
