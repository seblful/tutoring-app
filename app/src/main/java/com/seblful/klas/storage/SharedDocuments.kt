package com.seblful.klas.storage

import com.seblful.klas.domain.time.WallClock
import kotlinx.datetime.TimeZone
import java.io.File
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

internal class SharedDocuments(
    private val directory: File,
    private val clock: WallClock = WallClock.system(TimeZone.UTC),
) {
    fun write(filename: String, text: String): File {
        require(filename == File(filename).name) { "A share filename may not contain a path" }
        directory.mkdirs()
        val expiredBefore = clock.now().toEpochMilliseconds() - RETENTION.inWholeMilliseconds
        directory.listFiles()?.filter { it.lastModified() < expiredBefore }?.forEach { it.deleteRecursively() }
        // URI grants can outlive the chooser. Never reuse a path while an earlier recipient may read it.
        val share = File(directory, UUID.randomUUID().toString()).apply { check(mkdir()) }
        return File(share, filename).apply { writeText(text) }
    }

    private companion object {
        val RETENTION: Duration = 7.days
    }
}
