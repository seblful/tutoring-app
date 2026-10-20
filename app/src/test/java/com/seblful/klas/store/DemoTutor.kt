package com.seblful.klas.store

import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassMode.IN_PERSON
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.store.DemoSubject.LANGUAGE
import com.seblful.klas.store.DemoSubject.MATH
import com.seblful.klas.store.DemoSubject.PHYSICS
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.DayOfWeek.FRIDAY
import kotlinx.datetime.DayOfWeek.MONDAY
import kotlinx.datetime.DayOfWeek.SATURDAY
import kotlinx.datetime.DayOfWeek.SUNDAY
import kotlinx.datetime.DayOfWeek.THURSDAY
import kotlinx.datetime.DayOfWeek.TUESDAY
import kotlinx.datetime.DayOfWeek.WEDNESDAY
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal data class DemoTutor(
    val language: AppLanguage,
    /** The phone the shots are taken on: its strings, its plural rules and its 12/24-hour clock. */
    val locale: Locale,
    val firstDayOfWeek: DayOfWeek,
    val currency: Currency,
    val subjects: Subjects,
    val students: List<DemoStudent>,
    val group: DemoGroup,
    /** Done and archived, so the homework screen has a history as well as a to-do list. */
    val doneHomework: List<Pair<String, String>>,
    /**
     * What the promo video types, a key at a time, as the homework for the lesson «on now» once it is
     * marked taught (store/video.mjs). Short enough to type in about two seconds.
     */
    val typedHomework: String,
) {
    /** The student whose lesson is under way at [NOW]. */
    val onNow: DemoStudent get() = students.first { s -> s.slots.any { it.covers(NOW) } }

    /** The student whose one-to-one lesson is the day's last: the one the promo video calls off. */
    val lastToday: DemoStudent
        get() = students.filter { it.slotsOn(TODAY).isNotEmpty() }.maxBy { it.slotsOn(TODAY).first().time }

    /** The first student with no lesson today: the one [UNMARKED] is booked with. */
    val freeToday: DemoStudent get() = students.first { it.slotsOn(TODAY).isEmpty() }

    /** The first student who stopped paying ([DemoStudent.paysUntil]): the debt on the people screen. */
    val debtor: DemoStudent get() = students.first { it.paysUntil != null }

    /** The student who paid ahead ([DemoStudent.prepaysLessons]): the credit on the people screen. */
    val inCredit: DemoStudent get() = students.first { it.prepaysLessons > 0 }

    data class Subjects(val math: String, val physics: String, val language: String) {
        operator fun get(subject: DemoSubject): String = when (subject) {
            MATH -> math
            PHYSICS -> physics
            LANGUAGE -> language
        }
    }

    companion object {
        val ZONE: TimeZone = TimeZone.of("Europe/Minsk")
        val NOW: LocalDateTime = LocalDateTime(2026, 10, 14, 15, 20)
        val TODAY: LocalDate = NOW.date

        /** The app's clock, stopped at [NOW]. */
        val CLOCK: WallClock = WallClock.fixed(NOW.toInstant(ZONE), ZONE)

        /**
         * The first day taught: the summer before, with students joining through to late September — so
         * the month on the reports is up on the one before, as the caption over it says. Short on
         * purpose — every amount on the screenshots, a student's lifetime earnings included, stays within
         * a thousand, which is what a private tutor's numbers look like rather than a business's.
         */
        val FIRST_DAY: LocalDate = LocalDate(2026, 7, 1)

        /** How far ahead the schedule is booked, so next week's view is not empty. */
        val BOOKED_UNTIL: LocalDate = TODAY.plus(3, DateTimeUnit.WEEK)

        /**
         * One more lesson today, booked with [freeToday] and never marked — as a tutor who has not got round
         * to it would have it — for the promo video to swipe taught: 12:30 for an hour, over before the one
         * on now, in the gap between the morning's lesson and the afternoon's.
         */
        val UNMARKED = Slot(NOW.dayOfWeek, 12, 30, 60)

        val english = DemoTutor(
            language = AppLanguage.ENGLISH,
            locale = Locale.US,
            firstDayOfWeek = SUNDAY,
            currency = Currency.USD,
            subjects = Subjects(math = "Math", physics = "Physics", language = "Spanish"),
            students = listOf(
                DemoStudent(
                    name = "Emma Wilson", subject = MATH, rate = 30, from = LocalDate(2026, 7, 1),
                    slots = listOf(Slot(MONDAY, 17, 0, 60), Slot(WEDNESDAY, 14, 0, 60)),
                    phone = "+1 202 555 0143", email = "emma.wilson@example.com",
                    homework = "Ch. 4, problems 12–20: quadratic equations",
                ),
                DemoStudent(
                    name = "Liam Carter", subject = PHYSICS, rate = 25, from = LocalDate(2026, 7, 2),
                    slots = listOf(Slot(TUESDAY, 16, 0, 90), Slot(FRIDAY, 16, 0, 60)),
                    phone = "+1 202 555 0178",
                    homework = "Newton's laws: problems 5.12–5.20",
                    paysUntil = LocalDate(2026, 9, 20),
                ),
                DemoStudent(
                    name = "Olivia Brooks", subject = LANGUAGE, rate = 20, from = LocalDate(2026, 7, 15),
                    slots = listOf(Slot(WEDNESDAY, 15, 0, 60), Slot(SATURDAY, 11, 0, 60)),
                    email = "olivia.brooks@example.com",
                    homework = "Preterite vs imperfect: ex. 4–7, p. 52",
                ),
                DemoStudent(
                    name = "Noah Bennett", subject = PHYSICS, rate = 25, from = LocalDate(2026, 8, 3),
                    slots = listOf(Slot(MONDAY, 19, 0, 60), Slot(WEDNESDAY, 18, 30, 60)),
                    mode = ClassMode.REMOTE,
                    homework = "Pendulum lab: write up the report",
                ),
                DemoStudent(
                    name = "Ava Thompson", subject = MATH, rate = 30, from = LocalDate(2026, 9, 24),
                    slots = listOf(Slot(THURSDAY, 17, 0, 90)),
                    phone = "+1 202 555 0119",
                    paysUntil = LocalDate(2026, 9, 30),
                ),
                DemoStudent(
                    name = "Ethan Hughes", subject = LANGUAGE, rate = 20, from = LocalDate(2026, 9, 16),
                    slots = listOf(Slot(WEDNESDAY, 11, 0, 60), Slot(FRIDAY, 11, 0, 60)),
                    email = "ethan.hughes@example.com",
                    prepaysLessons = 8,
                ),
            ),
            group = DemoGroup(
                name = "SAT Math Prep", rate = 12, from = LocalDate(2026, 9, 16),
                slots = listOf(Slot(WEDNESDAY, 16, 30, 90), Slot(SATURDAY, 13, 0, 90)),
                members = listOf("Mia Foster", "Lucas Reed", "Chloe Adams", "Jack Morgan"),
                subject = MATH,
                owing = "Lucas Reed",
                homework = "Practice test 3, section 3: no calculator",
            ),
            doneHomework = listOf(
                "Emma Wilson" to "Ch. 3 review: linear inequalities",
                "Olivia Brooks" to "Vocabulary list 6 and a short essay",
                "Noah Bennett" to "Momentum: problems 4.1–4.9",
            ),
            typedHomework = "Ser vs estar: ex. 1–6, p. 58",
        )

        val russian = DemoTutor(
            language = AppLanguage.RUSSIAN,
            locale = Locale.forLanguageTag("ru-RU"),
            firstDayOfWeek = MONDAY,
            currency = Currency.BYN,
            subjects = Subjects(math = "Математика", physics = "Физика", language = "Английский"),
            students = listOf(
                DemoStudent(
                    name = "Аня Смирнова", subject = MATH, rate = 30, from = LocalDate(2026, 7, 1),
                    slots = listOf(Slot(MONDAY, 17, 0, 60), Slot(WEDNESDAY, 14, 0, 60)),
                    phone = "+375 29 123-45-67", email = "anya.smirnova@example.com",
                    homework = "§ 14, № 312–318: квадратные уравнения",
                ),
                DemoStudent(
                    name = "Максим Ковалёв", subject = PHYSICS, rate = 25, from = LocalDate(2026, 7, 2),
                    slots = listOf(Slot(TUESDAY, 16, 0, 90), Slot(FRIDAY, 16, 0, 60)),
                    phone = "+375 33 765-43-21",
                    homework = "Законы Ньютона: задачи 5.12–5.20",
                    paysUntil = LocalDate(2026, 9, 20),
                ),
                DemoStudent(
                    name = "София Лебедева", subject = LANGUAGE, rate = 20, from = LocalDate(2026, 7, 15),
                    slots = listOf(Slot(WEDNESDAY, 15, 0, 60), Slot(SATURDAY, 11, 0, 60)),
                    email = "sofia.lebedeva@example.com",
                    homework = "Present Perfect: упр. 4–7, стр. 52",
                ),
                DemoStudent(
                    name = "Артём Новиков", subject = PHYSICS, rate = 25, from = LocalDate(2026, 8, 3),
                    slots = listOf(Slot(MONDAY, 19, 0, 60), Slot(WEDNESDAY, 18, 30, 60)),
                    mode = ClassMode.REMOTE,
                    homework = "Лабораторная «Маятник»: оформить отчёт",
                ),
                DemoStudent(
                    name = "Ева Морозова", subject = MATH, rate = 30, from = LocalDate(2026, 9, 24),
                    slots = listOf(Slot(THURSDAY, 17, 0, 90)),
                    phone = "+375 44 555-01-19",
                    paysUntil = LocalDate(2026, 9, 30),
                ),
                DemoStudent(
                    name = "Ольга Кузнецова", subject = LANGUAGE, rate = 20, from = LocalDate(2026, 9, 16),
                    slots = listOf(Slot(WEDNESDAY, 11, 0, 60), Slot(FRIDAY, 11, 0, 60)),
                    email = "olga.k@example.com",
                    prepaysLessons = 8,
                ),
            ),
            group = DemoGroup(
                name = "Подготовка к ЦТ", rate = 12, from = LocalDate(2026, 9, 16),
                slots = listOf(Slot(WEDNESDAY, 16, 30, 90), Slot(SATURDAY, 13, 0, 90)),
                members = listOf("Даниил Волков", "Мила Соколова", "Тимур Орлов", "Полина Егорова"),
                subject = MATH,
                owing = "Тимур Орлов",
                homework = "ЦТ, вариант 7: часть А, задания 1–18",
            ),
            doneHomework = listOf(
                "Аня Смирнова" to "Повторить § 12: линейные неравенства",
                "София Лебедева" to "Слова к уроку 6 и короткое эссе",
                "Артём Новиков" to "Импульс: задачи 4.1–4.9",
            ),
            typedHomework = "Past Simple: упр. 1–6, стр. 58",
        )
    }
}

