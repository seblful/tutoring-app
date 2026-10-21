package com.seblful.klas.testutil

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectSeedingRatchetTest {
    private val seedCall = Regex("""[A-Za-z]*[Dd]ao\.(?:insertAll|upsert|insert)\(""")

    @Test
    fun `raw writes are limited to explicit legacy and malformed-state scenarios`() {
        val counts = ProjectFiles.testSources()
            .filterNot { it.parentFile?.name == "testutil" }
            .associate { file ->
                file.invariantSeparatorsPath.substringAfterLast("/src/") to
                    seedCall.findAll(SourceText.code(file.readText())).count()
            }
            .filterValues { it > 0 }
        val prefix = "commonTest/kotlin/com/seblful/klas/data/repository/"
        assertEquals(
            "Seed normal application states through fixtures. Keep each raw-storage exception explicit.",
            mapOf(
                // A malformed attendance row must be readable without inventing a valid booking.
                "${prefix}ClassRepositoryGroupTest.kt" to 1,
                // Pre-existing groups, prices and history without repository audit records.
                "${prefix}RateHistoryTest.kt" to 3,
                // Legacy backups may contain a rate without its enrollment link.
                "${prefix}SubjectRepositoryTest.kt" to 1,
            ),
            counts,
        )
    }
}
