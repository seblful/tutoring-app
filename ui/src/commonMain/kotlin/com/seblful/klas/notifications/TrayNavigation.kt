package com.seblful.klas.notifications

import com.seblful.klas.ACTION_PREFIX
import kotlinx.datetime.LocalDate

/**
 * Where a tap in the notification tray puts the tutor — **one rule for every notification the app posts**,
 * and for the home-screen widgets (`widget/`), which open the app the same way.
 *
 * What a *move* is stays unencoded: «Перенести» needs a screen a notification cannot draw, so the tutor
 * is put in front of the lesson, where a date and time are changed and where a clash on the new hour is
 * already refused.
 *
 * **Every entry asks for somewhere.** A tap on «занятие через час» must land on that lesson, not on
 * whatever tab the app was left on.
 *
 * [destinationFor] is a function of plain values rather than of an `Intent`, and that is the whole
 * point of it: an `Intent` can only be built on a device, so "which intents mean go somewhere" is exactly
 * the kind of question that ends up decided inside a composable with no test. The app is opened by a
 * launcher intent far more often than by a notification, and answering that one wrongly would yank the
 * tutor onto a lesson every single time they open the app.
 *
 * A **sealed** answer rather than a nullable id per destination, so adding a further tray entry cannot
 * quietly add another boolean to the composable that reads this: every arm has to be handled there.
 */
object TrayNavigation {

    /**
     * The action strings ride in `PendingIntent`s that may already be sitting in a tray, so their
     * **values** are not free to change.
     */
    const val ACTION_OPEN_LESSON = "${ACTION_PREFIX}OPEN_LESSON"
    const val ACTION_OPEN_DEBTS = "${ACTION_PREFIX}OPEN_DEBTS"
    const val ACTION_OPEN_TODAY = "${ACTION_PREFIX}OPEN_TODAY"
    const val ACTION_OPEN_DAY = "${ACTION_PREFIX}OPEN_DAY"
    const val ACTION_SHOW_LESSON = "${ACTION_PREFIX}SHOW_LESSON"
    const val ACTION_BOOK_LESSON = "${ACTION_PREFIX}BOOK_LESSON"
    const val ACTION_OPEN_PEOPLE = "${ACTION_PREFIX}OPEN_PEOPLE"
    const val ACTION_OPEN_HOMEWORK = "${ACTION_PREFIX}OPEN_HOMEWORK"

    /** The tabs and the page a keyboard chord can open besides the tray's — see `KeyboardShortcuts`. */
    const val ACTION_OPEN_SCHEDULE = "${ACTION_PREFIX}OPEN_SCHEDULE"
    const val ACTION_OPEN_REPORTS = "${ACTION_PREFIX}OPEN_REPORTS"
    const val ACTION_OPEN_SETTINGS = "${ACTION_PREFIX}OPEN_SETTINGS"

    /** The scheme of the app's own links: `klas://today`, `klas://lesson/42` — see [destinationForLink]. */
    const val LINK_SCHEME = "klas"

    /** The day an [ACTION_OPEN_DAY] entry is about, as `LocalDate.toEpochDays()`. */
    const val EXTRA_DAY = "day"

    /**
     * The extra every notification intent carries its lesson in — **declared once**.
     *
     * The value is inside `PendingIntent`s already sitting in people's trays, so it is not free to change;
     * the activity and both receivers read it from here.
     */
    const val EXTRA_CLASS_ID = "classId"

    /**
     * The lesson an intent names, or **null** when it names none — the one definition of "is this a real
     * class id", for the activity and both `BroadcastReceiver`s.
     *
     * Row ids are positive `AUTOINCREMENT` values, so anything else is a missing extra arriving as whatever
     * default its reader happened to pass. Zero must be refused too: it is the value the booking form reads
     * as "a new lesson". Readers pass the raw extra here rather than testing `< 0` themselves.
     */
    fun lessonIdOrNull(raw: Long): Long? = raw.takeIf { it > 0 }

    /** Somewhere a notification can send the tutor. */
    sealed interface Destination {

        /** The lesson's own form — «Перенести» on a reminder. */
        data class Lesson(val classId: Long) : Destination

        /**
         * The schedule on the day the lesson is on **now**, with that lesson's sheet open — a tap on a
         * reminder itself. The sheet is where the lesson is acted on (its status, the homework the reminder
         * named, a move), and it is what tapping the lesson's card opens; the form is for changing it.
         *
         * An id rather than a day, because a lesson can be moved after its reminder was posted.
         */
        data class LessonOnSchedule(val classId: Long) : Destination

        /**
         * The reports tab, scrolled to its debts — where each row opens the student who owes. The digest
         * can say how much is owed in total and nothing at all about by whom, so it hands over to the
         * screen that can.
         */
        data object Debts : Destination

        /** The schedule on today, opened by the lesson-reminder summary. */
        data object Today : Destination

        /**
         * The schedule on [date] — the day a digest is about, or the latest day auto-completion settled.
         * Carried rather than read as today, because a digest tapped after midnight is still about
         * yesterday, and today would be a page with none of its lessons on it.
         */
        data class Day(val date: LocalDate) : Destination

