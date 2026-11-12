package com.seblful.klas.testutil

import com.seblful.klas.domain.pro.ProPlan
import com.seblful.klas.pro.Ownership
import com.seblful.klas.pro.ProOffers
import com.seblful.klas.pro.ProStore
import com.seblful.klas.pro.PurchaseOutcome

/**
 * A store that answers from its fields; it sells Pro when [sells] says so. [owned] null is a store that cannot be
 * asked; [outcome] is how the next purchase sheet ends, and a bought plan becomes what [owned] says.
 */
class FakeProStore(
    var owned: Ownership? = Ownership.NOTHING,
    var offers: ProOffers? = ProOffers(
        monthly = ProOffers.Price("$1.00", trialDays = 7),
        lifetime = ProOffers.Price("$15.00"),
    ),
    override var sells: Boolean = true,
) : ProStore {
    var outcome: PurchaseOutcome? = null
    val bought = mutableListOf<ProPlan>()

    override suspend fun owned(): Ownership? = owned

    override suspend fun offers(): ProOffers? = offers

    override suspend fun buy(plan: ProPlan): PurchaseOutcome {
        bought += plan
        val result = outcome ?: PurchaseOutcome.Bought(plan)
        if (result is PurchaseOutcome.Bought) owned = Ownership(result.plan)
        return result
    }

    override val manageUrl: String? = "https://store.example/subscriptions"
}
