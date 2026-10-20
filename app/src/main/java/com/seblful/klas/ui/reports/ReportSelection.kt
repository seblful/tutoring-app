package com.seblful.klas.ui.reports

import com.seblful.klas.domain.reports.ReportPeriod
import com.seblful.klas.domain.reports.Reports
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.valueOrNull

internal fun Loadable<Reports.Report>.forPeriod(period: ReportPeriod): Reports.Report? =
    valueOrNull()?.takeIf { it.period == period }

internal data class ReportView(val shown: Reports.Report?, val exportable: Reports.Report?)

internal fun Loadable<Reports.Report>.viewFor(period: ReportPeriod): ReportView =
    ReportView(shown = valueOrNull(), exportable = forPeriod(period))
