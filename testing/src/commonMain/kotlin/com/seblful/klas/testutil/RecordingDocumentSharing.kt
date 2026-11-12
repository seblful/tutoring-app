package com.seblful.klas.testutil

import com.seblful.klas.storage.DocumentSharing

class RecordingDocumentSharing(var failing: Boolean = false) : DocumentSharing {
    class Shared(
        val filename: String,
        val mimeType: String,
        val subject: String,
        val bytes: ByteArray,
    ) {
        val text get() = bytes.decodeToString()
    }

    val shared = mutableListOf<Shared>()

    override suspend fun share(filename: String, mimeType: String, subject: String, bytes: ByteArray) {
        if (failing) error("Nothing can receive this")
        shared += Shared(filename, mimeType, subject, bytes)
    }
}
