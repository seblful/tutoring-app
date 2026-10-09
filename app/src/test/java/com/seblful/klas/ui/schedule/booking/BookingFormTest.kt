package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.format.MoneyField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The booking form's own rules, exercised without a dispatcher, a `SavedStateHandle` or six
 * repositories.
 *
 * These rules were the same rules before — they simply lived across eleven mutators on a thousand-line
 * view model, half of them inside `viewModelScope.launch` blocks, so reaching any one of them meant
 * standing up the whole machine. The three staleness checks in particular (a group's rate arriving after
 * the tutor has picked another group; a price arriving after the subject changed; the only-subject answer
 * arriving after the target moved) were `if` guards inside a coroutine that nothing could reach at all.
 */
class BookingFormTest {

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

    // ---- Opening a new lesson ----------------------------------------------------------------------

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
    fun `the only student is chosen, with their mode, as setup`() {
        val opened = BookingForm().after(BookingIntent.OnlyStudent(anya))
        assertEquals(anya.id, opened.form.studentId)
        assertEquals(ClassMode.REMOTE, opened.form.mode)
        assertFalse(opened.touched)
    }

    @Test
    fun `the only student does not overwrite a choice made meanwhile`() {
        assertEquals(
            "a student already picked",
            boris.id,
            BookingForm().after(BookingIntent.ChooseStudent(boris.id), BookingIntent.OnlyStudent(anya)).form.studentId,
        )
        assertNull(
            "a group lesson has no student",
            BookingForm().after(
                BookingIntent.ChooseTarget(ClassTarget.GROUP),
                BookingIntent.OnlyStudent(anya),
            ).form.studentId,
        )
    }

    // ---- The cascade -------------------------------------------------------------------------------

    @Test
    fun `switching target clears what belonged to the other one`() {
        val filled = BookingForm().after(
            BookingIntent.ChooseStudent(anya.id),
            BookingIntent.ChooseSubject(3),
            BookingIntent.Edit { it.copy(price = MoneyField("45")) },
        )
        assertEquals(anya.id, filled.form.studentId)

        val switched = filled.after(BookingIntent.ChooseTarget(ClassTarget.GROUP))

        assertNull("a group is not that student", switched.form.studentId)
        assertNull("a group's subject is not theirs", switched.form.subjectId)
        assertEquals(
            "a group's rate has nothing to do with a student's price for a subject",
            "",
            switched.form.price.text,
        )
    }

    @Test
    fun `choosing a student pre-fills the class mode from their default`() {
        val chosen = BookingForm().after(BookingIntent.ChooseStudent(anya.id))

        assertEquals(ClassMode.REMOTE, chosen.form.mode)
    }

    /** The auto-selection reads the row itself, at a point where the students list may not have emitted. */
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

