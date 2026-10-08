package com.seblful.klas.data.local

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An enum constant's **name is a persisted value**, and nothing else in the build says so.
 *
 * `lessons.status`, `attendance.state`, the three `mode` columns and the settings row's `themeMode`,
 * `currency` and `language` store constant names as text ([EnumConverters]), two DAO queries match on
 * `'SCHEDULED'` as a SQL literal, and a backup file carries the same names as JSON strings. So renaming a
 * constant — an ordinary refactor that every IDE offers and every reference in Kotlin follows — is really a
 * schema change, and one that fails **silently in different ways**:
 *
 * - existing rows keep the old text, and reading them stops working (the converter's total parse finds no
 *   constant, and falls back or fails — see [EnumConverters]);
 * - the two SQL literals keep the old text and simply stop matching, so nothing auto-completes and no
 *   reminder is ever planned — with no error anywhere, because "no rows due" is a perfectly ordinary
 *   answer.
 *
 * That second one is the reason this test exists rather than being left to code review. It is an
 * acknowledgement list, like `BackupSchemaCoverageTest` and `MappersTest`: renaming a constant fails
 * here, in front of whoever is doing it, where the message can say a migration is owed.
 *
 * Each list is checked twice: against the enum's constants, and against what [EnumConverters] actually
 * writes for them — the text on disk is the converter's output, so a converter that wrote anything but the
 * name (an ordinal, a lower-case spelling) would be the same silent schema change by another route.
 *
 * The in-memory DAO doubles compare typed constants (`LessonStatus.SCHEDULED`), which a rename follows and
 * a bare string no longer compiles against — so they need no scan of their own here any more.
 */
class StoredEnumNamesTest {

    /**
     * The names in the database today. **Changing this list is a migration**, not a refactor: every
     * existing row holds the old text, so the migration has to rewrite them (and the backup format's
     * copy of them) before this list may be updated.
     */
    private val storedStatuses = setOf("SCHEDULED", "COMPLETED", "CANCELLED")
    private val storedAttendance = setOf("EXPECTED", "PRESENT", "ABSENT", "ABSENT_BILLED")
    private val storedModes = setOf("IN_PERSON", "REMOTE")
    private val storedCurrencies = setOf("BYN", "RUB", "USD", "EUR")
    private val storedLanguages = setOf("SYSTEM", "RUSSIAN", "ENGLISH")
    private val storedThemes = setOf("LIGHT", "DARK", "SYSTEM")

    private val converters = EnumConverters()

    @Test
    fun `the stored status names are the ones the database holds`() {
        val message = "A LessonStatus constant was renamed, added or removed. Every `lessons.status` row holds " +
            "the old text — write a migration that rewrites them, check the two DAO queries " +
            "matching on a status literal, then update this list."
        assertEquals(message, storedStatuses, LessonStatus.entries.map { it.name }.toSet())
        assertEquals(message, storedStatuses, LessonStatus.entries.map(converters::fromStatus).toSet())
    }

    /**
     * The other half of what bills: an `attendance.state` the converter cannot read fails the read rather
     * than guessing (see [EnumConverters]), so a rename here takes every screen listing a lesson with it.
     */
    @Test
    fun `the stored attendance names are the ones the database holds`() {
        val message = "An Attended constant was renamed, added or removed. Every `attendance.state` row " +
            "holds the old text — write a migration that rewrites them, then update this list."
        assertEquals(message, storedAttendance, Attended.entries.map { it.name }.toSet())
        assertEquals(message, storedAttendance, Attended.entries.map(converters::fromAttended).toSet())
    }

    @Test
    fun `the stored mode names are the ones the database holds`() {
        val message = "A ClassMode constant was renamed, added or removed. Three columns store this text and " +
            "default to one of these values — write a migration, then update this list."
        assertEquals(message, storedModes, ClassMode.entries.map { it.name }.toSet())
        assertEquals(message, storedModes, ClassMode.entries.map(converters::fromMode).toSet())
    }

    /**
     * The one stored word that is *not* money-bearing: an unrecognized value falls back to BYN
     * (`Currency.parse`), so a rename loses only a label — but it still loses one silently, on every
     * device that stored the old text, which is exactly the drift this list exists to interrupt.
     */
    @Test
    fun `the stored currency names are the ones the settings row holds`() {
        val message = "A Currency constant was renamed, added or removed. The `settings.currency` column " +
            "stores these names — devices holding the old text will silently fall back to BYN. " +
            "Migrate the column (or accept the fallback deliberately), then update this list."
        assertEquals(message, storedCurrencies, Currency.entries.map { it.name }.toSet())
        assertEquals(message, storedCurrencies, Currency.entries.map(converters::fromCurrency).toSet())
    }