        /** An empty booking form — a widget's «Записать занятие». */
        data object NewLesson : Destination

        /** The homework tab. */
        data object Homework : Destination

        /** The People tab — the widget's «Люди», where a balance, a payment or a contact is one tap on. */
        data object People : Destination

        /** The schedule tab as it was left. */
        data object Schedule : Destination

        /** The reports tab, at its top. */
        data object Reports : Destination

        /** Settings, over the tab the tutor was on. */
        data object Settings : Destination

        /** A student's profile, on the People tab — a `klas://student/<id>` link. */
        data class Student(val studentId: Long) : Destination
    }

    /**
     * Where this intent asks the app to go, or **null** when it asks for nothing — which covers the
     * launcher and an intent whose id did not survive.
     *
     * @param classId the extra as read, so a missing one is the caller's default (0 or -1) and is refused
     *   here rather than being navigated to as lesson zero — which the booking form reads as "new lesson".
     * @param epochDay [EXTRA_DAY] as read, or null when the intent has none. A day that is missing or
     *   outside the years the schedule can draw is read as today: the activity is exported, as the launcher's is,
     *   so the value is not only this app's to write, and a number it cannot be a date must not crash the start.
     * @param fromHistory the intent carries `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`: Recents relaunching a
     *   task whose activity Back finished (Android 11 and below), with the intent that *started* the task.
     *   If a notification started it, the tap was answered then, so it must not be replayed.
     * @param link the intent's data, read only for a VIEW intent — see [destinationForLink].
     */
    fun destinationFor(
        action: String?,
        classId: Long,
        epochDay: Long? = null,
        fromHistory: Boolean = false,
        link: String? = null,
    ): Destination? = if (fromHistory) null else when (action) {
        ACTION_VIEW -> destinationForLink(link)
        ACTION_OPEN_LESSON -> lessonIdOrNull(classId)?.let { Destination.Lesson(it) }
        ACTION_SHOW_LESSON -> lessonIdOrNull(classId)?.let { Destination.LessonOnSchedule(it) }
        ACTION_OPEN_DAY ->
            epochDay
                ?.let { runCatching { LocalDate.fromEpochDays(it) }.getOrNull()?.drawable() }
                ?.let { Destination.Day(it) }
                ?: Destination.Today
        else -> fixed[action]
    }

    /**
     * Where a `klas://` link goes, or null for one this app does not know. Only ever somewhere to look:
     * the activity is exported and a link can come from any app or web page, so no link writes anything —
     * `klas://book` opens an empty form, it does not book.
     *
     * `klas://today`, `schedule`, `people`, `homework`, `reports`, `debts`, `book`, `settings`,
     * `lesson/<id>` (its sheet on the schedule), `day/<yyyy-mm-dd>` and `student/<id>` (their profile).
     */
    fun destinationForLink(link: String?): Destination? {
        val path = link?.takeIf { it.startsWith("$LINK_SCHEME://") }
            ?.removePrefix("$LINK_SCHEME://")
            ?.substringBefore('?')
            ?.trim('/')
            ?.split('/')
            ?: return null
        val argument = path.getOrNull(1)
        return when (path.first()) {
            "lesson" -> argument?.toLongOrNull()?.let(::lessonIdOrNull)?.let { Destination.LessonOnSchedule(it) }
            "day" -> argument?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.drawable() }
                ?.let { Destination.Day(it) }
            "student" -> argument?.toLongOrNull()?.takeIf { it > 0 }?.let { Destination.Student(it) }
            else -> linked[path.first()]
        }
    }

    /** The years the schedule can lay a month out in; its grid reaches past the day it is opened on. */
    private val DRAWABLE_DAYS = LocalDate(1900, 1, 1)..LocalDate(9999, 12, 31)

    private fun LocalDate.drawable(): LocalDate? = takeIf { it in DRAWABLE_DAYS }

    /** A place that carries nothing but its name, reachable by its intent action and by its `klas://` link. */
    private class Place(val action: String, val link: String, val destination: Destination)

    private val places = listOf(
        Place(ACTION_OPEN_DEBTS, "debts", Destination.Debts),
        Place(ACTION_OPEN_TODAY, "today", Destination.Today),
        Place(ACTION_BOOK_LESSON, "book", Destination.NewLesson),
        Place(ACTION_OPEN_HOMEWORK, "homework", Destination.Homework),
        Place(ACTION_OPEN_PEOPLE, "people", Destination.People),
        Place(ACTION_OPEN_SCHEDULE, "schedule", Destination.Schedule),
        Place(ACTION_OPEN_REPORTS, "reports", Destination.Reports),
        Place(ACTION_OPEN_SETTINGS, "settings", Destination.Settings),
    )

    private val fixed: Map<String, Destination> = places.associate { it.action to it.destination }
    private val linked: Map<String, Destination> = places.associate { it.link to it.destination }
}

/** Android's `Intent.ACTION_VIEW`: a `klas://` link opened from another app or a web page. */
internal const val ACTION_VIEW = "android.intent.action.VIEW"