internal enum class DemoSubject { MATH, PHYSICS, LANGUAGE }

internal data class Slot(val day: DayOfWeek, val hour: Int, val minute: Int, val minutes: Int) {
    val time: LocalTime get() = LocalTime(hour, minute)

    /** When it starts on [date], which has to fall on [day]. */
    fun startOn(date: LocalDate): Instant = date.atTime(time).toInstant(DemoTutor.ZONE)
    fun endOn(date: LocalDate): Instant = startOn(date).plus((minutes * 60L).seconds)

    /** Whether it is under way at [at]. */
    fun covers(at: LocalDateTime): Boolean = at.dayOfWeek == day && at.time >= time &&
        at.time.toSecondOfDay() < time.toSecondOfDay() + minutes * 60
}

internal sealed interface Weekly {
    val subject: DemoSubject
    val mode: ClassMode

    /** Per hour (and, for a group, per member), in the currency's major unit. */
    val rate: Long
    val from: LocalDate
    val slots: List<Slot>

    /** The lessons on [date]: none before [from]. */
    fun slotsOn(date: LocalDate): List<Slot> =
        if (date < from) emptyList() else slots.filter { it.day == date.dayOfWeek }
}

internal data class DemoStudent(
    val name: String,
    override val subject: DemoSubject,
    override val rate: Long,
    override val from: LocalDate,
    override val slots: List<Slot>,
    val phone: String = "",
    val email: String = "",
    override val mode: ClassMode = IN_PERSON,
    /** Set and still open, so it rides on the student's next lesson. */
    val homework: String? = null,
    /** Pays after each lesson up to this day and not since: the debt the cards and reports show. */
    val paysUntil: LocalDate? = null,
    /** Paid this many lessons ahead at the start of the month, so one card shows a credit. */
    val prepaysLessons: Int = 0,
) : Weekly

internal data class DemoGroup(
    val name: String,
    override val rate: Long,
    override val from: LocalDate,
    override val slots: List<Slot>,
    val members: List<String>,
    override val subject: DemoSubject,
    /** The one member who stopped paying a fortnight ago. */
    val owing: String,
    val homework: String,
) : Weekly {
    override val mode: ClassMode get() = IN_PERSON
}
