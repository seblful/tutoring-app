package com.seblful.klas.data.local

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An enum constant's **name is a persisted value**, and nothing else in the build says so.
 *
 * `classes.status` and the three `mode` columns store `ClassStatus`/`ClassMode` names as text, two DAO
 * queries match on `'SCHEDULED'` as a SQL literal, and three entity columns default to `"IN_PERSON"`.
 * So renaming a constant — an ordinary refactor that every IDE offers and every reference in Kotlin
 * follows — is really a schema change, and one that fails **silently in different ways**:
 *
 * - existing rows keep the old text, and reading them stops working (`ClassStatus.parse` returns null,
 *   see `data/Mappers.kt`);
 * - the two SQL literals keep the old text and simply stop matching, so nothing auto-completes and no
 *   reminder is ever planned — with no error anywhere, because "no rows due" is a perfectly ordinary
 *   answer.
 *
 * That second one is the reason this test exists rather than being left to code review. It is an
 * acknowledgement list, like `BackupSchemaCoverageTest` and `MappersTest`: renaming a constant fails
 * here, in front of whoever is doing it, where the message can say a migration is owed.
 */
class StoredEnumNamesTest {

    /**
     * The names in the database today. **Changing this list is a migration**, not a refactor: every
     * existing row holds the old text, so the migration has to rewrite them (and the backup format's
     * copy of them) before this list may be updated.
     */
    private val storedStatuses = setOf("SCHEDULED", "COMPLETED", "CANCELLED")
    private val storedModes = setOf("IN_PERSON", "REMOTE")
    private val storedCurrencies = setOf("BYN", "RUB")

    @Test
    fun `the stored status names are the ones the database holds`() {
        assertEquals(
            "A ClassStatus constant was renamed, added or removed. Every `classes.status` row holds " +
                "the old text — write a migration that rewrites them, check the two DAO queries " +
                "matching on a status literal, then update this list.",
            storedStatuses,
            ClassStatus.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun `the stored mode names are the ones the database holds`() {
        assertEquals(
            "A ClassMode constant was renamed, added or removed. Three columns store this text and " +
                "default to one of these values — write a migration, then update this list.",
            storedModes,
            ClassMode.entries.map { it.name }.toSet(),
        )
    }

    /**
     * The one stored word that is *not* money-bearing: an unrecognized value falls back to BYN
     * (`Currency.parse`), so a rename loses only a label — but it still loses one silently, on every
     * device that stored the old text, which is exactly the drift this list exists to interrupt.
     */
    @Test
    fun `the stored currency names are the ones the settings row holds`() {
        assertEquals(
            "A Currency constant was renamed, added or removed. The `settings.currency` column " +
                "stores these names — devices holding the old text will silently fall back to BYN. " +
                "Migrate the column (or accept the fallback deliberately), then update this list.",
            storedCurrencies,
            com.seblful.klas.domain.model.Currency.entries.map { it.name }.toSet(),
        )
    }

    /**
     * The entity defaults, read off the entities themselves rather than from the source text — a
     * column defaulting to a name no enum has would write unreadable rows from the very first insert.
     */
    @Test
    fun `every entity column defaulting to an enum name defaults to a real one`() {
        assertEquals(ClassMode.IN_PERSON, ClassMode.parse(StudentEntity(name = "Иван").defaultMode))
        assertEquals(ClassMode.IN_PERSON, ClassMode.parse(GroupEntity(name = "11 «А»").defaultMode))
        assertEquals(
            ClassMode.IN_PERSON,
            ClassMode.parse(
                ClassEntity(
                    studentId = 1, subjectId = 1, startMillis = 0, durationMinutes = 60,
                    status = ClassStatus.SCHEDULED.name,
                ).mode,
            ),
        )
    }

    /**
     * The SQL literals. `@Query` is not retained at runtime, so the source is read — the same trick
     * `BackupSchemaCoverageTest` uses to derive its expectations from a real artifact instead of
     * restating them.
     *
     * A rename leaves these behind, and the query then matches nothing rather than failing: no lesson
     * ever auto-completes and no reminder is ever planned, which no other test in the suite would
     * notice because both would simply be quiet.
     */
    @Test
    fun `every status literal in the DAO SQL names a real status`() {
        val sql = ProjectFiles.sourceFile("data/local/Daos.kt").readText()
        val literals = Regex("""status\s*=\s*'([A-Za-z_]+)'""").findAll(sql).map { it.groupValues[1] }.toList()

        assertTrue(
            "No status literal found in Daos.kt — did the queries move? This test guards them, so " +
                "it must be pointed at wherever they now live.",
            literals.isNotEmpty(),
        )
        val unknown = literals.filter { ClassStatus.parse(it) == null }.distinct()
        assertEquals(
            "A DAO query matches on a status that no longer exists, so it will silently match no " +
                "rows: $unknown",
            emptyList<String>(),
            unknown,
        )
    }

    /**
     * The in-memory doubles must not name a stored status themselves.
     *
     * This test's sibling above guards the SQL literals in `Daos.kt`, which is where the queries live —
     * but `FakeDaos` reproduced three of the same predicates in Kotlin with `"SCHEDULED"` spelled out,
     * and it is outside that scan. So renaming the constant passed the very tripwire built for the
     * risk: the real queries were caught, the doubles were not, and the JVM suite went on asserting
     * auto-completion and reminder planning that the app had stopped doing.
     *
     * The fix was to have the fakes read `ClassStatus.SCHEDULED.name`, which a rename follows. This
     * keeps it that way.
     */
    @Test
    fun `the fake DAOs name no status as a bare string`() {
        val source = ProjectFiles.testSourceFile("testutil/FakeDaos.kt").readText()
        val literals = Regex("""status\s*==\s*"([A-Za-z_]+)"""").findAll(source)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

        assertEquals(
            "A fake DAO compares a status against a bare string, which a rename of the constant will " +
                "not follow. Use `ClassStatus.<NAME>.name` instead: $literals",
            emptyList<String>(),
            literals,
        )
    }
}
