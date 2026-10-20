package com.seblful.klas.notifications

import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Reminders

internal class LessonNames(
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
) {
    /** The group's name for a group lesson, otherwise the student's — see [Reminders.announcedBy]. */
    suspend fun who(klass: TutoringClass): String = Reminders.announcedBy(
        groupName = klass.groupId?.let { groups.getById(it)?.name },
        studentName = students.getById(klass.studentId)?.name,
    )

    /** What [klass] is in. Every class of one lesson shares a subject, so any of them speaks for all. */
    suspend fun subject(klass: TutoringClass): String = subjects.getById(klass.subjectId)?.name.orEmpty()
}
