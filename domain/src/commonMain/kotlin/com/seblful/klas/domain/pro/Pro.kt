package com.seblful.klas.domain.pro

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The two ways to buy Klas Pro: a monthly subscription (with the store's free trial) or a one-time purchase. */
enum class ProPlan { MONTHLY, LIFETIME }

/** What a tutor without Pro asks for when the paywall opens; the screen leads with it. */
enum class ProFeature { STUDENTS, GROUPS, CALENDAR, DIGESTS, REPORTS, STATEMENTS }

/** Why a tutor has Pro, which decides what the Pro screen says about it. */
enum class ProSource {
    /** Used the app before Pro existed, so keeps everything free (schema v17's migration, or their backup). */
    EARLY_USER,
    MONTHLY,
    LIFETIME,

    /** The platform has no store to sell Pro through yet (iOS until StoreKit), so nothing is locked there. */
    NO_STORE,
}

/** Whether the tutor has Pro, and why. */
sealed interface Access {
    val isPro: Boolean get() = this is Pro

    data object Free : Access

    data class Pro(val source: ProSource) : Access

    companion object {
        /**
         * @param earlyUser the install, or the backup it was restored from, predates Pro.
         * @param owned what the store last said the tutor owns; null when nothing (or not answered yet).
         * @param storeSells false on a platform with no store to buy Pro from.
         */
        fun of(earlyUser: Boolean, owned: ProPlan?, storeSells: Boolean): Access = when {
            earlyUser -> Pro(ProSource.EARLY_USER)
            owned == ProPlan.LIFETIME -> Pro(ProSource.LIFETIME)
            owned == ProPlan.MONTHLY -> Pro(ProSource.MONTHLY)
            !storeSells -> Pro(ProSource.NO_STORE)
            else -> Free
        }
    }
}

/** What the tutor may do now: their [access], and how many active students they already have. */
data class ProGate(val access: Access, val activeStudents: Int) {
    fun allows(feature: ProFeature): Boolean = when (feature) {
        ProFeature.STUDENTS -> FreeTier.roomForStudent(access, activeStudents)
        else -> access.isPro
    }
}

/**
 * What the free tier allows. Losing Pro never hides or deletes anything: it stops only what would add more —
 * another active student, a group, a group lesson — and what runs by itself, the calendar mirror and digests.
 */
object FreeTier {
    /** Active (not archived) students a free tutor can have. */
    const val STUDENTS = 5

    /** Whether one more active student fits beside [active] ones. */
    fun roomForStudent(access: Access, active: Int): Boolean = access.isPro || active < STUDENTS
}

/**
 * When the bar's Klas Pro icon is shown to a free tutor: not in their first week, nor while the first-steps card
 * is still out — whichever ends first. A new tutor's first days are for the app working, not for selling it.
 */
object ProOffer {
    val QUIET: Duration = 7.days

    /** @param setupAt when first run was finished here; null for an install from before Pro, or a restore. */
    fun shown(setupAt: Instant?, now: Instant, firstStepsHidden: Boolean): Boolean =
        firstStepsHidden || setupAt == null || now - setupAt >= QUIET
}
