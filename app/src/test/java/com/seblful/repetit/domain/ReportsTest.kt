package com.seblful.repetit.domain

import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.StudentBalance
import com.seblful.repetit.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReportsTest {

    private val zone = ZoneId.of("Europe/Minsk")

    private fun klass(
        studentId: Long,
        subjectId: Long,
        start: String,
        charge: Long,
        status: ClassStatus = ClassStatus.COMPLETED,
        groupId: Long? = null,
    ) = TutoringClass(
        studentId = studentId, subjectId = subjectId, start = Instant.parse(start),
        durationMinutes = 60, status = status, charge = Money(charge), groupId = groupId,
    )

    @Test
    fun `total income counts only completed charges`() {
        val classes = listOf(
            klass(1, 1, "2026-05-10T10:00:00Z", 5000),
            klass(1, 1, "2026-05-11T10:00:00Z", 5000, ClassStatus.CANCELLED),
            klass(1, 1, "2026-05-12T10:00:00Z", 5000, ClassStatus.SCHEDULED),
        )
        assertEquals(Money(5000), Reports.totalIncome(classes))
    }

    @Test
    fun `income by month groups completed classes by calendar month`() {
        val classes = listOf(
            klass(1, 1, "2026-05-10T10:00:00Z", 5000),
            klass(1, 1, "2026-05-20T10:00:00Z", 3000),
            klass(2, 1, "2026-06-01T10:00:00Z", 4000),
        )
        val byMonth = Reports.incomeByMonth(classes, zone)
        // newest month first
        assertEquals(2026 * 12 + 5, byMonth[0].monthKey) // June (monthValue 6 -> index 5)
        assertEquals(Money(4000), byMonth[0].total)
        assertEquals(2026 * 12 + 4, byMonth[1].monthKey) // May
        assertEquals(Money(8000), byMonth[1].total)
    }

    @Test
    fun `income by student and subject sum the right charges`() {
        val classes = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(1, 11, "2026-05-11T10:00:00Z", 3000),
            klass(2, 10, "2026-05-12T10:00:00Z", 4000),
        )
        assertEquals(Money(8000), Reports.incomeByStudent(classes)[1])
        assertEquals(Money(4000), Reports.incomeByStudent(classes)[2])
        assertEquals(Money(9000), Reports.incomeBySubject(classes)[10])
        assertEquals(Money(3000), Reports.incomeBySubject(classes)[11])
    }

    @Test
    fun `income by kind splits one-to-one from group teaching, largest first`() {
        val classes = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(2, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(3, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            klass(4, 10, "2026-05-11T18:00:00Z", 2000, groupId = 7),
            // Neither of these was billed, so neither kind may count it.
            klass(1, 10, "2026-05-12T10:00:00Z", 5000, ClassStatus.CANCELLED),
            klass(2, 10, "2026-05-12T18:00:00Z", 2000, ClassStatus.SCHEDULED, groupId = 7),
        )

        val lines = Reports.incomeByKind(classes)

        // A group lesson's money is the sum of its members' charges — the group sets the rate, each
        // member owes their share of it — so three at 20 outweighs one at 50.
        assertEquals(listOf(LessonKind.GROUP, LessonKind.INDIVIDUAL), lines.map { it.kind })
        assertEquals(Money(6000), lines[0].total)
        assertEquals(Money(5000), lines[1].total)
        assertEquals(Reports.totalIncome(classes), lines.fold(Money.ZERO) { a, l -> a + l.total })
    }

    /** A tutor who teaches one way should not be shown a "split" that only restates the total. */
    @Test
    fun `a kind that earned nothing is left out rather than printed as zero`() {
        val onlyIndividual = listOf(
            klass(1, 10, "2026-05-10T10:00:00Z", 5000),
            klass(2, 10, "2026-05-11T18:00:00Z", 2000, ClassStatus.CANCELLED, groupId = 7),
        )

        assertEquals(
            listOf(LessonKind.INDIVIDUAL),
            Reports.incomeByKind(onlyIndividual).map { it.kind },
        )
        assertEquals(emptyList<Reports.KindIncome>(), Reports.incomeByKind(emptyList()))
    }

    @Test
    fun `debts lists only students who owe, largest first`() {
        val balances = listOf(
            StudentBalance(1, charged = Money(5000), paid = Money(2000)), // owes 3000
            StudentBalance(2, charged = Money(4000), paid = Money(9000)), // credit -5000
            StudentBalance(3, charged = Money(10000), paid = Money(1000)), // owes 9000
        )
        val debts = Reports.debts(balances)
        assertEquals(listOf(3L, 1L), debts.map { it.studentId })
        assertEquals(Money(9000), debts[0].amount)
        assertEquals(Money(12000), Reports.totalOutstanding(balances)) // 3000 + 9000, credit excluded
    }
}
