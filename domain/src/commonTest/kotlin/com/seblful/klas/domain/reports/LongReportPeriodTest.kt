package com.seblful.klas.domain.reports

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class LongReportPeriodTest {
    private val zone = TimeZone.of("Europe/Minsk")

    @Test
    fun `an all-time trend spans as many years as the data does`() {
        val classes = listOf(
            completed(id = 1, at = Instant.fromEpochMilliseconds(0)),
            completed(id = 2, at = Instant.parse("2026-05-01T09:00:00Z")),
        )

        val trend = Reports.trend(classes, ReportPeriod(ReportPeriod.Kind.ALL, YearMonth(2026, 5)), zone)
        assertEquals(Reports.Trend.Bucket.YEARS, trend.bucket)
        assertTrue(trend.bars.size > 50, "a bar per year since 1970")
    }

    private fun completed(id: Long, at: Instant) = TutoringClass(
        id = id, studentId = 1, subjectId = 1, start = at, durationMinutes = 60,
        status = LessonStatus.COMPLETED, charge = Money.ofMajor(30),
    )
}
