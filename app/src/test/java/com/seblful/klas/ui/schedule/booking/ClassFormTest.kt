package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.format.MoneyField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The booking form's model and the answers derived from it — [ClassForm], [ClassFormState] and the
 * clash they report — without the view model around them, so without a dispatcher or a database.
 */
class ClassFormTest {
    private val zone = ZoneId.of("Europe/Minsk")

    // --- ClassForm validation (pure) ---

    @Test
    fun `form is invalid until every required field is set`() {
        val empty = ClassForm()
        assertFalse(empty.isValid)
        assertNull(empty.durationMinutes)

        val full = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(10, 0),
            durationMinutes = 60,
            price = MoneyField("30"),
        )
        assertTrue(full.isValid)
        assertEquals(LocalTime.of(11, 0), full.ends(zone)?.time)
    }

    /**
     * A length of zero is a lesson that does not happen, and the form treats it as not filled in rather
     * than passing it to [Scheduling.Slot], which would throw. Nothing in the app produces one — the
     * chips are positive and «Другая» will not confirm at zero — so this is the guard, not a path.
     */
    @Test
    fun `a lesson of no length is not a lesson`() {
        val empty = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(11, 0),
            durationMinutes = 0,
            price = MoneyField("30"),
        )
        assertFalse(empty.isValid)
        assertNull(empty.ends(zone))
        assertNull(empty.slot(ZoneId.of("Europe/Minsk")))
    }

    /**
     * The end is arithmetic on the start *instant*, so a late lesson simply finishes tomorrow. It used to
     * be a second wall clock on the same date, which made 23:00 + 1 ч read as a negative range: see
     * `EdgeCaseCombinationsTest`, where the limitation this replaced was pinned.
     */
    @Test
    fun `a lesson that runs past midnight ends the next day`() {
        val late = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate.of(2026, 6, 20),
            timeFrom = LocalTime.of(23, 0),
            durationMinutes = 90,
            price = MoneyField("30"),
        )
        assertTrue(late.isValid)
        assertEquals(LocalTime.of(0, 30), late.ends(zone)?.time)
        assertEquals(true, late.ends(zone)?.nextDay)
    }

    /** The flag is about passing midnight, not about the clock reading less than the start. */
    @Test
    fun `a lesson ending exactly at midnight is the next day, one ending before it is not`() {
        val toMidnight = ClassForm(timeFrom = LocalTime.of(23, 0), durationMinutes = 60)
        assertEquals(LocalTime.MIDNIGHT, toMidnight.ends(zone)?.time)
        assertEquals(true, toMidnight.ends(zone)?.nextDay)

        val justBefore = ClassForm(timeFrom = LocalTime.of(23, 0), durationMinutes = 59)
        assertEquals(LocalTime.of(23, 59), justBefore.ends(zone)?.time)
        assertEquals(false, justBefore.ends(zone)?.nextDay)
    }

    // --- Decisions the screen used to make for itself ---

    private fun stateWith(
        form: ClassForm = ClassForm(),
        subjects: List<Subject> = emptyList(),
        members: List<Student> = emptyList(),
        groups: List<Group> = emptyList(),
        subjectFixedByGroup: Boolean = false,
        conflict: TimeConflict? = null,
    ) = ClassFormState(
        form = form,
        subjects = subjects,
        members = members,
        groups = groups,
        subjectFixedByGroup = subjectFixedByGroup,
        conflict = conflict,
    )

    private val validForm = ClassForm(
        studentId = 1, subjectId = 1,
        date = LocalDate.of(2026, 6, 20),
        timeFrom = LocalTime.of(10, 0),
        durationMinutes = 60,
        price = MoneyField("30"),
    )

    private val someoneElse = TimeConflict(
        slot = Scheduling.Slot(Instant.parse("2026-06-20T07:00:00Z"), 60),
        who = "Борис",
    )

    /**
     * A clash is named the way a reminder is announced: by its group, by its student when the group's name
     * is blank — `groupName ?: studentName` kept a blank name and refused the time with nobody named — and
     * by nobody only when neither is known.
     */
    @Test
    fun `a clash is named by its group, or by its student when the group has no name`() {
        val start = Instant.parse("2026-06-20T07:00:00Z")
        val clash = ClassSession(
            groupId = 7,
            classes = listOf(
                TutoringClass(studentId = 1, subjectId = 1, start = start, durationMinutes = 60, groupId = 7),
            ),
        )

        assertEquals("11 «А»", TimeConflict.of(clash, groupName = "11 «А»", studentName = "Аня").who)
        assertEquals("Аня", TimeConflict.of(clash, groupName = " ", studentName = "Аня").who)
        assertEquals("Аня", TimeConflict.of(clash, groupName = null, studentName = "Аня").who)
        assertEquals("", TimeConflict.of(clash, groupName = null, studentName = null).who)
        assertEquals(Scheduling.Slot(start, 60), TimeConflict.of(clash, null, "Аня").slot)
    }

    /**
     * A clash is now the only thing that can be wrong about a chosen time, so it is also the only thing
     * that can stop a save. There used to be a `TimeProblem` deciding which of two complaints to show.
     */
    @Test
    fun `a taken slot stops the save even though the form itself is complete`() {
        val state = stateWith(form = validForm, conflict = someoneElse)

        assertTrue(state.form.isValid)
        assertFalse(state.canSave)
        assertTrue(stateWith(form = validForm).canSave)
    }

    @Test
    fun `a half-filled time is incomplete rather than wrong`() {
        // Only a start picked: nothing to complain about, and nothing to save either.
        val state = stateWith(form = ClassForm(timeFrom = LocalTime.of(10, 0)))

        assertNull(state.conflict)
        assertFalse(state.canSave)
    }

    @Test
    fun `the subject picker is dead until somebody is chosen`() {
        assertFalse(stateWith(form = ClassForm()).subjectPickerEnabled)
        assertTrue(stateWith(form = ClassForm(studentId = 1)).subjectPickerEnabled)
    }

    @Test
    fun `a group that fixes the subject leaves nothing to pick`() {
        val state = stateWith(
            form = ClassForm(target = ClassTarget.GROUP, groupId = 1),
            subjectFixedByGroup = true,
        )
        assertFalse(state.subjectPickerEnabled)
        assertEquals(SubjectNotice.FIXED_BY_GROUP, state.subjectNotice)
    }

    @Test
    fun `an empty subject list is explained differently for a student and a group`() {
        assertEquals(
            SubjectNotice.NONE_FOR_STUDENT,
            stateWith(form = ClassForm(studentId = 1)).subjectNotice,
        )
        assertEquals(
            SubjectNotice.NONE_FOR_GROUP,
            stateWith(form = ClassForm(target = ClassTarget.GROUP, groupId = 1)).subjectNotice,
        )
    }

    @Test
    fun `no notice is shown before a target is chosen, or once subjects exist`() {
        assertNull(stateWith(form = ClassForm()).subjectNotice)
        assertNull(
            stateWith(
                form = ClassForm(studentId = 1),
                subjects = listOf(Subject(id = 1, name = "Математика")),
            ).subjectNotice,
        )
    }

    @Test
    fun `unticking every member is recognised, and blocks the save`() {
        val ann = Student(id = 1, name = "Аня")
        val bob = Student(id = 2, name = "Борис")
        val form = validForm.copy(
            target = ClassTarget.GROUP, studentId = null, groupId = 7,
            excludedMemberIds = setOf(ann.id, bob.id),
        )
        val state = stateWith(form = form, members = listOf(ann, bob))

        assertTrue(state.allMembersExcluded)
        assertFalse("a lesson billing nobody cannot be saved", state.canSave)

        val oneLeft = stateWith(form = form.copy(excludedMemberIds = setOf(ann.id)), members = listOf(ann, bob))
        assertFalse(oneLeft.allMembersExcluded)
        assertTrue(oneLeft.canSave)
    }

    @Test
    fun `an individual lesson is never all-excluded`() {
        assertFalse(stateWith(form = validForm).allMembersExcluded)
    }

    @Test
    fun `a taken slot disables the save just as a missing field does`() {
        assertTrue(stateWith(form = validForm).canSave)
        assertFalse(stateWith(form = validForm, conflict = someoneElse).canSave)
        assertFalse(stateWith(form = validForm.copy(subjectId = null)).canSave)
    }

    @Test
    fun `booking for a group says so when there are no groups`() {
        assertTrue(stateWith(form = ClassForm(target = ClassTarget.GROUP)).hasNoGroups)
        assertFalse(
            stateWith(
                form = ClassForm(target = ClassTarget.GROUP),
                groups = listOf(Group(id = 1, name = "11 «А»", price = Money.ofMajor(20))),
            ).hasNoGroups,
        )
        assertFalse("a student booking has no opinion about groups", stateWith().hasNoGroups)
    }
}
