package com.seblful.klas.pilot

import com.seblful.klas.KlasStore
import com.seblful.klas.data.lessons.LessonWrite
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

data class PilotStudent(val id: Long, val name: String)
data class PilotLesson(val id: Long, val studentName: String, val startsAtMillis: Long)
data class PilotSnapshot(val students: List<PilotStudent>, val lessons: List<PilotLesson>)

/** Small host-validation flow; all writes use the same repositories as the Android app. */
class PilotWorkflow(private val store: KlasStore) {
    suspend fun load(): PilotSnapshot {
        val students = store.students.getActive()
        val names = students.associate { it.id to it.name }
        return PilotSnapshot(
            students.map { PilotStudent(it.id, it.name) },
            store.classes.getAll().sortedBy { it.start }.map {
                PilotLesson(it.id, names[it.studentId].orEmpty(), it.start.toEpochMilliseconds())
            },
        )
    }

    suspend fun addStudent(name: String): PilotSnapshot {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Enter a student name." }
        val subject = store.subjects.observeAll().first().firstOrNull { it.name == SUBJECT }
            ?.id ?: store.subjects.upsert(Subject(name = SUBJECT))
        store.enrollment.createStudent(Student(name = trimmed), mapOf(subject to RATE))
        return load()
    }

    suspend fun book(studentId: Long, startsAtMillis: Long): PilotSnapshot {
        val student = requireNotNull(store.students.getById(studentId)) { "Select a student." }
        require(!student.archived) { "Select an active student." }
        val price = requireNotNull(store.enrollment.observePricesFor(studentId).first().firstOrNull()) {
            "The student needs a subject and price."
        }
        val outcome = store.classes.book(
            subjectId = price.subjectId,
            start = Instant.fromEpochMilliseconds(startsAtMillis),
            durationMinutes = LESSON_MINUTES,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(studentId),
            price = price.amount,
        )
        when (outcome) {
            is LessonWrite.Written -> return load()
            is LessonWrite.HourTaken -> error("That hour is already booked. Choose another time.")
            LessonWrite.Gone -> error("The lesson is no longer available.")
        }
    }

    private companion object {
        const val SUBJECT = "Math"
        const val LESSON_MINUTES = 60
        val RATE = Money.ofMajor(30)
    }
}
