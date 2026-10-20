package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTargetsTest {
    private val anya = Student(id = 1, name = "Аня")
    private val boris = Student(id = 2, name = "Борис")
    private val vera = Student(id = 3, name = "Вера")
    private val left = Student(id = 4, name = "Гоша", archived = true)
    private val ninth = Group(id = 9, name = "9 «Б»", price = Money.ofMajor(20))

    private val students = listOf(anya, boris, vera, left)
    private val groups = listOf(ninth)

    private fun names(targets: List<QuickTarget>) = targets.map { it.name }

    @Test
    fun `the most recently taught come first, then everybody else in list order`() {
        val picked = QuickTargets.pick(
            recent = listOf(Assignee.Group(ninth.id), Assignee.Student(vera.id)),
            students = students,
            groups = groups,
        )

        assertEquals(listOf("9 «Б»", "Вера", "Аня", "Борис"), names(picked))
    }

    @Test
    fun `archived people are never offered, even when recently taught`() {
        val picked = QuickTargets.pick(recent = listOf(Assignee.Student(left.id)), students, groups, shown = 10)

        assertEquals(listOf("Аня", "Борис", "Вера", "9 «Б»"), names(picked))
    }

    @Test
    fun `whoever is chosen is always a chip, first when it had to be added`() {
        val picked = QuickTargets.pick(
            recent = emptyList(),
            students = students,
            groups = groups,
            chosen = Assignee.Group(ninth.id),
            shown = 2,
        )

        assertEquals(listOf("9 «Б»", "Аня"), names(picked))
        assertEquals(
            "already among them, it stays where it is",
            listOf("Аня", "Борис"),
            names(QuickTargets.pick(emptyList(), students, groups, chosen = Assignee.Student(boris.id), shown = 2)),
        )
    }
}
