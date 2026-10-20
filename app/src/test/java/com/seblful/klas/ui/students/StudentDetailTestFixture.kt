package com.seblful.klas.ui.students

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.data.people.StudentEntity
import com.seblful.klas.queries.students.StudentDetailsQuery
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
abstract class StudentDetailTestFixture {

    @get:Rule
    val mainRule = MainDispatcherRule()

    protected val zone: TimeZone = TimeZone.of("Europe/Minsk")
    protected val today: LocalDate = LocalDate(2026, 5, 17)
    protected val clock = MutableClock(today.atTime(14, 30).toInstant(zone), zone)

    @get:Rule
    val env = TestEnv(clock = clock)

    protected fun vm(id: Long) = StudentDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("id" to id)),
        students = env.studentRepo,
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
    )

    protected suspend fun seedStudent(name: String = "Аня"): Long =
        env.studentDao.upsert(StudentEntity(name = name))
}
