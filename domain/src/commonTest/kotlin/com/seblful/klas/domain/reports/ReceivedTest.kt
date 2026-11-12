package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.model.Student
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReceivedTest {

    private val zone = TimeZone.of("Europe/Minsk")
    private val june = ReportPeriod(ReportPeriod.Kind.MONTH, YearMonth(2026, 6))

    private fun paid(at: String, amount: Long, method: PaymentMethod? = null, studentId: Long = 1) =
        Payment(studentId = studentId, date = Instant.parse(at), amount = Money(amount), method = method)

    @Test
    fun `only the period's payments count by the day they land on in the tutor's zone`() {
        val received = Reports.received(
            listOf(
                paid("2026-06-01T10:00:00Z", 3000),
                // 23:30 UTC on 31 May is already 1 June in Minsk.
                paid("2026-05-31T23:30:00Z", 1000),
                paid("2026-05-31T12:00:00Z", 5000),
                paid("2026-07-01T09:00:00Z", 7000),
            ),
            june,
            zone,
        )

        assertEquals(Money(4000), received.total)
    }

    @Test
    fun `the split follows the order methods are offered in with the unsaid last`() {
        val received = Reports.received(
            listOf(
                paid("2026-06-02T10:00:00Z", 1000),
                paid("2026-06-03T10:00:00Z", 2000, PaymentMethod.TRANSFER),
                paid("2026-06-04T10:00:00Z", 3000, PaymentMethod.CASH),
                paid("2026-06-05T10:00:00Z", 500, PaymentMethod.CASH),
            ),
            june,
            zone,
        )

        assertEquals(
            listOf(
                Reports.MethodLine(PaymentMethod.CASH, Money(3500)),
                Reports.MethodLine(PaymentMethod.TRANSFER, Money(2000)),
                Reports.MethodLine(null, Money(1000)),
            ),
            received.byMethod,
        )
        assertTrue(received.showsMethods)
    }

    @Test
    fun `a period paid without saying how has nothing to split`() {
        val received = Reports.received(listOf(paid("2026-06-02T10:00:00Z", 1000)), june, zone)

        assertFalse(received.showsMethods)
    }

    @Test
    fun `received is money in rather than income - a payment in advance counts with nothing taught`() {
        val report = reportFromHistory(
            period = june,
            classes = emptyList(),
            students = listOf(Student(id = 1, name = "Аня")),
            payments = listOf(paid("2026-06-10T10:00:00Z", 6000, PaymentMethod.CARD)),
            subjectNames = emptyMap(),
            groupNames = emptyMap(),
            zone = zone,
            today = LocalDate(2026, 6, 20),
        )

        assertEquals(Money.ZERO, report.totalIncome)
        assertEquals(Money(6000), report.received.total)
    }

    @Test
    fun `a payment by a student the report does not know is left out`() {
        val report = reportFromHistory(
            period = june,
            classes = emptyList(),
            students = listOf(Student(id = 1, name = "Аня")),
            payments = listOf(paid("2026-06-10T10:00:00Z", 6000), paid("2026-06-11T10:00:00Z", 4000, studentId = 9)),
            subjectNames = emptyMap(),
            groupNames = emptyMap(),
            zone = zone,
            today = LocalDate(2026, 6, 20),
        )

        assertEquals(Money(6000), report.received.total)
    }
}
