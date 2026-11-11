package com.seblful.klas.pro

import com.seblful.klas.domain.pro.ProPlan

/**
 * The platform's store, which sells Klas Pro: Google Play on Android. Every call answers or says it could not ask;
 * none throws for a store that is missing, signed out or offline.
 */
interface ProStore {
    /** False where the app has no store to sell Pro through yet; nothing is locked there. */
    val sells: Boolean

    /** What the store's account owns now; null when the store could not be asked. */
    suspend fun owned(): Ownership?

    /** The prices to show, in the account's currency; null when the store could not be asked. */
    suspend fun offers(): ProOffers?

    /** Opens the store's purchase sheet for [plan] and waits for how it ends. */
    suspend fun buy(plan: ProPlan): PurchaseOutcome

    /** The store's page where the tutor manages or cancels the subscription; null where there is none. */
    val manageUrl: String?
}

/**
 * @param plan the plan owned and paid for; the one-time purchase wins over a subscription bought beside it.
 * @param pending a purchase is waiting on payment (cash at a shop, for one) and unlocks nothing yet.
 */
data class Ownership(val plan: ProPlan?, val pending: Boolean = false) {
    companion object {
        val NOTHING = Ownership(plan = null)
    }
}

/** The two plans as the store prices them. Either is null when the store does not offer it. */
data class ProOffers(val monthly: Price?, val lifetime: Price?) {
    /**
     * @param formatted the price as the store writes it, in the account's currency («$1.00», «1,29 €»).
     * @param trialDays days free before the first charge, when the account can still have the trial.
     */
    data class Price(val formatted: String, val trialDays: Int? = null)
}

sealed interface PurchaseOutcome {
    data class Bought(val plan: ProPlan) : PurchaseOutcome

    /** Paid for with a method that settles later; Pro arrives with it. */
    data object Pending : PurchaseOutcome

    /** The tutor closed the sheet. Nothing to say. */
    data object Cancelled : PurchaseOutcome

    /** The store cannot sell here: not signed in, not installed, or the product is not offered. */
    data object Unavailable : PurchaseOutcome

    data object Failed : PurchaseOutcome
}
