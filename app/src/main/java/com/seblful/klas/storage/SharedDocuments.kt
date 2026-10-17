package com.seblful.klas.storage

import java.io.File
import java.time.Clock
import java.time.Duration
import java.util.UUID

/** Files lent through FileProvider; kept separate so their lifetime can be tested without a provider. */
internal class SharedDocuments(private val directory: File, private val clock: Clock = Clock.systemUTC()) {
    fun write(filename: String, text: String): File {
        require(filename == File(filename).name) { "A share filename may not contain a path" }
        directory.mkdirs()
        val expiredBefore = clock.millis() - RETENTION.toMillis()
        directory.listFiles()?.filter { it.lastModified() < expiredBefore }?.forEach { it.deleteRecursively() }
        // URI grants can outlive the chooser. Never reuse a path while an earlier recipient may read it.
        val share = File(directory, UUID.randomUUID().toString()).apply { check(mkdir()) }
        return File(share, filename).apply { writeText(text) }
    }

    private companion object {
        val RETENTION: Duration = Duration.ofDays(7)
    }
}
