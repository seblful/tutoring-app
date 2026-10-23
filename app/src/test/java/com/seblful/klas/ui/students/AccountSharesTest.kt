package com.seblful.klas.ui.students

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.money
import com.seblful.klas.queries.students.ClassRow
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.testutil.AppLocaleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class AccountSharesTest {

    @get:Rule
    val language = AppLocaleRule.english()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val currency = Currency.BYN

    private fun lesson(id: Long, at: String, charge: Long) = ClassRow(
        klass = TutoringClass(
            id = id, lessonId = id, studentId = 1, subjectId = 1, start = Instant.parse(at), durationMinutes = 60,
            price = Money(charge), status = LessonStatus.COMPLETED, attended = Attended.PRESENT,
            charge = Money(charge),
        ),
        subjectName = "Maths",
    )

    @Test
    fun `a statement lists each lesson and payment with the balance it left, then what is owed`() {
        val details = StudentDetails(
            balance = Balance.Owed(Money(3000)),
            classes = listOf(lesson(1, "2026-06-01T10:00:00Z", 3000), lesson(2, "2026-06-08T10:00:00Z", 3000)),
            payments = listOf(
                Payment(id = 1, studentId = 1, date = Instant.parse("2026-06-02T10:00:00Z"), amount = Money(3000)),
            ),
        )

        val lines = statementText(resources, currency, "Anna", details).lines()

        assertEquals("Anna: lessons and payments", lines.first())
        assertEquals(3, lines.count { "·" in it })
        assertTrue(lines[2], "Lesson, Maths" in lines[2])
        assertTrue(lines[3], "Payment" in lines[3] && "−" + Money(3000).money(currency) in lines[3])
        assertEquals("To pay: ${Money(3000).money(currency)}", lines.last())
    }

    @Test
    fun `a statement of someone paid up says nothing is due`() {
        val details = StudentDetails(
            classes = listOf(lesson(1, "2026-06-01T10:00:00Z", 3000)),
            payments = listOf(
                Payment(id = 1, studentId = 1, date = Instant.parse("2026-06-02T10:00:00Z"), amount = Money(3000)),
            ),
        )

        assertEquals("Nothing to pay", statementText(resources, currency, "Anna", details).lines().last())
    }

    @Test
    fun `the printed statement has a row per entry, the balance under each, and the total last`() {
        val details = StudentDetails(
            balance = Balance.Owed(Money(3000)),
            classes = listOf(lesson(1, "2026-06-01T10:00:00Z", 3000)),
        )

        val sheet = statementSheet(resources, currency, "Anna", details)

        assertEquals("Anna: lessons and payments", sheet.title)
        val row = sheet.sections.single().rows.single()
        assertTrue(row.label, row.label.endsWith("Lesson, Maths"))
        assertEquals(Money(3000).money(currency), row.value)
        assertEquals("Balance after: ${Money(3000).money(currency)}", row.detail)
        assertEquals("To pay: ${Money(3000).money(currency)}", sheet.closing)
    }
}
