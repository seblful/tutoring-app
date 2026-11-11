package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.format.MoneyField
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ClassFormTest {
    private val zone = TimeZone.of("Europe/Minsk")

    @Test
    fun `form is invalid until every required field is set`() {
        val empty = ClassForm()
        assertFalse(empty.isValid)
        assertNull(empty.durationMinutes)

        val full = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate(2026, 6, 20),
            timeFrom = LocalTime(10, 0),
            durationMinutes = 60,
            price = MoneyField("30"),
        )
        assertTrue(full.isValid)
        assertEquals(LocalTime(11, 0), full.ends(zone)?.time)
    }

    @Test
    fun `a lesson of no length is not a lesson`() {
        val empty = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate(2026, 6, 20),
            timeFrom = LocalTime(11, 0),
            durationMinutes = 0,
            price = MoneyField("30"),
        )
        assertFalse(empty.isValid)
        assertNull(empty.ends(zone))
        assertNull(empty.slot(TimeZone.of("Europe/Minsk")))
    }

    @Test
    fun `a lesson that runs past midnight ends the next day`() {
        val late = ClassForm(
            studentId = 1, subjectId = 1,
            date = LocalDate(2026, 6, 20),
            timeFrom = LocalTime(23, 0),
            durationMinutes = 90,
            price = MoneyField("30"),
        )
        assertTrue(late.isValid)
        assertEquals(LocalTime(0, 30), late.ends(zone)?.time)
        assertEquals(true, late.ends(zone)?.nextDay)
    }

    @Test
    fun `a lesson ending exactly at midnight is the next day — one ending before it is not`() {
        val toMidnight = ClassForm(timeFrom = LocalTime(23, 0), durationMinutes = 60)
        assertEquals(LocalTime(0, 0), toMidnight.ends(zone)?.time)
        assertEquals(true, toMidnight.ends(zone)?.nextDay)

        val justBefore = ClassForm(timeFrom = LocalTime(23, 0), durationMinutes = 59)
        assertEquals(LocalTime(23, 59), justBefore.ends(zone)?.time)
        assertEquals(false, justBefore.ends(zone)?.nextDay)
    }

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
        date = LocalDate(2026, 6, 20),
        timeFrom = LocalTime(10, 0),
        durationMinutes = 60,
        price = MoneyField("30"),
    )

    private val someoneElse = TimeConflict(
        slot = Scheduling.Slot(Instant.parse("2026-06-20T07:00:00Z"), 60),
        who = "Борис",
    )

    @Test
    fun `a clash is named by its group — or by its student when the group has no name`() {
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

    @Test
    fun `a taken slot stops the save even though the form itself is complete`() {
        val state = stateWith(form = validForm, conflict = someoneElse)

        assertTrue(state.form.isValid)
        assertFalse(state.canSave)
        assertTrue(stateWith(form = validForm).canSave)
    }

    @Test
    fun `a half-filled time is incomplete rather than wrong`() {
        val state = stateWith(form = ClassForm(timeFrom = LocalTime(10, 0)))

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
    fun `no notice is shown before a target is chosen — or once subjects exist`() {
        assertNull(stateWith(form = ClassForm()).subjectNotice)
        assertNull(
            stateWith(
                form = ClassForm(studentId = 1),
                subjects = listOf(Subject(id = 1, name = "Математика")),
            ).subjectNotice,
        )
    }

    @Test
    fun `unticking every member is recognised — and blocks the save`() {
        val ann = Student(id = 1, name = "Аня")
        val bob = Student(id = 2, name = "Борис")
        val form = validForm.copy(
            target = ClassTarget.GROUP, studentId = null, groupId = 7,
            excludedMemberIds = setOf(ann.id, bob.id),
        )
        val state = stateWith(form = form, members = listOf(ann, bob))

        assertTrue(state.allMembersExcluded)
        assertFalse(state.canSave, "a lesson billing nobody cannot be saved")

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

    // ---- The save bar ----------------------------------------------------------------------------

    @Test
    fun `the save bar names what is missing — in the order the form is filled in`() {
        assertEquals(
            SaveBar.Missing(BookingGap.entries),
            stateWith(form = ClassForm(price = MoneyField(""))).saveBar,
        )
        assertEquals(
            SaveBar.Missing(listOf(BookingGap.START)),
            stateWith(form = validForm.copy(timeFrom = null)).saveBar,
        )
    }

    @Test
    fun `nothing is missing exactly when the form is valid`() {
        val forms = listOf(
            ClassForm(),
            validForm,
            validForm.copy(studentId = null),
            validForm.copy(subjectId = null),
            validForm.copy(date = null),
            validForm.copy(timeFrom = null),
            validForm.copy(durationMinutes = 0),
            validForm.copy(price = MoneyField("abc")),
            validForm.copy(target = ClassTarget.GROUP, studentId = null, groupId = 4),
        )
        forms.forEach { form -> assertEquals(form.isValid, stateWith(form = form).gaps.isEmpty(), form.toString()) }
    }

    @Test
    fun `the save bar is ready exactly when the form can be saved`() {
        val member = Student(id = 5, name = "Аня")
        val group = validForm.copy(target = ClassTarget.GROUP, studentId = null, groupId = 4)
        val cases = mapOf(
            stateWith(form = validForm) to SaveBar.Ready,
            stateWith(form = validForm, conflict = someoneElse) to SaveBar.Taken,
            stateWith(form = group) to SaveBar.NoBookableMembers,
            stateWith(form = group.copy(excludedMemberIds = setOf(5)), members = listOf(member)) to
                SaveBar.NoneTicked,
            stateWith(form = group, members = listOf(member)) to SaveBar.Ready,
        )
        cases.forEach { (state, bar) ->
            assertEquals(bar, state.saveBar)
            assertEquals(bar == SaveBar.Ready, state.canSave)
        }
    }

    @Test
    fun `the terms ask for the rate only when it is all that is missing`() {
        assertTrue(stateWith(form = validForm.copy(price = MoneyField(""))).rateNeeded)
        assertFalse(stateWith(form = validForm).rateNeeded)
        assertFalse(stateWith(form = ClassForm(price = MoneyField(""))).rateNeeded)
    }

    @Test
    fun `the lesson is for the student or the group its kind names`() {
        assertNull(ClassForm().assignee)
        assertEquals(Assignee.Student(7), ClassForm(studentId = 7, groupId = 3).assignee)
        assertEquals(Assignee.Group(3), ClassForm(target = ClassTarget.GROUP, studentId = 7, groupId = 3).assignee)
    }
}
