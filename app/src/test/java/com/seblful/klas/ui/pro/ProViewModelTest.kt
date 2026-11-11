package com.seblful.klas.ui.pro

import com.seblful.klas.domain.pro.Access
import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.domain.pro.ProSource
import com.seblful.klas.pro.Ownership
import com.seblful.klas.pro.PurchaseOutcome
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.current
import com.seblful.klas.testutil.observing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProViewModelTest {

    @get:Rule(order = 1)
    val mainRule = MainDispatcherRule()

    @get:Rule(order = 0)
    val env = TestEnv()

    private fun vm(): ProViewModel {
        env.proStore.sells = true
        return mainRule.own(ProViewModel(env.proAccess))
    }

    private fun TestScope.eventsOf(vm: ProViewModel): List<ProViewModel.Event> {
        val seen = mutableListOf<ProViewModel.Event>()
        backgroundScope.launch { vm.events.stream.collect { seen += it } }
        return seen
    }

    @Test
    fun `the plans arrive at the store's prices`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        runCurrent()

        assertEquals("$1.00", vm.offers.current?.monthly?.formatted)
        assertEquals(7, vm.offers.current?.monthly?.trialDays)
        assertEquals("$15.00", vm.offers.current?.lifetime?.formatted)
    }

    @Test
    fun `a store that cannot be asked has no plans`() = runTest(mainRule.dispatcher) {
        env.proStore.offers = null
        val vm = vm()
        runCurrent()

        assertNull(vm.offers.current)
    }

    @Test
    fun `buying the picked plan makes the tutor Pro and says so`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        observing(vm.gate)
        val events = eventsOf(vm)
        runCurrent()

        vm.pick(ProPlan.LIFETIME)
        vm.buy()
        runCurrent()

        assertEquals(listOf(ProPlan.LIFETIME), env.proStore.bought)
        assertEquals(listOf(ProViewModel.Event.BOUGHT), events)
        assertEquals(Access.Pro(ProSource.LIFETIME), vm.gate.value?.access)
    }

    @Test
    fun `a closed sheet says nothing`() = runTest(mainRule.dispatcher) {
        env.proStore.outcome = PurchaseOutcome.Cancelled
        val vm = vm()
        val events = eventsOf(vm)

        vm.buy()
        runCurrent()

        assertEquals(emptyList<ProViewModel.Event>(), events)
        assertEquals(Access.Free, env.proAccess.access.first())
    }

    @Test
    fun `restoring says what the store owns`() = runTest(mainRule.dispatcher) {
        val vm = vm()
        val events = eventsOf(vm)

        vm.restore()
        runCurrent()
        env.proStore.owned = Ownership(ProPlan.MONTHLY)
        vm.restore()
        runCurrent()
        env.proStore.owned = null
        vm.restore()
        runCurrent()

        assertEquals(
            listOf(
                ProViewModel.Event.NOTHING_TO_RESTORE,
                ProViewModel.Event.RESTORED,
                ProViewModel.Event.UNREACHABLE,
            ),
            events,
        )
    }
}
