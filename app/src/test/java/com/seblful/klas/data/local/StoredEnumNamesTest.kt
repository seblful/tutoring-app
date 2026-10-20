package com.seblful.klas.data.local

import com.seblful.klas.data.billing.PaymentEntity
import com.seblful.klas.data.billing.PriceEntity
import com.seblful.klas.data.billing.RateChangeEntity
import com.seblful.klas.data.homework.HomeworkEntity
import com.seblful.klas.data.lessons.AttendanceEntity
import com.seblful.klas.data.lessons.ClassRow
import com.seblful.klas.data.lessons.LessonEntity
import com.seblful.klas.data.people.GroupEntity
import com.seblful.klas.data.people.GroupMemberCrossRef
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.data.people.StudentSubjectCrossRef
import com.seblful.klas.data.people.SubjectEntity
import com.seblful.klas.data.settings.SettingsEntity
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

class StoredEnumNamesTest {

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

    @Test
    fun `the stored currency names are the ones the settings row holds`() {
        val message = "A Currency constant was renamed, added or removed. The `settings.currency` column " +
            "stores these names — devices holding the old text will silently fall back to BYN. " +
            "Migrate the column (or accept the fallback deliberately), then update this list."
        assertEquals(message, storedCurrencies, Currency.entries.map { it.name }.toSet())
        assertEquals(message, storedCurrencies, Currency.entries.map(converters::fromCurrency).toSet())
    }

    @Test
    fun `the stored language names are the ones the settings row holds`() {
        val message = "An AppLanguage constant was renamed, added or removed. The `settings.language` column " +
            "stores these names — devices holding the old text will silently fall back to SYSTEM " +
            "and follow the phone again. Migrate the column (or accept the fallback deliberately), " +
            "then update this list."
        assertEquals(message, storedLanguages, AppLanguage.entries.map { it.name }.toSet())
        assertEquals(message, storedLanguages, AppLanguage.entries.map(converters::fromLanguage).toSet())
    }

    @Test
    fun `the stored theme names are the ones the settings row holds`() {
        val message = "A ThemeChoice constant was renamed, added or removed. The `settings.themeMode` column " +
            "stores these names — devices holding the old text will silently fall back to SYSTEM. " +
            "Migrate the column (or accept the fallback deliberately), then update this list."
        assertEquals(message, storedThemes, ThemeChoice.entries.map { it.name }.toSet())
        assertEquals(message, storedThemes, ThemeChoice.entries.map(converters::fromTheme).toSet())
    }

    @Test
    fun `every entity column defaulting to an enum writes a stored name`() {
        assertTrue(converters.fromMode(StudentEntity(name = "Иван").defaultMode) in storedModes)
        assertTrue(converters.fromMode(GroupEntity(name = "11 «А»").defaultMode) in storedModes)
        val lesson = LessonEntity(subjectId = 1, startMillis = 0, durationMinutes = 60, status = LessonStatus.SCHEDULED)
        assertTrue(converters.fromMode(lesson.mode) in storedModes)
        val row =
            ClassRow(
                studentId = 1,
                subjectId = 1,
                startMillis = 0,
                durationMinutes = 60,
                status = LessonStatus.SCHEDULED,
            )
        assertTrue(converters.fromAttended(row.state) in storedAttendance)
        assertTrue(converters.fromMode(row.mode) in storedModes)
        val settings = SettingsEntity()
        assertTrue(converters.fromTheme(settings.themeMode) in storedThemes)
        assertTrue(converters.fromCurrency(settings.currency) in storedCurrencies)
        assertTrue(converters.fromLanguage(settings.language) in storedLanguages)
    }

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

    @Test
    fun `every status literal in the DAO SQL names a real status`() {
        val sql = ProjectFiles.daoSources().joinToString("\n") { it.readText() }
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