    /**
     * The other stored word that carries no money: `settings.language`, which decides the app's words and
     * nothing about its numbers. An unrecognized value falls back to `SYSTEM` — follow the phone — so a
     * rename costs the tutor a preference rather than a record, but it costs it silently and on every
     * device that stored the old text.
     *
     * **`SYSTEM` is on this list even though it names no language.** It is the value the v5 → v6 migration
     * backfills with and the value the column defaults to, so it is as much a stored word as the two
     * translations are.
     */
    @Test
    fun `the stored language names are the ones the settings row holds`() {
        val message = "An AppLanguage constant was renamed, added or removed. The `settings.language` column " +
            "stores these names — devices holding the old text will silently fall back to SYSTEM " +
            "and follow the phone again. Migrate the column (or accept the fallback deliberately), " +
            "then update this list."
        assertEquals(message, storedLanguages, AppLanguage.entries.map { it.name }.toSet())
        assertEquals(message, storedLanguages, AppLanguage.entries.map(converters::fromLanguage).toSet())
    }

    /** `settings.themeMode` — a preference like the language, and lost as silently by a rename. */
    @Test
    fun `the stored theme names are the ones the settings row holds`() {
        val message = "A ThemeChoice constant was renamed, added or removed. The `settings.themeMode` column " +
            "stores these names — devices holding the old text will silently fall back to SYSTEM. " +
            "Migrate the column (or accept the fallback deliberately), then update this list."
        assertEquals(message, storedThemes, ThemeChoice.entries.map { it.name }.toSet())
        assertEquals(message, storedThemes, ThemeChoice.entries.map(converters::fromTheme).toSet())
    }

    /**
     * The entity defaults, read off the entities themselves and through the converter — a column
     * defaulting to a name no stored list has would write rows a later build cannot read, from the very
     * first insert.
     */
    @Test
    fun `every entity column defaulting to an enum writes a stored name`() {
        assertTrue(converters.fromMode(StudentEntity(name = "Иван").defaultMode) in storedModes)
        assertTrue(converters.fromMode(GroupEntity(name = "11 «А»").defaultMode) in storedModes)
        val lesson = LessonEntity(subjectId = 1, startMillis = 0, durationMinutes = 60, status = LessonStatus.SCHEDULED)
        assertTrue(converters.fromMode(lesson.mode) in storedModes)
        val row = ClassRow(studentId = 1, subjectId = 1, startMillis = 0, durationMinutes = 60, status = LessonStatus.SCHEDULED)
        assertTrue(converters.fromAttended(row.state) in storedAttendance)
        assertTrue(converters.fromMode(row.mode) in storedModes)
        val settings = SettingsEntity()
        assertTrue(converters.fromTheme(settings.themeMode) in storedThemes)
        assertTrue(converters.fromCurrency(settings.currency) in storedCurrencies)
        assertTrue(converters.fromLanguage(settings.language) in storedLanguages)
    }

    /**
     * **Every enum column goes through [EnumConverters], and nothing else.** Room has enum support of its
     * own and uses it silently wherever no converter is registered: it writes the same name — so the
     * schema, the data and every other test here would look right — but reads back with `valueOf`, which
     * throws for the unreadable text the converters fall back from, inside the `Flow` every screen
     * collects. Dropping the registration, or adding an enum column of a type with no converter pair,
     * would change nothing but that, which is why it is checked here rather than trusted.
     *
     * `@TypeConverters` is `BINARY`-retained, so the registration is read from the source, as the SQL
     * literals below are.
     */
    @Test
    fun `every enum column is read and written by the converters`() {
        assertTrue(
            "AppDatabase no longer registers EnumConverters — Room would fall back to valueOf.",
            ProjectFiles.sourceFile("data/local/AppDatabase.kt").readText()
                .contains("@TypeConverters(EnumConverters::class)"),
        )

        val rows = listOf(
            SubjectEntity::class, StudentEntity::class, StudentSubjectCrossRef::class, GroupEntity::class,
            GroupMemberCrossRef::class, PriceEntity::class, RateChangeEntity::class, LessonEntity::class,
            AttendanceEntity::class, PaymentEntity::class, HomeworkEntity::class, SettingsEntity::class,
            ClassRow::class,
        )
        val enumColumns = rows.flatMap { row ->
            row.java.declaredFields.filter { it.type.isEnum }.map { it.type }
        }.toSet()
        val methods = EnumConverters::class.java.declaredMethods
        val readable = methods.filter { it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
            .map { it.returnType }.toSet()
        val writable = methods.filter { it.returnType == String::class.java && it.parameterCount == 1 }
            .map { it.parameterTypes.single() }.toSet()

        assertEquals("the six enum types the schema stores", 6, enumColumns.size)
        assertEquals("An enum column has no converter to read it", emptySet<Class<*>>(), enumColumns - readable)
        assertEquals("An enum column has no converter to write it", emptySet<Class<*>>(), enumColumns - writable)
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
        val unknown = literals.filter { LessonStatus.parse(it) == null }.distinct()
        assertEquals(
            "A DAO query matches on a status that no longer exists, so it will silently match no " +
                "rows: $unknown",
            emptyList<String>(),
            unknown,
        )
    }
}
