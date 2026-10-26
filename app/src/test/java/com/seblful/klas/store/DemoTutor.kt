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
import kotlinx.datetime.toKotlinDayOfWeek
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal data class DemoTutor(
    val language: AppLanguage,
    /** The Play listing's language code, which names its folders: `no-NO`, not `nb-NO`. */
    val play: String,
    /** The phone the shots are taken on: its strings, its plural rules and its 12/24-hour clock. */
    val locale: Locale,
    val firstDayOfWeek: DayOfWeek,
    val currency: Currency,
    /** See [DemoWords.scale]. */
    val scale: Long,
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

    /** The first student with no lesson today: the one [UNMARKED] is booked with. */
    val freeToday: DemoStudent get() = students.first { it.slotsOn(TODAY).isEmpty() }

    /** The first student who stopped paying ([DemoStudent.paysUntil]): the debt on the people screen. */
    val debtor: DemoStudent get() = students.first { it.paysUntil != null }

    /** The student who paid ahead ([DemoStudent.prepaysLessons]): the credit on the people screen. */
    val inCredit: DemoStudent get() = students.first { it.prepaysLessons > 0 }

    @Serializable
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
         * a thousand (times [DemoWords.scale]), which is what a private tutor's numbers look like rather
         * than a business's.
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

        /** Where each language's words are: store/text/<Play code>/demo.json (app/build.gradle.kts). */
        private val TEXT by lazy { File(System.getProperty(TEXT_PROPERTY) ?: error("$TEXT_PROPERTY is not set")) }
        const val TEXT_PROPERTY = "klas.store.text"

        val english: DemoTutor by lazy { load("en-US") }
        val russian: DemoTutor by lazy { load("ru-RU") }

        /** Every language with a demo tutor, by Play code. */
        val all: List<DemoTutor> by lazy {
            TEXT.listFiles().orEmpty()
                .filter { File(it, "demo.json").isFile }
                .map { load(it.name) }
                .sortedBy { it.play }
        }

        /** The tutor in [play]'s words. */
        fun load(play: String): DemoTutor =
            of(play, Json.decodeFromString<DemoWords>(File(TEXT, "$play/demo.json").readText()))

        /**
         * The week every language tells, in [words]: the same students on the same days at the same times,
         * the same debts and the same credit — only who they are, what they study and what it costs differ.
         */
        fun of(play: String, words: DemoWords): DemoTutor = with(words) {
            require(students.size == 6 && phones.size == 3 && emails.size == 3 && homework.size == 4) {
                "$play: words missing"
            }
            require(members.size == 4 && doneHomework.size == 3) { "$play: words missing" }
            val locale = Locale.forLanguageTag(locale)
            DemoTutor(
                language = language,
                play = play,
                locale = locale,
                firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek.toKotlinDayOfWeek(),
                currency = currency,
                scale = scale,
                subjects = subjects,
                students = listOf(
                    DemoStudent(
                        name = students[0], subject = MATH, rate = 30 * scale, from = LocalDate(2026, 7, 1),
                        slots = listOf(Slot(MONDAY, 17, 0, 60), Slot(WEDNESDAY, 14, 0, 60)),
                        phone = phones[0], email = emails[0], homework = homework[0],
                    ),
                    DemoStudent(
                        name = students[1], subject = PHYSICS, rate = 25 * scale, from = LocalDate(2026, 7, 2),
                        slots = listOf(Slot(TUESDAY, 16, 0, 90), Slot(FRIDAY, 16, 0, 60)),
                        phone = phones[1], homework = homework[1], paysUntil = LocalDate(2026, 9, 20),
                    ),
                    DemoStudent(
                        name = students[2], subject = LANGUAGE, rate = 20 * scale, from = LocalDate(2026, 7, 15),
                        slots = listOf(Slot(WEDNESDAY, 15, 0, 60), Slot(SATURDAY, 11, 0, 60)),
                        email = emails[1], homework = homework[2],
                    ),
                    DemoStudent(
                        name = students[3], subject = PHYSICS, rate = 25 * scale, from = LocalDate(2026, 8, 3),
                        slots = listOf(Slot(MONDAY, 19, 0, 60), Slot(WEDNESDAY, 18, 30, 60)),
                        mode = ClassMode.REMOTE, homework = homework[3],
                    ),
                    DemoStudent(
                        name = students[4], subject = MATH, rate = 30 * scale, from = LocalDate(2026, 9, 24),
                        slots = listOf(Slot(THURSDAY, 17, 0, 90)),
                        phone = phones[2], paysUntil = LocalDate(2026, 9, 30),
                    ),
                    DemoStudent(
                        name = students[5], subject = LANGUAGE, rate = 20 * scale, from = LocalDate(2026, 9, 16),
                        slots = listOf(Slot(WEDNESDAY, 11, 0, 60), Slot(FRIDAY, 11, 0, 60)),
                        email = emails[2], prepaysLessons = 8,
                    ),
                ),
                group = DemoGroup(
                    name = group, rate = 12 * scale, from = LocalDate(2026, 9, 16),
                    slots = listOf(Slot(WEDNESDAY, 16, 30, 90), Slot(SATURDAY, 13, 0, 90)),
                    members = members, subject = MATH, owing = members[owing], homework = groupHomework,
                ),
                doneHomework = listOf(students[0], students[2], students[3]).zip(doneHomework),
                typedHomework = typedHomework,
            )
        }
    }
}

/**
 * One language's words for the shared week ([DemoTutor.of]), as its demo.json holds them, in the order its
 * students are booked there: six students, of whom the 1st, 2nd and 5th have a phone, the 1st, 3rd and 6th
 * an email, and the first four homework; four group members, [owing] the one in debt; homework done by the
 * 1st, 3rd and 4th students.
 */
@Serializable
internal data class DemoWords(
    val language: AppLanguage,
    /** The phone's language and region (BCP 47): the money, dates and first day of the week are the region's. */
    val locale: String,
    val currency: Currency,
    /** The shared week's rates times this, so a lesson costs what it would there: 3,000 yen, not 30. */
    val scale: Long = 1,
    val subjects: DemoTutor.Subjects,
    val students: List<String>,
    val phones: List<String>,
    val emails: List<String>,
    val homework: List<String>,
    val group: String,
    val members: List<String>,
    val owing: Int = 1,
    val groupHomework: String,
    val doneHomework: List<String>,
    /** See [DemoTutor.typedHomework]. */
    val typedHomework: String,
)

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
