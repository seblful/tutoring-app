package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportsChangeTest {

    @Test
    fun `no previous period means no comparison`() {
        assertNull(Reports.change(Money.ofMajor(100), previous = null))
    }

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

    @Test
    fun `earning nothing after a good period is minus one hundred`() {
        assertEquals(
            Reports.Change.Percent(-100),
            Reports.change(Money.ZERO, Money.ofMajor(80)),
        )
    }

    @Test
    fun `a half percent rounds up rather than vanishing`() {
        assertEquals(
            Reports.Change.Percent(1),
            Reports.change(Money(10_050), Money(10_000)),
        )
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
