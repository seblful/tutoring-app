package com.seblful.klas.widget

import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.format.formatTime
import com.seblful.klas.queries.lessons.NamedLesson
import com.seblful.klas.queries.lessons.TodayLessonsQuery
import com.seblful.klas.text.StringArrays
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts
import com.seblful.klas.text.formatText
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * One row of the Today widget — Android's and iOS's: a whole lesson, so a group lesson is one row, as on the
 * schedule.
 */
data class TodayLesson(
    /** The lesson's first class — what opening it and marking it taught address. */
    val classId: Long,
    val start: Instant,
    val end: Instant,
    /** The group's name for a group lesson, the student's for an individual one ([Captions.titleOf]). */
    val who: String,
    val subject: String,
    val taught: Boolean,
    /** Whether the check can mark it taught: the transitions the schedule's own menu allows. */
    val canMarkTaught: Boolean,
) {
    /** Being taught at [now]: started, not over, not yet marked — the Day view's «On now». */
    fun isOnAt(now: Instant): Boolean = !taught && now >= start && now < end
}

/** Today's lessons for the widget, re-read when the lessons, the names on them, or the date change. */
@Inject
class TodayLessons(private val query: TodayLessonsQuery) {
    fun observe(): Flow<List<TodayLesson>> = query.observe()
        .map { lessons -> lessons.map(NamedLesson::toTodayLesson) }
        .distinctUntilChanged()

    companion object {
        /**
         * When [lessons] next change what the widget says after [now] — a lesson starting or ending, which
         * moves «On now» — or null when none is left today.
         */
        fun nextChange(lessons: List<TodayLesson>, now: Instant): Instant? =
            lessons.flatMap { listOf(it.start, it.end) }.filter { it > now }.minOrNull()
    }
}

/** [this] as a widget row states it. */
fun NamedLesson.toTodayLesson(): TodayLesson = TodayLesson(
    classId = session.first.id,
    start = session.start,
    end = session.first.end,
    who = who,
    subject = subject,
    taught = session.status == LessonStatus.COMPLETED,
    canMarkTaught = LessonStatus.COMPLETED in SessionRules.sessionTransitions(session),
)

/** One lesson as the Today widget draws it. */
data class TodayRow(
    val classId: Long,
    /** Who the lesson is with. */
    val title: String,
    /** When, and in what: «10:00–11:00 · Математика». */
    val supportingText: String,
    val taught: Boolean,
    val canMarkTaught: Boolean,
    /** Being taught now: said before the time, as the Day view does. */
    val onNow: Boolean = false,
    /** What the tick says, naming its lesson: «Mark Olivia Brooks, 15:00 taught». */
    val tickLabel: String = "",
)

/** What the Today widget says, in the app's language rather than the phone's. */
data class TodayTexts(
    val title: String,
    val empty: String,
    val bookLesson: String,
    val markTaught: String,
    val taught: String,
    val onNow: String = "",
    /** «Mark %1$s, %2$s taught»: who, and when it starts. */
    val markLesson: String = "%1\$s, %2\$s",
    /** «%1$s, %2$s: taught». */
    val lessonTaught: String = "%1\$s, %2\$s",
)

/** [TodayTexts] from [this]. */
fun Texts.todayTexts() = TodayTexts(
    title = string(Strings.schedule_today),
    empty = string(Strings.widget_today_empty),
    bookLesson = string(Strings.schedule_add_class),
    markTaught = string(Strings.class_mark_completed),
    taught = string(Strings.class_status_completed),
    onNow = string(Strings.class_status_in_progress),
    markLesson = string(Strings.widget_mark_lesson),
    lessonTaught = string(Strings.widget_lesson_taught),
)

/** The picker's sample day, in [this] language: the first lesson taught. */
fun Texts.sampleRows(hour12: Boolean, tag: String): List<TodayRow> {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return array(StringArrays.widget_preview_lessons).chunked(2)
        .zip(listOf("09:00", "14:30", "17:00").map(LocalTime::parse))
        .mapIndexed { index, (lesson, time) ->
            val start = today.atTime(time).toInstant(TimeZone.currentSystemDefault())
            TodayLesson(
                classId = index.toLong(),
                start = start,
                end = start + 1.hours,
                who = lesson[0],
                subject = lesson[1],
                taught = index == 0,
                canMarkTaught = index != 0,
            ).toRow(hour12, tag, now = Instant.DISTANT_PAST, texts = todayTexts())
        }
}

/** The row for this lesson as it stands at [now]: «10:00–11:00 · Maths», «On now» while it is taught. */
fun TodayLesson.toRow(hour12: Boolean, tag: String, now: Instant, texts: TodayTexts): TodayRow {
    val starts = start.formatTime(hour12, tag)
    val range = "$starts–${end.formatTime(hour12, tag)}"
    return TodayRow(
        classId = classId,
        title = who,
        supportingText = listOf(range, subject).filter { it.isNotBlank() }.joinToString(" · "),
        taught = taught,
        canMarkTaught = canMarkTaught,
        onNow = isOnAt(now),
        tickLabel = formatText(if (taught) texts.lessonTaught else texts.markLesson, tag, arrayOf(who, starts)),
    )
}
