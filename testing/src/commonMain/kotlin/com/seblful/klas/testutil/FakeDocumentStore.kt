package com.seblful.klas.testutil

import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore

class FakeDocumentStore(var failing: Boolean = false) : DocumentStore {
    val docs = mutableMapOf<String, String>()

    /** What was written as bytes — a PDF — by document. */
    val files = mutableMapOf<String, ByteArray>()

    override suspend fun read(ref: DocumentRef): String {
        if (failing) error("Cannot open input stream")
        return docs[ref.value] ?: error("No such document: ${ref.value}")
    }

    override suspend fun write(ref: DocumentRef, text: String) {
        if (failing) error("Cannot open output stream")
        docs[ref.value] = text
    }

    override suspend fun write(ref: DocumentRef, bytes: ByteArray) {
        if (failing) error("Cannot open output stream")
        files[ref.value] = bytes
    }
}
