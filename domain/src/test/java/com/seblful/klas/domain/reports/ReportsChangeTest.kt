package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The period-on-period comparison — the line that turns the reports screen's headline number into an
 * answer to the question the screen exists to ask.
 */
class ReportsChangeTest {

    /** All time has no period before it, so there is nothing to state. */
    @Test
    fun `no previous period means no comparison`() {
        assertNull(Reports.change(Money.ofMajor(100), previous = null))
    }

    /**
     * Growth from nothing is not a percentage. «+100 %» or «+∞» would be arithmetic theatre; that the
     * previous period was empty is the actual news.
     */
    @Test
    fun `a previous period that earned nothing has no baseline`() {
        assertEquals(Reports.Change.NoBaseline, Reports.change(Money.ofMajor(100), Money.ZERO))
        assertEquals(Reports.Change.NoBaseline, Reports.change(Money.ZERO, Money.ZERO))
    }

    @Test
    fun `more than last time is positive`() {
        assertEquals(
            Reports.Change.Percent(12),
            Reports.change(Money.ofMajor(112), Money.ofMajor(100)),
        )
    }

    @Test
    fun `less than last time is negative`() {
        assertEquals(
            Reports.Change.Percent(-25),
            Reports.change(Money.ofMajor(75), Money.ofMajor(100)),
        )
    }

    @Test
    fun `an unchanged period is zero rather than a near-miss`() {
        assertEquals(
            Reports.Change.Percent(0),
            Reports.change(Money.ofMajor(100), Money.ofMajor(100)),
        )
    }

    /** Earning nothing this period against something last is −100 %, not a missing comparison. */
    @Test
    fun `earning nothing after a good period is minus one hundred`() {
        assertEquals(
            Reports.Change.Percent(-100),
            Reports.change(Money.ZERO, Money.ofMajor(80)),
        )
    }

    /** Rounded away from zero at the half, so a small real rise does not print as no change. */
    @Test
    fun `a half percent rounds up rather than vanishing`() {
        // 100,50 against 100,00 — half a percent.
        assertEquals(
            Reports.Change.Percent(1),
            Reports.change(Money(10_050), Money(10_000)),
        )
        // And the same magnitude downward keeps its sign.
        assertEquals(
            Reports.Change.Percent(-1),
            Reports.change(Money(9_950), Money(10_000)),
        )
    }

    @Test
    fun `more than doubling is expressed as over a hundred percent`() {
        assertEquals(
            Reports.Change.Percent(150),
            Reports.change(Money.ofMajor(250), Money.ofMajor(100)),
        )
    }
}
