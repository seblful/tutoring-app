package com.seblful.klas.ui.schedule.booking

import androidx.lifecycle.SavedStateHandle
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.format.MoneyField
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ClassEditTestFixture {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()
    protected val zone: TimeZone = env.clock.zone

    protected fun vm(
        id: Long = 0L,
        groupId: Long = 0L,
        day: LocalDate? = null,
        studentId: Long = 0L,
        like: Long = 0L,
    ) = mainRule.own(
        ClassEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "id" to id,
                    "group" to groupId,
                    "day" to day?.toString(),
                    "student" to studentId,
                    "like" to like,
                ),
            ),
            classes = env.classRepo,
            studentsRepo = env.studentRepo,
            groupsRepo = env.groupRepo,
            subjectsRepo = env.subjectRepo,
            enrollment = env.enrollmentRepo,
            settingsRepo = env.settingsRepo,
            notificationPermission = env.notificationPermission,
            appScope = mainRule.appScope,
            computation = mainRule.dispatcher,
            clock = env.clock,
        ),
    )

    protected suspend fun seedStudentWithPricedSubject(
        studentName: String = "Аня",
        subjectName: String = "Математика",
        price: Money = Money.ofMajor(30),
        mode: ClassMode = ClassMode.IN_PERSON,
    ): Pair<Long, Long> {
        val studentId = env.fixtures.student(name = studentName, defaultMode = mode)
        val subjectId = env.fixtures.subject(name = subjectName)
        env.enrollmentRepo.enroll(studentId, subjectId, price)
        return studentId to subjectId
    }

    protected suspend fun seedLesson(
        date: LocalDate,
        at: LocalTime,
        minutes: Int = 60,
        studentName: String = "Борис",
    ): Pair<Long, Long> {
        val (studentId, subjectId) = seedStudentWithPricedSubject(studentName = studentName)
        val id = env.classRepo.book(
            subjectId = subjectId,
            start = date.atTime(at).toInstant(zone),
            durationMinutes = minutes,
            mode = ClassMode.IN_PERSON,
            studentIds = listOf(studentId),
            price = Money.ofMajor(30),
        ).written().single()
        return studentId to id
    }

    protected fun ClassEditViewModel.fillFor(
        studentId: Long,
        subjectId: Long,
        date: LocalDate,
        from: LocalTime,
        minutes: Int,
    ) = change(
        BookingIntent.Edit {
            it.copy(
                studentId = studentId, subjectId = subjectId, date = date,
                timeFrom = from, durationMinutes = minutes, price = MoneyField("30"),
            )
        },
    )

    protected val groupRate = Money.ofMajor(20)

    protected suspend fun seedGroup(
        annPrice: Money = Money.ofMajor(30),
        bobPrice: Money = Money.ofMajor(25),
        price: Money = groupRate,
    ): Triple<Long, Long, Long> {
        val ann = env.fixtures.student(name = "Аня")
        val bob = env.fixtures.student(name = "Борис")
        val algebra = env.fixtures.subject(name = "Алгебра")
        env.enrollmentRepo.enroll(ann, algebra, annPrice)
        env.enrollmentRepo.enroll(bob, algebra, bobPrice)
        val groupId = env.groupRepo.save(
            Group(name = "11 «А»", price = price, defaultMode = ClassMode.REMOTE),
            setOf(ann, bob),
        )
        return Triple(groupId, ann, bob)
    }
}
