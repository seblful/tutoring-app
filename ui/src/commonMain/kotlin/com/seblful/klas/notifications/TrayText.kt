package com.seblful.klas.notifications

import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.format.bulletJoin
import com.seblful.klas.format.daySummaryText
import com.seblful.klas.format.forSystemFont
import com.seblful.klas.format.formatTime
import com.seblful.klas.format.money
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts

/**
 * The words of every tray entry, in [words]' language and on the phone's clock ([hour12]) — one source for
 * Android's notifier and iOS's, so the two trays say the same things. Which lessons fit is [TrayContent]'s;
 * channels, icons, actions and taps stay with each platform.
 *
 * Amounts go through [forSystemFont]: the system draws notifications in its own font, which has no
 * Belarusian ruble sign.
 */
class TrayText(private val words: Texts, private val tag: String, private val hour12: Boolean) {

    /**
     * One entry: [text] is the collapsed line; [expanded] the whole of it; [lines] a digest's list, with
     * [more] saying how many it could not name.
     */
    data class Entry(
        val title: String,
        val text: String,
        val expanded: String = text,
        val lines: List<String> = emptyList(),
        val more: String? = null,
    )

    /**
     * The lesson in the collapsed line — what the tray is sorted by at a glance — and the homework to check
     * only in the expanded text, which is what the tutor opens it to read.
     */
    fun reminder(klass: TutoringClass, whoName: String, subjectName: String, homeworkToCheck: String?): Entry {
        val headline = bulletJoin(klass.start.formatTime(hour12, tag), subjectName, whoName)
        val expanded = homeworkToCheck
            ?.let { "$headline\n${words.string(Strings.reminder_homework_to_check, it)}" }
            ?: headline
        return Entry(words.string(Strings.reminder_title), headline, expanded)
    }

    /** The day's summary sentence, as the schedule prints it, over the lessons that fit. */
    fun agenda(agenda: Digests.Agenda, currency: Currency): Entry {
        val summary = daySummaryText(words, agenda.summary, currency, tag).orEmpty()
        return listed(Strings.agenda_title, summary.forSystemFont(), TrayContent.agenda(agenda))
    }

    /** The count, not a total: nothing here has billed, so there is no money to print beside it. */
    fun unfinished(unfinished: Digests.Unfinished): Entry = listed(
        Strings.unfinished_title,
        words.plural(Plurals.unfinished_lessons, unfinished.count, unfinished.count),
        TrayContent.unfinished(unfinished),
    )

    fun debts(debts: Digests.Debts, currency: Currency): Entry = Entry(
        words.string(Strings.debts_title),
        bulletJoin(
            words.plural(Plurals.students_count, debts.students, debts.students),
            debts.total.money(currency, tag),
        ).forSystemFont(),
    )

    fun autoCompleted(lessons: Int, billed: Money, currency: Currency): Entry = Entry(
        words.string(Strings.upkeep_completed_title),
        bulletJoin(words.plural(Plurals.lessons_count, lessons, lessons), billed.money(currency, tag)).forSystemFont(),
    )

    /** A lesson of a listing digest: «09:00 · Algebra · Anna». */
    fun line(entry: Digests.Agenda.Entry): String =
        bulletJoin(entry.start.formatTime(hour12, tag), entry.subject, entry.who)

    private fun listed(title: StringResource, text: String, posting: TrayContent.AgendaPosting) =
        Entry(
            title = words.string(title),
            text = text,
            lines = posting.lines.map(::line),
            more = posting.more.takeIf { it > 0 }?.let { words.string(Strings.agenda_more, it) },
        )
}
