package com.seblful.klas.queries.reports

import com.seblful.klas.domain.reports.ReportScope

/** One entry in the report's filter menu. */
data class ScopeChoice(val scope: ReportScope, val name: String)
