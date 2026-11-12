package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.queries.students.StudentDetailsQuery
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.RecordingDocumentSharing
import com.seblful.klas.testutil.RecordingSheetPrinter
import com.seblful.klas.testutil.TestMain
import com.seblful.klas.testutil.UiTest
import com.seblful.klas.testutil.UiTestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalCoroutinesApi::class)
abstract class StudentDetailTestFixture : UiTest() {

    val main = TestMain()

    protected val zone: TimeZone = TimeZone.of("Europe/Minsk")
    protected val today: LocalDate = LocalDate(2026, 5, 17)
    protected val clock = MutableClock(today.atTime(14, 30).toInstant(zone), zone)
    protected val sharing = RecordingDocumentSharing()
    protected val printer = RecordingSheetPrinter()

    val env = UiTestEnv(clock = clock)

    @BeforeTest
    fun startMain() = main.start()

    @AfterTest
    fun finishMainThenCloseDatabase() {
        try {
            main.finish()
        } finally {
            env.close()
        }
    }

    protected fun vm(id: Long) = main.own(
        StudentDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to id)),
            students = env.studentRepo,
            lessonChanges = env.lessonChanges,
            details = StudentDetailsQuery(
                env.studentRepo, env.subjectRepo, env.enrollmentRepo, env.classRepo, env.groupRepo,
                env.homeworkQuery, env.paymentRepo, env.rateHistoryRepo,
            ),
            homework = env.homeworkRepo,
            payments = env.paymentRepo,
            enrollment = env.enrollmentRepo,
            appScope = main.appScope,
            computation = main.dispatcher,
            clock = env.clock,
            sharing = sharing,
            printer = printer,
        ),
    )

    protected suspend fun seedStudent(name: String = "Аня"): Long =
        env.fixtures.student(name = name)
}
