package com.seblful.klas.notifications

import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Reminders

/**
 * The words a tray entry names a lesson by — who it is with and what it is in — read by id as it posts.
 *
 * A reminder and the two lesson-listing digests name a lesson in the same sentence, and each looked the
 * names up for itself: the same three repository reads and the same [Reminders.announcedBy] call, twice.
 * A notification reads rows at the moment it fires rather than holding whole-table lists, which is why
 * this is lookups by id and not a `Captions`.
 *
 * Unknown ids read as blank, like `Captions`: the entry is still worth posting by its time alone.
 */
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
