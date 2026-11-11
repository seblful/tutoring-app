package com.seblful.klas.pro

import com.seblful.klas.domain.pro.Access
import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.domain.pro.ProSource
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProAccessTest {

    private val env = TestEnv().apply { proStore.sells = true }

    @AfterTest
    fun closeEnvironment() {
        env.close()
    }

    private val pro get() = env.proAccess

    @Test
    fun `a new tutor starts free`() = runTest {
        assertEquals(Access.Free, pro.current())
        assertEquals(Access.Free, pro.access.first())
    }

    @Test
    fun `what the store owns is Pro and held while the store cannot be asked`() = runTest {
        env.proStore.owned = Ownership(ProPlan.MONTHLY)
        assertTrue(pro.refresh())
        assertEquals(Access.Pro(ProSource.MONTHLY), pro.current())

        env.proStore.owned = null
        assertFalse(pro.refresh())
        assertEquals(Access.Pro(ProSource.MONTHLY), pro.current())
    }

    @Test
    fun `a purchase is Pro at once`() = runTest {
        val outcome = pro.buy(ProPlan.LIFETIME)

        assertEquals(PurchaseOutcome.Bought(ProPlan.LIFETIME), outcome)
        assertEquals(Access.Pro(ProSource.LIFETIME), pro.current())
    }

    @Test
    fun `a pending purchase unlocks nothing`() = runTest {
        env.proStore.outcome = PurchaseOutcome.Pending

        pro.buy(ProPlan.MONTHLY)

        assertEquals(Access.Free, pro.current())
    }

    @Test
    fun `a lapsed subscription stops the calendar mirror and the digests and keeps the rest`() = runTest {
        env.proStore.owned = Ownership(ProPlan.MONTHLY)
        pro.refresh()
        env.settingsRepo.update {
            it.copy(
                calendarSyncEnabled = true,
                dailyAgendaEnabled = true,
                unfinishedDigestEnabled = true,
                debtDigestEnabled = true,
                autoCompleteEnabled = true,
            )
        }

        env.proStore.owned = Ownership.NOTHING
        pro.refresh()

        val settings = env.settingsRepo.get()
        assertEquals(Access.Free, pro.current())
        assertFalse(settings.calendarSyncEnabled)
        assertFalse(settings.dailyAgendaEnabled)
        assertFalse(settings.unfinishedDigestEnabled)
        assertFalse(settings.debtDigestEnabled)
        assertTrue(settings.autoCompleteEnabled)
    }

    @Test
    fun `an early user keeps everything when the store owns nothing`() = runTest {
        env.settingsRepo.update { it.copy(earlyUser = true, calendarSyncEnabled = true, dailyAgendaEnabled = true) }

        pro.refresh()

        val settings = env.settingsRepo.get()
        assertEquals(Access.Pro(ProSource.EARLY_USER), pro.current())
        assertTrue(settings.calendarSyncEnabled)
        assertTrue(settings.dailyAgendaEnabled)
    }

    @Test
    fun `a bought Pro that ends is said once`() = runTest {
        env.proStore.owned = Ownership(ProPlan.MONTHLY)
        pro.refresh()
        assertFalse(pro.ended.first())

        env.proStore.owned = Ownership.NOTHING
        pro.refresh()
        assertTrue(pro.ended.first())
        pro.refresh()
        assertTrue(pro.ended.first(), "asking again does not clear it before it is said")

        pro.endedSeen()
        pro.refresh()
        assertFalse(pro.ended.first())
    }

    @Test
    fun `a tutor who never bought Pro has nothing to be told`() = runTest {
        pro.refresh()

        assertFalse(pro.ended.first())
    }

    @Test
    fun `buying again takes back the news that Pro ended`() = runTest {
        env.proStore.owned = Ownership(ProPlan.MONTHLY)
        pro.refresh()
        env.proStore.owned = Ownership.NOTHING
        pro.refresh()

        pro.buy(ProPlan.LIFETIME)

        assertFalse(pro.ended.first())
    }

    @Test
    fun `a pending payment is kept from the store's last answer`() = runTest {
        env.proStore.owned = Ownership(plan = null, pending = true)
        pro.refresh()
        assertTrue(pro.pending.value)

        env.proStore.owned = Ownership(ProPlan.MONTHLY)
        pro.refresh()
        assertFalse(pro.pending.value)
        assertEquals(Access.Pro(ProSource.MONTHLY), pro.current())
    }
}
