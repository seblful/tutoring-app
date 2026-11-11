package com.seblful.klas.ui.pro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seblful.klas.domain.pro.ProGate
import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.pro.ProAccess
import com.seblful.klas.pro.ProOffers
import com.seblful.klas.pro.PurchaseOutcome
import com.seblful.klas.runCatchingCancellable
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.UiEvents
import com.seblful.klas.ui.common.state.hold
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The Klas Pro screen: what the tutor has, the store's prices, and buying or restoring a purchase. */
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ProViewModel @Inject constructor(private val pro: ProAccess) : ViewModel() {

    val gate: StateFlow<ProGate?> = pro.gate.hold(this, null)

    private val _offers = MutableStateFlow<Loadable<ProOffers?>>(Loadable.Loading)

    /** The store's prices; ready with null when the store could not be asked. */
    val offers: StateFlow<Loadable<ProOffers?>> = _offers.asStateFlow()

    private val _plan = MutableStateFlow(ProPlan.MONTHLY)

    /** The plan picked on the screen: the subscription first, since its trial costs nothing to try. */
    val plan: StateFlow<ProPlan> = _plan.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** A purchase sheet or a restore is under way; the buttons wait for it. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** A purchase is waiting on payment, as the store last said — so it is still said on the next visit. */
    val pending: StateFlow<Boolean> = pro.pending

    val manageUrl: String? get() = pro.manageUrl

    enum class Event { BOUGHT, PENDING, UNAVAILABLE, FAILED, RESTORED, NOTHING_TO_RESTORE, UNREACHABLE }

    val events = UiEvents<Event>()

    init {
        loadOffers()
        // Quietly, so a pending payment that went through, or one still waiting, shows as it is now.
        viewModelScope.launch { runCatchingCancellable { pro.refresh() } }
    }

    fun loadOffers() {
        _offers.value = Loadable.Loading
        viewModelScope.launch { _offers.value = Loadable.Ready(runCatchingCancellable { pro.offers() }.getOrNull()) }
    }

    fun pick(plan: ProPlan) {
        _plan.value = plan
    }

    fun buy() = busyWith {
        val event = when (runCatchingCancellable { pro.buy(_plan.value) }.getOrElse { PurchaseOutcome.Failed }) {
            is PurchaseOutcome.Bought -> Event.BOUGHT
            PurchaseOutcome.Pending -> Event.PENDING
            PurchaseOutcome.Cancelled -> null
            PurchaseOutcome.Unavailable -> Event.UNAVAILABLE
            PurchaseOutcome.Failed -> Event.FAILED
        }
        event?.let(events::send)
    }

    fun restore() = busyWith {
        val asked = runCatchingCancellable { pro.refresh() }.getOrDefault(false)
        events.send(
            when {
                !asked -> Event.UNREACHABLE
                pro.current().isPro -> Event.RESTORED
                else -> Event.NOTHING_TO_RESTORE
            },
        )
    }

    private fun busyWith(work: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                work()
            } finally {
                _busy.value = false
            }
        }
    }
}
