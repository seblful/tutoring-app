package com.seblful.klas.pro

import com.seblful.klas.domain.pro.ProPlan
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * No store yet: iOS sells nothing, so nothing is locked there ([sells] false). Selling Pro on iOS means a StoreKit
 * adapter here and the products in App Store Connect — see `docs/ios.md`.
 */
@ContributesBinding(AppScope::class)
@Inject
class IosProStore : ProStore {
    override val sells: Boolean = false

    override suspend fun owned(): Ownership? = null

    override suspend fun offers(): ProOffers? = null

    override suspend fun buy(plan: ProPlan): PurchaseOutcome = PurchaseOutcome.Unavailable

    override val manageUrl: String? = null
}
