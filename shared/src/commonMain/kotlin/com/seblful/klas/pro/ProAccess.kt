package com.seblful.klas.pro

import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.pro.Access
import com.seblful.klas.domain.pro.ProGate
import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.domain.pro.ProSource
import com.seblful.klas.settings.SettingsWorkflow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether the tutor has Klas Pro, and keeping the settings honest about it.
 *
 * The store's answer is cached in the settings row (`proOwned`), so Pro holds offline and on a cold start before
 * the store answers; only an answer replaces it — a store that cannot be asked leaves it as it was. When an answer
 * leaves the tutor without Pro, what Pro ran by itself stops: the calendar mirror is withdrawn and the digests
 * are switched off. Nothing else changes — every student, group and lesson stays as usable as it was.
 */
@SingleIn(AppScope::class)
class ProAccess @Inject constructor(
    private val settings: SettingsRepository,
    private val workflow: SettingsWorkflow,
    private val store: ProStore,
    students: StudentRepository,
) {
    val access: Flow<Access> = settings.observe().map { it.access() }.distinctUntilChanged()

    /** What the tutor may do, which for students depends on how many are active. */
    val gate: Flow<ProGate> =
        combine(access, students.observeActive().map { it.size }.distinctUntilChanged(), ::ProGate)

    /** Whether the store sells Pro here at all. */
    val storeSells: Boolean get() = store.sells

    /** The store's prices; null when it could not be asked. */
    suspend fun offers(): ProOffers? = store.offers()

    /** Where the subscription is managed; null without one to manage. */
    val manageUrl: String? get() = store.manageUrl

    suspend fun current(): Access = settings.get().access()

    private val _pending = MutableStateFlow(false)

    /** A purchase is waiting on payment, as the store last said. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Pro bought on this phone has ended, and the tutor is still to be told once. */
    val ended: Flow<Boolean> = settings.observe().map { it.proEnded }.distinctUntilChanged()

    /** The tutor has been told that Pro ended. */
    suspend fun endedSeen() {
        settings.update { it.copy(proEnded = false) }
    }

    /** Asks the store what is owned and settles on its answer. False when the store could not be asked. */
    suspend fun refresh(): Boolean {
        val owned = store.owned() ?: return false
        _pending.value = owned.pending && owned.plan == null
        settle(owned.plan)
        return true
    }

    /**
     * Opens the store's purchase sheet for [plan]; a purchase made is Pro at once. Any other ending asks the store
     * again, which catches a pending payment that has since gone through.
     */
    suspend fun buy(plan: ProPlan): PurchaseOutcome = store.buy(plan).also {
        when (it) {
            is PurchaseOutcome.Bought -> settle(it.plan)
            PurchaseOutcome.Pending -> _pending.value = true
            else -> refresh()
        }
    }

    /** Stores [plan]; a purchase that stops being Pro is remembered, so the tutor is told why things went quiet. */
    private suspend fun settle(plan: ProPlan?) {
        val after = settings.update { before ->
            val now = before.copy(proOwned = plan)
            val bought = before.access().let { it is Access.Pro && it.source in BOUGHT }
            now.copy(proEnded = !now.access().isPro && (before.proEnded || bought))
        }.after
        if (!after.access().isPro) workflow.update { it.withoutPro() }
    }

    private fun AppSettings.access(): Access = Access.of(earlyUser, proOwned, store.sells)

    private companion object {
        val BOUGHT = setOf(ProSource.MONTHLY, ProSource.LIFETIME)
    }
}

/** [this] with what Pro runs by itself switched off. */
internal fun AppSettings.withoutPro(): AppSettings = copy(
    calendarSyncEnabled = false,
    dailyAgendaEnabled = false,
    unfinishedDigestEnabled = false,
    debtDigestEnabled = false,
)
