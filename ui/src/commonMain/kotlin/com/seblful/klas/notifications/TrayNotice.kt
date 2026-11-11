package com.seblful.klas.notifications

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.notifications.TrayNavigation.Destination
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import kotlinx.datetime.LocalDate

/** The five kinds the app posts. Android gives each a channel, iOS a thread; they differ in urgency. */
enum class TrayKind { REMINDER, AGENDA, UNFINISHED, DEBTS, UPKEEP }

/** A button on a notification. */
sealed interface TrayButton {
    val label: StringResource

    /** Marks the lesson taught without opening the app (`ReminderActions.complete`). It bills, so it asks for the unlock. */
    data class Complete(val classId: Long) : TrayButton {
        override val label: StringResource get() = Strings.class_status_completed
    }

    /** Opens the app at [destination]. */
    data class Open(override val label: StringResource, val destination: Destination) : TrayButton
}

/**
 * One notification as both trays show it: its words, where a tap goes and what its buttons do. Decided once
 * ([TrayNotices]), so a platform only draws it and the two trays cannot drift apart.
 *
 * @param classId the lesson a reminder is about; null for the rest, each of which replaces its predecessor.
 */
data class TrayNotice(
    val kind: TrayKind,
    val entry: TrayText.Entry,
    val tap: Destination,
    val buttons: List<TrayButton> = emptyList(),
    val classId: Long? = null,
)

/** Every notice the app posts, in [text]'s words. */
class TrayNotices(private val text: TrayText) {

    /**
     * A tap shows the lesson's sheet on the schedule. Its buttons are the two things a tutor does about a lesson
     * they have just been reminded of: it happened, or it has to move — and a move needs the lesson's form.
     */
    fun reminder(klass: TutoringClass, whoName: String, subjectName: String, homeworkToCheck: String?) = TrayNotice(
        kind = TrayKind.REMINDER,
        entry = text.reminder(klass, whoName, subjectName, homeworkToCheck),
        tap = Destination.LessonOnSchedule(klass.id),
        buttons = listOf(
            TrayButton.Complete(klass.id),
            TrayButton.Open(Strings.class_move, Destination.Lesson(klass.id)),
        ),
        classId = klass.id,
    )

    /** The day it is about, where each lesson is one swipe from being marked. */
    fun agenda(agenda: Digests.Agenda, currency: Currency) =
        TrayNotice(TrayKind.AGENDA, text.agenda(agenda, currency), Destination.Day(agenda.day))

    /** No buttons: «Проведено» on a list of lessons could only guess, and it bills. */
    fun unfinished(unfinished: Digests.Unfinished) =
        TrayNotice(TrayKind.UNFINISHED, text.unfinished(unfinished), Destination.Day(unfinished.day))

    /** The report that lists who owes; the tray can say how much, not by whom. */
    fun debts(debts: Digests.Debts, currency: Currency) =
        TrayNotice(TrayKind.DEBTS, text.debts(debts, currency), Destination.Debts)

    /** The latest day settled, where a lesson that did not happen can be corrected. */
    fun autoCompleted(lessons: Int, billed: Money, day: LocalDate, currency: Currency) =
        TrayNotice(TrayKind.UPKEEP, text.autoCompleted(lessons, billed, currency), Destination.Day(day))
}

/**
 * [Notifier] as both platforms post: every notice is built by [TrayNotices] in the app's language and handed to
 * [post], which is all a platform writes besides its channels and cancelling.
 */
abstract class TrayNotifier(private val displayLocale: DisplayLocale) : Notifier {

    /** Whether the phone's clock reads 12-hour; a reminder is read on the phone, in its format. */
    protected abstract fun hour12(): Boolean

    /** Shows [notice]. Best-effort: no permission is a silent no-op, never a failure. */
    protected abstract fun post(notice: TrayNotice)

    private fun notices() = TrayNotices(TrayText(displayLocale.texts, displayLocale.tag, hour12()))

    final override fun showClassReminder(
        klass: TutoringClass,
        whoName: String,
        subjectName: String,
        homeworkToCheck: String?,
    ) = post(notices().reminder(klass, whoName, subjectName, homeworkToCheck))

    final override fun showAgenda(agenda: Digests.Agenda, currency: Currency) =
        post(notices().agenda(agenda, currency))

    final override fun showUnfinished(unfinished: Digests.Unfinished) = post(notices().unfinished(unfinished))

    final override fun showDebts(debts: Digests.Debts, currency: Currency) = post(notices().debts(debts, currency))

    final override fun showAutoCompleted(lessons: Int, billed: Money, day: LocalDate, currency: Currency) =
        post(notices().autoCompleted(lessons, billed, day, currency))
}
