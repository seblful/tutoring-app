package com.seblful.klas.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DemoTutorTest {

    private val tutors = DemoTutor.all

    @Test
    fun `every language tells the same week`() {
        fun week(t: DemoTutor) = Pair(
            t.students.map { s ->
                listOf(s.subject, s.rate, s.from, s.slots, s.mode, s.homework != null, s.paysUntil, s.prepaysLessons)
            },
            with(t.group) { listOf(subject, rate, from, slots, members.size, owing in members) },
        )
        fun done(t: DemoTutor) = t.doneHomework.map { (name, _) -> t.students.indexOfFirst { it.name == name } }
        val english = DemoTutor.english
        for (t in tutors) {
            // The same week at the language's own prices.
            val priced = english.copy(
                students = english.students.map { it.copy(rate = it.rate * t.scale) },
                group = english.group.copy(rate = english.group.rate * t.scale),
            )
            assertEquals(t.play, week(priced), week(t))
            assertEquals(t.play, done(english), done(t))
        }
    }

    @Test
    fun `every language in the store listing has a demo tutor, with ten different people`() {
        val listed = File(System.getProperty(DemoTutor.TEXT_PROPERTY)).listFiles()!!
            .filter { File(it, "title.txt").isFile }.map { it.name }.sorted()
        assertEquals(listed, tutors.map { it.play })
        for (t in tutors) {
            val people = t.students.map { it.name } + t.group.members
            assertEquals("${t.play}: $people", 10, people.toSet().size)
        }
    }

    @Test
    fun `the clock stops mid-lesson`() {
        for (t in tutors) {
            assertTrue(t.onNow.slots.any { it.covers(DemoTutor.NOW) })
            assertEquals(1, t.students.count { s -> s.slots.any { it.covers(DemoTutor.NOW) } })
        }
        assertEquals("Olivia Brooks", DemoTutor.english.onNow.name)
    }

    @Test
    fun `the unmarked lesson is over and clashes with nothing that day`() {
        val unmarked = DemoTutor.UNMARKED
        assertEquals(DemoTutor.NOW.dayOfWeek, unmarked.day)
        assertFalse(unmarked.endOn(DemoTutor.TODAY) > DemoTutor.CLOCK.now())
        for (t in tutors) {
            assertTrue(t.freeToday.slotsOn(DemoTutor.TODAY).isEmpty())
            val today = (t.students + t.group).flatMap { it.slotsOn(DemoTutor.TODAY) }
            for (slot in today) {
                val apart = slot.endOn(DemoTutor.TODAY) <= unmarked.startOn(DemoTutor.TODAY) ||
                    unmarked.endOn(DemoTutor.TODAY) <= slot.startOn(DemoTutor.TODAY)
                assertTrue("$slot overlaps the unmarked lesson", apart)
            }
        }
    }

    @Test
    fun `one student owes and another is in credit`() {
        for (t in tutors) {
            assertTrue(t.debtor.paysUntil!! < DemoTutor.TODAY)
            assertNotEquals(t.debtor, t.inCredit)
            assertEquals(null, t.inCredit.paysUntil)
        }
    }

    @Test
    fun `every subject has a name`() {
        for (t in tutors) {
            assertEquals(DemoSubject.entries.size, DemoSubject.entries.map { t.subjects[it] }.toSet().size)
        }
    }
}
