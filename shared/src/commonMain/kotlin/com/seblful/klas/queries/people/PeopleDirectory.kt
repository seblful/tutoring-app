package com.seblful.klas.queries.people

import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.people.Captions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The names lessons and homework are drawn with, always from every student, subject and group — archived
 * ones included, as their lessons still show — so no row can be named from a partial set.
 */
@SingleIn(AppScope::class)
class PeopleDirectory @Inject constructor(
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val groups: GroupRepository,
) {
    fun observe(): Flow<Captions> = combine(
        students.observeAll(),
        subjects.observeAll(),
        groups.observeAll(),
    ) { studentList, subjectList, groupList -> Captions.of(studentList, subjectList, groupList) }
}