        assertEquals("a different group is different people", emptySet<Long>(), other.form.excludedMemberIds)
    }

    /** Only one group to choose from means there is nothing to pick. */
    @Test
    fun `switching to groups with a single group chooses it`() {
        val oneGroup = BookingContext(groups = listOf(eleventhA))

        val applied = BookingForm().change(BookingIntent.ChooseTarget(ClassTarget.GROUP), oneGroup)

        assertEquals(eleventhA.id, applied.form.form.groupId)
        assertEquals(
            "and it goes to read that group's rate",
            listOf(Lookup.GroupOf(eleventhA.id)),
            applied.lookups,
        )
    }

    @Test
    fun `a group's rate and subject arrive from the group, and never clobber a typed amount`() {
        val chosen = BookingForm().after(
            BookingIntent.ChooseTarget(ClassTarget.GROUP),
            BookingIntent.ChooseGroup(eleventhA.id),
        )

        val loaded = chosen.after(BookingIntent.GroupLoaded(eleventhA))
        assertEquals("20", loaded.form.price.text)
        assertEquals("a group with a subject decides it", 3L, loaded.form.subjectId)

        val typed = chosen
            .after(BookingIntent.Edit { it.copy(price = MoneyField("35")) })
            .after(BookingIntent.GroupLoaded(eleventhA))
        assertEquals("the tutor's amount stands", "35", typed.form.price.text)
    }

    @Test
    fun `choosing a subject goes to look up the student's price for it, but not a group's`() {
        assertEquals(
            listOf(Lookup.PriceOf(studentId = anya.id, subjectId = 3)),
            lookupsFor(BookingIntent.ChooseStudent(anya.id), BookingIntent.ChooseSubject(3)),
        )
        assertEquals(
            "a group lesson's rate is the group's, whatever the subject",
            emptyList<Lookup>(),
            lookupsFor(
                BookingIntent.ChooseTarget(ClassTarget.GROUP),
                BookingIntent.ChooseGroup(eleventhA.id),
                BookingIntent.ChooseSubject(3),
            ),
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

        val stale = moved.after(BookingIntent.PriceLoaded(subjectId = 3, amount = Money.ofMajor(30)))

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

        assertNull("Boris's subjects are not Anya's", stale.form.subjectId)
    }

    /** And a subject already chosen is never overwritten by one that merely happened to be the only one. */
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

    // ---- Time ---------------------------------------------------------------------------------------

    @Test
    fun `a start with no length yet takes the default lesson`() {
        val started = BookingForm().change(
            BookingIntent.StartAt(LocalTime.of(10, 0)),
            BookingContext(defaultLessonMinutes = 90),
        ).form

        assertEquals(90, started.form.durationMinutes)
        assertEquals(LocalTime.of(11, 30), started.form.ends?.time)
    }

    @Test
    fun `moving the start keeps the length`() {
        val lesson = BookingForm().after(
            BookingIntent.StartAt(LocalTime.of(10, 0)),
            BookingIntent.LastsFor(90),
            BookingIntent.StartAt(LocalTime.of(14, 0)),
        )

        assertEquals(90, lesson.form.durationMinutes)
        assertEquals(LocalTime.of(15, 30), lesson.form.ends?.time)
    }

    /**
     * A length is an answer on its own — it does not need a start to be added to, which is what it needed
     * back when setting it meant writing an end time. So the two can be answered in either order and the
     * form ends up the same, and the screen no longer has to disable the chips until a start exists.
     */
    @Test
    fun `a length may be chosen before the start`() {
        val early = BookingForm().after(BookingIntent.LastsFor(90))

        assertEquals(90, early.form.durationMinutes)
        assertTrue("and it is the tutor's work, so the form is unsaved", early.touched)
        assertNull("with nowhere to end until there is a start", early.form.ends)

        assertEquals(
            "either order, same lesson",
            early.after(BookingIntent.StartAt(LocalTime.of(9, 0))).form,
            BookingForm().after(
                BookingIntent.StartAt(LocalTime.of(9, 0)),
                BookingIntent.LastsFor(90),
            ).form,
        )
    }

    /** A lesson that runs into the next day is an ordinary lesson: 23:00 for an hour ends at 00:00. */
    @Test
    fun `a length that runs past midnight is kept as a length`() {
        val late = BookingForm().after(
            BookingIntent.StartAt(LocalTime.of(23, 0)),
            BookingIntent.LastsFor(60),
        )

        assertEquals(60, late.form.durationMinutes)
        assertEquals(LocalTime.MIDNIGHT, late.form.ends?.time)
        assertEquals(true, late.form.ends?.nextDay)
    }

    // ---- Whose work it was --------------------------------------------------------------------------

    @Test
    fun `the tutor's choices make the form unsaved`() {
        assertTrue(BookingForm().after(BookingIntent.ChooseStudent(anya.id)).touched)
        assertTrue(BookingForm().after(BookingIntent.StartAt(LocalTime.NOON)).touched)
        assertTrue(BookingForm().after(BookingIntent.IncludeMember(boris.id, false)).touched)
    }

    /**
     * And the app's own do not. This is what the four `selectX`/`applyX` pairs existed to express, by
     * being two functions that differed in one line.
     */
    @Test
    fun `loading, pre-selecting and auto-filling leave the form clean`() {
        assertFalse(
            "a lesson opened for editing is not an unsaved edit",
            BookingForm().after(BookingIntent.Load(ClassForm(studentId = anya.id))).touched,
        )
        assertFalse(
            "the group the form was opened from is not a choice the tutor made here",
            BookingForm().after(
                BookingIntent.ChooseTarget(ClassTarget.GROUP).asSetup(),
                BookingIntent.ChooseGroup(eleventhA.id).asSetup(),
            ).touched,
        )
        assertFalse(
            "nor is the only subject there was",
            BookingForm()
                .after(BookingIntent.ChooseStudent(anya.id, known = anya).asSetup())
                .let { it.after(BookingIntent.OnlySubject(SubjectKey.of(it.form), 3)) }
                .touched,
        )
    }

    /** An edit stays an edit, even when a lookup answers after it. */
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
