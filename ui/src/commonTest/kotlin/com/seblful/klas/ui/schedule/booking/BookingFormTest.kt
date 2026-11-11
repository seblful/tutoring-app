package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.format.MoneyField
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookingFormTest {
    private val zone = TimeZone.of("Europe/Minsk")

    private val anya = Student(id = 1, name = "Аня", defaultMode = ClassMode.REMOTE)
    private val boris = Student(id = 2, name = "Борис")
    private val eleventhA = Group(id = 7, name = "11 «А»", price = Money.ofMajor(20), subjectId = 3)
    private val evening = Group(id = 8, name = "Вечер", price = Money.ofMajor(25), subjectId = null)

    private val context = BookingContext(
        students = listOf(anya, boris),
        groups = listOf(eleventhA, evening),
        defaultLessonMinutes = 60,
    )

    private fun BookingForm.after(vararg intents: BookingIntent): BookingForm =
        intents.fold(this) { form, intent -> form.change(intent, context).form }

    private fun lookupsFor(vararg intents: BookingIntent): List<Lookup> {
        var form = BookingForm()
        var last = emptyList<Lookup>()
        intents.forEach {
            val applied = form.change(it, context)
            form = applied.form
            last = applied.lookups
        }
        return last
    }

    @Test
    fun `the default length fills an empty form without marking it unsaved`() {
        val opened = BookingForm().after(BookingIntent.DefaultLength(90))
        assertEquals(90, opened.form.durationMinutes)
        assertFalse(opened.touched)
    }

    @Test
    fun `a length the tutor picked while settings loaded outlives the default`() {
        val picked = BookingForm().after(BookingIntent.LastsFor(45), BookingIntent.DefaultLength(90))
        assertEquals(45, picked.form.durationMinutes)
    }

    @Test
    fun `the only student is chosen — with their mode — as setup`() {
        val opened = BookingForm().after(BookingIntent.OnlyStudent(anya))
        assertEquals(anya.id, opened.form.studentId)
        assertEquals(ClassMode.REMOTE, opened.form.mode)
        assertFalse(opened.touched)
    }

    @Test
    fun `the only student does not overwrite a choice made meanwhile`() {
        assertEquals(
            boris.id,
            BookingForm().after(BookingIntent.ChooseStudent(boris.id), BookingIntent.OnlyStudent(anya)).form.studentId,
            "a student already picked",
        )
        assertNull(
            BookingForm().after(
                BookingIntent.ChooseTarget(ClassTarget.GROUP),
                BookingIntent.OnlyStudent(anya),
            ).form.studentId,
            "a group lesson has no student",
        )
    }

    @Test
    fun `switching target clears what belonged to the other one`() {
        val filled = BookingForm().after(
            BookingIntent.ChooseStudent(anya.id),
            BookingIntent.ChooseSubject(3),
            BookingIntent.Edit { it.copy(price = MoneyField("45")) },
        )
        assertEquals(anya.id, filled.form.studentId)

        val switched = filled.after(BookingIntent.ChooseTarget(ClassTarget.GROUP))

        assertNull(switched.form.studentId, "a group is not that student")
        assertNull(switched.form.subjectId, "a group's subject is not theirs")
        assertEquals(
            "",
            switched.form.price.text,
            "a group's rate has nothing to do with a student's price for a subject",
        )
    }

    @Test
    fun `choosing a student pre-fills the class mode from their default`() {
        val chosen = BookingForm().after(BookingIntent.ChooseStudent(anya.id))

        assertEquals(ClassMode.REMOTE, chosen.form.mode)
    }

    @Test
    fun `a student handed in directly is used for the mode`() {
        val empty = BookingContext(students = emptyList())
        val chosen = BookingForm().change(
            BookingIntent.ChooseStudent(anya.id, known = anya),
            empty,
        ).form

        assertEquals(ClassMode.REMOTE, chosen.form.mode)
    }

    @Test
    fun `choosing a group clears the last group's roster ticks`() {
        val ticked = BookingForm().after(
            BookingIntent.ChooseTarget(ClassTarget.GROUP),
            BookingIntent.ChooseGroup(eleventhA.id),
            BookingIntent.IncludeMember(boris.id, included = false),
        )
        assertEquals(setOf(boris.id), ticked.form.excludedMemberIds)

        val other = ticked.after(BookingIntent.ChooseGroup(evening.id))

        assertEquals(emptySet<Long>(), other.form.excludedMemberIds, "a different group is different people")
    }

    /** Only one group to choose from means there is nothing to pick. */
    @Test
    fun `switching to groups with a single group chooses it`() {
        val oneGroup = BookingContext(groups = listOf(eleventhA))

        val applied = BookingForm().change(BookingIntent.ChooseTarget(ClassTarget.GROUP), oneGroup)

        assertEquals(eleventhA.id, applied.form.form.groupId)
        assertEquals(
            listOf(Lookup.GroupOf(eleventhA.id)),
            applied.lookups,
            "and it goes to read that group's rate",
        )
    }

    @Test
    fun `a group's rate and subject arrive from the group — and never clobber a typed amount`() {
        val chosen = BookingForm().after(
            BookingIntent.ChooseTarget(ClassTarget.GROUP),
            BookingIntent.ChooseGroup(eleventhA.id),
        )

        val loaded = chosen.after(BookingIntent.GroupLoaded(eleventhA))
        assertEquals("20", loaded.form.price.text)
        assertEquals(3L, loaded.form.subjectId, "a group with a subject decides it")

        val typed = chosen
            .after(BookingIntent.Edit { it.copy(price = MoneyField("35")) })
            .after(BookingIntent.GroupLoaded(eleventhA))
        assertEquals("35", typed.form.price.text, "the tutor's amount stands")
    }

    @Test
    fun `choosing a subject goes to look up the student's price for it — but not a group's`() {
        assertEquals(
            listOf(Lookup.PriceOf(studentId = anya.id, subjectId = 3)),
            lookupsFor(BookingIntent.ChooseStudent(anya.id), BookingIntent.ChooseSubject(3)),
        )
        assertEquals(
            emptyList<Lookup>(),
            lookupsFor(
                BookingIntent.ChooseTarget(ClassTarget.GROUP),
                BookingIntent.ChooseGroup(eleventhA.id),
                BookingIntent.ChooseSubject(3),
            ),
            "a group lesson's rate is the group's, whatever the subject",
        )
    }

    // ---- Answers that arrive late ------------------------------------------------------------------

    /**
     * Each of these is a database round trip finishing after the tutor has moved on. They were guards
     * inside `viewModelScope.launch` before, which is to say they were untested.
     */
    @Test
    fun `a group's rate is ignored once the form points at another group`() {
        val moved = BookingForm().after(
            BookingIntent.ChooseTarget(ClassTarget.GROUP),
            BookingIntent.ChooseGroup(eleventhA.id),
            BookingIntent.ChooseGroup(evening.id),
        )

        val stale = moved.after(BookingIntent.GroupLoaded(eleventhA))

        assertEquals("", stale.form.price.text)
        assertNull(stale.form.subjectId)
    }

    @Test
    fun `a price is ignored once the subject has changed`() {
        val moved = BookingForm().after(
            BookingIntent.ChooseStudent(anya.id),
            BookingIntent.ChooseSubject(3),
            BookingIntent.ChooseSubject(4),
        )

        val answer = BookingIntent.PriceLoaded(studentId = anya.id, subjectId = 3, amount = Money.ofMajor(30))
        val stale = moved.after(answer)

        assertEquals("", stale.form.price.text)
    }

    @Test
    fun `the only-subject answer is ignored once the target has moved`() {
        val asked = SubjectKey(ClassTarget.STUDENT, studentId = anya.id, groupId = null)
        val moved = BookingForm().after(
            BookingIntent.ChooseStudent(anya.id),
            BookingIntent.ChooseStudent(boris.id),
        )

        val stale = moved.after(BookingIntent.OnlySubject(asked, subjectId = 3))

        assertNull(stale.form.subjectId, "Boris's subjects are not Anya's")
    }

    @Test
    fun `the only-subject answer does not overwrite a subject already chosen`() {
        val chosen = BookingForm().after(
            BookingIntent.ChooseStudent(anya.id),
            BookingIntent.ChooseSubject(4),
        )
        val key = SubjectKey.of(chosen.form)

        val applied = chosen.after(BookingIntent.OnlySubject(key, subjectId = 3))

        assertEquals(4L, applied.form.subjectId)
    }

    @Test
    fun `a start with no length yet takes the default lesson`() {
        val started = BookingForm().change(
            BookingIntent.StartAt(LocalTime(10, 0)),
            BookingContext(defaultLessonMinutes = 90),
        ).form

        assertEquals(90, started.form.durationMinutes)
        assertEquals(LocalTime(11, 30), started.form.ends(zone)?.time)
    }

    @Test
    fun `moving the start keeps the length`() {
        val lesson = BookingForm().after(
            BookingIntent.StartAt(LocalTime(10, 0)),
            BookingIntent.LastsFor(90),
            BookingIntent.StartAt(LocalTime(14, 0)),
        )

        assertEquals(90, lesson.form.durationMinutes)
        assertEquals(LocalTime(15, 30), lesson.form.ends(zone)?.time)
    }

    @Test
    fun `a length may be chosen before the start`() {
        val early = BookingForm().after(BookingIntent.LastsFor(90))

        assertEquals(90, early.form.durationMinutes)
        assertTrue(early.touched, "and it is the tutor's work, so the form is unsaved")
        assertNull(early.form.ends(zone), "with nowhere to end until there is a start")

        assertEquals(
            early.after(BookingIntent.StartAt(LocalTime(9, 0))).form,
            BookingForm().after(
                BookingIntent.StartAt(LocalTime(9, 0)),
                BookingIntent.LastsFor(90),
            ).form,
            "either order, same lesson",
        )
    }

    @Test
    fun `a length that runs past midnight is kept as a length`() {
        val late = BookingForm().after(
            BookingIntent.StartAt(LocalTime(23, 0)),
            BookingIntent.LastsFor(60),
        )

        assertEquals(60, late.form.durationMinutes)
        assertEquals(LocalTime(0, 0), late.form.ends(zone)?.time)
        assertEquals(true, late.form.ends(zone)?.nextDay)
    }

    @Test
    fun `the tutor's choices make the form unsaved`() {
        assertTrue(BookingForm().after(BookingIntent.ChooseStudent(anya.id)).touched)
        assertTrue(BookingForm().after(BookingIntent.StartAt(LocalTime(12, 0))).touched)
        assertTrue(BookingForm().after(BookingIntent.IncludeMember(boris.id, false)).touched)
    }

    /**
     * And the app's own do not. This is what the four `selectX`/`applyX` pairs existed to express, by
     * being two functions that differed in one line.
     */
    @Test
    fun `loading — pre-selecting and auto-filling leave the form clean`() {
        assertFalse(
            BookingForm().after(BookingIntent.Load(ClassForm(studentId = anya.id))).touched,
            "a lesson opened for editing is not an unsaved edit",
        )
        assertFalse(
            BookingForm().after(
                BookingIntent.ChooseTarget(ClassTarget.GROUP).asSetup(),
                BookingIntent.ChooseGroup(eleventhA.id).asSetup(),
            ).touched,
            "the group the form was opened from is not a choice the tutor made here",
        )
        assertFalse(
            BookingForm()
                .after(BookingIntent.ChooseStudent(anya.id, known = anya).asSetup())
                .let { it.after(BookingIntent.OnlySubject(SubjectKey.of(it.form), 3)) }
                .touched,
            "nor is the only subject there was",
        )
    }

    @Test
    fun `an answer arriving after an edit does not make the form clean again`() {
        val edited = BookingForm().after(
            BookingIntent.ChooseTarget(ClassTarget.GROUP),
            BookingIntent.ChooseGroup(eleventhA.id),
            BookingIntent.GroupLoaded(eleventhA),
        )

        assertTrue(edited.touched)
    }
}
