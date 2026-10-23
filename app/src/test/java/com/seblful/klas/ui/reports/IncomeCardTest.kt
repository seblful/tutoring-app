package com.seblful.klas.ui.reports

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.domain.reports.Taught
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class IncomeCardTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val report = Reports.Report(
        period = ReportPeriod(ReportPeriod.Kind.YEAR, YearMonth(2026, 6)),
        hasData = true,
        totalIncome = Money.ofMajor(300),
        taught = Taught.Totals(lessons = 10, upcoming = 2, cancelled = 0, minutes = 600, earned = Money.ofMajor(300)),
        received = Reports.Received(
            total = Money.ofMajor(120),
            byMethod = listOf(
                Reports.MethodLine(PaymentMethod.CASH, Money.ofMajor(100)),
                Reports.MethodLine(PaymentMethod.CARD, Money.ofMajor(20)),
            ),
        ),
    )

    @Test
    fun `the hero shows earned with its teaching, and switches to received with its methods`() {
        compose.setContent { KlasTheme { IncomeCard(report) } }

        compose.onNodeWithText("300", substring = true).assertExists()
        compose.onNodeWithText("10 lessons", substring = true).assertExists()

        compose.onNodeWithText("Received").performClick()

        compose.onNodeWithText("120", substring = true).assertExists()
        compose.onNodeWithText("Cash", substring = true).assertExists()
        compose.onNodeWithText("10 lessons", substring = true).assertDoesNotExist()
    }

    @Test
    fun `one export menu offers sending and saving`() {
        var shared = 0
        var saved = 0
        compose.setContent { KlasTheme { ExportMenu(onShare = { shared++ }, onSave = { saved++ }) } }

        compose.onNodeWithContentDescription("Export").performClick()
        compose.onNodeWithText("Send the report").performClick()
        compose.onNodeWithContentDescription("Export").performClick()
        compose.onNodeWithText("Export to CSV").performClick()

        assertEquals(1, shared)
        assertEquals(1, saved)
    }
}
