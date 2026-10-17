package com.seblful.klas.ui.reports

import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull

/** A computed report that can be drawn and exported under the requested period's labels. */
internal fun Loadable<Reports.Report>.forPeriod(period: ReportPeriod): Reports.Report? =
    valueOrNull()?.takeIf { it.period == period }

/**
 * What the screen draws, and what it may send, while the tutor steps to another period.
 *
 * The period is its own flow, so for a frame or two after an arrow tap the report behind it is still the
 * previous period's. Drawing nothing in that window ([forPeriod] alone) blinked the body out to its
 * skeleton on every tap and took the navigator and the share icons with it. So the previous report stays
 * on screen — it labels itself by its own [Reports.Report.period] — and only what leaves the app is held
 * to the requested period: a file must never carry one month's figures under another month's name.
 */
internal data class ReportView(val shown: Reports.Report?, val exportable: Reports.Report?)

internal fun Loadable<Reports.Report>.viewFor(period: ReportPeriod): ReportView =
    ReportView(shown = valueOrNull(), exportable = forPeriod(period))
