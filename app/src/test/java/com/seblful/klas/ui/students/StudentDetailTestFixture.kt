package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.queries.students.StudentDetailsQuery
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.RecordingDocumentSharing
import com.seblful.klas.testutil.RecordingSheetPrinter
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
abstract class StudentDetailTestFixture {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    protected val zone: TimeZone = TimeZone.of("Europe/Minsk")
    protected val today: LocalDate = LocalDate(2026, 5, 17)
    protected val clock = MutableClock(today.atTime(14, 30).toInstant(zone), zone)
    protected val sharing = RecordingDocumentSharing()
    protected val printer = RecordingSheetPrinter()

    @get:Rule(order = 0)
    val env = TestEnv(clock = clock)

    protected fun vm(id: Long) = mainRule.own(
        StudentDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to id)),
            students = env.studentRepo,
            lessonChanges = env.lessonChanges,
            details = StudentDetailsQuery(
                env.studentRepo, env.subjectRepo, env.enrollmentRepo, env.classRepo, env.groupRepo,
                env.homeworkRepo, env.paymentRepo, env.rateHistoryRepo, env.clock,
            ),
            homework = env.homeworkRepo,
            payments = env.paymentRepo,
            enrollment = env.enrollmentRepo,
            appScope = mainRule.appScope,
            computation = mainRule.dispatcher,
            clock = env.clock,
            sharing = sharing,
            printer = printer,
        ),
    )

    protected suspend fun seedStudent(name: String = "Аня"): Long =
        env.fixtures.student(name = name)
}
