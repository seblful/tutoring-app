package com.seblful.klas.data.backup

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.GroupMemberCrossRef
import com.seblful.klas.data.local.HomeworkEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.StudentSubjectCrossRef
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.testutil.ProjectFiles
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSchemaCoverageTest {

    private val carried: Map<String, KSerializer<*>> = mapOf(
        "subjects" to SubjectEntity.serializer(),
        "students" to StudentEntity.serializer(),
        "student_subjects" to StudentSubjectCrossRef.serializer(),
        "student_groups" to GroupEntity.serializer(),
        "group_members" to GroupMemberCrossRef.serializer(),
        "prices" to PriceEntity.serializer(),
        "price_history" to RateChangeEntity.serializer(),
        "lessons" to BackupLesson.serializer(),
        "attendance" to BackupAttendance.serializer(),
        "payments" to PaymentEntity.serializer(),
        "homework" to HomeworkEntity.serializer(),
    )

    private val deviceLocal = setOf("settings")

    @Test
    fun `every table in the schema is either carried by the snapshot or deliberately device-local`() {
        assertEquals(
            "A table exists that the snapshot neither carries nor deliberately excludes — " +
                "add it to `carried` (so backups include it) or to `deviceLocal` (on purpose).",
            schemaTables.keys.sorted(),
            (carried.keys + deviceLocal).sorted(),
        )
    }

    @Test
    fun `every column of a carried table survives serialization`() {
        carried.forEach { (table, serializer) ->
            val columns = schemaTables.getValue(table)
            val serialized = serializer.descriptor.elementNames.toSet()
            val lost = columns - serialized
            assertEquals(
                "Columns of `$table` are persisted but not serialized, so a backup would drop them",
                emptySet<String>(),
                lost,
            )
        }
    }

    @Test
    fun `an export writes only the columns the schema has`() {
        val exported = Json { encodeDefaults = true }.encodeToJsonElement(
            BackupData.serializer(),
            DatabaseSnapshot(
                lessons = listOf(
                    LessonEntity(4, subjectId = 1, startMillis = 1L, durationMinutes = 60, status = LessonStatus.COMPLETED),
                ),
                attendance = listOf(AttendanceEntity(9, lessonId = 4, studentId = 1, state = Attended.PRESENT)),
            ).toBackupData(),
        ).jsonObject

        for (table in listOf("lessons", "attendance")) {
            val written = (exported.getValue(table).jsonArray.single() as JsonObject).keys
            assertEquals(
                "An export of `$table` writes keys the schema has no column for",
                emptySet<String>(),
                written - schemaTables.getValue(table),
            )
        }
    }

    @Test
    fun `the snapshot carries exactly the entities this test claims it does`() {
        val descriptor = BackupData.serializer().descriptor
        val inSnapshot = (0 until descriptor.elementsCount)
            .map { descriptor.getElementDescriptor(it) }
            .filter { it.kind == StructureKind.LIST }
            .map { it.getElementDescriptor(0).serialName }
            .toSet() - retiredShapes
        assertEquals(
            "BackupData's lists and this test's `carried` map disagree",
            carried.values.map { it.descriptor.serialName }.toSet(),
            inSnapshot,
        )
    }

    private val retiredShapes = setOf(LegacyClass.serializer().descriptor.serialName)

    private val schemaTables: Map<String, Set<String>> by lazy {
        val database = Json.parseToJsonElement(ProjectFiles.newestExportedSchema().readText())
            .jsonObject.getValue("database").jsonObject
        database.getValue("entities").jsonArray.associate { entity ->
            val o = entity.jsonObject
            o.getValue("tableName").jsonPrimitive.content to
                o.getValue("fields").jsonArray
                    .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
                    .toSet()
        }
    }
}
