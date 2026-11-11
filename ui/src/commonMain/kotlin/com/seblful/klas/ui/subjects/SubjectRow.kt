package com.seblful.klas.ui.subjects

import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Price
import com.seblful.klas.domain.model.Subject

/**
 * A subject as its page lists it: how many students take it and groups are taught it, at what rates — which
 * also says why a used one cannot be deleted. Archived students and groups count: they still hold it.
 *
 * @param lowest and [highest] the hourly rates it is taught at, students' and groups' alike; null when none.
 */
data class SubjectRow(
    val subject: Subject,
    val students: Int,
    val groups: Int,
    val lowest: Money?,
    val highest: Money?,
) {
    /** Nothing holds it, so its editor offers to delete it. */
    val unused: Boolean get() = students == 0 && groups == 0

    companion object {
        fun of(subject: Subject, prices: List<Price>, groups: List<Group>): SubjectRow {
            val rates = prices.map { it.amount } + groups.map { it.price }.filter { it > Money.ZERO }
            return SubjectRow(subject, prices.size, groups.size, rates.minOrNull(), rates.maxOrNull())
        }
    }
}
