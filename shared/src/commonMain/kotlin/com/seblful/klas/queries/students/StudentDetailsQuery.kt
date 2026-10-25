package com.seblful.klas.queries.students

import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.billing.RateHistoryRepository
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.domain.billing.Billing
import com.seblful.klas.domain.lesson.LessonKind
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.people.NameOrder
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.queries.homework.HomeworkQuery
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Joins a student's history, billing and homework independently of a screen's lifecycle. */
@SingleIn(AppScope::class)
class StudentDetailsQuery @Inject constructor(
    private val students: StudentRepository,
    private val subjects: SubjectRepository,
    private val enrollment: EnrollmentRepository,
    private val classes: ClassRepository,
    private val groups: GroupRepository,
    private val homework: HomeworkQuery,
    private val payments: PaymentRepository,
    private val rateHistory: RateHistoryRepository,
) {
    fun observe(studentId: Long): Flow<StudentDetails> {
        val partial: Flow<Partial> = combine(
            students.observeById(studentId),
            subjects.observeAll(),
            enrollment.observePricesFor(studentId),
            classes.observeForStudent(studentId),
            // The whole student's log in one query, split per subject below — the card shows every
            // subject at once, so a query per subject would be n queries for one screen.
            rateHistory.observeForStudent(studentId),
        ) { student, allSubjects, priceList, classList, changes ->
            val subjectsById = allSubjects.associateBy { it.id }
            // `as?`, not `as`: `price_history` is **one table for two kinds of rate** discriminated by
            // `RateOwner`, and `observeForStudent` selects on `studentId` alone. A hard cast therefore rested
            // on no row of a student's ever carrying a group id — a property of the queries as they stand
            // today rather than of the schema, and a `ClassCastException` inside a `Flow` on the card that
            // shows a student's money if it ever stopped holding. A row that is not a student's price is not
            // this card's history, so it is skipped.
            val historyBySubject = changes
                .mapNotNull { change -> (change.owner as? RateOwner.StudentSubject)?.subjectId?.to(change) }
                .groupBy({ it.first }, { it.second })
            Partial(
                student = student,
                allSubjects = allSubjects,
                // A student's subjects ARE their priced subjects — one source of truth, so the list here
                // is exactly what can be booked. No "price not set" rows, no bookable-but-hidden subjects.
                subjects = NameOrder.sorted(
                    priceList.mapNotNull { p ->
                        subjectsById[p.subjectId]?.let {
                            SubjectPrice(it, p.amount, historyBySubject[p.subjectId].orEmpty())
                        }
                    },
                ) { it.subject.name },
                classes = classList,
            )
        }

        // Two stages because combine accepts at most five typed sources.
        return combine(
            partial,
            payments.observeForStudent(studentId),
            groups.observeAll(),
            homework.observeForStudent(studentId),
        ) { partial, paymentList, groupList, homework ->
            val captions = Captions.of(
                students = listOfNotNull(partial.student),
                subjects = partial.allSubjects,
                groups = groupList,
            )
            StudentDetails(
                student = partial.student,
                balance = Billing.balanceOf(studentId, partial.classes, paymentList).state,
                totals = Taught.byStudent(partial.classes),
                subjects = partial.subjects,
                allSubjects = partial.allSubjects,
                classes = partial.classes.map { c ->
                    ClassRow(klass = c, subjectName = captions.subjectOf(c), groupName = captions.groupOf(c))
                },
                payments = paymentList,
                lessonKinds = LessonKind.tally(partial.classes),
                homework = homework,
            )
        }
    }

    private data class Partial(
        val student: Student?,
        val allSubjects: List<Subject>,
        val subjects: List<SubjectPrice>,
        val classes: List<TutoringClass>,
    )
}
