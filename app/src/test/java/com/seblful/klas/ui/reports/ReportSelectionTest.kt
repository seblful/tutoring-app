package com.seblful.klas.ui.reports

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.ui.common.state.Loadable
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReportSelectionTest {
    private val period = ReportPeriod.currentYear(LocalDate(2026, 6, 1))
    private val report = Reports.Report(period = period, totalIncome = Money.ofMajor(30))

    @Test
    fun `previous figures cannot be displayed or exported under a new period`() {
        assertNull(Loadable.Ready(report).forPeriod(period.shifted(-1)))
    }

    @Test
    fun `the report becomes available when its own period arrives`() {
        assertSame(report, Loadable.Ready(report).forPeriod(period))
        assertNull(Loadable.Loading.forPeriod(period))
    }

    @Test
    fun `stepping to another period keeps the previous report on screen but not in a file`() {
        val view = Loadable.Ready(report).viewFor(period.shifted(-1))

        assertSame(report, view.shown)
        assertNull(view.exportable)
    }

    @Test
    fun `a cold open has nothing to show until the first report arrives`() {
        val view = Loadable.Loading.viewFor(period)

        assertNull(view.shown)
        assertNull(view.exportable)
    }
}
